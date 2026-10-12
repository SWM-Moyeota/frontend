# 114 · api-integrator — 매칭방 SSE 변경분(delta) 전환 + join 응답 변경 대응

범위: `data/` · `domain/` (+ presentation 은 **컴파일 전용 최소 수정** 2곳).
백엔드 기준: 읽기 전용 체크아웃 `origin/develop 53b8cf1` 소스 직독. **실서버 미검증**(서버를 띄우지 않았다).

---

## 1. 변경 파일

### 신규
| 파일 | 역할 |
|---|---|
| `data/src/main/kotlin/com/moyeota/data/remote/matching/PartySseEvent.kt` | SSE 프레임의 data 계층 표현 + **순수 파싱 함수** `parsePartySseFrame(type, data)` + 이벤트 이름 상수 |
| `domain/src/main/kotlin/com/moyeota/domain/model/PartyError.kt` | `PartyJoinException` — 합류 실패를 **복구 가능한 신호**로 좁힌 도메인 예외 |
| `data/src/test/kotlin/com/moyeota/data/remote/matching/PartySseEventTest.kt` | SSE 계약 테스트 15건(실제 서버 포맷 문자열) |

### 수정 — domain
| 파일 | 내용 |
|---|---|
| `model/PartyEvent.kt` | `Connected`/`Changed` → `Snapshot`/`MemberJoined`/`MemberLeft` 로 **전면 교체** (`Closed`·`Disconnected` 유지) |
| `repository/RideRepository.kt` | `observePartyEvents` KDoc 전면 갱신(폴링 0회 근거·중복 수집 경고·best-effort), `joinParty` 반환 타입 `Ride` → `PartyStatus` |

### 수정 — data
| 파일 | 내용 |
|---|---|
| `remote/matching/PartyEventSource.kt` | `changed` 제거, 4종 이벤트 파싱 위임, `connected` 를 **스냅샷으로 발행**(이전엔 무시), 파싱 실패 = 경고 후 건너뜀, KDoc(heartbeat 30초·readTimeout·중복 수집) |
| `remote/dto/PartyDtos.kt` | `MemberInfo.joinedAt` **삭제**, `MemberJoinedPayload`·`MemberLeftPayload` 추가, 상세/상태 DTO KDoc 갱신 |
| `remote/PartyMappers.kt` | `PartySseEvent.toPartyEvent(currentUuid)` 추가(기존 `toRide`·`toUser` 재사용), `readApiError()`·`partyJoinException()` 추가 |
| `remote/MatchingApi.kt` | `joinParty` 반환 `PartyDetailResponse` → `PartyStatusResponse` |
| `remote/NetworkModule.kt` | **`streamClient.readTimeout` 0(무제한) → 45초** (`SSE_READ_TIMEOUT_SECONDS`) |
| `repository/RemoteRideRepository.kt` | `observePartyEvents` 에 세션 uuid 매핑, `joinParty` 재작성 + `recoverMembership` 복구 경로 |
| `repository/DummyRideRepository.kt` | `joinParty` → `PartyStatus` |

### 수정 — 테스트
`PartyMappersTest.kt`(joinedAt 제거 + 합류 응답 JSON 매핑 테스트 추가), `RemoteRideRepositoryTest.kt`(합류 테스트 재작성 + 409 복구 3건), `RemoteActivePartyRepositoryTest.kt`(Fake 시그니처).

---

## 2. 화면이 쓸 최종 시그니처 (전문)

### 2-1. 도메인 이벤트

