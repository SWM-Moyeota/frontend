package com.moyeota.data.repository

import com.moyeota.data.local.ActivePartyStorage
import com.moyeota.data.local.RememberedParty
import com.moyeota.domain.model.AssignedDriver
import com.moyeota.domain.model.AuthState
import com.moyeota.domain.model.ChatMember
import com.moyeota.domain.model.ChatMessage
import com.moyeota.domain.model.ChatMessagePage
import com.moyeota.domain.model.ChatRoom
import com.moyeota.domain.model.ChatRoomMembership
import com.moyeota.domain.model.ChatRoomStatus
import com.moyeota.domain.model.MyChatRoom
import com.moyeota.domain.model.NewParty
import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RideStatus
import com.moyeota.domain.model.RouteEstimate
import com.moyeota.domain.model.User
import com.moyeota.domain.repository.ChatRepository
import com.moyeota.domain.repository.RideRepository
import com.moyeota.domain.session.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * 이 저장소가 지켜야 하는 건 "찾았다/못 찾았다" 두 값이 아니라 **세 값**이다 —
 * 진행 중이다 / 확실히 아니다(기억 삭제) / 모르겠다(기억 보존).
 * 세 번째를 두 번째로 접는 순간 오프라인에서 앱을 켠 사용자의 복귀 경로가 영구히 사라지므로,
 * 아래 테스트의 절반은 "기억이 남아 있는가"를 본다.
 */
class RemoteActivePartyRepositoryTest {

    @Test
    fun `기억된 방이 진행 중이면 상세 1회로 돌려준다`() = runBlocking {
        val rides = FakeRideRepository(details = mapOf(10L to ride(id = 10, status = RideStatus.RECRUITING)))
        val chats = FakeChatRepository()
        val storage = FakeActivePartyStorage(RememberedParty("10", MY_UUID))

        val resolved = repository(rides, chats, storage).resolve()

        assertEquals("10", resolved?.id)
        assertEquals(listOf(10L), rides.detailCalls)
        assertEquals("기억이 맞았는데 채팅방 목록까지 불렀다", 0, chats.myRoomsCalls)
    }

    /** 정원이 막 찬 방(서버 COMPLETED → 도메인 MATCHED)도 진행 중이다. 여기서 놓치면 매칭 순간을 잃는다. */
    @Test
    fun `정원 충족 상태 MATCHED 도 진행 중으로 본다`() = runBlocking {
        val rides = FakeRideRepository(details = mapOf(10L to ride(id = 10, status = RideStatus.MATCHED)))
        val storage = FakeActivePartyStorage(RememberedParty("10", MY_UUID))

        val resolved = repository(rides, FakeChatRepository(), storage).resolve()

        assertNotNull(resolved)
    }

    @Test
    fun `기억된 방이 끝났으면 기억을 지우고 null 을 돌려준다`() = runBlocking {
        val rides = FakeRideRepository(details = mapOf(10L to ride(id = 10, status = RideStatus.COMPLETED)))
        val storage = FakeActivePartyStorage(RememberedParty("10", MY_UUID))

        val resolved = repository(rides, FakeChatRepository(), storage).resolve()

        assertNull(resolved)
        assertNull("끝난 방의 기억이 남았다", storage.load())
    }

    @Test
    fun `방이 사라졌으면(404) 기억을 지운다`() = runBlocking {
        val rides = FakeRideRepository(errors = mapOf(10L to httpError(404)))
        val storage = FakeActivePartyStorage(RememberedParty("10", MY_UUID))

        val resolved = repository(rides, FakeChatRepository(), storage).resolve()

        assertNull(resolved)
        assertNull(storage.load())
    }

    @Test
    fun `내가 멤버가 아닌 방이면 기억을 지운다`() = runBlocking {
        val notMine = ride(id = 10, status = RideStatus.ONGOING, memberUuid = "someone-else")
        val rides = FakeRideRepository(details = mapOf(10L to notMine))
        val storage = FakeActivePartyStorage(RememberedParty("10", MY_UUID))

        val resolved = repository(rides, FakeChatRepository(), storage).resolve()

        assertNull(resolved)
        assertNull(storage.load())
    }

    @Test
    fun `기억이 없으면 채팅방의 partyId 로 찾아 기억한다`() = runBlocking {
        val rides = FakeRideRepository(
            details = mapOf(
                7L to ride(id = 7, status = RideStatus.COMPLETED),
                8L to ride(id = 8, status = RideStatus.ONGOING),
            ),
        )
        val chats = FakeChatRepository(
            rooms = listOf(myChatRoom(roomId = 1, partyId = 7), myChatRoom(roomId = 2, partyId = 8)),
        )
        val storage = FakeActivePartyStorage(remembered = null)

        val resolved = repository(rides, chats, storage).resolve()

        assertEquals("8", resolved?.id)
        assertEquals(RememberedParty("8", MY_UUID), storage.load())
    }

