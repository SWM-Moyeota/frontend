package com.moyeota.data.remote

import com.moyeota.data.remote.dto.PartyDetailResponse
import com.moyeota.data.remote.dto.PartyListResponse
import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RideStatus
import com.moyeota.domain.model.User

// 요금·프로필은 아직 백엔드에 없어 기본값으로 채운다.
fun partyStatusToRideStatus(status: String): RideStatus = when (status) {
    "ACTIVE" -> RideStatus.RECRUITING
    "COMPLETED" -> RideStatus.MATCHED
    "MATCHING" -> RideStatus.DISPATCHING
    "FINISHED", "CANCELED" -> RideStatus.COMPLETED
    else -> RideStatus.RECRUITING
}

fun PartyListResponse.PartyItem.toRide(): Ride = Ride(
    id = partyId.toString(),
    origin = departure,
    destination = destination,
    departureLabel = "",
    capacity = capacity,
    members = List(currentMembers) { index ->
        User(id = "m$index", nickname = "멤버 ${index + 1}", verifiedLabel = "", rating = 0.0, rideCount = 0)
    },
    farePerPerson = 0,
    totalFare = 0,
    status = partyStatusToRideStatus(status),
)

fun PartyDetailResponse.toRide(): Ride = Ride(
    id = id.toString(),
    origin = departure,
    destination = destination,
    departureLabel = "",
    capacity = capacity,
    members = members.map { member ->
        User(
            id = member.memberId.toString(),
            nickname = if (member.isHost) "방장" else "멤버 ${member.memberId}",
            verifiedLabel = "",
            rating = 0.0,
            rideCount = 0,
        )
    },
    farePerPerson = 0,
    totalFare = 0,
    status = partyStatusToRideStatus(status),
)
