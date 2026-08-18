package com.moyeota.presentation.feature.explore

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.moyeota.core.designsystem.component.MapPlaceholder
import com.moyeota.core.designsystem.component.NoticeBanner
import com.moyeota.core.designsystem.component.NoticeKind
import com.moyeota.core.designsystem.component.PrimaryCtaButton
import com.moyeota.core.designsystem.component.SecondaryButton
import com.moyeota.core.designsystem.component.SheetHandle
import com.moyeota.core.designsystem.component.StatusBadge
import com.moyeota.core.designsystem.component.StatusBarMock
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RideStatus
import com.moyeota.domain.model.User
import kotlin.math.roundToInt

// 와이어프레임 그레이·서페이스 (core token 미정의 색 — 화면 재현용)
private val CanvasBg = Color(0xFFF5F7FA)
private val GrayMute = Color(0xFF8A93A0)
private val GrayAsh = Color(0xFF9AA1AC)
private val GraySlateDeep = Color(0xFF54637D)
private val TextMuteWarm = Color(0xFF6B7280)
private val RouteCardBg = Color(0xFFF1F5FD)
private val FareCardBg = Color(0xFFF6F8FB)
private val PillBg = Color(0xFFF1F5FD)

private val JoinHost = User("u-host", "김OO", "학교 인증", 4.9, 12)

private val DefaultJoinRide = Ride(
    id = "ride-1",
    origin = "부산대학교 정문 버스정류장",
    destination = "서면역 1번 출구",
    departureLabel = "3분 후 출발 예정 · 6.2km",
    capacity = 3,
    members = listOf(JoinHost, User("u-2", "이OO", "직장 인증", 4.8, 7)),
    farePerPerson = 3600,
    totalFare = 9600,
    status = RideStatus.RECRUITING,
)

private fun won(amount: Int): String = "%,d원".format(amount)

/**
 * 20 · 합류 확인 [신규]
 *
 * 진입: 18·19 목록/마커의 「합류」
 *
 * 이동(디스크립션):
 * - 「닫기」 / 뒤로가기 → 직전 목록(18/19)으로 복귀 (onDismiss)
 * - 「이 탑승에 합류하기」 → 22 탑승 상세 (onConfirmJoin, 제출 중 loading)
 * - 동승자 「김OO」 행 탭 → 23 동승자 프로필 (onMemberClick)
 * - [미연결] 우측 상단 방패 아이콘 — 무동작
 *
 * 검증·상태:
 * - 합류 시점 정원 재확인 — 이미 찼으면 CTA 위 NoticeBanner(ERROR) 「방금 인원이 찼어요」 + CTA 비활성
 */
