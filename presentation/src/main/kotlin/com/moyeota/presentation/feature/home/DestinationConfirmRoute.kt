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
import com.moyeota.domain.model.MatchCandidate
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
                        // 반경은 싣지 않는다 — 서버가 요청에서 빼고 고정값(500m)을 쓴다
                        // (Backend `a668aee`). 16 의 반경 칩도 함께 내렸다([MatchConditions]).
                    ),
                )
                _uiState.update { it.copy(creating = false, createdParty = party) }
            } catch (e: Exception) {
                _uiState.update { it.copy(creating = false, errorMessage = "동승 방을 만들지 못했어요. 잠시 후 다시 시도해 주세요") }
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
 * 16 에서 보여 줄 **「걸어서 가까운 방」 추천**(`POST /matching/rooms/realtime`).
 *
 * [RoutePreviewViewModel] 과 같은 모양이고 같은 좌표로 **함께** 1회 부른다 — 둘 다 "출발·도착이
 * 확정된 순간"에만 의미가 있고, 폴링할 값이 아니다(후보마다 서버가 도보 경로를 2번 계산한다).
 *
 * **[UiState.Hidden] 이 이 화면의 기본 상태다.** 서버가 출발지·도착지 **각각** 300m 안(AND)에서만
 * 고르기 때문에 초기에는 거의 항상 후보가 없다. 그래서 "없음"을 강조하지 않고 섹션을 아예 그리지
 * 않는다 — 빈 상태가 기본인데 실패처럼 보이면 안 된다.
 */
class NearbyCandidatesViewModel(private val repository: RideRepository) : ViewModel() {

    sealed interface UiState {
        /** 그릴 게 없다 — 좌표가 없거나, 후보가 없거나, 추천을 못 가져왔다. **셋 다 섹션을 숨긴다.** */
        data object Hidden : UiState
        data object Loading : UiState

        /** 서버가 도보 시간 순으로 정렬한 1~5개. **재정렬하지 않는다.** */
        data class Success(val candidates: List<MatchCandidate>) : UiState
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
                flowOf(UiState.Hidden)
            } else {
                flow {
                    emit(UiState.Loading)
                    val candidates = repository.findRealtimeCandidates(
                        departureLat = q.departureLat,
                        departureLng = q.departureLng,
                        destinationLat = q.destinationLat,
                        destinationLng = q.destinationLng,
                    )
                    // 빈 목록은 성공이지만 그릴 것이 없다 — 「후보 0개」를 띄우지 않는다.
                    emit(if (candidates.isEmpty()) UiState.Hidden else UiState.Success(candidates))
                }.catch {
                    // 추천은 **부가 기능**이다. 경로 엔진이 죽었든(502) 이미 방이 있든(409) 화면은
                    // 똑같이 섹션을 숨기고 방 만들기로 흘린다 — 여기서 배너를 띄우면 방을 만들러 온
                    // 사용자가 자기가 뭘 잘못했다고 읽는다. 사유별 분기는 RealtimeMatchException 에
                    // 남겨 두었으니 필요해지면 그때 갈라 쓴다.
                    emit(UiState.Hidden)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Hidden)

    /** 좌표 중 하나라도 없으면 추천을 비운다([RoutePreviewViewModel.preview] 와 같은 규칙). */
    fun find(origin: LatLng?, destination: LatLng?) {
        query.value =
            if (origin == null || destination == null) null
            else Query(origin.latitude, origin.longitude, destination.latitude, destination.longitude)
    }

    companion object {
        fun factory(repository: RideRepository) = viewModelFactory {
            initializer { NearbyCandidatesViewModel(repository) }
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
    // 15 에서 확정한 출발지(고른 곳 또는 실위치). 15 를 거치지 않고 들어오면 null — 방을 만들지 않고 안내만 한다
    origin: Place?,
    destination: Place?,
    onDismiss: () -> Unit = {},
    onPartyCreated: (Ride) -> Unit = {},
    /**
     * 「걸어서 가까운 방」 추천을 골랐다 — 그 방의 합류 확인(19)으로 보낸다.
     *
     * [onPartyCreated] 와 갈라 둔 이유: 저쪽은 **방을 만든 뒤** 21 대기로 가고, 이쪽은 아무것도
     * 만들지 않고 남의 방 합류 흐름을 탄다. 같은 콜백으로 묶으면 호출부가 둘을 구분할 수 없다.
     */
    onCandidateSelected: (Ride) -> Unit = {},
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
    val originPosition = adjustedOrigin ?: latLngOrNull(origin?.latitude, origin?.longitude)
    val destinationPosition =
        adjustedDestination ?: latLngOrNull(destination?.latitude, destination?.longitude)

    // 진입 시 1회 + 핀 조정으로 좌표가 바뀔 때마다 경로 미리보기를 다시 부른다
    val routeViewModel: RoutePreviewViewModel =
        viewModel(factory = RoutePreviewViewModel.factory(repository))
    val routeState by routeViewModel.uiState.collectAsState()
    LaunchedEffect(originPosition, destinationPosition) {
        routeViewModel.preview(originPosition, destinationPosition)
    }

    // 「걸어서 가까운 방」 추천 — 경로 미리보기와 **같은 좌표로 같이** 1회. 폴링하지 않는다
    // (후보마다 서버가 도보 경로를 2번 계산한다).
    val candidatesViewModel: NearbyCandidatesViewModel =
        viewModel(factory = NearbyCandidatesViewModel.factory(repository))
    val candidatesState by candidatesViewModel.uiState.collectAsState()
    LaunchedEffect(originPosition, destinationPosition) {
        candidatesViewModel.find(originPosition, destinationPosition)
    }
    val nearbyCandidates =
        (candidatesState as? NearbyCandidatesViewModel.UiState.Success)?.candidates.orEmpty()

    // 좌표 → 주소. 두 시점에만 부른다: (1) 조정 확정으로 좌표가 실제로 바뀐 필드, (2) 아래 GPS 출발지.
    val reverseViewModel: ReverseGeocodeViewModel =
        viewModel(factory = ReverseGeocodeViewModel.factory(placeRepository))
    val resolvedNames by reverseViewModel.uiState.collectAsState()

    // GPS 로 잡은 출발지는 이름이 「현재 위치」라는 고정 문구뿐이라 진입하자마자 실주소로 바꾼다.
    // 검색으로 고른 장소명(「CGV 서면」)은 사용자가 고른 정답이므로 건드리지 않는다.
    // 키가 origin 자체라 조정으로 좌표가 바뀌어도 재실행되지 않는다 — 조정 쪽은 onAdjustPositions 담당.
    LaunchedEffect(origin) {
        if (origin != null && origin.name == CurrentLocationName) {
            reverseViewModel.resolve(PinTarget.ORIGIN, latLngOrNull(origin.latitude, origin.longitude))
        }
    }

    // 주소를 받아냈으면 표시·전송 이름을 그것으로 바꾼다. 못 받았으면 원래 이름 그대로.
    val originName = resolvedNames.originAddress ?: origin?.name ?: "—"
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
    // 고정 좌표로 채우지 않는다 — 실제 출발 위치가 아닌 곳으로 방이 만들어진다.
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
            ?: "도착지 좌표가 없어요. 15 목적지 화면에서 장소를 다시 선택해 주세요".takeIf { destination == null }
            ?: "출발지가 없어요. 15 목적지 화면에서 출발지를 검색해 주세요".takeIf { origin == null },
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
        nearbyCandidates = nearbyCandidates,
        // 추천을 고르면 **방을 만들지 않고** 그 방의 합류 확인(19)으로 넘긴다 — 합류 흐름은
        // 이미 있는 걸 그대로 쓴다. 인원·요금처럼 추천 응답에 없던 값은 거기서 방 상세로 채워진다.
        onCandidateClick = { candidate -> onCandidateSelected(candidate.ride) },
        onFindCompanions = { conditions ->
            if (origin != null && destination != null) {
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
