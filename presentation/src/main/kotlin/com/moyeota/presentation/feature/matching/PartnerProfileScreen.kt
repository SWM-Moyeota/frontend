package com.moyeota.presentation.feature.matching

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.moyeota.core.designsystem.component.ChatBubbleIcon
import com.moyeota.core.designsystem.component.CheckIcon
import com.moyeota.core.designsystem.component.MoyeotaIconSize
import com.moyeota.core.designsystem.component.MoyeotaTopBar
import com.moyeota.core.designsystem.component.NavigationBarSpacer
import com.moyeota.core.designsystem.component.PrimaryCtaButton
import com.moyeota.core.designsystem.component.StatusBarSpacer
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.core.designsystem.theme.MoyeotaSpacing
import com.moyeota.core.designsystem.theme.MoyeotaType
import com.moyeota.domain.model.User
import com.moyeota.presentation.core.MemberAvatar
import com.moyeota.presentation.core.displayNickname

/**
 * 23 · 동승자 프로필 [S13]
 *
 * 이동(디스크립션):
 * - 뒤로 → 22 탑승 상세 (onBack)
 * - 「채팅으로 물어보기」 → 24 채팅 (onChatClick)
 *
 * 서버 계약(2026-09): 방 상세 `members[]` 가 주는 것은 닉네임·이미지·탑승 횟수뿐이다.
 * 그래서 이 화면은 그 값만 보여 준다 — 지표는 탑승 횟수 하나이고, 서버에 없는 평가·집계·검증
 * 항목은 「준비 중」 자리표시도 두지 않고 요소째 뺐다. 프로필은 사람을 판단하는 화면이라
 * 지어낸 수치나 빈 칸 하나가 그대로 신뢰의 근거가 된다.
 *
 * ### 2026-11 디자인 QA (PP1)
 * - 지표가 하나뿐인데 88dp 그림자 카드에 담겨 있었다 — 칸 하나짜리 표는 표가 아니라 장식이다.
 *   이름 아래 한 줄 「탑승 횟수 · 12회」로 줄였다
 * - CTA 는 앱 공용 [PrimaryCtaButton](알약 52dp)이다. 예전 r16 · 56dp · 파란 빛번짐 그림자 버튼은 이
 *   화면에만 있는 모양이었다. 말풍선 아이콘은 `leadingIcon` 자리로 지켰다
 * - 머리줄은 하위 화면 공용 [MoyeotaTopBar], 바탕은 흰색(G2)
 *
 * @param user 22·20 에서 탭한 그 멤버. 호출부가 반드시 넘긴다(기본값 없음).
 */
@Composable
fun PartnerProfileScreen(
    user: User,
    onBack: () -> Unit = {},
    onChatClick: () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxSize().background(MoyeotaColor.SurfaceCanvas)) {
        StatusBarSpacer()
        MoyeotaTopBar(title = "프로필", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MoyeotaSpacing.ScreenGutter),
        ) {
            Spacer(Modifier.height(16.dp))

            // 아바타 + 인증 배지 — 인증 배지는 서버가 badgeId 를 주기 시작할 때만 붙인다.
            // 아무나 체크 표시를 달면 "확인된 사람" 이라는 잘못된 신호가 된다.
            Box(modifier = Modifier.align(Alignment.CenterHorizontally)) {
                MemberAvatar(user = user, size = 80.dp)
                if (user.verifiedLabel.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(28.dp)
                            .background(MoyeotaColor.Primary500, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        CheckIcon(size = MoyeotaIconSize.Sm, tint = MoyeotaColor.TextOnDark)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = user.displayNickname,
                style = MoyeotaType.DisplayMd,
                color = MoyeotaColor.InkPrimary,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            // 서버가 배지 라벨을 줄 때만 한 줄 붙인다 — 없으면 줄째 생략한다
            if (user.verifiedLabel.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = user.verifiedLabel,
                    style = MoyeotaType.BodySm,
                    color = MoyeotaColor.TextMute,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            Spacer(Modifier.height(8.dp))
            // 서버가 주는 지표는 탑승 횟수 하나뿐이다 — 카드 없이 이름 아래 한 줄로 둔다
            Text(
                text = "탑승 횟수 · ${if (user.rideCount <= 0) "첫 탑승" else "${user.rideCount}회"}",
                style = MoyeotaType.BodyMd,
                color = MoyeotaColor.TextBody,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))

            Text(
                // 「학번」은 지웠다 — 학교 인증(06·07)이 가입 경로에 없어 학번을 받은 적이 없다.
                // 서버가 주는 멤버 정보는 publicId·닉네임·이미지·탑승 횟수뿐이다(2026-10-13).
                text = "실명과 연락처는 서로에게 공개되지 않아요",
                style = MoyeotaType.CaptionMd,
                color = MoyeotaColor.TextMute,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
        }

        // CTA — 채팅으로 물어보기 → 24 채팅
        PrimaryCtaButton(
            text = "채팅으로 물어보기",
            onClick = onChatClick,
            modifier = Modifier.padding(horizontal = MoyeotaSpacing.ScreenGutter),
            leadingIcon = { ChatBubbleIcon(size = 18.dp, tint = MoyeotaColor.TextOnDark) },
        )
        Spacer(Modifier.height(16.dp))
        NavigationBarSpacer()
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun PartnerProfileScreenPreview() {
    // 프리뷰에서만 쓰는 예시 값 — 실행 경로에는 흘러가지 않는다(호출부가 실제 멤버를 넘긴다)
    PartnerProfileScreen(
        user = User(
            id = "partner-1",
            nickname = "부산불곰",
            verifiedLabel = "",
            rating = 0.0,
            rideCount = 12,
        ),
    )
}
