package com.moyeota.domain.model

/**
 * 채팅 API 실패를 화면이 다룰 수 있는 형태로 좁힌 예외.
 *
 * 채팅은 다른 도메인과 에러 본문 형식이 다르다 — `ChatExceptionHandler` 가
 * `ErrorResponse(code, message)` 를 내려주며 [code] 는 서버 `ChatErrorCode` 의 **enum 이름**이다
 * (`CHAT_NOT_PARTICIPANT` 처럼. 인증 쪽 `USER102` 같은 번호 코드가 아니다).
 * 검증 실패만 `INVALID_REQUEST` 로 온다.
 *
 * 화면이 분기해야 하는 두 가지는 [isNotParticipant]·[isRoomClosed] 로 노출한다 —
 * 문자열 비교가 화면으로 새어 나가면 서버가 코드 이름을 바꿀 때 조용히 어긋난다.
 */
class ChatException(
    /** 서버 `ChatErrorCode` 이름. 응답 본문이 없거나 파싱 실패면 null(그때는 [httpStatus] 만 근거). */
    val code: String?,
    /** 서버가 준 한국어 원문 message. 없으면 null. */
    val serverMessage: String?,
    /** HTTP 상태 코드. 네트워크 실패 등 응답 자체가 없으면 null. */
    val httpStatus: Int? = null,
    /** 응답을 받지 못한 실패(연결 거부·타임아웃·DNS). 사용자에게는 재시도를 안내한다. */
    val isNetwork: Boolean = false,
    cause: Throwable? = null,
) : RuntimeException(serverMessage ?: code ?: "채팅 요청에 실패했습니다.", cause) {

    /** 403 `CHAT_NOT_PARTICIPANT` — 그 방의 참여자가 아니다(합류 전이거나 이미 나갔다). */
    val isNotParticipant: Boolean
        get() = code == "CHAT_NOT_PARTICIPANT" || (code == null && httpStatus == 403)

    /** 409 `CHAT_ROOM_CLOSED` — 종료된 방이라 더 이상 읽기/쓰기가 안 된다. */
    val isRoomClosed: Boolean
        get() = code == "CHAT_ROOM_CLOSED"

    /** 404 `CHAT_ROOM_NOT_FOUND` — 방이 사라졌다(목록에서 제외해야 한다). */
    val isRoomNotFound: Boolean
        get() = code == "CHAT_ROOM_NOT_FOUND" || (code == null && httpStatus == 404)

    /** 403 `CHAT_NOT_MESSAGE_OWNER` — 남의 메시지는 삭제할 수 없다. */
    val isNotMessageOwner: Boolean
        get() = code == "CHAT_NOT_MESSAGE_OWNER"

    /** 401 — 토큰이 없거나 만료됐다. 재발급까지 실패한 뒤에만 여기까지 올라온다. */
    val isUnauthorized: Boolean
        get() = code == "CHAT_UNAUTHORIZED" || httpStatus == 401
}
