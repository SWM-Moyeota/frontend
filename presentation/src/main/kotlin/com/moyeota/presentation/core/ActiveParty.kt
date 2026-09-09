package com.moyeota.presentation.core

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RideStatus
import com.moyeota.domain.repository.ActivePartyRepository
import com.moyeota.domain.repository.ChatRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 진행 중인 방이 지금 어느 단계인가. 서버 status 하나로는 갈리지 않는다 —
 * `DISPATCHING` 은 「기사 찾는 중」과 「배정돼 오는 중」을 함께 담고, 둘은 [Ride.driverId] 로 갈린다
 * (55 리포트의 25b/25c 판정 규칙과 같은 기준을 쓴다).
 */
enum class ActiveStage {
    /** 21 매칭 대기 — 아직 사람을 모으는 중 */
    WAITING,

    /** 25b 기사 찾는 중 */
    DRIVER_SEARCH,

    /** 25c·25 택시 오는 중 */
    DRIVER_COMING,

    /** 26 운행 중 */
    ONGOING,

    /** 끝났거나(COMPLETED·CANCELED) 단계로 옮길 수 없는 상태 */
    NONE,
}

val Ride.activeStage: ActiveStage
    get() = when {
        status == RideStatus.RECRUITING || status == RideStatus.MATCHED -> ActiveStage.WAITING
        status == RideStatus.DISPATCHING && driverId == null -> ActiveStage.DRIVER_SEARCH
        status == RideStatus.DISPATCHING -> ActiveStage.DRIVER_COMING
        status == RideStatus.ONGOING -> ActiveStage.ONGOING
        else -> ActiveStage.NONE
    }

/** 배너·카드에 쓰는 단계 문구. 각 단계 화면의 헤더 제목과 같은 말을 쓴다. */
val Ride.activeStageLabel: String
    get() = when (activeStage) {
        ActiveStage.WAITING -> "같이 탈 사람 찾는 중"
        ActiveStage.DRIVER_SEARCH -> "기사님 찾는 중"
        ActiveStage.DRIVER_COMING -> "택시 오는 중"
        ActiveStage.ONGOING -> "운행 중"
        ActiveStage.NONE -> "진행 중 탑승"
    }

/** 진행 중인 방의 채팅방을 다시 찾아보는 주기. 정원이 차야 방이 생기므로 한 번에 못 찾는 게 정상이다. */
private const val CHAT_ROOM_POLL_INTERVAL_MS = 10_000L

/**
 * "지금 내가 타고 있는 방"을 화면 전체가 함께 보는 홀더.
 *
 * **NavHost 바깥(액티비티 스코프)에 한 인스턴스만 둔다** — 홈·합승·34 내 탑승 배너와 앱 시작 시
 * 단계 복귀가 같은 값을 봐야 하고, 화면마다 만들면 탭을 옮길 때마다 같은 조회를 반복한다
 * ([UserProfileViewModel] 과 같은 배치).
 *
 * [ActivePartyRepository.resolve] 는 실패해도 예외를 던지지 않고 null 을 준다 — 그래서
 * Loading/Error 3상태를 두지 않는다. 못 찾으면 배너를 숨기는 것이 전부다.
 */
