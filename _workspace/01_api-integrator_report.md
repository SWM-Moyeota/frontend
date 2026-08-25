# 01 api-integrator — 백엔드 API 연동 (2026-08-24)

기준 브랜치: 프론트 `feature/api-integration` / 백엔드 스펙 `origin/develop` (컨트롤러 소스 기준)
검증: `:data:testDebugUnitTest` 22 tests / 0 failures, `:app:assembleDebug` BUILD SUCCESSFUL
**실서버 미검증** — localhost:8080 미기동(포트 LISTEN 없음). 아래 shape은 전부 origin/develop 컨트롤러·DTO 소스 대조 결과.

---

## 1. 가장 중요한 발견

### 1-1. 기존 매칭 조회 연동은 전부 404 상태였다
백엔드 `PartyController`는 `@RequestMapping("/api/v1")`. 프론트는 `api/matching/rooms`로 호출 중이었다.
→ **전 경로 `api/v1/...`로 수정.** 이전 "매칭 조회 연동 완료"는 서버가 v1으로 옮겨간 뒤 깨져 있었다.

### 1-2. `join` 엔드포인트가 백엔드에 없다 (지시서 범위와 불일치)
`PartyApplicationService.join(partyId, memberId)`는 구현돼 있으나 **`PartyController`에 매핑이 없다.**
`origin/develop` 전 컨트롤러를 훑어도 `/join` 매핑은 존재하지 않는다.
→ 경로를 추측해 호출하면 404로 원인만 흐려지므로, `RideRepository.joinParty` 계약은 유지하되
`RemoteRideRepository`에서 `ApiNotAvailableException("합류 API가 아직 서버에 없어요")`를 던진다.
**백엔드에 컨트롤러 매핑 추가 요청 필요.** 추가되면 `RemoteRideRepository.joinParty` 본문 한 줄만 바꾸면 된다.

### 1-3. `GET /api/v1/matching/routes`(경로 추천)도 존재하지 않는다
`origin/develop`에 매핑 없음. 도메인 메서드를 만들지 않고 **미구현으로 제외**했다.

### 1-4. `backend/http/*.http`는 신뢰할 수 없다 (실제 컨트롤러와 불일치)
| 파일 | .http 기재 | 실제 컨트롤러 |
|---|---|---|
| matching.http | `POST /api/matching/rooms` | `POST /api/v1/matching/rooms` |
| place.http | `POST /api/places/favorites/{userId}` + body `{name, roadAddress, ...}` | `POST /api/v1/users/me/favorite-places?userId=` + body `{placeName, roadName, ...}` |
| place.http | `GET /api/places/histories/{userId}` 등 검색기록 API | **컨트롤러에 없음** |
| chat.http | 대체로 일치 | 일치 |
→ 전부 **컨트롤러 소스를 정본으로 삼아** 구현했다.

---

## 2. 확정 Repository 시그니처 (compose-builder 인계용)

```kotlin
// domain/repository/RideRepository.kt
interface RideRepository {
    fun getNearbyParties(): List<Ride>
    fun getMyRides(): List<Ride>

    suspend fun getParties(): List<Ride>
    suspend fun getPartyDetail(partyId: Long): Ride

    suspend fun createParty(request: NewParty): Ride
    suspend fun joinParty(partyId: Long, memberId: Long)   // ⚠ ApiNotAvailableException
    suspend fun leaveParty(partyId: Long, memberId: Long)
    suspend fun setReady(partyId: Long, memberId: Long)
    suspend fun cancelReady(partyId: Long, memberId: Long)
    suspend fun startMatching(partyId: Long, memberId: Long)
}

// domain/repository/PlaceRepository.kt
interface PlaceRepository {
    suspend fun searchPlaces(query: String): List<Place>
    suspend fun addFavoritePlace(userId: Long, place: Place)
    suspend fun getFavoritePlaces(userId: Long): List<FavoritePlace>
}

// domain/repository/ChatRepository.kt  (REST 전용, STOMP 미구현)
interface ChatRepository {
    suspend fun getMyChatRooms(userId: Long): List<MyChatRoom>
    suspend fun getChatRoom(chatRoomId: Long): ChatRoom
    suspend fun createChatRoom(partyId: Long, departure: String, destination: String): ChatRoom
    suspend fun closeChatRoom(chatRoomId: Long)

    suspend fun joinChatRoom(chatRoomId: Long, userId: Long)
    suspend fun leaveChatRoom(chatRoomId: Long, userId: Long)
    suspend fun markAsRead(chatRoomId: Long, userId: Long, readMessageId: Long)

    suspend fun getMessages(chatRoomId: Long, userId: Long, cursor: Long? = null, size: Int = 30): ChatMessagePage
    suspend fun getMessagesAfter(chatRoomId: Long, userId: Long, cursor: Long, size: Int = 30): ChatMessagePage
    suspend fun sendMessage(chatRoomId: Long, userId: Long, content: String): ChatMessage
    suspend fun deleteMessage(chatRoomId: Long, userId: Long, messageId: Long)

    companion object { const val DEFAULT_PAGE_SIZE = 30 }
}

// domain/session/UserSession.kt — 화면이 memberId/userId 를 얻는 단일 출처
interface UserSession { val currentUserId: Long }
```

