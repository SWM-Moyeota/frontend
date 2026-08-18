package com.moyeota.presentation.feature.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeota.core.designsystem.component.MoyeotaTextField
import com.moyeota.core.designsystem.component.StatusBarMock
import com.moyeota.core.designsystem.theme.MoyeotaColor
import kotlinx.coroutines.withTimeoutOrNull

// 와이어프레임 색 (core token 미정의 — 화면 재현용)
private val EmergencyBg = Color(0xFFF5F7FA)
private val ReasonIconBg = Color(0xFFFDECEE) // Danger50 동일값
private val EmMuteGray = Color(0xFF8A93A0)
private val EmAshGray = Color(0xFF9AA1AC)
private val EmTextMute = Color(0xFF6B7280)
private val EmCardShadow = Color(0x1A1B2A4A)

// 신고 사유 (디스크립션: 경로 이탈 / 불쾌한 언행 / 위급 / 기타)
enum class EmergencyReason(val title: String, val subtitle: String) {
    ROUTE_DEVIATION("길이 이상해요", "정해진 경로를 벗어났어요"),
    UNPLEASANT_SPEECH("불쾌한 말을 들었어요", "언행이 불편했어요"),
    DANGEROUS("무서운 상황이에요", "즉시 도움이 필요해요"), // 즉시 대응 큐로 분리
    OTHER("그 밖의 위급 상황", "직접 설명할게요"), // 선택 시 텍스트 입력 필수 (10~500자)
}

/**
 * 27 · 긴급 신고 [S17]
 *
 * 이동(디스크립션):
 * - 뒤로(X) → 26 운행 중 (onBack)
 * - 사유 선택 (1개 필수 — 미선택 시 신고 버튼 비활성)
 * - 「3초간 길게 눌러 신고」 3초 유지 성공 → 접수 후 26 복귀 (onReportSubmitted)
 *   · 3초 롱프레스 유지 실패 시 미전송 (오작동 방지)
 *   · 전송 항목: 현재 위치 · 차량번호 · 탑승 ID · 사유 · 시각 (백엔드 미연결 — 콜백만 호출)
 *
 * Safety500/600 색상은 이 화면(신고) 전용.
 */
