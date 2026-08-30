package com.moyeota.data.repository

import com.moyeota.data.remote.MatchingApi
import com.moyeota.data.remote.toRequestDto
import com.moyeota.data.remote.toRide
import com.moyeota.domain.model.NewParty
import com.moyeota.domain.model.Ride
import com.moyeota.domain.repository.ApiNotAvailableException
import com.moyeota.domain.repository.RideRepository

// 매칭 도메인만 서버 연동 — 나머지는 더미로 위임한다.
class RemoteRideRepository(
    private val api: MatchingApi,
    private val local: RideRepository = DummyRideRepository(),
) : RideRepository {

    override fun getNearbyParties(): List<Ride> = local.getNearbyParties()

    override fun getMyRides(): List<Ride> = local.getMyRides()

    override suspend fun getParties(): List<Ride> = api.getParties().list.map { it.toRide() }

    override suspend fun getPartyDetail(partyId: Long): Ride = api.getPartyDetail(partyId).toRide()

    override suspend fun createParty(request: NewParty): Ride =
        api.openParty(request.toRequestDto()).toRide()

    // 백엔드 PartyApplicationService.join 은 있으나 PartyController 에 매핑이 없다(origin/develop 확인).
    // 경로를 추측해 호출하면 404 로 원인이 흐려지므로, 계약은 유지하되 명시적으로 실패시킨다.
    override suspend fun joinParty(partyId: Long, memberId: Long) {
        throw ApiNotAvailableException("합류 API가 아직 서버에 없어요")
    }

    override suspend fun leaveParty(partyId: Long, memberId: Long) = api.leaveParty(partyId, memberId)

    override suspend fun setReady(partyId: Long, memberId: Long) = api.ready(partyId, memberId)

    override suspend fun cancelReady(partyId: Long, memberId: Long) = api.cancelReady(partyId, memberId)

    override suspend fun startMatching(partyId: Long, memberId: Long) = api.startMatching(partyId, memberId)
}
