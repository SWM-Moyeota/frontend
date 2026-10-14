# 121 — API 연동: 회원 탈퇴 (입력 §B, Backend #206 / PR #216)

작업: `_workspace/00_input_resume-qa-fixes.md` §B
백엔드 기준: 읽기 전용 체크아웃 `origin/develop` = **`88d586d`** ("Feature 회원 탈퇴 기능 추가").
`user/deletion/` 전체 + `user/common/exception/UserErrorCode.java` + `git log af1e4cf..origin/develop` 12커밋 직접 확인.

> **경로 정정**: 입력 문서가 `user/exception/UserErrorCode.java` 라고 적었지만 실제 경로는
> **`user/common/exception/UserErrorCode.java`** 다. `4658c34` 가 옮긴 것은 `user/common/domain` →
> `user/users/domain` 이고 `user/common/exception`·`user/common/crypto`·`user/common/masking` 는 그대로다.

> **실서버 미검증.** 로컬·dev 서버를 띄우지 않았다(지시대로 실기 검증 없음). 아래 모든 shape 은
> **컨트롤러·서비스·enum·통합테스트 소스 기준**이다. 앱 쪽은 네트워크 없이 Retrofit 을 직접 돌려
> 요청 모양만 실측했다(§7).

---

## 1. 연동한 엔드포인트

| 메서드 | 경로 | 인증 | 요청 본문 | 응답 |
|---|---|---|---|---|
| DELETE | `/api/v1/users/me` | **필수 (Bearer)** | `{"password":"..."}` | **204 No Content** (본문 없음) |

근거: `user/deletion/presentation/UserDeletionController.java`
— `@RequestMapping("/api/v1/users")` + `@DeleteMapping("/me")` + `@ResponseStatus(NO_CONTENT)`,
`@CurrentUser Long userId` + `@Valid @RequestBody UserDeletionRequest(@NotBlank String password)`.

### 왜 `UserApi`(인증 있음)인가 — `NetworkModule` 을 읽고 판단한 결과

`NetworkModule.create` 는 클라이언트를 **둘** 만든다.

| 클라이언트 | Bearer | 401 재발급 | 쓰는 API |
|---|---|---|---|
| `publicRetrofit` (= `baseClient`) | ✗ | ✗ | `AuthApi`, `ConfigApi` |
| `retrofit` (= `apiClient`) | ✓ | ✓ | `MatchingApi`·`PlaceApi`·`ChatApi`·`DispatchApi`·`ReportApi`·**`UserApi`** |

`AuthApi` 는 `publicRetrofit` 이다 → 거기 두면 무토큰 요청이 되어 **401 `USER005`** 다.
`SecurityConfig` 의 permitAll 은 `/api/v1/auth/` 하위·`/api/v1/config`·`/ws-chat/` 하위·
`/health`·`/prometheus`·`/info` 뿐이고 `/api/v1/users` 하위는 포함되지 않는다.
→ **`UserApi.deleteAccount`**. `AuthenticatedPathContractTest` 가 "탈퇴가 `AuthApi` 로 옮겨가면 실패"까지 못 박았다.

---

## 2. Retrofit DELETE + body 를 어떻게 풀었나 (지시받은 함정)

**함정은 실재했다.** Retrofit 의 `@DELETE` 는 선언 자체가 `hasBody = false` 라 `@Body` 와 함께 쓸 수 없는데,
**컴파일러가 막지 않는다.** 터지는 건 Retrofit 이 서비스 메서드를 파싱하는 **실행 시점**이고,
그 시점은 사용자가 「탈퇴하기」를 누른 순간이다:

```
java.lang.IllegalArgumentException: Non-body HTTP method cannot contain @Body or @Field parameters.
```

### 쓴 것

```kotlin
// data/remote/UserApi.kt
@HTTP(method = "DELETE", path = "api/v1/users/me", hasBody = true)
suspend fun deleteAccount(@Body request: AccountDeletionRequest)
```

### 고려하고 버린 대안

| 대안 | 버린 이유 |
|---|---|
| `@DELETE` + `@Query("password")` | **비밀번호가 URL·서버 액세스 로그·프록시에 평문으로 남는다.** 논외 |
| `@POST` 로 보내기 | 서버가 `@DeleteMapping` 만 매핑한다 → 405 |
| `@HTTP(hasBody = false)` + 본문 생략 | 서버 `@NotBlank` 로 400. 비밀번호를 보낼 자리가 사라진다 |

