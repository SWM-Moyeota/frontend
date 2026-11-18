package com.moyeota.presentation.feature.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 10 프로필 만들기의 **닉네임 금칙어 문구**.
 *
 * 2026-11-17 QA: 「모두모여타요타요」를 넣으면 「사용할 수 없는 닉네임이에요」만 떴다. 사칭 뜻이 없는 닉네임도
 * 「모여타」가 들어 있으면 막히는데, 무엇이 걸렸는지 말하지 않으면 사용자는 어디를 고쳐야 할지 모른다.
 */
class NicknamePolicyTest {

    @Test
    fun `운영자 사칭어는 걸린 단어를 그대로 보여 준다`() {
        val message = NicknamePolicy.validate("모두모여타요타요")!!
        assertTrue(message, message.contains("「모여타」"))
        assertTrue(message, message.contains("운영자"))
    }

    @Test
    fun `영문 사칭어는 대소문자를 가리지 않는다`() {
        val message = NicknamePolicy.validate("SuperAdmin")!!
        assertTrue(message, message.contains("「admin」"))
        assertFalse(NicknamePolicy.isValid("ADMIN1"))
    }

    /** 욕설은 화면에 다시 적지 않는다 — 걸렸다는 사실만 말한다. */
    @Test
    fun `욕설은 단어를 되읽어 주지 않는다`() {
        assertEquals("욕설·비속어가 들어간 닉네임은 쓸 수 없어요", NicknamePolicy.validate("시발택시"))
    }

    @Test
    fun `금칙어가 없으면 통과한다`() {
        assertNull(NicknamePolicy.validate("모여요"))
        assertNull(NicknamePolicy.validate("택시친구"))
    }
}
