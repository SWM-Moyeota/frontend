package com.moyeota.data.remote

import com.moyeota.data.remote.dto.ChatMemberResponse
import com.moyeota.data.remote.dto.ChatMessageResponse
import com.moyeota.data.remote.dto.ChatMessageSliceResponse
import com.moyeota.data.remote.dto.ChatRoomResponse
import com.moyeota.data.remote.dto.ChatRoomUserResponse
import com.moyeota.data.remote.dto.CreateChatRoomRequestDto
import com.moyeota.data.remote.dto.SendMessageRequestDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 경로 기준: chat/presentation/{ChatRoom,ChatRoomUser,ChatMessage}Controller.java
 *
 * **주체는 Bearer 토큰 하나가 정한다.** 채팅 컨트롤러가 `@RequestHeader("X-User-Id") Long userId` 에서
 * `@CurrentUser Long userId` 로 전환되면서(백엔드 PR #72) 앱이 사용자 id 를 실어 보낼 자리는 사라졌다.
 * 남은 요구사항은 `Authorization: Bearer` 뿐이고, 이건 인터셉터가 붙인다
 * (NetworkModule 의 apiClient). 없으면 401 `USER005` 다.
 *
 * 그래서 이 인터페이스에는 `@Header` 가 하나도 없어야 한다 — 되살아나면
 * [AuthenticatedPathContractTest][com.moyeota.data.remote.AuthenticatedPathContractTest] 가 깨진다.
 * (전환 전에는 헤더의 id 와 토큰 주체가 서로 검증되지 않아, 로그인 계정과 무관한 고정 id 로
 * 채팅이 나가고 있었다.)
 *
 * 실패 본문은 이 도메인만 형식이 다르다: `ErrorResponse(code, message)` 이며 code 는
 * `ChatErrorCode` 의 enum 이름이다(`CHAT_NOT_PARTICIPANT` 등). 인증 쪽 `USER1xx` 번호 코드가 아니다.
 */
interface ChatApi {

    // --- ChatRoomController ---
    @GET("api/v1/chat-rooms/{chatRoomId}")
    suspend fun getRoom(@Path("chatRoomId") chatRoomId: Long): ChatRoomResponse

    @POST("api/v1/chat-rooms")
    suspend fun createRoom(@Body request: CreateChatRoomRequestDto): ChatRoomResponse

    // 204 No Content
    @DELETE("api/v1/chat-rooms/{chatRoomId}")
    suspend fun deleteRoom(@Path("chatRoomId") chatRoomId: Long)

    // --- ChatRoomUserController ---
    // 방 이름 없이 내 참여 정보만 배열로 내려온다(래핑 객체 아님).
    @GET("api/v1/chat-rooms/me")
    suspend fun getMyRooms(): List<ChatRoomUserResponse>

    // 201 Created, 본문 없음
    @POST("api/v1/chat-rooms/{chatRoomId}/users")
    suspend fun joinRoom(@Path("chatRoomId") chatRoomId: Long)

    // 200 OK, 본문 없음
    @DELETE("api/v1/chat-rooms/{chatRoomId}/users")
    suspend fun leaveRoom(@Path("chatRoomId") chatRoomId: Long)

    // 방 참여자 목록. 참여자만 부를 수 있다(아니면 403 CHAT_NOT_PARTICIPANT).
    // 나간 사람도 active=false 로 함께 내려온다 — 그 사람 메시지의 이름을 유지하기 위해서다.
    @GET("api/v1/chat-rooms/{chatRoomId}/users")
    suspend fun getMembers(@Path("chatRoomId") chatRoomId: Long): List<ChatMemberResponse>

    // 200 OK, 본문 없음
    // 알림 음소거 — 같은 경로에 POST(끄기) / DELETE(켜기). 둘 다 200, 본문 없음.
    @POST("api/v1/chat-rooms/{chatRoomId}/users/notification/mute")
    suspend fun muteNotification(@Path("chatRoomId") chatRoomId: Long)

    @DELETE("api/v1/chat-rooms/{chatRoomId}/users/notification/mute")
    suspend fun unmuteNotification(@Path("chatRoomId") chatRoomId: Long)

    @POST("api/v1/chat-rooms/{chatRoomId}/users/read/{readMessageId}")
    suspend fun readRoom(
        @Path("chatRoomId") chatRoomId: Long,
        @Path("readMessageId") readMessageId: Long,
    )

    // --- ChatMessageController ---
    // cursor 생략 시 최신 메시지부터. 서버 기본 size 는 30.
    @GET("api/v1/chat-rooms/{chatRoomId}/messages")
    suspend fun getMessages(
        @Path("chatRoomId") chatRoomId: Long,
        @Query("cursor") cursor: Long?,
        @Query("size") size: Int,
    ): ChatMessageSliceResponse

    // cursor 필수 — 이 값 이후의 새 메시지를 가져온다.
    @GET("api/v1/chat-rooms/{chatRoomId}/messages/after")
    suspend fun getMessagesAfter(
        @Path("chatRoomId") chatRoomId: Long,
        @Query("cursor") cursor: Long,
        @Query("size") size: Int,
    ): ChatMessageSliceResponse

    // 201 Created, 생성된 메시지 반환
    @POST("api/v1/chat-rooms/{chatRoomId}/messages")
    suspend fun sendMessage(
        @Path("chatRoomId") chatRoomId: Long,
        @Body request: SendMessageRequestDto,
    ): ChatMessageResponse

    // 204 No Content
    @DELETE("api/v1/chat-rooms/{chatRoomId}/messages/{messageId}")
    suspend fun deleteMessage(
        @Path("chatRoomId") chatRoomId: Long,
        @Path("messageId") messageId: Long,
    )

    // keyword 는 2자 이상이어야 한다 — 미만이면 400 CHAT_INVALID_KEYWORD.
    // 응답은 조회와 같은 ChatMessageSlice 다.
    @GET("api/v1/chat-rooms/{chatRoomId}/messages/search")
    suspend fun searchMessages(
        @Path("chatRoomId") chatRoomId: Long,
        @Query("keyword") keyword: String,
        @Query("cursor") cursor: Long?,
        @Query("size") size: Int,
    ): ChatMessageSliceResponse
}
