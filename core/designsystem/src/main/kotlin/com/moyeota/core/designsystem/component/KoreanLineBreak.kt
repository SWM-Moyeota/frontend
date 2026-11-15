package com.moyeota.core.designsystem.component

import android.os.Build
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.LineBreak

/**
 * 한국어 글을 **어절 단위로** 줄바꿈하는 도구 — 「…기록돼 / 요」·「없 / 어요」처럼 어절 한가운데서 줄이
 * 바뀌는 것을 막는다(16 도착지 확인의 예약 안내, 공용 확인 다이얼로그 본문에서 실제로 났다, 오너 UX 피드백 2026-11).
 *
 * ### 왜 그냥 두면 어절 한가운데서 끊기는가
 * 안드로이드 줄바꿈(ICU, UAX #14)은 한글 음절을 **한자처럼** 다룬다 — 음절과 음절 사이 어디서나
 * 끊을 수 있다. 그래서 줄 끝에 한 글자만 모자라면 어절 한가운데서 끊고, 마지막 「요」가 다음 줄로
 * 넘어간다. 웹의 `word-break: keep-all` 이 하는 일(띄어쓰기에서만 끊기)을 안드로이드에서 얻는 길이
 * 아래 두 갈래다.
 *
 * | API | 방법 |
 * |---|---|
 * | 33 이상 | **구 단위(WordBreak.Phrase)** 줄바꿈. 구 단위는 **텍스트 로케일이 한국어일 때만** 켜지므로 [KoreanLocale] 을 함께 건다(영어 로케일 기기에서도 동작해야 한다) |
 * | 32 이하 | 프레임워크에 구 단위 줄바꿈이 없다 → 글자 자체를 바꾼다([keepKoreanWordsTogether]): 어절 안 글자 사이에 WORD JOINER(U+2060), 마지막 두 어절 사이에 NBSP(U+00A0) |
 *
 * **보이는 글자는 같다.** U+2060 은 폭이 없는 「여기서 끊지 마라」 표시이고 NBSP 는 보통 띄어쓰기와
 * 폭이 같다. 스크린리더에는 원문을 그대로 싣는다([KoreanBodyText]) — 바꾼 문자열이 읽히지 않게.
 *
 * iOS 는 SwiftUI `Text` 의 기본 줄바꿈 전략에 한글 단어 우선(`hangulWordPriority`)이 들어 있어 따로 처리하지 않는다.
 */

/** U+2060 WORD JOINER — 폭 0, 「이 자리에서 줄을 바꾸지 마라」(UAX #14 LB11). */
const val WORD_JOINER: Char = '\u2060'

/** U+00A0 NO-BREAK SPACE — 보통 띄어쓰기와 같은 폭이지만 그 자리에서 끊지 않는다(UAX #14 GL). */
const val NO_BREAK_SPACE: Char = '\u00A0'

/** 구 단위 줄바꿈을 켜는 텍스트 로케일. 기기 로케일이 영어여도 이 글은 한국어라는 사실을 줄바꿈기에 알린다. */
val KoreanLocale: LocaleList = LocaleList("ko-KR")

/** 구 단위 줄바꿈(`LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE`)이 생긴 API — 33(Android 13, TIRAMISU). */
const val PHRASE_LINE_BREAK_MIN_SDK = 33

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
fun keepKoreanWordsTogether(text: String): String {
    if (text.length < 2) return text
    val lastSpace = text.indexOfLast { it == ' ' }
    val out = StringBuilder(text.length * 2)
    text.forEachIndexed { index, char ->
        if (index > 0 && joinable(text[index - 1], char)) out.append(WORD_JOINER)
        out.append(if (index == lastSpace) NO_BREAK_SPACE else char)
    }
    return out.toString()
}

/**
 * 두 UTF-16 글자 사이에 [WORD_JOINER] 를 넣어도 되는가 — 띄어쓰기 옆이 아니고, **한 글자의 조각 사이도 아니다**.
 * 이모지(서로게이트 짝)·이음 표시(ZWJ)·이체 선택자(U+FE0F 등)·결합 부호 앞뒤에 넣으면 글자가 깨진다(리뷰).
 */
private fun joinable(previous: Char, char: Char): Boolean {
    if (previous.isWhitespace() || char.isWhitespace()) return false
    if (previous.isHighSurrogate() && char.isLowSurrogate()) return false
    if (previous == ZERO_WIDTH_JOINER || char == ZERO_WIDTH_JOINER) return false
    if (char in VARIATION_SELECTORS) return false
    return Character.getType(char).toByte() !in COMBINING_MARK_TYPES
}

private const val ZERO_WIDTH_JOINER: Char = '\u200D'
private val VARIATION_SELECTORS: CharRange = '\uFE00'..'\uFE0F'
private val COMBINING_MARK_TYPES: Set<Byte> = setOf(
    Character.NON_SPACING_MARK,
    Character.ENCLOSING_MARK,
    Character.COMBINING_SPACING_MARK,
)

/**
 * 이 기기에서 실제로 그릴 문자열 — API 33 이상은 원문 그대로(프레임워크가 구 단위로 끊는다),
 * 그 아래는 [keepKoreanWordsTogether] 를 적용한 값.
 *
 * API 레벨을 **인자로** 받는다 — 순수 함수로 두어야 두 갈래를 JVM 테스트에서 함께 고정할 수 있다.
 */
fun koreanParagraphDisplayText(text: String, sdkInt: Int): String =
    if (sdkInt >= PHRASE_LINE_BREAK_MIN_SDK) text else keepKoreanWordsTogether(text)

/** 본문용 어절 줄바꿈 — 문단 품질(HighQuality) 배치 + 구 단위. 안내문(가운데 정렬)은 균형 배치인 [LineBreak.Heading] 을 쓴다 */
val KoreanBodyLineBreak: LineBreak = LineBreak(
    strategy = LineBreak.Strategy.HighQuality,
    strictness = LineBreak.Strictness.Strict,
    wordBreak = LineBreak.WordBreak.Phrase,
)

/**
 * 어절 단위로 줄바꿈하는 한국어 **본문**(시작 정렬). 확인 다이얼로그 본문처럼 왼쪽에 붙는 여러 줄 글에 쓴다.
 *
 * [style] 의 크기·굵기·정렬은 호출부 것을 그대로 쓰고, 줄바꿈·로케일만 덮어쓴다.
 */
@Composable
fun KoreanBodyText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val display = remember(text) { koreanParagraphDisplayText(text, Build.VERSION.SDK_INT) }
    Text(
        text = display,
        style = style.copy(lineBreak = KoreanBodyLineBreak, localeList = KoreanLocale),
        color = color,
        // 폴백 문자열(U+2060·U+00A0 포함)이 아니라 **원문**을 읽게 한다
        modifier = if (display == text) modifier else modifier.clearAndSetSemantics { contentDescription = text },
    )
}
