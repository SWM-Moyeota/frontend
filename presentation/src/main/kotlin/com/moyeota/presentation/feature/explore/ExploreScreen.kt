package com.moyeota.presentation.feature.explore

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.moyeota.core.designsystem.component.ChevronDirection
import com.moyeota.core.designsystem.component.ChevronIcon
import com.moyeota.core.designsystem.component.CompactPillButton
import com.moyeota.core.designsystem.component.MoyeotaBottomBar
import com.moyeota.core.designsystem.component.MoyeotaChip
import com.moyeota.core.designsystem.component.MoyeotaDefaultCamera
import com.moyeota.core.designsystem.component.MoyeotaIconSize
import com.moyeota.core.designsystem.component.MoyeotaTab
import com.moyeota.core.designsystem.component.MoyeotaTabHeader
import com.moyeota.core.designsystem.component.MyLocationOverlay
import com.moyeota.core.designsystem.component.NaverMapView
import com.moyeota.core.designsystem.component.NoticeKind
import com.moyeota.core.designsystem.component.PrimaryCtaButton
import com.moyeota.core.designsystem.component.SectionLabel
import com.moyeota.core.designsystem.component.SheetHandle
import com.moyeota.core.designsystem.component.StatusBadge
import com.moyeota.core.designsystem.component.StatusBarSpacer
import com.moyeota.core.designsystem.component.TextLinkButton
import com.moyeota.core.designsystem.component.latLngOrNull
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.core.designsystem.theme.MoyeotaShape
import com.moyeota.core.designsystem.theme.MoyeotaSpacing
import com.moyeota.core.designsystem.theme.MoyeotaType
import com.moyeota.core.designsystem.theme.sheetShadow
import com.moyeota.core.designsystem.theme.tabularNums
import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RideStatus
import com.moyeota.domain.model.RoomType
import com.moyeota.domain.model.User
import com.moyeota.presentation.core.ActiveRideBanner
import com.moyeota.presentation.core.RoutePlace
import com.moyeota.presentation.core.RoutePlaceLine
import com.moyeota.presentation.feature.home.ReservationDateStrip
import com.moyeota.presentation.feature.home.headcountLabel
import com.moyeota.presentation.feature.home.reservationDateLabel
import com.moyeota.presentation.feature.home.reservationDates
import com.moyeota.presentation.feature.home.reservationMonthLabel
import com.naver.maps.geometry.LatLng
import com.naver.maps.geometry.LatLngBounds
import com.naver.maps.map.NaverMap
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.util.MarkerIcons
import java.time.Instant
import java.time.LocalDate

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
/**
 * 조회 영역을 가시 영역보다 이만큼 넓힌다(각 변의 폭·높이 대비 비율).
 *
 * 서버는 **출발지가 영역 안**인 방만 돌려주므로, 보이는 만큼만 물으면 카메라가 조금만 움직여도
 * 가장자리의 방이 목록에서 빠져 **마커가 깜빡인다**(실기 QA: 확대·축소 중 출발지가 화면에 있는데도
 * 사라졌다 나타남). 조회 영역이 화면보다 넓으면 그 경계가 화면 밖에 있어 눈에 띄지 않는다.
 * 지도 앱이 뷰포트보다 넓게 미리 받아 두는 것과 같은 이유다.
 */
private const val BoundsMarginRatio = 0.3

/**
 * 가시 영역을 [BoundsMarginRatio] 만큼 넓힌 조회용 영역.
 * 위도는 ±90, 경도는 ±180 을 넘지 않게 자른다.
 */
internal fun MapBounds.expanded(ratio: Double = BoundsMarginRatio): MapBounds {
    val latMargin = (neLat - swLat) * ratio
    val lngMargin = (neLng - swLng) * ratio
    return MapBounds(
        swLat = (swLat - latMargin).coerceAtLeast(-90.0),
        swLng = (swLng - lngMargin).coerceAtLeast(-180.0),
        neLat = (neLat + latMargin).coerceAtMost(90.0),
        neLng = (neLng + lngMargin).coerceAtMost(180.0),
    )
}

@Immutable
data class MapBounds(
    val swLat: Double,
    val swLng: Double,
    val neLat: Double,
    val neLng: Double,
)

/**
 * 예약 모드가 그릴 것 전부 — 모드·날짜 선택지·목록·상태.
 *
 * 파라미터 10개를 [ExploreScreen] 에 늘어놓지 않고 한 덩어리로 묶는다. 묶을 수 있는 근거는 이
 * 값들이 **함께만 뜻이 있다**는 것이다: 모드가 [ExploreMode.REALTIME] 이면 나머지는 쓰이지 않고,
 * [ExploreMode.BY_DATE] 가 아니면 날짜 선택지도 쓰이지 않는다.
 *
 * **기본값이 「예약 기능 전」의 화면**이다 — 아무것도 넘기지 않으면 지금까지와 똑같이 실시간 목록만
 * 그린다(예시 방이 새지 않도록 목록에 기본값을 두지 않는 `parties` 와 같은 조심).
 *
 * @param items 서버가 준 **순서 그대로**. 화면도 ViewModel 도 재정렬하지 않는다
 *   ([ReservationFinderViewModel.UiState.Success])
 * @param loading **새 목록**(날짜·도착지·모드)을 아직 읽지 못했다. [items] 는 비어 있고(앞 날짜의 줄을 남기지
 *   않는다) 목록 자리는 비워 둔다 — 가운데 스피너 없이 새로고침 버튼의 도는 고리만 기다림을 보여 준다.
 *   같은 목록을 다시 읽을 때(주기·버튼·지도 이동·복귀)는 false 다(보던 목록을 그대로 둔다)
 * @param locating 「날짜로 찾기」인데 아직 내 좌표가 없다 — 조회를 **못 한 것**이지 실패가 아니다
 * @param destinationName 검색에 **실어 보낸** 도착지의 이름. null 이면 도착지 없이 검색했다
 *   (그때 서버는 `destinationNearby` 를 주지 않는다)
 * @param now 「오늘」·「내일」·출발 시각 표기의 기준 시각. 순수 판정 함수들은 시계를 읽지 않으므로
 *   (`ReservationRules.kt` KDoc) **화면도 호출부가 넘긴 이 값만** 쓴다 — 리컴포지션마다 시계를
 *   읽으면 같은 목록이 프레임마다 다른 글자를 갖는다
 */
@Immutable
data class ReservationPaneUi(
    val mode: ExploreMode = ExploreMode.REALTIME,
    val dates: List<LocalDate> = emptyList(),
    val selectedDate: LocalDate? = null,
    val items: List<ReservationCardUi> = emptyList(),
    val loading: Boolean = false,
    val failed: Boolean = false,
    val locating: Boolean = false,
    val destinationName: String? = null,
    val now: Instant = Instant.EPOCH,
) {
    /** 접힘 요약·목록 제목·빈 상태 문구가 함께 쓰는 「오늘」·「11월 9일 (일)」. 날짜가 없으면 null. */
    val selectedDateLabel: String?
        get() = selectedDate?.let { reservationDateLabel(it, dates.firstOrNull() ?: it) }

    /** 도착지를 실어 보냈는가 — 정렬 설명과 배지 노출이 이 값으로 갈린다. */
    val destinationSent: Boolean get() = destinationName != null
}

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

// 아래 미리보기 **전용** 예시 파티 (19 와이어프레임 카드 그대로).
//
// [ExploreScreen] 의 `parties` **기본 인자** 자리에서 걷어냈다 — 목록을 넘기지 않은 호출부가
// 생기면 서버에 없는 방 5개가 「합류」 버튼까지 달고 떴다. 21·25 가 예시 방을 그려 남의 가짜 방을
// 보여 준 사고(2026-10-15)와 같은 트랩이라 같은 규칙을 적용한다: **목록은 호출부가 넘긴다.**
private fun dummyUser(id: String, name: String) = User(id, name, "학교 인증", 4.9, 12)

