package com.moyeota.presentation.feature.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeota.core.designsystem.component.BackArrowIcon
import com.moyeota.core.designsystem.component.PrimaryCtaButton
import com.moyeota.core.designsystem.component.StatusBarMock
import com.moyeota.core.designsystem.theme.MoyeotaColor

private val ScreenBackground = Color(0xFFF5F7FA)
private val TextSlate = Color(0xFF4B5563)
private val TextSoft = Color(0xFF8A93A0)
private val TextFaint = Color(0xFF9AA1AC)
private val IconBoxIdle = Color(0xFFF6F8FB)
private val RadioIdleBorder = Color(0xFFD1D5DB)
private val CardShadow = Color(0x0F1B2A4A)

// 05 화면에서 분기되는 계정 유형 (라디오 1개만 선택 가능, 기본 선택 없음)
enum class AccountType {
    STUDENT, // 학생 → 06 학교 이메일
    WORKER, // 직장인 → 08 직장·일반 신원 인증
    GENERAL, // 일반 → 08 (본인 인증만 수행)
}

/**
 * 05 · 계정 유형 선택 [S25]
 *
 * 진입: 04 로그인 성공
 *
 * @param onBack 뒤로 → 04 시작 — 로그인 방식
 * @param onNext 유형 선택 + 「다음」 → 학생: 06 학교 이메일 / 직장인: 08 직장·일반 신원 인증 / 일반: 08 (본인 인증만 수행)
 * @param onSkipVerification 「나중에 인증할게요」 → 14 홈 (미인증 상태)
 */
@Composable
fun AccountTypeScreen(
    onBack: () -> Unit,
    onNext: (AccountType) -> Unit,
    onSkipVerification: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 라디오 1개만 선택 가능, 기본 선택 없음 — 미선택 시 「다음」 비활성
    var selectedType by remember { mutableStateOf<AccountType?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ScreenBackground),
    ) {
        StatusBarMock()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            IconButton(onClick = onBack, modifier = Modifier.padding(top = 12.dp)) {
                BackArrowIcon()
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = "어떤 방법으로\n인증할까요?",
                color = MoyeotaColor.InkPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 34.sp,
                letterSpacing = (-0.48).sp,
            )

            Spacer(Modifier.height(16.dp))
            Text(
                text = "신분에 맞는 인증으로 신뢰를 확인해요",
                color = MoyeotaColor.TextMute,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = (-0.28).sp,
            )

            Spacer(Modifier.height(34.dp))
            AccountTypeCard(
                title = "학생",
                subtitle = "학교 이메일로 인증해요",
                caption = "부산대 · 부경대 · 동아대",
                icon = { color -> GraduationCapIcon(color = color) },
                selected = selectedType == AccountType.STUDENT,
                onClick = { selectedType = AccountType.STUDENT },
            )

            Spacer(Modifier.height(16.dp))
            AccountTypeCard(
                title = "직장인",
                subtitle = "재직 정보로 인증해요",
                caption = "회사 메일 또는 재직 서류",
                icon = { color -> BriefcaseIcon(color = color) },
                selected = selectedType == AccountType.WORKER,
                onClick = { selectedType = AccountType.WORKER },
            )

            Spacer(Modifier.height(16.dp))
            AccountTypeCard(
                title = "일반",
                subtitle = "휴대폰 본인 인증으로 시작해요",
                caption = "누구나 · 매너 기록으로 신뢰를 쌓아요",
                icon = { color -> IdCardIcon(color = color) },
                selected = selectedType == AccountType.GENERAL,
                onClick = { selectedType = AccountType.GENERAL },
            )

            Spacer(Modifier.height(8.dp))
            Text(
                text = "인증 사용자에게 마일리지를 적립해 신뢰를 쌓는 방식은 고도화 시 검토",
                color = TextFaint,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 17.sp,
                letterSpacing = (-0.24).sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(7.dp))
            Text(
                text = "인증 유형은 마이페이지에서 추가할 수 있어요",
                color = TextFaint,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 17.sp,
                letterSpacing = (-0.24).sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(13.dp))
            PrimaryCtaButton(
                text = "다음",
                onClick = { selectedType?.let(onNext) },
                enabled = selectedType != null,
            )

            Spacer(Modifier.height(18.dp))
            Text(
                text = "나중에 인증할게요",
                color = MoyeotaColor.TextMute,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 19.sp,
                letterSpacing = (-0.26).sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onSkipVerification,
                    ),
            )
            Spacer(Modifier.height(28.dp))
        }

        HomeIndicatorMock()
    }
}

