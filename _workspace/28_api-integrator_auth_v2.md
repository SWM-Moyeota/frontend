# 28. 백엔드 인증 개편(v2) 재연동 — api-integrator

작업일: 2026-09-02 / 브랜치: `feature/naver-map` / 선행: `21_api-integrator_auth.md`, `22_api-integrator_authpaths.md`
백엔드: `feature/userdomain` 리베이스 (Spring Security + jti 리프레시 회전)

**검증**: `./gradlew :data:testDebugUnitTest :domain:compileDebugKotlin :app:assembleDebug --console=plain` → **BUILD SUCCESSFUL**
단위 테스트 **102개 / 0 실패** (이전 96 → +6). `:presentation` 은 **깨지지 않았다** — 도메인 시그니처를 하나도 바꾸지 않았다.
**실서버 검증: 완료.** 아래 응답은 전부 localhost:8080 curl 실측값이다. 서버는 죽이지 않았고 에뮬레이터는 건드리지 않았다.

---

## 1. 바뀐 계약 6가지 — 실측 대조표

| # | 항목 | 이전 | 지금 (실측 확정) |
|---|---|---|---|
| 1 | 가입 | `POST /api/v1/users` | **`POST /api/v1/auth/register`** → 201 `{"uuid":"01a06109-…"}` |
| 2 | 로그인 | `POST /api/v1/users/login` → `{userId,accessToken,refreshToken}` | **`POST /api/v1/auth/login`** → **`{accessToken,refreshToken}`** (userId 소멸) |
| 3 | reissue/logout | `/api/v1/auth/{reissue,logout}` | **경로·shape 동일**, 회전 유지 |
| 4 | 보호 범위 | 일부만 보호 | **전 도메인 API 인증 필수** |
| 5 | 나가기 | `DELETE /matching/leave/{partyId}/0` (더미 세그먼트) | **`DELETE /matching/leave/{partyId}`** |
| 6 | 방 생성 | 본문 `creatorId` 로 방장 지정 | **토큰 주체가 방장**, 본문 `creatorId` 는 서버가 무시 |

옛 경로는 404 가 아니라 **401** 로 답한다(Security 가 보호 경로로 잡는다) — 실측 `POST /api/v1/users/login` → 401.
이 차이가 위험한 이유: 404 면 "경로가 틀렸구나"로 바로 읽히는데 401 은 "비밀번호가 틀렸나?"로 오독되어 원인 추적이 길어진다.

---

## 2. `currentUserUuid` 를 어떻게 구했나 — JWT `sub` 파싱을 골랐다

로그인 응답에서 `userId` 가 사라져 사용자 UUID 출처가 없어졌다. 두 후보를 비교했다.

| 방식 | 커버 범위 | 판정 |
|---|---|---|
| **가입 응답 `uuid` 재사용** | "방금 가입한" 흐름 **하나뿐** | ✗ 기존 계정 로그인·앱 재시작 후 복원에서 값이 없다 |
| **액세스 토큰 `sub` 파싱** | 로그인·재발급 **모든 경로** | ✓ 채택 |

`sub` 는 `JwtProvider.build()` 가 `.subject(publicId.toString())` 로 넣는 값이고, **가입 응답 `uuid` 와 동일함을 실측했다**:

```
register → {"uuid":"01a06109-f878-7706-aab0-5466fb311d26"}
login    → accessToken payload {"sub":"01a06109-f878-7706-aab0-5466fb311d26","tokenType":"ACCESS","iat":…,"exp":…}
```

구현: `data/remote/auth/JwtSubject.kt` 의 `internal fun jwtSubject(token: String): String?`

- **서명은 검증하지 않는다.** 이 값은 화면 표시·자기 식별에만 쓰이고 권한 판정에는 쓰이지 않는다(권한은 서버가 서명을 검증해 정한다). 앱은 서명 키를 가질 수 없어 검증할 방법 자체가 없고, 위조해도 얻는 건 "내 화면에 남의 UUID 가 보인다"뿐이다.
- **base64url 디코딩에 okio 를 썼다.** `java.util.Base64` 는 API 26+ 인데 minSdk 가 24 이고 디슈가링 대상도 아니다. `android.util.Base64` 는 JVM 단위 테스트에서 동작하지 않는다. okio(`ByteString.decodeBase64`)는 표준/URL-safe 알파벳을 모두 받고 패딩이 없어도 되며 두 환경에서 같게 동작한다.
- **파싱 실패는 예외로 끊는다.** UUID 없이 세션을 열면 `AuthState.Authenticated("")` 라는 "로그인은 됐는데 내가 누군지 모르는" 상태가 조용히 굳는다.

액세스 토큰 수명은 실측 **10분**(`exp - iat = 600`). 리프레시는 약 14일.

