# 03 qa-verifier — 1차 정적 경계면 검증 (2026-08-24)

**범위:** 정적 경계면 검증만. 에뮬레이터 실기·`:app:assembleDebug`는 2차로 이월(compose-builder가 presentation/ 수정 중).
**정본:** 백엔드 `git show origin/develop:...` (커밋 소스). 프론트 `feature/api-integration` 워킹트리.
**백엔드 서버:** 기동/종료하지 않음. 실서버 응답 검증 없음 — 아래는 전부 소스 대조 결과.
**실행 검증:** `./gradlew :data:testDebugUnitTest --rerun-tasks` → BUILD SUCCESSFUL, **22 tests / 0 failures / 0 errors**
(UP-TO-DATE 캐시 통과가 아님을 확인하려고 `--rerun-tasks`로 강제 재실행 후 test-results XML을 직접 파싱했다.)

---

## 총평

| 검증 항목 | 결과 |
|---|---|
| 1. 엔드포인트 대조 (경로·메서드·파라미터·사용자 식별) | **통과 — 21/21 완전 일치, 불일치 0건** |
| 2. DTO 필드 대조 (필드명·타입·nullability) | **통과 — 14개 DTO 전수 일치, 불일치 0건** |
| 3. 매퍼 검증 (테스트 + nullable 안전성) | **통과 — 22/22, 널 처리 비대칭 없음** |
| 4. 01 보고서 플래그 재확인 | **4건 모두 독립 재확인 완료.** 단 에러 코드 관련 1건은 01 보고서가 **틀렸다** |
| 5. NetworkModule URL 조합 | **통과 — 슬래시 실수 없음** |

**차단(Blocker) 결함: 0건.** Medium 3건, Low 3건. 상세는 아래.

---

## 1. 엔드포인트 대조 — 통과 (21/21)

`origin/develop`의 전 컨트롤러에서 `@*Mapping`을 전수 추출해 프론트 3개 API 인터페이스와 1:1 대조했다.

### 매칭 — `MatchingApi.kt` ↔ `PartyController.java` (`@RequestMapping("/api/v1")`)

| 프론트 (파일:라인) | 백엔드 | 결과 |
|---|---|---|
| `MatchingApi.kt:17` GET `api/v1/matching/rooms` | `@GetMapping("/matching/rooms")` | ✅ |
| `MatchingApi.kt:20` GET `api/v1/matching/rooms/{partyId}` | `@GetMapping("/matching/rooms/{partyId}")` | ✅ |
| `MatchingApi.kt:23` POST `api/v1/matching/rooms` + `@Body` | `@PostMapping("/matching/rooms")` + `@RequestBody` | ✅ |
| `MatchingApi.kt:27` DELETE `api/v1/matching/leave/{partyId}/{memberId}` | `@DeleteMapping(...)` | ✅ |
| `MatchingApi.kt:33` POST `api/v1/matching/ready/{partyId}/{memberId}` | `@PostMapping(...)` | ✅ |
| `MatchingApi.kt:39` DELETE `api/v1/matching/ready/{partyId}/{memberId}` | `@DeleteMapping(...)` | ✅ |
| `MatchingApi.kt:45` POST `api/v1/matching/start/{partyId}/{memberId}` | `@PostMapping(...)` | ✅ |

백엔드 매핑 7개 중 7개 커버, 프론트에 존재하지 않는 경로 선언 0건.
`/api/v1` 프리픽스 반영 확인 — 01 보고서의 "기존 조회 연동이 404 상태였다"는 지적은 정확했다.

### 장소 — `PlaceApi.kt` ↔ `PlaceSearchController` / `FavoritePlaceController`

| 프론트 | 백엔드 | 결과 |
|---|---|---|
| `PlaceApi.kt:15` GET `api/v1/places` + `@Query("query")` | `@GetMapping("/places")` + `@RequestParam String query` | ✅ |
| `PlaceApi.kt:19` POST `api/v1/users/me/favorite-places` + `@Query("userId")` + `@Body` | `@PostMapping` + `@RequestBody` + `@RequestParam Long userId` | ✅ |
| `PlaceApi.kt:25` GET `api/v1/users/me/favorite-places` + `@Query("userId")` | `@GetMapping` + `@RequestParam Long userId` | ✅ |