    /** 채팅방이 아무리 많아도 요청은 5개까지. 최신(= id 가 큰) 방부터 본다. */
    @Test
    fun `채팅방 탐색은 최신순 5개까지만 상세를 부른다`() = runBlocking {
        val rides = FakeRideRepository(details = emptyMap())
        val chats = FakeChatRepository(
            rooms = (1L..8L).map { myChatRoom(roomId = it, partyId = it * 10) },
        )

        val resolved = repository(rides, chats, FakeActivePartyStorage(null)).resolve()

        assertNull(resolved)
        assertEquals(listOf(80L, 70L, 60L, 50L, 40L), rides.detailCalls)
    }

    /**
     * 로그아웃 → 다른 계정 로그인. 앞 사람의 방으로 끌고 가면 사고다.
     * 기억은 버리되, 새 계정의 방은 채팅방 목록에서 정상적으로 찾아야 한다.
     */
    @Test
    fun `계정이 바뀌면 이전 계정의 기억을 무시하고 지운다`() = runBlocking {
        val rides = FakeRideRepository(details = mapOf(99L to ride(id = 99, status = RideStatus.ONGOING)))
        val storage = FakeActivePartyStorage(RememberedParty("99", "previous-user-uuid"))

        val resolved = repository(rides, FakeChatRepository(), storage).resolve()

        assertNull(resolved)
        assertNull("남의 계정 기억이 남았다", storage.load())
        assertTrue("남의 계정 방을 조회했다", rides.detailCalls.isEmpty())
    }

    @Test
    fun `네트워크 실패는 예외를 던지지 않고 null 이며 기억을 남긴다`() = runBlocking {
        val rides = FakeRideRepository(errors = mapOf(10L to IOException("timeout")))
        val chats = FakeChatRepository()
        val storage = FakeActivePartyStorage(RememberedParty("10", MY_UUID))

        val resolved = repository(rides, chats, storage).resolve()

        assertNull(resolved)
        assertEquals("신호가 없다고 복귀 경로를 지웠다", RememberedParty("10", MY_UUID), storage.load())
        assertEquals("판정 불가인데 채팅방까지 훑었다", 0, chats.myRoomsCalls)
    }

    @Test
    fun `채팅방 목록 조회가 실패해도 null 로 접는다`() = runBlocking {
        val chats = FakeChatRepository(failing = true)

        val resolved = repository(FakeRideRepository(), chats, FakeActivePartyStorage(null)).resolve()

        assertNull(resolved)
    }

    @Test
    fun `미로그인 상태에서는 기억을 건드리지 않고 null`() = runBlocking {
        val storage = FakeActivePartyStorage(RememberedParty("10", MY_UUID))
        val repository = RemoteActivePartyRepository(
            rideRepository = FakeRideRepository(),
            chatRepository = FakeChatRepository(),
            storage = storage,
            session = FakeSession(uuid = null),
        )

        assertNull(repository.resolve())
        assertNotNull("미로그인은 판정 불가일 뿐인데 기억을 지웠다", storage.load())

        // 미로그인 상태의 remember 는 주인을 적을 수 없어 저장 자체를 하지 않는다.
        repository.clear()
        repository.remember("42")
        assertNull(storage.load())
    }

    private fun repository(
        rides: RideRepository,
        chats: ChatRepository,
        storage: ActivePartyStorage,
    ) = RemoteActivePartyRepository(rides, chats, storage, FakeSession(MY_UUID))

    private companion object {
        const val MY_UUID = "my-uuid"

        fun ride(id: Long, status: RideStatus, memberUuid: String = MY_UUID) = Ride(
            id = id.toString(),
            origin = "성결대",
            destination = "안양역",
            departureLabel = "지금 출발",
            capacity = 4,
            members = listOf(
                User(
                    id = memberUuid,
                    nickname = "나",
                    verifiedLabel = "",
                    rating = 0.0,
                    rideCount = 0,
                    isMe = memberUuid == MY_UUID,
                ),
            ),
            farePerPerson = 2000,
            totalFare = 8000,
            status = status,
        )

        fun myChatRoom(roomId: Long, partyId: Long) = MyChatRoom(
            room = ChatRoom(
                id = roomId,
                partyId = partyId,
                departure = "성결대",
                destination = "안양역",
                createdAt = "2026-09-09T00:00:00Z",
                status = ChatRoomStatus.ACTIVE,
            ),
            membership = ChatRoomMembership(
                chatRoomId = roomId,
                lastReadMessageId = null,
                notificationMuted = false,
                joinedAt = "2026-09-09T00:00:00Z",
            ),
        )

        fun httpError(code: Int) = HttpException(
            Response.error<Unit>(code, "{}".toResponseBody("application/json".toMediaType())),
        )
    }
}

