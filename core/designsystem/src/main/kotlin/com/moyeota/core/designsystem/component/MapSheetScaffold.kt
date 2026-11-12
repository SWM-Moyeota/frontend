package com.moyeota.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moyeota.core.designsystem.theme.MoyeotaColor
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * [MapSheetScaffold] 가 슬롯에 넘겨 주는 값. 배경(지도)이 카메라를 맞추고 플로팅 UI 를
 * 시트 위에 띄우는 데 필요한 치수만 담는다.
 */
@Immutable
class MapSheetState internal constructor(
    /** 시트가 펼쳐져 있는가. 드래그 중에도 **정착 목표** 기준이라 값이 흔들리지 않는다 */
    val expanded: Boolean,
    /**
     * 시트가 **손잡이만 남기고 내려가 있는가**(`hideable` 시트만 — 14 홈). 이때 지도는 손잡이 줄 위를
     * 전부 쓴다. [expanded] 와 같이 **정착 목표** 기준이다.
     */
    val hidden: Boolean,
    val containerWidth: Dp,
    val containerHeight: Dp,
    /**
     * 앵커 기준 시트 높이. 드래그·정착 애니메이션 중에도 **목표값**이라 프레임마다 변하지 않는다.
     * 카메라 fit·contentPadding 은 반드시 이 값을 써야 한다 — 실측값([sheetHeight])을 쓰면
     * 애니메이션 매 프레임 카메라가 다시 잡혀 사용자의 팬·줌과 싸운다.
     */
    val settledSheetHeight: Dp,
    /**
     * 실측 시트 높이. 콘텐츠가 정하므로 [settledSheetHeight] 와 몇 dp 다를 수 있다.
     * 지도 위 플로팅 버튼을 시트 **바로 위**에 붙일 때만 쓴다.
     */
    val sheetHeight: Dp,
) {
    /** 시트에 가리지 않고 실제로 보이는 지도 높이(앵커 기준). [fitMapCamera] 의 heightDp 다. */
    val visibleMapHeight: Dp get() = (containerHeight - settledSheetHeight).coerceAtLeast(1.dp)
}

object MapSheetDefaults {
    /** 펼친 시트 위로 남겨두는 지도 높이 */
    val MapRevealHeight: Dp = 160.dp

    /**
     * 접은 시트의 대략적인 높이. **추정치**다 — 실제 높이는 콘텐츠가 정한다(에러 배너가 뜨면 커진다).
     * 드래그 앵커와 카메라 계산에만 쓰이므로 몇 dp 어긋나도 펼침 상태에서 남는 지도 높이가
     * 그만큼 달라질 뿐 동작에는 영향이 없다. 화면마다 접힘 콘텐츠가 다르니 각자 넘긴다.
     */
    val CollapsedSheetHeight: Dp = 176.dp
}

/** 이 속도(px/s) 이상으로 던지면 위치와 무관하게 그 방향의 앵커로 붙는다 */
private const val SheetFlingVelocity = 400f

