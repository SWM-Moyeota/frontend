package com.moyeota.presentation.feature.explore

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.unit.sp
import com.moyeota.core.designsystem.component.AvatarCircle
import com.moyeota.core.designsystem.component.MoyeotaBottomBar
import com.moyeota.core.designsystem.component.MoyeotaTab
import com.moyeota.core.designsystem.component.NaverMapView
import com.moyeota.core.designsystem.component.SheetHandle
import com.moyeota.core.designsystem.component.StatusBarSpacer
import com.moyeota.core.designsystem.component.latLngOrNull
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RideStatus
import com.moyeota.domain.model.User
import com.moyeota.presentation.core.ActiveRideBanner
import com.moyeota.presentation.feature.home.DemoOrigin
import com.naver.maps.geometry.LatLng
import com.naver.maps.geometry.LatLngBounds
import com.naver.maps.map.NaverMap
import com.naver.maps.map.overlay.LocationOverlay
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.util.MarkerIcons

// 와이어프레임 그레이 (core token 미정의 색 — 화면 재현용)
private val CanvasBg = Color(0xFFF5F7FA)
private val GrayMute = Color(0xFF8A93A0)
private val GrayAsh = Color(0xFF9AA1AC)
private val ChipBorder = Color(0xFFE2E7EE)
private val BadgeGrayBg = Color(0xFFF3F6FA)

/** 바텀시트 3단계 — 17 지도(peek) / 18 지도+리스트(half) / 19 리스트(full) */
enum class ExploreSheetState { PEEK, HALF, FULL }

/**
 * 지도에 그릴 기기 실위치 1건.
 *
 * 좌표만으로는 「얼마나 믿을 수 있는 위치인지」를 그릴 수 없어 오차 반경·방향을 함께 받는다.
 * 세 값이 항상 같은 fix 에서 나와야 원과 점이 어긋나지 않으므로 하나로 묶는다.
 *
 * @param accuracyMeters 수평 오차 반경(m). null 이면 정확도 원을 그리지 않는다
 * @param bearingDegrees 진행 방향(도, 0 = 북). null 이면 방향 표시 없이 점만 그린다
 */
@Immutable
data class MyLocationFix(
    val position: LatLng,
    val accuracyMeters: Float? = null,
    val bearingDegrees: Float? = null,
)

/**
 * 지도에 지금 보이는 영역 — 방 목록 조회 범위.
 *
 * 필드 순서를 서버 파라미터 순서(swLat → swLng → neLat → neLng)와 맞춰 둔다.
 * 위도·경도가 뒤바뀌어도 서버는 오류가 아니라 **빈 목록**을 돌려주므로,
 * 순서를 틀리면 「이 근처엔 방이 없네」로 보여 발견이 늦는다.
 */
@Immutable
data class MapBounds(
    val swLat: Double,
    val swLng: Double,
    val neLat: Double,
    val neLng: Double,
)

/** 지도 카메라 1건 (중심 + 줌) */
@Immutable
private data class MapCamera(val center: LatLng, val zoom: Double)

/**
 * 지도 카메라 — **화면 레벨**에서 들고 있는다.
 *
 * 지도는 이제 시트 단계와 무관하게 하나지만, **탭 전환·회전**으로는 여전히 다시 만들어진다.
 * 카메라를 지도 안에만 두면 그때마다 기준점으로 되돌아가 보던 목록이 통째로 사라진다.
 */
@Stable
private class ExploreCameraState(
    initialCamera: MapCamera?,
    initialCenteredOnMyLocation: Boolean,
) {
    /** 마지막으로 본 카메라. null 이면 아직 지도가 한 번도 뜨지 않은 상태 */
    var camera by mutableStateOf(initialCamera)

    /** 첫 실위치로 한 번 옮겼는가. 매 fix 마다 따라가면 사용자가 지도를 팬할 수 없다 */
    var centeredOnMyLocation by mutableStateOf(initialCenteredOnMyLocation)
}

// 탭 전환·회전에도 보던 위치를 유지한다. 카메라가 아직 없으면 NaN 으로 저장한다
private val ExploreCameraStateSaver: Saver<ExploreCameraState, Any> =
    listSaver<ExploreCameraState, Double>(
        save = { state ->
            val camera = state.camera
            listOf(
                camera?.center?.latitude ?: Double.NaN,
                camera?.center?.longitude ?: Double.NaN,
                camera?.zoom ?: Double.NaN,
                if (state.centeredOnMyLocation) 1.0 else 0.0,
            )
        },
        restore = { saved ->
            ExploreCameraState(
                initialCamera = if (saved[0].isNaN()) null else MapCamera(LatLng(saved[0], saved[1]), saved[2]),
                initialCenteredOnMyLocation = saved[3] == 1.0,
            )
        },
    )

// 더미 파티 (19 와이어프레임 카드 그대로)
private fun dummyUser(id: String, name: String) = User(id, name, "학교 인증", 4.9, 12)

