package com.moyeota.data.remote

import com.moyeota.data.remote.dto.RegisterRequestDto
import com.moyeota.data.remote.dto.RegisterResponse
import com.moyeota.data.remote.dto.SmsSendRequestDto
import com.moyeota.data.remote.dto.SmsVerifyRequestDto
import com.moyeota.data.remote.dto.SmsVerifyResponse
import com.moyeota.data.remote.dto.TokenResponse
import com.moyeota.domain.model.AuthError
import com.moyeota.domain.model.AuthPolicy
import com.moyeota.domain.model.Gender
import com.moyeota.domain.model.NewUser
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneOffset

class AuthMappersTest {

    private val json = Json { ignoreUnknownKeys = true }

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

    @Test
    fun `birthDate 는 UTC 자정 Instant 문자열로 변환된다`() {
        assertEquals("2000-01-01T00:00:00Z", LocalDate.of(2000, 1, 1).toBirthDateInstant())
        assertEquals("1999-12-31T00:00:00Z", LocalDate.of(1999, 12, 31).toBirthDateInstant())
    }

    @Test
    fun `가입 요청 DTO 는 서버 필드명과 값 형식을 그대로 만든다`() {
        val dto = newUser.toDto()

        assertEquals("moyeota_test", dto.loginId)
        // 닉네임은 나중에 추가된 필수 필드다 — 빠지면 400 이고, 이름(name)과는 다른 값이다.
        assertEquals("모여타", dto.nickname)
        assertEquals("홍길동", dto.name)
        // 서버가 Instant 로 받으므로 날짜만 보내면 400 이다.
        assertEquals("2000-01-01T00:00:00Z", dto.birthDate)
        // 발송·확인과 같은 형태(하이픈 없는 숫자열)로 통일한다 — 서버도 하이픈을 떼고 저장·비교한다.
        assertEquals("01012345678", dto.phoneNumber)
        // 서버 enum 이름 그대로여야 역직렬화된다.
        assertEquals("MALE", dto.gender)
        assertEquals("test@moyeota.com", dto.email)

        val encoded = json.encodeToString(RegisterRequestDto.serializer(), dto)
        listOf(
            "loginId", "password", "nickname", "name", "birthDate",
            "phoneNumber", "gender", "email", "verificationToken",
        ).forEach { assertTrue("$it 필드가 빠졌다: $encoded", encoded.contains("\"$it\"")) }
    }

    /**
     * 2026-10 서버 변경으로 `verificationToken` 이 `@NotBlank` 필수가 됐다 — 빠뜨리면
     * 가입이 **전부** 400 `INVALID_REQUEST` 다. 본문에 실제로 실리는지까지 못 박는다.
     */
    @Test
    fun `가입 요청 본문에 문자 인증 토큰이 실린다`() {
        val dto = newUser.toDto()

        assertEquals("3f2b8c1e-0000-4000-8000-000000000001", dto.verificationToken)

        val encoded = json.encodeToString(RegisterRequestDto.serializer(), dto)
        assertTrue(
            "토큰이 본문에 없다: $encoded",
            encoded.contains("\"verificationToken\":\"3f2b8c1e-0000-4000-8000-000000000001\""),
        )
    }

    /**
     * 발송·확인·가입 세 요청이 같은 번호 형태를 써야 한다. 서버는 요청마다 `PhoneNumber` VO 로
     * 하이픈을 떼고 해시하므로 결과는 같지만, 앱이 화면마다 다른 형태를 보내면
     * "인증은 통과했는데 가입이 400"을 추적할 때 번호 표기부터 의심해야 한다.
     */
    @Test
    fun `전화번호는 하이픈과 공백을 떼고 보낸다`() {
        assertEquals("01012345678", AuthPolicy.normalizePhoneNumber("010-1234-5678"))
        assertEquals("01012345678", AuthPolicy.normalizePhoneNumber("01012345678"))
        assertEquals("01012345678", AuthPolicy.normalizePhoneNumber(" 010 1234 5678 "))
        // 서버 VO 는 앞뒤 공백만 strip 해서 중간 공백이 남은 값을 400 으로 떨군다 — 앱이 먼저 지운다.
        assertTrue(AuthPolicy.isValidPhoneNumber(AuthPolicy.normalizePhoneNumber("010 1234 5678")))
        // 011/016~019 도 서버 정규식이 받는다(가입 번호 길이가 10자리인 경우).
        assertEquals("0111234567", AuthPolicy.normalizePhoneNumber("011-123-4567"))
    }

