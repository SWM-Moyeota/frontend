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
