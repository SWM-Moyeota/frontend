package com.moyeota.presentation.feature.auth

import com.moyeota.domain.model.AuthPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 10 프로필 만들기(와 09 본인 인증)의 생년월일 판정 — **만 14세 미만 가입 차단**이 핵심이다.
 *
 * 문구까지 못 박는 이유: 개인정보 처리방침에 「만 14세 미만은 가입할 수 없습니다」로 고지할 예정이고,
 * 서버에는 연령 제한이 없어(`UserRegisterRequest` 는 `@Past` 뿐) **이 판정이 앱의 유일한 방어선**이다.
 * 고지와 동작이 어긋나면 둘 중 하나가 거짓이 되고, 뭉뚱그린 문구를 쓰면 사용자는 왜 막혔는지 모른다.
 *
 * 기준일을 전부 고정값([Today])으로 넣는다 — 경계값(생일 하루 전/당일/하루 뒤)이 이 테스트의
 * 전부인데 기기 시계에 묶이면 셋 중 어느 것도 검증할 수 없다. 기준일을 **인자로 받는 설계**가
 * 그래서 필요했다([AuthPolicy.isOldEnough]).
 */
class BirthDateRulesTest {

    /** 고정 기준일. 아래 생년월일은 모두 이 날짜에 대해 계산한다. */
    private val Today = LocalDate.of(2026, 10, 13)

    /**
     * 기대 문구를 상수가 아니라 **글자 그대로** 적는다. [AuthPolicy.MIN_AGE] 를 고치면 이 테스트가
     * 깨져야 한다 — 숫자만 바꾸고 개인정보 처리방침 고지를 그대로 두는 일을 막는 장치다.
     */
    private val UnderAgeMessage = "만 14세 미만은 가입할 수 없어요. 생년월일을 다시 확인해 주세요"
    private val FutureMessage = "생년월일은 오늘 이전 날짜로 입력해 주세요"
    private val NotADateMessage = "실제로 있는 날짜를 YYYYMMDD 로 입력해 주세요"

    private fun digits(date: LocalDate): String = date.format(DateTimeFormatter.BASIC_ISO_DATE)

    /** 10·09 의 「다음」 판정과 같은 식 — 문구가 남아 있으면 넘어가지 않는다. */
    private fun canProceed(digits: String, today: LocalDate): Boolean =
        parseBirthDate(digits, today) != null && birthDateError(digits, today) == null

    // ─────────────────────────── 경계값: 만 14세 생일 ───────────────────────────

    @Test
    fun `만 14세 생일 하루 전이면 가입할 수 없다`() {
        // 2012-10-14 생은 2026-10-14 에 만 14세가 된다 — 기준일은 그 하루 전이다
        val birth = LocalDate.of(2012, 10, 14)

        assertFalse(AuthPolicy.isOldEnough(birth, Today))
        assertFalse(canProceed(digits(birth), Today))
        assertEquals(UnderAgeMessage, birthDateError(digits(birth), Today))
    }

    @Test
    fun `만 14세 생일 당일부터 가입할 수 있다`() {
        val birth = LocalDate.of(2012, 10, 13)

        assertTrue("생일 당일에 만 14세가 된다", AuthPolicy.isOldEnough(birth, Today))
        assertTrue(canProceed(digits(birth), Today))
        assertNull(birthDateError(digits(birth), Today))
    }

    @Test
    fun `만 14세 생일 하루 뒤도 통과한다`() {
        val birth = LocalDate.of(2012, 10, 12)

        assertTrue(AuthPolicy.isOldEnough(birth, Today))
        assertNull(birthDateError(digits(birth), Today))
    }

    /** 연도 빼기로 계산하면 통과해 버리는 입력 — 만 나이로 세야 하는 이유 그 자체다. */
    @Test
    fun `연도 차이가 14여도 생일 전이면 거절한다`() {
        val birth = LocalDate.of(2012, 12, 31)
        val newYearsDay = LocalDate.of(2026, 1, 1) // 2026 - 2012 = 14

        assertFalse("아직 13세다", AuthPolicy.isOldEnough(birth, newYearsDay))
        assertEquals(UnderAgeMessage, birthDateError(digits(birth), newYearsDay))
        // 그 해 생일이 오면 통과한다
        assertTrue(AuthPolicy.isOldEnough(birth, LocalDate.of(2026, 12, 31)))
    }

    // ─────────────────────────── 윤년 2월 29일생 ───────────────────────────

