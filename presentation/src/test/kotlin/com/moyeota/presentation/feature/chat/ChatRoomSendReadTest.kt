package com.moyeota.presentation.feature.chat

import com.moyeota.domain.model.ChatMember
import com.moyeota.domain.model.ChatMessage
import com.moyeota.domain.model.ChatMessagePage
import com.moyeota.domain.model.ChatMessageType
import com.moyeota.domain.model.ChatRealtimeEvent
import com.moyeota.domain.model.ChatReportReason
import com.moyeota.domain.model.ChatRoom
import com.moyeota.domain.model.MyChatRoom
import com.moyeota.domain.repository.ChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 채팅 소켓 서버 분리 뒤의 채팅방 화면 — 보내기·읽음은 REST, 받기는 소켓.
 *
 * - 보내기: 누르는 즉시 일반 말풍선, 실패하면 「전송 실패」로 남기고 다음 메시지로 진행(재전송 없음)
 * - 읽음: 메시지마다 보내지 않고 약 3초에 한 번 묶어서, 화면을 벗어날 때는 바로
 * - 받기: seq 가 건너뛰면 `after` 로 빈 번호를 채운다(소켓 서버는 늦게 온 번호를 버린다)
 *
 * 보내는 **순서**(앞 응답 전에 다음이 안 나간다)는 저장소가 지킨다 — `RemoteChatRepositoryTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatRoomSendReadTest {

    private val main = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(main)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `보낸 메시지는 응답 전에 일반 말풍선으로 보이고 입력창이 비워진다`() = runTest(main) {
        val repository = FakeRoomRepository(initial = listOf(msg(100, seq = 10)))
        repository.sendHook = { content -> delay(5_000); msg(200, seq = 11, mine = true, content = content) }
        val viewModel = openRoom(repository)

        viewModel.onInputChange("곧 도착해요")
        viewModel.send()
        runCurrent()

        val pending = success(viewModel).pending.single()
        assertEquals("곧 도착해요", pending.content)
        assertFalse(pending.failed)
        assertEquals("", viewModel.inputState.value.text)
        assertEquals("보내는 중 표시는 없어야 한다", null, with(pending) { toUiMessage().meta })

        advanceTimeBy(5_001)
        runCurrent()
        val state = success(viewModel)
        assertTrue(state.pending.isEmpty())
        assertEquals(listOf(10L, 11L), state.messages.map { it.seq })
        viewModel.stopPolling()
    }

    @Test
    fun `실패하면 전송 실패로 남기고 다음 메시지로 넘어간다`() = runTest(main) {
        val repository = FakeRoomRepository(initial = listOf(msg(100, seq = 10)))
        repository.sendHook = { content ->
            delay(1_000)
            if (content == "첫째") throw IllegalStateException("timeout")
            msg(201, seq = 11, mine = true, content = content)
        }
        val viewModel = openRoom(repository)

        viewModel.onInputChange("첫째")
        viewModel.send()
        viewModel.onInputChange("둘째")
        viewModel.send()
        advanceTimeBy(2_500)
        runCurrent()

        val state = success(viewModel)
        val failed = state.pending.single()
        assertEquals("첫째", failed.content)
        assertTrue(failed.failed)
        assertEquals("전송 실패", failed.toUiMessage().meta)
        assertEquals(listOf("본문 100", "둘째"), state.messages.map { it.content })
        assertEquals("실패한 메시지를 자동으로 다시 보냈다", listOf("첫째", "둘째"), repository.sent)
        assertNotNull(viewModel.inputState.value.errorMessage)
        viewModel.stopPolling()
    }

    @Test
    fun `seq 10 11 13 을 받으면 11번 id 로 after 를 읽어 12 를 채운다`() = runTest(main) {
        val repository = FakeRoomRepository(initial = listOf(msg(100, seq = 10), msg(101, seq = 11)))
        repository.afterHook = { cursor ->
            if (cursor == 101L) listOf(msg(102, seq = 12), msg(103, seq = 13)) else emptyList()
        }
        val viewModel = openRoom(repository)

        repository.events.emit(ChatRealtimeEvent.Message(msg(103, seq = 13)))
        runCurrent()

        assertEquals(listOf(101L), repository.afterCursors)
        assertEquals(listOf(10L, 11L, 12L, 13L), success(viewModel).messages.map { it.seq })
        viewModel.stopPolling()
    }

    @Test
    fun `읽음은 메시지마다가 아니라 3초에 한 번 마지막 id 만 보낸다`() = runTest(main) {
        val repository = FakeRoomRepository(initial = listOf(msg(100, seq = 10)))
        val viewModel = openRoom(repository)

        repository.events.emit(ChatRealtimeEvent.Message(msg(101, seq = 11)))
        repository.events.emit(ChatRealtimeEvent.Message(msg(102, seq = 12)))
        repository.events.emit(ChatRealtimeEvent.Message(msg(103, seq = 13)))
        runCurrent()
        assertEquals("받을 때마다 읽음을 보냈다", emptyList<Long>(), repository.reads)

        advanceTimeBy(3_001)
        runCurrent()
        assertEquals(listOf(103L), repository.reads)

        advanceTimeBy(3_001)
        runCurrent()
        assertEquals("바뀐 게 없는데 또 보냈다", listOf(103L), repository.reads)
        viewModel.stopPolling()
    }

    @Test
    fun `화면을 벗어나면 3초를 기다리지 않고 바로 읽음을 보낸다`() = runTest(main) {
        val repository = FakeRoomRepository(initial = listOf(msg(100, seq = 10)))
        val viewModel = openRoom(repository)
        repository.events.emit(ChatRealtimeEvent.Message(msg(101, seq = 11)))
        runCurrent()

        viewModel.stopPolling()
        runCurrent()

        assertEquals(listOf(101L), repository.reads)
    }

    private fun kotlinx.coroutines.test.TestScope.openRoom(repository: FakeRoomRepository): ChatRoomViewModel {
        val viewModel = ChatRoomViewModel(repository)
        viewModel.onScreenStart(ROOM_ID, knownMuted = false)
        runCurrent()
        return viewModel
    }

    private fun success(viewModel: ChatRoomViewModel) =
        viewModel.uiState.value as ChatRoomViewModel.UiState.Success
}

private const val ROOM_ID = 5L

private fun msg(id: Long, seq: Long?, mine: Boolean = false, content: String = "본문 $id") = ChatMessage(
    id = id,
    chatRoomId = ROOM_ID,
    senderPublicId = if (mine) "me" else "peer",
    content = content,
    type = ChatMessageType.TEXT,
    createdAt = "2026-11-11T00:00:00Z",
    deleted = false,
    isMine = mine,
    senderName = if (mine) null else "동승자",
    seq = seq,
)

private class FakeRoomRepository(private val initial: List<ChatMessage>) : ChatRepository {
    val events = MutableSharedFlow<ChatRealtimeEvent>(extraBufferCapacity = 16)
    var sendHook: suspend (String) -> ChatMessage = { error("unexpected send") }
    var afterHook: (Long) -> List<ChatMessage> = { emptyList() }
    val sent = mutableListOf<String>()
    val reads = mutableListOf<Long>()
    val afterCursors = mutableListOf<Long>()

    override suspend fun getMyChatRooms(): List<MyChatRoom> = emptyList()
    override suspend fun getChatRoom(chatRoomId: Long): ChatRoom = error("unused")
    override suspend fun joinChatRoom(chatRoomId: Long) = Unit
    override suspend fun leaveChatRoom(chatRoomId: Long) = Unit
    override suspend fun getChatRoomMembers(chatRoomId: Long): List<ChatMember> = emptyList()
    override suspend fun setNotificationMuted(chatRoomId: Long, muted: Boolean) = Unit

    override suspend fun markAsRead(chatRoomId: Long, readMessageId: Long) {
        reads += readMessageId
    }

    override suspend fun getMessages(chatRoomId: Long, cursor: Long?, size: Int) =
        ChatMessagePage(messages = initial, nextCursor = null, hasNext = false)

    override fun observeRoom(chatRoomId: Long): Flow<ChatRealtimeEvent> = events

    override suspend fun getMessagesAfter(chatRoomId: Long, cursor: Long, size: Int): ChatMessagePage {
        afterCursors += cursor
        return ChatMessagePage(messages = afterHook(cursor), nextCursor = null, hasNext = false)
    }

    override suspend fun sendMessage(chatRoomId: Long, content: String): ChatMessage {
        sent += content
        return sendHook(content)
    }

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
