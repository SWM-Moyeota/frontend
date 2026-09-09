package com.moyeota.domain.repository

import com.moyeota.domain.model.ChatMember
import com.moyeota.domain.model.ChatMessage
import com.moyeota.domain.model.ChatMessagePage
import com.moyeota.domain.model.ChatRoom
import com.moyeota.domain.model.MyChatRoom

/**
 * REST 전용. STOMP(WebSocket) 실시간 수신은 이번 범위 밖이라 폴링/재조회로 대체한다.
 *
 * 요청 주체는 전부 **Bearer 토큰**이 정한다 — 채팅 컨트롤러도 `@CurrentUser` 로 전환돼
 * 더 이상 userId 를 파라미터로 받지 않는다. 실패는 전부
 * [ChatException][com.moyeota.domain.model.ChatException] 으로 좁혀 올라온다.
 */
interface ChatRepository {

    /**
     * GET /api/v1/chat-rooms/me.
     * 서버는 membership 만 주고 방 이름을 주지 않아, 각 방 상세를 합쳐서 돌려준다(방 수만큼 추가 요청).
     * 상세 조회에 실패한 방은 목록에서 제외한다 — 종료된 방 때문에 목록 전체가 깨지지 않게 한다.
     */
    suspend fun getMyChatRooms(): List<MyChatRoom>

    /** GET /api/v1/chat-rooms/{chatRoomId} */
    suspend fun getChatRoom(chatRoomId: Long): ChatRoom

    /** POST /api/v1/chat-rooms (201). 같은 partyId 로 두 번 만들면 409 CHAT_ROOM_ALREADY_EXISTS. */
    suspend fun createChatRoom(partyId: Long, departure: String, destination: String): ChatRoom

    /** DELETE /api/v1/chat-rooms/{chatRoomId} (204). 방 자체를 CLOSED 로 만든다(탑승 종료 시). */
    suspend fun closeChatRoom(chatRoomId: Long)

    /** POST /api/v1/chat-rooms/{chatRoomId}/users (201). 합류 시 호출. */
    suspend fun joinChatRoom(chatRoomId: Long)

    /** DELETE /api/v1/chat-rooms/{chatRoomId}/users. 나 혼자 방에서 나간다(방은 유지). */
    suspend fun leaveChatRoom(chatRoomId: Long)

    // GET /api/v1/chat-rooms/{chatRoomId}/users — 방 참여자 목록(나간 사람은 active=false 로 포함).
    // 저장소는 이 목록을 방별로 캐시해 메시지의 senderId → 닉네임/내 메시지 여부를 푼다. 화면이
    // 직접 부를 일은 아직 없지만(참여자 N명 표시 등), 판정의 근거를 계약으로 드러내 둔다.
    // 참여자가 아니면 403 CHAT_NOT_PARTICIPANT.
    suspend fun getChatRoomMembers(chatRoomId: Long): List<ChatMember>

    /** POST /api/v1/chat-rooms/{chatRoomId}/users/read/{readMessageId}. 성공 후 목록의 안읽음 배지 갱신 필요. */
    suspend fun markAsRead(chatRoomId: Long, readMessageId: Long)

    /**
     * GET /api/v1/chat-rooms/{chatRoomId}/messages — cursor 이전(과거) 방향 페이징.
     * 최초 진입 시 cursor = null. 이후 응답의 nextCursor 를 넘겨 위로 더 불러온다.
     */
    suspend fun getMessages(
        chatRoomId: Long,
        cursor: Long? = null,
        size: Int = DEFAULT_PAGE_SIZE,
    ): ChatMessagePage

    /**
     * GET /api/v1/chat-rooms/{chatRoomId}/messages/after — cursor 이후(신규) 방향.
     * STOMP 미구현 상태에서 새 메시지를 받아오는 수단이다(마지막 메시지 id 를 cursor 로 폴링).
     */
    suspend fun getMessagesAfter(
        chatRoomId: Long,
        cursor: Long,
        size: Int = DEFAULT_PAGE_SIZE,
    ): ChatMessagePage

    /**
     * POST /api/v1/chat-rooms/{chatRoomId}/messages (201). 내용은 1~1000자, 빈 문자열이면 400.
     *
     * 부수효과가 하나 있다: 응답의 내부 userId 를 "나"로 학습해, 참여자 목록을 쓸 수 없는 서버에서도
     * 이후 [ChatMessage.isMine] 판정이 맞아 들어간다([RemoteChatRepository][com.moyeota.data.repository.RemoteChatRepository]).
     */
    suspend fun sendMessage(chatRoomId: Long, content: String): ChatMessage

    /** DELETE /api/v1/chat-rooms/{chatRoomId}/messages/{messageId} (204). 본인 메시지만 가능(403 CHAT_NOT_MESSAGE_OWNER). */
    suspend fun deleteMessage(chatRoomId: Long, messageId: Long)

    /**
     * GET /api/v1/chat-rooms/{chatRoomId}/messages/search — 방 안 키워드 검색.
     * keyword 는 **2자 이상**이어야 한다(미만이면 400 CHAT_INVALID_KEYWORD).
     * 아직 화면이 없다 — 채팅방 검색 UI 가 붙을 때 쓰기 위해 계약만 열어 둔다.
     */
    suspend fun searchMessages(
        chatRoomId: Long,
        keyword: String,
        cursor: Long? = null,
        size: Int = DEFAULT_PAGE_SIZE,
    ): ChatMessagePage

    companion object {
        const val DEFAULT_PAGE_SIZE = 30
    }
}
