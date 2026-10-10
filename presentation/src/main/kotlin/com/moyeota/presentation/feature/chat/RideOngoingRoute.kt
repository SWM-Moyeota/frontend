package com.moyeota.presentation.feature.chat

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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

/** 운행 완료 폴링 간격. 21 매칭 대기의 상태 폴링과 같은 주기로 맞춘다. */
private const val RIDE_POLL_INTERVAL_MS = 4_000L

private const val TAG = "RideOngoing"

class RideOngoingViewModel(
    private val repository: RideRepository,
    private val partyId: Long,
) : ViewModel() {

    // 관찰 대상은 status 하나뿐이라 Loading/Error/Success 3상태를 두지 않고 "완료됐는가"만 내보낸다
    // — 폴링이 실패해도 화면은 그대로 살아있어야 한다.
    private val _finished = MutableStateFlow(false)
    val finished: StateFlow<Boolean> = _finished.asStateFlow()

    // 지도(출발·도착 마커, 경로)용 방 상세. **한 번만** 읽는다 — 여기서 쓰는 값(출발·도착 좌표,
    // routePolyline)은 방 생성 시 서버가 확정해 박아두는 값이라 운행 중에 바뀌지 않는다.
    // 지도는 부가 정보라 UiState 를 만들지 않고, 못 받은 동안(null)에는 마커 없는 지도를 그린다.
    private val _ride = MutableStateFlow<Ride?>(null)
    val ride: StateFlow<Ride?> = _ride.asStateFlow()

    /**
     * 가벼운 상태 조회([RideRepository.getPartyStatus], `GET /matching/rooms/{id}/status`)를 주기적으로
     * 읽어 기사측 운행 종료(`POST /dispatch/rides/{id}/complete`)를 따라잡는다.
     * 서버 status IN_RIDE → FINISHED 전이가 [RideStatus.ONGOING] → [RideStatus.COMPLETED] 로 매핑된다.
     *
     * **방 상세가 아니라 상태만 읽는 이유**: 이 루프가 기다리는 건 status 하나인데 방 상세는 쿼리 3개에
     * 경로(route)까지 실어 온다. 부하테스트에서 방 상세 폴링이 전체 요청의 45% 였고 그 대부분이
     * 이 화면과 채팅의 4초 폴링이었다(Backend #177). 상태 조회는 쿼리 1개다.
     * 지도용 상세는 [shouldLoadRideDetail] 이 정하는 대로 **첫 성공 한 번**만 읽는다.
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
                // 지도용 상세 — 아직 한 번도 못 받았을 때만. 실패 시 마지막 성공값을 유지한다
                // (마커가 깜빡이면 "사라졌다"로 읽힌다).
                if (shouldLoadRideDetail(_ride.value)) {
                    runCatching { repository.getPartyDetail(partyId) }
                        .onSuccess { _ride.value = it }
                        .onFailure { Log.d(TAG, "지도용 상세 조회 실패 — 다음 주기 재시도: ${it.message}") }
                }
                val status = runCatching { repository.getPartyStatus(partyId) }
                    .onFailure { Log.d(TAG, "상태 폴링 실패 — 다음 주기 재시도: ${it.message}") }
                    .getOrNull()
                if (isRideFinished(status?.status)) {
                    Log.d(TAG, "운행 완료 감지 partyId=$partyId → 33 도착 완료")
                    _finished.value = true
                    return
                }
                delay(RIDE_POLL_INTERVAL_MS)
            }
        } finally {
            Log.d(TAG, "운행 완료 폴링 중단 partyId=$partyId")
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
 * partyId 가 없으면(채팅·34 내 기록에서 바로 들어온 경우) 폴링 없이 화면만 띄운다 — 이때는 목적지·경유
 * 순서도 알 수 없어 「목적지로 이동 중」만 보인다.
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

    // 화면이 살아있는 동안만 폴링한다 — 이탈하면 LaunchedEffect 가 취소돼 함께 멈춘다 (25 pollDriver 와 동일)
    LaunchedEffect(viewModel) {
        viewModel.observeRideFinish()
    }

    LaunchedEffect(finished) {
        if (finished) onRideFinished()
    }

    // 경로는 방 상세의 routePolyline(서버가 방 생성 시 확정한 경로)을 그대로 쓴다 — 25 와 동일.
    // 상세는 폴링하지 않고 처음 한 번만 읽으므로 이 값도 운행 내내 그대로다(바뀔 값이 아니다).
    val routePath = remember(ride?.routePolyline) {
        ride?.routePolyline?.let(::decodePolyline).orEmpty()
    }
    RideOngoingScreen(
        // 좌표는 범위 검증(latLngOrNull)을 거친다 — 서버 위경도 전치 결함(QA D-1) 방어, 25 와 동일 판정
        originPosition = latLngOrNull(ride?.originLat, ride?.originLng),
        destinationPosition = latLngOrNull(ride?.destinationLat, ride?.destinationLng),
        routePath = routePath,
        myLocation = myLocation.coordinates,
        // 시트 상단 「{목적지}까지 약 N분」·경유 순서 카드 — 상세를 받기 전(null)엔 일반 문구만, 카드는 숨긴다
        originName = ride?.origin,
        destinationName = ride?.destination,
        estimatedMinutes = ride?.estimatedMinutes,
        onBack = onBack,
        onOpenChat = onOpenChat,
        onReport = onReport,
    )
}

/**
 * 운행이 끝났는가 — 33 도착 완료로 넘길 **유일한** 신호.
 *
 * [RideStatus.COMPLETED](서버 `FINISHED`) 하나만 참이다. [RideStatus.CANCELED] 는 **일부러 뺀다**:
 * 서버가 방을 CANCELED 로 닫는 건 기사 매칭 3분 타임아웃처럼 **탑승 전** 상황이고, 그건 25 배차 현황이
 * 「기사님을 찾지 못했어요」로 다룬다. 운행 중 화면에서 취소를 완료로 접으면 타지도 않은 운행의
 * 최종 요금 화면이 열린다 — 정상 종료는 FINISHED 뿐이다([com.moyeota.domain.model.RideStatus] KDoc).
 *
 * 상태 조회가 실패한 주기(null)는 "아직 안 끝났다"로 본다 — 한 번의 네트워크 오류로 화면을 넘기지 않는다.
 */
internal fun isRideFinished(status: RideStatus?): Boolean = status == RideStatus.COMPLETED

/**
 * 지도용 방 상세를 읽어야 하는가 — **아직 한 번도 못 받았을 때만** 참.
 *
 * 상세에서 쓰는 값(출발·도착 좌표, routePolyline)은 방 생성 시 확정돼 운행 중 바뀌지 않으니 한 번이면
 * 충분하다. 그래도 `held != null` 로만 끊는 이유는 **첫 조회가 실패할 수 있기** 때문이다 — 그때 다시
 * 묻지 않으면 운행이 끝날 때까지 마커·경로 없는 빈 지도가 남는다(예전엔 매 주기 상세를 읽어 저절로 복구됐다).
 */
internal fun shouldLoadRideDetail(held: Ride?): Boolean = held == null
