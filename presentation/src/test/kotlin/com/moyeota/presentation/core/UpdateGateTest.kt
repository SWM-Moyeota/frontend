package com.moyeota.presentation.core

import com.moyeota.domain.model.AppConfig
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 강제 업데이트 게이트.
 *
 * **막는 쪽보다 안 막는 쪽이 중요하다.** 여기서 한 줄이 틀리면 멀쩡한 사용자가 스토어 화면에
 * 갇히고, 그 상태를 서버에서 되돌릴 수단이 없다(막힌 앱은 설정을 다시 받아도 같은 판정이다).
 * 그래서 「통과해야 하는 경우」를 전부 못 박는다.
 */
class UpdateGateTest {

    private fun config(min: Int? = null, recommended: Int? = null) =
        AppConfig(minVersionCode = min, recommendedVersionCode = recommended)

    // ───────────────────── 반드시 통과해야 하는 경우 ─────────────────────

    @Test
    fun `서버가 기준을 안 주면 통과한다`() {
        // 2026-11-01 현재 서버 응답이 이 상태다 — 두 기준 중 어느 것도 오지 않는다.
        // (서버는 `taxiEnabled` 를 아직 싣지만 도메인에서 필드를 지워 앱이 읽지 않는다.)
        assertEquals(UpdateRequirement.NONE, updateRequirement(config(), 26110101L))
    }

    @Test
    fun `설정 조회에 실패해도 통과한다`() {
        // 서버 장애로 전원이 잠기는 쪽이 낡은 앱이 도는 것보다 훨씬 나쁘다.
        assertEquals(UpdateRequirement.NONE, updateRequirement(AppConfig.Default, 26110101L))
    }

    @Test
    fun `설치 버전을 못 읽으면 통과한다`() {
        assertEquals(
            UpdateRequirement.NONE,
            updateRequirement(config(min = 99999999), UNKNOWN_VERSION_CODE),
        )
    }

    @Test
    fun `서버가 0 이나 음수를 주면 통과한다`() {
        // 실수로 0 을 넣어 전원을 잠그는 사고를 막는다.
        assertEquals(UpdateRequirement.NONE, updateRequirement(config(min = 0), 26110101L))
        assertEquals(UpdateRequirement.NONE, updateRequirement(config(min = -1), 26110101L))
    }

    @Test
    fun `기준과 같은 버전은 통과한다`() {
        // 경계값 — `<` 가 `<=` 로 바뀌면 방금 올린 최신 빌드가 스스로를 막는다.
        assertEquals(UpdateRequirement.NONE, updateRequirement(config(min = 26110101), 26110101L))
    }

    @Test
    fun `기준보다 높은 버전은 통과한다`() {
        assertEquals(UpdateRequirement.NONE, updateRequirement(config(min = 26110101), 26110102L))
    }

    // ───────────────────── 막아야 하는 경우 ─────────────────────

    @Test
    fun `기준보다 낮으면 차단한다`() {
        assertEquals(UpdateRequirement.BLOCKED, updateRequirement(config(min = 26110102), 26110101L))
    }

    @Test
    fun `권장 기준만 밑돌면 막지 않고 알리기만 한다`() {
        assertEquals(
            UpdateRequirement.RECOMMENDED,
            updateRequirement(config(recommended = 26110102), 26110101L),
        )
    }

    @Test
    fun `둘 다 밑돌면 차단이 이긴다`() {
        // 차단이 권장보다 먼저 판정돼야 한다 — 순서가 뒤집히면 못 쓰는 버전이 안내만 받고 계속 돈다.
        assertEquals(
            UpdateRequirement.BLOCKED,
            updateRequirement(config(min = 26110102, recommended = 26110103), 26110101L),
        )
    }
}
