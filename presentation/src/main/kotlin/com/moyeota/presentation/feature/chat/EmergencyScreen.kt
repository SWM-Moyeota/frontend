package com.moyeota.presentation.feature.chat

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.moyeota.core.designsystem.component.NavigationBarSpacer
import com.moyeota.core.designsystem.component.StatusBarSpacer
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.domain.repository.RideRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

// 와이어프레임 색 (core token 미정의 — 화면 재현용)
private val EmergencyBg = Color(0xFFF5F7FA)
private val EmMuteGray = Color(0xFF8A93A0)
private val EmAshGray = Color(0xFF9AA1AC)
private val EmTextMute = Color(0xFF6B7280)
private val EmCardShadow = Color(0x1A1B2A4A)

/**
 * 27 · 긴급 신고 ViewModel [S17]
 *
 * 새 흐름 (사유 선택 없음 — 서버 ReportRequest 는 partyId/위치뿐):
 * 3초 홀드 완료 → ①신고 저장 발사(reportEmergency — 실패해도 **통화는 막지 않는다**)
 * ②UI 가 즉시 112 다이얼(ACTION_DIAL) → 앱 복귀(ON_RESUME) → "실제로 통화하셨나요?" 다이얼로그
 * → confirmEmergencyCall(called) 저장 성공 시 done → 26 복귀
 *
 * **실패를 숨기지 않는다.** 신고 저장이 실패하면 화면이 그 사실을 배너로 말하고 재시도를 내놓는다
 * ([UiState.reportSaveFailed]) — 예전엔 이 플래그를 아무도 읽지 않아, 지하 주차장·터널에서 저장이
 * 실패해도 버튼 문구만 「112 통화 화면으로 연결했어요」로 바뀌었다. 사용자는 위치·운행 정보가
 * 운영팀에 전달됐다고 믿는다(화면 상단이 그렇게 약속한다).
 */
