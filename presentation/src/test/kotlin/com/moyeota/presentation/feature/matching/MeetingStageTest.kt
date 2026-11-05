package com.moyeota.presentation.feature.matching

import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RideStatus
import com.moyeota.domain.model.RoomType
import com.moyeota.domain.model.User
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 21 매칭 대기의 **약속 단계 판정** — 리더가 정한 화면 상태표를 값으로 못 박는다([meetingStage]).
 *
 * ### 왜 이 테스트가 필요한가 ★
 * 이 판정은 **시계에 달려 있고, 틀려도 스크린샷에 보이지 않는다.** 도착 버튼이 약속 10분 전에 떠야
 * 하는데 9분 전에 뜨는 화면과 올바른 화면은 눈으로 구분할 수 없고, 그 차이는 **서버 409
 * `ARRIVAL_NOT_OPEN`** 으로만 드러난다 — 사용자가 버튼을 눌러 실패한 뒤에야 안다. 그래서 경계
 * 하나하나를 값으로 고정한다.
 *
 * 경계 둘이 이 파일의 주제다.
 * | 경계 | 규칙 | 서버 쪽 근거 |
 * |---|---|---|
 * | 도착 창 | `now ≥ 약속 − 10분` (정확히 10분 전이 **참**) | 「약속 시간 10분 전부터」 · 409 `ARRIVAL_NOT_OPEN` |
 * | 출발 | `now ≥ 약속` (정각이 **참**) | 「약속 시간 뒤에만」 · 409 `MEETING_NOT_STARTED` |
 *
 * `now` 는 전부 **인자로 넣는다** — 판정 함수가 제 안에서 시계를 읽으면 이 테스트를 쓸 수 없다.
 */
class MeetingStageTest {

    /** 기준 시각. 약속 시각을 이 값에서 분으로 옮겨 각 경계를 만든다. */
    private val now: Instant = Instant.parse("2026-11-06T06:00:00Z")

    private fun at(minutesFromNow: Long): String = now.plusSeconds(minutesFromNow * 60).toString()

    private fun member(id: String, isMe: Boolean = false, isHost: Boolean = false, arrived: Boolean = false) = User(
        id = id,
        nickname = id,
        verifiedLabel = "",
        rating = 0.0,
        rideCount = 0,
        isMe = isMe,
        isHost = isHost,
        hasArrived = arrived,
    )

    /** 나(비방장) + 동승자 둘 — 정원 3인이 찬 방의 기본 구성. */
    private fun members(meArrived: Boolean = false, otherArrived: Boolean = false) = listOf(
        member("me", isMe = true, arrived = meArrived),
        member("u2", isHost = true, arrived = otherArrived),
        member("u3", arrived = otherArrived),
    )

    private fun ride(
        status: RideStatus = RideStatus.MATCHED,
        members: List<User> = members(),
        isConfirmed: Boolean = false,
        departureAt: String? = null,
        roomType: RoomType = RoomType.REALTIME,
        capacity: Int = 3,
    ) = Ride(
        id = "1",
        origin = "부산대",
        destination = "서면",
        departureLabel = "지금 출발",
        capacity = capacity,
        members = members,
        farePerPerson = 0,
        totalFare = 0,
        status = status,
        departureAt = departureAt,
        isConfirmed = isConfirmed,
        roomType = roomType,
    )

    /** 내가 방장인 목록 — 확정 권한 분기를 보려면 내 줄에 host 가 서 있어야 한다. */
    private fun hostedByMe(meArrived: Boolean = false) = listOf(
        member("me", isMe = true, isHost = true, arrived = meArrived),
        member("u2"),
        member("u3"),
    )

    // ─────────────────────── 확정 전 ───────────────────────

    @Test
    fun `모집 중에는 약속 흐름이 끼어들지 않는다`() {
        assertEquals(MeetingStage.RECRUITING, meetingStage(ride(status = RideStatus.RECRUITING), now))
    }

    @Test
    fun `정원이 차고 내가 방장이면 약속 시간을 정할 수 있다`() {
        val stage = meetingStage(ride(members = hostedByMe()), now)

        assertEquals(MeetingStage.CONFIRM_BY_ME, stage)
        assertTrue(canConfirmMeeting(ride(members = hostedByMe())))
    }

