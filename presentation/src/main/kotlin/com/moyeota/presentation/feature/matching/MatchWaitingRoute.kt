package com.moyeota.presentation.feature.matching

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RideStatus
import com.moyeota.domain.repository.RideRepository
import com.moyeota.presentation.core.BackStateScaffold
import com.moyeota.presentation.core.ErrorBox
import com.moyeota.presentation.core.LoadingBox
import com.moyeota.presentation.core.location.rememberMyLocationState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 방 상세 폴링 간격. 매칭은 서버가 알아서 시작하므로 앱은 전이만 지켜본다. */
private const val PARTY_POLL_INTERVAL_MS = 4_000L

class MatchWaitingViewModel(
    private val repository: RideRepository,
    private val partyId: Long,
) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data class Success(val ride: Ride) : UiState
        data class Error(val message: String) : UiState
    }

    /**
     * 액션 결과. 「그만 찾기」(left)와 1차 배포 모드의 「합승 완료」(finished) — 둘 다 성공하면 방을 떠난다.
     * 준비/매칭 시작은 백엔드에서 삭제됐다.
     */
    data class ActionState(
        val inProgress: Boolean = false,
        val errorMessage: String? = null,
        val left: Boolean = false,
        val finished: Boolean = false,
    )

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _actionState = MutableStateFlow(ActionState())
    val actionState: StateFlow<ActionState> = _actionState.asStateFlow()

    init {
        refresh()
        observeAutoMatching()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            _uiState.value = try {
                UiState.Success(repository.getPartyDetail(partyId))
            } catch (e: Exception) {
                UiState.Error("매칭 정보를 불러오지 못했어요")
            }
        }
    }

    // 정원이 차면 서버가 스스로 기사 매칭을 시작한다(PartyApplicationService.join 내부) — 기사 모드일 때.
    // 1차 배포 모드에선 COMPLETED 에 머물다가 누군가의 「합승 완료」나 30분 스윕으로 FINISHED 가 된다.
    // 앱은 트리거하지 않고 상세를 주기적으로 다시 읽어 인원 현황과 status 전이만 관찰한다.
    // 폴링 실패는 일시적 네트워크 문제일 뿐이라 화면을 에러로 덮지 않고 다음 주기를 기다린다.
    private fun observeAutoMatching() {
        viewModelScope.launch {
            while (isActive) {
                delay(PARTY_POLL_INTERVAL_MS)
                val ride = runCatching { repository.getPartyDetail(partyId) }.getOrNull() ?: continue
                _uiState.value = UiState.Success(ride)
                // 배차 이후는 25 배차 현황이, 닫힌 방은 Route 의 onPartyClosed 가 이어받는다 — 폴링은 여기서 끝낸다
                if (ride.status !in WAITING_STATUSES) return@launch
            }
        }
    }

    fun leaveParty() = runAction(failureMessage = "방에서 나가지 못했어요") {
        // 나가는 주체는 Bearer 토큰이 정한다 — memberId 를 넘기지 않는다 (22 보고서 §2)
        repository.leaveParty(partyId)
        _actionState.update { it.copy(left = true) }
    }

    /**
     * 1차 배포 모드의 「합승 완료」 — 기사 없이 방을 닫는다. 참여자 누구든 한 명이 누르면 **모두의** 방이 끝난다.
     * 정원이 안 찼거나(409 PARTY_NOT_COMPLETED) 기사가 붙었으면(409 DRIVER_ALREADY_ASSIGNED) 서버가 거절한다.
     */
    fun finishParty() = runAction(failureMessage = "합승을 완료하지 못했어요") {
        repository.finishParty(partyId)
        _actionState.update { it.copy(finished = true) }
    }

    // 액션 공통: 중복 실행 차단 → 진행 표시 → 실패 시 한국어 메시지
    private fun runAction(failureMessage: String, block: suspend () -> Unit) {
        if (_actionState.value.inProgress) return
        viewModelScope.launch {
            _actionState.update { it.copy(inProgress = true, errorMessage = null) }
            try {
                block()
                _actionState.update { it.copy(inProgress = false) }
            } catch (e: Exception) {
                _actionState.update { it.copy(inProgress = false, errorMessage = failureMessage) }
            }
        }
    }

    companion object {
        // 아직 사람을 모으는 중이거나(ACTIVE) 정원이 찬(COMPLETED) 상태. 이 밖으로 나가면 배차가 시작됐거나 방이 닫힌 것이다.
        private val WAITING_STATUSES = setOf(RideStatus.RECRUITING, RideStatus.MATCHED)

        fun factory(repository: RideRepository, partyId: Long) = viewModelFactory {
            initializer { MatchWaitingViewModel(repository, partyId) }
        }
    }
}