### 검증 방법 (MockWebServer 없이 — 외부 라이브러리 추가 금지)

OkHttp 애플리케이션 인터셉터가 `chain.proceed()` 를 **부르지 않고** 응답을 직접 만들어 돌려주면
소켓이 열리지 않는다. 이 방법으로 `UserApiDeleteBodyTest`(신규, 4건)가 네트워크 없이 확인한다:

1. **`validateEagerly(true)`** 로 `UserApi` 의 **모든** 메서드를 `create()` 시점에 파싱 — Retrofit 이 거부하면 즉시 실패
2. 실제 나간 요청이 `DELETE http://localhost/api/v1/users/me`
3. **본문이 실제로 `{"password":"Passw0rd!"}`** 로 실렸다 (`request.body` 를 okio 로 읽어 비교)
4. 비밀번호가 쿼리 문자열에 없다 (`url.query == null`)
5. 204 + 빈 본문이 `Unit` 반환으로 예외 없이 끝난다
6. **음성 대조군**: 같은 서명을 `@DELETE` 로 선언한 private 인터페이스는 Retrofit 이 위 메시지로 거부한다
   → 함정 자체가 테스트로 기록돼, 누군가 `@HTTP` 를 `@DELETE` 로 "정리"하면 그 테스트가 먼저 깨진다

부수 수정: `AuthenticatedPathContractTest.pathOrNull()` 이 **`@HTTP` 를 읽지 못해** `@HTTP` 를 쓴 메서드가
전수 검사("auth 프리픽스가 `UserApi` 에 있으면 안 된다", "memberId/userId 가 남아 있지 않다")에서
조용히 빠지고 있었다 → `@HTTP.path` 도 읽게 고쳤다.

---

## 3. 최종 시그니처

### `domain/repository/AuthRepository.kt` — 신규 1개

```kotlin
/** DELETE /api/v1/users/me (204, 토큰 필수, 본문에 비밀번호). 성공하면 세션·로컬 기억이 비워지고
 *  authState 가 Unauthenticated 로 전이한다 — 화면은 logout() 과 똑같은 전이를 받는다. */
suspend fun deleteAccount(password: String)
```

- `password`: **현재 비밀번호**. 서버가 `@NotBlank` 만 보고 해시를 비교하므로 **앱은 길이·형식을 검사하지 않는다.**
- 반환값 없음. 실패는 전부 `AuthException`.

### `domain/model/AuthError.kt` — 신규 3종

`REJOIN_RESTRICTED` (409 `USER109`) · `ONGOING_PARTY_EXISTS` (409 `USER110`) · `PASSWORD_MISMATCH` (400 `USER111`)

### `domain/model/AuthPolicy` — 신규 상수 1개

```kotlin
/** 서버 UserDeletions.RETENTION = 30일. 재가입 차단과 탈퇴 기록 보관에 같은 값을 쓴다. */
const val REJOIN_RESTRICTED_DAYS = 30
```

### `data/remote/dto/UserDtos.kt` — 신규 DTO

```kotlin
@Serializable data class AccountDeletionRequest(val password: String)
```

### `RemoteAuthRepository` 생성자 — **파라미터 1개 추가** (AppContainer 만 호출)

```kotlin
RemoteAuthRepository(api, userApi, session, fcmTokenRegistrar, activePartyStorage)
//                                                             ^^^^^^^^^^^^^^^^^^ 신규
```

`ActivePartyStorage`(data 계층 인터페이스)를 직접 받는다 — 필요한 게 `clear()` 하나이고,
도메인 계약끼리 서로 의존하게 만들 이유가 없다. `AppContainer` 는 **인스턴스를 하나만** 만들어
`activePartyRepository` 와 나눠 쓴다(DataStore 는 같은 파일을 두 번 열면 `IllegalStateException`).

### presentation (compose-builder 몫이 아니라 이번에 내가 배선했다)

