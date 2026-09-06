package com.moyeota.data.remote.dto

import kotlinx.serialization.Serializable

// 백엔드 place/application/dto/*.java (origin/develop) 와 필드 1:1

// GET /api/v1/places?query=
@Serializable
data class PlaceSearchListResponse(
    val list: List<PlaceSearchItem> = emptyList(),
) {
    @Serializable
    data class PlaceSearchItem(
        val name: String = "",
        val roadName: String = "",
        val latitude: Double = 0.0,
        val longitude: Double = 0.0,
    )
}

// GET /api/v1/places/reverse?latitude=&longitude= (AddressResponse)
// address 는 서버가 roadAddress ?: jibunAddress 로 조합해 주는 표시용 값(Address.display()).
// 네이버 응답에서 roadaddr/addr 결과 중 하나만 오면 나머지가 null 이 되므로 세 필드 모두 nullable 로 방어한다.
// (실측: 35.0,128.0 → roadAddress=null)
@Serializable
data class AddressResponseDto(
    val address: String? = null,
    val roadAddress: String? = null,
    val jibunAddress: String? = null,
)

// POST /api/v1/users/me/favorite-places?userId= 요청 본문 (FavoritePlaceRequest)
// 주의: 검색 응답은 name 인데 즐겨찾기 요청/응답은 placeName 이다. 이름이 다르다.
@Serializable
data class FavoritePlaceRequestDto(
    val placeName: String,
    val roadName: String,
    val latitude: Double,
    val longitude: Double,
)

// GET /api/v1/users/me/favorite-places?userId=
@Serializable
data class FavoritePlaceListResponse(
    val places: List<FavoritePlaceItem> = emptyList(),
) {
    @Serializable
    data class FavoritePlaceItem(
        val placeName: String = "",
        val roadName: String = "",
        val latitude: Double = 0.0,
        val longitude: Double = 0.0,
        val placeSequence: Int = 0,
    )
}
