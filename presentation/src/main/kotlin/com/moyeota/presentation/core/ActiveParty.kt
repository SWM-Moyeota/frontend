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
import com.moyeota.domain.model.ChatRoomStatus
import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RideStatus
import com.moyeota.domain.repository.ActivePartyRepository
import com.moyeota.domain.repository.ChatRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
        // 정원이 찬 방(COMPLETED)은 「찾는 중」이 아니다. 기사 모드에선 순간이라 거의 안 보이고,
        // 1차 배포 모드에선 채팅으로 만나는 동안 계속 이 문구다.
        ActiveStage.WAITING -> if (status == RideStatus.MATCHED) "같이 탈 사람이 모였어요" else "같이 탈 사람 찾는 중"
        ActiveStage.DRIVER_SEARCH -> "기사님 찾는 중"
        ActiveStage.DRIVER_COMING -> "택시 오는 중"
        ActiveStage.ONGOING -> "운행 중"
        ActiveStage.NONE -> "진행 중 탑승"
    }

/** 진행 중인 방의 채팅방을 다시 찾아보는 주기. 정원이 차야 방이 생기므로 한 번에 못 찾는 게 정상이다. */
private const val CHAT_ROOM_POLL_INTERVAL_MS = 10_000L

/**
 * 「방금 끝난 조회」로 볼 창. 화면 진입 한 번에 여러 효과가 연달아 [ActivePartyViewModel.refresh] 를
 * 부르는 것만 흡수하면 되므로 **짧게** 잡는다 — QA 가 잰 중복은 25ms 간격이었고(119 D-2), 전이
 * 애니메이션을 타고 들어오는 재구성도 수백 ms 안이다.
 *
 * 길게 잡으면 그만큼 배너·단계가 늦게 바뀐다. 특히 정원이 차는 순간 21 이 거는 재조회
 * (`MainNavGraph` 의 `onMatchingStarted`)까지 삼키면 채팅방 id 가 한 박자 늦는다 — 그래서 0.5초다.
 */
private const val RESOLVE_DEDUP_WINDOW_MS = 500L

/**
 * 같은 조회를 연달아 날리지 않게 막는 문. 「진행 중인 조회가 있다」와 「방금 끝난 조회가 있다」를
 * 함께 본다 — 앞엣것만 보던 `refreshing` 플래그로는 **순서대로** 들어오는 중복(앱 재개 직후의
 * 탭 진입 등)이 그대로 통과했다.
 *
 * [ActivePartyViewModel] 밖에 둔 이유는 하나뿐이다 — viewModelScope 없이 시계만 가짜로 넣어
 * 검증할 수 있게 하려는 것(presentation 테스트에는 coroutines-test 가 없다).
 *
 * 시계는 [System.nanoTime] 이다. 벽시계(`currentTimeMillis`)는 사용자가 시간을 바꾸거나 NTP 가
 * 보정하면 뒤로 갈 수 있고, 그러면 창이 닫히지 않은 것처럼 보여 재조회가 오래 막힌다.
 */
internal class ResolveGate(
    private val windowMs: Long = RESOLVE_DEDUP_WINDOW_MS,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private var inFlight = 0
    private var finishedAtMs: Long? = null

    /** 겹치지 않을 때만 들어간다. 들어갔으면 반드시 [leave] 로 닫는다. */
    fun tryEnter(): Boolean {
        if (inFlight > 0) return false
        finishedAtMs?.let { if (nowMs() - it < windowMs) return false }
        inFlight++
        return true
    }

    /**
     * 창을 무시하고 들어간다. **조회의 전제가 방금 바뀐 경우**에만 쓴다(합류·방 생성 직후) —
     * 그때 「방금 끝난 조회」는 합류 **이전**의 사실이라 재사용하면 안 된다. 억제는 그래서 비대칭이다.
     */
    fun enterNow() {
        inFlight++
    }

    /** 조회가 끝났다. 실패도 창에 넣는다 — 같은 실패를 몇십 ms 뒤에 반복해 봐야 결과가 같다. */
    fun leave() {
        if (inFlight > 0) inFlight--
        finishedAtMs = nowMs()
    }
}

/**
 * 합류·생성 직후 방 상세를 **다시 읽어야 하는가**. 홀더가 이미 그 방을 들고 있으면 읽을 것이 없다
 * — 21 이 곧바로 자기 상세를 읽고, 배너·채팅 헤더가 보는 값은 이미 그 방이다.
 *
 * id 비교는 Long 으로 한다. 호출부가 `ride.id`(매퍼가 만든 문자열)와 `joinedPartyId.toString()` 두
 * 가지 길로 들어와 문자열 모양이 어긋날 수 있다.
 */
