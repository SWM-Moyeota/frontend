package com.moyeota.presentation.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.moyeota.core.designsystem.component.MoyeotaTab
import com.moyeota.domain.model.ChatRoom
import com.moyeota.domain.model.ChatRoomStatus
import com.moyeota.domain.model.MyChatRoom
import com.moyeota.domain.repository.ChatRepository
import com.moyeota.domain.session.UserSession
import com.moyeota.presentation.core.BackStateScaffold
import com.moyeota.presentation.core.ErrorBox
import com.moyeota.presentation.core.LoadingBox
import com.moyeota.presentation.core.TabStateScaffold
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.moyeota.domain.model.ChatMessage as DomainChatMessage

// STOMP 미구현 — 실시간 수신 대신 getMessagesAfter(cursor) 를 이 주기로 폴링한다.
private const val POLL_INTERVAL_MS = 3_000L

class ChatListViewModel(
    private val repository: ChatRepository,
    private val userSession: UserSession,
) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data class Success(val rooms: List<MyChatRoom>) : UiState
        data class Error(val message: String) : UiState
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            _uiState.value = try {
                UiState.Success(repository.getMyChatRooms(userSession.currentUserId))
            } catch (e: Exception) {
                UiState.Error("채팅방 목록을 불러오지 못했어요")
            }
        }
    }

    companion object {
        fun factory(repository: ChatRepository, userSession: UserSession) = viewModelFactory {
            initializer { ChatListViewModel(repository, userSession) }
        }
    }
}

class ChatRoomViewModel(
    private val repository: ChatRepository,
    private val userSession: UserSession,
    private val chatRoomId: Long,
) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data class Success(val messages: List<DomainChatMessage>) : UiState
        data class Error(val message: String) : UiState
    }

    data class InputState(
        val text: String = "",
        val sending: Boolean = false,
        val errorMessage: String? = null,
    )

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _inputState = MutableStateFlow(InputState())
    val inputState: StateFlow<InputState> = _inputState.asStateFlow()

    private val _leftRoom = MutableStateFlow(false)
    val leftRoom: StateFlow<Boolean> = _leftRoom.asStateFlow()

    private var lastMessageId: Long? = null

    init {
        loadInitial()
        startPolling()
    }

    fun loadInitial() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val page = repository.getMessages(chatRoomId, userSession.currentUserId)
                // 서버 정렬을 신뢰하지 않고 id 오름차순으로 맞춘다 (커서 페이징은 최신부터 올 수 있다)
                val ordered = page.messages.sortedBy { it.id }
                _uiState.value = UiState.Success(ordered)
                markLastAsRead(ordered.lastOrNull()?.id)
            } catch (e: Exception) {
                _uiState.value = UiState.Error("대화를 불러오지 못했어요")
            }
        }
    }

    // 폴링 루프는 viewModelScope 에 매달아 화면을 벗어나면 자동으로 멈춘다.
    private fun startPolling() {
        viewModelScope.launch {
            while (true) {
                delay(POLL_INTERVAL_MS)
                val cursor = lastMessageId ?: continue
                val current = _uiState.value as? UiState.Success ?: continue
                // 폴링 실패는 화면을 깨지 않는다 — 다음 주기에 다시 시도한다
                runCatching { repository.getMessagesAfter(chatRoomId, userSession.currentUserId, cursor) }
                    .onSuccess { page ->
                        if (page.messages.isEmpty()) return@onSuccess
                        val merged = (current.messages + page.messages)
                            .distinctBy { it.id }
                            .sortedBy { it.id }
                        _uiState.value = UiState.Success(merged)
                        markLastAsRead(merged.lastOrNull()?.id)
                    }
            }
        }
    }

    fun onInputChange(text: String) {
        _inputState.update { it.copy(text = text, errorMessage = null) }
    }

    fun send() {
        val content = _inputState.value.text.trim()
        if (content.isEmpty() || _inputState.value.sending) return
        viewModelScope.launch {
            _inputState.update { it.copy(sending = true, errorMessage = null) }
            try {
                val sent = repository.sendMessage(chatRoomId, userSession.currentUserId, content)
                val current = _uiState.value as? UiState.Success
                if (current != null) {
                    val merged = (current.messages + sent).distinctBy { it.id }.sortedBy { it.id }
                    _uiState.value = UiState.Success(merged)
                    markLastAsRead(merged.lastOrNull()?.id)
                }
                _inputState.update { InputState() } // 성공 시에만 입력창을 비운다
            } catch (e: Exception) {
                _inputState.update { it.copy(sending = false, errorMessage = "메시지를 보내지 못했어요") }
            }
        }
    }

    // 채팅방 나가기(DELETE /chat-rooms/{id}/users). 실패하면 화면에 남기고 사유를 알린다.
    fun leaveRoom() {
        viewModelScope.launch {
            try {
                repository.leaveChatRoom(chatRoomId, userSession.currentUserId)
                _leftRoom.value = true
            } catch (e: Exception) {
                _inputState.update { it.copy(errorMessage = "채팅방에서 나가지 못했어요") }
            }
        }
    }

    // 읽음 처리 실패는 사용자에게 알리지 않는다 (배지 정확도보다 대화 흐름이 우선)
    private fun markLastAsRead(messageId: Long?) {
        val id = messageId ?: return
        if (id == lastMessageId) return
        lastMessageId = id
        viewModelScope.launch {
            runCatching { repository.markAsRead(chatRoomId, userSession.currentUserId, id) }
        }
    }

    companion object {
        fun factory(repository: ChatRepository, userSession: UserSession, chatRoomId: Long) = viewModelFactory {
            initializer { ChatRoomViewModel(repository, userSession, chatRoomId) }
        }
    }
}

