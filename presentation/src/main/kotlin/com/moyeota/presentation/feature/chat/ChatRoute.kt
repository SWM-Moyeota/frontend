package com.moyeota.presentation.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.viewmodel.viewModelFactory
import com.moyeota.core.designsystem.component.MoyeotaTab
import com.moyeota.domain.model.ChatException
import com.moyeota.domain.model.ChatRoom
import com.moyeota.domain.model.ChatRoomStatus
import com.moyeota.domain.model.MyChatRoom
import com.moyeota.domain.repository.ChatRepository
import com.moyeota.presentation.core.BackStateScaffold
import com.moyeota.presentation.core.ErrorBox
import com.moyeota.presentation.core.LoadingBox
import com.moyeota.presentation.core.TabStateScaffold
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import com.moyeota.domain.model.ChatMessage as DomainChatMessage

// STOMP 미구현 — 실시간 수신 대신 getMessagesAfter(cursor) 를 이 주기로 폴링한다.
private const val POLL_INTERVAL_MS = 3_000L

// 발신자 닉네임이 없을 때 상대 말풍선에 붙이는 표기.
private const val PEER_FALLBACK_NAME = "동승자"

class ChatListViewModel(
    private val repository: ChatRepository,
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
                UiState.Success(repository.getMyChatRooms())
            } catch (e: Exception) {
                UiState.Error(e.toChatMessage("채팅방 목록을 불러오지 못했어요"))
            }
        }
    }

    companion object {
        fun factory(repository: ChatRepository) = viewModelFactory {
            initializer { ChatListViewModel(repository) }
        }
    }
}

/**
 * 방 하나의 대화 상태. **한 인스턴스가 방을 갈아탄다** — 방마다 새 ViewModel 을 만들면
 * 채팅 탭 엔트리의 ViewModelStore 에 계속 쌓이고 각자 폴링을 돌린다(QA 결함-2).
 *
 * 폴링은 [onScreenStart]/[stopPolling] 로만 돌고 멈춘다 — 화면이 보이지 않는 동안(탭 이동·백그라운드·
 * 로그아웃 뒤 남은 엔트리) 서버를 두드리지 않게 하기 위해서다(QA 결함-1).
 */
