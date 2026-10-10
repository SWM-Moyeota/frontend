# 113 qa-verifier — 운영용 마이페이지 정비 (35 재구성 + 36 프로필 수정) 검증

대상: `feat/mypage-ops` 워킹트리(미커밋), 입력 `_workspace/00_input_mypage-ops.md` · 구현자 보고 `_workspace/112_compose-builder_mypage-ops.md`
환경: 에뮬레이터 `emulator-5554`(Pixel_6, API 37) · 로컬 백엔드 `localhost:8080`(H2, `TAXI_ENABLED=false`) · APK 는 `MOYEOTA_BASE_URL=http://10.0.2.2:8080/` 로 빌드
스크린샷: `/private/tmp/claude-501/-Users-sungyoon-Desktop-moyeota-frontend/1b791af6-8c40-4510-9b30-44b1700ea413/scratchpad/qa113/`

## 요약

| 구분 | 수 | 항목 |
|---|---|---|
| 통과 | 9 / 9 (검증 항목 A1–A3, B4–B9) | 아래 표 |
| 실패 | 0 | — |
| 미검증 | 3 (부분) | 메일 **본문/제목** 실물 확인, SupportLinks **부분 null** 변형, 36 저장 직후 **즉시 재진입** 경합 |

**차단 결함 없음.** 코드는 수정하지 않았다(SupportLinks 임시 변경·복원만 예외, §복원 증빙).

| # | 항목 | 결과 | 근거 |
|---|---|---|---|
| A1 | 빌드 + 유닛 테스트 | 통과 | `BUILD SUCCESSFUL`, 경고 0. `data` **223건**, `presentation` **101건**, 0 실패/0 에러/0 스킵 (`--rerun` 으로 강제 재실행) |
| A2 | 경계면 교차 비교 | 통과 | §1. PATCH 본문 실측 `{"nickname":"qaName1"}` (22B), 닉네임 규칙 서버와 동일 |
| A3 | `MainNavGraph` 배선 | 통과 | 35 → 36 → 저장 → `profileViewModel.refresh()` → `back()`. `ModeDebugSection` 35 맨 아래 유지 |
| B4 | 35 구성 | 통과 | `04_mypage_35.png` |
| B5 | 알림 설정 → OS 설정 | 통과 | `mCurrentFocus=com.android.settings/.Settings$AppNotificationSettingsActivity` (대상 앱 = 모여타). `05_notification_settings.png` |
| B6 | 36 프리필·저장·전파 | 통과 | `06`·`08`·`09`·`10`. 35 카드·14 인사말·서버 `name` 모두 새 값 |
| B7 | 36 에러 경로 | 통과 | 1자·중복·11자 모두 차단. `07a`·`07b`·`07c`. 추가로 **저장 시 409 경합**까지 확인(`11`) |
| B8 | SupportLinks 지원 그룹 | 통과 | `12`·`13`·`14_contact_gmail`·`15`. 크래시 0 |
| B9 | 회귀(디버그 칩 · 로그아웃) | 통과 | `17`·`18`·`19`·`20`·`21` |

---

## 1. 경계면 교차 비교 (A2)

### 1-1. `AuthRepository` 계약 ↔ 36 Route

| 항목 | 생산자 | 소비자 | 결과 |
|---|---|---|---|
| `updateProfile(nickname: String?, imageUrl: String?)` | `domain/.../AuthRepository.kt:70` / 구현 `data/.../RemoteAuthRepository.kt:120` | `ProfileEditRoute.kt:169` `updateProfile(nickname = …, imageUrl = null)` | 일치 |
| `isNicknameTaken(nickname): Boolean` | `AuthRepository.kt:57` / 구현 `RemoteAuthRepository.kt:116` | `ProfileEditRoute.kt:142` | 일치 |
| `getMyProfile(): UserProfileInfo` | `AuthRepository.kt:109` | `UserProfile.kt:96` (`refresh()` 경유) | 일치 |
| 예외 타입 | 전부 `AuthException`(`authCall` 이 `HttpException`/`IOException` 을 흡수, `AuthMappers.kt:113`) | `ProfileEditRoute.kt:147,172,203` 이 `AuthException` 으로 분기, 나머지는 `catch (e: Exception)` 폴백 | 일치 — data 계층에서 HTTP 가 새지 않음 |

