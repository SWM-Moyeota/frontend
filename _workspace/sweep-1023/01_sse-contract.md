# 백엔드 SSE 변경이 앱을 깨뜨리는가 — 조사 리포트

- 작성: 2026-10-23
- 백엔드 기준점: `origin/develop` = **`82f000f`** (`Merge pull request #257 from SWM-Moyeota/refactor/remove-sse-version-heartbeat`, 2026-10-22 23:33 KST). 로컬 fetch 시각 2026-10-23 21:30.
- baseline: `284a950` (`Merge pull request #211`)
- 조사 범위: SSE · 매칭방 잠금 · version. 실시간매칭/즐겨찾기/채팅/결제는 보지 않음.
- 백엔드는 **읽기만** 했다. 서버를 띄우지 않았다. 코드도 고치지 않았다.

> ### 읽기 함정 — 백엔드 워킹트리를 그대로 읽으면 틀린다
> 백엔드 체크아웃은 지금 `refactor/driver-review` 브랜치이고 `#257` 이전에서 갈라졌다. 그래서
> **`src/.../matching/sse/infrastructure/PartySseHeartbeat.java` 가 디스크에는 존재한다.**
> `origin/develop` 에는 없다(`git show origin/develop:...` → `exists on disk, but not in 'origin/develop'`).
> 이 리포트의 모든 인용은 `git show origin/develop:<path>` / `git diff 284a950 origin/develop` 로 읽은 것이고,
> 줄번호도 그 내용 기준이다.

---

## 1. 현재 SSE 계약 전문 (origin/develop 확정)

### 1.1 엔드포인트

| | |
|---|---|
| 경로 | `GET /api/v1/matching/rooms/{partyId}/events` |
| Content-Type | `text/event-stream` |
| 근거 | `matching/sse/PartySseController.java:35-38` |
| 실패 | `403 NOT_PARTY_MEMBER`, `404 PARTY_NOT_FOUND` — `PartySseController.java:34`, 판정은 `matching/sse/PartySseService.java:21-23` |
| emitter 타임아웃 | 무제한(`new SseEmitter(0L)`) — `PartySseRegistry.java:117-119` |

Swagger 주석(`PartySseController.java:23-33`)이 계약 문서다. 아래는 그 주석과 소스를 대조해 확정한 내용이다.

### 1.2 이벤트 4종 — 이름 · 페이로드 · 발행 조건

**(1) `connected`**

| | |
|---|---|
| 전송 지점 | `PartySseRegistry.java:47` |
| 발행 조건 | 구독이 성립한 직후 **무조건 1회**. 재연결마다 다시. |
| data | `PartyDetailResult` **전체 JSON** (`GET /matching/rooms/{partyId}` 과 같은 모양) |

`PartyDetailResult` 필드 (`matching/party/dto/PartyDetailResult.java:11-19`):
`id, departureLat, departureLng, destinationLat, destinationLng, departure, destination, capacity, currentMembers, departureRadius, destinationRadius, status, createdAt, members[], estimateFare, estimateTime, route, taxiDriverId,` **`version (long)`**

`members[]` 원소 `MemberInfo` (`PartyDetailResult.java:21`):
`publicId, nickname, imageUrl, badgeId, rideCount,` **`host (boolean)`**
— `host` 는 `party.isHost(m.getMemberId())` 결과(`PartyDetailResult.java:33-34`). 서버 방장 = 현재 멤버 중 `min(joinedAt, memberId)` (`Party.java:224-231`).

**(2) `member-joined`**

| | |
|---|---|
| 전송 지점 | `RedisPartyChangeNotifier.java:20-22` → `PartySseRegistry.java:61` |
| 발행 조건 | `PartyService.join` 성공 후 커밋 뒤 (`PartyService.java:119-121`, `afterCommitExecutor`) |
| data | `MemberJoined(member, status, version)` — `matching/party/dto/MemberJoined.java:3` |

- `member` = `MemberInfo` (상세의 멤버와 **같은 record**). `host` 는 `saved.isHost(memberId)` 로 실려 온다(`PartyService.java:119`) — 새로 들어온 사람이라 사실상 항상 false.
- `status` = 들어온 **뒤**의 방 상태. 정원이 차면 그 자리에서 `partyCompletionPolicy.onCompleted` 까지 돌고(`PartyService.java:109-111`) 그 결과가 실린다.
- `version` = 반영 후 버전.

**(3) `member-left`**

| | |
|---|---|
| 전송 지점 | `RedisPartyChangeNotifier.java:25-27` → `PartySseRegistry.java:61` |
| 발행 조건 | `PartyService.leave` 후 **방이 닫히지 않은 경우에만** (`PartyService.java:143-149`). 마지막 멤버 이탈이면 대신 `closed` 가 간다. |
| data | `MemberLeft(publicId, status, hostPublicId, version)` — `matching/party/dto/MemberLeft.java:5` |

