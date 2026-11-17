package com.moyeota.presentation.feature.chat

import com.moyeota.domain.analytics.AnalyticsEvent
import com.moyeota.domain.model.ChatException
import com.moyeota.domain.model.ChatMember
import com.moyeota.domain.model.ChatMessagePage
import com.moyeota.domain.model.ChatRealtimeEvent
import com.moyeota.domain.model.ChatReportReason
import com.moyeota.domain.model.ChatRoom
import com.moyeota.domain.model.ChatRoomMembership
import com.moyeota.domain.model.ChatRoomStatus
import com.moyeota.domain.model.MyChatRoom
import com.moyeota.domain.model.PartyLeaveException
import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RideStatus
import com.moyeota.domain.model.User
import com.moyeota.domain.repository.ChatRepository
import com.moyeota.presentation.analytics.AnalyticsRecorderRule
import com.moyeota.presentation.analytics.FakeRideRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 채팅 목록 길게 누르기 — 메뉴(알림 끄기/켜기 · 나가기) → 확인창 → 요청.
 *
 * 메뉴를 여는 순간 방 상태를 읽어 「나가기」의 뜻을 정해 둔다([ChatLeavePlan]). 진행 중인 방은 동승에서,
 * 끝난 방은 채팅방에서만 나간다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatListMenuTest {

    @get:Rule
    val analytics = AnalyticsRecorderRule(StandardTestDispatcher())

    private val chat = FakeListRepository()
    private val rides = FakeRideRepository()

    @Test
    fun `메뉴를 열면 방 상태를 미리 읽어 나가기가 바로 확인창으로 간다`() = runTest(analytics.dispatcher) {
        rides.detail = { ride(RideStatus.RECRUITING, me(), other()) }
        val viewModel = loaded()

        viewModel.openMenu(item(7))
        runCurrent()
        viewModel.requestLeave()

        val confirm = viewModel.action.value as ChatListViewModel.RoomAction.Confirm
        assertTrue(confirm.plan is ChatLeavePlan.LeaveParty)
    }

    @Test
    fun `판정 전에 나가기를 누르면 판정이 끝나는 대로 확인창으로 넘어간다`() = runTest(analytics.dispatcher) {
        val gate = CompletableDeferred<Unit>()
        chat.beforeGetRoom = { gate.await() }
        rides.detail = { ride(RideStatus.COMPLETED, me(arrived = true), other()) }
        val viewModel = loaded()

        viewModel.openMenu(item(7))
        runCurrent()
        viewModel.requestLeave()
        assertTrue((viewModel.action.value as ChatListViewModel.RoomAction.Menu).leaveTapped)

        gate.complete(Unit)
        runCurrent()
        val confirm = viewModel.action.value as ChatListViewModel.RoomAction.Confirm
        assertEquals(ChatLeavePlan.ChatOnly(7), confirm.plan)
    }

    @Test
    fun `진행 중인 방은 동승에서 나가고 목록에서 빠진다`() = runTest(analytics.dispatcher) {
        val leftParties = mutableListOf<Long>()
        rides.leave = { leftParties += it }
        rides.detail = { ride(RideStatus.MATCHED, me(), other(), other("c")) }
        val viewModel = loaded()
        val left = mutableListOf<ChatLeavePlan>()
        backgroundScope.launch { viewModel.left.collect { left += it } }

        leave(viewModel, item(7))

        assertEquals(listOf(42L), leftParties)
        assertTrue("채팅방만 나가는 요청은 보내지 않는다", chat.leftChatRooms.isEmpty())
        assertEquals(listOf(8L), rooms(viewModel).map { it.room.room.id })
        assertTrue(left.single() is ChatLeavePlan.LeaveParty)
        assertEquals(1, analytics.only<AnalyticsEvent.MatchCanceled>().size)
        assertNull(viewModel.action.value)
    }

    @Test
    fun `끝난 방은 채팅방만 나간다`() = runTest(analytics.dispatcher) {
        val leftParties = mutableListOf<Long>()
        rides.leave = { leftParties += it }
        rides.detail = { ride(RideStatus.COMPLETED, me(arrived = true), other()) }
        val viewModel = loaded()

        leave(viewModel, item(7))

        assertEquals(listOf(7L), chat.leftChatRooms)
        assertTrue(leftParties.isEmpty())
        assertTrue(analytics.only<AnalyticsEvent.MatchCanceled>().isEmpty())
        assertEquals(listOf(8L), rooms(viewModel).map { it.room.room.id })
    }

    @Test
    fun `채팅방 상세가 403 이면 채팅방만 정리한다`() = runTest(analytics.dispatcher) {
        chat.getRoomError = ChatException(code = "CHAT_NOT_PARTY_MEMBER", serverMessage = null, httpStatus = 403)
        val viewModel = loaded()

        leave(viewModel, item(7))

        assertEquals(listOf(7L), chat.leftChatRooms)
    }

    @Test
    fun `이미 나간 채팅방이라는 답은 성공으로 친다`() = runTest(analytics.dispatcher) {
        rides.detail = { ride(RideStatus.CANCELED, me()) }
        chat.leaveError = ChatException(code = "CHAT_NOT_PARTICIPANT", serverMessage = null, httpStatus = 403)
        val viewModel = loaded()

        leave(viewModel, item(7))

        assertEquals(listOf(8L), rooms(viewModel).map { it.room.room.id })
        assertNull(viewModel.notice.value)
    }

    @Test
    fun `도착한 뒤에는 확인창이 이유만 알리고 아무 요청도 보내지 않는다`() = runTest(analytics.dispatcher) {
        val leftParties = mutableListOf<Long>()
        rides.leave = { leftParties += it }
        rides.detail = { ride(RideStatus.MATCHED, me(arrived = true), other()) }
        val viewModel = loaded()

        leave(viewModel, item(7))

        assertTrue(leftParties.isEmpty())
        assertTrue(chat.leftChatRooms.isEmpty())
        assertEquals(listOf(8L, 7L), rooms(viewModel).map { it.room.room.id })
    }

    @Test
    fun `나가기가 실패하면 목록에 남기고 이유를 알린다`() = runTest(analytics.dispatcher) {
        rides.detail = { ride(RideStatus.RECRUITING, me(), other()) }
        rides.leave = { error("network") }
        val viewModel = loaded()

        leave(viewModel, item(7))

        assertEquals(listOf(8L, 7L), rooms(viewModel).map { it.room.room.id })
        assertEquals(LEAVE_PARTY_FAILED_MESSAGE, viewModel.notice.value)
        assertNull(viewModel.action.value)
    }

    @Test
    fun `그사이 방이 끝났으면 다시 판정해 채팅방만 나가기 확인창으로 간다`() = runTest(analytics.dispatcher) {
        var status = RideStatus.RECRUITING
        rides.detail = { ride(status, me(), other()) }
        rides.leave = {
            status = RideStatus.COMPLETED
            throw PartyLeaveException(code = "PARTY_CLOSED", serverMessage = null, httpStatus = 409)
        }
        val viewModel = loaded()

        leave(viewModel, item(7))

        val confirm = viewModel.action.value as ChatListViewModel.RoomAction.Confirm
        assertEquals(ChatLeavePlan.ChatOnly(7), confirm.plan)
        assertEquals("그사이 방이 끝났어요. 채팅방만 나갈지 다시 확인해 주세요", viewModel.notice.value)
        assertTrue(chat.leftChatRooms.isEmpty())
    }

    @Test
    fun `그사이 도착을 눌렀으면 막힌 이유를 확인창으로 보여 준다`() = runTest(analytics.dispatcher) {
        rides.detail = { ride(RideStatus.MATCHED, me(), other(), other("c")) }
        rides.leave = { throw PartyLeaveException(code = "ARRIVED_MEMBER_CANNOT_LEAVE", serverMessage = null, httpStatus = 409) }
        val viewModel = loaded()

        leave(viewModel, item(7))

        val confirm = viewModel.action.value as ChatListViewModel.RoomAction.Confirm
        assertEquals(ChatLeavePlan.Blocked, confirm.plan)
        assertTrue(analytics.only<AnalyticsEvent.MatchCanceled>().isEmpty())
    }

    @Test
    fun `채팅방이 없다는 답도 이미 나간 것으로 친다`() = runTest(analytics.dispatcher) {
        chat.getRoomError = ChatException(code = "CHAT_ROOM_NOT_FOUND", serverMessage = null, httpStatus = 404)
        chat.leaveError = ChatException(code = "CHAT_ROOM_NOT_FOUND", serverMessage = null, httpStatus = 404)
        val viewModel = loaded()

        leave(viewModel, item(7))

        assertEquals(listOf(8L), rooms(viewModel).map { it.room.room.id })
        assertNull(viewModel.notice.value)
    }

    @Test
    fun `나간 결과는 받는 화면이 그 순간 없어도 다시 붙으면 받는다`() = runTest(analytics.dispatcher) {
        rides.detail = { ride(RideStatus.RECRUITING, me(), other()) }
        val viewModel = loaded()

        leave(viewModel, item(7))
        val left = mutableListOf<ChatLeavePlan>()
        backgroundScope.launch { viewModel.left.collect { left += it } }
        runCurrent()

        assertTrue(left.single() is ChatLeavePlan.LeaveParty)
    }

    @Test
    fun `앞 방의 판정이 늦게 와도 다른 방 메뉴에 섞이지 않는다`() = runTest(analytics.dispatcher) {
        val gate = CompletableDeferred<Unit>()
        chat.beforeGetRoom = { gate.await() }
        rides.detail = { ride(RideStatus.RECRUITING, me(), other()) }
        val viewModel = loaded()

        viewModel.openMenu(item(7))
        runCurrent()
        viewModel.dismissAction()
        chat.beforeGetRoom = {}
        viewModel.openMenu(item(8))
        runCurrent()
        gate.complete(Unit)
        runCurrent()

        val menu = viewModel.action.value as ChatListViewModel.RoomAction.Menu
        assertEquals(8L, menu.item.room.room.id)
        assertEquals(8L, (menu.plan as ChatLeavePlan.LeaveParty).chatRoomId)
    }

    @Test
    fun `나가기를 누른 뒤 판정이 실패하면 메뉴를 닫고 알린다`() = runTest(analytics.dispatcher) {
        rides.detail = { error("network") }
        val viewModel = loaded()

        viewModel.openMenu(item(7))
        viewModel.requestLeave()
        runCurrent()

        assertNull(viewModel.action.value)
        assertEquals("방 상태를 확인하지 못했어요. 잠시 뒤 다시 시도해 주세요", viewModel.notice.value)
    }

    @Test
    fun `방 상태를 못 읽었으면 나가기를 누를 때 다시 읽는다`() = runTest(analytics.dispatcher) {
        var fail = true
        rides.detail = { if (fail) error("network") else ride(RideStatus.RECRUITING, me(), other()) }
        val viewModel = loaded()

        viewModel.openMenu(item(7))
        runCurrent()
        assertTrue((viewModel.action.value as ChatListViewModel.RoomAction.Menu).planFailed)

        fail = false
        viewModel.requestLeave()
        runCurrent()
        assertTrue(viewModel.action.value is ChatListViewModel.RoomAction.Confirm)
    }

    @Test
    fun `알림 끄기는 바로 목록에 그려지고 서버에 간다`() = runTest(analytics.dispatcher) {
        val viewModel = loaded()

        viewModel.openMenu(item(7))
        viewModel.toggleMuted(item(7))
        assertTrue(rooms(viewModel).first { it.room.room.id == 7L }.room.membership.notificationMuted)
        assertNull("메뉴는 닫힌다", viewModel.action.value)

        runCurrent()
        assertEquals(listOf(7L to true), chat.muteCalls)
    }

    @Test
    fun `알림 설정이 실패하면 되돌리고 알린다`() = runTest(analytics.dispatcher) {
        chat.muteError = IllegalStateException("network")
        val viewModel = loaded()

        viewModel.toggleMuted(item(7))
        runCurrent()

        assertFalse(rooms(viewModel).first { it.room.room.id == 7L }.room.membership.notificationMuted)
        assertEquals("알림 설정을 바꾸지 못했어요", viewModel.notice.value)
    }

    // ─────────────────────── 도우미 ───────────────────────

    private fun TestScope.loaded(): ChatListViewModel {
        val viewModel = ChatListViewModel(chat, rides)
        viewModel.refresh()
        runCurrent()
        return viewModel
    }

    /** 메뉴 → (판정) → 나가기 → 확인 */
    private fun TestScope.leave(viewModel: ChatListViewModel, item: ChatRoomListItem) {
        viewModel.openMenu(item)
        runCurrent()
        viewModel.requestLeave()
        viewModel.confirmLeave()
        runCurrent()
    }

    private fun rooms(viewModel: ChatListViewModel) =
        (viewModel.uiState.value as ChatListViewModel.UiState.Success).rooms

    private fun item(chatRoomId: Long) = ChatRoomListItem(room = myRoom(chatRoomId), peerTitle = "상대")

    private fun me(arrived: Boolean = false) = member("me", isMe = true, arrived = arrived)
    private fun other(id: String = "b") = member(id, isHost = id == "b")

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

    private fun ride(status: RideStatus, vararg members: User): Ride = Ride(
        id = "42",
        origin = "강남역",
        destination = "서울시청",
        departureLabel = "지금 출발",
        capacity = 3,
        members = members.toList(),
        farePerPerson = 0,
        totalFare = 0,
        status = status,
        isConfirmed = status == RideStatus.MATCHED,
    )
}

