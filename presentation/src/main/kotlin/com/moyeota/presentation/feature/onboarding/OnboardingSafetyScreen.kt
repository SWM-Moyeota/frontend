package com.moyeota.presentation.feature.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeota.core.designsystem.component.NavigationBarSpacer
import com.moyeota.core.designsystem.component.PageDots
import com.moyeota.core.designsystem.component.PrimaryCtaButton
import com.moyeota.core.designsystem.component.StatusBarSpacer
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.core.designsystem.theme.MoyeotaType

/**
 * 03 · 온보딩 — 안심 [O03]
 *
 * 진입: 02에서 다음
 * 목적: 운행 중 긴급 신고 · 인원수 분할 요금 안내 후 가입 유도
 *
 * 인터랙션
 * - 「시작하기」 → 04 시작(로그인 방식) (onStart)
 * - 「이미 계정이 있어요」 → 14 홈(로그인된 상태 가정) (onAlreadyHaveAccount)
 * - 「건너뛰기」 → 04 로그인 (onSkip)
 *
 * 검증·상태
 * - 「이미 계정이 있어요」의 토큰 검사(없으면 04로 되돌림)는 호출부 책임 — 화면은 콜백만 노출
 */
@Composable
fun OnboardingSafetyScreen(
    onStart: () -> Unit,
    onAlreadyHaveAccount: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MoyeotaColor.SurfaceSoft),
    ) {
        StatusBarSpacer()

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

        SafetyIllustration(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(330.dp),
        )

        Spacer(Modifier.height(32.dp))

        Text(
            text = "타는 동안, 안심하도록",
            style = MoyeotaType.DisplayMd.copy(fontSize = 26.sp, letterSpacing = (-0.52).sp),
            color = MoyeotaColor.InkPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "운행 중 긴급 신고가 바로 되고\n요금은 인원수대로 나눠 안내해요",
            style = MoyeotaType.BodyMd.copy(lineHeight = 22.sp, letterSpacing = (-0.3).sp),
            color = MoyeotaColor.TextMute,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(32.dp))

        PageDots(
            count = 3,
            activeIndex = 2,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        Spacer(Modifier.weight(1f))

        PrimaryCtaButton(
            text = "시작하기",
            onClick = onStart,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(18.dp))

        Text(
            text = "이미 계정이 있어요",
            style = MoyeotaType.BodySm.copy(fontWeight = FontWeight.Medium, letterSpacing = (-0.26).sp),
            color = MoyeotaColor.TextMute,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickable { onAlreadyHaveAccount() }
                .padding(8.dp),
        )

        Spacer(Modifier.weight(1f))

        NavigationBarSpacer()
    }
}

// 일러스트 — 지도(경로: 파란 점 → 빨간 핀). 공유 배지는 보호자 공유 기능이 없어 뺐고, 지도를 카드 가운데에 둔다
@Composable
private fun SafetyIllustration(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(MoyeotaColor.Primary50, RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center,
    ) {
        // 지도 목업 (경로 라인) — 배지가 빠진 만큼 키워 카드를 채운다
        Box(
            modifier = Modifier
                .size(width = 260.dp, height = 180.dp)
                .background(Color(0xFFE2E7EF), RoundedCornerShape(16.dp)),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val start = Offset(size.width * 0.16f, size.height * 0.78f)
                val end = Offset(size.width * 0.84f, size.height * 0.24f)

                // 경로 라인
                drawLine(
                    color = MoyeotaColor.Primary500,
                    start = start,
                    end = end,
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round,
                )

                // 출발 지점 — 파란 점 (흰 테두리)
                drawCircle(color = Color.White, radius = 9.dp.toPx() / 2f + 2.dp.toPx(), center = start)
                drawCircle(color = MoyeotaColor.Primary500, radius = 9.dp.toPx() / 2f, center = start)

                // 도착 지점 — 빨간 핀
                val pinCenter = Offset(end.x, end.y - 8.dp.toPx())
                val pinRadius = 9.dp.toPx()
                val pinPath = Path().apply {
                    moveTo(pinCenter.x, pinCenter.y + pinRadius * 2.1f)
                    lineTo(pinCenter.x - pinRadius * 0.8f, pinCenter.y + pinRadius * 0.5f)
                    lineTo(pinCenter.x + pinRadius * 0.8f, pinCenter.y + pinRadius * 0.5f)
                    close()
                }
                drawPath(pinPath, color = MoyeotaColor.Danger500)
                drawCircle(color = MoyeotaColor.Danger500, radius = pinRadius, center = pinCenter)
                drawCircle(color = Color.White, radius = pinRadius * 0.35f, center = pinCenter)
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun OnboardingSafetyScreenPreview() {
    OnboardingSafetyScreen(onStart = {}, onAlreadyHaveAccount = {}, onSkip = {})
}
