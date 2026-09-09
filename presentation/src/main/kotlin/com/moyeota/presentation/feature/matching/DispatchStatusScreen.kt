package com.moyeota.presentation.feature.matching

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeota.core.designsystem.component.BackArrowIcon
import com.moyeota.core.designsystem.component.NavigationBarSpacer
import com.moyeota.core.designsystem.component.RouteMapView
import com.moyeota.core.designsystem.component.SheetHandle
import com.moyeota.core.designsystem.component.StatusBarSpacer
import com.moyeota.core.designsystem.component.decodePolyline
import com.moyeota.core.designsystem.component.latLngOrNull
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.domain.model.AssignedDriver
import com.moyeota.domain.model.DriverLocation
import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RideStatus
import com.moyeota.domain.model.User
import com.moyeota.presentation.core.OpenChatButton
import com.moyeota.presentation.core.displayNickname

// 와이어프레임 그레이 (core token 미정의 색 — 화면 재현용)
private val CanvasBg = Color(0xFFF5F7FA)
private val GraySlate = Color(0xFF4B5563)
private val GrayMute = Color(0xFF8A93A0)
private val GrayAsh = Color(0xFF9AA1AC)
private val ChipBg = Color(0xFFF1F5FD)
private val CardSoft = Color(0xFFF6F8FB)
private val DividerGray = Color(0xFFE7EAF0)

private val dispatchRideDummy = Ride(
    id = "ride-25",
    origin = "부산대학교 정문",
    destination = "서면역 1번 출구",
    departureLabel = "지금 출발",
    capacity = 3,
    members = listOf(
        User("partner-1", "부산불곰", "", 0.0, 12),
        User("partner-2", "해운대곰돌", "", 0.0, 6),
        User("me", "부산가자", "", 0.0, 5, isMe = true),
    ),
    farePerPerson = 3600,
    totalFare = 10800,
    status = RideStatus.DISPATCHING,
)

/**
 * 25 · 배차 상태 [S14]
 *
 * 진입: 22 「이 인원으로 출발」 또는 21 매칭 대기의 status 전이(DISPATCHING)
 * — 공통 규칙상 배차 이후 스택 초기화, 화면은 콜백만 노출
 *
 * 이동(디스크립션):
 * - 26 운행 중으로의 전이는 **이 화면이 하지 않는다**. 기사측 탑승 처리(서버 status IN_RIDE)를
 *   [DispatchStatusRoute] 의 폴링이 잡아 자동으로 넘긴다. 임시 트리거였던 화면 전체 탭은
 *   폴링이 실동작하게 되면서 걷어냈다(오탭하면 탑승 전에 26 으로 넘어갔다 — 결함 D-9 수정).
 *   26 의 「경유 카드 탭」을 걷어낸 것과 같은 이유다(35 보고서).
 * - 「채팅 열기」 → 24 채팅방(독립 목적지 push — 뒤로가기로 여기 복귀). [onOpenChat] 이 null 이면 그리지 않는다
 * - 차량 번호 롱프레스 → 복사 [미연결]
 * - 뒤로 → [미연결] 배차 후 되돌리기 차단 권장 (onBack — 무동작 기본값)
 * - 배차 실패 시 21 매칭 대기로 되돌리고 재탐색 (호출부 처리)
 *
 * 서버 제약: 백엔드 `DriverSummary` 는 (좌석수 · 번호판 · 차종) 뿐이라
 * **기사 이름·별점을 내려주지 않는다**. 그 자리는 하드코딩 대신 플레이스홀더로 둔다(백엔드 요청 대기).
 */
