package com.moyeota.presentation.feature.mypage

import com.moyeota.presentation.core.SupportLinkTargets
import com.moyeota.presentation.core.SupportLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 35 마이페이지 메뉴 구성 규칙.
 *
 * **여기서 테스트가 꼭 필요한 이유:** 「주소가 없으면 행이 사라진다」는 동작을 눈으로 확인하려면
 * 소스의 상수를 바꿔 다시 빌드해야 한다. 실제로 QA 가 그렇게 돌렸고, 되돌리다 사고가 날 뻔했다
 * (`_workspace/113_qa-verifier_report.md` §O4 — 그 파일이 untracked 라 `git checkout` 이 안 됐다).
 * 이제는 값으로 넣어 본다.
 *
 * 주소가 운영 값으로 채워진 뒤에도(2026-10-14) **빈 상태를 계속 테스트한다** — 페이지가 내려가면
 * 그 길로 긴급 복구하게 되고, 그때 레이아웃이 깨지면 고칠 시간이 없다.
 */
class MyPageMenuRulesTest {

    private val allLinks = SupportLinkTargets(
        privacyUrl = "https://example.com/privacy/",
        termsUrl = "https://example.com/terms/",
        contactEmail = "help@example.com",
    )

    private fun List<MyPageGroup>.group(label: String): MyPageGroup? = firstOrNull { it.label == label }

    private fun List<MyPageGroup>.rowTexts(label: String): List<String> =
        group(label)?.rows?.map { it.text } ?: emptyList()

    // ─────────────────────────── 그룹 구성 ───────────────────────────

    @Test
    fun `주소가 다 있으면 설정 안내 계정 세 그룹이 이 순서로 선다`() {
        val groups = myPageGroups(allLinks)

        assertEquals(
            listOf(MyPageGroupLabel.SETTINGS, MyPageGroupLabel.INFO, MyPageGroupLabel.ACCOUNT),
            groups.map { it.label },
        )
    }

    /** 빈 카드·머리말만 남은 그룹은 "고장난 메뉴"로 보인다. 그룹마다가 아니라 한 규칙으로 막는다. */
    @Test
    fun `행이 없는 그룹은 아예 만들지 않는다`() {
        listOf(allLinks, SupportLinkTargets.None, SupportLinkTargets(termsUrl = null)).forEach { links ->
            myPageGroups(links).forEach { group ->
                assertTrue("행 없는 그룹이 남았다: ${group.label}", group.rows.isNotEmpty())
            }
        }
    }

    // ─────────────────────────── 주소가 없을 때 ───────────────────────────

    /**
     * 셋 다 비면 「안내」는 **머리말과 카드까지** 사라진다. 「준비 중」 자리표시를 두지 않는 이유는
     * 눌러도 아무 일이 없는 행이 고장으로 읽히고, 스토어 심사도 죽은 링크를 잡기 때문이다.
     */
    @Test
    fun `주소가 전부 없으면 안내 그룹이 통째로 사라진다`() {
        val groups = myPageGroups(SupportLinkTargets.None)

        assertNull("안내 카드가 빈 채로 남았다", groups.group(MyPageGroupLabel.INFO))
        // 주소와 무관한 두 그룹은 그대로 있어야 한다 — 로그아웃·탈퇴가 사라지면 갇힌다.
        assertEquals(listOf("알림 설정"), groups.rowTexts(MyPageGroupLabel.SETTINGS))
        assertEquals(listOf("로그아웃", "탈퇴하기"), groups.rowTexts(MyPageGroupLabel.ACCOUNT))
    }

    /** 세 행은 서로 독립이다 — 하나가 비어도 나머지는 남는다. */
    @Test
    fun `주소 하나가 없으면 그 행만 빠진다`() {
        assertEquals(
            listOf("이용약관", "문의하기"),
            myPageGroups(allLinks.copy(privacyUrl = null)).rowTexts(MyPageGroupLabel.INFO),
        )
        assertEquals(
            listOf("개인정보 처리방침", "문의하기"),
            myPageGroups(allLinks.copy(termsUrl = null)).rowTexts(MyPageGroupLabel.INFO),
        )
        assertEquals(
            listOf("개인정보 처리방침", "이용약관"),
            myPageGroups(allLinks.copy(contactEmail = null)).rowTexts(MyPageGroupLabel.INFO),
        )
    }

    @Test
    fun `주소가 없으면 그 주소로 가는 동작도 사라진다`() {
        val actions = myPageGroups(SupportLinkTargets.None).flatMap { it.rows }.map { it.action }

        assertTrue(
            "주소 없는데 외부 이동 동작이 남았다: $actions",
            actions.none { it is MyPageAction.OpenWeb || it is MyPageAction.SendMail },
        )
    }

    // ─────────────────────────── 어느 행이 어디로 가는가 ───────────────────────────

    @Test
    fun `안내 행은 각자 받은 주소로 나간다`() {
        val rows = myPageGroups(allLinks).group(MyPageGroupLabel.INFO)!!.rows

        assertEquals(MyPageAction.OpenWeb(allLinks.privacyUrl!!), rows[0].action)
        assertEquals(MyPageAction.OpenWeb(allLinks.termsUrl!!), rows[1].action)
        assertEquals(MyPageAction.SendMail(allLinks.contactEmail!!), rows[2].action)
    }

    /** 기본값은 운영 주소다 — 화면이 아무것도 넘기지 않아도 운영 값으로 선다. */
    @Test
    fun `기본 주소 묶음은 SupportLinks 를 그대로 쓴다`() {
        val targets = SupportLinkTargets()

        assertEquals(SupportLinks.PRIVACY_URL, targets.privacyUrl)
        assertEquals(SupportLinks.TERMS_URL, targets.termsUrl)
        assertEquals(SupportLinks.CONTACT_EMAIL, targets.contactEmail)
    }