class EmergencyViewModel(
    private val repository: RideRepository,
    private val partyId: Long?,
) : ViewModel() {

    data class UiState(
        val dialLaunched: Boolean = false,      // 3초 홀드 완료 — 112 다이얼 발사됨
        /**
         * 신고 저장(`reportEmergency`) 실패. **통화 흐름은 멈추지 않는다** — 통화가 최우선이라는
         * 기존 설계는 그대로고, 달라진 건 이 사실을 **화면이 말한다**는 것뿐이다.
         */
        val reportSaveFailed: Boolean = false,
        /** 신고 저장 재시도 중 — 배너의 버튼을 잠그는 데만 쓴다 */
        val reportRetrying: Boolean = false,
        val showCallConfirm: Boolean = false,   // 다이얼 복귀 후 통화 여부 다이얼로그
        val confirmSaving: Boolean = false,
        val confirmError: String? = null,       // call-result 저장 실패 안내 (재선택 가능)
        /**
         * 통화 여부를 **아직 못 보냈고, 다이얼로그는 닫아 둔** 상태(「나중에」).
         *
         * 통화 여부는 운영팀 참고값이라 저장이 안 된다고 사용자를 잡아 둘 이유가 없다 — 예전에는
         * 네트워크가 없으면 두 버튼이 둘 다 실패하고 다이얼로그가 back·바깥 탭 둘 다 막아서,
         * 운행 중 화면·채팅으로 **돌아갈 길이 전혀 없었다**(실기 QA). 닫아 둔 뒤에도 사실은 남겨
         * 두고(배너 + 「다시 선택」) 다시 고를 수 있게 한다.
         */
        val callConfirmPending: Boolean = false,
        val done: Boolean = false,              // call-result 저장 완료 → 26 복귀
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /**
     * 3초 홀드 완료 — 신고 저장을 발사한다. 실패해도 다이얼은 이미 열리므로 **통화를 막지 않고**
     * [UiState.reportSaveFailed] 만 세운다. 그 플래그는 화면의 배너가 읽는다.
     */
    fun onEmergencyHold() {
        if (_uiState.value.dialLaunched) return
        _uiState.value = _uiState.value.copy(dialLaunched = true)
        viewModelScope.launch {
            try {
                repository.reportEmergency(partyId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(reportSaveFailed = true)
            }
        }
    }

    /**
     * 운영팀 전달(신고 저장) **재시도**. 통화와 무관하게 언제든 누를 수 있다 —
     * 지하 주차장·터널을 벗어난 뒤 한 번 더 보내는 게 이 버튼의 쓸모다.
     */
    fun retryReport() {
        val current = _uiState.value
        if (current.reportRetrying || !current.reportSaveFailed) return
        _uiState.value = current.copy(reportRetrying = true)
        viewModelScope.launch {
            _uiState.value = try {
                repository.reportEmergency(partyId)
                _uiState.value.copy(reportRetrying = false, reportSaveFailed = false)
            } catch (e: Exception) {
                _uiState.value.copy(reportRetrying = false, reportSaveFailed = true)
            }
        }
    }

    /**
     * 다이얼에서 앱 복귀(ON_RESUME) — 홀드 이후라면 통화 여부 다이얼로그를 띄운다.
     *
     * 「나중에」로 닫아 둔 뒤([UiState.callConfirmPending])에는 **다시 띄우지 않는다** — 복귀마다
     * 되살아나면 닫을 길을 만든 의미가 없다(앱을 전환할 때마다 같은 자리에 갇힌다).
     * 다시 고르는 길은 화면의 「다시 선택」이다([reopenCallConfirm]).
     */
    fun onResumedFromDial() {
        val current = _uiState.value
        if (current.dialLaunched && !current.done && !current.showCallConfirm && !current.callConfirmPending) {
            _uiState.value = current.copy(showCallConfirm = true)
        }
    }

    /**
     * 「나중에」 — 통화 여부를 **보류**하고 다이얼로그를 닫는다. 저장 중에는 무시한다(응답을 기다린다).
     * 성공 경로는 건드리지 않는다 — 저장이 되면 [UiState.done] 으로 26 에 돌아간다.
     *
     * [UiState.confirmError] 는 **지우지 않는다.** 그 값이 다이얼로그의 탈출구를 여는 조건이라,
     * 지워 버리면 「다시 선택」으로 열었을 때 다시 갇힌다(한 번 더 실패해야 탈출구가 돌아온다).
     */
    fun dismissCallConfirm() {
        val current = _uiState.value
        if (current.confirmSaving) return
        _uiState.value = current.copy(showCallConfirm = false, callConfirmPending = true)
    }

    /** 보류해 둔 통화 여부를 다시 고른다 — 화면 배너의 「다시 선택」 */
    fun reopenCallConfirm() {
        val current = _uiState.value
        if (current.done) return
        _uiState.value = current.copy(showCallConfirm = true, callConfirmPending = false)
    }

    /** 「통화했어요」/「통화 안 했어요」 선택 — 저장 성공 시 done, 실패 시 안내 후 재선택 */
    fun onCallConfirm(called: Boolean) {
        if (_uiState.value.confirmSaving) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(confirmSaving = true, confirmError = null)
            try {
                repository.confirmEmergencyCall(called)
                _uiState.value = _uiState.value.copy(
                    confirmSaving = false,
                    showCallConfirm = false,
                    callConfirmPending = false,
                    confirmError = null,
                    done = true,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    confirmSaving = false,
                    confirmError = "통화 여부 저장에 실패했어요. 다시 선택해 주세요",
                )
            }
        }
    }

    companion object {
        fun factory(repository: RideRepository, partyId: Long?) = viewModelFactory {
            initializer { EmergencyViewModel(repository, partyId) }
        }
    }
}

/**
 * 27 · 긴급 신고 진입점 — 서버 연동(신고 저장 + 통화 여부)
 *
 * 이동:
 * - 뒤로(X) → 26 운행 중 (onBack)
 * - 3초 홀드 완료 → 신고 저장 발사 + 즉시 112 다이얼(ACTION_DIAL — 발신은 사용자)
 * - 다이얼 복귀 → 통화 여부 다이얼로그 → 저장 성공 시 26 복귀 (onReportSubmitted)
 */
@Composable
fun EmergencyRoute(
    repository: RideRepository,
    partyId: Long?,
    /**
     * 「지금 타고 있는 차」 카드에 보일 「출발지 → 목적지」. null 이면 카드를 숨긴다(진행 중 운행을 모를 때).
     * 번호판은 모델에 없어 넣지 않는다.
     */
    rideSummary: String? = null,
    onBack: () -> Unit = {},
    onReportSubmitted: () -> Unit = {},
) {
    val viewModel: EmergencyViewModel =
        viewModel(factory = EmergencyViewModel.factory(repository, partyId))
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 112 다이얼에서 앱 복귀(ON_RESUME) 감지 — 홀드 이후 첫 복귀에 통화 여부 다이얼로그
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onResumedFromDial()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // call-result 저장 완료 → 26 운행 중 복귀
    LaunchedEffect(state.done) {
        if (state.done) onReportSubmitted()
    }

    EmergencyScreen(
        rideSummary = rideSummary,
        dialLaunched = state.dialLaunched,
        // 운영팀 전달 실패·통화 여부 보류는 **숨기지 않고** 비차단 배너로 알린다 (통화는 막지 않는다)
        reportSaveFailed = state.reportSaveFailed,
        reportRetrying = state.reportRetrying,
        callConfirmPending = state.callConfirmPending,
        onRetryReport = viewModel::retryReport,
        onReopenCallConfirm = viewModel::reopenCallConfirm,
        onBack = onBack,
        onHoldCompleted = {
            // ①신고 저장 발사 (실패해도 통화는 계속) ②즉시 112 다이얼 — 통화가 최우선
            viewModel.onEmergencyHold()
            runCatching {
                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")))
            }
        },
    )

    if (state.showCallConfirm) {
        CallConfirmDialog(
            saving = state.confirmSaving,
            errorText = state.confirmError,
            onCalled = { viewModel.onCallConfirm(true) },
            onNotCalled = { viewModel.onCallConfirm(false) },
            onLater = viewModel::dismissCallConfirm,
        )
    }
}

/**
 * 27 · 긴급 신고 [S17] — 순수 UI
 *
 * - 뒤로(X) → 26 운행 중 (onBack)
 * - 「3초간 길게 눌러 신고」 3초 유지 성공 → onHoldCompleted (신고 발사 + 112 다이얼)
 *   · 3초 롱프레스 유지 실패 시 미전송 (오작동 방지)
 * - 사유 선택 없음 — 서버가 사유를 받지 않는다 (partyId/위치만 전송)
 * - 「지금 타고 있는 차」 카드는 [rideSummary] 가 있을 때만 보인다(번호판 표기 없음)
 * - [reportSaveFailed]·[callConfirmPending] 이면 상단에 **비차단 배너**가 뜬다 — 통화는 막지 않는다
 *
 * Safety500/600 색상은 이 화면(신고) 전용.
 */
@Composable
fun EmergencyScreen(
    rideSummary: String? = null,
    dialLaunched: Boolean = false,
    /**
     * 운영팀 전달(신고 저장)이 실패했다. 화면 상단이 「현재 위치와 운행 정보가 운영팀에 전달되고」라고
     * 약속하므로 **이 실패를 숨기면 거짓말이 된다** — 지하 주차장·터널에서 실제로 일어난다.
     */
    reportSaveFailed: Boolean = false,
    reportRetrying: Boolean = false,
    /** 통화 여부를 「나중에」로 보류했다 — 다시 고를 수 있게 안내만 남긴다 */
    callConfirmPending: Boolean = false,
    onRetryReport: () -> Unit = {},
    onReopenCallConfirm: () -> Unit = {},
    onBack: () -> Unit = {},
    onHoldCompleted: () -> Unit = {},
) {
    var holding by remember { mutableStateOf(false) }

    // 다이얼 발사 후에는 중복 홀드 방지
    val holdEnabled = !dialLaunched
    val currentHoldCompleted by rememberUpdatedState(onHoldCompleted)

    Column(modifier = Modifier.fillMaxSize().background(EmergencyBg)) {
        StatusBarSpacer()
        // 닫기(X) → 26 운행 중
        Box(
            modifier = Modifier
                .padding(start = 24.dp, top = 14.dp)
                .size(28.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onBack() },
            contentAlignment = Alignment.CenterStart,
        ) {
            CloseIcon()
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = "괜찮으세요?",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = MoyeotaColor.InkPrimary,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "신고하면 현재 위치와 운행 정보가 운영팀에 전달되고,\n바로 112 통화 화면으로 연결돼요",
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = EmTextMute,
            )

            // 운영팀 전달 실패 — **이 실패가 통화를 막지 않는다**는 것만 말한다. 이 배너 때문에
            // 사용자가 통화를 멈추면 더 나쁘다. 재시도는 터널·지하를 벗어난 뒤를 위한 길이다.
            //
            // 「112 통화는 정상 진행됩니다」라고 **단정하지 않는다.** 앱은 통화 여부를 모른다 —
            // `ACTION_DIAL` 로 다이얼러를 열 뿐 발신은 사용자가 하고, 이 화면은 10초 뒤에
            // 「112와 실제로 통화하셨나요?」라고 되묻는다. 모른다고 적어 둔 값을 배너가
            // 「정상 진행」으로 말하면, 통화가 안 된 사용자에게 됐다고 알리는 꼴이다.
            if (reportSaveFailed) {
                Spacer(Modifier.height(18.dp))
                EmergencyNotice(
                    text = "운영팀에 전달하지 못했어요. 112 통화에는 지장이 없어요\n" +
                        "통신이 되는 곳에서 다시 시도해 주세요",
                    actionLabel = if (reportRetrying) "보내는 중…" else "다시 시도",
                    actionEnabled = !reportRetrying,
                    onAction = onRetryReport,
                )
            }

            // 통화 여부를 「나중에」로 닫아 둔 상태 — 사실은 남기고 다시 고를 길을 준다
            if (callConfirmPending) {
                Spacer(Modifier.height(18.dp))
                EmergencyNotice(
                    text = "통화 여부를 아직 보내지 못했어요. 운영팀 참고용이라 안 보내도 괜찮아요",
                    actionLabel = "다시 선택",
                    actionEnabled = true,
                    onAction = onReopenCallConfirm,
                )
            }

            if (rideSummary != null) {
                Spacer(Modifier.height(22.dp))
                // 지금 타고 있는 차 카드 — 운행 정보를 모르면 숨긴다
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(18.dp), spotColor = EmCardShadow)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MoyeotaColor.SurfaceCanvas)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    Text(
                        text = "지금 타고 있는 차",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = EmMuteGray,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = rideSummary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MoyeotaColor.InkPrimary,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // 하단 고정: 안내 + 신고 버튼 + 푸터
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(
                text = "3초를 채우면 즉시 112 통화 화면이 열려요",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = EmAshGray,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(10.dp))
            // 3초 롱프레스 신고 버튼 — Safety500 (이 화면 전용)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        when {
                            !holdEnabled -> MoyeotaColor.SurfaceSoft
                            holding -> MoyeotaColor.Safety600
                            else -> MoyeotaColor.Safety500
                        },
                    )
                    .pointerInput(holdEnabled) {
                        if (holdEnabled) {
                            detectTapGestures(
                                onPress = {
                                    holding = true
                                    // 3초 유지 실패(중도 해제) 시 미전송 — 오작동 방지
                                    val releasedEarly = withTimeoutOrNull(3_000L) { tryAwaitRelease() }
                                    holding = false
                                    if (releasedEarly == null) {
                                        currentHoldCompleted() // 신고 발사 + 112 다이얼
                                        tryAwaitRelease()
                                    }
                                },
                            )
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = when {
                        !holdEnabled -> "112 통화 화면으로 연결했어요"
                        holding -> "계속 누르고 계세요…"
                        else -> "3초간 길게 눌러 신고"
                    },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (holdEnabled) MoyeotaColor.TextOnDark else MoyeotaColor.TextAsh,
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "허위 신고 시 이용이 제한될 수 있어요",
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                color = EmAshGray,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(30.dp))
        }
        // 홈 인디케이터 목업 대신 실제 제스처 인셋 여백 (main 병합 — Bars.kt 정리 참조)
        NavigationBarSpacer()
    }
}

