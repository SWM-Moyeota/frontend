package com.moyeota.presentation.feature.matching

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.moyeota.domain.model.PartyEvent
import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RideStatus
import com.moyeota.domain.model.User
import com.moyeota.domain.repository.RideRepository
import com.moyeota.presentation.core.BackStateScaffold
import com.moyeota.presentation.core.ErrorBox
import com.moyeota.presentation.core.LoadingBox
import com.moyeota.presentation.core.location.rememberMyLocationState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * **화면이 보이고, SSE 가 끊긴 동안에만** 도는 폴백 폴링 간격.
 *
 * 가시성 조건은 나중에 붙었다([fallbackPollStep]). 예전에는 이 루프가 `viewModelScope` 에만 묶여 있어서
 * 앱을 내려놓아도 돌았고, 그때 OS 가 네트워크를 조여 SSE 가 죽으면 **3초 재연결 ↔ 6초 폴백**이 서로
 * 틈을 메우며 되풀이됐다(QA 123 D-3: 백그라운드 75초에 9건 — `/events` 5 + 상세 4, 분당 약 7건).
 *
 * 평상시 이 화면은 서버를 **주기적으로 부르지 않는다** — 방 상태는 SSE 변경분
 * ([RideRepository.observePartyEvents])이 전해 준다. 그래도 폴백을 남긴 이유는 그 스트림이
 * **best-effort** 라서다: 서버가 커밋 후 단일 스레드 큐로 발행하고 실패해도 재발행하지 않으며
 * (Redis Pub/Sub 도 at-most-once), `text/event-stream` 을 막는 망·프록시도 있다.
 * 그래서 정책은 채팅 소켓과 같다 — **살아 있으면 0회, 죽었을 때만.**
 *
 * 예전(4초)보다 느슨한 이유: 지문(fingerprint)과 가벼운 상태 조회 `GET /rooms/{id}/status` 가
 * 서버에서 사라져(Backend #221) 「바뀌었나」를 싸게 묻는 길이 없다. 폴백 수단은 서버 쿼리 3개에 경로
 * 폴리라인까지 실려 오는 **방 상세 하나**뿐이라, 같은 간격을 유지하면 부하가 3배가 된다.
 *
 * 그래서 값은 25·26 이 쓰는 상세 폴링의 **공통 하한**
 * ([com.moyeota.presentation.core.PARTY_DETAIL_POLL_FLOOR_MS])에 맞춘다 — 그 아래로 내리면 옛 구조보다
 * 부하가 커진다. 21 은 그 두 화면과 달리 **끊긴 동안에만** 도므로 하한을 그대로 쓰고 더 늦추지는 않는다:
 * 끊긴 상태에서는 이게 유일한 갱신 수단이라, 들고 나는 사람이 보이기까지의 지연이 그만큼 길어진다.
 */
private const val PARTY_FALLBACK_POLL_INTERVAL_MS = 6_000L

class MatchWaitingViewModel(
    private val repository: RideRepository,
    private val partyId: Long,
) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data class Success(val ride: Ride) : UiState
        data class Error(val message: String) : UiState
    }

    /**
     * 액션 결과. 「그만 찾기」(left)와 1차 배포 모드의 「합승 완료」(finished) — 둘 다 성공하면 방을 떠난다.
     * 준비/매칭 시작은 백엔드에서 삭제됐다.
     */
    data class ActionState(
        val inProgress: Boolean = false,
        val errorMessage: String? = null,
        val left: Boolean = false,
        val finished: Boolean = false,
    )

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _actionState = MutableStateFlow(ActionState())
    val actionState: StateFlow<ActionState> = _actionState.asStateFlow()

    /**
     * 서버가 **방이 닫혔다**고 알려 왔다([PartyEvent.Closed]). 누가 닫았는지(합승 완료·30분 만료·
     * 마지막 멤버 이탈)는 화면 동작이 같아 구분하지 않는다 — Route 가 받아 홈으로 보낸다.
     *
     * 확인용 상세 재조회를 하지 않는다: 서버는 이 신호 뒤에 구독을 끊고, 닫힌 방을 한 번 더 읽어도
     * 「닫혔다」는 사실만 다시 확인할 뿐이다(예전에는 FINISHED/CANCELED 를 상세로 읽어 와야 알았다).
     */
    private val _partyClosed = MutableStateFlow(false)
    val partyClosed: StateFlow<Boolean> = _partyClosed.asStateFlow()

    /**
     * SSE 가 살아 있는가 — [PartyEvent.Snapshot] 을 받으면 true, [PartyEvent.Disconnected] 와
     * 스트림 종료·구독 취소에서 false. **폴백 폴링이 이번 주기에 읽을지의 스위치다**([shouldPollFallback]).
     */
    @Volatile
    private var realtimeAlive = false

    /**
     * 화면이 지금 **보이는가** — SSE 구독([observeEvents])과 폴백 폴링([observeFallbackPolling])의
     * **공통 스위치**다. 보이지 않으면 둘 다 멈추고, 돌아오면 둘 다 다시 붙는다.
     *
     * ### 왜 끊어도 안전한가 ★
     * 재연결할 때마다 서버가 `connected` 로 **방 상세 전체**를 다시 준다([PartyEvent.Snapshot]).
     * 그래서 가려진 동안 끊고 있다가 복귀해 다시 붙으면 **그 순간 상태가 통째로 맞춰진다** — 끊긴 사이의
     * 변화를 따라잡는 catch-up 조회가 필요 없다. 이것이 이번 SSE 설계의 핵심이고(`117` §2 표의 1번 근거),
     * 이 게이트가 성립하는 근거다. 증분(`member-joined`/`member-left`)은 바탕이 있을 때만 쓰는 값이라
     * 「놓친 증분」을 복원할 필요도 없다 — 바탕 자체가 새로 온다.
     *
     * ### 왜 끊는 게 이득인가
     * 보이지 않는 동안 SSE 를 유지해도 **사용자가 얻는 것이 없다**: 알림은 FCM 이 띄우는데 **멤버 합류에는
     * FCM 이 없고**, 화면도 보이지 않는다. 반대로 비용은 확실하다 — 앱을 내려놓으면 OS 가 네트워크를 조여
     * SSE 가 죽고, 3초 뒤 재연결하고, 그 틈을 6초 폴백이 메우고, 다시 죽는다
     * (QA 123 D-3 실측: 백그라운드 **75초에 9건**, 분당 약 7건).
     *
     * ### 기본값이 true 인 이유
     * 이 값을 내려 주는 배선([onScreenStart]·[onScreenStop])은 Route 에 있다. 기본값을 false 로 두면
     * 배선이 빠진 채 머지됐을 때 **방 상태가 영영 갱신되지 않는** 기능 손실이 된다 — true 면 최악이
     * 「고쳐지지 않은 상태 유지」다([com.moyeota.presentation.core.ActivePartyViewModel] 의 `hostVisible`
     * 가 같은 이유로 true 다).
     *
     * 플래그가 아니라 [MutableStateFlow] 인 이유는 **수집과 같은 흐름에 묶기** 위해서다: `collectLatest`
     * 가 가려지는 순간 진행 중인 구독·조회와 `delay` 를 **함께** 취소하고, 다시 보이면 같은 패스를 새로
     * 시작한다(17 탐색 `ExploreViewModel.visible` 과 같은 모양).
     */
    private val visible = MutableStateFlow(true)

    /**
     * SSE 스트림이 **종결**로 끝났다 — 방 닫힘(`closed`)·403(멤버 아님)·404(방 없음), 또는 스트림 자체가
     * 없는 저장소(더미). 그 밖의 끊김은 데이터 계층이 3초 뒤 스스로 다시 붙으므로 여기까지 오지 않는다.
     *
     * 가시성 게이트가 **되살려야 하는 것과 그러면 안 되는 것**을 가르는 값이다: 가려져서 취소된 구독은
     * 복귀 때 다시 붙지만, 종결로 끝난 구독은 다시 붙어도 같은 답이라 붙지 않는다(게이트 이전 동작과 동일).
     *
     * **StateFlow 인 이유**: 폴백 루프의 `combine` 입력이라서다. 평범한 변수면 가려진 사이에 스트림이
     * 끝나도 복귀 패스가 그 사실을 모른 채 확정돼, 다시 붙을 스트림이 없는데도 6초를 기다린다.
     */
    private val streamEnded = MutableStateFlow(false)

    /**
     * 폴백 루프가 **제 할 일을 끝냈다** — 방이 대기 단계를 벗어났다([isWaitingForMembers] false:
     * 배차 시작·방 종료).
     *
     * 게이트를 넣었다고 끝난 루프가 되살아나면 회귀다. 가시성은 「멈췄다 다시 켜는」 스위치지만
     * 이 값은 「끝났다」는 사실이라 복귀로 되돌아가지 않는다.
     */
    @Volatile
    private var fallbackDone = false

    /**
     * 실시간 이벤트를 화면에 반영한 횟수. 값 자체에는 뜻이 없고, **상세 조회가 늦게 도착해**
     * 더 새로운 실시간 상태를 과거로 되돌리는 걸 막는 표식으로만 쓴다([readDetail]).
     */
    @Volatile
    private var appliedEvents = 0

    init {
        refresh()
        observeEvents()
        observeFallbackPolling()
    }

    /**
     * 화면이 보이기 시작했다 — **즉시 재구독**하고 폴백 폴링을 재개한다.
     *
     * 이 길로 오는 것: 첫 진입, 앱 복귀, 위에 올라와 있던 화면(22 상세·24 채팅방)의 pop.
     *
     * **즉시성**: 재구독이 곧 스냅샷이다. 패스가 다시 시작되는 순간 `connect` 가 나가고, 서버가
     * `connected` 에 방 상세 전체를 실어 주므로 6초 폴백 주기를 기다리는 구간이 없다(17 탐색은 같은
     * 구조에서 복귀 첫 조회가 58ms 였다 — QA 123 A3).
     */
    fun onScreenStart() {
        visible.value = true
    }

    /**
     * 화면이 가려졌다 — SSE 구독과 폴백 폴링을 **함께** 멈춘다.
     *
     * 이 길로 오는 것: 위에 다른 화면이 올라옴(22 상세·24 채팅방 — 엔트리가 CREATED 로 내려간다),
     * 앱 백그라운드(호스트 ON_STOP).
     *
     * 들고 있던 방 상태는 **건드리지 않는다.** 돌아올 때 보던 화면을 먼저 보여주고 스냅샷으로 덮는 것이
     * 맞고, 로딩으로 깜빡이게 할 이유가 없다.
     */
    fun onScreenStop() {
        visible.value = false
    }

    /**
     * 첫 진입과 에러 화면의 「다시 시도」 — **주기 폴링이 아니라 1회 조회**다.
     *
     * SSE 첫 스냅샷과 경쟁하지만 먼저 온 쪽으로 그리고 늦게 온 조회 결과는 버린다([readDetail]).
     * 이 1회를 남겨 둔 이유는 스트림이 **실패를 말해 주지 않기** 때문이다: 403/404 면 조용히 끝나고
     * 그 밖의 실패는 저장소가 재연결로 삼킨다. 조회가 없으면 에러 화면과 「다시 시도」를 띄울 근거가 없고,
     * 스트림이 없는 저장소(더미)에서는 영원히 로딩이다.
     */
    fun refresh() {
        viewModelScope.launch {
            // 이미 실시간 상태가 떠 있으면 로딩으로 덮지 않는다(스냅샷이 먼저 온 경우)
            if (_uiState.value !is UiState.Success) _uiState.value = UiState.Loading
            if (!readDetail() && _uiState.value !is UiState.Success) {
                _uiState.value = UiState.Error("매칭 정보를 불러오지 못했어요")
            }
        }
    }

    /**
     * 방 상태의 **주 경로** — SSE 변경분 구독([RideRepository.observePartyEvents]).
     *
     * **화면이 보이는 동안에만 구독한다**([visible]) — 가려지면 끊고, 돌아오면 다시 붙는다. 끊어도
     * 안전한 근거(재연결 = 스냅샷 = 상태 전체 재동기)는 [visible] 의 KDoc 에 적어 두었다.
     *
     * ⚠️ 수집 지점은 이 화면에서 **여기 하나뿐이어야 한다.** 서버는 같은 사용자의 기존 구독을 끊고 새 연결을
     * 받으므로(`PartySseRegistry.closeMember`), 같은 방을 두 곳에서 수집하면 두 연결이 서로를 끊고 3초마다
     * 번갈아 재연결하는 무한 루프가 된다. 그래서 `init` 에서 `launch` **한 번만** 걸고
     * ViewModel(키 `waiting-{partyId}`)이 사는 동안 그 하나를 유지한다 — 재구성·`LaunchedEffect` 로
     * 다시 걸지 않는다.
     *
     * 가시성 게이트가 그 성질을 깨지 않는 이유: `collectLatest` 는 새 값을 받으면 **앞 패스를 취소하고
     * 끝날 때까지 기다린 뒤**(`cancelAndJoin`) 다음 패스를 시작한다. 앞 패스가 끝난다는 건
     * `PartyEventSource` 의 `awaitClose { source.cancel() }` 까지 돌았다는 뜻이라, 어느 순간에도
     * **살아 있는 수집은 0개 또는 1개**다. 가시성이 빠르게 토글돼도 두 수집이 겹치지 않는다.
     *
     * 받는 순서는 늘 [PartyEvent.Snapshot] → 변경분이고 **재연결 때도 스냅샷이 먼저** 온다. 그래서
     * 끊긴 사이 놓친 변화를 메우는 catch-up 조회가 없다 — 스냅샷이 방을 통째로 덮는다.
     * 가시성 때문에 끊었다 붙는 것도 서버 입장에선 똑같은 재연결이다.
     *
     * 수집이 **스스로** 끝나는 건 종결뿐이다(방 닫힘·403 멤버 아님·404 방 없음). 그때는 다시 붙어도 같은
     * 답이라 복귀해도 붙지 않고([streamEnded]) 폴백이 갱신을 맡는다 — 내가 방을 나간 경우라면 Route 가
     * 이미 화면을 떠났고 수집도 함께 취소된다.
     */
    private fun observeEvents() {
        viewModelScope.launch {
            visible.collectLatest { shown ->
                if (!shouldObservePartyEvents(visible = shown, streamEnded = streamEnded.value)) {
                    return@collectLatest
                }
                try {
                    repository.observePartyEvents(partyId).collect { event -> applyEvent(event) }
                    // 여기까지 왔으면 스트림이 **종결**로 끝났다. 화면이 가려져 취소된 경우는
                    // CancellationException 으로 빠져나가 이 줄을 지나지 않는다 — 그래서 복귀 때 다시 붙는다.
                    streamEnded.value = true
                } finally {
                    // 종결이든 취소든 실시간 근거가 사라진다. 취소된 뒤에도 true 로 남겨 두면 복귀해서
                    // 재연결이 실패했을 때 폴백이 영영 돌지 않아 화면이 멈춘다.
                    realtimeAlive = false
                }
            }
        }
    }

    /**
     * 이벤트 하나를 화면 상태로 옮긴다 — **상세 재조회 없이**.
     *
     * | 이벤트 | 하는 일 |
     * |---|---|
     * | [PartyEvent.Snapshot] | 들고 있던 방을 **통째로 교체**(가장 신뢰할 수 있는 값) · 폴백 끔 |
     * | [PartyEvent.MemberJoined] | 멤버 목록에 더하고(같은 publicId 는 교체) status 갱신 ([Ride.withMemberJoined]) |
     * | [PartyEvent.MemberLeft] | publicId 로 제거 + status 갱신. **publicId 가 null 일 때만** 상세 1회 조회 |
     * | [PartyEvent.Closed] | 닫힘 신호만 세운다 → Route 가 홈으로 |
     * | [PartyEvent.Disconnected] | 폴백 폴링 켬. 상태는 **그대로 둔다** — 곧 올 스냅샷이 보정한다 |
     */
    private suspend fun applyEvent(event: PartyEvent) {
        when (event) {
            is PartyEvent.Snapshot -> {
                realtimeAlive = true
                appliedEvents++
                _uiState.value = UiState.Success(event.ride)
            }
            // member 의 isMe 는 상세와 같은 근거(publicId == 세션 uuid)로 이미 채워져 온다 — 다시 판정하지 않는다
            is PartyEvent.MemberJoined -> applyDelta { it.withMemberJoined(event.member, event.status) }
            is PartyEvent.MemberLeft -> {
                val publicId = event.publicId
                if (publicId == null) {
                    // 누가 나갔는지 서버도 모른다 — 지울 멤버를 특정할 수 없는, 상세를 다시 읽는 **유일한** 경우
                    applyStatus(event.status)
                    readDetail()
                } else {
                    applyDelta { it.withMemberLeft(publicId, event.status) }
                }
            }
            PartyEvent.Closed -> {
                realtimeAlive = false
                _partyClosed.value = true
            }
            PartyEvent.Disconnected -> realtimeAlive = false
        }
    }

    /**
     * 변경분을 들고 있는 방에 적용한다. 바탕이 없으면(로딩·에러) 적용할 대상이 없어 상세를 1회 읽는다 —
     * 증분은 **바탕이 있을 때만** 쓸 수 있다.
     *
     * 적용 결과가 [hasMemberDrift] 면(서버가 준 status 와 내 멤버 목록이 모순이면) 상세를 1회 읽어 맞춘다.
     * 증분이 유실됐다는 뜻이고, 그때는 **목록이 아니라 서버를 믿는다**. 주기 호출이 아니라 모순을 본
     * 그 순간 1회이며, 읽어 온 결과에는 이 검사를 다시 걸지 않으므로 되돌이가 되지 않는다.
     */
    private suspend fun applyDelta(transform: (Ride) -> Ride) {
        val held = (_uiState.value as? UiState.Success)?.ride
        if (held == null) {
            readDetail()
            return
        }
        val next = transform(held)
        appliedEvents++
        _uiState.value = UiState.Success(next)
        if (hasMemberDrift(next)) readDetail()
    }

    /** 멤버 목록은 그대로 두고 서버가 준 status 만 반영한다 — 지울 멤버를 특정할 수 없을 때(publicId null). */
    private fun applyStatus(status: RideStatus) {
        val held = (_uiState.value as? UiState.Success)?.ride ?: return
        appliedEvents++
        _uiState.value = UiState.Success(held.copy(status = status))
    }

    /**
     * 방 상세를 1회 읽어 화면에 반영한다. 돌려주는 값은 **읽기 성공 여부**다(에러 화면 판단용).
     *
     * 읽는 동안 실시간 이벤트가 반영됐으면([appliedEvents] 가 달라졌으면) 결과를 **버린다** — 왕복이 느릴 때
     * 늦게 도착한 조회 결과가 더 새 실시간 상태를 과거로 되돌리는 걸 막는다. 실패는 조용하다
     * (다음 이벤트·다음 폴백 주기가 메운다).
     *
     * 내용이 똑같은 상세를 다시 흘려 넣어도 재구성은 일어나지 않는다 — [UiState.Success] 와 [Ride] 가
     * data class 라 [MutableStateFlow] 가 같은 값으로 보고 내보내지 않는다
     * (25·26 이 [com.moyeota.presentation.core.shouldPublishDetail] 로 명시해 둔 것과 같은 절약이다).
     */
    private suspend fun readDetail(): Boolean {
        val mark = appliedEvents
        val ride = runCatching { repository.getPartyDetail(partyId) }.getOrNull() ?: return false
        if (appliedEvents == mark) _uiState.value = UiState.Success(ride)
        return true
    }

    /**
     * 끊긴 동안의 **폴백** 폴링 — 그리고 **화면이 보이는 동안에만**.
     *
     * 판정이 두 층이다. 섞지 않는 이유는 보는 값의 수명이 다르기 때문이다.
     *
     * | 함수 | 묻는 것 | 언제 바뀌나 |
     * |---|---|---|
     * | [fallbackPollStep] | 루프를 **돌릴지 / 첫 조회를 미룰지** | 가시성·방 닫힘·루프 종료 (패스 단위) |
     * | [shouldPollFallback] | 이번 주기에 **읽을지** | SSE 생사·화면에 방이 있는지 (주기 단위) |
     *
     * SSE 가 살아 있고 화면에 방이 떠 있으면 **한 번도 서버를 부르지 않는다**(평상시 0회). 거기에
     * 가시성이 더해져, 가려진 동안에는 **깨어나 아무 일도 안 하는 주기조차 남지 않는다** — `collectLatest`
     * 가 진행 중인 조회와 `delay` 를 함께 취소한다(17 탐색 `ExploreViewModel.observeRequests` 와 같은 모양).
     *
     * 폴백 수단은 방 상세뿐이다 — 지문과 `GET /rooms/{id}/status` 가 서버에서 사라져(Backend #221)
     * 「바뀌었나」를 싸게 묻는 길이 없다. 대신 상세를 **통째로 교체**하는 건 스냅샷과 같은 처리라 멱등하고,
     * 내용이 같으면 [MutableStateFlow] 가 같은 값으로 보아 흘리지 않으므로 재구성도 일어나지 않는다.
     *
     * **기존 종료 조건은 그대로다.** 방이 닫히면([_partyClosed]) 루프가 끝나고, 방이 대기 단계를 벗어나면
     * ([isWaitingForMembers] false) 폴백이 끝난다([fallbackDone]) — 둘 다 복귀로 되살아나지 않는다.
     * 화면 이동은 Route 의 `LaunchedEffect` 가 맡고, 조회 실패는 조용히 다음 주기를 기다린다.
     */
    private fun observeFallbackPolling() {
        viewModelScope.launch {
            // streamEnded 가 combine 입력인 이유: 가려진 사이에 스트림이 종결되면(방 닫힘·403·404)
            // 복귀 때 다시 붙을 스트림이 없다. 평범한 변수로 두면 패스가 이미 WAIT_THEN_READ 로
            // 확정된 뒤라 READ_NOW 가 영영 타지 않고, 그 전이가 늘 6초 늦는다(QA 125 D-2).
            combine(visible, _partyClosed, streamEnded, ::Triple).collectLatest { (shown, closed, ended) ->
                val step = fallbackPollStep(
                    visible = shown,
                    partyClosed = closed,
                    fallbackDone = fallbackDone,
                    streamEnded = ended,
                )
                if (step == FallbackStep.STOP) return@collectLatest
                // 평상시(WAIT_THEN_READ)는 첫 주기를 그대로 기다린다 — 복귀의 갱신은 폴백이 아니라
                // **재구독 스냅샷**이 맡으므로, 여기서 즉시 읽으면 복귀마다 상세 1건이 덧붙는다.
                // READ_NOW 는 다시 붙을 스트림이 아예 없는 경우다(종결·스트림 없는 저장소) —
                // 유일한 갱신 수단을 6초 미룰 이유가 없다.
                if (step == FallbackStep.WAIT_THEN_READ) delay(PARTY_FALLBACK_POLL_INTERVAL_MS)
                while (isActive) {
                    if (shouldPollFallback(realtimeAlive, hasRide = _uiState.value is UiState.Success) &&
                        readDetail()
                    ) {
                        val status = (_uiState.value as? UiState.Success)?.ride?.status
                        if (status != null && !isWaitingForMembers(status)) {
                            fallbackDone = true
                            return@collectLatest
                        }
                    }
                    delay(PARTY_FALLBACK_POLL_INTERVAL_MS)
                }
            }
        }
    }

    fun leaveParty() = runAction(failureMessage = "방에서 나가지 못했어요") {
        // 나가는 주체는 Bearer 토큰이 정한다 — memberId 를 넘기지 않는다 (22 보고서 §2)
        repository.leaveParty(partyId)
        _actionState.update { it.copy(left = true) }
    }

    /**
     * 1차 배포 모드의 「합승 완료」 — 기사 없이 방을 닫는다. 참여자 누구든 한 명이 누르면 **모두의** 방이 끝난다.
     * 정원이 안 찼거나(409 PARTY_NOT_COMPLETED) 기사가 붙었으면(409 DRIVER_ALREADY_ASSIGNED) 서버가 거절한다.
     */
    fun finishParty() = runAction(failureMessage = "합승을 완료하지 못했어요") {
        repository.finishParty(partyId)
        _actionState.update { it.copy(finished = true) }
    }

    // 액션 공통: 중복 실행 차단 → 진행 표시 → 실패 시 한국어 메시지
    private fun runAction(failureMessage: String, block: suspend () -> Unit) {
        if (_actionState.value.inProgress) return
        viewModelScope.launch {
            _actionState.update { it.copy(inProgress = true, errorMessage = null) }
            try {
                block()
                _actionState.update { it.copy(inProgress = false) }
            } catch (e: Exception) {
                _actionState.update { it.copy(inProgress = false, errorMessage = failureMessage) }
            }
        }
    }

    companion object {
        fun factory(repository: RideRepository, partyId: Long) = viewModelFactory {
            initializer { MatchWaitingViewModel(repository, partyId) }
        }
    }
}

