# 백엔드 변동 반영 — 계약 대조 (2026-11-12)

기준: 백엔드 `SWM-Moyeota/Backend` 로컬 `refactor/ride-record-pickup-time` (`3eaa2895`, 2026-11-12)
대조 대상: 프론트 `develop` (`ac2e3dd`, 2026-11-06)

프론트가 마지막으로 맞춘 날(11-06) 이후 백엔드가 **방 상태 정의를 다시 썼고, 노쇼를 지웠고,
`/finish` 를 없앴고, 확정에 만남 장소를 필수로 만들었고, 택시비 정산 모듈을 새로 붙였다.**

---

## A. 지금 당장 깨져 있는 것 (호출하면 4xx)

| # | 프론트가 하는 일 | 서버 현재 | 결과 |
|---|---|---|---|
| A1 | `POST /matching/rooms/{id}/confirm` 에 `{departureAt}` 만 보낸다 | `ConfirmPartyRequest` 가 `departureAt`·`departureLat`·`departureLng`·`departure` **전부 `@NotNull`/`@NotBlank`** | **400 INVALID_REQUEST — 약속 확정이 아예 안 된다** |
| A2 | `POST /matching/rooms/{id}/finish` (「합승 완료」) | **엔드포인트 삭제** (`b16551b7`: 구버전 종료 경로(/finish) 지운다) | 404. 방을 끝낼 길이 `depart` 뿐 |
| A3 | `GET /matching/no-shows` | **삭제** (노쇼 기능 전체 제거, V37) | 404 |
| A4 | `POST /matching/rooms/{id}/no-show-objection` | **삭제** | 404 |
| A5 | `GET /matching/rooms` (좌표 없는 전체 목록) | **삭제** | 400 (swLat 등 필수) |
| A6 | `GET /matching/reservations?from&to` | **삭제** (`INVALID_TIME_RANGE` 도 같이 삭제) | 404 |

## B. 조용히 어긋난 것 (에러는 안 나고 화면이 거짓말을 한다)

### B1 🔴 `PartyStatus.COMPLETED` 의 뜻이 바뀌었다

```
이전: COMPLETED = 정원이 찼다
지금: COMPLETED = 실시간 방 방장이 약속을 확정했다
      → 정원이 차도 ACTIVE 다. 참여만 PARTY_FULL 로 막는다 (Party.join)
```

프론트는 `"COMPLETED" -> RideStatus.MATCHED` 로 받고, `MATCHED` 를 **「정원이 찼다」로 읽는
자리가 9곳**이다. 전부 뜻이 뒤집혔다:

| 파일:라인 | 지금 코드 | 틀린 결과 |
|---|---|---|
| `MatchWaitingRoute.kt:864` | `isMatchFull(status) = status == MATCHED` | 2/3 확정 방을 「정원 찼다」로 집계(`complete_match` 이벤트가 거짓) |
| `MatchWaitingRoute.kt:1258` | `isFinishStage(status) = status == MATCHED` | 확정 방에 「합승 완료」(=삭제된 API)를 띄운다 |
| `MatchWaitingRoute.kt:1214` | `MATCHED -> members.size < capacity` | — |
| `ActiveParty.kt:70` | `MATCHED -> "같이 탈 사람이 모였어요"` | 2/3 확정 방에 「다 모였어요」 |
| `RideDetailScreen.kt:518` | `MATCHED -> "정원이 다 찼어요…"` | 같은 거짓말 |
| `JoinConfirmScreen.kt:429` | `MATCHED -> "모집 마감된 방"` | 틀리진 않다(확정 방은 참여 불가) — 문구만 어긋남 |
| `MatchWaitingScreen.kt:119` 외 KDoc | 「정원이 찬 뒤(= 서버 COMPLETED)」 | 주석이 거짓 |

**정원 판정의 유일한 참은 `members.size >= capacity` 다.** 상태값으로 알 수 없다.

### B2 `confirmed` SSE 가 만남 장소를 함께 싣는다

```java
PartyConfirmed(status, departureAt, departureLat, departureLng, departure, version)
```

확정은 **방의 출발지를 만남 장소로 옮긴다**(`Party.confirm`: `departureLocation = plan.departure()`).
앱이 이 셋을 무시하면 확정 뒤에도 옛 출발지를 그리고, 도착 판정(반경) 기준과 화면이 어긋난다.

### B3 `recruiting-closed` 이벤트 삭제

