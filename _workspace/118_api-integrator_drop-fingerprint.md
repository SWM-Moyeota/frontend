# 118 · api-integrator — 지문·상태조회 API 제거 + 25·26 폴링 수단 교체

범위: `data/` · `domain/` 전부 + presentation 의 25·26 두 화면과 그 테스트.
백엔드 기준: 읽기 전용 체크아웃 `af1e4cf`, 근거 커밋 `04f6be7` "refactor: 방 지문(fingerprint)과 방 상태 조회 API 제거" **소스 직독**. **실서버 미검증**(서버를 띄우지 않았다).

---

## 1. 백엔드 변경 확인 (`git show 04f6be7`)

| 항목 | 서버 변경 |
|---|---|
| `GET /matching/rooms/{partyId}/status` | **삭제** (`PartyController.status` + `PartyService.getPartyStatus` 제거) → 호출하면 404 |
| `PartyDetailResult` | `fingerprint` 필드 제거, `from(party, summaries, rideCounts)` 로 시그니처 축소 |
| `PartyStatusResponse` | `(status, currentMembers)` **둘뿐** (`of(snapshot, fingerprint)` → `from(party)`) |
| 삭제된 서버 내부 | `PartyFingerprint`, `PartyStatusSnapshot`, `PartyStatusRow`, 상태 전용 쿼리, `moyeota.party.fingerprint-secret` |
| Swagger | 방 상세 설명에서 지문 서술 제거, 합류는 "동승자 목록은 SSE 구독(/events)의 connected 로 받는다" 로 갱신 |

---

## 2. 제거한 것

### domain
| 파일 | 제거 |
|---|---|
| `model/Ride.kt` | `Ride.fingerprint` 필드. `PartyStatus` → **`(status, currentMembers)`**. **`PartyStatus.isSameAs(ride)` 제거** |
| `repository/RideRepository.kt` | `getPartyStatus(partyId)` (기본 구현째로). `getPartyDetail` 에 "주기 조회가 이걸 부른다 + 쿼리 3개라 비싸다" KDoc 신설 |

`PartyStatus` **타입은 남겼다** — `joinParty(partyId): PartyStatus` 의 반환 타입으로 계속 쓰인다. 필드만 줄였고, KDoc 을 "합류 응답 전용"으로 다시 썼다.

### data
| 파일 | 제거 |
|---|---|
| `remote/MatchingApi.kt` | `getPartyStatus` + `@GET("api/v1/matching/rooms/{partyId}/status")` 경로 상수 + KDoc. `getPartyDetail` KDoc 을 "주기 조회의 대상, 쿼리 3개" 로 승격 |
| `remote/dto/PartyDtos.kt` | `PartyDetailResponse.fingerprint`, `PartyStatusResponse.fingerprint` |
| `remote/PartyMappers.kt` | `toRide` 의 지문 이관, `toPartyStatus` 의 지문 이관 |
| `repository/RemoteRideRepository.kt` | `getPartyStatus` override. `recoverMembership` → `PartyStatus(status, members.size)` |
| `repository/DummyRideRepository.kt` | `joinParty` 의 `fingerprint = null` 인자 |

### presentation (25·26)
| 제거된 함수 | 왜 |
|---|---|
| `shouldReloadDetail(held, status)` (25) | 「지문이 그대로면 상세 재조회 생략」이 **원리적으로 불가능**해졌다 — 이제 받는 게 곧 상세다 |
| `shouldLoadRideDetail(held)` (26) | 폴링이 주는 게 그 상세여서 "읽을지 고를 대상"이 없다. 첫 조회 실패도 다음 주기가 그냥 복구 |
| `shouldFetchPartyDetail` 의존 (25 → 21 파일) | 끊었다. 21 담당 에이전트의 `MatchWaitingRoute.kt` 를 건드리지 않고도 내 쪽이 독립적으로 컴파일된다 |

---

## 3. 25·26 의 새 폴링 방식 — 간격과 근거

