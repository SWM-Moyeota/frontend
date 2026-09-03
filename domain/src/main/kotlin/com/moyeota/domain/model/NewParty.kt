package com.moyeota.domain.model

/**
 * 매칭방 생성 요청.
 * 서버 검증 규칙(위반 시 400): capacity 1~3, radius 100~500, 좌표는 한국 범위 내.
 */
data class NewParty(
    /**
     * **서버는 이 값을 무시한다 — 방장은 Bearer 토큰의 주체로 정해진다.**
     *
     * 서버가 `@CurrentUser` 로 전환되면서 요청 본문에서 방장 id 가 빠졌고, 앱도 전송하지 않는다
     * (`OpenPartyRequestDto` 참조). 필드를 남겨 둔 건 순전히 하위호환 — 지우면 호출부
     * (`DestinationConfirmRoute`)와 화면 코드가 함께 깨지는데, 얻는 게 없다.
     *
     * 그러니 여기에 무엇을 넣든 서버 기록은 달라지지 않는다. 로컬에서 생성 직후 만들어 보여 주는
     * `Ride.hostId` 표시값으로만 쓰인다.
     */
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