/**
 * 21 대기 화면이 머무는 단계 — 아직 사람을 모으는 중(서버 ACTIVE)이거나 정원이 찬(서버 COMPLETED) 상태.
 * 이 밖으로 나가면 배차가 시작됐거나(25 배차 현황) 방이 닫힌 것이라 폴백 폴링을 끝낸다.
 */
internal fun isWaitingForMembers(status: RideStatus): Boolean =
    status == RideStatus.RECRUITING || status == RideStatus.MATCHED

/**
 * 이번 주기에 방 상세를 읽어야 하나 — **폴링 정책 그 자체**.
 *
 * SSE 가 살아 있으면([realtimeAlive]) 방 상태는 변경분으로 오므로 **읽지 않는다 — 평상시 0회**.
 * 예전에는 신호가 붙어 있어도 20초마다 「바뀌었나」를 물었는데, 서버가 무엇이 바뀌었는지를 싣게 된 뒤로
 * 그 주기 조회는 같은 내용을 다시 받는 낭비다(상세 폴링이 서버 요청의 45% 였다 — Backend #177).
 *
 * 단 **화면에 방이 아직 없으면**([hasRide] false) 스트림이 살아 있어도 읽는다. 변경분은 바탕이 있을 때만
 * 쓸 수 있어서, 첫 조회가 실패해 에러 화면인 채로 두면 다음 멤버 변화가 올 때까지 거기에 갇힌다.
 */
