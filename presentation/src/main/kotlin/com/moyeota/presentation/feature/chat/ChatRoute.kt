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
import com.moyeota.domain.model.ChatRealtimeEvent
import com.moyeota.domain.model.ChatMember
import com.moyeota.domain.model.ChatReportReason
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

/**
 * 폴링 주기 — **소켓이 안 붙어 있을 때만** 돈다(미로그인·업그레이드 차단·재연결 중).
 *
 * 소켓이 붙어 있으면 폴링하지 않는다. 서버가 지난 메시지를 다시 주지 않아 생기는 구멍(재연결 사이)은
 * 연결될 때마다 한 번 `after` 조회로 메운다([ChatRealtimeEvent.Connected]) — heartbeat 는 끊김을
 * 알려 줄 뿐 그 구멍을 메우지 못한다.
 */
private const val POLL_INTERVAL_MS = 3_000L

/**
 * 소켓으로 보낸 메시지가 **되돌아오기를** 기다리는 시간. 서버는 전송에 답하지 않고 저장된 메시지를
 * 구독으로 밀어 주므로, 이 시간이 지나도록 오지 않으면 실패로 본다.
 * 확인은 거의 즉시(수십 ms) 오므로 넉넉한 값이다. 놓쳤으면 다음 재연결의 공백 조회나 재진입이 실제 결과로 바로잡는다.
 */
private const val PENDING_TIMEOUT_MS = 8_000L

/**
 * 서버 한도(`SendMessageRequest @Size(max = 1000)`)와 같은 값.
 *
 * `internal` 인 이유: 입력창([ChatScreen])이 **같은 숫자**를 봐야 한다. 예전엔 화면이 500자로 하드
 * 컷해서 이 1000자 검사와 그 안내가 영영 닿지 않는 죽은 코드였다 — 사용자는 500자에서 조용히
 * 막히고 왜 막혔는지 알 수 없었다.
 */
internal const val MAX_CONTENT_LENGTH = 1_000

/**
 * 재연결 공백을 메울 때 한 번에 이어 읽는 **최대 페이지 수**(한 페이지 [ChatRepository.DEFAULT_PAGE_SIZE]=30건).
 *
 * `hasNext` 가 거짓이 될 때까지 커서를 밀지만, 상한 없이 돌면 서버가 `hasNext=true` 를 잘못 내는 순간
 * 끝나지 않는 루프가 된다(여기서 멈추면 대화 화면이 통째로 멈춘다). 10페이지 = 300건으로 둔 근거:
 * - 이 함수가 메우는 건 **끊긴 몇 분 사이**의 공백이다. 300건이 비었다면 그건 공백이 아니라 「오랜만에
 *   다시 열었다」에 가깝고, 그 경우는 첫 페이지 조회(최신 30건)가 더 싸고 적절하다.
 * - 상한에 닿아도 **잃지 않는다**: 읽은 만큼 커서가 전진해 있으므로 다음 재연결·다음 폴링 주기가
 *   이어서 메운다.
 */
private const val MAX_GAP_FILL_PAGES = 10

/**
 * 검색어를 치는 동안 서버를 두드리지 않고 기다리는 시간. 한 글자마다 요청하면 「부산역」 한 단어에
 * 세 번 나간다. 이 시간 안에 더 안 치면 그때 한 번 묻는다.
 */
private const val SEARCH_DEBOUNCE_MS = 350L

/** 서버 규칙(`ChatMessageService.MIN_KEYWORD_LENGTH`) — 미만이면 400 CHAT_INVALID_KEYWORD 라 여기서 먼저 막는다 */
internal const val SEARCH_MIN_LENGTH = 2

/** 아직 확인이 오지 않은 내 말풍선에 붙이는 표기 */
private const val PENDING_LABEL = "보내는 중"
private const val PENDING_FAILED_LABEL = "전송 실패"

// 발신자 닉네임이 없을 때 상대 말풍선에 붙이는 표기.
private const val PEER_FALLBACK_NAME = "동승자"

/**
 * 목록 한 줄. 서버 `GET /chat-rooms/me` 가 주지 않는 것(참여자)을 방별로 덧붙인 형태다.
 *
 * @param peerTitle 나를 뺀 참여자 닉네임으로 지은 제목. **참여자 조회에 실패하면 null** —
 *   그때 화면은 예전처럼 「출발지 → 도착지」를 제목으로 쓴다(방 한 칸이 통째로 깨지지 않게).
 * @param peerImageUrl [peerTitle] 이 **맨 앞에 부르는 사람**의 프로필 사진([chatRoomPeerImageUrl]).
 *   제목과 **같은 멤버 목록**에서 뽑아 여기 실어 보낸다 — 행이 `membership.members` 를 보고 따로
 *   고르면, 참여자를 방별로 따로 조회하는 구버전 서버 경로에서 제목은 이름인데 얼굴만 비게 된다.
 */
data class ChatRoomListItem(
    val room: MyChatRoom,
    val peerTitle: String?,
    val peerImageUrl: String? = null,
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
    val names = chatRoomPeerMembers(members).map { it.nickname.ifBlank { PEER_FALLBACK_NAME } }
    return when {
        names.isEmpty() -> "동승자 없음"
        names.size <= 2 -> names.joinToString(" · ")
        // 셋 이상은 다 적으면 한 줄을 넘긴다 — 첫 사람 + 나머지 수
        else -> "${names.first()} 외 ${names.size - 1}명"
    }
}

/**
 * 제목이 부르는 참여자 — **이름과 얼굴이 함께 쓰는 유일한 출처**다. 순서는 서버가 준 순서 그대로다.
 *
 * [chatRoomPeerTitle] 과 [chatRoomPeerImageUrl] 이 각자 멤버를 고르면 한쪽에만 규칙이 빠지는 순간
 * 「A 이름 + B 얼굴」이 된다(나간 사람 제외가 대표적이다) — 고르는 일을 이 한 곳에만 둔다.
 */
internal fun chatRoomPeerMembers(members: List<ChatMember>): List<ChatMember> {
    val peers = members.filter { !it.isMe }
    return peers.filter { it.active }.ifEmpty { peers }
}

/**
 * 목록 행 아바타에 그릴 사진 — [chatRoomPeerTitle] 이 **맨 앞에 적는 사람**의 것.
 *
 * 여럿인 방도 얼굴 자리는 하나뿐이라 제목의 첫 사람으로 맞춘다(「스모크일 외 2명」이면 스모크일).
 * 사진을 고른 적 없는 사람에게도 서버는 기본 이미지 URL 을 주므로(백엔드 소스 기준) 여기서 null 이
 * 되는 건 참여자를 못 받은 방과 빈 문자열 응답뿐이고, 그때 아바타는 회색 원으로 떨어진다.
 */