private val DefaultParties = listOf(
    Ride("ride-1", "부산대 정문", "서면역", "3분 후 출발 예정 · 6.2km", 3, listOf(dummyUser("u1", "김OO"), dummyUser("u2", "이OO")), 3600, 9600, RideStatus.RECRUITING),
    Ride("ride-2", "부산대 정문", "사상역", "6분 후 출발 예정 · 8.4km", 3, listOf(dummyUser("u3", "박OO"), dummyUser("u4", "최OO")), 4200, 12600, RideStatus.RECRUITING),
    Ride("ride-3", "장전역", "서면역", "8분 후 출발 예정 · 5.1km", 3, listOf(dummyUser("u5", "정OO"), dummyUser("u6", "한OO")), 3200, 9600, RideStatus.RECRUITING),
    Ride("ride-4", "부산대 정문", "부산역", "10분 후 출발 예정 · 11km", 3, listOf(dummyUser("u7", "조OO")), 5400, 16200, RideStatus.RECRUITING),
    Ride("ride-5", "온천장역", "서면역", "12분 후 출발 예정 · 7.3km", 2, listOf(dummyUser("u8", "윤OO")), 4800, 9600, RideStatus.RECRUITING),
)

/**
 * 접힘(17) 시트 높이 — 손잡이 줄 + 요약.
 *
 * 디자인 다듬기(2026-11)에서 다시 쟀다: 손잡이 줄 28dp([PeekHandleZone]) + 제목 `HeadingLg` 한 줄(18sp×1.35 ≈ 24dp)
 * + 부제 `BodySm` 최대 두 줄(13sp×1.5×2 = 39dp) = 91dp → 4/8 격자로 올려 **96dp 그대로**다.
 * 실시간 목록이 있을 때는 부제가 없어(디자인 QA E7) 요약 줄이 남는 높이 가운데에 선다([PeekSummaryRow]).
 *
 * 상태마다 높이를 바꾸지 않는 이유: 이 값은 지도 `contentPadding` 이기도 해서, 목록이 0건↔N건을
 * 오갈 때마다 높이가 바뀌면 지도의 보이는 영역이 흔들리고 그 영역으로 다시 조회한다.
 */
private val PeekSheetHeight = 96.dp

/** 시트 맨 위 손잡이 줄 — 위 12dp + 손잡이 4dp + 아래 12dp (`MapSheetScaffold` 와 같다) */
private val PeekHandleZone = 28.dp

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

/** 시트 아래 「새 동승 방 만들기」 줄의 위아래 여백 — 새로고침 버튼이 FULL 에서 CTA 윗변을 잴 때도 쓴다. */
private val SheetCtaPadding = MoyeotaSpacing.Xs

/**
 * 17·18·19 · 근처 동승 찾기 [V07/V07b/V07c]
 *
 * **지도는 하나다.** 시트 단계(PEEK/HALF/FULL)는 지도 위에 얹힌 시트의 **높이**만 바꾼다 —
 * 예전에는 단계마다 다른 컴포저블(전체 지도 / 320dp 지도 / 지도 없음)로 갈아끼워서
 * 단계를 바꿀 때마다 지도가 새로 만들어지고(타일 재로드·카메라 복원) 전환 애니메이션도 없었다(실기 QA).
 * 지금은 시트 높이가 앵커 사이를 애니메이션으로 오가고, 지도는 `contentPadding` 만 따라 바뀐다.
 *
 * 시트 상태 전환:
 * - 핸들·헤더를 **드래그**하면 가까운 앵커(또는 던진 방향의 다음 앵커)로 정착 — 세 단계 모두
 * - PEEK 의 위 꺾쇠 버튼 → HALF · HALF/FULL 헤더의 「지도 접기 / 지도 펼치기」(+ 꺾쇠) → FULL/HALF
 *   (접기·펼치기는 **이 한 버튼**뿐이다 — 타이틀 행의 「🗺 지도」는 행 높이를 흔들어 뺐다)
 *
 * 이동(디스크립션):
 * - 「{단계} · {목적지} 보기」 배너 → 진행 중인 방의 단계 화면 21/25/26 (onOngoingRideClick)
 * - 지도 마커 탭 / 카드 「합류」 → 20 합류 확인 (onJoinParty)
 * - 「새 동승 방 만들기」 → 15 목적지 입력 (onCreateRoomClick)
 * - 하단탭 홈 / 채팅 / 마이 → 14 / 24 / 35 (onTabSelect)
 *
 * 검증·상태:
 * - 방 목록은 **지도에 보이는 범위**로 조회한 결과다([onVisibleBoundsChange]) — 마커·리스트·**헤더의 N개**가 같은 목록
 * - 지도는 네이버 실지도. 내 위치는 기기 GPS([myLocation]) — 못 받으면 기본 카메라([MoyeotaDefaultCamera]) 범위를 보여준다
 * - 위치 권한 없으면 지도 대신 권한 요청 안내 + 「위치 권한 허용」 버튼 (locationGranted)
 * - 후보 0건이면 peek 문구 자리에 빈 상태 + 지도를 움직여 보라는 안내
 *   (**내 방을 걸러내 0건이 된 경우**는 다른 문구다 — [myPartyHidden])
 * - 내가 이미 참여 중인 방은 목록·마커에 **없다**(`ExploreRoute` 의 `exploreList`) — 돌아가는 길은
 *   맨 위 진행 중 방 배너 한 번이고, 목록 카드는 20 을 거치는 더 긴 우회로였다
 * - 진행 중 탑승 없으면 상단 배너 숨김 ([activeRide] 가 null). 배너가 있으면 지도 줌 컨트롤이 그 아래로 내려간다
 * - 정원 찬 방(3/3)은 「합류」 비활성 + 「마감」 표기
 * - 15초 카운트다운 새로고침 버튼은 세 모드·세 시트 단계 모두에 있다([refreshButtonPlacement]). **목록 가운데에
 *   스피너를 두지 않는다** — 같은 목록을 다시 읽을 때는 보던 목록을 그대로 두고, 새 목록을 읽는 동안은 자리를 비우고
 *   헤더에 개수를 적지 않는다. 기다림은 버튼의 도는 고리 하나로만 보인다
 */
