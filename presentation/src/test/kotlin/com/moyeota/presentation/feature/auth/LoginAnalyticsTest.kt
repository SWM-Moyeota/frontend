package com.moyeota.presentation.feature.auth

import com.moyeota.domain.analytics.AnalyticsEvent
import com.moyeota.domain.model.AuthError
import com.moyeota.domain.model.AuthException
import com.moyeota.presentation.analytics.AnalyticsRecorderRule
import com.moyeota.presentation.analytics.FakeAuthRepository
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** 04a 아이디 로그인 — `complete_login` 은 성공 뒤에만. */
class LoginAnalyticsTest {

    @get:Rule val rule = AnalyticsRecorderRule()

    @Test
    fun `성공하면 1번`() {
        LoginViewModel(FakeAuthRepository()).login("moyeota", "pw")
        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.LoginCompleted), rule.recorded)
    }

    @Test
    fun `실패하면 보내지 않는다`() {
        val repo = FakeAuthRepository().apply { onLogin = { _, _ -> throw AuthException(AuthError.LOGIN_FAILED) } }
        LoginViewModel(repo).login("moyeota", "wrong")
        assertEquals(emptyList<AnalyticsEvent>(), rule.recorded)
    }
}
