package com.moyeota.presentation.feature.chat

import com.moyeota.domain.model.ChatLastMessage
import com.moyeota.domain.model.ChatMember
import com.moyeota.domain.model.ChatMessageType
import com.moyeota.domain.model.ChatRoom
import com.moyeota.domain.model.ChatRoomMembership
import com.moyeota.domain.model.ChatRoomStatus
import com.moyeota.domain.model.MyChatRoom
import com.moyeota.presentation.core.RoutePlace
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
    fun `둘이면 가운뎃점으로 잇는다`() {
        val members = listOf(member("스모크일"), member("스모크삼"), member("나", isMe = true))
        assertEquals("스모크일 · 스모크삼", chatRoomPeerTitle(members))
    }

    @Test
    fun `셋 이상은 한 줄을 넘겨 첫 사람 외 N명으로 줄인다`() {
        val members = listOf(
            member("스모크일"),
            member("스모크삼"),
            member("스모크사"),
            member("나", isMe = true),
        )
        assertEquals("스모크일 외 2명", chatRoomPeerTitle(members))
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
    fun `부제는 마지막 메시지가 우선이고 위치 공유는 사람 말로 바꾼다`() {
        val text = ChatRoomListItem(myRoom(1, lastMessageAt = "2026-09-11T03:00:00Z", content = "어디쯤이세요?"), "스모크일")
        assertEquals(ChatRoomSubtitle.Line("어디쯤이세요?"), chatRoomSubtitle(text))

        val location = ChatRoomListItem(
            myRoom(1, lastMessageAt = "2026-09-11T03:00:00Z", content = "35.1,129.0", type = ChatMessageType.LOCATION),
            "스모크일",
        )
        assertEquals(ChatRoomSubtitle.Line("위치를 공유했어요"), chatRoomSubtitle(location))
    }

    /**
     * 메시지가 없으면 경로를 **두 줄**로 쓴다 — 「출발지: …」·「도착지: …」(오너 UX 피드백 2026-11).
     * 한 줄 「출발 → 도착」은 장소 이름이 길면 화살표 뒤가 잘려 도착지가 통째로 사라졌다.
     */
    @Test
    fun `메시지가 없으면 출발지와 도착지를 두 줄로 쓴다`() {
        val none = ChatRoomListItem(myRoom(1), "스모크일")

        assertEquals(
            ChatRoomSubtitle.Route(listOf(RoutePlace("출발지", "출발"), RoutePlace("도착지", "도착"))),
            chatRoomSubtitle(none),
        )
    }

    @Test
    fun `닫힌 방의 경로 두 줄은 첫 줄에 종료를 붙인다`() {
        val closed = ChatRoomListItem(myRoom(1, status = ChatRoomStatus.CLOSED), "스모크일")

        assertEquals(
            ChatRoomSubtitle.Route(listOf(RoutePlace("종료 · 출발지", "출발"), RoutePlace("도착지", "도착"))),
            chatRoomSubtitle(closed),
        )
    }

    /**
     * ★ 참여자를 못 받은 방은 **제목이 이미 경로**다(「출발 → 도착」) — 그 아래에 출발지·도착지를 또 쓰면
     * 같은 말이 세 줄이 된다. 그때 부제는 방 상태 한 줄이다.
     */
    @Test
    fun `제목이 경로로 떨어진 방은 경로를 되풀이하지 않는다`() {
        val noPeers = ChatRoomListItem(myRoom(1), peerTitle = null)

        assertEquals("출발 → 도착", chatRoomListTitle(noPeers))
        assertEquals(ChatRoomSubtitle.Line("진행 중"), chatRoomSubtitle(noPeers))
        assertEquals(
            ChatRoomSubtitle.Line("종료된 방"),
            chatRoomSubtitle(ChatRoomListItem(myRoom(1, status = ChatRoomStatus.CLOSED), peerTitle = null)),
        )
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
