package com.moyeota.data.remote

import com.moyeota.data.remote.dto.ChatMessageResponse
import com.moyeota.data.remote.dto.ChatMessageSliceResponse
import com.moyeota.data.remote.dto.ChatRoomResponse
import com.moyeota.data.remote.dto.ChatRoomUserResponse
import com.moyeota.data.remote.dto.CreateChatRoomRequestDto
import com.moyeota.data.remote.dto.SendMessageRequestDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 경로 기준: chat/presentation/{ChatRoom,ChatRoomUser,ChatMessage}Controller.java
 *
 * **채팅만 헤더 두 개를 요구한다.** 다른 도메인이 `@CurrentUser` 로 넘어갈 때 채팅 컨트롤러는
 * `@RequestHeader("X-User-Id") Long userId` 로 남아, 이제 두 가지를 동시에 만족해야 한다:
 * - `Authorization: Bearer` — Security 가 전 경로를 인증 필수로 잡는다. 없으면 401 `USER005`.
 * - `X-User-Id` — 핸들러가 직접 읽는다. 없으면 400(스프링 기본 응답).
 *
 * 즉 토큰의 주체와 헤더의 id 가 **서로 검증되지 않는다** — 헤더에 아무 id 나 넣어도 통과한다.
 * 앱은 [com.moyeota.domain.session.UserSession.FIXED_MEMBER_ID] 를 싣고 있어 실제 로그인 계정과
 * 어긋날 수 있다. 백엔드에 `@CurrentUser` 전환을 요청해 뒀다.
 * (방 생성/조회/삭제는 X-User-Id 없이도 되지만 Bearer 는 여전히 필요하다.)
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
    suspend fun getMyRooms(@Header("X-User-Id") userId: Long): List<ChatRoomUserResponse>

    // 201 Created, 본문 없음
    @POST("api/v1/chat-rooms/{chatRoomId}/users")
    suspend fun joinRoom(
        @Header("X-User-Id") userId: Long,
        @Path("chatRoomId") chatRoomId: Long,
    )

    // 200 OK, 본문 없음
    @DELETE("api/v1/chat-rooms/{chatRoomId}/users")
    suspend fun leaveRoom(
        @Header("X-User-Id") userId: Long,
        @Path("chatRoomId") chatRoomId: Long,
    )

    // 200 OK, 본문 없음
    @POST("api/v1/chat-rooms/{chatRoomId}/users/read/{readMessageId}")
    suspend fun readRoom(
        @Header("X-User-Id") userId: Long,
        @Path("chatRoomId") chatRoomId: Long,
        @Path("readMessageId") readMessageId: Long,
    )

    // --- ChatMessageController ---
    // cursor 생략 시 최신 메시지부터. 서버 기본 size 는 30.
    @GET("api/v1/chat-rooms/{chatRoomId}/messages")
    suspend fun getMessages(
        @Header("X-User-Id") userId: Long,
        @Path("chatRoomId") chatRoomId: Long,
        @Query("cursor") cursor: Long?,
        @Query("size") size: Int,
    ): ChatMessageSliceResponse

    // cursor 필수 — 이 값 이후의 새 메시지를 가져온다.
    @GET("api/v1/chat-rooms/{chatRoomId}/messages/after")
    suspend fun getMessagesAfter(
        @Header("X-User-Id") userId: Long,
        @Path("chatRoomId") chatRoomId: Long,
        @Query("cursor") cursor: Long,
        @Query("size") size: Int,
    ): ChatMessageSliceResponse

    // 201 Created, 생성된 메시지 반환
    @POST("api/v1/chat-rooms/{chatRoomId}/messages")
    suspend fun sendMessage(
        @Header("X-User-Id") userId: Long,
        @Path("chatRoomId") chatRoomId: Long,
        @Body request: SendMessageRequestDto,
    ): ChatMessageResponse

    // 204 No Content
    @DELETE("api/v1/chat-rooms/{chatRoomId}/messages/{messageId}")
    suspend fun deleteMessage(
        @Header("X-User-Id") userId: Long,
        @Path("chatRoomId") chatRoomId: Long,
        @Path("messageId") messageId: Long,
    )
}
