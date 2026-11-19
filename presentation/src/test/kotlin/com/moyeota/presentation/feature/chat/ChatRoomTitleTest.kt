package com.moyeota.presentation.feature.chat

import com.moyeota.domain.model.ChatLastMessage
import com.moyeota.domain.model.ChatMember
import com.moyeota.domain.model.ChatMessageType
import com.moyeota.domain.model.ChatRoom
import com.moyeota.domain.model.ChatRoomMembership
import com.moyeota.domain.model.ChatRoomStatus
import com.moyeota.domain.model.MyChatRoom
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 채팅방 이름은 **앱이 짓는다** — 서버가 방에 이름을 주지 않는다.
 * 예전 이름(출발지 → 도착지)은 역지오코딩된 전체 주소가 들어와 목록에서 방을 가려낼 수 없었다.
 */
class ChatRoomTitleTest {

    private fun member(nickname: String, isMe: Boolean = false, active: Boolean = true) =
        ChatMember(
            userId = null,
            publicId = "public-$nickname",
            nickname = nickname,
            imageUrl = null,
            active = active,
            isMe = isMe,
        )

    @Test
    fun `나를 뺀 한 명이면 그 사람 이름이 방 이름이다`() {
        val members = listOf(member("스모크일"), member("스모크이", isMe = true))
        assertEquals("스모크일", chatRoomPeerTitle(members))
    }

    @Test
    fun `둘이면 쉼표로 잇는다`() {
        val members = listOf(member("스모크일"), member("스모크삼"), member("나", isMe = true))
        assertEquals("스모크일, 스모크삼", chatRoomPeerTitle(members))
    }

    @Test
    /** 「첫 사람 외 N명」으로 줄이면 제목 옆 인원과 「외 2명 4명」으로 겹쳐 읽혔다 — 다 적고 넘치면 행이 줄인다. */
    fun `셋 이상도 이름을 모두 쉼표로 잇는다`() {
        val members = listOf(
            member("스모크일"),
            member("스모크삼"),
            member("스모크사"),
            member("나", isMe = true),
        )
        assertEquals("스모크일, 스모크삼, 스모크사", chatRoomPeerTitle(members))
    }

    @Test
    fun `나 혼자면 동승자 없음`() {
        assertEquals("동승자 없음", chatRoomPeerTitle(listOf(member("나", isMe = true))))
    }

    @Test
    fun `방을 나간 사람은 이름에서 뺀다`() {
        val members = listOf(
            member("남은사람"),
            member("나간사람", active = false),
            member("나", isMe = true),
        )
        assertEquals("남은사람", chatRoomPeerTitle(members))
    }

    /**
     * 전원이 나갔다고 「동승자 없음」이라 부르면, 대화 상대가 분명히 있었던 방의 이름이 사라진다.
     * 남은 사람이 없을 때만 나간 사람을 되살려 쓴다.
     */
    @Test
    fun `남은 사람이 없으면 나간 사람이라도 쓴다`() {
        val members = listOf(member("나간사람", active = false), member("나", isMe = true))
        assertEquals("나간사람", chatRoomPeerTitle(members))
    }

    @Test
    fun `닉네임이 빈 문자열이면 동승자로 적는다`() {
        val members = listOf(member(""), member("나", isMe = true))
        assertEquals("동승자", chatRoomPeerTitle(members))
    }

    /** 정렬 키는 **마지막 활동 시각** — 마지막 메시지 시각, 없으면 방 개설 시각. 카카오톡식 최근 대화 순. */
    @Test
    fun `마지막 메시지가 최근인 방이 방 id 와 무관하게 위로 온다`() {
        val rooms = listOf(
            myRoom(1, lastMessageAt = "2026-09-11T03:00:00Z"),
            myRoom(9, lastMessageAt = "2026-09-10T03:00:00Z"),
            myRoom(4, lastMessageAt = "2026-09-11T01:00:00Z"),
        )
        assertEquals(listOf(1L, 4L, 9L), rooms.sortedByDescending(::chatRoomSortKey).map { it.room.id })
    }

    @Test
    fun `메시지가 없는 새 방은 개설 시각으로 끼어든다`() {
        val rooms = listOf(
            myRoom(1, lastMessageAt = "2026-09-11T03:00:00Z"),
            myRoom(2, createdAt = "2026-09-11T04:00:00Z"), // 방금 열린 방, 메시지 없음
            myRoom(3, lastMessageAt = "2026-09-11T02:00:00Z"),
        )
        assertEquals(listOf(2L, 1L, 3L), rooms.sortedByDescending(::chatRoomSortKey).map { it.room.id })
    }

    @Test
    fun `미리보기는 마지막 메시지이고 위치 공유는 사람 말로 바꾼다`() {
        val text = ChatRoomListItem(myRoom(1, lastMessageAt = "2026-09-11T03:00:00Z", content = "어디쯤이세요?"), "스모크일")
        assertEquals("어디쯤이세요?", chatRoomSubtitle(text).preview)

        val location = ChatRoomListItem(
            myRoom(1, lastMessageAt = "2026-09-11T03:00:00Z", content = "35.1,129.0", type = ChatMessageType.LOCATION),
            "스모크일",
        )
        assertEquals("위치를 공유했어요", chatRoomSubtitle(location).preview)
    }

