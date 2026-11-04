package com.moyeota.presentation.feature.auth

import com.moyeota.domain.analytics.AnalyticsEvent
import com.moyeota.domain.analytics.AnalyticsValue
import com.moyeota.domain.analytics.ageBand
import com.moyeota.domain.model.AuthError
import com.moyeota.domain.model.AuthException
import com.moyeota.domain.model.Gender
import com.moyeota.presentation.analytics.AnalyticsRecorderRule
import com.moyeota.presentation.analytics.FakeAuthRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

/** 12 매너 서약 — `complete_signup` 은 가입 **그리고** 자동 로그인까지 성공했을 때 한 번만. */
class SignupAnalyticsTest {

    @get:Rule val rule = AnalyticsRecorderRule()

    private val birth = LocalDate.of(2001, 6, 15)
    private val draft = SignupDraft(
        email = "a@b.c", nickname = "모여타", name = "홍길동", phoneNumber = "01012345678",
        birthDate = birth, gender = Gender.FEMALE, loginId = "moyeota", password = "pw123456!",
        verificationToken = "token",
    )

    @Test
    fun `가입은 됐는데 로그인이 실패하면 보내지 않는다`() {
        val repo = FakeAuthRepository().apply { onLogin = { _, _ -> throw IOException() } }
        SignupViewModel(repo).submit(draft)
        assertEquals(emptyList<String>(), rule.names())
    }

    @Test
    fun `가입이 실패하면 보내지 않는다`() {
        val repo = FakeAuthRepository().apply { onRegister = { throw AuthException(AuthError.LOGIN_ID_DUPLICATED) } }
        SignupViewModel(repo).submit(draft)
        assertEquals(emptyList<String>(), rule.names())
    }

    @Test
    fun `휴대폰 인증이 거절되면 보내지 않는다`() {
        val repo = FakeAuthRepository().apply { onRegister = { throw AuthException(AuthError.PHONE_NOT_VERIFIED) } }
        val vm = SignupViewModel(repo)
        vm.submit(draft)
        assertTrue(vm.uiState.value.phoneNotVerified)
        assertEquals(emptyList<String>(), rule.names())
    }

    @Test
    fun `둘 다 성공하면 성별과 연령대로 1번`() {
        SignupViewModel(FakeAuthRepository()).submit(draft)
        assertEquals(listOf(AnalyticsEvent.SignupCompleted(Gender.FEMALE, ageBand(birth))), rule.recorded)
        assertEquals(AnalyticsValue.Str("female"), rule.recorded.single().properties["gender"])
    }

    @Test
    fun `성별 밝히지 않음은 undisclosed 로 싣는다`() {
        SignupViewModel(FakeAuthRepository()).submit(draft.copy(gender = Gender.UNDISCLOSED))
        assertEquals(AnalyticsValue.Str("undisclosed"), rule.recorded.single().properties["gender"])
    }

    @Test
    fun `로그인만 실패한 뒤 재시도로 로그인이 성공하면 그때 1번 - 가입은 다시 부르지 않는다`() {
        var failLogin = true
        val repo = FakeAuthRepository().apply {
            onLogin = { _, _ -> if (failLogin) throw IOException() else "uuid" }
        }
        val vm = SignupViewModel(repo)

        vm.submit(draft)
        failLogin = false
        vm.submit(draft)

        assertEquals(1, repo.registerCalls)
        assertEquals(2, repo.loginCalls)
        assertEquals(listOf("complete_signup"), rule.names())
    }
}
