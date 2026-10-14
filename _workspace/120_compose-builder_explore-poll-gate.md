# 120 · compose-builder — 17 탐색 폴링을 화면 가시성에 묶음 (QA D-1)

범위: `presentation/.../feature/explore/` 와 그 테스트만. 다른 에이전트가 동시에 고치고 있는
`MatchWaitingRoute.kt`·`MainNavGraph.kt`·`core/ActiveParty.kt`·`RemoteActivePartyRepository.kt`·
`feature/mypage/*`·`Auth*` 계열은 **열지 않았다**(읽기만 — 아래 §5 의 보고도 수정 없이 위치만 적었다).
실기 검증은 하지 않았다(QA 담당).

입력: `_workspace/00_input_resume-qa-fixes.md` A/D-1, `_workspace/119_qa-verifier_report.md` §6 D-1,
`_workspace/117_compose-builder_waiting-sse.md` §4(21 의 `realtimeAlive` → `shouldPollFallback` 모양),
`.claude/skills/compose-feature/SKILL.md`.

---

## 1. 변경 파일

| 파일 | 내용 |
|---|---|
| `presentation/src/main/kotlin/com/moyeota/presentation/feature/explore/ExploreRoute.kt` | 가시성 게이트. `visible` StateFlow + `onScreenStart()`/`onScreenStop()`, 수집 루프를 `combine(request, visible)` 로 묶음, 순수 함수 `nearbyPollStep()` + `NearbyPollStep` 신설, 취소 예외 되던지기 |
| `presentation/src/test/kotlin/com/moyeota/presentation/feature/explore/ExplorePollGateTest.kt` | **신규 9건** — 가시성 게이트·복귀 즉시 조회·디바운스 유지 |

화면 디자인·문구·레이아웃은 건드리지 않았다. `ExploreScreen.kt` 는 **무수정**이다 —
시트 단계·카메라(`rememberSaveable` + `ExploreCameraStateSaver`)·지도 idle 보고 경로 모두 그대로다.
외부 라이브러리 추가 없음(`lifecycle-runtime-compose` 는 `presentation/build.gradle.kts` 에 이미 있고
`MainNavGraph`·`ChatRoute`·`MyLocationState` 가 이미 쓰고 있다).

---

## 2. 게이트 방식 — 「보일 때만 돈다」를 어디에 적었나

### 2-1. 신호: `LifecycleStartEffect` (Route) → `onScreenStart()`/`onScreenStop()` (ViewModel)

```kotlin
LifecycleStartEffect(viewModel) {
    viewModel.onScreenStart()
    onStopOrDispose { viewModel.onScreenStop() }
}
```

`ExploreViewModel` 은 탐색의 `NavBackStackEntry` 에 매여 있어 **앞으로 이동했을 뿐 pop 되지 않은 동안**
계속 살아 있고, `viewModelScope` 는 화면이 보이는지 보지 않는다. 그래서 신호를 Route 에서 내려 준다.

**RESUMED 가 아니라 STARTED 를 골랐다**(지시의 권장은 `LifecycleResumeEffect` 였다 — 바꾼 이유):

1. **NavHost 는 전환 애니메이션이 끝난 뒤에야 엔트리를 RESUMED 로 올린다**(`onTransitionComplete`).
   RESUMED 로 묶으면 탭을 열고 **첫 조회가 전환 시간만큼 늦는다** — 이번 지시의 요구사항
   「돌아오면 즉시 1회 조회, 옛 목록을 보여주지 말 것」과 정면으로 어긋난다.
2. **백스택에 가린 엔트리는 CREATED 까지 내려간다**(위에 올라온 게 `dialog` 가 아닌 한).
   그래서 「21·20·채팅이 위에 올라옴」은 STARTED 로도 똑같이 잡힌다 — D-1 의 증상이 정확히 이것이다.
3. **앱 백그라운드(ON_STOP)** 도 호스트 lifecycle 이 엔트리 상한을 내리므로 함께 잡힌다.
4. 같은 화면의 **GPS 구독이 이미 STARTED 로 묶여 있다**(`core/location/MyLocationState.kt:146`
   — 「화면이 보이는 동안(STARTED 이상)에만 구독한다」). 네트워크와 GPS 가 서로 다른 경계에서
   켜졌다 꺼지면 추적이 어려워진다.
5. 선례도 STARTED 다 — 24 채팅방이 `LifecycleStartEffect(room.id)` → `onScreenStart`/`stopPolling`
   으로 똑같이 묶고 있다(`feature/chat/ChatRoute.kt:771`, `:432`).

