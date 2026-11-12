package com.moyeota.presentation.feature.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.moyeota.domain.repository.RideRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * 17 탐색의 **예약 방 조회** — 「곧 출발」(`/reservations/soon`)과 「날짜로 찾기」
 * (`/reservations/search`)를 한 ViewModel 이 받는다.
 *
 * 두 엔드포인트를 갈라 두지 않은 이유: 화면에서 **동시에 보이지 않는다**(모드가 하나다). 한 흐름에
 * 두면 모드를 바꾸는 순간 [flatMapLatest] 가 앞 조회를 **취소**하므로, 늦게 끝난 「곧 출발」 응답이
 * 「날짜로 찾기」 목록을 덮어쓰는 일이 구조적으로 불가능해진다 — 두 ViewModel 이면 그 경쟁을
 * 화면에서 손으로 막아야 한다. 요청 모양의 차이는 [ReservationQuery] 가 들고 있다.
 *
 * ### 「곧 출발」만 15초마다 다시 읽는다 ★ (2026-11, 오너 UX 피드백으로 바뀜)
 * 예전에는 두 모드 모두 **폴링하지 않았다** — 「예약은 출발 시각이 바뀌지 않으니 7일 치 목록을 4초마다
 * 다시 읽을 이유가 없다」는 근거였다. 그 근거는 「날짜로 찾기」에는 그대로 맞고, 「곧 출발」에는 맞지
 * 않는다: **30분 안에** 출발하는 방이라 실시간 방만큼 빨리 낡는다(곧 떠나 목록에서 빠지고, 마지막 자리가
 * 찬다). 그래서 「곧 출발」은 실시간 목록과 **같은 주기·같은 카운트다운 버튼**을 갖는다
 * ([exploreAutoRefreshes] · `ExploreRefresh.kt`). 「날짜로 찾기」는 지금도 한 번의 검색이다.
 *
 * 다시 읽는 때: **모드·날짜·도착지를 바꿀 때**(새 [ReservationQuery]), **지도를 움직일 때**(「곧 출발」의
 * 범위가 바뀐다), **화면에 돌아올 때**([onScreenStart] — 가려진 동안은 아무것도 돌지 않는다), 「다시 시도」·
 * 새로고침 버튼([retry]·[refreshNow]), 그리고 「곧 출발」이면 15초마다.
 *
 * ### 같은 조회를 다시 읽을 때는 목록을 덮지 않는다
 * 주기·버튼·복귀는 **이미 보여 주던 조회**를 다시 읽는 것이라 로딩으로 덮지 않는다([shownQuery]) —
 * 15초마다 목록이 로딩 상자로 깜빡이면 읽을 수가 없다. 실패해도 보던 목록을 남기고, 연속
 * [STALE_NOTICE_FAILURES] 회부터 「최신 정보를 받지 못했어요」를 켠다([UiState.Success.stale] — 실시간과
 * 같은 규칙·같은 숫자). 조회가 **바뀌었을 때**만 로딩부터 보인다.
 *
 * [NearbyCandidatesViewModel][com.moyeota.presentation.feature.home.NearbyCandidatesViewModel] 과
 * 같은 모양이다(조회 + 재시도 입력 하나). 그쪽의 주의도 그대로 적용된다 — 같은 입력으로 다시
 * 조회하려면 [MutableStateFlow] 가 같은 값을 흘리지 않으므로 [attempt] 같은 두 번째 입력이 필요하다.
 */
class ReservationFinderViewModel(private val repository: RideRepository) : ViewModel() {

    sealed interface UiState {
        /**
         * **조회할 수 없다** — 실시간 모드이거나, 지도 범위·내 좌표·날짜 중 하나가 없다
         * ([reservationQueryOrNull] 의 표). 화면은 이 상태에서 **실패를 말하지 않는다**:
         * 아직 물어보지도 않았다.
         */
        data object Idle : UiState
        data object Loading : UiState

        /**
         * 서버가 준 순서 그대로의 목록. **재정렬하지 않는다** — 도착지를 보낸 검색은
         * `destinationNearby == true` 가 먼저이고 그 안에서 출발 시각 순이다. 앱이 다시 정렬하면
         * 그 두 단계 규칙을 베껴야 하고, 서버가 규칙을 바꾸면 목록이 서버와 다른 순서가 된다.
         *
         * 빈 목록은 **성공**이다(그 날 그 범위에 예약이 없을 뿐) — 실패와 다른 문구를 쓴다.
         *
         * [stale] 은 「곧 출발」의 주기 갱신이 **연속으로** 실패했다는 뜻이다 — 목록은 그대로 두고 화면이
         * 비차단 표시만 올린다(실시간 `ExploreViewModel.UiState.Success.stale` 과 같다).
         */
        data class Success(val items: List<ReservationCardUi>, val stale: Boolean = false) : UiState