---

## 3. 로그인 전 호출되는 API — **없다** (점검 결과)

`MainNavGraph` 기준 로그인 전 도달 가능한 화면은 온보딩 3개 · 로그인 2개 · 가입 4개 · 가입완료 1개다.
이 10개 화면이 참조하는 Repository 는 **`authRepository` 하나뿐**이고, 그 호출은 전부 permitAll 구간(`/api/v1/auth/…`)으로 나간다.

- 스플래시는 `AuthState.Unknown` 동안 네트워크를 치지 않고 디스크 복원만 기다린다.
- `startDestination` 이 `loggedIn` 으로 갈리므로 HOME·EXPLORE 등 도메인 API 화면은 로그인 뒤에만 열린다.

→ **화면 수정 필요 없음.** 다만 새로 잠긴 것이 있다:

| 엔드포인트 | 이전 | 지금 |
|---|---|---|
| `GET /api/v1/matching/rooms` (목록) | 공개 | **401** |
| `GET /api/v1/places?query=` (장소 검색) | 공개 | **401** |
| `GET /api/v1/places/reverse` (역지오코딩) | 공개 | **401** |

지금 구조에선 이 셋이 로그인 뒤에만 불리므로 문제가 없지만, **"로그인 전 미리보기" 같은 화면을 나중에 추가하면 그 순간 깨진다.** `MatchingApi`·`PlaceApi` KDoc 에 "전체가 토큰 필수"라고 못 박아 뒀다.

---

## 4. 방 생성 — `creatorId` 전송 제거, `NewParty.hostId` 는 유지

서버 `OpenPartyRequest.toCommand(creatorMemberId)` 가 본문의 `creatorId` 를 **버리고** `@CurrentUser` 를 쓴다(주석에도 "구버전 호환용으로 받기만 하고 무시한다"). 실측으로 `creatorId` 없이 200 이고 토큰 주체가 방장으로 기록됐다.

- `OpenPartyRequestDto` 에서 `creatorId` **필드 자체를 삭제**했다. 보내 봐야 무시되는 값을 남겨 두면 "앱이 보낸 방장이 유효하다"는 착각을 부른다.
- `NewParty.hostId` 는 **시그니처 유지**(하위호환) + KDoc 에 "서버가 무시, 토큰 주체를 쓴다" 명시. presentation 파급 0.
- 남은 쓰임은 하나뿐이다: 생성 응답에 `members` 가 없어 로컬에서 방장 1명을 만들어 보여 주는 표시값(`Ride.hostId`).

**리포트 22 §5 D-5 는 이것으로 해소됐다** — 이제 id 가 1 이 아닌 계정으로 방을 만들어도 서버는 그 계정을 방장으로 기록한다(실측: 신규 계정으로 생성 → 같은 계정으로 나가기 204).

---

## 5. 조용한 지뢰: 에러 코드가 이름에서 번호로 바뀌었다

이건 지시서에 없던 발견이고, **경로보다 위험하다** — 컴파일도 통과하고 요청도 성공하는데 사용자에게 틀린 메시지가 뜬다.

`UserErrorCode` 가 enum 이름(`LOGIN_FAILED`) 대신 고정 코드(`USER102`)를 내보낸다. 앱의 `toAuthException()` 은 옛 이름으로 분기하고 있었으므로 **전부 상태 코드 폴백으로 떨어졌고**, 폴백은 401 을 `SESSION_EXPIRED` 로 매핑한다:

> 아이디/비밀번호를 틀리면(401 `USER102`) 로그인 화면에 **"세션이 만료되었습니다"** 가 뜬다.

실측 코드표와 매핑 (`AuthMappers.kt`):

| 코드 | 상태 | 서버 메시지 | → AuthError |
|---|---|---|---|
| `USER001`~`USER005` | 401 | 토큰 무효/만료/용도불일치/재사용/무토큰 | `SESSION_EXPIRED` |
| `USER101` | 409 | 이미 존재하는 아이디입니다. | `LOGIN_ID_DUPLICATED` |
| `USER102` | 401 | 아이디나 비밀번호가 다릅니다. | `LOGIN_FAILED` |
| `USER103` | 404 | 존재하지 않는 사용자입니다. | `SESSION_EXPIRED` |
| `INVALID_REQUEST` | 400 | `필드: 사유` (이름 그대로 남았다) | `INVALID_REQUEST` |

---

## 6. 확정 시그니처 (compose-builder 계약) — **변경 없음**

도메인 경계면은 하나도 바뀌지 않았다. presentation 은 손댈 것이 없다.

