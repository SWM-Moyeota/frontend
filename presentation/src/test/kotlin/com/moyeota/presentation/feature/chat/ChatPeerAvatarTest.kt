package com.moyeota.presentation.feature.chat

import com.moyeota.domain.model.ChatMember
import com.moyeota.domain.model.ChatMessage
import com.moyeota.domain.model.ChatMessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 채팅 아바타에 **상대 사진**을 그리는 규칙.
 *
 * 못 박는 것은 두 가지다.
 * - 목록 행: 제목이 부르는 사람과 얼굴이 **같은 사람**이어야 한다. 제목과 얼굴이 멤버를 따로 고르면
 *   「A 이름 + B 얼굴」이 되는데, 둘 다 그럴듯해 보여 눈으로는 잡히지 않는다.
 * - 말풍선: 메시지 응답에는 사진이 없다. 참여자 조회로 세운 `publicId → 사진` 사전이 유일한 출처다.
 *
 * 사진 URL 은 CDN 의 프리셋 주소 형태를 그대로 쓴다(백엔드 소스 기준 — 이번에 로컬 서버 DB 가 죽어
 * API 를 실제로 호출해 보지는 못했다).
 */
class ChatPeerAvatarTest {

    private fun member(
        nickname: String,
        imageUrl: String? = "https://cdn.moyeota.co.kr/profile/presets/01.png",
        active: Boolean = true,
        isMe: Boolean = false,
    ) = ChatMember(
        userId = null,
        publicId = "public-$nickname",
        nickname = nickname,
        imageUrl = imageUrl,
        active = active,
        isMe = isMe,
    )

    private fun message(
        id: Long = 1L,
        senderPublicId: String? = "public-스모크일",
        isMine: Boolean = false,
    ) = ChatMessage(
        id = id,
        chatRoomId = 7L,
        senderPublicId = senderPublicId,
        content = "정문 앞 편의점에 있어요",
        type = ChatMessageType.TEXT,
        createdAt = "2026-09-11T03:00:00Z",
        deleted = false,
        isMine = isMine,
        senderName = "스모크일",
    )

    @Test
    fun `목록 행의 얼굴은 제목이 부르는 순서 그대로다`() {
        val members = listOf(
            member("스모크일", imageUrl = "https://cdn.moyeota.co.kr/profile/presets/01.png"),
            member("스모크삼", imageUrl = "https://cdn.moyeota.co.kr/profile/presets/03.png"),
            member("나", imageUrl = "https://cdn.moyeota.co.kr/profile/presets/09.png", isMe = true),
        )
        assertEquals("스모크일, 스모크삼", chatRoomPeerTitle(members))
        assertEquals(listOf("https://cdn.moyeota.co.kr/profile/presets/01.png", "https://cdn.moyeota.co.kr/profile/presets/03.png"), chatRoomPeerImageUrls(members))
    }

    /** 셋 이상인 방은 얼굴을 모두 모은다 — 사람 수가 얼굴로 보인다. */
    @Test
    fun `셋 이상인 방은 얼굴을 모두 모은다`() {
        val members = listOf(
            member("스모크일", imageUrl = "https://cdn.moyeota.co.kr/profile/presets/01.png"),
            member("스모크삼", imageUrl = "https://cdn.moyeota.co.kr/profile/presets/03.png"),
            member("스모크사", imageUrl = "https://cdn.moyeota.co.kr/profile/presets/04.png"),
            member("나", isMe = true),
        )
        assertEquals("스모크일, 스모크삼, 스모크사", chatRoomPeerTitle(members))
        assertEquals(listOf("https://cdn.moyeota.co.kr/profile/presets/01.png", "https://cdn.moyeota.co.kr/profile/presets/03.png", "https://cdn.moyeota.co.kr/profile/presets/04.png"), chatRoomPeerImageUrls(members))
    }

    @Test
    fun `얼굴은 넷까지만 모은다`() {
        val members = (1..6).map { member("동승$it") } + member("나", isMe = true)
        assertEquals(MAX_PEER_FACES, chatRoomPeerImageUrls(members).size)
    }

    @Test
    fun `방을 나간 사람은 제목과 얼굴에서 함께 빠진다`() {
        val members = listOf(
            member("나간사람", imageUrl = "https://cdn.moyeota.co.kr/profile/presets/02.png", active = false),
            member("남은사람", imageUrl = "https://cdn.moyeota.co.kr/profile/presets/05.png"),
            member("나", isMe = true),
        )
        assertEquals("남은사람", chatRoomPeerTitle(members))
        assertEquals(listOf("https://cdn.moyeota.co.kr/profile/presets/05.png"), chatRoomPeerImageUrls(members))
    }

