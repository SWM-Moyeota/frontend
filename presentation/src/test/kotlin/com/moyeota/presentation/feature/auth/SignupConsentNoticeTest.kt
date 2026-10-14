package com.moyeota.presentation.feature.auth

import androidx.compose.ui.text.LinkAnnotation
import com.moyeota.presentation.core.SupportLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 04 로그인의 「가입하면 이용약관 · 개인정보 처리방침에 동의하게 돼요」.
 *
 * 2026-10-14 까지 이 줄에는 **아무 일도 하지 않는 `clickable`** 이 걸려 있었다(열 주소가 없었다).
 * 주소가 생긴 뒤에 고쳤고, 여기서 고정하는 것은 두 가지다 — ① 문장은 주소 유무와 무관하게 같다,
 * ② 주소가 없는 문서는 링크가 되지 않는다(죽은 링크를 보여 주지 않는다).
 */
class SignupConsentNoticeTest {

    private val terms = "https://example.com/terms/"
    private val privacy = "https://example.com/privacy/"

    private fun urlsOf(notice: androidx.compose.ui.text.AnnotatedString): List<String> =
        notice.getLinkAnnotations(0, notice.length)
            .mapNotNull { (it.item as? LinkAnnotation.Url)?.url }

    @Test
    fun `주소가 있으면 두 문서가 각자 링크다`() {
        val notice = signupConsentNotice(termsUrl = terms, privacyUrl = privacy) {}

        assertEquals(listOf(terms, privacy), urlsOf(notice))
    }

    /**
     * 동의하게 된다는 사실은 주소와 무관하게 참이다 — 주소가 없을 때 문장을 바꾸거나 지우면
     * **고지가 사라진다.** 링크만 평범한 글자로 남는다.
     */
    @Test
    fun `문장은 주소가 없어도 그대로다`() {
        val withLinks = signupConsentNotice(termsUrl = terms, privacyUrl = privacy) {}
        val without = signupConsentNotice(termsUrl = null, privacyUrl = null) {}

        assertEquals(withLinks.text, without.text)
        assertEquals("가입하면 이용약관 · 개인정보 처리방침에 동의하게 돼요", without.text)
        assertTrue("주소가 없는데 링크가 생겼다", urlsOf(without).isEmpty())
    }

    @Test
    fun `주소가 하나만 있으면 그 문서만 링크다`() {
        assertEquals(
            listOf(privacy),
            urlsOf(signupConsentNotice(termsUrl = null, privacyUrl = privacy) {}),
        )
        assertEquals(
            listOf(terms),
            urlsOf(signupConsentNotice(termsUrl = terms, privacyUrl = null) {}),
        )
    }

    /**
     * 플랫폼 URI 핸들러에 맡기지 않는다 — 브라우저가 없는 기기에서 예외를 던진다.
     * 앱의 다른 외부 이동과 같은 길(`core/ExternalIntents`)로 보내 안내 토스트로 바꾼다.
     */
    @Test
    fun `링크를 누르면 받은 콜백으로 주소가 온다`() {
        val opened = mutableListOf<String>()
        val notice = signupConsentNotice(termsUrl = terms, privacyUrl = privacy) { opened += it }

        notice.getLinkAnnotations(0, notice.length).forEach { range ->
            val link = range.item as LinkAnnotation.Url
            link.linkInteractionListener?.onClick(link)
        }

        assertEquals(listOf(terms, privacy), opened)
    }

    /** 운영 기본값으로 불러도 같은 문장이다(지금은 두 주소가 다 채워져 있다). */
    @Test
    fun `기본값은 SupportLinks 의 주소를 쓴다`() {
        val notice = signupConsentNotice {}

        assertEquals(
            listOfNotNull(SupportLinks.TERMS_URL, SupportLinks.PRIVACY_URL),
            urlsOf(notice),
        )
    }
}
