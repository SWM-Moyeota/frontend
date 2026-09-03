package com.moyeota.data.remote

import com.moyeota.data.remote.dto.ChatMessageResponse
import com.moyeota.data.remote.dto.ChatMessageSliceResponse
import com.moyeota.data.remote.dto.ChatRoomResponse
import com.moyeota.data.remote.dto.ChatRoomUserResponse
import com.moyeota.domain.model.ChatMessage
import com.moyeota.domain.model.ChatMessagePage
import com.moyeota.domain.model.ChatMessageType
import com.moyeota.domain.model.ChatRoom
import com.moyeota.domain.model.ChatRoomMembership
import com.moyeota.domain.model.ChatRoomStatus

// 서버가 enum 을 새로 추가해도 앱이 죽지 않도록 알 수 없는 값은 기본값으로 흡수한다.
fun chatRoomStatusOf(raw: String): ChatRoomStatus = when (raw) {
    "ACTIVE" -> ChatRoomStatus.ACTIVE
    "CLOSED" -> ChatRoomStatus.CLOSED
    "ARCHIVED" -> ChatRoomStatus.ARCHIVED
    else -> ChatRoomStatus.ACTIVE
}

fun chatMessageTypeOf(raw: String): ChatMessageType = when (raw) {
    "LOCATION" -> ChatMessageType.LOCATION
    else -> ChatMessageType.TEXT
}

fun ChatRoomResponse.toChatRoom(): ChatRoom = ChatRoom(
    id = id,
    partyId = partyId,
    departure = departure,
    destination = destination,
    createdAt = createdAt.orEmpty(),
    status = chatRoomStatusOf(status),
)

fun ChatRoomUserResponse.toMembership(): ChatRoomMembership = ChatRoomMembership(
    chatRoomId = chatRoomId,
    lastReadMessageId = lastReadMessageId,
    notificationMuted = notificationMuted,
    joinedAt = joinedAt.orEmpty(),
)

fun ChatMessageResponse.toChatMessage(): ChatMessage = ChatMessage(
    id = id,
    chatRoomId = chatRoomId,
    senderId = userId,
    content = content,
    type = chatMessageTypeOf(type),
    createdAt = createdAt.orEmpty(),
    deleted = deleted,
)

fun ChatMessageSliceResponse.toPage(): ChatMessagePage = ChatMessagePage(
    messages = messages.map { it.toChatMessage() },
    nextCursor = nextCursor,
    hasNext = hasNext,
)