- `publicId` = 나간 사람. **null 가능** — 서버가 유저 요약을 못 찾으면 null 을 그대로 싣고도 신호는 보낸다(`PartyService.java:128-130`).
- `hostPublicId` = **`party.leave()` 를 반영한 뒤**의 `party.hostId()` 의 publicId (`PartyService.java:144-146`). 요약을 못 찾으면 null.
- 함께 `leftMemberId` 가 Redis 메시지에 실려(`RedisPartyChangeNotifier.java:26`) 나간 사람의 **자기 연결은 이벤트 없이 종료**된다(`PartySseChannel.java:26-28` → `PartySseRegistry.java:87-100`).

**(4) `closed`**

| | |
|---|---|
| 전송 지점 | `PartySseRegistry.java:76` — `closeAll(partyId, data)` 가 **직접** 보낸다. 트리거는 `PartySseChannel.java:30-32` |
| 전송 직후 | 같은 루프에서 `completeQuietly(emitter)` → 스트림 종료 (`PartySseRegistry.java:83`) |
| data | `PartyClosed(partyId, status, version)` — `matching/party/dto/PartyClosed.java:3` |

발행 조건 **5곳** (★ = baseline 에 없던 신규):

| 경로 | 결과 status | 근거 |
|---|---|---|
| 마지막 멤버 이탈 | `CANCELED` | `PartyService.java:139-142` |
| 합승 종료(참여자 호출) | `FINISHED` | `PartyService.java:195-197` |
| 정원 충족 후 방치 자동 종료 | `FINISHED` | `PartyService.java:209-211` |
| ★ **배차 실패** | `CANCELED` | `PartyAccessService.java:48-52` → `notifyClosed` `:142-145`. 호출자 `dispatch/call/MatchingSweeper.java:56` |
| ★ **운행 완료** | `FINISHED` | `PartyAccessService.java:74-78` → `notifyClosed` `:142-145`. 호출자 `dispatch/ride/RideService.java:51` |

### 1.3 heartbeat — **없다**

- SSE **이벤트** 로서의 heartbeat 는 존재하지 않는다. `#251`(`784bda5`)이 `PartySseHeartbeat`(30초마다 DB 에서 version 을 읽어 `heartbeat` 이벤트 전송)를 넣었고, **`#257`(`b8a308e`)이 클래스·테스트·`Parties.findVersions`·`PartyVersion`·`PartySseRegistry.sendHeartbeat`·`partyIds`·`closeAll(reason)` 까지 전부 제거했다.**
- 남은 것은 **연결 유지용 `:ping` 주석 하나뿐**이다 — 30초 고정, `PartySseRegistry.java:18`(`HEARTBEAT_MS = 30_000`), `:102-111`. 전용 스케줄러 1스레드(`PartySseSchedulerConfig.java:11-17`).
- `git grep -in heartbeat origin/develop -- src/main/java` 결과에서 matching 모듈 히트는 위 세 줄(상수·`@Scheduled`·`remove` reason 문자열)뿐이다. 나머지는 채팅 WebSocket·기사 위치로 무관.
- Swagger 문구: 「30초마다 `:ping` 주석(연결 유지). 45초 동안 아무것도 안 오면 끊긴 것으로 보고 3초 뒤 재연결한다」 (`PartySseController.java:33`).

### 1.4 version 계약 — **필드는 남았다**

`version` 은 `#257` 에서 **제거되지 않았다.** 지워진 건 heartbeat 전송 메커니즘뿐이다.

- 컬럼: `src/main/resources/db/migration/V18__match_room_version.sql`
- 도메인 필드: `Party.java:35`, 생성 시 `0L` (`Party.java:77`)
- **증가 지점은 정확히 4개**: `Party.java:98`(join), `:114`(leave), `:247`(finishWithoutDriver), `:253`(expireCompleted)
- **`failMatching()`(`:206`)·`completeRide()`(`:194`)·`startMatching()`·`assignDriver()`·`startRide()` 는 version 을 올리지 않는다.** 그래서 Swagger 가 「`closed` 는 version 과 상관없이 종료로 처리한다(version 은 직전과 같을 수 있다)」(`PartySseController.java:29`)고 적어 둔 것이다.
- 실려 가는 곳: `connected`(상세의 `version`), `member-joined`, `member-left`, `closed` — **4종 전부**.

규칙 원문 (`PartySseController.java:30-32`):

```
version 규칙: 받은 version 이 내 version+1 이면 반영, 같거나 작으면 버림,
그보다 크면 GET /matching/rooms/{partyId} 로 다시 받아 통째로 교체(스트림은 유지).
connected 와 다시 받은 상세는 내 version 이 없거나 그 이상일 때만 통째로 교체하고, 낮으면 버린다.
connected 전에 온 변경분은 틈으로 보고 상세를 다시 받는다.
version 이 없는 응답(이전 서버)이면 이미 있는 publicId 의 member-joined,
없는 publicId 의 member-left 는 목록은 그대로 두고 상태만 반영한다.
```

백엔드가 **자기 레퍼런스 클라이언트로 같은 규칙을 구현해 두었다**: `loadtest/lib/sse.js:77-135`
(`connected` 는 화면 version 이상일 때만 교체, `member-*` 는 +1 일 때만 반영, 그 외 `resync:gap`, `connected` 전 델타도 `gap`).

