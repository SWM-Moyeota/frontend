package com.moyeota.data.remote.chat

import android.util.Log
import com.moyeota.data.remote.dto.ChatMessageResponse
import com.moyeota.data.session.TokenHolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.hildan.krossbow.stomp.StompClient
import org.hildan.krossbow.stomp.StompSession
import org.hildan.krossbow.stomp.sendText
import org.hildan.krossbow.stomp.subscribeText
import org.hildan.krossbow.websocket.okhttp.OkHttpWebSocketClient

private const val TAG = "ChatSocket"

/** 끊긴 뒤 다시 붙기까지 기다리는 시간. 서버가 잠깐 재시작해도 몇 초 안에 돌아온다 */
private const val RECONNECT_DELAY_MS = 3_000L

/** 서버가 이 세션의 요청을 거절할 때 쓰는 통로(`StompExceptionHandler`). 구독은 인증만 되면 허용된다 */
private const val ERROR_DESTINATION = "/user/queue/errors"

/**
 * 채팅 실시간 **수신·전송** — STOMP over WebSocket.
 *
 * 서버는 메시지가 저장될 때마다 `/sub/chat-rooms/{id}` 로 밀어 준다(`ChatRedisSubscriber`). 페이로드는
 * REST 조회와 **같은 shape**(`ChatMessageResult`)이라 [ChatMessageResponse] 를 그대로 재사용한다.
 * 보내는 쪽은 `/pub/chat-rooms/{id}/messages` 로 SEND 프레임을 쓴다(`StompChatController`) —
 * **응답이 없는 단방향**이다. 저장 결과는 나에게도 위 구독으로 되돌아온다.
 *
 * 인증은 STOMP **CONNECT 프레임의 `Authorization: Bearer` 헤더**다(`StompAuthInterceptor`).
 * HTTP 헤더가 아니라 프레임 헤더라는 점이 중요하다 — 서버가 그것만 읽는다.
 * 구독도 서버가 검사한다: 그 방의 **활성 참여자**가 아니면 SUBSCRIBE 가 거부된다.
 *
 * **폴링을 대체하지 않고 앞선다.** 이 흐름이 끊겨도 호출부의 폴링이 메시지를 메우므로,
 * 연결 실패는 조용히 재시도만 하고 화면에 에러를 올리지 않는다. 재연결 사이에 오간 메시지도
 * 이 흐름으로는 오지 않으므로 커서 조회가 메워야 한다 — 그래서 폴링을 아주 끄지 않는다.
 */
