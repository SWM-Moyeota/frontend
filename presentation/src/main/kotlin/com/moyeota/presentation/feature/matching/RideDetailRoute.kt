package com.moyeota.presentation.feature.matching

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.User
import com.moyeota.domain.repository.RideRepository
import com.moyeota.presentation.core.BackStateScaffold
import com.moyeota.presentation.core.ErrorBox
import com.moyeota.presentation.core.LoadingBox
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RideDetailViewModel(
    private val repository: RideRepository,
    private val partyId: Long,
) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data class Success(val ride: Ride) : UiState
        data class Error(val message: String) : UiState
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _left = MutableStateFlow(false)
    val left: StateFlow<Boolean> = _left.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            _uiState.value = try {
                UiState.Success(repository.getPartyDetail(partyId))
            } catch (e: Exception) {
                UiState.Error("탑승 정보를 불러오지 못했어요")
            }
        }
    }

    // 나가기 실패 시 화면을 넘기지 않고 재시도 가능한 에러 상태로 떨어뜨린다.
    fun leave() {
        viewModelScope.launch {
            try {
                // 나가는 주체는 Bearer 토큰이 정한다 — memberId 를 넘기지 않는다 (22 보고서 §2)
                repository.leaveParty(partyId)
                _left.value = true
            } catch (e: Exception) {
                _uiState.value = UiState.Error("탑승에서 나가지 못했어요")
            }
        }
    }

    companion object {
        fun factory(repository: RideRepository, partyId: Long) = viewModelFactory {
            initializer { RideDetailViewModel(repository, partyId) }
        }
    }
}

// 22 탑승 상세 — 서버 상세 연동 진입점. partyId 없으면 기존 더미 화면 유지.
//
// UserSession 을 더 이상 받지 않는다: 「나」 판정이 매퍼(publicId == 세션 uuid)로 내려가
// 화면은 User.isMe 만 보면 된다. 예전에는 여기서 고정 memberId 1 을 내려보내야 했다.
@Composable
fun RideDetailRoute(
    repository: RideRepository,
    partyId: Long?,
    onBack: () -> Unit = {},
    onPartnerClick: (User) -> Unit = {},
    onLeave: () -> Unit = {},
) {
    if (partyId == null) {
        RideDetailScreen(
            onBack = onBack,
            onPartnerClick = onPartnerClick,
            onLeave = onLeave,
        )
        return
    }

    val viewModel: RideDetailViewModel = viewModel(
        key = "party-$partyId",
        factory = RideDetailViewModel.factory(repository, partyId),
    )
    val state by viewModel.uiState.collectAsState()
    val left by viewModel.left.collectAsState()

    LaunchedEffect(left) {
        if (left) onLeave()
    }

    // 탭바가 없는 화면 — 로딩·에러에서도 뒤로가기를 남긴다 (QA F-1 동류)
    when (val current = state) {
        RideDetailViewModel.UiState.Loading -> BackStateScaffold("탑승 상세", onBack) {
            LoadingBox()
        }
        is RideDetailViewModel.UiState.Error -> BackStateScaffold("탑승 상세", onBack) {
            ErrorBox(message = current.message, onRetry = viewModel::refresh)
        }
        is RideDetailViewModel.UiState.Success -> RideDetailScreen(
            ride = current.ride,
            onBack = onBack,
            onPartnerClick = onPartnerClick,
            onLeave = viewModel::leave, // 나가기 성공 후 onLeave 로 화면 전환
        )
    }
}
