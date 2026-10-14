# 122 · api-integrator — 21 진입 중복 조회(D-2) · 없는 방 번호 조회(D-3) · 채팅방 폴링 가시성 게이트

기준: `_workspace/00_input_resume-qa-fixes.md` A·D-2/D-3, `_workspace/119_qa-verifier_report.md` §6 D-2/D-3.
브랜치 `feat/backend-sync-sse` 워킹트리에만 남김(커밋·브랜치 변경 없음). **실기 검증 없음**(QA 담당).

## 1. 변경 파일

| 파일 | 내용 |
|------|------|
| `presentation/src/main/kotlin/com/moyeota/presentation/core/ActiveParty.kt` | D-2. `ResolveGate`(겹침 + 「방금 끝난 조회」 창) 신설, `refreshing` 플래그 대체, `rememberParty()` 재조회 조건화 · **추가 과제**. `pollChatRoom()` 가시성 게이트(`hostVisible` + `onHostStart/onHostStop` + `shouldPollChatRoom`), `lookupChatRoom(Long)` 로 시그니처 정리 |
| `data/src/main/kotlin/com/moyeota/data/repository/RemoteActivePartyRepository.kt` | D-3. `verify()`·`remember()`·`findFromChatRooms()` 에서 0 이하·비숫자 partyId 를 **조회 전에** 차단 |
| `presentation/src/test/kotlin/com/moyeota/presentation/core/ActivePartyEntryTest.kt` (신규) | 억제 방향·창 경계 9건 |
| `presentation/src/test/kotlin/com/moyeota/presentation/core/ChatRoomPollGateTest.kt` (신규) | 채팅방 폴링 가시성 판정 7건 |
| `data/src/test/kotlin/com/moyeota/data/repository/RemoteActivePartyRepositoryTest.kt` | D-3 4건 추가(총 15건) |

범위 외 파일은 읽기만 했고(호출부 확인) 수정하지 않았다: `MainNavGraph.kt`, `ChatRoute.kt`, `ChatMappers.kt`, `ChatDtos.kt`, 백엔드 `ChatRoomUserResult.java`.

## 2. D-2 — 어떤 방식을 골랐고 왜

지시의 두 선택지 중 **뒤쪽(짧은 중복 억제)** 을 골랐다. 앞쪽(기억만 저장하고 `reload()` 생략)은
코드를 읽어 보니 배너 공백 말고도 **기능 손실 두 건**을 만든다.

`ActivePartyViewModel._ride` 는 앱 전체가 공유하는 **유일한 `Ride` 객체**다. 21(`MatchWaitingRoute`)이
읽는 상세는 21 자신의 상태로만 쓰이고 이 홀더로 흘러들지 않으므로, `rememberParty` 의 조회를 지우면
홀더는 다음 `refresh()` 까지 **빈 채로** 남는다. 그 사이 깨지는 것:

1. **24 채팅 헤더의 「매칭 화면으로 →」 가 사라진다.** `MainNavGraph:668·696` 이 `activePartyId = activeRide?.id`
   로 넘기고 `ChatRoute.kt:902 isActiveParty()` 가 그 값과 채팅방의 partyId 를 비교한다. 합류 → 21 →
   「채팅 열기」 경로에서는 그동안 `refresh()` 가 한 번도 걸리지 않으므로 링크가 계속 없다.
