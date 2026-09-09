package com.moyeota.data.remote

import com.moyeota.data.remote.dto.LoginRequestDto
import com.moyeota.data.remote.dto.NicknameCheckRequestDto
import com.moyeota.data.remote.dto.NicknameCheckResponse
import com.moyeota.data.remote.dto.RefreshTokenRequestDto
import com.moyeota.data.remote.dto.RegisterRequestDto
import com.moyeota.data.remote.dto.RegisterResponse
import com.moyeota.data.remote.dto.TokenResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * 경로 기준: user/interfaces/AuthController.java (@RequestMapping("/api/v1/auth")).
 * 앱이 쓰는 인증 엔드포인트가 **전부 이 한 컨트롤러로 모였다** — 가입·로그인이 `/api/v1/users*` 에 있던
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

    /**
     * 가입 폼의 닉네임 중복 확인. 200 `{"exists":boolean}`.
     *
     * **`/api/v1/users` 가 아니라 여기(auth 프리픽스)에 있는 게 핵심이다.** 이 호출은 가입 도중,
     * 즉 로그인 전에 일어나므로 토큰이 없다. auth 구간만 permitAll 이라 경로도 이쪽이어야 하고,
     * 클라이언트도 Bearer 가 붙지 않는 이 API 여야 한다 — 만료된 토큰이 세션에 남아 있는 상태에서
     * 인증 클라이언트로 보내면 permitAll 경로인데도 JWT 필터에 걸려 401 이 날 수 있다.
     *
     * 형식이 틀리면 exists 판정 전에 400 `USER107` 이다(서버가 `Nickname` VO 를 먼저 만든다).
     */
    @POST("api/v1/auth/nickname/check")
    suspend fun checkNickname(@Body request: NicknameCheckRequestDto): NicknameCheckResponse
}
