package com.moyeota.data.remote

import com.moyeota.data.remote.dto.RegisterRequestDto
import com.moyeota.data.remote.dto.RegisterResponse
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
        assertEquals("010-1234-5678", dto.phoneNumber)
        // 서버 enum 이름 그대로여야 역직렬화된다.
        assertEquals("MALE", dto.gender)
        assertEquals("test@moyeota.com", dto.email)

        val encoded = json.encodeToString(RegisterRequestDto.serializer(), dto)
        listOf("loginId", "password", "nickname", "name", "birthDate", "phoneNumber", "gender", "email")
            .forEach { assertTrue("$it 필드가 빠졌다: $encoded", encoded.contains("\"$it\"")) }
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