@Composable
fun EmergencyScreen(
    rideSummary: String = "부산대 정문 → 서면역 · 12가 3456",
    onBack: () -> Unit = {},
    onReportSubmitted: (reason: EmergencyReason, detail: String) -> Unit = { _, _ -> },
) {
    var selectedReason by remember { mutableStateOf<EmergencyReason?>(null) }
    var detailText by remember { mutableStateOf("") }
    var holding by remember { mutableStateOf(false) }

    // 사유 1개 필수 + 「직접 설명할게요」는 10~500자 입력 필수
    val reportEnabled = selectedReason != null &&
        (selectedReason != EmergencyReason.OTHER || detailText.trim().length in 10..500)

    val currentSubmit by rememberUpdatedState(onReportSubmitted)
    val currentReason by rememberUpdatedState(selectedReason)
    val currentDetail by rememberUpdatedState(detailText)

    Column(modifier = Modifier.fillMaxSize().background(EmergencyBg)) {
        StatusBarMock()
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
                text = "무슨 일인지 알려주시면 바로 도와드릴게요",
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

            Spacer(Modifier.height(24.dp))
            Text(
                text = "무슨 일이 있었나요?",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = EmMuteGray,
            )
            Spacer(Modifier.height(10.dp))
            EmergencyReason.entries.forEachIndexed { index, reason ->
                ReasonCard(
                    reason = reason,
                    selected = selectedReason == reason,
                    onClick = { selectedReason = reason },
                )
                if (index != EmergencyReason.entries.lastIndex) Spacer(Modifier.height(12.dp))
            }

            // 「직접 설명할게요」 선택 시 텍스트 입력 필수 (10~500자)
            if (selectedReason == EmergencyReason.OTHER) {
                Spacer(Modifier.height(12.dp))
                MoyeotaTextField(
                    value = detailText,
                    onValueChange = { if (it.length <= 500) detailText = it },
                    placeholder = "상황을 설명해 주세요 (10~500자)",
                    errorText = if (detailText.isNotEmpty() && detailText.trim().length < 10) "10자 이상 입력해 주세요" else null,
                    helperText = if (detailText.isEmpty()) "10자 이상 입력해야 신고할 수 있어요" else null,
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        // 하단 고정: 안내 + 신고 버튼 + 푸터
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(
                text = "신고하면 현재 위치와 운행 정보가 운영팀에 전달돼요",
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
                            !reportEnabled -> MoyeotaColor.SurfaceSoft
                            holding -> MoyeotaColor.Safety600
                            else -> MoyeotaColor.Safety500
                        },
                    )
                    .pointerInput(reportEnabled) {
                        if (reportEnabled) {
                            detectTapGestures(
                                onPress = {
                                    holding = true
                                    // 3초 유지 실패(중도 해제) 시 미전송 — 오작동 방지
                                    val releasedEarly = withTimeoutOrNull(3_000L) { tryAwaitRelease() }
                                    holding = false
                                    if (releasedEarly == null) {
                                        currentReason?.let { currentSubmit(it, currentDetail.trim()) } // 접수 후 → 26 복귀
                                        tryAwaitRelease()
                                    }
                                },
                            )
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (holding) "계속 누르고 계세요…" else "3초간 길게 눌러 신고",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (reportEnabled) MoyeotaColor.TextOnDark else MoyeotaColor.TextAsh,
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
        // 홈 인디케이터
        Box(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(width = 135.dp, height = 5.dp)
                    .background(MoyeotaColor.InkPrimary, RoundedCornerShape(2.5.dp)),
            )
        }
    }
}

@Composable
private fun ReasonCard(reason: EmergencyReason, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .shadow(4.dp, RoundedCornerShape(16.dp), spotColor = EmCardShadow)
            .clip(RoundedCornerShape(16.dp))
            .background(MoyeotaColor.SurfaceCanvas)
            .then(
                if (selected) {
                    Modifier.border(1.5.dp, MoyeotaColor.Safety500, RoundedCornerShape(16.dp))
                } else {
                    Modifier
                },
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(32.dp).background(ReasonIconBg, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            when (reason) {
                EmergencyReason.ROUTE_DEVIATION -> RouteDeviationIcon()
                EmergencyReason.UNPLEASANT_SPEECH -> SpeechIcon()
                EmergencyReason.DANGEROUS -> DangerXIcon()
                EmergencyReason.OTHER -> DotsIcon()
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = reason.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MoyeotaColor.InkPrimary,
            )
            Text(
                text = reason.subtitle,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = EmMuteGray,
            )
        }
        ChevronRightIcon()
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

@Composable
private fun RouteDeviationIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(16.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.7.dp.toPx()
        val path = Path().apply {
            moveTo(w * 0.2f, h * 0.9f)
            lineTo(w * 0.2f, h * 0.45f)
            quadraticTo(w * 0.2f, h * 0.25f, w * 0.45f, h * 0.25f)
            lineTo(w * 0.8f, h * 0.25f)
        }
        drawPath(path, MoyeotaColor.Safety500, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawLine(MoyeotaColor.Safety500, Offset(w * 0.8f, h * 0.25f), Offset(w * 0.58f, h * 0.08f), stroke, StrokeCap.Round)
        drawLine(MoyeotaColor.Safety500, Offset(w * 0.8f, h * 0.25f), Offset(w * 0.58f, h * 0.42f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun SpeechIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(16.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.7.dp.toPx()
        drawRoundRect(
            color = MoyeotaColor.Safety500,
            topLeft = Offset(w * 0.08f, h * 0.12f),
            size = androidx.compose.ui.geometry.Size(w * 0.84f, h * 0.6f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
            style = Stroke(stroke),
        )
        val tail = Path().apply {
            moveTo(w * 0.3f, h * 0.72f)
            lineTo(w * 0.3f, h * 0.92f)
            lineTo(w * 0.5f, h * 0.72f)
        }
        drawPath(tail, MoyeotaColor.Safety500, style = Stroke(stroke, join = StrokeJoin.Round))
    }
}

@Composable
private fun DangerXIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(16.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 2.dp.toPx()
        drawLine(MoyeotaColor.Safety500, Offset(w * 0.2f, h * 0.2f), Offset(w * 0.8f, h * 0.8f), stroke, StrokeCap.Round)
        drawLine(MoyeotaColor.Safety500, Offset(w * 0.8f, h * 0.2f), Offset(w * 0.2f, h * 0.8f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun DotsIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(16.dp)) {
        val w = size.width
        val h = size.height
        val r = 1.5.dp.toPx()
        drawCircle(MoyeotaColor.Safety500, r, Offset(w * 0.2f, h * 0.5f))
        drawCircle(MoyeotaColor.Safety500, r, Offset(w * 0.5f, h * 0.5f))
        drawCircle(MoyeotaColor.Safety500, r, Offset(w * 0.8f, h * 0.5f))
    }
}

@Composable
private fun ChevronRightIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(16.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()
        drawLine(Color(0xFFC3CCDA), Offset(w * 0.38f, h * 0.22f), Offset(w * 0.66f, h * 0.5f), stroke, StrokeCap.Round)
        drawLine(Color(0xFFC3CCDA), Offset(w * 0.38f, h * 0.78f), Offset(w * 0.66f, h * 0.5f), stroke, StrokeCap.Round)
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun EmergencyScreenPreview() {
    EmergencyScreen()
}
