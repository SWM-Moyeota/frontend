package com.moyeota.presentation.feature.mypage

import com.moyeota.domain.model.NoShowRecord
import com.moyeota.domain.model.ObjectionStatus
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 38 노쇼 기록·이의 신청과 35 제재 배너의 판정 규칙.
 *
 * **여기서 테스트가 꼭 필요한 이유 — 틀려도 화면에 보이지 않는다.**
 * 이 파일이 지키는 1급 규칙은 「`objectionStatus == null && !voided` 인 줄에만 이의 신청 버튼을
 * 보여 준다」다. 한 칸 잘못 잡은 화면은 **스크린샷으로 구분할 수 없고**, 틀렸다는 사실은 사용자가
 * 그 버튼을 눌러 409 `NOT_NO_SHOW`·`ALREADY_OBJECTED` 를 받는 순간에만 드러난다 — 사유를 다 쓴
 * 뒤에. 눈으로 확인하려면 **무효 처리된 노쇼를 가진 계정**을 서버에 만들어야 한다(운영자 API 로만
 * 가능하다). 값으로 넣어 본다.
 *
 * 시계도 같은 이유다. 제재 배너의 「언제까지」는 `restrictedUntil` 이 **지금보다 뒤인가**로
 * 갈리는데, 24시간짜리 제한이라 그 경계를 실기로 밟으려면 하루를 기다려야 한다.
 */
class NoShowRecordsRulesTest {

    private val seoul: ZoneId = ZoneId.of("Asia/Seoul")

    /** 테스트 기준 시각 — 2026-11-06 00:00 KST. 어느 함수도 `Instant.now()` 를 안에서 부르지 않는다. */
    private val now: Instant = Instant.parse("2026-11-05T15:00:00Z")

    private fun record(
        partyId: Long = 1L,
        departure: String = "강남역",
        destination: String = "판교역",
        meetingAt: String? = "2026-11-05T07:15:00Z",
        noShowAt: String? = "2026-11-05T07:20:00Z",
        isVoided: Boolean = false,
        objectionStatus: ObjectionStatus? = null,
    ) = NoShowRecord(
        partyId = partyId,
        departure = departure,
        destination = destination,
        meetingAt = meetingAt,
        noShowAt = noShowAt,
        isVoided = isVoided,
        objectionStatus = objectionStatus,
    )

    // ─────────────────────────── 줄 상태 ───────────────────────────

    /**
     * ★ 이 저장소에서 가장 비싼 한 줄. 서버가 201 을 주는 조건과 **한 글자도 달라서는 안 된다.**
     */
    @Test
    fun `이의를 낸 적 없는 살아 있는 노쇼만 이의 신청을 허용한다`() {
        assertEquals(
            NoShowRowState.OPEN,
            noShowRowState(isVoided = false, objectionStatus = null),
        )
        assertTrue(noShowRowState(isVoided = false, objectionStatus = null).allowsObjection)
    }

    @Test
    fun `이미 이의를 낸 줄에는 버튼이 없다`() {
        listOf(ObjectionStatus.PENDING, ObjectionStatus.ACCEPTED, ObjectionStatus.REJECTED)
            .forEach { status ->
                val state = noShowRowState(isVoided = false, objectionStatus = status)
                assertFalse("$status 에 이의 신청 버튼이 생겼다", state.allowsObjection)
            }
    }

    /**
     * **`voided` 가 `objectionStatus` 를 이긴다.** 운영자가 이의 없이 무효로 돌린 기록
     * (`voided && objectionStatus == null`)이 실제로 올 수 있고, 거기에 버튼을 달면 409
     * `NOT_NO_SHOW` 가 확정이다(서버는 「노쇼가 아니거나 이미 무효」를 같은 코드로 준다).
     */
    @Test
    fun `무효가 된 노쇼에는 어떤 조합에서도 버튼이 없다`() {
        listOf(null, ObjectionStatus.PENDING, ObjectionStatus.ACCEPTED, ObjectionStatus.REJECTED)
            .forEach { status ->
                val state = noShowRowState(isVoided = true, objectionStatus = status)
                assertEquals("무효인데 상태가 $status 라고 갈렸다", NoShowRowState.VOIDED, state)
                assertFalse(state.allowsObjection)
            }
    }

