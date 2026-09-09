package com.moyeota.data.remote

import com.moyeota.data.remote.dto.ApiErrorDto
import com.moyeota.data.remote.dto.RegisterRequestDto
import com.moyeota.domain.model.AuthError
import com.moyeota.domain.model.AuthException
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
    phoneNumber = phoneNumber,
    gender = gender.name,
    email = email,
)

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
 * | `USER106` | 400 | FCM 토큰이 비어 있음 |
 * | `USER107` | 400 | 닉네임 형식 위반 |
 * | `USER108` | 409 | 닉네임 중복 |
 * | `INVALID_REQUEST` | 400 | 검증 실패 (이름 그대로 남았다) |
 *
 * 특히 **로그인 실패와 세션 만료가 둘 다 401** 이라 상태 코드 폴백만으로는 구분되지 않는다
 * — `USER102` 를 놓치면 아이디/비밀번호 오류에 "다시 로그인해 주세요"가 뜬다.
 *
 * 같은 이유로 **409 는 세 가지 뜻을 갖는다** — 아이디(`USER101`)·전화번호(`USER104`)·닉네임(`USER108`) 중복.
 * 서버 검사 순서가 loginId → phoneNumber → nickname 이라 셋 중 하나만 올라오는데,
 * 코드를 놓치고 상태 코드로 폴백하면 무엇이 겹쳤든 "이미 사용 중인 아이디예요"가 떠서
 * 사용자가 아이디만 계속 바꾸게 된다.
 */
internal fun Throwable.toAuthException(): AuthException = when (this) {
    is AuthException -> this

    is HttpException -> {
        val body = readErrorBody()
        val error = when (body?.code) {
            "USER102" -> AuthError.LOGIN_FAILED
            "USER101" -> AuthError.LOGIN_ID_DUPLICATED
            "USER104" -> AuthError.PHONE_NUMBER_DUPLICATED
            "USER107" -> AuthError.INVALID_NICKNAME
            "USER108" -> AuthError.NICKNAME_DUPLICATED
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
        AuthException(error, body?.message, this)
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
