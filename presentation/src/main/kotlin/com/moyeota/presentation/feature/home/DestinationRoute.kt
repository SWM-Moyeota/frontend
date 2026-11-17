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
import com.moyeota.domain.model.FavoritePlace
import com.moyeota.domain.model.FavoritePlaceException
import com.moyeota.domain.model.FavoritePlacePolicy
import com.moyeota.domain.model.Place
import com.moyeota.domain.repository.PlaceRepository
import com.moyeota.presentation.core.location.FormLocationIntervalMs
import com.moyeota.presentation.core.location.UserCoordinates
import com.moyeota.presentation.core.location.rememberMyLocationState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// 실위치로 만든 출발지의 표기 이름. 좌표만 실값이고 이름은 고정 문구다.
// 16 이 이 문구를 보고 「아직 주소를 모르는 GPS 출발지」를 식별해 역지오코딩으로 실주소를 채운다
// (검색으로 고른 장소명은 사용자가 고른 정답이라 건드리지 않는다). 그래서 internal.
internal const val CurrentLocationName = "현재 위치"

/**
 * 즐겨찾기 요청이 **응답을 못 받았을 때**의 문구(`FavoritePlaceException.isNetwork`).
 *
 * 노쇼·확정 같은 액션은 이 구분을 일부러 두지 않는다 — 「응답을 못 받았다」와 「모르는 코드」의
 * 대응이 둘 다 재시도로 같기 때문이다(`NoShowMappers` KDoc). 즐겨찾기가 다르게 가는 이유는
 * **일반 문구가 재시도를 말하지 않기** 때문이다: 비행기모드인 사용자에게 「담지 못했어요」만
 * 띄우면 왜인지 모른 채 ★ 를 계속 누른다. 고칠 것이 있는 실패라 그것을 말해 준다.
 *
 * 문구는 합류 쪽과 같은 것을 쓴다(`JoinConfirmRoute` 의 `isNetwork` 분기) — 같은 사실에 앱이
 * 두 가지 말을 하지 않게 한다. 담기·빼기·홈의 지우기가 이 하나를 공유하는 이유도 같다:
 * 같은 비행기모드에서 화면마다 다른 말을 하면 사용자가 원인을 자기 조작에서 찾는다.
 */
internal const val FavoriteNetworkFailureMessage = "네트워크가 불안정해요. 연결을 확인하고 다시 시도해 주세요"

/** 사유를 가릴 수 없는 담기 실패. [DestinationViewModel.addFavorite] 의 두 catch 가 함께 쓴다. */
private const val AddFailedMessage = "자주 가는 곳에 담지 못했어요"

/** 사유를 가릴 수 없는 빼기 실패. [DestinationViewModel.removeFavorite] 의 두 catch 가 함께 쓴다. */
private const val RemoveFailedMessage = "자주 가는 곳에서 빼지 못했어요"

/** 기기 실좌표 → 출발지 Place. 좌표만 실값이고 이름·주소는 고정 문구다 */
private fun currentLocationPlace(coordinates: UserCoordinates) = Place(
    name = CurrentLocationName,
    roadName = "",
    latitude = coordinates.latitude,
    longitude = coordinates.longitude,
)