    /** 제목이 나간 사람을 되살려 쓰는 경우에는 얼굴도 같이 되살아나야 한다(둘의 근거가 하나라서). */
    @Test
    fun `남은 사람이 없으면 나간 사람의 얼굴이라도 쓴다`() {
        val members = listOf(
            member("나간사람", imageUrl = "https://cdn.moyeota.co.kr/profile/presets/02.png", active = false),
            member("나", isMe = true),
        )
        assertEquals("나간사람", chatRoomPeerTitle(members))
        assertEquals(listOf("https://cdn.moyeota.co.kr/profile/presets/02.png"), chatRoomPeerImageUrls(members))
    }

    /**
     * 참여자를 못 받은 방(구버전 서버 응답은 멤버가 빈 목록이다)과 나 혼자인 방은 그릴 얼굴이 없다 —
     * 빈 목록이면 `AvatarCircle` 하나가 회색 원으로 떨어진다.
     */
    @Test
    fun `나 혼자인 방과 참여자를 못 받은 방은 얼굴이 없다`() {
        assertEquals(emptyList<String?>(), chatRoomPeerImageUrls(listOf(member("나", isMe = true))))
        assertEquals(emptyList<String?>(), chatRoomPeerImageUrls(emptyList()))
    }

    /**
     * 사진이 빈 문자열이면 없는 것으로 보되 **자리는 남긴다** — 얼굴 수가 곧 사람 수라 회색 원으로라도 그린다.
     * (서버는 사진을 고른 적 없는 사람에게도 기본 이미지 URL 을 준다 — 빈 문자열이 오면 응답이 깨진 것이다.)
     */
    @Test
    fun `사진이 없는 사람도 자리는 남긴다`() {
        val members = listOf(member("스모크일", imageUrl = ""), member("스모크삼", imageUrl = null), member("나", isMe = true))
        assertEquals(listOf<String?>(null, null), chatRoomPeerImageUrls(members))
    }

    /** 인원은 나 포함, 지금 방에 있는 사람만 센다 — 이름만으로는 몇 명이 탄 방인지 모른다. */
    @Test
    fun `인원은 나를 포함해 남아 있는 사람만 센다`() {
        val members = listOf(
            member("스모크일"),
            member("나간사람", active = false),
            member("스모크삼"),
            member("나", isMe = true),
        )
        assertEquals(3, chatRoomMemberCount(members))
    }

    /** 제목은 나간 사람이라도 부르지만 수는 그러지 않는다 — 아무도 없는 방에 둘이 있다고 말하지 않는다. */
    @Test
    fun `모두 나간 방은 나만 센다`() {
        assertEquals(1, chatRoomMemberCount(listOf(member("나간사람", active = false), member("나", isMe = true))))
    }

    /** 얼굴은 상대만 그려 1:1 방에 「2명」을 붙이면 얼굴 하나 옆에 2 가 선다 — 카카오톡처럼 셋부터 적는다. */
    @Test
    fun `인원 표기는 셋 이상인 방에만 붙는다`() {
        assertNull(chatRoomMemberCountLabel(2))
        assertNull(chatRoomMemberCountLabel(1))
        assertNull(chatRoomMemberCountLabel(null))
        assertEquals("3명", chatRoomMemberCountLabel(3))
        assertEquals("4명", chatRoomMemberCountLabel(4))
    }

    @Test
    fun `참여자를 모르면 인원도 모른다`() {
        assertNull(chatRoomMemberCount(emptyList()))
        assertNull(chatRoomMemberCount(listOf(member("나간사람", active = false))))
    }

    @Test
    fun `얼굴 수만큼 자리를 만들고 하나뿐이면 상자를 꽉 채운다`() {
        assertEquals(listOf(FaceSlot(0f, 0f, 1f)), peerFaceSlots(1))
        assertEquals(listOf(FaceSlot(0f, 0f, 1f)), peerFaceSlots(0))
        for (count in 2..4) assertEquals(count, peerFaceSlots(count).size)
        assertEquals(4, peerFaceSlots(9).size)
    }