    /**
     * ★ 메시지가 있어도 경로가 남는다(오너 피드백 2026-11) — 예전엔 미리보기가 경로 자리를 차지해
     * 대화가 오간 방은 어디서 어디로 가는 방인지 목록에서 알 수 없었다.
     */
    @Test
    fun `메시지가 있어도 경로 한 줄이 함께 보인다`() {
        val talked = ChatRoomListItem(myRoom(1, lastMessageAt = "2026-09-11T03:00:00Z", content = "어디쯤이세요?"), "스모크일")

        assertEquals(ChatRoomSubtitle(RouteLine("출발", "도착"), "어디쯤이세요?"), chatRoomSubtitle(talked))
    }

    @Test
    fun `메시지가 없으면 경로만 쓴다`() {
        assertEquals(ChatRoomSubtitle(RouteLine("출발", "도착"), preview = null), chatRoomSubtitle(ChatRoomListItem(myRoom(1), "스모크일")))
    }

    /** 닫힌 방의 「종료 · 」는 경로 줄이 한 번만 맡는다 — 미리보기에까지 붙으면 한 행에 두 번이다. */
    @Test
    fun `닫힌 방은 경로 줄에만 종료를 붙인다`() {
        val closed = ChatRoomListItem(
            myRoom(1, lastMessageAt = "2026-09-11T03:00:00Z", content = "감사합니다", status = ChatRoomStatus.CLOSED),
            "스모크일",
        )

        assertEquals(ChatRoomSubtitle(RouteLine("출발", "도착", closed = true), "감사합니다"), chatRoomSubtitle(closed))
    }

    /** 합승이 끝난 방(FINISHED·CANCELED)도 닫힌 방과 같이 「종료」다 — 대화는 계속돼도 탑승은 끝났다. */
    @Test
    fun `합승이 끝난 방도 경로 줄에 종료를 붙인다`() {
        for (status in listOf(ChatRoomStatus.FINISHED, ChatRoomStatus.CANCELED)) {
            assertEquals(true, chatRoomSubtitle(ChatRoomListItem(myRoom(1, status = status), "스모크일")).route?.closed)
        }
    }

    /**
     * ★ 참여자를 못 받은 방은 **제목이 이미 경로**다(「출발 → 도착」) — 그 아래에 경로를 또 쓰면
     * 같은 말이 두 번이다. 그때 경로 줄은 없고, 미리보기가 없으면 방 상태를 쓴다.
     */
    @Test
    fun `제목이 경로로 떨어진 방은 경로를 되풀이하지 않는다`() {
        val noPeers = ChatRoomListItem(myRoom(1), peerTitle = null)

        assertEquals("출발 → 도착", chatRoomListTitle(noPeers))
        assertEquals(ChatRoomSubtitle(route = null, preview = "진행 중"), chatRoomSubtitle(noPeers))
        assertEquals(
            ChatRoomSubtitle(route = null, preview = "종료된 방"),
            chatRoomSubtitle(ChatRoomListItem(myRoom(1, status = ChatRoomStatus.CLOSED), peerTitle = null)),
        )
        assertEquals(
            ChatRoomSubtitle(route = null, preview = "종료 · 감사합니다"),
            chatRoomSubtitle(
                ChatRoomListItem(
                    myRoom(1, lastMessageAt = "2026-09-11T03:00:00Z", content = "감사합니다", status = ChatRoomStatus.CLOSED),
                    peerTitle = null,
                ),
            ),
        )
    }

    @Test
    fun `경로 두 장소가 다 들어가면 그대로 쓴다`() {
        assertEquals(40 to 50, splitRouteWidths(available = 100, departure = 40, destination = 50))
    }

    /** ★ 출발지가 길어도 도착지는 다 보인다 — 한 줄을 끝에서 줄이던 때는 도착지가 통째로 사라졌다. */
    @Test
    fun `한쪽이 짧으면 짧은 쪽은 다 보이고 긴 쪽이 남은 폭을 쓴다`() {
        assertEquals(70 to 30, splitRouteWidths(available = 100, departure = 300, destination = 30))
        assertEquals(30 to 70, splitRouteWidths(available = 100, departure = 30, destination = 300))
    }

    @Test
    fun `둘 다 길면 반씩 나눠 양쪽 앞부분이 남는다`() {
        assertEquals(50 to 51, splitRouteWidths(available = 101, departure = 300, destination = 200))
    }

    @Test
    fun `쓸 폭이 없으면 둘 다 0이다`() {
        assertEquals(0 to 0, splitRouteWidths(available = 0, departure = 30, destination = 30))
    }

    @Test
    fun `참여자 이름이 있으면 그것이 제목이다`() {
        assertEquals("스모크일", chatRoomListTitle(ChatRoomListItem(myRoom(1), "스모크일")))
    }

    private fun myRoom(
        id: Long,
        createdAt: String = "2026-09-09T07:33:00Z",
        lastMessageAt: String? = null,
        content: String = "안녕하세요",
        type: ChatMessageType = ChatMessageType.TEXT,
        status: ChatRoomStatus = ChatRoomStatus.ACTIVE,
    ) = MyChatRoom(
        room = ChatRoom(
            id = id,
            partyId = id + 100,
            departure = "출발",
            destination = "도착",
            createdAt = createdAt,
            status = status,
        ),
        membership = ChatRoomMembership(
            chatRoomId = id,
            lastReadMessageId = null,
            notificationMuted = false,
            joinedAt = createdAt,
            lastMessage = lastMessageAt?.let {
                ChatLastMessage(id = id * 10, senderPublicId = "peer", content = content, type = type, createdAt = it, isMine = false)
            },
        ),
    )

}
