package com.moyeota.data.remote

import com.moyeota.data.remote.dto.ChatMemberResponse
import com.moyeota.data.remote.dto.ChatRoomUserResponse
import com.moyeota.domain.model.ChatRoomStatus
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `GET /chat-rooms/me` 가 방 정보와 참여자까지 담게 되면서(서버 2026-09-14) 앱은 목록을 **한 번만** 부른다.
 * 필드를 주지 않는 구버전 서버에서도 깨지지 않아야 배포 순서에 상관없이 동작한다.
 */
class ChatRoomListMappingTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `방 정보가 함께 오면 목록 항목만으로 방을 만든다`() {
        val body = """
            [{"chatRoomId":26,"departure":"서면역","destination":"해운대역","status":"ACTIVE",
              "lastReadMessageId":null,"unreadCount":2,"notificationMuted":false,
              "joinedAt":"2026-09-15T02:00:00Z","lastMessage":null,
              "members":[{"publicId":"me-uuid","nickname":"나","active":true},
                         {"publicId":"peer","nickname":"폴링라","active":true}]}]
        """.trimIndent()

        val item = json.decodeFromString<List<ChatRoomUserResponse>>(body).single()
        val room = item.toChatRoomOrNull() ?: error("방 정보가 있어야 한다")

        assertEquals(26L, room.id)
        assertEquals("서면역", room.departure)
        assertEquals("해운대역", room.destination)
        assertEquals(ChatRoomStatus.ACTIVE, room.status)

        val membership = item.toMembership(myUuid = "me-uuid")
        assertEquals(2, membership.members.size)
        assertTrue(membership.members.first { it.publicId == "me-uuid" }.isMe)
        assertEquals("폴링라", membership.members.first { it.publicId == "peer" }.nickname)
    }

    @Test
    fun `구버전 서버는 방 정보가 없어 null 이고 참여자도 비어 있다`() {
        val legacy = ChatRoomUserResponse(chatRoomId = 26, unreadCount = 0)

        assertNull(legacy.toChatRoomOrNull())
        assertTrue(legacy.toMembership(myUuid = "me-uuid").members.isEmpty())
    }

    /** 출발지만 오고 도착지가 없는 반쪽 응답은 쓰지 않는다 — 제목이 반쪽으로 그려지느니 상세를 다시 읽는다 */
    @Test
    fun `방 정보가 반쪽이면 쓰지 않는다`() {
        val half = ChatRoomUserResponse(chatRoomId = 26, departure = "서면역", destination = null)

        assertNull(half.toChatRoomOrNull())
    }

    /**
     * 목록 매퍼는 `partyId` 를 **모른다** — 응답에 그 필드가 없다.
     *
     * [UNKNOWN_PARTY_ID](0)는 「없음」을 적은 자리값이고 **비교에 쓰면 안 되는 값**이다. 예전에
     * 화면이 `partyId.toString() == activePartyId` 로 「진행 중인 방인가」를 가렸는데, 목록에서 연 방은
     * 늘 0 이라 그 판정이 **언제나 거짓**이었다 — 진행 중인 방인데도 되돌릴 수 없는 「나가기」가
     * 확인 없이 노출됐다(실기 QA). 지금은 채팅방 id 로 가린다(`ChatRoute.isActiveParty`).
     */
    @Test
    fun `목록 매퍼는 partyId 를 모른다`() {
        val item = ChatRoomUserResponse(
            chatRoomId = 26,
            departure = "서면역",
            destination = "해운대역",
            status = "ACTIVE",
        )

        val room = item.toChatRoomOrNull() ?: error("방 정보가 있어야 한다")

        assertEquals("목록 응답에 partyId 가 없으므로 「모른다」여야 한다", UNKNOWN_PARTY_ID, room.partyId)
    }

    /**
     * 닉네임이 `null` 인 참여자 하나로 **목록 전체**가 깨지면 안 된다.
     *
     * 서버 `users.nickname` 에는 NOT NULL 제약이 없고(마이그레이션 V1~V9 어디에도 없다) `User` 엔티티에
     * `nickname = null` 로 사용자를 만드는 정식 경로(소셜 가입)가 있다. DTO 를 논-널로 선언하면
     * `kotlinx.serialization` 이 그 자리에서 예외를 던지고(`ignoreUnknownKeys` 는 모르는 **필드**를
     * 흘려보내는 설정이고 `coerceInputValues` 는 꺼져 있다) `GET /chat-rooms/me` **응답 전체**가 깨져
     * 채팅 탭이 영구 에러가 된다 — 거기서 진행 중인 방을 찾는 `RemoteActivePartyRepository` 까지
     * 빈손이 되어 앱을 다시 설치한 사용자의 방 복귀 경로가 끊긴다.
     *
     * 지금은 소셜 로그인이 운영에 없어 터지지 않지만, 붙이는 날 터진다. 그래서 미리 못 박는다.
     */
    @Test
    fun `닉네임이 null 인 참여자가 있어도 목록이 열린다`() {
        val body = """
            [{"chatRoomId":26,"departure":"서면역","destination":"해운대역","status":"ACTIVE",
              "lastReadMessageId":null,"unreadCount":0,"notificationMuted":false,
              "joinedAt":"2026-09-15T02:00:00Z","lastMessage":null,
              "members":[{"publicId":"me-uuid","nickname":"나","active":true},
                         {"publicId":"peer","nickname":null,"active":true}]}]
        """.trimIndent()

        val item = json.decodeFromString<List<ChatRoomUserResponse>>(body).single()
        val members = item.toMembership(myUuid = "me-uuid").members

        assertEquals(2, members.size)
        // 「탈퇴」가 아니라 「닉네임 미설정」이라 빈 문자열로 떨어진다 — 읽는 쪽이 「동승자」로 부른다
        // (ChatRoute.chatRoomPeerTitle 의 PEER_FALLBACK_NAME).
        assertEquals("", members.first { it.publicId == "peer" }.nickname)
        assertTrue(members.first { it.publicId == "me-uuid" }.isMe)
    }
}
