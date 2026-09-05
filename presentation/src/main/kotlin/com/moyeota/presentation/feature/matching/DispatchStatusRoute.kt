package com.moyeota.presentation.feature.matching

import android.util.Log
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

/**
 * 배차 폴링 간격. 지도 마커뿐 아니라 **탑승 시작(IN_RIDE) 감지 지연**도 이 값이 정한다 —
 * 기사가 board 를 누른 뒤 최악의 경우 이만큼 기다렸다 26 으로 넘어간다.
 * 26 운행 중의 완료 폴링(4초)과 같은 자릿수로 둔다.
 */
private const val DRIVER_POLL_INTERVAL_MS = 5_000L

private const val TAG = "DispatchStatus"

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

    // 기사측 탑승 처리(IN_RIDE) 감지. 26 운행 중의 finished 와 같은 모양의 1회성 신호다 —
    // UiState 에 섞으면 화면 렌더와 네비게이션이 한 값에 묶여, 폴링 실패가 전이까지 막는다.
    private val _rideStarted = MutableStateFlow(false)
    val rideStarted: StateFlow<Boolean> = _rideStarted.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            _uiState.value = try {
                val ride = rideRepository.getPartyDetail(partyId)
                loadDriverIfAssigned(ride)
                markStartedIfInRide(ride)
                UiState.Success(ride)
            } catch (e: Exception) {
                UiState.Error("배차 정보를 불러오지 못했어요")
            }
        }
    }

    /**
     * 방 상세·기사 정보·기사 위치 폴링. **호출자(Route)의 코루틴 스코프에서 돌린다** —
     * LaunchedEffect 가 화면 이탈 시 이 함수를 취소해 폴링이 함께 멈춘다.
     *
     * 방 상세는 **매 주기 무조건** 다시 읽는다. 기사 배정 여부로 건너뛰면 안 된다 —
     * 이 화면이 기다리는 마지막 신호(기사측 board → IN_RIDE)는 **배정된 뒤에** 오기 때문에,
     * 배정을 조건으로 상세 조회를 멈추면 탑승 시작을 영영 못 본다 (결함 D-9 의 원인).
     * 기사 **정보** 조회만 이미 받아온 뒤 건너뛴다 — 그 값은 배차 동안 바뀌지 않는다.
     *
     * 기사 미배정·위치 미보고는 서버가 거절하는 정상 상황이라 실패를 삼키고 다음 주기를 기다린다.
     */
    suspend fun pollDriver() {
        Log.d(TAG, "배차 폴링 시작 partyId=$partyId")
        try {
            while (currentCoroutineContext().isActive) {
                val previous = (_uiState.value as? UiState.Success)?.ride
                // 방 상세를 다시 읽어 배차 전이(MATCHING → DRIVER_ASSIGNED → IN_RIDE)를 따라잡는다
                val latest = runCatching { rideRepository.getPartyDetail(partyId) }.getOrNull()
                if (latest != null) _uiState.value = UiState.Success(latest)
                loadDriverIfAssigned(latest ?: previous)
                if (markStartedIfInRide(latest)) return
                // 조회 주체는 Bearer 토큰이 정한다 — memberId 를 넘기지 않는다 (22 보고서 §2)
                runCatching { dispatchRepository.getDriverLocation(partyId) }
                    .onSuccess { _driverLocation.value = it }
                delay(DRIVER_POLL_INTERVAL_MS)
            }
        } finally {
            Log.d(TAG, "배차 폴링 중단 partyId=$partyId")
        }
    }

    // 기사 미배정(taxiDriverId == null)이면 서버가 거절하므로, 배차된 상태에서만 호출한다.
    // 이미 받아온 뒤에는 다시 부르지 않는다 — 차량 정보는 배차 동안 바뀌지 않는다.
    private suspend fun loadDriverIfAssigned(ride: Ride?) {
        if (_driver.value != null) return
        if (ride == null || ride.status !in DRIVER_ASSIGNED_STATUSES) return
        runCatching { rideRepository.getAssignedDriver(partyId) }
            .onSuccess { _driver.value = it }
    }

    /**
     * 서버 status IN_RIDE([RideStatus.ONGOING]) = 기사가 탑승 처리를 마쳤다는 뜻.
     * 25 가 26 으로 넘어가는 유일한 서버 신호다. 감지하면 true 를 돌려줘 폴링을 끝낸다.
     */
    private fun markStartedIfInRide(ride: Ride?): Boolean {
        if (ride?.status != RideStatus.ONGOING) return false
        if (!_rideStarted.value) Log.d(TAG, "탑승 시작 감지 partyId=$partyId → 26 운행 중")
        _rideStarted.value = true
        return true
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

/**
 * 25 배차 현황 — 방 상세 + 배정 기사 + 기사 위치 폴링 진입점. partyId 없으면 기존 더미 화면 유지.
 *
 * 26 운행 중으로의 전이 신호는 **서버 status IN_RIDE**(기사측 board) **하나뿐**이다.
 * [DispatchStatusScreen] 에는 [onStartRide] 를 부르는 수동 트리거가 없다 — 이 Route 의 폴링이 유일한 발화점이다.
 * partyId 가 없으면 폴링도 없어 전이도 없다(채팅·34 내 기록에서 직행한 경우의 폴백 화면).
 */
@Composable
fun DispatchStatusRoute(
    rideRepository: RideRepository,
    dispatchRepository: DispatchRepository,
    partyId: Long?,
    onStartRide: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    if (partyId == null) {
        DispatchStatusScreen(onBack = onBack)
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
    val rideStarted by viewModel.rideStarted.collectAsState()

    // 화면이 살아있는 동안만 폴링한다 — 이탈하면 LaunchedEffect 가 취소돼 함께 멈춘다
    LaunchedEffect(viewModel) {
        viewModel.pollDriver()
    }
    // 기사측 탑승 처리를 잡으면 26 운행 중으로 자동 전이한다 (26→28 의 onRideFinished 와 같은 모양)
    LaunchedEffect(rideStarted) {
        if (rideStarted) onStartRide()
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
            onBack = onBack,
        )
    }
}
