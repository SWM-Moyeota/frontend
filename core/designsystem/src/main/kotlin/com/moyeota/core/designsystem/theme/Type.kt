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

// moyeota-figma-tokens.json 의 core.font 값 그대로.
object MoyeotaType {
    val DisplayXl = TextStyle(fontFamily = PretendardFontFamily, fontSize = 32.sp, fontWeight = FontWeight.Bold, lineHeight = 1.25.em, letterSpacing = (-0.4).sp)
    val DisplayLg = TextStyle(fontFamily = PretendardFontFamily, fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 1.25.em, letterSpacing = (-0.3).sp)
    val DisplayMd = TextStyle(fontFamily = PretendardFontFamily, fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 1.25.em, letterSpacing = (-0.2).sp)
    val HeadingXl = TextStyle(fontFamily = PretendardFontFamily, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 1.35.em, letterSpacing = (-0.1).sp)
    val HeadingLg = TextStyle(fontFamily = PretendardFontFamily, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 1.35.em, letterSpacing = (-0.1).sp)
    val HeadingMd = TextStyle(fontFamily = PretendardFontFamily, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 1.35.em, letterSpacing = (-0.1).sp)
    val BodyMd = TextStyle(fontFamily = PretendardFontFamily, fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 1.5.em)
    val BodySm = TextStyle(fontFamily = PretendardFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 1.5.em)
    val CaptionMd = TextStyle(fontFamily = PretendardFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 1.35.em, letterSpacing = 0.2.sp)
    val CaptionSm = TextStyle(fontFamily = PretendardFontFamily, fontSize = 11.sp, fontWeight = FontWeight.Normal, lineHeight = 1.35.em, letterSpacing = 0.2.sp)
    val ButtonLg = TextStyle(fontFamily = PretendardFontFamily, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    val ButtonMd = TextStyle(fontFamily = PretendardFontFamily, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    val ButtonSm = TextStyle(fontFamily = PretendardFontFamily, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
}

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
