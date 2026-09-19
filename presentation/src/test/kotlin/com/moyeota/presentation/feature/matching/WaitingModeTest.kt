package com.moyeota.presentation.feature.matching

import com.moyeota.domain.model.RideStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 21 대기 화면의 모드 분기. 서버 택시 모드(taxiEnabled)에 따라 정원이 찬 방(COMPLETED=MATCHED)에서
 * 「합승 완료」를 보이고 나가기를 허용하는지가 갈린다.
 */
class WaitingModeTest {

    @Test
    fun `기사 모드에선 모집 중일 때만 나갈 수 있다`() {
        assertTrue(canLeaveParty(RideStatus.RECRUITING, taxiEnabled = true))
        assertFalse(canLeaveParty(RideStatus.MATCHED, taxiEnabled = true))
    }

    @Test
    fun `1차 배포 모드에선 정원이 찬 뒤에도 나갈 수 있다 - 서버가 모집으로 되돌린다`() {
        assertTrue(canLeaveParty(RideStatus.RECRUITING, taxiEnabled = false))
        assertTrue(canLeaveParty(RideStatus.MATCHED, taxiEnabled = false))
    }

    @Test
    fun `합승 완료는 1차 배포 모드에서 정원이 찼을 때만`() {
        assertTrue(canFinishParty(RideStatus.MATCHED, taxiEnabled = false))
        assertFalse(canFinishParty(RideStatus.RECRUITING, taxiEnabled = false))
        assertFalse(canFinishParty(RideStatus.MATCHED, taxiEnabled = true))
    }

    @Test
    fun `배차 이후 상태에선 어느 모드든 둘 다 안 된다`() {
        for (status in listOf(RideStatus.DISPATCHING, RideStatus.ONGOING, RideStatus.COMPLETED, RideStatus.CANCELED)) {
            assertFalse(canLeaveParty(status, taxiEnabled = false))
            assertFalse(canFinishParty(status, taxiEnabled = false))
        }
    }
}
