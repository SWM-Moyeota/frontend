# 운영 백엔드 ↔ 앱 계약 전수 대조 (출시 직전 최종 관문)

- 일시: 2026-10-14
- 앱: `/Users/sungyoon/Desktop/moyeota/frontend` (branch `ci/version-code`)
- 운영: `https://api.moyeota.p-e.kr/` — `app/build.gradle.kts:37` 기본 base URL 과 일치
- 판정 근거 ①: 운영 살아있는 OpenAPI (`GET /v3/api-docs`, 경로 37개). **보고서 작성 시점에 재취득해 저장본과 동일함을 확인**(경로 집합 identical)
- 판정 근거 ②: 백엔드 `origin/main` 소스. **주의 — 로컬 백엔드 체크아웃은 `feature/driver-withdrawal-retention`(26f78c4)으로 `origin/main`(284a950)보다 과거다. 본 대조는 전부 `git show origin/main:<path>` 로 읽었다.**
- 운영 서버에 쓰기 요청(POST/PATCH/DELETE)은 한 건도 보내지 않았다. GET 은 `/api/v1/config`, `/v3/api-docs` 둘뿐.

## 0. 한 줄 결론

**출시 차단 등급 불일치 0건.** 앱이 선언한 41개 Retrofit 엔드포인트 중 40개가 운영 스펙과 경로·메서드가 일치하고, 1개(`/api/v1/dispatch/rides/{partyId}`)는 **불일치가 아니라 서버 기능 플래그로 비활성화된 엔드포인트**이며 운영 모드에서 호출 경로가 존재하지 않는다.

---

## 1. 경로·메서드 전수 대조 (할 일 1)

기계적 대조 결과 (`@GET/@POST/@PUT/@PATCH/@DELETE/@HTTP` 41건 vs 스펙 46 op):

| 구분 | 건수 |
|---|---|
| 앱 선언 = 운영 스펙 일치 | 40 |
| 앱 선언인데 운영 스펙에 없음 | **1** (아래 §2) |
| 운영 스펙에 있는데 앱이 안 씀 | 6 (SSE 1 + 미구현 기능 5, §5-무해) |

앱이 안 쓰는 6개:
- `GET /api/v1/matching/rooms/{partyId}/events` — Retrofit 이 아니라 raw OkHttp SSE 로 호출한다(`PartyEventSource.kt:99`). **정상 사용 중**, 미사용 아님.
- `POST /api/v1/auth/phone/check` — 기사 앱 phone-first 온보딩용. 승객 앱 미사용.
- `POST /api/v1/chat-rooms/{id}/location`, `POST|DELETE /api/v1/chat-rooms/{id}/location/sharing` — 채팅 위치 공유. 앱 미구현.
- `POST /api/v1/chat-rooms/{id}/reports` — 채팅 메시지 신고. 앱 미구현.

---

## 2. `/api/v1/dispatch/**` 단서 — **확정: 불일치 아님. 기능 플래그로 꺼진 엔드포인트다**

### 결론
앱의 `@GET("api/v1/dispatch/rides/{partyId}")` (`data/src/main/kotlin/com/moyeota/data/remote/DispatchApi.kt:18`) 는 **백엔드 소스와 정확히 일치한다.**

서버 근거: `backend/src/main/java/team/codingforest/moyeota/dispatch/ride/RideController.java`
- L25 `@RequestMapping("/api/v1/dispatch")`
- L61 `@GetMapping("/rides/{partyId}")` → 합성 경로 `/api/v1/dispatch/rides/{partyId}` (앱 선언과 동일)
- **L22 `@ConditionalOnProperty(prefix = "moyeota.taxi", name = "enabled", havingValue = "true", matchIfMissing = true)`**

운영은 택시 모드가 꺼져 있다:
- `backend/src/main/resources/application-prod.yaml` L47-48 → `moyeota.taxi.enabled: ${TAXI_ENABLED:false}`
- 실측: `curl https://api.moyeota.p-e.kr/api/v1/config` → `{"taxiEnabled":false}` (200, 무인증)
- `AppConfigController.java:19` 이 같은 프로퍼티(`${moyeota.taxi.enabled:true}`)를 읽으므로 **config 응답이 곧 RideController 등록 여부다.**

