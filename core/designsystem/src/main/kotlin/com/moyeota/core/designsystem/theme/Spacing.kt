package com.moyeota.core.designsystem.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 간격 — 4/8 리듬(디자인 문서 spacing 표).
 *
 * 화면 좌우 여백은 **[ScreenGutter] 하나**다. 예전엔 16·18·20·24dp 가 섞여 같은 화면 안에서도 글자의
 * 왼쪽 끝이 어긋났다(홈 시트: 제목 20dp, 「즐겨찾기」 16dp). 세로 간격도 10·11·13·14·17·18 같은 값을
 * 쓰지 않고 이 표로 올리거나 내린다.
 */
object MoyeotaSpacing {
    val Xxs: Dp = 4.dp
    val Xs: Dp = 8.dp
    val Sm: Dp = 12.dp
    val Md: Dp = 16.dp
    val Lg: Dp = 24.dp
    val Xl: Dp = 32.dp

    /** 모든 화면·시트의 좌우 여백 */
    val ScreenGutter: Dp = 16.dp

    /** 한 화면 안의 묶음과 묶음 사이 */
    val SectionGap: Dp = 24.dp
}
