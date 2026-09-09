package com.moyeota.data.session

import com.moyeota.domain.model.AuthState
import com.moyeota.domain.session.UserSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 앱의 세션 단일 출처. 세 얼굴을 한 객체가 맡는다.
 * - [UserSession]: 화면이 보는 로그인 상태
 * - [TokenHolder]: OkHttp 가 보는 동기 토큰 창구
 * - [com.moyeota.data.repository.RemoteAuthRepository] 가 로그인/로그아웃 결과를 반영하는 대상
 *
 * 나눠 두면 "메모리의 토큰"과 "디스크의 토큰"이 어긋나는 지점이 생겨 401 루프의 원인이 된다.
 *
 * 메모리 캐시([session])가 항상 즉시 최신이고 디스크 반영은 뒤따른다(비동기). 요청에 실릴 토큰은
 * 메모리에서 읽으므로 저장이 늦어도 동작은 정확하고, 프로세스가 죽어 저장이 유실되면
 * 최악의 경우 재로그인 한 번이다.
 */
class SessionManager(
    private val storage: TokenStorage,
    private val scope: CoroutineScope,
) : UserSession, TokenHolder {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unknown)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    @Volatile
    private var session: StoredSession? = null

    // 디스크 복원 완료 신호. 복원 전에 나간 요청이 토큰 없이 401 을 맞고 억울하게 로그아웃되는 걸 막는다.
    private val restored = CountDownLatch(1)

    init {
        scope.launch {
            try {
                val loaded = storage.load()
                session = loaded
                _authState.value = loaded
                    ?.let { AuthState.Authenticated(it.userUuid) }
                    ?: AuthState.Unauthenticated
            } finally {
                // 복원이 실패해도 반드시 풀어준다 — 안 그러면 모든 네트워크 요청이 타임아웃까지 멈춘다.
                restored.countDown()
            }
        }
    }

    override fun currentAccessToken(): String? {
        awaitRestore()
        return session?.accessToken
    }

    override fun currentRefreshToken(): String? {
        awaitRestore()
        return session?.refreshToken
    }

    override fun onTokensRefreshed(accessToken: String, refreshToken: String) {
        val current = session ?: return
        val updated = current.copy(accessToken = accessToken, refreshToken = refreshToken)
        session = updated
        scope.launch { storage.save(updated) }
    }

    override fun onSessionExpired() {
        clearSession()
    }

    /** 로그인 성공 반영. 저장까지 끝내고 돌아온다 — 로그인 직후 앱이 죽어도 세션이 남게. */
    suspend fun onLoggedIn(newSession: StoredSession) {
        session = newSession
        storage.save(newSession)
        _authState.value = AuthState.Authenticated(newSession.userUuid)
        restored.countDown()
    }

    /**
     * 로그아웃 반영. 서버 무효화 호출보다 **먼저** 부른다 — 서버가 실패해도 사용자는 나갈 수 있어야 한다.
     * @return 서버에 무효화 요청을 보낼 리프레시 토큰(없으면 null)
     */
    fun onLoggedOut(): String? {
        val refreshToken = session?.refreshToken
        clearSession()
        return refreshToken
    }

    private fun clearSession() {
        session = null
        _authState.value = AuthState.Unauthenticated
        scope.launch { storage.clear() }
        restored.countDown()
    }

    private fun awaitRestore() {
        if (restored.count == 0L) return
        // 로컬 디스크 1회 읽기라 사실상 즉시 끝난다. 타임아웃은 어떤 이유로든 복원이 멈췄을 때
        // 앱 전체가 굳지 않게 하는 안전장치일 뿐이며, 지나면 미로그인으로 간주하고 진행한다.
        restored.await(RESTORE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    private companion object {
        const val RESTORE_TIMEOUT_SECONDS = 5L
    }
}
