package com.moyeota.presentation.feature.matching

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.moyeota.core.designsystem.component.AvatarCircle
import com.moyeota.core.designsystem.component.BackArrowIcon
import com.moyeota.core.designsystem.component.NavigationBarSpacer
import com.moyeota.core.designsystem.component.NoticeKind
import com.moyeota.core.designsystem.component.SheetHandle
import com.moyeota.core.designsystem.component.StatusBadge
import com.moyeota.core.designsystem.component.StatusBarSpacer
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RideStatus
import com.moyeota.domain.model.User

// 와이어프레임 그레이 (core token 미정의 색 — 화면 재현용)
private val CanvasBg = Color(0xFFF5F7FA)
private val GraySlate = Color(0xFF4B5563)
private val GrayDeep = Color(0xFF54637D)
private val GrayMute = Color(0xFF8A93A0)
private val GrayAsh = Color(0xFF9AA1AC)
private val ChipBg = Color(0xFFF1F5FD)
private val CardSoft = Color(0xFFF6F8FB)
private val GrayButtonBg = Color(0xFFEEF1F6)
private val MapBg = Color(0xFFE9EDF3)
private val MapBlock = Color(0xFFDDE3EC)
private val DashGray = Color(0xFFB9C3D6)

private val recruitingRideDummy = Ride(
    id = "ride-22",
    origin = "부산대학교 정문 버스정류장",
    destination = "서면역 1번 출구",
    departureLabel = "오후 6:45",
    capacity = 3,
    members = listOf(
        User("partner-1", "김OO", "부산대 인증", 4.9, 12),
        User("me", "나", "부산대 인증", 4.8, 5),
    ),
    farePerPerson = 5100,
    totalFare = 9600,
    status = RideStatus.RECRUITING,
)

/**
 * 22 · 탑승 상세 — 모집 중 [S12]
 *
 * 이동(디스크립션):
 * - 뒤로 → 21 매칭 대기 (onBack)
 * - 동승자 「김OO」 탭 → 23 동승자 프로필 (onPartnerClick)
 * - 「나가기」 → 14 홈, 탑승 이탈 (onLeave)
 * - [미연결] 없음
 *
 * 도메인 변경(2026-08-30): 백엔드가 자동 기사 매칭으로 전환하며 방장 개념이 사라졌다.
 * 수동 출발(「이 인원으로 출발」)이라는 행위 자체가 없어져 CTA 를 화면에서 제거했고,
 * 21 매칭 대기와 같은 결정이다. 멤버는 전부 동등하게 표시한다(방장 배지 없음).
 * 25 배차 현황으로는 서버 status 전이를 관찰하는 21 이 넘긴다 — 이 화면은 넘기지 않는다.
 */
