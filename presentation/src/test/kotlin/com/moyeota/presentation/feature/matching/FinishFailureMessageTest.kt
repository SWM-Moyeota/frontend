package com.moyeota.presentation.feature.matching

import com.moyeota.domain.model.PartyFinishException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 21 매칭 대기의 「합승 완료」 실패 안내.
 *
 * 예전에는 **전부 하나의 문구**였다 — 네트워크 끊김도, 403 도, 409 도 「합승을 완료하지 못했어요」.
 * 그 사이에 서버가 이 API 를 **방장 전용**으로 좁혔고(Backend #237), 비방장은 403 `NOT_PARTY_HOST` 를
 * 받는다. 사유를 가르지 않으면 그 사람은 **다음에 뭘 해야 하는지 알 수 없어** 같은 버튼을 계속 누른다.
 *
 * 그 사이 버튼은 **방장에게만 보이게 됐다**([canFinishParty]) — 서버가 멤버마다 `host` 를 내려 주기
 * 시작해(Backend #240 반영 완료) 노출 조건을 걸 근거가 생겼다. 그래도 **이 안내는 남는다**: 방장은
 * 멤버가 나갈 때마다 남은 멤버 중 `min(joinedAt, memberId)` 로 자동 승계되는데, 그걸 알려 주는 신호는
 * `member-left` 한 번뿐이라 놓치면 내 스냅샷이 낡은 채로 남는다. 그 틈에서 눌린 「합승 완료」를
 * 받아 주는 자리가 여기다.
 *
 * 코드 문자열의 근거는 백엔드 `MatchingErrorCode`(enum 이름이 그대로 `getCode()`)와
 * `Party.finishWithoutDriver` 의 검사 순서다.
 */
class FinishFailureMessageTest {

    private fun failure(code: String?, httpStatus: Int? = 409) = PartyFinishException(
        code = code,
        serverMessage = null,
        httpStatus = httpStatus,
    )

    private val general = "합승을 완료하지 못했어요"

    @Test
    fun `방장만 완료할 수 있다 - 누가 눌러야 하는지 말한다`() {
        val message = finishFailureMessage(failure(PartyFinishException.NOT_PARTY_HOST, httpStatus = 403))

        assertEquals("방장만 합승을 완료할 수 있어요. 가장 먼저 참여한 분에게 부탁해 주세요", message)
        // 「방장만」으로 끝내면 사용자가 할 수 있는 일이 없다 — 누가 눌러야 하는지가 이 문구의 핵심이다.
        assertTrue("누가 눌러야 하는지 말해야 한다", message.contains("가장 먼저 참여한"))
    }

    /** 같은 403 이지만 뜻이 다르다 — 멤버가 아닌 사람에게 방장을 찾으라고 하면 안 된다. */
    @Test
    fun `멤버가 아니다 - 같은 403 이어도 방장 안내로 뭉개지 않는다`() {
        val message = finishFailureMessage(failure(PartyFinishException.NOT_PARTY_MEMBER, httpStatus = 403))

        assertEquals("이 방의 참여자가 아니에요. 홈에서 진행 중인 방을 확인해 주세요", message)
        assertFalse("방장 안내와 섞이면 안 된다", message.contains("방장"))
    }

    @Test
    fun `정원이 안 찼다 - 기다리면 된다고 말한다`() {
        assertEquals(
            "아직 정원이 다 차지 않았어요. 인원이 모두 모이면 완료할 수 있어요",
            finishFailureMessage(failure(PartyFinishException.PARTY_NOT_COMPLETED)),
        )
    }

    @Test
    fun `기사가 배정됐다 - 직접 끝낼 방이 아니라고 말한다`() {
        assertEquals(
            "이미 기사가 배정된 방이라 직접 완료할 수 없어요",
            finishFailureMessage(failure(PartyFinishException.DRIVER_ALREADY_ASSIGNED)),
        )
    }

    @Test
    fun `사라진 방`() {
        assertEquals(
            "이미 사라진 방이에요. 홈으로 돌아가 주세요",
            finishFailureMessage(failure(PartyFinishException.PARTY_NOT_FOUND, httpStatus = 404)),
        )
    }

    /**
     * **서버가 코드를 늘려도 앱이 지어내면 안 된다.** 403·409 가 각각 두 가지 뜻이라 상태 코드만으로
     * 사유를 고르면 반드시 절반은 틀린 말이 된다 — 모르면 일반 문구로 떨어진다.
     */
    @Test
    fun `모르는 코드는 일반 문구로 떨어진다 - 상태 코드로 추측하지 않는다`() {
        assertEquals(general, finishFailureMessage(failure("PARTY_SOMETHING_NEW")))
        // 코드가 없는 403 을 「방장만」으로 추측하지 않는다.
        assertEquals(general, finishFailureMessage(failure(code = null, httpStatus = 403)))
        // 코드가 없는 409 를 「정원 미달」로 추측하지 않는다.
        assertEquals(general, finishFailureMessage(failure(code = null, httpStatus = 409)))
        assertEquals(general, finishFailureMessage(failure(code = null, httpStatus = 500)))
    }

    /** 응답을 못 받은 실패(연결 거부·타임아웃)는 서버가 말해 준 사유가 없다 — 기존 일반 문구 그대로다. */
    @Test
    fun `네트워크 실패는 기존 일반 문구를 유지한다`() {
        assertEquals(general, finishFailureMessage(failure(code = null, httpStatus = null)))
    }

    @Test
    fun `실패마다 다른 말을 한다 - 같은 문구가 두 갈래에 쓰이면 분기한 의미가 없다`() {
        val messages = listOf(
            failure(PartyFinishException.NOT_PARTY_HOST, httpStatus = 403),
            failure(PartyFinishException.NOT_PARTY_MEMBER, httpStatus = 403),
            failure(PartyFinishException.PARTY_NOT_COMPLETED),
            failure(PartyFinishException.DRIVER_ALREADY_ASSIGNED),
            failure(PartyFinishException.PARTY_NOT_FOUND, httpStatus = 404),
            failure(code = null, httpStatus = null),
        ).map(::finishFailureMessage)

        assertEquals(messages.size, messages.distinct().size)
    }

    /**
     * 지키지 못할 약속을 넣지 않는다. 이 저장소는 거짓 문구 19건을 들어낸 이력이 있고
     * (`ProductClaimsTest` KDoc), 여기서 특히 위험한 건 **재시도 안내**다 —
     * 403·409 는 다시 눌러도 같은 답이다. 방 사정(방장·정원·기사)이 바뀌어야 풀린다.
     */
    @Test
    fun `다시 누르면 된다고 약속하지 않는다`() {
        val allMessages = listOf(
            failure(PartyFinishException.NOT_PARTY_HOST, httpStatus = 403),
            failure(PartyFinishException.NOT_PARTY_MEMBER, httpStatus = 403),
            failure(PartyFinishException.PARTY_NOT_COMPLETED),
            failure(PartyFinishException.DRIVER_ALREADY_ASSIGNED),
            failure(PartyFinishException.PARTY_NOT_FOUND, httpStatus = 404),
            failure(code = null, httpStatus = null),
        ).map(::finishFailureMessage)

        listOf("잠시 후", "다시 시도", "다시 눌러").forEach { forbidden ->
            allMessages.forEach { message ->
                assertFalse("지키지 못할 약속이다: $message", message.contains(forbidden))
            }
        }
    }
}
