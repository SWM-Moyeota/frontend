package com.moyeota.domain.model

/**
 * 출발지→도착지 경로 추정치(`POST /api/v1/matching/routes`).
 * [encodedPath] 는 Google Encoded Polyline(정밀도 1e5, lat→lng 순)이라 그대로 디코드해 지도에 그린다.
 *
 * 16 목적지 확인 모달이 방을 만들기 **전에** 예상 요금·소요시간을 보여 줄 때 쓴다
 * ([com.moyeota.domain.repository.RideRepository.previewRoute]). 방을 만든 뒤의 값은 서버가 방에
 * 박아 두므로 [Ride.estimatedFare]·[Ride.estimatedMinutes]·[Ride.routePolyline] 에서 온다.
 *
 * 2026-11-05 까지 `Dispatch.kt` 안에 `AssignedDriver`·`DriverLocation` 과 함께 있었다. 그 둘은 택시
 * 기능과 함께 지웠지만 이것은 **택시와 무관한 살아 있는 타입**이라 파일을 갈라 남겼다 — 이름이
 * 배차를 가리키던 것은 처음부터 어긋난 배치였다.
 */
data class RouteEstimate(
    val estimatedFare: Int,
    val estimatedMinutes: Int,
    val encodedPath: String,
)
