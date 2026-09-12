package com.moyeota.data.repository

import com.moyeota.data.remote.ChatApi
import com.moyeota.data.remote.dto.ChatMemberResponse
import com.moyeota.data.remote.dto.ChatMessageResponse
import com.moyeota.data.remote.dto.ChatMessageSliceResponse
import com.moyeota.data.remote.dto.ChatRoomResponse
import com.moyeota.data.remote.dto.ChatRoomUserResponse
import com.moyeota.data.remote.dto.CreateChatRoomRequestDto
import com.moyeota.data.remote.dto.SendMessageRequestDto
import com.moyeota.domain.model.AuthState
import com.moyeota.domain.model.ChatException
import com.moyeota.domain.session.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * 서버가 메시지에 발신자 `publicId` 를 실어 주면서 이 저장소의 판정 책임은 **이름 하나**로 줄었다
 * (내 메시지 여부는 매퍼의 문자열 비교로 끝난다 — `ChatMappersTest`).
 * 그래서 여기서 지키는 건 두 가지다: 참여자 사전이 없거나 실패해도 **isMine 이 흔들리지 않을 것**,
 * 그리고 사전 조회가 필요 이상으로 반복되지 않을 것.
 */
class RemoteChatRepositoryTest {

    /**
     * 예전에는 "이번 세션에 한 번 보내기 전"까지 내 메시지가 상대 쪽에 있었다(학습 폴백의 대가).
     * 그 대가는 사라졌다 — 방에 처음 들어간 첫 프레임부터 정확해야 한다.
     */
    @Test
    fun `참여자 목록 없이 첫 진입부터 내 메시지를 가린다`() = runBlocking {
        val api = FakeChatApi(history = listOf(message(id = 1, publicId = UUID_ME), message(id = 2, publicId = UUID_OTHER)))

        val page = RemoteChatRepository(api, FakeChatSession()).getMessages(chatRoomId = 5)

        assertEquals(listOf(true, false), page.messages.map { it.isMine })
    }

    @Test
    fun `방금 보낸 메시지는 응답만으로 내 것이 된다`() = runBlocking {
        val api = FakeChatApi()

        val sent = RemoteChatRepository(api, FakeChatSession()).sendMessage(chatRoomId = 5, content = "안녕하세요")

        assertTrue("전송 응답의 publicId 를 읽지 못했다", sent.isMine)
        assertEquals("안녕하세요", sent.content)
    }

    @Test
    fun `전송 본문에는 내용만 담고 사용자 id 는 싣지 않는다`() = runBlocking {
        val api = FakeChatApi()

        RemoteChatRepository(api, FakeChatSession()).sendMessage(chatRoomId = 5, content = "안녕")

        assertEquals(SendMessageRequestDto("안녕"), api.sentBody)
        assertEquals(5L, api.sentRoomId)
    }

    @Test
    fun `참여자 목록으로 보낸 사람 이름을 채운다`() = runBlocking {
        val api = FakeChatApi(
            history = listOf(message(id = 1, publicId = UUID_ME), message(id = 2, publicId = UUID_OTHER)),
            members = listOf(
                memberDto(publicId = UUID_ME, nickname = "스모크일"),
                memberDto(publicId = UUID_OTHER, nickname = "스모크이"),
            ),
        )

        val page = RemoteChatRepository(api, FakeChatSession()).getMessages(chatRoomId = 5)

        assertEquals(listOf("스모크일", "스모크이"), page.messages.map { it.senderName })
        assertEquals(1, api.memberCalls)
    }

    // 나간 사람도 active=false 로 목록에 남는다 — 이름이 사라지면 대화가 "동승자"로 뭉개진다.
    @Test
    fun `방을 나간 참여자의 메시지도 이름을 유지한다`() = runBlocking {
        val api = FakeChatApi(
            history = listOf(message(id = 1, publicId = "uuid-gone")),
            members = listOf(memberDto(publicId = "uuid-gone", nickname = "먼저내림", active = false)),
        )

        val page = RemoteChatRepository(api, FakeChatSession()).getMessages(chatRoomId = 5)

        assertEquals("먼저내림", page.messages.single().senderName)
    }

