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
import com.moyeota.presentation.core.shouldPublishDetail
import com.moyeota.presentation.core.location.rememberMyLocationState
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive

/**
 * 운행 완료 폴링 간격 — **8초**. 기사가 운행을 끝낸 뒤 최악의 경우 이만큼 기다렸다 33 으로 넘어간다.
 *
 * 옛 4초에서 늘렸다. 매 주기 읽는 대상이 쿼리 1개짜리 상태 조회에서 **쿼리 3개짜리 방 상세**로
 * 바뀌었기 때문이다 — 서버가 `/status` 를 삭제했다(`04f6be7`). 4초를 그대로 두면 쿼리 부하가
 * 0.25 → 0.75 q/s 로 3배가 된다. 8초면 0.38 q/s 로 옛 구조와 비슷하다.
 *
 * 25 배차 상태(7초)보다 **길게** 두는 이유: 이 화면이 기다리는 전이는 운행 완료 하나뿐이고, 지도에
 * 주기마다 움직일 마커가 없다(출발·도착·경로는 방 생성 시 확정된 값이고 내 위치는 단말이 1초마다
 * 직접 갱신한다). 그래서 범위에서 가장 낮은 부하를 고를 수 있다.
 * 근거와 부하 표는 [com.moyeota.presentation.core.PARTY_DETAIL_POLL_FLOOR_MS] KDoc 에 있다.
 */
internal const val RIDE_POLL_INTERVAL_MS = 8_000L

private const val TAG = "RideOngoing"