@Composable
fun ExploreScreen(
    /**
     * 지도에 보이는 범위의 방 목록. **기본값이 없다** — 예시 방이 프로덕션에 새는 길을 막는다.
     *
     * **내가 참여 중인 방은 이미 빠져 있다**(`ExploreRoute` 의 `exploreList`) — 마커·카드·헤더의
     * N 이 모두 이 한 목록에서 나오므로 세 자리가 함께 빠진다.
     */
    parties: List<Ride>,
    /** 지금 진행 중인 내 방. null 이면 상단 배너를 그리지 않는다 */
    activeRide: Ride? = null,
    /**
     * [parties] 에서 **내 방을 실제로 빼냈는가**(`ExploreRoute` 의 `ExploreList.myPartyHidden`).
     *
     * 빈 목록 문구만 이 값으로 갈린다 — 주변에 방이 내 방뿐이면 걸러낸 뒤 목록이 0건이 되는데,
     * 그때 「이 근처엔 동승이 없어요」는 **거짓말**이다(방은 있고, 그게 내 방이다).
     * 목록이 비지 않았을 때는 아무것도 바꾸지 않는다: 「내 방 1개는 뺐어요」는 돌아갈 길이 이미
     * 배너로 있는 상황에서 더해지는 소음이다.
     */
    myPartyHidden: Boolean = false,
    /**
     * 주기 갱신이 **연속으로** 실패해 목록이 낡았는가([ExploreViewModel.UiState.Success.stale] ·
     * 「곧 출발」은 [ReservationFinderViewModel.UiState.Success.stale]).
     *
     * true 라도 보던 목록·마커는 그대로 둔다 — 지도를 에러 화면으로 바꾸지 않는다는 기존 판단을
     * 지키고, 대신 타이틀 줄에 **비차단 표시**만 올린다. 기본값 false 는 「문제 없음」이다.
     */
    stale: Boolean = false,
    /**
     * 실시간 목록을 **아직 읽지 못했다**(첫 조회·에러 화면의 「다시 시도」, [ExploreViewModel.UiState.Loading]).
     *
     * 이 화면은 그동안에도 지도와 빈 목록을 그린다 — 가운데 스피너를 두지 않고, 기다림은 새로고침 버튼의 도는
     * 고리 하나로만 보인다. 헤더는 개수를 적지 않고([exploreHeaderCount]) 빈 목록 문구(「이 근처엔 동승이
     * 없어요」)도 그리지 않는다 — 아직 묻지도 못한 것을 「없다」고 말하지 않는다. 예약 모드는
     * `reservations.loading` 을 본다.
     */
    loading: Boolean = false,
    /**
     * **카운트다운 새로고침 버튼**이 그릴 것. **null 이면 버튼이 없다** — 주기 갱신을 하지 않는 모드
     * ([exploreAutoRefreshes], 지금은 없다)이거나 물어볼 것이 없을 때(「날짜로 찾기」가 내 위치를 기다리는 중)
     * 호출부가 null 을 넘긴다. 세 시트 단계 모두에서 보이고 자리는 [refreshButtonPlacement] 가 정한다.
     */
    refresh: ExploreRefreshUi? = null,
    /** 새로고침 버튼 — 지금 보이는 목록을 즉시 다시 읽고 15초를 처음부터 센다. */
    onRefreshClick: () -> Unit = {},
    locationGranted: Boolean = true,
    // 기기 실위치. null = 권한 없음 · 아직 fix 없음 → 지도는 기본 카메라 위치에서 시작한다
    myLocation: MyLocationFix? = null,
    initialSheetState: ExploreSheetState = ExploreSheetState.PEEK,
    /**
     * 예약 모드의 상태 전부. 기본값이 **예약 기능 전의 화면**이다([ReservationPaneUi]).
     *
     * 목록·마커·헤더의 N 은 모드에 따라 [parties] 또는 `reservations.items` 에서 나오는데,
     * **한 번에 하나다** — 두 목록을 섞지 않는 근거는 둘이 서로 다른 영역을 본다는 것이다
     * (`ReservationFinder.kt` 의 1급 사실 표).
     */
    reservations: ReservationPaneUi = ReservationPaneUi(),
    onJoinParty: (Ride) -> Unit = {},
    onOngoingRideClick: () -> Unit = {},
    onCreateRoomClick: () -> Unit = {},
    onRequestLocationPermission: () -> Unit = {},
    // 지도 카메라가 멈출 때마다 보이는 영역을 올려보낸다 → 그 범위의 방 목록으로 갱신
    onVisibleBoundsChange: (MapBounds) -> Unit = {},
    onModeSelect: (ExploreMode) -> Unit = {},
    onDateSelect: (LocalDate) -> Unit = {},
    onRetryReservations: () -> Unit = {},
    /**
     * 「날짜로 찾기」의 도착지를 고르러 간다(15 목적지). **null 이면 칩을 그리지 않는다** —
     * 배선되지 않은 버튼을 보여 주지 않기 위해서다. 도착지 없이도 검색은 정상이고
     * (서버 계약: 둘 다 또는 둘 다 아니다) 그때는 정렬이 출발 시각 순이다.
     */
    onPickSearchDestination: (() -> Unit)? = null,
    onClearSearchDestination: () -> Unit = {},
    onTabSelect: (MoyeotaTab) -> Unit = {},
) {
    var sheetState by rememberSaveable { mutableStateOf(initialSheetState) }
    // 탭 전환·회전에도 보던 자리를 유지한다 (시트 단계 변경으로는 이제 지도가 재생성되지 않는다)
    val cameraState = rememberSaveable(saver = ExploreCameraStateSaver) {
        ExploreCameraState(initialCamera = null, initialCenteredOnMyLocation = false)
    }
    val mode = reservations.mode
    // **마커·카드·헤더의 N 이 나오는 단 하나의 목록.** 모드가 그 출처를 고르고, 아래 어디에서도
    // 두 목록을 합치지 않는다 — 실시간 목록은 지도 범위, 예약은 모드마다 다른 영역이라
    // (`ReservationFinder.kt` 표) 섞이면 「지도에 보이는데 목록에 없는 방」이 생긴다.
    val shownRides = if (mode.isReservation) reservations.items.map { it.ride } else parties
    val shownCount = shownRides.size
    // 헤더의 N — 목록을 아직 읽지 못했으면 적지 않는다(「0개」는 「없다」로 읽힌다)
    val headerCount = if (mode.isReservation) {
        exploreHeaderCount(shownCount, loading = reservations.loading, locating = reservations.locating, failed = reservations.failed)
    } else {
        exploreHeaderCount(shownCount, loading = loading, locating = false, failed = false)
    }
    Column(modifier = Modifier.fillMaxSize().background(MoyeotaColor.SurfaceCanvas)) {
        StatusBarSpacer()

        // 타이틀 행 — 탭 첫 화면 공용 머리줄([MoyeotaTabHeader], 52dp **고정 높이**). 단계에 따라 버튼이
        // 생기고 사라지면 행 높이가 흔들려 제목이 움직였다.
        //
        // 낡은 목록 표시는 **고정 높이인 이 줄**에 둔다 — 지도 위에 띄우면 시트를 끝까지 올린
        // 상태(FULL, 지도가 통째로 가려짐)에서 보이지 않고, 시트 안에 두면 접힘(PEEK) 높이가
        // 흔들린다. 이 자리면 세 단계 모두에서 같은 자리에 보이고 레이아웃도 밀리지 않는다.
        //
        // **주기 갱신이 없는 모드에서는 그리지 않는다**([exploreAutoRefreshes] — 지금은 세 모드 모두 돈다).
        // [stale] 은 갱신의 연속 실패 표시라, 갱신이 없는 목록에 남아 있던 true 를 그리면 **거짓 경고**가 된다.
        val showStale = stale && exploreAutoRefreshes(mode)
        MoyeotaTabHeader(
            title = "근처 동승 찾기",
            // 머리줄은 trailing 이 있으면 끝 여백을 4dp 로 줄인다(아이콘 버튼 자리) — 배지는 버튼이 아니라
            // 나머지 12dp 를 직접 줘서 16dp 화면 여백에 맞춘다
            trailing = if (showStale) {
                { StaleListNotice(modifier = Modifier.padding(end = 12.dp)) }
            } else {
                null
            },
        )

        // 모드 칩 — **지도 위가 아니라 지도 밖**에 둔다. 시트를 끝까지 올린 상태(FULL)에서도
        // 보여야 모드를 되돌릴 수 있고, 시트 안에 두면 접힘 높이가 모드에 따라 흔들린다
        // (낡은 목록 표시를 타이틀 줄에 둔 것과 같은 근거).
        ExploreModeRow(selected = mode, onSelect = onModeSelect)

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
                    rides = shownRides,
                    // 예약 마커는 캡션에 **시각을 먼저** 적는다 — 「곧 출발」에서 고르는 기준이
                    // 인원이 아니라 몇 시냐다([reservationMarkerCaption]). 실시간은 그대로 인원만.
                    reservationCaptions = mode.isReservation,
                    now = reservations.now,
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

            // 하단 CTA(「새 동승 방 만들기」)의 실측 높이 — FULL 에서 새로고침 버튼을 그 16dp 위에 띄운다
            var ctaHeightPx by remember { mutableIntStateOf(0) }
            val refreshPlacement = refreshButtonPlacement(
                sheetState = sheetState,
                containerHeight = containerHeight,
                sheetHeight = sheetHeight,
                // CTA 는 접힘에서는 없다. 있으면 아래 여백 + 버튼 높이가 그 윗변이다
                bottomCtaTop = if (sheetState == ExploreSheetState.PEEK) {
                    null
                } else {
                    SheetCtaPadding + with(density) { ctaHeightPx.toDp() }
                },
            )
            // 버튼이 목록 위에 떠 있으면 목록 끝에 그만큼 여백을 둔다 — 끝까지 내린 마지막 줄의 「합류」가 버튼 밑에 깔리지 않게
            val listBottomPadding =
                if (refresh != null && locationGranted && refreshPlacement.spot == RefreshButtonSpot.OVER_LIST) {
                    RefreshButtonListClearance
                } else {
                    0.dp
                }

            if (activeRide != null) {
                ActiveRideBanner(
                    ride = activeRide,
                    onClick = onOngoingRideClick,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .onSizeChanged { bannerHeightPx = it.height }
                        .padding(horizontal = MoyeotaSpacing.ScreenGutter, vertical = 4.dp),
                )
            }

            // 시트 — 높이만 앵커 사이를 오간다. 내용은 **목표 단계** 기준이라 애니메이션 중에 흔들리지 않는다.
            // FULL 에서는 모서리를 각지게 — 둥근 모서리 틈으로 뒤의 지도가 비친다.
            // 모서리·그림자는 다른 시트와 같은 토큰이다(디자인 QA G3·G4: 20dp · 8dp)
            val sheetShape = if (sheetState == ExploreSheetState.FULL) RectangleShape else MoyeotaShape.Sheet
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(sheetHeight)
                    .sheetShadow(sheetShape)
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
                    Box(modifier = Modifier.fillMaxWidth().height(PeekHandleZone), contentAlignment = Alignment.Center) {
                        SheetHandle()
                    }
                    if (sheetState == ExploreSheetState.PEEK) {
                        PeekSummaryRow(
                            mode = mode,
                            count = shownCount,
                            dateLabel = reservations.selectedDateLabel,
                            myPartyHidden = myPartyHidden,
                            // 접힘 요약은 **개수만** 보므로 실패·대기 중에도 0건이다 — 그 상태에서
                            // 「없어요」라고 적지 않도록 이유를 받아 둔다([reservationStatusTitle]).
                            statusText = if (mode.isReservation) {
                                reservationStatusTitle(
                                    loading = reservations.loading,
                                    locating = reservations.locating,
                                    failed = reservations.failed,
                                )
                            } else {
                                NEARBY_LOADING_TEXT.takeIf { loading }
                            },
                            onRaise = { sheetState = ExploreSheetState.HALF },
                        )
                    } else {
                        ListHeaderRow(
                            mode = mode,
                            count = headerCount,
                            dateLabel = reservations.selectedDateLabel,
                            destinationSent = reservations.destinationSent,
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
                        HorizontalDivider(color = MoyeotaColor.Hairline)
                        Spacer(Modifier.height(ListHeaderGap))
                    }
                    // **이 목록이 무엇을 기준으로 찾은 것인가.** 예약 모드에서만 한 줄 붙는다 —
                    // 「곧 출발」은 지도 범위, 「날짜로 찾기」는 내 위치 1km 라서 적어 두지 않으면
                    // 지도를 옮겨 둔 사람이 결과를 잘못 읽는다([exploreAreaNote]).
                    exploreAreaNote(mode, reservations.destinationSent)?.let { note ->
                        Text(
                            text = note,
                            style = MoyeotaType.CaptionMd,
                            color = MoyeotaColor.TextMute,
                            modifier = Modifier.padding(horizontal = MoyeotaSpacing.ScreenGutter),
                        )
                        Spacer(Modifier.height(ListHeaderGap))
                    }
                    // 날짜·도착지 선택은 「날짜로 찾기」에서만 뜻이 있다(「곧 출발」은 30분 고정이다)
                    if (mode == ExploreMode.BY_DATE) {
                        ReservationDateRow(
                            dates = reservations.dates,
                            selected = reservations.selectedDate,
                            destinationName = reservations.destinationName,
                            onDateSelect = onDateSelect,
                            onPickDestination = onPickSearchDestination,
                            onClearDestination = onClearSearchDestination,
                        )
                        Spacer(Modifier.height(ListHeaderGap))
                    }
                    if (mode.isReservation) {
                        ReservationList(
                            pane = reservations,
                            myPartyHidden = myPartyHidden,
                            onJoin = onJoinParty,
                            onRetry = onRetryReservations,
                            bottomPadding = listBottomPadding,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        PartyList(
                            parties = parties,
                            loading = loading,
                            myPartyHidden = myPartyHidden,
                            onJoinParty = onJoinParty,
                            showEndOfList = sheetState == ExploreSheetState.FULL,
                            bottomPadding = listBottomPadding,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // 문구는 모드와 무관하게 그대로 둔다 — 누르면 15 목적지로 가고, 예약 방을
                    // 만드는 길도 그 뒤의 16 모달 안에 있다(「예약 방 만들기」로 바꾸면 16 이
                    // 기본값 「지금 출발」로 열리는 것과 어긋난다).
                    //
                    // 공용 주 CTA 다(디자인 QA E3) — 예전 전용 버튼(r16·48dp)은 앱의 다른 주 CTA(알약·52dp)와
                    // 모양이 달랐다. 이 시트의 **단 하나뿐인 솔리드 파랑**이고, 줄마다의 「합류」는 옅은 알약이다.
                    // 「＋」 글자는 뺐다 — 아이콘 대신 쓴 글자다(G10).
                    PrimaryCtaButton(
                        text = "새 동승 방 만들기",
                        onClick = onCreateRoomClick,
                        modifier = Modifier
                            .padding(horizontal = MoyeotaSpacing.ScreenGutter, vertical = SheetCtaPadding)
                            // 여백 안쪽(버튼 자체)을 잰다 — 새로고침 버튼을 이 윗변 16dp 위에 띄운다
                            .onSizeChanged { ctaHeightPx = it.height },
                    )
                }
            }

            // 카운트다운 새로고침 버튼 — **세 시트 단계 모두에서 보인다**(2026-11, 오너 UX 피드백 2차).
            // 접힘·반펼침은 네이버 줌 컨트롤과 같은 세로선, 시트 윗변 16dp 위(지도 위)이고, 지도가 다 가려지는
            // FULL 에서는 목록 위 오른쪽 아래에 뜬다(CTA 16dp 위). 자리는 [refreshButtonPlacement] 가 정하고,
            // 시트를 따라 움직이도록 **실측 높이**(드래그·정착 애니메이션 중의 값)를 넘긴다. 목록 위에 뜨므로
            // 시트 **뒤에** 그린다. 권한이 없어 지도가 없을 때는 다시 읽을 지도 범위가 없어 그리지 않는다.
            if (refresh != null && locationGranted) {
                ExploreRefreshButton(
                    ui = refresh,
                    onClick = onRefreshClick,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = refreshPlacement.end, bottom = refreshPlacement.bottom),
                )
            }
        }

        MoyeotaBottomBar(selected = MoyeotaTab.EXPLORE, onSelect = onTabSelect)
    }
}

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