### 채팅 — `ChatApi.kt` ↔ `ChatRoom` / `ChatRoomUser` / `ChatMessageController` (11/11)

| 프론트 | 백엔드 | `X-User-Id` | 결과 |
|---|---|---|---|
| `ChatApi.kt:22` GET `/chat-rooms/{id}` | `ChatRoomController @GetMapping("/{chatRoomId}")` | 불필요 → 미전송 | ✅ |
| `ChatApi.kt:25` POST `/chat-rooms` | `@PostMapping` (base) | 불필요 → 미전송 | ✅ |
| `ChatApi.kt:29` DELETE `/chat-rooms/{id}` | `@DeleteMapping("/{chatRoomId}")` | 불필요 → 미전송 | ✅ |
| `ChatApi.kt:34` GET `/chat-rooms/me` | `ChatRoomUserController @GetMapping("/me")` | 필요 → 전송 | ✅ |
| `ChatApi.kt:38` POST `/chat-rooms/{id}/users` | `@PostMapping("/{chatRoomId}/users")` | 필요 → 전송 | ✅ |
| `ChatApi.kt:45` DELETE `/chat-rooms/{id}/users` | `@DeleteMapping("/{chatRoomId}/users")` | 필요 → 전송 | ✅ |
| `ChatApi.kt:52` POST `/chat-rooms/{id}/users/read/{readMessageId}` | `@PostMapping(...)` | 필요 → 전송 | ✅ |
| `ChatApi.kt:61` GET `/chat-rooms/{id}/messages` + `cursor?`,`size` | `ChatMessageController @GetMapping` + `@RequestParam(required=false) Long cursor`, `@RequestParam(defaultValue="30") int size` | 필요 → 전송 | ✅ |
| `ChatApi.kt:70` GET `.../messages/after` + `cursor`(non-null),`size` | `@GetMapping("/after")` + `@RequestParam Long cursor`(필수) | 필요 → 전송 | ✅ |
| `ChatApi.kt:79` POST `.../messages` + `@Body` | `@PostMapping` + `@Valid @RequestBody` | 필요 → 전송 | ✅ |
| `ChatApi.kt:87` DELETE `.../messages/{messageId}` | `@DeleteMapping("/{messageId}")` | 필요 → 전송 | ✅ |

`cursor` nullability까지 백엔드 `required=false`/필수 구분과 정확히 일치한다 (`getMessages`는 `Long?`, `getMessagesAfter`는 `Long`).

### 도메인별 사용자 식별 차이 — 통과

01 보고서가 지적한 3가지 방식이 **전부 정확히 반영**됐다.

- 매칭 = 경로 변수 `{memberId}` → `MatchingApi.kt:27~49` 전부 `@Path("memberId")` ✅
- 장소 = 쿼리 `?userId=` → `PlaceApi.kt:21,26` `@Query("userId")` ✅ (경로 변수로 잘못 넣지 않았다)
- 채팅 = `X-User-Id` 헤더 → `ChatApi.kt` 8개 메서드에 `@Header("X-User-Id")`, 헤더가 필요 없는 방 생성/조회/삭제 3개에는 **의도적으로 미부착** ✅

가장 사고 나기 쉬운 지점인데 3종이 서로 섞이지 않았다.

---

## 2. DTO 필드 대조 — 통과 (14개 전수)

백엔드 record를 직접 읽고 컴포넌트 단위로 비교했다 (01 보고서 무참조 재검증).

