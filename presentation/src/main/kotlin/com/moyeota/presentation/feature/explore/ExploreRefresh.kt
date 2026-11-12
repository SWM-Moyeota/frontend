package com.moyeota.presentation.feature.explore

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeota.core.designsystem.component.MapOverlayShadow
import com.moyeota.core.designsystem.theme.MoyeotaColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * 17 탐색 목록의 **주기 새로고침 + 카운트다운 버튼** — 판정과 숫자를 한 곳에 모은다.
 *
 * ### 4초 → 15초 (2026-11, 오너 UX 피드백)
 * 예전에는 화면이 보이는 동안 **4초마다** 같은 범위를 다시 읽었다. 목록이 4초마다 바뀌면 카드를 읽는
 * 도중 순서가 바뀌고 누르려던 카드가 손가락 아래에서 움직인다. 그리고 방이 차고 비는 주기는 분 단위라
 * 4초 간격은 대부분 같은 결과를 다시 받는 비용이었다. 15초는 「합류」를 누르기 전에 한 번은 갱신되는
 * 간격이면서(카드를 읽고 고르는 데 그 정도 걸린다) 읽는 동안 목록을 흔들지 않는 간격이다.
 *
 * 대신 **언제 갱신되는지 보이게** 한다 — 네이버 지도의 「주변 다시 검색」처럼 지도 오른쪽 아래에 남은
 * 초를 세는 원형 버튼([ExploreRefreshButton])을 두고, 누르면 그 자리에서 다시 읽고 15초를 새로 센다.
 * 주기가 길어진 만큼 「지금 보고 있는 게 최신인가」를 사용자가 직접 정할 수 있어야 한다.
 *
 * ### 어느 모드에서 도는가 — [exploreAutoRefreshes]
 * | 모드 | 주기 갱신·버튼 | 이유 |
 * |---|---|---|
 * | 실시간 | 있음 | 방이 **지금** 차고 빈다 — 낡은 목록이 곧 「사라진 방의 합류」다 |
 * | 곧 출발 | 있음 | 30분 안에 출발하는 방이라 실시간만큼 빨리 낡는다(곧 떠나 목록에서 빠진다) |
 * | 날짜로 찾기 | **없음** | 날짜 하나로 묻는 **한 번의 검색**이다. 며칠 뒤 출발 시각은 바뀌지 않고, 화면에 돌아오거나 날짜·도착지를 바꾸면 다시 읽는다 |
 *
 * ### 시계는 `delay` 하나다 — 테스트가 가상 시간으로 돌린다
 * 카운트다운은 [countDownToNextRefresh] 가 1초씩 `delay` 하며 남은 초를 알리는 것이 전부다. 벽시계를
 * 읽지 않으므로 `kotlinx-coroutines-test` 의 가상 시간(`advanceTimeBy`)이 곧 이 화면의 시계다
 * (`ExploreRefreshTest`). 화면이 가려지면 그 코루틴이 통째로 취소되고(가시성 게이트), 돌아오면 즉시
 * 1회 조회한 뒤 15부터 다시 센다 — 가려진 동안 줄어든 초를 이어 세지 않는다.
 */

/** 주기 갱신 간격 — **15초**. 근거는 이 파일 머리말. */
internal const val NEARBY_POLL_INTERVAL_MS = 15_000L

/** 카운트다운 한 칸 — 1초. 버튼의 숫자가 1씩 줄어드는 간격이다. */
internal const val REFRESH_TICK_MS = 1_000L

/** 버튼이 세기 시작하는 숫자(15). 간격을 칸으로 나눈 값이라 둘 중 하나만 바꿔도 따라온다. */
internal val REFRESH_COUNTDOWN_SECONDS: Int = ((NEARBY_POLL_INTERVAL_MS + REFRESH_TICK_MS - 1) / REFRESH_TICK_MS).toInt()

/**
 * 새로고침 버튼이 그릴 것.
 *
 * @param secondsLeft 다음 자동 갱신까지 남은 초(15 → 1). [refreshing] 이면 쓰이지 않는다
 * @param refreshing 조회가 나가 있다 — 버튼은 숫자 대신 도는 고리를 그리고 눌리지 않는다(연타가
 *   같은 조회를 겹쳐 보내지 않게)
 */
