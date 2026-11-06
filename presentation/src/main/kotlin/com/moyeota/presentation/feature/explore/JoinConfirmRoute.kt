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
import com.moyeota.domain.analytics.Analytics
import com.moyeota.domain.analytics.AnalyticsEvent
import com.moyeota.domain.analytics.MatchFailureReason
import com.moyeota.domain.analytics.MatchRequestMethod
import com.moyeota.domain.model.PartyJoinException
import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.User
import com.moyeota.domain.repository.ActivePartyRepository
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
    /**
     * 합류를 막은 **그 다른 방**이 어떤 방인지 알아내는 용도 하나로 쓴다([blockingPartyConfirmed]).
     * 서버는 409 에 「어느 방인지」도 「확정됐는지」도 싣지 않으므로, 그걸 알려면 내 진행 중인 방을
     * 따로 찾아야 한다. 실패 경로에서만 부르므로 평상시 왕복은 늘지 않는다.
     */
    private val activeParty: ActivePartyRepository,
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
            // Click — 요청이 나가는 순간(서버 응답 전)
            Analytics.track(AnalyticsEvent.PartyJoinClicked)
            try {
                repository.joinParty(partyId)
                finishJoin()
            } catch (e: CancellationException) {
                // 화면을 떠나며 취소된 것 — 실패 문구를 띄우지 않는다
                throw e
            } catch (e: PartyJoinException) {
                if (e.isAlreadyInThisParty) {
                    // 「이미 이 방 멤버」는 성공 경로 — iOS `finishJoin` 과 같이 request_match 도 센다(fail 은 없다)
                    finishJoin()
                    return@launch
                }
                // 「다른 방에 참여 중」일 때만 그 방의 사정을 확인한다 — 확정된 방이면 나갈 수가 없어서
                // 안내할 행동이 완전히 달라진다([alreadyInPartyMessage]).
                val blockingConfirmed = if (e.isAlreadyInAnotherParty) blockingPartyConfirmed() else null
                _joinState.update {
                    it.copy(joining = false, errorMessage = joinFailureMessage(e, blockingConfirmed))
                }
                Analytics.track(AnalyticsEvent.MatchRequestFailed(MatchRequestMethod.JOIN, joinFailureReason(e)))
                if (e.isPartyFull || e.isPartyClosed) refreshQuietly()
            } catch (e: Exception) {
                _joinState.update { it.copy(joining = false, errorMessage = JOIN_FAILED_MESSAGE) }
                Analytics.track(AnalyticsEvent.MatchRequestFailed(MatchRequestMethod.JOIN, MatchFailureReason.UNKNOWN))
            }
        }
    }

    /**
     * 합류 성공(또는 **이미 돼 있던** 합류) — 21 로 넘길 방 id 를 세우고 `request_match(join)` 을 기록한다
     * (iOS `finishJoin(joinedPartyId:)`).
     *
     * 합류 응답([com.moyeota.domain.model.PartyStatus])에는 정원·요금이 없어, **합류 전에 보고 있던 상세**에서
     * 꺼낸다. 방 상세에는 주소가 없어 지역은 싣지 않는다 — 방 생성 쪽만 지역이 있다. 정원은 0 으로 지어내지
     * 않는다 — 상세를 못 읽었거나 0 이면 키를 뺀다.
     */
    private fun finishJoin() {
        _joinState.update { it.copy(joining = false, joinedPartyId = partyId) }
        val ride = (_uiState.value as? UiState.Success)?.ride
        Analytics.track(
            AnalyticsEvent.MatchRequested(
                method = MatchRequestMethod.JOIN,
                capacity = ride?.capacity?.takeIf { it > 0 },
                originArea = null,
                destinationArea = null,
                estimatedFare = ride?.estimatedFare,
            ),
        )
    }

    // 떠 있는 방을 로딩으로 덮지 않고 다시 읽는다. 실패는 조용히 둔다 — 보던 값이 조금 낡는 것보다 낫다.
    private suspend fun refreshQuietly() {
        val ride = runCatching { repository.getPartyDetail(partyId) }.getOrNull() ?: return
        _uiState.value = UiState.Success(ride)
    }

    /**
     * 합류를 막은 그 방이 **약속이 정해진 방인가.** 모르면 null.
     *
     * 실패했을 때만 부른다. 실패하거나 못 찾으면 null 로 떨어뜨려 **단정하지 않는 문구**로 가게 한다 —
     * 안내 한 줄을 정확히 만들려다 안내 자체를 못 띄우면 더 나쁘다.
     */
    private suspend fun blockingPartyConfirmed(): Boolean? = try {
        activeParty.resolve()?.isConfirmed
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        null
    }

    companion object {
        fun factory(
            repository: RideRepository,
            activeParty: ActivePartyRepository,
            partyId: Long,
        ) = viewModelFactory {
            initializer { JoinConfirmViewModel(repository, activeParty, partyId) }
        }
    }
}

