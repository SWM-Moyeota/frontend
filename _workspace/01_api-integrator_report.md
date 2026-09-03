# 01 · api-integrator — 백엔드 계약 갱신 + 기사/위치/경로/신고 연동

날짜: 2026-08-30 · 브랜치: `feature/naver-map`
스펙 출처: `../backend` (브랜치 `feature/driver-report`, HEAD `99ade11`) **컨트롤러·DTO 소스 직접 확인**
검증: `:domain:compileDebugKotlin` ✅ · `:data:compileDebugKotlin` ✅ · `:data:testDebugUnitTest` ✅ **41 tests / 0 failures**
**실서버 미검증** — 백엔드 서버를 띄우지 않았다. 아래 응답 shape은 전부 컨트롤러/record 소스 기준이다.

---

## 1. compose-builder 가 쓰는 계약 (확정 시그니처 전문)

### `domain/repository/RideRepository.kt`

```kotlin
interface RideRepository {
    fun getNearbyParties(): List<Ride>
    fun getMyRides(): List<Ride>

    suspend fun getParties(): List<Ride>
    suspend fun getPartyDetail(partyId: Long): Ride
    suspend fun createParty(request: NewParty): Ride

    // ▼ 변경: 반환형이 Unit → Ride (합류 응답이 곧 방 상세)
    suspend fun joinParty(partyId: Long, memberId: Long): Ride

    suspend fun leaveParty(partyId: Long, memberId: Long)

    // ▼ 신규
    suspend fun getAssignedDriver(partyId: Long): AssignedDriver
    suspend fun previewRoute(
        departureLat: Double, departureLng: Double,
        destinationLat: Double, destinationLng: Double,
    ): RouteEstimate            // ※ 현재 항상 ApiNotAvailableException — 4절 참고

    // ▼ 삭제: setReady / cancelReady / startMatching
}
```

### `domain/repository/DispatchRepository.kt` (신규)

```kotlin
interface DispatchRepository {
    suspend fun getDriverLocation(partyId: Long, memberId: Long): DriverLocation
}
```

### `domain/repository/ReportRepository.kt` (신규)

```kotlin
interface ReportRepository {
    suspend fun report(request: NewReport): Long        // 반환값 = reportId
    suspend fun confirmCallResult(reportId: Long, called: Boolean)
}
```

### 도메인 모델

```kotlin
// domain/model/Dispatch.kt (신규)
data class AssignedDriver(val seats: Int?, val plateNumber: String, val vehicleType: String)
data class DriverLocation(val latitude: Double, val longitude: Double)
data class RouteEstimate(val estimatedFare: Int, val estimatedMinutes: Int, val encodedPath: String)

// domain/model/NewReport.kt (신규)
data class NewReport(
    val reporterId: Long, val partyId: Long,
    val latitude: Double? = null, val longitude: Double? = null,
)

// domain/model/Ride.kt — 기존 필드 유지 + 아래 4개 추가 (모두 기본값 null, 기존 호출부 무영향)
val estimatedFare: Int?      = null   // 경로 전체 택시 요금(원)
val estimatedMinutes: Int?   = null   // 예상 소요 분
val routePolyline: String?   = null   // Google Encoded Polyline (1e5, lat→lng) — 지도 경로용
val driverId: Long?          = null   // 배정 기사 id, 미배정이면 null
```

### DI — `app/AppContainer.kt`

```kotlin
val dispatchRepository: DispatchRepository = RemoteDispatchRepository(apis.dispatch)
val reportRepository:  ReportRepository  = RemoteReportRepository(apis.report)
```
`NetworkModule.Apis` 에 `dispatch`, `report` 추가됨. `userSession.currentUserId` 는 여전히 고정 `1L`.

---

## 2. 엔드포인트 ↔ DTO ↔ 도메인 매핑표

