package com.moyeota.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.Canvas
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.core.designsystem.theme.MoyeotaType

// 시스템 상태바 인셋만큼의 여백.
//
// 앱은 enableEdgeToEdge() 로 그려져 콘텐츠가 상태바 아래까지 올라온다. 예전에는 와이어프레임에서
// 따라온 상태바 목업(6:42 + 아이콘)이 44dp 를 차지하며 사실상 이 인셋 역할을 겸했는데,
// 실제 상태바와 이중으로 보여 제거했다. 목업이 있던 자리에 그대로 두어 콘텐츠가
// 상태바에 겹치지 않게 한다 — 배경은 바깥 컨테이너가 칠하므로 상태바 뒤까지 이어진다.
@Composable
fun StatusBarSpacer(modifier: Modifier = Modifier) {
    Spacer(modifier = modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars))
}

// 시스템 내비게이션바(제스처 인디케이터) 인셋만큼의 여백 — StatusBarSpacer 의 하단 짝.
//
// 와이어프레임에서 따라온 홈 인디케이터 목업(135x5dp 검은 바)이 실제 제스처 인디케이터와
// 겹쳐 보여 걷어냈고, 목업이 있던 자리에 그대로 넣어 하단 CTA·탭바가 제스처 영역에
// 닿지 않게 한다. 배경은 바깥 컨테이너가 칠한다 — 배경이 이어져야 하는 자리에서는
// modifier 로 넘긴다(예: NavigationBarSpacer(Modifier.background(MoyeotaColor.SurfaceCanvas))).
// dialog() 로 띄우는 화면에서는 인셋이 0 이라 자동으로 0dp 가 된다.
@Composable
fun NavigationBarSpacer(modifier: Modifier = Modifier) {
    Spacer(modifier = modifier.fillMaxWidth().windowInsetsBottomHeight(WindowInsets.navigationBars))
}

// 상단 앱바: 뒤로가기 + 타이틀 (+우측 액션)
@Composable
fun MoyeotaTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    Box(modifier = modifier.fillMaxWidth().height(56.dp)) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp)) {
                BackArrowIcon()
            }
        }
        Text(
            text = title,
            style = MoyeotaType.HeadingMd,
            modifier = Modifier.align(Alignment.Center),
        )
        Row(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            actions()
        }
    }
}

// 아이콘 라이브러리 없이 그리는 뒤로가기 화살표
@Composable
fun BackArrowIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(20.dp)) {
        val stroke = 2.dp.toPx()
        val cx = size.width
        val cy = size.height / 2f
        drawLine(MoyeotaColor.InkPrimary, Offset(cx * 0.15f, cy), Offset(cx * 0.9f, cy), stroke, StrokeCap.Round)
        drawLine(MoyeotaColor.InkPrimary, Offset(cx * 0.15f, cy), Offset(cx * 0.5f, cy * 0.35f), stroke, StrokeCap.Round)
        drawLine(MoyeotaColor.InkPrimary, Offset(cx * 0.15f, cy), Offset(cx * 0.5f, cy * 1.65f), stroke, StrokeCap.Round)
    }
}

enum class MoyeotaTab(val label: String) {
    HOME("홈"), EXPLORE("합승"), CHAT("채팅"), MYPAGE("마이")
}

// 하단탭 — 공통 규칙: 14(홈) · 17(합승) · 24(채팅) · 35(마이) 네 화면에서만 노출
//
// 배경(SurfaceCanvas)은 Column 전체에 칠해 내비게이션바 인셋 영역까지 이어지고,
// 아이콘·라벨은 인셋 위로만 올라온다 — 제스처 인디케이터와 겹치지 않게 하기 위함.
// (상단 StatusBarSpacer 와 같은 방식: 패딩 대신 같은 자리의 인셋 Spacer)
@Composable
fun MoyeotaBottomBar(
    selected: MoyeotaTab,
    onSelect: (MoyeotaTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().background(MoyeotaColor.SurfaceCanvas)) {
        HorizontalDivider(color = MoyeotaColor.Hairline)
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MoyeotaTab.entries.forEach { tab ->
                val color = if (tab == selected) MoyeotaColor.Primary500 else MoyeotaColor.TextMute
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.clickable { onSelect(tab) }.padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    TabIcon(tab = tab, tint = color)
                    Text(text = tab.label, style = MoyeotaType.CaptionSm, color = color)
                }
            }
        }
        NavigationBarSpacer()
    }
}

// ─── 하단탭 아이콘 ──────────────────────────────────────────────────────────
// material-icons 의존성 없이 24dp 그리드 위에 직접 그린 스트로크 아이콘.
// (같은 파일의 BackArrowIcon 과 동일한 Canvas 방식 — 선택/비선택은 tint 로만 구분)

