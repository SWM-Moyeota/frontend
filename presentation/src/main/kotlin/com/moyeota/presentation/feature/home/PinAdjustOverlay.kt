package com.moyeota.presentation.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeota.core.designsystem.component.BackArrowIcon
import com.moyeota.core.designsystem.component.NavigationBarSpacer
import com.moyeota.core.designsystem.component.PrimaryCtaButton
import com.moyeota.core.designsystem.component.RouteMapView
import com.moyeota.core.designsystem.component.StatusBarSpacer
import com.moyeota.core.designsystem.component.latLngOrNull
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.presentation.core.location.FormLocationIntervalMs
import com.moyeota.presentation.core.location.rememberMyLocationState
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.NaverMap

/** 핀 조정 대상. 한 화면에서 상단 토글로 전환한다. */
enum class PinTarget { ORIGIN, DESTINATION }

// 조정 화면 줌 — 건물 단위가 구분되는 수준. 16 미리보기(15.0)보다 두 단계 당긴다
private const val AdjustZoom = 17.0

private val PinWidth = 26.dp
private val PinHeight = 34.dp

// 카메라가 움직이는 동안 핀이 지면에서 떠오르는 높이 (카카오T 와 같은 「집어 든」 연출)
private val PinLift = 8.dp

private val GrayMute = Color(0xFF8A93A0)
private val GrayAsh = Color(0xFF9AA1AC)

/**
 * 출발지 · 도착지 좌표 미세조정 (16 모달 내부 확장 상태).
 *
 * **마커를 드래그하지 않는다.** 핀은 화면 정중앙에 고정되고 사용자가 지도를 팬·줌하면
 * 핀 아래 좌표(= `naverMap.cameraPosition.target`)가 바뀐다 — 카카오T·네이버지도와 같은
 * 방식. 손가락이 핀을 가리지 않고, 정밀한 조정을 확대로 해결할 수 있어서다.
 *
 * 조정 **중에는** 이름을 건드리지 않는다 — 팬 한 번에 역지오코딩을 부르면 요청이 쏟아진다.
 * 이름 갱신은 「이 위치로 설정」 확정 뒤 호출부(16)가 한 번만 처리한다.
 *
 * 신규 네비게이션 목적지를 만들지 않은 이유: 16 은 `dialog()` 목적지라 여기서 다시
 * `navigate` 하면 조정 화면이 **다이얼로그 아래**에 깔린다. 같은 다이얼로그 윈도우 안의
 * 최상단 레이어로 띄우는 편이 z-order·뒤로가기 모두 예측 가능하다.
 *
 * @param originName / [destinationName] 하단 패널 라벨용 이름. 실주소가 들어올 수 있어 1줄 말줄임이다
 * @param originPosition / [destinationPosition] 조정 시작 좌표. null 이면 그 대상은 조정 불가
 * @param initialTarget 진입점이 지정한 최초 조정 대상
 * @param onConfirm (출발지, 도착지) 확정 좌표. 조정하지 않은 쪽은 들어온 값 그대로 돌려준다
 */
