# 19 · 하단 탭바 — 실제 아이콘 교체 + 제스처 인디케이터 겹침 해소

사용자 피드백 2건. 대상은 `MoyeotaBottomBar` 하나이고, 이걸 쓰는 6개 화면 + `TabStateScaffold`가
함께 바뀐다.

## 1. 아이콘 — 의존성 추가 없이 designsystem 내부 정의 (선택지 b)

`material-icons-core/extended` **추가하지 않음**. 4개뿐이라 의존성보다 자체 정의가 가볍다는 판단.

정의 방식은 `ImageVector` 대신 **Canvas 스트로크 드로잉**을 골랐다 — 같은 파일 `Bars.kt:84`의
`BackArrowIcon`, 그리고 presentation 쪽 12개 아이콘(BellIcon·SearchIcon·HouseIcon…)이 전부 이미
이 방식이라, 새 표현을 하나 더 들이지 않는 쪽이 designsystem 톤에 맞다. tint 파라미터 하나로
선택/비선택을 처리하는 것도 동일하다.

- 24dp 정사각 그리드, 스트로크 1.8dp, `StrokeCap.Round` + `StrokeJoin.Round` (라운드·심플한 기존 톤)
- 좌표는 전부 정규화(0~1 × size)라 크기를 바꿔도 비율이 유지된다
- 홈 = 집(지붕+몸체+문) · 합승 = 택시(지붕 표시등 + 캐빈 + 차체 + 바퀴 2) ·
  채팅 = 꼬리 달린 말풍선(라운드 코너까지 한 Path로 그려 꼬리 접합부에 선이 남지 않게 함) ·
  마이 = 사람(머리 원 + 어깨 cubic)
- 색 규칙은 그대로: 선택 `MoyeotaColor.Primary500`, 비선택 `MoyeotaColor.TextMute`. 라벨 그대로.
- 아이콘이 22dp → 24dp 로 2dp 커진 만큼 탭 셀의 `vertical` 패딩을 8dp → 6dp 로 줄여 64dp 행 높이 유지.

## 2. 제스처 인디케이터 겹침

원인은 두 가지가 겹쳐 있었다.

1. `MoyeotaBottomBar` 에 내비게이션바 인셋 처리가 없었다 (앱은 `enableEdgeToEdge()`).
2. **탭바 아래에 가짜 홈 인디케이터(135×5dp 검은 바)를 그리는 화면이 5개** 있었다.
   17번 작업에서 제거한 `StatusBarMock` 의 하단 짝으로, 와이어프레임에서 따라온 목업이다.
   이게 실제 제스처 인디케이터와 정확히 같은 자리에 이중으로 그려지고 있었다.

인셋만 넣고 목업을 두면 "탭바 → 인셋 여백 → 다시 가짜 검은 바" 가 되어 더 나빠진다. 그래서
17번과 같은 처리를 했다 — **목업의 겉모습은 걷어내고 자리는 실제 인셋으로 대체**.

```kotlin
Column(modifier = modifier.fillMaxWidth().background(MoyeotaColor.SurfaceCanvas)) {
    HorizontalDivider(color = MoyeotaColor.Hairline)
    Row(modifier = Modifier.fillMaxWidth().height(64.dp)) { /* 탭 4개 */ }
    Spacer(Modifier.fillMaxWidth().windowInsetsBottomHeight(WindowInsets.navigationBars))
}
```

- 배경을 Row 에서 **Column 으로 올려** 인셋 영역까지 흰 배경이 이어지고, 아이콘·라벨만 인셋 위로 올라온다.
- `navigationBarsPadding()` 대신 인셋 Spacer 를 쓴 이유도 17번과 동일 — 각 화면 루트 컨테이너를
  건드리지 않고 배경 렌더링을 그대로 보존하기 위해.

## 변경 파일