    @Test
    fun `이의 상태가 그대로 줄 상태가 된다`() {
        assertEquals(
            NoShowRowState.PENDING,
            noShowRowState(isVoided = false, objectionStatus = ObjectionStatus.PENDING),
        )
        assertEquals(
            NoShowRowState.ACCEPTED,
            noShowRowState(isVoided = false, objectionStatus = ObjectionStatus.ACCEPTED),
        )
        assertEquals(
            NoShowRowState.REJECTED,
            noShowRowState(isVoided = false, objectionStatus = ObjectionStatus.REJECTED),
        )
    }

    // ─────────────────────────── 배지·부연 문구 ───────────────────────────

    /**
     * 다섯 상태가 **서로 구분돼야** 한다. 같은 글자가 두 상태에 쓰이면 상태를 가른 의미가 없다.
     * [NoShowRowState.OPEN] 만 배지가 없고, 그 줄을 가르는 것은 이의 신청 버튼이다.
     */
    @Test
    fun `상태마다 다른 배지 글자를 쓰고 이의 전에는 배지가 없다`() {
        assertNull("이의를 내기 전에는 말할 상태가 없다", noShowBadgeText(NoShowRowState.OPEN))

        val badges = NoShowRowState.entries
            .filter { it != NoShowRowState.OPEN }
            .map { noShowBadgeText(it) }
        assertTrue("배지가 빈 상태가 있다: $badges", badges.all { !it.isNullOrBlank() })
        assertEquals("같은 배지 글자가 두 상태에 쓰였다: $badges", badges.size, badges.distinct().size)
    }

    /**
     * ★ **검토 중 문구가 이 화면에서 가장 위험한 자리다.**
     *
     * 이의 결과를 알리는 푸시가 **서버에 없다.** 사용자가 결과를 아는 길은 이 화면을 다시 여는 것
     * 하나뿐이라, 「알려 드려요」·「며칠 안에」·「풀어 드려요」는 전부 지킬 수 없는 말이 된다.
     * 이 저장소는 그런 문구 19건을 들어낸 이력이 있다(`ProductClaimsTest` KDoc).
     */
    @Test
    fun `검토 중 문구는 알림도 시한도 결과도 약속하지 않는다`() {
        val note = noShowNoteText(NoShowRowState.PENDING)!!

        // 사용자가 실제로 할 수 있는 일을 말해야 한다 — 이 화면을 다시 보는 것.
        assertTrue(note, note.contains("이 화면"))
        listOf(
            "알려 드려요", "알림", "푸시",
            "시간 안에", "시간 내", "분 안에", "일 안에", "영업일",
            "곧", "빠르게", "즉시",
            "풀어 드려요", "해제해", "인정돼요",
        ).forEach { forbidden ->
            assertFalse("지킬 수 없는 약속이다: $note ($forbidden)", note.contains(forbidden))
        }
    }

    /**
     * 무효 문구는 **횟수에서 빠졌다**까지만 말한다. 「제한이 풀렸어요」는 거짓일 수 있다 —
     * 다른 노쇼가 남아 있으면 제한은 그대로다. 지금 제한 여부는 35 배너가 서버 값으로만 말한다.
     */
    @Test
    fun `무효 문구는 제한 해제를 말하지 않는다`() {
        val note = noShowNoteText(NoShowRowState.VOIDED)!!

        assertTrue(note, note.contains("횟수"))
        listOf("제한이 풀", "이용할 수 있어요", "해제").forEach { forbidden ->
            assertFalse("한 건의 무효가 제한 해제는 아니다: $note", note.contains(forbidden))
        }
    }

    /** 거절 뒤에 할 수 있는 일이 없다는 것을 말해 줘야 한다 — 빼면 같은 방의 버튼을 계속 찾는다. */
    @Test
    fun `거절 문구는 다시 낼 수 없다고 말한다`() {
        val note = noShowNoteText(NoShowRowState.REJECTED)!!

        assertTrue(note, note.contains("다시 이의를 낼 수 없"))
    }

