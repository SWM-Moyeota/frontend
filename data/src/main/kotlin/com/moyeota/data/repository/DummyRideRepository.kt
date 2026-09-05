package com.moyeota.data.repository

import com.moyeota.domain.model.AssignedDriver
import com.moyeota.domain.model.NewParty
import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RideStatus
import com.moyeota.domain.model.RouteEstimate
import com.moyeota.domain.model.User
import com.moyeota.domain.repository.RideRepository
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

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
            originLat = 37.3789,
            originLng = 126.9268,
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
            originLat = 37.3812,
            originLng = 126.9301,
        ),
    )

    override suspend fun getParties(): List<Ride> = getNearbyParties()

    // 서버 없이도 지도 화면이 그럴듯하게 동작하도록 출발 좌표로 직접 걸러낸다.
    override suspend fun getPartiesWithin(
        swLat: Double,
        swLng: Double,
        neLat: Double,
        neLng: Double,
    ): List<Ride> = getNearbyParties().filter { ride ->
        val lat = ride.originLat ?: return@filter false
        val lng = ride.originLng ?: return@filter false
        lat in swLat..neLat && lng in swLng..neLng
    }

    override suspend fun getPartyDetail(partyId: Long): Ride = getNearbyParties().first()

    // 액션은 더미에서 성공한 척만 한다. 프리뷰/오프라인 확인용이다.
    override suspend fun createParty(request: NewParty): Ride = Ride(
        id = "dummy-party",
        origin = request.departure,
        destination = request.destination,
        departureLabel = "지금 출발",
        capacity = request.capacity,
        members = listOf(me),
        farePerPerson = 0,
        totalFare = 0,
        status = RideStatus.RECRUITING,
        originLat = request.departureLat,
        originLng = request.departureLng,
        destinationLat = request.destinationLat,
        destinationLng = request.destinationLng,
    )

    override suspend fun joinParty(partyId: Long): Ride =
        getNearbyParties().first().copy(id = partyId.toString(), status = RideStatus.MATCHED)

    override suspend fun leaveParty(partyId: Long) = Unit

    override suspend fun getAssignedDriver(partyId: Long): AssignedDriver =
        AssignedDriver(seats = 4, plateNumber = "12가 3456", vehicleType = "쏘나타")

    /**
     * 직선거리 기준으로 요금·시간을 지어낸다(서울 택시 기본요금 4,800원 + km 당 1,300원,
     * 평균 시속 22km + 승하차 3분). 경로는 출발·도착을 잇는 2점짜리 인코딩 폴리라인이라
     * 지도에 직선이 그려진다 — 상수 빈 문자열보다 화면 확인에 쓸모 있다.
     */
    override suspend fun previewRoute(
        departureLat: Double,
        departureLng: Double,
        destinationLat: Double,
        destinationLng: Double,
    ): RouteEstimate {
        val km = straightLineKm(departureLat, departureLng, destinationLat, destinationLng)
        return RouteEstimate(
            estimatedFare = (4_800 + km * 1_300).roundToInt(),
            estimatedMinutes = (km / 22.0 * 60).roundToInt() + 3,
            encodedPath = encodePolyline(
                listOf(departureLat to departureLng, destinationLat to destinationLng),
            ),
        )
    }

    private fun straightLineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return earthRadiusKm * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    // Google Encoded Polyline Algorithm Format (precision 5) — 서버 path/route 와 같은 형식.
    private fun encodePolyline(points: List<Pair<Double, Double>>): String {
        val out = StringBuilder()
        var prevLat = 0
        var prevLng = 0
        points.forEach { (lat, lng) ->
            val e5Lat = (lat * 1e5).roundToInt()
            val e5Lng = (lng * 1e5).roundToInt()
            encodeValue(e5Lat - prevLat, out)
            encodeValue(e5Lng - prevLng, out)
            prevLat = e5Lat
            prevLng = e5Lng
        }
        return out.toString()
    }

    private fun encodeValue(value: Int, out: StringBuilder) {
        var v = if (value < 0) (value shl 1).inv() else value shl 1
        while (v >= 0x20) {
            out.append(((0x20 or (v and 0x1f)) + 63).toChar())
            v = v shr 5
        }
        out.append((v + 63).toChar())
    }

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
