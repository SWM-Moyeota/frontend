package com.moyeota.data.repository

import com.moyeota.data.remote.MatchingApi
import com.moyeota.data.remote.ReportApi
import com.moyeota.data.remote.dto.CallResultRequestDto
import com.moyeota.data.remote.dto.ReportRequestDto
import com.moyeota.data.remote.toRequestDto
import com.moyeota.data.remote.toRide
import com.moyeota.domain.model.NewParty
import com.moyeota.domain.model.Ride
import com.moyeota.domain.repository.ApiNotAvailableException
import com.moyeota.domain.repository.RideRepository
import kotlin.coroutines.cancellation.CancellationException

// 매칭·신고 도메인만 서버 연동 — 나머지는 더미로 위임한다.
// reportApi 기본값 null: AppContainer 배선(app 모듈)은 이번 범위 밖이라 기존 생성 호출
// `RemoteRideRepository(apis.matching)` 을 깨지 않기 위함. 배선 전 신고 호출은
// 한국어 IllegalStateException 으로 실패한다 (화면은 다이얼을 우선 연다).
class RemoteRideRepository(
    private val api: MatchingApi,
    private val local: RideRepository = DummyRideRepository(),
    private val reportApi: ReportApi? = null,
) : RideRepository {

    override fun getNearbyParties(): List<Ride> = local.getNearbyParties()

    override fun getMyRides(): List<Ride> = local.getMyRides()

    override suspend fun getParties(): List<Ride> = api.getParties().list.map { it.toRide() }

    override suspend fun getPartyDetail(partyId: Long): Ride = api.getPartyDetail(partyId).toRide()

    override suspend fun createParty(request: NewParty): Ride =
        api.openParty(request.toRequestDto()).toRide()

    // 백엔드 PartyApplicationService.join 은 있으나 PartyController 에 매핑이 없다(origin/develop 확인).
    // 경로를 추측해 호출하면 404 로 원인이 흐려지므로, 계약은 유지하되 명시적으로 실패시킨다.
    override suspend fun joinParty(partyId: Long, memberId: Long) {
        throw ApiNotAvailableException("합류 API가 아직 서버에 없어요")
    }

    override suspend fun leaveParty(partyId: Long, memberId: Long) = api.leaveParty(partyId, memberId)

    override suspend fun setReady(partyId: Long, memberId: Long) = api.ready(partyId, memberId)

    override suspend fun cancelReady(partyId: Long, memberId: Long) = api.cancelReady(partyId, memberId)

    override suspend fun startMatching(partyId: Long, memberId: Long) = api.startMatching(partyId, memberId)

    override suspend fun reportEmergency(partyId: Long?): Long {
        val report = reportApi
            ?: throw IllegalStateException("신고 API가 아직 연결되지 않았어요 (AppContainer 배선 필요)")
        return runReportCall("긴급 신고 저장에 실패했어요") {
            // TODO(측위 연동): 승객 앱에 측위 소스가 아직 없어 고정 좌표(서울시청)를 보낸다.
            //  위치 소스 도입 시 이 상수만 실제 좌표로 교체하면 된다.
            report.report(
                ReportRequestDto(
                    partyId = partyId,
                    latitude = FALLBACK_LATITUDE,
                    longitude = FALLBACK_LONGITUDE,
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

    private companion object {
        // TODO(측위 연동): 임시 기준점 — 서울시청 좌표
        const val FALLBACK_LATITUDE = 37.5665
        const val FALLBACK_LONGITUDE = 126.9780
    }
}
