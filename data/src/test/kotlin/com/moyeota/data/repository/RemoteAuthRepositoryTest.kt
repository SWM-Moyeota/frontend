package com.moyeota.data.repository

import com.moyeota.data.push.FcmTokenRegistrar
import com.moyeota.data.remote.AuthApi
import com.moyeota.data.remote.UserApi
import com.moyeota.data.remote.dto.FcmTokenRequest
import com.moyeota.data.remote.dto.LoginRequestDto
import com.moyeota.data.remote.dto.NicknameCheckRequestDto
import com.moyeota.data.remote.dto.NicknameCheckResponse
import com.moyeota.data.remote.dto.RefreshTokenRequestDto
import com.moyeota.data.remote.dto.RegisterRequestDto
import com.moyeota.data.remote.dto.RegisterResponse
import com.moyeota.data.remote.dto.TokenResponse
import com.moyeota.data.remote.dto.UpdateProfileRequest
import com.moyeota.data.remote.dto.UserProfileResponse
import com.moyeota.data.session.FakeTokenStorage
import com.moyeota.data.session.SessionManager
import com.moyeota.data.session.StoredSession
import com.moyeota.domain.model.AuthError
import com.moyeota.domain.model.AuthException
import com.moyeota.domain.model.AuthState
import com.moyeota.domain.model.Gender
import com.moyeota.domain.model.NewUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Call
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.time.LocalDate

class RemoteAuthRepositoryTest {

    private val newUser = NewUser(
        loginId = "moyeota_test",
        password = "Passw0rd!",
        nickname = "모여타",
        name = "홍길동",
        birthDate = LocalDate.of(2000, 1, 1),
        phoneNumber = "010-1234-5678",
        gender = Gender.MALE,
        email = "test@moyeota.com",
    )

    private fun session(initial: StoredSession? = null, storage: FakeTokenStorage = FakeTokenStorage(initial)) =
        SessionManager(storage, CoroutineScope(Dispatchers.Unconfined))

    // 내 정보 조회 API 는 가입/로그인/로그아웃 경로와 무관하다 — 그쪽 테스트가 신경 쓰지 않게 기본값으로 채운다.
    private fun repository(
        api: AuthApi,
        session: SessionManager,
        userApi: UserApi = FakeUserApi(),
        registrar: FcmTokenRegistrar = FcmTokenRegistrar(userApi, session),
    ) = RemoteAuthRepository(api, userApi, session, registrar)

    @Test
    fun `내 정보는 uuid 와 이름을 그대로 도메인으로 넘긴다`() = runBlocking {
        val profile = repository(FakeAuthApi(), session(), FakeUserApi(name = "김성윤")).getMyProfile()

        assertEquals("01a06145-3caf-7614-a3bd-cee6e25316b1", profile.uuid)
        assertEquals("김성윤", profile.name)
    }

    /**
     * 닉네임도 실명도 없는 사용자에게 서버는 `"name":null` 을 내려보낸다(`orElse(null)`).
     * 이걸 에러로 바꾸면 멀쩡한 사용자가 화면을 못 연다 — 이름 없는 프로필은 정상 경우다.
     */
    @Test
    fun `이름이 없어도 실패하지 않는다`() = runBlocking {
        val profile = repository(FakeAuthApi(), session(), FakeUserApi(name = null)).getMyProfile()

        assertNull(profile.name)
    }

    /**
     * 401 은 재발급이 이미 실패했다는 뜻이다(Authenticator 가 apiClient 에서 한 번 시도한다).
     * SESSION_EXPIRED 로 좁혀야 화면이 "다시 로그인"으로 보낸다.
     */
    @Test
    fun `내 정보 조회의 401 은 세션 만료로 좁힌다`() {
        val failing = FakeUserApi(failure = httpException(401, """{"code":"USER005","message":"로그인이 필요합니다."}"""))

        val thrown = runCatching {
            runBlocking { repository(FakeAuthApi(), session(), failing).getMyProfile() }
        }.exceptionOrNull()

        assertEquals(AuthError.SESSION_EXPIRED, (thrown as AuthException).error)
    }

    // uuid 없이 "나"를 화면에 흘려보내면 잘못된 상태가 조용히 굳는다 — 여기서 끊는다.
    @Test
    fun `내 정보 응답에 uuid 가 없으면 실패로 끊는다`() {
        val thrown = runCatching {
            runBlocking { repository(FakeAuthApi(), session(), FakeUserApi(uuid = "")).getMyProfile() }
        }.exceptionOrNull()

        assertEquals(AuthError.UNKNOWN, (thrown as AuthException).error)
    }