/**
 * 목록이 0건일 때의 두 줄 — 접힘 요약(17)과 리스트 빈 상태(18·19)가 **같은 말**을 쓴다.
 *
 * 걸러내기 전의 목록에 내 방이 있었는가([myPartyHidden])로 갈린다. 「이 근처엔 동승이 없어요」는
 * 주변에 내 방뿐일 때 **거짓말**이다 — 방은 있고 그게 내 방이며, 그래서 목록이 빈 것이다.
 * 그 경우 사실만 말한다: 이 범위에 남의 방이 없다.
 *
 * 내 방으로 **돌아가는 길을 문구로 가리키지 않는다**(「위 배너를 누르세요」를 쓰지 않았다) —
 * 시트를 끝까지 올린 상태(FULL)에서는 그 배너가 시트에 가려 보이지 않아 위치를 말하는 문장이
 * 그 순간 틀린 말이 된다. 배너는 PEEK·HALF 에서 바로 보이고, 두 줄의 역할은 「왜 비었는지」까지다.
 */
private fun emptyListCopy(myPartyHidden: Boolean): Pair<String, String> =
    if (myPartyHidden) "지금은 내 방뿐이에요" to "지도를 움직여 다른 방을 찾아보세요"
    else "이 근처엔 동승이 없어요" to "지도를 움직여 보세요"

/**
 * 실시간 목록을 아직 읽지 못했다 — 접힘 요약 한 줄이 쓴다(예약의 [RESERVATION_LOADING_TEXT] 와 같은 자리).
 *
 * 0건을 「이 근처엔 동승이 없어요」로 말하지 않기 위한 문구다. 목록 쪽은 글도 스피너도 두지 않고 자리를
 * 비워 둔다 — 기다림은 새로고침 버튼의 도는 고리가 보여 준다.
 */
internal const val NEARBY_LOADING_TEXT = "동승 목록을 불러오는 중이에요"

/**
 * 17 접힘 요약 — 「주변 합승 N개」. N 은 헤더·마커와 같은 목록의 방 수다.
 *
 * 제목·빈 상태 문구는 **모드가 정한다**([explorePeekTitle]·[reservationEmptyCopy]) — 실시간 쪽
 * 문구는 한 글자도 바뀌지 않았다.
 */
