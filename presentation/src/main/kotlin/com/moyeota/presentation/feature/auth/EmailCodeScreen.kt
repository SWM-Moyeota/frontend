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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeota.core.designsystem.component.MoyeotaTopBar
import com.moyeota.core.designsystem.component.PrimaryCtaButton
import com.moyeota.core.designsystem.component.StatusBarMock
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.core.designsystem.theme.MoyeotaType
import kotlinx.coroutines.delay

// 07 · 인증 코드 [S03]
// 진입: 06 인증 메일 보내기 / 뒤로 → 06 / 「인증하기」 → 09 본인 인증
// 「코드 다시 받기」 → 화면 유지, 타이머 4:31 리셋 / 「메일 주소 다시 입력하기」 → 06
private const val CODE_LENGTH = 6
private const val RESEND_COOLDOWN_SECONDS = 4 * 60 + 31 // 재발송 쿨다운 4:31
private const val CODE_EXPIRY_SECONDS = 10 * 60 // 코드 유효시간 10분

private val LabelGray = Color(0xFF8A93A0)
private val FooterGray = Color(0xFF9AA1AC)

@Composable
fun EmailCodeScreen(
    email: String = "moyeota@pusan.ac.kr",
    onBack: () -> Unit = {},
    onVerified: (code: String) -> Unit = {},
    onEditEmail: () -> Unit = {},
    onResendCode: () -> Unit = {},
) {
    var code by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var resendRemaining by remember { mutableIntStateOf(RESEND_COOLDOWN_SECONDS) }
    var expiryRemaining by remember { mutableIntStateOf(CODE_EXPIRY_SECONDS) }
    var timerKey by remember { mutableIntStateOf(0) }

    fun resend() {
        code = ""
        errorText = null
        submitting = false
        resendRemaining = RESEND_COOLDOWN_SECONDS
        expiryRemaining = CODE_EXPIRY_SECONDS
        timerKey++
        onResendCode()
    }

    LaunchedEffect(timerKey) {
        while (resendRemaining > 0 || expiryRemaining > 0) {
            delay(1_000)
            if (resendRemaining > 0) resendRemaining--
            if (expiryRemaining > 0) {
                expiryRemaining--
                if (expiryRemaining == 0) {
                    // 만료 → 「코드가 만료됐어요 · 다시 받기」, 입력값 초기화
                    code = ""
                    errorText = "코드가 만료됐어요 · 다시 받기"
                }
            }
        }
    }

    // 유효값 검증: 6자리 입력 완료 시 자동 제출
    LaunchedEffect(code) {
        if (code.length == CODE_LENGTH && !submitting) {
            submitting = true
            onVerified(code)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MoyeotaColor.SurfaceSoft)) {
        StatusBarMock()
        MoyeotaTopBar(
            title = "",
            onBack = onBack,
            actions = {
                Text(text = "1 / 5", style = MoyeotaType.BodySm, color = LabelGray)
            },
        )
        EmailCodeProgressBar(progress = 1f / 5f)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "메일로 보낸 6자리를\n입력해 주세요",
                style = MoyeotaType.DisplayMd,
                color = MoyeotaColor.InkPrimary,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "$email 로 보냈어요",
                style = MoyeotaType.BodySm.copy(fontSize = 14.sp),
                color = MoyeotaColor.TextMute,
            )
            Spacer(Modifier.height(28.dp))

            EmailCodeInput(
                code = code,
                isError = errorText != null,
                enabled = !submitting && expiryRemaining > 0,
                onCodeChange = { new ->
                    // 유효값 검증: 숫자 6자리만 입력, 문자·공백 무시
                    code = new.filter { it.isDigit() }.take(CODE_LENGTH)
                    errorText = null
                },
            )

            if (errorText != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = errorText.orEmpty(),
                    style = MoyeotaType.BodySm.copy(fontWeight = FontWeight.Bold),
                    color = MoyeotaColor.Danger500,
                    modifier = Modifier.clickable { resend() },
                )
            }

            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "남은 시간 ${formatSeconds(resendRemaining)}",
                    style = MoyeotaType.BodySm.copy(fontWeight = FontWeight.Bold),
                    color = MoyeotaColor.Waiting600,
                )
                Text(
                    text = "코드 다시 받기",
                    style = MoyeotaType.BodySm.copy(fontWeight = FontWeight.Bold),
                    color = MoyeotaColor.Primary500,
                    modifier = Modifier.clickable { resend() },
                )
            }

            Spacer(Modifier.height(22.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(18.dp))
                    .background(Color.White, RoundedCornerShape(18.dp)),
            ) {
                // 스팸함 안내 — 동작 없음
                EmailCodeHelpRow(
                    title = "스팸함을 확인해 주세요",
                    subtitle = "메일이 스팸으로 분류될 수 있어요",
                    onClick = null,
                )
                HorizontalDivider(
                    color = MoyeotaColor.Hairline,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                // 「메일 주소 다시 입력하기」 → 06
                EmailCodeHelpRow(
                    title = "메일 주소 다시 입력하기",
                    subtitle = "$email 이 맞나요?",
                    onClick = onEditEmail,
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            PrimaryCtaButton(
                text = "인증하기",
                onClick = {
                    submitting = true
                    onVerified(code)
                },
                enabled = code.length == CODE_LENGTH,
                loading = submitting,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "코드는 10분 뒤에 만료돼요",
                style = MoyeotaType.CaptionMd,
                color = FooterGray,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// 와이어프레임의 박스형 코드 입력 — BasicTextField + 박스 렌더링
@Composable
private fun EmailCodeInput(
    code: String,
    isError: Boolean,
    enabled: Boolean,
    onCodeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = code,
        onValueChange = onCodeChange,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        decorationBox = { innerTextField ->
            Box {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    repeat(CODE_LENGTH) { index ->
                        val filled = index < code.length
                        val active = index == code.length && enabled
                        val borderColor = when {
                            isError -> MoyeotaColor.Danger500
                            active -> MoyeotaColor.Primary500
                            else -> null
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp)
                                .shadow(2.dp, RoundedCornerShape(16.dp))
                                .background(Color.White, RoundedCornerShape(16.dp))
                                .then(
                                    if (borderColor != null) {
                                        Modifier.border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
                                    } else {
                                        Modifier
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (filled) {
                                Text(
                                    text = code[index].toString(),
                                    style = MoyeotaType.DisplayMd,
                                    color = MoyeotaColor.InkPrimary,
                                )
                            } else if (active) {
                                Box(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .height(28.dp)
                                        .background(MoyeotaColor.Primary500, RoundedCornerShape(1.dp)),
                                )
                            }
                        }
                    }
                }
                // 실제 입력 필드는 투명하게 겹쳐 터치·키 입력만 받는다
                Box(modifier = Modifier.matchParentSize().alpha(0f)) {
                    innerTextField()
                }
            }
        },
    )
}

@Composable
private fun EmailCodeHelpRow(
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EmailCodeMailIcon()
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MoyeotaType.BodySm.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                color = MoyeotaColor.InkPrimary,
            )
            Spacer(Modifier.height(3.dp))
            Text(text = subtitle, style = MoyeotaType.CaptionMd, color = LabelGray)
        }
        EmailCodeChevronIcon()
    }
}

