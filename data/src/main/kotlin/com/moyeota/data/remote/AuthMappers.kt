package com.moyeota.data.remote

import com.moyeota.data.remote.dto.ApiErrorDto
import com.moyeota.data.remote.dto.RegisterRequestDto
import com.moyeota.data.remote.dto.SocialLoginResponse
import com.moyeota.data.remote.dto.SocialRegisterRequestDto
import com.moyeota.data.remote.dto.TokenResponse
import com.moyeota.domain.model.AuthError
import com.moyeota.domain.model.AuthException
import com.moyeota.domain.model.AuthPolicy
import com.moyeota.domain.model.GoogleLoginResult
import com.moyeota.domain.model.NewSocialUser
import com.moyeota.domain.model.NewUser
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneOffset

private val errorJson = Json { ignoreUnknownKeys = true }

/**
 * 서버가 `Instant` 로 받는 birthDate 로 변환한다.
 *
 * UTC 자정을 쓰는 이유: 사용자가 고른 건 "날짜"뿐인데 로컬 타임존(KST, UTC+9)으로 자정을 잡으면
 * 서버에 하루 전날(전날 15:00Z)로 저장된다. 타임존이 뭐든 같은 날짜가 유지되도록 UTC 로 고정한다.
 *
 * 결과 형식은 `2000-01-01T00:00:00Z` — [java.time.Instant.toString] 이 ISO-8601 로 내며 초까지 항상 찍는다.
 */
internal fun LocalDate.toBirthDateInstant(): String =
    atStartOfDay(ZoneOffset.UTC).toInstant().toString()

internal fun NewUser.toDto(): RegisterRequestDto = RegisterRequestDto(
    loginId = loginId,
    password = password,
    // 서버가 앞뒤 공백을 strip 한 뒤 검사하므로 여기서 미리 다듬어 보낸다 — 저장되는 값도 같아진다.
    nickname = nickname.trim(),
    name = name,
    birthDate = birthDate.toBirthDateInstant(),
    // 발송·확인·가입 세 요청이 같은 형태를 쓴다 — 근거는 AuthPolicy.normalizePhoneNumber KDoc.
    phoneNumber = AuthPolicy.normalizePhoneNumber(phoneNumber),
    gender = gender.name,
    email = email,
    verificationToken = verificationToken,
)

/**
 * 다듬는 규칙은 로컬 가입([NewUser.toDto])과 **같다** — 닉네임은 trim, 번호는 숫자열, 생년월일은 UTC 자정.
 * 같은 사람이 같은 값을 넣었는데 가입 경로에 따라 저장되는 모양이 달라지면 안 된다.
 *
 * `signupToken`·`verificationToken` 은 건드리지 않는다 — 서버가 준 값을 그대로 되돌려 보내는 열쇠다.
 */
internal fun NewSocialUser.toDto(): SocialRegisterRequestDto = SocialRegisterRequestDto(
    signupToken = signupToken,
    verificationToken = verificationToken,
    // 발송·확인·가입 세 요청이 같은 형태를 쓴다 — 근거는 AuthPolicy.normalizePhoneNumber KDoc.
    phoneNumber = AuthPolicy.normalizePhoneNumber(phoneNumber),
    nickname = nickname.trim(),
    name = name,
    birthDate = birthDate.toBirthDateInstant(),
    gender = gender.name,
)

/**
 * 구글 로그인 응답을 `status` 로 가른 중간 결과. **data 계층 안에서만 쓴다.**
 *
 * 도메인의 [GoogleLoginResult] 로 곧장 못 가는 이유: [GoogleLoginResult.LoggedIn] 은 「세션이 열렸다」는
 * 뜻이라 토큰을 저장한 **뒤에야** 만들 수 있고, 그건 매퍼가 아니라 Repository 의 일이다
 * ([com.moyeota.data.repository.RemoteAuthRepository.loginWithGoogle]). 매퍼는 「어느 쪽인가」와
 * 「그쪽에 있어야 할 값이 다 있는가」까지만 답한다.
 */