### 교차 검증 (이 해석이 맞다는 결정적 증거)
`moyeota.taxi.enabled` 로 게이트된 **컨트롤러 4개가 전부, 그리고 그 4개만** 운영 스펙에서 빠져 있다:

| 컨트롤러 | 게이트 | 운영 스펙 |
|---|---|---|
| `dispatch/ride/RideController` | `havingValue="true"` | 없음 |
| `dispatch/location/DriverLocationController` | `havingValue="true"` | 없음 (`/api/v1/dispatch/online`, `/location`) |
| `dispatch/call/DispatchCallController` | `havingValue="true"` | 없음 |
| `driver/DriverController` | `havingValue="true"` | 없음 (`/api/v1/drivers/**` 0건) |

반대로 `havingValue="false"` 인 `CompletedPartySweeper`(30분 방치 스윕)·`ChatOnlyCompletionPolicy` 는 운영에서 **켜져 있다** — `docs/MODE-ROUTES.md` 의 동승 모드 서술과 정확히 일치.

### 호출 경로를 따라간 결과 — 사용자가 이걸 만날 수 있는가: **없다**

```
DispatchApi.getDriverLocation
  ← RemoteDispatchRepository.getDriverLocation            (data/.../RemoteDispatchRepository.kt:12)
  ← DispatchStatusViewModel.pollDriver                    (presentation/.../DispatchStatusRoute.kt:144)
       가드: if (shouldFetchDriverLocation(ride))         (DispatchStatusRoute.kt:332 → ride?.driverId != null)
       감싸기: runCatching { ... }.onSuccess { ... }      (실패해도 조용히 직전 값 유지)
  ← Routes.DISPATCH_STATUS (화면 25) 만 진입점
```

세 겹으로 막혀 있다:
1. **화면 25 에 닿을 수 없다.** `driverId` 는 `PartyDetailResult.taxiDriverId` 에서 온다. 동승 모드 서버는 기사 배정을 하지 않아 영구히 null 이고, status 가 `MATCHING`/`DRIVER_ASSIGNED` 로 가지 않으므로 `ActiveStage.DRIVER_SEARCH/DRIVER_COMING` 자체가 발생하지 않는다 (`docs/MODE-ROUTES.md` — 25·26·27·33 「동승 모드에서 안 보임」).
2. **가드.** 화면에 들어가도 `ride.driverId == null` 이면 호출하지 않는다.
3. **runCatching.** 설령 호출돼도 404/401 은 삼켜지고 지도 마커만 갱신되지 않는다. 역직렬화 예외도 여기서 잡힌다.

디버그 강제 모드(`setDebugOverride`)는 `MainNavGraph.kt:806` 에서 `if (debuggable)` 로 릴리스 빌드에서 제외된다.

### 판정
**무해.** `TAXI_ENABLED=true` 로 운영 모드를 바꾸는 순간 서버에 이 엔드포인트가 되살아나고 앱 선언이 그대로 맞는다. 고칠 것 없음.

> 다만 앱 테스트 `data/src/test/.../AuthenticatedPathContractTest.kt:190` 이 이 경로를 못박아 두고 있는데, 이 테스트는 **"운영에 존재한다"가 아니라 "택시 모드 서버의 경로가 이것이다"를 지키는 것**이다. 의미가 바뀐 게 아니므로 수정 불필요. 주석 한 줄로 「운영(동승 모드)에는 등록되지 않는다」를 남기면 다음 사람이 같은 조사를 반복하지 않는다. (선택)

---

## 3. 요청·응답 필드 / nullable 대조 (할 일 2)

### 3-1. 역직렬화로 화면이 죽을 수 있는가 — 최우선 점검

**메커니즘**: `NetworkModule.kt:24` 의 디코더는 `Json { ignoreUnknownKeys = true }` 뿐이고 **`coerceInputValues` 가 꺼져 있다.** 따라서
- 서버가 **키를 안 보내면** → 앱 DTO 의 기본값이 쓰인다 (안전)
- 서버가 **명시적 `null` 을 보내면** → non-nullable 프로퍼티는 기본값으로 떨어지지 않고 `SerializationException` 으로 **응답 전체 파싱이 실패**한다

