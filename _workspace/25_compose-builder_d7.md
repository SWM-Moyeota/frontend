# 25 · compose-builder — D-7 수정 (본인 인증 입력 순서 뒤바뀜)

## 결함
09 본인 인증 [S04] 의 휴대폰·생년월일 입력에서 `19950315` 를 순서대로 치면 `1995-31-50` 이 된다.

원인: `value` 에 하이픈이 낀 **표시 문자열**을 넘기고 `onValueChange` 에서 숫자만 다시 뽑는 구조였다.
`BasicTextField(value: String, …)` 는 직전 selection 인덱스를 그대로 새 텍스트에 얹기 때문에,
구분자가 새로 끼어 표시 길이가 1 이 아니라 2 늘어나는 순간 커서가 옛 인덱스(= 구분자 앞쪽)에 남는다.
이후 입력이 그 자리에 꽂혀 자릿수 순서가 뒤집힌다.

재현 추적(생년월일): `1995` + `0` → 표시 `1995-0`(길이 6) 인데 selection 은 5 → 커서가 `0` 앞.
`3` 입력 → 원시 `199530` → … → `1995-31-0` → 최종 `1995-31-50`.

## 수정
`value` 는 원시 숫자만 유지(입력 1 글자 = 길이 1 증가라 커서가 어긋날 여지가 없다)하고,
하이픈은 `VisualTransformation` 으로만 그린다. 커서 좌표 변환은 `OffsetMapping` 이 책임진다.

### 변경 파일

| 파일 | 라인 | 내용 |
|------|------|------|
| `presentation/src/main/kotlin/com/moyeota/presentation/core/DigitGroupVisualTransformation.kt` | 신규 | `DigitGroupVisualTransformation` + `PhoneNumberTransformation`(3-4-4) / `BirthDateTransformation`(4-2-2) |
| `presentation/src/main/kotlin/com/moyeota/presentation/feature/auth/IdentityVerifyScreen.kt` | 49-50 | import 추가 |
| 〃 | 166, 173 | 휴대폰: `value = phoneDigits`(원시) + `visualTransformation = PhoneNumberTransformation` |
| 〃 | 188, 195 | 생년월일: `value = birthDigits`(원시) + `visualTransformation = BirthDateTransformation` |
| 〃 | 274 | 제출 payload 는 종전대로 하이픈 형식 — `PhoneNumberTransformation.format(phoneDigits)` 재사용 |
| 〃 | (삭제) | 화면 전용 `formatPhone` / `formatBirthDate` 제거, 포맷 로직 단일화 |
| `presentation/src/test/kotlin/com/moyeota/presentation/core/DigitGroupVisualTransformationTest.kt` | 신규 | OffsetMapping 경계값·왕복 검증 8 케이스 |
| `presentation/build.gradle.kts` | deps | `testImplementation(libs.junit)` 추가 (test 소스셋 신설) |

`core:designsystem` 은 손대지 않았다 — `MoyeotaTextField` 가 이미 `visualTransformation` 파라미터를
기본값 `VisualTransformation.None` 으로 받고 있다(`Inputs.kt:38`). data/·domain/ 변경 없음.

## OffsetMapping 설계 근거

그룹 크기 `[3,4,4]` → 구분자가 **앞에** 붙는 원시 경계 `boundaries = [3, 7]`.
표시 문자열은 뒤따르는 숫자가 있을 때만 구분자를 그린다(`010-` 같은 꼬리 방지, 기존 동작과 동일).

1. **실제로 그려진 구분자만 센다**: `emitted = boundaries.filter { it < digits.length }`.
   이걸 빠뜨리면 7 자리("010-1234", 표시 길이 8)에서 `originalToTransformed(7) = 7 + 2 = 9` 가 되어
   표시 길이를 넘고 Compose 가 예외를 던진다. 필터 후에는 `7 + 1 = 8` 로 정확히 끝을 가리킨다.

2. **originalToTransformed(o) = o + count { b in emitted : b <= o }**
   `<=` 이므로 경계에 선 커서는 구분자 **뒤**에 놓인다: 원시 4 → 표시 5, `1995-|03`.
   각 단계에서 최소 1 씩 증가하므로 단조 증가가 보장된다(Compose 요구사항).
   `o = digits.length` 일 때 emitted 전부가 더해져 정확히 표시 길이가 된다.

3. **transformedToOriginal(t) = t - count { s in separatorOffsets : s < t }**
   `separatorOffsets = emitted.mapIndexed { k, b -> b + k }` — k 번째 구분자 앞에는 이미 k 개가 껴 있다.
   휴대폰 11 자리면 `[3, 8]` = `"010-1234-5678"` 의 실제 `-` 인덱스와 일치.
   `<` 이므로 구분자 앞/뒤(표시 3·4)가 같은 원시 3 으로 접힌다 — 이는 정상이며 왕복은 보존된다
   (`transformedToOriginal(originalToTransformed(o)) == o`, 테스트에서 전 길이 전 offset 확인).