    /** 버튼이 바로 아래 있는 줄에는 설명을 두지 않는다 — 같은 말을 두 번 하게 된다. */
    @Test
    fun `이의를 낼 수 있는 줄에는 부연이 없다`() {
        assertNull(noShowNoteText(NoShowRowState.OPEN))
    }

    // ─────────────────────────── 카드 한 장 ───────────────────────────

    /**
     * 서버가 UTC 로 주는 시각을 **기기 시간대로** 바꿔 쓴다. 문자열을 자르면 KST 오후 4시가
     * 오전 7시로 보인다(채팅에서 실제로 났던 결함). 34 지난 탑승 기록과 같은 함수를 쓴다.
     */
    @Test
    fun `두 시각을 기기 시간대로 바꿔 적는다`() {
        val item = record().toNoShowItem(now = now, zone = seoul)

        assertEquals("강남역 → 판교역", item.routeLabel)
        assertEquals("11월 5일 오후 4:15", item.meetingLabel)
        assertEquals("11월 5일 오후 4:20", item.markedLabel)
        assertTrue(item.allowsObjection)
        assertNull(item.badgeText)
    }

    /** 파싱할 수 없거나 아예 없는 시각은 null — **틀린 날짜보다 없는 편이 낫다**(34 와 같은 규칙). */
    @Test
    fun `시각을 모르면 그 줄을 비운다`() {
        val missing = record(meetingAt = null, noShowAt = null).toNoShowItem(now, seoul)
        assertNull(missing.meetingLabel)
        assertNull(missing.markedLabel)

        val broken = record(meetingAt = "2026-11-05", noShowAt = "어제").toNoShowItem(now, seoul)
        assertNull(broken.meetingLabel)
        assertNull(broken.markedLabel)
    }

    /** 한쪽만 있으면 있는 쪽만, 둘 다 없으면 null — 빈 화살표를 그리지 않는다. */
    @Test
    fun `경로는 있는 쪽만 적는다`() {
        assertEquals("강남역", record(destination = " ").toNoShowItem(now, seoul).routeLabel)
        assertEquals("판교역", record(departure = "").toNoShowItem(now, seoul).routeLabel)
        assertNull(record(departure = "", destination = "").toNoShowItem(now, seoul).routeLabel)
    }

    /** 카드가 들고 있는 상태·배지·부연이 판정 함수와 어긋나지 않는다(화면이 다시 판정하지 않는다). */
    @Test
    fun `카드는 같은 판정을 그대로 들고 있다`() {
        val voided = record(isVoided = true, objectionStatus = ObjectionStatus.ACCEPTED)
            .toNoShowItem(now, seoul)

        assertEquals(NoShowRowState.VOIDED, voided.state)
        assertEquals(noShowBadgeText(NoShowRowState.VOIDED), voided.badgeText)
        assertEquals(noShowNoteText(NoShowRowState.VOIDED), voided.note)
        assertFalse(voided.allowsObjection)
    }

    // ─────────────────────────── 35 제재 배너 ───────────────────────────

    @Test
    fun `노쇼가 없고 제한도 없으면 배너를 그리지 않는다`() {
        assertNull(noShowSanction(noShowCount = 0, restrictedUntil = null, now = now, zone = seoul))
        assertNull(noShowSanction(noShowCount = 0, restrictedUntil = "", now = now, zone = seoul))
    }

    /** 제한 중 — **횟수와 기한을 함께** 말하고, 이의를 낼 수 있는 길을 가리킨다. */
    @Test
    fun `제한 중이면 기한과 횟수를 말한다`() {
        val sanction = noShowSanction(
            noShowCount = 2,
            restrictedUntil = now.plus(20, ChronoUnit.HOURS).toString(),
            now = now,
            zone = seoul,
        )!!

        assertEquals(NoShowSanctionKind.RESTRICTED, sanction.kind)
        assertTrue(sanction.text, sanction.text.contains("노쇼 2건"))
        assertTrue(sanction.text, sanction.text.contains("11월 6일 오후 8:00"))
        assertTrue(sanction.text, sanction.text.contains("방·예약을 만들거나 참여할 수 없어요"))
        // 막힌 사람이 다음에 갈 곳 — 35 에 있는 행 이름을 그대로 인용한다.
        assertTrue(sanction.text, sanction.text.contains("「노쇼 기록」"))
    }

