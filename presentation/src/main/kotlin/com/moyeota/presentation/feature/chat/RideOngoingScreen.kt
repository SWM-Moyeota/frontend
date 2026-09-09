package com.moyeota.presentation.feature.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeota.core.designsystem.component.BackArrowIcon
import com.moyeota.core.designsystem.component.MoyeotaDefaultCamera
import com.moyeota.core.designsystem.component.NavigationBarSpacer
import com.moyeota.core.designsystem.component.RouteMapView
import com.moyeota.core.designsystem.component.SheetHandle
import com.moyeota.core.designsystem.component.StatusBarSpacer
import com.moyeota.core.designsystem.component.latLngOrNull
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.presentation.core.location.UserCoordinates
import com.naver.maps.geometry.LatLng
import com.naver.maps.geometry.LatLngBounds
import com.naver.maps.map.CameraAnimation
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.NaverMap
import com.naver.maps.map.overlay.LocationOverlay

// 와이어프레임 색 (core token 미정의 — 화면 재현용)
private val ScreenBg = Color(0xFFF5F7FA)
private val RouteCardBg = Color(0xFFF1F5FD)
private val RouteLine = Color(0xFFC9D6EC)
private val RouteDotGray = Color(0xFFC3CCDA)
private val MuteGray = Color(0xFF8A93A0)
private val AshGray = Color(0xFF9AA1AC)
private val SlateGray = Color(0xFF4B5563)
private val ReportBg = Color(0xFFFDECEE) // Danger50 동일값 — 와이어프레임 신고 칩 배경
private val ReportRed = Color(0xFFDC2626) // 와이어프레임 신고 텍스트 (Safety500 동일값, 신고 요소 전용)
private val CardShadow = Color(0x1A1B2A4A)

/**
 * 26 · 운행 중 — 안심 공유 [S15]
 *
 * 이동(디스크립션):
 * - 뒤로 → 25 배차 상태 (onBack)
 * - 「채팅 열기」 → 24 채팅 (onOpenChat)
 * - 「신고」 / 「문제가 생기면 아래에서 바로 신고할 수 있어요」 → 27 긴급 신고 (onReport)
 *
 * 28 최종 요금으로의 전이는 이 화면에 없다. 기사측 운행 종료를 서버 status 로 관찰하는
 * [RideOngoingRoute] 가 넘긴다 — 화면은 수동 트리거를 두지 않는다(승객이 하차를 선언하는 개념이 아니다).
 *
 * 상태: 보호자 공유 토글은 로컬 상태. 심야(23:00~04:00)는 설정 무관 자동 공유(11 설정 기준).
 * 플로우 진행 화면 — 하단탭 없음 (공통 규칙).
 *
 * 지도: 출발·도착 마커 + 서버 확정 경로([routePath]) + 내 현재 위치(파란 점, [myLocation]).
 * 좌표는 [RideOngoingRoute] 의 방 상세 폴링·위치 구독이 내려준다 — null 이면 그 요소만 빠진 지도를 그린다.
 */
