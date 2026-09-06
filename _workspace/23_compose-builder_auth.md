# 23. 인증 화면 연결 + 세션 라우팅 (presentation/) — compose-builder

작업일: 2026-09-02 / 브랜치: `feature/naver-map` / 선행: `21_api-integrator_auth.md`, `22_api-integrator_authpaths.md`
검증: `./gradlew :presentation:compileDebugKotlin :app:assembleDebug --console=plain` → **BUILD SUCCESSFUL**
실기: **emulator-5554 (Pixel_6, 콜드부트)** — 가입·로그인·재시작·로그아웃·오류·합류 전 항목 통과. 스크린샷 `_workspace/screenshots/auth_01~30_*.png`

---

## 1. 컴파일 복구 (22 보고서 §3의 9곳 / 7파일)

전부 새 시그니처로 교체했다. 인자를 지우는 김에 **ViewModel 에서 완전히 미사용이 된 `userSession` 도 함께 걷어냈다**(생성자·`factory`·Composable 파라미터·`MainNavGraph` 호출부까지 6개). 쓰지 않는 의존성을 남겨두면 "이 화면은 아직 memberId 를 쓴다"고 읽히기 때문이다.

| 파일 | 고친 호출 | userSession 제거 |
|---|---|---|
| `feature/explore/JoinConfirmRoute.kt` | `joinParty(partyId)` | ✅ |
| `feature/matching/MatchWaitingRoute.kt` | `leaveParty(partyId)` | ✅ |
| `feature/matching/RideDetailRoute.kt` | `leaveParty(partyId)` | ❌ (isHost 판정에 계속 필요) |
| `feature/matching/DispatchStatusRoute.kt` | `getDriverLocation(partyId)` | ✅ |
| `feature/home/HomeRoute.kt` | `getFavoritePlaces()` | ✅ |
| `feature/home/DestinationRoute.kt` | `getFavoritePlaces()` · `addFavoritePlace(place)` | ✅ |
| `feature/chat/EmergencyRoute.kt` | `NewReport(partyId = partyId)` | ✅ |

`NewReport` 는 **이름 붙인 인자로** 호출했다. 첫 필드가 `reporterId` → `partyId` 로 바뀐 변경이라 위치 인자로 두면 타입이 같아(`Long`) 컴파일은 통과하고 값만 조용히 어긋난다.

---

## 2. 가입 플로우 ↔ API 필드 매핑 (결정 근거)

서버 `POST /api/v1/users` 가 요구하는 7개 필드 중 **기존 와이어프레임이 묻던 것은 email 하나뿐**이었다. 화면을 새로 만들지 않고 기존 화면에 필드를 덧붙이는 쪽을 택했다 — 35화면 구조와 진행 표시(n/5)를 유지하기 위해서다.

| API 필드 | 수집 화면 | 조치 |
|---|---|---|
| `email` | 06 학교 이메일 / 08 직장 인증(회사 메일) | 기존 입력을 초안에 저장. **일반 유형은 회사 메일을 안 받으므로** 10 에서 직접 입력 |
| `name` | 09 본인 인증 | 기존 「이름」 재사용 |
| `phoneNumber` | 09 본인 인증 | 기존 「휴대폰 번호」 재사용 |
| `birthDate` | 09 본인 인증 | **필드 추가** (YYYYMMDD → 하이픈 자동 삽입, 실재하는 날짜인지 검증) |
| `gender` | 09 본인 인증 | **필드 추가** (남성/여성 세그먼트) |
| `loginId` | 10 프로필 만들기 | **「로그인 정보」 절 추가** |
| `password` | 10 프로필 만들기 | **「로그인 정보」 절 추가** (보기/숨기기 토글) |

값은 `feature/auth/SignupDraft.kt` 한 곳에 모으고 `MainNavGraph` 가 들고 다닌다. **제출은 12 매너 서약의 「동의하고 가입 완료」 단 한 곳**에서 일어난다(`MannerPledgeRoute`). 12·13 화면 흐름은 그대로 유지된다.

### 왜 09 에 생년월일·성별을 넣었나

09 의 원래 카피는 "성별은 통신사 인증으로만 확인해요" 였다. 통신사 연동이 없으므로 **확인하지 못한 값을 확인한 척하는 문구**가 된다. 카피를 "성별은 동성 매칭에만 써요"로 바꾸고 직접 고르게 했다. 생년월일도 같은 이유로 본인 확인 화면에 둔다(실명·연락처와 같은 결).