그래서 "앱이 non-null 로 받는 필드를 서버가 null 로 보낼 수 있는가"만 보면 된다. 전수 확인 결과:

| 응답 | 앱이 non-null 로 받는 필드 | 서버가 null 을 낼 수 있나 | 근거 |
|---|---|---|---|
| `PartyListResponse.PartyItem` (`PartyDtos.kt:11-18`) | partyId, departure, destination, currentMembers, capacity, status | **불가** | `PartyEntity.java:34-59` 전부 `@Column(nullable=false)`; currentMembers 는 `members.size()` |
| `PartyDetailResponse` (`PartyDtos.kt:24-41`) | id, departure, destination, capacity, currentMembers, departureRadius, destinationRadius, status | **불가** | 동상. `PartyResult.from` 이 Party 도메인에서 그대로 옮긴다 |
| `PartyDetailResponse.MemberInfo` (`PartyDtos.kt:45-49`) | rideCount | **불가** | `PartyDetailResult.java:32` `rideCounts.getOrDefault(id, 0)` |
| 〃 publicId/nickname/imageUrl/badgeId | — | 가능하지만 앱이 **전부 nullable** | `PartyDetailResult.java:24` 탈퇴자에 null 4개. 앱 `PartyMappers.kt:69` `nickname ?: "탈퇴한 회원"` ✅ |
| `OpenPartyResponse` | id, departure, destination, capacity, currentMembers, radius×2, status | **불가** | 동상 |
| `PartyStatusResponse` | status, currentMembers | **불가** | `PartyStatusResponse.java:8` |
| `ChatRoomResponse` (`ChatDtos.kt:11-16`) | id, partyId, departure, destination, status | **불가** | `ChatRoomResult.java:17-24`, ChatRoom 은 party 에서 생성 |
| `ChatMemberResponse` (`ChatDtos.kt:107-110`) | publicId, nickname, active | **불가** | `ChatRoomUserService.java:167` `.filter(members.containsKey(...))` 로 조회 불가 사용자를 **목록에서 제외**. 남은 사용자는 `User.displayNickname()` (`User.java:84-85`) 가 탈퇴자에게 `"탈퇴한 사용자"` 를 보장 |
| `ChatMessageResponse` (`ChatDtos.kt:64-84`) | id, chatRoomId, userId, content, type, deleted | **불가** | `ChatMessageResult.java:20-29`; `userId` 는 서버가 키 자체를 안 보내 기본값 0 (안전 경로) |
| `ChatLastMessageResponse` | id, content, type | **불가** | `ChatRoomUserResult.java:34-42` (삭제 메시지는 `"삭제된 메시지입니다"` 로 치환) |
| `ChatRoomUserResponse` | chatRoomId, notificationMuted | **불가** | `ChatRoomUserResult.java:47-58`, `unreadCount` 는 primitive `long` |
| `UserProfileResponse` (`UserDtos.kt:7-8`) | uuid | **불가** | `UserResponse(UUID uuid, String name)`; name 은 앱이 nullable ✅ |
| `TokenResponse`, `SmsVerifyResponse`, `RegisterResponse`, `NicknameCheckResponse`, `AppConfigResponse` | 전부 | **불가** | 생성 시점에 값이 보장됨 |
| `AddressResponseDto` | 없음 (3필드 전부 nullable) | — | ✅ |
| `RouteEstimateResponse` | 없음 (3필드 전부 nullable) | — | ✅ |

**→ 운영에서 도달 가능한 응답 중 역직렬화로 죽는 필드는 하나도 없다.**

주의가 필요한 것 2건은 §5(고쳐야 함)·§6(무해)에 분리했다.

### 3-2. 앱이 보내는데 서버가 모르는 필드 / 서버가 필수인데 앱이 안 보내는 필드

없음. 전수 확인:

