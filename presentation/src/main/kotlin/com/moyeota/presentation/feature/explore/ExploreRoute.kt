package com.moyeota.presentation.feature.explore

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.moyeota.core.designsystem.component.MoyeotaTab
import com.moyeota.domain.model.Ride
import com.moyeota.domain.repository.RideRepository
import com.moyeota.presentation.core.ErrorBox
import com.moyeota.presentation.core.LoadingBox
import com.moyeota.presentation.core.TabStateScaffold
import com.moyeota.presentation.core.location.rememberMyLocationState
import com.naver.maps.geometry.LatLng
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 카메라가 멈춘 뒤 조회까지 두는 여유.
 *
 * 팬·줌을 연타하면 idle 이 연달아 들어오는데, 중간 범위마다 서버를 때릴 이유가 없다.
 * 손을 뗀 직후의 체감 지연으로 느껴지지 않을 만큼만 둔다.
 */
private const val BOUNDS_DEBOUNCE_MS = 400L

/**
 * 같은 범위 재조회 주기 — 방은 다른 사람의 생성·합류·나가기로 계속 바뀐다.
 *
 * **이 주기는 화면이 보이는 동안에만 돈다**([ExploreViewModel.onScreenStart]). 탐색은 하단탭이라
 * 다른 화면이 위에 올라와도 ViewModel 이 백스택에 살아 있어서, 게이트가 없으면 21 매칭 대기·채팅에
 * 머무는 내내 4초마다 범위 조회가 나갔다(QA D-1: 21 체류 135초에 34회).
 */
private const val NEARBY_POLL_INTERVAL_MS = 4_000L

/**
 * 몇 번 연속으로 갱신에 실패하면 「최신 정보를 받지 못했어요」를 띄우는가 ([staleAfterPollFailures]).
 *
 * **3회 = 12초**([NEARBY_POLL_INTERVAL_MS] × 3). 1회로 두면 터널·엘리베이터처럼 금방 복구되는
 * 끊김마다 경고가 깜빡이고, 그러면 사용자가 곧 무시하게 된다. 반대로 상한이 **아예 없으면**
 * (고치기 전의 동작) 실패가 조용히 버려져 낡은 마커가 **영구히** 남고, 사용자는 이미 사라진 방의
 * 「합류」를 눌러 20 에서야 「사라진 방이에요」를 본다.
 *
 * 12초는 「탭했을 때 이미 틀린 목록일 수 있다」를 의심하기 시작할 만한 시간이고(방이 차고 비는 주기가
 * 분 단위다), 지하철 역 사이 터널(보통 1~2분)보다는 짧아 **진짜 끊김은 반드시 드러난다.**
 */
internal const val STALE_NOTICE_FAILURES = 3

