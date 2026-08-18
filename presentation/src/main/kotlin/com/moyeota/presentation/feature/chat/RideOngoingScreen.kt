package com.moyeota.presentation.feature.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeota.core.designsystem.component.BackArrowIcon
import com.moyeota.core.designsystem.component.MapPlaceholder
import com.moyeota.core.designsystem.component.SheetHandle
import com.moyeota.core.designsystem.component.StatusBarMock
import com.moyeota.core.designsystem.theme.MoyeotaColor

// 와이어프레임 색 (core token 미정의 — 화면 재현용)
private val ScreenBg = Color(0xFFF5F7FA)
private val RouteCardBg = Color(0xFFF1F5FD)
private val RouteLine = Color(0xFFC9D6EC)
private val RouteDotGray = Color(0xFFC3CCDA)
private val MuteGray = Color(0xFF8A93A0)
private val AshGray = Color(0xFF9AA1AC)
private val SlateGray = Color(0xFF4B5563)
private val ReportBg = Color(0xFFFDECEE) // Danger50 동일값 — 와이어프레임 신고 칩 배경
private val ReportRed = Color(0xFFDC2626) // 와이어프레임 신고 텍스트 (Safety500 동일값, 신고 요소 전용)
private val CardShadow = Color(0x1A1B2A4A)

/**
 * 26 · 운행 중 — 안심 공유 [S15]
 *
 * 이동(디스크립션):
 * - 뒤로 → 25 배차 상태 (onBack)
 * - 「채팅 열기」 → 24 채팅 (onOpenChat)
 * - 「신고」 / 「문제가 생기면 아래에서 바로 신고할 수 있어요」 → 27 긴급 신고 (onReport)
 * - [미연결] 도착 후 자동 전환 → 28 최종 요금 확인 (무동작)
 *
 * 상태: 보호자 공유 토글은 로컬 상태. 심야(23:00~04:00)는 설정 무관 자동 공유(11 설정 기준).
 * 플로우 진행 화면 — 하단탭 없음 (공통 규칙).
 */
@Composable
fun RideOngoingScreen(
    remainingLabel: String = "서면역까지 8분 남음",
    arrivalLabel: String = "오후 6:57 도착 예정 · 위치가 실시간으로 반영돼요",
    guardianLabel: String = "어머니 · 010-••••-1234 · 도착하면 자동으로 알려드려요",
    onBack: () -> Unit = {},
    onOpenChat: () -> Unit = {},
    onReport: () -> Unit = {},
) {
    var guardianSharing by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxSize().background(ScreenBg)) {
        // 헤더 (흰 배경)
        Column(modifier = Modifier.fillMaxWidth().background(MoyeotaColor.SurfaceCanvas)) {
            StatusBarMock()
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onBack() },
                ) {
                    BackArrowIcon(modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "운행 중",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MoyeotaColor.InkPrimary,
                )
                Spacer(Modifier.weight(1f))
                ShieldIcon(tint = SlateGray)
            }
        }

        // 지도 영역
        MapPlaceholder(modifier = Modifier.fillMaxWidth().height(190.dp))

        // 바텀 시트
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .shadow(14.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), spotColor = CardShadow)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(MoyeotaColor.SurfaceCanvas),
        ) {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 14.dp), contentAlignment = Alignment.Center) {
                SheetHandle()
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = remainingLabel,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MoyeotaColor.InkPrimary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = arrivalLabel,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MuteGray,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(Modifier.height(18.dp))
            // 경유 순서 카드
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(RouteCardBg, RoundedCornerShape(16.dp)),
            ) {
                // 타임라인 연결선
                Box(
                    Modifier
                        .offset(x = 30.dp, y = 30.dp)
                        .size(width = 3.dp, height = 88.dp)
                        .background(RouteLine, RoundedCornerShape(1.5.dp)),
                )
                Column(modifier = Modifier.fillMaxSize().padding(vertical = 18.dp)) {
                    RouteStepRow(label = "부산대 정문 · 탑승 완료", time = "6:45", state = RouteStepState.DONE)
                    Spacer(Modifier.weight(1f))
                    RouteStepRow(label = "서면역 1번 출구로 이동 중", time = "6:57", state = RouteStepState.CURRENT)
                    Spacer(Modifier.weight(1f))
                    RouteStepRow(label = "내린 뒤 현장에서 1/N 정산", time = null, state = RouteStepState.PENDING)
                }
            }

            Spacer(Modifier.height(18.dp))
            // 안심 공유 카드 — 보호자 실시간 공유 (11에서 등록·동의된 연락처에만 전송)
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .shadow(4.dp, RoundedCornerShape(16.dp), spotColor = CardShadow)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MoyeotaColor.SurfaceCanvas)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                ShieldIcon(tint = MoyeotaColor.Success500, modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "보호자에게 실시간 공유 중",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MoyeotaColor.InkPrimary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = guardianLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MuteGray,
                    )
                }
                Spacer(Modifier.width(10.dp))
                // 토글 (하차·도착 확인 시 자동 종료 + 도착 알림 발송)
                Box(
                    modifier = Modifier
                        .size(width = 46.dp, height = 26.dp)
                        .background(
                            if (guardianSharing) MoyeotaColor.Success500 else MoyeotaColor.TextAsh,
                            RoundedCornerShape(13.dp),
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { guardianSharing = !guardianSharing },
                    contentAlignment = if (guardianSharing) Alignment.CenterEnd else Alignment.CenterStart,
                ) {
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .size(20.dp)
                            .background(MoyeotaColor.SurfaceCanvas, CircleShape),
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // 신고 안내 문구 → 27
            Text(
                text = "문제가 생기면 아래에서 바로 신고할 수 있어요",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = AshGray,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onReport() },
            )
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                // 「신고」 → 27 긴급 신고
                Row(
                    modifier = Modifier
                        .size(width = 112.dp, height = 52.dp)
                        .background(ReportBg, RoundedCornerShape(16.dp))
                        .clickable { onReport() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    WarnTriangleIcon(tint = ReportRed)
                    Spacer(Modifier.width(6.dp))
                    Text(text = "신고", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ReportRed)
                }
                Spacer(Modifier.weight(1f))
                // 「채팅 열기」 → 24 채팅
                Row(
                    modifier = Modifier
                        .size(width = 176.dp, height = 52.dp)
                        .shadow(4.dp, RoundedCornerShape(16.dp), spotColor = CardShadow)
                        .clip(RoundedCornerShape(16.dp))
                        .background(RouteCardBg)
                        .clickable { onOpenChat() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    ChatBubbleIcon(tint = SlateGray)
                    Spacer(Modifier.width(7.dp))
                    Text(text = "채팅 열기", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = SlateGray)
                }
            }
            Spacer(Modifier.height(14.dp))
            // 홈 인디케이터
            Box(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 135.dp, height = 5.dp).background(MoyeotaColor.InkPrimary, CircleShape))
            }
        }
    }
}

