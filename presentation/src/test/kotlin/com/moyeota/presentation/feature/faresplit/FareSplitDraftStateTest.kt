package com.moyeota.presentation.feature.faresplit

import com.moyeota.domain.model.Bank
import com.moyeota.domain.model.FareSplitRole
import com.moyeota.domain.model.FareSplitStatus
import com.moyeota.domain.model.RideStatus
import com.moyeota.domain.model.TransferStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 정산 시작·수정 폼의 **1/N 채우기와 제출 잠금**.
 *
 * 서버는 틀린 요청을 400 하나로 답하고(`PAYMENTS_NOT_EQUAL_FARE`·`PAYMENT_TARGET_MISMATCH`) 그
 * 답은 **입력을 다 끝낸 뒤에** 온다. 그래서 잠금 규칙이 틀리면 ① 멀쩡해 보이는데 제출만 영구히
 * 실패하거나 ② 잠겨야 할 버튼이 열려 사용자가 400 을 스스로 만든다. **둘 다 스크린샷으로 구분되지
 * 않는다.**
 */
class FareSplitDraftStateTest {

    private fun startState() = (startDraftGate(ride()) as FareSplitDraftGate.Ready).state

    private fun filled() = startState()
        .withFare("18700")
        .withBank(Bank.KAKAO)
        .withAccount("3333-01-2345678")

    // ─────────────────── 열기 판정 ───────────────────

    @Test
    fun `끝난 방의 방장만 시작 폼을 연다`() {
        assertTrue(startDraftGate(ride()) is FareSplitDraftGate.Ready)
    }

    /** 「출발」을 누르면 풀리는 실패다 — **유일하게 사용자가 할 일이 있는** 쪽이다. */
    @Test
    fun `아직 출발하지 않은 방은 되돌릴 수 있는 막힘이다`() {
        val blocked = startDraftGate(ride(status = RideStatus.CONFIRMED)) as FareSplitDraftGate.Blocked

        assertTrue(blocked.recoverable)
        assertTrue(blocked.message.contains("출발"))
    }

    /** 도착은 끝난 방에서 되돌릴 수 없다 — 「다시 시도」를 권하면 거짓이 된다. */
    @Test
    fun `방장이 도착을 누르지 않았으면 영구히 정산할 수 없다고 말한다`() {
        val noArrival = ride(
            members = listOf(
                member(PAYER_ID, "방장", isMe = true, isHost = true, arrived = false),
                member(SENDER_A, "동승자A"),
            ),
        )
        val blocked = startDraftGate(noArrival) as FareSplitDraftGate.Blocked

        assertFalse(blocked.recoverable)
    }

    @Test
    fun `혼자 탔으면 나눌 상대가 없다고 말한다`() {
        val alone = ride(members = listOf(member(PAYER_ID, "방장", isMe = true, isHost = true)))
        val blocked = startDraftGate(alone) as FareSplitDraftGate.Blocked

        assertFalse(blocked.recoverable)
        assertTrue(blocked.message.contains("혼자"))
    }

    @Test
    fun `방장이 아니면 시작 폼을 열지 않는다`() {
        val notHost = ride(
            members = listOf(
                member(PAYER_ID, "방장", isHost = true),
                member(SENDER_A, "나", isMe = true),
            ),
        )

        assertTrue(startDraftGate(notHost) is FareSplitDraftGate.Blocked)
    }

    /**
     * 빼고 보내도 400, 넣고 보내도 400 이다(둘 다 `PAYMENT_TARGET_MISMATCH`) — 열어 봐야 제출할 수
     * 없는 폼이므로 열지 않는다.
     */
    @Test
    fun `서버가 설명하지 못한 탑승자가 있으면 폼을 열지 않는다`() {
        val anonymous = ride(
            members = listOf(
                member(PAYER_ID, "방장", isMe = true, isHost = true),
                member("", "탈퇴한 회원"),
                member(SENDER_B, "동승자B"),
            ),
        )

        assertTrue(startDraftGate(anonymous) is FareSplitDraftGate.Blocked)
    }