### 왜 폴링을 없앨 수 없나
배차·운행 전이 `MATCHING → DRIVER_ASSIGNED → IN_RIDE → FINISHED` 에는 **SSE 이벤트도 FCM 도 없다.**
서버 SSE 발행처는 `PartyService` 의 참여·퇴장·종료뿐이고 승객 푸시는 `DRIVER_ARRIVED` 하나뿐이다. 그래서 **수단만** 바꿨다: `/status` → `GET /matching/rooms/{partyId}`(`getPartyDetail`).

### 간격을 늘린 근거 (상세 = 서버 쿼리 3개)
| 화면 | 옛 구조 | 간격 그대로 두면 | **고른 값** | 결과 |
|---|---|---|---|---|
| 25 배차 상태 | 5초 × 쿼리 1개 = 0.20 q/s | 5초 × 3개 = **0.60 q/s (3배)** | **7초** | 0.43 q/s |
| 26 운행 중 | 4초 × 쿼리 1개 = 0.25 q/s | 4초 × 3개 = **0.75 q/s (3배)** | **8초** | 0.38 q/s |

**두 값이 다른 이유** (둘 다 지시받은 6~8초 범위 안):
- **25 = 7초, 범위의 짧은 쪽.** 기사 위치 조회(`GET /dispatch/rides/{id}`)가 같은 루프에 얹혀 있어 이 간격이 곧 **마커 갱신 주기**다. 40km/h 로 오는 택시가 한 걸음에 약 78m — 더 벌리면 마커가 튄다.
- **26 = 8초, 범위의 긴 쪽.** 기다리는 전이가 운행 완료 하나뿐이고 지도에 주기마다 움직일 마커가 없다(출발·도착·경로는 방 생성 시 확정, 내 위치는 단말이 1초마다 직접 갱신). 그래서 가장 낮은 부하를 고를 수 있다.

대가는 **전이 감지 지연**(최악의 경우 간격만큼)이다. 25→26 은 승객이 이미 차에 탄 상태, 26→33 은 하차 직후라 7~8초가 수용 가능하다고 판단했다.

근거·부하 표·택시 모드 한정 사실은 신설 파일 `presentation/core/PartyDetailPolling.kt` 의 KDoc 에 한곳으로 모았고, 두 화면의 간격 상수 KDoc 이 그걸 가리킨다.

### recomposition 억제 — 지문의 대체
```kotlin
// presentation/core/PartyDetailPolling.kt
internal fun shouldPublishDetail(held: Ride?, fetched: Ride): Boolean = held != fetched
```
매 주기 같은 상세가 오므로 그대로 흘려 넣으면 7~8초마다 지도·기사 카드가 재구성된다. 판정이 `!=` 하나인 게 핵심이다 — `Ride` 와 `User` 가 data class 라 **멤버 목록까지 깊은 비교**가 된다.

**지문이 하던 일을 equals 가 더 잘 한다.** 지문이 필요했던 이유는 폴링이 받는 게 *요약*(상태·인원수)이라 「한 명 나가고 한 명 들어온」 변화를 못 잡았기 때문인데, 받는 게 곧 상세인 지금은 그 경우까지 잡힌다. 지문을 되살릴 이유가 없다.

### 전이 판정 — 입력을 `Ride` 로 통일
| 함수 | 전 | 후 |
|---|---|---|
| 25 | `markStartedIfInRide(status: RideStatus?)` (private) | `isRideStarted(ride: Ride?): Boolean` (internal, 순수) + 그걸 쓰는 private `markStartedIfInRide(ride)` |
| 26 | `isRideFinished(status: RideStatus?)` | `isRideFinished(ride: Ride?)` |

두 응답(요약/상세)으로 똑같이 판정할 일이 없어졌으니 들고 있는 값 그대로 받는다.

### 루프에서 `fetched` 와 `held` 를 가려 쓴 곳
- **전이 판정은 이번 주기에 받은 값(`fetched`)으로만** — 실패한 주기(null)는 "아직 아니다". 옛 코드의 `status?.status` 와 같은 의미.
- **기사 조회 게이트는 `fetched ?: held`** — 한 주기 실패했다고 마커 갱신을 멈추면 택시가 오는 동안 지도가 굳는다.

