package com.moyeota.presentation.feature.mypage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 38 이의 신청의 실패 안내.
 *
 * 이 엔드포인트는 **409 가 두 가지 뜻**이다(`NOT_NO_SHOW`·`ALREADY_OBJECTED`). 상태 코드로 사유를
 * 고르면 반드시 절반이 틀린 말이 되고, 틀린 쪽을 받은 사용자는 **다음에 뭘 해야 하는지 알 수 없어**
 * 같은 버튼을 계속 누른다(`FinishFailureMessageTest` 가 같은 결함을 기록해 뒀다). 그래서 사유는
 * 도메인 예외의 `is…` 프로퍼티를 Route 가 [NoShowObjectionFailure] 로 한 번 접어 전달하고, 문구는
 * **그 enum 만** 본다 — 이 테스트가 예외 생성자에 묶이지 않는 이유이고, 서버가 코드를 늘려도
 * 문구가 흔들리지 않는 이유다.
 */
class NoShowObjectionMessageTest {

    /**
     * **유일하게 행동을 권하는 가지다.** 400 이고, 글자 수를 맞추면 실제로 통과한다 —
     * 나머지는 서버가 방의 사실을 보고 거절한 것이라 권할 행동이 없다.
     *
     * 화면이 버튼을 잠그므로 평소에는 오지 않지만, 서버가 이 코드를 **범용**으로 쓰기 때문에
     * (본문 깨짐·필드 이름 오류도 같은 400) 분기를 남긴다.
     */
    @Test
    fun `사유 길이 문제는 고치면 된다고 말한다`() {
        val message = noShowObjectionFailureMessage(NoShowObjectionFailure.INVALID_REASON)

        assertEquals("이의 사유는 1~500자로 적어야 보낼 수 있어요", message)
        // 입력창에서 고칠 수 있는 실패 — 보내기를 잠그면 쓴 글을 버리고 다시 열어야 한다.
        assertFalse(NoShowObjectionFailure.INVALID_REASON.isTerminal)
    }

    @Test
    fun `이미 냈으면 한 번만 낼 수 있다고 말한다`() {
        val message = noShowObjectionFailureMessage(NoShowObjectionFailure.ALREADY_OBJECTED)

        assertEquals("이 방에는 이미 이의를 냈어요. 방마다 한 번만 낼 수 있어요", message)
        // 「이미 냈어요」로 끝내면 사용자는 왜 또 못 내는지 모른다 — 규칙이 이 문구의 핵심이다.
        assertTrue(message, message.contains("한 번만"))
    }

    /**
     * 서버는 「이 방에서 노쇼가 아니다」와 「이미 무효가 됐다」를 **같은 코드**(`NOT_NO_SHOW`)로
     * 준다. 앱이 둘 중 하나로 단정하면 절반은 거짓이므로 **둘 다 가능성으로** 말한다.
     */
    @Test
    fun `노쇼가 아니거나 이미 무효인 경우를 둘 다 말한다`() {
        val message = noShowObjectionFailureMessage(NoShowObjectionFailure.NOT_NO_SHOW)

        assertEquals("이 방에서는 노쇼로 기록돼 있지 않아요. 이미 무효가 된 기록일 수 있어요", message)
        assertTrue(message, message.contains("무효"))
        // 단정하지 않는다 — 어느 쪽인지 서버가 가려 주지 않는다.
        assertTrue(message, message.contains("수 있어요"))
    }

    @Test
    fun `참여자가 아니면 그 사실만 말한다`() {
        assertEquals(
            "이 방의 참여자가 아니라 이의를 낼 수 없어요",
            noShowObjectionFailureMessage(NoShowObjectionFailure.NOT_MEMBER),
        )
    }

    @Test
    fun `사라진 방`() {
        assertEquals(
            "이미 사라진 방이에요. 이 방에는 이의를 낼 수 없어요",
            noShowObjectionFailureMessage(NoShowObjectionFailure.PARTY_NOT_FOUND),
        )
    }

