package com.moyeota.presentation.feature.mypage

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeota.core.designsystem.component.MoyeotaBottomBar
import com.moyeota.core.designsystem.component.MoyeotaTab
import com.moyeota.core.designsystem.component.StatusBarSpacer
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RideStatus
import com.moyeota.domain.model.User
import com.moyeota.presentation.core.activeStageLabel

// 와이어프레임 그레이 (core token 미정의 색 — 화면 재현용)
private val CanvasBg = Color(0xFFF5F7FA)
private val GraySlate = Color(0xFF4B5563)
private val GrayMute = Color(0xFF8A93A0)
private val GrayAsh = Color(0xFF9AA1AC)
private val CardShadow = Color(0x0F1B2A4A)

// 화면 34 **Preview 전용** 더미. 실제 진입(MainNavGraph)은 언제나 resolve() 결과를 넘긴다 —
// 예전에는 이 값들이 파라미터 기본값이라, 탑승이 하나도 없는 계정에도 「7월 25일 · 3,200원」이
// 늘 떠 있었다(눌러도 갈 곳 없는 카드).
private val DummyMembers = listOf(
    User(id = "u1", nickname = "김OO", verifiedLabel = "부산대 인증", rating = 4.9, rideCount = 12),
    User(id = "u2", nickname = "이OO", verifiedLabel = "부산대 인증", rating = 4.8, rideCount = 8),
    User(id = "u3", nickname = "박OO", verifiedLabel = "직장 인증", rating = 4.7, rideCount = 21),
)

private val DummyOngoingRide = Ride(
    id = "ride-ongoing",
    origin = "부산대 정문",
    destination = "서면역 1번 출구",
    departureLabel = "오후 6:45",
    capacity = 4,
    members = DummyMembers,
    farePerPerson = 3600,
    totalFare = 10800,
    status = RideStatus.ONGOING,
)

private val DummyUpcomingRide = Ride(
    id = "ride-upcoming",
    origin = "정문",
    destination = "서면역",
    departureLabel = "7월 25일 · 오전 8:20",
    capacity = 4,
    members = DummyMembers.take(2),
    farePerPerson = 3200,
    totalFare = 12800,
    status = RideStatus.MATCHED,
)

/**
 * 34 · 내 탑승 (진행 중 · 예정) [S22]
 *
 * 진입: 하단탭 「마이」 아님 — 17 배너 · 35 탑승 기록
 *
 * 이동(디스크립션):
 * - 「진행 상황 보기 ›」 → 지금 단계 화면 21/25/26 (onOpenStage) — 방 status·기사 배정 여부로 갈린다
 * - 진행 중 카드 탭 → 22 탑승 상세 (onRideClick)
 * - 예정 카드 탭 → 22 (onRideClick, 미연결)
 * - 하단탭 홈 / 합승 / 채팅 / 마이 → 14 / 17 / 24 / 35 (onTabSelect)
 * - 「지난 탑승 기록」 안내 → 35 마이페이지 (onHistoryClick)
 *
 * 검증(디스크립션):
 * - 지난 탑승은 이 탭에 표시하지 않음 (35 마이 > 탑승 기록)
 * - 진행 중 0건이면 「진행 중」 섹션 숨기고 예정만 표시, 둘 다 없으면 빈 상태 + 홈 유도
 * - 「예정 탑승」은 아직 서버에 개념이 없다(방은 만들자마자 진행 중이다) — 언제나 빈 목록이다
 */
@Composable
fun MyRidesScreen(
    /** 지금 진행 중인 내 방(ActivePartyViewModel.resolve 결과). 없으면 빈 상태를 그린다 */
    ongoingRide: Ride? = null,
    upcomingRides: List<Ride> = emptyList(),
    onRideClick: (Ride) -> Unit = {},      // → 22 탑승 상세
    onOpenStage: (Ride) -> Unit = {},      // → 21 / 25 / 26 (지금 단계)
    onHistoryClick: () -> Unit = {},       // → 35 마이페이지
    onTabSelect: (MoyeotaTab) -> Unit = {}, // → 14 / 17 / 24 / 35
) {
    // 내부 상태: 세그먼트 (진행 중 / 예정)
    var segment by remember { mutableStateOf(RideSegment.ONGOING) }

    Column(modifier = Modifier.fillMaxSize().background(CanvasBg)) {
        StatusBarSpacer()

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "내 탑승",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MoyeotaColor.InkPrimary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(Modifier.height(16.dp))
            // 세그먼트 (진행 중 · 예정) + 필터 아이콘 (무동작)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SegmentPill(
                    text = "진행 중",
                    selected = segment == RideSegment.ONGOING,
                    onClick = { segment = RideSegment.ONGOING },
                )
                Spacer(Modifier.size(8.dp))
                SegmentPill(
                    text = "예정",
                    selected = segment == RideSegment.UPCOMING,
                    onClick = { segment = RideSegment.UPCOMING },
                )
                Spacer(Modifier.weight(1f))
                FilterIcon()
            }

            val showOngoing = segment == RideSegment.ONGOING && ongoingRide != null
            val showUpcoming = upcomingRides.isNotEmpty()

            if (ongoingRide == null && upcomingRides.isEmpty()) {
                // 빈 상태 + 홈 유도
                Spacer(Modifier.height(140.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "진행 중·예정 탑승이 없어요",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = GrayMute,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "홈에서 목적지를 검색해 보세요",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MoyeotaColor.Primary600,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onTabSelect(MoyeotaTab.HOME) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            } else {
                if (showOngoing && ongoingRide != null) {
                    Spacer(Modifier.height(29.dp))
                    Text(
                        text = "진행 중",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = GrayMute,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    OngoingRideCard(
                        ride = ongoingRide,
                        onClick = { onRideClick(ongoingRide) },
                        onOpenStage = { onOpenStage(ongoingRide) },
                    )
                }

                if (showUpcoming) {
                    Spacer(Modifier.height(22.dp))
                    Text(
                        text = "예정 탑승 · ${upcomingRides.size}건",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = GrayMute,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        upcomingRides.forEach { ride ->
                            UpcomingRideCard(ride = ride, onClick = { onRideClick(ride) })
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))
                // 「지난 탑승 기록」 안내 → 35 마이페이지
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .height(64.dp)
                        .shadow(4.dp, RoundedCornerShape(18.dp), spotColor = CardShadow)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MoyeotaColor.SurfaceCanvas)
                        .clickable { onHistoryClick() }
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "지난 탑승 기록",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MoyeotaColor.InkPrimary,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "마이 > 탑승 기록에서 볼 수 있어요",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = GrayMute,
                    )
                }

                Spacer(Modifier.height(28.dp))
                Text(
                    text = "진행 중·예정 탑승만 이 탭에 보여요",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = GrayAsh,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }

            Spacer(Modifier.height(16.dp))
        }

        // 하단탭 홈 / 합승 / 채팅 / 마이 → 14 / 17 / 24 / 35
        MoyeotaBottomBar(selected = MoyeotaTab.EXPLORE, onSelect = onTabSelect)
    }
}

