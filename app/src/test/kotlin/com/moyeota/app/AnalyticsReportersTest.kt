package com.moyeota.app

import com.moyeota.domain.analytics.Analytics
import com.moyeota.domain.analytics.AnalyticsEvent
import com.moyeota.domain.model.AuthState
import com.moyeota.domain.model.Gender
import com.moyeota.domain.session.UserSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 세 도구가 **같은 것을 같은 순서로** 받는지, 식별·초기화 시점이 맞는지 본다.
 * SDK 는 여기 없다 — 녹음기 [AnalyticsDestination] 을 꽂는다.
 */
class AnalyticsReportersTest {

    private val authState = MutableStateFlow<AuthState>(AuthState.Unknown)
    private val session = object : UserSession {
        override val authState = this@AnalyticsReportersTest.authState
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private class Recorder(override val name: String, private val failing: Boolean = false) : AnalyticsDestination {
        val calls = mutableListOf<String>()

        private fun record(call: String) {
            if (failing) throw IllegalStateException("리포터 고장")
            calls += call
        }

        override fun track(event: String, properties: Map<String, Any>) = record("track $event $properties")
        override fun identify(userUuid: String) = record("identify $userUuid")
        override fun setProfile(properties: Map<String, Any>) = record("profile $properties")
        override fun reset() = record("reset")
    }

    private fun install(vararg destinations: AnalyticsDestination) =
        AnalyticsReporters.install(destinations.toList(), session, scope, observeOn = Dispatchers.Unconfined)

    @After
    fun tearDown() {
        scope.cancel()
        Analytics.reset()
    }

    @Test
    fun `이벤트는 켜진 도구 모두에게 같은 이름과 프로퍼티로 간다`() {
        val first = Recorder("A")
        val second = Recorder("B")
        install(first, second)

        Analytics.track(AnalyticsEvent.PartyCreateClicked(capacity = 3))

        assertEquals(listOf("track click_create_party {capacity=3}"), first.calls)
        assertEquals(first.calls, second.calls)
    }

    @Test
    fun `도구 하나가 던져도 나머지는 받는다`() {
        val broken = Recorder("고장", failing = true)
        val healthy = Recorder("정상")
        install(broken, healthy)

        authState.value = AuthState.Authenticated("uuid-1")
        Analytics.track(AnalyticsEvent.LoginCompleted)

        assertEquals(listOf("identify uuid-1", "track complete_login {}"), healthy.calls)
    }

    @Test
    fun `앱 시작 때의 미로그인에는 reset 하지 않고 로그인에서 빠져나올 때만 한다`() {
        val recorder = Recorder("A")
        install(recorder)

        authState.value = AuthState.Unauthenticated
        assertEquals(emptyList<String>(), recorder.calls)

        authState.value = AuthState.Authenticated("uuid-1")
        authState.value = AuthState.Unauthenticated
        // 이미 끊긴 뒤의 미로그인은 또 끊지 않는다
        authState.value = AuthState.Unknown
        authState.value = AuthState.Unauthenticated

        assertEquals(listOf("identify uuid-1", "reset"), recorder.calls)
    }

    @Test
    fun `식별 전에 온 프로필 값은 쥐고 있다가 identify 직후에 보낸다`() {
        val recorder = Recorder("A")
        install(recorder)

        Analytics.track(AnalyticsEvent.SignupCompleted(Gender.FEMALE, ageBand = "20s"))
        assertEquals(listOf("track complete_signup {gender=female, age_band=20s}"), recorder.calls)

        authState.value = AuthState.Authenticated("uuid-1")

        assertEquals(
            listOf(
                "track complete_signup {gender=female, age_band=20s}",
                "identify uuid-1",
                "profile {gender=female, age_band=20s}",
            ),
            recorder.calls,
        )
    }

    @Test
    fun `식별된 뒤의 프로필 값은 바로 보낸다`() {
        val recorder = Recorder("A")
        install(recorder)
        authState.value = AuthState.Authenticated("uuid-1")

        Analytics.track(AnalyticsEvent.SignupCompleted(Gender.MALE, ageBand = "30s"))

        assertEquals(
            listOf(
                "identify uuid-1",
                "track complete_signup {gender=male, age_band=30s}",
                "profile {gender=male, age_band=30s}",
            ),
            recorder.calls,
        )
    }

    @Test
    fun `식별되지 못한 프로필 값은 다음 사람에게 붙지 않는다`() {
        val recorder = Recorder("A")
        install(recorder)

        Analytics.track(AnalyticsEvent.SignupCompleted(Gender.MALE, ageBand = "30s"))
        authState.value = AuthState.Unauthenticated
        authState.value = AuthState.Authenticated("uuid-2")

        assertEquals(
            listOf("track complete_signup {gender=male, age_band=30s}", "identify uuid-2"),
            recorder.calls,
        )
    }
}