### 왜 10 에 아이디·비밀번호를 넣었나

09 는 본인 확인, 10 은 "나를 어떻게 부를지"를 정하는 화면이다. 계정 자격증명은 후자에 가깝고, 새 화면을 추가하면 n/5 진행 표시를 전부 다시 매겨야 한다. 표시 이름은 **09 의 실명으로 프리필**해 입력 부담을 줄였다(수정 가능).

### 화면별 제출 전 검증 (AuthPolicy)

- `loginId`: 대문자를 눌러도 조용히 소문자로 받는다 — 규칙이 영소문자만 허용하는데 "맞게 썼는데 왜 안 되지"를 만들지 않기 위해.
- `password`: 8~64자 + 영문·숫자·특수문자 각 1개 (`AuthPolicy.isValidPassword`).
- `email`: `@` 와 `.` 유무만 본다. 정교한 RFC 흉내는 정상 주소를 막는 쪽 사고가 더 흔하다.
- 서버 400 은 `AuthError.INVALID_REQUEST` 의 `serverMessage` 를 **그대로** 노출한다("필드: 사유" 형태라 고칠 곳이 바로 보인다).

### 🟡 이름 규칙을 넓혔다 (한글 전용 → 한글·영문)

09 의 이름 검증이 `^[가-힣]{2,10}$` 이라 **에뮬레이터(한글 IME 없음)에서 가입 자체가 불가능**했다. 서버도 한글을 강제하지 않으므로 `^[가-힣a-zA-Z]{2,20}$` 로 넓혔다(서버 `NAME_MAX_LENGTH = 20` 에 맞춤).

---

## 3. 로그인 · 세션 라우팅

### 04a 아이디 로그인 (신규)

`Routes.LOGIN_FORM = "auth/login-form"` — 04 의 「이미 계정이 있어요 · 로그인」, 03 온보딩의 「이미 계정이 있어요」, 그리고 로그아웃·세션 만료가 모두 여기로 온다.
`LOGIN_FAILED` 는 화면을 갈아엎지 않고 입력 바로 아래 `NoticeBanner(ERROR)` 로 인라인 표시한다 — 고칠 곳이 위에 있기 때문.

### 카카오 버튼

백엔드에 소셜 로그인이 없다. **버튼을 지우지도, 가입 플로우로 보내지도 않았다** — 지우면 나중에 붙일 자리를 잃고, 눌러서 가입으로 보내면 카카오로 가입한 줄 아는 사용자가 생긴다. 탭하면 「카카오 로그인은 준비 중이에요. 이메일로 시작해 주세요」 배너가 그 자리에 뜬다.

### 진입 라우팅 — `Unknown` 처리

`MainNavGraph` 를 둘로 쪼갰다.

```
MainNavGraph   ← authState 를 보고 Unknown 이면 SplashScreen 만 그린다
   └ MainNavHost ← 확정된 뒤에만 생성. 그 시점 상태가 startDestination 을 정한다
                   (Authenticated → HOME, Unauthenticated → ONBOARDING_SAVING)
```

`Unknown` 을 미로그인으로 취급하면 이미 로그인한 사용자가 앱을 켤 때마다 온보딩을 스쳐 본다. `startDestination` 은 `remember` 로 **한 번만** 계산한다 — 상태를 계속 따라가게 하면 로그아웃 때 NavHost 가 통째로 재생성돼 백스택이 날아간다.

### 로그아웃 · 세션 만료

둘 다 `authState` 가 `Authenticated → Unauthenticated` 로 바뀌는 같은 신호다. 개별 화면이 아니라 `MainNavHost` 의 `LaunchedEffect` **한 곳**에서 받아 `resetTo(LOGIN_FORM)` 한다(별도 이벤트 채널이 없으므로 이 스트림이 유일한 통보 경로). 구분은 `logoutRequested` 플래그로 한다 — 사용자가 직접 누른 로그아웃에 "세션이 만료됐어요"를 띄우면 거짓말이 된다.

마이페이지 「로그아웃」은 확인 다이얼로그 뒤 `authRepository.logout()` 를 호출한다. 이 호출은 실패하지 않으므로(로컬 먼저 비우고 서버 무효화는 최선 노력) 에러 처리가 없다.