/**
 * 지도(또는 임의의 배경) 위에 **드래그로 접었다 펴는 시트**를 얹는 공용 레이아웃.
 *
 * 앵커는 시트 전체의 오프셋이 아니라 **가운데 [sheetDetail] 영역의 높이**(0 ↔ detailMax)다.
 * material3 `BottomSheetScaffold` 처럼 시트를 통째로 밀어 내리면 보이는 건 콘텐츠의 *윗부분*이라
 * 하단에 고정된 CTA 가 화면 밖으로 나간다 — 이 앱의 두 화면(16 도착지 확인, 21 매칭 대기) 모두
 * 「같이 탈 사람 찾기」·「그만 찾기」가 접힘에서도 보여야 해서 그 구조를 쓸 수 없다.
 *
 * 시트 높이는 콘텐츠가 정하므로 어떤 것도 클리핑되지 않는다. 에러 배너가 떠서 푸터가 커지면
 * 시트가 그만큼 높아질 뿐이다.
 *
 * @param mapRevealHeight 펼침 상태에서 시트 위로 남겨 둘 배경 높이
 * @param collapsedSheetHeight 접힘 높이 추정치 ([MapSheetDefaults.CollapsedSheetHeight] 설명 참고)
 * @param background 시트 뒤를 가득 채우는 내용(지도). [BoxScope] 라 위에 칩·버튼을 align 으로 얹을 수 있다
 * @param sheetTop 핸들 아래에 붙는 **항상 보이는** 내용. 핸들과 함께 실제 드래그 타깃이 된다
 * @param sheetDetail 접히면 높이가 0 이 되는 상세 영역. 내부는 세로 스크롤된다
 * @param sheetFooter 시트 하단에 **항상 보이는** 내용(CTA·요금 문구 등)
 * @param hideable true 면 접힘보다 한 단계 더 내려 **손잡이 줄(28dp)만** 남길 수 있다 — 아래 「세 번째 앵커」
 * @param hiddenHandleLabel 손잡이만 남았을 때 그 줄을 누르는 동작의 스크린리더 이름(「검색 패널 펼치기」)
 *
 * ### 세 번째 앵커 — 손잡이만 남기기 ([hideable], 14 홈 · 2026-11 오너 UX 피드백)
 * 홈의 시트는 접어도 인사말·「어디로 갈까요?」·검색 바가 늘 떠 있어서 **지도를 다 볼 방법이 없었다.**
 * 그래서 접힘 아래에 앵커를 하나 더 둔다: 시트를 통째로 아래로 밀어 **손잡이 줄만** 하단탭 위에 남긴다.
 *
 * - 앵커 셋을 **한 줄의 위치**(`pos`)로 다룬다 — 0 = 손잡이만, `hiddenSpan` = 접힘, `hiddenSpan + detailMax`
 *   = 펼침. 드래그는 그 한 값을 움직이고, 상세 높이와 시트 내림 거리는 거기서 나눠 나온다. 그래서 펼침에서
 *   한 번에 끝까지 끌어내리면 상세가 먼저 접히고 이어서 시트가 내려간다(두 단계가 끊기지 않는다)
 * - `hiddenSpan` = (손잡이 + [sheetTop] + [sheetFooter]) 실측 높이 − 손잡이 줄. **상세 영역은 재지 않는다**
 *   — 애니메이션 중에 높이가 변하는 영역을 앵커 계산에 넣으면 앵커가 프레임마다 흔들린다
 * - 손잡이만 남았을 때 그 줄을 **누르면** 접힘(검색 바가 보이는 상태)으로 돌아온다 — 드래그를 못 하는
 *   사용자(스크린리더·스위치 접근)를 위한 길이다([hiddenHandleLabel])
 * - 지도 `contentPadding` 은 손잡이 줄 높이가 된다([MapSheetState.settledSheetHeight]) — 네이버 로고·축척이
 *   손잡이 바로 위에 보인다
 * - 정착은 기존과 같은 기본 스프링(감쇠 1 — 튀지 않음, 중간 강성 ≈ 250ms)이다
 *
 * [hideable] 이 false 면(16·21) 이 앵커가 없고 동작은 예전과 같다 — 그 두 화면은 접힘에서도 CTA 가
 * 보여야 해서 시트를 내릴 수 없다.
 */