internal fun shouldPollFallback(realtimeAlive: Boolean, hasRide: Boolean): Boolean =
    !realtimeAlive || !hasRide

/**
 * SSE 를 지금 구독해야 하는가 — **가시성 게이트의 판정**(`MatchWaitingViewModel.observeEvents`).
 *
 * | 판정 | 조건 | 왜 |
 * |---|---|---|
 * | 구독하지 않는다 | [visible] 이 false | **이 함수의 존재 이유.** 보이지 않는 화면이 연결을 붙들 이유가 없다 — 멤버 합류에는 FCM 이 없어 백그라운드에서 받아도 띄울 데가 없고, OS 가 네트워크를 조여 「죽고 재연결」만 되풀이된다(QA 123 D-3) |
 * | 구독하지 않는다 | [streamEnded] 가 true | 종결로 끝난 스트림이다(방 닫힘·403 멤버 아님·404 방 없음·스트림 없는 저장소). 다시 붙어도 같은 답이고, 게이트 이전에도 다시 붙지 않았다 |
 * | 구독한다 | 그 밖 | 보이고, 아직 종결되지 않았다 |
 *
 * 끊어도 안전한 근거는 **재연결마다 서버가 방 상세 전체를 다시 준다**는 것이다(`connected` =
 * [PartyEvent.Snapshot]) — 복귀해 붙는 순간 상태가 통째로 맞춰지므로 끊긴 동안의 변화를 따라잡는
 * 별도 조회가 없다(`MatchWaitingViewModel` 의 `visible` KDoc).
 *
 * @param visible 화면이 사용자에게 보이는가(`LifecycleStartEffect` 로 받는다)
 * @param streamEnded 스트림이 **종결**로 끝났는가(가려져서 취소된 것과 구분한다)
 */
