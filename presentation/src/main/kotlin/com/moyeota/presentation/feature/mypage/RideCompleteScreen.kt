package com.moyeota.presentation.feature.mypage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeota.core.designsystem.component.AvatarCircle
import com.moyeota.core.designsystem.component.PrimaryCtaButton
import com.moyeota.core.designsystem.component.StatusBarMock
import com.moyeota.core.designsystem.theme.MoyeotaColor

// 와이어프레임 그레이 (core token 미정의 색 — 화면 재현용)
private val GraySlate = Color(0xFF4B5563)
private val GrayMute = Color(0xFF8A93A0)
private val GrayAsh = Color(0xFF9AA1AC)
private val SoftBg = Color(0xFFF6F8FB)
private val SoftDivider = Color(0xFFE4E9F0)

/**
 * 33 · 도착 완료 · 평가 [S18]
 *
 * 진입: 32 「확인」 / 플로우 진행 화면 — 하단탭 없음
 *
 * 이동(디스크립션):
 * - 「평가 보내기」 → 14 홈 (onSubmit)
 * - 「다음에 할게요」 → 14 홈 (onSkip)
 * - [미연결] 동승자 이름 탭 → 23 프로필
 *
 * 검증(디스크립션):
 * - 평가는 선택 사항 — 미평가로 종료 가능
 * - 「좋았어요/아쉬웠어요」 미선택 시 「평가 보내기」 비활성
 * - 「좋았어요 / 아쉬웠어요」 선택 → 태그 목록 노출, 태그 0개 이상 다중 선택
 * - 「아쉬웠어요」 선택 시 사유 태그 세트가 부정형으로 교체
 */
@Composable
fun RideCompleteScreen(
    arrivalPlace: String = "서면역 1번 출구",
    arrivalTime: String = "오후 6:57",
    paidLabel: String = "3,600원",
    savedLabel: String = "6,600원",
    companionCountLabel: String = "2명",
    companionName: String = "김OO",
    companionMeta: String = "부산대 인증 · 탑승 12회",
    positiveTags: List<String> = listOf("시간 약속을 잘 지켜요", "조용하고 편했어요", "정산이 깔끔해요"),
    negativeTags: List<String> = listOf("시간 약속을 안 지켰어요", "조금 불편했어요", "정산이 늦어졌어요"),
    onSubmit: (liked: Boolean, tags: List<String>) -> Unit = { _, _ -> },
    onSkip: () -> Unit = {},
) {
    // 내부 상태: 좋았어요/아쉬웠어요 + 선택 태그 (감정 전환 시 태그 초기화)
    var liked by remember { mutableStateOf<Boolean?>(null) }
    var selectedTags by remember { mutableStateOf(setOf<String>()) }

    Column(modifier = Modifier.fillMaxSize().background(MoyeotaColor.SurfaceCanvas)) {
        StatusBarMock()

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(82.dp))

            // 도착 완료 체크 원
            Box(
                modifier = Modifier.size(76.dp).background(MoyeotaColor.Success50, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                CheckIcon(color = MoyeotaColor.Success600)
            }

            Spacer(Modifier.height(22.dp))
            Text(
                text = "잘 도착했어요",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MoyeotaColor.InkPrimary,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "$arrivalPlace · $arrivalTime",
                fontSize = 15.sp,
                color = MoyeotaColor.TextMute,
            )

            Spacer(Modifier.height(22.dp))
            // 요약 카드 — 내가 낸 돈 / 아낀 돈 / 함께 탄 사람
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(96.dp)
                    .background(SoftBg, RoundedCornerShape(18.dp)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SummaryCell(value = paidLabel, label = "내가 낸 돈", valueColor = MoyeotaColor.InkPrimary, modifier = Modifier.weight(1f))
                Box(Modifier.size(width = 1.dp, height = 44.dp).background(SoftDivider))
                SummaryCell(value = savedLabel, label = "아낀 돈", valueColor = MoyeotaColor.Success600, modifier = Modifier.weight(1f))
                Box(Modifier.size(width = 1.dp, height = 44.dp).background(SoftDivider))
                SummaryCell(value = companionCountLabel, label = "함께 탄 사람", valueColor = MoyeotaColor.InkPrimary, modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(34.dp))
            // 동승자 평가
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(
                    text = "오늘 같이 탄 사람은 어땠나요?",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MoyeotaColor.InkPrimary,
                )
                Spacer(Modifier.height(10.dp))
                // 동승자 이름 탭 → 23 프로필 (미연결 — 무동작)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvatarCircle(size = 36.dp)
                    Spacer(Modifier.size(10.dp))
                    Column {
                        Text(
                            text = companionName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MoyeotaColor.InkPrimary,
                        )
                        Text(
                            text = companionMeta,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = GrayMute,
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                // 좋았어요 / 아쉬웠어요
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    SentimentButton(
                        text = "좋았어요",
                        up = true,
                        selected = liked == true,
                        onClick = {
                            if (liked != true) selectedTags = emptySet()
                            liked = true
                        },
                        modifier = Modifier.weight(1f),
                    )
                    SentimentButton(
                        text = "아쉬웠어요",
                        up = false,
                        selected = liked == false,
                        onClick = {
                            if (liked != false) selectedTags = emptySet()
                            liked = false
                        },
                        modifier = Modifier.weight(1f),
                    )
                }

                // 감정 선택 후 태그 목록 노출 — 아쉬웠어요 선택 시 부정형 세트로 교체
                if (liked != null) {
                    val tags = if (liked == true) positiveTags else negativeTags
                    Spacer(Modifier.height(26.dp))
                    Text(
                        text = if (liked == true) "어떤 점이 좋았나요? (여러 개 선택 가능)" else "어떤 점이 아쉬웠나요? (여러 개 선택 가능)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = GrayMute,
                    )
                    Spacer(Modifier.height(9.dp))
                    TagFlow(
                        tags = tags,
                        selectedTags = selectedTags,
                        onToggle = { tag ->
                            selectedTags = if (tag in selectedTags) selectedTags - tag else selectedTags + tag
                        },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }

        // 「좋았어요/아쉬웠어요」 미선택 시 비활성 → 14 홈
        PrimaryCtaButton(
            text = "평가 보내기",
            onClick = { onSubmit(liked == true, selectedTags.toList()) },
            enabled = liked != null,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "다음에 할게요",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = GrayAsh,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(8.dp))
                .clickable { onSkip() }
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
        Spacer(Modifier.height(6.dp))
        HomeIndicatorBar()
    }
}

@Composable
private fun SummaryCell(value: String, label: String, valueColor: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = valueColor)
        Spacer(Modifier.height(4.dp))
        Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = GrayMute)
    }
}

