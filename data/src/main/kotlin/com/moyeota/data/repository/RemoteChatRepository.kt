package com.moyeota.data.repository

import com.moyeota.data.remote.ChatApi
import com.moyeota.data.remote.ChatIdentity
import com.moyeota.data.remote.chatCall
import com.moyeota.data.remote.dto.ChatMessageResponse
import com.moyeota.data.remote.dto.CreateChatRoomRequestDto
import com.moyeota.data.remote.dto.SendMessageRequestDto
import com.moyeota.data.remote.toChatMember
import com.moyeota.data.remote.toChatMessage
import com.moyeota.data.remote.toChatRoom
import com.moyeota.data.remote.toMembership
import com.moyeota.data.remote.toPage
import com.moyeota.domain.model.ChatMember
import com.moyeota.domain.model.ChatMessage
import com.moyeota.domain.model.ChatMessagePage
import com.moyeota.domain.model.ChatRoom
import com.moyeota.domain.model.MyChatRoom
import com.moyeota.domain.repository.ChatRepository
import com.moyeota.domain.session.UserSession
import kotlinx.coroutines.CancellationException
import java.util.concurrent.ConcurrentHashMap

// 이 저장소의 두 번째 책임은 조회가 아니라 **"이 메시지를 누가 보냈는가"의 번역**이다.
//
// 서버 ChatMessageResult 는 보낸 사람을 내부 PK(userId)로만 표시하는데, 앱이 아는 식별자는
// 공개 UUID 하나뿐이라 둘을 이을 키가 없었다. GET /chat-rooms/{id}/users 가 그 사전을 준다 —
// 참여자마다 (userId, publicId, nickname) 이 함께 오므로, 방에 들어가는 순간 한 번 받아 두면
// 이후 모든 메시지를 학습 없이 정확히 가릴 수 있다.
//
// 캐시 전략:
// - 방 진입(getMessages 의 cursor == null)에 참여자 목록을 받아 방별로 캐시한다.
//   실패해도 메시지 로드는 그대로 진행한다 — 이름이 덜 보일 뿐, 대화는 열려야 한다.
// - 폴링/전송 응답에 사전에 없는 발신자가 나오면(중간 합류자) 그 로드에서 **한 번만** 재조회한다.
// - 재조회해도 못 찾은 id 는 unresolved 에 적어 두고 다시 부르지 않는다. 그러지 않으면
//   userId 를 안 주는 구버전 서버에서 폴링마다 참여자 목록을 부르게 된다.
//
// 학습 폴백(구버전 서버 전용): 내가 sendMessage 로 보낸 메시지의 응답에는 내 내부 PK 가 들어 있다.
// 참여자 사전이 비어 있을 때만 이 값으로 내 메시지를 가린다. 사전이 채워지면 자동으로 뒤로 밀린다.
//
// 계정 경계: 참여자 캐시와 학습값 모두 "그때 그 계정"의 것이다. 학습 당시 uuid 를 함께 들고 있다가
// 현재 세션 uuid 와 다르면(로그아웃 → null, 재로그인 → 다른 값) 통째로 버린다. 안 버리면
// 재로그인한 다른 계정 화면에서 이전 사용자의 메시지가 내 말풍선으로 뜬다.
class RemoteChatRepository(
    private val api: ChatApi,
    private val session: UserSession,
) : ChatRepository {

    // 네트워크 콜백/여러 화면에서 동시에 읽힐 수 있어 @Volatile. 두 필드는 항상 함께 쓴다.
    @Volatile
    private var learnedInternalId: Long? = null

    @Volatile
    private var learnedForUuid: String? = null

    // 방 id → 그 방의 참여자 사전. 캐시가 어느 계정의 것인지는 cacheOwnerUuid 가 들고 있다.
    private val memberCache = ConcurrentHashMap<Long, RoomMembers>()

    @Volatile
    private var cacheOwnerUuid: String? = null

    // /chat-rooms/me 는 방 이름을 주지 않아 방마다 상세를 한 번 더 부른다(N+1).
    override suspend fun getMyChatRooms(): List<MyChatRoom> = chatCall {
        api.getMyRooms().mapNotNull { membership ->
            val room = runCatching { api.getRoom(membership.chatRoomId) }.getOrNull() ?: return@mapNotNull null
            MyChatRoom(room = room.toChatRoom(), membership = membership.toMembership())
        }
    }

    override suspend fun getChatRoom(chatRoomId: Long): ChatRoom = chatCall {
        api.getRoom(chatRoomId).toChatRoom()
    }

    override suspend fun createChatRoom(partyId: Long, departure: String, destination: String): ChatRoom =
        chatCall { api.createRoom(CreateChatRoomRequestDto(partyId, departure, destination)).toChatRoom() }

    override suspend fun closeChatRoom(chatRoomId: Long) = chatCall { api.deleteRoom(chatRoomId) }

    override suspend fun joinChatRoom(chatRoomId: Long) = chatCall { api.joinRoom(chatRoomId) }

    override suspend fun leaveChatRoom(chatRoomId: Long) = chatCall { api.leaveRoom(chatRoomId) }

    // 목록은 그대로 돌려주되 캐시도 함께 채운다 — 호출자가 참여자를 보려고 부른 김에
    // 다음 메시지 매핑도 정확해진다. userId 가 없는 참여자는 캐시에 넣을 수 없지만(이을 키가 없다)
    // 반환 목록에는 그대로 포함한다.
    override suspend fun getChatRoomMembers(chatRoomId: Long): List<ChatMember> = chatCall {
        fetchMembers(chatRoomId)
    }

    override suspend fun markAsRead(chatRoomId: Long, readMessageId: Long) = chatCall {
        api.readRoom(chatRoomId, readMessageId)
    }

    // 방 진입(cursor == null)에만 참여자 목록을 먼저 받는다. 위로 더 불러오는 페이징에서는
    // 사전에 없는 발신자가 실제로 나왔을 때만 재조회한다.
    override suspend fun getMessages(chatRoomId: Long, cursor: Long?, size: Int): ChatMessagePage = chatCall {
        val refreshed = if (cursor == null) refreshMembersQuietly(chatRoomId) else null
        val slice = api.getMessages(chatRoomId, cursor, size)
        slice.toPage(resolveIdentity(chatRoomId, slice.messages, refreshed))
    }

    override suspend fun getMessagesAfter(chatRoomId: Long, cursor: Long, size: Int): ChatMessagePage = chatCall {
        val slice = api.getMessagesAfter(chatRoomId, cursor, size)
        slice.toPage(resolveIdentity(chatRoomId, slice.messages, priorRefresh = null))
    }

    // 응답에 담긴 내 내부 PK 를 학습한 뒤 매핑한다 — 참여자 사전이 없는 서버에서도
    // 방금 보낸 메시지부터 isMine 이 참이어야 한다.
    override suspend fun sendMessage(chatRoomId: Long, content: String): ChatMessage = chatCall {
        val sent = api.sendMessage(chatRoomId, SendMessageRequestDto(content))
        learn(sent.userId)
        sent.toChatMessage(resolveIdentity(chatRoomId, listOf(sent), priorRefresh = null))
    }

    override suspend fun deleteMessage(chatRoomId: Long, messageId: Long) = chatCall {
        api.deleteMessage(chatRoomId, messageId)
    }

    override suspend fun searchMessages(
        chatRoomId: Long,
        keyword: String,
        cursor: Long?,
        size: Int,
    ): ChatMessagePage = chatCall {
        val slice = api.searchMessages(chatRoomId, keyword, cursor, size)
        slice.toPage(resolveIdentity(chatRoomId, slice.messages, priorRefresh = null))
    }

    // --- 참여자 사전 ---

    // 이 응답에 사전이 모르는 발신자가 있으면 한 번 재조회하고, 그래도 못 찾으면 다시 묻지 않게 적어 둔다.
    //
    // priorRefresh: 이 호출에서 이미 참여자 목록을 불렀다면 그 성공 여부(같은 로드에서 두 번 부르지 않는다).
    //   null 이면 아직 부르지 않았다는 뜻.
    private suspend fun resolveIdentity(
        chatRoomId: Long,
        messages: List<ChatMessageResponse>,
        priorRefresh: Boolean?,
    ): ChatIdentity {
        var identity = identity(chatRoomId)
        val missing = missingSenderIds(chatRoomId, messages, identity)
        if (missing.isEmpty()) return identity

        val refreshed = priorRefresh ?: refreshMembersQuietly(chatRoomId).also { identity = identity(chatRoomId) }
        // 조회 자체가 실패했다면 "없는 사람"으로 단정하지 않는다 — 다음 로드에서 다시 시도한다.
        if (refreshed) markUnresolved(chatRoomId, missing.filterNot { identity.members.containsKey(it) })
        return identity
    }

    private fun missingSenderIds(
        chatRoomId: Long,
        messages: List<ChatMessageResponse>,
        identity: ChatIdentity,
    ): List<Long> {
        val unresolved = roomMembers(chatRoomId).unresolved
        return messages.asSequence()
            // senderPublicId 가 오는 메시지는 사전이 필요 없다(서버가 신원을 직접 실어 준 경우).
            .filter { it.senderPublicId.isNullOrBlank() }
            .map { it.userId }
            .filter { it > 0L && !identity.members.containsKey(it) && it !in unresolved }
            .distinct()
            .toList()
    }

    // 실패를 삼킨다 — 참여자 목록은 표시를 좋게 할 뿐, 이것 때문에 채팅방이 안 열리면 안 된다.
    // 취소만은 통과시킨다(화면 이탈을 "조회 실패"로 기록하지 않기 위해).
    private suspend fun refreshMembersQuietly(chatRoomId: Long): Boolean =
        try {
            fetchMembers(chatRoomId)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            false
        }

    private suspend fun fetchMembers(chatRoomId: Long): List<ChatMember> {
        val myUuid = session.currentUserUuid
        val members = api.getMembers(chatRoomId).map { it.toChatMember(myUuid) }
        storeMembers(chatRoomId, members)
        return members
    }

    private fun identity(chatRoomId: Long): ChatIdentity {
        val uuid = session.currentUserUuid
        // 학습 당시의 계정과 지금 계정이 같을 때만 학습값이 유효하다.
        // uuid 가 null(로그아웃/복원 전)이면 어떤 학습값도 쓰지 않는다 — 잘못된 "내 메시지"보다 낫다.
        val learned = if (uuid != null && uuid == learnedForUuid) learnedInternalId else null
        return ChatIdentity(myUuid = uuid, myInternalId = learned, members = roomMembers(chatRoomId).byUserId)
    }

    // 읽는 시점에 계정을 확인해 남의 캐시를 버린다(authState 구독보다 어긋날 여지가 적다).
    private fun roomMembers(chatRoomId: Long): RoomMembers {
        val uuid = session.currentUserUuid
        if (uuid == null || uuid != cacheOwnerUuid) {
            memberCache.clear()
            return RoomMembers.EMPTY
        }
        return memberCache[chatRoomId] ?: RoomMembers.EMPTY
    }

    private fun storeMembers(chatRoomId: Long, members: List<ChatMember>) {
        val uuid = session.currentUserUuid ?: return
        claimCache(uuid)
        // userId 가 null 인 참여자는 메시지와 이을 수 없어 사전에 들어가지 못한다(구버전 서버).
        val byUserId = members.mapNotNull { member -> member.userId?.let { it to member } }.toMap()
        val stillUnknown = memberCache[chatRoomId]?.unresolved.orEmpty() - byUserId.keys
        memberCache[chatRoomId] = RoomMembers(byUserId = byUserId, unresolved = stillUnknown)
    }

    private fun markUnresolved(chatRoomId: Long, ids: Collection<Long>) {
        if (ids.isEmpty()) return
        val uuid = session.currentUserUuid ?: return
        claimCache(uuid)
        val previous = memberCache[chatRoomId] ?: RoomMembers.EMPTY
        memberCache[chatRoomId] = previous.copy(unresolved = previous.unresolved + ids)
    }

    private fun claimCache(uuid: String) {
        if (uuid != cacheOwnerUuid) {
            memberCache.clear()
            cacheOwnerUuid = uuid
        }
    }

    private fun learn(userId: Long) {
        // 서버가 값을 빠뜨려 DTO 기본값 0 이 들어온 경우까지 "내 id" 로 굳히면 남의 메시지가 내 것이 된다.
        if (userId <= 0L) return
        learnedForUuid = session.currentUserUuid
        learnedInternalId = userId
    }

    // byUserId: userId → 참여자. unresolved: 재조회로도 못 찾은 발신자 id(무한 재조회 방지).
    private data class RoomMembers(
        val byUserId: Map<Long, ChatMember>,
        val unresolved: Set<Long>,
    ) {
        companion object {
            val EMPTY = RoomMembers(byUserId = emptyMap(), unresolved = emptySet())
        }
    }
}