서버가 보내는 이벤트는 여섯 종이다: `connected` · `member-joined` · `member-left` ·
`confirmed` · `member-arrived` · `closed`. 프론트는 일곱 종을 알고 있다.

### B4 응답에서 사라진 키

`taxiDriverId` · `departureRadius` · `destinationRadius` · `members[].badgeId` ·
`pickupTime`(방 상세) / 요청의 `creatorId`.

### B5 도착 반경이 방 종류로 갈린다

```java
REALTIME_ARRIVAL_RADIUS_METERS    = 100
RESERVATION_ARRIVAL_RADIUS_METERS = 400   // 예약 방은 출발지가 역 중심점이고 만남 장소를 옮길 수 없다
```

프론트 문구·KDoc 은 100m 하나로만 말한다.

### B6 나가기에 새 거절 사유

`ARRIVED_MEMBER_CANNOT_LEAVE` (409) — 출발지 도착을 누른 뒤에는 나갈 수 없다
(정산 대상에서 빠지면 안 되기 때문). 확정 방에서 **나가기 자체는 여전히 된다.**

### B7 목록에서 정원이 찬 방이 빠진다

근처 방·지도·예약 목록 모두 `isFull` 을 뺀다. 「모집 마감된 방」 배지가 목록 경로로는 도달 불가.

### B8 탑승 기록의 요금이 택시비 정산에서 온다

`totalFare` · `farePerPerson` — 정산이 없거나 내가 정산 당사자가 아니면 **둘 다 null**
(함께 채우거나 함께 비운다). 프론트는 이미 nullable 로 받는지 확인 필요.

### B9 채팅방 직접 참여가 합승 멤버로 제한됐다

`POST /chat-rooms/{id}/users` — 매칭 동기화 참여는 그대로, 사용자 직접 참여는 멤버만.

---

## C. 새 기능 — 택시비 정산 (`faresplit` 모듈, 엔드포인트 7개)

동승이 끝난(FINISHED) 방에서 **방장이 택시비를 다 내고**, 사람마다 부담 금액을 적은 장부를
만들면, 함께 탄 나머지가 토스·은행 앱으로 각자 보낸다. **앱은 돈을 옮기지 않는다.**

| 메서드 | 경로 | 누가 |
|---|---|---|
| POST | `/matching/rooms/{partyId}/fare-split` | 방장(=PAYER). 201 |
| GET | `/matching/rooms/{partyId}/fare-split` | 당사자만 |
| PATCH | `/matching/rooms/{partyId}/fare-split` | PAYER. 전원 NOT_SENT 일 때만 |
| POST | `…/fare-split/sent` | SENDER. 204, 멱등 |
| POST | `…/fare-split/transfers/{publicId}/received` | PAYER. 204, 멱등 |
| POST | `…/fare-split/transfers/{publicId}/not-received` | PAYER. SENT → NOT_SENT |
| GET | `/users/me/fare-splits?status=NOT_SENT` | 홈 배너용 |

**앱이 책임지는 것(서버 Swagger 가 명시한다):**
- `payments` 1/N 미리 채우기 — 「1/N 로 미리 채우는 것은 앱이 한다」
- 홈 배너 — 「아직 보내지 않은 택시비」
- 계좌·금액 **복사 버튼** — 「비공식 규격이라 열리지 않을 수 있어 계좌·금액 복사 버튼을 함께 둔다」

**규칙:**
- 시작 조건: FINISHED 방 + 방장이 탑승자(도착 누름) + 탑승자 2명 이상
- `payments` 는 **방장 포함 탑승자 전원이 한 번씩**, 합 == `totalFare`
- 계좌번호: 하이픈·공백 떼고 숫자 10~16자리
- 상태: `NOT_SENT → SENT → CONFIRMED`, 전원 CONFIRMED 면 정산 `COMPLETED` + 계좌번호 삭제
- 만든 지 1일 지나면 `EXPIRED`
- 송금 재요청 알림: 4시간마다, 첫 요청 포함 6번
- `tossDeeplink`: `supertoss://send?amount=&bank=&accountNo=` — SENDER + OPEN 일 때만
- `maskedRealName`(홍*동): PAYER 가 볼 때만 — 은행 앱 입금자명 대조용

은행 22종(`Bank`), 에러코드 18종(`FareSplitErrorCode`).

---

## 새 상태 기계 (이게 반영의 기준이다)

