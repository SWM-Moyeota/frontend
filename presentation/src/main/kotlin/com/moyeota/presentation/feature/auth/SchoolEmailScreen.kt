package com.moyeota.presentation.feature.auth

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.moyeota.core.designsystem.component.CheckIcon
import com.moyeota.core.designsystem.component.ChevronIcon
import com.moyeota.core.designsystem.component.MoyeotaIconSize
import com.moyeota.core.designsystem.component.MoyeotaTextField
import com.moyeota.core.designsystem.component.MoyeotaTopBar
import com.moyeota.core.designsystem.component.NavigationBarSpacer
import com.moyeota.core.designsystem.component.PrimaryCtaButton
import com.moyeota.core.designsystem.component.SectionLabel
import com.moyeota.core.designsystem.component.StatusBarSpacer
import com.moyeota.core.designsystem.component.TextLinkButton
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.core.designsystem.theme.MoyeotaShape
import com.moyeota.core.designsystem.theme.MoyeotaType

// 06 · 학교 이메일 [S02]
//
// ⚠️ 현재 어떤 그래프에도 등록되어 있지 않다 — 가입 필수 경로에서 05~08 을 걷어냈다.
// 가입 API 에 학교 인증 개념이 없고, 인증은 마이페이지에서 나중에 붙이는 컨셉이다.
// 그때 이 화면을 그대로 재사용하기 위해 파일은 남겨 둔다(MainNavGraph 의 C 절 주석 참고).
//
// (원래 배선) 진입: 05에서 「학생」 선택 / 뒤로 → 05 / 「인증 메일 보내기」 → 07 인증 코드
private val SupportedDomains = listOf("@pusan.ac.kr", "@pukyong.ac.kr", "@donga.ac.kr")

// 유효값 검증: 로컬파트 — 영문·숫자·. _ - 만 허용, 2~64자, 공백 불가
private val LocalPartRegex = Regex("^[A-Za-z0-9._-]{2,64}$")

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

    Column(modifier = Modifier.fillMaxSize().imePadding().background(MoyeotaColor.SurfaceCanvas)) {
        StatusBarSpacer()
        MoyeotaTopBar(
            title = "",
            onBack = onBack,
            actions = {
                Text(text = "1 / 5", style = MoyeotaType.BodySm, color = MoyeotaColor.TextMute)
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
            Spacer(Modifier.height(12.dp))
            Text(
                text = "$domain 메일로 인증 코드를 보내드려요",
                style = MoyeotaType.BodySm,
                color = MoyeotaColor.TextMute,
            )
            Spacer(Modifier.height(32.dp))

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
                        // 도메인 고르기 — 글자만 눌리던 자리를 48dp 터치 + 물결로(디자인 QA G12)
                        TextLinkButton(
                            text = domain,
                            onClick = { domainMenuOpen = true },
                            color = MoyeotaColor.TextMute,
                            style = MoyeotaType.BodyMd,
                            modifier = Modifier.offset(x = 8.dp),
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

            // 이렇게 보호해요 — 파란 상자를 걷고 머리말 + 체크 목록으로 화면에 바로 놓는다(디자인 QA G8).
            // 세 줄짜리라 한 문장용 NoticeBanner 에는 담기지 않고, 04 시작 화면의 신뢰 불릿(A1)과 같은 모양이다.
            SectionLabel(text = "이렇게 보호해요")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SchoolEmailProtectRow(text = "이름과 학번은 저장하지 않아요")
                SchoolEmailProtectRow(text = "메일 주소는 암호화해 보관해요")
                SchoolEmailProtectRow(text = "다른 이용자에게 공개하지 않아요")
            }

            Spacer(Modifier.height(24.dp))

            // 다른 학교 메일 안내 → 도메인 셀렉트 열기. 흰 화면 위라 그림자 대신 Border 선(디자인 QA G4)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MoyeotaShape.Card)
                    .border(1.dp, MoyeotaColor.Border, MoyeotaShape.Card)
                    .clickable { domainMenuOpen = true }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "다른 학교 메일을 쓰나요?",
                        style = MoyeotaType.BodyStrong,
                        color = MoyeotaColor.InkPrimary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "부산대 · 부경대 · 동아대 메일을 지원해요",
                        style = MoyeotaType.BodySm,
                        color = MoyeotaColor.TextMute,
                    )
                }
                ChevronIcon()
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
            Spacer(Modifier.height(12.dp))
            // 미연결: 학생증 인증 — 클릭해도 동작 없음 (대체 인증 플로우 필요)
            Text(
                text = "학생증으로 인증하기",
                style = MoyeotaType.BodySm.copy(fontWeight = FontWeight.SemiBold),
                color = MoyeotaColor.Primary600,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            // 11sp 는 배지 전용이다(G3) — 읽혀야 하는 안내라 CaptionMd · TextMute 로 올린다
            Text(
                text = "인증 정보는 매칭 확인 외에 쓰이지 않아요",
                style = MoyeotaType.CaptionMd,
                color = MoyeotaColor.TextMute,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        NavigationBarSpacer()
    }
}

@Composable
private fun SchoolEmailProgressBar(progress: Float, modifier: Modifier = Modifier) {
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

// 체크 + 안내 한 줄 — 체크는 「확인된 사실」 표시라 회색 선 아이콘이다(04 신뢰 불릿과 같은 모양)
@Composable
private fun SchoolEmailProtectRow(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CheckIcon(size = MoyeotaIconSize.Sm, tint = MoyeotaColor.TextMute)
        Text(text = text, style = MoyeotaType.BodySm, color = MoyeotaColor.TextBody)
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun SchoolEmailScreenPreview() {
    SchoolEmailScreen()
}
