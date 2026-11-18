package com.moyeota.presentation.feature.faresplit

import com.moyeota.domain.model.FareSplitParticipant
import com.moyeota.domain.model.FareSplitStatus
import com.moyeota.domain.model.RideStatus
import com.moyeota.domain.model.TransferStatus
import com.moyeota.domain.model.WithdrawnMemberNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 정산 화면의 **표기와 하지 않는 약속들**.
 *
 * 이 테스트의 절반은 「무엇이 보이는가」가 아니라 **「무엇을 말하지 않는가」**다. 이 저장소는 거짓
 * 문구 19건을 들어낸 이력이 있고(`ProductClaimsTest`), 돈이 움직이는 화면에서 지어낸 사실은
 * 오해가 아니라 **되돌릴 수 없는 송금**으로 이어진다.
 */
class FareSplitDisplayTest {

    // ─────────────────── 금액·이름 ───────────────────

    @Test
    fun `금액은 세 자리마다 끊어 원을 붙인다`() {
        assertEquals("18,700원", fareWonLabel(18_700))
        assertEquals("0원", fareWonLabel(0))
    }

    @Test
    fun `이름이 비어 있으면 자리값을 쓴다`() {
        assertEquals(WithdrawnMemberNames.NO_SUMMARY, FareSplitParticipant("", "").displayName)
        assertEquals("방장", FareSplitParticipant(PAYER_ID, "방장").displayName)
        // 서버가 직접 넣은 「탈퇴한 사용자」는 **그대로 쓴다** — publicId 가 살아 있어 부를 수 있다.
        assertEquals(
            WithdrawnMemberNames.SERVER_DELETED,
            transfer(SENDER_A, WithdrawnMemberNames.SERVER_DELETED).displayName,
        )
    }

    // ─────────────────── 가린 실명 ───────────────────

    /** 쓸모가 화면에서 읽혀야 한다 — 은행 앱 입금 내역과 맞춰 보는 용도다. */
    @Test
    fun `가린 실명은 입금자명으로 보여 주고 그 쓸모를 적는다`() {
        assertEquals("입금자명 홍*동", depositorHintOrNull(transfer(maskedRealName = "홍*동")))
        assertTrue(DEPOSITOR_HINT_PURPOSE.contains("입금 내역"))
    }

    /** 보내는 사람에게는 **늘 null** 이고 그게 정상이다 — 「실명이 없는 사용자」가 아니다. */
    @Test
    fun `가린 실명이 없으면 입금자명 칸을 만들지 않는다`() {
        assertNull(depositorHintOrNull(transfer(maskedRealName = null)))
        assertNull(depositorHintOrNull(transfer(maskedRealName = "  ")))
    }

    // ─────────────────── 상태 표기 ───────────────────

    /**
     * COMPLETED 와 EXPIRED 를 **같은 톤·같은 문구로 뭉개지 않는다.** 뭉개면 만료된 정산을 본 사람이
     * 「정산이 끝났다」로 읽고 자기 몫을 영영 보내지 않는다 — 다시 시작하는 길은 없다.
     */
    @Test
    fun `정산 완료와 기간 지남은 글자도 톤도 다르다`() {
        val done = fareSplitBadge(FareSplitStatus.COMPLETED)
        val expired = fareSplitBadge(FareSplitStatus.EXPIRED)

        assertNotEquals(done.label, expired.label)
        assertNotEquals(done.tone, expired.tone)
        assertEquals(FareSplitTone.DONE, done.tone)
        assertEquals(FareSplitTone.WARN, expired.tone)
    }

    @Test
    fun `기간이 지난 정산을 끝났다고 말하지 않는다`() {
        val notice = fareSplitStatusNotice(FareSplitStatus.EXPIRED)!!

        assertFalse(notice.contains("정산이 끝났"))
        // 돈을 옮기는 길은 앱 밖에 있다 — 남은 수단을 가리킨다.
        assertTrue(notice.contains("채팅"))
    }

    @Test
    fun `진행 중인 정산에는 상태 설명을 붙이지 않는다`() {
        assertNull(fareSplitStatusNotice(FareSplitStatus.OPEN))
    }

    /**
     * ### 만료 경계는 「1일」이 아니라 「1일 + 최대 30분」이다 ★
     * 서버 스윕이 30분 cron 이라 24시간 1분이 지난 정산이 열려 있는 것도, 24시간 29분에 닫히는 것도
     * 정상이다. 분 단위 약속을 하면 **어느 모습도 화면과 맞지 않는다.**
     */
    @Test
    fun `만료 안내는 정확한 시각을 약속하지 않는다`() {
        assertTrue(EXPIRY_NOTICE.contains("하루"))
        assertTrue(EXPIRY_NOTICE.contains("늦을 수 있"))
        listOf("24시간", "분 뒤", "오늘까지", "자정", "정확히").forEach { forbidden ->
            assertFalse("만료 안내가 「$forbidden」을 약속하면 안 된다", EXPIRY_NOTICE.contains(forbidden))
        }
    }