internal fun shouldObservePartyEvents(visible: Boolean, streamEnded: Boolean): Boolean =
    visible && !streamEnded

/**
 * 폴백 폴링 패스 하나가 할 일 — [fallbackPollStep] 의 판정 결과.
 * (17 탐색 `NearbyPollStep` 과 같은 자리·같은 모양이다.)
 */
internal enum class FallbackStep {
    /** 아무것도 하지 않고 다음 신호를 기다린다 — 안 보이거나, 루프가 이미 끝났다 */
    STOP,

    /** 지금 바로 상세를 읽고 주기를 시작한다 — 다시 붙을 스트림이 없어 폴백이 **유일한** 갱신 수단이다 */
    READ_NOW,

    /** 한 주기 기다린 뒤 읽는다 — 평상시. 복귀의 갱신은 재구독 스냅샷이 맡는다 */
    WAIT_THEN_READ,
}

/**
 * 폴백 루프를 **돌릴지, 첫 조회를 미룰지, 아예 멈출지.**
 *
 * 폴링을 화면 가시성에 묶는 판정을 이 한 곳에 모은다(17 탐색 `nearbyPollStep` 과 같은 역할).
 * 위에서부터 읽는다 — 앞의 조건이 뒤를 가린다.
 *
 * | 판정 | 조건 | 이유 |
 * |---|---|---|
 * | [FallbackStep.STOP] | [visible] 이 false | **이 함수의 존재 이유.** 보이지 않는 화면이 서버를 부를 이유가 없다 (QA 123 D-3) |
 * | [FallbackStep.STOP] | [partyClosed] | 서버가 방이 닫혔다고 알려 왔다 — 닫힌 방을 다시 읽어도 「닫혔다」만 재확인한다. **기존 종료 조건** |
 * | [FallbackStep.STOP] | [fallbackDone] | 방이 대기 단계를 벗어나 루프가 이미 끝났다. **기존 종료 조건** — 복귀로 되살아나면 회귀다 |
 * | [FallbackStep.READ_NOW] | [streamEnded] | 다시 붙을 스트림이 없다(종결·더미). 복귀 갱신을 맡을 스냅샷이 없으므로 6초를 기다리지 않는다 |
 * | [FallbackStep.WAIT_THEN_READ] | 그 밖 | 평상시. 복귀 즉시 재구독이 스냅샷을 가져오므로, 여기서 또 읽으면 복귀마다 상세 1건이 덧붙는다 |
 *
 * 가시성이 맨 앞인 게 요점이다 — **보이지 않는 동안에는 나머지 조건을 보지 않는다.**
 * 반대로 [partyClosed]·[fallbackDone] 은 「끝났다」는 사실이라 복귀해도 되돌아가지 않는다:
 * 가시성은 스위치, 이 둘은 종결이다.
 *
 * @param visible 화면이 사용자에게 보이는가(`LifecycleStartEffect` 로 받는다)
 * @param partyClosed 서버가 `closed` 를 보냈는가([PartyEvent.Closed])
 * @param fallbackDone 대기 단계를 벗어나 루프가 끝났는가([isWaitingForMembers] false 를 본 뒤)
 * @param streamEnded SSE 가 종결로 끝나 재구독할 것이 없는가([shouldObservePartyEvents])
 */
