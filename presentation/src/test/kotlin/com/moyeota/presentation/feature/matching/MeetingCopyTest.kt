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
        status = RideStatus.MATCHED,
        isConfirmed = isConfirmed,
    )

    // ─────────────────────── 프리셋 ───────────────────────

    /** 리더가 정한 다섯 개. 늘리거나 줄이면 서버 한도·문구와 함께 다시 봐야 한다. */
    @Test
    fun `프리셋은 10 20 30 45 60분이다`() {
        assertEquals(listOf(10, 20, 30, 45, 60), MEETING_PRESET_MINUTES)
    }

    /**
     * **어느 프리셋도 서버의 「1시간 안」을 넘지 않는다.** 60분 프리셋이 경계에 걸려 있어
     * 분 단위 내림이 돌려주는 여유(최대 59초)가 실제로 필요하다 — 기기 시계가 서버보다 앞서 있으면
     * 그 여유가 400 을 막아 준다.
     */
    @Test
    fun `모든 프리셋은 지금부터 1시간 안이다`() {
        val limit = now.plusSeconds(3600)
        MEETING_PRESET_MINUTES.forEach { minutes ->
            val at = meetingPresetAt(now, minutes)
            assertTrue("${minutes}분 프리셋이 지금보다 과거다", at.isAfter(now))
            assertTrue("${minutes}분 프리셋이 1시간을 넘는다: $at", !at.isAfter(limit))
        }
    }

    /**
     * **분 단위로 내림한다** — 표시(「오후 3:23」)와 실제 경계가 어긋나지 않게. 06:13:47 + 10분은
     * 06:23:47 이지만 보내는 값은 06:23:00 이다.
     *
     * 내림해도 지난 시각이 될 수 없다(최소 프리셋이 10분이다) — 위 테스트가 그것도 함께 본다.
     */
    @Test
    fun `프리셋은 분 단위로 내림한다`() {
        assertEquals(Instant.parse("2026-11-06T06:23:00Z"), meetingPresetAt(now, 10))
        assertEquals(Instant.parse("2026-11-06T07:13:00Z"), meetingPresetAt(now, 60))
    }

    /**
     * **보여 준 시각이 그대로 보내진다** — 칩을 누르는 순간 한 번 만들어 [ConfirmingPreset] 에 담고,
     * 다이얼로그 제목도 서버 요청도 그 값을 쓴다.
     *
     * 왜 테스트가 필요한가: 예전에는 제목과 요청이 시계를 **따로** 읽었다. 분으로 내림하는 값이라
     * 「찰나뿐」이라고 적혀 있었지만, 다이얼로그를 몇 초 들여다보면 분은 쉽게 넘어간다 —
     * 실측(2026-11-06)에서 「오전 10:59에 만날까요?」로 묻고 **11:00 으로 확정됐다.** 확정은
     * 되돌릴 수 없고 어기면 노쇼가 되므로, 동의한 값과 보내는 값이 갈리면 안 된다.
     *
     * 아래 첫 단언이 그 갈림이 **실재함**을 고정하고(13초 뒤에 다시 만들면 값이 다르다),
     * 둘째가 캡처하면 그 갈림이 **사라짐**을 고정한다.
     */
    @Test
    fun `약속 시각은 칩을 누른 순간 한 번 만들어 제목과 요청이 함께 쓴다`() {
        // 06:13:47 + 10분 = 06:23:xx → 내림 06:23. 13초 뒤에 다시 만들면 06:24 다 — 옛 버그의 씨앗.
        val later = now.plusSeconds(13)
        assertEquals(Instant.parse("2026-11-06T06:24:00Z"), meetingPresetAt(later, 10))

        // 캡처하면 제출이 언제든 값은 하나다.
        val picked = ConfirmingPreset(minutes = 10, departureAt = meetingPresetIso(now, 10))

        assertEquals(meetingPresetIso(now, 10), picked.departureAt)
        assertEquals(
            "제목이 읽는 값과 서버로 가는 값은 같아야 한다",
            meetingTimeLabel(picked.departureAt),
            meetingTimeLabel(meetingPresetIso(now, 10)),
        )
        // 분이 넘어간 뒤에도 들고 있던 값은 그대로다.
        assertEquals(meetingPresetIso(now, 10), picked.departureAt)
    }

    /** 서버에 나가는 값은 ISO-8601 UTC 다. 초가 0 이면 `Instant.toString` 이 생략한다. */
    @Test
    fun `프리셋은 ISO-8601 문자열로 나간다`() {
        assertEquals("2026-11-06T06:23:00Z", meetingPresetIso(now, 10))
        // 왕복: 보낸 문자열을 그대로 다시 읽어도 같은 시각이다(화면이 이 값을 되읽어 표기한다)
        assertEquals(meetingPresetAt(now, 30), Instant.parse(meetingPresetIso(now, 30)))
    }

    /**
     * **보낸 값과 보여 준 값이 같다.** 다이얼로그 제목이 말하는 시각은 실제로 확정되는 시각이어야
     * 한다 — 이 왕복이 깨지면 「오후 3:23 로 정할까요?」가 거짓이 된다.
     */
    @Test
    fun `다이얼로그가 말하는 시각이 실제로 보내는 시각이다`() {
        val iso = meetingPresetIso(now, 10)

        assertEquals("오후 3:23", meetingTimeLabel(iso, kst))
        assertEquals("오후 3:23에 만날까요?", meetingConfirmTitle(meetingTimeLabel(iso, kst)))
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
    fun `프리셋 확인 제목은 시각을 못 읽으면 숫자를 빼고 묻는다`() {
        assertEquals("약속 시간을 정할까요?", meetingConfirmTitle(null))
    }

    // ─────────────────────── 방장 안내 ───────────────────────

    /**
     * 이름을 쓸 수 있으면 이름으로 부르고, 못 쓰면 **조건**을 말한다 — 서버 `Party.hostId()` 가
     * 현재 멤버 중 `min(joinedAt, memberId)` 라 「가장 먼저 참여한 분」은 사실이다
     * ([finishHostNotice] 와 같은 근거·같은 표현).
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
     * ★ **이 테스트가 이 파일에서 가장 중요하다.** 「출발」은 되돌릴 수 없고, 그 순간 도착하지 않은
     * 사람에게 **실제 제재**가 붙는다(약속 시간부터 노쇼 횟수 × 24시간 동안 방 생성·참여 금지).
     * 누르는 사람은 보통 **남의 제재**를 만들게 되므로, 숫자와 대가를 말하지 않으면 그 무게를 모른다.
     */
    @Test
    fun `출발 확인 문구는 노쇼 인원과 그 대가를 말한다`() {
        val message = departConfirmMessage(2)

        assertEquals(
            "같이 탄 사람 모두의 방이 끝나요. 아직 도착하지 않은 2명은 노쇼로 기록돼요 — " +
                "약속 시간부터 노쇼 횟수 × 24시간 동안 방·예약을 만들거나 참여할 수 없어요",
            message,
        )
        assertTrue("몇 명이 노쇼가 되는지 말해야 한다", message.contains("2명"))
        assertTrue("노쇼라는 말이 있어야 한다", message.contains("노쇼"))
        assertTrue("제재 기간을 말해야 한다", message.contains("24시간"))
        assertTrue("모두의 방이 끝난다는 것을 말해야 한다", message.contains("모두의 방이 끝나요"))
    }

    /**
     * **아무도 기록되지 않으면 제재를 말하지 않는다.** 겁만 주고, 정말 노쇼가 생길 때 같은 문구가
     * 무게를 잃는다.
     */
    @Test
    fun `모두 도착했으면 노쇼를 말하지 않는다`() {
        val message = departConfirmMessage(0)

        assertEquals("모두 도착했어요. 출발하면 같이 탄 사람 모두의 방이 끝나요", message)
        assertFalse("없는 제재를 말한다: $message", message.contains("노쇼"))
        assertFalse(message, message.contains("24시간"))
    }

    /** 음수는 올 수 없지만(목록 개수다) 와도 「0명 노쇼」를 그리지 않는다. */
    @Test
    fun `미도착 인원이 음수여도 노쇼를 말하지 않는다`() {
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