```kotlin
// Routes
const val ACCOUNT_DELETE = "mypage/account-delete"   // 37 신규

// MyPageScreen
onDeleteAccount: () -> Unit = {}                     // → 37

// AccountDeleteRoute (37 진입점)
@Composable fun AccountDeleteRoute(
    repository: AuthRepository,
    onBack: () -> Unit = {},
    onSessionEndExpected: () -> Unit = {},   // 요청 **직전** — 세션 만료 오인 방지 (§6)
    onSessionKept: () -> Unit = {},          // 실패 → 위 표시 되돌리기
)   // 성공 콜백은 없다. NavGraph 의 로그인 전이가 이동을 맡는다

// 순수 로직 (테스트 대상)
internal val AccountDeletionWarnings: List<String>                   // 경고 4줄
internal fun canSubmitAccountDeletion(password: String, submitting: Boolean = false): Boolean
internal enum class AccountDeleteErrorSlot { PASSWORD_FIELD, BANNER }
internal fun accountDeleteErrorSlot(error: AuthError): AccountDeleteErrorSlot

// core:designsystem
@Composable fun DangerCtaButton(text, onClick, modifier, enabled, loading)   // 신규
```

---

## 4. 에러 매핑표

출처: `UserErrorCode.java:21-23` (코드·HTTP 상태·메시지를 enum 에서 직접 읽음) + 각 `throw` 지점.

| 서버 코드 | HTTP | 발생 지점 (소스) | → `AuthError` | 화면 대응 |
|---|---|---|---|---|
| `USER109` REJOIN_RESTRICTED | 409 | **`SmsVerificationService.send` L65-66** — 가입 경로 | `REJOIN_RESTRICTED` | 번호 입력란 아래 문구 + 「인증번호 받기」 **잠금**. 다른 번호로 바꾸면 풀림 |
| `USER110` ONGOING_PARTY_EXISTS | 409 | `UserDeletionService.delete` (`ongoingPartyChecker`) | `ONGOING_PARTY_EXISTS` | 37 버튼 위 **배너**: 「합승에서 먼저 나간 뒤에 탈퇴해 주세요」 |
| `USER111` PASSWORD_MISMATCH | 400 | `UserDeletionService.delete` ×2 (로컬계정 없음 / 해시 불일치) | `PASSWORD_MISMATCH` | 37 **입력란 오류** + 입력값 비움 |
| `USER005` UNAUTHORIZED 등 | 401 | JWT 필터 | `SESSION_EXPIRED` *(기존)* | 배너 → 로그인 |

### `USER109` 는 **가입 경로**다 — 입력 문서보다 한 단계 앞이다

입력 문서는 "`USER109`(409, **가입** 경로에서)"라고 적었는데, 코드로 확인하니 `register` 가 아니라
**`POST /api/v1/auth/sms/send`**(인증번호 발송)에서 난다. `SmsVerificationService.send` 가
번호 중복(`USER104`)을 본 **바로 다음 줄**에서 탈퇴 기록을 본다 — 문자 비용을 쓰기 전에 끊는 것이다.
`LocalUserService.register` 에는 이 검사가 없다(grep: `existsDeletedSince` 호출처는 그 한 곳뿐).

→ 그래서 가입 화면의 대응은 `PHONE_NUMBER_DUPLICATED` 와 **같은 자리, 다른 처리**다.
`PhoneVerifyState` 에 `rejoinRestricted` 플래그를 추가하고 `canRequestCode` 에서 버튼을 잠갔다.
`alreadyRegistered` 로 묶지 **않았다** — 그 플래그의 뜻은 "로그인하면 된다"이고,
탈퇴한 번호에는 로그인할 계정이 없다. 백엔드 통합테스트
`탈퇴하면_로그인_토큰_재가입_채팅방_즐겨찾기가_모두_정리된다` 가 `send(phone)` → `REJOIN_RESTRICTED` 를 직접 못 박는다.

### 왜 상태 코드 폴백으로는 안 되는가

- **409 가 이제 다섯이다** (`USER101`·`USER104`·`USER108`·`USER109`·`USER110`). 기존 409 폴백은
  `LOGIN_ID_DUPLICATED` → 코드를 놓치면 **탈퇴 화면에 「이미 사용 중인 아이디예요」가 뜬다.**
  아이디를 입력한 적도 없는 화면이다.
- **400 폴백은 `INVALID_REQUEST`** = "입력한 정보를 다시 확인해 주세요". 비밀번호 하나만 받는
  화면에서 그 문구는 무엇이 틀렸는지 말해 주지 못한다.
- `USER111` 을 401/`SESSION_EXPIRED` 로 뭉치면 비밀번호를 한 번 틀린 사용자가 로그아웃된다.

### 합친 것 / 안 합친 것

