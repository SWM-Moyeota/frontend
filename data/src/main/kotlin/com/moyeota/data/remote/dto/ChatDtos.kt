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
    /**
     * 보낸 사람의 **서버 내부 PK**. 앱은 이 값을 다른 어떤 API 에서도 받지 못해 그 자체로는
     * 누구인지 알 수 없다 — 내가 보낸 메시지의 응답에서 학습해야 비교가 가능해진다
     * (RemoteChatRepository).
     */
    val userId: Long = 0,
    val content: String = "",
    val type: String = "",
    val createdAt: String? = null,
    val deleted: Boolean = false,
    /**
     * 보낸 사람의 공개 UUID. **아직 서버가 내려주지 않는다** — 백엔드에 추가를 요청해 둔 필드이며,
     * 들어오는 순간 학습 없이도 세션 uuid 와의 비교만으로 내 메시지를 정확히 가릴 수 있다.
     * 파티 members 가 이미 publicId 로 같은 문제를 푼다.
     */
    val senderPublicId: String? = null,
    /** 보낸 사람 닉네임. 역시 서버 추가 대기 중 — 없으면 화면이 "동승자"로 폴백한다. */
    val senderNickname: String? = null,
)

// 커서 페이징 응답 (ChatMessageSlice)
@Serializable
data class ChatMessageSliceResponse(
    val messages: List<ChatMessageResponse> = emptyList(),
    val nextCursor: Long? = null,
    val hasNext: Boolean = false,
)

// GET /api/v1/chat-rooms/{chatRoomId}/users → List<ChatRoomMemberResult>
// 메시지의 userId(내부 PK)와 공개 식별자(publicId)를 잇는 유일한 다리다 —
// 이 목록이 있어야 "누가 보냈는가"를 학습 없이 알 수 있다.
//
// userId 는 백엔드 패치로 뒤늦게 추가된 필드라 nullable 이다: 패치 전 서버는 이 값을 주지 않고,
// 그때는 메시지와 이을 키가 없어 저장소가 기존 학습 폴백으로 되돌아간다.
// active=false 는 방을 나간 사람이며, 그 사람이 남긴 메시지의 이름을 지우지 않기 위해 목록에 남는다.
@Serializable
data class ChatMemberResponse(
    val userId: Long? = null,
    val publicId: String = "",
    val nickname: String = "",
    val imageUrl: String? = null,
    val active: Boolean = true,
)
