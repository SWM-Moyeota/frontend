package com.moyeota.core.designsystem.theme

import androidx.compose.ui.graphics.Color

// moyeota-figma-tokens.json 의 core.color 값 그대로
object MoyeotaColor {
    // primary
    val Primary500 = Color(0xFF085AF5)
    val Primary600 = Color(0xFF054BC7)
    val Primary700 = Color(0xFF033D9F)
    val Primary50 = Color(0xFFE6EEFE)

    // status
    val Success500 = Color(0xFF10B981)
    val Success600 = Color(0xFF0F8F66)
    val Success50 = Color(0xFFD1FAE5)
    val Waiting500 = Color(0xFFF59E0B)
    val Waiting600 = Color(0xFFB57407)
    val Waiting50 = Color(0xFFFEF3C7)
    val Danger500 = Color(0xFFE63946)
    val Danger600 = Color(0xFFB32836)
    val Danger50 = Color(0xFFFDECEE)

    // 비상 신고 전용 — 다른 곳 사용 금지
    val Safety500 = Color(0xFFDC2626)
    val Safety600 = Color(0xFFA81D1D)

    // ink / text
    val InkPrimary = Color(0xFF111111)
    val InkDeep = Color(0xFF0A0A0B)
    val TextBody = Color(0xB8111111)      // rgba(17,17,17,0.72)
    /**
     * 보조 글자(메타·시각·설명). 흰 바탕 대비 4.8:1 — **정보를 싣는 회색은 이것 하나다.**
     * 예전에 화면마다 따로 두던 `#8A93A0`(3.1:1)·`#9AA1AC`(2.6:1)는 WCAG 4.5:1 에 못 미쳐 여기로 모았다.
     */
    val TextMute = Color(0xFF6B7280)
    /**
     * 입력 자리표시자와 비활성 글자 **전용**. 흰 바탕 대비 2.6:1 이라 읽혀야 하는 정보에는 쓰지 않는다 —
     * "아직 비어 있음"·"지금은 못 누름"처럼 흐린 것 자체가 뜻인 자리만.
     */
    val TextPlaceholder = Color(0xFF9AA1AC)
    /**
     * 읽지 않아도 되는 **장식 회색** — 시트 손잡이·비활성 페이지 점·비활성 아이콘 선. 흰 바탕 대비 1.47:1 이라
     * 글자에는 쓰지 않는다(글자는 [TextPlaceholder]). [BorderStrong] 과 같은 값(디자인 문서의 ash-light).
     */
    val TextAsh = Color(0xFFD1D5DB)
    val TextOnDark = Color(0xFFFFFFFF)
    val Link = Color(0xFF054BC7)

    // surface
    /** 기본 화면·시트 바탕(흰색). 설정형 묶음 목록(마이페이지·탑승 기록·노쇼 기록)만 [SurfaceSoft] 를 쓴다. */
    val SurfaceCanvas = Color(0xFFFFFFFF)
    /** 흰 화면 안의 옅은 면(검색 입력·상대 말풍선·읽기 전용 칸)과 묶음 목록 화면의 바탕 */
    val SurfaceSoft = Color(0xFFF4F6FA)
    val SurfaceCard = Color(0xFFFFFFFF)
    /**
     * 비활성 버튼 바탕. [SurfaceSoft] 로 칠하던 예전 값은 [SurfaceSoft] 화면 위에서 대비가 1.01:1 이라
     * 버튼 모양이 **사라지고** 회색 글자만 떠 있었다.
     */
    val SurfaceDisabled = Color(0xFFE8EBF0)
    val MapOverlay = Color(0xF0FFFFFF)    // rgba(255,255,255,0.94)
    /** 구분선·진행 막대 바닥 — 면을 나누는 가장 옅은 선 */
    val Hairline = Color(0xFFEDEFF3)
    /** 칩·목록 카드의 테두리. [Hairline] 은 흰 바탕에서 1.15:1 이라 "모양"을 만들지 못한다 */
    val Border = Color(0xFFE2E6EC)
    /** 입력칸·보조 버튼의 테두리, 경로 연결 점선 — 디자인 문서의 ash-light */
    val BorderStrong = Color(0xFFD1D5DB)

    // shadow — 그림자는 지도 위에 뜬 것과 시트 가장자리에만 쓴다(MoyeotaElevation)
    /** 지도 위 알약·버튼의 그림자 색 */
    val ShadowOverlay = Color(0x1A1B2A4A)
    /** 시트 가장자리·다이얼로그의 그림자 색 */
    val ShadowSheet = Color(0x141B2A4A)

    // route / marker
    val RouteShared = Color(0xFF085AF5)
    val RouteUserA = Color(0xFF3B82F6)
    val RouteUserB = Color(0xFF8B5CF6)
    val MarkerOrigin = Color(0xFF085AF5)
    val MarkerDestination = Color(0xFFEF4444)
    val MarkerPickup = Color(0xFF10B981)
    val MarkerDropoff = Color(0xFFF97316)

    val Scrim = Color(0x66000000)         // opacity.scrim 40%
}
