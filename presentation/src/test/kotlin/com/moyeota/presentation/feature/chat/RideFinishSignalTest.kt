package com.moyeota.presentation.feature.chat

import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RideStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 26 운행 중이 **33 도착 완료로 넘어가는 신호**.
 *
 * 화면 안에 수동 트리거가 없고 이 전이에는 SSE 이벤트도 FCM 도 없어서(서버 SSE 발행처가
 * 참여·퇴장·종료뿐) 폴링이 유일한 근거다 — 한 번 틀리면 화면이 영영 안 넘어가거나, 타지도 않은
 * 운행의 최종 요금이 열린다.
 *
 * 입력이 [Ride] 인 이유: 폴링 수단이 가벼운 상태 조회에서 **방 상세**로 바뀌었다(`/status` 가
 * 백엔드에서 삭제됐다 — `04f6be7`). 25 의
 * [com.moyeota.presentation.feature.matching.isRideStarted] 와 같은 모양이다.
 *
 * 「지도용 상세를 언제 읽는가」(`shouldLoadRideDetail`)는 사라졌다 — 폴링이 주는 게 곧 그 상세다.
 */
class RideFinishSignalTest {

    private fun ride(status: RideStatus = RideStatus.ONGOING) = Ride(
        id = "1", origin = "부산대", destination = "서면", departureLabel = "지금 출발",
        capacity = 2, members = emptyList(), farePerPerson = 0, totalFare = 0, status = status,
    )

    @Test
    fun `서버 FINISHED(COMPLETED)만 33 으로 넘긴다`() {
        assertTrue(isRideFinished(ride(RideStatus.COMPLETED)))
    }

    @Test
    fun `운행 중 상태들은 끝으로 보지 않는다`() {
        assertFalse(isRideFinished(ride(RideStatus.ONGOING)))
        assertFalse(isRideFinished(ride(RideStatus.DISPATCHING)))
        assertFalse(isRideFinished(ride(RideStatus.MATCHED)))
        assertFalse(isRideFinished(ride(RideStatus.RECRUITING)))
    }

    @Test
    fun `CANCELED 는 완료가 아니다 - 타지 않은 운행의 최종 요금을 열면 안 된다`() {
        assertFalse(isRideFinished(ride(RideStatus.CANCELED)))
    }

    @Test
    fun `상세 조회가 실패한 주기는 아직 안 끝난 것으로 본다`() {
        assertFalse(isRideFinished(null))
    }
}