| 프론트 DTO | 백엔드 record | 필드 수 | 결과 |
|---|---|---|---|
| `PartyDtos.kt:11` `PartyListResponse` | `PartyListResponse(List<PartyItem> list)` | 래퍼 키 `list` | ✅ |
| `PartyDtos.kt:15` `PartyItem` | `PartyItem(partyId, departure, destination, currentMembers, capacity, status)` | 6/6 | ✅ |
| `PartyDtos.kt:26` `PartyDetailResponse` | `PartyDetailResult(...15 components)` | 15/15 | ✅ |
| `PartyDtos.kt:44` `MemberInfo` | `MemberInfo(Long memberId, boolean isHost, Instant joinedAt)` | 3/3 | ✅ |
| `PartyDtos.kt:57` `OpenPartyRequestDto` | `OpenPartyRequest(...10)` | 10/10 | ✅ |
| `PartyDtos.kt:72` `OpenPartyResponse` | `OpenPartyResponse(...14)` | 14/14 (members 없음 — 프론트도 없음) | ✅ |
| `PlaceDtos.kt:9` `PlaceSearchListResponse` | `PlaceSearchListResponse(List list)` | 래퍼 키 `list` | ✅ |
| `PlaceDtos.kt:13` `PlaceSearchItem` | `PlaceSearchResponse(name, roadName, latitude, longitude)` | 4/4 | ✅ |
| `PlaceDtos.kt:24` `FavoritePlaceRequestDto` | `FavoritePlaceRequest(placeName, roadName, latitude, longitude)` | 4/4 | ✅ |
| `PlaceDtos.kt:33` `FavoritePlaceListResponse` | `FavoritePlaceListResponse(List places)` | 래퍼 키 **`places`** | ✅ |
| `PlaceDtos.kt:37` `FavoritePlaceItem` | `FavoritePlaceResponse(placeName, roadName, latitude, longitude, placeSequence)` | 5/5 | ✅ |
| `ChatDtos.kt:10` `ChatRoomResponse` | `ChatRoomResult(id, partyId, departure, destination, createdAt, status)` | 6/6 | ✅ |
| `ChatDtos.kt:22` `ChatRoomUserResponse` | `ChatRoomUserResult(chatRoomId, lastReadMessageId, notificationMuted, joinedAt)` | 4/4 | ✅ |
| `ChatDtos.kt:45` `ChatMessageResponse` | `ChatMessageResult(id, chatRoomId, userId, content, type, createdAt, deleted)` | 7/7 | ✅ |
| `ChatDtos.kt:57` `ChatMessageSliceResponse` | `ChatMessageSlice(messages, nextCursor, hasNext)` | 3/3 | ✅ |

**케이스/래퍼 함정 3개를 모두 통과했다:**
1. 장소 검색 응답 래퍼는 `list`, 즐겨찾기 응답 래퍼는 **`places`** — 서로 다른데 각각 맞게 선언됐다.
2. 장소 검색 필드는 `name`, 즐겨찾기 필드는 **`placeName`** — 매퍼(`PlaceMappers.kt:17`)에서만 흡수하고 DTO는 서버 이름을 그대로 유지했다. 올바른 방향이다.
3. `GET /chat-rooms/me`는 **래핑 없는 배열**(`List<ChatRoomUserResult>`) — 프론트도 `List<ChatRoomUserResponse>`(`ChatApi.kt:35`). unwrap 누락/과잉 없음.

**nullability 비대칭 — 없음.** 백엔드 record가 전부 박싱 타입(`Long`/`Integer`/`Double`)이라 null 가능한데,
프론트가 기본값 또는 `?`로 전부 방어한다. 도메인까지 널 여부가 일관되게 전파된다:

- `ChatRoomUserResult.lastReadMessageId`(null 가능) → DTO `Long? = null` → 도메인 `ChatRoomMembership.lastReadMessageId: Long?` ✅ 널을 끝까지 유지
- `ChatMessageSlice.nextCursor`(마지막 페이지 null) → DTO `Long? = null` → 도메인 `ChatMessagePage.nextCursor: Long?` ✅
- `PartyDetailResult.hostId`(null 가능) → DTO `Long? = null` → 도메인 `Ride.hostId: String?` ✅
- `createdAt`/`joinedAt` → DTO `String? = null` → 매퍼 `.orEmpty()` → 도메인 non-null `String` ✅ (널을 삼키는 지점이 매퍼 한 곳으로 모여 있다)