2. **21 의 「채팅 열기」 자체가 안 뜬다.** `pollChatRoom()` 이 `val current = _ride.value ?: continue` 로
   게이트돼 있어 홀더가 비면 채팅방을 **아예 찾지 않는다**. 채팅방은 합류 시점에 생기므로(Backend #109)
   21 에서 떠야 하는 버튼인데, 21 에는 `refresh()` 호출부가 없다.
3. 홈 배너 — 아래 §3.

그래서 **지우는 대신 겹치는 것만 지웠다.**

- `ResolveGate` 가 「진행 중인 조회」 + 「`RESOLVE_DEDUP_WINDOW_MS`(500ms) 안에 끝난 조회」를 함께 본다.
  예전 `refreshing` 플래그는 겹침만 막아 **순서대로** 들어오는 중복(앱 재개 `LifecycleResumeEffect` →
  탭 진입 `LaunchedEffect`, 전이 애니메이션을 타고 들어오는 재구성)을 그대로 통과시켰다.
- 억제는 **비대칭**이다. `refresh()`(화면 복귀)는 창을 지키고, `rememberParty()`(합류·생성)는 창을
  무시한다(`enterNow()`) — 직전 조회는 **합류 이전**의 사실이라 재사용하면 방금 들어간 방을 놓친다.
  단 끝난 시각은 남겨 뒤따르는 `refresh()` 를 흡수한다.
- `rememberParty()` 는 **홀더가 이미 그 방을 들고 있으면 상세를 건너뛴다**(`needsDetailAfterRemember`).
  서버가 「이미 이 방 멤버」로 답해 합류 교착에서 복구된 경로가 여기로 온다. 채팅방 id 만 아직이면
  그것만 따로 찾는다(채팅방 목록 1회, 방 상세 0회).
- 0 이하·비숫자 id 는 심지도 읽지도 않는다(D-3 과 같은 기준, 양쪽에서 막음).
- 창 길이를 0.5초로 **짧게** 잡은 이유: QA 가 잰 중복은 25ms 간격이고 전이 재구성도 수백 ms 안이다.
  길게 잡으면 정원이 차는 순간 `MainNavGraph.onMatchingStarted` 가 거는 재조회까지 삼켜 단계·채팅방
  id 가 한 박자 늦는다.

### 남는 1건 — 솔직한 한계

**21 진입의 상세 2건 중 1건은 이 파일에서 지울 수 없다.** 두 건은 계층이 다른 두 소비자다.

| # | 주체 | 경로 |
|---|------|------|
| 1 | 21 화면 | `MatchWaitingRoute.refresh()` → `RideRepository.getPartyDetail` |
| 2 | 진행중 방 홀더 | `rememberParty()` → `resolve()` → `RemoteActivePartyRepository.verify()` → `getPartyDetail` |

`ActiveParty.kt` 안의 어떤 억제도 1번을 모르고, 1번의 결과는 홀더로 들어오지 않는다. 정말 1회로
줄이려면 **이 작업 범위 밖**의 두 길 중 하나가 필요하다(리더 판단 요청):

- (ㄱ) `RemoteRideRepository.getPartyDetail` 에 아주 짧은 TTL 캐시(두 소비자가 같은 왕복을 공유).
- (ㄴ) 21 의 SSE 첫 스냅샷을 홀더에 밀어 넣는 API(`ActivePartyViewModel.publishRide(ride)`) — 호출부가
  `MainNavGraph`/`MatchWaitingRoute` 라 compose-builder 작업이다.

줄어드는 것: **합류 교착 복구 경로**(QA 가 3건으로 잰 경로)에서 홀더가 이미 그 방을 들고 있으면 1건
감소, 그리고 앱 재개·탭 진입이 겹치거나 연달아 들어오는 구간의 중복.

## 3. 홈 배너 공백 가능성 — 논리 분석

배너를 그리는 곳은 두 군데뿐이고(`HomeRoute` 14 · `ExploreRoute` 17), 둘 다 자기 `composable` 안에
`LaunchedEffect(Unit) { activePartyViewModel.refresh() }` 를 갖고 있다(`MainNavGraph:472·528`). 앱 전체
재개는 루트 `LifecycleResumeEffect`(`:168`)가 따로 건다.

- Navigation Compose 는 앞으로 이동할 때 이전 목적지의 컴포지션을 **버린다**. 돌아오면 다시 컴포즈되고
  `LaunchedEffect(Unit)` 가 **다시 실행**된다 → 배너 호스트 진입마다 `refresh()` 는 반드시 한 번 걸린다.
- 따라서 「`reload()` 생략」을 골랐다면 배너는 **그 한 왕복 동안 비어 보인다**(21 → 뒤로 → 14/17).
  조회가 실패하면(`resolve()` 는 오프라인에서도 null) 다음 복귀까지 계속 빈다.
- **채택한 방식에서는 그 창이 없다.** `rememberParty` 가 홀더를 채워 두므로 복귀 즉시 배너가 그려지고,
  그 뒤 화면의 `refresh()` 는 500ms 창에 걸려 **추가 왕복 없이** 같은 값을 쓴다 → 배너 공백 0, 왕복도
  오히려 1회 감소(예전에는 `rememberParty` 조회 + 복귀 조회 2회).
- 방 생성 경로(20 → 21)와 합류 경로(19 → 21) 모두 같다. 생성 경로의 스택은 `HOME → DESTINATION →
  MATCH_WAITING`(확인 모달은 `popUpTo(inclusive)` 로 지워짐), 합류 경로는 `EXPLORE → MATCH_WAITING` 이라
  둘 다 배너 호스트로 돌아갈 때 위 규칙이 적용된다.
- `clearParty()`(나가기·FINISHED·3분 타임아웃)는 그대로 `_ride = null` 이라 **끝난 방이 배너에 남는 일은
  없다**. 창은 「조회를 다시 걸까」만 결정하고 홀더 값을 지키지 않는다.

## 4. D-3 — `partyId = 0` 은 어디서 오는가 (추측 아님, 코드 확인)

- 백엔드 `chat/member/dto/ChatRoomUserResult.java`(읽기전용 사본 `…/scratchpad/backend-dev`)의 record
  필드는 `chatRoomId, departure, destination, status, lastReadMessageId, unreadCount,
  notificationMuted, joinedAt, lastMessage, members` — **`partyId` 가 없다.**
- 그래서 앱 DTO `ChatRoomUserResponse`(`data/remote/dto/ChatDtos.kt`)에도 그 필드가 없고,
  매퍼 `ChatMappers.kt:196 toChatRoomOrNull()` 이 `partyId = 0L` 로 **의도적으로** 채운다(그 자리 주석도
  "0 으로 채우지 않고 상세에서 받은 값만 신뢰해야 한다"고 적혀 있다).
- `RemoteChatRepository.toMyChatRoom()` 은 목록에 방 정보(`departure`/`destination`/`status`)가 실린
  서버(2026-09-14 이후)에서는 `GET /chat-rooms/{id}` 를 **생략**한다 → 목록 항목의 partyId 는 **전부 0**.
- `RemoteActivePartyRepository.findFromChatRooms()` 가 그 0 을 그대로 `verify("0")` → `GET
  /api/v1/matching/rooms/0` → **404**. 상세 응답 DTO(`ChatRoomResponse.partyId: Long = 0`)의 기본값이
  아니라, **목록 매퍼가 채운 자리값**이 원인이다.
- 「합승 완료」·방 종료 직후에 보이는 이유: 그 시점에 기억이 지워져(`clearParty`) 폴백이 돌고, 후보
  채팅방 수만큼(최대 5, QA 관측 1~2) 404 가 나간다.

**수정**
- `verify()`: `partyId.toLongOrNull()?.takeIf { it > 0 } ?: return Verdict.Inactive` — 네트워크를 타지
  않으므로 `Unknown` 이 아니라 `Inactive`(기억 삭제 허용)다.
- `findFromChatRooms()`: 0 이하 후보를 **조회 전에** 버린다. 걸러낸 항목은 `CHAT_ROOM_SCAN_LIMIT`(5)
  요청 예산도 쓰지 않는다.
- `remember()`: 조회할 수 없는 id 는 저장도 하지 않는다(들어오는 자리에서 차단).
- `ActiveParty.rememberParty()`: 같은 기준으로 한 번 더(서버 join 응답이 0 을 주는 사고 대비).

### 부수 발견 — 채팅방 폴백이 지금 서버에선 사실상 죽어 있다

위 사정 때문에 「기억이 없을 때 채팅방 목록으로 진행 중 방을 찾는」 2단 폴백은 **현 서버에서 아무것도
찾지 못한다**(예전에는 찾지 못하면서 404 를 냈고, 이제는 조용히 건너뛴다 — 동작 변화는 404 가 사라진
것뿐이다). 재설치·다른 기기 합류 복구 경로가 그만큼 비어 있다. 되살리려면 후보마다
`GET /chat-rooms/{id}`(이 응답에는 partyId 가 있다)를 한 번 더 읽어야 하는데, 기억 없는 드문 경우에
요청을 N 배로 늘리는 선택이라 **이번 범위에서는 하지 않고 클래스 KDoc 에 사정을 적어 두었다.** 리더 판단
필요(별 작업으로 분리 권장).

## 5. 테스트

`data` 15건(기존 11 + 신규 4) · `presentation` 16건(신규 2파일: 진입 중복 9 + 채팅방 폴링 게이트 7).

- D-3: `유효하지 않은 기억은 조회하지 않고 지운다`(`"0" "-1" "" " " "null"`), `partyId 가 0 인 채팅방은
  조회 대상에서 건너뛴다`, `채팅방이 전부 partyId 0 이면 방 상세를 한 번도 읽지 않는다`,
  `유효하지 않은 id 는 기억에 심지 않는다` — 모두 **가짜 API 호출 기록이 비어 있는지**로 검증(기존 관례의
  `FakeRideRepository.detailCalls`).
- D-2: `ActivePartyEntryTest` — 겹침 차단, 25ms·425ms 창 안 차단, 500ms 뒤 통과, 느린 응답은 **끝난
  시각**부터 창 시작, 실패 조회도 창 생성, 합류 직후는 창 무시, 합류 직후 조회도 뒤따르는 refresh 흡수,
  `needsDetailAfterRemember` 양방향. 시계는 가짜 람다(presentation 에 coroutines-test 가 없어 ViewModel
  대신 순수 게이트를 테스트 — `DriverPollGateTest` 관례).
- 채팅방 폴링 게이트: `ChatRoomPollGateTest` — **안 보이면 12개 조합 전부 false**(누수 회귀 방지),
  모집 중에도 찾음(Backend #109 회귀 방지), 찾으면 멈춤, 캐시를 버리면 다시 찾음, 방 없음·끝난 방은 안 찾음.

### 결과

```
./gradlew :data:testDebugUnitTest :presentation:testDebugUnitTest --console=plain
→ BUILD SUCCESSFUL   (공용 워킹트리, 2026-10-13 12:03)
   data 278건 · presentation 214건 · 실패 0 · 에러 0
   (내 신규/수정 31건 포함: RemoteActivePartyRepositoryTest 15 · ActivePartyEntryTest 9 ·
    ChatRoomPollGateTest 7. 총건수는 다른 에이전트의 신규 테스트까지 합친 값이다)
```

### 공용 워킹트리에서 겪은 깨짐 — 전부 남의 파일 (원인 구분)

마지막 실행은 공용 워킹트리에서 통과했다. 그 전에 세 번 깨졌고 **셋 다 내 변경과 무관**해서 고치지 않았다.

| 시점 | 깨진 파일 | 원인 | 내 것? |
|---|---|---|---|
| 1 | `data/remote/UserApi.kt:93` | KDoc 본문의 `/api/v1/users/**` — **Kotlin 블록 주석은 중첩된다.** 그 `/*` 가 주석을 한 겹 더 열어 파일 끝까지 삼켜 `:71 Missing '}'`·`:105 Unclosed comment`·`RemoteAuthRepository:179 Unresolved 'deleteAccount'` 가 뒤따랐다 | 아니다(탈퇴 연동). 그쪽에서 이미 고쳤다 |
| 2 | `data/src/test/.../RemoteAuthRepositoryTest.kt`, `.../push/FcmTokenRegistrarTest.kt` | 새 `UserApi.deleteAccount`·`RemoteAuthRepository(activePartyStorage=…)` 가 fake 에 미반영 + 같은 패키지에서 내 파일의 file-private `FakeActivePartyStorage` 를 참조(Kotlin 은 같은 패키지의 private 최상위 선언을 재선언 충돌로 본다) | 아니다. 그쪽에서 정리됨 — 내 fake 는 그대로 두었다 |
| 3 | `presentation/feature/mypage/AccountDeleteRoute.kt:136·183·184` | `Buttons.kt` 쪽 `deleted` 파라미터 미반영(`:app:assembleDebug` 에서만 보임) | 아니다(탈퇴 연동, 작성 중) |

그래서 **`:app:assembleDebug` 는 아직 통과를 확인하지 못했다**(위 3번 때문). 내 변경이 모듈 경계를
넘는 일은 없다 — 추가한 것은 `ActiveParty.kt` 의 `internal`/`private` 심볼과 `ActivePartyViewModel` 의
공개 메서드 2개(`onHostStart`/`onHostStop`, 아직 호출부 없음)뿐이고, 바꾼 `lookupChatRoom` 은 private 다.
`:presentation:testDebugUnitTest` 는 `presentation` main 전체를 컴파일하므로 내 코드의 컴파일은 그것으로
확인됐다.

## 6. 추가 과제 — `pollChatRoom()` 가시성 게이트 (리더 추가 지시)

### 무엇이 문제였나

`pollChatRoom()`(10초 주기, `GET /chat-rooms/me` + 필요 시 방 상세)의 호출부는 `MainNavGraph` 의
**NavHost 레벨 `LaunchedEffect`** 다. NavHost 컴포지션은 화면을 옮겨도, **앱이 백그라운드로 가도**
살아 있다. 그래서 「진행 중인 방이 있고 채팅방 id 를 아직 모르는」 동안에는 어느 화면에 있든, 앱을
내려놓아도 10초마다 요청이 나갔다. D-1(17 탐색 4초 폴링)과 성격이 같고, `120` §5 가 「가시성과 무관하게
네트워크를 때리는 루프로 남은 유일한 건」으로 지목한 자리다.

실제로 오래 도는 경우: **방을 만들어 혼자 기다리는 사용자**(채팅방은 누군가 합류해야 생긴다).
10분이면 60건이다. 반대로 채팅방을 찾으면 그 뒤로는 요청이 없었다(예전 루프도 `continue` 로 걸렀다).

### 어떻게 고쳤나 — `ActiveParty.kt` 안에서만

`ProcessLifecycleOwner` 는 **쓸 수 없다.** `presentation/build.gradle.kts` 에는
`lifecycle-runtime-compose`·`lifecycle-viewmodel-compose` 만 있고 `lifecycle-process` 가 없다
(지시대로 **의존성을 추가하지 않았다**). ViewModel 에는 Context 도 없다. 그래서 신호 입구를 두는 쪽을 골랐다.

```kotlin
private val hostVisible = MutableStateFlow(true)      // 기본 true — 아래 「배선」 참고
fun onHostStart() { hostVisible.value = true }
fun onHostStop() { hostVisible.value = false }

internal fun shouldPollChatRoom(visible: Boolean, ride: Ride?, chatRoomId: Long?): Boolean =
    visible && chatRoomId == null && ride != null && ride.activeStage != ActiveStage.NONE

suspend fun pollChatRoom() {
    combine(hostVisible, _ride, _chatRoomId) { visible, ride, chatRoomId ->
        ride.takeIf { shouldPollChatRoom(visible, ride, chatRoomId) }?.id?.toLongOrNull()
    }
        .distinctUntilChanged()
        .collectLatest { partyId -> … while (isActive) { delay(10s); lookupChatRoom(partyId) } }
}
```

17 탐색이 쓴 모양과 같다 — `combine` + `collectLatest` 라서 **조건이 깨지면 진행 중인 조회와 `delay` 가
함께 죽는다**(깨어나 아무 일도 안 하는 주기조차 남지 않는다). 흘리는 값을 `Ride` 가 아니라 **방 id(Long)**
로 좁힌 이유: Ride 객체를 흘리면 status 가 바뀔 때마다 패스가 재시작돼 10초 타이머가 초기화된다.
그 참에 `lookupChatRoom(ride: Ride)` → `lookupChatRoom(partyId: Long)` 로 바꿨다(호출부 3곳 모두 id 만 쓴다).

**멈추는 조건 / 다시 켜지는 조건** (기존 종료 조건을 깨지 않았다)

| 멈춘다 | 다시 켜진다 |
|---|---|
| 앱이 가려짐(`onHostStop`) | 앱 복귀(`onHostStart`) |
| 진행 중인 방이 없음(`clearParty`·resolve 실패) | 방 생성·합류(`rememberParty`)·`refresh()` 로 방을 찾음 |
| **채팅방을 찾음**(기존 종료 조건 — 이제는 깨어나지도 않는다) | 「채팅방 나가기」·403 으로 캐시를 버림(`forgetChatRoom`) |
| 끝난 방(`ActiveStage.NONE`) | — |

다시 켜지면 **10초 뒤** 첫 조회다. 17 탐색처럼 「복귀 즉시 1회」를 붙이지 **않은** 이유: 앱 복귀와
홈·합승 진입은 이미 `refresh()` → `reload()` 가 채팅방을 **그 자리에서** 한 번 찾는다. 즉시 조회를 또
붙이면 복귀마다 2건이 된다.

**RESUMED 가 아니라 STARTED** 를 권한다 — NavHost 는 전환 애니메이션이 끝난 뒤에야 RESUMED 로 올린다
(`120` §2-1 과 같은 근거, `MyLocationState`·`ChatRoute` 도 STARTED).

### ⚠ 남은 배선 — 누가 무엇을 불러야 하는가

`MainNavGraph.kt` 는 열지 않았다(다른 에이전트 작업 중). **compose-builder 또는 리더가** `MainNavGraph`
의 `activePartyViewModel` 생성 직후(지금 `LaunchedEffect(activePartyViewModel) { … pollChatRoom() }` 와
`LifecycleResumeEffect(Unit) { … refresh() }` 가 있는 자리, 2026-10-13 기준 `:163`~`:168`)에 **이 한 덩어리**를
넣어야 한다:

```kotlin
LifecycleStartEffect(activePartyViewModel) {
    activePartyViewModel.onHostStart()
    onStopOrDispose { activePartyViewModel.onHostStop() }
}
```

`androidx.lifecycle.compose.LifecycleStartEffect` 는 이미 이 모듈에서 쓰는 API 다(의존성 추가 불필요).

**배선 전까지는 동작이 지금과 똑같다** — `hostVisible` 기본값이 `true` 다. 일부러 그렇게 뒀다:
기본값을 false 로 두면 배선이 빠진 채 머지됐을 때 21 의 「채팅 열기」가 **영영 뜨지 않는다**(기능 손실).
기본값 true 면 최악이 「고쳐지지 않은 상태 유지」다. 즉 **배선이 붙기 전에는 이 건의 QA 숫자가 변하지
않는다** — §7 에 그렇게 적었다.

## 7. QA 가 셀 것

1. **21 진입 시 `GET /api/v1/matching/rooms/{id}` 건수**
   - 방 생성(20 → 21), 합류(19 → 21): **2건 → 2건 유지가 정상이다**(§2 「남는 1건」). 홀더 쪽 1건은
     계층이 달라 이번 수정으로 사라지지 않는다. **3건이 보이면 회귀**다.
   - 합류 교착 복구 경로(서버가 「이미 이 방 멤버」로 답하는 경로)에서 홈/합승 배너가 **이미 그 방을
     띄우고 있던** 상태였다면 **3건 → 2건**.
   - 21 → 뒤로 → 14 홈(또는 17): 복귀 시 상세 **0건**(예전엔 복귀 `refresh()` 로 1건). 500ms 창.
   - 앱 백그라운드 → 복귀(홈에 머문 상태): 1건 이하.
2. **방 종료·「합승 완료」 직후 `GET /api/v1/matching/rooms/0`: 0건**(404 0건). 방 번호 자리에 0 이나
   음수가 찍힌 요청이 **하나도** 없어야 한다.
3. **배너**: 21 진입 후 홈/합승으로 돌아갈 때 「진행 중 탑승」 배너가 **즉시**(빈 창 없이) 뜨는지.
   방을 나가거나 3분 타임아웃된 뒤에는 **사라지는지**(창이 홀더 값을 붙잡아 두지 않는지).
4. **21 의 「채팅 열기」**: 합류 직후 21 에서 10초 안에 뜨는지(홀더가 채워져 `pollChatRoom` 이 도는지),
   그 채팅방 헤더에 「매칭 화면으로 →」가 있는지.
5. **채팅방 감시 폴링(`GET /api/v1/chat-rooms/me`, 10초) — §6 의 배선이 붙은 뒤에만 숫자가 변한다.**
   - 방을 **만들고 혼자 대기**(채팅방이 아직 없는 상태)로 두고 **홈 버튼 → 2분 → 복귀**:
     백그라운드 구간 **0건**(수정·배선 전: 약 12건). 복귀 직후에는 `refresh()` 가 거는 1건이 정상이고,
     그 뒤 10초 주기가 재개된다.
   - 같은 상태로 21 에 머물면 10초 주기는 그대로 돈다(가려지지 않았으므로 정상).
   - **찾은 뒤에는 0건** — 「채팅 열기」가 뜬 뒤로는 `chat-rooms/me` 가 더 나가지 않아야 한다.
   - 회귀 주의: 합류 직후 21 에서 「채팅 열기」가 **10초 안에** 뜨는지, 「채팅방 나가기」 후 다시
     진행 화면으로 가면 버튼이 **다시** 뜨는지(캐시를 버린 뒤 재탐색).
6. 선행 조건: `:app:assembleDebug` 는 탈퇴 연동 쪽 `AccountDeleteRoute.kt`·`Buttons.kt` 가 맞물려야
   통과한다(§5 표 3번).
