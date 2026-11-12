package com.moyeota.core.designsystem.component

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.naver.maps.geometry.LatLng

/**
 * 경로 한 눈에 보기용 **지도 스트립**. 정보 화면(20 합류 확인 · 22 탑승 상세)의 상단에 얹는다.
 *
 * 16·21 처럼 시트가 지도를 끌어 가리지 않으므로 드래그가 없다. 카메라는 [routeCameraTarget] 이 정한다:
 * **쓸 수 있는 좌표**(출발·도착·경로선 — (0,0)·NaN·범위 밖은 버린다)가 다 들어오게 맞추고, 못 맞추면
 * 출발지 → 도착지 → [fallbackPosition] → 앱 기본 카메라 순으로 물러난다. **SDK 기본 카메라(서울)로는
 * 떨어지지 않는다**(첫 카메라도 [NaverMapView] 가 생성 옵션으로 건다).
 *
 * 지도 위에 겹쳐 그려지는 UI 가 있으면 [obscuredTop]·[obscuredBottom] 으로 알린다 — 그만큼을 여백에 더해
 * 마커가 그 UI 밑에 숨지 않게 하고, 카메라 중심도 가리지 않는 영역의 가운데로 잡힌다.
 *
 * 카메라는 좌표·경로·스트립 크기가 바뀔 때만 다시 계산한다(그래서 방을 다시 읽어 좌표가 바뀌면 그때
 * 다시 맞춘다). 그 사이 사용자가 핀치·팬으로 움직인 카메라는 [NaverMapView] 가 보존한다 — 같은 값이
 * 다시 와도 되돌아가지 않는다.
 *
 * @param destinationPosition null 이고 [fallbackPosition] 도 null 이면 지도를 띄우지 않고 [MapPlaceholder]
 *   로 떨어진다(22 의 기존 동작 — 좌표를 아직 못 받은 로딩 상태와 못 쓸 좌표를 같게 다룬다)
 * @param routePath 서버 경로 폴리라인. 비어 있으면 마커 2개만으로 카메라를 맞춘다
 * @param fallbackPosition 좌표를 하나도 못 쓸 때 보여 줄 「사용자 주변」. 넘기면 자리표시자 대신 늘
 *   지도를 띄운다(20 이 내 위치 또는 앱 기본 카메라를 넘긴다)
 * @param obscuredTop 지도 **위쪽**을 덮는 UI 의 높이(20 의 소요 시간 칩: 16dp 여백 + 30dp)
 * @param obscuredBottom 지도 **아래쪽**을 덮는 UI 의 높이(20 은 시트가 지도 아래 12dp 를 덮는다)
 * @param sidePadding 좌우 여백. 마커 캡션이 가장자리에 붙지 않을 만큼
 * @param useTextureView 기본 true(전환 시 검은 깜빡임 방지) — [NaverMapView] 설명 참고
 */
@Composable
fun RouteStripMap(
    modifier: Modifier = Modifier,
    originPosition: LatLng? = null,
    destinationPosition: LatLng? = null,
    routePath: List<LatLng> = emptyList(),
    useTextureView: Boolean = true,
    fallbackPosition: LatLng? = null,
    obscuredTop: Dp = 0.dp,
    obscuredBottom: Dp = 0.dp,
    sidePadding: Dp = MarkerSideInsetDp.dp,
) {
    BoxWithConstraints(modifier = modifier) {
        if (destinationPosition == null && fallbackPosition == null) {
            MapPlaceholder(modifier = Modifier.fillMaxSize())
            return@BoxWithConstraints
        }
        val widthDp = maxWidth.value
        val heightDp = maxHeight.value
        val target = remember(
            routePath, originPosition, destinationPosition, fallbackPosition,
            widthDp, heightDp, obscuredTop, obscuredBottom, sidePadding,
        ) {
            routeCameraTarget(
                origin = originPosition?.toGeoPoint(),
                destination = destinationPosition?.toGeoPoint(),
                route = routePath.map { it.toGeoPoint() },
                fallback = fallbackPosition?.toGeoPoint(),
                widthDp = widthDp,
                heightDp = heightDp,
                insetTopDp = obscuredTop.value + MarkerTopInsetDp,
                insetBottomDp = obscuredBottom.value + MarkerBottomInsetDp,
                insetSideDp = sidePadding.value,
            )
        }
        RouteMapView(
            modifier = Modifier.fillMaxSize(),
            // 못 쓸 좌표에는 마커·선을 그리지 않는다 — (0,0) 에 핀을 꽂아 봐야 화면 밖 바다다
            routePath = routePath.filter { isUsableMapCoordinate(it.latitude, it.longitude) },
            originPosition = originPosition?.takeIf { isUsableMapCoordinate(it.latitude, it.longitude) },
            destinationPosition = destinationPosition?.takeIf { isUsableMapCoordinate(it.latitude, it.longitude) },
            center = LatLng(target.latitude, target.longitude),
            zoom = target.zoom,
            useTextureView = useTextureView,
        )
    }
}

private fun LatLng.toGeoPoint(): GeoPoint = GeoPoint(latitude, longitude)