```
ACTIVE ──방장 confirm(약속시간 5분~1시간 + 만남장소)──▶ COMPLETED
  │                                                      │
  │ 정원이 차도 ACTIVE. join 만 PARTY_FULL                 │ 약속 10분 전부터 arrive(반경 안)
  │ 혼자인 방은 15분 뒤 모집 만료로 CANCELED                │
  │                                                      ▼
  └──────────────────────────────────────────▶ 도착한 방장이 depart ──▶ FINISHED
                                                                         │
                                                        ┌────────────────┘
                                                        ▼
                                              방장이 택시비 정산 시작
```

- **예약 방은 처음부터 `confirmed = true`** (`isConfirmed() = isReservation() || departureAt != null`)
  이고 상태는 ACTIVE 다. `confirm` 은 409 `NOT_REALTIME_PARTY`.
- `depart` 조건: 방장 · 확정 · 2명 이상 · `now >= departureAt` · **방장이 도착했음**(`NOT_ARRIVED`)
- 「합승 완료」는 없다. 방을 끝내는 버튼은 **「출발」** 하나다.

---

# 작업 진행 (2026-11-12)

## 반영 완료 — data · domain 계층

| # | 한 일 |
|---|---|
| A1 | `confirmMeeting` 본문 1필드 → **4필드**(`departureAt`+만남 장소 위도·경도·이름). 저장소 시그니처도 넷으로 |
| A2 | `/finish` 제거 — `MatchingApi.finishParty` · `RideRepository.finishParty` · `PartyFinishException` · `partyFinishException` 매퍼 전부 삭제 |
| A3·A4 | 노쇼 전면 제거 — 전용 파일 13개 삭제(2,422줄), 공용 파일 14개 수술 |
| A5 | 좌표 없는 전체 목록 `getParties()` 삭제 |
| A6 | 기간 예약 목록 `getReservations(from, to)` 삭제 |
| B1 | **`RideStatus.MATCHED` → `RideStatus.CONFIRMED`** 개명 + enum 값마다 KDoc. 「정원이 찼다」를 뜻하는 상태값은 **없다**는 걸 못 박음 |
| B2 | SSE `confirmed` 에 `departureLat`·`departureLng`·`departurePlace` 추가. 매퍼가 **셋이 다 왔을 때만** 올린다(일부만 덮어쓰면 도착 판정이 옛 좌표로 돈다) |
| B3 | `recruiting-closed` 이벤트·payload·도메인 타입 삭제 |
| B4 | 응답에서 사라진 키 제거 — `badgeId` · `departureRadius` · `destinationRadius` |
| B5 | 도착 반경이 방 종류로 갈린다(실시간 100m / 예약 400m)는 사실을 KDoc·문구 근거로 기록 |
| B6 | 나가기 거절 사유 교체 — `PARTY_NOT_RECRUITING`·`CANNOT_LEAVE_CONFIRMED_PARTY` → **`PARTY_CLOSED`·`ARRIVED_MEMBER_CANNOT_LEAVE`** |
| B8 | 탑승 기록 요금이 택시비 정산에서 온다는 사실 반영 |
| — | `PartyDepartException.isNotHost` **신설**(출발이 방장 전용이 됐다) |
| — | `PartyConfirmException`: `PARTY_NOT_RECRUITING` → `PARTY_CLOSED`, 약속 시간 범위 5분~1시간(하한 신설) |
| C | **택시비 정산 연동 신설** — API·DTO·매퍼·저장소·도메인 모델·예외 18종·`FareSplitPolicy`(1/N·계좌 정규화·사전 검증), `AppContainer` 배선 |

## 🔴 작업 중 찾은 실제 결함 (서버 변경이 조용히 깨뜨린 것)

### 1. 탑승 기록의 날짜가 전부 빈칸이 된다
```kotlin
// RideHistoryDisplay.kt:79
dateLabel = dropoffTime?.let { rideHistoryDateLabel(it, now, zone) }
```
서버가 하차 시각을 없애고 **승차 시각 하나로 바로잡았다**(`135df701`). `ignoreUnknownKeys` 때문에
**없는 키는 조용히 null** 이 되므로 파싱은 멀쩡하고 날짜만 사라진다 — 에러가 아니라 빈칸이라
알아채기 어렵다. 모델에서 `dropoffTime` 을 지워 **컴파일로 잡게** 했다.

