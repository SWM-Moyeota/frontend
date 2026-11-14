package com.moyeota.presentation.feature.chat

import com.moyeota.domain.model.ChatException
import com.moyeota.domain.model.PartyLeaveException
import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RideStatus
import com.moyeota.domain.model.RoomType
import com.moyeota.domain.model.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 채팅 목록 길게 누르기 — 「나가기」가 **무엇에서** 나가는지는 방 상태가 정한다(develop 규칙).
 *
 * - 끝났거나 취소된 방 → 채팅방만 나간다(목록 정리)
 * - 진행 중이고 도착 전 → 동승에서 나간다. 서버가 채팅방도 함께 정리한다
 * - 도착을 누른 뒤 → 서버가 409 `ARRIVED_MEMBER_CANNOT_LEAVE` 로 막는다. 누르기 전에 말한다
 */
class ChatListActionsTest {

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

    private fun ride(
        status: RideStatus,
        members: List<User>,
        roomType: RoomType = RoomType.REALTIME,
    ) = Ride(
        id = "42",
        origin = "강남역",
        destination = "서울시청",
        departureLabel = "지금 출발",
        capacity = 3,
        members = members,
        farePerPerson = 0,
        totalFare = 0,
        status = status,
        isConfirmed = status == RideStatus.MATCHED,
        roomType = roomType,
    )

    // ─────────────────────── 무엇에서 나가나 ───────────────────────

    @Test
    fun `방을 찾지 못하면 채팅방만 나간다`() {
        assertEquals(ChatLeavePlan.ChatOnly(chatRoomId = 7), chatLeavePlan(chatRoomId = 7, ride = null))
    }

    @Test
    fun `끝난 방은 채팅방만 나간다`() {
        val finished = ride(RideStatus.COMPLETED, listOf(member("me", isMe = true, arrived = true), member("b")))
        assertEquals(ChatLeavePlan.ChatOnly(chatRoomId = 7), chatLeavePlan(7, finished))
    }

    @Test
    fun `취소된 방은 채팅방만 나간다`() {
        val canceled = ride(RideStatus.CANCELED, listOf(member("me", isMe = true)))
        assertEquals(ChatLeavePlan.ChatOnly(chatRoomId = 7), chatLeavePlan(7, canceled))
    }

    @Test
    fun `이미 방 멤버가 아니면 채팅방만 나간다`() {
        val other = ride(RideStatus.RECRUITING, listOf(member("a", isHost = true), member("b")))
        assertEquals(ChatLeavePlan.ChatOnly(chatRoomId = 7), chatLeavePlan(7, other))
    }

    @Test
    fun `진행 중이고 도착 전이면 동승에서 나간다`() {
        val open = ride(RideStatus.RECRUITING, listOf(member("a", isHost = true), member("me", isMe = true)))
        assertEquals(
            ChatLeavePlan.LeaveParty(
                chatRoomId = 7,
                partyId = 42,
                isHost = false,
                alone = false,
                cancelsMeeting = false,
                capacity = 3,
                memberCount = 2,
            ),
            chatLeavePlan(7, open),
        )
    }

    @Test
    fun `예약 방도 도착 전이면 나갈 수 있다`() {
        val reservation = ride(
            RideStatus.RECRUITING,
            listOf(member("me", isMe = true, isHost = true), member("b")),
            roomType = RoomType.RESERVATION,
        )
        val plan = chatLeavePlan(7, reservation)
        assertTrue(plan is ChatLeavePlan.LeaveParty)
        assertTrue((plan as ChatLeavePlan.LeaveParty).isHost)
    }

    @Test
    fun `약속이 정해진 방도 도착 전이면 나갈 수 있다`() {
        val confirmed = ride(
            RideStatus.MATCHED,
            listOf(member("a", isHost = true), member("b"), member("me", isMe = true)),
        )
        val plan = chatLeavePlan(7, confirmed) as ChatLeavePlan.LeaveParty
        assertFalse(plan.cancelsMeeting)
    }

    @Test
    fun `약속이 정해진 둘뿐인 방에서 나가면 약속이 취소된다`() {
        val pair = ride(RideStatus.MATCHED, listOf(member("a", isHost = true), member("me", isMe = true)))
        val plan = chatLeavePlan(7, pair) as ChatLeavePlan.LeaveParty
        assertTrue(plan.cancelsMeeting)
    }

    @Test
    fun `혼자 있는 방이면 나가는 순간 방이 닫힌다`() {
        val alone = ride(RideStatus.RECRUITING, listOf(member("me", isMe = true, isHost = true)))
        val plan = chatLeavePlan(7, alone) as ChatLeavePlan.LeaveParty
        assertTrue(plan.alone)
    }

    @Test
    fun `도착을 누른 뒤에는 나갈 수 없다`() {
        val arrived = ride(
            RideStatus.MATCHED,
            listOf(member("a", isHost = true), member("me", isMe = true, arrived = true)),
        )
        assertTrue(chatLeavePlan(7, arrived) is ChatLeavePlan.Blocked)
    }