서버 코드 매핑도 양쪽이 맞는다: 409 `USER108` → `AuthError.NICKNAME_DUPLICATED`(`AuthMappers.kt:79`) → 36 이 `check = Taken` 으로 바꿔 필드에 표시. 400 `USER107`/`INVALID_REQUEST(message="nickname: …")` → `isNicknameFormatError()`(`ProfileEditRoute.kt:203`) 가 한 판단으로 모은다.

### 1-2. `PATCH /users/me` 본문 (DTO encodeDefaults)

정적: `NetworkModule.kt:18` 의 `Json { ignoreUnknownKeys = true }` 는 `encodeDefaults` 기본값 `false` 를 쓴다. `UpdateProfileRequest(nickname: String? = null, imageUrl: String? = null)`(`UserDtos.kt:36`)에서 `imageUrl = null` 은 **기본값과 같으므로 직렬화에서 빠진다**.

실측(logcat, OkHttp):
```
--> PATCH http://10.0.2.2:8080/api/v1/users/me
{"nickname":"qaName1"}
--> END PATCH (22-byte body)
<-- 204 http://10.0.2.2:8080/api/v1/users/me (1032ms)
```
`imageUrl` 키 없음 확인. 서버 계약(`UpdateProfileRequest`: "생략하면 유지")과 정확히 맞는다.

### 1-3. 닉네임 규칙 2~10자 ↔ 서버

| 층 | 규칙 | 출처 |
|---|---|---|
| 앱 도메인 | `^[가-힣a-zA-Z0-9]{2,10}$` + trim | `domain/.../Auth.kt:56,63` |
| 앱 화면 | 위 + 금칙어(모여타·관리자·admin·욕설) | `SignupFieldSupport.kt:47-75` |
| 서버 Bean Validation | `@Size(min=2,max=10)` | `backend/.../UpdateProfileRequest.java:7` |
| 서버 도메인 VO | `^[가-힣a-zA-Z0-9]{2,10}$` + `strip()` | `backend/.../Nickname.java:11` |

**일치.** 앱 쪽이 금칙어만큼 더 엄격하며(의도된 설계), 길이·문자 집합·공백 처리는 동일. 입력 단계 `take(10)`(`ProfileEditRoute.kt:131`)도 서버 상한과 같다.

### 1-4. 네비게이션

`Routes.PROFILE_EDIT = "mypage/profile-edit"`(`Routes.kt:68`) → `composable(Routes.PROFILE_EDIT)` 등록(`MainNavGraph.kt:767`) → 35 의 `onEditProfile = { navController.navigate(Routes.PROFILE_EDIT) }`(`MainNavGraph.kt:746`). 등록 안 된 라우트로 가는 `navigate` 없음.

`profileViewModel` 은 NavHost **바깥**(`MainNavGraph.kt:136`)에 있어 35·36·14 가 같은 인스턴스를 본다 → 저장 후 `refresh()` 한 번으로 세 화면이 함께 갱신된다. `ModeDebugSection` 은 `MyPageScreen.kt:208-211`, 여전히 로그아웃 줄 **아래**가 맞다.

---

## 2. 실기 검증 상세

### B4 — 35 구성 (`04_mypage_35.png`)
`마이페이지` 제목 → 프로필 카드(`모드QA` + `닉네임 바꾸기` + 꺾쇠 ›) → `설정` 카드(`알림 설정` + ↗) → `로그아웃` / 우측 `v1.0` → `개발자 옵션 · 디버그 빌드에만 보여요` + 모드 칩 3개. **지원 카드는 안 보임 = 정상**(SupportLinks 셋 다 null). 레이아웃 깨짐·잘림 없음.

