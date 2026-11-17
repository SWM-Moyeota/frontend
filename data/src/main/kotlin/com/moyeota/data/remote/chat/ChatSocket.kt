package com.moyeota.data.remote.chat

import android.util.Log
import com.moyeota.data.remote.dto.ChatMessageResponse
import com.moyeota.data.session.TokenHolder
import com.moyeota.domain.diagnostics.Diagnostics
import com.moyeota.domain.diagnostics.FailureSite
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.hildan.krossbow.stomp.StompClient
import org.hildan.krossbow.stomp.StompErrorFrameReceived
import org.hildan.krossbow.stomp.subscribeText
import org.hildan.krossbow.websocket.okhttp.OkHttpWebSocketClient

private const val TAG = "ChatSocket"

/**
 * 연속 실패 1·2·3·4회째의 재연결 대기 — **고정 3초가 아니다.**
 *
 * 예전에는 상수 하나(3초)였다. 서버가 5xx 로 누워 있는 동안 한 단말이 분당 20번 WebSocket
 * 업그레이드를 시도하는 하드 루프가 됐고, **서버가 아플 때 가장 세게 때리는** 모양이었다.
 * 같은 레포의 `AppConfigViewModel.RETRY_DELAYS_MS`(2s·5s·15s)도 같은 이유로 증가 간격을 쓴다.
 *
 * 값과 모양은 SSE 쪽(`com.moyeota.data.remote.matching` 의 같은 이름 함수)과 **일부러 같게** 뒀다 —
 * 두 실시간 통로가 같은 장애에 다르게 반응할 이유가 없다. 지수식(`base shl n`)이 아니라 표인 이유도
 * 그쪽과 같다: 간격이 눈에 보이고, 상한이 계산 결과가 아니라 적어 둔 값이다.
 *
 * 횟수를 **넘어가면 마지막 값(30초)에 머문다.** 멈추지는 않는다 — 소켓이 끊긴 동안 호출부의
 * 폴링(3초)이 메시지를 메우긴 하지만, 폴링은 왕복이 잦고 읽음 처리도 REST 로 떨어진다. 결국 다시
 * 붙는 쪽이 싸다.
 */
private val RECONNECT_DELAYS_MS = longArrayOf(3_000L, 6_000L, 12_000L, 30_000L)

/**
 * 대기 시간에 더하는 **지터** 비율(0~20%). 서버가 재기동하면 끊긴 단말들이 **같은 초에** 다시 몰려
 * 또 넘어진다 — 표만으로는 그 동기화를 풀 수 없다. 음수 쪽으로 흔들지 않는 건 상한(30초)과
 * 하한(3초)이 둘 다 **지키려는 값**이기 때문이다.
 */
private const val RECONNECT_JITTER_RATIO = 0.2

/**
 * 연속 실패 [failures] 회째의 재연결 대기(ms) — **네트워크 없이 테스트할 수 있는 순수 함수다.**
 *
 * [RECONNECT_DELAYS_MS] 표를 1회째부터 차례로 읽고, 표를 다 쓰면 마지막 값에 머문다. 거기에
 * [RECONNECT_JITTER_RATIO] 만큼의 지터를 **더한다**(0 ≤ [jitter] ≤ 1 이 그 안에서의 위치다).
 *
 * [failures] 가 0 이하여도 첫 간격으로 떨어진다 — 세는 쪽이 틀려도 0ms 하드 루프가 되지 않게.
 * **구독까지 성공하면 호출부가 횟수를 0 으로 되돌린다**([ChatSocket.room]) — 그러지 않으면 몇 시간
 * 붙어 있던 연결이 한 번 끊겼을 때 30초를 기다린다.
 */
/**
 * 소켓이 **붙지 못하고 있다**는 것을 딱 한 번 보고한다.
 *
 * 한 번의 끊김은 버그가 아니다 — 지하철에서는 늘 일어난다. 그래서 [RECONNECT_DELAYS_MS] 표를
 * 한 바퀴 다 쓴 [RECONNECT_REPORT_AT] 회째에 **정확히 한 번만** 올린다. `==` 비교인 것이
 * 중요하다 — `>=` 로 쓰면 못 붙는 동안 30초마다 같은 보고가 쌓여 대시보드를 덮는다.
 *
 * **IOException 필터를 일부러 비껴간다.** 소켓 실패는 거의 다 IOException 이라 원본을 넘기면
 * [Diagnostics] 가 걸러 버린다. 여기서 신호는 예외의 종류가 아니라 **「연속 N회」라는 사실**
 * 이므로, 종류 이름만 문자열로 옮겨 담은 새 예외로 올린다. 값은 들어가지 않는다.
 */