@Composable
fun RideOngoingScreen(
    remainingLabel: String = "서면역까지 8분 남음",
    arrivalLabel: String = "오후 6:57 도착 예정 · 위치가 실시간으로 반영돼요",
    guardianLabel: String = "어머니 · 010-••••-1234 · 도착하면 자동으로 알려드려요",
    originPosition: LatLng? = null,
    destinationPosition: LatLng? = null,
    routePath: List<LatLng> = emptyList(),
    myLocation: UserCoordinates? = null,
    onBack: () -> Unit = {},
    onOpenChat: () -> Unit = {},
    onReport: () -> Unit = {},
) {
    var guardianSharing by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxSize().background(ScreenBg)) {
        // 헤더 (흰 배경)
        Column(modifier = Modifier.fillMaxWidth().background(MoyeotaColor.SurfaceCanvas)) {
            StatusBarSpacer()
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onBack() },
                ) {
                    BackArrowIcon(modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "운행 중",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MoyeotaColor.InkPrimary,
                )
                Spacer(Modifier.weight(1f))
                ShieldIcon(tint = SlateGray)
            }
        }

        // 지도 — 출발·도착 마커 + 경로 + 내 위치. 크기는 기존 플레이스홀더(190dp)를 그대로 유지한다
        RideOngoingMap(
            modifier = Modifier.fillMaxWidth().height(190.dp),
            originPosition = originPosition,
            destinationPosition = destinationPosition,
            routePath = routePath,
            myLocation = myLocation,
        )

        // 바텀 시트
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .shadow(14.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), spotColor = CardShadow)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(MoyeotaColor.SurfaceCanvas),
        ) {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 14.dp), contentAlignment = Alignment.Center) {
                SheetHandle()
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = remainingLabel,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MoyeotaColor.InkPrimary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = arrivalLabel,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MuteGray,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(Modifier.height(18.dp))
            // 경유 순서 카드 — 진행 상황 표시 전용. 탭 동작 없음(하차는 기사가 서버에 알린다).
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(RouteCardBg),
            ) {
                // 타임라인 연결선
                Box(
                    Modifier
                        .offset(x = 30.dp, y = 30.dp)
                        .size(width = 3.dp, height = 88.dp)
                        .background(RouteLine, RoundedCornerShape(1.5.dp)),
                )
                Column(modifier = Modifier.fillMaxSize().padding(vertical = 18.dp)) {
                    RouteStepRow(label = "부산대 정문 · 탑승 완료", time = "6:45", state = RouteStepState.DONE)
                    Spacer(Modifier.weight(1f))
                    RouteStepRow(label = "서면역 1번 출구로 이동 중", time = "6:57", state = RouteStepState.CURRENT)
                    Spacer(Modifier.weight(1f))
                    RouteStepRow(label = "내린 뒤 현장에서 1/N 정산", time = null, state = RouteStepState.PENDING)
                }
            }

            Spacer(Modifier.height(18.dp))
            // 안심 공유 카드 — 보호자 실시간 공유 (11에서 등록·동의된 연락처에만 전송)
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .shadow(4.dp, RoundedCornerShape(16.dp), spotColor = CardShadow)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MoyeotaColor.SurfaceCanvas)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                ShieldIcon(tint = MoyeotaColor.Success500, modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "보호자에게 실시간 공유 중",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MoyeotaColor.InkPrimary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = guardianLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MuteGray,
                    )
                }
                Spacer(Modifier.width(10.dp))
                // 토글 (하차·도착 확인 시 자동 종료 + 도착 알림 발송)
                Box(
                    modifier = Modifier
                        .size(width = 46.dp, height = 26.dp)
                        .background(
                            if (guardianSharing) MoyeotaColor.Success500 else MoyeotaColor.TextAsh,
                            RoundedCornerShape(13.dp),
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { guardianSharing = !guardianSharing },
                    contentAlignment = if (guardianSharing) Alignment.CenterEnd else Alignment.CenterStart,
                ) {
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .size(20.dp)
                            .background(MoyeotaColor.SurfaceCanvas, CircleShape),
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // 신고 안내 문구 → 27
            Text(
                text = "문제가 생기면 아래에서 바로 신고할 수 있어요",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = AshGray,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onReport() },
            )
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                // 「신고」 → 27 긴급 신고
                Row(
                    modifier = Modifier
                        .size(width = 112.dp, height = 52.dp)
                        .background(ReportBg, RoundedCornerShape(16.dp))
                        .clickable { onReport() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    WarnTriangleIcon(tint = ReportRed)
                    Spacer(Modifier.width(6.dp))
                    Text(text = "신고", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ReportRed)
                }
                Spacer(Modifier.weight(1f))
                // 「채팅 열기」 → 24 채팅
                Row(
                    modifier = Modifier
                        .size(width = 176.dp, height = 52.dp)
                        .shadow(4.dp, RoundedCornerShape(16.dp), spotColor = CardShadow)
                        .clip(RoundedCornerShape(16.dp))
                        .background(RouteCardBg)
                        .clickable { onOpenChat() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    ChatBubbleIcon(tint = SlateGray)
                    Spacer(Modifier.width(7.dp))
                    Text(text = "채팅 열기", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = SlateGray)
                }
            }
            Spacer(Modifier.height(14.dp))
            NavigationBarSpacer()
        }
    }
}

