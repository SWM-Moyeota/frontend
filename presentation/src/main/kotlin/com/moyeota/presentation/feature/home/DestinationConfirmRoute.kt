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
import com.moyeota.domain.analytics.Analytics
import com.moyeota.domain.analytics.AnalyticsEvent
import com.moyeota.domain.analytics.MatchFailureReason
import com.moyeota.domain.analytics.MatchRequestMethod
import com.moyeota.domain.analytics.administrativeArea
import com.moyeota.domain.model.MatchCandidate
import com.moyeota.domain.model.NewParty
import com.moyeota.domain.model.NewReservation
import com.moyeota.domain.model.PartyOpenException
import com.moyeota.domain.model.Place
import com.moyeota.domain.model.RealtimeMatchException
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * 사유를 가릴 수 없는 방 생성 실패의 일반 문구 — 네트워크 실패와 **모르는 코드**가 여기로 떨어진다.
 * 서버가 `MatchingErrorCode` 를 늘려도 앱이 틀린 말을 하지 않는다(21 의 `FINISH_FAILED_MESSAGE` 와
 * 같은 규칙: 지어내는 것보다 모른다고 하는 게 낫다). 문구는 예전 단일 문구를 그대로 쓴다.
 */
private const val OPEN_FAILED_MESSAGE = "동승 방을 만들지 못했어요. 잠시 후 다시 시도해 주세요"

/**
 * 「이미 참여 중인 방이 있다」 — **세 자리가 같은 말을 해야 한다.**
 *
 * 서버가 같은 사유(`ALREADY_JOINED_OTHER_PARTY`)로 막는 곳이 셋이다: 방 생성(`PartyService.open`),
 * 추천 조회(`findRealtimeCandidates`), 합류(`join`). 앞의 둘이 이 상수를 함께 쓰고, 합류는 문구 끝이
 * 다르다(「그 방에서 나온 뒤 합류해 주세요」 — 그쪽은 돌아갈 곳이 목록이라 할 일이 다르다).
 *
 * 돌아갈 곳을 **홈**으로 적는 이유: 진행 중인 방으로 들어가는 길은 홈 배너뿐이다(21 의
 * `finishFailureMessage` 가 멤버 아님에 쓰는 「홈에서 진행 중인 방을 확인해 주세요」와 같은 근거).
 */
internal const val ALREADY_IN_PARTY_MESSAGE = "이미 참여 중인 방이 있어요. 홈에서 진행 중인 방을 확인해 주세요"

/**
 * 방 생성 실패를 사용자 문구로 옮긴다 — 실패마다 **다음 행동이 다르다**
 * (20 합류의 `joinFailureMessage`·21 완료의 `finishFailureMessage` 와 같은 자리·같은 이유).
 *
 * | 사유 | 서버 코드(HTTP) | 사용자가 할 일 |
 * |---|---|---|
 * | 이미 참여 중인 방 | `ALREADY_JOINED_OTHER_PARTY` (409) | 홈 배너로 그 방에 들어가 정리한다 |
 * | 출발지 = 도착지 | `SAME_DEPARTURE_DESTINATION` (400) | **핀을 옮긴다** |
 * | 서비스 지역 밖 | `OUT_OF_SERVICE_AREA` (400) | 다른 장소를 고른다 |
 * | 정원이 1~3 아님 | `INVALID_CAPACITY` (400) | 인원을 다시 고른다(사실상 앱 버그다) |
 * | 경로를 못 받음 | `ROUTE_SEARCH_FAILED` (502) · `INVALID_ROUTE_ESTIMATE`·`ROUTE_NOT_FOUND` (400) | 잠시 뒤 다시, 또는 핀을 조금 옮긴다 |
 * | 그 밖·네트워크 | 코드 없음 | [OPEN_FAILED_MESSAGE] |
 *
 * **상태 코드로 폴백하지 않는다** — 400 이 네 가지라 「400 이니 핀을 옮겨 주세요」가 거짓이 될 수 있다.
 * 코드를 모르면 [PartyOpenException.code] 가 null 이고 일반 문구로 떨어진다(서버가 503 HTML 을
 * 돌려주는 경우도 여기다 — `readApiError()` 가 null 을 돌려준다).
 *
 * 「출발지와 도착지가 같다」에 **「잠시 후 다시」를 붙이지 않는 것**이 이 분기의 핵심이다. 15 의 50m
 * 검사를 통과한 뒤에도 16 의 핀 조정은 그 검사를 **다시 거치지 않아서**, 두 핀을 겹쳐 놓으면 실제로
 * 이 400 이 떨어진다 — 기다려서 풀리지 않는다.
 */
