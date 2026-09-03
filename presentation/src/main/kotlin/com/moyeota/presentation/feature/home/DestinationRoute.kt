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
import com.moyeota.domain.model.Place
import com.moyeota.domain.repository.PlaceRepository
import com.moyeota.presentation.core.location.FormLocationIntervalMs
import com.moyeota.presentation.core.location.UserCoordinates
import com.moyeota.presentation.core.location.rememberMyLocationState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// 실위치를 못 받았을 때(권한 거부 · GPS 미취득)의 출발지 폴백.
// 방 생성(POST /matching/rooms)이 좌표를 필수로 받기 때문에 화면이 어떤 좌표든 갖고 있어야 한다.
val DemoOrigin = Place(
    name = "부산대학교 정문",
    roadName = "부산 금정구 부산대학로63번길 2",
    latitude = 35.2313,
    longitude = 129.0838,
)

// 실위치로 만든 출발지의 표기 이름. 좌표만 실값이고 이름은 고정 문구다.
// 16 이 이 문구를 보고 「아직 주소를 모르는 GPS 출발지」를 식별해 역지오코딩으로 실주소를 채운다
// (검색으로 고른 장소명은 사용자가 고른 정답이라 건드리지 않는다). 그래서 internal.
internal const val CurrentLocationName = "현재 위치"

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
        // null = 사용자가 고르지 않음 → 실위치, 그것도 없으면 DemoOrigin
        val selectedOrigin: Place? = null,
        // 기기 GPS 로 받은 현재 위치. 권한 거부·미취득이면 null
        val currentLocation: Place? = null,
        val selectedPlace: Place? = null,
    ) {
        // 우선순위: 사용자가 고른 출발지 > 실위치 > DemoOrigin 폴백
        val origin: Place get() = selectedOrigin ?: currentLocation ?: DemoOrigin
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
                _uiState.update { it.copy(searchLoading = false, searchResults = results) }
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
        viewModelScope.launch {
            try {
                repository.addFavoritePlace(place)
                _uiState.update { it.copy(favoriteActionMessage = "${place.name}을(를) 자주 가는 곳에 담았어요") }
                loadFavorites() // 등록 후 목록 재조회
            } catch (e: Exception) {
                // 서버 400: 이름 중복 · 10개 초과
                _uiState.update { it.copy(favoriteActionMessage = "자주 가는 곳에 담지 못했어요 (중복이거나 10개를 넘었을 수 있어요)") }
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
    // (출발지, 도착지) — 출발지를 고르지 않았으면 DemoOrigin 이 넘어간다
    onConfirmRoute: (Place, Place) -> Unit = { _, _ -> },
) {
    val viewModel: DestinationViewModel = viewModel(factory = DestinationViewModel.factory(repository))
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank() && state.query.isBlank()) viewModel.onQueryChange(initialQuery)
    }

    // 여기서는 권한 다이얼로그를 띄우지 않는다 — 검색이 본체인 화면이라 진입하자마자 막아서면 방해가 된다.
    // 합승 탭(17)에서 이미 허용했으면 그 좌표가 그대로 출발지 기본값이 되고, 아니면 DemoOrigin 으로 남는다.
    // 지도가 없어 출발지 좌표 한 번이면 충분하다 — 지도용 1초 주기를 여기까지 끌고 오지 않는다
    val myLocation = rememberMyLocationState(
        autoRequestPermission = false,
        updateIntervalMs = FormLocationIntervalMs,
    )
    LaunchedEffect(myLocation.coordinates) {
        viewModel.setCurrentLocation(myLocation.coordinates?.let(::currentLocationPlace))
    }

    DestinationScreen(
        origin = state.origin.name,
        originIsDefault = state.selectedOrigin == null,
        originLatitude = state.origin.latitude,
        originLongitude = state.origin.longitude,
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
        selectedPlace = state.selectedPlace,
        onPlaceSelect = viewModel::selectPlace,
        onAddFavorite = viewModel::addFavorite,
        onBack = onBack,
        onConfirmRoute = { destination -> onConfirmRoute(state.origin, destination) },
    )
}
