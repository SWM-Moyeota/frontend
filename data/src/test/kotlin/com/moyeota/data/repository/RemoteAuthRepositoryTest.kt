package com.moyeota.data.repository

import com.moyeota.data.local.ActivePartyStorage
import com.moyeota.data.local.RememberedParty
import com.moyeota.data.push.FcmTokenRegistrar
import com.moyeota.data.remote.AuthApi
import com.moyeota.data.remote.UserApi
import com.moyeota.data.remote.dto.AccountDeletionRequest
import com.moyeota.data.remote.dto.FcmTokenRequest
import com.moyeota.data.remote.dto.LoginRequestDto
import com.moyeota.data.remote.dto.NicknameCheckRequestDto
import com.moyeota.data.remote.dto.NicknameCheckResponse
import com.moyeota.data.remote.dto.PhoneCheckRequestDto
import com.moyeota.data.remote.dto.PhoneCheckResponse
import com.moyeota.data.remote.dto.ProfileImageResponse
import com.moyeota.data.remote.dto.RefreshTokenRequestDto
import com.moyeota.data.remote.dto.RegisterRequestDto
import com.moyeota.data.remote.dto.RegisterResponse
import com.moyeota.data.remote.dto.SmsSendRequestDto
import com.moyeota.data.remote.dto.SmsVerifyRequestDto
import com.moyeota.data.remote.dto.SmsVerifyResponse
import com.moyeota.data.remote.dto.TokenResponse
import com.moyeota.data.remote.dto.UpdateProfileRequest
import com.moyeota.data.remote.dto.UserProfileResponse
import com.moyeota.data.remote.dto.RideRecordPageResponse
import com.moyeota.data.session.FakeTokenStorage
import com.moyeota.data.session.SessionManager
import com.moyeota.data.session.StoredSession
import com.moyeota.domain.model.AuthError
import com.moyeota.domain.model.AuthException
import com.moyeota.domain.model.AuthState
import com.moyeota.domain.model.Gender
import com.moyeota.domain.model.NewUser
import com.moyeota.domain.model.ProfileImage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Call
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.time.LocalDate

class RemoteAuthRepositoryTest {

    private val newUser = NewUser(
        loginId = "moyeota_test",
        password = "Passw0rd!",
        nickname = "모여타",
        name = "홍길동",
        birthDate = LocalDate.of(2000, 1, 1),
        phoneNumber = "010-1234-5678",
        gender = Gender.MALE,
        email = "test@moyeota.com",
        verificationToken = "3f2b8c1e-0000-4000-8000-000000000001",
    )

    private fun session(initial: StoredSession? = null, storage: FakeTokenStorage = FakeTokenStorage(initial)) =
        SessionManager(storage, CoroutineScope(Dispatchers.Unconfined))

    // 내 정보 조회 API 는 가입/로그인/로그아웃 경로와 무관하다 — 그쪽 테스트가 신경 쓰지 않게 기본값으로 채운다.
    // activePartyStorage 도 같다: 탈퇴 경로만 건드리므로 나머지 테스트는 빈 기억으로 둔다.
    private fun repository(
        api: AuthApi,
        session: SessionManager,
        userApi: UserApi = FakeUserApi(),
        registrar: FcmTokenRegistrar = FcmTokenRegistrar(userApi, session),
        activePartyStorage: ActivePartyStorage = SpyActivePartyStorage(),
    ) = RemoteAuthRepository(api, userApi, session, registrar, activePartyStorage)

    @Test
    fun `내 정보는 uuid 와 이름을 그대로 도메인으로 넘긴다`() = runBlocking {
        val profile = repository(FakeAuthApi(), session(), FakeUserApi(name = "김성윤")).getMyProfile()

        assertEquals("01a06145-3caf-7614-a3bd-cee6e25316b1", profile.uuid)
        assertEquals("김성윤", profile.name)
    }

    /**
     * 닉네임도 실명도 없는 사용자에게 서버는 `"name":null` 을 내려보낸다(`orElse(null)`).
     * 이걸 에러로 바꾸면 멀쩡한 사용자가 화면을 못 연다 — 이름 없는 프로필은 정상 경우다.
     */
    @Test
    fun `이름이 없어도 실패하지 않는다`() = runBlocking {
        val profile = repository(FakeAuthApi(), session(), FakeUserApi(name = null)).getMyProfile()

        assertNull(profile.name)
    }

    /**
     * 401 은 재발급이 이미 실패했다는 뜻이다(Authenticator 가 apiClient 에서 한 번 시도한다).
     * SESSION_EXPIRED 로 좁혀야 화면이 "다시 로그인"으로 보낸다.
     */
    @Test
    fun `내 정보 조회의 401 은 세션 만료로 좁힌다`() {
        val failing = FakeUserApi(failure = httpException(401, """{"code":"USER005","message":"로그인이 필요합니다."}"""))

        val thrown = runCatching {
            runBlocking { repository(FakeAuthApi(), session(), failing).getMyProfile() }
        }.exceptionOrNull()

        assertEquals(AuthError.SESSION_EXPIRED, (thrown as AuthException).error)
    }

    // uuid 없이 "나"를 화면에 흘려보내면 잘못된 상태가 조용히 굳는다 — 여기서 끊는다.
    @Test
    fun `내 정보 응답에 uuid 가 없으면 실패로 끊는다`() {
        val thrown = runCatching {
            runBlocking { repository(FakeAuthApi(), session(), FakeUserApi(uuid = "")).getMyProfile() }
        }.exceptionOrNull()

        assertEquals(AuthError.UNKNOWN, (thrown as AuthException).error)
    }

    /**
     * 204 라 본문이 없다 — "돌려받을 게 없는 성공"이 예외 없이 끝나는지만 확인한다.
     * 번호는 하이픈을 떼고 보낸다(발송·확인·가입 세 곳을 같은 형태로 맞추려는 것).
     */
    @Test
    fun `인증번호 발송은 하이픈을 뗀 번호로 나가고 204 를 성공으로 끝낸다`() = runBlocking {
        val api = FakeAuthApi()

        repository(api, session()).sendPhoneVerificationCode("010-1234-5678")

        assertEquals(SmsSendRequestDto("01012345678"), api.smsSendRequest)
    }

    /**
     * **발송 단계에서 409 가 난다** — 서버가 문자 비용을 쓰기 전에 가입된 번호를 거른다.
     * 화면은 인증번호 입력칸을 열기 전에 "이미 가입된 번호"를 말해야 한다.
     */
    @Test
    fun `발송 409 USER104 는 이미 가입된 번호로 올라온다`() = runBlocking {
        val api = FakeAuthApi(
            smsSendFailure = httpException(409, """{"code":"USER104","message":"이미 가입된 전화번호입니다."}"""),
        )

        val thrown = runCatching {
            repository(api, session()).sendPhoneVerificationCode("01012345678")
        }.exceptionOrNull()

        assertEquals(AuthError.PHONE_NUMBER_DUPLICATED, (thrown as AuthException).error)
    }

