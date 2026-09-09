package com.moyeota.presentation.feature.matching

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeota.core.designsystem.component.BackArrowIcon
import com.moyeota.core.designsystem.component.NavigationBarSpacer
import com.moyeota.core.designsystem.component.NoticeBanner
import com.moyeota.core.designsystem.component.NoticeKind
import com.moyeota.core.designsystem.component.PrimaryCtaButton
import com.moyeota.core.designsystem.component.RadarSearchArea
import com.moyeota.core.designsystem.component.SheetHandle
import com.moyeota.core.designsystem.component.StatusBarSpacer
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.domain.model.AssignedDriver
import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RideStatus
import com.moyeota.domain.model.User
import kotlin.math.roundToInt

// 와이어프레임 그레이 (core token 미정의 색 — 화면 재현용)
private val StageCanvasBg = Color(0xFFF5F7FA)
private val StageGraySlate = Color(0xFF4B5563)
private val StageGrayMute = Color(0xFF8A93A0)
private val StageGrayAsh = Color(0xFF9AA1AC)
private val StageChipBg = Color(0xFFF1F5FD)
private val StageCardSoft = Color(0xFFF6F8FB)
private val StageDividerGray = Color(0xFFE7EAF0)
private val StageSheetShadow = Color(0x141B2A4A)

/**
 * 서버가 기사 매칭에 쓰는 제한 시간(분). 백엔드 `MATCHING_TIMEOUT` 과 같은 값이라
 * 문구에 그대로 쓴다 — 넘기면 서버가 방을 CANCELED 로 바꾼다([RideStatus.CANCELED]).
 */
private const val MATCHING_TIMEOUT_MINUTES = 3

/**
 * 도착 예정 시간을 어림할 때 쓰는 평균 시내 주행 속도(km/h).
 *
 * 직선거리 기반이라 **어림값이다.** 서버가 기사→탑승지 ETA 를 주면 그 값으로 갈아 끼워야 한다
 * (지금은 그런 API 가 없다). 실도로는 직선보다 길고 신호가 있어, 25km/h 는 그 둘을 함께 눌러 담은 값이다.
 */
private const val PICKUP_SPEED_KMH = 25.0

/**
 * 25b · 기사 찾는 중 (서버 status `MATCHING`)
 *
 * 정원이 차서 서버가 주변 기사에게 요청을 돌리는 단계다. 앱이 할 일은 없고 **기다림 자체가 화면의
 * 내용**이라, 지도 대신 [RadarSearchArea] 를 쓴다(기사 위치는 아직 없고, 경로는 21 에서 이미 봤다).
 *
 * **취소 버튼을 두지 않는다.** 서버가 MATCHING 상태에서 leave 를 허용하는지 확인되지 않았다 —
 * 눌리는 버튼을 두고 실패하는 것보다, 3분 뒤 타임아웃으로 [DriverSearchFailedScreen] 을 보여 주는 편이 낫다.
 *
 * @param onBack 헤더 뒤로가기. 배차 단계라 기본은 무동작이다(공통 규칙: 배차 후 되돌리기 차단)
 */
@Composable
fun DriverSearchScreen(
    ride: Ride = stageRideDummy,
    onBack: () -> Unit = {},
) {
    StageScaffold(title = "기사님 찾는 중", onBack = onBack) {
        RadarSearchArea(modifier = Modifier.fillMaxWidth().height(220.dp))

        StageSheet {
            Text(
                text = "주변 기사님에게 요청하고 있어요",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MoyeotaColor.InkPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "보통 1분 안에 배정돼요 · 최대 ${MATCHING_TIMEOUT_MINUTES}분",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = StageGrayMute,
            )
            Spacer(Modifier.height(16.dp))

            RideSummaryCard(ride = ride, showDestination = true)
            Spacer(Modifier.height(16.dp))

            NoticeBanner(kind = NoticeKind.WAITING, text = "기사님이 수락하면 바로 알려드려요")
        }
    }
}

