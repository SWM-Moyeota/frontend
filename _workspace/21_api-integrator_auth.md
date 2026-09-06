# 21. 인증 연동 (data/·domain/ + app DI) — api-integrator

작업일: 2026-09-02 / 브랜치: `feature/naver-map`
검증: `./gradlew :data:testDebugUnitTest :domain:compileDebugKotlin :app:assembleDebug` **BUILD SUCCESSFUL**
실서버 검증: **완료** (localhost:8080, curl 실측 — 아래 응답 shape은 전부 실측값)

---

## 1. 연동한 엔드포인트 (실측)

| 메서드 | 경로 | 요청 | 응답 |
|---|---|---|---|
| POST | `/api/v1/users` | `{loginId,password,name,birthDate,phoneNumber,gender,email}` | 201 `{"uuid":"d4ff55b9-…"}` |
| POST | `/api/v1/users/login` | `{loginId,password}` | 200 `{"userId":"…","accessToken":"…","refreshToken":"…"}` |
| POST | `/api/v1/auth/reissue` | `{refreshToken}` | 200 `{"accessToken":"…","refreshToken":"…"}` |
| POST | `/api/v1/auth/logout` | `{refreshToken}` | 204 (본문 없음) |

실측 에러 본문 (모두 `{code,message}`):

| 상황 | 상태 | 본문 |
|---|---|---|
| 아이디 중복 | 409 | `{"code":"LOGIN_ID_DUPLICATED","message":"이미 존재하는 아이디입니다."}` |
| 검증 실패 | 400 | `{"code":"INVALID_REQUEST","message":"password: 비밀번호는 영문, 숫자, 특수문자를 각각 하나 이상 포함해야 합니다."}` |
| 로그인 실패 | 401 | `{"code":"LOGIN_FAILED","message":"아이디나 비밀번호가 다릅니다."}` |
| 옛 리프레시 재사용 | 401 | `{"code":"UNAUTHORIZED","message":"로그인이 필요합니다."}` |

실측으로 확인한 서버 동작:
- **회전(rotation) 확인**: reissue 성공 직후 같은 리프레시로 재요청 → 401. 응답의 새 refreshToken을 저장하지 않으면 세션이 끊긴다.
- **logout 멱등 확인**: 같은 토큰으로 두 번 호출 → 둘 다 204. 이후 reissue는 401.
- **필터는 차단하지 않음 확인**: `Authorization: Bearer garbage` 를 달고 `GET /api/v1/matching/rooms` → **200 정상 응답**. 공개 조회는 토큰 유무·유효성과 무관하게 동작한다.
- **액세스 토큰 수명 = 10분** (JWT `exp - iat` = 600s). 백엔드 주석의 "최대 30분"과 다르다 — 실측 우선.

---

## 2. compose-builder 계약 (확정 시그니처)

### `domain/repository/AuthRepository.kt`

```kotlin
interface AuthRepository {
    val authState: StateFlow<AuthState>
    suspend fun register(newUser: NewUser): String   // 반환: 생성된 uuid
    suspend fun login(loginId: String, password: String): String  // 반환: uuid
    suspend fun logout()
}
```

주의할 계약 세 가지.

1. **`register` 는 로그인시키지 않는다.** 서버가 토큰 없이 uuid만 준다. 가입 성공 후 화면이 같은 자격증명으로 `login` 을 **이어서 호출**해야 홈으로 갈 수 있다.
2. **`login` 성공 시 토큰 저장·상태 전환이 Repository 안에서 끝난다.** ViewModel이 따로 세션을 저장할 필요 없다.
3. **`logout` 은 절대 실패하지 않는다.** 로컬을 먼저 비우고 서버 무효화는 최선 노력으로 시도한다(오프라인이어도 사용자는 로그아웃된다).

### 세션 상태 관찰 방법

`AuthRepository.authState` (또는 동일 값인 `UserSession.authState`) 를 구독한다.

```kotlin
sealed interface AuthState {
    data object Unknown : AuthState                       // 디스크 복원 전 (앱 시작 직후)
    data object Unauthenticated : AuthState
    data class Authenticated(val userUuid: String) : AuthState
}
```

- **`Unknown` 을 반드시 별도 처리할 것.** 앱 시작 직후에는 DataStore에서 토큰을 아직 읽지 못한 상태다. 이때 `Unauthenticated` 로 취급하면 **이미 로그인한 사용자가 매번 로그인 화면을 스쳐 본다.** `Unknown` 동안은 스플래시/로딩을 유지한다.
- **세션 만료 이벤트도 이 스트림으로 온다.** 재발급까지 실패하면 OkHttp Authenticator가 상태를 `Unauthenticated` 로 바꾼다. 최상위 NavGraph에서 `authState` 를 관찰해 `Unauthenticated` 로 전이되면 로그인 화면으로 보내면, 사용자가 어느 화면에 있든 일관되게 처리된다. 별도 이벤트 채널은 없다.

