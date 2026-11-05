package com.moyeota.presentation.feature.home

import com.moyeota.domain.model.PartyOpenException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 16 도착지 확인의 **예약 방 규칙** — 정원 범위·7일 창·한국 달력 날짜·실패 문구.
 *
 * 이 테스트가 잡는 것은 전부 **화면에서는 안 보이는 결함**이다: 7일 뒤 늦은 시각을 고를 수 있는
 * 화면과 올바른 화면은 눈으로 같고, 차이는 서버 400 으로만 드러난다. KST 자정 직후의 날짜 계산도
 * 같다 — 기기 시간대로 셈하면 검색이 하루 밀리는데 그 자리에는 「그 날 예약이 없어요」가 뜬다.
 */
class ReservationRulesTest {

    /** KST 2026-11-09 (일) 13:00 — 기준 시각. UTC 로는 11-09 04:00 이라 날짜가 같다. */
    private val now = Instant.parse("2026-11-09T04:00:00Z")

    // ─────────────────────────── 정원 ───────────────────────────

    /** 서버 계약: 실시간 `Capacity` 는 1~3, **예약은 2~3**. 벗어나면 400 `INVALID_CAPACITY`. */
    @Test
    fun `정원 선택지는 모드마다 다르다 - 예약은 1 을 지운다`() {
        assertEquals(listOf(1, 2, 3), capacityChoices(DepartureMode.NOW))
        assertEquals(listOf(2, 3), capacityChoices(DepartureMode.SCHEDULED))
    }

    /**
     * **1 을 고른 채 「시간 정하기」로 바꾸는 길이 있다** — 그 상태는 보낼 수 없으므로 접는다.
     * 고른 값 자체는 화면이 그대로 들고 있어서 모드를 되돌리면 1 이 살아 있다(여기서는 변환만 본다).
     */
    @Test
    fun `예약으로 바꾸면 1명은 2명으로 접힌다`() {
        assertEquals(2, capacityFor(DepartureMode.SCHEDULED, 1))
        assertEquals(2, capacityFor(DepartureMode.SCHEDULED, 2))
        assertEquals(3, capacityFor(DepartureMode.SCHEDULED, 3))
        // 실시간에서는 1 이 유효한 선택이다 — 접지 않는다(혼자 타기를 상품으로 인정한 결정)
        assertEquals(1, capacityFor(DepartureMode.NOW, 1))
    }

    /** 화면에 없는 값이 들어와도 **가장 가까운 유효값**이다 — 서버가 막아 주는 것과 앱이 보내지 않는 것은 다른 보호막이다. */
    @Test
    fun `범위 밖 정원은 가장 가까운 유효값이 된다`() {
        assertEquals(3, capacityFor(DepartureMode.NOW, 9))
        assertEquals(3, capacityFor(DepartureMode.SCHEDULED, 9))
        assertEquals(1, capacityFor(DepartureMode.NOW, 0))
        assertEquals(2, capacityFor(DepartureMode.SCHEDULED, 0))
    }

    // ─────────────────────────── 날짜 (한국 달력) ───────────────────────────

    @Test
    fun `날짜는 오늘부터 7일 뒤까지 여덟 개다`() {
        val dates = reservationDates(now)

        assertEquals(8, dates.size)
        assertEquals(LocalDate.of(2026, 11, 9), dates.first())
        assertEquals(LocalDate.of(2026, 11, 16), dates.last())
        // 하루씩 **빠짐없이** 이어진다 — 중간이 비면 사용자가 고를 수 없는 날이 생긴다
        dates.zipWithNext { a, b -> assertEquals(b, a.plusDays(1)) }
    }

