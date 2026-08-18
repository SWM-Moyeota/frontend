package com.moyeota.presentation.feature.matching

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeota.core.designsystem.component.BackArrowIcon
import com.moyeota.core.designsystem.component.PrimaryCtaButton
import com.moyeota.core.designsystem.component.SheetHandle
import com.moyeota.core.designsystem.component.StatusBarMock
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RideStatus
import com.moyeota.domain.model.User

// 와이어프레임 그레이 (core token 미정의 색 — 화면 재현용)
private val CanvasBg = Color(0xFFF5F7FA)
private val GraySlate = Color(0xFF4B5563)
private val GrayMute = Color(0xFF8A93A0)
private val CardSoft = Color(0xFFF6F8FB)
private val GrayButtonBg = Color(0xFFEEF1F6)
private val DividerGray = Color(0xFFE7EAF0)
private val ProgressTrack = Color(0xFFE6EAF0)
private val RadarBg = Color(0xFFEEF2F8)
private val RadarRing = Color(0xFFD7DFEC)

private val waitingRideDummy = Ride(
    id = "ride-21",
    origin = "부산대학교 정문",
    destination = "서면역 1번 출구",
    departureLabel = "지금 출발",
    capacity = 3,
    members = listOf(User("me", "나", "부산대 인증", 4.8, 5)),
    farePerPerson = 3600,
    totalFare = 10800,
    status = RideStatus.RECRUITING,
)

/**
 * 21 · 매칭 대기 [S11]
 *
 * 이동(디스크립션):
 * - 「그만 찾기」 → 14 홈, 탐색 취소 (onCancelSearch — 뒤로가기도 동일 처리)
 * - 카드 탭 / 매칭 성사 → 22 탑승 상세 (onCardClick)
 * - 매칭 조건 「수정」 → 16 또는 15 [미연결] (onEditCondition)
 * - 탐색 반경 「수정」 → 16 [미연결] (onEditRadius)
 * - 「조건 넓혀 찾기」 → 반경·인원 완화 후 재탐색 [미연결] (onWidenSearch)
 */
@Composable
fun MatchWaitingScreen(
    ride: Ride = waitingRideDummy,
    foundCount: Int = 1,
    conditionLabel: String = "3인 · 동성만",
    radiusLabel: String = "1km",
    onCancelSearch: () -> Unit = {},
    onCardClick: () -> Unit = {},
    onEditCondition: () -> Unit = {}, // 미연결
    onEditRadius: () -> Unit = {},    // 미연결
    onWidenSearch: () -> Unit = {},   // 미연결
) {
    Column(modifier = Modifier.fillMaxSize().background(CanvasBg)) {
        StatusBarMock()

        // 헤더 — 뒤로가기는 탐색 취소와 동일 (진행 화면 이탈 = 14 홈)
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCancelSearch) { BackArrowIcon() }
            Text(
                text = "같이 탈 사람 찾는 중",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MoyeotaColor.InkPrimary,
            )
        }

        // 레이더 대기 애니메이션 (단순 재현)
        RadarSearchArea(modifier = Modifier.fillMaxWidth().height(220.dp))

        // 바텀시트
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .shadow(14.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), spotColor = Color(0x141B2A4A))
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(MoyeotaColor.SurfaceCanvas),
        ) {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                SheetHandle()
            }
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "보통 2분 안에 찾아요",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MoyeotaColor.InkPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "지금 같은 방향 ${foundCount}명을 찾았어요 · 목표 ${ride.capacity}명",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = GrayMute,
                )
                Spacer(Modifier.height(14.dp))

                // 진행 바
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(ProgressTrack, CircleShape),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(150f / 361f)
                            .height(6.dp)
                            .background(MoyeotaColor.Primary500, CircleShape),
                    )
                }
                Spacer(Modifier.height(16.dp))

                // 조건 카드 — 탭 시 22 탑승 상세
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(CardSoft)
                        .clickable { onCardClick() }
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                ) {
                    ConditionRow(label = "출발지", value = ride.origin)
                    HorizontalDivider(color = DividerGray)
                    ConditionRow(label = "도착지", value = ride.destination)
                    HorizontalDivider(color = DividerGray)
                    ConditionRow(label = "매칭 조건", value = conditionLabel, onEdit = onEditCondition)
                    HorizontalDivider(color = DividerGray)
                    ConditionRow(label = "탐색 반경", value = radiusLabel, onEdit = onEditRadius)
                }
            }

            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GrayActionButton(text = "그만 찾기", onClick = onCancelSearch, modifier = Modifier.width(112.dp))
                PrimaryCtaButton(text = "조건 넓혀 찾기", onClick = onWidenSearch, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
        }
        HomeIndicator()
    }
}

@Composable
private fun ConditionRow(label: String, value: String, onEdit: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MoyeotaColor.TextMute)
        Spacer(Modifier.weight(1f))
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MoyeotaColor.InkPrimary)
        if (onEdit != null) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MoyeotaColor.Primary500)
                    .clickable { onEdit() }
                    .padding(horizontal = 15.dp, vertical = 5.dp),
            ) {
                Text(text = "수정", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MoyeotaColor.TextOnDark)
            }
        }
    }
}

// 매칭 대기 레이더 — 동심원 + 확장 펄스 + 주변 사용자 점 (단순 재현)
@Composable
private fun RadarSearchArea(modifier: Modifier = Modifier) {
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
        // 내 위치
        drawCircle(MoyeotaColor.Primary500, 8.dp.toPx(), center)
        // 주변에서 찾은 사용자 점
        drawCircle(MoyeotaColor.RouteUserA.copy(alpha = 0.85f), 5.dp.toPx(), center + Offset(-66.dp.toPx(), -32.dp.toPx()))
        drawCircle(MoyeotaColor.RouteUserA.copy(alpha = 0.5f), 5.dp.toPx(), center + Offset(58.dp.toPx(), 42.dp.toPx()))
    }
}

// 와이어프레임의 회색 보조 버튼 (그만 찾기)
@Composable
private fun GrayActionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(GrayButtonBg)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = GraySlate)
    }
}

// 홈 인디케이터 (와이어프레임 하단 검은 바)
@Composable
private fun HomeIndicator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MoyeotaColor.SurfaceCanvas)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 135.dp, height = 5.dp)
                .background(MoyeotaColor.InkPrimary, CircleShape),
        )
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun MatchWaitingScreenPreview() {
    MatchWaitingScreen()
}