internal fun openFailureMessage(e: PartyOpenException): String = when {
    e.isAlreadyInAnotherParty -> ALREADY_IN_PARTY_MESSAGE
    e.isSameDepartureDestination -> "출발지와 도착지가 같아요. 핀을 옮겨 조금 떨어뜨려 주세요"
    e.isOutOfServiceArea -> "서비스 지역을 벗어난 위치예요. 출발지와 도착지를 다시 골라 주세요"
    e.isInvalidCapacity -> "인원은 1~3명까지만 정할 수 있어요"
    e.isRouteUnavailable -> "경로를 찾지 못했어요. 잠시 후 다시 시도하거나 출발·도착지를 조금 옮겨 주세요"
    else -> OPEN_FAILED_MESSAGE
}

/**
 * `fail_match_request(create)` 의 `reason` — [openFailureMessage] 와 같은 분기를 **문구가 아니라 상수**로 옮긴다
 * (iOS `openFailureReason` 과 같은 표). 문구를 그대로 보내면 카피를 다듬을 때마다 Mixpanel 의 집계가 갈라진다.
 */
internal fun createFailureReason(e: PartyOpenException): String = when {
    e.isAlreadyInAnotherParty -> MatchFailureReason.ALREADY_IN_PARTY
    e.isSameDepartureDestination -> MatchFailureReason.SAME_DEPARTURE_DESTINATION
    e.isOutOfServiceArea -> MatchFailureReason.OUT_OF_SERVICE_AREA
    e.isInvalidCapacity -> MatchFailureReason.INVALID_CAPACITY
    e.isRouteUnavailable -> MatchFailureReason.ROUTE_UNAVAILABLE
    else -> MatchFailureReason.UNKNOWN
}

/**
 * `request_match` 의 `origin_area`/`destination_area` — 장소 주소의 시/구.
 *
 * iOS 는 `roadName` 하나로 계산하지만, Android 의 16 은 역지오코딩이 성공하면 실주소를 `name` 에 넣고
 * `roadName` 을 비운다. 그래서 도로명을 먼저 보고, 비었거나 주소로 판정되지 않으면 이름(=역지오코딩 주소)을
 * 본다. 둘 다 판정이 서지 않으면 null — 키를 빼고 보낸다.
 */
internal fun Place.analyticsArea(): String? =
    roadName.takeIf { it.isNotBlank() }?.let(::administrativeArea) ?: administrativeArea(name)

// 방 생성자는 서버가 Bearer 토큰 주체로 정한다 — 요청 본문에 사용자 id 를 싣지 않는다.
class CreatePartyViewModel(private val repository: RideRepository) : ViewModel() {