    /** 멤버 전원이 아니라 **도착을 누른 사람만** 대상이다 — 전원으로 채우면 400 이다. */
    @Test
    fun `정산 대상은 도착을 누른 멤버뿐이다`() {
        val mixed = ride(
            members = listOf(
                member(PAYER_ID, "방장", isMe = true, isHost = true),
                member(SENDER_A, "동승자A", arrived = true),
                member(SENDER_B, "노쇼", arrived = false),
            ),
        )
        val state = (startDraftGate(mixed) as FareSplitDraftGate.Ready).state

        assertEquals(listOf(PAYER_ID, SENDER_A), state.riders.map { it.publicId })
    }

    // ─────────────────── 1/N 채우기 ───────────────────

    /** Swagger 원문: 「1/N 로 미리 채우는 것은 앱이 한다」 — 택시비를 적는 순간이 그 자리다. */
    @Test
    fun `택시비를 적으면 1대N 로 미리 채워진다`() {
        val state = startState().withFare("18700")

        assertEquals("6234", state.amountInputs[PAYER_ID])
        assertEquals("6233", state.amountInputs[SENDER_A])
        assertEquals("6233", state.amountInputs[SENDER_B])
        assertEquals(18_700, state.amountSum)
        assertEquals(0, state.difference)
    }

    /** 나머지 1원은 **받는 사람이 흡수한다** — 보내는 금액이 전원 같아져 분쟁할 거리가 없다. */
    @Test
    fun `나누어떨어지지 않는 1원은 받는 사람이 부담하고 그 사실을 적는다`() {
        val state = startState().withFare("18700")

        assertEquals(6_234, state.amountInputs[PAYER_ID]!!.toInt())
        assertEquals(1, state.amountInputs[PAYER_ID]!!.toInt() - state.amountInputs[SENDER_A]!!.toInt())
        assertEquals("나누어떨어지지 않는 1원은 받는 분이 부담해요", evenSplitRemainderNotice(state))
    }

    @Test
    fun `나누어떨어지면 나머지 안내를 하지 않는다`() {
        assertNull(evenSplitRemainderNotice(startState().withFare("18000")))
    }

    /**
     * 사용자가 금액을 손댄 뒤에 택시비를 고쳐도 **그 조정이 사라지지 않는다.** 사라지면 사용자는
     * 자기가 지운 줄 모르고 저장하고, 그러면 다른 사람이 보는 금액이 바뀐다.
     */
    @Test
    fun `금액을 손대면 택시비를 고쳐도 1대N 로 덮지 않는다`() {
        val adjusted = startState().withFare("18700").withAmount(SENDER_A, "5000")
        val refared = adjusted.withFare("18000")

        assertEquals("5000", refared.amountInputs[SENDER_A])
        assertFalse(refared.autoSplits)
    }

    @Test
    fun `손대기 전에는 택시비를 고칠 때마다 다시 채워진다`() {
        val state = startState().withFare("18700").withFare("18000")

        assertEquals("6000", state.amountInputs[PAYER_ID])
        assertEquals(0, state.difference)
    }

    /** 명시적으로 누르면 되살아난다 — 자동 채우기까지 함께 켜진다. */
    @Test
    fun `똑같이 나누기를 누르면 조정을 덮고 자동 채우기가 되살아난다`() {
        val state = startState().withFare("18700").withAmount(SENDER_A, "1").splitEvenly()

        assertEquals("6233", state.amountInputs[SENDER_A])
        assertTrue(state.autoSplits)
    }

    /** 사용자가 「2」를 넣을 수 있다 — 그때 터지지 않고 그대로 남는다(그 상태는 버튼이 잠긴다). */
    @Test
    fun `택시비가 인원보다 적으면 나누지 않고 그대로 둔다`() {
        val state = startState().withFare("2")

        assertFalse(state.canSplitEvenly())
        assertTrue(state.amountInputs.isEmpty())
        assertEquals(FareSplitSubmitBlock.AMOUNT_EMPTY, submitBlockOf(state.withBank(Bank.KB).withAccount("3333012345678")))
    }

    // ─────────────────── 입력 정제 ───────────────────

    @Test
    fun `택시비 칸은 숫자만 받고 앞의 0 을 버린다`() {
        assertEquals("18700", startState().withFare("1a8,700원").fareInput)
        assertEquals("18700", startState().withFare("0018700").fareInput)
        assertEquals("", startState().withFare("000").fareInput)
    }

