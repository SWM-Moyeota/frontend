package com.moyeota.presentation.feature.matching

import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RideStatus
import com.moyeota.domain.model.User
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 약속 흐름의 **프리셋 계산과 화면 문구** — 보내는 값과 보여 주는 값이 서로를 배신하지 않는지.
 *
 * 세 가지를 못 박는다.
 * 1. **프리셋이 서버 한도를 넘지 않는다.** 서버는 약속 시간을 「지금부터 1시간 안」으로만 받고
 *    벗어나면 400 `INVALID_MEETING_TIME` 이다. 프리셋을 둔 이유가 그 400 을 사용자가 만들 수 없게
 *    하는 것이라, 프리셋 자체가 한도를 넘으면 **설계가 무의미해진다.**
 * 2. **표시한 시각과 보낸 시각이 같다.** 「오후 3:23 로 정할까요?」를 보고 누른 사람에게 3:23:47 이
 *    확정되면 화면이 거짓을 말한 것이다.
 * 3. **모르는 값을 지어내지 않는다.** 약속 시각을 읽을 수 없으면 null·폴백 문구이고, 틀린 시각을
 *    띄우지 않는다 — 틀린 약속 시간은 사람을 엉뚱한 때에 모이게 한다.
 */
class MeetingCopyTest {

    private val now: Instant = Instant.parse("2026-11-06T06:13:47Z")

    /** 표기 검증은 **기기 시간대에 흔들려서는 안 된다** — KST 로 못 박는다(서버는 UTC 로 준다). */
    private val kst: ZoneId = ZoneId.of("Asia/Seoul")

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

    private fun ride(members: List<User>, isConfirmed: Boolean = true) = Ride(
        id = "1",
        origin = "부산대",
        destination = "서면",
        departureLabel = "지금 출발",
        capacity = 3,
        members = members,
        farePerPerson = 0,
        totalFare = 0,
        status = RideStatus.CONFIRMED,
        isConfirmed = isConfirmed,
    )

    // ─────────────────────── 약속 시간 휠 ───────────────────────

    /**
     * 휠에는 **서버가 받는 시각만** 있다 — 지금부터 10분 뒤(올림) ~ 1시간 뒤(내림), 5분 정각.
     * 06:13:47 이면 06:23:47 을 올린 **06:25** 부터 07:13:47 을 내린 **07:10** 까지 열 칸이다.
     */
    @Test
    fun `휠은 10분 뒤부터 1시간 뒤까지 5분 정각만 담는다`() {
        val options = meetingTimeOptions(now)

        assertEquals(Instant.parse("2026-11-06T06:25:00Z"), options.first())
        assertEquals(Instant.parse("2026-11-06T07:10:00Z"), options.last())
        assertEquals(10, options.size)
        options.forEach { at ->
            assertEquals("5분 정각이 아니다: $at", 0L, at.epochSecond % 300)
            assertTrue("10분이 안 된다: $at", !at.isBefore(now.plusSeconds(600)))
            assertTrue("1시간을 넘는다: $at", !at.isAfter(now.plusSeconds(3600)))
        }
        assertEquals(options.sorted(), options)
    }

    /** 10분 뒤가 마침 5분 정각이면 그 칸부터다 — 올림이 한 칸을 더 건너뛰지 않는다. */
    @Test
    fun `정각에 맞으면 그 칸부터 시작한다`() {
        val options = meetingTimeOptions(Instant.parse("2026-11-06T06:15:00Z"))

        assertEquals(Instant.parse("2026-11-06T06:25:00Z"), options.first())
        assertEquals(Instant.parse("2026-11-06T07:15:00Z"), options.last())
    }

    /** 자정을 넘는 범위도 시각 순서 그대로다 — 시·분 휠이었다면 고를 수 없는 조합이 생기는 자리다. */
    @Test
    fun `자정을 넘어도 이른 순서로 이어진다`() {
        // KST 23:40
        val options = meetingTimeOptions(Instant.parse("2026-11-06T14:40:00Z"))
        val labels = options.map { meetingTimeLabel(it.toString(), kst) }

        assertEquals("오후 11:50", labels.first())
        assertEquals("오전 12:40", labels.last())
        assertTrue(labels.toString(), labels.contains("오전 12:00"))
    }

    /** 시간이 흘러 고른 시각이 목록에서 빠지면 가장 이른 시각을 가리킨다 — 빠진 값으로 확정하지 않는다. */
    @Test
    fun `고른 시각이 목록에서 빠지면 가장 이른 시각으로 옮긴다`() {
        val options = meetingTimeOptions(now)

        assertEquals(options.first(), meetingSelectedTime(options, null))
        assertEquals(options[3], meetingSelectedTime(options, options[3]))
        assertEquals(options.first(), meetingSelectedTime(options, Instant.parse("2026-11-06T06:20:00Z")))
    }

