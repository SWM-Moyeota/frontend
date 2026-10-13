package com.moyeota.domain.model

import java.time.LocalDate
import java.time.ZoneOffset

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
    /**
     * 문자 인증을 통과했다는 증표. **서버 필수 필드다**(`@NotBlank`) — 2026-10 서버 변경으로
     * 가입에 문자 인증이 선행 조건이 됐고, 이 값 없이 보내면 400 `INVALID_REQUEST` 다.
     *
     * 값은 [AuthRepository.verifyPhoneCode][com.moyeota.domain.repository.AuthRepository.verifyPhoneCode]
     * 가 돌려준 것을 그대로 넣는다. 서버는 **토큰에 묶인 번호와 [phoneNumber] 가 같은지까지** 본다
     * (`SmsVerificationService.checkVerified` 가 둘의 해시를 비교한다) — 인증을 끝낸 뒤
     * 가입 폼에서 번호를 고치면 400 `USER207` 로 떨어지므로, 번호를 바꾸면 인증을 다시 받아야 한다.
     *
     * **수명 10분, 가입 성공 시 1회 소진.** 서버가 토큰을 지우는 시점은 가입이 **다 저장된 뒤**다
     * (`LocalUserService.register` 의 `consume`) — 그래서 아이디·닉네임 중복(409) 같은 가입 실패
     * 뒤에는 **같은 토큰으로 바로 재시도할 수 있다**. [AuthPolicy.VERIFICATION_TOKEN_TTL_SECONDS]
     * 가 지나면 400 `USER207` 이 되고, 그때는 문자 인증을 처음부터 다시 해야 한다.
     */
    val verificationToken: String,
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

    /** 문자 인증번호는 숫자 6자리다 — 서버 `SmsVerifyRequest.code` 의 `@Pattern(^\d{6}$)`. */
    const val VERIFICATION_CODE_LENGTH = 6

    /**
     * 같은 번호로 인증번호를 다시 받을 수 있게 되는 데 걸리는 시간(초).
     * 서버 `SmsVerificationService.COOLDOWN` = 1분. 더 빨리 누르면 429 `USER202` 다.
     * 화면의 「재전송」 카운트다운이 이 값이어야 사용자가 헛되게 누르지 않는다.
     */
    const val SMS_RESEND_COOLDOWN_SECONDS = 60

    /**
     * 받은 인증번호가 유효한 시간(초). 서버 `SmsVerificationService.CODE_TTL` = 3분.
     * 지나면 400 `USER204`([AuthError.SMS_CODE_EXPIRED]) 이고 재발송이 필요하다.
     */
    const val SMS_CODE_TTL_SECONDS = 180

    /**
     * 인증 성공으로 받은 `verificationToken` 의 수명(초). 서버 `VERIFIED_TTL` = 10분.
     * 이 안에 가입을 끝내야 한다 — 지나면 가입이 400 `USER207` 로 떨어진다([NewUser.verificationToken]).
     */
    const val VERIFICATION_TOKEN_TTL_SECONDS = 600

    /** 한 번호가 24시간 동안 받을 수 있는 인증번호 횟수. 서버 `DAILY_LIMIT` = 5, 초과 시 429 `USER203`. */
    const val SMS_DAILY_LIMIT = 5

    /** 한 인증번호에 허용되는 입력 시도 횟수. 서버 `MAX_ATTEMPTS` = 5, 다섯 번째를 틀리면 코드가 무효가 된다. */
    const val SMS_MAX_VERIFY_ATTEMPTS = 5

    /**
     * 탈퇴한 번호로 다시 가입할 수 없는 기간(일). 서버 `UserDeletions.RETENTION` = 30일.
     *
     * 서버가 이 값을 **두 곳에 같이 쓴다**: 재가입 차단 판정(`existsDeletedSince`)과 탈퇴 번호 기록
     * 삭제(`UserDeletionSweeper`, 매일 04:00 KST). 그래서 "차단 기간"과 "보관 기간"이 같은 수다
     * — 탈퇴 안내 문구에 보관 기간을 따로 적을 필요가 없다.
     *
     * 쓸 곳이 두 군데다: 탈퇴 화면의 경고 문구와 가입 실패([AuthError.REJOIN_RESTRICTED]) 안내.
     * 두 화면이 서로 다른 숫자를 말하면 둘 중 하나는 거짓말이 되므로 한 상수를 본다.
     *
     * 쓸어내기가 하루 한 번이라 **실제로는 최대 하루 더 막힐 수 있다**(서버 KDoc 이 명시한다) —
     * 문구에 "정확히 30일 뒤"라고 약속하지 않는 이유다.
     */
    const val REJOIN_RESTRICTED_DAYS = 30

    /**
     * 가입할 수 있는 **최소 만 나이**. 판정은 [isOldEnough] 가 한다.
     *
     * 근거는 개인정보 보호법이다 — 만 14세 미만의 개인정보는 법정대리인 동의 없이 처리할 수 없다.
     * 동의 절차를 만드는 대신 **가입 자체를 막기로** 했다(서비스 특성상 미성년 단독 동승도 위험하다).
     * 개인정보 처리방침에도 「만 14세 미만은 가입할 수 없습니다」로 고지하므로 **고지와 동작이 어긋나면
     * 안 된다** — 화면 문구도 이 상수를 끼워 쓴다(`birthDateError`). 숫자를 한 곳에서만 고치게 하는 이유다.
     *
     * **서버에는 이 제한이 없다**(`UserRegisterRequest` 는 `birthDate` 에 `@Past` 뿐이다).
     * 즉 이 판정은 앱만의 방어선이고, API 를 직접 호출하면 우회된다 — 서버에도 같은 검증이 필요하다.
     */
    const val MIN_AGE = 14

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

    /**
     * 전화번호를 **서버에 보낼 단일 형태**(하이픈·공백 없는 숫자열)로 맞춘다.
     *
     * 문자 발송·인증 확인·가입 세 곳이 같은 번호를 보내야 하는데, 서버는 각 요청마다 번호를
     * `PhoneNumber` VO 로 다시 만들어(`value.replace("-", "").strip()`) **하이픈을 뗀 값의 HMAC 해시**로
     * 인증 상태를 찾는다. 즉 서버 기준으로는 하이픈 유무가 결과를 바꾸지 않지만, 앱이 화면마다
     * 다른 형태를 보내면 "왜 인증은 됐는데 가입이 안 되지"를 디버깅할 때 번호 표기부터 의심하게 된다.
     * 저장되는 값도 하이픈 없는 형태이므로(`UserProfile.of` 가 같은 VO 를 쓴다) 거기에 맞춘다.
     *
     * 화면 표시용 하이픈 포맷은 presentation 몫이다 — 이 함수의 결과는 **전송용**이다.
     */
    fun normalizePhoneNumber(value: String): String =
        value.filterNot { it == '-' || it.isWhitespace() }

    /** 인증번호 형식: 숫자 [VERIFICATION_CODE_LENGTH] 자리. 서버도 같은 기준으로 400 을 낸다. */
    fun isValidVerificationCode(value: String): Boolean =
        value.length == VERIFICATION_CODE_LENGTH && value.all { it.isDigit() }

    /**
     * 생년월일이 서버 `@Past` 를 통과하는지. 서버는 **Instant** 로 비교하고, 앱은 날짜를
     * UTC 자정으로 올려 보내므로(data 매퍼) "오늘"은 타임존에 따라 과거가 아닐 수 있다
     * — 09:00 KST 이전에 가입하면 오늘의 UTC 자정이 아직 미래라 400 이 난다.
     * 그 틈을 아예 막으려고 **UTC 기준 오늘보다 이른 날짜만** 통과시킨다.
     */
    fun isValidBirthDate(value: LocalDate): Boolean = value.isBefore(LocalDate.now(ZoneOffset.UTC))

    /**
     * [birthDate] 가 [today] 기준 만 [MIN_AGE] 세 이상인지 — **생일 당일부터 통과한다.**
     *
     * 만 나이이므로 연도만 빼면 안 된다: 2012-12-31 생은 2026-01-01 에 아직 13세다.
     * 그래서 "[birthDate] 의 [MIN_AGE] 년 뒤 날짜가 오늘까지 왔는지"로 본다 —
     * `Period.between(birthDate, today).years >= MIN_AGE` 와 **단 하루를 빼고 같은 판정**이다.
     * 그 하루는 윤년 2월 29일생의 평년 생일이다: [LocalDate.plusYears] 는 해당일이 없으면 그 달의
     * 말일로 맞춰(2008-02-29 + 14년 = 2022-02-28) **민법 제160조 ③과 같게** 2월 28일에 나이를 먹는데,
     * `Period` 로 세면 그날 13세로 떨어져 법적으로 14세인 사람을 하루 더 막는다. 막는 쪽이 아니라
     * 맞는 쪽을 골랐다. 어느 입력에도 예외를 던지지 않는다(없는 날짜는 애초에 [LocalDate] 가 못 된다).
     *
     * [today] 를 인자로 받는 이유: 이 함수의 값어치는 **경계값**(생일 하루 전/당일/하루 뒤)에 있는데
     * 안에서 `LocalDate.now()` 를 부르면 기기 시계에 묶여 그 셋을 검증할 방법이 없다.
     * 호출부(화면)가 `LocalDate.now()` 를 넘긴다.
     *
     * [isValidBirthDate] 와 **역할이 겹치지 않게** 나눠 둔 것이다:
     *
     * | | [isValidBirthDate] | [isOldEnough] |
     * |---|---|---|
     * | 근거 | 서버 `UserRegisterRequest` 의 `@Past` | 개인정보 보호법 + 앱 정책([MIN_AGE]) |
     * | 막는 값 | 보내면 **400 이 되는** 값(미래·오늘) | 보내면 **200 이지만 받아선 안 되는** 가입 |
     * | 기준 시각 | UTC 오늘(서버가 Instant 로 비교한다) | 호출부가 넘긴 오늘(사용자가 사는 지역의 날짜) |
     * | 사용자가 할 일 | 날짜를 고친다 | 고칠 게 없다 — 가입이 안 된다 |
     *
     * 만 [MIN_AGE] 세 이상이면 당연히 과거 날짜라 포함 관계처럼 보이지만 합치면 안 된다. 기준 시각이 다르고
     * (서버 타임존 vs 사용자 지역), 바뀌는 이유가 다르고(서버 검증 vs 법·정책), 무엇보다 **사용자에게
     * 할 말이 다르다.** 2005 를 2025 로 잘못 친 오타와 "정말 어리다"를 한 문구로 뭉개면 고칠 곳을 못 찾는다.
     */
    fun isOldEnough(birthDate: LocalDate, today: LocalDate): Boolean =
        !today.isBefore(birthDate.plusYears(MIN_AGE.toLong()))
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