### `domain/model/Auth.kt`

```kotlin
enum class Gender { MALE, FEMALE }

data class NewUser(
    val loginId: String, val password: String, val name: String,
    val birthDate: java.time.LocalDate,     // 화면은 날짜만 다룬다 (타임존 변환은 data 매퍼 몫)
    val phoneNumber: String,                // "010-1234-5678" / "01012345678" 둘 다 통과
    val gender: Gender, val email: String,
)
```

### `domain/model/AuthError.kt` — 에러 분기

`AuthRepository` 의 모든 실패는 `AuthException(error: AuthError, serverMessage: String?)` 로 던진다. HttpException/IOException은 presentation까지 새지 않는다.

| `AuthError` | 발생 | 권장 문구 |
|---|---|---|
| `LOGIN_FAILED` | 로그인 401 | "아이디 또는 비밀번호가 올바르지 않습니다." |
| `LOGIN_ID_DUPLICATED` | 가입 409 | "이미 사용 중인 아이디입니다." |
| `INVALID_REQUEST` | 가입/로그인 400 | `serverMessage` 를 그대로 노출 가능 ("필드: 사유" 형태라 의미가 통한다) |
| `SESSION_EXPIRED` | 401 UNAUTHORIZED/TOKEN_EXPIRED | "다시 로그인해 주세요." |
| `NETWORK` | 연결 실패·타임아웃 | "네트워크 연결을 확인해 주세요." + 재시도 버튼 |
| `UNKNOWN` | 5xx·해석 불가 | "잠시 후 다시 시도해 주세요." |

### `domain/model/Auth.kt` — `AuthPolicy` (제출 전 클라이언트 검증)

서버 Bean Validation 규칙을 그대로 옮겼다. `isValidLoginId` / `isValidPassword` / `isValidPhoneNumber` 를 화면에서 호출하면 왕복 400을 줄일 수 있다(최종 판정은 서버). 규칙: loginId `^[a-z][a-z0-9_]{3,19}$`, 비밀번호 8~64자 + 영문·숫자·특수문자 각 1개 이상, 이름 20자·이메일 100자 제한.

### `domain/session/UserSession.kt` (확장)

```kotlin
interface UserSession {
    val currentUserId: Long          // 고정 1L — 변경 없음 (아래 제약 참조)
    val authState: StateFlow<AuthState>
    val currentUserUuid: String?     // 미로그인/복원 전이면 null
    val isLoggedIn: Boolean          // Unknown 이면 false
    companion object { const val FIXED_MEMBER_ID = 1L }
}
```

기존 `currentUserId` 사용처는 **한 줄도 고칠 필요 없다.** `FixedUserSession` 은 삭제되고 `SessionManager` 가 그 자리를 대신한다(`AppContainer` 배선 완료).

---

## 3. 구현 구조

```
AuthApi (data/remote)  ── authClient: Bearer 없음, Authenticator 없음
   └ AuthDtos, AuthMappers(LocalDate→Instant, HttpException→AuthException)
RemoteAuthRepository ──> SessionManager ──> DataStoreTokenStorage
                            │  (UserSession + TokenHolder 겸함)
                            └──> AuthHeaderInterceptor / TokenAuthenticator  ── apiClient
```

**클라이언트를 둘로 나눈 이유**: 인증 엔드포인트에 Authenticator가 달려 있으면 재발급 요청이 401을 맞았을 때 다시 재발급을 트리거해 무한 루프가 된다. 구조적으로 차단했다(`NetworkModule.create`). 두 클라이언트는 `newBuilder()` 파생이라 커넥션 풀·디스패처를 공유한다.

**Authenticator 동작** (`TokenAuthenticator`):
1. 요청에 Bearer가 없으면 손대지 않는다 (미로그인 상태의 401은 재발급으로 못 푼다).
2. `priorResponse` 체인이 1 이상이면 포기 — 새 토큰으로도 401이면 무한 루프 대신 401을 올려보낸다.
3. 갱신 구간 전체를 `synchronized` — 잠금 획득 시 토큰이 이미 바뀌어 있으면 **재발급하지 않고** 그 토큰으로 재시도한다. 서버가 회전 방식이라 동시 재발급 시 늦은 쪽이 "이미 회전됨" 401을 맞고 멀쩡한 세션이 날아가기 때문.
4. 재발급 실패 → `TokenHolder.onSessionExpired()` → `authState = Unauthenticated`.

