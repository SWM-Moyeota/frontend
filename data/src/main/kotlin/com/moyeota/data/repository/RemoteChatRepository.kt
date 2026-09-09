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

// 이 저장소의 두 번째 책임은 조회가 아니라 **"보낸 사람이 누구인가"의 번역**이다.
//
// 서버 ChatMessageResult 는 발신자를 공개 UUID(publicId)로 밝힌다 — 세션 uuid 와 같은 체계라
// "내 메시지인가"는 문자열 비교 한 번으로 끝난다(매퍼가 한다). 남는 일은 이름뿐이다:
// 메시지에 닉네임이 없어 GET /chat-rooms/{id}/users 를 publicId → 닉네임 사전으로 캐시한다.
//
// 예전에는 서버가 발신자를 내부 PK(userId)로만 표시해, 앱이 "내가 보낸 메시지의 응답에 담긴 userId"를
// **학습**해 내 메시지를 가렸다. 서버가 userId 를 응답에서 빼고 publicId 를 실어 주면서(2026-09-09 실측)
// 그 학습 전략은 근거가 사라져 통째로 삭제됐다 — 42·46 문서의 "학습 폴백" 설명은 폐기다.
//
// 캐시 전략:
// - 방 진입(getMessages 의 cursor == null)에 참여자 목록을 받아 방별로 캐시한다.
//   실패해도 메시지 로드는 그대로 진행한다 — 이름이 덜 보일 뿐, 대화는 열려야 하고
//   **내 말풍선 위치는 사전과 무관하게 정확하다**.
// - 사전에 없는 발신자가 나오면(중간 합류자) 그 로드에서 **한 번만** 재조회한다.
// - 재조회해도 못 찾은 publicId 는 unresolved 에 적어 두고 다시 부르지 않는다(폴링마다 재조회 방지).
//
// 계정 경계: 참여자 캐시는 "그때 그 계정"의 것이다. 캐시를 만든 uuid 를 함께 들고 있다가 현재 세션
// uuid 와 다르면(로그아웃 → null, 재로그인 → 다른 값) 통째로 버린다.
class RemoteChatRepository(
    private val api: ChatApi,
    private val session: UserSession,
) : ChatRepository {

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

    // 전송 응답도 조회와 같은 shape 이라 발신자 publicId 가 들어 있다 — 방금 보낸 메시지가
    // 그 자리에서 바로 내 것으로 판정된다(화면이 전송 결과를 그대로 목록에 붙이기 때문에 중요하다).
    override suspend fun sendMessage(chatRoomId: Long, content: String): ChatMessage = chatCall {
        val sent = api.sendMessage(chatRoomId, SendMessageRequestDto(content))
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

    // --- 참여자 사전(publicId → 닉네임) ---

    // 이 응답에 사전이 모르는 발신자가 있으면 한 번 재조회하고, 그래도 못 찾으면 다시 묻지 않게 적어 둔다.
    // 사전은 이름을 위한 것이므로, 여기서 실패해도 isMine 판정은 흔들리지 않는다.
    //
    // priorRefresh: 이 호출에서 이미 참여자 목록을 불렀다면 그 성공 여부(같은 로드에서 두 번 부르지 않는다).
    //   null 이면 아직 부르지 않았다는 뜻.
    private suspend fun resolveIdentity(
        chatRoomId: Long,
        messages: List<ChatMessageResponse>,
        priorRefresh: Boolean?,
    ): ChatIdentity {
        var identity = identity(chatRoomId)
        val missing = missingSenderPublicIds(chatRoomId, messages, identity)
        if (missing.isEmpty()) return identity

        val refreshed = priorRefresh ?: refreshMembersQuietly(chatRoomId).also { identity = identity(chatRoomId) }
        // 조회 자체가 실패했다면 "없는 사람"으로 단정하지 않는다 — 다음 로드에서 다시 시도한다.
        if (refreshed) markUnresolved(chatRoomId, missing.filterNot { identity.members.containsKey(it) })
        return identity
    }

    private fun missingSenderPublicIds(
        chatRoomId: Long,
        messages: List<ChatMessageResponse>,
        identity: ChatIdentity,
    ): List<String> {
        val unresolved = roomMembers(chatRoomId).unresolved
        return messages.asSequence()
            .mapNotNull { it.senderPublicId?.takeIf(String::isNotBlank) ?: it.publicId?.takeIf(String::isNotBlank) }
            .filter { !identity.members.containsKey(it) && it !in unresolved }
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

    private fun identity(chatRoomId: Long): ChatIdentity =
        ChatIdentity(myUuid = session.currentUserUuid, members = roomMembers(chatRoomId).byPublicId)

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
        // publicId 가 빈 참여자는 메시지와 이을 키가 없어 사전에 들어가지 못한다(응답이 깨진 경우).
        val byPublicId = members.filter { it.publicId.isNotBlank() }.associateBy { it.publicId }
        val stillUnknown = memberCache[chatRoomId]?.unresolved.orEmpty() - byPublicId.keys
        memberCache[chatRoomId] = RoomMembers(byPublicId = byPublicId, unresolved = stillUnknown)
    }

    private fun markUnresolved(chatRoomId: Long, ids: Collection<String>) {
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

    // byPublicId: publicId → 참여자. unresolved: 재조회로도 못 찾은 발신자 publicId(무한 재조회 방지).
    private data class RoomMembers(
        val byPublicId: Map<String, ChatMember>,
        val unresolved: Set<String>,
    ) {
        companion object {
            val EMPTY = RoomMembers(byPublicId = emptyMap(), unresolved = emptySet())
        }
    }
}