- **하나도 합치지 않았다.** 셋 다 화면 대응이 서로 다르다(버튼 잠금 / 다른 화면으로 유도 / 입력란 재입력).
- 추측으로 만든 매핑은 없다. 세 행 모두 `UserErrorCode` enum + `throw` 지점 대조 결과다.

---

## 5. 탈퇴 시 지우는 로컬 상태

`logout()` 이 지우는 범위를 읽고 **거기에 하나를 더했다.**

| # | 상태 | 저장 위치 | `logout()` | `deleteAccount()` |
|---|---|---|---|---|
| 1 | accessToken · refreshToken · userUuid | 메모리(`SessionManager.session`) + DataStore `moyeota_session` | 지움 | **지움** |
| 2 | `authState` | `MutableStateFlow` | → `Unauthenticated` | **→ `Unauthenticated`** |
| 3 | **"진행 중인 방" 기억** (`active_party_id`, `active_party_owner_uuid`) | DataStore `moyeota_active_party` | **남긴다** (의도) | **지운다** |
| 4 | 화면이 들고 있던 진행 중 방 | `ActivePartyViewModel` (메모리) | `forget()` | `forget()` (같은 전이) |
| 5 | FCM 기기 토큰 (서버 쪽) | 서버 `users.fcm_token` | `DELETE /users/me/fcm-token` 호출 | **호출하지 않음** — 서버 `User.delete()` 가 직접 비운다 |
| 6 | FCM 보류 토큰 (`FcmTokenRegistrar.pendingToken`) | 메모리 | 남긴다 | 남긴다 (기기 토큰은 계정과 무관) |

### #3 — 로그아웃과 다른 유일한 지점

`logout()` 은 그 기억을 일부러 남긴다(`ownerUuid` 로 계정 가드가 걸려 있어, 같은 계정으로 다시
로그인하면 매칭 화면으로 복귀해야 한다). **탈퇴한 계정은 다시 로그인할 길이 없다** — 남겨 두면
기기에 앞 사람의 방 id 가 영구히 남는다. 양쪽을 테스트로 고정했다
(`탈퇴는 진행 중인 방 기억까지 지운다` / `로그아웃은 진행 중인 방 기억을 남긴다`).

### #5 — FCM 해제를 일부러 안 부른다

`User.delete()` 가 `fcmToken = null` 로 비우고 `users.save(user)` 한다(소스 확인).
미리 불러 두면 **409·400 으로 탈퇴가 실패했을 때 멀쩡한 사용자의 알림만 끊긴 상태**가 남는다.
테스트: `탈퇴는 푸시 토큰 해제를 부르지 않는다`.

### 순서가 로그아웃과 **반대**다

| | 순서 | 이유 |
|---|---|---|
| `logout()` | 로컬 먼저 → 서버는 최선 노력 | 서버가 죽어도 사용자가 나갈 수 있어야 한다 |
| `deleteAccount()` | **서버 먼저(204 확인) → 로컬** | ① 세션을 미리 비우면 Bearer 가 사라져 `@CurrentUser` 가 401 이다 ② 실패 시 세션을 **그대로 남겨야** 한다 |

로컬 정리 안에서는 **기억 → 세션** 순이다. 세션 비우기가 `authState` 전이를 동기로 일으켜 화면이
곧바로 넘어가므로, 디스크 쓰기를 뒤에 두면 화면 전환에 묻힐 여지가 생긴다.
기억 삭제가 실패해도 탈퇴는 성공으로 끝낸다(테스트 있음) — 서버 계정은 이미 사라졌다.

`onLoggedOut()` 이 돌려주는 리프레시 토큰은 **쓰지 않는다**. 서버가 이미 전량 취소했으므로
`POST /auth/logout` 은 401 만 받는 헛된 왕복이다(테스트: `탈퇴는 로그아웃 API 를 덧붙여 부르지 않는다`).

---

## 6. 화면 흐름

