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
import com.moyeota.domain.session.UserSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// 위치 권한/GPS 미연동 — 출발지는 부산대 정문 고정.
// 방 생성(POST /matching/rooms)이 좌표를 필수로 받기 때문에 화면이 어딘가에서 좌표를 만들어야 한다.
// 실제 현재 위치가 붙으면 이 상수만 교체하면 된다.
val DemoOrigin = Place(
    name = "부산대학교 정문",
    roadName = "부산 금정구 부산대학로63번길 2",
    latitude = 35.2313,
    longitude = 129.0838,
)

class DestinationViewModel(
    private val repository: PlaceRepository,
    private val userSession: UserSession,
) : ViewModel() {

    data class UiState(
        val query: String = "",
        val searchResults: List<Place> = emptyList(),
        val searchLoading: Boolean = false,
        val searchErrorMessage: String? = null,
        val favorites: List<FavoritePlace> = emptyList(),
        val favoritesLoading: Boolean = false,
        val favoritesErrorMessage: String? = null,
        val favoriteActionMessage: String? = null,
        val selectedPlace: Place? = null,
    )

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
                val places = repository.getFavoritePlaces(userSession.currentUserId)
                _uiState.update { it.copy(favoritesLoading = false, favorites = places.sortedBy { p -> p.sequence }) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(favoritesLoading = false, favoritesErrorMessage = "자주 가는 곳을 불러오지 못했어요")
                }
            }
        }
    }

    // 타이핑마다 호출하지 않도록 300ms 디바운스 후 검색한다.
    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query, searchErrorMessage = null) }
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

    fun selectPlace(place: Place) {
        _uiState.update { it.copy(selectedPlace = place, favoriteActionMessage = null) }
    }

    fun addFavorite(place: Place) {
        viewModelScope.launch {
            try {
                repository.addFavoritePlace(userSession.currentUserId, place)
                _uiState.update { it.copy(favoriteActionMessage = "${place.name}을(를) 자주 가는 곳에 담았어요") }
                loadFavorites() // 등록 후 목록 재조회
            } catch (e: Exception) {
                // 서버 400: 이름 중복 · 10개 초과
                _uiState.update { it.copy(favoriteActionMessage = "자주 가는 곳에 담지 못했어요 (중복이거나 10개를 넘었을 수 있어요)") }
            }
        }
    }

    companion object {
        fun factory(repository: PlaceRepository, userSession: UserSession) = viewModelFactory {
            initializer { DestinationViewModel(repository, userSession) }
        }
    }
}

// 15 목적지 — 장소 검색·즐겨찾기 서버 연동 진입점
@Composable
fun DestinationRoute(
    repository: PlaceRepository,
    userSession: UserSession,
    initialQuery: String = "", // 14 홈에서 넘어온 검색어 (자주 가는 곳 · 최근 목적지 탭)
    onBack: () -> Unit = {},
    onConfirmRoute: (Place) -> Unit = {},
) {
    val viewModel: DestinationViewModel = viewModel(factory = DestinationViewModel.factory(repository, userSession))
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank() && state.query.isBlank()) viewModel.onQueryChange(initialQuery)
    }

    DestinationScreen(
        origin = DemoOrigin.name,
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
        onConfirmRoute = onConfirmRoute,
    )
}
