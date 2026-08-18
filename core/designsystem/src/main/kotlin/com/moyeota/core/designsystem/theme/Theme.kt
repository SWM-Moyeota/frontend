package com.moyeota.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = MoyeotaColor.Primary500,
    onPrimary = MoyeotaColor.TextOnDark,
    primaryContainer = MoyeotaColor.Primary50,
    onPrimaryContainer = MoyeotaColor.Primary700,
    error = MoyeotaColor.Danger500,
    onError = MoyeotaColor.TextOnDark,
    background = MoyeotaColor.SurfaceCanvas,
    onBackground = MoyeotaColor.InkPrimary,
    surface = MoyeotaColor.SurfaceCanvas,
    onSurface = MoyeotaColor.InkPrimary,
    surfaceVariant = MoyeotaColor.SurfaceSoft,
    onSurfaceVariant = MoyeotaColor.TextMute,
    outline = MoyeotaColor.Hairline,
)

@Composable
fun MoyeotaTheme(content: @Composable () -> Unit) {
    // 와이어프레임은 라이트 단일 테마
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content,
    )
}
