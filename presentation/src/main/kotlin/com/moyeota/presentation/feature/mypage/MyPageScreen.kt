package com.moyeota.presentation.feature.mypage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeota.core.designsystem.component.AvatarCircle
import com.moyeota.core.designsystem.component.MoyeotaBottomBar
import com.moyeota.core.designsystem.component.MoyeotaTab
import com.moyeota.core.designsystem.component.StatusBarMock
import com.moyeota.core.designsystem.theme.MoyeotaColor

// 와이어프레임 그레이 (core token 미정의 색 — 화면 재현용)
private val CanvasBg = Color(0xFFF5F7FA)
private val GraySlate = Color(0xFF4B5563)
private val GrayMute = Color(0xFF8A93A0)
private val GrayAsh = Color(0xFF9AA1AC)
private val SoftBg = Color(0xFFF6F8FB)
private val SoftDivider = Color(0xFFE4E9F0)
private val CardShadow = Color(0x0F1B2A4A)

/**
 * 35 · 마이페이지 · 설정 [S24]
 *
 * 진입: 하단탭 마이
 *
 * 이동(디스크립션):
 * - 「탑승 기록」 → 34 내 탑승 (onRideHistoryClick — 현재 연결값, 지난 기록 화면 별도 필요)
 * - 하단탭 홈 / 합승 / 채팅 → 14 / 17 / 24 (onTabSelect)
 * - [미연결 — 무동작] 「안심 설정」(→11) · 「결제 수단」(→30) · 「알림 설정」 ·
 *   「고객센터 · 신고 내역」 · 「로그아웃」(→04, 확인 다이얼로그 후) · 「탈퇴하기」
 *
 * 상태(디스크립션):
 * - 「2차」 배지 항목(마일리지 · 안심 설정 · 결제 수단 · 알림)은 1차 MVP 미적용
 */
