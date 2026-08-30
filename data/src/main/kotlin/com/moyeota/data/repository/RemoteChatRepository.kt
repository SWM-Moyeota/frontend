package com.moyeota.data.repository

import com.moyeota.data.remote.ChatApi
import com.moyeota.data.remote.dto.CreateChatRoomRequestDto
import com.moyeota.data.remote.dto.SendMessageRequestDto
import com.moyeota.data.remote.toChatMessage
import com.moyeota.data.remote.toChatRoom
import com.moyeota.data.remote.toMembership
import com.moyeota.data.remote.toPage
import com.moyeota.domain.model.ChatMessage
import com.moyeota.domain.model.ChatMessagePage
import com.moyeota.domain.model.ChatRoom
import com.moyeota.domain.model.MyChatRoom
import com.moyeota.domain.repository.ChatRepository

class RemoteChatRepository(
    private val api: ChatApi,
) : ChatRepository {

    // /chat-rooms/me 는 방 이름을 주지 않아 방마다 상세를 한 번 더 부른다(N+1).
    // 서버가 목록에 방 정보를 포함하도록 개선되면 이 루프를 제거한다.
    override suspend fun getMyChatRooms(userId: Long): List<MyChatRoom> =
        api.getMyRooms(userId).mapNotNull { membership ->
            val room = runCatching { api.getRoom(membership.chatRoomId) }.getOrNull() ?: return@mapNotNull null
            MyChatRoom(room = room.toChatRoom(), membership = membership.toMembership())
        }

    override suspend fun getChatRoom(chatRoomId: Long): ChatRoom = api.getRoom(chatRoomId).toChatRoom()

    override suspend fun createChatRoom(partyId: Long, departure: String, destination: String): ChatRoom =
        api.createRoom(CreateChatRoomRequestDto(partyId, departure, destination)).toChatRoom()

    override suspend fun closeChatRoom(chatRoomId: Long) = api.deleteRoom(chatRoomId)

    override suspend fun joinChatRoom(chatRoomId: Long, userId: Long) = api.joinRoom(userId, chatRoomId)

    override suspend fun leaveChatRoom(chatRoomId: Long, userId: Long) = api.leaveRoom(userId, chatRoomId)

    override suspend fun markAsRead(chatRoomId: Long, userId: Long, readMessageId: Long) =
        api.readRoom(userId, chatRoomId, readMessageId)

    override suspend fun getMessages(
        chatRoomId: Long,
        userId: Long,
        cursor: Long?,
        size: Int,
    ): ChatMessagePage = api.getMessages(userId, chatRoomId, cursor, size).toPage()

    override suspend fun getMessagesAfter(
        chatRoomId: Long,
        userId: Long,
        cursor: Long,
        size: Int,
    ): ChatMessagePage = api.getMessagesAfter(userId, chatRoomId, cursor, size).toPage()

    override suspend fun sendMessage(chatRoomId: Long, userId: Long, content: String): ChatMessage =
        api.sendMessage(userId, chatRoomId, SendMessageRequestDto(content)).toChatMessage()

    override suspend fun deleteMessage(chatRoomId: Long, userId: Long, messageId: Long) =
        api.deleteMessage(userId, chatRoomId, messageId)
}