@Composable
private fun EmailCodeProgressBar(progress: Float, modifier: Modifier = Modifier) {
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
private fun EmailCodeMailIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(17.dp)) {
        val stroke = 1.5.dp.toPx()
        val color = Color(0xFF8A93A0)
        val top = size.height * 0.18f
        val bottom = size.height * 0.82f
        drawRoundRect(
            color = color,
            topLeft = Offset(0f, top),
            size = androidx.compose.ui.geometry.Size(size.width, bottom - top),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
            style = Stroke(width = stroke),
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.08f, top + stroke),
            end = Offset(size.width * 0.5f, size.height * 0.55f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.5f, size.height * 0.55f),
            end = Offset(size.width * 0.92f, top + stroke),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun EmailCodeChevronIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(16.dp)) {
        val stroke = 2.dp.toPx()
        val color = Color(0xFFB6BEC9)
        drawLine(
            color = color,
            start = Offset(size.width * 0.35f, size.height * 0.2f),
            end = Offset(size.width * 0.7f, size.height * 0.5f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.7f, size.height * 0.5f),
            end = Offset(size.width * 0.35f, size.height * 0.8f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

private fun formatSeconds(total: Int): String {
    val minutes = total / 60
    val seconds = total % 60
    return "%d:%02d".format(minutes, seconds)
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun EmailCodeScreenPreview() {
    EmailCodeScreen()
}
