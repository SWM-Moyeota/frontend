package com.moyeota.data.repository

import com.moyeota.data.remote.MatchingApi
import com.moyeota.data.remote.toRide
import com.moyeota.domain.model.Ride
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
}
