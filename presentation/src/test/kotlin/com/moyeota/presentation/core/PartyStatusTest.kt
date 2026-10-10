package com.moyeota.presentation.core

import com.moyeota.domain.model.PartyStatus
import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RideStatus
import com.moyeota.domain.model.User
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 가벼운 상태 조회(/status)만 보고 「상세를 다시 읽어야 하나」를 정하는 규칙 */
class PartyStatusTest {

    private fun ride(status: RideStatus, members: Int, fingerprint: String?) = Ride(
        id = "1", origin = "a", destination = "b", departureLabel = "", capacity = 3,
        members = List(members) { User(id = "u$it", nickname = "nickname", verifiedLabel = "verifiedLabel", rating = 0.0, rideCount = 0) },
        farePerPerson = 0, totalFare = 0, status = status, fingerprint = fingerprint,
    )

    @Test
    fun `지문이 양쪽에 있으면 지문만 본다 - 인원수가 같아도 멤버가 바뀌면 다르다`() {
        val held = ride(RideStatus.RECRUITING, 2, "aaaa")
        assertTrue(PartyStatus(RideStatus.RECRUITING, 2, "aaaa").isSameAs(held))
        assertFalse(PartyStatus(RideStatus.RECRUITING, 2, "bbbb").isSameAs(held))
    }

    @Test
    fun `지문이 한쪽이라도 없으면 상태와 인원수로 본다`() {
        val held = ride(RideStatus.RECRUITING, 2, null)
        assertTrue(PartyStatus(RideStatus.RECRUITING, 2, "aaaa").isSameAs(held))
        assertFalse(PartyStatus(RideStatus.RECRUITING, 3, "aaaa").isSameAs(held))
        assertFalse(PartyStatus(RideStatus.MATCHED, 2, null).isSameAs(held))
    }
}
