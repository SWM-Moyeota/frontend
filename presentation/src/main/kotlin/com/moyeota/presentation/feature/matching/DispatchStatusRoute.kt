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
import com.moyeota.domain.model.AssignedDriver
import com.moyeota.domain.model.DriverLocation
import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RideStatus
import com.moyeota.domain.repository.DispatchRepository
import com.moyeota.domain.repository.RideRepository
import com.moyeota.presentation.core.BackStateScaffold
import com.moyeota.presentation.core.ErrorBox
import com.moyeota.presentation.core.LoadingBox
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 기사 위치 폴링 간격. 지도 마커만 움직이므로 너무 촘촘할 필요가 없다. */
private const val DRIVER_POLL_INTERVAL_MS = 5_000L

class DispatchStatusViewModel(
    private val rideRepository: RideRepository,
    private val dispatchRepository: DispatchRepository,
    private val partyId: Long,
) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data class Success(val ride: Ride) : UiState
        data class Error(val message: String) : UiState
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // 기사 정보·위치는 UiState 와 분리한다. 둘 다 "아직 없음"이 정상 상황이라
    // 실패가 화면 전체를 에러로 덮으면 안 된다 (01 보고서 7절).
    private val _driver = MutableStateFlow<AssignedDriver?>(null)
    val driver: StateFlow<AssignedDriver?> = _driver.asStateFlow()

    private val _driverLocation = MutableStateFlow<DriverLocation?>(null)
    val driverLocation: StateFlow<DriverLocation?> = _driverLocation.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            _uiState.value = try {
                val ride = rideRepository.getPartyDetail(partyId)
                loadDriverIfAssigned(ride)
                UiState.Success(ride)
            } catch (e: Exception) {
                UiState.Error("배차 정보를 불러오지 못했어요")
            }
        }
    }

    /**
     * 기사 정보·위치 폴링. **호출자(Route)의 코루틴 스코프에서 돌린다** —
     * LaunchedEffect 가 화면 이탈 시 이 함수를 취소해 폴링이 함께 멈춘다.
     *
     * 기사 미배정·위치 미보고는 서버가 거절하는 정상 상황이라 실패를 삼키고 다음 주기를 기다린다.
     */
    suspend fun pollDriver() {
        while (currentCoroutineContext().isActive) {
            if (_driver.value == null) {
                val ride = (_uiState.value as? UiState.Success)?.ride
                // 방 상세를 다시 읽어 배차 전이(MATCHING → DRIVER_ASSIGNED)를 따라잡는다
                val latest = runCatching { rideRepository.getPartyDetail(partyId) }.getOrNull()
                if (latest != null) _uiState.value = UiState.Success(latest)
                loadDriverIfAssigned(latest ?: ride)
            }
            // 조회 주체는 Bearer 토큰이 정한다 — memberId 를 넘기지 않는다 (22 보고서 §2)
            runCatching { dispatchRepository.getDriverLocation(partyId) }
                .onSuccess { _driverLocation.value = it }
            delay(DRIVER_POLL_INTERVAL_MS)
        }
    }

    // 기사 미배정(taxiDriverId == null)이면 서버가 거절하므로, 배차된 상태에서만 호출한다.
    private suspend fun loadDriverIfAssigned(ride: Ride?) {
        if (ride == null || ride.status !in DRIVER_ASSIGNED_STATUSES) return
        runCatching { rideRepository.getAssignedDriver(partyId) }
            .onSuccess { _driver.value = it }
    }

    companion object {
        // 서버 DRIVER_ASSIGNED → DISPATCHING, IN_RIDE → ONGOING 으로 매핑된다
        private val DRIVER_ASSIGNED_STATUSES = setOf(RideStatus.DISPATCHING, RideStatus.ONGOING)

        fun factory(
            rideRepository: RideRepository,
            dispatchRepository: DispatchRepository,
            partyId: Long,
        ) = viewModelFactory {
            initializer {
                DispatchStatusViewModel(rideRepository, dispatchRepository, partyId)
            }
        }
    }
}

// 25 배차 현황 — 방 상세 + 배정 기사 + 기사 위치 폴링 진입점. partyId 없으면 기존 더미 화면 유지.
@Composable
fun DispatchStatusRoute(
    rideRepository: RideRepository,
    dispatchRepository: DispatchRepository,
    partyId: Long?,
    onStartRide: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    if (partyId == null) {
        DispatchStatusScreen(onStartRide = onStartRide, onBack = onBack)
        return
    }

    val viewModel: DispatchStatusViewModel = viewModel(
        key = "dispatch-$partyId",
        factory = DispatchStatusViewModel.factory(
            rideRepository,
            dispatchRepository,
            partyId,
        ),
    )
    val state by viewModel.uiState.collectAsState()
    val driver by viewModel.driver.collectAsState()
    val driverLocation by viewModel.driverLocation.collectAsState()

    // 화면이 살아있는 동안만 폴링한다 — 이탈하면 LaunchedEffect 가 취소돼 함께 멈춘다
    LaunchedEffect(viewModel) {
        viewModel.pollDriver()
    }

    when (val current = state) {
        DispatchStatusViewModel.UiState.Loading -> BackStateScaffold("택시 오는 중", onBack) {
            LoadingBox()
        }
        is DispatchStatusViewModel.UiState.Error -> BackStateScaffold("택시 오는 중", onBack) {
            ErrorBox(message = current.message, onRetry = viewModel::refresh)
        }
        is DispatchStatusViewModel.UiState.Success -> DispatchStatusScreen(
            ride = current.ride,
            driver = driver,
            driverLocation = driverLocation,
            onStartRide = onStartRide,
            onBack = onBack,
        )
    }
}
