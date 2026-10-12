package com.moyeota.presentation.feature.matching

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.moyeota.presentation.core.shouldPublishDetail
import com.moyeota.presentation.core.location.rememberMyLocationState
import com.moyeota.core.designsystem.component.latLngOrNull
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 배차 폴링 간격 — **7초**. 지도 마커 갱신 주기이자 **탑승 시작(IN_RIDE) 감지 지연**이다
 * (기사가 board 를 누른 뒤 최악의 경우 이만큼 기다렸다 26 으로 넘어간다).
 *
 * 옛 5초에서 늘렸다. 매 주기 읽는 대상이 쿼리 1개짜리 상태 조회에서 **쿼리 3개짜리 방 상세**로
 * 바뀌었기 때문이다 — 서버가 `/status` 를 삭제했다(`04f6be7`). 5초를 그대로 두면 쿼리 부하가
 * 0.20 → 0.60 q/s 로 3배가 된다. 7초면 0.43 q/s 다.
 *
 * 26 운행 중(8초)보다 **짧게** 두는 이유: 기사 위치 조회가 같은 루프에 얹혀 있어 이 값이 곧
 * 마커 갱신 주기다. 40km/h 로 오는 택시가 한 걸음에 약 78m 를 뛴다 — 더 벌리면 마커가 튄다.
 * 근거와 부하 표는 [com.moyeota.presentation.core.PARTY_DETAIL_POLL_FLOOR_MS] KDoc 에 있다.
 */
