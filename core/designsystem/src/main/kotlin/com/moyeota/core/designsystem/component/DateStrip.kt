package com.moyeota.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.core.designsystem.theme.MoyeotaType

/**
 * 날짜 칸의 요일 색 — 달력의 관습(토 파랑 · 일 빨강)을 그대로 따른다.
 *
 * 색만으로 요일을 구분하지 않는다: 칸에는 늘 요일 **글자**가 함께 있다(색은 덧붙인 단서다).
 */
enum class DateStripTone {
    WEEKDAY,
    SATURDAY,
    SUNDAY,
}

/**
 * 날짜 칸 하나가 그릴 것 — **글자는 호출부가 다 만들어 넘긴다.**
 *
 * 이 모듈은 도메인과 `java.time` 을 모른다(디자인시스템은 디슈가링이 없는 minSdk 24 모듈이다).
 * 「오늘이 언제인가」·「한국 시간대인가」·「요일 글자」·「그 날에 고를 시각이 있는가」는 전부 호출부의
 * 순수 함수가 정한다(`presentation` 의 `reservationDateCells`) — 그래야 그 규칙을 JVM 테스트로 고정할 수 있다.
 *
 * @param weekday 위 줄 — 요일 한 글자(「토」)
 * @param day 아래 줄 — 일(「14」)
 * @param isToday 오늘이면 숫자 아래 작은 점을 찍는다(선택 여부와 무관하게)
 * @param enabled false 면 **누를 수 없고** 숫자가 흐려진다 — 그 날에 고를 수 있는 시각이 없을 때
 *   (늦은 밤의 「오늘」). 칸을 지우지 않는 이유: 8칸 달력에서 한 칸이 빠지면 요일 줄이 밀려 「오늘」을
 *   찾을 수 없다
 * @param contentDescription 스크린리더 문장 — 「오늘, 11월 12일 목요일」 같은 완결된 말
 */
@Immutable
data class DateStripCell(
    val weekday: String,
    val day: String,
    val tone: DateStripTone,
    val isToday: Boolean,
    val contentDescription: String,
    val enabled: Boolean = true,
)

object DateStripDefaults {
    /** 칸 높이 — 요일(12sp) · 4dp · 날짜 원(36dp) · 오늘 점. 48dp 터치 타깃을 세로로 넉넉히 넘는다. */
    val CellHeight: Dp = 60.dp

    /** 선택 표시 원의 지름 — 날짜 숫자를 감싼다. */
    val SelectionDiameter: Dp = 36.dp

    /**
     * 「한 줄에 8칸을 나눠 담는다」가 성립하는 칸 최소 폭. 360dp 폰의 가장 좁은 자리(16 카드 안 288dp)가
     * 정확히 36dp 다. 이보다 좁으면(320dp 기기) 칸을 [ScrollCellWidth] 로 고정하고 가로로 스크롤한다.
     */
    val MinEqualCellWidth: Dp = 36.dp

    /** 스크롤로 떨어질 때의 칸 폭. */
    val ScrollCellWidth: Dp = 44.dp
}

/**
 * **주간 달력 띠** — 위에 요일, 아래에 날짜 숫자가 있는 칸 8개(오늘~7일 뒤).
 *
 * 16 도착지 확인(예약 날짜)과 17 탐색 「날짜로 찾기」가 **같은 컴포넌트**를 쓴다 — 같은 8일 창을 두
 * 화면이 다른 모양으로 고르면 같은 날이 다르게 보인다. 그래서 두 화면이 함께 보는 이 모듈에 둔다
 * (`compose-feature` 스킬: 두 화면 이상이 쓰는 요소만 승격한다).
 *
 * ### 모양 (디자인 QA 4-3)
 * - **8칸을 같은 폭으로** 나눠 한 줄에 담는다 — 달력은 한눈에 일주일이 보여야 달력이다. 360dp 이상에서는
 *   스크롤하지 않는다. 그보다 좁으면([DateStripDefaults.MinEqualCellWidth] 미만) 가로 스크롤로 떨어지고,
 *   그때는 **선택 칸이 화면 밖이면 그 칸까지 스크롤**해 보여 준다
 * - 글자: 요일 `CaptionMd`, 숫자 `BodyStrong`, 기본 색 `TextBody`. **토요일 `Primary600` · 일요일
 *   `Danger600`** — 500 이 아니라 600 인 이유는 12sp 글자가 4.5:1 대비를 넘게 하려는 것이고, `Primary500`
 *   은 선택 색이라 겹치면 토요일이 골라진 것처럼 보인다
 * - 선택: 숫자를 감싸는 36dp `Primary500` 원 + 흰 숫자. 요일 글자는 제 색을 지킨다
 * - 오늘: 숫자 아래 4dp `TextMute` 점(선택돼도 같다)
 * - 고를 수 없는 날([DateStripCell.enabled] false): 숫자 `TextPlaceholder`, 누르지 못한다
 * - 칩·테두리·그림자는 없다 — 카드 안에 또 하나의 카드를 만들지 않는다
 *
 * ### 터치 타깃
 * 칸 사이에 틈을 두지 않고 **칸 전체(폭 × 60dp)를 누를 수 있게** 한다. 360dp 에서 칸 폭은 36~39dp 로
 * 48dp 권장보다 좁지만(8칸을 한 줄에 담는다는 결정의 대가다), 틈이 없어 띠 어디를 눌러도 가장 가까운
 * 날짜가 잡히고 세로는 60dp 다.
 *
 * ### 접근성
 * 띠 전체가 하나의 선택 묶음([selectableGroup])이고 칸은 [Role.Tab] 이다 — 「8개 중 하나를 고른다」가
 * 스크린리더에 그대로 전달된다. 칸 안의 글자는 따로 읽히지 않게 지우고 [DateStripCell.contentDescription]
 * 한 문장으로 읽힌다.
 *
 * @param contentPadding 띠 양 끝 여백. 같은 폭 모드에서는 그만큼 안쪽에서 8칸을 나누고, 스크롤 모드에서는
 *   스크롤 영역 **안쪽**에 둬서 첫 칸이 제목과 같은 선에서 시작하고 스크롤하면 가장자리까지 흐른다
 */
