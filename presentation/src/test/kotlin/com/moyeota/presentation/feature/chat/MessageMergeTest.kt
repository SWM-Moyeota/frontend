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
        seq: Long? = null,
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
        seq = seq,
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

    // --- 채팅 소켓 서버 분리: seq 정렬 · 삭제 우선 · 빈 번호 ---

    /** 화면 순서는 seq 다 — id 가 뒤집혀 저장돼도(다른 서버·스레드) 서버가 매긴 순번을 따른다 */
    @Test
    fun `seq 가 있으면 id 가 아니라 seq 순이다`() {
        val merged = mergeMessagesById(
            listOf(message(5, "둘째", seq = 2)),
            listOf(message(4, "셋째", seq = 3), message(6, "첫째", seq = 1)),
        )

        assertEquals(listOf("첫째", "둘째", "셋째"), merged.map { it.content })
    }

    /** 원본이 순서 맞추기로 대기하는 사이 삭제 이벤트가 먼저 오면, 뒤늦은 원본이 지운 말을 되살리면 안 된다 */
    @Test
    fun `삭제 이벤트가 원본보다 먼저 와도 삭제 상태가 이긴다`() {
        val deletedFirst = mergeMessagesById(emptyList(), listOf(message(7, "삭제된 메시지입니다", deleted = true)))

        val merged = mergeMessagesById(deletedFirst, listOf(message(7, "원문", seq = 12)))

        val only = merged.single()
        assertTrue(only.deleted)
        assertEquals("삭제된 메시지입니다", only.content)
        assertEquals("삭제 사본이 원본의 순번을 빌려 와야 정렬된다", 12L, only.seq)
    }

    @Test
    fun `seq 10 11 13 이면 11번 메시지 id 가 빈 번호 커서다`() {
        val messages = mergeMessagesById(
            emptyList(),
            listOf(message(100, "a", seq = 10), message(101, "b", seq = 11), message(103, "c", seq = 13)),
        )

        assertEquals(101L, seqGapCursor(messages))
    }

    @Test
    fun `빈틈이 없거나 seq 가 없으면 커서도 없다`() {
        assertEquals(null, seqGapCursor(listOf(message(1, "a", seq = 1), message(2, "b", seq = 2))))
        assertEquals(null, seqGapCursor(listOf(message(1, "a"), message(5, "b"))))
        assertEquals(null, seqGapCursor(emptyList()))
    }

    /** 영영 안 메워지는 빈틈이 앞에 남아도 뒤에 새로 생긴 빈틈은 찾아야 한다 */
    @Test
    fun `채워 본 빈틈은 건너뛰고 다음 빈틈을 찾는다`() {
        val messages = listOf(
            message(100, "a", seq = 10), message(101, "b", seq = 11), message(103, "c", seq = 13),
            message(104, "d", seq = 14), message(106, "e", seq = 16),
        )

        assertEquals(101L, seqGapCursor(messages))
        assertEquals(104L, seqGapCursor(messages, skip = setOf(101L)))
        assertEquals(null, seqGapCursor(messages, skip = setOf(101L, 104L)))
    }
}