RESUMED 와 달라지는 유일한 경우는 **위에 `dialog` 목적지가 떴을 때**(그때만 아래 엔트리가 STARTED 로 남는다).
지금 탐색 위에 뜨는 목적지는 `JOIN_CONFIRM`·`DESTINATION` 둘 다 `composable` 이라 해당이 없고,
설령 모달이 떠도 그 뒤로 **지도가 그대로 보이므로** 거기서 멈추는 게 오히려 옳지 않다.

### 2-2. 판정: 순수 함수 하나 (21 의 `shouldPollFallback` 과 같은 자리)

```kotlin
internal fun nearbyPollStep(visible, manual, hasParties, requestSeq, shownSeq): NearbyPollStep = when {
    !visible -> STOP
    manual || !hasParties || requestSeq == shownSeq -> QUERY_NOW
    else -> DEBOUNCE_THEN_QUERY
}
```

| 판정 | 조건 | 왜 |
|---|---|---|
| `STOP` | 안 보임 | **D-1 수정의 핵심.** 보이지 않는 화면이 서버를 부를 이유가 없다 |
| `QUERY_NOW` | 「다시 시도」 | 방금 누른 버튼이 안 먹은 것처럼 보이면 안 된다 |
| `QUERY_NOW` | 목록 없음(첫 조회·에러 화면) | 보여줄 게 없으니 늦출 이득이 없다 |
| `QUERY_NOW` | `requestSeq == shownSeq` | **복귀**(범위는 그대로인데 패스가 다시 들어왔다) → 즉시 1회 |
| `DEBOUNCE_THEN_QUERY` | 그 밖(새 범위 + 목록 있음) | 팬·줌 연타의 중간 범위마다 서버를 때리지 않는 **기존 동작 유지** |

### 2-3. 루프: 범위와 가시성을 **같은 흐름**에 묶었다

```kotlin
combine(request.filterNotNull(), visible, ::Pair).collectLatest { (req, shown) -> … }
```

`collectLatest` 가 둘 중 **어느 쪽이 바뀌어도** 앞 패스를 취소하므로 세 가지가 공짜로 성립한다.

- 가려지면 → 진행 중인 조회와 `delay` 루프가 **함께 죽는다**(깨어나 아무 일도 안 하는 주기조차 없다).
- 다시 보이면 → **같은 요청으로 패스가 새로 시작**된다 = 즉시 1회 조회 + 주기 재개.
- 새 범위가 오면 → 기존처럼 앞 범위의 폴링이 취소되고 새 범위로 다시 시작된다.

「ViewModel 에 플래그를 두고 매 주기 `continue`」 방식(21·채팅이 쓰는 모양)과 결과는 같지만,
탐색은 폴백이 아니라 **주 수단**이 폴링이라 가려진 동안 4초마다 깨어날 이유가 전혀 없어 흐름 쪽을 골랐다.
켜고 끄는 스위치를 순수 함수 한 곳에 모으는 모양은 21 과 같다.

### 2-4. 곁가지 수정 1건 — 취소를 실패로 오인하던 길

`load()` 의 `catch (e: Exception)` 가 `CancellationException` 까지 삼켜,
**첫 조회 중에 화면을 떠나면** 「동승 목록을 불러오지 못했어요」가 상태에 박혔다(원래도 있던 결함 —
범위 변경으로도 취소된다). 가시성 게이트가 생기면서 이 길을 **자주** 지나게 되므로
`catch (e: CancellationException) { throw e }` 를 앞에 세웠다(20 `JoinConfirmRoute` 와 같은 처리).

### 2-5. 곁가지 수정 2건 — 복귀 때 기준 범위로 되돌아가던 길

Route 의 진입 이펙트 `LaunchedEffect(Unit) { onVisibleBoundsChange(defaultExploreBounds(...)) }` 는
**탭을 다녀올 때마다 다시 실행된다**(컴포지션이 새로 만들어진다). 그러면 복원된 카메라와 무관하게
기준점(내 위치 또는 `MoyeotaDefaultCamera`) 범위로 요청이 들어가, ① 지도는 보던 자리에 있는데
**리스트·마커만 다른 동네**가 잠깐 보이고 ② 복귀 즉시 조회가 그 엉뚱한 범위로 가 버린다.

