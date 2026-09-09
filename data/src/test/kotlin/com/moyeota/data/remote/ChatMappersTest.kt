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

    // 참여자 목록을 아직 못 받은 상태. isMine 은 여기서도 정확해야 한다 — 사전은 이름 전용이다.
    private val noDictionary = ChatIdentity(myUuid = UUID_ME)

    // 참여자 목록을 받아 둔 상태: 나 / 동승자 / 방을 나간 사람.
    private val known = ChatIdentity(
        myUuid = UUID_ME,
        members = listOf(
            member(publicId = UUID_ME, nickname = "성윤"),
            member(publicId = UUID_OTHER, nickname = "자동에이"),
            member(publicId = "uuid-gone", nickname = "먼저내림", active = false),
        ).associateBy { it.publicId },
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

    /**
     * 배포 서버 실측 응답(2026-09-09) 그대로. 발신자는 `publicId` 로 오고 `userId` 는 없다 —
     * 이 JSON 이 파싱되고 publicId 가 도메인까지 흐르는지가 이번 전환의 핵심이다.
     */
    @Test
    fun `서버 실측 응답의 publicId 를 senderPublicId 로 매핑한다`() {
        val body = """
            {"id":4,"chatRoomId":1,"publicId":"uuid-me","content":"probe","type":"TEXT",
             "createdAt":"2026-09-09T07:23:16.242906Z","deleted":false}
        """.trimIndent()

        val message = json.decodeFromString<ChatMessageResponse>(body).toChatMessage(noDictionary)

        assertEquals(UUID_ME, message.senderPublicId)
        assertEquals("probe", message.content)
        assertEquals(ChatMessageType.TEXT, message.type)
        assertTrue("서버가 준 publicId 가 내 uuid 인데 남의 메시지로 판정했다", message.isMine)
    }

    @Test
    fun `발신자 publicId 가 세션 uuid 와 다르면 남의 메시지다`() {
        assertFalse(message(id = 21, publicId = UUID_OTHER).toChatMessage(noDictionary).isMine)
    }

    /** 관용 필드. 서버가 필드명을 `senderPublicId` 로 되돌려도 앱이 따라간다. */
    @Test
    fun `senderPublicId 로 와도 같은 값으로 읽는다`() {
        val message = ChatMessageResponse(id = 22, senderPublicId = UUID_ME).toChatMessage(noDictionary)

        assertEquals(UUID_ME, message.senderPublicId)
        assertTrue(message.isMine)
    }

    @Test
    fun `senderPublicId 와 publicId 가 함께 오면 senderPublicId 가 이긴다`() {
        val message = ChatMessageResponse(id = 23, senderPublicId = UUID_OTHER, publicId = UUID_ME)

        assertFalse(message.toChatMessage(noDictionary).isMine)
    }

    /**
     * 빈 문자열끼리의 비교로 남의 메시지가 내 것이 되면 안 된다. 미로그인(myUuid=null)이면 더 위험하다.
     */
    @Test
    fun `발신자 식별자가 비었으면 누구의 메시지도 아니다`() {
        val blank = ChatMessageResponse(id = 24, publicId = " ", senderPublicId = "")

        assertNull(blank.toChatMessage(noDictionary).senderPublicId)
        assertFalse(blank.toChatMessage(noDictionary).isMine)
        assertFalse(blank.toChatMessage(ChatIdentity(myUuid = null)).isMine)
    }

    @Test
    fun `미로그인 상태에서는 어떤 메시지도 내 것이 아니다`() {
        assertFalse(message(id = 25, publicId = UUID_ME).toChatMessage(ChatIdentity(myUuid = null)).isMine)
    }

    @Test
    fun `삭제된 메시지는 deleted 플래그와 치환된 본문을 유지한다`() {
        val message = ChatMessageResponse(
            id = 26,
            chatRoomId = 5,
            publicId = UUID_ME,
            content = "삭제된 메시지입니다",
            type = "TEXT",
            deleted = true,
        ).toChatMessage(noDictionary)

        assertTrue(message.deleted)
        assertEquals("삭제된 메시지입니다", message.content)
    }

    @Test
    fun `참여자 목록에서 보낸 사람 닉네임을 채운다`() {
        assertEquals("자동에이", message(id = 27, publicId = UUID_OTHER).toChatMessage(known).senderName)
    }

    // 나간 사람(active=false)도 사전에 남는다. 빼면 그 사람이 남긴 대화가 통째로 "동승자"가 된다.
    @Test
    fun `방을 나간 참여자의 메시지도 이름을 유지한다`() {
        val message = message(id = 28, publicId = "uuid-gone").toChatMessage(known)

        assertEquals("먼저내림", message.senderName)
        assertFalse(message.isMine)
    }

    @Test
    fun `사전에 없는 발신자는 senderNickname 폴백을 쓰고 없으면 null 이다`() {
        val withNickname = ChatMessageResponse(id = 29, publicId = "uuid-newcomer", senderNickname = "중간합류")
        val without = message(id = 30, publicId = "uuid-newcomer")

        assertEquals("중간합류", withNickname.toChatMessage(known).senderName)
        assertNull(without.toChatMessage(known).senderName)
    }

    /** 사전이 우선이다 — 서버가 실어 준 닉네임보다 방 참여자 목록이 최신이다. */
    @Test
    fun `참여자 목록의 닉네임이 senderNickname 보다 우선한다`() {
        val message = ChatMessageResponse(id = 31, publicId = UUID_OTHER, senderNickname = "옛이름")

        assertEquals("자동에이", message.toChatMessage(known).senderName)
    }

    @Test
    fun `커서 페이징 응답을 ChatMessagePage 로 매핑한다`() {
        val page = ChatMessageSliceResponse(
            messages = listOf(ChatMessageResponse(id = 1), ChatMessageResponse(id = 2)),
            nextCursor = 1,
            hasNext = true,
        ).toPage(noDictionary)

        assertEquals(2, page.messages.size)
        assertEquals(1L, page.nextCursor)
        assertTrue(page.hasNext)
    }

    @Test
    fun `마지막 페이지는 nextCursor 가 null 이다`() {
        val page = ChatMessageSliceResponse(messages = emptyList(), nextCursor = null, hasNext = false)
            .toPage(noDictionary)

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
            [{"chatRoomId":1,"lastReadMessageId":null,"notificationMuted":false,"joinedAt":"2026-09-08T06:14:44.468742Z"}]
        """.trimIndent()

        val decoded = json.decodeFromString<List<ChatRoomUserResponse>>(body)

        assertEquals(1, decoded.size)
        assertNull(decoded[0].toMembership().lastReadMessageId)
    }

    /** 배포 서버 실측(2026-09-09): 참여자 응답에 userId 가 없다. 없어도 매핑이 깨지면 안 된다. */
    @Test
    fun `참여자 응답을 도메인 ChatMember 로 매핑한다`() {
        val body = """
            [{"publicId":"uuid-me","nickname":"스모크일","imageUrl":null,"active":true},
             {"publicId":"uuid-other","nickname":"스모크이","imageUrl":"http://img","active":false}]
        """.trimIndent()

        val decoded = json.decodeFromString<List<ChatMemberResponse>>(body).map { it.toChatMember(UUID_ME) }

        assertEquals("서버가 안 주는 userId 가 채워졌다", listOf(null, null), decoded.map { it.userId })
        assertTrue(decoded[0].isMe)
        assertEquals("스모크일", decoded[0].nickname)
        assertFalse(decoded[1].isMe)
        assertFalse(decoded[1].active)
        assertEquals("http://img", decoded[1].imageUrl)
    }

    // 로그아웃 상태(myUuid=null)에서 publicId 가 빈 참여자를 만나면 "빈 값끼리 같다"로
    // 남이 나로 둔갑할 수 있다 — 그 구멍을 막는다.
    @Test
    fun `publicId 가 비면 누구도 내가 되지 않는다`() {
        assertFalse(ChatMemberResponse(publicId = "").toChatMember(null).isMe)
        assertFalse(ChatMemberResponse(publicId = "").toChatMember(UUID_ME).isMe)
    }

    // 구버전 서버(userId 만 주고 publicId 는 없음) 응답도 파싱은 깨지지 않아야 한다.
    // 다만 발신자를 알 수 없으니 내 메시지 판정은 포기한다 — 학습 폴백은 삭제됐다.
    @Test
    fun `userId 만 오는 구버전 응답은 파싱되되 발신자를 알 수 없다`() {
        val body = """
            {"id":20,"chatRoomId":5,"userId":3,"content":"안녕","type":"TEXT","deleted":false}
        """.trimIndent()

        val decoded = json.decodeFromString<ChatMessageResponse>(body)
        val message = decoded.toChatMessage(noDictionary)

        assertEquals(3L, decoded.userId)
        assertNull(message.senderPublicId)
        assertFalse(message.isMine)
    }
}

private fun message(id: Long, publicId: String) = ChatMessageResponse(
    id = id,
    chatRoomId = 1,
    publicId = publicId,
    content = "내용",
    type = "TEXT",
)

private fun member(
    publicId: String,
    nickname: String,
    active: Boolean = true,
) = ChatMemberResponse(
    publicId = publicId,
    nickname = nickname,
    imageUrl = null,
    active = active,
).toChatMember(UUID_ME)

private const val UUID_ME = "uuid-me"
private const val UUID_OTHER = "uuid-other"