### 2. 약속 확정이 아예 안 된다
옛 한 필드 본문(`{departureAt}`)은 이제 400 `INVALID_REQUEST` 다. 그 코드는 `MatchingErrorCode` 에
없어서 화면에 **일반 문구로만** 보인다 — 「확정이 안 돼요」라는 신고에서 원인을 짚을 단서가 없다.

### 3. 확정·예약 방에서 나갈 길이 없다
앱이 「확정된 방은 아무도 나갈 수 없다」는 **사라진 규칙**으로 버튼을 숨기고 있었다. 서버가 허용하는
일을 앱이 막는 쪽이라 사용자에게는 복구 수단이 없다.

### 4. 사용자 문구가 지킬 수 없는 약속을 한다
「노쇼로 기록돼요 / 24시간 참여할 수 없어요」 — 제재가 서버에 없다.

## 백엔드에 확인이 필요한 것 (프론트가 손댈 수 없음)

| # | 내용 |
|---|---|
| 1 | `FareSplitRole.java` 에 `// host, guest로 변경` TODO 가 있다. **개명되면 모든 PAYER 가 SENDER 로 읽힌다**(수정·「받았어요」 버튼이 사라진다). 데이터는 안전하지만 기능이 죽는다 — 계획이 있으면 매퍼에 두 이름을 함께 받아 두겠다 |
| 2 | `Bank.java` 에 `// 은행 코드도 추가` TODO. 모르는 코드는 문자열로 들고 다니므로 깨지지 않지만 선택 목록에 안 뜬다 |
| 3 | `backend/http/` 에 정산 요청 예시가 **없다**(`matching`·`chat`·`place` 뿐). 이번 정산 연동은 컨트롤러·record 소스 대조만으로 만들었다 — **실서버 미검증** |
| 4 | 상세 응답에 `myStatus` 가 없다. 보내는 사람이 자기 송금 상태를 직접 받지 못해 `transfers` 에서 세션 uuid 로 찾아야 한다 |

---

# 실서버 실측 (2026-11-12, 로컬 dev + PostGIS)

방 하나를 만들어 **생성 → 합류 → 확정 → 도착 → 출발 → 정산 7개**를 끝까지 돌렸다.
스크립트: `scratchpad/verify_faresplit.py` · 로그: `scratchpad/faresplit_run.log` · partyId 4143.

## 확인된 것 (프론트 DTO 와 응답이 전부 일치)

| 확인 | 결과 |
|---|---|
| 확정 본문 4필드 | **204** |
| 옛 1필드 본문 | 400 `INVALID_REQUEST` — 「departureLat: 만남 장소 위도는 필수입니다…」 |
| 확정이 출발지를 옮기나 | **옮긴다.** `departure` 가 「강남역」→「강남역 2번 출구」, status ACTIVE→COMPLETED, `confirmed: true`, version 1→2 |
| 사라진 응답 키 | 상세에 `badgeId`·`departureRadius`·`destinationRadius`·`taxiDriverId` **없음** |
| 도착 창 | 약속 5분 전에 204(10분 창 안). **3회 연속 204** — 멱등 |
| 도착 뒤 나가기 | 409 `ARRIVED_MEMBER_CANNOT_LEAVE` |
| 약속 전 출발 | 409 `MEETING_NOT_STARTED` |
| **비방장 출발** | **403 `NOT_PARTY_HOST`** — 방장 전용이 맞다 |
| 정산 시작 | 201 + 상세. `myRole`·`myAmount`·`bank{code,name}`·`maskedRealName`(「정**객」) |
| payments 누락 | 400 `INVALID_REQUEST` — 「payments: 부담 금액은 필수입니다」 |
| 합 불일치 / 대상 누락 / 계좌 자릿수 | 400 `PAYMENTS_NOT_EQUAL_FARE` · `PAYMENT_TARGET_MISMATCH` · `INVALID_ACCOUNT_NUMBER` |
| 토스 딥링크 | **SENDER + OPEN 만**. PAYER 는 null |
| 가린 실명 | **PAYER 만**. SENDER 에게는 null |
| 닫힌 정산 | `accountNumber`·`tossDeeplink` 둘 다 **null**, `closedAt` 채워짐 |
| 홈 배너 목록 | 받는 사람으로 시작한 정산은 **안 나온다**(방장 조회 → 빈 배열) |
| `?status=NOPE` | 400 「status: 형식이 올바르지 않습니다.」 |
| **탑승 기록** | `dropoffTime` **없음**, `pickupTime` 만. `farePerPerson` 이 **보는 사람마다 다르다**(방장 8000 / 승객 12000) |

