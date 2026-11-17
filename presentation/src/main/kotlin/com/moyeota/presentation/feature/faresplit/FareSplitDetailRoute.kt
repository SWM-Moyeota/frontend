package com.moyeota.presentation.feature.faresplit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.moyeota.domain.model.FareSplitDetail
import com.moyeota.domain.model.FareSplitException
import com.moyeota.domain.model.Ride
import com.moyeota.domain.repository.FareSplitRepository
import com.moyeota.domain.repository.RideRepository
import com.moyeota.presentation.core.BackStateScaffold
import com.moyeota.presentation.core.ErrorBox
import com.moyeota.presentation.core.LoadingBox
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 39 정산 상세의 상태·호출.
 *
 * ### 본문 없는 204 셋 뒤에는 **반드시 재조회한다** ★★
 * 「보냈어요」·「받았어요」·「아직 안 들어왔어요」는 모두 **응답 본문이 없다.** 즉 서버가 무엇을
 * 바꿨는지 앱이 알 길이 없고, 한 행만 낙관적으로 고치면 두 가지를 놓친다:
 *
 * | 놓치는 것 | 그래서 보이는 화면 |
 * |---|---|
 * | **마지막 확인이 정산 전체를 닫는다**(COMPLETED + 계좌번호 삭제) | 닫힌 정산에 계좌번호가 남아 보이고, 「받았어요」가 그대로 떠 있다 |
 * | 그사이 다른 사람이 누른 「보냈어요」 | 내 화면만 과거에 머문다 |
 *
 * 그래서 모든 쓰기는 [runMutation] 한 곳을 지나고, **성공이든 「낡았다」는 실패든 상세를 다시
 * 읽는다.** 「이미 보냈습니다」 같은 문구는 만들지 않는다 — 멱등 204 는 「이미 그 상태였다」와
 * 「지금 바뀌었다」를 구분할 수 없다.
 *
 * ### 「받았어요」는 끝까지 멱등이 아니다 — 그 처리는 data 계층에 있다
 * 마지막 송금을 확인하면 정산이 닫히고 그다음부터는 409 `FARE_SPLIT_CLOSED` 가 온다(실측).
 * 데이터 계층이 그 409 를 성공으로 접으므로 이 ViewModel 은 신경 쓰지 않는다 — 다만 **성공 뒤
 * 재조회**는 그래서 더 중요하다(그 호출이 정산 전체를 닫았을 수 있다).
 *
 * ### 정산이 없는 것은 실패가 아니다
 * 저장소가 404 `FARE_SPLIT_NOT_FOUND` 를 **null 로** 접어 준다 — 끝난 방의 정상 상태다. 그때는
 * 「시작할 수 있는가」를 방 상세로 한 번 더 묻는다([UiState.Absent]): 묻지 않으면 방장이 아닌
 * 사람에게 「정산 시작하기」가 떠서 403 `NOT_PARTY_HOST` 로만 끝난다.
 */