**요청 DTO는 전부 기본값 없음** → kotlinx의 `encodeDefaults=false`와 무관하게 모든 필드가 항상 직렬화된다.
백엔드 `Party.open`이 `Location(double, double)` 원시 타입으로 언박싱하므로 필드 누락 시 서버가 NPE(500)를 내는데,
프론트가 필드를 빠뜨릴 수 없는 구조라 이 경로는 막혀 있다. ✅

---

## 3. 매퍼 검증 — 통과

```
:data:testDebugUnitTest --rerun-tasks   BUILD SUCCESSFUL
  PartyMappersTest   9 tests, 0 failures, 0 errors
  ChatMappersTest    8 tests, 0 failures, 0 errors
  PlaceMappersTest   5 tests, 0 failures, 0 errors
```

코드 리뷰 결과:

- `PartyMappers.kt:53~57` — `hostId`, 좌표 4종을 nullable 그대로 통과시키고 `!!`나 강제 언박싱이 없다. ✅
- `PartyMappers.kt:68` — `OpenPartyResponse.toRide()`가 `hostId?.let { ... } ?: emptyList()`로 널을 안전 처리. ✅
- `ChatMappers.kt:15~25` — enum 변환이 `when(else -> 기본값)`. `valueOf` 미사용이라 서버가 enum 값을 추가해도 죽지 않는다. ✅
- `ChatMappers.kt:32,40,49` — `createdAt`/`joinedAt` `.orEmpty()`. ✅
- `PartyMappers.kt:13~19` — `partyStatusToRideStatus`가 백엔드 `PartyStatus{ACTIVE, COMPLETED, MATCHING, FINISHED, CANCELED}` 5개 값을 전부 커버하고 else 폴백도 있다. ✅
- `RemotePlaceRepository.kt:26` — 서버가 정렬을 보장하지 않으므로 `sortedBy { it.sequence }`로 앱에서 확정. 백엔드 `FavoritePlaceApplicationService.getList`가 실제로 정렬 없이 `findByUserId` 결과를 그대로 내려주는 것을 확인했다. 올바른 방어. ✅
- `RemoteChatRepository.kt:24` — N+1 루프에서 `runCatching { }.getOrNull()`로 개별 방 조회 실패를 흡수. 종료된 방 하나 때문에 목록 전체가 깨지지 않는다. ✅

`!!`, `checkNotNull`, `valueOf` 등 크래시 유발 패턴은 data/domain 전 파일에서 발견되지 않았다.

---

## 4. 01 보고서 플래그 재확인 (독립 재검증)

### 4-1. `join` 엔드포인트 부재 — **재확인, 01 보고서 정확**

`origin/develop`의 **전 컨트롤러 8개**에서 `@RequestMapping|@GetMapping|@PostMapping|@PutMapping|@PatchMapping|@DeleteMapping`을 전수 추출했다.
전체 매핑 목록에 `/join`은 **존재하지 않는다.**

`PartyApplicationService.java`에는 `join(Long partyId, Long memberId)`가 `@Transactional`로 **구현되어 있다.**
즉 서비스만 있고 컨트롤러 노출이 빠진 상태다. → 백엔드에 매핑 추가 PR 필요.
프론트가 경로를 추측하지 않고 `ApiNotAvailableException`을 던진 판단(`RemoteRideRepository.kt:30`)은 적절하다.

### 4-2. `GET /api/v1/matching/routes` 부재 — **재확인, 01 보고서 정확**

전 컨트롤러 매핑 목록에 `routes` 문자열 자체가 없다. `PartyController`에는 7개 매핑만 존재.
미구현 제외 판단 타당.

### 4-3. `MemberInfo`에 ready 상태 없음 — **재확인, 01 보고서 정확**

```java
// matching/application/dto/PartyDetailResult.java
public record MemberInfo(Long memberId, boolean isHost, Instant joinedAt) {}
```

