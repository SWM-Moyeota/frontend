package com.moyeota.presentation.feature.explore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 조회 영역은 **가시 영역보다 넓어야** 한다.
 *
 * 서버는 출발지가 영역 안인 방만 돌려준다. 보이는 만큼만 물으면 카메라가 조금만 움직여도
 * 가장자리의 방이 빠졌다 들어오며 마커가 깜빡인다(실기 QA). 여유를 두면 그 경계가 화면 밖에 있다.
 */
class MapBoundsExpandTest {

    private val seomyeon = MapBounds(swLat = 35.15, swLng = 129.05, neLat = 35.17, neLng = 129.07)

    @Test
    fun `사방으로 넓어지고 원래 영역을 포함한다`() {
        val e = seomyeon.expanded(ratio = 0.3)

        assertTrue(e.swLat < seomyeon.swLat)
        assertTrue(e.swLng < seomyeon.swLng)
        assertTrue(e.neLat > seomyeon.neLat)
        assertTrue(e.neLng > seomyeon.neLng)
    }

    @Test
    fun `여유는 각 변의 길이에 비례한다`() {
        val e = seomyeon.expanded(ratio = 0.5)

        // 위도 폭 0.02 의 50% = 0.01 씩 양쪽
        assertEquals(35.14, e.swLat, 1e-9)
        assertEquals(35.18, e.neLat, 1e-9)
        assertEquals(129.04, e.swLng, 1e-9)
        assertEquals(129.08, e.neLng, 1e-9)
    }

    @Test
    fun `극지방·날짜변경선을 넘지 않는다`() {
        val edge = MapBounds(swLat = -89.0, swLng = -179.0, neLat = 89.0, neLng = 179.0).expanded(ratio = 1.0)

        assertEquals(-90.0, edge.swLat, 1e-9)
        assertEquals(90.0, edge.neLat, 1e-9)
        assertEquals(-180.0, edge.swLng, 1e-9)
        assertEquals(180.0, edge.neLng, 1e-9)
    }
}
