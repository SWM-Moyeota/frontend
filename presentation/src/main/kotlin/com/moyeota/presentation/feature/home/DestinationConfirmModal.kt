package com.moyeota.presentation.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeota.core.designsystem.component.BackArrowIcon
import com.moyeota.core.designsystem.component.MapPlaceholder
import com.moyeota.core.designsystem.component.PrimaryCtaButton
import com.moyeota.core.designsystem.component.SheetHandle
import com.moyeota.core.designsystem.component.StatusBarMock
import com.moyeota.core.designsystem.component.StatusBadge
import com.moyeota.core.designsystem.component.NoticeKind
import com.moyeota.core.designsystem.theme.MoyeotaColor
import kotlin.math.roundToInt

private val ChipBlueBg = Color(0xFFF1F5FD)
private val GraySlate = Color(0xFF4B5563)
private val GrayDeep = Color(0xFF54637D)
private val GrayMute = Color(0xFF8A93A0)
private val GrayAsh = Color(0xFF9AA1AC)

/**
 * 16 · 도착지 확인 · 매칭 조건 (모달) [신규]
 *
 * 이동(디스크립션):
 * - 닫기·배경 탭 → 15 (onDismiss) — 공통 규칙: 모달은 배경 탭·닫기로만 종료
 * - 「같이 탈 사람 찾기」 → 21 매칭 대기 (onFindCompanions, 디스크립션상 미연결·연결 필요)
 *
 * 선택 규칙(디스크립션):
 * - 인원 1인/2인/3인 → 예상 1인 요금 재계산 (미터기 추정 ÷ 인원, 10원 단위 반올림)
 * - 출발지·도착지 반경 500m/1km/2km 각 1개 필수 선택, 기본값 3인 / 1km
 * - 매칭 방식 「주요 승차지점」 선택
 * - 「동성만」 토글 — 본인 인증 완료 계정만 사용 가능(미인증 시 비활성 + 안내)
 */
@Composable
fun DestinationConfirmModal(
    destinationName: String = "서면역 1번 출구",
    destinationAddress: String = "부산진구 부전동",
    originStopName: String = "부산대학교 정문 버스정류장",
    departureTimeLabel: String = "오후 6:45",
    arrivalTimeLabel: String = "오후 6:57 도착",
    walkLabel: String = "도보 2분 · 180m",
    routeSummaryLabel: String = "예상 12분 · 6.2km",
    estimatedTotalFare: Int = 7_200, // 미터기 추정 총액 (더미)
    sameGenderAvailable: Boolean = true, // 09 본인 인증 완료 여부
    onDismiss: () -> Unit = {},
    onFindCompanions: () -> Unit = {},
) {
    // 기본값: 3인 / 1km (디스크립션 유효값 규칙)
    var peopleCount by rememberSaveable { mutableIntStateOf(3) }
    var originRadius by rememberSaveable { mutableStateOf("1km") }
    var destinationRadius by rememberSaveable { mutableStateOf("1km") }
    var sameGenderOnly by rememberSaveable { mutableStateOf(false) }

    val farePerPerson = ((estimatedTotalFare.toDouble() / peopleCount) / 10.0).roundToInt() * 10
    val fareText = "%,d".format(farePerPerson)

    Box(modifier = Modifier.fillMaxSize()) {
        // Scrim 배경 — 탭 시 닫기
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MoyeotaColor.Scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onDismiss() },
        )

        // 모달 카드 (풀스크린 라우트 겸용) — 내부 탭은 소비
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {},
        ) {
            // 상단 헤더
            Column(modifier = Modifier.fillMaxWidth().background(MoyeotaColor.SurfaceCanvas)) {
                StatusBarMock()
                Row(
                    modifier = Modifier.fillMaxWidth().height(56.dp).padding(start = 12.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        BackArrowIcon()
                    }
                    Text(
                        text = "도착지 확인",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MoyeotaColor.InkPrimary,
                    )
                    Spacer(Modifier.weight(1f))
                    ShieldIcon()
                }
            }

            // 지도 영역 + 예상 시간 칩
            Box(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                MapPlaceholder(modifier = Modifier.fillMaxSize())
                Box(
                    modifier = Modifier
                        .padding(16.dp)
                        .height(30.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(MoyeotaColor.SurfaceCanvas)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = routeSummaryLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MoyeotaColor.InkPrimary,
                    )
                }
            }

            // 하단 시트
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .shadow(14.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), spotColor = Color(0x141B2A4A))
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(MoyeotaColor.SurfaceCanvas),
            ) {
                SheetHandle(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 12.dp),
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                ) {
                    Spacer(Modifier.height(16.dp))

                    // 도착지 배지 + 동성만 토글
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        StatusBadge(kind = NoticeKind.SUCCESS, text = "도착지")
                        Spacer(Modifier.size(10.dp))
                        Text(
                            text = "$destinationName · $destinationAddress",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = GrayMute,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                        SameGenderToggle(
                            selected = sameGenderOnly,
                            enabled = sameGenderAvailable,
                            onToggle = { if (sameGenderAvailable) sameGenderOnly = !sameGenderOnly },
                        )
                    }
                    if (!sameGenderAvailable) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "동성만 필터는 본인 인증 후 사용할 수 있어요",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = GrayAsh,
                            modifier = Modifier.align(Alignment.End),
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    // 경로 카드
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(ChipBlueBg)
                            .padding(16.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(11.dp).background(MoyeotaColor.Primary500, CircleShape))
                            Spacer(Modifier.size(13.dp))
                            Text(
                                text = originStopName,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MoyeotaColor.InkPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = departureTimeLabel,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MoyeotaColor.InkPrimary,
                            )
                        }
                        Row {
                            Box(modifier = Modifier.size(width = 11.dp, height = 30.dp), contentAlignment = Alignment.Center) {
                                DashedVerticalLine()
                            }
                            Spacer(Modifier.size(13.dp))
                            Text(
                                text = walkLabel,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = GrayMute,
                                modifier = Modifier.align(Alignment.CenterVertically),
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(11.dp).background(MoyeotaColor.MarkerDestination, CircleShape))
                            Spacer(Modifier.size(13.dp))
                            Text(
                                text = destinationName,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MoyeotaColor.InkPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = arrivalTimeLabel,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MoyeotaColor.InkPrimary,
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    Text(
                        text = "매칭 조건",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = GrayMute,
                    )
                    Spacer(Modifier.height(10.dp))

                    // 매칭 조건 카드
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(4.dp, RoundedCornerShape(18.dp), spotColor = Color(0x1A1B2A4A))
                            .clip(RoundedCornerShape(18.dp))
                            .background(MoyeotaColor.SurfaceCanvas)
                            .padding(horizontal = 20.dp),
                    ) {
                        ConditionRow(label = "인원") {
                            listOf(1, 2, 3).forEach { count ->
                                ConditionChip(
                                    text = "${count}인",
                                    selected = peopleCount == count,
                                    onClick = { peopleCount = count },
                                )
                            }
                        }
                        HorizontalDivider(color = MoyeotaColor.Hairline)
                        ConditionRow(label = "출발지 반경") {
                            listOf("500m", "1km", "2km").forEach { radius ->
                                ConditionChip(
                                    text = radius,
                                    selected = originRadius == radius,
                                    onClick = { originRadius = radius },
                                )
                            }
                        }
                        HorizontalDivider(color = MoyeotaColor.Hairline)
                        ConditionRow(label = "도착지 반경") {
                            listOf("500m", "1km", "2km").forEach { radius ->
                                ConditionChip(
                                    text = radius,
                                    selected = destinationRadius == radius,
                                    onClick = { destinationRadius = radius },
                                )
                            }
                        }
                        HorizontalDivider(color = MoyeotaColor.Hairline)
                        ConditionRow(label = "매칭 방식") {
                            ConditionChip(
                                text = "주요 승차지점",
                                selected = false,
                                onClick = {}, // 단일 방식 — 항상 「주요 승차지점」
                                wide = true,
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "설정한 출발지 · 도착지 반경 안의 탑승만 18 합승 리스트에 보여요",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = GrayAsh,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    Spacer(Modifier.height(16.dp))
                }

                // 요금 안내 + CTA
                Text(
                    text = "예상 요금 1인 ${fareText}원 · 인원이 확정되면 요금도 확정돼요",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = GrayAsh,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(12.dp))
                PrimaryCtaButton(
                    text = "같이 탈 사람 찾기",
                    onClick = onFindCompanions,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 135.dp, height = 5.dp)
                            .background(MoyeotaColor.InkPrimary, CircleShape),
                    )
                }
            }
        }
    }
}