internal const val DRIVER_POLL_INTERVAL_MS = 7_000L

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
     * **동승 모드에서는 이 루프가 한 번도 돌지 않는다** — 서버가 `DISPATCHING` 을 내지 않아 25 에
     * 닿을 길이 없다(`docs/MODE-ROUTES.md`). 택시 모드 전용이다.
     *
     * 매 주기 읽는 건 **방 상세**([RideRepository.getPartyDetail], 서버 쿼리 3개)다. 예전에는 가벼운
     * 상태 조회(`/status`, 쿼리 1개)를 읽고 지문이 달라진 주기에만 상세를 읽었지만, 그 엔드포인트와
     * 지문이 백엔드에서 사라졌다(`04f6be7`). 그래서 **「안 바뀌었으면 상세 재조회 생략」은 원리적으로
     * 불가능하다** — 바뀌었는지 알려면 상세를 받아야 한다. 대신 두 가지로 비용을 눌렀다:
     * 1. 간격을 5초 → [DRIVER_POLL_INTERVAL_MS](7초)로 늘렸다.
     * 2. 받은 상세가 들고 있던 것과 같으면 **화면 상태를 갱신하지 않는다**([shouldPublishDetail]) —
     *    7초마다 지도·기사 카드가 다시 그려지지 않게.
     *
     * 수단을 바꾸면서 **옛 예외 조건 하나가 통째로 없어졌다**: 지문이 없는 서버에서는 요약만으로
     * `MATCHING`(기사 찾는 중)과 `DRIVER_ASSIGNED`(배정 완료)를 가를 수 없어(앱에서는 둘 다
     * [RideStatus.DISPATCHING], 인원도 같다) 「미배정인 동안에는 매 주기 상세를 읽는다」는 단서가
     * 붙어 있었다. 이제는 매 주기가 상세라 [Ride.driverId] 가 그 자리에서 들어온다.
     *
     * 기사 **정보** 조회는 이미 받아온 뒤 건너뛴다 — 그 값은 배차 동안 바뀌지 않는다.
     * 기사 **위치**(`GET /dispatch/rides/{id}`)는 방 상세가 아닌 별도 API 라 매 주기 묻는다
     * — 택시가 오는 동안 마커가 움직여야 한다. 그래서 이 간격이 마커 갱신 주기도 된다.
     *
     * 조회 실패(네트워크·기사 미배정·위치 미보고)는 삼키고 **들고 있던 값을 유지한 채** 다음 주기를
     * 기다린다 — 한 번의 실패로 화면이 깜빡이거나 에러로 덮이면 안 된다.
     */
    suspend fun pollDriver() {
        Log.d(TAG, "배차 폴링 시작 partyId=$partyId")
        try {
            while (currentCoroutineContext().isActive) {
                val held = (_uiState.value as? UiState.Success)?.ride
                // 실패하면(null) 들고 있던 값으로 이 주기를 넘긴다 — 깜빡임도 에러 화면도 없다.
                val fetched = runCatching { rideRepository.getPartyDetail(partyId) }.getOrNull()
                if (fetched != null && shouldPublishDetail(held, fetched)) {
                    _uiState.value = UiState.Success(fetched)
                }
                // 전이 판정은 **이번 주기에 받은 값**으로만 한다 — 실패한 주기(null)는 "아직 아니다"다.
                if (markStartedIfInRide(fetched)) return
                // 기사 조회 게이트는 반대로 **마지막으로 아는 값**을 쓴다 — 한 주기 실패했다고 마커
                // 갱신을 멈추면 택시가 오는 동안 지도가 굳는다.
                val ride = fetched ?: held
                loadDriverIfAssigned(ride)
                // 기사 위치는 배정된 뒤에만 묻는다 — 배정 전 호출은 서버가 DRIVER_NOT_ASSIGNED 로 거절하며
                // 폴링 주기마다 서버 WARN 로그만 쌓는다. 조회 주체는 Bearer 토큰이 정한다(22 보고서 §2).
                if (shouldFetchDriverLocation(ride)) {
                    runCatching { dispatchRepository.getDriverLocation(partyId) }
                        .onSuccess { _driverLocation.value = it }
                }
                delay(DRIVER_POLL_INTERVAL_MS)
            }
        } finally {
            Log.d(TAG, "배차 폴링 중단 partyId=$partyId")
        }
    }

    // 기사 미배정(taxiDriverId == null)이면 서버가 거절하므로, 배정된 뒤에만 호출한다.
    // 이미 받아온 뒤에는 다시 부르지 않는다 — 차량 정보는 배차 동안 바뀌지 않는다.
    private suspend fun loadDriverIfAssigned(ride: Ride?) {
        if (!shouldFetchDriverInfo(ride, alreadyLoaded = _driver.value != null)) return
        runCatching { rideRepository.getAssignedDriver(partyId) }
            .onSuccess { _driver.value = it }
    }

    /**
     * 탑승 시작([isRideStarted])을 감지하면 1회성 신호를 올리고 true 를 돌려줘 폴링을 끝낸다.
     * 판정 자체는 순수 함수로 빼 두었다 — 폴링 루프와 테스트가 같은 규칙을 본다.
     */
    private fun markStartedIfInRide(ride: Ride?): Boolean {
        if (!isRideStarted(ride)) return false
        if (!_rideStarted.value) Log.d(TAG, "탑승 시작 감지 partyId=$partyId → 26 운행 중")
        _rideStarted.value = true
        return true
    }

    companion object {
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
 * 배차 단계 — 하나의 라우트가 서버 status 에 따라 세 화면을 갈아 끼운다.
 *
 * | 서버 status | 화면 |
 * |---|---|
 * | `MATCHING` (기사 미배정) | 25b [DriverSearchScreen] — 레이더 |
 * | `CANCELED` (3분 타임아웃) | 25b-실패 [DriverSearchFailedScreen] |
 * | `DRIVER_ASSIGNED` 첫 감지 | 25c [DriverAssignedScreen] — 1회성 확인 |
 * | 그 이후 | 25 [DispatchStatusScreen] — 지도·기사 마커 |
 *
 * 별도 네비게이션 목적지를 만들지 않는다. 단계는 **서버 상태의 함수**라, 뒤로가기로 되돌릴 수 있는
 * 스택으로 쌓으면 상태와 화면이 어긋난다(이미 배정됐는데 「찾는 중」으로 돌아가는 식).
 *
 * MATCHING 과 DRIVER_ASSIGNED 는 도메인에서 둘 다 [RideStatus.DISPATCHING] 이라
 * **[Ride.driverId] 유무로 가른다** — 배정되면 서버가 taxiDriverId 를 채운다.
 *
 * 26 운행 중으로의 전이 신호는 **서버 status IN_RIDE**(기사측 board) **하나뿐**이다([isRideStarted]).
 * [DispatchStatusScreen] 에는 [onStartRide] 를 부르는 수동 트리거가 없다 — 이 Route 의 폴링이 유일한 발화점이다.
 * partyId 가 없으면 폴링도 없어 전이도 없다(채팅·34 내 기록에서 직행한 경우의 폴백 화면).
 *
 * **이 화면은 택시 모드에서만 보인다** — 동승 모드 서버는 `DISPATCHING` 을 내지 않는다
 * (`docs/MODE-ROUTES.md`). 그래서 [DispatchStatusViewModel.pollDriver] 의 상세 폴링도 택시 모드에서만 돈다.
 *
 * @param onRetryMatching 매칭 실패 후 「다시 찾기」. 취소된 방은 되살릴 수 없어 홈으로 보낸다
 */
@Composable
fun DispatchStatusRoute(
    rideRepository: RideRepository,
    dispatchRepository: DispatchRepository,
    partyId: Long?,
    onStartRide: () -> Unit = {},
    onRetryMatching: () -> Unit = {},
    onBack: () -> Unit = {},
    /** 이 방의 채팅방을 여는 길. 채팅방이 아직 없으면 null — 화면이 버튼을 그리지 않는다 */
    onOpenChat: (() -> Unit)? = null,
) {
    // 내 현재 위치 — 25c·25 지도의 파란 점. 권한 다이얼로그는 새로 띄우지 않는다(배차 흐름을 끊지 않게,
    // 26 RideOngoingRoute 와 같은 이유). 15~19 에서 이미 허용한 사용자라면 점이 뜨고, 아니면 마커만 그린다.
    val myLocation = rememberMyLocationState(autoRequestPermission = false)

    if (partyId == null) {
        DispatchStatusScreen(myLocation = myLocation.coordinates, onOpenChat = onOpenChat, onBack = onBack)
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
    // 기사측 탑승 처리를 잡으면 26 운행 중으로 자동 전이한다 (26→33 의 onRideFinished 와 같은 모양)
    LaunchedEffect(rideStarted) {
        if (rideStarted) onStartRide()
    }

    // 25c 를 이미 봤는가. 배정 확인은 **한 번이면 충분**하고, 매 재진입마다 다시 띄우면
    // 지도(25)로 가려는 사용자를 계속 가로막는다. rememberSaveable 이라 구성 변경에는 살아남고
    // 프로세스 재시작에는 사라진다 — 그때는 25 직행이 자연스럽다(이미 배정을 알고 있는 사용자다).
    var assignedSeen by rememberSaveable { mutableStateOf(false) }

    when (val current = state) {
        DispatchStatusViewModel.UiState.Loading -> BackStateScaffold("배차 준비 중", onBack) {
            LoadingBox()
        }
        is DispatchStatusViewModel.UiState.Error -> BackStateScaffold("배차 준비 중", onBack) {
            ErrorBox(message = current.message, onRetry = viewModel::refresh)
        }
        is DispatchStatusViewModel.UiState.Success -> {
            val ride = current.ride
            val assigned = ride.driverId != null
            when {
                // 3분 타임아웃 — 서버가 방을 스스로 취소했다. 되살릴 방이 없어 재시도 = 새 방 만들기
                ride.status == RideStatus.CANCELED -> DriverSearchFailedScreen(
                    onRetry = onRetryMatching,
                    onBack = onBack,
                )
                !assigned -> DriverSearchScreen(ride = ride, onOpenChat = onOpenChat, onBack = onBack)
                !assignedSeen -> DriverAssignedScreen(
                    ride = ride,
                    driver = driver,
                    driverLocation = driverLocation,
                    myLocation = myLocation.coordinates,
                    pickupEtaMinutes = pickupEtaMinutes(
                        pickupDistanceMeters(ride, driverLocation),
                    ),
                    onSeeDispatch = { assignedSeen = true },
                    onOpenChat = onOpenChat,
                    onBack = onBack,
                )
                else -> DispatchStatusScreen(
                    ride = current.ride,
                    driver = driver,
                    driverLocation = driverLocation,
                    myLocation = myLocation.coordinates,
                    onOpenChat = onOpenChat,
                    onBack = onBack,
                )
            }
        }
    }
}

/**
 * 기사가 탑승 처리를 마쳤는가 — 25 가 26 운행 중으로 넘어갈 **유일한** 신호.
 *
 * 서버 status `IN_RIDE`([RideStatus.ONGOING]) 하나만 참이다. [DispatchStatusScreen] 에는 수동
 * 트리거가 없어 이 판정이 전이의 발화점 전부다(26 의 [com.moyeota.presentation.feature.chat.isRideFinished]
 * 와 같은 모양).
 *
 * 입력이 [RideStatus] 가 아니라 [Ride] 인 이유: 폴링이 받는 게 **방 상세뿐**이 됐다(가벼운 상태
 * 조회가 서버에서 삭제됐다 — `04f6be7`). 두 종류의 응답으로 똑같이 판정할 일이 없어졌으니, 들고
 * 있는 값 그대로 받는다. 조회가 실패한 주기(null)는 "아직 아니다"로 본다 — 한 번의 네트워크
 * 오류로 화면을 넘기지 않는다.
 */
internal fun isRideStarted(ride: Ride?): Boolean = ride?.status == RideStatus.ONGOING

/**
 * 기사 **정보**(`GET /matching/rooms/{id}/driver`)를 물어도 되는가.
 *
 * 판정 기준은 **[Ride.driverId] 하나**다. status 로 가르면 안 된다 — 서버 `MATCHING`(기사 없음)과
 * `DRIVER_ASSIGNED`(기사 있음)가 앱에서는 둘 다 [RideStatus.DISPATCHING] 으로 접히기 때문에,
 * status 만 보면 「기사 찾는 중」에도 참이 되어 5초마다 서버가 `DRIVER_NOT_ASSIGNED` 409 를 던지고
 * WARN 로그를 쌓는다(실제로 있었던 결함 — 위치 조회만 driverId 로 막고 이 호출은 놓쳤었다).
 *
 * @param alreadyLoaded 이미 받아온 뒤면 다시 묻지 않는다 — 차량 정보는 배차 동안 바뀌지 않는다
 */
internal fun shouldFetchDriverInfo(ride: Ride?, alreadyLoaded: Boolean): Boolean =
    !alreadyLoaded && shouldFetchDriverLocation(ride)

/**
 * 기사 **위치**(`GET /dispatch/rides/{id}`)를 물어도 되는가. 위와 같은 이유로 [Ride.driverId] 만 본다.
 * 정보 게이트가 이 함수를 재사용하므로 "배정됨"의 정의는 **여기 한 곳**에만 있다 — 두 게이트가 따로
 * 갈리면서 한쪽만 고쳐지는 일(이번 결함의 원인)이 다시 생기지 않게.
 */
internal fun shouldFetchDriverLocation(ride: Ride?): Boolean = ride?.driverId != null

/**
 * 기사 현재 위치 → 탑승지 **직선거리**(m). 둘 중 하나라도 못 쓸 좌표면 null.
 *
 * 좌표 검증을 [latLngOrNull] 에 맡기는 이유는 지도와 같다 — 서버가 위경도를 뒤바꿔 주는 결함(QA D-1)이
 * 있어, 범위를 벗어난 값으로 거리를 재면 「약 9,000분 뒤 도착」 같은 숫자가 나온다.
 */
private fun pickupDistanceMeters(ride: Ride, location: DriverLocation?): Double? {
    val pickup = latLngOrNull(ride.originLat, ride.originLng) ?: return null
    val driverAt = latLngOrNull(location?.latitude, location?.longitude) ?: return null
    return pickup.distanceTo(driverAt)
}
