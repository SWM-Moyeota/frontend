package com.moyeota.data.remote

import com.moyeota.data.remote.dto.DriverSummaryResponse
import com.moyeota.data.remote.dto.OpenPartyRequestDto
import com.moyeota.data.remote.dto.OpenPartyResponse
import com.moyeota.data.remote.dto.PartyDetailResponse
import com.moyeota.data.remote.dto.PartyListResponse
import com.moyeota.data.remote.dto.RouteEstimateResponse
import com.moyeota.data.remote.dto.RouteRequestDto
import com.moyeota.domain.model.AssignedDriver
import com.moyeota.domain.model.NewParty
import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RideStatus
import com.moyeota.domain.model.RouteEstimate
import com.moyeota.domain.model.User

// 방 상세의 멤버는 이제 닉네임·프로필 이미지·탑승 횟수까지 서버가 준다(PartyDetailResult.MemberInfo).
// 다만 별점과 인증 배지는 여전히 서버에 값이 없어 0.0 / "" 로 채운다 — 지어내지 않는다.
fun partyStatusToRideStatus(status: String): RideStatus = when (status) {
    "ACTIVE" -> RideStatus.RECRUITING
    "COMPLETED" -> RideStatus.MATCHED
    "MATCHING" -> RideStatus.DISPATCHING
    // 기사 배정 완료 = 배차 확정. 아직 탑승 전이라 배차 단계로 본다.
    "DRIVER_ASSIGNED" -> RideStatus.DISPATCHING
    "IN_RIDE" -> RideStatus.ONGOING
    "FINISHED" -> RideStatus.COMPLETED
    // 정상 종료(FINISHED)와 갈라 둔다 — 기사 매칭 3분 타임아웃도 여기로 떨어지는데,
    // 25b 가 「기사님을 찾지 못했어요」를 띄우려면 완료와 구분돼야 한다.
    "CANCELED" -> RideStatus.CANCELED
    else -> RideStatus.RECRUITING
}

/**
 * 정원으로 나눈 1인당 요금. [estimateFare] 는 경로 전체의 택시 요금이다.
 * 정원이 0 이하이거나 요금이 없으면 0 을 돌려준다(화면이 "-" 로 처리).
 */
internal fun farePerPerson(estimateFare: Int?, capacity: Int): Int =
    if (estimateFare == null || capacity <= 0) 0 else estimateFare / capacity

/** 멤버 식별자가 없는 응답(목록·생성)에서 인원 수만큼 자리 표시용 멤버를 만든다. */
private fun placeholderMembers(count: Int): List<User> = List(count.coerceAtLeast(0)) { index ->
    User(id = "m$index", nickname = "멤버 ${index + 1}", verifiedLabel = "", rating = 0.0, rideCount = 0)
}

/**
 * 방 상세의 멤버 한 명 → 표시용 [User].
 *
 * 유저 요약이 없으면(탈퇴 등) 서버가 [PartyDetailResponse.MemberInfo.publicId] 부터 전부 null 로 준다.
 * 그때 id 는 빈 문자열이 되고 닉네임은 "탈퇴한 회원"이다 — 화면은 빈 id 를 탭 불가로 다뤄야 한다.
 *
 * [currentUuid] 는 로그인한 사용자의 UUID(`UserSession.currentUserUuid`). publicId 가 JWT `sub` 와
 * 같은 값이라 이 비교가 곧 "나" 판정이다. 미로그인이거나 요약이 없으면 [User.isMe] 는 false 다
 * — 둘 다 null 인 경우까지 참이 되지 않도록 publicId 의 null 을 먼저 끊는다.
 *
 * 별점([User.rating])과 인증 라벨([User.verifiedLabel])은 서버에 값이 없어 0.0 / "" 로 고정한다.
 * 여기서 그럴듯한 값을 지어내면 화면이 가짜 평판을 진짜처럼 보여 준다.
 */
private fun PartyDetailResponse.MemberInfo.toUser(currentUuid: String?): User = User(
    id = publicId ?: "",
    nickname = nickname ?: "탈퇴한 회원",
    verifiedLabel = "",
    rating = 0.0,
    rideCount = rideCount,
    imageUrl = imageUrl,
    isMe = publicId != null && publicId == currentUuid,
)

