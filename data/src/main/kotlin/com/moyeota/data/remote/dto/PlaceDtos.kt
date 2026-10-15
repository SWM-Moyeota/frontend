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
        // roadName 만 nullable 이다. 서버 place/search/domain/Place 는 record 라 검증이 없고,
        // KakaoPlaceClient 가 카카오의 road_address_name 을 그대로 담아 내보낸다 — 도로명이 없는
        // 장소(지번만 있는 건물·지하철역 등)에서 카카오가 null 을 주면 그 null 이 앱까지 온다.
        // kotlinx.serialization 은 **키 누락만** 기본값으로 떨어뜨리고 명시적 null 은 타입 예외라,
        // String 으로 두면 그런 장소 한 건 때문에 검색 결과 목록 전체가 파싱 실패한다.
        //
        // 전역 coerceInputValues 로 막지 않은 이유: 그 설정은 기본값을 가진 모든 non-null 필드에 걸려
        // accessToken(→ ""), chatRoomId(→ 0), 파티 status(→ "") 의 null 까지 조용히 삼킨다.
        // 빈 Bearer 로 401 루프를 도는 쪽이 파싱 실패보다 훨씬 늦게 발견된다. 그래서 범위를 여기로 좁혔다.
        val roadName: String? = null,
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
        // [PlaceSearchListResponse.PlaceSearchItem.roadName] 과 같은 이유로 nullable.
        // 검색에서 roadName 이 빈 장소를 즐겨찾기에 저장하면 서버가 받은 값을 그대로 보관하므로
        // 조회 응답에도 같은 값이 돌아온다 — 여기서 안 막으면 홈 진입마다 즐겨찾기 조회가 실패한다.
        val roadName: String? = null,
        val latitude: Double = 0.0,
        val longitude: Double = 0.0,
        val placeSequence: Int = 0,
    )
}
