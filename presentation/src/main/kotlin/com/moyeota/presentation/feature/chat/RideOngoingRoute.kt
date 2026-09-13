package com.moyeota.presentation.feature.chat

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.moyeota.domain.model.MemberLocation
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.moyeota.core.designsystem.component.decodePolyline
import com.moyeota.core.designsystem.component.latLngOrNull
import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RideStatus
import com.moyeota.domain.repository.RideRepository
import com.moyeota.presentation.core.location.rememberMyLocationState
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive

/** 운행 완료 폴링 간격. 21 매칭 대기의 방 상세 폴링과 같은 주기로 맞춘다. */
private const val RIDE_POLL_INTERVAL_MS = 4_000L

/**
 * 동승자 위치 보고·조회 주기. 서버 TTL(60초)보다 훨씬 짧아야 잠깐 끊겨도 점이 사라지지 않는다.
 * 걷는 속도(~1.4m/s)에서 5초면 7m — 탑승 지점에서 서로를 찾는 데 충분한 해상도다.
 */
private const val LOCATION_SHARE_INTERVAL_MS = 5_000L

private const val TAG = "RideOngoing"

class RideOngoingViewModel(
    private val repository: RideRepository,
    private val partyId: Long,
) : ViewModel() {

    // 관찰 대상은 status 하나뿐이라 Loading/Error/Success 3상태를 두지 않고 "완료됐는가"만 내보낸다
    // — 폴링이 실패해도 화면은 그대로 살아있어야 한다.
    private val _finished = MutableStateFlow(false)
    val finished: StateFlow<Boolean> = _finished.asStateFlow()

    // 지도(출발·도착 마커, 경로)용 방 상세. 완료 폴링이 매 주기 읽어오는 값을 그대로 흘려보낸다 —
    // 지도는 부가 정보라 UiState 를 만들지 않고, 못 받은 동안(null)에는 마커 없는 지도를 그린다.
    private val _ride = MutableStateFlow<Ride?>(null)
    val ride: StateFlow<Ride?> = _ride.asStateFlow()

    /**
     * 동승자들의 실시간 위치. 서버가 TTL 60초 안에 보고가 있는 멤버만 주므로 **빈 목록이 정상**이다
     * (상대가 앱을 껐거나 아직 화면을 안 열었을 때). 실패는 삼키고 마지막 값을 유지한다 —
     * 한 번의 네트워크 오류로 점이 깜빡이면 "사라졌다"로 읽힌다.
     */
    private val _memberLocations = MutableStateFlow<List<MemberLocation>>(emptyList())
    val memberLocations: StateFlow<List<MemberLocation>> = _memberLocations.asStateFlow()

    /**
     * 방 상세를 주기적으로 다시 읽어 기사측 운행 종료(`POST /dispatch/rides/{id}/complete`)를 따라잡는다.
     * 서버 status IN_RIDE → FINISHED 전이가 [RideStatus.ONGOING] → [RideStatus.COMPLETED] 로 매핑된다
     * (PartyMappers: "FINISHED", "CANCELED" → COMPLETED). CANCELED 는 모집 중에만 발생해 운행 중에는 올 수 없다.
     *
     * 21 [com.moyeota.presentation.feature.matching.MatchWaitingViewModel] 의 observeAutoMatching 과 같은
     * 루프지만, 스코프는 25 배차 현황의 pollDriver 처럼 **호출자(Route)** 것을 쓴다 —
     * LaunchedEffect 가 화면 이탈 시 이 함수를 취소해 폴링이 함께 멈춘다.
     *
     * 폴링 실패는 일시적 네트워크 문제일 뿐이라 화면을 에러로 덮지 않고 다음 주기를 기다린다
     * (운행 중 화면은 27 신고 진입점이라 무슨 일이 있어도 가려지면 안 된다 — QA D-3 동류).
     */
    suspend fun observeRideFinish() {
        Log.d(TAG, "운행 완료 폴링 시작 partyId=$partyId")
        try {
            while (currentCoroutineContext().isActive) {
                val ride = runCatching { repository.getPartyDetail(partyId) }
                    .onFailure { Log.d(TAG, "폴링 실패 — 다음 주기 재시도: ${it.message}") }
                    .getOrNull()
                if (ride != null) _ride.value = ride // 실패 시 마지막 성공값 유지 — 마커가 깜빡이면 안 된다
                if (ride?.status == RideStatus.COMPLETED) {
                    Log.d(TAG, "운행 완료 감지 partyId=$partyId → 28 최종 요금")
                    _finished.value = true
                    return
                }
                delay(RIDE_POLL_INTERVAL_MS)
            }
        } finally {
            Log.d(TAG, "운행 완료 폴링 중단 partyId=$partyId")
        }
    }

    /**
     * 내 위치를 올리고 동승자 위치를 받아오는 루프. 완료 폴링과 **따로** 돈다 —
     * 주기가 다르고(위치 5초 / 완료 4초), 한쪽 실패가 다른 쪽을 멈추면 안 된다.
     *
     * 호출자(Route)의 스코프에서 돌려 화면을 떠나면 함께 멈춘다. 멈추면 60초 뒤 서버 TTL 이
     * 내 좌표를 지우므로 동승자 화면에서도 자연스럽게 사라진다 — 별도 「공유 종료」 호출이 필요 없다.
     *
     * @param myPosition 최신 기기 좌표를 돌려주는 함수. 권한이 없거나 아직 fix 가 없으면 null 을
     *   돌려주며, 그때는 **보고만 건너뛰고** 동승자 조회는 계속한다(내 위치를 못 줘도 남은 볼 수 있다)
     */
    suspend fun shareLocations(myPosition: () -> Pair<Double, Double>?) {
        Log.d(TAG, "위치 공유 시작 partyId=$partyId")
        try {
            while (currentCoroutineContext().isActive) {
                myPosition()?.let { (latitude, longitude) ->
                    runCatching { repository.reportMyLocation(partyId, latitude, longitude) }
                        .onFailure { Log.d(TAG, "내 위치 보고 실패 — 다음 주기: ${it.message}") }
                }
                runCatching { repository.getMemberLocations(partyId) }
                    .onSuccess { _memberLocations.value = it }
                    .onFailure { Log.d(TAG, "동승자 위치 조회 실패 — 마지막 값 유지: ${it.message}") }
                delay(LOCATION_SHARE_INTERVAL_MS)
            }
        } finally {
            Log.d(TAG, "위치 공유 중단 partyId=$partyId")
        }
    }

    companion object {
        fun factory(repository: RideRepository, partyId: Long) = viewModelFactory {
            initializer { RideOngoingViewModel(repository, partyId) }
        }
    }
}