### B5 — 알림 설정
탭 → `com.android.settings/com.android.settings.Settings$AppNotificationSettingsActivity`, 타이틀 `모여타`. 뒤로가기로 35 복귀 확인(`mCurrentFocus=com.moyeota/...MainActivity`). API 26+ 경로가 이 이미지에서 정상 동작.

### B6 — 36 프로필 수정 (성공 경로)
1. 프로필 카드 탭 → 36. 입력란 `모드QA` 프리필, `4 / 10` 카운터, 헬퍼 `지금 쓰고 있는 닉네임이에요`, 「저장」 **비활성**(`enabled="false"` on container View). (`06_profile_edit_36.png`)
2. `qaName1` 입력 → 0.5초 뒤 자동 중복 확인 → `POST /api/v1/auth/nickname/check {"nickname":"qaName1"}` → 200 → `사용 가능한 닉네임이에요`(초록) + `탑승 상대에게는 「qaName1」 으로 보여요`, 「저장」 활성. (`08_ready_to_save.png`)
3. 「저장」 → `PATCH /users/me` 204 → 즉시 `GET /api/v1/local/users/info` 200(= `refresh()`) → 35 복귀.
4. **35 프로필 카드 = `qaName1`** (`09_after_save_35.png`), **14 홈 인사말 = `qaName1님, 반가워요`** (`10_home_greeting.png`).
5. 백엔드 교차: `curl GET /api/v1/local/users/info` → `{"uuid":"01a12557-…","name":"qaName1"}`.
6. 재진입 시 입력란이 새 값 `qaName1` 로 프리필되고 「저장」 비활성 — 비교 기준도 함께 갱신됨.

### B7 — 36 에러 경로
| 입력 | 표시 | 「저장」 |
|---|---|---|
| `q`(1자) | `닉네임은 2~10자로 입력해 주세요` | 비활성 (`enabled="false"`) |
| `ㅁ`(한글 자모 — IME 미전환 상태) | `한글·영문·숫자만 쓸 수 있어요 (공백·특수문자·이모지 불가)` | 비활성 |
| `qaTaken1`(타 계정 선점) | `이미 사용 중인 닉네임이에요` | 비활성 |
| `qaTaken1` + `abc` (11자 시도) | `qaTaken1ab`, `10 / 10` — **11번째 글자가 들어가지 않음** | — |

**추가 확인 — 저장 시 409 경합(설계 결정 9).** `qaRace2` 입력 → `사용 가능` 판정 → 그 사이 curl 로 다른 계정이 `qaRace2` 선점 → 「저장」 탭 → `409 {"code":"USER108"}` → 화면은 36 에 머물며 **필드가 `이미 사용 중인 닉네임이에요` 로 바뀌고 「저장」 이 함께 꺼짐**, 버튼 아래 배너 중복 없음. 설계대로 동작. (`11_save_409.png`)

### B8 — SupportLinks 임시 값
`TERMS_URL="https://example.com/terms"`, `PRIVACY_URL="https://example.com/privacy"`, `CONTACT_EMAIL="qa@example.com"` 로 바꿔 빌드·설치.

- `지원` 카드로 `문의하기` / `이용약관` / `개인정보 처리방침` 3행이 묶여 보이고, 각 행 오른쪽에 ↗. (`12_mypage_support.png`)
- `이용약관` 탭 → `com.android.chrome/...FirstRunActivity`(Chrome 최초 실행 화면 — 이 이미지에서 Chrome 을 쓴 적이 없어서다. ACTION_VIEW 가 브라우저로 해석된 것은 확정). (`13_terms_browser.png`)
- `개인정보 처리방침` 탭 → 같은 Chrome.
- `문의하기` 탭 → Gmail. logcat: `START ... act=android.intent.action.SENDTO dat=mailto:… cmp=com.google.android.gm/.ComposeActivityGmailExternal (has extras)`. **크래시 없음**(`logcat | grep "E AndroidRuntime"` 0건).
- **메일 앱 없음 폴백** — `pm disable-user com.google.android.gm` 후 탭 → 토스트 `메일 앱이 없어요. qa@example.com 로 보내 주세요`, **크래시 없음**. (`15_contact_toast_fallback.png`) 구현자가 "확인 못 함"으로 남긴 불확실 ②가 해소됐다. 이후 Gmail 재활성화 확인(`cmd package list packages -e | grep com.google.android.gm` 히트).

