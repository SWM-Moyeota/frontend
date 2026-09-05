package com.moyeota.domain.model

/**
 * 매칭방 생성 요청.
 * 서버 검증 규칙(위반 시 400): capacity 1~3, radius 100~500, 좌표는 한국 범위 내.
 *
 * 방장 id 필드는 없다. 서버가 자동 기사 매칭으로 전환하며 방장 개념을 없앴고,
 * 방 생성자는 Bearer 토큰 주체로만 기록된다(`OpenPartyRequest.creatorId` 는 구버전 호환용으로
 * 받기만 하고 버려진다). 앱이 실어 보낼 값이 아예 없다.
 */
data class NewParty(
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
