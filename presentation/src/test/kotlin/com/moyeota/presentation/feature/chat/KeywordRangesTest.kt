package com.moyeota.presentation.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Test

/** 검색 결과 본문에서 어느 구간을 강조하는가. 서버 LIKE 와 같은 규칙(부분 일치, 대소문자 무시)이어야 한다 */
class KeywordRangesTest {

    @Test
    fun `나오는 자리 전부를 찾는다`() {
        assertEquals(listOf(0..1, 3..4), keywordRanges("역에 역에", "역에"))
    }

    @Test
    fun `대소문자를 가리지 않는다`() {
        assertEquals(listOf(0..3), keywordRanges("Taxi 탑니다", "taxi"))
    }

    @Test
    fun `겹치는 후보는 앞의 것만 잡는다`() {
        assertEquals(listOf(0..1), keywordRanges("aaa", "aa"))
    }

    @Test
    fun `없으면 빈 목록`() {
        assertEquals(emptyList<IntRange>(), keywordRanges("부산역", "서면"))
        assertEquals(emptyList<IntRange>(), keywordRanges("부산역", ""))
    }
}