internal fun fallbackPollStep(
    visible: Boolean,
    partyClosed: Boolean,
    fallbackDone: Boolean,
    streamEnded: Boolean,
): FallbackStep = when {
    !visible -> FallbackStep.STOP
    partyClosed || fallbackDone -> FallbackStep.STOP
    streamEnded -> FallbackStep.READ_NOW
    else -> FallbackStep.WAIT_THEN_READ
}

/**
 * [PartyEvent.MemberJoined] 적용 — 멤버 목록에 더하고 서버가 준 **들어온 뒤의** [status] 로 바꾼다.
 *
 * 같은 publicId 가 이미 있으면 **자리를 지키며 교체**한다(덧붙이지 않는다). 같은 사람이 두 번 들어오는
 * 일은 없지만, 신호가 겹쳐 오거나 스냅샷 직후의 변경분이 다시 실릴 때 인원이 부풀면 안 된다 —
 * 21 의 인원 표시는 이 목록의 길이(`members.size`)가 유일한 근거다.
 *
 * id 가 빈 멤버(탈퇴 등으로 서버가 요약을 못 만든 경우)는 서로 구별할 근거가 없어 겹침 판정을 하지 않는다 —
 * 빈 id 끼리 같다고 보면 엉뚱한 사람을 덮어쓴다.
 *
 * [member] 의 [User.isMe] 는 상세와 같은 근거(publicId == 세션 uuid)로 **이미 채워져 온다** — 다시 판정하지 않는다.
 */