```kotlin
// domain/repository/AuthRepository.kt — 시그니처 그대로
suspend fun register(newUser: NewUser): String   // 반환 uuid: 서버 응답 그대로
suspend fun login(loginId: String, password: String): String
                                                 // 반환 uuid: 이제 액세스 토큰 sub 에서 파생
suspend fun logout()
val authState: StateFlow<AuthState>

// domain/repository/RideRepository.kt — 그대로
suspend fun createParty(request: NewParty): Ride // NewParty.hostId 유지(서버는 무시)
suspend fun joinParty(partyId: Long): Ride
suspend fun leaveParty(partyId: Long)            // 내부 경로만 /0 제거

// domain/session/UserSession.kt — 그대로
val currentUserId: Long                          // 이제 서버로 나가는 곳은 채팅 X-User-Id 뿐
val currentUserUuid: String?
```

---

## 7. 변경 파일

**data/remote**: `AuthApi.kt`(경로 4개·응답 타입), `AuthMappers.kt`(에러 코드), `MatchingApi.kt`(leave 경로·openParty·인터페이스 KDoc), `PlaceApi.kt`(공개 아님 명시), `ChatApi.kt`(헤더 2개 필요 명시), `auth/JwtSubject.kt`(**신규**), `auth/AuthHeaderInterceptor.kt`(KDoc 정정)
**data/remote/dto**: `AuthDtos.kt`(`LoginResponse` **삭제** → `TokenResponse` 공유), `PartyDtos.kt`(`creatorId` 삭제)
**data/repository**: `RemoteAuthRepository.kt`(sub 파싱)
**domain**: `model/NewParty.kt`, `repository/AuthRepository.kt`, `session/UserSession.kt` — 전부 KDoc만
**test**: `remote/auth/JwtSubjectTest.kt`(**신규 4개**), `remote/AuthenticatedPathContractTest.kt`, `remote/AuthMappersTest.kt`, `remote/PartyMappersTest.kt`, `repository/RemoteAuthRepositoryTest.kt`

### 테스트 보강 지점 — 왜 이것들인가

`AuthenticatedPathContractTest` 에 **인증 4경로 고정**을 추가했다. Retrofit 경로는 애노테이션 문자열이라 컴파일러가 못 잡고, 이번엔 틀렸을 때 404 가 아니라 **401** 이 와서 원인이 더 흐려진다. 나가기 경로는 더미 `/0` 이 되살아나지 않도록 값을 반대로 뒤집어 고정했다.

`PartyMappersTest` 는 `creatorId` 를 **본문에서 찾지 못해야 통과**하도록 뒤집었다. 되살아나도 서버가 조용히 무시하므로 테스트가 없으면 아무도 눈치채지 못한다.

`JwtSubjectTest` 는 실서버가 발급한 진짜 토큰을 그대로 넣었고, 별도로 `-`/`_` 가 모두 들어간 페이로드를 만들어 넣었다 — UUID 만 든 실제 페이로드에는 그 문자가 잘 안 나와서 표준 base64 디코더로 바꿔치기해도 진짜 토큰 테스트는 통과해 버린다.

`AuthMappersTest` 의 401 케이스는 **`USER102` 가 `SESSION_EXPIRED` 가 아님**을 못 박는 게 핵심이다(§5).

---

## 8. 실서버 스모크 (전부 실측)

신규 계정 `authv2u163358` / `Passw0rd!` 로 전 구간을 탔다.

| 호출 | 결과 |
|---|---|
| `POST /auth/register` (신규) | **201** `{"uuid":"01a06109-f878-7706-aab0-5466fb311d26"}` |
| `POST /auth/register` (중복) | 409 `USER101` |
| `POST /auth/register` (loginId 규칙 위반) | 400 `INVALID_REQUEST` `"loginId: 아이디는 영소문자로…"` |
| `POST /auth/login` | **200** `{accessToken, refreshToken}` — userId 없음 |
| 액세스 토큰 `sub` 디코드 | `01a06109-f878-7706-aab0-5466fb311d26` = **register uuid 와 일치** |
| `POST /auth/login` (오답) | 401 `USER102` |
| `POST /users/login` (옛 경로) | **401** (404 아님) |
| `POST /auth/reissue` | 200, 새 토큰 쌍 |
| `POST /auth/reissue` (옛 리프레시 재사용) | 401 `USER001` — 회전 확인 |
| `POST /auth/logout` | 204 |
| `POST /matching/rooms` (creatorId **없이**) | **200**, 토큰 주체가 방장 |
| `POST /matching/rooms/{id}/join` | 200, 방 상세 반환 |
| `DELETE /matching/leave/{id}` | **204** |
| `DELETE /matching/leave/{id}/0` (옛 경로) | **404** |
| `DELETE /matching/leave/{id}` (무토큰) | 401 `USER005` |
| `GET /matching/rooms` (무토큰 / 토큰) | **401 `USER005`** / 200 |
| `GET /places?query=` (무토큰 / 토큰) | **401** / 200 |
| `GET /users/me/favorite-places` | 200 `{"places":[]}` |
| `POST /users/me/favorite-places` | **500** — D-4 미해결 (§9) |
| `GET /chat-rooms/me` (토큰+헤더 / 무토큰 / 헤더누락) | 200 `[]` / 401 `USER005` / 400 |

