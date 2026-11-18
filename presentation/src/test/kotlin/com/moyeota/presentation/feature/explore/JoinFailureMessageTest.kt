package com.moyeota.presentation.feature.explore

import com.moyeota.domain.model.PartyJoinException
import com.moyeota.domain.model.PartyStatus
import com.moyeota.domain.model.RideStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 20 합류 확인의 실패 안내.
 *
 * 실패마다 **다음 행동이 다르다** — 가득 찬 방은 다른 방을 고르면 되고, 다른 방에 참여 중이면 그 방을
 * 먼저 정리해야 하고, 네트워크면 그냥 다시 누르면 된다. 예전에는 전부 "합류하지 못했어요" 하나였다.
 *
 * 그리고 하나는 **실패가 아니다**: [PartyJoinException.isAlreadyInThisParty] 는 서버에 내가 이미 그 방
 * 멤버로 들어가 있다는 뜻이라 성공과 같은 길로 가야 한다. 그걸 실패로 다루면 재시도도 409, 다른 방도 409 라
 * 30분 스윕 전까지 그 계정이 어떤 방에도 못 들어간다.
 */
class JoinFailureMessageTest {

    private fun failure(
        code: String?,
        httpStatus: Int? = 409,
        isNetwork: Boolean = false,
        joinedStatus: PartyStatus? = null,
    ) = PartyJoinException(
        code = code,
        serverMessage = null,
        httpStatus = httpStatus,
        isNetwork = isNetwork,
        joinedStatus = joinedStatus,
    )

    @Test
    fun `이미 이 방 멤버면 실패가 아니다 - 문구가 아니라 성공 경로로 가야 한다`() {
        val recovered = failure(
            code = PartyJoinException.ALREADY_JOINED_OTHER_PARTY,
            joinedStatus = PartyStatus(RideStatus.RECRUITING, currentMembers = 2),
        )

        assertTrue(recovered.isAlreadyInThisParty)
        // 「다른 방」과 섞이지 않는지 — 섞이면 복구 분기가 안내 문구로 떨어진다
        assertFalse(recovered.isAlreadyInAnotherParty)
    }

    @Test
    fun `가득 찬 방 - 다른 방을 찾으라고 말한다`() {
        assertEquals("방이 가득 찼어요. 다른 방을 찾아 주세요", joinFailureMessage(failure("PARTY_FULL")))
    }

    @Test
    fun `모집이 끝난 방`() {
        assertEquals("모집이 끝난 방이에요. 다른 방을 찾아 주세요", joinFailureMessage(failure("PARTY_CLOSED")))
    }

    @Test
    fun `사라진 방 - 코드가 없는 404 도 같게 다룬다`() {
        val expected = "사라진 방이에요. 목록을 다시 불러와 주세요"
        assertEquals(expected, joinFailureMessage(failure("PARTY_NOT_FOUND", httpStatus = 404)))
        assertEquals(expected, joinFailureMessage(failure(code = null, httpStatus = 404)))
    }

    @Test
    fun `확정 전 다른 방에 참여 중 - 그 방을 먼저 정리하라고 말한다`() {
        // 확인 결과 내가 이 방 멤버가 아니었을 때(또는 확인 자체가 실패했을 때) 여기로 떨어진다.
        val message = joinFailureMessage(
            failure(PartyJoinException.ALREADY_JOINED_OTHER_PARTY),
            blockingConfirmed = false,
        )
        assertEquals("이미 참여 중인 방이 있어요. 그 방에서 나온 뒤 합류해 주세요", message)
    }

