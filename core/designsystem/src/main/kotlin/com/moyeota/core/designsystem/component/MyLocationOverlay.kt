package com.moyeota.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.NaverMap
import com.naver.maps.map.overlay.LocationOverlay

/** 새 좌표까지 미끄러져 가는 시간. 갱신 주기(1초)보다 짧아야 다음 fix 전에 도착한다 */
private const val GlideMs = 800

/** 이 이상 튀면 보간하지 않고 순간이동한다 — 지도를 가로질러 기어가는 점이 더 이상하다 */
private const val SnapDistanceM = 200.0

/** 이 이하의 미세 이동은 애니메이션 없이 반영한다 (GPS 지터로 계속 애니메이션이 걸리는 것 방지) */
private const val MinMoveM = 0.5

/** 오차 원이 화면을 통째로 덮어 렌더러를 괴롭히지 않도록 두는 상한(px) */
private const val MaxAccuracyRadiusPx = 4000

/**
 * 내 위치 파란 점 — 네이버 SDK 내장 [LocationOverlay].
 *
 * 지도를 쓰는 화면이 공유한다(14 홈 · 17~19 합승 · 26 운행 중). 일반 [com.naver.maps.map.overlay.Marker] 를
 * 옮겨 찍는 것보다 네이버 지도 앱과 같은 인상을 주고, 오차 원·방향 화살표를 SDK 가 맡아 준다.
 *
 * 세 가지를 이 컴포저블이 책임진다.
 * 1. **보간** — 1초 주기 좌표를 그대로 찍으면 점이 뚝뚝 끊겨 「위치가 튄다」로 읽힌다. 등속으로 미끄러뜨린다
 *    (걷는 사람의 이동은 가감속이 없어 보이는 편이 자연스럽다). [SnapDistanceM] 이상 튀면 순간이동한다.
 * 2. **정리** — `locationOverlay` 는 지도에 붙어 있는 **싱글턴**이라 화면을 떠날 때 반드시 숨긴다.
 *    남겨 두면 같은 NaverMap 을 쓰는 다음 화면에 낡은 점이 그대로 뜬다.
 * 3. **오차 원 환산** — 반경 단위가 픽셀이라 같은 오차(m)라도 줌에 따라 화면 크기가 달라진다.
 *    카메라가 움직일 때마다 다시 환산해야 원이 지면에 붙어 있는 것처럼 보인다.
 *
 * @param map 준비된 지도. null 이면(아직 onMapReady 전) 아무것도 하지 않는다
 * @param position 최신 fix 좌표. 호출부가 유효성을 검증해 넘긴다([latLngOrNull])
 * @param accuracyMeters 오차 반경(m). **null 이면 원을 그리지 않는다** — 차량 이동처럼 반경이 정보가
 *   되지 않는 화면에서는 넘기지 않는다
 * @param bearingDegrees 이동 방향. 모르는 fix 는 화살표를 띄우지 않는다 — 없는 방향을 북쪽이라고
 *   그리면 사용자가 반대로 걸어간다
 */
@Composable
fun MyLocationOverlay(
    map: NaverMap?,
    position: LatLng,
    accuracyMeters: Float? = null,
    bearingDegrees: Float? = null,
) {
    var rendered by remember { mutableStateOf(position) }
    LaunchedEffect(position) {
        val from = rendered
        val moved = from.distanceTo(position)
        if (moved < MinMoveM || moved > SnapDistanceM || !moved.isFinite()) {
            rendered = position
            return@LaunchedEffect
        }
        // 다음 fix 가 오면 이 이펙트가 취소되고 현재 위치에서 새 목표로 다시 출발한다
        Animatable(0f).animateTo(1f, tween(GlideMs, easing = LinearEasing)) {
            rendered = LatLng(
                from.latitude + (position.latitude - from.latitude) * value,
                from.longitude + (position.longitude - from.longitude) * value,
            )
        }
    }

    DisposableEffect(map) {
        map?.locationOverlay?.isVisible = true
        onDispose { map?.locationOverlay?.isVisible = false }
    }

    LaunchedEffect(map, rendered, bearingDegrees) {
        val overlay = map?.locationOverlay ?: return@LaunchedEffect
        overlay.position = rendered
        overlay.bearing = bearingDegrees ?: 0f
        overlay.subIcon = if (bearingDegrees != null) LocationOverlay.DEFAULT_SUB_ICON_ARROW else null
    }

    DisposableEffect(map, accuracyMeters) {
        val naverMap = map
        if (naverMap == null) {
            onDispose {}
        } else {
            val overlay = naverMap.locationOverlay
            val applyRadius = { overlay.circleRadius = accuracyRadiusPx(naverMap, accuracyMeters) }
            applyRadius()
            val listener = NaverMap.OnCameraChangeListener { _, _ -> applyRadius() }
            naverMap.addOnCameraChangeListener(listener)
            onDispose { naverMap.removeOnCameraChangeListener(listener) }
        }
    }
}

/** 오차(m) → 화면 픽셀. 환산할 수 없으면 SDK 기본값에 맡긴다(원을 그리지 않는 것과 같다) */
private fun accuracyRadiusPx(map: NaverMap, accuracyMeters: Float?): Int {
    if (accuracyMeters == null || accuracyMeters <= 0f) return LocationOverlay.SIZE_AUTO
    val metersPerPixel = map.projection.metersPerPixel
    // 지도가 아직 레이아웃되지 않으면 0·NaN 이 나온다 — 다음 카메라 변화 때 다시 계산된다
    if (!metersPerPixel.isFinite() || metersPerPixel <= 0.0) return LocationOverlay.SIZE_AUTO
    return (accuracyMeters / metersPerPixel).toInt().coerceIn(0, MaxAccuracyRadiusPx)
}
