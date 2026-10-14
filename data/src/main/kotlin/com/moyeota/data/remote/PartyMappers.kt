package com.moyeota.data.remote

import com.moyeota.data.remote.dto.ApiErrorDto
import com.moyeota.data.remote.dto.DriverSummaryResponse
import com.moyeota.data.remote.dto.OpenPartyRequestDto
import com.moyeota.data.remote.dto.OpenPartyResponse
import com.moyeota.data.remote.dto.PartyDetailResponse
import com.moyeota.data.remote.dto.PartyListResponse
import com.moyeota.data.remote.dto.PartyStatusResponse
import com.moyeota.data.remote.dto.RouteEstimateResponse
import com.moyeota.data.remote.dto.RouteRequestDto
import com.moyeota.data.remote.matching.PartySseEvent
import com.moyeota.domain.model.AssignedDriver
import com.moyeota.domain.model.NewParty
import com.moyeota.domain.model.PartyEvent
import com.moyeota.domain.model.PartyJoinException
import com.moyeota.domain.model.PartyStatus
import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RideStatus
import com.moyeota.domain.model.RouteEstimate
import com.moyeota.domain.model.User
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

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
        // 응답의 departureRadius/destinationRadius 는 **옮기지 않는다** — 서버 고정값이고
        // 아무 조회·판정도 읽지 않는다([PartyDetailResponse.departureRadius] KDoc).
    )
}

/**
 * 합류 응답 → 도메인 [PartyStatus]. 상태 문자열 변환은 상세·목록과 **같은 표**를 쓴다
 * ([partyStatusToRideStatus]) — 두 경로가 갈리면 합류 직후 상태와 상세가 서로 다른 단계로 보인다.
 *
 * 옮기는 건 두 값뿐이다. 지문은 서버가 지웠고([PartyStatusResponse] KDoc), 25·26 이 「바뀌었나」를
 * 판정하는 자리는 이제 도메인 [Ride] 끼리의 `==` 다 — 매퍼가 할 일이 없다.
 */
fun PartyStatusResponse.toPartyStatus(): PartyStatus = PartyStatus(
    status = partyStatusToRideStatus(status),
    currentMembers = currentMembers,
)

/**
 * SSE 프레임([PartySseEvent]) → 도메인 [PartyEvent].
 *
 * 변환이 **상세 매퍼를 그대로 재사용하는 게 핵심**이다 — `connected` 는 방 상세와 같은 모양이라
 * [toRide] 를, `member-joined` 의 멤버는 상세의 멤버와 같은 record 라 [toUser] 를 쓴다. 같은 모양에
 * 변환을 두 벌 두면 한쪽만 서버 변경을 따라가다 "스냅샷의 나"와 "입장 신호의 나"가 어긋난다.
 *
 * [currentUuid] 는 로그인한 사용자의 UUID(`UserSession.currentUserUuid`) — 상세 조회와 **같은 근거**로
 * [com.moyeota.domain.model.User.isMe] 를 판정한다. 이벤트가 올 때마다 읽으므로 재로그인도 바로 반영된다.
 *
 * `member-left` 의 publicId 는 빈 문자열까지 null 로 접는다 — 화면은 "누가 나갔는지 모름"을
 * 한 가지 모양(null)으로만 다뤄야 한다.
 */
fun PartySseEvent.toPartyEvent(currentUuid: String? = null): PartyEvent = when (this) {
    is PartySseEvent.Snapshot -> PartyEvent.Snapshot(detail.toRide(currentUuid))
    is PartySseEvent.MemberJoined -> PartyEvent.MemberJoined(
        member = payload.member.toUser(currentUuid),
        status = partyStatusToRideStatus(payload.status),
    )
    is PartySseEvent.MemberLeft -> PartyEvent.MemberLeft(
        publicId = payload.publicId?.takeIf { it.isNotBlank() },
        status = partyStatusToRideStatus(payload.status),
    )
    PartySseEvent.Closed -> PartyEvent.Closed
    PartySseEvent.Disconnected -> PartyEvent.Disconnected
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
    // 반경은 상세 매퍼와 같은 이유로 옮기지 않는다([PartyDetailResponse.departureRadius] KDoc).
)

/**
 * 방 생성 요청 본문. 실리는 것은 **좌표·라벨·정원뿐**이다.
 *
 * 방장 id 는 요청에도 도메인 모델에도 없다 — 방 생성자는 서버가 토큰에서 정한다(NewParty KDoc 참고).
 * 반경도 없다 — 서버가 요청에서 빼고 고정값을 쓴다(Backend `a668aee`). 둘 다 **보내도 서버가 버리는**
 * 값이라, 되살리면 앱이 정한 값이 반영된다는 착각만 남는다. 「본문에 없다」는 PartyMappersTest 가 못 박는다.
 */
fun NewParty.toRequestDto(): OpenPartyRequestDto = OpenPartyRequestDto(
    departureLat = departureLat,
    departureLng = departureLng,
    destinationLat = destinationLat,
    destinationLng = destinationLng,
    departure = departure,
    destination = destination,
    capacity = capacity,
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

private val errorJson = Json { ignoreUnknownKeys = true }

/**
 * 서버 공통 에러 본문 `{code, message}`. HTTP 응답이 아닌 실패(네트워크 등)거나 본문이 없으면 null.
 *
 * **본문은 한 번만 읽을 수 있다** — `ResponseBody.string()` 이 스트림을 닫으므로 두 번째 호출은 실패한다.
 * 그래서 호출부가 결과를 **들고 다니며** 쓴다(두 번 읽는 코드를 만들지 말 것).
 */
internal fun Throwable.readApiError(): ApiErrorDto? = (this as? HttpException)?.let { http ->
    runCatching {
        http.response()?.errorBody()?.string()
            ?.takeIf { it.isNotBlank() }
            ?.let { errorJson.decodeFromString<ApiErrorDto>(it) }
    }.getOrNull()
}

/**
 * 합류 실패 → [PartyJoinException]. presentation 이 HTTP 를 알 필요가 없게 하는 경계다.
 *
 * [error] 는 이미 읽어 둔 본문([readApiError])이고, [joinedStatus] 는 "내가 이미 그 방 멤버임을
 * 확인했을 때"의 그 방 상태다 — 저장소가 방 상세로 **증거를 확인한 뒤에만** 넘긴다.
 */
internal fun partyJoinException(
    failure: Throwable,
    error: ApiErrorDto?,
    joinedStatus: PartyStatus? = null,
): PartyJoinException = when (failure) {
    is PartyJoinException -> failure

    is HttpException -> PartyJoinException(
        code = error?.code,
        serverMessage = error?.message,
        httpStatus = failure.code(),
        joinedStatus = joinedStatus,
        cause = failure,
    )

    // 응답 자체를 못 받았다(연결 거부·타임아웃·DNS). 서버가 합류를 처리했는지조차 알 수 없으므로
    // 화면은 재시도를 안내해야 한다 — 재시도가 409 로 떨어지면 그때 joinedStatus 로 복구된다.
    is IOException -> PartyJoinException(
        code = null,
        serverMessage = null,
        isNetwork = true,
        cause = failure,
    )

    else -> PartyJoinException(code = null, serverMessage = null, cause = failure)
}
