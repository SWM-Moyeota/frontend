# 03 측정 결과 — `adjustNothing` → `adjustResize` (API 29 / API 37)

작성: 2026-10-28 / qa-verifier
APK: 리더 제공 `BEFORE-stub-adjustNothing.apk`(0x30) · `AFTER-stub-adjustResize.apk`(0x10)
**소스·매니페스트 수정 없음. Gradle 빌드 없음. `../backend` 미기동.**
설치 때마다 aapt2 로 softInputMode 확인, 교체는 매번 `uninstall` 후 설치.
런타임에서도 `dumpsys` 의 `sim={adjust=...}` 로 **16칸 전부** 빌드 정체를 재확인했다(혼입 없음).

## 결론 4줄

1. **BEFORE/API29 결함 재현됨** — `imePadding=0px`, 창이 전혀 줄지 않고 입력창이 키보드에 완전히 가려진다
2. **AFTER/API29 수정 확인** — `imePadding=867px`, 입력창이 키보드 위로 올라온다
3. **API37 은 before/after 완전 동일** — 회귀 없음 (입력 바 좌표까지 픽셀 단위로 같다)
4. **이중 계산 없음** — 판별식은 126 이 나왔지만 결론은 "문제 없음"이다(§4 에 근거)

## 측정 매트릭스 (16칸 전부)

API29 는 `Cur insets content.bottom`/`stable.bottom` → androidx `Impl20` 역산 공식에 대입.
API37 은 `InsetsSource id=3 type=ime` 프레임. 화면 높이 2400.

| 기기 | 빌드 | 화면 | sim | 키보드 내림 | 키보드 올림 |
|---|---|---|---|---|---|
| API29 | BEFORE | 04a·10·15·24 (4개 모두) | `adjust=nothing` | content.b=126 stable.b=126 → **0px** | content.b=**126** stable.b=126 → **0px** |
| API29 | AFTER | 04a·10·15·24 (4개 모두) | `adjust=resize` | content.b=126 stable.b=126 → **0px** | content.b=**867** stable.b=126 → **867px** |
| API37 | BEFORE | 04a·10·15·24 (4개 모두) | `adjust=nothing` | ime visible=false → **0px** | ime frame.top=1517 → **883px** |
| API37 | AFTER | 04a·10·15·24 (4개 모두) | `adjust=resize` | ime visible=false → **0px** | ime frame.top=1517 → **883px** |

4개 화면이 칸마다 **완전히 같은 값**이 나왔다. `imePadding()` 의 동작을 정하는 것이 루트 모디파이어
하나라는 §02 의 그룹화 근거가 측정으로 뒷받침됐다.

창 프레임 변화(API29): `Frames: content` bottom 이 BEFORE 는 내림/올림 모두 **2274 고정**,
AFTER 는 **2274 → 1533**. `mFrame` 은 네 경우 모두 `[0,0][1080,2400]` 불변 — §02 의 정정대로
`mFrame` 을 봤으면 판정이 불가능했다.

---

## 꼭 답해야 하는 것 — 4개

### 1. BEFORE/API29 에서 입력창이 실제로 가려지는가 → **그렇다 (재현됨)**

키보드 상단 경계 **y=1533** (`InsetsSource TYPE_UNKNOWN_10 frame=[0,1533][1080,2400]`).

| 화면 | 포커스된 입력창 bounds | 판정 |
|---|---|---|
| **24 채팅** | `[95,1946][827,2072]` — 탭 전후 **변화 없음** | 키보드선보다 **413px 아래**. 입력 바 + 하단탭 **둘 다 완전히 사라짐** |
| **10 가입** | `[84,1758][903,1884]` (`password="true"` = 비밀번호 칸) | **225px 아래**, 100% 가림. 레이아웃이 **스크롤되지도 않았다** |
| 15 목적지 | `[84,385][996,511]` | 검색창 자체는 위라 안 가림 (결과 리스트가 가림) |
| 04a 로그인 (대조군) | `[84,592][996,718]` | **안 가려짐 — 정상** |

같은 스크린샷에서 교차 검증: 10 의 「아이디」 칸은 `[84,1498][996,1624]` 로 키보드선 1533 을
걸쳐 **윗부분 35px 만** 보인다. 스크린샷과 숫자가 정확히 일치한다.

**04a 대조군이 "안 가려짐"으로 나온 것이 중요하다** — 측정기가 어디서나 "깨짐"을 내놓지
않는다는 뜻이다. 즉 위 두 건은 측정 아티팩트가 아니다.

스크린샷: `BEFORE_api29_24_chat_up.png`(입력 바·하단탭 소멸) · `BEFORE_api29_10_profilesetup_up.png`

### 2. AFTER/API29 에서 안 가려지는가 → **그렇다 (수정 확인)**