`matching/domain/enums/MemberStatus.java`에 `NOT_READY, READY`가 **존재하지만** `MemberInfo`에 노출되지 않는다.
`PartyDetailResult.from()`도 `m.getMemberId()`, `isHost`, `joinedAt`만 매핑한다.
→ MatchWaiting 화면에서 "누가 준비했는지" 표시 불가. 백엔드 노출 요청 필요. **확정.**

### 4-4. 응답 공통 래퍼(envelope) 유무 — **재확인. 성공 응답에는 래퍼 없음**

`origin/develop` 전 소스에서 `ResponseBodyAdvice`는 **0건**, `@RestControllerAdvice`는 2건뿐이다:
- `chat/presentation/ChatExceptionHandler.java` — `basePackages = "team.codingforest.moyeota.chat"`로 **채팅 패키지에만** 적용, **예외 응답만** 변환
- `chat/presentation/StompExceptionHandler.java` — STOMP 전용

성공 응답을 감싸는 `ApiResponse`류 클래스는 존재하지 않는다.
컨트롤러가 `ResponseEntity<PartyListResponse>` 등 DTO를 직접 반환하므로 **프론트의 unwrap 없는 직접 매핑이 옳다.** ✅

다만 **에러 응답 shape은 도메인마다 다르다** (아래 결함 D-1 참조).

---

## 5. NetworkModule URL 조합 — 통과

```
AppContainer.kt:16   NetworkModule.create("http://10.0.2.2:8080/")   ← 후행 슬래시 있음
MatchingApi.kt:17    @GET("api/v1/matching/rooms")                   ← 선행 슬래시 없음
                     → http://10.0.2.2:8080/api/v1/matching/rooms    ✅
```

- baseUrl 후행 `/` 있음 → Retrofit의 `baseUrl must end in /` IllegalArgumentException 회피. ✅
- 상대 경로 전부 선행 `/` 없음 (`api/v1/...`) → baseUrl 경로가 잘려나가는 실수 없음. 3개 API 26개 선언 전수 확인. ✅
- `10.0.2.2` = 에뮬레이터에서 호스트 localhost. 실기 검증 시 적절. ✅
- `AndroidManifest.xml` — `android.permission.INTERNET` 있음 + `android:usesCleartextTraffic="true"` 있음 → HTTP 평문 통신 차단 안 됨. ✅ (이게 빠지면 모든 호출이 실패하는데 잘 들어가 있다)
- `NetworkModule.kt:29` — Retrofit 인스턴스 1개를 3 API가 공유. OkHttp 커넥션 풀/스레드풀 중복 없음. ✅
- **204/본문없음 응답 처리 검증(추측 아님):** `retrofit-3.0.0.jar` 내부를 직접 열어 `retrofit2/BuiltInConverters$UnitResponseBodyConverter.class` **존재를 확인**했다. `suspend fun ...()` (Unit 반환) 선언 8건이 정상 동작한다. ✅

---

## 결함 목록

### D-1 [Medium] 매칭·장소 도메인의 검증 실패는 400이 아니라 **500**으로 내려온다 — 01 보고서 및 코드 주석이 틀렸다

01 보고서 3절: "400 케이스: 이름 중복 등록, 10개 초과"
`domain/src/main/kotlin/com/moyeota/domain/model/NewParty.kt:4`: "서버 검증 규칙(**위반 시 400**): capacity 0~3, radius 100~500, 좌표는 한국 범위 내"

**실제:** 이 검증들은 전부 `IllegalArgumentException`을 던진다.

- `place/application/FavoritePlaceApplicationService.java` → `throw new IllegalArgumentException("이미 등록된 장소입니다.")`, `"...최대 10개까지만..."`
- `matching/domain/Capacity.java`, `Radius.java`, `Location.java` → 전부 `IllegalArgumentException`
- `matching/application/PartyApplicationService.java` → `getParty()` 미존재 방, `validateNotInOngoingParty()` 중복 참여 → `IllegalArgumentException` (소스에 `// TODO 예외처리 해야함` 주석이 그대로 남아 있다)