    /**
     * ★ **이 테스트가 「하루 밀림」을 막는다.**
     *
     * KST 자정 직후(UTC 로는 전날 15:30)에 서버의 「오늘」은 11-09 다. 기기 시간대나 UTC 로
     * 셈하면 11-08 이 「오늘」이 되고, 그 날짜는 서버의 「오늘~7일 뒤」를 벗어나 400
     * `INVALID_SEARCH_DATE` 가 된다 — 화면에는 「그 날 예약이 없어요」로만 보인다.
     */
    @Test
    fun `한국 자정을 넘기면 한국 날짜가 오늘이다 - UTC 날짜가 아니다`() {
        val justAfterKoreanMidnight = Instant.parse("2026-11-08T15:30:00Z") // KST 11-09 00:30

        assertEquals(LocalDate.of(2026, 11, 9), reservationDates(justAfterKoreanMidnight).first())
        // 같은 순간을 UTC 로 보면 11-08 이다 — 그래서 기본 시간대를 고정해 둔 것이다
        assertEquals(
            LocalDate.of(2026, 11, 8),
            reservationDates(justAfterKoreanMidnight, ZoneId.of("UTC")).first(),
        )
    }

    /** `date` 파라미터는 **달력 날짜**다 — 순간 문자열을 자른 값이 아니다. */
    @Test
    fun `검색 날짜는 yyyy-MM-dd 로 나간다`() {
        assertEquals("2026-11-09", reservationDateApiValue(LocalDate.of(2026, 11, 9)))
        // 한 자리 월·일도 두 자리로 — 서버 파서가 `yyyy-MM-dd` 를 기대한다
        assertEquals("2026-01-02", reservationDateApiValue(LocalDate.of(2026, 1, 2)))
    }

    @Test
    fun `검색 가능한 날짜는 오늘부터 7일 뒤까지다`() {
        val today = LocalDate.of(2026, 11, 9)

        assertTrue(isReservationSearchDate(today, now))
        assertTrue(isReservationSearchDate(today.plusDays(7), now))
        assertFalse("7일을 넘겼다", isReservationSearchDate(today.plusDays(8), now))
        assertFalse("어제는 고를 수 없다", isReservationSearchDate(today.minusDays(1), now))
    }

    // ─────────────────────────── 시각 슬롯 ───────────────────────────

    @Test
    fun `오늘 슬롯은 지금보다 뒤이고 10분 여유를 둔다`() {
        val today = LocalDate.of(2026, 11, 9) // 기준 시각은 KST 13:00
        val slots = reservationTimeSlots(today, now)

        // 13:00 + 10분 = 13:10 **초과**여야 하므로 13:30 이 가장 이르다(13:00 도 13:10 도 아니다)
        assertEquals(LocalTime.of(13, 30), slots.first())
        assertEquals(LocalTime.of(23, 30), slots.last())
        assertFalse("이미 지난 시각", slots.contains(LocalTime.of(12, 30)))
        // 30분 간격이라 자정 뒤 전부를 세면 하루 48개에서 지난 만큼이 빠진다
        assertEquals(21, slots.size)
    }

    @Test
    fun `내일은 자정부터 전부 고를 수 있다`() {
        val slots = reservationTimeSlots(LocalDate.of(2026, 11, 10), now)

        assertEquals(48, slots.size)
        assertEquals(LocalTime.of(0, 0), slots.first())
        assertEquals(LocalTime.of(23, 30), slots.last())
    }

    /**
     * ★ **보이지 않는 경계** — 「7일 뒤」 칩은 있는데 그 날 늦은 시각은 창을 넘는다.
     *
     * 기준 시각이 KST 13:00 이므로 7일 뒤에 허용되는 것은 **그 날 13:00 까지**다. 이 필터가 없으면
     * 사용자가 7일 뒤 23:30 을 골라 400 `INVALID_DEPARTURE_TIME` 을 만든다.
     */
    @Test
    fun `7일 뒤는 지금 시각까지만 고를 수 있다`() {
        val slots = reservationTimeSlots(LocalDate.of(2026, 11, 16), now)

        assertEquals(LocalTime.of(0, 0), slots.first())
        // 정확히 「지금 + 7일」인 13:00 은 **포함**이다(서버가 그 경계를 받는다)
        assertEquals(LocalTime.of(13, 0), slots.last())
        assertFalse(slots.contains(LocalTime.of(13, 30)))
    }

