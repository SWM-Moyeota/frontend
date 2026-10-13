package com.moyeota.presentation.feature.matching

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 21 매칭 대기의 SSE 구독·폴백 폴링이 **화면이 보일 때만** 도는지.
 *
 * 두 루프가 `viewModelScope` 에만 묶여 있던 동안에는 화면이 보이지 않아도 계속 돌았다. 앱을 백그라운드로
 * 내리면 OS 가 네트워크를 조여 SSE 가 죽고 → 3초 재연결 → 그 틈을 6초 폴백이 메우고 → 다시 죽고가
 * 되풀이됐다(QA 123 D-3 실측: 백그라운드 **75초에 9건** — `/events` 재연결 5 + 상세 4, 분당 약 7건).
 * 17 탐색의 4초 폴링을 같은 방식으로 고친 뒤(135초 34회 → 0회) 남은 마지막 누수였다.
 *
 * **끊어도 안전한 근거**: 재연결할 때마다 서버가 `connected` 로 방 상세 전체(스냅샷)를 다시 준다.
 * 그래서 가려진 동안 끊고 있다가 복귀해 다시 붙으면 그 순간 상태가 통째로 맞춰진다 — 끊긴 사이의 변화를
 * 따라잡는 별도 조회가 필요 없다. 그 성질 덕에 「보일 때만」이 성립한다.
 *
 * 여기서 고정하는 것은 **판정**이다. 「가려지면 진행 중인 구독과 `delay` 가 실제로 죽는가」는
 * `collectLatest` 의 취소 전파에 달려 있고, presentation 테스트 의존성이 `junit` 뿐이라(코루틴 테스트
 * 라이브러리를 넣으면 빌드 파일 변경이다) 단위 테스트로 확인할 수 없다 — QA 의 logcat 계수가 그 몫이다
 * (`ExplorePollGateTest`·`ChatRoomPollGateTest` 와 같은 한계).
 */
class WaitingVisibilityGateTest {

    // ─── SSE 구독 게이트 ────────────────────────────────────────────────────

    /** D-3 회귀 방지 — 안 보이면 **나머지 조건을 보지 않는다.** */
    @Test
    fun `구독 - 안 보이면 어떤 조합에서도 구독하지 않는다`() {
        for (streamEnded in listOf(false, true)) {
            assertFalse(
                "가려진 상태에서 SSE 를 구독했다 (streamEnded=$streamEnded)",
                shouldObservePartyEvents(visible = false, streamEnded = streamEnded),
            )
        }
    }

    @Test
    fun `구독 - 보이고 스트림이 살아 있으면 구독한다`() {
        assertTrue(shouldObservePartyEvents(visible = true, streamEnded = false))
    }

    /**
     * 가려져서 **취소된** 구독은 복귀하면 다시 붙는다 — 이게 이 게이트의 본체다.
     * 취소는 `CancellationException` 으로 빠져나가 `streamEnded` 를 세우지 않는다.
     */
    @Test
    fun `구독 - 가려졌다 돌아오면 다시 붙는다`() {
        assertTrue(shouldObservePartyEvents(visible = true, streamEnded = false)) // 진입
        assertFalse(shouldObservePartyEvents(visible = false, streamEnded = false)) // 백그라운드
        assertTrue(shouldObservePartyEvents(visible = true, streamEnded = false)) // 복귀 → 즉시 재구독
    }

    /**
     * **종결**로 끝난 스트림(방 닫힘·403 멤버 아님·404 방 없음·스트림 없는 더미 저장소)은 복귀해도
     * 다시 붙지 않는다 — 붙어 봐야 같은 답이고, 게이트 이전에도 다시 붙지 않았다.
     */
    @Test
    fun `구독 - 종결로 끝난 스트림은 복귀해도 다시 붙지 않는다`() {
        assertFalse(shouldObservePartyEvents(visible = true, streamEnded = true))
    }

    // ─── 폴백 폴링 게이트 ───────────────────────────────────────────────────

    /** D-3 회귀 방지 — 안 보이면 **나머지 세 조건의 모든 조합에서** 멈춘다. */
    @Test
    fun `폴백 - 안 보이면 어떤 조합에서도 멈춘다`() {
        for (closed in listOf(false, true)) {
            for (done in listOf(false, true)) {
                for (ended in listOf(false, true)) {
                    assertEquals(
                        "가려진 상태에서 폴백이 돌았다 (closed=$closed, done=$done, ended=$ended)",
                        FallbackStep.STOP,
                        fallbackPollStep(
                            visible = false,
                            partyClosed = closed,
                            fallbackDone = done,
                            streamEnded = ended,
                        ),
                    )
                }
            }
        }
    }

    /**
     * 평상시 복귀는 **한 주기 기다린다.** 복귀의 갱신은 폴백이 아니라 **재구독 스냅샷**이 맡으므로,
     * 여기서 즉시 읽으면 복귀마다 상세 1건이 덧붙는다(진행 중 방 홀더가 「즉시 1회」를 붙이지 않은 것과
     * 같은 판단 — `122` §6).
     */
    @Test
    fun `폴백 - 보이고 스트림이 살아 있으면 한 주기 기다린 뒤 읽는다`() {
        assertEquals(
            FallbackStep.WAIT_THEN_READ,
            fallbackPollStep(
                visible = true,
                partyClosed = false,
                fallbackDone = false,
                streamEnded = false,
            ),
        )
    }

