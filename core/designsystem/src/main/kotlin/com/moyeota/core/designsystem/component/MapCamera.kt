package com.moyeota.core.designsystem.component

import com.naver.maps.geometry.LatLng
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.sin

/**
 * 마커 아이콘([com.naver.maps.map.util.MarkerIcons] BLACK)이 좌표(꼭짓점)에서
 * **위로 솟는 높이 ≈ 49dp** + 여유 6dp.
 *
 * 예전 코드의 「~34dp」는 오기였다. 잘리지 않은 마커를 스크린샷에서 실측하면
 * 129px / density 2.625 = 약 49dp 다(QA D-1). 34dp 를 믿고 사방을 같은 값으로 비우는 바람에
 * 최상단 마커의 머리가 늘 잘려 있었다.
 */
const val MarkerTopInsetDp = 55f

/**
 * 캡션(「출발」/「도착」)이 꼭짓점 아래로 내려가는 높이 ~14dp + 여유 6dp.
 * 위쪽과 같은 6dp 여유를 두되 아이콘보다 훨씬 낮아 크게 잡을 이유가 없다 —
 * 좁은 지도(160dp 스트립)에서 아래 여백을 아끼는 만큼 경로가 커진다.
 */
const val MarkerBottomInsetDp = 20f

/** 아이콘 폭의 절반 + 캡션이 좌우로 삐져나오는 정도. 가로는 애초에 넉넉해 빡빡할 일이 없다. */
const val MarkerSideInsetDp = 24f

/**
 * [points] 가 모두 들어오는 카메라(중심·줌)를 구한다. 못 구하면 null.
 *
 * 네이버 지도에도 `CameraUpdate.fitBounds` 가 있지만, 그건 `NaverMap` 인스턴스를 직접 잡아
 * 명령형으로 호출해야 한다 — [RouteMapView] 는 center/zoom 을 선언형으로 받고 사용자의 카메라를
 * 보존하는 구조라, 거기에 명령형 이동을 섞으면 두 주인이 생긴다. 그래서 값만 계산해 넘긴다.
 *
 * 웹 메르카토르 기준 계산: 줌 z 에서 세계 지도 폭이 [WORLD_TILE_DP]·2^z **dp** 이므로,
 * 필요한 폭(높이)을 뷰포트 dp 로 나눈 비율의 로그가 곧 줌이다. 가로·세로 중 더 빡빡한 쪽을 쓴다.
 *
 * 여백은 **사방이 다르다**. 마커 아이콘이 좌표 꼭짓점에서 위로만 길게 솟기 때문이다 —
 * 사방을 같은 값으로 비우면 위쪽 마커는 잘리는데 아래·좌우는 쓰지도 않을 여백만 먹는다.
 *
 * @param heightDp **시트·바에 가리지 않고 실제로 보이는** 지도 높이. 뷰 전체 높이가 아니다
 * @param insetTopDp 위쪽 여백. 마커 아이콘 높이를 덮어야 한다 ([MarkerTopInsetDp])
 * @param insetBottomDp 아래쪽 여백. 캡션 높이만 덮으면 된다
 * @param insetSideDp 좌우 여백
 */