    // ─────────────────────── 확인창 문구 ───────────────────────

    private fun leave(isHost: Boolean = false, alone: Boolean = false, cancelsMeeting: Boolean = false) =
        ChatLeavePlan.LeaveParty(7, 42, isHost, alone, cancelsMeeting, capacity = 3, memberCount = 2)

    @Test
    fun `채팅방만 나갈 때는 되돌릴 수 없다고 먼저 말한다`() {
        val copy = chatLeaveDialog(ChatLeavePlan.ChatOnly(7))
        assertEquals("채팅방을 나갈까요?", copy.title)
        assertEquals("나가면 이 대화가 목록에서 사라지고 다시 들어올 수 없어요", copy.body)
        assertEquals("나가기", copy.confirmText)
        assertTrue(copy.destructive)
    }

    @Test
    fun `동승에서 나갈 때는 채팅방도 함께 사라진다고 말한다`() {
        val copy = chatLeaveDialog(leave())
        assertEquals("방에서 나갈까요?", copy.title)
        assertEquals("동승에서 빠지고 이 채팅방도 함께 사라져요", copy.body)
        assertTrue(copy.destructive)
    }

    @Test
    fun `방장이 나가면 방장이 넘어간다고 덧붙인다`() {
        val copy = chatLeaveDialog(leave(isHost = true))
        assertEquals("동승에서 빠지고 이 채팅방도 함께 사라져요\n방장은 남은 분 중 먼저 들어온 분에게 넘어가요", copy.body)
    }

    @Test
    fun `혼자 있는 방은 방이 닫힌다고만 말한다`() {
        val copy = chatLeaveDialog(leave(isHost = true, alone = true))
        assertEquals("혼자 있는 방이라 나가면 방이 닫혀요", copy.body)
    }

    @Test
    fun `둘뿐인 확정 방은 약속이 취소된다고 말한다`() {
        val copy = chatLeaveDialog(leave(isHost = true, cancelsMeeting = true))
        assertEquals("동승에서 빠지고 이 채팅방도 함께 사라져요\n남은 분 혼자가 되어 약속이 취소돼요", copy.body)
    }

    @Test
    fun `막힌 경우는 이유만 알리고 확인 하나로 닫는다`() {
        val copy = chatLeaveDialog(ChatLeavePlan.Blocked)
        assertEquals("지금은 나갈 수 없어요", copy.title)
        assertEquals("출발지에 도착했다고 누른 뒤에는 방에서 나갈 수 없어요", copy.body)
        assertEquals("확인", copy.confirmText)
        assertEquals(null, copy.dismissText)
        assertFalse(copy.destructive)
    }

    // ─────────────────────── 알림 ───────────────────────

    @Test
    fun `알림이 켜진 방의 메뉴는 끄기, 꺼진 방은 켜기`() {
        assertEquals("알림 끄기", chatMuteActionLabel(muted = false))
        assertEquals("알림 켜기", chatMuteActionLabel(muted = true))
    }

    // ─────────────────────── 실패 문구 ───────────────────────

    @Test
    fun `동승 나가기 중 방이 끝났으면 다시 판정하고 도착했으면 막는다`() {
        val closed = PartyLeaveException(code = "PARTY_CLOSED", serverMessage = null, httpStatus = 409)
        val arrived = PartyLeaveException(code = "ARRIVED_MEMBER_CANNOT_LEAVE", serverMessage = null, httpStatus = 409)
        assertEquals(ChatLeaveRetry.REPLAN, chatListLeaveRetry(leave(), closed))
        assertEquals(ChatLeaveRetry.BLOCKED, chatListLeaveRetry(leave(), arrived))
        assertEquals(ChatLeaveRetry.NONE, chatListLeaveRetry(leave(), IllegalStateException()))
    }

    @Test
    fun `채팅방만 나가기의 실패는 다시 판정하지 않는다`() {
        val closed = PartyLeaveException(code = "PARTY_CLOSED", serverMessage = null, httpStatus = 409)
        assertEquals(ChatLeaveRetry.NONE, chatListLeaveRetry(ChatLeavePlan.ChatOnly(7), closed))
    }

    @Test
    fun `채팅방 참여자가 아니거나 채팅방이 없다는 답은 이미 나간 것이다`() {
        assertTrue(isAlreadyOutOfChat(ChatException(code = "CHAT_NOT_PARTICIPANT", serverMessage = null, httpStatus = 403)))
        assertTrue(isAlreadyOutOfChat(ChatException(code = "CHAT_ROOM_NOT_FOUND", serverMessage = null, httpStatus = 404)))
        assertFalse(isAlreadyOutOfChat(ChatException(code = "CHAT_ROOM_CLOSED", serverMessage = null, httpStatus = 409)))
        assertFalse(isAlreadyOutOfChat(IllegalStateException()))
    }
}
