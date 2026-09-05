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

    // 긴급 신고 — POST /api/v1/reports, PATCH /api/v1/reports/call-result

    /**
     * 긴급 신고 접수 — 서버가 파티·위치를 저장하고 신고 id 를 반환한다.
     * 위치는 데이터 계층이 채운다 (측위 소스가 아직 없어 현재는 고정 좌표 — TODO).
     * 저장 실패 시 한국어 메시지의 [IllegalStateException] 을 던진다 —
     * 화면은 실패 여부와 무관하게 112 다이얼을 먼저 열어야 한다 (통화 최우선).
     *
     * @param partyId 신고 대상 파티. 화면에서 알 수 없으면 null (서버는 REPORT_NOT_ALLOWED
     *   응답 — 저장 실패로 처리된다)
     * @return 서버가 발급한 신고 id (reportId)
     */
    suspend fun reportEmergency(partyId: Long?): Long

    /**
     * 112 다이얼에서 앱 복귀 후 "실제로 통화했는지" 결과를 저장한다.
     * 실패 시 한국어 메시지의 [IllegalStateException] 을 던진다.
     */
    suspend fun confirmEmergencyCall(called: Boolean)
}
