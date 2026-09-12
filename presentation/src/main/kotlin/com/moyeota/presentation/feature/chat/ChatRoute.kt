package com.moyeota.presentation.feature.chat

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.moyeota.domain.model.ChatMember
import com.moyeota.domain.model.ChatRoom
import com.moyeota.domain.model.ChatRoomStatus
import com.moyeota.domain.model.MyChatRoom
import com.moyeota.domain.repository.ChatRepository
import com.moyeota.presentation.core.BackStateScaffold
import com.moyeota.presentation.core.ErrorBox
import com.moyeota.presentation.core.LoadingBox
import com.moyeota.presentation.core.TabStateScaffold
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

/**
 * 목록 한 줄. 서버 `GET /chat-rooms/me` 가 주지 않는 것(참여자)을 방별로 덧붙인 형태다.
 *
 * @param peerTitle 나를 뺀 참여자 닉네임으로 지은 제목. **참여자 조회에 실패하면 null** —
 *   그때 화면은 예전처럼 「출발지 → 도착지」를 제목으로 쓴다(방 한 칸이 통째로 깨지지 않게).
 */
data class ChatRoomListItem(
    val room: MyChatRoom,
    val peerTitle: String?,
)

/**
 * 채팅방 표시 이름 — **나를 뺀 참여자 닉네임**.
 *
 * 서버는 방에 이름을 붙여 주지 않아 앱이 짓는다. 예전 이름이었던 「출발지 → 도착지」는
 * 역지오코딩된 전체 주소가 그대로 들어와("부산광역시 부산진구 중앙대로 730 서면역 → …")
 * 목록에서 어느 방인지 가려내는 데 쓸 수가 없었다 — 사람 이름이 그 일을 한다.
 * 경로는 부제로 내려간다(사라지지 않는다).
 *
 * 방을 나간 사람(active=false)은 제외하되, 그래서 아무도 안 남으면 **나간 사람이라도 쓴다** —
 * 대화 상대가 분명히 있었던 방을 「동승자 없음」으로 부르는 것보다 낫다.
 */
internal fun chatRoomPeerTitle(members: List<ChatMember>): String {
    val peers = members.filter { !it.isMe }
    val visible = peers.filter { it.active }.ifEmpty { peers }
    val names = visible.map { it.nickname.ifBlank { PEER_FALLBACK_NAME } }
    return when {
        names.isEmpty() -> "동승자 없음"
        names.size <= 2 -> names.joinToString(" · ")
        // 셋 이상은 다 적으면 한 줄을 넘긴다 — 첫 사람 + 나머지 수
        else -> "${names.first()} 외 ${names.size - 1}명"
    }
}

/**
 * 목록 정렬 키(**내림차순** = 최근 방이 위) — **마지막 활동 시각**(epoch ms).
 *
 * 마지막 메시지가 있으면 그 시각, 없으면(방금 열린 방) 방 개설 시각이다. 카카오톡식 「최근에 대화한 방」
 * 정렬이며, 메시지가 없는 새 방은 개설 시각으로 자연스럽게 끼어든다. 두 시각 다 파싱이 안 되는
 * 깨진 응답은 방 id 로 떨어진다(epoch 와 자릿수가 달라 맨 아래로 가지만 목록이 죽진 않는다).
 */
internal fun chatRoomSortKey(item: MyChatRoom): Long {
    val stamp = item.membership.lastMessage?.createdAt?.takeIf { it.isNotBlank() } ?: item.room.createdAt
    return stamp.toEpochMillisOrNull() ?: item.room.id
}

