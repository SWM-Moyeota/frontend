package com.moyeota.presentation.feature.chat

import com.moyeota.domain.model.ChatMessage
import com.moyeota.domain.model.ChatMessageType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 소켓 전송은 응답이 없다 — 보낸 메시지는 구독으로 **되돌아와야** 확인된다.
 * 그 확인이 어떤 메시지와 짝지어지는지가 「보내는 중」 말풍선이 사라지는 규칙이다.
 */
class PendingMessageTest {

    private fun message(id: Long, content: String, mine: Boolean = true) = ChatMessage(
        id = id,
        chatRoomId = 1L,
        senderPublicId = if (mine) "me" else "peer",
        content = content,
        type = ChatMessageType.TEXT,
        createdAt = "2026-09-15T00:00:00Z",
        deleted = false,
        isMine = mine,
        senderName = if (mine) null else "동승자",
    )

    private fun pending(localId: Long, content: String, sinceId: Long) =
        ChatRoomViewModel.PendingMessage(localId = localId, content = content, sinceId = sinceId)

    @Test
    fun `되돌아온 내 메시지와 짝지어지면 사라진다`() {
        val left = reconcilePending(
            messages = listOf(message(10, "다 왔어요")),
            pending = listOf(pending(-1, "다 왔어요", sinceId = 9)),
        )

        assertEquals(emptyList<ChatRoomViewModel.PendingMessage>(), left)
    }

    @Test
    fun `보내기 전에 있던 같은 문장은 확인이 아니다`() {
        val left = reconcilePending(
            messages = listOf(message(9, "네")),
            pending = listOf(pending(-1, "네", sinceId = 9)),
        )

        assertEquals(1, left.size)
    }

    @Test
    fun `같은 문장을 두 번 보내면 하나씩 짝지어진다`() {
        val waiting = listOf(pending(-1, "네", sinceId = 5), pending(-2, "네", sinceId = 5))

        val one = reconcilePending(listOf(message(6, "네")), waiting)
        assertEquals(listOf(-2L), one.map { it.localId })

        val none = reconcilePending(listOf(message(6, "네"), message(7, "네")), waiting)
        assertEquals(emptyList<Long>(), none.map { it.localId })
    }

    @Test
    fun `같은 내용이어도 상대가 보낸 메시지는 확인이 아니다`() {
        val left = reconcilePending(
            messages = listOf(message(10, "네", mine = false)),
            pending = listOf(pending(-1, "네", sinceId = 5)),
        )

        assertEquals(1, left.size)
    }
}
