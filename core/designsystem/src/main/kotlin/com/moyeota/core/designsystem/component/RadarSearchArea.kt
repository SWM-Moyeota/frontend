package com.moyeota.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.moyeota.core.designsystem.theme.MoyeotaColor

private val RadarBg = Color(0xFFEEF2F8)
private val RadarRing = Color(0xFFD7DFEC)

/**
 * 동심원 + 바깥으로 퍼지는 펄스 링. 「지금 주변에 요청을 보내고 있다」는 상태를 나타낸다.
 *
 * **21 매칭 대기가 아니라 25b 기사 찾는 중의 그림이다.** 21 에서는 배경이 실지도로 바뀌면서
 * 이 레이더를 걷어냈다 — 출발·도착과 경로라는 **진짜 정보**가 그 자리에 있는데 동심원이 그걸
 * 가리기만 했다. 「어느 범위에서 찾는지」는 21 에서 아무도 말할 수 없다: 매칭은 반경을 보지 않는다
 * (그래서 탐색 반경 원도 2026-10-14 에 지웠다). 반대로 25b 에는 보여 줄 지도가 없다(기사 위치는
 * 아직 없고 경로는 이미 확정돼 새 정보가 아니다). 기다림 자체가 화면의 내용인 단계라 제 몫을 한다.
 *
 * 25b 의 「주변 기사님에게 요청하고 있어요」는 참이다 — 서버 배차가 실제로 반경 안의 기사에게
 * 콜을 보낸다(`DispatchService`). 21 의 동승자 찾기와 달리 여기엔 범위 개념이 실재한다.
 *
 * 그림의 점들은 **데이터가 아니라 장식**이다. 실제 인원·기사 수를 뜻하지 않는다.
 */
@Composable
fun RadarSearchArea(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "radar")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1800, easing = LinearEasing)),
        label = "pulse",
    )
    Canvas(modifier = modifier.background(RadarBg)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        listOf(34.dp, 60.dp, 88.dp).forEach { r ->
            drawCircle(RadarRing, r.toPx(), center, style = Stroke(1.5.dp.toPx()))
        }
        // 확장 펄스 링
        drawCircle(
            color = MoyeotaColor.Primary500.copy(alpha = (1f - pulse) * 0.35f),
            radius = (34.dp.toPx() + (88.dp.toPx() - 34.dp.toPx()) * pulse),
            center = center,
            style = Stroke(2.dp.toPx()),
        )
        // 요청을 보내는 나
        drawCircle(MoyeotaColor.Primary500, 8.dp.toPx(), center)
        // 주변 — 장식용 점 (실제 기사 수가 아니다)
        drawCircle(MoyeotaColor.RouteUserA.copy(alpha = 0.85f), 5.dp.toPx(), center + Offset(-66.dp.toPx(), -32.dp.toPx()))
        drawCircle(MoyeotaColor.RouteUserA.copy(alpha = 0.5f), 5.dp.toPx(), center + Offset(58.dp.toPx(), 42.dp.toPx()))
    }
}
