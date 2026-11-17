package com.moyeota.presentation.feature.faresplit

import com.moyeota.domain.model.FareSplitRole
import com.moyeota.domain.model.FareSplitStatus
import com.moyeota.domain.model.TransferStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **어떤 버튼이 어떤 행에 달리는가** — 이 저장소에서 틀렸을 때 가장 비싼 판정이다.
 *
 * 세 가지를 못 박는다.
 * 1. **「받았어요」와 「아직 안 들어왔어요」가 같은 조건에서 함께 열리지 않는다.** 둘 다 204 로
 *    성공해 보이는데 한쪽은 되돌릴 수 없다 — 바꿔 달려 있으면 **아무도 알아채지 못한다.**
 * 2. **멱등이 아닌 하나만 더 좁다.** `markNotReceived` 는 SENT 에서만 되고(409 `TRANSFER_NOT_SENT`),
 *    나머지 둘은 「이미 그 상태」여도 204 다.
 * 3. **서버가 식별하지 못한 행은 잠긴다.** 안 잠그면 404 가 영구히 나고, 그 한 행 때문에 정산이
 *    COMPLETED 로 닫히지 않는다.
 */
class FareSplitActionGateTest {

    // ─────────────────── 받는 사람의 두 버튼 ───────────────────

    /** 상태표 전체를 한 테스트로 고정한다 — 가지 하나를 빠뜨리면 여기서 깨진다. */
    @Test
    fun `송금 상태마다 누를 수 있는 버튼이 다르다`() {
        val expected = mapOf(
            // 아직 안 보냄 — 현금으로 받았으면 바로 확인할 수 있다(서버가 허용한다).
            // 되돌릴 것은 없다: 「아직 안 들어왔어요」는 409 TRANSFER_NOT_SENT 다.
            TransferStatus.NOT_SENT to (true to false),
            // 보냈다고 알림 — 확인도 되돌리기도 **여기서만** 둘 다 된다.
            TransferStatus.SENT to (true to true),
            // 받음 확인 — 되돌릴 길이 없다. 둘 다 잠긴다.
            TransferStatus.CONFIRMED to (false to false),
        )

        expected.forEach { (status, buttons) ->
            val (canConfirm, canRevert) = buttons
            val row = transfer(status = status)
            val actions = transferActionsOf(detail(transfers = listOf(row)), row)

            assertEquals("$status 의 「받았어요」", canConfirm, actions.canConfirm)
            assertEquals("$status 의 「아직 안 들어왔어요」", canRevert, actions.canMarkNotReceived)
        }
    }

    /**
     * 되돌리기가 **열려 있는 상태는 SENT 하나뿐**이다. 이 단정을 따로 두는 이유: 위 표가
     * 느슨해지는 방향으로 고쳐지는 것(NOT_SENT 에도 열어 주기)을 막는다 — 그 한 줄이 409 다.
     */
    @Test
    fun `되돌리기는 보냈다고 알린 송금에만 열린다`() {
        val opened = TransferStatus.entries.filter { status ->
            val row = transfer(status = status)
            transferActionsOf(detail(transfers = listOf(row)), row).canMarkNotReceived
        }

        assertEquals(listOf(TransferStatus.SENT), opened)
    }

    /**
     * 두 버튼의 **열림 조건 집합이 달라야** 한다 — 같으면 배선을 바꿔 달아도 화면이 똑같아 보인다.
     *
     * SENT 에서 둘이 **함께** 열리는 것은 맞다(그 상태에서만 확인도 되돌리기도 된다). 바꿔 단 것을
     * 잡아 주는 상태는 NOT_SENT 다 — 거기서 확인은 열리고 되돌리기는 잠겨야 한다. 그 한 칸이
     * 같아지는 순간, 되돌리기가 409 로만 끝나거나 현금 수령 확인 경로가 사라진다.
     */
    @Test
    fun `두 버튼의 열림 조건 집합이 서로 다르다`() {
        fun openStates(pick: (TransferActions) -> Boolean) = TransferStatus.entries.filter { status ->
            val row = transfer(status = status)
            pick(transferActionsOf(detail(transfers = listOf(row)), row))
        }

        val confirmOpen = openStates { it.canConfirm }
        val revertOpen = openStates { it.canMarkNotReceived }

        assertEquals(listOf(TransferStatus.NOT_SENT, TransferStatus.SENT), confirmOpen)
        assertEquals(listOf(TransferStatus.SENT), revertOpen)
        assertNotEquals("두 버튼의 조건이 같으면 바꿔 달아도 아무도 모른다", confirmOpen, revertOpen)
    }