private fun myRoom(chatRoomId: Long, muted: Boolean = false) = MyChatRoom(
    room = ChatRoom(
        id = chatRoomId,
        partyId = 0,
        departure = "강남역",
        destination = "서울시청",
        createdAt = "2026-11-13T07:00:0${chatRoomId % 10}Z",
        status = ChatRoomStatus.ACTIVE,
    ),
    membership = ChatRoomMembership(
        chatRoomId = chatRoomId,
        lastReadMessageId = null,
        notificationMuted = muted,
        joinedAt = "2026-11-13T07:00:00Z",
        members = listOf(
            ChatMember(userId = null, publicId = "p-me", nickname = "나", imageUrl = null, active = true, isMe = true),
            ChatMember(userId = null, publicId = "p-b", nickname = "상대", imageUrl = null, active = true, isMe = false),
        ),
    ),
)

/** 목록 두 방(7·8). 채팅방 상세는 방 42 를 가리킨다 */
private class FakeListRepository : ChatRepository {
    var beforeGetRoom: suspend () -> Unit = {}
    var getRoomError: Exception? = null
    var leaveError: Exception? = null
    var muteError: Exception? = null
    val leftChatRooms = mutableListOf<Long>()
    val muteCalls = mutableListOf<Pair<Long, Boolean>>()