@Composable
fun MapSheetScaffold(
    modifier: Modifier = Modifier,
    mapRevealHeight: Dp = MapSheetDefaults.MapRevealHeight,
    collapsedSheetHeight: Dp = MapSheetDefaults.CollapsedSheetHeight,
    initiallyExpanded: Boolean = true,
    hideable: Boolean = false,
    hiddenHandleLabel: String = "펼치기",
    background: @Composable BoxScope.(MapSheetState) -> Unit,
    sheetTop: @Composable ColumnScope.() -> Unit = {},
    sheetDetail: @Composable ColumnScope.() -> Unit = {},
    sheetFooter: @Composable ColumnScope.() -> Unit = {},
) {
    BoxWithConstraints(modifier = if (hideable) modifier.clipToBounds() else modifier) {
        val density = LocalDensity.current
        val containerWidth = maxWidth
        val containerHeight = maxHeight
        val scope = rememberCoroutineScope()

        val detailMaxDp = (containerHeight - mapRevealHeight - collapsedSheetHeight)
            .coerceAtLeast(0.dp)
        val detailMaxPx = with(density) { detailMaxDp.toPx() }
        val handleZonePx = with(density) { SheetHandleZoneHeight.toPx() }

        // 앵커 — 정착 목표. enum 이라 rememberSaveable 이 그대로 담는다(회전·탭 복귀에도 남는다).
        var anchor by rememberSaveable { mutableStateOf(if (initiallyExpanded) SheetAnchor.EXPANDED else SheetAnchor.COLLAPSED) }

        // 시트를 손잡이 줄만 남기고 내릴 거리 = (손잡이 + 위 + 아래) − 손잡이 줄. 상세는 재지 않는다(KDoc).
        var topChromePx by remember { mutableIntStateOf(0) }
        var footerPx by remember { mutableIntStateOf(0) }
        val hiddenSpanPx = if (hideable) (topChromePx + footerPx - handleZonePx).coerceAtLeast(0f) else 0f

        fun anchorPos(target: SheetAnchor): Float = when (target) {
            SheetAnchor.HIDDEN -> 0f
            SheetAnchor.COLLAPSED -> hiddenSpanPx
            SheetAnchor.EXPANDED -> hiddenSpanPx + detailMaxPx
        }

        // 첫 컴포지션에서 이미 containerHeight 를 알고 있어 목표 상태로 바로 그려진다(깜빡임 없음)
        val pos = remember { Animatable(anchorPos(anchor)) }
        // 회전 등으로 앵커가 바뀌면(또는 위·아래 높이가 처음 재어지면) 현재 앵커로 다시 앉힌다.
        // 정착 애니메이션 중이면 건드리지 않는다 — 끝나는 자리가 이미 새 앵커다.
        LaunchedEffect(detailMaxPx, hiddenSpanPx) {
            if (!pos.isRunning) pos.snapTo(anchorPos(anchor))
        }

        // 드래그 중에는 애니메이션을 건드리지 않고 이 값만 따라간다 — 손가락이 움직일 때마다
        // Animatable.snapTo 를 코루틴으로 쏘면 마지막 스냅이 놓아준 뒤의 정착 애니메이션을
        // 취소해(MutatorMutex) 시트가 어중간한 높이에 멈춘다.
        var dragPos by remember { mutableStateOf<Float?>(null) }
        val posPx = dragPos ?: pos.value
        // 한 줄의 위치에서 두 값을 나눠 낸다: 위쪽 구간은 상세 높이, 아래쪽 구간은 시트 내림 거리
        val detailPx = (posPx - hiddenSpanPx).coerceIn(0f, detailMaxPx)
        val hidePx = (hiddenSpanPx - posPx).coerceIn(0f, hiddenSpanPx)
        val detailHeight = with(density) { detailPx.toDp() }
        val maxPos = hiddenSpanPx + detailMaxPx
        val minPos = if (hideable) 0f else hiddenSpanPx

        val dragState = rememberDraggableState { delta ->
            // 아래로 끌면(delta > 0) 위치가 낮아진다 — 상세가 먼저 접히고, 이어서 시트가 내려간다
            dragPos = ((dragPos ?: pos.value) - delta).coerceIn(minPos, maxPos)
        }

        fun settleTo(target: SheetAnchor, from: Float) {
            anchor = target
            scope.launch {
                // 놓은 지점에서 이어서 정착시킨다(값을 먼저 옮기고 드래그 값을 비운다)
                pos.snapTo(from)
                dragPos = null
                pos.animateTo(anchorPos(target))
            }
        }

        var sheetHeightPx by remember { mutableIntStateOf(0) }
        val settledSheetHeight = when (anchor) {
            SheetAnchor.EXPANDED -> (containerHeight - mapRevealHeight).coerceAtLeast(collapsedSheetHeight)
            SheetAnchor.COLLAPSED -> collapsedSheetHeight
            SheetAnchor.HIDDEN -> SheetHandleZoneHeight
        }
        val state = MapSheetState(
            expanded = anchor == SheetAnchor.EXPANDED,
            hidden = anchor == SheetAnchor.HIDDEN,
            containerWidth = containerWidth,
            containerHeight = containerHeight,
            settledSheetHeight = settledSheetHeight,
            // 실제로 보이는 높이 — 내려간 만큼 뺀다(지도 위 플로팅 버튼이 시트 바로 위에 붙도록)
            sheetHeight = with(density) { (sheetHeightPx - hidePx).coerceAtLeast(0f).toDp() },
        )

        background(state)

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset { IntOffset(0, hidePx.roundToInt()) }
                .fillMaxWidth()
                .onSizeChanged { sheetHeightPx = it.height }
                .shadow(14.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), spotColor = SheetShadow)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(MoyeotaColor.SurfaceCanvas),
        ) {
            // 드래그 손잡이 — 핸들과 그 아래 항상 보이는 줄 전체가 실제 드래그 타깃이다
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { topChromePx = it.height }
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Vertical,
                        onDragStopped = { velocity ->
                            val from = dragPos ?: pos.value
                            val anchors = SheetAnchor.entries.filter { hideable || it != SheetAnchor.HIDDEN }
                            val target = when {
                                // 아래로 던짐 → 지금 위치보다 낮은 앵커 중 가장 가까운 것
                                velocity > SheetFlingVelocity ->
                                    anchors.lastOrNull { anchorPos(it) < from - 1f } ?: anchors.first()
                                velocity < -SheetFlingVelocity ->
                                    anchors.firstOrNull { anchorPos(it) > from + 1f } ?: anchors.last()
                                else -> anchors.minBy { abs(anchorPos(it) - from) } // 가까운 앵커로
                            }
                            settleTo(target, from)
                        },
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SheetHandleZoneHeight)
                        // 손잡이만 남았을 때 그 줄을 누르면 접힘(검색 바가 보이는 상태)으로 돌아온다 —
                        // 끌 수 없는 사용자를 위한 길. 그때 보이는 것이 이 줄뿐이라 누르는 자리도 이 줄이다
                        // (28dp — 터치 영역은 Compose 가 48dp 로 넓혀 잡는다). 다른 상태에서는 누르는 동작을
                        // 싣지 않는다(손잡이가 「누를 수 없는 버튼」으로 읽히지 않게).
                        .then(
                            if (anchor == SheetAnchor.HIDDEN) {
                                Modifier
                                    .clickable(onClickLabel = hiddenHandleLabel, role = Role.Button) {
                                        settleTo(SheetAnchor.COLLAPSED, pos.value)
                                    }
                                    .semantics { contentDescription = hiddenHandleLabel }
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    SheetHandle()
                }
                sheetTop()
            }

            // 접히면 높이가 0 이 되는 상세 영역
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(detailHeight)
                    .verticalScroll(rememberScrollState()),
            ) {
                sheetDetail()
            }

            Column(modifier = Modifier.fillMaxWidth().onSizeChanged { footerPx = it.height }) {
                sheetFooter()
            }
        }
    }
}

