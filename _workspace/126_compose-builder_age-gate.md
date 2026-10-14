# 126 · 만 14세 미만 가입 차단 (compose-builder)

브랜치 `feat/backend-sync-sse` 워킹트리 (커밋 안 함). 실기 검증 없음 — 지시대로.

## 변경 파일

| 파일 | 변경 |
|---|---|
| `domain/src/main/kotlin/com/moyeota/domain/model/Auth.kt` | `AuthPolicy.MIN_AGE = 14`, `AuthPolicy.isOldEnough(birthDate, today)` 추가 (+KDoc 에 `isValidBirthDate` 와의 역할 분리표) |
| `presentation/src/main/kotlin/com/moyeota/presentation/feature/auth/SignupFieldSupport.kt` | `parseCalendarDate`(private) 분리, `parseBirthDate(digits, today)` 로 기준일 주입, **`birthDateError(digits, today)` 신규**, `BirthDateHelperText` 신규 |
| `presentation/.../feature/auth/ProfileSetupScreen.kt` | 10 화면: `today` 파라미터, 생년월일 검증을 `birthDateError` 로 교체, 「다음」 판정에 연령 반영, 입력란 안내 문구 추가, KDoc 보강 |
| `presentation/.../feature/auth/IdentityVerifyScreen.kt` | 09 화면: 같은 `birthDateError` 사용, CTA 판정에 `birthError == null` 추가, 안내 문구 추가 |
| `presentation/.../core/SupportLinks.kt` | `PRIVACY_URL` KDoc 에 "그 페이지에 만 14세 미만 가입 불가가 들어가야 한다" 한 줄 |
| `presentation/src/test/kotlin/com/moyeota/presentation/feature/auth/BirthDateRulesTest.kt` | **신규** — 16 케이스 |
| `docs/BACKEND-GAP-CLEANUP.md` | §2 「추가·변경한 것」에 한 줄 (앱만의 방어선·API 직접 호출 우회 사실 포함) |

진입 경로: 04 로그인 「이메일로 시작하기」 / 04a 「가입하기」 → **10 프로필 만들기**(`Routes.SIGNUP_PROFILE`) → 「기본 정보」 절의 생년월일. 09 `IDENTITY_VERIFY` 는 여전히 어느 그래프에도 미연결(코드만 살아 있음).

## 만 나이 계산 방식과 경계값

```kotlin
fun isOldEnough(birthDate: LocalDate, today: LocalDate): Boolean =
    !today.isBefore(birthDate.plusYears(MIN_AGE.toLong()))
```

- **생일이 지났는지까지 본다.** 연도 빼기로 하면 2012-12-31 생이 2026-01-01 에 통과한다(실제 13세) — 테스트 `연도 차이가 14여도 생일 전이면 거절한다` 가 이 실수를 못 박는다.
- **생일 당일에 통과한다**(`isBefore` 가 아니라 `!isBefore`). 하루 전 거절 / 당일 통과 / 하루 뒤 통과를 각각 테스트로 고정했다.
- `today` 를 **인자로 받는다.** 경계값이 이 함수의 전부인데 안에서 `LocalDate.now()` 를 부르면 기기 시계에 묶여 세 경우를 검증할 수 없다. 호출부는 화면이고, 10 은 `today: LocalDate = LocalDate.now()` 파라미터로 받아 넘긴다(프리뷰·테스트가 시계를 고정할 수 있다).
- **윤년 2월 29일생**: `Period.between` 대신 `plusYears` 를 골랐다. `LocalDate.plusYears` 는 해당일이 없으면 그 달 말일로 맞춘다 — 2008-02-29 + 14년 = **2022-02-28**. 민법 제160조 ③(최종 월에 해당일이 없으면 말일에 만료)과 같은 결과이고, `Period` 로 세면 그날 13세로 떨어져 **법적으로 14세인 사람을 하루 더 막는다.** 그 하루를 제외하면 두 방식의 판정은 모든 날짜에서 동일하다. 어느 입력에도 예외를 던지지 않는다(윤년 산술을 직접 하지 않으므로).
- 미래 날짜·1900년생 등 극단값도 예외 없이 Boolean 을 돌려준다.

## 기존 검증과의 역할 분리

| | `isValidBirthDate(value)` (기존) | `isOldEnough(birthDate, today)` (신규) |
|---|---|---|
| 근거 | 서버 `UserRegisterRequest` 의 `@Past` | 개인정보 보호법 + 앱 정책(`MIN_AGE`) |
| 막는 값 | 보내면 **400 이 되는** 값(미래·UTC 기준 오늘) | 보내면 **200 이지만 받아선 안 되는** 가입 |
| 기준 시각 | UTC 오늘(서버가 Instant 로 비교하므로 내부 고정) | 호출부가 넘긴 오늘(사용자 지역 날짜) |
| 사용자가 할 일 | 날짜를 고친다 | 고칠 게 없다 — 가입이 안 된다 |

