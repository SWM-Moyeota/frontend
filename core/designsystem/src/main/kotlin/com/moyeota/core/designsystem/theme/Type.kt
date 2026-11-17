package com.moyeota.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.moyeota.core.designsystem.R

val PretendardFontFamily = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
)

/**
 * moyeota-figma-tokens.json 의 core.font 값 그대로.
 *
 * ### 쓰는 규칙 (디자인 QA G3, 2026-11)
 * - **크기는 이 표 밖으로 나가지 않는다** — 11(배지만)·12·13·15·16·18·22·24. 화면에서 `fontSize =` 를
 *   직접 쓰지 않는다. 예전엔 16가지 크기가 섞여 화면마다 "비슷하지만 다른" 글자가 생겼다
 * - **굵기는 역할이 정한다** — Bold 는 [ButtonLg] 와 Display 계열만, 제목은 SemiBold, 본문은 Normal.
 *   모든 글자가 굵으면 눈이 쉴 곳이 없어 위계가 사라진다
 * - 목록 제목은 [BodyStrong], 메타는 [BodySm]
 */
object MoyeotaType {
    val DisplayXl = TextStyle(fontFamily = PretendardFontFamily, fontSize = 32.sp, fontWeight = FontWeight.Bold, lineHeight = 1.25.em, letterSpacing = (-0.4).sp)
    val DisplayLg = TextStyle(fontFamily = PretendardFontFamily, fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 1.25.em, letterSpacing = (-0.3).sp)
    val DisplayMd = TextStyle(fontFamily = PretendardFontFamily, fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 1.25.em, letterSpacing = (-0.2).sp)
    val HeadingXl = TextStyle(fontFamily = PretendardFontFamily, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 1.35.em, letterSpacing = (-0.1).sp)
    val HeadingLg = TextStyle(fontFamily = PretendardFontFamily, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 1.35.em, letterSpacing = (-0.1).sp)
    val HeadingMd = TextStyle(fontFamily = PretendardFontFamily, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 1.35.em, letterSpacing = (-0.1).sp)
    val BodyMd = TextStyle(fontFamily = PretendardFontFamily, fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 1.5.em)
    /** 목록 제목·장소 이름 — 본문 크기 그대로 굵기만 올린다(디자인 문서 body-strong, 15/600/1.4) */
    val BodyStrong = TextStyle(fontFamily = PretendardFontFamily, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 1.4.em)
    val BodySm = TextStyle(fontFamily = PretendardFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 1.5.em)
    // 디자인 문서 caption-md 는 500 — 12sp 를 400 으로 두면 흰 바탕에서 획이 가늘어 회색 글자가 흐려진다
    val CaptionMd = TextStyle(fontFamily = PretendardFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 1.35.em, letterSpacing = 0.2.sp)
    val CaptionSm = TextStyle(fontFamily = PretendardFontFamily, fontSize = 11.sp, fontWeight = FontWeight.Normal, lineHeight = 1.35.em, letterSpacing = 0.2.sp)
    val ButtonLg = TextStyle(fontFamily = PretendardFontFamily, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    val ButtonMd = TextStyle(fontFamily = PretendardFontFamily, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    val ButtonSm = TextStyle(fontFamily = PretendardFontFamily, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
}

/**
 * 숫자 폭을 고정한다(tabular figures) — 시각·요금처럼 **값이 바뀌어도 자리가 흔들리면 안 되는** 숫자에 쓴다.
 * 「오후 7:51」→「오후 8:16」에서 글자 폭이 달라 오른쪽 끝이 출렁이지 않게 한다. iOS: `.monospacedDigit()`.
 */
fun TextStyle.tabularNums(): TextStyle = copy(fontFeatureSettings = "tnum")

// Material 컴포넌트의 기본 크기·굵기는 유지하고 글꼴만 통일한다.
internal val MoyeotaTypography = Typography().let { defaults ->
    Typography(
        displayLarge = defaults.displayLarge.copy(fontFamily = PretendardFontFamily),
        displayMedium = defaults.displayMedium.copy(fontFamily = PretendardFontFamily),
        displaySmall = defaults.displaySmall.copy(fontFamily = PretendardFontFamily),
        headlineLarge = defaults.headlineLarge.copy(fontFamily = PretendardFontFamily),
        headlineMedium = defaults.headlineMedium.copy(fontFamily = PretendardFontFamily),
        headlineSmall = defaults.headlineSmall.copy(fontFamily = PretendardFontFamily),
        titleLarge = defaults.titleLarge.copy(fontFamily = PretendardFontFamily),
        titleMedium = defaults.titleMedium.copy(fontFamily = PretendardFontFamily),
        titleSmall = defaults.titleSmall.copy(fontFamily = PretendardFontFamily),
        bodyLarge = defaults.bodyLarge.copy(fontFamily = PretendardFontFamily),
        bodyMedium = defaults.bodyMedium.copy(fontFamily = PretendardFontFamily),
        bodySmall = defaults.bodySmall.copy(fontFamily = PretendardFontFamily),
        labelLarge = defaults.labelLarge.copy(fontFamily = PretendardFontFamily),
        labelMedium = defaults.labelMedium.copy(fontFamily = PretendardFontFamily),
        labelSmall = defaults.labelSmall.copy(fontFamily = PretendardFontFamily),
    )
}