@Composable
private fun PeekSummaryRow(
    mode: ExploreMode,
    count: Int,
    dateLabel: String?,
    myPartyHidden: Boolean,
    statusText: String?,
    onRaise: () -> Unit,
) {
    val isEmpty = count == 0
    val (emptyTitle, emptySubtitle) =
        if (mode.isReservation) reservationEmptyCopy(mode, dateLabel, myPartyHidden)
        else emptyListCopy(myPartyHidden)
    // 「목적지 정하면 바로 자동 매칭」은 지웠다 — 목적지를 정하면 **새 방이 만들어지고**
    // (16 「같이 탈 사람 찾기」 → `POST /matching/rooms`) 사람이 합류하기를 기다린다.
    // 목적지로 기존 방에 자동으로 넣어 주는 기능은 없다(2026-10-13).
    //
    // 예약 모드에서는 **무엇을 기준으로 찾았는지**를 그 자리에 적는다([exploreAreaNote]) —
    // 접힘 상태에서는 목록 위의 같은 줄이 보이지 않아 여기가 유일한 자리다.
    // 예약 모드에서는 **도착지 정렬 설명을 빼고** 영역 기준만 적는다(destinationSent = false) —
    // 접힘 한 줄에 두 문장을 넣으면 잘린다. 그 설명은 목록 위의 같은 줄이 맡는다.
    //
    // 실시간 목록이 있을 때는 **부제를 두지 않는다**(디자인 QA E7). 예전 「위로 올리면 리스트, 목적지를
    // 정하면 새 방을 만들어요」는 화면 사용법을 글로 설명한 줄이었다 — 손잡이와 위 꺾쇠가 이미 그 말을 한다.
    val subtitle = when {
        isEmpty && statusText == null -> emptySubtitle
        mode.isReservation -> exploreAreaNote(mode, destinationSent = false)
        isEmpty -> emptySubtitle
        else -> null
    }
    Row(
        // 남는 높이([PeekSheetHeight] − 손잡이 줄) 가운데에 선다 — 부제가 없는 상태에서 제목이 위에 붙고
        // 아래가 비어 보이지 않게. 끝 여백 4dp 는 48dp 꺾쇠 버튼의 아이콘이 16dp 화면 여백에 오게 한다.
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PeekSheetHeight - PeekHandleZone)
            .padding(start = MoyeotaSpacing.ScreenGutter, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!isEmpty) {
            Box(Modifier.size(8.dp).background(MoyeotaColor.Primary500, CircleShape))
            Spacer(Modifier.width(8.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                // 0건일 때 **왜 0건인지**가 있으면 그것부터 — 실패·대기를 「없어요」로 말하지 않는다
                text = when {
                    isEmpty && statusText != null -> statusText
                    isEmpty -> emptyTitle
                    else -> explorePeekTitle(mode, count, dateLabel)
                },
                style = MoyeotaType.HeadingLg,
                color = MoyeotaColor.InkPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MoyeotaType.BodySm,
                    color = MoyeotaColor.TextMute,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // 48dp 버튼 — 예전엔 18dp 꺾쇠 그림 자체만 눌렸다(디자인 QA G12)
        IconButton(onClick = onRaise) {
            ChevronIcon(
                direction = ChevronDirection.UP,
                size = MoyeotaIconSize.Md,
                tint = MoyeotaColor.TextMute,
                modifier = Modifier.semantics { contentDescription = "목록 펼치기" },
            )
        }
    }
}

/**
 * 18·19 목록 헤더 — 「주변 합승 N개 · 가까운 순」 + 지도 접기/펼치기 **단일** 토글.
 *
 * 정렬을 말하는 조건이 모드마다 다르다([exploreListTitle]) — 서버가 보장하는 것만 적는다.
 */
@Composable
private fun ListHeaderRow(
    mode: ExploreMode,
    /** null 이면 개수를 적지 않는다 — 목록을 아직 읽지 못했다([exploreHeaderCount]) */
    count: Int?,
    dateLabel: String?,
    destinationSent: Boolean,
    mapShown: Boolean,
    onToggleMap: () -> Unit,
) {
    Row(
        // 끝 여백 8dp + 버튼 안쪽 8dp = 글자 끝이 16dp 화면 여백에 온다
        modifier = Modifier.fillMaxWidth().padding(start = MoyeotaSpacing.ScreenGutter, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = exploreListTitle(mode, count, dateLabel, destinationSent),
            style = MoyeotaType.HeadingLg,
            color = MoyeotaColor.InkPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        // 글자로 쓰던 꺾쇠(⌄ ⌃)는 그린 아이콘으로 바꿨다(G10) — 글꼴마다 모양·높이가 달랐다.
        // 파랑을 쓰지 않는다: 이 시트의 파랑은 「새 동승 방 만들기」 하나다. 48dp 터치(G12).
        TextLinkButton(
            text = if (mapShown) "지도 접기" else "지도 펼치기",
            onClick = onToggleMap,
            color = MoyeotaColor.TextBody,
            style = MoyeotaType.BodySm.copy(fontWeight = FontWeight.SemiBold),
            trailingIcon = {
                ChevronIcon(
                    direction = if (mapShown) ChevronDirection.DOWN else ChevronDirection.UP,
                    size = MoyeotaIconSize.Sm,
                    tint = MoyeotaColor.TextBody,
                )
            },
        )
    }
}

/**
 * 「실시간 / 곧 출발 / 날짜로 찾기」 칩 한 줄.
 *
 * 하단탭([MoyeotaBottomBar])처럼 보이지 않게 **칩**으로 둔다 — 이건 화면 이동이 아니라 같은 화면의
 * 필터이고, 지도 카메라·시트 단계는 모드를 바꿔도 그대로다. 디자인시스템의 [MoyeotaChip] 을 쓴다
 * (앱의 단 하나뿐인 칩이다 — 디자인 QA G6).
 *
 * ### 간격 (2026-11, 오너 UX 피드백 · 디자인 QA 4-8)
 * 예전에는 칩 줄이 제목 줄과 지도 사이에 위아래 여백 없이 끼어 지도에 맞닿아 있었다.
 * - **시작선**: 제목과 같은 [MoyeotaSpacing.ScreenGutter](16dp) — 두 줄이 한 머리글로 읽힌다
 *   ([MoyeotaTabHeader] 도 같은 값을 쓴다)
 * - **칩 사이 8dp**, 제목 아래 **8dp**, 지도 위 **12dp** — 4/8 리듬(`spacing.xs`·`spacing.sm`)
 * - **터치 타깃 48dp**: 칩의 보이는 높이는 36dp 이고 [MoyeotaChip] 이 스스로 위아래 6dp 씩 눌리는 자리를
 *   더한다. 그 6dp 는 투명하므로 위 「8dp·12dp」는 **보이는 칩 테두리 기준**이다(줄 여백 2dp·6dp + 터치 여유 6dp)
 *
 * 세 칩이 393dp 폭에 들어오지만 큰 글꼴 설정에서는 넘칠 수 있어 가로 스크롤을 둔다. 여백을 스크롤
 * **안쪽**에 둬서 첫 칩은 제목 선에서 시작하고 스크롤하면 가장자리까지 흐른다.
 */
@Composable
private fun ExploreModeRow(selected: ExploreMode, onSelect: (ExploreMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = MoyeotaSpacing.ScreenGutter, end = MoyeotaSpacing.ScreenGutter, top = 2.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExploreMode.entries.forEach { mode ->
            MoyeotaChip(
                text = exploreModeLabel(mode),
                selected = mode == selected,
                onClick = { onSelect(mode) },
            )
        }
    }
}

/**
 * 「날짜로 찾기」의 **주간 달력 띠**(오늘~7일 뒤)와 그 아래 **도착지 칩**.
 *
 * 날짜는 16 도착지 확인과 **같은 컴포넌트**다([ReservationDateStrip]) — 같은 8일 창을 두 화면이 다른
 * 모양(칩 vs 달력)으로 고르면 같은 날이 다르게 보인다(2026-11, 오너 UX 피드백). 띠 위에는 덮는 달을
 * 한 줄로 적는다([reservationMonthLabel]) — 칸에는 일(日) 숫자만 있다.
 *
 * 도착지 칩은 띠 **아래 자기 줄**(8dp 간격)로 옮겼다 — 예전에는 날짜 칩 끝에 붙어 가로 스크롤 끝까지
 * 가야 보였다. 둘 다 같은 검색 요청의 입력이라 붙어 있되, 도착지는 **있어도 되고 없어도 되는 값**이라
 * 날짜와 다른 줄이 낫다. [onPickDestination] 이 null 이면 칩을 **그리지 않는다**: 도착지를 고르는 길
 * (15 목적지)이 배선되지 않았을 수 있고, 그때 칩을 보여 주면 눌러도 아무 일도 일어나지 않는다.
 * 도착지 없이 검색하는 것은 서버가 인정하는 정상 경로다(둘 다 또는 둘 다 아니다).
 *
 * 「전체」 칸은 없다 — 검색은 하루 단위다(`date` 필수).
 */
@Composable
private fun ReservationDateRow(
    dates: List<LocalDate>,
    selected: LocalDate?,
    destinationName: String?,
    onDateSelect: (LocalDate) -> Unit,
    onPickDestination: (() -> Unit)?,
    onClearDestination: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 달 표기는 묶음 머리말이다(디자인 QA 4-3 — 「11월」·「11–12월」) — 아래 8dp 는 SectionLabel 이 준다
        reservationMonthLabel(dates)?.let { month ->
            SectionLabel(text = month, modifier = Modifier.padding(horizontal = MoyeotaSpacing.ScreenGutter))
        }
        ReservationDateStrip(
            dates = dates,
            selected = selected,
            onSelect = onDateSelect,
            // 띠의 첫 칸이 시트 본문(16dp 화면 여백)과 같은 선에서 시작한다
            contentPadding = PaddingValues(horizontal = MoyeotaSpacing.ScreenGutter),
        )
        if (destinationName != null || onPickDestination != null) {
            // 띠와 칩 사이는 **보이는 칩 테두리 기준 8dp** — 칩이 위아래로 둔 투명한 터치 여유 6dp 에 2dp 를 더한다
            Spacer(Modifier.height(2.dp))
            Row(modifier = Modifier.padding(horizontal = MoyeotaSpacing.ScreenGutter)) {
                if (destinationName != null) {
                    // 고른 도착지는 **이름으로** 보여 준다 — 「도착지 1곳」 같은 말로는 어디를 보냈는지
                    // 알 수 없고, 그 좌표가 결과 순서를 바꾼다(가까운 방이 먼저).
                    MoyeotaChip(
                        text = "$destinationName $RESERVATION_CLEAR_DESTINATION_LABEL",
                        selected = true,
                        onClick = onClearDestination,
                    )
                } else if (onPickDestination != null) {
                    MoyeotaChip(
                        text = RESERVATION_ADD_DESTINATION_LABEL,
                        selected = false,
                        onClick = onPickDestination,
                    )
                }
            }
        }
    }
}

// ─── 공용 조각 ──────────────────────────────────────────────────────────────

/**
 * @param loading 아직 읽지 못했다 — 빈 목록 문구를 그리지 않고 자리를 비워 둔다(기다림은 새로고침 버튼이 보여 준다)
 * @param bottomPadding 목록 끝 여백 — 새로고침 버튼이 목록 위에 떠 있을 때 마지막 줄이 그 밑에 깔리지 않게
 */
@Composable
private fun PartyList(
    parties: List<Ride>,
    loading: Boolean,
    myPartyHidden: Boolean,
    onJoinParty: (Ride) -> Unit,
    showEndOfList: Boolean,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    // 카드 더미가 아니라 **목록 줄**이다(디자인 QA E5) — 흰 시트 안에 흰 테두리 카드를 쌓으면 반펼침에서
    // 두 장 남짓만 보였다. 줄은 화면 폭을 다 쓰고 줄 사이는 안쪽으로 들인 헤어라인 하나다.
    LazyColumn(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = bottomPadding)) {
        items(parties.size) { index ->
            if (index > 0) ListRowDivider()
            PartyCard(ride = parties[index], onJoin = onJoinParty)
        }
        if (parties.isEmpty()) {
            if (!loading) item { EmptyListNotice(myPartyHidden = myPartyHidden) }
        } else if (showEndOfList) {
            // 스펙 19: 마지막 페이지면 목록의 끝을 알린다 (범위 기준)
            item {
                Text(
                    text = "이 근처는 여기까지예요",
                    style = MoyeotaType.CaptionMd,
                    color = MoyeotaColor.TextMute,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                )
            }
        }
    }
}

/** 목록 줄 사이 — 화면 여백만큼 안쪽으로 들인 헤어라인 */
@Composable
private fun ListRowDivider() {
    HorizontalDivider(
        color = MoyeotaColor.Hairline,
        modifier = Modifier.padding(horizontal = MoyeotaSpacing.ScreenGutter),
    )
}

/**
 * 예약 목록 — [PartyList] 와 같은 모양이고 **카드와 빈 상태만 다르다**.
 *
 * 「목록의 끝」 줄([PartyList] 의 `showEndOfList`)을 두지 않는다: 서버가 검색을 **최대 100개**로
 * 자르는데 그게 「이 날은 여기까지」인지 「100개에서 잘렸는지」를 앱이 알 수 없어, 끝을 단정하면
 * 거짓이 될 수 있다. 실시간 쪽은 뷰포트 전부를 받아 그 말이 참이었다.
 */
@Composable
private fun ReservationList(
    pane: ReservationPaneUi,
    myPartyHidden: Boolean,
    onJoin: (Ride) -> Unit,
    onRetry: () -> Unit,
    /** 목록 끝 여백 — 새로고침 버튼이 목록 위에 떠 있을 때 마지막 줄이 그 밑에 깔리지 않게 */
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = bottomPadding)) {
        items(pane.items.size) { index ->
            if (index > 0) ListRowDivider()
            ReservationCard(item = pane.items[index], now = pane.now, onJoin = onJoin)
        }
        if (pane.items.isEmpty()) {
            item { ReservationEmptyState(pane = pane, myPartyHidden = myPartyHidden, onRetry = onRetry) }
        }
    }
}

