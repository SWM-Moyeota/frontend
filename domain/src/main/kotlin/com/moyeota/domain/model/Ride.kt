package com.moyeota.domain.model

/**
 * 방의 진행 단계. 서버 `PartyStatus` 의 네 값과 하나씩 짝이다 —
 * `ACTIVE`→[RECRUITING] · `COMPLETED`→[CONFIRMED] · `FINISHED`→[COMPLETED] · `CANCELED`→[CANCELED].
 *
 * ### ⚠️ 이 enum 의 어느 값도 「정원이 찼다」를 뜻하지 않는다 ★
 * 오래 그렇지 **않았다**. 서버 `COMPLETED` 는 「정원 충족」이었고 이 enum 의 그 자리는
 * `MATCHED` 라는 이름이었다. `b16551b7` 이 정의를 바꿨다:
 *
 * > COMPLETED 는 **방장이 약속을 확정한 실시간 방**만 뜻한다. 정원이 차도 ACTIVE 이고
 * > 참여만 PARTY_FULL 로 막는다.
 *
 * 그래서 이름을 [CONFIRMED] 로 바꿨다. 값을 그대로 두면 **옛 이름이 틀린 사실을 계속 주장**해,
 * 읽는 사람이 2/3 짜리 확정 방에 「다 모였어요」를 그리는 코드를 또 쓴다(실제로 그랬다).
 *
 * **정원 판정의 유일한 참은 `members.size >= capacity` 다.** 상태값으로는 알 수 없다.
 *
 * [CANCELED] 를 [COMPLETED] 와 갈라 두는 이유: 서버는 아무도 출발지에 도착하지 않은 방을 자동
 * 정리할 때 CANCELED 로 닫는다. 그걸 「완료」와 같은 값으로 접으면 **탄 적 없이 닫힌 방**을
 * 정상 종료와 구분할 수 없다. 정상 종료는 FINISHED 뿐이다.
 *
 * 택시 시절의 `DISPATCHING`·`ONGOING` 은 지웠다 — 기사·배차를 걷어내면서 서버가
 * `MATCHING`·`DRIVER_ASSIGNED`·`IN_RIDE` 를 **보낼 수 없게** 됐다(서버 `PartyStatus` 에서 삭제).
 * 모르는 문자열은 매퍼가 [RECRUITING] 으로 떨어뜨리므로 옛 값이 와도 파싱은 깨지지 않는다.
 */
enum class RideStatus {
    /**
     * 서버 `ACTIVE` — **모집 중**. 정원이 찬 방도 여기다(참여만 막힌다).
     * **예약 방은 출발 시각까지 계속 이 상태다** — 확정이지만 ACTIVE 다([Ride.isConfirmed] 참고).
     */
    RECRUITING,

    /**
     * 서버 `COMPLETED` — **실시간 방의 방장이 약속을 확정했다**. 옛 이름은 `MATCHED` 였다.
     *
     * 이 값은 [Ride.isConfirmed] 보다 **좁다**: 예약 방은 확정이지만 [RECRUITING] 이다.
     * 그래서 「확정됐나」를 묻는 자리는 이 값이 아니라 [Ride.isConfirmed] 를 봐야 한다 —
     * 이 값으로 물으면 예약 방이 전부 빠진다.
     */
    CONFIRMED,

    /** 서버 `FINISHED` — 출발을 눌러 끝난 방. 택시비 정산을 시작할 수 있는 유일한 상태다. */
    COMPLETED,

    /** 서버 `CANCELED` — 탄 적 없이 닫힌 방(모집 만료·전원 이탈·도착자 없는 자동 정리). */
    CANCELED,
}

/**
 * 방의 종류. 서버 `RoomType` 과 짝이지만 **이름이 한쪽만 다르다** — 서버는 `REALTIME`·`RESERVED` 이고
 * 여기는 [REALTIME]·[RESERVATION] 이다. 그 간극을 메우는 곳은 매퍼 하나뿐이다
 * (`PartyMappers.toRoomType` 이 `"RESERVED"` 를 [RESERVATION] 으로 읽는다).
 * **서버가 `"RESERVATION"` 을 보낸다고 가정하지 말 것** — 서버 enum 에 그 이름은 없다.
 * 매퍼는 혹시 모를 개명까지 받아 두지만, 지금 실제로 오는 문자열은 `RESERVED` 다.
 *
 * 모르는 문자열은 [REALTIME] 로 떨어진다. 반대 방향으로 기울이면 안 되는 이유는 **틀렸을 때의 대가가
 * 비대칭**이기 때문이다:
 *
 * | 모르는 값을 무엇으로 읽나 | 실시간 방을 틀리게 읽으면 | 예약 방을 틀리게 읽으면 |
 * |---|---|---|
 * | [REALTIME] | — | 방장에게 「약속 시간 정하기」가 뜨고, 누르면 서버가 409 `NOT_REALTIME_PARTY` 로 막는다(복구됨) |
 * | [RESERVATION] | 「약속 시간 정하기」가 **사라져** 실시간 방을 끝낼 길이 없어진다(복구 불가) | — |
 */
