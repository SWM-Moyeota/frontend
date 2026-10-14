# 124 · compose-builder — 21 매칭 대기의 SSE 구독·폴백 폴링을 화면 가시성에 묶음 (QA 123 D-3)

범위: `presentation/.../feature/matching/MatchWaitingRoute.kt` 와 그 테스트만. 브랜치 `feat/backend-sync-sse`
워킹트리에만 남겼다(커밋·브랜치 변경 없음). **실기 검증은 하지 않았다**(QA 담당).
25·26(`DispatchStatusRoute`·`RideOngoingRoute`)은 **읽기만** 했고 수정하지 않았다 — 의견은 §6.

입력: `123_qa-verifier_report.md` §5 D-3(실측 근거), `120_compose-builder_explore-poll-gate.md`(같은 처방의
선례), `122_api-integrator_entry-dedup.md` §6(`ActiveParty.kt` 의 `onHostStart`/`onHostStop`),
`117_compose-builder_waiting-sse.md`(21 의 현재 설계), `.claude/skills/compose-feature/SKILL.md`.

---

## 1. 변경 파일

| 파일 | 내용 |
|---|---|
| `presentation/src/main/kotlin/com/moyeota/presentation/feature/matching/MatchWaitingRoute.kt` | 가시성 게이트. `visible` StateFlow(기본 **true**) + `onScreenStart()`/`onScreenStop()`, **SSE 수집과 폴백 루프를 둘 다** 가시성에 묶음, 순수 함수 `shouldObservePartyEvents()`·`fallbackPollStep()` + `FallbackStep` enum 신설, Route 에 `LifecycleStartEffect` 배선 |
| `presentation/src/test/kotlin/com/moyeota/presentation/feature/matching/WaitingVisibilityGateTest.kt` | **신규 14건** — 가시성 게이트·복귀 재개·기존 종료 조건 보존·두 루프가 함께 멈추는지 |

화면 디자인·문구·레이아웃은 건드리지 않았다 — `MatchWaitingScreen.kt` 는 **무수정**이고, Route 가 화면에
넘기는 값(`foundCount`·`conditionLabel`·`radiusLabel`·`taxiEnabled`·액션 콜백)도 그대로다.
외부 라이브러리 추가 없음(`lifecycle-runtime-compose` 는 `presentation/build.gradle.kts:34` 에 이미 있고
`ExploreRoute`·`ChatRoute`·`MainNavGraph`·`MyLocationState` 가 이미 쓰고 있다).
`shouldPollFallback`·`isWaitingForMembers`·`hasMemberDrift`·`withMemberJoined`/`withMemberLeft` 는
**시그니처·동작 모두 무변경**(기존 `WaitingSseStateTest` 20건이 그대로 통과한다).

---

## 2. 게이트 구조 — 「보일 때만 돈다」를 어디에 적었나

### 2-1. 신호: `LifecycleStartEffect`(Route) → `onScreenStart()`/`onScreenStop()`(ViewModel)

```kotlin
LifecycleStartEffect(viewModel) {
    viewModel.onScreenStart()
    onStopOrDispose { viewModel.onScreenStop() }
}
```

선례와 **같은 모양·같은 자리**다(`ExploreRoute.kt`, `ChatRoute.kt`, `MainNavGraph.kt:172`).
`MatchWaitingViewModel` 은 `MATCH_WAITING` 의 `NavBackStackEntry` 에 `key = "waiting-$partyId"` 로 매여
있어 **위에 화면이 올라왔을 뿐 pop 되지 않은 동안** 계속 살아 있고, `viewModelScope` 는 화면이 보이는지
보지 않는다. 그래서 신호를 Route 에서 내려 준다.

**RESUMED 가 아니라 STARTED**(지시대로, 근거는 같은 코드베이스의 선례와 일치):