만 14세 이상이면 당연히 과거 날짜라 포함 관계처럼 보이지만 합치지 않았다: 기준 시각이 다르고(서버 타임존 vs 사용자 지역), 바뀌는 이유가 다르고(서버 검증 vs 법·정책), 무엇보다 **사용자에게 할 말이 다르다.** 두 판정의 근거와 분리 이유는 `AuthPolicy.isOldEnough` KDoc 에 표로 적었다.

파싱도 역할을 쪼갰다: `parseCalendarDate`(달력에 있는 날짜인가, private) → `parseBirthDate`(생년월일로 쓸 수 있는가 = 미래 제외) → `birthDateError`(무엇이 문제인지 문구). 예전에는 "없는 날짜"와 "미래 날짜"가 같은 null 로 뭉개져 한 문구를 쓰고 있었다.

## 화면 문구

생년월일 한 칸이 거절 사유 셋을 **각각 다른 문구로** 말한다(`birthDateError`):

| 사유 | 문구 |
|---|---|
| 달력에 없는 날짜(`20250230`, 1900년 이전) | 「실제로 있는 날짜를 YYYYMMDD 로 입력해 주세요」 (기존 문구 유지) |
| 오늘 이후(서버 `@Past` 에 걸릴 값) | 「생년월일은 오늘 이전 날짜로 입력해 주세요」 (기존 문구 유지) |
| **만 14세 미만** | **「만 14세 미만은 가입할 수 없어요. 생년월일을 다시 확인해 주세요」** |

- 「입력이 올바르지 않습니다」 류로 접지 않았고, 세 문구가 서로 겹치지 않는 것을 테스트가 확인한다(`형식 미래 연령 세 문구는 서로 다르고 연령은 이유를 말한다`).
- 뒷문장이 「다시 확인해 주세요」인 이유: 이 오류가 실제로 뜨는 가장 흔한 경우는 2005 를 2025 로 잘못 치는 오타다. 앞문장이 사실(가입 불가)을 말하고 뒷문장이 고칠 수 있는 유일한 길을 가리킨다.
- 숫자는 `AuthPolicy.MIN_AGE` 를 끼워 쓴다 — 정책이 바뀌면 문구가 함께 바뀐다.
- **미리 고지한다**: 입력란 아래 안내 `BirthDateHelperText` = 「만 14세 이상만 가입할 수 있어요」. 오류가 있으면 `MoyeotaTextField` 가 errorText 를 대신 띄우므로 겹치지 않는다. 다 쓰고 나서 막히는 대신 쓰기 전에 알게 된다.
- 「다음」은 `birthValid = birthDate != null && birthError == null` 로 잠긴다 — 버튼 판정과 문구가 **같은 근거**를 본다(한쪽만 고치는 어긋남이 생기지 않는다).
- 색상·컴포넌트 신규 없음. `MoyeotaTextField` 의 errorText/helperText 만 쓴다.

## 09 IDENTITY_VERIFY 영향

같은 `parseBirthDate` 를 쓰고 있었다. 깨지지 않았고, **같은 규칙을 쓰도록 맞췄다**:

- `parseBirthDate` 에 `today` 기본값(`LocalDate.now()`)을 뒀으므로 09 의 호출부는 그대로 컴파일된다. 동작도 전과 같다(없는 날짜·미래 날짜 → null).
- 중복돼 있던 09 의 `birthError when` 블록을 `birthDateError(birthDigits)` 로 교체했다. 문구가 하나 늘었을 뿐 기존 두 경우의 문구는 그대로다.
- `ctaEnabled` 에 `birthError == null` 을 추가했다. **필요한 변경이다**: 만 13세는 "실제로 있는 과거 날짜"라 `parseBirthDate != null` 이고, 이 조건 없이는 오류 문구는 뜨는데 CTA 는 열린 상태가 된다.
- 09 가 가입 경로로 되살아날 때 연령 제한이 빠진 채 돌아오지 않게 한 것이기도 하다. 09 는 지금 미연결이라 화면 동작 변화가 사용자에게 보이지는 않는다.
- 12 매너 서약(`SignupDraft.toNewUser()`)에는 연령 검사를 **넣지 않았다.** 거기서 걸리면 사용자는 「가입 정보가 부족해요. 이전 단계를 다시 확인해 주세요」(뭉뚱그린 문구)를 보게 되고, 10 을 통과하지 않고 12 에 닿는 경로가 없다. 중복 방어 대신 두 입력 화면이 같은 함수를 쓰게 하고 테스트로 고정하는 쪽을 골랐다.

## 테스트 결과

`./gradlew :app:assembleDebug :data:testDebugUnitTest :presentation:testDebugUnitTest --console=plain -q` → **EXIT 0**
presentation 244건 / data 278건, 실패 0 (신규 16건 포함. 이전 presentation 228건)