@Composable
fun RideDetailScreen(
    ride: Ride = recruitingRideDummy,
    currentUserId: String = "me",
    genderLabel: String = "여성만",
    etaLabel: String = "예상 12분 · 6.2km",
    walkLabel: String = "도보 2분 · 180m",
    arrivalLabel: String = "오후 6:57 도착",
    serviceFee: Int = 600,
    onBack: () -> Unit = {},
    onPartnerClick: (User) -> Unit = {},
    onLeave: () -> Unit = {},
) {
    val partners = ride.members.filter { it.id != currentUserId }
    // 인원 변동 시 1인 부담 즉시 재계산 — 수수료 포함 10원 단위
    val perPersonFare = if (ride.members.isNotEmpty()) {
        (ride.totalFare + serviceFee) / ride.members.size / 10 * 10
    } else {
        0
    }

    Column(modifier = Modifier.fillMaxSize().background(CanvasBg)) {
        // 상단 흰색 헤더
        Column(modifier = Modifier.fillMaxWidth().background(MoyeotaColor.SurfaceCanvas)) {
            StatusBarSpacer()
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { BackArrowIcon() }
                Text(
                    text = "탑승 상세",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MoyeotaColor.InkPrimary,
                )
                Spacer(Modifier.weight(1f))
                ShieldIcon()
            }
        }

        // 지도 + 예상 시간 칩
        Box(modifier = Modifier.fillMaxWidth().height(136.dp)) {
            RouteMapArea(modifier = Modifier.fillMaxSize())
            Box(
                modifier = Modifier
                    .padding(start = 16.dp, top = 16.dp)
                    .shadow(4.dp, CircleShape, spotColor = Color(0x141B2A4A))
                    .background(MoyeotaColor.SurfaceCanvas, CircleShape)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Text(text = etaLabel, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MoyeotaColor.InkPrimary)
            }
        }

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
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                Spacer(Modifier.height(10.dp))

                // 상태 배지 행
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(kind = NoticeKind.SUCCESS, text = "모집 중")
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "${ride.members.size}명 참여 · 목표 ${ride.capacity}명",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = GrayMute,
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .background(ChipBg, CircleShape)
                            .padding(horizontal = 14.dp, vertical = 5.dp),
                    ) {
                        Text(text = genderLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GrayDeep)
                    }
                }
                Spacer(Modifier.height(16.dp))

                // Group A · 경로
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ChipBg, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(5.dp))
                        Box(Modifier.size(11.dp).background(MoyeotaColor.MarkerOrigin, CircleShape))
                        DashedRouteLine(modifier = Modifier.height(30.dp).padding(vertical = 3.dp))
                        Box(Modifier.size(11.dp).background(MoyeotaColor.MarkerDestination, CircleShape))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = ride.origin,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MoyeotaColor.InkPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = ride.departureLabel,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MoyeotaColor.InkPrimary,
                            )
                        }
                        Text(text = walkLabel, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = GrayMute)
                        Spacer(Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = ride.destination,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MoyeotaColor.InkPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = arrivalLabel,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MoyeotaColor.InkPrimary,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))

                // Group B · 요금
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardSoft, RoundedCornerShape(16.dp))
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                ) {
                    FareRow(label = "총 예상 요금", value = wonLabel(ride.totalFare))
                    Spacer(Modifier.height(12.dp))
                    FareRow(label = "서비스 수수료 (2차)", value = wonLabel(serviceFee))
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MoyeotaColor.Primary50, RoundedCornerShape(14.dp))
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = "1인 부담", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MoyeotaColor.Primary600)
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = wonLabel(perPersonFare),
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = MoyeotaColor.Primary500,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    FareRow(label = "정산 방식", value = "10원 단위 · 1/N")
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CheckSmallIcon(tint = MoyeotaColor.Success500)
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "수수료 포함 · 1원 단위 없이 10원 단위로 나눠요",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = GrayMute,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))

                // 함께 타는 사람
                Text(
                    text = "함께 타는 사람 · ${partners.size}명",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = GrayMute,
                )
                Spacer(Modifier.height(8.dp))
                partners.forEach { partner ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPartnerClick(partner) }
                            .padding(vertical = 4.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AvatarCircle(size = 40.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            // 멤버는 전부 동등하다 — 서버에 방장 표식이 없으므로 배지를 달지 않는다
                            Text(
                                text = partner.nickname,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MoyeotaColor.InkPrimary,
                            )
                            Text(
                                text = "탑승 ${partner.rideCount}회 · 매너 98%",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = GrayMute,
                            )
                        }
                        ChevronRightIcon()
                    }
                }
                Spacer(Modifier.height(14.dp))

                // 앱이 출발을 트리거하지 않는다는 사실을 알려주는 지점 (21 매칭 대기와 같은 문구)
                Text(
                    text = if (ride.members.size >= ride.capacity) {
                        "기사님을 찾고 있어요. 배차되면 바로 알려드릴게요"
                    } else {
                        "정원이 차면 기사님이 자동으로 배차돼요"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = GrayAsh,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(12.dp))
            }

            // 남은 액션은 나가기 하나뿐 — 수동 출발 버튼은 도메인에서 사라졌다
            GrayActionButton(
                text = "나가기",
                onClick = onLeave,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(12.dp))
        }
        NavigationBarSpacer(Modifier.background(MoyeotaColor.SurfaceCanvas))
    }
}

@Composable
private fun FareRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MoyeotaColor.TextMute)
        Spacer(Modifier.weight(1f))
        Text(text = value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MoyeotaColor.InkPrimary)
    }
}

private fun wonLabel(amount: Int): String = "%,d원".format(amount)