    /**
     * 참여자 목록은 이름을 위한 것뿐이다. 조회가 실패해도 채팅방은 열려야 하고,
     * **무엇보다 내 말풍선 위치가 흔들리면 안 된다** — 예전 구조에서는 여기서 판정이 무너졌다.
     */
    @Test
    fun `참여자 조회에 실패해도 메시지와 내 메시지 판정은 그대로다`() = runBlocking {
        val api = FakeChatApi(
            history = listOf(message(id = 1, publicId = UUID_ME)),
            membersFailure = IllegalStateException("참여자 조회 실패"),
        )

        val page = RemoteChatRepository(api, FakeChatSession()).getMessages(chatRoomId = 5)

        assertTrue("사전이 없다고 내 메시지를 놓쳤다", page.messages.single().isMine)
        assertNull(page.messages.single().senderName)
    }

    // 사전에 없는 발신자를 폴링마다 다시 물으면 트래픽이 두 배가 된다. 한 번만 묻고 적어 둔다.
    @Test
    fun `못 찾은 발신자를 폴링마다 다시 조회하지 않는다`() = runBlocking {
        val api = FakeChatApi(
            history = listOf(message(id = 1, publicId = "uuid-ghost")),
            members = listOf(memberDto(publicId = UUID_OTHER, nickname = "자동에이")),
        )
        val repository = RemoteChatRepository(api, FakeChatSession())

        repository.getMessages(chatRoomId = 5)
        val afterInitialLoad = api.memberCalls

        repository.getMessagesAfter(chatRoomId = 5, cursor = 1)
        repository.getMessagesAfter(chatRoomId = 5, cursor = 1)

        assertEquals("초기 로드에서 한 번만 불러야 한다", 1, afterInitialLoad)
        assertEquals("모르는 발신자를 폴링마다 다시 조회하고 있다", 1, api.memberCalls)
    }

    // 방에 뒤늦게 합류한 사람의 첫 메시지. 사전에 없는 발신자가 나오면 그 로드에서 한 번 다시 받는다.
    @Test
    fun `새 참여자가 나타나면 한 번만 재조회해 이름을 채운다`() = runBlocking {
        val api = FakeChatApi(
            history = listOf(message(id = 1, publicId = UUID_ME)),
            members = listOf(memberDto(publicId = UUID_ME, nickname = "스모크일")),
        )
        val repository = RemoteChatRepository(api, FakeChatSession())
        repository.getMessages(chatRoomId = 5)

        // 중간 합류자가 말을 걸었다.
        api.members = api.members + memberDto(publicId = UUID_OTHER, nickname = "늦게탄사람")
        api.afterMessages = listOf(message(id = 2, publicId = UUID_OTHER))

        val fresh = repository.getMessagesAfter(chatRoomId = 5, cursor = 1)
        assertEquals("늦게탄사람", fresh.messages.single().senderName)
        assertEquals(2, api.memberCalls)

        // 이제 사전에 있으니 다음 폴링은 조용해야 한다.
        repository.getMessagesAfter(chatRoomId = 5, cursor = 2)
        assertEquals(2, api.memberCalls)
    }

    /**
     * 계정이 바뀌면 참여자 캐시는 남의 것이다. 캐시를 버리는지 확인하려면 **캐시에만 있는 이름**이
     * 사라지는지 봐야 한다(isMine 은 이제 캐시와 무관하게 세션 uuid 로 판정되므로 근거가 못 된다).
     */
    @Test
    fun `계정이 바뀌면 참여자 캐시를 버린다`() = runBlocking {
        val api = FakeChatApi(
            history = listOf(message(id = 1, publicId = UUID_ME)),
            members = listOf(memberDto(publicId = UUID_ME, nickname = "스모크일")),
        )
        val session = FakeChatSession()
        val repository = RemoteChatRepository(api, session)

        val first = repository.getMessages(chatRoomId = 5).messages.single()
        assertTrue(first.isMine)
        assertEquals("스모크일", first.senderName)

        // 로그아웃: 캐시를 쓰지 않고, 그 계정의 메시지도 더는 내 것이 아니다.
        session.state.value = AuthState.Unauthenticated
        api.membersFailure = IllegalStateException("401")
        val loggedOut = repository.getMessages(chatRoomId = 5).messages.single()
        assertFalse(loggedOut.isMine)
        assertNull("로그아웃 후에도 이전 계정의 참여자 캐시를 썼다", loggedOut.senderName)

        // 다른 계정으로 로그인해도 앞 사람의 캐시가 되살아나면 안 된다.
        session.state.value = AuthState.Authenticated(UUID_OTHER)
        val switched = repository.getMessages(chatRoomId = 5).messages.single()
        assertFalse(switched.isMine)
        assertNull(switched.senderName)
    }