그래서 ViewModel 에 `onInitialBounds(bounds)` 를 두고 **이미 범위를 받은 뒤의 호출을 무시**한다
(`request.value != null` → return). 첫 진입은 그대로고, 복귀는 `onScreenStart()` 가 **마지막으로 보던
범위**로 처리한다. 복귀 때 나가는 조회가 최대 3건(기준 범위 + 복원 카메라 + 즉시 1회)에서
**1건(+카메라 범위가 실제로 달라졌으면 1건)** 으로 줄었다. 지도 쪽 코드는 손대지 않았다.

---

## 3. 상황별 동작

| 상황 | 조회 | 비고 |
|---|---|---|
| 탐색 탭에 머묾 | 4초 주기 (변화 없음) | 평상시 동작은 그대로다 |
| **21·20·채팅 등이 위에 올라옴** | **0회** | 엔트리 CREATED → `onScreenStop()`. D-1 수정 지점 |
| **하단탭으로 홈·채팅·마이 이동** | **0회** | 같은 경로(탐색 엔트리가 백스택에 남아도 CREATED) |
| **앱 백그라운드(홈 버튼·앱 전환)** | **0회** | 호스트 ON_STOP → 엔트리 상한 CREATED |
| **탭 복귀 / 위 화면 pop** | **즉시 1회** → 4초 주기 재개 | 같은 seq 로 패스 재시작(`QUERY_NOW`). 보던 목록은 로딩으로 덮지 않고 결과로 덮는다 |
| 복귀 직후 진입 이펙트 | **0회** | `onInitialBounds` 가 기준 범위를 무시한다(§2-5) — 복원된 카메라가 기준이다 |
| 복귀 직후 지도 idle | 복원된 카메라 범위가 **같으면 0회**(기존 `request.value?.bounds == bounds` 조기 반환), 다르면 디바운스 후 1회 | 카메라는 `rememberSaveable` 로 복원돼 초기화되지 않는다(무수정) |
| 카메라 팬·줌(보이는 중) | 400ms 디바운스 후 1회 + 주기 재개 | 뷰포트 기반 `swLat/swLng/neLat/neLng`·`expanded()` 경로 **무변경** |
| 가려진 동안 카메라 변경 | (일어나지 않음) 일어나도 복귀 시 그 범위로 조회 | 가려진 화면은 컴포지션에서 내려가 지도 idle 이 오지 않는다 |
| 에러 화면 → 탭 이동 → 복귀 | 즉시 1회(`hasParties == false`) | 에러가 복귀만으로 복구된다 |
| 위치 권한 다이얼로그 | 떠 있는 동안 멈추지 않음(ON_PAUSE 뿐) | 지도가 보이는 상태라 의도된 동작 |

**깨뜨리지 않은 것**: 시트 단계(`rememberSaveable`), 지도 카메라 보존, 뷰포트 조회 범위,
`expanded()` 여유분, 마커·리스트·헤더가 같은 목록을 보는 성질, 「갱신 실패로 에러 화면으로 덮지 않음」.

---

## 4. 테스트

`presentation/src/test/.../feature/explore/ExplorePollGateTest.kt` **9건**
(순수 함수만 — `presentation` 테스트 의존성은 `junit` 뿐이라 `runTest` 로 코루틴 루프를 돌릴 수 없다.
그래서 **판정**을 루프에서 떼어내 `nearbyPollStep()` 으로 만들고 그것을 고정했다. 루프 배선 자체
(=`combine`+`collectLatest` 가 취소를 전파하는가)는 단위 테스트 범위를 넘으므로 QA 의 logcat 계수로 확인한다.)

- 안 보일 때는 **manual·hasParties·seq 의 모든 조합**에서 `STOP`(8조합 반복 검사) ← D-1 회귀 방지
- 가려진 동안에는 복귀 모양(같은 seq)이어도 가시성이 앞선다
- 첫 진입·「다시 시도」는 즉시
- 새 범위 + 목록 있음은 디바운스(기존 동작 유지)
- 복귀는 즉시 1회 — 목록이 있을 때·에러 화면일 때 둘 다
- 복귀 뒤 지도가 다른 범위를 보고하면 멈추지 않고 그 범위로 조회

```
./gradlew :app:assembleDebug :presentation:testDebugUnitTest --console=plain -q
→ (결과는 §7)
```

---

## 5. 같은 성격의 루프 — `presentation` 전체 점검 결과

`while (isActive)`/`while (true)` + `delay(` 를 전수 검사했다. **내 범위 밖은 고치지 않았다.**

줄 번호는 다른 에이전트가 같은 워킹트리를 고치는 중이라 움직인다 — **심볼 이름**이 안정된 기준이다
(아래 번호는 2026-10-13 작업 시점).