    /** 자정 직후에는 7일 뒤의 선택지가 **자정 한 칸**으로 줄어든다(비지는 않는다). */
    @Test
    fun `자정 직후의 7일 뒤는 자정 한 칸만 남는다`() {
        val justAfterMidnight = Instant.parse("2026-11-08T15:05:00Z") // KST 11-09 00:05
        val lastDay = reservationDates(justAfterMidnight).last() // 11-16

        assertEquals(listOf(LocalTime.of(0, 0)), reservationTimeSlots(lastDay, justAfterMidnight))
    }

    /**
     * 늦은 밤에는 **「오늘」에 고를 시각이 없다** — 화면이 안내를 띄우는 상태다
     * ([RESERVATION_NO_SLOT_NOTICE]). 오늘의 마지막 슬롯이 23:30 이고 10분 여유가 필요하다.
     */
    @Test
    fun `밤 23시 55분에는 오늘 고를 시간이 없다`() {
        val lateNight = Instant.parse("2026-11-09T14:55:00Z") // KST 23:55
        val today = reservationDates(lateNight).first()

        assertTrue(reservationTimeSlots(today, lateNight).isEmpty())
        // 내일은 고를 수 있다 — 「고를 수 없다」가 날짜 하나에만 걸린다.
        // 다만 **자정 칸은 빠진다**(지금부터 5분 뒤라 10분 여유에 걸린다) — 48개가 아니라 47개다.
        val tomorrow = reservationTimeSlots(today.plusDays(1), lateNight)
        assertEquals(47, tomorrow.size)
        assertEquals(LocalTime.of(0, 30), tomorrow.first())
    }

    // ─────────────────────────── 보내는 값 ───────────────────────────

    /**
     * 서버는 `Instant` 를 받는다 — **오프셋 없는 로컬 시각은 400** 이고 그 400 은 어떤 사유에도
     * 걸리지 않아 일반 문구로 떨어진다(가장 찾기 어려운 실패다).
     */
    @Test
    fun `출발 시각은 오프셋이 붙은 순간 문자열로 나간다`() {
        val iso = reservationDepartureIso(LocalDate.of(2026, 11, 9), LocalTime.of(18, 30))

        // KST 18:30 = UTC 09:30 — **문자열 모양이 아니라 가리키는 순간**을 고정한다
        assertEquals(Instant.parse("2026-11-09T09:30:00Z"), Instant.parse(iso))
        assertTrue("UTC 표기(Z)로 끝나야 한다: $iso", iso.endsWith("Z"))
        assertFalse("날짜와 시각 사이에 공백을 넣지 않는다: $iso", iso.contains(" "))
    }

    // ─────────────────────────── 표기 ───────────────────────────

    @Test
    fun `날짜 칩은 오늘 내일을 이름으로 부르고 그 뒤는 요일을 붙인다`() {
        val today = LocalDate.of(2026, 11, 9) // 월요일

        assertEquals("오늘", reservationDateLabel(today, today))
        assertEquals("내일", reservationDateLabel(today.plusDays(1), today))
        assertEquals("11월 15일 (일)", reservationDateLabel(today.plusDays(6), today))
    }

    @Test
    fun `시각 표기는 자정과 정오를 12 로 적는다`() {
        assertEquals("오전 12:00", reservationTimeLabel(LocalTime.of(0, 0)))
        assertEquals("오전 12:30", reservationTimeLabel(LocalTime.of(0, 30)))
        assertEquals("오후 12:00", reservationTimeLabel(LocalTime.of(12, 0)))
        assertEquals("오후 6:30", reservationTimeLabel(LocalTime.of(18, 30)))
    }