1. NavHost 는 **전환 애니메이션이 끝난 뒤에야** 엔트리를 RESUMED 로 올린다 → RESUMED 로 묶으면 복귀
   재구독이 그만큼 늦는다(이번 요구사항 #2 와 정면으로 어긋난다).
2. 위에 올라온 화면에 가린 엔트리는 **CREATED 까지 내려간다** → 「22 상세·24 채팅방이 올라옴」이 STARTED
   로 똑같이 잡힌다.
3. **앱 백그라운드(ON_STOP)** 도 호스트 lifecycle 이 엔트리 상한을 내리므로 함께 잡힌다 — D-3 의 증상이 이것이다.
4. 17 탐색·24 채팅방·GPS 구독(`MyLocationState.kt`)·채팅방 감시 폴링(`MainNavGraph.kt:172`)이 **전부 STARTED 경계**다.

RESUMED 와 달라지는 경우는 위에 `dialog` 목적지가 뜰 때뿐인데, 21 위에 뜨는 것은 `RIDE_DETAIL`·
`chatRoom` 둘 다 `composable` 이라 해당이 없다.

### 2-2. 판정: 순수 함수 2개 (선례의 `nearbyPollStep` 과 같은 자리)

```kotlin
internal fun shouldObservePartyEvents(visible: Boolean, streamEnded: Boolean): Boolean =
    visible && !streamEnded

internal fun fallbackPollStep(visible, partyClosed, fallbackDone, streamEnded): FallbackStep = when {
    !visible                     -> STOP            // 이 함수의 존재 이유
    partyClosed || fallbackDone  -> STOP            // 기존 종료 조건 — 복귀로 되살아나지 않는다
    streamEnded                  -> READ_NOW        // 다시 붙을 스트림이 없다
    else                         -> WAIT_THEN_READ  // 평상시
}
```

`visible` 이 **맨 앞**인 게 요점이다(`shouldPollChatRoom` 과 같은 배치) — 보이지 않는 동안에는 나머지
조건을 보지 않는다.

**판정이 두 층인 이유**(섞지 않았다): 보는 값의 수명이 다르다.

| 함수 | 묻는 것 | 바뀌는 단위 |
|---|---|---|
| `fallbackPollStep` | 루프를 **돌릴지 / 첫 조회를 미룰지** | 패스(가시성·방 닫힘·루프 종료) |
| `shouldPollFallback` *(기존, 무변경)* | 이번 주기에 **읽을지** | 주기(SSE 생사·화면에 방이 있는지) |

### 2-3. 루프: SSE 와 폴백을 **둘 다** 가시성에 묶었다

```kotlin
// SSE 수집 — 입력이 가시성 하나라 combine 이 필요 없다
visible.collectLatest { shown ->
    if (!shouldObservePartyEvents(shown, streamEnded)) return@collectLatest
    try {
        repository.observePartyEvents(partyId).collect { applyEvent(it) }
        streamEnded = true          // 종결로 끝났다 — 취소는 이 줄을 지나지 않는다
    } finally {
        realtimeAlive = false       // 종결이든 취소든 실시간 근거가 사라진다
    }
}

// 폴백 루프 — 방 닫힘도 즉시 반영되게 같은 흐름에 묶었다
combine(visible, _partyClosed, ::Pair).collectLatest { (shown, closed) ->
    val step = fallbackPollStep(shown, closed, fallbackDone, streamEnded)
    if (step == STOP) return@collectLatest
    if (step == WAIT_THEN_READ) delay(PARTY_FALLBACK_POLL_INTERVAL_MS)
    while (isActive) { …기존 본문… ; delay(PARTY_FALLBACK_POLL_INTERVAL_MS) }
}
```

17 탐색이 쓴 구조 그대로다 — `collectLatest` 가 **조건이 깨지면 진행 중인 수집·조회와 `delay` 를 함께
죽이고**, 다시 보이면 **패스를 새로 시작**한다. 가려진 동안에는 깨어나 아무 일도 안 하는 주기조차 없다.

### 2-4. 왜 안전한가 ★ (KDoc 3곳에 적었다: `visible`, `observeEvents`, `shouldObservePartyEvents`)

**재연결할 때마다 서버가 `connected` 로 방 상세 전체(Snapshot)를 다시 준다.** 그래서 백그라운드에서
끊고 있다가 복귀해 붙으면 **그 순간 상태가 통째로 맞춰진다** — 끊긴 동안의 변화를 따라잡는 별도 조회가
필요 없다. 이게 이번 SSE 설계의 핵심이고(`117` §2 의 근거 1번), 이 게이트가 성립하는 근거다.
증분(`member-joined`/`member-left`)은 바탕이 있을 때만 쓰는 값이라 「놓친 증분」을 복원할 필요도 없다 —
바탕 자체가 새로 온다.

그리고 **백그라운드에서 SSE 를 유지해도 사용자가 얻는 것이 없다**: 알림은 FCM 이 띄우는데 멤버 합류에는
FCM 이 없고(실제 처리 대상은 `DRIVER_ARRIVED`·`CHAT_MESSAGE` 둘뿐 —
`app/.../MoyeotaFirebaseMessagingService.kt:56`), 화면도 보이지 않는다. 반대로 비용은 확실하다 —
OS 가 네트워크를 조여 SSE 가 죽고 → 3초 재연결 → 그 틈을 6초 폴백이 메우고 → 다시 죽는다(D-3 실측).

### 2-5. 기본값이 `true` 인 이유

배선(`LifecycleStartEffect`)이 빠진 채 머지되면 `false` 기본값은 **방 상태가 영영 갱신되지 않는** 기능
손실이 된다(인원이 늘지 않고, 방이 닫혀도 홈으로 못 간다). `true` 면 최악이 「고쳐지지 않은 상태 유지」다
— `ActiveParty.kt` 의 `hostVisible` 가 같은 이유로 `true` 인 것과 똑같은 판단이다.
(이번에는 배선을 같은 커밋 범위에서 함께 넣었으므로 그 최악은 일어나지 않는다.)

---

## 3. 복귀 즉시성 — 4초·6초를 기다리지 않는 근거

| 무엇이 | 언제 |
|---|---|
| **SSE 재구독** | **복귀 즉시.** `collectLatest` 가 같은 패스를 곧바로 다시 시작하고, 그 첫 줄이 `observePartyEvents(partyId)` 수집이다 — 앞에 `delay` 가 없다. 데이터 계층의 `RECONNECT_DELAY_MS`(3초)는 **실패 후 재시도** 경로이고 새 수집에는 걸리지 않는다 |
| **상태 반영** | 재구독의 `connected` 프레임 = 방 상세 전체. 즉 **재구독이 곧 스냅샷**이라 별도 조회가 없다 |
| 폴백 첫 조회(평상시) | 복귀 후 한 주기(6초) 뒤. **일부러 미뤘다** — 복귀 갱신은 스냅샷이 맡으므로 여기서 또 읽으면 복귀마다 상세 1건이 덧붙는다(`122` §6 이 「즉시 1회를 붙이지 않은 이유」로 적은 것과 같은 판단) |
| 폴백 첫 조회(`streamEnded`) | **즉시**(`READ_NOW`). 종결로 끝난 스트림·스트림 없는 더미에서는 다시 붙을 것이 없어 폴백이 유일한 갱신 수단이다 — 6초 미룰 이유가 없다 |

17 탐색은 같은 구조에서 복귀 첫 조회가 **58ms** 였다(QA 123 A3). 21 은 그 자리에 조회 대신
**재구독**이 들어간다 — QA 가 잴 숫자는 §7 #4 다.

---

## 4. 단일 구독 보장 — 요구사항 #1

`MatchWaitingRoute.kt:208` 의 ⚠️ 경고(서버 `PartySseRegistry.subscribe` 가 같은 memberId 의 기존 연결을
먼저 끊으므로 두 곳에서 수집하면 무한 재연결)를 읽고, 세 겹으로 확인했다.

1. **수집 호출부는 레포 전체에 하나다.** `grep -rn "observePartyEvents"` → 호출 1건
   (`MatchWaitingRoute.kt:239`), 선언 2건(`RideRepository`·`RemoteRideRepository`), 나머지는 KDoc 참조.
   25·26·채팅·`ActiveParty` 는 구독하지 않는다.
2. **게이트가 수집을 둘로 갈라지지 않았다.** `init` 의 `launch` 는 여전히 **하나**고, 그 안에서
   `visible.collectLatest` 가 **같은 수집을 켜고 끈다.** Route 에 추가한 것은 `LifecycleStartEffect` 로
   `visible` 값을 바꾸는 신호뿐이다 — 구독을 추가하지 않는다(Route KDoc 에 그 문장을 명시해 두었다).
3. **빠른 토글에도 두 연결이 겹치지 않는다.** `collectLatest` 는 새 값을 받으면 앞 패스를
   **`cancelAndJoin`** 한다(`ChannelFlowTransformLatest`) — 앞 패스가 끝났다는 것은
   `PartyEventSource.connect` 의 `awaitClose { source.cancel() }` 까지 돌았다는 뜻이다. 그래서 어느
   순간에도 **살아 있는 수집은 0개 또는 1개**다. 무한 재연결은 *두 개의 살아 있는 수집기가 서로를 끊고
   각자 3초 뒤 다시 붙을 때*만 생기는데, 여기에는 수집기가 하나뿐이라 그 모양이 만들어지지 않는다.
4. **ViewModel 이 두 개 생길 길도 없다.** `MATCH_WAITING` 엔트리는 하나다 —
   `navigateToStage` 가 이미 스택에 있으면 `popBackStack(route, inclusive = false)` 로 되돌아가고,
   없으면 `launchSingleTop = true` 로 쌓는다(`MainNavGraph.kt:300~312`). 설령 두 엔트리가 생겨도
   **아래 엔트리는 CREATED 라 이제는 구독하지 않는다** — 게이트가 이 위험을 오히려 줄인다.

---

## 5. 동작은 그대로 — 깨뜨리지 않은 것 (요구사항 #3·#4)

| 동작 | 어떻게 지켰나 |
|---|---|
| `Closed` → 홈 전이 | `_partyClosed` → Route 의 `LaunchedEffect(closed)` → `onPartyClosed()`. **무변경** |
| 방이 닫히면 폴백 루프 종료 | `_partyClosed` 를 폴백 게이트의 `combine` 입력으로 넣었다 → 닫히는 **그 순간** 패스가 죽고, 복귀해도 `STOP` 이다(예전엔 다음 주기 시작에서 `return@launch`). 되살아날 길 없음 |
| 대기 단계를 벗어나면 폴백 종료 | `fallbackDone` 플래그로 **영구화**했다. 가시성은 스위치, 이 값은 「끝났다」는 사실이라 복귀로 되돌아가지 않는다 |
| 종결된 스트림에 다시 붙지 않음 | `streamEnded` — 취소(`CancellationException`)와 종결을 구분한다. 게이트 이전 동작과 동일 |
| 합류 교착 복구 | 20 `JoinConfirmRoute` 의 분기다. **손대지 않았다** |
| 멤버 증감·drift 보정·늦은 상세 버리기 | `applyEvent`/`applyDelta`/`readDetail` **무변경** |
| 평상시 상세 0회 | `shouldPollFallback` 무변경 + 게이트는 「돌릴지」만 본다. 테스트로 조합을 고정했다 |
| 25 로의 전이(`DISPATCHING`) | Route 의 `LaunchedEffect(status)` 무변경. 그 status 는 증분·스냅샷·폴백 셋 중 무엇으로 와도 같다 |
| 「그만 찾기」·「합승 완료」·뒤로가기·지도·채팅 버튼 | 전부 무변경 |

### 알아 둘 변화 1건 (회귀가 아니라 지연, 최대 6초)

**24 채팅방을 쌓아 열고 그 안에 머무는 동안 방이 닫히면**, 예전에는 21 의 SSE 가 `closed` 를 받아
`_partyClosed` 를 세워 두었다가 **복귀 즉시** 홈으로 보냈다. 지금은 채팅방에 있는 동안 21 이 구독하지
않으므로, 복귀해서 ① 재구독이 403/404 로 끝나고 ② 폴백이 상세를 읽어 `COMPLETED`/`CANCELED` 를 보면
Route 의 `LaunchedEffect(status)` 가 홈으로 보낸다 — **최대 6초 지연**(`READ_NOW` 경로를 타면 그보다 빠르다).
어느 쪽이든 **화면이 갇히는 일은 없고** 문구·전이 결과도 같다. 채팅방 안에서는 21 이 보이지 않으니
그 6초 동안 사용자가 보는 화면도 달라지지 않는다.

---

## 6. 25·26 에 같은 게이트가 필요한가 — **의견**(코드 무수정)

**결론: 21 보다 오히려 더 필요하다. 단 별 작업으로, 「복귀 즉시 1회 조회」를 함께 붙여야 한다.**
우선순위는 중간(택시 모드 전용이라 지금 동승 배포에서는 전이가 일어나지 않는다. 다만 QA 123 D-7 이
적었듯 **돌고 있는 8080 은 `taxiEnabled: true`** 라 검증 환경에서는 닿는다).

근거를 넷으로 적는다.

1. **지시가 든 「화면을 켜둔 채 주머니에 넣는다」는 STARTED 게이트와 충돌하지 않는다.**
   화면이 켜져 있고 앱이 앞에 있으면 엔트리는 STARTED 이상이라 **게이트가 아무것도 하지 않는다.**
   게이트가 발동하는 것은 ① 다른 앱으로 전환 ② 홈 버튼 ③ **화면 꺼짐/잠금**뿐이다. 즉 「주머니 속
   켜둔 화면」은 지금처럼 계속 폴링한다 — 지키려는 상황이 그대로 지켜진다.
2. **누수 양은 21 보다 크다.** 25 `pollDriver()` 는 7초마다 **방 상세 + 기사 위치 2건** ≈ 분당 17건,
   26 `observeRideFinish()` 는 8초마다 상세 1건 ≈ 분당 7.5건이다. D-3 으로 고친 21 이 분당 약 7건이었다.
   둘 다 컴포지션 스코프라 화면 전환에는 멈추지만(`LaunchedEffect(viewModel)`), **앱 백그라운드·화면
   잠금에서는 계속 돈다**(`120` §5 가 적어 둔 그대로다).
3. **가장 급한 사건에는 FCM 이 있다.** 기사 도착은 `DRIVER_ARRIVED` 푸시로 뜬다
   (`MoyeotaFirebaseMessagingService.kt:57`). 나머지 전이(배차 완료 → 25c 다이얼로그, 탑승 시작 → 26,
   운행 종료 → 33 평가)는 **보고 있을 때만 의미가 있는** 전이다 — 잠긴 화면 뒤에서 미리 받아 둬도
   보여줄 데가 없고, 복귀 시 1회 조회로 똑같이 알 수 있다.
4. **안전성 근거는 21 보다 더 단순하다.** 25·26 은 SSE 가 없고 **매 주기 방 상세 전체**를 읽으므로,
   복귀 후 첫 조회 하나로 상태가 통째로 맞춰진다(21 의 스냅샷과 같은 성질, 증분이 없어 더 단순하다).

**단, 그대로 베끼면 안 되는 점 하나**: 25·26 에는 재구독 스냅샷이 없어 **폴링이 주 수단**이다. 그래서
21 처럼 「첫 주기를 기다린다」로 두면 복귀 후 최대 7~8초 동안 옛 상태가 떠 있고 25c·26→33 전이가
그만큼 늦는다. 17 탐색이 쓴 `QUERY_NOW`(복귀 즉시 1회 → 그 뒤 주기 재개) 모양을 써야 한다.
배선은 21 과 같은 모양이면 된다 — ViewModel 에 `visible` + `onScreenStart/Stop` 을 두고
`LaunchedEffect(viewModel) { pollDriver() }` 는 그대로 둔 채 루프를 `combine(visible, …) + collectLatest`
로 감싸고, Route 에 `LifecycleStartEffect` 를 추가. 「들고 있던 값을 유지한 채 다음 주기를 기다린다」·
`shouldPublishDetail` 절약은 그대로 살아 있다.

---

## 7. QA 가 셀 숫자

진입: `14 홈 → 17 합승 탐색 → (방 선택) 20 합류 확인 → 「이 탑승에 합류하기」 → 21 매칭 대기`
(또는 `14 홈 → 15 목적지 → 16 조건 → 방 만들기 → 21`).
세는 명령 예(21 진입 직후 `adb logcat -c` 로 비운 뒤):

```
adb logcat -d | grep -c "matching/rooms/[0-9]*/events"   # SSE 구독 요청
adb logcat -d | grep -cE "GET .*matching/rooms/[0-9]+ "  # 방 상세 (events 제외)
adb logcat -d | grep "PartyEvents"                       # 연결됨 / 연결 끊김 로그
```

| # | 재현 | 기대 숫자 | 수정 전(QA 123 D-3) |
|---|---|---|---|
| **1** | 21 진입 → **홈 버튼 → 75초 방치** | 백그라운드 구간 **`/events` 0건 · 상세 0건** ★ 핵심 | `events` 5 + 상세 4 = **9건** |
| 2 | 같은 경로로 **45초** | **0건 / 0건** | 3 + 3 = 6건 |
| **3** | 위 1·2 에서 **복귀** | **`/events` 1건**(재구독) · 상세 **0건**. 복귀 직후 6초 안에 상세가 1건 나가면 SSE 재연결이 안 된 것이니 `PartyEvents` 로그를 함께 볼 것 | — |
| **4** | 그 복귀의 **지연** | 앱 전환/언락 시각 → `/events` 요청 시각 = **200ms 이내**(17 탐색은 58ms). 4초·6초를 기다리면 실패 | — |
| 5 | 21 에서 **카드 탭 → 22 상세** 체류 30초 → 뒤로 | 22 체류 중 `/events` 0건·상세 0건, 복귀 시 `/events` 1건 | 22 체류 중에도 구독 유지 |
| 6 | 21 에서 **「채팅 열기」 → 24 채팅방** 체류 30초 → 뒤로 | 같음 | 같음 |
| 7 | **21 에 머무는 동안**(평상시, 2분) | `/events` **1건**(진입 시만) · 상세 **0건** · 인원 변화는 즉시 반영 | 같음(이 숫자는 유지돼야 한다 — 게이트가 과하게 잠그지 않았는지의 양성 대조) |
| **8** | **무한 재연결 회귀 확인** — 21 ↔ 22 를 5회 왕복, 앱 백그라운드/복귀 5회 | `PartyEvents` 로그에 「연결됨」이 **복귀 1회당 1줄**. 3초마다 「연결됨 → 끊김」이 번갈아 반복되면 **수집이 둘로 갈라진 것**(요구사항 #1 위반) | — |
| 9 | 두 계정 입·퇴장(SSE 증분) | 21 을 보고 있는 동안 인원·멤버 목록이 **즉시** 갱신(상세 재조회 0건). `117` §7 #1 과 같은 숫자 | 같음 |
| 10 | **방 닫힘 전이** — 다른 계정이 「합승 완료」 | 21 을 **보고 있으면** 즉시 홈. 21 이 **가려진 동안** 닫혔다면 복귀 후 **최대 6초 안에** 홈(§5 의 알아 둘 변화) | 보고 있으면 즉시 / 가려졌다 복귀하면 즉시 |
| 11 | 폴백이 살아 있는지 — 21 에서 **비행기 모드 ON** | 약 45초 뒤 `Disconnected`, 그 뒤 **6초마다** 상세 시도(실패 로그). 화면은 마지막 상태 유지 | 같음 |
| 12 | 그 상태로 **홈 버튼 → 60초** | **0건**(끊긴 폴백도 가려지면 멈춘다) | 폴백이 계속 돌았다 |
| 13 | 크래시·ANR | `FATAL EXCEPTION` 0 · `ANR in com.moyeota` 0 | — |

곁다리: 17 탐색의 숫자(A1~A4)와 `chat-rooms/me` 게이트(A5)는 이번 변경으로 달라지지 않는다 —
`ExploreRoute.kt`·`ActiveParty.kt`·`MainNavGraph.kt` 를 **열지 않았다**.

---

## 8. 테스트 결과 — 통과

```
./gradlew :app:assembleDebug :data:testDebugUnitTest :presentation:testDebugUnitTest --console=plain -q
→ BUILD SUCCESSFUL (exit 0)
   :presentation 228건 · :data 278건 = 506건 · 실패 0 · 에러 0 · 건너뜀 0
   (신규 WaitingVisibilityGateTest 14건 포함. 직전 123 기준 presentation 214 → 228)
   app/build/outputs/apk/debug/app-debug.apk 생성 확인
```

신규 14건(`WaitingVisibilityGateTest`) — 기존 `WaitingSseStateTest` 관례(한국어 백틱 이름, 순수 함수만,
`junit` 단독)를 따랐다.

- **구독 게이트 4건**: 안 보이면 `streamEnded` 양쪽 조합에서 전부 구독 안 함(D-3 회귀 방지) ·
  보이고 살아 있으면 구독 · **가려졌다 돌아오면 다시 붙는다** · 종결로 끝난 스트림은 복귀해도 안 붙는다
- **폴백 게이트 5건**: 안 보이면 **나머지 3조건의 8조합 전부 `STOP`**(D-3 회귀 방지) ·
  평상시 복귀는 한 주기 대기 · 재구독할 스트림이 없으면 `READ_NOW` ·
  **방 닫힘·대기 단계 이탈은 복귀해도 되살아나지 않는다**(기존 종료 조건 보존) ·
  종결 신호가 끝난 루프를 되살리지 않는다(판정 **순서** 고정)
- **두 루프가 함께 묶였는지 5건**: 가려지면 SSE·폴백이 **함께** 멈춘다(한쪽만 묶으면 남은 쪽이 틈을
  메운다 — 이번 결함이 정확히 그 모양이었다) · 복귀하면 함께 되살아난다 ·
  **보이는 동안에도 SSE 가 살아 있으면 주기 조회 0회**(「평상시 0회」가 안 깨졌는지 두 판정 층을 이어 확인) ·
  끊긴 동안에는 보이는 화면에서만 돈다

한계(선례와 같다): 고정한 것은 **판정**이고, 「가려지면 진행 중인 수집과 `delay` 가 실제로 죽는가」는
`collectLatest` 의 취소 전파에 달려 있다. `presentation` 의 테스트 의존성이 `junit` 뿐이라
(코루틴 테스트 라이브러리를 넣으면 빌드 파일 변경이므로 넣지 않았다) 그 배선은 단위 테스트로 확인할 수
없다 — **§7 #1 의 logcat 계수가 그 몫이다.** `visible` 기본값 `true` 도 ViewModel 을 세워야 보이는데
`init` 이 `viewModelScope` 를 쓰므로(메인 디스패처 필요) 같은 이유로 테스트하지 못했다.

---

## 9. 미완 · 넘길 것

1. **실기 검증 안 함**(지시대로). §7 이 QA 가 셀 목록이고, 핵심 숫자는 **백그라운드 75초 동안
   `/events` 0건 · 상세 0건**, 그리고 **복귀 재구독 200ms 이내**다.
2. **25·26 은 손대지 않았다.** §6 이 의견이다 — 요약하면 「같은 게이트가 필요하되, 복귀 즉시 1회 조회를
   함께 붙여야 한다. 『주머니 속 켜둔 화면』은 STARTED 게이트에 걸리지 않으므로 지시의 우려와 충돌하지
   않는다」. 소유자가 다른 범위이므로 **리더가 배분**해야 한다.
3. **채팅방 안에서 방이 닫히면 복귀 후 최대 6초 지연**(§5). 없애려면 복귀 시 상세 1회를 무조건 읽어야
   하는데, 그러면 복귀마다 상세 1건이 늘어 이번 작업의 목표와 상충한다 — 지연을 택했다.
4. 이론상 남는 구석: ViewModel 이 **호스트가 STOPPED 인 동안 새로 만들어지면** `visible` 기본값 `true`
   때문에 한 번 구독될 수 있다. 안드로이드 Recomposer 는 lifecycle 이 STARTED 아래면 재구성을 멈추므로
   실제로 그 경로는 생기지 않고, 생겨도 결과는 「고치기 전과 같음」이다(기본값을 `true` 로 둔 이유와 같은 맞교환).
