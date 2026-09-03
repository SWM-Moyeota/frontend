package com.moyeota.presentation.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.moyeota.domain.model.NewReport
import com.moyeota.domain.repository.ReportRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EmergencyViewModel(
    private val repository: ReportRepository,
    private val partyId: Long,
) : ViewModel() {

    /**
     * 신고 접수 → 통화 확인의 2단계 상태.
     * [reportId] 가 채워지면 화면이 「전화하셨나요?」 카드를 띄우고,
     * [done] 이 되면 Route 가 26 운행 중으로 되돌린다.
     */
    data class ReportState(
        val submitting: Boolean = false,
        val errorMessage: String? = null,
        val reportId: Long? = null,
        val confirmingCall: Boolean = false,
        val done: Boolean = false,
    )

    private val _state = MutableStateFlow(ReportState())
    val state: StateFlow<ReportState> = _state.asStateFlow()

    fun submit() {
        if (_state.value.submitting || _state.value.reportId != null) return // 중복 접수 차단
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, errorMessage = null) }
            try {
                // 신고자는 Bearer 토큰이 정한다 — NewReport 의 첫 인자는 이제 partyId 다 (22 보고서 §2).
                // 위치 권한 흐름이 아직 없어 좌표는 보내지 않는다 — 서버가 null 을 허용한다
                val id = repository.report(NewReport(partyId = partyId))
                _state.update { it.copy(submitting = false, reportId = id) }
            } catch (e: Exception) {
                // 서버는 탑승 중(IN_RIDE)이 아니면 신고를 거절한다. 사유를 본문에 담아주지 않아
                // 원인별 분기가 불가능하므로 일반 메시지로 안내한다 (01 보고서 7절).
                _state.update {
                    it.copy(submitting = false, errorMessage = "신고를 접수하지 못했어요. 탑승 중에만 신고할 수 있어요")
                }
            }
        }
    }

    fun confirmCall(called: Boolean) {
        val reportId = _state.value.reportId ?: return
        if (_state.value.confirmingCall) return
        viewModelScope.launch {
            _state.update { it.copy(confirmingCall = true) }
            // 통화 여부 기록 실패가 신고 자체를 무르게 하면 안 된다 — 조용히 넘기고 화면을 닫는다
            runCatching { repository.confirmCallResult(reportId, called) }
            _state.update { it.copy(confirmingCall = false, done = true) }
        }
    }

    companion object {
        fun factory(repository: ReportRepository, partyId: Long) = viewModelFactory {
            initializer { EmergencyViewModel(repository, partyId) }
        }
    }
}

// 27 긴급 신고 — 신고 접수 + 통화 확인 진입점. partyId 없으면 기존 더미 화면 유지.
@Composable
fun EmergencyRoute(
    repository: ReportRepository,
    partyId: Long?,
    rideSummary: String = "부산대 정문 → 서면역 · 12가 3456",
    onBack: () -> Unit = {},
) {
    if (partyId == null) {
        EmergencyScreen(rideSummary = rideSummary, onBack = onBack, onReportSubmitted = { _, _ -> onBack() })
        return
    }

    val viewModel: EmergencyViewModel = viewModel(
        key = "report-$partyId",
        factory = EmergencyViewModel.factory(repository, partyId),
    )
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.done) {
        if (state.done) onBack()
    }

    EmergencyScreen(
        rideSummary = rideSummary,
        submitting = state.submitting,
        submitErrorMessage = state.errorMessage,
        reportId = state.reportId,
        confirmingCall = state.confirmingCall,
        onBack = onBack,
        // 사유·상세는 서버 ReportRequest 에 담을 자리가 없어 아직 전송되지 않는다
        onReportSubmitted = { _, _ -> viewModel.submit() },
        onConfirmCallResult = viewModel::confirmCall,
    )
}
