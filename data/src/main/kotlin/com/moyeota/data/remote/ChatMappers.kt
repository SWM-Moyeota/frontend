package com.moyeota.data.remote

import com.moyeota.data.remote.dto.ApiErrorDto
import com.moyeota.data.remote.dto.ChatLastMessageResponse
import com.moyeota.data.remote.dto.ChatMemberResponse
import com.moyeota.data.remote.dto.ChatMessageResponse
import com.moyeota.data.remote.dto.ChatMessageSliceResponse
import com.moyeota.data.remote.dto.ChatRoomResponse
import com.moyeota.data.remote.dto.ChatRoomUserResponse
import com.moyeota.domain.model.ChatException
import com.moyeota.domain.model.ChatLastMessage
import com.moyeota.domain.model.ChatMember
import com.moyeota.domain.model.ChatMessage
import com.moyeota.domain.model.ChatMessagePage
import com.moyeota.domain.model.ChatMessageType
import com.moyeota.domain.model.ChatRoom
import com.moyeota.domain.model.ChatRoomMembership
import com.moyeota.domain.model.ChatRoomStatus
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

private val errorJson = Json { ignoreUnknownKeys = true }

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

/**
 * @param myUuid 세션 공개 UUID. 마지막 메시지가 내 것인지(`isMine`) 가리는 데만 쓴다 —
 *   빈 값끼리의 비교로 남의 메시지가 내 것이 되지 않게 빈 문자열은 "없음"으로 접는다.
 */
fun ChatRoomUserResponse.toMembership(myUuid: String? = null): ChatRoomMembership = ChatRoomMembership(
    chatRoomId = chatRoomId,
    lastReadMessageId = lastReadMessageId,
    notificationMuted = notificationMuted,
    joinedAt = joinedAt.orEmpty(),
    lastMessage = lastMessage?.toLastMessage(myUuid),
)

fun ChatLastMessageResponse.toLastMessage(myUuid: String?): ChatLastMessage {
    val sender = senderPublicId?.takeIf { it.isNotBlank() }
    return ChatLastMessage(
        id = id,
        senderPublicId = sender,
        content = content,
        type = chatMessageTypeOf(type),
        createdAt = createdAt.orEmpty(),
        isMine = sender != null && sender == myUuid,
    )
}

// "이 메시지가 누구 것인가"를 판정할 때 쓰는 근거.
//
// 예전에는 근거가 셋이었다(메시지 publicId / 참여자 사전 / 내가 보낸 메시지에서 학습한 내부 PK).
// 서버가 메시지에 발신자 publicId 를 실어 주면서 **하나로 줄었다** — 세션 uuid 와의 문자열 비교.
// 학습 폴백은 근거가 사라져 삭제했다(서버가 userId 를 더 이상 주지 않는다).
//
// myUuid: 세션의 공개 UUID. 미로그인/복원 전이면 null 이고, 그때는 어떤 메시지도 내 것이 아니다.
// members: 방 참여자 목록(publicId → 참여자). 이제 신원 판정에는 쓰이지 않고 **닉네임 사전**으로만 쓴다.
data class ChatIdentity(
    val myUuid: String?,
    val members: Map<String, ChatMember> = emptyMap(),
)

// publicId 가 빈 문자열인 참여자는 서버 응답이 깨진 경우다 — isMe 를 참으로 만들지 않는다
// (myUuid 도 null 이면 "빈 값끼리 같다"로 남의 메시지가 내 것이 될 수 있다).
fun ChatMemberResponse.toChatMember(myUuid: String?): ChatMember = ChatMember(
    userId = userId,
    publicId = publicId,
    nickname = nickname,
    imageUrl = imageUrl,
    active = active,
    isMe = publicId.isNotBlank() && publicId == myUuid,
)

/**
 * 발신자 공개 UUID. 서버 필드명이 흔들려서(`senderPublicId` 요청 → `publicId` 구현) 둘 다 읽는다.
 * 빈 문자열은 "없음"으로 접는다 — 빈 값끼리의 비교가 남의 메시지를 내 것으로 만들면 안 된다.
 */
