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
        assertEquals("어디쯤이세요?", chatRoomSubtitle(text))

        val location = ChatRoomListItem(
            myRoom(1, lastMessageAt = "2026-09-11T03:00:00Z", content = "35.1,129.0", type = ChatMessageType.LOCATION),
            "스모크일",
        )
        assertEquals("📍 위치를 공유했어요", chatRoomSubtitle(location))

        val none = ChatRoomListItem(myRoom(1), "스모크일")
        assertEquals("출발 → 도착", chatRoomSubtitle(none))
    }

    private fun myRoom(
        id: Long,
        createdAt: String = "2026-09-09T07:33:00Z",
        lastMessageAt: String? = null,
        content: String = "안녕하세요",
        type: ChatMessageType = ChatMessageType.TEXT,
    ) = MyChatRoom(
        room = ChatRoom(
            id = id,
            partyId = id + 100,
            departure = "출발",
            destination = "도착",
            createdAt = createdAt,
            status = ChatRoomStatus.ACTIVE,
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