    /** 429 가 셋이라 코드를 봐야 갈린다 — 쿨다운과 하루 한도는 화면 대응이 정반대다. */
    @Test
    fun `발송 429 는 쿨다운과 하루 한도로 갈린다`() = runBlocking {
        val cooldown = FakeAuthApi(
            smsSendFailure = httpException(429, """{"code":"USER202","message":"인증번호는 1분에 한 번만 요청할 수 있습니다."}"""),
        )
        val daily = FakeAuthApi(
            smsSendFailure = httpException(429, """{"code":"USER203","message":"오늘 인증번호 요청 횟수를 초과했습니다."}"""),
        )

        assertEquals(
            AuthError.SMS_COOLDOWN,
            (runCatching { repository(cooldown, session()).sendPhoneVerificationCode("01012345678") }
                .exceptionOrNull() as AuthException).error,
        )
        assertEquals(
            AuthError.SMS_DAILY_LIMIT_EXCEEDED,
            (runCatching { repository(daily, session()).sendPhoneVerificationCode("01012345678") }
                .exceptionOrNull() as AuthException).error,
        )
    }

    @Test
    fun `인증 확인은 토큰을 돌려주고 번호와 코드를 그대로 싣는다`() = runBlocking {
        val api = FakeAuthApi()

        val token = repository(api, session()).verifyPhoneCode("010-1234-5678", " 123456 ")

        assertEquals("3f2b8c1e-0000-4000-8000-000000000001", token)
        // 번호는 정규화하고 코드는 앞뒤 공백만 다듬는다(붙여넣기 대비).
        assertEquals(SmsVerifyRequestDto(phoneNumber = "01012345678", code = "123456"), api.smsVerifyRequest)
    }

    /**
     * 빈 토큰을 통과시키면 화면은 "인증 완료"로 넘어가고, 400 은 한참 뒤 가입 제출에서야 터진다.
     * uuid 를 끊는 것과 같은 이유로 여기서 끊는다.
     */
    @Test
    fun `인증 응답에 토큰이 없으면 실패로 끊는다`() = runBlocking {
        val api = FakeAuthApi(verificationToken = "")

        val thrown = runCatching {
            repository(api, session()).verifyPhoneCode("01012345678", "123456")
        }.exceptionOrNull()

        assertEquals(AuthError.UNKNOWN, (thrown as AuthException).error)
    }

    /** 만료·불일치·시도 초과는 모두 "재발송"이냐 "다시 입력"이냐가 달라 각각 올라와야 한다. */
    @Test
    fun `인증 확인 실패는 코드별 사유로 올라온다`() = runBlocking {
        val cases = listOf(
            Triple(400, """{"code":"USER204","message":"인증번호가 만료되었습니다. 다시 요청해 주세요."}""", AuthError.SMS_CODE_EXPIRED),
            Triple(400, """{"code":"USER205","message":"인증번호가 일치하지 않습니다."}""", AuthError.SMS_CODE_MISMATCH),
            Triple(429, """{"code":"USER206","message":"입력 횟수를 초과했습니다. 인증번호를 다시 요청해 주세요."}""", AuthError.SMS_TOO_MANY_ATTEMPTS),
            Triple(400, """{"code":"USER105","message":"전화번호 형식이 올바르지 않습니다."}""", AuthError.INVALID_PHONE_NUMBER),
            Triple(503, """{"code":"USER208","message":"지금은 인증을 진행할 수 없습니다. 잠시 후 다시 시도해 주세요."}""", AuthError.SMS_UNAVAILABLE),
        )

        cases.forEach { (status, body, expected) ->
            val api = FakeAuthApi(smsVerifyFailure = httpException(status, body))
            val thrown = runCatching {
                repository(api, session()).verifyPhoneCode("01012345678", "123456")
            }.exceptionOrNull()

            assertEquals(body, expected, (thrown as AuthException).error)
        }
    }

    /**
     * 토큰이 본문에 실리지 않으면 가입이 **전부** 400 이다(서버 `@NotBlank`).
     * 서버가 중복 검사보다 먼저 토큰을 보므로, 이 필드가 비면 다른 입력이 멀쩡해도 끝이다.
     */
    @Test
    fun `가입 요청에 문자 인증 토큰을 싣는다`() = runBlocking {
        val api = FakeAuthApi()

        repository(api, session()).register(newUser)

        assertEquals("3f2b8c1e-0000-4000-8000-000000000001", api.registerRequest?.verificationToken)
        // 인증·가입이 같은 번호 형태를 쓰는지 — 다르면 서버 해시 비교가 어긋날 소지를 남긴다.
        assertEquals("01012345678", api.registerRequest?.phoneNumber)
    }

    /**
     * 토큰 만료·소진·번호 불일치가 한 코드로 뭉쳐 온다. 400 폴백(INVALID_REQUEST)에 맡기면
     * "입력한 정보를 다시 확인해 주세요"가 떠서, 인증 단계로 되돌아가야 할 사용자가
     * 아이디·비밀번호만 고치며 맴돈다.
     */
    @Test
    fun `가입 400 USER207 은 휴대폰 미인증으로 올라온다`() = runBlocking {
        val api = FakeAuthApi(
            registerFailure = httpException(400, """{"code":"USER207","message":"휴대폰 인증이 필요합니다."}"""),
        )

        val thrown = runCatching { repository(api, session()).register(newUser) }.exceptionOrNull()

        assertEquals(AuthError.PHONE_NOT_VERIFIED, (thrown as AuthException).error)
    }

    @Test
    fun `가입은 uuid 만 돌려주고 로그인시키지는 않는다`() = runBlocking {
        val api = FakeAuthApi()
        val session = session()

        val uuid = repository(api, session).register(newUser)

        assertEquals("uuid-1", uuid)
        assertEquals("2000-01-01T00:00:00Z", api.registerRequest?.birthDate)
        // 서버가 토큰을 주지 않으므로 화면은 이어서 login 을 호출해야 한다.
        assertEquals(AuthState.Unauthenticated, session.authState.value)
    }

    /**
     * 서버 응답에는 사용자 식별자가 없다 — UUID 는 액세스 토큰의 `sub` 에서 나와야 한다.
     * 저장된 세션의 userUuid 가 그 값과 같은지까지 확인한다.
     */
    @Test
    fun `로그인 성공 시 액세스 토큰 sub 를 UUID 로 삼아 영속화한다`() = runBlocking {
        val api = FakeAuthApi()
        val storage = FakeTokenStorage(null)
        val session = session(storage = storage)

        val uuid = repository(api, session).login("moyeota_test", "Passw0rd!")

        assertEquals("uuid-1", uuid)
        assertEquals(LoginRequestDto("moyeota_test", "Passw0rd!"), api.loginRequest)
        assertEquals(AuthState.Authenticated("uuid-1"), session.authState.value)
        assertEquals(StoredSession("uuid-1", ACCESS_TOKEN_WITH_SUB, "refresh.jwt"), storage.saved)
    }