/**
 * 25b-실패 · 기사 매칭 타임아웃 (서버 status `CANCELED`)
 *
 * 서버가 [MATCHING_TIMEOUT_MINUTES] 안에 기사를 못 찾으면 방을 스스로 취소한다. 그 방은 이미
 * 없어졌으므로 재시도는 **새 방을 만드는 것**뿐이다 — 그래서 「다시 찾기」는 홈으로 보낸다.
 */
@Composable
fun DriverSearchFailedScreen(
    onRetry: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    StageScaffold(title = "기사님 찾는 중", onBack = onBack) {
        Box(
            modifier = Modifier.fillMaxWidth().height(220.dp).background(StageCanvasBg),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier.size(88.dp).background(StageChipBg, RoundedCornerShape(44.dp)),
                contentAlignment = Alignment.Center,
            ) {
                CarIcon()
            }
        }

        StageSheet {
            Text(
                text = "기사님을 찾지 못했어요",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MoyeotaColor.InkPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${MATCHING_TIMEOUT_MINUTES}분 동안 수락한 기사님이 없어 요청이 취소됐어요",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = StageGrayMute,
            )
            Spacer(Modifier.height(16.dp))
            NoticeBanner(kind = NoticeKind.INFO, text = "다시 찾으면 같은 조건으로 새 방을 만들어요")
            Spacer(Modifier.height(20.dp))
            PrimaryCtaButton(text = "다시 찾기", onClick = onRetry)
        }
    }
}

/**
 * 25c · 배정 완료 (서버 status `DRIVER_ASSIGNED` 진입 직후 1회)
 *
 * 「배정됐다」는 사실만 확실히 전하고 25 배차 상태로 넘기는 **한 번짜리 확인 화면**이다.
 * 재진입 시에는 이 화면을 건너뛰고 25 로 직행한다([DispatchStatusRoute] 의 플래그).
 *
 * 기사 **이름·별점은 그리지 않는다** — 백엔드 `DriverSummary` 에 필드 자체가 없다.
 * 차량 번호·차종·인승만 서버가 준 값이며, 그 자리에 그럴듯한 이름을 지어내지 않는다.
 *
 * @param pickupEtaMinutes 기사 위치 → 탑승지 직선거리로 어림한 도착 예정(분). 위치를 아직
 *   못 받았으면 null 이고, 그때는 시간 대신 「탑승 위치로 오고 있어요」만 쓴다
 */
