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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.moyeota.core.designsystem.component.ChevronIcon
import com.moyeota.core.designsystem.component.IconStrokeWidth
import com.moyeota.core.designsystem.component.MoyeotaIconSize
import com.moyeota.core.designsystem.component.MoyeotaTopBar
import com.moyeota.core.designsystem.component.NavigationBarSpacer
import com.moyeota.core.designsystem.component.PrimaryCtaButton
import com.moyeota.core.designsystem.component.StatusBarSpacer
import com.moyeota.core.designsystem.component.TextLinkButton
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.core.designsystem.theme.MoyeotaShape
import com.moyeota.core.designsystem.theme.MoyeotaType
import com.moyeota.core.designsystem.theme.tabularNums
import kotlinx.coroutines.delay

// 07 · 인증 코드 [S03]
//
// ⚠️ 현재 어떤 그래프에도 등록되어 있지 않다 (06 과 같은 이유 — 인증은 마이페이지로 이동 예정).
//
// (원래 배선) 진입: 06 인증 메일 보내기 / 뒤로 → 06 / 「인증하기」 → 09 본인 인증
// 「코드 다시 받기」 → 화면 유지, 타이머 4:31 리셋 / 「메일 주소 다시 입력하기」 → 06
private const val CODE_LENGTH = 6
private const val RESEND_COOLDOWN_SECONDS = 4 * 60 + 31 // 재발송 쿨다운 4:31
private const val CODE_EXPIRY_SECONDS = 10 * 60 // 코드 유효시간 10분

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

    Column(modifier = Modifier.fillMaxSize().imePadding().background(MoyeotaColor.SurfaceCanvas)) {
        StatusBarSpacer()
        MoyeotaTopBar(
            title = "",
            onBack = onBack,
            actions = {
                Text(text = "1 / 5", style = MoyeotaType.BodySm, color = MoyeotaColor.TextMute)
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
                style = MoyeotaType.BodySm,
                color = MoyeotaColor.TextMute,
            )
            Spacer(Modifier.height(32.dp))

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
                Spacer(Modifier.height(8.dp))
                Text(
                    text = errorText.orEmpty(),
                    style = MoyeotaType.BodySm.copy(fontWeight = FontWeight.SemiBold),
                    color = MoyeotaColor.Danger500,
                    modifier = Modifier.clickable { resend() },
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "남은 시간 ${formatSeconds(resendRemaining)}",
                    // 초가 바뀔 때마다 글자 폭이 흔들리지 않게 숫자 폭을 고정한다
                    style = MoyeotaType.BodySm.copy(fontWeight = FontWeight.SemiBold).tabularNums(),
                    color = MoyeotaColor.Waiting600,
                )
                // 글자 오른쪽 끝을 화면 여백에 맞춘다(TextLinkButton 의 안쪽 8dp 만큼 바깥으로)
                TextLinkButton(
                    text = "코드 다시 받기",
                    onClick = { resend() },
                    style = MoyeotaType.BodySm.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.offset(x = 8.dp),
                )
            }

            Spacer(Modifier.height(16.dp))

            // 흰 화면 위라 그림자 대신 Border 선으로 묶음을 나눈다(디자인 QA G4)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MoyeotaShape.Card)
                    .border(1.dp, MoyeotaColor.Border, MoyeotaShape.Card),
            ) {
                // 스팸함 안내 — 동작 없음
                EmailCodeHelpRow(
                    title = "스팸함을 확인해 주세요",
                    subtitle = "메일이 스팸으로 분류될 수 있어요",
                    onClick = null,
                )
                HorizontalDivider(
                    color = MoyeotaColor.Hairline,
                    modifier = Modifier.padding(horizontal = 16.dp),
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
            Spacer(Modifier.height(12.dp))
            Text(
                text = "코드는 10분 뒤에 만료돼요",
                style = MoyeotaType.CaptionMd,
                color = MoyeotaColor.TextMute,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        NavigationBarSpacer()
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
        textStyle = MoyeotaType.DisplayMd,
        onValueChange = onCodeChange,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        decorationBox = { innerTextField ->
            Box {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    repeat(CODE_LENGTH) { index ->
                        val filled = index < code.length
                        val active = index == code.length && enabled
                        // 칸 하나하나가 입력칸이다 — 그림자 대신 MoyeotaTextField 와 같은 테두리 규칙(쉬는 칸 BorderStrong)
                        val borderColor = when {
                            isError -> MoyeotaColor.Danger500
                            active -> MoyeotaColor.Primary500
                            else -> MoyeotaColor.BorderStrong
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp)
                                .background(MoyeotaColor.SurfaceCanvas, MoyeotaShape.Card)
                                .border(if (isError || active) 1.5.dp else 1.dp, borderColor, MoyeotaShape.Card),
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
                                        .background(MoyeotaColor.Primary500, MoyeotaShape.Pill),
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EmailCodeMailIcon()
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MoyeotaType.BodyStrong,
                color = MoyeotaColor.InkPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(text = subtitle, style = MoyeotaType.BodySm, color = MoyeotaColor.TextMute)
        }
        // 누를 수 있는 줄에만 꺾쇠를 둔다 — 안내뿐인 줄(스팸함)에 꺾쇠가 있으면 눌러도 아무 일 없는 버튼이 된다
        if (onClick != null) ChevronIcon()
    }
}

@Composable
private fun EmailCodeProgressBar(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(4.dp)
            .background(MoyeotaColor.Hairline, MoyeotaShape.Small),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .fillMaxHeight()
                .background(MoyeotaColor.Primary500, MoyeotaShape.Small),
        )
    }
}

@Composable
private fun EmailCodeMailIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(MoyeotaIconSize.Md)) {
        val stroke = IconStrokeWidth.toPx() * (MoyeotaIconSize.Md / MoyeotaIconSize.Lg)
        val color = MoyeotaColor.TextMute
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
