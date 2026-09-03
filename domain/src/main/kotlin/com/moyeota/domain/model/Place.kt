package com.moyeota.domain.model

// 장소 검색 결과 1건. 백엔드 PlaceSearchResponse(name, roadName, latitude, longitude) 대응.
data class Place(
    val name: String,
    val roadName: String,
    val latitude: Double,
    val longitude: Double,
)

/**
 * 좌표를 되짚은 주소 1건. 백엔드 AddressResponse 대응.
 *
 * [address] 는 서버가 도로명 우선(없으면 지번)으로 조합해 준 표시 전용 값이라 항상 비어 있지 않다.
 * 앱에서 다시 조합하지 말고 그대로 노출한다 — 조합 규칙이 서버에 있어야 나중에 바뀌어도 앱이 안 깨진다.
 * [roadAddress] 는 시골/신규 필지처럼 도로명이 없는 좌표에서 null 이다.
 */
data class ReverseAddress(
    val address: String,
    val roadAddress: String?,
    val jibunAddress: String?,
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
