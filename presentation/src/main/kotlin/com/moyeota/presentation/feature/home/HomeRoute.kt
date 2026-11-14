package com.moyeota.presentation.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.moyeota.core.designsystem.component.MoyeotaTab
import com.moyeota.domain.diagnostics.Diagnostics
import com.moyeota.domain.diagnostics.FailureSite
import com.moyeota.domain.model.FavoritePlace
import com.moyeota.domain.model.FavoritePlaceException
import com.moyeota.domain.model.Ride
import com.moyeota.domain.repository.PlaceRepository
import com.moyeota.presentation.core.location.rememberMyLocationState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: PlaceRepository,
) : ViewModel() {

    /**
     * 즐겨찾기 편집 상태. 목록과 분리해 둔 이유: 목록은 진입마다 서버에서 다시 받는 값이고
     * 이쪽은 사용자가 화면에서 만든 값이다 — 한 덩어리로 묶으면 재조회가 선택을 밀어낸다.
     */
    data class FavoriteEditState(
        val editing: Boolean = false,
        /** 고른 [FavoritePlace.id] 들. 서버 placeId 라 세션이 바뀌면 의미가 없다(편집 모드를 닫으면 비운다) */
        val selectedIds: Set<Long> = emptySet(),
        /** 삭제 요청이 날아간 동안 true — 화면이 손잡이를 잠근다 */
        val deleting: Boolean = false,
        val errorMessage: String? = null,
        /**
         * **조회가 한 번이라도 성공했는가.** 「전체 삭제」를 그릴지의 유일한 근거다.
         *
         * 이게 없으면 조회 실패와 「등록한 곳이 없음」이 **화면에서 똑같아진다.** 그 상태로 편집을 열면
         * 앱이 「목록에 안 보이는 장소가 서버에 남아 있을 수 있어요」라며 전체 삭제를 권하는데,
         * 실제로는 서버에 멀쩡한 즐겨찾기가 있고 **통신이 돌아오는 순간 그게 전부 지워진다.**
         * 되돌릴 수 없다. 레거시 행 탈출구라는 명분은 「목록을 받아 왔고 그 결과가 비었다」일 때만 선다.
         */
        val loaded: Boolean = false,
        /** 조회가 실패해 목록을 못 받았다. 빈 목록과 **다르게** 그려야 한다 */
        val loadFailed: Boolean = false,
    )

    // 홈의 「즐겨찾기」 목록은 부가 정보라 실패해도 화면 전체를 막지 않는다. 다만 실패를 **빈 목록으로
    // 보이게 두지는 않는다** — 그 둘을 섞으면 파괴적 동작(전체 삭제)의 근거가 거짓이 된다.
    private val _favorites = MutableStateFlow<List<FavoritePlace>>(emptyList())
    val favorites: StateFlow<List<FavoritePlace>> = _favorites.asStateFlow()

    private val _editState = MutableStateFlow(FavoriteEditState())
    val editState: StateFlow<FavoriteEditState> = _editState.asStateFlow()

    fun refresh() {
        viewModelScope.launch { loadFavorites() }
    }

    /** 조회 실패 뒤 사용자가 「다시 시도」를 눌렀을 때. [refresh] 와 같은 일이지만 의도가 다르다. */
    fun retryFavorites() = refresh()

    private suspend fun loadFavorites() {
        // 즐겨찾기 주체는 Bearer 토큰이 정한다 — userId 를 넘기지 않는다 (22 보고서 §2)
        val result = runCatching { repository.getFavoritePlaces() }
            .map { places -> places.sortedBy { it.sequence } }
        // 실패했으면 **이미 보여 주던 목록을 비우지 않는다** — 지하철에서 한 번 끊겼다고 화면이
        // 「등록한 곳이 없어요」로 바뀌면 사용자는 자기 즐겨찾기가 사라진 줄 안다.
        result.getOrNull()?.let { _favorites.value = it }
        // 화면은 그대로 두고 **보고만** 한다. 지하철 끊김은 [Diagnostics] 가 걸러 내고,
        // 파싱 실패나 5xx 처럼 우리가 고쳐야 하는 것만 올라간다.
        result.exceptionOrNull()?.let { Diagnostics.report(FailureSite.FAVORITE_PLACES, it) }
        _editState.update {
            it.copy(loaded = it.loaded || result.isSuccess, loadFailed = result.isFailure)
        }
    }

    /**
     * 편집 모드를 열고 닫는다. 닫을 때 선택과 실패 문구를 함께 비운다 — 남겨 두면 다음에 열었을 때
     * 사용자가 고르지도 않은 곳이 선택된 채로 「선택한 N곳 삭제」가 활성된다.
     */
    fun toggleFavoriteEditing() {
        if (_editState.value.deleting) return
        // `loaded`·`loadFailed` 는 조회의 사실이라 편집을 여닫는다고 바뀌지 않는다 —
        // 통째로 새 객체를 만들면 `loaded` 가 false 로 돌아가 「전체 삭제」가 사라진다.
        _editState.update {
            it.copy(
                editing = !it.editing,
                selectedIds = emptySet(),
                errorMessage = null,
            )
        }
    }

    fun toggleFavoriteSelection(place: FavoritePlace) {
        _editState.update { state ->
            val next = if (place.id in state.selectedIds) {
                state.selectedIds - place.id
            } else {
                state.selectedIds + place.id
            }
            state.copy(selectedIds = next, errorMessage = null)
        }
    }

    /**
     * 고른 곳들을 **한 요청으로** 지운다(POST .../favorite-places/batch-delete).
     *
     * 단건 삭제를 선택 수만큼 돌리지 않는 이유: 서버가 전부-아니면-전무라 다건 경로는
     * 「일부만 지워진」 상태를 만들지 않는다. 단건을 N번 돌리면 중간 404 에서 앞 몇 건만 지워져
     * 화면과 서버가 어긋난다. 선택이 1개여도 같은 경로로 보낸다(서버가 204 로 받는다).
     *
     * 빈 선택은 호출하지 않는다 — 서버가 400 `INVALID_PLACE_IDS` 를 준다.
     */
    fun deleteSelectedFavorites() {
        val ids = _editState.value.selectedIds
        if (ids.isEmpty() || _editState.value.deleting) return
        viewModelScope.launch {
            _editState.update { it.copy(deleting = true, errorMessage = null) }
            runDelete { repository.deleteFavoritePlaces(ids.toList()) }
        }
    }

    /**
     * 서버 목록을 통째로 비운다(DELETE .../favorite-places/all).
     *
     * **목록이 비어 보일 때도 부른다.** placeId·좌표가 없는 레거시 행은 매퍼가 버려서 화면에
     * 안 나오는데 서버에서는 10칸 중 하나를 차지한다 — 고를 수 없으니 이 경로가 유일한 탈출구다.
     * 「이미 비어 있다」는 404 는 리포지토리가 삼키므로 그때도 실패 문구가 서지 않는다.
     */
    fun deleteAllFavorites() {
        if (_editState.value.deleting) return
        viewModelScope.launch {
            _editState.update { it.copy(deleting = true, errorMessage = null) }
            runDelete { repository.deleteAllFavoritePlaces() }
        }
    }

    /**
     * 삭제 한 번과 그 뒷정리. **성공이든 실패든 목록을 다시 받는다** — 서버가 전부-아니면-전무라
     * 실패했을 때 「일부는 지워졌겠지」로 낙관적 제거를 하면 어긋나고, 404 는 「이미 지워졌거나
     * 남의 것」이라 화면이 들고 있던 목록 자체가 낡았다는 뜻이다.
     *
     * 선택은 **404 일 때만** 비운다. 서버가 전부-아니면-전무라 네트워크 실패는 아무것도 지우지
     * 않았다는 뜻이고, 그때까지 체크를 날리면 사용자가 처음부터 다시 고른다. 반대로 404 는
     * 「그 id 가 이제 없다」라 그대로 두면 같은 404 를 반복한다.
     *
     * [CancellationException] 만 그대로 던진다. **취소는 실패가 아니라 화면을 떠난 것**이고,
     * `runCatching` 처럼 통째로 삼키면 그 뒤 [loadFavorites] 가 이미 취소된 스코프에서 돌아
     * 즐겨찾기를 빈 목록으로 덮어쓴다(같은 이유로 `runSearch` 도 취소를 다시 던진다).
     */
    private suspend fun runDelete(block: suspend () -> Unit) {
        val error = try {
            block()
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            e
        }
        loadFavorites()
        val notFound = (error as? FavoritePlaceException)?.isNotFound == true
        _editState.update {
            it.copy(
                // 성공하면 편집 모드를 닫고, 실패하면 열어 둔다(다시 고를 수 있어야 한다)
                editing = error != null,
                selectedIds = if (error == null || notFound) emptySet() else it.selectedIds,
                deleting = false,
                errorMessage = error?.let(::deleteFailureMessage),
            )
        }
    }

    /**
     * 404 를 성공으로 접지 않으므로 그 자리에 쓸 문구가 필요하다. 서버가 「없는 것」과 「남의 것」을
     * 같은 404 로 숨기니 둘을 구분하지 않고, 방금 목록을 다시 받았다는 것까지 알린다.
     */
    private fun deleteFailureMessage(error: Throwable): String = when {
        (error as? FavoritePlaceException)?.isNotFound == true ->
            "이미 지워졌거나 없는 곳이에요. 목록을 다시 불러왔어요"
        // 15 의 담기·빼기와 같은 문구를 쓴다 — 같은 비행기모드에서 화면마다 다른 말을 하지 않는다
        (error as? FavoritePlaceException)?.isNetwork == true -> FavoriteNetworkFailureMessage
        else -> "즐겨찾기를 지우지 못했어요"
    }

    companion object {
        fun factory(repository: PlaceRepository) = viewModelFactory {
            initializer { HomeViewModel(repository) }
        }
    }
}

