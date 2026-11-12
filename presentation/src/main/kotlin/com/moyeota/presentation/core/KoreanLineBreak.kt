package com.moyeota.presentation.core

import android.os.Build
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

/**
 * 한국어 안내문을 **어절 단위로** 줄바꿈하는 도구 — 「…기록돼 / 요」처럼 마지막 음절 하나가 혼자
 * 줄을 차지하는 것을 막는다(16 도착지 확인의 예약 안내에서 실제로 났다, 오너 UX 피드백 2026-11).
 *
 * ### 왜 그냥 두면 「요」가 혼자 떨어지는가
 * 안드로이드 줄바꿈(ICU, UAX #14)은 한글 음절을 **한자처럼** 다룬다 — 음절과 음절 사이 어디서나
 * 끊을 수 있다. 그래서 줄 끝에 한 글자만 모자라면 어절 한가운데서 끊고, 마지막 「요」가 다음 줄로
 * 넘어간다. 웹의 `word-break: keep-all` 이 하는 일(띄어쓰기에서만 끊기)을 안드로이드에서 얻는 길이
 * 아래 두 갈래다.
 *
 * | API | 방법 |
 * |---|---|
 * | 33 이상 | [LineBreak.Heading] — 균형 배치(Balanced) + **구 단위(WordBreak.Phrase)** 줄바꿈. 구 단위는 **텍스트 로케일이 한국어일 때만** 켜지므로 [KoreanLocale] 을 함께 건다(영어 로케일 기기에서도 동작해야 한다) |
 * | 32 이하 | 프레임워크에 구 단위 줄바꿈이 없다 → 글자 자체를 바꾼다([keepKoreanWordsTogether]): 어절 안 글자 사이에 WORD JOINER(U+2060), 마지막 두 어절 사이에 NBSP(U+00A0) |
 *
 * 균형 배치(`BREAK_STRATEGY_BALANCED`)는 API 23 부터 있어 **양쪽 모두** 걸린다 — 두 줄의 길이를
 * 비슷하게 맞춰 짧은 꼬리 줄 자체가 덜 생긴다.
 *
 * **보이는 글자는 같다.** U+2060 은 폭이 없는 「여기서 끊지 마라」 표시이고 NBSP 는 보통 띄어쓰기와
 * 폭이 같다. 스크린리더에는 원문을 그대로 싣는다([KoreanParagraphText]) — 바꾼 문자열이 읽히지 않게.
 *
 * iOS 포팅: SwiftUI `Text` 는 기본 줄바꿈 전략에 한글 단어 우선(`hangulWordPriority`)이 들어 있어
 * 별도 처리가 필요 없을 수 있다 — 실기에서 꼬리 음절이 남는지 확인하고, 남으면 같은 [keepKoreanWordsTogether]
 * 규칙(U+2060·U+00A0)을 문자열에 적용한다.
 */

/** U+2060 WORD JOINER — 폭 0, 「이 자리에서 줄을 바꾸지 마라」(UAX #14 LB11). */
internal const val WORD_JOINER: Char = '⁠'

/** U+00A0 NO-BREAK SPACE — 보통 띄어쓰기와 같은 폭이지만 그 자리에서 끊지 않는다(UAX #14 GL). */
internal const val NO_BREAK_SPACE: Char = ' '

/** 구 단위 줄바꿈을 켜는 텍스트 로케일. 기기 로케일이 영어여도 이 글은 한국어라는 사실을 줄바꿈기에 알린다. */
internal val KoreanLocale: LocaleList = LocaleList("ko-KR")

/**
 * API 32 이하용 **keep-all 흉내** — 띄어쓰기에서만 줄을 바꿀 수 있게, 그리고 마지막 두 어절은 함께
 * 다니게 문자열을 바꾼다.
 *
 * 1. **어절 안**의 이웃한 두 글자 사이에 [WORD_JOINER] 를 넣는다 → 한글 음절 사이 줄바꿈 기회가 사라진다
 * 2. **마지막 띄어쓰기**를 [NO_BREAK_SPACE] 로 바꾼다 → 마지막 줄에 어절 하나만 남는 일(고아 어절)이 없다
 *
 * 띄어쓰기(공백 문자)가 하나도 없으면 1 만 적용된다. 빈 문자열·한 글자는 그대로 돌려준다.
 * 이미 들어 있는 줄바꿈 문자(`\n`)는 공백으로 보고 건드리지 않는다 — 그 자리는 원래 끊기는 자리다.
 *
 * 어절이 한 줄보다 길면 줄바꿈기가 어쩔 수 없이 그 안에서 끊는다(넘쳐서 잘리지는 않는다).
 */
internal fun keepKoreanWordsTogether(text: String): String {
    if (text.length < 2) return text
    val lastSpace = text.indexOfLast { it == ' ' }
    val out = StringBuilder(text.length * 2)
    text.forEachIndexed { index, char ->
        if (index > 0) {
            val previous = text[index - 1]
            if (!previous.isWhitespace() && !char.isWhitespace()) out.append(WORD_JOINER)
        }
        out.append(if (index == lastSpace) NO_BREAK_SPACE else char)
    }
    return out.toString()
}

/**
 * 이 기기에서 실제로 그릴 문자열 — API 33 이상은 원문 그대로(프레임워크가 구 단위로 끊는다),
 * 그 아래는 [keepKoreanWordsTogether] 를 적용한 값.
 *
 * API 레벨을 **인자로** 받는다 — 순수 함수로 두어야 두 갈래를 JVM 테스트에서 함께 고정할 수 있다.
 */
internal fun koreanParagraphDisplayText(text: String, sdkInt: Int): String =
    if (sdkInt >= PHRASE_LINE_BREAK_MIN_SDK) text else keepKoreanWordsTogether(text)

/** 구 단위 줄바꿈(`LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE`)이 생긴 API — 33(Android 13, TIRAMISU). */
internal const val PHRASE_LINE_BREAK_MIN_SDK = 33

/**
 * 가운데 정렬 + 어절 단위 줄바꿈으로 그리는 한국어 안내문. 글자 자체는 바꾸지 않는다(위 표).
 *
 * [style] 의 색·크기·굵기는 호출부 것을 그대로 쓰고, 정렬·줄바꿈·로케일만 덮어쓴다.
 * 정렬을 가운데로 고정하는 이유: 두 줄짜리 안내가 버튼 위에 놓일 때 왼쪽 정렬이면 둘째 줄이 짧아
 * 비뚤어 보이고, 가운데면 균형 배치와 함께 두 줄이 한 덩어리로 읽힌다.
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
