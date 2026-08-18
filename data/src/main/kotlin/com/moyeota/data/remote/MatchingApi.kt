package com.moyeota.data.remote

import com.moyeota.data.remote.dto.PartyDetailResponse
import com.moyeota.data.remote.dto.PartyListResponse
import retrofit2.http.GET
import retrofit2.http.Path

interface MatchingApi {
    @GET("api/matching/rooms")
    suspend fun getParties(): PartyListResponse

    @GET("api/matching/rooms/{partyId}")
    suspend fun getPartyDetail(@Path("partyId") partyId: Long): PartyDetailResponse
}
