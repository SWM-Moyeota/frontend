package com.moyeota.presentation.feature.matching

import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RideStatus
import com.moyeota.domain.model.User
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 21 대기 화면의 모드 분기. 서버 택시 모드(taxiEnabled)에 따라 정원이 찬 방(COMPLETED=MATCHED)에서
 * 「합승 완료」 단계로 들어가고 나가기를 허용하는지가 갈린다.
 *
 * **단계([isFinishStage])와 권한([canFinishParty])은 다른 질문이다.** 전자는 「방이 어떤 단계인가」,
 * 후자는 「내가 누를 수 있는가」 — 방장 조건은 후자에만 걸린다. 방장 쪽 분기는
 * [WaitingHostNoticeTest] 가 따로 맡는다.
 */
class WaitingModeTest {

    private fun ride(status: RideStatus, members: List<User> = emptyList()) = Ride(
        id = "1",
        origin = "부산대",
        destination = "서면",
        departureLabel = "지금 출발",
        capacity = 3,
        members = members,
        farePerPerson = 0,
        totalFare = 0,
        status = status,
    )

    /** 방장인 나 — 권한 분기를 보려면 목록에 내가 방장으로 있어야 한다. */
    private val hostMe = User(
        id = "me",
        nickname = "나",
        verifiedLabel = "",
        rating = 0.0,
        rideCount = 0,
        isMe = true,
        isHost = true,
    )

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
    fun `합승 완료 단계는 1차 배포 모드에서 정원이 찼을 때만`() {
        assertTrue(isFinishStage(RideStatus.MATCHED, taxiEnabled = false))
        assertFalse(isFinishStage(RideStatus.RECRUITING, taxiEnabled = false))
        assertFalse(isFinishStage(RideStatus.MATCHED, taxiEnabled = true))
    }

    /** 방장이어도 단계가 아니면 누를 수 없다 — 방장 조건이 단계 조건을 대체하지 않는다. */
    @Test
    fun `방장이어도 합승 완료는 그 단계에서만`() {
        assertTrue(canFinishParty(ride(RideStatus.MATCHED, listOf(hostMe)), taxiEnabled = false))
        assertFalse(canFinishParty(ride(RideStatus.RECRUITING, listOf(hostMe)), taxiEnabled = false))
        assertFalse(canFinishParty(ride(RideStatus.MATCHED, listOf(hostMe)), taxiEnabled = true))
    }

    @Test
    fun `배차 이후 상태에선 어느 모드든 둘 다 안 된다`() {
        for (status in listOf(RideStatus.DISPATCHING, RideStatus.ONGOING, RideStatus.COMPLETED, RideStatus.CANCELED)) {
            assertFalse(canLeaveParty(status, taxiEnabled = false))
            assertFalse(isFinishStage(status, taxiEnabled = false))
            assertFalse(canFinishParty(ride(status, listOf(hostMe)), taxiEnabled = false))
        }
    }
}