internal fun needsDetailAfterRemember(held: Ride?, partyId: Long): Boolean =
    held?.id?.toLongOrNull() != partyId

/**
 * 지금 채팅방을 찾아야 하는가([ActivePartyViewModel.pollChatRoom] 의 판정).
 *
 * `visible` 이 맨 앞인 게 이 함수의 요점이다 — **보이지 않는 동안에는 나머지 조건을 보지 않는다.**
 * 예전에는 이 조건이 없어서, 진행 중인 방이 있고 채팅방이 아직 없는 사용자는 **어느 화면에 있든, 앱이
 * 백그라운드여도** 10초마다 `GET /chat-rooms/me` 를 보냈다(QA 119 D-1 과 같은 성격의 누수,
 * `_workspace/120_compose-builder_explore-poll-gate.md` §5 가 유일하게 남은 건으로 지목한 자리).
 *
 * 나머지 세 조건은 예전 루프가 매 주기 `continue` 로 걸러낸 것과 같다 — 방이 없으면 찾을 대상이 없고,
 * 이미 찾았으면 다시 찾을 이유가 없고(그래서 **찾는 순간 루프가 끝난다**), 끝난 방([ActiveStage.NONE])의
 * 채팅방은 열어 줄 데가 없다.
 */
internal fun shouldPollChatRoom(visible: Boolean, ride: Ride?, chatRoomId: Long?): Boolean =
    visible && chatRoomId == null && ride != null && ride.activeStage != ActiveStage.NONE