### B9 — 회귀
- 모드 칩: `택시 강제` 탭 → 칩이 파랗게 선택(`17_taxi_forced.png`), `동승 강제` → `서버 값` 순환 정상(`18_chip_server.png`). `서버 모드: 동승` 표기 유지.
- 로그아웃: 확인 다이얼로그 `로그아웃할까요? / 다시 이용하려면 아이디로 로그인해야 해요` → `로그아웃` → **04a 로그인 화면** 전이(`19`·`20`). 재로그인 후 홈 복귀 정상(`21`).
- 세션 전체 `E AndroidRuntime` / `FATAL EXCEPTION` **0건**.

---

## 3. 미검증 항목 (실패 아님)

1. **문의 메일의 제목·본문 실물** — 이 에뮬레이터 이미지의 Gmail 에 계정이 없어 `WelcomeTourActivity` 로 막혔다. Intent 가 `(has extras)` 로 발사된 것과 `supportMailBody()` 유닛 테스트 3건(`SupportMailBodyTest.kt`)은 통과했으나, **작성 화면에 받는사람/제목 `[모여타 문의]`/본문 3줄이 실제로 채워지는지는 확인하지 못했다.** 계정이 있는 기기에서 1회 확인 권장.
2. **SupportLinks 값 하나만 null** — 빌드 사이클이 한 번 더 들어 생략. 정적으로는 `MyPageScreen.kt:103-113` 의 `buildList` 가 세 값을 **서로 독립적인 `?.let`** 으로 추가하고 `MyPageMenuCard` 가 `items.isEmpty()` 에 early return(`:261`) 하므로 "그 행만 빠진다"가 구조적으로 보장된다. 양 극단(셋 다 null → 카드 숨김, 셋 다 설정 → 3행)은 실기로 확인했다.
3. **36 저장 직후 즉시 재진입 경합**(구현자 §남은 불확실) — 로컬 백엔드의 `GET /local/users/info` 가 **4ms** 에 끝나 adb 왕복(수백 ms)으로는 재현 불가. 아래 §4-O2 에 이론적 영향 범위를 적어 둔다.

---

## 4. 결함·관찰 (전부 비차단)

### O1 — `/local/users/info` 의 `name` 은 닉네임이 아닐 수 있고, 그때 36 이 열자마자 형식 오류를 띄운다 (낮음, 현재 도달 불가)

- 파일: `presentation/.../feature/mypage/ProfileEditRoute.kt:115`(`prefill`) · `:44`(`nicknameEditError`), 서버 `backend/.../LocalUserService.java:73-74`
- 메커니즘: 서버는 닉네임이 없으면 **마스킹한 실명**으로 폴백한다 — `Masking.name("김성윤") = "김*윤"`. 그 값이 `currentNickname` 이 되어 입력란에 프리필되는데, `*` 는 `NicknamePolicy` 의 `^[가-힣a-zA-Z0-9]+$` 를 통과하지 못한다. 결과적으로 36 이 **손대기도 전에** `한글·영문·숫자만 쓸 수 있어요` 를 빨갛게 띄운다. 사용자에게는 "내 현재 이름이 잘못됐다"로 읽힌다.
- 도달성: 앱 가입 경로는 닉네임이 필수라(`UserRegisterRequest.java:26` `@NotBlank`) **현재는 도달하지 않는다.** 시드 데이터·관리자 생성·향후 소셜 로그인이 생기면 열린다.
- 제안(api-integrator / compose-builder): `UserProfileInfo` 에 "이 name 이 닉네임인지"를 구분할 필드가 없다. 서버가 `nickname`/`maskedName` 을 나눠 주는 게 근본 해법(§백엔드 요청에 추가 권장). 앱만으로 막으려면 `prefill` 에서 `NicknamePolicy.isValid(name)` 이 false 면 `currentNickname = null`, `nickname = ""` 으로 두고 플레이스홀더만 보이게 하는 1줄 가드로 충분하다.

