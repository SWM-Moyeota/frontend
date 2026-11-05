package com.moyeota.data.remote

import com.moyeota.data.remote.dto.AppConfigResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `GET /api/v1/config`.
 *
 * 서버 응답은 아직 `{"taxiEnabled":false}` 하나지만 **앱은 그 키를 받지 않는다** — 기사·배차가
 * 사라져 갈릴 모드가 없어졌고, 서버는 출시된 앱 호환으로 키만 남겨 두었다. 그래서 아래 JSON 들은
 * 일부러 `taxiEnabled` 를 싣는다: 「모르는 키를 조용히 흘린다」가 이 매퍼의 남은 계약이다.
 */
class AppConfigMappersTest {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 모르는 필드가 늘어나도 깨지지 않는다 — 서버가 `taxiEnabled` 를 계속 보내도, 다른 키를 더해도
     * 역직렬화가 살아 있어야 한다. 여기서 터지면 **앱 시작이 막힌다**(설정은 시작 때 한 번 읽는다).
     */
    @Test
    fun `모르는 필드가 늘어나도 깨지지 않고 앱으로 새지 않는다`() {
        val config = json
            .decodeFromString<AppConfigResponse>("""{"taxiEnabled":true,"minVersion":"1.2.0"}""")
            .toAppConfig()

        // 모르는 키는 아무것도 채우지 않는다 — 버전 기준만 읽는 DTO 다.
        assertNull(config.minVersionCode)
        assertNull(config.recommendedVersionCode)
    }

    @Test
    fun `응답이 비어도 기본값으로 떨어진다`() {
        val config = json.decodeFromString<AppConfigResponse>("""{}""").toAppConfig()

        assertNull(config.minVersionCode)
        assertNull(config.recommendedVersionCode)
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