    /** 서버 `SmsVerifyRequest.code` 의 `@Pattern(^\d{6}$)` 과 같은 판정. */
    @Test
    fun `인증번호는 숫자 6자리만 통과한다`() {
        assertTrue(AuthPolicy.isValidVerificationCode("000000"))
        assertTrue(AuthPolicy.isValidVerificationCode("123456"))
        assertFalse("5자리는 안 된다", AuthPolicy.isValidVerificationCode("12345"))
        assertFalse("7자리는 안 된다", AuthPolicy.isValidVerificationCode("1234567"))
        assertFalse("숫자가 아니면 안 된다", AuthPolicy.isValidVerificationCode("12345a"))
        assertFalse(AuthPolicy.isValidVerificationCode(""))
    }

    /**
     * 서버 `@Past` 는 **Instant** 비교이고 앱은 날짜를 UTC 자정으로 올려 보낸다.
     * 그래서 "오늘"은 09:00 KST 이전이면 아직 미래다 — 그 틈을 도메인에서 미리 막는다.
     */
    @Test
    fun `생년월일은 UTC 기준 오늘보다 이른 날짜만 통과한다`() {
        val todayUtc = LocalDate.now(ZoneOffset.UTC)

        assertTrue(AuthPolicy.isValidBirthDate(LocalDate.of(2000, 1, 1)))
        assertTrue(AuthPolicy.isValidBirthDate(todayUtc.minusDays(1)))
        assertFalse("오늘은 UTC 자정이 미래일 수 있다", AuthPolicy.isValidBirthDate(todayUtc))
        assertFalse(AuthPolicy.isValidBirthDate(todayUtc.plusDays(1)))
    }

    /** 인증 확인 성공 응답은 토큰 하나뿐이다 — 백엔드 `SmsVerifyResponse`. */
    @Test
    fun `문자 인증 200 응답에서 verificationToken 을 읽는다`() {
        val body = """{"verificationToken":"3f2b8c1e-0000-4000-8000-000000000001"}"""

        val response = json.decodeFromString(SmsVerifyResponse.serializer(), body)

        assertEquals("3f2b8c1e-0000-4000-8000-000000000001", response.verificationToken)
        // 필드가 빠진 응답은 빈 값으로 방어한다(끊는 건 Repository 몫이다).
        assertEquals("", json.decodeFromString(SmsVerifyResponse.serializer(), "{}").verificationToken)
    }

    /** 발송 요청 본문은 번호 하나다 — 백엔드 `SmsSendRequest`. */
    @Test
    fun `문자 발송 요청 본문은 phoneNumber 하나다`() {
        val encoded = json.encodeToString(SmsSendRequestDto.serializer(), SmsSendRequestDto("01012345678"))

        assertEquals("""{"phoneNumber":"01012345678"}""", encoded)
    }

    @Test
    fun `문자 확인 요청 본문은 번호와 코드다`() {
        val encoded = json.encodeToString(
            SmsVerifyRequestDto.serializer(),
            SmsVerifyRequestDto(phoneNumber = "01012345678", code = "123456"),
        )

        assertEquals("""{"phoneNumber":"01012345678","code":"123456"}""", encoded)
    }

    /**
     * **429 가 세 가지 뜻을 갖는다** — 재발송 대기(USER202)·하루 한도(USER203)·입력 초과(USER206).
     * 상태 코드로 폴백하면 셋 다 [AuthError.UNKNOWN] 이 되어 "잠시 후 다시 시도해 주세요"가 뜨는데,
     * 하루 한도에 그 문구를 띄우면 사용자가 1분마다 재전송을 누르며 하루를 보낸다.
     */
    @Test
    fun `429 세 코드는 서로 다른 사유로 갈린다`() {
        val throttles = listOf(
            """{"code":"USER202","message":"인증번호는 1분에 한 번만 요청할 수 있습니다."}""",
            """{"code":"USER203","message":"오늘 인증번호 요청 횟수를 초과했습니다."}""",
            """{"code":"USER206","message":"입력 횟수를 초과했습니다. 인증번호를 다시 요청해 주세요."}""",
        ).map { httpException(429, it).toAuthException().error }

        assertEquals(
            listOf(
                AuthError.SMS_COOLDOWN,
                AuthError.SMS_DAILY_LIMIT_EXCEEDED,
                AuthError.SMS_TOO_MANY_ATTEMPTS,
            ),
            throttles,
        )
    }

