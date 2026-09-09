package com.moyeota.presentation.feature.explore

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.User
import com.moyeota.domain.repository.RideRepository
import com.moyeota.presentation.core.BackStateScaffold
import com.moyeota.presentation.core.ErrorBox
import com.moyeota.presentation.core.LoadingBox
import com.moyeota.presentation.core.location.rememberMyLocationState
import com.moyeota.presentation.core.pickupDistanceMeters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class JoinConfirmViewModel(
    private val repository: RideRepository,
    private val partyId: Long,
) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data class Success(val ride: Ride) : UiState
        data class Error(val message: String) : UiState
    }

    // 합류 제출 상태. joinedRide 가 채워지면 Route 가 21 매칭 대기로 넘긴다.
    // 서버 합류 응답이 곧 최신 방 상세라 별도 재조회가 필요 없다.
    data class JoinState(
        val joining: Boolean = false,
        val errorMessage: String? = null,
        val joinedRide: Ride? = null,
    )

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _joinState = MutableStateFlow(JoinState())
    val joinState: StateFlow<JoinState> = _joinState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            _uiState.value = try {
                UiState.Success(repository.getPartyDetail(partyId))
            } catch (e: Exception) {
                UiState.Error("합승 정보를 불러오지 못했어요")
            }
        }
    }

    fun join() {
        if (_joinState.value.joining) return // 중복 제출 차단 (공통 규칙)
        viewModelScope.launch {
            _joinState.update { it.copy(joining = true, errorMessage = null) }
            try {
                // 합류 주체는 Bearer 토큰이 정한다 — memberId 를 넘기지 않는다 (22 보고서 §2).
                // 합류 응답(PartyDetailResponse)이 곧 갱신된 방 상세 — 화면 상태를 이걸로 덮는다
                val joined = repository.joinParty(partyId)
                _uiState.value = UiState.Success(joined)
                _joinState.update { it.copy(joining = false, joinedRide = joined) }
            } catch (e: Exception) {
                _joinState.update { it.copy(joining = false, errorMessage = "합류하지 못했어요. 잠시 후 다시 시도해 주세요") }
            }
        }
    }

    companion object {
        fun factory(repository: RideRepository, partyId: Long) = viewModelFactory {
            initializer { JoinConfirmViewModel(repository, partyId) }
        }
    }
}

// 20 합류 확인 — 서버 상세 조회 + 합류 액션 진입점. partyId 없으면 기존 더미 화면 유지.
// 합류 성공 시 서버가 돌려준 최신 [Ride] 를 그대로 넘겨, 다음 화면(21 매칭 대기)이 재조회 없이 이어받는다.
@Composable
fun JoinConfirmRoute(
    repository: RideRepository,
    partyId: Long?,
    onDismiss: () -> Unit = {},
    onJoined: (Ride) -> Unit = {},
    onMemberClick: (User) -> Unit = {},
) {
    if (partyId == null) {
        JoinConfirmScreen(
            onDismiss = onDismiss,
            onConfirmJoin = onJoined,
            onMemberClick = onMemberClick,
        )
        return
    }

    val viewModel: JoinConfirmViewModel = viewModel(
        key = "join-$partyId",
        factory = JoinConfirmViewModel.factory(repository, partyId),
    )
    val state by viewModel.uiState.collectAsState()
    val join by viewModel.joinState.collectAsState()

    // 「내 위치에서 약 N m」용 좌표. 권한을 자동 요청하지 않는다 — 합류 판단에 꼭 필요한 값이 아니라
    // 여기서 다이얼로그를 띄우면 흐름을 끊는다. 이미 허용돼 있으면(14·17 에서) 거리 줄이 뜬다.
    val myLocation = rememberMyLocationState(autoRequestPermission = false)

    LaunchedEffect(join.joinedRide) {
        join.joinedRide?.let(onJoined)
    }

    // 탭바가 없는 화면이라 뒤로가기가 유일한 이동 수단 — 로딩·에러에서도 남긴다 (QA F-1 동류)
    when (val current = state) {
        JoinConfirmViewModel.UiState.Loading -> BackStateScaffold("합류할까요?", onDismiss) {
            LoadingBox()
        }
        is JoinConfirmViewModel.UiState.Error -> BackStateScaffold("합류할까요?", onDismiss) {
            ErrorBox(message = current.message, onRetry = viewModel::refresh)
        }
        is JoinConfirmViewModel.UiState.Success -> JoinConfirmScreen(
            ride = current.ride,
            pickupDistanceMeters = pickupDistanceMeters(current.ride, myLocation.coordinates),
            joining = join.joining,
            joinErrorMessage = join.errorMessage,
            onDismiss = onDismiss,
            onConfirmJoin = { viewModel.join() },
            onMemberClick = onMemberClick,
        )
    }
}
