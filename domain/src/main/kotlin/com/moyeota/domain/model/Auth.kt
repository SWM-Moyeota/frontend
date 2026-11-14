package com.moyeota.domain.model

import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 성별. 서버 `UserRegisterRequest.gender` 는 `@NotNull` 이라 **셋 중 하나를 반드시 고른다** —
 * 전송 값은 enum 이름 그대로이고(`Gender.name`, data 매퍼) 비워 보낼 수 있는 필드가 아니다.
 */
enum class Gender {
    MALE,
    FEMALE,

    /**
     * 선택 안 함 — 사용자가 성별을 밝히지 않기로 **고른** 상태.
     *
     * **값을 비우는 게 아니라 명시적인 값이다.** 서버의 `@NotNull` 은 그대로라서(빈 값은 여전히 400)
     * 「밝히지 않음」도 `"UNDISCLOSED"` 라는 하나의 값으로 실려 나간다. 그래서 도메인에서도
     * `Gender?` 로 뭉개지 않고 **세 번째 상수**로 둔다 — 화면은 `null` 을 "아직 안 골랐다"(제출 불가)로
     * 쓰고 있어서, 비공개를 같은 `null` 에 얹으면 제출 가능 여부 판정부터 무너진다.
     *
     * 상수를 따로 두는 값어치는 **집계**에 있다: 나중에 성비를 볼 때 「입력 안 함」과 「응답 거부」는
     * 서로 다른 사실이다. 앞은 가입 흐름이 값을 못 받았다는 **앱 쪽 결함**이고, 뒤는 사용자가 행사한
     * **선택**이다. 비워서 저장하면 둘이 한 칸에 섞여 들어가 그 구분이 영구히 사라진다 — 나중에
     * 되살릴 방법이 없으므로 처음부터 나눠 적는다.
     *
     * 운영 서버는 2026-10-15 배포(Backend PR #235)부터 이 값을 받는다. 그 전까지는 400 이었고,
     * 되돌아갈 경우를 대비해 화면 노출 스위치를 presentation 에 한 줄로 남겨 뒀다 — 자세한 사정은
     * data 쪽 `RegisterRequestDto` KDoc 에 적었다(domain 은 data 를 모르므로 이름만 적는다).
     */
    UNDISCLOSED,
}

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
 * 구글 로그인 뒤의 **소셜 회원가입** 요청. 백엔드 `SocialRegisterRequest` 와 필드 1:1
 * (`POST /api/v1/auth/social/register`).
 *
 * [NewUser] 에서 `loginId`·`password`·`email` 이 빠지고 [signupToken] 이 들어간 모양이다.
 * 닮았다고 [NewUser] 를 재사용하지 않는 이유: 없는 값을 빈 문자열로 채워 넘기는 순간 「아이디가
 * 비어 있는 가입 요청」이 타입으로 표현 가능해지고, 그 값이 언젠가 로컬 가입 경로로 샌다.
 *
 * **이메일 칸이 없는 건 빠뜨린 게 아니다.** 서버가 요청에서 이메일을 받지 않고, 구글이 확인해 준
 * 값을 가입 대기표에서 꺼내 쓴다(`SocialAuthService.register`). 화면이 보여 주는 이메일
 * ([GoogleLoginResult.SignupRequired.email])은 **표시 전용**이고 되돌려 보낼 자리가 없다.
 *
 * [birthDate] 는 [NewUser] 와 같이 날짜로만 다룬다 — Instant 변환은 data 매퍼가 UTC 자정으로 한다.
 */
data class NewSocialUser(
    /**
     * 구글 로그인을 통과했다는 증표(가입 대기표).
     * [AuthRepository.loginWithGoogle][com.moyeota.domain.repository.AuthRepository.loginWithGoogle] 이
     * [GoogleLoginResult.SignupRequired.signupToken] 으로 돌려준 값을 그대로 넣는다.
     *
     * **수명 [AuthPolicy.SOCIAL_SIGNUP_TOKEN_TTL_SECONDS]초, 가입 성공 시 1회 소진.** 지나거나 없는 값이면
     * 401 `USER402`([AuthError.SOCIAL_SIGNUP_EXPIRED]) 이고 그때는 구글 로그인부터 다시 해야 한다.
     * [verificationToken] 과 마찬가지로 **가입 실패가 이 값을 태우지 않는다** — 서버는 저장이 다 끝난 뒤에야
     * 대기표를 지운다. 닉네임 중복(409)으로 되돌아온 사용자는 닉네임만 고쳐 같은 값으로 재시도할 수 있다.
     */
    val signupToken: String,
    /**
     * 문자 인증을 통과했다는 증표. 로컬 가입의 [NewUser.verificationToken] 과 **같은 값·같은 규칙**이다 —
     * 소셜 회원도 같은 문자 인증 API(`/auth/sms/send`·`/auth/sms/verify`)를 쓴다.
     * 서버가 토큰에 묶인 번호와 [phoneNumber] 가 같은지까지 본다(다르면 400 `USER207`).
     */
    val verificationToken: String,
    val phoneNumber: String,
    /** 동승자에게 보이는 이름. 규칙은 로컬 가입과 같다([AuthPolicy.isValidNickname]). */
    val nickname: String,
    /**
     * 실명. 구글 계정 이름([GoogleLoginResult.SignupRequired.name])을 **미리 채워 주되 사용자가 고칠 수 있는**
     * 값이다 — 서버는 여기 실린 값을 저장하고 구글 쪽 이름은 다시 보지 않는다. 구글 계정에 이름이 없으면
     * 미리 채울 값이 없으므로 화면이 직접 받아야 한다(서버 `@NotBlank`).
     */
    val name: String,
    val birthDate: LocalDate,
    val gender: Gender,
)

