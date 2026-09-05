package com.moyeota.data.remote

import com.moyeota.data.remote.dto.DriverSummaryResponse
import com.moyeota.data.remote.dto.OpenPartyRequestDto
import com.moyeota.data.remote.dto.OpenPartyResponse
import com.moyeota.data.remote.dto.PartyDetailResponse
import com.moyeota.data.remote.dto.PartyListResponse
import com.moyeota.data.remote.dto.RouteEstimateResponse
import com.moyeota.data.remote.dto.RouteRequestDto
import com.moyeota.domain.model.NewParty
import com.moyeota.domain.model.RideStatus
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals(RideStatus.DISPATCHING, partyStatusToRideStatus("DRIVER_ASSIGNED"))
        assertEquals(RideStatus.ONGOING, partyStatusToRideStatus("IN_RIDE"))
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
        // 목록 응답에는 요금/경로가 없고, 좌표는 없으면 null 로 남는다.
        assertEquals(0, ride.farePerPerson)
        assertEquals(0, ride.totalFare)
        assertNull(ride.routePolyline)
        assertNull(ride.originLat)
    }

    @Test
    fun `지도 범위 목록 아이템의 출발 좌표를 Ride 로 옮긴다`() {
        val ride = PartyListResponse.PartyItem(
            partyId = 1,
            departure = "부산대 정문",
            destination = "서면역",
            currentMembers = 1,
            capacity = 3,
            status = "ACTIVE",
            departureLat = 35.2313,
            departureLng = 129.0838,
        ).toRide()

        assertEquals(35.2313, ride.originLat!!, 0.00001)
        assertEquals(129.0838, ride.originLng!!, 0.00001)
        // 도착 좌표·요금·경로는 목록에 없다 — 상세에서 채운다.
        assertNull(ride.destinationLat)
        assertNull(ride.destinationLng)
        assertNull(ride.routePolyline)
    }

    @Test
    fun `지도 범위 목록 응답 JSON 을 실제 서버 shape 그대로 역직렬화한다`() {
        // 실측: GET /api/v1/matching/rooms?swLat=35.20&swLng=129.05&neLat=35.26&neLng=129.12
        val body = """
            {"list":[{"partyId":1,"departure":"부산대 정문","destination":"서면역","currentMembers":1,
             "capacity":3,"status":"ACTIVE","departureLat":35.2313,"departureLng":129.0838}]}
        """.trimIndent()

        val rides = json.decodeFromString<PartyListResponse>(body).list.map { it.toRide() }

        assertEquals(1, rides.size)
        assertEquals("1", rides[0].id)
        assertEquals("부산대 정문", rides[0].origin)
        assertEquals(RideStatus.RECRUITING, rides[0].status)
        assertEquals(35.2313, rides[0].originLat!!, 0.00001)
        assertEquals(129.0838, rides[0].originLng!!, 0.00001)
    }

    @Test
    fun `목록 아이템에 좌표가 빠져도 매핑이 깨지지 않는다`() {
        // 브랜치에 따라 전체 목록 응답에는 좌표가 없을 수 있다.
        val body = """
            {"list":[{"partyId":7,"departure":"서울시청","destination":"강남역","currentMembers":2,"capacity":3,"status":"ACTIVE"}]}
        """.trimIndent()

        val ride = json.decodeFromString<PartyListResponse>(body).list.single().toRide()

        assertNull(ride.originLat)
        assertNull(ride.originLng)
        assertEquals("7", ride.id)
    }

    @Test
    fun `상세 응답을 Ride 로 매핑하고 요금 경로 기사 id 를 채운다`() {
        val detail = detailResponse()

        val ride = detail.toRide()

        assertEquals("7", ride.id)
        assertEquals("서울시청", ride.origin)
        assertEquals("강남역", ride.destination)
        assertEquals(RideStatus.DISPATCHING, ride.status)
        assertEquals(2, ride.members.size)
        assertEquals(37.5665, ride.originLat!!, 0.0001)
        assertEquals(127.0276, ride.destinationLng!!, 0.0001)
        assertEquals(9600, ride.estimatedFare)
        assertEquals(14, ride.estimatedMinutes)
        assertEquals("_p~iF~ps|U", ride.routePolyline)
        assertEquals(42L, ride.driverId)
        // 전체 요금은 정원으로 나눠 1인당 요금을 만든다(9600 / 3).
        assertEquals(9600, ride.totalFare)
        assertEquals(3200, ride.farePerPerson)
    }

    /**
     * 백엔드가 자동 기사 매칭으로 바뀌며 방장 개념이 사라졌다. 예전엔 joinedAt 이 가장 이른 멤버를
     * 방장으로 추정해 "방장" 닉네임을 붙였는데, 그 추정이 되살아나지 않는지 못박는다.
     */
    @Test
    fun `멤버는 전부 동등하게 매핑된다 — 방장 추정이 없다`() {
        val detail = detailResponse(
            members = listOf(
                PartyDetailResponse.MemberInfo(memberId = 3, joinedAt = "2026-08-17T09:01:00Z"),
                PartyDetailResponse.MemberInfo(memberId = 1, joinedAt = "2026-08-17T09:00:00Z"),
            ),
        )

        val ride = detail.toRide()

        // 서버가 준 순서 그대로, joinedAt 으로 재정렬하거나 특별 취급하지 않는다.
        assertEquals(listOf("3", "1"), ride.members.map { it.id })
        assertEquals(listOf("멤버 3", "멤버 1"), ride.members.map { it.nickname })
    }

    @Test
    fun `정원이 0 이면 1인당 요금 계산이 터지지 않는다`() {
        assertEquals(0, farePerPerson(estimateFare = 9600, capacity = 0))
        assertEquals(0, farePerPerson(estimateFare = null, capacity = 3))
    }

    @Test
    fun `방 생성 응답은 멤버 목록 없이 인원 수만 주므로 자리 표시용 멤버를 만든다`() {
        val response = OpenPartyResponse(
            id = 12,
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
            estimateFare = 9600,
            estimateTime = 14,
            route = "_p~iF~ps|U",
            taxiDriverId = null,
        )

        val ride = response.toRide()

        assertEquals("12", ride.id)
        assertEquals(RideStatus.RECRUITING, ride.status)
        assertEquals(1, ride.members.size)
        assertEquals("멤버 1", ride.members[0].nickname)
        assertEquals("_p~iF~ps|U", ride.routePolyline)
        assertNull(ride.driverId)
    }

    @Test
    fun `생성 응답 인원 수가 0 이면 멤버가 빈다`() {
        val ride = OpenPartyResponse(id = 12, capacity = 3, status = "ACTIVE").toRide()

        assertTrue(ride.members.isEmpty())
    }

    /**
     * 방 생성자는 서버가 토큰에서 정한다. 사용자 id 가 **본문에 실려서는 안 된다** — 실려도 서버는
     * 조용히 무시하므로(테스트가 없으면) 되살아난 걸 아무도 눈치채지 못하고,
     * "앱이 보낸 생성자"가 유효하다는 착각만 남는다.
     */
    @Test
    fun `생성 요청 본문에 사용자 id 를 싣지 않는다`() {
        val dto: OpenPartyRequestDto = NewParty(
            departureLat = 37.5665,
            departureLng = 126.9780,
            destinationLat = 37.4979,
            destinationLng = 127.0276,
            departure = "서울시청",
            destination = "강남역",
            capacity = 3,
        ).toRequestDto()

        assertEquals("서울시청", dto.departure)
        assertEquals(3, dto.capacity)
        // 서버 검증 범위(100~500)를 만족하는 기본 반경이 채워져야 한다.
        assertEquals(500, dto.departureRadius)
        assertEquals(500, dto.destinationRadius)

        val encoded = json.encodeToString(dto)
        assertFalse("생성자 id 가 본문에 되살아났다: $encoded", encoded.contains("creatorId"))
        assertFalse("생성자 id 가 본문에 되살아났다: $encoded", encoded.contains("hostId"))
    }

    @Test
    fun `서버가 좌표나 요금 경로를 빠뜨려도 상세 매핑이 깨지지 않는다`() {
        val decoded = json.decodeFromString<PartyDetailResponse>(
            """{"id":9,"departure":"성결대 정문","destination":"안양역","capacity":3,"status":"ACTIVE"}""",
        )

        val ride = decoded.toRide()

        assertEquals("9", ride.id)
        assertNull(ride.originLat)
        assertNull(ride.estimatedFare)
        assertNull(ride.routePolyline)
        assertNull(ride.driverId)
        assertTrue(ride.members.isEmpty())
        assertEquals(RideStatus.RECRUITING, ride.status)
    }

    @Test
    fun `상세 응답 JSON 을 실제 서버 shape 그대로 역직렬화한다`() {
        // PartyDetailResult(… members, estimateFare, estimateTime, route, taxiDriverId) 기준.
        val body = """
            {"id":7,"departureLat":37.5665,"departureLng":126.978,"destinationLat":37.4979,
             "destinationLng":127.0276,"departure":"서울시청","destination":"강남역","capacity":3,
             "currentMembers":2,"departureRadius":500,"destinationRadius":500,"status":"DRIVER_ASSIGNED",
             "createdAt":"2026-08-30T09:00:00Z",
             "members":[{"memberId":1,"joinedAt":"2026-08-30T09:00:00Z"},
                        {"memberId":3,"joinedAt":"2026-08-30T09:01:00Z"}],
             "estimateFare":9600,"estimateTime":14,"route":"_p~iF~ps|U","taxiDriverId":42}
        """.trimIndent()

        val ride = json.decodeFromString<PartyDetailResponse>(body).toRide()

        assertEquals("7", ride.id)
        assertEquals(RideStatus.DISPATCHING, ride.status)
        assertEquals(listOf("1", "3"), ride.members.map { it.id })
        assertEquals(42L, ride.driverId)
        assertEquals(9600, ride.estimatedFare)
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

    @Test
    fun `기사 요약 응답을 도메인 모델로 매핑한다`() {
        // 서버 DriverSummary(seats, plateNumber, type) — 이름/별점 필드가 없다.
        val decoded = json.decodeFromString<DriverSummaryResponse>(
            """{"seats":4,"plateNumber":"12가 3456","type":"쏘나타"}""",
        )

        val driver = decoded.toAssignedDriver()

        assertEquals(4, driver.seats)
        assertEquals("12가 3456", driver.plateNumber)
        assertEquals("쏘나타", driver.vehicleType)
    }

    @Test
    fun `기사 요약에 좌석 수가 없어도 매핑이 깨지지 않는다`() {
        val driver = json.decodeFromString<DriverSummaryResponse>("""{"plateNumber":"99하 1111"}""")
            .toAssignedDriver()

        assertNull(driver.seats)
        assertEquals("", driver.vehicleType)
    }

    @Test
    fun `경로 미리보기 요청 본문 필드명이 백엔드 RouteRequest 와 일치한다`() {
        // 백엔드 record RouteRequest(departureLat, departureLng, destinationLat, destinationLng).
        // 이름이 하나라도 어긋나면 Spring 이 0.0 으로 바인딩해 엉뚱한 경로가 나온다.
        val body = json.encodeToString(
            RouteRequestDto.serializer(),
            routeRequestDto(35.2313, 129.0838, 35.1580, 129.0594),
        )

        assertEquals(
            """{"departureLat":35.2313,"departureLng":129.0838,""" +
                """"destinationLat":35.158,"destinationLng":129.0594}""",
            body,
        )
    }

    @Test
    fun `경로 응답의 path 를 encodedPath 로 옮긴다`() {
        // 서버 RouteEstimate(estimateFare, estimateTime, path) — path 는 인코딩된 폴리라인.
        val decoded = json.decodeFromString<RouteEstimateResponse>(
            """{"estimateFare":9600,"estimateTime":14,"path":"_p~iF~ps|U_ulLnnqC"}""",
        )

        val estimate = decoded.toRouteEstimate()

        assertEquals(9600, estimate.estimatedFare)
        assertEquals(14, estimate.estimatedMinutes)
        assertEquals("_p~iF~ps|U_ulLnnqC", estimate.encodedPath)
    }

    @Test
    fun `경로 응답이 비어도 기본값으로 방어한다`() {
        val estimate = RouteEstimateResponse().toRouteEstimate()

        assertEquals(0, estimate.estimatedFare)
        assertEquals(0, estimate.estimatedMinutes)
        assertEquals("", estimate.encodedPath)
    }

    private fun detailResponse(
        members: List<PartyDetailResponse.MemberInfo> = listOf(
            PartyDetailResponse.MemberInfo(memberId = 1, joinedAt = "2026-08-17T09:00:00Z"),
            PartyDetailResponse.MemberInfo(memberId = 3, joinedAt = "2026-08-17T09:01:00Z"),
        ),
    ) = PartyDetailResponse(
        id = 7,
        departureLat = 37.5665,
        departureLng = 126.9780,
        destinationLat = 37.4979,
        destinationLng = 127.0276,
        departure = "서울시청",
        destination = "강남역",
        capacity = 3,
        currentMembers = members.size,
        departureRadius = 500,
        destinationRadius = 500,
        status = "MATCHING",
        createdAt = "2026-08-17T09:00:00Z",
        members = members,
        estimateFare = 9600,
        estimateTime = 14,
        route = "_p~iF~ps|U",
        taxiDriverId = 42,
    )
}
