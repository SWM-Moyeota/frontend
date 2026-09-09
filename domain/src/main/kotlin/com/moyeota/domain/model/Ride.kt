package com.moyeota.domain.model

/**
 * 방의 진행 단계.
 *
 * [CANCELED] 는 [COMPLETED] 와 갈라 둔다 — 서버는 **기사 매칭 3분 타임아웃**에도 방을 CANCELED 로
 * 바꾸는데, 그걸 「완료」와 같은 값으로 접으면 앱이 「기사님을 찾지 못했어요」를 정상 종료와
 * 구분할 수 없다. 정상 종료는 FINISHED 뿐이다.
 *
 * [DISPATCHING] 은 서버의 MATCHING(기사 찾는 중)과 DRIVER_ASSIGNED(배정 완료)를 함께 담는다 —
 * 둘의 구분은 [Ride.driverId] 유무로 한다(배정되면 taxiDriverId 가 채워진다).
 */
enum class RideStatus { RECRUITING, MATCHED, DISPATCHING, ONGOING, COMPLETED, CANCELED }

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
    // 방장(host) 필드는 없다 — 백엔드가 자동 기사 매칭으로 바뀌며 방장 개념 자체를 없앴다
    // (PartyDetailResult 에 host 관련 필드 없음, 방 생성자도 토큰 주체로만 기록된다).
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
    /**
     * 방 생성 시 정해진 탐색 반경(m). 서버 departureRadius / destinationRadius.
     *
     * 목록 응답에는 없어 null 이다 — 21 대기 화면이 「탐색 반경」을 하드코딩("1km")하지 않고
     * 실제 방 값으로 보여 주기 위해 상세·생성 응답에서만 채운다(QA D-3).
     * 서버가 0 을 내려보내면(값 없음) null 로 접는다 — 0m 반경은 의미가 없다.
     */
    val departureRadiusMeters: Int? = null,
    val destinationRadiusMeters: Int? = null,
)
