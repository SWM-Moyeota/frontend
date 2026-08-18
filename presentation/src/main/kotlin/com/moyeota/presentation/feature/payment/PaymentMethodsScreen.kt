package com.moyeota.presentation.feature.payment

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
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
import com.moyeota.core.designsystem.component.PrimaryCtaButton
import com.moyeota.core.designsystem.component.StatusBarMock
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.core.designsystem.theme.MoyeotaType

// 30 · 결제 수단 선택 [S20]
// 진입: 29 결제 수단 변경 · 31 추가 완료
// 뒤로 → 29 정산 / 「+ 결제 수단 추가하기」 → 31 결제 수단 추가 / 「기본 수단으로 저장」 → 29 정산 (선택값 반영)
// 등록 수단 행 탭 → 라디오 선택 / [미연결] 수단 삭제 · 수단 상세

private val MethodsLabelGray = Color(0xFF8A93A0)
private val MethodsSlateGray = Color(0xFF4B5563)
private val MethodsFooterGray = Color(0xFF9AA1AC)
private val MethodsAddCardBlue = Color(0xFFF1F5FD)
private val MethodsInfoCardGray = Color(0xFFF6F8FB)
private val MethodsKakaoYellow = Color(0xFFFEE500)
private val MethodsKakaoBrown = Color(0xFF3C1E1E)
private val MethodsTossBg = Color(0xFFDEE9FF)
private val MethodsTossBlue = Color(0xFF2563EB)
private val MethodsCardBg = Color(0xFFEEF1F6)
private val MethodsRadioBorder = Color(0xFFD3DAE4)

data class PaymentMethodUi(
    val id: String,
    val name: String,
    val detail: String,
    val logoText: String,
    val logoBg: Color,
    val logoFg: Color,
    val usable: Boolean = true, // 만료·해지된 수단은 선택 불가 + 「사용할 수 없어요」
)

fun defaultPaymentMethods(): List<PaymentMethodUi> = listOf(
    PaymentMethodUi("kakao", "카카오페이", "신한 ••••1234", "pay", MethodsKakaoYellow, MethodsKakaoBrown),
    PaymentMethodUi("toss", "토스페이", "국민 ••••8821", "toss", MethodsTossBg, MethodsTossBlue),
    PaymentMethodUi("card", "신용·체크카드", "현대 ••••4402", "card", MethodsCardBg, MethodsSlateGray),
)

