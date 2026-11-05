package com.moyeota.presentation.feature.mypage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.moyeota.domain.model.NoShowObjectionException
import com.moyeota.domain.model.NoShowRecord
import com.moyeota.domain.model.ObjectionStatus
import com.moyeota.domain.repository.NoShowRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 38 노쇼 기록·이의 신청의 상태·조회·제출.
 *
 * 목록과 입력값을 화면(`rememberSaveable`)이 아니라 여기에 두는 이유는 36 프로필 수정과 같다:
 * **사유를 쓰는 중에 화면이 회전하면** `rememberSaveable` 로는 입력이 복원되지만 「어느 방에 대한
 * 이의였는지」와 「보내는 중이었는지」가 함께 보존되지 않아, 복원된 글이 다른 방으로 날아가거나
 * 같은 이의를 두 번 보내게 된다(방마다 한 번뿐이라 두 번째는 409 다).
 */
class NoShowRecordsViewModel(private val repository: NoShowRepository) : ViewModel() {

    internal data class UiState(
        /** 서버가 준 그대로. 노쇼 시각 최신순 최대 20개이고 **페이징이 없다.** */
        val records: List<NoShowRecord> = emptyList(),
        val loading: Boolean = false,
        /**
         * 조회가 실패했는가. 사유를 구분하지 않는다 — 네트워크든 5xx 든 사용자가 할 일은
         * 「다시 시도」 하나다.
         */
        val failed: Boolean = false,
        /** 이의 신청 시트가 열려 있는 방. null 이면 닫혀 있다. */
        val objectionTarget: Long? = null,
        val reason: String = "",
        val submitting: Boolean = false,
        val objectionFailure: NoShowObjectionFailure? = null,
        /** 201 을 받았다 — 목록 위 안내. 다음 이의를 열 때 지운다. */
        val objectionSent: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    internal val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // 진행 중인 조회. 같은 요청이 겹치지 않게 한 곳으로 모은다.
    private var loadJob: Job? = null

    init {
        refresh()
    }

    /** 목록을 처음부터 다시 받는다. 진입과 「다시 시도」가 같은 경로를 쓴다. */
    fun refresh() {
        if (loadJob?.isActive == true) return
        _uiState.update { it.copy(loading = true, failed = false) }
        loadJob = viewModelScope.launch {
            runCatching { repository.getMyNoShows() }
                .onSuccess { records ->
                    _uiState.update { it.copy(records = records, loading = false, failed = false) }
                }
                .onFailure {
                    // 실패해도 이전 목록은 남긴다 — 보고 있던 화면이 비지 않게.
                    _uiState.update { state -> state.copy(loading = false, failed = true) }
                }
        }
    }

    /**
     * 이의 신청 시트를 연다.
     *
     * **직전 실패와 직전 성공 안내를 함께 지운다.** 남겨 두면 다른 방의 시트에 「이미 이의를
     * 냈어요」가 그대로 떠 있고, 그 방은 아직 낸 적이 없다.
     */
    fun openObjection(partyId: Long) {
        _uiState.update {
            it.copy(
                objectionTarget = partyId,
                reason = "",
                objectionFailure = null,
                objectionSent = false,
            )
        }
    }

    /** 시트를 닫는다. **보내는 중에는 닫지 않는다** — 응답을 버리면 보냈는지 알 수 없게 된다. */
    fun dismissObjection() {
        if (_uiState.value.submitting) return
        _uiState.update { it.copy(objectionTarget = null, reason = "", objectionFailure = null) }
    }

    /**
     * 사유를 고친다. **입력을 trim 하지 않는다** — 줄바꿈과 공백은 글을 쓰는 중의 정상 상태이고,
     * 입력 중에 깎으면 커서가 튄다. 길이 판정만 trim 한 값으로 한다([objectionReasonUi]) 그리고
     * 보낼 때 한 번 trim 한다(서버가 그렇게 검사한다).
     *
     * 한도의 2배에서 자르는 것은 화면이 한다(붙여넣기 폭주 방어) — 여기서도 한 번 더 잘라 두면
     * 어느 쪽이 기준인지 흐려진다.
     *
     * 고치는 순간 직전 실패를 지운다 — 빨간 배너를 들고 타이핑하게 두지 않는다(37 과 같은 규칙).
     */
    fun onReasonChange(raw: String) {
        _uiState.update { it.copy(reason = raw, objectionFailure = null) }
    }

    /**
     * `POST /matching/rooms/{partyId}/no-show-objection` 로 사유를 보낸다.
     *
     * ### 보내기 전에 앱이 막는 것
     * [objectionReasonUi] 가 false 를 주면 **요청을 만들지 않는다.** 공백만 쓴 글과 500자를 넘긴
     * 글은 서버에서 400 `INVALID_REQUEST` 인데, 그 응답 문구로는 사용자가 무엇을 고칠지 모른다.
     * 화면이 이미 버튼을 잠그고 있지만 여기서도 본다 — 버튼이 늘면 그쪽만 고치고 여기를 잊는다.
     *
     * ### 성공 뒤 — 그 줄을 고치고 **그리고** 다시 받는다 ★
     * 201 에 **본문이 없어** 접수된 이의의 상태를 응답에서 알 길이 없다. 그래서 둘을 함께 한다:
     * - 그 줄을 [ObjectionStatus.PENDING] 으로 고친다 — **이건 지어낸 값이 아니다.** 방금 접수된
     *   이의의 상태가 PENDING 이다(`ObjectionStatus` KDoc). 이걸 안 하면 재조회가 실패했을 때
     *   버튼이 그대로 남아(노출 조건이 `objectionStatus == null`) 사용자가 한 번만 낼 수 있는
     *   이의를 다시 눌러 409 를 받는다.
     * - 그리고 목록을 다시 받는다([refresh]) — 서버 값이 최종 근거다
     *   (`NoShowRepository.submitObjection` KDoc 이 요구하는 재조회가 이것이다). 성공하면 지역
     *   수정은 같은 값으로 덮이고, 실패해도 위의 수정이 남아 버튼이 되살아나지 않는다.
     *
     * ### 409 `ALREADY_OBJECTED` 는 **PENDING 으로 고치지 않는다**
     * 「이미 냈다」는 「검토 중이다」가 아니다 — 그 이의는 이미 기각됐을 수도 있다. 그 줄을 손으로
     * PENDING 으로 바꾸면 **기각된 사람에게 「이의 검토 중」이라고 말하게** 되고, 그 사람은 오지 않을
     * 결과를 기다린다(도메인 `isAlreadyObjected` KDoc 이 못 박은 함정이다). 상태는 재조회가 준다.
     *
     * ### 서버가 거절한 실패는 전부 「내 목록이 낡았다」는 신호다
     * 403·404·409 는 모두 내가 들고 있는 줄이 서버와 다를 때만 나온다(화면이 그 조건에서만 버튼을
     * 띄우기 때문이다). 그래서 [NoShowObjectionFailure.isTerminal] 인 실패는 문구를 띄우면서
     * **목록을 다시 받는다** — 그러면 무효가 된 줄·이미 낸 줄이 제 상태로 바뀐다.
     */
    fun submitObjection() {
        val state = _uiState.value
        val partyId = state.objectionTarget ?: return
        if (!objectionReasonUi(state.reason, state.submitting).submitEnabled) return

        _uiState.update { it.copy(submitting = true, objectionFailure = null) }
        viewModelScope.launch {
            runCatching { repository.submitObjection(partyId, state.reason.trim()) }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            records = it.records.markPending(partyId),
                            submitting = false,
                            objectionTarget = null,
                            reason = "",
                            objectionFailure = null,
                            objectionSent = true,
                        )
                    }
                    // 서버 값으로 맞춘다. 실패해도 위 수정이 남아 버튼은 돌아오지 않는다.
                    refresh()
                }
                .onFailure { failure ->
                    val reason = failure.toNoShowObjectionFailure()
                    _uiState.update { it.copy(submitting = false, objectionFailure = reason) }
                    // 서버가 거절한 사유는 전부 「목록이 낡았다」는 신호다 — 사실을 다시 받아 온다.
                    // 글자 수(400)·연결 실패는 목록과 무관하므로 다시 받지 않는다.
                    if (reason.isTerminal) refresh()
                }
        }
    }

    companion object {
        fun factory(repository: NoShowRepository) = viewModelFactory {
            initializer { NoShowRecordsViewModel(repository) }
        }
    }
}

