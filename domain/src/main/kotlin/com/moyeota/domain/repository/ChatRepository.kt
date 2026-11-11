package com.moyeota.domain.repository

import com.moyeota.domain.model.ChatMember
import com.moyeota.domain.model.ChatRealtimeEvent
import com.moyeota.domain.model.ChatMessage
import com.moyeota.domain.model.ChatReportReason
import kotlinx.coroutines.flow.Flow
import com.moyeota.domain.model.ChatMessagePage
import com.moyeota.domain.model.ChatRoom
import com.moyeota.domain.model.MyChatRoom

/**
 * 조회·상태 변경·**보내기·읽음**은 REST, 실시간 **받기**만 STOMP(WebSocket)다.
 *
 * 채팅 소켓 서버가 API 서버에서 분리되면서(2026-11) 소켓은 연결 유지와 전달만 한다 — STOMP SEND 를
 * 보내면 `ERROR SEND_NOT_SUPPORTED` 다. 그래서 예전의 `trySendMessage`·`tryMarkAsRead`(소켓 우선,
 * REST 폴백)는 지웠고 [sendMessage]·[markAsRead] 하나씩만 남았다.
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

    /**
     * POST /api/v1/chat-rooms/{chatRoomId}/users/read/{readMessageId} (200). 성공 후 목록의 안읽음 배지 갱신 필요.
     * **메시지마다 부르지 않는다** — 호출부가 약 3초에 한 번, 마지막으로 읽은 id 만 묶어 보낸다.
     * 서버는 읽음 위치를 **5초 모았다가 한 번에** 반영한다(Backend #188) — 바로 저장되지 않아도 정상이다.
     * 내 목록 조회(`GET /chat-rooms/me`) 직전에는 내 것만 먼저 반영하므로 방금 읽은 방이 안 읽음으로 보이진 않는다.
     */
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
     * 이 방의 **실시간 연결**(STOMP `/sub/chat-rooms/{id}`) — 연결됨·새 메시지·끊김 신호.
     *
     * 수집하는 동안만 연결되고, 끊기면 알아서 다시 붙는다. 실패는 흐름을 끝내지 않고 재시도로 삼킨다.
     * 서버는 지난 메시지를 다시 주지 않으므로 호출부는 [ChatRealtimeEvent.Connected] 마다 [getMessagesAfter] 로
     * 공백을 메워야 한다. 연결 중에도 [ChatMessage.seq] 가 건너뛰면 같은 조회로 그 자리를 메운다.
     * 구독이 거절되면 [ChatRealtimeEvent.Stopped] 를 내고 끝난다.
     */
    fun observeRoom(chatRoomId: Long): Flow<ChatRealtimeEvent>

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
     * POST /api/v1/chat-rooms/{chatRoomId}/messages (201). 내용은 1~1000자, 빈 문자열이면 400.
     *
     * **방마다 한 번에 하나씩, 부른 순서대로 나간다.** 앞 요청의 응답(201 또는 실패)을 받기 전에는 같은 방의
     * 다음 요청을 보내지 않는다 — REST 요청은 서로 독립이라 연달아 쏘면 다른 서버·스레드로 가서 저장 순서가
     * 뒤집힐 수 있다(STOMP 는 연결 하나라 저절로 지켜졌다). 실패해도 **다시 보내지 않는다** — 예외를 올리고
     * 다음 요청으로 넘어간다. 다시 보낼지는 사용자가 정한다.
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

    /**
     * POST /api/v1/chat-rooms/{chatRoomId}/reports (201, 본문 없음) — **메시지 1건**을 신고한다.
     *
     * **앱에 남은 유일한 신고다.** 운행 중 긴급 신고(`POST /api/v1/reports` + 112 통화 결과 저장)가
     * 따로 있었지만 기사·배차와 함께 지웠다 — 그쪽은 「파티 + 내 좌표」를 신고하고 사유 필드가 없었다.
     * 이쪽은 특정 메시지와 그 글을 쓴 사람을 가리키고 후속 통화가 없다. 그래서 신고 대상을 고를 수
     * 없는 자리(상단바 메뉴·동승자 프로필)에는 붙일 수 없다 — [chatMessageId] 가 서버에서 필수다.
     * **없어진 긴급 신고를 이 경로로 되살리지 말 것**: 본문 모양이 달라 섞으면 400 이다
     * (`AuthenticatedPathContractTest` 참고).
     *
     * - [description] 은 [ChatReportReason.ETC] 에서만 필수이며, **공백만 있어도 서버가 거절한다**.
     *   호출부가 비어 있는 값을 null 로 접어 보내므로 구현체는 빈 문자열을 그대로 싣지 않는다.
     * - [ChatReportReason.MAX_DESCRIPTION_LENGTH]자를 넘기면 400 이다.
     * - **종료된 방에서도 받는다** — 채팅방 참여 여부만 보고 파티 상태를 보지 않는다.
     *
     * 실패는 [ChatException][com.moyeota.domain.model.ChatException] 으로 온다. 화면이 분기해야
     * 하는 것은 [isAlreadyReported][com.moyeota.domain.model.ChatException.isAlreadyReported]
     * (재시도해도 같은 답이다) ·
     * [isMessageNotFound][com.moyeota.domain.model.ChatException.isMessageNotFound] ·
     * [isNotParticipant][com.moyeota.domain.model.ChatException.isNotParticipant] 다.
     */
    suspend fun reportMessage(
        chatRoomId: Long,
        chatMessageId: Long,
        reason: ChatReportReason,
        description: String? = null,
    )

    companion object {
        const val DEFAULT_PAGE_SIZE = 30
    }
}