```
35 마이페이지
  ├ 「지원」 카드
  ├ 로그아웃 + 버전              ← 그대로
  │  … 40dp + 하フ라인 …          ← 오탭 방지 (아래 참조)
  └ 「계정」 ▸ 탈퇴하기 (danger, 꺾쇠)
       └ 부연 한 줄: "계정과 저장한 정보가 사라지고 되돌릴 수 없어요"
            ↓ navigate
37 회원 탈퇴 (신규)
  상단바 「회원 탈퇴」 → "탈퇴하면 되돌릴 수 없어요"
  → 경고 카드 4줄 (danger 배경)
  → 비밀번호 입력 (보기/숨기기, 본인 확인)
  → DangerCtaButton 「탈퇴하기」  (비밀번호 비면 비활성)
       ↓ 확인 다이얼로그 「정말 탈퇴할까요?」
       ↓ onSessionEndExpected()  ← accountDeleted = true  (요청 **전**)
       ↓ DELETE /api/v1/users/me
   ┌───┴────────────────────────────────────────────┐
   │ 204                      │ 400 USER111          │ 409 USER110        │ IOException
   │ 세션·기억 비움            │ 입력란 오류 + 비움    │ 배너               │ 배너
   │ authState → Unauth       │ onSessionKept()      │ onSessionKept()    │ onSessionKept()
   │   ↓                      │ (37 에 머묾)          │ (37 에 머묾)        │ (37 에 머묾)
   │ MainNavGraph 로그인 전이  │
   │ → 04a, 스택 전체 비움     │
   │ → 안내: 「탈퇴가 처리됐어요. 그동안 이용해 주셔서 고마웠어요」
   └──────────────────────────┘
```

### 세션 만료 안내가 뜨지 않게 — 기존 장치와 같은 방식

기존 장치는 `MainNavGraph` 의 `logoutRequested` 다(로그아웃 직전에 세워 두고, 전이 때 소비).
탈퇴는 **안내 문구가 달라야** 해서(아무 안내도 없으면 처리 여부를 알 수 없다) 같은 모양의 플래그를
하나 더 뒀다:

```kotlin
var accountDeleted by remember { mutableStateOf(false) }
...
signOutNotice = when {
    accountDeleted -> "탈퇴가 처리됐어요. 그동안 이용해 주셔서 고마웠어요"
    logoutRequested -> null
    else -> "세션이 만료됐어요. 다시 로그인해 주세요"
}
accountDeleted = false
logoutRequested = false
```

**요청 직전에 세우는 것이 핵심이다.** `authState` 전이는 `deleteAccount()` 안에서 일어나고,
그 전이를 받는 `LaunchedEffect(loggedIn)` 은 **`NavHost` 보다 앞에 선언돼 있어 같은 컴포지션에서
먼저 돈다**. 응답을 받은 뒤에 세우면 전이가 이미 「세션이 만료됐어요」로 처리된 뒤다.
실패하면 `onSessionKept` 가 되돌린다 — 안 되돌리면 나중의 **진짜** 세션 만료가 조용해진다.

### 35 배치 — 로그아웃과 섞지 않은 방법

로그아웃은 13sp 회색 **글자 링크**다. 탈퇴도 글자 링크로 두면 두 줄이 붙어 오탭이 난다. 그래서
(a) 40dp 간격 (b) 하프라인 (c) 「계정」 머리말 (d) 흰 카드 + `Danger500` + 꺾쇠 + 56dp 터치 영역.
꺾쇠(›)인 이유는 **앱 안**의 37 로 가기 때문이다 — 이 화면의 기호 규칙은 ↗ = 앱 밖, › = 앱 안이다.
오탭해도 되돌릴 수 있다: 37 은 비밀번호를 받고, 거기서 또 확인 다이얼로그를 띄운다.

### 경고 문구 — 서버 동작과 어긋나지 않게

`AccountDeletionWarnings` 4줄과 그 근거:

| 문구 | 서버 동작 |
|---|---|
| 지금 아이디와 비밀번호로는 다시 로그인할 수 없어요 | `localUsers.deleteByUserId` |
| 참여 중인 채팅방에서 모두 나가고, 함께 탄 사람들에게는 「탈퇴한 사용자」로 보여요 | `ChatRoomUserDeletionListener` + `User.DELETED_DISPLAY_NAME` |
| 저장해 둔 자주 가는 곳이 지워져요 | `FavoritePlaceUserDeletionListener` |
| 같은 휴대폰 번호로는 30일 동안 다시 가입할 수 없어요 | `userDeletions.record` + `UserDeletions.RETENTION` |

**적지 않은 것이 더 중요하다.**
- **지난 합승 기록은 서버가 지우지 않는다** — 끝난 방의 멤버로는 남고 이름만 가려진다.
  "모든 기록이 사라져요" 류를 쓰지 않았고, `AccountDeleteRulesTest` 가 그 문구가 섞이는 것을 막는다.