    data class UiState(
        val creating: Boolean = false,
        val errorMessage: String? = null,
        val createdParty: Ride? = null,
        /**
         * **진행 중인 방 기억을 되살려야 한다** — 생성이 409 `ALREADY_JOINED_OTHER_PARTY` 로 막혔다.
         *
         * 문구만으로는 탈출구가 되지 않는다. 그 사람의 유일한 복구 경로는 **홈 배너 → 21 → 「그만
         * 찾기」**인데, 그 배너는 `ActivePartyRepository.resolve()` 가 방을 찾아야 뜬다 — 앱을 다시
         * 깔았거나 기억이 지워진 상태라면 조회를 한 번 더 돌려야 그 방이 보인다. 그래서 「문구」와
         * 「탈출구」를 따로 둔다.
         *
         * Route 가 이 값을 콜백으로 바꿔 올리고(`onActivePartyRefresh`), 호출부(`MainNavGraph`)가
         * `ActivePartyViewModel.refresh()` 에 꽂는다 — 이 ViewModel 은 그 홀더에 닿을 수 없다
         * (계층이 다르고, 모달은 `ActivePartyViewModel` 을 인자로 받지 않는다).
         */
        val needsActivePartyRefresh: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /**
     * 방 생성 제출. 실패는 [PartyOpenException] 으로 좁혀 올라오고 [openFailureMessage] 가 사유별
     * 문구로 가른다 — 예전에는 **모든 사유가 「잠시 후 다시 시도해 주세요」 하나**였다.
     *
     * 그게 특히 나쁜 자리였던 까닭은 이 엔드포인트의 실패가 **기다려서 풀리는 것과 사용자가 고쳐야
     * 하는 것으로 갈리기** 때문이다: 409 「이미 참여 중인 방」은 그 방을 정리해야 풀리고(그 방이 아무도
     * 합류하지 않은 ACTIVE 방이면 서버 30분 스윕에도 남아 **영구 교착**이다), 400 「출발지와 도착지가
     * 같다」는 핀을 옮겨야 풀린다. 둘 다 「잠시 후 다시」로 안내하면 영원히 같은 실패를 맞는다.
     */
    fun createParty(origin: Place, destination: Place, conditions: MatchConditions) {
        if (_uiState.value.creating) return // 중복 제출 차단
        // **이 한 값이 엔드포인트를 가른다**([MatchConditions.departureAt]) — null 이면 실시간 방
        // (`POST /matching/rooms`), 값이 있으면 예약 방(`POST /matching/reservations`).
        // 정원 범위·실패 문구도 함께 갈린다(예약은 2~3, 실시간은 1~3).
        val departureAt = conditions.departureAt
        val mode = if (departureAt == null) DepartureMode.NOW else DepartureMode.SCHEDULED
        // 모달이 이미 접어서 보내지만 계약을 여기서 한 번 더 못박는다 — 벗어나면 400
        // `INVALID_CAPACITY` 이고, 값이 **빠지면** 서버가 primitive 로 풀다 500 을 낸다
        // (사유 없는 실패 = 일반 문구). [capacityFor] 가 모드별 범위를 아는 단일 창구다.
        val capacity = capacityFor(mode, conditions.capacity)
        viewModelScope.launch {
            _uiState.update { it.copy(creating = true, errorMessage = null) }
            // Click — 요청이 나가는 순간(서버 응답 전). iOS 와 같이 사용자가 고른 정원 그대로 싣는다.
            Analytics.track(AnalyticsEvent.PartyCreateClicked(capacity))
            try {
                val party = if (departureAt != null) {
                    // 예약 방([NewReservation]). 요청 필드가 [NewParty] 와 **출발 시각 하나만**
                    // 다르지만 모델이 둘인 이유는 정원 하한이다 — 실시간 1, 예약 2(그 KDoc 의 표).
                    // 돌아오는 것은 같은 [Ride] 이고 경로·요금·소요 시간이 **비어 있다**
                    // (`Ride` 의 기존 nullable 필드가 그 사실을 이미 표현한다).
                    repository.createReservation(
                        NewReservation(
                            departureLat = origin.latitude,
                            departureLng = origin.longitude,
                            destinationLat = destination.latitude,
                            destinationLng = destination.longitude,
                            departure = origin.name,
                            destination = destination.name,
                            capacity = capacity,
                            departureAt = departureAt,
                        ),
                    )
                } else {
                    repository.createParty(
                        NewParty(
                            departureLat = origin.latitude,
                            departureLng = origin.longitude,
                            destinationLat = destination.latitude,
                            destinationLng = destination.longitude,
                            departure = origin.name,
                            destination = destination.name,
                            capacity = capacity,
                            // 반경은 싣지 않는다 — 서버가 요청에서 빼고 고정값(500m)을 쓴다
                            // (Backend `a668aee`). 16 의 반경 칩도 함께 내렸다([MatchConditions]).
                        ),
                    )
                }
                _uiState.update { it.copy(creating = false, createdParty = party) }
                // 핵심 퍼널 2단계(방 만들기 쪽). 지역은 시/구까지만 — 좌표·번지는 싣지 않는다.
                //
                // **예약도 `create` 로 센다.** [MatchRequestMethod] 에 `reserve` 를 더하는 일은
                // domain 변경이고(이 작업에서 다른 사람이 쥔 모듈이다) 퍼널 이름 셋
                // (`complete_signup`→`request_match`→`complete_match`)이 Mixpanel 보고서에 박혀
                // 있어, 값을 늘리면 그 보고서가 갈린다. 예약을 따로 세고 싶어지면 그때 도메인에
                // 값을 더한다 — 지금은 「방을 만들었다」가 참이고, [estimatedFare] 는 예약 방에서
                // null 이라 **키가 빠진다**(없는 요금을 0 으로 지어내지 않는다).
                Analytics.track(
                    AnalyticsEvent.MatchRequested(
                        method = MatchRequestMethod.CREATE,
                        capacity = party.capacity,
                        originArea = origin.analyticsArea(),
                        destinationArea = destination.analyticsArea(),
                        estimatedFare = party.estimatedFare,
                    ),
                )
            } catch (e: CancellationException) {
                // 모달을 닫으며 취소된 것 — 실패 문구를 띄우지 않는다(20 합류와 같은 처리)
                throw e
            } catch (e: PartyOpenException) {
                _uiState.update {
                    it.copy(
                        creating = false,
                        // **같은 예외, 다른 표.** 예약에만 오는 세 코드(403 `NO_SHOW_RESTRICTED` ·
                        // 409 `PARTY_TIME_CONFLICT` · 400 `INVALID_DEPARTURE_TIME`)가 실시간 표에
                        // 없어서, 한 함수로 뭉개면 그 셋이 전부 「잠시 후 다시 시도해 주세요」가 된다
                        // — 세 가지 모두 기다려서 풀리지 않는다([reservationOpenFailureMessage]).
                        errorMessage = if (departureAt != null) {
                            reservationOpenFailureMessage(e)
                        } else {
                            openFailureMessage(e)
                        },
                        // 409 는 문구만으로 풀리지 않는다 — 홈 배너가 뜨도록 진행 중 방 조회를 되살린다.
                        needsActivePartyRefresh = it.needsActivePartyRefresh || e.isAlreadyInAnotherParty,
                    )
                }
                Analytics.track(AnalyticsEvent.MatchRequestFailed(MatchRequestMethod.CREATE, createFailureReason(e)))
            } catch (e: Exception) {
                // 데이터 계층이 좁히지 못한 실패(직렬화 등). 사유를 모르므로 일반 문구다 —
                // 「무엇을 만들려다 실패했는지」만 모드에 맞춘다.
                val general = if (departureAt != null) RESERVATION_OPEN_FAILED_MESSAGE else OPEN_FAILED_MESSAGE
                _uiState.update { it.copy(creating = false, errorMessage = general) }
                Analytics.track(AnalyticsEvent.MatchRequestFailed(MatchRequestMethod.CREATE, MatchFailureReason.UNKNOWN))
            }
        }
    }

    companion object {
        /**
         * 백엔드 검증 상한(서버 `Capacity` record = 1~3). 넘기면 400 `INVALID_CAPACITY` 가 오고
         * 화면은 사유별 문구로 안내한다([openFailureMessage]) — 예전에는 모든 실패가 한 문구라
         * 「에러 바디 없는 500」과 구별되지 않았다.
         *
         * 접는 일은 [capacityFor] 가 한다 — **하한이 모드에 따라 다르기 때문**이다(실시간 1,
         * 예약 2). 상한은 둘 다 3 이라 이 상수가 그 함수의 선택지 표와 같은 값을 가리킨다
         * ([capacityChoices]); 둘이 어긋나면 예약에서 4 가 전송될 수 있으니 한쪽을 고칠 때
         * 다른 쪽을 함께 본다.
         */
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
        /**
         * 그릴 게 없다 — 좌표가 없거나, 후보가 없다. **둘 다 섹션을 숨긴다.**
         *
         * 예전에는 **조회 실패까지** 여기로 접었다. 그게 결함이었다: 「후보 0개」와 「못 가져왔다」는
         * 사용자에게 구별되지 않는데 **올바른 다음 행동이 정반대**다(방 만들기 / 다시 시도). OSRM 이
         * 죽거나 503 이면 300m 안에 방이 있어도 섹션이 그냥 사라지고, 사용자는 **합승이 가능한데도**
         * 새 방을 만든다 — 합승 매칭이라는 제품의 핵심이 조용히 꺼지는 자리다.
         *
         * 그래서 실패를 [Failed]·[AlreadyInParty] 로 갈라 냈고, **이 상태의 뜻은 좁혔다.**
         * 다만 화면이 아직 새 상태를 모르면 섹션을 안 그리는 것은 같다(호출부는 `Success` 만 본다).
         */
        data object Hidden : UiState
        data object Loading : UiState

        /** 서버가 도보 시간 순으로 정렬한 1~5개. **재정렬하지 않는다.** */
        data class Success(val candidates: List<MatchCandidate>) : UiState

        /**
         * **조회 자체가 실패했다**(502 경로 엔진·503·네트워크·모르는 코드). 후보가 없는 것과 다르다.
         *
         * 화면은 이걸 **차단형 배너로 띄우지 않는다** — 방을 만들러 온 사용자가 자기가 뭘 잘못했다고
         * 읽는다. 작은 인라인 「다시 시도」 줄 하나면 된다([retry]). 그 줄이 있어야 OSRM 장애가
         * 「주변에 방이 없다」로 위장되지 않는다.
         */
        data object Failed : UiState

        /**
         * 409 `ALREADY_JOINED_OTHER_PARTY` — **이미 진행 중인 방이 있다.** 서버
         * `PartyService.findRealtimeCandidates` 도 `validateNotInOngoingParty` 를 먼저 탄다.
         *
         * 추천을 보여 줄 이유가 없다는 점은 예전과 같지만, **이 신호를 지우면 안 된다**: 바로 뒤에
         * 사용자가 누를 「방 만들기」도 **같은 409 로 막힌다**(그쪽은 영구 교착이다 —
         * [openFailureMessage] 의 409 분기 참고). 즉 이건 그 교착의 **예고 신호**이고, 화면은 같은
         * 사유를 같은 말로 안내하며([ALREADY_IN_PARTY_MESSAGE]) 진행 중 방 조회를 되살려야 한다.
         */
        data object AlreadyInParty : UiState
    }

    private data class Query(
        val departureLat: Double,
        val departureLng: Double,
        val destinationLat: Double,
        val destinationLng: Double,
    )

    private val query = MutableStateFlow<Query?>(null)

    /**
     * 「다시 시도」가 올리는 번호 — **같은 좌표로 다시 조회할 유일한 수단**이다.
     *
     * [query] 는 [MutableStateFlow] 라 같은 값을 넣으면 흘리지 않는다(그게 리컴포지션 중복 호출을
     * 막는 장치다). 재시도는 좌표가 **그대로인 채** 다시 부르는 일이라 그 중복 방지와 정면으로
     * 부딪히므로, 입력을 하나 더 두어 가른다.
     */
    private val attempt = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<UiState> = combine(query, attempt) { q, _ -> q }
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
                }.catch { cause ->
                    // 추천은 **부가 기능**이지만, 실패를 「후보 0개」와 똑같이 다루면 안 된다 —
                    // 둘의 올바른 다음 행동이 정반대다(다시 시도 / 방 만들기). 차단형 배너는
                    // 여전히 띄우지 않는다: 가르는 건 **상태**이고, 그 상태를 어떻게 보여 줄지는
                    // 화면이 정한다(UiState.Failed KDoc).
                    emit(candidatesFailure(cause))
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

    /**
     * 「다시 시도」 — **같은 좌표로** 추천을 한 번 더 조회한다([UiState.Failed] 의 인라인 줄이 부른다).
     *
     * 폴링이 아니라 사용자가 누를 때만이다: 후보마다 서버가 도보 경로를 2번 계산하므로 자동 재시도를
     * 걸면 장애 중인 경로 엔진을 앱이 대신 두드린다.
     */
    fun retry() {
        attempt.update { it + 1 }
    }

    companion object {
        fun factory(repository: RideRepository) = viewModelFactory {
            initializer { NearbyCandidatesViewModel(repository) }
        }
    }
}

/**
 * 추천 조회 실패를 화면 상태로 옮긴다 — **「후보 0개」와 섞지 않는 게 요점**이다.
 *
 * | 사유 | 상태 | 왜 갈라야 하나 |
 * |---|---|---|
 * | 409 `ALREADY_JOINED_OTHER_PARTY` | [NearbyCandidatesViewModel.UiState.AlreadyInParty] | 추천도, **방 만들기도** 막힌 상태다 — 바로 뒤의 409 교착을 예고하는 신호라 지우면 안 된다 |
 * | 그 밖(502·503·네트워크·모르는 코드) | [NearbyCandidatesViewModel.UiState.Failed] | 「주변에 방이 없다」가 아니라 **못 물어봤다**는 뜻이고, 할 일은 재시도다 |
 *
 * 빈 목록은 여기 오지 않는다 — 후보가 없는 건 성공이고, 데이터 계층이 예외로 올리지 않는다.
 *
 * [RealtimeMatchException] 이 아닌 실패(직렬화 등)도 [NearbyCandidatesViewModel.UiState.Failed] 다 —
 * 사유를 모를 때 「진행 중인 방이 있어요」라고 **추측하지 않는다**(그 말은 거짓일 때 사용자를
 * 없는 방으로 찾아 보내게 한다).
 */
internal fun candidatesFailure(cause: Throwable): NearbyCandidatesViewModel.UiState =
    if ((cause as? RealtimeMatchException)?.isAlreadyInParty == true) {
        NearbyCandidatesViewModel.UiState.AlreadyInParty
    } else {
        NearbyCandidatesViewModel.UiState.Failed
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
    /**
     * 모달을 **시간 정하기로 열까.** 17 탐색의 예약 탭에서 들어온 경우에만 참이다
     * (`MainNavGraph.createScheduled`).
     *
     * 기본값이 false 라 **지금까지의 모든 진입이 그대로다** — 홈 검색·즐겨찾기로 들어오면 전과 같이
     * 「지금 출발」로 열린다. 고르는 쪽을 바꿀 뿐이라 사용자는 모달에서 언제든 되돌릴 수 있다.
     */
    initiallyScheduled: Boolean = false,
    onDismiss: () -> Unit = {},
    onPartyCreated: (Ride) -> Unit = {},
    /**
     * 「걸어서 가까운 방」 추천을 골랐다 — 그 방의 합류 확인(19)으로 보낸다.
     *
     * [onPartyCreated] 와 갈라 둔 이유: 저쪽은 **방을 만든 뒤** 21 대기로 가고, 이쪽은 아무것도
     * 만들지 않고 남의 방 합류 흐름을 탄다. 같은 콜백으로 묶으면 호출부가 둘을 구분할 수 없다.
     */
    onCandidateSelected: (Ride) -> Unit = {},
    /**
     * **진행 중인 방 조회를 다시 돌려라** — 서버가 「이미 참여 중인 방이 있다」(409)고 답했다.
     *
     * 호출부는 `ActivePartyViewModel.refresh()` 를 꽂아야 한다. 문구만으로는 탈출구가 되지 않기
     * 때문이다: 그 사람의 유일한 복구 경로가 **홈 배너 → 21 → 「그만 찾기」**인데, 배너는
     * `ActivePartyRepository.resolve()` 가 그 방을 찾아야 뜬다
     * ([CreatePartyViewModel.UiState.needsActivePartyRefresh] KDoc).
     *
     * 기본값이 「아무것도 안 함」인 이유: 배선이 있는 곳은 `MainNavGraph` 이고 그 파일은 지금 다른
     * 작업이 쥐고 있다. 배선이 빠져도 **문구는 그대로 뜬다** — 최악이 「탈출구가 한 겹 적은 예전
     * 상태」이고, 기본값을 필수 인자로 두면 머지 순서에 따라 빌드가 깨진다.
     */
    onActivePartyRefresh: () -> Unit = {},
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

    // 「이미 참여 중인 방이 있다」를 **두 창구 중 어느 쪽에서든** 들었다 — 생성 409(눌러 본 뒤)와
    // 추천 409(누르기 전)다. 둘 다 같은 사실이고 탈출구도 같으므로 함께 올린다. 추천 쪽이 먼저
    // 뜨므로, 사용자가 「방 만들기」를 눌러 교착을 한 번 더 확인하기 전에 홈 배너가 준비된다.
    val alreadyInParty = candidatesState is NearbyCandidatesViewModel.UiState.AlreadyInParty
    val needsActivePartyRefresh = state.needsActivePartyRefresh || alreadyInParty
    LaunchedEffect(needsActivePartyRefresh) {
        if (needsActivePartyRefresh) onActivePartyRefresh()
    }

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
        initiallyScheduled = initiallyScheduled,
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
        // 추천 409 를 **누르기 전에** 알려 준다 — 그 상태에서는 「방 만들기」도 같은 409 로 막히므로
        // (서버가 두 경로 모두 validateNotInOngoingParty 를 먼저 탄다) 미리 말하는 쪽이 정확하다.
        // 눌러 본 뒤의 실패 문구(state.errorMessage)가 있으면 그게 먼저다 — 더 최근의 사실이다.
        // 추천의 **단순 실패**(UiState.Failed)는 여기 넣지 않는다: 차단형 배너로 띄우면 방을 만들러
        // 온 사용자가 자기가 뭘 잘못했다고 읽는다 — 그 자리는 인라인 재시도 줄이고, 모달 쪽 배선이
        // 필요하다(보고서 참고).
        errorMessage = state.errorMessage
            ?: ALREADY_IN_PARTY_MESSAGE.takeIf { alreadyInParty }
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
        // 「후보 0건」과 「못 물어봤다」를 모달이 구별해 그릴 수 있게 실패를 따로 넘긴다.
        // 409(이미 참여 중)는 여기가 아니라 errorMessage 슬롯으로 간다 — 그 상태에서는
        // 「방 만들기」도 같은 409 로 막히므로 재시도가 아니라 안내가 맞다.
        nearbyLoadFailed = candidatesState is NearbyCandidatesViewModel.UiState.Failed,
        onRetryNearby = candidatesViewModel::retry,
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