```kotlin
package com.moyeota.domain.model

sealed interface PartyEvent {
    /** 구독 직후 서버가 준 방 상세 전체. 재연결 때마다 다시 오므로 catch-up 조회가 필요 없다. */
    data class Snapshot(val ride: Ride) : PartyEvent

    /** 들어온 멤버 1명 + 들어온 뒤의 방 상태(정원 충족 전이가 여기서 보인다). */
    data class MemberJoined(val member: User, val status: RideStatus) : PartyEvent

    /** publicId 가 null 이면 누가 나갔는지 서버도 모른다 — 화면은 상세를 다시 받아야 한다. */
    data class MemberLeft(val publicId: String?, val status: RideStatus) : PartyEvent

    /** 방이 닫혔다. 서버가 이 뒤에 구독을 끊는다(data 는 partyId 숫자 문자열이라 실을 값이 없다). */
    data object Closed : PartyEvent

    /** 연결이 끊겼다(서버 신호가 아니라 앱이 알아챈 상태). 곧 다시 붙고 Snapshot 이 전체를 보정한다. */
    data object Disconnected : PartyEvent
}
```

- `MemberJoined.member` 는 **방 상세의 멤버와 같은 매퍼**를 거친 `User` 다 → `isMe` 가 상세와 같은 근거(publicId == 세션 uuid)로 이미 채워져 있다. 그대로 멤버 목록에 덧붙이면 된다.
- `MemberLeft.publicId` 는 빈 문자열까지 null 로 접었다 — "모름"은 한 가지 모양(null)이다.

### 2-2. 구독

```kotlin
fun observePartyEvents(partyId: Long): Flow<PartyEvent> = emptyFlow()   // 기본 구현 유지
```

21 화면 담당이 반드시 지켜야 하는 계약:
1. **순서는 늘 `Snapshot` → 변경분**이다. 재연결 때도 `Snapshot` 이 먼저 온다.
2. 따라서 **평상시 폴링 0회 · 재연결 catch-up 조회 0회**. `Snapshot.ride` 로 상태를 덮으면 끝이다(상세 재조회 불필요).
3. 상세를 다시 읽어야 하는 경우는 **단 하나**: `MemberLeft.publicId == null`.
4. ⚠️ **같은 partyId 를 두 곳에서 수집하지 말 것.** 서버 `PartySseRegistry.subscribe` 가 같은 memberId 의 기존 연결을 먼저 끊으므로(`closeMember`), 두 번 구독하면 두 연결이 서로를 끊고 3초마다 번갈아 재연결하는 무한 루프가 된다. 수집은 ViewModel 당 정확히 한 번.
5. ⚠️ **best-effort 다.** 서버가 after-commit 단일 스레드(corePoolSize=1, queue 5000)로 발행하고 **실패해도 재발행하지 않는다**. Redis Pub/Sub 도 at-most-once. → `Disconnected` 동안의 폴백 폴링은 **반드시** 남겨야 한다(채팅 소켓과 같은 정책: 살아 있으면 0회, 죽었을 때만).
6. **배차·운행 전이(MATCHING→DRIVER_ASSIGNED→IN_RIDE→FINISHED)는 이 스트림에 오지 않는다.** 서버 발행처가 join/leave/finish/expire 뿐이다. 25·26 화면은 폴링 유지.
7. 403/404 면 스트림이 조용히 끝난다(재시도 없음). 내가 방을 나가면 **이벤트 없이** 끊긴다.

### 2-3. 합류

```kotlin
suspend fun joinParty(partyId: Long): PartyStatus   // PartyStatus(status: RideStatus, currentMembers: Int, fingerprint: String?)
```

- **동승자 목록이 오지 않는다.** 필요하면 `getPartyDetail(partyId)` 를 따로 부르거나 — **권장** — 바로 21 로 넘겨 `observePartyEvents` 의 첫 `Snapshot` 이 상세를 주게 한다(왕복 1회 절약).
- 반환 `status` 로 "내가 마지막 자리를 채웠는지" 알 수 있다(COMPLETED, 기사 모드면 MATCHING).
- 실패는 **모두 `PartyJoinException`**:

