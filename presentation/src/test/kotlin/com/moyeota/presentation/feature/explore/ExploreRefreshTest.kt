package com.moyeota.presentation.feature.explore

import com.moyeota.domain.model.ReservationDestination
import com.moyeota.domain.model.ReservationSearchResult
import com.moyeota.domain.model.Ride
import com.moyeota.presentation.analytics.FakeRideRepository
import com.moyeota.presentation.analytics.testRide
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 17 탐색의 **15초 주기 갱신 + 카운트다운 버튼** (`ExploreRefresh.kt`).
 *
 * 화면에는 「숫자가 줄어든다」로만 보이는 동작이라 스크린샷으로는 주기·재시작·멈춤을 확인할 수 없다.
 * 시계는 `delay` 하나뿐이므로([countDownToNextRefresh]) `kotlinx-coroutines-test` 의 **가상 시간**이 곧
 * 이 화면의 시계다 — `advanceTimeBy` 로 15초를 밀어 보고, 그동안 서버를 몇 번 불렀는지 센다.
 *
 * 못 박는 것:
 * - 실시간·「곧 출발」은 **15초마다** 다시 읽고, 버튼 숫자가 15 → 1 로 줄어든다
 * - 버튼은 **즉시** 다시 읽고 15부터 다시 센다. 조회 중에 누른 것은 버린다
 * - **가려지면 멈추고**, 돌아오면 즉시 1회 읽는다(기존 가시성 규칙)
 * - 「날짜로 찾기」는 **주기 갱신이 없다**
 * - 연속 [STALE_NOTICE_FAILURES](=2) 회 실패하면 「낡음」을 켜고, 성공 한 번이 끈다
 * - 같은 조회를 다시 읽을 때 목록을 **로딩으로 덮지 않는다**
 */
// `Dispatchers.setMain`·`runCurrent`·`advanceTimeBy` 는 아직 opt-in 이 필요하다(kotlinx-coroutines-test 1.10.2).
@OptIn(ExperimentalCoroutinesApi::class)
class ExploreRefreshTest {

    private val mainDispatcher = StandardTestDispatcher()
    private val bounds = MapBounds(swLat = 35.15, swLng = 129.05, neLat = 35.17, neLng = 129.07)

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** 부른 횟수를 세는 가짜 저장소. [gate] 가 있으면 범위 조회가 그 문이 열릴 때까지 매달린다(조회 중 상태). */
    private class CountingRepository : FakeRideRepository() {
        var nearbyCalls = 0
        var soonCalls = 0
        var searchCalls = 0
        var failNearbyFromCall = Int.MAX_VALUE
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun getPartiesWithin(swLat: Double, swLng: Double, neLat: Double, neLng: Double): List<Ride> {
            nearbyCalls++
            gate?.await()
            if (nearbyCalls >= failNearbyFromCall) throw IOException("offline")
            return listOf(testRide())
        }

        override suspend fun getReservationsSoon(swLat: Double, swLng: Double, neLat: Double, neLng: Double): List<Ride> {
            soonCalls++
            return listOf(testRide())
        }

        override suspend fun searchReservations(
            departureLat: Double,
            departureLng: Double,
            date: String,
            destination: ReservationDestination?,
        ): List<ReservationSearchResult> {
            searchCalls++
            return emptyList()
        }
    }