@Composable
private fun SentimentButton(
    text: String,
    up: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    val bg = if (selected) MoyeotaColor.Primary50 else SoftBg
    val fg = if (selected) MoyeotaColor.Primary600 else GrayMute
    Row(
        modifier = modifier
            .height(56.dp)
            .clip(shape)
            .background(bg)
            .then(if (selected) Modifier.border(1.5.dp, MoyeotaColor.Primary500, shape) else Modifier)
            .clickable { onClick() },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThumbIcon(up = up, color = fg)
        Spacer(Modifier.size(7.dp))
        Text(text = text, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagFlow(
    tags: List<String>,
    selectedTags: Set<String>,
    onToggle: (String) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tags.forEach { tag ->
            val selected = tag in selectedTags
            Box(
                modifier = Modifier
                    .height(36.dp)
                    .clip(CircleShape)
                    .background(if (selected) MoyeotaColor.Primary50 else SoftBg)
                    .clickable { onToggle(tag) }
                    .padding(horizontal = 15.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = tag,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) MoyeotaColor.Primary600 else GraySlate,
                )
            }
        }
    }
}

// ─── 아이콘 (material-icons 미사용 — Canvas 직접 드로잉) ─────────────────────

@Composable
private fun CheckIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(31.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 3.5.dp.toPx()
        drawLine(color, Offset(w * 0.12f, h * 0.55f), Offset(w * 0.4f, h * 0.82f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.4f, h * 0.82f), Offset(w * 0.88f, h * 0.22f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun ThumbIcon(up: Boolean, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(19.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit = {
            // 손바닥 (왼쪽 세로 막대)
            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.06f, h * 0.45f),
                size = androidx.compose.ui.geometry.Size(w * 0.2f, h * 0.45f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx()),
                style = stroke,
            )
            // 엄지 + 몸통
            val path = Path().apply {
                moveTo(w * 0.32f, h * 0.5f)
                lineTo(w * 0.52f, h * 0.14f)
                quadraticTo(w * 0.68f, h * 0.16f, w * 0.62f, h * 0.42f)
                lineTo(w * 0.88f, h * 0.42f)
                quadraticTo(w * 0.96f, h * 0.48f, w * 0.92f, h * 0.6f)
                lineTo(w * 0.84f, h * 0.86f)
                quadraticTo(w * 0.82f, h * 0.9f, w * 0.76f, h * 0.9f)
                lineTo(w * 0.32f, h * 0.9f)
                close()
            }
            drawPath(path, color, style = stroke)
        }
        if (up) draw() else scale(scaleX = 1f, scaleY = -1f) { draw() }
    }
}

// 홈 인디케이터 (와이어프레임 하단 검은 바)
@Composable
private fun HomeIndicatorBar() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
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
private fun RideCompleteScreenPreview() {
    RideCompleteScreen()
}
