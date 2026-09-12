package com.moyeota.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.NaverMap
import com.naver.maps.map.overlay.CircleOverlay
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.PathOverlay
import com.naver.maps.map.util.MarkerIcons

/**
 * Google Encoded Polyline(정밀도 1e5, lat→lng 순)을 좌표 목록으로 디코딩한다.
 *
 * 서버 `Ride.routePolyline` 이 이 형식이라 그대로 넣으면 된다.
 * 형식이 깨진 문자열은 예외 대신 **거기까지 읽은 좌표만** 돌려준다 — 지도는 부가 정보라
 * 폴리라인 하나 때문에 화면이 죽으면 안 된다.
 */
fun decodePolyline(encoded: String): List<LatLng> {
    val points = mutableListOf<LatLng>()
    var index = 0
    var lat = 0
    var lng = 0
    while (index < encoded.length) {
        val dLat = decodeChunk(encoded, index) ?: break
        index = dLat.second
        lat += dLat.first
        val dLng = decodeChunk(encoded, index) ?: break
        index = dLng.second
        lng += dLng.first
        points += LatLng(lat / 1e5, lng / 1e5)
    }
    return points
}

// 한 값(위도 또는 경도 델타)을 읽어 (델타, 다음 인덱스)를 돌려준다. 문자열이 중간에 끊기면 null.
private fun decodeChunk(encoded: String, start: Int): Pair<Int, Int>? {
    var index = start
    var shift = 0
    var result = 0
    var byte: Int
    do {
        if (index >= encoded.length) return null
        byte = encoded[index++].code - 63
        result = result or ((byte and 0x1F) shl shift)
        shift += 5
    } while (byte >= 0x20)
    val delta = if (result and 1 != 0) (result shr 1).inv() else result shr 1
    return delta to index
}

/**
 * 지도에 그려도 되는 좌표일 때만 [LatLng] 를 만든다. 아니면 null (호출부가 렌더를 건너뛴다).
 *
 * 거르는 값: null · NaN/Infinite · 위도 ±90 또는 경도 ±180 을 벗어난 값.
 *
 * 범위 검사가 필요한 이유: 서버가 위경도를 **뒤바꿔** 내려보내는 결함이 있다(QA D-1 —
 * `{"longitude":37.56, "latitude":126.97}`). 이때 위도 126.97 은 지구상에 없는 값인데도
 * SDK 의 `LatLng.isValid()` 는 NaN·Infinite 만 보므로 **통과시킨다**. 그래서 직접 범위를 본다.
 *
 * 좌표를 프론트에서 되돌려 끼우지(swap) 않는다 — 백엔드가 D-1 을 고치는 순간 다시 뒤집혀서
 * 같은 버그를 반대 방향으로 재현하게 된다. 범위 검증 + 렌더 스킵만이 수정 전후 모두 안전하다.
 */
fun latLngOrNull(latitude: Double?, longitude: Double?): LatLng? {
    if (latitude == null || longitude == null) return null
    if (!latitude.isFinite() || !longitude.isFinite()) return null
    if (latitude !in LatLng.MINIMUM_LATITUDE..LatLng.MAXIMUM_LATITUDE) return null
    if (longitude !in LatLng.MINIMUM_LONGITUDE..LatLng.MAXIMUM_LONGITUDE) return null
    return LatLng(latitude, longitude)
}

/**
 * 경로 · 마커를 얹은 네이버 지도 (공용 컴포넌트).
 *
 * [NaverMapView] 를 감싸 오버레이 생명주기만 관리한다. 오버레이는 `map` 프로퍼티로 붙고
 * 떨어지므로, 값이 바뀔 때마다 DisposableEffect 로 **떼었다 다시 붙인다**(누수·중복 방지).
 *
 * 카메라 중심은 [center] 로 고정한다. 폴링으로 움직이는 [driverPosition] 을 중심으로 쓰면
 * 갱신마다 카메라가 튀어 사용자의 팬·줌을 덮어쓴다 — 마커만 움직이게 둔다.
 *
 * @param routePath 경로 좌표. [decodePolyline] 결과를 그대로 넣는다. 2점 미만이면 그리지 않는다
 * @param driverPosition 기사 현재 위치. 아직 못 받았으면 null (마커 미표시)
 * @param radiusCircle 반경 원(21 매칭 대기의 탐색 반경). null 이면 그리지 않는다
 * @param myPosition 내 위치 파란 점. **null 이면 SDK 의 locationOverlay 를 아예 건드리지 않는다** —
 *   26 운행중처럼 화면이 오버레이를 직접 관리하는 경우와 충돌하지 않기 위해서다
 * @param useTextureView 기본 true(전환 시 검은 깜빡임 방지) — [NaverMapView] 설명 참고
 * @param onMapReady 지도 인스턴스가 필요할 때만 쓴다(카메라 이동 구독 등). 마커·경로는 이
 *   컴포저블이 이미 관리하므로, 여기서 또 붙이면 생명주기가 이원화된다
 */
