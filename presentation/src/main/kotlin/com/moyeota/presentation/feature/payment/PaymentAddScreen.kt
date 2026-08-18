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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeota.core.designsystem.component.BackArrowIcon
import com.moyeota.core.designsystem.component.MoyeotaTextField
import com.moyeota.core.designsystem.component.PrimaryCtaButton
import com.moyeota.core.designsystem.component.StatusBarMock
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.core.designsystem.theme.MoyeotaType
import java.util.Calendar

// 31 · 결제 수단 추가 [신규]
// 진입: 30 「+ 결제 수단 추가하기」
// 뒤로 → 30 / 카카오페이·토스페이 선택 → 외부 인증 후 복귀 (미연결) / 「신용 · 체크카드」 선택 → 카드 정보 입력 영역 노출
// 「추가하기」 → 30 결제 수단 선택
// 에러: 카드사 거절 → 필드 하단 사유 표기 (미연결) / 외부 인증 취소 → 30으로 복귀, 토스트 없음

private val AddLabelGray = Color(0xFF8A93A0)
private val AddSlateGray = Color(0xFF4B5563)
private val AddFooterGray = Color(0xFF9AA1AC)
private val AddKakaoYellow = Color(0xFFFEE500)
private val AddKakaoBrown = Color(0xFF3C1E1E)
private val AddTossBg = Color(0xFFDEE9FF)
private val AddTossBlue = Color(0xFF2563EB)
private val AddCardBg = Color(0xFFEEF1F6)

private enum class AddMethodType(val title: String, val subtitle: String) {
    KAKAO("카카오페이", "카카오 계정으로 연결해요"),
    TOSS("토스페이", "토스 계정으로 연결해요"),
    CARD("신용 · 체크카드", "카드번호를 직접 입력해요"),
}

// ── 유효값 검증 ──────────────────────────────────────────────────────────────

// 카드번호: 숫자 15~16자리 + Luhn 체크 (AMEX(34/37 시작)는 15자리)
internal fun luhnValid(digits: String): Boolean {
    if (digits.isEmpty()) return false
    var sum = 0
    digits.reversed().forEachIndexed { i, c ->
        var d = c - '0'
        if (i % 2 == 1) {
            d *= 2
            if (d > 9) d -= 9
        }
        sum += d
    }
    return sum % 10 == 0
}

internal fun isAmex(digits: String): Boolean =
    digits.startsWith("34") || digits.startsWith("37")

internal fun cardNumberValid(digits: String): Boolean =
    digits.length in 15..16 && luhnValid(digits)

// 유효기간: MM 01~12, YY 현재 연월 이후
internal fun expiryValid(digits: String, nowYear: Int, nowMonth: Int): Boolean {
    if (digits.length != 4) return false
    val mm = digits.take(2).toIntOrNull() ?: return false
    val yy = digits.drop(2).toIntOrNull() ?: return false
    if (mm !in 1..12) return false
    val curYY = nowYear % 100
    return yy > curYY || (yy == curYY && mm >= nowMonth)
}

// 4자리씩 자동 하이픈
internal fun formatCardNumber(digits: String): String =
    digits.chunked(4).joinToString("-")

