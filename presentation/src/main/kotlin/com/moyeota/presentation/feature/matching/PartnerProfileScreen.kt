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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeota.core.designsystem.component.AvatarCircle
import com.moyeota.core.designsystem.component.BackArrowIcon
import com.moyeota.core.designsystem.component.StatusBarMock
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.domain.model.User

// 와이어프레임 그레이 (core token 미정의 색 — 화면 재현용)
private val CanvasBg = Color(0xFFF5F7FA)
private val GraySlate = Color(0xFF4B5563)
private val GrayDeep = Color(0xFF54637D)
private val GrayMute = Color(0xFF8A93A0)
private val GrayAsh = Color(0xFF9AA1AC)
private val ChipBg = Color(0xFFF1F5FD)

private val partnerDummy = User(
    id = "partner-1",
    nickname = "김OO",
    verifiedLabel = "부산대학교 · 2026년 3월 인증",
    rating = 4.9,
    rideCount = 12,
)

/**
 * 23 · 동승자 프로필 [S13]
 *
 * 이동(디스크립션):
 * - 뒤로 → 22 탑승 상세 (onBack)
 * - 「채팅으로 물어보기」 → 24 채팅 (onChatClick)
 * - 「신고」 → [미연결] 신고 사유 시트 필요 (onReport — 무동작 기본값)
 * - 탑승 이력 0회면 매너 점수 대신 「첫 탑승」
 */
@Composable
fun PartnerProfileScreen(
    user: User = partnerDummy,
    mannerPercent: Int = 98,
    noShowCount: Int = 0,
    verifiedItems: List<Pair<String, String>> = listOf(
        "학교 이메일 인증" to "2026.03 완료",
        "휴대폰 본인 인증" to "완료",
        "결제 수단 등록" to "카카오페이",
    ),
    reviewTags: List<Pair<String, Int>> = listOf(
        "시간 약속을 잘 지켜요" to 8,
        "조용하고 편했어요" to 5,
        "정산이 깔끔해요" to 3,
    ),
    onBack: () -> Unit = {},
    onChatClick: () -> Unit = {},
    onReport: () -> Unit = {}, // 미연결
) {
    Column(modifier = Modifier.fillMaxSize().background(CanvasBg)) {
        StatusBarMock()

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
            Spacer(Modifier.weight(1f))
            Text(
                text = "신고",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = GrayMute,
                modifier = Modifier.clickable { onReport() },
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

            // 아바타 + 인증 배지
            Box(modifier = Modifier.align(Alignment.CenterHorizontally)) {
                AvatarCircle(size = 80.dp)
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
            Spacer(Modifier.height(12.dp))
            Text(
                text = user.nickname,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MoyeotaColor.InkPrimary,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = user.verifiedLabel,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = GrayMute,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(20.dp))

            // 지표 카드 — 매너 점수 · 탑승 횟수 · 노쇼
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .shadow(4.dp, RoundedCornerShape(18.dp), spotColor = Color(0x0F1B2A4A))
                    .clip(RoundedCornerShape(18.dp))
                    .background(MoyeotaColor.SurfaceCanvas),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatCell(
                    // 탑승 이력 0회면 매너 점수 대신 「첫 탑승」
                    value = if (user.rideCount == 0) "첫 탑승" else "${mannerPercent}%",
                    label = "매너 점수",
                    modifier = Modifier.weight(1f),
                )
                Box(Modifier.size(width = 1.dp, height = 44.dp).background(MoyeotaColor.Hairline))
                StatCell(value = "${user.rideCount}회", label = "탑승 횟수", modifier = Modifier.weight(1f))
                Box(Modifier.size(width = 1.dp, height = 44.dp).background(MoyeotaColor.Hairline))
                StatCell(value = "${noShowCount}회", label = "노쇼 · 무단취소", modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(20.dp))

            // 확인된 정보
            Text(text = "확인된 정보", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = GrayMute)
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(4.dp, RoundedCornerShape(18.dp), spotColor = Color(0x0F1B2A4A))
                    .clip(RoundedCornerShape(18.dp))
                    .background(MoyeotaColor.SurfaceCanvas)
                    .padding(horizontal = 20.dp),
            ) {
                verifiedItems.forEachIndexed { index, (label, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CheckSmallIcon(tint = MoyeotaColor.Success500)
                        Spacer(Modifier.width(10.dp))
                        Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = GraySlate)
                        Spacer(Modifier.weight(1f))
                        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = GrayMute)
                    }
                    if (index != verifiedItems.lastIndex) {
                        HorizontalDivider(color = MoyeotaColor.Hairline)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))

            // 후기 태그 집계
            Text(text = "함께 탄 사람들이 남긴 말", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = GrayMute)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                reviewTags.chunked(2).forEach { rowTags ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowTags.forEach { (tag, count) ->
                            Row(
                                modifier = Modifier
                                    .background(ChipBg, CircleShape)
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(text = tag, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = GrayDeep)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "$count",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MoyeotaColor.Primary600,
                                )
                            }
                        }
                    }
                }
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
        HomeIndicator()
    }
}

@Composable
private fun StatCell(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MoyeotaColor.InkPrimary)
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

// 홈 인디케이터 (와이어프레임 하단 검은 바)
@Composable
private fun HomeIndicator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(CanvasBg)
            .padding(vertical = 8.dp),
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
private fun PartnerProfileScreenPreview() {
    PartnerProfileScreen()
}
