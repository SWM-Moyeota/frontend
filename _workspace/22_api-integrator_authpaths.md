# 22. 도메인 API 인증 전환 (data/·domain/) — api-integrator

작업일: 2026-09-02 / 브랜치: `feature/naver-map` / 선행: `21_api-integrator_auth.md`
검증: `./gradlew :data:testDebugUnitTest :domain:compileDebugKotlin --console=plain` → **BUILD SUCCESSFUL, 96 tests / 0 failures** (이전 88 → +8)
실서버 검증: **완료** (localhost:8080, 새 코드로 기동 중. 아래 응답은 전부 curl 실측값)
`:presentation:compileDebugKotlin` — **의도적으로 깨진 상태**. 깨진 지점은 §3 표 참조 (수정은 compose-builder 몫).

---

## 1. 컨트롤러 전수 대조 결과 — 무엇이 전환됐고 무엇이 안 됐나

기준: `backend/src/main/java/team/codingforest/moyeota/{matching,dispatch,place,report,chat}/…` 현재 소스 + 실서버 curl.

### 전환됨 (@LoginUser · Bearer 필수 · 무토큰 401 `{"code":"UNAUTHORIZED","message":"로그인이 필요합니다."}`)

| 이전 (앱이 쓰던 것) | 지금 (확정) | 실측 |
|---|---|---|
| `POST /matching/rooms/{partyId}/{memberId}/join` | `POST /matching/rooms/{partyId}/join` | 409 `ALREADY_JOINED_OTHER_PARTY` / 무토큰 401 |
| `DELETE /matching/leave/{partyId}/{memberId}` | **`DELETE /matching/leave/{partyId}/0`** (아래 ⚠) | 204, 실제로 토큰 주체가 나감 |
| `GET /dispatch/rides/{partyId}/{memberId}` | `GET /dispatch/rides/{partyId}` | 409 `DRIVER_NOT_ASSIGNED` / 무토큰 401 |
| `GET /users/me/favorite-places?userId=` | `GET /users/me/favorite-places` | 200 `{"places":[]}` / 무토큰 401 |
| `POST /users/me/favorite-places?userId=` | `POST /users/me/favorite-places` | **500 — 백엔드 결함, §5 D-4** |
| `POST /reports` body `{reporterId,partyId,lat,lng}` | `POST /reports` body `{partyId,latitude,longitude}` | 403 `REPORT_NOT_ALLOWED` / 무토큰 401 |
| `PATCH /reports/{reportId}/call-result` | **경로 그대로** | 404 `REPORT_NOT_FOUND`(핸들러 도달) / 무토큰 401 |

### ⚠ 나가기 경로는 지시서와 다르다 — 세그먼트가 하나 더 필요하다

지시서에는 `DELETE /matching/leave/{partyId}` 로 적혀 있으나 **실제 매핑은 그대로 두 세그먼트다**:

```java
@DeleteMapping("/matching/leave/{partyId}/{memberId}")
public ResponseEntity<Void> leave(@PathVariable Long partyId, @LoginUser Long memberId) { … }
```

`memberId` 는 `@PathVariable` 이 아니라 `@LoginUser` 라 **경로 값에 바인딩되지 않는다** — 값은 무시되고 주체는 토큰에서 나온다. 그런데 URL 템플릿에는 세그먼트가 남아 있어 실측 결과:

- `DELETE /matching/leave/1` → 스프링 기본 404 (핸들러 없음)
- `DELETE /matching/leave/1/0` → **204, 그리고 토큰 주체가 실제로 방에서 빠짐**
- `DELETE /matching/leave/1/me` (비숫자) → 403 `NOT_PARTY_MEMBER` — 즉 세그먼트 값은 파싱조차 안 되고 무시된다

그래서 `MatchingApi.leaveParty` 는 `api/v1/matching/leave/{partyId}/0` 으로 **더미 `0` 을 박아** 호출한다. 백엔드에 세그먼트 제거를 요청해 뒀고(§5 R-1), 제거되면 이 리터럴도 같이 지운다. 오타로 보이지 않도록 KDoc 과 `AuthenticatedPathContractTest` 양쪽에 근거를 남겼다.

### 전환 안 됨 (손대지 않았다)