    /**
     * **이미 지난 `restrictedUntil` 을 「지금 막혀 있어요」로 말하지 않는다.**
     *
     * 서버는 제한이 없으면 null 을 주지만 응답은 몇 분 전 값일 수 있고, 24시간짜리 제한은 그 사이에
     * 끝난다. null 여부만 보면 실제로는 되는 방 만들기를 사용자가 시도조차 하지 않는다.
     */
    @Test
    fun `기한이 지났으면 제한으로 말하지 않는다`() {
        val sanction = noShowSanction(
            noShowCount = 1,
            restrictedUntil = now.minusSeconds(60).toString(),
            now = now,
            zone = seoul,
        )!!

        assertEquals(NoShowSanctionKind.COUNTED, sanction.kind)
        assertFalse(sanction.text, sanction.text.contains("참여할 수 없어요"))
    }

    /** 제한이 풀렸고 횟수만 남은 상태 — 30일 만료를 말한다(제한 기간과 다른 수다). */
    @Test
    fun `횟수만 남으면 30일 만료를 말한다`() {
        val sanction = noShowSanction(noShowCount = 1, restrictedUntil = null, now = now, zone = seoul)!!

        assertEquals(NoShowSanctionKind.COUNTED, sanction.kind)
        assertTrue(sanction.text, sanction.text.contains("노쇼 1건"))
        assertTrue(sanction.text, sanction.text.contains("30일"))
    }

    /**
     * 기한을 **읽을 수 없을 때도 제한 자체는 숨기지 않는다.** 값이 비어 있지 않다는 건 서버가
     * 제한을 걸었다는 뜻이다 — 「제한 없음」으로 접으면 막힌 이유를 설명할 유일한 배너가 사라진다.
     * 대신 **틀린 시각을 쓰지 않는다**(「까지」가 없어야 한다).
     */
    @Test
    fun `기한을 못 읽어도 제한은 말하고 시각은 지어내지 않는다`() {
        val sanction = noShowSanction(
            noShowCount = 1,
            restrictedUntil = "2026년 11월 7일",
            now = now,
            zone = seoul,
        )!!

        assertEquals(NoShowSanctionKind.RESTRICTED, sanction.kind)
        assertTrue(sanction.text, sanction.text.contains("지금은"))
        assertFalse("읽지 못한 기한을 적었다: ${sanction.text}", sanction.text.contains("까지"))
    }

    /** 「노쇼 0건으로 제한됐어요」는 그 자체로 모순이다 — 횟수는 1 이상일 때만 문구에 넣는다. */
    @Test
    fun `횟수가 0 이면 제한 문구에 횟수를 넣지 않는다`() {
        val sanction = noShowSanction(
            noShowCount = 0,
            restrictedUntil = now.plus(3, ChronoUnit.HOURS).toString(),
            now = now,
            zone = seoul,
        )!!

        assertEquals(NoShowSanctionKind.RESTRICTED, sanction.kind)
        assertFalse(sanction.text, sanction.text.contains("노쇼 0"))
    }

    @Test
    fun `제한 여부 판정은 시각으로만 한다`() {
        assertTrue(isNoShowRestricted(now.plusSeconds(1).toString(), now))
        assertFalse(isNoShowRestricted(now.toString(), now))
        assertFalse(isNoShowRestricted(now.minusSeconds(1).toString(), now))
        assertFalse(isNoShowRestricted(null, now))
        assertFalse(isNoShowRestricted("  ", now))
        assertFalse("읽을 수 없는 값을 미래로 보지 않는다", isNoShowRestricted("내일", now))
    }

    /**
     * 제한 기한 표기는 **시·분을 절대 생략하지 않는다.** 34 의 날짜 표기는 다른 해의 기록에서
     * 시각을 떼는데, 여기서 그러면 연말에 걸린 제한이 「1월 1일」로만 보이고 그날 하루 종일 못
     * 쓰는 것처럼 읽힌다.
     */
    @Test
    fun `제한 기한은 해가 달라도 시각을 남긴다`() {
        val label = noShowRestrictionLabel("2027-01-01T01:30:00Z", now, seoul)

        assertEquals("2027년 1월 1일 오전 10:30", label)
    }

