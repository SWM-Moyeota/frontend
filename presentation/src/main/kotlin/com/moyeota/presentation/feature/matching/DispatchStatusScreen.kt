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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeota.core.designsystem.component.BackArrowIcon
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
private val GrayAsh = Color(0xFF9AA1AC)
private val ChipBg = Color(0xFFF1F5FD)
private val CardSoft = Color(0xFFF6F8FB)
private val DividerGray = Color(0xFFE7EAF0)
private val MapBg = Color(0xFFE9EDF3)
private val MapBlock = Color(0xFFDDE3EC)

private val dispatchRideDummy = Ride(
    id = "ride-25",
    origin = "부산대학교 정문",
    destination = "서면역 1번 출구",
    departureLabel = "지금 출발",
    capacity = 3,
    members = listOf(
        User("partner-1", "김OO", "부산대 인증", 4.9, 12),
        User("partner-2", "이OO", "부산대 인증", 4.7, 6),
        User("me", "나", "부산대 인증", 4.8, 5),
    ),
    farePerPerson = 3600,
    totalFare = 10800,
    status = RideStatus.DISPATCHING,
)

/**
 * 25 · 배차 상태 [S14]
 *
 * 진입: 22 「이 인원으로 출발」 — 공통 규칙상 배차 이후 스택 초기화, 화면은 콜백만 노출
 *
 * 이동(디스크립션):
 * - 화면 탭 / 탑승 시작 → 26 운행 중 (onStartRide)
 * - 차량 번호 「12가 3456」 롱프레스 → 복사 [미연결]
 * - 뒤로 → [미연결] 배차 후 되돌리기 차단 권장 (onBack — 무동작 기본값)
 * - 배차 실패 시 21 매칭 대기로 되돌리고 재탐색 (호출부 처리)
 */
@Composable
fun DispatchStatusScreen(
    ride: Ride = dispatchRideDummy,
    arrivalMinutes: Int = 3,
    pickupSpot: String = "정문 버스정류장",
    vehicleNumber: String = "12가 3456",
    vehicleModel: String = "쏘나타 · 흰색",
    driverLabel: String = "김OO 기사 · 별점 4.9",
    onStartRide: () -> Unit = {},
    onBack: () -> Unit = {}, // 미연결 (배차 후 되돌리기 차단 권장)
) {
    // 화면 탭 → 26 운행 중
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBg)
            .clickable { onStartRide() },
    ) {
        // 상단 흰색 헤더
        Column(modifier = Modifier.fillMaxWidth().background(MoyeotaColor.SurfaceCanvas)) {
            StatusBarMock()
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { BackArrowIcon() }
                Text(
                    text = "택시 오는 중",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MoyeotaColor.InkPrimary,
                )
            }
        }

        // 지도 — 기사 위치와 경로
        DriverMapArea(modifier = Modifier.fillMaxWidth().height(176.dp))

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
                    text = "${arrivalMinutes}분 뒤 도착해요",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MoyeotaColor.InkPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$pickupSpot 앞에서 만나요",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = GrayMute,
                )
                Spacer(Modifier.height(16.dp))

                // 차량 카드
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(18.dp), spotColor = Color(0x0F1B2A4A))
                        .clip(RoundedCornerShape(18.dp))
                        .background(MoyeotaColor.SurfaceCanvas)
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(64.dp).background(ChipBg, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CarIcon()
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = vehicleNumber,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MoyeotaColor.InkPrimary,
                        )
                        Text(text = vehicleModel, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = GraySlate)
                        Text(text = driverLabel, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = GrayMute)
                    }
                    // 기사 전화 (미연결 — 무동작)
                    Box(
                        modifier = Modifier.size(44.dp).background(MoyeotaColor.Primary50, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        PhoneIcon()
                    }
                }
                Spacer(Modifier.height(16.dp))

                // 탑승 정보 카드
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardSoft, RoundedCornerShape(18.dp))
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                ) {
                    DispatchInfoRow(label = "탑승 위치", value = pickupSpot)
                    HorizontalDivider(color = DividerGray)
                    DispatchInfoRow(label = "동승자", value = "나 포함 ${ride.members.size}명")
                    HorizontalDivider(color = DividerGray)
                    DispatchInfoRow(
                        label = "1인 부담",
                        value = "%,d원".format(ride.farePerPerson),
                        valueColor = MoyeotaColor.Primary500,
                    )
                }
                Spacer(Modifier.height(16.dp))

                Text(
                    text = "기사님 도착 후 3분간 기다려요",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = GrayAsh,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.weight(1f))
        }
        HomeIndicator()
    }
}