fun fitMapCamera(
    points: List<LatLng>,
    widthDp: Float,
    heightDp: Float,
    insetTopDp: Float = MarkerTopInsetDp,
    insetBottomDp: Float = MarkerBottomInsetDp,
    insetSideDp: Float = MarkerSideInsetDp,
): Pair<LatLng, Double>? {
    if (points.size < 2 || widthDp <= 0f || heightDp <= 0f) return null

    val minLat = points.minOf { it.latitude }
    val maxLat = points.maxOf { it.latitude }
    val minLng = points.minOf { it.longitude }
    val maxLng = points.maxOf { it.longitude }

    val minY = mercatorY(minLat)
    val maxY = mercatorY(maxLat)

    // 두 점이 사실상 같은 좌표면(핀을 겹쳐 찍은 경우) 폭이 0 이라 줌이 발산한다
    val latFraction = (maxY - minY) / (2 * PI)
    val lngFraction = (maxLng - minLng) / 360.0
    if (latFraction <= 0.0 && lngFraction <= 0.0) return null

    // 뷰포트가 아주 낮으면 세로 인셋 합이 높이를 다 먹어 경로가 들어갈 자리가 없어진다.
    // 그때만 위·아래를 같은 비율로 줄여 가용 높이를 최소 40% 는 남긴다(비율은 유지된다).
    val verticalInset = insetTopDp + insetBottomDp
    val shrink = if (verticalInset > heightDp * 0.6f) (heightDp * 0.6f) / verticalInset else 1f
    val topDp = insetTopDp * shrink
    val bottomDp = insetBottomDp * shrink

    val usableHeight = (heightDp - topDp - bottomDp).coerceAtLeast(1f)
    val usableWidth = (widthDp - 2 * insetSideDp).coerceAtLeast(1f)

    val zoomForLat = if (latFraction > 0.0) log2(usableHeight / WORLD_TILE_DP / latFraction) else Double.MAX_VALUE
    val zoomForLng = if (lngFraction > 0.0) log2(usableWidth / WORLD_TILE_DP / lngFraction) else Double.MAX_VALUE
    val zoom = minOf(zoomForLat, zoomForLng).coerceIn(MIN_FIT_ZOOM, MAX_FIT_ZOOM)

    // 카메라 타깃은 **가시 영역의 정중앙**에 놓인다. 위 여백이 아래보다 크면 좌표 묶음의 중심은
    // 화면 중앙보다 (top − bottom)/2 dp 만큼 **아래**에 있어야 하므로, 타깃을 그만큼 북쪽으로 올린다.
    // (단순 평균이 아니라 메르카토르 y 로 계산해야 화면에서 위아래로 치우치지 않는다.)
    val offsetDp = (topDp - bottomDp) / 2.0
    val mercatorPerDp = 2 * PI / (WORLD_TILE_DP * 2.0.pow(zoom))
    val centerY = (minY + maxY) / 2.0 + offsetDp * mercatorPerDp
    return LatLng(inverseMercatorY(centerY), (minLng + maxLng) / 2.0) to zoom
}

/**
 * 반경 [radiusMeters] 원의 외접 사각형 꼭짓점 4개. [fitMapCamera] 의 points 에 넣으면
 * **원 전체가 화면에 들어오도록** 줌이 잡힌다(중심 좌표 하나만 넣으면 원이 잘린다).
 *
 * 위경도 ↔ m 환산은 구면 근사다. 반경이 수백 m 수준이라 이 오차(0.1% 미만)는 화면에서 보이지 않는다.
 */
fun circleBoundsPoints(center: LatLng, radiusMeters: Double): List<LatLng> {
    if (radiusMeters <= 0.0 || !radiusMeters.isFinite()) return listOf(center)
    val deltaLat = radiusMeters / METERS_PER_DEGREE_LAT
    // 경도 1도의 길이는 위도가 높을수록 짧아진다. 극지방(cos≈0)에서 발산하지 않게 하한을 둔다.
    val cosLat = cos(center.latitude * PI / 180.0).coerceAtLeast(0.01)
    val deltaLng = radiusMeters / (METERS_PER_DEGREE_LAT * cosLat)
    return listOf(
        LatLng((center.latitude - deltaLat).coerceAtLeast(LatLng.MINIMUM_LATITUDE), center.longitude),
        LatLng((center.latitude + deltaLat).coerceAtMost(LatLng.MAXIMUM_LATITUDE), center.longitude),
        LatLng(center.latitude, (center.longitude - deltaLng).coerceAtLeast(LatLng.MINIMUM_LONGITUDE)),
        LatLng(center.latitude, (center.longitude + deltaLng).coerceAtMost(LatLng.MAXIMUM_LONGITUDE)),
    )
}

private const val METERS_PER_DEGREE_LAT = 111_320.0

/**
 * 네이버 지도의 줌 1 단계당 세계 지도 폭 (dp).
 *
 * 구글·OSM 계열의 통상값인 256px 이 **아니다** — 네이버는 타일이 두 배 크고 dp 기준이라,
 * 같은 축척을 구글 줌 z+1 로 표현한다. 256 으로 계산하면 두 배 넘게 당겨진 화면이 나온다.
 * 실측 근거: 16 화면에서 `NaverMap.contentBounds` 를 찍어보면 줌 12.11 · 뷰포트 411dp 폭이
 * 경도 0.065° 를 덮는다 → 세계 폭 = 411 / (0.065/360) / 2^12.11 ≈ 512dp.
 */
private const val WORLD_TILE_DP = 512.0
private const val MIN_FIT_ZOOM = 6.0
private const val MAX_FIT_ZOOM = 16.0

private fun mercatorY(latitude: Double): Double {
    val s = sin(latitude * PI / 180.0).coerceIn(-0.9999, 0.9999)
    return ln((1 + s) / (1 - s)) / 2.0
}

private fun inverseMercatorY(y: Double): Double = (2 * atan(exp(y)) - PI / 2) * 180.0 / PI
