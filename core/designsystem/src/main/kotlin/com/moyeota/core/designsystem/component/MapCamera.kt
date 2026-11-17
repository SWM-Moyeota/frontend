package com.moyeota.core.designsystem.component

import com.naver.maps.geometry.LatLng
import kotlin.math.PI
import kotlin.math.atan
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
): Pair<LatLng, Double>? =
    fitCameraTarget(
        points = points.map { GeoPoint(it.latitude, it.longitude) },
        widthDp = widthDp,
        heightDp = heightDp,
        insetTopDp = insetTopDp,
        insetBottomDp = insetBottomDp,
        insetSideDp = insetSideDp,
    )?.let { LatLng(it.latitude, it.longitude) to it.zoom }

/**
 * 지도 SDK 를 모르는 좌표 한 점. 카메라 계산을 JVM 단위 테스트(그리고 iOS 포팅)에서 그대로 돌리려고
 * 네이버 `LatLng` 대신 쓴다 — 계산 함수가 SDK 타입을 받으면 테스트가 SDK 를 끌어와야 한다.
 */
data class GeoPoint(val latitude: Double, val longitude: Double)

/** 카메라 하나 — 중심과 줌. [GeoPoint] 와 같은 이유로 SDK 타입을 쓰지 않는다. */
data class CameraTarget(val latitude: Double, val longitude: Double, val zoom: Double)

/** 부산 서면 — 앱의 기본 카메라([MoyeotaDefaultCamera])와 같은 점. SDK 기본값(서울)이 아니다. */
val MoyeotaDefaultGeoPoint: GeoPoint = GeoPoint(35.1578, 129.0594)

/** 한 점만 알 때의 줌 — 그 점 주변 블록이 식별되는 정도(16·20·22 가 써 온 값). */
const val SinglePointZoom: Double = 15.0

/** 아는 점이 하나도 없어 「사용자 주변」·기본 카메라를 보여 줄 때의 줌 — 동네가 한눈에 들어오는 정도. */
const val AreaFallbackZoom: Double = 14.0

/**
 * 지도에 **그려도 되는 좌표인가** — 카메라·마커가 믿어도 되는 값만 통과시킨다.
 *
 * | 거르는 값 | 왜 |
 * |---|---|
 * | null · NaN · 무한대 | 계산이 발산한다 |
 * | 위도 ±90 · 경도 ±180 밖 | 서버가 위·경도를 **뒤바꿔** 내린 결함(QA D-1)이 이 모양이다(위도 126.97) |
 * | **(0, 0)** | 값이 빠졌을 때의 기본값이다(서버 record 가 원시 `double` 이라 필드가 빠지면 0.0). 기니만 앞바다라 카메라가 그 점과 국내 점을 함께 담으려다 **최소 줌으로 바다 한가운데**를 비춘다 — 20 합류 확인이 「해외를 보여 준다」던 증상이다 |
 *
 * 한쪽만 0 인 값은 막지 않는다(적도·본초자오선 위의 실제 좌표일 수 있다 —
 * `reservationDestinationOrNull` 과 같은 규칙).
 */
fun isUsableMapCoordinate(latitude: Double?, longitude: Double?): Boolean {
    if (latitude == null || longitude == null) return false
    if (!latitude.isFinite() || !longitude.isFinite()) return false
    if (latitude < -90.0 || latitude > 90.0 || longitude < -180.0 || longitude > 180.0) return false
    return !(latitude == 0.0 && longitude == 0.0)
}

/** [isUsableMapCoordinate] 를 통과한 점만. */
fun GeoPoint?.usableOrNull(): GeoPoint? = this?.takeIf { isUsableMapCoordinate(it.latitude, it.longitude) }