### 새 도메인 모델
```kotlin
// model/NewParty.kt
data class NewParty(
    val hostId: Long,
    val departureLat: Double, val departureLng: Double,
    val destinationLat: Double, val destinationLng: Double,
    val departure: String, val destination: String,
    val capacity: Int,
    val departureRadius: Int = 500,   // 서버 검증: 100~500
    val destinationRadius: Int = 500,
)

// model/Place.kt
data class Place(val name: String, val roadName: String, val latitude: Double, val longitude: Double)
data class FavoritePlace(val name: String, val roadName: String, val latitude: Double, val longitude: Double, val sequence: Int)

// model/Chat.kt
enum class ChatRoomStatus { ACTIVE, CLOSED, ARCHIVED }
enum class ChatMessageType { TEXT, LOCATION }
data class ChatRoom(val id: Long, val partyId: Long, val departure: String, val destination: String, val createdAt: String, val status: ChatRoomStatus)
data class ChatRoomMembership(val chatRoomId: Long, val lastReadMessageId: Long?, val notificationMuted: Boolean, val joinedAt: String)
data class MyChatRoom(val room: ChatRoom, val membership: ChatRoomMembership)
data class ChatMessage(val id: Long, val chatRoomId: Long, val senderId: Long, val content: String, val type: ChatMessageType, val createdAt: String, val deleted: Boolean)
data class ChatMessagePage(val messages: List<ChatMessage>, val nextCursor: Long?, val hasNext: Boolean)
```

### Ride 확장 (기존 필드 유지, 전부 기본값 → 기존 화면 무영향)
```kotlin
val hostId: String? = null          // "내가 방장인가" → 매칭 시작 버튼 노출 판단
val originLat/originLng: Double? = null
val destinationLat/destinationLng: Double? = null
```

### DI (app/AppContainer.kt)
```kotlin
val userSession: UserSession        // FixedUserSession(1L)
val rideRepository: RideRepository
val placeRepository: PlaceRepository
val chatRepository: ChatRepository
```
`MainNavGraph`에는 아직 `rideRepository`만 전달된다 — place/chat 화면 연결 시 compose-builder가 시그니처 확장 필요.

---

## 3. 백엔드 스펙 대조표 (origin/develop 컨트롤러 기준)

### 매칭 — `PartyController` (`/api/v1`)
| 메서드 | 경로 | 요청 | 응답 | 프론트 |
|---|---|---|---|---|
| GET | `/matching/rooms` | - | `{list:[{partyId,departure,destination,currentMembers,capacity,status}]}` | `getParties()` |
| GET | `/matching/rooms/{partyId}` | - | `PartyDetailResult` + `members:[{memberId,isHost,joinedAt}]` | `getPartyDetail()` |
| POST | `/matching/rooms` | `OpenPartyRequest` 10필드 | `OpenPartyResponse` (**members 배열 없음**) | `createParty()` |
| DELETE | `/matching/leave/{partyId}/{memberId}` | - | 204 본문없음 | `leaveParty()` |
| POST | `/matching/ready/{partyId}/{memberId}` | - | 204 | `setReady()` |
| DELETE | `/matching/ready/{partyId}/{memberId}` | - | 204 | `cancelReady()` |
| POST | `/matching/start/{partyId}/{memberId}` | - | 204 | `startMatching()` |
| — | `join`, `routes` | — | **엔드포인트 없음** | 미구현 |