// [ExploreScreen] 의 공개 파라미터에 실려 나가므로 공개 타입이다(`ExploreMode` 와 같은 이유).
@Immutable
data class ExploreRefreshUi(
    val secondsLeft: Int = REFRESH_COUNTDOWN_SECONDS,
    val refreshing: Boolean = false,
)

/**
 * 이 모드가 **주기 갱신과 새로고침 버튼을 갖는가** — 위 표의 판정이다.
 *
 * `when` 으로 적어 [ExploreMode] 가 늘면 컴파일이 깨지게 한다(새 탭이 주기 갱신을 할지 그 자리에서 정한다).
 */
internal fun exploreAutoRefreshes(mode: ExploreMode): Boolean = when (mode) {
    ExploreMode.REALTIME, ExploreMode.SOON -> true
    ExploreMode.BY_DATE -> false
}

/**
 * 다음 자동 갱신까지 **1초씩 세며 기다린다** — [onTick] 으로 15, 14, …, 1 을 차례로 알리고, 1 을 알린 뒤
 * 1초가 더 지나면 돌아온다(합계 [intervalMs]). 돌아오면 호출부가 조회한다.
 *
 * 취소되면(화면이 가려짐·범위가 바뀜·사용자가 버튼을 누름) 그 자리에서 멈춘다 — 남은 초를 기억하지
 * 않는다. 새 패스는 늘 즉시 조회 후 처음부터 센다.
 */
internal suspend fun countDownToNextRefresh(
    onTick: (secondsLeft: Int) -> Unit,
    intervalMs: Long = NEARBY_POLL_INTERVAL_MS,
    tickMs: Long = REFRESH_TICK_MS,
) {
    var remainingMs = intervalMs
    while (remainingMs > 0) {
        onTick(((remainingMs + tickMs - 1) / tickMs).toInt())
        val step = minOf(tickMs, remainingMs)
        delay(step)
        remainingMs -= step
    }
}

/** 새로고침 버튼의 스크린리더 이름. */
internal const val REFRESH_BUTTON_LABEL = "목록 새로고침"

/**
 * 새로고침 버튼의 스크린리더 상태 — 「15초마다 자동」 / 「새로고침 중」.
 *
 * **남은 초를 읽어 주지 않는다.** 숫자는 1초마다 바뀌는데, 스크린리더는 초점이 놓인 요소의 상태가 바뀔
 * 때마다 그것을 읽는다 — 「14초 후 자동」, 「13초 후 자동」이 쉬지 않고 들린다. 그래서 상태에는 **주기**를
 * 적는다(바뀌지 않는다). 조회 중일 때만 「새로고침 중」으로 바뀌고 그때 버튼은 눌리지 않는다.
 */
internal fun refreshButtonStateDescription(ui: ExploreRefreshUi): String =
    if (ui.refreshing) "새로고침 중" else "${REFRESH_COUNTDOWN_SECONDS}초마다 자동"

/** 버튼 지름 — 48dp 터치 타깃과 같다. */
internal val RefreshButtonSize = 48.dp

/**
 * 버튼의 오른쪽 여백 — **네이버 지도 줌 컨트롤과 같은 세로선**에 세운다.
 *
 * SDK 의 줌 컨트롤은 폭 46dp · 오른쪽 여백 10dp 다(`navermap_map_controls_view.xml` ·
 * `navermap_zoom_control_view.xml`, SDK 3.23.3). 그 중심선은 오른쪽에서 33dp 이고, 48dp 버튼의 중심을
 * 거기 맞추면 여백은 33 − 24 = 9dp 다.
 */
internal val RefreshButtonEndInset = 9.dp