| 파일 · 심볼 | 루프 | 가시성에 묶여 있나 | 판단 |
|---|---|---|---|
| `core/ActiveParty.kt:296` `pollChatRoom()` (10초, `GET /chat/rooms` + 필요 시 방 상세) | 채팅방 생성 감시 | **아니다 — 가장 D-1 과 닮았다** | 호출부가 `core/MainNavGraph.kt:163` 의 `LaunchedEffect(activePartyViewModel)` 로 **NavHost 컴포지션 전체**다. 어느 화면에 있든, **앱이 백그라운드여도** 진행 중 방이 있고 채팅방 id 를 모르는 동안 10초마다 조회가 나간다. 두 파일 모두 다른 에이전트 작업 중이라 **보고만** 한다. 고칠 때는 그 `LaunchedEffect` 를 `LifecycleStartEffect`(또는 `repeatOnLifecycle(STARTED)`)로 바꾸고, 복귀 시 1회 조회를 붙이면 된다 |
| `feature/matching/DispatchStatusRoute.kt:125` `pollDriver()` (7초, 방 상세 + 기사 위치) | 25 배차 | 부분 — 컴포지션 스코프(`:246` `LaunchedEffect`)라 **다른 화면이 위에 오면 멈추지만**, 앱 백그라운드에서는 계속 돈다 | 범위 밖(25 담당). 택시 모드 전용이라 현재 실사용 서버(동승)에서는 돌지 않는다 |
| `feature/chat/RideOngoingRoute.kt:88` `observeRideFinish()` (8초, 방 상세) | 26 운행 중 | 위와 같음(`:165` `LaunchedEffect`) | 범위 밖(26 담당). 같은 이유로 택시 모드 전용 |
| `feature/chat/ChatRoute.kt:442` `startPolling()` (소켓이 끊길 때만) | 24 채팅 | **묶여 있다** — `LifecycleStartEffect(room.id)`(`:771`) → `stopPolling()`(`:433`) | 이번 수정이 따른 **선례** |
| `feature/matching/MatchWaitingRoute.kt:240` 폴백 폴링(6초) | 21 대기 | 가시성에는 묶이지 않음. 단 `realtimeAlive` 로 평상시 0회고, 21 에서 가는 길(25·26·홈)은 이 화면을 **대체**한다 | 범위 밖(21 담당, 작업 중). 백그라운드에서 SSE 가 끊기면 6초 폴백이 돌 수 있다 — 같이 묶으면 더 좋다 |
| `core/AppConfigViewModel.kt:66` `load()` | 설정 조회 **재시도**(백오프) | — | 폴링이 아니다. 성공·재시도 소진으로 끝난다 |
| `feature/auth/PhoneVerification.kt:504` `startTicker()` (1초) | 인증 카운트다운 | 아니다(`viewModelScope`) | **네트워크 없음**. 셀 것이 없어지면 스스로 멈춘다. 백그라운드에서 1초 틱이 남는 건 사소하나, 고치려면 같은 모양을 쓸 수 있다 |
| `feature/chat/ChatScreen.kt:729` `keywordRanges()` | 문자열 탐색 | — | 폴링과 무관 |
| `core/location/MyLocationState.kt:146` | GPS 구독(1초) | **묶여 있다**(STARTED) | 이번 선택(STARTED)의 근거 |

요약: **가시성과 무관하게 네트워크를 때리는 루프는 `ActiveParty.pollChatRoom()` 하나가 남는다.**
D-1 과 성격이 같고(백스택·백그라운드 무관하게 돈다) 주기가 10초라 체감은 덜하지만,
「평상시 네트워크 0」을 목표로 하면 다음 차례다. 소유자가 다른 파일이므로 **리더가 배분**해야 한다.

---

## 6. QA 가 확인할 것

1. **D-1 재현 경로로 0건 확인** — 동승 탭(17) → 시트 스와이프업 → 카드 「합류」 → 20 → 21 진입 후
   **2분 체류**. logcat 에서 `GET /api/v1/matching/rooms?swLat=` 가 **0건**이어야 한다
   (수정 전: 약 34건). 세는 명령 예:
   `adb logcat -d | grep -c "matching/rooms?swLat"` — 21 진입 시점에 `adb logcat -c` 로 비운 뒤 센다.