@Composable
fun JoinConfirmScreen(
    ride: Ride = DefaultJoinRide,
    femaleOnly: Boolean = true,
    serviceFee: Int = 600,
    etaLabel: String = "예상 12분 · 6.2km",
    pickupTimeLabel: String = "오후 6:45",
    walkLabel: String = "도보 2분 · 180m",
    arrivalTimeLabel: String = "오후 6:57 도착",
    onDismiss: () -> Unit = {},
    onConfirmJoin: (Ride) -> Unit = {},
    onMemberClick: (User) -> Unit = {},
) {
    var joining by remember { mutableStateOf(false) }
    val joinedCount = ride.members.size
    val isFull = joinedCount >= ride.capacity

    Column(modifier = Modifier.fillMaxSize().background(CanvasBg)) {
        // 상단 헤더 (흰 배경)
        Column(modifier = Modifier.fillMaxWidth().background(MoyeotaColor.SurfaceCanvas)) {
            StatusBarMock()
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackArrowIcon(modifier = Modifier.clickable { onDismiss() })
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "합류할까요?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MoyeotaColor.InkPrimary,
                )
                Spacer(Modifier.weight(1f))
                ShieldIcon() // 미연결 — 무동작
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // 경로 지도 스트립
            Box(modifier = Modifier.fillMaxWidth().height(172.dp)) {
                MapPlaceholder(modifier = Modifier.fillMaxSize())
                RoutePreviewCanvas(modifier = Modifier.fillMaxSize())
                Box(
                    modifier = Modifier
                        .padding(16.dp)
                        .height(30.dp)
                        .shadow(4.dp, CircleShape, spotColor = Color(0x141B2A4A))
                        .background(MoyeotaColor.SurfaceCanvas, CircleShape)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = etaLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MoyeotaColor.InkPrimary,
                    )
                }
            }

            // 바텀시트
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 160.dp)
                    .shadow(14.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), spotColor = Color(0x141B2A4A))
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(MoyeotaColor.SurfaceCanvas),
            ) {
                Spacer(Modifier.height(10.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    SheetHandle()
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                ) {
                    Spacer(Modifier.height(14.dp))

                    // 상태 행
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusBadge(kind = NoticeKind.SUCCESS, text = "모집 중")
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "${joinedCount}명 참여 · 목표 ${ride.capacity}명 · 내가 ${joinedCount + 1}번째",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = GrayMute,
                            modifier = Modifier.weight(1f),
                        )
                        if (femaleOnly) {
                            GrayPill(text = "여성만")
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    RouteCard(
                        origin = ride.origin,
                        destination = ride.destination,
                        pickupTimeLabel = pickupTimeLabel,
                        walkLabel = walkLabel,
                        arrivalTimeLabel = arrivalTimeLabel,
                    )

                    Spacer(Modifier.height(16.dp))
                    FareCard(
                        totalFare = ride.totalFare,
                        serviceFee = serviceFee,
                        farePerPerson = ride.farePerPerson,
                        capacity = ride.capacity,
                    )

                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "이미 참여한 사람 · ${joinedCount}명",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = GrayMute,
                    )
                    Spacer(Modifier.height(8.dp))
                    val host = ride.members.firstOrNull()
                    if (host != null) {
                        MemberRow(
                            host = host,
                            othersCount = joinedCount - 1,
                            onClick = { onMemberClick(host) }, // → 23 동승자 프로필
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "합류하면 인원 ${joinedCount + 1}명으로 요금이 확정되고 채팅방에 들어가요",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = GrayAsh,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                }

                // 정원 재확인 — 이미 찼으면 CTA 위 오류 배너 + CTA 비활성 (스펙 20)
                if (isFull) {
                    NoticeBanner(
                        kind = NoticeKind.ERROR,
                        text = "방금 인원이 찼어요. 목록에서 다른 합승을 선택해 주세요",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SecondaryButton(
                        text = "닫기",
                        onClick = onDismiss,
                        modifier = Modifier.width(112.dp),
                    )
                    PrimaryCtaButton(
                        text = "이 탑승에 합류하기",
                        onClick = {
                            joining = true
                            onConfirmJoin(ride) // → 22 탑승 상세
                        },
                        enabled = !isFull,
                        loading = joining,
                        modifier = Modifier.weight(1f),
                    )
                }

                HomeIndicatorBar()
            }
        }
    }
}

// ─── 조각 ───────────────────────────────────────────────────────────────────

@Composable
private fun RouteCard(
    origin: String,
    destination: String,
    pickupTimeLabel: String,
    walkLabel: String,
    arrivalTimeLabel: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(RouteCardBg, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RouteDot(color = MoyeotaColor.MarkerOrigin)
            Spacer(Modifier.width(12.dp))
            Text(
                text = origin,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MoyeotaColor.InkPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = pickupTimeLabel,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MoyeotaColor.InkPrimary,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(11.dp), contentAlignment = Alignment.Center) {
                DashedVerticalLine()
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = walkLabel,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = GrayMute,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RouteDot(color = MoyeotaColor.MarkerDestination)
            Spacer(Modifier.width(12.dp))
            Text(
                text = destination,
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
}

@Composable
private fun RouteDot(color: Color) {
    Box(Modifier.size(11.dp).background(color, CircleShape))
}

@Composable
private fun DashedVerticalLine() {
    Canvas(modifier = Modifier.size(width = 3.dp, height = 26.dp)) {
        drawLine(
            color = Color(0xFFC3CCDA),
            start = Offset(size.width / 2f, 0f),
            end = Offset(size.width / 2f, size.height),
            strokeWidth = size.width,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
        )
    }
}

@Composable
private fun FareCard(
    totalFare: Int,
    serviceFee: Int,
    farePerPerson: Int,
    capacity: Int,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(FareCardBg, RoundedCornerShape(16.dp))
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        FareRow(label = "총 예상 요금", value = won(totalFare))
        Spacer(Modifier.height(12.dp))
        FareRow(label = "서비스 수수료 (2차)", value = won(serviceFee))
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(MoyeotaColor.Primary50, RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "합류 시 1인 부담",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MoyeotaColor.Primary600,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = won(farePerPerson),
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = MoyeotaColor.Primary500,
            )
        }
        Spacer(Modifier.height(14.dp))
        FareRow(label = "정산 방식", value = "10원 단위 · 1/N")
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CheckIcon(color = GrayMute)
            Spacer(Modifier.width(6.dp))
            Text(
                text = "${capacity}명 기준 · 수수료 포함 10원 단위로 나눠요",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = GrayMute,
            )
        }
    }
}

