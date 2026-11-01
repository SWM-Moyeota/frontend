package com.moyeota.presentation.feature.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 26 운행 중의 완료 폴링이 **화면이 보일 때만** 도는지([shouldPollRideFinish]).
 *
 * 2026-10-28 결함: 이 루프를 Route 의 `LaunchedEffect(viewModel)` 가 들고 있었다. `LaunchedEffect` 는
 * **컴포지션 이탈**에만 취소되고 **앱 백그라운드**에는 반응하지 않는데, `NavHost` 컴포지션은 앱이 뒤로
 * 가도 살아 있다(`ActiveParty.kt` KDoc). 그래서 홈 버튼을 눌러도 8초마다 쿼리 3개짜리 방 상세가
 * 계속 나갔다 — 30분 운행이면 승객 1명당 약 225회다. 같은 레포의 다른 폴링은 전부
 * `LifecycleStartEffect` 경계를 쓴다(`ExplorePollGateTest`·`WaitingVisibilityGateTest` 와 같은 자리).
 *
 * **화면에는 아무 증상이 없다** — logcat·프록시로만 드러나므로 판정을 순수 함수로 빼 여기서 고정한다.
 * 함께 지켜야 하는 것도 본다: **완료는 종결**이라 가시성이 다시 켜져도 되살아나지 않는다.
 */
class RidePollGateTest {

    @Test
    fun `화면이 보이지 않으면 폴링하지 않는다`() {
        // 27 신고가 위에 올라온 동안, 그리고 앱이 백그라운드인 동안
        assertFalse(shouldPollRideFinish(visible = false, finished = false))
    }

    @Test
    fun `보이는 동안에는 폴링한다`() {
        assertTrue(shouldPollRideFinish(visible = true, finished = false))
    }

    @Test
    fun `완료를 감지한 뒤에는 돌아와도 되살아나지 않는다`() {
        // 가시성은 「멈췄다 켜는」 스위치지만 완료는 사실이다 — 되살아나면 33 에서 뒤로 왔을 때
        // 다시 33 으로 튕겨내는 루프가 된다(MainNavGraph 의 popUpTo 주석과 같은 이유).
        assertFalse(shouldPollRideFinish(visible = true, finished = true))
        assertFalse(shouldPollRideFinish(visible = false, finished = true))
    }

    @Test
    fun `가려진 동안에는 완료 여부보다 가시성이 앞선다`() {
        // 신호가 겹쳐 와도 보이지 않으면 요청으로 새지 않는다
        for (finished in listOf(false, true)) {
            assertFalse(
                "visible=false 인데 폴링했다 (finished=$finished)",
                shouldPollRideFinish(visible = false, finished = finished),
            )
        }
    }
}