| 엔드포인트 | 현재 방식 | 근거 |
|---|---|---|
| `POST /matching/rooms` | 본문 `creatorId` | `OpenPartyRequest(Long creatorId, …)` — @LoginUser 없음 |
| 채팅 전체 (`/chat-rooms/**`) | `@RequestHeader("X-User-Id") Long userId` | `chat/presentation/*Controller.java` 전부 헤더 방식 유지 |
| 기사 전용 (`/dispatch/online`, `/location`, `/calls/**`, `/rides/{id}/arrive|board|complete`) | `@PathVariable driverId` | 승객 앱 미사용 |
| `/places`, `/places/reverse` | 공개 | 토큰 무관 |

→ `UserSession.currentUserId` 는 **삭제하지 않았다.** 남은 사용처가 위 둘(채팅 헤더 · 방 생성 creatorId)뿐이라는 사실을 KDoc 에 적어 뒀다.

---

## 2. 확정 시그니처 (compose-builder 계약)

```kotlin
// domain/repository/RideRepository.kt
suspend fun joinParty(partyId: Long): Ride          // memberId 제거
suspend fun leaveParty(partyId: Long)               // memberId 제거
suspend fun createParty(request: NewParty): Ride    // 변경 없음 — NewParty.hostId 여전히 필요

// domain/repository/DispatchRepository.kt
suspend fun getDriverLocation(partyId: Long): DriverLocation   // memberId 제거

// domain/repository/PlaceRepository.kt
suspend fun addFavoritePlace(place: Place)                     // userId 제거
suspend fun getFavoritePlaces(): List<FavoritePlace>           // userId 제거

// domain/repository/ReportRepository.kt — 시그니처 변경 없음
suspend fun report(request: NewReport): Long
suspend fun confirmCallResult(reportId: Long, called: Boolean)

// domain/model/NewReport.kt — 첫 필드가 사라졌다 (위치 인자 호출은 조용히 어긋난다)
data class NewReport(val partyId: Long, val latitude: Double? = null, val longitude: Double? = null)
```

`ChatRepository` · `AuthRepository` · `UserSession` 시그니처는 그대로다.

---

## 3. 깨지는 presentation 지점 (9곳 / 7파일) — 수정은 compose-builder

`./gradlew :presentation:compileDebugKotlin` 실측 오류 그대로.

| 파일 | 라인 | 현재 | 고칠 형태 |
|---|---|---|---|
| `feature/explore/JoinConfirmRoute.kt` | 72 | `repository.joinParty(partyId, userSession.currentUserId)` | `repository.joinParty(partyId)` |
| `feature/matching/MatchWaitingRoute.kt` | 87 | `repository.leaveParty(partyId, userSession.currentUserId)` | `repository.leaveParty(partyId)` |
| `feature/matching/RideDetailRoute.kt` | 61 | `repository.leaveParty(partyId, userSession.currentUserId)` | `repository.leaveParty(partyId)` |
| `feature/matching/DispatchStatusRoute.kt` | 89 | `dispatchRepository.getDriverLocation(partyId, userSession.currentUserId)` | `getDriverLocation(partyId)` |
| `feature/home/HomeRoute.kt` | 31 | `repository.getFavoritePlaces(userSession.currentUserId)` | `repository.getFavoritePlaces()` |
| `feature/home/DestinationRoute.kt` | 89 | `repository.getFavoritePlaces(userSession.currentUserId)` | `repository.getFavoritePlaces()` |
| `feature/home/DestinationRoute.kt` | 174 | `repository.addFavoritePlace(userSession.currentUserId, place)` | `repository.addFavoritePlace(place)` |
| `feature/chat/EmergencyRoute.kt` | 49 | `NewReport(userSession.currentUserId, partyId)` | `NewReport(partyId)` |

**정리 기회 (컴파일과 무관, 선택)**: 위 수정 후 `userSession` 이 ViewModel 안에서 **완전히 미사용**이 되는 곳이 6개다 — `JoinConfirmViewModel`, `MatchWaitingViewModel`, `DispatchStatusViewModel`, `HomeViewModel`, `DestinationViewModel`, `EmergencyViewModel`. 생성자·`factory`·Composable 파라미터·`MainNavGraph` 호출부까지 함께 걷어낼 수 있다. `RideDetailViewModel` 만은 `RideDetailRoute.kt:103` 에서 `isHost` 판정에 계속 쓴다 — 남겨야 한다.

**주의 (컴파일은 통과하지만 논리가 틀린 곳)**: `RideDetailRoute.kt:103` 의 `currentUserId = userSession.currentUserId.toString()` 은 고정 `"1"` 이다. 서버 멤버 목록은 이제 진짜 로그인 사용자 기준이라, id 가 1 이 아닌 계정으로 로그인하면 **방장 배지와 "나 제외" 필터가 엉뚱한 사람을 가리킨다.** §5 D-5 참조 — 이번 범위 밖이라 손대지 않았다.

