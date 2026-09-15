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
}