/**
 * `fail_match_request(join)` 의 `reason` — [joinFailureMessage] 와 같은 분기를 **문구가 아니라 상수**로 옮긴다
 * (iOS `joinFailureReason` 과 같은 표). [PartyJoinException.isAlreadyInThisParty] 는 여기 오지 않는다(성공 경로).
 */
internal fun joinFailureReason(e: PartyJoinException): String = when {
    e.isPartyFull -> MatchFailureReason.PARTY_FULL
    e.isPartyClosed -> MatchFailureReason.PARTY_CLOSED
    e.isPartyNotFound -> MatchFailureReason.PARTY_NOT_FOUND
    e.isAlreadyInAnotherParty -> MatchFailureReason.ALREADY_IN_PARTY
    e.isAlreadyJoined -> MatchFailureReason.ALREADY_JOINED
    e.isNetwork -> MatchFailureReason.NETWORK
    else -> MatchFailureReason.UNKNOWN
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
internal fun joinFailureMessage(
    e: PartyJoinException,
    /** 합류를 막은 그 방이 약속이 정해진 방인가. 모르면 null — [alreadyInPartyMessage] 참고. */
    blockingConfirmed: Boolean? = null,
): String = when {
    e.isPartyFull -> "방이 가득 찼어요. 다른 방을 찾아 주세요"
    e.isPartyClosed -> "모집이 끝난 방이에요. 다른 방을 찾아 주세요"
    e.isPartyNotFound -> "사라진 방이에요. 목록을 다시 불러와 주세요"
    e.isAlreadyInAnotherParty -> alreadyInPartyMessage(blockingConfirmed)
    e.isAlreadyJoined -> "이미 참여 중이에요. 홈에서 진행 중인 방을 확인해 주세요"
    e.isNetwork -> "네트워크가 불안정해요. 연결을 확인하고 다시 시도해 주세요"
    else -> JOIN_FAILED_MESSAGE
}

/**
 * 「이미 참여 중인 방이 있다」를 **그 방의 사정에 맞춰** 말한다.
 *
 * 예전에는 한 문구였다 — 「그 방에서 나온 뒤 합류해 주세요」. 확정 전 방에서는 맞는 말이지만
 * **약속이 정해진 방에서는 할 수 없는 일을 시킨다**: 나가기가 409 `CANNOT_LEAVE_CONFIRMED_PARTY` 로
 * 막히고, 방 종료도 409 `USE_DEPART_FOR_CONFIRMED_PARTY` 로 막힌다. 끝내는 길은 **출발지에 가서
 * 「출발」을 누르는 것 하나뿐**이라, 나가기 버튼을 찾아 헤매게 하지 않고 그 길을 말해 준다.
 *
 * null(모름)일 때 「나온 뒤」를 말하지 않는 이유도 같다 — 틀릴 수 있는 지시보다 **틀릴 수 없는 안내**가
 * 낫다. 그 방으로 보내면 방 화면이 「약속을 정한 뒤에는 나갈 수 없어요」로 사실을 말해 준다.
 */
internal fun alreadyInPartyMessage(blockingConfirmed: Boolean?): String = when (blockingConfirmed) {
    true -> "약속이 정해진 방에 참여 중이에요. 출발지에서 「출발」을 눌러 그 방을 끝낸 뒤에 합류할 수 있어요"
    false -> "이미 참여 중인 방이 있어요. 그 방에서 나온 뒤 합류해 주세요"
    null -> "이미 참여 중인 방이 있어요. 홈에서 진행 중인 방을 확인해 주세요"
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
    activePartyRepository: ActivePartyRepository,
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
        factory = JoinConfirmViewModel.factory(repository, activePartyRepository, partyId),
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
            // 이미 그 방 멤버면 합류를 다시 시도할 이유가 없다 — **합류 성공과 같은 길**로 보낸다.
            // 서버가 409 `ALREADY_JOINED` 를 돌려줬을 때 이미 이렇게 처리하고 있어(ViewModel.join 의
            // isAlreadyInThisParty 분기) 새 네비게이션 인자가 필요 없다. partyId 는 이 Route 가
            // 들고 있는 값이라 화면이 들고 있는 Ride 를 다시 쓰지 않는다.
            onOpenJoinedRide = { _ -> onJoined(partyId) },
            onMemberClick = onMemberClick,
        )
    }
}