`PartyStatus` = `ACTIVE, COMPLETED, MATCHING, FINISHED, CANCELED` → 기존 `partyStatusToRideStatus` 매핑 유지.

### 장소 — `PlaceSearchController` / `FavoritePlaceController` (`/api/v1`)
| 메서드 | 경로 | 요청 | 응답 |
|---|---|---|---|
| GET | `/places?query=` | query param | `{list:[{name,roadName,latitude,longitude}]}` |
| POST | `/users/me/favorite-places?userId=` | body `{placeName,roadName,latitude,longitude}` | 204 |
| GET | `/users/me/favorite-places?userId=` | query param | `{places:[{placeName,roadName,latitude,longitude,placeSequence}]}` |

- **userId가 경로가 아닌 쿼리 파라미터**다. (백엔드 TODO: 추후 인증에서 추출)
- **검색은 `name`, 즐겨찾기는 `placeName`으로 필드명이 다르다.** 매퍼(`PlaceMappers`)에서 흡수.
- 400 케이스: 이름 중복 등록, 10개 초과 (`FavoritePlaceApplicationService`).

### 채팅 — `ChatRoom/ChatRoomUser/ChatMessageController` (`/api/v1/chat-rooms`)
| 메서드 | 경로 | 인증 헤더 | 응답 |
|---|---|---|---|
| GET | `/{chatRoomId}` | - | `ChatRoomResult{id,partyId,departure,destination,createdAt,status}` |
| POST | `` | - | 201 `ChatRoomResult` (body `{partyId,departure,destination}`) |
| DELETE | `/{chatRoomId}` | - | 204 |
| GET | `/me` | `X-User-Id` | **배열** `[{chatRoomId,lastReadMessageId,notificationMuted,joinedAt}]` |
| POST | `/{chatRoomId}/users` | `X-User-Id` | 201 본문없음 |
| DELETE | `/{chatRoomId}/users` | `X-User-Id` | 200 본문없음 |
| POST | `/{chatRoomId}/users/read/{readMessageId}` | `X-User-Id` | 200 본문없음 |
| GET | `/{chatRoomId}/messages?cursor=&size=` | `X-User-Id` | `{messages:[...],nextCursor,hasNext}` |
| GET | `/{chatRoomId}/messages/after?cursor=&size=` | `X-User-Id` | 동일 (cursor 필수) |
| POST | `/{chatRoomId}/messages` | `X-User-Id` | 201 `ChatMessageResult` (body `{content}`, 1~1000자) |
| DELETE | `/{chatRoomId}/messages/{messageId}` | `X-User-Id` | 204 |

- **사용자 식별이 `X-User-Id` 헤더**다 (매칭·장소는 경로/쿼리 파라미터 — 도메인마다 방식이 다르다).
- `GET /me`는 **방 이름을 주지 않는다.** 채팅 목록 화면이 한 번에 그릴 수 있도록
  `RemoteChatRepository.getMyChatRooms`가 방마다 `/chat-rooms/{id}`를 추가 호출해 합친다(**N+1**).
  상세 조회 실패한 방은 목록에서 제외해 종료된 방 때문에 전체가 깨지지 않게 했다.
  → 백엔드가 목록에 방 정보를 포함해주면 이 루프 제거 가능.
- 삭제된 메시지는 서버가 `content`를 `"삭제된 메시지입니다"`로 치환하고 `deleted:true`로 내려준다.

---

## 4. 액션 성공 후 갱신 대상 (compose-builder 참고)

| 액션 | 성공 후 갱신 |
|---|---|
| `createParty` | 응답 Ride를 그대로 MatchWaiting으로 전달 (재조회 불필요) |
| `leaveParty` | Explore 목록 재조회 — **Explore는 화면 재진입 시에만 재로드되는 제약 있음** |
| `setReady` / `cancelReady` | `getPartyDetail(partyId)` 재조회 |
| `startMatching` | `getPartyDetail` 재조회 (status → MATCHING) |
| `addFavoritePlace` | `getFavoritePlaces(userId)` 재조회 |
| `sendMessage` | 응답 메시지를 목록에 append |
| `markAsRead` | 채팅 목록 안읽음 배지 갱신 |

