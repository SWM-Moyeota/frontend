package com.moyeota.presentation.feature.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.moyeota.core.designsystem.component.CheckIcon
import com.moyeota.core.designsystem.component.MoyeotaIconSize
import com.moyeota.core.designsystem.component.NavigationBarSpacer
import com.moyeota.core.designsystem.component.PageDots
import com.moyeota.core.designsystem.component.PrimaryCtaButton
import com.moyeota.core.designsystem.component.StatusBarSpacer
import com.moyeota.core.designsystem.component.TextLinkButton
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.core.designsystem.theme.MoyeotaShape
import com.moyeota.core.designsystem.theme.MoyeotaType

/**
 * 02 · 온보딩 — 신뢰 [O02]
 *
 * 진입: 01에서 다음
 * 목적: 휴대폰 인증을 마친 사람만 가입하고, 상대의 탑승 횟수를 볼 수 있다는 신뢰 전달
 *
 * 문구는 [OnboardingTrustCopy] 에 있다 — 예전 본문 「같은 성별끼리만 함께 타요」는 **성별 조건
 * 매칭이 없어서** 지웠다(2026-10-13). 일러스트의 체크 배지는 휴대폰 인증을 뜻한다.
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
            .background(MoyeotaColor.SurfaceCanvas),
    ) {
        StatusBarSpacer()

        // 건너뛰기 (우상단) — 48dp 터치 + 물결(디자인 QA G12). 링크 안쪽 8dp 만큼 줄 여백을 줄여
        // 글자 끝이 16dp 화면 여백에 선다
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextLinkButton(text = "건너뛰기", onClick = onSkip, color = MoyeotaColor.TextMute)
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
            text = OnboardingTrustCopy.headline,
            style = MoyeotaType.DisplayMd,
            color = MoyeotaColor.InkPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = OnboardingTrustCopy.body,
            style = MoyeotaType.BodyMd,
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

        NavigationBarSpacer()
    }
}

/**
 * 일러스트 — 휴대폰 인증을 마친 프로필 카드 (아바타 + 체크 배지 + 이름 자리 바).
 *
 * 뒤에 깔던 옅은 파란 판(r24)은 걷었다(디자인 QA A3) — 판 위의 카드는 "상자 속 상자"였다. 이제 카드가 흰
 * 화면에 바로 서므로 그림자 대신 [MoyeotaColor.Border] 선으로 모양을 낸다(G4). 높이는 그대로라 세 장의
 * 제목 자리가 같다.
 */
@Composable
private fun TrustIllustration(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(88.dp))

        Column(
            modifier = Modifier
                .size(width = 166.dp, height = 120.dp)
                .background(MoyeotaColor.SurfaceCanvas, MoyeotaShape.Card)
                .border(1.dp, MoyeotaColor.Border, MoyeotaShape.Card),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))

            Box {
                // 아바타 (사람 실루엣)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(MoyeotaColor.BorderStrong, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    PersonGlyph(Modifier.size(24.dp))
                }
                // 파란 체크 배지 (휴대폰 인증 완료)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 6.dp, y = 4.dp)
                        .size(20.dp)
                        .background(MoyeotaColor.Primary500, CircleShape)
                        .border(2.dp, MoyeotaColor.SurfaceCanvas, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    CheckIcon(size = MoyeotaIconSize.Sm, tint = MoyeotaColor.TextOnDark)
                }
            }

            Spacer(Modifier.height(16.dp))

            // 이름 자리 표시 바
            Box(
                modifier = Modifier
                    .width(112.dp)
                    .height(14.dp)
                    .background(MoyeotaColor.Border, CircleShape),
            )
        }
    }
}

@Composable
private fun PersonGlyph(modifier: Modifier = Modifier, color: Color = MoyeotaColor.TextMute) {
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

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun OnboardingTrustScreenPreview() {
    OnboardingTrustScreen(onNext = {}, onSkip = {})
}
