package com.moyeota.presentation.feature.mypage

import com.moyeota.domain.model.AuthError
import com.moyeota.domain.model.AuthException
import com.moyeota.domain.model.AuthState
import com.moyeota.domain.model.NewUser
import com.moyeota.domain.model.ProfileImage
import com.moyeota.domain.model.UserProfileInfo
import com.moyeota.domain.repository.AuthRepository
import com.moyeota.presentation.feature.auth.NicknameCheckState
import com.moyeota.presentation.feature.auth.authUserMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 36 프로필 수정이 **무엇을 서버에 보내는가** — [ProfileEditViewModel.save] 가 고른 값을
 * Repository 까지 실제로 넘기는지 본다.
 *
 * 이 테스트가 따로 있어야 하는 이유: `ProfileSaveRulesTest` 는 「저장」 버튼이 켜지는가(순수 게이트)
 * 까지만 보고, data 쪽 `RemoteAuthRepositoryTest.이미지 키만 바꿀 때 닉네임을 싣지 않는다` 는
 * 「Repository 에 들어온 imageKey 가 PATCH 본문에 실리는가」까지만 본다. **그 둘 사이가 빈다** —
 * 화면이 애초에 `imageKey = null` 로 부르면 양쪽 테스트가 다 통과하면서 사진은 저장되지 않는다.
 * 그게 이번 작업 전 이 화면의 실제 결함이었다.
 *
 * 특히 세 가지를 못 박는다:
 * - 고른 key 가 **그 값 그대로** PATCH 로 간다(URL 로 바뀌면 400 `USER112` 다)
 * - 되돌리기는 **PATCH 가 아니라 DELETE** 다. `updateProfile(imageKey = null)` 은 서버에서
 *   「건드리지 마라」라서, 되돌리기를 그렇게 부르면 아무 일도 일어나지 않는다
 * - 되돌리기와 닉네임을 함께 저장할 때 **PATCH 가 먼저** 나간다(닉네임이 409 로 실패했을 때
 *   사진만 기본으로 돌아간 상태가 남지 않게)
 *
 * 화면을 그리지는 않는다 — 이 모듈에는 Robolectric 도 compose-ui-test 도 없다. 여기서 보는 건
 * ViewModel 과 Repository 사이의 호출뿐이고, `viewModelScope` 를 돌리기 위해 Main 디스패처만 바꾼다.
 */