    /**
     * `sub` 를 못 읽으면 "로그인은 됐는데 내가 누군지 모르는" 세션이 열린다.
     * 그런 상태는 조용히 굳으므로 여기서 끊고, 세션도 열지 않는다.
     */
    @Test
    fun `토큰에서 sub 를 읽지 못하면 세션을 열지 않는다`() = runBlocking {
        val api = FakeAuthApi(accessToken = ACCESS_TOKEN_WITHOUT_SUB)
        val storage = FakeTokenStorage(null)
        val session = session(storage = storage)

        val thrown = runCatching { repository(api, session).login("moyeota_test", "Passw0rd!") }
            .exceptionOrNull()

        assertEquals(AuthError.UNKNOWN, (thrown as AuthException).error)
        assertEquals(AuthState.Unauthenticated, session.authState.value)
        assertNull(storage.saved)
    }

    @Test
    fun `로그인 401 은 LOGIN_FAILED AuthException 으로 올라온다`() = runBlocking {
        val api = FakeAuthApi(
            loginFailure = httpException(401, """{"code":"USER102","message":"아이디나 비밀번호가 다릅니다."}"""),
        )
        val session = session()

        val thrown = runCatching { repository(api, session).login("moyeota_test", "wrong") }
            .exceptionOrNull()

        assertTrue(thrown is AuthException)
        assertEquals(AuthError.LOGIN_FAILED, (thrown as AuthException).error)
        assertEquals(AuthState.Unauthenticated, session.authState.value)
    }

    @Test
    fun `가입 409 는 중복 아이디로 올라온다`() = runBlocking {
        val api = FakeAuthApi(
            registerFailure = httpException(409, """{"code":"USER101","message":"이미 존재하는 아이디입니다."}"""),
        )

        val thrown = runCatching { repository(api, session()).register(newUser) }.exceptionOrNull()

        assertEquals(AuthError.LOGIN_ID_DUPLICATED, (thrown as AuthException).error)
    }

    /** 가입 요청에 닉네임이 실려야 한다 — 서버 필수 필드이고, 이름(name)과는 다른 값이다. */
    @Test
    fun `가입 요청에 닉네임을 싣는다`() = runBlocking {
        val api = FakeAuthApi()

        repository(api, session()).register(newUser)

        assertEquals("모여타", api.registerRequest?.nickname)
        assertEquals("홍길동", api.registerRequest?.name)
    }

    /**
     * 아이디·전화번호·닉네임 중복이 **전부 409** 다. 코드를 보지 않고 상태 코드로 폴백하면
     * 전화번호가 겹쳤는데 "이미 사용 중인 아이디예요"가 떠서 사용자가 아이디만 계속 바꾸게 된다.
     */
    @Test
    fun `가입 409 USER104 는 전화번호 중복으로 올라온다`() = runBlocking {
        val api = FakeAuthApi(
            registerFailure = httpException(409, """{"code":"USER104","message":"이미 가입된 전화번호입니다."}"""),
        )

        val thrown = runCatching { repository(api, session()).register(newUser) }.exceptionOrNull()

        assertEquals(AuthError.PHONE_NUMBER_DUPLICATED, (thrown as AuthException).error)
        assertEquals("이미 가입된 전화번호입니다.", thrown.serverMessage)
    }

    /**
     * 서버 검사 순서는 아이디 → 전화번호 → 닉네임이라 **닉네임 중복도 409 로 온다**.
     * 코드를 보지 않고 상태 코드로 폴백하면 "이미 사용 중인 아이디예요"가 떠서
     * 사용자가 아이디만 계속 바꾸게 된다.
     */
    @Test
    fun `가입 409 USER108 은 닉네임 중복으로 올라온다`() = runBlocking {
        val api = FakeAuthApi(
            registerFailure = httpException(409, """{"code":"USER108","message":"이미 사용 중인 닉네임입니다."}"""),
        )

        val thrown = runCatching { repository(api, session()).register(newUser) }.exceptionOrNull()

        assertEquals(AuthError.NICKNAME_DUPLICATED, (thrown as AuthException).error)
    }

    @Test
    fun `닉네임 중복 확인은 서버 exists 를 그대로 돌려준다`() = runBlocking {
        val taken = FakeAuthApi(nicknameExists = true)
        val free = FakeAuthApi(nicknameExists = false)

        assertTrue(repository(taken, session()).isNicknameTaken("모여타"))
        assertFalse(repository(free, session()).isNicknameTaken("모여타"))
        assertEquals(NicknameCheckRequestDto("모여타"), taken.nicknameRequest)
    }

    /**
     * 서버는 앞뒤 공백을 strip 한 뒤 검사하고 저장한다. 앱이 다듬지 않으면 `"모여타 "` 가
     * "사용 가능"으로 보이고선 가입 때 같은 닉네임으로 409 를 맞는다.
     */
    @Test
    fun `닉네임 중복 확인은 앞뒤 공백을 다듬어 보낸다`() = runBlocking {
        val api = FakeAuthApi()

        repository(api, session()).isNicknameTaken("  모여타  ")

        assertEquals("모여타", api.nicknameRequest?.nickname)
    }

    /** 형식이 틀리면 서버가 exists 판정 전에 400 USER107 을 낸다 — "중복"이 아니라 "형식 오류"다. */
    @Test
    fun `닉네임 중복 확인의 400 USER107 은 형식 오류로 올라온다`() = runBlocking {
        val api = FakeAuthApi(
            nicknameFailure = httpException(
                400,
                """{"code":"USER107","message":"닉네임은 2~10자의 한글, 영문, 숫자만 가능합니다."}""",
            ),
        )

        val thrown = runCatching { repository(api, session()).isNicknameTaken("!") }.exceptionOrNull()

        assertEquals(AuthError.INVALID_NICKNAME, (thrown as AuthException).error)
    }

    // ───────────────────────── 전화번호 가입 여부 선확인 ─────────────────────────

    /**
     * 서버도 하이픈을 지우지만(`PhoneNumber` VO), 앱이 발송·확인·가입과 **다른 형태**를 보내면
     * 「발송은 되는데 선확인만 400」처럼 번호 표기부터 의심해야 하는 어긋남이 생긴다.
     * 네 경로가 모두 `AuthPolicy.normalizePhoneNumber` 를 거치는지를 여기서 못 박는다.
     */
    @Test
    fun `가입 여부 확인은 하이픈을 떼고 보낸다`() = runBlocking {
        val api = FakeAuthApi()

        repository(api, session()).isPhoneRegistered("010-1234-5678")

        assertEquals(PhoneCheckRequestDto("01012345678"), api.phoneRequest)
    }

