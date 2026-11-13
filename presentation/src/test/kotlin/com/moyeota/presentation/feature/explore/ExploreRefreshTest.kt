package com.moyeota.presentation.feature.explore

import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 17 탐색의 **15초 주기 갱신 + 카운트다운 버튼** (`ExploreRefresh.kt`).
 *
 * 화면에는 「숫자가 줄어든다」로만 보이는 동작이라 스크린샷으로는 주기·재시작·멈춤을 확인할 수 없다.
 * 시계는 `delay` 하나뿐이므로([countDownToNextRefresh]·지도 디바운스) `kotlinx-coroutines-test` 의 **가상 시간**이
 * 곧 이 화면의 시계다 — `advanceTimeBy` 로 시간을 밀어 보고, 그동안 서버를 몇 번·무엇으로 불렀는지 센다.
 *
 * 못 박는 것:
 * - **세 모드 모두** 15초마다 다시 읽고, 버튼 숫자가 15 → 1 로 줄어든다(「날짜로 찾기」 포함)
 * - 버튼은 **즉시** 다시 읽고 15부터 다시 센다. 조회 중에 누른 것은 버린다
 * - **지도를 움직여도 카운트다운은 그대로다** — 새 범위는 디바운스 뒤 곧바로 읽고, 남은 초는 이어 센다.
 *   0 에서는 그때의 **최신 범위**로 읽는다. 날짜·도착지를 바꿔도 마찬가지다
 * - **가려지면 멈추고**, 돌아오면 즉시 1회 읽고 15부터 새로 센다
 * - 연속 [STALE_NOTICE_FAILURES](=2) 회 실패하면 「낡음」을 켜고 성공 한 번이 끈다 — 지도 이동 조회의 실패도 센다
 * - 같은 목록을 다시 읽을 때 목록을 **로딩으로 덮지 않는다**. 새 목록(날짜·도착지)은 자리를 비우고 헤더에 개수가 없다
 * - 버튼은 FULL 에서도 보인다(자리 판정)
 */
// `Dispatchers.setMain`·`runCurrent`·`advanceTimeBy` 는 아직 opt-in 이 필요하다(kotlinx-coroutines-test 1.10.2).
@OptIn(ExperimentalCoroutinesApi::class)
class ExploreRefreshTest {

    private val mainDispatcher = StandardTestDispatcher()
    private val bounds = MapBounds(swLat = 35.15, swLng = 129.05, neLat = 35.17, neLng = 129.07)
    private val moved = MapBounds(swLat = 35.20, swLng = 129.10, neLat = 35.22, neLng = 129.12)

    private fun byDate(date: String = "2026-11-17", lat: Double = 35.23, lng: Double = 129.08) =
        ReservationQuery.ByDate(departureLat = lat, departureLng = lng, date = date, destination = null)

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * 부른 횟수·물은 값을 세는 가짜 저장소. [gate]·[reservationGate] 가 있으면 조회가 그 문이 열릴 때까지
     * 매달린다(조회 중 상태). 실패는 엔드포인트마다 켜고 끈다.
     */
    private class CountingRepository : FakeRideRepository() {
        var nearbyCalls = 0
        var soonCalls = 0
        var searchCalls = 0
        var failNearbyFromCall = Int.MAX_VALUE
        var failNearby = false
        var failReservations = false
        var gate: CompletableDeferred<Unit>? = null
        var reservationGate: CompletableDeferred<Unit>? = null
        val nearbyAreas = mutableListOf<MapBounds>()
        val soonAreas = mutableListOf<MapBounds>()
        val searchedFrom = mutableListOf<Pair<Double, Double>>()
        val searchedDates = mutableListOf<String>()

        override suspend fun getPartiesWithin(swLat: Double, swLng: Double, neLat: Double, neLng: Double): List<Ride> {
            nearbyCalls++
            nearbyAreas += MapBounds(swLat, swLng, neLat, neLng)
            gate?.await()
            if (failNearby || nearbyCalls >= failNearbyFromCall) throw IOException("offline")
            return listOf(testRide())
        }

