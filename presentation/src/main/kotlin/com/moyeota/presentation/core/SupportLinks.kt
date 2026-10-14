package com.moyeota.presentation.core

/**
 * 앱이 여는 운영 주소 한 곳 — 이용약관 · 개인정보 처리방침 · 문의 메일 · 계정 삭제 안내.
 *
 * **2026-10-14 에 네 값이 모두 채워졌다.** `legal.moyeota.p-e.kr` 에 정적 페이지를 올렸고
 * 로그인 없이 열린다(소스는 저장소에 보관돼 있다 — `_workspace/legal-site/`).
 * 주소가 바뀌면 **이 파일 한 곳만 고친다** — 화면은 값의 내용이 아니라 유무만 본다.
 *
 * 쓰는 곳:
 * - 35 마이페이지 「안내」 카드 — [PRIVACY_URL] · [TERMS_URL] · [CONTACT_EMAIL]
 * - 37 회원 탈퇴 「자세히 보기」 — [ACCOUNT_DELETION_URL]
 * - 04 로그인의 「가입하면 … 동의하게 돼요」 — [TERMS_URL] · [PRIVACY_URL]
 *
 * ### null 로 비우면 그 행이 사라진다 (그대로 남겨 둔 동작)
 *
 * 값을 null 로 되돌리면 해당 메뉴 행을 **아예 그리지 않고**, 그룹의 행이 전부 사라지면 머리말과
 * 카드도 그리지 않는다(`myPageGroups`). 주소가 생긴 뒤에도 이 길을 남겨 두는 이유는 두 가지다.
 * ① 페이지가 내려가거나 도메인이 만료되면 **행을 숨기는 것이 죽은 링크를 보여 주는 것보다 낫다**
 *    (스토어 심사도 죽은 링크를 잡는다) — 이 파일 한 줄만 고쳐 긴급 대응할 수 있어야 한다.
 * ② 「준비 중」 같은 자리표시는 눌러도 아무 일이 없어 사용자에게 고장으로 읽힌다.
 *
 * ### 주소를 고칠 때 주의
 * - [PRIVACY_URL] 은 Play 콘솔 「개인정보처리방침」에 등록한 주소와 **같은 페이지**여야 한다.
 * - [ACCOUNT_DELETION_URL] 은 Play 콘솔 데이터 안전 섹션의 계정 삭제 안내 주소다 —
 *   **앱을 설치하지 않은 사람도** 열 수 있어야 한다는 것이 요건이다.
 * - 넷 다 앱 밖(브라우저 · 메일 앱)에서 열린다 — 로그인 없이 접근 가능한 공개 주소를 쓴다.
 */
object SupportLinks {

    /** 이용약관 페이지. 비우면(null) 관련 행을 숨긴다. */
    val TERMS_URL: String? = "https://legal.moyeota.p-e.kr/terms/"

    /**
     * 개인정보 처리방침 페이지. Play 콘솔 등록 주소와 같아야 한다.
     *
     * 그 페이지에 **「만 14세 미만은 가입할 수 없습니다」가 들어가야 한다** — 앱이 실제로 막고 있다
     * (`AuthPolicy.MIN_AGE`, 10 프로필 만들기의 생년월일 판정). 고지와 동작 중 하나만 바꾸면
     * 둘 중 하나가 거짓이 된다.
     */
    val PRIVACY_URL: String? = "https://legal.moyeota.p-e.kr/privacy/"

    /** 문의를 받을 메일 주소. 메일 앱으로 제목·환경 정보까지 채워 띄운다. */
    val CONTACT_EMAIL: String? = "codingforest17@gmail.com"

    /**
     * 계정 삭제 안내 페이지 — 「어떻게 지우는가 · 무엇이 지워지고 무엇이 남는가 · 언제까지」.
     *
     * 앱 안 탈퇴(37)와 **역할이 다르다.** 37 은 지금 탈퇴하려는 사람에게 네 줄로 요점만 보여 주고,
     * 이 페이지는 그 요점의 전문(보관 기간·남는 데이터·처리 절차)을 담는다. 그래서 35 메뉴에
     * 따로 행을 두지 않고 **37 안에서만** 연다 — 자세히 알고 싶어지는 자리가 거기다.
     */
    val ACCOUNT_DELETION_URL: String? = "https://legal.moyeota.p-e.kr/account-deletion/"
}

/**
 * 화면에 넘기는 주소 묶음. 기본값은 [SupportLinks] 의 운영 값이다.
 *
 * 화면이 [SupportLinks] 를 직접 읽지 않고 **값으로 받는** 이유는 하나다 — 프리뷰와 단위 테스트가
 * 「주소가 없는 상태」를 만들어 볼 수 있어야 한다. 전역 object 를 읽으면 그 상태를 재현하려면
 * 소스를 고쳐 다시 빌드해야 하고(실제로 그렇게 QA 를 돌린 기록이 있다 — `_workspace/113…` §B8),
 * 행이 사라지는 동작이 테스트로 고정되지 않는다.
 */
data class SupportLinkTargets(
    val privacyUrl: String? = SupportLinks.PRIVACY_URL,
    val termsUrl: String? = SupportLinks.TERMS_URL,
    val contactEmail: String? = SupportLinks.CONTACT_EMAIL,
) {
    companion object {
        /** 주소가 하나도 없는 상태 — 프리뷰·테스트용. 운영 값을 전부 비웠을 때와 같은 모습이다. */
        val None = SupportLinkTargets(privacyUrl = null, termsUrl = null, contactEmail = null)
    }
}