    /**
     * 자릿수 상한이 없으면 `Int` 가 넘쳐 합이 **음수로 감긴다** — 그러면 「합계가 택시비와 같다」가
     * 거짓으로 참이 되는 조합이 생긴다.
     */
    @Test
    fun `금액 칸은 자릿수를 잘라 Int 가 넘치지 않게 한다`() {
        assertEquals(MAX_FARE_DIGITS, startState().withFare("99999999999999").fareInput.length)
        val state = startState().withAmount(SENDER_A, "99999999999999")
        assertEquals(MAX_AMOUNT_DIGITS, state.amountInputs[SENDER_A]!!.length)
        assertNotNull(state.amountInputs[SENDER_A]!!.toIntOrNull())
    }

    /**
     * 하이픈·공백은 **지우지 않는다** — 은행 앱이 복사해 주는 모양을 그대로 붙여넣게 하고, 떼는 일은
     * 매퍼가 보내기 직전에 한다. 숫자 아닌 **다른** 문자는 애초에 받지 않는다(조용히 고친 계좌번호로
     * 돈이 움직이면 되돌릴 수 없다).
     */
    @Test
    fun `계좌번호 칸은 하이픈과 공백을 남기고 다른 문자는 받지 않는다`() {
        assertEquals("3333-01-2345678", startState().withAccount("3333-01-2345678").accountInput)
        assertEquals("3333 01 2345678", startState().withAccount("3333 01 2345678").accountInput)
        assertEquals("333301 2345678", startState().withAccount("3333(국민)01 2345678").accountInput)
    }

    @Test
    fun `계좌번호가 자릿수를 못 맞추면 칸 아래에 이유를 적는다`() {
        assertNull("빈 칸을 틀렸다고 말하지 않는다", accountErrorText(startState()))
        assertNotNull(accountErrorText(startState().withAccount("333301")))
        assertNull(accountErrorText(startState().withAccount("3333-01-2345678")))
    }

    // ─────────────────── 제출 잠금 ───────────────────

    @Test
    fun `택시비 은행 계좌 금액이 모두 맞으면 보낼 수 있다`() {
        assertNull(submitBlockOf(filled()))
    }

    /** 폼의 위에서 아래 순서로 **다음에 할 일 하나**를 가리킨다. */
    @Test
    fun `잠금 사유는 폼 순서대로 하나만 알려 준다`() {
        val empty = startState()
        assertEquals(FareSplitSubmitBlock.FARE_EMPTY, submitBlockOf(empty))

        val fared = empty.withFare("18700")
        assertEquals(FareSplitSubmitBlock.BANK_NOT_SELECTED, submitBlockOf(fared))

        val banked = fared.withBank(Bank.KAKAO)
        assertEquals(FareSplitSubmitBlock.ACCOUNT_EMPTY, submitBlockOf(banked))

        val shortAccount = banked.withAccount("3333")
        assertEquals(FareSplitSubmitBlock.ACCOUNT_INVALID, submitBlockOf(shortAccount))

        assertNull(submitBlockOf(banked.withAccount("3333012345678")))
    }

    @Test
    fun `합이 택시비와 다르면 보낼 수 없다`() {
        val mismatched = filled().withAmount(SENDER_A, "1")

        assertEquals(FareSplitSubmitBlock.SUM_NOT_EQUAL_FARE, submitBlockOf(mismatched))
    }

    @Test
    fun `비어 있는 금액 칸이 하나라도 있으면 보낼 수 없다`() {
        val missing = filled().withAmount(SENDER_B, "")

        assertEquals(FareSplitSubmitBlock.AMOUNT_EMPTY, submitBlockOf(missing))
    }

    /** 0원을 적은 것과 **아직 안 적은 것**은 다르다 — 앞은 「1원 이상」을 말하고 뒤는 버튼만 잠근다. */
    @Test
    fun `0 원을 적으면 1원 이상이라고 말한다`() {
        // "0" 은 앞의 0 을 버려 빈 칸이 되므로, 1원 이상 미달을 만들려면 합을 어긋나게 둔다.
        val zeroish = filled().withAmount(SENDER_A, "0")
        assertEquals(FareSplitSubmitBlock.AMOUNT_EMPTY, submitBlockOf(zeroish))
    }