---

## 4. 변경 파일

**data/remote**: `MatchingApi.kt`(join 경로·leave 더미 세그먼트·openParty 미전환 주석), `DispatchApi.kt`, `PlaceApi.kt`, `dto/ReportDtos.kt`(reporterId 삭제), `ReportMappers.kt`
**data/repository**: `RemoteRideRepository.kt`, `DummyRideRepository.kt`, `RemoteDispatchRepository.kt`, `RemotePlaceRepository.kt`
**domain**: `repository/{RideRepository,DispatchRepository,PlaceRepository}.kt`, `model/NewReport.kt`, `session/UserSession.kt`(KDoc — 남은 사용처 명시)
**test**: `remote/AuthenticatedPathContractTest.kt`(**신규**), `remote/{DispatchMappersTest,ReportMappersTest}.kt`, `repository/RemoteRideRepositoryTest.kt`, `session/SessionManagerTest.kt`

### 신규 테스트 `AuthenticatedPathContractTest` (6개) — 왜 만들었나

Retrofit 경로는 **애노테이션 안의 문자열**이라 컴파일러도 일반 매퍼 테스트도 잡지 못한다. 1차 연동에서 실제로 이 계층이 통째로 404 였는데도 빌드는 초록이었다. 그래서 리플렉션으로 애노테이션 값을 직접 못 박았다: 다섯 경로 리터럴 + "전환된 4개 Api 어디에도 `@Path`/`@Query("memberId"|"userId")` 가 없다". 백엔드가 또 경로를 바꾸면 **여기가 먼저 빨개진다.**

`ReportMappersTest` 에도 회귀 방지 한 줄을 추가했다 — 서버가 모르는 필드를 조용히 버리는 걸 실측했기 때문이다(`reporterId` 를 실어 보내도 400 이 아니라 정상 403 이 온다). 실패로 드러나지 않는 종류의 오류라 "본문에 `reporterId` 문자열이 없다"를 직접 검증한다.

---

## 5. 미해결 · 백엔드 요청

### 🔴 D-4 (신규) — `POST /users/me/favorite-places` 가 항상 500

```
POST /api/v1/users/me/favorite-places  {"placeName":"강남역","roadName":"…","latitude":37.4979,"longitude":127.0276}
→ 500 {"timestamp":…,"status":500,"error":"Internal Server Error","path":"/api/v1/users/me/favorite-places"}
```

`{code,message}` 규격이 아닌 **스프링 기본 에러 페이지** = 핸들러 안에서 처리되지 않은 예외. 같은 토큰의 GET 은 200 이라 인증 문제는 아니고, 요청 필드도 서버 record(`FavoritePlaceRequest(placeName, roadName, latitude, longitude)`)와 정확히 일치시킨 뒤에도 동일하다.

유력 원인: `FavoritePlaceEntity` 가 `@IdClass(FavoritePlaceId.class)` 인데 **`FavoritePlaceId` 에 public 무인자 생성자가 없다** (`FavoritePlaceId(Long, String)` 하나뿐). JPA 는 IdClass 인스턴스화에 무인자 생성자를 요구하므로 `save()` 시점에 터진다 — 조회 경로에는 인스턴스화가 없어 GET 만 멀쩡한 것과 일치한다. **앱에서 고칠 것은 없다.** 즐겨찾기 "추가"는 백엔드 수정 전까지 실패한다는 전제로 QA 할 것(도메인 KDoc 에도 적어 뒀다).

### 🔴 D-5 (신규) — 방 생성만 `creatorId` 라 로그인 주체와 어긋난다

`POST /matching/rooms` 만 본문 `creatorId` 를 쓰고, 합류·나가기·기사위치·신고는 토큰 주체를 쓴다. 앱은 `creatorId` 에 `UserSession.FIXED_MEMBER_ID = 1` 을 넣는다. 결과: **id 가 1 이 아닌 계정으로 로그인해 방을 만들면 서버는 그 방을 member 1 의 방으로 기록한다** — 만든 사람은 자기 방의 멤버가 아니라 이후 나가기가 403 `NOT_PARTY_MEMBER` 다.

지금 이게 안 터지는 건 **테스트 계정 `testuser1` 의 내부 id 가 우연히 1 이기 때문**이다(실측 확인: creatorId=1 로 만든 방을 testuser1 토큰으로 나가기 → 204 성공). 새 계정으로 가입해 테스트하면 바로 재현된다. 백엔드 R-2 로 요청.

