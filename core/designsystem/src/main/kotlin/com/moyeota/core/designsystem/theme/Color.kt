package com.moyeota.core.designsystem.theme

import androidx.compose.ui.graphics.Color

// moyeota-figma-tokens.json 의 core.color 값 그대로
object MoyeotaColor {
    // primary
    val Primary500 = Color(0xFF085AF5)
    val Primary600 = Color(0xFF054BC7)
    val Primary700 = Color(0xFF033D9F)
    val Primary50 = Color(0xFFE6EEFE)

    // status
    val Success500 = Color(0xFF10B981)
    val Success600 = Color(0xFF0F8F66)
    val Success50 = Color(0xFFD1FAE5)
    val Waiting500 = Color(0xFFF59E0B)
    val Waiting600 = Color(0xFFB57407)
    val Waiting50 = Color(0xFFFEF3C7)
    val Danger500 = Color(0xFFE63946)
    val Danger600 = Color(0xFFB32836)
    val Danger50 = Color(0xFFFDECEE)

    // 비상 신고 전용 — 다른 곳 사용 금지
    val Safety500 = Color(0xFFDC2626)
    val Safety600 = Color(0xFFA81D1D)

    // ink / text
    val InkPrimary = Color(0xFF111111)
    val InkDeep = Color(0xFF0A0A0B)
    val TextBody = Color(0xB8111111)      // rgba(17,17,17,0.72)
    val TextMute = Color(0xFF6B7280)
    val TextAsh = Color(0xFFD1D5DB)
    val TextOnDark = Color(0xFFFFFFFF)
    val Link = Color(0xFF054BC7)

    // surface
    val SurfaceCanvas = Color(0xFFFFFFFF)
    val SurfaceSoft = Color(0xFFF4F6FA)
    val SurfaceCard = Color(0xFFFFFFFF)
    val MapOverlay = Color(0xF0FFFFFF)    // rgba(255,255,255,0.94)
    val Hairline = Color(0xFFEDEFF3)

    // route / marker
    val RouteShared = Color(0xFF085AF5)
    val RouteUserA = Color(0xFF3B82F6)
    val RouteUserB = Color(0xFF8B5CF6)
    val MarkerOrigin = Color(0xFF085AF5)
    val MarkerDestination = Color(0xFFEF4444)
    val MarkerPickup = Color(0xFF10B981)
    val MarkerDropoff = Color(0xFFF97316)

    val Scrim = Color(0x66000000)         // opacity.scrim 40%
}