    @Test
    fun `참여자 목록을 그대로 돌려주고 나를 표시한다`() = runBlocking {
        val api = FakeChatApi(
            members = listOf(
                memberDto(publicId = UUID_ME, nickname = "스모크일"),
                memberDto(publicId = UUID_OTHER, nickname = "스모크이"),
            ),
        )

        val members = RemoteChatRepository(api, FakeChatSession()).getChatRoomMembers(chatRoomId = 5)

        assertEquals(listOf("스모크일", "스모크이"), members.map { it.nickname })
        assertEquals(listOf(true, false), members.map { it.isMe })
        assertEquals("서버가 안 주는 userId 가 채워졌다", listOf(null, null), members.map { it.userId })
    }

    // 목록을 이미 받아 왔으면 이어지는 메시지 조회가 그 캐시를 그대로 쓴다(중복 호출 금지).
    @Test
    fun `참여자를 조회해 두면 다음 메시지 조회가 캐시를 쓴다`() = runBlocking {
        val api = FakeChatApi(
            history = listOf(message(id = 1, publicId = UUID_OTHER)),
            members = listOf(memberDto(publicId = UUID_OTHER, nickname = "자동에이")),
        )
        val repository = RemoteChatRepository(api, FakeChatSession())
        repository.getChatRoomMembers(chatRoomId = 5)

        val page = repository.getMessagesAfter(chatRoomId = 5, cursor = 0)

        assertEquals("자동에이", page.messages.single().senderName)
        assertEquals(1, api.memberCalls)
    }

    @Test
    fun `검색은 keyword 와 페이징 파라미터를 그대로 넘긴다`() = runBlocking {
        val api = FakeChatApi()

        RemoteChatRepository(api, FakeChatSession())
            .searchMessages(chatRoomId = 5, keyword = "약속", cursor = 42, size = 10)

        assertEquals(listOf(5L, "약속", 42L, 10), api.searchCall)
    }

    @Test
    fun `조회 기본값은 커서 없음과 서버 기본 크기 30 이다`() = runBlocking {
        val api = FakeChatApi()

        RemoteChatRepository(api, FakeChatSession()).getMessages(chatRoomId = 5)

        assertNull(api.messagesCursor)
        assertEquals(30, api.messagesSize)
    }

    /** 목록은 membership 만 오므로 방 상세를 합쳐 준다. 상세가 깨진 방은 목록 전체를 죽이지 않고 빠진다. */
    @Test
    fun `상세 조회에 실패한 방은 목록에서 제외한다`() = runBlocking {
        val api = FakeChatApi(memberships = listOf(1L, 2L), brokenRoomIds = setOf(2L))

        val rooms = RemoteChatRepository(api, FakeChatSession()).getMyChatRooms()

        assertEquals(listOf(1L), rooms.map { it.room.id })
    }

    /** 음소거는 같은 경로의 POST/DELETE 다 — 불리언 하나가 어느 메서드로 가는지가 계약이다. */
    @Test
    fun `알림 끄기는 mute, 켜기는 unmute 로 간다`() = runBlocking {
        val api = FakeChatApi()
        val repository = RemoteChatRepository(api, FakeChatSession())

        repository.setNotificationMuted(5, muted = true)
        repository.setNotificationMuted(5, muted = false)

        assertEquals(listOf(5L to true, 5L to false), api.muteCalls)
    }

    @Test
    fun `음소거 실패도 ChatException 으로 좁혀진다`() = runBlocking {
        val api = FakeChatApi(
            failure = httpException(403, """{"code":"CHAT_NOT_PARTICIPANT","message":"채팅방 참여자가 아닙니다."}"""),
        )
        val error = runCatching { RemoteChatRepository(api, FakeChatSession()).setNotificationMuted(5, true) }
            .exceptionOrNull()
        assertTrue((error as? ChatException)?.isNotParticipant == true)
    }

