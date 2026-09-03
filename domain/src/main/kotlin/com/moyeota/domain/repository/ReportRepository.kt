package com.moyeota.domain.repository

import com.moyeota.domain.model.NewReport

interface ReportRepository {
    /**
     * POST /api/v1/reports — 긴급 신고 사전 기록. 112 다이얼 전환 직전에 호출한다.
     * @return 생성된 reportId. [confirmCallResult] 에 그대로 넘긴다.
     *
     * 서버는 **운행 중(IN_RIDE)인 방의 멤버**만 허용한다. 그 외에는 실패한다.
     */
    suspend fun report(request: NewReport): Long

    /**
     * PATCH /api/v1/reports/{reportId}/call-result — 다이얼에서 복귀한 뒤 통화 여부 보강. 응답 본문 없음(204).
     * 신고당 **한 번만** 가능하다(두 번째 호출은 서버가 거절).
     */
    suspend fun confirmCallResult(reportId: Long, called: Boolean)
}
