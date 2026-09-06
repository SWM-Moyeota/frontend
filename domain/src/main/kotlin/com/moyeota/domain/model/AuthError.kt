package com.moyeota.domain.model

/**
 * 인증 실패 사유. 서버의 `UserErrorCode` + 전송 계층 실패를 화면이 분기 가능한 형태로 좁힌 것.
 * ViewModel 은 이 값으로 한국어 안내 문구를 고른다.
 */
enum class AuthError {
    /** 401 LOGIN_FAILED — 아이디나 비밀번호가 다르다. */
    LOGIN_FAILED,

    /** 409 LOGIN_ID_DUPLICATED — 이미 존재하는 아이디. */
    LOGIN_ID_DUPLICATED,

    /**
     * 400 INVALID_REQUEST — 서버 검증 실패. 서버 message 가 "필드: 사유" 형태라
     * [AuthException.serverMessage] 를 그대로 보여줘도 사용자에게 의미가 통한다.
     */
    INVALID_REQUEST,

    /**
     * 리프레시 재발급까지 실패해 세션이 끝났다(만료·회전·로그아웃·탈퇴).
     * 서버가 사유를 401 하나로 뭉치므로 앱의 대응도 "재로그인" 하나다.
     */
    SESSION_EXPIRED,

    /** 연결 실패·타임아웃 등 응답을 받지 못한 경우. 재시도 안내 대상. */
    NETWORK,

    /** 5xx 또는 해석 불가. */
    UNKNOWN,
}

/**
 * [AuthRepository][com.moyeota.domain.repository.AuthRepository] 의 모든 실패는 이 예외로 통일된다.
 * data 계층의 HttpException/IOException 은 여기서 막히고 presentation 까지 새지 않는다.
 */
class AuthException(
    val error: AuthError,
    /** 서버가 준 원문 message. 없으면 null. */
    val serverMessage: String? = null,
    cause: Throwable? = null,
) : RuntimeException(serverMessage ?: error.name, cause)
