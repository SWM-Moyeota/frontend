package com.moyeota.data.remote.dto

import kotlinx.serialization.Serializable

// 백엔드 report/application/dto/*.java 와 필드 1:1

// POST /api/v1/reports 요청 본문.
// 신고자는 Bearer 토큰에서 나온다(@CurrentUser). 서버 record 는 `ReportRequest(Long partyId, Double latitude, Double longitude)`.
// reporterId 를 남겨 보내도 Jackson 이 조용히 버리므로(실측: 무시하고 403 비즈니스 응답) 실패로 드러나지 않는다 —
// 그래서 더 위험하다. 서버가 안 보는 값을 계속 계산해 보내지 않도록 DTO 에서 지웠다.
@Serializable
data class ReportRequestDto(
    val partyId: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

// POST /api/v1/reports 응답
@Serializable
data class ReportResponse(
    val reportId: Long,
)

// PATCH /api/v1/reports/{reportId}/call-result 요청 본문. 응답은 204(본문 없음).
@Serializable
data class CallResultRequestDto(
    val called: Boolean,
)