/**
 * 그 방의 줄을 「이의 검토 중」으로 고친다. **`isVoided` 는 건드리지 않는다** — 무효는 운영자가
 * 정하는 값이고, 이의를 냈다는 사실만으로 무효를 앞질러 말하면 그게 거짓 문구다.
 */
private fun List<NoShowRecord>.markPending(partyId: Long): List<NoShowRecord> = map { record ->
    if (record.partyId == partyId) {
        record.copy(objectionStatus = ObjectionStatus.PENDING)
    } else {
        record
    }
}

/**
 * 도메인 예외 → 화면이 쓰는 사유.
 *
 * **이 함수가 서버 코드 문자열을 아는 유일한 경계다.** 문구 쪽([noShowObjectionFailureMessage])은
 * enum 만 보므로, 서버가 `MatchingErrorCode` 를 늘리거나 예외 모양이 바뀌어도 문구는 흔들리지
 * 않고 여기 한 곳만 고치면 된다.
 *
 * 예외가 아닌 실패(연결 거부·타임아웃·본문 파싱 실패)와 **모르는 코드**는 전부
 * [NoShowObjectionFailure.UNKNOWN] 이다 — 상태 코드로 사유를 추측하지 않는다. 이 엔드포인트의
 * 409 는 두 가지 뜻이라 상태로 고르면 반드시 절반이 틀린 말이 된다.
 */
