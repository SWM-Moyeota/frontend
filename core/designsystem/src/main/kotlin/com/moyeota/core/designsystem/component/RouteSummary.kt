package com.moyeota.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.core.designsystem.theme.MoyeotaType
import com.moyeota.core.designsystem.theme.tabularNums

/** 출발·도착 점 지름 — 예전 11dp 는 이름 글자보다 무거워 보였다 */
private val RouteDotSize = 8.dp

/** 점과 이름 사이 */
private val RouteDotGap = 12.dp

/**
 * 출발지 → 도착지 요약 — 16 도착지 확인, 20 합류 확인, 22 탑승 상세가 함께 쓴다 (디자인 QA G9, 2026-11).
 *
 * 예전엔 세 화면이 각자 그렸고 셋 다 **옅은 파란 판 위에 15sp Bold 네 줄**이었다: 이름과 시각이 같은 무게라
 * 무엇이 중요한지 읽히지 않았고, 흰 시트 안에 파란 판·회색 판이 겹겹이 쌓여 "AI 가 만든 카드 더미"로 보였다.
 *
 * - **판(배경)이 없다.** 흰 시트 위에 바로 놓인다
 * - 점 8dp([MoyeotaColor.MarkerOrigin]·[MoyeotaColor.MarkerDestination]) — 지도 표식과 같은 색
 * - 장소 이름 [MoyeotaType.BodyStrong] 한 줄 말줄임(역지오코딩 실주소가 들어와도 오른쪽 시각을 밀어내지 않는다)
 * - 오른쪽 시각·소요 시간 [MoyeotaType.BodyMd] [MoyeotaColor.TextBody], 숫자 폭 고정([tabularNums])
 * - 두 점 사이는 1.5dp [MoyeotaColor.BorderStrong] 점선. 줄 높이와 상관없이 **점 가운데에서 점 가운데까지**
 *   이어지도록 위치를 재어 그린다 — 「조정」 버튼(48dp 터치)이 있으면 줄이 높아져도 선이 끊기지 않는다
 * - [connectorNote](「내 위치에서 350m」)는 점선 옆 [MoyeotaType.CaptionMd] [MoyeotaColor.TextMute]
 *
 * @param originMeta 출발 줄 오른쪽(「오후 7:51」·「오늘 오후 9:30 출발」). null/빈 값이면 비운다
 * @param destinationMeta 도착 줄 오른쪽(「오후 8:16 도착」·「약 25분」)
 * @param onAdjustOrigin 있으면 출발 줄 끝에 「조정」(16 의 좌표 조정 진입점). 없으면 그리지 않는다
 */
@Composable
fun RouteSummary(
    origin: String,
    destination: String,
    modifier: Modifier = Modifier,
    originMeta: String? = null,
    destinationMeta: String? = null,
    connectorNote: String? = null,
    onAdjustOrigin: (() -> Unit)? = null,
    onAdjustDestination: (() -> Unit)? = null,
) {
    // 두 점의 세로 가운데 — 점선을 그 사이에 긋는다
    var originCenterY by remember { mutableFloatStateOf(0f) }
    var destinationCenterY by remember { mutableFloatStateOf(0f) }
    val connectorColor = MoyeotaColor.BorderStrong
    Column(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                if (destinationCenterY <= originCenterY) return@drawBehind
                val x = RouteDotSize.toPx() / 2f
                // 점 가장자리에서 4dp 떨어져 시작·끝난다 — 점에 붙으면 점이 꼬리를 단 것처럼 보인다
                val inset = RouteDotSize.toPx() / 2f + 4.dp.toPx()
                val dash = 3.dp.toPx()
                drawLine(
                    color = connectorColor,
                    start = Offset(x, originCenterY + inset),
                    end = Offset(x, destinationCenterY - inset),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)),
                )
            },
    ) {
        RouteEndRow(
            dotColor = MoyeotaColor.MarkerOrigin,
            name = origin,
            meta = originMeta,
            onAdjust = onAdjustOrigin,
            adjustLabel = "출발지 위치 조정",
            modifier = Modifier.onPlaced { originCenterY = it.positionInParent().y + it.size.height / 2f },
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 20.dp)
                .padding(start = RouteDotSize + RouteDotGap, top = 2.dp, bottom = 2.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (!connectorNote.isNullOrBlank()) {
                Text(
                    text = connectorNote,
                    style = MoyeotaType.CaptionMd,
                    color = MoyeotaColor.TextMute,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        RouteEndRow(
            dotColor = MoyeotaColor.MarkerDestination,
            name = destination,
            meta = destinationMeta,
            onAdjust = onAdjustDestination,
            adjustLabel = "도착지 위치 조정",
            modifier = Modifier.onPlaced { destinationCenterY = it.positionInParent().y + it.size.height / 2f },
        )
    }
}

@Composable
private fun RouteEndRow(
    dotColor: Color,
    name: String,
    meta: String?,
    onAdjust: (() -> Unit)?,
    adjustLabel: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(RouteDotSize).background(dotColor, CircleShape))
        Spacer(Modifier.width(RouteDotGap))
        Text(
            text = name,
            style = MoyeotaType.BodyStrong,
            color = MoyeotaColor.InkPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // 빈 문자열이면 Text 를 세우지 않는다 — 빈 글자도 폭을 먹어 이름 말줄임 위치가 달라진다
        if (!meta.isNullOrEmpty()) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = meta,
                style = MoyeotaType.BodyMd.tabularNums(),
                color = MoyeotaColor.TextBody,
                maxLines = 1,
            )
        }
        if (onAdjust != null) {
            // 파랑이 아니라 회색 — 이 시트에서 파랑은 CTA 하나에만 둔다(예전엔 한 시트에 파랑이 여섯 번 나왔다)
            TextLinkButton(
                text = "조정",
                onClick = onAdjust,
                color = MoyeotaColor.TextMute,
                style = MoyeotaType.BodySm.copy(fontWeight = FontWeight.SemiBold),
                onClickLabel = adjustLabel,
            )
        }
    }
}

