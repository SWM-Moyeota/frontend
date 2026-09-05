package com.moyeota.presentation.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.moyeota.core.designsystem.component.latLngOrNull
import com.moyeota.domain.model.NewParty
import com.moyeota.domain.model.Place
import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RouteEstimate
import com.moyeota.domain.repository.PlaceRepository
import com.moyeota.domain.repository.RideRepository
import com.naver.maps.geometry.LatLng
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

// 방 생성자는 서버가 Bearer 토큰 주체로 정한다 — 요청 본문에 사용자 id 를 싣지 않는다.
class CreatePartyViewModel(private val repository: RideRepository) : ViewModel() {

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

        fun factory(repository: RideRepository) = viewModelFactory {
            initializer { CreatePartyViewModel(repository) }
        }
    }
}

/**
 * 16 경로 미리보기 — 좌표만으로 예상 요금·소요시간·폴리라인을 받아온다 (POST /matching/routes).
 *
 * 핀 조정으로 좌표가 바뀔 때마다 다시 부른다. 조정 확정을 연타하면 요청이 겹치는데,
 * [flatMapLatest] 가 **직전 호출을 취소**하므로 늦게 끝난 옛 응답이 최신 값을 덮어쓰지 못한다.
 * 좌표를 StateFlow 로 들고 있어 같은 좌표로는 재호출되지 않는다(리컴포지션 중복 호출 방지).
 */
class RoutePreviewViewModel(private val repository: RideRepository) : ViewModel() {

    sealed interface UiState {
        /** 좌표가 아직(또는 끝내) 없어 부를 수 없다 */
        data object Idle : UiState
        data object Loading : UiState
        data class Success(val estimate: RouteEstimate) : UiState

        /** 미리보기는 부가 정보라 사용자에게 배너를 띄우지 않는다 — 값만 감춘다 */
        data object Error : UiState
    }

    private data class Query(
        val departureLat: Double,
        val departureLng: Double,
        val destinationLat: Double,
        val destinationLng: Double,
    )

