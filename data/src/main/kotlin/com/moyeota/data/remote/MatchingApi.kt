package com.moyeota.data.remote

import com.moyeota.data.remote.dto.OpenPartyRequestDto
import com.moyeota.data.remote.dto.OpenPartyResponse
import com.moyeota.data.remote.dto.PartyDetailResponse
import com.moyeota.data.remote.dto.PartyListResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

// 경로 기준: origin/develop matching/interfaces/PartyController.java
// (@RequestMapping("/api/v1") + 메서드별 "/matching/...")
// 주의: backend/http/matching.http 는 v1 이 없는 옛 경로라 신뢰하지 않는다.
interface MatchingApi {
    @GET("api/v1/matching/rooms")
    suspend fun getParties(): PartyListResponse

    @GET("api/v1/matching/rooms/{partyId}")
    suspend fun getPartyDetail(@Path("partyId") partyId: Long): PartyDetailResponse

    @POST("api/v1/matching/rooms")
    suspend fun openParty(@Body request: OpenPartyRequestDto): OpenPartyResponse

    // 아래 액션 4종은 모두 204 No Content — 응답 본문이 없다.
    @DELETE("api/v1/matching/leave/{partyId}/{memberId}")
    suspend fun leaveParty(
        @Path("partyId") partyId: Long,
        @Path("memberId") memberId: Long,
    )

    @POST("api/v1/matching/ready/{partyId}/{memberId}")
    suspend fun ready(
        @Path("partyId") partyId: Long,
        @Path("memberId") memberId: Long,
    )

    @DELETE("api/v1/matching/ready/{partyId}/{memberId}")
    suspend fun cancelReady(
        @Path("partyId") partyId: Long,
        @Path("memberId") memberId: Long,
    )

    @POST("api/v1/matching/start/{partyId}/{memberId}")
    suspend fun startMatching(
        @Path("partyId") partyId: Long,
        @Path("memberId") memberId: Long,
    )

    // 합류(join)와 경로 추천(routes)은 origin/develop PartyController 에 매핑이 없어 선언하지 않는다.
    // 경로를 추측해 호출하면 404 만 나므로 RemoteRideRepository 에서 명시적으로 예외를 던진다.
}