@Composable
private fun FareRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = TextMuteWarm,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MoyeotaColor.InkPrimary,
        )
    }
}

@Composable
private fun MemberRow(
    host: User,
    othersCount: Int,
    onClick: () -> Unit,
) {
    val mannerPercent = (host.rating * 20).roundToInt()
    val othersLabel = if (othersCount > 0) " · 외 ${othersCount}명" else ""
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarCircle(size = 40.dp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = host.nickname,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MoyeotaColor.InkPrimary,
                )
                Spacer(Modifier.width(8.dp))
                GrayPill(text = "방장")
            }
            Text(
                text = "탑승 ${host.rideCount}회 · 매너 ${mannerPercent}%$othersLabel",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = GrayMute,
            )
        }
        ChevronRightIcon(color = GrayAsh)
    }
}

@Composable
private fun GrayPill(text: String) {
    Box(
        modifier = Modifier
            .background(PillBg, CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = GraySlateDeep,
        )
    }
}

// ─── Canvas 아이콘·경로 (material-icons 미사용) ─────────────────────────────

@Composable
private fun RoutePreviewCanvas(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val pickup = Offset(w * 0.38f, h * 0.63f)
        // 도보 구간 (점선)
        drawLine(
            color = GrayAsh,
            start = Offset(w * 0.16f, h * 0.84f),
            end = pickup,
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)),
        )
        // 합승 경로
        val route = Path().apply {
            moveTo(pickup.x, pickup.y)
            quadraticTo(w * 0.55f, h * 0.28f, w * 0.77f, h * 0.30f)
        }
        drawPath(route, MoyeotaColor.RouteShared, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
        // 출발점
        drawCircle(Color.White, 7.dp.toPx(), pickup)
        drawCircle(MoyeotaColor.MarkerOrigin, 5.dp.toPx(), pickup)
        // 도착 핀
        val pin = Offset(w * 0.77f, h * 0.24f)
        val tail = Path().apply {
            moveTo(pin.x - 6.dp.toPx(), pin.y + 5.dp.toPx())
            lineTo(pin.x + 6.dp.toPx(), pin.y + 5.dp.toPx())
            lineTo(pin.x, pin.y + 16.dp.toPx())
            close()
        }
        drawPath(tail, MoyeotaColor.MarkerDestination)
        drawCircle(MoyeotaColor.MarkerDestination, 8.dp.toPx(), pin)
        drawCircle(Color.White, 3.dp.toPx(), pin)
    }
}

@Composable
private fun ShieldIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val shield = Path().apply {
            moveTo(w * 0.5f, h * 0.08f)
            lineTo(w * 0.85f, h * 0.2f)
            lineTo(w * 0.85f, h * 0.5f)
            quadraticTo(w * 0.85f, h * 0.76f, w * 0.5f, h * 0.92f)
            quadraticTo(w * 0.15f, h * 0.76f, w * 0.15f, h * 0.5f)
            lineTo(w * 0.15f, h * 0.2f)
            close()
        }
        drawPath(shield, MoyeotaColor.InkPrimary, style = stroke)
        drawLine(MoyeotaColor.InkPrimary, Offset(w * 0.36f, h * 0.5f), Offset(w * 0.47f, h * 0.62f), stroke.width, StrokeCap.Round)
        drawLine(MoyeotaColor.InkPrimary, Offset(w * 0.47f, h * 0.62f), Offset(w * 0.66f, h * 0.36f), stroke.width, StrokeCap.Round)
    }
}

@Composable
private fun CheckIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(14.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()
        drawLine(color, Offset(w * 0.15f, h * 0.55f), Offset(w * 0.42f, h * 0.8f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.42f, h * 0.8f), Offset(w * 0.85f, h * 0.25f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun ChevronRightIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(16.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()
        drawLine(color, Offset(w * 0.38f, h * 0.22f), Offset(w * 0.66f, h * 0.5f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.38f, h * 0.78f), Offset(w * 0.66f, h * 0.5f), stroke, StrokeCap.Round)
    }
}

// 홈 인디케이터 (와이어프레임 하단 검은 바)
@Composable
private fun HomeIndicatorBar() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
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
private fun JoinConfirmScreenPreview() {
    JoinConfirmScreen()
}