class ExploreViewModel(private val repository: RideRepository) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState

        /**
         * 그릴 목록 하나. [stale] 은 「보여 주고는 있지만 갱신이 연속 실패했다」는 뜻이다
         * ([STALE_NOTICE_FAILURES]) — 목록을 **치우지 않고** 화면이 비차단 표시만 올린다.
         * 조회가 한 번이라도 성공하면 그 결과로 교체되므로 자동으로 false 로 돌아온다.
         */
        data class Success(val parties: List<Ride>, val stale: Boolean = false) : UiState
        data class Error(val message: String) : UiState
    }

    /**
     * 조회 요청 1건.
     *
     * [seq] 는 「같은 범위를 다시 읽어라」(재시도)를 구분하기 위한 것 —
     * StateFlow 는 같은 값을 다시 방출하지 않아 seq 없이는 재시도가 씹힌다.
     */
    private data class LoadRequest(val bounds: MapBounds, val manual: Boolean, val seq: Int)

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val request = MutableStateFlow<LoadRequest?>(null)
    private var seq = 0

    /**
     * 화면이 지금 **보이는가** — 조회·폴링의 유일한 스위치다([nearbyPollStep]).
     *
     * 이 ViewModel 은 탐색의 `NavBackStackEntry` 에 매여 있어 다른 화면이 위에 올라와도 살아 있고,
     * `viewModelScope` 는 화면이 보이는지 보지 않는다. 그래서 신호 없이는 21 매칭 대기·채팅에 머무는
     * 동안에도 범위 조회가 계속 나간다 — 「평상시 네트워크 0」을 그 루프가 상쇄했다(QA D-1).
     *
     * 플래그가 아니라 [MutableStateFlow] 인 이유는 **요청과 같은 흐름에 묶기** 위해서다
     * ([observeRequests]): 가려지는 순간 그 범위의 폴링이 함께 취소되고, 다시 보이면 같은 요청으로
     * 패스가 새로 시작돼 **즉시 1회** 조회된다. 21 매칭 대기가 `realtimeAlive` 로 폴백을 켜고 끄는 것과
     * 같은 모양이다 — 판정을 순수 함수 한 곳에 모으고 루프는 그 결과만 따른다.
     */
    private val visible = MutableStateFlow(false)

    /**
     * **보이는 상태에서 조회를 시작한** 마지막 요청의 [LoadRequest.seq].
     *
     * 같은 seq 로 패스가 다시 들어왔다면 조회 범위가 바뀐 게 아니라 **화면이 다시 보이기 시작한 것**이다.
     * 그때는 디바운스 없이 곧바로 읽는다([nearbyPollStep]) — 돌아왔는데 주기만큼 옛 목록이 남아 있으면
     * 이미 사라진 방의 「합류」를 누르게 된다.
     */
    private var shownSeq = 0

    init { observeRequests() }

    /**
     * 화면이 보이기 시작했다 — **즉시 1회 조회**하고 주기 갱신을 재개한다.
     *
     * 탭 복귀·앱 복귀·위에 있던 화면의 pop 이 모두 이 길로 온다(`LifecycleStartEffect`).
     * 숨어 있던 동안 보던 목록은 그대로 두고 그 위에 새 결과를 덮으므로 로딩으로 깜빡이지 않는다([load]).
     * 첫 조회가 실패해 에러 화면이던 경우도 여기서 함께 복구된다(목록이 없으면 즉시 조회 대상이다).
     */
    fun onScreenStart() {
        visible.value = true
    }

    /**
     * 화면이 가려졌다(탭 이동·다른 화면 진입·앱 백그라운드) — 주기 조회를 멈춘다.
     *
     * 들고 있던 목록·지도 카메라는 건드리지 않는다. 돌아올 때 그 목록을 그대로 보여주고
     * 새로 읽은 결과로 덮는 게 맞기 때문이다.
     */
    fun onScreenStop() {
        visible.value = false
    }

    /**
     * 지도에 보이는 영역이 바뀌었다(카메라 idle). 첫 진입 시에는 기준점 범위로 한 번 호출한다.
     *
     * 지도 마커와 시트 목록은 **여기서 받아온 하나의 목록**에서 나온다 — 화면 밖 방이
     * 리스트에만 남아 있는 상태를 만들지 않는다.
     */
    fun onVisibleBoundsChange(bounds: MapBounds) {
        if (request.value?.bounds == bounds) return // idle 이 같은 범위로 다시 와도 재조회하지 않는다
        request.value = LoadRequest(bounds, manual = false, seq = ++seq)
    }

    /**
     * 지도가 범위를 알려주기 전의 **기준 범위** — 아직 어떤 범위도 받지 못했을 때만 쓴다.
     *
     * Route 의 진입 이펙트는 탭을 다녀오면 **다시 실행된다**(컴포지션이 새로 만들어진다). 그때도 기준
     * 범위를 밀어 넣으면 ① 복원된 카메라와 **다른 동네의 목록**이 잠깐 보이고(지도는 보던 자리에 있는데
     * 리스트·마커만 기준점 주변으로 바뀐다) ② 복귀 즉시 조회가 그 엉뚱한 범위로 가 버린다.
     * 그래서 「첫 범위」라는 뜻을 여기서 지킨다 — 복귀는 [onScreenStart] 가 마지막 범위로 처리한다.
     */
    fun onInitialBounds(bounds: MapBounds) {
        if (request.value != null) return
        onVisibleBoundsChange(bounds)
    }

    /** 에러 화면의 「다시 시도」 — 마지막으로 본 범위를 그대로 다시 읽는다. */
    fun refresh() {
        val current = request.value ?: return // 아직 범위조차 못 받은 상태면 재시도할 대상이 없다
        request.value = current.copy(manual = true, seq = ++seq)
    }

    /**
     * 요청 하나를 조회 + 주기 갱신 루프로 처리한다 — **보이는 동안만.**
     *
     * 범위([request])와 가시성([visible])을 하나의 흐름으로 합치는 것이 이 함수의 핵심이다.
     * `collectLatest` 가 둘 중 **어느 쪽이 바뀌어도** 앞 패스를 취소하므로 세 가지가 한꺼번에 성립한다.
     *
     * | 바뀐 것 | 일어나는 일 |
     * |---|---|
     * | 새 범위(카메라 idle) | 앞 범위의 조회·폴링이 취소되고 새 범위로 다시 시작 — 낡은 결과가 남지 않는다 |
     * | 가려짐 | 진행 중인 조회와 폴링이 **함께 죽는다**(다음 신호까지 아무것도 하지 않는다) |
     * | 다시 보임 | 같은 요청으로 패스가 새로 시작 → **즉시 1회 조회** 후 주기 재개 |
     *
     * 가려진 동안에는 `delay` 조차 돌지 않는다 — 깨어나 아무 일도 하지 않는 주기 자체가 없어진다.
     */
    private fun observeRequests() {
        viewModelScope.launch {
            combine(request.filterNotNull(), visible, ::Pair).collectLatest { (req, shown) ->
                val step = nearbyPollStep(
                    visible = shown,
                    manual = req.manual,
                    hasParties = hasParties(),
                    requestSeq = req.seq,
                    shownSeq = shownSeq,
                )
                if (step == NearbyPollStep.STOP) return@collectLatest
                // 디바운스 **전에** 적어 둔다 — 기다리는 동안 가려졌다 돌아오면 그때는 복귀로 보고
                // 또 기다리지 않게 한다(카메라는 이미 멈춰 있다).
                shownSeq = req.seq
                if (step == NearbyPollStep.DEBOUNCE_THEN_QUERY) delay(BOUNDS_DEBOUNCE_MS)
                load(req.bounds)
                // 연속 실패 횟수는 **패스마다 0 에서 시작한다** — 범위가 바뀌거나 화면이 다시 보이면
                // 새 패스이고, 그때는 직전 범위에서의 실패를 이어 셈할 이유가 없다.
                var failures = 0
                while (isActive) {
                    delay(NEARBY_POLL_INTERVAL_MS)
                    val parties = runCatching { req.bounds.query() }.getOrNull()
                    if (parties == null) {
                        // 실패해도 보던 목록은 그대로 둔다(지도를 에러 화면으로 바꾸지 않는다).
                        // 다만 **버리지 않고 세어 둔다** — 상한이 없던 동안 낡은 마커가 영구히 남았다.
                        failures++
                        if (staleAfterPollFailures(failures)) markStale()
                        continue
                    }
                    failures = 0
                    _uiState.value = UiState.Success(parties)
                }
            }
        }
    }

    /**
     * 들고 있는 목록은 그대로 두고 「낡음」만 켠다 — 화면이 그 사실을 비차단으로 알린다.
     *
     * 목록이 없는 상태(Loading·Error)에서는 아무것도 하지 않는다: 보여 줄 게 없으면 「낡았다」가
     * 아니라 첫 조회 실패이고, 그쪽은 [load] 가 에러 화면으로 다룬다.
     */
    private fun markStale() {
        val current = _uiState.value
        if (current is UiState.Success && !current.stale) {
            _uiState.value = current.copy(stale = true)
        }
    }

    private suspend fun load(bounds: MapBounds) {
        val hadParties = hasParties()
        // 이미 목록이 있으면 Loading 으로 덮지 않는다. 지도가 화면에서 사라졌다 돌아오면
        // 카메라가 초기 위치로 되돌아가고, 그 idle 이 또 조회를 부르는 되먹임이 생긴다.
        if (!hadParties) _uiState.value = UiState.Loading
        _uiState.value = try {
            UiState.Success(bounds.query())
        } catch (e: CancellationException) {
            // 범위가 바뀌었거나 **화면이 가려져** 취소된 것 — 실패가 아니므로 에러 화면으로 바꾸지 않는다.
            // 가시성 게이트가 생긴 뒤로는 이 길을 자주 지난다(첫 조회 중에 탭을 옮기면 매번).
            throw e
        } catch (e: Exception) {
            // 갱신 실패로 지도를 통째로 에러 화면으로 바꾸지 않는다(21 대기 화면과 같은 판단) —
            // 보던 목록을 남기고 다음 주기에 다시 읽는다. 처음부터 실패한 경우만 에러 화면.
            // 이 실패가 계속되면 주기 루프가 연속 횟수를 세어 「낡음」을 알린다([markStale]) —
            // 목록을 남기는 것과 **아무 말도 안 하는 것**은 다르다.
            if (hadParties) return
            UiState.Error("동승 목록을 불러오지 못했어요")
        }
    }

    // 인자 순서(남서 위도 → 남서 경도 → 북동 위도 → 북동 경도)를 한 곳에서만 쓴다.
    // 뒤바뀌면 서버가 오류 대신 빈 목록을 돌려주므로 호출부마다 풀어 쓰면 눈에 띄지 않는 버그가 된다.
    // 가시 영역보다 넓게 묻는다 — 경계에 걸친 방이 카메라가 조금 움직일 때마다 빠졌다 들어오며
    // 마커가 깜빡이는 것을 막는다([MapBounds.expanded] 설명 참고).
    private suspend fun MapBounds.query(): List<Ride> = expanded().let { area ->
        repository.getPartiesWithin(area.swLat, area.swLng, area.neLat, area.neLng)
    }

    private fun hasParties() = _uiState.value is UiState.Success

    companion object {
        fun factory(repository: RideRepository) = viewModelFactory {
            initializer { ExploreViewModel(repository) }
        }
    }
}