    private val query = MutableStateFlow<Query?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<UiState> = query
        .flatMapLatest { q ->
            if (q == null) {
                flowOf(UiState.Idle)
            } else {
                flow {
                    emit(UiState.Loading)
                    emit(
                        UiState.Success(
                            repository.previewRoute(
                                departureLat = q.departureLat,
                                departureLng = q.departureLng,
                                destinationLat = q.destinationLat,
                                destinationLng = q.destinationLng,
                            ),
                        ),
                    )
                }.catch { emit(UiState.Error) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Idle)

    /** 좌표 중 하나라도 없으면 미리보기를 비운다(15 를 거치지 않은 진입) */
    fun preview(origin: LatLng?, destination: LatLng?) {
        query.value =
            if (origin == null || destination == null) null
            else Query(origin.latitude, origin.longitude, destination.latitude, destination.longitude)
    }

    companion object {
        fun factory(repository: RideRepository) = viewModelFactory {
            initializer { RoutePreviewViewModel(repository) }
        }
    }
}

/**
 * 16 핀 좌표 → 주소 (GET /places/reverse). 출발지·도착지의 **표시 이름과 방 생성 요청에 실리는
 * 이름**을 실주소로 갈아 끼운다.
 *
 * 이름 갱신은 부가 기능이라 **흐름을 절대 막지 않는다**:
 * - 실패(네트워크·400·502)하면 조용히 기존 이름을 유지한다. 배너·스낵바를 띄우지 않는다.
 * - 주소가 없는 좌표(바다 등, Repository 계약상 `null`)도 정상 결과라 마찬가지로 기존 이름 유지.
 * - 호출 중에도 확정 버튼을 잠그지 않는다 — 낙관적으로 진행하고 응답이 오면 그때 이름이 바뀐다.
 *
 * 팬 중에는 부르지 않는다. 호출 시점은 「이 위치로 설정」 확정 순간과 16 진입(GPS 출발지) 둘뿐이다.
 */
class ReverseGeocodeViewModel(private val repository: PlaceRepository) : ViewModel() {

    /** 필드별로 확정된 주소. null = 아직(또는 끝내) 없음 → 원래 장소 이름을 그대로 쓴다 */
    data class UiState(
        val originAddress: String? = null,
        val destinationAddress: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // 좌표 → 주소. **null 값도 캐시한다** — 「주소가 없는 좌표」라는 사실 자체가 안정된 결과다.
    private val addresses = mutableMapOf<String, String?>()

    // 대상별 마지막으로 요청한 좌표. 같은 좌표로 재확정하면 서버를 다시 부르지 않는다
    // (조정 화면은 한쪽만 만져도 양쪽 좌표를 함께 돌려주므로 중복 호출이 쉽게 생긴다).
    private val requested = mutableMapOf<PinTarget, String>()
    private val jobs = mutableMapOf<PinTarget, Job>()

    fun resolve(target: PinTarget, position: LatLng?) {
        if (position == null) return
        val key = "%.6f,%.6f".format(position.latitude, position.longitude)
        if (requested[target] == key) return
        requested[target] = key

        if (addresses.containsKey(key)) {
            addresses[key]?.let { apply(target, it) } // 주소 없는 좌표였다면 기존 이름 유지
            return
        }

        // 확정을 연타하면 요청이 겹친다 — 직전 호출을 취소해 늦게 끝난 옛 응답이 최신 좌표를 덮지 못하게
        jobs[target]?.cancel()
        jobs[target] = viewModelScope.launch {
            val address = try {
                repository.reverseGeocode(position.latitude, position.longitude)?.address
                    .also { addresses[key] = it }
            } catch (e: CancellationException) {
                throw e // 취소는 실패가 아니다 — requested 를 지우면 새 좌표 등록이 날아간다
            } catch (e: Exception) {
                // 다음 확정 때 다시 시도할 수 있도록 요청 기록을 지운다(캐시에도 남기지 않는다)
                requested.remove(target)
                null
            }
            if (address != null) apply(target, address)
        }
    }

    private fun apply(target: PinTarget, address: String) {
        _uiState.update {
            when (target) {
                PinTarget.ORIGIN -> it.copy(originAddress = address)
                PinTarget.DESTINATION -> it.copy(destinationAddress = address)
            }
        }
    }

    companion object {
        fun factory(repository: PlaceRepository) = viewModelFactory {
            initializer { ReverseGeocodeViewModel(repository) }
        }
    }
}

// 16 도착지 확인 모달 — 방 생성 진입점. 성공하면 생성된 방을 그대로 21 매칭 대기로 넘긴다.
@Composable
fun DestinationConfirmRoute(
    repository: RideRepository,
    placeRepository: PlaceRepository,
    // 15 에서 고른 출발지 — 고르지 않았으면 호출부가 DemoOrigin 을 넘긴다
    origin: Place = DemoOrigin,
    destination: Place?,
    onDismiss: () -> Unit = {},
    onPartyCreated: (Ride) -> Unit = {},
) {
    val viewModel: CreatePartyViewModel = viewModel(factory = CreatePartyViewModel.factory(repository))
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.createdParty) {
        state.createdParty?.let(onPartyCreated)
    }

    // 핀 조정 결과. ViewModel 이 아니라 화면 상태로 들고 있다 — 방을 만드는 순간에만 쓰이고,
    // 모달을 닫으면(15 로 복귀) 검색 결과 좌표에서 다시 시작하는 편이 예측 가능하다.
    // LatLng 는 Parcelable 이라 구성 변경에도 살아남는다.
    var adjustedOrigin by rememberSaveable { mutableStateOf<LatLng?>(null) }
    var adjustedDestination by rememberSaveable { mutableStateOf<LatLng?>(null) }

    // 조정값이 있으면 그것, 없으면 검색 좌표(범위 검증 통과분)
    val originPosition = adjustedOrigin ?: latLngOrNull(origin.latitude, origin.longitude)
    val destinationPosition =
        adjustedDestination ?: latLngOrNull(destination?.latitude, destination?.longitude)

    // 진입 시 1회 + 핀 조정으로 좌표가 바뀔 때마다 경로 미리보기를 다시 부른다
    val routeViewModel: RoutePreviewViewModel =
        viewModel(factory = RoutePreviewViewModel.factory(repository))
    val routeState by routeViewModel.uiState.collectAsState()
    LaunchedEffect(originPosition, destinationPosition) {
        routeViewModel.preview(originPosition, destinationPosition)
    }

    // 좌표 → 주소. 두 시점에만 부른다: (1) 조정 확정으로 좌표가 실제로 바뀐 필드, (2) 아래 GPS 출발지.
    val reverseViewModel: ReverseGeocodeViewModel =
        viewModel(factory = ReverseGeocodeViewModel.factory(placeRepository))
    val resolvedNames by reverseViewModel.uiState.collectAsState()

    // GPS 로 잡은 출발지는 이름이 「현재 위치」라는 고정 문구뿐이라 진입하자마자 실주소로 바꾼다.
    // 검색으로 고른 장소명(「CGV 서면」)은 사용자가 고른 정답이므로 건드리지 않는다.
    // 키가 origin 자체라 조정으로 좌표가 바뀌어도 재실행되지 않는다 — 조정 쪽은 onAdjustPositions 담당.
    LaunchedEffect(origin) {
        if (origin.name == CurrentLocationName) {
            reverseViewModel.resolve(PinTarget.ORIGIN, latLngOrNull(origin.latitude, origin.longitude))
        }
    }

    // 주소를 받아냈으면 표시·전송 이름을 그것으로 바꾼다. 못 받았으면 원래 이름 그대로.
    val originName = resolvedNames.originAddress ?: origin.name
    val destinationName = resolvedNames.destinationAddress ?: destination?.name ?: "—"

    val estimate = (routeState as? RoutePreviewViewModel.UiState.Success)?.estimate
    // 출발·도착 시각은 「지금 출발」 기준. 추정이 갱신될 때 두 값을 같은 기준 시각으로 다시 찍는다.
    val departureTimeLabel = remember(estimate) { clockLabel(minutesFromNow = 0) }
    val arrivalTimeLabel = remember(estimate, routeState) {
        when {
            estimate != null -> "${clockLabel(estimate.estimatedMinutes)} 도착"
            routeState is RoutePreviewViewModel.UiState.Loading -> "도착 계산 중"
            else -> "—"
        }
    }

    // 15 를 거치지 않고 들어오면 좌표가 없어 방을 만들 수 없다 — 안내만 하고 닫기 유도.
    // 폴백으로 출발지(DemoOrigin)를 쓰면 도착지 칸에 출발지 이름이 떠서 더 헷갈린다 (QA F-2).
    DestinationConfirmModal(
        destinationName = destinationName,
        // 이름 자리가 이미 실주소면 검색 당시의 도로명은 같은 값을 두 번 적는 꼴이라 비운다.
        // (핀을 옮겼다면 그 도로명은 더 이상 핀이 선 자리도 아니다)
        destinationAddress = if (resolvedNames.destinationAddress != null) "" else destination?.roadName ?: "",
        originStopName = originName,
        departureTimeLabel = departureTimeLabel,
        arrivalTimeLabel = arrivalTimeLabel,
        routePreview = RoutePreviewUi(
            loading = routeState is RoutePreviewViewModel.UiState.Loading,
            estimatedMinutes = estimate?.estimatedMinutes,
            estimatedFare = estimate?.estimatedFare,
            encodedPath = estimate?.encodedPath,
        ),
        // 좌표 검증은 latLngOrNull 에 맡긴다 — 범위를 벗어난 값이면 null 이 되어
        // 지도가 엉뚱한 곳을 비추는 대신 placeholder 로 떨어진다 (QA D-1 위경도 전치 대비)
        destinationPosition = destinationPosition,
        originPosition = originPosition,
        creating = state.creating,
        errorMessage = state.errorMessage
            ?: "도착지 좌표가 없어요. 15 목적지 화면에서 장소를 다시 선택해 주세요".takeIf { destination == null },
        onDismiss = onDismiss,
        onAdjustPositions = { newOrigin, newDestination ->
            // 좌표가 **실제로 바뀐 쪽만** 역지오코딩한다. 조정 화면은 한쪽만 만져도 양쪽 좌표를
            // 함께 돌려주므로, 안 건드린 필드까지 부르면 사용자가 고른 장소명이 주소로 덮인다.
            if (movedFrom(originPosition, newOrigin)) {
                reverseViewModel.resolve(PinTarget.ORIGIN, newOrigin)
            }
            if (movedFrom(destinationPosition, newDestination)) {
                reverseViewModel.resolve(PinTarget.DESTINATION, newDestination)
            }
            adjustedOrigin = newOrigin
            adjustedDestination = newDestination
        },
        onFindCompanions = { conditions ->
            if (destination != null) {
                // 조정한 좌표와 **갱신된 이름**을 그대로 방 생성 요청에 태운다.
                // 주소를 못 받았으면 renamedTo(null) 이 원래 이름을 유지한다.
                viewModel.createParty(
                    origin.movedTo(adjustedOrigin).renamedTo(resolvedNames.originAddress),
                    destination.movedTo(adjustedDestination).renamedTo(resolvedNames.destinationAddress),
                    conditions,
                )
            }
        },
    )
}

/**
 * 「오후 6:45」 형식의 시계 라벨. minSdk 24 라 java.time 을 못 써서 [Calendar] 로 만든다
 * (프로젝트의 다른 시각 표기 — 12 결제수단 등록 — 도 같은 방식).
 */
private fun clockLabel(minutesFromNow: Int): String {
    val time = Calendar.getInstance().apply { add(Calendar.MINUTE, minutesFromNow) }
    val hour24 = time.get(Calendar.HOUR_OF_DAY)
    val meridiem = if (hour24 < 12) "오전" else "오후"
    val hour12 = if (hour24 % 12 == 0) 12 else hour24 % 12
    return "%s %d:%02d".format(meridiem, hour12, time.get(Calendar.MINUTE))
}

/** 조정 좌표가 있으면 갈아 끼운다. 없으면(조정 안 함) 검색 결과를 그대로 쓴다. */
private fun Place.movedTo(position: LatLng?): Place =
    if (position == null) this
    else copy(latitude = position.latitude, longitude = position.longitude)

/**
 * 역지오코딩으로 받은 주소가 있으면 이름을 그것으로 바꾼다. 없으면(실패·주소 없는 좌표) 원래 이름 유지.
 * 도로명은 비운다 — 이름 자리가 이미 주소라 서버에 같은 문자열을 두 번 실어 보낼 이유가 없다.
 */
private fun Place.renamedTo(address: String?): Place =
    if (address == null) this else copy(name = address, roadName = "")

/** 핀이 실제로 움직였는지. 조금이라도 다르면 새 좌표다 — 팬 한 번에 소수점 아래가 바뀐다. */
private fun movedFrom(before: LatLng?, after: LatLng?): Boolean =
    after != null &&
        (before == null || before.latitude != after.latitude || before.longitude != after.longitude)