    /** 화면을 열자마자 빨간 띠가 떠 있으면 사용자는 아무 잘못도 없이 오류 화면을 본다. */
    @Test
    fun `아직 안 적은 칸에는 경고 문구를 띄우지 않는다`() {
        listOf(
            FareSplitSubmitBlock.FARE_EMPTY,
            FareSplitSubmitBlock.BANK_NOT_SELECTED,
            FareSplitSubmitBlock.ACCOUNT_EMPTY,
            FareSplitSubmitBlock.AMOUNT_EMPTY,
        ).forEach { block ->
            assertNull("$block", submitBlockMessage(block))
        }
        assertNull(submitBlockMessage(null))
    }

    /** 합계 어긋남은 금액 칸 **바로 아래 합계 줄**이 실시간으로 말한다 — 두 군데서 말하지 않는다. */
    @Test
    fun `합계 어긋남은 아래 경고가 아니라 합계 줄이 말한다`() {
        assertNull(submitBlockMessage(FareSplitSubmitBlock.SUM_NOT_EQUAL_FARE))
        assertNotNull(differenceNotice(filled().withAmount(SENDER_A, "1")))
    }

    @Test
    fun `적었는데 틀린 경우에는 문구를 띄운다`() {
        assertNotNull(submitBlockMessage(FareSplitSubmitBlock.ACCOUNT_INVALID))
        assertNotNull(submitBlockMessage(FareSplitSubmitBlock.FARE_BELOW_MINIMUM))
        assertNotNull(submitBlockMessage(FareSplitSubmitBlock.TARGET_MISMATCH))
    }

    // ─────────────────── 합계 표시 ───────────────────

    @Test
    fun `합계 줄은 합과 택시비를 나란히 보여 준다`() {
        assertEquals("합계 18,700원 / 택시비 18,700원", sumLabel(filled()))
    }

    /** 「모자라다」와 「많다」를 가르는 이유는 **할 일이 정반대**라서다. */
    @Test
    fun `차액은 모자람과 넘침을 갈라 금액으로 말한다`() {
        val short = filled().withAmount(SENDER_A, "1")
        assertEquals("택시비보다 6,232원 모자라요", differenceNotice(short)!!.label)
        assertEquals(FareSplitTone.WARN, differenceNotice(short)!!.tone)

        val over = filled().withAmount(SENDER_A, "9999")
        assertEquals("택시비보다 3,766원 많아요", differenceNotice(over)!!.label)

        assertEquals(FareSplitTone.DONE, differenceNotice(filled())!!.tone)
    }

    @Test
    fun `택시비를 적기 전에는 차액을 말하지 않는다`() {
        assertNull(differenceNotice(startState()))
    }

    // ─────────────────── 보낼 값 ───────────────────

    @Test
    fun `보낼 값은 사용자가 적은 원문 계좌번호를 그대로 담는다`() {
        val draft = filled().toDraft()

        assertEquals(18_700, draft.totalFare)
        assertEquals(Bank.KAKAO.code, draft.bankCode)
        // 정규화는 매퍼가 보내기 직전에 한다 — 두 곳에서 하면 한쪽이 바뀔 때 조용히 갈린다.
        assertEquals("3333-01-2345678", draft.accountNumber)
        assertEquals(3, draft.shares.size)
        assertEquals(18_700, draft.shares.sumOf { it.amount })
    }

    // ─────────────────── 수정 폼 ───────────────────

    @Test
    fun `수정 폼은 방 상세 없이 기존 정산만으로 채워진다`() {
        val state = (editDraftGate(detail()) as FareSplitDraftGate.Ready).state

        assertEquals(FareSplitEditMode.EDIT, state.mode)
        assertEquals("18700", state.fareInput)
        assertEquals("3333012345678", state.accountInput)
        assertEquals(listOf(PAYER_ID, SENDER_A, SENDER_B), state.riders.map { it.publicId })
        assertEquals("6234", state.amountInputs[PAYER_ID])
        assertEquals("6233", state.amountInputs[SENDER_A])
        assertNull(submitBlockOf(state))
    }