- **진행 중 합승 제약은 경고에 넣지 않았다.** 앱이 미리 알 수 없는 값이다("지금 내가 타고 있는 방"을
  묻는 엔드포인트가 없다) — 경고가 아니라 **실패 사유**로 다룬다. 테스트로 고정.
- 30일을 "정확히 30일 뒤"라고 약속하지 않았다. 서버 쓸어내기가 하루 한 번이라 최대 하루 더 막힐 수 있다
  (`UserDeletionSweeper` KDoc 이 명시).

### 왜 다이얼로그가 아니라 전용 화면(37)인가

서버가 비밀번호를 **본문으로** 요구한다 → (a) 입력란, (b) 실패 표시 2종(입력란/배너),
(c) 경고 4줄 읽을 자리가 필요하다. 다이얼로그에 다 넣으면 **키보드가 올라온 순간 경고가 화면 밖으로
밀려 읽히지 않는 경고**가 된다.

---

## 7. 테스트 결과

```
./gradlew :app:assembleDebug :data:testDebugUnitTest :presentation:testDebugUnitTest --console=plain -q
→ BUILD SUCCESSFUL   (경고 0 — 내 파일 기준)

data         278 tests, 0 failures, 0 errors   (내 추가 +15)
presentation 214 tests, 0 failures, 0 errors   (내 추가 +15)
```

| 테스트 클래스 | 건수 | 내 추가 |
|---|---|---|
| `data.remote.UserApiDeleteBodyTest` (**신규**) | 4 | +4 |
| `data.remote.AuthMappersTest` | 32 | +2 |
| `data.remote.AuthenticatedPathContractTest` | 18 | +1 |
| `data.repository.RemoteAuthRepositoryTest` | 41 | +8 |
| `presentation.feature.mypage.AccountDeleteRulesTest` (**신규**) | 13 | +13 |
| `presentation.feature.auth.PhoneVerifyStateTest` | 41 | +2 |

### 다른 에이전트 때문에 깨진 것 — 없다 (원인 구분 기록)

작업 도중 `data:compileDebugUnitTestKotlin` 이 한 번 실패했는데 **내 쪽 원인**이었다:
동시에 QA D-3 를 고치던 에이전트의 `RemoteActivePartyRepositoryTest.kt` 에 이미
`private class FakeActivePartyStorage` 가 있어 같은 패키지 top-level 이름이 충돌했다
(Kotlin 은 private 끼리도 redeclaration 으로 잡는다). **남의 파일은 고치지 않고 내 쪽을
`SpyActivePartyStorage` 로 바꿨다.** 최종 상태는 양쪽 다 초록이다.

그 에이전트의 in-flight 파일(`RemoteActivePartyRepository.kt`·`RemoteActivePartyRepositoryTest.kt`·
`ActiveParty.kt`·`feature/explore/*`)은 **열지도 고치지도 않았다.**

### 하다가 걸린 함정 하나 더 (기록)

`UserApi` KDoc 에 `` `/api/v1/users/**` `` 를 적었더니 **Kotlin 블록 주석이 중첩되기 때문에**
`/**` 가 주석을 한 겹 더 열어 파일이 통째로 깨졌다(`Unclosed comment` at EOF).
`AuthApi` KDoc 이 같은 자리에서 「하위 전체」라고 풀어 쓰는 이유가 이것이었다 — 같은 방식으로 고쳤다.

---

## 8. 변경 파일

### domain (3)
- `model/AuthError.kt` — `REJOIN_RESTRICTED`·`ONGOING_PARTY_EXISTS`·`PASSWORD_MISMATCH` 추가, `SESSION_EXPIRED` KDoc 보강
- `model/Auth.kt` — `AuthPolicy.REJOIN_RESTRICTED_DAYS = 30`
- `repository/AuthRepository.kt` — `deleteAccount(password)` + 서버가 하는 일/하지 않는 일 KDoc

### data (4 main + 5 test)
- `remote/UserApi.kt` — `deleteAccount` (`@HTTP`), 함정 KDoc
- `remote/dto/UserDtos.kt` — `AccountDeletionRequest`
- `remote/AuthMappers.kt` — `USER109`·`USER110`·`USER111` 분기 + 코드표·409 주석 갱신
- `repository/RemoteAuthRepository.kt` — `deleteAccount` 구현, `activePartyStorage` 생성자 추가
- test: `UserApiDeleteBodyTest`(신규) · `AuthMappersTest` · `AuthenticatedPathContractTest`
  (`@HTTP` 인식 + 탈퇴 계약) · `RemoteAuthRepositoryTest` · `FcmTokenRegistrarTest`(페이크 메서드 추가)

