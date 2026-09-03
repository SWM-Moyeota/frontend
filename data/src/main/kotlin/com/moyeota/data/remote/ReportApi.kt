package com.moyeota.data.remote

import com.moyeota.data.remote.dto.CallResultRequestDto
import com.moyeota.data.remote.dto.ReportRequestDto
import com.moyeota.data.remote.dto.ReportResponse
import retrofit2.http.Body
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

// 경로 기준: feature/driver-report report/interfaces/ReportController.java
// (@RequestMapping("/api/v1/reports"))
interface ReportApi {
    @POST("api/v1/reports")
    suspend fun report(@Body request: ReportRequestDto): ReportResponse

    // 204 No Content — 응답 본문이 없다.
    @PATCH("api/v1/reports/{reportId}/call-result")
    suspend fun confirmCallResult(
        @Path("reportId") reportId: Long,
        @Body request: CallResultRequestDto,
    )
}
