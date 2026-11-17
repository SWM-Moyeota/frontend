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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.moyeota.core.designsystem.component.CheckIcon
import com.moyeota.core.designsystem.component.IconStrokeWidth
import com.moyeota.core.designsystem.component.MoyeotaIconSize
import com.moyeota.core.designsystem.component.MoyeotaTextField
import com.moyeota.core.designsystem.component.MoyeotaTopBar
import com.moyeota.core.designsystem.component.NavigationBarSpacer
import com.moyeota.core.designsystem.component.PrimaryCtaButton
import com.moyeota.core.designsystem.component.SectionLabel
import com.moyeota.core.designsystem.component.StatusBarSpacer
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.core.designsystem.theme.MoyeotaShape
import com.moyeota.core.designsystem.theme.MoyeotaType

// 08 · 직장인 재직 인증 [S26]
//
// ⚠️ 현재 어떤 그래프에도 등록되어 있지 않다 (06 과 같은 이유 — 인증은 마이페이지로 이동 예정).
//
// (원래 배선) 진입: 05에서 「직장인」 선택 / 뒤로 → 05 / 「인증 요청 보내기」 → 09 본인 인증
// 유효값 검증: 회사 이메일 — RFC 형식 + 무료 메일 도메인(gmail·naver 등) 차단
private val CompanyEmailRegex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
private val FreeMailDomains = setOf(
    "gmail.com", "naver.com", "daum.net", "hanmail.net", "kakao.com",
    "nate.com", "outlook.com", "hotmail.com", "yahoo.com", "icloud.com",
)

@Composable
fun WorkVerifyScreen(
    onBack: () -> Unit = {},
    onSubmit: (companyEmail: String) -> Unit = {},
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
    // 회사 메일과 재직 서류 중 최소 1개 필수 — 서류 업로드가 미연결이므로 회사 메일이 사실상 필수다
    val ctaEnabled = emailValid

    Column(modifier = Modifier.fillMaxSize().imePadding().background(MoyeotaColor.SurfaceCanvas)) {
        StatusBarSpacer()
        MoyeotaTopBar(
            title = "",
            onBack = onBack,
            actions = {
                // 08 은 학생 경로의 06·07 과 같은 「인증」 1단계다 — 09 가 2단계이므로 여기는 1/5
                Text(text = "1 / 5", style = MoyeotaType.BodySm, color = MoyeotaColor.TextMute)
            },
        )
        WorkVerifyProgressBar(progress = 1f / 5f)

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
            Spacer(Modifier.height(12.dp))
            Text(
                text = "재직 정보로 직장인 인증을 진행해요",
                style = MoyeotaType.BodySm,
                color = MoyeotaColor.TextMute,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = "직장인 재직 인증 · 05 계정 유형 선택에서 선택함",
                style = MoyeotaType.BodySm.copy(fontWeight = FontWeight.SemiBold),
                color = MoyeotaColor.Primary600,
            )
            Spacer(Modifier.height(24.dp))

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
            // 필드 라벨은 MoyeotaTextField 의 라벨과 같은 모양(BodySm · TextBody)이어야 한 화면에서 같은 층으로 읽힌다
            Text(text = "재직 서류 (선택)", style = MoyeotaType.BodySm, color = MoyeotaColor.TextBody)
            Spacer(Modifier.height(8.dp))
            // 미연결: 재직 서류 탭 → 파일/카메라 선택 시트 — 클릭해도 동작 없음
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MoyeotaColor.SurfaceSoft, MoyeotaShape.Card)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 아이콘 바탕은 흰색 — 회색 면 위에 옅은 파랑을 또 깔면 면이 세 겹이 된다
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(MoyeotaColor.SurfaceCanvas, MoyeotaShape.Card),
                    contentAlignment = Alignment.Center,
                ) {
                    WorkVerifyCameraIcon()
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = "재직증명서 · 사원증 촬영",
                        style = MoyeotaType.BodyStrong,
                        color = MoyeotaColor.InkPrimary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "회사 메일이 없으면 서류로 인증할 수 있어요",
                        style = MoyeotaType.BodySm,
                        color = MoyeotaColor.TextMute,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            // 이렇게 보호해요 — 파란 상자를 걷고 머리말 + 체크 목록으로 화면에 바로 놓는다(디자인 QA G8).
            // 세 줄짜리라 한 문장용 NoticeBanner 에는 담기지 않고, 04 시작 화면의 신뢰 불릿(A1)과 같은 모양이다.
            SectionLabel(text = "이렇게 보호해요")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WorkVerifyProtectRow(text = "회사명과 서류는 인증 후 즉시 삭제해요")
                WorkVerifyProtectRow(text = "직업은 「직장인 인증」 배지로만 표시돼요")
                WorkVerifyProtectRow(text = "동료에게 가입 사실이 알려지지 않아요")
            }
            Spacer(Modifier.height(24.dp))
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            PrimaryCtaButton(
                text = "인증 요청 보내기",
                onClick = {
                    submitting = true
                    onSubmit(email)
                },
                enabled = ctaEnabled,
                loading = submitting,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "재직 확인은 보통 1일 안에 끝나요",
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
private fun WorkVerifyProgressBar(progress: Float, modifier: Modifier = Modifier) {
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
private fun WorkVerifyProtectRow(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CheckIcon(size = MoyeotaIconSize.Sm, tint = MoyeotaColor.TextMute)
        Text(text = text, style = MoyeotaType.BodySm, color = MoyeotaColor.TextBody)
    }
}

@Composable
private fun WorkVerifyCameraIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(MoyeotaIconSize.Md)) {
        // 공용 아이콘과 같은 선 굵기(24dp 기준 1.75dp 를 20dp 로 비례)
        val stroke = IconStrokeWidth.toPx() * (MoyeotaIconSize.Md / MoyeotaIconSize.Lg)
        val color = MoyeotaColor.TextBody
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
