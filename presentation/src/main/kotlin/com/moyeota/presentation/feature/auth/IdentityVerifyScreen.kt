package com.moyeota.presentation.feature.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import com.moyeota.core.designsystem.component.MoyeotaTextField
import com.moyeota.core.designsystem.component.MoyeotaTopBar
import com.moyeota.core.designsystem.component.PrimaryCtaButton
import com.moyeota.core.designsystem.component.StatusBarMock
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.core.designsystem.theme.MoyeotaType

// 09 · 본인 인증 [S04]
// 진입: 07 인증 완료 · 08 인증 요청 완료 / 뒤로 → 07 / 「인증 문자 받기」 → 10 프로필 만들기
private val Carriers = listOf("SKT", "KT", "LG U+", "알뜰폰")

// 유효값 검증: 이름 — 한글 2~10자
private val KoreanNameRegex = Regex("^[가-힣]{2,10}$")

private val LabelGray = Color(0xFF8A93A0)
private val SlateGray = Color(0xFF4B5563)
private val FooterGray = Color(0xFF9AA1AC)
private val InfoCardBlue = Color(0xFFF1F5FD)

@Composable
fun IdentityVerifyScreen(
    onBack: () -> Unit = {},
    onRequestCode: (carrier: String, phone: String, name: String) -> Unit = { _, _, _ -> },
) {
    var carrier by remember { mutableStateOf<String?>(null) }
    var phoneDigits by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var agreed by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }

    // 유효값 검증: 휴대폰 — 010 + 숫자 8자리, 하이픈 자동 삽입, 그 외 형식 거부
    val phoneValid = phoneDigits.length == 11 && phoneDigits.startsWith("010")
    val phoneError = when {
        phoneDigits.isEmpty() -> null
        phoneDigits.length >= 3 && !phoneDigits.startsWith("010") ->
            "010으로 시작하는 휴대폰 번호만 인증할 수 있어요"
        phoneDigits.length in 3..10 || phoneValid -> null
        else -> "휴대폰 번호는 010 + 숫자 8자리로 입력해 주세요"
    }
    val nameValid = KoreanNameRegex.matches(name)
    val nameError = if (name.isNotEmpty() && !nameValid) "이름은 한글 2~10자로 입력해 주세요" else null

    // 통신사 1개 필수 + 휴대폰·이름 유효 + 약관 동의 체크 필수 → 미충족 시 CTA 비활성
    val ctaEnabled = carrier != null && phoneValid && nameValid && agreed

    Column(modifier = Modifier.fillMaxSize().background(MoyeotaColor.SurfaceSoft)) {
        StatusBarMock()
        MoyeotaTopBar(
            title = "",
            onBack = onBack,
            actions = {
                Text(text = "2 / 5", style = MoyeotaType.BodySm, color = LabelGray)
            },
        )
        IdentityVerifyProgressBar(progress = 2f / 5f)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "휴대폰으로 본인 확인할게요",
                style = MoyeotaType.DisplayMd,
                color = MoyeotaColor.InkPrimary,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "실명과 성별은 매칭 안전에만 쓰고 저장하지 않아요",
                style = MoyeotaType.BodySm.copy(fontSize = 14.sp),
                color = MoyeotaColor.TextMute,
            )
            Spacer(Modifier.height(26.dp))

            Text(text = "통신사", style = MoyeotaType.BodySm, color = LabelGray)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Carriers.forEach { candidate ->
                    IdentityCarrierOption(
                        text = candidate,
                        selected = carrier == candidate,
                        onClick = { carrier = candidate },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            MoyeotaTextField(
                value = formatPhone(phoneDigits),
                onValueChange = { new -> phoneDigits = new.filter { it.isDigit() }.take(11) },
                label = "휴대폰 번호",
                placeholder = "010-1234-5678",
                errorText = phoneError,
                enabled = !submitting,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            )

            Spacer(Modifier.height(20.dp))
            MoyeotaTextField(
                value = name,
                onValueChange = { name = it },
                label = "이름",
                placeholder = "실명을 입력해 주세요",
                errorText = nameError,
                enabled = !submitting,
            )

            Spacer(Modifier.height(24.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(InfoCardBlue, RoundedCornerShape(18.dp))
                    .padding(20.dp),
            ) {
                Text(
                    text = "성별은 통신사 인증으로만 확인해요",
                    style = MoyeotaType.BodySm.copy(fontWeight = FontWeight.Bold),
                    color = MoyeotaColor.InkPrimary,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "동성 매칭에만 쓰이고, 프로필에는 표시되지 않아요",
                    style = MoyeotaType.CaptionMd,
                    color = SlateGray,
                )
            }

            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IdentityAgreeCheckbox(checked = agreed, onToggle = { agreed = !agreed })
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "이용약관 · 개인정보 수집에 동의해요",
                    style = MoyeotaType.BodySm,
                    color = SlateGray,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { agreed = !agreed },
                )
                // 미연결: 「보기」(약관) — 클릭해도 동작 없음 (약관 상세 필요)
                Text(
                    text = "보기",
                    style = MoyeotaType.BodySm.copy(fontWeight = FontWeight.Bold),
                    color = LabelGray,
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            PrimaryCtaButton(
                text = "인증 문자 받기",
                onClick = {
                    submitting = true
                    onRequestCode(carrier.orEmpty(), formatPhone(phoneDigits), name)
                },
                enabled = ctaEnabled,
                loading = submitting,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "인증 문자는 1회만 발송되고, 3분 안에 도착해요",
                style = MoyeotaType.CaptionMd,
                color = FooterGray,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// 하이픈 자동 삽입 (010-1234-5678)
private fun formatPhone(digits: String): String = when {
    digits.length <= 3 -> digits
    digits.length <= 7 -> "${digits.take(3)}-${digits.drop(3)}"
    else -> "${digits.take(3)}-${digits.substring(3, 7)}-${digits.drop(7)}"
}

@Composable
private fun IdentityCarrierOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    val base = if (selected) {
        modifier
            .height(44.dp)
            .background(MoyeotaColor.Primary50, shape)
            .border(1.5.dp, MoyeotaColor.Primary500, shape)
    } else {
        modifier
            .height(44.dp)
            .shadow(2.dp, shape)
            .background(Color.White, shape)
    }
    Box(
        modifier = base.clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MoyeotaType.BodySm.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            ),
            color = if (selected) MoyeotaColor.Primary500 else SlateGray,
        )
    }
}

@Composable
private fun IdentityAgreeCheckbox(checked: Boolean, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(7.dp)
    Box(
        modifier = Modifier
            .size(22.dp)
            .then(
                if (checked) {
                    Modifier.background(MoyeotaColor.Primary500, shape)
                } else {
                    Modifier
                        .background(Color.White, shape)
                        .border(1.5.dp, MoyeotaColor.Hairline, shape)
                },
            )
            .clickable { onToggle() },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Canvas(modifier = Modifier.size(14.dp)) {
                val stroke = 2.dp.toPx()
                drawLine(
                    color = Color.White,
                    start = Offset(size.width * 0.15f, size.height * 0.55f),
                    end = Offset(size.width * 0.4f, size.height * 0.8f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = Color.White,
                    start = Offset(size.width * 0.4f, size.height * 0.8f),
                    end = Offset(size.width * 0.85f, size.height * 0.25f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun IdentityVerifyProgressBar(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(4.dp)
            .background(Color(0xFFE6EAF0), RoundedCornerShape(2.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .fillMaxHeight()
                .background(MoyeotaColor.Primary500, RoundedCornerShape(2.dp)),
        )
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun IdentityVerifyScreenPreview() {
    IdentityVerifyScreen()
}
