# 115 — API 연동: 문자 인증 가입 (Track B, data/·domain/)

작업: `_workspace/00_input_backend-sync-sse.md` §4 (Backend #212)
백엔드 기준: 읽기 전용 체크아웃 `origin/develop 53b8cf1` — **컨트롤러·서비스·enum 소스 직접 확인**.
리더가 띄운 로컬 서버에서 `send` → 204, `verify` → `{"verificationToken":"..."}` 를 실측했다고 통보받음(교차 확인됨).
앱 테스트는 네트워크 없이 순수 파싱/매핑으로만 검증했다.

범위: `data/`·`domain/` 만. `presentation/` 은 **컴파일 복구용 최소 수정 2건**뿐(§7).

---

## 1. 연동한 엔드포인트

| 메서드 | 경로 | 인증 | 요청 본문 | 응답 |
|---|---|---|---|---|
| POST | `/api/v1/auth/sms/send` | 불필요 | `{"phoneNumber":"01012345678"}` | **204 No Content** (본문 없음) |
| POST | `/api/v1/auth/sms/verify` | 불필요 | `{"phoneNumber":"01012345678","code":"123456"}` | 200 `{"verificationToken":"<uuid>"}` |
| POST | `/api/v1/auth/register` | 불필요 | 기존 8필드 + **`verificationToken`** | 201 `{"uuid":"...","name":"..."}` (변경 없음) |

### 인증 클라이언트 — 기존 경로를 그대로 썼다

`AuthApi` 는 `NetworkModule` 의 `publicRetrofit`(= `baseClient`, Bearer 인터셉터도 401 재발급
Authenticator 도 **붙지 않음**)으로 생성된다. `register`/`login`/`nickname/check` 가 이미 그 경로이고
문자 인증도 같은 컨트롤러 프리픽스(`/api/v1/auth`)라서 **같은 `AuthApi` 에 추가**했다.
별도 인스턴스를 만들 이유가 없다(`ConfigApi` 도 같은 `publicRetrofit` 을 공유한다).

근거: `SecurityConfig.securityFilterChain` 의 permitAll 목록은
`/api/v1/auth/**`, `/api/v1/config`, `/ws-chat/**`, `/health`, `/prometheus`, `/info` 뿐이다.
**`SmsVerificationController` 는 별 파일이지만 `@RequestMapping("/api/v1/auth/sms")` 라서 이 구간에 들어온다.**

> 부수 정정: `AuthApi` KDoc 이 permitAll 목록에 `/api/v1/local/users` 를 적어 두고 있었다.
> 그 가입 엔드포인트는 삭제됐고 permitAll 목록에도 없다 — 주석을 현행 `SecurityConfig` 기준으로 고쳤다.

---

## 2. 화면이 쓸 최종 시그니처 (전문)

### `domain/repository/AuthRepository.kt` — 신규 2개

```kotlin
/** POST /api/v1/auth/sms/send (204, 토큰 불필요). 번호는 하이픈 유무 무관 — 내부에서 정규화한다. */
suspend fun sendPhoneVerificationCode(phoneNumber: String)

/** POST /api/v1/auth/sms/verify (200, 토큰 불필요). @return 가입에 실을 verificationToken */
suspend fun verifyPhoneCode(phoneNumber: String, code: String): String
```

`register(newUser: NewUser): String` — **시그니처는 그대로**, `NewUser` 에 필드가 하나 늘었다.

### `domain/model/Auth.kt` — `NewUser` 에 필수 필드 1개 추가 (맨 뒤)

```kotlin
data class NewUser(
    val loginId: String,
    val password: String,
    val nickname: String,
    val name: String,
    val birthDate: LocalDate,
    val phoneNumber: String,
    val gender: Gender,
    val email: String,
    val verificationToken: String,   // ← 신규. verifyPhoneCode() 가 돌려준 값을 그대로
)
```

기본값을 주지 않은 건 의도다 — 빈 값으로 보내면 가입이 **전부** 400 이므로 컴파일러가 채우도록 강제한다.

### `domain/model/AuthPolicy` — 신규 상수·함수 (화면 타이머/검증용)

```kotlin
const val VERIFICATION_CODE_LENGTH = 6
const val SMS_RESEND_COOLDOWN_SECONDS = 60     // 재전송 카운트다운
const val SMS_CODE_TTL_SECONDS = 180           // 인증번호 유효시간 카운트다운
const val VERIFICATION_TOKEN_TTL_SECONDS = 600 // 인증 완료 후 가입 제출 제한시간
const val SMS_DAILY_LIMIT = 5
const val SMS_MAX_VERIFY_ATTEMPTS = 5

fun normalizePhoneNumber(value: String): String   // 하이픈·공백 제거 → 전송용 숫자열
fun isValidVerificationCode(value: String): Boolean
fun isValidBirthDate(value: LocalDate): Boolean   // 서버 @Past 대응 (UTC 기준 오늘 이전만)
```

### `domain/model/AuthError` — 신규 8종 (§5 표)

`INVALID_PHONE_NUMBER` · `SMS_COOLDOWN` · `SMS_DAILY_LIMIT_EXCEEDED` · `SMS_CODE_EXPIRED`
· `SMS_CODE_MISMATCH` · `SMS_TOO_MANY_ATTEMPTS` · `PHONE_NOT_VERIFIED` · `SMS_UNAVAILABLE`

> **가입 화면 에이전트 주의:** `AuthError` 는 enum 이고 `SignupDraft.authUserMessage()` 가
> 비포괄 `when` 으로 받는다. 8개 분기를 **초안 문구로 채워 컴파일은 살려 뒀다**(§7) — 문구와
> 상태 전이는 가입 플로우를 쥔 쪽이 확정할 것.

### 화면 흐름 (서버 제약에서 바로 따라오는 것)

```
전화번호 입력
  → sendPhoneVerificationCode(번호)
      409 PHONE_NUMBER_DUPLICATED → "이미 가입된 번호, 로그인하세요" (여기서 끝. §4 중요)
      429 SMS_COOLDOWN / SMS_DAILY_LIMIT_EXCEEDED → 재전송 버튼 제어
  → (성공) 인증번호 6자리 입력, 3분 타이머 / 재전송 60초 쿨다운
  → verifyPhoneCode(번호, 코드) → verificationToken
      SMS_CODE_MISMATCH   → 입력칸만 비우고 재입력 (남은 시도 있음)
      SMS_TOO_MANY_ATTEMPTS / SMS_CODE_EXPIRED → 재발송으로 유도 (코드가 서버에서 지워졌다)
  → (10분 안에) 나머지 가입 입력 → register(NewUser(..., verificationToken))
      PHONE_NOT_VERIFIED → 문자 인증 단계로 되돌리기
      409 3종(아이디/번호/닉네임 중복) → 입력만 고쳐 **같은 토큰으로 재시도** (§4)
```

---

## 3. 서버 제한값 (1차 출처: `SmsVerificationService.java:26-31`)

| 항목 | 값 | 상수 | 넘기면 |
|---|---|---|---|
| 인증번호 유효시간 | **3분** | `CODE_TTL` (L26) | 400 `USER204` |
| 재발송 쿨다운 | **60초** | `COOLDOWN` (L27) | 429 `USER202` |
| 일일 발송 한도 | **5회 / 24시간** | `DAILY_LIMIT` (L29), `DAILY_WINDOW` (L28) | 429 `USER203` |
| 코드당 입력 시도 | **5회** | `MAX_ATTEMPTS` (L30) | 429 `USER206` (+ 서버가 코드 삭제) |
| **verificationToken TTL** | **10분** | `VERIFIED_TTL` (L31) | 가입이 400 `USER207` |

세부 동작(코드 근거):
- 일일 카운터는 **첫 발송 시점부터 24시간 고정 창**이다 — `increase()` 가 `setIfAbsent(…, ttl)` 후 `INCR`
  (INCR 은 TTL 을 유지한다). 거절된 요청도 카운터를 올리지만 `> DAILY_LIMIT` 판정이라 추가 잠금은 없다.
- `saveCode` 가 **오입력 횟수를 0으로 되돌린다**(`SmsVerificationRedis.saveCode` 가 `ATTEMPT` 키를 먼저 삭제)
  → 재발송하면 5회가 리셋된다.
- 5회째 틀리면 서버가 코드를 지운다 → 이후 같은 번호 입력은 `USER206` 이 아니라 **`USER204`** 로 바뀐다.
- 로컬/dev 고정 코드: `application.yaml` `provider: log`(기본, 문자 미발송 — 서버 로그에 코드 출력),
  `application-dev.yaml` `fixed-code: ${SMS_FIXED_CODE:000000}`. 리더의 로컬 서버는 `123456`.

---

## 4. **토큰 수명·재사용 — 확인 결과 (반드시 읽을 항목)**

### 결론: 가입이 **실패하면 토큰이 남는다. 성공하면 소진된다.**

`LocalUserService.register` 의 순서가 근거다:

| 줄 | 내용 |
|---|---|
| L39 | `smsVerificationService.checkVerified(token, phoneNumber)` — **중복 검사보다 먼저** 토큰 확인 |
| L42 / L46 / L50 | 아이디 / 전화번호 / 닉네임 중복 → 예외 (`@Transactional` 롤백) |
| L59 | `userProfiles.save(...)` — 3개 테이블 저장 완료 |
| **L62** | `smsVerificationService.consume(token)` — **여기서야 토큰 삭제** |

→ 아이디·닉네임 중복(409) 등으로 가입이 실패한 사용자는 **문자 인증을 다시 하지 않고,
같은 `verificationToken` 으로 입력만 고쳐 재시도할 수 있다.** 단 10분 TTL 안에서만.
백엔드 주석도 명시적이다: *"저장까지 끝난 뒤에 지움 - 닉네임 중복 등으로 실패하면 인증을 다시 하지 않고 재시도 가능"*.
백엔드 단위 테스트 `소비한_토큰은_다시_쓸_수_없다` 가 성공 후 1회성임을 못 박고 있다.

- `checkVerified` 는 토큰을 **지우지 않는다**(조회+비교만) → 10분 안에는 몇 번이고 재시도 가능.
- `consume` 실패는 서버가 삼키고 TTL 에 맡긴다(로그 경고만).
- **번호를 바꾸면 무효다.** `checkVerified` 가 `sms:verified:<token>` 에 저장된 phoneHash 와
  가입 번호의 해시를 비교한다 → 인증 후 가입 폼에서 번호를 고치면 400 `USER207`.
- `verify` 는 성공 시 코드를 삭제하므로 **같은 코드로 토큰을 두 번 받을 수 없다** → 화면은 받은 토큰을
  가입 제출까지 들고 있어야 한다(재확인으로 다시 얻는 길이 없다).

### 화면 설계 결론
- 가입 실패 → **문자 인증 단계로 되돌리지 말 것.** 입력 단계에 머물며 토큰을 유지한다.
- 되돌려야 하는 유일한 신호는 `AuthError.PHONE_NOT_VERIFIED`.
- 인증 완료 후 가입 제출까지 **10분 예산**이다. 가입 폼이 길면 남은 시간 표시 또는
  `PHONE_NOT_VERIFIED` 수신 시 "인증이 만료됐어요" 안내가 필요하다.

---

## 5. 에러 코드 매핑표

출처: `UserErrorCode.java:16-29` (코드·메시지·HTTP 상태 전부 enum 에서 직접 읽음).

| 서버 코드 | HTTP | 발생 지점 | → `AuthError` | 화면 대응 |
|---|---|---|---|---|
| `USER104` PHONE_NUMBER_DUPLICATED | 409 | **send** (+ register) | `PHONE_NUMBER_DUPLICATED` *(기존)* | 로그인으로 유도 |
| `USER105` INVALID_PHONE_NUMBER | 400 | **verify** (VO) | `INVALID_PHONE_NUMBER` *(신규)* | 번호 형식 안내 |
| `USER201` SMS_SEND_FAILED | 502 | send | `SMS_UNAVAILABLE` *(신규, 합침)* | 잠시 후 재시도 (쿨다운 해제됨 → 즉시 가능) |
| `USER202` SMS_COOLDOWN | 429 | send | `SMS_COOLDOWN` *(신규)* | 60초 카운트다운 |
| `USER203` SMS_DAILY_LIMIT_EXCEEDED | 429 | send | `SMS_DAILY_LIMIT_EXCEEDED` *(신규)* | **재시도 권하지 말 것** |
| `USER204` SMS_CODE_EXPIRED | 400 | verify | `SMS_CODE_EXPIRED` *(신규)* | 재발송 유도 |
| `USER205` SMS_CODE_MISMATCH | 400 | verify | `SMS_CODE_MISMATCH` *(신규)* | 입력칸만 비움 |
| `USER206` SMS_TOO_MANY_ATTEMPTS | 429 | verify | `SMS_TOO_MANY_ATTEMPTS` *(신규)* | 재발송 유도 (코드 삭제됨) |
| `USER207` SMS_NOT_VERIFIED | 400 | **register** | `PHONE_NOT_VERIFIED` *(신규)* | 인증 단계로 되돌리기 |
| `USER208` SMS_UNAVAILABLE | 503 | send·verify·register | `SMS_UNAVAILABLE` *(신규, 합침)* | 잠시 후 재시도 |
| `INVALID_REQUEST` | 400 | **send** (`@Pattern` 번호 형식), register (토큰 누락) | `INVALID_REQUEST` *(기존)* | `serverMessage` 가 `"필드: 사유"` |

### 왜 상태 코드 폴백으로는 안 되는가
- **429 가 셋** (`USER202`·`USER203`·`USER206`) — 게다가 기존 폴백 표에 429 가 아예 없어서
  코드를 놓치면 전부 `UNKNOWN`("잠시 후 다시 시도해 주세요")이 된다. 일일 한도에 그 문구를 띄우면
  사용자가 1분마다 재전송을 누르며 하루를 보낸다.
- **400 이 셋** (`USER204`·`USER205`·`USER207`) — 폴백은 셋 다 `INVALID_REQUEST`("입력한 정보를
  다시 확인해 주세요")로 뭉친다. `USER207` 은 입력 문제가 아니라 단계 되돌리기가 필요하다.

### 합친 것 / 안 합친 것
- `USER201`(502) + `USER208`(503) → `SMS_UNAVAILABLE` **하나로 합쳤다.** 앱 대응이 동일("잠시 후 재시도")이고,
  `SESSION_EXPIRED` 가 토큰 코드 6개를 합친 기존 선례와 같은 기준이다. 원문은 `serverMessage` 에 남는다.
- 나머지는 **전부 분리.** 화면 대응이 서로 다르다.
- **추측으로 만든 매핑은 없다.** 표의 모든 행이 `UserErrorCode` enum + 서비스 코드의 `throw` 지점 대조 결과다.

### 코드에서 못 찾은 것 (매핑하지 않음)
- 남은 발송 횟수 / 남은 쿨다운 초 / 남은 입력 시도 횟수를 **알려 주는 응답 필드나 엔드포인트가 없다.**
  429 는 "넘었다"만 말한다. → 쿨다운 타이머는 앱이 로컬에서 세야 하고, **앱을 재시작하면 남은 쿨다운을
  알 수 없다**(한 번 429 를 맞아 보는 것 외에 방법이 없다). 백엔드 요청 후보.
- `Retry-After` 헤더도 쓰지 않는다(`GlobalExceptionHandler` 가 body 만 만든다).

---

## 6. 전화번호 형식 결정 — **하이픈 없는 숫자열로 통일**

`AuthPolicy.normalizePhoneNumber()` 가 하이픈과 공백을 제거하고, **send·verify·register 세 곳 모두**
이 결과를 보낸다 (`RemoteAuthRepository.sendPhoneVerificationCode`/`verifyPhoneCode`, `NewUser.toDto()`).

서버가 세 곳에서 같은 값으로 매칭하는지 — **코드로 확인했다. 한다.**

| 지점 | 서버가 하는 일 |
|---|---|
| `send` | `new PhoneNumber(raw).value()` → 하이픈 제거 후 `fieldHasher.hash(phone)` 로 Redis 키 |
| `verify` | 똑같이 `fieldHasher.hash(new PhoneNumber(raw).value())` |
| `register` → `checkVerified` | 똑같이 정규화 후 해시해서 `sms:verified:<token>` 값과 비교 |
| 저장 | `UserProfile.of(...)` 가 같은 `PhoneNumber` VO 를 통과시킨다 → **DB 에도 하이픈 없이 저장** |

`PhoneNumber` 레코드(`user/common/domain/PhoneNumber.java`): `value.replace("-","").strip()` 후
`^01[016-9]\d{7,8}$` 검사. 즉 **서버 기준으로는 하이픈 유무가 결과를 바꾸지 않는다.**
그래도 앱에서 통일한 이유는 (a) 저장 형태와 일치, (b) "인증은 통과했는데 가입이 400" 을 디버깅할 때
번호 표기를 의심할 필요를 없애기 위함이다. 화면 표시용 하이픈 포맷은 presentation 몫으로 남겼다.

**앱이 중간 공백까지 지우는 건 서버보다 관대한 쪽이다** — 서버 VO 는 앞뒤 공백만 strip 해서
`"010 1234 5678"` 을 400 으로 떨군다. 앱이 먼저 지우므로 그 400 이 안 난다.

---

## 7. 기존 가입 계약 재확인 (§3) — 대조 결과

`UserRegisterRequest.java` vs `domain/model/AuthPolicy`:

| 제약 | 서버 | 앱 | 판정 |
|---|---|---|---|
| loginId | `^[a-z][a-z0-9_]{3,19}$` | `LOGIN_ID_PATTERN` 동일 | ✅ 그대로 |
| password | `@Size(8,64)` + 영문·숫자·특수문자 각 1 | `PASSWORD_PATTERN` + MIN/MAX 동일 (특수문자 집합까지 문자 단위 일치) | ✅ 그대로 |
| nickname | `@Size(2,10)` + VO `Nickname` `^[가-힣a-zA-Z0-9]{2,10}$` (strip 후) | `NICKNAME_PATTERN` 동일, `isValidNickname` 이 trim 후 검사 | ✅ 그대로 |
| name | `@Size(max=20)` | `NAME_MAX_LENGTH = 20` | ✅ 그대로 |
| phoneNumber | `^01[016-9]-?\d{3,4}-?\d{4}$` | `PHONE_PATTERN` 동일 | ✅ 그대로 |
| email | `@Email` + `@Size(max=100)` | `EMAIL_MAX_LENGTH = 100`, 형식은 presentation `SimpleEmailRegex`(`^[^@\s]+@[^@\s]+\.[^@\s]+$`) | ✅ 그대로 — 서버 `@Email` 보다 **앱이 조금 엄격**하지만 정상 주소를 막는 수준은 아니라 손대지 않음 |
| birthDate | `@NotNull @Past` **Instant** | 검증 없음 (LocalDate 만) | ⚠️ **보강** → `isValidBirthDate` 추가 |
| verificationToken | `@NotBlank` | 없었음 | ⚠️ **추가** (§2) |

`birthDate` 보강 근거: 서버는 Instant 를 `Instant.now()` 와 비교하고, 앱은 날짜를 **UTC 자정**으로
올려 보낸다(`toBirthDateInstant()`). 그래서 "오늘"을 고르면 **09:00 KST 이전에는 UTC 자정이 아직
미래라 400** 이 난다. `isValidBirthDate` 는 UTC 기준 오늘보다 이른 날짜만 통과시켜 그 틈을 막는다.
(presentation `parseBirthDate` 는 `!isAfter(now())` 라서 오늘을 허용한다 — 화면 쪽에서 이 함수로
좁히는 건 가입 화면 에이전트에게 넘긴다.)

### presentation 최소 수정 2건 (`SignupDraft.kt`) — 로직 설계 아님, 컴파일 복구

1. `SignupDraft` 에 `verificationToken: String = ""` 필드 + `toNewUser()` 가 그 값을 싣는다.
   빈 값이면 `null` 을 돌려준다(닉네임과 **똑같은 기존 패턴** — "필수 값이 비면 제출 전에 걸러낸다").
   `TODO(가입 화면 에이전트)` 로 채울 자리를 표시했다.
2. `authUserMessage()` 의 비포괄 `when` 에 신규 8분기 **초안 문구** 추가 + `TODO(가입 화면 에이전트)`.
   특히 재전송 남은 초·재발송 유도·인증 단계 되돌리기는 문구만으로 안 되고 상태 전이가 필요하다고 명기.

이 두 개 말고 presentation 은 건드리지 않았다. `MainNavGraph`·`ProfileSetup*`·`SignupSubmitRoute` 는 무변경.

---

## 8. 변경 파일

### domain (4)
- `domain/src/main/kotlin/com/moyeota/domain/model/Auth.kt` — `NewUser.verificationToken`,
  `AuthPolicy` 상수 6개 + `normalizePhoneNumber`·`isValidVerificationCode`·`isValidBirthDate`
- `domain/src/main/kotlin/com/moyeota/domain/model/AuthError.kt` — 신규 8종, `PHONE_NUMBER_DUPLICATED` KDoc 에 "발송 단계에서 난다" 명기
- `domain/src/main/kotlin/com/moyeota/domain/repository/AuthRepository.kt` — `sendPhoneVerificationCode`·`verifyPhoneCode`, `register` KDoc 갱신

### data (4)
- `data/src/main/kotlin/com/moyeota/data/remote/AuthApi.kt` — `sendSmsCode`(204)·`verifySmsCode`, permitAll 주석 정정
- `data/src/main/kotlin/com/moyeota/data/remote/dto/AuthDtos.kt` — `SmsSendRequestDto`·`SmsVerifyRequestDto`·`SmsVerifyResponse`, `RegisterRequestDto.verificationToken`
- `data/src/main/kotlin/com/moyeota/data/remote/AuthMappers.kt` — `toDto()` 에 토큰·번호 정규화, 에러 코드 9개 분기
- `data/src/main/kotlin/com/moyeota/data/repository/RemoteAuthRepository.kt` — 신규 2 메서드 구현

### test (3)
- `data/src/test/kotlin/com/moyeota/data/remote/AuthMappersTest.kt` — +13
- `data/src/test/kotlin/com/moyeota/data/repository/RemoteAuthRepositoryTest.kt` — +8
- `data/src/test/kotlin/com/moyeota/data/remote/AuthenticatedPathContractTest.kt` — +1 (경로·POST 고정)

### presentation (1, 컴파일 복구만)
- `presentation/src/main/kotlin/com/moyeota/presentation/feature/auth/SignupDraft.kt` — §7

`NetworkModule`·`MatchingApi`·`PartyDtos`·`RideRepository`·`PartyEventSource` 는 **열지 않았다**(Track A 소유).

---

## 9. 테스트 결과

```
./gradlew :data:testDebugUnitTest :domain:test --console=plain -q
→ BUILD SUCCESSFUL

data 모듈 전체                  263 tests, 0 failures, 0 errors
  AuthMappersTest                30 tests  (신규 +13)
  RemoteAuthRepositoryTest       33 tests  (신규 +8)
  AuthenticatedPathContractTest  17 tests  (신규 +1)
```

`:data:compileDebugKotlin` · `:domain:compileDebugKotlin` · `:presentation:compileDebugKotlin`
· `:app:compileDebugKotlin` 전부 통과.

`:domain:test` 는 `NO-SOURCE` 다 — domain 모듈에 테스트 소스도 junit 의존성도 없다.
그래서 `AuthPolicy` 검증(정규화·코드 형식·생년월일)은 data 테스트에 뒀다(기존 `AuthMappersTest` 가
이미 `AuthPolicy` 를 그렇게 테스트하던 방식 그대로).

### 작업 도중의 일시적 실패 기록 (해소됨)

작업 중간에는 Track A 가 고치던 `PartyMappersTest`·`RemoteRideRepositoryTest` 가 컴파일되지 않아
(`joinParty` 반환형, `PartyDetailResponse.id/members`, `MemberInfo.joinedAt` 제거 — 입력 문서 §2·§3)
`:data:compileDebugUnitTestKotlin` 이 모듈 전체를 한 번에 컴파일하는 탓에 테스트 태스크 전체가 실패했다.
내 변경과 무관했음을 확인하려고 그 두 파일만 컴파일에서 제외하는 init script
(`<scratchpad>/exclude-trackA.gradle`, 프로젝트 파일 무수정)로 검증했고, 그 상태에서 전부 통과했다.
**Track A 가 자기 테스트를 갱신한 뒤 제외 없이도 초록이다** — 위 결과가 그 상태의 것이다.

**실서버 검증**: send/verify 두 엔드포인트의 요청·응답 shape 은 리더가 로컬 서버에서 실측 확인
(204 / `{"verificationToken":"..."}`). 앱에서 직접 호출한 E2E 는 아직 없다 → 에뮬레이터 실기 검증은 qa-verifier 몫.

---

## 10. 백엔드 계약 중 이상한 점

1. **번호 형식 오류가 두 코드로 갈린다.** `SmsSendRequest.phoneNumber` 에는 `@Pattern` 이 있는데
   `SmsVerifyRequest.phoneNumber` 에는 `@NotBlank` 만 있다. 같은 잘못된 번호가
   발송에서는 `INVALID_REQUEST`, 확인에서는 `USER105` 로 온다. 앱은 양쪽 다 같은 문구로 수렴시켰지만
   서버 쪽 `@Pattern` 을 verify 에도 붙이는 게 맞다.

2. **`USER204` 가 세 상황을 뭉친다.** "발송한 적 없음" · "3분 만료" · "이미 인증에 성공해 코드가 지워짐"
   이 전부 `SMS_CODE_EXPIRED` 다 (`findCode` 가 비면 바로 이 코드). 화면이
   "인증번호를 먼저 요청하세요" 와 "만료됐어요" 를 구분할 수 없다.

3. **거절된 발송도 쿨다운 슬롯을 먹는다.** `send` 는 `tryStartCooldown` 을 먼저 하고 그 다음에
   일일 한도를 센다. 한도 초과로 던질 때 `releaseCooldown` 을 부르지 않는다(발송 사업자 실패 경로에만 있다).
   실질 영향은 작지만(이미 거절 상태) 의도된 설계인지 확인이 필요하다.

4. **남은 쿨다운/횟수를 알 길이 없다** (§5 하단). `Retry-After` 도 없다. 앱이 재시작되면
   재전송 버튼을 바로 활성화할 수밖에 없고, 사용자는 눌러 보고 429 를 받는다. → **백엔드 요청 후보**:
   `send` 204 응답에 `retryAfterSeconds` / `remainingToday` 를 싣거나 `Retry-After` 헤더를 주면
   화면이 정확해진다.

5. **`verify` 는 번호 중복을 확인하지 않는다.** `send` 만 한다. 발송과 확인 사이에 그 번호로
   누가 가입하면 인증은 통과하고 가입에서 409 가 난다. 드물고, 앱이 409 를 이미 처리하므로 실해는 없다.

6. **입력 문서 §4 와 실제 응답 필드명이 다르다.** 문서는 `UserResponse` 를 `(publicId, nickname)` 으로
   적었지만 실제 레코드는 `UserResponse(UUID uuid, String name)` 이다 — **JSON 키는 `uuid`/`name`.**
   앱 `RegisterResponse(uuid)` 가 실제 쪽과 맞으므로 변경 불필요. 문서가 틀렸다.

7. 참고(결함 아님): `moyeota.sms.fixed-code` 는 `provider=log` 가 아니면 기동 자체가 막힌다
   (`Assert.isTrue` 두 개). 운영에 고정 코드가 새어 나갈 경로는 없다.

---

## 11. 후속 인계

- **가입 화면 에이전트**: §2 시그니처 + §4 토큰 수명/재사용 + §7 TODO 2건.
  핵심 한 줄 — **가입 실패(409)에 문자 인증을 다시 시키지 말 것. 토큰은 10분간 살아 있다.**
  그리고 **전화번호 중복은 발송 단계(409)에서 난다** — 인증번호 입력칸을 열기 전에 안내해야 한다.
- **qa-verifier**: 교차 검증 포인트 — ① 번호에 하이픈을 넣어 입력해도 send/verify/register 세 요청 본문이
  모두 `01012345678` 형태인지(logcat BODY 로그), ② `verify` 성공 후 번호를 고쳐 가입하면
  `USER207` → "휴대폰 인증을 다시" 로 떨어지는지, ③ 닉네임 중복으로 가입 실패 후 **재인증 없이**
  닉네임만 고쳐 가입이 성공하는지(§4 의 핵심 가정), ④ 60초 안에 재전송을 누르면 429 가
  `UNKNOWN` 이 아니라 쿨다운 문구로 보이는지. 로컬 서버 고정 코드는 `123456`.
- **Track A 에게**: `data/src/test` 의 `PartyMappersTest`·`RemoteRideRepositoryTest` 가 아직 컴파일되지 않아
  `:data:testDebugUnitTest` 전체가 실패한다(§9). 내 테스트는 그 둘만 빼면 전부 통과한다.
