package com.moyeota.data.repository

import com.moyeota.data.remote.MatchingApi
import com.moyeota.data.remote.routeRequestDto
import com.moyeota.data.remote.toAssignedDriver
import com.moyeota.data.remote.toRequestDto
import com.moyeota.data.remote.toRide
import com.moyeota.data.remote.toRouteEstimate
import com.moyeota.domain.model.AssignedDriver
import com.moyeota.domain.model.NewParty
import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RouteEstimate
import com.moyeota.domain.repository.RideRepository
import com.moyeota.domain.session.UserSession

/**
 * 매칭 도메인만 서버 연동 — 나머지는 더미로 위임한다.
 *
 * [session] 이 필요한 이유는 하나다: 방 상세의 멤버 중 **누가 나인지**는 서버가 표시해 주지 않는다.
 * `MemberInfo.publicId` 가 JWT `sub` 와 같은 값이라 앱이 직접 비교해야 하고, 비교 대상인
 * 현재 사용자 UUID 의 단일 출처가 [session] 이다. 매퍼에 세션을 들려 보내지 않으면 화면마다
 * "나 찾기"를 다시 구현하게 된다.
 */
class RemoteRideRepository(
    private val api: MatchingApi,
    private val session: UserSession,
    private val local: RideRepository = DummyRideRepository(),
) : RideRepository {

    override fun getNearbyParties(): List<Ride> = local.getNearbyParties()

    override fun getMyRides(): List<Ride> = local.getMyRides()

    override suspend fun getParties(): List<Ride> = api.getParties().list.map { it.toRide() }

    // 전체 목록과 같은 경로 + 쿼리 파라미터 4개. 매퍼가 departureLat/Lng 를 originLat/Lng 로 옮긴다.
    override suspend fun getPartiesWithin(
        swLat: Double,
        swLng: Double,
        neLat: Double,
        neLng: Double,
    ): List<Ride> = api.getPartiesWithin(swLat, swLng, neLat, neLng).list.map { it.toRide() }

    // currentUserUuid 는 호출 시점에 읽는다 — 재로그인으로 주체가 바뀌어도 다음 조회부터 바로 반영된다.
    override suspend fun getPartyDetail(partyId: Long): Ride =
        api.getPartyDetail(partyId).toRide(session.currentUserUuid)

    // 생성자는 서버가 토큰에서 정한다 — 요청 본문에도 응답에도 id 가 없다.
    override suspend fun createParty(request: NewParty): Ride =
        api.openParty(request.toRequestDto()).toRide()

    // 합류 응답이 곧 방 상세라 재조회 없이 그대로 반환한다. 합류자는 Bearer 토큰이 정한다.
    override suspend fun joinParty(partyId: Long): Ride =
        api.joinParty(partyId).toRide(session.currentUserUuid)

    override suspend fun leaveParty(partyId: Long) = api.leaveParty(partyId)

    override suspend fun getAssignedDriver(partyId: Long): AssignedDriver =
        api.getAssignedDriver(partyId).toAssignedDriver()

    // 백엔드가 POST 로 바뀌어 열린 엔드포인트. 응답 폴리라인 필드는 route 가 아니라 path 다.
    override suspend fun previewRoute(
        departureLat: Double,
        departureLng: Double,
        destinationLat: Double,
        destinationLng: Double,
    ): RouteEstimate = api.previewRoute(
        routeRequestDto(departureLat, departureLng, destinationLat, destinationLng),
    ).toRouteEstimate()
}