private class FakeSession(private val uuid: String?) : UserSession {
    private val state = MutableStateFlow(
        uuid?.let { AuthState.Authenticated(it) } ?: AuthState.Unauthenticated,
    )
    override val authState: StateFlow<AuthState> = state.asStateFlow()
}

private class FakeActivePartyStorage(remembered: RememberedParty?) : ActivePartyStorage {
    private var value: RememberedParty? = remembered
    override suspend fun load(): RememberedParty? = value
    override suspend fun save(party: RememberedParty) { value = party }
    override suspend fun clear() { value = null }
}

private class FakeRideRepository(
    private val details: Map<Long, Ride> = emptyMap(),
    private val errors: Map<Long, Throwable> = emptyMap(),
) : RideRepository {
    val detailCalls = mutableListOf<Long>()

    override suspend fun getPartyDetail(partyId: Long): Ride {
        detailCalls += partyId
        errors[partyId]?.let { throw it }
        return details[partyId] ?: throw HttpException(
            Response.error<Unit>(404, "{}".toResponseBody("application/json".toMediaType())),
        )
    }

    override fun getNearbyParties(): List<Ride> = emptyList()
    override fun getMyRides(): List<Ride> = emptyList()
    override suspend fun getParties(): List<Ride> = emptyList()
    override suspend fun getPartiesWithin(swLat: Double, swLng: Double, neLat: Double, neLng: Double): List<Ride> =
        emptyList()
    override suspend fun createParty(request: NewParty): Ride = error("쓰이지 않는다")
    override suspend fun joinParty(partyId: Long): Ride = error("쓰이지 않는다")
    override suspend fun leaveParty(partyId: Long) = error("쓰이지 않는다")
    override suspend fun getAssignedDriver(partyId: Long): AssignedDriver = error("쓰이지 않는다")
    override suspend fun reportEmergency(partyId: Long?): Long = error("쓰이지 않는다")
    override suspend fun confirmEmergencyCall(called: Boolean) = error("쓰이지 않는다")
    override suspend fun previewRoute(
        departureLat: Double,
        departureLng: Double,
        destinationLat: Double,
        destinationLng: Double,
    ): RouteEstimate = error("쓰이지 않는다")
}

private class FakeChatRepository(
    private val rooms: List<MyChatRoom> = emptyList(),
    private val failing: Boolean = false,
) : ChatRepository {
    var myRoomsCalls = 0
        private set

    override suspend fun getMyChatRooms(): List<MyChatRoom> {
        myRoomsCalls++
        if (failing) throw IOException("offline")
        return rooms
    }

    override suspend fun getChatRoom(chatRoomId: Long): ChatRoom = error("쓰이지 않는다")
    override suspend fun createChatRoom(partyId: Long, departure: String, destination: String): ChatRoom =
        error("쓰이지 않는다")
    override suspend fun closeChatRoom(chatRoomId: Long) = error("쓰이지 않는다")
    override suspend fun joinChatRoom(chatRoomId: Long) = error("쓰이지 않는다")
    override suspend fun leaveChatRoom(chatRoomId: Long) = error("쓰이지 않는다")
    override suspend fun getChatRoomMembers(chatRoomId: Long): List<ChatMember> = error("쓰이지 않는다")
    override suspend fun markAsRead(chatRoomId: Long, readMessageId: Long) = error("쓰이지 않는다")
    override suspend fun getMessages(chatRoomId: Long, cursor: Long?, size: Int): ChatMessagePage =
        error("쓰이지 않는다")
    override suspend fun getMessagesAfter(chatRoomId: Long, cursor: Long, size: Int): ChatMessagePage =
        error("쓰이지 않는다")
    override suspend fun sendMessage(chatRoomId: Long, content: String): ChatMessage = error("쓰이지 않는다")
    override suspend fun deleteMessage(chatRoomId: Long, messageId: Long) = error("쓰이지 않는다")
    override suspend fun searchMessages(chatRoomId: Long, keyword: String, cursor: Long?, size: Int): ChatMessagePage =
        error("쓰이지 않는다")
}