@Composable
private fun ConditionRow(label: String, chips: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MoyeotaColor.InkPrimary,
        )
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            chips()
        }
    }
}

// 와이어프레임 스타일 칩 — 선택 시 Primary 솔리드, 미선택 시 흰색 + 그림자
@Composable
private fun ConditionChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    wide: Boolean = false,
) {
    val bg = if (selected) MoyeotaColor.Primary500 else MoyeotaColor.SurfaceCanvas
    val fg = if (selected) MoyeotaColor.TextOnDark else GraySlate
    Box(
        modifier = Modifier
            .height(32.dp)
            .shadow(if (selected) 0.dp else 3.dp, RoundedCornerShape(18.dp), spotColor = Color(0x1A1B2A4A))
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = if (wide) 28.dp else 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = fg,
        )
    }
}

// 동성만 토글 칩
@Composable
private fun SameGenderToggle(
    selected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val bg = if (selected) MoyeotaColor.Primary500 else ChipBlueBg
    val fg = if (selected) MoyeotaColor.TextOnDark else GrayDeep
    Box(
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.4f)
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(enabled = enabled) { onToggle() }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "동성만",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = fg,
        )
    }
}

@Composable
private fun DashedVerticalLine(modifier: Modifier = Modifier, color: Color = Color(0xFFB9C6DE)) {
    Canvas(modifier = modifier.size(width = 3.dp, height = 30.dp)) {
        drawLine(
            color = color,
            start = Offset(size.width / 2f, 0f),
            end = Offset(size.width / 2f, size.height),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
        )
    }
}

@Composable
private fun ShieldIcon(modifier: Modifier = Modifier, color: Color = GrayDeep) {
    Canvas(modifier = modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val strokeWidth = 1.6.dp.toPx()
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.08f)
            lineTo(w * 0.85f, h * 0.22f)
            quadraticTo(w * 0.85f, h * 0.62f, w * 0.5f, h * 0.92f)
            quadraticTo(w * 0.15f, h * 0.62f, w * 0.15f, h * 0.22f)
            close()
        }
        drawPath(path, color, style = Stroke(strokeWidth, join = StrokeJoin.Round))
        drawLine(color, Offset(w * 0.35f, h * 0.48f), Offset(w * 0.46f, h * 0.6f), strokeWidth, StrokeCap.Round)
        drawLine(color, Offset(w * 0.46f, h * 0.6f), Offset(w * 0.66f, h * 0.36f), strokeWidth, StrokeCap.Round)
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun DestinationConfirmModalPreview() {
    DestinationConfirmModal()
}