### 1.5 이벤트 순서 — **connected 가 먼저라는 보장이 없다**

Swagger: 「연결 중에 방이 바뀌면 변경분이 connected 보다 먼저 올 수 있다」 (`PartySseController.java:26`).

소스가 그 이유를 그대로 보여 준다 (`PartySseRegistry.subscribe`):

```java
// PartySseRegistry.java:36-40  — 먼저 레지스트리에 등록한다
byParty.compute(partyId, (k, set) -> { ...subscriptions.add(subscription); return subscriptions; });
...
// PartySseRegistry.java:47     — 그 다음에야 snapshot 을 "읽어서" 보낸다
emitter.send(SseEmitter.event().name("connected").data(snapshot.get(), MediaType.APPLICATION_JSON));
```

`snapshot.get()` 은 `partyService.getPartyDetail(partyId)` 다(`PartySseService.java:25`) — `@Transactional(readOnly = true)` 로 방·멤버요약·탑승횟수를 읽는 **DB 왕복**(`PartyService.java:154-161`). 그 **등록 이후 ~ connected 전송 전** 구간에서 Redis 리스너 스레드의 `notify`(`PartySseRegistry.java:58-68`)가 **같은 emitter 로 델타를 먼저 쓸 수 있다**. 창은 DB 조회 1회 길이다(짧지 않다).

### 1.6 구독 수명

| 규칙 | 근거 |
|---|---|
| 같은 `(partyId, memberId)` 로 다시 연결하면 **이전 연결을 먼저 닫는다** | `PartySseRegistry.java:32` (`closeMember(..., "replaced")`) |
| 방을 나가면 그 사람 연결은 **이벤트 없이** 종료 | `PartySseChannel.java:26-28`, `PartySseRegistry.java:87-100` |
| `closed` 는 보낸 즉시 전원 종료 | `PartySseRegistry.java:70-85` |
| 관측 지표 신설 | `PartySseMetrics` (`sent(event, ok)`, `closed(reason)`, `bindConnections`) — PR #249 |

---

## 2. baseline(284a950) 대비 무엇이 바뀌었나

