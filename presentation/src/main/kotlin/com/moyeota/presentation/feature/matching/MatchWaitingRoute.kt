package com.moyeota.presentation.feature.matching

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * **끊긴 동안에만** 도는 폴백 폴링 간격.
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
     * 스트림 종료에서 false. **폴백 폴링의 유일한 스위치다**([shouldPollFallback]).
     */
    @Volatile
    private var realtimeAlive = false

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
     * ⚠️ 수집 지점은 이 화면에서 **여기 하나뿐이어야 한다.** 서버는 같은 사용자의 기존 구독을 끊고 새 연결을
     * 받으므로(`PartySseRegistry.closeMember`), 같은 방을 두 곳에서 수집하면 두 연결이 서로를 끊고 3초마다
     * 번갈아 재연결하는 무한 루프가 된다. 그래서 `init` 에서 한 번만 걸고 ViewModel(키 `waiting-{partyId}`)이
     * 사는 동안 그 수집을 유지한다 — 재구성·`LaunchedEffect` 로 다시 걸지 않는다.
     *
     * 받는 순서는 늘 [PartyEvent.Snapshot] → 변경분이고 **재연결 때도 스냅샷이 먼저** 온다. 그래서
     * 끊긴 사이 놓친 변화를 메우는 catch-up 조회가 없다 — 스냅샷이 방을 통째로 덮는다.
     *
     * 수집이 끝나는 건 종결뿐이다(방 닫힘·403 멤버 아님·404 방 없음). 그때는 실시간 근거가 사라지므로
     * 폴백을 켠다 — 내가 방을 나간 경우라면 Route 가 이미 화면을 떠났고 수집도 함께 취소된다.
     */
    private fun observeEvents() {
        viewModelScope.launch {
            repository.observePartyEvents(partyId).collect { event -> applyEvent(event) }
            realtimeAlive = false
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
     * 끊긴 동안의 **폴백** 폴링. SSE 가 살아 있고 화면에 방이 떠 있으면 **한 번도 서버를 부르지 않는다** —
     * 이번 변경의 핵심이다(예전에는 신호가 붙어 있어도 20초마다 물었다). 판정은 [shouldPollFallback] 하나에 있다.
     *
     * 폴백 수단은 방 상세뿐이다 — 지문과 `GET /rooms/{id}/status` 가 서버에서 사라져(Backend #221)
     * 「바뀌었나」를 싸게 묻는 길이 없다. 대신 상세를 **통째로 교체**하는 건 스냅샷과 같은 처리라 멱등하고,
     * 내용이 같으면 [MutableStateFlow] 가 같은 값으로 보아 흘리지 않으므로 재구성도 일어나지 않는다.
     *
     * 방이 대기 단계를 벗어났거나([isWaitingForMembers]) 닫혔으면 더 볼 게 없어 루프를 끝낸다 —
     * 화면 이동은 Route 의 `LaunchedEffect` 가 맡는다. 조회 실패는 조용히 다음 주기를 기다린다.
     */
    private fun observeFallbackPolling() {
        viewModelScope.launch {
            while (isActive) {
                delay(PARTY_FALLBACK_POLL_INTERVAL_MS)
                if (_partyClosed.value) return@launch
                if (!shouldPollFallback(realtimeAlive, hasRide = _uiState.value is UiState.Success)) continue
                if (!readDetail()) continue
                val status = (_uiState.value as? UiState.Success)?.ride?.status ?: continue
                if (!isWaitingForMembers(status)) return@launch
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
 * 연결을 끊어 무한 재연결이 되므로, 여기서 `LaunchedEffect` 로 구독을 추가하지 말 것.
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
                radiusLabel = radiusLabel(ride),
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

/**
 * 21 「탐색 반경」 라벨. 방 생성 시 고른 값을 그대로 보여 준다 — 예전에는 화면 기본값 "1km" 가
 * 서버 값과 무관하게 늘 떠 있었다(QA D-3).
 *
 * 출발지·도착지 반경은 16 에서 따로 고를 수 있어 다를 수 있다. 같으면 한 값으로, 다르면 둘 다 적는다.
 * 목록 응답으로 만든 [Ride] 에는 반경이 없어 null 이며 그때는 "—" 다(값을 지어내지 않는다).
 */
private fun radiusLabel(ride: Ride): String {
    val departure = ride.departureRadiusMeters
    val destination = ride.destinationRadiusMeters
    return when {
        departure == null && destination == null -> "—"
        departure == destination -> "${departure}m"
        else -> "출발 ${meters(departure)} · 도착 ${meters(destination)}"
    }
}

private fun meters(value: Int?): String = if (value == null) "—" else "${value}m"