@Composable
fun MyPageScreen(
    userName: String = "김OO",
    verifiedLine: String = "부산대학교 · 2026년 3월 인증",
    mannerScoreLabel: String = "98%",
    rideCountLabel: String = "42회",
    mileageLabel: String = "0P",
    rideHistoryValue: String = "42회 · 지난 탑승 보기",
    paymentValue: String = "카카오페이",
    versionLabel: String = "v1.0.0",
    onRideHistoryClick: () -> Unit = {},     // → 34 내 탑승
    onTabSelect: (MoyeotaTab) -> Unit = {},  // → 14 / 17 / 24
) {
    Column(modifier = Modifier.fillMaxSize().background(CanvasBg)) {
        StatusBarMock()

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "마이",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MoyeotaColor.InkPrimary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(Modifier.height(17.dp))
            // 프로필 카드 (탭 동작 디스크립션 미정 — 무동작)
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(104.dp)
                    .shadow(4.dp, RoundedCornerShape(18.dp), spotColor = CardShadow)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MoyeotaColor.SurfaceCanvas)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(56.dp)) {
                    AvatarCircle(size = 56.dp)
                    // 인증 체크 배지
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .offset(x = 40.dp, y = 40.dp)
                            .background(MoyeotaColor.Primary500, CircleShape)
                            .border(2.dp, MoyeotaColor.SurfaceCanvas, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        SmallCheckIcon(color = MoyeotaColor.TextOnDark)
                    }
                }
                Spacer(Modifier.size(14.dp))
                Column {
                    Text(
                        text = userName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MoyeotaColor.InkPrimary,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = verifiedLine,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = GrayMute,
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .height(22.dp)
                            .background(MoyeotaColor.Primary50, CircleShape)
                            .padding(horizontal = 13.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "학생 인증 완료",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MoyeotaColor.Primary600,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                ChevronRightIcon(size = 16.dp, color = GrayAsh)
            }

            Spacer(Modifier.height(16.dp))
            // 매너 점수 · 탑승 · 마일리지 요약
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(84.dp)
                    .background(SoftBg, RoundedCornerShape(18.dp)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatCell(value = mannerScoreLabel, label = "매너 점수", modifier = Modifier.weight(1f))
                Box(Modifier.size(width = 1.dp, height = 40.dp).background(SoftDivider))
                StatCell(value = rideCountLabel, label = "탑승", modifier = Modifier.weight(1f))
                Box(Modifier.size(width = 1.dp, height = 40.dp).background(SoftDivider))
                StatCell(value = mileageLabel, label = "마일리지 (고도화)", modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(20.dp))
            // 설정 목록
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .shadow(4.dp, RoundedCornerShape(18.dp), spotColor = CardShadow)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MoyeotaColor.SurfaceCanvas),
            ) {
                // 안심 설정 → 11 (미연결 — 무동작)
                SettingRow(
                    title = "안심 설정",
                    secondPhase = true,
                    value = "1차 MVP 미적용",
                    icon = { ShieldIcon(color = GraySlate) },
                )
                SettingDivider()
                // 결제 수단 → 30 (미연결 — 무동작)
                SettingRow(
                    title = "결제 수단",
                    secondPhase = true,
                    value = paymentValue,
                    icon = { CardIcon(color = GraySlate) },
                )
                SettingDivider()
                // 탑승 기록 → 34 내 탑승
                SettingRow(
                    title = "탑승 기록",
                    secondPhase = false,
                    value = rideHistoryValue,
                    icon = { DocIcon(color = GraySlate) },
                    onClick = onRideHistoryClick,
                )
                SettingDivider()
                // 알림 설정 (미연결 — 무동작)
                SettingRow(
                    title = "알림 설정",
                    secondPhase = true,
                    value = null,
                    icon = { BellOutlineIcon(color = GraySlate) },
                )
                SettingDivider()
                // 고객센터 · 신고 내역 (미연결 — 무동작)
                SettingRow(
                    title = "고객센터 · 신고 내역",
                    secondPhase = false,
                    value = null,
                    icon = { QuestionIcon(color = GraySlate) },
                )
            }

            Spacer(Modifier.height(24.dp))
            // 로그아웃 → 04 (확인 다이얼로그 후, 미연결 — 무동작)
            Text(
                text = "로그아웃",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = GrayMute,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(11.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 탈퇴하기 (미연결 — 무동작)
                Text(
                    text = "탈퇴하기",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = GrayAsh,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = versionLabel,
                    fontSize = 11.sp,
                    color = GrayAsh,
                )
            }

            Spacer(Modifier.height(24.dp))
        }

        // 하단탭 홈 / 합승 / 채팅 → 14 / 17 / 24
        MoyeotaBottomBar(selected = MoyeotaTab.MYPAGE, onSelect = onTabSelect)
        HomeIndicatorMyPage()
    }
}

@Composable
private fun StatCell(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MoyeotaColor.InkPrimary)
        Spacer(Modifier.height(2.dp))
        Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = GrayMute)
    }
}

@Composable
private fun SettingRow(
    title: String,
    secondPhase: Boolean,
    value: String?,
    icon: @Composable () -> Unit,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.size(11.dp))
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MoyeotaColor.InkPrimary,
        )
        if (secondPhase) {
            Spacer(Modifier.size(8.dp))
            // 「2차」 배지 — 1차 MVP 미적용 항목
            Box(
                modifier = Modifier
                    .height(22.dp)
                    .background(MoyeotaColor.Primary500, CircleShape)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "2차",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MoyeotaColor.TextOnDark,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        if (value != null) {
            Text(
                text = value,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = GrayMute,
            )
            Spacer(Modifier.size(8.dp))
        }
        ChevronRightIcon(size = 15.dp, color = GrayAsh)
    }
}

@Composable
private fun SettingDivider() {
    HorizontalDivider(
        color = MoyeotaColor.Hairline,
        modifier = Modifier.padding(start = 50.dp, end = 20.dp),
    )
}

// ─── 아이콘 (material-icons 미사용 — Canvas 직접 드로잉) ─────────────────────

