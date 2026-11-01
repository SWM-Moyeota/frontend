package com.moyeota.data.repository

import com.moyeota.data.remote.MatchingApi
import com.moyeota.data.remote.ReportApi
import com.moyeota.data.remote.dto.ApiErrorDto
import com.moyeota.data.remote.dto.CallResultRequestDto
import com.moyeota.data.remote.dto.RealTimeMatchingRequestDto
import com.moyeota.data.remote.dto.ReportRequestDto
import com.moyeota.data.remote.matching.PartyEventSource
import com.moyeota.data.remote.partyFinishException
import com.moyeota.data.remote.realtimeMatchException
import com.moyeota.data.remote.partyJoinException
import com.moyeota.data.remote.readApiError
import com.moyeota.data.remote.toMatchCandidate
import com.moyeota.data.remote.routeRequestDto
import com.moyeota.data.remote.toAssignedDriver
import com.moyeota.data.remote.toPartyEvent
import com.moyeota.data.remote.toPartyStatus
import com.moyeota.data.remote.toRequestDto
import com.moyeota.data.remote.toRide
import com.moyeota.data.remote.toRouteEstimate
import com.moyeota.domain.model.AssignedDriver
import com.moyeota.domain.model.MatchCandidate
import com.moyeota.domain.model.NewParty
import com.moyeota.domain.model.PartyDetail
import com.moyeota.domain.model.PartyEvent
import com.moyeota.domain.model.PartyFinishException
import com.moyeota.domain.model.PartyJoinException
import com.moyeota.domain.model.PartyLeaveException
import com.moyeota.domain.model.PartyOpenException
import com.moyeota.domain.model.PartyStatus
import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RouteEstimate
import com.moyeota.domain.repository.RideRepository
import com.moyeota.domain.session.UserSession
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import retrofit2.HttpException

/**
 * 매칭·신고 도메인만 서버 연동 — 나머지는 더미로 위임한다.
 *
 * [session] 이 필요한 이유는 하나다: 방 상세의 멤버 중 **누가 나인지**는 서버가 표시해 주지 않는다.
 * `MemberInfo.publicId` 가 JWT `sub` 와 같은 값이라 앱이 직접 비교해야 하고, 비교 대상인
 * 현재 사용자 UUID 의 단일 출처가 [session] 이다. 매퍼에 세션을 들려 보내지 않으면 화면마다
 * "나 찾기"를 다시 구현하게 된다.
 *
 * [reportApi] 기본값 null: 신고 배선이 없는 생성 호출(테스트 등)을 깨지 않기 위함.
 * 배선 전 신고 호출은 한국어 IllegalStateException 으로 실패한다 (화면은 다이얼을 우선 연다).
 * [currentLocation]: 신고 시점의 실측 좌표 공급자(app 모듈이 FusedLocation 으로 주입).
 * 못 얻으면 null — 서버 ReportRequest 는 좌표 null 을 허용하므로 가짜 좌표를 지어내지 않는다.
 */
