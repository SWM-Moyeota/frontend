package com.moyeota.data.remote.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * STOMP 재연결 간격([reconnectDelayMs]).
 *
 * 예전에는 **고정 3초**였다. 서버가 5xx 로 누워 있는 동안 한 단말이 분당 20번 WebSocket 업그레이드를
 * 시도하는 하드 루프가 되어, **서버가 아플 때 가장 세게** 때리는 모양이었다. 같은 레포의
 * `AppConfigViewModel.RETRY_DELAYS_MS`(2s·5s·15s)는 이미 증가 간격을 쓴다.
 *
 * 값은 SSE 쪽(`com.moyeota.data.remote.matching` 의 같은 이름 함수)과 **같게** 뒀다 — 두 실시간 통로가
 * 같은 장애에 다르게 반응할 이유가 없다. 화면에는 아무 증상이 없는 종류의 회귀라(끊긴 동안은 호출부
 * 폴링이 메시지를 메운다) 숫자로 못 박는다.
 *
 * 지터를 인자로 받으므로 난수 없이 확정적으로 확인한다.
 */
class ChatSocketBackoffTest {

    @Test
    fun `첫 실패는 예전과 같은 3초다`() {
        // 서버가 잠깐 재시작한 경우 — 흔하고, 몇 초 안에 돌아온다. 이 경우를 느리게 만들지 않는다.
        assertEquals(3_000L, reconnectDelayMs(1, jitter = 0.0))
    }

    @Test
    fun `연속 실패마다 간격이 늘어난다`() {
        assertEquals(6_000L, reconnectDelayMs(2, jitter = 0.0))
        assertEquals(12_000L, reconnectDelayMs(3, jitter = 0.0))
        assertEquals(30_000L, reconnectDelayMs(4, jitter = 0.0))
    }

    @Test
    fun `표를 다 쓰면 마지막 값에 머문다`() {
        assertEquals(30_000L, reconnectDelayMs(5, jitter = 0.0))
        assertEquals(30_000L, reconnectDelayMs(50, jitter = 0.0))
    }

    /** 지터는 **더하기만** 한다 — 하한(3초)·상한(30초)은 둘 다 지키려는 값이다 */
    @Test
    fun `지터는 간격을 늘리기만 하고 20퍼센트를 넘지 않는다`() {
        assertEquals(3_000L, reconnectDelayMs(1, jitter = 0.0))
        assertEquals(3_600L, reconnectDelayMs(1, jitter = 1.0))
        assertEquals(36_000L, reconnectDelayMs(9, jitter = 1.0))
    }

    /** 범위를 벗어난 지터가 들어와도 간격을 음수·무한으로 만들지 않는다 */
    @Test
    fun `지터가 범위를 벗어나도 접어 넣는다`() {
        assertEquals(3_000L, reconnectDelayMs(1, jitter = -5.0))
        assertEquals(3_600L, reconnectDelayMs(1, jitter = 9.0))
    }

    /** 세는 쪽이 틀려도 0ms 하드 루프가 되지 않아야 한다 */
    @Test
    fun `0 이하는 첫 간격과 같다`() {
        assertEquals(3_000L, reconnectDelayMs(0, jitter = 0.0))
        assertEquals(3_000L, reconnectDelayMs(-1, jitter = 0.0))
    }

    @Test
    fun `아무리 쌓여도 3초에서 36초 사이다`() {
        for (failures in 1..1_000) {
            val delay = reconnectDelayMs(failures, jitter = 1.0)
            assertTrue("failures=$failures 에서 $delay", delay in 3_000L..36_000L)
        }
    }
}
