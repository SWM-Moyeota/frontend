package com.moyeota.presentation.feature.explore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 17 탐색의 범위 조회가 **화면이 보일 때만** 도는지.
 *
 * 탐색은 하단탭이라 21 매칭 대기·20 합류 확인·채팅이 위에 올라와도 `NavBackStackEntry` 와 함께
 * ViewModel 이 살아 있고, `viewModelScope` 는 화면이 보이는지 보지 않는다. 그래서 게이트가 없으면
 * 다른 화면에 머무는 내내 4초마다 `GET /matching/rooms?swLat=…` 가 나갔다
 * (QA D-1: 21 체류 135초에 34회). 화면에는 아무 증상이 없어 **logcat/프록시로만 드러난다** —
 * 그래서 판정을 순수 함수로 빼 두고 여기서 고정한다.
 *
 * 함께 지켜야 하는 두 가지도 같이 본다.
 * - **돌아오면 즉시 1회**: 복귀 후 주기를 기다리면 이미 사라진 방의 「합류」를 누르게 된다.
 * - **팬·줌 디바운스 유지**: 중간 범위마다 서버를 때리던 것을 막는 기존 동작을 게이트가 지우지 않는다.
 */
class ExplorePollGateTest {

    /** 기준 패스: 보이는 상태 · 카메라를 움직여 들어온 새 범위 · 이미 목록을 들고 있음 */
    private fun step(
        visible: Boolean = true,
        manual: Boolean = false,
        hasParties: Boolean = true,
        requestSeq: Int = 7,
        shownSeq: Int = 6,
    ) = nearbyPollStep(
        visible = visible,
        manual = manual,
        hasParties = hasParties,
        requestSeq = requestSeq,
        shownSeq = shownSeq,
    )

    @Test
    fun `화면이 보이지 않으면 어떤 요청도 조회하지 않는다`() {
        // 21·20·채팅이 위에 올라온 동안, 그리고 앱이 백그라운드인 동안의 모든 조합
        for (manual in listOf(false, true)) {
            for (hasParties in listOf(false, true)) {
                for (requestSeq in listOf(6, 7)) {
                    assertEquals(
                        "visible=false 인데 조회했다 (manual=$manual, hasParties=$hasParties, seq=$requestSeq)",
                        NearbyPollStep.STOP,
                        step(visible = false, manual = manual, hasParties = hasParties, requestSeq = requestSeq),
                    )
                }
            }
        }
    }

    @Test
    fun `가려진 동안에는 복귀 판정보다 가시성이 앞선다`() {
        // 같은 seq(=복귀 모양)로 들어와도 보이지 않으면 멈춘다 — 신호가 겹쳐 와도 조회로 새지 않게
        assertEquals(NearbyPollStep.STOP, step(visible = false, requestSeq = 7, shownSeq = 7))
    }

    @Test
    fun `첫 진입은 기다리지 않고 바로 조회한다`() {
        // 보여줄 목록이 없으면(첫 조회·에러 화면) 늦출 이득이 없다
        assertEquals(NearbyPollStep.QUERY_NOW, step(hasParties = false, requestSeq = 1, shownSeq = 0))
    }

    @Test
    fun `에러 화면의 다시 시도는 디바운스 없이 조회한다`() {
        assertEquals(NearbyPollStep.QUERY_NOW, step(manual = true))
        assertEquals(NearbyPollStep.QUERY_NOW, step(manual = true, hasParties = false))
    }

    @Test
    fun `카메라를 움직인 새 범위는 디바운스 뒤에 조회한다`() {
        // 팬·줌 연타의 중간 범위마다 서버를 때리지 않는 기존 동작
        assertEquals(NearbyPollStep.DEBOUNCE_THEN_QUERY, step())
    }

    @Test
    fun `탭에서 돌아오면 같은 범위를 즉시 다시 읽는다`() {
        // 가시성만 바뀌어 같은 요청(seq)으로 패스가 다시 들어온 경우 = 복귀.
        // 여기서 기다리면 돌아온 사용자가 주기만큼 옛 목록을 본다.
        assertEquals(NearbyPollStep.QUERY_NOW, step(requestSeq = 7, shownSeq = 7))
    }

    @Test
    fun `에러 화면에서 탭을 옮겼다 돌아오면 그 자리에서 다시 읽는다`() {
        assertEquals(NearbyPollStep.QUERY_NOW, step(hasParties = false, requestSeq = 7, shownSeq = 7))
    }

    @Test
    fun `돌아온 뒤 지도가 다른 범위를 보고하면 그 범위로 조회한다`() {
        // 복귀 조회 뒤 지도가 복원된 카메라로 idle 을 보내 범위가 갱신되는 경로.
        // 새 seq 이므로 복귀가 아니라 평소의 범위 변경으로 다룬다 — 멈추지는 않는다.
        val next = step(requestSeq = 8, shownSeq = 7)
        assertNotEquals(NearbyPollStep.STOP, next)
        assertEquals(NearbyPollStep.DEBOUNCE_THEN_QUERY, next)
    }

    @Test
    fun `보이기 시작하면 멈춤 상태가 아니다`() {
        // 가려진 동안 범위가 바뀌어 있었든(새 seq) 그대로든(같은 seq) 복귀는 반드시 조회로 이어진다
        for (requestSeq in listOf(7, 8)) {
            assertNotEquals(
                "복귀했는데 조회로 이어지지 않았다 (seq=$requestSeq)",
                NearbyPollStep.STOP,
                step(requestSeq = requestSeq, shownSeq = 7),
            )
        }
    }
}
