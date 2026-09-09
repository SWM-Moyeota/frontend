package com.moyeota.domain.model

import java.time.LocalDate

enum class Gender { MALE, FEMALE }

/**
 * 회원가입 요청. 백엔드 `UserRegisterRequest` 와 필드 1:1.
 *
 * [birthDate] 는 도메인에서 날짜(LocalDate)로만 다룬다 — 서버가 요구하는 Instant 로의 변환은
 * data 매퍼가 UTC 자정 기준으로 처리한다. 화면이 타임존을 신경 쓸 일이 없게 하기 위함이다.
 */
data class NewUser(
    val loginId: String,
    val password: String,
    /**
     * 동승자에게 보이는 이름. **서버 필수 필드다**(`@NotBlank` + 도메인 VO `Nickname`).
     * 규칙은 [AuthPolicy.isValidNickname] — 2~10자의 한글·영문·숫자.
     *
     * 기본값을 두지 않는 건 의도다: 빈 값으로 가입을 보내면 서버가 400 으로 튕기므로,
     * 새 가입 경로가 생겼을 때 컴파일러가 값을 채우도록 강제한다.
     */
    val nickname: String,
    val name: String,
    val birthDate: LocalDate,
    val phoneNumber: String,
    val gender: Gender,
    val email: String,
)

/**
 * 서버 `UserRegisterRequest` 의 Bean Validation 규칙을 그대로 옮긴 것.
 * 화면이 제출 전에 같은 기준으로 검사해 왕복 400 을 줄인다 — 최종 판정은 어디까지나 서버다.
 */
object AuthPolicy {
    /** 영소문자로 시작하는 4~20자의 영소문자·숫자·`_` 조합. */
    const val LOGIN_ID_PATTERN = "^[a-z][a-z0-9_]{3,19}\$"

    /** 8~64자이며 영문·숫자·특수문자를 각각 하나 이상 포함. */
    const val PASSWORD_PATTERN =
        "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#\$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]).+\$"
    const val PASSWORD_MIN_LENGTH = 8
    const val PASSWORD_MAX_LENGTH = 64

    /** 하이픈은 있어도 없어도 통과한다. */
    const val PHONE_PATTERN = "^01[016-9]-?\\d{3,4}-?\\d{4}\$"

    const val NAME_MAX_LENGTH = 20
    const val EMAIL_MAX_LENGTH = 100

    /**
     * 닉네임: 2~10자의 한글·영문·숫자. 공백과 특수문자는 불가하다.
     * 서버 도메인 VO `Nickname` 의 정규식을 그대로 옮긴 것이며, 서버도 **앞뒤 공백을 strip 한 뒤** 검사한다
     * — [isValidNickname] 이 trim 하는 이유다.
     */
    const val NICKNAME_PATTERN = "^[가-힣a-zA-Z0-9]{2,10}\$"
    const val NICKNAME_MIN_LENGTH = 2
    const val NICKNAME_MAX_LENGTH = 10

    fun isValidLoginId(value: String): Boolean = value.matches(Regex(LOGIN_ID_PATTERN))

    /** 서버와 같은 기준. 통과해도 중복 여부는 별개다([AuthRepository.isNicknameTaken][com.moyeota.domain.repository.AuthRepository.isNicknameTaken]). */
    fun isValidNickname(value: String): Boolean = value.trim().matches(Regex(NICKNAME_PATTERN))

    fun isValidPassword(value: String): Boolean =
        value.length in PASSWORD_MIN_LENGTH..PASSWORD_MAX_LENGTH &&
            value.matches(Regex(PASSWORD_PATTERN))

    fun isValidPhoneNumber(value: String): Boolean = value.matches(Regex(PHONE_PATTERN))
}

/**
 * 로그인 상태. presentation 은 토큰을 절대 보지 않고 이 상태만 본다.
 *
 * [Unknown] 이 따로 있는 이유: 앱 시작 직후에는 디스크(DataStore)에서 토큰을 아직 읽지 못했다.
 * 이때 [Unauthenticated] 로 취급하면 이미 로그인한 사용자가 매번 로그인 화면을 스쳐 본다.
 * 화면은 [Unknown] 동안 스플래시/로딩을 유지해야 한다.
 */
sealed interface AuthState {
    data object Unknown : AuthState

    data object Unauthenticated : AuthState

    /** [userUuid] 는 서버가 발급한 대외 식별자(UUID 문자열). */
    data class Authenticated(val userUuid: String) : AuthState
}
