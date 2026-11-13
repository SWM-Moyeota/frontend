package com.moyeota.presentation.feature.home

import com.moyeota.core.designsystem.component.DateStripTone
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

    // ─────────────────────────── 시각 범위(10분 휠) ───────────────────────────

    /** KST 벽시계 시각 → 순간. 테스트가 「한국 시간 몇 시」로 읽히게 한다. */
    private fun kst(date: LocalDate, hour: Int, minute: Int, second: Int = 0): Instant =
        date.atTime(hour, minute, second).atZone(ZoneId.of("Asia/Seoul")).toInstant()

    private val monday = LocalDate.of(2026, 11, 9)

    /**
     * 오늘의 가장 이른 값 = 「지금 + 10분」을 **10분 단위로 올림**. 정확히 경계에 떨어지면(초까지 0) 그대로다
     * — 그 값이 이미 10분 여유를 품고 있다.
     */
    @Test
    fun `오늘은 지금부터 10분 뒤를 10분 단위로 올린 시각부터다`() {
        val range = reservationTimeRange(monday, now)!! // 기준 시각 KST 13:00:00

        assertEquals(LocalTime.of(13, 10), range.earliest)
        assertEquals(LocalTime.of(23, 50), range.latest)
        assertFalse("이미 지난 시각", LocalTime.of(12, 50) in range)
    }

    /** 초가 남아 있으면 분 단위로 먼저 올린다 — 13:00:30 + 10분을 13:10 으로 내리면 여유가 30초 모자란다. */
    @Test
    fun `초가 남아 있으면 다음 10분으로 올린다`() {
        assertEquals(LocalTime.of(13, 20), reservationTimeRange(monday, kst(monday, 13, 0, 30))!!.earliest)
        assertEquals(LocalTime.of(13, 20), reservationTimeRange(monday, kst(monday, 13, 3))!!.earliest)
        assertEquals(LocalTime.of(13, 20), reservationTimeRange(monday, kst(monday, 13, 10))!!.earliest)
    }

    @Test
    fun `내일은 자정부터 23시 50분까지 전부 고를 수 있다`() {
        val range = reservationTimeRange(monday.plusDays(1), now)!!

        assertEquals(LocalTime.of(0, 0), range.earliest)
        assertEquals(LocalTime.of(23, 50), range.latest)
        assertEquals((0..23).toList(), reservationHourOptions(range))
        assertEquals(listOf(0, 10, 20, 30, 40, 50), reservationMinuteOptions(range, 18))
    }

    /**
     * ★ **보이지 않는 경계** — 「7일 뒤」 칸은 있는데 그 날 늦은 시각은 창을 넘는다.
     *
     * 기준 시각이 KST 13:00 이므로 7일 뒤에 허용되는 것은 **그 날 13:00 까지**다(경계 포함 — 서버가
     * `isAfter` 로 본다). 이 경계가 없으면 사용자가 7일 뒤 23:50 을 골라 400 `INVALID_DEPARTURE_TIME` 을 만든다.
     */
    @Test
    fun `7일 뒤는 지금 시각까지만 고를 수 있다`() {
        val range = reservationTimeRange(LocalDate.of(2026, 11, 16), now)!!

        assertEquals(LocalTime.of(0, 0), range.earliest)
        assertEquals(LocalTime.of(13, 0), range.latest)
        assertEquals((0..13).toList(), reservationHourOptions(range))
        assertEquals("마지막 시에는 정각만 남는다", listOf(0), reservationMinuteOptions(range, 13))
    }

    /** 늦은 쪽은 10분 단위로 **내린다** — 13:05 의 7일 뒤 13:10 은 이미 창 밖이다. */
    @Test
    fun `7일 뒤의 마지막 값은 10분 단위로 내린다`() {
        val range = reservationTimeRange(LocalDate.of(2026, 11, 16), kst(monday, 13, 5))!!

        assertEquals(LocalTime.of(13, 0), range.latest)
    }

    /** 자정 직후에는 7일 뒤의 선택지가 **자정 한 칸**으로 줄어든다(비지는 않는다). */
    @Test
    fun `자정 직후의 7일 뒤는 자정 한 칸만 남는다`() {
        val justAfterMidnight = Instant.parse("2026-11-08T15:05:00Z") // KST 11-09 00:05
        val lastDay = reservationDates(justAfterMidnight).last() // 11-16

        val range = reservationTimeRange(lastDay, justAfterMidnight)!!
        assertEquals(LocalTime.of(0, 0), range.earliest)
        assertEquals(LocalTime.of(0, 0), range.latest)
    }

    /**
     * 늦은 밤에는 **「오늘」에 고를 시각이 없다** — 오늘의 마지막 값이 23:50 이고 10분 여유가 필요하다.
     * 그 날은 띠에서 누를 수 없고([reservationDateCells]) 기본 선택이 다음 날로 옮겨 간다.
     */
    @Test
    fun `밤 23시 55분에는 오늘 고를 시간이 없고 내일은 00시 10분부터다`() {
        val lateNight = Instant.parse("2026-11-09T14:55:00Z") // KST 23:55
        val today = reservationDates(lateNight).first()

        assertNull(reservationTimeRange(today, lateNight))
        // 23:55 + 10분 = 00:05 → 10분 단위로 올려 00:10
        assertEquals(LocalTime.of(0, 10), reservationTimeRange(today.plusDays(1), lateNight)!!.earliest)
    }

    @Test
    fun `밤 23시 40분 정각이면 오늘은 23시 50분 한 칸이 남는다`() {
        val range = reservationTimeRange(monday, kst(monday, 23, 40))!!

        assertEquals(LocalTime.of(23, 50), range.earliest)
        assertEquals(LocalTime.of(23, 50), range.latest)
        assertNull("1분만 지나도 오늘은 끝이다", reservationTimeRange(monday, kst(monday, 23, 41)))
    }

    @Test
    fun `시 휠은 범위에 걸치는 시만 담고 분 휠은 그 시 안의 값만 담는다`() {
        val range = reservationTimeRange(monday, kst(monday, 13, 3))!! // 13:20 ~ 23:50

        assertEquals((13..23).toList(), reservationHourOptions(range))
        assertEquals("첫 시는 잘린다", listOf(20, 30, 40, 50), reservationMinuteOptions(range, 13))
        assertEquals(listOf(0, 10, 20, 30, 40, 50), reservationMinuteOptions(range, 14))
    }

    // ─────────────────────────── 휠의 기본값·접기 ───────────────────────────

    /**
     * ★ **규칙 변경** — 칩 시절에는 기본 선택이 없었다. 휠은 늘 값을 보여 주므로 손대지 않았으면
     * **그 날의 가장 이른 시각**이 선택이고, CTA 는 켜진다([reservationSelectedTime] KDoc).
     */
    @Test
    fun `휠을 건드리지 않았으면 그 날의 가장 이른 시각이 선택이다`() {
        assertEquals(LocalTime.of(13, 10), reservationSelectedTime(null, reservationTimeRange(monday, now)))
        assertEquals(LocalTime.of(0, 0), reservationSelectedTime(null, reservationTimeRange(monday.plusDays(1), now)))
    }

    @Test
    fun `그 날에 시각이 없으면 선택도 없다`() {
        val lateNight = Instant.parse("2026-11-09T14:55:00Z")
        assertNull(reservationSelectedTime(LocalTime.of(18, 0), reservationTimeRange(monday, lateNight)))
    }

    /** 날짜를 바꾸면 보여 줄 값만 그 날의 범위로 접는다 — 고른 값은 다른 날로 돌아가면 살아 있다. */
    @Test
    fun `날짜를 바꾸면 고른 시각을 그 날의 범위로 접는다`() {
        val chosen = LocalTime.of(18, 30)
        val lastDay = reservationTimeRange(LocalDate.of(2026, 11, 16), now) // ~13:00
        val today = reservationTimeRange(monday, now) // 13:10~

        assertEquals("7일 뒤로 가면 마지막 값으로", LocalTime.of(13, 0), reservationSelectedTime(chosen, lastDay))
        assertEquals("오늘 이른 값은 가장 이른 값으로", LocalTime.of(13, 10), reservationSelectedTime(LocalTime.of(9, 0), today))
        assertEquals("범위 안이면 그대로", chosen, reservationSelectedTime(chosen, today))
    }

    @Test
    fun `10분 단위가 아닌 값은 10분 단위로 내려 접는다`() {
        val tomorrow = reservationTimeRange(monday.plusDays(1), now)
        assertEquals(LocalTime.of(18, 30), reservationSelectedTime(LocalTime.of(18, 37, 12), tomorrow))
    }

    /** 시를 굴리면 분은 지키고, 범위를 벗어나면 범위 안으로 올린다. */
    @Test
    fun `시를 굴리면 분을 지키되 범위로 접는다`() {
        val range = reservationTimeRange(monday, kst(monday, 13, 3))!! // 13:20 ~

        assertEquals(LocalTime.of(15, 40), reservationTimeWithHour(LocalTime.of(18, 40), 15, range))
        assertEquals(LocalTime.of(13, 40), reservationTimeWithHour(LocalTime.of(18, 40), 13, range))
        assertEquals(LocalTime.of(13, 20), reservationTimeWithHour(LocalTime.of(18, 0), 13, range))
        assertEquals(LocalTime.of(18, 50), reservationTimeWithMinute(LocalTime.of(18, 0), 50, range))
    }

    /**
     * ★ **휠에 보이는 값 = 서버로 가는 값.** 접힌 값으로 ISO 를 만들고, 보내는 자리에서 시계를 다시 읽지
     * 않는다 — 화면의 시각과 보낸 시각이 어긋나면 약속 시간이 다른 방이 만들어진다(바꿀 수 없다).
     */
    @Test
    fun `보내는 출발 시각은 휠이 보여 주는 값과 같다`() {
        val shown = reservationSelectedTime(null, reservationTimeRange(monday, now))!!
        val iso = reservationDepartureIso(monday, shown)

        assertEquals(LocalTime.of(13, 10), shown)
        assertEquals(Instant.parse("2026-11-09T04:10:00Z"), Instant.parse(iso))
    }

    @Test
    fun `휠 글자는 시는 그대로 분은 두 자리다`() {
        assertEquals("0", reservationHourLabel(0))
        assertEquals("18", reservationHourLabel(18))
        assertEquals("00", reservationMinuteLabel(0))
        assertEquals("50", reservationMinuteLabel(50))
    }

    // ─────────────────────────── 날짜 띠 ───────────────────────────

    @Test
    fun `날짜 칸은 요일과 일을 담고 토요일 파랑 일요일 빨강이다`() {
        val dates = reservationDates(now) // 11-09(월) ~ 11-16(월)
        val cells = reservationDateCells(dates, dates.first())

        assertEquals(8, cells.size)
        assertEquals(listOf("월", "화", "수", "목", "금", "토", "일", "월"), cells.map { it.weekday })
        assertEquals(listOf("9", "10", "11", "12", "13", "14", "15", "16"), cells.map { it.day })
        assertEquals(DateStripTone.SATURDAY, cells[5].tone)
        assertEquals(DateStripTone.SUNDAY, cells[6].tone)
        assertEquals(DateStripTone.WEEKDAY, cells[0].tone)
        assertEquals("오늘 점은 첫 칸에만", listOf(true) + List(7) { false }, cells.map { it.isToday })
        assertTrue("기본은 모든 날을 고를 수 있다(17 날짜로 찾기)", cells.all { it.enabled })
    }

    @Test
    fun `날짜 칸의 스크린리더 문장은 월과 오늘 내일을 말한다`() {
        val dates = reservationDates(now)
        val cells = reservationDateCells(dates, dates.first())

        assertEquals("오늘, 11월 9일 월요일", cells[0].contentDescription)
        assertEquals("내일, 11월 10일 화요일", cells[1].contentDescription)
        assertEquals("11월 14일 토요일", cells[5].contentDescription)
    }

    /** 16 은 「그 날에 남은 시각이 있는가」로 칸을 막는다 — 늦은 밤의 오늘 칸은 누를 수 없다. */
    @Test
    fun `고를 시각이 없는 날은 누를 수 없는 칸이 되고 기본 선택은 다음 날로 간다`() {
        val lateNight = Instant.parse("2026-11-09T14:55:00Z") // KST 23:55
        val dates = reservationDates(lateNight)
        val selectable: (LocalDate) -> Boolean = { reservationTimeRange(it, lateNight) != null }
        val cells = reservationDateCells(dates, dates.first(), selectable)

        assertFalse(cells[0].enabled)
        assertTrue(cells.drop(1).all { it.enabled })
        assertEquals(1, reservationSelectableDateIndex(0, dates, selectable))
        assertEquals("고를 수 있는 날을 고른 것은 그대로", 3, reservationSelectableDateIndex(3, dates, selectable))
    }

    // ─────────────────────────── 기준 시각을 옮길 때 ───────────────────────────

    private fun selectableAt(now: Instant): (LocalDate) -> Boolean = { reservationTimeRange(it, now) != null }

    /**
     * ★ 리뷰 반영(PR #99): 23:58 에 「내일」을 고르고 00:05 에 누르면 다시 재기가 실패해 기준이 옮겨진다. 일수(1)를
     * 그대로 두면 새 띠에서 「모레」를 가리킨다 — 보던 **달력 날짜**의 자리를 다시 찾는다.
     */
    @Test
    fun `자정을 넘겨 기준을 옮겨도 고른 날짜는 그대로다`() {
        val opened = Instant.parse("2026-11-09T14:58:00Z") // KST 11-09 23:58
        val tomorrow = LocalDate.of(2026, 11, 10)
        val openedDates = reservationDates(opened)
        assertEquals(tomorrow, openedDates[1])
        // 「내일」의 기본 시각(가장 이른 00:10)은 00:05 에 누르면 여유를 잃었다 — 기준을 옮기는 길이다
        val defaultTime = reservationTimeRange(tomorrow, opened)!!.earliest
        assertEquals(LocalTime.of(0, 10), defaultTime)
        val fresh = Instant.parse("2026-11-09T15:05:00Z") // KST 11-10 00:05
        assertFalse(reservationTimeStillValid(tomorrow, defaultTime, fresh))

        val freshDates = reservationDates(fresh)
        val offset = reservationDateOffsetAfterClockMove(tomorrow, freshDates, selectableAt(fresh))

        assertEquals(0, offset)
        assertEquals(tomorrow, freshDates[reservationSelectableDateIndex(offset, freshDates, selectableAt(fresh))])
        assertEquals("예전처럼 일수를 그대로 두면 「모레」가 된다", LocalDate.of(2026, 11, 11), freshDates[1])
    }

    /** 자정을 넘기지 않으면 일수는 그대로다 — 날짜 띠가 움직이지 않았다. */
    @Test
    fun `같은 날 안에서 기준을 옮기면 일수는 그대로다`() {
        val opened = now // KST 13:00
        val picked = reservationDates(opened)[3]
        val fresh = opened.plusSeconds(15 * 60) // KST 13:15
        assertEquals(3, reservationDateOffsetAfterClockMove(picked, reservationDates(fresh), selectableAt(fresh)))
    }

    /**
     * 「오늘」을 골라 둔 채 자정을 넘기면 그 날은 **어제**가 돼 띠에서 사라진다 — 고를 수 있는 첫 날로 간다.
     * 고를 수 있는 날을 판정으로 고르는지는 가짜 판정으로 따로 본다(실제로는 자정 직후의 오늘은 늘 고를 수 있다).
     */
    @Test
    fun `고른 날짜가 범위를 벗어나면 고를 수 있는 첫 날로 간다`() {
        val today = LocalDate.of(2026, 11, 9)
        val fresh = Instant.parse("2026-11-09T15:05:00Z") // KST 11-10 00:05 — 11-09 는 띠에서 빠졌다
        val freshDates = reservationDates(fresh)
        assertFalse(today in freshDates)

        assertEquals(0, reservationDateOffsetAfterClockMove(today, freshDates, selectableAt(fresh)))
        assertEquals(1, reservationDateOffsetAfterClockMove(today, freshDates) { it != freshDates[0] })
    }

    /**
     * 범위 안이지만 고를 수 없게 된 날(밤 23:40 을 넘긴 「오늘」)은 그 자리를 저장한다 — 보여 줄 때
     * [reservationSelectableDateIndex] 가 다음 날로 접는다(「고른 값은 지우지 않고 보여 줄 값만 접는다」).
     */
    @Test
    fun `범위 안이지만 고를 수 없게 된 날은 그 자리를 두고 보여 줄 때 접는다`() {
        val today = LocalDate.of(2026, 11, 9)
        val fresh = Instant.parse("2026-11-09T14:45:00Z") // KST 23:45 — 오늘은 고를 시각이 없다
        val freshDates = reservationDates(fresh)

        val offset = reservationDateOffsetAfterClockMove(today, freshDates, selectableAt(fresh))

        assertEquals(0, offset)
        assertEquals(1, reservationSelectableDateIndex(offset, freshDates, selectableAt(fresh)))
    }

    @Test
    fun `띠 위의 달 표시는 달을 넘으면 범위로 쓴다`() {
        assertEquals("11월", reservationMonthLabel(reservationDates(now)))
        val endOfMonth = Instant.parse("2026-11-27T04:00:00Z") // 11-27 ~ 12-04
        assertEquals("11–12월", reservationMonthLabel(reservationDates(endOfMonth)))
        val endOfYear = Instant.parse("2026-12-28T04:00:00Z") // 12-28 ~ 01-04
        assertEquals("12–1월", reservationMonthLabel(reservationDates(endOfYear)))
        assertNull(reservationMonthLabel(emptyList()))
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

    @Test
    fun `지금 출발이면 휠에 시각이 있어도 출발 시각을 싣지 않는다`() {
        val date = LocalDate.of(2026, 11, 14)
        val time = LocalTime.of(21, 30)
        // 휠은 늘 값을 보여 준다 — 그 값을 「지금 출발」에서 실으면 실시간 방 대신 예약 방이 만들어진다
        assertNull(matchDepartureAt(scheduled = false, date = date, time = time))
        // 「시간 정하기」면 휠 값 그대로 — 한국 21:30 = UTC 12:30
        assertEquals(Instant.parse("2026-11-14T12:30:00Z"), Instant.parse(matchDepartureAt(scheduled = true, date = date, time = time)))
        assertEquals(reservationDepartureIso(date, time), matchDepartureAt(scheduled = true, date = date, time = time))
        // 고를 시각이 없는 날(늦은 밤의 오늘)은 「시간 정하기」여도 없다 — CTA 가 잠긴다
        assertNull(matchDepartureAt(scheduled = true, date = date, time = null))
    }

    @Test
    fun `오래 열어 둬 여유를 잃은 기본 시각은 보내기 전에 다시 잰다`() {
        val opened = Instant.parse("2026-11-14T04:00:30Z") // 한국 13:00:30
        val today = LocalDate.of(2026, 11, 14)
        val earliest = reservationTimeRange(today, opened)!!.earliest
        assertEquals(LocalTime.of(13, 20), earliest) // 13:10:30 → 10분 단위로 올림
        // 1분 뒤에 누르면 그대로 보낸다
        assertTrue(reservationTimeStillValid(today, earliest, opened.plusSeconds(60)))
        // 10분 넘게 머물면 13:20 은 여유가 없다 — 보내지 않고 기준을 옮긴다
        assertFalse(reservationTimeStillValid(today, earliest, opened.plusSeconds(10 * 60 + 1)))
        // 고를 시각이 없으면 보낼 것이 없다
        assertFalse(reservationTimeStillValid(today, null, opened))
    }
}
