# 27. 가입 플로우 단순화 — 계정 유형 3택 제거, 09 직행 (compose-builder)

작업일: 2026-09-02 / 브랜치: `feature/naver-map` / 선행: `23_compose-builder_auth.md`, `26_compose-builder_d8.md`
빌드: `./gradlew :presentation:compileDebugKotlin :app:assembleDebug --console=plain` → **BUILD SUCCESSFUL**
실기: **emulator-5554**(`install -r`, 데이터 보존) — 신규 계정 `mergeqa902` 가입 → 자동 로그인 → 홈 진입 통과. 스크린샷 `_workspace/screenshots/signup2_01~16_*.png`
후속: 리더 지시로 09·10 의 거짓 카피 2건까지 정정 후 재빌드·재검증 (§5, §7-1)
범위: `presentation/` 만. `data/`·`domain/` 무수정.

---

## 1. 새 가입 플로우 (4단계)

```
04 시작 「이메일로 시작하기」  ┐
04a 로그인 「가입하기」        ┘→ 09 본인 인증(1/4) → 10 프로필(2/4) → 11 안심 설정(3/4)
                                → 12 매너 서약(4/4, 여기서 POST /users + 자동 로그인) → 13 완료 → 14 홈
```

**빠진 화면**: 05 계정 유형 · 06 학교 이메일 · 07 인증 코드 · 08 재직 인증.

근거는 요청받은 그대로다 — `POST /api/v1/users` 는 `loginId·password·name·birthDate·phoneNumber·gender·email` 7개만 받고 계정 유형 개념이 없다. 05 에서 고른 값은 **화면 분기 외에는 아무 데도 쓰이지 않았고**, 06/08 이 받던 이메일도 결국 10 의 이메일 필드와 같은 값이었다. 즉 세 화면이 서버로 가지 않는 값을 묻고 있었다.

## 2. 화면을 지우지 않고 배선만 끊은 방법

파일 4개(`AccountTypeScreen`·`SchoolEmailScreen`·`EmailCodeScreen`·`WorkVerifyScreen`)는 **그대로 남겼다.** 인증을 마이페이지로 옮길 때 재사용 대상이다.

- `MainNavGraph` 에서 해당 `composable(...)` 4개와 import 를 제거 → 그래프에서 도달 불가
- `Routes.kt` 의 상수 4개는 **남기되 `(미연결)` 표시 + B' 절로 분리**하고 남긴 이유를 주석에 적었다. 상수를 지우면 화면 파일이 "어디에 붙던 것인지" 단서가 사라진다
- 각 화면 파일 상단에 「⚠️ 현재 어떤 그래프에도 등록되어 있지 않다」 배너 주석 — 다음 사람이 앱을 뒤져보다 "이 화면 왜 안 나오지"를 다시 조사하지 않도록
- 죽은 상태 제거: `MainNavGraph` 의 `var schoolEmail`(06→07 전달용) 삭제

`SignupDraft` 는 **필드를 하나도 지우지 않았다** — 7개 필드가 곧 서버 요청 7개 필드라 불필요한 것이 없다. 수집 화면 표(KDoc)만 새 플로우로 고쳤다.

## 3. 이메일 = 10 에서 직접 입력

06/08 프리필 경로가 사라졌으므로 `ProfileSetupScreen` 의 `initialEmail` 파라미터를 제거하고 빈 값에서 시작한다. 검증은 그대로(`SimpleEmailRegex` + `AuthPolicy.EMAIL_MAX_LENGTH`, 최종 판정은 서버).

- placeholder `moyeota@pusan.ac.kr` → `moyeota@example.com` (학교 메일만 받는 화면이 아니게 됐다)
- helperText 「앞에서 인증한 메일 주소예요」 → 「비밀번호를 잊었을 때 쓰는 주소예요」. 앞 문구는 인증 절차가 사라진 지금 **거짓말**이 된다

## 4. 진행 표시 재계산 (n/5 → n/4)

| 화면 | 이전 | 이후 |
|---|---|---|
| 06·07·08 | 1 / 5 | — (경로에서 빠짐, 파일 값은 손대지 않음) |
| 09 본인 인증 | 2 / 5 | **1 / 4** |
| 10 프로필 | 3 / 5 | **2 / 4** |
| 11 안심 설정 | 4 / 5 | **3 / 4** |
| 12 매너 서약 | 5 / 5 | **4 / 4** |