private enum class RouteStepState { DONE, CURRENT, PENDING }

@Composable
private fun RouteStepRow(label: String, time: String?, state: RouteStepState) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(30.dp), contentAlignment = Alignment.Center) {
            when (state) {
                RouteStepState.CURRENT -> Box(
                    Modifier
                        .size(20.dp)
                        .background(MoyeotaColor.Primary500, CircleShape)
                        .border(3.dp, MoyeotaColor.SurfaceCanvas, CircleShape),
                )
                RouteStepState.DONE -> Box(Modifier.size(14.dp).background(RouteDotGray, CircleShape))
                RouteStepState.PENDING -> Box(Modifier.size(14.dp).background(RouteLine, CircleShape))
            }
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (state == RouteStepState.CURRENT) FontWeight.Bold else FontWeight.Medium,
            color = if (state == RouteStepState.CURRENT) MoyeotaColor.InkPrimary else MuteGray,
            modifier = Modifier.weight(1f),
        )
        if (time != null) {
            Text(
                text = time,
                fontSize = 14.sp,
                fontWeight = if (state == RouteStepState.CURRENT) FontWeight.Bold else FontWeight.Medium,
                color = if (state == RouteStepState.CURRENT) MoyeotaColor.InkPrimary else MuteGray,
            )
        }
    }
}

// ─── 아이콘 (material-icons 미사용 — Canvas 직접 드로잉) ─────────────────────

@Composable
private fun ShieldIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.7.dp.toPx()
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.08f)
            lineTo(w * 0.88f, h * 0.24f)
            lineTo(w * 0.88f, h * 0.52f)
            quadraticTo(w * 0.88f, h * 0.78f, w * 0.5f, h * 0.94f)
            quadraticTo(w * 0.12f, h * 0.78f, w * 0.12f, h * 0.52f)
            lineTo(w * 0.12f, h * 0.24f)
            close()
        }
        drawPath(path, tint, style = Stroke(stroke, join = StrokeJoin.Round))
        drawLine(tint, Offset(w * 0.34f, h * 0.5f), Offset(w * 0.46f, h * 0.62f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.46f, h * 0.62f), Offset(w * 0.68f, h * 0.36f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun WarnTriangleIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.12f)
            lineTo(w * 0.92f, h * 0.85f)
            lineTo(w * 0.08f, h * 0.85f)
            close()
        }
        drawPath(path, tint, style = Stroke(stroke, join = StrokeJoin.Round))
        drawLine(tint, Offset(w * 0.5f, h * 0.4f), Offset(w * 0.5f, h * 0.62f), stroke, StrokeCap.Round)
        drawCircle(tint, radius = 1.1.dp.toPx(), center = Offset(w * 0.5f, h * 0.74f))
    }
}

@Composable
private fun ChatBubbleIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(19.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.08f, h * 0.12f),
            size = androidx.compose.ui.geometry.Size(w * 0.84f, h * 0.62f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
            style = Stroke(stroke),
        )
        val tail = Path().apply {
            moveTo(w * 0.28f, h * 0.74f)
            lineTo(w * 0.28f, h * 0.92f)
            lineTo(w * 0.46f, h * 0.74f)
        }
        drawPath(tail, tint, style = Stroke(stroke, join = StrokeJoin.Round))
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun RideOngoingScreenPreview() {
    RideOngoingScreen()
}