/**
 * 비차단 안내 배너 — 신고 저장 실패·통화 여부 보류를 **감추지 않고** 알린다.
 *
 * 다이얼로그가 아니라 배너인 게 요점이다: 이 화면의 본업(112 통화)을 한 순간도 막지 않는다.
 * 문구는 「무슨 일이 일어났고 / 통화는 어떻게 되는지 / 사용자가 할 수 있는 일」 순서로 적는다.
 */
@Composable
private fun EmergencyNotice(
    text: String,
    actionLabel: String,
    actionEnabled: Boolean,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MoyeotaColor.Danger50)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MoyeotaColor.Danger600,
        )
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (actionEnabled) MoyeotaColor.SurfaceCanvas else MoyeotaColor.SurfaceSoft)
                .clickable(enabled = actionEnabled) { onAction() }
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = actionLabel,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (actionEnabled) MoyeotaColor.Danger600 else MoyeotaColor.TextAsh,
            )
        }
    }
}

/**
 * "112와 실제로 통화하셨나요?" — 다이얼 복귀 후 통화 여부 확인.
 *
 * 성공 경로는 예전과 같다: 둘 중 하나를 고르고 저장이 되면 26 으로 복귀한다. 바깥 탭으로는 여전히
 * 닫히지 않는다 — 실수로 흘려 보내지 않게.
 *
 * **저장이 실패한 뒤에는 탈출구를 연다**([errorText] != null → back 허용 + 「나중에」).
 * 예전에는 네트워크가 없으면 두 버튼이 둘 다 실패해 `done` 이 서지 않고, back·바깥 탭·X 버튼(이
 * 다이얼로그에 가려진다)이 전부 막혀 **운행 중 화면·채팅으로 돌아갈 길이 없었다**(실기 QA).
 * 통화 여부는 운영팀 참고값이라 그걸로 사용자를 잡아 둘 근거가 없다 — 보류해 두고 배너로 남긴다.
 */