class ChatRoomViewModel(
    private val repository: ChatRepository,
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
    private var chatRoomId: Long? = null
    private var pollingJob: Job? = null
    private var loadJob: Job? = null

    /**
     * 화면이 보이기 시작할 때 호출한다(최초 진입·뒤로 돌아옴·앱 복귀·다른 방으로 전환).
     * 방이 바뀌었으면 상태를 갈아엎고, 같은 방이면 조용히 다시 읽는다 —
     * 재진입 시 재조회가 없으면 내가 보낸 뒤 학습된 「내 메시지」 판정이 교정되지 않는다(QA 결함-2).
     */
    fun onScreenStart(roomId: Long) {
        val roomChanged = chatRoomId != roomId
        if (roomChanged) {
            stopPolling()
            loadJob?.cancel()
            chatRoomId = roomId
            lastMessageId = null
            _uiState.value = UiState.Loading
            _inputState.value = InputState()
        }
        // 나가기 신호는 화면이 다시 열릴 때마다 내린다 — 남겨두면 재진입 즉시 또 나가진 것처럼 튕긴다.
        _leftRoom.value = false
        // 이미 대화를 그리고 있으면 스피너로 되돌리지 않는다(깜빡임 방지)
        load(showLoading = roomChanged || _uiState.value !is UiState.Success)
        startPolling()
    }

    fun retryLoad() {
        load(showLoading = true)
    }

    private fun load(showLoading: Boolean) {
        val roomId = chatRoomId ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (showLoading) _uiState.value = UiState.Loading
            try {
                val page = repository.getMessages(roomId)
                // 서버 정렬을 신뢰하지 않고 id 오름차순으로 맞춘다 (커서 페이징은 최신부터 올 수 있다)
                val ordered = page.messages.sortedBy { it.id }
                _uiState.value = UiState.Success(ordered)
                markLastAsRead(ordered.lastOrNull()?.id)
            } catch (e: Exception) {
                // 이미 대화가 떠 있는데(조용한 재조회) 실패하면 화면을 에러로 갈아치우지 않는다
                if (showLoading || _uiState.value !is UiState.Success) {
                    _uiState.value = UiState.Error(e.toChatMessage("대화를 불러오지 못했어요"))
                }
            }
        }
    }

    /** 화면이 가려지면(탭 이동·백그라운드·이탈) 폴링을 멈춘다. 멈추지 않으면 로그아웃 뒤에도 계속 쏜다. */
    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            while (true) {
                delay(POLL_INTERVAL_MS)
                val roomId = chatRoomId ?: continue
                val cursor = lastMessageId ?: continue
                val current = _uiState.value as? UiState.Success ?: continue
                // 폴링 실패는 화면을 깨지 않는다 — 다음 주기에 다시 시도한다
                runCatching { repository.getMessagesAfter(roomId, cursor) }
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
        val roomId = chatRoomId ?: return
        val content = _inputState.value.text.trim()
        if (content.isEmpty() || _inputState.value.sending) return
        viewModelScope.launch {
            _inputState.update { it.copy(sending = true, errorMessage = null) }
            try {
                val sent = repository.sendMessage(roomId, content)
                val current = _uiState.value as? UiState.Success
                if (current != null) {
                    val merged = (current.messages + sent).distinctBy { it.id }.sortedBy { it.id }
                    _uiState.value = UiState.Success(merged)
                    markLastAsRead(merged.lastOrNull()?.id)
                }
                _inputState.update { InputState() } // 성공 시에만 입력창을 비운다
            } catch (e: Exception) {
                _inputState.update {
                    it.copy(sending = false, errorMessage = e.toChatMessage("메시지를 보내지 못했어요"))
                }
            }
        }
    }

    // 채팅방 나가기(DELETE /chat-rooms/{id}/users). 실패하면 화면에 남기고 사유를 알린다.
    fun leaveRoom() {
        val roomId = chatRoomId ?: return
        viewModelScope.launch {
            try {
                repository.leaveChatRoom(roomId)
                _leftRoom.value = true
            } catch (e: Exception) {
                _inputState.update { it.copy(errorMessage = e.toChatMessage("채팅방에서 나가지 못했어요")) }
            }
        }
    }

    // 읽음 처리 실패는 사용자에게 알리지 않는다 (배지 정확도보다 대화 흐름이 우선)
    private fun markLastAsRead(messageId: Long?) {
        val roomId = chatRoomId ?: return
        val id = messageId ?: return
        if (id == lastMessageId) return
        lastMessageId = id
        viewModelScope.launch {
            runCatching { repository.markAsRead(roomId, id) }
        }
    }

    companion object {
        fun factory(repository: ChatRepository) = viewModelFactory {
            initializer { ChatRoomViewModel(repository) }
        }
    }
}

// 24 채팅 — 방 목록에서 고르면 같은 라우트 안에서 방으로 전환한다.
@Composable
fun ChatRoute(
    repository: ChatRepository,
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
            onRoomClick = { openedRoom = it.room },
            onTabSelect = onTabSelect,
        )
    } else {
        ChatRoomRoute(
            repository = repository,
            room = room,
            onBack = { openedRoom = null },
            onOpenRideOngoing = onOpenRideOngoing,
            onStartLocationShare = onStartLocationShare,
            // 나간 방을 열어둔 채로 홈에 보내면, 채팅 탭에 돌아왔을 때 참여자가 아닌 방을 다시 연다.
            onLeaveChat = {
                openedRoom = null
                onLeaveChat()
            },
            onTabSelect = onTabSelect,
        )
    }
}