**앱 시작 시 경합 방지**: DataStore 복원이 끝나기 전에 나간 요청이 토큰 없이 401을 맞고 억울하게 로그아웃되는 걸 막기 위해, `SessionManager` 의 동기 토큰 getter는 복원 완료(`CountDownLatch`, 5초 안전 타임아웃)를 기다린다. OkHttp I/O 스레드에서만 호출되므로 메인 스레드는 막지 않는다.

---

## 4. 저장소 선택 근거 — DataStore Preferences

| | DataStore Preferences (**선택**) | EncryptedSharedPreferences |
|---|---|---|
| 유지보수 | 현행 권장 | `androidx.security:security-crypto` **deprecated**, stable 후속 없음 |
| API | suspend/Flow — 앱 시작 복원을 코루틴으로 | 동기 API → 메인 스레드 디스크 I/O 유발 |
| 암호화 | 없음 | 있음 (Tink) |

deprecated 라이브러리를 지금 붙이면 곧 마이그레이션 부채가 되므로 DataStore를 택했다.

**의도적 트레이드오프 — 저장 내용은 암호화되지 않는다.** 방어선은 앱 전용 내부 저장소(다른 앱 접근 불가) + 기기 전체 암호화이며, **루팅/디버거블 단말에서는 토큰이 읽힐 수 있다.** 완화책: 액세스 토큰 수명 10분, 리프레시 회전, 로그아웃 시 서버 무효화. 보안 요구가 올라가면 `TokenStorage` 인터페이스 구현체만 교체하면 된다(그 목적으로 인터페이스를 분리해 뒀다).

---

## 5. 제약 및 리더 확인 필요 사항

### 🔴 D-3 (신규·중요) — 백엔드가 이미 `@LoginUser` 로 마이그레이션했다 (미배포)

리더 지시서에는 "백엔드 TODO: @LoginUser 마이그레이션 예정" 이라고 되어 있으나, **실제로는 이미 작성되어 백엔드 워킹트리에 미커밋 상태로 존재한다** (`feature/userdomain`, `git status` 기준 M 표시). 현재 **실행 중인 서버는 옛 코드**라 앱은 지금 정상 동작하지만, 백엔드를 현재 소스로 재빌드/재시작하는 순간 아래가 전부 깨진다.

| 앱 현재 호출 (실행 서버 = OK) | 백엔드 워킹트리 (재빌드 시) | 영향 |
|---|---|---|
| `POST /matching/rooms/{partyId}/{memberId}/join` | `POST /matching/rooms/{partyId}/join` + `@LoginUser` | 404 → 경로 변경 + 토큰 필수 |
| `GET /dispatch/rides/{partyId}/{memberId}` | `GET /dispatch/rides/{partyId}` + `@LoginUser` | 404 |
| `PATCH /reports/{reportId}/call-result` | `PATCH /reports/call-result` + `@LoginUser` | 404 |
| `POST /reports` (body에 reporterId) | body에서 제거, `@LoginUser reporterId` | 400 |
| `DELETE /matching/leave/{partyId}/{memberId}` | 경로는 같으나 `@LoginUser` 사용 (path var 무시) | memberId 무시됨 |
| `/users/me/favorite-places` | `@LoginUser` | 401 |

실측 근거 (실행 중인 서버):
- `POST /api/v1/matching/rooms/1/join` → **404 Not Found** (스프링 기본 404, 핸들러 없음)
- `POST /api/v1/matching/rooms/1/1/join` → **404 `{"code":"PARTY_NOT_FOUND"}`** (핸들러 있음, 방만 없음)
- `POST /api/v1/reports` (빈 body) → **400 `{"code":"INVALID_REQUEST","message":"reporterId: 널이어서는 안됩니다"}`** (여전히 body 필드)

또한 백엔드 워킹트리의 `ReportController.confirmCall(@LoginUser Long reportId, …)` 는 **변수명이 reportId지만 실제로는 로그인 사용자 id가 주입되는 버그**로 보인다 — 백엔드에 확인 요청 권장.

→ **조치 제안**: 백엔드 팀에 배포 시점을 확인하고, 배포되는 시점에 맞춰 `MatchingApi`/`DispatchApi`/`ReportApi`/`PlaceApi` 경로와 `UserSession.currentUserId` 제거를 한 번에 처리하는 후속 작업을 잡을 것. **이번 작업 범위에는 넣지 않았다** (현재 실행 서버를 깨뜨리므로).

### 🟡 `currentUserId = 1L` 유지 (지시대로)

지시대로 고정 `1L` 을 유지했다. 결과적으로 **누가 로그인해도 매칭·배차·신고·채팅 API 는 memberId=1 의 데이터를 본다.** 로그인한 사용자의 UUID(`currentUserUuid`)와 도메인 API가 쓰는 memberId는 현재 이어져 있지 않다. 위 D-3이 해소되면 이 프로퍼티는 통째로 사라진다.

