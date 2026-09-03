package com.moyeota.domain.model

/**
 * 긴급 신고 사전 기록. 112 다이얼 전환 **직전에** 호출한다.
 * 백엔드 `report/application/dto/ReportRequest` 와 필드 1:1.
 *
 * **신고자 id 는 담지 않는다** — 서버가 Bearer 토큰에서 뽑는다(@CurrentUser).
 * 화면이 "누가 신고하는지"를 알아낼 필요가 없다.
 *
 * 서버 제약(ReportApplicationService.report): partyId 가 null 이거나 신고자가 그 방에서
 * **운행 중(IN_RIDE)** 상태가 아니면 거절한다(실측 403 `REPORT_NOT_ALLOWED`
 * "운행 중에만 신고할 수 있습니다"). 즉 탑승 중에만 신고할 수 있다.
 */
data class NewReport(
    val partyId: Long,
    /** 신고 시점 위치. 못 얻으면 null 로 보내도 서버가 저장한다. */
    val latitude: Double? = null,
    val longitude: Double? = null,
)