2. **하단탭 이동도 0건** — 17 → 채팅 탭 → 1분 체류 → 같은 계수가 0. 홈·마이도 같다.
3. **앱 백그라운드 0건** — 17 에서 홈 버튼 → 1분 → 복귀. 백그라운드 구간에서 0건.
4. **복귀 즉시 1건** — 위 2·3 에서 **돌아온 직후 1건이 바로** 나가야 한다(4초를 기다리지 않는다).
   화면이 로딩으로 깜빡이지 않고 목록이 유지된 채 갱신되는지도 본다.
5. **카메라 보존(회귀 주의)** — 17 에서 지도를 다른 동네로 팬·줌 → 탭 이동 → 복귀 시
   **카메라가 그 자리에 있어야** 한다(초기 위치로 돌아가면 회귀). 복귀 후 목록이 그 범위의 방인지도 본다.
6. **팬·줌 조회 유지** — 지도를 움직이면 멈춘 뒤 약 0.4초에 1건, 연타 시 중간 범위마다 나가지 않는지.
7. **에러 복구** — 비행기 모드로 17 진입(에러 화면) → 비행기 모드 해제 → 탭 이동 후 복귀하면
   「다시 시도」를 누르지 않아도 목록이 떠야 한다.
8. **첫 진입 지연 없음** — 홈에서 동승 탭을 처음 열 때 목록이 예전과 같은 체감으로 떠야 한다
   (STARTED 를 고른 이유가 이것이다 — RESUMED 였다면 전환 애니메이션만큼 늦는다).

---

## 7. 빌드 결과 — 통과

```
./gradlew :app:assembleDebug :presentation:testDebugUnitTest --console=plain -q
→ BUILD SUCCESSFUL (exit 0)
   단위 테스트 192건 · 실패 0 · 에러 0 · 건너뜀 0
   (내 신규 `ExplorePollGateTest` 9건 포함. 총건수는 회원 탈퇴 작업의 신규 테스트까지 합친 값이다)
```

**중간에 두 번 깨졌는데 둘 다 내 파일이 아니다** — 같은 워킹트리에서 진행 중인 **회원 탈퇴 연동**(입력 B)의
중간 상태였고, 45초 간격 재시도 로그에서 `feature/explore` 에러는 **매 시도 0건**이었다.

| 시도 | 깨진 파일 | 내 것? |
|---|---|---|
| 1~3 | `presentation/feature/auth/SignupDraft.kt`(`when` 비망라), `data/repository/RemoteAuthRepository.kt` | 아니다 |
| 4~10 | `data/remote/UserApi.kt`, `data/repository/RemoteAuthRepository.kt`, (한때) `presentation/feature/mypage/AccountDeleteRoute.kt` | 아니다 |
| 11 | — | **통과** |

중간 단계에서 `:presentation:testDebugUnitTest` 만 따로 돌려(이 모듈은 `domain`·`core:designsystem`
에만 의존한다) **내 변경의 컴파일·테스트 범위 전부**를 먼저 통과시켜 두었다 — 183건 전부 통과.
`app`·`data`·`domain`·`core` 어디에서도 `ExploreViewModel`·`onVisibleBoundsChange`·`onInitialBounds`·
`nearbyPollStep` 을 참조하지 않아(`grep` 확인) 모듈 경계를 넘는 영향도 없다.

---

## 8. 미완 · 넘길 것

1. **실기 검증 안 함**(지시대로). §6 이 QA 가 셀 목록이다 — 핵심 숫자는 **21 체류 중 `rooms?swLat` 0건**.
2. **`ActiveParty.pollChatRoom()` 이 같은 성격으로 남아 있다**(§5 첫 줄). D-1 과 성격이 같고
   앱이 백그라운드여도 10초마다 돈다. 파일 소유자가 달라 손대지 않았다 — **리더가 배분**해야 한다.
3. 25·26 의 상세 폴링은 컴포지션 스코프라 화면 전환에는 멈추지만 **백그라운드에서는 돈다**(§5).
   택시 모드 전용이라 현재 운영 서버(동승)에서는 돌지 않아 급하지 않다.
4. `nearbyPollStep` 이 고정하는 것은 **판정**이고, 「가려지면 루프가 실제로 죽는가」는
   `combine`+`collectLatest` 의 취소 전파에 달려 있다. `presentation` 의 테스트 의존성이 `junit` 뿐이라
   (코루틴 테스트 라이브러리를 추가하면 빌드 파일 변경이 되므로 넣지 않았다) 그 배선은 단위 테스트로
   확인하지 못했다 — QA 의 logcat 계수가 그 역할을 한다.