    /**
     * 서버에 있던 금액은 방장이 **손으로 적은 값**일 수 있다. 자동 채우기를 켜 두면 택시비 한 글자를
     * 고치는 순간 그 조정이 통째로 사라진다.
     */
    @Test
    fun `수정 폼은 1대N 자동 채우기를 켜지 않는다`() {
        val state = (editDraftGate(detail()) as FareSplitDraftGate.Ready).state
        val refared = state.withFare("20000")

        assertFalse(state.autoSplits)
        assertEquals("6234", refared.amountInputs[PAYER_ID])
        assertEquals(FareSplitSubmitBlock.SUM_NOT_EQUAL_FARE, submitBlockOf(refared))
    }

    /**
     * 서버가 23번째 은행을 추가하면 그 코드는 앱의 enum 에 없다. 그래도 **코드를 그대로 되돌려
     * 보내야** 금액만 고칠 수 있다 — enum 으로 좁히면 그 정산은 영구히 못 고친다.
     */
    @Test
    fun `모르는 은행 코드의 정산도 고칠 수 있다`() {
        val unknownBank = detail(
            bank = com.moyeota.domain.model.FareSplitBank("NEW_BANK", "새로생긴은행", known = null),
        )
        val state = (editDraftGate(unknownBank) as FareSplitDraftGate.Ready).state

        assertEquals("NEW_BANK", state.bankCode)
        // 보여 줄 이름의 출처는 서버다 — 앱이 모르는 코드여도 화면은 정상으로 보인다.
        assertEquals("새로생긴은행", state.bankLabel)
        assertEquals("NEW_BANK", state.toDraft().bankCode)
    }

    @Test
    fun `누가 보냈다고 누른 정산은 수정 폼이 열리지 않는다`() {
        val started = detail(transfers = listOf(transfer(SENDER_A, status = TransferStatus.SENT)))
        val blocked = editDraftGate(started) as FareSplitDraftGate.Blocked

        assertFalse(blocked.recoverable)
    }

    @Test
    fun `보내는 사람은 수정 폼을 열 수 없다`() {
        assertTrue(editDraftGate(detail(role = FareSplitRole.SENDER)) is FareSplitDraftGate.Blocked)
    }

    @Test
    fun `닫힌 정산은 수정 폼을 열 수 없다`() {
        assertTrue(editDraftGate(detail(status = FareSplitStatus.EXPIRED)) is FareSplitDraftGate.Blocked)
    }

    @Test
    fun `서버가 식별하지 못한 보내는 사람이 있으면 수정 폼을 열지 않는다`() {
        val anonymous = detail(transfers = listOf(transfer(publicId = ""), transfer(SENDER_B)))

        assertTrue(editDraftGate(anonymous) is FareSplitDraftGate.Blocked)
    }

    // ─────────────────── 문구 ───────────────────

    /**
     * 앱의 **예상 요금**을 칸에 미리 넣지 않는다 — 그대로 제출될 수 있고, 그러면 앱이 지어낸 숫자로
     * 돈이 움직인다. 힌트로만 보여 준다.
     */
    @Test
    fun `예상 요금은 힌트로만 보여 주고 칸에 넣지 않는다`() {
        val withEstimate = ride(estimatedFare = 10_800)
        val state = (startDraftGate(withEstimate) as FareSplitDraftGate.Ready).state

        assertEquals("", state.fareInput)
        assertEquals("앱이 예상한 요금은 10,800원이었어요", estimatedFareHint(withEstimate))
        assertNull(estimatedFareHint(ride()))
        assertNull(estimatedFareHint(ride(estimatedFare = 0)))
    }

    @Test
    fun `시작과 수정의 제목과 CTA 가 다르다`() {
        assertEquals("택시비 정산", editTitle(FareSplitEditMode.START))
        assertEquals("정산 고치기", editTitle(FareSplitEditMode.EDIT))
        assertTrue(editCtaLabel(FareSplitEditMode.START).contains("시작"))
        assertTrue(editCtaLabel(FareSplitEditMode.EDIT).contains("저장"))
    }

    /** 누른 뒤에 알면 늦은 두 사실 — 알림이 간다, 그리고 취소할 길이 없다. */
    @Test
    fun `시작 전에 알림과 취소 불가를 미리 말한다`() {
        assertTrue(START_IRREVERSIBLE_NOTICE.contains("송금 요청이 가요"))
        assertTrue(START_IRREVERSIBLE_NOTICE.contains("취소하는 길은 없"))
    }
}