@Composable
fun DispatchStatusScreen(
    ride: Ride = dispatchRideDummy,
    driver: AssignedDriver? = null,
    driverLocation: DriverLocation? = null,
    /** 이 방의 채팅방을 연다. 아직 방이 없으면 null — 버튼 자체를 그리지 않는다 */
    onOpenChat: (() -> Unit)? = null,
    onBack: () -> Unit = {}, // 미연결 (배차 후 되돌리기 차단 권장)
) {
    val pickupSpot = ride.origin
    // 기사 위치는 값이 있어도 좌표로 못 쓸 수 있다(서버 위경도 전치 — QA D-1).
    // 지도 마커와 아래 안내 문구가 **같은 판정**을 쓰도록 여기서 한 번만 검증한다.
    val driverPosition = latLngOrNull(driverLocation?.latitude, driverLocation?.longitude)
    val vehicleNumber = driver?.plateNumber ?: "차량 번호 확인 중"
    val vehicleModel = when {
        driver == null -> "차량 정보를 불러오는 중이에요"
        driver.seats != null -> "${driver.vehicleType} · ${driver.seats}인승"
        else -> driver.vehicleType
    }
    // 동승자 = 나를 뺀 멤버(isMe = publicId == 세션 uuid). 탈퇴 회원은 이름이 없어 그렇게 적는다.
    val partners = ride.members.filter { !it.isMe }
    val partnerLabel = when {
        partners.isEmpty() -> "나 혼자 탑승"
        else -> partners.joinToString(", ") { it.displayNickname } + " · 나 포함 ${ride.members.size}명"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBg),
    ) {
        // 상단 흰색 헤더
        Column(modifier = Modifier.fillMaxWidth().background(MoyeotaColor.SurfaceCanvas)) {
            StatusBarSpacer()
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

        // 지도 — 서버 경로 폴리라인 + 폴링으로 갱신되는 기사 위치 마커.
        // 경로는 방 상세의 routePolyline 을 쓴다. 방이 이미 만들어진 뒤라 서버가 확정한 경로가
        // 있으므로, 좌표만으로 추정하는 previewRoute(16 도착지 확인에서 쓴다)를 부를 이유가 없다.
        val routePath = remember(ride.routePolyline) {
            ride.routePolyline?.let(::decodePolyline).orEmpty()
        }
        RouteMapView(
            modifier = Modifier.fillMaxWidth().height(176.dp),
            routePath = routePath,
            driverPosition = driverPosition, // 범위를 벗어난 좌표면 null → 마커 미표시
            originPosition = latLngOrNull(ride.originLat, ride.originLng),
            destinationPosition = latLngOrNull(ride.destinationLat, ride.destinationLng),
        )

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
                    text = if (driver == null) "기사님을 배정하고 있어요" else "기사님이 오고 있어요",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MoyeotaColor.InkPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    // 기사가 아직 위치를 보고하지 않았을 수 있다 — 정상 상황이라 에러로 다루지 않는다.
                    // 좌표가 못 쓸 값이어도(D-1) 마커를 못 그리므로 「받아오는 중」을 유지한다.
                    text = if (driverPosition == null) {
                        "$pickupSpot 앞에서 만나요 · 기사님 위치를 받아오는 중"
                    } else {
                        "$pickupSpot 앞에서 만나요"
                    },
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
                        // 기사명·별점은 백엔드 DriverSummary 에 필드가 없다 (백엔드 요청 2번)
                        Text(
                            text = "기사 정보 준비 중",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = GrayMute,
                        )
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
                    DispatchInfoRow(label = "도착지", value = ride.destination)
                    HorizontalDivider(color = DividerGray)
                    // 서버가 닉네임을 주므로 "나 포함 N명" 대신 누구와 타는지를 쓴다 —
                    // 차에 타기 직전 화면이라 인원수보다 이름이 필요한 자리다.
                    DispatchInfoRow(label = "동승자", value = partnerLabel)
                    HorizontalDivider(color = DividerGray)
                    if (ride.estimatedMinutes != null) {
                        DispatchInfoRow(label = "예상 소요", value = "${ride.estimatedMinutes}분")
                        HorizontalDivider(color = DividerGray)
                    }
                    DispatchInfoRow(
                        label = "1인 부담",
                        value = "%,d원".format(ride.farePerPerson),
                        valueColor = MoyeotaColor.Primary500,
                    )
                }
                if (onOpenChat != null) {
                    Spacer(Modifier.height(16.dp))
                    OpenChatButton(onClick = onOpenChat, modifier = Modifier.fillMaxWidth())
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
        NavigationBarSpacer(Modifier.background(MoyeotaColor.SurfaceCanvas))
    }
}

@Composable
internal fun DispatchInfoRow(label: String, value: String, valueColor: Color = MoyeotaColor.InkPrimary) {
    Row(
        modifier = Modifier.fillMaxWidth().height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MoyeotaColor.TextMute)
        Spacer(Modifier.width(12.dp))
        // 값 쪽이 길어질 수 있다(동승자 닉네임 나열·전체 주소) — 라벨을 밀어내는 대신 말줄임한다
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

// ─── 아이콘 (material-icons 미사용 — Canvas 직접 드로잉) ─────────────────────

@Composable
internal fun CarIcon(modifier: Modifier = Modifier, tint: Color = Color(0xFF54637D)) {
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

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun DispatchStatusScreenPreview() {
    DispatchStatusScreen()
}