fun PartyListResponse.PartyItem.toRide(): Ride = Ride(
    id = partyId.toString(),
    origin = departure,
    destination = destination,
    departureLabel = "",
    capacity = capacity,
    // 목록 응답에는 멤버 식별자가 없고 인원 수만 온다 — 자리 표시용 멤버를 만든다.
    members = placeholderMembers(currentMembers),
    farePerPerson = 0,
    totalFare = 0,
    status = partyStatusToRideStatus(status),
    // 출발 좌표만 목록에 있다(지도 핀용). 도착 좌표·경로·요금은 상세에서 채운다.
    originLat = departureLat,
    originLng = departureLng,
)

/**
 * [currentUuid] 를 넘기면 멤버 중 본인이 [User.isMe] 로 표시된다. 기본값 null 은 "모름" —
 * 아무도 나로 표시되지 않는다. 넘기는 쪽은 [com.moyeota.data.repository.RemoteRideRepository] 다.
 */
fun PartyDetailResponse.toRide(currentUuid: String? = null): Ride {
    return Ride(
        id = id.toString(),
        origin = departure,
        destination = destination,
        departureLabel = "",
        capacity = capacity,
        // 멤버는 전부 동등하다 — 서버가 방장을 구분하지 않는다.
        members = members.map { member -> member.toUser(currentUuid) },
        farePerPerson = farePerPerson(estimateFare, capacity),
        totalFare = estimateFare ?: 0,
        status = partyStatusToRideStatus(status),
        originLat = departureLat,
        originLng = departureLng,
        destinationLat = destinationLat,
        destinationLng = destinationLng,
        estimatedFare = estimateFare,
        estimatedMinutes = estimateTime,
        routePolyline = route,
        driverId = taxiDriverId,
        departureRadiusMeters = departureRadius.takeIf { it > 0 },
        destinationRadiusMeters = destinationRadius.takeIf { it > 0 },
    )
}

// 생성 응답에는 members 목록이 없고 currentMembers 개수만 있다(생성 직후엔 보통 1 = 생성자).
// 목록 응답과 같은 방식으로 자리 표시용 멤버를 만든다 — 식별자는 상세 조회에서 채워진다.
fun OpenPartyResponse.toRide(): Ride = Ride(
    id = id.toString(),
    origin = departure,
    destination = destination,
    departureLabel = "",
    capacity = capacity,
    members = placeholderMembers(currentMembers),
    farePerPerson = farePerPerson(estimateFare, capacity),
    totalFare = estimateFare ?: 0,
    status = partyStatusToRideStatus(status),
    originLat = departureLat,
    originLng = departureLng,
    destinationLat = destinationLat,
    destinationLng = destinationLng,
    estimatedFare = estimateFare,
    estimatedMinutes = estimateTime,
    routePolyline = route,
    driverId = taxiDriverId,
    departureRadiusMeters = departureRadius.takeIf { it > 0 },
    destinationRadiusMeters = destinationRadius.takeIf { it > 0 },
)

// 방장 id 는 요청에도 도메인 모델에도 없다 — 방 생성자는 서버가 토큰에서 정한다(NewParty KDoc 참고).
fun NewParty.toRequestDto(): OpenPartyRequestDto = OpenPartyRequestDto(
    departureLat = departureLat,
    departureLng = departureLng,
    destinationLat = destinationLat,
    destinationLng = destinationLng,
    departure = departure,
    destination = destination,
    capacity = capacity,
    departureRadius = departureRadius,
    destinationRadius = destinationRadius,
)

fun DriverSummaryResponse.toAssignedDriver(): AssignedDriver = AssignedDriver(
    seats = seats,
    plateNumber = plateNumber,
    vehicleType = type,
)

fun RouteEstimateResponse.toRouteEstimate(): RouteEstimate = RouteEstimate(
    estimatedFare = estimateFare ?: 0,
    estimatedMinutes = estimateTime ?: 0,
    encodedPath = path.orEmpty(),
)

fun routeRequestDto(
    departureLat: Double,
    departureLng: Double,
    destinationLat: Double,
    destinationLng: Double,
): RouteRequestDto = RouteRequestDto(
    departureLat = departureLat,
    departureLng = departureLng,
    destinationLat = destinationLat,
    destinationLng = destinationLng,
)