        /**
         * 조회 자체가 실패했다(네트워크·5xx·모르는 400). 「예약이 없다」와 **다른 상태**다 —
         * 둘을 합치면 장애가 「그 날은 예약이 없네」로 위장되고 사용자는 날짜만 계속 바꾼다
         * (16 추천의 `nearbyLoadFailed` 와 같은 근거).
         *
         * 400 은 사실상 앱 버그다(날짜 창·도착지 둘다/아니다·지도 범위를 전부 순수 함수가 막는다)
         * — 그래서 사유별 문구를 두지 않고 한 문구 + 「다시 시도」로 받는다.
         */
        data object Failed : UiState
    }

    private val query = MutableStateFlow<ReservationQuery?>(null)

    /** 같은 요청을 **다시** 보내는 유일한 수단 — [MutableStateFlow] 는 같은 값을 흘리지 않는다. */
    private val attempt = MutableStateFlow(0)

    /**
     * 화면이 지금 **보이는가** — 실시간 목록의 같은 이름 스위치와 같다(`ExploreViewModel.visible`).
     * 가려지면 조회·카운트다운이 함께 취소되고, 다시 보이면 같은 조회로 즉시 한 번 읽는다(덮지 않고).
     */
    private val visible = MutableStateFlow(false)

    /**
     * 마지막으로 **목록을 보여 준** 조회. 같은 조회를 다시 읽을 때(주기·버튼·복귀) 로딩으로 덮지 않고,
     * 실패해도 그 목록을 남기는 근거다. 조회가 바뀌면(모드·날짜·범위) 이 값과 달라져 로딩부터 보인다.
     */
    @Volatile
    private var shownQuery: ReservationQuery? = null

    /** [shownQuery] 로 마지막에 보여 준 목록 — 같은 조회의 갱신이 실패했을 때 「낡음」 표시와 함께 다시 흘린다. */
    @Volatile
    private var shownItems: List<ReservationCardUi>? = null