@Composable
fun PinAdjustOverlay(
    originName: String,
    destinationName: String,
    originPosition: LatLng?,
    destinationPosition: LatLng?,
    initialTarget: PinTarget,
    onCancel: () -> Unit,
    onConfirm: (LatLng?, LatLng?) -> Unit,
) {
    // 조정 대상이 없으면 띄울 이유가 없다 (호출부가 이미 막지만 방어)
    if (originPosition == null && destinationPosition == null) return

    var target by remember {
        mutableStateOf(
            when {
                initialTarget == PinTarget.ORIGIN && originPosition == null -> PinTarget.DESTINATION
                initialTarget == PinTarget.DESTINATION && destinationPosition == null -> PinTarget.ORIGIN
                else -> initialTarget
            },
        )
    }

    // 작업 중인 좌표. 카메라 이동 콜백이 **매 프레임** 쓰므로 by 위임 없이 State 로 들고,
    // 화면 본문에서는 읽지 않는다(읽으면 팬 중에 전체가 재구성된다). 읽는 쪽은 아래
    // 좌표 표시·핀 색처럼 지연 읽기(람다)로 스코프를 좁힌 컴포저블뿐이다.
    val originWorking = remember { mutableStateOf(originPosition) }
    val destinationWorking = remember { mutableStateOf(destinationPosition) }
    val moving = remember { mutableStateOf(false) }

    // 카메라에 실제로 넣는 값. 대상 전환 시에만 바뀐다 — 팬 중에 이 값을 갱신하면
    // NaverMapView 가 카메라를 되돌려 지도가 손가락과 싸운다.
    var anchor by remember {
        mutableStateOf(
            (if (target == PinTarget.ORIGIN) originPosition else destinationPosition)
                ?: originPosition ?: destinationPosition!!,
        )
    }
    LaunchedEffect(target) {
        val next = if (target == PinTarget.ORIGIN) originWorking.value else destinationWorking.value
        if (next != null) anchor = next
    }

    var map by remember { mutableStateOf<NaverMap?>(null) }
    val currentTarget by rememberUpdatedState(target)

    // 지도가 멈춰 있을 때만 이벤트가 오므로, change 는 이동 중 실시간 좌표 · idle 은 확정 좌표.
    DisposableEffect(map) {
        val naverMap = map
        if (naverMap == null) return@DisposableEffect onDispose { }

        fun writeTarget() {
            val position = naverMap.cameraPosition.target
            when (currentTarget) {
                PinTarget.ORIGIN -> originWorking.value = position
                PinTarget.DESTINATION -> destinationWorking.value = position
            }
        }

        val onChange = NaverMap.OnCameraChangeListener { _, _ ->
            moving.value = true
            writeTarget()
        }
        val onIdle = NaverMap.OnCameraIdleListener {
            moving.value = false
            writeTarget()
        }
        naverMap.addOnCameraChangeListener(onChange)
        naverMap.addOnCameraIdleListener(onIdle)
        onDispose {
            naverMap.removeOnCameraChangeListener(onChange)
            naverMap.removeOnCameraIdleListener(onIdle)
        }
    }

    // GPS 파란 점 — 자기 위치 대비 조정에 쓴다. 권한을 새로 묻지 않는다(조정 흐름을 끊지 않게).
    // 지도 화면이지만 점 자체가 보조 정보라 1초 대신 5초 주기로 충분하다.
    val myLocation = rememberMyLocationState(
        autoRequestPermission = false,
        updateIntervalMs = FormLocationIntervalMs,
    )
    val myPosition = latLngOrNull(
        myLocation.coordinates?.latitude,
        myLocation.coordinates?.longitude,
    )
    DisposableEffect(map, myPosition) {
        val overlay = map?.locationOverlay
        if (overlay != null && myPosition != null) {
            overlay.position = myPosition
            overlay.isVisible = true
        }
        // locationOverlay 는 NaverMap 당 하나뿐인 싱글턴이라 setMap 이 아니라 isVisible 로 끈다
        onDispose { overlay?.isVisible = false }
    }

    BackHandler { onCancel() }

    val targetColor =
        if (target == PinTarget.ORIGIN) MoyeotaColor.MarkerOrigin else MoyeotaColor.MarkerDestination
    val targetName = if (target == PinTarget.ORIGIN) originName else destinationName

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MoyeotaColor.SurfaceCanvas)
            // 아래(16 모달)로 탭이 새지 않게 흡수한다
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {},
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 헤더 + 대상 토글
            Column(modifier = Modifier.fillMaxWidth().background(MoyeotaColor.SurfaceCanvas)) {
                StatusBarSpacer()
                Row(
                    modifier = Modifier.fillMaxWidth().height(56.dp).padding(start = 12.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onCancel) { BackArrowIcon() }
                    Text(
                        text = "위치 조정",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MoyeotaColor.InkPrimary,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // 좌표가 없는 대상은 조정할 것이 없으므로 칩 자체를 내지 않는다
                    if (originPosition != null) {
                        ConditionChip(
                            text = "출발지",
                            selected = target == PinTarget.ORIGIN,
                            onClick = { target = PinTarget.ORIGIN },
                            wide = true,
                        )
                    }
                    if (destinationPosition != null) {
                        ConditionChip(
                            text = "도착지",
                            selected = target == PinTarget.DESTINATION,
                            onClick = { target = PinTarget.DESTINATION },
                            wide = true,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // 지도 + 중앙 고정 핀
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                RouteMapView(
                    modifier = Modifier.fillMaxSize(),
                    // 조정 중인 쪽은 중앙 핀이 대신하므로 마커를 띄우지 않는다(핀과 겹쳐 보인다).
                    // 반대쪽 마커는 남겨 둬야 두 지점 관계를 보면서 조정할 수 있다.
                    originPosition = if (target == PinTarget.ORIGIN) null else originWorking.value,
                    destinationPosition =
                        if (target == PinTarget.DESTINATION) null else destinationWorking.value,
                    center = anchor,
                    zoom = AdjustZoom,
                    useTextureView = true, // 16 이 dialog() 목적지 — 04 리포트와 같은 이유
                    onMapReady = { map = it },
                )

                // 핀이 가리키는 지점. 핀이 떠 있는 동안에도 이 점은 제자리에 남아 기준이 된다
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(8.dp)
                        .background(targetColor.copy(alpha = 0.25f), CircleShape),
                )
                // 핀 끝이 지도 중심(= 카메라 target)에 오도록 자기 높이의 절반만큼 올린다
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = -PinHeight / 2),
                ) {
                    CenterPin(color = targetColor, lifted = { moving.value })
                }
            }

            // 하단 확정 패널
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(14.dp, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), spotColor = Color(0x141B2A4A))
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(MoyeotaColor.SurfaceCanvas)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(11.dp).background(targetColor, CircleShape))
                    Spacer(Modifier.size(10.dp))
                    Text(
                        text = targetName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MoyeotaColor.InkPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(4.dp))
                CoordinateLabel(
                    position = {
                        if (currentTarget == PinTarget.ORIGIN) originWorking.value
                        else destinationWorking.value
                    },
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    // 1줄에 맞춘 길이 — 「…갱신돼 / 요」처럼 마지막 글자만 떨어지면 지저분하다
                    text = "지도를 움직여 핀을 맞춰 주세요 · 설정하면 주소가 갱신돼요",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = GrayAsh,
                )
                Spacer(Modifier.height(12.dp))
                PrimaryCtaButton(
                    text = "이 위치로 설정",
                    onClick = { onConfirm(originWorking.value, destinationWorking.value) },
                )
                NavigationBarSpacer()
            }
        }
    }
}

