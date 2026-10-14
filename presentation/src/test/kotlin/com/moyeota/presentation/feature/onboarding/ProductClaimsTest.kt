package com.moyeota.presentation.feature.onboarding

import com.moyeota.presentation.feature.auth.LoginTrustBullets
import com.moyeota.presentation.feature.auth.mannerPledgeItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 가입 전에 사용자가 읽는 문구(01~03 온보딩 · 04 시작 · 12 매너 서약)가 **없는 기능을 약속하지
 * 않는지** 못 박는다.
 *
 * 문구를 테스트하는 게 과해 보일 수 있지만 여기서는 **문구가 고지**다(37 탈퇴 경고를
 * `AccountDeleteRulesTest` 가 못 박는 것과 같은 이유). 기능은 지웠는데 그걸 자랑하던 문구만 남는
 * 사고가 실제로 일어났고(2026-10-13, `_workspace/127_compose-builder_false-claims.md`),
 * 그때 지운 주장이 다시 들어오는 것을 막는 것이 이 테스트의 유일한 목적이다.
 *
 * **금지어를 늘릴 때는 그 기능이 없다는 근거를 코드에서 확인하고 추가한다.** 기능이 생기면
 * 이 테스트에서 먼저 빼라 — 테스트가 사실보다 오래 남으면 그게 또 거짓이 된다.
 */
class ProductClaimsTest {

    private val onboardingPages = listOf(
        OnboardingSavingCopy,
        OnboardingTrustCopy,
        OnboardingMeetingCopy,
    )

    /** 가입 전에 읽히는 모든 문구를 한 덩어리로 본다 — 어느 화면에 섞여 들어와도 걸린다. */
    private val preSignupCopy: String
        get() = (
            onboardingPages.flatMap { listOf(it.headline, it.body) } +
                LoginTrustBullets +
                mannerPledgeItems.flatMap { listOfNotNull(it.title, it.description) }
            ).joinToString(" ")

    // ─────────────────────────── 없는 기능 ───────────────────────────

    /**
     * **성별 조건 매칭은 없다.** 성별은 가입 때 받아 서버 `UserProfile` 에 저장될 뿐이고, 백엔드
     * `matching` 패키지에 성별이 한 번도 등장하지 않는다(`grep -i gender` 로 확인: user/local ·
     * user/profile · user/users/domain/enums 에만 있다). 앱 쪽도 `Gender` 가 가입 요청
     * (`AuthMappers`·`AuthDtos`)에만 쓰이고 파티 DTO 에는 성별 필드가 아예 없다.
     *
     * 성별 **입력란은 지우지 않는다** — 서버 가입 API 가 필수로 받는다. 여기서 막는 것은 성별이
     * 매칭에 쓰인다고 **말하는** 것뿐이다.
     */
    @Test
    fun `가입 전 문구는 성별로 매칭한다고 말하지 않는다`() {
        listOf("같은 성별", "동성 매칭", "동성끼리", "성별끼리").forEach { forbidden ->
            assertFalse("없는 성별 조건 매칭을 약속한다: $forbidden", preSignupCopy.contains(forbidden))
        }
    }

    /**
     * **위치 공유 기능이 없다.** `docs/BACKEND-GAP-CLEANUP.md` §1 이 `RideRepository`의
     * `reportMyLocation`·`getMemberLocations` 와 보호자 공유 카드를 모두 지웠다. 지금 도메인에
     * 남은 위치 조회는 `DispatchRepository.getDriverLocation`(기사 위치, 택시 모드) 하나다.
     *
     * 12 매너 서약의 「운행 중 내 위치를 동승자와 공유하는 데 동의해요」 항목도 같은 이유로 지웠다 —
     * 없는 기능에 동의를 받고 그 동의마저 서버에 보내지 않고 있었다.
     */
    @Test
    fun `가입 전 문구는 위치 공유를 약속하지 않는다`() {
        listOf("위치를 공유", "위치 공유", "실시간 위치", "지인과 공유", "보호자").forEach { forbidden ->
            assertFalse("없는 위치 공유를 약속한다: $forbidden", preSignupCopy.contains(forbidden))
        }
    }

    /**
     * **평가·매너 점수가 없다.** 평가 API 자체가 서버에 없어 `User.rating` 은 항상 0.0 이다
     * (`User` KDoc). 상대에 대해 실제로 보이는 것은 닉네임과 탑승 횟수뿐이다.
     */
    @Test
    fun `가입 전 문구는 평가나 매너 점수를 약속하지 않는다`() {
        listOf("매너 점수", "매너 기록", "별점", "후기", "평가").forEach { forbidden ->
            assertFalse("없는 평가 기능을 약속한다: $forbidden", preSignupCopy.contains(forbidden))
        }
    }

    /**
     * **결제·정산이 없다.** 28~32 결제 화면 5개가 삭제됐고 요금 확정·결제·영수증 API 가 전무하다.
     * 앱이 보여 줄 수 있는 것은 「예상 요금 / 정원」이라는 **추정치**뿐이다.
     *
     * 01 의 「요금이 1/N로 줄어요」는 그 추정치를 말하는 것이라 통과해야 한다 — 금지하는 것은
     * 앱이 정산해 준다는 주장이다.
     */
    @Test
    fun `가입 전 문구는 앱이 정산해 준다고 말하지 않는다`() {
        listOf("정산", "결제", "카드", "자동으로 나눠").forEach { forbidden ->
            assertFalse("없는 결제·정산을 약속한다: $forbidden", preSignupCopy.contains(forbidden))
        }
    }