    @Test
    fun `가입은 uuid 만 돌려주고 로그인시키지는 않는다`() = runBlocking {
        val api = FakeAuthApi()
        val session = session()

        val uuid = repository(api, session).register(newUser)

        assertEquals("uuid-1", uuid)
        assertEquals("2000-01-01T00:00:00Z", api.registerRequest?.birthDate)
        // 서버가 토큰을 주지 않으므로 화면은 이어서 login 을 호출해야 한다.
        assertEquals(AuthState.Unauthenticated, session.authState.value)
    }

    /**
     * 서버 응답에는 사용자 식별자가 없다 — UUID 는 액세스 토큰의 `sub` 에서 나와야 한다.
     * 저장된 세션의 userUuid 가 그 값과 같은지까지 확인한다.
     */
    @Test
    fun `로그인 성공 시 액세스 토큰 sub 를 UUID 로 삼아 영속화한다`() = runBlocking {
        val api = FakeAuthApi()
        val storage = FakeTokenStorage(null)
        val session = session(storage = storage)

        val uuid = repository(api, session).login("moyeota_test", "Passw0rd!")

        assertEquals("uuid-1", uuid)
        assertEquals(LoginRequestDto("moyeota_test", "Passw0rd!"), api.loginRequest)
        assertEquals(AuthState.Authenticated("uuid-1"), session.authState.value)
        assertEquals(StoredSession("uuid-1", ACCESS_TOKEN_WITH_SUB, "refresh.jwt"), storage.saved)
    }

    /**
     * `sub` 를 못 읽으면 "로그인은 됐는데 내가 누군지 모르는" 세션이 열린다.
     * 그런 상태는 조용히 굳으므로 여기서 끊고, 세션도 열지 않는다.
     */
    @Test
    fun `토큰에서 sub 를 읽지 못하면 세션을 열지 않는다`() = runBlocking {
        val api = FakeAuthApi(accessToken = ACCESS_TOKEN_WITHOUT_SUB)
        val storage = FakeTokenStorage(null)
        val session = session(storage = storage)

        val thrown = runCatching { repository(api, session).login("moyeota_test", "Passw0rd!") }
            .exceptionOrNull()

        assertEquals(AuthError.UNKNOWN, (thrown as AuthException).error)
        assertEquals(AuthState.Unauthenticated, session.authState.value)
        assertNull(storage.saved)
    }

    @Test
    fun `로그인 401 은 LOGIN_FAILED AuthException 으로 올라온다`() = runBlocking {
        val api = FakeAuthApi(
            loginFailure = httpException(401, """{"code":"USER102","message":"아이디나 비밀번호가 다릅니다."}"""),
        )
        val session = session()

        val thrown = runCatching { repository(api, session).login("moyeota_test", "wrong") }
            .exceptionOrNull()

        assertTrue(thrown is AuthException)
        assertEquals(AuthError.LOGIN_FAILED, (thrown as AuthException).error)
        assertEquals(AuthState.Unauthenticated, session.authState.value)
    }

    @Test
    fun `가입 409 는 중복 아이디로 올라온다`() = runBlocking {
        val api = FakeAuthApi(
            registerFailure = httpException(409, """{"code":"USER101","message":"이미 존재하는 아이디입니다."}"""),
        )

        val thrown = runCatching { repository(api, session()).register(newUser) }.exceptionOrNull()

        assertEquals(AuthError.LOGIN_ID_DUPLICATED, (thrown as AuthException).error)
    }

    /** 가입 요청에 닉네임이 실려야 한다 — 서버 필수 필드이고, 이름(name)과는 다른 값이다. */
    @Test
    fun `가입 요청에 닉네임을 싣는다`() = runBlocking {
        val api = FakeAuthApi()

        repository(api, session()).register(newUser)

        assertEquals("모여타", api.registerRequest?.nickname)
        assertEquals("홍길동", api.registerRequest?.name)
    }

    /**
     * 아이디·전화번호·닉네임 중복이 **전부 409** 다. 코드를 보지 않고 상태 코드로 폴백하면
     * 전화번호가 겹쳤는데 "이미 사용 중인 아이디예요"가 떠서 사용자가 아이디만 계속 바꾸게 된다.
     */
    @Test
    fun `가입 409 USER104 는 전화번호 중복으로 올라온다`() = runBlocking {
        val api = FakeAuthApi(
            registerFailure = httpException(409, """{"code":"USER104","message":"이미 가입된 전화번호입니다."}"""),
        )

        val thrown = runCatching { repository(api, session()).register(newUser) }.exceptionOrNull()

        assertEquals(AuthError.PHONE_NUMBER_DUPLICATED, (thrown as AuthException).error)
        assertEquals("이미 가입된 전화번호입니다.", thrown.serverMessage)
    }