private val DefaultParties = listOf(
    Ride("ride-1", "부산대 정문", "서면역", "3분 후 출발 예정 · 6.2km", 3, listOf(dummyUser("u1", "김OO"), dummyUser("u2", "이OO")), 3600, 9600, RideStatus.RECRUITING),
    Ride("ride-2", "부산대 정문", "사상역", "6분 후 출발 예정 · 8.4km", 3, listOf(dummyUser("u3", "박OO"), dummyUser("u4", "최OO")), 4200, 12600, RideStatus.RECRUITING),
    Ride("ride-3", "장전역", "서면역", "8분 후 출발 예정 · 5.1km", 3, listOf(dummyUser("u5", "정OO"), dummyUser("u6", "한OO")), 3200, 9600, RideStatus.RECRUITING),
    Ride("ride-4", "부산대 정문", "부산역", "10분 후 출발 예정 · 11km", 3, listOf(dummyUser("u7", "조OO")), 5400, 16200, RideStatus.RECRUITING),
    Ride("ride-5", "온천장역", "서면역", "12분 후 출발 예정 · 7.3km", 2, listOf(dummyUser("u8", "윤OO")), 4800, 9600, RideStatus.RECRUITING),
)

// 「여성만」 방 (Ride 도메인 모델에 없는 속성 — 카드 배지 재현용)
private val FemaleOnlyRideIds = setOf("ride-1", "ride-3", "ride-5")

/** 접힘(17) 시트 높이 — 핸들 + 한 줄 요약 */
private val PeekSheetHeight = 96.dp

/** 반펼침(18)에서 시트 위로 남기는 지도 높이 */
private val HalfMapHeight = 300.dp

/** 시트 앵커 사이를 오가는 정착 애니메이션 */
private const val SheetAnimMs = 260

/** 이 속도(px/s) 이상으로 던지면 위치와 무관하게 그 방향의 다음 앵커로 붙는다 (MapSheetScaffold 와 같은 값) */
private const val SheetFlingVelocity = 400f

/**
 * 목록 헤더와 첫 카드 사이 간격 — 18·19 공통.
 */
private val ListHeaderGap = 12.dp

/**
 * 17·18·19 · 합승 — 내 주변 [V07/V07b/V07c]
 *
 * **지도는 하나다.** 시트 단계(PEEK/HALF/FULL)는 지도 위에 얹힌 시트의 **높이**만 바꾼다 —
 * 예전에는 단계마다 다른 컴포저블(전체 지도 / 320dp 지도 / 지도 없음)로 갈아끼워서
 * 단계를 바꿀 때마다 지도가 새로 만들어지고(타일 재로드·카메라 복원) 전환 애니메이션도 없었다(실기 QA).
 * 지금은 시트 높이가 앵커 사이를 애니메이션으로 오가고, 지도는 `contentPadding` 만 따라 바뀐다.
 *
 * 시트 상태 전환:
 * - 핸들·헤더를 **드래그**하면 가까운 앵커(또는 던진 방향의 다음 앵커)로 정착 — 세 단계 모두
 * - PEEK 의 「⌃」 탭 → HALF · HALF/FULL 헤더의 「지도 접기 ⌄ / 지도 펼치기 ⌃」 → FULL/HALF
 *   (접기·펼치기는 **이 한 버튼**뿐이다 — 타이틀 행의 「🗺 지도」는 행 높이를 흔들어 뺐다)
 *
 * 이동(디스크립션):
 * - 「{단계} · {목적지} 보기 ›」 배너 → 진행 중인 방의 단계 화면 21/25/26 (onOngoingRideClick)
 * - 지도 마커 탭 / 카드 「합류」 → 20 합류 확인 (onJoinParty)
 * - 「＋ 새 합승 방 만들기」 → 15 목적지 입력 (onCreateRoomClick)
 * - 하단탭 홈 / 채팅 / 마이 → 14 / 24 / 35 (onTabSelect)
 *
 * 검증·상태:
 * - 방 목록은 **지도에 보이는 범위**로 조회한 결과다([onVisibleBoundsChange]) — 마커·리스트·**헤더의 N개**가 같은 목록
 * - 지도는 네이버 실지도. 내 위치는 기기 GPS([myLocation]) — 못 받으면 [DemoOrigin] 기준점으로 폴백
 * - 위치 권한 없으면 지도 대신 권한 요청 안내 + 「위치 권한 허용」 버튼 (locationGranted)
 * - 후보 0건이면 peek 문구 자리에 빈 상태 + 지도를 움직여 보라는 안내
 * - 진행 중 탑승 없으면 상단 배너 숨김 ([activeRide] 가 null). 배너가 있으면 지도 줌 컨트롤이 그 아래로 내려간다
 * - 정원 찬 방(3/3)은 「합류」 비활성 + 「마감」 표기
 */