| 요청 | 서버 필수(`@NotNull/@NotBlank`) | 앱이 보내나 |
|---|---|---|
| `POST /auth/register` ← `RegisterRequestDto` (`AuthDtos.kt:6-16`) | loginId, password, nickname, name, birthDate, phoneNumber, gender, email, **verificationToken** | 9개 전부 ✅ (`UserRegisterRequest.java:14-53`) |
| 〃 `birthDate` 는 서버 타입이 `Instant` | ISO-8601 instant 필요 | `AuthMappers.kt:26-27` `atStartOfDay(UTC).toInstant().toString()` → `"2000-01-01T00:00:00Z"` ✅ |
| 〃 `gender` 는 서버 enum `Gender{MALE,FEMALE}` | 정확 일치 필요 | `AuthMappers.kt:38` `gender.name` — 앱 도메인 enum 이 MALE/FEMALE ✅ |
| `POST /auth/login` ← `LoginRequestDto` | loginId, password | ✅ |
| `POST /auth/reissue`·`/logout` ← `RefreshTokenRequestDto` | refreshToken | ✅ |
| `POST /auth/sms/send`·`/verify` | phoneNumber / phoneNumber+code | ✅ |
| `POST /auth/nickname/check` | nickname | ✅ |
| `POST /matching/rooms` ← `OpenPartyRequestDto` (`PartyDtos.kt:72-80`) | 없음 (전부 optional) | 7필드. **`creatorId` 를 안 보내는 것이 정답** — `OpenPartyRequest.java:4,11` "무시됨, deprecated". **반경(radius)은 요청에 없다** — a668aee 이후 서버 고정 500m, 앱도 안 보낸다 ✅ |
| `POST /matching/routes` ← `RouteRequestDto` | 없음 | 4좌표 ✅ |
| `POST /users/me/favorite-places` ← `FavoritePlaceRequestDto` | 없음 | 4필드 ✅ |
| `PATCH /users/me` ← `UpdateProfileRequest` | 없음 (넘어온 것만 변경) | nickname·imageUrl 둘 다 nullable ✅ |
| `PUT /users/me/fcm-token` ← `FcmTokenRequest` | token | ✅ |
| `POST /reports` ← `ReportRequestDto` | 없음 | partyId·lat·lng ✅ |
| `PATCH /reports/call-result` ← `CallResultRequestDto` | **called** | ✅ |
| `POST /chat-rooms/{id}/messages` ← `SendMessageRequestDto` | **content** | ✅ |
| `DELETE /users/me` ← `AccountDeletionRequest` | **password** | ✅ (§4) |

Spring Boot 기본값이 `FAIL_ON_UNKNOWN_PROPERTIES=false` 라 여분 필드를 보내도 무시되지만, 애초에 여분 필드가 없다.

### 3-3. 쿼리 파라미터

| 호출 | 서버 | 앱 | 판정 |
|---|---|---|---|
| `GET /matching/rooms` (전체) | `PartyController.java:67-70` `list()` — **파라미터 없는 핸들러가 따로 있다** | `MatchingApi.kt:29` 무파라미터 | ✅ **스펙만 보면 함정.** OpenAPI 는 같은 path+method 두 매핑을 표현 못 해 `listWithin` 쪽(`swLat` 등 required=true)만 노출한다. 소스 기준으로 무파라미터 호출이 정상이다 |
| `GET /matching/rooms` (지도) | `PartyController.java:82-88` `params={swLat,swLng,neLat,neLng}` | `MatchingApi.kt:38-44` 4개 전부 | ✅ 하나라도 빠지면 전체 목록 핸들러로 떨어짐 — 앱은 4개 다 보낸다 |
| `GET /places?query=` | required | ✅ | |
| `GET /places/reverse?latitude&longitude` | required `double` | ✅ `Double` | |
| `GET /chat-rooms/{id}/messages?cursor&size` | cursor optional, size `defaultValue="30"` | `cursor: Long?`(null 이면 Retrofit 이 생략) + `size: Int` | ✅ |
| `GET .../messages/after?cursor&size` | cursor **required** | `cursor: Long` non-null | ✅ |
| `GET .../messages/search?keyword&cursor&size` | keyword required (2자 미만 400 `CHAT_INVALID_KEYWORD`) | ✅ | |

### 3-4. enum 값 집합 — 서버가 새 값을 주면 앱이 터지는가: **안 터진다**

앱은 enum 을 **전부 String 으로 받고 매퍼에서 방어적으로 변환**한다.

