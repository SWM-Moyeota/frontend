package com.moyeota.presentation.feature.matching

import androidx.compose.runtime.Composable
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

    companion object {
        fun factory(repository: RideRepository, partyId: Long) = viewModelFactory {
            initializer { RideDetailViewModel(repository, partyId) }
        }
    }
}

// 22 탑승 상세 — 서버 상세 연동 진입점. partyId 없으면 기존 더미 화면 유지.
@Composable
fun RideDetailRoute(
    repository: RideRepository,
    partyId: Long?,
    onBack: () -> Unit = {},
    onPartnerClick: (User) -> Unit = {},
    onLeave: () -> Unit = {},
    onDepart: () -> Unit = {},
) {
    if (partyId == null) {
        RideDetailScreen(
            onBack = onBack,
            onPartnerClick = onPartnerClick,
            onLeave = onLeave,
            onDepart = onDepart,
        )
        return
    }

    val viewModel: RideDetailViewModel = viewModel(
        key = "party-$partyId",
        factory = RideDetailViewModel.factory(repository, partyId),
    )
    val state by viewModel.uiState.collectAsState()

    when (val current = state) {
        RideDetailViewModel.UiState.Loading -> LoadingBox()
        is RideDetailViewModel.UiState.Error -> ErrorBox(message = current.message, onRetry = viewModel::refresh)
        is RideDetailViewModel.UiState.Success -> RideDetailScreen(
            ride = current.ride,
            // 인증 연동 전 임시 사용자 (backend TODO: JWT 에서 memberId 추출)
            currentUserId = "1",
            onBack = onBack,
            onPartnerClick = onPartnerClick,
            onLeave = onLeave,
            onDepart = onDepart,
        )
    }
}