    /**
     * 서버 검사 순서는 아이디 → 전화번호 → 닉네임이라 **닉네임 중복도 409 로 온다**.
     * 코드를 보지 않고 상태 코드로 폴백하면 "이미 사용 중인 아이디예요"가 떠서
     * 사용자가 아이디만 계속 바꾸게 된다.
     */
    @Test
    fun `가입 409 USER108 은 닉네임 중복으로 올라온다`() = runBlocking {
        val api = FakeAuthApi(
            registerFailure = httpException(409, """{"code":"USER108","message":"이미 사용 중인 닉네임입니다."}"""),
        )

        val thrown = runCatching { repository(api, session()).register(newUser) }.exceptionOrNull()

        assertEquals(AuthError.NICKNAME_DUPLICATED, (thrown as AuthException).error)
    }

    @Test
    fun `닉네임 중복 확인은 서버 exists 를 그대로 돌려준다`() = runBlocking {
        val taken = FakeAuthApi(nicknameExists = true)
        val free = FakeAuthApi(nicknameExists = false)

        assertTrue(repository(taken, session()).isNicknameTaken("모여타"))
        assertFalse(repository(free, session()).isNicknameTaken("모여타"))
        assertEquals(NicknameCheckRequestDto("모여타"), taken.nicknameRequest)
    }

    /**
     * 서버는 앞뒤 공백을 strip 한 뒤 검사하고 저장한다. 앱이 다듬지 않으면 `"모여타 "` 가
     * "사용 가능"으로 보이고선 가입 때 같은 닉네임으로 409 를 맞는다.
     */
    @Test
    fun `닉네임 중복 확인은 앞뒤 공백을 다듬어 보낸다`() = runBlocking {
        val api = FakeAuthApi()

        repository(api, session()).isNicknameTaken("  모여타  ")

        assertEquals("모여타", api.nicknameRequest?.nickname)
    }

    /** 형식이 틀리면 서버가 exists 판정 전에 400 USER107 을 낸다 — "중복"이 아니라 "형식 오류"다. */
    @Test
    fun `닉네임 중복 확인의 400 USER107 은 형식 오류로 올라온다`() = runBlocking {
        val api = FakeAuthApi(
            nicknameFailure = httpException(
                400,
                """{"code":"USER107","message":"닉네임은 2~10자의 한글, 영문, 숫자만 가능합니다."}""",
            ),
        )

        val thrown = runCatching { repository(api, session()).isNicknameTaken("!") }.exceptionOrNull()

        assertEquals(AuthError.INVALID_NICKNAME, (thrown as AuthException).error)
    }

    /** null 필드는 본문에서 빠진다 — 서버가 "넘어온 것만 갱신"하므로 null 로 지우기를 표현할 수 없다. */
    @Test
    fun `프로필 수정은 넘어온 값만 서버로 보낸다`() = runBlocking {
        val userApi = FakeUserApi()

        repository(FakeAuthApi(), session(), userApi).updateProfile(nickname = " 새닉 ", imageUrl = null)

        assertEquals(listOf(UpdateProfileRequest(nickname = "새닉", imageUrl = null)), userApi.profileUpdates)
    }

    @Test
    fun `프로필 수정 실패도 AuthException 으로 좁힌다`() = runBlocking {
        val userApi = FakeUserApi(
            profileFailure = httpException(409, """{"code":"USER108","message":"이미 사용 중인 닉네임입니다."}"""),
        )

        val thrown = runCatching {
            repository(FakeAuthApi(), session(), userApi).updateProfile("겹치는닉", null)
        }.exceptionOrNull()

        assertEquals(AuthError.NICKNAME_DUPLICATED, (thrown as AuthException).error)
    }

    /**
     * FCM 토큰은 로그인 전에 도착해 보류돼 있는 게 정상이다(앱 최초 실행·로그인 화면에서의 회전).
     * 세션이 열리는 이 순간이 서버에 실어 보낼 수 있는 첫 시점이라, 로그인 경로가 직접 이어 준다.
     */
    @Test
    fun `로그인 성공 시 보류된 푸시 토큰을 서버에 등록한다`() = runBlocking {
        val userApi = FakeUserApi()
        val session = session()
        val registrar = FcmTokenRegistrar(userApi, session)
        registrar.onTokenAvailable("device-token")
        assertTrue("로그인 전인데 보냈다", userApi.fcmCalls.isEmpty())

        repository(FakeAuthApi(), session, userApi, registrar).login("moyeota_test", "Passw0rd!")

        assertEquals(listOf("PUT:device-token"), userApi.fcmCalls)
    }

