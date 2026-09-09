package com.moyeota.data.push

import com.moyeota.data.remote.UserApi
import com.moyeota.data.remote.dto.FcmTokenRequest
import com.moyeota.data.remote.dto.UpdateProfileRequest
import com.moyeota.data.remote.dto.UserProfileResponse
import com.moyeota.domain.model.AuthState
import com.moyeota.domain.session.UserSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * 이 클래스의 계약은 "언제 서버를 부르고 언제 참는가" 하나다 — 잘못 부르면 401 이,
 * 참아야 할 때 안 참으면 도착 알림이 통째로 사라진다. 그래서 검증 대상은 전부 호출 여부/순서다.
 */
class FcmTokenRegistrarTest {

    @Test
    fun `로그인 상태면 받은 토큰을 곧바로 등록한다`() = runTest {
        val api = FakeUserApi()

        registrar(api, authenticated()).onTokenAvailable("device-token")

        assertEquals(listOf(FcmTokenRequest("device-token")), api.registered)
    }

    /**
     * 미로그인 시 PUT 을 보내면 Bearer 가 없어 401 만 받는다. 조용히 보류해 두는 게 정답이고,
     * 그 보류분은 다음 로그인에서 살아나야 한다 — 아래 테스트가 이어서 확인한다.
     */
    @Test
    fun `미로그인 상태면 등록하지 않고 보류한다`() = runTest {
        val api = FakeUserApi()

        registrar(api, unauthenticated()).onTokenAvailable("device-token")

        assertTrue(api.registered.isEmpty())
    }

    @Test
    fun `보류된 토큰은 로그인 직후 등록된다`() = runTest {
        val api = FakeUserApi()
        val session = FakeUserSession(AuthState.Unauthenticated)
        val registrar = registrar(api, session)

        registrar.onTokenAvailable("device-token")
        assertTrue("아직 로그인 전인데 보냈다", api.registered.isEmpty())

        // 실제로는 RemoteAuthRepository.login 이 세션을 연 뒤 이 호출을 잇는다.
        session.state.value = AuthState.Authenticated("uuid-1")
        registrar.registerPendingToken()

        assertEquals(listOf(FcmTokenRequest("device-token")), api.registered)
    }

    /**
     * 서버는 토큰을 **사용자별로** 들고 있다. 같은 기기에 다른 계정이 로그인하면 그 계정 앞으로
     * 다시 달아 줘야 하므로, 이미 한 번 보낸 토큰이라도 로그인 때마다 재전송하는 게 맞다.
     */
    @Test
    fun `로그인할 때마다 같은 토큰을 다시 등록한다`() = runTest {
        val api = FakeUserApi()
        val registrar = registrar(api, authenticated())

        registrar.onTokenAvailable("device-token")
        registrar.registerPendingToken()

        assertEquals(2, api.registered.size)
    }

    /** 보류분이 없으면 부를 게 없다 — 토큰 조회가 실패한 기기에서 헛된 400 을 만들지 않는다. */
    @Test
    fun `보류된 토큰이 없으면 로그인해도 서버를 부르지 않는다`() = runTest {
        val api = FakeUserApi()

        registrar(api, authenticated()).registerPendingToken()

        assertTrue(api.registered.isEmpty())
    }

    /** 서버 `@NotBlank` 에 걸려 400 만 받을 요청이다. 보낼 이유가 없다. */
    @Test
    fun `빈 토큰은 보류도 전송도 하지 않는다`() = runTest {
        val api = FakeUserApi()
        val registrar = registrar(api, authenticated())

        registrar.onTokenAvailable("")
        registrar.registerPendingToken()

        assertTrue(api.registered.isEmpty())
    }

    @Test
    fun `해제는 서버 DELETE 를 호출한다`() = runTest {
        val api = FakeUserApi()

        registrar(api, authenticated()).unregister()

        assertEquals(1, api.deleteCount)
    }

