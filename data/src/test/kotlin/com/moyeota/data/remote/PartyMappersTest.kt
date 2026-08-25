package com.moyeota.data.remote

import com.moyeota.data.remote.dto.OpenPartyResponse
import com.moyeota.data.remote.dto.PartyDetailResponse
import com.moyeota.data.remote.dto.PartyListResponse
import com.moyeota.domain.model.NewParty
import com.moyeota.domain.model.RideStatus
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PartyMappersTest {

    private val json = Json { ignoreUnknownKeys = true }

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
        assertEquals("1", ride.hostId)
        assertEquals(37.5665, ride.originLat!!, 0.0001)
        assertEquals(127.0276, ride.destinationLng!!, 0.0001)
    }

    @Test
    fun `방 생성 응답을 Ride 로 매핑하고 방장을 멤버로 채운다`() {
        val response = OpenPartyResponse(
            id = 12,
            hostId = 1,
            departureLat = 37.5665,
            departureLng = 126.9780,
            destinationLat = 37.4979,
            destinationLng = 127.0276,
            departure = "서울시청",
            destination = "강남역",
            capacity = 3,
            currentMembers = 1,
            departureRadius = 500,
            destinationRadius = 500,
            status = "ACTIVE",
            createdAt = "2026-08-24T09:00:00Z",
        )

        val ride = response.toRide()

        assertEquals("12", ride.id)
        assertEquals("1", ride.hostId)
        assertEquals(RideStatus.RECRUITING, ride.status)
        // 생성 응답에는 members 배열이 없어 hostId 로 1명을 구성한다.
        assertEquals(1, ride.members.size)
        assertEquals("방장", ride.members[0].nickname)
    }

    @Test
    fun `NewParty 를 생성 요청 DTO 로 변환한다`() {
        val dto = NewParty(
            hostId = 1,
            departureLat = 37.5665,
            departureLng = 126.9780,
            destinationLat = 37.4979,
            destinationLng = 127.0276,
            departure = "서울시청",
            destination = "강남역",
            capacity = 3,
        ).toRequestDto()

        assertEquals(1L, dto.hostId)
        assertEquals("서울시청", dto.departure)
        assertEquals(3, dto.capacity)
        // 서버 검증 범위(100~500)를 만족하는 기본 반경이 채워져야 한다.
        assertEquals(500, dto.departureRadius)
        assertEquals(500, dto.destinationRadius)
    }

    // 서버가 record 의 boolean isHost 를 "host" 로 직렬화하는 Jackson 버전이 있어 두 이름을 모두 받는다.
    @Test
    fun `상세 응답의 isHost 와 host 필드명을 모두 인식한다`() {
        val withIsHost = json.decodeFromString<PartyDetailResponse.MemberInfo>(
            """{"memberId":1,"isHost":true,"joinedAt":"2026-08-24T09:00:00Z"}""",
        )
        val withHost = json.decodeFromString<PartyDetailResponse.MemberInfo>(
            """{"memberId":1,"host":true,"joinedAt":"2026-08-24T09:00:00Z"}""",
        )

        assertTrue(withIsHost.isHost)
        assertTrue(withHost.isHost)
    }

    @Test
    fun `서버가 좌표나 hostId 를 빠뜨려도 상세 매핑이 깨지지 않는다`() {
        val decoded = json.decodeFromString<PartyDetailResponse>(
            """{"id":9,"departure":"성결대 정문","destination":"안양역","capacity":3,"status":"ACTIVE"}""",
        )

        val ride = decoded.toRide()

        assertEquals("9", ride.id)
        assertNull(ride.hostId)
        assertNull(ride.originLat)
        assertTrue(ride.members.isEmpty())
        assertEquals(RideStatus.RECRUITING, ride.status)
    }

    @Test
    fun `목록 응답 JSON 을 실제 서버 shape 그대로 역직렬화한다`() {
        val body = """
            {"list":[{"partyId":7,"departure":"서울시청","destination":"강남역","currentMembers":2,"capacity":3,"status":"ACTIVE"}]}
        """.trimIndent()

        val decoded = json.decodeFromString<PartyListResponse>(body)

        assertEquals(1, decoded.list.size)
        assertEquals("7", decoded.list[0].toRide().id)
    }
}