private enum class RouteStepState { DONE, CURRENT, PENDING }

@Composable
private fun RouteStepRow(label: String, time: String?, state: RouteStepState) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(30.dp), contentAlignment = Alignment.Center) {
            when (state) {
                RouteStepState.CURRENT -> Box(
                    Modifier
                        .size(20.dp)
                        .background(MoyeotaColor.Primary500, CircleShape)
                        .border(3.dp, MoyeotaColor.SurfaceCanvas, CircleShape),
                )
                RouteStepState.DONE -> Box(Modifier.size(14.dp).background(RouteDotGray, CircleShape))
                RouteStepState.PENDING -> Box(Modifier.size(14.dp).background(RouteLine, CircleShape))
            }
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (state == RouteStepState.CURRENT) FontWeight.Bold else FontWeight.Medium,
            color = if (state == RouteStepState.CURRENT) MoyeotaColor.InkPrimary else MuteGray,
            modifier = Modifier.weight(1f),
        )
        if (time != null) {
            Text(
                text = time,
                fontSize = 14.sp,
                fontWeight = if (state == RouteStepState.CURRENT) FontWeight.Bold else FontWeight.Medium,
                color = if (state == RouteStepState.CURRENT) MoyeotaColor.InkPrimary else MuteGray,
            )
        }
    }
}

// ─── 지도 ────────────────────────────────────────────────────────────────────

/** 출발·도착이 모두 보이게 맞출 때 마커가 지도 가장자리에 붙지 않도록 두는 여백 */
private val FitBoundsPadding = 40.dp

/** 서버 좌표가 오기 전, 내 위치만으로 카메라를 잡을 때의 줌 — 동네 단위가 보이는 수준 */
private const val MyLocationZoom = 15.0

/**
 * 운행 중 지도. [RouteMapView] 가 출발·도착 마커와 경로를 그리고, 이 컴포저블은
 * 내 위치 파란 점과 **초기 카메라**(세 지점이 다 보이는 fitBounds)만 얹는다.
 *
 * 카메라는 딱 두 번만 움직인다 — ① 첫 실위치가 잡히면 내 위치 중심(서버 좌표 대기 중 폴백),
 * ② 출발·도착 좌표가 도착하면 내 위치까지 포함한 fitBounds. 그 뒤로는 사용자 팬·줌이 주인이다.
 * 폴링(4초)·위치 갱신(1초)마다 카메라를 다시 맞추면 지도를 볼 수가 없다
 * (RouteMapView 의 driverPosition 을 center 로 쓰지 않는 것과 같은 이유).
 * 그래서 [RouteMapView] 의 center 도 **상수**로 고정한다 — 기본값(originPosition)을 쓰면
 * 폴링으로 좌표가 도착하는 순간 NaverMapView 가 카메라를 되돌려 fitBounds 를 덮어쓴다.
 */
