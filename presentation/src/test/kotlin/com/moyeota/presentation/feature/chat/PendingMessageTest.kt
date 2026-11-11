package com.moyeota.presentation.feature.chat

import com.moyeota.domain.model.ChatMessage
import com.moyeota.domain.model.ChatMessageType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 보낸 메시지는 저장 응답(201)보다 소켓 브로드캐스트로 **먼저 되돌아올** 수 있다.
 * 그 확인이 어떤 메시지와 짝지어지는지가 대기 말풍선이 실제 메시지로 넘어가는 규칙이다.
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

    /** 실패는 사용자가 다시 보낼 때까지 남는다 — 다시 보낸 같은 문장의 확인이 「전송 실패」를 지우면 안 된다 */
    @Test
    fun `실패한 말풍선은 같은 문장의 확인으로 지워지지 않는다`() {
        val waiting = listOf(
            ChatRoomViewModel.PendingMessage(localId = -1, content = "네", sinceId = 5, failed = true),
            pending(-2, "네", sinceId = 5),
        )

        val left = reconcilePending(listOf(message(6, "네")), waiting)

        assertEquals(listOf(-1L), left.map { it.localId })
    }
}
