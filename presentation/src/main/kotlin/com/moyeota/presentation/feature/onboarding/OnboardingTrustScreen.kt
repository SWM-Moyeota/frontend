package com.moyeota.presentation.feature.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeota.core.designsystem.component.PageDots
import com.moyeota.core.designsystem.component.PrimaryCtaButton
import com.moyeota.core.designsystem.component.StatusBarMock
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.core.designsystem.theme.MoyeotaType

/**
 * 02 · 온보딩 — 신뢰 [O02]
 *
 * 진입: 01에서 다음
 * 목적: 인증 사용자만 매칭된다는 신뢰 전달
 *
 * 인터랙션
 * - 「다음」 → 03 온보딩 안심 (onNext)
 * - 「건너뛰기」 → 04 로그인 (onSkip)
 *
 * 검증·상태
 * - 별도 입력 없음. 페이지 인디케이터 2/3 표시
 */
@Composable
fun OnboardingTrustScreen(
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MoyeotaColor.SurfaceSoft),
    ) {
        StatusBarMock()

        // 건너뛰기 (우상단)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                text = "건너뛰기",
                style = MoyeotaType.ButtonMd,
                color = MoyeotaColor.TextMute,
                modifier = Modifier
                    .clickable { onSkip() }
                    .padding(8.dp),
            )
        }

        Spacer(Modifier.height(24.dp))

        TrustIllustration(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(330.dp),
        )

        Spacer(Modifier.height(32.dp))

        Text(
            text = "믿을 수 있는 사람과",
            style = MoyeotaType.DisplayMd.copy(fontSize = 26.sp, letterSpacing = (-0.52).sp),
            color = MoyeotaColor.InkPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "인증을 마친 이용자만 매칭되고\n동성끼리 탈 수도 있어요",
            style = MoyeotaType.BodyMd.copy(lineHeight = 22.sp, letterSpacing = (-0.3).sp),
            color = MoyeotaColor.TextMute,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(32.dp))

        PageDots(
            count = 3,
            activeIndex = 1,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        Spacer(Modifier.weight(1f))

        PrimaryCtaButton(
            text = "다음",
            onClick = onNext,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(Modifier.weight(1f))

        // 홈 인디케이터 목업
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 9.dp)
                .size(width = 135.dp, height = 5.dp)
                .background(MoyeotaColor.InkPrimary, CircleShape),
        )
    }
}

// 일러스트 — 인증 완료된 프로필 카드 (아바타 + 체크 배지 + 이름 자리 바)
@Composable
private fun TrustIllustration(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.background(MoyeotaColor.Primary50, RoundedCornerShape(24.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(86.dp))

        Column(
            modifier = Modifier
                .size(width = 166.dp, height = 120.dp)
                .background(MoyeotaColor.SurfaceCanvas, RoundedCornerShape(16.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(18.dp))

            Box {
                // 아바타 (사람 실루엣)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color(0xFFCBD2DA), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    PersonGlyph(Modifier.size(24.dp))
                }
                // 파란 체크 배지 (인증 완료)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 6.dp, y = 4.dp)
                        .size(20.dp)
                        .background(MoyeotaColor.Primary500, CircleShape)
                        .border(2.dp, MoyeotaColor.SurfaceCanvas, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    CheckGlyph(Modifier.size(10.dp), color = MoyeotaColor.TextOnDark)
                }
            }

            Spacer(Modifier.height(16.dp))

            // 이름 자리 표시 바
            Box(
                modifier = Modifier
                    .width(112.dp)
                    .height(14.dp)
                    .background(Color(0xFFD3DAE4), CircleShape),
            )
        }
    }
}

@Composable
private fun PersonGlyph(modifier: Modifier = Modifier, color: Color = Color(0xFF7C8592)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // 머리
        drawCircle(color = color, radius = w * 0.22f, center = Offset(w / 2f, h * 0.3f))
        // 어깨 (반원)
        drawArc(
            color = color,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(w * 0.12f, h * 0.62f),
            size = Size(w * 0.76f, h * 0.72f),
            style = Fill,
        )
    }
}

@Composable
private fun CheckGlyph(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val stroke = 2.dp.toPx()
        val w = size.width
        val h = size.height
        drawLine(color, Offset(w * 0.1f, h * 0.55f), Offset(w * 0.4f, h * 0.85f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.4f, h * 0.85f), Offset(w * 0.9f, h * 0.2f), stroke, StrokeCap.Round)
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun OnboardingTrustScreenPreview() {
    OnboardingTrustScreen(onNext = {}, onSkip = {})
}