@Composable
fun ExploreScreen(
    parties: List<Ride> = DefaultParties,
    /** 지금 진행 중인 내 방. null 이면 상단 배너를 그리지 않는다 */
    activeRide: Ride? = null,
    locationGranted: Boolean = true,
    // 기기 실위치. null = 권한 없음 · 아직 fix 없음 → 지도는 DemoOrigin 기준점으로 떨어진다
    myLocation: MyLocationFix? = null,
    initialSheetState: ExploreSheetState = ExploreSheetState.PEEK,
    onJoinParty: (Ride) -> Unit = {},
    onOngoingRideClick: () -> Unit = {},
    onCreateRoomClick: () -> Unit = {},
    onRequestLocationPermission: () -> Unit = {},
    // 지도 카메라가 멈출 때마다 보이는 영역을 올려보낸다 → 그 범위의 방 목록으로 갱신
    onVisibleBoundsChange: (MapBounds) -> Unit = {},
    onTabSelect: (MoyeotaTab) -> Unit = {},
) {
    var sheetState by rememberSaveable { mutableStateOf(initialSheetState) }
    // 탭 전환·회전에도 보던 자리를 유지한다 (시트 단계 변경으로는 이제 지도가 재생성되지 않는다)
    val cameraState = rememberSaveable(saver = ExploreCameraStateSaver) {
        ExploreCameraState(initialCamera = null, initialCenteredOnMyLocation = false)
    }
    Column(modifier = Modifier.fillMaxSize().background(CanvasBg)) {
        StatusBarSpacer()

        // 타이틀 행 — **고정 높이**. 단계에 따라 버튼이 생기고 사라지면 행 높이가 흔들려 제목이 움직였다
        Box(
            modifier = Modifier.fillMaxWidth().height(TitleRowHeight).padding(horizontal = 24.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = "합승 — 내 주변",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MoyeotaColor.InkPrimary,
            )
        }

        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val density = LocalDensity.current
            val containerHeight = maxHeight
            // 앵커 = 시트 높이. HALF 는 지도 300dp 를 남기고, FULL 은 지도를 다 덮는다
            val anchorDp: (ExploreSheetState) -> Dp = { state ->
                when (state) {
                    ExploreSheetState.PEEK -> PeekSheetHeight
                    ExploreSheetState.HALF -> (containerHeight - HalfMapHeight).coerceAtLeast(PeekSheetHeight)
                    ExploreSheetState.FULL -> containerHeight
                }
            }
            val anchorPx: (ExploreSheetState) -> Float = { state -> with(density) { anchorDp(state).toPx() } }

            val sheetPx = remember { Animatable(anchorPx(sheetState)) }
            // 단계가 바뀌면(탭·드래그 정착·회전) 그 앵커로 애니메이션
            LaunchedEffect(sheetState, containerHeight) {
                sheetPx.animateTo(anchorPx(sheetState), tween(SheetAnimMs))
            }
            // 드래그 중에는 이 값만 따라간다 (MapSheetScaffold 와 같은 이유 — 매 프레임 snapTo 는 정착을 끊는다)
            var dragPx by remember { mutableStateOf<Float?>(null) }
            val sheetHeight = with(density) { (dragPx ?: sheetPx.value).toDp() }
            val scope = rememberCoroutineScope()
            val dragState = rememberDraggableState { delta ->
                dragPx = ((dragPx ?: sheetPx.value) - delta)
                    .coerceIn(anchorPx(ExploreSheetState.PEEK), anchorPx(ExploreSheetState.FULL))
            }

            // 진행 배너 높이 — 지도 위 contentPadding 으로 넘겨 줌 컨트롤(+/−)이 배너 아래로 내려가게 한다
            // (작은 폰에서 배너와 줌 버튼이 겹치던 문제)
            var bannerHeightPx by remember { mutableIntStateOf(0) }
            val bannerHeight = with(density) { bannerHeightPx.toDp() }

            // 배경 지도 — 단계가 바뀌어도 같은 인스턴스. 시트에 덮이는 높이만 contentPadding 으로 알린다.
            // FULL 에서는 지도가 통째로 가려지므로 HALF 기준으로 고정한다(패딩이 뷰 높이를 넘으면 안 된다)
            val mapBottomInset = minOf(anchorDp(sheetState), anchorDp(ExploreSheetState.HALF))
            if (locationGranted) {
                ExploreMap(
                    rides = parties,
                    myLocation = myLocation,
                    cameraState = cameraState,
                    onMarkerClick = onJoinParty,
                    onVisibleBoundsChange = onVisibleBoundsChange,
                    contentPadding = PaddingValues(top = bannerHeight, bottom = mapBottomInset),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LocationPermissionNotice(
                    onRequestPermission = onRequestLocationPermission,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            if (activeRide != null) {
                ActiveRideBanner(
                    ride = activeRide,
                    onClick = onOngoingRideClick,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .onSizeChanged { bannerHeightPx = it.height }
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }

            // 시트 — 높이만 앵커 사이를 오간다. 내용은 **목표 단계** 기준이라 애니메이션 중에 흔들리지 않는다.
            // FULL 에서는 모서리를 각지게 — 둥근 모서리 틈으로 뒤의 지도가 비친다
            val sheetShape = if (sheetState == ExploreSheetState.FULL) RoundedCornerShape(0.dp)
            else RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(sheetHeight)
                    .shadow(14.dp, sheetShape, spotColor = Color(0x1A000000))
                    .clip(sheetShape)
                    .background(MoyeotaColor.SurfaceCanvas),
            ) {
                // 드래그 타깃 = 핸들 + 그 아래 한 줄(요약 또는 헤더)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .draggable(
                            state = dragState,
                            orientation = Orientation.Vertical,
                            onDragStopped = { velocity ->
                                val from = dragPx ?: sheetPx.value
                                val target = settleTarget(from, velocity, anchorPx)
                                scope.launch {
                                    sheetPx.snapTo(from)
                                    dragPx = null
                                    // 같은 단계로 돌아가면 LaunchedEffect 가 다시 돌지 않으므로 직접 정착시킨다
                                    if (target == sheetState) sheetPx.animateTo(anchorPx(target), tween(SheetAnimMs))
                                    else sheetState = target
                                }
                            },
                        ),
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        SheetHandle()
                    }
                    if (sheetState == ExploreSheetState.PEEK) {
                        PeekSummaryRow(count = parties.size, onRaise = { sheetState = ExploreSheetState.HALF })
                    } else {
                        ListHeaderRow(
                            count = parties.size,
                            mapShown = sheetState == ExploreSheetState.HALF,
                            onToggleMap = {
                                sheetState = if (sheetState == ExploreSheetState.HALF) ExploreSheetState.FULL else ExploreSheetState.HALF
                            },
                        )
                    }
                }

                if (sheetState != ExploreSheetState.PEEK) {
                    Spacer(Modifier.height(ListHeaderGap))
                    if (sheetState == ExploreSheetState.FULL) {
                        HorizontalDivider(color = ChipBorder)
                        Spacer(Modifier.height(ListHeaderGap))
                    }
                    PartyList(
                        parties = parties,
                        onJoinParty = onJoinParty,
                        showEndOfList = sheetState == ExploreSheetState.FULL,
                        modifier = Modifier.weight(1f),
                    )
                    CreateRoomButton(
                        onClick = onCreateRoomClick,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                    )
                }
            }
        }

        MoyeotaBottomBar(selected = MoyeotaTab.EXPLORE, onSelect = onTabSelect)
    }
}