---

## 4. 변경 파일

**신규 (presentation)**
- `feature/auth/SignupDraft.kt` — 가입 초안 + `AuthError` → 한국어 문구 매핑
- `feature/auth/LoginFormScreen.kt` — 04a (스테이트리스)
- `feature/auth/LoginFormRoute.kt` — `LoginViewModel`
- `feature/auth/SignupSubmitRoute.kt` — `SignupViewModel` + `MannerPledgeRoute`
- `core/SplashScreen.kt` — `AuthState.Unknown` 동안의 화면

**변경 (presentation)**
- `core/MainNavGraph.kt` — 인증 게이트 분리, `authRepository` 주입, 04a 등록, 가입 초안 배선, 세션 전이 처리, 미사용 `userSession` 인자 제거
- `core/Routes.kt` — `LOGIN_FORM` 추가
- `feature/auth/LoginScreen.kt` — `onKakaoStart` 제거 + 「준비 중」 배너
- `feature/auth/IdentityVerifyScreen.kt` — 생년월일·성별 추가, 이름 규칙 확장, 콜백 시그니처 변경
- `feature/auth/ProfileSetupScreen.kt` — 「로그인 정보」 절 추가, 프리필 파라미터
- `feature/auth/MannerPledgeScreen.kt` — 자체 딜레이 제거 → `submitting`/`errorMessage` 수신
- `feature/mypage/MyPageScreen.kt` — `onLogout` + 확인 다이얼로그
- 컴파일 복구 7파일 (§1)

**변경 (app)**: `MainActivity.kt` — `authRepository = container.authRepository` 전달

하드코딩 색상 없음(신규 파일 `Color(0x…)` 0건). 기존 화면의 와이어프레임 그레이는 그대로 뒀다.

---

## 5. 화면 진입 경로

| 화면 | 경로 |
|---|---|
| 04a 로그인 | 01~03 온보딩 → 04 「로그인」 / 03 「이미 계정이 있어요」 / 로그아웃·세션만료 자동 이동 |
| 가입 (학생) | 04 「이메일로 시작하기」 → 05 학생 → 06 → 07 → 09 → 10 → 11 → 12 → 13 → 14 홈 |
| 가입 (직장인·일반) | 05 직장인/일반 → 08 → 09 → 10 → 11 → 12 → 13 |
| 가입 (인증 생략) | 05 「나중에 인증할게요」 → **09** (홈 직행이 아니다 — 계정 없이는 홈이 성립하지 않는다) |
| 로그아웃 | 35 마이 → 「로그아웃」 → 확인 → 04a |

---

## 6. 실기 검증 결과 (emulator-5554, 서버 localhost:8080)

| # | 항목 | 결과 | 근거 |
|---|---|---|---|
| a | 신규 가입 → 자동 로그인 → 홈 | ✅ | `POST /users` **201** → `POST /users/login` **200** 연속 발화. 13 완료 → 14 홈 (`auth_24~26`) |
| b | 앱 재시작 → 로그인 유지, 홈 직행 | ✅ | force-stop 후 재실행에 온보딩 없이 홈 (`auth_07`) |
| c | 로그아웃 → 로그인 화면 | ✅ | `POST /auth/logout` **204**, 04a 로 이동. 만료 안내 없음(직접 로그아웃이므로) (`auth_13~14`) |
| d | 잘못된 비밀번호 → 에러 표시 | ✅ | 401 → 「아이디 또는 비밀번호가 올바르지 않아요」 인라인 (`auth_05`) |
| e | 로그인 후 방 합류 (새 토큰 경로) | ✅ | `POST /matching/rooms/3/join` **200**, 응답 members 에 토큰 주체가 추가됨. 나가기도 `DELETE /matching/leave/3/0` **204** (`auth_08~11`) |
| + | 로그아웃 후 재시작 | ✅ | 온보딩으로 복귀 = 세션이 실제로 비워짐 (`auth_30`) |
| + | 카카오 「준비 중」 안내 | ✅ | (`auth_03`) |

**테스트 계정**: `testuser1 / Passw0rd!` (기존), `qatester902 / Passw0rd!` (앱으로 신규 가입), `probeuser902 / Passw0rd!` (아래 조사용 curl 생성).