그런데 `@RestControllerAdvice`는 **채팅 패키지에만** 걸려 있다(`basePackages = "team.codingforest.moyeota.chat"`).
따라서 매칭·장소의 `IllegalArgumentException`은 어떤 핸들러도 잡지 못하고 **HTTP 500 + Spring 기본 에러 바디**(`{timestamp,status,error,path}`)로 나간다.

**영향:** ViewModel이 `HttpException.code() == 400`으로 "정원 초과"·"이미 등록된 장소" 같은 사용자 안내를 분기하면 **절대 동작하지 않는다.**
채팅만 `ErrorResponse{code, message}`를 주고, 매칭·장소는 사용자에게 보여줄 메시지가 아예 없다. 도메인 간 에러 계약이 비대칭이다.

**수정 요청 (api-integrator):** `NewParty.kt:4` 주석의 "위반 시 400"을 "위반 시 500(백엔드 예외 핸들러 미구현)"으로 정정.
**백엔드 요청:** 매칭·장소 도메인에 `@RestControllerAdvice` 추가 + `IllegalArgumentException` → 400 + `ErrorResponse` 매핑 (채팅과 동일한 shape으로 통일).
**compose-builder 인계:** 2차 화면 연결 시 매칭/장소 액션 실패는 상태코드 분기 없이 일반 에러 메시지로 처리할 것.

### D-2 [Medium] `capacity` 상한 3 — 클라이언트 가드가 없어 4명 방 생성 시 원인 불명 500

```java
// matching/domain/Capacity.java
public Capacity(int value) { if (value < 0 || value > 3) throw new IllegalArgumentException(...); }
```

`NewParty.capacity`(`NewParty.kt:13`)에는 범위 제약이 없고, `RemoteRideRepository.createParty`(`RemoteRideRepository.kt:25`)에도 사전 검증이 없다.
택시 정원 감각으로 UI가 4를 넘기면 D-1 때문에 메시지 없는 500이 뜬다.

**수정 요청 (api-integrator):** `NewParty`에 `init { require(capacity in 0..3) }` 또는 `MAX_CAPACITY = 3` 상수 노출.
`departureRadius`/`destinationRadius`도 동일(백엔드 100~500, 프론트 기본값 500은 유효 범위 안이라 기본값 사용 시엔 안전).

### D-3 [Medium] `Instant` 필드의 JSON 표현 미검증 — 숫자로 내려오면 역직렬화 예외

백엔드는 **Spring Boot 4.1.0**(`build.gradle`)이고 `createdAt`/`joinedAt`이 `java.time.Instant`다.
`application.yaml`·`application-prod.yaml` 어디에도 `spring.jackson.*` 설정이 없고, 커스텀 `ObjectMapper`/`JsonMapper` 빈도 `origin/develop` 전 소스에 **0건**이다.

프론트는 `createdAt: String?`(`PartyDtos.kt:40,86`, `ChatDtos.kt:15,27,51`)으로 받는다.
Spring Boot 기본값(`WRITE_DATES_AS_TIMESTAMPS` 비활성) 대로면 ISO-8601 문자열이라 정상이지만,
만약 epoch 숫자로 내려오면 kotlinx-serialization이 number→String 변환을 거부해 **`GET /matching/rooms/{id}`와 모든 채팅 API가 예외로 죽는다.**
현재 테스트는 전부 ISO 문자열 픽스처만 쓰고 있어 이 경우를 잡지 못한다.

**2차 실기 최우선 확인 항목.** 백엔드 기동 후 `curl -s localhost:8080/api/v1/matching/rooms/1 | grep createdAt` 한 줄이면 판정된다.
숫자로 나오면 `createdAt`을 `JsonPrimitive` 수용 커스텀 serializer로 바꾸거나 백엔드에 `write-dates-as-timestamps: false` 명시를 요청.