@Composable
fun PaymentAddScreen(
    onBack: () -> Unit = {},   // → 30 결제 수단 선택
    onAdded: () -> Unit = {},  // 「추가하기」 → 30 결제 수단 선택 (간편결제는 연결만으로 등록 완료)
) {
    var selectedType by remember { mutableStateOf(AddMethodType.KAKAO) }
    var cardDigits by remember { mutableStateOf("") }   // 카드번호 (숫자만 보관)
    var expiryDigits by remember { mutableStateOf("") } // MMYY (숫자만 보관)
    var cvc by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }

    val now = remember { Calendar.getInstance() }
    val nowYear = now.get(Calendar.YEAR)
    val nowMonth = now.get(Calendar.MONTH) + 1

    val amex = isAmex(cardDigits)
    val cvcLength = if (amex) 4 else 3 // CVC: 3자리 (AMEX 4자리)
    val maxCardDigits = if (amex) 15 else 16

    val cardOk = cardNumberValid(cardDigits)
    val expiryOk = expiryValid(expiryDigits, nowYear, nowMonth)
    val cvcOk = cvc.length == cvcLength

    // 입력을 마친 필드만 오류 표기 (필드 단위 오류는 MoyeotaTextField errorText)
    val cardError = if (cardDigits.length >= 15 && !cardOk) "카드번호를 다시 확인해 주세요" else null
    val expiryError = if (expiryDigits.length == 4 && !expiryOk) "유효기간을 다시 확인해 주세요" else null
    val cvcError = if (cvc.length >= cvcLength && !cvcOk) "CVC ${cvcLength}자리를 입력해 주세요" else null

    // 간편결제는 연결만으로 등록 완료 / 카드는 세 필드 모두 통과해야 「추가하기」 활성
    val ctaEnabled = when (selectedType) {
        AddMethodType.KAKAO, AddMethodType.TOSS -> true
        AddMethodType.CARD -> cardOk && expiryOk && cvcOk
    }

    Column(modifier = Modifier.fillMaxSize().background(MoyeotaColor.SurfaceSoft)) {
        StatusBarMock()
        PaymentAddHeader(title = "결제 수단 추가", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Text(text = "추가할 수단", style = MoyeotaType.BodySm, color = AddLabelGray)
            Spacer(Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(18.dp))
                    .background(Color.White, RoundedCornerShape(18.dp))
                    .padding(vertical = 4.dp),
            ) {
                AddMethodType.entries.forEachIndexed { index, type ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = MoyeotaColor.Hairline,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }
                    PaymentAddOptionRow(
                        type = type,
                        selected = type == selectedType,
                        onClick = { selectedType = type },
                    )
                }
            }

            // 「신용 · 체크카드」 선택 시에만 카드 정보 입력 영역 노출 — 간편결제는 비노출
            if (selectedType == AddMethodType.CARD) {
                Spacer(Modifier.height(24.dp))
                Text(text = "카드 정보", style = MoyeotaType.BodySm, color = AddLabelGray)
                Spacer(Modifier.height(8.dp))

                MoyeotaTextField(
                    value = formatCardNumber(cardDigits),
                    onValueChange = { new ->
                        cardDigits = new.filter { it.isDigit() }.take(maxCardDigits)
                    },
                    placeholder = "카드번호 0000-0000-0000-0000",
                    errorText = cardError,
                    enabled = !submitting,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        MoyeotaTextField(
                            value = formatExpiry(expiryDigits),
                            onValueChange = { new ->
                                expiryDigits = new.filter { it.isDigit() }.take(4)
                            },
                            placeholder = "유효기간 MM/YY",
                            errorText = expiryError,
                            enabled = !submitting,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        MoyeotaTextField(
                            value = cvc,
                            onValueChange = { new ->
                                cvc = new.filter { it.isDigit() }.take(cvcLength)
                            },
                            placeholder = "CVC 000",
                            errorText = cvcError,
                            enabled = !submitting,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                // 저장 표기: 뒤 4자리만 (「신한 ••••1234」)
                Text(
                    text = "앱에는 카드 뒤 4자리만 표시돼요",
                    style = MoyeotaType.CaptionMd,
                    color = AddLabelGray,
                )
            }

            Spacer(Modifier.height(24.dp))

            // 이렇게 보호해요 카드
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(18.dp))
                    .background(Color.White, RoundedCornerShape(18.dp))
                    .padding(20.dp),
            ) {
                Text(
                    text = "이렇게 보호해요",
                    style = MoyeotaType.BodySm.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    color = MoyeotaColor.InkPrimary,
                )
                Spacer(Modifier.height(14.dp))
                PaymentAddCheckRow(text = "카드 정보는 암호화해 결제사에만 전달돼요")
                Spacer(Modifier.height(13.dp))
                PaymentAddCheckRow(text = "앱에는 카드 뒤 4자리만 표시돼요")
                Spacer(Modifier.height(13.dp))
                PaymentAddCheckRow(text = "등록 후 언제든 삭제할 수 있어요")
            }
            Spacer(Modifier.height(20.dp))
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "간편결제는 연결만 하면 바로 쓸 수 있어요",
                style = MoyeotaType.CaptionMd,
                color = AddFooterGray,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            PrimaryCtaButton(
                text = "추가하기",
                onClick = {
                    submitting = true
                    onAdded()
                },
                enabled = ctaEnabled,
                loading = submitting,
            )
        }
    }
}

// 유효기간 MM/YY — 2자리 뒤 자동 「/」
private fun formatExpiry(digits: String): String =
    if (digits.length <= 2) digits else digits.take(2) + "/" + digits.drop(2)

@Composable
private fun PaymentAddOptionRow(
    type: AddMethodType,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val (logoText, logoBg, logoFg) = when (type) {
        AddMethodType.KAKAO -> Triple("pay", AddKakaoYellow, AddKakaoBrown)
        AddMethodType.TOSS -> Triple("toss", AddTossBg, AddTossBlue)
        AddMethodType.CARD -> Triple("card", AddCardBg, AddSlateGray)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(34.dp).background(logoBg, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = logoText,
                style = MoyeotaType.CaptionSm.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                color = logoFg,
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = type.title,
                style = MoyeotaType.BodyMd.copy(fontWeight = FontWeight.Bold),
                color = MoyeotaColor.InkPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(text = type.subtitle, style = MoyeotaType.CaptionMd, color = AddLabelGray)
        }
        PaymentMethodsRadio(selected = selected)
    }
}

@Composable
private fun PaymentAddHeader(title: String, onBack: () -> Unit) {
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
private fun PaymentAddCheckRow(text: String) {
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
        Text(text = text, style = MoyeotaType.BodySm, color = AddSlateGray)
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun PaymentAddScreenPreview() {
    PaymentAddScreen()
}