// 17–19 합승 탐색 — 서버 목록 연동 진입점
@Composable
fun ExploreRoute(
    repository: RideRepository,
    /** 지금 진행 중인 내 방(ActivePartyViewModel.resolve 결과). null 이면 상단 배너를 그리지 않는다 */
    activeRide: Ride? = null,
    onJoinParty: (Ride) -> Unit = {},
    onOngoingRideClick: () -> Unit = {},
    onCreateRoomClick: () -> Unit = {},
    onTabSelect: (MoyeotaTab) -> Unit = {},
) {
    val viewModel: ExploreViewModel = viewModel(factory = ExploreViewModel.factory(repository))
    val state by viewModel.uiState.collectAsState()

    // 지도가 화면의 본체라 탭 진입 시 권한을 요청한다.
    // 목록 로딩·에러 분기 **밖**에서 잡아야 조회가 실패해도 권한 흐름이 죽지 않는다.
    val myLocation = rememberMyLocationState()
    // 좌표·오차·방향을 한 덩어리로 넘긴다 — 지도가 파란 점과 오차 원을 같은 fix 로 그려야 한다
    val myFix = remember(myLocation.coordinates) {
        myLocation.coordinates?.let {
            MyLocationFix(
                position = LatLng(it.latitude, it.longitude),
                accuracyMeters = it.accuracyMeters,
                bearingDegrees = it.bearingDegrees,
            )
        }
    }

    // 화면이 보이는 동안만 조회·폴링한다 — 탭을 옮기거나 다른 화면이 위에 올라오거나 앱이 백그라운드로
    // 가면 즉시 멈추고, 돌아오면 그 자리에서 1회 조회한 뒤 주기를 재개한다 (24 채팅방과 같은 배치).
    //
    // RESUMED 가 아니라 STARTED 를 쓴다: ① NavHost 는 **전환 애니메이션이 끝난 뒤에야** 엔트리를
    // RESUMED 로 올려서, RESUMED 로 묶으면 탭을 열고 첫 조회까지 그만큼 더 기다린다(돌아왔는데 옛 목록을
    // 보는 시간이 늘어난다) ② 백스택에 가린 엔트리는 CREATED 까지 내려가므로 「다른 화면이 위에 올라옴」도
    // STARTED 로 똑같이 잡히고, 앱 백그라운드(ON_STOP)도 함께 잡힌다.
    LifecycleStartEffect(viewModel) {
        viewModel.onScreenStart()
        onStopOrDispose { viewModel.onScreenStop() }
    }

    // 첫 조회는 지도를 기다리지 않고 기준 범위로 먼저 쏜다.
    // 지도는 Success 상태에서만 붙으므로(로딩 화면에는 지도가 없다) 지도의 첫 idle 을
    // 기다리면 Loading 에서 영영 못 나온다. 권한을 거부해 지도가 아예 없는 경로도 같다.
    // 실좌표가 뒤늦게 잡히면 지도 카메라가 그리로 옮겨가며 idle → 실제 범위로 다시 조회된다.
    //
    // 탭을 다녀오면 이 이펙트가 **다시** 실행되지만, 그때 쓸 범위는 기준점이 아니라
    // 보던 카메라다 — [ExploreViewModel.onInitialBounds] 가 이미 범위를 받은 뒤의 호출을 무시한다.
    LaunchedEffect(Unit) {
        viewModel.onInitialBounds(defaultExploreBounds(myFix?.position))
    }

    // 로딩·에러에서도 하단탭은 남긴다 — 조회 실패로 탭 이동이 막히면 안 된다 (QA F-1)
    when (val current = state) {
        ExploreViewModel.UiState.Loading -> TabStateScaffold(MoyeotaTab.EXPLORE, onTabSelect) {
            LoadingBox()
        }
        is ExploreViewModel.UiState.Error -> TabStateScaffold(MoyeotaTab.EXPLORE, onTabSelect) {
            ErrorBox(message = current.message, onRetry = viewModel::refresh)
        }
        is ExploreViewModel.UiState.Success -> {
            // 내가 이미 참여 중인 방은 목록·마커에서 뺀다([exploreList]).
            //
            // **ViewModel 이 아니라 여기서** 거르는 이유:
            // ① [activeRide] 는 `ActivePartyViewModel`(NavHost 바깥, 앱 전체가 공유하는 홀더)이
            //    들고 있는 값이고 이 ViewModel 은 그걸 모른다. 넘겨주려면 탐색 ViewModel 에 두 번째
            //    데이터 출처가 생기고, 그 출처가 바뀔 때마다 상태를 다시 만들어야 한다.
            // ② `UiState.Success(parties, stale)` 는 **서버가 말한 것**이어야 한다. 거기서 걸러
            //    버리면 `stale`(갱신 연속 실패 표시)과 목록이 다른 뜻을 갖게 된다 — 「서버 목록은
            //    낡지 않았는데 내가 뺀 탓에 비었다」를 구분할 수 없고, `hasParties` 같은 폴링 판정이
            //    표시용 목록을 보게 된다.
            // 거르는 층이 화면 쪽이라도 **마커·카드·헤더 N 은 어긋나지 않는다** — 셋 다
            // [ExploreScreen] 의 `parties` **한 파라미터**에서 나온다(지도는 `rides = parties`).
            val list = remember(current.parties, activeRide?.id) {
                exploreList(current.parties, activeRide?.id)
            }
            ExploreScreen(
                parties = list.parties,
                myPartyHidden = list.myPartyHidden,
                stale = current.stale,
                activeRide = activeRide,
                locationGranted = myLocation.isGranted,
                myLocation = myFix,
                onJoinParty = onJoinParty,
                onOngoingRideClick = onOngoingRideClick,
                onCreateRoomClick = onCreateRoomClick,
                onRequestLocationPermission = myLocation.onRequestPermission,
                onVisibleBoundsChange = viewModel::onVisibleBoundsChange,
                onTabSelect = onTabSelect,
            )
        }
    }
}

