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
import com.moyeota.domain.model.PartyJoinException
import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.User
import com.moyeota.domain.repository.RideRepository
import com.moyeota.presentation.core.BackStateScaffold
import com.moyeota.presentation.core.ErrorBox
import com.moyeota.presentation.core.LoadingBox
import com.moyeota.presentation.core.location.rememberMyLocationState
import com.moyeota.presentation.core.pickupDistanceMeters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 원인을 가릴 수 없는 합류 실패(401·5xx·직렬화 등)의 문구 — 재시도 말고는 안내할 게 없다 */
private const val JOIN_FAILED_MESSAGE = "합류하지 못했어요. 잠시 후 다시 시도해 주세요"

class JoinConfirmViewModel(
    private val repository: RideRepository,
    private val partyId: Long,
) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data class Success(val ride: Ride) : UiState
        data class Error(val message: String) : UiState
    }

    /**
     * 합류 제출 상태. [joinedPartyId] 가 채워지면 Route 가 21 매칭 대기로 넘긴다.
     *
     * 넘기는 건 방 **id 하나**다. 합류 응답에 동승자 목록이 없고(Backend #213) 21 이 구독하는 SSE 의
     * 첫 스냅샷이 방 상세 전체를 주므로, 예전처럼 여기서 상세를 한 번 더 읽어 [Ride] 를 만들어 넘기면
     * **왕복 1회가 그냥 버려진다.**
     */
    data class JoinState(
        val joining: Boolean = false,
        val errorMessage: String? = null,
        val joinedPartyId: Long? = null,
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
                UiState.Error("동승 정보를 불러오지 못했어요")
            }
        }
    }

    /**
     * 합류 제출. 합류 주체는 Bearer 토큰이 정한다 — memberId 를 넘기지 않는다 (22 보고서 §2).
     *
     * 성공하면 **응답을 쓰지 않고** 방 id 만 세운다(상태·인원은 21 의 첫 스냅샷이 더 정확히 준다).
     *
     * 실패 중 하나는 실패가 아니다: [PartyJoinException.isAlreadyInThisParty] 는 **서버에 내가 이미 그 방
     * 멤버로 들어가 있다**는 뜻이고(데이터 계층이 상세로 확인한 증거다) — 그러니 **성공과 똑같은 길로**
     * 21 로 보낸다. 이 분기가 없으면 그 계정은 재시도도 409, 다른 방도 409 라 30분 스윕 전까지 어떤 방에도
     * 못 들어간다(합류는 서버가 멤버 추가를 먼저 커밋하고 응답을 만든다).
     *
     * 나머지 실패는 [joinFailureMessage] 가 각각 다른 문구로 안내한다. 방 사정이 바뀌어 막힌 경우
     * (가득 참·모집 종료)에는 들고 있는 상세를 **조용히** 다시 읽어 지금 인원이 보이게 한다 —
     * 화면을 로딩으로 덮으면 방금 띄운 안내 문구가 사라진다.
     */
    fun join() {
        if (_joinState.value.joining) return // 중복 제출 차단 (공통 규칙)
        viewModelScope.launch {
            _joinState.update { it.copy(joining = true, errorMessage = null) }
            try {
                repository.joinParty(partyId)
                _joinState.update { it.copy(joining = false, joinedPartyId = partyId) }
            } catch (e: CancellationException) {
                // 화면을 떠나며 취소된 것 — 실패 문구를 띄우지 않는다
                throw e
            } catch (e: PartyJoinException) {
                if (e.isAlreadyInThisParty) {
                    _joinState.update { it.copy(joining = false, joinedPartyId = partyId) }
                    return@launch
                }
                _joinState.update { it.copy(joining = false, errorMessage = joinFailureMessage(e)) }
                if (e.isPartyFull || e.isPartyClosed) refreshQuietly()
            } catch (e: Exception) {
                _joinState.update { it.copy(joining = false, errorMessage = JOIN_FAILED_MESSAGE) }
            }
        }
    }

    // 떠 있는 방을 로딩으로 덮지 않고 다시 읽는다. 실패는 조용히 둔다 — 보던 값이 조금 낡는 것보다 낫다.
    private suspend fun refreshQuietly() {
        val ride = runCatching { repository.getPartyDetail(partyId) }.getOrNull() ?: return
        _uiState.value = UiState.Success(ride)
    }

    companion object {
        fun factory(repository: RideRepository, partyId: Long) = viewModelFactory {
            initializer { JoinConfirmViewModel(repository, partyId) }
        }
    }
}

