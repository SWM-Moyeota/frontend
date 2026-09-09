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
 * 이 저장소의 핵심 책임은 조회가 아니라 **"이 메시지가 내 것인가" 판정**이다.
 * 서버가 그 정보를 주지 않아 저장소가 학습으로 메우고 있으므로, 학습의 경계(학습 전/후,
 * publicId 우선, 계정 변경)를 테스트가 지킨다.
 */
class RemoteChatRepositoryTest {

    @Test
    fun `학습 전에는 내 과거 메시지도 상대 메시지로 온다`() = runBlocking {
        val api = FakeChatApi(history = listOf(message(id = 1, userId = 7)))

        val page = RemoteChatRepository(api, FakeChatSession()).getMessages(chatRoomId = 5)

        assertFalse(page.messages.single().isMine)
    }

    /**
     * 전송 응답의 userId 가 곧 내 내부 PK 다. 이걸 놓치면 세션 내내 내 말풍선이 상대 쪽에 남는다.
     * 방금 보낸 메시지 자신부터 참이어야 한다 — 화면이 전송 결과를 그대로 목록에 붙이기 때문이다.
     */
    @Test
    fun `전송 응답으로 내 내부 id 를 학습하면 같은 발신자 메시지가 내 것이 된다`() = runBlocking {
        val api = FakeChatApi(history = listOf(message(id = 1, userId = 7), message(id = 2, userId = 9)))
        val repository = RemoteChatRepository(api, FakeChatSession())

        val sent = repository.sendMessage(chatRoomId = 5, content = "안녕하세요")
        val page = repository.getMessages(chatRoomId = 5)

        assertTrue("방금 보낸 메시지가 내 것으로 오지 않았다", sent.isMine)
        assertEquals(listOf(true, false), page.messages.map { it.isMine })
    }

    @Test
    fun `전송 본문에는 내용만 담고 사용자 id 는 싣지 않는다`() = runBlocking {
        val api = FakeChatApi()

        RemoteChatRepository(api, FakeChatSession()).sendMessage(chatRoomId = 5, content = "안녕")

        assertEquals(SendMessageRequestDto("안녕"), api.sentBody)
        assertEquals(5L, api.sentRoomId)
    }

    /**
     * 서버가 senderPublicId 를 추가하면 학습 없이 첫 진입부터 정확해져야 한다 —
     * 이 케이스가 통과하는 한 백엔드 배포만으로 인터림 전략의 한계가 사라진다.
     */
    @Test
    fun `senderPublicId 가 오면 학습 없이도 세션 uuid 로 판정한다`() = runBlocking {
        val api = FakeChatApi(
            history = listOf(
                message(id = 1, userId = 999, senderPublicId = UUID_ME, senderNickname = "성윤"),
                message(id = 2, userId = 7, senderPublicId = "uuid-other", senderNickname = "동승자A"),
            ),
        )

        val page = RemoteChatRepository(api, FakeChatSession()).getMessages(chatRoomId = 5)

        assertEquals(listOf(true, false), page.messages.map { it.isMine })
        assertEquals(listOf("성윤", "동승자A"), page.messages.map { it.senderName })
    }

