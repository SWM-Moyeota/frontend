package com.moyeota.presentation.feature.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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

// 06 · 학교 이메일 [S02]
// 진입: 05에서 「학생」 선택 / 뒤로 → 05 / 「인증 메일 보내기」 → 07 인증 코드
private val SupportedDomains = listOf("@pusan.ac.kr", "@pukyong.ac.kr", "@donga.ac.kr")

// 유효값 검증: 로컬파트 — 영문·숫자·. _ - 만 허용, 2~64자, 공백 불가
private val LocalPartRegex = Regex("^[A-Za-z0-9._-]{2,64}$")

private val LabelGray = Color(0xFF8A93A0)
private val SlateGray = Color(0xFF4B5563)
private val FooterGray = Color(0xFF9AA1AC)
private val InfoCardBlue = Color(0xFFF1F5FD)

@Composable
fun SchoolEmailScreen(
    onBack: () -> Unit = {},
    onSendMail: (email: String) -> Unit = {},
) {
    var localPart by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf(SupportedDomains.first()) }
    var domainMenuOpen by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }

    val localPartValid = LocalPartRegex.matches(localPart)
    val fieldError = if (localPart.isNotEmpty() && !localPartValid) {
        "영문·숫자·. _ - 만 2~64자로 입력할 수 있어요"
    } else {
        null
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
        SchoolEmailProgressBar(progress = 1f / 5f)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "학교 이메일을 알려주세요",
                style = MoyeotaType.DisplayMd,
                color = MoyeotaColor.InkPrimary,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "$domain 메일로 인증 코드를 보내드려요",
                style = MoyeotaType.BodySm.copy(fontSize = 14.sp),
                color = MoyeotaColor.TextMute,
            )
            Spacer(Modifier.height(30.dp))

            MoyeotaTextField(
                value = localPart,
                onValueChange = { new -> localPart = new.filter { !it.isWhitespace() } },
                label = "학교 이메일",
                placeholder = "moyeota",
                errorText = fieldError,
                helperText = "인증 메일은 5분 안에 도착해요",
                enabled = !submitting,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                trailing = {
                    Box {
                        Text(
                            text = domain,
                            style = MoyeotaType.BodyMd,
                            color = LabelGray,
                            modifier = Modifier.clickable { domainMenuOpen = true },
                        )
                        // 도메인 셀렉트 → @pusan.ac.kr / @pukyong.ac.kr / @donga.ac.kr — 지원 3개 외 선택 불가
                        DropdownMenu(
                            expanded = domainMenuOpen,
                            onDismissRequest = { domainMenuOpen = false },
                        ) {
                            SupportedDomains.forEach { candidate ->
                                DropdownMenuItem(
                                    text = { Text(text = candidate, style = MoyeotaType.BodySm) },
                                    onClick = {
                                        domain = candidate
                                        domainMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                },
            )

            Spacer(Modifier.height(24.dp))

            // 이렇게 보호해요 카드
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
                Spacer(Modifier.height(14.dp))
                SchoolEmailProtectRow(text = "이름과 학번은 저장하지 않아요")
                Spacer(Modifier.height(13.dp))
                SchoolEmailProtectRow(text = "메일 주소는 암호화해 보관해요")
                Spacer(Modifier.height(13.dp))
                SchoolEmailProtectRow(text = "다른 이용자에게 공개하지 않아요")
            }

            Spacer(Modifier.height(24.dp))

            // 다른 학교 메일 안내 → 도메인 셀렉트 열기
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(18.dp))
                    .background(Color.White, RoundedCornerShape(18.dp))
                    .clickable { domainMenuOpen = true }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "다른 학교 메일을 쓰나요?",
                        style = MoyeotaType.BodySm.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                        color = MoyeotaColor.InkPrimary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "부산대 · 부경대 · 동아대 메일을 지원해요",
                        style = MoyeotaType.CaptionMd,
                        color = LabelGray,
                    )
                }
                SchoolEmailChevronIcon()
            }
            Spacer(Modifier.height(24.dp))
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            PrimaryCtaButton(
                text = "인증 메일 보내기",
                onClick = {
                    submitting = true
                    onSendMail(localPart + domain)
                },
                enabled = localPartValid,
                loading = submitting,
            )
            Spacer(Modifier.height(14.dp))
            // 미연결: 학생증 인증 — 클릭해도 동작 없음 (대체 인증 플로우 필요)
            Text(
                text = "학생증으로 인증하기",
                style = MoyeotaType.BodySm.copy(fontWeight = FontWeight.Bold),
                color = MoyeotaColor.Primary500,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "인증 정보는 매칭 확인 외에 쓰이지 않아요",
                style = MoyeotaType.CaptionSm,
                color = FooterGray,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SchoolEmailProgressBar(progress: Float, modifier: Modifier = Modifier) {
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
private fun SchoolEmailProtectRow(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        SchoolEmailCheckIcon()
        Text(text = text, style = MoyeotaType.BodySm, color = SlateGray)
    }
}

@Composable
private fun SchoolEmailCheckIcon(modifier: Modifier = Modifier) {
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

@Composable
private fun SchoolEmailChevronIcon(modifier: Modifier = Modifier) {
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

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun SchoolEmailScreenPreview() {
    SchoolEmailScreen()
}
