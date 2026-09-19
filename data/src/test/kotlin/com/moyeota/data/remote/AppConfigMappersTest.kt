package com.moyeota.data.remote

import com.moyeota.data.remote.dto.AppConfigResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** `GET /api/v1/config` — 서버 `AppConfigResponse(boolean taxiEnabled)` 기준 */
class AppConfigMappersTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `taxiEnabled false 를 그대로 옮긴다`() {
        val response = json.decodeFromString<AppConfigResponse>("""{"taxiEnabled":false}""")
        assertFalse(response.toAppConfig().taxiEnabled)
    }

    @Test
    fun `필드가 빠지면 서버 기본값과 같은 true 다`() {
        val response = json.decodeFromString<AppConfigResponse>("""{}""")
        assertTrue(response.toAppConfig().taxiEnabled)
    }

    @Test
    fun `모르는 필드가 늘어나도 깨지지 않는다`() {
        val response = json.decodeFromString<AppConfigResponse>("""{"taxiEnabled":true,"minVersion":"1.2.0"}""")
        assertTrue(response.toAppConfig().taxiEnabled)
    }
}