private fun reportIfStuck(failures: Int, lastErrorType: String?) {
    if (failures != RECONNECT_REPORT_AT) return
    Diagnostics.report(
        site = FailureSite.CHAT_SOCKET_RECONNECT,
        throwable = IllegalStateException(
            "$RECONNECT_REPORT_AT consecutive failures; last=${lastErrorType ?: "no-exception"}",
        ),
    )
}

/** 백오프 표를 한 바퀴 다 쓴 시점. 표가 4칸이므로 4회째다. */
private val RECONNECT_REPORT_AT = RECONNECT_DELAYS_MS.size

internal fun reconnectDelayMs(failures: Int, jitter: Double = Random.nextDouble()): Long {
    val index = (failures.coerceAtLeast(1) - 1).coerceAtMost(RECONNECT_DELAYS_MS.lastIndex)
    val base = RECONNECT_DELAYS_MS[index]
    return base + (base * RECONNECT_JITTER_RATIO * jitter.coerceIn(0.0, 1.0)).toLong()
}

/**
 * 소켓 서버가 STOMP **ERROR 프레임의 `message` 헤더**에 싣는 고정 코드(채팅 소켓 서버 분리, 2026-11).
 * 이름이 서버 값과 1:1 이다 — 바꾸면 [of] 가 못 읽는다.
 */
internal enum class ChatSocketErrorCode {
    /** 토큰 만료 — 재발급 후 다시 붙는다 */
    TOKEN_EXPIRED,

    /** 잘못된 토큰 — **재발급하지 않는다**(해도 또 거절이라 무한 반복). 다시 로그인한다 */
    TOKEN_INVALID,

    /** API 가 잠시 답을 못 한다(배포 중 등) — 같은 토큰으로 잠시 뒤 다시 붙는다 */
    SERVER_UNAVAILABLE,

    /** 참여하지 않은 방 — 구독을 멈춘다 */
    NOT_PARTICIPANT,

    /** 잘못된 구독 경로 — 앱 버그다. 다시 붙어도 같은 답이라 멈춘다 */
    INVALID_DESTINATION,

    /** SEND 를 보냈다 — 이 앱은 더 이상 보내지 않는다(보내기·읽음은 REST). 오면 버그라 로그만 남긴다 */
    SEND_NOT_SUPPORTED,
    ;

    companion object {
        fun of(message: String?): ChatSocketErrorCode? {
            val code = message?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return entries.firstOrNull { it.name == code }
        }
    }
}

/** ERROR 코드 → 이 연결 루프가 할 일. 네트워크 없이 시험하려고 순수 함수로 뺐다 */
internal enum class ChatSocketErrorAction { RENEW_TOKEN, RELOGIN, RETRY, STOP }

internal fun ChatSocketErrorCode?.toAction(): ChatSocketErrorAction = when (this) {
    ChatSocketErrorCode.TOKEN_EXPIRED -> ChatSocketErrorAction.RENEW_TOKEN
    ChatSocketErrorCode.TOKEN_INVALID -> ChatSocketErrorAction.RELOGIN
    ChatSocketErrorCode.NOT_PARTICIPANT,
    ChatSocketErrorCode.INVALID_DESTINATION,
    -> ChatSocketErrorAction.STOP
    // 코드가 없는 실패(네트워크 끊김·타임아웃)도 여기 — 백오프 뒤 다시 붙는다
    ChatSocketErrorCode.SERVER_UNAVAILABLE,
    ChatSocketErrorCode.SEND_NOT_SUPPORTED,
    null,
    -> ChatSocketErrorAction.RETRY
}

