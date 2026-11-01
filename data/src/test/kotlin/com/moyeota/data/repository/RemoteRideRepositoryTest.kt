package com.moyeota.data.repository

import com.moyeota.data.remote.MatchingApi
import com.moyeota.data.remote.dto.RealTimeMatchingRequestDto
import com.moyeota.data.remote.dto.MatchCandidateResponse
import com.moyeota.data.remote.dto.DriverSummaryResponse
import com.moyeota.data.remote.dto.OpenPartyRequestDto
import com.moyeota.data.remote.dto.OpenPartyResponse
import com.moyeota.data.remote.dto.PartyDetailResponse
import com.moyeota.data.remote.dto.PartyListResponse
import com.moyeota.data.remote.dto.PartyStatusResponse
import com.moyeota.data.remote.dto.RouteEstimateResponse
import com.moyeota.data.remote.dto.RouteRequestDto
import com.moyeota.domain.model.RealtimeMatchException
import com.moyeota.domain.model.AuthState
import com.moyeota.domain.model.NewParty
import com.moyeota.data.remote.matching.reconnectDelayMs
import com.moyeota.domain.model.PartyFinishException
import com.moyeota.domain.model.PartyJoinException
import com.moyeota.domain.model.PartyLeaveException
import com.moyeota.domain.model.PartyOpenException
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

    // ───────────── 「합승 완료」 실패 (403·409 가 각각 두 가지 뜻) ─────────────

    /**
     * 「합승 완료」는 **방장 전용**이 됐다(Backend #237). 비방장이 누르면 403 `NOT_PARTY_HOST` 다.
     * 앱은 아직 방장을 식별할 수 없어(서버 `hostId` 는 내부 PK, 멤버 목록은 publicId UUID — Backend #240)
     * 버튼 노출로 막지 못하므로, **이 실패가 사유를 들고 올라오는 것**이 유일한 안내 수단이다.
     */
    @Test
    fun `합승 완료 403 은 방장 아님을 코드로 구분해 올린다`() = runBlocking {
        val api = FakeMatchingApi(
            finishFailure = httpError(403, "NOT_PARTY_HOST", "방장만 수행할 수 있습니다."),
        )

        val error = runCatching { RemoteRideRepository(api, session()).finishParty(partyId = 7) }
            .exceptionOrNull() as PartyFinishException

        assertEquals(7L, api.finishCall)
        assertTrue(error.isNotHost)
        // 같은 403 인 「멤버 아님」과 섞이면 방 멤버가 아닌 사람에게 방장을 찾으라고 말하게 된다.
        assertFalse(error.isNotMember)
        assertEquals(403, error.httpStatus)
        // 서버 원문을 그대로 들고 온다 — 화면 문구는 화면이 정한다.
        assertEquals("방장만 수행할 수 있습니다.", error.serverMessage)
    }

    /** 같은 403 이지만 뜻이 다르다 — 멤버가 아니면 방장을 찾는 게 할 일이 아니다. */
    @Test
    fun `합승 완료 403 중 멤버 아님은 방장 아님과 갈린다`() = runBlocking {
        val api = FakeMatchingApi(
            finishFailure = httpError(403, "NOT_PARTY_MEMBER", "해당 방에 참여하고 있지 않습니다."),
        )

        val error = runCatching { RemoteRideRepository(api, session()).finishParty(partyId = 7) }
            .exceptionOrNull() as PartyFinishException

        assertTrue(error.isNotMember)
        assertFalse(error.isNotHost)
    }

    /** 409 도 두 가지다 — 정원 미달과 기사 배정은 사용자가 할 일이 정반대다(기다리기 / 기다릴 일 없음). */
    @Test
    fun `합승 완료 409 두 가지를 코드로 가른다`() = runBlocking {
        val notCompleted = runCatching {
            RemoteRideRepository(
                FakeMatchingApi(finishFailure = conflict("PARTY_NOT_COMPLETED", "정원이 다 차지 않은 방입니다.")),
                session(),
            ).finishParty(partyId = 7)
        }.exceptionOrNull() as PartyFinishException

        val driverAssigned = runCatching {
            RemoteRideRepository(
                FakeMatchingApi(finishFailure = conflict("DRIVER_ALREADY_ASSIGNED", "이미 기사가 배정된 방입니다.")),
                session(),
            ).finishParty(partyId = 7)
        }.exceptionOrNull() as PartyFinishException

        assertTrue(notCompleted.isNotCompleted)
        assertFalse(notCompleted.isDriverAssigned)
        assertTrue(driverAssigned.isDriverAssigned)
        assertFalse(driverAssigned.isNotCompleted)
    }

    /**
     * **서버가 코드를 늘려도 앱이 틀린 말을 하면 안 된다.** 모르는 코드·빈 본문은 code = null 로 올라와
     * 화면이 일반 문구로 떨어진다 — 403 이라고 「방장만」을, 409 라고 「정원 미달」을 **추측하지 않는다**.
     */
    @Test
    fun `모르는 코드와 빈 본문은 code 가 null 이고 사유를 추측하지 않는다`() = runBlocking {
        val unknownCode = runCatching {
            RemoteRideRepository(
                FakeMatchingApi(finishFailure = httpError(409, "PARTY_SOMETHING_NEW", "새 규칙입니다.")),
                session(),
            ).finishParty(partyId = 7)
        }.exceptionOrNull() as PartyFinishException

        val bodyless = runCatching {
            RemoteRideRepository(FakeMatchingApi(finishFailure = bodylessError(403)), session())
                .finishParty(partyId = 7)
        }.exceptionOrNull() as PartyFinishException

        // 모르는 코드는 그대로 들고 오지만 어느 사유도 아니다.
        assertEquals("PARTY_SOMETHING_NEW", unknownCode.code)
        assertFalse(unknownCode.isNotCompleted)
        assertFalse(unknownCode.isDriverAssigned)

        assertNull(bodyless.code)
        assertFalse(bodyless.isNotHost)
        assertFalse(bodyless.isNotMember)
        assertEquals(403, bodyless.httpStatus)
    }

    /** 성공은 204 로 몸통이 없다 — partyId 만 보내고 아무것도 돌려주지 않는다(주체는 Bearer 토큰). */
    @Test
    fun `합승 완료 성공은 partyId 만 보낸다`() = runBlocking {
        val api = FakeMatchingApi()

        RemoteRideRepository(api, session()).finishParty(partyId = 7)

        assertEquals(7L, api.finishCall)
        assertEquals(0, api.detailCallCount)
    }

    // ───────────── 방 생성 실패 (409 는 영구 교착, 400 은 사용자가 고쳐야 한다) ─────────────

    /**
     * **생성 409 가 이 묶음의 이유다.** 서버 `PartyService.open` 첫 줄이 `validateNotInOngoingParty` 이고,
     * 거기서 보는 「진행 중」은 `!(CANCELED || FINISHED)` 다. 그런데 30분 스윕(`CompletedPartySweeper`)은
     * **COMPLETED 만** 쓸어내므로 **아무도 합류하지 않은 ACTIVE 방은 영구히 남아** 그 계정의 생성·합류를
     * 계속 막는다. 사유가 code 로 올라오지 않으면 화면은 「잠시 후 다시 시도해 주세요」만 말할 수 있고,
     * 그건 영원히 틀린 안내다(기다려서 풀리지 않는다).
     */
    @Test
    fun `생성 409 는 이미 참여 중인 방으로 좁혀진다`() = runBlocking {
        val api = FakeMatchingApi(
            openFailure = conflict("ALREADY_JOINED_OTHER_PARTY", "이미 참여 중인 방이 있습니다."),
        )

        val error = runCatching { RemoteRideRepository(api, session()).createParty(newParty()) }
            .exceptionOrNull() as PartyOpenException

        assertTrue(error.isAlreadyInAnotherParty)
        // 합류와 달리 **상세를 읽지 않는다** — 만들려던 방은 아직 없으니 확인할 멤버십이 없다.
        assertEquals(0, api.detailCallCount)
        assertEquals(409, error.httpStatus)
        assertEquals("이미 참여 중인 방이 있습니다.", error.serverMessage)
    }

    /**
     * 400 이 네 가지다 — **상태 코드로는 가를 수 없다.** 섞이면 「핀을 옮겨 주세요」를 서비스 지역 밖
     * 좌표에게, 「인원을 다시」를 좌표 문제에게 말하게 된다.
     */
    @Test
    fun `생성 400 은 각 code 로 갈린다`() = runBlocking {
        val same = openError(httpError(400, "SAME_DEPARTURE_DESTINATION", "출발지와 도착지가 같습니다."))
        val area = openError(httpError(400, "OUT_OF_SERVICE_AREA", "서비스 지역을 벗어난 좌표입니다."))
        val capacity = openError(httpError(400, "INVALID_CAPACITY", "방 정원은 1~3명이어야 합니다."))
        val estimate = openError(httpError(400, "INVALID_ROUTE_ESTIMATE", "경로 정보가 올바르지 않습니다."))

        assertTrue(same.isSameDepartureDestination)
        assertFalse(same.isOutOfServiceArea)
        assertTrue(area.isOutOfServiceArea)
        assertFalse(area.isSameDepartureDestination)
        assertTrue(capacity.isInvalidCapacity)
        assertFalse(capacity.isRouteUnavailable)
        // 경로 관련 400 은 502 와 **같은 갈래**다 — 사용자가 할 수 있는 일이 같다(위 KDoc).
        assertTrue(estimate.isRouteUnavailable)
        assertFalse(estimate.isInvalidCapacity)
    }

    /** 502 는 경로 엔진 장애다. 본문이 없어도 상태 코드로 같은 갈래로 떨어뜨린다(게이트웨이 응답 대비). */
    @Test
    fun `생성 502 는 본문이 없어도 경로 실패로 다룬다`() = runBlocking {
        assertTrue(openError(httpError(502, "ROUTE_SEARCH_FAILED", "경로 조회에 실패했습니다.")).isRouteUnavailable)
        assertTrue(openError(bodylessError(502)).isRouteUnavailable)
    }

    /**
     * **에러 본문이 JSON 이 아닐 수 있다** — 게이트웨이·프록시가 가로챈 503 은 HTML 을 돌려준다.
     * 그때 `readApiError()` 는 null 이고, 사유를 **추측하지 않고** 일반 문구로 떨어져야 한다.
     */
    @Test
    fun `생성 실패 본문이 HTML 이면 code 없는 예외가 된다`() = runBlocking {
        val error = openError(htmlError(503))

        assertNull(error.code)
        assertNull(error.serverMessage)
        assertEquals(503, error.httpStatus)
        assertFalse(error.isAlreadyInAnotherParty)
        assertFalse(error.isSameDepartureDestination)
        assertFalse(error.isOutOfServiceArea)
        assertFalse(error.isInvalidCapacity)
        // 503 을 502 처럼 「경로 실패」로 지어내지 않는다.
        assertFalse(error.isRouteUnavailable)
    }

    /** 응답을 아예 못 받은 실패(네트워크)도 code 없는 [PartyOpenException] 이다 — 화면은 일반 문구다. */
    @Test
    fun `생성 네트워크 실패도 같은 예외로 좁힌다`() = runBlocking {
        val error = openError(java.io.IOException("connection reset"))

        assertNull(error.code)
        assertNull(error.httpStatus)
        assertFalse(error.isAlreadyInAnotherParty)
    }

    // ───────────── 나가기 실패 (403 은 "이미 됨") ─────────────

    /**
     * **403 `NOT_PARTY_MEMBER` 는 성공이다** — 나가기의 목표가 정확히 「이 방에 내가 없다」이기 때문이다.
     *
     * 되돌리지 않으면 204 응답이 유실된 사용자가(터널·타임아웃) 「나가지 못했어요」를 보고 다시 눌러
     * **같은 문구의 403** 을 맞는다. 로컬 기억과 홈 배너는 그 방을 계속 들고 있으니 화면상 나는 아직
     * 그 방에 있고, 벗어날 길이 없다. 합류가 409 를 성공으로 되돌리는 것과 대칭인 처방이다.
     */
    @Test
    fun `나가기 403 은 성공으로 다룬다`() = runBlocking {
        val api = FakeMatchingApi(
            leaveFailure = httpError(403, "NOT_PARTY_MEMBER", "해당 방에 참여하고 있지 않습니다."),
        )

        // 예외가 나오면 이 줄에서 실패한다 — "성공으로 다룬다"가 곧 "던지지 않는다"다.
        RemoteRideRepository(api, session()).leaveParty(partyId = 7)

        assertEquals(7L, api.leaveCall)
        // 합류의 복구와 달리 **상세로 확인하지 않는다** — 이 403 자체가 "이 방에 내가 없다"다.
        assertEquals(0, api.detailCallCount)
    }

    /** 409 는 **다른 뜻**이다 — 매칭이 시작돼 나갈 수 없다. 재시도 안내로 뭉개면 영원히 같은 409 다. */
    @Test
    fun `나가기 409 는 모집 중이 아님으로 좁혀진다`() = runBlocking {
        val api = FakeMatchingApi(leaveFailure = conflict("PARTY_NOT_RECRUITING", "모집 중인 방이 아닙니다."))

        val error = runCatching { RemoteRideRepository(api, session()).leaveParty(partyId = 7) }
            .exceptionOrNull() as PartyLeaveException

        assertTrue(error.isNotRecruiting)
        assertFalse(error.isPartyNotFound)
        assertEquals(409, error.httpStatus)
    }

    /** 404 는 방이 사라진 것 — 나갈 방이 없으므로 홈으로 보내면 된다. 코드 없는 404 도 같게 다룬다. */
    @Test
    fun `나가기 404 는 방 없음으로 좁혀진다`() = runBlocking {
        assertTrue(leaveError(httpError(404, "PARTY_NOT_FOUND", "존재하지 않는 방입니다.")).isPartyNotFound)
        assertTrue(leaveError(bodylessError(404)).isPartyNotFound)
    }

    /** 모르는 코드는 그대로 들고 오지만 어느 사유도 아니다 — 서버가 코드를 늘려도 틀린 말을 하지 않는다. */
    @Test
    fun `나가기 모르는 코드는 사유를 추측하지 않는다`() = runBlocking {
        val error = leaveError(httpError(409, "PARTY_SOMETHING_NEW", "새 규칙입니다."))

        assertEquals("PARTY_SOMETHING_NEW", error.code)
        assertFalse(error.isNotRecruiting)
        assertFalse(error.isPartyNotFound)
    }

    // ───────────── SSE 재연결 백오프 ─────────────
    //
    // 이 두 테스트가 **여기 있는 건 파일 소유 때문**이다(제자리는 `remote/matching/PartySseEventTest`
    // 다). 옮길 때 함께 옮길 것 — 보는 대상은 `PartyEventSource` 의 순수 함수다.

    /**
     * 재연결 간격이 **고정 3초였다.** 실패를 세지 않아 서버가 502/503 을 내거나 Redis 가 죽은 동안에도
     * 간격이 그대로였고, 그때 이 루프는 재기동 중인 서버를 때리는 하드 루프가 된다 — 대기 화면에
     * 12명이 있으면 분당 240회다. 그래서 횟수에 따라 늘리고 상한을 둔다.
     */
    @Test
    fun `재연결 간격은 실패 횟수만큼 늘고 30초에서 멈춘다`() {
        // 지터 0 = 표의 값 그대로. 지터는 아래 테스트가 따로 본다.
        assertEquals(3_000L, reconnectDelayMs(failures = 1, jitter = 0.0))
        assertEquals(6_000L, reconnectDelayMs(failures = 2, jitter = 0.0))
        assertEquals(12_000L, reconnectDelayMs(failures = 3, jitter = 0.0))
        assertEquals(30_000L, reconnectDelayMs(failures = 4, jitter = 0.0))
        // 상한 뒤로는 머문다 — 재시도를 멈추지는 않는다(폴백 폴링이 더 비싸다).
        assertEquals(30_000L, reconnectDelayMs(failures = 50, jitter = 0.0))
        // 세는 쪽이 틀려도 0ms 하드 루프가 되지 않게 — 0·음수는 첫 간격이다.
        assertEquals(3_000L, reconnectDelayMs(failures = 0, jitter = 0.0))
        assertEquals(3_000L, reconnectDelayMs(failures = -3, jitter = 0.0))
    }

    /**
     * 지터는 **더하기만** 한다 — 상한(30초)과 하한(3초)이 둘 다 지키려는 값이다. 12명이 같은 장애로
     * 동시에 끊겼을 때 같은 초에 다시 몰리는 것을 푸는 게 목적이다.
     */
    @Test
    fun `재연결 간격에 지터를 더한다 - 기준값 아래로는 내려가지 않는다`() {
        assertEquals(3_600L, reconnectDelayMs(failures = 1, jitter = 1.0)) // 3s + 20%
        assertEquals(36_000L, reconnectDelayMs(failures = 9, jitter = 1.0)) // 30s + 20%
        for (failures in 1..6) {
            for (jitter in listOf(0.0, 0.3, 0.5, 1.0)) {
                val wait = reconnectDelayMs(failures, jitter)
                assertTrue("간격이 첫 값보다 짧다 (failures=$failures, jitter=$jitter)", wait >= 3_000L)
                assertTrue("상한+지터를 넘었다 (failures=$failures, jitter=$jitter)", wait <= 36_000L)
            }
        }
    }

    // 생성 실패만 바꿔 끼우는 호출 — 네 좌표·라벨·정원은 어느 테스트에서도 같다.
    private fun openError(failure: Throwable): PartyOpenException = runBlocking {
        runCatching {
            RemoteRideRepository(FakeMatchingApi(openFailure = failure), session()).createParty(newParty())
        }.exceptionOrNull() as PartyOpenException
    }

    private fun leaveError(failure: Throwable): PartyLeaveException = runBlocking {
        runCatching {
            RemoteRideRepository(FakeMatchingApi(leaveFailure = failure), session()).leaveParty(partyId = 7)
        }.exceptionOrNull() as PartyLeaveException
    }

    private fun newParty() = NewParty(
        departureLat = 37.5665,
        departureLng = 126.9780,
        destinationLat = 37.4979,
        destinationLng = 127.0276,
        departure = "서울시청",
        destination = "강남역",
        capacity = 3,
    )

    /** 게이트웨이가 가로챈 응답 — 본문이 JSON 이 아니라 HTML 이다. `readApiError()` 가 null 을 돌려준다. */
    private fun htmlError(status: Int) = HttpException(
        Response.error<Unit>(
            status,
            "<html><body><h1>503 Service Temporarily Unavailable</h1></body></html>"
                .toResponseBody("text/html".toMediaType()),
        ),
    )

    /** 서버 공통 에러 본문 그대로 — 저장소가 code 를 읽어 분기한다. */
    private fun conflict(code: String, message: String) = httpError(409, code, message)

    private fun httpError(status: Int, code: String, message: String) = HttpException(
        Response.error<Unit>(
            status,
            """{"code":"$code","message":"$message"}""".toResponseBody("application/json".toMediaType()),
        ),
    )

    // ─── 걸어서 가까운 방 추천 ────────────────────────────────────────────

    /**
     * **요청 좌표가 제자리로 가는지.** 서버 record 필드명이 `destLat`/`destLng` 인데 응답 쪽만
     * `destinationLat`/`destinationLng` 이다. 기존 DTO 를 복사해 쓰면 도착 좌표가 빠지고, 전부
     * primitive double 이라 서버에서 `0.0` 이 되며, (0,0) 은 서비스 지역 밖이라 **400
     * OUT_OF_SERVICE_AREA** 가 떨어진다. 즉 이 테스트가 깨지면 사용자에게는 "서비스 지역이
     * 아니에요" 로 보인다.
     */
    @Test
    fun `추천 요청은 네 좌표를 제자리에 싣는다`() = runBlocking {
        val api = FakeMatchingApi()

        RemoteRideRepository(api, session()).findRealtimeCandidates(
            departureLat = 37.4979, departureLng = 127.0276,
            destinationLat = 37.3948, destinationLng = 127.1112,
        )

        val sent = api.realtimeRequest!!
        assertEquals(37.4979, sent.departureLat, 1e-9)
        assertEquals(127.0276, sent.departureLng, 1e-9)
        assertEquals("도착 위도가 빠졌다 — 서버에서 0.0 이 되어 400 이 된다", 37.3948, sent.destLat, 1e-9)
        assertEquals("도착 경도가 빠졌다 — 서버에서 0.0 이 되어 400 이 된다", 127.1112, sent.destLng, 1e-9)
    }

    /** 후보가 없는 건 **정상**이다 — 빈 목록을 돌려주고 예외를 던지지 않는다. */
    @Test
    fun `후보가 없으면 빈 목록이다`() = runBlocking {
        val repository = RemoteRideRepository(FakeMatchingApi(realtimeCandidates = emptyList()), session())

        assertTrue(repository.findRealtimeCandidates(1.0, 2.0, 3.0, 4.0).isEmpty())
    }

    /** 502 는 도보 경로 엔진 장애다 — 화면이 "지금은 추천을 못 가져왔어요" 로 갈리게 좁혀 던진다. */
    @Test
    fun `경로 엔진 실패는 RealtimeMatchException 으로 좁힌다`() = runBlocking {
        val api = FakeMatchingApi(realtimeFailure = error502())
        val repository = RemoteRideRepository(api, session())

        val error = runCatching { repository.findRealtimeCandidates(1.0, 2.0, 3.0, 4.0) }.exceptionOrNull()

        val realtime = error as RealtimeMatchException
        assertTrue(realtime.isRouteUnavailable)
        assertFalse(realtime.isAlreadyInParty)
    }

    /** 409 면 이미 방에 들어가 있다 — 화면은 추천 섹션 자체를 숨겨야 하므로 사유가 갈려야 한다. */
    @Test
    fun `이미 방이 있으면 그 사유로 갈린다`() = runBlocking {
        val api = FakeMatchingApi(realtimeFailure = error409AlreadyJoined())
        val repository = RemoteRideRepository(api, session())

        val error = runCatching { repository.findRealtimeCandidates(1.0, 2.0, 3.0, 4.0) }.exceptionOrNull()

        val realtime = error as RealtimeMatchException
        assertTrue(realtime.isAlreadyInParty)
        assertFalse(realtime.isRouteUnavailable)
    }

    private fun error502() = HttpException(
        Response.error<Unit>(
            502,
            """{"code":"ROUTE_SEARCH_FAILED","message":"경로 조회에 실패했습니다."}"""
                .toResponseBody("application/json".toMediaType()),
        ),
    )

    private fun error409AlreadyJoined() = HttpException(
        Response.error<Unit>(
            409,
            """{"code":"ALREADY_JOINED_OTHER_PARTY","message":"이미 참여 중인 방이 있습니다."}"""
                .toResponseBody("application/json".toMediaType()),
        ),
    )

    /** 본문 없는 실패 — 게이트웨이·프록시가 가로챈 응답이 이렇게 온다(서버 코드가 없다). */
    private fun bodylessError(status: Int) = HttpException(
        Response.error<Unit>(status, "".toResponseBody("application/json".toMediaType())),
    )

    private class FakeMatchingApi(
        private val joinFailure: Throwable? = null,
        private val finishFailure: Throwable? = null,
        private val realtimeCandidates: List<MatchCandidateResponse> = emptyList(),
        private val realtimeFailure: Throwable? = null,
        private val openFailure: Throwable? = null,
        private val leaveFailure: Throwable? = null,
    ) : MatchingApi {
        var realtimeRequest: RealTimeMatchingRequestDto? = null
        var joinCall: Long? = null
        var finishCall: Long? = null
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

        override suspend fun getRealtimeCandidates(

            request: RealTimeMatchingRequestDto,

        ): List<MatchCandidateResponse> {
            realtimeRequest = request
            realtimeFailure?.let { throw it }
            return realtimeCandidates
        }


        override suspend fun getPartyDetail(partyId: Long): PartyDetailResponse {
            detailCallCount++
            return detail(partyId)
        }

        override suspend fun openParty(request: OpenPartyRequestDto): OpenPartyResponse {
            openRequest = request
            openFailure?.let { throw it }
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

        override suspend fun finishParty(partyId: Long) {
            finishCall = partyId
            finishFailure?.let { throw it }
        }

        override suspend fun leaveParty(partyId: Long) {
            leaveCall = partyId
            leaveFailure?.let { throw it }
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