internal fun chatRoomPeerImageUrl(members: List<ChatMember>): String? =
    chatRoomPeerMembers(members).firstOrNull()?.imageUrl?.takeIf { it.isNotBlank() }

/**
 * 말풍선용 `publicId → 프로필 사진` 사전.
 *
 * 메시지에는 발신자 publicId 만 실려 오고 사진은 참여자 응답에만 있다 — 이 사전이 없으면 상대
 * 말풍선 아바타는 영영 회색 원이다(닉네임을 잇는 길과 같은 구조다).
 *
 * 방을 나간 사람(active=false)도 **담는다** — 남긴 메시지의 얼굴이 사라지면 대화가 누구와 한
 * 것인지 흐려진다([chatRoomPeerTitle] 이 이름을 남기는 것과 같은 이유). 내 사진까지 담기지만
 * 내 말풍선에는 아바타 자리가 없어 쓰이지 않는다 — 거르는 조건을 하나 더 두지 않는다.
 * 서버는 사람당 한 줄만 주므로 같은 publicId 가 겹치지 않는다.
 */
internal fun chatMemberImageUrls(members: List<ChatMember>): Map<String, String> =
    members.mapNotNull { member ->
        member.imageUrl?.takeIf { it.isNotBlank() }?.let { member.publicId to it }
    }.toMap()

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
        // 목록 응답에 참여자가 함께 오면(서버 2026-09-14) **추가 조회 없이** 제목을 짓는다.
        // 다 채워져 있으면 화면 하나에 요청 1회로 끝난다 — 예전엔 1+2N 회였다.
        if (rooms.all { it.membership.members.isNotEmpty() }) {
            return rooms.map {
                ChatRoomListItem(
                    room = it,
                    peerTitle = chatRoomPeerTitle(it.membership.members),
                    peerImageUrl = chatRoomPeerImageUrl(it.membership.members),
                )
            }
        }
        // 구버전 서버 폴백 — 방마다 참여자를 따로 읽는다. 순차로 돌면 방 수만큼 왕복이 쌓여 병렬로 돌린다.
        // 조회 실패는 **그 방만** null 로 떨어뜨린다(목록 전체를 에러로 만들지 않는다).
        return coroutineScope {
            rooms.map { room ->
                async {
                    // 멤버 목록을 먼저 확정하고 제목·얼굴을 **거기서 함께** 뽑는다 — 둘을 따로 구하면
                    // 조회가 한쪽에만 반영되는 틈이 생기고, 행은 이름과 얼굴이 어긋난 채로 그려진다.
                    val members = room.membership.members.takeIf { it.isNotEmpty() }
                        ?: runCatching { repository.getChatRoomMembers(room.room.id) }.getOrNull()
                    ChatRoomListItem(
                        room = room,
                        peerTitle = members?.let(::chatRoomPeerTitle),
                        peerImageUrl = members?.let(::chatRoomPeerImageUrl),
                    )
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
        data class Success(
            val messages: List<DomainChatMessage>,
            /**
             * 아직 서버에서 되돌아오지 않은 내 메시지. 소켓 전송(`/pub/…/messages`)은 **응답이 없어서**
             * 화면이 먼저 그려 두고, 같은 내용이 [messages] 로 돌아오면 지운다.
             */
            val pending: List<PendingMessage> = emptyList(),
        ) : UiState
        /** @param notParticipant 403 CHAT_NOT_PARTICIPANT — 이 방에 더는 참여자가 아니다(나갔거나 빠졌다). 호출자가 캐시를 버린다 */
        data class Error(val message: String, val notParticipant: Boolean = false) : UiState
    }

    /**
     * 보내 놓고 확인을 기다리는 메시지.
     *
     * @param localId 화면에서 구분하기 위한 임시 id(음수 — 서버 id 와 겹치지 않는다)
     * @param sinceId 보낼 때 이미 있던 마지막 메시지 id. 예전에 보낸 **같은 문장**을 확인으로 착각하지 않게 한다
     * @param failed 확인이 [PENDING_TIMEOUT_MS] 안에 오지 않았다
     */
    data class PendingMessage(
        val localId: Long,
        val content: String,
        val sinceId: Long,
        val failed: Boolean = false,
    )

    data class InputState(
        val text: String = "",
        val sending: Boolean = false,
        val errorMessage: String? = null,
    )

    /**
     * 메시지 신고(`POST /chat-rooms/{id}/reports`)의 진행 상태.
     *
     * @param outcome 직전 결과. **전송을 시작할 때 null 로 되돌린다** — 같은 결과가 연달아 오면
     *   (같은 메시지를 또 신고 → 같은 409) 값이 안 바뀌어 화면의 `LaunchedEffect(reportOutcome)` 가
     *   발화하지 않고 시트가 열린 채 남는다.
     */
    data class ReportState(
        val submitting: Boolean = false,
        val outcome: ChatReportOutcome? = null,
    )

    /**
     * 방 안 메시지 검색(`GET /chat-rooms/{id}/messages/search`). 헤더의 검색 아이콘으로 열고 닫는다.
     *
     * @param results null 이면 아직 검색하지 않았다(검색어가 짧거나 비어 있다). 빈 목록은 「결과 없음」이다 —
     *   둘을 구분해야 안내 문구가 다르다.
     * @param nextCursor 더 과거 결과를 받을 커서. 서버는 최신부터 준다(id 내림차순).
     */
    data class SearchState(
        val open: Boolean = false,
        val query: String = "",
        val results: List<DomainChatMessage>? = null,
        val loading: Boolean = false,
        val loadingMore: Boolean = false,
        val hasNext: Boolean = false,
        val nextCursor: Long? = null,
        val errorMessage: String? = null,
    )

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _inputState = MutableStateFlow(InputState())
    val inputState: StateFlow<InputState> = _inputState.asStateFlow()

    private val _searchState = MutableStateFlow(SearchState())
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    private val _leftRoom = MutableStateFlow(false)
    val leftRoom: StateFlow<Boolean> = _leftRoom.asStateFlow()

    /**
     * 메시지 신고의 **전송 중 여부와 결과**. 고른 사유와 적던 상세는 화면이 들고 있다 —
     * 여기 올려 두면 화면 상태가 두 곳으로 갈려 「시트는 닫혔는데 사유가 남아 있다」가 생긴다.
     */
    private val _reportState = MutableStateFlow(ReportState())
    val reportState: StateFlow<ReportState> = _reportState.asStateFlow()

    /**
     * 헤더 제목 — 나를 뺀 참여자 닉네임([chatRoomPeerTitle], 목록과 같은 규칙).
     * 아직 못 받았거나 조회에 실패하면 null 이고, 그때 화면은 「출발지 → 도착지」로 떨어진다.
     */
    private val _peerTitle = MutableStateFlow<String?>(null)
    val peerTitle: StateFlow<String?> = _peerTitle.asStateFlow()

    /**
     * 상대 말풍선 아바타용 `publicId → 사진`([chatMemberImageUrls]). 제목과 **같은 조회 한 번**에서
     * 나온다 — 메시지에는 사진이 실려 오지 않아 이 사전이 유일한 출처다.
     * 못 받았으면 빈 사전이고, 그때 아바타는 회색 원으로 떨어진다.
     */
    private val _peerImageUrls = MutableStateFlow<Map<String, String>>(emptyMap())
    val peerImageUrls: StateFlow<Map<String, String>> = _peerImageUrls.asStateFlow()

    /**
     * 이 방의 푸시 알림 음소거 여부(서버 `notificationMuted`). 서버 값을 받기 전엔 false 다 —
     * 「알림 꺼짐」을 잘못 보여 주는 것보다 잠깐 켜진 것으로 보이는 편이 낫다(기본값이 켜짐이므로).
     */
    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    /**
     * 페이징 커서 — 화면이 들고 있는 **가장 마지막 메시지 id**. 읽음 처리의 성공 여부와 무관하게 전진한다.
     */
    private var lastMessageId: Long? = null

    /**
     * **서버에 읽음으로 반영된** 마지막 메시지 id. [lastMessageId] 와 **일부러 분리했다** —
     * 예전엔 한 변수가 커서와 「보냈다」 표시를 겸해서, 읽음 호출이 실패해도 커서가 이미 전진해 있어
     * **다시 보내지 않았다**(`if (id == lastMessageId) return` 에서 조기 반환). 그러면 새 메시지가 더
     * 오지 않는 방은 읽음이 영영 반영되지 않아 목록 배지가 읽은 방에 계속 남는다.
     *
     * 서버는 읽음을 5초 버퍼에 모아 반영하므로(Backend #188) 다음 주기에 한 번 더 보내는 비용이 거의 없다.
     */
    private var readSentMessageId: Long? = null

    /**
     * 지금 서버로 **보내고 있는** 읽음 지점. 재시도 주기(3초)가 느린 응답과 겹쳐 같은 id 를 두 번
     * 보내지 않게 한다 — 예전엔 커서를 먼저 세워 두는 것이 이 역할까지 겸했다.
     */
    private var readSendingMessageId: Long? = null

    private var nextLocalId = -1L
    private var searchJob: Job? = null
    private var chatRoomId: Long? = null
    private var pollingJob: Job? = null
    private var socketJob: Job? = null
    private var loadJob: Job? = null

    /** 소켓 구독이 살아 있는가 — 살아 있으면 폴링하지 않는다 */
    @Volatile
    private var realtimeAlive = false

    /**
     * 화면이 보이기 시작할 때 호출한다(최초 진입·뒤로 돌아옴·앱 복귀·다른 방으로 전환).
     * 방이 바뀌었으면 상태를 갈아엎고, 같은 방이면 조용히 다시 읽는다 —
     * 재진입 시 재조회가 없으면 내가 보낸 뒤 학습된 「내 메시지」 판정이 교정되지 않는다(QA 결함-2).
     */
    fun onScreenStart(roomId: Long, knownMuted: Boolean? = null) {
        val roomChanged = chatRoomId != roomId
        if (roomChanged) {
            stopPolling()
            loadJob?.cancel()
            chatRoomId = roomId
            lastMessageId = null
            readSentMessageId = null
            readSendingMessageId = null
            _peerTitle.value = null
            // 제목과 **같은 자리에서** 비운다 — 사전은 「이 방의 참여자」가 근거다. 앞 방 것을 남겨 두면
            // 새 방의 참여자 조회가 실패해(= 근거가 없어) 회색 원이어야 할 자리에 앞 방에서 본 얼굴이
            // 그려진다. 두 방에 겹치는 사람이 많아 눈에 잘 띄지도 않는다.
            _peerImageUrls.value = emptyMap()
            _muted.value = false
            _uiState.value = UiState.Loading
            _inputState.value = InputState()
            // 신고 상태도 방 단위다 — 앞 방의 결과 배너가 새 방에 남으면 방금 신고한 것처럼
            // 읽히고, 방을 갈아타며 버린 전송(아래 roomId 가드) 때문에 submitting 이 참으로
            // 굳어 있으면 새 방에서 신고 버튼이 영영 잠긴다.
            _reportState.value = ReportState()
            searchJob?.cancel()
            _searchState.value = SearchState()
        }
        // 나가기 신호는 화면이 다시 열릴 때마다 내린다 — 남겨두면 재진입 즉시 또 나가진 것처럼 튕긴다.
        _leftRoom.value = false
        // 이미 대화를 그리고 있으면 스피너로 되돌리지 않는다(깜빡임 방지)
        load(showLoading = roomChanged || _uiState.value !is UiState.Success)
        loadPeerMembers(roomId)
        if (roomChanged) loadMuted(roomId, knownMuted)
        startRealtime(roomId)
        startPolling()
    }

    /**
     * 음소거 상태는 방 단독 API 가 없어 **내 방 목록**(`/chat-rooms/me`)에서 이 방을 찾아 읽는다.
     * 방을 처음 열 때 한 번이면 된다 — 이후 변경은 이 화면의 토글이 유일한 출처라 응답을 기다리지 않고 반영한다.
     * 실패는 삼킨다(기본 「켜짐」으로 남을 뿐).
     */
    /**
     * 음소거 상태. 서버에 방 단건으로 묻는 길이 없어 **내 방 목록**에서 이 방을 찾아 읽는다.
     * 목록에서 들어온 경우엔 그 값을 그대로 받아(knownMuted) 호출 자체를 생략한다 —
     * 목록 API 는 방 정보·참여자까지 다 담고 있어 방을 열 때마다 다시 부르기엔 무겁다.
     */
    private fun loadMuted(roomId: Long, knownMuted: Boolean?) {
        if (knownMuted != null) {
            _muted.value = knownMuted
            return
        }
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
     * 참여자를 **한 번 읽어** 헤더 제목과 말풍선 아바타 사전을 함께 세운다. 실패는 삼킨다 —
     * 제목이 경로로, 얼굴이 회색 원으로 남을 뿐 대화는 멀쩡하다.
     * 방이 열려 있는 동안 참여자는 거의 바뀌지 않아 재진입마다 한 번이면 충분하다(폴링하지 않는다).
     */
    private fun loadPeerMembers(roomId: Long) {
        viewModelScope.launch {
            val members = runCatching { repository.getChatRoomMembers(roomId) }.getOrNull()
            // 늦게 온 응답이 그 사이 바뀐 방의 제목·얼굴을 덮어쓰지 않게 한다
            if (chatRoomId != roomId) return@launch
            _peerTitle.value = members?.let(::chatRoomPeerTitle)
            _peerImageUrls.value = members?.let(::chatMemberImageUrls).orEmpty()
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
                publish(ordered)
                markLastAsRead(ordered.lastOrNull()?.id)
                // 소켓이 이 조회보다 먼저 붙었으면 그 순간의 공백 조회는 화면이 아직 없어 건너뛰었다 — 지금 메운다
                if (realtimeAlive) fetchNew(roomId)
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

    /**
     * 실시간 수신(STOMP) 구독. 화면이 보이는 동안만 연결한다.
     *
     * 연결될 때마다(처음·재연결) 마지막 id 이후를 **한 번** 조회해 끊긴 사이의 공백을 메운다 — 서버 브로커는
     * 지금 구독 중인 사람에게만 밀고 지난 메시지를 다시 주지 않는다. 연결이 살아 있는 동안은 폴링하지 않는다.
     * 실패·끊김은 저장소가 재시도로 삼키므로 화면엔 아무것도 띄우지 않는다 — 끊긴 동안은 폴링이 메운다.
     */
    private fun startRealtime(roomId: Long) {
        socketJob?.cancel()
        realtimeAlive = false
        socketJob = viewModelScope.launch {
            repository.observeRoom(roomId).collect { event ->
                if (chatRoomId != roomId) return@collect
                when (event) {
                    ChatRealtimeEvent.Connected -> {
                        realtimeAlive = true
                        fetchNew(roomId) // 구독이 걸린 뒤에 조회 — 겹치는 메시지는 id 로 합쳐진다
                    }
                    ChatRealtimeEvent.Disconnected -> realtimeAlive = false
                    is ChatRealtimeEvent.Message -> {
                        val current = _uiState.value as? UiState.Success ?: return@collect
                        // 같은 id 가 다시 오면(삭제 브로드캐스트) **새 사본이 이긴다** — mergeMessagesById KDoc
                        val merged = mergeMessagesById(current.messages, listOf(event.message))
                        publish(merged, current.pending)
                        markLastAsRead(merged.lastOrNull()?.id)
                    }
                }
            }
        }
    }

    /** 화면이 가려지면(탭 이동·백그라운드·이탈) 폴링과 소켓을 함께 멈춘다. 안 그러면 로그아웃 뒤에도 계속 쏜다. */
    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        socketJob?.cancel()
        socketJob = null
        realtimeAlive = false
    }

    // 소켓이 안 붙어 있을 때만 조회한다. 붙어 있으면 새 메시지는 소켓으로 오고, 공백은 연결 순간에 메웠다.
    private fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            while (true) {
                delay(POLL_INTERVAL_MS)
                val roomId = chatRoomId ?: continue
                // 뒤처진 읽음 처리는 **소켓 생사와 무관하게** 다시 보낸다 — 실패한 읽음은 새 메시지가
                // 더 오지 않으면 스스로 나갈 기회가 없고, 그러면 이미 읽은 방에 목록 배지가 계속 남는다.
                // 이미 반영된 지점과 같으면 [markLastAsRead] 가 그 자리에서 되돌아간다(평상시 0회).
                markLastAsRead(lastMessageId)
                if (realtimeAlive) continue
                fetchNew(roomId)
            }
        }
    }

    /**
     * 마지막 메시지 이후를 읽어 합친다 — 소켓 (재)연결 순간과, 소켓이 없을 때의 폴링이 같이 쓴다.
     *
     * 커서(마지막 메시지 id)가 없으면 **첫 페이지를 다시 읽는다**. 메시지가 하나도 없는 방은 커서가 영영 null 인데,
     * `after` 에 0 을 넘기면 서버가 cursor < 1 을 400 CHAT_INVALID_CURSOR 로 막는다(#30).
     *
     * **`hasNext` 를 끝까지 따라간다.** 예전에는 한 페이지(30건)만 읽고 `hasNext` 를 버렸다 — 끊긴 사이에
     * 31건 이상이 오간 방은 그 뒤 `realtimeAlive` 가 참이 되면서 폴링도 건너뛰어, 31번째 이후가 **화면을
     * 벗어나 다시 열 때까지 영구히 비어 있었다**(읽음 커서도 함께 뒤처져 목록 배지가 어긋났다).
     * 서버는 `size + 1` 을 읽어 `hasNext` 를 정확히 내려준다(`ChatMessageService.findAfter`) —
     * 「더 있다」는 신호가 오는데 앱이 버리고 있었다. 무한 루프 방어로 [MAX_GAP_FILL_PAGES] 까지만 돈다.
     *
     * **과거 방향(커서 없음)으로는 이어 읽지 않는다.** 그 응답의 `hasNext` 는 「더 **과거**가 있다」는
     * 뜻이라 여기서 따라가면 올려 읽는 역방향 페이징이 되는데, 그건 이 함수의 일이 아니다.
     *
     * 합칠 때는 응답이 온 **뒤의** 상태를 다시 읽는다 — 조회하는 동안 소켓으로 들어온 메시지를 덮어쓰지 않게.
     * 실패는 화면을 깨지 않는다 — 다음 연결·다음 주기가 메운다.
     */
    private suspend fun fetchNew(roomId: Long) {
        if (_uiState.value !is UiState.Success) return
        var cursor = lastMessageId
        var pagesRead = 0
        while (true) {
            // 커서가 있으면 「이후」 방향, 없으면 첫 페이지(= 과거 방향 최신 30건).
            // 지역 val 로 받아 두는 이유: 람다가 캡처한 var 는 스마트 캐스트가 안 된다.
            val at = cursor
            val page = runCatching {
                if (at == null) repository.getMessages(roomId) else repository.getMessagesAfter(roomId, at)
            }.getOrNull() ?: return
            if (page.messages.isEmpty() || chatRoomId != roomId) return
            val latest = _uiState.value as? UiState.Success ?: return
            // 같은 id 의 새 사본이 이긴다(삭제 브로드캐스트) — mergeMessagesById KDoc
            val merged = mergeMessagesById(latest.messages, page.messages)
            publish(merged, latest.pending)
            markLastAsRead(merged.lastOrNull()?.id)
            if (at == null || !page.hasNext) return
            if (++pagesRead >= MAX_GAP_FILL_PAGES) return
            // 커서는 서버가 준 nextCursor 를 쓴다(이 방향에서는 「방금 받은 마지막 id」다).
            // 안 주는 서버면 받은 페이지의 최대 id 로 떨어진다 — 화면의 마지막 id 를 쓰면 그 사이
            // 소켓으로 들어온 더 큰 id 때문에 중간을 건너뛸 수 있다.
            cursor = page.nextCursor ?: page.messages.maxOf { it.id }
        }
    }

    fun onInputChange(text: String) {
        _inputState.update { it.copy(text = text, errorMessage = null) }
    }

    /**
     * 전송. **소켓이 붙어 있으면 소켓으로**(`/pub/chat-rooms/{id}/messages`), 아니면 REST 로 보낸다.
     *
     * 둘 중 하나만 쓴다 — 소켓으로 보낸 뒤 확인이 늦다고 REST 로 다시 보내면 같은 메시지가 두 번 저장된다.
     * 그래서 소켓 경로의 실패는 재전송이 아니라 **실패 표시**로 끝난다(사용자가 다시 보낼지 정한다).
     */
    fun send() {
        val roomId = chatRoomId ?: return
        val content = _inputState.value.text.trim()
        if (content.isEmpty() || _inputState.value.sending) return
        if (content.length > MAX_CONTENT_LENGTH) {
            // 서버도 같은 한도로 거절한다(SendMessageRequest @Size). 프레임을 띄우기 전에 여기서 막는다.
            _inputState.update { it.copy(errorMessage = "메시지는 ${MAX_CONTENT_LENGTH}자까지 보낼 수 있어요") }
            return
        }
        viewModelScope.launch {
            _inputState.update { it.copy(sending = true, errorMessage = null) }
            val overSocket = runCatching { repository.trySendMessage(roomId, content) }.getOrDefault(false)
            if (overSocket) addPending(content) else sendOverRest(roomId, content)
        }
    }

    // 서버가 답을 주지 않는 전송이라 화면이 먼저 그린다. 같은 내용이 구독으로 돌아오면(또는 폴링이 물어 오면)
    // publish 가 이 말풍선을 걷어낸다.
    private fun addPending(content: String) {
        val current = _uiState.value as? UiState.Success
        val entry = PendingMessage(
            localId = nextLocalId--,
            content = content,
            sinceId = current?.messages?.lastOrNull()?.id ?: 0L,
        )
        if (current != null) _uiState.value = current.copy(pending = current.pending + entry)
        _inputState.value = InputState() // 프레임은 이미 나갔다 — 입력창은 바로 비운다
        watchPending(entry.localId)
    }

    /** 확인이 끝내 오지 않으면 실패로 바꿔 보여 준다. 그 전에 돌아오면 이미 목록에서 사라져 있다. */
    private fun watchPending(localId: Long) {
        viewModelScope.launch {
            delay(PENDING_TIMEOUT_MS)
            val current = _uiState.value as? UiState.Success ?: return@launch
            if (current.pending.none { it.localId == localId }) return@launch
            _uiState.value = current.copy(
                pending = current.pending.map { if (it.localId == localId) it.copy(failed = true) else it },
            )
            _inputState.update { it.copy(errorMessage = "메시지를 보내지 못했어요") }
        }
    }

    // 소켓이 없을 때의 경로. 이쪽은 저장 결과가 응답으로 와서 그 자리에서 붙일 수 있다.
    private suspend fun sendOverRest(roomId: Long, content: String) {
        try {
            val sent = repository.sendMessage(roomId, content)
            val current = _uiState.value as? UiState.Success
            if (current != null) {
                // 같은 id 가 이미 있으면 **방금 받은 응답이 이긴다** — mergeMessagesById KDoc
                val merged = mergeMessagesById(current.messages, listOf(sent))
                publish(merged, current.pending)
                markLastAsRead(merged.lastOrNull()?.id)
            }
            _inputState.update { InputState() } // 성공 시에만 입력창을 비운다
        } catch (e: Exception) {
            _inputState.update {
                it.copy(sending = false, errorMessage = e.toChatMessage("메시지를 보내지 못했어요"))
            }
        }
    }

    /** 목록을 새로 그린다. 서버에서 돌아온 메시지와 짝지어진 대기 말풍선은 여기서 사라진다. */
    private fun publish(messages: List<DomainChatMessage>, pending: List<PendingMessage> = emptyList()) {
        _uiState.value = UiState.Success(messages, reconcilePending(messages, pending))
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

    /**
     * 메시지 1건 신고. **성공해도 되돌릴 길이 없다** — 서버에 취소 엔드포인트가 없고, 같은
     * 메시지를 다시 신고하면 사유를 바꿔도 409 다(실측). 그래서 재확인은 사유 시트가 맡고
     * (사유를 고르는 것 자체가 확인이다) 여기서는 두 번 보내지 않는 것만 지킨다.
     *
     * [description] 은 호출부가 이미 공백을 접어서 넘긴다 — 저장소도 한 번 더 접는다(이중 방어).
     */
    fun reportMessage(chatMessageId: Long, reason: ChatReportReason, description: String?) {
        val roomId = chatRoomId ?: return
        if (_reportState.value.submitting) return
        // 보내는 동안 앞 결과를 지운다: 화면이 결과의 **변화**로 시트를 닫으므로, 같은 결과가
        // 두 번 올 때 null 을 한 번 거치지 않으면 두 번째가 없던 일이 된다.
        _reportState.value = ReportState(submitting = true, outcome = null)
        viewModelScope.launch {
            val outcome = try {
                repository.reportMessage(roomId, chatMessageId, reason, description)
                ChatReportOutcome(
                    // 운영이 **언제 무엇을 하겠다**고는 쓰지 않는다(`ProductClaimsTest` 의 시한
                    // 금지와 같은 선). 서버가 실제로 보장하는 둘만 적는다: 접수됐고, 중복은 막힌다.
                    message = "신고를 접수했어요. 같은 메시지는 다시 신고할 수 없어요.",
                    accepted = true,
                )
            } catch (e: Exception) {
                e.toChatReportOutcome()
            }
            if (chatRoomId != roomId) return@launch
            _reportState.value = ReportState(submitting = false, outcome = outcome)
        }
    }

    fun openSearch() {
        _searchState.update { it.copy(open = true) }
    }

    /** 닫으면 검색어·결과를 다 버린다 — 다시 열 때 지난 검색이 남아 있으면 「방금 친 것」으로 오해한다 */
    fun closeSearch() {
        searchJob?.cancel()
        _searchState.value = SearchState()
    }

    /**
     * 검색어 변경. [SEARCH_MIN_LENGTH] 이상이면 [SEARCH_DEBOUNCE_MS] 뒤 첫 페이지를 묻는다.
     * 짧아지면 결과를 비우고(null) 요청도 보내지 않는다 — 서버가 어차피 400 으로 거절한다.
     */
    fun onSearchQueryChange(text: String) {
        searchJob?.cancel()
        val keyword = text.trim()
        if (keyword.length < SEARCH_MIN_LENGTH) {
            _searchState.update { it.copy(query = text, results = null, loading = false, loadingMore = false, hasNext = false, nextCursor = null, errorMessage = null) }
            return
        }
        _searchState.update { it.copy(query = text, loading = true, errorMessage = null) }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            runSearch(keyword, cursor = null)
        }
    }

    /** 에러 화면의 「다시 시도」 — 디바운스 없이 바로 */
    fun retrySearch() {
        val keyword = _searchState.value.query.trim()
        if (keyword.length < SEARCH_MIN_LENGTH) return
        searchJob?.cancel()
        _searchState.update { it.copy(loading = true, errorMessage = null) }
        searchJob = viewModelScope.launch { runSearch(keyword, cursor = null) }
    }

    /** 다음 페이지(더 과거). 이미 받는 중이거나 더 없으면 무시한다 */
    fun loadMoreSearch() {
        val current = _searchState.value
        val cursor = current.nextCursor ?: return
        if (!current.hasNext || current.loading || current.loadingMore) return
        val keyword = current.query.trim()
        if (keyword.length < SEARCH_MIN_LENGTH) return
        searchJob?.cancel()
        _searchState.update { it.copy(loadingMore = true, errorMessage = null) }
        searchJob = viewModelScope.launch { runSearch(keyword, cursor) }
    }

    // cursor == null 이면 첫 페이지(결과 교체), 아니면 이어 붙인다. 응답이 오기 전에 검색어가 바뀌면
    // 이 코루틴은 onSearchQueryChange 가 취소하므로 낡은 결과가 새 검색어 위에 덮이지 않는다.
    private suspend fun runSearch(keyword: String, cursor: Long?) {
        val roomId = chatRoomId ?: return
        try {
            val page = repository.searchMessages(roomId, keyword, cursor)
            _searchState.update { state ->
                val merged = if (cursor == null) page.messages
                else ((state.results ?: emptyList()) + page.messages).distinctBy { it.id }
                state.copy(
                    results = merged,
                    loading = false,
                    loadingMore = false,
                    hasNext = page.hasNext,
                    nextCursor = page.nextCursor,
                    errorMessage = null,
                )
            }
        } catch (e: Exception) {
            _searchState.update {
                it.copy(loading = false, loadingMore = false, errorMessage = e.toChatMessage("검색하지 못했어요"))
            }
        }
    }

    /**
     * 읽음 처리. 실패는 사용자에게 알리지 않는다(배지 정확도보다 대화 흐름이 우선) —
     * 대신 **[readSentMessageId] 를 올리지 않아** 다음 폴링 주기가 같은 id 로 다시 보낸다.
     * 메시지마다 한 번씩 일어나는 일이라 소켓이 붙어 있으면 그쪽으로 보내 REST 왕복을 아낀다.
     *
     * 커서([lastMessageId])는 성공 여부와 무관하게 전진한다 — 메시지를 다시 받아 오는 일과
     * 읽음을 알리는 일은 서로의 재시도에 끼어들지 않아야 한다.
     */
    private fun markLastAsRead(messageId: Long?) {
        val roomId = chatRoomId ?: return
        val id = messageId ?: return
        lastMessageId = id
        // 이미 반영됐거나 지금 보내는 중인 지점이면 보낼 것이 없다(평상시 이 길로 되돌아간다)
        if (id == readSentMessageId || id == readSendingMessageId) return
        readSendingMessageId = id
        viewModelScope.launch {
            val sent = runCatching {
                if (!repository.tryMarkAsRead(roomId, id)) repository.markAsRead(roomId, id)
                true
            }.getOrDefault(false)
            // 방을 갈아탄 뒤 늦게 온 결과가 새 방의 지점을 더럽히지 않게 한다
            if (chatRoomId != roomId) return@launch
            if (readSendingMessageId == id) readSendingMessageId = null
            if (sent) readSentMessageId = id
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
 * 21 의 「채팅 열기」는 이 경로로 오지 않는다 — 탭으로 보내면 매칭 화면이 스택에서 빠진다.
 * 그쪽은 [ChatRoomDestinationRoute] 를 독립 목적지로 쌓아 뒤로가기가 원래 화면으로 돌아가게 한다.
 *
 * @param activeChatRoomId 진행 중인 방의 **채팅방 id**(`ActivePartyViewModel.chatRoomId`). 열린 채팅방이
 *   이것이면 헤더에 「매칭 화면으로 →」를 띄우고 「나가기」를 감춘다. 이 경로의 방은 목록 응답으로 만든
 *   것이어서 `partyId` 가 0(모름)이라 **이 값만이 판정 근거다**([ChatRoom.isActiveParty]).
 * @param activePartyId 진행 중인 방의 id([com.moyeota.domain.model.Ride.id]). 이 경로에서는 사실상
 *   판정에 기여하지 못한다(위 참조) — 그래도 같은 판정 함수를 쓰므로 함께 넘긴다. 없으면 null.
 */
@Composable
fun ChatRoute(
    repository: ChatRepository,
    activeChatRoomId: Long? = null,
    activePartyId: String? = null,
    onOpenMatching: () -> Unit = {},
    onLeaveChat: () -> Unit = {},
    onTabSelect: (MoyeotaTab) -> Unit = {},
    /** 열린 방에서 403(참여자 아님) — 진행 화면의 채팅방 id 캐시를 버리게 한다 */
    onNotParticipant: () -> Unit = {},
) {
    var openedRoom by rememberSaveable(stateSaver = ChatRoomSaver) { mutableStateOf<ChatRoom?>(null) }
    // 목록에서 고른 방의 음소거 상태 — 방 화면이 목록 API 를 다시 부르지 않게 함께 들고 간다
    var openedMuted by rememberSaveable { mutableStateOf<Boolean?>(null) }
    // 방이 열린 상태의 시스템 뒤로가기는 탭을 빠져나가지 않고 목록으로 돌아간다 (화면 ← 와 동일)
    BackHandler(enabled = openedRoom != null) { openedRoom = null }

    val room = openedRoom
    if (room == null) {
        ChatListRoute(
            repository = repository,
            onRoomClick = {
                openedMuted = it.membership.notificationMuted
                openedRoom = it.room
            },
            onTabSelect = onTabSelect,
        )
    } else {
        ChatRoomRoute(
            repository = repository,
            room = room,
            knownMuted = openedMuted,
            onBack = { openedRoom = null },
            onOpenMatching = onOpenMatching.takeIf { room.isActiveParty(activeChatRoomId, activePartyId) },
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
    /** 목록에서 들어왔다면 이미 알고 있는 음소거 상태 — 방을 열 때 목록 API 를 다시 부르지 않는다 */
    knownMuted: Boolean? = null,
    onBack: () -> Unit,
    onOpenMatching: (() -> Unit)?,
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
    val peerImageUrls by viewModel.peerImageUrls.collectAsState()
    val muted by viewModel.muted.collectAsState()
    val search by viewModel.searchState.collectAsState()
    val report by viewModel.reportState.collectAsState()

    // 화면이 보이는 동안만 조회·폴링한다. 탭을 옮기거나 앱이 백그라운드로 가면 즉시 멈춘다.
    // 같은 구간 동안 「이 방을 보고 있다」를 알려 푸시 알림이 겹치지 않게 한다([ChatForeground]).
    LifecycleStartEffect(room.id) {
        viewModel.onScreenStart(room.id, knownMuted)
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
            // 아직 확인이 안 온 내 메시지는 목록 끝에 「보내는 중」으로 붙는다 — 소켓 전송은 응답이 없다.
            messages = current.messages.map { it.toUiMessage(peerImageUrls) } + current.pending.map { it.toUiMessage() },
            input = input.text,
            sending = input.sending,
            errorMessage = input.errorMessage,
            onInputChange = viewModel::onInputChange,
            onSend = viewModel::send,
            onBack = onBack,
            muted = muted,
            onToggleMute = viewModel::toggleMuted,
            // 진행 중인 내 방의 채팅방에서는 「나가기」를 **아예 두지 않는다** — 서버가 나간 참여자를
            // 되살리지 못해(chat_room_user 복합키에 leftAt 만 찍힘) 한 번 나가면 운행 내내 대화에
            // 못 돌아온다(실기 QA). 진행 중 판정이 onOpenMatching 의 유무와 같은 근거라 그걸 그대로 본다.
            //
            // 진행 중이 아닌 방에서도 나가기는 되돌릴 수 없다(재합류 경로가 없다) — 그쪽은 메뉴에
            // 남겨 두되 ChatScreen 이 재확인을 받는다.
            canLeave = onOpenMatching == null,
            search = ChatSearchUi(
                open = search.open,
                query = search.query,
                results = search.results?.map { it.toUiMessage(peerImageUrls) },
                loading = search.loading,
                loadingMore = search.loadingMore,
                hasMore = search.hasNext,
                errorMessage = search.errorMessage,
            ),
            onOpenSearch = viewModel::openSearch,
            onCloseSearch = viewModel::closeSearch,
            onSearchQueryChange = viewModel::onSearchQueryChange,
            onSearchRetry = viewModel::retrySearch,
            onSearchLoadMore = viewModel::loadMoreSearch,
            onOpenMatching = onOpenMatching,
            onLeaveChat = viewModel::leaveRoom, // 서버에서 빠진 뒤 14 홈으로
            onTabSelect = onTabSelect,
            reportSubmitting = report.submitting,
            reportOutcome = report.outcome,
            onReportMessage = viewModel::reportMessage,
        )
    }
}

/**
 * 24 채팅방 — **독립 목적지**(`Routes.CHAT_ROOM`). 21 의 「채팅 열기」가 이걸 스택에 쌓는다.
 *
 * 대화 자체는 탭 안의 방 화면과 **완전히 같은 것**을 쓴다([ChatRoomRoute]) — 방 하나에 화면이 둘이면
 * 폴링·읽음 처리·나가기 규칙이 두 벌이 된다. 여기서 더 하는 일은 하나뿐이다: 라우트 인자로 받은
 * roomId 로 방 이름(출발지 → 목적지)을 한 번 조회하는 것. 탭 경로는 목록에서 이미 [ChatRoom] 을
 * 통째로 들고 오지만, 진행 화면에서는 id 밖에 없다.
 *
 * @param activeChatRoomId 진행 중인 방의 **채팅방 id**. 이 화면의 [roomId] 가 그것이면 헤더에
 *   「매칭 화면으로 →」가 뜨고 「나가기」가 사라진다 — 판정의 1순위 근거다([ChatRoom.isActiveParty]).
 * @param activePartyId 진행 중인 방 id. 이 경로의 [ChatRoom] 은 방 상세에서 온 **진짜** partyId 를
 *   들고 있어 보조 근거가 된다(채팅방 id 를 아직 못 찾은 구간을 메운다).
 *   뒤로가기로도 돌아갈 수 있지만, 채팅방에 오래 머문 뒤에는 「어디로 돌아가는 뒤로가기인지」가
 *   사라진다 — 이름 붙은 길을 함께 둔다.
 */
@Composable
fun ChatRoomDestinationRoute(
    repository: ChatRepository,
    roomId: Long,
    activeChatRoomId: Long? = null,
    activePartyId: String? = null,
    onBack: () -> Unit = {},
    onOpenMatching: () -> Unit = {},
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
            onOpenMatching = onOpenMatching.takeIf { current.isActiveParty(activeChatRoomId, activePartyId) },
            onNotParticipant = onNotParticipant,
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

/**
 * 이 채팅방이 **지금 진행 중인 방**의 것인가 — 「매칭 화면으로 →」 배너와 「나가기」 노출을 가르는 판정.
 *
 * 기준은 **채팅방 id** 다([activeChatRoomId]). 예전에는 [ChatRoom.partyId] 와 진행 중인 방 id 를
 * 비교했는데, 채팅 **탭 목록**에서 연 방은 그 값이 0 이다 — 목록 응답(`GET /chat-rooms/me`)에 partyId 가
 * 없어 매퍼가 「모른다」로 0 을 채운다(`UNKNOWN_PARTY_ID`). 그래서 목록 경로에서는 판정이 **언제나
 * 거짓**이었고, 진행 중인 방인데도 배너가 안 뜨고(목록에서 들어온 사용자에게는 그게 유일한 복귀 길이다)
 * 되돌릴 수 없는 「나가기」가 노출됐다(실기 QA). 채팅방 id 는 두 경로 모두 서버가 준 값이라 믿을 수 있다.
 *
 * [activePartyId] 는 **보조 근거**다. 진행 중인 방은 알지만 그 방의 채팅방 id 를 아직 못 찾은 구간이
 * 있어(`ActivePartyViewModel.pollChatRoom` 이 10초 주기로 찾는다) 그동안에도 단독 목적지 경로는
 * 판정할 수 있어야 한다 — 그쪽 [ChatRoom] 은 방 상세에서 온 **진짜** partyId 를 들고 있다.
 * 그래서 partyId 는 [UNKNOWN_PARTY_ID] 가 아닐 때만 본다 — 0 은 「모른다」이고 비교에 쓰면 안 된다.
 *
 * 둘 다 모르면 거짓이다. 그래도 「확인 없는 나가기」로는 새지 않는다 — 재확인은 [ChatScreen] 이
 * **모든 방에** 대해 받는다(나가기는 어느 방에서든 되돌릴 수 없다).
 */
internal fun ChatRoom.isActiveParty(activeChatRoomId: Long?, activePartyId: String? = null): Boolean =
    (activeChatRoomId != null && id == activeChatRoomId) ||
        (activePartyId != null && partyId != UNKNOWN_PARTY_ID && partyId.toString() == activePartyId)

/**
 * 목록 응답으로 만든 방의 `partyId` 자리값 — 「모른다」는 뜻이다
 * (데이터 계층의 `com.moyeota.data.remote.UNKNOWN_PARTY_ID` 와 같은 값).
 * 이 값과 **같은지만** 보고, 이 값으로 다른 무엇과 비교하지 않는다.
 */
private const val UNKNOWN_PARTY_ID = 0L

/**
 * 메시지 목록을 id 로 합친다 — 겹치면 **나중에 온 사본([incoming])이 이긴다.**
 *
 * 예전에는 `(held + incoming).distinctBy { it.id }` 였는데 `distinctBy` 는 **먼저 등장한** 쪽을
 * 남긴다. 그래서 동승자가 메시지를 지우면 서버가 **같은 id** 를 `deleted = true`,
 * `content = "삭제된 메시지입니다"` 로 다시 밀어 주는데도(`ChatMessageBroadcaster.onMessageDeleted`)
 * 화면은 삭제 전 사본을 그대로 들고 있었다 — **지운 말이 그대로 남았다.**
 *
 * `associateBy` 는 같은 키에 **나중 값**을 덮어쓴다. 반환 순서는 믿지 않고 마지막에 다시
 * id 오름차순으로 정렬한다(말풍선 순서는 이 정렬이 유일한 근거다).
 */
internal fun mergeMessagesById(
    held: List<DomainChatMessage>,
    incoming: List<DomainChatMessage>,
): List<DomainChatMessage> =
    (held + incoming).associateBy { it.id }.values.sortedBy { it.id }

/**
 * 서버 메시지 → 화면 표시 모델.
 *
 * "내 메시지" 판정은 Repository 가 계산한 isMine 을 그대로 믿는다(토큰 주체 기준).
 * 발신자 닉네임이 아직 없는 응답은 "동승자" 로 표기한다.
 *
 * @param imageUrls 참여자 조회로 세운 `publicId → 사진` 사전([chatMemberImageUrls]). 메시지에는
 *   사진이 실려 오지 않아 이것 말고는 얼굴을 알 길이 없다. 내 말풍선에는 아바타 자리가 없어
 *   내 메시지는 찾아보지 않고, 사전에 없는 발신자(탈퇴·신원 누락)는 회색 원으로 떨어진다.
 */
internal fun DomainChatMessage.toUiMessage(imageUrls: Map<String, String>): ChatUiMessage = ChatUiMessage(
    text = content,
    isMine = isMine,
    senderName = if (isMine) null else (senderName ?: PEER_FALLBACK_NAME),
    senderImageUrl = if (isMine) null else senderPublicId?.let(imageUrls::get),
    timeLabel = createdAt.toTimeLabel(),
    // 신고가 서버에 보낼 `chatMessageId` 다. 서버에서 온 메시지에는 **항상** 있다.
    id = id,
)

// 확인을 기다리는(또는 실패한) 내 메시지 → 말풍선. 시간 대신 상태를 메타로 보여 준다.
// id 를 싣지 않는다(= NO_SERVER_MESSAGE_ID) — 서버 id 가 아직 없다. 내 메시지라 신고 길도
// 애초에 안 생기지만, 음수 localId 를 그 자리에 넣으면 「id 가 있다」로 보여 판정이 갈린다.
private fun ChatRoomViewModel.PendingMessage.toUiMessage(): ChatUiMessage = ChatUiMessage(
    text = content,
    isMine = true,
    meta = if (failed) PENDING_FAILED_LABEL else PENDING_LABEL,
)

/**
 * 서버에서 돌아온 메시지와 짝지어진 대기 말풍선을 걷어낸다.
 *
 * 짝의 조건은 **내 메시지 + 보낸 뒤에 생긴 id + 같은 내용**이다. id 조건이 없으면 예전에 보낸 같은 문장
 * (“네”, “도착했어요”)이 확인으로 오해된다. 같은 문장을 연달아 두 번 보낼 수도 있으므로
 * 서버 메시지 하나는 대기 하나만 지운다.
 */
internal fun reconcilePending(
    messages: List<DomainChatMessage>,
    pending: List<ChatRoomViewModel.PendingMessage>,
): List<ChatRoomViewModel.PendingMessage> {
    if (pending.isEmpty()) return pending
    val matched = mutableSetOf<Long>()
    return pending.filterNot { waiting ->
        val echo = messages.firstOrNull { message ->
            message.isMine &&
                message.id > waiting.sinceId &&
                message.content == waiting.content &&
                message.id !in matched
        }
        if (echo != null) matched += echo.id
        echo != null
    }
}

/**
 * 신고 실패 → **문구 + 시트를 열어 둘지**.
 *
 * `retryable` 이 정하는 것은 하나뿐이다: 같은 내용으로 **다시 보내서 달라질 수 있는가.**
 * - 연결 실패만 참이다. 그래서 적어 둔 상세를 들고 시트가 열려 있는다.
 * - 「이미 신고한 메시지」는 거짓이다 — 사유를 바꿔 보내도 같은 409 다(실측). 열어 두면
 *   사용자가 빠져나갈 수 없는 시트에 갇힌다.
 * - 참여자 아님·메시지 없음·본인 신고도 거짓이다. 재시도가 아니라 **대상이 틀렸다**는 뜻이라
 *   다시 눌러 봐야 같은 답이다.
 *
 * `INVALID_REQUEST` 의 서버 message 는 필드명을 담고 있어(「chatMessageId: 메시지 ID는 1 이상…」)
 * **그대로 보여 주지 않는다** — 그 상태는 화면이 전송 버튼으로 미리 막는 것이므로, 여기까지
 * 왔다면 막는 쪽이 틀린 것이고 사용자에게는 「보낼 수 없었다」만 말한다.
 */
internal fun Throwable.toChatReportOutcome(): ChatReportOutcome {
    val chat = this as? ChatException
        ?: return ChatReportOutcome(message = "신고를 보내지 못했어요", accepted = false, retryable = true)
    return when {
        chat.isAlreadyReported ->
            ChatReportOutcome("이미 신고한 메시지예요", accepted = false)
        chat.isCannotReportSelf ->
            ChatReportOutcome("내가 보낸 메시지는 신고할 수 없어요", accepted = false)
        chat.isMessageNotFound ->
            ChatReportOutcome("메시지를 찾을 수 없어요. 이미 지워졌을 수 있어요", accepted = false)
        chat.isNotParticipant ->
            ChatReportOutcome("이 방의 참여자가 아니라 신고할 수 없어요", accepted = false)
        chat.isReportDescriptionRequired ->
            ChatReportOutcome("「그 밖의 이유예요」는 상세 내용을 적어야 보낼 수 있어요", accepted = false, retryable = true)
        chat.isInvalidRequest ->
            ChatReportOutcome("신고 내용을 보낼 수 없어요. 사유를 다시 골라 주세요", accepted = false, retryable = true)
        chat.isNetwork ->
            ChatReportOutcome("연결이 끊겼어요. 잠시 뒤 다시 시도해 주세요", accepted = false, retryable = true)
        // 남는 것은 토큰 만료(401)·서버 오류(5xx) 같은 「다음에 되는」 실패다.
        else -> ChatReportOutcome("신고를 보내지 못했어요", accepted = false, retryable = true)
    }
}

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