### O2 — 저장 직후 즉시 재진입 시 비교 기준이 옛 이름으로 굳을 수 있다 (낮음, 재현 불가)

- 파일: `ProfileEditRoute.kt:115-124`(`seeded` 는 단 한 번만 먹는다) + `:227`(`LaunchedEffect(userName)`)
- 메커니즘: `refresh()` 가 끝나기 전에 36 에 다시 들어가면 `prefill(옛 이름)` 이 먼저 먹고, 이후 새 이름이 도착해도 `seeded == true` 라 `currentNickname` 이 **옛 이름인 채로 남는다.** 구현자 보고는 "입력이 옛 이름으로 채워질 수 있다"까지만 적었는데, 그 상태에서 사용자가 방금 저장한 **자기 새 닉네임**을 입력하면 `isNicknameUnchanged` 가 false → 서버에 묻는다 → 자기 것이므로 `exists=true` → **`이미 사용 중인 닉네임이에요`** 가 뜬다. 잘못 저장되지는 않지만(409 로 막힌다) 문구가 거짓이다.
- 재현: 로컬 백엔드(4ms 응답)로는 불가. 느린 네트워크에서 저장 직후 <1s 안에 재진입.
- 제안: `prefill` 의 `seeded` 가드를 "입력을 덮어쓰지 않는다"로만 좁히고 `currentNickname` 은 매번 최신 값으로 갱신하면 된다 — 비교 기준을 한 번만 세울 이유는 없다(입력 보존과 별개 관심사).

### O3 — 비활성 「저장」 버튼이 배경과 같은 색이라 버튼 형태가 보이지 않는다 (낮음, 이번 변경의 결함 아님)

- 파일: `core/designsystem/.../Buttons.kt:37` `disabledContainerColor = MoyeotaColor.SurfaceSoft` vs `ProfileEditScreen.kt:72` `background(MoyeotaColor.SurfaceSoft)` — 둘 다 `#F4F6FA`.
- 결과: 36 의 비활성 「저장」 은 알약 모양이 사라지고 회색 글자만 뜬다(`06_profile_edit_36.png` 하단). 글자색 `TextAsh #D1D5DB` 대 `#F4F6FA` 는 대비 약 1.3:1 로 WCAG 미달이다.
- **이번 변경이 만든 문제가 아니다** — 10 프로필 만들기(`ProfileSetupScreen`), 04a 로그인(`LoginFormScreen`) 등 `SurfaceSoft` 배경을 쓰는 9개 화면이 모두 같다. 36 은 기존 관례를 그대로 따랐다. 디자인 시스템 차원에서 `disabledContainerColor` 를 한 단계 어둡게 잡는 별건으로 다루는 게 맞다.

### O4 — `SupportLinks.kt` 는 **untracked** 라 `git checkout --` 으로 되돌릴 수 없다 (프로세스 메모)

지시받은 `git checkout -- presentation/.../SupportLinks.kt` 는 `error: pathspec … did not match any file(s) known to git` 로 실패한다. feat/mypage-ops 작업분 전체가 미커밋이라 이 파일은 `??` 상태다. 아래처럼 **값만 수동 복원**했고, 그 증빙을 §5 에 남긴다. 같은 실수가 반복되지 않도록, 앞으로 미커밋 신규 파일을 임시 수정할 때는 복원 전에 사본을 떠 두는 편이 안전하다.

---

## 5. SupportLinks 복원 증빙

```
$ git checkout -- presentation/src/main/kotlin/com/moyeota/presentation/core/SupportLinks.kt
error: pathspec '…/SupportLinks.kt' did not match any file(s) known to git     # untracked (??)
```
→ `sed` 로 세 값을 원래의 `null` 로 수동 복원.

