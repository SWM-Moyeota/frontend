package com.moyeota.data.repository

import com.moyeota.data.remote.MatchingApi
import com.moyeota.data.remote.dto.DriverSummaryResponse
import com.moyeota.data.remote.dto.OpenPartyRequestDto
import com.moyeota.data.remote.dto.OpenPartyResponse
import com.moyeota.data.remote.dto.PartyDetailResponse
import com.moyeota.data.remote.dto.PartyListResponse
import com.moyeota.data.remote.dto.RouteEstimateResponse
import com.moyeota.data.remote.dto.RouteRequestDto
import com.moyeota.domain.model.NewParty
import com.moyeota.domain.model.RideStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteRideRepositoryTest {

    @Test
    fun `합류는 서버가 돌려준 방 상세를 재조회 없이 그대로 반환한다`() = runBlocking {
        val api = FakeMatchingApi()

        val ride = RemoteRideRepository(api).joinParty(partyId = 7)

        // 합류자는 Bearer 토큰이 정한다 — partyId 외에 아무것도 넘기지 않는다.
        assertEquals(7L, api.joinCall)
        // 합류 응답이 곧 상세라 getPartyDetail 을 한 번도 부르지 않아야 한다.
        assertEquals(0, api.detailCallCount)
        assertEquals("7", ride.id)
        assertEquals(RideStatus.MATCHED, ride.status)
        assertEquals(2, ride.members.size)
    }

    @Test
    fun `나가기도 partyId 만 넘긴다 — 나가는 주체는 토큰이 정한다`() = runBlocking {
        val api = FakeMatchingApi()

        RemoteRideRepository(api).leaveParty(partyId = 7)

        assertEquals(7L, api.leaveCall)
    }

    @Test
    fun `방 생성 요청 본문에는 좌표 라벨 정원만 실린다 — 생성자는 토큰이 정한다`() = runBlocking {
        val api = FakeMatchingApi()

        val ride = RemoteRideRepository(api).createParty(
            NewParty(
                departureLat = 37.5665,
                departureLng = 126.9780,
                destinationLat = 37.4979,
                destinationLng = 127.0276,
                departure = "서울시청",
                destination = "강남역",
                capacity = 3,
            ),
        )

        // 서버는 생성자를 토큰에서 정한다 — 본문에는 좌표·라벨·정원만 실린다.
        assertEquals("서울시청", api.openRequest?.departure)
        assertEquals("12", ride.id)
        // 생성 응답에 members 목록이 없어 currentMembers 만큼 자리 표시용 멤버를 만든다.
        assertEquals(1, ride.members.size)
    }

    @Test
    fun `지도 범위 조회는 남서 북동 순서 그대로 API 에 넘기고 출발 좌표를 채워 돌려준다`() = runBlocking {
        val api = FakeMatchingApi()

        val rides = RemoteRideRepository(api).getPartiesWithin(
            swLat = 35.20,
            swLng = 129.05,
            neLat = 35.26,
            neLng = 129.12,
        )

        // 순서가 뒤바뀌면 서버가 빈 목록을 돌려준다 — 인자 순서를 고정한다.
        assertEquals(listOf(35.20, 129.05, 35.26, 129.12), api.withinCall)
        assertEquals(1, rides.size)
        assertEquals("1", rides[0].id)
        assertEquals(35.2313, rides[0].originLat!!, 0.00001)
        assertEquals(129.0838, rides[0].originLng!!, 0.00001)
        assertEquals(RideStatus.RECRUITING, rides[0].status)
    }

    @Test
    fun `더미 저장소는 범위 밖 방을 걸러낸다`() = runBlocking {
        val repository = DummyRideRepository()

        // 성결대 주변(37.37~37.39, 126.91~126.94)만 포함하는 범위.
        assertEquals(2, repository.getPartiesWithin(37.37, 126.91, 37.39, 126.94).size)
        // 부산 범위에는 아무것도 없다.
        assertEquals(0, repository.getPartiesWithin(35.20, 129.05, 35.26, 129.12).size)
    }

    @Test
    fun `배정된 기사 요약을 도메인 모델로 돌려준다`() = runBlocking {
        val driver = RemoteRideRepository(FakeMatchingApi()).getAssignedDriver(partyId = 7)

        assertEquals(4, driver.seats)
        assertEquals("12가 3456", driver.plateNumber)
        assertEquals("쏘나타", driver.vehicleType)
    }

    @Test
    fun `경로 미리보기는 좌표 4개를 본문으로 보내고 path 를 encodedPath 로 돌려준다`() = runBlocking {
        val api = FakeMatchingApi()

        val estimate = RemoteRideRepository(api)
            .previewRoute(35.2313, 129.0838, 35.1580, 129.0594)

        // 순서가 뒤바뀌면 엉뚱한 경로가 나온다 — 출발/도착 좌표 배치를 고정한다.
        assertEquals(
            RouteRequestDto(
                departureLat = 35.2313,
                departureLng = 129.0838,
                destinationLat = 35.1580,
                destinationLng = 129.0594,
            ),
            api.routeRequest,
        )
        assertEquals(12_800, estimate.estimatedFare)
        assertEquals(35, estimate.estimatedMinutes)
        // 서버 응답 필드명은 route 가 아니라 path 다.
        assertEquals("ub`vEwtzrW?BQxA", estimate.encodedPath)
    }

    @Test
    fun `더미 경로 미리보기는 거리에 비례한 요금과 두 점짜리 폴리라인을 돌려준다`() = runBlocking {
        // 부산대 정문 → 서면역: 직선 약 8.4km.
        val estimate = DummyRideRepository().previewRoute(35.2313, 129.0838, 35.1580, 129.0594)

        assertTrue("요금이 기본요금보다 커야 한다", estimate.estimatedFare > 4_800)
        assertTrue("소요시간이 승하차 시간보다 커야 한다", estimate.estimatedMinutes > 3)
        // 정확히 두 점을 인코딩한 폴리라인(Google precision 5).
        assertEquals("sb`vEwtzrWbiMnwC", estimate.encodedPath)
    }

    private class FakeMatchingApi : MatchingApi {
        var joinCall: Long? = null
        var leaveCall: Long? = null
        var openRequest: OpenPartyRequestDto? = null
        var detailCallCount = 0
        var withinCall: List<Double>? = null
        var routeRequest: RouteRequestDto? = null

        override suspend fun getParties(): PartyListResponse = PartyListResponse()

        override suspend fun getPartiesWithin(
            swLat: Double,
            swLng: Double,
            neLat: Double,
            neLng: Double,
        ): PartyListResponse {
            withinCall = listOf(swLat, swLng, neLat, neLng)
            return PartyListResponse(
                list = listOf(
                    PartyListResponse.PartyItem(
                        partyId = 1,
                        departure = "부산대 정문",
                        destination = "서면역",
                        currentMembers = 1,
                        capacity = 3,
                        status = "ACTIVE",
                        departureLat = 35.2313,
                        departureLng = 129.0838,
                    ),
                ),
            )
        }

        override suspend fun getPartyDetail(partyId: Long): PartyDetailResponse {
            detailCallCount++
            return detail(partyId)
        }

        override suspend fun openParty(request: OpenPartyRequestDto): OpenPartyResponse {
            openRequest = request
            return OpenPartyResponse(
                id = 12,
                departure = request.departure,
                destination = request.destination,
                capacity = request.capacity,
                currentMembers = 1,
                status = "ACTIVE",
                estimateFare = 9600,
                estimateTime = 14,
                route = "_p~iF~ps|U",
            )
        }

        override suspend fun joinParty(partyId: Long): PartyDetailResponse {
            joinCall = partyId
            return detail(partyId)
        }

        override suspend fun leaveParty(partyId: Long) {
            leaveCall = partyId
        }

        override suspend fun getAssignedDriver(partyId: Long) =
            DriverSummaryResponse(seats = 4, plateNumber = "12가 3456", type = "쏘나타")

        // 실서버(localhost:8080) 응답을 축약한 값 — 폴리라인 필드명이 path 다.
        override suspend fun previewRoute(request: RouteRequestDto): RouteEstimateResponse {
            routeRequest = request
            return RouteEstimateResponse(
                estimateFare = 12_800,
                estimateTime = 35,
                path = "ub`vEwtzrW?BQxA",
            )
        }

        // 정원이 차서 서버가 COMPLETED 로 마감한 방 — 앱이 매칭 시작을 트리거하지 않는다.
        private fun detail(partyId: Long) = PartyDetailResponse(
            id = partyId,
            departure = "서울시청",
            destination = "강남역",
            capacity = 3,
            currentMembers = 2,
            status = "COMPLETED",
            members = listOf(
                PartyDetailResponse.MemberInfo(memberId = 1, joinedAt = "2026-08-30T09:00:00Z"),
                PartyDetailResponse.MemberInfo(memberId = 3, joinedAt = "2026-08-30T09:01:00Z"),
            ),
            estimateFare = 9600,
            estimateTime = 14,
            route = "_p~iF~ps|U",
        )
    }
}
