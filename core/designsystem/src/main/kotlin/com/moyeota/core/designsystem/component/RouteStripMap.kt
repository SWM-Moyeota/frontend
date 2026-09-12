package com.moyeota.core.designsystem.component

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.naver.maps.geometry.LatLng

/**
 * 경로 한 눈에 보기용 **지도 스트립**. 정보 화면(20 합류 확인 · 22 탑승 상세)의 상단에 얹는다.
 *
 * 16·21 처럼 시트가 지도를 가리지 않으므로 `contentPadding` 도, 드래그도 없다 — 스트립 높이가
 * 그대로 보이는 높이다. 카메라는 [fitMapCamera] 의 비대칭 인셋 기본값을 써서 **출발·도착 마커의
 * 머리가 잘리지 않는 선까지만** 당겨 잡는다.
 *
 * 카메라는 좌표·경로·스트립 크기가 바뀔 때만 다시 계산한다. 그 사이 사용자가 핀치·팬으로 움직인
 * 카메라는 [NaverMapView] 가 보존한다 — 폴링으로 같은 값이 다시 와도 되돌아가지 않는다.
 *
 * @param destinationPosition null 이면 지도를 띄우지 않고 [MapPlaceholder] 로 떨어진다
 *   (좌표를 아직 못 받은 로딩 상태와 못 쓸 좌표를 같게 다룬다)
 * @param routePath 서버 경로 폴리라인. 비어 있으면 마커 2개만으로 카메라를 맞춘다
 * @param useTextureView 기본 true(전환 시 검은 깜빡임 방지) — [NaverMapView] 설명 참고
 */
@Composable
fun RouteStripMap(
    modifier: Modifier = Modifier,
    originPosition: LatLng? = null,
    destinationPosition: LatLng? = null,
    routePath: List<LatLng> = emptyList(),
    useTextureView: Boolean = true,
) {
    BoxWithConstraints(modifier = modifier) {
        if (destinationPosition == null) {
            MapPlaceholder(modifier = Modifier.fillMaxSize())
            return@BoxWithConstraints
        }
        val widthDp = maxWidth.value
        val heightDp = maxHeight.value
        val fitted = remember(routePath, originPosition, destinationPosition, widthDp, heightDp) {
            fitMapCamera(
                points = routePath + listOfNotNull(originPosition, destinationPosition),
                widthDp = widthDp,
                heightDp = heightDp,
            )
        }
        RouteMapView(
            modifier = Modifier.fillMaxSize(),
            routePath = routePath,
            originPosition = originPosition,
            destinationPosition = destinationPosition,
            center = fitted?.first ?: destinationPosition,
            // 경로·출발지가 없어 fit 을 못 구하면 도착지 주변 블록이 식별될 정도로 당겨 본다
            zoom = fitted?.second ?: 15.0,
            useTextureView = useTextureView,
        )
    }
}
