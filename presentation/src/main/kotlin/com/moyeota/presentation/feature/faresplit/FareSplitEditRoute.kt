package com.moyeota.presentation.feature.faresplit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.moyeota.domain.model.Bank
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 40 정산 시작·수정의 상태·호출.
 *
 * ### 입력을 ViewModel 에 두는 이유
 * 34 기록·36 프로필 수정과 같다 — 구성 변경(회전·시스템 테마)에서 입력이 살아남아야 한다. 더 센
 * 이유가 하나 더 있다: **은행 선택 다이얼로그가 떴다 사라지는 동안** 금액 열 칸이 그대로 있어야
 * 하고, 그 값들은 서로 **합계 제약**으로 묶여 있다([submitBlockOf]) — 일부만 복원되면 합이
 * 어긋난 폼이 된다.
 *
 * ### 시작과 수정이 **읽는 곳이 다르다** ★
 *
 * | 모드 | 탑승자 목록의 출처 | 왜 |
 * |---|---|---|
 * | [FareSplitEditMode.START] | **방 상세**(`getPartyDetail`) | 아직 정산이 없으니 도착을 누른 멤버를 방에서만 알 수 있다([com.moyeota.domain.model.fareSplitRiders]) |
 * | [FareSplitEditMode.EDIT] | **정산 상세**(`getFareSplit`) | 받는 사람 + `transfers` 가 곧 탑승자 전원이다 — 방을 다시 읽을 이유가 없다 |
 *
 * 수정에서 방을 읽지 않는 것이 단순한 절약이 아니다: 방이 지워진 뒤에도 정산은 남으므로
 * ([com.moyeota.domain.model.MyFareSplit] 의 null 경로) 방을 필수로 읽으면 **고칠 수 있는 정산을
 * 못 고치는** 경우가 생긴다.
 *
 * ### 409 둘을 **실패로만 끝내지 않는다**
 *
 * | 서버 | 뜻 | 이 화면이 하는 일 |
 * |---|---|---|
 * | `FARE_SPLIT_ALREADY_EXISTS` | 이미 그 방에 정산이 있다 | 적은 값을 **남긴 채** 「지금 있는 정산 보기」를 준다([FareSplitEditScreen] 의 `onSeeExisting`) |
 * | `FARE_SPLIT_ALREADY_STARTED` | 그사이 누가 「보냈어요」를 눌렀다 | 적은 값을 **남긴 채** 읽기 전용으로 바꾼다 |
 *
 * 둘 다 **화면을 닫지 않는다.** 금액을 다 채운 뒤에 오는 실패라, 닫으면 그 입력이 통째로 사라진다.
 */
