package com.moyeota.core.designsystem.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * 모서리 반경 — **이 넷 밖의 값을 쓰지 않는다**(디자인 QA G3, 2026-11).
 *
 * 예전엔 카드 반경이 12·14·16·18dp, 시트 윗모서리가 20·24dp 로 화면마다 달랐다. 하나하나는 그럴듯해도
 * 나란히 놓이면 "화면마다 따로 만든" 티가 난다. 디자인 문서의 rounded 표(sm 6 · md 12 · lg 20 · full)로 모은다.
 *
 * iOS 는 같은 이름의 `RoundedRectangle(cornerRadius:)` 값으로 옮긴다.
 */
object MoyeotaShape {
    /** 6dp — 작은 선택 상자·진행 막대 */
    val Small = RoundedCornerShape(6.dp)

    /** 12dp — 카드·목록 묶음·입력칸·안내 배너. 기본값이다 */
    val Card = RoundedCornerShape(12.dp)

    /** 윗모서리만 20dp — 화면 아래에서 올라오는 시트 */
    val Sheet = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

    /** 20dp — 확인 다이얼로그 */
    val Dialog = RoundedCornerShape(20.dp)

    /** 완전한 알약 — 모든 CTA·칩·상태 배지 */
    val Pill = CircleShape
}