    /** 가상 시간으로 [millis] 만큼 밀고, 그 시각에 걸린 일까지 돌린다. */
    private fun TestScope.advance(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    // ─────────────────────────── 순수 판정 ───────────────────────────

    @Test
    fun `카운트다운은 15에서 1까지 1초마다 줄고 15초 뒤에 끝난다`() = runTest(mainDispatcher) {
        val ticks = mutableListOf<Int>()
        var finished = false
        launch {
            countDownToNextRefresh(onTick = { ticks += it })
            finished = true
        }
        runCurrent()
        assertEquals(listOf(15), ticks)

        advance(14_000)
        assertEquals((15 downTo 1).toList(), ticks)
        assertFalse("1 을 보여 준 1초 동안은 아직 기다린다", finished)

        advance(1_000)
        assertTrue(finished)
    }

    @Test
    fun `주기는 15초이고 버튼은 15부터 센다`() {
        assertEquals(15_000L, NEARBY_POLL_INTERVAL_MS)
        assertEquals(15, REFRESH_COUNTDOWN_SECONDS)
        assertEquals(REFRESH_COUNTDOWN_SECONDS, ExploreRefreshUi().secondsLeft)
    }

    @Test
    fun `날짜로 찾기만 주기 갱신이 없다`() {
        assertTrue(exploreAutoRefreshes(ExploreMode.REALTIME))
        assertTrue(exploreAutoRefreshes(ExploreMode.SOON))
        assertFalse(exploreAutoRefreshes(ExploreMode.BY_DATE))
    }

    /** 남은 초는 읽지 않는다 — 1초마다 바뀌는 상태를 스크린리더가 쉬지 않고 읽게 된다. */
    @Test
    fun `버튼의 스크린리더 상태는 주기와 조회 중만 말한다`() {
        assertEquals("목록 새로고침", REFRESH_BUTTON_LABEL)
        assertEquals("15초마다 자동", refreshButtonStateDescription(ExploreRefreshUi(secondsLeft = 7)))
        assertEquals("15초마다 자동", refreshButtonStateDescription(ExploreRefreshUi(secondsLeft = 1)))
        assertEquals("새로고침 중", refreshButtonStateDescription(ExploreRefreshUi(refreshing = true)))
    }

    // ─────────────────────────── 실시간 (ExploreViewModel) ───────────────────────────

    @Test
    fun `실시간 목록은 보이는 동안 15초마다 다시 읽고 버튼 숫자가 줄어든다`() = runTest(mainDispatcher) {
        val repository = CountingRepository()
        val viewModel = ExploreViewModel(repository)
        viewModel.onInitialBounds(bounds)
        viewModel.onScreenStart()
        runCurrent()

        assertEquals("보이자마자 1회", 1, repository.nearbyCalls)
        assertEquals(ExploreRefreshUi(secondsLeft = 15), viewModel.refresh.value)

        advance(5_000)
        assertEquals(ExploreRefreshUi(secondsLeft = 10), viewModel.refresh.value)
        assertEquals(1, repository.nearbyCalls)

        advance(10_000)
        assertEquals("15초가 지나면 다시 읽는다", 2, repository.nearbyCalls)
        assertEquals("읽고 나면 15부터 다시 센다", ExploreRefreshUi(secondsLeft = 15), viewModel.refresh.value)

        advance(14_999)
        assertEquals(2, repository.nearbyCalls)
        advance(1)
        assertEquals(3, repository.nearbyCalls)

        viewModel.onScreenStop()
        runCurrent()
    }

    @Test
    fun `새로고침 버튼은 즉시 다시 읽고 15부터 다시 센다`() = runTest(mainDispatcher) {
        val repository = CountingRepository()
        val viewModel = ExploreViewModel(repository)
        viewModel.onInitialBounds(bounds)
        viewModel.onScreenStart()
        runCurrent()
        advance(9_000)
        assertEquals(ExploreRefreshUi(secondsLeft = 6), viewModel.refresh.value)

        viewModel.refreshNow()
        runCurrent()

        assertEquals("기다리지 않고 바로 읽는다(디바운스 없음)", 2, repository.nearbyCalls)
        assertEquals(ExploreRefreshUi(secondsLeft = 15), viewModel.refresh.value)
        // 새 카운트다운이 처음부터 돈다 — 옛 카운트다운의 남은 6초에 다시 읽지 않는다
        advance(6_000)
        assertEquals(2, repository.nearbyCalls)
        advance(9_000)
        assertEquals(3, repository.nearbyCalls)

        viewModel.onScreenStop()
        runCurrent()
    }

    @Test
    fun `조회 중에 누른 새로고침은 버린다`() = runTest(mainDispatcher) {
        val repository = CountingRepository()
        val viewModel = ExploreViewModel(repository)
        viewModel.onInitialBounds(bounds)
        viewModel.onScreenStart()
        runCurrent()

        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        advance(15_000) // 주기 조회가 나가서 문 앞에 매달린다
        assertTrue(viewModel.refresh.value.refreshing)
        assertEquals(2, repository.nearbyCalls)

        viewModel.refreshNow()
        runCurrent()
        assertEquals("겹친 조회를 보내지 않는다", 2, repository.nearbyCalls)

        repository.gate = null
        gate.complete(Unit)
        runCurrent()
        assertEquals(ExploreRefreshUi(secondsLeft = 15), viewModel.refresh.value)

        viewModel.onScreenStop()
        runCurrent()
    }

    @Test
    fun `가려진 동안에는 읽지 않고 돌아오면 즉시 읽는다`() = runTest(mainDispatcher) {
        val repository = CountingRepository()
        val viewModel = ExploreViewModel(repository)
        viewModel.onInitialBounds(bounds)
        viewModel.onScreenStart()
        runCurrent()

        viewModel.onScreenStop()
        runCurrent()
        advance(120_000)
        assertEquals("가려진 2분 동안 0회", 1, repository.nearbyCalls)

        viewModel.onScreenStart()
        runCurrent()
        assertEquals("돌아오자마자 1회", 2, repository.nearbyCalls)
        assertEquals("카운트다운은 처음부터", ExploreRefreshUi(secondsLeft = 15), viewModel.refresh.value)

        viewModel.onScreenStop()
        runCurrent()
    }

    @Test
    fun `주기 조회가 연속 두 번 실패하면 낡음을 켜고 성공하면 끈다`() = runTest(mainDispatcher) {
        val repository = CountingRepository().apply { failNearbyFromCall = 2 }
        val viewModel = ExploreViewModel(repository)
        viewModel.onInitialBounds(bounds)
        viewModel.onScreenStart()
        runCurrent()

        advance(15_000) // 실패 1회 — 아직 알리지 않는다
        assertFalse((viewModel.uiState.value as ExploreViewModel.UiState.Success).stale)

        advance(15_000) // 실패 2회 = 30초 — 알린다. 목록은 그대로다
        val stale = viewModel.uiState.value as ExploreViewModel.UiState.Success
        assertTrue(stale.stale)
        assertEquals(1, stale.parties.size)

        repository.failNearbyFromCall = Int.MAX_VALUE
        advance(15_000)
        assertFalse((viewModel.uiState.value as ExploreViewModel.UiState.Success).stale)

        viewModel.onScreenStop()
        runCurrent()
    }

    // ─────────────────────────── 예약 (ReservationFinderViewModel) ───────────────────────────

    @Test
    fun `곧 출발은 15초마다 다시 읽고 그때마다 로딩으로 덮지 않는다`() = runTest(mainDispatcher) {
        val repository = CountingRepository()
        val viewModel = ReservationFinderViewModel(repository)
        val states = mutableListOf<ReservationFinderViewModel.UiState>()
        backgroundScope.launch { viewModel.uiState.collect { states += it } }
        viewModel.request(ReservationQuery.Soon(bounds))
        viewModel.onScreenStart()
        runCurrent()

        assertEquals(1, repository.soonCalls)
        assertEquals(ExploreRefreshUi(secondsLeft = 15), viewModel.refresh.value)

        advance(15_000)
        assertEquals(2, repository.soonCalls)

        viewModel.refreshNow()
        runCurrent()
        assertEquals("버튼은 즉시 읽는다", 3, repository.soonCalls)
        assertEquals(ExploreRefreshUi(secondsLeft = 15), viewModel.refresh.value)

        // 첫 목록이 뜬 뒤로는 로딩이 한 번도 끼어들지 않는다 — 주기·버튼은 보던 목록을 덮지 않는다
        // (처음의 로딩은 StateFlow 가 곧 이어진 성공과 합쳐 버릴 수 있어 세지 않는다)
        val firstShown = states.indexOfFirst { it is ReservationFinderViewModel.UiState.Success }
        assertTrue(firstShown >= 0)
        assertTrue(
            "목록을 보여 준 뒤 로딩으로 덮었다: $states",
            states.drop(firstShown).none { it == ReservationFinderViewModel.UiState.Loading },
        )

        viewModel.onScreenStop()
        runCurrent()
        advance(60_000)
        assertEquals("가려지면 멈춘다", 3, repository.soonCalls)
    }

    @Test
    fun `날짜로 찾기는 한 번만 읽고 주기 갱신이 없다`() = runTest(mainDispatcher) {
        val repository = CountingRepository()
        val viewModel = ReservationFinderViewModel(repository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.request(
            ReservationQuery.ByDate(departureLat = 35.23, departureLng = 129.08, date = "2026-11-12", destination = null),
        )
        viewModel.onScreenStart()
        runCurrent()
        assertEquals(1, repository.searchCalls)

        advance(120_000)
        assertEquals("2분이 지나도 다시 읽지 않는다", 1, repository.searchCalls)

        // 화면에 돌아오면 한 번 다시 읽는다(예전 동작 그대로)
        viewModel.onScreenStop()
        runCurrent()
        viewModel.onScreenStart()
        runCurrent()
        assertEquals(2, repository.searchCalls)

        viewModel.onScreenStop()
        runCurrent()
    }
}
