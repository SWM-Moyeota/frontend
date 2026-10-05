package com.moyeota.presentation.feature.chat

import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RideStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 26 운행 중의 폴링 판단 두 개.
 *
 * 상세 폴링을 가벼운 상태 조회로 바꾸면서 "언제 끝났다고 보는가"와 "상세를 언제 읽는가"가
 * 루프 안의 조건문이 아니라 이름 있는 규칙이 됐다 — 둘 다 한 번 틀리면 화면이 영영 안 넘어가거나
 * 타지도 않은 운행의 최종 요금이 열린다.
 */
class RideFinishSignalTest {

    private fun ride(status: RideStatus = RideStatus.ONGOING) = Ride(
        id = "1", origin = "부산대", destination = "서면", departureLabel = "지금 출발",
        capacity = 2, members = emptyList(), farePerPerson = 0, totalFare = 0, status = status,
    )

    @Test
    fun `서버 FINISHED(COMPLETED)만 28 로 넘긴다`() {
        assertTrue(isRideFinished(RideStatus.COMPLETED))
    }

    @Test
    fun `운행 중 상태들은 끝으로 보지 않는다`() {
        assertFalse(isRideFinished(RideStatus.ONGOING))
        assertFalse(isRideFinished(RideStatus.DISPATCHING))
        assertFalse(isRideFinished(RideStatus.MATCHED))
        assertFalse(isRideFinished(RideStatus.RECRUITING))
    }

    @Test
    fun `CANCELED 는 완료가 아니다 - 타지 않은 운행의 최종 요금을 열면 안 된다`() {
        assertFalse(isRideFinished(RideStatus.CANCELED))
    }

    @Test
    fun `상태 조회가 실패한 주기는 아직 안 끝난 것으로 본다`() {
        assertFalse(isRideFinished(null))
    }

    @Test
    fun `지도용 상세는 못 받았을 때만 읽는다`() {
        assertTrue(shouldLoadRideDetail(null))
        assertFalse(shouldLoadRideDetail(ride()))
    }
}