/**
 * 서버가 나가기를 허용하는가. 서버 `ensureRecruiting` = ACTIVE 또는 COMPLETED.
 *
 * 기사 모드에선 COMPLETED 가 순간이라(곧 MATCHING) 사실상 ACTIVE 에서만 나갈 수 있고, 눌러 봐야 409 다 —
 * 그래서 예전처럼 막는다. 1차 배포 모드에선 COMPLETED 에 머무르므로 거기서도 나갈 수 있어야 한다
 * (나가면 서버가 방을 ACTIVE 로 되돌려 다시 모집한다).
 */
internal fun canLeaveParty(status: RideStatus, taxiEnabled: Boolean): Boolean =
    status == RideStatus.RECRUITING || (!taxiEnabled && status == RideStatus.MATCHED)

/**
 * 1차 배포 모드에서 「합승 완료」를 보여 줄 때 — 정원이 찼고(COMPLETED) 기사가 없을 때.
 * 기사 모드에선 절대 보이지 않는다(정원이 차면 곧바로 25 로 넘어간다).
 */
internal fun canFinishParty(status: RideStatus, taxiEnabled: Boolean): Boolean =
    !taxiEnabled && status == RideStatus.MATCHED

// 21 매칭 대기 — 파티 상세 폴링 + 나가기/합승 완료 진입점. partyId 없으면 기존 더미 화면 유지.
@Composable
fun MatchWaitingRoute(
    repository: RideRepository,
    partyId: Long?,
    /**
     * 서버 운영 설정의 택시 모드([AppConfig.taxiEnabled][com.moyeota.domain.model.AppConfig.taxiEnabled]).
     * false(1차 배포)면 정원이 찼을 때 기사 대기 대신 「합승 완료」를 보여 준다.
     */
    taxiEnabled: Boolean = true,
    onCancelSearch: () -> Unit = {},
    /** 방을 유지한 채 화면만 벗어난다(나가기가 막힌 단계의 뒤로가기) */
    onExitKeepingParty: () -> Unit = {},
    onCardClick: () -> Unit = {},
    onMatchingStarted: () -> Unit = {},
    /**
     * 방이 닫혔다(FINISHED·CANCELED) — 내가 「합승 완료」를 눌렀거나, 다른 참여자가 눌렀거나, 30분 스윕이 닫았다.
     * 호출자는 진행 중 기억을 지우고 홈으로 보낸다.
     */
    onPartyClosed: () -> Unit = {},
    /** 이 방의 채팅방을 여는 길. 채팅방이 아직 없으면 null — 화면이 버튼을 그리지 않는다 */
    onOpenChat: (() -> Unit)? = null,
) {
    if (partyId == null) {
        MatchWaitingScreen(
            onCancelSearch = onCancelSearch,
            onExitKeepingParty = onExitKeepingParty,
            onCardClick = onCardClick,
            onOpenChat = onOpenChat,
        )
        return
    }

    val viewModel: MatchWaitingViewModel = viewModel(
        key = "waiting-$partyId",
        factory = MatchWaitingViewModel.factory(repository, partyId),
    )
    val state by viewModel.uiState.collectAsState()
    val action by viewModel.actionState.collectAsState()

    // 지도의 파란 점. 권한을 자동 요청하지 않는다 — 대기 화면의 본질은 지도가 아니라 「기다림」이라,
    // 여기서 다이얼로그를 띄우면 흐름을 끊는다. 이미 허용돼 있으면(14·17 에서 받았을 것) 점이 뜬다.
    val myLocation = rememberMyLocationState(autoRequestPermission = false)

    LaunchedEffect(action.left) {
        if (action.left) onCancelSearch()
    }
    LaunchedEffect(action.finished) {
        if (action.finished) onPartyClosed()
    }

    // 매칭 시작 버튼이 없어졌으므로, 25 배차 현황으로의 이동은 서버 status 전이가 유일한 신호다.
    // 닫힘(FINISHED·CANCELED)도 같다 — 다른 참여자의 「합승 완료」나 스윕은 폴링으로만 알 수 있다.
    val status = (state as? MatchWaitingViewModel.UiState.Success)?.ride?.status
    LaunchedEffect(status) {
        when (status) {
            RideStatus.DISPATCHING, RideStatus.ONGOING -> onMatchingStarted()
            RideStatus.COMPLETED, RideStatus.CANCELED -> onPartyClosed()
            else -> Unit
        }
    }

    // 탭바가 없는 화면 — 로딩·에러에서도 뒤로가기(=탐색 취소)를 남긴다 (QA F-1 동류)
    when (val current = state) {
        // 로딩·에러에서는 방 상태를 모른다. 나가기를 걸면 매칭 중인 방에 409 를 쏘게 되므로
        // 여기서의 뒤로가기는 **방을 유지한 채** 화면만 벗어난다.
        MatchWaitingViewModel.UiState.Loading -> BackStateScaffold("같이 탈 사람 찾는 중", onExitKeepingParty) {
            LoadingBox()
        }
        is MatchWaitingViewModel.UiState.Error -> BackStateScaffold("같이 탈 사람 찾는 중", onExitKeepingParty) {
            ErrorBox(message = current.message, onRetry = viewModel::refresh)
        }
        is MatchWaitingViewModel.UiState.Success -> {
            val ride = current.ride
            MatchWaitingScreen(
                ride = ride,
                foundCount = ride.members.size,
                conditionLabel = "${ride.capacity}인",
                radiusLabel = radiusLabel(ride),
                myLocation = myLocation.coordinates,
                taxiEnabled = taxiEnabled,
                actionInProgress = action.inProgress,
                actionErrorMessage = action.errorMessage,
                onCancelSearch = viewModel::leaveParty, // 나가기 성공 시 14 홈
                onFinish = viewModel::finishParty, // 합승 완료 성공 시 14 홈
                onExitKeepingParty = onExitKeepingParty,
                onCardClick = onCardClick,
                onOpenChat = onOpenChat,
            )
        }
    }
}

/**
 * 21 「탐색 반경」 라벨. 방 생성 시 고른 값을 그대로 보여 준다 — 예전에는 화면 기본값 "1km" 가
 * 서버 값과 무관하게 늘 떠 있었다(QA D-3).
 *
 * 출발지·도착지 반경은 16 에서 따로 고를 수 있어 다를 수 있다. 같으면 한 값으로, 다르면 둘 다 적는다.
 * 목록 응답으로 만든 [Ride] 에는 반경이 없어 null 이며 그때는 "—" 다(값을 지어내지 않는다).
 */
private fun radiusLabel(ride: Ride): String {
    val departure = ride.departureRadiusMeters
    val destination = ride.destinationRadiusMeters
    return when {
        departure == null && destination == null -> "—"
        departure == destination -> "${departure}m"
        else -> "출발 ${meters(departure)} · 도착 ${meters(destination)}"
    }
}

private fun meters(value: Int?): String = if (value == null) "—" else "${value}m"