    override suspend fun getMyChatRooms(): List<MyChatRoom> = listOf(myRoom(8), myRoom(7))

    override suspend fun getChatRoom(chatRoomId: Long): ChatRoom {
        beforeGetRoom()
        getRoomError?.let { throw it }
        return myRoom(chatRoomId).room.copy(partyId = 42)
    }

    override suspend fun joinChatRoom(chatRoomId: Long) = Unit

    override suspend fun leaveChatRoom(chatRoomId: Long) {
        leaveError?.let { throw it }
        leftChatRooms += chatRoomId
    }

    override suspend fun getChatRoomMembers(chatRoomId: Long): List<ChatMember> = emptyList()

    override suspend fun setNotificationMuted(chatRoomId: Long, muted: Boolean) {
        muteError?.let { throw it }
        muteCalls += chatRoomId to muted
    }

    override suspend fun markAsRead(chatRoomId: Long, readMessageId: Long) = Unit
    override suspend fun getMessages(chatRoomId: Long, cursor: Long?, size: Int) =
        ChatMessagePage(messages = emptyList(), nextCursor = null, hasNext = false)
    override fun observeRoom(chatRoomId: Long): Flow<ChatRealtimeEvent> = emptyFlow()
    override suspend fun getMessagesAfter(chatRoomId: Long, cursor: Long, size: Int) =
        ChatMessagePage(messages = emptyList(), nextCursor = null, hasNext = false)
    override suspend fun sendMessage(chatRoomId: Long, content: String) = error("unused")
    override suspend fun deleteMessage(chatRoomId: Long, messageId: Long) = Unit
    override suspend fun searchMessages(chatRoomId: Long, keyword: String, cursor: Long?, size: Int) =
        ChatMessagePage(messages = emptyList(), nextCursor = null, hasNext = false)
    override suspend fun reportMessage(
        chatRoomId: Long,
        chatMessageId: Long,
        reason: ChatReportReason,
        description: String?,
    ) = Unit
}
