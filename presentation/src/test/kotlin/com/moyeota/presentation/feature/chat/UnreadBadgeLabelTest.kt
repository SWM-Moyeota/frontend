package com.moyeota.presentation.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 채팅 목록 안읽음 배지 — 서버 unreadCount 를 어떻게 적는가. null(개수 모름)은 점으로 떨어진다. */
class UnreadBadgeLabelTest {

    @Test
    fun `개수를 모르면 라벨이 없다 - 점 배지`() {
        assertNull(unreadBadgeLabel(null))
    }

    @Test
    fun `0 이하는 그리지 않는다`() {
        assertNull(unreadBadgeLabel(0))
        assertNull(unreadBadgeLabel(-1))
    }

    @Test
    fun `두 자리까지는 그대로, 그 위는 99+`() {
        assertEquals("1", unreadBadgeLabel(1))
        assertEquals("99", unreadBadgeLabel(99))
        assertEquals("99+", unreadBadgeLabel(100))
        assertEquals("99+", unreadBadgeLabel(1234))
    }
}