@Composable
private fun RideOngoingMap(
    modifier: Modifier = Modifier,
    originPosition: LatLng?,
    destinationPosition: LatLng?,
    routePath: List<LatLng>,
    myLocation: UserCoordinates?,
) {
    var map by remember { mutableStateOf<NaverMap?>(null) }
    val density = LocalDensity.current
    val myPosition = latLngOrNull(myLocation?.latitude, myLocation?.longitude)
    // fitBounds 는 마커 좌표가 "도착한 순간" 1회만 도는 이펙트라, 그 시점의 내 위치는
    // key 가 아니라 최신 참조로 읽는다 — key 로 넣으면 1초마다 카메라가 다시 맞춰진다
    val currentMyPosition by rememberUpdatedState(myPosition)

    // 화면 회전·재진입 후에는 NaverMapView 가 사용자가 보던 카메라를 복원한다 —
    // 그 위에 fitBounds 를 또 걸면 보던 위치가 날아가므로 두 플래그 모두 rememberSaveable 로 남긴다
    var boundsFitted by rememberSaveable { mutableStateOf(false) }
    var centeredOnMe by rememberSaveable { mutableStateOf(false) }

    RouteMapView(
        modifier = modifier,
        routePath = routePath,
        originPosition = originPosition,
        destinationPosition = destinationPosition,
        center = MoyeotaDefaultCamera, // 상수 — 카메라는 아래 이펙트가 움직인다 (KDoc 참고)
        onMapReady = { map = it },
    )

    // ② 출발·도착 좌표가 갖춰지면 세 지점이 다 보이게 1회 fitBounds.
    // 좌표는 서버가 방 생성 시 확정한 값이라 폴링 주기마다 같은 값이 온다 — key 재실행 없음.
    LaunchedEffect(map, originPosition, destinationPosition) {
        val naverMap = map ?: return@LaunchedEffect
        if (boundsFitted || originPosition == null || destinationPosition == null) return@LaunchedEffect
        val bounds = LatLngBounds.Builder()
            .include(originPosition)
            .include(destinationPosition)
            .apply { currentMyPosition?.let(::include) }
            .build()
        val padding = with(density) { FitBoundsPadding.roundToPx() }
        naverMap.moveCamera(CameraUpdate.fitBounds(bounds, padding).animate(CameraAnimation.Easing))
        boundsFitted = true
    }

    // ① 폴백 — 서버 좌표보다 실위치가 먼저 잡히면 우선 내 위치 중심으로. 1회만(이후 갱신은 점만 따라간다)
    LaunchedEffect(map, myPosition) {
        val naverMap = map ?: return@LaunchedEffect
        val fix = myPosition ?: return@LaunchedEffect
        if (boundsFitted || centeredOnMe) return@LaunchedEffect
        naverMap.moveCamera(CameraUpdate.scrollAndZoomTo(fix, MyLocationZoom))
        centeredOnMe = true
    }

    if (myPosition != null) {
        RideMyLocationOverlay(
            map = map,
            position = myPosition,
            bearingDegrees = myLocation?.bearingDegrees,
        )
    }
}

// 내 위치 보간 파라미터 — 합승 탭(ExploreScreen)의 MyLocationOverlay 와 같은 값
/** 새 좌표까지 미끄러져 가는 시간. 갱신 주기(1초)보다 짧아야 다음 fix 전에 도착한다 */
private const val MyLocationGlideMs = 800

/** 이 이상 튀면 보간하지 않고 순간이동한다 — 지도를 가로질러 기어가는 점이 더 이상하다 */
private const val MyLocationSnapDistanceM = 200.0

/** 이 이하의 미세 이동은 애니메이션 없이 반영한다 (GPS 지터로 계속 애니메이션이 걸리는 것 방지) */
private const val MyLocationMinMoveM = 0.5

/**
 * 내 위치 파란 점 — 네이버 SDK 의 [LocationOverlay]. 합승 탭의 MyLocationOverlay(비공개)와
 * 같은 패턴의 화면 전용 축약판이다: 1초 주기 좌표를 등속 보간으로 미끄러뜨리고, 방향을 아는
 * fix 에만 화살표를 띄운다. 오차 원은 그리지 않는다 — 차량 이동 속도에서는 반경이 정보가
 * 아니라 잡음이고, 줌마다 픽셀 환산을 다시 해야 해 카메라 리스너까지 필요해진다.
 * (공용 승격은 세 번째 지도 화면 정리 때 함께 — 지금은 explore·home 조정·여기 모두 각자 든다)
 */
