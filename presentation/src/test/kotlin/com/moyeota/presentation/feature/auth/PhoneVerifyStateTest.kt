package com.moyeota.presentation.feature.auth

import com.moyeota.domain.model.AuthError
import com.moyeota.domain.model.AuthPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 10 프로필 만들기의 문자 인증 블록 상태 전이.
 *
 * 왜 테스트가 필요한가: 이 블록이 틀려도 **화면에는 아무 증상이 없다**. 토큰이 비거나 번호와
 * 어긋난 채로 제출되면 가입이 400 으로 떨어지는데, 사용자에게는 "입력한 정보를 다시 확인해
 * 주세요" 한 줄만 보인다(서버가 토큰 문제와 입력 문제를 같은 400 으로 뭉친다). 그래서
 * 전이 규칙은 Compose 밖의 순수 함수로 빼 두고 여기서 못 박는다.
 *
 * 타이머는 기한(epoch millis) 기반이라 실제 시간이 흐르기를 기다리지 않는다 — [NOW] 를 옮겨 쓴다.
 */
class PhoneVerifyStateTest {

    /** 임의의 고정 시각. 값 자체에 의미는 없고, 계산이 상대값으로만 이뤄지는지를 보려고 큰 수를 쓴다. */
    private val NOW = 1_800_000_000_000L
    private val PHONE = "01012345678"
    private val CODE_TTL_MS = AuthPolicy.SMS_CODE_TTL_SECONDS * 1000L
    private val COOLDOWN_MS = AuthPolicy.SMS_RESEND_COOLDOWN_SECONDS * 1000L

    /** 발송까지 끝난 상태(= 인증번호 입력칸이 열린 상태). */
    private fun sent(now: Long = NOW) =
        PhoneVerifyState().onRequestStart(PHONE).onCodeSent(now)

    /** 인증까지 끝난 상태. */
    private fun verified(now: Long = NOW) = sent(now).onCodeChanged("123456").onVerified("tok-1")

    // ─────────────────────────────── 남은 초 계산·표시 ───────────────────────────────

    @Test
    fun `남은 초는 올림한다 - 0_2초 남았으면 1초로 보여야 사라지지 않는다`() {
        assertEquals(1, secondsUntil(NOW + 200, NOW))
        assertEquals(1, secondsUntil(NOW + 1_000, NOW))
        assertEquals(2, secondsUntil(NOW + 1_001, NOW))
    }

    @Test
    fun `기한이 지났거나 없으면 0`() {
        assertEquals(0, secondsUntil(NOW, NOW))
        assertEquals(0, secondsUntil(NOW - 5_000, NOW))
        assertEquals(0, secondsUntil(NO_DEADLINE, NOW))
    }

    @Test
    fun `카운트다운 표시는 m ss`() {
        assertEquals("3:00", formatRemainingSeconds(180))
        assertEquals("1:05", formatRemainingSeconds(65))
        assertEquals("0:09", formatRemainingSeconds(9))
        assertEquals("0:00", formatRemainingSeconds(0))
    }

    @Test
    fun `시계가 뒤로 가도 음수를 그리지 않는다`() {
        assertEquals("0:00", formatRemainingSeconds(-7))
    }

    // ─────────────────────────────── 발송 버튼 판정 ───────────────────────────────

    @Test
    fun `번호가 11자리 010 이면 인증번호를 받을 수 있다`() {
        assertTrue(PhoneVerifyState().canRequestCode(PHONE, NOW))
    }

    @Test
    fun `번호가 아직 덜 찼으면 받을 수 없다`() {
        assertFalse(PhoneVerifyState().canRequestCode("0101234", NOW))
        assertFalse(PhoneVerifyState().canRequestCode("", NOW))
        // 010 이 아닌 번호는 이 화면이 받지 않는다(안내 문구도 010 기준이다)
        assertFalse(PhoneVerifyState().canRequestCode("01112345678", NOW))
    }

    @Test
    fun `쿨다운 중에는 받을 수 없고 끝나면 다시 받을 수 있다`() {
        val state = sent()
        assertFalse(state.canRequestCode(PHONE, NOW))
        assertFalse(state.canRequestCode(PHONE, NOW + COOLDOWN_MS - 1))
        assertTrue(state.canRequestCode(PHONE, NOW + COOLDOWN_MS))
    }

    @Test
    fun `보내는 중에는 두 번 눌리지 않는다`() {
        assertFalse(PhoneVerifyState().onRequestStart(PHONE).canRequestCode(PHONE, NOW))
    }

    @Test
    fun `인증을 끝냈으면 더 받을 수 없다`() {
        assertFalse(verified().canRequestCode(PHONE, NOW + CODE_TTL_MS))
    }