class RideOngoingViewModel(
    private val repository: RideRepository,
    private val partyId: Long,
) : ViewModel() {

    // 관찰 대상은 status 하나뿐이라 Loading/Error/Success 3상태를 두지 않고 "완료됐는가"만 내보낸다
    // — 폴링이 실패해도 화면은 그대로 살아있어야 한다.
    private val _finished = MutableStateFlow(false)
    val finished: StateFlow<Boolean> = _finished.asStateFlow()

    // 지도(출발·도착 마커, 경로)와 시트 문구용 방 상세. 폴링이 돌려주는 값이 그대로 들어온다.
    // 여기서 쓰는 값(출발·도착 좌표, routePolyline, 목적지명, 예상 소요시간)은 방 생성 시 서버가
    // 확정해 박아두는 값이라 운행 중에 바뀌지 않는다 — 그래서 같은 값이 다시 와도 흘려보낸다
    // (shouldPublishDetail). 지도는 부가 정보라 UiState 를 만들지 않고, 못 받은 동안(null)에는
    // 마커 없는 지도를 그린다.
    private val _ride = MutableStateFlow<Ride?>(null)
    val ride: StateFlow<Ride?> = _ride.asStateFlow()

    /**
     * 방 상세([RideRepository.getPartyDetail], `GET /matching/rooms/{id}`)를 주기적으로 읽어
     * 기사측 운행 종료(`POST /dispatch/rides/{id}/complete`)를 따라잡는다.
     * 서버 status IN_RIDE → FINISHED 전이가 [RideStatus.ONGOING] → [RideStatus.COMPLETED] 로 매핑된다.
     *
     * **동승 모드에서는 이 루프가 한 번도 돌지 않는다** — 서버가 `ONGOING` 을 내지 않아 26 에 닿을
     * 길이 없다(`docs/MODE-ROUTES.md`). 택시 모드 전용이다.
     *
     * **상세를 읽는 이유**: 이 루프가 기다리는 건 status 하나뿐이라 예전에는 가벼운 상태 조회
     * (`/status`, 쿼리 1개)를 읽었다. 그 엔드포인트가 백엔드에서 삭제됐다(`04f6be7` — 매칭방 변화가
     * SSE 전용이 되면서 폴링용 조회가 쓰이지 않게 됐다). 그런데 **배차·운행 전이에는 SSE 이벤트도
     * FCM 도 없어서**(서버 발행처가 참여·퇴장·종료뿐) 폴링을 없앨 수도 없다 — 수단만 상세로 바꿨다.
     *
     * 상세는 쿼리 3개에 경로까지 실어 오는 무거운 호출이라(부하테스트에서 상세 폴링이 전체 요청의
     * 45% — Backend #177) 간격을 4초 → [RIDE_POLL_INTERVAL_MS](8초)로 늘렸고, 받은 값이 들고 있던
     * 것과 같으면 **흘려보낸다**([shouldPublishDetail]) — 바뀔 값이 없는 화면이라 평상시엔 매 주기
     * 같은 상세가 오고, 그대로 넣으면 8초마다 지도가 다시 그려진다.
     *
     * 지도용 상세를 "첫 성공 한 번만" 읽던 분기는 **없어졌다** — 폴링이 주는 게 곧 그 상세다.
     * 첫 조회가 실패해도 다음 주기가 자동으로 복구한다(예전에 `shouldLoadRideDetail` 이 하던 일).
     *
     * 스코프는 25 배차 현황의 pollDriver 처럼 **호출자(Route)** 것을 쓴다 — LaunchedEffect 가
     * 화면 이탈 시 이 함수를 취소해 폴링이 함께 멈춘다.
     *
     * 폴링 실패는 일시적 네트워크 문제일 뿐이라 화면을 에러로 덮지 않고 다음 주기를 기다린다
     * (운행 중 화면은 27 신고 진입점이라 무슨 일이 있어도 가려지면 안 된다 — QA D-3 동류).
     */
    suspend fun observeRideFinish() {
        Log.d(TAG, "운행 완료 폴링 시작 partyId=$partyId")
        try {
            while (currentCoroutineContext().isActive) {
                val held = _ride.value
                // 실패 시 마지막 성공값을 유지한다 — 마커가 깜빡이면 "사라졌다"로 읽힌다.
                val fetched = runCatching { repository.getPartyDetail(partyId) }
                    .onFailure { Log.d(TAG, "상세 폴링 실패 — 다음 주기 재시도: ${it.message}") }
                    .getOrNull()
                if (fetched != null && shouldPublishDetail(held, fetched)) {
                    _ride.value = fetched
                }
                // 판정은 **이번 주기에 받은 값**으로만 한다 — 실패한 주기(null)는 "아직 아니다"다.
                if (isRideFinished(fetched)) {
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
 * 26 운행 중 — 서버 운행 완료를 **방 상세 폴링**으로 따라잡는 진입점.
 *
 * partyId 가 없으면(채팅·34 내 기록에서 바로 들어온 경우) 폴링 없이 화면만 띄운다 — 이때는 목적지·경유
 * 순서도 알 수 없어 「목적지로 이동 중」만 보인다.
 * 33 으로의 전이는 서버 status 전이가 유일한 신호다([isRideFinished]) — 화면 안에 수동 트리거는 없다.
 *
 * **이 화면은 택시 모드에서만 보인다** — 동승 모드 서버는 `ONGOING` 을 내지 않는다
 * (`docs/MODE-ROUTES.md`). 그래서 [RideOngoingViewModel.observeRideFinish] 의 상세 폴링도
 * 택시 모드에서만 돈다.
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
    // 폴링이 매 주기 상세를 받아오지만 같은 값이면 ViewModel 이 흘려보내므로(shouldPublishDetail)
    // 이 remember 는 운행 내내 한 번만 디코드한다 — 폴리라인 디코딩은 좌표 수백 개짜리 작업이다.
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
 * 입력이 [RideStatus] 가 아니라 [Ride] 인 이유: 폴링이 받는 게 **방 상세뿐**이 됐다(가벼운 상태 조회가
 * 서버에서 삭제됐다 — `04f6be7`). 두 종류의 응답으로 똑같이 판정할 일이 없어졌으니 들고 있는 값 그대로
 * 받는다(25 의 [com.moyeota.presentation.feature.matching.isRideStarted] 와 같은 모양).
 *
 * 조회가 실패한 주기(null)는 "아직 안 끝났다"로 본다 — 한 번의 네트워크 오류로 화면을 넘기지 않는다.
 */
internal fun isRideFinished(ride: Ride?): Boolean = ride?.status == RideStatus.COMPLETED
// 「지도용 상세를 아직 못 받았을 때만 읽는다」(shouldLoadRideDetail)는 없앴다 — 폴링이 주는 게 곧
// 그 상세여서 읽을지 고를 대상이 없다. 첫 조회 실패도 다음 주기가 그냥 복구한다.