    @Test
    fun `가입 여부 확인은 exists 를 그대로 돌려준다`() = runBlocking {
        assertTrue(repository(FakeAuthApi(phoneExists = true), session()).isPhoneRegistered("01012345678"))
        assertFalse(repository(FakeAuthApi(phoneExists = false), session()).isPhoneRegistered("01012345678"))
    }

    /**
     * **발송과 확인의 형식 오류 코드가 다르다.** 발송 요청에는 `@Pattern` 이 있어 Bean Validation 이
     * 먼저 걸려 `INVALID_REQUEST` 이고, 확인 요청에는 없어 서버 `PhoneNumber` VO 가 걸러
     * 400 `USER105` 다(실측 2026-11-01, 로컬 :8080).
     *
     * 매핑을 놓치면 400 폴백이 `INVALID_REQUEST` 인데 serverMessage 가 `"phoneNumber"` 로 시작하지도
     * 않아 화면의 분기에 걸리지 않고 서버 원문이 그대로 뜬다.
     */
    @Test
    fun `가입 여부 확인의 400 USER105 는 INVALID_PHONE_NUMBER 로 올라온다`() = runBlocking {
        val api = FakeAuthApi(
            phoneCheckFailure = httpException(
                400,
                """{"code":"USER105","message":"전화번호 형식이 올바르지 않습니다."}""",
            ),
        )

        val thrown = runCatching { repository(api, session()).isPhoneRegistered("abc") }.exceptionOrNull()

        assertEquals(AuthError.INVALID_PHONE_NUMBER, (thrown as AuthException).error)
    }

    /** null 필드는 본문에서 빠진다 — 서버가 "넘어온 것만 갱신"하므로 null 로 지우기를 표현할 수 없다. */
    @Test
    fun `프로필 수정은 넘어온 값만 서버로 보낸다`() = runBlocking {
        val userApi = FakeUserApi()

        repository(FakeAuthApi(), session(), userApi).updateProfile(nickname = " 새닉 ", imageKey = null)

        assertEquals(listOf(UpdateProfileRequest(nickname = "새닉", imageKey = null)), userApi.profileUpdates)
    }

    @Test
    fun `프로필 수정 실패도 AuthException 으로 좁힌다`() = runBlocking {
        val userApi = FakeUserApi(
            profileFailure = httpException(409, """{"code":"USER108","message":"이미 사용 중인 닉네임입니다."}"""),
        )

        val thrown = runCatching {
            repository(FakeAuthApi(), session(), userApi).updateProfile("겹치는닉", null)
        }.exceptionOrNull()

        assertEquals(AuthError.NICKNAME_DUPLICATED, (thrown as AuthException).error)
    }

    // ───────────────────────────── 프로필 이미지 ─────────────────────────────

    /**
     * **오늘 실제로 났던 결함을 못 박는 자리다** — 앱이 이미지를 골라도 `imageKey = null` 만 보내
     * PATCH 가 아무것도 바꾸지 않고 204 로 끝났다. 서버는 빠진 필드를 건드리지 않으므로
     * 요청은 성공으로 보이고, 사용자는 「저장됐어요」를 본 뒤 화면을 다시 열면 옛 이미지를 만난다.
     *
     * 닉네임이 `null` **그대로** 남는 것도 함께 본다. 빈 문자열로 바뀌면 서버가 닉네임까지 갱신하려 들어
     * 400 이고, 서버는 imageKey 를 닉네임보다 먼저 검사하므로 거절되면 **이미지도 함께 안 바뀐다**.
     */
    @Test
    fun `이미지 키만 바꿀 때 닉네임을 싣지 않는다`() = runBlocking {
        val userApi = FakeUserApi()

        repository(FakeAuthApi(), session(), userApi)
            .updateProfile(nickname = null, imageKey = "profile/presets/03.png")

        assertEquals(
            listOf(UpdateProfileRequest(nickname = null, imageKey = "profile/presets/03.png")),
            userApi.profileUpdates,
        )
    }

    /**
     * 서버가 준 순서가 곧 격자에 그려지는 순서다 — 정렬하거나 뒤섞으면 서버가 정한 순서가 사라진다.
     * 같은 단언이 key·url 이 뒤집히지 않는 것까지 본다(뒤집히면 PATCH 가 400 `USER112` 다).
     */
    @Test
    fun `프로필 이미지 목록을 서버 순서 그대로 도메인으로 넘긴다`() = runBlocking {
        val images = repository(FakeAuthApi(), session(), FakeUserApi()).getProfileImages()

        assertEquals(
            listOf(
                ProfileImage(key = "profile/presets/01.png", url = "https://cdn.moyeota.co.kr/profile/presets/01.png"),
                ProfileImage(key = "profile/presets/02.png", url = "https://cdn.moyeota.co.kr/profile/presets/02.png"),
            ),
            images,
        )
    }

    /**
     * 설정이 비어 0장이 와도 **정상 응답**이다. 여기서 예외로 바꾸면 이미지와 상관없는
     * 닉네임 수정까지 같은 화면에서 막힌다.
     */
    @Test
    fun `빈 이미지 목록도 그대로 통과한다`() = runBlocking {
        val userApi = FakeUserApi(profileImages = emptyList())

        assertTrue(repository(FakeAuthApi(), session(), userApi).getProfileImages().isEmpty())
    }

    /**
     * 되돌리기는 204·멱등이라 돌려받을 값이 없고, 확인할 수 있는 건 「정확히 한 번 나갔는가」뿐이다.
     * 재시도를 끼워 넣지 않는지까지 보는 이유는 경로가 **회원 탈퇴와 한 조각 차이**이기 때문이다
     * (`DELETE /users/me/profile-image` 대 `DELETE /users/me`) — 경로 검사는
     * [com.moyeota.data.remote.AuthenticatedPathContractTest] 쪽이 맡는다.
     */
    @Test
    fun `되돌리기는 DELETE 를 한 번만 부른다`() = runBlocking {
        val userApi = FakeUserApi()

        repository(FakeAuthApi(), session(), userApi).resetProfileImage()

        assertEquals(1, userApi.resetCount)
    }