/** 타이틀 행 고정 높이 — 예전 「🗺 지도」 버튼(32dp) + 세로 여백(6dp×2) 과 같은 값이라 레이아웃이 그대로다 */
private val TitleRowHeight = 44.dp

/**
 * 손을 뗀 위치·속도로 정착할 앵커. 빠르게 던지면 그 방향의 **다음** 앵커, 아니면 가장 가까운 앵커.
 */
private fun settleTarget(
    fromPx: Float,
    velocity: Float,
    anchorPx: (ExploreSheetState) -> Float,
): ExploreSheetState {
    val ordered = ExploreSheetState.entries.sortedBy(anchorPx)
    return when {
        // 아래로 던짐 → 현재 위치보다 낮은 앵커 중 가장 가까운 것
        velocity > SheetFlingVelocity -> ordered.lastOrNull { anchorPx(it) < fromPx - 1f } ?: ordered.first()
        velocity < -SheetFlingVelocity -> ordered.firstOrNull { anchorPx(it) > fromPx + 1f } ?: ordered.last()
        else -> ordered.minBy { kotlin.math.abs(anchorPx(it) - fromPx) }
    }
}

/** 17 접힘 요약 — 「주변 합승 N개」. N 은 헤더·마커와 같은 목록(지도 범위)의 방 수다 */
@Composable
private fun PeekSummaryRow(count: Int, onRaise: () -> Unit) {
    val isEmpty = count == 0
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!isEmpty) {
            Box(Modifier.size(10.dp).background(MoyeotaColor.Primary500, CircleShape))
            Spacer(Modifier.width(8.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isEmpty) "이 근처엔 합승이 없어요" else "주변 합승 ${count}개 모집 중",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = MoyeotaColor.InkPrimary,
            )
            Text(
                text = if (isEmpty) "지도를 움직여 보세요" else "위로 올리면 리스트, 목적지 정하면 바로 자동 매칭",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = GrayMute,
            )
        }
        ChevronUpIcon(
            color = GrayAsh,
            modifier = Modifier.clickable { onRaise() },
        )
    }
}

/** 18·19 목록 헤더 — 「주변 합승 N개 · 가까운 순」 + 지도 접기/펼치기 **단일** 토글 */
@Composable
private fun ListHeaderRow(count: Int, mapShown: Boolean, onToggleMap: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "주변 합승 ${count}개 · 가까운 순",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = MoyeotaColor.InkPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (mapShown) "지도 접기 ⌄" else "지도 펼치기 ⌃",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MoyeotaColor.Primary500,
            modifier = Modifier.clickable { onToggleMap() },
        )
    }
}

// ─── 공용 조각 ──────────────────────────────────────────────────────────────

