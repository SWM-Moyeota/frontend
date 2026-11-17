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
 * 21 매칭 대기의 `complete_match` — **멤버 수와 정원 비교로만**, ViewModel 당 한 번,
 * waited_seconds 는 화면 진입 기준.
 *
 * 방을 알게 되는 길(SSE 스냅샷·변경분·상태 전이·상세 재조회)이 여럿이라 전부 `publish` 하나를 지나는지,
 * 그래서 「두 번 세지 않는다」가 지켜지는지를 길마다 본다.
 *
 * ### 🔴 판정 근거가 status 에서 **목록**으로 바뀌었다 (2026-11-12) ★
 * 이 파일의 첫 줄은 「status 로만」이었다. 서버가 COMPLETED 의 뜻을 「정원 충족」에서 「방장이 약속을
 * 확정했다」로 바꾸면서(`b16551b7`) 그 식이 **양쪽으로 거짓**이 됐다 — 2/3 확정 방을 세고, 정원이
 * 꽉 찬 미확정 방은 안 셌다. 그래서 테스트가 쓰는 `testRide(...)` 의 **members/capacity 가 이제
 * 판정 입력**이고 status 는 집계에 영향을 주지 않는다. 식 자체는 [WaitingStageTest] 가 못 박는다.
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

    /**
     * 「다음 단계로 가도 1번」을 보던 세 번째 스냅샷(`DISPATCHING`)이 빠졌다 — 서버가 그 status 를
     * 보낼 수 없다. 정원이 찬 방은 **status 와 무관하게** 이 화면에 머무므로, 못 박을 것은
     * **같은 방이 몇 번 와도 한 번**이다(스냅샷은 재연결마다 다시 온다).
     *
     * status 를 RECRUITING 으로 두는 것이 요점이다 — **서버가 정원이 찬 방을 ACTIVE 로 둔다.**
     * 여기서 CONFIRMED 를 쓰면 옛 식(`status == MATCHED`)으로도 통과해 회귀를 못 잡는다.
     */
    @Test
    fun `정원 충족을 처음 보면 1번 - 같은 방이 다시 와도 여전히 1번`() {
        enter()
        now += 42_700
        repeat(3) { snapshot(testRide(RideStatus.RECRUITING, capacity = 3, members = 3)) }

        assertEquals(listOf(AnalyticsEvent.MatchCompleted(capacity = 3, memberCount = 3, waitedSeconds = 42)), rule.recorded)
    }

    /**
     * 🔴 ★ **2/3 짜리 확정 방은 매칭 완료가 아니다** — 옛 식이 거짓을 말한 바로 그 경우다.
     *
     * `status == MATCHED` 로 세면 이 방이 `complete_match` 로 집계된다. 서버 COMPLETED 는
     * 「방장이 약속을 확정했다」일 뿐이고 정원과 아무 관계가 없다(`b16551b7`) — 퍼널 3단계가
     * 「같이 타면 택시비가 싸진다를 경험한 순간」을 세는 자리라, 2명으로 간 방을 섞으면 **그 지표가
     * 거짓이 된다.** 화면만 봐서는 알 수 없는 종류의 거짓이라 값으로 고정한다.
     */
    @Test
    fun `정원이 차지 않은 확정 방은 매칭 완료로 세지 않는다`() {
        enter()
        snapshot(testRide(RideStatus.CONFIRMED, capacity = 3, members = 2))

        assertEquals(emptyList<String>(), rule.names())
    }

    /**
     * ★ **정원을 모르는 방(`capacity` 0)은 세지 않는다.** 목록·추천 응답으로 만든 [Ride] 가 그렇고,
     * 0 은 「정원이 0명」이 아니라 「정원을 모른다」다 — `0 >= 0` 이 참이라 가드가 없으면 **방금
     * 만든 1인 방까지** 매칭 완료가 된다.
     */
    @Test
    fun `정원을 모르는 방은 매칭 완료로 세지 않는다`() {
        enter()
        snapshot(testRide(RideStatus.RECRUITING, capacity = 0, members = 1))

        assertEquals(emptyList<String>(), rule.names())
    }

    /**
     * 변경분으로 마지막 자리가 채워지는 경로. 서버가 싣는 status 는 **ACTIVE 그대로**다
     * (정원이 차도 바뀌지 않는다) — 그래서 이 경로는 **목록 길이로만** 집계된다.
     */
    @Test
    fun `변경분(member-joined)으로 정원이 차도 1번`() {
        current = testRide(RideStatus.RECRUITING, capacity = 2, members = 1)
        enter()
        now += 5_000
        emit(PartyEvent.MemberJoined(member = testMember(1), status = RideStatus.RECRUITING))

        assertEquals(listOf(AnalyticsEvent.MatchCompleted(capacity = 2, memberCount = 2, waitedSeconds = 5)), rule.recorded)
    }

    @Test
    fun `처음부터 찬 방도 첫 조회에서 1번 - 뒤이은 스냅샷은 다시 세지 않는다`() {
        current = testRide(RideStatus.RECRUITING, capacity = 2, members = 2)
        enter()
        snapshot(current)

        assertEquals(listOf(AnalyticsEvent.MatchCompleted(capacity = 2, memberCount = 2, waitedSeconds = 0)), rule.recorded)
    }

    @Test
    fun `그만 찾기 성공은 나가기 직전 방 기준으로 cancel_match`() {
        current = testRide(RideStatus.RECRUITING, capacity = 3, members = 2)
        enter().leaveParty()
        assertEquals(listOf(AnalyticsEvent.MatchCanceled(capacity = 3, memberCount = 2)), rule.recorded)
    }

    @Test
    fun `나가기가 실패하면 cancel_match 를 보내지 않는다`() {
        // 코드 이름이 `PARTY_NOT_RECRUITING` 이었다 — 서버가 단계 검사를 묶으면서 통일했다(`b16551b7`).
        repo.leave = { throw PartyLeaveException(PartyLeaveException.PARTY_CLOSED, null, 409) }
        enter().leaveParty()
        assertEquals(emptyList<String>(), rule.names())
    }

    /**
     * 🔴 **`finish_party` 의 기록 지점이 「합승 완료」에서 「출발」로 옮겨 왔다** (2026-11-12) ★
     *
     * 옛 테스트 이름은 「합승 완료 성공은 finish_party - 실패면 보내지 않는다」였다. 서버가
     * `/finish` 를 없애면서(`b16551b7`) 그 액션이 사라졌고, **그대로 두면 Mixpanel 퍼널의 마지막
     * 단계가 조용히 0 이 된다** — 이벤트가 세는 사실(「방이 끝났다」)은 그대로이고 끝내는 버튼만
     * 바뀐 것이므로 [MatchWaitingViewModel.departParty] 로 옮겼다.
     *
     * `member_count` 의 뜻도 지킨다: **방을 끝낼 때 방에 있던 인원**이다(도착한 인원이 아니다 —
     * 그건 다른 숫자라 같은 프로퍼티에 담으면 대시보드가 조용히 다른 값을 보게 된다).
     *
     * ⚠️ 이 이벤트 이름(`finish_party`)은 **대시보드와 iOS 가 보는 문자열**이다 — 안드로이드만
     * 기록 지점을 옮겼으니 iOS 도 같은 자리로 옮겨야 두 플랫폼의 숫자가 합쳐진다.
     */
    @Test
    fun `출발 성공이 finish_party 를 보낸다 - 실패면 보내지 않는다`() {
        current = testRide(RideStatus.RECRUITING, capacity = 3, members = 3)
        val vm = enter()
        repo.depart = { error("403") }
        vm.departParty()
        assertEquals(listOf("complete_match"), rule.names())

        repo.depart = {}
        vm.departParty()
        assertEquals(listOf("complete_match", "finish_party"), rule.names())
        assertEquals(AnalyticsEvent.PartyFinished(memberCount = 3), rule.recorded.last())
    }
}