### 없어진 옛 예외 조건 (25)
지문이 없는 서버에서는 요약만으로 `MATCHING`(기사 찾는 중)과 `DRIVER_ASSIGNED`(배정 완료)를 가를 수 없어(앱에서는 둘 다 `DISPATCHING`, 인원도 같다) 「미배정인 동안에는 매 주기 상세를 읽는다」는 단서가 붙어 있었다. 이제 매 주기가 상세라 `Ride.driverId` 가 그 자리에서 들어온다 — 조건문 하나가 통째로 사라졌다.

### 택시 모드 전용
**동승 모드에서는 25·26 에 아예 들어갈 수 없다** — 서버가 `DISPATCHING`·`ONGOING` 을 내지 않는다(`docs/MODE-ROUTES.md` 원칙 1). 즉 이 폴링은 **택시 모드에서만 돈다**. 두 화면의 Route KDoc, ViewModel 폴링 함수 KDoc, `PartyDetailPolling.kt` 세 곳에 명시했다.

---

## 4. 깨진 테스트 처리

### 삭제
| 파일 | 왜 |
|---|---|
| `presentation/core/PartyStatusTest.kt` | 주제가 `PartyStatus.isSameAs` 하나였다. 그 함수가 없어졌고 `PartyStatus` 는 로직 없는 데이터 홀더가 됐다 |
| `presentation/feature/matching/DetailReloadGateTest.kt` | 주제가 `shouldReloadDetail` 이었다. 7건 전부 지문 비교 기반 |

### 신설
| 파일 | 내용 |
|---|---|
| `presentation/core/PartyDetailPollingTest.kt` | 9건. `shouldPublishDetail` — 첫 조회 실패 복구(held=null), **같은 상세는 흘려보냄**, 상태 변화, **driverId 만 달라지는 기사 배정 전이**, **인원수 같은 멤버 교체**(지문이 하던 일), 인원 증감. + **간격 회귀 가드** 2건: 두 상수가 하한(6초) 아래로 못 내려가고, 25 ≤ 26 순서가 뒤바뀌지 않는다 |
| `presentation/feature/matching/RideStartSignalTest.kt` | 6건. `isRideStarted` — ONGOING 만 참, 배차 단계(배정 전·후)·MATCHED·RECRUITING·CANCELED·COMPLETED 는 거짓, null 은 "아직 아니다" |

간격 상수에 테스트를 붙인 이유: 수단이 쿼리 1개 → 3개로 바뀌었으니 누가 간격을 4초로 되돌리면 부하가 3배가 되는데 **화면에는 아무 증상이 없다**(서버 부하로만 드러난다). 그래서 `DRIVER_POLL_INTERVAL_MS`·`RIDE_POLL_INTERVAL_MS` 를 `internal` 로 올려 숫자로 못 박았다.

### 수정
| 파일 | 내용 |
|---|---|
| `presentation/feature/chat/RideFinishSignalTest.kt` | `isRideFinished` 입력이 `Ride?` 로 바뀜. `shouldLoadRideDetail` 테스트 삭제. 오타 수정(28 → 33) |
| `data/.../PartyMappersTest.kt` | 지문 테스트 7건 제거. 합류 응답 계약을 `(status, currentMembers)` 로 다시 못 박고, **「합류 응답 DTO 에 필드가 딱 둘」**·**「상세 DTO 에 fingerprint 가 없다」** 를 `serializer().descriptor` 로 고정(JVM 필드 목록은 `@Serializable` 의 `Companion` 이 섞여 못 쓴다). **「옛 서버가 지문을 실어 보내도 깨지지 않는다」** 2건 추가(롤아웃 중 방어 — `ignoreUnknownKeys`). 「같은 방 상세를 두 번 매핑하면 `==`, 멤버 교체면 다르다」 추가 |
| `data/.../RemoteRideRepositoryTest.kt` | `getPartyStatus` 테스트 3건 → 「주기 조회는 상세를 읽고 같은 방을 두 번 읽으면 서로 같다」+「더미도 같다」로 교체. `FakeMatchingApi.getPartyStatus`·`statusCall`·`FINGERPRINT` 제거 |
| `data/.../AuthenticatedPathContractTest.kt` | `/status` 경로 고정 제거. 상세 경로 단독 고정 + **「`getPartyStatus` 메서드가 되살아나면 실패」** 리플렉션 가드 |
| `data/.../PartySseEventTest.kt` | `connected` 스냅샷 픽스처에서 `fingerprint` 줄과 그 단정 제거(실제 서버 포맷에 맞춤) |

