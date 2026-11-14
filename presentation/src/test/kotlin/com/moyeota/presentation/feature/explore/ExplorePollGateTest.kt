package com.moyeota.presentation.feature.explore

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 17 탐색의 조회가 **화면이 보일 때만** 도는지 — 갱신 시계([ExploreRefreshCycle])의 문.
 *
 * 탐색은 하단탭이라 21 매칭 대기·20 합류 확인·채팅이 위에 올라와도 `NavBackStackEntry` 와 함께
 * ViewModel 이 살아 있고, `viewModelScope` 는 화면이 보이는지 보지 않는다. 그래서 게이트가 없으면
 * 다른 화면에 머무는 내내 `GET /matching/rooms?swLat=…` 가 나갔다(QA D-1: 4초 주기 시절 21 체류 135초에
 * 34회). 화면에는 아무 증상이 없어 **logcat/프록시로만 드러난다** — 그래서 시계를 직접 돌려 여기서 고정한다.
 *
 * 함께 지켜야 하는 것도 같이 본다.
 * - **돌아오면 즉시 1회**, 카운트다운은 15부터 새로: 복귀 후 주기를 기다리면 이미 사라진 방의 「합류」를 누르게 된다
 * - **팬·줌 디바운스 유지**: 중간 범위마다 서버를 때리던 것을 막는 기존 동작을 게이트가 지우지 않는다
 * - **물어볼 것이 없으면 시계도 돌지 않는다**: 실시간 모드에서 예약 시계가 1초마다 깨어날 이유가 없다
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExplorePollGateTest {

    private fun TestScope.advance(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    /** 읽은 입력을 차례로 적는 시계 하나 — 테스트가 끝나면 `backgroundScope` 와 함께 멈춘다. */
    private fun TestScope.cycle(): Pair<ExploreRefreshCycle<String>, List<String>> {
        val reads = mutableListOf<String>()
        return ExploreRefreshCycle<String>(backgroundScope) { reads += it } to reads
    }

    @Test
    fun `화면이 보이지 않으면 어떤 입력도 읽지 않는다`() = runTest {
        val (cycle, reads) = cycle()
        // 21·20·채팅이 위에 올라온 동안, 그리고 앱이 백그라운드인 동안의 모든 입구
        ExploreLoadTiming.entries.forEachIndexed { index, timing -> cycle.update("범위$index", timing) }
        cycle.refreshNow()
        cycle.retry()
        advance(120_000)

        assertEquals(emptyList<String>(), reads)
    }

    @Test
    fun `보이기 시작하면 마지막 입력을 기다리지 않고 바로 읽는다`() = runTest {
        val (cycle, reads) = cycle()
        cycle.update("기준 범위", ExploreLoadTiming.NOW)
        cycle.update("움직인 범위", ExploreLoadTiming.DEBOUNCED) // 가려진 동안 들어온 범위도
        cycle.setVisible(true)
        runCurrent()

        assertEquals("디바운스 없이 마지막 입력으로", listOf("움직인 범위"), reads)
        assertEquals(ExploreRefreshUi(secondsLeft = 15), cycle.ui.value)
    }

    @Test
    fun `가려지면 멈추고 돌아오면 즉시 읽고 15부터 센다`() = runTest {
        val (cycle, reads) = cycle()
        cycle.update("범위", ExploreLoadTiming.NOW)
        cycle.setVisible(true)
        runCurrent()
        advance(7_000)

        cycle.setVisible(false)
        advance(120_000)
        assertEquals("가려진 동안 0회", 1, reads.size)

        cycle.setVisible(true)
        runCurrent()
        assertEquals(2, reads.size)
        assertEquals("남은 8초를 이어 세지 않는다", 15, cycle.ui.value.secondsLeft)
    }

    @Test
    fun `카메라를 움직인 새 범위는 디바운스 뒤에 읽는다`() = runTest {
        val (cycle, reads) = cycle()
        cycle.update("앞 범위", ExploreLoadTiming.NOW)
        cycle.setVisible(true)
        runCurrent()

        cycle.update("중간 범위", ExploreLoadTiming.DEBOUNCED)
        advance(200)
        cycle.update("새 범위", ExploreLoadTiming.DEBOUNCED) // 팬·줌 연타
        advance(BOUNDS_DEBOUNCE_MS - 1)
        assertEquals(listOf("앞 범위"), reads)
        advance(1)

        assertEquals("중간 범위는 건너뛴다", listOf("앞 범위", "새 범위"), reads)
    }

    @Test
    fun `새 목록은 기다리지 않고 다음 자동 갱신 입력은 지금 읽지 않는다`() = runTest {
        val (cycle, reads) = cycle()
        cycle.update("11월 17일", ExploreLoadTiming.NOW)
        cycle.setVisible(true)
        runCurrent()

        cycle.update("11월 18일", ExploreLoadTiming.NOW)
        runCurrent()
        cycle.update("11월 18일 · 몇 m 옆", ExploreLoadTiming.NEXT_TICK)
        advance(14_999)
        assertEquals(listOf("11월 17일", "11월 18일"), reads)

        advance(1)
        assertEquals("0 에서 최신 입력으로", "11월 18일 · 몇 m 옆", reads.last())
    }

    @Test
    fun `물어볼 것이 없으면 시계도 읽기도 멈추고 생기면 15부터 새로 센다`() = runTest {
        val (cycle, reads) = cycle()
        cycle.setVisible(true)
        cycle.update("곧 출발", ExploreLoadTiming.NOW)
        runCurrent()
        advance(6_000)

        cycle.update(null, ExploreLoadTiming.NOW) // 실시간 모드로
        advance(60_000)
        assertEquals(1, reads.size)
        cycle.refreshNow()
        runCurrent()
        assertEquals("물어볼 것이 없으면 버튼도 아무 일도 하지 않는다", 1, reads.size)

        cycle.update("곧 출발", ExploreLoadTiming.NOW)
        runCurrent()
        assertEquals(2, reads.size)
        assertEquals(15, cycle.ui.value.secondsLeft)
    }

    /** 실시간 목록의 지도 이동 — 보여 주던 목록이 있으면 디바운스, 없으면(첫 조회·에러 화면) 늦출 이득이 없다. */
    @Test
    fun `실시간 지도 이동은 목록이 있으면 디바운스하고 없으면 바로 읽는다`() {
        assertEquals(ExploreLoadTiming.DEBOUNCED, nearbyLoadTiming(listShown = true))
        assertEquals(ExploreLoadTiming.NOW, nearbyLoadTiming(listShown = false))
    }
}