    /**
     * 로그아웃하면 학습값은 남의 것이 된다. 안 버리면 재로그인한 **다른 계정**의 화면에서
     * 이전 사용자의 메시지가 내 말풍선으로 뜬다 — 계정 전환 시 가장 눈에 띄는 오염이다.
     */
    @Test
    fun `로그아웃하면 학습한 내부 id 를 버린다`() = runBlocking {
        val api = FakeChatApi(history = listOf(message(id = 1, userId = 7)))
        val session = FakeChatSession()
        val repository = RemoteChatRepository(api, session)

        repository.sendMessage(chatRoomId = 5, content = "안녕")
        assertTrue(repository.getMessages(chatRoomId = 5).messages.single().isMine)

        session.state.value = AuthState.Unauthenticated
        assertFalse(repository.getMessages(chatRoomId = 5).messages.single().isMine)

        // 다른 계정으로 다시 로그인해도 되살아나면 안 된다.
        session.state.value = AuthState.Authenticated("uuid-other")
        assertFalse(repository.getMessages(chatRoomId = 5).messages.single().isMine)
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

    // 여기서부터가 참여자 목록 연동의 본론이다.
    // 학습은 "이번 세션에 내가 한 번 보냈다"가 전제라 재진입하면 다시 무너졌다.
    // 참여자 목록은 그 전제 없이 첫 화면부터 정답을 준다.
    @Test
    fun `참여자 목록으로 학습 없이 첫 진입부터 내 메시지를 가린다`() = runBlocking {
        val api = FakeChatApi(
            history = listOf(message(id = 1, userId = MY_INTERNAL_ID), message(id = 2, userId = 9)),
            members = listOf(
                memberDto(userId = MY_INTERNAL_ID, publicId = UUID_ME, nickname = "성윤"),
                memberDto(userId = 9, publicId = "uuid-other", nickname = "자동에이"),
            ),
        )

        val page = RemoteChatRepository(api, FakeChatSession()).getMessages(chatRoomId = 5)

        assertEquals(listOf(true, false), page.messages.map { it.isMine })
        assertEquals(listOf("성윤", "자동에이"), page.messages.map { it.senderName })
        assertEquals(1, api.memberCalls)
    }

    // 나간 사람도 active=false 로 목록에 남는다 — 이름이 사라지면 대화가 "동승자"로 뭉개진다.
    @Test
    fun `방을 나간 참여자의 메시지도 이름을 유지한다`() = runBlocking {
        val api = FakeChatApi(
            history = listOf(message(id = 1, userId = 9)),
            members = listOf(memberDto(userId = 9, publicId = "uuid-gone", nickname = "먼저내림", active = false)),
        )

        val page = RemoteChatRepository(api, FakeChatSession()).getMessages(chatRoomId = 5)

        assertEquals("먼저내림", page.messages.single().senderName)
    }

    // 참여자 목록은 표시를 좋게 할 뿐이다. 이것 때문에 채팅방이 안 열리면 안 된다.
    @Test
    fun `참여자 조회에 실패해도 메시지는 그대로 오고 학습 폴백이 남는다`() = runBlocking {
        val api = FakeChatApi(
            history = listOf(message(id = 1, userId = MY_INTERNAL_ID)),
            membersFailure = IllegalStateException("참여자 조회 실패"),
        )
        val repository = RemoteChatRepository(api, FakeChatSession())

        val before = repository.getMessages(chatRoomId = 5)
        assertFalse(before.messages.single().isMine)
        assertNull(before.messages.single().senderName)

        repository.sendMessage(chatRoomId = 5, content = "안녕")
        assertTrue(repository.getMessages(chatRoomId = 5).messages.single().isMine)
    }

    // 패치 전 서버는 userId 를 안 준다 — 메시지와 이을 키가 없으니 학습으로 되돌아가야 한다.
    @Test
    fun `참여자에 userId 가 없으면 학습 폴백으로 판정한다`() = runBlocking {
        val api = FakeChatApi(
            history = listOf(message(id = 1, userId = MY_INTERNAL_ID)),
            members = listOf(memberDto(userId = null, publicId = UUID_ME, nickname = "성윤")),
        )
        val repository = RemoteChatRepository(api, FakeChatSession())

        assertFalse(repository.getMessages(chatRoomId = 5).messages.single().isMine)

        repository.sendMessage(chatRoomId = 5, content = "안녕")
        assertTrue(repository.getMessages(chatRoomId = 5).messages.single().isMine)
    }

    // 그 서버에서 폴링마다 참여자 목록을 다시 부르면 트래픽이 두 배가 된다.
    // 못 찾은 발신자는 한 번만 묻고 적어 둔다.
    @Test
    fun `못 찾은 발신자를 폴링마다 다시 조회하지 않는다`() = runBlocking {
        val api = FakeChatApi(
            history = listOf(message(id = 1, userId = 9)),
            members = listOf(memberDto(userId = null, publicId = "uuid-other", nickname = "자동에이")),
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
            history = listOf(message(id = 1, userId = MY_INTERNAL_ID)),
            members = listOf(memberDto(userId = MY_INTERNAL_ID, publicId = UUID_ME, nickname = "성윤")),
        )
        val repository = RemoteChatRepository(api, FakeChatSession())
        repository.getMessages(chatRoomId = 5)

        // 중간 합류자가 말을 걸었다.
        api.members = api.members + memberDto(userId = 9, publicId = "uuid-other", nickname = "늦게탄사람")
        api.afterMessages = listOf(message(id = 2, userId = 9))

        val fresh = repository.getMessagesAfter(chatRoomId = 5, cursor = 1)
        assertEquals("늦게탄사람", fresh.messages.single().senderName)
        assertEquals(2, api.memberCalls)

        // 이제 사전에 있으니 다음 폴링은 조용해야 한다.
        repository.getMessagesAfter(chatRoomId = 5, cursor = 2)
        assertEquals(2, api.memberCalls)
    }

    // 계정이 바뀌면 참여자 캐시는 남의 것이다. 안 버리면 이전 사용자의 메시지가 내 말풍선으로 뜬다.
    @Test
    fun `계정이 바뀌면 참여자 캐시를 버린다`() = runBlocking {
        val api = FakeChatApi(
            history = listOf(message(id = 1, userId = MY_INTERNAL_ID)),
            members = listOf(memberDto(userId = MY_INTERNAL_ID, publicId = UUID_ME, nickname = "성윤")),
        )
        val session = FakeChatSession()
        val repository = RemoteChatRepository(api, session)
        assertTrue(repository.getMessages(chatRoomId = 5).messages.single().isMine)

        session.state.value = AuthState.Unauthenticated
        // 로그아웃 상태에서는 캐시를 쓰지 않는다(참여자 조회도 401 이 정상이라 실패로 둔다).
        assertFalse(repository.getMessages(chatRoomId = 5).messages.single().isMine)

        // 다른 계정으로 다시 로그인하면 그 계정 기준으로 다시 받아 판정한다.
        session.state.value = AuthState.Authenticated("uuid-other")
        assertFalse(repository.getMessages(chatRoomId = 5).messages.single().isMine)
    }

    @Test
    fun `참여자 목록은 userId 가 없는 참여자까지 그대로 돌려준다`() = runBlocking {
        val api = FakeChatApi(
            members = listOf(
                memberDto(userId = MY_INTERNAL_ID, publicId = UUID_ME, nickname = "성윤"),
                memberDto(userId = null, publicId = "uuid-other", nickname = "자동에이"),
            ),
        )

        val members = RemoteChatRepository(api, FakeChatSession()).getChatRoomMembers(chatRoomId = 5)

        assertEquals(listOf("성윤", "자동에이"), members.map { it.nickname })
        assertEquals(listOf(true, false), members.map { it.isMe })
    }

    // 목록을 이미 받아 왔으면 이어지는 메시지 조회가 그 캐시를 그대로 쓴다(중복 호출 금지).
    @Test
    fun `참여자를 조회해 두면 다음 메시지 조회가 캐시를 쓴다`() = runBlocking {
        val api = FakeChatApi(
            history = listOf(message(id = 1, userId = 9)),
            members = listOf(memberDto(userId = 9, publicId = "uuid-other", nickname = "자동에이")),
        )
        val repository = RemoteChatRepository(api, FakeChatSession())
        repository.getChatRoomMembers(chatRoomId = 5)

        val page = repository.getMessagesAfter(chatRoomId = 5, cursor = 0)

        assertEquals("자동에이", page.messages.single().senderName)
        assertEquals(1, api.memberCalls)
    }

    private fun httpException(code: Int, body: String) = HttpException(
        Response.error<Any>(code, body.toResponseBody("application/json".toMediaType())),
    )
}

private const val UUID_ME = "uuid-me"
private const val MY_INTERNAL_ID = 7L

private fun memberDto(
    userId: Long?,
    publicId: String,
    nickname: String,
    active: Boolean = true,
) = ChatMemberResponse(
    userId = userId,
    publicId = publicId,
    nickname = nickname,
    imageUrl = null,
    active = active,
)

private fun message(
    id: Long,
    userId: Long,
    senderPublicId: String? = null,
    senderNickname: String? = null,
) = ChatMessageResponse(
    id = id,
    chatRoomId = 5,
    userId = userId,
    content = "본문 $id",
    type = "TEXT",
    createdAt = "2026-09-07T09:0$id:00Z",
    deleted = false,
    senderPublicId = senderPublicId,
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
    // 참여자 목록은 테스트 도중 바뀔 수 있다(중간 합류자). var 로 두고 갈아 끼운다.
    var members: List<ChatMemberResponse> = emptyList(),
    private val membersFailure: Throwable? = null,
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

    // 서버는 전송자의 내부 PK 를 응답에 담아 준다 — 앱이 "나"를 학습하는 유일한 창구다.
    override suspend fun sendMessage(chatRoomId: Long, request: SendMessageRequestDto): ChatMessageResponse {
        failIfNeeded()
        sentRoomId = chatRoomId
        sentBody = request
        return message(id = 99, userId = MY_INTERNAL_ID).copy(content = request.content)
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