/**
 * 26 운행 중 — 서버 운행 완료 상태 폴링 진입점.
 *
 * partyId 가 없으면(채팅·34 내 기록에서 바로 들어온 경우) 폴링 없이 화면만 띄운다.
 * 28 로의 전이는 서버 status 전이가 유일한 신호다 — 화면 안에 수동 트리거는 없다.
 */
@Composable
fun RideOngoingRoute(
    repository: RideRepository,
    partyId: Long?,
    onBack: () -> Unit = {},
    onOpenChat: () -> Unit = {},
    onReport: () -> Unit = {},
    onRideFinished: () -> Unit = {},
) {
    // 내 현재 위치 — 지도에 파란 점으로 계속 반영된다(1초 주기, 화면이 보이는 동안만).
    // 권한 다이얼로그는 새로 띄우지 않는다(운행 흐름을 끊지 않게 — PinAdjustOverlay 와 같은 이유).
    // 지도까지 온 사용자라면 15~19 에서 이미 허용했고, 없으면 점 없이 마커만 그린다.
    val myLocation = rememberMyLocationState(autoRequestPermission = false)

    if (partyId == null) {
        // 서버 좌표 없이도 지도는 띄운다 — 내 위치가 있으면 그 점이라도 보인다
        RideOngoingScreen(
            myLocation = myLocation.coordinates,
            onBack = onBack,
            onOpenChat = onOpenChat,
            onReport = onReport,
        )
        return
    }

    val viewModel: RideOngoingViewModel = viewModel(
        key = "ongoing-$partyId",
        factory = RideOngoingViewModel.factory(repository, partyId),
    )
    val finished by viewModel.finished.collectAsState()
    val ride by viewModel.ride.collectAsState()
    val memberLocations by viewModel.memberLocations.collectAsState()

    // 화면이 살아있는 동안만 폴링한다 — 이탈하면 LaunchedEffect 가 취소돼 함께 멈춘다 (25 pollDriver 와 동일)
    LaunchedEffect(viewModel) {
        viewModel.observeRideFinish()
    }

    // 위치 공유도 화면이 살아있는 동안만. 최신 좌표는 key 가 아니라 **최신 참조**로 읽는다 —
    // key 로 넣으면 1초마다 루프가 다시 시작돼 5초 주기가 무너진다.
    val currentMyLocation by rememberUpdatedState(myLocation.coordinates)
    LaunchedEffect(viewModel) {
        viewModel.shareLocations { currentMyLocation?.let { it.latitude to it.longitude } }
    }
    LaunchedEffect(finished) {
        if (finished) onRideFinished()
    }

    // 경로는 방 상세의 routePolyline(서버가 방 생성 시 확정한 경로)을 그대로 쓴다 — 25 와 동일
    val routePath = remember(ride?.routePolyline) {
        ride?.routePolyline?.let(::decodePolyline).orEmpty()
    }
    RideOngoingScreen(
        // 좌표는 범위 검증(latLngOrNull)을 거친다 — 서버 위경도 전치 결함(QA D-1) 방어, 25 와 동일 판정
        originPosition = latLngOrNull(ride?.originLat, ride?.originLng),
        destinationPosition = latLngOrNull(ride?.destinationLat, ride?.destinationLng),
        routePath = routePath,
        myLocation = myLocation.coordinates,
        memberLocations = memberLocations,
        onBack = onBack,
        onOpenChat = onOpenChat,
        onReport = onReport,
    )
}