**서버 상태**: 인계받은 그대로 복원했다 — ACTIVE 방 **partyId 3(1/3), 4(1/3)**. 합류 테스트 후 즉시 나가기로 되돌렸다. 서버는 죽이지 않았다.

한글 IME 부재로 이름은 영문(`QaTester`)으로 입력했다 — §2 의 이름 규칙 확장으로 통과한다.

---

## 7. 알려진 한계 · 백엔드 요청

### 🟡 RideDetail `isHost` 고정 `"1"` (수정하지 않음 — 지시대로)

`RideDetailRoute.kt:103` 의 `currentUserId` 는 여전히 `UserSession.currentUserId.toString()` = `"1"` 이다. **앱이 고칠 수 있는 문제가 아니다**: 로그인으로 받는 식별자는 UUID 뿐이고, 방 상세 응답(`members[].memberId`)에는 "내 멤버십/방장 여부"가 없다. id 가 1 이 아닌 계정에서는 방장 배지와 「나 제외」 필터가 엉뚱한 사람을 가리킨다. 코드에 근거를 주석으로 남겼다.
→ **백엔드 요청**: 방 상세 응답에 `isHost` / `myMemberId` 를 포함하거나, `GET /users/me` 로 내부 id 를 알 수 있게 해 달라.

### 🟡 방 생성 `creatorId = 1` 하드코딩 (D-5, 유지)

`POST /matching/rooms` 만 본문 `creatorId` 를 쓴다. 22 보고서 R-2 그대로 대기 중.

### 🔴 D-6 (신규) — 새로 가입한 계정이 시드 파티의 유령 멤버가 된다

**증상**: 방금 가입한 계정으로 `POST /matching/rooms/3/join` 하면 **409 `ALREADY_JOINED_OTHER_PARTY` "이미 참여 중인 방이 있습니다."**

**앱 결함이 아님을 확인했다** — 앱을 거치지 않고 curl 로만 재현된다:

```
POST /api/v1/users     (loginId=probeuser902)      → 201
POST /api/v1/users/login                            → 200 (accessToken)
POST /api/v1/matching/rooms/3/join  + Bearer        → 409 ALREADY_JOINED_OTHER_PARTY
```

**원인 추정**: 시드된 파티 3·4 는 `creatorId` 2·3 을 **본문으로 지정해** 만들어졌는데(D-5), 그 시점에 id 2·3 사용자는 존재하지 않았다. 이후 가입하는 사용자가 순서대로 id 2, 3 을 받으면서 **자기가 만든 적 없는 방의 멤버로 태어난다.** 실제로 파티 3 의 멤버는 `memberId: 2`, 파티 4 는 `memberId: 3` 하나뿐이다.

**영향**: 신규 가입자의 첫 합류가 이유 없이 막힌다. QA 시나리오에서 "가입 → 합류"를 이어서 검증할 수 없다.

**조치 제안**: R-2(방 생성 `@LoginUser` 전환)가 처리되면 근본 원인이 사라진다. 그 전까지는 시드 파티를 **실제로 가입한 계정의 id 로** 만들어야 한다.

### 🟡 세션 만료(SESSION_EXPIRED) 전이는 실기에서 미검증

액세스 토큰 수명이 10분이고 재발급이 자동으로 성공하므로, 리프레시까지 무효화하지 않으면 만료를 만들 수 없다(앱의 리프레시 토큰은 DataStore 안이라 외부에서 무효화할 수단이 없었다). 로직 자체는 **로그아웃과 완전히 같은 경로**(`authState` 전이 → `resetTo(LOGIN_FORM)`)를 타며, 로그아웃 쪽이 실기로 검증됐다. 차이는 안내 문구 유무뿐이다.

### 🟢 로그아웃 후 시스템 뒤로가기 = 앱 종료

로그아웃하면 백스택이 비워지고 04a 가 루트가 된다. 이 상태의 시스템 back 은 앱을 종료한다(안드로이드 루트 화면 관례대로). 상단바 뒤로 화살표는 04 시작 화면으로 보낸다.

### 🟢 표시 이름은 아직 서버로 가지 않는다

`POST /users` 에 표시 이름을 담을 필드가 없다. 10 에서 받되 전송하지 않고, 서버 `name` 에는 09 의 실명이 들어간다.
