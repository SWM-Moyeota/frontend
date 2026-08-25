package com.moyeota.domain.repository

import com.moyeota.domain.model.FavoritePlace
import com.moyeota.domain.model.Place

interface PlaceRepository {
    /** GET /api/v1/places?query=. 카카오 로컬 검색 프록시라 결과가 0건일 수 있다. */
    suspend fun searchPlaces(query: String): List<Place>

    /**
     * POST /api/v1/users/me/favorite-places?userId=. 응답 본문 없음(204).
     * 서버가 400 을 주는 경우: 같은 이름 중복 등록, 10개 초과.
     * 성공 후 [getFavoritePlaces] 재조회 필요.
     */
    suspend fun addFavoritePlace(userId: Long, place: Place)

    /** GET /api/v1/users/me/favorite-places?userId=. sequence 오름차순 정렬해서 돌려준다. */
    suspend fun getFavoritePlaces(userId: Long): List<FavoritePlace>
}