/**
 * 17 이 실제로 그릴 목록 — 서버 목록에서 **내가 이미 참여 중인 방**을 뺀 결과.
 *
 * @param parties 서버가 지도 범위로 돌려준 목록(`status = ACTIVE` 인 방만 온다)
 * @param myPartyHidden 이 범위의 목록에서 **내 방을 실제로 빼냈는가**. 빈 목록 문구가 이 값으로
 *   갈린다 — 내 방 하나뿐인 동네에서 「이 근처엔 동승이 없어요」는 거짓말이 된다
 */
internal data class ExploreList(
    val parties: List<Ride>,
    val myPartyHidden: Boolean,
)

/**
 * 내가 참여 중인 방을 17 목록·마커에서 **뺀다.**
 *
 * **왜 빼는가.** 서버 목록은 내 방을 빼지 않아서, 내가 속한 방이 남의 방과 **똑같은 「합류」 카드**로
 * 섞여 보였다. 그 카드는 거짓말을 한다 — 「합류」 버튼이 활성이고 인원 배지는 내가 들어가기 **전**의
 * 수처럼 읽힌다. 눌러서 20 합류 확인에 가면 `joinGate` 가 `ALREADY_JOINED` 로 받아 「이미 참여
 * 중인 방이에요」 + 「방으로 돌아가기」를 주므로 막힌 길은 아니지만, **같은 화면 맨 위에 진행 중 방
 * 배너가 이미 있다**(`ExploreScreen` 의 `ActiveRideBanner` → `onOngoingRideClick`): 한 번 눌러
 * 21/25/26 으로 바로 간다. 20 을 거치는 카드는 탭이 한 번 더 들고 방 상세를 한 번 더 읽는 **더 긴
 * 우회로**이며, 그 사이 화면은 내가 합류할 수 있는 방처럼 보인다. 돌아가는 길이 이미 더 짧게 있으니
 * 그 카드는 소음이다.
 *
 * **20 의 `ALREADY_JOINED` 분기는 그대로 남는다** — 이 필터는 17 의 **표시**만 바꾼다. 목록이
 * 낡아 내 방이 아직 섞여 있는 순간, 알림으로 20 에 바로 들어오는 경우, 그리고 `activeRide` 홀더가
 * 아직 비어 있는 순간(앱 시작 직후 첫 `resolve` 전)이 모두 그 분기로 간다.
 *
 * **판정은 `activeRide` 의 id 하나로 한다.** `ride.members.any { it.isMe }` 는 이 데이터로는
 * **영원히 false** 다 — 목록 응답에는 멤버 식별자가 없어 매퍼가 자리 표시용 멤버를 만들고
 * (`PartyMappers.placeholderMembers`) 그 멤버에는 `publicId` 가 없어 [com.moyeota.domain.model.User.isMe] 가 늘 false 다.
 * 즉 목록 데이터만으로는 「내가 그 방에 있는가」를 알 수 없고, 알고 있는 쪽은 방 상세를 읽어 둔
 * `ActivePartyViewModel` 뿐이다.
 *
 * id 비교는 **Long 으로 먼저** 본다(`needsDetailAfterRemember` 와 같은 이유): 두 값은 서로 다른
 * 응답에서 왔고(목록 `partyId`, 상세 `id`) 문자열 모양이 어긋날 수 있다. 숫자로 안 읽히는 id 는
 * 문자열로 비교한다 — Preview 의 예시 방(`"ride-1"`)이 그 길로 간다.
 *
 * [activeRide] 의 단계는 보지 않는다. 끝난 방(COMPLETED·CANCELED)은 `status = ACTIVE` 목록에
 * 아예 오지 않아 id 가 걸릴 일이 없고, 「어느 단계부터 거른다」는 조건을 두면 그 조건이 배너를
 * 그리는 조건(null 이 아니면 그린다)과 갈릴 수 있다.
 */