### presentation (6 main + 2 test)
- `feature/mypage/AccountDeleteRoute.kt` (**신규**) — 순수 로직 + ViewModel + Route
- `feature/mypage/AccountDeleteScreen.kt` (**신규**) — 37 화면 + 프리뷰 3
- `feature/mypage/MyPageScreen.kt` — 「계정」 영역 + `onDeleteAccount`
- `core/Routes.kt` — `ACCOUNT_DELETE`
- `core/MainNavGraph.kt` — 37 배선, `accountDeleted` 플래그, 3분기 안내 (**이 파일은 내가 소유**)
- `feature/auth/SignupDraft.kt` — `authErrorMessage` 에 신규 3분기 (비포괄 `when` 이라 컴파일이 강제)
- `feature/auth/PhoneVerification.kt` — `rejoinRestricted` 플래그 + `onRequestFailure` 분기 + `canRequestCode` 잠금
- test: `AccountDeleteRulesTest`(신규) · `PhoneVerifyStateTest`

### core:designsystem (1) · app (1) · docs (1)
- `component/Buttons.kt` — `DangerCtaButton`
- `app/AppContainer.kt` — `activePartyStorage` 를 하나만 만들어 둘에 주입
- `docs/BACKEND-GAP-CLEANUP.md` — §1·§4 에 취소선, **§6 「되살린 것」 신설** (탈퇴 = 연동됨)

---

## 9. QA 확인 경로 (qa-verifier 인계)

**전제: 백엔드 `develop`(`88d586d` 이상)으로 띄운 서버**가 필요하다. 운영 `api.moyeota.p-e.kr` 는
main(옛 계약)이라 `DELETE /api/v1/users/me` 가 **401 또는 404** 로 떨어진다.

| # | 경로 | 기대 |
|---|---|---|
| 1 | 35 마이페이지 하단 | 「계정」 머리말 + 빨간 「탈퇴하기」 + 꺾쇠. **로그아웃과 40dp 이상 떨어져 있다** |
| 2 | 「탈퇴하기」 탭 | 37 열림. 경고 4줄이 **키보드 전에** 보인다 |
| 3 | 비밀번호 비운 채 | 「탈퇴하기」 버튼 **비활성** |
| 4 | **비밀번호 틀림** | 400 `USER111` → **입력란 아래** 「비밀번호가 일치하지 않아요」, 입력값 비워짐, 37 에 머묾, **로그아웃되지 않음** |
| 5 | **진행 중 합승 있는 계정** (21/25/26 단계에서 탈퇴 시도) | 409 `USER110` → **버튼 위 배너** 「진행 중인 합승이 있어요. 합승에서 먼저 나간 뒤에 탈퇴해 주세요」. 37 에 머묾 |
| 6 | 합승에서 나간 뒤 재시도 | 204 → 04a 로그인. 안내는 **「탈퇴가 처리됐어요…」** (파란 INFO 배너). **「세션이 만료됐어요」가 뜨면 결함** |
| 7 | 6 직후 같은 아이디로 로그인 | 401 `USER102` (계정 삭제됨) |
| 8 | 6 직후 **같은 번호**로 가입 시도 → 「인증번호 받기」 | 409 `USER109` → 번호 입력란 문구 「탈퇴한 번호는 30일 동안…」 + **버튼 잠김**. 번호를 다른 값으로 고치면 버튼이 풀린다 |
| 9 | 탈퇴자와 같은 방이었던 **다른 계정**으로 채팅/합승 보기 | 그 사람이 「탈퇴한 사용자」로 보인다 (서버 `displayNickname()`) |
| 10 | 탈퇴 후 앱 **강제 종료 → 재실행** | 로그인 화면. 홈 「진행 중인 방」 배너가 **되살아나지 않는다**(로컬 기억 삭제 확인) |
| 11 | 비행기 모드로 탈퇴 시도 | 배너 「네트워크 연결을 확인해 주세요」, **세션 유지**. 비행기 모드 해제 후 재시도 성공 |
| 12 | logcat BODY 로그 (디버그 빌드) | `--> DELETE http://…/api/v1/users/me` + 본문 `{"password":"…"}`. **URL 에 비밀번호가 없어야 한다** |

