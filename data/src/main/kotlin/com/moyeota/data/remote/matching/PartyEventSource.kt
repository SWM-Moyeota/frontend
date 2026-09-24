package com.moyeota.data.remote.matching

import android.util.Log
import com.moyeota.domain.model.PartyEvent
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

/** 서버 이벤트 이름(`PartySseListener`). 문자열이 계약이다 */
private const val EVENT_CHANGED = "changed"
private const val EVENT_CLOSED = "closed"

/**
 * 매칭방 변화 신호 — SSE(`GET /matching/rooms/{id}/events`, `text/event-stream`).
 *
 * 서버는 `changed`/`closed` 이벤트에 partyId 만 싣고, 15초마다 `:ping` 주석으로 살아있음을 알린다
 * (CloudFront 오리진 타임아웃 30초 대응). `closed` 뒤에는 서버가 연결을 끝낸다.
 *
 * 인증은 보통 API 와 같은 Bearer 헤더다 — [okHttpClient] 는 인증 인터셉터·401 재발급이 붙은 클라이언트에
 * readTimeout 만 끈 것이어야 한다(상시 연결이라 "응답이 늦다"는 개념이 없다).
 *
 * 멤버가 아니거나(403) 방이 없으면(404) 다시 붙지 않는다 — 붙어 봐야 같은 답이다. 그 외 끊김은
 * [RECONNECT_DELAY_MS] 뒤 재시도하며, 그때마다 [PartyEvent.Connected] 를 다시 내 호출부가 놓친 변화를 메우게 한다.
 */
class PartyEventSource(
    private val baseUrl: String,
    okHttpClient: OkHttpClient,
) {
    private val factory = EventSources.createFactory(okHttpClient)

    fun events(partyId: Long): Flow<PartyEvent> = flow {
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
            emit(PartyEvent.Disconnected)
            delay(RECONNECT_DELAY_MS)
        }
    }

    private sealed interface Signal {
        data class Event(val event: PartyEvent) : Signal

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
                override fun onOpen(eventSource: EventSource, response: Response) {
                    Log.d(TAG, "연결됨 partyId=$partyId")
                    trySend(Signal.Event(PartyEvent.Connected))
                }

                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    when (type) {
                        EVENT_CHANGED -> trySend(Signal.Event(PartyEvent.Changed))
                        EVENT_CLOSED -> {
                            trySend(Signal.Event(PartyEvent.Closed))
                            trySend(Signal.Terminal)
                            close()
                        }
                        // 모르는 이벤트는 흘려보낸다 — 서버가 종류를 늘려도 깨지지 않는다. 주석(:ping)은 여기 오지 않는다
                        else -> Log.d(TAG, "모르는 이벤트 무시 type=$type")
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