internal fun exploreList(parties: List<Ride>, myPartyId: String?): ExploreList {
    if (myPartyId.isNullOrBlank()) return ExploreList(parties, myPartyHidden = false)
    val remaining = parties.filterNot { samePartyId(it.id, myPartyId) }
    return ExploreList(
        parties = remaining,
        myPartyHidden = remaining.size != parties.size,
    )
}

/** 같은 방인가. 숫자로 읽히면 숫자로, 아니면 문자열로 본다([exploreList] KDoc). */
private fun samePartyId(a: String, b: String): Boolean {
    val left = a.toLongOrNull()
    val right = b.toLongOrNull()
    return if (left != null || right != null) left == right else a == b
}

/**
 * 수집 패스 한 번이 할 일 — [nearbyPollStep] 의 판정 결과.
 */
internal enum class NearbyPollStep {
    /** 아무것도 하지 않고 다음 신호를 기다린다(화면이 보이지 않는다) */
    STOP,

    /** 지금 바로 조회하고 주기 갱신을 시작한다 */
    QUERY_NOW,

    /** 팬·줌 중간 범위다 — 잠깐 기다린 뒤 조회한다 */
    DEBOUNCE_THEN_QUERY,
}

/**
 * 탐색 목록 조회를 **지금 할지, 기다릴지, 아예 하지 않을지.**
 *
 * 폴링을 화면 가시성에 묶는 판정을 이 한 곳에 모은다(21 매칭 대기의 `shouldPollFallback` 과 같은 자리).
 * 위에서부터 읽는다 — 앞의 조건이 뒤를 가린다.
 *
 * | 판정 | 조건 | 이유 |
 * |---|---|---|
 * | [NearbyPollStep.STOP] | [visible] 이 false | **이 함수의 존재 이유.** 보이지 않는 화면이 서버를 부를 이유가 없다 |
 * | [NearbyPollStep.QUERY_NOW] | [manual] (「다시 시도」) | 사용자가 방금 누른 것이다. 기다리면 버튼이 먹지 않은 것처럼 보인다 |
 * | [NearbyPollStep.QUERY_NOW] | [hasParties] 가 false | 첫 조회이거나 에러 화면이다 — 보여줄 게 없으니 늦출 이득이 없다 |
 * | [NearbyPollStep.QUERY_NOW] | [requestSeq] == [shownSeq] | **탭 복귀**(범위는 그대로인데 패스가 다시 들어왔다). 숨어 있던 동안의 변화를 주기만큼 미루면 이미 사라진 방에 합류를 누르게 된다 |
 * | [NearbyPollStep.DEBOUNCE_THEN_QUERY] | 그 밖(새 범위 + 목록 있음) | 팬·줌을 연타하면 idle 이 연달아 온다. 중간 범위마다 서버를 때리지 않는다 |
 *
 * 숨어 있는 동안 지도 카메라가 바뀌는 일은 없다(가려진 화면은 컴포지션에서 내려가 idle 이 오지 않는다).
 * 그래도 바뀐 범위가 들어와 있었다면 그 요청은 **새 seq** 라 복귀 때 디바운스 한 번 뒤에 그 범위로 조회된다 —
 * 어느 쪽이든 돌아온 뒤에는 보이는 범위로 다시 읽는다.
 *
 * @param visible 화면이 사용자에게 보이는가(`LifecycleStartEffect` 로 받는다)
 * @param manual 에러 화면의 「다시 시도」로 생긴 요청인가
 * @param hasParties 지금 화면에 그릴 목록을 들고 있는가
 * @param requestSeq 이번 패스가 처리할 요청의 순번
 * @param shownSeq 보이는 상태에서 마지막으로 조회를 시작한 요청의 순번
 */
