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
import com.moyeota.domain.repository.RideRepository
import com.moyeota.domain.session.UserSession
import com.moyeota.presentation.core.BackStateScaffold
import com.moyeota.presentation.core.ErrorBox
import com.moyeota.presentation.core.LoadingBox
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MatchWaitingViewModel(
    private val repository: RideRepository,
    private val userSession: UserSession,
    private val partyId: Long,
) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data class Success(val ride: Ride) : UiState
        data class Error(val message: String) : UiState
    }

    // ready 는 서버 응답(PartyDetailResult)에 없어 로컬로만 추적한다 (01 보고서 플래그 6).
    data class ActionState(
        val inProgress: Boolean = false,
        val errorMessage: String? = null,
        val ready: Boolean = false,
        val left: Boolean = false,
        val matchingStarted: Boolean = false,
    )

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _actionState = MutableStateFlow(ActionState())
    val actionState: StateFlow<ActionState> = _actionState.asStateFlow()

    init {
        refresh()
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

    fun toggleReady() = runAction(failureMessage = "준비 상태를 바꾸지 못했어요") {
        val nowReady = !_actionState.value.ready
        if (nowReady) {
            repository.setReady(partyId, userSession.currentUserId)
        } else {
            repository.cancelReady(partyId, userSession.currentUserId)
        }
        _actionState.update { it.copy(ready = nowReady) }
        reload() // 준비 반영 후 상세 재조회
    }

    fun startMatching() = runAction(failureMessage = "매칭을 시작하지 못했어요") {
        repository.startMatching(partyId, userSession.currentUserId)
        reload() // status → MATCHING
        _actionState.update { it.copy(matchingStarted = true) }
    }

    fun leaveParty() = runAction(failureMessage = "방에서 나가지 못했어요") {
        repository.leaveParty(partyId, userSession.currentUserId)
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

    // 액션 성공 후 갱신 — 실패해도 직전 화면 상태를 유지한다
    private suspend fun reload() {
        runCatching { repository.getPartyDetail(partyId) }
            .onSuccess { _uiState.value = UiState.Success(it) }
    }

    companion object {
        fun factory(repository: RideRepository, userSession: UserSession, partyId: Long) = viewModelFactory {
            initializer { MatchWaitingViewModel(repository, userSession, partyId) }
        }
    }
}

// 21 매칭 대기 — 파티 상세 + 준비/나가기/매칭 시작 진입점. partyId 없으면 기존 더미 화면 유지.
@Composable
fun MatchWaitingRoute(
    repository: RideRepository,
    userSession: UserSession,
    partyId: Long?,
    onCancelSearch: () -> Unit = {},
    onCardClick: () -> Unit = {},
    onMatchingStarted: () -> Unit = {},
) {
    if (partyId == null) {
        MatchWaitingScreen(onCancelSearch = onCancelSearch, onCardClick = onCardClick)
        return
    }

    val viewModel: MatchWaitingViewModel = viewModel(
        key = "waiting-$partyId",
        factory = MatchWaitingViewModel.factory(repository, userSession, partyId),
    )
    val state by viewModel.uiState.collectAsState()
    val action by viewModel.actionState.collectAsState()

    LaunchedEffect(action.left) {
        if (action.left) onCancelSearch()
    }
    LaunchedEffect(action.matchingStarted) {
        if (action.matchingStarted) onMatchingStarted()
    }

    // 탭바가 없는 화면 — 로딩·에러에서도 뒤로가기(=탐색 취소)를 남긴다 (QA F-1 동류)
    when (val current = state) {
        MatchWaitingViewModel.UiState.Loading -> BackStateScaffold("같이 탈 사람 찾는 중", onCancelSearch) {
            LoadingBox()
        }
        is MatchWaitingViewModel.UiState.Error -> BackStateScaffold("같이 탈 사람 찾는 중", onCancelSearch) {
            ErrorBox(message = current.message, onRetry = viewModel::refresh)
        }
        is MatchWaitingViewModel.UiState.Success -> {
            val ride = current.ride
            MatchWaitingScreen(
                ride = ride,
                foundCount = ride.members.size,
                conditionLabel = "${ride.capacity}인",
                isHost = ride.hostId != null && ride.hostId == userSession.currentUserId.toString(),
                isReady = action.ready,
                actionInProgress = action.inProgress,
                actionErrorMessage = action.errorMessage,
                onCancelSearch = viewModel::leaveParty, // 나가기 성공 시 14 홈
                onCardClick = onCardClick,
                onToggleReady = viewModel::toggleReady,
                onStartMatching = viewModel::startMatching,
            )
        }
    }
}