enum class RoomType {
    /**
     * 지금 모여서 타는 방. 약속 시간은 방장이 나중에 확정한다
     * ([com.moyeota.domain.repository.RideRepository.confirmMeeting]) — 확정 전에는 [Ride.departureAt] 이 null 이다.
     */
    REALTIME,

    /**
     * 만들 때 출발 시각을 정해 둔 예약 방(서버 `RESERVED`). **처음부터 확정 상태**라
     * ([Ride.isConfirmed] 가 늘 true) 약속 시간을 다시 정할 수 없다 — 서버가 409 `NOT_REALTIME_PARTY` 로 막는다.
     */
    RESERVATION,
}

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
    //
    // 방의 **방장 id** 필드는 없다 — 방장은 [members] 안에 [User.isHost] 로 표시된다
    // (서버 `MemberInfo.host`, Backend #240). 방 수준 필드로 올리지 않는 이유는 서버가 내부 PK 였던
    // `PartyDetailResult.hostId` 를 **지웠기** 때문이다: 밖으로 나가는 식별자는 publicId 뿐이고,
    // 그 publicId 는 이미 멤버 목록에 있다. 방 수준에 또 두면 「목록이 말하는 방장」과
    // 「방이 말하는 방장」이 어긋날 수 있는 두 번째 진실이 생긴다 —
    // 「내가 방장인가」는 [com.moyeota.domain.model.isHostedByMe] 하나로만 묻는다.
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
    /**
     * 약속 시간 — 서버 `PartyDetailResult.departureAt` 을 **ISO-8601 문자열 그대로** 들고 온다.
     * 실시간 방은 방장이 확정하기 전까지 null 이고, 예약 방은 만들 때 정한 출발 시각이 처음부터 들어 있다.
     *
     * **파싱해서 보관하지 않는다** — `RideRecord.createdAt`·`ChatMessage.sentAt` 과 같은 관례다. 도메인에서
     * `Instant` 로 바꾸면 ① data 와 domain 이 같은 파싱을 각자 들고 어긋날 수 있고 ② 「바뀌었나」를
     * [Ride] 끼리의 `==` 로 보는 주기 조회가 파싱 결과의 동등성에 묶인다. 사람이 읽을 모양
     * (「오후 6:30」)으로 바꾸는 일은 화면이 한다.
     *
     * 장소는 싣지 않는다 — 약속 장소는 **늘 방의 출발지**다(서버가 따로 담지 않는다).
     */
    val departureAt: String? = null,
    /**
     * 약속이 확정된 방인가(서버 `PartyDetailResult.confirmed`).
     *
     * 지금은 **[departureAt] 이 null 이 아닌 것과 같은 뜻**이다 — 서버도 저장하지 않고
     * `isReservation() || departureAt != null` 로 **셈해서** 내려 준다. 그래도 서버 값을 따로 받는 이유는
     * 그 식이 **서버 것**이기 때문이다: 확정 취소 같은 게 생겨 서버가 판정을 바꾸면 앱이 베낀 식만 뒤처진다.
     * (서버가 말해 주지 않은 옛 배포에서만 매퍼가 같은 식으로 셈해 채운다 — `PartyMappers.toRide`.)
     *
     * ### 「확정됐나」를 묻는 **단일 창구**가 이 값이다 — [RideStatus] 로 묻지 말 것 ★
     * 실시간 방이 확정되면 상태가 [RideStatus.CONFIRMED] 로 가지만 **예약 방은 확정인데도
     * [RideStatus.RECRUITING]** 이다(서버가 출발 시각까지 ACTIVE 로 둔다). 상태값으로 물으면
     * 예약 방이 통째로 빠져, 예약 방 사용자에게 도착·출발 버튼이 영원히 안 뜬다.
     *
     * 확정되면 바뀌는 것: 모집이 끝나 **참여가 막히고**(실시간 방은 ACTIVE→COMPLETED), 도착·출발이
     * 열린다. **나가기는 여전히 된다** — 확정을 이유로 막던 규칙은 서버에서 사라졌고(`b16551b7`),
     * 지금 나가기를 막는 건 **도착을 누른 사람**뿐이다(409 `ARRIVED_MEMBER_CANNOT_LEAVE`).
     * 끝내는 길은 **도착한 방장의 「출발」** 하나뿐이다
     * ([com.moyeota.domain.repository.RideRepository.departParty]).
     */
    val isConfirmed: Boolean = false,
    /**
     * 실시간 방인가 예약 방인가. 「약속 시간 정하기」는 **실시간 방에서만** 되고
     * ([com.moyeota.domain.repository.RideRepository.confirmMeeting]), 예약 방은 처음부터 확정이다.
     *
     * 목록·추천 응답에는 이 값이 없어 기본값 [RoomType.REALTIME] 으로 남는다 — 그쪽 화면은 방을
     * 끝내는 동작을 띄우지 않으므로 읽는 쪽이 없다.
     */
    val roomType: RoomType = RoomType.REALTIME,
)

