package com.moyeota.presentation.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 16 「걸어서 가까운 방」 카드의 시간 문구.
 *
 * 서버가 주는 `totalSeconds` 는 **탑승지까지 걷는 시간 + 내려서 목적지까지 걷는 시간의 합**이고
 * **차 타는 시간은 들어 있지 않다**. 「7분」이나 「7분 소요」로 쓰면 사용자는 목적지까지 7분으로
 * 읽는다 — 택시 앱에서 그 오독은 치명적이라 문구에 「걸어서」를 못 박는다.
 */
class WalkMinutesLabelTest {

    @Test
    fun `분 단위로 올림한다`() {
        // 61초를 「1분」으로 내리면 실제보다 짧게 약속하는 셈이다 — 걷는 시간은 올림이 안전하다.
        assertEquals("걸어서 2분", walkMinutesLabel(61))
        assertEquals("걸어서 7분", walkMinutesLabel(412))
        assertEquals("걸어서 1분", walkMinutesLabel(60))
    }

    /**
     * **0분을 만들지 않는다.** 「걸어서 0분」은 "걸을 필요 없다"로 읽히는데 그런 경우가 아니고,
     * 반올림해 0 이 되는 짧은 거리일 뿐이다.
     */
    @Test
    fun `아주 짧아도 1분으로 올린다`() {
        assertEquals("걸어서 1분", walkMinutesLabel(1))
        assertEquals("걸어서 1분", walkMinutesLabel(0))
    }

    /** 문구에 「걸어서」가 반드시 남아 있어야 한다 — 이게 빠지면 총 소요시간으로 오독된다. */
    @Test
    fun `도보임을 문구에 밝힌다`() {
        for (seconds in listOf(0, 59, 60, 300, 3600)) {
            assertTrue("「걸어서」가 빠졌다: ${walkMinutesLabel(seconds)}", walkMinutesLabel(seconds).startsWith("걸어서"))
        }
    }
}