    /**
     * 로그아웃 뒤에도 기기 토큰 자체는 유효하다. 지워 버리면 재로그인 시 등록할 값이 사라져,
     * 앱을 껐다 켜기 전까지 도착 알림을 못 받는다.
     */
    @Test
    fun `해제해도 보류 토큰은 남아 다음 로그인에 다시 등록된다`() = runTest {
        val api = FakeUserApi()
        val registrar = registrar(api, authenticated())

        registrar.onTokenAvailable("device-token")
        registrar.unregister()
        api.registered.clear()

        registrar.registerPendingToken()

        assertEquals(listOf(FcmTokenRequest("device-token")), api.registered)
    }

    /**
     * 등록 실패를 위로 던지면 이 호출을 품고 있는 로그인/로그아웃이 실패한 것처럼 보인다.
     * 잃는 건 도착 알림 하나뿐이므로 여기서 끊는 게 맞다.
     */
    @Test
    fun `등록이 실패해도 예외를 올리지 않는다`() = runTest {
        val networkDown = FakeUserApi(failure = IOException("offline"))
        val unauthorized = FakeUserApi(failure = httpException(401))

        // 던지면 이 테스트가 그대로 실패한다.
        registrar(networkDown, authenticated()).onTokenAvailable("device-token")
        registrar(unauthorized, authenticated()).onTokenAvailable("device-token")
    }

    @Test
    fun `해제가 실패해도 예외를 올리지 않는다`() = runTest {
        registrar(FakeUserApi(failure = IOException("offline")), authenticated()).unregister()
    }

    /** 취소까지 삼키면 구조적 동시성이 깨져, 스코프가 죽어도 이 호출만 살아남는다. */
    @Test
    fun `취소는 삼키지 않고 그대로 전파한다`() {
        val api = FakeUserApi(failure = CancellationException("scope cancelled"))

        val thrown = runCatching {
            runBlocking { registrar(api, authenticated()).onTokenAvailable("device-token") }
        }.exceptionOrNull()

        assertTrue("취소가 삼켜졌다: $thrown", thrown is CancellationException)
    }

    /**
     * 앱 시작 직후 세션은 항상 [AuthState.Unknown] 이다(디스크 복원이 비동기).
     * 이걸 미로그인으로 단정하면 **이미 로그인한 사용자의 앱 시작 등록이 매번 보류로 새어 나가**,
     * 재로그인 전까지 도착 알림을 못 받는다. 복원이 끝날 때까지 기다렸다 판단해야 한다.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `복원 전 Unknown 상태에서는 확정될 때까지 기다렸다 등록한다`() = runTest {
        val api = FakeUserApi()
        val session = FakeUserSession(AuthState.Unknown)

        val registering = async { registrar(api, session).onTokenAvailable("device-token") }
        runCurrent()
        assertTrue("Unknown 인데 벌써 보냈다", api.registered.isEmpty())

        session.state.value = AuthState.Authenticated("uuid-1")
        registering.await()

        assertEquals(listOf(FcmTokenRequest("device-token")), api.registered)
    }

    private fun registrar(api: UserApi, session: UserSession) = FcmTokenRegistrar(api, session)

    private fun authenticated() = FakeUserSession(AuthState.Authenticated("uuid-1"))

    private fun unauthenticated() = FakeUserSession(AuthState.Unauthenticated)

    private fun httpException(code: Int) = HttpException(
        Response.error<Any>(code, "".toResponseBody("application/json".toMediaType())),
    )
}

private class FakeUserSession(initial: AuthState) : UserSession {
    val state = MutableStateFlow(initial)
    override val authState: StateFlow<AuthState> = state.asStateFlow()
}

private class FakeUserApi(private val failure: Throwable? = null) : UserApi {
    val registered = mutableListOf<FcmTokenRequest>()
    var deleteCount = 0
        private set

    override suspend fun getMyProfile(): UserProfileResponse =
        throw UnsupportedOperationException("푸시 토큰 등록과 무관하다")

    override suspend fun updateProfile(request: UpdateProfileRequest): Unit =
        throw UnsupportedOperationException("푸시 토큰 등록과 무관하다")

    override suspend fun registerFcmToken(request: FcmTokenRequest) {
        failure?.let { throw it }
        registered += request
    }

    override suspend fun deleteFcmToken() {
        failure?.let { throw it }
        deleteCount++
    }
}
