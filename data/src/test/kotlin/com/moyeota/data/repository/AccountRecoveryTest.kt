package com.moyeota.data.repository

import com.moyeota.data.local.ActivePartyStorage
import com.moyeota.data.local.RememberedParty
import com.moyeota.data.push.FcmTokenRegistrar
import com.moyeota.data.remote.AuthApi
import com.moyeota.data.remote.UserApi
import com.moyeota.data.remote.dto.FindLoginIdResponse
import com.moyeota.data.remote.dto.LoginRequestDto
import com.moyeota.data.remote.dto.NicknameCheckRequestDto
import com.moyeota.data.remote.dto.NicknameCheckResponse
import com.moyeota.data.remote.dto.PasswordResetConfirmRequestDto
import com.moyeota.data.remote.dto.PasswordResetSendRequestDto
import com.moyeota.data.remote.dto.PasswordResetSendResponse
import com.moyeota.data.remote.dto.PasswordResetVerifyRequestDto
import com.moyeota.data.remote.dto.PasswordResetVerifyResponse
import com.moyeota.data.remote.dto.PhoneCheckRequestDto
import com.moyeota.data.remote.dto.PhoneCheckResponse
import com.moyeota.data.remote.dto.RefreshTokenRequestDto
import com.moyeota.data.remote.dto.RegisterRequestDto
import com.moyeota.data.remote.dto.RegisterResponse
import com.moyeota.data.remote.dto.SmsSendRequestDto
import com.moyeota.data.remote.dto.SmsVerifyRequestDto
import com.moyeota.data.remote.dto.SmsVerifyResponse
import com.moyeota.data.remote.dto.TokenResponse
import com.moyeota.data.remote.toAuthException
import com.moyeota.data.session.FakeTokenStorage
import com.moyeota.data.session.SessionManager
import com.moyeota.domain.model.AuthError
import com.moyeota.domain.model.AuthException
import com.moyeota.domain.model.AuthPolicy
import com.moyeota.domain.model.PasswordResetTicket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Call
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.http.POST
import java.lang.reflect.Proxy

/**
 * 아이디 찾기(Backend #314) · 비밀번호 재설정(Backend #331) 의 서버 계약.
 *
 * 기준: 백엔드 `AuthController`(`/api/v1/auth/find-id/...`) · `PasswordResetController`
 * (`/api/v1/auth/password-reset/...`) · `UserErrorCode`. iOS 의 같은 이름 테스트(frontend-ios #47)와 짝이다.
 * 실측(2026-11-14, 운영): 미가입 번호 발송 404 `USER114` / 없는 계정 재설정 요청 202 /
 * 틀린 인증번호 400 `USER301` / 무효 토큰 400 `USER302`.
 */
class AccountRecoveryTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun repository(api: AuthApi): RemoteAuthRepository {
        val session = SessionManager(FakeTokenStorage(null), CoroutineScope(Dispatchers.Unconfined))
        // 로그인 전 흐름이라 Bearer 가 붙는 UserApi 를 한 번도 부르지 않는다 — 불리면 그 자체가 결함이다.
        val userApi = Proxy.newProxyInstance(UserApi::class.java.classLoader, arrayOf(UserApi::class.java)) { _, m, _ ->
            error("로그인 전 흐름은 UserApi 를 부르지 않는다: ${m.name}")
        } as UserApi
        return RemoteAuthRepository(api, userApi, session, FcmTokenRegistrar(userApi, session), EmptyPartyStorage)
    }

    private fun httpException(code: Int, body: String) = HttpException(
        Response.error<Any>(code, body.toResponseBody("application/json".toMediaType())),
    )

    private fun errorOf(block: suspend () -> Unit): AuthError? =
        (runCatching { runBlocking { block() } }.exceptionOrNull() as? AuthException)?.error

    // ── 경로 ──

    /** 다섯 경로 모두 `/api/v1/auth/` 하위여야 한다 — 그 프리픽스만 permitAll 이라 로그인 전에 부를 수 있다. */
    @Test
    fun `아이디 찾기·비밀번호 재설정 경로는 auth 프리픽스 아래의 POST 다`() {
        fun path(name: String) = AuthApi::class.java.methods.first { it.name == name }.getAnnotation(POST::class.java)?.value

        assertEquals("api/v1/auth/find-id/send", path("sendFindLoginIdCode"))
        assertEquals("api/v1/auth/find-id/verify", path("findLoginId"))
        assertEquals("api/v1/auth/password-reset/send", path("sendPasswordResetCode"))
        assertEquals("api/v1/auth/password-reset/verify", path("verifyPasswordResetCode"))
        assertEquals("api/v1/auth/password-reset/confirm", path("confirmPasswordReset"))
    }

    // ── JSON ──

    @Test
    fun `재설정 요청 본문의 키는 서버 record 필드와 같다`() {
        assertEquals(
            """{"loginId":"a","phoneNumber":"b"}""",
            json.encodeToString(PasswordResetSendRequestDto.serializer(), PasswordResetSendRequestDto("a", "b")),
        )
        assertEquals(
            """{"requestId":"a","code":"b"}""",
            json.encodeToString(PasswordResetVerifyRequestDto.serializer(), PasswordResetVerifyRequestDto("a", "b")),
        )
        assertEquals(
            """{"resetToken":"a","newPassword":"b","newPasswordConfirm":"c"}""",
            json.encodeToString(
                PasswordResetConfirmRequestDto.serializer(),
                PasswordResetConfirmRequestDto("a", "b", "c"),
            ),
        )
    }

    @Test
    fun `재설정 응답을 서버가 주는 모양 그대로 읽는다`() {
        val sent = json.decodeFromString(
            PasswordResetSendResponse.serializer(),
            """{"requestId":"abc","expiresIn":180,"resendAfter":60,"message":"입력한 정보가 가입 정보와 일치하면 인증번호가 발송됩니다."}""",
        )
        assertEquals("abc", sent.requestId)
        assertEquals(180L, sent.expiresIn)
        assertEquals(60L, sent.resendAfter)

        // message 는 앱이 읽지 않는다 — 서버가 빼더라도 접수가 깨지면 안 된다.
        val withoutMessage = json.decodeFromString(
            PasswordResetSendResponse.serializer(),
            """{"requestId":"abc","expiresIn":180,"resendAfter":60}""",
        )
        assertNull(withoutMessage.message)

        assertEquals(
            PasswordResetVerifyResponse("tok", 600),
            json.decodeFromString(PasswordResetVerifyResponse.serializer(), """{"resetToken":"tok","expiresIn":600}"""),
        )
        assertEquals(
            "moyeota",
            json.decodeFromString(FindLoginIdResponse.serializer(), """{"loginId":"moyeota"}""").loginId,
        )
    }

    // ── 아이디 찾기 ──

    @Test
    fun `아이디 찾기 발송은 하이픈을 뗀 번호로 나간다`() = runBlocking {
        val api = RecoveryFakeAuthApi()

        repository(api).sendFindLoginIdCode("010-1234-5678")

        assertEquals(SmsSendRequestDto("01012345678"), api.findIdSendRequest)
    }

    /**
     * 가입 발송의 409 `USER104`(이미 가입)와 뜻이 정반대다 — 여기서는 **가입이 안 된** 번호를 끊는다.
     * 404 라 코드를 놓치면 UNKNOWN(「잠시 후 다시」)으로 떨어져 같은 번호를 계속 넣어 보게 된다.
     */
    @Test
    fun `아이디 찾기 발송 404 USER114 는 가입되지 않은 번호로 올라온다`() {
        val api = RecoveryFakeAuthApi(
            failure = httpException(404, """{"code":"USER114","message":"가입되지 않은 사용자입니다."}"""),
        )

        assertEquals(AuthError.PHONE_NOT_REGISTERED, errorOf { repository(api).sendFindLoginIdCode("01012345678") })
    }

    @Test
    fun `아이디 찾기 확인은 아이디 전체를 돌려주고 번호와 코드를 다듬어 싣는다`() = runBlocking {
        val api = RecoveryFakeAuthApi(loginId = "moyeota_test")

        val loginId = repository(api).findLoginId("010-1234-5678", " 123456 ")

        assertEquals("moyeota_test", loginId)
        assertEquals(SmsVerifyRequestDto("01012345678", "123456"), api.findIdVerifyRequest)
    }

    @Test
    fun `아이디 찾기 응답에 아이디가 없으면 실패로 끊는다`() {
        val api = RecoveryFakeAuthApi(loginId = "")

        assertEquals(AuthError.UNKNOWN, errorOf { repository(api).findLoginId("01012345678", "123456") })
    }

    @Test
    fun `아이디 찾기 확인 실패는 가입 인증과 같은 코드로 갈린다`() {
        listOf(
            Triple(400, "USER204", AuthError.SMS_CODE_EXPIRED),
            Triple(400, "USER205", AuthError.SMS_CODE_MISMATCH),
            Triple(429, "USER206", AuthError.SMS_TOO_MANY_ATTEMPTS),
        ).forEach { (status, code, expected) ->
            val api = RecoveryFakeAuthApi(failure = httpException(status, """{"code":"$code","message":"m"}"""))
            assertEquals(code, expected, errorOf { repository(api).findLoginId("01012345678", "123456") })
        }
    }

    // ── 비밀번호 재설정 ──

    @Test
    fun `재설정 요청은 아이디와 정규화한 번호를 싣고 접수 결과를 초 단위로 넘긴다`() = runBlocking {
        val api = RecoveryFakeAuthApi()

        val ticket = repository(api).requestPasswordReset(" moyeota ", "010-1234-5678")

        assertEquals(PasswordResetSendRequestDto("moyeota", "01012345678"), api.resetSendRequest)
        assertEquals(PasswordResetTicket(REQUEST_ID, 180, 60), ticket)
    }

    /** 0 이하를 그대로 넘기면 화면이 입력칸을 열자마자 「만료됐어요」를 그린다. */
    @Test
    fun `재설정 접수의 시간 값이 0 이하면 정책 상수로 메운다`() = runBlocking {
        val api = RecoveryFakeAuthApi(sendResponse = PasswordResetSendResponse(REQUEST_ID, 0, 0))

        val ticket = repository(api).requestPasswordReset("moyeota", "01012345678")

        assertEquals(AuthPolicy.SMS_CODE_TTL_SECONDS, ticket.expiresInSeconds)
        assertEquals(AuthPolicy.SMS_RESEND_COOLDOWN_SECONDS, ticket.resendAfterSeconds)
    }

    @Test
    fun `재설정 접수 응답에 접수번호가 없으면 실패로 끊는다`() {
        val api = RecoveryFakeAuthApi(sendResponse = PasswordResetSendResponse("", 180, 60))

        assertEquals(AuthError.UNKNOWN, errorOf { repository(api).requestPasswordReset("moyeota", "01012345678") })
    }

    @Test
    fun `재설정 확인은 접수번호와 코드를 싣고 토큰을 돌려준다`() = runBlocking {
        val api = RecoveryFakeAuthApi()

        val token = repository(api).verifyPasswordResetCode(REQUEST_ID, " 123456 ")

        assertEquals(RESET_TOKEN, token)
        assertEquals(PasswordResetVerifyRequestDto(REQUEST_ID, "123456"), api.resetVerifyRequest)
    }

    /** 비밀번호를 다듬으면 사용자가 정한 값과 저장된 값이 달라진다 — 로그인은 입력을 그대로 보낸다. */
    @Test
    fun `새 비밀번호는 다듬지 않고 그대로 싣는다`() = runBlocking {
        val api = RecoveryFakeAuthApi()

        repository(api).confirmPasswordReset(RESET_TOKEN, " Passw0rd! ", " Passw0rd! ")

        assertEquals(
            PasswordResetConfirmRequestDto(RESET_TOKEN, " Passw0rd! ", " Passw0rd! "),
            api.resetConfirmRequest,
        )
    }

    /** 400 이 다섯이라 상태 코드 폴백(INVALID_REQUEST)으로는 하나도 갈리지 않는다 — 화면 대응이 전부 다르다. */
    @Test
    fun `재설정 오류 코드는 하나씩 제 사유로 올라온다`() {
        listOf(
            Triple(400, "USER301", AuthError.PASSWORD_RESET_CODE_REJECTED),
            Triple(400, "USER302", AuthError.PASSWORD_RESET_TOKEN_INVALID),
            Triple(400, "USER303", AuthError.PASSWORD_CONFIRM_MISMATCH),
            Triple(400, "USER304", AuthError.PASSWORD_POLICY_VIOLATION),
            Triple(400, "USER305", AuthError.SAME_PASSWORD),
            Triple(429, "USER306", AuthError.PASSWORD_RESET_RATE_LIMITED),
            Triple(503, "USER307", AuthError.SMS_UNAVAILABLE),
        ).forEach { (status, code, expected) ->
            val exception = httpException(status, """{"code":"$code","message":"m"}""").toAuthException()
            assertEquals(code, expected, exception.error)
        }
    }

    /** 사유는 없어도 남은 초는 온다 — 화면이 「곧 풀린다」와 「한참 걸린다」를 가르는 유일한 근거다. */
    @Test
    fun `429 의 Retry-After 를 초로 읽어 올린다`() {
        fun retryAfter(header: String?): Int? {
            val raw = okhttp3.Response.Builder()
                .request(okhttp3.Request.Builder().url("http://localhost/").build())
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(429)
                .message("Too Many Requests")
                .apply { header?.let { header("Retry-After", it) } }
                .build()
            val body = """{"code":"USER306","message":"m"}""".toResponseBody("application/json".toMediaType())
            return HttpException(Response.error<Any>(body, raw)).toAuthException().retryAfterSeconds
        }

        assertEquals(42, retryAfter("42"))
        assertEquals(7, retryAfter(" 7 "))
        assertNull(retryAfter(null))
        assertNull(retryAfter("0"))
        assertNull(retryAfter("Wed, 21 Oct 2026 07:28:00 GMT"))
    }

    @Test
    fun `재설정 요청 429 는 제한으로 올라온다`() {
        val api = RecoveryFakeAuthApi(
            failure = httpException(429, """{"code":"USER306","message":"잠시 후 다시 시도해 주세요."}"""),
        )

        assertEquals(
            AuthError.PASSWORD_RESET_RATE_LIMITED,
            errorOf { repository(api).requestPasswordReset("moyeota", "01012345678") },
        )
    }
}