@Composable
fun RouteMapView(
    modifier: Modifier = Modifier,
    routePath: List<LatLng> = emptyList(),
    driverPosition: LatLng? = null,
    originPosition: LatLng? = null,
    destinationPosition: LatLng? = null,
    radiusCircle: MapRadiusCircle? = null,
    myPosition: LatLng? = null,
    center: LatLng = originPosition ?: routePath.firstOrNull() ?: MoyeotaDefaultCamera,
    zoom: Double = 14.0,
    contentPadding: PaddingValues = PaddingValues(),
    useTextureView: Boolean = true,
    onMapReady: (NaverMap) -> Unit = {},
) {
    // @Preview 에서는 네이티브 지도가 뜨지 않는다 (NaverMapView 와 동일한 대체 처리)
    if (LocalInspectionMode.current) {
        Box(modifier = modifier.background(MoyeotaColor.SurfaceSoft))
        return
    }

    var map by remember { mutableStateOf<NaverMap?>(null) }
    val density = LocalDensity.current
    // 람다가 매 컴포지션마다 새로 생겨도 NaverMapView 의 이펙트가 재실행되지 않게 최신 참조만 갱신
    val currentOnMapReady by rememberUpdatedState(onMapReady)

    NaverMapView(
        modifier = modifier,
        center = center,
        zoom = zoom,
        contentPadding = contentPadding,
        useTextureView = useTextureView,
        onMapReady = {
            map = it
            currentOnMapReady(it)
        },
    )

    DisposableEffect(map, routePath) {
        val naverMap = map
        val overlay = if (naverMap != null && routePath.size >= 2) {
            PathOverlay().apply {
                coords = routePath
                width = with(density) { 5.dp.roundToPx() }
                outlineWidth = 0
                color = MoyeotaColor.RouteShared.toArgb()
                this.map = naverMap
            }
        } else {
            null
        }
        onDispose { overlay?.map = null }
    }

    // 반경 원 — 경로·마커보다 먼저 붙여 마커가 원 위로 오게 한다
    DisposableEffect(map, radiusCircle, density) {
        val naverMap = map
        val circle = if (naverMap != null && radiusCircle != null && radiusCircle.radiusMeters > 0.0) {
            CircleOverlay().apply {
                this.center = radiusCircle.center
                this.radius = radiusCircle.radiusMeters
                this.color = MoyeotaColor.Primary500.copy(alpha = RadiusFillAlpha).toArgb()
                this.outlineColor = MoyeotaColor.Primary500.toArgb()
                this.outlineWidth = with(density) { 2.dp.roundToPx() }
                this.map = naverMap
            }
        } else {
            null
        }
        onDispose { circle?.map = null }
    }

    // 내 위치 파란 점. locationOverlay 는 지도에 붙어 있는 **싱글턴**이라, 쓰지 않는 화면에서는
    // 손대지 않고(overlay = null) 떠날 때만 숨긴다 — 남겨 두면 다음 화면에 낡은 점이 그대로 뜬다.
    DisposableEffect(map, myPosition) {
        val naverMap = map
        val overlay = if (naverMap != null && myPosition != null) {
            naverMap.locationOverlay.apply {
                this.position = myPosition
                this.isVisible = true
            }
        } else {
            null
        }
        onDispose { overlay?.isVisible = false }
    }

    MapMarker(map, originPosition, MoyeotaColor.MarkerOrigin.toArgb(), "출발")
    MapMarker(map, destinationPosition, MoyeotaColor.MarkerDestination.toArgb(), "도착")
    MapMarker(map, driverPosition, MoyeotaColor.InkPrimary.toArgb(), "기사님")
}

/** 지도에 그리는 반경 원. 중심과 반경(m)만 있으면 된다 — 색은 디자인 토큰으로 고정한다. */
data class MapRadiusCircle(val center: LatLng, val radiusMeters: Double)

// 원 안쪽 채우기 투명도. 지도의 도로·지명이 비쳐야 「이 범위」로 읽히지 덮개로 보이지 않는다.
private const val RadiusFillAlpha = 0.12f

@Composable
private fun MapMarker(map: NaverMap?, position: LatLng?, tint: Int, caption: String) {
    DisposableEffect(map, position, tint, caption) {
        val marker = if (map != null && position != null) {
            Marker().apply {
                this.position = position
                icon = MarkerIcons.BLACK
                iconTintColor = tint
                captionText = caption
                this.map = map
            }
        } else {
            null
        }
        onDispose { marker?.map = null }
    }
}
