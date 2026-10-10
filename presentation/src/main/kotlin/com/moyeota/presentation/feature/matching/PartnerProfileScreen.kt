package com.moyeota.presentation.feature.matching

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeota.core.designsystem.component.BackArrowIcon
import com.moyeota.core.designsystem.component.NavigationBarSpacer
import com.moyeota.core.designsystem.component.StatusBarSpacer
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.domain.model.User
import com.moyeota.presentation.core.MemberAvatar
import com.moyeota.presentation.core.displayNickname

// 와이어프레임 그레이 (core token 미정의 색 — 화면 재현용)
private val CanvasBg = Color(0xFFF5F7FA)
private val GrayMute = Color(0xFF8A93A0)
private val GrayAsh = Color(0xFF9AA1AC)

/**
 * 23 · 동승자 프로필 [S13]
 *
 * 이동(디스크립션):
 * - 뒤로 → 22 탑승 상세 (onBack)
 * - 「채팅으로 물어보기」 → 24 채팅 (onChatClick)
 *
 * 서버 계약(2026-09): 방 상세 `members[]` 가 주는 것은 닉네임·이미지·탑승 횟수뿐이다.
 * 그래서 이 화면은 그 값만 보여 준다 — 지표는 탑승 횟수 한 칸이고, 서버에 없는 평가·집계·검증
 * 항목은 「준비 중」 자리표시도 두지 않고 요소째 뺐다. 프로필은 사람을 판단하는 화면이라
 * 지어낸 수치나 빈 칸 하나가 그대로 신뢰의 근거가 된다.
 *
 * @param user 22·20 에서 탭한 그 멤버. 호출부가 반드시 넘긴다(기본값 없음).
 */
@Composable
fun PartnerProfileScreen(
    user: User,
    onBack: () -> Unit = {},
    onChatClick: () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxSize().background(CanvasBg)) {
        StatusBarSpacer()

        // 헤더
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { BackArrowIcon() }
            Text(
                text = "프로필",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MoyeotaColor.InkPrimary,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
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
                        CheckSmallIcon(tint = MoyeotaColor.TextOnDark)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = user.displayNickname,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MoyeotaColor.InkPrimary,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            // 서버가 배지 라벨을 줄 때만 한 줄 붙인다 — 없으면 줄째 생략한다
            if (user.verifiedLabel.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = user.verifiedLabel,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = GrayMute,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            Spacer(Modifier.height(20.dp))

            // 지표 카드 — 서버가 주는 지표는 탑승 횟수 하나뿐이다
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .shadow(4.dp, RoundedCornerShape(18.dp), spotColor = Color(0x0F1B2A4A))
                    .clip(RoundedCornerShape(18.dp))
                    .background(MoyeotaColor.SurfaceCanvas),
                contentAlignment = Alignment.Center,
            ) {
                StatCell(
                    value = if (user.rideCount <= 0) "첫 탑승" else "${user.rideCount}회",
                    label = "탑승 횟수",
                    valueFontSize = if (user.rideCount <= 0) 16.sp else 20.sp,
                )
            }
            Spacer(Modifier.height(20.dp))

            Text(
                text = "실명과 학번은 서로에게 공개되지 않아요",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = GrayAsh,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
        }

        // CTA — 채팅으로 물어보기 → 24 채팅
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(56.dp)
                .shadow(10.dp, RoundedCornerShape(16.dp), spotColor = Color(0x42085AF5))
                .clip(RoundedCornerShape(16.dp))
                .background(MoyeotaColor.Primary500)
                .clickable { onChatClick() },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChatBubbleIcon()
            Spacer(Modifier.width(9.dp))
            Text(
                text = "채팅으로 물어보기",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MoyeotaColor.TextOnDark,
            )
        }
        Spacer(Modifier.height(12.dp))
        NavigationBarSpacer(Modifier.background(CanvasBg))
    }
}

@Composable
private fun StatCell(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    // 숫자 대신 「첫 탑승」 같은 문구가 들어가면 작게 줄인다
    valueFontSize: TextUnit = 20.sp,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = valueFontSize,
            fontWeight = FontWeight.Bold,
            color = MoyeotaColor.InkPrimary,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(2.dp))
        Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = GrayMute)
    }
}

// ─── 아이콘 (material-icons 미사용 — Canvas 직접 드로잉) ─────────────────────

@Composable
private fun CheckSmallIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(14.dp)) {
        val w = size.width
        val h = size.height
        val strokeWidth = 2.dp.toPx()
        drawLine(tint, Offset(w * 0.12f, h * 0.55f), Offset(w * 0.42f, h * 0.82f), strokeWidth, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.42f, h * 0.82f), Offset(w * 0.88f, h * 0.2f), strokeWidth, StrokeCap.Round)
    }
}

@Composable
private fun ChatBubbleIcon(modifier: Modifier = Modifier, tint: Color = MoyeotaColor.TextOnDark) {
    Canvas(modifier = modifier.size(19.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.08f, h * 0.12f),
            size = Size(w * 0.84f, h * 0.6f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
            style = stroke,
        )
        // 말풍선 꼬리
        drawLine(tint, Offset(w * 0.3f, h * 0.72f), Offset(w * 0.3f, h * 0.92f), stroke.width, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.3f, h * 0.92f), Offset(w * 0.5f, h * 0.72f), stroke.width, StrokeCap.Round)
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
