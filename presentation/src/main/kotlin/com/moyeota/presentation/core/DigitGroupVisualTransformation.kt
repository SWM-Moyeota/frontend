package com.moyeota.presentation.core

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * 숫자만 담긴 원시 값을 화면에서만 그룹 구분자로 끊어 보여주는 변환.
 *
 * 왜 필요한가 (D-7):
 * 예전에는 `value = formatPhone(digits)` 처럼 하이픈이 낀 **표시 문자열**을 TextField 의 value 로 넘기고
 * `onValueChange` 에서 숫자만 다시 뽑아냈다. 이러면 한 글자를 칠 때마다 표시 문자열 길이가 1 이 아니라
 * 2 만큼 늘어나는 구간(구분자가 새로 끼는 순간)이 생기는데, `BasicTextField(value: String, ...)` 는
 * 이전 selection 인덱스를 그대로 들고 새 텍스트에 얹는다. 그래서 커서가 구분자 앞쪽으로 밀리고
 * 이후 입력이 그 자리에 꽂혀 `19950315` → `1995-31-50` 처럼 순서가 뒤집혔다.
 *
 * 해결: value 는 항상 원시 숫자만 유지하고(입력 1 글자 = 길이 1 증가라 커서가 어긋날 여지가 없다),
 * 하이픈은 [VisualTransformation] 으로만 그린다. 커서 좌표 변환은 [OffsetMapping] 이 책임진다.
 *
 * @param groupSizes 앞에서부터의 그룹 크기. 휴대폰 `[3, 4, 4]` → `010-1234-5678`,
 *                   생년월일 `[4, 2, 2]` → `1995-03-15`
 * @param separator 그룹 사이에 그리는 문자
 */
internal class DigitGroupVisualTransformation(
    groupSizes: List<Int>,
    private val separator: Char = '-',
) : VisualTransformation {

    /**
     * 구분자가 **앞에** 붙는 원시 인덱스들. `[3, 4, 4]` → `[3, 7]`.
     * 마지막 그룹 뒤에는 경계가 없으므로 그룹 수보다 하나 적다.
     */
    private val boundaries: List<Int> = buildList {
        var acc = 0
        for (index in 0 until groupSizes.size - 1) {
            acc += groupSizes[index]
            add(acc)
        }
    }

    /** 원시 숫자 → 표시 문자열. 뒤따르는 숫자가 없으면 구분자를 그리지 않는다("010-" 같은 꼬리 방지). */
    fun format(digits: String): String = buildString(digits.length + boundaries.size) {
        digits.forEachIndexed { index, char ->
            if (index in boundaries) append(separator)
            append(char)
        }
    }

    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val formatted = format(digits)

        // 실제로 그려진 구분자만 센다. 예: 휴대폰 7 자리면 경계 7 은 아직 구분자를 만들지 않는다.
        // 이 필터를 빠뜨리면 originalToTransformed(길이) 가 표시 길이를 넘겨 Compose 가 예외를 던진다.
        val emitted = boundaries.filter { it < digits.length }
        // k 번째 구분자의 표시 인덱스 = 원시 경계 + 그 앞에 이미 낀 구분자 수(k)
        val separatorOffsets = emitted.mapIndexed { k, boundary -> boundary + k }

        return TransformedText(
            AnnotatedString(formatted),
            object : OffsetMapping {
                // 원시 offset 앞에 놓인 구분자 개수만큼 밀어준다.
                // `<=` 라서 경계에 선 커서는 구분자 "뒤"에 놓인다: "1995-|03" (경계 4 → 표시 5).
                // 재구성 도중 옛 offset 이 들어올 수 있어 coerce 로 방어한다(D-7 재발 시 크래시 방지).
                override fun originalToTransformed(offset: Int): Int {
                    val original = offset.coerceIn(0, digits.length)
                    return original + emitted.count { it <= original }
                }

                // 역변환: 표시 offset 왼쪽에 있는 구분자 수를 뺀다.
                // `<` 라서 구분자 바로 앞/뒤(표시 4·5)가 같은 원시 4 로 접힌다 — 왕복은 항상 보존된다.
                override fun transformedToOriginal(offset: Int): Int {
                    val transformed = offset.coerceIn(0, formatted.length)
                    return transformed - separatorOffsets.count { it < transformed }
                }
            },
        )
    }
}

/** 휴대폰 010-1234-5678 (원시 11 자리) */
internal val PhoneNumberTransformation = DigitGroupVisualTransformation(listOf(3, 4, 4))

/** 생년월일 YYYY-MM-DD (원시 8 자리) */
internal val BirthDateTransformation = DigitGroupVisualTransformation(listOf(4, 2, 2))

/**
 * 카드번호 0000-0000-0000-0000 (원시 15~16 자리).
 * AMEX 15 자리도 종전 `chunked(4)` 표기와 같은 4-4-4-3 으로 그려진다 — 표시 규칙은 바꾸지 않는다.
 */
internal val CardNumberTransformation = DigitGroupVisualTransformation(listOf(4, 4, 4, 4))

/** 카드 유효기간 MM/YY (원시 4 자리). 구분자만 다르고 매핑 규칙은 하이픈 필드와 동일하다. */
internal val CardExpiryTransformation =
    DigitGroupVisualTransformation(listOf(2, 2), separator = '/')
