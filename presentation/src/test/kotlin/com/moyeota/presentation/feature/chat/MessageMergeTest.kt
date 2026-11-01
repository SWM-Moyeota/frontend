package com.moyeota.presentation.feature.chat

import com.moyeota.domain.model.ChatMessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.moyeota.domain.model.ChatMessage as DomainChatMessage

/**
 * 같은 id 의 메시지가 두 번 들어올 때 **어느 사본이 남는가**([mergeMessagesById]).
 *
 * 예전에는 `distinctBy { it.id }` 였고, 그건 **먼저 등장한** 쪽을 남긴다. 그래서 동승자가 메시지를
 * 지우면 서버가 같은 id 를 `deleted=true`·「삭제된 메시지입니다」로 다시 브로드캐스트하는데도
 * (`ChatMessageBroadcaster.onMessageDeleted`) 화면은 삭제 전 원문을 그대로 들고 있었다 —
 * **지운 말이 화면에 남는다.** 증상이 「아무 일도 안 일어남」이라 눈으로는 회귀를 못 잡는다.
 */
class MessageMergeTest {

    private fun message(
        id: Long,
        content: String,
        deleted: Boolean = false,
    ) = DomainChatMessage(
        id = id,
        chatRoomId = 1,
        senderPublicId = "uuid-peer",
        content = content,
        type = ChatMessageType.TEXT,
        createdAt = "2026-10-28T02:00:0${id}Z",
        deleted = deleted,
        isMine = false,
        senderName = "동승자",
    )

    @Test
    fun `같은 id 의 새 사본이 이긴다`() {
        val held = listOf(message(1, "안녕"), message(2, "지울 말"))
        val deletedCopy = message(2, "삭제된 메시지입니다", deleted = true)

        val merged = mergeMessagesById(held, listOf(deletedCopy))

        assertEquals(2, merged.size)
        val second = merged.last()
        assertEquals("삭제된 메시지입니다", second.content)
        assertTrue("deleted 플래그까지 새 사본을 따라야 한다", second.deleted)
    }

    @Test
    fun `합친 뒤에도 id 오름차순이다`() {
        // 소켓·조회·REST 응답이 섞여 들어오는 자리라 순서를 믿을 수 없다 — 말풍선 순서의 유일한 근거다
        val merged = mergeMessagesById(
            held = listOf(message(5, "다섯"), message(1, "하나")),
            incoming = listOf(message(9, "아홉"), message(3, "셋")),
        )

        assertEquals(listOf(1L, 3L, 5L, 9L), merged.map { it.id })
    }

    @Test
    fun `겹치지 않으면 둘 다 남는다`() {
        val merged = mergeMessagesById(listOf(message(1, "하나")), listOf(message(2, "둘")))

        assertEquals(listOf(1L, 2L), merged.map { it.id })
    }

    @Test
    fun `한쪽이 비어도 그대로 돌려준다`() {
        val held = listOf(message(1, "하나"))

        assertEquals(held, mergeMessagesById(held, emptyList()))
        assertEquals(held, mergeMessagesById(emptyList(), held))
    }
}