/**
 * "지금 내가 타고 있는 방"을 화면 전체가 함께 보는 홀더.
 *
 * **NavHost 바깥(액티비티 스코프)에 한 인스턴스만 둔다** — 홈·합승 배너와 앱 시작 시
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

    /**
     * 앱 화면이 지금 보이는가. [pollChatRoom] 의 게이트다.
     *
     * **기본값이 true 인 이유**: 이 값을 내려 주는 배선([onHostStart]·[onHostStop])은 `MainNavGraph`
     * 에 있고 그 파일은 다른 에이전트가 쥐고 있다. 배선이 붙기 전에는 지금까지와 똑같이 돌아야 한다
     * — false 로 시작하면 배선이 빠진 채로 머지됐을 때 21 의 「채팅 열기」가 **영영 뜨지 않는다**.
     */
    private val hostVisible = MutableStateFlow(true)

    // 재조회 중복 방지 — 홈 진입 LaunchedEffect·앱 재개 LifecycleResumeEffect·init 이 같은 프레임에
    // 겹치거나 몇십 ms 차로 잇따라 들어온다. 겹침만 막던 플래그를 「방금 끝난 조회」까지 보는 문으로 바꿨다.
    private val resolveGate = ResolveGate()

    init {
        refresh()
    }

    /**
     * 화면 복귀(onResume·탭 진입)마다 부른다. 로컬 기억이 있으면 상세 1회로 끝나는 가벼운 조회다.
     *
     * 진입 한 번에 여러 효과가 겹쳐 부르는 것은 [ResolveGate] 가 흡수한다 — 진행 중인 조회가 있거나
     * [RESOLVE_DEDUP_WINDOW_MS] 안에 끝난 조회가 있으면 그 결과를 그대로 쓴다.
     */
    fun refresh() {
        if (!resolveGate.tryEnter()) return
        viewModelScope.launch {
            try {
                reload()
            } finally {
                resolveGate.leave()
            }
        }
    }

    /**
     * 방 생성·합류 성공 직후. 기억을 심고, **홀더가 아직 그 방을 모를 때만** 상세를 읽는다.
     *
     * 조회를 아예 걷어내지 않은 이유(QA 119 D-2 의 두 선택지 중 뒤쪽을 고른 근거): [ride] 는 앱 전체가
     * 공유하는 **유일한 [Ride] 객체**다 — 14·17 배너, 24 채팅 헤더의 「매칭 화면으로 →」(`MainNavGraph`
     * 가 `activeRide?.id` 로 가른다), [pollChatRoom] 의 게이트가 모두 이 값에 달려 있다. 21 이 읽는
     * 상세는 21 자신의 상태로만 쓰이고 이 홀더로 흘러들지 않으므로, 여기서 조회를 지우면 홀더는 빈 채로
     * 남는다. 그러면
     * - 홈·합승으로 돌아갈 때 각 화면의 `LaunchedEffect { refresh() }` 가 채워 주긴 하지만 **한 왕복
     *   동안 배너가 비어 보이고**(그 조회가 실패하면 다음 복귀까지 계속 비어 있다),
     * - 그 전에 21 에서 채팅을 열면 헤더의 매칭 화면 링크가 사라지며,
     * - [pollChatRoom] 이 `ride == null` 에서 멈춰 21 의 「채팅 열기」가 아예 뜨지 않는다
     *   (채팅방은 합류 시점에 생기므로 21 에서 떠야 하는 버튼이다).
     *
     * 그래서 지우는 대신 **겹치는 것만** 지운다.
     * - 이미 그 방을 들고 있으면([needsDetailAfterRemember]) 상세를 건너뛴다 — 서버가 「이미 이 방
     *   멤버」라고 답해 합류 교착에서 복구된 경로가 여기로 온다. 채팅방 id 만 아직이면 그것만 따로
     *   찾는다(채팅방 목록 1회, 방 상세 0회).
     * - 읽어야 할 때는 창을 무시하고 읽는다([ResolveGate.enterNow]) — 직전 조회는 합류 **이전**의
     *   사실이라 재사용하면 방금 들어간 방을 놓친다. 대신 끝난 시각을 남겨, 전이 도중의 재구성으로
     *   뒤따라 들어오는 `refresh()` 를 흡수한다.
     *
     * 21 진입에 남는 상세 1건(21 자신의 조회)은 계층이 달라 이 파일에서 지울 수 없다 —
     * `RideRepository.getPartyDetail` 에 짧은 TTL 캐시를 두거나 21 의 SSE 첫 스냅샷을 홀더로 밀어
     * 넣어야 한다.
     *
     * 숫자가 아니거나 0 이하인 id 는 심지도, 읽지도 않는다 — 그런 값은 `GET /matching/rooms/0` 404 로만
     * 끝난다([com.moyeota.data.repository.RemoteActivePartyRepository] 가 같은 기준으로 한 번 더 막는다).
     */
    fun rememberParty(partyId: String) {
        val id = partyId.toLongOrNull()?.takeIf { it > 0 } ?: return
        viewModelScope.launch {
            runCatching { activePartyRepository.remember(id.toString()) }
            val held = _ride.value
            if (held != null && !needsDetailAfterRemember(held, id)) {
                if (_chatRoomId.value == null && held.activeStage != ActiveStage.NONE) {
                    lookupChatRoom(id)
                }
                return@launch
            }
            resolveGate.enterNow()
            try {
                reload()
            } finally {
                resolveGate.leave()
            }
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
     * 채팅방 캐시만 비운다 — 「채팅방 나가기」 성공 뒤, 또는 캐시된 방에서 403(참여자 아님)을 받았을 때.
     * 방(party)은 그대로이므로 [pollChatRoom] 이 다음 주기에 다시 찾는다(서버가 되살려 주면 붙는다).
     * 비우지 않으면 26 의 「채팅 열기」가 죽은 방 id 로 계속 403 을 맞는다(실기 QA).
     */
    fun forgetChatRoom() {
        _chatRoomId.value = null
        chatRoomPartyId = null
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
     * 앱 화면이 보이기 시작했다 / 가려졌다. **`MainNavGraph` 가 호스트 lifecycle 로 불러 줘야 한다**:
     *
     * ```
     * LifecycleStartEffect(activePartyViewModel) {
     *     activePartyViewModel.onHostStart()
     *     onStopOrDispose { activePartyViewModel.onHostStop() }
     * }
     * ```
     *
     * **RESUMED 가 아니라 STARTED** 다 — NavHost 는 전환 애니메이션이 끝난 뒤에야 RESUMED 로 올리므로
     * 복귀 첫 조회가 그만큼 늦는다. 같은 이유로 17 탐색(`ExploreRoute.onScreenStart`)·24 채팅
     * (`ChatRoute`)·GPS 구독(`MyLocationState`)이 모두 STARTED 를 쓴다.
     *
     * 이 ViewModel 은 NavHost **바깥**(액티비티 스코프)이라 화면 전환으로는 가려지지 않는다 —
     * 여기서 잡히는 것은 **앱 자체가 백그라운드로 가는 순간**(ON_STOP)이다. 그래서 신호 하나로 족하고,
     * 화면별 가시성은 볼 필요가 없다(홈이든 21 이든 진행 중 방의 채팅방은 똑같이 필요하다).
     */
    fun onHostStart() {
        hostVisible.value = true
    }

    /** @see onHostStart */
    fun onHostStop() {
        hostVisible.value = false
    }

    /**
     * 채팅방이 생겼는지 주기적으로 확인한다. **호출자(NavHost)의 스코프에서 돌린다** —
     * 컴포지션이 사라지면 함께 멈춘다(25 pollDriver 와 같은 배치).
     *
     * 다만 NavHost 컴포지션은 **앱이 백그라운드로 가도 살아 있다.** 그래서 컴포지션 스코프만으로는
     * 부족하고, [hostVisible] 로 한 번 더 막는다([shouldPollChatRoom]) — 가시성·방·채팅방 세 흐름을
     * `combine` + `collectLatest` 로 묶어 **조건이 깨지면 진행 중인 조회와 `delay` 가 함께 죽는다**
     * (17 탐색이 쓴 모양과 같다. 깨어나 아무 일도 안 하는 주기조차 남기지 않는다).
     *
     * 멈추는 조건: 앱이 가려짐 · 진행 중인 방이 없음 · **채팅방을 찾음** · 끝난 방.
     * 다시 켜지는 조건: 그 조건이 되돌아감 — 앱 복귀, 방 생성·합류([rememberParty]),
     * 그리고 「채팅방 나가기」로 캐시를 버린 뒤([forgetChatRoom]). 되돌아오면 **10초 뒤** 첫 조회다
     * (즉시 1회를 붙이지 않은 이유: 홈·합승 진입과 앱 복귀는 이미 `refresh()` → `reload()` 가
     * 채팅방을 **그 자리에서** 한 번 찾는다. 즉시 조회를 또 붙이면 복귀마다 2건이 된다).
     *
     * 모집 중(21)에도 찾는다 — 서버가 채팅방을 **합류 시점에** 만든다(Backend #109). 예전엔 정원이 차야
     * 생겨서 대기 중엔 건너뛰었는데, 그대로 두면 1차 배포 모드(정원 차도 대기 단계)에선 영영 안 뜬다.
     */
    suspend fun pollChatRoom() {
        combine(hostVisible, _ride, _chatRoomId) { visible, ride, chatRoomId ->
            // 방 id 만 꺼내 흘린다. Ride 객체를 그대로 흘리면 status 가 바뀔 때마다 패스가 다시 시작돼
            // 10초 타이머가 초기화된다.
            ride.takeIf { shouldPollChatRoom(visible, ride, chatRoomId) }?.id?.toLongOrNull()
        }
            .distinctUntilChanged()
            .collectLatest { partyId ->
                if (partyId == null) return@collectLatest
                while (currentCoroutineContext().isActive) {
                    delay(CHAT_ROOM_POLL_INTERVAL_MS)
                    lookupChatRoom(partyId)
                }
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
        if (_chatRoomId.value == null && resolved.activeStage != ActiveStage.NONE && partyId != null) {
            lookupChatRoom(partyId)
        }
    }

    /**
     * 방 id → 채팅방 id. 서버에 「이 방의 채팅방」을 직접 묻는 API 가 없어
     * `GET /chat-rooms/me` 를 훑는다. 실패는 삼킨다 — 채팅 버튼이 늦게 뜰 뿐이다.
     *
     * 방 id 를 [Ride] 가 아니라 Long 으로 받는다 — 호출부 세 곳(복귀 조회·합류 직후·폴링) 중
     * 폴링은 Ride 객체를 들고 있을 이유가 없고(위 `combine` 참고), 나머지 둘도 id 만 쓴다.
     */
    private suspend fun lookupChatRoom(partyId: Long) {
        val rooms = runCatching { chatRepository.getMyChatRooms() }.getOrNull() ?: return
        // 목록 응답에는 partyId 가 없다(서버 ChatRoomUserResult) — 목록만 보면 0 이라 영영 못 찾는다.
        // 목록에 이미 값이 있으면 그걸 쓰고, 없으면 **닫히지 않은 방만** 상세를 읽어 partyId 를 확인한다.
        // 찾는 순간 채팅방 id 를 기억하고 이후 폴링은 멈추므로(pollChatRoom) 상세 조회는 몇 번 안 된다.
        val roomId = rooms.firstOrNull { it.room.partyId == partyId }?.room?.id
            ?: rooms.asSequence()
                .filter { it.room.partyId == 0L && it.room.status != ChatRoomStatus.CLOSED }
                .firstOrNull { candidate ->
                    runCatching { chatRepository.getChatRoom(candidate.room.id) }.getOrNull()?.partyId == partyId
                }?.room?.id
            ?: return
        chatRoomPartyId = partyId
        _chatRoomId.value = roomId
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
