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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.moyeota.core.designsystem.component.MoyeotaTextField
import com.moyeota.core.designsystem.component.MoyeotaTopBar
import com.moyeota.core.designsystem.component.PrimaryCtaButton
import com.moyeota.core.designsystem.component.StatusBarMock
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.core.designsystem.theme.MoyeotaTheme
import com.moyeota.core.designsystem.theme.MoyeotaType

// 10 · 프로필 만들기 [S05]
// 진입: 09 본인 인증 완료 / 뒤로 → 09 / 「다음」 → 11 안심 설정
@Composable
fun ProfileSetupScreen(
    onBack: () -> Unit,
    onNext: (displayName: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf("") }
    var selectedColor by remember { mutableIntStateOf(0) }

    // [유효값 검증]
    // · 표시 이름 3~8자 (카운터 「n / 8」 실시간 갱신)
    // · 한글·영문·숫자 허용, 공백·특수문자·이모지 불가
    // · 금칙어·욕설 필터, 운영자 사칭어(모여타·관리자) 차단
    // · 중복 허용 (고유 식별자 아님) — 별도 중복 검사 없음
    val nameError: String? = when {
        name.isEmpty() -> null
        !profileNameCharRegex.matches(name) -> "한글·영문·숫자만 쓸 수 있어요 (공백·특수문자·이모지 불가)"
        name.length < 3 -> "표시 이름은 3~8자로 입력해 주세요"
        profileBannedWords.any { name.contains(it) } -> "사용할 수 없는 표시 이름이에요"
        else -> null
    }
    val isValid = name.isNotEmpty() && nameError == null

    Column(modifier = modifier.fillMaxSize().background(MoyeotaColor.SurfaceSoft)) {
        StatusBarMock()
        MoyeotaTopBar(
            title = "",
            onBack = onBack,
            actions = {
                Text(
                    text = "3 / 5",
                    style = MoyeotaType.BodySm,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF8A93A0),
                )
            },
        )
        ProfileStepProgressBar(progress = 3f / 5f)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "어떻게 불러드릴까요?",
                style = MoyeotaType.DisplayMd,
                color = MoyeotaColor.InkPrimary,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "실명 대신 표시 이름으로 보여요",
                style = MoyeotaType.BodySm.copy(fontSize = 14.sp),
                color = MoyeotaColor.TextMute,
            )

            Spacer(Modifier.height(22.dp))
            ProfileAvatarPlaceholder(
                tint = profileAvatarPalette[selectedColor],
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                profileAvatarPalette.forEachIndexed { index, swatch ->
                    ProfileColorSwatch(
                        swatch = swatch,
                        selected = index == selectedColor,
                        onClick = { selectedColor = index },
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
            MoyeotaTextField(
                value = name,
                onValueChange = { name = it.take(8) },
                label = "표시 이름",
                placeholder = "예) 김OO",
                errorText = nameError,
                helperText = if (isValid) "탑승 상대에게는 「$name」 으로 보여요" else null,
                trailing = {
                    Text(
                        text = "${name.length} / 8",
                        style = MoyeotaType.CaptionMd,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF9AA1AC),
                    )
                },
            )

            Spacer(Modifier.height(24.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF1F5FD), RoundedCornerShape(18.dp))
                    .padding(horizontal = 20.dp, vertical = 20.dp),
            ) {
                Text(
                    text = "사진은 올리지 않아요",
                    style = MoyeotaType.BodySm.copy(fontSize = 14.sp),
                    fontWeight = FontWeight.Bold,
                    color = MoyeotaColor.InkPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "얼굴 사진 없이도 학교 인증 배지와 매너 기록으로 서로를 확인할 수 있어요",
                    style = MoyeotaType.BodySm,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF4B5563),
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            // 미입력·규칙 위반 시 「다음」 비활성
            PrimaryCtaButton(
                text = "다음",
                onClick = { onNext(name) },
                enabled = isValid,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "표시 이름은 나중에 바꿀 수 있어요",
                style = MoyeotaType.CaptionMd,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF9AA1AC),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
        ProfileHomeIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

private val profileNameCharRegex = Regex("^[가-힣a-zA-Z0-9]+$")

// 금칙어·욕설 + 운영자 사칭어(모여타·관리자)
private val profileBannedWords = listOf(
    "모여타", "관리자", "운영자", "admin",
    "시발", "씨발", "병신", "새끼", "지랄", "미친", "좆", "썅",
)

private data class ProfileAvatarSwatch(val outer: Color, val inner: Color)

private val profileAvatarPalette = listOf(
    ProfileAvatarSwatch(outer = Color(0xFFD8E0F2), inner = Color(0xFF8296C8)),
    ProfileAvatarSwatch(outer = Color(0xFFD5EEDF), inner = Color(0xFF41BA83)),
    ProfileAvatarSwatch(outer = Color(0xFFE3DDF6), inner = Color(0xFF9A82DC)),
    ProfileAvatarSwatch(outer = Color(0xFFF4E3D0), inner = Color(0xFFDFA366)),
)

// 와이어프레임의 사람 실루엣 아바타 (사진 업로드 없음 — 정책상 미지원)
@Composable
private fun ProfileAvatarPlaceholder(tint: ProfileAvatarSwatch, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(88.dp).clip(CircleShape)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(color = tint.outer)
            // 머리
            drawCircle(
                color = tint.inner,
                radius = size.width * 0.155f,
                center = Offset(size.width / 2f, size.height * 0.40f),
            )
            // 어깨
            drawCircle(
                color = tint.inner,
                radius = size.width * 0.30f,
                center = Offset(size.width / 2f, size.height * 0.98f),
            )
        }
    }
}

@Composable
private fun ProfileColorSwatch(
    swatch: ProfileAvatarSwatch,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .then(
                if (selected) {
                    Modifier.border(2.dp, MoyeotaColor.Primary500, CircleShape)
                } else {
                    Modifier
                },
            )
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(34.dp).background(swatch.outer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(13.dp).background(swatch.inner, CircleShape))
        }
    }
}

@Composable
private fun ProfileStepProgressBar(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(4.dp)
            .background(Color(0xFFE6EAF0), RoundedCornerShape(2.dp)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress)
                .height(4.dp)
                .background(MoyeotaColor.Primary500, RoundedCornerShape(2.dp)),
        )
    }
}

@Composable
private fun ProfileHomeIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(top = 12.dp, bottom = 9.dp)
            .size(width = 135.dp, height = 5.dp)
            .background(MoyeotaColor.InkPrimary, RoundedCornerShape(2.5.dp)),
    )
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun ProfileSetupScreenPreview() {
    MoyeotaTheme {
        ProfileSetupScreen(onBack = {}, onNext = {})
    }
}
