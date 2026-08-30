package com.moyeota.domain.repository

import com.moyeota.domain.model.ChatMessage
import com.moyeota.domain.model.ChatMessagePage
import com.moyeota.domain.model.ChatRoom
import com.moyeota.domain.model.MyChatRoom

// REST 전용. STOMP(WebSocket) 실시간 수신은 이번 범위 밖이라 폴링/재조회로 대체한다.
interface ChatRepository {

    /**
     * GET /api/v1/chat-rooms/me (헤더 X-User-Id).
     * 서버는 membership 만 주고 방 이름을 주지 않아, 각 방 상세를 합쳐서 돌려준다(방 수만큼 추가 요청).
     * 상세 조회에 실패한 방은 목록에서 제외한다 — 종료된 방 때문에 목록 전체가 깨지지 않게 한다.
     */
    suspend fun getMyChatRooms(userId: Long): List<MyChatRoom>

    /** GET /api/v1/chat-rooms/{chatRoomId} */
    suspend fun getChatRoom(chatRoomId: Long): ChatRoom

    /** POST /api/v1/chat-rooms (201). 같은 partyId 로 두 번 만들면 실패한다. */
    suspend fun createChatRoom(partyId: Long, departure: String, destination: String): ChatRoom

    /** DELETE /api/v1/chat-rooms/{chatRoomId} (204). 방 자체를 CLOSED 로 만든다(방장/탑승 종료 시). */
    suspend fun closeChatRoom(chatRoomId: Long)

    /** POST /api/v1/chat-rooms/{chatRoomId}/users (201). 합류 시 호출. */
    suspend fun joinChatRoom(chatRoomId: Long, userId: Long)

    /** DELETE /api/v1/chat-rooms/{chatRoomId}/users. 나 혼자 방에서 나간다(방은 유지). */
    suspend fun leaveChatRoom(chatRoomId: Long, userId: Long)

    /** POST /api/v1/chat-rooms/{chatRoomId}/users/read/{readMessageId}. 성공 후 목록의 안읽음 배지 갱신 필요. */
    suspend fun markAsRead(chatRoomId: Long, userId: Long, readMessageId: Long)

    /**
     * GET /api/v1/chat-rooms/{chatRoomId}/messages — cursor 이전(과거) 방향 페이징.
     * 최초 진입 시 cursor = null. 이후 응답의 nextCursor 를 넘겨 위로 더 불러온다.
     */
    suspend fun getMessages(
        chatRoomId: Long,
        userId: Long,
        cursor: Long? = null,
        size: Int = DEFAULT_PAGE_SIZE,
    ): ChatMessagePage

    /**
     * GET /api/v1/chat-rooms/{chatRoomId}/messages/after — cursor 이후(신규) 방향.
     * STOMP 미구현 상태에서 새 메시지를 받아오는 수단이다(마지막 메시지 id 를 cursor 로 폴링).
     */
    suspend fun getMessagesAfter(
        chatRoomId: Long,
        userId: Long,
        cursor: Long,
        size: Int = DEFAULT_PAGE_SIZE,
    ): ChatMessagePage

    /** POST /api/v1/chat-rooms/{chatRoomId}/messages (201). 내용은 1~1000자, 빈 문자열이면 400. */
    suspend fun sendMessage(chatRoomId: Long, userId: Long, content: String): ChatMessage

    /** DELETE /api/v1/chat-rooms/{chatRoomId}/messages/{messageId} (204). 본인 메시지만 가능. */
    suspend fun deleteMessage(chatRoomId: Long, userId: Long, messageId: Long)

    companion object {
        const val DEFAULT_PAGE_SIZE = 30
    }
}
