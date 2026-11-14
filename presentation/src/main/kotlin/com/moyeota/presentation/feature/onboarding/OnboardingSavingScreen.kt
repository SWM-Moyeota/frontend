package com.moyeota.presentation.feature.onboarding

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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.moyeota.core.designsystem.component.ChevronIcon
import com.moyeota.core.designsystem.component.MoyeotaIconSize
import com.moyeota.core.designsystem.component.NavigationBarSpacer
import com.moyeota.core.designsystem.component.PageDots
import com.moyeota.core.designsystem.component.PrimaryCtaButton
import com.moyeota.core.designsystem.component.StatusBarSpacer
import com.moyeota.core.designsystem.component.TextLinkButton
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.core.designsystem.theme.MoyeotaShape
import com.moyeota.core.designsystem.theme.MoyeotaType
import com.moyeota.core.designsystem.theme.tabularNums

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

        SavingIllustration(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(330.dp),
        )

        Spacer(Modifier.height(32.dp))

        Text(
            text = OnboardingSavingCopy.headline,
            style = MoyeotaType.DisplayMd,
            color = MoyeotaColor.InkPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = OnboardingSavingCopy.body,
            style = MoyeotaType.BodyMd,
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

        NavigationBarSpacer()
    }
}

/**
 * 일러스트 — 요금 비교 (9,600원 → 3,200원) 두 알약만.
 *
 * 예전엔 옅은 파란 판 위에 44sp 「₩」 글자를 담은 원이 있었다(디자인 QA A3). 글자를 그림 대신 쓴 장식이고,
 * 판까지 더해 파랑이 화면을 덮었다. 메시지는 두 금액이 전부 말하므로 그것만 흰 바탕에 남긴다.
 * 높이는 호출부가 주는 그대로 두어(다른 두 장과 같은 330dp) 페이지를 넘겨도 제목 자리가 흔들리지 않는다.
 */
@Composable
private fun SavingIllustration(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FarePill(text = "9,600원", background = MoyeotaColor.SurfaceCanvas, textColor = MoyeotaColor.InkPrimary, outlined = true)
            // 「→」 — 예전엔 더하기 기호였는데 두 금액은 더하는 관계가 아니라 줄어드는 관계다
            ChevronIcon(size = MoyeotaIconSize.Md, tint = MoyeotaColor.TextMute)
            FarePill(text = "3,200원", background = MoyeotaColor.Primary500, textColor = MoyeotaColor.TextOnDark)
        }
    }
}

@Composable
private fun FarePill(text: String, background: Color, textColor: Color, outlined: Boolean = false) {
    Box(
        modifier = Modifier
            .background(background, MoyeotaShape.Card)
            // 흰 알약은 흰 화면에서 모양이 사라지므로 테두리로 선다(디자인 QA G4)
            .then(if (outlined) Modifier.border(1.dp, MoyeotaColor.Border, MoyeotaShape.Card) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MoyeotaType.HeadingLg.tabularNums(),
            color = textColor,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun OnboardingSavingScreenPreview() {
    OnboardingSavingScreen(onNext = {}, onSkip = {})
}
