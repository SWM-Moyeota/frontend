package com.moyeota.data.repository

import com.moyeota.data.remote.PlaceApi
import com.moyeota.data.remote.toFavoritePlace
import com.moyeota.data.remote.toFavoriteRequestDto
import com.moyeota.data.remote.toPlace
import com.moyeota.data.remote.toReverseAddressOrNull
import com.moyeota.domain.model.FavoritePlace
import com.moyeota.domain.model.Place
import com.moyeota.domain.model.ReverseAddress
import com.moyeota.domain.repository.PlaceRepository

class RemotePlaceRepository(
    private val api: PlaceApi,
) : PlaceRepository {

    override suspend fun searchPlaces(query: String): List<Place> =
        api.searchPlaces(query).list.map { it.toPlace() }

    // 404 ADDRESS_NOT_FOUND → null 변환은 매퍼가 한다. 그 외 4xx/5xx 는 HttpException 으로 던져진다.
    override suspend fun reverseGeocode(latitude: Double, longitude: Double): ReverseAddress? =
        api.reverseGeocode(latitude, longitude).toReverseAddressOrNull()

    override suspend fun addFavoritePlace(place: Place) {
        api.addFavoritePlace(place.toFavoriteRequestDto())
    }

    // 서버가 정렬을 보장한다는 언급이 없어(placeSequence 만 내려줌) 앱에서 순서를 확정한다.
    override suspend fun getFavoritePlaces(): List<FavoritePlace> =
        api.getFavoritePlaces().places
            .map { it.toFavoritePlace() }
            .sortedBy { it.sequence }
}