internal sealed interface SocialLoginOutcome {
    /** `status=LOGIN`. 두 토큰이 모두 비어 있지 않다는 것까지 확인된 상태다. */
    data class Tokens(val tokens: TokenResponse) : SocialLoginOutcome

    /** `status=SIGNUP_REQUIRED`. `signupToken` 이 비어 있지 않다는 것까지 확인된 상태다. */
    data class SignupRequired(val result: GoogleLoginResult.SignupRequired) : SocialLoginOutcome
}

/**
 * [SocialLoginResponse] 를 `status` 로 가른다. **`status` 만이 분기의 근거다** — 「토큰이 있으면 로그인」
 * 식으로 칸의 유무를 보고 짐작하지 않는다. 서버가 어느 쪽인지 말해 주는 칸이 있는데 그걸 두고 추측하면,
 * 서버가 status 를 하나 늘렸을 때(예: 정지된 계정에 토큰 없이 안내만 주는 응답) 앱이 엉뚱한 쪽으로 간다.
 *
 * 끊는 경우는 전부 [AuthError.UNKNOWN] 이다 — 사용자가 고칠 수 있는 게 없고, 서버와 앱의 계약이 어긋났다는
 * 뜻이다. `serverMessage` 에는 무엇이 빠졌는지 적어 로그에서 원인을 찾을 수 있게 한다(화면은
 * [AuthError.UNKNOWN] 의 문구를 쓰므로 사용자에게 보이지 않는다).
 *
 * - **모르는 status·status 누락**: 가입 분기로도 로그인 분기로도 보내지 않는다. 어느 쪽으로 보내든 틀린다.
 * - **LOGIN 인데 토큰이 빠짐**: 통과시키면 빈 토큰으로 세션이 열려 「로그인은 됐는데 모든 요청이 401」이 된다.
 * - **SIGNUP_REQUIRED 인데 signupToken 이 빠짐**: 통과시키면 화면은 문자 인증까지 다 받게 한 뒤
 *   가입 제출에서야 400 을 본다([com.moyeota.data.repository.RemoteAuthRepository.verifyPhoneCode] 가
 *   빈 인증 토큰을 끊는 것과 같은 이유다).
 *
 * `email`·`name` 은 **빠져도 끊지 않는다.** `name` 은 구글 계정에 이름이 없으면 서버가 키를 빼는 정상
 * 경우이고, `email` 은 표시 전용이라 없다고 가입을 막을 이유가 없다. 공백뿐인 값은 null 로 접는다 —
 * 도메인의 「없음」은 null 하나다([com.moyeota.data.remote.toDomain] 이 이름·아이디에 거는 것과 같은 정규화).
 */
internal fun SocialLoginResponse.toOutcome(): SocialLoginOutcome = when (status) {
    SOCIAL_STATUS_LOGIN -> {
        val access = accessToken?.takeIf { it.isNotBlank() }
        val refresh = refreshToken?.takeIf { it.isNotBlank() }
        if (access == null || refresh == null) {
            throw AuthException(AuthError.UNKNOWN, "구글 로그인 응답(LOGIN)에 토큰이 없습니다.")
        }
        SocialLoginOutcome.Tokens(TokenResponse(accessToken = access, refreshToken = refresh))
    }

    SOCIAL_STATUS_SIGNUP_REQUIRED -> {
        val token = signupToken?.takeIf { it.isNotBlank() }
            ?: throw AuthException(AuthError.UNKNOWN, "구글 로그인 응답(SIGNUP_REQUIRED)에 signupToken 이 없습니다.")
        SocialLoginOutcome.SignupRequired(
            GoogleLoginResult.SignupRequired(
                signupToken = token,
                email = email?.trim()?.takeIf { it.isNotEmpty() },
                name = name?.trim()?.takeIf { it.isNotEmpty() },
            ),
        )
    }

    else -> throw AuthException(AuthError.UNKNOWN, "구글 로그인 응답의 status 를 해석할 수 없습니다: $status")
}

/** 서버 `SocialLoginStatus` enum 의 이름 그대로다. 대소문자까지 같아야 한다. */
private const val SOCIAL_STATUS_LOGIN = "LOGIN"
private const val SOCIAL_STATUS_SIGNUP_REQUIRED = "SIGNUP_REQUIRED"