| enum | 서버 값 | 앱 변환 | fallback |
|---|---|---|---|
| `PartyStatus` | ACTIVE, COMPLETED, MATCHING, DRIVER_ASSIGNED, IN_RIDE, FINISHED, CANCELED (`PartyStatus.java:4`) | `PartyMappers.kt:28-40` | `else -> RideStatus.RECRUITING` ✅ 7값 전부 매핑됨 |
| `ChatRoomStatus` | ACTIVE, CLOSED, ARCHIVED | `ChatMappers.kt:27-32` | `else -> ACTIVE` ✅ |
| `ChatMessageType` | TEXT, LOCATION | `ChatMappers.kt:34-37` | `else -> TEXT` ✅ (LOCATION 도 명시 처리) |
| `Gender` | MALE, FEMALE | 요청 전용 | ✅ |
| `ReportReasonType` | ABUSE/SPAM/FRAUD/SEXUAL/ETC | 앱 미사용 | — |

SSE 프레임도 같은 정책이다 — `PartySseEvent.kt:62-69` 모르는 이벤트는 `Skipped` 로 흘려보내고, 파싱 실패해도 스트림을 죽이지 않는다(`:71-75`).

---

## 4. SSE `/api/v1/matching/rooms/{partyId}/events` (할 일 3) — **일치**

| 항목 | 서버 | 앱 | 판정 |
|---|---|---|---|
| 경로·메서드 | `PartySseController.java:31` `GET .../events`, `produces=text/event-stream` | `PartyEventSource.kt:99-101` 동일 + `Accept: text/event-stream` | ✅ |
| 인증 | Bearer (`@CurrentUser`) | `NetworkModule.kt:78-80` `streamClient` = apiClient + readTimeout 45s | ✅ |
| `connected` | `PartySseRegistry.java:34` `.name("connected").data(getPartyDetail(partyId), APPLICATION_JSON)` → **방 상세 전체** | `PartySseEvent.kt:63` → `PartyDetailResponse` 로 디코드 | ✅ 같은 shape |
| `member-joined` | `RedisPartyChangeNotifier.java:20` name=`"member-joined"`, payload `MemberJoined{member, status}` (`MemberJoined.java:3`) | `PartySseEvent.kt:64` → `MemberJoinedPayload(member, status)` (`PartyDtos.kt:54-57`) | ✅ |
| `member-left` | `:25` name=`"member-left"`, `MemberLeft{publicId, status}` (`MemberLeft.java:5`, publicId 는 UUID — null 가능) | `PartySseEvent.kt:65` → `MemberLeftPayload(publicId: String?, status)` (`PartyDtos.kt:61-62`) | ✅ publicId nullable 맞음 |
| `closed` | `:30` name=`"closed"`, **data 가 JSON 이 아니라 partyId 숫자 문자열** | `PartySseEvent.kt:66` 데이터를 아예 파싱하지 않고 종결 신호로만 사용 | ✅ |
| heartbeat | `PartySseRegistry.java:17,68-72` 30초 `:ping` 주석 | `NetworkModule.kt:20` readTimeout 45초(=30초×1.5) | ✅ |
| 중복 구독 | `:25` `closeMember` — 같은 사용자의 기존 연결을 끊는다 | `PartyEventSource.kt:58-60` 화면당 1회 수집 규약 명시 | ✅ |
| 403/404 | NOT_PARTY_MEMBER / PARTY_NOT_FOUND | `:133-136` 재연결 중단 | ✅ |

**이벤트 이름 4개(connected/member-joined/member-left/closed) 전부 문자열 단위로 일치.** 변경분(delta) 전달 방식 전환(e729801)도 앱에 반영돼 있다 — 삭제된 `changed` 이벤트, 삭제된 `/status`·fingerprint 조회(04f6be7)를 호출하는 코드는 앱에 남아 있지 않다(기계 대조로 확인, §1).

---

## 5. 회원 탈퇴 `DELETE /api/v1/users/me` (할 일 4) — **일치**