internal fun Ride.withMemberJoined(member: User, status: RideStatus): Ride {
    val index = members.indexOfFirst { it.isSameMemberAs(member) }
    val merged = if (index < 0) members + member else members.toMutableList().also { it[index] = member }
    return copy(members = merged, status = status)
}

/**
 * [PartyEvent.MemberLeft] 적용 — [publicId] 로 **그 한 명만** 지우고 나간 뒤의 [status] 로 바꾼다.
 *
 * 모르는 publicId 면 목록은 그대로 두고 status 만 반영한다 — 그 사람의 입장 신호를 놓쳤다는 뜻이고,
 * 없는 사람을 지우려 상세를 또 읽지는 않는다(어긋남은 [hasMemberDrift] 나 다음 스냅샷이 잡는다).
 * 빈 publicId 로는 아무도 지우지 않는다 — 요약이 없는 멤버들을 한꺼번에 날리는 사고를 막는다.
 */
internal fun Ride.withMemberLeft(publicId: String, status: RideStatus): Ride =
    copy(members = members.filterNot { it.id.isNotBlank() && it.id == publicId }, status = status)

// 같은 사람인가 — 서버 식별자(publicId)가 **양쪽에 다 있을 때만** 같다고 본다.
private fun User.isSameMemberAs(other: User): Boolean = id.isNotBlank() && id == other.id

