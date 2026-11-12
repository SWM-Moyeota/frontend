package com.moyeota.presentation.feature.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeota.core.designsystem.component.SnapWheelPicker
import com.moyeota.core.designsystem.component.WeekDateStrip
import com.moyeota.core.designsystem.component.WheelPickerFrame
import com.moyeota.core.designsystem.theme.MoyeotaColor
import java.time.LocalDate
import java.time.LocalTime

/**
 * 예약 날짜·시각을 고르는 **두 조각** — 16 도착지 확인과 17 탐색 「날짜로 찾기」가 함께 쓴다.
 *
 * 모양(칸·휠)은 디자인시스템([WeekDateStrip]·[SnapWheelPicker])이 갖고, **무엇을 고를 수 있는가**는
 * 이 모듈의 순수 함수([reservationDateCells]·[reservationTimeRange]·[reservationHourOptions]·
 * [reservationMinuteOptions])가 정한다. 이 파일은 둘을 잇기만 한다 — 판정을 여기서 다시 세우지 않는다.
 *
 * 17 이 `feature/home` 에서 이 조각을 가져다 쓰는 것은 [headcountLabel]·[reservationDates] 와 같은
 * 선례다: 예약의 시간 계약은 방을 만드는 쪽(16)이 서버와 맺고, 검색은 그 그림자다(`ReservationRules.kt`).
 */

/**
 * 오늘~7일 뒤 **주간 달력 띠**. [selected] 가 목록에 없으면(자정을 넘긴 탐색 탭 등) 아무 칸도 칠하지 않는다.
 *
 * @param dates [reservationDates] 결과 그대로. 첫 원소가 오늘이다
 * @param contentPadding 띠 양 끝 여백 — 호출부 본문의 좌우 여백과 같게 줘야 첫 칸이 제목과 같은 선에서
 *   시작한다(띠는 스크롤되므로 여백을 바깥 `padding` 이 아니라 여기로 준다)
 */
@Composable
internal fun ReservationDateStrip(
    dates: List<LocalDate>,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    /** 그 날을 고를 수 있는가 — 16 은 「그 날에 남은 시각이 있는가」, 17 은 늘 true([reservationDateCells]) */
    isSelectable: (LocalDate) -> Boolean = { true },
) {
    val today = dates.firstOrNull() ?: return
    val cells = remember(dates, today, isSelectable) { reservationDateCells(dates, today, isSelectable) }
    WeekDateStrip(
        cells = cells,
        selectedIndex = dates.indexOf(selected),
        onSelect = { index -> dates.getOrNull(index)?.let(onSelect) },
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
    )
}

/**
 * 시·분 **휠 두 개**(시 0~23 · 분 00/10/…/50) — 그 날의 [range] 안 값만 담는다.
 *
 * - 시 휠은 [reservationHourOptions], 분 휠은 지금 시에 대한 [reservationMinuteOptions] 다.
 *   범위 밖 값은 **목록에 없다**(흐리게 남기지 않는다 — [SnapWheelPicker] KDoc)
 * - 시를 굴리면 분은 지키되 범위로 접는다([reservationTimeWithHour]) — 그래서 오늘의 첫 시로
 *   굴리면 분이 가장 이른 값으로 올라갈 수 있고, 분 휠이 그 칸으로 따라 움직인다
 * - [selected] 는 호출부가 [reservationSelectedTime] 으로 접어 둔 값이고, **그 값이 곧 서버에 가는
 *   값**이다(휠이 보여 주는 것과 보내는 것이 갈라지지 않게 같은 값 하나를 쓴다)
 *
 * 단위 글자 「시」·「분」은 휠 **오른쪽 고정 글자**다(가운데 줄 높이) — 휠 줄마다 붙이면 굴릴 때 단위까지
 * 흘러가 숫자를 읽기 어렵다. 스크린리더에는 휠마다 슬라이더 하나로 「출발 시, 18시」처럼 읽힌다.
 *
 * **24시간제 두 휠**이다(시 0~23 · 분). 오전/오후 휠을 따로 두지 않는다 — 오너가 준 참고(흔한 한국식
 * 시/분 휠)가 그 모양이고, 휠이 하나 늘면 같은 값을 고르는 데 손이 한 번 더 간다. 오전·오후는 CTA 위
 * 되읽기 줄(「오늘 오후 6:30 출발」)이 말해 준다.
 */
@Composable
internal fun ReservationTimeWheel(
    range: ReservationTimeRange,
    selected: LocalTime,
    onSelect: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hours = remember(range) { reservationHourOptions(range) }
    val minutes = remember(range, selected.hour) { reservationMinuteOptions(range, selected.hour) }
    WheelPickerFrame(modifier = modifier.fillMaxWidth()) {
        SnapWheelPicker(
            items = hours.map(::reservationHourLabel),
            selectedIndex = hours.indexOf(selected.hour).coerceAtLeast(0),
            onSelectedIndexChange = { index ->
                hours.getOrNull(index)?.let { onSelect(reservationTimeWithHour(selected, it, range)) }
            },
            label = "출발 시",
            itemDescription = { index -> "${hours[index]}시" },
            modifier = Modifier.weight(1f),
        )
        WheelUnit("시")
        Spacer(Modifier.width(8.dp))
        SnapWheelPicker(
            items = minutes.map(::reservationMinuteLabel),
            selectedIndex = minutes.indexOf(selected.minute).coerceAtLeast(0),
            onSelectedIndexChange = { index ->
                minutes.getOrNull(index)?.let { onSelect(reservationTimeWithMinute(selected, it, range)) }
            },
            label = "출발 분",
            itemDescription = { index -> "${minutes[index]}분" },
            modifier = Modifier.weight(1f),
        )
        WheelUnit("분")
    }
}

@Composable
private fun WheelUnit(text: String) {
    Text(
        text = text,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = MoyeotaColor.InkPrimary,
        // 휠 줄의 문장(「18시」)이 단위를 이미 말한다 — 이 글자가 따로 읽히면 「시」가 한 번 더 들린다
        modifier = Modifier.clearAndSetSemantics {},
    )
}
