package com.moyeota.data.remote.dto

import kotlinx.serialization.Serializable

// GET /api/v1/dispatch/rides/{partyId}/{memberId}
// 백엔드 dispatch/application/dto/DriverLocationResponse(double longitude, double latitude)
// 원시 double 이라 항상 채워져 온다. 순서가 longitude 먼저인 점에 유의(이름 기반 역직렬화라 영향은 없다).
@Serializable
data class DriverLocationResponse(
    val longitude: Double = 0.0,
    val latitude: Double = 0.0,
)