| # | 메서드 · 경로 | 요청 DTO | 응답 DTO (백엔드 record) | 도메인 | Repository 메서드 |
|---|---|---|---|---|---|
| 1 | `POST /api/v1/matching/rooms` | `OpenPartyRequestDto` | `OpenPartyResponse` | `Ride` | `createParty` |
| 2 | `POST /api/v1/matching/rooms/{partyId}/{memberId}/join` **신규** | — (본문 없음) | `PartyDetailResponse` = `PartyDetailResult` | `Ride` | `joinParty` |
| 3 | `GET /api/v1/matching/rooms` | — | `PartyListResponse` | `List<Ride>` | `getParties` |
| 4 | `GET /api/v1/matching/rooms/{partyId}` | — | `PartyDetailResponse` | `Ride` | `getPartyDetail` |
| 5 | `DELETE /api/v1/matching/leave/{partyId}/{memberId}` | — | 204 | — | `leaveParty` |
| 6 | `GET /api/v1/matching/rooms/{partyId}/driver` **신규** | — | `DriverSummaryResponse` = `driver/api/DriverSummary` | `AssignedDriver` | `getAssignedDriver` |
| 7 | `GET /api/v1/dispatch/rides/{partyId}/{memberId}` **신규** | — | `DriverLocationResponse` | `DriverLocation` | `DispatchRepository.getDriverLocation` |
| 8 | `POST /api/v1/reports` **신규** | `ReportRequestDto` | `ReportResponse` | `Long` (reportId) | `ReportRepository.report` |
| 9 | `PATCH /api/v1/reports/{reportId}/call-result` **신규** | `CallResultRequestDto` | 204 | — | `ReportRepository.confirmCallResult` |
| 10 | `GET /api/v1/matching/routes` | `RouteRequestDto` | `RouteEstimateResponse` = `matching/domain/RouteEstimate` | `RouteEstimate` | `previewRoute` — **호출 불가, 4절** |

### 실제 응답 shape (컨트롤러/record 기준)

```jsonc
// #2, #4 — PartyDetailResult
{ "id":7, "departureLat":37.5665, "departureLng":126.978,
  "destinationLat":37.4979, "destinationLng":127.0276,
  "departure":"서울시청", "destination":"강남역",
  "capacity":3, "currentMembers":2, "departureRadius":500, "destinationRadius":500,
  "status":"DRIVER_ASSIGNED", "createdAt":"2026-08-30T09:00:00Z",
  "members":[ {"memberId":1,"joinedAt":"2026-08-30T09:00:00Z"} ],
  "estimateFare":9600, "estimateTime":14, "route":"_p~iF~ps|U", "taxiDriverId":42 }

// #6 — DriverSummary
{ "seats":4, "plateNumber":"12가 3456", "type":"쏘나타" }

// #7 — DriverLocationResponse (longitude 가 먼저 선언돼 있음)
{ "longitude":126.978, "latitude":37.5665 }

// #8 — ReportRequest → ReportResponse
{ "reporterId":1, "partyId":7, "latitude":37.5665, "longitude":126.978 }  →  { "reportId":55 }

// #9 — CallResultRequest
{ "called": true }
```

### 매핑 규칙 (PartyMappers)

- `PartyStatus → RideStatus`: `ACTIVE→RECRUITING`, `COMPLETED→MATCHED`, `MATCHING→DISPATCHING`,
  **`DRIVER_ASSIGNED→DISPATCHING`(신규)**, **`IN_RIDE→ONGOING`(신규)**, `FINISHED`/`CANCELED→COMPLETED`.
  `RideStatus` enum 자체는 변경 없음 → presentation 의 `when` 분기 영향 없음.
- 요금: `totalFare = estimateFare`, `farePerPerson = estimateFare / capacity` (정원 0 또는 요금 null 이면 0).
  목록 응답에는 요금 필드가 아예 없어 목록 카드는 여전히 0 이다.
- `route`(폴리라인)·좌표는 **상세 응답에만** 있다. 목록은 `partyId/출발/도착/인원/정원/상태` 6개뿐.

---

## 3. ⚠️ 발견한 계약 불일치 3건 (기존 코드가 틀려 있었음)

1. **방 생성 요청의 필드명이 틀려 있었다 — 실제 장애 원인급.**
   백엔드 `OpenPartyRequest` 의 첫 필드는 `creatorId` 인데 앱은 `hostId` 로 보내고 있었다.
   서버가 `creatorId=null` 로 받아 `Party.open` 에서 실패했을 것. → `OpenPartyRequestDto.creatorId` 로 수정.
   (`backend/http/matching.http` 는 `hostId` + `/api/matching` 옛 경로라 **신뢰 불가**. 컨트롤러가 진실.)