    @Test
    fun `이미 가입된 번호와 일일 한도는 재시도를 열지 않는다`() {
        val duplicated = PhoneVerifyState().onRequestStart(PHONE)
            .onRequestFailure(AuthError.PHONE_NUMBER_DUPLICATED, "이미", NOW)
        assertFalse(duplicated.canRequestCode(PHONE, NOW + COOLDOWN_MS))

        val dailyLimit = PhoneVerifyState().onRequestStart(PHONE)
            .onRequestFailure(AuthError.SMS_DAILY_LIMIT_EXCEEDED, "한도", NOW)
        assertFalse(dailyLimit.canRequestCode(PHONE, NOW + COOLDOWN_MS))
    }

    @Test
    fun `버튼 문구는 상태마다 다르다`() {
        assertEquals("인증번호 받기", PhoneVerifyState().requestButtonLabel(NOW))
        assertEquals("인증번호 보내는 중…", PhoneVerifyState().onRequestStart(PHONE).requestButtonLabel(NOW))
        assertEquals("60초 후 다시 받기", sent().requestButtonLabel(NOW))
        assertEquals("1초 후 다시 받기", sent().requestButtonLabel(NOW + COOLDOWN_MS - 1))
        assertEquals("인증번호 다시 받기", sent().requestButtonLabel(NOW + COOLDOWN_MS))
    }

    // ─────────────────────────────── 번호 변경 → 무효화 ───────────────────────────────

    @Test
    fun `번호를 고치면 인증이 무효가 된다`() {
        val before = verified()
        assertEquals("tok-1", before.token)

        val after = before.onPhoneDigitsChanged("01099998888")
        assertEquals(PhoneVerifyStage.Idle, after.stage)
        assertEquals("", after.token)
        assertEquals("", after.boundPhone)
        assertEquals("", after.code)
    }

    @Test
    fun `한 글자만 지워도 무효다 - 서버가 토큰을 번호에 묶는다`() {
        val after = verified().onPhoneDigitsChanged("0101234567")
        assertEquals(PhoneVerifyStage.Idle, after.stage)
        assertEquals("", after.token)
    }

    @Test
    fun `같은 번호면 상태를 그대로 둔다 - 하이픈 표기 차이는 무효화가 아니다`() {
        val before = verified()
        assertSame(before, before.onPhoneDigitsChanged(PHONE))
        assertSame(before, before.onPhoneDigitsChanged("010-1234-5678"))
    }

    @Test
    fun `발송 중에 번호가 바뀌면 묶인 번호도 바뀐다 - 늦게 온 응답을 버릴 근거`() {
        val requesting = PhoneVerifyState().onRequestStart(PHONE)
        assertEquals(PHONE, requesting.boundPhone)
        val changed = requesting.onPhoneDigitsChanged("01099998888")
        // boundPhone 이 비므로 ViewModel 이 "이 응답은 남의 것"이라고 판정할 수 있다
        assertEquals("", changed.boundPhone)
        assertFalse(changed.requesting)
    }

    @Test
    fun `번호 변경 버튼은 안내 문구를 남기지 않고 가입 거절은 남긴다`() {
        assertNull(verified().invalidated().phoneMessage)
        assertEquals("만료됐어요", verified().invalidated("만료됐어요").phoneMessage)
    }

    // ─────────────────────────────── 발송 성공·실패 ───────────────────────────────

    @Test
    fun `발송 성공은 3분 만료와 1분 쿨다운을 같이 건다`() {
        val state = sent()
        assertEquals(PhoneVerifyStage.CodeSent, state.stage)
        assertEquals(AuthPolicy.SMS_CODE_TTL_SECONDS, state.codeRemainingSeconds(NOW))
        assertEquals(AuthPolicy.SMS_RESEND_COOLDOWN_SECONDS, state.resendRemainingSeconds(NOW))
        assertFalse(state.requesting)
    }

    @Test
    fun `재발송은 입력값과 직전 오류를 비운다 - 서버도 오입력 횟수를 되돌린다`() {
        val stale = sent()
            .onCodeChanged("111111")
            .onVerifyFailure(AuthError.SMS_CODE_MISMATCH, "틀렸어요")
        assertEquals("틀렸어요", stale.codeMessage)

        val resent = stale.onRequestStart(PHONE).onCodeSent(NOW + COOLDOWN_MS)
        assertEquals("", resent.code)
        assertNull(resent.codeMessage)
        assertEquals(AuthPolicy.SMS_CODE_TTL_SECONDS, resent.codeRemainingSeconds(NOW + COOLDOWN_MS))
    }

