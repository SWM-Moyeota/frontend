package com.moyeota.data.remote.dto

import kotlinx.serialization.Serializable

// 백엔드 report 도메인 record 와 필드 1:1 대응
// (report/application/dto/ReportRequest.java, ReportResponse.java, CallResultRequest.java)

/**
 * POST /api/v1/reports 요청 본문 (백엔드 ReportRequest — 세 필드 모두 박싱 타입이라 null 허용).
 * 사유(reason) 필드는 서버에 없다 — 임의로 추가하지 말 것.
 */
@Serializable
data class ReportRequestDto(
    val partyId: Long? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

/** POST /api/v1/reports 응답 (백엔드 ReportResponse) */
@Serializable
data class ReportResponse(
    val reportId: Long,
)

/** PATCH /api/v1/reports/call-result 요청 본문 (백엔드 CallResultRequest — @NotNull) */
@Serializable
data class CallResultRequestDto(
    val called: Boolean,
)