/**
 * Retrofit/OkHttp 예외를 [AuthException] 으로 좁힌다. presentation 이 HTTP 를 알 필요가 없게 하는 경계다.
 *
 * 서버 본문의 `code` 를 우선 보고, 없으면(스프링이 직접 만든 404 등) 상태 코드로 폴백한다
 * — `ApiErrorDto` 주석대로 code 가 있어야 "서버가 의도한 에러"다.
 *
 * **코드 값이 이름에서 번호로 바뀌었다.** 인증 개편으로 서버 `UserErrorCode` 가
 * `LOGIN_FAILED` 같은 enum 이름 대신 `USER102` 같은 고정 코드를 내보낸다(실측):
 *
 * | 코드 | 상태 | 뜻 |
 * |---|---|---|
 * | `USER001`~`USER005` | 401 | 토큰 무효/만료/용도불일치/재사용/무토큰 |
 * | `USER101` | 409 | 아이디 중복 |
 * | `USER102` | 401 | 로그인 실패 |
 * | `USER103` | 404 | 사용자 없음 |
 * | `USER104` | 409 | 전화번호 중복 |
 * | `USER105` | 400 | 전화번호 형식 위반 |
 * | `USER106` | 400 | FCM 토큰이 비어 있음 |
 * | `USER107` | 400 | 닉네임 형식 위반 |
 * | `USER108` | 409 | 닉네임 중복 |
 * | `USER109` | 409 | 탈퇴한 번호의 재가입 제한 기간(30일) |
 * | `USER110` | 409 | 진행 중인 합승이 있어 탈퇴 불가 |
 * | `USER111` | 400 | 탈퇴 본인 확인 비밀번호 불일치 |
 * | `USER112` | 400 | 제공하지 않는 프로필 이미지 키 |
 * | `USER201` | 502 | 문자 발송 실패 |
 * | `USER202` | 429 | 재발송 대기(1분) |
 * | `USER203` | 429 | 하루 발송 한도(5회) 초과 |
 * | `USER204` | 400 | 인증번호 만료/없음 |
 * | `USER205` | 400 | 인증번호 불일치 |
 * | `USER206` | 429 | 입력 시도(5회) 초과 |
 * | `USER207` | 400 | 가입 토큰 무효(만료·소진·번호 불일치) |
 * | `USER208` | 503 | 인증 저장소 장애 |
 * | `USER114` | 404 | 가입되지 않은 번호(아이디 찾기) |
 * | `USER301` | 400 | 재설정 인증번호 틀림·만료·없는 접수 |
 * | `USER302` | 400 | 재설정 토큰 무효·만료 |
 * | `USER303` | 400 | 새 비밀번호와 확인 값 불일치 |
 * | `USER304` | 400 | 비밀번호 규칙 위반 |
 * | `USER305` | 400 | 지금 비밀번호와 같음 |
 * | `USER306` | 429 | 재설정 요청 제한(`Retry-After`) |
 * | `USER307` | 503 | 재설정 인증 저장소 장애 |
 * | `USER401` | 401 | 구글 idToken 무효(위조·만료·다른 앱용·email 없음) |
 * | `USER402` | 401 | 소셜 가입 대기표 없음·만료 |
 * | `USER403` | 409 | 이미 가입된 소셜 계정 |
 * | `USER404` | 503 | 소셜 로그인·가입 일시 불가(대기표 저장소 장애) — **HTTP 404 가 아니다** |
 * | `INVALID_REQUEST` | 400 | 검증 실패 (이름 그대로 남았다) |
 *
 * **문자 인증 계열은 상태 코드 폴백으로는 전혀 갈리지 않는다** — 429 가 셋(`USER202`·`USER203`·`USER206`),
 * 400 이 셋(`USER204`·`USER205`·`USER207`)이고 각각 화면의 대응이 다르다(재전송 카운트다운 /
 * 재시도 포기 / 인증 단계로 되돌리기). 게다가 429 는 폴백 표에 아예 없어서 코드를 놓치면
 * 전부 [AuthError.UNKNOWN] = "잠시 후 다시 시도해 주세요" 하나로 뭉쳐진다.
 *
 * 특히 **로그인 실패와 세션 만료가 둘 다 401** 이라 상태 코드 폴백만으로는 구분되지 않는다
 * — `USER102` 를 놓치면 아이디/비밀번호 오류에 "다시 로그인해 주세요"가 뜬다.
 * 소셜 쪽 둘(`USER401`·`USER402`)도 401 이다. 둘 다 **로그인하기 전에** 나는 코드라, 놓쳐서
 * [AuthError.SESSION_EXPIRED] 로 떨어지면 세션이 있었던 적도 없는 사람에게 세션 만료를 말하게 된다.
 *
 * 같은 이유로 **409 는 다섯 가지 뜻을 갖는다** — 아이디(`USER101`)·전화번호(`USER104`)·닉네임(`USER108`)
 * 중복에, 탈퇴 계열 둘(`USER109` 재가입 제한 · `USER110` 진행 중 합승)이 2026-10 에 더해졌다.
 * 가입 쪽 셋은 서버 검사 순서가 loginId → phoneNumber → nickname 이라 하나만 올라오고,
 * 코드를 놓치고 상태 코드로 폴백하면 무엇이 겹쳤든 "이미 사용 중인 아이디예요"가 떠서
 * 사용자가 아이디만 계속 바꾸게 된다. **탈퇴 화면에서는 그 폴백이 더 나쁘다** — 아이디를 입력한 적도
 * 없는 화면에 아이디 중복 안내가 뜬다.
 */
