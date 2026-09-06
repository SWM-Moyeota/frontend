package com.moyeota.presentation.core

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D-7 회귀 방어: 커서 좌표 변환(OffsetMapping)의 경계값 검증.
 *
 * 실기에서 순서가 뒤집혔던 원인은 "구분자가 새로 끼는 순간"의 offset 처리였으므로,
 * 그 경계(휴대폰 3·7, 생년월일 4·6)와 길이 끝을 집중적으로 본다.
 */
class DigitGroupVisualTransformationTest {

    private val phone = PhoneNumberTransformation
    private val birth = BirthDateTransformation
    private val card = CardNumberTransformation
    private val expiry = CardExpiryTransformation

    @Test
    fun `휴대폰 표시 문자열은 자릿수에 따라 하이픈이 늘어난다`() {
        assertEquals("", phone.format(""))
        assertEquals("010", phone.format("010"))
        // 뒤따르는 숫자가 없으면 꼬리 하이픈을 만들지 않는다
        assertEquals("010-1", phone.format("0101"))
        assertEquals("010-1234", phone.format("0101234"))
        assertEquals("010-1234-5", phone.format("01012345"))
        assertEquals("010-1234-5678", phone.format("01012345678"))
    }

    @Test
    fun `생년월일 표시 문자열은 YYYY-MM-DD 로 끊긴다`() {
        assertEquals("1995", birth.format("1995"))
        assertEquals("1995-0", birth.format("19950"))
        assertEquals("1995-03", birth.format("199503"))
        assertEquals("1995-03-1", birth.format("1995031"))
        assertEquals("1995-03-15", birth.format("19950315"))
    }

    @Test
    fun `휴대폰 - 원시 offset 이 하이픈 개수만큼 밀린다`() {
        val mapping = phone.filter(AnnotatedString("01012345678")).offsetMapping
        // "010-1234-5678"
        assertEquals(0, mapping.originalToTransformed(0))
        assertEquals(2, mapping.originalToTransformed(2))
        // 경계 3: 커서는 하이픈 뒤 → "010-|1234-5678"
        assertEquals(4, mapping.originalToTransformed(3))
        assertEquals(5, mapping.originalToTransformed(4))
        // 경계 7: 하이픈 2 개를 지났다 → "010-1234-|5678"
        assertEquals(9, mapping.originalToTransformed(7))
        // 문자열 끝
        assertEquals(13, mapping.originalToTransformed(11))
    }

    @Test
    fun `휴대폰 - 표시 offset 이 원시 offset 으로 되돌아온다`() {
        val mapping = phone.filter(AnnotatedString("01012345678")).offsetMapping
        assertEquals(0, mapping.transformedToOriginal(0))
        // 하이픈 앞뒤(3·4)는 같은 원시 3 으로 접힌다
        assertEquals(3, mapping.transformedToOriginal(3))
        assertEquals(3, mapping.transformedToOriginal(4))
        assertEquals(4, mapping.transformedToOriginal(5))
        assertEquals(7, mapping.transformedToOriginal(8))
        assertEquals(7, mapping.transformedToOriginal(9))
        assertEquals(11, mapping.transformedToOriginal(13))
    }

    @Test
    fun `생년월일 - 경계 4 와 6 에서 커서가 하이픈 뒤에 선다`() {
        val mapping = birth.filter(AnnotatedString("19950315")).offsetMapping
        // "1995-03-15"
        assertEquals(0, mapping.originalToTransformed(0))
        assertEquals(5, mapping.originalToTransformed(4))
        assertEquals(8, mapping.originalToTransformed(6))
        assertEquals(10, mapping.originalToTransformed(8))
        assertEquals(4, mapping.transformedToOriginal(4))
        assertEquals(4, mapping.transformedToOriginal(5))
        assertEquals(6, mapping.transformedToOriginal(8))
        assertEquals(8, mapping.transformedToOriginal(10))
    }

    @Test
    fun `타이핑 도중 모든 길이에서 왕복 변환과 표시 길이가 일치한다`() {
        // 19950315 를 한 글자씩 치는 과정 = D-7 재현 시나리오
        val full = "19950315"
        for (length in 0..full.length) {
            val digits = full.take(length)
            val transformed = birth.filter(AnnotatedString(digits))
            val displayed = transformed.text.text
            assertEquals(birth.format(digits), displayed)

            var previous = -1
            for (offset in 0..digits.length) {
                val mapped = transformed.offsetMapping.originalToTransformed(offset)
                // 경계 안 + 단조 증가 — 둘 중 하나만 깨져도 Compose 가 예외를 던지거나 커서가 튄다
                assertTrue("offset $offset out of bounds in '$displayed'", mapped in 0..displayed.length)
                assertTrue("offset $offset not monotonic in '$displayed'", mapped > previous)
                previous = mapped
                // 왕복 보존: 원시 → 표시 → 원시
                assertEquals(offset, transformed.offsetMapping.transformedToOriginal(mapped))
            }
            // 표시 → 원시도 경계 안에 머문다
            for (offset in 0..displayed.length) {
                assertTrue(transformed.offsetMapping.transformedToOriginal(offset) in 0..digits.length)
            }
        }
    }