---

## 5. 방어 처리 (런타임 크래시 예방)

- 백엔드 record가 전부 **박싱 타입**(`Long`/`Integer`/`Double`)이라 null 가능 → DTO 필드에 기본값/nullable 지정.
- `PartyDetailResult.MemberInfo`의 `boolean isHost`: Jackson 버전에 따라 `"isHost"` / `"host"`로 갈리는
  알려진 이슈가 있어 `@JsonNames("host")`로 **두 이름 모두 수용**. (테스트로 양쪽 검증)
- enum(`status`, `type`)은 String으로 받고 매퍼에서 변환 — 서버가 값을 추가해도 앱이 죽지 않는다.
- 204/본문없음 응답은 Retrofit `Unit` 반환. Retrofit 3.0.0 jar에 `BuiltInConverters$UnitResponseBodyConverter`
  존재 확인 완료(추측 아님).

---

## 6. 변경/추가 파일

**수정**
- `app/src/main/kotlin/com/moyeota/app/AppContainer.kt`
- `data/src/main/kotlin/com/moyeota/data/remote/MatchingApi.kt` (v1 경로 + 액션 5종)
- `data/src/main/kotlin/com/moyeota/data/remote/NetworkModule.kt` (Retrofit 1개 공유, 3 API 생성)
- `data/src/main/kotlin/com/moyeota/data/remote/PartyMappers.kt`
- `data/src/main/kotlin/com/moyeota/data/remote/dto/PartyDtos.kt`
- `data/src/main/kotlin/com/moyeota/data/repository/DummyRideRepository.kt`
- `data/src/main/kotlin/com/moyeota/data/repository/RemoteRideRepository.kt`
- `data/src/test/kotlin/com/moyeota/data/remote/PartyMappersTest.kt`
- `domain/src/main/kotlin/com/moyeota/domain/model/Ride.kt`
- `domain/src/main/kotlin/com/moyeota/domain/repository/RideRepository.kt`

**신규**
- `domain/.../model/{Place,Chat,NewParty}.kt`
- `domain/.../repository/{PlaceRepository,ChatRepository,ApiNotAvailableException}.kt`
- `domain/.../session/UserSession.kt`
- `data/.../remote/{PlaceApi,ChatApi,PlaceMappers,ChatMappers}.kt`
- `data/.../remote/dto/{PlaceDtos,ChatDtos}.kt`
- `data/.../repository/{RemotePlaceRepository,RemoteChatRepository}.kt`
- `data/.../session/FixedUserSession.kt`
- `data/src/test/.../{PlaceMappersTest,ChatMappersTest}.kt`

---

## 7. 테스트 결과

초기 연동 완료 시점:
```
:data:testDebugUnitTest   BUILD SUCCESSFUL
  PartyMappersTest  9 tests, 0 failures
  ChatMappersTest   8 tests, 0 failures
  PlaceMappersTest  5 tests, 0 failures
:app:assembleDebug        BUILD SUCCESSFUL
```

D-4 / D-5 수정 후 재검증:
```
:data:testDebugUnitTest --rerun-tasks    BUILD SUCCESSFUL — 22 tests, 0 failures
:app:compileDebugKotlin --rerun-tasks    BUILD SUCCESSFUL
:data:testDebugUnitTest :app:assembleDebug   BUILD SUCCESSFUL
  → app/build/outputs/apk/debug/app-debug.apk 생성 확인
```
- 캐시 통과가 아님을 확인하려고 `--rerun-tasks`로 `:data:compileDebugKotlin`·`:app:compileDebugKotlin`을
  강제 재컴파일해 수정 파일이 실제로 컴파일되는지 검증했다.
- 첫 시도에서는 `:app:assembleDebug`가 compose-builder의 F-1 in-flight 작업 때문에 `presentation/`
  (`LoadState.kt` fillMaxWidth 미import, `ChatRoute.kt` TabStateScaffold 미정의)에서 막혔다.
  **본 수정과 무관한 별개 모듈 상태**였고, presentation 정리 후 재시도에서 통과했다.

---

## 7-1. QA 지적 반영 (2026-08-24, 03 보고서 D-4 / D-5)