class FareSplitEditViewModel(
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
    private val mode: FareSplitEditMode,
) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState

        /**
         * 적는 중.
         *
         * @property readOnlyReason null 이 아니면 **보낼 길이 닫혔지만 적은 값은 남아 있다**
         *   (409 `FARE_SPLIT_ALREADY_STARTED`).
         * @property existingFound 이미 그 방에 정산이 있었다 — 화면이 「지금 있는 정산 보기」를 준다.
         */
        data class Editing(
            val draft: FareSplitDraftState,
            val fareHint: String?,
            val submitting: Boolean = false,
            val errorMessage: String? = null,
            val readOnlyReason: String? = null,
            val existingFound: Boolean = false,
        ) : UiState

        /**
         * 애초에 열 수 없는 화면. [recoverable] 이 false 면 **이 방은 영구히 정산할 수 없다** —
         * 화면이 「다시 시도」를 권하지 않는다([FareSplitDraftGate.Blocked]).
         */
        data class Blocked(val message: String, val recoverable: Boolean) : UiState

        data class Error(val message: String) : UiState
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** 저장이 끝났다 — Route 가 이 신호로 되돌아간다(39 가 복귀하며 다시 읽는다). */
    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            _uiState.value = try {
                when (mode) {
                    FareSplitEditMode.START -> {
                        val ride = loadRide(partyId)
                        toState(startDraftGate(ride), fareHint = estimatedFareHint(ride))
                    }
                    FareSplitEditMode.EDIT -> {
                        val detail = repository.getFareSplit(partyId)
                        // 고치려고 왔는데 정산이 사라졌다(= 아무도 시작하지 않았다). 빈 폼을 그리지
                        // 않는다 — 수정 폼은 기존 값을 전제로 만들어졌고, 시작은 39 를 거쳐야 한다.
                        if (detail == null) {
                            UiState.Blocked("이 방에는 택시비 정산이 없어요", recoverable = true)
                        } else {
                            toState(editDraftGate(detail), fareHint = null)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: FareSplitException) {
                UiState.Error(fareSplitFailureMessage(e, FareSplitAction.LOAD))
            } catch (e: Exception) {
                UiState.Error(fareSplitGenericFailure(FareSplitAction.LOAD))
            }
        }
    }

    private fun toState(gate: FareSplitDraftGate, fareHint: String?): UiState = when (gate) {
        is FareSplitDraftGate.Ready -> UiState.Editing(draft = gate.state, fareHint = fareHint)
        is FareSplitDraftGate.Blocked -> UiState.Blocked(gate.message, gate.recoverable)
    }

    // ─── 입력 ───
    //
    // 전부 순수 함수에 맡긴다([FareSplitDraftState] 의 with* 확장) — 1/N 자동 채우기와
    // 자릿수 상한 같은 규칙이 ViewModel 과 화면 두 곳으로 갈리지 않게 한다.

    fun onFareChange(raw: String) = editDraft { it.withFare(raw) }

    fun onBankSelect(bank: Bank) = editDraft { it.withBank(bank) }

    fun onAccountChange(raw: String) = editDraft { it.withAccount(raw) }

    fun onAmountChange(publicId: String, raw: String) = editDraft { it.withAmount(publicId, raw) }

    fun onSplitEvenly() = editDraft { it.splitEvenly() }

    /**
     * 적은 값을 고친다. **읽기 전용이면 아무것도 하지 않는다** — 409 뒤에도 값은 남겨 두지만
     * 고칠 수는 없다(화면이 `enabled = false` 로 막지만, 상태 쪽에서도 막아 두 겹으로 둔다).
     *
     * 고칠 때마다 [UiState.Editing.errorMessage] 를 지운다 — 서버가 거절한 이유를 고치고 있는
     * 중인데 그 문구가 남아 있으면 **방금 고친 것이 또 거절된 것처럼** 보인다.
     */
    private fun editDraft(transform: (FareSplitDraftState) -> FareSplitDraftState) {
        _uiState.update { current ->
            val editing = current as? UiState.Editing ?: return@update current
            if (editing.readOnlyReason != null || editing.submitting) return@update current
            editing.copy(draft = transform(editing.draft), errorMessage = null)
        }
    }

    /**
     * 서버로 보낸다. **[submitBlockOf] 가 null 일 때만** 실제 호출이 나간다 — 화면이 버튼을 잠그지만
     * 여기서도 한 번 더 본다(잠금이 한 겹뿐이면 그 한 겹이 풀릴 때 조용히 400 이 된다).
     */
    fun submit() {
        val editing = _uiState.value as? UiState.Editing ?: return
        if (editing.submitting || editing.readOnlyReason != null) return
        if (submitBlockOf(editing.draft) != null) return

        val draft = editing.draft.toDraft()
        _uiState.value = editing.copy(submitting = true, errorMessage = null)

        viewModelScope.launch {
            try {
                when (mode) {
                    FareSplitEditMode.START -> repository.startFareSplit(partyId, draft)
                    FareSplitEditMode.EDIT -> repository.editFareSplit(partyId, draft)
                }
                // 응답으로 온 상세는 쓰지 않는다 — 되돌아가면 39 가 다시 읽는다
                // ([FareSplitDetailViewModel.onScreenStart]). 두 화면이 각자 상세를 들고 있으면
                // 한쪽만 낡는 자리가 생긴다.
                _saved.value = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: FareSplitException) {
                _uiState.update { current ->
                    val now = current as? UiState.Editing ?: return@update current
                    now.copy(
                        submitting = false,
                        errorMessage = fareSplitFailureMessage(e, submitAction()),
                        // 그사이 누가 「보냈어요」를 눌렀다 — 보낼 길만 닫고 **값은 남긴다.**
                        readOnlyReason = if (e.isAlreadyStarted) {
                            fareSplitFailureMessage(e, submitAction())
                        } else {
                            now.readOnlyReason
                        },
                        // 이미 있는 정산 — 서버가 「GET 으로 본다」라고 적어 둔 복구 가능한 실패다.
                        existingFound = now.existingFound || e.isAlreadyExists,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { current ->
                    val now = current as? UiState.Editing ?: return@update current
                    now.copy(submitting = false, errorMessage = fareSplitGenericFailure(submitAction()))
                }
            }
        }
    }

    private fun submitAction(): FareSplitAction = when (mode) {
        FareSplitEditMode.START -> FareSplitAction.START
        FareSplitEditMode.EDIT -> FareSplitAction.EDIT
    }

    companion object {
        fun factory(
            repository: FareSplitRepository,
            rideRepository: RideRepository,
            partyId: Long,
            mode: FareSplitEditMode,
        ) = viewModelFactory {
            initializer { FareSplitEditViewModel(repository, rideRepository::getPartyDetail, partyId, mode) }
        }
    }
}

/**
 * 40 정산 시작·수정 진입점.
 *
 * ViewModel key 에 **모드까지** 넣는 이유: 같은 방의 시작 폼과 수정 폼은 **다른 값을 들고 있다**
 * (하나는 빈 폼, 하나는 서버 값). key 가 같으면 시작하려다 되돌아온 뒤 수정으로 들어갔을 때
 * 빈 폼이 재사용돼 **기존 금액이 보이지 않는다.**
 */
@Composable
fun FareSplitEditRoute(
    repository: FareSplitRepository,
    rideRepository: RideRepository,
    partyId: Long?,
    mode: FareSplitEditMode,
    onBack: () -> Unit = {},
    /** 저장 성공. 39 상세로 되돌아가고, 그쪽이 복귀하며 다시 읽는다. */
    onSaved: () -> Unit = {},
) {
    if (partyId == null) {
        BackStateScaffold("택시비 정산", onBack) {
            ErrorBox(message = "정산 정보를 불러오지 못했어요", onRetry = onBack, actionLabel = "돌아가기")
        }
        return
    }

    val viewModel: FareSplitEditViewModel = viewModel(
        key = "fare-split-edit-$partyId-$mode",
        factory = FareSplitEditViewModel.factory(repository, rideRepository, partyId, mode),
    )
    val state by viewModel.uiState.collectAsState()
    val saved by viewModel.saved.collectAsState()

    LaunchedEffect(saved) {
        if (saved) onSaved()
    }

    when (val current = state) {
        FareSplitEditViewModel.UiState.Loading -> BackStateScaffold(editTitle(mode), onBack) {
            LoadingBox()
        }

        is FareSplitEditViewModel.UiState.Error -> BackStateScaffold(editTitle(mode), onBack) {
            ErrorBox(message = current.message, onRetry = viewModel::load)
        }

        is FareSplitEditViewModel.UiState.Blocked -> BackStateScaffold(editTitle(mode), onBack) {
            // 되돌릴 수 없는 막힘에는 「다시 시도」를 적지 않는다 — 다시 눌러도 같은 답이다
            // (`ErrorBox` 의 actionLabel 이 바로 이 경우를 위해 있다).
            ErrorBox(
                message = current.message,
                onRetry = if (current.recoverable) viewModel::load else onBack,
                actionLabel = if (current.recoverable) "다시 시도" else "돌아가기",
            )
        }

        is FareSplitEditViewModel.UiState.Editing -> FareSplitEditScreen(
            state = current.draft,
            fareHint = current.fareHint,
            submitting = current.submitting,
            errorMessage = current.errorMessage,
            readOnlyReason = current.readOnlyReason,
            onBack = onBack,
            onFareChange = viewModel::onFareChange,
            onBankSelect = viewModel::onBankSelect,
            onAccountChange = viewModel::onAccountChange,
            onAmountChange = viewModel::onAmountChange,
            onSplitEvenly = viewModel::onSplitEvenly,
            onSubmit = viewModel::submit,
            // 이미 있는 정산을 보러 간다 = 39 로 되돌아간다(그쪽이 복귀하며 읽는다).
            onSeeExisting = if (current.existingFound) onSaved else null,
        )
    }
}