    @Test
    fun `이미 가입된 번호는 인증번호 입력칸을 열지 않는다`() {
        val state = PhoneVerifyState().onRequestStart(PHONE)
            .onRequestFailure(AuthError.PHONE_NUMBER_DUPLICATED, "이미 가입된 번호예요", NOW)
        assertEquals(PhoneVerifyStage.Idle, state.stage)
        assertTrue(state.alreadyRegistered)
        assertEquals("이미 가입된 번호예요", state.phoneMessage)
        assertEquals(0, state.codeRemainingSeconds(NOW))
    }

    @Test
    fun `쿨다운 429 는 남은 시간을 최대치로 다시 잡는다 - 서버가 남은 초를 주지 않는다`() {
        // 앱을 껐다 켠 직후: 발송 시점을 모르니 쿨다운이 0 인 상태에서 눌러 429 를 맞는다
        val restarted = PhoneVerifyState().onRequestStart(PHONE)
        val state = restarted.onRequestFailure(AuthError.SMS_COOLDOWN, "1분에 한 번", NOW)
        assertEquals(AuthPolicy.SMS_RESEND_COOLDOWN_SECONDS, state.resendRemainingSeconds(NOW))
        assertFalse(state.canRequestCode(PHONE, NOW))
    }

    @Test
    fun `쿨다운 429 가 이미 걸린 타이머를 앞당기지는 않는다`() {
        // 남은 쿨다운이 더 길 수도 있다(서버 기준이 앞서 있는 경우) — 짧은 쪽으로 덮으면 또 429 다
        val later = sent(NOW + 30_000)
        val state = later.onRequestFailure(AuthError.SMS_COOLDOWN, "1분에 한 번", NOW)
        assertEquals(90, state.resendRemainingSeconds(NOW))
    }

    @Test
    fun `쿨다운 429 는 열려 있는 입력칸을 닫지 않는다`() {
        val state = sent().onCodeChanged("12")
            .onRequestFailure(AuthError.SMS_COOLDOWN, "1분에 한 번", NOW + 1_000)
        assertEquals(PhoneVerifyStage.CodeSent, state.stage)
        assertEquals("12", state.code)
    }

    @Test
    fun `일일 한도는 버튼을 잠근다`() {
        val state = PhoneVerifyState().onRequestStart(PHONE)
            .onRequestFailure(AuthError.SMS_DAILY_LIMIT_EXCEEDED, "하루 5번", NOW)
        assertTrue(state.dailyLimitReached)
        assertFalse(state.canRequestCode(PHONE, NOW))
    }

    @Test
    fun `문자 서비스 장애는 바로 다시 누를 수 있다 - 서버가 쿨다운을 풀어 준다`() {
        val state = PhoneVerifyState().onRequestStart(PHONE)
            .onRequestFailure(AuthError.SMS_UNAVAILABLE, "잠시 뒤", NOW)
        assertTrue(state.canRequestCode(PHONE, NOW))
        assertEquals("잠시 뒤", state.phoneMessage)
    }

    // ─────────────────────────────── 인증번호 입력·확인 ───────────────────────────────

    @Test
    fun `인증번호는 숫자 6자리만 받는다`() {
        assertEquals("123456", sent().onCodeChanged("12ab34-56789").code)
        assertEquals("1234", sent().onCodeChanged("1234").code)
    }

    @Test
    fun `발송 전에는 인증번호를 받지 않는다`() {
        val idle = PhoneVerifyState()
        assertSame(idle, idle.onCodeChanged("123456"))
    }

    @Test
    fun `6자리를 채우기 전에는 확인할 수 없다`() {
        assertFalse(sent().onCodeChanged("12345").canSubmitCode(NOW))
        assertTrue(sent().onCodeChanged("123456").canSubmitCode(NOW))
    }

    @Test
    fun `만료된 뒤에는 확인할 수 없다`() {
        val state = sent().onCodeChanged("123456")
        assertTrue(state.canSubmitCode(NOW + CODE_TTL_MS - 1))
        assertFalse(state.canSubmitCode(NOW + CODE_TTL_MS))
    }

    @Test
    fun `확인 중에는 두 번 보내지 않는다`() {
        assertFalse(sent().onCodeChanged("123456").onVerifyStart().canSubmitCode(NOW))
    }

    @Test
    fun `인증 성공은 토큰을 들고 타이머를 멈춘다`() {
        val state = verified()
        assertEquals(PhoneVerifyStage.Verified, state.stage)
        assertEquals("tok-1", state.token)
        assertEquals(0, state.codeRemainingSeconds(NOW))
        assertEquals(0, state.resendRemainingSeconds(NOW))
        assertFalse(state.hasRunningCountdown(NOW))
        assertNull(state.codeMessage)
    }