    /** 자정이 「오전 0:05」로 찍히지 않는다(`hour % 12` 만 쓰면 그렇게 된다). */
    @Test
    fun `자정과 정오를 12로 적는다`() {
        assertEquals("11월 6일 오전 12:05", noShowRestrictionLabel("2026-11-05T15:05:00Z", now, seoul))
        assertEquals("11월 6일 오후 12:05", noShowRestrictionLabel("2026-11-06T03:05:00Z", now, seoul))
    }

    @Test
    fun `읽을 수 없는 기한은 null 이다`() {
        assertNull(noShowRestrictionLabel("2026-11-07", now, seoul))
        assertNotNull("오프셋 표기는 읽어야 한다", noShowRestrictionLabel("2026-11-07T09:00:00+09:00", now, seoul))
    }

    // ─────────────────────────── 이의 사유 입력 ───────────────────────────

    /**
     * ★ **공백만 쓴 글은 보낼 수 없다.** 서버가 `reason.trim()` 을 1~500자로 검사하므로 trim 전
     * 길이로 재면 스페이스 세 개를 쓴 사용자에게 버튼이 열리고 400 `INVALID_REQUEST` 가 돌아온다 —
     * 앱이 막을 수 있었던 거절이고, 그 응답 문구로는 무엇을 고칠지 알 수 없다.
     */
    @Test
    fun `공백만 있는 사유는 보낼 수 없다`() {
        listOf("", " ", "   ", "\n", " \n\t ").forEach { blank ->
            val ui = objectionReasonUi(blank)
            assertFalse("공백만 있는 사유로 버튼이 열렸다: 「$blank」", ui.submitEnabled)
            // 잠긴 이유를 카운터가 말한다 — 0자다.
            assertEquals("0/500자", ui.counter)
        }
    }

    @Test
    fun `한 글자부터 보낼 수 있다`() {
        val ui = objectionReasonUi("늦")

        assertTrue(ui.submitEnabled)
        assertFalse(ui.overLimit)
        assertEquals("1/500자", ui.counter)
    }

    /** 서버 한도와 같은 경계여야 한다 — 500 은 통과, 501 은 막힌다. */
    @Test
    fun `500자는 보내고 501자는 막는다`() {
        val max = objectionReasonUi("가".repeat(OBJECTION_REASON_MAX))
        assertTrue(max.submitEnabled)
        assertFalse(max.overLimit)
        assertEquals("500/500자", max.counter)

        val over = objectionReasonUi("가".repeat(OBJECTION_REASON_MAX + 1))
        assertFalse(over.submitEnabled)
        assertTrue(over.overLimit)
    }

    /** 앞뒤 공백은 서버가 떼므로 길이에 세지 않는다 — 공백 때문에 501자가 되는 일이 없어야 한다. */
    @Test
    fun `앞뒤 공백은 길이에 세지 않는다`() {
        val ui = objectionReasonUi("  " + "가".repeat(OBJECTION_REASON_MAX) + "\n ")

        assertTrue("trim 하면 500자인데 막혔다", ui.submitEnabled)
        assertEquals("500/500자", ui.counter)
    }

    /**
     * 넘겼을 때 **몇 자를 줄여야 하는지**까지 적는다. 「500자를 넘었어요」만 적으면 517자를 쓴
     * 사람이 얼마를 지워야 하는지 직접 세야 한다.
     */
    @Test
    fun `한도를 넘기면 몇 자를 줄여야 하는지 말한다`() {
        val ui = objectionReasonUi("가".repeat(OBJECTION_REASON_MAX + 17))

        assertEquals("517/500자 — 17자 줄여야 보낼 수 있어요", ui.counter)
    }

    /** 보내는 중에는 두 번 보낼 수 없다 — 방마다 한 번뿐이라 두 번째는 409 다. */
    @Test
    fun `보내는 중에는 버튼이 잠긴다`() {
        assertFalse(objectionReasonUi("늦었어요", submitting = true).submitEnabled)
    }

    /** 입력창은 한도의 2배까지 받아 준다 — 500자에서 입력이 멈추는 칸은 고장으로 읽힌다. */
    @Test
    fun `입력창의 하드 리밋은 한도보다 넉넉하다`() {
        assertTrue(OBJECTION_REASON_HARD_LIMIT > OBJECTION_REASON_MAX)
    }