private val ChatMessageResponse.effectiveSenderPublicId: String?
    get() = senderPublicId?.takeIf { it.isNotBlank() } ?: publicId?.takeIf { it.isNotBlank() }

// isMine 은 발신자 publicId 와 세션 uuid 의 비교 하나로 끝난다. 둘 다 서버가 준 공개 신원이라
// 계정 전환 직후에도 어긋나지 않는다(캐시된 ChatMember.isMe 를 믿지 않는 이유이기도 하다).
//
// senderName 은 참여자 사전(publicId → 닉네임)이 우선이고, 서버가 senderNickname 을 실어 주면
// 그걸 폴백으로 쓴다. 방을 나간 참여자(active=false)도 사전에 남아 있어 이름이 유지된다.
fun ChatMessageResponse.toChatMessage(identity: ChatIdentity): ChatMessage {
    val senderPublicId = effectiveSenderPublicId
    val member = senderPublicId?.let { identity.members[it] }
    return ChatMessage(
        id = id,
        chatRoomId = chatRoomId,
        senderPublicId = senderPublicId,
        content = content,
        type = chatMessageTypeOf(type),
        createdAt = createdAt.orEmpty(),
        deleted = deleted,
        isMine = senderPublicId != null && senderPublicId == identity.myUuid,
        senderName = member?.nickname?.takeIf { it.isNotBlank() }
            ?: senderNickname?.takeIf { it.isNotBlank() },
    )
}

fun ChatMessageSliceResponse.toPage(identity: ChatIdentity): ChatMessagePage = ChatMessagePage(
    messages = messages.map { it.toChatMessage(identity) },
    nextCursor = nextCursor,
    hasNext = hasNext,
)

/**
 * Retrofit/OkHttp 예외를 [ChatException] 으로 좁힌다. presentation 이 HTTP 를 알 필요가 없게 하는 경계다.
 *
 * 채팅은 `ChatExceptionHandler` 가 `ErrorResponse(code, message)` 를 내려주는데 code 가
 * **enum 이름 그대로**다(`CHAT_NOT_PARTICIPANT` 등) — 인증 쪽 `USER102` 같은 번호 체계와 다르므로
 * [toAuthException] 의 매핑을 재사용할 수 없다. 본문 shape 만 같아 [ApiErrorDto] 를 공유한다.
 *
 * 상태 코드만으로는 갈리지 않는 조합이 있어 code 를 우선 본다 — 403 은 참여자 아님과
 * 남의 메시지 삭제 둘 다, 409 는 종료된 방·중복 참여·이미 삭제된 메시지가 겹친다.
 */
internal fun Throwable.toChatException(): ChatException = when (this) {
    is ChatException -> this

    is HttpException -> {
        val body = readChatErrorBody()
        ChatException(
            code = body?.code,
            serverMessage = body?.message,
            httpStatus = code(),
            cause = this,
        )
    }

    // 응답 자체를 못 받은 경우(연결 거부·타임아웃·DNS). 사용자에게는 재시도를 안내한다.
    is IOException -> ChatException(code = null, serverMessage = null, isNetwork = true, cause = this)

    else -> ChatException(code = null, serverMessage = null, cause = this)
}

private fun HttpException.readChatErrorBody(): ApiErrorDto? =
    runCatching {
        response()?.errorBody()?.string()
            ?.takeIf { it.isNotBlank() }
            ?.let { errorJson.decodeFromString<ApiErrorDto>(it) }
    }.getOrNull()

/**
 * 채팅 호출을 감싸 실패를 [ChatException] 으로 통일한다.
 * [CancellationException] 은 그대로 통과시킨다 — 취소를 에러로 바꾸면 화면 이탈이 에러 토스트로 보인다.
 */
internal suspend fun <T> chatCall(block: suspend () -> T): T =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        throw e.toChatException()
    }