class ChatListViewModel(
    private val repository: ChatRepository,
) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data class Success(val rooms: List<ChatRoomListItem>) : UiState
        data class Error(val message: String) : UiState
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            _uiState.value = try {
                UiState.Success(loadRooms())
            } catch (e: Exception) {
                UiState.Error(e.toChatMessage("채팅방 목록을 불러오지 못했어요"))
            }
        }
    }

    /**
     * 목록 + 방마다 참여자 1회. **N+1 이지만 병렬로 돈다** — 방은 사람당 많아야 몇 개고,
     * 순차로 돌면 방 수만큼 왕복이 쌓여 목록이 눈에 띄게 늦게 뜬다.
     *
     * 참여자 조회 실패는 **그 방만** null 로 떨어뜨린다(목록 전체를 에러로 만들지 않는다).
     */
    private suspend fun loadRooms(): List<ChatRoomListItem> {
        val rooms = repository.getMyChatRooms().sortedByDescending(::chatRoomSortKey)
        return coroutineScope {
            rooms.map { room ->
                async {
                    val title = runCatching { repository.getChatRoomMembers(room.room.id) }
                        .getOrNull()
                        ?.let(::chatRoomPeerTitle)
                    ChatRoomListItem(room = room, peerTitle = title)
                }
            }.awaitAll()
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
        /** @param notParticipant 403 CHAT_NOT_PARTICIPANT — 이 방에 더는 참여자가 아니다(나갔거나 빠졌다). 호출자가 캐시를 버린다 */
        data class Error(val message: String, val notParticipant: Boolean = false) : UiState
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

    /**
     * 헤더 제목 — 나를 뺀 참여자 닉네임([chatRoomPeerTitle], 목록과 같은 규칙).
     * 아직 못 받았거나 조회에 실패하면 null 이고, 그때 화면은 「출발지 → 도착지」로 떨어진다.
     */
    private val _peerTitle = MutableStateFlow<String?>(null)
    val peerTitle: StateFlow<String?> = _peerTitle.asStateFlow()

    /**
     * 이 방의 푸시 알림 음소거 여부(서버 `notificationMuted`). 서버 값을 받기 전엔 false 다 —
     * 「알림 꺼짐」을 잘못 보여 주는 것보다 잠깐 켜진 것으로 보이는 편이 낫다(기본값이 켜짐이므로).
     */
    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

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
            _peerTitle.value = null
            _muted.value = false
            _uiState.value = UiState.Loading
            _inputState.value = InputState()
        }
        // 나가기 신호는 화면이 다시 열릴 때마다 내린다 — 남겨두면 재진입 즉시 또 나가진 것처럼 튕긴다.
        _leftRoom.value = false
        // 이미 대화를 그리고 있으면 스피너로 되돌리지 않는다(깜빡임 방지)
        load(showLoading = roomChanged || _uiState.value !is UiState.Success)
        loadPeerTitle(roomId)
        if (roomChanged) loadMuted(roomId)
        startPolling()
    }

    /**
     * 음소거 상태는 방 단독 API 가 없어 **내 방 목록**(`/chat-rooms/me`)에서 이 방을 찾아 읽는다.
     * 방을 처음 열 때 한 번이면 된다 — 이후 변경은 이 화면의 토글이 유일한 출처라 응답을 기다리지 않고 반영한다.
     * 실패는 삼킨다(기본 「켜짐」으로 남을 뿐).
     */
    private fun loadMuted(roomId: Long) {
        viewModelScope.launch {
            val rooms = runCatching { repository.getMyChatRooms() }.getOrNull() ?: return@launch
            if (chatRoomId != roomId) return@launch
            rooms.firstOrNull { it.room.id == roomId }?.let { _muted.value = it.membership.notificationMuted }
        }
    }

    /**
     * 알림 끄기/켜기. **낙관적으로** 먼저 바꾸고 서버가 거절하면 되돌린다 — 메뉴를 닫자마자
     * 헤더의 「알림 꺼짐」이 따라와야 눌렀다는 느낌이 난다.
     */
    fun toggleMuted() {
        val roomId = chatRoomId ?: return
        val target = !_muted.value
        _muted.value = target
        viewModelScope.launch {
            runCatching { repository.setNotificationMuted(roomId, target) }
                .onFailure {
                    if (chatRoomId == roomId) {
                        _muted.value = !target
                        _inputState.update { state ->
                            state.copy(errorMessage = it.toChatMessage("알림 설정을 바꾸지 못했어요"))
                        }
                    }
                }
        }
    }

    fun retryLoad() {
        load(showLoading = true)
    }

    /**
     * 참여자를 읽어 헤더 제목을 짓는다. 실패는 삼킨다 — 제목이 경로로 남을 뿐 대화는 멀쩡하다.
     * 방이 열려 있는 동안 참여자는 거의 바뀌지 않아 재진입마다 한 번이면 충분하다(폴링하지 않는다).
     */
    private fun loadPeerTitle(roomId: Long) {
        viewModelScope.launch {
            val members = runCatching { repository.getChatRoomMembers(roomId) }.getOrNull()
            // 늦게 온 응답이 그 사이 바뀐 방의 제목을 덮어쓰지 않게 한다
            if (chatRoomId != roomId) return@launch
            _peerTitle.value = members?.let(::chatRoomPeerTitle)
        }
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
                    _uiState.value = UiState.Error(
                        message = e.toChatMessage("대화를 불러오지 못했어요"),
                        notParticipant = (e as? ChatException)?.isNotParticipant == true,
                    )
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

/**
 * 24 채팅 **탭** — 방 목록에서 고르면 같은 라우트 안에서 방으로 전환한다.
 *
 * 21·25·26 의 「채팅 열기」는 이 경로로 오지 않는다 — 탭으로 보내면 매칭 화면이 스택에서 빠진다.
 * 그쪽은 [ChatRoomDestinationRoute] 를 독립 목적지로 쌓아 뒤로가기가 원래 화면으로 돌아가게 한다.
 *
 * @param activePartyId 진행 중인 방의 id([com.moyeota.domain.model.Ride.id]). 열린 채팅방이 그 방의
 *   것이면 헤더에 「매칭 화면으로 →」를 띄운다. 진행 중인 방이 없으면 null.
 */
@Composable
fun ChatRoute(
    repository: ChatRepository,
    activePartyId: String? = null,
    onOpenMatching: () -> Unit = {},
    onOpenRideOngoing: () -> Unit = {},
    onStartLocationShare: () -> Unit = {},
    onLeaveChat: () -> Unit = {},
    onTabSelect: (MoyeotaTab) -> Unit = {},
    /** 열린 방에서 403(참여자 아님) — 진행 화면의 채팅방 id 캐시를 버리게 한다 */
    onNotParticipant: () -> Unit = {},
) {
    var openedRoom by rememberSaveable(stateSaver = ChatRoomSaver) { mutableStateOf<ChatRoom?>(null) }
    // 방이 열린 상태의 시스템 뒤로가기는 탭을 빠져나가지 않고 목록으로 돌아간다 (화면 ← 와 동일)
    BackHandler(enabled = openedRoom != null) { openedRoom = null }

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
            onOpenMatching = onOpenMatching.takeIf { room.isActiveParty(activePartyId) },
            onOpenRideOngoing = onOpenRideOngoing,
            onStartLocationShare = onStartLocationShare,
            // 나간 방을 열어둔 채로 홈에 보내면, 채팅 탭에 돌아왔을 때 참여자가 아닌 방을 다시 연다.
            onLeaveChat = {
                openedRoom = null
                onLeaveChat()
            },
            onTabSelect = onTabSelect,
            onNotParticipant = onNotParticipant,
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
    onOpenMatching: (() -> Unit)?,
    onOpenRideOngoing: () -> Unit,
    onStartLocationShare: () -> Unit,
    onLeaveChat: () -> Unit,
    onTabSelect: (MoyeotaTab) -> Unit,
    /** 서버가 「참여자 아님」(403)을 답했다 — 호출자는 이 방 id 캐시를 버려야 한다 */
    onNotParticipant: () -> Unit = {},
) {
    // 방이 바뀌어도 인스턴스는 하나다 — 방별 key 로 만들면 스토어에 쌓여 각자 폴링한다(QA 결함-2).
    val viewModel: ChatRoomViewModel = viewModel(
        key = "chat-room",
        factory = ChatRoomViewModel.factory(repository),
    )
    val state by viewModel.uiState.collectAsState()
    val input by viewModel.inputState.collectAsState()
    val leftRoom by viewModel.leftRoom.collectAsState()
    val peerTitle by viewModel.peerTitle.collectAsState()
    val muted by viewModel.muted.collectAsState()

    // 화면이 보이는 동안만 조회·폴링한다. 탭을 옮기거나 앱이 백그라운드로 가면 즉시 멈춘다.
    // 같은 구간 동안 「이 방을 보고 있다」를 알려 푸시 알림이 겹치지 않게 한다([ChatForeground]).
    LifecycleStartEffect(room.id) {
        viewModel.onScreenStart(room.id)
        ChatForeground.visibleRoomId = room.id
        onStopOrDispose {
            viewModel.stopPolling()
            if (ChatForeground.visibleRoomId == room.id) ChatForeground.visibleRoomId = null
        }
    }

    LaunchedEffect(leftRoom) {
        if (leftRoom) onLeaveChat()
    }
    LaunchedEffect(state) {
        if ((state as? ChatRoomViewModel.UiState.Error)?.notParticipant == true) onNotParticipant()
    }

    // 방 화면의 이동 수단은 뒤로가기(목록 복귀) + 하단탭 둘 다다.
    // 대화를 못 불러와도 둘 다 남긴다 — Success 일 때의 골격과 같게 (QA F-1)
    //
    // 제목은 **참여자 닉네임**이고 경로는 부제로 내려간다(목록과 같은 규칙 — chatRoomPeerTitle KDoc).
    // 참여자를 아직/끝내 못 받았으면 예전처럼 경로가 제목이고, 그때 부제는 메시지 수다
    // (제목과 부제에 같은 문장을 두 번 적지 않는다). ChatScreen 의 기본값은 Preview 전용.
    val routeLabel = "${room.departure} → ${room.destination}"
    val roomTitle = peerTitle ?: routeLabel
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
            roomSubtitle = if (peerTitle != null) routeLabel else "메시지 ${current.messages.size}개",
            hasOngoingRide = false,
            messages = current.messages.map { it.toUiMessage() },
            input = input.text,
            sending = input.sending,
            errorMessage = input.errorMessage,
            onInputChange = viewModel::onInputChange,
            onSend = viewModel::send,
            onBack = onBack,
            muted = muted,
            onToggleMute = viewModel::toggleMuted,
            // 진행 중인 내 방의 채팅방에서는 「나가기」를 두지 않는다 — 서버가 나간 참여자를 되살리지 못해
            // (chat_room_user 복합키에 leftAt 만 찍힘) 한 번 나가면 운행 내내 대화에 못 돌아온다(실기 QA)
            canLeave = onOpenMatching == null,
            onOpenMatching = onOpenMatching,
            onOpenRideOngoing = onOpenRideOngoing,
            onStartLocationShare = onStartLocationShare,
            onLeaveChat = viewModel::leaveRoom, // 서버에서 빠진 뒤 14 홈으로
            onTabSelect = onTabSelect,
        )
    }
}

