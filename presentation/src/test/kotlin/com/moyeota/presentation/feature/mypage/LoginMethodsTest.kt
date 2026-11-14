package com.moyeota.presentation.feature.mypage

import com.moyeota.domain.model.*
import com.moyeota.domain.repository.AuthRepository
import com.moyeota.presentation.analytics.AnalyticsRecorderRule
import com.moyeota.presentation.analytics.FakeAuthRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LoginMethodsTest {
    @get:Rule val rule = AnalyticsRecorderRule()
    private var time = 0L
    private var links = 0
    private var sends = 0
    private var fail: AuthError? = null
    private val repo = object : AuthRepository by FakeAuthRepository() {
        override suspend fun getLoginMethods() = LoginMethods(false, if (links > 0) listOf(LoginMethod(SocialProvider.GOOGLE, LoginMethodStatus.ACTIVE)) else emptyList())
        override suspend fun sendLoginMethodCode() { sends++ }
        override suspend fun verifyLoginMethodCode(code: String) = LinkVerification("member-bound-proof", 300)
        override suspend fun linkGoogle(idToken: String, verificationToken: String) {
            assertEquals("google-proof", idToken)
            assertEquals("member-bound-proof", verificationToken)
            fail?.let { throw AuthException(it) }
            links++
        }
    }
    private fun verified(): LoginMethodsViewModel = LoginMethodsViewModel(repo, { time }).also {
        it.sendCode(); it.changeCode("123456"); it.verifyCode()
    }
    @Test fun `문자 인증 전에는 공급자 토큰이 있어도 연결하지 않는다`() = runBlocking {
        LoginMethodsViewModel(repo, { time }).linkGoogle("google-proof")
        assertEquals(0, links)
    }
    @Test fun `연결 성공 후 목록 갱신하고 증표를 소진한다`() = runBlocking {
        val vm = verified()
        vm.linkGoogle("google-proof")
        assertEquals(SocialProvider.GOOGLE, vm.state.value.methods?.methods?.single()?.provider)
        assertFalse(vm.state.value.verified)
        vm.linkGoogle("google-proof")
        assertEquals(1, links)
    }
    @Test fun `5분이 지나면 연결을 보내지 않고 문자 인증으로 돌아간다`() = runBlocking {
        val vm = verified()
        time = 300_001
        vm.linkGoogle("google-proof")
        assertEquals(0, links)
        assertFalse(vm.state.value.verified)
        assertNotNull(vm.state.value.error)
    }
    @Test fun `서버가 증표 만료를 반환하면 재인증할 수 있다`() = runBlocking {
        val vm = verified(); fail = AuthError.SOCIAL_LINK_VERIFICATION_EXPIRED
        vm.linkGoogle("google-proof")
        assertFalse(vm.state.value.verified)
        assertFalse(vm.state.value.codeSent)
    }
    @Test fun `중복 연결은 계정 병합 없이 오류를 표시한다`() = runBlocking {
        val vm = verified(); fail = AuthError.SOCIAL_LINK_CONFLICT
        vm.linkGoogle("google-proof")
        assertEquals(0, links)
        assertTrue(vm.state.value.error!!.contains("다른 계정"))
    }
    @Test fun `재발송은 1분 쿨다운을 적용한다`() {
        val vm = LoginMethodsViewModel(repo, { time })
        vm.sendCode(); vm.sendCode()
        assertEquals(1, sends)
        time = 60_001; vm.sendCode()
        assertEquals(2, sends)
    }
    @Test fun `소셜 전용 회원 탈퇴는 비밀번호 없는 API를 사용한다`() {
        var socialDeletes = 0
        val deletionRepo = object : AuthRepository by repo {
            override suspend fun deleteSocialAccount() { socialDeletes++ }
            override suspend fun deleteAccount(password: String) { fail("로컬 탈퇴 호출") }
        }
        val vm = AccountDeleteViewModel(deletionRepo)
        assertEquals(false, vm.uiState.value.hasPassword)
        var completed = false
        vm.delete(onDeleted = { completed = true })
        assertTrue(completed)
        assertEquals(1, socialDeletes)
    }
}