    @Test
    fun `정원이 차고 내가 방장이 아니면 방장이 정하기를 기다린다`() {
        assertEquals(MeetingStage.CONFIRM_BY_HOST, meetingStage(ride(), now))
        assertFalse(canConfirmMeeting(ride()))
    }

    /**
     * 서버 `confirm` 은 **실시간 방만** 받는다(409 `NOT_REALTIME_PARTY`). 프리셋을 띄우면 무엇을
     * 눌러도 409 이므로, 약속 흐름이 붙기 전의 화면 그대로 둔다 — 「합승 완료」가 그 자리다.
     */
    @Test
    fun `예약 방에서는 약속 시간을 정할 수 없다`() {
        val reservation = ride(members = hostedByMe(), roomType = RoomType.RESERVATION)

        assertEquals(MeetingStage.FINISH_ONLY, meetingStage(reservation, now))
        assertFalse(canConfirmMeeting(reservation))
    }

    /**
     * 서버는 **멤버 2명 이상**에서만 확정을 받는다(409 `NOT_ENOUGH_MEMBERS`). 정원 1인 방은
     * 「정원 참」이면서 혼자라 그 조건을 못 맞춘다 — `capacity` 가 아니라 **목록 길이**를 세는 이유다.
     */
    @Test
    fun `혼자 있는 방에서는 약속 시간을 정할 수 없다`() {
        val solo = ride(
            members = listOf(member("me", isMe = true, isHost = true)),
            capacity = 1,
        )

        assertEquals(MeetingStage.FINISH_ONLY, meetingStage(solo, now))
        assertFalse(canConfirmMeeting(solo))
    }

    // ─────────────────────── 도착 창 경계 ───────────────────────

    @Test
    fun `확정됐지만 약속 10분 전이 아직 안 됐으면 도착 창이 닫혀 있다`() {
        val stage = meetingStage(ride(isConfirmed = true, departureAt = at(30)), now)

        assertEquals(MeetingStage.CONFIRMED_WAITING, stage)
        assertFalse(canArriveAtDeparture(ride(isConfirmed = true, departureAt = at(30)), now))
    }

    /**
     * **정확히 10분 전이 참이다.** 서버가 「10분 전부터」라고 적었으므로 그 순간은 열려 있어야 한다.
     * 한 칸 어긋나면 10분 전에 맞춰 도착한 사람이 버튼을 못 보고, 10분 1초 전에는 눌러서 409 를 맞는다.
     */
    @Test
    fun `도착 창은 약속 10분 전 그 순간에 열린다`() {
        // 10분 1초 전 — 아직 닫혀 있다
        assertFalse(canArriveAtDeparture(ride(isConfirmed = true, departureAt = at(10).plusOneSecond()), now))
        // 정확히 10분 전 — 열린다
        assertTrue(canArriveAtDeparture(ride(isConfirmed = true, departureAt = at(10)), now))
        // 9분 전 — 열려 있다
        assertTrue(canArriveAtDeparture(ride(isConfirmed = true, departureAt = at(9)), now))
    }

    /**
     * 도착 창에는 **상한이 없다.** 약속 시간이 지난 뒤에도 도착을 알릴 수 있어야 한다 — 늦게 온
     * 사람이 노쇼를 면할 유일한 길이고, 서버 도착 창에도 상한이 없다.
     */
    @Test
    fun `약속 시간이 지나도 미도착이면 도착을 알릴 수 있다`() {
        val late = ride(isConfirmed = true, departureAt = at(-5))

        assertEquals(MeetingStage.ARRIVE_OPEN, meetingStage(late, now))
        assertTrue(canArriveAtDeparture(late, now))
        // 단계에는 드러나지 않는 사실 — 「왜 나만 출발이 없는지」를 말해 줄 근거다
        assertTrue(isMeetingTimePassed(late.departureAt, now))
    }

    // ─────────────────────── 출발 경계 ───────────────────────

    @Test
    fun `도착했지만 약속 시간 전이면 출발할 수 없다`() {
        val waiting = ride(isConfirmed = true, departureAt = at(5), members = members(meArrived = true))

        assertEquals(MeetingStage.ARRIVED_WAITING, meetingStage(waiting, now))
        assertFalse(canDepartParty(waiting, now))
    }

