package com.moyeota.presentation.feature.explore

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.moyeota.core.designsystem.component.MoyeotaTab
import com.moyeota.domain.model.Ride
import com.moyeota.domain.repository.RideRepository
import com.moyeota.presentation.core.ErrorBox
import com.moyeota.presentation.core.LoadingBox
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ExploreViewModel(private val repository: RideRepository) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data class Success(val parties: List<Ride>) : UiState
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
                UiState.Success(repository.getParties())
            } catch (e: Exception) {
                UiState.Error("합승 목록을 불러오지 못했어요")
            }
        }
    }

    companion object {
        fun factory(repository: RideRepository) = viewModelFactory {
            initializer { ExploreViewModel(repository) }
        }
    }
}

// 17–19 합승 탐색 — 서버 목록 연동 진입점
@Composable
fun ExploreRoute(
    repository: RideRepository,
    onJoinParty: (Ride) -> Unit = {},
    onOngoingRideClick: () -> Unit = {},
    onCreateRoomClick: () -> Unit = {},
    onTabSelect: (MoyeotaTab) -> Unit = {},
) {
    val viewModel: ExploreViewModel = viewModel(factory = ExploreViewModel.factory(repository))
    val state by viewModel.uiState.collectAsState()

    when (val current = state) {
        ExploreViewModel.UiState.Loading -> LoadingBox()
        is ExploreViewModel.UiState.Error -> ErrorBox(message = current.message, onRetry = viewModel::refresh)
        is ExploreViewModel.UiState.Success -> ExploreScreen(
            parties = current.parties,
            waitingCount = current.parties.sumOf { it.members.size },
            onJoinParty = onJoinParty,
            onOngoingRideClick = onOngoingRideClick,
            onCreateRoomClick = onCreateRoomClick,
            onTabSelect = onTabSelect,
        )
    }
}