class RemoteRideRepository(
    private val api: MatchingApi,
    private val session: UserSession,
    private val local: RideRepository = DummyRideRepository(),
    private val reportApi: ReportApi? = null,
    private val currentLocation: suspend () -> Pair<Double, Double>? = { null },
    /** 매칭방 변화 신호(SSE). null 이면 신호 없이 폴링만 한다(테스트·더미) */
    private val partyEvents: PartyEventSource? = null,
) : RideRepository {

    /**
     * SSE 프레임을 도메인 이벤트로 옮긴다. 세션 uuid 는 **이벤트가 올 때마다** 읽는다 — 스냅샷·입장 신호의
     * "나" 판정이 상세 조회와 같은 근거를 쓰게 하고, 구독 중 재로그인이 있어도 다음 이벤트부터 맞게 한다.
     */
    override fun observePartyEvents(partyId: Long): Flow<PartyEvent> =
        partyEvents?.events(partyId)?.map { it.toPartyEvent(session.currentUserUuid) } ?: emptyFlow()

    override fun getNearbyParties(): List<Ride> = local.getNearbyParties()

    override suspend fun getParties(): List<Ride> = api.getParties().list.map { it.toRide() }

    // 전체 목록과 같은 경로 + 쿼리 파라미터 4개. 매퍼가 departureLat/Lng 를 originLat/Lng 로 옮긴다.
    override suspend fun getPartiesWithin(
        swLat: Double,
        swLng: Double,
        neLat: Double,
        neLng: Double,
    ): List<Ride> = api.getPartiesWithin(swLat, swLng, neLat, neLng).list.map { it.toRide() }

    // currentUserUuid 는 호출 시점에 읽는다 — 재로그인으로 주체가 바뀌어도 다음 조회부터 바로 반영된다.
    override suspend fun getPartyDetail(partyId: Long): Ride =
        api.getPartyDetail(partyId).toRide(session.currentUserUuid)

    // 같은 엔드포인트를 한 번만 때리고 순번까지 함께 넘긴다 — 두 번 읽지 않는다.
    override suspend fun getPartyDetailWithVersion(partyId: Long): PartyDetail =
        api.getPartyDetail(partyId).let { PartyDetail(it.toRide(session.currentUserUuid), it.version) }

    /**
     * 생성자는 서버가 토큰에서 정한다 — 요청 본문에도 응답에도 id 가 없다.
     *
     * 실패는 전부 [PartyOpenException] 으로 좁힌다([joinParty] 와 같은 모양·같은 이유) — 이 액션의
     * 실패는 **기다리면 풀리는 것과 사용자가 고쳐야 하는 것으로 갈리는데**(409 진행 중인 방 · 400
     * 좌표/정원 · 502 경로 엔진) 예전에는 전부 한 문구로 떨어져 **그중 어느 것도 다음 행동을 말해
     * 주지 못했다.** 특히 409 는 아무도 합류하지 않은 ACTIVE 방이 30분 스윕에도 남아 영구 교착이 된다
     * (그 예외 KDoc 참고).
     *
     * 에러 본문은 스트림이라 한 번만 읽을 수 있다 — 읽어서 들고 넘긴다([readApiError]).
     * 복구 조회는 없다: 서버는 검사를 **먼저** 하고 방을 만들므로, 실패했으면 만들어진 방이 없다
     * (확인할 것이 없다 — [finishParty] 와 같은 사정이다).
     */
    override suspend fun createParty(request: NewParty): Ride {
        val response = runCatching { api.openParty(request.toRequestDto()) }
        val failure = response.exceptionOrNull()
            ?: return response.getOrThrow().toRide()
        // runCatching 은 취소까지 삼키므로 되던져 화면 이탈이 에러로 보이지 않게 한다.
        if (failure is CancellationException) throw failure
        throw partyOpenException(failure, failure.readApiError())
    }

    /**
     * 합류 응답은 더 이상 방 상세가 아니다(Backend #213) — 상태·인원만 담긴 [PartyStatus] 다
     * (지문 필드는 `04f6be7` 에서 빠졌다).
     *
     * 성공 경로에서는 세션을 보지 않는다: 응답에 멤버 목록이 없어 "누가 나인지"를 판정할 거리가 없다.
     * 동승자 목록은 [getPartyDetail] 이나 [observePartyEvents] 의 첫 스냅샷으로 받는다.
     *
     * 실패는 전부 [PartyJoinException] 으로 좁힌다 — 그중 **「이미 참여 중」 409 는 복구를 시도한다**
     * ([recoverMembership]).
     */
    override suspend fun joinParty(partyId: Long): PartyStatus {
        val response = runCatching { api.joinParty(partyId) }
        val failure = response.exceptionOrNull()
            ?: return response.getOrThrow().toPartyStatus()
        // runCatching 은 취소까지 삼키므로 되던져 화면 이탈이 에러로 보이지 않게 한다.
        if (failure is CancellationException) throw failure

        // 에러 본문은 스트림이라 한 번만 읽을 수 있다 — 읽어서 들고 다닌다.
        val error = failure.readApiError()
        val joined = if (PartyJoinException.isAlreadyJoinedCode(error?.code)) recoverMembership(partyId) else null
        throw partyJoinException(failure, error, joined)
    }

    /**
     * 「이미 참여 중입니다」 409 가 **이 방**을 가리키는지 확인해, 그렇다면 그 방의 현재 상태를 돌려준다.
     *
     * 이 확인이 필요한 이유는 두 가지다.
     * 1. 서버가 두 경우를 **구분해 주지 않는다** — 재합류도, 다른 방에 있는 것도 똑같이
     *    `ALREADY_JOINED_OTHER_PARTY` 다([PartyJoinException.isAlreadyJoinedCode] KDoc).
     * 2. 합류는 서버가 **멤버 추가를 먼저 커밋**하고 응답을 만든다. 그 응답을 앱이 놓치면(역직렬화 실패·
     *    네트워크 끊김) 서버에는 들어가 있는데 화면은 실패로 끝나고, 재시도는 409 로 막힌다 —
     *    그대로 두면 **30분 스윕 전까지 그 계정이 어떤 방에도 못 들어간다.**
     *
     * 판정 근거는 추측이 아니라 증거다: 방 상세의 members 에 내 publicId 가 있는지 본다
     * (`isMe` 판정은 [getPartyDetail] 과 같은 매퍼·같은 세션 uuid 를 쓴다). 상세 조회까지 실패하면
     * 복구를 포기하고 null 을 돌려준다 — 없는 멤버십을 있다고 하면 더 나쁘다.
     */
    private suspend fun recoverMembership(partyId: Long): PartyStatus? {
        val ride = runCatching { getPartyDetail(partyId) }.getOrNull() ?: return null
        if (ride.members.none { it.isMe }) return null
        return PartyStatus(ride.status, ride.members.size)
    }

    /**
     * 나가기. **403 `NOT_PARTY_MEMBER` 는 성공으로 되돌린다** — [recoverMembership] 과 대칭인 처방이다.
     *
     * 되돌리는 근거: 서버 `Party.leave` 는 「멤버인가」를 먼저 보므로 그 403 은 **내가 이 방에 없다**는
     * 뜻이고, 나가기의 목표가 정확히 그 상태다. 되돌리지 않으면 204 응답이 유실된 사용자가(터널·타임아웃)
     * 「나가지 못했어요」를 보고 다시 눌러 **같은 문구의 403** 을 맞는다 — 로컬 기억과 홈 배너는 그 방을
     * 계속 들고 있으니 화면상 나는 아직 그 방에 있고, 벗어날 길이 없다.
     *
     * 합류와 달리 **상세 조회로 확인하지 않는다**: 합류는 「이 방인가 다른 방인가」를 서버가 구분해 주지
     * 않아 증거가 필요했지만, 이 403 은 그 자체로 「이 방에 내가 없다」를 말한다. 덧붙여 방 상세에는
     * 참여자 검사가 없어([com.moyeota.domain.model.PartyMembershipLostException] KDoc) 읽어도 멤버십을
     * 되묻지 못한다.
     *
     * 나머지 실패는 [PartyLeaveException] 으로 좁힌다 — 409 `PARTY_NOT_RECRUITING` 은 「매칭이 시작돼
     * 나갈 수 없다」라서 재시도 안내와 **다음 행동이 다르다**.
     */
    override suspend fun leaveParty(partyId: Long) {
        val failure = runCatching { api.leaveParty(partyId) }.exceptionOrNull() ?: return
        // runCatching 은 취소까지 삼키므로 되던져 화면 이탈이 에러로 보이지 않게 한다.
        if (failure is CancellationException) throw failure
        // 에러 본문은 스트림이라 한 번만 읽을 수 있다 — 읽어서 들고 다닌다.
        val error = failure.readApiError()
        // 이미 나갔다 = 나가기는 "실패"가 아니라 "이미 됨"이다.
        if (error?.code == PartyLeaveException.NOT_PARTY_MEMBER) return
        throw partyLeaveException(failure, error)
    }

    /**
     * 실패를 전부 [PartyFinishException] 으로 좁힌다 — **403 과 409 가 각각 두 가지 뜻**이라
     * 화면이 상태 코드만으로는 안내 문구를 가를 수 없다(그 예외 KDoc 참고).
     *
     * 에러 본문은 스트림이라 한 번만 읽을 수 있다 — 읽어서 들고 넘긴다([readApiError]).
     * 합류와 달리 멤버십 복구 조회는 없다: 완료는 서버가 **검사를 먼저** 하고 상태를 바꾸므로,
     * 실패했으면 방은 그대로다(확인할 것이 없다).
     */
    override suspend fun finishParty(partyId: Long) {
        val failure = runCatching { api.finishParty(partyId) }.exceptionOrNull() ?: return
        // runCatching 은 취소까지 삼키므로 되던져 화면 이탈이 에러로 보이지 않게 한다.
        if (failure is CancellationException) throw failure
        throw partyFinishException(failure, failure.readApiError())
    }

    // 빈 목록은 성공이다 — 양쪽 300m 를 동시에 만족하는 방이 없다는 사실일 뿐이라 예외로 올리지 않는다.
    // summary 가 없는 항목만 버린다(방 id 가 없으면 탭해도 갈 곳이 없다).
    override suspend fun findRealtimeCandidates(
        departureLat: Double,
        departureLng: Double,
        destinationLat: Double,
        destinationLng: Double,
    ): List<MatchCandidate> {
        val request = RealTimeMatchingRequestDto(
            departureLat = departureLat,
            departureLng = departureLng,
            // 요청 쪽 필드명은 destLat/destLng 다 — 응답 쪽(destinationLat)과 다르다.
            destLat = destinationLat,
            destLng = destinationLng,
        )
        val result = runCatching { api.getRealtimeCandidates(request) }
        result.exceptionOrNull()?.let { failure ->
            if (failure is CancellationException) throw failure
            throw realtimeMatchException(failure, failure.readApiError())
        }
        return result.getOrThrow().mapNotNull { it.toMatchCandidate() }
    }

    override suspend fun getAssignedDriver(partyId: Long): AssignedDriver =
        api.getAssignedDriver(partyId).toAssignedDriver()

    override suspend fun reportEmergency(partyId: Long?): Long {
        val report = reportApi
            ?: throw IllegalStateException("신고 API가 아직 연결되지 않았어요 (AppContainer 배선 필요)")
        // 실측 좌표. 측위 실패(권한 없음·타임아웃)는 신고를 막지 않는다 — 좌표만 null 로 보낸다.
        val location = try {
            currentLocation()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        return runReportCall("긴급 신고 저장에 실패했어요") {
            report.report(
                ReportRequestDto(
                    partyId = partyId,
                    latitude = location?.first,
                    longitude = location?.second,
                ),
            ).reportId
        }
    }

    override suspend fun confirmEmergencyCall(called: Boolean) {
        val report = reportApi
            ?: throw IllegalStateException("신고 API가 아직 연결되지 않았어요 (AppContainer 배선 필요)")
        runReportCall("112 통화 결과 저장에 실패했어요") {
            report.confirmCallResult(CallResultRequestDto(called = called))
        }
    }

    // 신고 흐름의 계약: 어떤 원인(HTTP 4xx/5xx, 네트워크, 직렬화)이든 한국어 메시지의
    // IllegalStateException 하나로 전파한다. 화면은 이 실패를 "저장 실패 플래그"로만 다루고
    // 112 다이얼은 무조건 연다 (통화 최우선). 코루틴 취소는 삼키지 않고 그대로 올린다.
    private inline fun <T> runReportCall(message: String, block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw IllegalStateException(message, e)
    }

    // 백엔드가 POST 로 바뀌어 열린 엔드포인트. 응답 폴리라인 필드는 route 가 아니라 path 다.
    override suspend fun previewRoute(
        departureLat: Double,
        departureLng: Double,
        destinationLat: Double,
        destinationLng: Double,
    ): RouteEstimate = api.previewRoute(
        routeRequestDto(departureLat, departureLng, destinationLat, destinationLng),
    ).toRouteEstimate()
}

/**
 * 방 생성 실패 → [PartyOpenException]. presentation 이 HTTP 를 알 필요가 없게 하는 경계다.
 *
 * `PartyMappers.kt` 의 [com.moyeota.data.remote.partyJoinException]·
 * [com.moyeota.data.remote.partyFinishException] 과 **같은 모양**이지만 그 파일이 아니라 여기 있다 —
 * 파일 소유가 갈려 있어서다(다른 작업과 동시에 고치는 중). 나중에 세 함수를 한곳에 모으는 쪽이 낫다.
 *
 * [error] 는 이미 읽어 둔 본문([readApiError])이다 — **본문은 한 번만 읽을 수 있으므로** 여기서 다시
 * 읽지 않는다. 합류의 `isNetwork` 같은 구분은 두지 않는다: 이 액션은 응답을 못 받은 실패와 모르는
 * 코드의 **대응이 같다**(일반 문구 = 잠시 후 다시 시도). 쓰지 않을 구분을 노출하면 나중에 네트워크
 * 실패에 「핀을 옮겨 주세요」가 붙는 식으로 어긋난다.
 */
private fun partyOpenException(
    failure: Throwable,
    error: ApiErrorDto?,
): PartyOpenException = when (failure) {
    is PartyOpenException -> failure

    is HttpException -> PartyOpenException(
        code = error?.code,
        serverMessage = error?.message,
        httpStatus = failure.code(),
        cause = failure,
    )

    // 응답 자체를 못 받았다(연결 거부·타임아웃·DNS) 또는 그 밖의 실패. 서버가 말해 준 사유가 없으므로
    // code 는 null 이고, 화면은 일반 문구로 안내한다.
    else -> PartyOpenException(code = null, serverMessage = null, cause = failure)
}

/**
 * 나가기 실패 → [PartyLeaveException]. [partyOpenException] 과 같은 자리·같은 이유의 경계다.
 *
 * **403 `NOT_PARTY_MEMBER` 는 여기까지 오지 않는다** — 호출부가 그 전에 성공으로 되돌린다
 * ([RemoteRideRepository.leaveParty]). 그 되돌림을 이 함수 안으로 옮기지 말 것: 예외를 만드는 자리가
 * "실패가 아님"까지 판정하면 호출부가 성공 경로로 빠질 길이 없다.
 */
private fun partyLeaveException(
    failure: Throwable,
    error: ApiErrorDto?,
): PartyLeaveException = when (failure) {
    is PartyLeaveException -> failure

    is HttpException -> PartyLeaveException(
        code = error?.code,
        serverMessage = error?.message,
        httpStatus = failure.code(),
        cause = failure,
    )

    else -> PartyLeaveException(code = null, serverMessage = null, cause = failure)
}