2. **`hostId` / `members[].isHost` 는 서버에 존재하지 않는다.**
   `PartyDetailResult` 에도 `MemberInfo` 에도 그 필드가 없다(방장 개념이 매칭 도메인에서 빠짐).
   기존 DTO 의 `hostId`, `@JsonNames("host") isHost` 는 항상 기본값이었다.
   → 앱이 **`joinedAt` 이 가장 이른 멤버를 방 생성자로 추정**해 `Ride.hostId` 를 채우도록 변경(테스트 있음).
   `Ride.hostId` 자체는 유지했으므로 `RideDetailRoute.kt:119` 등 기존 참조는 그대로 컴파일된다.
3. **기사 이름·별점이 백엔드에 없다.**
   작업 정의서는 "기사명·차량·별점"을 요구하지만 `DriverSummary` 는 `(seats, plateNumber, type)` 뿐이다.
   `driver/api/DriverInfo` 도 `(plateNumber, type, seats)` 로 동일. → **백엔드 필드 추가 요청 필요**.
   현재 배차 현황 화면에는 차량번호/차종/좌석수만 표시 가능하다.

---

## 4. ⚠️ `GET /api/v1/matching/routes` — 앱에서 호출 불가 (백엔드 수정 필요)

```java
@GetMapping("/matching/routes")
public ResponseEntity<RouteEstimate> preView(@RequestBody RouteRequest req)   // ← GET + @RequestBody
```

OkHttp 5.1.0 은 GET 요청에 본문 싣는 것을 **`Request.Builder` 와 공개 생성자 양쪽에서** 거부한다.
data 모듈에서 임시 테스트로 직접 확인했다(확인 후 테스트는 삭제):

```
IllegalArgumentException: method GET must not have a request body.   (Builder / 생성자 모두)
```

Retrofit 역시 `@GET` 에 `@Body` 를 붙이면 파싱 단계에서 거부한다. 우회로가 없다.

- **처리**: `MatchingApi` 에는 선언하지 않고(주석으로 대체 선언 2안을 남겨둠),
  `RemoteRideRepository.previewRoute` 가 `ApiNotAvailableException("경로 조회 API를 아직 앱에서 호출할 수 없어요")` 를 던진다.
- **백엔드 요청**: `@RequestParam` 4개로 바꾸거나 `@PostMapping` 으로 변경. 고치면 `MatchingApi.kt` 주석만 풀면 된다.
- **우회 가능**: 지도 경로 표시는 `getPartyDetail(...).routePolyline` (같은 형식의 인코딩 폴리라인)으로 **이미 충족된다.**
  compose-builder 는 `previewRoute` 대신 이 값을 쓰면 된다.

---

## 5. ready/start 제거로 깨지는 presentation 참조 지점 (수정은 compose-builder 몫)

`./gradlew :presentation:compileDebugKotlin` 결과 — **오류 3건, 전부 한 파일**:

```
e: MatchWaitingRoute.kt:69:24  Unresolved reference 'setReady'.
e: MatchWaitingRoute.kt:71:24  Unresolved reference 'cancelReady'.
e: MatchWaitingRoute.kt:78:20  Unresolved reference 'startMatching'.
```

### 함께 정리해야 할 연쇄 지점

`presentation/.../feature/matching/MatchWaitingRoute.kt`
| 라인 | 내용 | 조치 |
|---|---|---|
| 36, 40 | `ActionState.ready` (로컬 추적 주석) | 제거 |
| 42 | `ActionState.matchingStarted` | 제거 또는 status==MATCHING 파생으로 대체 |
| 66–75 | `fun toggleReady()` | **함수 통째 제거** |
| 77–81 | `fun startMatching()` | **함수 통째 제거** |
| 140–141 | `LaunchedEffect(action.matchingStarted) { onMatchingStarted() }` | `ride.status == DISPATCHING` 관찰로 대체 |
| 158 | `isHost = ...` | 매칭 시작 버튼이 없어지므로 사실상 불필요 |
| 159 | `isReady = action.ready` | 제거 |
| 164 | `onToggleReady = viewModel::toggleReady` | 제거 |
| 165 | `onStartMatching = viewModel::startMatching` | 제거 |