    /**
     * **서버가 코드를 늘려도 앱이 지어내지 않는다.** 응답을 못 받은 실패(연결 거부·타임아웃)도
     * 같은 자리다 — 서버가 말해 준 사유가 없으면 모른다고 한다.
     */
    @Test
    fun `모르는 실패는 일반 문구로 떨어진다`() {
        assertEquals(
            OBJECTION_FAILED_MESSAGE,
            noShowObjectionFailureMessage(NoShowObjectionFailure.UNKNOWN),
        )
        assertEquals("이의를 보내지 못했어요", OBJECTION_FAILED_MESSAGE)
    }

    @Test
    fun `실패마다 다른 말을 한다 - 같은 문구가 두 갈래에 쓰이면 분기한 의미가 없다`() {
        val messages = NoShowObjectionFailure.entries.map(::noShowObjectionFailureMessage)

        assertEquals(messages.size, messages.distinct().size)
    }

    /**
     * ★ 지키지 못할 약속을 넣지 않는다. 여기서 특히 위험한 건 **재시도 안내**다 —
     * 403·409·404 는 다시 눌러도 같은 답이고, 이 화면에서 바꿀 수 있는 것이 없다.
     * `FinishFailureMessageTest` 가 세운 바를 그대로 적용한다.
     */
    @Test
    fun `다시 누르면 된다고 약속하지 않는다`() {
        val messages = NoShowObjectionFailure.entries.map(::noShowObjectionFailureMessage)

        listOf("잠시 후", "다시 시도", "다시 눌러", "다시 보내").forEach { forbidden ->
            messages.forEach { message ->
                assertFalse("지키지 못할 약속이다: $message", message.contains(forbidden))
            }
        }
    }

    /**
     * **없는 길을 권하지 않는다.** 21 매칭의 같은 코드들은 「홈으로 돌아가 주세요」로 안내하지만
     * (돌아갈 진행 중인 방이 있다), 여기는 **끝난 방의 기록**이다 — 홈에 가도 할 일이 없다.
     * 할 수 있는 일이 없을 때는 없다고만 말한다.
     */
    @Test
    fun `끝난 방의 실패에 홈으로 가라고 하지 않는다`() {
        val messages = NoShowObjectionFailure.entries.map(::noShowObjectionFailureMessage)

        listOf("홈으로", "홈에서").forEach { forbidden ->
            messages.forEach { message ->
                assertFalse("갈 곳이 없는데 길을 권한다: $message", message.contains(forbidden))
            }
        }
    }

    /** 운영 처리·결과를 약속하지 않는다 — 실패 문구에서도 같은 바다. */
    @Test
    fun `실패 문구가 운영 처리를 약속하지 않는다`() {
        val messages = NoShowObjectionFailure.entries.map(::noShowObjectionFailureMessage)

        listOf("확인해 드려요", "알려 드려요", "시간 안에", "영업일", "해제").forEach { forbidden ->
            messages.forEach { message ->
                assertFalse("지킬 수 없는 약속이다: $message", message.contains(forbidden))
            }
        }
    }

    /**
     * 「다시 눌러도 같은 답」을 **문구가 아니라 버튼으로도** 말한다. 403·409·404 는 보내기를
     * 잠그고(시트의 왼쪽 버튼이 「닫기」로 바뀐다), 연결 실패만 다시 누를 수 있다 — 사용자는
     * 문구를 읽지 않아도 버튼은 본다.
     */
    @Test
    fun `서버가 거절한 실패는 다시 보낼 수 없고 연결 실패만 다시 보낼 수 있다`() {
        listOf(
            NoShowObjectionFailure.NOT_NO_SHOW,
            NoShowObjectionFailure.ALREADY_OBJECTED,
            NoShowObjectionFailure.NOT_MEMBER,
            NoShowObjectionFailure.PARTY_NOT_FOUND,
        ).forEach { failure ->
            assertTrue("$failure 는 다시 보내도 같은 답이다", failure.isTerminal)
        }
        // 연결 실패는 다시 보내면 통할 수 있고, 글자 수는 고치면 통한다.
        assertFalse(NoShowObjectionFailure.UNKNOWN.isTerminal)
        assertFalse(NoShowObjectionFailure.INVALID_REASON.isTerminal)
    }
}
