package com.moyeota.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * `POST /api/v1/matching/reservations` 의 요청 본문.
 * 백엔드 `OpenReservationRequest` 와 필드 1:1 (`matching/party/dto/OpenReservationRequest.java`).
 *
 * ### 좌표 필드명이 **전체 이름**이다 — `destLat` 이 아니다 ★
 * 이 레포에는 세 가지 이름이 공존한다. 베끼는 순서를 틀리면 조용히 (0,0) 이 가거나 파라미터가
 * 빠진다:
 *
 * | 자리 | 도착지 필드명 |
 * |---|---|
 * | **예약 생성 본문**(이 DTO) · 방 생성 본문([OpenPartyRequestDto]) · 경로 미리보기([RouteRequestDto]) | `destinationLat` / `destinationLng` |
 * | 실시간 후보 본문([RealTimeMatchingRequestDto]) | `destLat` / `destLng` |
 * | 예약 **검색** 쿼리([com.moyeota.data.remote.MatchingApi.searchReservations]) | `destLat` / `destLng` (출발지도 `depLat`/`depLng` 로 줄어든다) |
 *
 * 여기서 `destLat` 으로 적으면 서버 record 의 `destinationLat` 이 **`@NotNull` 위반**이 되어
 * 400 `INVALID_REQUEST` 다. 실시간 후보 쪽은 같은 실수가 전부 primitive 라 0.0 으로 메워져
 * 400 `OUT_OF_SERVICE_AREA` 로 보였는데(그 DTO KDoc), 이쪽은 박스 타입 + `@NotNull` 이라
 * **다른 400** 으로 떨어진다 — 사유가 다르니 증상으로 원인을 추측하지 말고 이 표를 볼 것.
 *
 * [capacity] 는 **2~3** 이다(실시간은 1~3). 하한을 어기면 `INVALID_CAPACITY` 가 아니라
 * **400 `INVALID_REQUEST`** 인데, 그 사정은
 * [com.moyeota.domain.model.PartyOpenException.isInvalidCapacity] 에 적어 두었다.
 *
 * [departureAt] 은 ISO-8601 **순간**이어야 한다 — 서버 record 가 `Instant` 라 오프셋 없는 로컬
 * 시각은 역직렬화에서 터져 400 `INVALID_REQUEST` 가 된다([ConfirmMeetingRequestDto] 와 같은 함정).
 *
 * 생성자 id 와 반경은 **없다** — [OpenPartyRequestDto] 와 같은 이유다(토큰이 정하고, 서버가
 * 고정값을 쓴다).
 */
@Serializable
data class OpenReservationRequestDto(
    val departureLat: Double,
    val departureLng: Double,
    /** 서버 record 필드명 그대로. **`destLat` 이 아니다** — 위 표 참고. */
    val destinationLat: Double,
    /** 서버 record 필드명 그대로. **`destLng` 이 아니다** — 위 표 참고. */
    val destinationLng: Double,
    val departure: String,
    val destination: String,
    /** 정원(생성자 포함) **2~3**. 실시간 방의 1~3 과 다르다. */
    val capacity: Int,
    /** 출발 시각(ISO-8601 instant). 지금 뒤 ~ 7일 안, 양쪽 모두 엄격하다. */
    val departureAt: String,
)

/**
 * 예약 목록·검색의 200 응답. 백엔드 record **둘**을 하나로 받는다:
 * `ReservationListResponse(List<ReservationItem> list)` 와
 * `ReservationSearchResponse(List<ReservationSearchItem> list)`.
 *
 * ### 엔벨로프 키가 `list` 다 — 노쇼 목록만 `noShows` 다
 * 세 예약 엔드포인트가 모두 `list` 를 쓴다(방 목록 [PartyListResponse] 와 같다). 같은 매칭 도메인에서
 * [MyNoShowListResponse] 하나만 `noShows` 이므로 서로 베끼지 말 것 — 틀리면 에러 없이
 * **항상 빈 목록**이다.
 *
 * ### 서버 record 가 둘인데 DTO 를 하나로 두는 이유 ★
 * 두 record 는 **같은 11개 필드가 같은 순서로** 있고, 검색 쪽에만 `destinationNearby` 가 12번째로
 * 붙는다. 즉 검색 항목은 목록 항목의 **상위 집합**이다. 그래서 `destinationNearby` 를
 * nullable 로 둔 DTO 하나면 세 엔드포인트가 전부 파싱된다 — 목록 두 개는 그 키가 **아예 없어**
 * 기본값 null 로 떨어지고, 그 null 이 곧 「물어보지 않았다」라는 **정확한 뜻**이다
 * ([com.moyeota.domain.model.ReservationSearchResult.destinationNearby]).
 *
 * DTO 를 둘로 쪼개면 같은 매퍼를 두 벌 들고, 그중 한쪽만 서버 변경을 따라가게 된다 —
 * 상세와 SSE 스냅샷이 DTO·매퍼를 공유하는 것과 같은 판단이다([PartyDetailResponse]).
 */
