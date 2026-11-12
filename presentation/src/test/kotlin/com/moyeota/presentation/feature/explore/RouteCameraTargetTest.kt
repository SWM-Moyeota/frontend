package com.moyeota.presentation.feature.explore

import com.moyeota.core.designsystem.component.AreaFallbackZoom
import com.moyeota.core.designsystem.component.CameraTarget
import com.moyeota.core.designsystem.component.GeoPoint
import com.moyeota.core.designsystem.component.MoyeotaDefaultGeoPoint
import com.moyeota.core.designsystem.component.SinglePointZoom
import com.moyeota.core.designsystem.component.fitCameraTarget
import com.moyeota.core.designsystem.component.isUsableMapCoordinate
import com.moyeota.core.designsystem.component.routeCameraTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 20 합류 확인 지도의 **카메라 맞춤과 물러나는 순서** — `routeCameraTarget`.
 *
 * 오너 UX 피드백(2026-11): 「가끔 엉뚱한 곳, 심지어 해외가 보인다」. 원인은 (0,0) 같은 **값이 빠졌을 때의
 * 기본 좌표**가 카메라 범위에 섞여 기니만 앞바다까지 담으려 했던 것이다. 지도는 스크린샷 없이는 확인할 수
 * 없으므로 계산을 SDK 없는 순수 함수로 빼고 여기서 고정한다:
 * - 쓸 수 있는 점 둘 이상 → 그 점들의 **경계에 맞춘다**(최대 줌 16)
 * - (0,0) · NaN · 범위 밖은 **버린다**
 * - 못 맞추면 출발지 → 도착지 → 사용자 주변 → 앱 기본 카메라 순. **SDK 기본(서울)으로는 떨어지지 않는다**
 */
class RouteCameraTargetTest {

    private val seomyeon = GeoPoint(35.1578, 129.0594)
    private val pnu = GeoPoint(35.2313, 129.0846)
    private val myArea = GeoPoint(35.1796, 129.0756)

    /** 20 의 지도 스트립(393 × 172dp)과 같은 크기·여백. */
    private fun target(
        origin: GeoPoint?,
        destination: GeoPoint?,
        route: List<GeoPoint> = emptyList(),
        fallback: GeoPoint? = myArea,
    ): CameraTarget = routeCameraTarget(
        origin = origin,
        destination = destination,
        route = route,
        fallback = fallback,
        widthDp = 393f,
        heightDp = 172f,
        insetTopDp = 46f + 55f,
        insetBottomDp = 12f + 20f,
        insetSideDp = 48f,
    )

    private fun CameraTarget.isNear(point: GeoPoint, tolerance: Double = 0.05): Boolean =
        kotlin.math.abs(latitude - point.latitude) < tolerance && kotlin.math.abs(longitude - point.longitude) < tolerance

    @Test
    fun `출발지와 도착지가 있으면 둘 사이에 맞춘다`() {
        val camera = target(origin = pnu, destination = seomyeon)

        // 가운데는 두 점 사이(위도는 여백 비대칭으로 조금 북쪽으로 옮겨진다), 줌은 도시 단위
        assertTrue(camera.longitude in seomyeon.longitude..pnu.longitude)
        assertTrue(camera.latitude in seomyeon.latitude..(pnu.latitude + 0.05))
        assertTrue("두 점이 다 들어오려면 축소돼 있어야 한다: ${camera.zoom}", camera.zoom in 8.0..14.0)
    }

    /** 아주 가까운 두 점에 거리 단위까지 당기지 않는다 — 최대 줌 16. */
    @Test
    fun `아주 가까운 두 점도 줌 16 을 넘지 않는다`() {
        val camera = target(origin = seomyeon, destination = GeoPoint(seomyeon.latitude + 0.0001, seomyeon.longitude))

        assertEquals(16.0, camera.zoom, 1e-9)
    }

