package com.moyeota.domain.repository

import com.moyeota.domain.model.AssignedDriver
import com.moyeota.domain.model.NewParty
import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RouteEstimate

interface RideRepository {
    fun getNearbyParties(): List<Ride>
    fun getMyRides(): List<Ride>

    // 조회 — GET /api/v1/matching/rooms, /rooms/{partyId}
    /** 목록 응답에는 좌표·요금·경로가 없다(partyId/출발/도착/인원/정원/상태만). 상세는 [getPartyDetail]. */
    suspend fun getParties(): List<Ride>

    /**
     * 지도 범위(남서 [swLat]/[swLng] ~ 북동 [neLat]/[neLng]) 안의 모집 중 방 목록.
     * GET /api/v1/matching/rooms?swLat=&swLng=&neLat=&neLng=
     *
     * [getParties] 와 같은 [Ride] 목록을 돌려주되, **출발 좌표(originLat/originLng)가 채워져 있다**
     * — 지도 핀을 찍는 데 쓴다. 도착 좌표·요금·경로는 여전히 없으니 [getPartyDetail] 로 받는다.
     */
    suspend fun getPartiesWithin(
        swLat: Double,
        swLng: Double,
        neLat: Double,
        neLng: Double,
    ): List<Ride>

    suspend fun getPartyDetail(partyId: Long): Ride

    // 액션 — 성공 후 화면이 갱신해야 하는 대상은 아래 주석 참고.
    /**
     * POST /api/v1/matching/rooms. 생성된 방을 그대로 돌려주므로 MatchWaiting 으로 바로 넘길 수 있다.
     *
     * 생성자는 **로그인 토큰이 정한다**(@CurrentUser) — 합류·나가기와 같다. 요청 본문에 사용자 id 를
     * 싣지 않는다. 생성 응답에는 멤버 목록이 없어 인원 수만큼 자리 표시용 멤버가 채워지므로,
     * 실제 멤버 식별자가 필요하면 [getPartyDetail] 로 다시 읽는다.
     */
    suspend fun createParty(request: NewParty): Ride

    /**
     * 합류. POST /api/v1/matching/rooms/{partyId}/join.
     * 합류자는 **로그인 토큰이 정한다**(@CurrentUser) — memberId 를 넘기지 않는다.
     * 서버가 **합류 후 방 상세를 그대로 응답**하므로 별도 재조회 없이 MatchWaiting 으로 넘길 수 있다.
     *
     * 정원이 차면 서버가 알아서 매칭을 시작한다(status COMPLETED → MATCHING). 앱이 시작을 트리거하지 않는다.
     * 실패 응답: 401 `UNAUTHORIZED`(미로그인) /
     * 409 `ALREADY_JOINED_OTHER_PARTY`("이미 참여 중인 방이 있습니다.") /
     * 409 `PARTY_CLOSED`("마감된 방입니다.") / 이미 꽉 참 / 이미 참여한 방.
     */
    suspend fun joinParty(partyId: Long): Ride

    /**
     * 나가기. DELETE /api/v1/matching/leave/{partyId}. 나가는 주체는 토큰이 정한다.
     * 성공 시 Explore 목록 재조회 필요.
     *
     * 그 방의 멤버가 아니면 403 `NOT_PARTY_MEMBER`. 마지막 멤버가 나가면 방은 CANCELED 로 닫힌다(실측).
     */
    suspend fun leaveParty(partyId: Long)

    /**
     * GET /api/v1/matching/rooms/{partyId}/driver — 배정된 기사 요약.
     * 기사 미배정(taxiDriverId == null)이면 서버가 예외를 던지므로 status 가
     * DRIVER_ASSIGNED/IN_RIDE 일 때만 호출한다. 기사명·별점은 서버에 아직 없다.
     */
    suspend fun getAssignedDriver(partyId: Long): AssignedDriver

    /**
     * POST /api/v1/matching/routes — 방을 만들기 전 좌표만으로 보는 경로 미리보기.
     *
     * 예상 요금·소요시간과 인코딩 폴리라인([RouteEstimate.encodedPath])을 돌려준다.
     * 이미 만들어진 방의 경로는 [getPartyDetail] 의 `routePolyline` 으로 얻는다.
     */
    suspend fun previewRoute(
        departureLat: Double,
        departureLng: Double,
        destinationLat: Double,
        destinationLng: Double,
    ): RouteEstimate
}