@Serializable
data class ReservationListResponse(
    val list: List<ReservationItemDto> = emptyList(),
)

/**
 * 예약 방 한 건(목록·곧 출발·검색 공통). 백엔드 `ReservationItem` / `ReservationSearchItem` 과
 * 필드 1:1.
 *
 * ### 방 id 키가 `partyId` 다 — 생성 응답만 `id` 다
 * [OpenPartyResponse] 는 `id`, 이 목록은 `partyId` 다([PartyListResponse.PartyItem] 과 같다).
 * 실시간 후보([MatchCandidateResponse.NearbyPartySummaryDto])는 또 `id` 다 — 세 이름이 다르므로
 * 매퍼를 베낄 때 가장 먼저 어긋나는 자리다. 틀리면 기본값 0 이 되고, 매퍼가 그 항목을 **버린다**
 * (id 없는 카드는 탭해도 갈 곳이 없다).
 *
 * ### `roomType` 필드가 **없다** — 서버가 보내지 않는다 ★
 * 세 예약 응답 record 에는 `roomType` 컴포넌트가 아예 없다(생성 응답 [OpenPartyResponse] 에만 있다).
 * 그래서 「이 방이 예약 방인가」는 **엔드포인트가 증거**이고, 그 단정을 매퍼가 한다
 * ([com.moyeota.data.remote.toRide]). 여기에 `roomType: String?` 자리를 만들어 두면 늘 null 이
 * 들어오고, `toRoomType(null)` 은 설계상 [com.moyeota.domain.model.RoomType.REALTIME] 이라
 * **예약 방이 전부 실시간 방으로 읽힌다** — 그러면 화면이 「약속 시간 정하기」를 띄우고 서버가
 * 409 `NOT_REALTIME_PARTY` 로 막는다. 받는 자리를 만들지 않는 것이 그 실수를 막는다.
 *
 * ### `status` 는 늘 `"ACTIVE"` 다
 * 세 쿼리가 모두 `status = 'ACTIVE'` 로 못 박혀 있다. 그래도 문자열로 받아
 * [com.moyeota.data.remote.partyStatusToRideStatus] 를 거치는 건 **표를 한 벌로 유지하기**
 * 위해서다 — 고정값으로 두면 서버가 필터를 넓힐 때 목록만 뒤처진다.
 *
 * [currentMembers] 를 nullable 로 받는 근거는 [PartyListResponse.PartyItem.currentMembers] 와
 * 같다 — 「0」과 「모른다」가 다른 뜻이고, non-null 선언은 명시적 null 에서 **목록 전체의 파싱을
 * 터뜨린다**. (서버 쪽은 SQL `count` 를 `.intValue()` 로 좁혀 담으므로 실제로는 늘 1 이상이다.)
 *
 * [departureAt] 은 예약 방의 **약속 시각**이고 늘 있다(예약은 만들 때 정한다). 그래도 nullable 인
 * 이유는 컬럼이 공용이라서다 — 같은 테이블을 확정 전 실시간 방이 null 로 쓴다.
 * ⚠️ 소수점 아래가 붙을 수 있다(`timestamp(6)`) — 화면 파싱은 `Instant.parse` 를 쓸 것.
 */
@Serializable
data class ReservationItemDto(
    val partyId: Long = 0,
    val departure: String = "",
    val destination: String = "",
    val currentMembers: Int? = null,
    val capacity: Int = 0,
    val status: String = "",
    val departureLat: Double? = null,
    val departureLng: Double? = null,
    /** 목록에도 **도착 좌표가 온다** — 방 목록([PartyListResponse.PartyItem])과 다른 점이다. */
    val destinationLat: Double? = null,
    val destinationLng: Double? = null,
    val departureAt: String? = null,
    /**
     * 이 방의 도착지도 내가 보낸 도착지 1km 안인가. **검색 응답에만, 도착지를 보냈을 때만** 있다.
     *
     * 목록·곧 출발 응답에는 키가 **아예 없고**, 도착지 없는 검색에는 키는 있고 값이 null 이다 —
     * 둘 다 여기서 null 로 모인다. 그 null 을 false 로 접지 말 것: 「도착지가 멀다」와
     * 「물어보지 않았다」는 다른 사실이고, 접으면 도착지 없는 검색 결과 전체에 틀린 배지가 붙는다.
     */
    val destinationNearby: Boolean? = null,
)
