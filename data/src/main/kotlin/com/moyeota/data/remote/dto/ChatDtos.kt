package com.moyeota.data.remote.dto

import kotlinx.serialization.Serializable

// 백엔드 chat/app/dto/*.java, chat/presentation/dto/*.java (origin/develop) 와 필드 1:1
// enum(status/type)은 문자열로 받고 매퍼에서 도메인 enum 으로 방어적으로 변환한다.

// GET /api/v1/chat-rooms/{id}, POST /api/v1/chat-rooms → ChatRoomResult
@Serializable
data class ChatRoomResponse(
    val id: Long,
    val partyId: Long = 0,
    val departure: String = "",
    val destination: String = "",
    val createdAt: String? = null,
    val status: String = "",
)

// GET /api/v1/chat-rooms/me → List<ChatRoomUserResult>
// 방 이름 정보가 없다는 점에 주의(방마다 상세를 따로 조회해야 한다).
@Serializable
data class ChatRoomUserResponse(
    val chatRoomId: Long,
    val lastReadMessageId: Long? = null,
    val notificationMuted: Boolean = false,
    val joinedAt: String? = null,
)

// POST /api/v1/chat-rooms 요청 본문 (ChatRoomRequest)
@Serializable
data class CreateChatRoomRequestDto(
    val partyId: Long,
    val departure: String,
    val destination: String,
)

// POST /api/v1/chat-rooms/{id}/messages 요청 본문 (SendMessageRequest)
@Serializable
data class SendMessageRequestDto(
    val content: String,
)

// 메시지 1건 (ChatMessageResult). 삭제된 메시지는 서버가 content 를 "삭제된 메시지입니다" 로 치환해 내려준다.
@Serializable
data class ChatMessageResponse(
    val id: Long,
    val chatRoomId: Long = 0,
    val userId: Long = 0,
    val content: String = "",
    val type: String = "",
    val createdAt: String? = null,
    val deleted: Boolean = false,
)

// 커서 페이징 응답 (ChatMessageSlice)
@Serializable
data class ChatMessageSliceResponse(
    val messages: List<ChatMessageResponse> = emptyList(),
    val nextCursor: Long? = null,
    val hasNext: Boolean = false,
)
