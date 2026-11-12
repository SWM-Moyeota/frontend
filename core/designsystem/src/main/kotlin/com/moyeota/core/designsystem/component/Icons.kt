package com.moyeota.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moyeota.core.designsystem.theme.MoyeotaColor
import kotlin.math.cos
import kotlin.math.sin

/**
 * 앱 전체가 함께 쓰는 **그린 아이콘 한 벌** (디자인 QA G10, 2026-11).
 *
 * 이 앱은 아이콘 라이브러리 의존성을 두지 않는다(material-icons 를 넣지 않는 방침 — [BackArrowIcon]·하단탭과 같다).
 * 그래서 화면마다 `ChevronRightIcon`·`CheckIcon`·`SearchIcon` 을 따로 그렸고, 크기(16·18dp)와 선 굵기(1.8·2dp)가
 * 조금씩 달라 나란히 놓이면 "다른 세트"로 보였다. 또 `⌄ ⌃ ＋ › → ₩ 📍` 같은 **글자를 아이콘 대신** 쓴 곳이
 * 있었다 — 글꼴마다 모양이 달라지고 색·크기를 토큰으로 맞출 수 없다(no-emoji-icons).
 *
 * ### 규칙
 * - 모든 아이콘은 **24dp 격자**에 그리고 선 굵기는 [IconStrokeWidth] 1.75dp(디자인 문서). 20·16dp 로 줄이면
 *   선도 같은 비율로 준다 — 작은 아이콘만 굵어 보이지 않게
 * - 크기는 [MoyeotaIconSize] 셋만 쓴다
 * - 스크린리더 이름은 **감싸는 버튼**이 준다. 아이콘 자체는 장식이다
 *
 * iOS 는 같은 이름의 SF Symbol(chevron.right · checkmark · star · magnifyingglass · xmark · arrow.clockwise ·
 * arrow.up.right · ellipsis · bubble.left)을 `.font(.system(size:, weight: .medium))` 로 옮긴다.
 */
object MoyeotaIconSize {
    val Lg: Dp = 24.dp
    val Md: Dp = 20.dp
    val Sm: Dp = 16.dp
}

/** 24dp 기준 선 굵기 — 아이콘 크기에 비례해 준다 */
val IconStrokeWidth: Dp = 1.75.dp

enum class ChevronDirection { LEFT, RIGHT, UP, DOWN }

/** 꺾쇠 — 「다음 화면으로」(오른쪽)·「펼치기/접기」(위·아래) */
@Composable
fun ChevronIcon(
    modifier: Modifier = Modifier,
    direction: ChevronDirection = ChevronDirection.RIGHT,
    size: Dp = MoyeotaIconSize.Sm,
    tint: Color = MoyeotaColor.TextPlaceholder,
) {
    GridIcon(modifier, size) { u, stroke ->
        val points = when (direction) {
            ChevronDirection.RIGHT -> listOf(9f to 6f, 15f to 12f, 9f to 18f)
            ChevronDirection.LEFT -> listOf(15f to 6f, 9f to 12f, 15f to 18f)
            ChevronDirection.UP -> listOf(6f to 15f, 12f to 9f, 18f to 15f)
            ChevronDirection.DOWN -> listOf(6f to 9f, 12f to 15f, 18f to 9f)
        }
        drawPolyline(points, u, tint, stroke)
    }
}

/** 체크 표시 — 완료·확인된 사실 */
@Composable
fun CheckIcon(
    modifier: Modifier = Modifier,
    size: Dp = MoyeotaIconSize.Sm,
    tint: Color = MoyeotaColor.TextMute,
) {
    GridIcon(modifier, size) { u, stroke ->
        drawPolyline(listOf(5f to 12.5f, 10f to 17.5f, 19f to 7f), u, tint, stroke)
    }
}

/** 별 — 즐겨찾기. [filled] 면 채운다 */
@Composable
fun StarIcon(
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    size: Dp = MoyeotaIconSize.Md,
    tint: Color = MoyeotaColor.TextPlaceholder,
) {
    GridIcon(modifier, size) { u, stroke ->
        val path = Path()
        val cx = 12f * u
        val cy = 12.6f * u
        val outer = 9.5f * u
        val inner = 4.2f * u
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) outer else inner
            val angle = Math.toRadians(-90.0 + i * 36.0)
            val x = cx + r * cos(angle).toFloat()
            val y = cy + r * sin(angle).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, tint, style = if (filled) Fill else stroke)
        if (filled) drawPath(path, tint, style = stroke) // 채운 별도 외곽선을 둘러 모서리를 둥글게
    }
}

