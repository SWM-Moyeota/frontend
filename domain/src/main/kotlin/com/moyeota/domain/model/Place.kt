package com.moyeota.domain.model

// 장소 검색 결과 1건. 백엔드 PlaceSearchResponse(name, roadName, latitude, longitude) 대응.
data class Place(
    val name: String,
    val roadName: String,
    val latitude: Double,
    val longitude: Double,
)

// 자주 가는 장소. 백엔드 FavoritePlaceResponse 대응.
// sequence 는 등록 순서(1부터), 목록 정렬 기준으로 쓴다.
data class FavoritePlace(
    val name: String,
    val roadName: String,
    val latitude: Double,
    val longitude: Double,
    val sequence: Int,
)