    /**
     * **약속 시간 정각이 참이다.** 서버는 약속 시간 **전**에만 409 `MEETING_NOT_STARTED` 를 던지므로,
     * 정각을 막으면 약속 시간에 딱 맞춰 누른 사람이 서버가 허용하는 일을 못 하게 된다.
     *
     * 1초 단위로 짚는 이유: 분 단위로만 보면 「정각 포함/제외」가 갈리지 않는다.
     */
    @Test
    fun `출발은 약속 시간 정각에 열린다`() {
        fun departableAtSeconds(offsetSeconds: Long) = canDepartParty(
            ride(
                isConfirmed = true,
                departureAt = now.plusSeconds(offsetSeconds).toString(),
                members = members(meArrived = true),
            ),
            now,
        )

        assertFalse("약속 1초 전에는 막혀야 한다", departableAtSeconds(1))
        assertTrue("정각은 열린다", departableAtSeconds(0))
        assertTrue("지난 뒤에도 열린다", departableAtSeconds(-60))
    }

    /**
     * **출발은 방장 전용이 아니다** — 서버가 도착한 멤버 누구에게나 열어 두었다(`NOT_ARRIVED` 만 본다).
     * 「합승 완료」의 방장 조건을 베껴 오면 **방장이 늦는 방이 영원히 출발하지 못한다.**
     */
    @Test
    fun `출발은 도착한 멤버 누구나 누를 수 있다 - 방장 조건이 아니다`() {
        // 내 줄에 host 가 없다(u2 가 방장) — 그래도 출발할 수 있어야 한다
        val notHostButArrived = ride(
            isConfirmed = true,
            departureAt = at(-1),
            members = members(meArrived = true),
        )

        assertFalse("내가 방장이 아닌 구성이어야 의미가 있다", canFinishParty(notHostButArrived))
        assertEquals(MeetingStage.DEPART_OPEN, meetingStage(notHostButArrived, now))
        assertTrue(canDepartParty(notHostButArrived, now))
    }

    @Test
    fun `도착하지 않으면 약속 시간이 지나도 출발할 수 없다`() {
        val notArrived = ride(isConfirmed = true, departureAt = at(-1))

        assertEquals(MeetingStage.ARRIVE_OPEN, meetingStage(notArrived, now))
        assertFalse(canDepartParty(notArrived, now))
    }

    // ─────────────────────── 읽을 수 없는 값 · 닫힌 방 ───────────────────────

    /**
     * **약속 시각을 읽을 수 없으면 아무 창도 열지 않는다.** 열어 두고 409 를 맞게 하는 것보다
     * 「확정됐다」만 보여 주는 편이 낫다 — 틀린 시각으로 사람을 엉뚱한 때에 모이게 하는 것이 최악이다.
     */
    @Test
    fun `약속 시각을 읽을 수 없으면 도착도 출발도 열지 않는다`() {
        for (broken in listOf(null, "", "   ", "오후 3시", "2026-11-06 15:00")) {
            val r = ride(isConfirmed = true, departureAt = broken, members = members(meArrived = true))
            assertEquals("departureAt=$broken", MeetingStage.ARRIVED_WAITING, meetingStage(r, now))
            assertFalse("departureAt=$broken", canDepartParty(r, now))

            val notArrived = ride(isConfirmed = true, departureAt = broken)
            assertEquals("departureAt=$broken", MeetingStage.CONFIRMED_WAITING, meetingStage(notArrived, now))
            assertFalse("departureAt=$broken", canArriveAtDeparture(notArrived, now))
        }
    }

    /** 오프셋 표기(`+09:00`)도 읽는다 — 서버가 UTC `Z` 로 주지만 계약은 ISO-8601 이다. */
    @Test
    fun `오프셋 표기도 읽는다`() {
        // 2026-11-06T15:05:00+09:00 == 06:05:00Z == now + 5분 → 도착 창이 열려 있다
        val offset = ride(isConfirmed = true, departureAt = "2026-11-06T15:05:00+09:00")

        assertEquals(MeetingStage.ARRIVE_OPEN, meetingStage(offset, now))
    }