@Composable
private fun PartyList(
    parties: List<Ride>,
    onJoinParty: (Ride) -> Unit,
    showEndOfList: Boolean,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(parties.size) { index ->
            PartyCard(ride = parties[index], onJoin = onJoinParty)
        }
        if (parties.isEmpty()) {
            item { EmptyListNotice() }
        } else if (showEndOfList) {
            // 스펙 19: 마지막 페이지면 목록의 끝을 알린다 (범위 기준)
            item {
                Text(
                    text = "이 근처는 여기까지예요",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = GrayAsh,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun PartyCard(
    ride: Ride,
    onJoin: (Ride) -> Unit,
    modifier: Modifier = Modifier,
) {
    val full = ride.members.size >= ride.capacity
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MoyeotaColor.SurfaceCanvas, RoundedCornerShape(16.dp))
            .border(1.dp, ChipBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarStack(count = ride.members.size)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            // 출발지·목적지를 라벨 붙여 두 줄로 — 「A → B」 한 줄은 주소가 길면 어디서 갈리는지 읽기 어렵다
            PlaceLine(label = "출발지", place = ride.origin)
            PlaceLine(label = "목적지", place = ride.destination)
            Text(
                text = ride.departureLabel,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = GrayMute,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CapacityBadge(text = "${ride.members.size}/${ride.capacity}명")
                if (ride.id in FemaleOnlyRideIds) {
                    GrayBadge(text = "여성만")
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        JoinButton(full = full, onClick = { onJoin(ride) })
    }
}

/** 「출발지 : 장소」 한 줄. 라벨은 고정 폭이라 두 줄의 장소가 같은 x 에서 시작한다 */
@Composable
private fun PlaceLine(label: String, place: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = "$label :",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = GrayMute,
            modifier = Modifier.width(52.dp),
        )
        Text(
            text = place,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MoyeotaColor.InkPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AvatarStack(count: Int) {
    val shown = count.coerceAtLeast(1).coerceAtMost(3)
    Box(modifier = Modifier.width(30.dp + 18.dp * (shown - 1)).height(30.dp)) {
        repeat(shown) { i ->
            AvatarCircle(size = 30.dp, modifier = Modifier.offset(x = 18.dp * i))
        }
    }
}

@Composable
private fun CapacityBadge(text: String) {
    Box(
        modifier = Modifier
            .background(MoyeotaColor.Waiting500, CircleShape)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(text = text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MoyeotaColor.TextOnDark)
    }
}

@Composable
private fun GrayBadge(text: String) {
    Box(
        modifier = Modifier
            .background(BadgeGrayBg, CircleShape)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(text = text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GrayMute)
    }
}

// 정원 찬 방(3/3)은 「합류」 비활성 + 「마감」 표기 (스펙 18)
@Composable
private fun JoinButton(full: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    if (full) {
        Box(
            modifier = Modifier
                .size(width = 48.dp, height = 34.dp)
                .background(MoyeotaColor.SurfaceSoft, shape),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "마감", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = GrayAsh)
        }
    } else {
        Box(
            modifier = Modifier
                .size(width = 48.dp, height = 34.dp)
                .clip(shape)
                .background(MoyeotaColor.Primary500)
                .clickable { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "합류", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MoyeotaColor.TextOnDark)
        }
    }
}

@Composable
private fun CreateRoomButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MoyeotaColor.Primary500)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "＋ 새 합승 방 만들기",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MoyeotaColor.TextOnDark,
        )
    }
}

// 위치 권한 없을 때 지도 대신 안내 (스펙 17).
// 자동 요청을 한 번 거부한 뒤에도 여기서 다시 요청할 수 있어야 지도로 돌아올 길이 남는다
// (영구 거부 상태면 시스템이 다이얼로그 없이 바로 거부를 돌려주고 안내는 그대로 유지된다).
@Composable
private fun LocationPermissionNotice(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(MoyeotaColor.SurfaceSoft),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "위치 권한이 필요해요",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MoyeotaColor.InkPrimary,
            )
            Text(
                text = "권한을 허용하면 내 주변 합승을 지도로 볼 수 있어요",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = GrayMute,
            )
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .height(36.dp)
                    .clip(CircleShape)
                    .background(MoyeotaColor.Primary500)
                    .clickable { onRequestPermission() }
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "위치 권한 허용",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MoyeotaColor.SurfaceCanvas,
                )
            }
        }
    }
}

@Composable
private fun EmptyListNotice() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "이 근처엔 합승이 없어요",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MoyeotaColor.InkPrimary,
        )
        Text(
            text = "지도를 움직여 보세요",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = GrayMute,
        )
    }
}

// ─── 지도 (네이버 실지도 + 내 위치·합승 마커) ────────────────────────────────

/**
 * 실위치를 못 받았을 때의 기준점.
 *
 * 권한 거부·GPS 미취득 상황에서도 지도가 서울 한복판(SDK 기본 카메라)으로 튀지 않게,
 * 앱 공통 컨벤션인 [DemoOrigin](부산대 정문 · 15 목적지 입력과 동일 좌표)으로 떨어진다.
 * 실위치가 아니므로 마커 캡션도 「내 위치(기준점)」으로 구분한다.
 */
private val FallbackOrigin = LatLng(DemoOrigin.latitude, DemoOrigin.longitude)

/** 내 주변 1km 남짓이 한 화면에 들어오는 줌 — 첫 범위 조회가 이 정도를 훑는다 */
private const val ExploreZoom = 15.0

/**
 * 지도가 아직 범위를 알려주지 못할 때 쓰는 기준 범위의 반변(도).
 *
 * [ExploreZoom] 의 실제 가시 영역(Pixel 6 실측 위도 ±0.006 · 경도 ±0.004)에 맞춘 값이다.
 * 지도가 뜨면 곧바로 실제 [NaverMap.getContentBounds] 로 덮어쓰지만, 그 사이에 「지도에 없는
 * 방」이 리스트에 잠깐 떴다 사라지지 않도록 처음부터 비슷한 크기로 묻는다.
 */
private const val DefaultBoundsHalfSpanDeg = 0.006