### D-4 [Low] `HttpLoggingInterceptor.Level.BODY`가 빌드 타입과 무관하게 항상 켜져 있다

`NetworkModule.kt:18` — `BuildConfig.DEBUG` 가드가 없다. 릴리즈 빌드에서도 채팅 메시지 본문 전체와 향후 붙을 인증 토큰이 logcat에 남는다.
**수정 요청 (api-integrator):** 레벨을 파라미터로 받거나 `data` 모듈에 `buildConfig = true`를 켜고 `if (BuildConfig.DEBUG)` 분기.

### D-5 [Low] `NetworkModule`의 `createMatchingApi`/`createPlaceApi`/`createChatApi` 3개는 데드코드

`NetworkModule.kt:43~47`. `app`·`presentation` 전체 grep 결과 호출부 0건(`AppContainer.kt:16`의 `create()`만 사용).
남겨두면 나중에 누가 호출해 Retrofit·OkHttp 인스턴스가 API마다 중복 생성된다(`create()`를 만든 이유가 바로 그것).
**수정 요청 (api-integrator):** 3개 함수 삭제.

### D-6 [Low] 로컬 백엔드 장소 검색은 `KAKAO_API_KEY` 없이는 동작하지 않는다

`application.yaml` — `kakao.api.key: ${KAKAO_API_KEY:}` (기본값 빈 문자열).
`PlaceSearchApplicationService`는 `PlaceSearcher`(카카오 API 호출)에 위임한다.
2차 실기에서 DestinationScreen 검색이 실패하면 프론트 결함이 아니라 환경 변수 미설정일 수 있다. 실기 전 사용자에게 키 설정 여부 확인 필요.

---

## 미검증 (2차 이월)

- **실서버 응답 검증** — 백엔드 미기동. 본 리포트의 모든 shape은 `origin/develop` 소스 대조 결과이며 실제 JSON 바이트를 본 적이 없다. D-3이 여기 걸려 있다.
- **`:app:assembleDebug`** — compose-builder가 presentation/ 수정 중이라 미실행. 지시대로 `:data:testDebugUnitTest`만 수행.
- **presentation ↔ Repository 경계면** — `MainNavGraph.kt`, `ExploreRoute.kt`, `RideDetailRoute.kt`가 현재 수정 중이라 대조 보류. 2차에서 다음을 확인할 것:
  - `MainNavGraph`가 `rideRepository`만 받는 상태였다(01 보고서 2절) — place/chat 화면 연결 시 시그니처 확장 여부
  - Routes.kt 상수 ↔ composable 등록 ↔ navigate 호출 3자 대조
  - Explore가 화면 재진입 시에만 재로드되는 제약(01 보고서 4절) — `leaveParty` 성공 후 목록 갱신 누락 여부
  - `ApiNotAvailableException`(join)을 JoinConfirmScreen이 잡아서 안내 메시지로 표시하는지
- **에뮬레이터 실기·스크린샷** — 2차 별도 지시 대기.

---

## 정보성 (결함 아님, 인지 필요)

- `RemoteRideRepository.kt:17~19` — `getNearbyParties()`/`getMyRides()`는 여전히 `DummyRideRepository`에 위임한다. 홈 화면은 더미 데이터를 그린다. 백엔드에 대응 엔드포인트가 없으므로 현 시점 타당한 선택.
- `RemoteChatRepository.kt:22~26` — 채팅 목록 N+1(방 개수만큼 상세 조회) 확인. `GET /chat-rooms/me`가 방 이름을 안 주는 것이 원인임을 백엔드 소스로 확인했다. 백엔드가 목록에 방 정보를 포함해주면 제거 가능.
- 인증 부재 — `FixedUserSession(1L)`. 백엔드 `PartyController.java:27`에도 `// TODO 추후 인증 관련 JWT 헤더에서 memberId 추출`가 그대로 있어 양쪽 전제가 일치한다.
- `PartyMappers.kt:44` — 닉네임을 `"멤버 {memberId}"`로 채운다. 서버에 프로필이 없어 불가피.
