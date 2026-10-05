# 80 · 방 상태 API(`/status`) + 상세 지문 연동 (Backend #177)

브랜치: `feature/party-status-api` (커밋하지 않음). **`data/` 모듈만** 수정했다 — `domain/` 계약은 리더가
이미 추가해 둔 것을 그대로 구현했고, `presentation/` 은 건드리지 않았다(81 compose-builder 담당).

## 연동한 엔드포인트

| 메서드 | 경로 | 응답 | 인증 |
|--------|------|------|------|
| GET | `/api/v1/matching/rooms/{partyId}/status` | `PartyStatusResponse(status, currentMembers, fingerprint)` | Bearer 필수 |
| GET | `/api/v1/matching/rooms/{partyId}` (기존) | `PartyDetailResult` 에 **`fingerprint` 필드 추가됨** | Bearer 필수 |

컨트롤러 기준: `backend origin/develop:src/main/java/team/codingforest/moyeota/matching/party/PartyController.java`
(폴더가 재구성돼 `matching/interfaces/` 가 아니라 `matching/party/` 다 — `MatchingApi` KDoc 의 경로 설명이 한 단계 낡았지만
경로 문자열 자체는 그대로 유효하므로 건드리지 않았다).

## 실제 응답 shape — **실서버 검증 완료** (localhost:8080, 2026-10-06)

사용자가 띄워 둔 서버가 이미 #177 을 포함하고 있었다(`/v3/api-docs` 에 `/status` 매핑과
`PartyDetailResult.fingerprint` 존재). 재시작·종료 없이 조회만 했고, 토큰을 얻기 위해 검증용 계정
하나(`apiverif*`)를 가입시킨 것 외에 **파티 데이터는 일절 쓰지 않았다**.

```
GET /api/v1/matching/rooms/1/status        → 200 {"status":"ACTIVE","currentMembers":1,"fingerprint":"4aa5adb7bd31ea6d"}
GET /api/v1/matching/rooms/1               → 200 {... ,"taxiDriverId":null,"fingerprint":"4aa5adb7bd31ea6d"}
GET /api/v1/matching/rooms/999999/status   → 404 {"code":"PARTY_NOT_FOUND","message":"존재하지 않는 방입니다."}
GET /api/v1/matching/rooms/1/status (토큰 X) → 401 {"code":"USER005","message":"로그인이 필요합니다."}
```

확인된 계약:

- **지문은 두 응답에서 글자까지 같다** — 상세의 `fingerprint` 와 상태의 `fingerprint` 를 그대로 비교할 수 있다.
  서버가 같은 재료(`partyId|STATUS|정렬된 멤버 id`)로 HMAC-SHA256 한 값의 앞 8바이트, 즉 **16진수 16글자**다
  (`PartyFingerprint.java`). 앱은 해석하지 않고 같은지만 본다.
- **참여자 검사가 없다** — 그 방에 들어가 본 적 없는 갓 가입한 계정으로도 200 이었다. 403 은 나지 않는다
  (컨트롤러 메서드에 `@CurrentUser` 조차 없다). 그래도 토큰은 필수다: Security 가 `/api/v1/auth/**` 밖
  전 경로에 `anyRequest().authenticated()` 를 건다.
- `currentMembers` 는 멤버 **id 만** 세는 읽기 모델(`PartyStatusSnapshot`)에서 나온다 — 쿼리 1개. 상세는 3개.
- 목록(`PartyResult`)·생성(`OpenPartyResponse`) 응답에는 지문 필드가 **서버에도 없다**. 상세에만 있다.

문서와의 불일치: `backend/http/matching.http` 는 여전히 `/api/matching/...`(v1 없음) + `hostId` 인 옛 스펙이라
`/status` 예시가 아예 없다. 기준은 컨트롤러 + 위 실측이다.

## Repository 시그니처 (compose-builder 경계면)

`domain/repository/RideRepository` — 리더가 추가한 계약 그대로, **변경 없음**:

```kotlin
suspend fun getPartyStatus(partyId: Long): PartyStatus =
    getPartyDetail(partyId).let { PartyStatus(it.status, it.members.size, it.fingerprint) }   // 기본 구현(더미·테스트)
```

- `RemoteRideRepository` 가 이 기본 구현을 **덮어쓴다** → `api.getPartyStatus(partyId).toPartyStatus()`.
  가벼운 API 를 부르는 게 이 구현의 존재 이유라서, "상세를 부르지 않는다"를 테스트로 못박았다.
- `DummyRideRepository` 는 기본 구현을 그대로 쓴다(지문이 없어 상태+인원수로 비교된다) — 손대지 않았다.
- `Ride.fingerprint: String?` 는 **상세·합류 응답에서만** 채워진다. 목록·생성 응답의 `Ride` 는 null 이다.
- 81 문서(`shouldFetchPartyDetail`, `PartyStatus.isSameAs`)의 사용법과 일치함을 교차 확인했다.