`presentation/.../feature/matching/MatchWaitingScreen.kt` (컴파일은 되지만 죽은 UI)
| 라인 | 내용 |
|---|---|
| 80–81 | KDoc 의 `/matching/ready`, `/matching/start` 설명 |
| 93–94, 99–100 | `isHost`, `isReady`, `onToggleReady`, `onStartMatching` 파라미터 |
| 148 | `" · 나는 준비 완료"` 라벨 |
| 201–204 | `if (isHost) { "매칭 시작하기" 버튼 }` |
| 216–223 | 「준비 완료 / 준비 취소」 버튼 |

**새 흐름**: 정원이 차면 서버가 `Party.startMatching()` 을 스스로 호출한다
(`PartyApplicationService.open` / `.join` 내부). 앱은 트리거하지 않고 `getPartyDetail` 폴링으로
`status: ACTIVE → COMPLETED → MATCHING → DRIVER_ASSIGNED` 전이만 관찰하면 된다.

### 깨지지 않은 것
- `JoinConfirmRoute.kt:71` `repository.joinParty(...)` — 반환형이 `Unit → Ride` 로 바뀌었지만
  Kotlin 에서 반환값 무시는 합법이라 **컴파일 영향 없음**. 다만 `catch (ApiNotAvailableException)` 분기(73행)는
  이제 절대 안 타므로 정리 권장. 합류 성공 시 응답 `Ride` 를 그대로 MatchWaiting 에 넘기면 재조회 1회를 아낀다.

---

## 6. 액션 성공 후 갱신 대상

| 액션 | 성공 후 갱신 |
|---|---|
| `createParty` | 응답 `Ride` 를 그대로 MatchWaiting 에 전달(재조회 불필요) |
| `joinParty` | 응답 `Ride` 가 곧 상세 — **재조회 불필요**. Explore 목록은 화면 재진입 시 재로드 |
| `leaveParty` | Explore 목록 재조회 |
| `report` | 반환된 `reportId` 를 화면 상태에 보관 → 다이얼 복귀 후 `confirmCallResult` 에 사용 |
| 매칭 진행 상태 | `getPartyDetail` 폴링 (서버가 자동 전이) |
| 기사 위치 | `getDriverLocation` 폴링 |

---

## 7. 서버측 실패 동작 (ViewModel 메시지 변환용)

**matching/dispatch/report 도메인에는 `@RestControllerAdvice` 가 없다** (chat 패키지에만 있음).
따라서 `IllegalArgumentException` 은 Spring 기본 처리로 **500 + `{"timestamp","status","error","path"}`**
형태가 되고 한국어 사유 메시지가 본문에 담기지 않는다. 앱에서 원인별 분기가 불가하므로
일반 메시지로 처리하고, 백엔드에 전역 예외 핸들러 추가를 요청해야 한다.

| 호출 | 서버가 거절하는 조건 |
|---|---|
| `joinParty` | 이미 참여 중인 다른 방 있음 / 마감된 방 / 정원 초과 / 이미 참여한 방 |
| `createParty` | 이미 참여 중인 방 있음 / 출발지=도착지 / capacity·radius·좌표 검증 위반 |
| `getAssignedDriver` | `taxiDriverId == null` (기사 미배정) → **status 가 DRIVER_ASSIGNED·IN_RIDE 일 때만 호출** |
| `getDriverLocation` | 내가 방 멤버 아님 / 기사 미배정 / **기사가 아직 위치 미보고** ← 정상 상황이므로 폴링 중 실패는 화면을 에러로 덮지 말 것 |
| `report` | `partyId == null` 이거나 신고자가 그 방에서 **IN_RIDE 상태가 아님** → 탑승 중에만 신고 가능 |
| `confirmCallResult` | 이미 통화 여부가 확정된 신고 (신고당 1회만) |

---

## 8. 산출 파일

