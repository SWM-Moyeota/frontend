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

    // 남은 액션은 「그만 찾기」 하나뿐 — 준비/매칭 시작은 백엔드에서 삭제됐다.
    data class ActionState(
        val inProgress: Boolean = false,
        val errorMessage: String? = null,
        val left: Boolean = false,
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

    // 정원이 차면 서버가 스스로 기사 매칭을 시작한다(PartyApplicationService.join 내부).
    // 앱은 트리거하지 않고 상세를 주기적으로 다시 읽어 인원 현황과 status 전이만 관찰한다.
    // 폴링 실패는 일시적 네트워크 문제일 뿐이라 화면을 에러로 덮지 않고 다음 주기를 기다린다.
    private fun observeAutoMatching() {
        viewModelScope.launch {
            while (isActive) {
                delay(PARTY_POLL_INTERVAL_MS)
                val ride = runCatching { repository.getPartyDetail(partyId) }.getOrNull() ?: continue
                _uiState.value = UiState.Success(ride)
                // 배차 이후는 25 배차 현황이 이어받는다 — 대기 화면의 폴링은 여기서 끝낸다
                if (ride.status !in WAITING_STATUSES) return@launch
            }
        }
    }

    fun leaveParty() = runAction(failureMessage = "방에서 나가지 못했어요") {
        // 나가는 주체는 Bearer 토큰이 정한다 — memberId 를 넘기지 않는다 (22 보고서 §2)
        repository.leaveParty(partyId)
        _actionState.update { it.copy(left = true) }
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
        // 아직 사람을 모으는 중인 상태. 이 밖으로 나가면 배차가 시작된 것이다.
        private val WAITING_STATUSES = setOf(RideStatus.RECRUITING, RideStatus.MATCHED)

        fun factory(repository: RideRepository, partyId: Long) = viewModelFactory {
            initializer { MatchWaitingViewModel(repository, partyId) }
        }
    }
}

// 21 매칭 대기 — 파티 상세 폴링 + 나가기 진입점. partyId 없으면 기존 더미 화면 유지.
@Composable
fun MatchWaitingRoute(
    repository: RideRepository,
    partyId: Long?,
    onCancelSearch: () -> Unit = {},
    /** 방을 유지한 채 화면만 벗어난다(나가기가 막힌 단계의 뒤로가기) */
    onExitKeepingParty: () -> Unit = {},
    onCardClick: () -> Unit = {},
    onMatchingStarted: () -> Unit = {},
) {
    if (partyId == null) {
        MatchWaitingScreen(
            onCancelSearch = onCancelSearch,
            onExitKeepingParty = onExitKeepingParty,
            onCardClick = onCardClick,
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

    // 매칭 시작 버튼이 없어졌으므로, 25 배차 현황으로의 이동은 서버 status 전이가 유일한 신호다.
    val status = (state as? MatchWaitingViewModel.UiState.Success)?.ride?.status
    LaunchedEffect(status) {
        if (status == RideStatus.DISPATCHING || status == RideStatus.ONGOING) onMatchingStarted()
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
                actionInProgress = action.inProgress,
                actionErrorMessage = action.errorMessage,
                onCancelSearch = viewModel::leaveParty, // 나가기 성공 시 14 홈
                onExitKeepingParty = onExitKeepingParty,
                onCardClick = onCardClick,
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