/**
 * 요금 요약 — 20 합류 확인·22 탑승 상세 (디자인 QA G9).
 *
 * 예전엔 회색 판 안에 옅은 파란 판이 또 들어 있고 그 안에 19sp 파란 숫자가 있었다(판 속의 판). 이제는
 * 구분선 하나 아래 **두 줄**이다: 총액은 작게, 1인 부담은 [MoyeotaType.HeadingLg] 검정으로 크게 —
 * 사용자가 실제로 내는 돈이 이 화면에서 가장 큰 숫자다. 파랑은 쓰지 않는다(CTA 의 색이다).
 *
 * @param total 「24,000원」
 * @param perPerson 「8,000원」
 * @param capacity 1인 부담을 나눈 정원 — 아래 한 줄 설명에 들어간다
 * @param perPersonLabel 20 은 「합류 시 1인 부담」, 22 는 「1인 부담」
 */
@Composable
fun FareSummary(
    total: String,
    perPerson: String,
    capacity: Int,
    modifier: Modifier = Modifier,
    perPersonLabel: String = "1인 부담",
) {
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(color = MoyeotaColor.Hairline)
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "총 예상 요금",
                style = MoyeotaType.BodySm,
                color = MoyeotaColor.TextMute,
                modifier = Modifier.weight(1f),
            )
            Text(text = total, style = MoyeotaType.BodyMd.tabularNums(), color = MoyeotaColor.InkPrimary)
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = perPersonLabel,
                style = MoyeotaType.BodyStrong,
                color = MoyeotaColor.InkPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(text = perPerson, style = MoyeotaType.HeadingLg.tabularNums(), color = MoyeotaColor.InkPrimary)
        }
        Spacer(Modifier.height(4.dp))
        // 계산 근거 한 줄 — 체크 아이콘을 달지 않는다(확인된 "사실"이 아니라 계산 방식의 설명이다)
        Text(
            text = "총 예상 요금을 정원 ${capacity}명으로 나눈 금액이에요",
            style = MoyeotaType.CaptionMd,
            color = MoyeotaColor.TextMute,
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun RouteSummaryPreview() {
    Column(modifier = Modifier.padding(16.dp)) {
        RouteSummary(
            origin = "강남역 2번 출구",
            destination = "판교역 동편 버스정류장",
            originMeta = "오후 7:51",
            destinationMeta = "오후 8:16 도착",
            onAdjustOrigin = {},
            onAdjustDestination = {},
        )
        Spacer(Modifier.height(24.dp))
        RouteSummary(
            origin = "강남역",
            destination = "판교역",
            originMeta = "오늘 오후 9:30 출발",
            destinationMeta = "약 25분",
            connectorNote = "내 위치에서 350m",
        )
        Spacer(Modifier.height(16.dp))
        FareSummary(total = "24,000원", perPerson = "8,000원", capacity = 3, perPersonLabel = "합류 시 1인 부담")
    }
}
