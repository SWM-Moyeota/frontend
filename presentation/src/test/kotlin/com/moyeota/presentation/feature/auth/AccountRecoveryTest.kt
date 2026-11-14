package com.moyeota.presentation.feature.auth

import com.moyeota.domain.model.AuthError
import com.moyeota.domain.model.AuthException
import com.moyeota.domain.model.AuthPolicy
import com.moyeota.domain.model.AuthState
import com.moyeota.domain.model.NewUser
import com.moyeota.domain.model.PasswordResetTicket
import com.moyeota.domain.model.ProfileImage
import com.moyeota.domain.model.UserProfileInfo
import com.moyeota.domain.repository.AuthRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 04b 아이디 찾기 · 04c 비밀번호 재설정의 발송·확인·저장 흐름과 실패 해석을 화면 없이 확인한다.
 * iOS 의 같은 이름 테스트(frontend-ios #47)와 짝이다.
 *
 * **`advanceUntilIdle` 을 쓰지 않는다.** 뷰모델의 카운트다운 틱은 실제 시계(`System.currentTimeMillis`)로
 * 끝을 판단하는데 가상 시간은 그 시계를 움직이지 않는다 — 다 돌리려 들면 틱이 실제 3분 동안 돈다.
 * 지금 줄 선 일만 돌리는 `runCurrent` 로 충분하다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountRecoveryTest {

    private val mainDispatcher = StandardTestDispatcher()
    private val PHONE = "01012345678"

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun remaining(viewModel: RecoveryCodeViewModel) =
        viewModel.state.value.codeRemainingSeconds(viewModel.nowMillis.value)

    // ── 04b 아이디 찾기 ──

    @Test
    fun `아이디 찾기는 번호로 발송하고 6자리를 채우면 스스로 확인해 아이디를 얻는다`() = runTest(mainDispatcher) {
        val repository = FakeRecoveryRepository()
        val viewModel = RecoveryCodeViewModel(RecoveryPurpose.FIND_LOGIN_ID, repository)

        viewModel.requestCode(PHONE)
        assertTrue("태스크 시작을 기다리지 않고 켜져야 두 번 눌리지 않는다", viewModel.state.value.requesting)
        runCurrent()
        assertEquals(PhoneVerifyStage.CodeSent, viewModel.state.value.stage)
        assertEquals(AuthPolicy.SMS_CODE_TTL_SECONDS, remaining(viewModel))

        viewModel.onCodeChanged("123456")
        runCurrent()

        assertEquals(PhoneVerifyStage.Verified, viewModel.state.value.stage)
        assertEquals("moyeota_test", viewModel.state.value.token)
        assertEquals(listOf("find-send 01012345678", "find-verify 01012345678 123456"), repository.calls)
    }

    /** 가입 흐름의 「이미 가입된 번호」와 정반대다 — 입력칸을 열지 않고 가입으로 보낼 수 있어야 한다. */
    @Test
    fun `가입되지 않은 번호는 입력칸을 열지 않고 가입 길을 켠다`() = runTest(mainDispatcher) {
        val repository = FakeRecoveryRepository(sendFailure = AuthException(AuthError.PHONE_NOT_REGISTERED))
        val viewModel = RecoveryCodeViewModel(RecoveryPurpose.FIND_LOGIN_ID, repository)

        viewModel.requestCode(PHONE)
        runCurrent()

        assertEquals(PhoneVerifyStage.Idle, viewModel.state.value.stage)
        assertFalse(viewModel.state.value.requesting)
        assertEquals(authErrorMessage(AuthError.PHONE_NOT_REGISTERED), viewModel.state.value.phoneMessage)
        assertEquals(RecoveryRequestIssue.NOT_REGISTERED, viewModel.requestIssue.value)

        // 번호를 고치면 그 안내는 남의 번호 이야기가 된다.
        viewModel.onPhoneDigitsChanged("01099998888")
        assertNull(viewModel.requestIssue.value)
        assertEquals(PhoneVerifyState(), viewModel.state.value)
    }

    @Test
    fun `하루 한도 문구는 다른 번호로 가입하라고 하지 않는다`() = runTest(mainDispatcher) {
        val repository = FakeRecoveryRepository(sendFailure = AuthException(AuthError.SMS_DAILY_LIMIT_EXCEEDED))
        val viewModel = RecoveryCodeViewModel(RecoveryPurpose.FIND_LOGIN_ID, repository)

        viewModel.requestCode(PHONE)
        runCurrent()

        val message = viewModel.state.value.phoneMessage.orEmpty()
        assertTrue(viewModel.state.value.dailyLimitReached)
        assertFalse(message.contains("가입"))
        assertTrue(message.contains("내일"))
    }

    @Test
    fun `틀린 인증번호는 입력칸만 비우고 다시 받는다`() = runTest(mainDispatcher) {
        val repository = FakeRecoveryRepository(verifyFailure = AuthException(AuthError.SMS_CODE_MISMATCH))
        val viewModel = RecoveryCodeViewModel(RecoveryPurpose.FIND_LOGIN_ID, repository)

        viewModel.requestCode(PHONE)
        runCurrent()
        viewModel.onCodeChanged("000000")
        runCurrent()

        assertEquals(PhoneVerifyStage.CodeSent, viewModel.state.value.stage)
        assertEquals("", viewModel.state.value.code)
        assertEquals(authErrorMessage(AuthError.SMS_CODE_MISMATCH), viewModel.state.value.codeMessage)
        assertTrue("입력칸은 열려 있어야 한다", remaining(viewModel) > 0)
    }

    @Test
    fun `요청이 날아간 사이 번호를 고치면 늦게 온 응답을 버린다`() = runTest(mainDispatcher) {
        val repository = FakeRecoveryRepository(holdSend = true)
        val viewModel = RecoveryCodeViewModel(RecoveryPurpose.FIND_LOGIN_ID, repository)

        viewModel.requestCode(PHONE)
        runCurrent()
        viewModel.onPhoneDigitsChanged("0109999888")
        repository.sendGate.complete(Unit)
        runCurrent()

        assertEquals(PhoneVerifyState(), viewModel.state.value)
    }

    // ── 04c 비밀번호 재설정 ──

    @Test
    fun `재설정은 아이디와 번호로 요청하고 접수번호로 확인해 토큰을 얻는다`() = runTest(mainDispatcher) {
        val repository = FakeRecoveryRepository()
        val viewModel = RecoveryCodeViewModel(RecoveryPurpose.RESET_PASSWORD, repository)

        viewModel.requestCode(PHONE, "moyeota")
        runCurrent()
        assertEquals(PhoneVerifyStage.CodeSent, viewModel.state.value.stage)

        viewModel.onCodeChanged("123456")
        runCurrent()

        assertEquals(PhoneVerifyStage.Verified, viewModel.state.value.stage)
        assertEquals("reset-token", viewModel.state.value.token)
        assertEquals(listOf("reset-send moyeota 01012345678", "reset-verify req-1 123456"), repository.calls)
    }

    @Test
    fun `유효 시간과 재요청 간격은 서버가 준 값을 쓴다`() = runTest(mainDispatcher) {
        val repository = FakeRecoveryRepository(ticket = PasswordResetTicket("req-1", 90, 30))
        val viewModel = RecoveryCodeViewModel(RecoveryPurpose.RESET_PASSWORD, repository)

        viewModel.requestCode(PHONE, "moyeota")
        runCurrent()

        val state = viewModel.state.value
        assertEquals(90_000L - 30_000L, state.codeExpiresAt - state.resendAllowedAt)
        assertEquals(90, remaining(viewModel))
    }

    /** 접수는 「아이디 + 번호」 한 쌍에 묶여 있다 — 옛 아이디로 받은 인증번호로 확인하면 바뀌는 건 옛 아이디의 비밀번호다. */
    @Test
    fun `인증번호를 받은 뒤 아이디를 고치면 인증을 버린다`() = runTest(mainDispatcher) {
        val repository = FakeRecoveryRepository()
        val viewModel = RecoveryCodeViewModel(RecoveryPurpose.RESET_PASSWORD, repository)

        viewModel.requestCode(PHONE, "moyeota")
        runCurrent()
        assertEquals(PhoneVerifyStage.CodeSent, viewModel.state.value.stage)

        viewModel.onLoginIdChanged("moyeota")
        assertEquals("같은 아이디는 건드리지 않는다", PhoneVerifyStage.CodeSent, viewModel.state.value.stage)

        viewModel.onLoginIdChanged("moyeota2")
        assertEquals(PhoneVerifyState(), viewModel.state.value)

        viewModel.onCodeChanged("123456")
        runCurrent()
        assertFalse(repository.calls.any { it.startsWith("reset-verify") })
    }

    /** 04b 에서 아이디를 찾자마자 같은 번호로 04c 를 열면 문자 쿨다운에 걸린다 — 가장 흔한 429 다. */
    @Test
    fun `재설정 요청 제한은 서버가 준 남은 시간만큼만 버튼을 잠근다`() = runTest(mainDispatcher) {
        val repository = FakeRecoveryRepository(
            sendFailure = AuthException(AuthError.PASSWORD_RESET_RATE_LIMITED, retryAfterSeconds = 23),
        )
        val viewModel = RecoveryCodeViewModel(RecoveryPurpose.RESET_PASSWORD, repository)

        viewModel.requestCode(PHONE, "moyeota")
        runCurrent()

        val state = viewModel.state.value
        assertEquals(23, state.resendRemainingSeconds(viewModel.nowMillis.value))
        assertTrue(state.phoneMessage.orEmpty().contains("방금"))
        assertFalse(state.dailyLimitReached)
    }

    /** 하루 횟수·계정당 제한은 몇 분~몇 시간이다 — 초를 세는 카운트다운을 걸면 「3600초 후 다시 받기」가 된다. */
    @Test
    fun `오래 걸리는 재설정 제한은 카운트다운 없이 버튼을 잠근다`() = runTest(mainDispatcher) {
        val repository = FakeRecoveryRepository(
            sendFailure = AuthException(AuthError.PASSWORD_RESET_RATE_LIMITED, retryAfterSeconds = 1_500),
        )
        val viewModel = RecoveryCodeViewModel(RecoveryPurpose.RESET_PASSWORD, repository)

        viewModel.requestCode(PHONE, "moyeota")
        runCurrent()

        val state = viewModel.state.value
        assertTrue(state.dailyLimitReached)
        assertEquals(0, state.resendRemainingSeconds(viewModel.nowMillis.value))
        assertTrue(state.phoneMessage.orEmpty().contains("약 25분"))
        assertFalse(state.canRequestCode(PHONE, viewModel.nowMillis.value))
    }

    @Test
    fun `재설정 제한은 남은 시간으로 갈린다`() {
        assertEquals(
            RecoveryRateLimit.ShortWait(60, authErrorMessage(AuthError.PASSWORD_RESET_RATE_LIMITED)),
            recoveryRateLimit(null),
        )
        assertEquals(60, (recoveryRateLimit(60) as RecoveryRateLimit.ShortWait).seconds)
        assertEquals(
            RecoveryRateLimit.LongWait("인증번호 요청이 너무 많았어요. 약 2분 뒤에 다시 시도해 주세요"),
            recoveryRateLimit(61),
        )
        assertEquals(
            RecoveryRateLimit.LongWait("인증번호 요청이 너무 많았어요. 약 1시간 뒤에 다시 시도해 주세요"),
            recoveryRateLimit(3_600),
        )
        assertEquals(
            RecoveryRateLimit.LongWait("인증번호 요청이 너무 많았어요. 약 23시간 뒤에 다시 시도해 주세요"),
            recoveryRateLimit(80_000),
        )
    }

    @Test
    fun `남은 시간을 모르는 재설정 제한은 재요청 버튼을 쿨다운으로 잠근다`() = runTest(mainDispatcher) {
        val repository = FakeRecoveryRepository(sendFailure = AuthException(AuthError.PASSWORD_RESET_RATE_LIMITED))
        val viewModel = RecoveryCodeViewModel(RecoveryPurpose.RESET_PASSWORD, repository)

        viewModel.requestCode(PHONE, "moyeota")
        runCurrent()

        val state = viewModel.state.value
        assertEquals(PhoneVerifyStage.Idle, state.stage)
        assertEquals(authErrorMessage(AuthError.PASSWORD_RESET_RATE_LIMITED), state.phoneMessage)
        assertFalse(state.canRequestCode(PHONE, viewModel.nowMillis.value))
        assertTrue(state.resendRemainingSeconds(viewModel.nowMillis.value) > 0)
    }

    /** 접수가 살아 있어 5번까지 다시 넣을 수 있다 — 입력칸을 닫으면 안 된다. */
    @Test
    fun `재설정 인증번호 400 은 입력칸을 비우고 다시 받는다`() = runTest(mainDispatcher) {
        val repository = FakeRecoveryRepository(verifyFailure = AuthException(AuthError.PASSWORD_RESET_CODE_REJECTED))
        val viewModel = RecoveryCodeViewModel(RecoveryPurpose.RESET_PASSWORD, repository)

        viewModel.requestCode(PHONE, "moyeota")
        runCurrent()
        viewModel.onCodeChanged("000000")
        runCurrent()

        assertEquals(PhoneVerifyStage.CodeSent, viewModel.state.value.stage)
        assertEquals("", viewModel.state.value.code)
        assertEquals(authErrorMessage(AuthError.PASSWORD_RESET_CODE_REJECTED), viewModel.state.value.codeMessage)
        assertTrue(remaining(viewModel) > 0)
    }

    @Test
    fun `재설정 인증번호를 5번 넘기면 입력칸을 닫고 재요청으로 보낸다`() = runTest(mainDispatcher) {
        val repository = FakeRecoveryRepository(verifyFailure = AuthException(AuthError.PASSWORD_RESET_RATE_LIMITED))
        val viewModel = RecoveryCodeViewModel(RecoveryPurpose.RESET_PASSWORD, repository)

        viewModel.requestCode(PHONE, "moyeota")
        runCurrent()
        viewModel.onCodeChanged("000000")
        runCurrent()

        assertEquals(0, remaining(viewModel))
        assertEquals(authErrorMessage(AuthError.PASSWORD_RESET_RATE_LIMITED), viewModel.state.value.codeMessage)
    }

    @Test
    fun `되돌리기는 인증을 버리고 사유를 번호 칸에 남긴다`() = runTest(mainDispatcher) {
        val viewModel = RecoveryCodeViewModel(RecoveryPurpose.RESET_PASSWORD, FakeRecoveryRepository())
        viewModel.requestCode(PHONE, "moyeota")
        runCurrent()
        viewModel.onCodeChanged("123456")
        runCurrent()
        assertEquals(PhoneVerifyStage.Verified, viewModel.state.value.stage)

        viewModel.restart("다시 인증해 주세요")

        assertEquals(PhoneVerifyState(phoneMessage = "다시 인증해 주세요"), viewModel.state.value)
    }

    // ── 새 비밀번호 저장 ──

    @Test
    fun `저장에 성공하면 completed 가 켜지고 값을 그대로 보낸다`() = runTest(mainDispatcher) {
        val repository = FakeRecoveryRepository()
        val viewModel = PasswordResetConfirmViewModel(repository)

        viewModel.confirm("tok", "Passw0rd!", "Passw0rd!")
        assertTrue(viewModel.uiState.value.submitting)
        runCurrent()

        assertTrue(viewModel.uiState.value.completed)
        assertFalse(viewModel.uiState.value.submitting)
        assertEquals(listOf("reset-confirm tok Passw0rd! Passw0rd!"), repository.calls)
    }

    @Test
    fun `실패는 고칠 곳에 따라 다른 자리에 뜬다`() = runTest(mainDispatcher) {
        fun stateAfter(error: AuthError): PasswordResetConfirmViewModel.UiState {
            val viewModel = PasswordResetConfirmViewModel(FakeRecoveryRepository(confirmFailure = AuthException(error)))
            viewModel.confirm("tok", "Passw0rd!", "Passw0rd!")
            runCurrent()
            return viewModel.uiState.value
        }

        val same = stateAfter(AuthError.SAME_PASSWORD)
        assertEquals(authErrorMessage(AuthError.SAME_PASSWORD), same.passwordError)
        assertNull(same.bannerMessage)
        assertFalse(same.submitting || same.completed)

        assertEquals(
            authErrorMessage(AuthError.PASSWORD_POLICY_VIOLATION),
            stateAfter(AuthError.PASSWORD_POLICY_VIOLATION).passwordError,
        )
        assertEquals(
            authErrorMessage(AuthError.PASSWORD_CONFIRM_MISMATCH),
            stateAfter(AuthError.PASSWORD_CONFIRM_MISMATCH).confirmError,
        )

        // 토큰 무효는 입력을 고쳐서 풀리지 않는다 — 인증으로 되돌리라는 신호만 싣는다.
        val token = stateAfter(AuthError.PASSWORD_RESET_TOKEN_INVALID)
        assertEquals(authErrorMessage(AuthError.PASSWORD_RESET_TOKEN_INVALID), token.reverifyMessage)
        assertNull(token.passwordError)
        assertNull(token.bannerMessage)

        assertEquals(authErrorMessage(AuthError.NETWORK), stateAfter(AuthError.NETWORK).bannerMessage)
    }

    @Test
    fun `입력을 고치면 서버 오류 문구를 지운다`() = runTest(mainDispatcher) {
        val viewModel = PasswordResetConfirmViewModel(
            FakeRecoveryRepository(confirmFailure = AuthException(AuthError.SAME_PASSWORD)),
        )
        viewModel.confirm("tok", "Passw0rd!", "Passw0rd!")
        runCurrent()
        assertTrue(viewModel.uiState.value.passwordError != null)

        viewModel.onInputChanged()

        assertEquals(PasswordResetConfirmViewModel.UiState(), viewModel.uiState.value)
    }

    @Test
    fun `재설정 실패 코드는 화면 대응이 같은 전이로 옮긴다`() {
        assertEquals(
            AuthError.SMS_COOLDOWN,
            recoveryTransitionError(AuthError.PASSWORD_RESET_RATE_LIMITED, verifying = false),
        )
        assertEquals(
            AuthError.SMS_TOO_MANY_ATTEMPTS,
            recoveryTransitionError(AuthError.PASSWORD_RESET_RATE_LIMITED, verifying = true),
        )
        assertEquals(
            AuthError.SMS_CODE_MISMATCH,
            recoveryTransitionError(AuthError.PASSWORD_RESET_CODE_REJECTED, verifying = true),
        )
        assertEquals(AuthError.NETWORK, recoveryTransitionError(AuthError.NETWORK, verifying = true))
    }
}

private class FakeRecoveryRepository(
    private val sendFailure: Throwable? = null,
    private val verifyFailure: Throwable? = null,
    private val confirmFailure: Throwable? = null,
    private val holdSend: Boolean = false,
    private val ticket: PasswordResetTicket = PasswordResetTicket("req-1", 180, 60),
) : AuthRepository {

    override val authState: StateFlow<AuthState> = MutableStateFlow(AuthState.Unauthenticated)

    val calls = mutableListOf<String>()
    val sendGate = CompletableDeferred<Unit>()

    override suspend fun sendFindLoginIdCode(phoneNumber: String) {
        calls += "find-send $phoneNumber"
        if (holdSend) sendGate.await()
        sendFailure?.let { throw it }
    }

    override suspend fun findLoginId(phoneNumber: String, code: String): String {
        calls += "find-verify $phoneNumber $code"
        verifyFailure?.let { throw it }
        return "moyeota_test"
    }

    override suspend fun requestPasswordReset(loginId: String, phoneNumber: String): PasswordResetTicket {
        calls += "reset-send $loginId $phoneNumber"
        if (holdSend) sendGate.await()
        sendFailure?.let { throw it }
        return ticket
    }

    override suspend fun verifyPasswordResetCode(requestId: String, code: String): String {
        calls += "reset-verify $requestId $code"
        verifyFailure?.let { throw it }
        return "reset-token"
    }

    override suspend fun confirmPasswordReset(resetToken: String, newPassword: String, newPasswordConfirm: String) {
        calls += "reset-confirm $resetToken $newPassword $newPasswordConfirm"
        confirmFailure?.let { throw it }
    }

    private fun unused(): Nothing = throw UnsupportedOperationException("아이디 찾기·재설정과 무관하다")

    override suspend fun sendPhoneVerificationCode(phoneNumber: String): Unit = unused()
    override suspend fun verifyPhoneCode(phoneNumber: String, code: String): String = unused()
    override suspend fun register(newUser: NewUser): String = unused()
    override suspend fun isNicknameTaken(nickname: String): Boolean = unused()
    override suspend fun isPhoneRegistered(phoneNumber: String): Boolean = unused()
    override suspend fun updateProfile(nickname: String?, imageKey: String?): Unit = unused()
    override suspend fun getProfileImages(): List<ProfileImage> = unused()
    override suspend fun resetProfileImage(): Unit = unused()
    override suspend fun login(loginId: String, password: String): String = unused()
    override suspend fun logout(): Unit = unused()
    override suspend fun deleteAccount(password: String): Unit = unused()
    override suspend fun loginWithGoogle(idToken: String): com.moyeota.domain.model.GoogleLoginResult = unused()
    override suspend fun registerSocial(newUser: com.moyeota.domain.model.NewSocialUser): String = unused()
    override suspend fun deleteSocialAccount(): Unit = unused()
    override suspend fun getMyProfile(): UserProfileInfo = unused()
}