**건드리지 않은 것**: `WaitingPollTest.kt`(21 전용 — 작업 중 21 담당이 이미 삭제하고 `WaitingSseStateTest.kt` 로 교체했다), `MatchWaiting*`, `JoinConfirm*`, `MainNavGraph.kt`, `feature/auth/*`, `SignupDraft.kt`.

---

## 5. 검증 결과

```
./gradlew :data:testDebugUnitTest --console=plain -q        → BUILD SUCCESSFUL
./gradlew :presentation:testDebugUnitTest                    → BUILD SUCCESSFUL
./gradlew :app:assembleDebug                                 → BUILD SUCCESSFUL
```
- `:data` **259건 전부 통과** (실패·에러 0)
- `:presentation` **174건 전부 통과** (실패·에러 0) — 신설 `PartyDetailPollingTest`·`RideStartSignalTest` 포함
- `--rerun-tasks` 로 캐시 없이 재실행해도 동일

### 다른 에이전트 파일 때문에 남은 컴파일 에러 — **없다**
작업 중 21 담당 에이전트가 `MatchWaitingRoute.kt`·`JoinConfirmRoute.kt`·`WaitingPollTest.kt` 의 같은 변경을 **먼저 반영했다**(`WaitingSseStateTest.kt` 신설, `shouldFetchPartyDetail` 제거 확인). 그래서 `PartyStatus` 축소·`getPartyStatus` 제거로 그쪽에서 깨질 것으로 예상했던 에러가 이미 해소된 상태이고, `:app:assembleDebug` 까지 통과했다. **내 파일이 원인인 미해결 에러도, 남의 파일이 원인인 미해결 에러도 없다.**

### 코드에 남은 "fingerprint" 문자열
전부 **의도된 문서·가드**다: 왜 지웠는지 설명하는 KDoc/주석, 그리고 되살아나는 걸 막는 테스트 3건(`PartyStatusResponse` 필드 둘, `PartyDetailResponse` 에 없음, `MatchingApi.getPartyStatus` 없음) + 옛 서버 호환 픽스처 2건. 동작 코드에는 하나도 없다.

### 문서
`docs/MODE-ROUTES.md` 는 **건드리지 않았다** — 25·26 **폴링**에 관한 서술이 없다(모드별 노출 여부와 확인 방법만 있고, 간격·수단 언급 없음). 지시대로 그대로 뒀다.

---

## 6. 화면이 쓸 최종 시그니처 (변경분)

```kotlin
// domain/model/Ride.kt
data class Ride(...)                              // fingerprint 필드 없음
data class PartyStatus(                           // 합류 응답 전용
    val status: RideStatus,
    val currentMembers: Int,
)                                                 // isSameAs 없음

// domain/repository/RideRepository.kt
suspend fun getPartyDetail(partyId: Long): Ride   // 25·26 주기 조회의 대상. 서버 쿼리 3개 — 비싸다
suspend fun joinParty(partyId: Long): PartyStatus // 그대로
// getPartyStatus 삭제

// presentation/core/PartyDetailPolling.kt (신설)
internal const val PARTY_DETAIL_POLL_FLOOR_MS = 6_000L
internal fun shouldPublishDetail(held: Ride?, fetched: Ride): Boolean

// presentation/feature/matching/DispatchStatusRoute.kt
internal const val DRIVER_POLL_INTERVAL_MS = 7_000L
internal fun isRideStarted(ride: Ride?): Boolean          // shouldReloadDetail 대체

// presentation/feature/chat/RideOngoingRoute.kt
internal const val RIDE_POLL_INTERVAL_MS = 8_000L
internal fun isRideFinished(ride: Ride?): Boolean         // 입력 타입 변경
// shouldLoadRideDetail 삭제
```