@Composable
private fun DispatchInfoRow(label: String, value: String, valueColor: Color = MoyeotaColor.InkPrimary) {
    Row(
        modifier = Modifier.fillMaxWidth().height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MoyeotaColor.TextMute)
        Spacer(Modifier.weight(1f))
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

// 지도 자리표시 — 블록 + 경로 + 접근 중인 차량 마커 (와이어프레임 재현)
@Composable
private fun DriverMapArea(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.background(MapBg)) {
        val w = size.width
        val h = size.height
        listOf(
            Offset(w * 0.04f, h * 0.14f) to Size(w * 0.24f, h * 0.26f),
            Offset(w * 0.34f, h * 0.06f) to Size(w * 0.3f, h * 0.22f),
            Offset(w * 0.7f, h * 0.14f) to Size(w * 0.25f, h * 0.3f),
            Offset(w * 0.04f, h * 0.56f) to Size(w * 0.28f, h * 0.3f),
            Offset(w * 0.38f, h * 0.54f) to Size(w * 0.24f, h * 0.3f),
            Offset(w * 0.76f, h * 0.62f) to Size(w * 0.2f, h * 0.26f),
        ).forEach { (topLeft, blockSize) ->
            drawRoundRect(MapBlock, topLeft, blockSize, CornerRadius(6.dp.toPx()))
        }
        // 내 위치 → 차량 경로
        val me = Offset(w * 0.24f, h * 0.78f)
        val car = Offset(w * 0.78f, h * 0.3f)
        drawLine(MoyeotaColor.RouteShared, me, car, 3.5.dp.toPx(), StrokeCap.Round)
        drawCircle(MoyeotaColor.RouteShared, 6.dp.toPx(), me)
        // 차량 마커 (검정 라운드 박스 + 흰색 창)
        drawRoundRect(
            color = MoyeotaColor.InkPrimary,
            topLeft = Offset(car.x - 22.dp.toPx(), car.y - 11.dp.toPx()),
            size = Size(44.dp.toPx(), 22.dp.toPx()),
            cornerRadius = CornerRadius(7.dp.toPx()),
        )
        drawRoundRect(
            color = MoyeotaColor.TextOnDark,
            topLeft = Offset(car.x - 12.dp.toPx(), car.y - 1.5.dp.toPx()),
            size = Size(8.dp.toPx(), 3.dp.toPx()),
            cornerRadius = CornerRadius(1.5.dp.toPx()),
        )
        drawRoundRect(
            color = MoyeotaColor.TextOnDark,
            topLeft = Offset(car.x + 4.dp.toPx(), car.y - 1.5.dp.toPx()),
            size = Size(8.dp.toPx(), 3.dp.toPx()),
            cornerRadius = CornerRadius(1.5.dp.toPx()),
        )
    }
}

// ─── 아이콘 (material-icons 미사용 — Canvas 직접 드로잉) ─────────────────────

@Composable
private fun CarIcon(modifier: Modifier = Modifier, tint: Color = Color(0xFF54637D)) {
    Canvas(modifier = modifier.size(width = 40.dp, height = 24.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
        // 차체
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.05f, h * 0.4f),
            size = Size(w * 0.9f, h * 0.42f),
            cornerRadius = CornerRadius(4.dp.toPx()),
            style = stroke,
        )
        // 지붕
        drawLine(tint, Offset(w * 0.28f, h * 0.4f), Offset(w * 0.38f, h * 0.1f), stroke.width, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.38f, h * 0.1f), Offset(w * 0.68f, h * 0.1f), stroke.width, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.68f, h * 0.1f), Offset(w * 0.78f, h * 0.4f), stroke.width, StrokeCap.Round)
        // 바퀴
        drawCircle(tint, 2.5.dp.toPx(), Offset(w * 0.25f, h * 0.86f))
        drawCircle(tint, 2.5.dp.toPx(), Offset(w * 0.75f, h * 0.86f))
    }
}

@Composable
private fun PhoneIcon(modifier: Modifier = Modifier, tint: Color = MoyeotaColor.Primary500) {
    Canvas(modifier = modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.28f, h * 0.1f),
            size = Size(w * 0.44f, h * 0.8f),
            cornerRadius = CornerRadius(3.dp.toPx()),
            style = Stroke(width = 1.8.dp.toPx()),
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.42f, h * 0.76f),
            end = Offset(w * 0.58f, h * 0.76f),
            strokeWidth = 1.8.dp.toPx(),
            cap = StrokeCap.Round,
        )
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
private fun DispatchStatusScreenPreview() {
    DispatchStatusScreen()
}