    /** CTA 앞에서 두 선택이 합쳐져 무엇이 되는지 되읽어 주는 줄 — 예약은 되돌릴 수 없다. */
    @Test
    fun `고른 출발 시각 요약은 날짜와 시각을 함께 적는다`() {
        val today = LocalDate.of(2026, 11, 9)

        assertEquals("오늘 오후 6:30", reservationDepartureSummary(today, LocalTime.of(18, 30), today))
        assertEquals(
            "11월 15일 (일) 오전 9:00",
            reservationDepartureSummary(today.plusDays(6), LocalTime.of(9, 0), today),
        )
    }

    // ─────────────────────────── 실패 문구 ───────────────────────────

    private fun failure(code: String?, httpStatus: Int? = 400) =
        PartyOpenException(code = code, serverMessage = null, httpStatus = httpStatus)

    /** 「다시 시도」류를 403·409 에 붙이지 않는다 — `FinishFailureMessageTest` 가 세운 규칙이다. */
    private fun assertNoRetryPromise(message: String) {
        listOf("다시 시도", "잠시 후", "잠시만", "재시도").forEach { forbidden ->
            assertFalse("재시도를 약속한다: $message", message.contains(forbidden))
        }
    }

    /**
     * ★ 403 `NO_SHOW_RESTRICTED` — **재시도할 수 없는 실패**다. 끝나는 시각이 서버에 있고
     * 그때까지는 몇 번 눌러도 같은 403 이다.
     *
     * 그 시각(`restrictedUntil`)을 **여기서 말하지 않는다**: 이 화면은 그 값을 읽지 않으므로
     * 숫자를 적으면 지어내는 것이고, 「마이페이지에서 보세요」는 이 화면이 확인할 수 없는 약속이다.
     */
    @Test
    fun `노쇼 제재는 왜 막혔는지만 말하고 재시도를 약속하지 않는다`() {
        val message = reservationOpenFailureMessage(failure(PartyOpenException.NO_SHOW_RESTRICTED, httpStatus = 403))

        assertEquals("노쇼 제재 중에는 방을 만들 수 없어요. 제재가 끝나면 다시 만들 수 있어요", message)
        assertNoRetryPromise(message)
        assertTrue("왜 막혔는지 말해야 한다", message.contains("노쇼"))
        // 모르는 숫자·다른 화면을 가리키지 않는다
        assertFalse(message, message.contains("시까지"))
        assertFalse(message, message.contains("마이페이지"))
    }

    /**
     * 409 `PARTY_TIME_CONFLICT` — 앱이 **예측하지 않는** 실패다(내 다른 예약 목록을 이 화면이
     * 모른다). 그래서 문구가 유일한 안내이고, 다음 행동은 **다른 시간을 고르는 것**이다.
     */
    @Test
    fun `출발 2시간 안에 다른 예약이 있으면 다른 시간을 고르라고 말한다`() {
        val message = reservationOpenFailureMessage(failure(PartyOpenException.PARTY_TIME_CONFLICT, httpStatus = 409))

        assertEquals("출발 2시간 안에 다른 예약이 있어요. 다른 시간으로 골라 주세요", message)
        assertNoRetryPromise(message)
        // 「2시간」은 서버 규칙이라 숫자를 적는다 — 없으면 얼마나 옮겨야 하는지 알 수 없다
        assertTrue(message, message.contains("2시간"))
    }

    @Test
    fun `출발 시각이 7일을 벗어나면 창을 숫자로 말한다`() {
        val message = reservationOpenFailureMessage(failure(PartyOpenException.INVALID_DEPARTURE_TIME))

        assertEquals("출발 시각은 지금부터 7일 안이어야 해요. 다른 시간으로 골라 주세요", message)
        assertTrue(message, message.contains("7일"))
    }