    @Test
    fun `서버가 식별하지 못한 행은 두 버튼이 모두 잠기고 이유가 적힌다`() {
        val anonymous = transfer(publicId = "", nickname = "탈퇴한 회원", status = TransferStatus.SENT)
        val actions = transferActionsOf(detail(transfers = listOf(anonymous)), anonymous)

        assertFalse(actions.canConfirm)
        assertFalse(actions.canMarkNotReceived)
        // 잠긴 이유를 적지 않으면 「왜 이 사람만 못 누르지」에 답할 수 없다.
        assertNotNull(actions.lockReason)
    }

    @Test
    fun `이미 확인한 행은 되돌릴 수 없다고 말한다`() {
        val done = transfer(status = TransferStatus.CONFIRMED)
        val actions = transferActionsOf(detail(transfers = listOf(done)), done)

        assertTrue(actions.lockReason!!.contains("되돌릴 수 없"))
    }

    @Test
    fun `보내는 사람 화면에는 받는 사람의 버튼이 하나도 없다`() {
        val row = transfer(status = TransferStatus.SENT)
        val actions = transferActionsOf(detail(role = FareSplitRole.SENDER, transfers = listOf(row)), row)

        assertFalse(actions.canConfirm)
        assertFalse(actions.canMarkNotReceived)
        // 그릴 버튼이 없으므로 설명할 것도 없다 — 잠금 문구를 띄우면 보내는 사람이
        // 「내가 확인해야 하나」로 읽는다.
        assertNull(actions.lockReason)
    }

    @Test
    fun `닫힌 정산에서는 받는 사람도 아무것도 누를 수 없다`() {
        listOf(FareSplitStatus.COMPLETED, FareSplitStatus.EXPIRED).forEach { status ->
            val row = transfer(status = TransferStatus.SENT)
            val actions = transferActionsOf(detail(status = status, transfers = listOf(row)), row)

            assertFalse("$status", actions.canConfirm)
            assertFalse("$status", actions.canMarkNotReceived)
        }
    }

    // ─────────────────── 마지막 한 건 ───────────────────

    @Test
    fun `나머지가 모두 확인됐으면 마지막 한 건이다`() {
        val last = transfer(SENDER_A, amount = 6_233, status = TransferStatus.SENT)
        val done = transfer(SENDER_B, amount = 6_233, status = TransferStatus.CONFIRMED)

        assertTrue(isLastOutstandingTransfer(detail(transfers = listOf(last, done)), last))
    }

    @Test
    fun `아직 확인 안 된 다른 행이 있으면 마지막이 아니다`() {
        val a = transfer(SENDER_A, status = TransferStatus.SENT)
        val b = transfer(SENDER_B, status = TransferStatus.NOT_SENT)

        assertFalse(isLastOutstandingTransfer(detail(transfers = listOf(a, b)), a))
    }

    /** 마지막 한 건의 확인은 **정산 전체를 닫고 계좌번호를 지운다** — 그 사실을 다이얼로그가 말해야 한다. */
    @Test
    fun `마지막 한 건의 확인 다이얼로그는 정산이 끝난다고 말한다`() {
        val last = transfer(SENDER_A, status = TransferStatus.SENT)
        val done = transfer(SENDER_B, status = TransferStatus.CONFIRMED)
        val message = confirmReceivedDialogMessage(detail(transfers = listOf(last, done)), last)

        assertTrue(message.contains("정산이 끝나고"))
        assertTrue(message.contains("계좌번호가 지워져요"))
    }

    @Test
    fun `확인 다이얼로그는 언제나 되돌릴 수 없다고 말하고 금액과 이름을 적는다`() {
        val a = transfer(SENDER_A, nickname = "동승자A", amount = 6_233, status = TransferStatus.SENT)
        val b = transfer(SENDER_B, status = TransferStatus.NOT_SENT)
        val message = confirmReceivedDialogMessage(detail(transfers = listOf(a, b)), a)

        assertTrue(message.contains("되돌릴 수 없어요"))
        assertTrue(message.contains("동승자A"))
        assertTrue(message.contains("6,233원"))
        // 마지막이 아니므로 정산이 끝난다고 말하지 않는다 — 매번 말하면 경고가 닳는다.
        assertFalse(message.contains("정산이 끝나고"))
    }

    /** 「취소」라고 적으면 방장이 **정산 자체를 지우는 버튼**으로 읽는다. 그 길은 서버에 없다. */
    @Test
    fun `되돌리기 다이얼로그는 정산 취소라고 말하지 않는다`() {
        val message = markNotReceivedDialogMessage(transfer(status = TransferStatus.SENT))

        assertFalse(message.contains("취소"))
        assertTrue(message.contains("되돌릴까요"))
    }

    // ─────────────────── 보내는 사람 단계 ───────────────────