/**
 * 지도 오른쪽 아래의 **카운트다운 새로고침 버튼** — 오너가 준 네이버 지도 참고 그대로: 짙은 원에
 * 원형 화살표 고리, 가운데에 남은 초.
 *
 * - 누르면 즉시 다시 읽고 15부터 다시 센다([onClick]). 조회가 나가 있는 동안은 숫자 대신 **고리가 돈다**
 *   (눌리지 않는다)
 * - **움직임 줄이기**(개발자 옵션·접근성의 「애니메이션 삭제」 = 애니메이터 배율 0)가 켜져 있으면 돌리지
 *   않고 고리를 흐리게만 한다 — 도는 표시는 장식이고, 상태는 「새로고침 중」으로 이미 읽힌다
 * - 색: 짙은 원(`InkPrimary`)에 흰 글자 — 지도 타일 위 어디서나 대비가 선다. 그림자는 지도 위 카드와
 *   같은 단계(`MapOverlayShadow`)다
 */
@Composable
internal fun ExploreRefreshButton(
    ui: ExploreRefreshUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReduceMotion()
    val state = refreshButtonStateDescription(ui)
    Box(
        modifier = modifier
            .size(RefreshButtonSize)
            .shadow(4.dp, CircleShape, spotColor = MapOverlayShadow)
            .clip(CircleShape)
            .background(MoyeotaColor.InkPrimary)
            // 역할·누르기·비활성은 clickable 이 의미 트리에 싣는다. 이름과 상태만 더한다 —
            // 가운데 숫자는 아래에서 의미 트리에서 지운다(1초마다 바뀌는 글자가 이름에 섞이지 않게)
            .clickable(
                enabled = !ui.refreshing,
                onClickLabel = "지금 새로고침",
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                contentDescription = REFRESH_BUTTON_LABEL
                stateDescription = state
            },
        contentAlignment = Alignment.Center,
    ) {
        val spinning = ui.refreshing && !reduceMotion
        val rotation = if (spinning) {
            val transition = rememberInfiniteTransition(label = "refresh-spin")
            val angle by transition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing), RepeatMode.Restart),
                label = "refresh-angle",
            )
            angle
        } else {
            0f
        }
        CircularArrowRing(
            color = if (ui.refreshing && reduceMotion) Color.White.copy(alpha = 0.5f) else Color.White,
            modifier = Modifier.fillMaxSize().padding(7.dp).rotate(rotation),
        )
        if (!ui.refreshing) {
            Text(
                text = ui.secondsLeft.toString(),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

/**
 * 원형 화살표 고리 — 12시 방향에서 시계 방향으로 300° 를 돌고 끝에 작은 화살촉. 아이콘 자산이 없어
 * (이 앱의 다른 아이콘처럼) 직접 그린다.
 */
@Composable
private fun CircularArrowRing(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 2.dp.toPx()
        val inset = stroke / 2 + 1.dp.toPx()
        val diameter = size.minDimension - inset * 2
        val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
        val startAngle = -90f + 30f
        val sweep = 300f
        drawArc(
            color = color,
            startAngle = startAngle,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = topLeft,
            size = Size(diameter, diameter),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        // 화살촉 — 호의 끝점에서 진행 방향(접선)으로 작은 삼각형
        val radius = diameter / 2
        val center = Offset(size.width / 2, size.height / 2)
        val endRad = (startAngle + sweep) * PI.toFloat() / 180f
        val tip = Offset(center.x + radius * cos(endRad), center.y + radius * sin(endRad))
        val tangent = Offset(-sin(endRad), cos(endRad)) // 시계 방향 진행
        val normal = Offset(cos(endRad), sin(endRad))
        val head = 4.dp.toPx()
        val path = Path().apply {
            moveTo(tip.x + tangent.x * head, tip.y + tangent.y * head)
            lineTo(tip.x - normal.x * head, tip.y - normal.y * head)
            lineTo(tip.x + normal.x * head, tip.y + normal.y * head)
            close()
        }
        drawPath(path, color)
    }
}

/**
 * 시스템이 **애니메이션을 끄라고** 했는가 — 애니메이터 배율(`ANIMATOR_DURATION_SCALE`)이 0 이면 true.
 *
 * Android 에 「움직임 줄이기」 전용 설정은 없고, 접근성의 「애니메이션 삭제」가 이 배율을 0 으로 만든다.
 * 화면이 떠 있는 동안 설정을 바꾸는 일은 드물어 진입 때 한 번 읽는다.
 */
@Composable
private fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        runCatching { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }
            .getOrDefault(1f) == 0f
    }
}