internal fun Throwable.toAuthException(): AuthException = when (this) {
    is AuthException -> this

    is HttpException -> {
        val body = readErrorBody()
        val error = when (body?.code) {
            "USER102" -> AuthError.LOGIN_FAILED
            "USER101" -> AuthError.LOGIN_ID_DUPLICATED
            "USER104" -> AuthError.PHONE_NUMBER_DUPLICATED
            "USER105" -> AuthError.INVALID_PHONE_NUMBER
            "USER107" -> AuthError.INVALID_NICKNAME
            "USER108" -> AuthError.NICKNAME_DUPLICATED
            // 탈퇴 계열(2026-10, Backend #206). 셋 다 화면 대응이 서로 다르므로 하나도 합치지 않는다 —
            // 109 는 가입(문자 발송) 경로, 110·111 은 탈퇴 경로에서 난다.
            "USER109" -> AuthError.REJOIN_RESTRICTED
            "USER110" -> AuthError.ONGOING_PARTY_EXISTS
            "USER111" -> AuthError.PASSWORD_MISMATCH
            // 제공 목록 밖의 프로필 이미지 키(Backend #235). 400 폴백이 INVALID_REQUEST 라
            // 코드를 놓치면 서버 원문(「제공하지 않는 프로필 이미지입니다」)이 그대로 떠서
            // 사용자가 할 일을 알 수 없다. 사용자 입력 문제가 아니라 앱이 잘못된 키를 보낸 것이다.
            "USER112" -> AuthError.INVALID_PROFILE_IMAGE
            // 아이디 찾기(Backend #314). 404 라 코드를 놓치면 폴백이 UNKNOWN — 「잠시 후 다시」가 떠서
            // 가입한 적 없는 번호를 계속 다시 넣어 보게 된다.
            "USER114" -> AuthError.PHONE_NOT_REGISTERED
            // 비밀번호 재설정(Backend #331). 400 이 다섯(301~305)이라 상태 코드 폴백으로는 전혀 갈리지 않는다.
            "USER301" -> AuthError.PASSWORD_RESET_CODE_REJECTED
            "USER302" -> AuthError.PASSWORD_RESET_TOKEN_INVALID
            "USER303" -> AuthError.PASSWORD_CONFIRM_MISMATCH
            "USER304" -> AuthError.PASSWORD_POLICY_VIOLATION
            "USER305" -> AuthError.SAME_PASSWORD
            "USER306" -> AuthError.PASSWORD_RESET_RATE_LIMITED
            // 인증 저장소 장애(503). 사용자가 할 일이 문자 인증 장애와 같아 한 값으로 모은다.
            "USER307" -> AuthError.SMS_UNAVAILABLE
            // 소셜(구글) 로그인·가입(백엔드 user/social). 401 둘·409 하나·503 하나라 폴백이 전부 틀린 말을 한다 —
            // 401 은 SESSION_EXPIRED(로그인 전인데 세션 만료), 409 는 LOGIN_ID_DUPLICATED(아이디가 없는 가입),
            // 503 은 UNKNOWN. 404 번은 장애지만 SMS_UNAVAILABLE 에 합치지 않는다 — 문자를 요청한 적 없는
            // 구글 로그인 버튼 자리에서도 나므로 「문자 인증이 안 된다」는 안내가 거짓이 된다.
            "USER401" -> AuthError.SOCIAL_TOKEN_INVALID
            "USER402" -> AuthError.SOCIAL_SIGNUP_EXPIRED
            "USER403" -> AuthError.SOCIAL_ALREADY_REGISTERED
            "USER404" -> AuthError.SOCIAL_UNAVAILABLE
            // 문자 인증. 둘(201/208)만 합치고 나머지는 그대로 남긴다 — 위 표 아래 주석에 근거.
            "USER201", "USER208" -> AuthError.SMS_UNAVAILABLE
            "USER202" -> AuthError.SMS_COOLDOWN
            "USER203" -> AuthError.SMS_DAILY_LIMIT_EXCEEDED
            "USER204" -> AuthError.SMS_CODE_EXPIRED
            "USER205" -> AuthError.SMS_CODE_MISMATCH
            "USER206" -> AuthError.SMS_TOO_MANY_ATTEMPTS
            "USER207" -> AuthError.PHONE_NOT_VERIFIED
            // 빈 FCM 토큰. 사용자가 고칠 수 있는 입력이 아니라 앱 쪽 문제이므로 일반 검증 실패로 둔다
            // (푸시 등록 실패는 FcmTokenRegistrar 가 삼키므로 여기까지 올라오지도 않는다).
            "USER106" -> AuthError.INVALID_REQUEST
            "INVALID_REQUEST" -> AuthError.INVALID_REQUEST
            // 토큰 계열 + 사용자 없음: 앱의 대응은 모두 "재로그인"이다.
            "USER001", "USER002", "USER003", "USER004", "USER005", "USER103" -> AuthError.SESSION_EXPIRED
            else -> when (code()) {
                400 -> AuthError.INVALID_REQUEST
                401 -> AuthError.SESSION_EXPIRED
                409 -> AuthError.LOGIN_ID_DUPLICATED
                else -> AuthError.UNKNOWN
            }
        }
        // 초 단위 정수만 읽는다(서버가 그렇게 준다). 날짜 형식이나 0 이하는 「모른다」로 둔다.
        val retryAfter = response()?.headers()?.get("Retry-After")?.trim()?.toIntOrNull()?.takeIf { it > 0 }
        AuthException(error, body?.message, this, retryAfter)
    }

    // 응답 자체를 못 받은 경우(연결 거부·타임아웃·DNS). 사용자에게는 재시도를 안내한다.
    is IOException -> AuthException(AuthError.NETWORK, cause = this)

    else -> AuthException(AuthError.UNKNOWN, cause = this)
}

private fun HttpException.readErrorBody(): ApiErrorDto? =
    runCatching {
        response()?.errorBody()?.string()
            ?.takeIf { it.isNotBlank() }
            ?.let { errorJson.decodeFromString<ApiErrorDto>(it) }
    }.getOrNull()

/**
 * 인증 계열 호출을 감싸 실패를 [AuthException] 으로 통일한다.
 * [CancellationException] 은 그대로 통과시킨다 — 취소를 에러로 바꾸면 화면 이탈이 에러 토스트로 보인다.
 */
internal suspend fun <T> authCall(block: suspend () -> T): T =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        throw e.toAuthException()
    }
