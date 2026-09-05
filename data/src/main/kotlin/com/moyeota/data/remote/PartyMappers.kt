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

// 프로필(닉네임·별점·인증)은 아직 백엔드에 없어 기본값으로 채운다.
fun partyStatusToRideStatus(status: String): RideStatus = when (status) {
    "ACTIVE" -> RideStatus.RECRUITING
    "COMPLETED" -> RideStatus.MATCHED
    "MATCHING" -> RideStatus.DISPATCHING
    // 기사 배정 완료 = 배차 확정. 아직 탑승 전이라 배차 단계로 본다.
    "DRIVER_ASSIGNED" -> RideStatus.DISPATCHING
    "IN_RIDE" -> RideStatus.ONGOING
    "FINISHED", "CANCELED" -> RideStatus.COMPLETED
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

fun PartyDetailResponse.toRide(): Ride {
    return Ride(
        id = id.toString(),
        origin = departure,
        destination = destination,
        departureLabel = "",
        capacity = capacity,
        // 멤버는 전부 동등하다 — 서버가 방장을 구분하지 않는다.
        members = members.map { member ->
            User(
                id = member.memberId.toString(),
                nickname = "멤버 ${member.memberId}",
                verifiedLabel = "",
                rating = 0.0,
                rideCount = 0,
            )
        },
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
