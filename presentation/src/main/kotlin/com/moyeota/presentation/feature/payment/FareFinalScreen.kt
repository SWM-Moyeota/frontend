package com.moyeota.presentation.feature.payment

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeota.core.designsystem.component.BackArrowIcon
import com.moyeota.core.designsystem.component.NoticeBanner
import com.moyeota.core.designsystem.component.NoticeKind
import com.moyeota.core.designsystem.component.PrimaryCtaButton
import com.moyeota.core.designsystem.component.StatusBadge
import com.moyeota.core.designsystem.component.StatusBarMock
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.core.designsystem.theme.MoyeotaType

// 28 · 최종 요금 확인 (기사 입력) [S19a]
// 진입: 26 도착 (프로토타입 미연결)
// 뒤로 → 26 운행 중 / 「확인했어요 · 정산으로」 → 29 정산 / 「영수증」·「이의 신청」 → 미연결

private val FareLabelGray = Color(0xFF8A93A0)
private val FareSlateGray = Color(0xFF4B5563)
private val FareFooterGray = Color(0xFF9AA1AC)
private val FareInfoCardBlue = Color(0xFFF1F5FD)
private val KakaoYellow = Color(0xFFFEE500)
private val KakaoBrown = Color(0xFF3C1E1E)

// 예상 대비 30% 이상 초과면 확인 전 이의 신청 유도 배너 강제 노출 (임계값 TBD)
private const val OverchargeThreshold = 1.3

@Composable
fun FareFinalScreen(
    finalFare: Int = 10_200,      // 최종 요금 = 기사 입력값
    expectedFare: Int = 9_600,    // 탑승 전 예상 요금
    onBack: () -> Unit = {},      // → 26 운행 중
    onConfirm: () -> Unit = {},   // → 29 정산 (확인이 정산 트리거 — 확인 전에는 1/N 미계산)
) {
    var submitting by remember { mutableStateOf(false) }

    // 예상 요금과 차이를 항상 병기
    val diff = finalFare - expectedFare
    val diffLabel = if (diff >= 0) "+${formatWon(diff)}" else "-${formatWon(-diff)}"
    val overcharged = expectedFare > 0 && finalFare >= expectedFare * OverchargeThreshold

    Column(modifier = Modifier.fillMaxSize().background(MoyeotaColor.SurfaceSoft)) {
        StatusBarMock()
        FareFinalHeader(title = "최종 요금 확인", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(46.dp))
            Text(
                text = formatWon(finalFare),
                style = MoyeotaType.DisplayXl.copy(fontSize = 38.sp, letterSpacing = (-1).sp),
                color = MoyeotaColor.InkPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                StatusBadge(kind = NoticeKind.INFO, text = "기사 입력 · 미터기 요금")
            }
            Spacer(Modifier.height(32.dp))

            // 분담 내역 카드
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(18.dp))
                    .background(Color.White, RoundedCornerShape(18.dp))
                    .padding(20.dp),
            ) {
                FareFinalRow(label = "미터기 요금", value = formatWon(finalFare))
                Spacer(Modifier.height(16.dp))
                FareFinalRow(label = "탑승 전 예상 요금", value = formatWon(expectedFare))
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MoyeotaColor.Hairline)
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MoyeotaColor.Primary50, RoundedCornerShape(14.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "차이",
                        style = MoyeotaType.BodyMd.copy(fontWeight = FontWeight.Bold),
                        color = MoyeotaColor.Primary600,
                    )
                    Text(
                        text = diffLabel,
                        style = MoyeotaType.HeadingLg.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold),
                        color = MoyeotaColor.Primary500,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // 기사 입력 정보 카드 — 「영수증」 미연결
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(18.dp))
                    .background(Color.White, RoundedCornerShape(18.dp))
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FareFinalKakaoLogo()
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "기사 김OO · 부산 12가 3456",
                        style = MoyeotaType.BodyMd.copy(fontWeight = FontWeight.Bold),
                        color = MoyeotaColor.InkPrimary,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "오후 6:58 입력 · 도착 확인 완료",
                        style = MoyeotaType.CaptionMd,
                        color = FareLabelGray,
                    )
                }
                // 미연결: 영수증 상세
                Text(
                    text = "영수증",
                    style = MoyeotaType.BodySm.copy(fontWeight = FontWeight.Bold),
                    color = MoyeotaColor.Primary500,
                    modifier = Modifier.clickable { /* 미연결 */ },
                )
            }

            Spacer(Modifier.height(20.dp))

            // 안내 카드
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FareInfoCardBlue, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                FareFinalCheckRow(text = "기사가 입력한 최종 요금이에요")
                FareFinalCheckRow(text = "확인하면 1/N 분담 금액이 계산돼요")
                FareFinalCheckRow(text = "금액이 다르면 확인 전에 이의를 넣어 주세요")
            }
            Spacer(Modifier.height(20.dp))
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            if (overcharged) {
                // 예상 대비 30% 이상 초과 — 이의 신청 유도 배너 강제 노출 (이의 신청 플로우 미연결)
                NoticeBanner(
                    kind = NoticeKind.ERROR,
                    text = "예상 요금보다 30% 이상 높아요. 확인 전에 «영수증»에서 이의 신청을 권해요",
                )
                Spacer(Modifier.height(12.dp))
            }
            PrimaryCtaButton(
                text = "확인했어요 · 정산으로",
                onClick = {
                    submitting = true
                    onConfirm()
                },
                loading = submitting,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "금액이 다르면 «영수증»에서 이의 신청",
                style = MoyeotaType.CaptionMd,
                color = FareFooterGray,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun formatWon(amount: Int): String = "%,d원".format(amount)

@Composable
private fun FareFinalHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.clickable { onBack() }) {
            BackArrowIcon(Modifier.size(22.dp))
        }
        Spacer(Modifier.size(8.dp))
        Text(text = title, style = MoyeotaType.HeadingLg, color = MoyeotaColor.InkPrimary)
    }
}

@Composable
private fun FareFinalRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MoyeotaType.BodyMd.copy(fontWeight = FontWeight.Medium),
            color = MoyeotaColor.TextMute,
        )
        Text(
            text = value,
            style = MoyeotaType.BodyMd.copy(fontWeight = FontWeight.Bold),
            color = MoyeotaColor.InkPrimary,
        )
    }
}

@Composable
private fun FareFinalKakaoLogo(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(34.dp).background(KakaoYellow, RoundedCornerShape(11.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "pay",
            style = MoyeotaType.BodySm.copy(fontWeight = FontWeight.Bold),
            color = KakaoBrown,
        )
    }
}

@Composable
private fun FareFinalCheckRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        FareFinalCheckIcon()
        Text(text = text, style = MoyeotaType.BodySm, color = FareSlateGray)
    }
}

@Composable
private fun FareFinalCheckIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(15.dp)) {
        val stroke = 2.dp.toPx()
        drawLine(
            color = MoyeotaColor.Primary500,
            start = Offset(size.width * 0.1f, size.height * 0.55f),
            end = Offset(size.width * 0.4f, size.height * 0.85f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = MoyeotaColor.Primary500,
            start = Offset(size.width * 0.4f, size.height * 0.85f),
            end = Offset(size.width * 0.9f, size.height * 0.2f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun FareFinalScreenPreview() {
    FareFinalScreen()
}
