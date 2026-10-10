package com.moyeota.presentation.core

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 35 「문의하기」가 메일 앱에 미리 채워 넣는 본문.
 *
 * 환경 정보가 빠진 문의는 "앱 버전이 무엇인가요"를 되묻는 왕복을 한 번 더 만든다.
 * 그래서 **읽기에 실패한 값도 줄 자체는 남긴다** — 줄이 사라지면 받는 쪽은 사용자가 지운 것인지
 * 앱이 못 읽은 것인지 구분할 수 없다.
 */
class SupportMailBodyTest {

    @Test
    fun `앱 버전 Android 버전 기기 모델을 담는다`() {
        val body = supportMailBody(appVersion = "1.0.0", androidRelease = "14", deviceModel = "Pixel 6")
        assertTrue(body.contains("앱 버전: 1.0.0"))
        assertTrue(body.contains("Android: 14"))
        assertTrue(body.contains("기기: Pixel 6"))
    }

    @Test
    fun `못 읽은 값은 줄을 지우지 않고 알 수 없음으로 적는다`() {
        val body = supportMailBody(appVersion = null, androidRelease = null, deviceModel = null)
        assertTrue(body.contains("앱 버전: 알 수 없음"))
        assertTrue(body.contains("Android: 알 수 없음"))
        assertTrue(body.contains("기기: 알 수 없음"))
    }

    @Test
    fun `사용자가 적을 자리가 맨 위에 비어 있다`() {
        val body = supportMailBody(appVersion = "1.0.0", androidRelease = "14", deviceModel = "Pixel 6")
        assertTrue(body.startsWith("\n\n"))
    }
}