    /**
     * 400 도 세 가지다 — 만료(USER204)·불일치(USER205)·가입 토큰 무효(USER207).
     * 폴백은 셋을 모두 [AuthError.INVALID_REQUEST] 로 뭉쳐 "입력한 정보를 다시 확인해 주세요"를 띄우는데,
     * USER207 은 입력이 아니라 **인증 단계로 되돌려야 하는** 상황이다.
     */
    @Test
    fun `문자 인증 400 세 코드는 서로 다른 사유로 갈린다`() {
        val badRequests = listOf(
            """{"code":"USER204","message":"인증번호가 만료되었습니다. 다시 요청해 주세요."}""",
            """{"code":"USER205","message":"인증번호가 일치하지 않습니다."}""",
            """{"code":"USER207","message":"휴대폰 인증이 필요합니다."}""",
        ).map { httpException(400, it).toAuthException().error }

        assertEquals(
            listOf(
                AuthError.SMS_CODE_EXPIRED,
                AuthError.SMS_CODE_MISMATCH,
                AuthError.PHONE_NOT_VERIFIED,
            ),
            badRequests,
        )
    }

    /**
     * 발송 실패(502)와 저장소 장애(503)는 앱의 대응이 같아 하나로 합쳤다.
     * 합친 게 의도라는 걸 남겨 둔다 — 나중에 둘을 다르게 안내하려면 여기부터 갈라야 한다.
     */
    @Test
    fun `발송 실패와 저장소 장애는 한 사유로 합친다`() {
        assertEquals(
            AuthError.SMS_UNAVAILABLE,
            httpException(502, """{"code":"USER201","message":"인증번호 발송에 실패했습니다. 잠시 후 다시 시도해 주세요."}""")
                .toAuthException().error,
        )
        assertEquals(
            AuthError.SMS_UNAVAILABLE,
            httpException(503, """{"code":"USER208","message":"지금은 인증을 진행할 수 없습니다. 잠시 후 다시 시도해 주세요."}""")
                .toAuthException().error,
        )
    }

    /**
     * 번호 형식 위반이 **두 코드로 갈린다**: 발송은 `@Pattern` 이 먼저 걸려 `INVALID_REQUEST`,
     * 확인은 `@NotBlank` 뿐이라 `PhoneNumber` VO 까지 내려가 `USER105` 다. 화면 문구는 같아야 한다.
     */
    @Test
    fun `400 USER105 는 전화번호 형식 오류로 좁힌다`() {
        val fromVo = httpException(400, """{"code":"USER105","message":"전화번호 형식이 올바르지 않습니다."}""")
            .toAuthException()
        assertEquals(AuthError.INVALID_PHONE_NUMBER, fromVo.error)

        val fromBeanValidation = httpException(
            400,
            """{"code":"INVALID_REQUEST","message":"phoneNumber: 전화번호 형식이 올바르지 않습니다."}""",
        ).toAuthException()
        assertEquals(AuthError.INVALID_REQUEST, fromBeanValidation.error)
        assertTrue(fromBeanValidation.serverMessage!!.startsWith("phoneNumber: "))
    }

    /** 발송 단계에서 이미 가입된 번호를 거른다 — 기존 409 매핑이 그대로 쓰인다. */
    @Test
    fun `발송 409 USER104 는 이미 가입된 번호다`() {
        val exception = httpException(409, """{"code":"USER104","message":"이미 가입된 전화번호입니다."}""")
            .toAuthException()

        assertEquals(AuthError.PHONE_NUMBER_DUPLICATED, exception.error)
    }

    /** 토큰이 빠진 가입 요청에 서버가 내는 본문. 필드명이 그대로 보여야 화면이 원인을 짚을 수 있다. */
    @Test
    fun `토큰 누락 400 은 필드명이 담긴 검증 실패로 온다`() {
        val exception = httpException(
            400,
            """{"code":"INVALID_REQUEST","message":"verificationToken: 휴대폰 인증이 필요합니다."}""",
        ).toAuthException()

        assertEquals(AuthError.INVALID_REQUEST, exception.error)
        assertTrue(exception.serverMessage!!.startsWith("verificationToken: "))
    }

    @Test
    fun `가입 201 응답에서 uuid 를 읽는다`() {
        val body = """{"uuid":"7f6c1e2a-0f4b-4a53-9b5c-1c2d3e4f5a6b"}"""

        val response = json.decodeFromString(RegisterResponse.serializer(), body)

        assertEquals("7f6c1e2a-0f4b-4a53-9b5c-1c2d3e4f5a6b", response.uuid)
    }