internal fun Throwable.toNoShowObjectionFailure(): NoShowObjectionFailure {
    val e = this as? NoShowObjectionException ?: return NoShowObjectionFailure.UNKNOWN
    return when {
        e.isInvalidReason -> NoShowObjectionFailure.INVALID_REASON
        e.isNotNoShow -> NoShowObjectionFailure.NOT_NO_SHOW
        e.isAlreadyObjected -> NoShowObjectionFailure.ALREADY_OBJECTED
        e.isNotMember -> NoShowObjectionFailure.NOT_MEMBER
        e.isPartyNotFound -> NoShowObjectionFailure.PARTY_NOT_FOUND
        else -> NoShowObjectionFailure.UNKNOWN
    }
}

/**
 * 38 노쇼 기록 — `GET /api/v1/matching/no-shows` 진입점.
 *
 * 진입 즉시 목록을 받는다([NoShowRecordsViewModel] 의 `init`). 실패하면 화면이 재시도를 그린다.
 *
 * 35 마이페이지의 제재 배너와 **다른 값을 본다.** 배너는 프로필 조회의 `noShowCount`·
 * `restrictedUntil` 이고 이 화면은 노쇼 **목록**이다 — 둘은 서로를 대체하지 않는다:
 * 무효가 된 노쇼와 30일이 지나 횟수에서 빠진 노쇼도 목록에는 남는다. 그래서 「노쇼 0회」인
 * 사용자에게도 이 화면에 줄이 있을 수 있고, 반대로 제한이 걸려 있는데 목록 조회가 실패할 수도
 * 있다(그때 배너는 35 에 남아 있다).
 */
@Composable
fun NoShowRecordsRoute(
    repository: NoShowRepository,
    onBack: () -> Unit = {},
) {
    val viewModel: NoShowRecordsViewModel =
        viewModel(factory = NoShowRecordsViewModel.factory(repository))
    val state by viewModel.uiState.collectAsState()

    NoShowRecordsScreen(
        records = state.records,
        loading = state.loading,
        failed = state.failed,
        objectionTarget = state.objectionTarget,
        reason = state.reason,
        submitting = state.submitting,
        objectionFailure = state.objectionFailure,
        objectionSent = state.objectionSent,
        onObjectionOpen = viewModel::openObjection,
        onObjectionDismiss = viewModel::dismissObjection,
        onReasonChange = viewModel::onReasonChange,
        onObjectionSubmit = viewModel::submitObjection,
        onRetry = viewModel::refresh,
        onBack = onBack,
    )
}