    /**
     * 채팅 실패 본문은 `{code, message}` 이고 code 가 enum 이름이다. 이걸 도메인 예외로 좁혀 두지 않으면
     * 화면이 HttpException 을 직접 뒤져야 하고, 403 두 종류(참여자 아님/남의 메시지)가 구분되지 않는다.
     */
    @Test
    fun `403 CHAT_NOT_PARTICIPANT 는 참여자 아님 예외로 좁혀진다`() = runBlocking {
        val api = FakeChatApi(
            failure = httpException(403, """{"code":"CHAT_NOT_PARTICIPANT","message":"채팅방 참여자가 아닙니다."}"""),
        )

        val error = runCatching { RemoteChatRepository(api, FakeChatSession()).getMessages(5) }
            .exceptionOrNull()

        val chat = error as? ChatException ?: error("ChatException 이 아니다: $error")
        assertTrue(chat.isNotParticipant)
        assertFalse(chat.isRoomClosed)
        assertEquals("채팅방 참여자가 아닙니다.", chat.serverMessage)
    }

    @Test
    fun `409 CHAT_ROOM_CLOSED 는 종료된 방 예외로 좁혀진다`() = runBlocking {
        val api = FakeChatApi(
            failure = httpException(409, """{"code":"CHAT_ROOM_CLOSED","message":"종료된 채팅방입니다."}"""),
        )

        val error = runCatching { RemoteChatRepository(api, FakeChatSession()).sendMessage(5, "안녕") }
            .exceptionOrNull()

        val chat = error as? ChatException ?: error("ChatException 이 아니다: $error")
        assertTrue(chat.isRoomClosed)
        assertFalse(chat.isNotParticipant)
    }

    /** 스프링이 직접 만드는 404 처럼 본문에 code 가 없을 때도 상태 코드로 최소한의 분기는 서야 한다. */
    @Test
    fun `code 없는 404 도 방 없음으로 읽힌다`() = runBlocking {
        val api = FakeChatApi(failure = httpException(404, ""))

        val error = runCatching { RemoteChatRepository(api, FakeChatSession()).getChatRoom(5) }
            .exceptionOrNull()

        val chat = error as? ChatException ?: error("ChatException 이 아니다: $error")
        assertNull(chat.code)
        assertTrue(chat.isRoomNotFound)
    }

    private fun httpException(code: Int, body: String) = HttpException(
        Response.error<Any>(code, body.toResponseBody("application/json".toMediaType())),
    )
}

private const val UUID_ME = "uuid-me"
private const val UUID_OTHER = "uuid-other"

private fun memberDto(
    publicId: String,
    nickname: String,
    active: Boolean = true,
) = ChatMemberResponse(
    publicId = publicId,
    nickname = nickname,
    imageUrl = null,
    active = active,
)

// 배포 서버 실측 shape: userId 없이 publicId 로 발신자를 밝힌다.
private fun message(
    id: Long,
    publicId: String,
    senderNickname: String? = null,
) = ChatMessageResponse(
    id = id,
    chatRoomId = 5,
    publicId = publicId,
    content = "본문 $id",
    type = "TEXT",
    createdAt = "2026-09-07T09:0$id:00Z",
    deleted = false,
    senderNickname = senderNickname,
)

private class FakeChatSession(initial: AuthState = AuthState.Authenticated(UUID_ME)) : UserSession {
    val state = MutableStateFlow(initial)
    override val authState: StateFlow<AuthState> = state.asStateFlow()
}

/**
 * 헤더 검증은 여기서 하지 않는다 — Retrofit 애노테이션은 이 계층을 거치지 않으므로
 * `X-User-Id` 가 되살아나는 건 `AuthenticatedPathContractTest` 가 리플렉션으로 잡는다.
 * 이 페이크는 저장소가 **무엇을 넘기는지**만 기록한다.
 */
