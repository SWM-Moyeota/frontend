package com.moyeota.presentation.feature.mypage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.moyeota.domain.model.RideRecord
import com.moyeota.domain.repository.RideRecordRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 34 지난 탑승 기록의 상태·조회.
 *
 * 목록을 화면(`rememberSaveable`)이 아니라 여기에 두는 이유: 커서 페이징이라 **이미 받은 페이지와
 * 다음 커서가 함께 보존돼야** 한다. 화면이 들고 있으면 구성 변경 때 목록은 남고 커서는 사라져
 * 같은 페이지를 다시 받아 목록이 중복된다(36 이 입력값을 여기 두는 것과 같은 결의 이유).
 */
class RideHistoryViewModel(private val repository: RideRecordRepository) : ViewModel() {

    data class UiState(
        val records: List<RideRecord> = emptyList(),
        /** 첫 페이지 조회 중. 목록이 비어 있을 때만 화면 전체 스피너가 된다. */
        val loading: Boolean = false,
        /** 다음 페이지 조회 중. 목록 맨 아래 스피너다. */
        val loadingMore: Boolean = false,
        /**
         * 다음 페이지 커서. **[hasNext] 가 true 일 때만 값이 있다** — 서버가 그렇게 준다.
         * 화면에 내보내지 않는다(화면이 판정에 쓰는 것은 [hasNext] 하나다).
         */
        val nextCursor: Long? = null,
        val hasNext: Boolean = false,
        /**
         * 조회가 실패했는가. 사유를 구분하지 않는다 — 네트워크든 5xx 든 사용자가 할 일은
         * 「다시 시도」 하나다. 다음 페이지 실패도 같은 플래그를 쓰지만, 목록이 이미 있으면
         * 화면은 전면 오류로 바꾸지 않고 바닥 스피너만 거둔다.
         */
        val failed: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // 진행 중인 조회. 같은 커서로 두 번 나가는 것을 막는다 — 스크롤 트리거는 쉽게 두 번 불린다.
    private var loadJob: Job? = null

    init {
        refresh()
    }

    /** 첫 페이지부터 다시 받는다. 재시도 버튼과 진입이 같은 경로를 쓴다. */
    fun refresh() {
        if (loadJob?.isActive == true) return
        _uiState.update { it.copy(loading = true, failed = false) }
        loadJob = viewModelScope.launch {
            runCatching { repository.getMyRecords(cursor = null) }
                .onSuccess { page ->
                    _uiState.update {
                        it.copy(
                            records = page.records,
                            loading = false,
                            nextCursor = page.nextCursor,
                            hasNext = page.hasNext,
                            failed = false,
                        )
                    }
                }
                .onFailure {
                    // 실패해도 이전 목록은 남긴다 — 2페이지를 보던 사용자의 화면이 비지 않게.
                    _uiState.update { state -> state.copy(loading = false, failed = true) }
                }
        }
    }

    /**
     * 다음 페이지. **`hasNext` 가 false 면 아무것도 하지 않는다** — 서버는 그때 `nextCursor` 를
     * 항상 null 로 주므로, 커서 없이 부르면 **첫 페이지를 다시 받아** 목록이 무한히 중복된다.
     *
     * 커서가 null 인데 `hasNext` 가 true 인 응답은 서버가 주지 않지만, 그때도 부르지 않는다 —
     * 보낼 커서가 없으면 그 호출은 첫 페이지 재조회와 같다.
     */
    fun loadMore() {
        val state = _uiState.value
        val cursor = state.nextCursor ?: return
        if (!shouldLoadMore(state.hasNext, state.loadingMore || state.loading)) return
        if (loadJob?.isActive == true) return

        _uiState.update { it.copy(loadingMore = true, failed = false) }
        loadJob = viewModelScope.launch {
            runCatching { repository.getMyRecords(cursor = cursor) }
                .onSuccess { page ->
                    _uiState.update { prev ->
                        val known = prev.records.mapTo(mutableSetOf()) { it.partyId }
                        prev.copy(
                            // 서버 정렬이 `dropoffTime desc, matchId desc` 라 뒤에 붙이면 된다.
                            // 같은 방이 두 번 들어오는 것만 막는다 — LazyColumn 의 key 가
                            // partyId 라 중복 키가 들어오면 그 자리에서 터진다.
                            records = prev.records + page.records.filterNot { it.partyId in known },
                            loadingMore = false,
                            nextCursor = page.nextCursor,
                            hasNext = page.hasNext,
                            failed = false,
                        )
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(loadingMore = false, failed = true) }
                }
        }
    }

    companion object {
        fun factory(repository: RideRecordRepository) = viewModelFactory {
            initializer { RideHistoryViewModel(repository) }
        }
    }
}

/**
 * 34 지난 탑승 기록 — `GET /api/v1/users/me/parties` 진입점.
 *
 * 진입 즉시 첫 페이지를 받는다([RideHistoryViewModel] 의 `init`). 실패하면 화면이 재시도를 그린다.
 *
 * **[com.moyeota.domain.repository.ActivePartyRepository] 를 받지 않는다.** 이 엔드포인트에는
 * 진행 중인 방이 들어오지 않아(실측 2026-11-01, 로컬 `:8080`) 둘은 서로를 대체하지 않는다 —
 * 진행 중인 방은 14 홈·17 탐색의 배너가 보여 준다.
 */
@Composable
fun RideHistoryRoute(
    repository: RideRecordRepository,
    onBack: () -> Unit = {},
) {
    val viewModel: RideHistoryViewModel =
        viewModel(factory = RideHistoryViewModel.factory(repository))
    val state by viewModel.uiState.collectAsState()

    RideHistoryScreen(
        records = state.records,
        loading = state.loading,
        loadingMore = state.loadingMore,
        hasNext = state.hasNext,
        failed = state.failed,
        onLoadMore = viewModel::loadMore,
        onRetry = viewModel::refresh,
        onBack = onBack,
    )
}
