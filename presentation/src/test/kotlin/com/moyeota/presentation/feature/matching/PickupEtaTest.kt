package com.moyeota.presentation.feature.matching

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 25c 「약 N분 뒤 탑승 위치 도착 예정」의 어림 계산.
 *
 * 서버에 기사→탑승지 ETA API 가 없어 앱이 직선거리로 어림한다. 그 어림이 **말이 되는 범위**에
 * 머무는지만 본다 — 정확도가 아니라 「0분」·「9,000분」 같은 값이 화면에 나가지 않는 것이 요점이다.
 */
class PickupEtaTest {

    @Test
    fun `좌표가 없으면 시간을 만들지 않는다`() {
        assertNull(pickupEtaMinutes(null))
    }

    @Test
    fun `음수 거리는 계산하지 않는다`() {
        assertNull(pickupEtaMinutes(-10.0))
    }

    @Test
    fun `NaN·무한대는 계산하지 않는다`() {
        assertNull(pickupEtaMinutes(Double.NaN))
        assertNull(pickupEtaMinutes(Double.POSITIVE_INFINITY))
    }

    @Test
    fun `코앞이어도 0분이라고 하지 않는다`() {
        // 「약 0분 뒤 도착」은 정보가 아니라 오류처럼 읽힌다 — 최소 1분
        assertEquals(1, pickupEtaMinutes(0.0))
        assertEquals(1, pickupEtaMinutes(50.0))
    }

    @Test
    fun `시속 25km 기준으로 분을 어림한다`() {
        // 25km/h = 분당 약 416m
        assertEquals(2, pickupEtaMinutes(833.0))
        assertEquals(5, pickupEtaMinutes(2_083.0))
        assertEquals(12, pickupEtaMinutes(5_000.0))
    }
}
