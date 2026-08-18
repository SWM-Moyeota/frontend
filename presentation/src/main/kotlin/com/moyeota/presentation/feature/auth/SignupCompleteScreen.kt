package com.moyeota.presentation.feature.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeota.core.designsystem.component.PrimaryCtaButton
import com.moyeota.core.designsystem.component.StatusBarMock
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.core.designsystem.theme.MoyeotaTheme
import com.moyeota.core.designsystem.theme.MoyeotaType

// 13 · 가입 완료 [S08]
// 진입: 12 동의 완료 — 뒤로가기로 가입 플로우 재진입 불가 (스택 초기화는 호출부 책임)
// 「탑승할 사람 찾기」 → 14 홈 / 「앱 먼저 둘러보기」 → 17 합승 지도 (peek)
@Composable
fun SignupCompleteScreen(
    onStart: () -> Unit,
    onExplore: () -> Unit = {},
    modifier: Modifier = Modifier,
    couponIssued: Boolean = true, // 쿠폰 발급 실패해도 화면 진행은 막지 않음 (배너만 숨김)
) {
    Column(modifier = modifier.fillMaxSize().background(MoyeotaColor.SurfaceSoft)) {
        StatusBarMock()

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(104.dp))
            CompleteCheckBadge(modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(32.dp))
            Text(
                text = "가입이 끝났어요",
                style = MoyeotaType.DisplayMd.copy(fontSize = 26.sp),
                color = MoyeotaColor.InkPrimary,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "이제 같이 탈 사람을 찾아볼까요?",
                style = MoyeotaType.BodyMd,
                color = MoyeotaColor.TextMute,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            Spacer(Modifier.height(30.dp))
            if (couponIssued) {
                // 첫 탑승 3,000원 지원: 발급 시점 = 가입 완료, 만료 30일, 첫 결제 1회 자동 차감
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(18.dp), spotColor = Color(0x0F1B2A4A))
                        .background(MoyeotaColor.SurfaceCanvas, RoundedCornerShape(18.dp))
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                ) {
                    Text(
                        text = "첫 탑승 3,000원 지원",
                        style = MoyeotaType.HeadingLg,
                        fontWeight = FontWeight.Bold,
                        color = MoyeotaColor.Primary500,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "첫 탑승 요금에서 자동으로 빠져요 · 30일 안에 사용",
                        style = MoyeotaType.CaptionMd,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF8A93A0),
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
            Text(
                text = "이렇게 이용해요",
                style = MoyeotaType.BodySm.copy(fontSize = 14.sp),
                fontWeight = FontWeight.Bold,
                color = MoyeotaColor.InkPrimary,
            )
            Spacer(Modifier.height(12.dp))
            completeGuideSteps.forEachIndexed { index, step ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(26.dp).background(MoyeotaColor.Primary50, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MoyeotaType.CaptionMd,
                            fontWeight = FontWeight.Bold,
                            color = MoyeotaColor.Primary500,
                        )
                    }
                    Spacer(Modifier.width(11.dp))
                    Text(
                        text = step,
                        style = MoyeotaType.BodySm.copy(fontSize = 14.sp),
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF4B5563),
                    )
                }
                if (index != completeGuideSteps.lastIndex) {
                    Spacer(Modifier.height(18.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            PrimaryCtaButton(
                text = "탑승할 사람 찾기",
                onClick = onStart,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "앱 먼저 둘러보기",
                style = MoyeotaType.BodySm,
                fontWeight = FontWeight.Medium,
                color = MoyeotaColor.TextMute,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onExplore,
                    ),
            )
        }
        CompleteHomeIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

private val completeGuideSteps = listOf(
    "목적지를 넣으면 같은 방향 사람을 찾아요",
    "매칭되면 채팅으로 만날 곳을 정해요",
    "내릴 때 1/N으로 나눠 내면 끝이에요",
)

// 큰 체크 배지 — Primary50 원 안에 Primary 체크표시
@Composable
private fun CompleteCheckBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(88.dp).background(MoyeotaColor.Primary50, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(37.dp)) {
            val stroke = 5.dp.toPx()
            val w = size.width
            val h = size.height
            drawLine(
                color = MoyeotaColor.Primary500,
                start = Offset(w * 0.08f, h * 0.55f),
                end = Offset(w * 0.38f, h * 0.85f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = MoyeotaColor.Primary500,
                start = Offset(w * 0.38f, h * 0.85f),
                end = Offset(w * 0.92f, h * 0.15f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun CompleteHomeIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(top = 12.dp, bottom = 9.dp)
            .size(width = 135.dp, height = 5.dp)
            .background(MoyeotaColor.InkPrimary, RoundedCornerShape(2.5.dp)),
    )
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun SignupCompleteScreenPreview() {
    MoyeotaTheme {
        SignupCompleteScreen(onStart = {}, onExplore = {})
    }
}
