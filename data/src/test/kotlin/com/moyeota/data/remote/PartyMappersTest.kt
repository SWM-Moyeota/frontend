package com.moyeota.data.remote

import com.moyeota.data.remote.dto.DriverSummaryResponse
import com.moyeota.data.remote.dto.OpenPartyRequestDto
import com.moyeota.data.remote.dto.OpenPartyResponse
import com.moyeota.data.remote.dto.PartyDetailResponse
import com.moyeota.data.remote.dto.PartyListResponse
import com.moyeota.data.remote.dto.PartyStatusResponse
import com.moyeota.data.remote.dto.RouteEstimateResponse
import com.moyeota.data.remote.dto.RouteRequestDto
import com.moyeota.domain.model.NewParty
import com.moyeota.domain.model.RideStatus
import kotlinx.serialization.KSerializer
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
        assertEquals(RideStatus.CANCELED, partyStatusToRideStatus("CANCELED"))
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
     * 백엔드가 자동 기사 매칭으로 바뀌며 방장 개념이 사라졌다. 예전엔 입장 시각(joinedAt)이 가장 이른
     * 멤버를 방장으로 추정해 "방장" 닉네임을 붙였는데, 그 추정이 되살아나지 않는지 못박는다.
     * 서버가 그 필드를 아예 없앴으므로(Backend #218) 추정의 재료조차 남지 않았다.
     */
    @Test
    fun `멤버는 전부 동등하게 매핑된다 — 방장 추정이 없다`() {
        val detail = detailResponse(
            members = listOf(
                member(publicId = UUID_OTHER, nickname = "다른사람"),
                member(publicId = UUID_ME, nickname = "성윤"),
            ),
        )

        val ride = detail.toRide()

        // 서버가 준 순서 그대로, 재정렬하거나 특별 취급하지 않는다.
        assertEquals(listOf(UUID_OTHER, UUID_ME), ride.members.map { it.id })
        assertEquals(listOf("다른사람", "성윤"), ride.members.map { it.nickname })
    }

    /** 서버가 주는 값(닉네임·이미지·탑승 횟수)은 그대로, 주지 않는 값(별점·인증)은 지어내지 않는다. */
    @Test
    fun `멤버 요약을 표시용 User 로 옮긴다`() {
        val detail = detailResponse(
            members = listOf(
                member(
                    publicId = UUID_OTHER,
                    nickname = "모여타",
                    imageUrl = "https://cdn.moyeota.com/p/1.png",
                    rideCount = 12,
                ),
            ),
        )

        val user = detail.toRide(currentUuid = UUID_ME).members.single()

        assertEquals(UUID_OTHER, user.id)
        assertEquals("모여타", user.nickname)
        assertEquals("https://cdn.moyeota.com/p/1.png", user.imageUrl)
        assertEquals(12, user.rideCount)
        // 평가 API 도 배지 매핑도 없다 — 4.9·매너 98% 같은 가짜 값이 되살아나면 여기서 깨진다.
        assertEquals(0.0, user.rating, 0.0)
        assertEquals("", user.verifiedLabel)
        assertFalse(user.isMe)
    }

    /**
     * 탈퇴 등으로 유저 요약이 없으면 서버가 publicId·nickname·imageUrl 을 전부 null 로 내려보낸다.
     * 그래도 방 상세는 열려야 한다 — 매핑이 터지거나 빈 닉네임이 그대로 화면에 나가면 안 된다.
     */
    @Test
    fun `요약이 없는 멤버는 탈퇴한 회원으로 표시하고 id 를 비운다`() {
        val detail = detailResponse(
            members = listOf(member(publicId = null, nickname = null, rideCount = 3)),
        )

        val user = detail.toRide(currentUuid = UUID_ME).members.single()

        assertEquals("", user.id)
        assertEquals("탈퇴한 회원", user.nickname)
        assertNull(user.imageUrl)
        // rideCount 는 파티 쪽 데이터라 유저 요약이 없어도 채워진다.
        assertEquals(3, user.rideCount)
        // publicId 가 null 이라고 "나"가 되면 안 된다 — currentUuid 가 null 인 경우까지 참이 될 수 있다.
        assertFalse(user.isMe)
    }

    /** publicId 가 JWT sub 와 같은 값이라 이 비교가 곧 "나" 판정이다(기존 「나 제외」 필터 결함의 해소). */
    @Test
    fun `publicId 가 세션 uuid 와 같은 멤버만 나로 표시한다`() {
        val ride = detailResponse().toRide(currentUuid = UUID_ME)

        assertEquals(listOf(true, false), ride.members.map { it.isMe })
        assertEquals(1, ride.members.count { it.isMe })
    }

    /** 미로그인·복원 전이면 currentUserUuid 가 null 이다. 아무도 나로 표시되지 않아야 한다. */
    @Test
    fun `세션 uuid 를 모르면 아무도 나로 표시하지 않는다`() {
        val ride = detailResponse().toRide(currentUuid = null)

        assertTrue(ride.members.none { it.isMe })
    }

    /** 자리 표시용 멤버(목록·생성 응답)는 식별자가 없어 "나"를 알 수 없다 — 판정이 새어 들어오면 안 된다. */
    @Test
    fun `자리 표시용 멤버는 나로 표시되지 않는다`() {
        val ride = PartyListResponse.PartyItem(
            partyId = 7,
            currentMembers = 2,
            capacity = 3,
            status = "ACTIVE",
        ).toRide()

        assertTrue(ride.members.none { it.isMe })
        assertEquals(listOf("멤버 1", "멤버 2"), ride.members.map { it.nickname })
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
        // members 는 MemberInfo(publicId, nickname, imageUrl, badgeId, rideCount) —
        // 내부 PK memberId 와 joinedAt 은 사라졌고, 둘째 멤버는 요약이 없는(탈퇴) 경우다.
        // badgeId 는 서버 TODO 라 항상 null.
        val body = """
            {"id":7,"departureLat":37.5665,"departureLng":126.978,"destinationLat":37.4979,
             "destinationLng":127.0276,"departure":"서울시청","destination":"강남역","capacity":3,
             "currentMembers":2,"departureRadius":500,"destinationRadius":500,"status":"DRIVER_ASSIGNED",
             "createdAt":"2026-08-30T09:00:00Z",
             "members":[{"publicId":"$UUID_ME","nickname":"성윤","imageUrl":null,"badgeId":null,
                         "rideCount":4},
                        {"publicId":null,"nickname":null,"imageUrl":null,"badgeId":null,
                         "rideCount":0}],
             "estimateFare":9600,"estimateTime":14,"route":"_p~iF~ps|U","taxiDriverId":42}
        """.trimIndent()

        val ride = json.decodeFromString<PartyDetailResponse>(body).toRide(currentUuid = UUID_ME)

        assertEquals("7", ride.id)
        assertEquals(RideStatus.DISPATCHING, ride.status)
        assertEquals(listOf(UUID_ME, ""), ride.members.map { it.id })
        assertEquals(listOf("성윤", "탈퇴한 회원"), ride.members.map { it.nickname })
        assertEquals(listOf(4, 0), ride.members.map { it.rideCount })
        assertEquals(listOf(true, false), ride.members.map { it.isMe })
        assertEquals(42L, ride.driverId)
        assertEquals(9600, ride.estimatedFare)
    }

    /**
     * 서버가 `memberId`·`joinedAt` 으로 되돌아가거나 새 필드를 얹어도 방 상세가 열리는 건 지켜야 한다
     * (`ignoreUnknownKeys` + 전 필드 기본값). 파싱이 터지면 21/25 화면이 통째로 죽는다.
     */
    @Test
    fun `멤버 항목에 모르는 필드가 있거나 필드가 빠져도 파싱이 깨지지 않는다`() {
        val body = """
            {"id":9,"departure":"성결대 정문","destination":"안양역","capacity":3,"status":"ACTIVE",
             "members":[{"memberId":1,"publicId":"$UUID_OTHER","nickname":"모여타","unknown":"x"},
                        {"joinedAt":"2026-08-30T09:01:00Z"}]}
        """.trimIndent()

        val ride = json.decodeFromString<PartyDetailResponse>(body).toRide(currentUuid = UUID_ME)

        assertEquals(listOf(UUID_OTHER, ""), ride.members.map { it.id })
        assertEquals(listOf(0, 0), ride.members.map { it.rideCount })
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

    // ---- 합류 응답(POST /matching/rooms/{partyId}/join) ----
    //
    // 이 DTO 의 쓰임은 **합류 응답 하나뿐**이다. 예전에는 폴링용 상태 조회
    // `GET /matching/rooms/{partyId}/status` 도 같은 모양이었지만 백엔드가 그 엔드포인트와
    // 지문(fingerprint)을 함께 삭제했다(`04f6be7`) — 매칭방 변화가 SSE 전용이 되면서
    // 「요약이 상세와 같은가」를 비교할 이유가 없어졌다.

    /**
     * 합류 응답은 상세와 **같은 상태 변환표**를 써야 한다([partyStatusToRideStatus]) —
     * 갈리면 합류 직후 상태와 방 상세가 같은 방을 서로 다른 단계로 보여 준다.
     */
    @Test
    fun `합류 응답을 도메인 PartyStatus 로 매핑한다`() {
        val status = PartyStatusResponse(
            status = "DRIVER_ASSIGNED",
            currentMembers = 3,
        ).toPartyStatus()

        assertEquals(RideStatus.DISPATCHING, status.status)
        assertEquals(3, status.currentMembers)
    }

    /**
     * **합류 응답이 이 모양으로 바뀌었다**(Backend #213) — 방 상세가 아니라 상태·인원 두 값뿐이고,
     * `04f6be7` 에서 지문까지 빠져 **정확히 두 값**이다.
     *
     * 예전 응답(방 상세)을 기대하던 DTO 로는 이 본문이 **역직렬화에서 터졌다** — `PartyDetailResponse.id`
     * 가 기본값 없는 유일한 필드라서다. 서버는 이미 멤버를 추가해 둔 상태라 그 실패가 곧
     * "어떤 방에도 못 들어가는" 계정을 만들었다(RemoteRideRepository.recoverMembership 참고).
     */
    @Test
    fun `합류 응답 JSON 을 실제 서버 shape 그대로 역직렬화한다`() {
        // 백엔드 PartyController.join → PartyStatusResponse(status, currentMembers). 내가 마지막 자리를
        // 채운 경우(서버가 그 자리에서 정원 충족까지 끝내고 COMPLETED 를 돌려준다).
        val body = """{"status":"COMPLETED","currentMembers":3}"""

        val status = json.decodeFromString<PartyStatusResponse>(body).toPartyStatus()

        assertEquals(RideStatus.MATCHED, status.status)
        assertEquals(3, status.currentMembers)
    }

    /**
     * 합류 응답의 필드가 **딱 둘**인 것을 못박는다 — 지문이 다시 생기면 비교할 짝(상세의 지문)이 없는
     * 죽은 필드가 되고, 「요약과 상세를 비교한다」는 사라진 설계가 코드에 되살아난다.
     */
    @Test
    fun `합류 응답 DTO 에는 상태와 인원 두 필드만 있다`() {
        assertEquals(listOf("status", "currentMembers"), PartyStatusResponse.serializer().fieldNames())
    }

    /**
     * 롤아웃 중 서버가 아직 옛 버전이라 `fingerprint` 를 실어 보내도 합류가 깨지지 않아야 한다 —
     * 디코더가 `ignoreUnknownKeys` 다(NetworkModule). 합류는 서버가 **멤버 추가를 먼저 커밋**하므로,
     * 여기서 파싱이 터지면 그 계정은 30분 스윕 전까지 어떤 방에도 못 들어간다.
     */
    @Test
    fun `옛 서버가 지문을 실어 보내도 합류 응답 파싱이 깨지지 않는다`() {
        val status = json.decodeFromString<PartyStatusResponse>(
            """{"status":"FINISHED","currentMembers":3,"fingerprint":"9f3c1a7b4e2d0586","memberIds":[1,2,3]}""",
        ).toPartyStatus()

        assertEquals(RideStatus.COMPLETED, status.status)
        assertEquals(3, status.currentMembers)
    }

    // ---- 상세 응답에 지문이 없다 ----

    /**
     * 옛 서버의 상세 응답이 지문을 실어 보내도 **조용히 무시**돼야 한다. 25·26 의 주기 조회가
     * 이 상세로 옮겨왔으므로, 여기서 파싱이 터지면 두 화면이 배차·운행 전이를 영영 못 잡는다.
     */
    @Test
    fun `옛 서버가 지문을 실어 보내도 상세 매핑이 깨지지 않는다`() {
        val body = """
            {"id":7,"departure":"서울시청","destination":"강남역","capacity":3,"currentMembers":3,
             "status":"MATCHING","fingerprint":"9f3c1a7b4e2d0586"}
        """.trimIndent()

        val ride = json.decodeFromString<PartyDetailResponse>(body).toRide()

        assertEquals("7", ride.id)
        assertEquals(RideStatus.DISPATCHING, ride.status)
    }

    /**
     * 상세 DTO 에 지문 필드가 **되살아나지 않는 것**을 못박는다. 서버가 지웠으니 되살리면 항상 null 인
     * 필드가 되고, 그 null 을 근거로 「바뀌었나」를 판정하던 옛 분기가 따라 돌아온다
     * (「지문이 없으면 매 주기 상세를 읽는다」 같은 예외 조건).
     */
    @Test
    fun `상세 응답 DTO 에 지문 필드가 없다`() {
        val fields = PartyDetailResponse.serializer().fieldNames()

        assertFalse("PartyDetailResponse 에 fingerprint 가 되살아났다", fields.contains("fingerprint"))
        // 폴링이 읽는 값들은 그대로 있어야 한다 — 상세가 25·26 의 유일한 전이 근거다.
        assertTrue(fields.containsAll(listOf("status", "members", "taxiDriverId", "route")))
    }

    /** DTO 의 **직렬화 계약**(JSON 키 이름·순서)을 읽는다 — JVM 필드 목록과 달리 Companion 이 섞이지 않는다. */
    private fun KSerializer<*>.fieldNames(): List<String> =
        (0 until descriptor.elementsCount).map { descriptor.getElementName(it) }

    /**
     * 같은 방의 상세를 두 번 매핑하면 **`==` 로 같다**는 것 — 25·26 폴링이 「안 바뀌었다」를 판정하는
     * 유일한 근거가 이 동등성이다(지문이 하던 일을 [Ride] 의 data class equals 가 대신한다).
     *
     * 멤버가 교체되면(인원수는 같다) 달라야 한다 — 요약 비교로는 못 잡아 지문이 필요했던 바로 그 경우가,
     * 받는 게 상세가 된 지금은 equals 로 그냥 잡힌다.
     */
    @Test
    fun `같은 방의 상세를 두 번 매핑하면 서로 같고 멤버가 교체되면 달라진다`() {
        val held = detailResponse().toRide(currentUuid = UUID_ME)
        val same = detailResponse().toRide(currentUuid = UUID_ME)
        val swapped = detailResponse(
            members = listOf(
                member(publicId = UUID_ME, nickname = "성윤"),
                member(publicId = UUID_STRANGER, nickname = "새로온사람"),
            ),
        ).toRide(currentUuid = UUID_ME)

        assertEquals(held, same)
        // 인원수가 같아도 멤버가 바뀌면 다른 방 상태다(한 명 나가고 한 명 들어온 경우).
        assertEquals(held.members.size, swapped.members.size)
        assertFalse(held == swapped)
    }

    private fun detailResponse(
        members: List<PartyDetailResponse.MemberInfo> = listOf(
            member(publicId = UUID_ME, nickname = "성윤"),
            member(publicId = UUID_OTHER, nickname = "다른사람"),
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

    private fun member(
        publicId: String? = UUID_ME,
        nickname: String? = "성윤",
        imageUrl: String? = null,
        rideCount: Int = 0,
    ) = PartyDetailResponse.MemberInfo(
        publicId = publicId,
        nickname = nickname,
        imageUrl = imageUrl,
        badgeId = null,
        rideCount = rideCount,
    )

    private companion object {
        /** 서버가 내려보내는 UUID v7 문자열 형식 그대로. 로그인 세션의 `sub` 와 같은 값을 가정한다. */
        const val UUID_ME = "01a06145-3caf-7614-a3bd-cee6e25316b1"
        const val UUID_OTHER = "01a06145-3caf-7614-a3bd-cee6e2531999"

        /** 멤버 교체(인원수는 그대로)를 만들 때 쓰는 제3의 계정. */
        const val UUID_STRANGER = "01a06145-3caf-7614-a3bd-cee6e2531aaa"
    }
}