| # | 항목 | baseline `284a950` | 현재 `origin/develop` | 들어온 커밋 |
|---|---|---|---|---|
| A | `PartyDetailResult.version` | **없음** | `long version` 추가 (`PartyDetailResult.java:19`) | `784bda5` (#251) |
| B | `PartyDetailResult.MemberInfo.host` | **없음** (`publicId, nickname, imageUrl, badgeId, rideCount`) | `boolean host` 추가 (`PartyDetailResult.java:21`) | `b81aa1a` (#243) |
| C | `member-joined` data | `{member, status}` | `{member, status, version}` (`MemberJoined.java:3`) | `784bda5` (#251) |
| D | `member-left` data | `{publicId, status}` | `{publicId, status, hostPublicId, version}` (`MemberLeft.java:5`) | `hostPublicId`←`b81aa1a`(#243), `version`←`784bda5`(#251) |
| E | `closed` data | **`"42"`** (partyId 숫자 문자열, `String.valueOf(partyId)`) | **`{partyId, status, version}` JSON** (`PartyClosed.java:3`) | `784bda5` (#251) |
| F | `closed` 발행 조건 | leave-CANCELED / finish / expire — 3곳 | **+ 배차 실패(CANCELED), + 운행 완료(FINISHED)** — 5곳 (`PartyAccessService.java:51,77,142-145`) | `b8a308e` (#257) |
| G | `closed` 전송 경로 | 채널이 `notify("closed", data)` 한 뒤 `closeAll(partyId)` 가 `complete()` (`284a950:PartySseChannel.java:21-25`) | `closeAll(partyId, data)` 가 `closed` 를 **직접 전송 후** complete (`PartySseChannel.java:30-32`, `PartySseRegistry.java:70-85`) | `2406342`(#250) + `3065da0`(#250) |
| H | **heartbeat 이벤트** | 없음 | **없음** (←#251 이 넣었다가 #257 이 되돌림) | `784bda5` 추가 → `b8a308e` 삭제 |
| I | `:ping` 주석 주기 | 30초 | 30초 (동일) | — |
| J | 재연결 타임아웃 문구 | 「**90초** 동안 아무것도 안 오면 재연결」 | 「**45초** … 3초 뒤 재연결」 (`PartySseController.java:33`) | `784bda5`/`b8a308e` |
| K | **version gap/stale 규칙** | **없음** | 신설 (`PartySseController.java:30-32`) | `784bda5`(#251) · 문구 `774deb9` |
| L | **이벤트 순서 명시** | 없음 (「연결 직후 connected」만) | 「변경분이 connected 보다 먼저 올 수 있다」 + 「connected 전 변경분은 틈」 (`:26`, `:31`) | `774deb9`(#251) |
| M | `member-joined`/`member-left` 의 "이미 있는/없는 publicId" 규칙 | **상시 규칙** (`284a950:PartySseController.java:7-8`) | **version 이 없는 응답(이전 서버)일 때만의 폴백**으로 격하 (`:32`) | `774deb9`(#251) |
| N | `PartySseRegistry` 동시성 | `computeIfAbsent`+`add`, `remove` 가 비원자적, `closeMember` 가 `complete()` 직접 호출 | `compute`/`computeIfPresent` 원자화, `completeQuietly`, 메트릭 | `2406342`·`3065da0`(#250) |
| O | `finish` 권한 | 참여자 누구나 | **방장만** (`Party.java:241` → `NOT_PARTY_HOST`), Swagger 403 에 추가 | `#237` 계열 |
| P | 잠금 조회 | `findById` 기반 `hasMemberOnParty` | `existsById`+`hasMember` 로 분리, 잠금 대기 후 선행 커밋 가시성 PG 테스트 고정 | `2406342`·`3065da0`(#250) |

**중간 상태 주의**: `#251`(`784bda5`)이 `heartbeat` 이벤트 + `PartySseHeartbeat` + `Parties.findVersions` + `PartyVersion` + `PartySseRegistry.sendHeartbeat/partyIds` 를 넣었다. `#257`(`b8a308e`)이 **그 전부를 되돌렸다**. **`version` 필드는 되돌리지 않았다.** 즉 최종 계약 = `version` 있음 + heartbeat 이벤트 없음.

---

## 3. 앱이 깨지는가

앱의 SSE 구독자는 **딱 하나**다: `MatchWaitingViewModel`(화면 21). 저장소 경유는
`data/src/main/kotlin/com/moyeota/data/repository/RemoteRideRepository.kt:61`.
25 배차 현황·26 운행은 폴링이고 SSE 를 구독하지 않는다(`grep PartyEventSource|\.events\(` 전체 히트 3건, 모두 21 경로).

### 판정 요약

| # | 변경 | 판정 |
|---|---|---|
| A | `connected` 에 `version` | 파싱 **무해** / 규칙 미구현은 **I-1 로 집계** |
| B | `MemberInfo.host` | **무해** (앱이 이미 구현) |
| C | `member-joined` 에 `version` | 파싱 **무해** / → I-1 |
| D | `member-left` 에 `hostPublicId`·`version` | **무해** (`hostPublicId` 이미 구현, 계약도 변경 없음) |
| E | `closed` data 가 JSON 으로 | **무해** (앱이 data 를 아예 읽지 않는다). 다만 **주석·테스트가 낡았다** |
| F | `closed` 발행 조건 2곳 추가 | **무해** (해당 시점에 21 은 이미 구독 중이 아니다) |
| G | `closed` 전송 경로 교체 | **무해** (와이어에 나가는 이벤트 이름·시점이 동일) |
| H/I | heartbeat 없음 + `:ping` 30초 | **무해** (앱은 heartbeat 를 쓰지 않았고 `:ping`/45초 전제가 그대로 맞다) |
| J | 90초 → 45초 | **무해** (앱 `SSE_READ_TIMEOUT_SECONDS = 45L` 로 이미 일치) |
| K+L+M | version gap/stale 규칙 + 순서 역전 명시 | **조용히 틀려짐 — 조치 필요** (I-1) |
| N/P | 레지스트리·잠금 동시성 | **무해** (서버 내부) |
| O | `finish` 방장 전용 | **무해** (앱이 이미 방장에게만 노출) |

---

### 3.1 무해한 것들 — 근거

**B · D (`host`, `hostPublicId`) — 앱이 이미 맞다. 그리고 계약은 이번에 바뀌지 않았다.**

지시문이 「`withHostSuccession` 이 옮긴 계약이 이번에 바뀌었는지 반드시 확인하라」고 한 부분이다. **바뀌지 않았다.**

현재 Swagger(`PartySseController.java:28`):
> `member-left`: { publicId, status, hostPublicId, version }. hostPublicId 는 이 이벤트를 반영한 뒤의 방장 - 받을 때마다 members 의 host 표시를 이 값으로 다시 지정한다. **null 이거나 목록에 없는 publicId 면 방 상세를 다시 받는다**

앱 구현(`presentation/.../MatchWaitingRoute.kt:639-645`)이 그대로다 — null/빈값/목록에 없음 → `RideDelta(this, needsDetail = true)`(표시 손대지 않고 상세 재조회), 있음 → 그 한 명만 `isHost = true`. 서버가 요약을 못 찾아 `hostPublicId = null` 을 싣는 경로도 그대로 있다(`PartyService.java:145-146`). `MemberInfo.host` 도 앱 DTO 에 `val host: Boolean? = null` 로 이미 있다(`PartyDtos.kt:129`) — nullable 이라 서버가 안 보내도 안전.

**E (`closed` payload) — 앱이 data 를 읽지 않아서 살았다.**

`data/src/main/kotlin/com/moyeota/data/remote/matching/PartySseEvent.kt:69`
```kotlin
EVENT_CLOSED -> PartySseFrame.Received(PartySseEvent.Closed)   // data 를 아예 보지 않는다
```
`"42"` 든 `{"partyId":42,...}` 든 결과가 같다. 그리고 애초에 와이어 모양만 봐도 baseline 에서도 `closed` 이벤트는 실제로 나갔다(`284a950:PartySseChannel.java:21` 의 `notify` 가 `closeAll` 보다 **먼저** 돌았다) — 즉 앱의 `Closed` 경로는 죽은 코드가 아니었고, 지금도 똑같이 동작한다.

다만 **문서·테스트가 낡았다**(동작은 멀쩡, 다음 사람이 속는다):
- `data/src/main/kotlin/com/moyeota/data/remote/matching/PartySseEvent.kt:36` — 「data 는 partyId 숫자 문자열이라 실을 값이 없다」
- `data/src/main/kotlin/com/moyeota/data/remote/matching/PartyEventSource.kt:30` — 「`closed` — data 가 JSON 이 아니라 **partyId 숫자 문자열**이다」
- `domain/src/main/kotlin/com/moyeota/domain/model/PartyEvent.kt:69` — 같은 문구
- `data/src/test/kotlin/com/moyeota/data/remote/matching/PartySseEventTest.kt:136-142` — 테스트명이 「`closed` 의 data 는 숫자 문자열이지만…」이고 `parse(EVENT_CLOSED, "42")` 로 고정. **통과는 계속 하지만(data 를 안 보니까) 서술이 거짓이 됐다.** `parse(EVENT_CLOSED, """{"partyId":42,"status":"FINISHED","version":7}""")` 케이스를 추가하면 계약을 고정할 수 있다.

**F (`closed` 발행 2곳 추가) — 그 시점에 21 은 구독 중이 아니다.**

- 배차 실패: `MatchingSweeper.java:56`. 방이 `MATCHING` 단계에 들어간 뒤에 온다.
- 운행 완료: `RideService.java:51`. `IN_RIDE` 뒤.

21 은 status 가 `DISPATCHING`/`ONGOING` 으로 보이는 순간 화면을 떠난다(`MatchWaitingRoute.kt:910-916`), 그리고 떠나면 `LifecycleStartEffect` 의 `onStopOrDispose → onScreenStop()` 으로 `visible = false` 가 되어 `collectLatest` 가 수집을 취소한다(`MatchWaitingRoute.kt:889-892`, `:236-253`). 그래서 이 두 `closed` 를 21 이 받을 일이 사실상 없다.

받는 희귀 경로가 있어도 동작이 옳다: `applyEvent` 의 `PartyEvent.Closed → _partyClosed = true`(`:294-297`) → Route 의 `LaunchedEffect(closed) { onPartyClosed() }`(`:902-904`) → 홈. 방이 닫힌 게 사실이므로 맞는 처리다. 폴백 루프도 `partyClosed` 로 멈춘다(`:560`).

**H · I · J (heartbeat / `:ping` / 45초) — 앱 전제가 최종 상태와 정확히 일치한다.**

- 앱은 heartbeat **이벤트**를 쓰지 않는다. `#251` 이 넣었던 `heartbeat` 이벤트가 살아 있었어도 `parsePartySseFrame` 의 `else -> PartySseFrame.Skipped`(`PartySseEvent.kt:71`)로 조용히 흘렸을 것이다. 지금은 아예 오지 않는다. **앱에 할 일 없음.**
- `:ping` 주석은 okhttp-sse 가 삼키므로 앱이 보지 않고, 쓰임은 **readTimeout 을 깨우는 것** 하나다. 앱 `NetworkModule.SSE_READ_TIMEOUT_SECONDS = 45L`(`data/src/main/kotlin/com/moyeota/data/remote/NetworkModule.kt:20`, 적용 `:78-79`)이 Swagger 의 「45초」와 같고, 재연결 지연도 `PartyEventSource.RECONNECT_DELAY_MS = 3_000L`(`PartyEventSource.kt:20`)로 「3초 뒤」와 같다. `#257` 이 `:ping` 30초를 그대로 둔 덕에 45초 전제가 유지된다.

**A · C (`version` 필드 추가) — 파싱은 안 깨진다.**

SSE 디코더 `Json { ignoreUnknownKeys = true }`(`PartySseEvent.kt:57`), REST 디코더도 같은 정책(`NetworkModule.kt:24`). `version` 은 앱 DTO 어디에도 없고(`PartyDtos.kt` 전체에 `version` 토큰 0건) 조용히 버려진다. **크래시·파싱 실패 없음.** 문제는 "쓰지 않는다"는 쪽이고 그건 아래 I-1 이다.

**O (`finish` 방장 전용)** — 범위 밖이지만 확인했다. 앱은 이미 방장에게만 「합승 완료」를 노출한다(`presentation/.../MatchWaitingScreen.kt:139`, `:187`, 근거 주석에 Backend #237 명시). 정렬돼 있다.

---

### 3.2 **조용히 틀려짐 — I-1: `version` 미구현 + 순서 역전**

> 이번 변경에서 앱이 실제로 손봐야 하는 **단 하나**다.

#### 무엇이 어긋났나

앱 소스에 **서버 계약이 방금 명시적으로 부정한 전제**가 적혀 있다.

`presentation/src/main/kotlin/com/moyeota/presentation/feature/matching/MatchWaitingRoute.kt:228-229`
```
 * 받는 순서는 늘 [PartyEvent.Snapshot] → 변경분이고 **재연결 때도 스냅샷이 먼저** 온다. 그래서
 * 끊긴 사이 놓친 변화를 메우는 catch-up 조회가 없다 — 스냅샷이 방을 통째로 덮는다.
```
서버(`PartySseController.java:26`, `:31`): 「연결 중에 방이 바뀌면 변경분이 connected 보다 먼저 올 수 있다」 / 「connected 전에 온 변경분은 틈으로 보고 상세를 다시 받는다」.

#### 어떻게 틀려지나 (구체 시나리오)

```
t0  subscribe 가 emitter 를 byParty 에 등록                       PartySseRegistry.java:36-40
t1  snapshot.get() = getPartyDetail 트랜잭션이 version N 을 읽기 시작  PartySseService.java:25 / PartyService.java:154-161
t2  다른 사람의 join 이 커밋(version N+1), afterCommit → Redis 발행   PartyService.java:115-121
t3  Redis 리스너 스레드가 notify() → member-joined(N+1) 을 이 emitter 로 먼저 씀  PartySseRegistry.java:58-68
t4  subscribe 스레드가 connected(version N) 을 뒤늦게 씀            PartySseRegistry.java:47
```

앱 처리:
1. `member-joined(N+1)` → `applyDelta`(`MatchWaitingRoute.kt:279-280`, `:312-322`). 바탕이 있으면 그 위에 반영, 없으면 `readDetail()`(N+1 을 읽어 옴).
2. `connected(N)` → `applyEvent` 의 `is PartyEvent.Snapshot` 가 **조건 없이** `_uiState.value = UiState.Success(event.ride)` 로 **통째로 덮는다**(`MatchWaitingRoute.kt:272-276`). → **N+1 의 변화가 사라진다.**

복구 수단이 없다:
- `realtimeAlive` 는 스냅샷을 받아 `true`(`:273`) → `shouldPollFallback(realtimeAlive, hasRide) = !realtimeAlive || !hasRide`(`:493-494`)가 false → **폴백 폴링이 한 번도 안 돈다**.
- 스트림은 살아 있으니 45초 readTimeout 도 안 터지고 재연결 스냅샷도 없다.
- `hasMemberDrift`(`:694-700`)는 **정원 모순만** 본다: 이 경우 status 도 N 시점 값(`RECRUITING`)이고 목록도 N 시점(짧음)이라 **모순이 없다 → 못 잡는다.**
- `needsDetailAfterDelta`(`:673-674`)도 `member-left` 의 방장 지목 실패만 본다.

**최악의 결과**: 잃어버린 `member-joined` 가 정원을 채운 그 사람이면, 서버는 이미 `COMPLETED → MATCHING` 으로 배차를 시작했는데 앱 화면은 `RECRUITING` + 모자란 목록으로 **21 에 머문다**. Route 의 화면 전환은 `state.ride.status` 하나에 걸려 있다(`:909-916`). 자가 복구는 **30분 만료(expire) 의 `closed` 뿐**이다.

창의 폭은 DB 조회 1회 길이(방+멤버요약+탑승횟수 3 쿼리)다. 짧지 않고, 합류 동시성이 있는 인기 방에서 재현된다. 백엔드가 `version` 과 `SseResyncRate` 경고를 넣은 이유가 이것이다(`infra/monitoring/rules/alerts.yml`, `loadtest/lib/sse.js:77-115`).

**이 레이스 자체는 baseline 에도 있었다** — `284a950` 의 `subscribe` 도 등록 후 connected 전송 순서였다(`git diff` 확인). 즉 **이번 PR 들이 만든 회귀는 아니다.** 바뀐 것은 두 가지고, 둘 다 앱 쪽 조치를 요구한다:
1. 서버가 이 역전을 **계약으로 공표**했다(`:26`, `:31`) — 앱의 `:228-229` 주석은 이제 **틀린 서술**이다.
2. 서버가 **고칠 수단(`version`)을 줬다.** 그리고 기존의 "이미 있는 publicId / 없는 publicId" 완화 규칙을 **`version` 없는 옛 서버용 폴백으로 격하**시켰다(`:32`, 표 M) — 지금 앱이 그 폴백 쪽에 머물러 있다.

#### 고쳐야 하는 곳 (파일:라인)

**(a) data 계층 — `version` 을 받는다**

- `data/src/main/kotlin/com/moyeota/data/remote/dto/PartyDtos.kt:43-72` `PartyDetailResponse` 에 `val version: Long? = null` 추가.
  nullable 로 두는 이유는 기존 `host: Boolean?`(`:129`)과 같다 — 「서버가 말하지 않았다」(옛 배포)와 `0`(새 방)을 가를 수 있어야 하고, `version == null` 이면 계약의 옛-서버 폴백 분기로 떨어뜨린다.
- 같은 파일 `:148-153` `MemberJoinedPayload` 에 `val version: Long? = null`.
- 같은 파일 `:162-178` `MemberLeftPayload` 에 `val version: Long? = null`.
- `closed` 는 **건드리지 않는다** — Swagger 가 「version 과 상관없이 종료」(`PartySseController.java:29`)라 했고 앱은 이미 data 를 안 본다.

**(b) 이벤트 타입에 version 을 싣는다**

- `data/src/main/kotlin/com/moyeota/data/remote/matching/PartySseEvent.kt:23-41` — `Snapshot`/`MemberJoined`/`MemberLeft` 는 payload 를 그대로 들고 있으므로 **추가 변경 불필요**(payload 에 version 이 생기면 따라온다).
- `domain/src/main/kotlin/com/moyeota/domain/model/PartyEvent.kt:32`, `:41`, `:60-64` — `Snapshot(ride, version: Long?)`, `MemberJoined(member, status, version: Long?)`, `MemberLeft(publicId, status, hostPublicId, version: Long?)`.
  `Ride` 에 넣지 말 것 — `version` 은 방의 성질이 아니라 **프레임의 순서 표식**이고, `Ride` 에 넣으면 `Ride` 끼리 `==` 로 「바뀌었나」를 보는 25·26 비교가 version 때문에 매번 달라진다(`RideDelta` 를 `Ride` 밖에 둔 것과 같은 이유 — `MatchWaitingRoute.kt:654-657`).
  단 `readDetail()` 결과도 version 을 알아야 하므로 `RideRepository.getPartyDetail` 반환에도 version 을 실을 자리가 필요하다. 가장 적은 변경은 `Ride` 가 아닌 `PartyDetail(ride, version)` 래퍼이거나, 저장소에 `getPartyDetailVersion` 을 따로 두지 않고 `Snapshot` 과 같은 타입을 재사용하는 것 — **설계 선택은 compose-builder 와 합의 필요**(§4 참조).
- `data/src/main/kotlin/com/moyeota/data/remote/PartyMappers.kt:176-188` `toPartyEvent` 에 version 전달.

**(c) ViewModel — gap/stale 판정**

`presentation/src/main/kotlin/com/moyeota/presentation/feature/matching/MatchWaitingRoute.kt`

- `:159` 근처에 `@Volatile private var heldVersion: Long? = null` 추가 (`appliedEvents` 옆).
- `:272-276` `is PartyEvent.Snapshot` — 무조건 교체를 **조건부**로:
  `heldVersion == null || event.version == null || event.version >= heldVersion` 일 때만 교체하고 `heldVersion = event.version`. 낮으면 **버린다**(`realtimeAlive = true` 는 세워도 된다 — 연결은 살아 있다).
- `:279-280` `MemberJoined` / `:281-293` `MemberLeft` 공통 전처리:
  - `heldVersion == null` (= connected 전) → **`readDetail()`** (계약 `:31` 의 「틈」)
  - `v == null || heldVersion == null` → 현행 동작 유지(옛 서버 폴백, 계약 `:32`)
  - `v <= heldVersion` → **버린다**
  - `v == heldVersion + 1` → 현행대로 반영하고 `heldVersion = v`
  - `v > heldVersion + 1` → **`readDetail()`** (스트림은 끊지 않는다)
- `:312-322` `applyDelta` — version 전달을 위해 시그니처에 `version: Long?` 를 추가하거나 호출부에서 판정 후 넘긴다.
- `:333-337` `applyStatus`(publicId null 경로) — 이 경로는 곧바로 `readDetail()` 을 부르므로(`:289`) 추가 판정 불필요. `heldVersion` 갱신만 `readDetail` 쪽에 맡긴다.
- `:350-355` `readDetail` — 읽어 온 상세의 version 으로 `heldVersion` 을 갱신하고, 계약의 「다시 받은 상세는 내 version 이 없거나 그 이상일 때만 교체」를 적용. 기존 `appliedEvents == mark` 가드(`:353`)는 **그대로 둔다** — 늦게 도착한 조회가 더 새 실시간 상태를 되돌리는 걸 막는 별개의 장치다.
- `:298` `Disconnected` — `heldVersion` 을 **지우지 말 것**. 재연결 `connected` 의 version 이 들고 있던 것보다 낮을 때 버리려면 필요하다(계약 `:31`).
- `:294-297` `Closed` — 손대지 않는다(version 무관).
- `:228-229` — **주석을 정정한다.** 「늘 Snapshot → 변경분」은 거짓이다. 계약 `PartySseController.java:26` 인용으로 바꾸고, `version` 으로 어떻게 메우는지 적는다.

**(d) 테스트** (`data/src/test`, `presentation/src/test`)

- `data/src/test/kotlin/com/moyeota/data/remote/matching/PartySseEventTest.kt:136-142` — `closed` JSON 케이스 추가, 테스트명 정정.
- `PartySseEventTest` 에 `member-joined`/`member-left`/`connected` 의 `version` 파싱 + **version 없는 프레임**(옛 서버) 케이스.
- 신규(순수 함수로 뽑으면 Robolectric 없이 가능): connected 전 델타 → 상세 재조회, `v <= held` → 버림, `v > held+1` → 재조회, `connected` 의 version 이 낮으면 버림. 기존 `WaitingVisibilityGateTest`·`PartyDetailPollingTest` 와 같은 자리.

**(e) 참고로 함께 정정할 낡은 서술** (동작 영향 없음)

- `data/src/main/kotlin/com/moyeota/data/remote/matching/PartyEventSource.kt:28-30` — `member-left` 를 `{publicId, status}` 로, `closed` 를 숫자 문자열로 적어 둠. 둘 다 옛 모양.
- `domain/src/main/kotlin/com/moyeota/domain/model/PartyEvent.kt:20-23`, `:69` — 「`GET /rooms/{id}/status` 의 지문 비교」는 그 엔드포인트가 사라진 뒤 죽은 서술이고, `closed` data 설명도 옛 모양.
- `MatchWaitingRoute.kt:612-615` 의 Swagger 인용 — 현재 원문은 `{ publicId, status, hostPublicId, version }` 이다. 인용을 현행으로.

---

## 4. 확신 못 하는 것 (미확인 — 추측하지 않음)

1. **실서버 미검증.** 서버를 띄우지 않았다(지시대로). prod 는 배포 중 503. 이 리포트는 **전부 `origin/develop` 소스 기준**이다. 특히 (a) `closed` 의 실제 와이어 JSON 키 이름, (b) `connected` JSON 의 `version` 키 이름, (c) `MemberLeft.hostPublicId` 의 직렬화 이름은 Jackson 기본 동작(record 컴포넌트명 그대로)을 **가정**했다. 운영 OpenAPI 로 교차 확인 필요 — `host` 가 `isHost` 가 아님을 과거에 운영 OpenAPI 로 확인한 선례가 있다(`PartyDtos.kt:119-121`).
2. **순서 역전의 실측 빈도.** 레이스가 소스상 성립함은 확인했지만(`PartySseRegistry.java:36-47`), 실제 발생률은 측정하지 않았다. 백엔드 `loadtest/results/d2-sse-4000-r8181.*` 와 `SseResyncRate` 지표에 `sse_resync{reason=gap}` 수치가 있을 것으로 보이지만 **읽지 않았다.** 조치 우선순위를 정하려면 그 숫자를 보는 게 맞다.
3. **`version` 을 도메인에 어떻게 실을지.** §3.2 (b)에서 `Ride` 에 넣지 말아야 할 이유는 확실하지만, `PartyDetail(ride, version)` 래퍼 / `RideRepository` 시그니처 변경 / 그 밖의 선택지 중 무엇이 25·26 과 `ActivePartyViewModel` 에 영향이 적은지는 **확정하지 않았다.** `getPartyDetail` 의 반환형이 바뀌면 `RideRepository` 를 쓰는 다른 화면이 전부 영향을 받으므로 compose-builder 와 합의가 필요하다.
4. **`closed` 의 `status` 를 화면이 써야 하는지.** 지금 앱은 `closed` 를 사유 구분 없이 홈 이동으로 처리한다(`MatchWaitingRoute.kt:86-94`). 신규 발행처 중 **배차 실패(CANCELED)** 와 **운행 완료(FINISHED)** 는 UX 상 다른 안내가 맞을 수 있으나, 그 시점에 21 이 구독 중이 아니므로(§3.1 F) **지금은 영향 없음**으로 판정했다. 25·26 이 나중에 SSE 를 구독하게 되면 다시 봐야 한다.
5. **`PartyClosedEvent`(스프링 내부 이벤트, `PartyService.java:140`,`195`,`209`) 가 FCM 등 다른 통지로 이어지는지** — 추적하지 않았다. 배차 실패/운행 완료는 `PartyAccessService.notifyClosed` 가 SSE 만 보내고 `PartyClosedEvent` 는 발행하지 않는다(`:142-145`)는 **비대칭**이 보이는데, 앱 영향 여부(푸시가 안 가는지)는 범위 밖이라 확인하지 않았다. 리더 판단 필요.
6. **`finishWithoutDriver` 의 `NOT_PARTY_HOST`(403) 를 앱 에러 문구가 가르는지** — 버튼이 방장에게만 보이므로 정상 흐름에서는 안 나지만, `PartyFinishException`/`finishFailureMessage`(`MatchWaitingRoute.kt:438-444`)가 이 코드를 따로 다루는지는 끝까지 읽지 않았다. 범위 밖.
7. **`PartyAccessService` 가 package-private 클래스인데 `AfterCommitExecutor`·`PartyChangeNotifier` 를 새로 주입한 것이 다른 모듈의 빈 구성에 영향을 주는지** — 서버 내부 문제이고 앱 영향이 없어 보지 않았다.