24 채팅 입력 바가 **`[95,1946][827,2072]` → `[95,1376][827,1502]`** 로 이동.
키보드 상단 1533 보다 **31px 위** — 완전히 보인다. `imePadding=867px` 가 실제로 적용됐다.

스크린샷 `AFTER_api29_24_chat_up.png` 에서 「메시지 보내기」 입력 바와 전송 버튼이 키보드 바로
위에 또렷하게 보인다.

> 하단탭이 안 보이는 것은 **의도된 동작**이다 — `ChatScreen.kt:569 if (!imeVisible) {` 가 IME
> 가 뜨면 `MoyeotaBottomBar` 를 아예 그리지 않는다. 그 `imeVisible`(`:285`)은
> `WindowInsets.ime.getBottom()`(`:284`) 이 0 이 아니어야 참이 되는데, **BEFORE/API29 에서는
> 0 이라 이 분기가 죽어 있었다.** 즉 이번 수정으로 `ChatScreen` 의 IME 분기가 **처음으로 동작**한다.

### 3. API37 은 before/after 가 동일한가 → **완전히 동일 (회귀 없음)**

4개 화면 전부 내림 `0px` / 올림 `883px` 로 before·after가 **같다**.
24 채팅 입력 바 좌표도 **`[95,1360][827,1486]` 로 before·after 픽셀 단위 동일**.

이유도 측정으로 확인됐다: **API37 은 BEFORE(`adjustNothing`)에서 이미 883px 을 받는다.**
`Impl30` 이 플랫폼 `WindowInsets.getInsets(ime)` 를 직접 읽어 창 리사이즈와 무관하기 때문이고,
그래서 `adjustResize` 로 바꿔도 받는 값이 달라지지 않는다. 리더 근거 문서의 예상과 일치한다.
교차검증: `mImeHeight=820` + 내비바 63 = 883 (`InsetsSource type=navigationBars frame=[0,2337][1080,2400]`).

**→ 이 변경의 비용은 API30+ 에서 0 이다.**

### 4. 이중 계산 → **없음**

내가 제시한 판별식의 실측값은 **126** 이다(키보드 내림 시 `content.bottom`). 판별식대로면
"이중계산 의심"이지만, **세 가지 교차 확인 결과 실제로는 과잉이 없다.** 판별식 쪽을 정정한다.

1. **867 은 키보드+내비바가 아니라 IME 창의 실제 높이다.**
   API29 의 IME 창은 `frame=[0,1533][1080,2400]` = **867px** 로, 내비게이션 바 영역까지
   **직접 덮는다**. 즉 `imePadding=867` 은 사용자가 실제로 가려지는 높이와 정확히 같다.
   (741 = 867−126 은 "내비바를 뺀 키보드"일 뿐, 실제 가림 높이가 아니다.)
2. **평상시(키보드 내림) 레이아웃이 before/after 바이트 단위로 같다.**
   24 채팅 입력 바: BEFORE `[95,1946][827,2072]` / AFTER `[95,1946][827,2072]` — **동일**.
   내림 상태 `content.bottom` 도 둘 다 126 으로 같다. → **평상시 하단 과잉 여백 없음.**
3. **`NavigationBarSpacer` 와 겹칠 수 없다.**
   내가 §02 에서 지목한 `Bars.kt:60`(`windowInsetsBottomHeight(navigationBars)`)은
   `MoyeotaBottomBar` 끝에 붙는데(`Bars.kt:139`), 그 `MoyeotaBottomBar` 는 키보드가 뜨면
   `ChatScreen.kt:569` 에서 **아예 컴포즈되지 않는다**. 겹칠 대상이 존재하지 않는다.

**→ 회귀의 주 후보로 지목했던 이중 계산은 실측 결과 발생하지 않는다.**

---

## 덤 항목

### 1. 채팅 글자수 카운터 — **통과** (P0 중 가장 값싸게 같이 확인됨)

코드 상수 확인: `COUNTER_VISIBLE_FROM = MAX_CONTENT_LENGTH - 100 = 900`, `INPUT_HARD_LIMIT = 2000`.

| 길이 | 카운터 | 위치 | 전송 버튼 |
|---|---|---|---|
| 900 | `900/1000자` | `[53,1280][1027,1343]` | `enabled="true"` |
| 1001 | `1001/1000자 — 줄여야 보낼 수 있어요` (빨강·Bold) | `[53,1280][1027,1343]` — **동일** | **`enabled="false"` `clickable="false"`** |
| 2000 | `2000/1000자 — 줄여야 보낼 수 있어요` | 동일 | `enabled="false"` |

