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
     * 409 `USER104` PHONE_NUMBER_DUPLICATED — 이미 가입된 전화번호.
     *
     * **아이디 중복과 같은 409 라 코드를 보지 않으면 구분되지 않는다.** 폴백에 맡기면
     * 전화번호가 겹쳤는데 "이미 사용 중인 아이디예요"가 떠서 사용자가 아이디만 계속 바꾸게 된다
     * (서버 검사 순서: loginId → phoneNumber → nickname).
     *
     * **2026-10 부터는 가입이 아니라 문자 발송 단계에서 먼저 난다** —
     * [AuthRepository.sendPhoneVerificationCode][com.moyeota.domain.repository.AuthRepository.sendPhoneVerificationCode]
     * 가 호출하는 서버 메서드의 첫 줄이 번호 중복 검사다(문자 비용을 쓰기 전에 끊는다).
     * 즉 화면은 **전화번호를 받은 직후**, 인증번호 입력칸을 열기 전에 이 안내를 띄워야 한다
     * — 인증번호를 기다리게 한 뒤 "이미 가입된 번호"를 말하면 사용자는 오지 않는 문자를 기다린다.
     * 가입 단계의 409 는 그 사이에 같은 번호로 누가 먼저 가입한 드문 경우에만 남는다.
     */
    PHONE_NUMBER_DUPLICATED,

    /**
     * 400 `USER107` INVALID_NICKNAME — 닉네임 형식 위반(2~10자 한글·영문·숫자가 아니다).
     *
     * [INVALID_REQUEST] 와 굳이 나눈 이유: 닉네임 중복 확인
     * ([AuthRepository.isNicknameTaken][com.moyeota.domain.repository.AuthRepository.isNicknameTaken])
     * 은 형식이 틀리면 exists 판정 전에 400 을 낸다 — 화면은 이걸 "중복"이 아니라 "형식 오류"로
     * 안내해야 한다. 서버 Bean Validation(@NotBlank/@Size) 에 먼저 걸리면 코드가
     * `INVALID_REQUEST` 로 오므로 화면 문구는 양쪽 다 형식 안내로 수렴시키는 게 안전하다.
     */
    INVALID_NICKNAME,

    /** 409 `USER108` NICKNAME_DUPLICATED — 이미 사용 중인 닉네임. 가입과 중복 확인 양쪽에서 나온다. */
    NICKNAME_DUPLICATED,

    /**
     * 400 `USER105` INVALID_PHONE_NUMBER — 전화번호 형식 위반.
     *
     * 서버가 번호를 `PhoneNumber` VO 로 만들다가 내는 코드다. **문자 인증 확인
     * ([AuthRepository.verifyPhoneCode][com.moyeota.domain.repository.AuthRepository.verifyPhoneCode])
     * 쪽에서 주로 보인다** — 발송 요청에는 Bean Validation `@Pattern` 이 걸려 있어 같은 상황이
     * [INVALID_REQUEST] 로 오는데, 확인 요청에는 `@NotBlank` 만 있어 VO 까지 내려가기 때문이다.
     * 사용자에게는 양쪽 다 "번호 형식" 안내로 수렴시키는 게 맞다.
     */
    INVALID_PHONE_NUMBER,

    /**
     * 429 `USER202` SMS_COOLDOWN — 재발송 대기 중.
     * 같은 번호는 [AuthPolicy.SMS_RESEND_COOLDOWN_SECONDS][com.moyeota.domain.model.AuthPolicy.SMS_RESEND_COOLDOWN_SECONDS]
     * 초에 한 번만 받을 수 있다. 화면이 카운트다운을 걸면 이 에러는 거의 보이지 않아야 정상이다.
     */
    SMS_COOLDOWN,

    /**
     * 429 `USER203` SMS_DAILY_LIMIT_EXCEEDED — 하루 발송 한도 초과
     * ([AuthPolicy.SMS_DAILY_LIMIT][com.moyeota.domain.model.AuthPolicy.SMS_DAILY_LIMIT]회).
     *
     * **[SMS_COOLDOWN] 과 같은 429 라 코드를 보지 않으면 구분되지 않는다** — "1분 뒤 다시"로
     * 안내하면 사용자가 하루 종일 1분마다 눌러 보게 된다. 여기서는 재시도를 권하면 안 된다.
     */
    SMS_DAILY_LIMIT_EXCEEDED,

    /**
     * 400 `USER204` SMS_CODE_EXPIRED — 저장된 인증번호가 없다.
     *
     * 만료([AuthPolicy.SMS_CODE_TTL_SECONDS][com.moyeota.domain.model.AuthPolicy.SMS_CODE_TTL_SECONDS]초)
     * 뿐 아니라 **발송한 적이 없는 번호**와 **이미 한 번 인증에 성공해 코드가 지워진 번호**도 같은 코드로 온다
     * (서버가 성공 시 코드를 삭제한다). 어느 쪽이든 화면의 대응은 "재발송"이다.
     */
    SMS_CODE_EXPIRED,

    /** 400 `USER205` SMS_CODE_MISMATCH — 인증번호가 틀렸다. 남은 시도는 입력칸만 비우고 다시 받으면 된다. */
    SMS_CODE_MISMATCH,

    /**
     * 429 `USER206` SMS_TOO_MANY_ATTEMPTS — 입력 시도
     * [AuthPolicy.SMS_MAX_VERIFY_ATTEMPTS][com.moyeota.domain.model.AuthPolicy.SMS_MAX_VERIFY_ATTEMPTS]회 초과.
     *
     * **서버가 이때 코드를 지운다** — [SMS_CODE_MISMATCH] 와 달리 같은 번호를 더 입력해 봐야
     * 이후로는 [SMS_CODE_EXPIRED] 만 온다. 화면은 입력칸이 아니라 재발송으로 유도해야 한다.
     * 단 재발송에는 [SMS_COOLDOWN] 1분이 걸려 있다.
     */
    SMS_TOO_MANY_ATTEMPTS,

    /**
     * 400 `USER207` SMS_NOT_VERIFIED — 가입에 실린 `verificationToken` 이 유효하지 않다.
     *
     * 세 경우가 한 코드로 뭉쳐 온다: 토큰 만료
     * ([AuthPolicy.VERIFICATION_TOKEN_TTL_SECONDS][com.moyeota.domain.model.AuthPolicy.VERIFICATION_TOKEN_TTL_SECONDS]초),
     * 이미 가입에 성공해 소진된 토큰, **인증한 번호와 가입 번호가 다른 경우**.
     * 셋 다 화면의 대응은 하나다 — 문자 인증 단계로 되돌린다.
     */
    PHONE_NOT_VERIFIED,

    /**
     * 문자 인증을 지금 진행할 수 없다. 서버 코드 두 개를 합친 값이다 —
     * 502 `USER201` SMS_SEND_FAILED(문자 사업자 호출 실패)와 503 `USER208` SMS_UNAVAILABLE(인증 저장소 장애).
     *
     * 둘을 나누지 않은 이유는 앱의 대응이 같기 때문이다: 사용자 입력 문제가 아니고, 잠시 뒤 재시도뿐이다.
     * 참고로 발송 실패 쪽은 서버가 재발송 대기를 풀어 주므로 **1분을 기다리지 않고 바로 다시 요청할 수 있다**.
     */
    SMS_UNAVAILABLE,

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
