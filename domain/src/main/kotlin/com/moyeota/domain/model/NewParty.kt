package com.moyeota.domain.model

// 매칭방 생성 요청. 백엔드 OpenPartyRequest 와 필드 1:1.
// 서버 검증 규칙(위반 시 400): capacity 0~3, radius 100~500, 좌표는 한국 범위 내.
data class NewParty(
    val hostId: Long,
    val departureLat: Double,
    val departureLng: Double,
    val destinationLat: Double,
    val destinationLng: Double,
    val departure: String,
    val destination: String,
    val capacity: Int,
    val departureRadius: Int = DEFAULT_RADIUS_METERS,
    val destinationRadius: Int = DEFAULT_RADIUS_METERS,
) {
    companion object {
        const val DEFAULT_RADIUS_METERS = 500
    }
}