    /**
     * 평년에는 2월 29일이 없다. 민법 제160조 ③(해당일이 없으면 그 달 말일)과 같게
     * **2월 28일에 만 14세가 된다** — 법적으로 14세인 사람을 하루 더 막지 않는다.
     * 무엇보다 **어떤 조합에서도 예외를 던지지 않아야 한다**(윤년 계산은 그래서 직접 하지 않는다).
     */
    @Test
    fun `윤년 2월 29일생은 평년에도 예외 없이 판정된다`() {
        val birth = LocalDate.of(2008, 2, 29)

        assertFalse("2월 27일은 아직 13세", AuthPolicy.isOldEnough(birth, LocalDate.of(2022, 2, 27)))
        assertTrue("평년 생일은 2월 28일", AuthPolicy.isOldEnough(birth, LocalDate.of(2022, 2, 28)))
        assertTrue(AuthPolicy.isOldEnough(birth, LocalDate.of(2022, 3, 1)))
        // 윤년이 돌아온 해의 2월 29일(만 16세)도 그대로 통과한다
        assertTrue(AuthPolicy.isOldEnough(birth, LocalDate.of(2024, 2, 29)))

        assertNull(birthDateError("20080229", LocalDate.of(2022, 2, 28)))
        assertEquals(UnderAgeMessage, birthDateError("20080229", LocalDate.of(2022, 2, 27)))
    }

    /**
     * 기준일이 2월 29일인 경우도 같은 규칙이다 — 경계가 윤일에 걸려도 하루 전은 하루 전이다.
     * (2010-02-29 생은 애초에 [LocalDate] 가 될 수 없어 입력 단계에서 걸린다 — 그래서 여기 없다)
     */
    @Test
    fun `기준일이 윤일이어도 생일 하루 전은 거절한다`() {
        val leapDay = LocalDate.of(2024, 2, 29)

        assertTrue("전날이 생일이면 이미 14세", AuthPolicy.isOldEnough(LocalDate.of(2010, 2, 28), leapDay))
        assertFalse("3월 1일이 생일이면 아직 하루 남았다", AuthPolicy.isOldEnough(LocalDate.of(2010, 3, 1), leapDay))
        // 윤일에 태어난 사람의 생일 당일(평년 2월 28일)은 위 테스트가 본다
        assertNull(birthDateError("20100228", leapDay))
        assertEquals(UnderAgeMessage, birthDateError("20100301", leapDay))
    }

    // ─────────────────────────── 미래 날짜는 기존 검증이 잡는다 ───────────────────────────

    /**
     * 미래 날짜는 **연령과 다른 문구**로 막아야 한다. 서버 `@Past` 에 걸려 400 이 될 값이므로
     * 할 일은 "숫자를 고치는 것"이고, 만 14세 미만은 고칠 게 없는 상황이다.
     */
    @Test
    fun `미래 날짜는 연령과 다른 문구로 막는다`() {
        assertFalse(AuthPolicy.isValidBirthDate(LocalDate.of(2999, 1, 1)))
        assertNull("파싱 단계에서 이미 걸러진다", parseBirthDate("29990101", Today))
        assertEquals(FutureMessage, birthDateError("29990101", Today))
        assertNotEquals(UnderAgeMessage, birthDateError("29990101", Today))
        // 연령 판정에 넣어도 예외 없이 false 다(막는 방향은 같다 — 문구만 다르다)
        assertFalse(AuthPolicy.isOldEnough(LocalDate.of(2999, 1, 1), Today))
    }

    /**
     * 내일은 어느 시계로 봐도 미래다. 실제 시계를 쓰는 이유는 고정 날짜로 적으면 테스트를
     * 돌리는 날에 따라 "내일"이 아니게 되기 때문이다.
     */
    @Test
    fun `내일 날짜는 오늘 이전 날짜 문구로 막는다`() {
        val now = LocalDate.now()

        assertEquals(FutureMessage, birthDateError(digits(now.plusDays(1)), now))
        assertFalse(canProceed(digits(now.plusDays(1)), now))
    }

