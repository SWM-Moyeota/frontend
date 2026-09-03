package com.moyeota.data.remote

import com.moyeota.data.remote.dto.AddressResponseDto
import com.moyeota.data.remote.dto.FavoritePlaceListResponse
import com.moyeota.data.remote.dto.FavoritePlaceRequestDto
import com.moyeota.data.remote.dto.PlaceSearchListResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

// 경로 기준: origin/develop place/interfaces/PlaceSearchController.java, FavoritePlaceController.java
// 주의: backend/http/place.http 는 실제 컨트롤러와 완전히 다른 옛 경로(/api/places/favorites/...)라 신뢰하지 않는다.
// 즐겨찾기 두 엔드포인트는 @CurrentUser 로 전환됐다 — 소유자는 토큰이 정하고 userId 파라미터는 없다.
// **장소 검색·역지오코딩도 더 이상 공개가 아니다** — Security 가 전 경로를 인증 필수로 바꾸면서
// 무토큰 호출이 401 `USER005` 로 막힌다(실측). 즉 이 인터페이스 전체가 로그인 후에만 쓸 수 있다.
interface PlaceApi {
    @GET("api/v1/places")
    suspend fun searchPlaces(@Query("query") query: String): PlaceSearchListResponse

    /**
     * 좌표 → 주소. 주소가 없는 좌표(바다 등)는 404 ADDRESS_NOT_FOUND 로 내려오는데
     * 이는 정상 유저 행동이라 예외가 아니라 null 로 다뤄야 해서 [Response] 로 받는다.
     * 판별과 예외 전환은 PlaceMappers.toReverseAddressOrNull() 이 담당한다.
     */
    @GET("api/v1/places/reverse")
    suspend fun reverseGeocode(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
    ): Response<AddressResponseDto>

    // 204 No Content. 소유자는 Bearer 토큰에서 나온다(@CurrentUser) — userId 쿼리 파라미터는 사라졌다.
    @POST("api/v1/users/me/favorite-places")
    suspend fun addFavoritePlace(@Body request: FavoritePlaceRequestDto)

    @GET("api/v1/users/me/favorite-places")
    suspend fun getFavoritePlaces(): FavoritePlaceListResponse
}
