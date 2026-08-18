package com.moyeota.data.remote

import com.moyeota.data.remote.dto.PartyDetailResponse
import com.moyeota.data.remote.dto.PartyListResponse
import com.moyeota.domain.model.RideStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class PartyMappersTest {

    @Test
    fun `백엔드 PartyStatus 를 RideStatus 로 매핑한다`() {
        assertEquals(RideStatus.RECRUITING, partyStatusToRideStatus("ACTIVE"))
        assertEquals(RideStatus.MATCHED, partyStatusToRideStatus("COMPLETED"))
        assertEquals(RideStatus.DISPATCHING, partyStatusToRideStatus("MATCHING"))
        assertEquals(RideStatus.COMPLETED, partyStatusToRideStatus("FINISHED"))
        assertEquals(RideStatus.COMPLETED, partyStatusToRideStatus("CANCELED"))
    }

    @Test
    fun `알 수 없는 상태는 RECRUITING 으로 처리한다`() {
        assertEquals(RideStatus.RECRUITING, partyStatusToRideStatus("SOMETHING_NEW"))
    }

    @Test
    fun `목록 PartyItem 을 Ride 로 매핑한다`() {
        val item = PartyListResponse.PartyItem(
            partyId = 7,
            departure = "서울시청",
            destination = "강남역",
            currentMembers = 2,
            capacity = 3,
            status = "ACTIVE",
        )

        val ride = item.toRide()

        assertEquals("7", ride.id)
        assertEquals("서울시청", ride.origin)
        assertEquals("강남역", ride.destination)
        assertEquals(3, ride.capacity)
        assertEquals(2, ride.members.size)
        assertEquals(RideStatus.RECRUITING, ride.status)
        assertEquals(0, ride.farePerPerson)
        assertEquals(0, ride.totalFare)
    }

    @Test
    fun `상세 응답을 Ride 로 매핑하고 방장을 구분한다`() {
        val detail = PartyDetailResponse(
            id = 7,
            hostId = 1,
            departureLat = 37.5665,
            departureLng = 126.9780,
            destinationLat = 37.4979,
            destinationLng = 127.0276,
            departure = "서울시청",
            destination = "강남역",
            capacity = 3,
            currentMembers = 2,
            departureRadius = 500,
            destinationRadius = 500,
            status = "MATCHING",
            createdAt = "2026-08-17T09:00:00Z",
            members = listOf(
                PartyDetailResponse.MemberInfo(memberId = 1, isHost = true, joinedAt = "2026-08-17T09:00:00Z"),
                PartyDetailResponse.MemberInfo(memberId = 3, isHost = false, joinedAt = "2026-08-17T09:01:00Z"),
            ),
        )

        val ride = detail.toRide()

        assertEquals("7", ride.id)
        assertEquals("서울시청", ride.origin)
        assertEquals("강남역", ride.destination)
        assertEquals(RideStatus.DISPATCHING, ride.status)
        assertEquals(2, ride.members.size)
        assertEquals("1", ride.members[0].id)
        assertEquals("방장", ride.members[0].nickname)
        assertEquals("멤버 3", ride.members[1].nickname)
    }
}
