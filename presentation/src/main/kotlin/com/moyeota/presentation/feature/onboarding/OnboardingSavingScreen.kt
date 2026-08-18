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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
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
 * 01 · 온보딩 — 절약 [O01]
 *
 * 진입: 앱 최초 실행 1회 (이후 마이 > 도움말에서 재열람)
 * 목적: 1/N 절약 가치 전달
 *
 * 인터랙션
 * - 「다음」 → 02 온보딩 신뢰 (onNext)
 * - 「건너뛰기」 → 04 로그인 (onSkip)
 * - 좌우 스와이프 → 02 (제스처, 프로토타입 미구현 → 미구현)
 *
 * 검증·상태
 * - 온보딩 완료 플래그 로컬 저장/재실행 분기는 호출부(내비게이션) 책임 — 화면은 콜백만 노출
 * - 요금 예시(9,600 → 3,200)는 3인 기준 하드코딩 값
 */
@Composable
fun OnboardingSavingScreen(
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

        SavingIllustration(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(330.dp),
        )

        Spacer(Modifier.height(32.dp))

        Text(
            text = "택시비, 혼자 다 내지 마요",
            style = MoyeotaType.DisplayMd.copy(fontSize = 26.sp, letterSpacing = (-0.52).sp),
            color = MoyeotaColor.InkPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "같은 방향 사람과 나눠 타면\n요금이 1/N로 줄어요",
            style = MoyeotaType.BodyMd.copy(lineHeight = 22.sp, letterSpacing = (-0.3).sp),
            color = MoyeotaColor.TextMute,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(32.dp))

        PageDots(
            count = 3,
            activeIndex = 0,
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

// 일러스트 — 원 안의 ₩ + 요금 비교 (9,600원 → 3,200원)
@Composable
private fun SavingIllustration(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.background(MoyeotaColor.Primary50, RoundedCornerShape(24.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(72.dp))

        Box(
            modifier = Modifier
                .size(140.dp)
                .background(Color(0xFFDCE7FB), CircleShape)
                .border(2.dp, MoyeotaColor.Primary500.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "₩",
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                color = MoyeotaColor.Primary500,
            )
        }

        Spacer(Modifier.height(22.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FarePill(text = "9,600원", background = MoyeotaColor.SurfaceCanvas, textColor = MoyeotaColor.InkPrimary)
            PlusIcon()
            FarePill(text = "3,200원", background = MoyeotaColor.Primary500, textColor = MoyeotaColor.TextOnDark)
        }
    }
}

@Composable
private fun FarePill(text: String, background: Color, textColor: Color) {
    Box(
        modifier = Modifier.background(background, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun PlusIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(14.dp)) {
        val stroke = 2.5.dp.toPx()
        val c = size.width / 2f
        drawLine(Color(0xFF8A93A0), Offset(0f, c), Offset(size.width, c), stroke, StrokeCap.Round)
        drawLine(Color(0xFF8A93A0), Offset(c, 0f), Offset(c, size.height), stroke, StrokeCap.Round)
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun OnboardingSavingScreenPreview() {
    OnboardingSavingScreen(onNext = {}, onSkip = {})
}
