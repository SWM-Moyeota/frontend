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
        assertEquals("홍길동", dto.name)
        // 서버가 Instant 로 받으므로 날짜만 보내면 400 이다.
        assertEquals("2000-01-01T00:00:00Z", dto.birthDate)
        assertEquals("010-1234-5678", dto.phoneNumber)
        // 서버 enum 이름 그대로여야 역직렬화된다.
        assertEquals("MALE", dto.gender)
        assertEquals("test@moyeota.com", dto.email)

        val encoded = json.encodeToString(RegisterRequestDto.serializer(), dto)
        listOf("loginId", "password", "name", "birthDate", "phoneNumber", "gender", "email")
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

    private fun httpException(code: Int, body: String): HttpException = HttpException(
        Response.error<Any>(code, body.toResponseBody("application/json".toMediaType())),
    )
}