## 🔴 Swagger 와 실제가 어긋난 것 — 코드로 반영했다

**「받았어요」는 끝까지 멱등이 아니다.** Swagger 는 「이미 CONFIRMED 면 아무것도 바꾸지 않고 204」라고
적었지만, 마지막 송금을 확인하면 정산이 COMPLETED 로 닫히고 **그다음부터 409 `FARE_SPLIT_CLOSED`** 다.

```
received 1회 → 204   (확인 + 정산 닫힘)
received 2회 → 409 FARE_SPLIT_CLOSED
```

접지 않으면 204 가 유실된 뒤 다시 누른 받는 사람에게 「이미 끝난 정산입니다」가 뜬다 — **자기가 방금
닫은 정산인데** 실패로 보인다. `RemoteFareSplitRepository.confirmReceived` 가 그 409 만 성공으로 접도록
고치고 테스트로 못 박았다(「보냈어요」는 접지 않는다 — 그쪽의 닫힘 409 는 「내가 보낼 필요가 없어졌다」는
새 정보다).

## 반영한 백엔드 신규 변경

**PR #328 `myTransferStatus`** — 상세 응답에 내 송금 상태가 생겼다(SENDER 만, PAYER 는 null).
`transfers` 에서 자기 publicId 로 자기 행을 찾던 우회를 걷어내고, `FareSplitDetail.myTransferStatus` +
`canMarkSent`(진행 중 · 보내는 사람 · 아직 안 보냄)로 바꿨다. **null 을 NOT_SENT 로 접지 않는다** —
접으면 방장 화면에 「보냈어요」가 떠서 403 `NOT_FARE_SPLIT_SENDER` 가 된다.

## 실측 중 알게 된 로컬 환경 사실

- `application-local.yaml` 이 `access-token-validity: 2m` 다(운영 기본 30분). 긴 시나리오를 돌릴 때는
  중간에 재로그인해야 한다 — 결함이 아니라 토큰 갱신을 일부러 자주 겪게 하는 설정이다.
- 루트에서 `./gradlew bootRun` 을 돌리면 `socket-app` 까지 떠서 **9091 포트가 충돌한다**.
  API 만 띄우려면 `./gradlew :bootRun` 이다.
- `backend/http/faresplit.http` 가 **낡았다**: 생성·수정 본문에 `payments` 가 없고(서버가 1/N 을 계산하던
  시절), 지금 없는 `POST .../fare-split/cancel` 이 4번으로 적혀 있다. 그대로 쓰면 400·404 다.
  → 백엔드 쪽 갱신 필요(프론트가 고칠 수 없다).

## 아직 안 본 백엔드 변경

**PR #314 `feature/find-login-id`**(휴대폰 번호로 로컬 아이디를 찾는 API, 11-06 오픈, 미머지).
앱에 「아이디 찾기」 화면이 없다 — 머지되면 붙일지 결정이 필요하다.

---

# 최종 엔드포인트 대조

프론트가 Retrofit 으로 부르는 **57개 경로가 서버 컨트롤러와 전부 일치**한다(메서드+경로 기준).
서버에만 있고 프론트가 안 부르는 것은 다섯인데 전부 설명이 된다:

| 경로 | 왜 안 부르나 |
|---|---|
| `DELETE /users/me` | 부른다 — Retrofit `@HTTP`(본문 있는 DELETE)라 패턴에 안 걸렸다 |
| `GET /matching/rooms/{}/events` | 부른다 — SSE 라 Retrofit 이 아니라 OkHttp 직접(`PartyEventSource`) |
| `POST·DELETE /chat-rooms/{}/location/sharing`, `POST /chat-rooms/{}/location` | 위치 공유를 **STOMP 로** 한다(`StompChatController.shareLocation`). REST 쪽은 쓰지 않는다 — 새 변경이 아니다 |

## 🟡 확인이 필요한 것 — 채팅 소켓이 분리됐다

백엔드가 `socket-app` 모듈을 새로 만들고 **별도 배포**까지 붙였다(`0cfc9f8d`·`646fb182`·`10ef60cd`).
그런데 **`/ws-chat` 을 메인 API 앱과 socket-app 이 둘 다 등록한다**:

```
src/main/java/.../chat/common/config/WebSocketConfig.java:26      registry.addEndpoint("/ws-chat")
socket-app/src/main/java/.../socket/config/WebSocketConfig.java:29  registry.addEndpoint("/ws-chat")
```

앱은 `BASE_URL` 을 `wss://` 로 바꿔 `/ws-chat` 에 붙으므로(`ChatSocket.kt:273`) **지금은 그대로 동작한다**
— 메인 앱이 아직 그 엔드포인트를 들고 있기 때문이다.

**질문:** 채팅 소켓 트래픽을 socket-app 으로 옮길 계획인가? 옮긴다면 앱에 **소켓 전용 URL**이 하나 더
필요하다(지금은 API URL 하나를 돌려쓴다). 메인 앱의 `WebSocketConfig` 를 걷어내는 시점에 앱이
따라가지 못하면 **채팅이 통째로 끊긴다** — 미리 분리해 두는 편이 안전하다.

---

# 🔴 이번 서버 변경이 **새로 만든** 구멍 — 결정 필요

## 확정된 약속이 말없이 사라진다

실측(partyId 4144):

```
방장 + 승객 2명, 정원 3 → 확정(30분 뒤 강남역 2번 출구)  status=COMPLETED
승객이 나간다                                           → 204   (나갈 수 있다 — 맞다)
방 상태                                                 → CANCELED  (혼자 남은 확정 방은 서버가 닫는다)
방장이 나가기 시도                                       → 409 PARTY_CLOSED (이미 닫혔다)
```

**방장은 아무것도 하지 않았는데 약속이 없어졌다.** 그런데 앱이 그 사실을 말할 수 없다:

| | |
|---|---|
| 서버가 보내는 것 | `closed` **하나뿐**. `PartyService.leave` 는 CANCELED 면 `publishClosed` 만 하고 `member-left` 를 **보내지 않는다** |
| 앱이 `closed` 를 읽는 법 | **data 를 읽지 않는다** — 배포마다 모양이 달랐고(`{status}` / `{partyId,status,version}` / 숫자 문자열 `"42"`) 역직렬화하면 옛 모양에서 터진다. 종결 신호로만 쓴다 |
| 그래서 화면이 하는 일 | `onPartyClosed` → 기억 지우고 **말없이 홈**(`MainNavGraph.kt:847`). 주석도 「사용자는 요약 없이 14 홈으로 간다」로 적혀 있다 |

즉 방장은 **「동승이 끝났다」와 「상대가 나가서 약속이 취소됐다」를 구분할 수 없다.**
그리고 전자는 자기가 「출발」을 눌러서 안다 — **설명이 필요한 쪽은 후자뿐이다.**

최악의 경로: 방장이 약속 시간에 강남역 2번 출구에 나가고, **아무도 오지 않는다.** 앱은 끝까지
아무 말도 하지 않았다.

### 왜 **새** 구멍인가
`b16551b7` 전에는 확정된 방을 **나갈 수 없었다**(409 `CANNOT_LEAVE_CONFIRMED_PARTY`). 그래서 이
경로가 **존재할 수 없었다.** 「요약 없이 홈으로」가 괜찮았던 것은 방이 닫히는 일이 **늘 자기 행동의
결과**였기 때문이다.

### 고치는 법 (제안)
`closed` 를 받으면 **방 상세를 한 번 읽는다**(`GET /matching/rooms/{id}` 에는 멤버 검사가 없어 닫힌
방도 읽힌다 — 실측). 그 `status` 로 문구를 가른다:

| 상세의 status | 문구 |
|---|---|
| `FINISHED` | 지금처럼 말없이 홈(자기가 눌렀다) |
| `CANCELED` | **「같이 타려던 분이 나가서 약속이 취소됐어요」** + 홈 |

조회 1건이 늘고, 새 사용자 문구 하나가 생긴다. **문구가 새로 생기는 일이라 결정을 받고 하겠다.**

---

# 에뮬레이터 실측 (2026-11-12) — 전 구간 통과

로컬 백엔드(`10.0.2.2:8080`, dev 프로필 + PostGIS)에 붙인 디버그 빌드로 **실제 탭해서** 확인했다.