private val REQUEST_ID = "r".repeat(43)
private val RESET_TOKEN = "t".repeat(43)

private object EmptyPartyStorage : ActivePartyStorage {
    override suspend fun load(): RememberedParty? = null
    override suspend fun save(party: RememberedParty) = Unit
    override suspend fun clear() = Unit
}

private class RecoveryFakeAuthApi(
    private val failure: Throwable? = null,
    private val loginId: String = "moyeota",
    private val sendResponse: PasswordResetSendResponse = PasswordResetSendResponse(REQUEST_ID, 180, 60),
) : AuthApi {
    var findIdSendRequest: SmsSendRequestDto? = null
        private set
    var findIdVerifyRequest: SmsVerifyRequestDto? = null
        private set
    var resetSendRequest: PasswordResetSendRequestDto? = null
        private set
    var resetVerifyRequest: PasswordResetVerifyRequestDto? = null
        private set
    var resetConfirmRequest: PasswordResetConfirmRequestDto? = null
        private set

    override suspend fun sendFindLoginIdCode(request: SmsSendRequestDto) {
        findIdSendRequest = request
        failure?.let { throw it }
    }

    override suspend fun findLoginId(request: SmsVerifyRequestDto): FindLoginIdResponse {
        findIdVerifyRequest = request
        failure?.let { throw it }
        return FindLoginIdResponse(loginId)
    }

    override suspend fun sendPasswordResetCode(request: PasswordResetSendRequestDto): PasswordResetSendResponse {
        resetSendRequest = request
        failure?.let { throw it }
        return sendResponse
    }

    override suspend fun verifyPasswordResetCode(request: PasswordResetVerifyRequestDto): PasswordResetVerifyResponse {
        resetVerifyRequest = request
        failure?.let { throw it }
        return PasswordResetVerifyResponse(RESET_TOKEN, 600)
    }

    override suspend fun confirmPasswordReset(request: PasswordResetConfirmRequestDto) {
        resetConfirmRequest = request
        failure?.let { throw it }
    }

    // 아래는 이 테스트가 건드리지 않는 경로다.
    override suspend fun sendSmsCode(request: SmsSendRequestDto): Unit = unused()
    override suspend fun verifySmsCode(request: SmsVerifyRequestDto): SmsVerifyResponse = unused()
    override suspend fun register(request: RegisterRequestDto): RegisterResponse = unused()
    override suspend fun login(request: LoginRequestDto): TokenResponse = unused()
    override suspend fun loginWithGoogle(
        request: com.moyeota.data.remote.dto.GoogleLoginRequestDto,
    ): com.moyeota.data.remote.dto.SocialLoginResponse = unused()
    override suspend fun registerSocial(request: com.moyeota.data.remote.dto.SocialRegisterRequestDto): TokenResponse =
        unused()
    override fun reissue(request: RefreshTokenRequestDto): Call<TokenResponse> = unused()
    override suspend fun logout(request: RefreshTokenRequestDto): Unit = unused()
    override suspend fun checkNickname(request: NicknameCheckRequestDto): NicknameCheckResponse = unused()
    override suspend fun checkPhone(request: PhoneCheckRequestDto): PhoneCheckResponse = unused()

    private fun unused(): Nothing = error("이 테스트가 부르지 않는 경로")
}