| 파일 | 위치 | 내용 |
|------|------|------|
| `core/designsystem/.../component/Bars.kt` | 96~131 | `MoyeotaBottomBar` — 배경 Column 이동, 플레이스홀더 → `TabIcon`, 하단 인셋 Spacer |
| 〃 | 133~251 | `TabIcon`(139) + `drawHomeIcon`(156)/`drawTaxiIcon`(182)/`drawChatIcon`(214)/`drawPersonIcon`(242) 신규 (private) |
| 〃 | 3~36 | import 정리 (`navigationBars`·`windowInsetsBottomHeight`·Path/Stroke 계열 추가, `RoundedCornerShape` 제거) |
| `presentation/.../feature/home/HomeScreen.kt` | 239 (호출), 482~498 (정의) | 가짜 `HomeIndicator` 제거 |
| `presentation/.../feature/explore/ExploreScreen.kt` | 307 (호출), 1130~1146 (정의) | 〃 |
| `presentation/.../feature/chat/ChatScreen.kt` | 391 (호출), 559~571 (정의) | 〃 |
| `presentation/.../feature/mypage/MyRidesScreen.kt` | 257 (호출), 395~411 (정의) | `HomeIndicatorOnBar` 제거 |
| `presentation/.../feature/mypage/MyPageScreen.kt` | 264 (호출), 466~482 (정의) | `HomeIndicatorMyPage` 제거 |

(라인 번호는 변경 전 기준. `ChatListScreen.kt`·`core/LoadState.kt` 는 목업이 없어 컴포넌트 교체 효과만 받는다.)

data/ · domain/ 수정 없음. 하드코딩 색상 없음 (`MoyeotaColor` 만 사용).

## 검증

### 빌드
```
./gradlew :presentation:compileDebugKotlin :app:assembleDebug --console=plain
BUILD SUCCESSFUL in 3s
```
경고 1건은 기존 것 (MyRidesScreen.kt:180 상수 조건).

### 에뮬레이터 실기 (emulator-5554, 1080x2400 / density 420, 제스처 내비게이션)
서버·실기기 미접촉. 스크린샷 `_workspace/screenshots/bottombar_*.png`

| # | 화면 | 아이콘 | 선택 상태 | 제스처 바 겹침 |
|---|------|--------|-----------|----------------|
| 01 | 14 홈 | 집·택시·말풍선·사람 4개 모두 정상 렌더 | 홈 Primary500 | 없음 — 라벨 아래 인셋 여백, 흰 배경은 끝까지 이어짐 |
| 02 | 17 합승 (peek 시트) | 정상 | 합승 Primary500 | 없음 |
| 03 | 17 합승 (half 시트) | 정상 | 합승 | 없음 — 「+ 새 합승 방 만들기」 CTA·리스트 레이아웃 회귀 없음 |
| 04 | 24 채팅 목록 | 정상 | 채팅 | 없음 (이 화면은 원래 목업이 없어 이전엔 탭바가 제스처 바에 직접 붙어 있었음) |
| 05 | 35 마이 | 정상 | 마이 | 없음 |
| 06 | 18 내 탑승 | 정상 | 합승(스펙대로) | 없음 |

가짜 검은 바는 6개 화면 모두에서 사라졌고, 실제 제스처 인디케이터만 흰 배경 위에 남는다.

미검증: **24 채팅방(ChatScreen)** — 현재 계정에 참여 중인 채팅방이 없고, 방을 만들려면 합류
POST 로 서버 데이터를 만들어야 해서 건너뛰었다. 같은 공용 컴포넌트라 위 5개와 동일하게 동작하며,
입력창(메시지 Row)은 탭바 위에 그대로 있고 이번 변경은 탭바 아래 인셋만 추가한다.

## 2차 — 하단바 없는 19개 화면까지 확장 (같은 방식)

1차에서 "범위 밖"으로 남겨 둔 19개 화면의 가짜 홈 인디케이터를 같은 방식으로 정리했다.
실기에서 확인한 문제는 아래 두 가지였다.

- **20 탑승 상세** (`bottombar_07_ridedetail_cta.png`) — 가짜 검은 바가 실제 제스처 인디케이터와
  정확히 겹쳐 그려짐.
