package com.moyeota.data.push

import com.moyeota.data.remote.UserApi
import com.moyeota.data.remote.dto.FcmTokenRequest
import com.moyeota.domain.model.AuthState
import com.moyeota.domain.session.UserSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * 이 기기의 FCM 토큰을 서버(`/api/v1/users/me/fcm-token`)와 맞춰 두는 단일 창구.
 *
 * **도메인 인터페이스를 두지 않은 건 의도다.** presentation 은 푸시 토큰을 전혀 보지 않는다 —
 * 등록/해제가 걸리는 지점은 앱 시작·토큰 회전(app 모듈)과 로그인/로그아웃
 * ([com.moyeota.data.repository.RemoteAuthRepository]) 뿐이라, 화면이 보는 계약에 올릴 이유가 없다.
 *
 * ### 왜 "보류(pending)"가 필요한가
 * 서버 등록은 Bearer 를 요구하는데 FCM 토큰은 로그인과 무관한 시점에 도착한다
 * (앱 최초 실행, 로그인 화면에서의 토큰 회전). 그래서 토큰을 받으면 일단 [pendingToken] 에 담아 두고,
 * 로그인 상태일 때만 실제로 PUT 한다. 미로그인이었다면 다음 로그인 직후
 * [registerPendingToken] 이 같은 값을 집어 올린다.
 *
 * 보류 값은 **메모리에만** 둔다. 프로세스가 죽으면 사라지지만 복구 경로가 이미 있다 —
 * `MoyeotaApplication` 이 앱 시작마다 `FirebaseMessaging.getToken()` 으로 토큰을 다시 흘려보낸다.
 * 디스크에 또 하나의 진실을 만들어 세션과 어긋나게 두는 것보다 이쪽이 안전하다.
 *
 * ### 실패를 삼키는 이유
 * 푸시 등록 실패는 치명적이지 않다 — 앱의 모든 기능은 그대로 동작하고, 잃는 건 도착 알림 하나다.
 * 반대로 실패를 위로 던지면 **로그인이 실패한 것처럼 보인다**. 그래서 [CancellationException]
 * 외의 모든 예외를 여기서 끊고, 재시도는 다음 자연 발생 시점(앱 재시작·토큰 회전·재로그인)에 맡긴다.
 */
class FcmTokenRegistrar(
    private val userApi: UserApi,
    private val session: UserSession,
) {

    /**
     * 마지막으로 확인된 이 기기의 FCM 토큰. 등록 성공 여부와 무관하게 최신 값을 들고 있는다
     * — 실패했더라도 다음 기회에 이 값으로 다시 시도해야 하기 때문이다.
     *
     * `@Volatile`: 쓰기는 FCM 콜백 스레드, 읽기는 로그인 코루틴이라 스레드가 다르다.
     */
    @Volatile
    private var pendingToken: String? = null

    /**
     * 기기 토큰을 알린다. 로그인 상태면 곧바로 서버에 등록하고, 아니면 보류만 한다.
     *
     * 호출 지점은 셋이다 — 앱 시작 시의 명시 조회, `onNewToken`(신규 발급), `onRegistered`(회전).
     * 셋 다 같은 토큰을 줄 수 있으므로 중복 호출을 걸러 내지 않는다:
     * 서버 쪽이 단순 덮어쓰기라 멱등이고, 오히려 **앞선 등록이 실패했을 때 스스로 낫는** 경로가 된다.
     */
    suspend fun onTokenAvailable(token: String) {
        if (token.isBlank()) return
        pendingToken = token
        registerIfAuthenticated(token)
    }

    /**
     * 로그인 직후 호출. 보류 중인 토큰이 있으면 이제야 서버에 등록한다.
     *
     * 로그인 때마다 다시 보내는 게 맞다 — 서버는 토큰을 **사용자별로** 들고 있으므로,
     * 같은 기기에 다른 계정이 로그인하면 그 계정 앞으로 새로 달아 줘야 한다.
     */
    suspend fun registerPendingToken() {
        val token = pendingToken ?: return
        register(token)
    }

    /**
     * 서버에서 이 기기 토큰을 뗀다. **로그아웃으로 세션을 비우기 전에** 불러야 한다
     * (Bearer 가 살아 있어야 `@CurrentUser` 가 대상을 찾는다).
     *
     * [pendingToken] 은 지우지 않는다 — 기기 토큰 자체는 로그아웃과 무관하게 유효하고,
     * 다음 로그인 때 [registerPendingToken] 이 그대로 쓴다.
     */
    suspend fun unregister() {
        runSwallowing { userApi.deleteFcmToken() }
    }

    /**
     * 복원이 끝날 때까지 기다렸다가 판단한다. [AuthState.Unknown] 을 그냥 "미로그인"으로 보면
     * 앱 시작 시 조회한 토큰이 매번 보류로 새어 나가, 이미 로그인된 사용자가 재로그인하기 전까지
     * 도착 알림을 못 받는다 — 세션 디스크 복원이 비동기라 앱 시작 직후엔 항상 Unknown 이다.
     */
    private suspend fun registerIfAuthenticated(token: String) {
        val state = session.authState.first { it !is AuthState.Unknown }
        if (state !is AuthState.Authenticated) return
        register(token)
    }

    private suspend fun register(token: String) {
        if (token.isBlank()) return
        runSwallowing { userApi.registerFcmToken(FcmTokenRequest(token)) }
    }

    private suspend fun runSwallowing(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            // 취소는 실패가 아니다. 삼키면 구조적 동시성이 깨진다.
            throw e
        } catch (_: Throwable) {
            // 네트워크·401·400 무엇이든 앱이 지금 할 수 있는 일이 없다. 다음 기회에 다시 시도한다.
        }
    }
}