/**
 * 예외 메시지 폴백용 패턴 — **메시지 전체가 코드**이거나 **`…: 코드` 로 끝날 때만** 맞춘다
 * (krossbow 의 `StompErrorFrameReceived` 메시지 모양이 `STOMP ERROR frame received: <message 헤더>` 다).
 * 메시지 중간에 코드 이름이 인용된 다른 오류를 그 코드로 오인하지 않게 「어딘가에 들어 있으면」은 쓰지 않는다.
 * 사슬을 돌 때마다 컴파일하지 않도록 한 번만 만든다.
 */
private val ERROR_CODE_PATTERNS: Map<ChatSocketErrorCode, Regex> =
    ChatSocketErrorCode.entries.associateWith { Regex("""(^|:\s*)${it.name}\s*$""") }

/**
 * 실패에서 서버 ERROR 코드를 꺼낸다. krossbow 는 ERROR 프레임을 [StompErrorFrameReceived] 로 올리지만
 * 연결 단계에서는 다른 예외로 감싸 올 수 있어 원인 사슬을 끝까지 본다. 프레임 객체가 없으면 예외 메시지가
 * 코드로 끝나는지 본다([ERROR_CODE_PATTERNS]).
 */
internal fun Throwable.chatSocketErrorCode(): ChatSocketErrorCode? {
    var current: Throwable? = this
    var depth = 0
    while (current != null && depth++ < 8) {
        if (current is StompErrorFrameReceived) {
            ChatSocketErrorCode.of(current.frame.headers.message)?.let { return it }
        }
        val text = current.message
        if (text != null) {
            ERROR_CODE_PATTERNS.entries.firstOrNull { (_, pattern) -> pattern.containsMatchIn(text) }
                ?.let { return it.key }
        }
        current = current.cause
    }
    return null
}

/**
 * 채팅 실시간 **받기 전용** — STOMP over WebSocket.
 *
 * 채팅 소켓 서버가 API 서버에서 분리됐다(2026-11). 소켓 서버는 연결 유지와 전달만 한다 —
 * **SEND 를 받지 않는다**(`ERROR SEND_NOT_SUPPORTED`). 보내기·읽음은 REST 이고([com.moyeota.data.remote.ChatApi]),
 * 그래서 예전의 `sendMessage`·`markAsRead`(SEND `/pub/…`)와 거절 통로(`/user/queue/errors`) 구독은 지웠다 —
 * 소켓 서버에 없는 경로를 구독하면 `INVALID_DESTINATION` 으로 연결째 끊긴다.
 *
 * 서버는 메시지가 저장될 때마다 `/sub/chat-rooms/{id}` 로 밀어 준다. 페이로드는 REST 조회와 **같은 shape**
 * (`ChatMessageResult` + `seq`)이라 [ChatMessageResponse] 를 그대로 재사용한다. 순서를 지키려고 서버가 늦게 온
 * 번호를 **버리므로**, 호출부가 `seq` 의 빈 번호를 REST 로 메워야 한다.
 *
 * 인증은 STOMP **CONNECT 프레임의 `Authorization: Bearer` 헤더**다. ERROR 프레임의 `message` 헤더로 오는
 * 고정 코드([ChatSocketErrorCode])에 따라 재발급·재로그인·재시도·중단을 가른다([toAction]).
 *
 * **폴링을 대체하지 않고 앞선다.** 이 흐름이 끊겨도 호출부의 폴링이 메시지를 메우므로,
 * 연결 실패는 조용히 재시도만 하고 화면에 에러를 올리지 않는다.
 *
 * @param renewAccessToken 거절당한 액세스 토큰을 넘기면 새 토큰을 돌려준다(못 받으면 null).
 *   HTTP 401 재발급과 **같은 잠금**을 써야 한다([com.moyeota.data.remote.auth.TokenAuthenticator.renew]).
 */