/**
 * 예약 목록이 비었을 때의 **네 가지 상태** — 섞으면 안 되는 것들이다.
 *
 * | 상태 | 뜻 | 사용자가 할 일 |
 * |---|---|---|
 * | 조회 중 | 아직 모른다 | 기다린다 — 자리를 **비워 둔다**. 기다림은 새로고침 버튼의 도는 고리가 보여 준다(가운데 스피너 없음) |
 * | 내 위치 확인 중 | **아직 묻지도 못했다**(1km 의 중심이 없다) | 기다린다 |
 * | 실패 | 못 물어봤다 | 다시 시도 |
 * | 0건 | 물어봤고 **없다** | 날짜를 바꾸거나 지도를 옮긴다 |
 *
 * 아래 둘을 합치면 장애가 「그 날은 예약이 없네」로 위장되고 사용자는 날짜만 계속 바꾼다
 * (16 추천의 `nearbyLoadFailed` 가 같은 이유로 갈라져 있다).
 */
@Composable
private fun ReservationEmptyState(pane: ReservationPaneUi, myPartyHidden: Boolean, onRetry: () -> Unit) {
    when {
        // 비워 둔다 — 예전의 가운데 스피너(`LoadingBox`)는 「날짜로 찾기」에서 날짜를 바꿀 때마다 목록 한가운데를
        // 차지했다(2026-11, 오너 UX 피드백 2차). 헤더도 개수를 적지 않는다([exploreHeaderCount])
        pane.loading -> Unit
        pane.locating -> ReservationNotice(text = RESERVATION_LOCATING_TEXT)
        pane.failed -> ReservationNotice(text = RESERVATION_LOAD_FAILED_TEXT, onRetry = onRetry)
        else -> {
            val (title, subtitle) = reservationEmptyCopy(pane.mode, pane.selectedDateLabel, myPartyHidden)
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = MoyeotaSpacing.ScreenGutter, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text = title, style = MoyeotaType.BodyStrong, color = MoyeotaColor.InkPrimary, textAlign = TextAlign.Center)
                Text(text = subtitle, style = MoyeotaType.BodySm, color = MoyeotaColor.TextMute, textAlign = TextAlign.Center)
            }
        }
    }
}

/** 한 줄 안내(+ 선택적 재시도). 화면을 통째로 덮지 않는다 — 지도·모드 칩은 그대로 둔다. */
@Composable
private fun ReservationNotice(text: String, onRetry: (() -> Unit)? = null) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = MoyeotaSpacing.ScreenGutter, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = text, style = MoyeotaType.BodySm, color = MoyeotaColor.TextMute, textAlign = TextAlign.Center)
        if (onRetry != null) {
            // 48dp 터치 + 눌림 물결(G12) — 예전엔 글자 크기만큼만 눌렸다
            TextLinkButton(text = "다시 시도", onClick = onRetry)
        }
    }
}