// 14 홈 — 즐겨찾기 목록을 서버에서 채운다(domain FavoritePlace 그대로, sequence 순).
@Composable
fun HomeRoute(
    repository: PlaceRepository,
    /** 지금 진행 중인 내 방(ActivePartyViewModel.resolve 결과). 있으면 지도 위에 복귀 배너를 띄운다 */
    activeRide: Ride? = null,
    onSearchClick: () -> Unit = {},
    onActiveRideClick: () -> Unit = {},
    onPlaceQuery: (String) -> Unit = {},
    onTabSelect: (MoyeotaTab) -> Unit = {},
) {
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(repository))
    val favorites by viewModel.favorites.collectAsState()
    val editState by viewModel.editState.collectAsState()

    // 홈은 배경이 통째로 지도인 화면이라 내 위치가 있어야 지도가 쓸모 있다 — 합승 탭(17~19)과 같이
    // 권한을 요청한다. 거부하면 파란 점 없이 기본 카메라(부산)로 남고 화면은 그대로 동작한다.
    val myLocation = rememberMyLocationState()

    // 15 에서 즐겨찾기를 등록하고 돌아오면 반영돼야 해서 진입마다 재조회한다
    LaunchedEffect(Unit) { viewModel.refresh() }

    HomeScreen(
        activeRide = activeRide,
        myLocation = myLocation.coordinates,
        favoritePlaces = favorites,
        editingFavorites = editState.editing,
        selectedFavoriteIds = editState.selectedIds,
        deletingFavorites = editState.deleting,
        favoriteErrorMessage = editState.errorMessage,
        favoritesLoaded = editState.loaded,
        favoritesLoadFailed = editState.loadFailed,
        onRetryFavorites = viewModel::retryFavorites,
        onSearchClick = onSearchClick,
        onActiveRideClick = onActiveRideClick,
        // 도로명이 없는 장소가 있다 — 서버가 카카오의 road_address_name 을 그대로 흘리고,
        // 지번만 있는 건물·지하철역에서 그 값이 빈다(매퍼가 null 을 "" 로 떨군다).
        // 빈 값을 그대로 넘기면 **빈 검색어로 15 가 열려** 아무것도 안 뜬다. 이름으로 되돌린다.
        onFavoritePlaceClick = { onPlaceQuery(it.roadName.ifBlank { it.name }) },
        onEditFavoritesToggle = viewModel::toggleFavoriteEditing,
        onFavoriteSelectToggle = viewModel::toggleFavoriteSelection,
        onDeleteSelected = viewModel::deleteSelectedFavorites,
        onDeleteAll = viewModel::deleteAllFavorites,
        onTabSelect = onTabSelect,
    )
}