    // ─────────────────────────── 고정 문구 ───────────────────────────

    /**
     * 시트·성공 안내가 **운영 처리를 약속하지 않는다.** 이의는 사람이 보고, 결과 푸시는 없다.
     * 「접수됐습니다」로 끝내면 사용자는 오지 않는 알림을 기다린다 — 결과를 보는 길을 같은
     * 문장에서 알려 줘야 한다.
     */
    @Test
    fun `이의 안내 문구는 시한도 알림도 결과도 약속하지 않는다`() {
        val copy = listOf(
            OBJECTION_SHEET_TITLE,
            OBJECTION_SHEET_NOTICE,
            OBJECTION_SENT_TEXT,
            OBJECTION_INPUT_PLACEHOLDER,
        ).joinToString(" ")

        listOf(
            "알려 드려요", "알림", "푸시",
            "시간 안에", "시간 내", "분 안에", "일 안에", "영업일",
            "곧", "즉시", "빠르게",
            "풀어 드려요", "해제", "인정돼요", "취소돼요",
        ).forEach { forbidden ->
            assertFalse("지킬 수 없는 약속이다: $forbidden", copy.contains(forbidden))
        }

        // 서버 계약으로 참인 둘은 **남아야** 한다
        assertTrue(OBJECTION_SHEET_NOTICE, OBJECTION_SHEET_NOTICE.contains("한 번만"))
        assertTrue(OBJECTION_SENT_TEXT, OBJECTION_SENT_TEXT.contains("이 화면"))
    }

    /**
     * 제재 계산식은 **「약속 시간부터」**를 빼면 안 된다. 사용자가 노쇼로 찍힌 시각(= 누군가
     * 출발을 누른 때)부터 센다고 읽으면 자기 제한이 언제 풀리는지 계산이 어긋난다.
     */
    @Test
    fun `제재 계산식은 기준 시각과 24시간을 함께 말한다`() {
        assertTrue(NO_SHOW_RULE_TEXT, NO_SHOW_RULE_TEXT.contains("약속 시간부터"))
        assertTrue(NO_SHOW_RULE_TEXT, NO_SHOW_RULE_TEXT.contains("24시간"))
        // 24시간 제한과 30일 만료는 다른 수다 — 한 문장에 넣으면 「30일 동안 못 쓴다」로 읽힌다.
        assertFalse(NO_SHOW_RULE_TEXT, NO_SHOW_RULE_TEXT.contains("30일"))
        assertTrue(NO_SHOW_EXPIRY_TEXT, NO_SHOW_EXPIRY_TEXT.contains("30일"))
    }

    /**
     * 서버가 **최신 20건에서 자르고** 커서를 주지 않는다. 아무 말도 없으면 사용자는 그 목록이
     * 전부라고 읽는다 — 20건을 넘는 노쇼가 있는 사용자에게는 거짓이다. 「더 보기」는 달 수 없으니
     * (누를 수 없는 버튼이 된다) **꽉 찼을 때만** 사실을 적는다.
     */
    @Test
    fun `목록이 꽉 찼을 때만 전체가 아니라고 말한다`() {
        assertNull(noShowHistoryCapNotice(0))
        assertNull(noShowHistoryCapNotice(19))
        assertEquals("최근 20건만 볼 수 있어요", noShowHistoryCapNotice(20))
        // 서버가 상한을 넘겨 보내도 같은 말이 참이다.
        assertEquals("최근 20건만 볼 수 있어요", noShowHistoryCapNotice(21))
    }

    /** 기록이 없는 사용자에게 겪지도 않은 제재를 설명하지 않는다 — 「없어요」까지가 사실이다. */
    @Test
    fun `빈 목록 문구는 훈계하지 않는다`() {
        assertEquals("노쇼로 기록된 적이 없어요", NO_SHOW_EMPTY_TEXT)
        listOf("24시간", "제한", "주의").forEach { forbidden ->
            assertFalse(NO_SHOW_EMPTY_TEXT, NO_SHOW_EMPTY_TEXT.contains(forbidden))
        }
    }
}