    /**
     * 로그인 응답은 재발급과 같은 [TokenResponse] 이고 `userId` 는 없다.
     * 서버가 옛 필드를 다시 실어 보내도 무시되는지까지 확인한다(ignoreUnknownKeys).
     */
    @Test
    fun `로그인 200 응답은 토큰 쌍뿐이다`() {
        val body = """{"accessToken":"access.jwt.value","refreshToken":"refresh.jwt.value"}"""

        val response = json.decodeFromString(TokenResponse.serializer(), body)

        assertEquals("access.jwt.value", response.accessToken)
        assertEquals("refresh.jwt.value", response.refreshToken)

        val withLegacyField =
            """{"userId":"7f6c1e2a-0f4b-4a53-9b5c-1c2d3e4f5a6b","accessToken":"a","refreshToken":"r"}"""
        assertEquals("a", json.decodeFromString(TokenResponse.serializer(), withLegacyField).accessToken)
    }

    @Test
    fun `재발급 응답은 리프레시까지 새 값으로 온다`() {
        val body = """{"accessToken":"new.access","refreshToken":"new.refresh"}"""

        val response = json.decodeFromString(TokenResponse.serializer(), body)

        assertEquals("new.access", response.accessToken)
        // 회전 방식 — 이 값을 저장하지 않으면 다음 재발급이 401 이 된다.
        assertEquals("new.refresh", response.refreshToken)
    }

    /**
     * **401 이 두 가지 뜻을 갖는다** — 로그인 실패(USER102)와 세션 만료(USER001~005).
     * 코드를 안 보고 상태 코드로만 폴백하면 아이디/비밀번호 오류에 "다시 로그인해 주세요"가 뜬다.
     * 본문은 실서버 응답 그대로다.
     */
    @Test
    fun `401 USER102 는 세션 만료가 아니라 로그인 실패다`() {
        val exception = httpException(401, """{"code":"USER102","message":"아이디나 비밀번호가 다릅니다."}""")
            .toAuthException()

        assertEquals(AuthError.LOGIN_FAILED, exception.error)
        assertEquals("아이디나 비밀번호가 다릅니다.", exception.serverMessage)
    }

    @Test
    fun `409 USER101 은 중복 아이디로 매핑된다`() {
        val exception = httpException(409, """{"code":"USER101","message":"이미 존재하는 아이디입니다."}""")
            .toAuthException()

        assertEquals(AuthError.LOGIN_ID_DUPLICATED, exception.error)
    }

    /**
     * **409 는 세 가지 뜻을 갖는다** — 아이디(USER101)·전화번호(USER104)·닉네임(USER108) 중복.
     * 상태 코드로만 폴백하면 무엇이 겹쳤든 "이미 사용 중인 아이디예요"가 떠서
     * 사용자가 아이디만 계속 바꾸게 된다. 셋이 서로 다른 값으로 갈리는지 한 번에 못 박는다.
     */
    @Test
    fun `409 세 코드는 서로 다른 중복 사유로 갈린다`() {
        val duplicates = listOf(
            """{"code":"USER101","message":"이미 존재하는 아이디입니다."}""",
            """{"code":"USER104","message":"이미 가입된 전화번호입니다."}""",
            """{"code":"USER108","message":"이미 사용 중인 닉네임입니다."}""",
        ).map { httpException(409, it).toAuthException().error }

        assertEquals(
            listOf(
                AuthError.LOGIN_ID_DUPLICATED,
                AuthError.PHONE_NUMBER_DUPLICATED,
                AuthError.NICKNAME_DUPLICATED,
            ),
            duplicates,
        )
    }

    @Test
    fun `409 USER108 은 아이디가 아니라 닉네임 중복이다`() {
        val exception = httpException(409, """{"code":"USER108","message":"이미 사용 중인 닉네임입니다."}""")
            .toAuthException()

        assertEquals(AuthError.NICKNAME_DUPLICATED, exception.error)
        assertEquals("이미 사용 중인 닉네임입니다.", exception.serverMessage)
    }

    /** 닉네임 형식 위반은 일반 검증 실패와 달리 "중복 확인" 화면이 따로 안내해야 한다. */
    @Test
    fun `400 USER107 은 닉네임 형식 오류로 좁힌다`() {
        val exception = httpException(
            400,
            """{"code":"USER107","message":"닉네임은 2~10자의 한글, 영문, 숫자만 가능합니다."}""",
        ).toAuthException()

        assertEquals(AuthError.INVALID_NICKNAME, exception.error)
    }

    /** USER106(빈 FCM 토큰)은 사용자가 고칠 입력이 아니다 — 일반 검증 실패로 둔다. */
    @Test
    fun `400 USER106 은 일반 검증 실패로 취급한다`() {
        val exception = httpException(400, """{"code":"USER106","message":"FCM 토큰이 비어 있습니다."}""")
            .toAuthException()

        assertEquals(AuthError.INVALID_REQUEST, exception.error)
    }

