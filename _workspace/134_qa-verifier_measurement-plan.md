# 02 측정 설계 — IME inset 창 프레임 측정

작성: 2026-10-28 / qa-verifier
전제: `01b_leader_ime-inset-evidence.md` 읽음. **빌드·설치·소스수정 없음.** 대기 중.

## 1. 비교군 정리 완료

| AVD | 상태 |
|-----|------|
| `Pixel_6_QA` | `show_ime_with_hard_keyboard` **0 으로 원복 확인** 후 종료. 흔적 없음 |
| `Pixel_6_API29` | 가동 중 (`emulator-5556`, sdk=29) |
| `Pixel_6_API37_QA` | **신규 생성·부팅 확인** (`emulator-5558`, sdk=37) |

두 측정 기기 config 전체 diff 결과 **차이가 이것뿐**이다:
```
avd.id / avd.name
image.sysdir.1   android-29/google_apis  vs  android-37.0/google_apis_ps16k
tag.ids / tag.displaynames / target
```
하드웨어 설정(`hw.keyboard=no`, 1080x2400, density 420, portrait, ramSize, gpu)은 **전부 동일**.
런타임에서도 양쪽 `am get-config` 가 똑같이 `keysexposed nokeys navhidden nonav` 를 돌려준다.
→ **변수는 API 레벨 하나.**

차이로 남는 것 하나: 내비게이션 바 높이가 **API29=126px(3버튼), API37=63px(제스처)** 로 다르다.
기기 간 절대값 비교는 못 하지만 판정은 **기기별 before/after** 로 하므로 문제 없다.

## 2. [정정] `mFrame` 은 안 변한다 — 봐야 할 키는 따로 있다

리더 지시의 "`adjustResize` → 프레임 높이가 줄어든다" 는 **`mFrame` 기준으로는 틀렸다.**
연락처 앱(`adjust=resize` 확인됨)으로 리허설한 실측값:

**API 29 / 연락처 / `sim={adjust=resize}`**

| 키 | 키보드 내림 | 키보드 올림 | 델타 |
|----|------------|------------|------|
| `mFrame` | `[0,0][1080,2400]` | `[0,0][1080,2400]` | **0 (불변)** |
| `Frames: content` | `[0,63][1080,2274]` | `[0,63][1080,1533]` | **-741** |
| `Cur insets: content` bottom | **126** | **867** | **+741** |
| `Cur insets: stable` bottom | 126 | 126 | 0 |

`LAYOUT_IN_SCREEN|LAYOUT_INSET_DECOR` 창은 전체화면을 유지한 채 **콘텐츠 인셋만** 바뀐다.
`mFrame` 만 봤으면 `adjustResize` 를 `adjustNothing` 으로 **오판할 뻔했다.**

### 이 숫자가 그대로 androidx 공식의 입력값이다

리더가 디컴파일한 `Impl20.getInsetsForType(IME)`:
```java
if (systemWindow.bottom > rootStable.bottom) return Insets.of(0,0,0, systemWindow.bottom);
```
- `systemWindowInsets.bottom` = `Cur insets: content` bottom
- `rootStableInsets.bottom` = `Cur insets: stable` bottom

대입: 올림 `867 > 126` → true → **IME inset = 867**. 내림 `126 > 126` → false → **0**.
→ **소스를 안 고치고도 `imePadding()` 이 받을 값을 adb 출력만으로 계산할 수 있다.**

### 여기서 바로 보이는 이중 계산

실제 키보드 높이는 `867 - 126 = 741`. 그런데 공식은 차이가 아니라 **`systemWindow.bottom` 통째(867)**
를 돌려준다 → **내비바 126px 이 IME inset 에 섞여 들어간다.**

그리고 `core/designsystem/.../Bars.kt:60` 의 `NavigationBarSpacer` =
`windowInsetsBottomHeight(WindowInsets.navigationBars)` 이고, 이게 `MoyeotaBottomBar` 끝에 붙어
(`Bars.kt:139`) **ChatScreen 의 `imePadding()` Column 안에**(`ChatScreen.kt:304`, `:566`) 들어간다.
→ `adjustResize` 로 고친 뒤 **하단이 내비바 높이만큼 과하게 뜨는** 회귀가 나올 수 있다.