    /**
     * **버튼이 말하는 시각이 그대로 보내진다** — 휠이 가리키는 값 하나를 버튼 글자와 요청이 함께 쓴다
     * (실측 2026-11-06: 제목과 요청이 시계를 따로 읽어 「오전 10:59에 만날까요?」로 묻고 11:00 으로 확정됐다).
     */
    @Test
    fun `버튼이 말하는 시각이 실제로 보내는 시각이다`() {
        val picked = meetingTimeOptions(now).first()

        assertEquals("2026-11-06T06:25:00Z", picked.toString())
        assertEquals("오후 3:25", meetingTimeLabel(picked.toString(), kst))
        assertEquals("오후 3:25에 만나기", meetingConfirmButtonText(meetingTimeLabel(picked.toString(), kst)))
    }

    /** 휠 아래 되읽기는 남은 분을 **올림**한다 — 11분 13초 남았으면 「12분 뒤」가 아니라 거짓이 되지 않게. */
    @Test
    fun `휠 아래에 지금부터 몇 분 뒤인지 적는다`() {
        assertEquals("지금부터 12분 뒤", meetingMinutesFromNowLabel(now, Instant.parse("2026-11-06T06:25:00Z")))
        assertEquals("지금부터 57분 뒤", meetingMinutesFromNowLabel(now, Instant.parse("2026-11-06T07:10:00Z")))
    }

    // ─────────────────────── 시각 표기 ───────────────────────

    /**
     * **문자열을 자르면 안 된다.** 서버는 UTC 로 주므로 `T` 뒤를 잘라 쓰면 KST 오후 3시가 오전 6시로
     * 보인다 — 채팅에서 실제로 났던 결함(QA 결함-3)과 같은 함정이고, 약속 시간에서는 **사람이
     * 9시간 전에 나가게** 만든다.
     */
    @Test
    fun `약속 시각은 기기 시간대로 바꿔 표기한다`() {
        assertEquals("오후 3:23", meetingTimeLabel("2026-11-06T06:23:00Z", kst))
        assertEquals("오전 6:23", meetingTimeLabel("2026-11-06T06:23:00Z", ZoneId.of("UTC")))
    }

    /** 0시·12시가 12 가 되어야 한다 — `hour % 12` 만 쓰면 자정이 「오전 0:05」로 찍힌다. */
    @Test
    fun `자정과 정오를 12시로 적는다`() {
        assertEquals("오전 12:05", meetingTimeLabel("2026-11-06T15:05:00Z", kst)) // KST 00:05
        assertEquals("오후 12:05", meetingTimeLabel("2026-11-06T03:05:00Z", kst)) // KST 12:05
    }

    @Test
    fun `읽을 수 없는 시각은 null 이다 - 그 자리를 비운다`() {
        for (broken in listOf(null, "", "   ", "오후 3시", "2026-11-06 15:00", "15:00")) {
            assertNull("departureAt=$broken", meetingTimeLabel(broken, kst))
        }
    }

    /** 헤드라인은 **시각을 지어내지 않는다** — 읽을 수 없으면 확정됐다는 사실만 말한다. */
    @Test
    fun `헤드라인은 시각을 못 읽으면 사실만 말한다`() {
        assertEquals("오후 3:23에 만나요", meetingHeadline("2026-11-06T06:23:00Z", kst))
        assertEquals("약속 시간이 정해졌어요", meetingHeadline(null, kst))
        assertEquals("약속 시간이 정해졌어요", meetingHeadline("오후 3시", kst))
    }

    @Test
    fun `확정 버튼은 시각을 못 읽으면 숫자를 빼고 적는다`() {
        assertEquals("이 시간으로 정하기", meetingConfirmButtonText(null))
    }

    // ─────────────────────── 방장 안내 ───────────────────────

    /**
     * 이름을 쓸 수 있으면 이름으로 부르고, 못 쓰면 **조건**을 말한다 — 서버 `Party.hostId()` 가
     * 현재 멤버 중 `min(joinedAt, memberId)` 라 「가장 먼저 참여한 분」은 사실이다
     * ([departHostNotice] 와 같은 근거·같은 표현 — 그쪽은 옛 `finishHostNotice` 를 옮겨 온 것이다).
     */
    @Test
    fun `방장 안내는 이름을 쓸 수 있을 때만 이름으로 부른다`() {
        assertEquals(
            "방장 해운대님이 약속 시간을 정해요. 정해지면 여기에 보여요",
            meetingHostNotice("해운대"),
        )
        assertEquals(
            "방장(가장 먼저 참여한 분)이 약속 시간을 정해요. 정해지면 여기에 보여요",
            meetingHostNotice(null),
        )
        assertEquals(meetingHostNotice(null), meetingHostNotice("  "))
    }

