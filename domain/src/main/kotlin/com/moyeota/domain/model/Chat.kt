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

// 방 참여자 1명(백엔드 ChatRoomMemberResult). 메시지의 senderId(내부 PK)와 공개 신원을 잇는 조각이다.
//
// userId 가 null 인 건 서버가 아직 그 필드를 내려주지 않는 경우다(패치 전 서버). 그때는 이 사람을
// 메시지에 연결할 수 없어 이름 표시가 학습 폴백으로 되돌아간다.
// active=false 는 방을 나간 사람이다 — 목록에서 빼지 않는다. 남긴 메시지의 이름이 사라지면
// 대화가 "동승자"로 뭉개진다.
data class ChatMember(
    val userId: Long?,
    val publicId: String,
    val nickname: String,
    val imageUrl: String?,
    val active: Boolean,
    val isMe: Boolean,
)

data class ChatMessage(
    val id: Long,
    val chatRoomId: Long,
    /**
     * 보낸 사람의 **서버 내부 PK**. 앱은 이 값을 다른 어떤 API 에서도 받지 못하므로
     * 그 자체로는 "누구"인지 알 수 없다 — 내 메시지 판정은 [isMine] 을 볼 것.
     */
    val senderId: Long,
    val content: String,
    val type: ChatMessageType,
    val createdAt: String,
    val deleted: Boolean,
    /**
     * 내가 보낸 메시지인지. 저장소가 계산한다
     * ([com.moyeota.data.repository.RemoteChatRepository] 의 KDoc 에 판정 근거).
     *
     * 근거는 세 가지이고 우선순위가 있다: 메시지의 `senderPublicId`(서버 추가 대기) →
     * 방 참여자 목록의 publicId → 내가 보낸 메시지에서 학습한 내부 id.
     * 참여자 목록이 [ChatMember.userId] 를 주지 않는 서버에서는 학습만 남으므로,
     * 그때는 **이 세션에서 한 번 보낸 뒤에야** 내 과거 메시지가 참이 된다.
     */
    val isMine: Boolean,
    // 보낸 사람 닉네임. 서버 senderNickname 이 오면 그것을, 없으면 방 참여자 목록
    // (GET /chat-rooms/{id}/users)에서 senderId 로 찾은 닉네임을 쓴다. 둘 다 없으면 null.
    val senderName: String?,
)

// 커서 페이징 결과. nextCursor 가 null 이거나 hasNext=false 면 더 불러올 것이 없다.
data class ChatMessagePage(
    val messages: List<ChatMessage>,
    val nextCursor: Long?,
    val hasNext: Boolean,
)