### D-4 [Low] 릴리즈 빌드에서도 HTTP BODY 로깅이 켜져 있던 문제 — 수정
`NetworkModule`이 `HttpLoggingInterceptor(Level.BODY)`를 빌드 타입과 무관하게 항상 붙이고 있었다.
채팅 메시지 본문 전체와 향후 붙을 인증 토큰이 릴리즈 logcat에 남는 상태였다.

**채택한 방식:** 로깅 여부를 `NetworkModule`이 판단하지 않고 **호출자가 주입**한다.
```
MoyeotaApplication.isDebuggableBuild()
  → AppContainer(debugLogging)
  → NetworkModule.create(baseUrl, debugLogging)
  → debugLogging == false 이면 인터셉터를 아예 추가하지 않음
```

**`BuildConfig.DEBUG`를 쓰지 않은 이유 (두 가지, 둘 다 이 프로젝트에 해당):**
1. `app/build.gradle.kts`의 `buildFeatures`에 `compose = true`만 있고 `buildConfig = true`가 없다.
   AGP 8+에서는 이 플래그 없이 `BuildConfig`가 아예 생성되지 않는다 → `com.moyeota.app.BuildConfig.DEBUG` 참조 불가.
2. `data`는 라이브러리 모듈이라 자체 `BuildConfig.DEBUG`가 app의 빌드 타입과 일치하지 않는다(QA가 지적한 함정).

대신 manifest의 debuggable 플래그를 직접 읽는다 — "이 빌드가 디버그인가"를 정확히 답하며 빌드 스크립트 변경이 필요 없다:
```kotlin
private fun isDebuggableBuild(): Boolean =
    applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
```

### D-5 [Low] 데드코드 3개 제거 — 수정
`NetworkModule`의 `createMatchingApi` / `createPlaceApi` / `createChatApi` 삭제.
삭제 전 `--include=*.kt` 전수 grep으로 호출부 0건 재확인(유일한 사용처는 `AppContainer`의 `create()`).
남겨두면 API마다 Retrofit·OkHttp 인스턴스가 중복 생성될 수 있었다 — `create()`를 만든 이유가 그것이다.

**변경 파일:** `data/.../remote/NetworkModule.kt`, `app/.../AppContainer.kt`, `app/.../MoyeotaApplication.kt`
(`presentation/`은 compose-builder가 F-1 작업 중이라 건드리지 않았다)

**남은 사항:** `AppContainer` 생성자가 `AppContainer(debugLogging: Boolean)`로 바뀌었다. 생성부는
`MoyeotaApplication.kt` 한 곳뿐이라 함께 수정했고 다른 호출부는 없다.

---

## 8. 플래그 / 미해결

1. **실서버 미검증** — 백엔드 미기동. 모든 shape은 컨트롤러 소스 대조 결과. 실기 검증 시 `X-User-Id` 헤더·쿼리 파라미터 수용 여부 우선 확인 권장.
2. **`join` 엔드포인트 부재** — 백엔드 PR 필요. JoinConfirmScreen은 현재 실패 경로만 탄다.
3. **`matching/routes` 부재** — 경로 추천 미구현.
4. **STOMP 미구현** — 실시간 수신 없음. `getMessagesAfter(cursor)` 폴링으로 대체 가능하도록 시그니처 제공.
   백엔드에 `StompChatController`/`WebSocketConfig` 존재(`allowed-origins: http://localhost:*` — 에뮬레이터 Origin 허용 여부 확인 필요).
5. **인증 없음** — `FixedUserSession(1L)` 고정. 백엔드도 동일하게 "JWT에서 memberId 추출" TODO 상태라 양쪽이 고정 사용자 전제.
6. **멤버 ready 상태가 응답에 없음** — 백엔드 `MemberStatus{NOT_READY, READY}`는 있으나 `PartyDetailResult.MemberInfo`에 노출되지 않는다. MatchWaiting에서 "누가 준비했는지" 표시 불가. 백엔드 노출 요청 필요.
7. **프로필·요금 필드 부재** — 닉네임/평점/요금은 서버에 없어 매퍼가 `"멤버 N"`, 0으로 채운다(기존과 동일).
8. **채팅 목록 N+1** — 위 3절 참고.
