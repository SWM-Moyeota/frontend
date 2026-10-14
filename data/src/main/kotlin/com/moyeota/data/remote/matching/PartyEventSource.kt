package com.moyeota.data.remote.matching

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

private const val TAG = "PartyEvents"

/** 끊긴 뒤 다시 붙기까지. 재시작 중인 서버를 연달아 두드리지 않을 만큼만 */
private const val RECONNECT_DELAY_MS = 3_000L

/**
 * 매칭방 변화 신호 — SSE(`GET /matching/rooms/{id}/events`, `text/event-stream`).
 *
 * **서버가 변경분(delta)을 싣는 방식으로 바뀌었다**(Backend #218). 예전 `changed`(= "뭔가 바뀜, 상세를
 * 다시 읽어라") 이벤트는 **사라졌고**, 이제 네 종류가 온다:
 * - `connected` — data 가 **방 상세 전체 JSON**(`PartyDetailResult`). 구독 직후 1회, **재연결 때마다 다시**.
 * - `member-joined` — `{"member": {publicId,nickname,imageUrl,badgeId,rideCount}, "status": "..."}`
 * - `member-left` — `{"publicId": "..."|null, "status": "..."}` (publicId 는 null 일 수 있다)
 * - `closed` — data 가 JSON 이 아니라 **partyId 숫자 문자열**이다. 파싱하지 않고 종결 신호로만 쓴다.
 *
 * 그리고 **30초**마다 `:ping` 주석이 온다(Backend #218 에서 5초 → 30초).
 *
 * **주석으로 살아있음을 판정하지 않는다** — okhttp-sse 가 주석을 삼켜 [EventSourceListener.onEvent] 까지
 * 오지 않으므로, 앱이 알 수 있는 건 "소켓에 바이트가 흘렀다"뿐이다. 그 사실을 쓰는 곳은 OkHttp 의
 * **readTimeout** 하나다: ping 이 30초마다 읽기를 깨우고, 45초 안에 아무것도 오지 않으면
 * `SocketTimeoutException` → [EventSourceListener.onFailure] → 이 흐름의 재연결로 이어진다
 * (`NetworkModule.SSE_READ_TIMEOUT_SECONDS`). SSE 에는 OkHttp ping(WebSocket 전용)이 듣지 않아
 * **readTimeout 을 0(무제한)으로 두면 죽은 연결을 영원히 살아있다고 믿는다** — 폴링을 끈 대기 화면이
 * 그대로 멈추므로, 그 값을 0 으로 되돌리지 말 것.
 *
 * **파싱 실패한 프레임은 스트림을 죽이지 않는다** — 경고만 남기고 건너뛴다([parsePartySseFrame]).
 * 서버가 이벤트 종류나 필드를 늘렸을 때 대기 화면이 통째로 멈추는 것보다, 한 프레임을 놓치고
 * 다음 재연결의 `connected` 스냅샷으로 보정되는 쪽이 낫다.
 *
 * **내가 방을 나가면 서버가 이벤트 없이 스트림을 끝낸다**(`PartySseRegistry.closeMember`, 다른 기기의
 * 연결 정리용). 그때 이 흐름은 [PartySseEvent.Disconnected] → [RECONNECT_DELAY_MS] 뒤 재연결 →
 * 멤버가 아니라 403 → 종료, 로 한 번 더 두드리고 멈춘다. 보통은 「그만 찾기」가 화면을 떠나며 수집을
 * 먼저 취소하므로 그 재연결조차 일어나지 않는다.
 *
 * 인증은 보통 API 와 같은 Bearer 헤더다 — [okHttpClient] 는 인증 인터셉터·401 재발급이 붙은 클라이언트에
 * readTimeout 만 늘린 것이어야 한다(`NetworkModule.streamClient`).
 *
 * 멤버가 아니거나(403) 방이 없으면(404) 다시 붙지 않는다 — 붙어 봐야 같은 답이다. 그 외 끊김은
 * [RECONNECT_DELAY_MS] 뒤 재시도하며, 그때마다 서버가 `connected` 스냅샷을 다시 주므로
 * **호출부는 놓친 변화를 메우기 위한 catch-up 조회를 할 필요가 없다.**
 *
 * **같은 partyId 로 두 번 수집하지 말 것.** 서버 `PartySseRegistry.subscribe` 가 **같은 사용자의 기존
 * 연결을 먼저 끊는다**(`closeMember`) — 한 화면이 두 번 구독하면 두 연결이 서로를 끊고, 끊긴 쪽이
 * 3초 뒤 다시 붙으며 또 상대를 끊는 **무한 재연결**이 된다. 수집 지점은 화면당 하나여야 한다.
 */
class PartyEventSource(
    private val baseUrl: String,
    okHttpClient: OkHttpClient,
) {
    private val factory = EventSources.createFactory(okHttpClient)

    fun events(partyId: Long): Flow<PartySseEvent> = flow {
        while (true) {
            var terminal = false
            try {
                connect(partyId).collect { signal ->
                    when (signal) {
                        is Signal.Event -> emit(signal.event)
                        Signal.Terminal -> terminal = true
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "연결 끊김 partyId=$partyId — ${RECONNECT_DELAY_MS}ms 뒤 재시도: ${e.message}")
            }
            if (terminal) return@flow
            emit(PartySseEvent.Disconnected)
            delay(RECONNECT_DELAY_MS)
        }
    }

    private sealed interface Signal {
        data class Event(val event: PartySseEvent) : Signal

        /** 다시 붙지 않는다 — 방이 닫혔거나(closed) 내가 볼 자격이 없다(403/404) */
        data object Terminal : Signal
    }

    // 연결 하나의 수명. 콜백 → 채널. 서버가 닫거나(closed) 오류가 나면 채널을 닫아 바깥 루프가 이어받는다.
    private fun connect(partyId: Long): Flow<Signal> = callbackFlow {
        val request = Request.Builder()
            .url("${baseUrl}api/v1/matching/rooms/$partyId/events")
            .header("Accept", "text/event-stream")
            .build()

        val source = factory.newEventSource(
            request,
            object : EventSourceListener() {
                // 여기서는 아무것도 내지 않는다 — 상태를 싣고 오는 건 바로 뒤에 올 `connected` 프레임이다.
                // 예전처럼 onOpen 에서 신호를 내면 스냅샷이 오기 전에 호출부가 상세를 한 번 더 읽는다.
                override fun onOpen(eventSource: EventSource, response: Response) {
                    Log.d(TAG, "연결됨 partyId=$partyId")
                }

                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    when (val frame = parsePartySseFrame(type, data)) {
                        is PartySseFrame.Received -> {
                            trySend(Signal.Event(frame.event))
                            // 방이 닫히면 서버가 곧 연결을 끊는다 — 다시 붙지 않는다
                            if (frame.event is PartySseEvent.Closed) {
                                trySend(Signal.Terminal)
                                close()
                            }
                        }
                        // 깨진·모르는 프레임 하나로 스트림을 끝내지 않는다
                        is PartySseFrame.Skipped -> Log.w(TAG, "프레임 건너뜀 partyId=$partyId — ${frame.reason}")
                    }
                }

                override fun onClosed(eventSource: EventSource) {
                    close()
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    val code = response?.code
                    if (code == 403 || code == 404) {
                        Log.d(TAG, "구독 거부 partyId=$partyId code=$code — 다시 붙지 않는다")
                        trySend(Signal.Terminal)
                        close()
                    } else {
                        close(t ?: IllegalStateException("SSE 실패 code=$code"))
                    }
                }
            },
        )
        awaitClose { source.cancel() }
    }
}