class ActivePartyViewModel(
    private val activePartyRepository: ActivePartyRepository,
    private val chatRepository: ChatRepository,
) : ViewModel() {

    private val _ride = MutableStateFlow<Ride?>(null)
    val ride: StateFlow<Ride?> = _ride.asStateFlow()

    /**
     * 첫 [resolve] 가 끝났는가. 앱 시작 시 「홈 대신 단계 화면」 판단은 이 값이 true 가 된 뒤에만 한다
     * — 아직 모르는 상태(null)와 없는 상태(null)를 구분할 길이 이것뿐이다.
     */
    private val _resolvedOnce = MutableStateFlow(false)
    val resolvedOnce: StateFlow<Boolean> = _resolvedOnce.asStateFlow()

    /**
     * 진행 중인 방에 딸린 채팅방 id. 정원이 차기 전에는 방이 없어 null 이고, 그때 21·25 의
     * 「채팅 열기」는 아예 그리지 않는다 — 눌러도 열 것이 없는 버튼을 두지 않는다.
     */
    private val _chatRoomId = MutableStateFlow<Long?>(null)
    val chatRoomId: StateFlow<Long?> = _chatRoomId.asStateFlow()

    // 위 채팅방 id 가 어느 방의 것인지. 방이 바뀌면 즉시 무효로 만든다.
    private var chatRoomPartyId: Long? = null

    // 재조회 중복 방지 — 홈 진입 LaunchedEffect 와 init 이 같은 프레임에 겹친다.
    private var refreshing = false

    init {
        refresh()
    }

    /** 화면 복귀(onResume·탭 진입)마다 부른다. 로컬 기억이 있으면 상세 1회로 끝나는 가벼운 조회다. */
    fun refresh() {
        if (refreshing) return
        refreshing = true
        viewModelScope.launch {
            try {
                reload()
            } finally {
                refreshing = false
            }
        }
    }

    /** 방 생성·합류 성공 직후. 기억을 심고 곧바로 다시 읽어 배너·단계가 같은 값을 보게 한다. */
    fun rememberParty(partyId: String) {
        viewModelScope.launch {
            runCatching { activePartyRepository.remember(partyId) }
            reload()
        }
    }

    /** 나가기 성공·FINISHED/CANCELED 확인. 로컬 기억까지 지운다. */
    fun clearParty() {
        viewModelScope.launch {
            runCatching { activePartyRepository.clear() }
            _ride.value = null
            _chatRoomId.value = null
            chatRoomPartyId = null
        }
    }

    /**
     * 로그아웃·세션 만료용 **메모리 초기화**. 로컬 기억은 건드리지 않는다 —
     * 그 기억은 계정에 매인 것이고, 같은 계정으로 다시 로그인하면 그대로 되살아나야 한다.
     */
    fun forget() {
        _ride.value = null
        _chatRoomId.value = null
        chatRoomPartyId = null
        _resolvedOnce.value = false
    }

    /**
     * 채팅방이 생겼는지 주기적으로 확인한다. **호출자(NavHost)의 스코프에서 돌린다** —
     * 컴포지션이 사라지면 함께 멈춘다(25 pollDriver 와 같은 배치).
     *
     * 모집 중(21)에는 아예 확인하지 않는다. 채팅방은 정원이 찬 뒤 만들어지므로 그 전의 조회는
     * 반드시 헛걸음이다. 한 번 찾으면 그 뒤로는 조회하지 않는다.
     */
    suspend fun pollChatRoom() {
        while (currentCoroutineContext().isActive) {
            delay(CHAT_ROOM_POLL_INTERVAL_MS)
            val current = _ride.value ?: continue
            if (_chatRoomId.value != null) continue
            if (current.activeStage == ActiveStage.WAITING) continue
            lookupChatRoom(current)
        }
    }

    private suspend fun reload() {
        val resolved = activePartyRepository.resolve()
        _ride.value = resolved
        _resolvedOnce.value = true
        val partyId = resolved?.id?.toLongOrNull()
        if (partyId == null || partyId != chatRoomPartyId) {
            _chatRoomId.value = null
            chatRoomPartyId = null
        }
        if (resolved == null) return
        if (_chatRoomId.value == null && resolved.activeStage != ActiveStage.WAITING) {
            lookupChatRoom(resolved)
        }
    }

    /**
     * 방 id → 채팅방 id. 서버에 「이 방의 채팅방」을 직접 묻는 API 가 없어
     * `GET /chat-rooms/me` 를 훑는다. 실패는 삼킨다 — 채팅 버튼이 늦게 뜰 뿐이다.
     */
    private suspend fun lookupChatRoom(ride: Ride) {
        val partyId = ride.id.toLongOrNull() ?: return
        val rooms = runCatching { chatRepository.getMyChatRooms() }.getOrNull() ?: return
        val room = rooms.firstOrNull { it.room.partyId == partyId } ?: return
        chatRoomPartyId = partyId
        _chatRoomId.value = room.room.id
    }

    companion object {
        fun factory(
            activePartyRepository: ActivePartyRepository,
            chatRepository: ChatRepository,
        ) = viewModelFactory {
            initializer { ActivePartyViewModel(activePartyRepository, chatRepository) }
        }
    }
}

/**
 * 14 홈 · 17 합승 상단의 「진행 중 탑승 · 보기」 배너.
 *
 * 문구는 단계별로 갈린다 — 「진행 중 탑승」 한마디로는 지금 눌러야 하는지(택시가 오는 중)와
 * 그냥 기다리면 되는지(사람 찾는 중)를 구분할 수 없다.
 */
@Composable
fun ActiveRideBanner(
    ride: Ride,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            // 그림자도 토큰에서 뽑는다 — 예전 배너는 같은 값을 0x29085AF5 로 박아 두고 있었다
            .shadow(8.dp, RoundedCornerShape(18.dp), spotColor = MoyeotaColor.Primary500.copy(alpha = 0.16f))
            .clip(RoundedCornerShape(18.dp))
            .background(MoyeotaColor.SurfaceCanvas)
            .clickable { onClick() }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(MoyeotaColor.Primary500, CircleShape))
        Spacer(Modifier.size(10.dp))
        Text(
            text = ride.activeStageLabel,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MoyeotaColor.InkPrimary,
            maxLines = 1,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = ride.destination,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MoyeotaColor.TextMute,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = "보기 ›",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MoyeotaColor.Primary600,
        )
    }
}

/**
 * 21·25 의 「채팅 열기」. **채팅방이 실제로 있을 때만** 그린다(호출부가 null 검사로 가른다).
 *
 * 26 운행 중에는 같은 이름의 버튼이 화면 레이아웃 안에 이미 박혀 있어 여기서 쓰지 않는다.
 */
@Composable
fun OpenChatButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MoyeotaColor.Primary50)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "채팅 열기",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MoyeotaColor.Primary600,
        )
    }
}