/**
 * 합류 실패를 사용자 문구로 옮긴다 — 실패마다 **다른 말**을 해야 다음 행동이 갈린다
 * (가득 찬 방은 다른 방을 고르면 되고, 다른 방에 참여 중이면 그 방을 먼저 정리해야 한다).
 *
 * 분기 근거는 서버 코드 문자열이 아니라 [PartyJoinException] 의 프로퍼티다 — 서버가 enum 이름을 바꿀 때
 * 화면이 조용히 어긋나지 않게 도메인이 한 번 걸러 준 값이다.
 *
 * [PartyJoinException.isAlreadyInThisParty] 는 여기 오지 않는다 — 그건 실패가 아니라 **이미 합류됨**이라
 * 호출부가 성공 경로로 보낸다.
 *
 * 「다른 방에 참여 중」과 「이 방인지 확인 실패」가 같은 자리에 섞여 있는 건 서버 사정이다: 재합류도
 * 다른 방 참여도 똑같이 `ALREADY_JOINED_OTHER_PARTY` 로 오고, 데이터 계층이 상세 조회로 가른다.
 * 그 조회까지 실패하면 「다른 방」으로 떨어지므로, 문구는 **그 방으로 가 보라는 쪽**으로 적는다.
 */
internal fun joinFailureMessage(e: PartyJoinException): String = when {
    e.isPartyFull -> "방이 가득 찼어요. 다른 방을 찾아 주세요"
    e.isPartyClosed -> "모집이 끝난 방이에요. 다른 방을 찾아 주세요"
    e.isPartyNotFound -> "사라진 방이에요. 목록을 다시 불러와 주세요"
    e.isAlreadyInAnotherParty -> "이미 참여 중인 방이 있어요. 그 방에서 나온 뒤 합류해 주세요"
    e.isAlreadyJoined -> "이미 참여 중이에요. 홈에서 진행 중인 방을 확인해 주세요"
    e.isNetwork -> "네트워크가 불안정해요. 연결을 확인하고 다시 시도해 주세요"
    else -> JOIN_FAILED_MESSAGE
}

/**
 * 20 합류 확인 — 서버 상세 조회 + 합류 액션 진입점. partyId 없으면 오류 안내 후 되돌린다.
 *
 * 합류가 끝나면 **방 id 만** 넘긴다([onJoined]) — 합류 응답은 더 이상 방 상세가 아니고(Backend #213),
 * 다음 화면(21 매칭 대기)이 SSE 첫 스냅샷으로 상세 전체를 받으므로 여기서 상세를 읽어 넘길 이유가 없다.
 */
@Composable
fun JoinConfirmRoute(
    repository: RideRepository,
    partyId: Long?,
    onDismiss: () -> Unit = {},
    /** 합류가 끝난(또는 **이미 돼 있던**) 방 id — 호출자는 21 매칭 대기로 넘긴다 */
    onJoined: (Long) -> Unit = {},
    onMemberClick: (User) -> Unit = {},
) {
    // 목록에서 방을 고르지 않고 닿을 길은 없다. 그래도 null 이면 예시 방을 그리는 대신
    // 사실대로 말하고 되돌린다 — 존재하지 않는 방에 합류 버튼을 보여 줄 수는 없다.
    if (partyId == null) {
        BackStateScaffold("합류할까요?", onDismiss) {
            ErrorBox(message = "동승 정보를 불러오지 못했어요", onRetry = onDismiss)
        }
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

    LaunchedEffect(join.joinedPartyId) {
        join.joinedPartyId?.let(onJoined)
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
