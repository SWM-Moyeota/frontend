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

/**
 * 방의 **마지막 메시지** 요약(백엔드 `ChatRoomUserResult.LastMessage`, 2026-09-11 추가).
 * 목록의 미리보기·정렬·안읽음 판정에 쓴다. 삭제된 메시지는 서버가 content 를 「삭제된 메시지입니다」로 바꿔 준다.
 *
 * @property senderPublicId 보낸 사람 공개 UUID. 탈퇴 등으로 서버가 못 찾으면 null
 * @property isMine 내가 보낸 메시지인지(senderPublicId == 세션 uuid). 내 메시지가 마지막이면 안읽음이 아니다
 */
data class ChatLastMessage(
    val id: Long,
    val senderPublicId: String?,
    val content: String,
    val type: ChatMessageType,
    val createdAt: String,
    val isMine: Boolean,
)

// 내가 그 방에 대해 갖는 상태(마지막 읽은 메시지 등). 백엔드 ChatRoomUserResult 대응.
data class ChatRoomMembership(
    val chatRoomId: Long,
    val lastReadMessageId: Long?,
    val notificationMuted: Boolean,
    val joinedAt: String,
    /** 방의 마지막 메시지. 아직 아무 메시지도 없으면 null (구버전 서버 응답도 null 로 떨어진다) */
    val lastMessage: ChatLastMessage? = null,
) {
    /**
     * 안 읽은 메시지가 있는가 — **마지막 메시지가 남의 것이고 읽음 커서가 그 앞에 있을 때**.
     * 메시지가 없는 방은 읽을 게 없으니 false. 서버가 안읽음 **개수**는 아직 주지 않아 유무만 안다.
     */
    val hasUnread: Boolean
        get() {
            val last = lastMessage ?: return false
            if (last.isMine) return false
            val cursor = lastReadMessageId ?: return true
            return cursor < last.id
        }
}

// GET /chat-rooms/me 는 membership(+마지막 메시지)만 주고 방 이름(출발지/목적지)을 주지 않는다.
// 채팅 목록 화면이 한 번에 그릴 수 있도록 Repository 에서 방 상세를 합쳐 내려준다.
data class MyChatRoom(
    val room: ChatRoom,
    val membership: ChatRoomMembership,
)

// 방 참여자 1명(백엔드 ChatRoomMemberResult). 메시지의 발신자 publicId 를 닉네임/프로필로 잇는 사전이다.
//
// [userId] 는 항상 null 이다 — 서버가 내부 PK 를 응답에서 뺐다(2026-09-09 실측). 필드를 남긴 건
// 구버전 서버 응답을 파싱 에러 없이 받기 위해서이며, 앱은 이 값으로 아무것도 판정하지 않는다.
// active=false 는 방을 나간 사람이다 — 목록에서 빼지 않는다. 남긴 메시지의 이름이 사라지면
// 대화가 "동승자"로 뭉개진다.
data class ChatMember(
    /** 서버 내부 PK. 현행 서버는 주지 않아 항상 null 이다(구버전 호환 잔여 필드). */
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
     * 보낸 사람의 **공개 UUID**(서버 `publicId`, JWT `sub` 와 같은 값).
     * 파티 멤버의 [User.id][com.moyeota.domain.model.User.id] 와 같은 체계라 프로필로 이을 수 있다.
     *
     * 서버가 신원을 빠뜨린 응답이면 null 이고, 그때는 [isMine] 이 false, [senderName] 이 null 이다.
     */
    val senderPublicId: String?,
    val content: String,
    val type: ChatMessageType,
    val createdAt: String,
    val deleted: Boolean,
    /**
     * 내가 보낸 메시지인지. `senderPublicId == UserSession.currentUserUuid` 하나로 판정한다
     * — 서버가 메시지에 발신자 publicId 를 실어 주면서 예전의 3단 근거(사전·학습)가 필요 없어졌다.
     */
    val isMine: Boolean,
    /**
     * 보낸 사람 닉네임. 방 참여자 목록(GET /chat-rooms/{id}/users)을 publicId 로 찾은 값이 우선이고,
     * 서버가 `senderNickname` 을 실어 주면 그걸 폴백으로 쓴다. 둘 다 없으면 null.
     */
    val senderName: String?,
)

// 커서 페이징 결과. nextCursor 가 null 이거나 hasNext=false 면 더 불러올 것이 없다.
data class ChatMessagePage(
    val messages: List<ChatMessage>,
    val nextCursor: Long?,
    val hasNext: Boolean,
)