@Composable
private fun SmallCheckIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(10.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()
        drawLine(color, Offset(w * 0.12f, h * 0.55f), Offset(w * 0.4f, h * 0.82f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.4f, h * 0.82f), Offset(w * 0.88f, h * 0.22f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun ChevronRightIcon(size: androidx.compose.ui.unit.Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = 1.8.dp.toPx()
        drawLine(color, Offset(w * 0.35f, h * 0.2f), Offset(w * 0.65f, h * 0.5f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.35f, h * 0.8f), Offset(w * 0.65f, h * 0.5f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun ShieldIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(19.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.08f)
            lineTo(w * 0.88f, h * 0.22f)
            lineTo(w * 0.88f, h * 0.52f)
            quadraticTo(w * 0.88f, h * 0.78f, w * 0.5f, h * 0.92f)
            quadraticTo(w * 0.12f, h * 0.78f, w * 0.12f, h * 0.52f)
            lineTo(w * 0.12f, h * 0.22f)
            close()
        }
        drawPath(path, color, style = stroke)
        drawLine(color, Offset(w * 0.34f, h * 0.48f), Offset(w * 0.46f, h * 0.6f), stroke.width, StrokeCap.Round)
        drawLine(color, Offset(w * 0.46f, h * 0.6f), Offset(w * 0.68f, h * 0.34f), stroke.width, StrokeCap.Round)
    }
}

@Composable
private fun CardIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(19.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.5.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.08f, h * 0.2f),
            size = Size(w * 0.84f, h * 0.6f),
            cornerRadius = CornerRadius(2.5.dp.toPx()),
            style = Stroke(stroke),
        )
        drawLine(color, Offset(w * 0.08f, h * 0.4f), Offset(w * 0.92f, h * 0.4f), stroke)
    }
}

@Composable
private fun DocIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(19.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.5.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.16f, h * 0.08f),
            size = Size(w * 0.68f, h * 0.84f),
            cornerRadius = CornerRadius(2.5.dp.toPx()),
            style = Stroke(stroke),
        )
        drawLine(color, Offset(w * 0.32f, h * 0.34f), Offset(w * 0.68f, h * 0.34f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.32f, h * 0.52f), Offset(w * 0.68f, h * 0.52f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.32f, h * 0.7f), Offset(w * 0.54f, h * 0.7f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun BellOutlineIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(19.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawArc(
            color = color,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.22f, h * 0.14f),
            size = Size(w * 0.56f, h * 0.6f),
            style = stroke,
        )
        drawLine(color, Offset(w * 0.22f, h * 0.44f), Offset(w * 0.22f, h * 0.7f), stroke.width, StrokeCap.Round)
        drawLine(color, Offset(w * 0.78f, h * 0.44f), Offset(w * 0.78f, h * 0.7f), stroke.width, StrokeCap.Round)
        drawLine(color, Offset(w * 0.12f, h * 0.7f), Offset(w * 0.88f, h * 0.7f), stroke.width, StrokeCap.Round)
        drawCircle(color, radius = 1.3.dp.toPx(), center = Offset(w * 0.5f, h * 0.85f))
    }
}

@Composable
private fun QuestionIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(19.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(color, radius = w * 0.42f, center = Offset(w * 0.5f, h * 0.5f), style = stroke)
        // 물음표 곡선 + 점
        drawArc(
            color = color,
            startAngle = 190f,
            sweepAngle = 200f,
            useCenter = false,
            topLeft = Offset(w * 0.34f, h * 0.24f),
            size = Size(w * 0.32f, h * 0.28f),
            style = stroke,
        )
        drawLine(color, Offset(w * 0.5f, h * 0.52f), Offset(w * 0.5f, h * 0.6f), stroke.width, StrokeCap.Round)
        drawCircle(color, radius = 1.2.dp.toPx(), center = Offset(w * 0.5f, h * 0.74f))
    }
}

// 홈 인디케이터 (하단탭 아래 흰 배경)
@Composable
private fun HomeIndicatorMyPage() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MoyeotaColor.SurfaceCanvas)
            .padding(top = 8.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 135.dp, height = 5.dp)
                .background(MoyeotaColor.InkPrimary, CircleShape),
        )
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun MyPageScreenPreview() {
    MyPageScreen()
}