- **16 도착지 확인 모달** (`bottombar_08_modal_cta.png`) — `dialog()` 라 기능적 겹침은 없지만
  다이얼로그 안 가짜 바와 스크림 위 실제 인디케이터가 **위아래 두 줄**로 보임. (15 목적지 검색도 동일)

### 공용 컴포넌트

목업이 화면마다 이름·형태가 제각각이었다(`HomeIndicator` · `HomeIndicatorMock` · `HomeIndicatorBar` ·
`PledgeHomeIndicator` · `DestinationHomeIndicator` … 인라인 Box 까지 7가지). 전부 지우고
`StatusBarSpacer` 의 하단 짝을 designsystem 에 하나 올려 통일했다.

```kotlin
// core/designsystem/.../component/Bars.kt
@Composable
fun NavigationBarSpacer(modifier: Modifier = Modifier) {
    Spacer(modifier = modifier.fillMaxWidth().windowInsetsBottomHeight(WindowInsets.navigationBars))
}
```

`MoyeotaBottomBar` 도 1차에서 인라인으로 넣었던 Spacer 를 이걸로 바꿔 한 군데서만 정의된다.

### 화면별 처리

| 처리 | 화면 수 | 내용 |
|------|---------|------|
| `NavigationBarSpacer()` 로 교체 | 14 | 목업이 배경을 칠하지 않던 화면 — 그대로 치환 |
| `NavigationBarSpacer(Modifier.background(...))` | 4 | 20 탑승 상세 · 21 매칭 대기 · 25 배차 · 22 상대 프로필 — 목업이 흰 배경(`SurfaceCanvas`/`CanvasBg`)을 칠해 시트 배경을 이어주고 있었다. 그 배경을 modifier 로 넘겨 이전 렌더를 그대로 보존 |
| `Spacer(20.dp)` + `NavigationBarSpacer()` | 1 | 16 도착지 확인 모달 — `dialog()` 라 인셋이 0 이라, 목업이 차지하던 CTA 아래 여백(10+5+8dp)이 통째로 사라져 CTA 가 다이얼로그 바닥에 붙는다. 명시 여백으로 대체 |

```
auth/       AccountTypeScreen · LoginScreen · MannerPledgeScreen · ProfileSetupScreen ·
            SafetySettingsScreen · SignupCompleteScreen                        (6)
chat/       EmergencyScreen · RideOngoingScreen                                (2)
explore/    JoinConfirmScreen                                                  (1)
home/       DestinationScreen · DestinationConfirmModal                        (2)
matching/   DispatchStatusScreen · MatchWaitingScreen · PartnerProfileScreen ·
            RideDetailScreen                                                   (4)
mypage/     RideCompleteScreen                                                 (1)
onboarding/ OnboardingSavingScreen · OnboardingTrustScreen · OnboardingSafetyScreen (3)
```

`Modifier.align(Alignment.CenterHorizontally)` 를 넘기던 4곳(auth 계열)은 목업이 고정폭 Box 였기
때문이고, `NavigationBarSpacer` 는 `fillMaxWidth` 라 인자 없이 호출한다.

### 검증

```
./gradlew :presentation:compileDebugKotlin --rerun-tasks
BUILD SUCCESSFUL — 경고 2건은 기존 것 (AccountTypeScreen quadraticBezierTo, MyRidesScreen 상수 조건)
./gradlew :presentation:compileDebugKotlin :app:assembleDebug   BUILD SUCCESSFUL
```

grep: `135.dp` · `HomeIndicator` 잔존 **0건** (presentation·designsystem 전체)

실기 스팟 체크 6곳 — 스크린샷 `_workspace/screenshots/bottombar2_*.png`

| # | 화면 | 결과 |
|---|------|------|
| 01 | 01 온보딩(절약) | 가짜 바 없음 · 「다음」 CTA 는 원래 위쪽이라 여유 |
| 02 | 02 로그인 | 가짜 바 없음 · 하단 약관 문구가 제스처 바 위에서 끝남 |
| 03 | 15 목적지 검색 | 「경로 확인하기」 CTA 아래 인셋 여백 — 제스처 바와 안 겹침 |
| 04 | 16 도착지 확인 모달 | CTA 아래 20dp 여백 후 다이얼로그 끝, 그 아래 스크림+실제 인디케이터. **이중 바 사라짐**, 이중 여백도 없음 |
| 05 | 20 탑승 상세 | 「나가기 / 이 인원으로 출발」 아래 흰 배경이 인셋까지 이어지고 제스처 바와 안 겹침 |
| 06 | 23 합류 확인 | 「닫기 / 이 탑승에 합류하기」 아래 여백 정상 |