// 인증 유형 선택 카드 — 선택 시 파란 테두리 + 파란 아이콘/라디오
@Composable
private fun AccountTypeCard(
    title: String,
    subtitle: String,
    caption: String,
    icon: @Composable (Color) -> Unit,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    val cardModifier = if (selected) {
        modifier
            .fillMaxWidth()
            .background(Color.White, shape)
            .border(1.5.dp, MoyeotaColor.Primary500, shape)
    } else {
        modifier
            .fillMaxWidth()
            .shadow(3.dp, shape, ambientColor = CardShadow, spotColor = CardShadow)
            .background(Color.White, shape)
    }
    Row(
        modifier = cardModifier
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(if (selected) MoyeotaColor.Primary50 else IconBoxIdle, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            icon(if (selected) MoyeotaColor.Primary500 else TextSlate)
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MoyeotaColor.InkPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 25.sp,
                letterSpacing = (-0.34).sp,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = subtitle,
                color = TextSlate,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 19.sp,
                letterSpacing = (-0.26).sp,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = caption,
                color = TextSoft,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 17.sp,
                letterSpacing = (-0.24).sp,
            )
        }
        Spacer(Modifier.width(12.dp))
        RadioMark(selected = selected)
    }
}

// 라디오 표시 — 선택: 파란 원 + 흰 체크 / 미선택: 회색 외곽선 원
@Composable
private fun RadioMark(selected: Boolean, modifier: Modifier = Modifier) {
    if (selected) {
        Box(
            modifier = modifier
                .size(24.dp)
                .background(MoyeotaColor.Primary500, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            CheckMarkIcon(iconSize = 12.dp, color = Color.White)
        }
    } else {
        Box(
            modifier = modifier
                .size(24.dp)
                .border(2.dp, RadioIdleBorder, CircleShape),
        )
    }
}

// 학사모 아이콘 (아이콘 라이브러리 없이 Canvas 로 표현)
@Composable
private fun GraduationCapIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
        // 모자 상판 (마름모)
        val board = Path().apply {
            moveTo(w * 0.5f, h * 0.14f)
            lineTo(w * 0.95f, h * 0.38f)
            lineTo(w * 0.5f, h * 0.62f)
            lineTo(w * 0.05f, h * 0.38f)
            close()
        }
        drawPath(board, color)
        // 모자 몸통
        val body = Path().apply {
            moveTo(w * 0.26f, h * 0.5f)
            lineTo(w * 0.26f, h * 0.74f)
            quadraticBezierTo(w * 0.5f, h * 0.92f, w * 0.74f, h * 0.74f)
            lineTo(w * 0.74f, h * 0.5f)
        }
        drawPath(body, color, style = stroke)
        // 태슬
        drawLine(color, Offset(w * 0.95f, h * 0.38f), Offset(w * 0.95f, h * 0.62f), 1.8.dp.toPx(), StrokeCap.Round)
    }
}

// 서류 가방 아이콘
@Composable
private fun BriefcaseIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val strokePx = 1.8.dp.toPx()
        val stroke = Stroke(width = strokePx, cap = StrokeCap.Round)
        // 가방 몸통
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.1f, h * 0.32f),
            size = Size(w * 0.8f, h * 0.52f),
            cornerRadius = CornerRadius(3.dp.toPx()),
            style = stroke,
        )
        // 손잡이
        val handle = Path().apply {
            moveTo(w * 0.36f, h * 0.32f)
            lineTo(w * 0.36f, h * 0.2f)
            lineTo(w * 0.64f, h * 0.2f)
            lineTo(w * 0.64f, h * 0.32f)
        }
        drawPath(handle, color, style = stroke)
        // 가운데 라인
        drawLine(color, Offset(w * 0.1f, h * 0.56f), Offset(w * 0.9f, h * 0.56f), strokePx)
    }
}

// 신분증 아이콘
@Composable
private fun IdCardIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val strokePx = 1.8.dp.toPx()
        val stroke = Stroke(width = strokePx, cap = StrokeCap.Round)
        // 카드 외곽
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.06f, h * 0.22f),
            size = Size(w * 0.88f, h * 0.56f),
            cornerRadius = CornerRadius(3.dp.toPx()),
            style = stroke,
        )
        // 얼굴 원
        drawCircle(
            color = color,
            radius = w * 0.09f,
            center = Offset(w * 0.3f, h * 0.48f),
            style = Stroke(width = strokePx),
        )
        // 정보 라인
        drawLine(color, Offset(w * 0.52f, h * 0.42f), Offset(w * 0.8f, h * 0.42f), strokePx, StrokeCap.Round)
        drawLine(color, Offset(w * 0.52f, h * 0.58f), Offset(w * 0.8f, h * 0.58f), strokePx, StrokeCap.Round)
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun AccountTypeScreenPreview() {
    AccountTypeScreen(
        onBack = {},
        onNext = {},
        onSkipVerification = {},
    )
}
