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

/**
 * 백엔드가 방장(host)을 더 이상 내려주지 않아, **가장 먼저 참여한 멤버 = 방 생성자**로 추정한다.
 * joinedAt 은 ISO-8601 UTC 문자열이라 사전순 비교가 곧 시간순 비교다.
 * joinedAt 이 전부 없으면 서버가 준 목록 순서의 첫 멤버를 쓴다.
 */
internal fun List<PartyDetailResponse.MemberInfo>.inferHost(): PartyDetailResponse.MemberInfo? =
    minWithOrNull(compareBy(nullsLast<String>()) { it.joinedAt })

fun PartyListResponse.PartyItem.toRide(): Ride = Ride(
    id = partyId.toString(),
    origin = departure,
    destination = destination,
    departureLabel = "",
    capacity = capacity,
    // 목록 응답에는 멤버 식별자가 없고 인원 수만 온다 — 자리 표시용 멤버를 만든다.
    members = List(currentMembers) { index ->
        User(id = "m$index", nickname = "멤버 ${index + 1}", verifiedLabel = "", rating = 0.0, rideCount = 0)
    },
    farePerPerson = 0,
    totalFare = 0,
    status = partyStatusToRideStatus(status),
    // 출발 좌표만 목록에 있다(지도 핀용). 도착 좌표·경로·요금은 상세에서 채운다.
    originLat = departureLat,
    originLng = departureLng,
)

fun PartyDetailResponse.toRide(): Ride {
    val host = members.inferHost()
    return Ride(
        id = id.toString(),
        origin = departure,
        destination = destination,
        departureLabel = "",
        capacity = capacity,
        members = members.map { member ->
            User(
                id = member.memberId.toString(),
                nickname = if (member.memberId == host?.memberId) "방장" else "멤버 ${member.memberId}",
                verifiedLabel = "",
                rating = 0.0,
                rideCount = 0,
            )
        },
        farePerPerson = farePerPerson(estimateFare, capacity),
        totalFare = estimateFare ?: 0,
        status = partyStatusToRideStatus(status),
        hostId = host?.memberId?.toString(),
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

// 생성 응답에는 members 목록도 생성자 id 도 없다(currentMembers 개수만 있음).
// 요청한 쪽이 방장이므로 [toRide] 에 creatorId 를 넘겨 멤버 1명과 hostId 를 구성한다.
fun OpenPartyResponse.toRide(creatorId: Long? = null): Ride = Ride(
    id = id.toString(),
    origin = departure,
    destination = destination,
    departureLabel = "",
    capacity = capacity,
    members = creatorId?.let {
        listOf(User(id = it.toString(), nickname = "방장", verifiedLabel = "", rating = 0.0, rideCount = 0))
    } ?: emptyList(),
    farePerPerson = farePerPerson(estimateFare, capacity),
    totalFare = estimateFare ?: 0,
    status = partyStatusToRideStatus(status),
    hostId = creatorId?.toString(),
    originLat = departureLat,
    originLng = departureLng,
    destinationLat = destinationLat,
    destinationLng = destinationLng,
    estimatedFare = estimateFare,
    estimatedMinutes = estimateTime,
    routePolyline = route,
    driverId = taxiDriverId,
)

// [NewParty.hostId] 는 여기서 **의도적으로 버려진다** — 방장은 서버가 토큰에서 정한다.
// 필드 자체는 하위호환으로 남아 있다(자세한 근거는 NewParty KDoc).
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