**신규**
```
domain/src/main/kotlin/com/moyeota/domain/model/Dispatch.kt
domain/src/main/kotlin/com/moyeota/domain/model/NewReport.kt
domain/src/main/kotlin/com/moyeota/domain/repository/DispatchRepository.kt
domain/src/main/kotlin/com/moyeota/domain/repository/ReportRepository.kt
data/src/main/kotlin/com/moyeota/data/remote/DispatchApi.kt
data/src/main/kotlin/com/moyeota/data/remote/ReportApi.kt
data/src/main/kotlin/com/moyeota/data/remote/DispatchMappers.kt
data/src/main/kotlin/com/moyeota/data/remote/ReportMappers.kt
data/src/main/kotlin/com/moyeota/data/remote/dto/DispatchDtos.kt
data/src/main/kotlin/com/moyeota/data/remote/dto/ReportDtos.kt
data/src/main/kotlin/com/moyeota/data/repository/RemoteDispatchRepository.kt
data/src/main/kotlin/com/moyeota/data/repository/RemoteReportRepository.kt
data/src/test/kotlin/com/moyeota/data/remote/DispatchMappersTest.kt        (2)
data/src/test/kotlin/com/moyeota/data/remote/ReportMappersTest.kt          (5)
data/src/test/kotlin/com/moyeota/data/repository/RemoteRideRepositoryTest.kt (4)
```

**수정**
```
domain/src/main/kotlin/com/moyeota/domain/model/Ride.kt
domain/src/main/kotlin/com/moyeota/domain/repository/RideRepository.kt
data/src/main/kotlin/com/moyeota/data/remote/MatchingApi.kt
data/src/main/kotlin/com/moyeota/data/remote/PartyMappers.kt
data/src/main/kotlin/com/moyeota/data/remote/NetworkModule.kt
data/src/main/kotlin/com/moyeota/data/remote/dto/PartyDtos.kt
data/src/main/kotlin/com/moyeota/data/repository/RemoteRideRepository.kt
data/src/main/kotlin/com/moyeota/data/repository/DummyRideRepository.kt
data/src/test/kotlin/com/moyeota/data/remote/PartyMappersTest.kt           (17)
app/src/main/kotlin/com/moyeota/app/AppContainer.kt
```

presentation 은 **한 줄도 건드리지 않았다.**

---

## 9. 미검증 / 백엔드 요청 목록

- 🚩 **실서버 미검증** — 백엔드를 기동하지 않았다. 모든 shape 은 소스 기준. qa-verifier 실기 검증 필요.
- 🚩 `joinedAt`·`createdAt` 을 ISO-8601 **문자열**로 가정했다(Spring Boot 기본이 타임스탬프 직렬화를 끔).
  백엔드가 Jackson 3(`tools.jackson.*`)으로 올라갔으므로 실서버에서 숫자로 내려오면 역직렬화가 깨진다 — 우선 확인 대상.
- 🚩 `estimateFare` 를 **경로 전체 요금**으로 해석했다(`NaverDirectionsClient` 가 `summary.taxiFare()` 를 그대로 담음).
  1인당 요금은 앱이 `정원`으로 나눈다. 서버 의도와 다르면 알려달라.
- **백엔드 요청 1**: `GET /matching/routes` 를 `@RequestParam` 또는 `POST` 로 변경 (4절).
- **백엔드 요청 2**: `DriverSummary` 에 기사 이름·별점 추가 (3절-3).
- **백엔드 요청 3**: matching/dispatch/report 전역 예외 핸들러 — 현재 400 이어야 할 것이 500 이고 사유가 본문에 없다 (7절).
- **백엔드 요청 4**(기존): 승객용 FCM 토큰 등록 엔드포인트 부재.

---

## 10. qa-verifier 에게 — 교차 검증 요청

1. `./gradlew :data:testDebugUnitTest` 재현 (41 tests)
2. compose-builder 의 MatchWaiting 수정 후 `:app:assembleDebug`
3. 실서버 기동 후 shape 대조 — **최우선 3건**: (a) `creatorId` 로 방 생성 성공 여부,
   (b) `joinedAt` 타입이 문자열인지, (c) `join` 응답에 `route`/`estimateFare`/`taxiDriverId` 가 실제로 담기는지
4. 기사 미배정 상태에서 `getAssignedDriver` / `getDriverLocation` 호출 시 실제 상태코드와 본문 확인
