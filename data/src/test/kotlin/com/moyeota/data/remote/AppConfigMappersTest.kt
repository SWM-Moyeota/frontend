package com.moyeota.data.remote

import com.moyeota.data.remote.dto.AppConfigResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun `taxiEnabled true 를 그대로 옮긴다`() {
        val response = json.decodeFromString<AppConfigResponse>("""{"taxiEnabled":true}""")
        assertTrue(response.toAppConfig().taxiEnabled)
    }

    @Test
    fun `필드가 빠지면 동승 모드(false) 다 — 모를 땐 실배포 모드`() {
        val response = json.decodeFromString<AppConfigResponse>("""{}""")
        assertFalse(response.toAppConfig().taxiEnabled)
    }

    @Test
    fun `모르는 필드가 늘어나도 깨지지 않는다`() {
        val response = json.decodeFromString<AppConfigResponse>("""{"taxiEnabled":true,"minVersion":"1.2.0"}""")
        assertTrue(response.toAppConfig().taxiEnabled)
    }

    // ─────────────── 강제 업데이트 기준 (서버가 아직 주지 않는 필드) ───────────────

    @Test
    fun `지금 서버 응답에는 버전 기준이 없어 둘 다 null 이다`() {
        // 2026-11-01 로컬 :8080 실측 응답 모양. null 이면 게이트가 아무도 막지 않는다.
        val config = json.decodeFromString<AppConfigResponse>("""{"taxiEnabled":false}""").toAppConfig()

        assertNull(config.minVersionCode)
        assertNull(config.recommendedVersionCode)
    }

    @Test
    fun `서버가 버전 기준을 주기 시작하면 그대로 옮긴다`() {
        // 백엔드에 필드를 더하는 날 앱 배포 없이 바로 걸려야 한다 — 그게 이 필드를 미리 둔 이유다.
        val config = json
            .decodeFromString<AppConfigResponse>(
                """{"taxiEnabled":false,"minVersionCode":26110102,"recommendedVersionCode":26110103}""",
            )
            .toAppConfig()

        assertEquals(26110102, config.minVersionCode)
        assertEquals(26110103, config.recommendedVersionCode)
    }
}