// 24 채팅 — 방 목록에서 고르면 같은 라우트 안에서 방으로 전환한다.
@Composable
fun ChatRoute(
    repository: ChatRepository,
    userSession: UserSession,
    onOpenRideOngoing: () -> Unit = {},
    onStartLocationShare: () -> Unit = {},
    onLeaveChat: () -> Unit = {},
    onTabSelect: (MoyeotaTab) -> Unit = {},
) {
    var openedRoom by rememberSaveable(stateSaver = ChatRoomSaver) { mutableStateOf<ChatRoom?>(null) }

    val room = openedRoom
    if (room == null) {
        ChatListRoute(
            repository = repository,
            userSession = userSession,
            onRoomClick = { openedRoom = it.room },
            onTabSelect = onTabSelect,
        )
    } else {
        ChatRoomRoute(
            repository = repository,
            userSession = userSession,
            room = room,
            onBack = { openedRoom = null },
            onOpenRideOngoing = onOpenRideOngoing,
            onStartLocationShare = onStartLocationShare,
            onLeaveChat = onLeaveChat,
            onTabSelect = onTabSelect,
        )
    }
}

@Composable
private fun ChatListRoute(
    repository: ChatRepository,
    userSession: UserSession,
    onRoomClick: (MyChatRoom) -> Unit,
    onTabSelect: (MoyeotaTab) -> Unit,
) {
    val viewModel: ChatListViewModel = viewModel(factory = ChatListViewModel.factory(repository, userSession))
    val state by viewModel.uiState.collectAsState()

    // 방에 들어갔다 돌아오면 안읽음 배지가 갱신돼야 해서 매 진입마다 다시 읽는다
    LaunchedEffect(Unit) { viewModel.refresh() }

    // 로딩·에러에서도 하단탭 유지 (QA F-1)
    when (val current = state) {
        ChatListViewModel.UiState.Loading -> TabStateScaffold(MoyeotaTab.CHAT, onTabSelect) {
            LoadingBox()
        }
        is ChatListViewModel.UiState.Error -> TabStateScaffold(MoyeotaTab.CHAT, onTabSelect) {
            ErrorBox(message = current.message, onRetry = viewModel::refresh)
        }
        is ChatListViewModel.UiState.Success -> ChatListScreen(
            rooms = current.rooms,
            onRoomClick = onRoomClick,
            onTabSelect = onTabSelect,
        )
    }
}

