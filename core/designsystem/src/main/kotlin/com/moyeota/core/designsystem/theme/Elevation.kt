package com.moyeota.core.designsystem.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 그림자 높이 — **지도 위에 뜬 것만 그림자를 갖는다**(디자인 QA G4, 2026-11).
 *
 * 예전엔 2·3·4·8·10·12·14dp 일곱 가지 그림자가 카드·칩·말풍선·목록 묶음에까지 붙어 있었고, 세 곳은
 * 파란 빛번짐이었다. 이미 흰 시트 **안에** 있는 카드에 그림자를 주면 시트와 카드가 같은 높이를 다투어
 * 화면이 떠 보인다. 흰 면 위에서 카드를 나눌 때는 그림자 대신 [MoyeotaColor.Border] 선이나
 * [MoyeotaColor.SurfaceSoft] 면을 쓴다.
 *
 * | 단계 | 값 | 쓰는 곳 |
 * |---|---|---|
 * | [MapOverlay] | 4dp | 지도 위 알약·원형 버튼 |
 * | [Sheet] | 8dp | 아래에서 올라온 시트의 윗가장자리 |
 * | [Modal] | 12dp | 진짜 겹침(드롭다운 메뉴·다이얼로그)만 |
 *
 * 그림자 색은 늘 회청색이다 — 브랜드 파랑으로 물든 그림자는 쓰지 않는다.
 * iOS: `.shadow(color: .black.opacity(0.1), radius: 값/2, y: 값/4)` 정도로 옮긴다.
 */
object MoyeotaElevation {
    val MapOverlay: Dp = 4.dp
    val Sheet: Dp = 8.dp
    val Modal: Dp = 12.dp
}

/** 지도 위에 뜬 알약·버튼의 그림자([MoyeotaElevation.MapOverlay]) */
fun Modifier.mapOverlayShadow(shape: Shape): Modifier =
    shadow(MoyeotaElevation.MapOverlay, shape, spotColor = MoyeotaColor.ShadowOverlay)

/** 시트 윗가장자리 그림자([MoyeotaElevation.Sheet]) */
fun Modifier.sheetShadow(shape: Shape = MoyeotaShape.Sheet): Modifier =
    shadow(MoyeotaElevation.Sheet, shape, spotColor = MoyeotaColor.ShadowSheet)

/** 드롭다운 메뉴·다이얼로그처럼 화면 위에 진짜로 겹치는 것의 그림자([MoyeotaElevation.Modal]) */
fun Modifier.modalShadow(shape: Shape): Modifier =
    shadow(MoyeotaElevation.Modal, shape, spotColor = MoyeotaColor.ShadowSheet)