class ChatSocket(
    private val baseUrl: String,
    okHttpClient: OkHttpClient,
    private val tokens: TokenHolder,
) {
    private val json = Json { ignoreUnknownKeys = true }

    // 인증 인터셉터가 붙은 apiClient 를 그대로 쓰지 않는다 — STOMP 인증은 프레임 헤더라 HTTP 헤더는
    // 필요 없고, 401 재발급 Authenticator 가 업그레이드 요청에 끼어들 이유도 없다.
    private val client = StompClient(OkHttpWebSocketClient(okHttpClient))

    /**
     * 지금 붙어 있는 세션. [messages] 를 수집하는 동안에만 채워지고, 전송이 **같은 세션**을 쓴다 —
     * 보내려고 따로 연결하면 서버 입장에서 세션이 둘이 되고, 전송 결과를 되돌려받을 구독도 없다.
     */
    @Volatile
    private var live: LiveSession? = null

    private class LiveSession(val chatRoomId: Long, val session: StompSession)

    @Serializable
    private data class SendMessageFrame(val content: String)

    @Serializable
    private data class ReadFrame(val lastReadMessageId: Long)

    /**
     * 이 방의 새 메시지 흐름. 수집이 취소될 때까지 유지되고, 끊기면 [RECONNECT_DELAY_MS] 뒤 다시 붙는다.
     * 미로그인이면 연결하지 않고 다음 주기를 기다린다(로그인 뒤 자연히 붙는다).
     */
    fun messages(chatRoomId: Long): Flow<ChatMessageResponse> = flow {
        while (true) {
            try {
                collectRoom(chatRoomId) { emit(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "연결 끊김 roomId=$chatRoomId — ${RECONNECT_DELAY_MS}ms 뒤 재시도: ${e.message}")
            }
            delay(RECONNECT_DELAY_MS)
        }
    }

    /**
     * 이 방으로 메시지를 **보낸다**(SEND `/pub/chat-rooms/{id}/messages`).
     *
     * 서버 핸들러는 `void` 다 — 성공 여부가 프레임으로 돌아오지 않고, 저장된 메시지가 [messages] 구독으로
     * 온다. 그래서 여기서 참을 돌려줘도 "서버가 저장했다"는 뜻이 아니라 **"살아 있는 세션에 써 넣었다"**는
     * 뜻이다. 호출부는 되돌아오는 메시지를 확인으로 삼아야 한다.
     *
     * 연결이 없거나(미로그인·끊김) 다른 방에 붙어 있으면 false — 그때는 호출부가 REST 로 보낸다.
     */
    suspend fun sendMessage(chatRoomId: Long, content: String): Boolean =
        send(chatRoomId, "/pub/chat-rooms/$chatRoomId/messages", json.encodeToString(SendMessageFrame(content)))

    /**
     * 읽음 지점 갱신(SEND `/pub/chat-rooms/{id}/read`). 메시지마다 한 번씩 일어나는 일이라
     * 소켓이 붙어 있으면 REST 왕복을 아낀다. 실패해도 조용하다 — 배지 정확도보다 대화가 우선이다.
     */
    suspend fun markAsRead(chatRoomId: Long, readMessageId: Long): Boolean =
        send(chatRoomId, "/pub/chat-rooms/$chatRoomId/read", json.encodeToString(ReadFrame(readMessageId)))

    private suspend fun send(chatRoomId: Long, destination: String, body: String): Boolean {
        val current = live ?: return false
        if (current.chatRoomId != chatRoomId) return false
        return try {
            current.session.sendText(destination, body)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "전송 실패 dest=$destination: ${e.message}")
            false
        }
    }

    private suspend fun collectRoom(chatRoomId: Long, emit: suspend (ChatMessageResponse) -> Unit) {
        val token = tokens.currentAccessToken()
        if (token == null) {
            Log.d(TAG, "토큰이 없어 연결하지 않는다(미로그인)")
            return
        }
        val session = client.connect(
            url = webSocketUrl(),
            customStompConnectHeaders = mapOf("Authorization" to "Bearer $token"),
        )

        Log.d(TAG, "연결됨 roomId=$chatRoomId")
        live = LiveSession(chatRoomId, session)
        try {
            coroutineScope {
                // 서버가 내 SEND 를 거절하면(참여자 아님·1000자 초과 등) 이 통로로만 알려 온다.
                val errors = launch { collectErrors(session) }
                try {
                    // 프레임 본문을 직접 받아 파싱한다 — 서버가 보내는 JSON 이 REST 응답과 같은 shape 이라
                    // 변환 계층을 따로 끼우지 않는다. 모르는 필드는 무시하므로 서버가 필드를 늘려도 깨지지 않는다.
                    session.subscribeText("/sub/chat-rooms/$chatRoomId").collect { body ->
                        val response = runCatching { json.decodeFromString<ChatMessageResponse>(body) }.getOrNull()
                            ?: return@collect // 파싱 못 한 프레임은 흘려보낸다 — 폴링이 같은 메시지를 메운다
                        emit(response)
                    }
                } finally {
                    errors.cancel()
                }
            }
        } finally {
            live = null
            runCatching { session.disconnect() }
        }
    }

    // 에러 통로가 막혀도(구버전 서버 등) 대화는 그대로 돌아가야 한다 — 실패는 로그로만 남긴다.
    private suspend fun collectErrors(session: StompSession) {
        session.subscribeText(ERROR_DESTINATION)
            .catch { Log.d(TAG, "에러 통로 구독 실패: ${it.message}") }
            .collect { body -> Log.w(TAG, "서버가 요청을 거절함: $body") }
    }

    /** `https://host/` → `wss://host/ws-chat`. baseUrl 은 Retrofit 규약상 항상 `/` 로 끝난다 */
    private fun webSocketUrl(): String =
        baseUrl.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") + "ws-chat"
}
