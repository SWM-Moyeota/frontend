package com.moyeota.presentation.feature.auth

import com.moyeota.domain.model.AuthError
import com.moyeota.domain.model.AuthException
import com.moyeota.domain.model.AuthState
import com.moyeota.domain.model.NewUser
import com.moyeota.domain.model.ProfileImage
import com.moyeota.domain.model.UserProfileInfo
import com.moyeota.domain.repository.AuthRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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
 * 10 프로필 만들기의 **누르기 전 가입 여부 선확인**(POST /api/v1/auth/phone/check)이
 * [PhoneVerifyViewModel] 안에서 언제·몇 번 불리는가.
 *
 * 순수 전이는 `PhoneVerifyStateTest` 가 본다. 여기서만 볼 수 있는 건 셋이다:
 * - **몇 번 묻는가** — 선확인 경로는 무인증·무쿨다운·무레이트리밋이라(실측 2026-11-01, 로컬 :8080)
 *   앱이 스스로 묶지 않으면 타자마다 서버 왕복이 쌓인다. 디바운스·직전 요청 취소·11자리 조건이
 *   함께 걸려 있는지는 상태 함수로 못 박을 수 없다.
 * - **`exists=false` 에 아무 일도 안 하는가** — 이게 이 작업의 가장 중요한 계약이다(아래 테스트 KDoc).
 * - **실패를 사용자에게 알리지 않는가**.
 *
 * 화면은 그리지 않는다 — 이 모듈에는 Robolectric 도 compose-ui-test 도 없다.
 * `viewModelScope` 를 돌리기 위해 Main 디스패처만 바꾼다(`ProfileEditSaveTest` 와 같은 장치).
 */
// `Dispatchers.setMain`·`resetMain`·`advanceUntilIdle`·`advanceTimeBy` 는 아직 opt-in 이 필요하다
// (kotlinx-coroutines-test 1.10.2). 테스트 전용이고 대체 API 가 없어 클래스 단위로 받는다.
@OptIn(ExperimentalCoroutinesApi::class)
class PhonePrecheckTest {

    private val mainDispatcher = StandardTestDispatcher()

    /** 가입된 번호(실측: 로컬 DB 에 실제로 있는 번호와 같은 모양). 값 자체에 의미는 없다. */
    private val PHONE = "01012345678"

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * 11자리가 채워지기 전에는 묻지 않고, 채워진 뒤에도 **입력이 멈출 때까지** 기다린다.
     *
     * 타자마다 부르면 번호 하나에 왕복이 열한 번 쌓이고, 무레이트리밋 경로라 서버가 막아 주지도 않는다.
     */
    @Test
    fun `11자리가 되기 전에는 가입 여부를 묻지 않는다`() = runTest(mainDispatcher) {
        val repository = FakePhonePrecheckRepository()
        val viewModel = PhoneVerifyViewModel(repository)

        "0101234567".forEachIndexed { index, _ ->
            viewModel.onPhoneDigitsChanged("0101234567".take(index + 1))
        }
        advanceUntilIdle()

        assertEquals(emptyList<String>(), repository.asked)
    }

    @Test
    fun `입력이 멈춘 뒤에 묻는다`() = runTest(mainDispatcher) {
        val repository = FakePhonePrecheckRepository()
        val viewModel = PhoneVerifyViewModel(repository)

        viewModel.onPhoneDigitsChanged(PHONE)
        // 디바운스(500ms)가 끝나기 전 — 아직 아무것도 묻지 않았다.
        advanceTimeBy(400)
        assertEquals(emptyList<String>(), repository.asked)

        advanceUntilIdle()
        assertEquals(listOf(PHONE), repository.asked)
    }