// `Dispatchers.setMain`·`resetMain`·`advanceUntilIdle` 은 아직 opt-in 이 필요하다
// (kotlinx-coroutines-test 1.10.2). 테스트 전용이고 대체 API 가 없어 클래스 단위로 받는다.
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileEditSaveTest {

    /**
     * `viewModelScope` 는 `Dispatchers.Main.immediate` 를 쓴다 — 바꿔 두지 않으면 ViewModel 생성
     * 시점의 `init { loadImages() }` 부터 터진다. [runTest] 에 같은 디스패처를 넘겨 스케줄러를
     * 공유하므로 `advanceUntilIdle()` 이 ViewModel 안의 코루틴까지 끝까지 돌린다.
     */
    private val mainDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `고른 이미지의 key 를 그대로 PATCH 에 싣는다`() = runTest(mainDispatcher) {
        val repository = FakeAuthRepository()
        val viewModel = ProfileEditViewModel(repository)
        viewModel.prefill(name = "성윤", imageUrl = preset("07"))
        advanceUntilIdle()

        viewModel.onImagePick("profile/presets/03.png")
        viewModel.save()
        advanceUntilIdle()

        // 닉네임은 건드리지 않았으니 null 이고, key 는 목록이 준 값 그대로여야 한다.
        assertEquals(listOf<Pair<String?, String?>>(null to "profile/presets/03.png"), repository.patches)
        // 사진을 고른 저장은 되돌리기가 아니다 — DELETE 가 함께 나가면 방금 고른 사진이 지워진다.
        assertEquals(0, repository.resetCount)
        assertTrue(viewModel.uiState.value.saved)
    }

    @Test
    fun `되돌리기는 PATCH 가 아니라 DELETE 로 나간다`() = runTest(mainDispatcher) {
        val repository = FakeAuthRepository()
        val viewModel = ProfileEditViewModel(repository)
        viewModel.prefill(name = "성윤", imageUrl = preset("03"))
        advanceUntilIdle()

        viewModel.onImageReset()
        viewModel.save()
        advanceUntilIdle()

        // PATCH 는 **아예 나가지 않는다.** `updateProfile(null, null)` 로 부르면 서버는 204 를
        // 주면서 아무것도 바꾸지 않아, 사용자에게는 「저장했는데 사진이 그대로」로 보인다.
        assertEquals(emptyList<Pair<String?, String?>>(), repository.patches)
        assertEquals(1, repository.resetCount)
        assertTrue(viewModel.uiState.value.saved)
    }

    /**
     * 기본 이미지를 쓰는 사람에게 「되돌리기」는 **「고른 것 취소」** 다 — 서버 상태가 이미 기본이라
     * 되돌릴 것이 없다. 그 경우 `onImageReset` 이 [ImageIntent.Reset] 을 세우면 화면이 거짓말을 한다:
     * 선택 링이 풀리고 되돌리기 줄도 사라져 **진입 직후와 구분되지 않는데** 「저장」만 켜진 채 남고,
     * 누르면 아무것도 바꾸지 않는 DELETE 가 나가고 「저장했다」며 35 로 돌아간다.
     *
     * 리뷰에서 실제로 잡힌 결함이라 회귀 테스트로 남긴다.
     */
    @Test
    fun `기본 이미지 사용자가 고른 뒤 되돌리면 저장이 꺼지고 아무 요청도 나가지 않는다`() = runTest(mainDispatcher) {
        val repository = FakeAuthRepository()
        val viewModel = ProfileEditViewModel(repository)
        // 한 번도 고른 적 없는 사용자 — 서버는 그래도 기본 이미지의 전체 URL 을 준다
        viewModel.prefill(name = "성윤", imageUrl = "https://cdn.moyeota.co.kr/profile/presets/default.png")
        advanceUntilIdle()

        viewModel.onImagePick("profile/presets/07.png")
        assertTrue("고르면 저장이 켜져야 한다", viewModel.uiState.value.canSaveNow())

        viewModel.onImageReset()

        assertFalse("취소했으니 저장이 꺼져야 한다", viewModel.uiState.value.canSaveNow())
        assertEquals(ImageIntent.None, viewModel.uiState.value.imageIntent)

        viewModel.save()
        advanceUntilIdle()

        assertEquals(emptyList<Pair<String?, String?>>(), repository.patches)
        assertEquals("바꿀 게 없으니 DELETE 도 나가면 안 된다", 0, repository.resetCount)
    }

    /**
     * 반대 경우 — 프리셋을 쓰던 사람의 되돌리기는 진짜 되돌리기다. 위 테스트의 수정이
     * 이쪽을 [ImageIntent.None] 으로 접어 버리지 않았는지 함께 못 박는다.
     */
    @Test
    fun `프리셋을 쓰던 사용자의 되돌리기는 DELETE 로 나간다`() = runTest(mainDispatcher) {
        val repository = FakeAuthRepository()
        val viewModel = ProfileEditViewModel(repository)
        viewModel.prefill(name = "성윤", imageUrl = preset("03"))
        advanceUntilIdle()

        viewModel.onImageReset()

        assertEquals(ImageIntent.Reset, viewModel.uiState.value.imageIntent)
        assertTrue("바뀌는 게 있으니 저장이 켜져야 한다", viewModel.uiState.value.canSaveNow())

        viewModel.save()
        advanceUntilIdle()

        assertEquals(1, repository.resetCount)
    }

    /**
     * **로그인 직후** 이 화면을 열면 프로필 조회가 아직 안 끝나 `prefill(null, null)` 이 먼저 온다.
     * 예전에는 그 호출이 시드를 태워 버려, 뒤이어 이름이 도착해도 입력란이 **영영 빈 채로** 남았다
     * — 사용자에게는 「닉네임이 없는 계정」으로 보인다. 실기 검증에서 잡힌 결함이라 회귀로 남긴다.
     */
    @Test
    fun `조회가 늦게 끝나도 도착한 닉네임으로 입력란을 채운다`() = runTest(mainDispatcher) {
        val viewModel = ProfileEditViewModel(FakeAuthRepository())

        // 1) 조회 전 — 화면이 Loading 상태의 UserNameState 로 먼저 그려진다
        viewModel.prefill(name = null, imageUrl = null)
        assertEquals("", viewModel.uiState.value.nickname)

        // 2) 조회가 끝나 이름이 도착한다
        viewModel.prefill(name = "테스터1", imageUrl = preset("03"))

        assertEquals("테스터1", viewModel.uiState.value.nickname)
        assertEquals("테스터1", viewModel.uiState.value.currentNickname)
    }

    /**
     * 시드를 「실제로 채웠을 때만」 태우는 수정이 **금칙어 가드를 뚫으면 안 된다.**
     * 서버는 금칙어를 검사하지 않으므로(길이·문자종류만 본다) 다른 경로로 가입한 사용자가
     * 「모여타」가 든 닉네임을 가질 수 있다. 그 이름을 입력란에 채우면 열자마자 형식 오류가 뜬다.
     * 비교 기준([UiState.currentNickname])에는 그대로 담아야 한다 — 안 그러면 「안 바꿨는데 바꾼 것」이 된다.
     */
    @Test
    fun `규칙에 안 맞는 표시 이름은 입력란에 채우지 않는다`() = runTest(mainDispatcher) {
        val viewModel = ProfileEditViewModel(FakeAuthRepository())

        viewModel.prefill(name = "모여타일", imageUrl = preset("03"))

        assertEquals("", viewModel.uiState.value.nickname)
        assertEquals("모여타일", viewModel.uiState.value.currentNickname)
    }

    /**
     * 반대 방향 — 시드는 **한 번만** 돈다. 저장 직후 재조회나 탭 복귀로 `prefill` 이 다시 와도
     * 사용자가 고치던 글자를 되돌리면 안 된다.
     */
    @Test
    fun `채운 뒤에는 재조회가 사용자 입력을 되돌리지 않는다`() = runTest(mainDispatcher) {
        val viewModel = ProfileEditViewModel(FakeAuthRepository())
        viewModel.prefill(name = "테스터1", imageUrl = preset("03"))

        viewModel.onNicknameChange("새이름")
        viewModel.prefill(name = "테스터1", imageUrl = preset("03"))

        assertEquals("새이름", viewModel.uiState.value.nickname)
        // 비교 기준은 **올 때마다** 갱신된다 — 안 그러면 방금 저장한 자기 닉네임이 「이미 사용 중」이 된다
        assertEquals("테스터1", viewModel.uiState.value.currentNickname)
    }

    /**
     * 화면이 「저장」 활성을 계산하는 것과 **같은 식**을 쓴다(`ProfileEditScreen` 이
     * [canSaveProfile] 을 그대로 부른다). 여기서 따로 판단하면 테스트와 화면이 갈린다.
     */
    private fun ProfileEditViewModel.UiState.canSaveNow(): Boolean =
        canSaveProfile(nickname, currentNickname, check, imageIntent, saving)

    @Test
    fun `닉네임과 사진을 함께 바꾸면 한 번의 PATCH 에 둘 다 싣는다`() = runTest(mainDispatcher) {
        val repository = FakeAuthRepository()
        val viewModel = ProfileEditViewModel(repository)
        viewModel.prefill(name = "성윤", imageUrl = preset("03"))
        advanceUntilIdle()

        viewModel.onNicknameChange("윤성")
        viewModel.onImagePick("profile/presets/09.png")
        viewModel.save()
        advanceUntilIdle()

        // 두 번 보내면 안 된다 — 서버는 한 요청을 통째로 받거나 통째로 거절한다.
        assertEquals(listOf<Pair<String?, String?>>("윤성" to "profile/presets/09.png"), repository.patches)
        assertEquals(0, repository.resetCount)
    }

    @Test
    fun `사진을 건드리지 않은 저장은 imageKey 를 싣지 않는다`() = runTest(mainDispatcher) {
        val repository = FakeAuthRepository()
        val viewModel = ProfileEditViewModel(repository)
        viewModel.prefill(name = "성윤", imageUrl = preset("03"))
        advanceUntilIdle()

        viewModel.onNicknameChange("윤성")
        viewModel.save()
        advanceUntilIdle()

        // imageKey 는 null 이어야 한다 — 「건드리지 마라」가 정확히 이 경우의 뜻이다.
        assertEquals(listOf<Pair<String?, String?>>("윤성" to null), repository.patches)
        assertEquals(0, repository.resetCount)
    }

    @Test
    fun `닉네임을 함께 바꿔도 되돌리기는 PATCH 뒤에 보낸다`() = runTest(mainDispatcher) {
        val repository = FakeAuthRepository()
        val viewModel = ProfileEditViewModel(repository)
        viewModel.prefill(name = "성윤", imageUrl = preset("03"))
        advanceUntilIdle()

        viewModel.onNicknameChange("윤성")
        viewModel.onImageReset()
        viewModel.save()
        advanceUntilIdle()

        // 순서가 뒤집히면, 닉네임이 409 로 실패했을 때 사진만 이미 기본으로 돌아간 상태가 남는다.
        assertEquals(listOf("PATCH", "DELETE"), repository.callOrder)
        assertEquals(listOf<Pair<String?, String?>>("윤성" to null), repository.patches)
    }

    @Test
    fun `목록 밖의 키라고 거절되면 고른 값을 비우고 목록을 다시 받는다`() = runTest(mainDispatcher) {
        val repository = FakeAuthRepository(
            updateFailure = AuthException(AuthError.INVALID_PROFILE_IMAGE, "존재하지 않는 프로필 이미지입니다."),
        )
        val viewModel = ProfileEditViewModel(repository)
        viewModel.prefill(name = "성윤", imageUrl = preset("03"))
        advanceUntilIdle()
        assertEquals(1, repository.imageListCalls)

        viewModel.onImagePick("profile/presets/99.png")
        viewModel.save()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        // 같은 키를 다시 눌러도 같은 400 이다 — 그 선택을 들고 있으면 사용자가 빠져나올 수 없다.
        assertEquals(ImageIntent.None, state.imageIntent)
        // 앱이 가진 목록이 낡았다는 뜻이므로 앱이 알아서 다시 받는다(사용자가 누를 자리가 없다).
        assertEquals(2, repository.imageListCalls)
        assertFalse(state.saved)
        // 사진 오류는 닉네임 필드가 아니라 배너로 떨어진다 — 닉네임은 멀쩡하다.
        // 문구를 여기 복사하지 않고 같은 출처와 비교한다: 문구가 바뀌어도 「배너로 간다」는 사실만 본다.
        assertEquals(AuthException(AuthError.INVALID_PROFILE_IMAGE).authUserMessage(), state.errorMessage)
        assertNotEquals(NicknameCheckState.Taken, state.check)
        assertNotEquals(NicknameCheckState.InvalidFormat, state.check)
    }

    /** 프리셋 URL. 판정에 쓰이는 건 파일 이름뿐이라 호스트는 CDN 실제 주소 모양만 맞춘다. */
    private fun preset(number: String) = "https://cdn.moyeota.co.kr/profile/presets/$number.png"
}

