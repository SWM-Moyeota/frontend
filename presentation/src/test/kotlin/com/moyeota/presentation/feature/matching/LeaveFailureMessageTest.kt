package com.moyeota.presentation.feature.matching

import com.moyeota.domain.model.PartyLeaveException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 21 「그만 찾기」·「방 나가기」의 실패 안내.
 *
 * 예전에는 모든 실패가 「방에서 나가지 못했어요」 하나였다. 그게 특히 나쁜 까닭은 **이미 나간
 * 상태가 영구 실패로 보였다**는 것이다: 서버가 204 로 처리했는데 응답이 유실되면(터널·타임아웃)
 * 앱은 그 문구를 띄우고, 다시 누르면 403 `NOT_PARTY_MEMBER` → **같은 문구**다. 로컬 기억과 홈 배너는
 * 그 방을 계속 들고 있으니 화면상 나는 아직 그 방에 있고, 벗어날 길이 없다.
 *
 * 그래서 **그 403 은 이 함수에 오지 않는다** — 데이터 계층이 성공으로 되돌려 나가기 성공과 같은
 * 길(홈)로 보낸다(`RemoteRideRepository.leaveParty`, 합류의 409 복구와 대칭). 여기 남는 것은
 * 「지금은 나갈 수 없다」와 「이미 사라진 방」뿐이고, 둘은 다음 행동이 다르다.
 */
class LeaveFailureMessageTest {

    private fun failure(code: String?, httpStatus: Int? = 409) = PartyLeaveException(
        code = code,
        serverMessage = null,
        httpStatus = httpStatus,
    )

    /**
     * 409 `PARTY_NOT_RECRUITING` — 서버가 막는다(`Party.ensureRecruiting`). **「잠시 후 다시」를 붙이지
     * 않는다**: 방이 모집 단계로 되돌아가는 길이 없어 다시 눌러도 같은 409 다. 대신 **왜** 못 나가는지
     * 말한다.
     *
     * 문구가 「매칭이 시작돼 지금은 나갈 수 없어요」였던 것은 **택시 시절**의 사실이다. 서버의
     * `isRecruiting()` 은 `ACTIVE`·`COMPLETED` 둘 다 참이라, 기사 배차 단계가 사라진 뒤로 이 409 가
     * 올 수 있는 경우는 **방이 이미 닫힌 것**(FINISHED·CANCELED)뿐이다. 옛 문구는 일어날 수 없는
     * 일을 기다리라고 말하는 셈이었다.
     */
    @Test
    fun `모집 단계가 아니면 왜 못 나가는지 말한다`() {
        val message = leaveFailureMessage(failure(PartyLeaveException.PARTY_NOT_RECRUITING))

        assertEquals("이미 끝난 방이에요. 홈으로 돌아가 주세요", message)
    }

    /**
     * 409 `CANNOT_LEAVE_CONFIRMED_PARTY` — 약속이 정해진 방이다. 예약 방은 합류 순간부터,
     * 실시간 방은 방장이 약속 시간을 확정한 뒤부터 이 답이 온다.
     *
     * 못 박는 것: **[PartyLeaveException.PARTY_NOT_RECRUITING] 과 다른 말을 한다.** 같은 409 지만
     * 그쪽은 이미 끝난 방이라 홈으로 보내는 게 맞고, 이쪽은 **아직 살아 있는 방**이다 — 홈으로
     * 돌아가라고 하면 사용자는 끝났다고 믿고 안 가고, 그대로 노쇼로 남는다. 끝내는 길이 「출발」
     * 하나뿐이므로 어디로 가야 하는지까지 말해 준다.
     */
    @Test
    fun `약속이 정해진 방은 끝난 방과 다른 말을 한다`() {
        val confirmed = leaveFailureMessage(failure(PartyLeaveException.CANNOT_LEAVE_CONFIRMED_PARTY))

        assertEquals("약속이 정해진 방은 나갈 수 없어요. 약속 시간에 출발지에서 만나 주세요", confirmed)
        assertNotEquals(
            "끝난 방과 확정된 방을 같은 문구로 뭉갰다 — 전자는 홈, 후자는 약속 장소다",
            leaveFailureMessage(failure(PartyLeaveException.PARTY_NOT_RECRUITING)),
            confirmed,
        )
    }

    @Test
    fun `사라진 방 - 코드가 없는 404 도 같게 다룬다`() {
        val expected = "이미 사라진 방이에요. 홈으로 돌아가 주세요"

        assertEquals(expected, leaveFailureMessage(failure(PartyLeaveException.PARTY_NOT_FOUND, 404)))
        assertEquals(expected, leaveFailureMessage(failure(code = null, httpStatus = 404)))
    }

    /**
     * 모르는 코드·네트워크 실패는 일반 문구다 — 서버가 `MatchingErrorCode` 를 늘려도 앱이 틀린 말을
     * 하지 않는다(409 라고 「매칭이 시작됐어요」로 **추측하지 않는다**).
     */
    @Test
    fun `모르는 코드와 네트워크 실패는 일반 문구로 떨어진다`() {
        val general = "방에서 나가지 못했어요"

        assertEquals(general, leaveFailureMessage(failure("PARTY_SOMETHING_NEW")))
        assertEquals(general, leaveFailureMessage(failure(code = null, httpStatus = null)))
        assertEquals(general, leaveFailureMessage(failure(code = null, httpStatus = 409)))
        assertEquals(general, leaveFailureMessage(failure(code = null, httpStatus = 500)))
    }

    /**
     * **403 이 일반 문구로 떨어지는 것 자체는 괜찮다** — 거기까지 오지 않는 것이 계약이기 때문이다
     * (데이터 계층이 성공으로 되돌린다). 혹시 되돌림이 빠져 올라오더라도 「매칭이 시작됐어요」 같은
     * 거짓말은 하지 않는다는 것만 못 박는다.
     */
    @Test
    fun `멤버 아님 403 은 사유를 지어내지 않는다`() {
        val message = leaveFailureMessage(failure(PartyLeaveException.NOT_PARTY_MEMBER, httpStatus = 403))

        assertEquals("방에서 나가지 못했어요", message)
    }

    @Test
    fun `실패마다 다른 말을 한다`() {
        val messages = listOf(
            failure(PartyLeaveException.PARTY_NOT_RECRUITING),
            failure(PartyLeaveException.CANNOT_LEAVE_CONFIRMED_PARTY),
            failure(PartyLeaveException.PARTY_NOT_FOUND, 404),
            failure(code = null, httpStatus = null),
        ).map(::leaveFailureMessage)

        assertEquals(messages.size, messages.distinct().size)
    }
}
