package com.moyeota.presentation.feature.auth

import com.moyeota.domain.model.AuthError
import com.moyeota.domain.model.AuthException
import com.moyeota.domain.model.Gender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 가입 초안의 제출 가능 판정과 에러 문구 표.
 *
 * 토큰 누락은 **서버에서 400 `INVALID_REQUEST` 로만 드러나고**, 그 문구는 "입력한 정보를 다시
 * 확인해 주세요"라서 사용자도 개발자도 원인을 못 짚는다. 그래서 제출 전에 걸러내는 조건을 고정한다.
 */
class SignupDraftTest {

    private val filled = SignupDraft(
        email = "moyeota@example.com",
        nickname = "모여",
        name = "홍길동",
        phoneNumber = "01012345678",
        birthDate = LocalDate.of(1995, 3, 15),
        gender = Gender.FEMALE,
        loginId = "moyeota",
        password = "abcd1234!",
        verificationToken = "tok-1",
    )

    @Test
    fun `아홉 값이 다 차면 가입 요청으로 바뀐다`() {
        val newUser = filled.toNewUser()
        assertNotNull(newUser)
        assertEquals("tok-1", newUser!!.verificationToken)
        assertEquals("01012345678", newUser.phoneNumber)
    }

    @Test
    fun `문자 인증 토큰이 비면 제출하지 않는다 - 보내면 전부 400 이다`() {
        assertNull(filled.copy(verificationToken = "").toNewUser())
    }

    @Test
    fun `닉네임은 앞뒤 공백을 떼고 담는다 - 서버도 strip 후 검사한다`() {
        assertEquals("모여", filled.copy(nickname = "  모여  ").toNewUser()?.nickname)
    }

    @Test
    fun `빈 초안은 제출되지 않는다`() {
        assertNull(SignupDraft().toNewUser())
    }

    // ─────────────────────────────── 성별 ───────────────────────────────
    //
    // 「선택 안 함」(Gender.UNDISCLOSED)이 생기면서 **비슷해 보이지만 반대인 두 상태**가 생겼다:
    //  · gender == null        → 아직 고르지 않았다 → 제출 불가
    //  · gender == UNDISCLOSED → 밝히지 않기로 **골랐다** → 제출된다
    // 둘을 한쪽으로 뭉개면 성별이 선택 사항이 되거나(서버 @NotNull 위반 → 400) 비공개를
    // 고른 사람이 가입을 못 하게 된다. 그래서 양방향을 함께 못 박는다.

    @Test
    fun `성별을 선택 안 함으로 골라도 가입 요청이 만들어진다`() {
        val newUser = filled.copy(gender = Gender.UNDISCLOSED).toNewUser()
        assertNotNull(newUser)
        // 값이 비거나 다른 성별로 바뀌어 실리지 않는다 — data 매퍼가 이 이름을 그대로 보낸다
        assertEquals(Gender.UNDISCLOSED, newUser!!.gender)
    }

    @Test
    fun `성별을 고르지 않으면 제출하지 않는다 - 선택 안 함과 미선택은 다르다`() {
        assertNull(filled.copy(gender = null).toNewUser())
    }

    /**
     * 성별 상수가 더 늘어도 **제출 가능 여부가 값에 따라 갈리지 않는다.** 특정 값만 통과시키는
     * 분기가 생기면 "고를 수는 있는데 가입은 안 되는 선택지"가 만들어진다.
     */
    @Test
    fun `모든 성별 값으로 가입 요청이 만들어진다`() {
        for (gender in Gender.entries) {
            assertNotNull(gender.name, filled.copy(gender = gender).toNewUser())
        }
    }

    // ─────────────────────────────── 에러 문구 표 ───────────────────────────────

    @Test
    fun `같은 상태 코드로 뭉쳐 오는 실패들이 서로 다른 말을 한다`() {
        // 429 세 개 — "잠시 후 다시 시도" 하나로 뭉치면 일일 한도에 걸린 사용자가 1분마다 재전송을 누른다
        val tooManyRequests = listOf(
            AuthError.SMS_COOLDOWN,
            AuthError.SMS_DAILY_LIMIT_EXCEEDED,
            AuthError.SMS_TOO_MANY_ATTEMPTS,
        ).map { authErrorMessage(it) }
        assertEquals(tooManyRequests.toString(), 3, tooManyRequests.toSet().size)

        // 400 세 개 — 되돌릴 단계가 서로 다르다(재입력 / 재발송 / 인증 처음부터)
        val badRequests = listOf(
            AuthError.SMS_CODE_MISMATCH,
            AuthError.SMS_CODE_EXPIRED,
            AuthError.PHONE_NOT_VERIFIED,
        ).map { authErrorMessage(it) }
        assertEquals(badRequests.toString(), 3, badRequests.toSet().size)
    }

    @Test
    fun `모든 AuthError 에 문구가 있다 - 빈 문구나 코드 이름이 새어 나가면 안 된다`() {
        for (error in AuthError.entries) {
            val message = authErrorMessage(error)
            assertTrue(error.name, message.isNotBlank())
            assertTrue(error.name, !message.contains(error.name))
        }
    }

    @Test
    fun `서버 제한값은 문구에 직접 적지 않고 도메인 상수를 쓴다`() {
        assertTrue(authErrorMessage(AuthError.SMS_DAILY_LIMIT_EXCEEDED).contains("하루 5번"))
        assertTrue(authErrorMessage(AuthError.SMS_TOO_MANY_ATTEMPTS).contains("5번"))
    }

    @Test
    fun `번호 형식 오류는 발송과 확인 어느 쪽에서 와도 같은 안내로 모인다`() {
        val fromVerify = authErrorMessage(AuthError.INVALID_PHONE_NUMBER)
        val fromSend = authErrorMessage(AuthError.INVALID_REQUEST, "phoneNumber: 올바른 형식이 아닙니다")
        assertEquals(fromVerify, fromSend)
    }

    @Test
    fun `AuthException 이 아닌 예외는 안내 문구로 뭉갠다`() {
        assertEquals("잠시 후 다시 시도해 주세요", IllegalStateException("boom").authUserMessage())
        assertEquals(
            authErrorMessage(AuthError.PHONE_NOT_VERIFIED),
            AuthException(AuthError.PHONE_NOT_VERIFIED).authUserMessage(),
        )
    }
}