@Composable
private fun ChatListRoute(
    repository: ChatRepository,
    onRoomClick: (MyChatRoom) -> Unit,
    onTabSelect: (MoyeotaTab) -> Unit,
) {
    val viewModel: ChatListViewModel = viewModel(factory = ChatListViewModel.factory(repository))
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
    room: ChatRoom,
    onBack: () -> Unit,
    onOpenRideOngoing: () -> Unit,
    onStartLocationShare: () -> Unit,
    onLeaveChat: () -> Unit,
    onTabSelect: (MoyeotaTab) -> Unit,
) {
    // 방이 바뀌어도 인스턴스는 하나다 — 방별 key 로 만들면 스토어에 쌓여 각자 폴링한다(QA 결함-2).
    val viewModel: ChatRoomViewModel = viewModel(
        key = "chat-room",
        factory = ChatRoomViewModel.factory(repository),
    )
    val state by viewModel.uiState.collectAsState()
    val input by viewModel.inputState.collectAsState()
    val leftRoom by viewModel.leftRoom.collectAsState()

    // 화면이 보이는 동안만 조회·폴링한다. 탭을 옮기거나 앱이 백그라운드로 가면 즉시 멈춘다.
    LifecycleStartEffect(room.id) {
        viewModel.onScreenStart(room.id)
        onStopOrDispose { viewModel.stopPolling() }
    }

    LaunchedEffect(leftRoom) {
        if (leftRoom) onLeaveChat()
    }

    // 방 화면의 이동 수단은 뒤로가기(목록 복귀) + 하단탭 둘 다다.
    // 대화를 못 불러와도 둘 다 남긴다 — Success 일 때의 골격과 같게 (QA F-1)
    // 제목·부제는 서버 ChatRoom 실값(출발지 → 목적지)이다. ChatScreen 의 기본값("서면역 동승")은 Preview 전용.
    val roomTitle = "${room.departure} → ${room.destination}"
    when (val current = state) {
        ChatRoomViewModel.UiState.Loading -> TabStateScaffold(MoyeotaTab.CHAT, onTabSelect) {
            BackStateScaffold(title = roomTitle, onBack = onBack) { LoadingBox() }
        }
        is ChatRoomViewModel.UiState.Error -> TabStateScaffold(MoyeotaTab.CHAT, onTabSelect) {
            BackStateScaffold(title = roomTitle, onBack = onBack) {
                ErrorBox(message = current.message, onRetry = viewModel::retryLoad)
            }
        }
        is ChatRoomViewModel.UiState.Success -> ChatScreen(
            roomTitle = roomTitle,
            roomSubtitle = "메시지 ${current.messages.size}개",
            hasOngoingRide = false,
            messages = current.messages.map { it.toUiMessage() },
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
// "내 메시지" 판정은 Repository 가 계산한 isMine 을 그대로 믿는다(토큰 주체 기준).
// 발신자 닉네임이 아직 없는 응답은 "동승자" 로 표기한다.
private fun DomainChatMessage.toUiMessage(): ChatUiMessage = ChatUiMessage(
    text = content,
    isMine = isMine,
    senderName = if (isMine) null else (senderName ?: PEER_FALLBACK_NAME),
    timeLabel = createdAt.toTimeLabel(),
)

// 채팅 서버 실패 → 사용자 문구. 코드 문자열 비교는 도메인(ChatException)에 두고
// 화면은 의미만 본다. 매핑이 없는 실패는 호출부의 기본 문구를 쓴다.
private fun Throwable.toChatMessage(fallback: String): String {
    val chat = this as? ChatException ?: return fallback
    return when {
        chat.isNotParticipant -> "이 채팅방에 참여하고 있지 않아요"
        chat.isRoomClosed -> "종료된 채팅방이에요"
        else -> fallback
    }
}

// createdAt 은 서버가 UTC 기준으로 내려주는 ISO-8601 문자열이다.
// 문자열을 그대로 자르면 KST 17:36 이 08:36 으로 보인다(QA 결함-3) — 기기 시간대로 변환해 HH:mm 만 쓴다.
// 파싱할 수 없는 형식(오프셋 없는 LocalDateTime 등)이면 예전처럼 잘라 쓴다 — 화면은 살아야 한다.
internal fun String.toTimeLabel(zone: ZoneId = ZoneId.systemDefault()): String? {
    val instant = runCatching { Instant.parse(this) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(this).toInstant() }.getOrNull()
    if (instant != null) {
        val local = instant.atZone(zone).toLocalTime()
        return "%02d:%02d".format(local.hour, local.minute)
    }
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