    /**
     * 다음 타자가 들어오면 직전 조회를 취소한다 — 번호를 고치는 동안 왕복이 쌓이지 않고,
     * 늦게 도착한 옛 응답이 최신 입력에 문구를 띄우지도 않는다.
     */
    @Test
    fun `번호를 고치면 직전 조회를 취소하고 마지막 번호만 묻는다`() = runTest(mainDispatcher) {
        val repository = FakePhonePrecheckRepository(registered = true)
        val viewModel = PhoneVerifyViewModel(repository)

        viewModel.onPhoneDigitsChanged(PHONE)
        advanceTimeBy(300)
        viewModel.onPhoneDigitsChanged("0101234567") // 한 자 지웠다 — 11자리가 아니라 묻지 않는다
        viewModel.onPhoneDigitsChanged("01012345679")
        advanceUntilIdle()

        assertEquals(listOf("01012345679"), repository.asked)
    }

    /**
     * 가입된 번호면 **버튼을 누르기 전에** 기존 안내가 뜬다 — 문구도, 잠금도, 「로그인하러 가기」도
     * 이미 있는 `alreadyRegistered` 경로를 그대로 쓴다(새 상태·새 문구를 만들지 않는다).
     */
    @Test
    fun `가입된 번호면 누르기 전에 안내가 뜨고 버튼이 잠긴다`() = runTest(mainDispatcher) {
        val repository = FakePhonePrecheckRepository(registered = true)
        val viewModel = PhoneVerifyViewModel(repository)

        viewModel.onPhoneDigitsChanged(PHONE)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.alreadyRegistered)
        assertEquals(authErrorMessage(AuthError.PHONE_NUMBER_DUPLICATED), state.phoneMessage)
        assertEquals(PhoneVerifyStage.Idle, state.stage)
        assertFalse(state.canRequestCode(PHONE, System.currentTimeMillis()))
    }

    /**
     * ★★ 이 작업의 핵심 계약 — **`exists=false` 에는 아무 말도 하지 않는다.**
     *
     * 실측(2026-11-01, 로컬 :8080): 탈퇴 후 재가입 제한 기간(30일)인 번호도 선확인에서는
     * `{"exists":false}` 200 이다(서버가 활성 프로필 한 테이블만 보고 탈퇴 기록을 보지 않는다).
     * 같은 번호의 `POST /auth/sms/send` 는 409 `USER109` 다.
     *
     * 그러므로 false 를 「가입 가능」으로 읽어 「사용할 수 있어요」 같은 문구를 띄우거나 무언가를
     * 열어 주면, 사용자는 안심한 뒤 눌러서 「30일 동안 가입 불가」를 듣는다 —
     * **선확인을 붙인 탓에 지금보다 나빠지는 유일한 경로다.**
     * 상태가 초기값과 **글자 하나 다르지 않아야** 한다는 것으로 그걸 못 박는다.
     */
    @Test
    fun `가입된 번호가 아니면 아무 말도 하지 않는다`() = runTest(mainDispatcher) {
        val repository = FakePhonePrecheckRepository(registered = false)
        val viewModel = PhoneVerifyViewModel(repository)

        viewModel.onPhoneDigitsChanged(PHONE)
        advanceUntilIdle()

        assertEquals(listOf(PHONE), repository.asked) // 묻기는 했다
        assertEquals(PhoneVerifyState(), viewModel.state.value) // 그런데 아무것도 바뀌지 않았다
        assertNull(viewModel.state.value.phoneMessage)
        assertTrue(viewModel.state.value.canRequestCode(PHONE, System.currentTimeMillis()))
    }

    /**
     * 확인 실패는 사용자가 고칠 수 있는 게 아니다 — 알리지도, 「다음」을 막지도 않는다
     * (닉네임 쪽 [NicknameCheckState.Unknown] 과 같은 판단). 최종 판정은 발송의 409 다.
     */
    @Test
    fun `선확인이 실패해도 사용자에게 알리지 않는다`() = runTest(mainDispatcher) {
        val repository = FakePhonePrecheckRepository(
            failure = AuthException(AuthError.NETWORK, "네트워크 오류"),
        )
        val viewModel = PhoneVerifyViewModel(repository)

        viewModel.onPhoneDigitsChanged(PHONE)
        advanceUntilIdle()

        assertEquals(PhoneVerifyState(), viewModel.state.value)
        assertTrue(viewModel.state.value.canRequestCode(PHONE, System.currentTimeMillis()))
    }

    /**
     * ★ 사용자가 디바운스가 끝나는 순간 「인증번호 받기」를 누르면 두 경로가 겹친다.
     *
     * 늦게 온 선확인이 그대로 적용되면 진행 중인 발송 위에 「이미 가입된 번호」가 뜨고
     * 진행 표시가 되돌려진다 — 발송은 아직 응답도 오지 않았는데.
     */
    @Test
    fun `발송을 누른 뒤 도착한 선확인은 진행 중인 발송을 덮지 않는다`() = runTest(mainDispatcher) {
        // 발송은 끝나지 않는다 — "요청이 날아간 상태"를 그대로 붙잡아 둔다.
        val repository = FakePhonePrecheckRepository(registered = true, blockSend = true)
        val viewModel = PhoneVerifyViewModel(repository)

        viewModel.onPhoneDigitsChanged(PHONE)
        viewModel.requestCode(PHONE)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue("발송 진행 표시가 되돌려졌다", state.requesting)
        assertFalse("진행 중인 발송 위에 「이미 가입된 번호」가 떴다", state.alreadyRegistered)
        assertNull(state.phoneMessage)
    }
}

