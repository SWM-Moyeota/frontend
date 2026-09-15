package com.moyeota.domain.repository

import com.moyeota.domain.model.ChatMember
import com.moyeota.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow
import com.moyeota.domain.model.ChatMessagePage
import com.moyeota.domain.model.ChatRoom
import com.moyeota.domain.model.MyChatRoom

/**
 * 조회·상태 변경은 REST, 대화의 실시간 구간(수신·전송·읽음)은 STOMP(WebSocket)다.
 * 소켓이 붙어 있지 않을 때를 위해 같은 일을 하는 REST 경로를 함께 둔다 — [trySendMessage] 가
 * 거짓을 돌려주면 [sendMessage] 로 보낸다.
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
    // 저장소는 이 목록을 방별로 캐시해 메시지의 발신자 publicId → 닉네임을 푼다("내 메시지" 판정은
    // 메시지 자체의 publicId 로 끝나므로 이 목록이 없어도 정확하다). 화면이 직접 부를 일은 아직
    // 없지만(참여자 N명 표시 등), 판정의 근거를 계약으로 드러내 둔다.
    // 참여자가 아니면 403 CHAT_NOT_PARTICIPANT.
    suspend fun getChatRoomMembers(chatRoomId: Long): List<ChatMember>

    /**
     * 이 방의 **푸시 알림** 끄기/켜기 — POST(끄기) / DELETE(켜기) `/api/v1/chat-rooms/{chatRoomId}/users/notification/mute` (200).
     * 서버는 음소거한 참여자에게 새 메시지 푸시(`CHAT_MESSAGE`)를 보내지 않는다. 방 목록의
     * [ChatRoomMembership.notificationMuted][com.moyeota.domain.model.ChatRoomMembership.notificationMuted] 로 현재 상태를 읽는다.
     * 참여자가 아니면 403 CHAT_NOT_PARTICIPANT.
     */
    suspend fun setNotificationMuted(chatRoomId: Long, muted: Boolean)

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
     * 이 방의 **새 메시지 실시간 스트림**(STOMP `/sub/chat-rooms/{id}`).
     *
     * 수집하는 동안만 연결되고, 끊기면 알아서 다시 붙는다. 실패는 흐름을 끝내지 않고 재시도로 삼킨다 —
     * 실시간은 **폴링을 앞설 뿐 대체하지 않는다.** 재연결 사이에 오간 메시지는 이 흐름으로 오지 않으므로
     * 호출부는 [getMessagesAfter] 로 메워야 한다.
     */
    fun observeMessages(chatRoomId: Long): Flow<ChatMessage>

    /**
     * GET /api/v1/chat-rooms/{chatRoomId}/messages/after — cursor 이후(신규) 방향.
     * 소켓이 끊겼거나 재연결 사이에 오간 메시지를 메우는 수단이다(마지막 메시지 id 를 cursor 로 폴링).
     */
    suspend fun getMessagesAfter(
        chatRoomId: Long,
        cursor: Long,
        size: Int = DEFAULT_PAGE_SIZE,
    ): ChatMessagePage

    /**
     * 실시간 연결로 메시지를 보낸다 — STOMP SEND `/pub/chat-rooms/{id}/messages`.
     *
     * **응답이 없다.** 서버 핸들러는 void 라 저장 결과가 프레임으로 돌아오지 않고, 저장된 메시지가
     * [observeMessages] 로 온다(보낸 사람도 같은 구독을 받는다). 그래서 참을 돌려줘도 "저장됐다"가 아니라
     * **"살아 있는 연결에 써 넣었다"**는 뜻이다 — 화면은 되돌아온 메시지를 확인으로 삼고, 오지 않으면
     * 실패로 보여야 한다.
     *
     * 연결이 없으면 거짓을 돌려준다. 그때는 [sendMessage] 로 보낸다 — **둘 중 하나만** 써야 한다.
     * 보낸 뒤 확인이 늦다고 REST 로 다시 보내면 같은 메시지가 두 번 저장된다.
     */
    suspend fun trySendMessage(chatRoomId: Long, content: String): Boolean = false

    /**
     * 읽음 지점을 실시간 연결로 알린다 — STOMP SEND `/pub/chat-rooms/{id}/read`.
     * 새 메시지마다 한 번씩 일어나는 일이라 소켓이 붙어 있으면 REST 왕복을 아낀다.
     * 연결이 없으면 거짓 — 그때는 [markAsRead] 를 쓴다.
     */
    suspend fun tryMarkAsRead(chatRoomId: Long, readMessageId: Long): Boolean = false

    /**
     * POST /api/v1/chat-rooms/{chatRoomId}/messages (201). 내용은 1~1000자, 빈 문자열이면 400.
     * 실시간 연결이 없을 때의 경로다 — 연결돼 있으면 [trySendMessage] 가 먼저다.
     *
     * 응답은 다른 조회와 같은 메시지 shape 이며 발신자 publicId 가 실려 있으므로
     * 방금 보낸 메시지부터 [ChatMessage.isMine] 이 참이다(예전의 "내 id 학습" 부수효과는 사라졌다).
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
