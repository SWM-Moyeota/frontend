package com.moyeota.presentation.feature.explore

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
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

/** 같은 범위 재조회 주기 — 방은 다른 사람의 생성·합류·나가기로 계속 바뀐다 (21 대기 화면과 같은 간격) */
private const val NEARBY_POLL_INTERVAL_MS = 4_000L

class ExploreViewModel(private val repository: RideRepository) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data class Success(val parties: List<Ride>) : UiState
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

    init { observeRequests() }

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

    /** 에러 화면의 「다시 시도」 — 마지막으로 본 범위를 그대로 다시 읽는다. */
    fun refresh() {
        val current = request.value ?: return // 아직 범위조차 못 받은 상태면 재시도할 대상이 없다
        request.value = current.copy(manual = true, seq = ++seq)
    }

    // 요청 하나를 조회 + 주기 갱신 루프로 처리한다.
    // collectLatest 라 새 범위가 들어오면 이전 조회와 그 범위의 폴링이 함께 취소된다 —
    // 두 코루틴이 _uiState 를 경쟁적으로 덮어써 낡은 범위의 결과가 남는 일이 없다.
    private fun observeRequests() {
        viewModelScope.launch {
            request.filterNotNull().collectLatest { req ->
                // 첫 조회와 수동 재시도는 기다릴 이유가 없다. 팬·줌 중간 범위만 디바운스한다
                if (!req.manual && hasParties()) delay(BOUNDS_DEBOUNCE_MS)
                load(req.bounds)
                while (isActive) {
                    delay(NEARBY_POLL_INTERVAL_MS)
                    val parties = runCatching { req.bounds.query() }.getOrNull() ?: continue
                    _uiState.value = UiState.Success(parties)
                }
            }
        }
    }

    private suspend fun load(bounds: MapBounds) {
        val hadParties = hasParties()
        // 이미 목록이 있으면 Loading 으로 덮지 않는다. 지도가 화면에서 사라졌다 돌아오면
        // 카메라가 초기 위치로 되돌아가고, 그 idle 이 또 조회를 부르는 되먹임이 생긴다.
        if (!hadParties) _uiState.value = UiState.Loading
        _uiState.value = try {
            UiState.Success(bounds.query())
        } catch (e: Exception) {
            // 갱신 실패로 지도를 통째로 에러 화면으로 바꾸지 않는다(21 대기 화면과 같은 판단) —
            // 보던 목록을 남기고 다음 주기에 다시 읽는다. 처음부터 실패한 경우만 에러 화면.
            if (hadParties) return
            UiState.Error("합승 목록을 불러오지 못했어요")
        }
    }

    // 인자 순서(남서 위도 → 남서 경도 → 북동 위도 → 북동 경도)를 한 곳에서만 쓴다.
    // 뒤바뀌면 서버가 오류 대신 빈 목록을 돌려주므로 호출부마다 풀어 쓰면 눈에 띄지 않는 버그가 된다.
    private suspend fun MapBounds.query(): List<Ride> =
        repository.getPartiesWithin(swLat, swLng, neLat, neLng)

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

    // 첫 조회는 지도를 기다리지 않고 기준 범위로 먼저 쏜다.
    // 지도는 Success 상태에서만 붙으므로(로딩 화면에는 지도가 없다) 지도의 첫 idle 을
    // 기다리면 Loading 에서 영영 못 나온다. 권한을 거부해 지도가 아예 없는 경로도 같다.
    // 실좌표가 뒤늦게 잡히면 지도 카메라가 그리로 옮겨가며 idle → 실제 범위로 다시 조회된다.
    LaunchedEffect(Unit) {
        viewModel.onVisibleBoundsChange(defaultExploreBounds(myFix?.position))
    }

    // 로딩·에러에서도 하단탭은 남긴다 — 조회 실패로 탭 이동이 막히면 안 된다 (QA F-1)
    when (val current = state) {
        ExploreViewModel.UiState.Loading -> TabStateScaffold(MoyeotaTab.EXPLORE, onTabSelect) {
            LoadingBox()
        }
        is ExploreViewModel.UiState.Error -> TabStateScaffold(MoyeotaTab.EXPLORE, onTabSelect) {
            ErrorBox(message = current.message, onRetry = viewModel::refresh)
        }
        is ExploreViewModel.UiState.Success -> ExploreScreen(
            parties = current.parties,
            waitingCount = current.parties.sumOf { it.members.size },
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