    /**
     * **동승 모드는 26 운행 중에 닿지 않아 27 긴급 신고로 들어갈 길이 없다**(`docs/MODE-ROUTES.md`).
     * 서버도 `POST /reports` 를 `isRidingMember` 가 아니면 403 `REPORT_NOT_ALLOWED` 로 막는다
     * (`ReportService.report`). 신고 UI 가 없는 것은 그 밖의 신고도 마찬가지다 — 앱은 백엔드의
     * 채팅 신고 API(`ChatReportController`)를 호출하지 않는다.
     */
    @Test
    fun `가입 전 문구는 긴급 신고를 약속하지 않는다`() {
        listOf("긴급 신고", "신고가 바로", "112").forEach { forbidden ->
            assertFalse("닿을 수 없는 화면의 기능을 약속한다: $forbidden", preSignupCopy.contains(forbidden))
        }
    }

    // ─────────────────────────── 지킬 수 없는 약속 ───────────────────────────

    /**
     * 운영이 **몇 시간 안에 무엇을 하겠다**고 약속하지 않는다. 신고를 받을 창구가 문의 메일뿐인데
     * `SupportLinks.CONTACT_EMAIL` 이 null 이라 그 행조차 보이지 않는다 — 지킬 수단이 없다.
     *
     * 기술적으로 즉시인 것(SSE 반영 등)은 이 금지의 대상이 아니다. 여기서 보는 문구에는 그런 말이 없다.
     */
    @Test
    fun `가입 전 문구는 운영 대응 시한을 약속하지 않는다`() {
        listOf("시간 안에", "시간 내", "분 안에", "일 안에", "즉시 확인").forEach { forbidden ->
            assertFalse("지킬 수단이 없는 시한을 약속한다: $forbidden", preSignupCopy.contains(forbidden))
        }
    }

    // ─────────────────────────── 남아야 하는 사실 ───────────────────────────

    /**
     * 거짓을 지운 자리를 빈 채로 두지 않는다. 온보딩 세 장은 **코드로 확인한 네 가지**를 말해야 한다.
     * 하나라도 빠지면 가입 전에 서비스가 무엇을 해 주는지 알 수 없는 화면이 된다.
     */
    @Test
    fun `온보딩은 실제로 참인 것을 말한다`() {
        val all = onboardingPages.joinToString(" ") { it.headline + " " + it.body }

        // 1인 부담 = 예상 요금 / 정원 (Ride.farePerPerson)
        assertTrue(all, all.contains("1/N"))
        // 서버가 USER207 PHONE_NOT_VERIFIED 로 가입을 막는다
        assertTrue(all, all.contains("휴대폰 인증"))
        // ChatRoomMatchingListener.on(PartyMemberJoinedEvent) — 합류 즉시 방이 열린다
        assertTrue(all, all.contains("채팅방이 열려요"))
        // PartyDetailResult.MemberInfo.rideCount
        assertTrue(all, all.contains("탑승 횟수"))
    }

    @Test
    fun `온보딩 세 장이 모두 헤드라인과 본문을 갖는다`() {
        assertEquals(3, onboardingPages.size)
        onboardingPages.forEach {
            assertTrue("빈 헤드라인: $it", it.headline.isNotBlank())
            assertTrue("빈 본문: $it", it.body.isNotBlank())
        }
    }

    /** 04 시작의 신뢰 불릿은 **두 줄**이다 — 카드 여백이 두 줄 기준으로 맞춰져 있다. */
    @Test
    fun `시작 화면 신뢰 불릿은 두 줄이고 비어 있지 않다`() {
        assertEquals(2, LoginTrustBullets.size)
        LoginTrustBullets.forEach { assertTrue("빈 불릿", it.isNotBlank()) }
    }

    // ─────────────────────────── 12 매너 서약 ───────────────────────────

    /**
     * 동의 항목은 **전부 체크해야 가입 버튼이 열린다.** 즉 항목을 하나 넣는 것은 "이걸 동의하지
     * 않으면 가입시키지 않겠다"는 뜻이라, 없는 기능에 대한 동의를 넣으면 사용자가 존재하지 않는
     * 것에 동의해야 가입할 수 있게 된다.
     */
    @Test
    fun `매너 서약 항목은 전부 제목이 있고 네 개다`() {
        assertEquals(4, mannerPledgeItems.size)
        mannerPledgeItems.forEach { assertTrue("빈 제목", it.title.isNotBlank()) }
    }

    /** 설명을 비울 수는 있어도 **공백 문자열로 비우면** 안 된다 — 빈 줄이 그려진다. */
    @Test
    fun `매너 서약 설명은 없거나 내용이 있다`() {
        mannerPledgeItems.mapNotNull { it.description }.forEach {
            assertTrue("공백만 있는 설명", it.isNotBlank())
        }
    }

    /**
     * 「불쾌한 말과 행동을 하지 않을게요」에는 설명을 달지 않는다. 신고를 받을 창구가 앱에 없는
     * 동안에는 「신고가 들어오면 …」으로 줄여 적어도 없는 창구를 있다고 말하는 셈이 된다.
     */
    @Test
    fun `매너 서약은 신고 처리를 약속하지 않는다`() {
        val all = mannerPledgeItems.joinToString(" ") { it.title + " " + (it.description ?: "") }

        assertFalse(all, all.contains("신고가 들어오면"))
        assertFalse(all, all.contains("24시간"))
    }
}
