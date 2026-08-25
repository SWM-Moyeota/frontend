package com.moyeota.presentation.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.moyeota.domain.model.NewParty
import com.moyeota.domain.model.Place
import com.moyeota.domain.model.Ride
import com.moyeota.domain.repository.RideRepository
import com.moyeota.domain.session.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CreatePartyViewModel(
    private val repository: RideRepository,
    private val userSession: UserSession,
) : ViewModel() {

    data class UiState(
        val creating: Boolean = false,
        val errorMessage: String? = null,
        val createdParty: Ride? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun createParty(origin: Place, destination: Place, conditions: MatchConditions) {
        if (_uiState.value.creating) return // 중복 제출 차단
        viewModelScope.launch {
            _uiState.update { it.copy(creating = true, errorMessage = null) }
            try {
                val party = repository.createParty(
                    NewParty(
                        hostId = userSession.currentUserId,
                        departureLat = origin.latitude,
                        departureLng = origin.longitude,
                        destinationLat = destination.latitude,
                        destinationLng = destination.longitude,
                        departure = origin.name,
                        destination = destination.name,
                        // 서버 상한 3 — 초과하면 메시지 없는 500 이 떨어져 원인 파악이 어렵다.
                        // 16 모달 칩이 1~3 뿐이지만 계약을 여기서 한 번 더 못박는다.
                        capacity = conditions.capacity.coerceIn(1, MAX_PARTY_CAPACITY),
                        // 서버 검증이 100~500m 라 UI 의 1km·2km 선택은 500m 로 clamp 한다.
                        departureRadius = conditions.departureRadiusMeters.coerceIn(100, 500),
                        destinationRadius = conditions.destinationRadiusMeters.coerceIn(100, 500),
                    ),
                )
                _uiState.update { it.copy(creating = false, createdParty = party) }
            } catch (e: Exception) {
                _uiState.update { it.copy(creating = false, errorMessage = "합승 방을 만들지 못했어요. 잠시 후 다시 시도해 주세요") }
            }
        }
    }

    companion object {
        // 백엔드 OpenPartyRequest 검증 상한. 초과 시 400 이 아니라 500 이 온다(에러 바디 없음).
        const val MAX_PARTY_CAPACITY = 3

        fun factory(repository: RideRepository, userSession: UserSession) = viewModelFactory {
            initializer { CreatePartyViewModel(repository, userSession) }
        }
    }
}

// 16 도착지 확인 모달 — 방 생성 진입점. 성공하면 생성된 방을 그대로 21 매칭 대기로 넘긴다.
@Composable
fun DestinationConfirmRoute(
    repository: RideRepository,
    userSession: UserSession,
    destination: Place?,
    onDismiss: () -> Unit = {},
    onPartyCreated: (Ride) -> Unit = {},
) {
    val viewModel: CreatePartyViewModel = viewModel(factory = CreatePartyViewModel.factory(repository, userSession))
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.createdParty) {
        state.createdParty?.let(onPartyCreated)
    }

    // 15 를 거치지 않고 들어오면 좌표가 없어 방을 만들 수 없다 — 안내만 하고 닫기 유도.
    // 폴백으로 출발지(DemoOrigin)를 쓰면 도착지 칸에 출발지 이름이 떠서 더 헷갈린다 (QA F-2).
    DestinationConfirmModal(
        destinationName = destination?.name ?: "—",
        destinationAddress = destination?.roadName ?: "",
        originStopName = DemoOrigin.name,
        creating = state.creating,
        errorMessage = state.errorMessage
            ?: "도착지 좌표가 없어요. 15 목적지 화면에서 장소를 다시 선택해 주세요".takeIf { destination == null },
        onDismiss = onDismiss,
        onFindCompanions = { conditions ->
            if (destination != null) viewModel.createParty(DemoOrigin, destination, conditions)
        },
    )
}