    /**
     * 판정의 입력은 **서버가 직접 준** `myTransferStatus` 다 — 세션 uuid 로 `transfers` 에서 내 행을
     * 찾던 방식(Backend PR #328 이전)은 서버가 나를 설명하지 못한 응답에서 답을 못 냈다.
     */
    @Test
    fun `보낼 단계는 서버가 준 내 송금 상태로 갈린다`() {
        fun sender(status: TransferStatus?, fareSplitStatus: FareSplitStatus = FareSplitStatus.OPEN) =
            detail(status = fareSplitStatus, role = FareSplitRole.SENDER, myTransferStatus = status)

        assertEquals(SenderStage.SEND_READY, senderStageOf(sender(TransferStatus.NOT_SENT)))
        assertEquals(SenderStage.SENT_WAITING, senderStageOf(sender(TransferStatus.SENT)))
        assertEquals(SenderStage.CONFIRMED, senderStageOf(sender(TransferStatus.CONFIRMED)))
        assertEquals(
            SenderStage.CLOSED,
            senderStageOf(sender(TransferStatus.NOT_SENT, FareSplitStatus.COMPLETED)),
        )
    }

    /**
     * 받는 사람이 탈퇴하면 서버가 **OPEN 인 정산의 계좌번호만** 지운다 — 상태는 그대로다.
     * 「계좌가 없으면 끝난 정산」으로 읽으면 아직 보낼 돈이 남은 사람에게 「정산이 끝났어요」가 뜬다.
     */
    @Test
    fun `진행 중인데 계좌가 사라지면 계좌 소실 단계다`() {
        val lost = detail(
            role = FareSplitRole.SENDER,
            myTransferStatus = TransferStatus.NOT_SENT,
            accountNumber = null,
        )

        assertEquals(SenderStage.ACCOUNT_LOST, senderStageOf(lost))
        // 다시 받아도 계좌는 돌아오지 않는다 — 재시도를 권하지 말고 채팅으로 보낸다.
        assertTrue(senderStageNotice(SenderStage.ACCOUNT_LOST)!!.contains("채팅"))
        assertTrue(showsChatFallback(lost))
    }

    /** 이미 보낸 사람에게 「계좌를 물어봐 주세요」는 **이미 끝난 일을 다시 하라는 말**이다. */
    @Test
    fun `계좌가 사라져도 이미 보낸 송금은 보냈다고 알린 단계로 남는다`() {
        val lost = detail(
            role = FareSplitRole.SENDER,
            myTransferStatus = TransferStatus.SENT,
            accountNumber = null,
        )

        assertEquals(SenderStage.SENT_WAITING, senderStageOf(lost))
    }

    /**
     * 서버가 상태를 늘렸거나 옛 배포가 그 필드를 싣지 않으면 null 로 온다(매퍼가 둘을 같게 떨어뜨린다).
     * [SenderStage.SEND_READY] 로 떨어뜨리지 않는 것이 핵심 — 모르는 상태에 버튼을 열면 이미 보낸
     * 사람이 또 보낸다.
     */
    @Test
    fun `서버가 내 송금 상태를 말해 주지 않으면 보낼 단계로 떨어지지 않는다`() {
        val unknown = detail(role = FareSplitRole.SENDER, myTransferStatus = null)

        assertEquals(SenderStage.UNKNOWN_MY_TRANSFER, senderStageOf(unknown))
        assertFalse(showsMarkSentButton(unknown))
    }

    /** 멀쩡한 정산에 「채팅으로 물어보기」를 두면 **할 일이 흐려진다.** */
    @Test
    fun `앱 안에 길이 남아 있으면 채팅으로 보내지 않는다`() {
        assertFalse(showsChatFallback(detail(role = FareSplitRole.SENDER)))
        assertFalse(showsChatFallback(detail(status = FareSplitStatus.COMPLETED, accountNumber = null)))
        // 기간이 지난 정산은 앱 안에서 할 수 있는 일이 없다 — 돈이 안 건너갔을 수도 있다.
        assertTrue(showsChatFallback(detail(status = FareSplitStatus.EXPIRED, accountNumber = null)))
    }

    // ─────────────────── 토스·보냈어요 버튼 ───────────────────

    /** 서버가 딥링크를 주지 않으면 **만들어 끼우지 않는다** — 규격도 은행 표기도 서버 것이다. */
    @Test
    fun `토스 버튼은 서버가 딥링크를 줬을 때만 그린다`() {
        val ready = detail(
            role = FareSplitRole.SENDER,
            myTransferStatus = TransferStatus.NOT_SENT,
            tossDeeplink = "supertoss://send?amount=6233&bank=카카오뱅크&accountNo=3333012345678",
        )
        assertTrue(showsTossButton(ready))

        assertFalse(showsTossButton(ready.copy(tossDeeplink = null)))
        assertFalse(showsTossButton(ready.copy(tossDeeplink = "")))
        // 이미 보낸 사람에게 송금 버튼을 남기지 않는다(딥링크가 남아 있어도).
        assertFalse(showsTossButton(ready.copy(myTransferStatus = TransferStatus.SENT)))
    }