internal fun nearbyPollStep(
    visible: Boolean,
    manual: Boolean,
    hasParties: Boolean,
    requestSeq: Int,
    shownSeq: Int,
): NearbyPollStep = when {
    !visible -> NearbyPollStep.STOP
    manual || !hasParties || requestSeq == shownSeq -> NearbyPollStep.QUERY_NOW
    else -> NearbyPollStep.DEBOUNCE_THEN_QUERY
}

/**
 * 갱신 실패를 **사용자에게 알릴 때인가** — [nearbyPollStep] 과 같은 자리의 판정이다(순수 함수 한 곳에
 * 모으고 루프는 결과만 따른다).
 *
 * 한 번의 실패는 알리지 않는다(지도 앱의 흔한 일시 끊김이다). 연속 [STALE_NOTICE_FAILURES] 회부터
 * 알린다 — 임계값의 근거는 그 상수에 적어 두었다. **성공 한 번이 카운터를 0 으로 돌린다**(호출부)
 * 이므로, 이 함수는 「지금까지 연속 몇 번 실패했는가」만 보면 된다.
 *
 * @param consecutiveFailures 마지막 성공 이후 연속 실패 횟수(성공하면 0 으로 돌아간다)
 */
internal fun staleAfterPollFailures(consecutiveFailures: Int): Boolean =
    consecutiveFailures >= STALE_NOTICE_FAILURES