    /** 서버가 아는 것은 **버튼이 눌렸다**는 사실뿐이다. 「입금됨」으로 적으면 은행 앱을 안 보고 확인한다. */
    @Test
    fun `보냈다고 알린 상태를 입금됨이라고 적지 않는다`() {
        val sent = transferStatusLabel(TransferStatus.SENT)

        assertEquals("보냈다고 알림", sent.label)
        assertFalse(sent.label.contains("입금"))
    }

    @Test
    fun `세 송금 상태의 글자가 서로 다르다`() {
        val labels = TransferStatus.entries.map { transferStatusLabel(it).label }

        assertEquals(labels.size, labels.toSet().size)
    }

    @Test
    fun `앱이 돈을 옮기지 않는다는 사실을 적는다`() {
        assertTrue(APP_DOES_NOT_MOVE_MONEY.contains("돈을 옮기지 않아요"))
    }

    // ─────────────────── 받는 사람 요약 ───────────────────

    @Test
    fun `받을 돈은 보내는 사람들의 합이고 내 부담은 빠져 있다`() {
        val summary = payerSummaryOf(detail())

        assertEquals(18_700, summary.totalFare)
        assertEquals(6_234, summary.myAmount)
        assertEquals(12_466, summary.toReceive)
    }

    /** SENT 는 **아직 못 받은 돈**이다 — 눌렸다는 사실이 입금을 뜻하지 않는다. */
    @Test
    fun `보냈다고 알린 금액도 아직 못 받은 돈으로 센다`() {
        val summary = payerSummaryOf(
            detail(
                transfers = listOf(
                    transfer(SENDER_A, amount = 6_233, status = TransferStatus.SENT),
                    transfer(SENDER_B, amount = 6_233, status = TransferStatus.CONFIRMED),
                ),
            ),
        )

        assertEquals(6_233, summary.outstanding)
        assertEquals(1, summary.confirmedCount)
        assertEquals("2명 중 1명 확인", confirmedProgressLabel(summary))
    }

    /**
     * ### 금액은 1/N 이 아니다 ★
     * 방장이 사람마다 적은 값이라 **같은 방을 보는 두 사람이 다른 값을 본다**(백엔드 `fe6ba35a`).
     * 「1인당 N원」이라고 단정하는 문구가 이 화면에 들어오면 안 된다.
     */
    @Test
    fun `어느 문구도 1인당 금액이라고 단정하지 않는다`() {
        val everyCopy = buildList {
            add(APP_DOES_NOT_MOVE_MONEY)
            add(EXPIRY_NOTICE)
            add(DEPOSITOR_HINT_PURPOSE)
            add(TOSS_OPEN_FAILED_NOTICE)
            add(START_IRREVERSIBLE_NOTICE)
            addAll(FareSplitStatus.entries.map { fareSplitBadge(it).label })
            addAll(FareSplitStatus.entries.mapNotNull { fareSplitStatusNotice(it) })
            addAll(TransferStatus.entries.map { transferStatusLabel(it).label })
            addAll(SenderStage.entries.mapNotNull { senderStageNotice(it) })
            addAll(FareSplitAction.entries.map { fareSplitGenericFailure(it) })
            addAll(FareSplitSubmitBlock.entries.mapNotNull { submitBlockMessage(it) })
            add(confirmedProgressLabel(payerSummaryOf(detail())))
        }

        everyCopy.forEach { copy ->
            listOf("1인당", "1/N", "똑같이 나눠", "n분의 1").forEach { forbidden ->
                assertFalse("「$forbidden」이라고 단정하면 안 된다: $copy", copy.contains(forbidden))
            }
        }
    }

    /**
     * 멱등 204 는 「이미 그 상태였다」와 「지금 바뀌었다」를 구분할 수 없다 — 「이미 보냈습니다」류를
     * 지어내면 **틀릴 수 있다.** 어느 문구에도 없어야 한다.
     */
    @Test
    fun `이미 그 상태였다고 지어내는 문구가 없다`() {
        val everyCopy = buildList {
            addAll(SenderStage.entries.mapNotNull { senderStageNotice(it) })
            addAll(FareSplitStatus.entries.mapNotNull { fareSplitStatusNotice(it) })
            addAll(FareSplitAction.entries.map { fareSplitGenericFailure(it) })
            add(senderStageNotice(SenderStage.SENT_WAITING).orEmpty())
        }

        everyCopy.forEach { copy ->
            listOf("이미 보냈", "이미 알렸", "이미 확인했어요").forEach { forbidden ->
                assertFalse("「$forbidden」은 서버 응답으로 알 수 없는 사실이다: $copy", copy.contains(forbidden))
            }
        }
    }