    /**
     * ### 「보냈어요」의 조건은 `canMarkSent` 하나다 ★
     * 「진행 중 · 내가 보내는 사람 · 아직 안 보냄」을 화면이 다시 조립하면 **받는 사람의 null 을
     * 「아직 안 보냈다」로 읽는 실수**가 끼어들고, 그 버튼은 403 `NOT_FARE_SPLIT_SENDER` 다.
     */
    @Test
    fun `보냈어요 버튼은 받는 사람에게 뜨지 않는다`() {
        // 받는 사람의 myTransferStatus 는 서버가 일부러 null 로 준다.
        assertFalse(showsMarkSentButton(detail(role = FareSplitRole.PAYER)))
        assertTrue(showsMarkSentButton(detail(role = FareSplitRole.SENDER)))
    }

    /**
     * 보낸 뒤에는 버튼이 아니라 **상태**를 보여 준다. 서버는 다시 눌러도 204 지만, 버튼이 남아 있으면
     * 사용자가 「안 눌렸나」 싶어 또 누르고 같은 화면이 반복된다.
     */
    @Test
    fun `이미 보냈거나 확인됐으면 버튼 대신 상태를 보여 준다`() {
        listOf(TransferStatus.SENT, TransferStatus.CONFIRMED).forEach { status ->
            val sent = detail(role = FareSplitRole.SENDER, myTransferStatus = status)

            assertFalse("$status", showsMarkSentButton(sent))
            assertNotNull("$status", senderStageNotice(senderStageOf(sent)))
        }
    }

    /**
     * 계좌가 사라진 단계에도 「보냈어요」는 남는다 — 채팅으로 계좌를 받아 **앱 밖에서 이미 보낼 수
     * 있고**, 버튼을 거두면 그 사실을 알릴 길이 없어 4시간마다 재요청 알림이 계속 간다.
     */
    @Test
    fun `계좌가 사라져도 보냈다고 알릴 수 있다`() {
        val lost = detail(
            role = FareSplitRole.SENDER,
            myTransferStatus = TransferStatus.NOT_SENT,
            accountNumber = null,
        )

        assertTrue(showsMarkSentButton(lost))
    }

    @Test
    fun `닫힌 정산에서는 보냈다고 알릴 수 없다`() {
        listOf(FareSplitStatus.COMPLETED, FareSplitStatus.EXPIRED).forEach { status ->
            val closed = detail(
                status = status,
                role = FareSplitRole.SENDER,
                myTransferStatus = TransferStatus.NOT_SENT,
            )

            assertFalse("$status", showsMarkSentButton(closed))
        }
    }

    /**
     * 보내는 사람은 자기 「보냈어요」를 되돌릴 수 없다(`markNotReceived` 는 PAYER 전용이다) —
     * 그 사실을 누르기 전에 말한다.
     */
    @Test
    fun `보냈어요 확인은 금액과 되돌릴 수 없음을 말한다`() {
        val message = markSentDialogMessage(detail(role = FareSplitRole.SENDER, myAmount = 6_233))

        assertTrue(message.contains("6,233원"))
        assertTrue(message.contains("받는 분만 할 수 있어요"))
        // 「이미 보냈습니다」류를 지어내지 않는다 — 본문 없는 204 로는 알 수 없는 사실이다.
        assertFalse(message.contains("이미"))
    }

    // ─────────────────── 수정 가능 여부 ───────────────────

    @Test
    fun `전원이 아직 안 보냈으면 받는 사람은 고칠 수 있다`() {
        assertNull(editBlockReason(detail()))
    }

    /** 「정산 취소는 없다」(서버 원문)를 그 자리에 적는다 — 없으면 방장이 취소할 길을 찾는다. */
    @Test
    fun `누가 보냈다고 누르면 왜 못 고치는지와 대신 할 일을 적는다`() {
        val started = detail(transfers = listOf(transfer(SENDER_A, status = TransferStatus.SENT)))
        val reason = editBlockReason(started)!!

        assertTrue(reason.contains("더 이상 고칠 수 없어요"))
        assertTrue(reason.contains("취소하는 길은 없"))
    }

    @Test
    fun `닫힌 정산은 고칠 수 없다고만 말한다`() {
        assertEquals("닫힌 정산은 고칠 수 없어요", editBlockReason(detail(status = FareSplitStatus.COMPLETED)))
    }

    /** 보내는 사람 화면에는 수정 영역이 통째로 없다 — 설명할 자리가 없다는 뜻의 null 이다. */
    @Test
    fun `보내는 사람에게는 수정 안내를 적지 않는다`() {
        assertNull(editBlockReason(detail(role = FareSplitRole.SENDER)))
    }
}
