package com.moyeota.presentation.feature.chat

import com.moyeota.domain.model.ChatException
import com.moyeota.domain.model.ChatReportReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 24 채팅방 메시지 신고에서 **화면이 미리 막아야 하는 것**과 **실패를 어떻게 읽는지**를 못 박는다.
 *
 * 왜 화면이 미리 막는가: 서버가 거절하는 조건 넷(본인 메시지 · id 없음 · 「기타」인데 상세 없음 ·
 * 상세 500자 초과)은 **누르기 전부터 전부 알 수 있다.** 왕복해서 받으면 사용자는 사유를 고르고
 * 글까지 쓴 끝에 거절당하는데, 서버 문구는 「chatMessageId: 메시지 ID는 1 이상이어야 합니다」처럼
 * 보여 줄 수 없는 말이다(실측 2026-11-01, 로컬 :8080).
 */
class MessageReportGuardTest {

    private fun peer(id: Long) = ChatUiMessage(text = "본문", isMine = false, id = id)

    // ─── 신고 길을 만들어도 되는가 ───

    /**
     * 내 메시지에는 신고 길을 **만들지 않는다.**
     *
     * 안 막으면 서버 400 `CHAT_CANNOT_REPORT_SELF` 를 왕복해서 받는다. 그보다 나쁜 것은
     * 「자기 자신을 신고하는 경로가 화면에 있다」는 사실 자체다 — 눌러 보고서야 안 된다는 걸 안다.
     */
    @Test
    fun `내 메시지에는 신고 길을 만들지 않는다`() {
        assertFalse(ChatUiMessage(text = "내 말", isMine = true, id = 42L).canReport())
        assertTrue(peer(42L).canReport())
    }

    /**
     * 서버 id 가 없는 말풍선도 신고할 수 없다.
     *
     * 전송 확인을 기다리는 내 메시지(`PendingMessage`)와 Preview 더미가 그렇다. 그 자리에 0 이
     * 들어가는데, 0 을 보내면 서버가 400 `INVALID_REQUEST` 를 낸다 — 즉 화면의 하한
     * ([ChatReportReason.MIN_MESSAGE_ID])이 서버의 `@Positive` 와 **같은 기준**이다.
     */
    @Test
    fun `서버 id 가 없는 말풍선은 신고할 수 없다`() {
        assertFalse(peer(NO_SERVER_MESSAGE_ID).canReport())
        assertFalse("음수 id 가 통과한다", peer(-3L).canReport())
        assertTrue(peer(ChatReportReason.MIN_MESSAGE_ID).canReport())
    }

    // ─── 사유 시트 ───

    /**
     * 사유 줄은 **서버 enum 을 하나도 빠뜨리지 않는다.**
     *
     * 빠뜨리면 그 사유로는 신고할 길이 없어 사용자가 「그 밖의 이유예요」로 뭉개 적는다. 서버가
     * `chat_report` 에 사유를 저장하고 피신고자 인덱스를 걸어 둔 건 분류로 조치하려는 설계인데,
     * 전부 ETC 로 들어가면 그 설계가 무력해진다.
     */
    @Test
    fun `사유 줄은 서버 enum 을 하나도 빠뜨리지 않는다`() {
        assertEquals(ChatReportReason.entries.toList(), chatReportReasonLabels.map { it.first })
        chatReportReasonLabels.forEach { (reason, label) ->
            assertTrue("$reason 의 문구가 비었다", label.isNotBlank())
        }
    }

    /** 사유를 고르기 전에는 보낼 수 없다 — 서버는 「신고 사유는 필수입니다」로 400 을 낸다. */
    @Test
    fun `사유를 고르지 않으면 보낼 수 없다`() {
        assertFalse(chatReportSubmitUi(reason = null, description = "").submitEnabled)
    }

    /** 「기타」가 아닌 사유는 상세 없이 보낼 수 있다 — 생략해도 201 이다(실측). */
    @Test
    fun `기타가 아닌 사유는 상세 없이 보낼 수 있다`() {
        ChatReportReason.entries.filterNot { it.requiresDescription }.forEach { reason ->
            assertTrue("$reason 가 상세 없이 잠겨 있다", chatReportSubmitUi(reason, description = "").submitEnabled)
        }
    }

    /**
     * 「기타」는 상세 내용 없이 보낼 수 없다. **공백만 적어도 마찬가지**다 —
     * 서버가 `isBlank` 로 보고 400 `CHAT_REPORT_DESCRIPTION_REQUIRED` 를 낸다(실측).
     *
     * 공백을 허용하면 「썼는데 입력하라고 한다」가 된다. 잠그는 쪽이 설명 가능하다.
     */
    @Test
    fun `기타 사유는 상세 내용 없이 전송할 수 없다`() {
        assertTrue(ChatReportReason.ETC.requiresDescription)
        assertFalse(chatReportSubmitUi(ChatReportReason.ETC, description = "").submitEnabled)
        assertFalse(chatReportSubmitUi(ChatReportReason.ETC, description = "   \n\t ").submitEnabled)
        assertTrue(chatReportSubmitUi(ChatReportReason.ETC, description = "같은 말을 반복해요").submitEnabled)
    }