    @Test
    fun `400 INVALID_REQUEST 는 필드 사유 메시지를 그대로 보존한다`() {
        val exception = httpException(
            400,
            """{"code":"INVALID_REQUEST","message":"password: 비밀번호는 영문, 숫자, 특수문자를 각각 하나 이상 포함해야 합니다."}""",
        ).toAuthException()

        assertEquals(AuthError.INVALID_REQUEST, exception.error)
        assertTrue(exception.serverMessage!!.startsWith("password: "))
    }

    /** 토큰 계열 USER001~005 와 사용자 없음 USER103 은 앱 대응이 "재로그인" 하나로 같다. */
    @Test
    fun `토큰 계열 코드는 세션 만료 하나로 뭉친다`() {
        listOf(
            401 to """{"code":"USER001","message":"유효하지 않은 토큰입니다."}""",
            401 to """{"code":"USER002","message":"만료된 토큰입니다."}""",
            401 to """{"code":"USER003","message":"토큰 용도가 올바르지 않습니다."}""",
            401 to """{"code":"USER004","message":"다시 로그인해 주세요."}""",
            401 to """{"code":"USER005","message":"로그인이 필요합니다."}""",
            404 to """{"code":"USER103","message":"존재하지 않는 사용자입니다."}""",
        ).forEach { (status, body) ->
            assertEquals(body, AuthError.SESSION_EXPIRED, httpException(status, body).toAuthException().error)
        }
    }

    @Test
    fun `code 없는 응답은 상태 코드로 폴백한다`() {
        // 스프링이 직접 만드는 404 처럼 ErrorResponse shape 이 아닌 본문.
        assertEquals(AuthError.INVALID_REQUEST, httpException(400, "").toAuthException().error)
        assertEquals(AuthError.UNKNOWN, httpException(500, "<html>oops</html>").toAuthException().error)
    }

    @Test
    fun `연결 실패는 NETWORK 로 매핑되고 서버 메시지는 없다`() {
        val exception = IOException("failed to connect").toAuthException()

        assertEquals(AuthError.NETWORK, exception.error)
        assertNull(exception.serverMessage)
    }

    @Test
    fun `AuthPolicy 는 서버 검증 규칙과 같은 판정을 낸다`() {
        assertTrue(AuthPolicy.isValidLoginId("moyeota_1"))
        assertFalse("숫자로 시작하면 안 된다", AuthPolicy.isValidLoginId("1moyeota"))
        assertFalse("4자 미만은 안 된다", AuthPolicy.isValidLoginId("abc"))
        assertFalse("대문자는 안 된다", AuthPolicy.isValidLoginId("Moyeota"))

        assertTrue(AuthPolicy.isValidPassword("Passw0rd!"))
        assertFalse("특수문자가 없다", AuthPolicy.isValidPassword("Password1"))
        assertFalse("8자 미만이다", AuthPolicy.isValidPassword("Pa0!"))

        assertTrue(AuthPolicy.isValidPhoneNumber("010-1234-5678"))
        assertTrue("하이픈 없이도 통과한다", AuthPolicy.isValidPhoneNumber("01012345678"))
        assertFalse(AuthPolicy.isValidPhoneNumber("02-123-4567"))
    }

    /** 서버 도메인 VO `Nickname` 과 같은 판정이어야 왕복 400 을 줄일 수 있다. */
    @Test
    fun `닉네임 규칙은 서버 Nickname VO 와 같다`() {
        assertTrue(AuthPolicy.isValidNickname("모여타"))
        assertTrue("영문·숫자 혼용도 통과한다", AuthPolicy.isValidNickname("moyeota99"))
        assertTrue("서버도 strip 한 뒤 검사한다", AuthPolicy.isValidNickname("  모여타  "))
        assertFalse("2자 미만은 안 된다", AuthPolicy.isValidNickname("김"))
        assertFalse("10자 초과는 안 된다", AuthPolicy.isValidNickname("가나다라마바사아자차카"))
        assertFalse("가운데 공백은 안 된다", AuthPolicy.isValidNickname("모여 타"))
        assertFalse("특수문자는 안 된다", AuthPolicy.isValidNickname("모여타!"))
        assertFalse("자모 낱자는 안 된다", AuthPolicy.isValidNickname("ㄱㄴㄷ"))
    }

    private fun httpException(code: Int, body: String): HttpException = HttpException(
        Response.error<Any>(code, body.toResponseBody("application/json".toMediaType())),
    )
}