단, 연락처는 edge-to-edge 가 아니다. 모여타는 `enableEdgeToEdge()`(`MainActivity.kt:49`) 라
`LAYOUT_HIDE_NAVIGATION` 이 걸려 content inset 의 내비바 성분이 0 일 수 있다. **판별식:**

| 앱에서 `Cur insets: content` bottom (키보드 내림) | 해석 |
|---|---|
| `0` | edge-to-edge 가 내비바를 뺀다 → 올림값 = 키보드 높이 → **이중계산 없음** |
| `126` | 내비바가 섞인다 → 올림값 = 키보드+126 → **이중계산, 하단 126px 과잉** |

## 3. 확정된 측정 명령 (기기별로 다르다 — 실측 확인함)

### API 29 (`emulator-5556`)
```bash
adb -s emulator-5556 shell dumpsys window windows   # 포커스 창 블록
```
읽을 키 3개:
- `mAttrs={... sim={adjust=XXX ...}}` → **매니페스트 변경이 먹었는지 직접 확인** (`nothing`/`resize`)
- `Cur insets: content=[l,t][r,B]  ... stable=[l,t][r,S]` → 공식 입력값 `B`, `S`
- `Frames: content=[...]` → 실제 콘텐츠 영역

예측 IME inset = `B > S ? B : 0`

> API 29 에도 `InsetsSource` 목록이 있지만 IME 가 `TYPE_UNKNOWN_10` 으로 나오고
> `visible` 이 실제와 어긋난다(키보드가 떠 있는데 `visible=false`). **쓰지 않는다.**
> 참고로 그 frame `[0,1533][1080,2400]` 은 2400-1533=867 로 위 `Cur insets` 와 일치한다(교차검증용).

### API 37 (`emulator-5558`)
`Cur insets` 라인이 **아예 없다.** 대신:
```bash
adb -s emulator-5558 shell dumpsys window | grep -E "InsetsSource id=3 type=ime|type=navigationBars"
adb -s emulator-5558 shell dumpsys window displays | grep -E "mIsImeShowing|mImeHeight"
adb -s emulator-5558 shell dumpsys window windows   # sim={adjust=...} 확인용
```
실측 검증 완료:
- 올림: `type=ime frame=[0,1517][1080,2400] visible=true` → inset = 2400-1517 = **883**, `mImeHeight=820`
- 내림: `type=ime frame=[0,0][0,0] visible=false` → **0**, `mImeHeight=0`
- 내비바: `type=navigationBars frame=[0,2337][1080,2400]` → 63 (883 = 820+63)

### 재현 스크립트
`.../scratchpad/ime_probe2.sh <serial> <tag> <outdir>` — 앱 실행 → EditText 탐색(uiautomator) →
BACK 으로 키보드 내림 확인 → down 스냅 → 탭 → up 스냅. 각 스냅마다 창 블록 + PNG 를 남긴다.
연락처로 리허설 완료, 앱 패키지만 바꾸면 그대로 쓴다.

## 4. 측정 대상 화면 — 10개가 아니라 **6개**다

### [결함 아님, 사실 정정] 4개는 런타임에서 도달 불가
`MainNavGraph.kt` 참조 수 실측:

| 화면 | `Routes` 참조 | nav 등록 |
|------|--------------|---------|
| `SCHOOL_EMAIL`(06) `EMAIL_CODE`(07) `WORK_VERIFY`(08) `IDENTITY_VERIFY`(09) | **0** | **없음** |

`Routes.kt:14` 주석이 의도를 밝힌다 — "부가 인증 05~09 는 **가입 그래프에 등록하지 않는다**".
→ 이 4개는 실기 측정이 **불가능**하고, 지금 사용자에게 영향도 없다. 나중에 연결될 때를 대비해
매니페스트 수정의 수혜는 같이 받는다. **측정 대상에서 제외.**

### 남은 6개를 구조로 묶었다
전부 루트가 `Column(fillMaxSize().imePadding())` + `Column(weight(1f).verticalScroll)` 로 같다.
**`imePadding()` 동작을 정하는 건 루트 모디파이어 하나**라 같은 구조는 같은 결과가 나온다.
그래서 구조가 겹치는 것은 대표 하나로 묶는다.

