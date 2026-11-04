package com.moyeota.presentation.feature.home

import com.moyeota.domain.analytics.AnalyticsEvent
import com.moyeota.domain.analytics.MatchFailureReason
import com.moyeota.domain.analytics.MatchRequestMethod
import com.moyeota.domain.model.PartyJoinException
import com.moyeota.domain.model.PartyOpenException
import com.moyeota.domain.model.Place
import com.moyeota.presentation.analytics.AnalyticsRecorderRule
import com.moyeota.presentation.analytics.FakeRideRepository
import com.moyeota.presentation.analytics.testRide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 16 「방 만들기」 — click 은 요청 직전, request_match 는 성공 뒤, 실패는 fail_match_request(reason). */
class CreatePartyAnalyticsTest {

    @get:Rule val rule = AnalyticsRecorderRule()

    // 검색 결과 그대로(도로명이 있음)
    private val origin = Place(name = "서울대입구역", roadName = "서울 관악구 남부순환로 1820", latitude = 37.48, longitude = 126.95)

    // 역지오코딩으로 이름이 실주소가 되고 도로명이 비워진 장소
    private val destination = Place(name = "경기 안양시 만안구 성결대학로 53", roadName = "", latitude = 37.38, longitude = 126.93)

    private val conditions = MatchConditions(capacity = 2)

    private fun openError(code: String?, httpStatus: Int? = null) =
        PartyOpenException(code = code, serverMessage = null, httpStatus = httpStatus)

    @Test
    fun `성공하면 click 1번과 request_match 1번 - 시구 지역과 예상 요금을 싣는다`() {
        val repo = FakeRideRepository().apply { create = { testRide(capacity = 2, estimatedFare = 12_300) } }

        CreatePartyViewModel(repo).createParty(origin, destination, conditions)

        assertEquals(listOf("click_create_party", "request_match"), rule.names())
        assertEquals(AnalyticsEvent.PartyCreateClicked(2), rule.recorded[0])
        assertEquals(
            AnalyticsEvent.MatchRequested(
                method = MatchRequestMethod.CREATE,
                capacity = 2,
                originArea = "서울 관악구",
                destinationArea = "경기 안양시 만안구",
                estimatedFare = 12_300,
            ),
            rule.recorded[1],
        )
    }

    @Test
    fun `주소로 판정되지 않는 장소는 지역 키를 뺀다`() {
        val repo = FakeRideRepository().apply { create = { testRide(capacity = 2) } }
        val placeOnly = Place(name = "부산대", roadName = "부산대로", latitude = 35.2, longitude = 129.0)

        CreatePartyViewModel(repo).createParty(placeOnly, placeOnly, conditions)

        val requested = rule.only<AnalyticsEvent.MatchRequested>().single()
        assertNull(requested.originArea)
        assertNull(requested.destinationArea)
        assertEquals(setOf("method", "capacity"), requested.properties.keys)
    }

    @Test
    fun `PartyOpenException 으로 실패하면 click 1번과 fail 1번 - request_match 는 보내지 않는다`() {
        val repo = FakeRideRepository().apply {
            create = { throw openError(PartyJoinException.ALREADY_JOINED_OTHER_PARTY, 409) }
        }
        val vm = CreatePartyViewModel(repo)

        vm.createParty(origin, destination, conditions)

        assertEquals(listOf("click_create_party", "fail_match_request"), rule.names())
        assertEquals(
            AnalyticsEvent.MatchRequestFailed(MatchRequestMethod.CREATE, MatchFailureReason.ALREADY_IN_PARTY),
            rule.recorded[1],
        )
        // 기존 동작(사유별 문구·진행 중 방 조회 되살리기)은 그대로다
        assertEquals(ALREADY_IN_PARTY_MESSAGE, vm.uiState.value.errorMessage)
        assertTrue(vm.uiState.value.needsActivePartyRefresh)
    }

    @Test
    fun `좁혀지지 않은 예외는 unknown 으로 1번`() {
        val repo = FakeRideRepository().apply { create = { throw IllegalStateException("직렬화") } }

        CreatePartyViewModel(repo).createParty(origin, destination, conditions)

        assertEquals(
            listOf(
                AnalyticsEvent.PartyCreateClicked(2),
                AnalyticsEvent.MatchRequestFailed(MatchRequestMethod.CREATE, MatchFailureReason.UNKNOWN),
            ),
            rule.recorded,
        )
    }

    @Test
    fun `createFailureReason 은 iOS openFailureReason 과 같은 표다`() {
        val table = listOf(
            openError(PartyJoinException.ALREADY_JOINED_OTHER_PARTY) to "already_in_party",
            openError(PartyJoinException.ALREADY_JOINED_PARTY) to "already_in_party",
            openError(PartyOpenException.SAME_DEPARTURE_DESTINATION) to "same_departure_destination",
            openError(PartyOpenException.OUT_OF_SERVICE_AREA) to "out_of_service_area",
            openError(PartyOpenException.INVALID_CAPACITY) to "invalid_capacity",
            openError(PartyOpenException.ROUTE_SEARCH_FAILED) to "route_unavailable",
            openError(PartyOpenException.INVALID_ROUTE_ESTIMATE) to "route_unavailable",
            openError(PartyOpenException.ROUTE_NOT_FOUND) to "route_unavailable",
            openError(null, httpStatus = 502) to "route_unavailable",
            openError(null, httpStatus = 400) to "unknown",
            openError("SOMETHING_NEW") to "unknown",
            openError(null) to "unknown",
        )
        table.forEach { (e, reason) -> assertEquals("${e.code}/${e.httpStatus}", reason, createFailureReason(e)) }
    }
}