    /**
     * 닫힌 방(FINISHED·CANCELED)에서는 **모든 단계가 CLOSED** 다. Route 가 곧 화면을 떠나지만
     * 그 사이 한 프레임을 그리고, 그 프레임에서 눌린 확정·도착·출발은 409 로만 끝난다.
     *
     * 확정 플래그가 서 있어도 그렇다 — 닫힘 판정이 **맨 위**에 있어야 하는 이유다.
     */
    @Test
    fun `닫힌 방에서는 약속 흐름이 전부 닫힌다`() {
        for (status in listOf(RideStatus.COMPLETED, RideStatus.CANCELED)) {
            val closed = ride(
                status = status,
                isConfirmed = true,
                departureAt = at(-1),
                members = members(meArrived = true),
            )
            assertEquals("$status", MeetingStage.CLOSED, meetingStage(closed, now))
            assertFalse("$status", canArriveAtDeparture(closed, now))
            assertFalse("$status", canDepartParty(closed, now))
            assertFalse("$status", canConfirmMeeting(closed))
        }
    }

    /**
     * **열거형 전체를 훑어** 「대기 단계가 아니면 CLOSED」를 못 박는다. 서버에 단계가 늘어
     * [RideStatus] 에 값이 더해지면, 이름을 적는 위 테스트는 그 값을 **묻지 않고 지나간다.**
     * 「새 단계에서는 약속 흐름을 열지 않는다」가 안전한 기본값이다.
     */
    @Test
    fun `대기 단계가 아닌 모든 status 는 CLOSED 다`() {
        RideStatus.entries
            .filter { it != RideStatus.RECRUITING && it != RideStatus.MATCHED }
            .forEach { status ->
                assertEquals("$status", MeetingStage.CLOSED, meetingStage(ride(status = status), now))
            }
    }

    /**
     * **확정 판정이 정원 판정보다 위에 있어야 한다.** SSE `confirmed` 는 `isConfirmed` 만 세우고
     * status 는 건드리지 않으므로(프레임의 status 가 문자열이라 옮길 수 없다) **확정된 RECRUITING**
     * 을 그리는 한 프레임이 실제로 존재한다. 순서가 뒤집히면 이미 확정된 방에 「약속 시간 정하기」가
     * 다시 떠서 409 `ALREADY_CONFIRMED` 로 끝난다.
     */
    @Test
    fun `확정 플래그가 정원 단계보다 먼저 읽힌다`() {
        val confirmedButRecruiting = ride(
            status = RideStatus.RECRUITING,
            members = hostedByMe(),
            isConfirmed = true,
            departureAt = at(30),
        )

        assertEquals(MeetingStage.CONFIRMED_WAITING, meetingStage(confirmedButRecruiting, now))
        assertFalse(canConfirmMeeting(confirmedButRecruiting))
    }

    // ─────────────────────── 도착 집계 ───────────────────────

    @Test
    fun `내 도착 여부는 멤버 목록의 내 줄만 본다`() {
        assertTrue(hasArrivedMyself(ride(members = members(meArrived = true))))
        assertFalse(hasArrivedMyself(ride(members = members(meArrived = false))))
    }

    /**
     * 내가 목록에 없으면 false 다 — 그건 「도착하지 않았다」가 아니라 **「도착했다고 말해 주는
     * 근거가 없다」**다. 그 경우 화면은 도착 버튼을 띄우고, 이미 도착했다면 서버가 멱등하게 받는다.
     */
    @Test
    fun `내가 목록에 없으면 도착 여부를 단정하지 않는다`() {
        assertFalse(hasArrivedMyself(ride(members = listOf(member("u2"), member("u3")))))
    }

    @Test
    fun `도착 인원과 미도착 인원이 목록에서 나온다`() {
        val r = ride(members = members(meArrived = true, otherArrived = false))

        assertEquals(1, arrivedCount(r))
        assertEquals(2, notArrivedCount(r))
        assertEquals(3, arrivedCount(r) + notArrivedCount(r))
    }

    /** 「도착」 배지는 **확정된 방에서만** 뜻이 있다 — 확정 전에는 도착 개념이 없다. */
    @Test
    fun `도착 배지는 확정 전에는 붙지 않는다`() {
        val arrivedMember = member("me", isMe = true, arrived = true)

        assertTrue(showsArrivedBadge(arrivedMember, confirmed = true))
        assertFalse(showsArrivedBadge(arrivedMember, confirmed = false))
        assertFalse(showsArrivedBadge(member("u2"), confirmed = true))
    }
}

/** `at(10)` 보다 **1초 앞선** 시각 — 「정확히 10분 전」 경계의 바로 바깥을 짚는다. */
private fun String.plusOneSecond(): String = Instant.parse(this).plusSeconds(1).toString()
