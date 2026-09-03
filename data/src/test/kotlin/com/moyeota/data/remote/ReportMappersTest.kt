package com.moyeota.data.remote

import com.moyeota.data.remote.dto.CallResultRequestDto
import com.moyeota.data.remote.dto.ReportRequestDto
import com.moyeota.data.remote.dto.ReportResponse
import com.moyeota.data.repository.RemoteReportRepository
import com.moyeota.domain.model.NewReport
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportMappersTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `NewReport 를 서버 ReportRequest shape 으로 변환한다`() {
        val dto = NewReport(partyId = 7, latitude = 37.5665, longitude = 126.9780).toRequestDto()

        assertEquals(7L, dto.partyId)
        assertEquals(37.5665, dto.latitude!!, 0.000001)

        val encoded = json.encodeToString(dto)
        assertTrue(encoded.contains("\"partyId\":7"))
    }

    // 서버는 모르는 필드를 조용히 버린다(실측). 그래서 reporterId 가 남아 있어도 실패로 드러나지 않는다 —
    // 본문에 절대 나가지 않는 것을 여기서 못 박는다.
    @Test
    fun `신고 본문에 reporterId 를 싣지 않는다 — 신고자는 토큰이 정한다`() {
        val encoded = json.encodeToString(NewReport(partyId = 7).toRequestDto())

        assertTrue(!encoded.contains("reporterId"))
    }

    @Test
    fun `위치를 못 얻으면 좌표 없이 신고한다`() {
        val dto = NewReport(partyId = 7).toRequestDto()

        assertNull(dto.latitude)
        assertNull(dto.longitude)
    }

    @Test
    fun `신고 응답의 reportId 를 그대로 돌려준다`() = runBlocking {
        val api = FakeReportApi(ReportResponse(reportId = 55))

        val reportId = RemoteReportRepository(api).report(NewReport(partyId = 7))

        assertEquals(55L, reportId)
        assertEquals(7L, api.lastReport?.partyId)
    }

    @Test
    fun `통화 여부 확정은 reportId 와 called 를 보낸다`() = runBlocking {
        val api = FakeReportApi(ReportResponse(reportId = 55))

        RemoteReportRepository(api).confirmCallResult(reportId = 55, called = true)

        assertEquals(55L, api.lastCallResult?.first)
        assertEquals(CallResultRequestDto(called = true), api.lastCallResult?.second)
    }

    @Test
    fun `서버 신고 응답 JSON 을 역직렬화한다`() {
        assertEquals(55L, json.decodeFromString<ReportResponse>("""{"reportId":55}""").reportId)
    }

    private class FakeReportApi(private val response: ReportResponse) : ReportApi {
        var lastReport: ReportRequestDto? = null
        var lastCallResult: Pair<Long, CallResultRequestDto>? = null

        override suspend fun report(request: ReportRequestDto): ReportResponse {
            lastReport = request
            return response
        }

        override suspend fun confirmCallResult(reportId: Long, request: CallResultRequestDto) {
            lastCallResult = reportId to request
        }
    }
}