/**
 * 구글 로그인의 결과 (`POST /api/v1/auth/login/google` 의 200).
 *
 * **성공이 둘로 갈린다** — 서버는 구글 로그인을 통과해도 회원을 바로 만들지 않는다. 처음 온 사람에게는
 * 토큰 대신 가입 대기표를 주고, 문자 인증까지 마친 뒤에야 회원 생성과 토큰 발급을 한 번에 한다
 * (「토큰을 가진 사람 = 휴대폰 인증을 마친 사람」이라는 규칙을 로컬 가입과 똑같이 지키려는 서버 설계다).
 *
 * 그래서 이 값이 [SignupRequired] 면 **아직 로그인된 게 아니다.** 예외가 아니라 반환값으로 둔 이유도
 * 그것이다 — 실패가 아니라 「다음 화면이 다르다」는 정상 분기이고, `when` 을 포괄로 쓰면 가입 분기를
 * 빠뜨린 채 홈으로 보내는 실수를 컴파일러가 잡는다.
 */
sealed interface GoogleLoginResult {

    /**
     * 이미 가입한 회원 — **세션이 열렸다.** 이 값을 받은 시점에 토큰은 이미 저장됐고
     * [AuthRepository.authState][com.moyeota.domain.repository.AuthRepository.authState] 는
     * [AuthState.Authenticated] 다. 로컬 로그인 성공과 똑같은 상태이므로 화면이 더 할 일은 없다.
     *
     * @property userUuid 로그인한 사용자의 UUID. 로컬 로그인과 같이 액세스 토큰의 `sub` 에서 읽은 값이다.
     */
    data class LoggedIn(val userUuid: String) : GoogleLoginResult

    /**
     * 처음 온 사람 — **세션은 그대로다**(토큰이 없다). 문자 인증을 거쳐
     * [AuthRepository.registerSocial][com.moyeota.domain.repository.AuthRepository.registerSocial] 로
     * 가입을 끝내야 로그인된다.
     *
     * @property signupToken 가입 대기표. [NewSocialUser.signupToken] 에 그대로 넣는다.
     *   수명 [AuthPolicy.SOCIAL_SIGNUP_TOKEN_TTL_SECONDS]초 — **저장하지 않고 메모리에만 든다**
     *   (앱이 죽으면 구글 로그인부터 다시 하면 되고, 디스크에 남길 만큼 오래 사는 값이 아니다).
     * @property email 구글 계정 이메일. **가입 화면 표시용**이다 — 서버로 되돌려 보내지 않는다
     *   ([NewSocialUser] 에 이메일 칸이 없다). 서버는 이메일 없는 토큰을 401 로 거절하므로 정상 응답이면
     *   항상 있지만, 표시 전용 값이 빠졌다고 가입을 막을 이유가 없어 nullable 로 둔다(null 이면 그 줄을 숨긴다).
     * @property name 구글 계정 이름. 실명 입력란을 **미리 채우는 용도**다.
     *   **구글 계정에 이름이 없으면 null** 이다(서버가 키 자체를 뺀다) — 그때는 빈 입력란으로 시작한다.
     */
    data class SignupRequired(
        val signupToken: String,
        val email: String?,
        val name: String?,
    ) : GoogleLoginResult
}

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

    /**
     * 구글 로그인이 준 가입 대기표(`signupToken`)의 수명(초). 서버 `SocialAuthService.SIGNUP_TTL` = 10분.
     * [VERIFICATION_TOKEN_TTL_SECONDS] 와 **값은 같지만 시계가 따로 돈다** — 대기표는 구글 로그인 시점부터,
     * 인증 토큰은 문자 인증 성공 시점부터 센다. 그래서 문자 인증에서 오래 머문 사용자는 인증 토큰이
     * 멀쩡해도 대기표가 먼저 죽을 수 있다(401 `USER402`, [AuthError.SOCIAL_SIGNUP_EXPIRED]).
     */
    const val SOCIAL_SIGNUP_TOKEN_TTL_SECONDS = 600

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
 * 비밀번호 재설정 인증번호 요청의 접수 결과 (`POST /api/v1/auth/password-reset/send` 의 202).
 *
 * **「문자를 보냈다」는 뜻이 아니다** — 서버는 아이디·번호가 가입 정보와 맞든 틀리든 같은 접수를 준다.
 * 화면은 이 값으로 입력칸과 카운트다운만 열고, 발송 여부는 단정하지 않는다.
 *
 * @property requestId 확인 요청에 되돌려 보내는 접수번호. 번호가 아니라 이 값으로 인증번호를 확인한다.
 * @property expiresInSeconds 인증번호 유효 시간(서버 값 180).
 * @property resendAfterSeconds 다시 요청할 수 있을 때까지의 시간(서버 값 60).
 */
data class PasswordResetTicket(
    val requestId: String,
    val expiresInSeconds: Int,
    val resendAfterSeconds: Int,
)

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