/**
 * 화면 중앙 고정 핀. [lifted] 는 **지연 읽기** — 카메라 이동 상태를 이 컴포저블 안에서만
 * 구독해 팬 중에 상위 화면이 재구성되지 않게 한다.
 */
@Composable
private fun CenterPin(color: Color, lifted: () -> Boolean) {
    val lift by animateDpAsState(
        targetValue = if (lifted()) PinLift else 0.dp,
        label = "pinLift",
    )
    Canvas(
        modifier = Modifier
            .offset(y = -lift)
            .size(width = PinWidth, height = PinHeight),
    ) {
        val w = size.width
        val h = size.height
        val headRadius = w / 2f
        val headCenter = Offset(w / 2f, headRadius)
        // 물방울 꼬리 — 머리 원 아래를 삼각형으로 이어 끝점이 정확히 (중앙, 바닥)
        val tail = Path().apply {
            moveTo(w / 2f - headRadius * 0.62f, headRadius + headRadius * 0.62f)
            lineTo(w / 2f, h)
            lineTo(w / 2f + headRadius * 0.62f, headRadius + headRadius * 0.62f)
            close()
        }
        drawPath(tail, color)
        drawCircle(color, radius = headRadius, center = headCenter)
        drawCircle(Color.White, radius = headRadius * 0.38f, center = headCenter)
    }
}

/** 핀 아래 좌표. [position] 도 지연 읽기라 팬 중에는 이 텍스트만 재구성된다. */
@Composable
private fun CoordinateLabel(position: () -> LatLng?) {
    val value = position()
    Text(
        text = value?.let { "%.6f, %.6f".format(it.latitude, it.longitude) } ?: "좌표 없음",
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = GrayMute,
    )
}