/**
 * 들고 있는 멤버 목록이 서버가 준 status 와 **모순**인가 — 변경분이 유실됐다는 신호.
 *
 * 인원 표시의 근거는 **멤버 목록 하나**다([MatchWaitingScreen] 의 `foundCount` = `members.size`) —
 * 별도의 인원 카운터를 들고 있지 않다(합류 응답의 `currentMembers` 도 21 로 넘기지 않는다). 그래야
 * 「목록엔 3명인데 카운터는 4」 같은 두 번째 진실이 생기지 않는다.
 *
 * 그래도 어긋날 수 있는 짝이 하나 남는다: 서버가 변경분에 실어 준 status 와, 변경분을 쌓아 만든 목록.
 * 둘이 모순이면 **틀린 쪽은 목록**이라고 본다 — status 는 서버가 그 자리에서 판정한 값이고, 목록은
 * 유실될 수 있는 신호를 더해 만든 값이다. 그래서 모순을 보면 상세를 1회 읽어 **서버 쪽으로 맞춘다.**
 *
 * 모순의 정의는 정원뿐이다: 정원이 찼다는 status(MATCHED)인데 목록이 모자라거나, 아직 모집 중
 * (RECRUITING)인데 목록이 정원을 채웠거나. 배차 이후 단계는 Route 가 화면을 넘기므로 보지 않고,
 * 정원을 모르는 방(capacity 0 — 목록·더미 응답)은 비교 기준이 없어 판단하지 않는다.
 */
internal fun hasMemberDrift(ride: Ride): Boolean {
    if (ride.capacity <= 0) return false
    return when (ride.status) {
        RideStatus.MATCHED -> ride.members.size < ride.capacity
        RideStatus.RECRUITING -> ride.members.size >= ride.capacity
        else -> false
    }
}

/**
 * 서버가 나가기를 허용하는가. 서버 `ensureRecruiting` = ACTIVE 또는 COMPLETED.
 *
 * 기사 모드에선 COMPLETED 가 순간이라(곧 MATCHING) 사실상 ACTIVE 에서만 나갈 수 있고, 눌러 봐야 409 다 —
 * 그래서 예전처럼 막는다. 1차 배포 모드에선 COMPLETED 에 머무르므로 거기서도 나갈 수 있어야 한다
 * (나가면 서버가 방을 ACTIVE 로 되돌려 다시 모집한다).
 */
internal fun canLeaveParty(status: RideStatus, taxiEnabled: Boolean): Boolean =
    status == RideStatus.RECRUITING || (!taxiEnabled && status == RideStatus.MATCHED)

/**
 * 1차 배포 모드에서 「합승 완료」를 보여 줄 때 — 정원이 찼고(COMPLETED) 기사가 없을 때.
 * 기사 모드에선 절대 보이지 않는다(정원이 차면 곧바로 25 로 넘어간다).
 */
internal fun canFinishParty(status: RideStatus, taxiEnabled: Boolean): Boolean =
    !taxiEnabled && status == RideStatus.MATCHED

/**
 * 21 매칭 대기 — SSE 변경분 구독(+끊김 폴백) · 나가기/합승 완료 진입점. partyId 없으면 기존 더미 화면 유지.
 *
 * 방 상태의 출처는 [MatchWaitingViewModel] 하나뿐이다 — 이 화면에서 SSE 를 두 번 수집하면 서버가 서로의
 * 연결을 끊어 무한 재연결이 되므로, 여기서 `LaunchedEffect` 로 구독을 추가하지 말 것. 아래
 * `LifecycleStartEffect` 는 **구독을 추가하지 않는다** — 그 하나의 수집을 켜고 끄는 신호만 보낸다
 * ([MatchWaitingViewModel.onScreenStart]·[MatchWaitingViewModel.onScreenStop]).
 */