@Composable
fun PaymentMethodsScreen(
    methods: List<PaymentMethodUi> = defaultPaymentMethods(),
    onBack: () -> Unit = {},                            // → 29 정산
    onAddMethod: () -> Unit = {},                       // → 31 결제 수단 추가
    onSaveDefault: (method: PaymentMethodUi) -> Unit = {}, // → 29 정산 (선택값 반영)
) {
    // 수단 1개 필수 선택 — 미선택 시 CTA 비활성. 첫 번째 사용 가능 수단이 기본 선택
    var selectedId by remember { mutableStateOf(methods.firstOrNull { it.usable }?.id) }
    val selected = methods.firstOrNull { it.id == selectedId && it.usable }

    Column(modifier = Modifier.fillMaxSize().background(MoyeotaColor.SurfaceSoft)) {
        StatusBarMock()
        PaymentMethodsHeader(title = "결제 수단", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Text(text = "등록된 수단", style = MoyeotaType.BodySm, color = MethodsLabelGray)
            Spacer(Modifier.height(8.dp))

            if (methods.isEmpty()) {
                // 등록 수단 0건이면 목록 대신 추가 유도 화면
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(2.dp, RoundedCornerShape(18.dp))
                        .background(Color.White, RoundedCornerShape(18.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "아직 등록된 결제 수단이 없어요",
                        style = MoyeotaType.BodyMd.copy(fontWeight = FontWeight.Bold),
                        color = MoyeotaColor.InkPrimary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "결제 수단을 추가하면 바로 쓸 수 있어요",
                        style = MoyeotaType.CaptionMd,
                        color = MethodsLabelGray,
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(2.dp, RoundedCornerShape(18.dp))
                        .background(Color.White, RoundedCornerShape(18.dp))
                        .padding(vertical = 4.dp),
                ) {
                    methods.forEachIndexed { index, method ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = MoyeotaColor.Hairline,
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                        }
                        PaymentMethodRow(
                            method = method,
                            selected = method.id == selectedId,
                            onClick = { if (method.usable) selectedId = method.id },
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // + 결제 수단 추가하기 → 31
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MethodsAddCardBlue, RoundedCornerShape(16.dp))
                    .clickable { onAddMethod() }
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "+  결제 수단 추가하기",
                    style = MoyeotaType.BodySm.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    color = MoyeotaColor.Primary600,
                )
            }

            Spacer(Modifier.height(24.dp))

            // 이렇게 결제돼요 카드
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MethodsInfoCardGray, RoundedCornerShape(18.dp))
                    .padding(20.dp),
            ) {
                Text(
                    text = "이렇게 결제돼요",
                    style = MoyeotaType.BodySm.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    color = MoyeotaColor.InkPrimary,
                )
                Spacer(Modifier.height(14.dp))
                PaymentMethodsCheckRow(text = "탑승이 끝나면 자동으로 결제돼요")
                Spacer(Modifier.height(13.dp))
                PaymentMethodsCheckRow(text = "인원이 줄면 차액은 자동 환불돼요")
                Spacer(Modifier.height(13.dp))
                PaymentMethodsCheckRow(text = "결제 정보는 암호화해 보관해요")
            }
            Spacer(Modifier.height(20.dp))
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "기본 수단은 언제든 바꿀 수 있어요",
                style = MoyeotaType.CaptionMd,
                color = MethodsFooterGray,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            PrimaryCtaButton(
                text = "기본 수단으로 저장",
                onClick = { selected?.let(onSaveDefault) },
                enabled = selected != null, // 수단 1개 필수 선택 — 미선택 시 비활성
            )
        }
    }
}

@Composable
private fun PaymentMethodRow(
    method: PaymentMethodUi,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = method.usable) { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(34.dp).background(method.logoBg, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = method.logoText,
                style = MoyeotaType.CaptionSm.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                color = method.logoFg,
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = method.name,
                style = MoyeotaType.BodyMd.copy(fontWeight = FontWeight.Bold),
                color = if (method.usable) MoyeotaColor.InkPrimary else MoyeotaColor.TextAsh,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (method.usable) method.detail else "사용할 수 없어요",
                style = MoyeotaType.CaptionMd,
                color = if (method.usable) MethodsLabelGray else MoyeotaColor.Danger500,
            )
        }
        PaymentMethodsRadio(selected = selected)
    }
}

// 라디오 — 선택: primary 원 + 흰 체크 / 미선택: 테두리 원
@Composable
internal fun PaymentMethodsRadio(selected: Boolean, modifier: Modifier = Modifier) {
    if (selected) {
        Box(
            modifier = modifier.size(22.dp).background(MoyeotaColor.Primary500, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(12.dp)) {
                val stroke = 2.dp.toPx()
                drawLine(
                    color = Color.White,
                    start = Offset(size.width * 0.15f, size.height * 0.55f),
                    end = Offset(size.width * 0.42f, size.height * 0.82f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = Color.White,
                    start = Offset(size.width * 0.42f, size.height * 0.82f),
                    end = Offset(size.width * 0.88f, size.height * 0.22f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    } else {
        Box(
            modifier = modifier
                .size(22.dp)
                .background(Color.White, CircleShape)
                .border(1.5.dp, MethodsRadioBorder, CircleShape),
        )
    }
}

@Composable
private fun PaymentMethodsHeader(title: String, onBack: () -> Unit) {
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
private fun PaymentMethodsCheckRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Canvas(modifier = Modifier.size(15.dp)) {
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
        Text(text = text, style = MoyeotaType.BodySm, color = MethodsSlateGray)
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun PaymentMethodsScreenPreview() {
    PaymentMethodsScreen()
}
