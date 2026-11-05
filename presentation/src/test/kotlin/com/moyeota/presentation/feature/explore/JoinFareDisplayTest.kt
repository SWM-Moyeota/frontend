package com.moyeota.presentation.feature.explore

import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RideStatus
import com.moyeota.domain.model.RoomType
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 20 합류 확인이 **예약 방에 대해 거짓을 말하지 않는지** 못 박는다.
 *
 * 예약 방이 탐색 목록에 뜨기 시작하면서 생긴 자리다. 서버는 예약 방에 요금·경로·소요 시간을 넣지
 * 않는데 [Ride.totalFare]·[Ride.farePerPerson] 은 non-null `Int` 라 **0 으로 온다** — 고치기 전
 * 이 화면은 그 0 을 금액으로 그려 「총 예상 요금 0원 · 합류 시 1인 부담 0원」이라고 적었다.
 * 「공짜로 탄다」로 읽히는 문구를 합류 버튼 바로 위에 두는 셈이었다.
 */
class JoinFareDisplayTest {

    /** KST 2026-11-09 13:00. */
    private val now = Instant.parse("2026-11-09T04:00:00Z")

    private fun ride(
        totalFare: Int = 0,
        farePerPerson: Int = 0,
        roomType: RoomType = RoomType.RESERVATION,
        departureAt: String? = "2026-11-09T09:30:00Z",
        departureLabel: String = "",
    ) = Ride(
        id = "1",
        origin = "부산대 정문",
        destination = "서면역",
        departureLabel = departureLabel,
        capacity = 3,
        members = emptyList(),
        farePerPerson = farePerPerson,
        totalFare = totalFare,
        status = RideStatus.RECRUITING,
        departureAt = departureAt,
        isConfirmed = true,
        roomType = roomType,
    )

    // ─────────────────────────── 요금 ───────────────────────────

    @Test
    fun `요금을 모르는 방에는 요금 카드를 그리지 않는다`() {
        assertNull("0원을 금액으로 그리면 공짜로 읽힌다", joinFareOrNull(totalFare = 0, farePerPerson = 0))
    }

    @Test
    fun `요금이 있으면 서버 값을 그대로 쓴다`() {
        assertEquals(
            JoinFare(totalFare = 9_600, farePerPerson = 3_200),
            joinFareOrNull(totalFare = 9_600, farePerPerson = 3_200),
        )
        // 한쪽만 아는 응답에서도 카드는 세운다 — 그 조합은 지금 오지 않지만 값을 버리지 않는다
        assertEquals(JoinFare(9_600, 0), joinFareOrNull(9_600, 0))
        assertEquals(JoinFare(0, 3_200), joinFareOrNull(0, 3_200))
    }

    /** 예약 방은 **왜 없는지**를 말할 수 있다. 실시간 방의 0 은 사유를 모르므로 단정하지 않는다. */
    @Test
    fun `요금이 없는 이유는 방 종류에 따라 다르게 말한다`() {
        val reservation = joinFareAbsentNotice(RoomType.RESERVATION)
        assertTrue(reservation, reservation.contains("예약 방"))
        assertTrue("무엇이 요금을 정하는지 말한다", reservation.contains("미터기"))

        val realtime = joinFareAbsentNotice(RoomType.REALTIME)
        assertEquals("예상 요금을 받지 못했어요", realtime)
        assertFalse("사유를 모르는 쪽에 예약을 붙이면 틀린 설명이 된다", realtime.contains("예약"))
    }

    /** 둘 다 「요금을 받게 된다」고 약속하지 않는다 — 앱이 확인할 수 없는 말이다. */
    @Test
    fun `요금 없음 안내는 요금이 생길 것이라고 약속하지 않는다`() {
        RoomType.entries.forEach { type ->
            val notice = joinFareAbsentNotice(type)
            listOf("계산 중", "곧 ", "나중에 알려", "다시 시도").forEach { forbidden ->
                assertFalse("$notice — 지킬 수 없는 말: $forbidden", notice.contains(forbidden))
            }
        }
    }

    // ─────────────────────────── 출발 표기 ───────────────────────────

    /**
     * 예약 방에서 이 줄이 비면 **언제 출발하는지 모르고 합류**한다 — 목록에서 본 시각이 합류
     * 화면에는 없는 상태가 된다.
     */
    @Test
    fun `예약 방은 약속 시각에서 출발 표기를 만든다`() {
        assertEquals("오늘 오후 6:30 출발", joinDepartureLabel(ride(), now))
    }

    /** 실시간 방은 예전 표기를 그대로 쓴다 — 서버 라벨에 거리가 함께 들어 있다. */
    @Test
    fun `실시간 방은 서버 표기를 쓴다`() {
        assertNull(
            joinDepartureLabel(
                ride(roomType = RoomType.REALTIME, departureLabel = "3분 후 출발 예정 · 6.2km"),
                now,
            ),
        )
    }

    /** 읽을 수 없는 약속 시각은 null — 「시간 미정」으로 확정된 방을 미정처럼 보여 주지 않는다. */
    @Test
    fun `약속 시각을 읽을 수 없으면 출발 표기가 없다`() {
        assertNull(joinDepartureLabel(ride(departureAt = null), now))
        assertNull(joinDepartureLabel(ride(departureAt = "2026-11-09T18:30:00"), now))
    }
}