@Composable
fun MatchWaitingRoute(
    repository: RideRepository,
    partyId: Long?,
    /**
     * 서버 운영 설정의 택시 모드([AppConfig.taxiEnabled][com.moyeota.domain.model.AppConfig.taxiEnabled]).
     * false(1차 배포)면 정원이 찼을 때 기사 대기 대신 「합승 완료」를 보여 준다.
     */
    taxiEnabled: Boolean = false,
    onCancelSearch: () -> Unit = {},
    /** 방을 유지한 채 화면만 벗어난다(나가기가 막힌 단계의 뒤로가기) */
    onExitKeepingParty: () -> Unit = {},
    onCardClick: () -> Unit = {},
    onMatchingStarted: () -> Unit = {},
    /**
     * 방이 닫혔다(FINISHED·CANCELED) — 내가 「합승 완료」를 눌렀거나, 다른 참여자가 눌렀거나, 30분 스윕이 닫았다.
     * 호출자는 진행 중 기억을 지우고 홈으로 보낸다.
     */
    onPartyClosed: () -> Unit = {},
    /** 이 방의 채팅방을 여는 길. 채팅방이 아직 없으면 null — 화면이 버튼을 그리지 않는다 */
    onOpenChat: (() -> Unit)? = null,
) {
    if (partyId == null) {
        MatchWaitingScreen(
            onCancelSearch = onCancelSearch,
            onExitKeepingParty = onExitKeepingParty,
            onCardClick = onCardClick,
            onOpenChat = onOpenChat,
        )
        return
    }

    val viewModel: MatchWaitingViewModel = viewModel(
        key = "waiting-$partyId",
        factory = MatchWaitingViewModel.factory(repository, partyId),
    )
    val state by viewModel.uiState.collectAsState()
    val action by viewModel.actionState.collectAsState()
    val closed by viewModel.partyClosed.collectAsState()

    // 지도의 파란 점. 권한을 자동 요청하지 않는다 — 대기 화면의 본질은 지도가 아니라 「기다림」이라,
    // 여기서 다이얼로그를 띄우면 흐름을 끊는다. 이미 허용돼 있으면(14·17 에서 받았을 것) 점이 뜬다.
    val myLocation = rememberMyLocationState(autoRequestPermission = false)

    // 화면이 보이는 동안만 SSE 를 구독하고 폴백 폴링을 돌린다 — 위에 22 상세·24 채팅방이 올라오거나
    // 앱이 백그라운드로 가면 둘 다 멈추고, 돌아오면 그 자리에서 다시 붙는다.
    // 끊어도 안전한 근거는 **재연결마다 서버가 방 상세 전체를 다시 준다**는 것이다(MatchWaitingViewModel.visible).
    //
    // RESUMED 가 아니라 STARTED 를 쓴다: ① NavHost 는 **전환 애니메이션이 끝난 뒤에야** 엔트리를
    // RESUMED 로 올려서, RESUMED 로 묶으면 복귀 재구독이 그만큼 늦는다 ② 위에 올라온 화면에 가린
    // 엔트리는 CREATED 까지 내려가므로 「22·24 가 올라옴」도 STARTED 로 똑같이 잡히고, 앱 백그라운드
    // (ON_STOP)도 함께 잡힌다 ③ 같은 코드베이스의 17 탐색·24 채팅방·GPS 구독이 전부 STARTED 경계다.
    LifecycleStartEffect(viewModel) {
        viewModel.onScreenStart()
        onStopOrDispose { viewModel.onScreenStop() }
    }

    LaunchedEffect(action.left) {
        if (action.left) onCancelSearch()
    }
    LaunchedEffect(action.finished) {
        if (action.finished) onPartyClosed()
    }
    // 서버가 방을 닫았다(SSE closed) — 다른 참여자의 「합승 완료」·30분 만료·마지막 멤버 이탈.
    // 예전에는 폴링이 FINISHED/CANCELED 를 읽어 와야 알았고, 지금은 신호 하나로 바로 떠난다.
    LaunchedEffect(closed) {
        if (closed) onPartyClosed()
    }

    // 매칭 시작 버튼이 없어졌으므로, 25 배차 현황으로의 이동은 서버 status 전이가 유일한 신호다.
    // 그 전이는 SSE 로 오지 않지만(서버 발행처가 참여·퇴장·종료뿐) 정원이 찬 뒤 서버가 곧바로 배차를
    // 시작하므로, 변경분에 실려 온 status 와 끊김 폴백의 상세가 함께 그 전이를 비춘다.
    val status = (state as? MatchWaitingViewModel.UiState.Success)?.ride?.status
    LaunchedEffect(status) {
        when (status) {
            RideStatus.DISPATCHING, RideStatus.ONGOING -> onMatchingStarted()
            RideStatus.COMPLETED, RideStatus.CANCELED -> onPartyClosed()
            else -> Unit
        }
    }

    // 탭바가 없는 화면 — 로딩·에러에서도 뒤로가기(=탐색 취소)를 남긴다 (QA F-1 동류)
    when (val current = state) {
        // 로딩·에러에서는 방 상태를 모른다. 나가기를 걸면 매칭 중인 방에 409 를 쏘게 되므로
        // 여기서의 뒤로가기는 **방을 유지한 채** 화면만 벗어난다.
        MatchWaitingViewModel.UiState.Loading -> BackStateScaffold("같이 탈 사람 찾는 중", onExitKeepingParty) {
            LoadingBox()
        }
        is MatchWaitingViewModel.UiState.Error -> BackStateScaffold("같이 탈 사람 찾는 중", onExitKeepingParty) {
            ErrorBox(message = current.message, onRetry = viewModel::refresh)
        }
        is MatchWaitingViewModel.UiState.Success -> {
            val ride = current.ride
            MatchWaitingScreen(
                ride = ride,
                foundCount = ride.members.size,
                conditionLabel = "${ride.capacity}인",
                myLocation = myLocation.coordinates,
                taxiEnabled = taxiEnabled,
                actionInProgress = action.inProgress,
                actionErrorMessage = action.errorMessage,
                onCancelSearch = viewModel::leaveParty, // 나가기 성공 시 14 홈
                onFinish = viewModel::finishParty, // 합승 완료 성공 시 14 홈
                onExitKeepingParty = onExitKeepingParty,
                onCardClick = onCardClick,
                onOpenChat = onOpenChat,
            )
        }
    }
}

// 21 「탐색 반경」 라벨을 만들던 radiusLabel/meters 는 지웠다(2026-10-14) — 반경이 서버 고정값이
// 되면서(Backend `a668aee`) 사용자가 정한 조건이 아니게 됐고, 매칭은 그 값을 읽지도 않는다
// (합류에 거리 검사 없음, 목록은 지도 뷰포트). 도메인 [Ride] 에서도 반경 필드를 뺐다.
