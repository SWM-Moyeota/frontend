package com.moyeota.data.remote

import com.moyeota.data.remote.dto.CallResultRequestDto
import com.moyeota.data.remote.dto.ReportRequestDto
import com.moyeota.data.remote.dto.ReportResponse
import retrofit2.http.Body
import retrofit2.http.PATCH
import retrofit2.http.POST

// 경로 기준: report/interfaces/ReportController.java
// (@RequestMapping("/api/v1/reports"))
// 두 엔드포인트 모두 @CurrentUser 로 Bearer 토큰이 필수 — 승객 앱에는 아직 인증 계층이
// 없어 실서버에서는 401 이 난다 (알려진 한계, _workspace/90_emergency_contract.md 참고).
interface ReportApi {
    /** 긴급 신고 저장. 200 {reportId} */
    @POST("api/v1/reports")
    suspend fun report(@Body request: ReportRequestDto): ReportResponse

    /** 112 통화 여부 저장. 204 No Content — 응답 본문 없음 */
    @PATCH("api/v1/reports/call-result")
    suspend fun confirmCallResult(@Body request: CallResultRequestDto)
}