숫자와 함께 진행 바 비율(`1f/4f`, `2f/4f`, `3f/4f`, `1f`)도 같이 맞췄다. 06~08 의 `1 / 5` 는 그대로 뒀다 — 지금 도달 불가이고, 마이페이지로 옮길 때는 가입 단계가 아니라 단독 플로우가 되므로 그때 다시 매기는 편이 맞다.

## 5. 카피 정정 — 통합으로 거짓이 된 3곳

가입 경로에서 인증 화면들이 빠지면서 **사실과 어긋나게 된 문구**만 골라 고쳤다. 312명 통계·신뢰 불릿 등 나머지 데모 카피는 지시대로 그대로다.

| 위치 | 이전 | 이후 | 왜 |
|---|---|---|---|
| 04 시작 화면 부제 (`LoginScreen.kt`) | 「부산대 학생만 · 학교 이메일 인증으로 확인해요」 | **「아이디로 간편하게 가입해요」** | 학교 이메일 인증이 가입 필수 경로에서 빠졌다 |
| 09 CTA (`IdentityVerifyScreen.kt`) | 「인증 문자 받기」 | **「다음」** | 백엔드에 본인인증(SMS) API 가 없다 — 문자를 보내지 않고 곧장 10 으로 넘어간다 |
| 09 하단 안내문 | 「인증 문자는 1회만 발송되고, 3분 안에 도착해요」 | **삭제** | 발송 자체가 없으므로 조건을 설명할 대상이 없다 |
| 10 안내 카드 (`ProfileSetupScreen.kt`) | 「얼굴 사진 없이도 **학교 인증 배지**와 매너 기록으로…」 | **「얼굴 사진 없이도 매너 기록으로 서로를 확인할 수 있어요」** | 가입 중 발급되는 배지가 없다. 카드의 논지(사진 없이도 신뢰 가능)는 매너 기록만으로 성립하므로 문장을 지우지 않고 근거만 줄였다 |

09 은 안내문과 함께 `TextAlign` import·`FooterGray` 색상 상수도 쓰이지 않게 되어 같이 지웠다. 콜백 이름 `onRequestCode` 는 호출부 4곳이 걸려 있어 그대로 두고, "SMS 인증을 보내던 시절의 잔재"라는 근거를 KDoc 에 남겼다.

---

## 6. 변경 파일 (전부 `presentation/src/main/kotlin/com/moyeota/presentation/`)

| 파일 | 변경 |
|---|---|
| `core/MainNavGraph.kt` | 05~08 composable·import 제거, `schoolEmail` 상태 삭제, 04/04a → `IDENTITY_VERIFY`, `initialEmail` 전달 제거 |
| `core/Routes.kt` | B/B'/C 절 재구성 — 05~08 을 「미연결」로 분리 + 근거 주석 |
| `feature/auth/IdentityVerifyScreen.kt` | 1/4, 진행 바 `1f/4f`, 헤더 주석(진입 경로), **CTA 「다음」 + 하단 안내문 삭제** + 미사용 `TextAlign` import·`FooterGray` 제거 |
| `feature/auth/ProfileSetupScreen.kt` | 2/4, 진행 바 `2f/4f`, `initialEmail` 파라미터 제거, 이메일 placeholder·helper 문구, **안내 카드 배지 언급 제거**, Preview |
| `feature/auth/SafetySettingsScreen.kt` | 3/4, 진행 바 `3f/4f` |
| `feature/auth/MannerPledgeScreen.kt` | 4/4 |
| `feature/auth/LoginScreen.kt` | 부제 정정 + KDoc(`onEmailStart` → 09) |
| `feature/auth/SignupDraft.kt` | KDoc 수집 화면 표 갱신 (필드 변경 없음) |
| `feature/auth/{AccountType,SchoolEmail,EmailCode,WorkVerify}Screen.kt` | 상단 「미연결」 주석만 |

색상 하드코딩 추가 없음. Routes 상수 추가·삭제 없음(주석·배치만 변경).

---

## 7. 실기 검증 (emulator-5554, 서버 localhost:8080 유지)