| 확인 | 결과 |
|---|---|
| 2/3 방에 **확정 블록**이 뜨나 | **뜬다** — 「몇 분 뒤에 만날까요?」 + 프리셋 5개 |
| 「합승 완료」가 사라졌나 | **없다** (화면 어디에도) |
| 확정 **4필드 요청**이 통하나 | **204** → status COMPLETED, departureAt 설정, 출발지 유지(앱이 그대로 다시 실었다) |
| 확정 방에 **「방 나가기」**가 있나 | **있다**. 눌러서 **204** — 서버가 받았다 |
| 「도착을 알리면 나갈 수 없어요」 | 뜬다 — 진짜 구속 조건을 말한다 |
| 나가기 다이얼로그 | 「정해진 약속에서 빠지고 이 방에 다시 들어올 수 없어요」 — 재합류 409 `PARTY_CLOSED` 실측과 일치 |
| 마이페이지 「노쇼 기록」 | **사라졌다** |
| 탑승 기록 **날짜** | 「11월 12일 오후 5:25」 — `pickupTime` 으로 나온다(고치지 않았으면 빈칸) |
| 정산 진입 → 시작 화면 | 방 상세를 읽어 「택시비 정산 시작하기」를 띄운다 |
| 정산 입력 폼 | 1/N 자동 채우기(18,700 → 9,350 + 9,350), 합계 실시간 판정, 예상 요금은 **힌트로만** |
| **정산 시작 요청** | **201** — 본문이 정확하고 하이픈이 매퍼에서 떨어졌다(`3333012345678`) |
| 받는 사람 화면 | 받을 돈·가린 실명(「테**2」)·계좌 복사·「받았어요」. 「못 받았어요」는 **NOT_SENT 라 안 뜬다**(설계대로) |
| 홈 배너 | 「아직 보내지 않은 택시비 1건 · 9,350원 · 테스터1님에게」 |
| 보내는 사람 화면 | 토스 딥링크·계좌/금액 복사·「보냈어요」 |
| **「보냈어요」** | **204** → 즉시 재조회 → 버튼이 **상태**로 바뀐다(「보냈다고 알렸어요」) |

## 🔴 실측에서만 드러난 결함 2건 — 고쳤다

### 1. 정산을 **시작할 길이 없었다**
진입점이 둘(22 탑승 상세 · 34 탑승 기록)인데 **둘 다 막혀 있었다**:
- 22 는 21 대기 화면의 카드에서만 열리고, 방이 끝나면 21 이 닫힌다 → **끝난 방의 22 에 닿을 수 없다**
- 34 는 `fare != null`(= 정산이 이미 있다)일 때만 버튼을 띄웠다 → **정산이 없으면 시작 버튼도 없다**

고리가 닫혀 있어 방장이 자기 방을 영영 정산하지 못했다. 34 의 조건을 **`memberCount >= 2`** 로 바꿨다 —
목록에 있다는 것이 이미 「끝난 방 + 내가 탑승자」를 뜻하므로(서버가 도착한 멤버에게만 기록을 준다)
남은 조건은 그것뿐이고, 「내가 방장인가」는 39 화면이 방 상세를 읽어 가른다.

### 2. 보내는 사람이 **아무것도 못 했다**
「내 송금 상태를 확인하지 못했어요」가 뜨고 「보냈어요」·「토스로 보내기」가 **둘 다 없었다.**
원인: `myTransferStatus` 는 **PR #328 이 아직 머지 전**이라 지금 서버가 보내지 않는다. 그 null 을
「모른다」로 읽어 버튼을 닫은 것이다(그 자체는 옳은 방어다).

`transfers` 에서 **세션 uuid 로 내 행을 찾는 폴백**을 넣었다(그 필드가 생기기 전의 옛 길).
**받는 사람에게는 폴백하지 않는다** — PAYER 의 null 은 「모른다」가 아니라 「보낼 것이 없다」이고,
메우면 방장에게 「보냈어요」가 떠서 403 `NOT_FARE_SPLIT_SENDER` 가 된다.
`RemoteFareSplitRepository` 가 세션을 받게 되었다(그 한 자리에만 쓴다).

## 운영 메모

- 디버그 APK 는 **`MOYEOTA_BASE_URL` 을 매 빌드마다 줘야** 로컬을 가리킨다. 안 주면 기본값이
  프로덕션(`https://api.moyeota.co.kr/`)이다 — 작업 중 한 번 그 상태로 빌드해 **실패한 로그인 1건**이
  프로덕션에 나갔다(401, 쓰기 없음). `local.properties` 에 그 키를 박아 두면 사고가 반복되지 않는다.
