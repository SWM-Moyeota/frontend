package com.moyeota.presentation.core

import com.moyeota.presentation.feature.home.reservationCommitmentNotice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 한국어 안내문의 **어절 단위 줄바꿈 폴백**(API 32 이하) — [keepKoreanWordsTogether].
 *
 * 화면에서는 「요」가 혼자 떨어지느냐로만 보이는 결함이라 스크린샷 없이는 잡히지 않는다. 그래서
 * 「어디서 끊을 수 있는가」를 문자열 수준에서 고정한다: 띄어쓰기가 아닌 자리에는 WORD JOINER 가
 * 있어야 하고, 마지막 두 어절은 NBSP 로 묶여야 한다. 그리고 **보이는 글자는 원문과 같아야** 한다.
 */
class KoreanLineBreakTest {

    /** 보이지 않는 표시를 걷어내면 원문이 돌아와야 한다 — 글자를 바꾸면 안 된다. */
    private fun String.visible(): String =
        replace(WORD_JOINER.toString(), "").replace(NO_BREAK_SPACE, ' ')

    @Test
    fun `보이는 글자는 원문과 같다`() {
        val notice = reservationCommitmentNotice(joining = false)

        assertEquals(notice, keepKoreanWordsTogether(notice).visible())
    }

    @Test
    fun `어절 안 글자 사이에는 줄바꿈을 막는 표시가 들어간다`() {
        val result = keepKoreanWordsTogether("기록돼요")

        assertEquals("기⁠록⁠돼⁠요", result)
    }

    @Test
    fun `마지막 두 어절은 끊기지 않는 공백으로 묶인다`() {
        val result = keepKoreanWordsTogether("노쇼로 기록돼요")

        // 「노쇼로」와 「기록돼요」 사이가 NBSP — 마지막 줄에 어절 하나만 남는 일이 없다
        assertEquals("노⁠쇼⁠로 기⁠록⁠돼⁠요", result)
    }

    @Test
    fun `마지막이 아닌 띄어쓰기는 줄바꿈 자리로 남는다`() {
        val result = keepKoreanWordsTogether("출발 시각에 안 가면")

        // 공백은 셋, 그중 마지막 하나만 NBSP 다
        assertEquals(2, result.count { it == ' ' })
        assertEquals(1, result.count { it == NO_BREAK_SPACE })
        // 띄어쓰기 바로 옆에는 WORD JOINER 를 넣지 않는다(그 자리는 끊을 수 있어야 한다)
        assertFalse(result.contains(" $WORD_JOINER"))
        assertFalse(result.contains("$WORD_JOINER "))
    }

    @Test
    fun `마침표는 앞 어절에 붙어 다닌다`() {
        // 「없어요.」 의 마침표가 다음 줄 첫 글자가 되지 않는다(마침표도 어절의 일부다)
        assertTrue(keepKoreanWordsTogether("없어요. 출발").startsWith("없⁠어⁠요⁠."))
    }

    @Test
    fun `짧거나 빈 문자열은 그대로다`() {
        assertEquals("", keepKoreanWordsTogether(""))
        assertEquals("요", keepKoreanWordsTogether("요"))
    }

    @Test
    fun `API 33 이상은 원문을 그대로 그리고 그 아래만 폴백한다`() {
        val notice = reservationCommitmentNotice(joining = false)

        assertEquals(notice, koreanParagraphDisplayText(notice, sdkInt = PHRASE_LINE_BREAK_MIN_SDK))
        assertEquals(notice, koreanParagraphDisplayText(notice, sdkInt = 37))
        assertEquals(keepKoreanWordsTogether(notice), koreanParagraphDisplayText(notice, sdkInt = 32))
        assertEquals(keepKoreanWordsTogether(notice), koreanParagraphDisplayText(notice, sdkInt = 24))
    }
}