| # | 항목 | 결과 | 스크린샷 |
|---|---|---|---|
| 1 | 온보딩 건너뛰기 → 04 시작 화면 부제 | ✅ 「아이디로 간편하게 가입해요」 | `signup2_02_start.png` |
| 2 | 04a 로그인 → 「가입하기」 | ✅ **05 를 거치지 않고 09 로 직행**, 우상단 `1 / 4` | `signup2_03`, `signup2_04_step1_identity.png` |
| 3 | 09 입력 (SKT·010-3344-0902·`MergeQa`·1998-03-12·남성·약관동의) | ✅ CTA 활성 → 10 | `signup2_05`, `signup2_06` |
| 4 | 10 프로필 `2 / 4` · 이메일 **빈 필드 직접 입력** | ✅ placeholder `moyeota@example.com`, helper 「비밀번호를 잊었을 때 쓰는 주소예요」 | `signup2_07`, `signup2_08_profile_email.png` |
| 5 | 11 안심 설정 `3 / 4` | ✅ | `signup2_10_step3_safety.png` |
| 6 | 12 매너 서약 `4 / 4` (진행 바 100%) | ✅ | `signup2_11_step4_pledge.png` |
| 7 | 「동의하고 가입 완료」 → 13 가입 완료 | ✅ 가입 + 자동 로그인 성공 | `signup2_12_complete.png` |
| 8 | 13 → 홈 진입 (하단탭 홈) | ✅ 「김OO님, 좋은 저녁이에요」 + 최근 목적지 로드 = 인증된 요청 성공 | `signup2_13`, `signup2_14_home_tab.png` |
| + | 서버에 계정이 실제로 생성됐는지 교차 확인 | ✅ `POST /api/v1/users/login` (`mergeqa902`) → **200** | — |

### 7-1. 카피 정정 후 재검증 (2차 빌드·재설치)

35 마이 → 로그아웃 → 04a 「가입하기」로 09 에 다시 진입해 확인했다.

| # | 항목 | 결과 | 스크린샷 |
|---|---|---|---|
| 9 | 09 CTA 문구 | ✅ **「다음」**, 하단 「인증 문자는 1회만…」 **사라짐** (빈 상태·입력 완료 상태 모두) | `signup2_15_step1_cta.png`, `signup2_15_step1_cta_next.png` |
| 10 | 10 안내 카드 | ✅ 「얼굴 사진 없이도 **매너 기록으로** 서로를 확인할 수 있어요」 — 배지 언급 없음 | `signup2_16_step2_notice.png` |

안내문이 빠진 만큼 09 의 CTA 가 화면 하단에 더 붙지만 `NavigationBarSpacer()` 가 그대로라 제스처 바와 겹치지 않는다(`signup2_15_step1_cta_next.png` 로 확인). 재검증은 가입을 완료하지 않고 09·10 확인 후 뒤로 나왔다 — 계정을 하나 더 만들지 않기 위해서다.

테스트 계정: `mergeqa902 / Passw0rd!` (이메일 `mergeqa902@example.com`, 이름 `MergeQa`).
한글 IME 부재로 이름은 영문 — 23 보고서의 이름 규칙 확장(`^[가-힣a-zA-Z]{2,20}$`) 덕에 통과한다.
서버는 죽이지 않았고 파티 데이터도 건드리지 않았다(가입·로그인만 발생). 앱은 `install -r`.

---

## 8. 남긴 관찰

### ✅ (해결) 09 CTA · 10 배지 언급 — §5 에서 정정 완료

초안에서 「범위 밖」으로 남겼던 2건은 리더 지시로 이번 작업에 포함해 처리했다. 근거·결과는 §5, 검증은 §7-1.

### 🟡 35 마이페이지의 「학생 인증 완료」 배지는 아직 데모 값이다

로그아웃 경로를 타며 확인했다 — 마이 상단이 「부산대학교 · 2026년 3월 인증」 + 「학생 인증 완료」로 **하드코딩**되어 있다. 가입에서 학교 인증이 빠진 지금은 방금 가입한 계정에도 이 배지가 붙는다(매너 점수 98%·탑승 42회와 같은 성격의 와이어프레임 더미). 인증을 마이페이지로 옮길 때 이 카드가 실제 인증 상태를 반영해야 하므로, 그 작업의 시작점으로 함께 다루는 편이 맞다.

### 🟢 05~08 재사용 시 필요한 것

마이페이지로 옮길 때는 (a) `MainNavGraph` 에 마이 탭 하위로 재등록, (b) 진행 표시 `1 / 5` 제거 또는 재계산, (c) 인증 결과를 저장할 서버 API(현재 없음)가 필요하다. 지금은 이메일만 받아 `SignupDraft` 에 넣던 구조라 **인증 자체가 서버에 기록되지 않는다** — 옮기기 전에 백엔드 엔드포인트가 먼저 있어야 한다.