신규 `BirthDateRulesTest` (16): 생일 하루 전 거절 · 당일 통과 · 하루 뒤 통과 · 연도차 14 지만 생일 전 거절 · 윤년 2/29생 평년 판정(2/27 거절, 2/28 통과, 3/1 통과, 윤년 2/29 통과, 예외 없음) · 기준일이 윤일인 경우 · 미래 날짜(`29990101`)는 연령과 다른 문구 · 내일 · 오늘 · 1900-01-01 통과 · 1899년은 오타 처리 · 8자리 미만은 무언 · 없는 날짜 · 세 문구 비중복 · 안내 문구의 최소 연령 일치 · 기준일 생략 시 기기 시계(09 경로).

테스트는 기준일을 전부 고정값(2026-10-13 등)으로 넣어 기기 시계와 무관하다. 예외 둘(「내일 날짜」·「오늘 날짜」)은 `LocalDate.now()` 를 쓰는데, "내일"을 고정 날짜로 적으면 돌리는 날에 따라 내일이 아니게 되기 때문이다. 「오늘」 케이스는 **문구를 단정하지 않고 막히는 것만** 검증한다 — UTC 보다 앞선 지역(KST)에서는 서버 `@Past` 에 걸릴 값이라 미래 문구가 맞고, UTC 보다 뒤진 지역에서는 서버가 받아 줄 과거 Instant 라 연령 문구가 맞다(둘 다 사실이고 둘 다 막는다).

## 백엔드에도 같은 제한을 넣어야 하는지 — 넣어야 한다

**지금은 앱이 유일한 방어선이고, API 를 직접 호출하면 그대로 우회된다.** `POST /api/v1/auth/register` 의 `UserRegisterRequest` 는 `birthDate` 에 `@Past` 뿐이라, 서버는 생년월일이 어제인 요청도 **200 으로 받아 저장한다.** 문자 인증 토큰이 필요하지만 그건 번호 소유 확인일 뿐 나이와 무관하다 — 휴대폰이 있는 13세는 앱 화면을 거치지 않고(curl 한 줄로) 가입할 수 있다.

이게 단순한 "완벽하지 않은 방어" 수준의 문제가 아닌 이유:

1. **고지가 거짓이 된다.** 개인정보 처리방침에 「만 14세 미만은 가입할 수 없습니다」라고 적는 순간, 그 문장은 서버 동작에 대한 약속이 된다. 앱 화면 분기는 그 약속을 지키는 수단이 아니다.
2. **법적 책임은 서버에 쌓인 데이터에 붙는다.** 개인정보 보호법상 문제는 "앱이 막았는가"가 아니라 "법정대리인 동의 없이 만 14세 미만의 개인정보를 처리했는가"다. 우회로 들어온 레코드는 DB 에 남고, 실명·생년월일·연락처가 들어 있다.
3. **앱 버전은 되돌아간다.** 구버전 APK 는 계속 돌아가고, 연령 판정이 없던 빌드도 서버에 가입 요청을 보낼 수 있다. 서버 검증이 없으면 "언제 배포된 앱으로 가입했는가"에 따라 정책이 달라진다.

제안(백엔드 쪽 작업 — 이 작업 범위 밖, 서버는 건드리지 않았다):

- `UserRegisterRequest.birthDate` 에 **만 14세 이상 검증을 추가**한다(커스텀 제약 또는 `LocalUserService.register` 에서 `Period.between(birthDate, LocalDate.now(KST)).years >= 14`). 거절은 전용 에러 코드로 — 400 `INVALID_REQUEST` 에 섞으면 앱이 「입력한 정보를 다시 확인해 주세요」로 뭉개고, 우리가 방금 없앤 뭉뚱그린 문구가 서버 경로로 되돌아온다. 코드가 생기면 `AuthError` 에 한 줄, `authErrorMessage` 에 같은 문구 한 줄로 받는다(`when` 이 비포괄이 아니라 컴파일러가 빠뜨리지 못하게 막는다).
- **기준 시각**: 서버는 `birthDate` 를 Instant 로 받으므로 어느 타임존의 "오늘"로 만 나이를 세는지 정해야 한다(KST 권장 — 사용자 기준). 앱은 기기 로컬 날짜로 센다. 경계 하루의 차이는 생일 당일 사용자에게만 보이는데, **서버가 더 엄격하면 앱이 통과시킨 가입이 400 으로 떨어진다**(문구 없는 실패). 서버 기준을 KST 로 두면 국내 사용자에게는 두 판정이 일치한다.
- 이미 쌓인 데이터: 연령 제한 없이 가입된 레코드가 있는지 `birthDate` 로 조회해 확인할 수 있다(지금은 앱 경로로 만든 계정뿐이라 없을 가능성이 높지만, 제한을 넣기 전에 한 번 세는 게 맞다).

`docs/BACKEND-GAP-CLEANUP.md` §2 에 이 사실(앱만의 방어선, 직접 호출 우회)을 적어 뒀다 — §4 와 성격이 반대인 공백이다. 거기는 "백엔드가 없어서 앱에서 뺀 것"이고, 이건 **"백엔드가 더 막아야 하는 것"**이다.
