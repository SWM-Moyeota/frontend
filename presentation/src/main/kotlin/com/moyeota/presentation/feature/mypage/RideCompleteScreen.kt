package com.moyeota.presentation.feature.mypage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.moyeota.core.designsystem.component.NavigationBarSpacer
import com.moyeota.core.designsystem.component.PrimaryCtaButton
import com.moyeota.core.designsystem.component.StatusBarSpacer
import com.moyeota.core.designsystem.theme.MoyeotaColor

// 와이어프레임 그레이 (core token 미정의 색 — 화면 재현용)
private val GrayMute = Color(0xFF8A93A0)
private val SoftBg = Color(0xFFF6F8FB)
private val SoftDivider = Color(0xFFE4E9F0)

/**
 * 33 · 도착 완료 [S18]
 *
 * 진입: 26 운행 중에서 서버 FINISHED 감지 시 자동 전이(26 은 스택에서 제거) — 하단탭 없음
 *
 * 이동(디스크립션):
 * - 「홈으로」 → 14 홈 (onDone)
 *
 * 표시 값(전부 진행 중이던 방 [com.moyeota.domain.model.Ride] 에서 온다):
 * - [routeLabel] 「출발지 → 도착지」. null 이면 줄을 숨긴다
 * - 요약 카드 「내가 낸 돈」(farePerPerson) · 「함께 탄 사람」(나를 뺀 멤버 수). 값이 없는 칸은 빼고,
 *   두 칸 다 없으면 카드 자체를 숨긴다
 *
 * 동승자 평가(좋았어요/아쉬웠어요 · 태그)와 「아낀 돈」은 뺐다 — 평가를 받을 서버 API 가 없고,
 * 아낀 돈은 계산 근거(혼자 탔을 때 요금)가 확정되지 않았다.
 */
@Composable
fun RideCompleteScreen(
    routeLabel: String?,
    paidAmount: Int?,
    companionCount: Int?,
    onDone: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().background(MoyeotaColor.SurfaceCanvas)) {
        StatusBarSpacer()

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
            if (routeLabel != null) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = routeLabel,
                    fontSize = 15.sp,
                    color = MoyeotaColor.TextMute,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }

            // 요약 카드 — 내가 낸 돈 / 함께 탄 사람 (값이 있는 칸만)
            val cells = buildList {
                if (paidAmount != null) add("%,d원".format(paidAmount) to "내가 낸 돈")
                if (companionCount != null) add("${companionCount}명" to "함께 탄 사람")
            }
            if (cells.isNotEmpty()) {
                Spacer(Modifier.height(22.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(96.dp)
                        .background(SoftBg, RoundedCornerShape(18.dp)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    cells.forEachIndexed { index, (value, label) ->
                        if (index > 0) {
                            Box(Modifier.size(width = 1.dp, height = 44.dp).background(SoftDivider))
                        }
                        SummaryCell(value = value, label = label, modifier = Modifier.weight(1f))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }

        PrimaryCtaButton(
            text = "홈으로",
            onClick = onDone,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(16.dp))
        NavigationBarSpacer()
    }
}

@Composable
private fun SummaryCell(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MoyeotaColor.InkPrimary)
        Spacer(Modifier.height(4.dp))
        Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = GrayMute)
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

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun RideCompleteScreenPreview() {
    RideCompleteScreen(
        routeLabel = "출발지 → 도착지",
        paidAmount = 4000,
        companionCount = 2,
        onDone = {},
    )
}