    @Test
    fun `불일치는 입력칸만 비우고 같은 인증번호를 계속 쓴다`() {
        val state = sent().onCodeChanged("111111")
            .onVerifyFailure(AuthError.SMS_CODE_MISMATCH, "일치하지 않아요")
        assertEquals(PhoneVerifyStage.CodeSent, state.stage)
        assertEquals("", state.code)
        assertEquals("일치하지 않아요", state.codeMessage)
        // 아직 서버에 인증번호가 남아 있다 → 타이머도 그대로
        assertEquals(AuthPolicy.SMS_CODE_TTL_SECONDS, state.codeRemainingSeconds(NOW))
    }

    @Test
    fun `5회 초과와 만료는 입력칸을 닫고 재발송으로 보낸다`() {
        for (error in listOf(AuthError.SMS_TOO_MANY_ATTEMPTS, AuthError.SMS_CODE_EXPIRED)) {
            val state = sent().onCodeChanged("111111").onVerifyFailure(error, "다시 받아 주세요")
            assertEquals(error.name, 0, state.codeRemainingSeconds(NOW))
            assertEquals(error.name, "", state.code)
            assertNotNull(error.name, state.codeMessage)
            // 쿨다운은 건드리지 않는다 — 발송 1분 안에 5회를 틀릴 수 있어서 그때는 기다려야 한다
            assertEquals(error.name, AuthPolicy.SMS_RESEND_COOLDOWN_SECONDS, state.resendRemainingSeconds(NOW))
        }
    }

    @Test
    fun `번호 형식 오류는 1단계로 되돌린다 - 고칠 곳이 인증번호가 아니다`() {
        val state = sent().onCodeChanged("123456")
            .onVerifyFailure(AuthError.INVALID_PHONE_NUMBER, "형식을 확인해 주세요")
        assertEquals(PhoneVerifyStage.Idle, state.stage)
        assertEquals("형식을 확인해 주세요", state.phoneMessage)
        assertNull(state.codeMessage)
    }

    @Test
    fun `네트워크 실패는 입력값을 남겨 둔다 - 확인 버튼으로 재시도한다`() {
        val state = sent().onCodeChanged("123456")
            .onVerifyFailure(AuthError.NETWORK, "연결을 확인해 주세요")
        assertEquals("123456", state.code)
        assertTrue(state.canSubmitCode(NOW))
    }

    // ─────────────────────────────── 3분 자체 만료 ───────────────────────────────

    @Test
    fun `3분이 지나면 서버에 묻지 않고 입력칸을 닫는다`() {
        val state = sent().onCodeChanged("123456").onCodeExpired("만료됐어요")
        assertEquals("", state.code)
        assertEquals(0, state.codeRemainingSeconds(NOW))
        assertEquals("만료됐어요", state.codeMessage)
        assertFalse(state.canSubmitCode(NOW))
    }

    @Test
    fun `만료 전이는 두 번 적용되지 않는다 - 틱마다 불려도 안전해야 한다`() {
        val expired = sent().onCodeExpired("만료됐어요")
        assertSame(expired, expired.onCodeExpired("다른 문구"))
    }

    @Test
    fun `인증을 끝낸 뒤에는 만료 전이가 상태를 건드리지 않는다`() {
        val state = verified()
        assertSame(state, state.onCodeExpired("만료됐어요"))
    }

    @Test
    fun `카운트다운이 남아 있는 동안만 틱이 필요하다`() {
        val state = sent()
        assertTrue(state.hasRunningCountdown(NOW))
        assertTrue(state.hasRunningCountdown(NOW + COOLDOWN_MS))
        assertFalse(state.hasRunningCountdown(NOW + CODE_TTL_MS))
    }

    // ─────────────────────────────── 가입 실패 후 재시도 ───────────────────────────────

    @Test
    fun `가입 실패로 인증을 되돌리는 경우는 PHONE_NOT_VERIFIED 하나뿐이다`() {
        assertTrue(signupErrorNeedsReverify(AuthError.PHONE_NOT_VERIFIED))
        // 토큰은 가입이 다 저장된 뒤에야 소진된다 — 중복 409 는 같은 토큰으로 재시도할 수 있다
        assertFalse(signupErrorNeedsReverify(AuthError.NICKNAME_DUPLICATED))
        assertFalse(signupErrorNeedsReverify(AuthError.LOGIN_ID_DUPLICATED))
        assertFalse(signupErrorNeedsReverify(AuthError.PHONE_NUMBER_DUPLICATED))
        assertFalse(signupErrorNeedsReverify(AuthError.INVALID_NICKNAME))
        assertFalse(signupErrorNeedsReverify(AuthError.NETWORK))
        assertFalse(signupErrorNeedsReverify(null))
    }
}
