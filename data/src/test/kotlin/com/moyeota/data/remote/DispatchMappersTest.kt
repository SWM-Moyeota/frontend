package com.moyeota.data.remote

import com.moyeota.data.remote.dto.DriverLocationResponse
import com.moyeota.data.repository.RemoteDispatchRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class DispatchMappersTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `서버가 longitude 를 먼저 주지만 도메인은 위도 경도를 이름으로 구분한다`() {
        // DriverLocationResponse(double longitude, double latitude) — 순서가 뒤바뀐 함정.
        val decoded = json.decodeFromString<DriverLocationResponse>(
            """{"longitude":126.9780,"latitude":37.5665}""",
        )

        val location = decoded.toDriverLocation()

        assertEquals(37.5665, location.latitude, 0.000001)
        assertEquals(126.9780, location.longitude, 0.000001)
    }

    @Test
    fun `Repository 가 API 응답을 그대로 도메인 위치로 넘긴다`() = runBlocking {
        val api = FakeDispatchApi(DriverLocationResponse(longitude = 127.0276, latitude = 37.4979))

        val location = RemoteDispatchRepository(api).getDriverLocation(partyId = 7)

        assertEquals(37.4979, location.latitude, 0.000001)
        assertEquals(127.0276, location.longitude, 0.000001)
        // memberId 는 더 이상 넘기지 않는다 — 서버가 Bearer 토큰에서 뽑는다(@LoginUser).
        assertEquals(7L, api.lastPartyId)
    }

    private class FakeDispatchApi(private val response: DriverLocationResponse) : DispatchApi {
        var lastPartyId: Long? = null

        override suspend fun getDriverLocation(partyId: Long): DriverLocationResponse {
            lastPartyId = partyId
            return response
        }
    }
}