class DestinationViewModel(
    private val repository: PlaceRepository,
) : ViewModel() {

    data class UiState(
        // 활성 필드 — 검색어·검색 결과는 이 필드 것으로 해석된다
        val activeField: DestinationField = DestinationField.DESTINATION,
        val originQuery: String = "",
        val query: String = "", // 도착지 검색어
        val searchResults: List<Place> = emptyList(),
        val searchLoading: Boolean = false,
        val searchErrorMessage: String? = null,
        val favorites: List<FavoritePlace> = emptyList(),
        val favoritesLoading: Boolean = false,
        val favoritesErrorMessage: String? = null,
        val favoriteActionMessage: String? = null,
        /** 담기·빼기 요청이 날아간 동안 true. 단건 삭제가 멱등이 아니라서 연타를 막는다 */
        val favoriteActionLoading: Boolean = false,
        // null = 사용자가 고르지 않음 → 실위치를 쓴다
        val selectedOrigin: Place? = null,
        // 기기 GPS 로 받은 현재 위치. 권한 거부·미취득이면 null
        val currentLocation: Place? = null,
        val selectedPlace: Place? = null,
    ) {
        // 우선순위: 사용자가 고른 출발지 > 실위치. 둘 다 없으면 null — 고정 좌표로 채우지 않는다.
        // 방 생성(POST /matching/rooms)은 실제 출발 좌표가 필요하므로 15 가 「경로 확인하기」를 막고
        // 출발지 검색을 안내한다.
        val origin: Place? get() = selectedOrigin ?: currentLocation
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadFavorites()
    }

    fun loadFavorites() {
        viewModelScope.launch {
            _uiState.update { it.copy(favoritesLoading = true, favoritesErrorMessage = null) }
            try {
                // 즐겨찾기 주체는 Bearer 토큰이 정한다 — userId 를 넘기지 않는다 (22 보고서 §2)
                val places = repository.getFavoritePlaces()
                _uiState.update { it.copy(favoritesLoading = false, favorites = places.sortedBy { p -> p.sequence }) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(favoritesLoading = false, favoritesErrorMessage = "자주 가는 곳을 불러오지 못했어요")
                }
            }
        }
    }

    // 타이핑마다 호출하지 않도록 300ms 디바운스 후 검색한다. 활성 필드의 검색어를 갱신한다.
    fun onQueryChange(query: String) {
        _uiState.update {
            if (it.activeField == DestinationField.ORIGIN) {
                it.copy(originQuery = query, searchErrorMessage = null)
            } else {
                it.copy(query = query, searchErrorMessage = null)
            }
        }
        runSearch(query)
    }

    // 필드 전환 — 검색 결과는 필드마다 달라야 하므로 비우고 그 필드의 검색어로 다시 검색한다
    fun focusField(field: DestinationField) {
        if (_uiState.value.activeField == field) return
        val next = _uiState.value.copy(
            activeField = field,
            searchResults = emptyList(),
            searchLoading = false,
            searchErrorMessage = null,
        )
        _uiState.value = next
        runSearch(if (field == DestinationField.ORIGIN) next.originQuery else next.query)
    }

    private fun runSearch(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList(), searchLoading = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            _uiState.update { it.copy(searchLoading = true) }
            try {
                val results = repository.searchPlaces(query.trim())
                // 성공하면 **이전 실패 문구를 반드시 지운다.** 화면은 에러를 `when` 의 첫 가지로 두므로
                // (DestinationScreen), 남겨 두면 결과가 와도 그 문구에 가려 한 건도 안 보인다.
                _uiState.update {
                    it.copy(searchLoading = false, searchResults = results, searchErrorMessage = null)
                }
            } catch (e: CancellationException) {
                // **취소는 실패가 아니다.** 글자를 칠 때마다 위에서 `searchJob?.cancel()` 로 이전 요청을
                // 끊는데, 그 취소가 여기까지 올라온다. `catch (Exception)` 하나로 받으면 타이핑하는
                // 족족 「검색하지 못했어요」가 서고, 뒤따라 성공한 검색의 결과까지 그 문구에 가려진다.
                // 다시 던져야 구조적 동시성도 깨지지 않는다 — 상태는 새 요청이 곧 덮는다.
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(searchLoading = false, searchResults = emptyList(), searchErrorMessage = "장소를 검색하지 못했어요")
                }
            }
        }
    }

    // 기기 위치 갱신 — 사용자가 출발지를 직접 고른 뒤에는 origin 계산에서 밀려나므로 덮어쓰지 않는다
    fun setCurrentLocation(place: Place?) {
        if (_uiState.value.currentLocation == place) return
        _uiState.update { it.copy(currentLocation = place) }
    }

    // 검색 결과·자주 가는 곳 선택 — 활성 필드에 따라 출발지/도착지가 정해진다
    fun selectPlace(place: Place) {
        if (_uiState.value.activeField == DestinationField.ORIGIN) {
            searchJob?.cancel()
            val next = _uiState.value.copy(
                selectedOrigin = place,
                originQuery = place.name,
                favoriteActionMessage = null,
                // 출발지를 정했으면 자연스럽게 도착지 입력으로 넘어간다
                activeField = DestinationField.DESTINATION,
                searchResults = emptyList(),
                searchLoading = false,
                searchErrorMessage = null,
            )
            _uiState.value = next
            runSearch(next.query)
        } else {
            _uiState.update { it.copy(selectedPlace = place, favoriteActionMessage = null) }
        }
    }

    fun addFavorite(place: Place) {
        if (_uiState.value.favoriteActionLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(favoriteActionLoading = true) }
            try {
                repository.addFavoritePlace(place)
                _uiState.update { it.copy(favoriteActionMessage = "${place.name}을(를) 자주 가는 곳에 담았어요") }
                loadFavorites() // 등록 후 목록 재조회
            } catch (e: CancellationException) {
                // 취소는 실패가 아니다 — 화면을 떠난 것이라 문구를 세울 자리가 없다(removeFavorite 와 같은 규칙).
                throw e
            } catch (e: FavoritePlaceException) {
                _uiState.update { it.copy(favoriteActionMessage = addFailureMessage(e)) }
            } catch (e: Exception) {
                _uiState.update { it.copy(favoriteActionMessage = AddFailedMessage) }
            } finally {
                _uiState.update { it.copy(favoriteActionLoading = false) }
            }
        }
    }

    /**
     * 등록 실패 문구. 둘 다 409 라 **코드를 봐야 가려진다** — 상태만 보면 뭉개진다
     * (운영 OpenAPI 는 409 를 적어 두지 않았지만 실제 응답이 409 다 — 컨트롤러 `@ApiResponses` 누락).
     *
     * 두 사유의 **할 일이 반대**라서 가른다: 상한은 하나를 빼면 담기고, 중복은 할 일이 없다.
     */
    private fun addFailureMessage(e: FavoritePlaceException): String = when {
        e.isLimitExceeded ->
            "자주 가는 곳은 ${FavoritePlacePolicy.MAX_COUNT}개까지만 담을 수 있어요. 하나를 빼고 다시 담아 주세요"
        e.isDuplicated -> "이미 담아 둔 곳이에요"
        // 담기가 이 경계를 쓰게 되면서 비로소 닿는 경우다 — 그 전에는 raw IOException 이라 못 가렸다
        e.isNetwork -> FavoriteNetworkFailureMessage
        else -> AddFailedMessage
    }

    /** 빼기 실패 문구. [addFailureMessage] 와 대칭이다 — 같은 예외를 쓰지만 사유 구성이 다르다. */
    private fun removeFailureMessage(e: FavoritePlaceException): String = when {
        e.isNotFound -> "이미 지워졌거나 없는 곳이에요"
        e.isNetwork -> FavoriteNetworkFailureMessage
        else -> RemoveFailedMessage
    }

    /**
     * 담은 곳을 다시 뺀다(DELETE .../favorite-places/{placeId}).
     *
     * 잘못 담은 장소를 취소할 길이 이것뿐이다 — 등록 상한이 10개이고 같은 이름은 재등록도 막히므로
     * (409 `FAVORITE_PLACE_DUPLICATED`) 빼지 못하면 오타 난 이름이 10칸 중 하나를 영구히 차지한다.
     *
     * **404 를 성공으로 접지 않는다.** 서버가 「이미 지워졌다」·「없다」·「남의 것」을 같은 404 로
     * 숨기므로, 성공으로 접으면 남의 행을 지우려는 시도까지 성공으로 보인다. 대신 실패 문구를 띄우고
     * [loadFavorites] 로 목록을 다시 받아 ★ 상태를 서버와 맞춘다 — 그 404 는 화면이 들고 있던
     * 목록이 낡았다는 신호이기도 하다.
     */
    fun removeFavorite(id: Long) {
        if (_uiState.value.favoriteActionLoading) return
        val name = _uiState.value.favorites.firstOrNull { it.id == id }?.name
        viewModelScope.launch {
            _uiState.update { it.copy(favoriteActionLoading = true) }
            try {
                repository.deleteFavoritePlace(id)
                val label = name?.let { "${it}을(를) " }.orEmpty()
                _uiState.update { it.copy(favoriteActionMessage = "${label}자주 가는 곳에서 뺐어요") }
            } catch (e: CancellationException) {
                // 취소는 실패가 아니다 — 화면을 떠난 것이라 문구를 세울 자리가 없다(runSearch 와 같은 규칙).
                throw e
            } catch (e: FavoritePlaceException) {
                _uiState.update { it.copy(favoriteActionMessage = removeFailureMessage(e)) }
            } catch (e: Exception) {
                _uiState.update { it.copy(favoriteActionMessage = RemoveFailedMessage) }
            } finally {
                // 성공이든 실패든 목록을 다시 받는다 — 실패가 「목록이 낡았다」는 뜻일 수 있다
                loadFavorites()
                _uiState.update { it.copy(favoriteActionLoading = false) }
            }
        }
    }

    companion object {
        fun factory(repository: PlaceRepository) = viewModelFactory {
            initializer { DestinationViewModel(repository) }
        }
    }
}