```
$ grep -n "val " presentation/src/main/kotlin/com/moyeota/presentation/core/SupportLinks.kt
20:    val TERMS_URL: String? = null
23:    val PRIVACY_URL: String? = null
26:    val CONTACT_EMAIL: String? = null

$ grep -c "example.com" presentation/src/main/kotlin/com/moyeota/presentation/core/SupportLinks.kt
0

$ md5 -q .../SupportLinks.kt
3a8a94f7415718bb704aa8bcd7f8fc15
```

`git diff --stat` (추적 파일만 — SupportLinks 는 untracked 라 여기 나올 수 없다):
```
 .DS_Store                                          | Bin 6148 -> 8196 bytes
 docs/BACKEND-GAP-CLEANUP.md                        |   7 +-
 docs/MODE-ROUTES.md                                |   3 +-
 .../com/moyeota/presentation/core/MainNavGraph.kt  |  18 ++-
 .../kotlin/com/moyeota/presentation/core/Routes.kt |   1 +
 .../com/moyeota/presentation/core/UserProfile.kt   |  40 ++++-
 .../presentation/feature/mypage/MyPageScreen.kt    | 177 ++++++++++++++++++---
 7 files changed, 216 insertions(+), 30 deletions(-)
```
`git status --porcelain` 이 **세션 시작 스냅샷과 완전히 동일**(M 7건 / ?? 10건, 신규 항목 0). `.DS_Store` 는 시작 시점부터 M 이었다. 브랜치·커밋·stash 변경 없음.

**설치된 APK 상태:** 복원된 소스로 다시 빌드·설치 완료(`adb install -r -t`, 20:32). 실기에서 「지원」 카드가 다시 사라진 것을 확인(`16_restored_mypage.png`) — 즉 현재 기기에 깔린 APK 는 **운영 기본값(SupportLinks 전부 null)** 이고 `MOYEOTA_BASE_URL=http://10.0.2.2:8080/` 다.

---

## 6. 검증 스택에 남긴 변화 (되돌릴 수 없는 것)

로컬 백엔드는 H2 인메모리라 재시작하면 모두 사라진다. 그래도 적어 둔다.

| 변화 | 상태 |
|---|---|
| `modeqa1` 닉네임 `모드QA` → `qaName1` | **`모드QA` 로 복원함**(curl PATCH 204 + 앱 재실행 후 35 표기 확인, `22_final_mypage.png`) |
| 신규 계정 `qadup1`(닉 `qaTaken1`) | 남아 있음 — 중복 검증용 |
| 신규 계정 `qarace1`(닉 `qaRace1`) · `qarace2`(닉 `qaRace2`) | 남아 있음 — 409 경합 검증용 |
| Gmail 비활성화 | **재활성화함**(`pm enable` 후 `list packages -e` 히트 확인) |
| IME 서브타입 한국어 → 영문 | 영문 상태. `adb shell input keyevent 204` 로 되돌릴 수 있다 |

에뮬레이터·백엔드 프로세스는 죽이지 않았다. 둘 다 그대로 떠 있다.

---

## 7. 담당 에이전트 요청

- **compose-builder**: O1 가드 1줄(`prefill` 에서 형식 불만족 이름은 기준으로 삼지 않기), O2 의 `currentNickname` 갱신 분리. 둘 다 낮은 우선순위 — 지금 머지를 막을 이유는 없다.
- **api-integrator / 백엔드 이슈**: `/local/users/info` 가 "닉네임"과 "마스킹 실명"을 구분해 주면 O1 이 근본적으로 닫힌다. 00 입력의 백엔드 요청 목록(`imageUrl·phone 확장`)에 이 한 줄을 붙여 두는 게 좋다.
- **리더**: 차단 결함 없음. 스토어 제출 전에 `SupportLinks` 3개 주소를 채우는 것이 전제라는 구현자 지적에 동의한다 — 그 값이 들어오면 B8 의 메일 본문 실물 확인(미검증 1)을 함께 끝내면 된다.
