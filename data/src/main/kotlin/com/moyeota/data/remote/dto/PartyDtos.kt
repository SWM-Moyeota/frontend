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

// PartyDetailResult — GET /matching/rooms/{partyId} 의 응답, 그리고 SSE `connected` 이벤트의 data.
//
// **합류(POST .../join)는 더 이상 이 모양을 주지 않는다**(Backend #213) — 상태·인원만 담은
// [PartyStatusResponse] 로 바뀌었다. 합류 직후 동승자 목록이 필요하면 상세를 따로 읽어야 한다.
//
// **`fingerprint` 필드는 없다** — 백엔드가 지웠다(`04f6be7`). 지문은 「상태 요약이 내가 그린 상세와
// 같은가」를 비교하려고 만든 값인데, 그 짝인 상태 조회 API 가 함께 사라졌다. 25·26 의 주기 조회가
// 이제 곧 이 상세이므로 「바뀌었나」는 도메인 Ride 끼리 `==` 로 본다.
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
    /**
     * 서버가 방에 박아 둔 반경(m). **사용자가 고른 값이 아니다** — 요청에서 빠지고 서버 고정값
     * (`Radius.DEFAULT` = 500m)이 됐다(Backend `a668aee`). 응답에는 남아 있어 서버 record 와 모양을
     * 맞추려고 받아 두지만, **도메인 [com.moyeota.domain.model.Ride] 로는 옮기지 않는다.**
     *
     * 옮기지 않는 이유: 서버의 어떤 조회·판정도 이 값을 읽지 않는다 — 합류(`party.join`)에 거리 검사가
     * 없고 목록은 지도 뷰포트만 본다. 화면에 띄우면 아무것도 걸러내지 않는 숫자를 「탐색 범위」로
     * 약속하는 셈이고, 사용자가 바꿀 수도 없다. 경로 매칭이 실제로 이 값을 쓰기 시작하면 그때 태운다.
     */
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
     * String badgeId, Integer rideCount)`.
     *
     * **내부 PK 였던 `memberId` 는 사라졌다.** 식별자는 [publicId] 하나이며 JWT `sub` 와 같은 값이라
     * "이 멤버가 나인가"를 앱이 직접 판정할 수 있다([com.moyeota.data.remote.toRide] 의 currentUuid).
     *
     * **`joinedAt` 도 사라졌다**(Backend #218) — 서버 record 에 필드 자체가 없으니 입장 시각을
     * 화면에 쓸 근거도 없다. 되살리면 항상 null 인 필드로 "최초 입장자 = 방장" 같은 추정이 되살아난다.
     *
     * **네 문자열 필드가 전부 nullable 인 건 방어가 아니라 서버 계약이다** — 탈퇴 등으로 유저 요약을
     * 못 찾으면 서버가 `MemberInfo(null, null, null, null, rideCount)` 을 그대로 내려보낸다
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
    )
}

/**
 * SSE `member-joined` 이벤트의 data — 백엔드 `MemberJoined(PartyDetailResult.MemberInfo member, String status)`.
 *
 * [member] 가 방 상세의 멤버와 **같은 record** 라 DTO 도 같은 타입을 그대로 쓴다 — 같은 모양을 두 번
 * 선언하면 한쪽만 서버 변경을 따라가다 어긋난다. 변환도 상세와 같은 매퍼(`toUser`)를 재사용한다.
 *
 * [member] 에 기본값을 준 건 서버가 비워 보낼 수 있다고 보기 때문이 아니라, 키가 빠진 프레임 하나가
 * 스트림을 죽이지 않게 하려는 것이다 — 그때는 요약 없는 멤버("탈퇴한 회원")로 떨어진다.
 */
@Serializable
data class MemberJoinedPayload(
    val member: PartyDetailResponse.MemberInfo = PartyDetailResponse.MemberInfo(),
    /** 들어온 **뒤**의 방 상태(서버 `PartyStatus` enum 이름). 정원이 차면 여기서 전이가 보인다. */
    val status: String = "",
)

/**
 * SSE `member-left` 이벤트의 data — 백엔드 `MemberLeft(UUID publicId, String status)`.
 *
 * [publicId] 는 **null 일 수 있다** — 서버가 `userAccess.findMemberSummaries` 로 요약을 못 찾으면
 * (탈퇴 등) null 을 그대로 싣고도 신호는 보낸다(`PartyService.leave`). 받는 쪽은 "누가 나갔는지 모름"으로
 * 다뤄야 한다. 논-널로 선언하면 그 프레임에서 파싱이 터진다.
 */
@Serializable
data class MemberLeftPayload(
    val publicId: String? = null,
    /** 나간 **뒤**의 방 상태. 마지막 멤버가 나가면 방은 닫히고 `member-left` 대신 `closed` 가 온다. */
    val status: String = "",
)

/**
 * **POST /api/v1/matching/rooms/{partyId}/join 응답** — 백엔드 `PartyStatusResponse(String status,
 * Integer currentMembers)`. 합류 응답에 동승자 목록이 없으므로(Backend #213), 목록이 필요하면
 * 상세([PartyDetailResponse])를 따로 읽거나 21 대기 화면의 SSE 첫 스냅샷에 맡긴다.
 *
 * **쓰임은 합류 응답 하나뿐이다.** 예전에는 폴링용 상태 조회 `GET /rooms/{partyId}/status` 도 같은
 * 모양이었지만 그 엔드포인트는 백엔드에서 삭제됐다(`04f6be7`) — 매칭방 변화가 SSE 전용이 되면서
 * 폴링용으로 만든 조회와 지문이 쓰이지 않게 됐다. 25·26 의 주기 조회는 상세를 읽는다.
 *
 * **`fingerprint` 필드도 같은 커밋에서 빠졌다.** 되살리면 비교할 짝(상세의 지문)이 없는 죽은 필드가
 * 된다. 서버가 아직 옛 버전이라 지문을 실어 보내도 디코더의 `ignoreUnknownKeys` 가 흘린다.
 *
 * 두 필드 모두 서버 record 가 박싱 타입(String/Integer)이라 기본값으로 방어한다.
 */
@Serializable
data class PartyStatusResponse(
    /** 서버 `PartyStatus` enum 이름(ACTIVE/COMPLETED/MATCHING/DRIVER_ASSIGNED/IN_RIDE/FINISHED/CANCELED). */
    val status: String = "",
    val currentMembers: Int = 0,
)

/**
 * POST /api/v1/matching/rooms 요청 본문 (백엔드 `OpenPartyRequest`).
 *
 * **생성자 id 필드는 없다.** 서버 record 에 `creatorId` 가 구버전 호환으로 남아 있지만
 * `toCommand()` 가 그 값을 버리고 `@CurrentUser` 로 받은 토큰 주체를 쓴다 — 보내 봐야 무시되는
 * 값이라 아예 싣지 않는다. 되살리면 "앱이 보낸 생성자"가 유효하다는 착각을 부른다.
 *
 * **반경 필드도 없다**(Backend `a668aee`). `departureRadius`·`destinationRadius` 가 서버 record 에서
 * 빠지고 서버가 고정값(`Radius.DEFAULT` = 500m)을 쓴다. 계속 보내도 서버는 모르는 키로 흘려 버리므로
 * (Jackson 기본 동작) 되살리면 **사용자가 고른 값이 조용히 버려지는** 상태로 돌아간다.
 * 응답에는 반경이 남아 있지만 그건 서버가 정한 값이다([PartyDetailResponse.departureRadius]).
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
    /** 서버 고정값(500m). 요청에 없는 값이고 도메인으로도 옮기지 않는다 — [PartyDetailResponse.departureRadius] 참고. */
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