    /**
     * 상세는 500자까지다. **자르지 않고 카운터로 알린다** — 이 화면의 입력창이 세운 규칙과 같다
     * (잘라 버리면 넘겼다는 사실을 말해 줄 기회가 사라진다). 넘긴 상태에서는 잠긴다.
     */
    @Test
    fun `상세 내용은 500자를 넘길 수 없다`() {
        val atLimit = "가".repeat(ChatReportReason.MAX_DESCRIPTION_LENGTH)
        val over = atLimit + "가"

        assertTrue(chatReportSubmitUi(ChatReportReason.ETC, atLimit).submitEnabled)
        chatReportSubmitUi(ChatReportReason.ETC, over).let {
            assertTrue(it.overLimit)
            assertFalse(it.submitEnabled)
            assertTrue("넘긴 이유를 말하지 않는다", it.counter.orEmpty().contains("줄여야"))
        }
    }

    /**
     * **카운터는 잠김보다 먼저 뜬다.** 한도에서야 나타나면 ① 경고가 아니라 사후 통보가 되고
     * ② 카운터 줄이 생기면서 버튼이 움직이는 그 프레임에 버튼이 잠긴다. 입력창 쪽
     * `COUNTER_VISIBLE_FROM` 과 같은 근거다.
     */
    @Test
    fun `카운터는 잠기기 전에 먼저 나타난다`() {
        assertTrue(REPORT_COUNTER_VISIBLE_FROM < ChatReportReason.MAX_DESCRIPTION_LENGTH)

        // 평상시(짧게 적는 중)에는 줄을 그리지 않는다 — 의미 없는 숫자가 시트 높이를 빼앗는다.
        assertEquals(null, chatReportSubmitUi(ChatReportReason.ETC, "짧게").counter)
        // 한도에 가까워지면 뜨고, 아직 잠기지는 않는다.
        chatReportSubmitUi(ChatReportReason.ETC, "가".repeat(REPORT_COUNTER_VISIBLE_FROM)).let {
            assertFalse(it.overLimit)
            assertTrue(it.submitEnabled)
            assertTrue(it.counter.orEmpty().isNotBlank())
        }
    }

    /** 보내는 중에는 다시 누를 수 없다 — 같은 신고를 두 번 보내면 둘째는 409 다. */
    @Test
    fun `보내는 중에는 다시 누를 수 없다`() {
        assertFalse(
            chatReportSubmitUi(ChatReportReason.ABUSE, description = "", submitting = true).submitEnabled,
        )
    }

    // ─── 실패를 어떻게 읽는가 ───

    private fun chat(code: String, status: Int, network: Boolean = false) = ChatException(
        code = code.takeIf { it.isNotEmpty() },
        serverMessage = null,
        httpStatus = status,
        isNetwork = network,
    )

    /**
     * 「이미 신고한 메시지」는 **재시도로 열어 두지 않는다.**
     *
     * 사유를 바꿔 보내도 같은 409 다(중복 판정이 「신고자 + 메시지」 단위 — 실측). 시트를 열어
     * 두면 사용자가 다섯 사유를 돌아가며 눌러도 전부 거절되는 곳에 갇힌다.
     */
    @Test
    fun `이미 신고한 메시지는 재시도하지 않는다`() {
        val outcome = chat("CHAT_ALREADY_REPORTED", 409).toChatReportOutcome()

        assertFalse(outcome.accepted)
        assertFalse("시트를 열어 두고 재시도를 유도한다", outcome.retryable)
        assertTrue(outcome.message.contains("이미 신고"))
    }

    /**
     * 연결 실패**만** 시트를 열어 둔다 — 적어 둔 상세 수백 자를 한 번 끊긴 연결 때문에 버리게
     * 하지 않는다. 반대로 「대상이 틀렸다」류(참여자 아님·메시지 없음·본인)는 다시 눌러도 같은
     * 답이라 닫는다.
     */
    @Test
    fun `연결 실패만 시트를 열어 둔다`() {
        // 저장소가 응답을 못 받은 실패를 이 모양으로 올려 준다(ChatMappers.toChatException).
        val offline = ChatException(code = null, serverMessage = null, isNetwork = true)
        assertTrue(offline.toChatReportOutcome().retryable)

        listOf(
            chat("CHAT_NOT_PARTICIPANT", 403),
            chat("CHAT_MESSAGE_NOT_FOUND", 404),
            chat("CHAT_CANNOT_REPORT_SELF", 400),
        ).forEach { error ->
            val outcome = error.toChatReportOutcome()
            assertFalse("${error.code} 를 재시도로 읽고 있다", outcome.retryable)
            assertFalse(outcome.accepted)
        }
    }

    /**
     * 서버 원문을 **그대로 보여 주지 않는다.** `INVALID_REQUEST` 의 message 는 필드명을 담고
     * 있어(「chatMessageId: 메시지 ID는 1 이상이어야 합니다」) 사용자에게 흘리면 안 된다.
     */
    @Test
    fun `검증 실패 문구에 서버 필드명을 흘리지 않는다`() {
        val error = ChatException(
            code = "INVALID_REQUEST",
            serverMessage = "chatMessageId: 메시지 ID는 1 이상이어야 합니다",
            httpStatus = 400,
        )

        val outcome = error.toChatReportOutcome()
        assertFalse(outcome.message.contains("chatMessageId"))
        assertFalse(outcome.accepted)
    }

    /** 매핑에 없는 실패(5xx·401 등)는 다음에 될 수 있는 것으로 본다 — 입력을 버리지 않는다. */
    @Test
    fun `모르는 실패는 다시 시도할 수 있는 것으로 둔다`() {
        val outcome = chat("", 500).toChatReportOutcome()

        assertTrue(outcome.retryable)
        assertFalse(outcome.accepted)
    }
}