| 항목 | 서버 | 앱 | 판정 |
|---|---|---|---|
| 경로·메서드 | `UserDeletionController.java:21,43` `@RequestMapping("/api/v1/users")` + `@DeleteMapping("/me")` | `UserApi.kt:104` `@HTTP(method="DELETE", path="api/v1/users/me", hasBody=true)` | ✅ |
| 본문 필수 | `:45` `@Valid @RequestBody UserDeletionRequest` | `:105` `@Body request: AccountDeletionRequest` | ✅ — 서버 Swagger(`:35`)가 **"Android Retrofit 은 `@DELETE` 대신 `@HTTP(..., hasBody = true)`"** 라고 이 형태를 직접 지시하고 있다 |
| 본문 필드 | `UserDeletionRequest.java:11` `@NotBlank String password` | `UserDtos.kt:18-20` `password: String` | ✅ |
| 응답 | `:44` `@ResponseStatus(NO_CONTENT)`, 본문 없음 | `suspend fun deleteAccount(...)` 반환형 Unit | ✅ |
| 에러 코드 | 400 `USER111` PASSWORD_MISMATCH / 409 `USER110` ONGOING_PARTY_EXISTS / 가입 시 `USER109` REJOIN_RESTRICTED | `AuthMappers.kt:105-107` 셋 다 개별 `AuthError` 로 매핑 | ✅ 화면 대응이 서로 다른 셋을 합치지 않음 |
| FCM 토큰 | `:37` "서버가 비우므로 DELETE /users/me/fcm-token 을 따로 부를 필요 없음" | — | ✅ |
| 탈퇴자 표시 | `User.java:18,84-85` `displayNickname()` → `"탈퇴한 사용자"` (nickname 컬럼은 null 로 비움) | 앱의 `MemberInfo.nickname` nullable + `?: "탈퇴한 회원"`, 채팅은 서버가 목록에서 제외 | ✅ 양쪽 다 안전 |

---

## 6. 등급별 불일치 목록

### 🔴 출시 차단 — **0건**

없음.

### 🟡 출시 가능하나 고쳐야 함 — 1건

**[M-1] `GET /api/v1/places` 응답의 `roadName` 이 null 로 오면 장소 검색 화면이 결과를 못 그린다 (가능성 낮음 · 일부 미확인)**

- 앱: `data/src/main/kotlin/com/moyeota/data/remote/dto/PlaceDtos.kt:13` — `val roadName: String = ""` (**non-nullable**)
- 서버: `place/search/dto/PlaceSearchResponse.java:5` `String roadName` (nullable 가능) ← `place/search/infrastructure/KakaoPlaceClient.java:50` `new Place(doc.placeName(), doc.roadAddressName(), ...)`. `KakaoSearchResponse.java` 의 `@JsonProperty("road_address_name")` 가 **카카오 응답에 키가 없으면 null 이 된다.** 같은 메서드의 try/catch(`:53`)는 좌표 파싱 실패만 거르고 roadName 은 null 그대로 통과시킨다.
- 터지는 메커니즘: `NetworkModule.kt:24` 디코더에 `coerceInputValues` 가 없어, 명시적 `null` 은 기본값 `""` 로 떨어지지 않고 `SerializationException` → **리스트 전체** 파싱 실패.
- **재현 경로**: 15 목적지 검색 화면에서, 도로명주소가 없는 장소(산·섬·일부 신규 건물)가 결과에 섞이는 키워드를 검색 → 결과 1건 때문에 목록 전체가 "검색 실패"로 떨어진다. 같은 필드가 `FavoritePlaceListResponse.FavoritePlaceItem.roadName`(`PlaceDtos.kt:41`)에도 non-null 로 있어, 그런 장소를 즐겨찾기에 저장하면 **14 홈의 즐겨찾기 조회가 매번 실패**한다(`FavoritePlaceEntity.java:34` roadName 컬럼에 `nullable=false` 가 없다).
- **미확인**: 카카오 로컬 키워드 검색이 `road_address_name` 키를 생략하는지, 빈 문자열 `""` 로 주는지는 **확인하지 못했다**(`GET /api/v1/places` 는 인증 필요라 운영 실측 불가, 카카오 API 키 없음). 빈 문자열이면 현재 코드로 아무 문제 없다.
- 고치는 비용이 매우 싸다: `roadName: String? = null` 로 바꾸고 매퍼(`PlaceMappers.kt:21,29`)에서 `.orEmpty()`, 또는 디코더에 `coerceInputValues = true` 한 줄. 후자는 §6-무해의 잠재 위험까지 한 번에 덮는다.