미검증(진입에 서버 쓰기가 필요해 제외): 21 매칭 대기 · 25 배차 · 26 운행 중 · 27 신고 · 결제 계열.
이 중 배경을 넘긴 3곳(21·22·25)은 목업이 칠하던 배경색을 그대로 modifier 로 옮긴 것이라
렌더 결과가 이전과 동일하고, 나머지는 배경 없는 단순 치환이다.

## 3차 — 목업이 없던 하단 CTA 화면 10개

애초에 가짜 인디케이터조차 없어 인셋이 **아예 없던** 화면들. 하단 CTA 블록이 화면 바닥에서
12dp 만 띄우고 끝나 제스처 영역과 겹친다.

10곳 모두 구조가 같았다 — 루트 Column 의 마지막 자식이
`Column/Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))` 이고 그 안에
CTA + 안내 문구가 들어 있다. **기존 12dp 여백은 그대로 두고 그 블록 뒤에 `NavigationBarSpacer()`
한 줄만 추가**했다(16 모달에서 쓴 기준 — 여백 관행 유지, 인셋만 덧대기).

| 화면 | 삽입 위치 |
|------|-----------|
| 06 학교 이메일 · 07 인증 코드 · 08 본인 인증 · 09 재직 인증 | CTA 블록 뒤 (루트 Column 마지막) |
| 29 최종 요금 · 30 정산 · 31 결제 수단 · 32 결제 추가 · 33 결제 결과 | 〃 |
| 핀 조정(PinAdjustOverlay) | 하단 확정 패널 **안쪽** CTA 아래 — 패널이 흰 배경+라운드 클립이라 배경이 인셋까지 이어져야 한다 |

### 검증

```
./gradlew :presentation:compileDebugKotlin :app:assembleDebug --console=plain   BUILD SUCCESSFUL
./gradlew :presentation:compileDebugKotlin --rerun-tasks   경고 2건은 기존 것
```

`PrimaryCtaButton`/`SecondaryButton` 을 쓰면서 `NavigationBarSpacer` 가 없는 화면 **0건**.

실기 스팟 체크 — 스크린샷 `_workspace/screenshots/bottombar3_*.png`

| # | 화면 | 결과 |
|---|------|------|
| 01 | 05 계정 유형 | 「나중에 인증할게요」 아래 여유 — 겹침 없음 (2차 처리분 회귀 확인) |
| 02 | 06 학교 이메일 | 「인증 정보는 매칭 확인 외에…」 푸터가 제스처 바 위에서 끝남 |
| 03 | 07 인증 코드 | 「코드는 10분 뒤에 만료돼요」 푸터 정상 |
| 04 | 핀 조정 오버레이 | **다이얼로그 윈도우 안이라 인셋 0** — Spacer 가 0dp 가 되고 패널 자체 16dp 여백만 남는다. 이중 여백 없음, 실제 인디케이터는 스크림 위에 따로 있어 겹치지 않음 |

08 본인 인증 · 09 재직 인증은 05 계정 유형에서 「일반」/「직장인」을 고르는 같은 분기이고 06·07 과
동일한 CTA 블록 구조라 별도 확인은 생략했다. 결제 5화면(29~33)은 진입에 정산까지 가는 서버 쓰기가
필요해 미검증 — 삽입 위치가 auth 4곳과 완전히 동일한 패턴이다.

## 남은 것

이제 목업이 전부 없어졌고 하단 CTA 화면에도 인셋이 다 들어갔으므로,
`StatusBarSpacer` / `NavigationBarSpacer` 두 개가 시스템 바 여백의 유일한 경로다.
새 화면은 이 둘만 쓰면 된다.