    /** 등록 실패로 로그인이 실패한 것처럼 보이면 안 된다 — 잃는 건 도착 알림 하나뿐이다. */
    @Test
    fun `푸시 등록이 실패해도 로그인은 성공한다`() = runBlocking {
        val userApi = FakeUserApi(fcmFailure = IOException("offline"))
        val session = session()
        val registrar = FcmTokenRegistrar(userApi, session)
        registrar.onTokenAvailable("device-token")

        val uuid = repository(FakeAuthApi(), session, userApi, registrar).login("moyeota_test", "Passw0rd!")

        assertEquals("uuid-1", uuid)
        assertEquals(AuthState.Authenticated("uuid-1"), session.authState.value)
    }

    /**
     * **순서가 이 테스트의 전부다.** 서버는 `@CurrentUser` 로 대상을 찾으므로 세션을 먼저 비우면
     * Bearer 가 사라져 401 이 나고, 서버에는 죽은 토큰이 남아 다음 사용자의 도착 알림이
     * 이 기기로 온다. 그래서 해제 시점에 액세스 토큰이 아직 살아 있는지를 직접 붙잡아 확인한다.
     */
    @Test
    fun `로그아웃은 세션을 비우기 전에 푸시 토큰을 해제한다`() = runBlocking {
        val session = session(StoredSession("uuid-1", "access.jwt", "refresh.jwt"))
        val userApi = TokenProbingUserApi { session.currentAccessToken() }

        repository(FakeAuthApi(), session, userApi, FcmTokenRegistrar(userApi, session)).logout()

        assertEquals(1, userApi.deleteCount)
        assertEquals("access.jwt", userApi.accessTokenAtDelete)
        assertEquals(AuthState.Unauthenticated, session.authState.value)
    }

    /** 보낼 Bearer 가 없으니 401 만 받는다. 로그아웃 경로에 헛된 왕복을 끼워 넣지 않는다. */
    @Test
    fun `미로그인 상태의 로그아웃은 푸시 해제도 부르지 않는다`() = runBlocking {
        val userApi = FakeUserApi()
        val session = session()

        repository(FakeAuthApi(), session, userApi, FcmTokenRegistrar(userApi, session)).logout()

        assertTrue(userApi.fcmCalls.isEmpty())
    }

    /** 오프라인에서 해제가 실패해도 로그아웃은 그대로 끝나야 한다 — "절대 실패 안 함" 계약. */
    @Test
    fun `푸시 해제가 실패해도 로그아웃은 완료된다`() = runBlocking {
        val userApi = FakeUserApi(fcmFailure = IOException("offline"))
        val session = session(StoredSession("uuid-1", "access.jwt", "refresh.jwt"))

        repository(FakeAuthApi(), session, userApi, FcmTokenRegistrar(userApi, session)).logout()

        assertEquals(AuthState.Unauthenticated, session.authState.value)
        assertNull(session.currentAccessToken())
    }

    @Test
    fun `로그아웃은 서버에 리프레시 무효화를 요청한다`() = runBlocking {
        val api = FakeAuthApi()
        val session = session(StoredSession("uuid-1", "access.jwt", "refresh.jwt"))

        repository(api, session).logout()

        assertEquals(RefreshTokenRequestDto("refresh.jwt"), api.logoutRequest)
        assertEquals(AuthState.Unauthenticated, session.authState.value)
    }

    @Test
    fun `서버 로그아웃이 실패해도 로컬 세션은 비워지고 예외를 올리지 않는다`() = runBlocking {
        val api = FakeAuthApi(logoutFailure = IOException("offline"))
        val session = session(StoredSession("uuid-1", "access.jwt", "refresh.jwt"))

        // 여기서 던지면 오프라인 사용자가 로그아웃하지 못하고 갇힌다.
        repository(api, session).logout()

        assertEquals(AuthState.Unauthenticated, session.authState.value)
        assertNull(session.currentAccessToken())
    }

    @Test
    fun `미로그인 상태의 로그아웃은 서버를 부르지 않는다`() = runBlocking {
        val api = FakeAuthApi()

        repository(api, session()).logout()

        assertNull(api.logoutRequest)
    }

    private fun httpException(code: Int, body: String) = HttpException(
        Response.error<Any>(code, body.toResponseBody("application/json".toMediaType())),
    )
}

/** 페이로드 `{"sub":"uuid-1","tokenType":"ACCESS"}` 를 base64url 로 담은 가짜 액세스 토큰. 서명은 검증되지 않는다. */
private const val ACCESS_TOKEN_WITH_SUB =
    "header.eyJzdWIiOiJ1dWlkLTEiLCJ0b2tlblR5cGUiOiJBQ0NFU1MifQ.signature"

