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
 * 3초 홀드 완료 → ①신고 저장 발사(reportEmergency — 실패해도 플래그만, 통화가 최우선)
 * ②UI 가 즉시 112 다이얼(ACTION_DIAL) → 앱 복귀(ON_RESUME) → "실제로 통화하셨나요?" 다이얼로그
 * → confirmEmergencyCall(called) 저장 성공 시 done → 26 복귀
 */
class EmergencyViewModel(
    private val repository: RideRepository,
    private val partyId: Long?,
) : ViewModel() {

    data class UiState(
        val dialLaunched: Boolean = false,      // 3초 홀드 완료 — 112 다이얼 발사됨
        val reportSaveFailed: Boolean = false,  // 신고 저장 실패 (플래그만 — 통화 흐름은 계속)
        val showCallConfirm: Boolean = false,   // 다이얼 복귀 후 통화 여부 다이얼로그
        val confirmSaving: Boolean = false,
        val confirmError: String? = null,       // call-result 저장 실패 안내 (재선택 가능)
        val done: Boolean = false,              // call-result 저장 완료 → 26 복귀
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** 3초 홀드 완료 — 신고 저장을 발사한다. 실패해도 다이얼은 이미 열리므로 플래그만 남긴다. */
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

    /** 다이얼에서 앱 복귀(ON_RESUME) — 홀드 이후라면 통화 여부 다이얼로그를 띄운다 */
    fun onResumedFromDial() {
        val current = _uiState.value
        if (current.dialLaunched && !current.done && !current.showCallConfirm) {
            _uiState.value = current.copy(showCallConfirm = true)
        }
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
    rideSummary: String = "부산대 정문 → 서면역 · 12가 3456",
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
        onBack = onBack,
        onHoldCompleted = {
            // ①신고 저장 발사 (실패해도 플래그만) ②즉시 112 다이얼 — 통화가 최우선
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
 *
 * Safety500/600 색상은 이 화면(신고) 전용.
 */
@Composable
fun EmergencyScreen(
    rideSummary: String = "부산대 정문 → 서면역 · 12가 3456",
    dialLaunched: Boolean = false,
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

            Spacer(Modifier.height(22.dp))
            // 지금 타고 있는 차 카드
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
 * "112와 실제로 통화하셨나요?" — 다이얼 복귀 후 통화 여부 확인.
 * 바깥 탭·back 으로 닫히지 않는다 — 반드시 둘 중 하나를 선택해야 26 으로 복귀.
 */
@Composable
private fun CallConfirmDialog(
    saving: Boolean,
    errorText: String?,
    onCalled: () -> Unit,
    onNotCalled: () -> Unit,
) {
    Dialog(
        onDismissRequest = { /* 선택 전에는 닫을 수 없다 */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
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
    EmergencyScreen()
}