    @Test
    fun `휴대폰도 모든 길이에서 왕복 변환이 보존된다`() {
        val full = "01012345678"
        for (length in 0..full.length) {
            val digits = full.take(length)
            val transformed = phone.filter(AnnotatedString(digits))
            val displayed = transformed.text.text
            for (offset in 0..digits.length) {
                val mapped = transformed.offsetMapping.originalToTransformed(offset)
                assertTrue(mapped in 0..displayed.length)
                assertEquals(offset, transformed.offsetMapping.transformedToOriginal(mapped))
            }
        }
    }

    @Test
    fun `카드번호 표시는 종전 chunked(4) 표기와 같다`() {
        // 옛 구현 `digits.chunked(4).joinToString("-")` 와 전 길이에서 동일해야 표시 규칙이 안 바뀐다
        val full = "3400000000000009" // 16 자리
        for (length in 0..full.length) {
            val digits = full.take(length)
            assertEquals(digits.chunked(4).joinToString("-"), card.format(digits))
        }
        // AMEX 15 자리도 4-4-4-3
        assertEquals("3400-0000-0000-000", card.format("340000000000000"))
    }

    @Test
    fun `카드번호 - 4 자리 경계마다 커서가 하이픈 뒤에 선다`() {
        val mapping = card.filter(AnnotatedString("4000123412341234")).offsetMapping
        // "4000-1234-1234-1234"
        assertEquals(5, mapping.originalToTransformed(4))
        assertEquals(10, mapping.originalToTransformed(8))
        assertEquals(15, mapping.originalToTransformed(12))
        assertEquals(19, mapping.originalToTransformed(16))
        assertEquals(4, mapping.transformedToOriginal(4))
        assertEquals(4, mapping.transformedToOriginal(5))
        assertEquals(16, mapping.transformedToOriginal(19))
    }

    @Test
    fun `유효기간은 하이픈이 아닌 슬래시로 끊긴다`() {
        assertEquals("12", expiry.format("12"))
        assertEquals("12/2", expiry.format("122"))
        assertEquals("12/28", expiry.format("1228"))
        val mapping = expiry.filter(AnnotatedString("1228")).offsetMapping
        assertEquals(3, mapping.originalToTransformed(2))
        assertEquals(5, mapping.originalToTransformed(4))
        assertEquals(2, mapping.transformedToOriginal(3))
        assertEquals(4, mapping.transformedToOriginal(5))
    }

    @Test
    fun `카드 필드도 모든 길이에서 왕복 변환이 보존된다`() {
        // 15(AMEX)·16 자리, 유효기간 4 자리 — 타이핑 도중 전 구간
        val cases = listOf(
            card to "4000123412341234",
            card to "340000000000000",
            expiry to "1228",
        )
        for ((transformation, full) in cases) {
            for (length in 0..full.length) {
                val digits = full.take(length)
                val transformed = transformation.filter(AnnotatedString(digits))
                val displayed = transformed.text.text
                var previous = -1
                for (offset in 0..digits.length) {
                    val mapped = transformed.offsetMapping.originalToTransformed(offset)
                    assertTrue("offset $offset out of bounds in '$displayed'", mapped in 0..displayed.length)
                    assertTrue("offset $offset not monotonic in '$displayed'", mapped > previous)
                    previous = mapped
                    assertEquals(offset, transformed.offsetMapping.transformedToOriginal(mapped))
                }
                for (offset in 0..displayed.length) {
                    assertTrue(transformed.offsetMapping.transformedToOriginal(offset) in 0..digits.length)
                }
            }
        }
    }

    @Test
    fun `범위를 벗어난 offset 은 크래시 대신 경계로 잘린다`() {
        val transformed = birth.filter(AnnotatedString("1995"))
        assertEquals(4, transformed.offsetMapping.originalToTransformed(99))
        assertEquals(0, transformed.offsetMapping.originalToTransformed(-3))
        assertEquals(4, transformed.offsetMapping.transformedToOriginal(99))
        assertEquals(0, transformed.offsetMapping.transformedToOriginal(-3))
    }
}
