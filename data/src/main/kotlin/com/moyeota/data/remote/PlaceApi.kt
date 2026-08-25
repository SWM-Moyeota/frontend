package com.moyeota.data.remote

import com.moyeota.data.remote.dto.FavoritePlaceListResponse
import com.moyeota.data.remote.dto.FavoritePlaceRequestDto
import com.moyeota.data.remote.dto.PlaceSearchListResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

// 경로 기준: origin/develop place/interfaces/PlaceSearchController.java, FavoritePlaceController.java
// 주의: backend/http/place.http 는 실제 컨트롤러와 완전히 다른 옛 경로(/api/places/favorites/...)라 신뢰하지 않는다.
// userId 가 경로가 아닌 쿼리 파라미터인 점에 유의 (백엔드 TODO: 추후 인증에서 추출).
interface PlaceApi {
    @GET("api/v1/places")
    suspend fun searchPlaces(@Query("query") query: String): PlaceSearchListResponse

    // 204 No Content
    @POST("api/v1/users/me/favorite-places")
    suspend fun addFavoritePlace(
        @Query("userId") userId: Long,
        @Body request: FavoritePlaceRequestDto,
    )

    @GET("api/v1/users/me/favorite-places")
    suspend fun getFavoritePlaces(@Query("userId") userId: Long): FavoritePlaceListResponse
}