private enum class RideSegment { ONGOING, UPCOMING }

@Composable
private fun SegmentPill(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MoyeotaColor.Primary500 else MoyeotaColor.SurfaceCanvas
    val fg = if (selected) MoyeotaColor.TextOnDark else GraySlate
    Box(
        modifier = Modifier
            .height(31.dp)
            .then(
                if (selected) Modifier
                else Modifier.shadow(4.dp, CircleShape, spotColor = CardShadow),
            )
            .clip(CircleShape)
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 19.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}

/**
 * 진행 중 카드 — 값은 전부 서버 방 상세([Ride])에서 온다.
 *
 * 「실시간 위치 보기」였던 자리가 「진행 상황 보기」가 됐다. 예전엔 무슨 단계든 26 운행 중으로만
 * 보냈는데, 아직 사람을 모으는 방에서 그리로 가면 빈 지도가 뜬다 — 지금 단계로 보낸다.
 */
@Composable
private fun OngoingRideCard(
    ride: Ride,
    onClick: () -> Unit,
    onOpenStage: () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(18.dp), spotColor = CardShadow)
            .clip(RoundedCornerShape(18.dp))
            .background(MoyeotaColor.SurfaceCanvas)
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Text(
            text = ride.activeStageLabel,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MoyeotaColor.Primary600,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = ride.destination,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MoyeotaColor.InkPrimary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "출발 ${ride.origin}",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = GrayMute,
        )
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MoyeotaColor.Hairline)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                // 서버 members[] 그대로 — 나를 포함한 수다. 방이 「몇 명으로 굴러가는지」가 이 자리의 값이다
                text = "동승자 나 포함 ${ride.members.size}명 · 정원 ${ride.capacity}명",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = GraySlate,
            )
            Spacer(Modifier.weight(1f))
            // 「진행 상황 보기 ›」 → 21 / 25 / 26
            Text(
                text = "진행 상황 보기 ›",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MoyeotaColor.Primary600,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOpenStage() },
            )
        }
    }
}

@Composable
private fun UpcomingRideCard(ride: Ride, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(100.dp)
            .shadow(4.dp, RoundedCornerShape(18.dp), spotColor = CardShadow)
            .clip(RoundedCornerShape(18.dp))
            .background(MoyeotaColor.SurfaceCanvas)
            .clickable { onClick() }
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = ride.departureLabel,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = GrayMute,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "${ride.origin} → ${ride.destination}",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MoyeotaColor.InkPrimary,
            )
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "%,d원".format(ride.farePerPerson),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MoyeotaColor.InkPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "예정",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = GrayMute,
            )
        }
    }
}

// ─── 아이콘 (material-icons 미사용 — Canvas 직접 드로잉) ─────────────────────

// 필터(정렬) 아이콘 — 디스크립션상 동작 없음
@Composable
private fun FilterIcon(modifier: Modifier = Modifier, color: Color = GraySlate) {
    Canvas(modifier = modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()
        drawLine(color, Offset(w * 0.1f, h * 0.28f), Offset(w * 0.9f, h * 0.28f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.22f, h * 0.52f), Offset(w * 0.78f, h * 0.52f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.36f, h * 0.76f), Offset(w * 0.64f, h * 0.76f), stroke, StrokeCap.Round)
    }
}


@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun MyRidesScreenPreview() {
    MyRidesScreen(ongoingRide = DummyOngoingRide, upcomingRides = listOf(DummyUpcomingRide))
}
