package com.moyeota.presentation.feature.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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

// 08 · 직장·일반 신원 인증 [S26]
// 진입: 05에서 「직장인」 또는 「일반」 선택 / 뒤로 → 05 / 「인증 요청 보내기」 → 09 본인 인증
// 유효값 검증: 회사 이메일 — RFC 형식 + 무료 메일 도메인(gmail·naver 등) 차단
private val CompanyEmailRegex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
private val FreeMailDomains = setOf(
    "gmail.com", "naver.com", "daum.net", "hanmail.net", "kakao.com",
    "nate.com", "outlook.com", "hotmail.com", "yahoo.com", "icloud.com",
)

private val LabelGray = Color(0xFF8A93A0)
private val SlateGray = Color(0xFF4B5563)
private val FooterGray = Color(0xFF9AA1AC)
private val InfoCardBlue = Color(0xFFF1F5FD)
private val DocCardGray = Color(0xFFF6F8FB)

@Composable
fun WorkVerifyScreen(
    isWorker: Boolean = true,
    onBack: () -> Unit = {},
    onSubmit: (companyEmail: String?) -> Unit = {},
) {
    var email by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }

    val formatValid = CompanyEmailRegex.matches(email)
    val domain = email.substringAfterLast('@', "").lowercase()
    val isFreeMail = formatValid && domain in FreeMailDomains
    val emailValid = formatValid && !isFreeMail
    val fieldError = when {
        email.isEmpty() -> null
        !formatValid -> "이메일 형식이 올바르지 않아요"
        isFreeMail -> "무료 메일(gmail·naver 등)은 사용할 수 없어요"
        else -> null
    }
    // 회사 메일과 재직 서류 중 최소 1개 필수 — 서류 업로드는 미연결이므로 직장인은 회사 메일 필수.
    // 일반 유형은 둘 다 생략 가능 (입력했다면 유효해야 함)
    val ctaEnabled = if (isWorker) emailValid else email.isEmpty() || emailValid

    Column(modifier = Modifier.fillMaxSize().background(MoyeotaColor.SurfaceSoft)) {
        StatusBarMock()
        MoyeotaTopBar(
            title = "",
            onBack = onBack,
            actions = {
                Text(text = "2 / 5", style = MoyeotaType.BodySm, color = LabelGray)
            },
        )
        WorkVerifyProgressBar(progress = 2f / 5f)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "신원을 확인할게요",
                style = MoyeotaType.DisplayMd,
                color = MoyeotaColor.InkPrimary,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "직장인은 재직 정보로, 일반 이용자는 본인 인증으로 시작해요",
                style = MoyeotaType.BodySm.copy(fontSize = 14.sp),
                color = MoyeotaColor.TextMute,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = "직장인 재직 인증 · 05 계정 유형 선택에서 선택함",
                style = MoyeotaType.BodySm.copy(fontWeight = FontWeight.Bold),
                color = MoyeotaColor.Primary500,
            )
            Spacer(Modifier.height(28.dp))

            MoyeotaTextField(
                value = email,
                onValueChange = { new -> email = new.filter { !it.isWhitespace() } },
                label = "회사 이메일",
                placeholder = "name@company.com",
                errorText = fieldError,
                enabled = !submitting,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )

            Spacer(Modifier.height(24.dp))
            Text(text = "재직 서류 (선택)", style = MoyeotaType.BodySm, color = LabelGray)
            Spacer(Modifier.height(8.dp))
            // 미연결: 재직 서류 탭 → 파일/카메라 선택 시트 — 클릭해도 동작 없음
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DocCardGray, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(MoyeotaColor.Primary50, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    WorkVerifyCameraIcon()
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = "재직증명서 · 사원증 촬영",
                        style = MoyeotaType.BodySm.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                        color = MoyeotaColor.InkPrimary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "회사 메일이 없으면 서류로 인증할 수 있어요",
                        style = MoyeotaType.CaptionMd,
                        color = LabelGray,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(InfoCardBlue, RoundedCornerShape(18.dp))
                    .padding(20.dp),
            ) {
                Text(
                    text = "이렇게 보호해요",
                    style = MoyeotaType.BodySm.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    color = MoyeotaColor.InkPrimary,
                )
                Spacer(Modifier.height(13.dp))
                WorkVerifyProtectRow(text = "회사명과 서류는 인증 후 즉시 삭제해요")
                Spacer(Modifier.height(5.dp))
                WorkVerifyProtectRow(text = "직업은 「직장인 인증」 배지로만 표시돼요")
                Spacer(Modifier.height(5.dp))
                WorkVerifyProtectRow(text = "동료에게 가입 사실이 알려지지 않아요")
            }
            Spacer(Modifier.height(24.dp))
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            PrimaryCtaButton(
                text = "인증 요청 보내기",
                onClick = {
                    submitting = true
                    onSubmit(email.ifEmpty { null })
                },
                enabled = ctaEnabled,
                loading = submitting,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "재직 확인은 보통 1일 안에 끝나요",
                style = MoyeotaType.CaptionMd,
                color = FooterGray,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun WorkVerifyProgressBar(progress: Float, modifier: Modifier = Modifier) {
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

@Composable
private fun WorkVerifyProtectRow(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        WorkVerifyCheckIcon()
        Text(text = text, style = MoyeotaType.BodySm, color = SlateGray)
    }
}

@Composable
private fun WorkVerifyCheckIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(14.dp)) {
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

@Composable
private fun WorkVerifyCameraIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(20.dp)) {
        val stroke = 1.5.dp.toPx()
        val color = MoyeotaColor.Primary500
        val bodyTop = size.height * 0.25f
        drawRoundRect(
            color = color,
            topLeft = Offset(0f, bodyTop),
            size = Size(size.width, size.height - bodyTop - size.height * 0.08f),
            cornerRadius = CornerRadius(3.dp.toPx()),
            style = Stroke(width = stroke),
        )
        // 뷰파인더 돌출부
        drawLine(
            color = color,
            start = Offset(size.width * 0.32f, bodyTop),
            end = Offset(size.width * 0.42f, size.height * 0.1f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.42f, size.height * 0.1f),
            end = Offset(size.width * 0.58f, size.height * 0.1f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.58f, size.height * 0.1f),
            end = Offset(size.width * 0.68f, bodyTop),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        // 렌즈
        drawCircle(
            color = color,
            radius = size.width * 0.17f,
            center = Offset(size.width * 0.5f, size.height * 0.58f),
            style = Stroke(width = stroke),
        )
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun WorkVerifyScreenPreview() {
    WorkVerifyScreen()
}