### 🟢 무해 — 7건

**[H-1] `@GET("api/v1/dispatch/rides/{partyId}")` (`DispatchApi.kt:18`)** — §2. 경로는 백엔드 소스와 정확히 일치하며, 운영은 `TAXI_ENABLED=false` 라 컨트롤러가 등록되지 않았을 뿐이다. 호출 지점은 화면 25(동승 모드에서 도달 불가) + `driverId != null` 가드 + `runCatching` 으로 3중 차단. **수정 불필요.**

**[H-2] `DriverSummaryResponse.plateNumber`·`type` 이 non-null** (`PartyDtos.kt:106-107`) — 서버 `driver/api/DriverSummary.java:3` 은 `String` 이라 null 가능. 다만 `GET /matching/rooms/{id}/driver` 는 기사 배정 후에만 200 을 주고(미배정 409), 동승 모드에선 배정이 없다. 호출부 `DispatchStatusRoute.kt:158` 도 `runCatching`. 택시 모드 전환 시점에만 검토하면 된다.

**[H-3] `GET /api/v1/places/reverse` 의 400 `OUT_OF_SERVICE_AREA` 를 앱이 구분하지 않는다** — `PlaceMappers.kt:44` 는 404+`ADDRESS_NOT_FOUND` 만 null 로 흡수하고 나머지는 `HttpException` 을 던진다. 호출부 `DestinationConfirmRoute.kt:199-203` 이 `catch (e: Exception)` 으로 삼켜 주소만 비는 조용한 실패가 된다. 한국 밖으로 핀을 끌어야 나오는 경로라 정상 사용 범위 밖.

**[H-4] 앱이 채팅 위치 공유 3개 엔드포인트를 쓰지 않는다** (`POST /chat-rooms/{id}/location`, `POST|DELETE /chat-rooms/{id}/location/sharing`) — 미구현 기능. 계약 불일치 아님.

**[H-5] 앱이 `POST /chat-rooms/{id}/reports`(채팅 신고)를 쓰지 않는다** — 미구현 기능. (별건: 서버 `ChatReportEntity.java:44-45` 의 `description` 이 `@Column(nullable=false)` 인데 `ChatReport.create` 는 ETC 가 아닌 사유에 null 을 허용한다 → `DataIntegrityViolationException` 이 `CHAT_ALREADY_REPORTED` 로 둔갑한다(`ChatReportService.java:53-55`). **백엔드 결함이지만 앱이 이 API 를 안 쓰므로 출시 영향 없음.**)

**[H-6] 앱이 `POST /api/v1/auth/phone/check` 를 쓰지 않는다** — 기사 앱 phone-first 온보딩용. 승객 앱 가입은 `sms/send` → `sms/verify` → `register` 로 완결된다.

**[H-7] 앱이 `/sub/chat-rooms/{id}/members`·`/user/queue/room-left` 를 구독하지 않는다** — `ChatSocket.kt:152` 는 `/sub/chat-rooms/{id}` 만 구독한다. 서버는 멤버 변경을 `/members` 접미사 목적지로 따로 보내므로(`ChatRedisSubscriber.java:70-76`) **앱의 메시지 구독에 섞여 들어오지 않는다**(파싱 실패 없음 ✅). 채팅방 안에서 동승자 입·퇴장이 실시간으로 반영되지 않는 기능 공백일 뿐이다.

---

## 7. 추가로 확인했고 이상 없는 것