    /**
     * 오늘 태어난 값도 가입으로 넘어갈 수 없다.
     *
     * **문구는 단정하지 않는다**: UTC 보다 앞선 지역(KST)에서는 서버 `@Past` 에 걸릴 값이라
     * "오늘 이전 날짜로" 가 맞고, UTC 보다 뒤진 지역에서는 서버가 받아 줄 과거 Instant 라
     * 연령 문구가 맞다. 둘 다 사실이므로 **막히는 것만** 못 박는다.
     */
    @Test
    fun `오늘 날짜로는 가입을 진행할 수 없다`() {
        val now = LocalDate.now()

        assertFalse(canProceed(digits(now), now))
        assertNotNull(birthDateError(digits(now), now))
        assertFalse(AuthPolicy.isOldEnough(now, now))
    }

    // ─────────────────────────── 아주 오래된 날짜 ───────────────────────────

    @Test
    fun `1900년생도 예외 없이 통과한다`() {
        val birth = LocalDate.of(1900, 1, 1)

        assertTrue(AuthPolicy.isOldEnough(birth, Today))
        assertEquals(birth, parseBirthDate("19000101", Today))
        assertNull(birthDateError("19000101", Today))
    }

    /** 1900년 이전은 오타로 본다(입력 하한) — 연령 문구가 아니라 형식 문구다. */
    @Test
    fun `1900년보다 이른 해는 오타로 본다`() {
        assertNull(parseBirthDate("18991231", Today))
        assertEquals(NotADateMessage, birthDateError("18991231", Today))
    }

    // ─────────────────────────── 형식·미입력 ───────────────────────────

    @Test
    fun `8자리를 채우기 전에는 아무 말도 하지 않는다`() {
        assertNull(birthDateError("", Today))
        assertNull(birthDateError("2012", Today))
        assertNull(birthDateError("2012101", Today))
        // 그래도 「다음」은 열리지 않는다 — 아직 값이 없다
        assertFalse(canProceed("2012101", Today))
    }

    @Test
    fun `달력에 없는 날짜는 형식 문구로 막는다`() {
        assertEquals(NotADateMessage, birthDateError("20250230", Today))
        assertEquals(NotADateMessage, birthDateError("20251301", Today))
        assertFalse(canProceed("20250230", Today))
    }

    // ─────────────────────────── 문구가 서로 겹치지 않는지 ───────────────────────────

    /**
     * 세 사유의 **할 일이 전부 다르므로** 한 문구도 겹쳐선 안 된다. 특히 연령은
     * "입력이 올바르지 않습니다" 류로 접으면 사용자가 날짜만 고쳐 보다 끝까지 이유를 모른다.
     */
    @Test
    fun `형식 미래 연령 세 문구는 서로 다르고 연령은 이유를 말한다`() {
        val notADate = birthDateError("20250230", Today)
        val future = birthDateError("29990101", Today)
        val underAge = birthDateError("20121014", Today)

        assertNotEquals(notADate, future)
        assertNotEquals(notADate, underAge)
        assertNotEquals(future, underAge)

        assertTrue("무엇이 문제인지 말해야 한다", underAge!!.contains("만 ${AuthPolicy.MIN_AGE}세 미만"))
        assertTrue(underAge.contains("가입할 수 없어요"))
        // 뭉뚱그린 문구 금지
        assertFalse(underAge.contains("올바르지 않"))
    }

    /** 입력 전에 미리 알려주는 안내도 같은 숫자를 본다 — 고지와 동작이 어긋나지 않게. */
    @Test
    fun `생년월일 안내 문구는 같은 최소 연령을 말한다`() {
        assertEquals("만 ${AuthPolicy.MIN_AGE}세 이상만 가입할 수 있어요", BirthDateHelperText)
    }

    // ─────────────────────────── 09 본인 인증 ───────────────────────────

    /**
     * 09 는 현재 어떤 그래프에도 등록되어 있지 않지만 코드는 살아 있고, 같은 파싱·검증을 쓴다.
     * 되살아날 때 연령 제한이 빠진 채로 돌아오지 않게 **기본 기준일(기기 시계)로도** 확인한다.
     */
    @Test
    fun `기준일을 생략하면 기기 시계를 쓴다`() {
        val thirteen = LocalDate.now().minusYears(13)

        assertEquals(UnderAgeMessage, birthDateError(digits(thirteen)))
        // 파싱은 통과한다 — 만 13세도 "실제로 있는 과거 날짜"다. 그래서 09 의 CTA 는 문구까지 봐야 한다
        assertEquals(thirteen, parseBirthDate(digits(thirteen)))

        val adult = LocalDate.now().minusYears(30)
        assertNull(birthDateError(digits(adult)))
        assertEquals(adult, parseBirthDate(digits(adult)))
    }
}