```kotlin
class PartyJoinException(
    val code: String?,              // 서버 MatchingErrorCode 이름
    val serverMessage: String?,     // 서버 한국어 원문
    val httpStatus: Int? = null,
    val isNetwork: Boolean = false,
    val joinedStatus: PartyStatus? = null,   // 아래 참고
    cause: Throwable? = null,
) : RuntimeException(...) {
    val isAlreadyInThisParty: Boolean        // joinedStatus != null — 사실상 성공
    val isAlreadyInAnotherParty: Boolean
    val isAlreadyJoined: Boolean
    val isPartyFull: Boolean
    val isPartyClosed: Boolean
    val isPartyNotFound: Boolean
}
```

**`isAlreadyInThisParty == true` 는 실패가 아니다.** 데이터 계층이 `GET /matching/rooms/{partyId}` 로 내 publicId 가 members 에 있는지 **확인한 뒤에만** `joinedStatus` 를 채운다(추측 아님). 화면은 이 경우를 **합류 성공과 똑같이** 처리해야 한다 — 그러지 않으면 그 계정은 재시도도 409, 다른 방도 409 라 30분 스윕 전까지 어떤 방에도 못 들어간다.

20 합류 화면 분기 권장:
| 신호 | 처리 |
|---|---|
| 정상 반환 | 21 로 이동 |
| `isAlreadyInThisParty` | **21 로 이동**(토스트 없이, 또는 "이미 참여 중인 방이에요") |
| `isAlreadyInAnotherParty` | "이미 참여 중인 방이 있어요" + 그 방으로 가는 길 |
| `isPartyFull` / `isPartyClosed` | 목록 재조회 + 각각의 문구 |
| `isPartyNotFound` | 목록에서 제외 |
| `isNetwork` | 재시도 안내(재시도가 409 로 떨어지면 위 복구가 받는다) |

---

## 3. 백엔드 계약 확인 결과 (소스 직독)

| 이벤트 | data 실제 모양 |
|---|---|
| `connected` | `PartyDetailResult` 전체 JSON (`PartySseRegistry.subscribe`, `MediaType.APPLICATION_JSON`) |
| `member-joined` | `{"member":{"publicId","nickname","imageUrl","badgeId","rideCount"},"status":"ACTIVE"}` |
| `member-left` | `{"publicId":"uuid"` 또는 `null`, `"status":"ACTIVE"}` |
| `closed` | `"42"` — **partyId 숫자 문자열**(`String.valueOf(partyId)`), JSON 아님 |
| 주석 | `:ping`, 30초(`PartySseRegistry.HEARTBEAT_MS = 30_000`) |

`POST /matching/rooms/{partyId}/join` → `PartyStatusResponse(status, currentMembers, fingerprint)` (`PartyController.join`).
`PartyDetailResult.MemberInfo` = `(UUID publicId, String nickname, String imageUrl, String badgeId, Integer rideCount)` — `joinedAt` 없음.

### 이상한 점 / 백엔드에 요청할 것