/** 시트의 앵커 셋. [HIDDEN] 은 `hideable` 시트에만 있다. */
private enum class SheetAnchor { HIDDEN, COLLAPSED, EXPANDED }

/** 손잡이 줄 높이 = 위 12dp + 손잡이 4dp + 아래 12dp. 손잡이만 남을 때 보이는 높이이기도 하다. */
private val SheetHandleZoneHeight: Dp = 28.dp

// 시트 그림자 — 16·21 이 함께 쓰던 값
private val SheetShadow = Color(0x141B2A4A)

/** 지도 위에 얹는 흰 알약 배지의 그림자 — 요약 칩·「위치 조정」 버튼이 함께 쓴다 */
val MapOverlayShadow = Color(0x1A1B2A4A)

/** [MapSheetScaffold] 시트 안쪽 좌우 여백 — 16·21 이 같은 값을 쓴다 */
val MapSheetContentPadding: Dp = 16.dp

/** 배경(지도) 위에 떠 있는 알약 형태의 흰 배지 — 요약 칩·상태 배지에 쓴다 */
@Composable
fun MapOverlayPill(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .height(30.dp)
            .shadow(3.dp, RoundedCornerShape(15.dp), spotColor = MapOverlayShadow)
            .clip(RoundedCornerShape(15.dp))
            .background(MoyeotaColor.SurfaceCanvas)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** 배경을 가득 채우는 자리표시자가 필요할 때 (지도 좌표가 없는 진입) */
@Composable
internal fun MapSheetBackgroundPlaceholder(modifier: Modifier = Modifier) {
    MapPlaceholder(modifier = modifier.fillMaxSize())
}