    /**
     * 새로 들어온 두 메서드도 [HttpException] 을 밖으로 흘리지 않아야 한다. 화면은 [AuthException] 만
     * 다루므로 그대로 새면 「다시 로그인」 안내로 이어지지 못하고 처리되지 않은 예외가 된다.
     */
    @Test
    fun `프로필 이미지 조회와 되돌리기 실패도 AuthException 으로 좁힌다`() {
        val expired = httpException(401, """{"code":"USER005","message":"로그인이 필요합니다."}""")

        val listFailure = runCatching {
            runBlocking {
                repository(FakeAuthApi(), session(), FakeUserApi(profileImagesFailure = expired))
                    .getProfileImages()
            }
        }.exceptionOrNull()
        val resetFailure = runCatching {
            runBlocking {
                repository(FakeAuthApi(), session(), FakeUserApi(resetProfileImageFailure = expired))
                    .resetProfileImage()
            }
        }.exceptionOrNull()

        assertEquals(AuthError.SESSION_EXPIRED, (listFailure as AuthException).error)
        assertEquals(AuthError.SESSION_EXPIRED, (resetFailure as AuthException).error)
    }

    /**
     * FCM 토큰은 로그인 전에 도착해 보류돼 있는 게 정상이다(앱 최초 실행·로그인 화면에서의 회전).
     * 세션이 열리는 이 순간이 서버에 실어 보낼 수 있는 첫 시점이라, 로그인 경로가 직접 이어 준다.
     */
    @Test
    fun `로그인 성공 시 보류된 푸시 토큰을 서버에 등록한다`() = runBlocking {
        val userApi = FakeUserApi()
        val session = session()
        val registrar = FcmTokenRegistrar(userApi, session)
        registrar.onTokenAvailable("device-token")
        assertTrue("로그인 전인데 보냈다", userApi.fcmCalls.isEmpty())

        repository(FakeAuthApi(), session, userApi, registrar).login("moyeota_test", "Passw0rd!")

        assertEquals(listOf("PUT:device-token"), userApi.fcmCalls)
    }

    /** 등록 실패로 로그인이 실패한 것처럼 보이면 안 된다 — 잃는 건 도착 알림 하나뿐이다. */
    @Test
    fun `푸시 등록이 실패해도 로그인은 성공한다`() = runBlocking {
        val userApi = FakeUserApi(fcmFailure = IOException("offline"))
        val session = session()
        val registrar = FcmTokenRegistrar(userApi, session)
        registrar.onTokenAvailable("device-token")

        val uuid = repository(FakeAuthApi(), session, userApi, registrar).login("moyeota_test", "Passw0rd!")

        assertEquals("uuid-1", uuid)
        assertEquals(AuthState.Authenticated("uuid-1"), session.authState.value)
    }

    /**
     * **순서가 이 테스트의 전부다.** 서버는 `@CurrentUser` 로 대상을 찾으므로 세션을 먼저 비우면
     * Bearer 가 사라져 401 이 나고, 서버에는 죽은 토큰이 남아 다음 사용자의 도착 알림이
     * 이 기기로 온다. 그래서 해제 시점에 액세스 토큰이 아직 살아 있는지를 직접 붙잡아 확인한다.
     */
    @Test
    fun `로그아웃은 세션을 비우기 전에 푸시 토큰을 해제한다`() = runBlocking {
        val session = session(StoredSession("uuid-1", "access.jwt", "refresh.jwt"))
        val userApi = TokenProbingUserApi { session.currentAccessToken() }

        repository(FakeAuthApi(), session, userApi, FcmTokenRegistrar(userApi, session)).logout()

        assertEquals(1, userApi.deleteCount)
        assertEquals("access.jwt", userApi.accessTokenAtDelete)
        assertEquals(AuthState.Unauthenticated, session.authState.value)
    }

    /**
     * **로그아웃이 중간에 취소돼도 세션은 비워져야 한다.** 호출부는 `MainNavGraph` 의
     * `rememberCoroutineScope()` 라, 푸시 해제를 기다리는 최대 2초 안에 화면이 회전하면 스코프가
     * 취소된다. [FcmTokenRegistrar] 는 [CancellationException] 만은 되던지므로, 예전 구현은
     * 그 취소가 `session.onLoggedOut()` 에 닿지 못해 **세션이 그대로 남았다** —
     * 사용자는 로그아웃된 줄 아는데 토큰은 살아 있었다(공용 기기면 안전 문제다).
     *
     * 해제가 매달려 있는 동안 정확히 취소를 걸어 그 창을 재현한다. [entered] 를 기다리는 게 중요하다
     * — 그냥 취소하면 `logout()` 에 들어가기도 전에 끝나 다른 상황을 검증하게 된다.
     */
    @Test
    fun `로그아웃 중 호출부가 취소돼도 세션은 비워진다`() = runBlocking {
        val session = session(StoredSession("uuid-1", "access.jwt", "refresh.jwt"))
        val entered = CompletableDeferred<Unit>()
        val userApi = SuspendingFcmUserApi(entered)
        val repo = repository(FakeAuthApi(), session, userApi, FcmTokenRegistrar(userApi, session))

        val job = launch(Dispatchers.Default) { repo.logout() }
        entered.await()
        // 화면 회전으로 rememberCoroutineScope 가 날아간 순간.
        job.cancel()
        job.join()

        assertEquals(AuthState.Unauthenticated, session.authState.value)
        assertNull(session.currentAccessToken())
    }

    /** 보낼 Bearer 가 없으니 401 만 받는다. 로그아웃 경로에 헛된 왕복을 끼워 넣지 않는다. */
    @Test
    fun `미로그인 상태의 로그아웃은 푸시 해제도 부르지 않는다`() = runBlocking {
        val userApi = FakeUserApi()
        val session = session()

        repository(FakeAuthApi(), session, userApi, FcmTokenRegistrar(userApi, session)).logout()

        assertTrue(userApi.fcmCalls.isEmpty())
    }

    /** 오프라인에서 해제가 실패해도 로그아웃은 그대로 끝나야 한다 — "절대 실패 안 함" 계약. */
    @Test
    fun `푸시 해제가 실패해도 로그아웃은 완료된다`() = runBlocking {
        val userApi = FakeUserApi(fcmFailure = IOException("offline"))
        val session = session(StoredSession("uuid-1", "access.jwt", "refresh.jwt"))

        repository(FakeAuthApi(), session, userApi, FcmTokenRegistrar(userApi, session)).logout()

        assertEquals(AuthState.Unauthenticated, session.authState.value)
        assertNull(session.currentAccessToken())
    }

    @Test
    fun `로그아웃은 서버에 리프레시 무효화를 요청한다`() = runBlocking {
        val api = FakeAuthApi()
        val session = session(StoredSession("uuid-1", "access.jwt", "refresh.jwt"))

        repository(api, session).logout()

        assertEquals(RefreshTokenRequestDto("refresh.jwt"), api.logoutRequest)
        assertEquals(AuthState.Unauthenticated, session.authState.value)
    }

