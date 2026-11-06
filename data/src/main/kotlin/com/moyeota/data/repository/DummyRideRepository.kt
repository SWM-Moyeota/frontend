package com.moyeota.data.repository

import com.moyeota.domain.model.MatchCandidate
import com.moyeota.domain.model.NewParty
import com.moyeota.domain.model.NewReservation
import com.moyeota.domain.model.PartyDetail
import com.moyeota.domain.model.PartyStatus
import com.moyeota.domain.model.ReservationDestination
import com.moyeota.domain.model.ReservationSearchResult
import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RideStatus
import com.moyeota.domain.model.RoomType
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

    // 더미는 순번을 모른다 — null 이면 옛 서버와 같은 완화 규칙으로 떨어지므로 프리뷰가 멀쩡히 돈다.
    override suspend fun getPartyDetailWithVersion(partyId: Long): PartyDetail =
        PartyDetail(getPartyDetail(partyId), version = null)

    // 더미에는 도보 경로 엔진이 없다. **빈 목록이 정답이다** — 가짜 후보를 지어내면 프리뷰에서
    // 「걸어서 N분」이 실제로는 아무 근거 없는 숫자가 된다(도메인 더미를 지운 이유와 같다).
    override suspend fun findRealtimeCandidates(
        departureLat: Double,
        departureLng: Double,
        destinationLat: Double,
        destinationLng: Double,
    ): List<MatchCandidate> = emptyList()

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

    // 서버와 같은 모양으로 합류 직후 상태만 돌려준다 — 동승자 목록은 상세 조회가 맡는다.
    override suspend fun joinParty(partyId: Long): PartyStatus {
        val joined = getNearbyParties().first()
        return PartyStatus(
            status = RideStatus.MATCHED,
            currentMembers = joined.members.size,
        )
    }

    override suspend fun leaveParty(partyId: Long) = Unit

    override suspend fun finishParty(partyId: Long) = Unit

    // 약속 확정·도착·출발도 성공한 척만 한다. 더미는 방 상태를 들고 있지 않아 **다음 호출에 아무
    // 영향이 없다** — 확정한 뒤 getPartyDetail 을 읽어도 isConfirmed 는 여전히 false 다.
    // 그걸 흉내 내려면 더미가 방의 생애를 갖게 되는데, 그러면 「서버 없이 화면이 열리는가」만 보려는
    // 이 저장소가 두 번째 서버 구현이 된다(실패 경로는 흉내 낼 수 없으니 반쪽짜리다).
    // 확정/도착/출발이 실제로 어떤 모양인지는 FakeMatchingApi 를 쓰는 RemoteRideRepositoryTest 가 본다.
    override suspend fun confirmMeeting(partyId: Long, departureAt: String) = Unit

    override suspend fun arriveAtDeparture(partyId: Long, latitude: Double, longitude: Double) = Unit

    override suspend fun departParty(partyId: Long) = Unit

    /**
     * 예약 생성도 **성공한 척만 한다.** 돌려주는 방이 실시간 생성([createParty])과 다른 점은 둘이고
     * 둘 다 서버가 실제로 그렇게 주는 값이다: [RoomType.RESERVATION] 이고 처음부터 확정이다.
     * 요금·경로를 지어내지 않는 것도 서버와 같다 — 예약 방은 서버가 경로를 추정하지 않는다.
     */
    override suspend fun createReservation(request: NewReservation): Ride = Ride(
        id = "dummy-reservation",
        origin = request.departure,
        destination = request.destination,
        departureLabel = "",
        capacity = request.capacity,
        members = listOf(me),
        farePerPerson = 0,
        totalFare = 0,
        status = RideStatus.RECRUITING,
        originLat = request.departureLat,
        originLng = request.departureLng,
        destinationLat = request.destinationLat,
        destinationLng = request.destinationLng,
        departureAt = request.departureAt,
        isConfirmed = true,
        roomType = RoomType.RESERVATION,
    )

    // 예약 조회 셋은 **빈 목록이 정답이다.** 더미에는 예약 방이 없고, 지어내면 프리뷰에서
    // 「11월 13일 08:00 출발」 같은 **실재하지 않는 약속**을 그려 준다 — 실시간 후보
    // (findRealtimeCandidates)를 빈 목록으로 둔 것과 같은 근거다. 서버 없이 화면이 열리는지만 본다.
    override suspend fun getReservations(from: String, to: String): List<Ride> = emptyList()

    override suspend fun getReservationsSoon(
        swLat: Double,
        swLng: Double,
        neLat: Double,
        neLng: Double,
    ): List<Ride> = emptyList()

    override suspend fun searchReservations(
        departureLat: Double,
        departureLng: Double,
        date: String,
        destination: ReservationDestination?,
    ): List<ReservationSearchResult> = emptyList()

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
}
