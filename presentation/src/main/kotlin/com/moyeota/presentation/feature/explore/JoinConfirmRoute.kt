package com.moyeota.presentation.feature.explore

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
import com.moyeota.domain.model.User
import com.moyeota.domain.repository.ApiNotAvailableException
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

class JoinConfirmViewModel(
    private val repository: RideRepository,
    private val userSession: UserSession,
    private val partyId: Long,
) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data class Success(val ride: Ride) : UiState
        data class Error(val message: String) : UiState
    }

    // 합류 제출 상태. joined 가 true 가 되면 Route 가 22 탑승 상세로 넘긴다.
    data class JoinState(
        val joining: Boolean = false,
        val errorMessage: String? = null,
        val joined: Boolean = false,
    )

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _joinState = MutableStateFlow(JoinState())
    val joinState: StateFlow<JoinState> = _joinState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            _uiState.value = try {
                UiState.Success(repository.getPartyDetail(partyId))
            } catch (e: Exception) {
                UiState.Error("합승 정보를 불러오지 못했어요")
            }
        }
    }

    fun join() {
        if (_joinState.value.joining) return // 중복 제출 차단 (공통 규칙)
        viewModelScope.launch {
            _joinState.update { it.copy(joining = true, errorMessage = null) }
            try {
                repository.joinParty(partyId, userSession.currentUserId)
                _joinState.update { it.copy(joining = false, joined = true) }
            } catch (e: ApiNotAvailableException) {
                // 서버에 합류 엔드포인트가 아직 없다 — 원인 메시지를 그대로 노출한다.
                _joinState.update { it.copy(joining = false, errorMessage = e.message) }
            } catch (e: Exception) {
                _joinState.update { it.copy(joining = false, errorMessage = "합류하지 못했어요. 잠시 후 다시 시도해 주세요") }
            }
        }
    }

    companion object {
        fun factory(repository: RideRepository, userSession: UserSession, partyId: Long) = viewModelFactory {
            initializer { JoinConfirmViewModel(repository, userSession, partyId) }
        }
    }
}

// 20 합류 확인 — 서버 상세 조회 + 합류 액션 진입점. partyId 없으면 기존 더미 화면 유지.
@Composable
fun JoinConfirmRoute(
    repository: RideRepository,
    userSession: UserSession,
    partyId: Long?,
    onDismiss: () -> Unit = {},
    onJoined: () -> Unit = {},
    onMemberClick: (User) -> Unit = {},
) {
    if (partyId == null) {
        JoinConfirmScreen(
            onDismiss = onDismiss,
            onConfirmJoin = { onJoined() },
            onMemberClick = onMemberClick,
        )
        return
    }

    val viewModel: JoinConfirmViewModel = viewModel(
        key = "join-$partyId",
        factory = JoinConfirmViewModel.factory(repository, userSession, partyId),
    )
    val state by viewModel.uiState.collectAsState()
    val join by viewModel.joinState.collectAsState()

    LaunchedEffect(join.joined) {
        if (join.joined) onJoined()
    }

    // 탭바가 없는 화면이라 뒤로가기가 유일한 이동 수단 — 로딩·에러에서도 남긴다 (QA F-1 동류)
    when (val current = state) {
        JoinConfirmViewModel.UiState.Loading -> BackStateScaffold("합류할까요?", onDismiss) {
            LoadingBox()
        }
        is JoinConfirmViewModel.UiState.Error -> BackStateScaffold("합류할까요?", onDismiss) {
            ErrorBox(message = current.message, onRetry = viewModel::refresh)
        }
        is JoinConfirmViewModel.UiState.Success -> JoinConfirmScreen(
            ride = current.ride,
            joining = join.joining,
            joinErrorMessage = join.errorMessage,
            onDismiss = onDismiss,
            onConfirmJoin = { viewModel.join() },
            onMemberClick = onMemberClick,
        )
    }
}