    @Test
    fun `서버 로그아웃이 실패해도 로컬 세션은 비워지고 예외를 올리지 않는다`() = runBlocking {
        val api = FakeAuthApi(logoutFailure = IOException("offline"))
        val session = session(StoredSession("uuid-1", "access.jwt", "refresh.jwt"))

        // 여기서 던지면 오프라인 사용자가 로그아웃하지 못하고 갇힌다.
        repository(api, session).logout()

        assertEquals(AuthState.Unauthenticated, session.authState.value)
        assertNull(session.currentAccessToken())
    }

    @Test
    fun `미로그인 상태의 로그아웃은 서버를 부르지 않는다`() = runBlocking {
        val api = FakeAuthApi()

        repository(api, session()).logout()

        assertNull(api.logoutRequest)
    }

    // ─────────────────────────────── 회원 탈퇴 ───────────────────────────────

    /** 서버 `UserDeletionRequest` 는 `password` 하나다. 다듬지 않고 그대로 싣는다 — trim 하면 다른 값이 된다. */
    @Test
    fun `탈퇴는 입력한 비밀번호를 그대로 본문에 싣는다`() = runBlocking {
        val userApi = FakeUserApi()
        val session = session(StoredSession("uuid-1", "access.jwt", "refresh.jwt"))

        repository(FakeAuthApi(), session, userApi).deleteAccount(" Passw0rd! ")

        assertEquals(listOf(AccountDeletionRequest(" Passw0rd! ")), userApi.deletions)
    }

    /**
     * **204 를 받으면 세션이 비워져야 한다.** 서버가 리프레시를 전량 취소하고 인증 캐시까지 비우므로
     * 그 뒤의 모든 요청은 401 이다 — 로컬 토큰을 들고 있으면 다음 화면 조회가 401 을 맞고
     * [AuthError.SESSION_EXPIRED] 가 올라와 스스로 탈퇴한 사용자에게 「세션이 만료됐어요」가 뜬다.
     */
    @Test
    fun `탈퇴가 성공하면 세션이 비워지고 미로그인으로 전이한다`() = runBlocking {
        val session = session(StoredSession("uuid-1", "access.jwt", "refresh.jwt"))

        repository(FakeAuthApi(), session).deleteAccount("Passw0rd!")

        assertEquals(AuthState.Unauthenticated, session.authState.value)
        assertNull(session.currentAccessToken())
        assertNull(session.currentRefreshToken())
    }

    /**
     * **로그아웃보다 한 가지 넓다.** `logout()` 은 "진행 중인 방" 기억을 일부러 남기지만
     * (계정에 매인 값이라 같은 계정 재로그인 시 되살아나야 한다) 탈퇴한 계정은 다시 로그인할 길이 없다.
     * 남겨 두면 다음 사용자의 기기에 앞 사람의 방 id 가 그대로 남는다.
     */
    @Test
    fun `탈퇴는 진행 중인 방 기억까지 지운다`() = runBlocking {
        val storage = SpyActivePartyStorage(RememberedParty("42", "uuid-1"))
        val session = session(StoredSession("uuid-1", "access.jwt", "refresh.jwt"))

        repository(FakeAuthApi(), session, activePartyStorage = storage).deleteAccount("Passw0rd!")

        assertNull(storage.load())
    }

    /** 반대쪽 못 박기 — 로그아웃은 그 기억을 남긴다. 둘이 같아지면 재로그인 복귀가 깨진다. */
    @Test
    fun `로그아웃은 진행 중인 방 기억을 남긴다`() = runBlocking {
        val remembered = RememberedParty("42", "uuid-1")
        val storage = SpyActivePartyStorage(remembered)
        val session = session(StoredSession("uuid-1", "access.jwt", "refresh.jwt"))

        repository(FakeAuthApi(), session, activePartyStorage = storage).logout()

        assertEquals(remembered, storage.load())
    }

    /**
     * 비밀번호를 틀렸거나 진행 중인 합승이 있으면 **아무 일도 없었던 것처럼** 계속 쓸 수 있어야 한다.
     * 세션을 먼저 비우는 구현(= 로그아웃과 같은 순서)이면 400 한 번에 사용자가 로그아웃된다.
     */
    @Test
    fun `탈퇴가 실패하면 세션과 방 기억이 그대로 남는다`() {
        val failures = listOf(
            httpException(400, """{"code":"USER111","message":"비밀번호가 일치하지 않습니다."}""") to
                AuthError.PASSWORD_MISMATCH,
            httpException(409, """{"code":"USER110","message":"진행 중인 합승이 있어 탈퇴할 수 없습니다."}""") to
                AuthError.ONGOING_PARTY_EXISTS,
            IOException("offline") to AuthError.NETWORK,
        )

        failures.forEach { (failure, expected) ->
            val remembered = RememberedParty("42", "uuid-1")
            val storage = SpyActivePartyStorage(remembered)
            val session = session(StoredSession("uuid-1", "access.jwt", "refresh.jwt"))
            val userApi = FakeUserApi(deleteAccountFailure = failure)

            val thrown = runCatching {
                runBlocking {
                    repository(FakeAuthApi(), session, userApi, activePartyStorage = storage)
                        .deleteAccount("wrong")
                }
            }.exceptionOrNull()

            assertEquals(expected, (thrown as AuthException).error)
            assertEquals("$expected 에서 세션이 날아갔다", AuthState.Authenticated("uuid-1"), session.authState.value)
            assertEquals("$expected 에서 방 기억이 날아갔다", remembered, runBlocking { storage.load() })
        }
    }

    /**
     * **탈퇴는 푸시 토큰을 따로 해제하지 않는다.** 서버 `User.delete()` 가 `fcmToken` 을 비우므로
     * 왕복이 필요 없고, 미리 불러 두면 400·409 로 탈퇴가 실패했을 때 멀쩡한 사용자의 알림만 끊긴다
     * (로그아웃은 반대로 반드시 불러야 한다 — 그쪽은 계정이 남기 때문이다).
     */
    @Test
    fun `탈퇴는 푸시 토큰 해제를 부르지 않는다`() = runBlocking {
        val userApi = FakeUserApi()
        val session = session(StoredSession("uuid-1", "access.jwt", "refresh.jwt"))
        val registrar = FcmTokenRegistrar(userApi, session)
        registrar.onTokenAvailable("device-token")
        userApi.fcmCalls.clear()

        repository(FakeAuthApi(), session, userApi, registrar).deleteAccount("Passw0rd!")

        assertTrue("탈퇴가 FCM 해제를 불렀다: ${userApi.fcmCalls}", userApi.fcmCalls.isEmpty())
    }