/** 돋보기 — 검색 */
@Composable
fun SearchIcon(
    modifier: Modifier = Modifier,
    size: Dp = MoyeotaIconSize.Lg,
    tint: Color = MoyeotaColor.TextBody,
) {
    GridIcon(modifier, size) { u, stroke ->
        drawCircle(tint, radius = 6.5f * u, center = Offset(11f * u, 11f * u), style = stroke)
        drawLine(tint, Offset(15.8f * u, 15.8f * u), Offset(20f * u, 20f * u), stroke.width, StrokeCap.Round)
    }
}

/** 엑스 — 닫기·지우기 */
@Composable
fun CloseIcon(
    modifier: Modifier = Modifier,
    size: Dp = MoyeotaIconSize.Lg,
    tint: Color = MoyeotaColor.TextBody,
) {
    GridIcon(modifier, size) { u, stroke ->
        drawLine(tint, Offset(6f * u, 6f * u), Offset(18f * u, 18f * u), stroke.width, StrokeCap.Round)
        drawLine(tint, Offset(18f * u, 6f * u), Offset(6f * u, 18f * u), stroke.width, StrokeCap.Round)
    }
}

/** 시계 방향 화살표 — 새로고침 */
@Composable
fun RefreshIcon(
    modifier: Modifier = Modifier,
    size: Dp = MoyeotaIconSize.Lg,
    tint: Color = MoyeotaColor.TextBody,
) {
    GridIcon(modifier, size) { u, stroke ->
        // 3시 → 아래 → 9시 → 12시로 도는 원호(270°), 이어서 오른쪽 위로 올라가 화살촉에 닿는다
        drawArc(
            color = tint,
            startAngle = 0f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(3f * u, 3f * u),
            size = Size(18f * u, 18f * u),
            style = stroke,
        )
        val tail = Path().apply {
            moveTo(12f * u, 3f * u)
            cubicTo(14.5f * u, 3f * u, 16.9f * u, 4f * u, 18.7f * u, 5.7f * u)
            lineTo(21f * u, 8f * u)
        }
        drawPath(tail, tint, style = stroke)
        drawPolyline(listOf(21f to 3f, 21f to 8f, 16f to 8f), u, tint, stroke)
    }
}

/** 오른쪽 위 화살표 — **앱 밖으로 나가는** 링크(약관·문의). 앱 안 이동은 [ChevronIcon] */
@Composable
fun ExternalLinkIcon(
    modifier: Modifier = Modifier,
    size: Dp = MoyeotaIconSize.Sm,
    tint: Color = MoyeotaColor.TextPlaceholder,
) {
    GridIcon(modifier, size) { u, stroke ->
        drawLine(tint, Offset(7f * u, 17f * u), Offset(17f * u, 7f * u), stroke.width, StrokeCap.Round)
        drawPolyline(listOf(8f to 7f, 17f to 7f, 17f to 16f), u, tint, stroke)
    }
}

/** 세로 점 셋 — 더보기 메뉴 */
@Composable
fun MoreVerticalIcon(
    modifier: Modifier = Modifier,
    size: Dp = MoyeotaIconSize.Lg,
    tint: Color = MoyeotaColor.TextBody,
) {
    GridIcon(modifier, size) { u, _ ->
        listOf(5f, 12f, 19f).forEach { y -> drawCircle(tint, radius = 1.6f * u, center = Offset(12f * u, y * u)) }
    }
}

/** 말풍선 — 채팅. 하단탭 채팅 아이콘과 같은 모양이다 */
@Composable
fun ChatBubbleIcon(
    modifier: Modifier = Modifier,
    size: Dp = MoyeotaIconSize.Lg,
    tint: Color = MoyeotaColor.TextBody,
) {
    Canvas(modifier = modifier.size(size)) {
        val u = this.size.width / 24f
        drawChatIcon(tint, iconStroke(u))
    }
}

// ─── 그리기 도구 ──────────────────────────────────────────────────────────────

/** 24dp 격자 단위 `u` 와 크기에 비례한 선을 넘겨 그린다 */
@Composable
private fun GridIcon(
    modifier: Modifier,
    size: Dp,
    draw: DrawScope.(u: Float, stroke: Stroke) -> Unit,
) {
    Canvas(modifier = modifier.size(size)) {
        val u = this.size.width / 24f
        draw(u, iconStroke(u))
    }
}

private fun DrawScope.iconStroke(u: Float): Stroke =
    Stroke(width = IconStrokeWidth.toPx() * (u * 24f / 24.dp.toPx()), cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun DrawScope.drawPolyline(points: List<Pair<Float, Float>>, u: Float, tint: Color, stroke: Stroke) {
    val path = Path()
    points.forEachIndexed { i, (x, y) -> if (i == 0) path.moveTo(x * u, y * u) else path.lineTo(x * u, y * u) }
    drawPath(path, tint, style = stroke)
}
