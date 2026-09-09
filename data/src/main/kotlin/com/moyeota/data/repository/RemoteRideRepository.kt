package com.moyeota.data.repository

import com.moyeota.data.remote.MatchingApi
import com.moyeota.data.remote.ReportApi
import com.moyeota.data.remote.dto.CallResultRequestDto
import com.moyeota.data.remote.dto.ReportRequestDto
import com.moyeota.data.remote.routeRequestDto
import com.moyeota.data.remote.toAssignedDriver
import com.moyeota.data.remote.toRequestDto
import com.moyeota.data.remote.toRide
import com.moyeota.data.remote.toRouteEstimate
import com.moyeota.domain.model.AssignedDriver
import com.moyeota.domain.model.NewParty
import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RouteEstimate
import com.moyeota.domain.repository.RideRepository
import com.moyeota.domain.session.UserSession
import kotlin.coroutines.cancellation.CancellationException

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
) : RideRepository {

    override fun getNearbyParties(): List<Ride> = local.getNearbyParties()

    override fun getMyRides(): List<Ride> = local.getMyRides()

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

    // 생성자는 서버가 토큰에서 정한다 — 요청 본문에도 응답에도 id 가 없다.
    override suspend fun createParty(request: NewParty): Ride =
        api.openParty(request.toRequestDto()).toRide()

    // 합류 응답이 곧 방 상세라 재조회 없이 그대로 반환한다. 합류자는 Bearer 토큰이 정한다.
    override suspend fun joinParty(partyId: Long): Ride =
        api.joinParty(partyId).toRide(session.currentUserUuid)

    override suspend fun leaveParty(partyId: Long) = api.leaveParty(partyId)

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