1. **D-A (문서 오류, 영향 중)** — `PartySseController` 의 Swagger `@Operation` 설명이 **옛 계약 그대로**다: "멤버 변화 시 `changed`, … 데이터는 partyId 뿐이라 받으면 상세를 다시 조회한다. 5초마다 heartbeat". 실제 코드는 `member-joined`/`member-left` + 상세 스냅샷 + 30초다. Swagger 를 믿고 구현하면 이번 작업 전 상태로 되돌아간다.
2. **D-B (코드 오류, 영향 큼)** — **`ALREADY_JOINED_PARTY` 는 join 경로에서 도달 불가**다. `PartyService.join` 이 `party.join()` 보다 먼저 `validateNotInOngoingParty` → `existsOngoingByMemberId` 를 보고, 이 질의가 **합류 대상 방을 제외하지 않는다**(`PartyJpa.existsByMemberIdAndStatusIn`). 그래서 **같은 방에 재합류해도 `ALREADY_JOINED_OTHER_PARTY`("이미 참여 중인 방이 있습니다.")** 가 온다 — 앱이 "이 방인지 다른 방인지"를 코드로 가릴 수 없다. 앱은 상세 조회로 우회했지만(§2-3), 서버가 `existsOngoing` 에서 `partyId` 를 제외하거나 재합류를 멱등 200 으로 답해 주는 게 맞다.
3. **D-C (설계 공백, 영향 큼)** — 배차·운행 전이에 SSE 이벤트가 없다(발행처가 join/leave/finish/expire 뿐). `MATCHING→DRIVER_ASSIGNED→IN_RIDE→FINISHED` 는 25·26 화면이 계속 폴링해야 한다. 상태 전이 이벤트 추가 요청.
4. **D-D (안정성)** — SSE 발행이 after-commit **단일 스레드 큐**이고 실패 시 재발행이 없다. 멤버 변화가 몰리는 순간에 유실 가능 → 앱이 끊김 폴백을 지울 수 없다.
5. **D-E (주의)** — `badgeId` 는 서버가 항상 null(`MemberInfo.of` 의 TODO). 타입도 어긋난다(`MemberSummary.badgeId` 는 Long, DTO 는 String).

---

## 4. 테스트 결과

```
./gradlew :data:testDebugUnitTest :domain:test --console=plain -q   → BUILD SUCCESSFUL
./gradlew :app:assembleDebug                                        → BUILD SUCCESSFUL
```
`:data` 264건 전부 통과 (실패·에러 0). `:domain:test` 는 NO-SOURCE(테스트 소스 없음).

새로 추가한 검증:
- `PartySseEventTest` 15건 — `connected` 전체 스냅샷, 여러 줄 data, `member-joined`(타인/나/요약 없음/`member` 키 누락), `member-left`(uuid/null/빈 문자열), **`closed` 의 비-JSON data**, 모르는 이벤트 이름, 이름 없는 프레임, 깨진 JSON 3종이 **예외가 아니라 Skipped** 로 떨어지는지, 모르는 상태 문자열 폴백, `Disconnected` 통과.
- `PartyMappersTest` — 합류 응답(`PartyStatusResponse`) JSON 매핑, `joinedAt` 제거 반영.
- `RemoteRideRepositoryTest` — 합류가 상세를 **부르지 않음**, 409 복구(상세 1회 조회 → `joinedStatus` 채움), 상세에 내가 없으면 `isAlreadyInAnotherParty`, `PARTY_FULL` 은 상세를 **읽지 않음**.

## 5. presentation 에 남긴 TODO (로직 변경 없음, 컴파일만 맞춤)

| 파일 | TODO |
|---|---|
| `feature/matching/MatchWaitingRoute.kt` | `observeEvents()` 의 when 분기. `// TODO(21 화면 에이전트): Snapshot.ride 를 그대로 쓰고 멤버 증감을 목록에 반영하면 상세 재조회가 0 이 된다(평상시 폴링도 끌 수 있다)`. 현재는 예전 동작(신호마다 `refreshQuietly()`) 그대로 — `Snapshot`≈옛 `Connected`, `MemberJoined`/`MemberLeft`≈옛 `Changed`. 폴링 상수(4초/20초)도 손대지 않았다. |
| `feature/explore/JoinConfirmRoute.kt` | `join()` 이 `joinParty` 뒤 `getPartyDetail` 을 한 번 더 부른다. `// TODO(21 화면 에이전트): onJoined 가 ride.id 만 쓰므로 partyId 만 넘기도록 바꾸면 이 재조회가 없어진다`. **`PartyJoinException` 분기는 넣지 않았다**(§2-3 표 참고 — 현재는 기존 문구 "합류하지 못했어요"로 떨어진다). |

추가로 고치지 않고 남긴 것: `core/MainNavGraph.kt` 의 주석 "합류 응답이 곧 최신 방 상세" 는 이제 사실이 아니다(코드는 정상 동작, 주석만 낡음).
