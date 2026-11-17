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
import com.moyeota.domain.analytics.Analytics
import com.moyeota.domain.analytics.AnalyticsEvent
import com.moyeota.domain.model.PartyArriveException
import com.moyeota.domain.model.PartyConfirmException
import com.moyeota.domain.model.PartyDepartException
import com.moyeota.domain.model.PartyEvent
import com.moyeota.domain.model.PartyLeaveException
import com.moyeota.domain.model.PartyMembershipLostException
import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RideStatus
import com.moyeota.domain.model.RoomType
import com.moyeota.domain.model.User
import com.moyeota.domain.model.isHostedByMe
import com.moyeota.domain.repository.RideRepository
import com.moyeota.presentation.core.BackStateScaffold
import com.moyeota.presentation.core.ErrorBox
import com.moyeota.presentation.core.LoadingBox
import com.moyeota.presentation.core.location.rememberMyLocationState
import java.time.Instant
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
 * 그래서 6초를 **하한으로 잡는다** — 그 아래로 내리면 옛 구조(상태 조회 4초 × 쿼리 1개)보다 부하가
 * 커진다. 부하테스트에서 방 상세 폴링이 전체 요청의 45% 였다(Backend #177). 하한을 그대로 쓰고 더
 * 늦추지는 않는다: 끊긴 상태에서는 이게 유일한 갱신 수단이라, 들고 나는 사람이 보이기까지의 지연이
 * 그만큼 길어진다.
 *
 * 이 값은 예전에 25·26 과 함께 쓰던 `core/PartyDetailPolling.kt` 의 공통 상수였다. 두 화면이 사라져
 * 방 상세를 폴링하는 곳이 **여기 하나뿐**이 되면서 「공통 하한」이 성립하지 않아, 그 파일을 지우고
 * 근거를 이 자리로 들였다.
 */
private const val PARTY_FALLBACK_POLL_INTERVAL_MS = 6_000L

/**
 * 약속 단계 판정에 쓰는 **시계 틱** 간격 — 10초.
 *
 * 이 틱이 필요한 이유: 약속 흐름의 두 전이는 **사용자가 아무것도 하지 않아도** 일어난다.
 * 「약속 10분 전」이 되면 도착 버튼이 떠야 하고, 약속 시간이 지나면 출발 버튼이 떠야 한다
 * ([meetingStage]). 재구성을 일으키는 다른 신호가 없으면 화면은 그 순간을 그냥 지나친다 —
 * SSE 는 약속 시간이 됐다고 알려 주지 않는다(그건 서버에서 아무 일도 일어나지 않은 시점이다).
 *
 * 10초인 이유는 **버튼이 늦게 떠도 손해가 없고, 자주 떠도 이득이 없기** 때문이다. 경계는 분 단위
 * (10분 전·정각)이고 서버가 같은 조건을 다시 재므로, 최악이 「버튼이 10초 늦게 뜬다」다. 반대로 1초
 * 틱으로 두면 지도·시트가 올라간 이 화면이 분당 60번 재구성된다 — 폴백 폴링(6초)보다 잦은 갱신을
 * 서버도 아닌 시계 때문에 걸 이유가 없다.
 *
 * 화면이 보이는 동안에만 돈다([MatchWaitingViewModel.visible]) — 가려진 화면의 시계는 쓸 데가 없다.
 */
private const val MEETING_CLOCK_TICK_MS = 10_000L

class MatchWaitingViewModel(
    private val repository: RideRepository,
    private val partyId: Long,
    /** 현재 시각(ms). `complete_match.waited_seconds` 계산용 — 테스트가 시계를 주입한다. */
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data class Success(val ride: Ride) : UiState
        data class Error(val message: String) : UiState
    }

    /**
     * 액션 결과.
     *
     * | 값 | 액션 | 성공하면 |
     * |---|---|---|
     * | [left] | 「그만 찾기」·「방 나가기」 | 방을 떠난다 → 홈 |
     * | [departed] | 「출발」(도착한 방장) | 방이 **모두에게** 닫힌다 → 홈 |
     *
     * `finished`(「합승 완료」)는 **지웠다** — 서버가 `POST /rooms/{id}/finish` 를 없앴다
     * (`b16551b7`). 방을 끝내는 길은 [departParty] 하나이고, 조건이 「정원이 찼다」에서
     * **「약속을 정하고 가서 만났다」**로 바뀌었다. 두 플래그가 같은 곳(홈)으로 보냈으므로 지워도
     * 잃는 전이는 없다.
     *
     * 확정([confirmMeeting])과 도착([arriveAtDeparture])은 **화면을 떠나지 않는다** — 같은 방에
     * 머문 채 단계만 바뀌므로 플래그를 두지 않고, 성공 시 들고 있는 방을 그 자리에서 고친다.
     * 준비/매칭 시작은 백엔드에서 삭제됐다.
     */
    data class ActionState(
        val inProgress: Boolean = false,
        val errorMessage: String? = null,
        val left: Boolean = false,
        val departed: Boolean = false,
    )

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _actionState = MutableStateFlow(ActionState())
    val actionState: StateFlow<ActionState> = _actionState.asStateFlow()

    /**
     * 서버가 **방이 닫혔다**고 알려 왔다([PartyEvent.Closed]). 누가 닫았는지(누군가의 「출발」·
     * 모집 만료·마지막 멤버 이탈·도착자 없는 자동 정리)는 화면 동작이 같아 구분하지 않는다 —
     * Route 가 받아 홈으로 보낸다.
     *
     * 확인용 상세 재조회를 하지 않는다: 서버는 이 신호 뒤에 구독을 끊고, 닫힌 방을 한 번 더 읽어도
     * 「닫혔다」는 사실만 다시 확인할 뿐이다(예전에는 FINISHED/CANCELED 를 상세로 읽어 와야 알았다).
     */
    private val _partyClosed = MutableStateFlow(false)
    val partyClosed: StateFlow<Boolean> = _partyClosed.asStateFlow()

    /**
     * **나는 이 방의 멤버가 아니다** — 「방이 닫혔다」([_partyClosed])와 **다른 사실**이고, 사용자가
     * 할 일은 같다(이 화면에 있을 이유가 없다). Route 가 받아 홈으로 보낸다.
     *
     * 두 가지 근거로 선다.
     * 1. **SSE 403** `NOT_PARTY_MEMBER`([PartyMembershipLostException]) — 서버가 멤버십을 말해 주는
     *    **유일한 창구**다. 방 상세(`GET /matching/rooms/{id}`)에는 참여자 검사가 없어 멤버가 아니어도
     *    조회가 계속 성공한다.
     * 2. **상세의 members 에 내가 없다**([hasLostMembership]) — SSE 가 아예 붙지 않는 망(프록시가
     *    `text/event-stream` 을 막는 경우)에서는 1번 창구가 열리지 않으므로 폴백 조회로 한 번 더 본다.
     *
     * 이 값이 없던 동안 403 은 「스트림이 끝났다」와 똑같이 뭉개졌고, 그러면 폴백 폴링이 **내가 들어
     * 있지 않은 방**을 6초마다 영원히 읽으면서 「같이 탈 사람을 찾고 있어요」를 띄웠다. 거기서
     * 「그만 찾기」를 눌러도 다시 403 이라 **벗어날 길이 없었다.**
     *
     * [_partyClosed] 와 합치지 않는 이유: 저 값은 서버가 보낸 `closed` 신호라는 **사실**이고, 닫히지
     * 않은 방을 닫혔다고 적으면 그 값을 읽는 쪽(폴백 종료 조건)의 뜻이 흐려진다.
     */
    private val _membershipLost = MutableStateFlow(false)
    val membershipLost: StateFlow<Boolean> = _membershipLost.asStateFlow()

    /**
     * 화면이 약속 단계를 판정할 때 쓰는 **지금 시각**(ms). [MEETING_CLOCK_TICK_MS] 마다,
     * **화면이 보이는 동안에만** 올라간다([observeMeetingClock]).
     *
     * 시계를 ViewModel 에 두는 이유: ① 가시성 스위치([visible])가 이미 여기 있어 가려진 화면의 틱을
     * 공짜로 끊을 수 있다 ② 테스트가 [clock] 을 주입해 시각을 지배할 수 있다 ③ 화면은 받은 값을
     * 쓸 뿐이라 Compose 안에서 `Instant.now()` 를 부르는 비멱등 재구성이 생기지 않는다.
     *
     * 판정 함수들은 이 값을 **인자로** 받는다 — 어느 판정도 제 안에서 시계를 읽지 않는다
     * (그러면 경계를 테스트로 고정할 수 없다 — `MeetingFlow.kt` KDoc).
     */
    private val _nowMillis = MutableStateFlow(clock())
    val nowMillis: StateFlow<Long> = _nowMillis.asStateFlow()

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
     * SSE 스트림이 **종결**로 끝났다 — 방 닫힘(`closed`)·404(방 없음), 또는 스트림 자체가 없는
     * 저장소(더미). 그 밖의 끊김은 데이터 계층이 백오프 뒤 스스로 다시 붙으므로 여기까지 오지 않는다.
     *
     * **403(멤버 아님)도 여기를 세우지만 그것만으로 끝나지 않는다** — [_membershipLost] 가 함께 서고,
     * 그쪽이 화면을 떠나게 한다. 둘을 같은 값으로 뭉개면 「내가 없는 방」을 영원히 폴링한다.
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

    /**
     * 화면이 반영한 가장 마지막 **방 순번**([com.moyeota.domain.model.PartyEvent] 의 표).
     *
     * [appliedEvents] 와 역할이 다르다 — 저건 "내가 몇 번 반영했나"(늦은 조회 결과를 버리는 표식)고,
     * 이건 "서버 기준 어디까지 봤나"다. 프레임 순서가 뒤집혀 도착하는 걸 가려내는 유일한 근거다.
     *
     * **끊겨도 지우지 않는다.** 재연결 스냅샷이 들고 있던 것보다 낮을 때 버리려면 남아 있어야 한다.
     * null 은 "아직 모른다"(첫 연결 전) 또는 "서버가 말하지 않는다"(옛 배포) 둘 다를 뜻하고,
     * 어느 쪽이든 순서를 가릴 수 없으니 예전 완화 규칙으로 떨어진다.
     */
    @Volatile
    private var heldVersion: Long? = null

    /**
     * 서버가 준 멤버 목록에서 **나를 한 번이라도 봤는가**([User.isMe] 가 true 인 멤버).
     *
     * [hasLostMembership] 의 전제다. 이 플래그 없이 「목록에 내가 없다」만 보면 **내가 누군지 모르는
     * 경우까지 멤버십 상실로 읽는다**: `isMe` 는 `publicId == 세션 uuid` 비교라, 세션 uuid 가 없으면
     * (미로그인·복원 전) 아무도 나로 표시되지 않아 목록이 정상이어도 none 이 참이 된다. 그러면
     * **멀쩡히 방에 들어 있는 사용자를 쫓아낸다** — 틀렸을 때의 대가가 한쪽으로 크게 기울어 있으므로,
     * 「나를 본 적이 있다」는 증거가 생긴 뒤에만 그 판정을 켠다.
     */
    @Volatile
    private var sawMyself = false

    /** 21 에 들어온 시각 — `complete_match.waited_seconds` 는 신청 시점이 아니라 이 화면 진입 기준이다(iOS `enteredAt`). */
    private val enteredAtMillis: Long = clock()

    /** `complete_match` 를 이미 기록했는가 — 이 ViewModel 수명 동안 한 번만([trackMatchCompletionIfNeeded]). */
    private var matchCompletionTracked = false

    init {
        refresh()
        observeEvents()
        observeFallbackPolling()
        observeMeetingClock()
    }

    /**
     * 약속 단계 판정용 시계를 **화면이 보이는 동안에만** 흘린다 — 근거는 [MEETING_CLOCK_TICK_MS].
     *
     * `collectLatest` 로 묶는 모양은 [observeFallbackPolling] 과 같다: 가려지는 순간 진행 중인
     * `delay` 까지 함께 취소되고, 다시 보이면 **곧바로 한 번 찍고** 주기를 새로 시작한다. 복귀
     * 즉시 찍는 것이 중요하다 — 가려진 사이에 약속 10분 전이 지났을 수 있고, 그때 다음 틱을 기다리면
     * 돌아온 화면이 10초 동안 낡은 단계를 보여 준다.
     */
    private fun observeMeetingClock() {
        viewModelScope.launch {
            visible.collectLatest { shown ->
                if (!shown) return@collectLatest
                while (isActive) {
                    _nowMillis.value = clock()
                    delay(MEETING_CLOCK_TICK_MS)
                }
            }
        }
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
     * **스냅샷이 먼저 온다는 보장은 없다.** 서버는 구독을 레지스트리에 먼저 등록한 뒤 DB 를 읽어
     * `connected` 를 보내므로, 그 사이에 커밋된 변경분이 스냅샷보다 **먼저** 도착할 수 있다
     * (`PartySseController`: "연결 중에 방이 바뀌면 변경분이 connected 보다 먼저 올 수 있다").
     * 그래서 프레임마다 실려 오는 순번으로 순서를 가린다 — [deltaVerdict]·[acceptsSnapshot].
     * 가시성 때문에 끊었다 붙는 것도 서버 입장에선 똑같은 재연결이고, 그때도 같은 판정이 걸린다.
     *
     * 수집이 **스스로** 끝나는 건 종결뿐이다(방 닫힘·404 방 없음). 그때는 다시 붙어도 같은 답이라
     * 복귀해도 붙지 않고([streamEnded]) 폴백이 갱신을 맡는다 — 내가 방을 나간 경우라면 Route 가
     * 이미 화면을 떠났고 수집도 함께 취소된다.
     *
     * **403 멤버 아님은 그 종결에 섞이지 않는다** — 데이터 계층이 [PartyMembershipLostException] 으로
     * 올리고, 여기서 받아 [_membershipLost] 를 세운다. 그러면 폴백도 멈추고 Route 가 화면을 떠난다.
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
                } catch (e: PartyMembershipLostException) {
                    // 403 `NOT_PARTY_MEMBER` — 끊긴 게 아니라 **내가 이 방 사람이 아니다**.
                    // 다시 붙어 봐야 같은 답이라 종결로 적되(streamEnded), 「스트림이 끝났다」와 갈라
                    // 화면을 떠나게 한다. 순서가 중요하다: 상실을 먼저 세워야 폴백 패스가 다시
                    // 평가될 때 READ_NOW 를 거치지 않고 곧바로 STOP 으로 떨어진다.
                    _membershipLost.value = true
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
     * | [PartyEvent.MemberLeft] | publicId 로 제거 + status 갱신 + **방장 승계 반영** ([Ride.withHostSuccession]) |
     *
     * | [PartyEvent.Confirmed] | 약속 확정 + **바뀐 출발지** 반영 ([Ride.withMeetingConfirmed]) — status 는 건드리지 않는다 |
     * | [PartyEvent.MemberArrived] | 그 멤버의 도착 표시 ([Ride.withMemberArrived]) |
     * | [PartyEvent.Closed] | 닫힘 신호만 세운다 → Route 가 홈으로 |
     * | [PartyEvent.Disconnected] | 폴백 폴링 켬. 상태는 **그대로 둔다** — 곧 올 스냅샷이 보정한다 |
     *
     * 상세 1회 조회가 붙는 경우는 셋이다 — **누가 나갔는지 모를 때**(publicId null), 서버가 지목한
     * **방장이 내 목록에 없을 때**, 그리고 **확정 프레임의 출발지 셋이 반쪽일 때**
     * ([RideDelta.needsDetail] — 세 경우 다 서버 계약이 그렇게 하라고 적어 두었다).
     *
     * `recruiting-closed` 분기는 **지웠다** — 서버가 그 이벤트를 없앴다(`b16551b7`). 「정원이 차서
     * 모집이 끝난다」는 전이 자체가 사라졌고(정원이 차도 ACTIVE 다), 모집을 끝내는 신호는
     * [PartyEvent.Confirmed] 하나다. 그 분기가 하던 일(status 문자열을 옮길 수 없어 상세를 1회 읽기)은
     * 받을 프레임이 없어 **영원히 돌지 않는 코드**였으므로 남겨 두면 읽는 사람을 속인다.
     */
    private suspend fun applyEvent(event: PartyEvent) {
        when (event) {
            is PartyEvent.Snapshot -> {
                // 버리는 경우에도 연결은 살아 있다 — realtimeAlive 는 세운다(폴백이 끼어들면 안 된다).
                realtimeAlive = true
                if (acceptsSnapshot(heldVersion, event.version)) {
                    heldVersion = event.version ?: heldVersion
                    appliedEvents++
                    rememberMyself(event.ride)
                    publish(event.ride)
                }
            }
            // member 의 isMe 는 상세와 같은 근거(publicId == 세션 uuid)로 이미 채워져 온다 — 다시 판정하지 않는다
            // 합류는 방장을 바꿀 수 없어 승계 판정이 없다 — 변경분을 그대로 싣는다(needsDetail = false)
            is PartyEvent.MemberJoined -> when (deltaVerdict(heldVersion, event.version)) {
                FrameVerdict.Drop -> Unit
                FrameVerdict.Resync -> readDetail()
                FrameVerdict.Apply -> {
                    heldVersion = event.version ?: heldVersion
                    applyDelta { RideDelta(it.withMemberJoined(event.member, event.status)) }
                }
            }
            is PartyEvent.MemberLeft -> when (deltaVerdict(heldVersion, event.version)) {
                FrameVerdict.Drop -> Unit
                // 틈이다 — 지울 사람을 특정해도 중간 프레임을 모르니 상세를 믿는다. 다만 승계는 먼저
                // 반영한다(publicId null 경로와 같은 이유: 상세 읽기가 실패하면 이게 유일한 승계 근거다).
                FrameVerdict.Resync -> {
                    applyStatus(event.status, event.hostPublicId)
                    readDetail()
                }
                FrameVerdict.Apply -> {
                    heldVersion = event.version ?: heldVersion
                    applyMemberLeft(event)
                }
            }
            // 약속이 확정됐다. **status 를 건드리지 않는다** — 프레임의 status 는 서버 enum 이름
            // (문자열)이라 [RideStatus] 로 옮기려면 `PartyMappers` 의 매핑을 화면 쪽에 베껴야 하고,
            // 그러면 서버가 값을 늘릴 때 두 곳이 어긋난다. 확정 사실만 싣고 status 는 다음 스냅샷·
            // 폴백 상세가 맞춘다 — 화면이 이 단계에서 보는 값은 `isConfirmed` 라([meetingStage])
            // 그 지연이 사용자에게 보이지 않는다.
            //
            // ★ **출발지 셋은 반대로 꼭 받아야 한다.** 확정이 방의 출발지를 만남 장소로 옮기므로
            // (서버 `Party.confirm`), 무시하면 지도가 옛 출발지를 가리키고 **도착 버튼이 틀린 곳을
            // 기준으로 판정**돼 약속 장소에 서 있는 사람이 400 `TOO_FAR_FROM_DEPARTURE` 를 맞는다.
            is PartyEvent.Confirmed -> when (deltaVerdict(heldVersion, event.version)) {
                FrameVerdict.Drop -> Unit
                FrameVerdict.Resync -> readDetail()
                FrameVerdict.Apply -> {
                    heldVersion = event.version ?: heldVersion
                    applyDelta {
                        it.withMeetingConfirmed(
                            departureAt = event.departureAt,
                            departureLat = event.departureLat,
                            departureLng = event.departureLng,
                            departurePlace = event.departurePlace,
                        )
                    }
                }
            }
            // 누가 출발지에 도착했다. 내 도착도 같은 길로 올 수 있지만, 그걸 기다리지 않는다 —
            // 액션 성공 시 [arriveAtDeparture] 가 내 줄을 그 자리에서 고친다(SSE 는 at-most-once).
            is PartyEvent.MemberArrived -> when (deltaVerdict(heldVersion, event.version)) {
                FrameVerdict.Drop -> Unit
                FrameVerdict.Resync -> readDetail()
                FrameVerdict.Apply -> {
                    heldVersion = event.version ?: heldVersion
                    applyDelta { it.withMemberArrived(event.publicId) }
                }
            }
            PartyEvent.Closed -> {
                realtimeAlive = false
                _partyClosed.value = true
            }
            PartyEvent.Disconnected -> realtimeAlive = false
        }
    }

    /** [PartyEvent.MemberLeft] 를 순서 판정 **뒤에** 실제로 반영한다. */
    private suspend fun applyMemberLeft(event: PartyEvent.MemberLeft) {
        val publicId = event.publicId
        if (publicId == null) {
            // 누가 나갔는지 서버도 모른다 — 지울 멤버를 특정할 수 없으니 상세를 읽는다.
            // 그래도 **승계는 먼저 반영한다**: 상세 읽기가 실패하면 이 프레임이 유일한 승계 근거다
            // (서버는 member-left 를 다시 보내지 않는다). 이 경로는 아래 readDetail 이 **무조건**
            // 돌므로 승계 쪽 needsDetail 을 따로 볼 필요가 없다 — 서버 계약의 「다시 받는다」가 이미 지켜진다.
            applyStatus(event.status, event.hostPublicId)
            readDetail()
        } else {
            applyDelta { it.withMemberLeft(publicId, event.status, event.hostPublicId) }
        }
    }

    /**
     * 변경분을 들고 있는 방에 적용한다. 바탕이 없으면(로딩·에러) 적용할 대상이 없어 상세를 1회 읽는다 —
     * 증분은 **바탕이 있을 때만** 쓸 수 있다.
     *
     * 적용 결과가 [needsDetailAfterDelta] 면 상세를 1회 읽어 서버 쪽으로 맞춘다 — 사유는 둘이고
     * (방장 지목을 못 맞췄다 / 목록이 status 와 모순이다) 처방이 같다. 그때는 **목록이 아니라 서버를
     * 믿는다**. 주기 호출이 아니라 그 순간 1회이며, 읽어 온 결과에는 이 검사를 다시 걸지 않으므로
     * (읽기 결과는 [readDetail] 이 [UiState.Success] 로 직접 세운다) **되돌이가 되지 않는다** —
     * 프레임 하나당 추가 조회는 최대 1건이다.
     */
    private suspend fun applyDelta(transform: (Ride) -> RideDelta) {
        val held = (_uiState.value as? UiState.Success)?.ride
        if (held == null) {
            readDetail()
            return
        }
        val delta = transform(held)
        appliedEvents++
        publish(delta.ride)
        if (needsDetailAfterDelta(delta)) readDetail()
    }

    /**
     * 멤버 **목록**은 그대로 두고 서버가 준 status 와 방장 승계만 반영한다 — 지울 멤버를 특정할 수
     * 없을 때(publicId null)의 경로다. 목록은 못 고쳐도 [Ride.withHostSuccession] 은 publicId 로
     * 이름을 불린 한 명만 보므로 그대로 적용할 수 있다(그 사람이 목록에 없으면 표시를 손대지 않는다).
     *
     * [RideDelta.needsDetail] 을 **여기서 보지 않는다**: 이 경로의 호출부가 곧바로 [readDetail] 을
     * 부르므로 서버 계약의 「목록에 없으면 방 상세를 다시 받는다」가 이미 지켜진다. 여기서 또 읽으면
     * 같은 프레임에 상세가 2건 나간다.
     */
    private fun applyStatus(status: RideStatus, hostPublicId: String?) {
        val held = (_uiState.value as? UiState.Success)?.ride ?: return
        appliedEvents++
        publish(held.withHostSuccession(hostPublicId).ride.copy(status = status))
    }

    /**
     * 방 상세를 1회 읽어 화면에 반영한다. 돌려주는 값은 **읽기 성공 여부**다(에러 화면 판단용).
     *
     * 읽는 동안 실시간 이벤트가 반영됐으면([appliedEvents] 가 달라졌으면) 결과를 **버린다** — 왕복이 느릴 때
     * 늦게 도착한 조회 결과가 더 새 실시간 상태를 과거로 되돌리는 걸 막는다. 실패는 조용하다
     * (다음 이벤트·다음 폴백 주기가 메운다).
     *
     * 내용이 똑같은 상세를 다시 흘려 넣어도 재구성은 일어나지 않는다 — [UiState.Success] 와 [Ride] 가
     * data class 라 [MutableStateFlow] 가 같은 값으로 보고 내보내지 않는다 — 들고 있던 것과 비교하는
     * 판정을 따로 두지 않아도 재구성이 생기지 않는다.
     *
     * **읽어 온 목록에서 내가 사라졌는지도 여기서 본다**([hasLostMembership]). SSE 403 이 같은 사실을
     * 먼저 말해 주지만, 그 스트림이 아예 붙지 않는 망에서는 이 조회가 **유일한 창구**다 — 방 상세에는
     * 참여자 검사가 없어 멤버가 아니어도 200 이 오므로, 보지 않으면 내가 없는 방을 계속 그린다.
     * 판정은 **버려진 결과에도** 적용한다: 멤버십은 순번과 무관한 사실이고, 늦게 온 응답이라고
     * 「내가 그 방에 없다」가 뒤집히지는 않는다.
     */
    private suspend fun readDetail(): Boolean {
        val mark = appliedEvents
        val detail = runCatching { repository.getPartyDetailWithVersion(partyId) }.getOrNull() ?: return false
        rememberMyself(detail.ride)
        if (hasLostMembership(sawMyself, detail.ride)) _membershipLost.value = true
        // 가드가 둘인 이유가 다르다: appliedEvents 는 **내 반영**이 끼어들었는지(왕복 지연), acceptsSnapshot 은
        // 읽어 온 상세가 **서버 기준으로** 과거인지(순서 역전). 둘 중 하나만으로는 각자의 경우를 못 막는다.
        if (appliedEvents == mark && acceptsSnapshot(heldVersion, detail.version)) {
            heldVersion = detail.version ?: heldVersion
            publish(detail.ride)
        }
        return true
    }

    /**
     * 서버가 준 멤버 목록에 내가 있었다는 사실을 기록한다 — [hasLostMembership] 의 전제([sawMyself]).
     *
     * 한 번 true 가 되면 되돌리지 않는다. 「전에는 있었는데 지금은 없다」가 바로 상실의 정의다.
     */
    private fun rememberMyself(ride: Ride) {
        if (ride.members.any { it.isMe }) sawMyself = true
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
            // streamEnded 가 combine 입력인 이유: 가려진 사이에 스트림이 종결되면(방 닫힘·404)
            // 복귀 때 다시 붙을 스트림이 없다. 평범한 변수로 두면 패스가 이미 WAIT_THEN_READ 로
            // 확정된 뒤라 READ_NOW 가 영영 타지 않고, 그 전이가 늘 6초 늦는다(QA 125 D-2).
            //
            // _membershipLost 도 같은 이유로 입력이다 — 403 은 streamEnded 와 **같은 순간**에 서지만,
            // 평범한 변수로 읽으면 그 값이 바뀌어도 패스가 다시 평가되지 않아 「내가 없는 방」을
            // 6초마다 계속 읽는다. 판정을 입력으로 끌어올려 상실이 서는 즉시 루프가 죽게 한다.
            combine(visible, _partyClosed, streamEnded, _membershipLost) { shown, closed, ended, lost ->
                fallbackPollStep(
                    visible = shown,
                    partyClosed = closed,
                    fallbackDone = fallbackDone,
                    streamEnded = ended,
                    membershipLost = lost,
                )
            }.collectLatest { step ->
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

    /**
     * 「그만 찾기」·「방 나가기」 — 성공하면 Route 가 홈으로 보낸다.
     *
     * **403 `NOT_PARTY_MEMBER` 는 여기까지 실패로 오지 않는다** — 데이터 계층이 성공으로 되돌린다
     * (`RemoteRideRepository.leaveParty`). 그래야 204 응답이 유실된 사용자가(터널·타임아웃)
     * 「나가지 못했어요」를 보고 다시 눌렀을 때 **같은 문구의 403** 에 갇히지 않는다 — 그 상태에서는
     * 로컬 기억과 홈 배너가 그 방을 계속 들고 있어 화면상 나는 아직 그 방에 있다.
     *
     * 남은 실패는 [leaveFailureMessage] 가 사유별로 가른다 — 「이미 끝난 방」과 「내가 도착을 눌러
     * 묶인 방」이 둘 다 409 인데 **다음 행동이 정반대**다(홈으로 돌아간다 / 출발을 기다린다).
     */
    fun leaveParty() = runAction(
        failureMessage = LEAVE_FAILED_MESSAGE,
        messageOf = { (it as? PartyLeaveException)?.let(::leaveFailureMessage) },
    ) {
        // 나가기 직전 들고 있던 방 — 퍼널에서 빠져나갈 때 몇 명이 모여 있었는지가 「왜 포기했나」의 첫 단서다
        val held = heldRide()
        // 나가는 주체는 Bearer 토큰이 정한다 — memberId 를 넘기지 않는다 (22 보고서 §2)
        repository.leaveParty(partyId)
        _actionState.update { it.copy(left = true) }
        if (held != null) {
            Analytics.track(AnalyticsEvent.MatchCanceled(capacity = held.capacity, memberCount = held.members.size))
        }
    }

    // 「합승 완료」(`finishParty`)는 **지웠다** — 서버가 `POST /rooms/{id}/finish` 를 없앴다
    // (`b16551b7`: 「구버전 종료 경로(/finish)…를 지운다」). 방을 끝내는 길은 [departParty] 하나다.
    //
    // 함께 지운 것: `FINISH_FAILED_MESSAGE` · `finishFailureMessage` · `isFinishStage` ·
    // `canFinishParty` · `ActionState.finished`. 그 403 `NOT_PARTY_HOST` 문구가 들고 있던 사실
    // (「방장만 끝낼 수 있다」)은 **사라지지 않았다** — 출발이 방장 전용이 되면서 그 사유가
    // [departFailureMessage] 로 옮겨 갔고, 비방장용 안내문은 [departHostNotice] 로 옮겼다.
    //
    // `finish_party` 분석 이벤트는 **[departParty] 가 이어 받는다** — 아래 KDoc 참고.

    /**
     * 「약속 시간 정하기」 — 사용자가 **다이얼로그에서 본 그 시각**([departureAt], ISO-8601)을 보낸다.
     *
     * ### 왜 분(minutes)이 아니라 시각을 받나 ★
     * 예전에는 분만 받아 여기서 [clock] 으로 다시 만들었다. 그러면 시계를 **두 번** 읽게 되고,
     * 다이얼로그를 띄운 뒤 분이 바뀌면 **사용자가 동의한 시각과 저장되는 시각이 달라진다**
     * (실측 2026-11-06: 「오전 10:59에 만날까요?」로 묻고 11:00 으로 확정됐다). 확정은 **한 번만**
     * 할 수 있고(409 `ALREADY_CONFIRMED`) 그 시각에 사람들이 모이므로, 동의한 값과 보내는 값이
     * 갈리면 안 된다.
     *
     * 예전 주석은 「화면이 만들면 1시간 한도에 가까워진다」고 적었는데 **반대다.** 캡처한 값은
     * `고른시각 + 60분` 이라 제출 시점의 `지금 + 60분` 보다 늘 **앞**이다 — 오히려 한도에서 멀어진다.
     * 재계산 쪽이 한도에 정확히 걸터앉아 앱·서버 시계 오차에 취약했다.
     *
     * 다이얼로그를 **10분 넘게** 열어 두면 캡처한 값이 과거가 되어 서버가 400
     * `INVALID_MEETING_TIME` 으로 거절한다 — [confirmFailureMessage] 가 「더 가까운 시간으로 골라
     * 주세요」로 답하므로 사용자가 할 일이 분명하다. 조용히 다른 시각을 보내는 쪽보다 낫다.
     *
     * ### 만남 장소를 **함께** 보낸다 ★ (2026-11-12)
     * 서버가 `departureLat`·`departureLng`·`departure` 를 **필수**로 만들었다(`2732cc75`). 넷 중
     * 하나라도 빠지면 400 `INVALID_REQUEST` 이고, 그 400 은 [PartyConfirmException] 의 어느 사유도
     * 아니라 **일반 문구로만** 보인다 — 가장 찾기 어려운 실패다.
     *
     * 앱은 **방의 지금 출발지를 그대로** 보낸다(채팅으로 다른 곳을 정하는 UI 는 아직 없다). 그래서
     * 확정은 출발지를 바꾸지 않지만, 서버 쪽에서는 그게 「출발지를 이 값으로 덮어쓴다」는 뜻이다.
     *
     * ⚠️ **좌표가 없으면 아예 보내지 않는다**([meetingPlaceOrNull]). 0.0 으로 채우면 400
     * `OUT_OF_SERVICE_AREA` 이고 그건 **방을 끝낼 길이 사라지는** 실패다. 그 대신 사실을 말하고
     * 상세를 다시 읽어 둔다 — 상세 응답에는 좌표가 있으므로 다시 누르면 통과한다.
     *
     * ### 성공하면 그 자리에서 고친다
     * 서버가 204 를 주면 **확정은 사실**이다. SSE `confirmed` 가 보통 곧 오지만 그 스트림은
     * at-most-once 이고 망에 따라 아예 붙지 않으므로, 기다리지 않고 들고 있는 방을 고친다
     * ([Ride.withMeetingConfirmed]). 그러면 같은 프레임에 「약속 시간 정하기」가 사라지고 약속 시각이 뜬다.
     *
     * **나가기는 그대로 열려 있다.** 예전 이 자리의 주석은 「확정과 함께 나가기가 막힌다」고 적었는데,
     * 서버가 그 규칙을 버렸다(`b16551b7`) — 지금 나가기를 막는 것은 **도착을 누른 본인**뿐이다
     * ([canLeaveParty]).
     *
     * 상세를 다시 읽지 않는다 — 내가 보낸 값이 그대로 확정된 것이라 서버에 물어 더 알아낼 것이 없다.
     */
    fun confirmMeeting(departureAt: String) {
        // 좌표를 모르면 **요청을 만들지 않는다.** runAction 밖에서 거르는 이유: 이건 서버 실패가
        // 아니라 앱이 보낼 값을 못 갖춘 것이고, 할 일(상세 재조회)이 액션 재시도와 다르다.
        val place = heldRide()?.let(::meetingPlaceOrNull)
        if (place == null) {
            _actionState.update { it.copy(errorMessage = CONFIRM_PLACE_UNKNOWN_MESSAGE) }
            viewModelScope.launch { readDetail() }
            return
        }
        runAction(
            failureMessage = CONFIRM_FAILED_MESSAGE,
            messageOf = { (it as? PartyConfirmException)?.let(::confirmFailureMessage) },
        ) {
            repository.confirmMeeting(
                partyId,
                departureAt,
                latitude = place.latitude,
                longitude = place.longitude,
                placeName = place.name,
            )
            // 보낸 장소가 곧 방의 새 출발지다(서버 `Party.confirm` 이 덮어쓴다) — SSE `confirmed` 가
            // 같은 셋을 들고 오지만 그 프레임을 기다리지 않는다.
            heldRide()?.let {
                publish(
                    it.withMeetingConfirmed(
                        departureAt = departureAt,
                        departureLat = place.latitude,
                        departureLng = place.longitude,
                        departurePlace = place.name,
                    ).ride,
                )
            }
        }
    }

    /**
     * 「출발지 도착했어요」 — 기기의 **현재 좌표**를 보낸다.
     *
     * ### 거리는 서버가 잰다 ★
     * 앱은 출발지와의 거리를 재지 않고 좌표만 보낸다. 서버가 PostGIS 로 반경을 재고(실시간 100m ·
     * 예약 400m — [arrivalRadiusMeters]) 400 `TOO_FAR_FROM_DEPARTURE` 로 답한다
     * ([arriveFailureMessage] 가 그 사유를 방 종류에 맞는 숫자로 보여 준다). 앱이 같은 거리를 다시
     * 재면 **서버는 받아 주는데 앱이 막는** 경우가 생긴다 — 근거는 [canArriveAtDeparture] KDoc.
     *
     * ### ⚠️ 누르면 **이 방에 묶인다**
     * 도착을 누른 멤버는 나갈 수 없다(409 `ARRIVED_MEMBER_CANNOT_LEAVE`) — 도착한 멤버가 곧
     * 탑승자이고 택시비 정산 대상이라 장부에서 빠질 수 없기 때문이다. **도착 취소 API 는 없다.**
     * 그래서 화면이 누르기 전에 그 사실을 말한다([ARRIVE_LOCKS_IN_NOTICE]). 예전에 사용자를 묶던
     * 것은 「약속 확정」이었는데 서버가 둘을 맞바꿨다(`b16551b7`).
     *
     * ### 멱등이다
     * 이미 도착한 사람이 또 눌러도 서버는 204 로 받는다(도착 시각을 유지하고 version·SSE 를 올리지
     * 않는다). 그래서 204 가 유실돼 다시 누르는 경로가 실패로 끝나지 않는다 — 나가기의 403 되돌림
     * ([PartyLeaveException] KDoc)과 같은 성질을 서버가 제공하는 셈이다.
     *
     * 성공하면 내 줄을 그 자리에서 **도착으로 고친다**. `member-arrived` 를 기다리지 않는 이유는
     * 확정과 같다(at-most-once · 망에 따라 안 붙는다). 고치지 않으면 버튼을 누른 사람이 아무 변화도
     * 못 보고 같은 버튼을 다시 누른다.
     */
    fun arriveAtDeparture(latitude: Double, longitude: Double) = runAction(
        failureMessage = ARRIVE_FAILED_MESSAGE,
        // 반경 숫자가 방 종류로 갈리므로 **들고 있는 방**을 함께 넘긴다. 방을 모르는 상태(로딩·
        // 에러)에서 이 액션이 돌 수는 없지만, 그래도 실시간으로 떨어뜨린다 — 더 엄한 숫자(100m)를
        // 말하는 쪽이 「더 가까이 가라」는 지시로서 안전하다.
        messageOf = { e ->
            (e as? PartyArriveException)?.let {
                arriveFailureMessage(it, heldRide()?.roomType ?: RoomType.REALTIME)
            }
        },
    ) {
        repository.arriveAtDeparture(partyId, latitude = latitude, longitude = longitude)
        val held = heldRide()
        val myPublicId = held?.members?.firstOrNull { it.isMe }?.id
        // 내가 목록에 없거나 식별자가 없으면 고칠 줄이 없다 — 서버는 받았으니 다음 스냅샷·폴백이
        // 맞춘다. 없는 줄을 만들어 끼우지는 않는다(목록의 유일한 근거는 서버다).
        if (held != null && !myPublicId.isNullOrBlank()) {
            // 내 줄은 목록에 있는 것이 확인됐으니 needsDetail 은 늘 false 다 — 상세를 또 읽지 않는다.
            publish(held.withMemberArrived(myPublicId).ride)
        }
    }

    /**
     * 「출발」 — **방을 끝내는 유일한 길**이다. 누르면 **모두의** 방이 FINISHED 가 된다.
     *
     * 옛 「합승 완료」(`/finish`)가 서버에서 사라지면서 이쪽이 그 역할까지 이어 받았다. 조건은 전혀
     * 다르다:
     *
     * | | 옛 「합승 완료」 | 지금 「출발」 |
     * |---|---|---|
     * | 방 상태 | 정원이 찬(COMPLETED) 방 | **확정된** 방 |
     * | 누가 | 방장 | **도착한 방장**(403 `NOT_PARTY_HOST` · 409 `NOT_ARRIVED`) |
     * | 시각 | 아무 때나 | 약속 시각 **뒤**(409 `MEETING_NOT_STARTED`) |
     * | 남기는 것 | 없음 | **탑승 기록** — 도착을 누른 멤버만 그 방의 탑승자가 된다 |
     *
     * ### 방장 조건을 **베껴 온다** ★ (방향이 뒤집혔다)
     * 예전 이 자리의 주석은 「방장 조건을 베껴 오지 않은 것이 중요하다 — 막으면 방장이 늦는 방이
     * 영원히 출발하지 못한다」였다. 서버가 그 트레이드오프를 **반대로 골랐다**(`b16551b7`: 「출발은
     * 도착한 방장만 누른다」). 그래서 지금 방장이 오지 않는 방은 **실제로 출발할 수 없고**, 앱이 할
     * 수 있는 일은 그 사실을 비방장에게 말해 주는 것뿐이다([departHostNotice]).
     *
     * ### 미도착자에게 생기는 일이 바뀌었다
     * **노쇼 제재는 없다**(서버가 기능을 통째로 지웠다). 대신 도착하지 않은 멤버는 탑승 기록·탑승
     * 횟수·**택시비 정산 대상**에서 빠진다 — 화면이 **누르기 전에** 그 인원을 숫자로 말해 준다
     * ([departConfirmMessage]).
     *
     * ### `finish_party` 를 여기서 기록한다
     * 그 이벤트는 「합승 완료」가 성공했을 때의 기록이었다. 그 버튼이 사라졌으니 **기록 지점도 함께
     * 사라질 뻔했다** — 그러면 Mixpanel 퍼널의 마지막 단계가 조용히 0 이 된다. 이벤트가 세는 사실
     * (「방이 끝났다」)은 그대로이고 끝내는 버튼만 바뀐 것이므로 여기로 옮긴다. `member_count` 도
     * 뜻을 지킨다 — **방을 끝낼 때 방에 있던 인원**이다(도착한 인원이 아니다. 그건 다른 숫자라
     * 같은 프로퍼티에 담으면 대시보드가 조용히 다른 값을 보게 된다).
     *
     * 뒤처리: 서버가 방을 닫고 구독을 끊으므로 [ActionState.departed] 로 홈에 보낸다
     * (SSE `closed` 가 오면 [_partyClosed] 로도 같은 길이다 — 둘 중 먼저 온 쪽이 이긴다).
     */
    fun departParty() = runAction(
        failureMessage = DEPART_FAILED_MESSAGE,
        messageOf = { (it as? PartyDepartException)?.let(::departFailureMessage) },
    ) {
        // 끝내기 직전 들고 있던 방의 인원. iOS 와 같이 들고 있는 방이 없으면 0 명으로 싣는다
        val memberCount = heldRide()?.members?.size ?: 0
        repository.departParty(partyId)
        _actionState.update { it.copy(departed = true) }
        Analytics.track(AnalyticsEvent.PartyFinished(memberCount = memberCount))
    }

    private fun heldRide(): Ride? = (_uiState.value as? UiState.Success)?.ride

    /**
     * 화면 상태에 방을 싣는 **유일한 길**. SSE 스냅샷·변경분·상태 전이·상세 재조회 어느 쪽으로 알게 되든
     * 여기를 지나므로 `complete_match` 판정도 한 자리다(iOS `publish` 와 같다).
     */
    private fun publish(ride: Ride) {
        _uiState.value = UiState.Success(ride)
        trackMatchCompletionIfNeeded(ride)
    }

    /**
     * 핵심 퍼널 3단계 `complete_match` — 방이 **정원을 채운 것**([isMatchFull])을 이 ViewModel 이
     * **처음 본 순간** 한 번만 기록한다. 처음부터 찬 방(마지막 자리를 채운 합류)도 첫 스냅샷에서 1회 센다.
     */
    private fun trackMatchCompletionIfNeeded(ride: Ride) {
        if (matchCompletionTracked || !isMatchFull(ride)) return
        matchCompletionTracked = true
        Analytics.track(
            AnalyticsEvent.MatchCompleted(
                capacity = ride.capacity,
                memberCount = ride.members.size,
                waitedSeconds = ((clock() - enteredAtMillis) / 1000).toInt(),
            ),
        )
    }

    /**
     * 액션 공통: 중복 실행 차단 → 진행 표시 → 실패 시 한국어 메시지.
     *
     * [messageOf] 가 실패를 **사유별 문구**로 옮긴다 — null 이면 [failureMessage] 로 떨어진다.
     * 기본값이 "항상 null" 이라 사유를 가르지 않는 액션은 예전과 똑같이 동작한다. 지금은 네 액션
     * (나가기·확정·도착·출발) 다 사유를 가르므로 기본값을 쓰는 호출부가 없지만, 남겨 둔다 —
     * 새 액션이 사유를 가르기 전에도 돌아야 한다.
     */
    private fun runAction(
        failureMessage: String,
        messageOf: (Throwable) -> String? = { null },
        block: suspend () -> Unit,
    ) {
        if (_actionState.value.inProgress) return
        viewModelScope.launch {
            _actionState.update { it.copy(inProgress = true, errorMessage = null) }
            try {
                block()
                _actionState.update { it.copy(inProgress = false) }
            } catch (e: Exception) {
                _actionState.update { it.copy(inProgress = false, errorMessage = messageOf(e) ?: failureMessage) }
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
 * **정원이 찼는가** — `complete_match` 의 판정이다(iOS `isMatchFull` 과 **같은 식이어야 한다**).
 *
 * ```
 * ride.capacity > 0 && ride.members.size >= ride.capacity
 * ```
 *
 * ### status 로 세던 것이 거짓이 됐다 ★ (2026-11-12)
 * 오래 `status == MATCHED`(서버 COMPLETED) 하나였고, 그때는 그게 「정원 충족」의 유일한 표현이었다.
 * 서버가 그 값의 **뜻을 바꿨다**(`b16551b7`): COMPLETED 는 이제 **방장이 약속을 확정한 실시간 방**이고,
 * **정원이 차도 status 는 ACTIVE** 다(참여만 `PARTY_FULL` 로 막는다).
 *
 * 그대로 두면 퍼널이 **양쪽으로** 거짓을 말했다:
 *
 * | 방 | 옛 판정 | 참 |
 * |---|---|---|
 * | 3인 방에 2명 모인 **확정** 방 | 매칭 완료로 **센다** | 정원이 차지 않았다 |
 * | 3인 방에 3명 모인 **미확정** 방 | 세지 **않는다** | 정원이 찼다 |
 *
 * **정원 판정의 유일한 참은 `members.size >= capacity` 다.** 목록이 유실될 수 있는 신호로 만든 값이라
 * 틀릴 수 있지만(그래서 예전에 status 를 골랐다) 지금 status 는 **아예 다른 사실**을 말하므로 비교
 * 대상이 아니다. 어긋남은 SSE version 규칙이 상세 재조회로 메운다([deltaVerdict]).
 *
 * ### `capacity > 0` 가 조건인 이유 ★
 * 목록·추천·더미 응답으로 만든 [Ride] 는 `capacity` 가 0 이다 — 그건 「정원이 0명」이 아니라
 * **「정원을 모른다」**다. `0 >= 0` 이 참이므로 가드가 없으면 **방금 만든 1인 방까지 전부 매칭
 * 완료로 센다.** 모르는 것을 「찼다」로 세면 퍼널 3단계가 1단계와 같은 숫자가 된다.
 *
 * 방이 닫혔는지는 **보지 않는다.** 예전 판정이 그걸 겸했지만(COMPLETED·CANCELED 는 거짓이었다)
 * 이 함수가 세는 것은 「정원이 찼다」는 사실 하나이고, 호출부가 21 대기 화면의 `publish` 하나뿐이라
 * 닫힌 방이 여기 닿기 전에 Route 가 화면을 떠난다. 닫힘을 다시 보면 **같은 조건이 두 곳에** 생긴다.
 */
internal fun isMatchFull(ride: Ride): Boolean = ride.capacity > 0 && ride.members.size >= ride.capacity

/**
 * 21 대기 화면이 머무는 단계 — 모집 중(서버 ACTIVE)이거나 **약속이 확정된**(서버 COMPLETED) 상태.
 *
 * 「정원이 찬」이 아니다 — 서버가 COMPLETED 의 뜻을 「방장이 약속을 확정했다」로 바꿨다(`b16551b7`).
 * 두 값을 함께 통과시키는 결론은 그대로다: 21 은 모집부터 출발까지를 한 화면에서 다루므로 **방이
 * 닫히기 전까지 전부** 이 화면의 단계다.
 *
 * 이 밖은 **닫힌 방뿐이다**(서버 FINISHED·CANCELED) — 기사·배차를 걷어낸 뒤로 대기 단계를 벗어나는
 * 길이 그 하나로 줄었다. 그래서 이 판정이 false 가 되면 폴백 폴링을 끝낸다: 닫힌 방을 다시 읽어도
 * 「닫혔다」만 재확인하고, Route 가 이미 화면을 떠나게 한다.
 */
internal fun isWaitingForMembers(status: RideStatus): Boolean =
    status == RideStatus.RECRUITING || status == RideStatus.CONFIRMED

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
 * | 구독하지 않는다 | [streamEnded] 가 true | 종결로 끝난 스트림이다(방 닫힘·404 방 없음·403 멤버 아님·스트림 없는 저장소). 다시 붙어도 같은 답이고, 게이트 이전에도 다시 붙지 않았다 |
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
 * (17 탐색이 예전에 쓰던 `NearbyPollStep` 과 같은 모양이다 — 17 은 지금 시계 `ExploreRefreshCycle` 의 세션이 가시성을 본다.)
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
 * 폴링을 화면 가시성에 묶는 판정을 이 한 곳에 모은다(17 탐색에서는 `ExploreRefreshCycle` 의 세션이 같은 역할을 한다).
 * 위에서부터 읽는다 — 앞의 조건이 뒤를 가린다.
 *
 * | 판정 | 조건 | 이유 |
 * |---|---|---|
 * | [FallbackStep.STOP] | [visible] 이 false | **이 함수의 존재 이유.** 보이지 않는 화면이 서버를 부를 이유가 없다 (QA 123 D-3) |
 * | [FallbackStep.STOP] | [partyClosed] | 서버가 방이 닫혔다고 알려 왔다 — 닫힌 방을 다시 읽어도 「닫혔다」만 재확인한다. **기존 종료 조건** |
 * | [FallbackStep.STOP] | [fallbackDone] | 방이 대기 단계를 벗어나 루프가 이미 끝났다. **기존 종료 조건** — 복귀로 되살아나면 회귀다 |
 * | [FallbackStep.STOP] | [membershipLost] | **내가 이 방 멤버가 아니다.** 읽어도 남의 방이고, Route 가 곧 화면을 떠난다 |
 * | [FallbackStep.READ_NOW] | [streamEnded] | 다시 붙을 스트림이 없다(종결·더미). 복귀 갱신을 맡을 스냅샷이 없으므로 6초를 기다리지 않는다 |
 * | [FallbackStep.WAIT_THEN_READ] | 그 밖 | 평상시. 복귀 즉시 재구독이 스냅샷을 가져오므로, 여기서 또 읽으면 복귀마다 상세 1건이 덧붙는다 |
 *
 * 가시성이 맨 앞인 게 요점이다 — **보이지 않는 동안에는 나머지 조건을 보지 않는다.**
 * 반대로 [partyClosed]·[fallbackDone]·[membershipLost] 는 「끝났다」는 사실이라 복귀해도 되돌아가지
 * 않는다: 가시성은 스위치, 나머지는 종결이다.
 *
 * **[membershipLost] 가 [streamEnded] 보다 위에 있는 게 요점이다.** 그 둘은 403 에서 **같은 순간에**
 * 서는데, 순서가 뒤집히면 「다시 붙을 스트림이 없으니 지금 읽어라」(READ_NOW)가 이겨서 **내가 들어
 * 있지 않은 방**을 6초마다 영원히 읽는다 — 이 함수가 고치는 결함이 정확히 그것이었다.
 *
 * @param visible 화면이 사용자에게 보이는가(`LifecycleStartEffect` 로 받는다)
 * @param partyClosed 서버가 `closed` 를 보냈는가([PartyEvent.Closed])
 * @param fallbackDone 대기 단계를 벗어나 루프가 끝났는가([isWaitingForMembers] false 를 본 뒤)
 * @param streamEnded SSE 가 종결로 끝나 재구독할 것이 없는가([shouldObservePartyEvents])
 * @param membershipLost 내가 이 방 멤버가 아니라고 서버가 말했는가(SSE 403 · 상세의 members 에 내가 없음)
 */
internal fun fallbackPollStep(
    visible: Boolean,
    partyClosed: Boolean,
    fallbackDone: Boolean,
    streamEnded: Boolean,
    membershipLost: Boolean = false,
): FallbackStep = when {
    !visible -> FallbackStep.STOP
    partyClosed || fallbackDone || membershipLost -> FallbackStep.STOP
    streamEnded -> FallbackStep.READ_NOW
    else -> FallbackStep.WAIT_THEN_READ
}

/**
 * 서버가 준 멤버 목록에서 **내가 사라졌는가** — 「유령 대기방」 판정.
 *
 * SSE 403 이 같은 사실을 더 빨리 말해 주지만([PartyMembershipLostException]), 그 스트림이 붙지 않는
 * 망에서는 이 판정이 **유일한 창구**다: `GET /matching/rooms/{partyId}` 에는 참여자 검사가 **없어**
 * 멤버가 아니어도 200 이 오고, 그러면 화면은 남의 방을 「같이 탈 사람을 찾고 있어요」로 그린다.
 *
 * 세 조건을 **모두** 요구한다 — 틀렸을 때 멀쩡히 방에 들어 있는 사람을 쫓아내기 때문이다.
 *
 * | 조건 | 없으면 |
 * |---|---|
 * | [sawMyself] | `isMe` 는 `publicId == 세션 uuid` 라, 세션 uuid 가 없으면(미로그인·복원 전) 아무도 나로 표시되지 않는다 — 정상인 방까지 상실로 읽는다 |
 * | 목록이 비어 있지 않다 | 멤버가 0명인 방은 **있을 수 없다**(마지막 멤버가 나가면 서버가 방을 닫는다). 빈 목록은 응답이 이상하다는 뜻이라 판정 근거가 되지 못한다 |
 * | 목록에 내가 없다 | 상실의 정의 그 자체 |
 *
 * 반대로 **status 는 보지 않는다.** 내가 멤버가 아니라는 사실은 방이 어느 단계인지와 무관하고,
 * ACTIVE 유령 방이 바로 이 결함의 본체였다(닫힌 방은 [PartyEvent.Closed] 가 이미 잡는다).
 */
internal fun hasLostMembership(sawMyself: Boolean, ride: Ride): Boolean =
    sawMyself && ride.members.isNotEmpty() && ride.members.none { it.isMe }

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
/** 변경분 프레임 하나를 어떻게 다룰지 — [deltaVerdict] 의 답. */
internal enum class FrameVerdict {
    /** 순서가 맞다(또는 가릴 수 없다). 들고 있는 방에 그대로 얹는다. */
    Apply,

    /** 이미 반영한 프레임이다. **버린다** — 다시 얹으면 과거로 되돌아간다. */
    Drop,

    /** 중간을 놓쳤거나 스냅샷보다 먼저 왔다. 상세를 1회 다시 읽어 맞춘다(스트림은 끊지 않는다). */
    Resync,
}

/**
 * 받은 변경분의 [incoming] 순번을 들고 있는 [held] 와 견줘 판정한다
 * (서버 계약 원문은 [com.moyeota.domain.model.PartyEvent] 의 표).
 *
 * | [held] | [incoming] | 답 | 왜 |
 * |---|---|---|---|
 * | 무엇이든 | `null` | [Apply] | 순번을 모르는 **옛 서버**다. 가릴 근거가 없으니 예전처럼 그대로 얹는다 |
 * | `null` | 숫자 | [Resync] | 스냅샷을 아직 못 받았다 = **틈**. 서버가 "상세를 다시 받으라"고 적어 둔 경우 |
 * | N | ≤ N | [Drop] | 이미 반영했다 |
 * | N | N+1 | [Apply] | 바로 다음 프레임 |
 * | N | ≥ N+2 | [Resync] | 중간 프레임을 놓쳤다 |
 *
 * **[Drop] 이 이 함수의 존재 이유다.** 서버는 구독을 먼저 등록한 뒤 DB 를 읽어 스냅샷을 보내므로
 * 그 사이 커밋된 변경분이 스냅샷보다 먼저 올 수 있고, 그때 순번 없이 받는 대로 얹으면 뒤늦게 온
 * 낡은 스냅샷이 이미 반영한 합류를 지운다. 지워진 합류가 **정원을 채운 사람**이면 서버는 배차를
 * 시작했는데 화면은 모집 중에 멈추고, 스트림이 살아 있어 재연결 스냅샷도 폴백 폴링도 오지 않는다.
 */
internal fun deltaVerdict(held: Long?, incoming: Long?): FrameVerdict = when {
    incoming == null -> FrameVerdict.Apply
    held == null -> FrameVerdict.Resync
    incoming <= held -> FrameVerdict.Drop
    incoming == held + 1 -> FrameVerdict.Apply
    else -> FrameVerdict.Resync
}

/**
 * 스냅샷(또는 다시 읽은 상세)으로 방을 **통째로 교체해도 되는가**.
 *
 * 변경분과 규칙이 다르다 — 스냅샷은 "+1" 을 요구하지 않는다. 한 덩어리로 완결돼 있어 중간을 건너뛰어도
 * 되기 때문이다. 다만 **들고 있는 것보다 과거면 버려야 한다**: 늦게 도착한 스냅샷이 그 사이 반영한
 * 변경분을 되돌리는 게 정확히 우리가 막으려는 것이다.
 *
 * [held] 나 [incoming] 이 null 이면 가릴 근거가 없으므로 받아들인다(옛 서버·첫 연결).
 */
internal fun acceptsSnapshot(held: Long?, incoming: Long?): Boolean =
    held == null || incoming == null || incoming >= held

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
 *
 * [hostPublicId] 는 서버가 실어 준 **승계 후 방장**이다 — [Ride.withHostSuccession] 으로 함께 옮긴다.
 * 그래서 승계는 **나간 사람을 빼고 난 목록**에 적용된다(서버가 싣는 값도 「이 이벤트를 반영한 뒤의
 * 방장」이다). 그 값이 null·빈 값이거나 남은 목록에 없으면 표시를 손대지 않고
 * [RideDelta.needsDetail] 로 **「방 상세를 다시 받으라」**를 돌려준다 — 서버 계약이다.
 */
internal fun Ride.withMemberLeft(
    publicId: String,
    status: RideStatus,
    hostPublicId: String? = null,
): RideDelta = copy(
    members = members.filterNot { it.id.isNotBlank() && it.id == publicId },
    status = status,
).withHostSuccession(hostPublicId)

/**
 * 방장 승계 반영 — 서버가 `member-left` 에 실어 준 [hostPublicId] 를 멤버 목록의 [User.isHost] 로 옮긴다.
 *
 * **이게 빠지면 방장이 나가도 승계된 사람이 방장 전용 버튼(약속 확정·출발)을 못 본다.** 서버는 멤버가 나갈 때마다
 * 남은 멤버 중 `min(joinedAt, memberId)` 를 방장으로 다시 정하는데(저장값이 아니라 파생값),
 * 그 결과를 알려 주는 신호는 이 한 번뿐이다 — 다시 보내지 않으므로 여기서 버리면 다음 스냅샷
 * (재연결)이나 폴백 상세 조회까지 승계된 사람의 화면이 낡은 채로 남는다.
 *
 * ### 서버 계약 원문 (백엔드 `matching/sse/PartySseController` 의 Swagger description) ★
 * > `member-left`: { publicId, status, hostPublicId, version }. … hostPublicId 는 이 이벤트를 반영한 뒤의
 * > 방장 — 받을 때마다 members 의 host 표시를 이 값으로 다시 지정한다. 단 **null 이거나 목록에 없는
 * > publicId 면 host 표시를 바꾸지 말고 방 상세를 다시 받는다**(이벤트 순서 역전 대비)
 *
 * 그대로 두 갈래다:
 *
 * | 지목된 publicId | 하는 일 | 왜 |
 * |---|---|---|
 * | 목록에 **있다** | 표시를 **통째로 다시 칠한다** — 그 한 명만 true, 나머지는 전부 false | 서버 말이 곧 「방장은 한 명」이므로, 전에 방장이던 사람(또는 나)의 true 를 지우는 것까지가 이 함수의 일이다 |
 * | null·빈 값·목록에 **없다** | 표시를 **그대로 두고** [RideDelta.needsDetail] 을 세운다 | 내 목록이 그 사람을 모르니 다시 칠할 근거가 없다 — 서버에 방 상세를 다시 묻는다 |
 *
 * ### 「목록에 없으면 전원 false」로 되돌리지 말 것 ★
 * 그게 2026-10-15 까지의 동작이었고(근거는 「서버가 나 아닌 누군가를 방장이라 했으니 적어도 *내가
 * 방장이다*는 거짓이다」였다), **서버 계약과 정반대다.** 깨지는 경로가 실제로 있다 — 두 멤버가
 * 연달아 나가 프레임 순서가 역전되면(`u2` 이탈 프레임이 `u1` 이탈 프레임보다 먼저 도착) 뒤늦게 온
 * 프레임이 **이미 나간 사람**을 방장으로 지목한다. 전원 false 로 밀면 그 순간 **진짜 방장의
 * [User.isHost] 가 지워져 약속 확정·출발 버튼을 영구히 잃는다**: [hasMemberDrift] 는 정원 초과만
 * 보므로 못 잡고(목록 길이는 맞다), SSE 가 살아 있으면 폴백 폴링도 돌지 않아([shouldPollFallback])
 * 재연결 스냅샷이나 자동 정리 말고는 복구 수단이 없다 — **방을 끝낼 길이 사라진다.**
 *
 * null·빈 값도 같은 자리로 떨어진다. 서버가 새 방장의 요약을 못 찾은 경우인데
 * (`PartyService.leave` 의 `hostSummary == null`), 모른다고 전부 false 로 밀면 역시 **진짜 방장의
 * 표시를 지운다.** 그래서 손대지 않고 상세로 확인한다.
 *
 * 내용이 같으면 [Ride] 가 data class 라 `copy` 결과가 원본과 `==` 이므로 재구성도 일어나지 않는다.
 */
internal fun Ride.withHostSuccession(hostPublicId: String?): RideDelta {
    val host = hostPublicId?.takeIf { it.isNotBlank() } ?: return RideDelta(this, needsDetail = true)
    // host 가 빈 값이 아니므로 `it.id == host` 는 id 가 빈 멤버(요약 없음)를 자동으로 비껀다 —
    // 빈 문자열끼리 맞아떨어져 엉뚱한 사람이 방장이 되는 일이 없다.
    if (members.none { it.id == host }) return RideDelta(this, needsDetail = true)
    return RideDelta(copy(members = members.map { it.copy(isHost = it.id == host) }))
}

/**
 * 변경분 하나를 적용한 결과 — 새 방과, **서버가 「방 상세를 다시 받으라」고 한 프레임인가.**
 *
 * [needsDetail] 이 서는 자리는 지금 하나다: `member-left` 가 지목한 방장을 내 목록에서 찾지 못한
 * 경우([Ride.withHostSuccession]). 그때는 host 표시를 손대지 않고 서버에 다시 묻는다 — 서버 Swagger
 * 가 그렇게 적어 두었고, 이유는 **이벤트 순서 역전**이다.
 *
 * 왜 [Ride] 안에 담지 않고 따로 내보내나: 이 값은 방의 성질이 아니라 **이 프레임의 처리 지시**다.
 * [Ride] 에 실으면 스냅샷·상세 조회 결과에도 따라붙어 「읽어 온 결과를 보고 또 읽는」 되돌이가 생긴다.
 */
internal data class RideDelta(val ride: Ride, val needsDetail: Boolean = false)

/**
 * [PartyEvent.Confirmed] 적용 — 약속이 정해졌다는 **사실**, 그 시각, 그리고 **바뀐 출발지**를 싣는다.
 *
 * ### status 를 건드리지 않는 이유 ★
 * 서버는 확정과 함께 모집을 끝내므로(ACTIVE→COMPLETED) 「확정됐다」와 「status 가 바뀌었다」는 보통
 * 같이 온다. 그래도 여기서 status 를 바꾸지 않는다 — 프레임이 싣는 status 는 **서버 enum 이름 문자열**
 * ([PartyEvent.Confirmed] 의 `status`)이고, 그걸 [RideStatus] 로 옮기려면 `PartyMappers` 의 매핑을
 * 화면 쪽에 베껴 와야 한다. 사본이 생기면 서버가 값을 늘릴 때 한쪽만 따라간다.
 *
 * 그래서 status 는 다음 스냅샷·폴백 상세에 맡긴다(예전에는 `recruiting-closed` 프레임이 그 재조회를
 * 걸어 주었는데 **서버가 그 이벤트를 없앴다** — `b16551b7`). 그 사이 화면이 틀리지 않는 이유: 확정
 * 뒤의 단계 판정은 [meetingStage] 가 **`isConfirmed` 를 먼저** 보기 때문이다 — status 가 아직
 * RECRUITING 으로 남아 있어도 확정 화면이 그려진다.
 *
 * ### 출발지 셋을 **덮어쓴다** ★ (2026-11-12)
 * 확정은 방의 출발지를 만남 장소로 옮긴다(서버 `Party.confirm`: `departureLocation = plan.departure()`).
 * 무시하면 두 가지가 깨진다: ① 지도가 옛 출발지를 가리킨다 ② **도착 버튼이 틀린 곳을 기준으로
 * 판정**돼 약속 장소에 서 있는 사람이 400 `TOO_FAR_FROM_DEPARTURE` 를 맞는다. ②가 본체다 —
 * 지도는 보고 넘기지만 그쪽은 **방을 끝낼 길이 막힌다**.
 *
 * | 프레임이 실어 온 것 | 하는 일 |
 * |---|---|
 * | 좌표·이름이 **셋 다** 있다 | 방의 출발지를 통째로 교체 |
 * | **일부만** 있다 | 손대지 않고 [RideDelta.needsDetail] — 좌표와 이름이 어긋난 출발지를 만들지 않는다 |
 * | 셋 다 없다 | 손대지 않는다(옛 배포) |
 *
 * 매퍼가 이미 「셋이 다 있거나 전부 null」로 보장하지만(`PartySseEvent`) 그 보장을 **여기서 다시
 * 확인한다** — 타입은 셋 다 nullable 이고, 반쪽으로 덮어쓴 출발지는 지도와 도착 판정이 서로 다른
 * 곳을 가리키게 만든다. 그때 틀렸다는 것을 화면에서는 알 수 없다.
 *
 * [departureAt] 이 null·빈 값이면 들고 있던 값을 **지우지 않는다.** 서버가 시각을 말해 주지 못한
 * 프레임(필드 누락)으로 이미 받아 둔 약속 시각을 날리면, 화면이 「약속 시간이 정해졌어요」로
 * 떨어져 **몇 시에 만나는지를 잃는다** — 지우는 쪽이 늘 더 나쁘다. 단 **양쪽 다 없으면** 상세를
 * 읽는다: 확정됐다는 사실만 알고 **언제**를 모르면 도착 창이 열렸는지 판정할 수 없어
 * ([meetingStage] 가 `CONFIRMED_WAITING` 에 영구히 머문다) 도착 버튼이 영영 뜨지 않는다.
 */
internal fun Ride.withMeetingConfirmed(
    departureAt: String?,
    departureLat: Double? = null,
    departureLng: Double? = null,
    departurePlace: String? = null,
): RideDelta {
    val place = departurePlace?.takeIf { it.isNotBlank() }
    val movedOrigin = departureLat != null && departureLng != null && place != null
    val partialOrigin = !movedOrigin && (departureLat != null || departureLng != null || place != null)

    val mergedDepartureAt = departureAt?.takeIf { it.isNotBlank() } ?: this.departureAt
    val confirmed = copy(
        isConfirmed = true,
        departureAt = mergedDepartureAt,
        origin = if (movedOrigin) place!! else origin,
        originLat = if (movedOrigin) departureLat else originLat,
        originLng = if (movedOrigin) departureLng else originLng,
    )
    return RideDelta(confirmed, needsDetail = partialOrigin || mergedDepartureAt.isNullOrBlank())
}

/**
 * [PartyEvent.MemberArrived] 적용 — [publicId] 로 **그 한 명만** 도착 표시한다.
 *
 * 모르는 publicId 면 **상세를 다시 읽는다**([RideDelta.needsDetail]). 서버가 그렇게 하라고 적어
 * 두었고(「publicId 가 null 이거나 목록에 없으면 방 상세를 다시 받는다」), 근거가 하나 더 있다 —
 * 모르는 publicId 는 **내 멤버 목록이 낡았다**는 뜻이다(그 사람의 합류 신호를 놓쳤다).
 *
 * 낡은 목록을 그냥 두면 [notArrivedCount] 가 실제보다 적어지고, 그 수는 「출발」 확인 문구가
 * **탑승 기록·택시비 정산에서 빠질 사람 수**를 말하는 데 쓰인다(서버가 「도착한 멤버 = 탑승자」로
 * 센다). 적게 말하고 누르게 하는 쪽이, 조회 1건을 더 쓰는 쪽보다 나쁘다 — 같이 탄 사람이 정산
 * 장부에서 사라진다. 그래서 여기서는 [Ride.withMemberLeft] 처럼 「다음 스냅샷에 맡긴다」로 가지 않는다.
 *
 * 빈 publicId 로는 아무도 표시하지 않는다 — 요약이 없는 멤버들(id 가 빈 값)을 한꺼번에 도착으로
 * 만드는 사고를 막는다. 그쪽은 데이터 계층이 프레임째 버려(version 에 구멍이 나) 다음 프레임이
 * 어긋나 보이고, 그 길로 상세를 다시 읽는다.
 */
internal fun Ride.withMemberArrived(publicId: String?): RideDelta {
    val target = publicId?.takeIf { it.isNotBlank() } ?: return RideDelta(this)
    if (members.none { it.id == target }) return RideDelta(this, needsDetail = true)
    return RideDelta(copy(members = members.map { if (it.id == target) it.copy(hasArrived = true) else it }))
}


/**
 * 변경분을 적용한 뒤 **방 상세를 1회 읽어야 하는가** — `MatchWaitingViewModel.applyDelta` 의 판정.
 *
 * 둘 중 하나라도 참이면 읽는다. 사유는 다르지만 처방이 같다(**목록이 아니라 서버를 믿는다**):
 *
 * | 사유 | 뜻 |
 * |---|---|
 * | [RideDelta.needsDetail] | 서버가 지목한 방장이 내 목록에 없다 — **host 표시를 믿을 수 없다** |
 * | [hasMemberDrift] | 서버가 준 status 와 내 멤버 목록이 모순이다 — **목록이 낡았다** |
 *
 * 둘 다 참이어도 **상세는 한 번만** 읽는다. 그리고 읽어 온 결과에는 이 판정을 다시 걸지 않는다
 * (`readDetail` 이 화면 상태를 직접 세운다) — 그래서 **되돌이가 생기지 않는다**: 이벤트 프레임
 * 하나당 추가 조회는 최대 1건이고, 그 조회 결과가 또 조회를 부르는 길이 없다.
 */
internal fun needsDetailAfterDelta(delta: RideDelta): Boolean =
    delta.needsDetail || hasMemberDrift(delta.ride)

// 같은 사람인가 — 서버 식별자(publicId)가 **양쪽에 다 있을 때만** 같다고 본다.
private fun User.isSameMemberAs(other: User): Boolean = id.isNotBlank() && id == other.id

/**
 * 들고 있는 멤버 목록이 **그 자체로 모순**인가 — 변경분이 유실됐거나 겹쳐 쌓였다는 신호.
 *
 * 인원 표시의 근거는 **멤버 목록 하나**다([MatchWaitingScreen] 의 `foundCount` = `members.size`) —
 * 별도의 인원 카운터를 들고 있지 않다(합류 응답의 `currentMembers` 도 21 로 넘기지 않는다). 그래야
 * 「목록엔 3명인데 카운터는 4」 같은 두 번째 진실이 생기지 않는다. 대신 그 하나뿐인 근거가 틀렸을 때
 * 알아챌 길이 필요하고, 이 함수가 그 자리다 — 모순을 보면 상세를 1회 읽어 **서버 쪽으로 맞춘다.**
 *
 * ### 판정 근거가 바뀌었다 ★ (2026-11-12)
 * 예전에는 **서버가 준 status 와 목록**을 견줬다:
 *
 * ```
 * MATCHED     -> members.size < capacity   // 정원이 찼다는데 목록이 모자라다
 * RECRUITING  -> members.size >= capacity  // 모집 중인데 목록이 정원을 채웠다
 * ```
 *
 * **두 줄 다 모순이 아니게 됐다.** 서버가 COMPLETED 의 뜻을 「방장이 약속을 확정했다」로 바꾸면서
 * (`b16551b7`) ① 2/3 짜리 확정 방이 정상이고 ② **정원이 찬 방도 ACTIVE** 다. 즉 status 로 인원
 * 어긋남을 잡을 길이 아예 없어졌다 — 그 두 줄을 남겨 두면 **영원히 false 인 함수**가 되어
 * 「상세를 다시 읽는 일이 영원히 없다」는 더 나쁜 상태가 된다.
 *
 * ### 남은 유일한 참 — 정원 **초과**
 * 서버는 정원을 넘는 합류를 받지 않는다(`Party.join` 이 `PARTY_FULL` 로 막는다). 그러므로
 * `members.size > capacity` 는 서버 상태로는 **있을 수 없고**, 봤다면 내 목록이 부풀었다는 뜻이다 —
 * 같은 합류를 두 번 얹었거나 이탈 프레임을 놓쳤다. 정원 **미달**은 이제 아무것도 뜻하지 않으므로
 * (언제든 정상이다) 보지 않는다.
 *
 * 정원을 모르는 방(`capacity` 0 — 목록·추천·더미 응답)은 비교 기준이 없어 판단하지 않는다.
 *
 * ### 이 함수가 그물의 **전부가 아니다** — 주 수단은 version 이다
 * 이 판정으로 잡히는 범위가 좁아진 대신, 더 촘촘한 그물이 이미 깔려 있다:
 *
 * | 수단 | 잡는 것 |
 * |---|---|
 * | [deltaVerdict] 의 `Resync` | 프레임을 놓쳤다(version +2 이상) · 스냅샷 전에 변경분이 왔다 — **인원수가 같은 멤버 교체까지** 잡는다 |
 * | [acceptsSnapshot] | 늦게 온 낡은 스냅샷이 반영분을 되돌리는 것 |
 * | 재연결 스냅샷 | 방 상세 **전체**를 다시 준다(가시성 복귀마다) |
 * | 폴백 폴링 | SSE 가 죽은 동안 6초마다 상세 |
 * | [RideDelta.needsDetail] | 서버가 지목한 방장·도착자가 내 목록에 없다 · 확정 프레임의 출발지가 반쪽이다 |
 *
 * version 이 **null 인 옛 배포**에서는 그 첫 줄이 작동하지 않고([deltaVerdict] 가 전부 `Apply`),
 * 거기서는 이 함수와 재연결 스냅샷이 유일한 그물이다 — 그래서 지우지 않고 **근거를 바꿔** 남겼다.
 */
internal fun hasMemberDrift(ride: Ride): Boolean =
    ride.capacity > 0 && ride.members.size > ride.capacity

/**
 * 서버가 나가기를 허용하는가 — **대기 단계이고, 내가 아직 도착을 누르지 않았다.**
 *
 * ```
 * isWaitingForMembers(ride.status) && !hasArrivedMyself(ride)
 * ```
 *
 * 닫힌 방(FINISHED·CANCELED)에서 거짓인 것은 그대로다. 서버가 방을 닫은 순간과 Route 가 그걸
 * 알아채 화면을 떠나는 순간 사이에 한 프레임이 있고, 그 프레임에서 눌린 나가기는 409
 * `PARTY_CLOSED` 로만 끝난다([leaveFailureMessage]).
 *
 * ### 확정은 더 이상 나가기를 막지 않는다 ★ (2026-11-12 — 이 작업의 사용자 영향 1순위)
 * 2026-11-06 부터 이 함수는 `&& !ride.isConfirmed` 를 들고 있었고, 그 KDoc 은 「확정된 방에서는
 * 아무도 나갈 수 없다」를 ★ 로 못 박고 있었다. **서버가 그 규칙을 버렸다**(`b16551b7`: 「확정 방에서도
 * 나갈 수 있고, 확정 방에서 혼자 남으면 닫는다」).
 *
 * 그 한 줄이 가두고 있던 범위가 넓었다 — [Ride.isConfirmed] 는 **예약 방에서 늘 true** 이므로
 * (`isReservation() || departureAt != null`) 확정 방뿐 아니라 **모든 예약 방**에서 「그만 찾기」가
 * 비활성이었다. 들어간 사람에게 방을 벗어날 길이 없었고, 서버가 그 계정의 다른 방 합류·방 만들기·
 * 예약까지 전부 막으므로(`ALREADY_JOINED_OTHER_PARTY`) **앱 안에서 복구할 방법이 없었다.**
 *
 * 서버가 허용하는 일을 앱이 막는 실패는 늘 이렇게 비대칭이다: 반대 방향(앱이 열어 두고 서버가
 * 거절)은 문구 한 줄로 끝나지만, 이쪽은 사용자를 가둔다.
 *
 * ### 지금 막는 것은 **도착** 하나다
 * 서버가 거절하는 유일한 사유가 409 `ARRIVED_MEMBER_CANNOT_LEAVE` 다 — 도착을 누른 멤버가 곧
 * 탑승자이고 택시비 정산 대상이라 장부에서 빠질 수 없기 때문이다. 조건이 「방이 어떤 상태인가」가
 * 아니라 **「내가 무엇을 눌렀는가」**로 바뀐 것이고, 그 판정은 [hasArrivedMyself] 가 이미 들고 있다.
 *
 * 숨기지 않고 **비활성 문구로 바꾸는** 이유는 그대로다: 이 자리가 비면 뒤로 갈 길이 사라진 것처럼
 * 보인다. 버튼은 남기고 「나갈 수 없다」는 사실과 **그 사유**를 적는다(헤더의 뒤로가기는 **방을
 * 유지한 채** 화면만 벗어나는 길로 그대로 열려 있다 — [MatchWaitingRoute] 의 `onExitKeepingParty`).
 *
 * @param ride 방 전체를 받는다 — status 와 내 도착 여부가 **같은 스냅샷**이어야 한다
 */
internal fun canLeaveParty(ride: Ride): Boolean =
    isWaitingForMembers(ride.status) && !hasArrivedMyself(ride)

// `isFinishStage`·`canFinishParty` 는 **지웠다.** 둘 다 「정원이 찼다」를 `status == MATCHED` 로
// 세고 있었고, 그 식이 거짓이 됐다(서버 COMPLETED = 확정 — [isMatchFull] KDoc 의 표).
// 가리킬 버튼(「합승 완료」)도 서버에서 사라졌으므로 근거와 쓰임이 함께 없어진 셈이다.
//
// 그 둘이 갈라 들던 질문(「방이 어떤 단계인가」 / 「내가 누를 수 있는가」)은 지금 각각
// [Ride.isConfirmed] 와 [meetingStage] 가 맡는다 — 방장 여부로 갈리는 자리는 `DEPART_BY_ME` 와
// `DEPART_BY_HOST` 가 타입으로 가른다.

/**
 * 사유를 가릴 수 없는 나가기 실패의 일반 문구 — 네트워크 실패와 **모르는 코드**가 여기로 떨어진다.
 *
 * 「이미 나갔다」(403)가 여기 오지 않는 것이 핵심이다 — 그건 실패가 아니라 **이미 됨**이라 데이터
 * 계층이 성공으로 되돌린다([PartyLeaveException] KDoc). 되돌림이 없던 동안에는 이 문구가 「서버는
 * 나를 내보냈는데 앱만 모르는」 상태의 영구 실패 안내였다.
 */
private const val LEAVE_FAILED_MESSAGE = "방에서 나가지 못했어요"

/**
 * 나가기 실패를 사용자 문구로 옮긴다 — 실패마다 **다음 행동이 다르다**
 * ([MeetingFlow] 의 세 액션과 같은 자리·같은 이유).
 *
 * | 사유 | 서버 코드(HTTP) | 사용자가 할 일 |
 * |---|---|---|
 * | **내가 도착을 눌렀다** | `ARRIVED_MEMBER_CANNOT_LEAVE` (409) | 나갈 수 없다 — 출발을 기다린다 |
 * | 방이 닫혔다 | `PARTY_CLOSED` (409) | 홈으로 돌아간다 |
 * | 방 없음 | `PARTY_NOT_FOUND` (404) | 홈으로 돌아간다(이미 닫힌 방이다) |
 * | 그 밖·네트워크 | 코드 없음 | [LEAVE_FAILED_MESSAGE] |
 *
 * ### 두 409 가 **정반대 행동**을 가리킨다 ★ 이 함수의 존재 이유
 * `PARTY_CLOSED` 는 「이미 끝난 방」이라 **떠나야** 하고, `ARRIVED_MEMBER_CANNOT_LEAVE` 는
 * 「아직 살아 있는데 내가 묶인 방」이라 **머물러야** 한다. 상태 코드로 가르면 절반이 반드시 틀린다.
 *
 * ### 첫 줄의 사유가 바뀌었다 (2026-11-12)
 * 예전 첫 줄은 `CANNOT_LEAVE_CONFIRMED_PARTY` 였고 문구는 「약속이 정해진 방은 나갈 수 없어요.
 * 약속 시간에 출발지에서 만나 주세요」였다. **서버가 그 규칙과 코드를 함께 지웠다**(`b16551b7`) —
 * 확정 방에서도 나갈 수 있다. 지금 나가기를 막는 유일한 사유는 **내가 도착을 눌렀는가**이고,
 * 되돌릴 길이 없다(도착 취소 API 가 없다). 그래서 문구가 「만나 주세요」에서 「출발을 기다려
 * 주세요」로 바뀐다 — 그 사람은 이미 출발지에 서 있다.
 *
 * 둘째 줄의 코드도 `PARTY_NOT_RECRUITING` → `PARTY_CLOSED` 로 통일됐다(같은 커밋이 `Party` 의 단계
 * 검사를 `validateOpen()` 하나로 묶었다). 사용자 문구는 그대로다 — 어느 이름이든 사실은 같다.
 *
 * 「이미 나갔다」(403 `NOT_PARTY_MEMBER`)는 **이 함수에 오지 않는다** — 데이터 계층이 성공으로
 * 되돌리므로 화면은 나가기 성공과 같은 길(홈)로 간다.
 *
 * 「잠시 후 다시 시도」는 넣지 않는다: 다시 눌러도 같은 409 다. 화면은 두 사유 모두에서 버튼을 이미
 * 비활성으로 바꾸지만([canLeaveParty]) 전이를 알아채기까지 한 프레임이 비고, 그 틈에 눌린 것이
 * 여기로 온다.
 */
internal fun leaveFailureMessage(e: PartyLeaveException): String = when {
    // 끝난 방이 아니라 **내가 도착을 눌러 묶인 방**이다 — 홈으로 돌아가라고 하면 안 된다.
    // 도착을 되돌릴 길이 없으므로(취소 API 없음) 할 수 있는 말은 「무엇을 기다리면 되는가」뿐이다.
    e.isArrivedMemberCannotLeave -> "도착을 알린 뒤에는 나갈 수 없어요. 방장이 출발하면 방이 끝나요"
    e.isPartyClosed -> "이미 끝난 방이에요. 홈으로 돌아가 주세요"
    e.isPartyNotFound -> "이미 사라진 방이에요. 홈으로 돌아가 주세요"
    else -> LEAVE_FAILED_MESSAGE
}

/**
 * 21 매칭 대기 — SSE 변경분 구독(+끊김 폴백) · 나가기 · **약속 확정 → 출발지 도착 → 출발**
 * 진입점. partyId 없으면 오류 안내 후 되돌린다.
 *
 * 방 상태의 출처는 [MatchWaitingViewModel] 하나뿐이다 — 이 화면에서 SSE 를 두 번 수집하면 서버가 서로의
 * 연결을 끊어 무한 재연결이 되므로, 여기서 `LaunchedEffect` 로 구독을 추가하지 말 것. 아래
 * `LifecycleStartEffect` 는 **구독을 추가하지 않는다** — 그 하나의 수집을 켜고 끄는 신호만 보낸다
 * ([MatchWaitingViewModel.onScreenStart]·[MatchWaitingViewModel.onScreenStop]).
 *
 * **약속 흐름은 새 화면을 만들지 않는다** — 전부 이 화면 안에서 끝난다. 단계 판정은
 * [meetingStage] 한 곳이고, 그 판정이 보는 시각은 ViewModel 의 시계
 * ([MatchWaitingViewModel.nowMillis])다. 여기서 `Instant.now()` 를 부르지 말 것: Compose 재구성마다
 * 다른 값이 나오면 같은 프레임의 두 판정이 어긋날 수 있고, 테스트가 경계를 고정할 수 없다.
 */
@Composable
fun MatchWaitingRoute(
    repository: RideRepository,
    partyId: Long?,
    onCancelSearch: () -> Unit = {},
    /** 방을 유지한 채 화면만 벗어난다(나가기가 막힌 단계의 뒤로가기) */
    onExitKeepingParty: () -> Unit = {},
    onCardClick: () -> Unit = {},
    /**
     * 방이 닫혔다(FINISHED·CANCELED) — 방장이 「출발」을 눌렀거나, 모집이 만료됐거나, 도착자 없는
     * 방을 서버가 자동 정리했다. 호출자는 진행 중 기억을 지우고 홈으로 보낸다.
     */
    onPartyClosed: () -> Unit = {},
    /** 이 방의 채팅방을 여는 길. 채팅방이 아직 없으면 null — 화면이 버튼을 그리지 않는다 */
    onOpenChat: (() -> Unit)? = null,
) {
    // 방 id 없이 닿을 길은 없다(16 생성 · 20 합류 · 홈 배너가 모두 id 를 넘긴다). 그래도 null 이면
    // 예시 방을 그리는 대신 사실대로 말하고 되돌린다 — 20·22·23 과 같은 방식이다.
    //
    // 예전에는 여기서 [MatchWaitingScreen] 을 `ride` 없이 불러 **예시 방**을 그렸다. 구성 변경에 방
    // id 가 날아가던 동안(이제 saveable 이다 — MainNavGraph) 진짜 방에 들어 있던 사용자에게 남의
    // 가짜 방이 떴다. 그 길을 아예 막기 위해 화면 쪽 `ride` 기본값도 함께 걷어냈다.
    //
    // 되돌아갈 곳은 [onExitKeepingParty] — **방은 그대로 두고** 홈으로만 간다. 내가 어느 방에 있는지
    // 모르는 상태에서 나가기·기억 지우기([onCancelSearch]·[onPartyClosed])를 걸면 아직 들어 있는
    // 방을 끊어 버릴 수 있고, 홈 배너가 그 방으로 다시 들어갈 길이다.
    if (partyId == null) {
        BackStateScaffold("매칭 대기", onExitKeepingParty) {
            ErrorBox(
                message = "어느 방을 기다리는지 알 수 없어요. 홈에서 진행 중인 방으로 다시 들어가 주세요",
                onRetry = onExitKeepingParty,
                actionLabel = "홈으로 가기",
            )
        }
        return
    }

    val viewModel: MatchWaitingViewModel = viewModel(
        key = "waiting-$partyId",
        factory = MatchWaitingViewModel.factory(repository, partyId),
    )
    val state by viewModel.uiState.collectAsState()
    val action by viewModel.actionState.collectAsState()
    val closed by viewModel.partyClosed.collectAsState()
    val membershipLost by viewModel.membershipLost.collectAsState()
    val nowMillis by viewModel.nowMillis.collectAsState()

    // 지도의 파란 점 **그리고 「출발지 도착했어요」가 보내는 좌표**.
    //
    // 권한을 자동 요청하지 않는 것은 그대로다 — 대기 화면의 본질은 지도가 아니라 「기다림」이라,
    // 진입하자마자 다이얼로그를 띄우면 흐름을 끊는다. 이미 허용돼 있으면(14·17 에서 받았을 것) 점이 뜬다.
    //
    // 도착 단계가 붙은 뒤에도 자동 요청으로 바꾸지 않았다. 바꾸면 **약속과 무관하게 모든 진입에서**
    // 다이얼로그가 뜨고(모집 중에도 뜬다) 정작 필요한 순간은 그보다 한참 뒤다. 대신 그 순간에
    // 거부돼 있으면 비활성 버튼을 두지 않고 **왜 필요한지 + 허용 버튼**을 그 자리에 넣는다
    // (17 탐색의 `LocationPermissionNotice` 와 같은 처방 — [arriveNeedsPermissionNotice]).
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
    // 「출발」이 성공했다 — 서버가 방을 **모두에게** 닫고 구독을 끊었다. 진행 중 기억을 지우고 홈으로
    // 보낸다. SSE `closed` 도 곧 오지만 서버가 연결을 끊는 중이라 도착하지 않을 수 있어, 액션 성공
    // 자체를 전이 근거로 둔다. (`action.finished` 로 같은 길을 타던 「합승 완료」는 지웠다.)
    LaunchedEffect(action.departed) {
        if (action.departed) onPartyClosed()
    }
    // 서버가 방을 닫았다(SSE closed) — 방장의 「출발」·모집 만료·마지막 멤버 이탈·자동 정리.
    // 예전에는 폴링이 FINISHED/CANCELED 를 읽어 와야 알았고, 지금은 신호 하나로 바로 떠난다.
    LaunchedEffect(closed) {
        if (closed) onPartyClosed()
    }
    // **나는 이 방 멤버가 아니다**(SSE 403 · 상세에서 내가 사라짐). 「방이 닫혔다」와 다른 사실이지만
    // 할 일은 같아 같은 길로 보낸다 — 진행 중 기억을 지우고 홈으로. 그대로 머무르면 내가 들어 있지
    // 않은 방을 6초마다 폴링하며 「같이 탈 사람을 찾고 있어요」를 띄우고, 「그만 찾기」도 403 이라
    // 벗어날 길이 없다([MatchWaitingViewModel.membershipLost]).
    //
    // 기억을 지우는 쪽([onPartyClosed])을 고른 이유: 그 방은 내 방이 아니므로 홈 배너가 다시
    // 그리로 데려가면 같은 자리로 되돌아온다. 방을 유지하는 [onExitKeepingParty] 는 "내가 어느 방에
    // 있는지 모를 때"의 길이고, 여기서는 **모르는 게 아니라 아니라는 걸 안다.**
    LaunchedEffect(membershipLost) {
        if (membershipLost) onPartyClosed()
    }

    // **이 화면이 넘어가는 곳은 이제 홈뿐이다.** 예전에는 정원이 차면 서버가 기사 매칭을 시작해
    // `DISPATCHING`·`ONGOING` 으로 전이했고, 그 두 status 가 25 배차 현황으로 보내는 신호였다
    // (`onMatchingStarted`). 서버가 그 status 를 보낼 수 없게 되면서 **보낼 화면도, 신호도 사라졌다** —
    // 정원이 차도 방은 이 화면에 머물고, 사용자는 여기서 채팅으로 만날 곳을 정하고 약속을 확정한 뒤
    // 출발지에서 「출발」로 닫는다.
    //
    // 남은 전이는 「방이 닫혔다」 하나다. SSE `closed`([MatchWaitingViewModel.partyClosed]) 가 보통 먼저
    // 알려 주지만 그 스트림은 at-most-once 라, 끊김 폴백이 읽어 온 status 로도 같은 결론에 닿게 둔다.
    val status = (state as? MatchWaitingViewModel.UiState.Success)?.ride?.status
    LaunchedEffect(status) {
        when (status) {
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
                // 약속 단계 판정의 시각. ViewModel 의 10초 틱이 흘려 주는 값을 **그대로** 쓴다 —
                // 여기서 Instant.now() 를 부르면 재구성마다 값이 달라져 같은 프레임의 판정들이 어긋난다.
                now = Instant.ofEpochMilli(nowMillis),
                foundCount = ride.members.size,
                conditionLabel = "${ride.capacity}인",
                myLocation = myLocation.coordinates,
                locationGranted = myLocation.isGranted,
                onRequestLocationPermission = myLocation.onRequestPermission,
                actionInProgress = action.inProgress,
                actionErrorMessage = action.errorMessage,
                onCancelSearch = viewModel::leaveParty, // 나가기 성공 시 14 홈
                // 화면이 **보여 준 그 시각**을 그대로 넘긴다 — 동의한 값과 보내는 값이 같아야 한다.
                // 만남 장소(좌표·이름)는 화면이 고르지 않는다 — ViewModel 이 방의 지금 출발지를
                // 그대로 싣는다([MatchWaitingViewModel.confirmMeeting]).
                onConfirmMeeting = viewModel::confirmMeeting,
                // 좌표를 묶어서 받는다 — lat/lng 두 Double 로 받으면 전치가 컴파일을 통과한다
                onArrive = { coordinates ->
                    viewModel.arriveAtDeparture(
                        latitude = coordinates.latitude,
                        longitude = coordinates.longitude,
                    )
                },
                onDepart = viewModel::departParty, // 출발 성공 시 14 홈 (방이 모두에게 닫힌다 — 유일한 종료 경로)
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
