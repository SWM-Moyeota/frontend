package com.moyeota.presentation.feature.matching

import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RideStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 25 배차 상태가 **26 운행 중으로 넘어가는 신호**.
 *
 * 화면 안에 수동 트리거가 없어 이 판정이 전이의 발화점 전부다. 게다가 이 전이에는 SSE 이벤트도 FCM 도
 * 없어서(서버 SSE 발행처가 참여·퇴장·종료뿐) 폴링이 유일한 근거다 — 한 번 틀리면 승객이 택시에 타고
 * 있는데도 화면은 「기사님이 오고 있어요」에 머문다.
 *
 * 26 의 [com.moyeota.presentation.feature.chat.isRideFinished] 와 짝이며, 폴링 수단이 방 상세로
 * 바뀌면서(가벼운 상태 조회가 서버에서 삭제됐다 — `04f6be7`) 둘 다 [Ride] 를 받는다.
 */
class RideStartSignalTest {

    private fun ride(status: RideStatus, driverId: Long? = 7L) = Ride(
        id = "1", origin = "부산대", destination = "서면", departureLabel = "지금 출발",
        capacity = 2, members = emptyList(), farePerPerson = 0, totalFare = 0,
        status = status, driverId = driverId,
    )

    @Test
    fun `서버 IN_RIDE(ONGOING)만 26 으로 넘긴다`() {
        assertTrue(isRideStarted(ride(RideStatus.ONGOING)))
    }

    @Test
    fun `배차 단계는 아직 탑승이 아니다 - 기사가 배정돼도 board 전까지는 25 에 머문다`() {
        assertFalse(isRideStarted(ride(RideStatus.DISPATCHING, driverId = null)))
        assertFalse(isRideStarted(ride(RideStatus.DISPATCHING, driverId = 7L)))
        assertFalse(isRideStarted(ride(RideStatus.MATCHED)))
        assertFalse(isRideStarted(ride(RideStatus.RECRUITING)))
    }

    /**
     * 3분 타임아웃으로 서버가 방을 닫은 경우다. 25 가 「기사님을 찾지 못했어요」로 다뤄야 하므로
     * 탑승으로 접으면 안 된다 — 타지도 않은 운행 화면이 열린다.
     */
    @Test
    fun `CANCELED 는 탑승이 아니다`() {
        assertFalse(isRideStarted(ride(RideStatus.CANCELED)))
    }

    /** 이미 끝난 방을 뒤늦게 받아도 25 → 26 전이를 다시 일으키지 않는다. */
    @Test
    fun `COMPLETED 는 탑승이 아니다`() {
        assertFalse(isRideStarted(ride(RideStatus.COMPLETED)))
    }

    @Test
    fun `조회가 실패한 주기는 아직 아닌 것으로 본다`() {
        assertFalse(isRideStarted(null))
    }
}
