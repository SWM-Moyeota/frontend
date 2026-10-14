# 116 — 화면: 문자 인증 가입 (Track B, presentation/)

작업: `_workspace/00_input_backend-sync-sse.md` §4 (Backend #212) 의 화면 절반.
시그니처·제한값·에러 매핑의 1차 출처는 `_workspace/115_api-integrator_sms-verification.md` (연동 보고서).
범위: `presentation/feature/auth/` + 가입 그래프 배선. **data/·domain/ 무수정.**
Track A 소유 파일(`MatchingApi`·`PartyDtos`·`RideRepository`·`PartyEventSource`·`MatchWaiting*`)은 열지 않았다.

---

## 1. 결론 한 줄

가입 폼(10 프로필 만들기) 안의 휴대폰 번호 입력란 **바로 아래**에 인증 블록을 넣고,
인증을 끝내야 「다음」이 열리게 했다. 인증 토큰은 가입 제출까지 10 의 ViewModel 이 들고 있으며,
**아이디·닉네임 중복(409)으로 가입이 실패해도 인증을 다시 시키지 않는다.**

## 2. 설계 판단 (지시와 다르게 한 것 / 지시에 없어 결정한 것)

| 항목 | 결정 | 근거 |
|---|---|---|
| 인증 위치 | 지시대로 **10 안에서** 처리. 09 `IDENTITY_VERIFY` 는 미연결 유지 | 번호 입력란이 이미 10 에 있고 단계 표시가 1/2·2/2 다 |
| 인증번호 입력 UI | 6칸 박스(07 화면 모양) 대신 **`MoyeotaTextField` 한 줄** | 10 은 입력 필드가 8개다. 64dp 박스 6개를 세우면 번호 입력과 다음 절 사이가 한 화면을 넘어간다 |
| 발송·확인 버튼 배치 | 「남은 시간 2:43」 + 「다시 받기」 한 줄(07 화면 배치 그대로) + 아래 전폭 「확인」 | `SecondaryButton` 둘을 반쪽 너비로 나란히 두면 "인증번호 다시 받기"가 들어가지 않아 두 줄로 깨진다 |
| 번호 수정 | 인증 완료 시 번호 입력란을 **잠그고**, 블록에 「번호 변경」 링크를 둔다 | 지시 3(번호 수정 불가)과 4(고치면 무효)가 그대로 읽으면 충돌한다. 잠금 + 명시적 변경 버튼이면 둘을 다 지킨다 — 실수로 한 글자 지워 인증이 날아가는 일은 막고, 바꾸려는 사람은 무효화를 선택해서 간다. 방어로 `onPhoneDigitsChanged` 도 여전히 다른 번호면 무효화한다(단위 테스트 있음) |
| 타이머 구현 | 남은 초를 깎지 않고 **기한(epoch millis) − 현재 시각**으로 계산 | 1초마다 카운터를 깎는 방식은 절전(Doze)으로 틱을 거르면 뒤처져 쿨다운이 실제보다 길게 보인다 |
| 타이머 소유 | 컴포지션이 아니라 **ViewModel**(10 의 NavBackStackEntry 스코프) | 12 에 올라가 있는 동안에도 쿨다운이 흘러야 한다. 토큰도 같은 이유로 여기 있다(12 에서 409 로 되돌아와도 살아 있어야 한다) |
| 토큰 10분 남은 시간 표시 | **하지 않았다** | 가입 폼 위에 초읽기를 얹으면 서두르게만 만든다. 10분은 남은 입력(아이디·비밀번호·이메일)에 충분하고, 넘기면 `PHONE_NOT_VERIFIED` 안내 + 인증 단계 되돌리기로 복구된다 |
| 생년월일 | `AuthPolicy.isValidBirthDate` 로 좁혔다 (연동 보고서 §7 이 넘긴 일) | 서버는 `@Past` 를 Instant 로 보고 앱은 UTC 자정으로 올려 보낸다 → **09:00 KST 이전에 "오늘"을 고르면 가입이 400** 이다. `parseBirthDate` 는 손대지 않았다(09 화면도 쓰고, 오늘 입력에 "없는 날짜"라고 말하면 거짓말이 된다) — 화면에 전용 분기와 문구를 더했다 |
| 가입 번호 표기 | 하이픈 없는 숫자열로 제출(기존엔 `010-1234-5678`) | **문자 인증에 쓴 번호와 글자 단위로 같은 값**이 된다. 서버는 어느 쪽이든 하이픈을 떼고 해시하므로 결과는 같지만, "인증은 됐는데 가입이 400" 을 디버깅할 때 번호 표기를 의심할 필요가 없어진다 |

## 3. 화면 흐름과 상태 전이

```
10 프로필 만들기 (1/2)
└ 기본 정보 ─ 휴대폰 번호 [입력란]  ← 인증 완료 시 잠김
                └ 인증 블록
   Idle ──「인증번호 받기」(010+8자리일 때 활성)
           └ send 204 ─▶ CodeSent  : 6자리 입력칸 + 남은 시간 3:00 + 「60초 후 다시 받기」
                            ├ 6자리 채움 → 자동 verify (또는 「확인」)
                            │    └ 200 ─▶ Verified : 「휴대폰 인증 완료」 · 「번호 변경」 · 번호 잠금
                            ├ 3분 경과 → 입력칸 닫고 "만료됐어요" + 「인증번호 다시 받기」
                            └ 429 5회 초과 → 입력칸 닫고 재발송 유도 (쿨다운은 그대로)
           └ 409 중복 ─▶ Idle : 번호 입력란 아래 "이미 가입된 번호예요…" + 「로그인하러 가기」(→ 04a)
   「다음」 = Verified 일 때만 활성 → SignupDraft(… verificationToken) → 12 매너 서약
12 매너 서약 (2/2) 「동의하고 가입 완료」 = register + 자동 로그인
   ├ 409 닉네임 중복 → 배너 + 「닉네임 바꾸기」 → 10 (토큰 유지, 재인증 없음)
   └ 400 USER207    → 배너 + 「휴대폰 인증 다시 하기」 → 10 (토큰 폐기, Idle 로 되돌림)
```

상태 전이는 전부 `PhoneVerifyState` 의 **순수 함수**다. 화면은 판단하지 않고 그리기만 한다.

| 전이 | 하는 일 | 핵심 |
|---|---|---|
| `onRequestStart(phone)` | 상태를 그 번호에 묶고 진행 표시 | 응답이 늦게 와도 번호가 바뀌었으면 버릴 근거가 된다 |
| `onCodeSent(now)` | 만료 3분 + 쿨다운 60초 동시 시작, 입력값·문구 초기화 | 재발송 시 서버도 오입력 횟수를 0으로 되돌린다 |
| `onRequestFailure(409)` | `Idle` + `alreadyRegistered` | **인증번호 입력칸을 열지 않는다** |
| `onRequestFailure(429 쿨다운)` | 쿨다운을 최대치(60초)로 다시 잡음(`maxOf`) | 서버가 남은 초를 안 주니 보수적으로. 이미 더 긴 타이머는 앞당기지 않는다 |
| `onRequestFailure(429 일일)` | `dailyLimitReached` → 버튼 잠금 | 재시도를 권하지 않는다 |
| `onVerifyFailure(불일치)` | 입력칸만 비움, 타이머 유지 | 서버에 인증번호가 남아 있다 |
| `onVerifyFailure(만료·5회초과)` | 입력칸 닫음, 쿨다운 유지 | 서버가 인증번호를 지웠다 → 재발송만이 길 |
| `onVerifyFailure(번호형식)` | `Idle` + 번호 입력란 문구 | 고칠 곳이 인증번호가 아니다 |
| `onCodeExpired(msg)` | 3분 경과 시 서버에 묻지 않고 입력칸 닫음 (멱등) | 다 입력하고 「확인」 눌러 400 받는 길을 없앤다 |
| `onPhoneDigitsChanged` | 다른 번호면 **전부 초기화** | 서버가 토큰을 번호 해시에 묶는다 |
| `invalidated(msg)` | 「번호 변경」·`PHONE_NOT_VERIFIED` 공용 | 전자는 문구 없음(스스로 누른 행동) |

## 4. 에러 문구표 (`SignupDraft.kt` · `authErrorMessage`)

같은 HTTP 상태로 뭉쳐 오는 묶음(429 셋 · 400 셋)이 **한 문구도 겹치지 않는다** — 단위 테스트로 고정했다.

| AuthError | HTTP·코드 | 문구 | 화면 동작 |
|---|---|---|---|
| `PHONE_NUMBER_DUPLICATED` | 409 `USER104` | 이미 가입된 번호예요. 로그인해서 이어가 주세요 | 입력칸 열지 않음 + 「로그인하러 가기」 |
| `INVALID_PHONE_NUMBER` | 400 `USER105` | 휴대폰 번호를 010-1234-5678 형식으로 입력해 주세요 | 1단계로 되돌림 |
| `INVALID_REQUEST`(`phoneNumber:`) | 400 | (같은 문구) | 발송 쪽 `@Pattern` 위반을 같은 안내로 수렴 |
| `SMS_COOLDOWN` | 429 `USER202` | 인증번호는 1분에 한 번만 받을 수 있어요. 버튼의 남은 시간이 끝나면 다시 받아 주세요 | 쿨다운 60초 재설정, 버튼 카운트다운 |
| `SMS_DAILY_LIMIT_EXCEEDED` | 429 `USER203` | 오늘 받을 수 있는 인증번호를 모두 썼어요(하루 5번). 내일 다시 시도하거나 다른 번호로 가입해 주세요 | **버튼 잠금**, 재시도 권하지 않음 |
| `SMS_CODE_EXPIRED` | 400 `USER204` | 인증번호가 만료됐어요. 「인증번호 다시 받기」로 새 번호를 받아 주세요 | 입력칸 닫음 |
| `SMS_CODE_MISMATCH` | 400 `USER205` | 인증번호가 일치하지 않아요. 문자로 받은 6자리를 다시 입력해 주세요 | 입력칸만 비움 |
| `SMS_TOO_MANY_ATTEMPTS` | 429 `USER206` | 인증번호를 5번 잘못 입력했어요. 새 인증번호를 받아 주세요 | 입력칸 닫음(쿨다운 유지) |
| `PHONE_NOT_VERIFIED` | 400 `USER207` | 휴대폰 인증이 만료됐어요. 인증을 다시 하고 가입을 마쳐 주세요 | 12 배너 + 「휴대폰 인증 다시 하기」 → 10 초기화 |
| `SMS_UNAVAILABLE` | 502 `USER201` / 503 `USER208` | 문자 인증이 일시적으로 안 되고 있어요. 잠시 뒤 다시 시도해 주세요 | 바로 재시도 가능(서버가 쿨다운을 풀어 준다) |

문구에 든 숫자(하루 5번·5번 잘못)는 `AuthPolicy` 상수에서 보간한다 — 서버 제한값이 바뀌면 문구가 따라간다.

`authUserMessage()`(Throwable 확장)는 이제 `authErrorMessage(error, serverMessage)` 를 호출하는 껍데기다.
예외 객체 없이도 문구가 필요한 곳이 생겼기 때문이다 — **3분 자체 만료**와 **가입 거절로 인한 되돌리기**.
`when` 은 여전히 비포괄이 아니다(enum 이 늘면 컴파일이 깨져 문구 누락을 막는다 — 이번 8종이 그렇게 발견됐다).

## 5. 앱 타이머의 한계 (KDoc 에 명기)

`PhoneVerification.kt` 파일 머리 KDoc에 적었다. 서버가 **남은 쿨다운·남은 발송 횟수·남은 입력 시도를
응답에 담지 않고 `Retry-After` 도 주지 않는다** → 카운트다운은 전부 앱 자체 계산이고:

- **앱을 껐다 켜면 쿨다운을 모른다.** 버튼이 곧바로 활성으로 보이고, 눌러서 429 를 받은 뒤에야 안내한다.
  그때는 최대치(60초)로 보수적으로 다시 잡는다(실제 남은 시간보다 길 수 있다).
- 일일 한도도 미리 막을 수 없다 — 한 번 429 를 받은 뒤에야 버튼을 잠근다.
- 입력 시도 5회는 서버만 센다. 앱은 429 `SMS_TOO_MANY_ATTEMPTS` 로만 알게 된다.

→ **백엔드 요청 후보**(연동 보고서 §10-4 와 같은 건): `send` 204 응답에 `retryAfterSeconds`/`remainingToday`.

## 6. 변경·추가 파일

### 추가 (3)
- `presentation/src/main/kotlin/com/moyeota/presentation/feature/auth/PhoneVerification.kt`
  — `PhoneVerifyStage`·`PhoneVerifyState`(공개: 공개 컴포저블 파라미터) + 전이·판정 순수 함수 13개
  + `PhoneVerifyViewModel`(발송/확인/틱) + `PhoneVerificationBlock`(UI)
- `presentation/src/test/kotlin/com/moyeota/presentation/feature/auth/PhoneVerifyStateTest.kt` — 39 테스트
- `presentation/src/test/kotlin/com/moyeota/presentation/feature/auth/SignupDraftTest.kt` — 9 테스트

### 수정 (6)
- `…/feature/auth/ProfileSetupScreen.kt` — 인증 블록 삽입, 번호 입력란 잠금·문구 연결,
  「다음」 활성 조건에 인증 완료 추가, 생년월일 UTC 보정, 제출 시 토큰·번호 형태
- `…/feature/auth/ProfileSetupRoute.kt` — `PhoneVerifyViewModel` 추가, 재인증 신호 수신, 로그인 유도 경로
- `…/feature/auth/SignupDraft.kt` — `TODO(가입 화면 에이전트)` **2곳 제거**: 토큰 필드 KDoc 확정,
  문구 표를 `authErrorMessage(error, serverMessage)` 로 분리하고 8종 문구 확정
- `…/feature/auth/SignupSubmitRoute.kt` — `UiState.phoneNotVerified` 추가, `signupErrorNeedsReverify` 적용
- `…/feature/auth/MannerPledgeScreen.kt` — `onReverifyPhone` 파라미터 + 배너 복구 링크 일원화
- `…/core/MainNavGraph.kt` — `reverifyPhoneRequest` 카운터, 10 에 `onGoToLogin`·신호 전달,
  12 의 「휴대폰 인증 다시 하기」 배선, 새 가입 시작 시 카운터 리셋

**화면 진입 경로**: 04 로그인 「이메일로 시작하기」(또는 04a 「가입하기」) → **10 `auth/profile`**
→ 「다음」 → 12 `auth/manner-pledge` → 13 `auth/signup-complete`.
새 라우트는 추가하지 않았다(인증이 10 안에 있으므로 `Routes.kt` 무수정).

## 7. 재인증 신호를 카운터로 둔 이유 (놓치기 쉬운 함정)

12 → 10 되돌리기를 `Boolean` 으로 두면 **두 번째 신호를 놓친다**(새로 받은 토큰도 또 만료될 수 있다).
그래서 `MainNavGraph` 가 `reverifyPhoneRequest++` 로 일련번호를 올린다.
여기에 함정이 하나 더 있었다 — 10 의 컴포지션은 12 로 올라가는 동안 **버려진다.** 돌아올 때마다
`LaunchedEffect(키)` 가 새로 실행되므로, 사용자가 12 에서 그냥 **뒤로** 눌러 돌아온 정상 경로에서도
같은 신호가 다시 적용돼 **멀쩡한 인증이 날아간다.** 그래서 ViewModel 이 "이미 처리한 신호 번호"를
기억하고 중복을 무시한다(`PhoneVerifyViewModel.handledReverifyRequest`). 새 가입을 시작하면
`MainNavGraph` 가 카운터를 0으로 되돌린다 — 지난 가입의 안내가 새 폼에 남지 않게.

## 8. 빌드·테스트 결과

```
./gradlew :app:assembleDebug :presentation:testDebugUnitTest --console=plain -q
→ (아래 "실행 기록" 참조)
```

### 실행 기록

1. `:presentation:compileDebugKotlin` — **내 파일에서 에러 0.**
   첫 시도에서 하나 잡혔고 고쳤다: 공개 컴포저블 `ProfileSetupScreen` 이 `internal` 타입을
   파라미터로 노출 → `PhoneVerifyStage`·`PhoneVerifyState` 를 공개로 올렸다
   (기존 `NicknameCheckState` 와 같은 자리·같은 가시성이다). 전이 함수·ViewModel·UI 블록은 internal 유지.
2. 모듈 전체 컴파일은 한동안 **Track A 의 작업 중 파일** 때문에 실패했다 —
   `RideOngoingRoute.kt`(`getPartyStatus`), `DispatchStatusRoute.kt`(`shouldFetchPartyDetail`·
   `shouldPublishDetail`·`isRideStarted`·`fingerprint`). 내 변경과 무관하고 내가 열지 않은 파일이다.
   Track A 가 정리된 뒤의 최종 결과는 아래.

```
./gradlew :app:assembleDebug :presentation:testDebugUnitTest --console=plain
→ BUILD SUCCESSFUL   (2026-10-13 04:12, Track A 가 자기 테스트를 고친 뒤)

presentation 모듈 전체                174 tests, 0 failures, 0 errors
  PhoneVerifyStateTest   39 tests  (신규)
  SignupDraftTest         9 tests  (신규)
```

중간 확인: Track A 의 `RideFinishSignalTest.kt` 가 컴파일되지 않던 동안에는 그 파일 하나만 컴파일에서
빼는 init script(`<scratchpad>/exclude-trackA-test.gradle`, **프로젝트 파일 무수정**)로 내 테스트 48개가
통과함을 먼저 확인했다. 위 결과는 제외 없이 돌린 최종 상태의 것이다.

## 9. QA 가 확인해야 할 경로

로컬 서버 고정 인증번호는 **`123456`** 이다(`provider=log` + `moyeota.sms.fixed-code`).
실기 검증은 하지 않았다 — 에뮬레이터는 QA 담당이 쓴다.

진입: 04 로그인 → 「이메일로 시작하기」 → 10.

| # | 경로 | 기대 |
|---|---|---|
| 1 | 번호 11자리 입력 전 | 「인증번호 받기」 비활성, 번호 입력란 아래 "인증번호를 받아 확인해야 가입할 수 있어요" |
| 2 | 「인증번호 받기」 → `123456` 입력 | **버튼을 누르지 않아도** 자동 확인 → "휴대폰 인증 완료" + 번호 입력란 잠김 + 「다음」 활성 |
| 3 | **쿨다운** — 발송 직후 60초 안에 「다시 받기」 | 버튼이 `n초 후 다시 받기` 로 비활성. 0 이 되면 활성 |
| 4 | **쿨다운(앱 재시작)** — 발송 후 앱 강제 종료→재실행→같은 번호로 받기 | 버튼이 활성으로 보이고, 누르면 429 → "1분에 한 번만…" 문구 + 60초 카운트다운 시작. (§5 의 알려진 한계) |
| 5 | **만료** — 발송 후 3분 대기 | 입력칸이 닫히고 "인증번호가 만료됐어요…" + 「인증번호 다시 받기」. 만료 후 입력 시도 불가 |
| 6 | **불일치** — `000000` 입력 | 입력칸만 비워지고 남은 시간은 계속 흐른다. 5번째에 "5번 잘못 입력했어요" + 입력칸 닫힘 |
| 7 | **중복 번호** — 이미 가입된 번호로 「인증번호 받기」 | 인증번호 입력칸이 **열리지 않고**, 번호 입력란 아래 "이미 가입된 번호예요…" + 「로그인하러 가기」(→ 04a). 뒤로 누르면 입력값 유지 |
| 8 | **번호 변경** — 인증 완료 후 「번호 변경」 | 번호 입력란 잠금 해제, 인증 상태·토큰 초기화, 「다음」 비활성 |
| 9 | **가입 실패 후 재시도(핵심)** — 남이 쓰는 닉네임으로 가입 → 「닉네임 바꾸기」 → 닉네임만 고쳐 재가입 | **문자 인증을 다시 요구하지 않고** 가입 성공. 인증 블록은 "휴대폰 인증 완료" 그대로 |
| 10 | **토큰 만료** — 인증 후 10분 넘겨 가입 제출 | 12 배너 "휴대폰 인증이 만료됐어요…" + 「휴대폰 인증 다시 하기」 → 10 에서 인증 블록이 Idle 로, 다른 입력값은 유지 |
| 11 | **뒤로 가기 회귀** — 인증 완료 → 「다음」 → 12 에서 **뒤로** → 10 | 인증 완료 상태가 **그대로 남아 있어야 한다**(§7 의 함정) |
| 12 | 번호 표기 — 하이픈 넣어 입력 | logcat BODY 로그에서 send·verify·register 세 요청 모두 `01012345678` |
| 13 | 생년월일 — 오늘 날짜 입력 | "생년월일은 오늘 이전 날짜로 입력해 주세요"(09:00 KST 이전엔 어제도 UTC 기준으로 막힐 수 있다) |

## 10. 미완·불확실

1. **쿨다운·일일 한도·시도 횟수를 앱이 정확히 알 수 없다**(§5). 백엔드 응답 보강이 유일한 해결책이다.
2. **`USER204` 가 세 상황을 뭉친다**(발송 안 함 / 3분 만료 / 이미 인증 성공) — 연동 보고서 §10-2.
   화면은 전부 "만료됐어요 · 다시 받기"로 안내한다. "먼저 인증번호를 요청하세요"와 구분할 길이 없다.
   실제로는 앱이 발송 전에 입력칸을 열지 않으므로 사용자가 이 구분을 필요로 할 상황은 거의 없다.
3. **10분 토큰 수명을 화면이 세지 않는다**(§2). 넘기면 `PHONE_NOT_VERIFIED` 로 복구하는 설계다.
   QA #10 이 그 경로를 확인한다. 10분이 짧다는 신호가 QA 에서 나오면 남은 시간 표시를 다시 검토할 일이다.
4. **`PhoneVerifyViewModel` 자체에는 단위 테스트가 없다.** `presentation` 모듈 테스트 의존성이
   `junit` 하나뿐이고 `kotlinx-coroutines-test` 가 없다(의존성 추가 금지 지시). 그래서 전이·판정 로직을
   전부 순수 함수로 빼서 테스트했고, ViewModel 에는 **호출 순서와 늦은 응답 버리기**만 남겼다
   — 그 두 가지는 QA #4·#7·#11 경로가 실질적으로 덮는다.
5. **09 `IDENTITY_VERIFY` 는 손대지 않았다**(지시대로 미연결 유지). 통신사 본인 인증이 실제로 붙으면
   그 화면이 되살아날 자리이고, 지금의 문자 인증이 그것을 대체하지는 않는다.
6. `app:assembleDebug` 최종 통과는 Track A 의 작업 중 파일이 정리된 뒤에 확인했다(§8).
