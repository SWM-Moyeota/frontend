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
import kotlin.math.roundToInt

// 29 · 정산 — 1/N 확인 [S19]
// 진입: 28 확인 · 30 기본 수단 저장 · 32 뒤로
// 뒤로 → 28 최종 요금 확인 / 「결제 수단 · 변경」 → 30 결제 수단 선택 / 「3,600원 결제하기」 → 32 결제 결과 · 영수증
// 에러: 결제 실패 → 화면 유지 + danger 배너 (32로 이동하지 않음) / 미납 누적 시 다음 매칭 차단

private val SettleLabelGray = Color(0xFF8A93A0)
private val SettleSlateGray = Color(0xFF4B5563)
private val SettleFooterGray = Color(0xFF9AA1AC)
private val SettleInfoCardBlue = Color(0xFFF1F5FD)
private val SettleKakaoYellow = Color(0xFFFEE500)
private val SettleKakaoBrown = Color(0xFF3C1E1E)

// 공통 규칙: 1인 부담 = (최종 요금 + 서비스 수수료) ÷ 분담 인원, 10원 단위
// 예: (10,200 + 600) ÷ 3 = 3,600원 — 인원 변동 시 파라미터 변경으로 즉시 재계산
internal fun perPersonShare(totalFare: Int, serviceFee: Int, memberCount: Int): Int {
    if (memberCount <= 0) return 0
    val raw = (totalFare + serviceFee).toDouble() / memberCount
    return (raw / 10.0).roundToInt() * 10
}

@Composable
fun SettlementScreen(
    totalFare: Int = 10_200,             // 28에서 확정된 최종 요금
    serviceFee: Int = 600,               // 서비스 수수료
    memberCount: Int = 3,                // 분담 인원 (나 포함) — 변동 시 즉시 재계산
    methodName: String = "카카오페이",     // 30에서 저장한 기본 결제 수단
    methodDetail: String = "신한 ••••1234",
    hasPaymentMethod: Boolean = true,    // 결제 수단 미등록이면 CTA 비활성 → 30/31로 유도
    paymentErrorText: String? = null,    // 결제 실패 시 danger 배너 문구 (화면 유지)
    onBack: () -> Unit = {},             // → 28 최종 요금 확인
    onChangeMethod: () -> Unit = {},     // → 30 결제 수단 선택
    onPay: (amount: Int) -> Unit = {},   // 결제 성공 → 32 결제 결과 · 영수증
) {
    var submitting by remember { mutableStateOf(false) }

    // 인원이 줄어드는 등 값이 바뀌면 리컴포지션으로 즉시 재계산됨
    val myShare = perPersonShare(totalFare, serviceFee, memberCount)

    Column(modifier = Modifier.fillMaxSize().background(MoyeotaColor.SurfaceSoft)) {
        StatusBarMock()
        SettlementHeader(title = "정산", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(46.dp))
            Text(
                text = formatSettleWon(myShare),
                style = MoyeotaType.DisplayXl.copy(fontSize = 38.sp, letterSpacing = (-1).sp),
                color = MoyeotaColor.InkPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                StatusBadge(kind = NoticeKind.INFO, text = "확정 금액")
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
                SettlementRow(label = "총 요금", value = formatSettleWon(totalFare))
                Spacer(Modifier.height(16.dp))
                SettlementRow(label = "함께 탄 사람", value = "${memberCount}명 (나 포함)")
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
                        text = "1인 부담",
                        style = MoyeotaType.BodyMd.copy(fontWeight = FontWeight.Bold),
                        color = MoyeotaColor.Primary600,
                    )
                    Text(
                        text = formatSettleWon(myShare),
                        style = MoyeotaType.HeadingLg.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold),
                        color = MoyeotaColor.Primary500,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // 결제 수단 카드 — 「변경」 → 30 결제 수단 선택
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(18.dp))
                    .background(Color.White, RoundedCornerShape(18.dp))
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettlementKakaoLogo()
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (hasPaymentMethod) methodName else "결제 수단을 등록해 주세요",
                        style = MoyeotaType.BodyMd.copy(fontWeight = FontWeight.Bold),
                        color = MoyeotaColor.InkPrimary,
                    )
                    if (hasPaymentMethod) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = methodDetail,
                            style = MoyeotaType.CaptionMd,
                            color = SettleLabelGray,
                        )
                    }
                }
                Text(
                    text = "변경",
                    style = MoyeotaType.BodySm.copy(fontWeight = FontWeight.Bold),
                    color = MoyeotaColor.Primary500,
                    modifier = Modifier.clickable { onChangeMethod() },
                )
            }

            Spacer(Modifier.height(20.dp))

            // 안내 카드
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SettleInfoCardBlue, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                SettlementCheckRow(text = "수수료 ${formatSettleWon(serviceFee)} 포함 · 10원 단위로 나눠요")
                SettlementCheckRow(text = "인원이 줄면 차액은 자동으로 돌려드려요")
                SettlementCheckRow(text = "영수증은 앱에 30일 동안 남아 있어요")
            }
            Spacer(Modifier.height(20.dp))
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            if (paymentErrorText != null) {
                // 결제 실패 — 화면 유지, 다른 수단 선택 유도 (32로 이동하지 않음)
                NoticeBanner(kind = NoticeKind.ERROR, text = paymentErrorText)
                Spacer(Modifier.height(12.dp))
            }
            PrimaryCtaButton(
                text = "${formatSettleWon(myShare)} 결제하기",
                onClick = {
                    submitting = true
                    onPay(myShare)
                },
                enabled = hasPaymentMethod, // 결제 수단 미등록이면 비활성 → 30/31로 유도
                loading = submitting,       // 제출 중 중복 결제 차단
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "결제 정보는 암호화되어 저장돼요",
                style = MoyeotaType.CaptionMd,
                color = SettleFooterGray,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun formatSettleWon(amount: Int): String = "%,d원".format(amount)

@Composable
private fun SettlementHeader(title: String, onBack: () -> Unit) {
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
private fun SettlementRow(label: String, value: String) {
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
private fun SettlementKakaoLogo(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(34.dp).background(SettleKakaoYellow, RoundedCornerShape(11.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "pay",
            style = MoyeotaType.BodySm.copy(fontWeight = FontWeight.Bold),
            color = SettleKakaoBrown,
        )
    }
}

@Composable
private fun SettlementCheckRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        SettlementCheckIcon()
        Text(text = text, style = MoyeotaType.BodySm, color = SettleSlateGray)
    }
}

@Composable
private fun SettlementCheckIcon(modifier: Modifier = Modifier) {
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
private fun SettlementScreenPreview() {
    SettlementScreen()
}