/**
 * 경로 지도(20 합류 확인 등)의 카메라 — **믿을 수 있는 점들로 맞추고, 못 맞추면 정해진 순서로 물러난다.**
 * **SDK 기본 카메라(서울)로는 절대 떨어지지 않는다** — 끝까지 아무것도 없으면 앱 기본 카메라
 * ([MoyeotaDefaultGeoPoint])다.
 *
 * | 순서 | 조건 | 카메라 |
 * |---|---|---|
 * | 1 | 쓸 수 있는 점(출발·도착·경로선)이 둘 이상이고 서로 다르다 | 그 점들이 **가려지지 않는 영역**에 다 들어오게 맞춘다([fitCameraTarget] — 최대 줌 16) |
 * | 2 | 출발지만 쓸 수 있다 | 출발지, 줌 [SinglePointZoom] |
 * | 3 | 도착지만 쓸 수 있다 | 도착지, 같은 줌 |
 * | 4 | 둘 다 못 쓴다 | 사용자 주변([fallback]), 줌 [AreaFallbackZoom] |
 * | 5 | 그것도 없다 | 앱 기본 카메라, 같은 줌 |
 *
 * 출발지를 도착지보다 먼저 보는 이유: 20 에서 사용자가 실제로 가야 하는 곳은 **탑승지(출발지)**다.
 *
 * 경로선의 점도 하나하나 걸러 쓴다 — 정밀도가 다른 폴리라인을 잘못 풀면 범위 밖 좌표가 섞여 들어와
 * 카메라가 지구 반대편까지 늘어난다.
 *
 * @param heightDp 지도 뷰 **전체** 높이. 위·아래에 겹친 UI 는 [insetTopDp]·[insetBottomDp] 에 더해 넘긴다
 *   (그래야 카메라 중심이 가리지 않는 영역의 가운데로 잡힌다)
 */
fun routeCameraTarget(
    origin: GeoPoint?,
    destination: GeoPoint?,
    route: List<GeoPoint>,
    fallback: GeoPoint?,
    widthDp: Float,
    heightDp: Float,
    insetTopDp: Float = MarkerTopInsetDp,
    insetBottomDp: Float = MarkerBottomInsetDp,
    insetSideDp: Float = MarkerSideInsetDp,
): CameraTarget {
    val usableOrigin = origin.usableOrNull()
    val usableDestination = destination.usableOrNull()
    val points = route.mapNotNull { it.usableOrNull() } + listOfNotNull(usableOrigin, usableDestination)
    fitCameraTarget(points, widthDp, heightDp, insetTopDp, insetBottomDp, insetSideDp)?.let { return it }
    usableOrigin?.let { return CameraTarget(it.latitude, it.longitude, SinglePointZoom) }
    usableDestination?.let { return CameraTarget(it.latitude, it.longitude, SinglePointZoom) }
    val area = fallback.usableOrNull() ?: MoyeotaDefaultGeoPoint
    return CameraTarget(area.latitude, area.longitude, AreaFallbackZoom)
}

/**
 * [points] 가 모두 들어오는 카메라(중심·줌) — [fitMapCamera] 의 계산 본체. SDK 타입을 받지 않아
 * JVM 테스트에서 그대로 돈다. 점이 둘 미만이거나 모두 같은 점이면 null.
 */
fun fitCameraTarget(
    points: List<GeoPoint>,
    widthDp: Float,
    heightDp: Float,
    insetTopDp: Float = MarkerTopInsetDp,
    insetBottomDp: Float = MarkerBottomInsetDp,
    insetSideDp: Float = MarkerSideInsetDp,
): CameraTarget? {
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
    return CameraTarget(inverseMercatorY(centerY), (minLng + maxLng) / 2.0, zoom)
}

// 반경 원의 외접 사각형을 구하던 circleBoundsPoints 는 지웠다 — 21 매칭 대기의 탐색 반경 원이
// 유일한 사용처였고, 그 원은 서버가 반경을 고정값으로 정하면서(Backend `a668aee`) 아무것도
// 걸러내지 않는 범위를 그리는 일이 됐다. 원을 되살릴 이유가 생기면 환산도 함께 다시 쓰면 된다.

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

/**
 * 폴리라인의 실제 이동 거리(m). 이웃한 점 사이 거리를 그대로 더한다.
 *
 * 서버는 방에 **거리를 내려주지 않는다**(요금·소요시간만 준다). 20 합류 확인 등에서 「N.Nkm」 를
 * 보여 주려면 앱이 서버가 준 경로선에서 재는 수밖에 없다 — 출발·도착 **직선거리로 대체하지 않는다**.
 * 직선거리는 실제 주행 거리보다 늘 짧아 요금과 어긋나 보인다.
 *
 * 점이 2개 미만이면 0.0 — 호출부가 거리 표기를 생략한다.
 */
fun polylineDistanceMeters(points: List<LatLng>): Double {
    if (points.size < 2) return 0.0
    var total = 0.0
    for (i in 1 until points.size) {
        val step = points[i - 1].distanceTo(points[i])
        if (step.isFinite()) total += step
    }
    return total
}
