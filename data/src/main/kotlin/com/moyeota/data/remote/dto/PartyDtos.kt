package com.moyeota.data.remote.dto

import kotlinx.serialization.Serializable

// 백엔드 matching 도메인 응답 record 와 필드 1:1 대응
// (feature/driver-report: matching/application/dto/*.java, matching/domain/RouteEstimate.java)
// 서버 record 필드가 전부 박싱 타입(Long/Integer/Double)이라 null 이 올 수 있는 자리는 기본값으로 방어한다.
@Serializable
data class PartyListResponse(
    val list: List<PartyItem> = emptyList(),
) {
    // PartyListResponse.PartyItem — 목록에는 요금/경로/도착 좌표가 없다.
    // 출발 좌표(departureLat/Lng)는 지도 범위 조회를 위해 서버 record 에 추가된 필드다.
    // 실서버(2026-09-01) 는 무파라미터 전체 목록에도 좌표를 실어주지만, 브랜치에 따라
    // 빠질 수 있으므로 nullable + null 기본값으로 방어한다.
    @Serializable
    data class PartyItem(
        val partyId: Long,
        val departure: String = "",
        val destination: String = "",
        val currentMembers: Int = 0,
        val capacity: Int = 0,
        val status: String = "",
        val departureLat: Double? = null,
        val departureLng: Double? = null,
    )
}

// PartyDetailResult — GET /matching/rooms/{partyId} 및 POST /matching/rooms/{partyId}/{memberId}/join 의 응답
//
// 서버 record 에 hostId 도, MemberInfo.isHost 도 **없다** — 방장 개념이 매칭 도메인에서 빠졌다.
// 앱도 방장을 추정하지 않는다: 멤버는 전부 동등하게 매핑된다(PartyMappers 참고).
// Party 도메인에는 matchingStartedAt 필드가 추가됐지만 PartyDetailResult 가 노출하지 않으므로 DTO 에도 없다.
@Serializable
data class PartyDetailResponse(
    val id: Long,
    val departureLat: Double? = null,
    val departureLng: Double? = null,
    val destinationLat: Double? = null,
    val destinationLng: Double? = null,
    val departure: String = "",
    val destination: String = "",
    val capacity: Int = 0,
    val currentMembers: Int = 0,
    val departureRadius: Int = 0,
    val destinationRadius: Int = 0,
    val status: String = "",
    val createdAt: String? = null,
    val members: List<MemberInfo> = emptyList(),
    val estimateFare: Int? = null,
    val estimateTime: Int? = null,
    /** Google Encoded Polyline. 서버가 방 생성 시 네이버 경로 API 로 산출해 저장해 둔 값. */
    val route: String? = null,
    val taxiDriverId: Long? = null,
) {
    /**
     * 서버 `PartyDetailResult.MemberInfo(UUID publicId, String nickname, String imageUrl,
     * String badgeId, Integer rideCount, Instant joinedAt)`.
     *
     * **내부 PK 였던 `memberId` 는 사라졌다.** 식별자는 [publicId] 하나이며 JWT `sub` 와 같은 값이라
     * "이 멤버가 나인가"를 앱이 직접 판정할 수 있다([com.moyeota.data.remote.toRide] 의 currentUuid).
     *
     * **네 문자열 필드가 전부 nullable 인 건 방어가 아니라 서버 계약이다** — 탈퇴 등으로 유저 요약을
     * 못 찾으면 서버가 `MemberInfo(null, null, null, null, rideCount, joinedAt)` 을 그대로 내려보낸다
     * (방 상세 조회 자체는 깨지면 안 되므로). 논-널로 선언하면 그 순간 파싱이 터져 방 화면이 열리지 않는다.
     */
    @Serializable
    data class MemberInfo(
        /** UUID v7 문자열. 유저 요약이 없으면 null. */
        val publicId: String? = null,
        val nickname: String? = null,
        val imageUrl: String? = null,
        /**
         * 인증 배지. **서버가 아직 항상 null 을 넣는다**(`toMemberInfo` 의 TODO).
         * 타입도 어긋나 있다 — `MemberSummary.badgeId` 는 Long 인데 여기서는 String 이다.
         * 서버가 실제 값을 채우기 시작하면 이 필드 타입부터 다시 확인할 것.
         */
        val badgeId: String? = null,
        /** 이 멤버가 끝낸(FINISHED) 파티 수. 서버가 없으면 0 을 채워 준다. */
        val rideCount: Int = 0,
        /** ISO-8601 Instant 문자열. Spring Boot 기본 설정이 타임스탬프 직렬화를 끄므로 문자열로 온다. */
        val joinedAt: String? = null,
    )
}

/**
 * POST /api/v1/matching/rooms 요청 본문 (백엔드 `OpenPartyRequest`).
 *
 * **생성자 id 필드는 없다.** 서버 record 에 `creatorId` 가 구버전 호환으로 남아 있지만
 * `toCommand()` 가 그 값을 버리고 `@CurrentUser` 로 받은 토큰 주체를 쓴다 — 보내 봐야 무시되는
 * 값이라 아예 싣지 않는다. 되살리면 "앱이 보낸 생성자"가 유효하다는 착각을 부른다.
 */
@Serializable
data class OpenPartyRequestDto(
    val departureLat: Double,
    val departureLng: Double,
    val destinationLat: Double,
    val destinationLng: Double,
    val departure: String,
    val destination: String,
    val capacity: Int,
    val departureRadius: Int,
    val destinationRadius: Int,
)

// POST /api/v1/matching/rooms 응답 (백엔드 OpenPartyResponse — members 목록이 없다)
@Serializable
data class OpenPartyResponse(
    val id: Long,
    val departureLat: Double? = null,
    val departureLng: Double? = null,
    val destinationLat: Double? = null,
    val destinationLng: Double? = null,
    val departure: String = "",
    val destination: String = "",
    val capacity: Int = 0,
    val currentMembers: Int = 0,
    val departureRadius: Int = 0,
    val destinationRadius: Int = 0,
    val status: String = "",
    val createdAt: String? = null,
    val estimateFare: Int? = null,
    val estimateTime: Int? = null,
    val route: String? = null,
    val taxiDriverId: Long? = null,
)

// GET /api/v1/matching/rooms/{partyId}/driver — 백엔드 driver/api/DriverSummary
// 기사 이름·별점 필드는 서버에 없다.
@Serializable
data class DriverSummaryResponse(
    val seats: Int? = null,
    val plateNumber: String = "",
    /** 차종. 서버 필드명이 type 이다. */
    val type: String = "",
)

// POST /api/v1/matching/routes — 백엔드 RouteRequest / RouteEstimate
// 컨트롤러가 도메인 record RouteEstimate 를 그대로 반환한다(RoutePreviewResponse 와 필드명 동일).
// Integer 필드라 null 이 올 수 있어 셋 다 nullable + 기본값으로 방어한다.
@Serializable
data class RouteRequestDto(
    val departureLat: Double,
    val departureLng: Double,
    val destinationLat: Double,
    val destinationLng: Double,
)

@Serializable
data class RouteEstimateResponse(
    val estimateFare: Int? = null,
    val estimateTime: Int? = null,
    /** Google Encoded Polyline. 서버 필드명이 path 다(방 상세의 route 와 같은 형식). */
    val path: String? = null,
)
