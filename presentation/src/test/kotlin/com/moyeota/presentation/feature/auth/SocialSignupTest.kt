package com.moyeota.presentation.feature.auth

import com.moyeota.domain.model.*
import com.moyeota.domain.repository.AuthRepository
import com.moyeota.presentation.analytics.AnalyticsRecorderRule
import com.moyeota.presentation.analytics.FakeAuthRepository
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class SocialSignupTest {
    @get:Rule val rule = AnalyticsRecorderRule()
    private val draft = SignupDraft(nickname = "모여타", name = "홍길동", phoneNumber = "01012345678",
        birthDate = LocalDate.of(2000, 1, 1), gender = Gender.UNDISCLOSED, verificationToken = "sms-proof")

    @Test fun `아이디 비밀번호 이메일 없이 소셜 가입하고 로컬 로그인은 호출하지 않는다`() {
        val local = FakeAuthRepository()
        var sent: NewSocialUser? = null
        val repo = object : AuthRepository by local {
            override suspend fun registerSocial(newUser: NewSocialUser): String { sent = newUser; return "same-user" }
        }
        val vm = SignupViewModel(repo)
        vm.submit(draft, "signup-proof")
        assertTrue(vm.uiState.value.done)
        assertEquals("signup-proof", sent?.signupToken)
        assertEquals("sms-proof", sent?.verificationToken)
        assertEquals(0, local.loginCalls)
        assertEquals(0, local.registerCalls)
        assertNull(draft.toNewUser())
    }
    @Test fun `가입 대기표 만료는 문자 재인증 대신 구글 로그인 재시작으로 분기한다`() {
        val repo = object : AuthRepository by FakeAuthRepository() {
            override suspend fun registerSocial(newUser: NewSocialUser): String = throw AuthException(AuthError.SOCIAL_SIGNUP_EXPIRED)
        }
        val vm = SignupViewModel(repo)
        vm.submit(draft, "expired")
        assertTrue(vm.uiState.value.socialExpired)
        assertFalse(vm.uiState.value.phoneNotVerified)
        assertFalse(vm.uiState.value.done)
    }
    @Test fun `문자 인증 없는 소셜 가입은 전송하지 않는다`() {
        assertNull(draft.copy(verificationToken = "").toNewSocialUser("proof"))
        assertNull(draft.toNewSocialUser(""))
    }
    @Test fun `메모리 초안 제거 후 대기표와 개인정보가 남지 않는다`() {
        val state = SocialSignupState()
        state.begin(GoogleLoginResult.SignupRequired("proof", null, null))
        state.draft = draft
        state.clear()
        assertNull(state.ticket)
        assertEquals(SignupDraft(), state.draft)
    }
}
