package com.moyeota.presentation.core

import com.moyeota.domain.model.AppConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 동승(실배포) / 택시(개발 중) 모드 판정. 서버 값이 없으면 동승, 디버그 강제값이 있으면 그것을 따른다.
 * 모드가 어긋난 단계(동승 모드에 배차·운행)는 status 를 따르되 설정 재조회 신호로만 쓴다.
 */
class AppModeTest {

    @Test
    fun `설정을 못 받았을 때 기본값은 동승 모드다`() {
        assertFalse(AppConfig.Default.taxiEnabled)
        assertEquals(AppMode.COMPANION, AppMode.of(AppConfig.Default.taxiEnabled))
    }

    @Test
    fun `강제값이 없으면 서버 값을 따른다`() {
        assertTrue(effectiveTaxiEnabled(server = true, override = null))
        assertFalse(effectiveTaxiEnabled(server = false, override = null))
    }

    @Test
    fun `강제값이 있으면 서버 값보다 우선한다`() {
        assertTrue(effectiveTaxiEnabled(server = false, override = true))
        assertFalse(effectiveTaxiEnabled(server = true, override = false))
    }

    @Test
    fun `동승 모드에서 배차·운행 단계는 모드 밖이다`() {
        assertTrue(isStageOutsideMode(ActiveStage.DRIVER_SEARCH, taxiEnabled = false))
        assertTrue(isStageOutsideMode(ActiveStage.DRIVER_COMING, taxiEnabled = false))
        assertTrue(isStageOutsideMode(ActiveStage.ONGOING, taxiEnabled = false))
    }

    @Test
    fun `대기·종료 단계는 어느 모드에서도 모드 밖이 아니다`() {
        for (taxi in listOf(true, false)) {
            assertFalse(isStageOutsideMode(ActiveStage.WAITING, taxi))
            assertFalse(isStageOutsideMode(ActiveStage.NONE, taxi))
        }
    }

    @Test
    fun `택시 모드에서는 모든 단계가 모드 안이다`() {
        ActiveStage.entries.forEach { assertFalse(isStageOutsideMode(it, taxiEnabled = true)) }
    }
}
