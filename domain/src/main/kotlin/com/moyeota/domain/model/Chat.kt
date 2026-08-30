package com.moyeota.domain.model

// 백엔드 chat 도메인 enum 과 1:1. 알 수 없는 값은 매퍼에서 ACTIVE/TEXT 로 방어한다.
enum class ChatRoomStatus { ACTIVE, CLOSED, ARCHIVED }

enum class ChatMessageType { TEXT, LOCATION }

// 채팅방. createdAt 은 서버가 ISO-8601(Instant) 문자열로 내려주며 도메인에서는 문자열로 보관한다.
// (domain 모듈은 Android/java.time 의존을 두지 않는다 — 표시 포맷은 presentation 책임)
data class ChatRoom(
    val id: Long,
    val partyId: Long,
    val departure: String,
    val destination: String,
    val createdAt: String,
    val status: ChatRoomStatus,
)

// 내가 그 방에 대해 갖는 상태(마지막 읽은 메시지 등). 백엔드 ChatRoomUserResult 대응.
data class ChatRoomMembership(
    val chatRoomId: Long,
    val lastReadMessageId: Long?,
    val notificationMuted: Boolean,
    val joinedAt: String,
)

// GET /chat-rooms/me 는 membership 만 주고 방 이름(출발지/목적지)을 주지 않는다.
// 채팅 목록 화면이 한 번에 그릴 수 있도록 Repository 에서 방 상세를 합쳐 내려준다.
data class MyChatRoom(
    val room: ChatRoom,
    val membership: ChatRoomMembership,
)

data class ChatMessage(
    val id: Long,
    val chatRoomId: Long,
    val senderId: Long,
    val content: String,
    val type: ChatMessageType,
    val createdAt: String,
    val deleted: Boolean,
)

// 커서 페이징 결과. nextCursor 가 null 이거나 hasNext=false 면 더 불러올 것이 없다.
data class ChatMessagePage(
    val messages: List<ChatMessage>,
    val nextCursor: Long?,
    val hasNext: Boolean,
)
