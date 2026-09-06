package com.moyeota.domain.model

/**
 * 방에 배정된 기사 요약. 백엔드 `driver/api/DriverSummary(seats, plateNumber, type)` 와 1:1.
 *
 * 주의: 기사 **이름·별점·연락처는 백엔드가 아직 내려주지 않는다**(DriverSummary 에 필드 자체가 없음).
 * 배차 현황 화면에서 기사명/별점이 필요하면 백엔드에 필드 추가를 요청해야 한다.
 */
data class AssignedDriver(
    /** 차량 좌석 수. */
    val seats: Int?,
    /** 차량 번호판. 예: "12가 3456". */
    val plateNumber: String,
    /** 차종. 서버 필드명은 type. 예: "쏘나타". */
    val vehicleType: String,
)

/** 기사 현재 위치. 폴링으로 갱신한다. */
data class DriverLocation(
    val latitude: Double,
    val longitude: Double,
)

/**
 * 출발지→도착지 경로 추정치.
 * [encodedPath] 는 Google Encoded Polyline(정밀도 1e5, lat→lng 순)이라 그대로 디코드해 지도에 그린다.
 */
data class RouteEstimate(
    val estimatedFare: Int,
    val estimatedMinutes: Int,
    val encodedPath: String,
)