## 변경 파일 (전부 `data/`)

| 파일 | 내용 |
|------|------|
| `data/src/main/kotlin/com/moyeota/data/remote/dto/PartyDtos.kt` | `PartyStatusResponse` 신규(`@Serializable`, 서버 필드명 그대로, 3필드 모두 기본값), `PartyDetailResponse.fingerprint: String? = null` 추가 |
| `data/src/main/kotlin/com/moyeota/data/remote/MatchingApi.kt` | `getPartyStatus(@Path partyId): PartyStatusResponse` 추가 + 실측 KDoc |
| `data/src/main/kotlin/com/moyeota/data/remote/PartyMappers.kt` | `PartyStatusResponse.toPartyStatus()` 신규(`partyStatusToRideStatus` 재사용), `PartyDetailResponse.toRide()` 가 `fingerprint` 전달 |
| `data/src/main/kotlin/com/moyeota/data/repository/RemoteRideRepository.kt` | `override suspend fun getPartyStatus` |
| `data/src/test/.../remote/PartyMappersTest.kt` | 테스트 11개 추가 (36개) |
| `data/src/test/.../remote/AuthenticatedPathContractTest.kt` | `/status` 경로 문자열 고정 1개 (16개) |
| `data/src/test/.../repository/RemoteRideRepositoryTest.kt` | `FakeMatchingApi.getPartyStatus` 구현 + 테스트 3개 (13개) |

### 방어 결정 두 가지

1. **지문은 nullable + 기본값 null.** 구버전 서버(#177 이전)에는 필드 자체가 없다. 논-널로 선언하면
   그 순간 폴링이 전부 파싱 실패한다. `PartyStatus.isSameAs` 가 "한쪽이라도 없으면 상태+인원수"로
   내려가도록 설계돼 있어 null 이 정상 경로다.
2. **빈 문자열은 null 로 접는다**(상세·상태 양쪽 동일). 빈 값끼리 "같다"로 판정해 변화를 놓치는 걸 막는다.
   두 매퍼가 같은 규칙을 쓰는지도 테스트로 묶었다(`같은 방의 상세와 상태를 매핑하면 서로 같다고 판정한다`).

## 테스트 결과

```
./gradlew :data:testDebugUnitTest --console=plain -q   → BUILD SUCCESSFUL
tests=222  failures+errors=0   (신규 15개 포함)
```

새로 추가한 커버리지:

- 상태 문자열 → `RideStatus`(상세와 **같은 변환표**), `currentMembers`, 지문 전달
- 실서버 shape JSON 역직렬화 / 지문 누락 → null / 지문 빈 문자열 → null / 모르는 필드 혼입
- 상세 응답 JSON·DTO 의 지문이 `Ride.fingerprint` 로 넘어가는지, 목록·생성 `Ride` 는 null 인지
- 구버전 상세 응답(지문 없음)에서도 매핑이 안 깨지는지
- `RemoteRideRepository.getPartyStatus` 가 `getPartyDetail` 을 **0회** 부르는지 + `partyId` 전달
- 상세·상태 지문이 같은 자리에 담겨 `isSameAs` 가 "안 바뀌었다"로 판정하는지(인원·상태가 달라도 지문 우선)
- 더미 저장소가 기본 구현(상세에서 깎기)을 그대로 쓰는지
- Retrofit 경로 문자열 `api/v1/matching/rooms/{partyId}/status` 고정 — `/status` 를 빠뜨리면 404 가 아니라
  **상세 응답이 와서 조용히 파싱된다**(지문만 null → 매 주기 "바뀌었다"). 컴파일러가 못 잡는 자리다.

`:app:assembleDebug` 는 돌리지 않았다 — 다른 에이전트가 `presentation/` 을 동시에 고치는 중이라 전체 빌드가
그쪽 사정으로 깨질 수 있다. `data/` 는 `:data:testDebugUnitTest` 가 컴파일까지 포함해 통과했다.

## qa-verifier 에게

- 21 매칭 대기 화면에서 **안 바뀐 주기의 `GET /rooms/{id}` 가 0회**인지 로그로 확인해 달라
  (`/status` 만 반복, 지문이 달라진 주기에만 상세 1회).
- 멤버 교체(한 명 나가고 한 명 들어옴 → 인원수 동일)에서도 상세를 다시 읽는지 — 지문이 잡아야 하는 케이스다.
- 서버를 #177 이전 버전으로 되돌린 환경에서도 대기 화면이 도는지(지문 null → 상태+인원수 비교로 하향).

## 정리하지 않은 것

실서버 검증용 계정 `apiverif<난수>` / uuid `01a10cc7-642c-7e0e-847a-8d309caf8ad7` 가 로컬 백엔드에 남아 있다.
삭제 엔드포인트를 쓰지 않았다 — 인메모리라면 재시작으로 사라지고, 아니면 수동 정리가 필요하다.
