package com.moyeota.presentation.feature.explore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 17 탐색의 **낡은 목록 알림** 판정.
 *
 * 왜 테스트가 필요한가: 고치기 전의 주기 갱신은 실패를 `runCatching{}.getOrNull() ?: continue` 로
 * **조용히 버렸다.** 목록을 남기는 판단 자체는 맞지만(지도를 통째로 에러 화면으로 바꾸지 않는다)
 * **연속 실패 상한이 없어** 낡은 마커가 영구히 남을 수 있었다 — 사용자는 이미 사라진 방의 「합류」를
 * 누르고 20 에서야 「사라진 방이에요」를 본다. 화면에는 아무 증상이 없어(목록이 멀쩡히 보인다)
 * 실기 QA 로도 드러나지 않는 종류의 결함이라, 판정을 순수 함수로 떼어 여기서 고정한다.
 *
 * [ExplorePollGateTest] 와 같은 모양이다 — 루프는 판정 결과만 따르고, 임계값의 근거는
 * [STALE_NOTICE_FAILURES] KDoc 에 적혀 있다.
 */
class ExploreStaleNoticeTest {

    /** 한 번의 실패로는 알리지 않는다 — 터널·엘리베이터처럼 금방 복구되는 끊김이 흔하다. */
    @Test
    fun `한 번 실패에는 알리지 않는다`() {
        assertFalse(staleAfterPollFailures(0))
        assertFalse(staleAfterPollFailures(1))
    }

    /**
     * 2회(= 15초 주기 × 2 = 30초)부터 알린다. 그 뒤로는 계속 알린 상태를 유지한다 — 복구될 때까지 사실이 그대로다.
     *
     * 4초 주기 시절에는 3회(12초)였다. 주기를 15초로 늘리며 같은 두 조건(「한 번의 끊김은 넘긴다」·
     * 「1~2분 터널보다 짧다」)을 다시 맞춘 값이 2회다([STALE_NOTICE_FAILURES] KDoc 의 표).
     */
    @Test
    fun `연속 두 번 실패하면 알린다`() {
        assertTrue(staleAfterPollFailures(2))
        assertTrue(staleAfterPollFailures(3))
        assertTrue(staleAfterPollFailures(30))
    }

    /** 알리기까지 걸리는 시간이 터널 하나(약 1분)보다 짧다 — 진짜 끊김은 반드시 드러난다. */
    @Test
    fun `알리기까지 1분이 걸리지 않는다`() {
        assertTrue(STALE_NOTICE_FAILURES * NEARBY_POLL_INTERVAL_MS < 60_000L)
        assertTrue("한 번의 주기만으로 알리면 일시 끊김마다 깜빡인다", STALE_NOTICE_FAILURES >= 2)
    }

    /**
     * 임계값이 **상수 하나**로만 정해진다는 것 — 루프 쪽에 숫자가 흘러 들어가면 근거를 적어 둔
     * 자리와 실제 동작이 갈린다.
     */
    @Test
    fun `임계값은 상수가 정한다`() {
        assertEquals(2, STALE_NOTICE_FAILURES)
        assertFalse(staleAfterPollFailures(STALE_NOTICE_FAILURES - 1))
        assertTrue(staleAfterPollFailures(STALE_NOTICE_FAILURES))
    }

    /**
     * **성공 한 번이 알림을 끈다**는 규약을 문서화한다. 카운터를 0 으로 되돌리는 건 호출부(루프)이고,
     * 이 함수는 「지금까지 연속 몇 번」만 본다 — 되돌림을 깜빡하면 성공한 뒤에도 경고가 남는다.
     */
    @Test
    fun `성공하면 카운터가 0 으로 돌아가 알림이 꺼진다`() {
        assertTrue(staleAfterPollFailures(5))
        assertFalse("성공 뒤에도 경고가 남는다", staleAfterPollFailures(0))
    }
}