@Composable
private fun RideMyLocationOverlay(map: NaverMap?, position: LatLng, bearingDegrees: Float?) {
    var rendered by remember { mutableStateOf(position) }
    LaunchedEffect(position) {
        val from = rendered
        val moved = from.distanceTo(position)
        if (moved < MyLocationMinMoveM || moved > MyLocationSnapDistanceM || !moved.isFinite()) {
            rendered = position
            return@LaunchedEffect
        }
        // 다음 fix 가 오면 이 이펙트가 취소되고 현재 위치에서 새 목표로 다시 출발한다
        Animatable(0f).animateTo(1f, tween(MyLocationGlideMs, easing = LinearEasing)) {
            rendered = LatLng(
                from.latitude + (position.latitude - from.latitude) * value,
                from.longitude + (position.longitude - from.longitude) * value,
            )
        }
    }

    // 오버레이는 NaverMap 당 하나뿐인 싱글턴 — 화면을 떠날 때 반드시 숨긴다
    DisposableEffect(map) {
        map?.locationOverlay?.isVisible = true
        onDispose { map?.locationOverlay?.isVisible = false }
    }

    LaunchedEffect(map, rendered, bearingDegrees) {
        val overlay = map?.locationOverlay ?: return@LaunchedEffect
        overlay.position = rendered
        // 방향을 모르는 fix 는 화살표를 띄우지 않는다 — 없는 방향을 북쪽이라고 그리면 안 된다
        overlay.bearing = bearingDegrees ?: 0f
        overlay.subIcon = if (bearingDegrees != null) LocationOverlay.DEFAULT_SUB_ICON_ARROW else null
    }
}

// ─── 아이콘 (material-icons 미사용 — Canvas 직접 드로잉) ─────────────────────

@Composable
private fun ShieldIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.7.dp.toPx()
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.08f)
            lineTo(w * 0.88f, h * 0.24f)
            lineTo(w * 0.88f, h * 0.52f)
            quadraticTo(w * 0.88f, h * 0.78f, w * 0.5f, h * 0.94f)
            quadraticTo(w * 0.12f, h * 0.78f, w * 0.12f, h * 0.52f)
            lineTo(w * 0.12f, h * 0.24f)
            close()
        }
        drawPath(path, tint, style = Stroke(stroke, join = StrokeJoin.Round))
        drawLine(tint, Offset(w * 0.34f, h * 0.5f), Offset(w * 0.46f, h * 0.62f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.46f, h * 0.62f), Offset(w * 0.68f, h * 0.36f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun WarnTriangleIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.12f)
            lineTo(w * 0.92f, h * 0.85f)
            lineTo(w * 0.08f, h * 0.85f)
            close()
        }
        drawPath(path, tint, style = Stroke(stroke, join = StrokeJoin.Round))
        drawLine(tint, Offset(w * 0.5f, h * 0.4f), Offset(w * 0.5f, h * 0.62f), stroke, StrokeCap.Round)
        drawCircle(tint, radius = 1.1.dp.toPx(), center = Offset(w * 0.5f, h * 0.74f))
    }
}

@Composable
private fun ChatBubbleIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(19.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.08f, h * 0.12f),
            size = androidx.compose.ui.geometry.Size(w * 0.84f, h * 0.62f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
            style = Stroke(stroke),
        )
        val tail = Path().apply {
            moveTo(w * 0.28f, h * 0.74f)
            lineTo(w * 0.28f, h * 0.92f)
            lineTo(w * 0.46f, h * 0.74f)
        }
        drawPath(tail, tint, style = Stroke(stroke, join = StrokeJoin.Round))
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun RideOngoingScreenPreview() {
    RideOngoingScreen()
}