        override suspend fun getReservationsSoon(swLat: Double, swLng: Double, neLat: Double, neLng: Double): List<Ride> {
            soonCalls++
            soonAreas += MapBounds(swLat, swLng, neLat, neLng)
            reservationGate?.await()
            if (failReservations) throw IOException("offline")
            return listOf(testRide())
        }

        override suspend fun searchReservations(
            departureLat: Double,
            departureLng: Double,
            date: String,
            destination: ReservationDestination?,
        ): List<ReservationSearchResult> {
            searchCalls++
            searchedFrom += departureLat to departureLng
            searchedDates += date
            reservationGate?.await()
            if (failReservations) throw IOException("offline")
            return listOf(ReservationSearchResult(ride = testRide(), destinationNearby = null))
        }
    }

    /** 가상 시간으로 [millis] 만큼 밀고, 그 시각에 걸린 일까지 돌린다. */
    private fun TestScope.advance(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    /** 모든 상태를 하나도 합치지 않고 받는다 — 잠깐 끼어든 로딩도 놓치지 않는다. */
    private fun TestScope.record(viewModel: ReservationFinderViewModel): List<ReservationFinderViewModel.UiState> {
        val states = mutableListOf<ReservationFinderViewModel.UiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect { states += it } }
        return states
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

    /** 「날짜로 찾기」도 돈다 — 출발 시각은 그대로여도 자리는 차고 빈다(2026-11, 오너 UX 피드백 2차). */
    @Test
    fun `세 모드 모두 주기 갱신과 버튼이 있다`() {
        ExploreMode.entries.forEach { mode ->
            assertTrue("$mode 에 버튼이 없다", exploreAutoRefreshes(mode))
        }
    }

    /** 남은 초는 읽지 않는다 — 1초마다 바뀌는 상태를 스크린리더가 쉬지 않고 읽게 된다. */
    @Test
    fun `버튼의 스크린리더 상태는 주기와 조회 중만 말한다`() {
        assertEquals("목록 새로고침", REFRESH_BUTTON_LABEL)
        assertEquals("15초마다 자동", refreshButtonStateDescription(ExploreRefreshUi(secondsLeft = 7)))
        assertEquals("15초마다 자동", refreshButtonStateDescription(ExploreRefreshUi(secondsLeft = 1)))
        assertEquals("새로고침 중", refreshButtonStateDescription(ExploreRefreshUi(refreshing = true)))
    }

    // ─────────────────────────── 버튼 자리 ───────────────────────────

    /**
     * ★ FULL 에서 **숨기지 않는다** — 지도가 다 가려져도 목록은 15초마다 바뀐다. 목록 위 오른쪽 아래, 끝에서
     * 16dp, 하단 CTA 윗변 16dp 위에 뜬다.
     */
    @Test
    fun `FULL 에서는 목록 위 CTA 16dp 위 끝에서 16dp 에 뜬다`() {
        val placement = refreshButtonPlacement(
            sheetState = ExploreSheetState.FULL,
            containerHeight = 600.dp,
            sheetHeight = 600.dp,
            bottomCtaTop = 60.dp,
        )

        assertEquals(RefreshButtonSpot.OVER_LIST, placement.spot)
        assertEquals(16.dp, placement.end)
        assertEquals(76.dp, placement.bottom)
    }

    /** 접힘·반펼침은 예전 자리 그대로 — 네이버 줌 컨트롤과 같은 세로선, 시트 윗변 16dp 위. */
    @Test
    fun `접힘과 반펼침에서는 지도 위 시트 윗변 16dp 위에 선다`() {
        listOf(ExploreSheetState.PEEK to 96.dp, ExploreSheetState.HALF to 300.dp).forEach { (state, sheet) ->
            val placement = refreshButtonPlacement(state, containerHeight = 600.dp, sheetHeight = sheet, bottomCtaTop = 60.dp)
            assertEquals(state.name, RefreshButtonSpot.ABOVE_SHEET, placement.spot)
            assertEquals(state.name, RefreshButtonEndInset, placement.end)
            assertEquals(state.name, sheet + 16.dp, placement.bottom)
        }
    }

    /** 끝까지 끌어 올리는 동안 지도에 자리가 없어지면 지도 밖으로 밀려나지 않고 목록 위로 내려온다. */
    @Test
    fun `지도에 버튼 자리가 없으면 목록 위로 내려오고 CTA 가 없으면 아래 끝에서 16dp 다`() {
        val dragged = refreshButtonPlacement(ExploreSheetState.HALF, containerHeight = 600.dp, sheetHeight = 560.dp, bottomCtaTop = 60.dp)
        assertEquals(RefreshButtonSpot.OVER_LIST, dragged.spot)
        assertEquals(76.dp, dragged.bottom)

        val noCta = refreshButtonPlacement(ExploreSheetState.FULL, containerHeight = 600.dp, sheetHeight = 600.dp, bottomCtaTop = null)
        assertEquals(16.dp, noCta.bottom)
    }

    /**
     * 목록 위에 뜬 버튼은 목록 아래 끝에서 8~56dp 를 덮는다(CTA 줄 위 여백 8dp + 간격 16dp + 버튼 48dp). 목록 끝
     * 여백이 그보다 커야 끝까지 내린 마지막 줄의 「합류」가 버튼 밑에 깔리지 않는다.
     */
    @Test
    fun `목록 끝 여백은 떠 있는 버튼보다 높다`() {
        val coveredAboveListBottom = RefreshButtonGap + RefreshButtonSize - 8.dp
        assertTrue(RefreshButtonListClearance > coveredAboveListBottom)
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
    fun `가려진 동안에는 읽지 않고 돌아오면 즉시 읽고 15부터 새로 센다`() = runTest(mainDispatcher) {
        val repository = CountingRepository()
        val viewModel = ExploreViewModel(repository)
        viewModel.onInitialBounds(bounds)
        viewModel.onScreenStart()
        runCurrent()
        advance(5_000)
        assertEquals(10, viewModel.refresh.value.secondsLeft)

        viewModel.onScreenStop()
        runCurrent()
        advance(120_000)
        assertEquals("가려진 2분 동안 0회", 1, repository.nearbyCalls)

        viewModel.onScreenStart()
        runCurrent()
        assertEquals("돌아오자마자 1회", 2, repository.nearbyCalls)
        assertEquals("남은 10초를 이어 세지 않고 처음부터", ExploreRefreshUi(secondsLeft = 15), viewModel.refresh.value)

        viewModel.onScreenStop()
        runCurrent()
    }

    /**
     * ★ 오너 요청 B — **지도를 움직여도 카운트다운은 그대로다.** 새 범위는 디바운스(400ms) 뒤 곧바로 읽지만
     * 남은 초는 이어 세고, 0 에서는 **그때의 범위**(움직인 범위)로 다시 읽는다.
     */
    @Test
    fun `지도를 움직여도 카운트다운은 이어 세고 0 에서 최신 범위로 읽는다`() = runTest(mainDispatcher) {
        val repository = CountingRepository()
        val viewModel = ExploreViewModel(repository)
        viewModel.onInitialBounds(bounds)
        viewModel.onScreenStart()
        runCurrent()
        advance(5_000)
        assertEquals(10, viewModel.refresh.value.secondsLeft)

        viewModel.onVisibleBoundsChange(moved)
        runCurrent()
        assertEquals("디바운스 동안은 읽지 않는다", 1, repository.nearbyCalls)
        advance(BOUNDS_DEBOUNCE_MS)
        assertEquals("움직인 범위를 곧바로 읽는다", 2, repository.nearbyCalls)
        assertEquals(moved.expanded(), repository.nearbyAreas.last())
        assertEquals("15로 돌아가지 않는다", ExploreRefreshUi(secondsLeft = 10), viewModel.refresh.value)

        advance(600)
        assertEquals("그 자리에서 이어 센다", 9, viewModel.refresh.value.secondsLeft)

        advance(9_000) // 보이기 시작한 지 15초
        assertEquals("0 에서 자동 갱신", 3, repository.nearbyCalls)
        assertEquals("자동 갱신은 최신 범위로 읽는다", moved.expanded(), repository.nearbyAreas.last())
        assertEquals(15, viewModel.refresh.value.secondsLeft)

        viewModel.onScreenStop()
        runCurrent()
    }

    /** 지도 이동 조회가 나가 있는 동안 버튼은 돌지만, **초는 밑에서 계속 줄어** 끝나면 그 숫자부터 보인다. */
    @Test
    fun `지도 이동 조회 중에도 초는 줄어들고 끝나면 이어 보인다`() = runTest(mainDispatcher) {
        val repository = CountingRepository()
        val viewModel = ExploreViewModel(repository)
        viewModel.onInitialBounds(bounds)
        viewModel.onScreenStart()
        runCurrent()
        advance(5_000)

        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        viewModel.onVisibleBoundsChange(moved)
        advance(BOUNDS_DEBOUNCE_MS)
        assertTrue("지도 이동 조회 중에는 돈다", viewModel.refresh.value.refreshing)

        advance(3_000) // 보이기 시작한 지 8.4초
        assertTrue(viewModel.refresh.value.refreshing)
        repository.gate = null
        gate.complete(Unit)
        runCurrent()

        assertEquals("15가 아니라 줄어든 7초부터", ExploreRefreshUi(secondsLeft = 7), viewModel.refresh.value)

        viewModel.onScreenStop()
        runCurrent()
    }

    /**
     * 0 에 닿았을 때 디바운스가 아직 기다리는 중이면 자동 갱신이 **그 범위로** 읽고 디바운스는 그것으로 갈음한다
     * — 같은 범위를 0.4초 사이에 두 번 읽지 않는다.
     */
    @Test
    fun `디바운스 중에 0 이 오면 움직인 범위로 한 번만 읽는다`() = runTest(mainDispatcher) {
        val repository = CountingRepository()
        val viewModel = ExploreViewModel(repository)
        viewModel.onInitialBounds(bounds)
        viewModel.onScreenStart()
        runCurrent()

        advance(14_800)
        viewModel.onVisibleBoundsChange(moved)
        advance(200) // 0 — 디바운스는 아직 200ms 남았다
        assertEquals(2, repository.nearbyCalls)
        assertEquals(moved.expanded(), repository.nearbyAreas.last())

        advance(1_000)
        assertEquals("디바운스가 같은 범위를 또 읽지 않는다", 2, repository.nearbyCalls)

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

    /**
     * ★ 다시 세운 「낡음」 규칙 — 지도 이동이 더는 패스를 새로 열지 않으므로 **지도 이동 조회의 실패도 같은
     * 카운터에 들어간다**. 지도 이동 실패 1회 + 자동 갱신 실패 1회 = 연속 2회.
     */
    @Test
    fun `지도 이동 조회의 실패와 자동 갱신의 실패를 함께 센다`() = runTest(mainDispatcher) {
        val repository = CountingRepository()
        val viewModel = ExploreViewModel(repository)
        viewModel.onInitialBounds(bounds)
        viewModel.onScreenStart()
        runCurrent()

        repository.failNearby = true
        viewModel.onVisibleBoundsChange(moved)
        advance(BOUNDS_DEBOUNCE_MS) // 지도 이동 조회 실패 1회
        assertFalse("한 번으로는 알리지 않는다", (viewModel.uiState.value as ExploreViewModel.UiState.Success).stale)

        advance(15_000 - BOUNDS_DEBOUNCE_MS) // 자동 갱신 실패 — 연속 2회
        assertTrue((viewModel.uiState.value as ExploreViewModel.UiState.Success).stale)

        repository.failNearby = false
        viewModel.refreshNow()
        runCurrent()
        assertFalse("성공 한 번이 끈다", (viewModel.uiState.value as ExploreViewModel.UiState.Success).stale)

        viewModel.onScreenStop()
        runCurrent()
    }

    /** 첫 조회는 가운데 스피너 대신 버튼이 돈다 — 그동안 화면 상태는 `Loading` 이고 버튼은 「조회 중」이다. */
    @Test
    fun `실시간 첫 조회 동안 버튼이 돌고 끝나면 숫자가 보인다`() = runTest(mainDispatcher) {
        val repository = CountingRepository()
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        val viewModel = ExploreViewModel(repository)
        viewModel.onInitialBounds(bounds)
        viewModel.onScreenStart()
        runCurrent()

        assertEquals(ExploreViewModel.UiState.Loading, viewModel.uiState.value)
        assertTrue(viewModel.refresh.value.refreshing)

        repository.gate = null
        gate.complete(Unit)
        runCurrent()
        assertTrue(viewModel.uiState.value is ExploreViewModel.UiState.Success)
        assertEquals(ExploreRefreshUi(secondsLeft = 15), viewModel.refresh.value)

        viewModel.onScreenStop()
        runCurrent()
    }

    // ─────────────────────────── 예약 (ReservationFinderViewModel) ───────────────────────────

    @Test
    fun `곧 출발은 15초마다 다시 읽고 버튼과 지도 이동도 목록을 로딩으로 덮지 않는다`() = runTest(mainDispatcher) {
        val repository = CountingRepository()
        val viewModel = ReservationFinderViewModel(repository)
        val states = record(viewModel)
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

        advance(3_000)
        viewModel.request(ReservationQuery.Soon(moved))
        advance(BOUNDS_DEBOUNCE_MS)
        assertEquals("지도를 움직이면 디바운스 뒤 읽는다", 4, repository.soonCalls)
        assertEquals(moved.expanded(), repository.soonAreas.last())
        assertEquals("카운트다운은 그대로", 12, viewModel.refresh.value.secondsLeft)

        val firstShown = states.indexOfFirst { it is ReservationFinderViewModel.UiState.Success }
        assertTrue(firstShown >= 0)
        assertTrue(
            "목록을 보여 준 뒤 로딩으로 덮었다: $states",
            states.drop(firstShown).none { it == ReservationFinderViewModel.UiState.Loading },
        )

        viewModel.onScreenStop()
        runCurrent()
        advance(60_000)
        assertEquals("가려지면 멈춘다", 4, repository.soonCalls)
    }

    /** ★ 오너 요청 A — 「날짜로 찾기」도 보이는 동안 15초마다 다시 읽는다(예전에는 한 번의 검색이었다). */
    @Test
    fun `날짜로 찾기도 보이는 동안 15초마다 다시 읽는다`() = runTest(mainDispatcher) {
        val repository = CountingRepository()
        val viewModel = ReservationFinderViewModel(repository)
        record(viewModel)
        viewModel.request(byDate())
        viewModel.onScreenStart()
        runCurrent()
        assertEquals(1, repository.searchCalls)
        assertEquals(ExploreRefreshUi(secondsLeft = 15), viewModel.refresh.value)

        advance(5_000)
        assertEquals(ExploreRefreshUi(secondsLeft = 10), viewModel.refresh.value)
        advance(10_000)
        assertEquals("15초에 다시 읽는다", 2, repository.searchCalls)
        advance(15_000)
        assertEquals(3, repository.searchCalls)

        viewModel.onScreenStop()
        runCurrent()
        advance(60_000)
        assertEquals("가려지면 멈춘다", 3, repository.searchCalls)
    }

    /**
     * ★ 같은 목록을 다시 읽을 때(자동 갱신·버튼·화면 복귀·내 위치가 조금 움직임) **목록을 로딩으로 덮지 않는다** —
     * 오너 스크린샷의 「목록 가운데 큰 스피너」가 그 덮개였다.
     */
    @Test
    fun `날짜로 찾기에서 같은 목록을 다시 읽을 때는 로딩으로 덮지 않는다`() = runTest(mainDispatcher) {
        val repository = CountingRepository()
        val viewModel = ReservationFinderViewModel(repository)
        val states = record(viewModel)
        viewModel.request(byDate())
        viewModel.onScreenStart()
        runCurrent()
        val firstShown = states.indexOfFirst { it is ReservationFinderViewModel.UiState.Success }
        assertTrue(firstShown >= 0)

        advance(15_000) // 자동 갱신
        viewModel.refreshNow() // 버튼
        runCurrent()
        viewModel.request(byDate(lat = 35.2301, lng = 129.0801)) // GPS 가 몇 m 흔들림
        runCurrent()
        viewModel.onScreenStop() // 화면 복귀
        runCurrent()
        viewModel.onScreenStart()
        runCurrent()

        assertEquals(4, repository.searchCalls)
        assertTrue(
            "같은 목록을 다시 읽는 동안 로딩이 끼어들었다: $states",
            states.drop(firstShown).none { it == ReservationFinderViewModel.UiState.Loading },
        )

        viewModel.onScreenStop()
        runCurrent()
    }

    /**
     * ★ 날짜를 바꾸면 **새 목록**이다 — 앞 날짜의 줄을 남기지 않고 비운 채(Loading) 읽는다. 헤더에는 개수가 없고
     * (「11월 18일 (수) 출발 · 출발 시간 순」), 기다림은 버튼의 도는 고리뿐이다. 카운트다운은 그대로 센다.
     */
    @Test
    fun `날짜를 바꾸면 목록을 비우고 헤더에 개수 없이 읽으며 카운트다운은 그대로다`() = runTest(mainDispatcher) {
        val repository = CountingRepository()
        val viewModel = ReservationFinderViewModel(repository)
        record(viewModel)
        viewModel.request(byDate(date = "2026-11-17"))
        viewModel.onScreenStart()
        runCurrent()
        advance(5_000)
        assertTrue(viewModel.uiState.value is ReservationFinderViewModel.UiState.Success)

        val gate = CompletableDeferred<Unit>()
        repository.reservationGate = gate
        viewModel.request(byDate(date = "2026-11-18"))
        runCurrent()

        assertEquals("앞 날짜의 줄을 남기지 않는다", ReservationFinderViewModel.UiState.Loading, viewModel.uiState.value)
        assertEquals("새 날짜는 기다리지 않고 읽는다", "2026-11-18", repository.searchedDates.last())
        assertEquals("카운트다운은 그대로, 버튼만 돈다", ExploreRefreshUi(secondsLeft = 10, refreshing = true), viewModel.refresh.value)
        val count = exploreHeaderCount(0, loading = true, locating = false, failed = false)
        assertNull("읽는 중에는 개수를 적지 않는다", count)
        assertEquals(
            "11월 18일 (수) 출발 · 출발 시간 순",
            exploreListTitle(ExploreMode.BY_DATE, count, dateLabel = "11월 18일 (수)"),
        )

        repository.reservationGate = null
        gate.complete(Unit)
        runCurrent()
        assertTrue(viewModel.uiState.value is ReservationFinderViewModel.UiState.Success)
        assertEquals(ExploreRefreshUi(secondsLeft = 10), viewModel.refresh.value)

        viewModel.onScreenStop()
        runCurrent()
    }

    /**
     * 「날짜로 찾기」의 내 위치는 1초마다 몇 m 씩 흔들린다 — 그때마다 읽지 않고, **다음 자동 갱신이 최신 위치로** 읽는다.
     */
    @Test
    fun `내 위치가 움직이면 바로 읽지 않고 다음 자동 갱신이 새 위치로 읽는다`() = runTest(mainDispatcher) {
        val repository = CountingRepository()
        val viewModel = ReservationFinderViewModel(repository)
        record(viewModel)
        viewModel.request(byDate(lat = 35.23, lng = 129.08))
        viewModel.onScreenStart()
        runCurrent()

        advance(3_000)
        viewModel.request(byDate(lat = 35.2302, lng = 129.0803))
        advance(1_000)
        viewModel.request(byDate(lat = 35.2304, lng = 129.0806))
        advance(1_000)
        assertEquals("위치가 바뀔 때마다 읽지 않는다", 1, repository.searchCalls)

        advance(10_000)
        assertEquals(2, repository.searchCalls)
        assertEquals("자동 갱신은 최신 위치로 읽는다", 35.2304 to 129.0806, repository.searchedFrom.last())

        viewModel.onScreenStop()
        runCurrent()
    }

    /**
     * 「곧 출발」↔「날짜로 찾기」는 같은 목록 자리다 — 카운트다운을 이어 센다. 실시간으로 가면(요청 없음) 멈추고,
     * 예약 모드로 돌아오면 그 목록이 다시 보이는 것이라 즉시 읽고 15부터 센다.
     */
    @Test
    fun `예약 모드끼리 바꾸면 이어 세고 실시간에 다녀오면 15부터 센다`() = runTest(mainDispatcher) {
        val repository = CountingRepository()
        val viewModel = ReservationFinderViewModel(repository)
        record(viewModel)
        viewModel.request(ReservationQuery.Soon(bounds))
        viewModel.onScreenStart()
        runCurrent()
        advance(4_000)

        viewModel.request(byDate())
        runCurrent()
        assertEquals(1, repository.searchCalls)
        assertEquals(11, viewModel.refresh.value.secondsLeft)

        viewModel.request(null) // 실시간 모드
        runCurrent()
        assertEquals(ReservationFinderViewModel.UiState.Idle, viewModel.uiState.value)
        advance(60_000)
        assertEquals("실시간 모드에서는 예약을 읽지 않는다", 1, repository.soonCalls)
        assertEquals(1, repository.searchCalls)

        viewModel.request(ReservationQuery.Soon(bounds))
        runCurrent()
        assertEquals(2, repository.soonCalls)
        assertEquals(ExploreRefreshUi(secondsLeft = 15), viewModel.refresh.value)

        viewModel.onScreenStop()
        runCurrent()
    }

    /** 예약 목록도 같은 「낡음」 규칙이다 — 「곧 출발」의 지도 이동 실패와 자동 갱신 실패를 함께 센다. */
    @Test
    fun `곧 출발의 지도 이동 실패와 자동 갱신 실패가 연속 두 번이면 낡음을 켠다`() = runTest(mainDispatcher) {
        val repository = CountingRepository()
        val viewModel = ReservationFinderViewModel(repository)
        record(viewModel)
        viewModel.request(ReservationQuery.Soon(bounds))
        viewModel.onScreenStart()
        runCurrent()

        repository.failReservations = true
        viewModel.request(ReservationQuery.Soon(moved))
        advance(BOUNDS_DEBOUNCE_MS)
        val once = viewModel.uiState.value as ReservationFinderViewModel.UiState.Success
        assertFalse(once.stale)

        advance(15_000 - BOUNDS_DEBOUNCE_MS)
        val twice = viewModel.uiState.value as ReservationFinderViewModel.UiState.Success
        assertTrue("목록을 남긴 채 낡음을 켠다", twice.stale)
        assertEquals(1, twice.items.size)

        repository.failReservations = false
        advance(15_000)
        assertFalse((viewModel.uiState.value as ReservationFinderViewModel.UiState.Success).stale)

        viewModel.onScreenStop()
        runCurrent()
    }
}
