package com.moyeota.data.remote

import com.moyeota.data.remote.dto.LoginRequestDto
import com.moyeota.data.remote.dto.RefreshTokenRequestDto
import com.moyeota.data.remote.dto.RegisterRequestDto
import com.moyeota.data.remote.dto.RegisterResponse
import com.moyeota.data.remote.dto.TokenResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * 경로 기준: user/interfaces/AuthController.java (@RequestMapping("/api/v1/auth")).
 * 네 엔드포인트가 **전부 이 한 컨트롤러로 모였다** — 가입·로그인이 `/api/v1/users*` 에 있던
 * 이전 배치는 사라졌다(실측: `POST /api/v1/users/login` → 401, Security 가 보호 경로로 잡는다).
 *
 * 이 프리픽스가 Security 의 사실상 유일한 permitAll 구간이기도 하다 — `SecurityConfig` 가 열어 두는 건
 * `/api/v1/auth/` 하위 전체, `/api/v1/local/users`, `/ws-chat/` 하위 전체뿐이고 나머지는
 * `anyRequest().authenticated()` 다. 즉 **여기 말고는 토큰 없이 부를 수 있는 도메인 API 가 없다.**
 *
 * **이 API 는 Bearer 헤더도, 401 재발급 Authenticator 도 붙지 않은 별도 OkHttp 클라이언트로 만든다**
 * ([NetworkModule.create] 참조). 그렇게 하지 않으면 재발급이 실패했을 때 재발급 요청 자체가
 * 다시 재발급을 트리거해 무한 루프에 빠진다.
 */
interface AuthApi {

    /** 201. 성공해도 토큰은 없다(uuid 만) — 가입 직후 [login] 을 이어서 불러야 한다. */
    @POST("api/v1/auth/register")
    suspend fun register(@Body request: RegisterRequestDto): RegisterResponse

    /**
     * 200 / 401 `USER102`.
     *
     * 응답은 재발급과 **똑같은 [TokenResponse] 다** — 예전에 있던 `userId` 필드가 사라졌다.
     * 사용자 UUID 는 액세스 토큰의 `sub` 클레임에서 읽는다([com.moyeota.data.remote.auth.jwtSubject]).
     */
    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequestDto): TokenResponse

    /**
     * 200 / 401 UNAUTHORIZED. 회전 방식이라 성공 응답의 refreshToken 까지 저장해야 한다.
     *
     * OkHttp [okhttp3.Authenticator] 는 non-suspend 컨텍스트라 여기서만 [Call] 을 쓴다
     * — suspend 버전을 runBlocking 으로 감싸면 Retrofit 이 만든 콜백 스레드를 기다리게 되어
     * OkHttp 디스패처가 포화된 상황에서 교착될 수 있다. 동기 execute() 는 그 위험이 없다.
     */
    @POST("api/v1/auth/reissue")
    fun reissue(@Body request: RefreshTokenRequestDto): Call<TokenResponse>

    /** 204 No Content, 멱등. 이미 무효화된 리프레시를 보내도 성공한다. */
    @POST("api/v1/auth/logout")
    suspend fun logout(@Body request: RefreshTokenRequestDto)
}