@Composable
private fun ChatRoomRoute(
    repository: ChatRepository,
    userSession: UserSession,
    room: ChatRoom,
    onBack: () -> Unit,
    onOpenRideOngoing: () -> Unit,
    onStartLocationShare: () -> Unit,
    onLeaveChat: () -> Unit,
    onTabSelect: (MoyeotaTab) -> Unit,
) {
    val viewModel: ChatRoomViewModel = viewModel(
        key = "chat-room-${room.id}",
        factory = ChatRoomViewModel.factory(repository, userSession, room.id),
    )
    val state by viewModel.uiState.collectAsState()
    val input by viewModel.inputState.collectAsState()
    val leftRoom by viewModel.leftRoom.collectAsState()
    val myId = remember(userSession) { userSession.currentUserId }

    LaunchedEffect(leftRoom) {
        if (leftRoom) onLeaveChat()
    }

    // 방 화면의 이동 수단은 뒤로가기(목록 복귀) + 하단탭 둘 다다.
    // 대화를 못 불러와도 둘 다 남긴다 — Success 일 때의 골격과 같게 (QA F-1)
    val roomTitle = "${room.departure} → ${room.destination}"
    when (val current = state) {
        ChatRoomViewModel.UiState.Loading -> TabStateScaffold(MoyeotaTab.CHAT, onTabSelect) {
            BackStateScaffold(title = roomTitle, onBack = onBack) { LoadingBox() }
        }
        is ChatRoomViewModel.UiState.Error -> TabStateScaffold(MoyeotaTab.CHAT, onTabSelect) {
            BackStateScaffold(title = roomTitle, onBack = onBack) {
                ErrorBox(message = current.message, onRetry = viewModel::loadInitial)
            }
        }
        is ChatRoomViewModel.UiState.Success -> ChatScreen(
            roomTitle = roomTitle,
            roomSubtitle = "메시지 ${current.messages.size}개",
            hasOngoingRide = false,
            messages = current.messages.map { it.toUiMessage(myId) },
            input = input.text,
            sending = input.sending,
            errorMessage = input.errorMessage,
            onInputChange = viewModel::onInputChange,
            onSend = viewModel::send,
            onBack = onBack,
            onOpenRideOngoing = onOpenRideOngoing,
            onStartLocationShare = onStartLocationShare,
            onLeaveChat = viewModel::leaveRoom, // 서버에서 빠진 뒤 14 홈으로
            onTabSelect = onTabSelect,
        )
    }
}

// 서버 메시지 → 화면 표시 모델.
// 닉네임 API 가 없어 발신자는 "멤버 {id}" 로 표기한다 (01 보고서 플래그 7).
private fun DomainChatMessage.toUiMessage(currentUserId: Long): ChatUiMessage = ChatUiMessage(
    text = content,
    isMine = senderId == currentUserId,
    senderName = if (senderId == currentUserId) null else "멤버 $senderId",
    timeLabel = createdAt.toTimeLabel(),
)

// createdAt 은 ISO-8601 문자열. 표시용 HH:mm 만 잘라 쓴다(파싱 실패해도 화면은 살아야 한다).
private fun String.toTimeLabel(): String? {
    val time = substringAfter('T', missingDelimiterValue = "")
    return time.take(5).takeIf { it.length == 5 }
}

// 방 전환 상태를 프로세스 재생성 뒤에도 유지한다.
private val ChatRoomSaver = listSaver<ChatRoom?, Any>(
    save = { room ->
        if (room == null) {
            emptyList()
        } else {
            listOf(room.id, room.partyId, room.departure, room.destination, room.createdAt, room.status.name)
        }
    },
    restore = { values ->
        if (values.isEmpty()) {
            null
        } else {
            ChatRoom(
                id = values[0] as Long,
                partyId = values[1] as Long,
                departure = values[2] as String,
                destination = values[3] as String,
                createdAt = values[4] as String,
                status = ChatRoomStatus.valueOf(values[5] as String),
            )
        }
    },
)