@Composable
fun WeekDateStrip(
    cells: List<DateStripCell>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    if (cells.isEmpty()) return
    val layoutDirection = LocalLayoutDirection.current
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val start = contentPadding.calculateStartPadding(layoutDirection)
        val end = contentPadding.calculateEndPadding(layoutDirection)
        val equalWidth = (maxWidth - start - end) / cells.size
        if (equalWidth >= DateStripDefaults.MinEqualCellWidth) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = start, end = end)
                    .selectableGroup(),
            ) {
                cells.forEachIndexed { index, cell ->
                    DateStripCellView(
                        cell = cell,
                        selected = index == selectedIndex,
                        onClick = { onSelect(index) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        } else {
            ScrollingDateStrip(cells, selectedIndex, onSelect, contentPadding)
        }
    }
}

/** 좁은 기기용 — 칸 폭을 고정하고 가로로 스크롤한다. 선택 칸이 완전히 보이지 않으면 데려온다. */
@Composable
private fun ScrollingDateStrip(
    cells: List<DateStripCell>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    contentPadding: PaddingValues,
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = selectedIndex.coerceIn(0, cells.lastIndex),
    )
    // 이미 보이는 칸을 누를 때는 움직이지 않는다 — 누른 칸이 손가락 아래에서 도망가면 안 된다
    LaunchedEffect(selectedIndex, cells.size) {
        if (selectedIndex !in cells.indices) return@LaunchedEffect
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == selectedIndex }
        val fullyVisible = item != null &&
            item.offset >= info.viewportStartOffset &&
            item.offset + item.size <= info.viewportEndOffset
        if (!fullyVisible) listState.animateScrollToItem(selectedIndex)
    }
    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.Start,
    ) {
        itemsIndexed(cells) { index, cell ->
            DateStripCellView(
                cell = cell,
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                modifier = Modifier.width(DateStripDefaults.ScrollCellWidth),
            )
        }
    }
}

@Composable
private fun DateStripCellView(
    cell: DateStripCell,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val toneColor = when (cell.tone) {
        DateStripTone.SATURDAY -> MoyeotaColor.Primary600
        DateStripTone.SUNDAY -> MoyeotaColor.Danger600
        DateStripTone.WEEKDAY -> MoyeotaColor.TextBody
    }
    val dayColor = when {
        selected -> MoyeotaColor.TextOnDark
        !cell.enabled -> MoyeotaColor.TextPlaceholder
        else -> toneColor
    }
    Box(
        modifier = modifier
            .height(DateStripDefaults.CellHeight)
            .selectable(selected = selected, enabled = cell.enabled, role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = cell.contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = cell.weekday,
                style = MoyeotaType.CaptionMd,
                color = if (cell.enabled) toneColor else MoyeotaColor.TextPlaceholder,
                modifier = Modifier.clearAndSetSemantics {},
            )
            Spacer(Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .size(DateStripDefaults.SelectionDiameter)
                    .background(if (selected) MoyeotaColor.Primary500 else Color.Transparent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = cell.day,
                    style = MoyeotaType.BodyStrong,
                    color = dayColor,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
            // 오늘 표시 — 다른 칸에도 같은 자리를 비워 둬서 오늘 칸만 위로 밀려 보이지 않게 한다
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(4.dp)
                    .background(if (cell.isToday) MoyeotaColor.TextMute else Color.Transparent, CircleShape),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun WeekDateStripPreview() {
    val weekdays = listOf("목", "금", "토", "일", "월", "화", "수", "목")
    val cells = weekdays.mapIndexed { index, weekday ->
        DateStripCell(
            weekday = weekday,
            day = (12 + index).toString(),
            tone = when (weekday) {
                "토" -> DateStripTone.SATURDAY
                "일" -> DateStripTone.SUNDAY
                else -> DateStripTone.WEEKDAY
            },
            isToday = index == 0,
            contentDescription = "11월 ${12 + index}일 ${weekday}요일",
            enabled = index != 0,
        )
    }
    Column {
        // 16·17 시트 모두 좌우 16dp 화면 여백 안에 놓인다(디자인 QA G3)
        WeekDateStrip(cells = cells, selectedIndex = 1, onSelect = {}, contentPadding = PaddingValues(horizontal = 16.dp))
        Spacer(Modifier.height(8.dp))
        WeekDateStrip(cells = cells, selectedIndex = 2, onSelect = {}, contentPadding = PaddingValues(horizontal = 16.dp))
    }
}