/**
 * 예약 방 카드 한 장 — [PartyCard] 와 **같은 조각으로 짜되 출발 시각이 맨 위**다.
 *
 * 실시간 카드는 `departureLabel`(서버가 만든 「3분 후 출발 예정 · 6.2km」)을 장소 아래에 둔다.
 * 예약에서 그 자리에 시각을 두면 가장 중요한 값이 세 번째 줄로 내려간다 — 이 목록에서 고르는
 * 기준은 **몇 시에 출발하는가**이고(오늘 오후 6:30 과 내일 오전 9:00 은 전혀 다른 방이다) 장소는
 * 그다음이다. 시각을 읽을 수 없으면 줄을 **그리지 않는다**([reservationDepartureLabel] 이 null) —
 * 「시간 미정」 같은 자리값을 두면 약속 시간이 정해진 방을 미정으로 보여 주는 거짓이 된다.
 *
 * 「도착지도 1km 안」 배지는 서버가 true 라고 말했을 때만 붙는다([destinationNearbyBadge]).
 */
@Composable
private fun ReservationCard(
    item: ReservationCardUi,
    now: Instant,
    onJoin: (Ride) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ride = item.ride
    // 정원을 모르면(0) 「마감」이라고 단정하지 않는다 — [PartyCard] 와 같은 규칙이다.
    val full = ride.capacity > 0 && ride.members.size >= ride.capacity
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MoyeotaSpacing.ScreenGutter, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // 출발 시각이 이 줄의 제목이다 — 검정 굵은 글자로 둔다. 파랑은 시트의 CTA 몫이라 쓰지 않고,
            // 숫자 폭을 고정해 줄마다 시각의 끝이 출렁이지 않게 한다
            reservationDepartureLabel(ride.departureAt, now)?.let { departure ->
                Text(
                    text = departure,
                    style = MoyeotaType.BodyStrong.tabularNums(),
                    color = MoyeotaColor.InkPrimary,
                    maxLines = 1,
                )
            }
            // 제목이 시각이라 장소는 부제 모양(24 채팅 목록과 같은 조각, emphasizePlace = false)
            RoutePlaceLine(place = RoutePlace(label = "출발지", place = ride.origin))
            RoutePlaceLine(place = RoutePlace(label = "목적지", place = ride.destination))
            val headcount = headcountLabel(joined = ride.members.size, capacity = ride.capacity)
            val nearby = destinationNearbyBadge(item.destinationNearby)
            if (headcount.isNotEmpty() || nearby != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (headcount.isNotEmpty()) StatusBadge(kind = NoticeKind.NEUTRAL, text = headcount)
                    // 「도착지도 1km 안」 — 인원 배지(회색)와 **다른 색**이어야 다른 종류의 사실로 읽힌다.
                    // 서버가 확인해 준 사실이라 초록을 그대로 둔다(디자인 QA E2)
                    if (nearby != null) StatusBadge(kind = NoticeKind.SUCCESS, text = nearby)
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        JoinButton(full = full, onClick = { onJoin(ride) })
    }
}

@Composable
private fun PartyCard(
    ride: Ride,
    onJoin: (Ride) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 정원을 모르면(0) 「마감」이라고 단정하지 않는다 — `0 >= 0` 이 참이라 전에는 인원·정원을 둘 다
    // 모르는 방이 **마감으로 보였다.** 인원 문구를 지우면서 버튼만 마감으로 남기면 같은 카드가 두
    // 가지 말을 한다. 실제로 찼는지는 탭해서 들어간 20 이 방 상세로 다시 판정한다([joinGate]).
    val full = ride.capacity > 0 && ride.members.size >= ride.capacity
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MoyeotaSpacing.ScreenGutter, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 겹친 빈 아바타(사진·이름 없는 회색 원 N개)는 지웠다(디자인 QA E4) — 바로 옆 「2/3명」 배지와
        // 같은 말을 그림으로 한 번 더 했을 뿐이다.
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // 출발지·목적지를 라벨 붙여 두 줄로 — 「A → B」 한 줄은 주소가 길면 어디서 갈리는지 읽기 어렵다.
            // 이 줄에는 다른 제목이 없어 장소가 제목이다(emphasizePlace) — 24 채팅 목록과 같은 조각이다
            RoutePlaceLine(place = RoutePlace(label = "출발지", place = ride.origin), emphasizePlace = true)
            RoutePlaceLine(place = RoutePlace(label = "목적지", place = ride.destination), emphasizePlace = true)
            // 서버 목록 매핑(`PartyMappers`)은 이 값을 늘 빈 문자열로 둔다 — 그대로 그리면 모든 줄에 빈 줄이
            // 하나씩 생겼다(디자인 QA E1). 값이 올 때만 세운다
            if (ride.departureLabel.isNotBlank()) {
                Text(
                    text = ride.departureLabel,
                    style = MoyeotaType.CaptionMd,
                    color = MoyeotaColor.TextMute,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // 인원 문구는 16 추천 카드와 **같은 함수**를 쓴다([headcountLabel]) — 리터럴로 쓰던 동안
            // 인원을 모르는 방을 「0/3명」, 정원을 모르는 방을 「2/0명」으로 그렸다(없는 상태다).
            // 빈 문자열이면 배지째 지운다(그 판단은 [headcountLabel] KDoc 에 있다).
            //
            // 회색 배지다(디자인 QA E2) — 예전 호박색 바탕 + 흰 11sp 는 대비가 2.15:1 이었고, 호박색은 이 앱에서
            // 「기다리는 중」이라는 **상태**를 뜻한다. 인원은 상태가 아니라 사실이다.
            val headcount = headcountLabel(joined = ride.members.size, capacity = ride.capacity)
            if (headcount.isNotEmpty()) {
                StatusBadge(kind = NoticeKind.NEUTRAL, text = headcount)
            }
        }
        Spacer(Modifier.width(8.dp))
        JoinButton(full = full, onClick = { onJoin(ride) })
    }
}

/**
 * 줄 끝의 「합류」 — 정원 찬 방(3/3)은 비활성 + 「마감」 표기 (스펙 18).
 *
 * 공용 [CompactPillButton] 이다(디자인 QA E3): 보이는 높이 32dp·터치 48dp 의 옅은 파랑 알약. 예전 34dp
 * 솔리드 파랑 버튼은 손가락에 작았고 줄마다 하나씩 있어 시트의 진짜 주 CTA(「새 동승 방 만들기」)를 묻었다.
 */
@Composable
private fun JoinButton(full: Boolean, onClick: () -> Unit) {
    CompactPillButton(text = if (full) "마감" else "합류", onClick = onClick, enabled = !full)
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
        Column(
            modifier = Modifier.padding(horizontal = MoyeotaSpacing.ScreenGutter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "위치 권한이 필요해요",
                style = MoyeotaType.BodyStrong,
                color = MoyeotaColor.InkPrimary,
            )
            Text(
                text = "권한을 허용하면 내 주변 동승을 지도로 볼 수 있어요",
                style = MoyeotaType.BodySm,
                color = MoyeotaColor.TextMute,
                textAlign = TextAlign.Center,
            )
            // 보이는 알약은 36dp, 눌리는 자리는 48dp(G12) — 이 상태에서 화면의 유일한 행동이라 솔리드 파랑이다
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(MoyeotaShape.Pill)
                    .clickable(role = Role.Button) { onRequestPermission() },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .background(MoyeotaColor.Primary500, MoyeotaShape.Pill)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "위치 권한 허용",
                        style = MoyeotaType.ButtonMd,
                        color = MoyeotaColor.TextOnDark,
                    )
                }
            }
        }
    }
}

/**
 * 「최신 정보를 받지 못했어요」 — 목록을 **덮지 않는** 표시.
 *
 * 띄우는 조건은 [staleAfterPollFailures] 가 정한다(연속 실패 2회 = 30초). 여기서 하는 일은 그
 * 사실을 조용히 알리는 것뿐이고, 이 표시에는 재시도 버튼을 달지 않는다 — 폴링이 15초마다 알아서 다시
 * 읽고 한 번 성공하면 이 표시가 사라진다. 지금 바로 다시 읽고 싶으면 지도의 새로고침 버튼이 있다
 * ([ExploreRefreshButton]) — 같은 일을 하는 버튼을 두 자리에 두지 않는다.
 */