private val TabIconSize = 24.dp

@Composable
private fun TabIcon(tab: MoyeotaTab, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(TabIconSize)) {
        val stroke = Stroke(
            width = 1.8.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        when (tab) {
            MoyeotaTab.HOME -> drawHomeIcon(tint, stroke)
            MoyeotaTab.EXPLORE -> drawTaxiIcon(tint, stroke)
            MoyeotaTab.CHAT -> drawChatIcon(tint, stroke)
            MoyeotaTab.MYPAGE -> drawPersonIcon(tint, stroke)
        }
    }
}

// 홈 — 집
private fun DrawScope.drawHomeIcon(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    val roof = Path().apply {
        moveTo(w * 0.08f, h * 0.47f)
        lineTo(w * 0.50f, h * 0.12f)
        lineTo(w * 0.92f, h * 0.47f)
    }
    drawPath(roof, tint, style = stroke)
    val body = Path().apply {
        moveTo(w * 0.20f, h * 0.38f)
        lineTo(w * 0.20f, h * 0.88f)
        lineTo(w * 0.80f, h * 0.88f)
        lineTo(w * 0.80f, h * 0.38f)
    }
    drawPath(body, tint, style = stroke)
    val door = Path().apply {
        moveTo(w * 0.41f, h * 0.88f)
        lineTo(w * 0.41f, h * 0.62f)
        lineTo(w * 0.59f, h * 0.62f)
        lineTo(w * 0.59f, h * 0.88f)
    }
    drawPath(door, tint, style = stroke)
}

// 합승 — 택시(지붕 표시등 있는 자동차)
private fun DrawScope.drawTaxiIcon(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    // 지붕 표시등 (작아서 스트로크 대신 채움)
    drawRoundRect(
        color = tint,
        topLeft = Offset(w * 0.40f, h * 0.14f),
        size = Size(w * 0.20f, h * 0.12f),
        cornerRadius = CornerRadius(w * 0.03f),
    )
    // 캐빈
    val cabin = Path().apply {
        moveTo(w * 0.24f, h * 0.48f)
        lineTo(w * 0.33f, h * 0.26f)
        lineTo(w * 0.67f, h * 0.26f)
        lineTo(w * 0.76f, h * 0.48f)
    }
    drawPath(cabin, tint, style = stroke)
    // 차체
    drawRoundRect(
        color = tint,
        topLeft = Offset(w * 0.08f, h * 0.46f),
        size = Size(w * 0.84f, h * 0.28f),
        cornerRadius = CornerRadius(w * 0.08f),
        style = stroke,
    )
    // 바퀴
    drawCircle(tint, radius = w * 0.075f, center = Offset(w * 0.29f, h * 0.80f), style = stroke)
    drawCircle(tint, radius = w * 0.075f, center = Offset(w * 0.71f, h * 0.80f), style = stroke)
}

// 채팅 — 꼬리 달린 말풍선 (모서리 라운드까지 한 붓으로)
private fun DrawScope.drawChatIcon(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    val l = w * 0.10f
    val t = h * 0.16f
    val r = w * 0.90f
    val b = h * 0.68f
    val rx = w * 0.16f
    val ry = h * 0.16f
    val bubble = Path().apply {
        moveTo(l + rx, t)
        lineTo(r - rx, t)
        arcTo(Rect(r - 2 * rx, t, r, t + 2 * ry), -90f, 90f, false)
        lineTo(r, b - ry)
        arcTo(Rect(r - 2 * rx, b - 2 * ry, r, b), 0f, 90f, false)
        lineTo(w * 0.46f, b)
        lineTo(w * 0.24f, h * 0.92f)   // 꼬리 끝
        lineTo(w * 0.34f, b)
        lineTo(l + rx, b)
        arcTo(Rect(l, b - 2 * ry, l + 2 * rx, b), 90f, 90f, false)
        lineTo(l, t + ry)
        arcTo(Rect(l, t, l + 2 * rx, t + 2 * ry), 180f, 90f, false)
        close()
    }
    drawPath(bubble, tint, style = stroke)
}

// 마이 — 사람
private fun DrawScope.drawPersonIcon(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    drawCircle(tint, radius = w * 0.17f, center = Offset(w * 0.5f, h * 0.32f), style = stroke)
    val shoulders = Path().apply {
        moveTo(w * 0.17f, h * 0.86f)
        cubicTo(w * 0.17f, h * 0.50f, w * 0.83f, h * 0.50f, w * 0.83f, h * 0.86f)
    }
    drawPath(shoulders, tint, style = stroke)
}
