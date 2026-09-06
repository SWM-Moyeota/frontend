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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeota.core.designsystem.component.MoyeotaTextField
import com.moyeota.core.designsystem.component.MoyeotaTopBar
import com.moyeota.core.designsystem.component.NavigationBarSpacer
import com.moyeota.core.designsystem.component.PrimaryCtaButton
import com.moyeota.core.designsystem.component.StatusBarSpacer
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.core.designsystem.theme.MoyeotaType
import com.moyeota.domain.model.Gender
import com.moyeota.presentation.core.BirthDateTransformation
import com.moyeota.presentation.core.PhoneNumberTransformation
import java.time.LocalDate

// 09 · 본인 인증 [S04] — ⚠️ **현재 어떤 그래프에도 등록되어 있지 않다 (미연결)**
//
// 휴대폰(통신사) 본인 인증은 MVP 범위 밖으로 빠졌다. 이 화면은 인증 없이 값만 묻는 폼이
// 되어 있었으므로 가입 경로에서 들어냈고, 받던 값(실명·휴대폰·생년월일·성별)은 그대로
// 10 프로필 만들기의 「기본 정보」 절로 옮겼다. 검증 규칙은 SignupFieldSupport.kt 로 공용화했다.
//
// 파일을 남긴 이유: 실제 본인 인증(SMS·PASS)을 붙일 때 통신사 선택 UI 와 폼 배치를 재사용한다.
// 되살릴 때는 (a) MainNavGraph 에 다시 등록, (b) 진행 표시 「1 / 4」 재계산,
// (c) 10 과 중복되는 입력 정리, (d) 약관 동의(현재 12 매너 서약에 통합됨) 중복 여부 확인이 필요하다.
private val Carriers = listOf("SKT", "KT", "LG U+", "알뜰폰")

private val LabelGray = Color(0xFF8A93A0)
private val SlateGray = Color(0xFF4B5563)
private val InfoCardBlue = Color(0xFFF1F5FD)

/**
 * @param onRequestCode 「다음」 → 10 프로필 만들기. 서버 가입에 그대로 들어갈 값들이다.
 *   (이름은 SMS 인증을 보내던 시절의 잔재다 — 실제 발송은 없다)
 */
@Composable
fun IdentityVerifyScreen(
    onBack: () -> Unit = {},
    onRequestCode: (name: String, phone: String, birthDate: LocalDate, gender: Gender) -> Unit =
        { _, _, _, _ -> },
) {
    var carrier by remember { mutableStateOf<String?>(null) }
    var phoneDigits by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var birthDigits by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf<Gender?>(null) }
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
    val nameValid = PersonNameRegex.matches(name)
    val nameError = if (name.isNotEmpty() && !nameValid) "이름은 한글 또는 영문 2~20자로 입력해 주세요" else null

    // 유효값 검증: 생년월일 — 8자리를 채웠을 때만 실제 날짜인지까지 본다(20250230 같은 값 차단)
    val birthDate = parseBirthDate(birthDigits)
    val birthError = when {
        birthDigits.length < 8 -> null
        birthDate == null -> "실제로 있는 날짜를 YYYYMMDD 로 입력해 주세요"
        else -> null
    }

    // 모든 필수 값 + 약관 동의 → 미충족 시 CTA 비활성
    val ctaEnabled = carrier != null && phoneValid && nameValid &&
        birthDate != null && gender != null && agreed

    Column(modifier = Modifier.fillMaxSize().background(MoyeotaColor.SurfaceSoft)) {
        StatusBarSpacer()
        MoyeotaTopBar(
            title = "",
            onBack = onBack,
            actions = {
                // 가입은 09 → 10 → 11 → 12 네 단계다 (05~08 부가 인증은 가입 경로에서 빠졌다)
                Text(text = "1 / 4", style = MoyeotaType.BodySm, color = LabelGray)
            },
        )
        IdentityVerifyProgressBar(progress = 1f / 4f)

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
                // 실명·성별은 가입 시 서버에 저장된다 — "저장하지 않아요"로 안내하면 사실과 다르다
                text = "실명과 성별은 매칭 안전을 위해서만 사용해요",
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
                    SignupChoiceChip(
                        text = candidate,
                        selected = carrier == candidate,
                        onClick = { carrier = candidate },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            // value 는 원시 숫자만. 하이픈은 VisualTransformation 이 그린다 — 표시 문자열을 value 로
            // 넘기면 길이가 튀는 순간 커서가 옛 인덱스에 남아 입력 순서가 뒤집힌다(D-7).
            MoyeotaTextField(
                value = phoneDigits,
                onValueChange = { new -> phoneDigits = new.filter { it.isDigit() }.take(11) },
                label = "휴대폰 번호",
                placeholder = "010-1234-5678",
                errorText = phoneError,
                enabled = !submitting,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                visualTransformation = PhoneNumberTransformation,
            )

            Spacer(Modifier.height(20.dp))
            MoyeotaTextField(
                value = name,
                onValueChange = { name = it.take(20) },
                label = "이름",
                placeholder = "실명을 입력해 주세요",
                errorText = nameError,
                enabled = !submitting,
            )

            Spacer(Modifier.height(20.dp))
            MoyeotaTextField(
                value = birthDigits,
                onValueChange = { new -> birthDigits = new.filter { it.isDigit() }.take(8) },
                label = "생년월일",
                placeholder = "2000-01-01",
                errorText = birthError,
                enabled = !submitting,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                visualTransformation = BirthDateTransformation,
            )

            Spacer(Modifier.height(20.dp))
            Text(text = "성별", style = MoyeotaType.BodySm, color = LabelGray)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SignupChoiceChip(
                    text = "남성",
                    selected = gender == Gender.MALE,
                    onClick = { gender = Gender.MALE },
                    modifier = Modifier.weight(1f),
                )
                SignupChoiceChip(
                    text = "여성",
                    selected = gender == Gender.FEMALE,
                    onClick = { gender = Gender.FEMALE },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(24.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(InfoCardBlue, RoundedCornerShape(18.dp))
                    .padding(20.dp),
            ) {
                Text(
                    text = "성별은 동성 매칭에만 써요",
                    style = MoyeotaType.BodySm.copy(fontWeight = FontWeight.Bold),
                    color = MoyeotaColor.InkPrimary,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "프로필에는 표시되지 않아요",
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
            // 「인증 문자 받기」가 아니라 「다음」이다 — 백엔드에 본인인증(SMS) API 가 없어
            // 실제로 문자를 보내지 않고 곧장 10 으로 넘어간다. 보내지 않는 것을 보낸다고 쓰지 않는다.
            PrimaryCtaButton(
                text = "다음",
                onClick = {
                    val birth = birthDate ?: return@PrimaryCtaButton
                    val genderValue = gender ?: return@PrimaryCtaButton
                    submitting = true
                    // 서버에는 지금까지와 같이 하이픈이 든 형식으로 보낸다 — 표시용 포매터를 그대로 재사용
                    onRequestCode(
                        name.trim(),
                        PhoneNumberTransformation.format(phoneDigits),
                        birth,
                        genderValue,
                    )
                },
                enabled = ctaEnabled,
                loading = submitting,
            )
        }
        NavigationBarSpacer()
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
