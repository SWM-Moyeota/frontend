package com.moyeota.presentation.feature.matching

import androidx.lifecycle.viewModelScope
import com.moyeota.domain.analytics.AnalyticsEvent
import com.moyeota.domain.model.PartyEvent
import com.moyeota.domain.model.PartyLeaveException
import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RideStatus
import com.moyeota.presentation.analytics.AnalyticsRecorderRule
import com.moyeota.presentation.analytics.FakeRideRepository
import com.moyeota.presentation.analytics.testMember
import com.moyeota.presentation.analytics.testRide
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 21 매칭 대기의 `complete_match` — status 로만, ViewModel 당 한 번, waited_seconds 는 화면 진입 기준.
 *
 * 방을 알게 되는 길(SSE 스냅샷·변경분·상태 전이·상세 재조회)이 여럿이라 전부 `publish` 하나를 지나는지,
 * 그래서 「두 번 세지 않는다」가 지켜지는지를 길마다 본다.
 */
class MatchCompletionAnalyticsTest {

    @get:Rule val rule = AnalyticsRecorderRule()

    private var now = 1_000_000L
    private var current: Ride = testRide(RideStatus.RECRUITING)
    private val repo = FakeRideRepository().apply { detail = { current } }
    private var vm: MatchWaitingViewModel? = null

    private fun enter(): MatchWaitingViewModel =
        MatchWaitingViewModel(repo, partyId = 1, clock = { now }).also { vm = it }

    private fun emit(event: PartyEvent) {
        repo.events.tryEmit(event)
        rule.dispatcher.scheduler.runCurrent()
    }

    private fun snapshot(ride: Ride) {
        current = ride
        emit(PartyEvent.Snapshot(ride))
    }

    @After
    fun tearDown() {
        vm?.viewModelScope?.cancel() // SSE 구독·폴백 루프 정리
    }

    @Test
    fun `모집 중이면 기록하지 않는다`() {
        enter()
        snapshot(testRide(RideStatus.RECRUITING, members = 2))
        assertEquals(emptyList<String>(), rule.names())
    }

    @Test
    fun `정원 충족을 처음 보면 1번 - 같은 상태가 다시 와도, 다음 단계로 가도 여전히 1번`() {
        enter()
        now += 42_700
        snapshot(testRide(RideStatus.MATCHED, capacity = 3, members = 3))
        snapshot(testRide(RideStatus.MATCHED, capacity = 3, members = 3))
        snapshot(testRide(RideStatus.DISPATCHING, capacity = 3, members = 3))

        assertEquals(listOf(AnalyticsEvent.MatchCompleted(capacity = 3, memberCount = 3, waitedSeconds = 42)), rule.recorded)
    }

    @Test
    fun `변경분(member-joined)으로 정원이 차도 1번`() {
        current = testRide(RideStatus.RECRUITING, capacity = 2, members = 1)
        enter()
        now += 5_000
        emit(PartyEvent.MemberJoined(member = testMember(1), status = RideStatus.MATCHED))

        assertEquals(listOf(AnalyticsEvent.MatchCompleted(capacity = 2, memberCount = 2, waitedSeconds = 5)), rule.recorded)
    }

    @Test
    fun `처음부터 찬 방도 첫 조회에서 1번 - 뒤이은 스냅샷은 다시 세지 않는다`() {
        current = testRide(RideStatus.MATCHED, capacity = 2, members = 2)
        enter()
        snapshot(current)

        assertEquals(listOf(AnalyticsEvent.MatchCompleted(capacity = 2, memberCount = 2, waitedSeconds = 0)), rule.recorded)
    }

    @Test
    fun `닫힌 방은 매칭 완료가 아니다`() {
        enter()
        snapshot(testRide(RideStatus.COMPLETED, members = 3))
        snapshot(testRide(RideStatus.CANCELED, members = 3))
        assertEquals(emptyList<String>(), rule.names())
    }

    @Test
    fun `그만 찾기 성공은 나가기 직전 방 기준으로 cancel_match`() {
        current = testRide(RideStatus.RECRUITING, capacity = 3, members = 2)
        enter().leaveParty()
        assertEquals(listOf(AnalyticsEvent.MatchCanceled(capacity = 3, memberCount = 2)), rule.recorded)
    }

    @Test
    fun `나가기가 실패하면 cancel_match 를 보내지 않는다`() {
        repo.leave = { throw PartyLeaveException(PartyLeaveException.PARTY_NOT_RECRUITING, null, 409) }
        enter().leaveParty()
        assertEquals(emptyList<String>(), rule.names())
    }

    @Test
    fun `합승 완료 성공은 finish_party - 실패면 보내지 않는다`() {
        current = testRide(RideStatus.MATCHED, capacity = 3, members = 3)
        val vm = enter()
        repo.finish = { error("403") }
        vm.finishParty()
        assertEquals(listOf("complete_match"), rule.names())

        repo.finish = {}
        vm.finishParty()
        assertEquals(listOf("complete_match", "finish_party"), rule.names())
        assertEquals(AnalyticsEvent.PartyFinished(memberCount = 3), rule.recorded.last())
    }

    @Test
    fun `isMatchFull 은 status 로만 판정한다`() {
        assertTrue(isMatchFull(RideStatus.MATCHED))
        assertTrue(isMatchFull(RideStatus.DISPATCHING))
        assertTrue(isMatchFull(RideStatus.ONGOING))
        assertFalse(isMatchFull(RideStatus.RECRUITING))
        assertFalse(isMatchFull(RideStatus.COMPLETED))
        assertFalse(isMatchFull(RideStatus.CANCELED))
    }
}
