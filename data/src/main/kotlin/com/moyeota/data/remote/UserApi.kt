package com.moyeota.data.remote

import com.moyeota.data.remote.dto.UserProfileResponse
import retrofit2.http.GET

/**
 * 경로 기준: user/interfaces/LocalUserController.java (`@RequestMapping("/api/v1/local")`).
 *
 * **[AuthApi] 와 붙이면 안 된다.** 프리픽스가 `/api/v1/local` 로 다른 것도 이유지만, 결정적인 건
 * 클라이언트가 달라야 한다는 점이다 — [AuthApi] 는 Bearer 도 401 재발급도 붙지 않는 별도
 * OkHttp 클라이언트를 쓴다([NetworkModule.create] 참조). 이 조회는 **토큰 필수**라
 * (실측: 무토큰 → 401 `{"code":"USER005","message":"로그인이 필요합니다."}`)
 * 인증 클라이언트 쪽 Retrofit 으로 만들어야 하고, 그래서 인터페이스를 따로 둔다.
 *
 * 같은 컨트롤러의 가입(`POST /api/v1/local/users`)은 permitAll 구간이지만 앱은 쓰지 않는다
 * — 가입은 `POST /api/v1/auth/register` 로 간다([AuthApi.register]).
 */
interface UserApi {

    /**
     * 200. 조회 주체는 토큰이 정한다(`@CurrentUser`) — 경로·쿼리에 식별자를 싣지 않는다.
     *
     * 프리픽스가 `/api/v1/users` 가 아니라 `/api/v1/local` 이다. 즐겨찾기(`/api/v1/users/me/...`)와
     * 헷갈려 `users` 로 적으면 404 가 아니라 **401** 로 떨어져(Security 가 미매핑 경로도 보호한다)
     * 세션 만료로 오인하기 쉽다. 값은 [AuthenticatedPathContractTest] 가 못 박는다.
     */
    @GET("api/v1/local/users/info")
    suspend fun getMyProfile(): UserProfileResponse
}
