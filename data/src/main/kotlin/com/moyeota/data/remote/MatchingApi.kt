package com.moyeota.data.remote

import com.moyeota.data.remote.dto.DriverSummaryResponse
import com.moyeota.data.remote.dto.OpenPartyRequestDto
import com.moyeota.data.remote.dto.OpenPartyResponse
import com.moyeota.data.remote.dto.PartyDetailResponse
import com.moyeota.data.remote.dto.PartyListResponse
import com.moyeota.data.remote.dto.RouteEstimateResponse
import com.moyeota.data.remote.dto.RouteRequestDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 경로 기준: matching/interfaces/PartyController.java
 * (@RequestMapping("/api/v1") + 메서드별 "/matching/...").
 * 주의: backend/http/matching.http 는 v1 이 없는 옛 경로 + 옛 필드명(hostId)이라 신뢰하지 않는다.
 *
 * **여기 전부가 토큰 필수다.** 목록·상세 조회까지 포함해 예외가 없다
 * (Security 가 `/api/v1/auth/` 하위를 뺀 전 경로에 `anyRequest().authenticated()` 를 건다).
 * 미로그인 상태에서 부르면 화면에 데이터 대신 401 에러가 뜨므로,
 * 이 API 를 쓰는 화면은 로그인 뒤에만 열려야 한다.
 */
interface MatchingApi {
    @GET("api/v1/matching/rooms")
    suspend fun getParties(): PartyListResponse

    /**
     * 지도 화면 범위(남서·북동 모서리) 안의 ACTIVE 방 목록.
     * 전체 목록과 **경로가 같고** 서버(PartyController.listWithin)가 `params={swLat,swLng,neLat,neLng}`
     * 유무로 핸들러를 고른다 — 4개 중 하나라도 빠지면 전체 목록 핸들러로 떨어진다.
     * 응답 아이템에는 departureLat/departureLng 가 포함된다.
     */
    @GET("api/v1/matching/rooms")
    suspend fun getPartiesWithin(
        @Query("swLat") swLat: Double,
        @Query("swLng") swLng: Double,
        @Query("neLat") neLat: Double,
        @Query("neLng") neLng: Double,
    ): PartyListResponse

    @GET("api/v1/matching/rooms/{partyId}")
    suspend fun getPartyDetail(@Path("partyId") partyId: Long): PartyDetailResponse

    /**
     * 방 생성. **방장은 이제 Bearer 토큰이 정한다**
     * (`PartyController.open(@CurrentUser Long memberId, @RequestBody OpenPartyRequest request)`).
     *
     * 서버 record 에 `creatorId` 필드가 남아 있긴 하지만 `toCommand(creatorMemberId)` 가 이를 버리고
     * 토큰 주체를 쓴다 — 구버전 호환용 잔재다. 그래서 앱은 아예 보내지 않는다
     * (실측: creatorId 없이 200, 토큰 주체가 방장으로 기록됨).
     * 리포트 22번 D-5("내가 만든 방에 내가 없는" 상태)는 이것으로 해소됐다.
     */
    @POST("api/v1/matching/rooms")
    suspend fun openParty(@Body request: OpenPartyRequestDto): OpenPartyResponse

    /**
     * 합류. 성공 시 서버가 방 상세를 그대로 돌려준다(PartyDetailResult). 요청 본문은 없다.
     *
     * 합류자는 **Bearer 토큰에서 추출**된다(`PartyController.join(@PathVariable Long partyId, @CurrentUser Long memberId)`)
     * — 경로의 memberId 는 사라졌다. 토큰 없이 호출하면 401 `{"code":"USER005","message":"로그인이 필요합니다."}`.
     */
    @POST("api/v1/matching/rooms/{partyId}/join")
    suspend fun joinParty(@Path("partyId") partyId: Long): PartyDetailResponse

    /**
     * 나가기. 204 No Content — 응답 본문이 없다. 나가는 주체는 Bearer 토큰이 정한다.
     *
     * 이전에 붙어 있던 더미 `/0` 세그먼트는 **사라졌다** — 백엔드가 매핑을
     * `@DeleteMapping("/matching/leave/{partyId}")` 로 정리했다.
     * 실측: `/leave/{id}` → 204, `/leave/{id}/0` → 404. 옛 형태를 되살리면 조용히 404 다.
     */
    @DELETE("api/v1/matching/leave/{partyId}")
    suspend fun leaveParty(@Path("partyId") partyId: Long)

    // 기사 미배정이면 서버가 IllegalArgumentException 을 던진다(전역 예외 핸들러가 없어 500).
    @GET("api/v1/matching/rooms/{partyId}/driver")
    suspend fun getAssignedDriver(@Path("partyId") partyId: Long): DriverSummaryResponse

    /**
     * 방을 만들기 전 출발·도착 좌표만으로 예상 요금·소요시간·경로를 미리 본다.
     *
     * 백엔드가 `@PostMapping` + `@RequestBody RouteRequest` 로 바뀌어 열린 엔드포인트다
     * (이전 `@GetMapping` + body 조합은 OkHttp 가 요청 자체를 못 만들어 호출 불가였다).
     * 응답 폴리라인 필드명이 **path** 다 — 방 상세/생성 응답의 `route` 와 이름이 다르니 주의.
     */
    @POST("api/v1/matching/routes")
    suspend fun previewRoute(@Body request: RouteRequestDto): RouteEstimateResponse

    // 삭제된 엔드포인트: matching/ready(POST·DELETE), matching/start(POST).
    // 정원이 차면 서버가 스스로 매칭을 시작하므로 앱이 트리거하지 않는다.
}