    /**
     * 「곧 출발」의 **카운트다운 새로고침 버튼**이 그릴 것(`ExploreRefresh.kt`). 다른 모드에서는 화면이
     * 버튼을 그리지 않으므로 이 값을 읽지 않는다.
     */
    private val _refresh = MutableStateFlow(ExploreRefreshUi(refreshing = true))
    val refresh: StateFlow<ExploreRefreshUi> = _refresh.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<UiState> = combine(query, attempt, visible) { q, _, shown -> q to shown }
        .flatMapLatest { (q, shown) ->
            when {
                q == null -> {
                    shownQuery = null
                    shownItems = null
                    flowOf(UiState.Idle)
                }
                // 가려진 동안은 아무것도 하지 않는다 — 보던 목록은 그대로 두고(stateIn 이 마지막 값을
                // 지킨다), 다시 보이면 이 흐름이 새로 돌며 즉시 한 번 읽는다.
                !shown -> emptyFlow()
                else -> results(q)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Idle)

    /**
     * 조회 하나의 결과 흐름 — 첫 조회 후, 「곧 출발」이면 15초마다 다시 읽는다.
     *
     * 실패 처리는 **보여 줄 목록이 있느냐**로 갈린다: 없으면 [UiState.Failed](실패를 말한다), 있으면
     * 목록을 남기고 연속 실패를 센다([staleAfterPollFailures]). 실시간 목록과 같은 판단이다.
     */
    private fun results(q: ReservationQuery): Flow<UiState> = flow {
        // 같은 조회를 다시 읽는 것이면(버튼·복귀) 이미 보여 주던 목록에서 시작한다
        var last: List<ReservationCardUi>? = shownItems.takeIf { shownQuery == q }
        var failures = 0
        // 한 번 읽고 결과를 흘린다. 이 흐름이 취소되면(조회 변경·가려짐·버튼) 그 자리에서 끝난다.
        suspend fun FlowCollector<UiState>.readOnce() {
            _refresh.value = ExploreRefreshUi(refreshing = true)
            val items = try {
                fetch(q)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (items != null) {
                failures = 0
                last = items
                shownQuery = q
                shownItems = items
                emit(UiState.Success(items))
                return
            }
            failures++
            val showing = last
            if (showing == null) {
                // 보여 줄 목록이 없다 — 「없다」가 아니라 **못 물어봤다**를 말한다
                emit(UiState.Failed)
            } else if (staleAfterPollFailures(failures)) {
                // 목록은 남기고 「낡음」만 켠다 — 지금 보이는 목록을 실패 문구로 갈아엎지 않는다
                emit(UiState.Success(showing, stale = true))
            }
        }

        if (last == null) emit(UiState.Loading)
        readOnce()
        if (!q.autoRefreshes) return@flow
        while (true) {
            countDownToNextRefresh(onTick = { left -> _refresh.value = ExploreRefreshUi(secondsLeft = left) })
            readOnce()
        }
    }

    /**
     * 조회할 요청을 올린다. **null 이면 비운다**(모드가 실시간으로 돌아갔거나 입력이 빠졌다) —
     * 그때 목록을 들고 있으면 실시간 화면에 예약 카드가 한 프레임 남는다.
     *
     * 같은 요청으로 다시 부르는 것은 공짜다(StateFlow 가 같은 값을 흘리지 않는다) — 리컴포지션마다
     * 불려도 서버를 두 번 때리지 않는다.
     */
    internal fun request(query: ReservationQuery?) {
        // `internal` 인 이유: [ReservationQuery] 는 모듈 안의 조회 계약이라 internal 이고, 공개
        // 멤버가 그 타입을 받으면 「public 함수가 internal 타입을 노출한다」로 컴파일이 막힌다.
        // 공개 클래스의 internal 멤버는 허용되고, 부르는 곳은 같은 모듈의 `ExploreRoute` 하나다.
        this.query.value = query
    }

    /** 화면이 보이기 시작했다 — 지금 조회를 즉시 한 번 읽고(덮지 않고), 「곧 출발」이면 주기를 재개한다. */
    fun onScreenStart() {
        visible.value = true
    }

    /** 화면이 가려졌다 — 조회·카운트다운을 멈춘다. 보던 목록은 그대로 둔다. */
    fun onScreenStop() {
        visible.value = false
    }

    /** 「다시 시도」 — 같은 요청을 한 번 더. 실패 화면에서 누른다. */
    fun retry() {
        attempt.update { it + 1 }
    }

    /**
     * 「곧 출발」의 **새로고침 버튼** — 즉시 다시 읽고 15부터 다시 센다. 보던 목록은 덮지 않는다
     * ([shownQuery]). 조회가 나가 있는 동안 들어온 탭은 버린다(실시간 `ExploreViewModel.refreshNow` 와 같다).
     */
    fun refreshNow() {
        if (_refresh.value.refreshing) return
        retry()
    }

    private suspend fun fetch(query: ReservationQuery): List<ReservationCardUi> = when (query) {
        // 가시 영역보다 넓게 묻는다 — 실시간 목록과 **같은 이유·같은 함수**다([MapBounds.expanded]):
        // 경계에 걸친 방이 카메라가 조금 움직일 때마다 빠졌다 들어오며 마커가 깜빡인다.
        is ReservationQuery.Soon -> query.bounds.expanded().let { area ->
            repository.getReservationsSoon(
                swLat = area.swLat,
                swLng = area.swLng,
                neLat = area.neLat,
                neLng = area.neLng,
            )
        }.map { ReservationCardUi(ride = it) }

        // 도착지는 **둘 다 또는 둘 다 아니다** — 도메인 `ReservationDestination` 이 그 상태만
        // 담으므로 여기서 한쪽만 보내는 길이 **타입에 없다**(400 `INCOMPLETE_DESTINATION`).
        is ReservationQuery.ByDate -> repository.searchReservations(
            departureLat = query.departureLat,
            departureLng = query.departureLng,
            date = query.date,
            destination = query.destination,
        ).map { ReservationCardUi(ride = it.ride, destinationNearby = it.destinationNearby) }
    }

    companion object {
        fun factory(repository: RideRepository) = viewModelFactory {
            initializer { ReservationFinderViewModel(repository) }
        }
    }
}

/**
 * 이 조회가 **주기 갱신을 하는가** — 「곧 출발」만이다([exploreAutoRefreshes] 의 표를 조회 쪽에서 본 것).
 * 두 판정이 갈리지 않게 모드 판정에 맡긴다.
 */
internal val ReservationQuery.autoRefreshes: Boolean
    get() = when (this) {
        is ReservationQuery.Soon -> exploreAutoRefreshes(ExploreMode.SOON)
        is ReservationQuery.ByDate -> exploreAutoRefreshes(ExploreMode.BY_DATE)
    }
