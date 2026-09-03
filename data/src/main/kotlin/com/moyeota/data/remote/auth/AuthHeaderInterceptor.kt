package com.moyeota.data.remote.auth

import com.moyeota.data.session.TokenHolder
import okhttp3.Interceptor
import okhttp3.Response

internal const val HEADER_AUTHORIZATION = "Authorization"
internal const val BEARER_PREFIX = "Bearer "

/**
 * 모든 요청에 `Authorization: Bearer {access}` 를 붙인다.
 *
 * 토큰이 없으면(미로그인) 헤더 없이 그냥 보낸다. 다만 **이제 그런 요청은 반드시 401 이다** —
 * 서버가 Spring Security 로 넘어가면서 `/api/v1/auth/` 하위를 뺀 전 경로가 인증 필수가 됐고,
 * 예전에 열려 있던 목록·장소 조회도 마찬가지다.
 * 헤더 없이 보내는 건 "서버가 401 로 명확히 답하게 두는" 선택이며,
 * 그 401 은 [TokenAuthenticator] 가 재발급으로 풀지 못하므로(재시도할 토큰 자체가 없다)
 * 화면 에러로 올라간다. 즉 이 인터셉터는 미로그인 호출을 구제하지 않는다 — 화면이 막아야 한다.
 *
 * 인증 엔드포인트(가입/로그인/재발급/로그아웃)는 이 인터셉터가 없는 별도 클라이언트를 쓴다
 * ([com.moyeota.data.remote.NetworkModule] 참조).
 */
class AuthHeaderInterceptor(
    private val tokens: TokenHolder,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        // 이미 붙어 있으면 건드리지 않는다 — Authenticator 가 재발급 토큰으로 세팅한 재시도 요청이 여기 해당한다.
        if (request.header(HEADER_AUTHORIZATION) != null) return chain.proceed(request)

        val accessToken = tokens.currentAccessToken() ?: return chain.proceed(request)

        return chain.proceed(
            request.newBuilder()
                .header(HEADER_AUTHORIZATION, BEARER_PREFIX + accessToken)
                .build(),
        )
    }
}
