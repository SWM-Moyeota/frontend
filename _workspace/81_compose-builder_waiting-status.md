# 81 · 21 매칭 대기 — 상세 폴링을 가벼운 상태 폴링으로 (Backend #177)

브랜치: `feature/party-status-api` (커밋하지 않음)

## 왜

부하테스트에서 방 상세 폴링(`GET /matching/rooms/{id}`, 쿼리 3개 + 경로 폴리라인)이 전체 요청의 45%였다.
21 대기 화면은 주기 대부분에서 **아무것도 안 바뀐 걸 확인할 뿐**이므로, 서버가 새로 낸 가벼운
`GET /matching/rooms/{id}/status`(쿼리 1개 — 상태·현재 인원·지문)로 「바뀌었나」만 묻고 달라졌을 때만
상세를 읽는다. 안 바뀐 주기의 상세 호출은 **0**이다.

## 변경 파일

| 파일 | 내용 |
|------|------|
| `presentation/src/main/kotlin/com/moyeota/presentation/feature/matching/MatchWaitingRoute.kt` | `observeAutoMatching()` 폴링을 `getPartyStatus` 기반으로 교체, 판단을 순수 함수 2개로 분리, KDoc 갱신 |
| `presentation/src/test/kotlin/com/moyeota/presentation/feature/matching/WaitingPollTest.kt` | 신규 — 폴링 게이트 JUnit4 테스트 7개 |

`data/`·`domain/`·화면(`MatchWaitingScreen.kt`)·`Routes.kt`·`MainNavGraph.kt`는 건드리지 않았다.
(동작 중인 다른 에이전트가 `data/`의 `/status` 구현을 맡는다.)

## 새 폴링 동작 (`MatchWaitingViewModel.observeAutoMatching`)

주기는 그대로 — 신호(SSE) 살아 있으면 20초(`PARTY_POLL_INTERVAL_REALTIME_MS`), 아니면 4초(`PARTY_POLL_INTERVAL_MS`).

1. 들고 있는 방(`UiState.Success.ride`)이 **없으면**(Loading·Error) 비교 기준이 없으니 기존대로 상세를 읽는다.
2. 있으면 `getPartyStatus(partyId)` 를 읽고 `shouldFetchPartyDetail(status, held)` 로 판단한다.
   - **같다** → 상세 호출 없음. 단 신호가 이미 대기 밖 상태를 반영해 뒀다면(`!isWaitingForMembers`) 더 볼 게 없어 루프 종료.
   - **다르다** → `getPartyDetail` 로 상세를 읽어 `_uiState` 갱신. 그 status 가 대기 밖이면 루프 종료.
3. 상태 조회·상세 조회 실패는 기존처럼 조용히 다음 주기(`continue`) — 실패로 루프를 끝내지 않는다(SSE 가 죽은 환경에서 전이를 놓치면 안 되므로).

화면 전환은 그대로 Route 의 `LaunchedEffect(status)` 가 맡는다 — `DISPATCHING`/`ONGOING` → 25 배차 현황,
`COMPLETED`/`CANCELED` → `onPartyClosed()`(홈). 즉 「상세를 한 번 읽어 반영한 뒤 종료」라는 기존 계약은 유지된다.

`observeEvents()`(SSE `Connected`/`Changed`/`Closed` → `refreshQuietly()` = 상세 재조회)는 **그대로**다 —
신호는 "바뀌었다"는 뜻이라 상세를 바로 읽는 게 맞다.

## 분리한 순수 함수 (같은 파일, `internal`)

- `isWaitingForMembers(status: RideStatus): Boolean` — 대기 단계(RECRUITING·MATCHED)인가.
  기존 `companion object` 의 `private val WAITING_STATUSES` 를 대체한다(테스트 가능하게 밖으로 뺐다).
- `shouldFetchPartyDetail(status: PartyStatus, held: Ride?): Boolean` — 상태 응답을 받았을 때 상세를 읽어야 하나.
  `held == null || !status.isSameAs(held)`. 「같나」 판정 자체는 도메인의 `PartyStatus.isSameAs`(지문 우선, 없으면 상태+인원수)에 맡긴다.

## 테스트 (`WaitingPollTest`, 기존 `WaitingModeTest` 스타일)

- 지문 같으면 상세 안 읽음 / 지문 없고 상태·인원 같으면 상세 안 읽음
- 인원 증가, 상태 전이(MATCHED→DISPATCHING) → 상세 읽음
- 인원수 같은 **멤버 교체**도 지문으로 잡아 상세 읽음
- 들고 있는 방이 없으면(Loading·Error) 상세 읽음
- `isWaitingForMembers`: RECRUITING·MATCHED 만 true, DISPATCHING/ONGOING/COMPLETED/CANCELED 는 false

## 검증 결과

```
./gradlew :presentation:compileDebugKotlin :presentation:testDebugUnitTest --console=plain -q
```

컴파일 통과, 경고 없음. 단위 테스트 **69개 전부 통과**(failures 0 / errors 0) — 그중 신규 `WaitingPollTest` 7개.
색상 하드코딩 추가 없음(화면 파일 미수정).

## 진입 경로

14 홈 → 16 조건 설정 → 방 생성 → **21 매칭 대기**(`Routes.MATCH_WAITING` = `"matching/waiting"`,
`MainNavGraph.kt:539`). 합류 경로는 17 탐색 → 20 합류 확인 → 21. partyId 는 라우트 인자가 아니라
NavGraph 가 들고 있는 `createdPartyId`(진행 중 방 기억)에서 온다 — null 이면 더미 화면이라 폴링 자체가 없다.
