package com.moyeota.data.repository

import com.moyeota.data.remote.MatchingApi
import com.moyeota.data.remote.dto.DriverSummaryResponse
import com.moyeota.data.remote.dto.OpenPartyRequestDto
import com.moyeota.data.remote.dto.OpenPartyResponse
import com.moyeota.data.remote.dto.PartyDetailResponse
import com.moyeota.data.remote.dto.PartyListResponse
import com.moyeota.data.remote.dto.PartyStatusResponse
import com.moyeota.data.remote.dto.RouteEstimateResponse
import com.moyeota.data.remote.dto.RouteRequestDto
import com.moyeota.domain.model.AuthState
import com.moyeota.domain.model.NewParty
import com.moyeota.domain.model.PartyJoinException
import com.moyeota.domain.model.RideStatus
import com.moyeota.domain.session.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class RemoteRideRepositoryTest {

    /** 로그인한 사용자를 흉내 내는 세션. uuid 가 null 이면 미로그인/복원 전이다. */
    private fun session(uuid: String? = UUID_ME): UserSession = object : UserSession {
        override val authState: StateFlow<AuthState> = MutableStateFlow(
            if (uuid == null) AuthState.Unauthenticated else AuthState.Authenticated(uuid),
        )
    }

    /**
     * 합류 응답이 방 상세 → `PartyStatusResponse` 로 바뀌었다(Backend #213). 동승자 목록은 오지 않으므로
     * 저장소도 **상태만** 돌려준다 — 몰래 상세를 한 번 더 읽어 Ride 를 만들어 주면, 합류 왕복이 2회로
     * 늘어난 걸 호출부가 모르게 된다(대기 화면은 SSE 스냅샷으로 상세를 받는다).
     */
    @Test
    fun `합류는 상태와 인원만 돌려주고 상세를 부르지 않는다`() = runBlocking {
        val api = FakeMatchingApi()

        val status = RemoteRideRepository(api, session()).joinParty(partyId = 7)

        // 합류자는 Bearer 토큰이 정한다 — partyId 외에 아무것도 넘기지 않는다.
        assertEquals(7L, api.joinCall)
        assertEquals(0, api.detailCallCount)
        assertEquals(RideStatus.MATCHED, status.status)
        assertEquals(2, status.currentMembers)
    }

    /**
     * 서버는 멤버 목록에 "누가 나인지"를 표시해 주지 않는다 — publicId 와 세션 uuid 비교가 유일한 근거다.
     * 저장소가 세션을 매퍼에 흘려보내지 않으면 화면의 「나 제외」 카운트와 나 배지가 통째로 어긋난다.
     */
    @Test
    fun `상세 응답의 멤버 중 세션 uuid 와 같은 사람을 나로 표시한다`() = runBlocking {
        val repository = RemoteRideRepository(FakeMatchingApi(), session())

        val detail = repository.getPartyDetail(partyId = 7)

        assertEquals(listOf(true, false), detail.members.map { it.isMe })
        assertEquals(listOf("성윤", "다른사람"), detail.members.map { it.nickname })
        assertEquals(4, detail.members[0].rideCount)
    }

    /**
     * 서버는 **멤버 추가를 먼저 커밋**하고 응답을 만든다. 그 응답을 앱이 놓치면 서버에는 들어가 있는데
     * 화면은 실패로 끝나고, 재시도는 409 로 막힌다 — 복구가 없으면 30분 스윕 전까지 그 계정이
     * 어떤 방에도 못 들어간다. 그래서 409 를 받으면 상세로 **내가 이미 멤버인지 확인해** 되돌린다.
     */
    @Test
    fun `이미 참여 중 409 는 상세로 멤버십을 확인해 복구 신호로 올린다`() = runBlocking {
        val api = FakeMatchingApi(joinFailure = conflict("ALREADY_JOINED_OTHER_PARTY", "이미 참여 중인 방이 있습니다."))

        val error = runCatching { RemoteRideRepository(api, session()).joinParty(partyId = 7) }
            .exceptionOrNull() as PartyJoinException

        // 확인은 추측이 아니라 상세 조회다 — 한 번은 반드시 읽는다.
        assertEquals(1, api.detailCallCount)
        assertTrue(error.isAlreadyInThisParty)
        assertFalse(error.isAlreadyInAnotherParty)
        // 복구에 필요한 건 "그 방의 지금 상태" — 합류 성공과 같은 모양으로 넘길 수 있어야 한다.
        assertEquals(RideStatus.MATCHED, error.joinedStatus?.status)
        assertEquals(2, error.joinedStatus?.currentMembers)
    }

    /** 상세에 내가 없으면 **다른 방**에 있는 것이다 — 그 방을 정리해야 들어갈 수 있다는 다른 안내가 필요하다. */
    @Test
    fun `상세에 내가 없으면 다른 방에 참여 중으로 올린다`() = runBlocking {
        val api = FakeMatchingApi(joinFailure = conflict("ALREADY_JOINED_OTHER_PARTY", "이미 참여 중인 방이 있습니다."))

        // 세션 uuid 가 멤버 목록에 없는 계정.
        val error = runCatching { RemoteRideRepository(api, session(uuid = UUID_STRANGER)).joinParty(partyId = 7) }
            .exceptionOrNull() as PartyJoinException

        assertFalse(error.isAlreadyInThisParty)
        assertTrue(error.isAlreadyInAnotherParty)
        assertNull(error.joinedStatus)
    }

    /** 「이미 참여 중」이 아닌 실패는 상세를 읽을 이유가 없다 — 쓸데없는 왕복을 늘리지 않는다. */
    @Test
    fun `정원 초과 409 는 상세를 읽지 않고 그대로 올린다`() = runBlocking {
        val api = FakeMatchingApi(joinFailure = conflict("PARTY_FULL", "정원이 가득 찬 방입니다."))

        val error = runCatching { RemoteRideRepository(api, session()).joinParty(partyId = 7) }
            .exceptionOrNull() as PartyJoinException

        assertEquals(0, api.detailCallCount)
        assertTrue(error.isPartyFull)
        assertFalse(error.isAlreadyInThisParty)
        assertEquals(409, error.httpStatus)
        // 서버 원문을 그대로 들고 온다 — 화면 문구는 화면이 정한다.
        assertEquals("정원이 가득 찬 방입니다.", error.serverMessage)
    }

    @Test
    fun `미로그인 상태에서는 아무도 나로 표시되지 않는다`() = runBlocking {
        val ride = RemoteRideRepository(FakeMatchingApi(), session(uuid = null))
            .getPartyDetail(partyId = 7)

        assertTrue(ride.members.none { it.isMe })
    }

    @Test
    fun `나가기도 partyId 만 넘긴다 — 나가는 주체는 토큰이 정한다`() = runBlocking {
        val api = FakeMatchingApi()

        RemoteRideRepository(api, session()).leaveParty(partyId = 7)

        assertEquals(7L, api.leaveCall)
    }

    @Test
    fun `방 생성 요청 본문에는 좌표 라벨 정원만 실린다 — 생성자는 토큰이 정한다`() = runBlocking {
        val api = FakeMatchingApi()

        val ride = RemoteRideRepository(api, session()).createParty(
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

        val rides = RemoteRideRepository(api, session()).getPartiesWithin(
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
        val driver = RemoteRideRepository(FakeMatchingApi(), session()).getAssignedDriver(partyId = 7)

        assertEquals(4, driver.seats)
        assertEquals("12가 3456", driver.plateNumber)
        assertEquals("쏘나타", driver.vehicleType)
    }

    @Test
    fun `경로 미리보기는 좌표 4개를 본문으로 보내고 path 를 encodedPath 로 돌려준다`() = runBlocking {
        val api = FakeMatchingApi()

        val estimate = RemoteRideRepository(api, session())
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

    /**
     * 25·26 의 주기 조회가 **상세로 옮겨왔다** — 가벼운 상태 조회(`/status`)가 백엔드에서 삭제됐기
     * 때문이다(`04f6be7`). 그 호출을 되살리면 404 라, 저장소에 남아 있으면 안 된다.
     *
     * 같은 방을 두 번 읽으면 `==` 로 같아야 한다 — 그게 두 화면이 「안 바뀌었다」를 판정하는 유일한
     * 근거이고(지문이 하던 일), 어긋나면 매 주기 화면 상태가 갈려 recomposition 이 멈추지 않는다.
     */
    @Test
    fun `주기 조회는 상세를 읽고 같은 방을 두 번 읽으면 서로 같다`() = runBlocking {
        val api = FakeMatchingApi()
        val repository = RemoteRideRepository(api, session())

        val first = repository.getPartyDetail(partyId = 7)
        val second = repository.getPartyDetail(partyId = 7)

        assertEquals(2, api.detailCallCount)
        assertEquals(first, second)
        assertEquals(RideStatus.MATCHED, first.status)
        assertEquals(2, first.members.size)
    }

    /** 더미 저장소도 같은 방을 두 번 읽으면 같아야 한다 — 디버그 빌드에서 폴링이 헛돌지 않게. */
    @Test
    fun `더미 저장소의 상세도 두 번 읽으면 서로 같다`() = runBlocking {
        val repository = DummyRideRepository()

        assertEquals(repository.getPartyDetail(partyId = 1), repository.getPartyDetail(partyId = 1))
    }

    /** 서버 공통 에러 본문 그대로 — 저장소가 code 를 읽어 분기한다. */
    private fun conflict(code: String, message: String) = HttpException(
        Response.error<Unit>(
            409,
            """{"code":"$code","message":"$message"}""".toResponseBody("application/json".toMediaType()),
        ),
    )

    private class FakeMatchingApi(private val joinFailure: Throwable? = null) : MatchingApi {
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

        // 합류 응답은 상태·인원 두 값뿐이다(Backend #213 + `04f6be7` 로 지문 제거).
        // 상세와 **같은 방**을 가리키도록 같은 상태·인원을 쓴다.
        override suspend fun joinParty(partyId: Long): PartyStatusResponse {
            joinCall = partyId
            joinFailure?.let { throw it }
            return PartyStatusResponse(status = "COMPLETED", currentMembers = 2)
        }

        override suspend fun finishParty(partyId: Long) = error("쓰이지 않는다")

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
                PartyDetailResponse.MemberInfo(
                    publicId = UUID_ME,
                    nickname = "성윤",
                    rideCount = 4,
                ),
                PartyDetailResponse.MemberInfo(
                    publicId = UUID_OTHER,
                    nickname = "다른사람",
                ),
            ),
            estimateFare = 9600,
            estimateTime = 14,
            route = "_p~iF~ps|U",
        )
    }

    private companion object {
        /** 세션 uuid 와 첫 멤버의 publicId 가 같은 값이어야 "나" 판정이 성립한다. */
        const val UUID_ME = "01a06145-3caf-7614-a3bd-cee6e25316b1"
        const val UUID_OTHER = "01a06145-3caf-7614-a3bd-cee6e2531999"

        /** 이 방의 멤버 목록에 **없는** 계정 — "다른 방에 참여 중" 판정을 가르는 데 쓴다. */
        const val UUID_STRANGER = "01a06145-3caf-7614-a3bd-cee6e2531aaa"
    }
}
