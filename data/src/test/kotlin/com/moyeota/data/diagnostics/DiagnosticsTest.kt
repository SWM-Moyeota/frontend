package com.moyeota.data.diagnostics

import com.moyeota.domain.diagnostics.Diagnostics
import com.moyeota.domain.diagnostics.FailureSite
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * 보고 기준이 틀리면 대시보드가 **지하철로 가득 차고** 진짜 결함이 소음에 묻힌다.
 * 켜 두나 마나인 상태가 되지 않게 규칙을 여기서 못 박는다.
 *
 * `isWorthReporting` 이 domain 모듈 internal 이라 **공개 경로인 [Diagnostics.report] 로** 검증한다
 * (domain 에는 테스트 의존성이 없다). sink 가 불렸는지로 「걸렀는가」를 본다 — 내부 함수를
 * 직접 부르는 것보다 실제 동작에 가깝다.
 */
class DiagnosticsTest {

    private val seen = mutableListOf<Pair<FailureSite, Throwable>>()

    private fun installRecorder() = Diagnostics.install { site, t -> seen += site to t }

    @After
    fun tearDown() {
        // 전역 sink 라 테스트끼리 샌다. 반드시 비운다.
        Diagnostics.reset()
    }

    // ───────────────────────── 걸러야 하는 것 ─────────────────────────

    @Test
    fun `코루틴 취소는 보고하지 않고 그대로 되던진다`() {
        installRecorder()

        // 삼키면 구조적 동시성이 깨진다 — 호출부가 취소를 알아야 한다.
        assertThrows(CancellationException::class.java) {
            Diagnostics.report(FailureSite.FAVORITE_PLACES, CancellationException("화면 이탈"))
        }
        assertEquals("취소는 한 건도 올라가면 안 된다", emptyList<Any>(), seen)
    }

    @Test
    fun `네트워크 끊김은 보고하지 않는다`() {
        installRecorder()

        // 지하철이지 결함이 아니다.
        Diagnostics.report(FailureSite.RIDE_HISTORY, IOException("unexpected end of stream"))
        Diagnostics.report(FailureSite.RIDE_HISTORY, SocketTimeoutException("timeout"))

        assertEquals(emptyList<Any>(), seen)
    }

    @Test
    fun `4xx 는 서버의 정상 판단이라 보고하지 않는다`() {
        installRecorder()

        listOf(400, 401, 403, 404, 409, 429).forEach { code ->
            Diagnostics.report(FailureSite.FAVORITE_PLACES, IllegalStateException(), httpStatus = code)
        }
        assertEquals("호출부가 이미 처리하는 응답이다", emptyList<Any>(), seen)
    }

    // ───────────────────────── 올려야 하는 것 ─────────────────────────

    @Test
    fun `5xx 는 서버 결함이라 보고한다`() {
        installRecorder()

        listOf(500, 502, 503).forEach { code ->
            Diagnostics.report(FailureSite.FAVORITE_PLACES, IllegalStateException(), httpStatus = code)
        }
        assertEquals("앱이 서버 결함을 아는 유일한 창구다", 3, seen.size)
    }

    @Test
    fun `응답 파싱 실패와 매퍼 결함은 보고한다`() {
        installRecorder()

        // 계약이 깨졌다는 뜻이라 반드시 보여야 한다.
        Diagnostics.report(FailureSite.RIDE_HISTORY, SerializationException("Unexpected JSON token"))
        Diagnostics.report(FailureSite.RIDE_HISTORY, IllegalArgumentException("nickname was null"))
        Diagnostics.report(FailureSite.RIDE_HISTORY, NullPointerException())

        assertEquals(3, seen.size)
    }

    // ───────────────────────── sink 계약 ─────────────────────────

    @Test
    fun `sink 를 꽂기 전의 보고는 조용히 버린다`() {
        // 앱 시작 직후나 단위 테스트에서 터지면 안 된다.
        Diagnostics.report(FailureSite.RIDE_HISTORY, IllegalStateException("too early"))
    }

    @Test
    fun `sink 가 던져도 호출부로 새어 나가지 않는다`() {
        // 이 함수는 전부 catch 안에서 불린다 — 여기서 터지면 삼켜져 있던 실패가 크래시로 승격된다.
        Diagnostics.install { _, _ -> error("Firebase 초기화 실패") }

        Diagnostics.report(FailureSite.TOKEN_REFRESH_REJECTED, IllegalStateException("rejected"))
    }

    @Test
    fun `보고가 지점과 예외를 그대로 넘긴다`() {
        installRecorder()
        val boom = IllegalStateException("boom")

        Diagnostics.report(FailureSite.ACTIVE_PARTY_RESTORE, boom)

        assertEquals(listOf(FailureSite.ACTIVE_PARTY_RESTORE to boom), seen)
    }
}