4. **범위 방어**: 재구성 도중 옛 offset 이 들어올 수 있어 양쪽 모두 `coerceIn` 한다.
   D-7 계열이 재발해도 IndexOutOfBounds 크래시가 아니라 커서가 경계에 붙는 정도로 끝난다.

경계값 표(생년월일 `19950315` → `1995-03-15`):

| 원시 | 0 | 4 | 6 | 8 |
|------|---|---|---|---|
| 표시 | 0 | 5 | 8 | 10 |

| 표시 | 4 | 5 | 8 | 10 |
|------|---|---|---|----|
| 원시 | 4 | 4 | 6 | 8 |

## 검증
- `./gradlew :presentation:testDebugUnitTest` → **8 tests, 0 failures**
  (경계 offset, 전 길이 왕복·단조성·범위, 범위 초과 방어, 표시 포맷)
- `./gradlew :presentation:compileDebugKotlin :app:assembleDebug --console=plain` → **BUILD SUCCESSFUL**
- 에뮬레이터 미사용·install 미실행 (지시대로 실기 확인은 리더 몫)

## 실기 확인 경로 (리더용)
스플래시 → 로그인 → 07 인증 완료/08 인증 요청 완료 → **09 본인 인증 [S04]**
→ 생년월일에 `19950315` 순서대로 입력 → `1995-03-15` 표시 확인
→ 휴대폰에 `01012345678` 입력 → `010-1234-5678` 표시 확인
→ 추가 확인: 중간 커서로 이동해 한 글자 지우기(백스페이스)·중간 삽입 시 순서 유지

## 후속 (리더 지시로 같은 커밋 범위에서 처리) — 31 결제 수단 추가 [PaymentAddScreen]

카드번호·유효기간이 D-7 과 정확히 같은 구조(`value = format…(digits)` + onValueChange 에서 숫자 추출)여서
동일 유틸로 교체했다. 같은 증상(예: `4000123412341234` 순서 입력 시 자릿수 뒤바뀜)이 재현될 수 있었다.

| 파일 | 라인 | 내용 |
|------|------|------|
| `presentation/.../core/DigitGroupVisualTransformation.kt` | 추가 | `CardNumberTransformation`(4-4-4-4), `CardExpiryTransformation`(2-2, `separator = '/'`) |
| `presentation/.../feature/payment/PaymentAddScreen.kt` | 46-47 | import 추가 |
| 〃 | 186, 194 | 카드번호: `value = cardDigits`(원시) + `visualTransformation = CardNumberTransformation` |
| 〃 | 200, 208 | 유효기간: `value = expiryDigits`(원시) + `visualTransformation = CardExpiryTransformation` |
| 〃 | (삭제) | `formatCardNumber` / `formatExpiry` 제거 — 표시 포맷을 유틸로 단일화 |

### 구분자 파라미터화 판단
**추가 작업 불필요.** `DigitGroupVisualTransformation` 은 처음부터 `separator: Char = '-'` 로 받게 설계했고,
매핑 규칙(구분자 개수만 세는 방식)이 구분자 종류와 무관하다. `'/'` 를 넘기는 것만으로 MM/YY 가 된다.

### 표시·payload 호환
- 카드번호 표시는 옛 `digits.chunked(4).joinToString("-")` 와 **전 길이에서 동일**함을 테스트로 고정했다.
  AMEX 15 자리도 종전대로 4-4-4-3(`3400-0000-0000-000`) — 표시 규칙을 바꾸지 않았다.
- 유효기간 표시도 옛 `take(2) + "/" + drop(2)` 와 동일(2 자리 이하면 슬래시 없음).
- 제출 payload: 이 화면의 CTA 는 `onAdded()` 로 인자가 없어 서버로 나가는 값 자체가 없다. 변경 없음.
- 검증 로직(`cardNumberValid` · `luhnValid` · `expiryValid`)은 원래부터 원시 숫자(`cardDigits`/`expiryDigits`)를
  보고 있었으므로 손대지 않았다. CVC 는 구분자가 없어 대상 아님.

### 검증 (누적)
- `./gradlew :presentation:testDebugUnitTest` → **12 tests, 0 failures** (기존 8 + 카드 4)
- `./gradlew :app:assembleDebug --console=plain` → **BUILD SUCCESSFUL**
- 에뮬레이터 미사용·install 미실행

### 실기 확인 경로 (리더용)
마이 → 30 결제 수단 → 「+ 결제 수단 추가하기」 → **31 결제 수단 추가**
→ 「신용 · 체크카드」 선택 → 카드번호 `4000123412341234` 순서 입력 → `4000-1234-1234-1234` 확인
→ 유효기간 `1228` 입력 → `12/28` 확인 (AMEX 는 `3400…` 으로 시작하면 15 자리 · CVC 4 자리로 전환)