@Composable
fun DriverAssignedScreen(
    ride: Ride = stageRideDummy,
    driver: AssignedDriver? = null,
    pickupEtaMinutes: Int? = null,
    onSeeDispatch: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    StageScaffold(title = "배정 완료", onBack = onBack) {
        Box(
            modifier = Modifier.fillMaxWidth().height(200.dp).background(StageCanvasBg),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier.size(96.dp).background(StageChipBg, RoundedCornerShape(48.dp)),
                contentAlignment = Alignment.Center,
            ) {
                CarIcon(modifier = Modifier.size(width = 56.dp, height = 34.dp))
            }
        }

        StageSheet {
            Text(
                text = "기사님이 배정됐어요",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MoyeotaColor.InkPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (pickupEtaMinutes != null) {
                    "약 ${pickupEtaMinutes}분 뒤 탑승 위치 도착 예정"
                } else {
                    "탑승 위치로 오고 있어요"
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = StageGrayMute,
            )
            Spacer(Modifier.height(16.dp))

            // 차량 카드 — 서버가 주는 값(번호판·차종·인승)만 쓴다
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
                    modifier = Modifier.size(64.dp).background(StageChipBg, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    CarIcon()
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = driver?.plateNumber ?: "차량 번호 확인 중",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MoyeotaColor.InkPrimary,
                    )
                    Text(
                        text = vehicleLabel(driver),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = StageGraySlate,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            RideSummaryCard(ride = ride, showDestination = false)
            Spacer(Modifier.height(16.dp))

            Text(
                text = "기사님 정보를 확인하고 탑승 위치로 이동하세요",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = StageGrayAsh,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            PrimaryCtaButton(text = "배차 상태 보기", onClick = onSeeDispatch)
        }
    }
}

/**
 * 기사 위치 → 탑승지 **직선거리**로 어림한 도착 예정(분). 좌표가 없으면 null.
 *
 * 서버에 기사 ETA API 가 없어 앱이 어림한다. 직선거리라 실제보다 짧게 나오므로
 * [PICKUP_SPEED_KMH] 를 실제 주행 속도보다 낮게 잡아 상쇄한다. 0분은 쓰지 않는다 —
 * 「약 0분 뒤 도착」은 정보가 아니라 오류처럼 읽힌다.
 */
fun pickupEtaMinutes(distanceMeters: Double?): Int? {
    if (distanceMeters == null || !distanceMeters.isFinite() || distanceMeters < 0.0) return null
    val minutes = (distanceMeters / 1000.0) / PICKUP_SPEED_KMH * 60.0
    return minutes.roundToInt().coerceAtLeast(1)
}

/** 차종 · 인승. 인승을 모르면 차종만 쓴다 — 「?인승」을 쓰지 않는다. */
private fun vehicleLabel(driver: AssignedDriver?): String = when {
    driver == null -> "차량 정보를 불러오는 중이에요"
    driver.seats != null -> "${driver.vehicleType} · ${driver.seats}인승"
    else -> driver.vehicleType
}

/** 25b·25c 가 공유하는 정보 카드 — 탑승 위치 · (도착지) · 동승자 · 1인 부담 */
@Composable
private fun RideSummaryCard(ride: Ride, showDestination: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(StageCardSoft, RoundedCornerShape(18.dp))
            .padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        DispatchInfoRow(label = "탑승 위치", value = ride.origin)
        HorizontalDivider(color = StageDividerGray)
        if (showDestination) {
            DispatchInfoRow(label = "도착지", value = ride.destination)
            HorizontalDivider(color = StageDividerGray)
        }
        // 아직 차에 타기 전이라 「누구와」보다 「몇 명이」가 필요한 자리다(닉네임 나열은 25 에서 한다)
        DispatchInfoRow(label = "동승자", value = "나 포함 ${ride.members.size}명")
        HorizontalDivider(color = StageDividerGray)
        DispatchInfoRow(
            label = "1인 부담",
            value = "%,d원".format(ride.farePerPerson),
            valueColor = MoyeotaColor.Primary500,
        )
    }
}

/** 25b·25c 공통 뼈대 — 흰 헤더 + 상단 그림 + 시트 */
@Composable
private fun StageScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().background(StageCanvasBg)) {
        Column(modifier = Modifier.fillMaxWidth().background(MoyeotaColor.SurfaceCanvas)) {
            StatusBarSpacer()
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { BackArrowIcon() }
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MoyeotaColor.InkPrimary,
                )
            }
        }
        content()
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.StageSheet(
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .shadow(14.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), spotColor = StageSheetShadow)
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(MoyeotaColor.SurfaceCanvas),
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
            SheetHandle()
        }
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(10.dp))
            content()
        }
        Spacer(Modifier.weight(1f))
        NavigationBarSpacer()
    }
}

internal val stageRideDummy = Ride(
    id = "ride-25b",
    origin = "부산대학교 정문",
    destination = "서면역 1번 출구",
    departureLabel = "지금 출발",
    capacity = 2,
    members = listOf(
        User("me", "부산가자", "", 0.0, 5, isMe = true),
        User("partner-1", "부산불곰", "", 0.0, 12),
    ),
    farePerPerson = 6400,
    totalFare = 12800,
    status = RideStatus.DISPATCHING,
)

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun DriverSearchScreenPreview() {
    DriverSearchScreen()
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun DriverSearchFailedScreenPreview() {
    DriverSearchFailedScreen()
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun DriverAssignedScreenPreview() {
    DriverAssignedScreen(
        driver = AssignedDriver(seats = 4, plateNumber = "12가 3456", vehicleType = "쏘나타"),
        pickupEtaMinutes = 4,
    )
}