@Composable
private fun CallConfirmDialog(
    saving: Boolean,
    errorText: String?,
    onCalled: () -> Unit,
    onNotCalled: () -> Unit,
    onLater: () -> Unit,
) {
    // 저장이 한 번이라도 실패한 뒤에만 열어 준다. 저장 중에는 응답을 기다린다(연타 방지).
    val escapable = errorText != null && !saving
    Dialog(
        onDismissRequest = { if (escapable) onLater() },
        properties = DialogProperties(
            dismissOnBackPress = escapable,
            // 바깥 탭은 계속 막는다 — 실패했다고 실수로 흘려 보낼 길까지 열 이유는 없다
            dismissOnClickOutside = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MoyeotaColor.SurfaceCanvas)
                .padding(horizontal = 22.dp, vertical = 24.dp),
        ) {
            Text(
                text = "112와 실제로 통화하셨나요?",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MoyeotaColor.InkPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "실제 통화 여부는 운영팀 대응에 사용돼요",
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                color = EmTextMute,
            )
            if (errorText != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = errorText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MoyeotaColor.Safety500,
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                // 「통화 안 했어요」 — 보조
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MoyeotaColor.SurfaceSoft)
                        .clickable(enabled = !saving) { onNotCalled() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "통화 안 했어요",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (saving) MoyeotaColor.TextAsh else MoyeotaColor.InkPrimary,
                    )
                }
                Spacer(Modifier.width(10.dp))
                // 「통화했어요」 — 주 액션 (Safety500 — 신고 화면 전용색)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (saving) MoyeotaColor.SurfaceSoft else MoyeotaColor.Safety500)
                        .clickable(enabled = !saving) { onCalled() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (saving) "저장 중…" else "통화했어요",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (saving) MoyeotaColor.TextAsh else MoyeotaColor.TextOnDark,
                    )
                }
            }
            // 저장이 실패한 뒤의 탈출구. 성공 경로에는 나타나지 않는다 — 평상시엔 둘 중 하나를 고르는 게 맞다.
            if (escapable) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "나중에 하기",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = EmTextMute,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onLater() },
                )
            }
        }
    }
}

// ─── 아이콘 (material-icons 미사용 — Canvas 직접 드로잉) ─────────────────────

@Composable
private fun CloseIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 2.2.dp.toPx()
        drawLine(MoyeotaColor.InkPrimary, Offset(w * 0.18f, h * 0.18f), Offset(w * 0.82f, h * 0.82f), stroke, StrokeCap.Round)
        drawLine(MoyeotaColor.InkPrimary, Offset(w * 0.82f, h * 0.18f), Offset(w * 0.18f, h * 0.82f), stroke, StrokeCap.Round)
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun EmergencyScreenPreview() {
    EmergencyScreen(rideSummary = "학교 정문 → 역 광장")
}
