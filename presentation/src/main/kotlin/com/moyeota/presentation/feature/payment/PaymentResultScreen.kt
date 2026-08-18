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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.moyeota.core.designsystem.component.PrimaryCtaButton
import com.moyeota.core.designsystem.component.StatusBarMock
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.core.designsystem.theme.MoyeotaType

// 32 · 결제 결과 · 영수증 [S21]
// 진입: 29 결제 성공 (결제 실패는 이 화면에 도달하지 않음 — 29에서 처리)
// 뒤로 → 29 정산 / 「확인」 → 33 도착 완료 · 평가
// 「공유」 → 시스템 공유 시트 (미연결) / 「영수증 자세히 보기」 → 미연결
// 영수증 보관 30일 — 이후 34/35 기록에서도 조회 불가

private val ResultLabelGray = Color(0xFF8A93A0)
private val ResultSlateGray = Color(0xFF4B5563)
private val ResultFooterGray = Color(0xFF9AA1AC)
private val ResultReceiptBlue = Color(0xFFF1F5FD)
private val ResultShareBg = Color(0xFFEEF1F6)
private val ResultSuccessCircle = Color(0xFFD9F3E6)

@Composable
fun PaymentResultScreen(
    totalFare: Int = 10_200,               // 총 요금
    serviceFee: Int = 600,                 // 서비스 수수료 (전체)
    memberCount: Int = 3,                  // 분담 인원
    methodName: String = "카카오페이",       // 결제 수단
    paidAtLabel: String = "7월 24일 오후 6:58", // 결제 일시
    onBack: () -> Unit = {},               // → 29 정산
    onConfirm: () -> Unit = {},            // 「확인」 → 33 도착 완료 · 평가
) {
    // 공통 규칙: 1인 부담 = (총 요금 + 서비스 수수료) ÷ 분담 인원, 10원 단위
    val myShare = perPersonShare(totalFare, serviceFee, memberCount)
    val feePerPerson = if (memberCount > 0) serviceFee / memberCount else 0

    Column(modifier = Modifier.fillMaxSize().background(MoyeotaColor.SurfaceSoft)) {
        StatusBarMock()
        PaymentResultHeader(title = "결제 완료", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(10.dp))

            // 성공 아이콘
            Box(
                modifier = Modifier.size(76.dp).background(ResultSuccessCircle, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.size(31.dp)) {
                    val stroke = 3.5.dp.toPx()
                    drawLine(
                        color = MoyeotaColor.Success500,
                        start = Offset(size.width * 0.08f, size.height * 0.55f),
                        end = Offset(size.width * 0.38f, size.height * 0.85f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = MoyeotaColor.Success500,
                        start = Offset(size.width * 0.38f, size.height * 0.85f),
                        end = Offset(size.width * 0.92f, size.height * 0.18f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                }
            }

            Spacer(Modifier.height(26.dp))
            Text(
                text = "내 부담 (수수료 ${formatResultWon(feePerPerson)} 포함)",
                style = MoyeotaType.BodyMd.copy(fontWeight = FontWeight.Medium),
                color = ResultLabelGray,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = formatResultWon(myShare),
                style = MoyeotaType.DisplayXl,
                color = MoyeotaColor.InkPrimary,
            )
            Spacer(Modifier.height(26.dp))

            // 영수증 카드
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(18.dp))
                    .background(Color.White, RoundedCornerShape(18.dp))
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                PaymentResultRow(label = "결제 수단", value = methodName)
                HorizontalDivider(color = MoyeotaColor.Hairline)
                PaymentResultRow(label = "결제 일시", value = paidAtLabel)
                HorizontalDivider(color = MoyeotaColor.Hairline)
                PaymentResultRow(label = "총 요금", value = formatResultWon(totalFare))
                HorizontalDivider(color = MoyeotaColor.Hairline)
                PaymentResultRow(
                    label = "서비스 수수료",
                    value = "${formatResultWon(serviceFee)} (1인 ${formatResultWon(feePerPerson)})",
                )
                HorizontalDivider(color = MoyeotaColor.Hairline)
                PaymentResultRow(label = "분담 인원", value = "${memberCount}명")
            }

            Spacer(Modifier.height(16.dp))

            // 영수증 자세히 보기 — 미연결
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ResultReceiptBlue, RoundedCornerShape(16.dp))
                    .clickable { /* 미연결 */ }
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "영수증 자세히 보기",
                    style = MoyeotaType.BodySm.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    color = MoyeotaColor.Primary600,
                    modifier = Modifier.weight(1f),
                )
                PaymentResultChevron()
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "요금 ${formatResultWon(totalFare)} + 수수료 ${formatResultWon(serviceFee)}을 ${memberCount}명이 나눠 냈어요",
                style = MoyeotaType.CaptionMd,
                color = ResultFooterGray,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
        }

        // 하단: 공유 (미연결) + 확인 → 33
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .width(112.dp)
                    .height(52.dp)
                    .background(ResultShareBg, CircleShape)
                    .clickable { /* 미연결: 시스템 공유 시트 */ },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PaymentResultShareIcon()
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "공유",
                    style = MoyeotaType.BodyMd.copy(fontWeight = FontWeight.Bold),
                    color = ResultSlateGray,
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                PrimaryCtaButton(text = "확인", onClick = onConfirm)
            }
        }
    }
}

private fun formatResultWon(amount: Int): String = "%,d원".format(amount)

@Composable
private fun PaymentResultHeader(title: String, onBack: () -> Unit) {
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
private fun PaymentResultRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MoyeotaType.BodySm.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
            color = MoyeotaColor.TextMute,
        )
        Text(
            text = value,
            style = MoyeotaType.BodySm.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
            color = MoyeotaColor.InkPrimary,
        )
    }
}

@Composable
private fun PaymentResultChevron(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(16.dp)) {
        val stroke = 2.dp.toPx()
        drawLine(
            color = MoyeotaColor.Primary600,
            start = Offset(size.width * 0.35f, size.height * 0.2f),
            end = Offset(size.width * 0.7f, size.height * 0.5f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = MoyeotaColor.Primary600,
            start = Offset(size.width * 0.7f, size.height * 0.5f),
            end = Offset(size.width * 0.35f, size.height * 0.8f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

// 공유 아이콘 — 위로 향한 화살표 + 트레이
@Composable
private fun PaymentResultShareIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(18.dp)) {
        val stroke = 1.8.dp.toPx()
        val color = ResultSlateGray
        // 화살대
        drawLine(
            color = color,
            start = Offset(size.width * 0.5f, size.height * 0.08f),
            end = Offset(size.width * 0.5f, size.height * 0.55f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        // 화살촉
        drawLine(
            color = color,
            start = Offset(size.width * 0.3f, size.height * 0.26f),
            end = Offset(size.width * 0.5f, size.height * 0.08f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.7f, size.height * 0.26f),
            end = Offset(size.width * 0.5f, size.height * 0.08f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        // 트레이
        drawLine(
            color = color,
            start = Offset(size.width * 0.15f, size.height * 0.45f),
            end = Offset(size.width * 0.15f, size.height * 0.9f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.15f, size.height * 0.9f),
            end = Offset(size.width * 0.85f, size.height * 0.9f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.85f, size.height * 0.9f),
            end = Offset(size.width * 0.85f, size.height * 0.45f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun PaymentResultScreenPreview() {
    PaymentResultScreen()
}
