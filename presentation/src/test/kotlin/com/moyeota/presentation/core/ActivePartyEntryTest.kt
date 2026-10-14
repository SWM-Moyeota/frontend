package com.moyeota.presentation.core

import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RideStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 21 진입 한 번에 방 상세를 몇 번 읽는가(QA 119 D-2).
 *
 * 진입 경로에는 같은 조회를 거는 곳이 여럿 있다 — `init`, 앱 재개 `LifecycleResumeEffect`,
 * 14·17 의 `LaunchedEffect { refresh() }`, 그리고 합류·생성 직후의 `rememberParty`.
 * 겹침만 막던 예전 플래그는 **순서대로** 들어오는 중복(몇십 ms 차)을 그대로 통과시켰다.
 *
 * 이 테스트가 보는 건 억제 **방향**이다. 「방금 끝난 조회」를 재사용해도 되는 쪽(화면 복귀)과
 * 절대 재사용하면 안 되는 쪽(합류·생성 — 직전 조회는 합류 이전의 사실이다)을 뒤섞으면,
 * 방금 들어간 방을 홀더가 못 보고 배너·채팅 헤더가 빈 채로 남는다.
 */
class ActivePartyEntryTest {

    private var now = 0L
    private fun gate(windowMs: Long = 500L) = ResolveGate(windowMs) { now }

    @Test
    fun `진행 중인 조회가 있으면 두 번째 재조회는 들어오지 못한다`() {
        val gate = gate()

        assertTrue(gate.tryEnter())
        assertFalse("같은 조회가 날아가 있는데 또 걸었다", gate.tryEnter())
    }

    @Test
    fun `방금 끝난 조회가 있으면 창 안에서는 들어오지 못한다`() {
        val gate = gate(windowMs = 500L)

        assertTrue(gate.tryEnter())
        gate.leave()

        now += 25 // QA 가 잰 실제 중복 간격
        assertFalse("25ms 뒤 같은 조회가 또 나갔다", gate.tryEnter())

        now += 400 // 합쳐서 425ms — 아직 창 안이다
        assertFalse(gate.tryEnter())
    }

    @Test
    fun `창이 지나면 다시 조회한다`() {
        val gate = gate(windowMs = 500L)

        assertTrue(gate.tryEnter())
        gate.leave()

        now += 500
        assertTrue("창이 지났는데도 조회를 막았다 — 끝난 방이 배너에 남는다", gate.tryEnter())
    }

    /** 조회 자체가 오래 걸려도 창은 **끝난 시각**부터 센다. 응답을 기다리는 동안은 겹침 규칙이 막는다. */
    @Test
    fun `창은 조회가 끝난 시각부터 센다`() {
        val gate = gate(windowMs = 500L)

        assertTrue(gate.tryEnter())
        now += 2_000 // 느린 응답
        gate.leave()

        assertFalse(gate.tryEnter())
        now += 500
        assertTrue(gate.tryEnter())
    }

    /** 실패한 조회도 창에 들어간다 — 같은 실패를 몇십 ms 뒤에 반복해 봐야 결과가 같다. */
    @Test
    fun `실패한 조회도 창을 만든다`() {
        val gate = gate(windowMs = 500L)

        assertTrue(gate.tryEnter())
        gate.leave() // reload 가 던지든 null 을 주든 finally 로 닫힌다

        assertFalse(gate.tryEnter())
    }

    /**
     * 합류·방 생성 직후는 창을 무시한다. 직전 조회는 **합류 이전**의 사실이라,
     * 재사용하면 방금 들어간 방을 홀더가 못 보고 14·17 배너와 24 채팅 헤더가 빈 채로 남는다.
     */
    @Test
    fun `합류 직후의 조회는 창을 무시한다`() {
        val gate = gate(windowMs = 500L)

        assertTrue(gate.tryEnter())
        gate.leave()
        assertFalse("화면 복귀 쪽은 막혀야 한다", gate.tryEnter())

        gate.enterNow() // rememberParty 경로 — 무조건 읽는다
        gate.leave()
    }

    /** 다만 그 조회도 끝난 시각을 남겨, 전이 도중 재구성으로 뒤따라 들어오는 refresh 는 흡수한다. */
    @Test
    fun `합류 직후의 조회도 뒤따르는 재조회를 흡수한다`() {
        val gate = gate(windowMs = 500L)

        gate.enterNow()
        gate.leave()

        now += 25
        assertFalse("합류 재조회 직후의 탭 진입이 같은 조회를 또 걸었다", gate.tryEnter())
    }

    /**
     * 홀더가 이미 그 방을 들고 있으면(서버가 「이미 이 방 멤버」라고 답해 합류 교착에서 복구된 경로)
     * 상세를 다시 읽지 않는다 — 21 이 곧바로 자기 상세를 읽고, 배너가 보는 값은 이미 그 방이다.
     */
    @Test
    fun `이미 들고 있는 방이면 합류 직후에도 상세를 읽지 않는다`() {
        assertFalse(needsDetailAfterRemember(ride(id = "7"), partyId = 7L))
    }

    @Test
    fun `모르는 방이거나 홀더가 비어 있으면 읽는다`() {
        assertTrue("다른 방인데 예전 방을 그대로 들고 있다", needsDetailAfterRemember(ride(id = "7"), partyId = 8L))
        assertTrue("홀더가 비어 있으면 읽어야 배너가 뜬다", needsDetailAfterRemember(null, partyId = 7L))
        // 서버가 준 적 없는 모양의 id 를 들고 있어도 같다 — 비교가 조용히 참이 되면 안 된다
        assertTrue(needsDetailAfterRemember(ride(id = "ride-7"), partyId = 7L))
    }

    private fun ride(id: String) = Ride(
        id = id,
        origin = "성결대",
        destination = "안양역",
        departureLabel = "지금 출발",
        capacity = 4,
        members = emptyList(),
        farePerPerson = 2_000,
        totalFare = 8_000,
        status = RideStatus.RECRUITING,
    )
}