    /**
     * ★ 정원 문구가 실시간과 **달라야** 한다 — 예약에서 1 은 고를 수 없는 값이다.
     *
     * 다만 **정원 1 은 이 코드로 오지 않는다**: 서버가 `@Min(2)` 빈 검증으로 먼저 막아 400
     * `INVALID_REQUEST` 가 되고, 그건 [PartyOpenException.isInvalidCapacity] 에 걸리지 않아
     * 일반 문구로 떨어진다(아래 테스트). 그래서 **진짜 방어는 칩에서 1 을 지우는 것**이고
     * ([capacityChoices]·[capacityFor]) 이 문구는 상한을 어긴 요청 같은 나머지를 받는다.
     */
    @Test
    fun `예약 정원 실패는 2에서 3이라고 말한다`() {
        val message = reservationOpenFailureMessage(failure(PartyOpenException.INVALID_CAPACITY))

        assertEquals("예약 방 인원은 2~3명이어야 해요", message)
        assertFalse("실시간 범위를 말하면 고를 수 없는 값을 고르라고 하게 된다", message.contains("1~3"))
        // 실시간 쪽은 그대로다 — 두 문구가 서로를 덮지 않는다
        assertEquals(
            "인원은 1~3명까지만 정할 수 있어요",
            openFailureMessage(failure(PartyOpenException.INVALID_CAPACITY)),
        )
    }

    /** 겹치는 사유는 **같은 문장**을 쓴다 — 같은 사실을 두 가지로 말하지 않는다. */
    @Test
    fun `실시간과 같은 사유는 같은 문구를 쓴다`() {
        listOf(
            PartyOpenException.SAME_DEPARTURE_DESTINATION,
            PartyOpenException.OUT_OF_SERVICE_AREA,
        ).forEach { code ->
            assertEquals(
                openFailureMessage(failure(code)),
                reservationOpenFailureMessage(failure(code)),
            )
        }
        val conflict = failure("ALREADY_JOINED_OTHER_PARTY", httpStatus = 409)
        assertEquals(ALREADY_IN_PARTY_MESSAGE, reservationOpenFailureMessage(conflict))
    }

    /**
     * 서버가 코드를 늘려도 앱이 지어내지 않는다. **경로 실패 가지를 두지 않은 것**도 여기서 드러난다 —
     * 서버는 예약 방에 경로를 계산하지 않으므로 그 코드가 와도 「경로를 찾지 못했어요」로 말하지 않는다.
     */
    /**
     * **정원 1 로 보낸 예약은 `INVALID_REQUEST` 다**(서버 `@Min(2)` 빈 검증이 도메인보다 먼저 돈다).
     * 그 코드는 어느 사유에도 걸리지 않아 일반 문구로 떨어진다 — 그래서 UI 가 1 을 **고를 수 없게**
     * 만드는 것이 유일한 실질적 방어다([capacityChoices]). 그 사실을 여기 값으로 고정해 둔다.
     */
    @Test
    fun `정원 1 로 보낸 예약은 일반 문구로 떨어진다 - 칩에서 막는 것이 유일한 방어다`() {
        assertEquals(RESERVATION_OPEN_FAILED_MESSAGE, reservationOpenFailureMessage(failure("INVALID_REQUEST")))
        // 고를 수 있는 값에 1 이 없다 — 이 보장이 위 문구보다 먼저다
        assertFalse(capacityChoices(DepartureMode.SCHEDULED).contains(1))
    }

    @Test
    fun `모르는 코드는 일반 문구로 떨어진다`() {
        assertEquals(RESERVATION_OPEN_FAILED_MESSAGE, reservationOpenFailureMessage(failure("SOMETHING_NEW")))
        assertEquals(RESERVATION_OPEN_FAILED_MESSAGE, reservationOpenFailureMessage(failure(code = null, httpStatus = 500)))
        assertEquals(
            RESERVATION_OPEN_FAILED_MESSAGE,
            reservationOpenFailureMessage(failure(PartyOpenException.ROUTE_SEARCH_FAILED, httpStatus = 502)),
        )
        // 예약을 만들려던 사람에게 「동승 방」이라고 말하지 않는다
        assertTrue(RESERVATION_OPEN_FAILED_MESSAGE, RESERVATION_OPEN_FAILED_MESSAGE.contains("예약 방"))
    }

    // ─────────────────────────── 요금 없음 문구 ───────────────────────────

