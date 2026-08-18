package com.moyeota.data.repository

import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RideStatus
import com.moyeota.domain.model.User
import com.moyeota.domain.repository.RideRepository

// 서버 연동 전 더미 데이터. 와이어프레임 예시 값과 동일하게 유지한다.
class DummyRideRepository : RideRepository {

    private val me = User(id = "u0", nickname = "성윤", verifiedLabel = "성결대 인증", rating = 4.9, rideCount = 12)
    private val minji = User(id = "u1", nickname = "민지", verifiedLabel = "성결대 인증", rating = 4.8, rideCount = 21)
    private val junho = User(id = "u2", nickname = "준호", verifiedLabel = "성결대 인증", rating = 4.7, rideCount = 8)

    override fun getNearbyParties(): List<Ride> = listOf(
        Ride(
            id = "r1",
            origin = "성결대 정문",
            destination = "안양역",
            departureLabel = "지금 출발",
            capacity = 3,
            members = listOf(minji),
            farePerPerson = 3_200,
            totalFare = 9_600,
            status = RideStatus.RECRUITING,
        ),
        Ride(
            id = "r2",
            origin = "성결대 후문",
            destination = "범계역",
            departureLabel = "18:30",
            capacity = 4,
            members = listOf(minji, junho),
            farePerPerson = 3_600,
            totalFare = 10_800,
            status = RideStatus.RECRUITING,
        ),
    )

    override suspend fun getParties(): List<Ride> = getNearbyParties()

    override suspend fun getPartyDetail(partyId: Long): Ride = getNearbyParties().first()

    override fun getMyRides(): List<Ride> = listOf(
        Ride(
            id = "r3",
            origin = "안양역",
            destination = "성결대 정문",
            departureLabel = "내일 08:40",
            capacity = 3,
            members = listOf(me, minji),
            farePerPerson = 3_200,
            totalFare = 9_600,
            status = RideStatus.MATCHED,
        ),
    )
}