// 지도 자리표시 — 블록 + 공유 경로 폴리라인 + 도착 핀 (와이어프레임 재현)
@Composable
private fun RouteMapArea(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.background(MapBg)) {
        val w = size.width
        val h = size.height
        // 건물 블록
        listOf(
            Offset(w * 0.04f, h * 0.14f) to Size(w * 0.24f, h * 0.28f),
            Offset(w * 0.34f, h * 0.06f) to Size(w * 0.3f, h * 0.24f),
            Offset(w * 0.7f, h * 0.16f) to Size(w * 0.25f, h * 0.3f),
            Offset(w * 0.38f, h * 0.56f) to Size(w * 0.26f, h * 0.3f),
            Offset(w * 0.76f, h * 0.62f) to Size(w * 0.2f, h * 0.28f),
        ).forEach { (topLeft, blockSize) ->
            drawRoundRect(MapBlock, topLeft, blockSize, CornerRadius(6.dp.toPx()))
        }
        // 도보 구간 (점선) + 공유 경로 (실선)
        val start = Offset(w * 0.16f, h * 0.82f)
        val mid = Offset(w * 0.38f, h * 0.66f)
        val pin = Offset(w * 0.77f, h * 0.28f)
        drawLine(
            color = DashGray,
            start = start,
            end = mid,
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)),
        )
        drawLine(MoyeotaColor.RouteShared, mid, pin, 3.5.dp.toPx(), StrokeCap.Round)
        drawCircle(MoyeotaColor.RouteShared, 6.dp.toPx(), mid)
        // 도착 핀
        val pinPath = Path().apply {
            moveTo(pin.x, pin.y + 12.dp.toPx())
            lineTo(pin.x - 9.dp.toPx(), pin.y - 4.dp.toPx())
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(
                    center = Offset(pin.x, pin.y - 4.dp.toPx()),
                    radius = 9.dp.toPx(),
                ),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false,
            )
            close()
        }
        drawPath(pinPath, MoyeotaColor.MarkerDestination)
        drawCircle(MoyeotaColor.TextOnDark, 3.5.dp.toPx(), Offset(pin.x, pin.y - 4.dp.toPx()))
    }
}

// 경로 카드의 출발-도착 점선 연결선
@Composable
private fun DashedRouteLine(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.width(3.dp)) {
        drawLine(
            color = DashGray,
            start = Offset(size.width / 2f, 0f),
            end = Offset(size.width / 2f, size.height),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 7f)),
        )
    }
}

// ─── 아이콘 (material-icons 미사용 — Canvas 직접 드로잉) ─────────────────────

@Composable
private fun ShieldIcon(modifier: Modifier = Modifier, tint: Color = GrayDeep) {
    Canvas(modifier = modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val shield = Path().apply {
            moveTo(w * 0.5f, h * 0.08f)
            lineTo(w * 0.88f, h * 0.22f)
            lineTo(w * 0.88f, h * 0.52f)
            quadraticTo(w * 0.88f, h * 0.78f, w * 0.5f, h * 0.94f)
            quadraticTo(w * 0.12f, h * 0.78f, w * 0.12f, h * 0.52f)
            lineTo(w * 0.12f, h * 0.22f)
            close()
        }
        drawPath(shield, tint, style = stroke)
        drawLine(tint, Offset(w * 0.34f, h * 0.5f), Offset(w * 0.46f, h * 0.62f), stroke.width, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.46f, h * 0.62f), Offset(w * 0.68f, h * 0.36f), stroke.width, StrokeCap.Round)
    }
}

@Composable
private fun CheckSmallIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(14.dp)) {
        val w = size.width
        val h = size.height
        val strokeWidth = 1.8.dp.toPx()
        drawLine(tint, Offset(w * 0.12f, h * 0.55f), Offset(w * 0.42f, h * 0.82f), strokeWidth, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.42f, h * 0.82f), Offset(w * 0.88f, h * 0.2f), strokeWidth, StrokeCap.Round)
    }
}

@Composable
private fun ChevronRightIcon(modifier: Modifier = Modifier, tint: Color = GrayAsh) {
    Canvas(modifier = modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val strokeWidth = 2.dp.toPx()
        drawLine(tint, Offset(w * 0.38f, h * 0.24f), Offset(w * 0.66f, h * 0.5f), strokeWidth, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.38f, h * 0.76f), Offset(w * 0.66f, h * 0.5f), strokeWidth, StrokeCap.Round)
    }
}

// 와이어프레임의 회색 보조 버튼 (나가기)
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

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun RideDetailScreenPreview() {
    RideDetailScreen()
}