/**
 * 기준점 중심의 조회 범위.
 *
 * [center] 가 null(위치 권한 거부·아직 fix 없음)이면 [FallbackOrigin](부산대 정문)을 쓴다 —
 * 첫 조회가 서울 한복판을 훑고 빈 목록을 보여주는 것보다 낫다.
 */
fun defaultExploreBounds(center: LatLng?): MapBounds {
    val origin = center ?: FallbackOrigin
    return MapBounds(
        swLat = origin.latitude - DefaultBoundsHalfSpanDeg,
        swLng = origin.longitude - DefaultBoundsHalfSpanDeg,
        neLat = origin.latitude + DefaultBoundsHalfSpanDeg,
        neLng = origin.longitude + DefaultBoundsHalfSpanDeg,
    )
}

/**
 * 합승 탐색 지도 — 내 위치를 중심으로 한 네이버 실지도.
 *
 * 지도는 화면의 **바닥 레이어**다. 위에 얹히는 배너·시트는 자기 영역에서만 터치를 소비하므로
 * 남는 영역의 팬·줌은 그대로 지도로 전달된다 (홈 14 와 동일한 처리).
 *
 * 합승 마커는 서버가 좌표를 준 방만 찍는다 — 좌표가 없거나 범위를 벗어난 값은
 * [latLngOrNull] 이 걸러 렌더를 건너뛴다. 마커가 없어도 리스트의 「합류」로 같은 곳에 갈 수 있다.
 *
 * @param myLocation 기기 실위치. null 이면 [FallbackOrigin] 기준점으로 그린다
 * @param onVisibleBoundsChange 카메라가 멈출 때 보이는 영역. 이 범위로 방 목록을 다시 읽는다
 * @param contentPadding 시트가 지도를 덮는 영역. 카메라 중심이 시트 뒤로 밀리지 않게 한다
 */
@Composable
private fun ExploreMap(
    rides: List<Ride>,
    myLocation: MyLocationFix?,
    cameraState: ExploreCameraState,
    onMarkerClick: (Ride) -> Unit,
    onVisibleBoundsChange: (MapBounds) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    var map by remember { mutableStateOf<NaverMap?>(null) }
    val myPosition = myLocation?.position

    // [NaverMapView] 에 넘기는 카메라는 **이 지도 인스턴스를 만들 때 한 번** 정하고 그 뒤로는
    // 사용자 조작이 주인이다. idle 로 읽은 카메라를 그대로 center 로 돌려주면 안 된다 —
    // SDK 가 돌려주는 좌표가 우리가 넣은 값과 부동소수점 끝자리만큼 달라
    // 「적용 → idle → 적용」이 끝없이 돌고, 매 idle 이 조회를 재시작시켜 목록이 영영 갱신되지 않는다.
    // 사용자가 움직인 카메라는 [cameraState] 에만 적어 두고, 시트 단계가 바뀌어 지도가
    // 새로 만들어질 때 그 값에서 시작한다.
    var mapCamera by remember {
        mutableStateOf(cameraState.camera ?: MapCamera(myPosition ?: FallbackOrigin, ExploreZoom))
    }

    // 카메라를 **처음 잡힌 실위치로 한 번만** 옮긴다.
    // 주기 갱신(1초)마다 center 를 바꾸면 그때마다 카메라가 되돌아가 사용자가 지도를 팬·줌할 수
    // 없다 (RouteMapView 의 driverPosition 과 같은 이유). 파란 점은 계속 최신 좌표를 따라간다.
    // 플래그는 [cameraState] — 시트 단계가 바뀌어도 이미 옮겼다는 사실이 남아야 한다.
    LaunchedEffect(myPosition) {
        val fix = myPosition ?: return@LaunchedEffect
        if (!cameraState.centeredOnMyLocation) {
            cameraState.centeredOnMyLocation = true
            val moved = MapCamera(fix, ExploreZoom)
            cameraState.camera = moved
            mapCamera = moved // 이 값의 변경만이 실제로 카메라를 움직인다
        }
    }

    NaverMapView(
        modifier = modifier,
        center = mapCamera.center,
        zoom = mapCamera.zoom,
        contentPadding = contentPadding,
        onMapReady = { map = it },
    )

    MapIdleReporter(
        map = map,
        cameraState = cameraState,
        onVisibleBoundsChange = onVisibleBoundsChange,
    )

    if (myLocation != null) {
        // 실위치가 있으면 SDK 내장 위치 오버레이(파란 점 + 오차 원)를 쓴다 —
        // 일반 마커를 옮겨 찍는 것보다 네이버 지도 앱과 같은 인상을 준다
        MyLocationOverlay(map = map, fix = myLocation)
    } else {
        // 실위치가 아직 없을 때만 기준점 마커. 「내 위치」라고 단정하지 않는다
        ExploreMarker(
            map = map,
            position = FallbackOrigin,
            tint = MoyeotaColor.MarkerOrigin.toArgb(),
            caption = "내 위치(기준점)",
        )
    }
    rides.forEach { ride ->
        // key 로 묶어야 범위 조회로 목록이 바뀔 때 마커 상태가 옆 항목으로 밀리지 않는다
        key(ride.id) {
            ExploreMarker(
                map = map,
                position = latLngOrNull(ride.originLat, ride.originLng),
                tint = MoyeotaColor.Primary500.toArgb(),
                caption = "${ride.members.size}명",
                onClick = { onMarkerClick(ride) }, // 마커 탭 → 20 합류 확인
            )
        }
    }
}