class FareSplitDetailViewModel(
    private val repository: FareSplitRepository,
    /**
     * 방 상세를 읽는 **한 가지 일**만 받는다 — `RideRepository` 전체가 아니다.
     *
     * 이유가 둘이다.
     * 1. **쓰는 것이 하나뿐이다.** 이 ViewModel 이 방에서 필요한 것은 「도착을 누른 멤버가 누구인가」
     *    (`getPartyDetail` → [com.moyeota.domain.model.fareSplitRiders]) 하나다. 저장소 전체를 받으면
     *    의존이 실제보다 넓다고 말하게 된다.
     * 2. **테스트가 17개 멤버를 흉내 내지 않아도 된다.** `RideRepository` 는 매칭·예약·SSE 까지
     *    품은 큰 인터페이스이고 지금도 바뀌는 중이다. 전체를 구현한 가짜를 두면 **정산과 무관한
     *    시그니처 변경마다 이 테스트가 깨진다** — 그러면 깨짐이 신호가 아니라 잡음이 된다.
     */
    private val loadRide: suspend (partyId: Long) -> Ride,
    private val partyId: Long,
) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState

        /**
         * 이 방에 정산이 **아직 없다**. 오류가 아니다.
         *
         * @property canStart 내가 시작할 수 있는가([startDraftGate] 가 판정한다).
         * @property blockedReason 시작할 수 없는 이유. **null 이면 「알 수 없다」**다(방 상세 조회
         *   실패) — 그때는 단정하지 않고 다시 불러오게 한다.
         */
        data class Absent(val canStart: Boolean, val blockedReason: String?) : UiState

        data class Ready(val detail: FareSplitDetail) : UiState

        data class Error(val message: String) : UiState
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** 쓰기 호출이 도는 중. 세 버튼을 **함께** 잠근다 — 한 행을 누른 뒤 다른 행을 누르면 재조회가 겹친다. */
    private val _actionInProgress = MutableStateFlow(false)
    val actionInProgress: StateFlow<Boolean> = _actionInProgress.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    init {
        load(showLoading = true)
    }

    /** 에러 화면의 「다시 시도」와 첫 진입이 같은 길을 쓴다. */
    fun refresh() = load(showLoading = true)

    /**
     * 화면이 보이기 시작했다 — **40 수정 화면에서 돌아온 경우**가 이 길의 이유다.
     *
     * 수정은 상세를 돌려주지만 그 값은 수정 화면이 들고 있던 것이다. 여기서 다시 읽지 않으면
     * 뒤로 돌아온 상세가 옛 금액을 보여 준다. 들고 있던 값은 지우지 않으므로(= `showLoading = false`)
     * 깜빡이지 않는다.
     */
    fun onScreenStart() {
        if (_uiState.value is UiState.Loading) return
        load(showLoading = false)
    }

    fun markSent() = runMutation(FareSplitAction.MARK_SENT) { repository.markSent(partyId) }

    /**
     * 「받았어요」 — **되돌릴 수 없다.** 화면이 확인 다이얼로그를 거친 뒤에만 부른다.
     *
     * [senderPublicId] 가 빈 값이면 **부르지 않는다**(서버가 404 로 막는다). 화면이
     * [FareSplitTransfer.isConfirmable] 로 버튼을 잠그지만, 여기서 한 번 더 막는 이유는 이 함수가
     * 외부에서 보이는 API 라서다 — 잠금이 한 겹뿐이면 그 한 겹이 풀릴 때 조용히 404 가 된다.
     */
    fun confirmReceived(senderPublicId: String) {
        if (senderPublicId.isEmpty()) return
        runMutation(FareSplitAction.CONFIRM_RECEIVED) { repository.confirmReceived(partyId, senderPublicId) }
    }

    /** 「아직 안 들어왔어요」 — **멱등이 아닌 유일한 동작**이다(SENT 에서만 된다). */
    fun markNotReceived(senderPublicId: String) {
        if (senderPublicId.isEmpty()) return
        runMutation(FareSplitAction.MARK_NOT_RECEIVED) { repository.markNotReceived(partyId, senderPublicId) }
    }

    fun clearActionError() {
        _actionError.value = null
    }

    /**
     * 쓰기 호출 하나 — **성공 뒤 재조회가 이 함수의 존재 이유다.**
     *
     * 실패도 두 갈래로 다룬다: 「낡았다」는 신호면([needsReloadAfter]) 문구와 **함께** 다시 읽는다.
     * 그래야 사용자가 어긋난 화면 위에서 다음 동작을 하지 않는다.
     *
     * 중복 호출을 막는 이유는 네트워크 절약이 아니라 **순서**다 — 두 번 눌린 「받았어요」가 각자
     * 재조회를 걸면 늦게 끝난 쪽의 낡은 응답이 화면을 되돌린다.
     */
    private fun runMutation(action: FareSplitAction, block: suspend () -> Unit) {
        // ★ 빗장을 `launch` **밖에서** 세운다. 안에서 세우면 디스패처가 본문을 즉시 실행하지 않는 한
        // 두 번째 호출이 **첫 호출의 본문보다 먼저** 검사를 지나간다. `viewModelScope` 는
        // `Dispatchers.Main.immediate` 라 실제로는 통과하지만, 중복 방어가 **디스패처의 성질에
        // 기대고 있으면** 그 성질이 바뀌는 날 조용히 깨진다 — 그리고 깨진 모습은 「두 재조회가 겹쳐
        // 늦게 끝난 쪽의 낡은 응답이 화면을 되돌리는」 것이라 재현도 어렵다.
        // (`FareSplitMutationReloadTest` 가 `StandardTestDispatcher` 로 이 자리를 고정한다.)
        if (_actionInProgress.value) return
        _actionInProgress.value = true
        _actionError.value = null

        viewModelScope.launch {
            try {
                block()
                // ★ 본문이 없다 — 다시 읽는 것이 유일한 진실이다.
                load(showLoading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: FareSplitException) {
                _actionError.value = fareSplitFailureMessage(e, action)
                if (needsReloadAfter(e)) load(showLoading = false)
            } catch (e: Exception) {
                _actionError.value = fareSplitGenericFailure(action)
            } finally {
                _actionInProgress.value = false
            }
        }
    }

    /**
     * 상세를 읽는다. [showLoading] 이 false 면 **보던 화면을 지우지 않는다** — 쓰기 뒤의 재조회와
     * 화면 복귀가 그 경우다(깜빡이면 사용자가 자기가 누른 결과를 못 본다).
     */
    private fun load(showLoading: Boolean) {
        viewModelScope.launch {
            if (showLoading) _uiState.value = UiState.Loading
            try {
                val detail = repository.getFareSplit(partyId)
                _uiState.value = if (detail == null) absentState() else UiState.Ready(detail)
            } catch (e: CancellationException) {
                throw e
            } catch (e: FareSplitException) {
                _uiState.value = failureState(fareSplitFailureMessage(e, FareSplitAction.LOAD))
            } catch (e: Exception) {
                _uiState.value = failureState(fareSplitGenericFailure(FareSplitAction.LOAD))
            }
        }
    }

    /**
     * 정산이 없을 때 **누구에게 「정산 시작하기」를 띄울지** 방 상세로 묻는다.
     *
     * 방 상세 조회가 실패하면 **단정하지 않는다** — `canStart = false` + 이유 null 이고, 화면은
     * 「정산이 아직 없어요」와 다시 불러오기만 보여 준다. 실패를 「당신은 시작할 수 없다」로 읽으면
     * 방장이 자기 방을 정산하지 못하는 막다른 화면이 된다.
     */
    private suspend fun absentState(): UiState.Absent = try {
        when (val gate = startDraftGate(loadRide(partyId))) {
            is FareSplitDraftGate.Ready -> UiState.Absent(canStart = true, blockedReason = null)
            is FareSplitDraftGate.Blocked -> UiState.Absent(canStart = false, blockedReason = gate.message)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        UiState.Absent(canStart = false, blockedReason = null)
    }

    /**
     * 조회 실패. **이미 보고 있던 상세가 있으면 그것을 지우지 않는다** — 쓰기 뒤의 재조회가 한 번
     * 실패했다고 사용자가 보던 금액·계좌를 빼앗을 이유가 없다(17 탐색의 `markStale` 과 같은 판단).
     * 그때 실패는 [actionError] 가 이미 말하고 있거나, 다음 복귀에서 다시 읽힌다.
     */
    private fun failureState(message: String): UiState =
        (_uiState.value as? UiState.Ready) ?: UiState.Error(message)

    companion object {
        fun factory(
            repository: FareSplitRepository,
            rideRepository: RideRepository,
            partyId: Long,
        ) = viewModelFactory {
            initializer { FareSplitDetailViewModel(repository, rideRepository::getPartyDetail, partyId) }
        }
    }
}

/**
 * 39 정산 상세 진입점. 역할 분기는 화면이 한다([FareSplitDetailScreen]).
 *
 * `key = "fare-split-$partyId"` 를 주는 이유는 [com.moyeota.presentation.feature.matching.RideDetailRoute]
 * 와 같다 — 방이 다른데 같은 ViewModel 을 물려받으면 **남의 정산이 보인다.** 정산은 돈이 걸린
 * 화면이라 그 혼동의 대가가 특히 크다.
 */
@Composable
fun FareSplitDetailRoute(
    repository: FareSplitRepository,
    rideRepository: RideRepository,
    partyId: Long?,
    onBack: () -> Unit = {},
    /**
     * 40 정산 시작·수정으로. **`start` 가 둘을 가른다** — 아직 정산이 없는 방에서는 시작(POST)이고,
     * 있는 정산에서는 수정(PATCH)이다. 한 콜백으로 묶은 이유는 목적지가 같은 라우트라서이고,
     * 불리언으로 가르는 이유는 **호출하는 자리가 이미 그 사실을 알고 있기** 때문이다
     * (시작은 [FareSplitAbsentScreen], 수정은 [FareSplitDetailScreen] 에서 온다).
     */
    onOpenEditor: (start: Boolean) -> Unit = {},
    /** 그 방의 채팅으로. 열 방이 없으면 null — 버튼을 그리지 않는다. */
    onOpenChat: (() -> Unit)? = null,
) {
    // 방 id 없이 닿을 길은 없다. 그래도 null 이면 **예시 정산을 그리지 않고** 사실대로 말하고 되돌린다
    // (21 의 waitingRideDummy 가 프로덕션에 샜던 사고와 같은 종류의 방어다).
    if (partyId == null) {
        BackStateScaffold("택시비 정산", onBack) {
            ErrorBox(message = "정산 정보를 불러오지 못했어요", onRetry = onBack, actionLabel = "돌아가기")
        }
        return
    }

    val viewModel: FareSplitDetailViewModel = viewModel(
        key = "fare-split-$partyId",
        factory = FareSplitDetailViewModel.factory(repository, rideRepository, partyId),
    )
    val state by viewModel.uiState.collectAsState()
    val actionInProgress by viewModel.actionInProgress.collectAsState()
    val actionError by viewModel.actionError.collectAsState()
    val context = LocalContext.current

    // 40 에서 돌아오면 금액이 바뀌어 있을 수 있다 — 복귀마다 다시 읽는다(깜빡이지 않는다).
    LifecycleStartEffect(viewModel) {
        viewModel.onScreenStart()
        onStopOrDispose { }
    }

    when (val current = state) {
        FareSplitDetailViewModel.UiState.Loading -> BackStateScaffold("택시비 정산", onBack) {
            LoadingBox()
        }

        is FareSplitDetailViewModel.UiState.Error -> BackStateScaffold("택시비 정산", onBack) {
            ErrorBox(message = current.message, onRetry = viewModel::refresh)
        }

        is FareSplitDetailViewModel.UiState.Absent -> FareSplitAbsentScreen(
            canStart = current.canStart,
            blockedReason = current.blockedReason,
            onBack = onBack,
            onStart = { onOpenEditor(true) },
            onRetry = viewModel::refresh,
        )

        is FareSplitDetailViewModel.UiState.Ready -> FareSplitDetailScreen(
            detail = current.detail,
            actionInProgress = actionInProgress,
            actionErrorMessage = actionError,
            onBack = onBack,
            onEdit = { onOpenEditor(false) },
            onMarkSent = viewModel::markSent,
            onConfirmReceived = viewModel::confirmReceived,
            onMarkNotReceived = viewModel::markNotReceived,
            // 딥링크는 **서버가 준 문자열 그대로** 넘긴다. 열지 못하면 false 가 돌아가 화면이
            // 복사 안내로 바뀐다([TOSS_OPEN_FAILED_NOTICE]).
            onOpenToss = { deeplink -> context.openTossTransfer(deeplink) },
            onCopy = { label, value -> context.copyToClipboard(label, value) },
            onOpenChat = onOpenChat,
        )
    }
}