    /**
     * 서버 토큰은 이미 전량 취소됐다 — `POST /auth/logout` 을 더 보내는 건 401 만 받는 헛된 왕복이다.
     */
    @Test
    fun `탈퇴는 로그아웃 API 를 덧붙여 부르지 않는다`() = runBlocking {
        val api = FakeAuthApi()
        val session = session(StoredSession("uuid-1", "access.jwt", "refresh.jwt"))

        repository(api, session).deleteAccount("Passw0rd!")

        assertNull(api.logoutRequest)
    }

    /**
     * 로컬 저장소가 터져도(디스크 꽉 참·손상) 탈퇴는 성공으로 끝나야 한다 —
     * 서버 계정은 이미 사라졌으므로 "실패"로 올리면 사용자가 이미 없는 계정의 탈퇴를 다시 시도한다.
     */
    @Test
    fun `방 기억 삭제가 실패해도 탈퇴는 성공으로 끝난다`() = runBlocking {
        val storage = SpyActivePartyStorage(RememberedParty("42", "uuid-1"), failOnClear = true)
        val session = session(StoredSession("uuid-1", "access.jwt", "refresh.jwt"))

        repository(FakeAuthApi(), session, activePartyStorage = storage).deleteAccount("Passw0rd!")

        assertEquals(AuthState.Unauthenticated, session.authState.value)
    }

    private fun httpException(code: Int, body: String) = HttpException(
        Response.error<Any>(code, body.toResponseBody("application/json".toMediaType())),
    )
}

/** 인메모리 "진행 중인 방" 기억. [failOnClear] 로 디스크 실패를 흉내 낸다. */
private class SpyActivePartyStorage(
    remembered: RememberedParty? = null,
    private val failOnClear: Boolean = false,
) : ActivePartyStorage {
    private var value: RememberedParty? = remembered

    override suspend fun load(): RememberedParty? = value

    override suspend fun save(party: RememberedParty) {
        value = party
    }

    override suspend fun clear() {
        if (failOnClear) throw IOException("디스크 실패")
        value = null
    }
}

/** 페이로드 `{"sub":"uuid-1","tokenType":"ACCESS"}` 를 base64url 로 담은 가짜 액세스 토큰. 서명은 검증되지 않는다. */
private const val ACCESS_TOKEN_WITH_SUB =
    "header.eyJzdWIiOiJ1dWlkLTEiLCJ0b2tlblR5cGUiOiJBQ0NFU1MifQ.signature"

/** 페이로드 `{"tokenType":"ACCESS"}` — sub 가 없는 비정상 토큰. */
private const val ACCESS_TOKEN_WITHOUT_SUB = "header.eyJ0b2tlblR5cGUiOiJBQ0NFU1MifQ.signature"

private class FakeAuthApi(
    private val accessToken: String = ACCESS_TOKEN_WITH_SUB,
    private val registerFailure: Throwable? = null,
    private val loginFailure: Throwable? = null,
    private val logoutFailure: Throwable? = null,
    private val nicknameExists: Boolean = false,
    private val nicknameFailure: Throwable? = null,
    private val phoneExists: Boolean = false,
    private val phoneCheckFailure: Throwable? = null,
    private val smsSendFailure: Throwable? = null,
    private val smsVerifyFailure: Throwable? = null,
    private val verificationToken: String = "3f2b8c1e-0000-4000-8000-000000000001",
) : AuthApi {
    var registerRequest: RegisterRequestDto? = null
        private set
    var nicknameRequest: NicknameCheckRequestDto? = null
        private set
    var phoneRequest: PhoneCheckRequestDto? = null
        private set
    var loginRequest: LoginRequestDto? = null
        private set
    var logoutRequest: RefreshTokenRequestDto? = null
        private set
    var smsSendRequest: SmsSendRequestDto? = null
        private set
    var smsVerifyRequest: SmsVerifyRequestDto? = null
        private set

    override suspend fun sendSmsCode(request: SmsSendRequestDto) {
        smsSendRequest = request
        smsSendFailure?.let { throw it }
    }

    override suspend fun verifySmsCode(request: SmsVerifyRequestDto): SmsVerifyResponse {
        smsVerifyRequest = request
        smsVerifyFailure?.let { throw it }
        return SmsVerifyResponse(verificationToken = verificationToken)
    }

    override suspend fun register(request: RegisterRequestDto): RegisterResponse {
        registerRequest = request
        registerFailure?.let { throw it }
        return RegisterResponse(uuid = "uuid-1")
    }

    override suspend fun login(request: LoginRequestDto): TokenResponse {
        loginRequest = request
        loginFailure?.let { throw it }
        // 서버는 사용자 식별자를 주지 않는다 — 토큰 쌍이 전부다.
        return TokenResponse(accessToken = accessToken, refreshToken = "refresh.jwt")
    }

    // 재발급은 OkHttp Authenticator 경로 전용이라 Repository 테스트에서는 호출되지 않는다.
    override fun reissue(request: RefreshTokenRequestDto): Call<TokenResponse> =
        throw UnsupportedOperationException("reissue 는 TokenAuthenticator 가 호출한다")

    override suspend fun logout(request: RefreshTokenRequestDto) {
        logoutRequest = request
        logoutFailure?.let { throw it }
    }

    override suspend fun checkNickname(request: NicknameCheckRequestDto): NicknameCheckResponse {
        nicknameRequest = request
        nicknameFailure?.let { throw it }
        return NicknameCheckResponse(exists = nicknameExists)
    }

    override suspend fun checkPhone(request: PhoneCheckRequestDto): PhoneCheckResponse {
        phoneRequest = request
        phoneCheckFailure?.let { throw it }
        return PhoneCheckResponse(exists = phoneExists)
    }
}

/**
 * 목록 응답 픽스처. 백엔드 `ProfileImages` 설정 기준이다 — **실서버로 확인한 값이 아니다**
 * (이번에는 로컬 DB 가 죽어 API 를 호출하지 못했다). key 는 `profile/presets/NN.png` 로
 * 제로패딩이고, url 은 같은 key 를 CDN 주소에 붙인 값이다.
 *
 * 실제 목록은 16장이지만 두 장만 둔다 — 여기서 확인하는 건 「서버 순서가 보존되는가」와
 * 「key·url 이 뒤집히지 않는가」뿐이고, 장수는 그 둘과 무관하다.
 */
private val PRESET_PROFILE_IMAGES = listOf(
    ProfileImageResponse(key = "profile/presets/01.png", url = "https://cdn.moyeota.co.kr/profile/presets/01.png"),
    ProfileImageResponse(key = "profile/presets/02.png", url = "https://cdn.moyeota.co.kr/profile/presets/02.png"),
)

