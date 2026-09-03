package com.moyeota.data.repository

import com.moyeota.data.remote.ReportApi
import com.moyeota.data.remote.toRequestDto
import com.moyeota.data.remote.dto.CallResultRequestDto
import com.moyeota.domain.model.NewReport
import com.moyeota.domain.repository.ReportRepository

class RemoteReportRepository(
    private val api: ReportApi,
) : ReportRepository {

    override suspend fun report(request: NewReport): Long = api.report(request.toRequestDto()).reportId

    override suspend fun confirmCallResult(reportId: Long, called: Boolean) {
        api.confirmCallResult(reportId, CallResultRequestDto(called = called))
    }
}
