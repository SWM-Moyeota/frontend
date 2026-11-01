package com.moyeota.data.remote.auth

import com.moyeota.data.session.TokenHolder
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * 401 을 만나면 리프레시로 재발급하고 원요청을 **한 번** 재시도한다.
 *
 * 설계 포인트 세 가지.
 * 1. **경합 시 재발급은 한 번만.** 서버가 회전(rotation) 방식이라 두 스레드가 같은 리프레시로
 *    동시에 재발급하면 늦은 쪽이 "이미 회전됨" 401 을 맞고 멀쩡한 세션이 날아간다.
 *    그래서 갱신 구간 전체를 [synchronized] 로 감싸고, 잠금을 얻었을 때 토큰이 이미 바뀌어 있으면
 *    (= 다른 스레드가 갱신을 끝냈으면) 재발급하지 않고 그 토큰으로 바로 재시도한다.
 * 2. **세션 만료는 서버가 거절했을 때만 알린다.** [RefreshResult.Rejected] 면
 *    [TokenHolder.onSessionExpired] 가 authState 를 Unauthenticated 로 바꾸고, presentation 이
 *    그걸 보고 로그인 화면으로 보낸다. 반대로 [RefreshResult.Unavailable] — 즉 **답을 못 받은 경우**
 *    에는 세션을 건드리지 않는다. 예전에는 둘을 구분하지 않아, 지하철에서 토큰이 만료되거나 배포 중
 *    503 을 맞은 멀쩡한 사용자가 로그인 화면으로 쫓겨났다(근거는 [RefreshResult] KDoc).
 * 3. **재시도는 1회.** 새 토큰으로도 401 이면 재발급으로 풀릴 문제가 아니다(권한 없음 등).
 *    무한 루프 대신 401 을 호출부로 올려보낸다.
 *
 * 이 Authenticator 는 인증 엔드포인트에는 달지 않는다 — 재발급 자체가 재발급을 부르면 안 된다.
 */
class TokenAuthenticator(
    private val tokens: TokenHolder,
    private val refresher: TokenRefresher,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // 애초에 토큰을 안 실은 요청의 401 은 재발급으로 풀 수 없다(미로그인 상태의 보호 API 접근).
        val failedAccessToken = response.request.bearerToken() ?: return null

        if (retryCount(response) >= MAX_RETRY) return null

        val newAccessToken = synchronized(this) {
            val current = tokens.currentAccessToken()
            when {
                // 다른 스레드가 이미 갱신을 끝냈다 — 재발급 없이 새 토큰으로 재시도한다.
                current != null && current != failedAccessToken -> current

                else -> {
                    val refreshToken = tokens.currentRefreshToken()
                    if (refreshToken == null) {
                        tokens.onSessionExpired()
                        null
                    } else {
                        when (val refreshed = refresher.refresh(refreshToken)) {
                            is RefreshResult.Rotated -> {
                                val (accessToken, newRefreshToken) = refreshed.tokens
                                tokens.onTokensRefreshed(accessToken, newRefreshToken)
                                accessToken
                            }

                            // 서버가 "이 리프레시는 못 쓴다"고 답했다 — 재로그인 외에 길이 없다.
                            RefreshResult.Rejected -> {
                                tokens.onSessionExpired()
                                null
                            }

                            // 답을 못 받았을 뿐이다. 세션을 **비우지 않고** 401 을 호출부로 올려보낸다 —
                            // 리프레시는 14일 수명 그대로 유효하므로 다음 요청이 재발급을 다시 시도한다.
                            RefreshResult.Unavailable -> null
                        }
                    }
                }
            }
        } ?: return null

        return response.request.newBuilder()
            .header(HEADER_AUTHORIZATION, BEARER_PREFIX + newAccessToken)
            .build()
    }

    private fun Request.bearerToken(): String? =
        header(HEADER_AUTHORIZATION)
            ?.takeIf { it.startsWith(BEARER_PREFIX) }
            ?.removePrefix(BEARER_PREFIX)
            ?.takeIf { it.isNotBlank() }

    // OkHttp 는 Authenticator 가 만든 재시도의 앞선 응답을 priorResponse 로 이어 붙인다.
    private fun retryCount(response: Response): Int {
        var count = 0
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    private companion object {
        const val MAX_RETRY = 1
    }
}