    // ─────────────────── 보내는 사람 안내 ───────────────────

    @Test
    fun `보낼 단계에는 안내 대신 버튼이 말한다`() {
        assertNull(senderStageNotice(SenderStage.SEND_READY))
    }

    /** 닫힌 정산의 설명은 상태 문구가 **COMPLETED 와 EXPIRED 를 갈라서** 한다 — 여기서 또 적지 않는다. */
    @Test
    fun `닫힌 단계의 안내는 상태 문구에 맡긴다`() {
        assertNull(senderStageNotice(SenderStage.CLOSED))
    }

    @Test
    fun `막힌 단계는 위험 톤이다`() {
        assertEquals(FareSplitTone.DANGER, senderStageTone(SenderStage.ACCOUNT_LOST))
        assertEquals(FareSplitTone.DANGER, senderStageTone(SenderStage.UNKNOWN_MY_TRANSFER))
        assertEquals(FareSplitTone.DONE, senderStageTone(SenderStage.CONFIRMED))
    }

    /** 토스 실패는 「오류」가 아니라 **이미 아래에 있는 길**을 가리킨다(서버 주석의 근거 그대로). */
    @Test
    fun `토스 실패 안내는 복사 버튼을 가리킨다`() {
        assertTrue(TOSS_OPEN_FAILED_NOTICE.contains("복사"))
        assertTrue(TOSS_OPEN_FAILED_NOTICE.contains("은행 앱"))
    }

    // ─────────────────── 진입 판정 ───────────────────

    @Test
    fun `끝난 방에서 도착을 누른 사람만 정산 화면을 열 수 있다`() {
        assertTrue(canOpenFareSplit(ride()))
        // 아직 끝나지 않은 방
        assertFalse(canOpenFareSplit(ride(status = RideStatus.CONFIRMED)))
        // 도착을 누르지 않았다 = 탑승자가 아니다 → 상세는 403 NOT_FARE_SPLIT_PARTICIPANT 다
        assertFalse(
            canOpenFareSplit(
                ride(
                    members = listOf(
                        member(PAYER_ID, "방장", isHost = true),
                        member(SENDER_A, "나", isMe = true, arrived = false),
                    ),
                ),
            ),
        )
    }

    /** 방장이 누르는 버튼은 **만드는** 버튼이고 나머지가 누르는 버튼은 **보는** 버튼이다. */
    @Test
    fun `진입점 글자가 방장과 그 밖으로 갈린다`() {
        assertEquals("택시비 정산하기", fareSplitEntryLabel(ride()))

        val asSender = ride(
            members = listOf(
                member(PAYER_ID, "방장", isHost = true),
                member(SENDER_A, "나", isMe = true),
            ),
        )
        assertEquals("내 택시비 보내기", fareSplitEntryLabel(asSender))
    }

    @Test
    fun `갈 수 없으면 진입점을 그리지 않는다`() {
        assertNull(fareSplitEntryLabel(ride(status = RideStatus.CANCELED)))
    }

    // ─────────────────── 홈 배너 ───────────────────

    @Test
    fun `배너 경로는 있는 쪽만 적고 둘 다 없으면 줄을 만들지 않는다`() {
        assertEquals("센텀시티역 → 서면역", myFareSplitRouteLabel(myFareSplit()))
        assertEquals("센텀시티역", myFareSplitRouteLabel(myFareSplit(destination = null)))
        assertEquals("서면역", myFareSplitRouteLabel(myFareSplit(departure = null)))
        // 방이 지워지면 서버가 둘을 **함께** null 로 준다 — 자리값을 넣지 않는다.
        assertNull(myFareSplitRouteLabel(myFareSplit(departure = null, destination = null)))
        assertNull(myFareSplitRouteLabel(myFareSplit(departure = "  ", destination = "")))
    }

    @Test
    fun `잠긴 행에는 이유가 있고 열린 행에는 없다`() {
        val sent = transfer(status = TransferStatus.SENT)
        assertNull(transferActionsOf(detail(transfers = listOf(sent)), sent).lockReason)

        val anonymous = transfer(publicId = "", status = TransferStatus.SENT)
        assertNotNull(transferActionsOf(detail(transfers = listOf(anonymous)), anonymous).lockReason)
    }
}