class ChatSocket(
    private val baseUrl: String,
    okHttpClient: OkHttpClient,
    private val tokens: TokenHolder,
    private val renewAccessToken: (String) -> String? = { null },
) {
    private val json = Json { ignoreUnknownKeys = true }

    // 인증 인터셉터가 붙은 apiClient 를 그대로 쓰지 않는다 — STOMP 인증은 프레임 헤더라 HTTP 헤더는
    // 필요 없고, 401 재발급 Authenticator 가 업그레이드 요청에 끼어들 이유도 없다.
    private val client = StompClient(OkHttpWebSocketClient(okHttpClient))

    /** 연결 하나에서 오는 신호. 구독이 걸린 순간([Connected])을 알려야 호출부가 그 전의 공백을 메울 수 있다 */
    sealed interface RoomSignal {
        data object Connected : RoomSignal
        data class Message(val response: ChatMessageResponse) : RoomSignal
        data object Disconnected : RoomSignal

        /** 서버가 구독을 거절했다(참여자 아님·잘못된 경로·잘못된 토큰). 흐름은 이 뒤에 끝난다 */
        data object Stopped : RoomSignal
    }

    /**
     * 이 방의 새 메시지 흐름. 수집이 취소될 때까지 유지되고, 끊기면 [reconnectDelayMs] 뒤 다시 붙는다
     * (연속 실패마다 늘어나는 간격 + 소량 지터 — 고정 3초가 아니다).
     * 미로그인이면 연결하지 않고 다음 주기를 기다린다(로그인 뒤 자연히 붙는다).
     *
     * 서버 ERROR 코드별 동작([toAction]):
     * - `TOKEN_EXPIRED` — 재발급 후 **바로** 다시 붙는다. 재발급 직후 또 만료면(서버 시계 문제 등) 무한 반복을
     *   막으려고 연속으로는 한 번만 바로 붙고, 그다음은 백오프를 탄다. 재발급이 거절돼 세션이 비면 끝난다.
     * - `TOKEN_INVALID` — 재발급하지 않고 세션을 비운다([TokenHolder.onSessionExpired]) — 앱이 로그인 화면으로 간다.
     * - `NOT_PARTICIPANT`·`INVALID_DESTINATION` — [RoomSignal.Stopped] 를 내고 끝난다.
     * - `SERVER_UNAVAILABLE`·코드 없음 — 같은 토큰으로 백오프 뒤 다시 붙는다.
     *
     * **[Dispatchers.IO] 에서 돈다.** 업스트림 본문이 [TokenHolder.currentAccessToken] 과 [renewAccessToken] 을
     * 부르는데 둘 다 블로킹이다(디스크 복원 대기·재발급 왕복). 수집은 `viewModelScope`(메인)에서 일어나므로
     * `flowOn` 이 없으면 그 블로킹이 메인 스레드에서 일어난다. `flowOn` 은 업스트림만 옮긴다.
     */
    fun room(chatRoomId: Long): Flow<RoomSignal> = flow {
        // 연속 실패 횟수 — 붙는 데 성공하면(Connected) 0 으로 되돌린다
        var failures = 0
        // 마지막 실패의 **종류만** 기억한다. 예외 객체나 메시지를 들고 있지 않는 이유는
        // 보고에 서버 응답 조각 같은 값이 섞여 나가지 않게 하기 위해서다.
        var lastErrorType: String? = null
        // 재발급 직후 바로 붙은 적이 있는가 — 연속 두 번은 바로 붙지 않는다(무한 반복 방지)
        var renewedJustNow = false
        while (true) {
            val token = tokens.currentAccessToken()
            if (token == null) {
                // 미로그인. 조건이 그대로면 다음 시도도 같은 결과라 **실패로 센다**(하드 루프 방지)
                Log.d(TAG, "토큰이 없어 연결하지 않는다(미로그인)")
                failures++
            } else {
                try {
                    collectRoom(chatRoomId, token) { signal ->
                        // 구독까지 성공했으면 백오프를 처음으로 되돌린다. 긴 연결이 끊긴 것과
                        // 「붙지도 못하는 상태」를 같게 다루면 멀쩡한 서버에도 30초를 기다린다.
                        if (signal is RoomSignal.Connected) {
                            failures = 0
                            lastErrorType = null
                            renewedJustNow = false
                        }
                        emit(signal)
                    }
                    // 예외 없이 돌아오는 경우(구독 종료)도 실패로 센다
                    failures++
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val code = e.chatSocketErrorCode()
                    when (code.toAction()) {
                        ChatSocketErrorAction.STOP -> {
                            Log.w(TAG, "구독 거절 roomId=$chatRoomId code=$code — 다시 붙지 않는다")
                            emit(RoomSignal.Disconnected)
                            emit(RoomSignal.Stopped)
                            return@flow
                        }
                        ChatSocketErrorAction.RELOGIN -> {
                            Log.w(TAG, "잘못된 토큰(TOKEN_INVALID) — 재발급하지 않고 세션을 비운다")
                            tokens.onSessionExpired()
                            emit(RoomSignal.Disconnected)
                            emit(RoomSignal.Stopped)
                            return@flow
                        }
                        ChatSocketErrorAction.RENEW_TOKEN -> {
                            if (!renewedJustNow && renewAccessToken(token) != null) {
                                Log.d(TAG, "토큰 만료 — 재발급 후 바로 다시 붙는다 roomId=$chatRoomId")
                                renewedJustNow = true
                                emit(RoomSignal.Disconnected)
                                continue
                            }
                            // 재발급을 서버가 거절해 세션이 비워졌다 — 재로그인 말고 길이 없으니 TOKEN_INVALID 처럼 끝낸다.
                            // 계속 돌면 미로그인 분기로 30초마다 영원히 깨어난다.
                            if (tokens.currentAccessToken() == null) {
                                Log.w(TAG, "재발급 거절로 세션이 비었다 — 구독을 끝낸다")
                                emit(RoomSignal.Disconnected)
                                emit(RoomSignal.Stopped)
                                return@flow
                            }
                            failures++
                            lastErrorType = "TOKEN_EXPIRED"
                        }
                        ChatSocketErrorAction.RETRY -> {
                            // 「연속 두 번 바로 재발급하지 않는다」의 연속은 TOKEN_EXPIRED 끼리다. 다른 실패가 끼면 끊는다 —
                            // 안 그러면 네트워크 오류로 백오프하는 사이 토큰이 또 만료됐을 때 재발급 없이 만료 토큰으로만 돈다.
                            renewedJustNow = false
                            if (code == ChatSocketErrorCode.SEND_NOT_SUPPORTED) {
                                Log.w(TAG, "SEND_NOT_SUPPORTED — 보내기는 REST 여야 한다(버그)")
                            }
                            failures++
                            lastErrorType = code?.name ?: e.javaClass.name
                        }
                    }
                    Log.d(TAG, "연결 끊김 roomId=$chatRoomId — ${failures}회째 실패 code=$code: ${e.message}")
                }
            }
            reportIfStuck(failures, lastErrorType)
            emit(RoomSignal.Disconnected)
            val wait = reconnectDelayMs(failures)
            Log.d(TAG, "재연결 대기 roomId=$chatRoomId — ${wait}ms (${failures}회째)")
            delay(wait)
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun collectRoom(chatRoomId: Long, token: String, emit: suspend (RoomSignal) -> Unit) {
        val session = client.connect(
            url = webSocketUrl(),
            customStompConnectHeaders = mapOf("Authorization" to "Bearer $token"),
        )
        Log.d(TAG, "연결됨 roomId=$chatRoomId")
        try {
            // 프레임 본문을 직접 받아 파싱한다 — 서버가 보내는 JSON 이 REST 응답과 같은 shape 이라
            // 변환 계층을 따로 끼우지 않는다. 모르는 필드는 무시하므로 서버가 필드를 늘려도 깨지지 않는다.
            // subscribeText 는 suspend 라 반환 시점에 SUBSCRIBE 가 이미 나갔다. 그 **뒤에** Connected 를 내야
            // 호출부의 공백 조회와 소켓 수신 사이에 틈이 없다(겹치는 메시지는 호출부가 id 로 합친다).
            val messages = session.subscribeText("/sub/chat-rooms/$chatRoomId")
            emit(RoomSignal.Connected)
            messages.collect { body ->
                val response = runCatching { json.decodeFromString<ChatMessageResponse>(body) }.getOrNull()
                    ?: return@collect // 파싱 못 한 프레임은 흘려보낸다 — seq 빈 번호 채우기가 메운다
                emit(RoomSignal.Message(response))
            }
        } finally {
            runCatching { session.disconnect() }
        }
    }

    /** `https://host/` → `wss://host/ws-chat`. baseUrl 은 Retrofit 규약상 항상 `/` 로 끝난다 */
    private fun webSocketUrl(): String =
        baseUrl.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") + "ws-chat"
}
