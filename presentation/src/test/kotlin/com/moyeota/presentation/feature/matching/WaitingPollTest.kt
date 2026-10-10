package com.moyeota.presentation.feature.matching

import com.moyeota.domain.model.PartyStatus
import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RideStatus
import com.moyeota.domain.model.User
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 21 대기 화면의 폴링 판단. 주기마다 가벼운 상태 조회(`/rooms/{id}/status`, 쿼리 1개)만 읽고,
 * **바뀐 게 있을 때만** 방 상세(쿼리 3개 + 경로)를 읽는다 — 부하테스트에서 상세 폴링이 전체 요청의
 * 45% 였다(Backend #177). 이 게이트가 무너지면 절감이 그대로 사라지며, 앱 화면에는 아무 증상이 없어
 * 서버 부하로만 드러난다.
 */
class WaitingPollTest {

    private fun ride(status: RideStatus, members: Int, fingerprint: String? = null) = Ride(
        id = "1", origin = "부산대", destination = "서면", departureLabel = "지금 출발",
        capacity = 4,
        members = List(members) {
            User(id = "u$it", nickname = "승객$it", verifiedLabel = "", rating = 0.0, rideCount = 0)
        },
        farePerPerson = 0, totalFare = 0, status = status, fingerprint = fingerprint,
    )

    @Test
    fun `안 바뀌었으면 상세를 읽지 않는다 - 지문이 같을 때`() {
        val held = ride(RideStatus.RECRUITING, members = 2, fingerprint = "aaaaaaaa")
        assertFalse(shouldFetchPartyDetail(PartyStatus(RideStatus.RECRUITING, 2, "aaaaaaaa"), held))
    }

    @Test
    fun `안 바뀌었으면 상세를 읽지 않는다 - 지문이 없고 상태와 인원이 같을 때`() {
        val held = ride(RideStatus.RECRUITING, members = 2)
        assertFalse(shouldFetchPartyDetail(PartyStatus(RideStatus.RECRUITING, 2, null), held))
    }

    @Test
    fun `인원이 늘면 상세를 읽는다`() {
        val held = ride(RideStatus.RECRUITING, members = 2)
        assertTrue(shouldFetchPartyDetail(PartyStatus(RideStatus.RECRUITING, 3, null), held))
    }

    @Test
    fun `상태가 바뀌면 상세를 읽는다 - 정원이 차서 배차로 넘어갈 때`() {
        val held = ride(RideStatus.MATCHED, members = 4)
        assertTrue(shouldFetchPartyDetail(PartyStatus(RideStatus.DISPATCHING, 4, null), held))
    }

    @Test
    fun `인원수가 같은 멤버 교체도 지문으로 잡는다`() {
        val held = ride(RideStatus.RECRUITING, members = 2, fingerprint = "aaaaaaaa")
        assertTrue(shouldFetchPartyDetail(PartyStatus(RideStatus.RECRUITING, 2, "bbbbbbbb"), held))
    }

    @Test
    fun `들고 있는 방이 없으면(Loading·Error) 비교 기준이 없어 상세를 읽는다`() {
        assertTrue(shouldFetchPartyDetail(PartyStatus(RideStatus.RECRUITING, 2, "aaaaaaaa"), null))
    }

    @Test
    fun `대기 단계는 모집 중과 정원 찬 상태뿐 - 그 밖이면 폴링을 끝낸다`() {
        assertTrue(isWaitingForMembers(RideStatus.RECRUITING))
        assertTrue(isWaitingForMembers(RideStatus.MATCHED))
        for (status in listOf(
            RideStatus.DISPATCHING,
            RideStatus.ONGOING,
            RideStatus.COMPLETED,
            RideStatus.CANCELED,
        )) {
            assertFalse(isWaitingForMembers(status))
        }
    }
}