// 15 목적지 — 장소 검색·즐겨찾기 서버 연동 진입점
@Composable
fun DestinationRoute(
    repository: PlaceRepository,
    initialQuery: String = "", // 14 홈에서 넘어온 검색어 (자주 가는 곳 · 최근 목적지 탭)
    onBack: () -> Unit = {},
    // (출발지, 도착지) — 출발지는 사용자가 고른 곳 또는 실위치. 둘 다 없으면 CTA 가 비활성이라 호출되지 않는다
    onConfirmRoute: (Place, Place) -> Unit = { _, _ -> },
) {
    val viewModel: DestinationViewModel = viewModel(factory = DestinationViewModel.factory(repository))
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank() && state.query.isBlank()) viewModel.onQueryChange(initialQuery)
    }

    // 여기서는 권한 다이얼로그를 띄우지 않는다 — 검색이 본체인 화면이라 진입하자마자 막아서면 방해가 된다.
    // 합승 탭(17)에서 이미 허용했으면 그 좌표가 그대로 출발지 기본값이 되고, 아니면 출발지가 비어
    // 사용자가 출발지를 검색해 채운다.
    // 지도가 없어 출발지 좌표 한 번이면 충분하다 — 지도용 1초 주기를 여기까지 끌고 오지 않는다
    val myLocation = rememberMyLocationState(
        autoRequestPermission = false,
        updateIntervalMs = FormLocationIntervalMs,
    )
    LaunchedEffect(myLocation.coordinates) {
        viewModel.setCurrentLocation(myLocation.coordinates?.let(::currentLocationPlace))
    }

    DestinationScreen(
        origin = state.origin?.name,
        originIsDefault = state.selectedOrigin == null,
        originLatitude = state.origin?.latitude,
        originLongitude = state.origin?.longitude,
        originQuery = state.originQuery,
        activeField = state.activeField,
        onFieldFocus = viewModel::focusField,
        query = state.query,
        onQueryChange = viewModel::onQueryChange,
        searchResults = state.searchResults,
        searchLoading = state.searchLoading,
        searchErrorMessage = state.searchErrorMessage,
        favoritePlaces = state.favorites,
        favoritesLoading = state.favoritesLoading,
        favoritesErrorMessage = state.favoritesErrorMessage,
        favoriteActionMessage = state.favoriteActionMessage,
        favoriteActionLoading = state.favoriteActionLoading,
        selectedPlace = state.selectedPlace,
        onPlaceSelect = viewModel::selectPlace,
        onAddFavorite = viewModel::addFavorite,
        onRemoveFavorite = viewModel::removeFavorite,
        onBack = onBack,
        onConfirmRoute = { destination -> state.origin?.let { onConfirmRoute(it, destination) } },
    )
}
