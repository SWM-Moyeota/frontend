package com.moyeota.presentation.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

/**
 * 말풍선 시각 표기. 서버 createdAt 은 UTC 라, 문자열을 그대로 자르면 한국에서 9시간 과거로 보인다
 * (QA 결함-3: KST 17:36 → 08:36).
 */
class ChatTimeLabelTest {

    private val seoul = ZoneId.of("Asia/Seoul")

    @Test
    fun `UTC Z 표기를 기기 시간대로 옮긴다`() {
        assertEquals("17:36", "2026-09-07T08:36:12Z".toTimeLabel(seoul))
    }

    @Test
    fun `밀리초가 붙어도 분까지만 쓴다`() {
        assertEquals("00:05", "2026-09-07T15:05:59.123456Z".toTimeLabel(seoul))
    }

    // 목록 시각 — 오늘은 시각, 올해는 월일, 그 전은 연월일 (기준 시각을 넣어 결정적으로 검사한다)
    private val now = java.time.Instant.parse("2026-09-11T05:00:00Z") // KST 2026-09-11 14:00

    @Test
    fun `목록 시각은 오늘이면 HH mm 이다`() {
        assertEquals("11:52", "2026-09-11T02:52:55.123Z".toListTimeLabel(now, seoul))
    }

    @Test
    fun `목록 시각은 올해 다른 날이면 월일이다`() {
        assertEquals("9월 10일", "2026-09-10T14:59:00Z".toListTimeLabel(now, seoul)) // KST 9/10 23:59
    }

    @Test
    fun `목록 시각은 작년이면 연월일이다`() {
        assertEquals("2025.12.31", "2025-12-31T03:00:00Z".toListTimeLabel(now, seoul))
    }

    @Test
    fun `목록 시각도 날짜 경계는 기기 시간대로 판단한다`() {
        // UTC 로는 9/10 이지만 KST 로는 9/11 00:10 → 오늘
        assertEquals("00:10", "2026-09-10T15:10:00Z".toListTimeLabel(now, seoul))
    }

    @Test
    fun `목록 시각은 파싱이 안 되면 비운다`() {
        assertNull("어제".toListTimeLabel(now, seoul))
    }

    @Test
    fun `오프셋 표기도 같은 순간으로 읽는다`() {
        assertEquals("17:36", "2026-09-07T17:36:12+09:00".toTimeLabel(seoul))
    }

    @Test
    fun `시간대 정보가 없으면 문자열을 그대로 잘라 쓴다`() {
        assertEquals("17:36", "2026-09-07T17:36:12".toTimeLabel(seoul))
    }

    @Test
    fun `형식을 알 수 없으면 null 이다`() {
        assertNull("어제".toTimeLabel(seoul))
    }
}
