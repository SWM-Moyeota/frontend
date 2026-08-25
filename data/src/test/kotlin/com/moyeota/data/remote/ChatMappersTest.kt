package com.moyeota.data.remote

import com.moyeota.data.remote.dto.ChatMessageResponse
import com.moyeota.data.remote.dto.ChatMessageSliceResponse
import com.moyeota.data.remote.dto.ChatRoomResponse
import com.moyeota.data.remote.dto.ChatRoomUserResponse
import com.moyeota.domain.model.ChatMessageType
import com.moyeota.domain.model.ChatRoomStatus
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMappersTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `채팅방 응답을 도메인 ChatRoom 으로 매핑한다`() {
        val room = ChatRoomResponse(
            id = 5,
            partyId = 7,
            departure = "서울시청",
            destination = "강남역",
            createdAt = "2026-08-24T09:00:00Z",
            status = "CLOSED",
        ).toChatRoom()

        assertEquals(5L, room.id)
        assertEquals(7L, room.partyId)
        assertEquals(ChatRoomStatus.CLOSED, room.status)
        assertEquals("2026-08-24T09:00:00Z", room.createdAt)
    }

    @Test
    fun `알 수 없는 방 상태와 메시지 타입은 기본값으로 흡수한다`() {
        assertEquals(ChatRoomStatus.ACTIVE, chatRoomStatusOf("SOMETHING_NEW"))
        assertEquals(ChatMessageType.TEXT, chatMessageTypeOf("SOMETHING_NEW"))
        assertEquals(ChatMessageType.LOCATION, chatMessageTypeOf("LOCATION"))
    }

    // 서버 필드는 userId 지만 도메인은 senderId 다 — 이름이 바뀌는 유일한 지점.
    @Test
    fun `메시지 응답의 userId 를 senderId 로 매핑한다`() {
        val message = ChatMessageResponse(
            id = 20,
            chatRoomId = 5,
            userId = 3,
            content = "안녕하세요",
            type = "TEXT",
            createdAt = "2026-08-24T09:01:00Z",
            deleted = false,
        ).toChatMessage()

        assertEquals(3L, message.senderId)
        assertEquals("안녕하세요", message.content)
        assertEquals(ChatMessageType.TEXT, message.type)
    }

    @Test
    fun `삭제된 메시지는 deleted 플래그와 치환된 본문을 유지한다`() {
        val message = ChatMessageResponse(
            id = 21,
            chatRoomId = 5,
            userId = 3,
            content = "삭제된 메시지입니다",
            type = "TEXT",
            deleted = true,
        ).toChatMessage()

        assertTrue(message.deleted)
        assertEquals("삭제된 메시지입니다", message.content)
    }

    @Test
    fun `커서 페이징 응답을 ChatMessagePage 로 매핑한다`() {
        val page = ChatMessageSliceResponse(
            messages = listOf(ChatMessageResponse(id = 1), ChatMessageResponse(id = 2)),
            nextCursor = 1,
            hasNext = true,
        ).toPage()

        assertEquals(2, page.messages.size)
        assertEquals(1L, page.nextCursor)
        assertTrue(page.hasNext)
    }

    @Test
    fun `마지막 페이지는 nextCursor 가 null 이다`() {
        val page = ChatMessageSliceResponse(messages = emptyList(), nextCursor = null, hasNext = false).toPage()

        assertNull(page.nextCursor)
        assertTrue(page.messages.isEmpty())
    }

    @Test
    fun `아직 아무것도 안 읽은 참여자는 lastReadMessageId 가 null 이다`() {
        val membership = ChatRoomUserResponse(chatRoomId = 5, lastReadMessageId = null).toMembership()

        assertNull(membership.lastReadMessageId)
        assertEquals(5L, membership.chatRoomId)
        assertEquals("", membership.joinedAt)
    }

    // /chat-rooms/me 는 래핑 객체가 아니라 배열을 그대로 내려준다.
    @Test
    fun `내 채팅방 목록 응답 JSON 을 배열로 역직렬화한다`() {
        val body = """
            [{"chatRoomId":5,"lastReadMessageId":20,"notificationMuted":false,"joinedAt":"2026-08-24T09:00:00Z"}]
        """.trimIndent()

        val decoded = json.decodeFromString<List<ChatRoomUserResponse>>(body)

        assertEquals(1, decoded.size)
        assertEquals(20L, decoded[0].toMembership().lastReadMessageId)
    }
}