### 특히 봐야 할 것
- **#6 의 안내 문구** — 이 작업에서 가장 깨지기 쉬운 지점이다(`accountDeleted` 를 요청 **전**에 세우는 것이 전제).
- **#4·#5 가 로그아웃을 일으키지 않는가** — 실패가 세션을 건드리면 구현 순서가 뒤집힌 것이다.
- **#8** — 서버가 `send` 단계에서 거절하므로, 인증번호 입력칸이 열리면 안 된다.

### 남은 Play 요건 (이번 범위 밖)
앱 안 탈퇴는 끝났지만 스토어 등록용 **웹 탈퇴 안내 페이지**가 여전히 없다.
`SupportLinks.PRIVACY_URL`·`TERMS_URL`·`CONTACT_EMAIL` 이 전부 null 인 것과 같은 공백이다.

---

## 10. 백엔드 계약 중 눈에 걸린 점 (결함 아님 / 요청 후보)

1. **비밀번호 없는 계정(소셜)에 `PASSWORD_MISMATCH` 를 쓴다.** `localUsers.findByUserId` 가 비면
   그 코드를 던진다(서버 주석도 "소셜은 아직 탈퇴 경로가 없다"고 적었다). 지금은 로컬 가입만 있어
   영향이 없지만 소셜 로그인이 붙으면 **비밀번호를 가진 적도 없는 사용자에게 "비밀번호가 다릅니다"**
   가 뜬다. 그때는 별 코드가 필요하다.
2. **진행 중 합승 여부를 미리 물을 길이 없다.** 앱은 눌러 본 뒤 409 로만 안다 — `GET /users/me/parties`
   같은 조회가 있으면 37 에서 미리 안내하고 버튼을 잠글 수 있다(기존 `ActivePartyRepository` 가
   로컬 기억으로 흉내 내는 공백과 같은 뿌리다).
3. **탈퇴가 멱등이 아니다.** `User.delete()` 가 이미 탈퇴한 사용자에 `USER_NOT_FOUND`(404)를 던진다.
   토큰이 즉시 죽으므로 실제로 두 번 부를 경로는 없지만, 네트워크 재시도가 겹치면 404 가 보일 수 있다.
   앱에서는 404 → `UNKNOWN`("잠시 후 다시 시도해 주세요")이 되는데, 이미 탈퇴가 된 상태다.
4. 참고(정상): 탈퇴 기록은 **전화번호 해시**만 남긴다(`userDeletions.record(profile.getPhoneNumber(), now)`
   → `fieldHasher`). 평문 번호를 보관하지 않으므로 30일 차단과 개인정보 최소수집이 양립한다.

---

## 11. 덧붙인 배선 1건 (리더 요청, 탈퇴와 무관)

다른 에이전트가 `ActiveParty.kt` 에 만들어 둔 가시성 입구를 `MainNavGraph`(내 소유 파일)에서 연결했다 —
`activePartyViewModel.pollChatRoom()`(10초 주기 `GET /chat-rooms/me`)이 **앱이 백그라운드여도** 돌던
누수를 막는다. ViewModel·NavHost 가 액티비티 스코프라 컴포지션 스코프만으로는 멈추지 않는다.

```kotlin
// 기존 LaunchedEffect(activePartyViewModel) { pollChatRoom() } 바로 아래
LifecycleStartEffect(activePartyViewModel) {
    activePartyViewModel.onHostStart()
    onStopOrDispose { activePartyViewModel.onHostStop() }
}
```

`androidx.lifecycle.compose.LifecycleStartEffect` import 추가. **STARTED 경계**다(17 탐색·24 채팅·GPS 구독과
동일 — RESUMED 는 NavHost 전환 애니메이션이 끝나야 와서 복귀 첫 조회가 늦는다).
기존 `pollChatRoom()` 호출은 그대로 뒀다 — 루프 내부가 `hostVisible` 을 보고 알아서 멈추고 재개한다
(`shouldPollChatRoom`). `ActiveParty.kt` 는 읽기만 했고 수정하지 않았다.

QA 확인: 21/25 단계(진행 중 방 있고 채팅방 아직 없음)에서 **홈 버튼으로 백그라운드 → 2분 방치** 시
logcat 에 `GET /chat-rooms/me` 가 더 나오지 않아야 한다. 앱 복귀 후 10초 뒤 다시 1건.