    /** 모든 얼굴이 상자 안에 들어간다 — 넘치면 옆 행이나 제목을 덮는다. */
    @Test
    fun `얼굴은 상자 밖으로 나가지 않는다`() {
        for (count in 1..4) for (slot in peerFaceSlots(count)) {
            assertTrue(slot.x >= 0f && slot.y >= 0f && slot.x + slot.size <= 1f + 1e-6f && slot.y + slot.size <= 1f + 1e-6f)
        }
    }

    /** 둘은 왼쪽 위·오른쪽 아래, 셋은 위 가운데 하나와 아래 둘 — 뒤에 오는 얼굴이 아래쪽에서 위에 그려진다. */
    @Test
    fun `둘은 비껴 겹치고 셋은 삼각형이다`() {
        val (back, front) = peerFaceSlots(2)
        assertEquals(0f, back.x); assertEquals(0f, back.y)
        assertEquals(1f, front.x + front.size, 1e-6f); assertEquals(1f, front.y + front.size, 1e-6f)

        val (top, left, right) = peerFaceSlots(3)
        assertEquals(0.5f, top.x + top.size / 2f, 1e-6f)
        assertEquals(0f, top.y)
        assertEquals(left.y, right.y)
        assertEquals(0f, left.x)
        assertEquals(1f, right.x + right.size, 1e-6f)
    }

    @Test
    fun `사전은 참여자의 publicId 에 사진을 잇는다`() {
        val members = listOf(
            member("스모크일", imageUrl = "https://cdn.moyeota.co.kr/profile/presets/01.png"),
            member("나", imageUrl = "https://cdn.moyeota.co.kr/profile/presets/09.png", isMe = true),
        )
        assertEquals(
            mapOf(
                "public-스모크일" to "https://cdn.moyeota.co.kr/profile/presets/01.png",
                "public-나" to "https://cdn.moyeota.co.kr/profile/presets/09.png",
            ),
            chatMemberImageUrls(members),
        )
    }

    /** 사진 없는 사람을 null 값으로 담아 두면 「사전에 있다」와 「얼굴이 있다」가 달라진다 — 아예 담지 않는다. */
    @Test
    fun `사진이 없는 참여자는 사전에 담지 않는다`() {
        val members = listOf(member("무사진", imageUrl = null), member("빈문자", imageUrl = ""))
        assertEquals(emptyMap<String, String>(), chatMemberImageUrls(members))
    }

    /** 나간 사람이 남긴 메시지도 얼굴을 유지한다 — 이름을 남기는 것과 같은 이유다. */
    @Test
    fun `방을 나간 사람도 사전에 남는다`() {
        val members = listOf(
            member("나간사람", imageUrl = "https://cdn.moyeota.co.kr/profile/presets/02.png", active = false),
        )
        assertEquals(
            mapOf("public-나간사람" to "https://cdn.moyeota.co.kr/profile/presets/02.png"),
            chatMemberImageUrls(members),
        )
    }

    @Test
    fun `상대 메시지는 사전에서 발신자 사진을 찾아 싣는다`() {
        val dictionary = mapOf("public-스모크일" to "https://cdn.moyeota.co.kr/profile/presets/01.png")
        val ui = message().toUiMessage(dictionary)
        assertEquals("https://cdn.moyeota.co.kr/profile/presets/01.png", ui.senderImageUrl)
        assertEquals("스모크일", ui.senderName)
    }

    /** 내 말풍선에는 아바타 자리가 없다 — 사전에 내 사진이 있어도 싣지 않는다(이름도 그렇다). */
    @Test
    fun `내 메시지는 사전에 내 사진이 있어도 싣지 않는다`() {
        val dictionary = mapOf("public-나" to "https://cdn.moyeota.co.kr/profile/presets/09.png")
        val ui = message(senderPublicId = "public-나", isMine = true).toUiMessage(dictionary)
        assertNull(ui.senderImageUrl)
        assertNull(ui.senderName)
    }

    /**
     * 참여자 조회가 아직/끝내 안 끝난 방, 탈퇴로 서버가 신원을 못 실은 메시지 — 둘 다 회색 원이다.
     * 이름이 「동승자」로 떨어지는 것과 같은 자리다.
     */
    @Test
    fun `사전에 없는 발신자와 신원 없는 메시지는 사진 없이 그린다`() {
        assertNull(message().toUiMessage(emptyMap()).senderImageUrl)
        val unknown = message(senderPublicId = null)
            .toUiMessage(mapOf("public-스모크일" to "https://cdn.moyeota.co.kr/profile/presets/01.png"))
        assertNull(unknown.senderImageUrl)
    }
}
