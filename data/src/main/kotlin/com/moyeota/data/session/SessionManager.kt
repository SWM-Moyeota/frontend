package com.moyeota.data.session

import com.moyeota.domain.model.AuthState
import com.moyeota.domain.session.UserSession
import kotlinx.coroutines.CancellationException
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

    /**
     * **복원 실패는 「미로그인 확정」으로 떨어뜨린다.** 예외를 그냥 올려보내면 두 가지로 끝난다 —
     * [scope] 에 핸들러가 없으면 안드로이드 기본 핸들러가 프로세스를 죽이고(= 파일이 손상된 경우
     * **매 실행 크래시 루프**), 누군가 삼키면 [_authState] 가 [AuthState.Unknown] 에 영구히 머물러
     * `MainNavGraph` 가 스플래시만 그린다(= **무한 스피너**). 둘 다 복구 수단이 재설치뿐이다.
     *
     * 틀린 쪽으로 떨어지는 비용은 재로그인 한 번이므로, 모르면 미로그인으로 본다.
     */
    init {
        scope.launch {
            try {
                val loaded = storage.load()
                session = loaded
                _authState.value = loaded
                    ?.let { AuthState.Authenticated(it.userUuid) }
                    ?: AuthState.Unauthenticated
            } catch (e: CancellationException) {
                // 스코프 취소는 실패가 아니다(앱 종료 등). 상태를 건드리지 않고 그대로 통과시킨다.
                throw e
            } catch (_: Throwable) {
                session = null
                _authState.value = AuthState.Unauthenticated
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
        scope.launch { saveSwallowing(updated) }
    }

    override fun onSessionExpired() {
        clearSession()
    }

    /**
     * 로그인 성공 반영. 저장까지 기다리고 돌아온다 — 로그인 직후 앱이 죽어도 세션이 남게.
     *
     * **저장이 실패해도 로그인은 성공이다.** 디스크가 꽉 찼거나 파일이 손상된 사용자를 로그인 자체에서
     * 막으면 앱을 전혀 쓸 수 없다. 잃는 건 "다음 실행에서의 자동 로그인"뿐이고, 지금 이 세션은
     * 메모리에 있어 그대로 동작한다(클래스 KDoc 의 "메모리가 항상 즉시 최신" 원칙).
     */
    suspend fun onLoggedIn(newSession: StoredSession) {
        session = newSession
        saveSwallowing(newSession)
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
        scope.launch {
            try {
                storage.clear()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // 디스크 비우기 실패로 "로그아웃이 안 된 것처럼" 보이게 하지 않는다 — 메모리와 화면은
                // 이미 미로그인이다. 남은 파일은 다음 실행에서 복원되지만 그 토큰은 서버에서 이미
                // 무효화됐으므로 첫 요청의 401 → 재발급 거절 → 다시 미로그인으로 수렴한다.
            }
        }
        restored.countDown()
    }

    /**
     * 디스크 저장 실패를 삼킨다. 세션 저장은 **메모리를 뒤따르는 캐시 갱신**이라 실패가 치명적이지 않고,
     * 반대로 예외를 올려보내면 [scope] 의 미처리 예외가 되어 프로세스가 죽는다
     * (`SupervisorJob` 은 형제 취소만 막고 예외를 삼키지 않는다 — `AppContainer` 참조).
     */
    private suspend fun saveSwallowing(newSession: StoredSession) {
        try {
            storage.save(newSession)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // 다음 저장 기회(재발급·재로그인)에 같은 값으로 다시 시도된다.
        }
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
