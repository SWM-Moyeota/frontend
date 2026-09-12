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
// lastMessage 는 2026-09-11 서버 커밋(963c340)부터 실린다 — 그 전 서버는 필드가 없어 null.
@Serializable
data class ChatRoomUserResponse(
    val chatRoomId: Long,
    val lastReadMessageId: Long? = null,
    val notificationMuted: Boolean = false,
    val joinedAt: String? = null,
    val lastMessage: ChatLastMessageResponse? = null,
    // 2026-09-12 서버(7c95683)부터. 필드가 없는 구서버는 null 로 두어 앱이 「개수 모름」으로 다룬다.
    val unreadCount: Long? = null,
)

// ChatRoomUserResult.LastMessage — 방의 마지막 메시지 요약. 삭제된 메시지는 서버가 content 를 치환해 준다.
// senderPublicId 는 발신자를 못 찾으면(탈퇴) null.
@Serializable
data class ChatLastMessageResponse(
    val id: Long,
    val senderPublicId: String? = null,
    val content: String = "",
    val type: String = "",
    val createdAt: String? = null,
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
//
// 배포 서버 실측(2026-09-09, GET /chat-rooms/1/messages · POST .../messages):
//   {"id","chatRoomId","publicId","content","type","createdAt","deleted"}
// 즉 **userId 는 더 이상 오지 않고 publicId(발신자 공개 UUID)가 그 자리를 대신한다.**
@Serializable
data class ChatMessageResponse(
    val id: Long,
    val chatRoomId: Long = 0,
    /**
     * 보낸 사람의 **공개 UUID**(JWT `sub` 와 같은 값). 현행 서버가 실어 주는 발신자 신원이며,
     * 세션 uuid 와 비교하는 것만으로 내 메시지를 가릴 수 있다 — 학습도 사전도 필요 없다.
     */
    val publicId: String? = null,
    /**
     * 같은 값의 옛 이름. 앱이 백엔드에 요청했던 필드명이 `senderPublicId` 였고 서버는 `publicId` 로
     * 냈다 — 어느 쪽이 와도 읽도록 둘 다 받는다([publicId] 보다 이쪽이 우선).
     */
    val senderPublicId: String? = null,
    /**
     * 보낸 사람의 서버 내부 PK. **현행 서버는 주지 않는다**(기본값 0 이 그대로 남는다).
     * 앱은 이 값을 더 이상 쓰지 않으며, 필드는 구버전 서버 응답을 파싱 에러 없이 받기 위해서만 남겼다.
     */
    val userId: Long = 0,
    val content: String = "",
    val type: String = "",
    val createdAt: String? = null,
    val deleted: Boolean = false,
    /** 보낸 사람 닉네임. 서버가 주지 않아 참여자 목록에서 채운다 — 오면 쓰는 관용 필드. */
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
// 배포 서버 실측(2026-09-09): {"publicId","nickname","imageUrl","active"} — userId 는 없다.
//
// 메시지도 publicId 로 발신자를 밝히므로 이 목록은 **publicId → 닉네임/프로필 사전**으로만 쓰인다
// (예전처럼 내부 PK 를 공개 신원으로 번역하는 다리 역할은 끝났다).
// active=false 는 방을 나간 사람이며, 그 사람이 남긴 메시지의 이름을 지우지 않기 위해 목록에 남는다.
@Serializable
data class ChatMemberResponse(
    /** 현행 서버는 주지 않는다(항상 null). 구버전 응답 호환으로만 남긴 필드. */
    val userId: Long? = null,
    val publicId: String = "",
    val nickname: String = "",
    val imageUrl: String? = null,
    val active: Boolean = true,
)