/**
 * 내가 이 방의 방장인가 — 화면이 방장 전용 동작(**약속 확정 · 출발**)을 가릴 때 보는 **단일 창구**다.
 *
 * 멤버 목록에서 나를 찾아 그 한 명의 [User.isHost] 만 본다. 목록 첫째 원소나 입장 순서로 흉내 내지
 * 말 것 — 서버의 방장은 현재 멤버 중 `min(joinedAt, memberId)` 라는 **파생값**이라([User.isHost])
 * 앱이 같은 식을 다시 세우면 동시 입장의 tie-break·자동 승계에서 서버와 어긋난다.
 *
 * 내가 목록에 없으면(식별자 없는 목록·생성 응답, 요약 없는 멤버) false 다. **false 를 "방장이 따로
 * 있다"로 읽지 말 것** — "내가 방장이라고 말해 주는 근거가 없다" 가 전부다. 그리고 true 라도 서버가
 * 최종 판정이다: 눌렀을 때의 403 `NOT_PARTY_HOST` 는 이 값과 무관하게 **남는다**(방장은 멤버가 들고
 * 날 때마다 바뀌는데 그 사이 앱 상태가 낡을 수 있다).
 */
val Ride.isHostedByMe: Boolean
    get() = members.firstOrNull { it.isMe }?.isHost == true
// 방의 **반경**(서버 `departureRadius`/`destinationRadius`) 필드는 없다 — 백엔드가 방 생성 요청에서
// 반경을 빼고 서버 고정값(500m)으로 바꿨다(Backend `a668aee`). 응답에는 아직 실려 오지만
// (`PartyDetailResponse`) 그건 **사용자가 정한 값이 아니고**, 서버의 어떤 조회·판정도 읽지 않는다 —
// 합류에 거리 검사가 없고 목록은 지도 뷰포트만 본다. 도메인에 태우면 21 대기 화면의 「탐색 반경」처럼
// 아무것도 걸러내지 않는 숫자를 사용자가 고른 조건으로 보여 주게 된다. 경로 매칭이 실제로 이 값을
// 쓰기 시작하면 그때 다시 태운다.
//
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
    /**
     * 합류 **직후**의 방 상태.
     *
     * ### ⚠️ 이 값으로 「내가 마지막 자리를 채웠는지」를 알 수 없다 ★
     * 오래 알 수 있었다. 마지막 자리를 채우면 서버가 그 자리에서 `COMPLETED` 로 넘겼고, 이 KDoc 도
     * 「내가 마지막 자리를 채웠으면 MATCHED 로 온다」고 적혀 있었다. `b16551b7` 이 그걸 바꿨다 —
     * **정원이 차도 ACTIVE** 이고(참여만 `PARTY_FULL` 로 막는다) `COMPLETED` 는 「방장이 약속을
     * 확정한 실시간 방」만 뜻한다. 그래서 합류 응답에 `COMPLETED` 가 올 일이 **없다**(실측:
     * 2/2 를 채운 합류도 `{"status":"ACTIVE","currentMembers":2}` 였다).
     *
     * 정원이 찼는지가 필요하면 [currentMembers] 와 **그 방의 정원을 직접 비교**해야 한다.
     * 이 응답에는 정원이 없으므로, 호출부가 들고 있던 값(목록·추천 카드의 capacity)을 써야 한다 —
     * 상태값에서 꺼낼 길은 없다. 윗줄 [RideStatus] KDoc 이 같은 함정을 더 길게 적어 두었다.
     */
    val status: RideStatus,
    val currentMembers: Int,
)