- **WebSocket/STOMP 전 구간 일치**: 엔드포인트 `/ws-chat`(`WebSocketConfig.java:26` ↔ `ChatSocket.kt:178`), 프리픽스 `/pub`·`/sub`(`:32-33`), SEND 목적지 `/pub/chat-rooms/{id}/messages`·`/read`(`StompChatController.java:32,44` ↔ `ChatSocket.kt:107,114`), 페이로드 `{content}`·`{lastReadMessageId}`(`SendMessageRequest`/`ReadMessageRequest` ↔ `SendMessageFrame`/`ReadFrame`), CONNECT 프레임 `Authorization: Bearer`(`StompAuthInterceptor.java:44-48` ↔ `ChatSocket.kt:138`), 에러 통로 `@SendToUser("/queue/errors")` ↔ 클라이언트 `/user/queue/errors`(`ChatSocket.kt:28`).
- **구독 목적지에 메시지만 온다**: `ChatRedisSubscriber.java:51` 은 `/sub/chat-rooms/{id}` 에 `ChatMessageResult` 만 보내고, LOCATION/MEMBER 는 접미사 목적지로, ROOM_LEFT 는 user queue 로 분리한다. 앱이 base 목적지를 `ChatMessageResponse` 로 디코드해도 섞인 타입이 들어오지 않는다.
- **인증 경계**: 서버 permitAll 은 `/api/v1/auth/**`, `/api/v1/config`, `/ws-chat/**`, `/health`, `/prometheus`, `/info`, swagger (`SecurityConfig.java:46-49`). 앱은 `AuthApi`·`ConfigApi` 만 Bearer 없는 `publicRetrofit` 으로 만들고 나머지는 전부 `apiClient`(`NetworkModule.kt:56-59, 90-103`) — 정확히 일치. 토큰 재발급 무한루프 방지를 위해 `AuthApi` 를 Authenticator 없는 클라이언트에 둔 것도 맞다.
- **에러 본문 shape**: 서버 `common/exception/ErrorResponse(code, message)` ↔ 앱 `ApiErrorDto(code: String?, message: String?)`. 운영 실측 `{"code":"USER005","message":"로그인이 필요합니다."}` 와 일치.
- **날짜 포맷**: 서버 `Instant` 는 ISO-8601 문자열로 직렬화된다(`_workspace` 실측 로그 다수: `"createdAt":"2026-09-08T04:58:17.077652Z"`, `"joinedAt":"..."`). 앱은 `createdAt`/`joinedAt` 을 `String?` 으로 받는다 — 숫자 timestamp 였다면 전부 터졌을 지점이다. ✅
- **204 No Content ↔ 반환형**: 204 를 주는 15개 엔드포인트 전부 앱 쪽 반환형이 `Unit`. 본문 있는 응답을 `Unit` 으로 받거나 그 반대인 경우 0건.
- **삭제된 API 잔재 0건**: 방 fingerprint, `GET /matching/rooms/{id}/status`, `matching/ready`, `matching/start`, `POST /api/v1/users/login`, `/api/v1/local/users` 가입 — 앱 어디에도 선언이 남아 있지 않다(§1 기계 대조).
- **FCM data 타입**: 서버 승객 대상은 `CHAT_MESSAGE`(`FcmChatNotifier.java:47`)·`DRIVER_ARRIVED`(`FcmPassengerNotifier.java:47`) 둘. 앱 `MoyeotaFirebaseMessagingService.kt:57-58` 이 정확히 그 둘을 처리한다. `CALL_OPENED`/`CALL_CLOSED` 는 기사 앱 대상.
- **앱 모드 기본값이 안전쪽**: 설정 조회 실패 시 `AppConfig.Default`(동승 모드)로 간다 — 운영 서버의 실제 모드와 같아 틀려도 같은 화면이다.

---

## 8. 조사 한계 (미확인으로 남은 것)

1. **[M-1] 카카오 `road_address_name` 의 실제 null 여부** — 인증 필요 엔드포인트라 운영 실측 불가, 카카오 API 키 없음.
2. **인증이 필요한 엔드포인트의 실제 응답 바디를 한 건도 실측하지 못했다.** 운영 쓰기 금지 제약 + 로그인 계정 미보유. 응답 shape 판정은 전부 `origin/main` 소스(record 정의 + JPA 컬럼 제약) 기준이다. `_workspace` 에 남은 과거 실측 로그로 날짜 포맷·필드명만 교차 검증했다.
3. **`FavoritePlaceService.save` 의 roadName 검증 유무**를 읽지 않았다 — [M-1] 의 저장 경로 쪽 영향 범위에만 관계된다.
