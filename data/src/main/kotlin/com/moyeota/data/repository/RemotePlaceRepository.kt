package com.moyeota.data.repository

import com.moyeota.data.remote.PlaceApi
import com.moyeota.data.remote.toFavoritePlace
import com.moyeota.data.remote.toFavoriteRequestDto
import com.moyeota.data.remote.toPlace
import com.moyeota.domain.model.FavoritePlace
import com.moyeota.domain.model.Place
import com.moyeota.domain.repository.PlaceRepository

class RemotePlaceRepository(
    private val api: PlaceApi,
) : PlaceRepository {

    override suspend fun searchPlaces(query: String): List<Place> =
        api.searchPlaces(query).list.map { it.toPlace() }

    override suspend fun addFavoritePlace(userId: Long, place: Place) {
        api.addFavoritePlace(userId, place.toFavoriteRequestDto())
    }

    // 서버가 정렬을 보장한다는 언급이 없어(placeSequence 만 내려줌) 앱에서 순서를 확정한다.
    override suspend fun getFavoritePlaces(userId: Long): List<FavoritePlace> =
        api.getFavoritePlaces(userId).places
            .map { it.toFavoritePlace() }
            .sortedBy { it.sequence }
}
