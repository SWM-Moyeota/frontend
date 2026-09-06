package com.moyeota.data.remote

import com.moyeota.data.remote.dto.CallResultRequestDto
import com.moyeota.data.remote.dto.ReportRequestDto
import com.moyeota.data.remote.dto.ReportResponse
import retrofit2.http.Body
import retrofit2.http.PATCH
import retrofit2.http.POST

// 경로 기준: report/interfaces/ReportController.java (@RequestMapping("/api/v1/reports")).
// 두 엔드포인트 모두 @CurrentUser — 신고자는 Bearer 토큰이 정한다(apiClient 로 생성해야 한다).
// call-result 는 백엔드가 {reportId} 경로를 오간 이력이 있다 — 현행 컨트롤러는 reportId 없이
// 토큰 주체의 최근 신고에 기록한다 (AuthenticatedPathContractTest 가 경로를 못 박는다).
interface ReportApi {
    /** 긴급 신고 저장. 200 {reportId} */
    @POST("api/v1/reports")
    suspend fun report(@Body request: ReportRequestDto): ReportResponse

    /** 112 통화 여부 저장. 204 No Content — 응답 본문 없음 */
    @PATCH("api/v1/reports/call-result")
    suspend fun confirmCallResult(@Body request: CallResultRequestDto)
}