/** 페이로드 `{"tokenType":"ACCESS"}` — sub 가 없는 비정상 토큰. */
private const val ACCESS_TOKEN_WITHOUT_SUB = "header.eyJ0b2tlblR5cGUiOiJBQ0NFU1MifQ.signature"

private class FakeAuthApi(
    private val accessToken: String = ACCESS_TOKEN_WITH_SUB,
    private val registerFailure: Throwable? = null,
    private val loginFailure: Throwable? = null,
    private val logoutFailure: Throwable? = null,
    private val nicknameExists: Boolean = false,
    private val nicknameFailure: Throwable? = null,
) : AuthApi {
    var registerRequest: RegisterRequestDto? = null
        private set
    var nicknameRequest: NicknameCheckRequestDto? = null
        private set
    var loginRequest: LoginRequestDto? = null
        private set
    var logoutRequest: RefreshTokenRequestDto? = null
        private set

    override suspend fun register(request: RegisterRequestDto): RegisterResponse {
        registerRequest = request
        registerFailure?.let { throw it }
        return RegisterResponse(uuid = "uuid-1")
    }

    override suspend fun login(request: LoginRequestDto): TokenResponse {
        loginRequest = request
        loginFailure?.let { throw it }
        // 서버는 사용자 식별자를 주지 않는다 — 토큰 쌍이 전부다.
        return TokenResponse(accessToken = accessToken, refreshToken = "refresh.jwt")
    }

    // 재발급은 OkHttp Authenticator 경로 전용이라 Repository 테스트에서는 호출되지 않는다.
    override fun reissue(request: RefreshTokenRequestDto): Call<TokenResponse> =
        throw UnsupportedOperationException("reissue 는 TokenAuthenticator 가 호출한다")

    override suspend fun logout(request: RefreshTokenRequestDto) {
        logoutRequest = request
        logoutFailure?.let { throw it }
    }

    override suspend fun checkNickname(request: NicknameCheckRequestDto): NicknameCheckResponse {
        nicknameRequest = request
        nicknameFailure?.let { throw it }
        return NicknameCheckResponse(exists = nicknameExists)
    }
}

/** 기본값은 실서버 실측 응답 그대로(testuser1). */
private class FakeUserApi(
    private val uuid: String = "01a06145-3caf-7614-a3bd-cee6e25316b1",
    private val name: String? = "김성윤",
    private val failure: Throwable? = null,
    private val fcmFailure: Throwable? = null,
    private val profileFailure: Throwable? = null,
) : UserApi {
    /** 푸시 토큰 호출이 **일어난 순서**. 어떤 호출이 실제로 나갔는지를 그대로 남긴다. */
    val fcmCalls = mutableListOf<String>()

    /** PATCH 로 실제 나간 본문. null 필드가 어떻게 실렸는지까지 확인한다. */
    val profileUpdates = mutableListOf<UpdateProfileRequest>()

    override suspend fun getMyProfile(): UserProfileResponse {
        failure?.let { throw it }
        return UserProfileResponse(uuid = uuid, name = name)
    }

    override suspend fun updateProfile(request: UpdateProfileRequest) {
        profileUpdates += request
        profileFailure?.let { throw it }
    }

    override suspend fun registerFcmToken(request: FcmTokenRequest) {
        fcmFailure?.let { throw it }
        fcmCalls += "PUT:${request.token}"
    }

    override suspend fun deleteFcmToken() {
        fcmFailure?.let { throw it }
        fcmCalls += "DELETE"
    }
}

/**
 * 해제가 불린 **그 순간의** 액세스 토큰을 붙잡아 둔다.
 * "세션 정리보다 먼저 해제했는가"는 호출 횟수로는 알 수 없고, 이렇게 시점을 찍어야만 드러난다.
 */
private class TokenProbingUserApi(private val accessToken: () -> String?) : UserApi {
    var accessTokenAtDelete: String? = null
        private set
    var deleteCount = 0
        private set

    override suspend fun getMyProfile(): UserProfileResponse =
        throw UnsupportedOperationException("푸시 해제 순서 검증 전용 페이크다")

    override suspend fun updateProfile(request: UpdateProfileRequest) =
        throw UnsupportedOperationException("푸시 해제 순서 검증 전용 페이크다")

    override suspend fun registerFcmToken(request: FcmTokenRequest) = Unit

    override suspend fun deleteFcmToken() {
        accessTokenAtDelete = accessToken()
        deleteCount++
    }
}