    /**
     * ★ **예약 방에 예상 요금·소요 시간이 없다는 것을 말해야 한다.**
     *
     * 이 저장소는 거짓 문구 19개를 들어낸 이력이 있고(`ProductClaimsTest`), 이 자리가 바로 그런
     * 자리였다: `POST /matching/routes` 추정은 예약에서도 성공하므로 아무것도 고치지 않으면
     * 화면이 **방에는 없는 금액**을 적는다. 그래서 숫자를 감추고 **없다는 사실**을 적는다.
     */
    @Test
    fun `예약 안내는 요금과 소요 시간이 없다고 말한다`() {
        assertTrue(RESERVATION_NO_ESTIMATE_NOTICE, RESERVATION_NO_ESTIMATE_NOTICE.contains("예상 요금"))
        assertTrue(RESERVATION_NO_ESTIMATE_NOTICE, RESERVATION_NO_ESTIMATE_NOTICE.contains("소요 시간"))
        // 「계산 중」처럼 곧 나올 것처럼 말하지 않는다 — 서버가 끝까지 주지 않는다
        assertFalse(RESERVATION_NO_ESTIMATE_NOTICE, RESERVATION_NO_ESTIMATE_NOTICE.contains("계산"))
        // 서버가 보내는 알림(예약 리마인더)을 **약속하지 않는다** — 앱이 확인할 수 없는 말이다
        listOf(RESERVATION_NO_ESTIMATE_NOTICE, RESERVATION_CAPACITY_NOTICE, RESERVATION_NEEDS_TIME_NOTICE)
            .forEach { copy ->
                assertFalse("없는 알림을 약속한다: $copy", copy.contains("알림"))
                assertFalse("없는 알림을 약속한다: $copy", copy.contains("알려 드려요"))
            }
    }

    /**
     * ★ **나갈 수 없다는 사실을 두 자리가 같은 문장으로 말한다.**
     *
     * 예약 방은 만들어진 순간부터 확정이라 나가기가 **언제나** 409
     * `CANNOT_LEAVE_CONFIRMED_PARTY` 다(서버 소스 실측). 실시간 방은 「그만 찾기」로 빠져나올 수
     * 있어서, 이 말을 빼면 사용자는 **되돌릴 수 있다고 배운 동작**으로 되돌릴 수 없는 약속을 한다.
     */
    @Test
    fun `예약은 나갈 수 없다는 사실을 만들기와 합류 양쪽에서 같은 말로 알린다`() {
        val creating = reservationCommitmentNotice(joining = false)
        val joining = reservationCommitmentNotice(joining = true)

        assertEquals("예약 방은 만들면 나갈 수 없어요. 출발 시각에 안 가면 노쇼로 기록돼요", creating)
        assertEquals("예약 방은 합류하면 나갈 수 없어요. 출발 시각에 안 가면 노쇼로 기록돼요", joining)
        // 두 문장의 **뒷문장이 같다** — 같은 대가를 두 화면이 다른 무게로 말하지 않는다
        val consequence = "출발 시각에 안 가면 노쇼로 기록돼요"
        assertTrue(creating, creating.endsWith(consequence))
        assertTrue(joining, joining.endsWith(consequence))
        // 확인할 수 없는 알림을 약속하지 않는다(서버에 예약 리마인더가 있지만 앱이 발송을 모른다)
        listOf(creating, joining).forEach { copy ->
            assertFalse(copy, copy.contains("알림"))
            assertFalse(copy, copy.contains("알려 드려요"))
        }
    }

    @Test
    fun `예약 정원 안내는 2명부터라는 사실만 말한다`() {
        assertEquals("예약 방은 2명부터 만들 수 있어요", RESERVATION_CAPACITY_NOTICE)
        // 혼자 출발 안내와 **함께 뜨지 않는다** — 예약에서 정원은 1 이 될 수 없다
        assertNull(soloCapacityNotice(capacityFor(DepartureMode.SCHEDULED, 1)))
    }
}