    /** 비활성 버튼을 두지 않는 자리의 안내문이므로 **다음에 무엇이 일어나는지**를 말해야 한다. */
    @Test
    fun `방장 안내는 정해지면 어떻게 되는지 말한다`() {
        listOf(meetingHostNotice("해운대"), meetingHostNotice(null)).forEach { message ->
            assertTrue(message, message.contains("정해지면"))
        }
    }

    // ─────────────────────── 출발 확인 문구 ───────────────────────

    /**
     * 🔴 ★ **이 테스트가 이 파일에서 가장 중요하고, 뜻이 바뀐 자리다.**
     *
     * 옛 문구: 「… 아직 도착하지 않은 2명은 **노쇼로 기록돼요** — 약속 시간부터 노쇼 횟수 × 24시간
     * 동안 방·예약을 만들거나 참여할 수 없어요」. **서버가 노쇼 기능을 통째로 지웠다**
     * (`b16551b7`, V37 이 테이블까지 떨어뜨린다) — 그 제재는 **존재하지 않는 일**이라, 남겨 두면
     * 겁을 주고 그 겁이 거짓이다.
     *
     * **대가가 사라진 건 아니다.** 서버는 「도착을 누른 멤버 = 탑승자」로 세므로(`Party.riderIds`)
     * 미도착자는 **탑승 기록 · 탑승 횟수 · 택시비 정산 대상**에서 함께 빠진다. 같이 탔는데도 정산
     * 장부에 이름이 없는 것이 지금의 대가다. 그래서 **겁주지 않고 사실만** 적는다 — 처벌조
     * (「제외됩니다」)가 아니라 「빠져요」로.
     *
     * 「출발」이 되돌릴 수 없고 누르는 사람이 보통 **남의 손해**를 만든다는 것은 그대로다. 그래서
     * 숫자는 여전히 필수다.
     */
    @Test
    fun `출발 확인 문구는 빠지는 인원과 무엇에서 빠지는지를 말한다`() {
        val message = departConfirmMessage(2)

        assertEquals(
            "같이 탄 사람 모두의 방이 끝나요. 아직 도착하지 않은 2명은 탑승 기록과 택시비 정산에서 빠져요",
            message,
        )
        assertTrue("몇 명이 빠지는지 말해야 한다", message.contains("2명"))
        assertTrue("탑승 기록에서 빠진다는 것을 말해야 한다", message.contains("탑승 기록"))
        assertTrue("정산에서 빠지는 것이 실제 대가다", message.contains("정산"))
        assertTrue("모두의 방이 끝난다는 것을 말해야 한다", message.contains("모두의 방이 끝나요"))
        // 🔴 사라진 제재로 겁을 주지 않는다
        assertFalse("서버에 없는 제재를 말한다: $message", message.contains("노쇼"))
        assertFalse("서버에 없는 제재 기간을 말한다: $message", message.contains("24시간"))
    }

    /**
     * **아무도 빠지지 않으면 그 문장을 말하지 않는다.** 겁만 주고, 정말 미도착자가 있을 때 같은
     * 문구가 무게를 잃는다.
     */
    @Test
    fun `모두 도착했으면 빠진다는 말을 하지 않는다`() {
        val message = departConfirmMessage(0)

        assertEquals("모두 도착했어요. 출발하면 같이 탄 사람 모두의 방이 끝나요", message)
        assertFalse("아무도 빠지지 않는데 빠진다고 말한다: $message", message.contains("빠져요"))
        assertFalse(message, message.contains("노쇼"))
    }

    /** 음수는 올 수 없지만(목록 개수다) 와도 「0명이 빠진다」를 그리지 않는다. */
    @Test
    fun `미도착 인원이 음수여도 빠진다는 말을 하지 않는다`() {
        assertEquals(departConfirmMessage(0), departConfirmMessage(-1))
    }

    // ─────────────────────── 도착 집계 표기 ───────────────────────

    @Test
    fun `도착 숫자는 확정된 방에서만 뜬다`() {
        val members = listOf(
            member("me", isMe = true, arrived = true),
            member("u2", isHost = true),
            member("u3"),
        )

        assertEquals("도착 1/3", arrivalCountLabel(ride(members)))
        assertNull(arrivalCountLabel(ride(members, isConfirmed = false)))
    }

    /** 멤버가 없는 응답(목록·생성 응답으로 만든 Ride)에서는 셀 것이 없다 — 「도착 0/0」을 그리지 않는다. */
    @Test
    fun `멤버가 없으면 도착 숫자를 그리지 않는다`() {
        assertNull(arrivalCountLabel(ride(emptyList())))
    }
}