- 카운터 y=1280~1343 로 **키보드 상단 1533 보다 위** → AFTER/API29 에서 **가려지지 않는다**
- 900 → 1001 에서 카운터 bounds 가 **변하지 않는다** → 등장 후 버튼이 움직이며 잠기는 문제 없음
- 1001 에서 전송 **실제로 잠긴다**(enabled=false 확정)
- 스크린샷: `BONUS_api29_counter_900.png` · `_1001.png` · `_hardlimit.png`

### 2. 2000자 붙여넣기 / `singleLine` 가로 스크롤 — **통과**

- 2000자에서 **50자 더 입력해도 2000 유지** → `INPUT_HARD_LIMIT=2000` 이 붙여넣기 폭주를 막는다
- 입력 바 bounds `[95,1376][827,1502]` — 빈 상태와 **높이 동일(126px)**. 세로로 늘어나지 않는다
- 가로 스크롤로 **문자열 끝**이 보이고 캐럿이 끝에 있다 (`BONUS_api29_counter_hardlimit.png`)

### 3. 작은 폰 360×640dp 「1명」 안내 — **미검증 (막힘)**

`wm size 720x1280` + `wm density 320` 적용 성공(`am get-config` → `sw360dp w360dp h545dp`).
**측정 후 `wm size reset` · `wm density reset` 으로 원복 확인함.**

그러나 **16 도착지 확인 모달에 도달하지 못했다.** 15 에서 검색어를 넣으면 앱이
`GET /api/v1/places?query=gangnam` 을 실제로 호출하고 스텁이 **200 + 3건(380바이트)** 을
돌려주는데, **화면에 결과가 한 건도 렌더되지 않는다**(uiautomator 덤프에 「강남역」 없음).
`PlaceMappers.toPlace()` 는 필터가 없어 매핑에서 걸릴 이유는 없었다.

내 스텁 응답이 실서버와 다를 가능성과 앱 결함 가능성을 **구분하지 못했다** — 추정으로
채우지 않고 미검증으로 남긴다. 확인하려면 실서버 `/api/v1/places` 응답 한 건이 필요하다.
스크린샷: `BONUS_small_15_search.png`

### 4. 17 내 방 거르기 — **미착수** (3번이 막혀 시간을 P0 완결에 썼다)

---

## 미검증 / 막힌 지점 정리

| 항목 | 사유 |
|---|---|
| 06 학교이메일·07 이메일코드·08 재직인증·09 본인인증 | **런타임 도달 불가** — `MainNavGraph.kt` 참조 0건(§02). 매니페스트 수정의 수혜는 같이 받지만 실기 확인은 불가능 |
| 36 프로필수정 · 37 회원탈퇴 | 10 과 동일 A 구조라 묶어서 제외(§02). 10 이 상계이고 통과함 |
| 덤 3 (작은 폰 16 모달) | 15 검색 결과 미렌더로 16 도달 실패 (위) |
| 덤 4 (17 내 방 거르기) | 미착수 |

## 환경 / 정리 상태

- 스텁 서버: `scratchpad/stub/stub_server.py`, `127.0.0.1:8080` 만 바인드. **측정 종료 후 종료 확인**
  (포트 해제 확인). 재기동: `python3 -I <경로>/stub_server.py <로그경로>`
  - 로그인 토큰은 **실제 JWT 형태**여야 한다 — `RemoteAuthRepository.kt:113` 이
    `jwtSubject(accessToken)` 로 `sub` 를 읽는다. 평문 토큰이면 로그인이 「잠시 후 다시
    시도해 주세요」로 조용히 실패한다(처음에 여기서 한 번 막혔다)
  - 앱이 `GET /ws-chat` 도 호출한다(웹소켓 추정) — 스텁은 200 `{}` 로 흘려보냈고 화면 동작에 영향 없었다
- 에뮬레이터 2대 **기동 유지**: `emulator-5556`(API29, AFTER 설치됨) · `emulator-5558`(API37, AFTER 설치됨)
- `Pixel_6_QA` 는 이전 단계에서 `show_ime_with_hard_keyboard=0` 원복 후 종료된 상태 그대로
- 소스·매니페스트·`local.properties` 미접근, git 명령 미실행

## 산출물 경로

- 측정 원본 64개(스냅샷 txt + PNG): `/private/tmp/claude-501/-Users-sungyoon-Desktop-moyeota-frontend/1b791af6-8c40-4510-9b30-44b1700ea413/scratchpad/shots/`
  - 명명: `{BEFORE|AFTER}_{api29|api37}_{04a_login|10_profilesetup|15_destination|24_chat}_{down|up}.{txt,png}`
- 재현 스크립트: 같은 scratchpad 의 `measure.sh` `report.sh` `goto.sh` `login_only.sh` `install.sh` `tap.sh` `tapc.sh`
