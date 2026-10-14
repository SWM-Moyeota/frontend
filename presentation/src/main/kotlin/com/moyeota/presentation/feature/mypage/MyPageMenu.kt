package com.moyeota.presentation.feature.mypage

import com.moyeota.presentation.core.SupportLinkTargets

/**
 * 35 마이페이지 메뉴의 **구성 규칙** — 어떤 행이 어느 그룹에 생기는가.
 *
 * 화면(`MyPageScreen`)에서 떼어 둔 이유: 「주소가 없으면 행이 사라지고, 행이 다 사라지면 그룹도
 * 사라진다」는 규칙은 눈으로 확인하려면 **값을 바꿔 다시 빌드해야** 하는 종류다(실제로 QA 가
 * 그렇게 돌렸다 — `_workspace/113_qa-verifier_report.md` §B8). 순수 함수로 두면 테스트가 고정한다.
 */

/** 그룹 머리말. 테스트와 화면이 같은 값을 보게 한 곳에 둔다. */
internal object MyPageGroupLabel {
    const val SETTINGS = "설정"
    const val INFO = "안내"
    const val ACCOUNT = "계정"
}

/**
 * 「계정」 카드 아래 한 줄. **「탈퇴하면」으로 시작하는 것이 중요하다** — 로그아웃과 탈퇴가 한 카드에
 * 들어오면서 주어가 없는 문장("계정과 저장한 정보가 사라지고…")은 바로 위의 로그아웃에까지
 * 걸리는 말로 읽힌다. 로그아웃은 아무것도 지우지 않는다.
 */
internal const val ACCOUNT_CARD_CAPTION = "탈퇴하면 계정과 저장한 정보가 사라지고 되돌릴 수 없어요"

/**
 * 행을 눌렀을 때 일어나는 일. **주소를 행이 들고 있다**(`OpenWeb`·`SendMail`).
 *
 * 화면이 다시 null 을 확인할 필요가 없어지고, 「개인정보 처리방침 행이 PRIVACY_URL 로 나가는가」를
 * 단위 테스트가 값으로 검사할 수 있다. 외부 이동 수단(Intent)은 화면이 `core/ExternalIntents` 로
 * 옮긴다 — 이 파일은 안드로이드를 모른다(그래서 JVM 테스트가 그냥 돈다).
 */
internal sealed interface MyPageAction {

    /** OS 앱 알림 설정 (앱 밖). 앱 안 토글을 두지 않는 이유는 `openAppNotificationSettings` 참고. */
    data object NotificationSettings : MyPageAction

    /** 브라우저로 공개 페이지 열기 (앱 밖). */
    data class OpenWeb(val url: String) : MyPageAction

    /** 메일 앱으로 문의 열기 (앱 밖). */
    data class SendMail(val address: String) : MyPageAction

    /** 확인 다이얼로그 → 세션 정리 → 04a 로그인 (앱 안). */
    data object Logout : MyPageAction

    /** 37 회원 탈퇴 (앱 안). */
    data object DeleteAccount : MyPageAction
}

/**
 * 행 오른쪽 기호. 이 화면의 규칙은 **↗ = 앱 밖으로 나간다, › = 앱 안에서 다음 단계로 간다** 다.
 *
 * 아이콘 세트가 없는 앱이라 행마다 뜻이 다른 그림을 붙일 수 없다. 대신 이 두 기호에만 뜻을 주고
 * 나머지는 글자로 말한다 — 레퍼런스 앱처럼 행마다 아이콘을 두려면 아이콘 자산이 먼저 있어야 한다.
 */
internal enum class MyPageRowMark { EXTERNAL, CHEVRON }

internal data class MyPageRow(
    val text: String,
    val action: MyPageAction,
    val mark: MyPageRowMark,
    /** 되돌릴 수 없는 행 — 빨간 글자. 한 카드 안에서 색이 다른 유일한 행이다. */
    val danger: Boolean = false,
)

internal data class MyPageGroup(
    val label: String,
    val rows: List<MyPageRow>,
    /** 카드 **아래** 한 줄 부연. null 이면 그리지 않는다. */
    val caption: String? = null,
)

/**
 * 35 의 메뉴 그룹 목록. 순서가 화면 순서다 — 설정 → 안내 → 계정.
 *
 * ### 왜 이 순서인가
 * 자주 쓰는 것이 위, 되돌릴 수 없는 것이 맨 아래다. 「안내」(약관·개인정보·문의)는 평소에 보지
 * 않지만 **찾을 때는 반드시 찾아야 하는** 묶음이라 계정보다 위에 둔다. 그룹 안에서도 같은 기준이다:
 * 개인정보 처리방침이 가장 많이 찾는 문서고(스토어 심사도 이 경로를 본다), 계정 카드는
 * 로그아웃 → 탈퇴 순으로 파괴적인 쪽이 아래다.
 *
 * ### 빈 그룹을 거르는 규칙은 하나다
 * 마지막 `filter` 한 줄이 **모든 그룹**에 적용된다. 「안내」만 특별히 처리하지 않는 이유는,
 * 나중에 다른 그룹이 조건부 행만 갖게 돼도 빈 카드가 생기지 않게 하기 위해서다.
 * (지금은 「설정」·「계정」의 행이 조건 없이 생기므로 「안내」만 사라질 수 있다.)
 */
internal fun myPageGroups(links: SupportLinkTargets): List<MyPageGroup> = listOf(
    MyPageGroup(
        label = MyPageGroupLabel.SETTINGS,
        rows = listOf(
            MyPageRow("알림 설정", MyPageAction.NotificationSettings, MyPageRowMark.EXTERNAL),
        ),
    ),
    MyPageGroup(
        label = MyPageGroupLabel.INFO,
        rows = buildList {
            links.privacyUrl?.let {
                add(MyPageRow("개인정보 처리방침", MyPageAction.OpenWeb(it), MyPageRowMark.EXTERNAL))
            }
            links.termsUrl?.let {
                add(MyPageRow("이용약관", MyPageAction.OpenWeb(it), MyPageRowMark.EXTERNAL))
            }
            links.contactEmail?.let {
                add(MyPageRow("문의하기", MyPageAction.SendMail(it), MyPageRowMark.EXTERNAL))
            }
        },
    ),
    MyPageGroup(
        label = MyPageGroupLabel.ACCOUNT,
        rows = listOf(
            // 로그아웃이 탈퇴보다 훨씬 흔한 동작인데 예전엔 13sp 회색 글자 한 줄이었다 — 더 흔한
            // 기능이 더 작아 위계가 뒤집혀 있었다. 이제 탈퇴와 같은 56dp 카드 행이다.
            // 오탭 보호는 색(탈퇴만 빨강)과 확인 단계가 맡는다: 로그아웃은 확인 다이얼로그,
            // 탈퇴는 37 에서 비밀번호 + 또 한 번의 다이얼로그다.
            MyPageRow("로그아웃", MyPageAction.Logout, MyPageRowMark.CHEVRON),
            MyPageRow("탈퇴하기", MyPageAction.DeleteAccount, MyPageRowMark.CHEVRON, danger = true),
        ),
        caption = ACCOUNT_CARD_CAPTION,
    ),
).filter { it.rows.isNotEmpty() }
