package com.moyeota.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.core.designsystem.theme.MoyeotaType
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

object WheelPickerDefaults {
    /** 한 줄 높이. 5줄이면 200dp — 16 시트의 스크롤 영역 안에서 한 화면에 들어오는 높이다. */
    val ItemHeight: Dp = 40.dp

    /**
     * 보이는 줄 수(홀수). 가운데 한 줄이 선택이고 위아래 두 줄씩이 「앞뒤에 무엇이 있는지」를 보여 준다
     * — 한국 앱의 흔한 시/분 휠 모양이다. 짝수면 가운데가 없으므로 홀수만 쓴다.
     */
    const val VisibleItemCount: Int = 5
}

/**
 * 휠 피커의 틀 — **가운데 줄 뒤에 강조 띠**를 깔고, 그 위에 휠([SnapWheelPicker])과 단위 글자(「시」·「분」)를
 * 한 줄로 얹는다.
 *
 * 띠를 휠마다 따로 그리지 않고 틀이 한 번 그리는 이유: 시·분 두 휠과 단위 글자가 **한 줄의 값**
 * (「18 시 30 분」)으로 읽혀야 하는데, 휠마다 띠가 있으면 칸이 끊겨 두 개의 따로 노는 선택처럼 보인다.
 * 색은 `SurfaceSoft` 채움 + 8dp 모서리 — **파랑이 아니다**(디자인 QA 4-4). 이 시트의 파랑은 CTA 하나에
 * 아껴 둔다(DESIGN-moyeota.md 「한 화면에 primary 는 드물게」).
 */