### 🟡 Authenticator 는 현재 실질적으로 발화하지 않는다

실행 중인 서버의 도메인 API는 `@LoginUser` 를 쓰지 않아 401을 내지 않는다(garbage Bearer로도 200 확인). 즉 **401→reissue 경로는 지금 실서버에서 발화할 일이 없다.** 로직 자체는 단위 테스트로 검증했고(아래), D-3 배포 후에 실제로 동작하게 된다. **qa-verifier 실기 검증 시 이 경로는 재현 불가**임을 감안할 것.

### 🟡 core library desugaring 신규 도입

`birthDate` 를 도메인에서 `java.time.LocalDate` 로 다루라는 지시를 따르려면 minSdk 24에서 java.time이 필요하다. `domain`·`data`·`presentation`·`app` 네 모듈에 `isCoreLibraryDesugaringEnabled = true` + `coreLibraryDesugaring(libs.desugar.jdk.libs)` 를 추가했다(버전 카탈로그 `desugarJdkLibs = "2.1.5"`). 부수 효과로 앱 전역에서 java.time 사용이 가능해졌다 — 기존 `Calendar` 기반 시각 포맷 코드(`DestinationConfirmRoute.kt`)를 정리할 여지가 생겼으나 이번엔 건드리지 않았다.

### 🟢 birthDate 타임존

`LocalDate.atStartOfDay(ZoneOffset.UTC)` 로 변환한다. KST 자정으로 잡으면 서버에 **하루 전날**(전날 15:00Z)로 저장되므로 UTC 고정이 필수다. 결과 형식 `2000-01-01T00:00:00Z` 는 실서버 201 응답으로 확인했다.

---

## 6. 테스트 결과

`./gradlew :data:testDebugUnitTest` — **88 tests, 0 failures** (신규 34개)

| 클래스 | 개수 | 검증 내용 |
|---|---|---|
| `AuthMappersTest` | 12 | LocalDate→UTC Instant, 요청 DTO 필드명, 응답 파싱(가입/로그인/재발급), 에러코드→AuthError 6종, code 없는 응답의 상태코드 폴백, IOException→NETWORK, AuthPolicy 규칙 |
| `TokenAuthenticatorTest` | 7 | 401→재발급→새 Bearer로 재시도 / 회전된 리프레시 저장 / 재발급 실패→세션만료 / 리프레시 없음→재발급 시도 안 함 / Bearer 없는 401 무시 / priorResponse 있으면 포기(무한루프 방지) / **4스레드 동시 401 → 재발급 1회, 전원 새 토큰으로 재시도** |
| `SessionManagerTest` | 8 | 재시작 복원, 미로그인 확정, memberId 고정, 로그인 영속화, 재발급 반영, 세션만료 초기화, 로그아웃 리프레시 반환, 멱등 로그아웃 |
| `RemoteAuthRepositoryTest` | 7 | 가입이 로그인시키지 않음, 로그인 영속화, 401→LOGIN_FAILED, 409→중복, 로그아웃 서버 호출, **서버 로그아웃 실패해도 로컬은 비워지고 예외 없음**, 미로그인 로그아웃은 서버 미호출 |

---

## 7. 변경 파일

**신규 (domain)**: `model/Auth.kt`, `model/AuthError.kt`, `repository/AuthRepository.kt`
**변경 (domain)**: `session/UserSession.kt`, `build.gradle.kts`
**신규 (data)**: `remote/AuthApi.kt`, `remote/dto/AuthDtos.kt`, `remote/AuthMappers.kt`, `remote/auth/{AuthHeaderInterceptor,TokenRefresher,TokenAuthenticator}.kt`, `session/{TokenStorage,DataStoreTokenStorage,TokenHolder,SessionManager}.kt`, `repository/RemoteAuthRepository.kt`
**변경 (data)**: `remote/NetworkModule.kt`, `build.gradle.kts` / **삭제**: `session/FixedUserSession.kt`
**신규 테스트**: `remote/AuthMappersTest.kt`, `remote/auth/TokenAuthenticatorTest.kt`, `session/SessionManagerTest.kt`, `repository/RemoteAuthRepositoryTest.kt`
**변경 (app)**: `AppContainer.kt`(+`context` 파라미터, `authRepository` 노출), `MoyeotaApplication.kt`, `build.gradle.kts`
**변경 (기타)**: `gradle/libs.versions.toml`, `presentation/build.gradle.kts`(desugaring만)

`MainNavGraph` 배선은 건드리지 않았다 — `container.authRepository` 를 노출해 뒀으니 compose-builder가 연결한다.