private class FakeChatApi(
    private val history: List<ChatMessageResponse> = emptyList(),
    private val memberships: List<Long> = emptyList(),
    private val brokenRoomIds: Set<Long> = emptySet(),
    private val failure: Throwable? = null,
    // 참여자 목록은 테스트 도중 바뀔 수 있다(중간 합류자·로그아웃). var 로 두고 갈아 끼운다.
    var members: List<ChatMemberResponse> = emptyList(),
    var membersFailure: Throwable? = null,
) : ChatApi {

    // 재조회가 정말 "한 번만" 일어나는지 세려면 호출 횟수가 필요하다.
    var memberCalls = 0
        private set
    var afterMessages: List<ChatMessageResponse>? = null

    var sentRoomId: Long? = null
        private set
    var sentBody: SendMessageRequestDto? = null
        private set
    var messagesCursor: Long? = null
        private set
    var messagesSize: Int? = null
        private set
    var searchCall: List<Any?> = emptyList()
        private set

    private fun failIfNeeded() = failure?.let { throw it }

    override suspend fun getRoom(chatRoomId: Long): ChatRoomResponse {
        failIfNeeded()
        if (chatRoomId in brokenRoomIds) throw IllegalStateException("방 상세 조회 실패")
        return ChatRoomResponse(
            id = chatRoomId,
            partyId = 100 + chatRoomId,
            departure = "서울시청",
            destination = "강남역",
            createdAt = "2026-09-07T08:00:00Z",
            status = "ACTIVE",
        )
    }

    override suspend fun createRoom(request: CreateChatRoomRequestDto): ChatRoomResponse {
        failIfNeeded()
        return getRoom(1)
    }

    override suspend fun deleteRoom(chatRoomId: Long) = failIfNeeded() ?: Unit

    override suspend fun getMyRooms(): List<ChatRoomUserResponse> {
        failIfNeeded()
        return memberships.map { ChatRoomUserResponse(chatRoomId = it, lastReadMessageId = null) }
    }

    override suspend fun joinRoom(chatRoomId: Long) = failIfNeeded() ?: Unit

    override suspend fun leaveRoom(chatRoomId: Long) = failIfNeeded() ?: Unit

    override suspend fun getMembers(chatRoomId: Long): List<ChatMemberResponse> {
        memberCalls++
        membersFailure?.let { throw it }
        return members
    }

    override suspend fun readRoom(chatRoomId: Long, readMessageId: Long) = failIfNeeded() ?: Unit

    val muteCalls = mutableListOf<Pair<Long, Boolean>>()
    override suspend fun muteNotification(chatRoomId: Long) { failIfNeeded(); muteCalls += chatRoomId to true }
    override suspend fun unmuteNotification(chatRoomId: Long) { failIfNeeded(); muteCalls += chatRoomId to false }

    override suspend fun getMessages(chatRoomId: Long, cursor: Long?, size: Int): ChatMessageSliceResponse {
        failIfNeeded()
        messagesCursor = cursor
        messagesSize = size
        return ChatMessageSliceResponse(messages = history, nextCursor = null, hasNext = false)
    }

    override suspend fun getMessagesAfter(chatRoomId: Long, cursor: Long, size: Int): ChatMessageSliceResponse {
        failIfNeeded()
        return ChatMessageSliceResponse(messages = afterMessages ?: history, nextCursor = null, hasNext = false)
    }

    // 서버는 전송 응답에도 발신자 publicId 를 담아 준다 — 조회와 같은 shape 이다.
    override suspend fun sendMessage(chatRoomId: Long, request: SendMessageRequestDto): ChatMessageResponse {
        failIfNeeded()
        sentRoomId = chatRoomId
        sentBody = request
        return message(id = 99, publicId = UUID_ME).copy(content = request.content)
    }

    override suspend fun deleteMessage(chatRoomId: Long, messageId: Long) = failIfNeeded() ?: Unit

    override suspend fun searchMessages(
        chatRoomId: Long,
        keyword: String,
        cursor: Long?,
        size: Int,
    ): ChatMessageSliceResponse {
        failIfNeeded()
        searchCall = listOf(chatRoomId, keyword, cursor, size)
        return ChatMessageSliceResponse(messages = history, nextCursor = null, hasNext = false)
    }
}
