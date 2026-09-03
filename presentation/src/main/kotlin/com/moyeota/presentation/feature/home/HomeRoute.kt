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
import com.moyeota.domain.repository.PlaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: PlaceRepository,
) : ViewModel() {

    // 홈의 「자주 가는 곳」은 부가 정보라 실패해도 화면 전체를 막지 않는다 — 빈 목록으로 떨어뜨린다.
    private val _favorites = MutableStateFlow<List<FavoritePlace>>(emptyList())
    val favorites: StateFlow<List<FavoritePlace>> = _favorites.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            // 즐겨찾기 주체는 Bearer 토큰이 정한다 — userId 를 넘기지 않는다 (22 보고서 §2)
            _favorites.value = runCatching { repository.getFavoritePlaces() }
                .map { places -> places.sortedBy { it.sequence }.map { FavoritePlace(it.name, it.roadName) } }
                .getOrDefault(emptyList())
        }
    }

    companion object {
        fun factory(repository: PlaceRepository) = viewModelFactory {
            initializer { HomeViewModel(repository) }
        }
    }
}

// 14 홈 — 자주 가는 곳만 서버에서 채운다(나머지는 아직 더미).
@Composable
fun HomeRoute(
    repository: PlaceRepository,
    // 이름은 NavHost 의 공용 홀더(UserProfileViewModel)가 세션당 한 번 받아 내려보낸다
    // — 홈 진입마다 다시 조회하지 않는다.
    userName: String? = null,
    onSearchClick: () -> Unit = {},
    onPlaceQuery: (String) -> Unit = {},
    onTabSelect: (MoyeotaTab) -> Unit = {},
) {
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(repository))
    val favorites by viewModel.favorites.collectAsState()

    // 15 에서 즐겨찾기를 등록하고 돌아오면 반영돼야 해서 진입마다 재조회한다
    LaunchedEffect(Unit) { viewModel.refresh() }

    HomeScreen(
        userName = userName,
        favoritePlaces = favorites,
        onSearchClick = onSearchClick,
        onFavoritePlaceClick = { onPlaceQuery(it.address) },
        onRecentPlaceClick = { onPlaceQuery(it.name) },
        onTabSelect = onTabSelect,
    )
}
