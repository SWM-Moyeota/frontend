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
// 방의 **지문**(서버 `fingerprint`) 필드는 없다 — 백엔드가 지웠다(Backend `04f6be7`).
// 지문은 「가벼운 상태 요약이 내가 그린 상세와 같은가」를 비교하려고 만든 값이고, 그 비교 대상인
// 상태 조회 API(`GET /matching/rooms/{id}/status`)가 함께 사라지면서 비교할 짝을 잃었다.
// 지금은 주기 조회가 곧 상세([com.moyeota.domain.repository.RideRepository.getPartyDetail])이므로
// 「바뀌었나」는 받은 Ride 와 들고 있던 Ride 의 `==` 로 직접 본다 — 요약으로는 못 잡던
// 「인원수 같은 멤버 교체」까지 잡히니 지문보다 오히려 정확하다. 되살리면 쓰이지 않는 필드가 된다.

/**
 * 합류 직후의 방 상태(서버 `POST /matching/rooms/{partyId}/join` 응답 `PartyStatusResponse`).
 *
 * 이름이 「상태」인 건 **합류 응답이 방 상세가 아니라 이 두 값뿐**이기 때문이다(Backend #213) —
 * 동승자 목록이 오지 않으므로, 필요하면 상세를 따로 읽거나 21 대기 화면의 SSE 첫 스냅샷에 맡긴다
 * ([com.moyeota.domain.repository.RideRepository.joinParty]).
 *
 * 쓰임은 **합류 응답 하나뿐**이다. 예전에는 폴링용 상태 조회(`/status`)도 같은 모양이었지만
 * 그 엔드포인트는 사라졌다(Backend `04f6be7`). 그래서 지문 필드도, 상세와 비교하던 `isSameAs` 도 없다.
 */
data class PartyStatus(
    /** 합류 **직후**의 상태. 내가 마지막 자리를 채웠으면 COMPLETED(기사 모드면 MATCHING)로 온다. */
    val status: RideStatus,
    val currentMembers: Int,
)