@Composable
private fun StaleListNotice(modifier: Modifier = Modifier) {
    // 공용 상태 배지(빨강 = 오류)다 — 예전 전용 알약(11sp Bold)과 색은 같고 글자 규칙만 맞췄다
    StatusBadge(kind = NoticeKind.ERROR, text = "최신 정보를 받지 못했어요", modifier = modifier)
}

@Composable
private fun EmptyListNotice(myPartyHidden: Boolean) {
    // 접힘 요약(17)과 **같은 두 줄**이다 — 같은 상황을 두 자리가 다르게 말하지 않게 한 곳에서 뽑는다
    val (title, subtitle) = emptyListCopy(myPartyHidden)
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = MoyeotaSpacing.ScreenGutter, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MoyeotaType.BodyStrong,
            color = MoyeotaColor.InkPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = subtitle,
            style = MoyeotaType.BodySm,
            color = MoyeotaColor.TextMute,
            textAlign = TextAlign.Center,
        )
    }
}

// ─── 지도 (네이버 실지도 + 내 위치·합승 마커) ────────────────────────────────

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
 * 지도가 범위를 알려주기 전의 첫 조회 범위.
 *
 * [center] 가 null(위치 권한 거부·아직 fix 없음)이면 지도가 처음 보여주는 기본 카메라
 * ([MoyeotaDefaultCamera]) 주변을 조회한다 — 지도에 보이는 곳과 목록이 어긋나지 않게.
 */
fun defaultExploreBounds(center: LatLng?): MapBounds {
    val origin = center ?: MoyeotaDefaultCamera
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
 * @param myLocation 기기 실위치. null 이면 기본 카메라([MoyeotaDefaultCamera])에서 시작하고 내 위치 표시는 없다
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
    /** 캡션에 출발 시각을 먼저 적는가(예약 모드) — [reservationMarkerCaption] */
    reservationCaptions: Boolean = false,
    /** 「오늘 오후 6:30」을 셈하는 기준 시각. 예약 모드에서만 쓰인다 */
    now: Instant = Instant.EPOCH,
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
        mutableStateOf(cameraState.camera ?: MapCamera(myPosition ?: MoyeotaDefaultCamera, ExploreZoom))
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
        // 실위치가 있으면 SDK 내장 위치 오버레이(파란 점 + 오차 원 + 방향)를 쓴다 — 14 홈과 같은 컴포넌트
        MyLocationOverlay(
            map = map,
            position = myLocation.position,
            accuracyMeters = myLocation.accuracyMeters,
            bearingDegrees = myLocation.bearingDegrees,
        )
    }
    rides.forEach { ride ->
        // key 로 묶어야 범위 조회로 목록이 바뀔 때 마커 상태가 옆 항목으로 밀리지 않는다
        key(ride.id) {
            ExploreMarker(
                map = map,
                position = latLngOrNull(ride.originLat, ride.originLng),
                tint = MoyeotaColor.Primary500.toArgb(),
                // 캡션도 카드 배지와 **같은 함수**다([headcountLabel]) — 리터럴로 쓰던 동안 인원을
                // 모르는 방의 마커가 「0명」이라고 적혀 있었고, 같은 방의 카드는 「최대 3명」이었다.
                // 둘 다 모르면 빈 문자열이 오고, 그때는 캡션을 아예 달지 않는다([ExploreMarker]).
                //
                // 예약 모드는 **시각 · 인원**이다([reservationMarkerCaption]) — 그 함수도 둘 다
                // 모를 때 빈 문자열을 돌려주므로 위 계약이 그대로 유지된다.
                caption = if (reservationCaptions) {
                    reservationMarkerCaption(
                        departureAt = ride.departureAt,
                        joined = ride.members.size,
                        capacity = ride.capacity,
                        now = now,
                    )
                } else {
                    headcountLabel(joined = ride.members.size, capacity = ride.capacity)
                },
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

/**
 * 지도 마커 하나. 오버레이는 `map` 프로퍼티로 붙고 떨어지므로 값이 바뀌면 떼었다 다시 붙인다.
 *
 * @param caption 마커 아래 글자. **빈 문자열이면 캡션을 달지 않는다** — `captionText` 의 SDK 기본값이
 *   ""(캡션 없음)이므로 아예 세우지 않는 것과 같고, 빈 글자를 넣어 마커 아래에 빈 자리가 생기거나
 *   SDK 버전에 따라 빈 라벨이 그려지는 일을 피한다. 호출부는 「인원·정원을 둘 다 모른다」를 이 빈
 *   문자열로 넘긴다([headcountLabel]) — 「0명」처럼 없는 사실을 지도에 박지 않기 위해서다.
 */
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
                if (caption.isNotEmpty()) captionText = caption
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

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun ExploreScreenPeekPreview() {
    ExploreScreen(parties = DefaultParties, initialSheetState = ExploreSheetState.PEEK)
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun ExploreScreenHalfPreview() {
    ExploreScreen(parties = DefaultParties, initialSheetState = ExploreSheetState.HALF)
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun ExploreScreenFullPreview() {
    ExploreScreen(parties = DefaultParties, initialSheetState = ExploreSheetState.FULL)
}

// 미리보기 **전용** 예약 방 — `DefaultParties` 와 같은 조심이 붙는다(기본 인자 자리에 두지 않는다).
// 출발 시각은 고정 기준 시각(`PreviewNow`)에서 떨어진 값이라 미리보기가 날마다 달라지지 않는다.
private val PreviewNow: Instant = Instant.parse("2026-11-09T04:00:00Z") // KST 13:00

private fun previewReservation(
    id: String,
    origin: String,
    destination: String,
    departureAt: String,
    members: Int,
    capacity: Int = 3,
    destinationNearby: Boolean? = null,
) = ReservationCardUi(
    ride = Ride(
        id = id,
        origin = origin,
        destination = destination,
        departureLabel = "",
        capacity = capacity,
        members = List(members) { dummyUser("p$id$it", "김OO") },
        farePerPerson = 0,
        totalFare = 0,
        status = RideStatus.RECRUITING,
        departureAt = departureAt,
        isConfirmed = true,
        roomType = RoomType.RESERVATION,
    ),
    destinationNearby = destinationNearby,
)

/** 「곧 출발」 — 지도 범위 기준이라는 안내가 보이는지, 마커·카드의 시각 표기가 맞는지 확인한다. */
@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun ExploreScreenSoonPreview() {
    ExploreScreen(
        parties = emptyList(),
        initialSheetState = ExploreSheetState.HALF,
        reservations = ReservationPaneUi(
            mode = ExploreMode.SOON,
            now = PreviewNow,
            items = listOf(
                previewReservation("r1", "부산대 정문", "서면역", "2026-11-09T04:20:00Z", members = 2),
                previewReservation("r2", "장전역", "부산역", "2026-11-09T04:25:00Z", members = 1),
            ),
        ),
    )
}

/** 「날짜로 찾기」 — 주간 달력 띠·도착지 칩과 「도착지도 1km 안」 배지가 보이는 상태. */
@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun ExploreScreenByDatePreview() {
    val dates = reservationDates(PreviewNow)
    ExploreScreen(
        parties = emptyList(),
        initialSheetState = ExploreSheetState.FULL,
        reservations = ReservationPaneUi(
            mode = ExploreMode.BY_DATE,
            now = PreviewNow,
            dates = dates,
            selectedDate = dates[1],
            destinationName = "서면역",
            items = listOf(
                previewReservation("r3", "부산대 정문", "서면역", "2026-11-10T00:30:00Z", members = 2, destinationNearby = true),
                previewReservation("r4", "온천장역", "사상역", "2026-11-10T09:00:00Z", members = 1, destinationNearby = false),
            ),
        ),
        onPickSearchDestination = {},
    )
}

/** 「날짜로 찾기」 0건 — 실패와 **다른 문구**인지 눈으로 확인하는 자리. */
@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun ExploreScreenByDateEmptyPreview() {
    val dates = reservationDates(PreviewNow)
    ExploreScreen(
        parties = emptyList(),
        initialSheetState = ExploreSheetState.FULL,
        reservations = ReservationPaneUi(
            mode = ExploreMode.BY_DATE,
            now = PreviewNow,
            dates = dates,
            selectedDate = dates[3],
        ),
    )
}
