package com.moyeota.domain.model

enum class RideStatus { RECRUITING, MATCHED, DISPATCHING, ONGOING, COMPLETED }

data class Ride(
    val id: String,
    val origin: String,
    val destination: String,
    val departureLabel: String, // 예: "지금 출발", "18:30"
    val capacity: Int,
    val members: List<User>,
    val farePerPerson: Int,
    val totalFare: Int,
    val status: RideStatus,
    // 아래는 서버 연동으로 채워지는 값. 더미/목록 응답에는 없어 기본값을 둔다.
    // hostId 는 "내가 방장인가" 판단에 쓴다. 백엔드에 host 개념이 사라져(PartyDetailResult 에
    // hostId/isHost 필드 없음) 가장 먼저 참여한(joinedAt 최소) 멤버 = 방 생성자로 추정한 값이다.
    val hostId: String? = null,
    val originLat: Double? = null,
    val originLng: Double? = null,
    val destinationLat: Double? = null,
    val destinationLng: Double? = null,
    // 서버가 방 생성 시점에 네이버 경로 API 로 산출해 방에 박아두는 값 (상세 응답에만 있음).
    /** 경로 전체 택시 요금(원). 서버 estimateFare. */
    val estimatedFare: Int? = null,
    /** 예상 소요 시간(분). 서버 estimateTime. */
    val estimatedMinutes: Int? = null,
    /** Google Encoded Polyline (정밀도 1e5, lat→lng 순). 서버 route. 지도 경로 그리기에 쓴다. */
    val routePolyline: String? = null,
    /** 배정된 기사 id. 미배정이면 null. 서버 taxiDriverId. */
    val driverId: Long? = null,
)