/**
 * 선확인 한 가지만 기록하는 페이크. 나머지 메서드는 이 블록에서 불리면 **테스트가 터져야 한다** —
 * 조용히 기본값을 돌려주면 「선확인이 엉뚱한 API 를 부른다」는 사고가 통과한다.
 *
 * @param registered 서버의 `exists` 값
 * @param failure 던질 예외(네트워크·서버 오류 흉내)
 * @param blockSend 문자 발송을 **끝내지 않는다** — 발송이 날아간 상태를 붙잡아 두기 위한 것이다.
 */
private class FakePhonePrecheckRepository(
    private val registered: Boolean = false,
    private val failure: Throwable? = null,
    private val blockSend: Boolean = false,
) : AuthRepository {

    override val authState: StateFlow<AuthState> = MutableStateFlow(AuthState.Unauthenticated)

    /** 선확인으로 **실제로 서버에 물은 번호**를 순서대로 담는다. 길이가 곧 왕복 횟수다. */
    val asked = mutableListOf<String>()

    private val neverSent = CompletableDeferred<Unit>()

    override suspend fun isPhoneRegistered(phoneNumber: String): Boolean {
        asked += phoneNumber
        failure?.let { throw it }
        return registered
    }

    override suspend fun sendPhoneVerificationCode(phoneNumber: String) {
        if (blockSend) neverSent.await()
    }

    override suspend fun verifyPhoneCode(phoneNumber: String, code: String): String =
        throw UnsupportedOperationException("선확인과 무관하다")

    override suspend fun isNicknameTaken(nickname: String): Boolean =
        throw UnsupportedOperationException("닉네임은 NicknameCheckViewModel 이 묻는다")

    override suspend fun register(newUser: NewUser): String =
        throw UnsupportedOperationException("가입 제출은 12 매너 서약의 몫이다")

    override suspend fun login(loginId: String, password: String): String =
        throw UnsupportedOperationException("선확인과 무관하다")

    override suspend fun logout(): Unit =
        throw UnsupportedOperationException("선확인과 무관하다")

    override suspend fun updateProfile(nickname: String?, imageKey: String?): Unit =
        throw UnsupportedOperationException("선확인과 무관하다")

    override suspend fun getProfileImages(): List<ProfileImage> =
        throw UnsupportedOperationException("선확인과 무관하다")

    override suspend fun resetProfileImage(): Unit =
        throw UnsupportedOperationException("선확인과 무관하다")

    override suspend fun deleteAccount(password: String): Unit =
        throw UnsupportedOperationException("선확인과 무관하다")

    override suspend fun getMyProfile(): UserProfileInfo =
        throw UnsupportedOperationException("선확인과 무관하다")
}
