package com.moyeota.presentation.core

import android.os.Build
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.moyeota.core.designsystem.component.KoreanLocale
import com.moyeota.core.designsystem.component.koreanParagraphDisplayText

/**
 * 가운데 정렬 + 어절 단위 줄바꿈으로 그리는 한국어 안내문. 글자 자체는 바꾸지 않는다.
 *
 * 어절 줄바꿈의 원리(API 33 이상은 구 단위 줄바꿈, 그 아래는 U+2060·U+00A0 폴백)는 디자인시스템의
 * `KoreanLineBreak.kt` 에 있다 — 공용 확인 다이얼로그 본문(`KoreanBodyText`)도 같은 규칙을 쓴다.
 *
 * [style] 의 색·크기·굵기는 호출부 것을 그대로 쓰고, 정렬·줄바꿈·로케일만 덮어쓴다.
 * 정렬을 가운데로 고정하는 이유: 두 줄짜리 안내가 버튼 위에 놓일 때 왼쪽 정렬이면 둘째 줄이 짧아
 * 비뚤어 보이고, 가운데면 균형 배치([LineBreak.Heading] — 균형 배치 + 구 단위)와 함께 두 줄이 한 덩어리로 읽힌다.
 */
@Composable
internal fun KoreanParagraphText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    val display = remember(text) { koreanParagraphDisplayText(text, Build.VERSION.SDK_INT) }
    Text(
        text = display,
        style = style.copy(
            textAlign = TextAlign.Center,
            lineBreak = LineBreak.Heading,
            localeList = KoreanLocale,
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        // 폴백 문자열(U+2060·U+00A0 포함)이 아니라 **원문**을 읽게 한다
        modifier = if (display == text) modifier else modifier.clearAndSetSemantics { contentDescription = text },
    )
}