/**
 * 카메라가 멈출 때마다 「지금 보이는 영역」과 카메라 위치를 위로 올려보내는 부분.
 *
 * 카메라가 **멈췄을 때**만(`addOnCameraIdleListener`) 보고한다 — 드래그 중 매 프레임 보고하면
 * 손가락을 떼기도 전에 조회가 수십 번 나간다.
 *
 * 범위는 [NaverMap.getContentBounds] 로 읽는다. 뷰 크기 기준이 아니라
 * **contentPadding 을 뺀 실제로 보이는 영역**이라, 시트에 가린 방이 목록에 섞이지 않는다.
 *
 * 지도가 준비된 직후에도 한 번 보고한다. idle 은 「카메라가 움직였다 멈춤」에만 오므로,
 * 사용자가 지도를 건드리지 않으면 첫 이벤트가 영영 오지 않을 수 있다.
 */
@Composable
private fun MapIdleReporter(
    map: NaverMap?,
    cameraState: ExploreCameraState,
    onVisibleBoundsChange: (MapBounds) -> Unit,
) {
    val currentCallback by rememberUpdatedState(onVisibleBoundsChange)
    DisposableEffect(map) {
        val naverMap = map
        if (naverMap == null) {
            onDispose {}
        } else {
            val report = {
                val position = naverMap.cameraPosition
                cameraState.camera = MapCamera(position.target, position.zoom)
                naverMap.contentBounds.toMapBoundsOrNull()?.let { currentCallback(it) }
                Unit
            }
            report()
            val listener = NaverMap.OnCameraIdleListener { report() }
            naverMap.addOnCameraIdleListener(listener)
            onDispose { naverMap.removeOnCameraIdleListener(listener) }
        }
    }
}

/**
 * 조회에 쓸 수 있는 범위인지 확인하고 변환한다.
 *
 * 지도가 아직 레이아웃되지 않은 순간에는 두 모서리가 같은 점이거나 범위 밖 좌표가 나온다.
 * 그대로 조회하면 항상 빈 목록이 돌아와 「주변에 방이 없다」로 잘못 보이므로, 거르고
 * 다음 idle 을 기다린다.
 */
private fun LatLngBounds.toMapBoundsOrNull(): MapBounds? {
    if (!southWest.isValid || !northEast.isValid) return null
    if (southWest.latitude >= northEast.latitude || southWest.longitude >= northEast.longitude) return null
    return MapBounds(
        swLat = southWest.latitude,
        swLng = southWest.longitude,
        neLat = northEast.latitude,
        neLng = northEast.longitude,
    )
}

/** 지도 마커 하나. 오버레이는 `map` 프로퍼티로 붙고 떨어지므로 값이 바뀌면 떼었다 다시 붙인다 */
@Composable
private fun ExploreMarker(
    map: NaverMap?,
    position: LatLng?,
    tint: Int,
    caption: String,
    onClick: (() -> Unit)? = null,
) {
    // 콜백은 최신 참조 유지 — 이펙트 key 에 넣으면 람다가 새로 생길 때마다 마커가 재생성된다
    val currentOnClick by rememberUpdatedState(onClick)
    DisposableEffect(map, position, tint, caption) {
        val marker = if (map != null && position != null) {
            Marker().apply {
                this.position = position
                icon = MarkerIcons.BLACK
                iconTintColor = tint
                captionText = caption
                setOnClickListener {
                    val handler = currentOnClick
                    handler?.invoke()
                    handler != null // 처리한 클릭만 소비 (내 위치 마커는 지도로 흘려보낸다)
                }
                this.map = map
            }
        } else {
            null
        }
        onDispose { marker?.map = null }
    }
}

// ─── 내 위치 오버레이 (네이버 SDK 내장 파란 점) ────────────────────────────────

/** 새 좌표까지 미끄러져 가는 시간. 갱신 주기(1초)보다 짧아야 다음 fix 전에 도착한다 */
private const val MyLocationGlideMs = 800

/** 이 이상 튀면 보간하지 않고 순간이동한다 — 지도를 가로질러 기어가는 점이 더 이상하다 */
private const val MyLocationSnapDistanceM = 200.0

/** 이 이하의 미세 이동은 애니메이션 없이 반영한다 (GPS 지터로 계속 애니메이션이 걸리는 것 방지) */
private const val MyLocationMinMoveM = 0.5

/** 오차 원이 화면을 통째로 덮어 렌더러를 괴롭히지 않도록 두는 상한(px) */
private const val MaxAccuracyRadiusPx = 4000

/**
 * 기기 실위치 — 네이버 SDK 의 [LocationOverlay](파란 점 + 오차 원)로 그린다.
 *
 * 일반 [Marker] 를 옮겨 찍지 않는 이유: 위치 표시는 「지도 위의 어떤 지점」이 아니라
 * 「지금 내가 여기 있다」는 상태 표시라 SDK 가 그리는 모습이 사용자의 기대(네이버 지도 앱)와
 * 같아야 한다. 오버레이는 [NaverMap] 당 하나뿐이라 [NaverMap.getLocationOverlay] 로 얻어
 * 보이기/숨기기만 제어한다.
 *
 * `LocationTrackingMode`·`FusedLocationSource` 는 쓰지 않는다 — Activity 권한 콜백 포워딩을
 * 요구해 Compose 권한 런처와 맞지 않는다(07 리포트). 좌표는 `rememberMyLocationState` 가 준다.
 */
