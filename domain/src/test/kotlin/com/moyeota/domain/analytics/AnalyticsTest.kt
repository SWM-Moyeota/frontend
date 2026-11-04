package com.moyeota.domain.analytics

import com.moyeota.domain.model.Gender
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 이벤트 이름·프로퍼티 키를 **못 박는다**. Mixpanel 대시보드와 iOS(`Analytics.swift`)가 이 문자열을 보고 있어
 * 하나라도 바뀌면 퍼널이 끊긴다 — 바꿔야 하면 iOS·보드를 같이 바꾸고 이 테스트를 고친다.
 */
class AnalyticsTest {

    @After
    fun tearDown() = Analytics.reset()

    @Test
    fun `이벤트 이름은 iOS 와 같은 문자열로 고정된다`() {
        val expected = mapOf<AnalyticsEvent, String>(
            AnalyticsEvent.ScreenViewed("home") to "view_screen",
            AnalyticsEvent.SignupCompleted(Gender.MALE, "20s") to "complete_signup",
            AnalyticsEvent.LoginCompleted to "complete_login",
            AnalyticsEvent.PartyCreateClicked(3) to "click_create_party",
            AnalyticsEvent.PartyJoinClicked to "click_join_party",
            AnalyticsEvent.MatchRequested(MatchRequestMethod.CREATE, 3, null, null, null) to "request_match",
            AnalyticsEvent.MatchRequestFailed(MatchRequestMethod.JOIN, MatchFailureReason.PARTY_FULL) to "fail_match_request",
            AnalyticsEvent.MatchCompleted(3, 3, 42) to "complete_match",
            AnalyticsEvent.MatchCanceled(3, 1) to "cancel_match",
            AnalyticsEvent.PartyFinished(3) to "finish_party",
            AnalyticsEvent.RideFinished to "finish_ride",
            AnalyticsEvent.EmergencyTriggered to "trigger_emergency",
            AnalyticsEvent.AccountDeleted to "delete_account",
        )
        for ((event, name) in expected) assertEquals(name, event.name)
    }

    @Test
    fun `프로퍼티 키와 값 상수는 snake_case 소문자다`() {
        val signup = AnalyticsEvent.SignupCompleted(Gender.FEMALE, "30s").properties
        assertEquals(AnalyticsValue.Str("female"), signup["gender"])
        assertEquals(AnalyticsValue.Str("30s"), signup["age_band"])

        val requested = AnalyticsEvent.MatchRequested(
            method = MatchRequestMethod.CREATE,
            capacity = 3,
            originArea = "서울 관악구",
            destinationArea = "서울 강남구",
            estimatedFare = 12_300,
        ).properties
        assertEquals(
            setOf("method", "capacity", "origin_area", "destination_area", "estimated_fare"),
            requested.keys,
        )
        assertEquals(AnalyticsValue.Str("create"), requested["method"])
        assertEquals(AnalyticsValue.Int(12_300), requested["estimated_fare"])

        val completed = AnalyticsEvent.MatchCompleted(3, 3, 42).properties
        assertEquals(setOf("capacity", "member_count", "waited_seconds"), completed.keys)

        val failed = AnalyticsEvent.MatchRequestFailed(MatchRequestMethod.JOIN, MatchFailureReason.ALREADY_IN_PARTY).properties
        assertEquals(AnalyticsValue.Str("join"), failed["method"])
        assertEquals(AnalyticsValue.Str("already_in_party"), failed["reason"])
    }

    @Test
    fun `값이 없는 프로퍼티는 키를 빼고 보낸다 - 0 이나 빈 문자열로 지어내지 않는다`() {
        val joined = AnalyticsEvent.MatchRequested(
            method = MatchRequestMethod.JOIN,
            capacity = null,
            originArea = null,
            destinationArea = null,
            estimatedFare = null,
        ).properties
        assertEquals(mapOf("method" to AnalyticsValue.Str("join")), joined)
    }

    @Test
    fun `프로퍼티 없는 이벤트는 빈 맵이다`() {
        for (event in listOf(
            AnalyticsEvent.LoginCompleted, AnalyticsEvent.PartyJoinClicked, AnalyticsEvent.RideFinished,
            AnalyticsEvent.EmergencyTriggered, AnalyticsEvent.AccountDeleted,
        )) {
            assertTrue(event.name, event.properties.isEmpty())
        }
    }

    @Test
    fun `sink 가 없으면 무동작이고 sink 가 던져도 호출부로 새지 않는다`() {
        Analytics.track(AnalyticsEvent.LoginCompleted) // 설치 전 — 아무 일도 없어야 한다

        val recorded = mutableListOf<AnalyticsEvent>()
        Analytics.install { recorded += it }
        Analytics.track(AnalyticsEvent.LoginCompleted)
        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.LoginCompleted), recorded)

        Analytics.install { throw IllegalStateException("리포터 고장") }
        Analytics.track(AnalyticsEvent.LoginCompleted) // 던지지 않아야 한다
    }

    @Test
    fun `주소는 시도와 시군구까지만 남긴다`() {
        assertEquals("서울 관악구", administrativeArea("서울 관악구 남부순환로 1820"))
        assertEquals("경기 안양시 만안구", administrativeArea("경기 안양시 만안구 성결대학로 53"))
        assertEquals("부산광역시 금정구", administrativeArea("부산광역시 금정구 부산대학로63번길 2"))
        assertEquals("경기도 수원시 영통구", administrativeArea("경기도 수원시 영통구 월드컵로 206"))
        assertEquals("강원 춘천시", administrativeArea("강원 춘천시 중앙로 1"))
        assertEquals("전남 담양군", administrativeArea("전남 담양군 담양읍 중앙로 1"))
    }

    @Test
    fun `주소가 아니거나 시군구가 없으면 null 이다`() {
        assertNull(administrativeArea("부산대로"))
        assertNull(administrativeArea("CGV 서면"))
        assertNull(administrativeArea("세종특별자치시 한누리대로 2130"))
        assertNull(administrativeArea("서울"))
        assertNull(administrativeArea(""))
        assertNull(administrativeArea("현재 위치"))
    }

    @Test
    fun `연령대는 만 나이로 접고 생일 당일부터 다음 나이다`() {
        val today = LocalDate.of(2026, 11, 4)
        assertEquals("20s", ageBand(LocalDate.of(2000, 11, 4), today)) // 오늘 26세
        assertEquals("20s", ageBand(LocalDate.of(1996, 11, 5), today)) // 내일 30세 → 아직 29
        assertEquals("30s", ageBand(LocalDate.of(1996, 11, 4), today)) // 오늘 30세
        assertEquals("10s", ageBand(LocalDate.of(2007, 1, 1), today)) // 19세
        assertEquals("10s", ageBand(LocalDate.of(2020, 1, 1), today)) // 20 미만은 전부 10s
        assertEquals("40s", ageBand(LocalDate.of(1980, 6, 1), today))
        assertEquals("50s", ageBand(LocalDate.of(1970, 6, 1), today))
        assertEquals("60s+", ageBand(LocalDate.of(1966, 11, 4), today)) // 오늘 60세
        assertEquals("60s+", ageBand(LocalDate.of(1940, 1, 1), today))
    }

    @Test
    fun `성별 값은 소문자 상수다`() {
        assertEquals("male", Gender.MALE.analyticsValue)
        assertEquals("female", Gender.FEMALE.analyticsValue)
        assertEquals("undisclosed", Gender.UNDISCLOSED.analyticsValue)
        assertFalse(Gender.MALE.analyticsValue.any(Char::isUpperCase))
    }
}