    /**
     * 주소가 채워져 있다면 **모양이 맞아야** 한다. 값 자체를 못 박지는 않는다 —
     * 페이지가 내려갔을 때 null 로 비우는 길이 테스트 때문에 막히면 안 된다(`SupportLinks` KDoc).
     */
    @Test
    fun `채워진 운영 주소는 공개 https 주소와 메일 형식이다`() {
        listOf(SupportLinks.PRIVACY_URL, SupportLinks.TERMS_URL, SupportLinks.ACCOUNT_DELETION_URL)
            .filterNotNull()
            .forEach { url ->
                assertTrue("로그인 없이 열리는 https 주소여야 한다: $url", url.startsWith("https://"))
                assertFalse("주소에 공백이 있다: $url", url.contains(" "))
            }
        SupportLinks.CONTACT_EMAIL?.let { email ->
            assertTrue("메일 주소가 아니다: $email", email.contains("@") && !email.contains(" "))
        }
    }

    // ─────────────────────────── 기호 규칙 (↗ / ›) ───────────────────────────

    /**
     * 이 화면의 기호는 뜻이 둘뿐이다 — ↗ 는 앱을 벗어나고, › 는 앱 안에서 다음 단계로 간다.
     * 아이콘 세트가 없는 앱이라 이 두 기호가 유일한 비언어 단서다. 어긋나면 규칙이 무의미해진다.
     */
    @Test
    fun `앱 밖으로 나가는 행은 전부 외부 표시다`() {
        myPageGroups(allLinks).flatMap { it.rows }.forEach { row ->
            val leavesApp = when (row.action) {
                MyPageAction.NotificationSettings,
                is MyPageAction.OpenWeb,
                is MyPageAction.SendMail,
                -> true
                MyPageAction.Logout, MyPageAction.DeleteAccount -> false
            }
            val expected = if (leavesApp) MyPageRowMark.EXTERNAL else MyPageRowMark.CHEVRON
            assertEquals(row.text, expected, row.mark)
        }
    }

    // ─────────────────────────── 계정 카드 ───────────────────────────

    /**
     * 로그아웃과 탈퇴가 **같은 카드에 나란히** 있다(레퍼런스 구조). 로그아웃이 13sp 회색 글자였던
     * 동안에는 훨씬 자주 쓰는 기능이 더 작아 위계가 뒤집혀 있었다. 파괴적인 쪽이 아래다.
     */
    @Test
    fun `계정 카드는 로그아웃 다음에 탈퇴하기다`() {
        val rows = myPageGroups(allLinks).group(MyPageGroupLabel.ACCOUNT)!!.rows

        assertEquals(listOf("로그아웃", "탈퇴하기"), rows.map { it.text })
        assertEquals(MyPageAction.Logout, rows[0].action)
        assertEquals(MyPageAction.DeleteAccount, rows[1].action)
    }

    /** 카드 안에서 빨강이 하나뿐이라야 경고로 읽힌다. 로그아웃은 되돌릴 수 있는 동작이다. */
    @Test
    fun `탈퇴하기만 빨간 행이다`() {
        val danger = myPageGroups(allLinks).flatMap { it.rows }.filter { it.danger }

        assertEquals(listOf("탈퇴하기"), danger.map { it.text })
    }

    /**
     * 부연은 **「탈퇴하면」으로 시작해야 한다.** 두 행이 한 카드에 들어오면서, 주어 없는 문장은
     * 바로 위 로그아웃에까지 걸리는 말로 읽힌다 — 로그아웃은 아무것도 지우지 않는다.
     */
    @Test
    fun `계정 카드 부연은 탈퇴를 가리킨다`() {
        val caption = myPageGroups(allLinks).group(MyPageGroupLabel.ACCOUNT)!!.caption

        assertEquals(ACCOUNT_CARD_CAPTION, caption)
        assertTrue(caption!!, caption.startsWith("탈퇴하면"))
        assertTrue(caption, caption.contains("되돌릴 수 없"))
    }

    /** 부연은 「계정」 카드에만 있다 — 설정·안내 행에 경고를 달 이유가 없다. */
    @Test
    fun `부연은 계정 카드에만 붙는다`() {
        myPageGroups(allLinks).filter { it.label != MyPageGroupLabel.ACCOUNT }.forEach {
            assertNull("${it.label} 그룹에 부연이 붙었다", it.caption)
        }
    }

    // ─────────────────────────── 없는 기능을 만들지 않는다 ───────────────────────────

    /**
     * 서버 API 가 없는 메뉴는 행을 만들지 않는다(`docs/BACKEND-GAP-CLEANUP.md`).
     * 레퍼런스 앱의 「로그인 수단 안내」도 같은 이유로 없다 — 이 화면이 아는 서버 값은 이름뿐이다.
     */
    @Test
    fun `서버가 주지 않는 것으로 행을 만들지 않는다`() {
        val texts = myPageGroups(allLinks).flatMap { it.rows }.map { it.text }

        listOf("탑승 기록", "고객센터", "매너 점수", "마일리지", "결제", "로그인 수단").forEach { absent ->
            assertFalse("없는 기능 행이 생겼다: $absent", texts.any { it.contains(absent) })
        }
        // 행 이름이 겹치면 사용자가 같은 것을 두 군데서 찾는다
        assertEquals(texts.size, texts.toSet().size)
    }
}