    /** 다시 붙을 스트림이 없으면 폴백이 **유일한** 갱신 수단이다 — 6초를 기다릴 이유가 없다. */
    @Test
    fun `폴백 - 재구독할 스트림이 없으면 즉시 읽는다`() {
        assertEquals(
            FallbackStep.READ_NOW,
            fallbackPollStep(
                visible = true,
                partyClosed = false,
                fallbackDone = false,
                streamEnded = true,
            ),
        )
    }

    /** 기존 종료 조건 ① — 방이 닫히면 루프가 끝난다. 게이트를 넣었다고 복귀로 되살아나면 회귀다. */
    @Test
    fun `폴백 - 방이 닫히면 멈추고 복귀해도 되살아나지 않는다`() {
        assertEquals(
            FallbackStep.STOP,
            fallbackPollStep(
                visible = true,
                partyClosed = true,
                fallbackDone = false,
                streamEnded = false,
            ),
        )
    }

    /** 기존 종료 조건 ② — 대기 단계를 벗어나면(배차 시작·방 종료) 폴백이 끝난다. */
    @Test
    fun `폴백 - 대기 단계를 벗어나 끝난 루프는 복귀해도 되살아나지 않는다`() {
        assertEquals(
            FallbackStep.STOP,
            fallbackPollStep(
                visible = true,
                partyClosed = false,
                fallbackDone = true,
                streamEnded = false,
            ),
        )
    }

    /** 종결 신호가 끝난 루프를 되살리지는 않는다 — 판정 순서(종료 조건이 READ_NOW 보다 앞)를 고정한다. */
    @Test
    fun `폴백 - 종결이어도 끝난 루프를 되살리지 않는다`() {
        for (closed in listOf(false, true)) {
            for (done in listOf(false, true)) {
                if (!closed && !done) continue
                assertEquals(
                    "끝난 루프가 종결 신호로 되살아났다 (closed=$closed, done=$done)",
                    FallbackStep.STOP,
                    fallbackPollStep(
                        visible = true,
                        partyClosed = closed,
                        fallbackDone = done,
                        streamEnded = true,
                    ),
                )
            }
        }
    }

    // ─── 두 루프가 **함께** 묶였는가 ─────────────────────────────────────────

    /**
     * D-3 의 핵심 숫자가 0 이 되는 조건 — 백그라운드에서 `/events` 와 상세가 **둘 다** 0건이어야 한다.
     * 한쪽만 묶으면 남은 쪽이 그 틈을 메운다(이번 결함이 바로 그 모양이었다: SSE 가 죽은 틈을 폴백이 메움).
     */
    @Test
    fun `가려지면 SSE 와 폴백이 함께 멈춘다`() {
        assertFalse(shouldObservePartyEvents(visible = false, streamEnded = false))
        assertEquals(
            FallbackStep.STOP,
            fallbackPollStep(
                visible = false,
                partyClosed = false,
                fallbackDone = false,
                streamEnded = false,
            ),
        )
    }

    /** 복귀하면 둘 다 다시 붙는다 — 재구독은 즉시, 폴백은 한 주기 뒤(스냅샷이 먼저 메운다). */
    @Test
    fun `복귀하면 SSE 와 폴백이 함께 되살아난다`() {
        assertTrue(shouldObservePartyEvents(visible = true, streamEnded = false))
        assertEquals(
            FallbackStep.WAIT_THEN_READ,
            fallbackPollStep(
                visible = true,
                partyClosed = false,
                fallbackDone = false,
                streamEnded = false,
            ),
        )
    }

    /**
     * 게이트가 **평상시 0회**를 깨뜨리지 않았는지 — 두 판정 층을 이어 본다.
     *
     * 보이는 동안 루프는 돌지만([fallbackPollStep] 이 STOP 이 아니다), SSE 가 살아 있고 화면에 방이 떠
     * 있으면 주기마다 **읽지 않는다**([shouldPollFallback] false). 그래서 21 에 머무는 동안의 상세 조회는
     * 여전히 0건이다.
     */
    @Test
    fun `보이는 동안에도 SSE 가 살아 있으면 주기 조회는 0회다`() {
        val step = fallbackPollStep(
            visible = true,
            partyClosed = false,
            fallbackDone = false,
            streamEnded = false,
        )
        assertEquals(FallbackStep.WAIT_THEN_READ, step)
        assertFalse(shouldPollFallback(realtimeAlive = true, hasRide = true))
    }

    /** 끊긴 동안에는 보이는 화면에서만 폴백이 돈다 — 「끊겼으면 읽는다」는 정책 자체는 그대로다. */
    @Test
    fun `끊긴 동안에는 보이는 화면에서만 폴백이 돈다`() {
        assertTrue(shouldPollFallback(realtimeAlive = false, hasRide = true)) // 정책: 읽어야 한다
        // 그래도 안 보이면 루프 자체가 돌지 않는다 — 정책 앞에 게이트가 있다
        assertEquals(
            FallbackStep.STOP,
            fallbackPollStep(
                visible = false,
                partyClosed = false,
                fallbackDone = false,
                streamEnded = false,
            ),
        )
        assertEquals(
            FallbackStep.WAIT_THEN_READ,
            fallbackPollStep(
                visible = true,
                partyClosed = false,
                fallbackDone = false,
                streamEnded = false,
            ),
        )
    }
}