/**
 * 36 이 쓰는 세 가지(`updateProfile`·`resetProfileImage`·`getProfileImages`)만 기록하는 페이크.
 *
 * 나머지 메서드는 이 화면에서 불리면 **테스트가 터져야 한다** — 조용히 기본값을 돌려주면
 * 「저장이 엉뚱한 API 를 부른다」는 사고가 통과한다.
 *
 * 목록 값은 **백엔드 `ProfileImages` 설정 기준**이다. 로컬 DB 가 죽어 실서버로 확인한 값이 아니다.
 */
private class FakeAuthRepository(
    private val images: List<ProfileImage> = PRESET_PROFILE_IMAGES,
    private val updateFailure: Throwable? = null,
) : AuthRepository {

    override val authState: StateFlow<AuthState> = MutableStateFlow(AuthState.Authenticated("user-uuid"))

    /** 나간 PATCH 의 본문. `(nickname, imageKey)` 를 보낸 순서대로 담는다. */
    val patches = mutableListOf<Pair<String?, String?>>()

    /** PATCH 와 DELETE 가 나간 순서. 둘의 **앞뒤**가 이 화면의 계약이다. */
    val callOrder = mutableListOf<String>()

    var resetCount: Int = 0
        private set

    var imageListCalls: Int = 0
        private set

    override suspend fun updateProfile(nickname: String?, imageKey: String?) {
        patches += nickname to imageKey
        callOrder += "PATCH"
        updateFailure?.let { throw it }
    }

    override suspend fun resetProfileImage() {
        resetCount++
        callOrder += "DELETE"
    }

    override suspend fun getProfileImages(): List<ProfileImage> {
        imageListCalls++
        return images
    }

    /** 중복 확인은 디바운스가 끝나면 불린다 — 여기서는 통과시켜 저장 경로만 보게 한다. */
    override suspend fun isNicknameTaken(nickname: String): Boolean = false

    // 전화번호 가입 여부 선확인은 10 프로필 만들기의 번호 입력란에서만 불린다 — 36 에는 번호 입력이
    // 없다. 조용히 false 를 돌려주면 「프로필 수정이 가입 API 를 두드린다」는 사고가 통과한다.
    override suspend fun isPhoneRegistered(phoneNumber: String): Boolean =
        throw UnsupportedOperationException("36 프로필 수정과 무관하다")

    override suspend fun sendPhoneVerificationCode(phoneNumber: String): Unit =
        throw UnsupportedOperationException("36 프로필 수정과 무관하다")

    override suspend fun verifyPhoneCode(phoneNumber: String, code: String): String =
        throw UnsupportedOperationException("36 프로필 수정과 무관하다")

    override suspend fun register(newUser: NewUser): String =
        throw UnsupportedOperationException("36 프로필 수정과 무관하다")

    override suspend fun login(loginId: String, password: String): String =
        throw UnsupportedOperationException("36 프로필 수정과 무관하다")

    override suspend fun logout(): Unit =
        throw UnsupportedOperationException("36 프로필 수정과 무관하다")

    override suspend fun deleteAccount(password: String): Unit =
        throw UnsupportedOperationException("탈퇴는 37 의 몫이다")

    override suspend fun deleteSocialAccount(): Unit =
        throw UnsupportedOperationException("탈퇴는 37 의 몫이다")

    override suspend fun loginWithGoogle(idToken: String): com.moyeota.domain.model.GoogleLoginResult =
        throw UnsupportedOperationException("36 프로필 수정과 무관하다")

    override suspend fun registerSocial(newUser: com.moyeota.domain.model.NewSocialUser): String =
        throw UnsupportedOperationException("36 프로필 수정과 무관하다")

    override suspend fun getMyProfile(): UserProfileInfo =
        throw UnsupportedOperationException("프리필 값은 35 가 조회해 prefill 로 넘긴다")

    override suspend fun sendFindLoginIdCode(phoneNumber: String): Unit = throw UnsupportedOperationException("36 프로필 수정과 무관하다")
    override suspend fun findLoginId(phoneNumber: String, code: String): String = throw UnsupportedOperationException("36 프로필 수정과 무관하다")
    override suspend fun requestPasswordReset(loginId: String, phoneNumber: String): com.moyeota.domain.model.PasswordResetTicket = throw UnsupportedOperationException("36 프로필 수정과 무관하다")
    override suspend fun verifyPasswordResetCode(requestId: String, code: String): String = throw UnsupportedOperationException("36 프로필 수정과 무관하다")
    override suspend fun confirmPasswordReset(resetToken: String, newPassword: String, newPasswordConfirm: String): Unit = throw UnsupportedOperationException("36 프로필 수정과 무관하다")
}

/**
 * 격자에 쓰는 프리셋 두 장. 서버는 16장을 주지만 **이 테스트가 보는 건 장수가 아니라 호출 내용**이라
 * 늘리지 않았다(목록이 16장이라는 건 서버 계약이고 단위 테스트로 못 박을 수 없다).
 */
private val PRESET_PROFILE_IMAGES = listOf(
    ProfileImage(key = "profile/presets/01.png", url = "https://cdn.moyeota.co.kr/profile/presets/01.png"),
    ProfileImage(key = "profile/presets/02.png", url = "https://cdn.moyeota.co.kr/profile/presets/02.png"),
)