testuser1 로도 새 경로 join(200) → leave(204) 왕복을 확인했다. testuser1 의 `sub` = `01a06107-dd06-7c74-82f4-8a85dd6b7575`.

### 서버 상태 (qa-verifier 인계)

스모크가 끝난 뒤 ACTIVE 방 **2개**를 남겨 뒀다. 둘 다 **testuser1 이 아닌 계정**이 만든 방이라 앱에서 합류 플로우를 그대로 탈 수 있다.

- `partyId=1` 부산대 정문 → 서면역 (1/3명, 방장 qahost01)
- `partyId=3` 장전역 → 부산역 (1/3명, 방장 authv2u163358)

주의: 마지막 멤버가 나가면 방이 닫힌다. 소진되면 아무 계정으로 `POST /matching/rooms` 하면 되고, **`creatorId` 는 넣지 않는다.**

---

## 9. 미해결 · 백엔드 요청

### 🔴 D-4 (리포트 22 에서 이월, **여전히 재현**) — `POST /users/me/favorite-places` 가 항상 500

```
POST /api/v1/users/me/favorite-places  {"placeName":"강남역","roadName":"…","latitude":37.4979,"longitude":127.0276}
→ 500 {"timestamp":…,"status":500,"error":"Internal Server Error", …}
```

`{code,message}` 규격이 아닌 스프링 기본 응답 = 핸들러 안에서 처리되지 않은 예외. 같은 토큰의 GET 은 200 이라 인증 문제가 아니다. 유력 원인은 22번과 동일(`FavoritePlaceId` 에 public 무인자 생성자 없음 → JPA 가 IdClass 인스턴스화에 실패). **앱에서 고칠 것은 없다.** 즐겨찾기 "추가"는 실패하는 전제로 QA 할 것.

### 🟡 채팅 X-User-Id 와 토큰 주체가 서로 검증되지 않는다

채팅만 `@RequestHeader("X-User-Id")` 로 남아, 이제 **Bearer + X-User-Id 를 동시에** 요구한다(실측: 토큰 없으면 401, 헤더 없으면 400). 그런데 **둘을 대조하지 않는다** — 헤더에 아무 id 나 넣어도 통과한다. 앱은 `FIXED_MEMBER_ID = 1` 을 싣고 있으므로 로그인 계정의 실제 서버 id 가 1 이 아니면 **남의 채팅방 목록을 보게 된다.** 인증 관점의 결함이기도 하다.

### 🟡 `RideDetail` 방장 배지가 여전히 고정 id 로 판정된다

`RideDetailRoute.kt:103` 의 `currentUserId = userSession.currentUserId.toString()` 은 고정 `"1"` 이다. 서버 멤버 목록은 진짜 로그인 사용자 기준이라, id 가 1 이 아닌 계정에서는 방장 배지와 "나 제외" 필터가 어긋난다. **이번 범위 밖이라 손대지 않았다** — 채팅이 `@CurrentUser` 로 넘어가 `currentUserId` 가 사라지면 함께 정리된다.

### 백엔드 요청 사항

| # | 내용 | 상태 |
|---|---|---|
| R-1 | leave 경로에서 `/{memberId}` 세그먼트 제거 | ✅ **완료** |
| R-2 | 방 생성을 토큰 주체로 전환 | ✅ **완료** (`creatorId` 필드는 잔재로 남음 — 지워도 앱은 영향 없다) |
| R-3 | 즐겨찾기 저장 500 수정 (D-4) | ❌ 미해결 |
| R-4 | 채팅 `X-User-Id` → `@CurrentUser` 전환 | ❌ 미해결 — 이제 **인증 결함**이다(위 🟡) |
| R-5 | `ReportController.confirmCall` 의 소유자 검증 미구현 | 미확인 (이번 범위 밖) |

### 문서만 낡은 곳 (compose-builder 참고, 컴파일 무관)

presentation 주석 3곳이 옛 가입 경로를 가리킨다 — 동작에는 영향 없다.
`core/Routes.kt:16`, `feature/auth/ProfileSetupScreen.kt:65`, `feature/auth/SignupDraft.kt:12` 의 `POST /api/v1/users` → `POST /api/v1/auth/register`.
