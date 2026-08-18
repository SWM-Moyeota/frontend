package com.moyeota.domain.repository

import com.moyeota.domain.model.Ride

interface RideRepository {
    fun getNearbyParties(): List<Ride>
    fun getMyRides(): List<Ride>

    suspend fun getParties(): List<Ride>
    suspend fun getPartyDetail(partyId: Long): Ride
}
