package com.moyeota.data.remote.auth

import com.moyeota.data.remote.AuthApi
import com.moyeota.data.remote.dto.RefreshTokenRequestDto

/** 재발급으로 받은 새 토큰 쌍. 서버가 회전 방식이라 리프레시도 함께 바뀐다. */
data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
)

/**
 * 리프레시 토큰으로 새 토큰 쌍을 받아오는 **동기** 창구.
 *
 * [okhttp3.Authenticator] 에서 호출되므로 non-suspend 다. Retrofit 을 몰라도 되도록 인터페이스로
 * 분리해 두면 [TokenAuthenticator] 의 재시도·경합 로직을 실제 HTTP 없이 단위 테스트할 수 있다.
 */
fun interface TokenRefresher {
    /** 성공 시 새 토큰 쌍, 실패(401·네트워크 오류 등) 시 null. */
    fun refresh(refreshToken: String): TokenPair?
}

/**
 * Retrofit 구현. **Bearer 인터셉터도 Authenticator 도 붙지 않은 클라이언트**의 [AuthApi] 를 받아야 한다
 * — 재발급 요청이 401 을 맞았을 때 다시 재발급을 트리거하면 무한 루프가 된다.
 */
class RetrofitTokenRefresher(
    private val authApi: AuthApi,
) : TokenRefresher {

    override fun refresh(refreshToken: String): TokenPair? {
        val response = runCatching {
            authApi.reissue(RefreshTokenRequestDto(refreshToken)).execute()
        }.getOrNull() ?: return null

        val body = response.body()
        // 401(만료·회전됨·로그아웃·탈퇴)은 전부 "재로그인" 하나로 귀결되므로 사유를 구분하지 않는다.
        if (!response.isSuccessful || body == null) return null
        if (body.accessToken.isBlank() || body.refreshToken.isBlank()) return null

        return TokenPair(body.accessToken, body.refreshToken)
    }
}