@Composable
fun WheelPickerFrame(
    modifier: Modifier = Modifier,
    itemHeight: Dp = WheelPickerDefaults.ItemHeight,
    visibleItemCount: Int = WheelPickerDefaults.VisibleItemCount,
    content: @Composable RowScope.() -> Unit,
) {
    Box(
        modifier = modifier.height(itemHeight * visibleItemCount),
        contentAlignment = Alignment.Center,
    ) {
        HighlightBand(itemHeight)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun BoxScope.HighlightBand(itemHeight: Dp) {
    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .fillMaxWidth()
            .height(itemHeight)
            .background(MoyeotaColor.SurfaceSoft, RoundedCornerShape(8.dp)),
    )
}

/**
 * **손가락을 떼면 한 칸에 딱 붙는** 세로 휠 — Compose 에는 기본 휠 피커가 없어 `LazyColumn` +
 * 스냅 플링([rememberSnapFlingBehavior])으로 만든다.
 *
 * ### 동작
 * - 위아래로 [WheelPickerDefaults.VisibleItemCount] / 2 줄만큼 콘텐츠 여백을 둬서 **첫 항목과 끝 항목도
 *   가운데 줄에 올 수 있다**. 그래서 「가운데에 있는 항목」 = 선택이다
 * - 스크롤이 **멈췄을 때만** [onSelectedIndexChange] 를 부른다 — 굴리는 도중의 값마다 부르면 부모가
 *   그 값으로 범위를 다시 접고(시가 바뀌면 분 목록이 바뀐다) 다시 그리느라 휠이 손가락과 싸운다
 * - 부모가 [selectedIndex] 를 바꾸면(날짜를 바꿔 범위로 접혔을 때 등) 휠이 그 칸으로 **옮겨 간다**.
 *   굴리는 중에는 옮기지 않는다(손가락이 이긴다)
 * - 항목을 탭하면 그 칸으로 굴러간다
 * - 한 칸 넘어갈 때마다 가벼운 햅틱 — 눈을 떼고 굴려도 몇 칸 움직였는지 손이 안다
 * - **세로 드래그를 끝까지 삼킨다**([ConsumeRemainingScroll]) — 휠 끝에 닿은 손가락이 바깥(16 시트의
 *   세로 스크롤)을 끌고 가면 휠을 굴리려던 사람의 화면이 통째로 움직인다
 *
 * ### 접근성 — 슬라이더처럼
 * 스크린리더 사용자는 휠을 굴릴 수 없다. 그래서 휠 전체를 **하나의 조절 가능한 값**(슬라이더 의미:
 * [progressBarRangeInfo] + [setProgress])으로 내보내고, 줄 하나하나는 의미 트리에서 지운다 — TalkBack
 * 에서 위아래 스와이프로 한 칸씩 바뀌고 「출발 시, 18시」처럼 읽힌다([label] + [itemDescription]).
 *
 * ### 빠지는 값 vs 흐린 값
 * 이 휠은 **받은 항목만** 그린다 — 고를 수 없는 값은 호출부가 목록에서 뺀다(예약의 「지난 시각」).
 * 흐리게 남겨 두면 손가락이 거기서 멈췄을 때 「되돌아가는」 동작이 필요하고, 그 사이 화면은 고를 수
 * 없는 값을 보여 준다.
 *
 * @param items 각 줄의 글자. 비어 있으면 아무것도 그리지 않는다
 * @param label 이 휠이 무엇을 고르는지(「출발 시」) — 스크린리더가 값 앞에 읽는다
 * @param itemDescription 스크린리더가 읽을 각 줄의 문장(「18시」). 단위가 따로 그려지는 휠이라
 *   숫자만 읽히면 무엇의 18 인지 모른다
 */
@Composable
fun SnapWheelPicker(
    items: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    itemHeight: Dp = WheelPickerDefaults.ItemHeight,
    visibleItemCount: Int = WheelPickerDefaults.VisibleItemCount,
    itemDescription: (Int) -> String = { items[it] },
) {
    if (items.isEmpty()) return
    val safeSelected = selectedIndex.coerceIn(0, items.lastIndex)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = safeSelected)
    val scope = rememberCoroutineScope()
    val centered by remember(listState) { derivedStateOf { listState.centeredItemIndex() } }
    val currentOnChange by rememberUpdatedState(onSelectedIndexChange)
    val currentSelected by rememberUpdatedState(safeSelected)
    val currentLastIndex by rememberUpdatedState(items.lastIndex)
    val haptic = LocalHapticFeedback.current

    // 굴리는 동안 가운데 칸이 바뀔 때마다 한 번 — 부모가 옮기는 스크롤(범위로 접힘)에는 울리지 않는다
    LaunchedEffect(listState) {
        snapshotFlow { listState.centeredItemIndex() }
            .distinctUntilChanged()
            .drop(1)
            .collect { if (listState.isScrollInProgress) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    }

    // 손가락을 떼고 스냅까지 끝났을 때 한 번 — 그때 가운데에 있는 칸이 새 선택이다
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .filter { scrolling -> !scrolling }
            .collect {
                val index = listState.centeredItemIndex().coerceIn(0, currentLastIndex)
                if (index != currentSelected) currentOnChange(index)
            }
    }
    // 부모가 정한 선택으로 맞춘다(범위가 바뀌어 접힌 경우 등). 굴리는 중이면 손가락을 존중한다.
    LaunchedEffect(safeSelected, items.size) {
        if (!listState.isScrollInProgress && listState.centeredItemIndex() != safeSelected) {
            listState.scrollToItem(safeSelected)
        }
    }

    Box(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = label
            stateDescription = itemDescription(safeSelected)
            progressBarRangeInfo = ProgressBarRangeInfo(
                current = safeSelected.toFloat(),
                range = 0f..items.lastIndex.toFloat().coerceAtLeast(0f),
                steps = (items.size - 2).coerceAtLeast(0),
            )
            setProgress { target ->
                val index = target.roundToInt().coerceIn(0, currentLastIndex)
                if (index != currentSelected) currentOnChange(index)
                true
            }
        },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .nestedScroll(ConsumeRemainingScroll)
                .fillMaxWidth()
                .height(itemHeight * visibleItemCount),
            flingBehavior = rememberSnapFlingBehavior(lazyListState = listState, snapPosition = SnapPosition.Center),
            contentPadding = PaddingValues(vertical = itemHeight * (visibleItemCount / 2)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(items.size) { index ->
                val distance = abs(index - centered)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .clickable { scope.launch { listState.animateScrollToItem(index) } }
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = items[index],
                        // 가운데(선택) 줄만 크고 진하게, 멀어질수록 흐리게(0.6 → 0.3) — 휠의 「원통」 느낌을
                        // 그림자·그라데이션 없이(DESIGN: 크롬에 그라데이션 금지) 글자 크기와 투명도만으로 낸다
                        style = if (distance == 0) MoyeotaType.HeadingLg else MoyeotaType.BodyMd,
                        color = when (distance) {
                            0 -> MoyeotaColor.InkPrimary
                            1 -> MoyeotaColor.TextMute.copy(alpha = 0.6f)
                            else -> MoyeotaColor.TextMute.copy(alpha = 0.3f)
                        },
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * 휠이 다 쓰지 못한 세로 스크롤·플링을 **바깥으로 넘기지 않는다** — 휠 위에서 시작한 손가락은 휠만
 * 움직인다. 16 의 휠은 시트의 세로 스크롤 영역 안에 있어서, 이게 없으면 휠 끝(23시·50분)에 닿은 뒤의
 * 남은 드래그가 시트를 끌어 올린다.
 */
private val ConsumeRemainingScroll = object : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        Offset(x = 0f, y = available.y)

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
        Velocity(x = 0f, y = available.y)
}

/**
 * 뷰포트 한가운데에 가장 가까운 항목. 콘텐츠 여백 때문에 [LazyListState.firstVisibleItemIndex] 는
 * 선택이 아니다 — 위쪽 여백 줄이 보이는 동안에도 그 값은 0 이다.
 *
 * `visibleItemsInfo` 의 offset 과 `viewportStartOffset`(여백만큼 음수)은 같은 좌표계라 둘의 가운데를
 * 그대로 비교하면 된다.
 */
private fun LazyListState.centeredItemIndex(): Int {
    val info = layoutInfo
    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
    return info.visibleItemsInfo
        .minByOrNull { abs(it.offset + it.size / 2 - center) }
        ?.index
        ?: firstVisibleItemIndex
}

@Preview(showBackground = true, widthDp = 288)
@Composable
private fun SnapWheelPickerPreview() {
    WheelPickerFrame {
        SnapWheelPicker(
            items = (0..23).map { it.toString() },
            selectedIndex = 18,
            onSelectedIndexChange = {},
            label = "출발 시",
            modifier = Modifier.weight(1f),
        )
        Text(text = "시", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MoyeotaColor.InkPrimary)
        SnapWheelPicker(
            items = listOf("00", "10", "20", "30", "40", "50"),
            selectedIndex = 3,
            onSelectedIndexChange = {},
            label = "출발 분",
            modifier = Modifier.weight(1f),
        )
        Text(text = "분", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MoyeotaColor.InkPrimary)
    }
}
