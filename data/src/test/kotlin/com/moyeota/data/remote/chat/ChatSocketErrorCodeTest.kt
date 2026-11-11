package com.moyeota.data.remote.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 소켓 서버가 ERROR 프레임 `message` 헤더에 싣는 고정 코드 → 앱 동작.
 * 특히 `TOKEN_INVALID` 를 재발급으로 다루면 무한 반복이라, 둘이 갈리는 것을 못 박는다.
 */
class ChatSocketErrorCodeTest {

    @Test
    fun `코드마다 정해진 동작으로 간다`() {
        val table = mapOf(
            ChatSocketErrorCode.TOKEN_EXPIRED to ChatSocketErrorAction.RENEW_TOKEN,
            ChatSocketErrorCode.TOKEN_INVALID to ChatSocketErrorAction.RELOGIN,
            ChatSocketErrorCode.SERVER_UNAVAILABLE to ChatSocketErrorAction.RETRY,
            ChatSocketErrorCode.NOT_PARTICIPANT to ChatSocketErrorAction.STOP,
            ChatSocketErrorCode.INVALID_DESTINATION to ChatSocketErrorAction.STOP,
            ChatSocketErrorCode.SEND_NOT_SUPPORTED to ChatSocketErrorAction.RETRY,
        )
        assertEquals("새 코드가 생기면 표에 동작을 정해야 한다", ChatSocketErrorCode.entries.toSet(), table.keys)
        table.forEach { (code, action) -> assertEquals(code.name, action, code.toAction()) }
    }

    @Test
    fun `코드 없는 실패는 백오프 재시도다`() {
        assertEquals(ChatSocketErrorAction.RETRY, (null as ChatSocketErrorCode?).toAction())
    }

    @Test
    fun `헤더 값은 이름과 정확히 같아야 읽힌다`() {
        assertEquals(ChatSocketErrorCode.TOKEN_EXPIRED, ChatSocketErrorCode.of(" TOKEN_EXPIRED "))
        assertNull(ChatSocketErrorCode.of("token_expired"))
        assertNull(ChatSocketErrorCode.of(""))
        assertNull(ChatSocketErrorCode.of(null))
    }

    @Test
    fun `감싼 예외의 메시지에서도 코드를 꺼낸다`() {
        val wrapped = RuntimeException("connect failed", IllegalStateException("STOMP ERROR frame received: NOT_PARTICIPANT"))

        assertEquals(ChatSocketErrorCode.NOT_PARTICIPANT, wrapped.chatSocketErrorCode())
    }

    @Test
    fun `TOKEN_INVALID 를 TOKEN_EXPIRED 로 잘못 읽지 않는다`() {
        assertEquals(ChatSocketErrorCode.TOKEN_INVALID, IllegalStateException("TOKEN_INVALID").chatSocketErrorCode())
    }

    @Test
    fun `코드가 없는 네트워크 실패는 null 이다`() {
        assertNull(java.io.IOException("socket closed").chatSocketErrorCode())
    }

    /** 폴백은 메시지가 코드로 **끝날 때만** 맞춘다 — 설명 문구에 인용된 코드 이름에 속지 않는다 */
    @Test
    fun `메시지 중간에 인용된 코드 이름은 코드로 읽지 않는다`() {
        assertNull(IllegalStateException("retry after TOKEN_EXPIRED handling failed").chatSocketErrorCode())
    }
}
