package com.moyeota.data.repository

import com.moyeota.data.remote.DispatchApi
import com.moyeota.data.remote.toDriverLocation
import com.moyeota.domain.model.DriverLocation
import com.moyeota.domain.repository.DispatchRepository

class RemoteDispatchRepository(
    private val api: DispatchApi,
) : DispatchRepository {

    override suspend fun getDriverLocation(partyId: Long): DriverLocation =
        api.getDriverLocation(partyId).toDriverLocation()
}