---

## 7. 백엔드에 요청할 것

1. **D-C (설계 공백, 영향 큼 — 114 §3 에서 올렸고 이번에 더 커졌다).**
   배차·운행 전이(`MATCHING → DRIVER_ASSIGNED → IN_RIDE → FINISHED`)에 **SSE 이벤트가 없다.** 발행처가 join/leave/finish/expire 뿐이다. 전에는 쿼리 1개짜리 `/status` 로 싸게 폴링했지만 그 엔드포인트가 사라져 **쿼리 3개짜리 방 상세를 7~8초마다 돌려야 한다** — 지문·`/status` 제거로 21 대기 화면의 폴링은 0 이 됐지만 **25·26 의 폴링 비용은 오히려 올랐다.**
   요청: `PartyService` 의 상태 전이(기사 배정·탑승 시작·운행 종료)에도 SSE 이벤트를 발행해 달라. 그러면 25·26 도 21 처럼 폴링을 0 으로 내릴 수 있다. 차선책은 `/status` 같은 **가벼운 상태 조회의 부활**(지문 없이 `status` + `taxiDriverId` 만이면 충분하다 — `driverId` 가 있어야 `MATCHING`/`DRIVER_ASSIGNED` 를 가를 수 있다).

2. **D-B (코드 오류, 영향 큼 — 114 §3 재송부, 미해결).**
   `ALREADY_JOINED_PARTY` 가 join 경로에서 도달 불가다. `PartyService.join` 의 `validateNotInOngoingParty → existsByMemberIdAndStatusIn` 이 **합류 대상 방을 제외하지 않아** 같은 방 재합류도 `ALREADY_JOINED_OTHER_PARTY` 로 온다. 앱은 상세 조회로 우회 중(`recoverMembership`)이지만, `existsOngoing` 에서 `partyId` 를 제외하거나 재합류를 멱등 200 으로 답해 주는 게 맞다.

3. **D-E (주의 — 114 §3 재송부).** `badgeId` 는 서버가 항상 null(`MemberInfo.of` 의 TODO)이고 타입도 어긋난다(`MemberSummary.badgeId` 는 Long, DTO 는 String).

4. **신규 — 롤아웃 순서.** 앱은 지문을 더 이상 **읽지 않지만** 옛 서버가 실어 보내도 깨지지 않는다(`ignoreUnknownKeys`, 테스트 2건으로 고정). 반대로 **`/status` 는 서버에서 사라졌으므로 앱이 먼저 배포되어도 안전하다**(부르는 코드가 없다). 순서 제약 없음.

---

## 8. qa-verifier 에게 — 교차 검증 요청

| 확인할 것 | 어떻게 |
|---|---|
| 25 배차 상태 전이 | 택시 모드 서버(`TAXI_ENABLED=true`) + 기사측 API 로 배정 → **7초 안**에 25b 레이더 → 25c 배정 확인으로 넘어가는지. 기사 마커가 7초마다 움직이는지 |
| 25 → 26 | 기사측 board → **7초 안**에 26 으로 자동 전이 |
| 26 → 33 | 기사측 complete → **8초 안**에 33 도착 완료 |
| recomposition | 상태가 안 바뀌는 동안 25·26 지도가 7~8초마다 **깜빡이지 않는지**(`shouldPublishDetail` 이 먹는지) |
| 404 회귀 | logcat 에 `/matching/rooms/{id}/status` 호출이 **한 건도 없는지** |
| 합류 | 20 → 21 합류이 정상 동작하는지(응답 필드가 둘로 줄었다) |
| 동승 모드 | 25·26 에 닿지 않는지 — 닿으면 폴링이 돌아선 안 되는 곳에서 돈다 |