    /**
     * 🔴 ★ 못 박는 것: **가벼운 지시로 무거운 선택을 시키지 않는다.** (근거가 바뀐 자리다)
     *
     * 옛 문구는 「출발지에서 「출발」을 눌러 그 방을 끝낸 뒤에 합류할 수 있어요」였고, 근거는
     * 「확정된 방은 나가기가 409 `CANNOT_LEAVE_CONFIRMED_PARTY` 라 **나올 수 없다**」였다.
     * **서버가 그 규칙과 코드를 함께 지웠다**(`b16551b7`) — 확정된 방에서도 나갈 수 있으므로
     * 「나올 수 없으니 출발로 끝내라」는 이제 거짓이다.
     *
     * **그래도 가르는 일 자체는 남는다.** 확정된 방에서 나가는 것은 「모집을 그만두는 것」과 무게가
     * 다르다 — 이미 정해진 약속을 버리는 것이고, 그 방은 모집이 닫혀 **다시 들어갈 수 없다**
     * (`PARTY_CLOSED`). 「그 방에서 나온 뒤 합류해 주세요」로 가볍게 시키면 사용자는 되돌릴 수 있는
     * 동작으로 읽고, 되돌릴 수 없다.
     *
     * 이 상태는 드물지 않다: 약속을 정해 둔 사용자가 17 탐색을 둘러보다 다른 방에 합류를 누르면
     * 바로 여기다.
     */
    @Test
    fun `약속이 정해진 방이 막고 있으면 되돌릴 수 없다는 것을 말한다`() {
        val message = joinFailureMessage(
            failure(PartyJoinException.ALREADY_JOINED_OTHER_PARTY),
            blockingConfirmed = true,
        )

        assertEquals("약속이 정해진 방에 참여 중이에요. 그 방에서 나오면 다시 들어갈 수 없어요", message)
        assertFalse("되돌릴 수 있는 동작처럼 가볍게 시킨다", message.contains("나온 뒤"))
        // 🔴 서버가 버린 규칙으로 안내하지 않는다 — 출발만이 그 방을 벗어나는 길은 아니다
        assertFalse("서버가 허용하는 나가기를 숨긴다: $message", message.contains("「출발」"))
    }

    /**
     * 막은 방을 못 찾았을 때(기억이 없고 채팅방 훑기로도 안 나옴). **단정하지 않는다** —
     * 틀릴 수 있는 지시보다 틀릴 수 없는 안내가 낫고, 그 방으로 보내면 방 화면이 사실을 말해 준다.
     */
    @Test
    fun `막은 방을 모르면 나가라고도 출발하라고도 하지 않는다`() {
        val message = joinFailureMessage(
            failure(PartyJoinException.ALREADY_JOINED_OTHER_PARTY),
            blockingConfirmed = null,
        )

        assertEquals("이미 참여 중인 방이 있어요. 홈에서 진행 중인 방을 확인해 주세요", message)
        assertFalse(message.contains("나온 뒤"))
        assertFalse(message.contains("출발"))
    }

    /** 세 경우가 서로 다른 말을 한다 — 같은 문구로 뭉개면 가린 의미가 사라진다. */
    @Test
    fun `막은 방의 사정마다 다른 말을 한다`() {
        val messages = listOf(true, false, null).map {
            joinFailureMessage(failure(PartyJoinException.ALREADY_JOINED_OTHER_PARTY), it)
        }

        assertEquals(messages.toString(), 3, messages.distinct().size)
    }

    @Test
    fun `이미 참여한 방 코드 - 확인은 못 했지만 재시도를 권하지는 않는다`() {
        // 서버에 있지만 join 경로에서는 사실상 오지 않는 코드(ALREADY_JOINED_OTHER_PARTY 에 가린다).
        // 와도 "다시 시도"로 떨어지지 않게 자리를 만들어 둔다 — 다시 눌러도 같은 409 다.
        val message = joinFailureMessage(failure(PartyJoinException.ALREADY_JOINED_PARTY))
        assertEquals("이미 참여 중이에요. 홈에서 진행 중인 방을 확인해 주세요", message)
    }

    @Test
    fun `네트워크 실패 - 연결을 확인하고 다시 시도하라고 말한다`() {
        val message = joinFailureMessage(failure(code = null, httpStatus = null, isNetwork = true))
        assertEquals("네트워크가 불안정해요. 연결을 확인하고 다시 시도해 주세요", message)
    }

    @Test
    fun `원인을 가릴 수 없으면 재시도 안내로 떨어진다`() {
        assertEquals(
            "합류하지 못했어요. 잠시 후 다시 시도해 주세요",
            joinFailureMessage(failure("NOT_PARTY_MEMBER", httpStatus = 403)),
        )
        assertEquals(
            "합류하지 못했어요. 잠시 후 다시 시도해 주세요",
            joinFailureMessage(failure(code = null, httpStatus = 500)),
        )
    }

    @Test
    fun `실패마다 다른 말을 한다 - 같은 문구가 두 갈래에 쓰이면 분기한 의미가 없다`() {
        val messages = listOf(
            failure("PARTY_FULL"),
            failure("PARTY_CLOSED"),
            failure("PARTY_NOT_FOUND", httpStatus = 404),
            failure(PartyJoinException.ALREADY_JOINED_OTHER_PARTY),
            failure(PartyJoinException.ALREADY_JOINED_PARTY),
            failure(code = null, httpStatus = null, isNetwork = true),
        ).map(::joinFailureMessage)

        assertEquals(messages.size, messages.distinct().size)
    }
}