| # | 화면 | 구조 분류 | 고른 이유 |
|---|------|----------|----------|
| 1 | **ChatScreen** (24) | B · 하단 고정 입력바 + 하단탭 | 입력창이 화면 **최하단 고정** → 가림 최악. 유일하게 `WindowInsets.ime` 직접 읽음(`:280`). `MoyeotaBottomBar`(=`NavigationBarSpacer`)가 `imePadding` 안 → **이중계산 관측 지점**. 빼면 안 되는 화면 |
| 2 | **ProfileSetupScreen** (10) | A · 스크롤 폼 (worst case) | 591줄·`MoyeotaTextField` 7개로 A 중 필드가 **가장 아래**까지 내려간다. A 가 깨지면 여기서 가장 먼저 보인다 |
| 3 | **DestinationScreen** (15) | C · 리스트 위 검색창 | 입력은 위, **결과 리스트가 키보드에 가리는** 유일한 패턴. `BasicTextField` |
| 4 | **LoginFormScreen** (04a) | A · 필드 상단 (**음성 대조군**) | 필드가 위라 **가려지지 않아야 정상**. 측정기가 "항상 깨짐"을 내놓지 않는지 검증하는 대조군. 게다가 백엔드 없이 도달 |

**묶어서 뺀 것:** `ProfileEditScreen`(36), `AccountDeleteScreen`(37) — 둘 다 ProfileSetup 과
동일한 A 구조에 필드 수만 더 적다(각 1개·1개). ProfileSetup 이 상계(upper bound)라 통과하면 함께 통과.
2번이 예상 밖으로 나오면 그때 추가한다.

→ 4화면 × (내림/올림) × 2기기 × (before/after) = **16 런**. 10화면 40런 대비 60% 감축.

## 5. [막힘] 4개 중 2개는 백엔드 없이 못 간다

`navDeepLink`·`<data android:scheme>` 이 **코드 전체에 없다** → `am start -d` 로 화면 점프 불가.
UI 를 타고 들어가야 하는데 `MainNavGraph.kt:420` 이
`startDestination = if (loggedIn) HOME else ONBOARDING_SAVING` 이다.

| 화면 | 경로 | 백엔드 |
|------|------|--------|
| LoginForm(04a) | 온보딩 건너뛰기 → 04 → 「로그인」(`:553`) | **불필요** |
| ProfileSetup(10) | 온보딩 건너뛰기 → 04 → 「회원가입」(`:551`) | **불필요** (제출 전까지) |
| Destination(15) | 로그인 성공 → 홈 → 목적지 | **필요** |
| Chat(24) | 로그인 성공 → 채팅탭 | **필요** |

리더 지시대로 "로그인 성공이 필요 없다" 가 성립하는 건 **앞의 2개뿐**이다. 가장 중요한
**ChatScreen 이 하필 로그인 뒤**에 있다. 선택지:
- (a) `../backend` 를 띄워 로그인 → 4화면 전부 측정 ← **권장**
- (b) 백엔드 없이 LoginForm·ProfileSetup 만 측정하고 Chat·Destination 은 미검증으로 남김

(a) 로 갈지, 백엔드를 누가 띄울지 지시 바란다. 현재 `localhost:8080` 은 응답 없음(확인함).

## 6. 측정 항목 (화면마다 동일)

1. `sim={adjust=...}` — 매니페스트 상태 기록
2. 키보드 **내림** 상태 하단 인셋 — edge-to-edge 이중계산 함정(리더 지시 4번)
3. 키보드 **올림** 상태 인셋 → 공식 대입 → `imePadding()` 예상값
4. 스크린샷 2장 — 입력창이 실제로 가리는지(사용자 체감). 수치만으로는 체감을 모르고,
   스크린샷만으로는 원인을 모른다

판정:
- API29 before: 올림에도 inset 0 → `imePadding()` 무동작 → **결함 재현**
- API29 after(`adjustResize`): inset>0 + 입력창 보임 → **수정 확인**. 동시에 하단 과잉 여백 확인
- API37 before/after 동일 → **회귀 없음**

## 7. 대기 중 / 지키고 있는 것

- 빌드·설치·매니페스트 수정 **안 함** — 리더 신호 대기
- `ChatScreen.kt` 를 포함해 소스 **한 줄도 안 건드림** (디버그 로그 포함). compose-builder 작업 중
- 에뮬레이터 2대 기동 유지
- 렌더러: API37_QA=`host`, API29=`swangle`(소프트웨어). 메모리 압박 탓. 창 프레임·인셋 측정에는
  무관하나 속도 차가 있다. 실제 측정은 **한 대씩 순차**로 돌리면 둘 다 `host` 를 받는다 — 권장