/** 기본값은 실서버 실측 응답 그대로(testuser1). */
private class FakeUserApi(
    private val uuid: String = "01a06145-3caf-7614-a3bd-cee6e25316b1",
    private val name: String? = "김성윤",
    private val failure: Throwable? = null,
    private val fcmFailure: Throwable? = null,
    private val profileFailure: Throwable? = null,
    private val deleteAccountFailure: Throwable? = null,
    private val profileImages: List<ProfileImageResponse> = PRESET_PROFILE_IMAGES,
    private val profileImagesFailure: Throwable? = null,
    private val resetProfileImageFailure: Throwable? = null,
) : UserApi {
    /** 푸시 토큰 호출이 **일어난 순서**. 어떤 호출이 실제로 나갔는지를 그대로 남긴다. */
    val fcmCalls = mutableListOf<String>()

    /** PATCH 로 실제 나간 본문. null 필드가 어떻게 실렸는지까지 확인한다. */
    val profileUpdates = mutableListOf<UpdateProfileRequest>()

    /** 탈퇴 요청으로 나간 본문. 비밀번호가 다듬어지지 않았는지까지 본다. */
    val deletions = mutableListOf<AccountDeletionRequest>()

    /**
     * 되돌리기가 나간 횟수. 204 라 돌려받을 값이 없어 **"몇 번 나갔는가"만이 검증 대상**이다 —
     * 경로가 회원 탈퇴(`DELETE /users/me`)와 한 조각 차이라 왕복이 늘어나는 것 자체가 위험 신호다.
     */
    var resetCount = 0
        private set

    override suspend fun getMyProfile(): UserProfileResponse {
        failure?.let { throw it }
        return UserProfileResponse(uuid = uuid, name = name)
    }

    override suspend fun updateProfile(request: UpdateProfileRequest) {
        profileUpdates += request
        profileFailure?.let { throw it }
    }

    override suspend fun getProfileImages(): List<ProfileImageResponse> {
        profileImagesFailure?.let { throw it }
        return profileImages
    }

    override suspend fun resetProfileImage() {
        // 실패해도 세어 둔다 — "몇 번 나갔는가"는 실패 경로에서도 검증 대상이다(탈퇴와 경로가 비슷하다).
        resetCount++
        resetProfileImageFailure?.let { throw it }
    }

    override suspend fun registerFcmToken(request: FcmTokenRequest) {
        fcmFailure?.let { throw it }
        fcmCalls += "PUT:${request.token}"
    }

    override suspend fun deleteFcmToken() {
        fcmFailure?.let { throw it }
        fcmCalls += "DELETE"
    }

    override suspend fun deleteAccount(request: AccountDeletionRequest) {
        // 실패해도 기록은 남긴다 — "무엇을 보내려 했는가"가 실패 경로에서도 검증 대상이다.
        deletions += request
        deleteAccountFailure?.let { throw it }
    }

    // UserApi 에 탑승 기록 조회(GET /users/me/parties)가 들어와 생긴 구현. 인증 흐름과 무관하다.
    override suspend fun getMyRideRecords(cursor: Long?, size: Int): RideRecordPageResponse =
        throw UnsupportedOperationException("인증 흐름과 무관하다")
}

/**
 * 해제가 불린 **그 순간의** 액세스 토큰을 붙잡아 둔다.
 * "세션 정리보다 먼저 해제했는가"는 호출 횟수로는 알 수 없고, 이렇게 시점을 찍어야만 드러난다.
 */
private class TokenProbingUserApi(private val accessToken: () -> String?) : UserApi {
    var accessTokenAtDelete: String? = null
        private set
    var deleteCount = 0
        private set

    override suspend fun getMyProfile(): UserProfileResponse =
        throw UnsupportedOperationException("푸시 해제 순서 검증 전용 페이크다")

    override suspend fun updateProfile(request: UpdateProfileRequest) =
        throw UnsupportedOperationException("푸시 해제 순서 검증 전용 페이크다")

    override suspend fun getProfileImages(): List<ProfileImageResponse> =
        throw UnsupportedOperationException("푸시 해제 순서 검증 전용 페이크다")

    override suspend fun resetProfileImage() =
        throw UnsupportedOperationException("푸시 해제 순서 검증 전용 페이크다")

    override suspend fun registerFcmToken(request: FcmTokenRequest) = Unit

    override suspend fun deleteFcmToken() {
        accessTokenAtDelete = accessToken()
        deleteCount++
    }

    override suspend fun deleteAccount(request: AccountDeletionRequest) =
        throw UnsupportedOperationException("푸시 해제 순서 검증 전용 페이크다")

    override suspend fun getMyRideRecords(cursor: Long?, size: Int): RideRecordPageResponse =
        throw UnsupportedOperationException("푸시 해제 순서 검증 전용 페이크다")
}

/**
 * 푸시 해제를 잠시 **매달아 둔다.** 그 사이에 호출부를 취소해 "로그아웃이 세션을 비우기 전에
 * 취소되는" 창을 정확히 재현한다. [entered] 는 "해제 호출이 실제로 들어왔다"는 신호다.
 *
 * 매달아 두는 수단이 [delay] 인 것이 중요하다 — **취소 가능한 지점**이라, 취소를 무시하지 않는
 * 구현에서는 반드시 여기서 [kotlinx.coroutines.CancellationException] 이 나고
 * `session.onLoggedOut()` 까지 가지 못한다. 완료 신호로 깨우는 방식은 경합에 따라 취소를
 * 비껴갈 수 있어 회귀 테스트로서 믿을 수 없다.
 */
private class SuspendingFcmUserApi(private val entered: CompletableDeferred<Unit>) : UserApi {
    override suspend fun getMyProfile(): UserProfileResponse =
        throw UnsupportedOperationException("로그아웃 취소 검증 전용 페이크다")

    override suspend fun updateProfile(request: UpdateProfileRequest) =
        throw UnsupportedOperationException("로그아웃 취소 검증 전용 페이크다")

    override suspend fun getProfileImages(): List<ProfileImageResponse> =
        throw UnsupportedOperationException("로그아웃 취소 검증 전용 페이크다")

    override suspend fun resetProfileImage() =
        throw UnsupportedOperationException("로그아웃 취소 검증 전용 페이크다")

    override suspend fun registerFcmToken(request: FcmTokenRequest) = Unit

    override suspend fun deleteFcmToken() {
        entered.complete(Unit)
        delay(HANGING_MILLIS)
    }

    override suspend fun deleteAccount(request: AccountDeletionRequest) =
        throw UnsupportedOperationException("로그아웃 취소 검증 전용 페이크다")

    override suspend fun getMyRideRecords(cursor: Long?, size: Int): RideRecordPageResponse =
        throw UnsupportedOperationException("로그아웃 취소 검증 전용 페이크다")

    private companion object {
        /** FCM 해제 상한(2초)보다 충분히 짧게 — 타임아웃이 먼저 터지면 취소를 검증하지 못한다. */
        const val HANGING_MILLIS = 100L
    }
}
