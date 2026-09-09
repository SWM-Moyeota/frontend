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

/**
 * [MapSheetScaffold] 가 슬롯에 넘겨 주는 값. 배경(지도)이 카메라를 맞추고 플로팅 UI 를
 * 시트 위에 띄우는 데 필요한 치수만 담는다.
 */
@Immutable
class MapSheetState internal constructor(
    /** 시트가 펼쳐져 있는가. 드래그 중에도 **정착 목표** 기준이라 값이 흔들리지 않는다 */
    val expanded: Boolean,
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
 */
@Composable
fun MapSheetScaffold(
    modifier: Modifier = Modifier,
    mapRevealHeight: Dp = MapSheetDefaults.MapRevealHeight,
    collapsedSheetHeight: Dp = MapSheetDefaults.CollapsedSheetHeight,
    initiallyExpanded: Boolean = true,
    background: @Composable BoxScope.(MapSheetState) -> Unit,
    sheetTop: @Composable ColumnScope.() -> Unit = {},
    sheetDetail: @Composable ColumnScope.() -> Unit = {},
    sheetFooter: @Composable ColumnScope.() -> Unit = {},
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val containerWidth = maxWidth
        val containerHeight = maxHeight

        val detailMaxDp = (containerHeight - mapRevealHeight - collapsedSheetHeight)
            .coerceAtLeast(0.dp)
        val detailMaxPx = with(density) { detailMaxDp.toPx() }
        var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
        // 첫 컴포지션에서 이미 containerHeight 를 알고 있어 목표 상태로 바로 그려진다(깜빡임 없음)
        val detail = remember { Animatable(if (initiallyExpanded) detailMaxPx else 0f) }
        // 회전 등으로 앵커가 바뀌면 현재 상태의 앵커로 다시 앉힌다
        LaunchedEffect(detailMaxPx) { detail.snapTo(if (expanded) detailMaxPx else 0f) }

        // 드래그 중에는 애니메이션을 건드리지 않고 이 값만 따라간다 — 손가락이 움직일 때마다
        // Animatable.snapTo 를 코루틴으로 쏘면 마지막 스냅이 놓아준 뒤의 정착 애니메이션을
        // 취소해(MutatorMutex) 시트가 어중간한 높이에 멈춘다.
        var dragDetailPx by remember { mutableStateOf<Float?>(null) }
        val detailPx = dragDetailPx ?: detail.value
        val detailHeight = with(density) { detailPx.toDp() }

        val dragState = rememberDraggableState { delta ->
            // 아래로 끌면(delta > 0) 상세 영역이 줄어 시트가 낮아진다
            dragDetailPx = ((dragDetailPx ?: detail.value) - delta).coerceIn(0f, detailMaxPx)
        }

        var sheetHeightPx by remember { mutableIntStateOf(0) }
        val settledSheetHeight = if (expanded) {
            (containerHeight - mapRevealHeight).coerceAtLeast(collapsedSheetHeight)
        } else {
            collapsedSheetHeight
        }
        val state = MapSheetState(
            expanded = expanded,
            containerWidth = containerWidth,
            containerHeight = containerHeight,
            settledSheetHeight = settledSheetHeight,
            sheetHeight = with(density) { sheetHeightPx.toDp() },
        )

        background(state)

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
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
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Vertical,
                        onDragStopped = { velocity ->
                            val from = dragDetailPx ?: detail.value
                            val toExpanded = when {
                                velocity > SheetFlingVelocity -> false // 아래로 던짐 → 접기
                                velocity < -SheetFlingVelocity -> true
                                else -> from > detailMaxPx / 2f // 가까운 앵커로
                            }
                            expanded = toExpanded
                            // 놓은 지점에서 이어서 정착시킨다(값을 먼저 옮기고 드래그 값을 비운다)
                            detail.snapTo(from)
                            dragDetailPx = null
                            detail.animateTo(if (toExpanded) detailMaxPx else 0f)
                        },
                    ),
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
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

            sheetFooter()
        }
    }
}

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
