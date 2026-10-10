package com.moyeota.presentation.feature.matching

import com.moyeota.domain.model.PartyStatus
import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RideStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 25 배차 폴링이 **언제 방 상세를 다시 읽는가**.
 *
 * 매 주기 읽던 상세(쿼리 3개 · route 포함, 부하테스트 요청의 45%)를 가벼운 상태 조회로 바꾼 대신,
 * 「바뀐 주기」를 놓치면 배정된 기사가 화면에 영영 안 나온다 — 그 판정이 이 함수 하나에 모여 있다.
 */
class DetailReloadGateTest {

    private fun ride(
        status: RideStatus = RideStatus.DISPATCHING,
        driverId: Long? = null,
        fingerprint: String? = "aaaa",
    ) = Ride(
        id = "1", origin = "부산대", destination = "서면", departureLabel = "지금 출발",
        capacity = 2, members = emptyList(), farePerPerson = 0, totalFare = 0,
        status = status, driverId = driverId, fingerprint = fingerprint,
    )

    @Test
    fun `상세를 아직 못 받았으면 읽는다 - 첫 조회 실패에서 폴링이 복구된다`() {
        assertTrue(shouldReloadDetail(null, PartyStatus(RideStatus.DISPATCHING, 2, "aaaa")))
    }

    @Test
    fun `지문이 그대로면 읽지 않는다`() {
        val held = ride(fingerprint = "aaaa")
        assertFalse(shouldReloadDetail(held, PartyStatus(RideStatus.DISPATCHING, 0, "aaaa")))
    }

    @Test
    fun `기사 배정으로 지문이 달라지면 읽는다 - 그때 driverId 가 들어온다`() {
        val searching = ride(fingerprint = "aaaa", driverId = null)
        assertTrue(shouldReloadDetail(searching, PartyStatus(RideStatus.DISPATCHING, 0, "bbbb")))
    }

    @Test
    fun `탑승 시작으로 status 가 바뀌면 읽는다`() {
        val assigned = ride(status = RideStatus.DISPATCHING, driverId = 7L, fingerprint = "aaaa")
        assertTrue(shouldReloadDetail(assigned, PartyStatus(RideStatus.ONGOING, 0, "bbbb")))
    }

    @Test
    fun `지문이 없고 아직 미배정이면 매 주기 읽는다 - MATCHING 과 DRIVER_ASSIGNED 가 둘 다 DISPATCHING 이라 구분이 안 된다`() {
        val searching = ride(status = RideStatus.DISPATCHING, driverId = null, fingerprint = null)
        assertTrue(shouldReloadDetail(searching, PartyStatus(RideStatus.DISPATCHING, 0, null)))
    }

    @Test
    fun `지문이 없어도 배정된 뒤에는 상태만 본다 - 남은 전이(IN_RIDE)는 status 로 보인다`() {
        val assigned = ride(status = RideStatus.DISPATCHING, driverId = 7L, fingerprint = null)
        assertFalse(shouldReloadDetail(assigned, PartyStatus(RideStatus.DISPATCHING, 0, null)))
    }

    @Test
    fun `지문이 없고 배차 단계가 아니면 상태만 본다`() {
        val canceled = ride(status = RideStatus.CANCELED, driverId = null, fingerprint = null)
        assertFalse(shouldReloadDetail(canceled, PartyStatus(RideStatus.CANCELED, 0, null)))
    }
}
