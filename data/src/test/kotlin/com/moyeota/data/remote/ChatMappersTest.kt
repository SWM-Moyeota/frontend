package com.moyeota.data.remote

import com.moyeota.data.remote.dto.ChatMemberResponse
import com.moyeota.data.remote.dto.ChatMessageResponse
import com.moyeota.data.remote.dto.ChatMessageSliceResponse
import com.moyeota.data.remote.dto.ChatRoomResponse
import com.moyeota.data.remote.dto.ChatRoomUserResponse
import com.moyeota.domain.model.ChatMessageType
import com.moyeota.domain.model.ChatRoomStatus
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMappersTest {

    private val json = Json { ignoreUnknownKeys = true }

    // 아직 아무것도 학습하지 못한(= 이번 세션에 메시지를 보낸 적 없는) 상태.
    private val stranger = ChatIdentity(myUuid = UUID_ME, myInternalId = null)
    private val learned = ChatIdentity(myUuid = UUID_ME, myInternalId = 3L)

    // 참여자 목록을 받아 둔 상태: 3 번이 나, 4 번이 동승자, 8 번은 방을 나간 사람.
    private val known = ChatIdentity(
        myUuid = UUID_ME,
        myInternalId = null,
        members = listOf(
            member(userId = 3, publicId = UUID_ME, nickname = "성윤"),
            member(userId = 4, publicId = "uuid-other", nickname = "자동에이"),
            member(userId = 8, publicId = "uuid-gone", nickname = "먼저내림", active = false),
        ).associateBy { it.userId!! },
    )

    @Test
    fun `채팅방 응답을 도메인 ChatRoom 으로 매핑한다`() {
        val room = ChatRoomResponse(
            id = 5,
            partyId = 7,
            departure = "서울시청",
            destination = "강남역",
            createdAt = "2026-08-24T09:00:00Z",
            status = "CLOSED",
        ).toChatRoom()

        assertEquals(5L, room.id)
        assertEquals(7L, room.partyId)
        assertEquals(ChatRoomStatus.CLOSED, room.status)
        assertEquals("2026-08-24T09:00:00Z", room.createdAt)
    }

    @Test
    fun `알 수 없는 방 상태와 메시지 타입은 기본값으로 흡수한다`() {
        assertEquals(ChatRoomStatus.ACTIVE, chatRoomStatusOf("SOMETHING_NEW"))
        assertEquals(ChatMessageType.TEXT, chatMessageTypeOf("SOMETHING_NEW"))
        assertEquals(ChatMessageType.LOCATION, chatMessageTypeOf("LOCATION"))
    }

    // 서버 필드는 userId 지만 도메인은 senderId 다 — 이름이 바뀌는 유일한 지점.
    @Test
    fun `메시지 응답의 userId 를 senderId 로 매핑한다`() {
        val message = ChatMessageResponse(
            id = 20,
            chatRoomId = 5,
            userId = 3,
            content = "안녕하세요",
            type = "TEXT",
            createdAt = "2026-08-24T09:01:00Z",
            deleted = false,
        ).toChatMessage(learned)

        assertEquals(3L, message.senderId)
        assertEquals("안녕하세요", message.content)
        assertEquals(ChatMessageType.TEXT, message.type)
    }

    @Test
    fun `삭제된 메시지는 deleted 플래그와 치환된 본문을 유지한다`() {
        val message = ChatMessageResponse(
            id = 21,
            chatRoomId = 5,
            userId = 3,
            content = "삭제된 메시지입니다",
            type = "TEXT",
            deleted = true,
        ).toChatMessage(stranger)

        assertTrue(message.deleted)
        assertEquals("삭제된 메시지입니다", message.content)
    }

    @Test
    fun `커서 페이징 응답을 ChatMessagePage 로 매핑한다`() {
        val page = ChatMessageSliceResponse(
            messages = listOf(ChatMessageResponse(id = 1), ChatMessageResponse(id = 2)),
            nextCursor = 1,
            hasNext = true,
        ).toPage(stranger)

        assertEquals(2, page.messages.size)
        assertEquals(1L, page.nextCursor)
        assertTrue(page.hasNext)
    }

    @Test
    fun `마지막 페이지는 nextCursor 가 null 이다`() {
        val page = ChatMessageSliceResponse(messages = emptyList(), nextCursor = null, hasNext = false).toPage(stranger)

        assertNull(page.nextCursor)
        assertTrue(page.messages.isEmpty())
    }

    @Test
    fun `아직 아무것도 안 읽은 참여자는 lastReadMessageId 가 null 이다`() {
        val membership = ChatRoomUserResponse(chatRoomId = 5, lastReadMessageId = null).toMembership()

        assertNull(membership.lastReadMessageId)
        assertEquals(5L, membership.chatRoomId)
        assertEquals("", membership.joinedAt)
    }

    // /chat-rooms/me 는 래핑 객체가 아니라 배열을 그대로 내려준다.
    @Test
    fun `내 채팅방 목록 응답 JSON 을 배열로 역직렬화한다`() {
        val body = """
            [{"chatRoomId":5,"lastReadMessageId":20,"notificationMuted":false,"joinedAt":"2026-08-24T09:00:00Z"}]
        """.trimIndent()

        val decoded = json.decodeFromString<List<ChatRoomUserResponse>>(body)

        assertEquals(1, decoded.size)
        assertEquals(20L, decoded[0].toMembership().lastReadMessageId)
    }

    /**
     * 학습 전에는 **내 메시지도 상대 메시지로 보인다.** 이게 이 인터림 전략의 유일한 대가라
     * 회귀로 오해하지 않게 못 박아 둔다(서버가 senderPublicId 를 주면 자동으로 사라진다).
     */
    @Test
    fun `학습 전에는 어떤 메시지도 내 것이 되지 않는다`() {
        val message = ChatMessageResponse(id = 20, userId = 3).toChatMessage(stranger)

        assertFalse(message.isMine)
    }

    @Test
    fun `학습한 내부 id 와 같은 발신자면 내 메시지다`() {
        assertTrue(ChatMessageResponse(id = 20, userId = 3).toChatMessage(learned).isMine)
        assertFalse(ChatMessageResponse(id = 21, userId = 4).toChatMessage(learned).isMine)
    }

    /**
     * 서버가 senderPublicId 를 추가하면 학습값보다 **그쪽이 이긴다.**
     * 두 근거가 어긋나는 건 계정 전환 직후처럼 학습값이 낡았을 때뿐이고, 그때 맞는 쪽은 publicId 다.
     */
    @Test
    fun `senderPublicId 가 오면 학습값보다 우선한다`() {
        val other = ChatMessageResponse(id = 22, userId = 3, senderPublicId = "uuid-other")
        val me = ChatMessageResponse(id = 23, userId = 999, senderPublicId = UUID_ME)

        // userId 는 학습값과 같지만 publicId 가 남이므로 내 메시지가 아니다.
        assertFalse(other.toChatMessage(learned).isMine)
        // 반대로 userId 가 달라도 publicId 가 나면 내 메시지다.
        assertTrue(me.toChatMessage(learned).isMine)
    }

    @Test
    fun `빈 senderPublicId 는 없는 것으로 보고 학습값으로 판정한다`() {
        val message = ChatMessageResponse(id = 24, userId = 3, senderPublicId = " ")

        assertTrue(message.toChatMessage(learned).isMine)
    }

    @Test
    fun `senderNickname 이 비어 있으면 senderName 은 null 이다`() {
        assertNull(ChatMessageResponse(id = 25, senderNickname = "").toChatMessage(stranger).senderName)
        assertEquals("성윤", ChatMessageResponse(id = 26, senderNickname = "성윤").toChatMessage(stranger).senderName)
    }

    // 서버가 아직 안 주는 필드라, 없어도 역직렬화가 깨지지 않아야 한다(추가되면 그대로 실린다).
    @Test
    fun `senderPublicId 가 없는 현재 응답도 그대로 역직렬화된다`() {
        val body = """
            {"id":20,"chatRoomId":5,"userId":3,"content":"안녕","type":"TEXT","createdAt":"2026-09-07T09:00:00Z","deleted":false}
        """.trimIndent()

        val decoded = json.decodeFromString<ChatMessageResponse>(body)

        assertNull(decoded.senderPublicId)
        assertNull(decoded.senderNickname)
        assertEquals(3L, decoded.userId)
    }

    // 참여자 목록이 있으면 학습이 필요 없다 — 방에 다시 들어온 첫 화면부터 내 말풍선이 제자리다.
    @Test
    fun `참여자 목록이 있으면 학습 없이도 내 메시지를 가린다`() {
        assertTrue(ChatMessageResponse(id = 20, userId = 3).toChatMessage(known).isMine)
        assertFalse(ChatMessageResponse(id = 21, userId = 4).toChatMessage(known).isMine)
    }

    @Test
    fun `참여자 목록에서 보낸 사람 닉네임을 채운다`() {
        assertEquals("자동에이", ChatMessageResponse(id = 21, userId = 4).toChatMessage(known).senderName)
    }

    // 나간 사람(active=false)도 사전에 남는다. 빼면 그 사람이 남긴 대화가 통째로 "동승자"가 된다.
    @Test
    fun `방을 나간 참여자의 메시지도 이름을 유지한다`() {
        val message = ChatMessageResponse(id = 22, userId = 8).toChatMessage(known)

        assertEquals("먼저내림", message.senderName)
        assertFalse(message.isMine)
    }

    // 판정 근거의 우선순위. 셋이 어긋나는 경우를 한 곳에서 못 박는다.
    @Test
    fun `senderPublicId 가 참여자 목록보다 우선한다`() {
        // 사전에는 내 id 지만 메시지가 남의 publicId 를 실어 왔다 — 서버가 직접 말한 쪽이 이긴다.
        val message = ChatMessageResponse(id = 23, userId = 3, senderPublicId = "uuid-other")

        assertFalse(message.toChatMessage(known).isMine)
    }

    @Test
    fun `참여자 목록이 학습값보다 우선한다`() {
        // 학습값은 3 번이 나라고 하지만 사전은 3 번을 남으로 안다(계정 전환 직후의 낡은 학습).
        val identity = ChatIdentity(
            myUuid = UUID_ME,
            myInternalId = 3L,
            members = mapOf(3L to member(userId = 3, publicId = "uuid-other", nickname = "자동에이")),
        )

        assertFalse(ChatMessageResponse(id = 24, userId = 3).toChatMessage(identity).isMine)
    }

    // 사전에 없는 발신자에게는 기존 학습 폴백이 그대로 적용된다(구버전 서버·중간 합류자).
    @Test
    fun `참여자 목록에 없는 발신자는 학습값으로 판정한다`() {
        val identity = ChatIdentity(
            myUuid = UUID_ME,
            myInternalId = 9L,
            members = mapOf(4L to member(userId = 4, publicId = "uuid-other", nickname = "자동에이")),
        )

        val message = ChatMessageResponse(id = 25, userId = 9).toChatMessage(identity)

        assertTrue(message.isMine)
        assertNull(message.senderName)
    }

    // userId 를 안 주는(패치 전) 서버 응답. 필드가 없어도 역직렬화가 깨지면 안 된다.
    @Test
    fun `참여자 응답을 도메인 ChatMember 로 매핑한다`() {
        val body = """
            [{"userId":1,"publicId":"uuid-me","nickname":"성윤","imageUrl":null,"active":true},
             {"publicId":"uuid-other","nickname":"자동에이","imageUrl":"http://img","active":false}]
        """.trimIndent()

        val decoded = json.decodeFromString<List<ChatMemberResponse>>(body).map { it.toChatMember(UUID_ME) }

        assertEquals(listOf(1L, null), decoded.map { it.userId })
        assertTrue(decoded[0].isMe)
        assertEquals("성윤", decoded[0].nickname)
        assertFalse(decoded[1].isMe)
        assertFalse(decoded[1].active)
        assertEquals("http://img", decoded[1].imageUrl)
    }

    // 로그아웃 상태(myUuid=null)에서 publicId 가 빈 참여자를 만나면 "빈 값끼리 같다"로
    // 남이 나로 둔갑할 수 있다 — 그 구멍을 막는다.
    @Test
    fun `publicId 가 비면 누구도 내가 되지 않는다`() {
        assertFalse(ChatMemberResponse(userId = 1, publicId = "").toChatMember(null).isMe)
        assertFalse(ChatMemberResponse(userId = 1, publicId = "").toChatMember(UUID_ME).isMe)
    }
}

private fun member(
    userId: Long,
    publicId: String,
    nickname: String,
    active: Boolean = true,
) = ChatMemberResponse(
    userId = userId,
    publicId = publicId,
    nickname = nickname,
    imageUrl = null,
    active = active,
).toChatMember(UUID_ME)

private const val UUID_ME = "uuid-me"