### 백엔드 요청 사항

| # | 내용 |
|---|---|
| R-1 | `@DeleteMapping("/matching/leave/{partyId}/{memberId}")` 에서 `/{memberId}` 세그먼트 제거 (파라미터는 이미 `@LoginUser` — 경로만 남은 잔재) |
| R-2 | `POST /matching/rooms` 를 `@LoginUser` 로 전환하고 `OpenPartyRequest.creatorId` 제거 (D-5) |
| R-3 | 즐겨찾기 저장 500 수정 — `FavoritePlaceId` 에 public 무인자 생성자 추가 후 재확인 (D-4) |
| R-4 | 채팅 `X-User-Id` 헤더 → `@LoginUser` 전환 (전환되면 `UserSession.currentUserId` 를 완전히 삭제 가능) |
| R-5 | `ReportController.confirmCall` 의 `@LoginUser Long requesterId` 가 아직 소유자 검증에 쓰이지 않는다(주석에도 TODO). 남의 reportId 로 통화 결과를 덮어쓸 수 있다 |

### 이전 리포트에서 해소된 것

21번 §5 의 **D-3 은 전부 해소**됐다(이번 작업). 21번에서 "백엔드 워킹트리 기준" 으로 추정했던 두 가지는 실서버에서 **다르게 확정**됐으니 정정한다: `PATCH /reports/call-result` 로 옮겨간 게 아니라 **`{reportId}` 경로 유지**이고, 나가기 경로는 **세그먼트가 남아 있다.**

또한 21번의 "🟡 Authenticator 는 현재 실질적으로 발화하지 않는다" 제약도 **해소**됐다 — 도메인 API 가 이제 진짜로 401 을 내므로, 액세스 토큰 10분 만료 후 401 → reissue → 재시도 경로가 실기에서 발화한다. qa-verifier 가 검증 가능해졌다.

---

## 6. 실서버 스모크 (전부 실측)

로그인: `POST /api/v1/users/login {"loginId":"testuser1","password":"Passw0rd!"}` → 200, accessToken 획득.

| 호출 | 결과 |
|---|---|
| `POST /matching/rooms/1/join` (토큰) | 409 `ALREADY_JOINED_OTHER_PARTY` — 핸들러 도달, 경로 정상 |
| `POST /matching/rooms/1/join` (무토큰) | 401 `UNAUTHORIZED` |
| `DELETE /matching/leave/{n}/0` (토큰) | 204, 방 `currentMembers` 감소 확인 |
| `DELETE /matching/leave/1` (토큰) | 404 (기본) — 세그먼트 필수 근거 |
| `GET /dispatch/rides/1` (토큰/무토큰) | 409 `DRIVER_NOT_ASSIGNED` / 401 |
| `GET /users/me/favorite-places` (토큰/무토큰) | 200 `{"places":[]}` / 401 |
| `POST /users/me/favorite-places` (토큰) | **500** (D-4) |
| `POST /reports` `{partyId,latitude,longitude}` (토큰/무토큰) | 403 `REPORT_NOT_ALLOWED` "운행 중에만 신고할 수 있습니다." / 401 — reporterId 없이 검증 통과 확인 |
| `PATCH /reports/999/call-result` (토큰/무토큰) | 404 `REPORT_NOT_FOUND` / 401 — 경로 유지 확인 |
| `PATCH /reports/call-result` (토큰) | 404 (기본) — 옮겨가지 **않았음** 확인 |

**서버 상태 (qa-verifier 인계)**: 스모크 중 기존 방 1·2 가 소진되어(마지막 멤버가 나가면 방이 `CANCELED` 로 닫힌다) 새로 시드해 뒀다. 현재 ACTIVE 방 **2개**:

- `partyId=3` 부산대 정문 → 서면역 (creatorId 2, 1/3명)
- `partyId=4` 장전역 → 부산역 (creatorId 3, 1/3명)

둘 다 **testuser1(id 1) 이 아닌 멤버가 만든 방**이라 앱에서 합류 플로우를 그대로 탈 수 있다. 서버는 죽이지 않았다.

부수 발견: `POST /matching/rooms` 는 `capacity` 를 **1~3** 으로 제한한다(4 → 400 `INVALID_CAPACITY` "방 정원은 1~3명이어야 합니다."). 앱은 이미 `DestinationConfirmRoute.MAX_PARTY_CAPACITY = 3` 으로 `coerceIn(1, 3)` 하고 있어 **일치한다** — 조치 불필요, 서버 규칙이 바뀌면 이 상수도 같이 옮기면 된다.
