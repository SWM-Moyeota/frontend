package com.moyeota.data.remote

import com.moyeota.data.remote.dto.FcmTokenRequest
import com.moyeota.data.remote.dto.UpdateProfileRequest
import com.moyeota.data.remote.dto.UserProfileResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.PUT

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

    /**
     * 내 프로필 수정. 204 No Content. 기준: `user/interfaces/UserController.java`
     * (프리픽스가 `/api/v1/users` — [getMyProfile] 의 `/api/v1/local` 과 다르다).
     *
     * **PATCH 다.** 서버가 `@PatchMapping` 만 매핑하므로 PUT/POST 로 보내면 405 다.
     * 본문에서 빠진 필드는 건드리지 않는다 — 지우기는 표현할 수 없다
     * ([com.moyeota.data.remote.dto.UpdateProfileRequest] 참조).
     *
     * 닉네임 형식 위반은 400 `USER107`, 중복은 409 `USER108` 이다.
     */
    @PATCH("api/v1/users/me")
    suspend fun updateProfile(@Body request: UpdateProfileRequest)

    /**
     * 푸시 토큰 등록. 204 No Content, 멱등(같은 토큰을 다시 보내도 덮어쓰기만 한다).
     * 기준: `user/interfaces/UserController.java` — 이쪽만 프리픽스가 `/api/v1/users` 다
     * ([getMyProfile] 의 `/api/v1/local` 과 다르다. 둘을 바꿔 쓰면 401 로 떨어져 세션 만료로 오인한다).
     *
     * 이 토큰이 있어야 기사 도착 시 `FcmPassengerNotifier` 가 이 사용자를 멀티캐스트 대상에 넣는다
     * — 등록이 없으면 서버는 조용히 `"FCM 토큰이 등록된 승객이 없음"` 을 로그로 남기고 끝낸다.
     */
    @PUT("api/v1/users/me/fcm-token")
    suspend fun registerFcmToken(@Body request: FcmTokenRequest)

    /**
     * 푸시 토큰 해제. 204 No Content.
     *
     * **로그아웃 API 가 대신 해 주지 않는다** — 서버 `UserFcmTokenService` KDoc 이 명시하듯
     * 로그아웃은 리프레시 토큰만 받으므로, 앱이 로그아웃 **직전에** 따로 불러야 한다.
     * 순서가 뒤집혀 세션을 먼저 비우면 Bearer 가 사라져 401 이 나고, 서버에는 죽은 토큰이 남아
     * 다음 사용자가 이 기기로 로그인할 때까지 남의 도착 알림이 이 기기로 온다.
     */
    @DELETE("api/v1/users/me/fcm-token")
    suspend fun deleteFcmToken()
}
