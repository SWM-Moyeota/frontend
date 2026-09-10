package com.moyeota.presentation.feature.matching

import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RideStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 배차 폴링이 기사 정보·위치를 **배정된 뒤에만** 묻는지.
 *
 * 서버 `MATCHING` 과 `DRIVER_ASSIGNED` 는 앱에서 둘 다 [RideStatus.DISPATCHING] 이라 status 로는
 * 배정 여부를 알 수 없다. 이 게이트가 무너지면 「기사 찾는 중」 3분 동안 승객마다 5초에 한 번
 * 서버에 `DRIVER_NOT_ASSIGNED` 409 + WARN 로그가 쌓인다 — 앱 화면에는 아무 증상이 없어 서버 로그로만 드러난다.
 */
class DriverPollGateTest {

    private fun ride(status: RideStatus, driverId: Long?) = Ride(
        id = "1", origin = "부산대", destination = "서면", departureLabel = "지금 출발",
        capacity = 2, members = emptyList(), farePerPerson = 0, totalFare = 0,
        status = status, driverId = driverId,
    )

    @Test
    fun `기사 찾는 중(MATCHING→DISPATCHING, driverId 없음)에는 기사 정보를 묻지 않는다`() {
        val searching = ride(RideStatus.DISPATCHING, driverId = null)
        assertFalse(shouldFetchDriverInfo(searching, alreadyLoaded = false))
        assertFalse(shouldFetchDriverLocation(searching))
    }

    @Test
    fun `배정되면(driverId 있음) 정보와 위치를 묻는다`() {
        val assigned = ride(RideStatus.DISPATCHING, driverId = 7L)
        assertTrue(shouldFetchDriverInfo(assigned, alreadyLoaded = false))
        assertTrue(shouldFetchDriverLocation(assigned))
    }

    @Test
    fun `운행 중에도 driverId 가 기준이다`() {
        assertTrue(shouldFetchDriverLocation(ride(RideStatus.ONGOING, driverId = 7L)))
        assertFalse(shouldFetchDriverLocation(ride(RideStatus.ONGOING, driverId = null)))
    }

    @Test
    fun `기사 정보는 한 번 받으면 다시 묻지 않는다`() {
        assertFalse(shouldFetchDriverInfo(ride(RideStatus.DISPATCHING, driverId = 7L), alreadyLoaded = true))
    }

    @Test
    fun `방 상세를 아직 못 받았으면 아무것도 묻지 않는다`() {
        assertFalse(shouldFetchDriverInfo(null, alreadyLoaded = false))
        assertFalse(shouldFetchDriverLocation(null))
    }
}