/**
 * 24 채팅방 — **독립 목적지**(`Routes.CHAT_ROOM`). 21·25·26 의 「채팅 열기」가 이걸 스택에 쌓는다.
 *
 * 대화 자체는 탭 안의 방 화면과 **완전히 같은 것**을 쓴다([ChatRoomRoute]) — 방 하나에 화면이 둘이면
 * 폴링·읽음 처리·나가기 규칙이 두 벌이 된다. 여기서 더 하는 일은 하나뿐이다: 라우트 인자로 받은
 * roomId 로 방 이름(출발지 → 목적지)을 한 번 조회하는 것. 탭 경로는 목록에서 이미 [ChatRoom] 을
 * 통째로 들고 오지만, 진행 화면에서는 id 밖에 없다.
 *
 * @param activePartyId 진행 중인 방 id. 이 채팅방이 그 방의 것이면 헤더에 「매칭 화면으로 →」가 뜬다.
 *   뒤로가기로도 돌아갈 수 있지만, 채팅방에 오래 머문 뒤에는 「어디로 돌아가는 뒤로가기인지」가
 *   사라진다 — 이름 붙은 길을 함께 둔다.
 */
@Composable
fun ChatRoomDestinationRoute(
    repository: ChatRepository,
    roomId: Long,
    activePartyId: String? = null,
    onBack: () -> Unit = {},
    onOpenMatching: () -> Unit = {},
    onOpenRideOngoing: () -> Unit = {},
    onStartLocationShare: () -> Unit = {},
    onLeaveChat: () -> Unit = {},
    onTabSelect: (MoyeotaTab) -> Unit = {},
    /** 이 방에 참여자가 아니라는 403 — 진행 화면이 들고 있던 채팅방 id 캐시를 버리게 한다 */
    onNotParticipant: () -> Unit = {},
) {
    var room by rememberSaveable(stateSaver = ChatRoomSaver) { mutableStateOf<ChatRoom?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    // 「다시 시도」로 같은 roomId 를 한 번 더 읽기 위한 손잡이 — key 가 같으면 LaunchedEffect 가 다시 돌지 않는다
    var retryKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(roomId, retryKey) {
        if (room?.id == roomId) return@LaunchedEffect
        failure = null
        runCatching { repository.getChatRoom(roomId) }
            .onSuccess { room = it }
            .onFailure { failure = it.toChatMessage("채팅방을 불러오지 못했어요") }
    }

    val current = room
    when {
        current != null -> ChatRoomRoute(
            repository = repository,
            room = current,
            onBack = onBack,
            onOpenMatching = onOpenMatching.takeIf { current.isActiveParty(activePartyId) },
            onNotParticipant = onNotParticipant,
            onOpenRideOngoing = onOpenRideOngoing,
            onStartLocationShare = onStartLocationShare,
            onLeaveChat = onLeaveChat,
            onTabSelect = onTabSelect,
        )
        // 탭바가 아니라 뒤로가기가 이 화면의 이동 수단이다 — 어느 상태에서도 원래 화면으로 돌아갈 수 있어야 한다
        failure != null -> BackStateScaffold(title = "채팅", onBack = onBack) {
            ErrorBox(message = failure.orEmpty(), onRetry = { retryKey++ })
        }
        else -> BackStateScaffold(title = "채팅", onBack = onBack) { LoadingBox() }
    }
}

// 이 채팅방이 지금 진행 중인 방의 것인가. 진행 중인 방이 없으면(null) 언제나 false.
private fun ChatRoom.isActiveParty(activePartyId: String?): Boolean =
    activePartyId != null && partyId.toString() == activePartyId

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

/** 서버 ISO-8601(Instant 또는 오프셋 표기) → Instant. 그 외 형식은 null. */
private fun String.toInstantOrNull(): Instant? =
    runCatching { Instant.parse(this) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(this).toInstant() }.getOrNull()

internal fun String.toEpochMillisOrNull(): Long? = toInstantOrNull()?.toEpochMilli()

/**
 * 채팅 **목록**의 시각 표기 — 오늘이면 `HH:mm`, 올해면 `M월 d일`, 그 전이면 `yyyy.M.d`.
 * 말풍선([toTimeLabel])과 달리 날짜가 필요하다 — 목록에서 「17:36」만 보면 어제인지 지난주인지 모른다.
 * 파싱이 안 되면 null(그 칸을 비운다 — 틀린 시각보다 없는 편이 낫다).
 */
internal fun String.toListTimeLabel(now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String? {
    val at = toInstantOrNull()?.atZone(zone) ?: return null
    val today = now.atZone(zone).toLocalDate()
    val date = at.toLocalDate()
    return when {
        date == today -> "%02d:%02d".format(at.hour, at.minute)
        date.year == today.year -> "${date.monthValue}월 ${date.dayOfMonth}일"
        else -> "${date.year}.${date.monthValue}.${date.dayOfMonth}"
    }
}

// createdAt 은 서버가 UTC 기준으로 내려주는 ISO-8601 문자열이다.
// 문자열을 그대로 자르면 KST 17:36 이 08:36 으로 보인다(QA 결함-3) — 기기 시간대로 변환해 HH:mm 만 쓴다.
// 파싱할 수 없는 형식(오프셋 없는 LocalDateTime 등)이면 예전처럼 잘라 쓴다 — 화면은 살아야 한다.
internal fun String.toTimeLabel(zone: ZoneId = ZoneId.systemDefault()): String? {
    val instant = toInstantOrNull()
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