@Composable
private fun MyLocationOverlay(map: NaverMap?, fix: MyLocationFix) {
    // 1초마다 좌표를 그대로 찍으면 점이 뚝뚝 끊겨 「위치가 튄다」로 읽힌다.
    // 이전 좌표에서 새 좌표까지 보간해 미끄러지듯 이동시킨다.
    var rendered by remember { mutableStateOf(fix.position) }
    LaunchedEffect(fix.position) {
        val from = rendered
        val to = fix.position
        val moved = from.distanceTo(to)
        if (moved < MyLocationMinMoveM || moved > MyLocationSnapDistanceM || !moved.isFinite()) {
            rendered = to
            return@LaunchedEffect
        }
        // 등속 보간 — 걷는 사람의 이동은 가감속이 없어 보이는 편이 자연스럽다.
        // 다음 fix 가 오면 이 이펙트가 취소되고 현재 위치에서 새 목표로 다시 출발한다.
        Animatable(0f).animateTo(1f, tween(MyLocationGlideMs, easing = LinearEasing)) {
            rendered = LatLng(
                from.latitude + (to.latitude - from.latitude) * value,
                from.longitude + (to.longitude - from.longitude) * value,
            )
        }
    }

    // 오버레이는 지도에 붙어 있는 싱글턴이라 화면을 떠날 때 반드시 숨긴다.
    // 남겨 두면 같은 NaverMap 을 쓰는 다음 화면에 낡은 점이 그대로 뜬다.
    DisposableEffect(map) {
        map?.locationOverlay?.isVisible = true
        onDispose { map?.locationOverlay?.isVisible = false }
    }

    LaunchedEffect(map, rendered, fix.bearingDegrees) {
        val overlay = map?.locationOverlay ?: return@LaunchedEffect
        overlay.position = rendered
        // 방향을 모르는 fix 는 0도로 두고 화살표도 띄우지 않는다 — 없는 방향을 북쪽이라고
        // 그리면 사용자가 반대로 걸어간다
        overlay.bearing = fix.bearingDegrees ?: 0f
        overlay.subIcon =
            if (fix.bearingDegrees != null) LocationOverlay.DEFAULT_SUB_ICON_ARROW else null
    }

    // 오차 원 — 반경 단위가 픽셀이라 같은 오차(m)라도 줌에 따라 화면 크기가 달라진다.
    // 카메라가 움직일 때마다 다시 환산해야 원이 지면에 붙어 있는 것처럼 보인다.
    val accuracyMeters = fix.accuracyMeters
    DisposableEffect(map, accuracyMeters) {
        val naverMap = map
        if (naverMap == null) {
            onDispose {}
        } else {
            val overlay = naverMap.locationOverlay
            val applyRadius = {
                overlay.circleRadius = accuracyRadiusPx(naverMap, accuracyMeters)
            }
            applyRadius()
            val listener = NaverMap.OnCameraChangeListener { _, _ -> applyRadius() }
            naverMap.addOnCameraChangeListener(listener)
            onDispose { naverMap.removeOnCameraChangeListener(listener) }
        }
    }
}

@Composable
private fun ChevronUpIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 2.2.dp.toPx()
        drawLine(color, Offset(w * 0.2f, h * 0.62f), Offset(w * 0.5f, h * 0.34f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.8f, h * 0.62f), Offset(w * 0.5f, h * 0.34f), stroke, StrokeCap.Round)
    }
}

/**
 * 오차 반경(m)을 현재 줌의 화면 픽셀로 환산한다.
 *
 * 정확도를 모르면 [LocationOverlay.SIZE_AUTO](=0)를 돌려 원을 그리지 않는다 —
 * 모르는 오차를 임의의 크기로 그리면 실제보다 정확하거나 부정확해 보인다.
 */
private fun accuracyRadiusPx(map: NaverMap, accuracyMeters: Float?): Int {
    if (accuracyMeters == null || accuracyMeters <= 0f) return LocationOverlay.SIZE_AUTO
    val metersPerPixel = map.projection.metersPerPixel
    // 지도가 아직 레이아웃되지 않으면 0·NaN 이 나온다 — 다음 카메라 변화 때 다시 계산된다
    if (!metersPerPixel.isFinite() || metersPerPixel <= 0.0) return LocationOverlay.SIZE_AUTO
    return (accuracyMeters / metersPerPixel).toInt().coerceIn(0, MaxAccuracyRadiusPx)
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun ExploreScreenPeekPreview() {
    ExploreScreen(initialSheetState = ExploreSheetState.PEEK)
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun ExploreScreenHalfPreview() {
    ExploreScreen(initialSheetState = ExploreSheetState.HALF)
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun ExploreScreenFullPreview() {
    ExploreScreen(initialSheetState = ExploreSheetState.FULL)
}
