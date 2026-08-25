package com.moyeota.domain.repository

import com.moyeota.domain.model.NewParty
import com.moyeota.domain.model.Ride

interface RideRepository {
    fun getNearbyParties(): List<Ride>
    fun getMyRides(): List<Ride>

    // 조회 — GET /api/v1/matching/rooms, /rooms/{partyId}
    suspend fun getParties(): List<Ride>
    suspend fun getPartyDetail(partyId: Long): Ride

    // 액션 — 성공 후 화면이 갱신해야 하는 대상은 아래 주석 참고.
    /** POST /api/v1/matching/rooms. 생성된 방을 그대로 돌려주므로 MatchWaiting 으로 바로 넘길 수 있다. */
    suspend fun createParty(request: NewParty): Ride

    /**
     * 합류. **백엔드에 REST 엔드포인트가 아직 없어** [ApiNotAvailableException] 을 던진다.
     * (PartyApplicationService.join 은 구현돼 있으나 PartyController 에 매핑이 없음)
     */
    suspend fun joinParty(partyId: Long, memberId: Long)

    /** DELETE /api/v1/matching/leave/{partyId}/{memberId}. 성공 시 Explore 목록 재조회 필요. */
    suspend fun leaveParty(partyId: Long, memberId: Long)

    /** POST /api/v1/matching/ready/{partyId}/{memberId}. 성공 시 getPartyDetail 재조회 필요. */
    suspend fun setReady(partyId: Long, memberId: Long)

    /** DELETE /api/v1/matching/ready/{partyId}/{memberId}. 성공 시 getPartyDetail 재조회 필요. */
    suspend fun cancelReady(partyId: Long, memberId: Long)

    /** POST /api/v1/matching/start/{partyId}/{memberId}. 방장만 가능. 성공 시 상태가 MATCHING 으로 바뀐다. */
    suspend fun startMatching(partyId: Long, memberId: Long)
}
