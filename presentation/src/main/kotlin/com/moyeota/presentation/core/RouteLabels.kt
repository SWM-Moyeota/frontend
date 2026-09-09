package com.moyeota.presentation.core

import com.moyeota.core.designsystem.component.latLngOrNull
import com.moyeota.domain.model.Ride
import com.moyeota.presentation.core.location.UserCoordinates

/**
 * 지도 위 요약 칩 문구(「예상 12분 · 6.2km」). **잰 값만 쓴다.**
 *
 * 소요 시간은 서버가 방에 박아 둔 `estimateTime`, 거리는 서버 경로 폴리라인에서 직접 잰 값이다.
 * 서버는 거리를 내려주지 않으므로 출발·도착 **직선거리로 대체하지 않는다** — 실제 주행 거리보다
 * 늘 짧아 요금과 어긋나 보인다. 둘 다 없으면 null 이고, 호출부가 칩을 통째로 감춘다
 * (틀린 값보다 없는 편이 낫다 — 16 경로 미리보기 칩과 같은 규칙).
 */
fun buildRouteChipLabel(estimatedMinutes: Int?, routeKm: Double?): String? {
    val time = estimatedMinutes?.let { "예상 ${it}분" }
    val distance = routeKm?.let { "%.1fkm".format(it) }
    return listOfNotNull(time, distance).takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/**
 * 내 위치 → 탑승 위치 거리 표기. 1km 미만은 10m 단위 m, 그 이상은 소수 1자리 km.
 *
 * 「약」을 붙이는 건 GPS 오차가 그만큼 있다는 뜻이다. **「도보 N분」은 쓰지 않는다** —
 * 보행 속도를 지어내야 하고, 거리와 달리 잰 값이 아니다.
 */
fun pickupDistanceLabel(meters: Double): String =
    if (meters < 1_000) "약 ${(meters / 10).toInt() * 10}m" else "약 %.1fkm".format(meters / 1000.0)

/**
 * 내 위치 → 방의 탑승 위치 **직선거리**(m). 둘 중 하나라도 못 쓸 좌표면 null.
 *
 * 좌표 검증을 [latLngOrNull] 에 맡기는 이유는 지도와 같다 — 서버가 위경도를 뒤바꿔 주는 결함(QA D-1)이
 * 있어, 범위를 벗어난 값으로 거리를 재면 「약 9,000km」 같은 숫자가 화면에 나간다.
 */
fun pickupDistanceMeters(ride: Ride, myLocation: UserCoordinates?): Double? {
    val pickup = latLngOrNull(ride.originLat, ride.originLng) ?: return null
    val me = latLngOrNull(myLocation?.latitude, myLocation?.longitude) ?: return null
    return pickup.distanceTo(me).takeIf { it.isFinite() }
}