    /** ★ 「해외가 보인다」의 원인 — (0,0) 이 범위에 섞이면 바다 한가운데를 비춘다. 버리고 나머지로 맞춘다. */
    @Test
    fun `0,0 출발지는 버리고 도착지로 물러난다`() {
        val camera = target(origin = GeoPoint(0.0, 0.0), destination = seomyeon)

        assertEquals(CameraTarget(seomyeon.latitude, seomyeon.longitude, SinglePointZoom), camera)
    }

    @Test
    fun `도착지를 못 쓰면 출발지에 줌 15 로 선다`() {
        assertEquals(
            CameraTarget(pnu.latitude, pnu.longitude, SinglePointZoom),
            target(origin = pnu, destination = GeoPoint(Double.NaN, 129.0)),
        )
        assertEquals(
            CameraTarget(pnu.latitude, pnu.longitude, SinglePointZoom),
            target(origin = pnu, destination = null),
        )
    }

    @Test
    fun `둘 다 못 쓰면 사용자 주변으로 물러난다`() {
        val camera = target(origin = GeoPoint(0.0, 0.0), destination = null, fallback = myArea)

        assertEquals(CameraTarget(myArea.latitude, myArea.longitude, AreaFallbackZoom), camera)
    }

    /** 끝까지 아무것도 없어도 **앱 기본 카메라**(부산 서면)다 — SDK 기본값(서울)이 아니다. */
    @Test
    fun `사용자 위치도 없으면 앱 기본 카메라다`() {
        val camera = target(origin = null, destination = GeoPoint(0.0, 0.0), fallback = null)

        assertEquals(CameraTarget(MoyeotaDefaultGeoPoint.latitude, MoyeotaDefaultGeoPoint.longitude, AreaFallbackZoom), camera)
        assertFalse("SDK 기본(서울 시청 부근)이 아니다", camera.isNear(GeoPoint(37.5666, 126.9784), tolerance = 1.0))
    }

    /** 위·경도가 뒤바뀐 응답(QA D-1, 위도 126.97)과 깨진 경로선 점도 버린다. */
    @Test
    fun `범위 밖 좌표와 깨진 경로선 점은 카메라 범위에 들지 않는다`() {
        val camera = target(
            origin = GeoPoint(126.97, 37.56), // 뒤바뀐 좌표 — 위도 126.97 은 없다
            destination = seomyeon,
            route = listOf(pnu, GeoPoint(351.0, 1290.0), GeoPoint(0.0, 0.0), seomyeon),
        )

        // 남은 점은 서면·부산대 둘 — 그 사이에 맞춘다(해외·바다가 아니다)
        assertTrue(camera.isNear(GeoPoint((seomyeon.latitude + pnu.latitude) / 2, (seomyeon.longitude + pnu.longitude) / 2), 0.1))
        assertTrue(camera.zoom > 8.0)
    }

    @Test
    fun `같은 점 두 개는 맞출 수 없어 그 점으로 물러난다`() {
        assertNull(fitCameraTarget(listOf(seomyeon, seomyeon), 393f, 172f))
        assertEquals(
            CameraTarget(seomyeon.latitude, seomyeon.longitude, SinglePointZoom),
            target(origin = seomyeon, destination = seomyeon),
        )
    }

    @Test
    fun `쓸 수 있는 좌표의 기준`() {
        assertTrue(isUsableMapCoordinate(35.15, 129.05))
        assertFalse("값이 빠졌을 때의 기본값", isUsableMapCoordinate(0.0, 0.0))
        assertTrue("한쪽만 0 은 실제 좌표일 수 있다", isUsableMapCoordinate(0.0, 129.0))
        assertFalse(isUsableMapCoordinate(null, 129.0))
        assertFalse(isUsableMapCoordinate(Double.NaN, 129.0))
        assertFalse(isUsableMapCoordinate(35.0, Double.POSITIVE_INFINITY))
        assertFalse(isUsableMapCoordinate(126.97, 37.56))
    }
}
