# 17 · 가짜 상태바(StatusBarMock) 제거

## 배경
와이어프레임에서 따라온 상태바 목업(`StatusBarMock` — "6:42" + 우측 검은 박스 3개, 높이 44dp)이
실제 시스템 상태바와 이중으로 보인다는 사용자 피드백.

## 처리 방식 — 삭제가 아니라 "실제 인셋으로 교체"

먼저 앱의 edge-to-edge 여부를 확인했다.

- `app/src/main/kotlin/com/moyeota/app/MainActivity.kt:23` → `enableEdgeToEdge()` **사용 중**
- 코드베이스 전체 grep 결과 `statusBarsPadding` / `systemBarsPadding` / `WindowInsets` **사용처 0건**

즉 앱은 상태바 뒤까지 그려지는데 인셋 처리가 어디에도 없었고, **44dp 목업이 사실상 상태바 인셋
역할을 겸해 왔다**. 그래서 목업을 그냥 지우면 36개 화면 전부에서 콘텐츠가 시스템 상태바 아래로
파고든다.

따라서 목업의 "겉모습"만 걷어내고 "자리"는 실제 인셋으로 대체했다.

`core/designsystem/.../component/Bars.kt`

```kotlin
@Composable
fun StatusBarSpacer(modifier: Modifier = Modifier) {
    Spacer(modifier = modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars))
}
```

- `StatusBarMock` 은 Bars.kt 에서 **삭제**. 안 쓰게 된 `FontWeight` import 정리.
- 36개 사용처는 호출부 위치·구조 그대로 두고 이름만 `StatusBarMock()` → `StatusBarSpacer()` 로
  일괄 치환. 각 화면의 컨테이너 구조·배경을 건드리지 않았으므로 배경색은 이전과 동일하게
  상태바 뒤까지 이어진다.
- 루트 modifier 에 `.statusBarsPadding()` 을 다는 대신 같은 자리의 인셋 Spacer 를 쓴 이유:
  36개 화면마다 "어느 컨테이너가 루트인지"를 개별 판단해야 하는 리스크를 없애고, 배경이
  상태바 뒤까지 칠해지는 현재 렌더링을 그대로 보존하기 위함. 효과는 동일하다.

### 화면별 spacing 보정
**하지 않았다.** 목업 바로 아래 요소들은 이미 자체 여백(`Spacer(24.dp)`, `padding(vertical = 8.dp)`,
`MoyeotaTopBar` 56dp 등)을 갖고 있어, 44dp → 상태바 인셋(≈24dp) 으로 줄어도 답답해지지 않는다.
실기 스크린샷으로 6개 유형 확인 완료(아래). 개별 리디자인 없음.

### 모달(16 도착지 확인)
`dialog(...)` 로 띄우는 화면이라 Dialog 윈도우가 이미 시스템 윈도우에 맞춰져 있고,
그 안에서 `WindowInsets.statusBars` 는 0 을 반환한다 → Spacer 가 0dp 가 되어 이중 여백이
생기지 않는다. 실기에서 확인했다.

## 변경 파일 (37개)
- `core/designsystem/src/main/kotlin/com/moyeota/core/designsystem/component/Bars.kt` (컴포넌트 교체)
- `presentation/src/main/kotlin/com/moyeota/presentation/` 하위 **36개** 화면/스캐폴드 파일
  (`core/LoadState.kt` 의 `TabStateScaffold`·`BackStateScaffold` 포함 — 이 둘이 하단탭 4화면과
  뒤로가기 화면들의 로딩·에러 골격을 담당)

data/ · domain/ 수정 없음.

## 검증

### 빌드
```
./gradlew :presentation:compileDebugKotlin :app:assembleDebug --console=plain
BUILD SUCCESSFUL in 7s
```
(경고 2건은 기존 것 — AccountTypeScreen quadraticBezierTo deprecated, MyRidesScreen 상수 조건)

### grep
`StatusBarMock` 잔존 **0건** (남은 "6:42" 문자열은 ChatScreen 더미 메시지의 타임스탬프로 무관)

### 에뮬레이터 실기 (emulator-5554, Pixel_6 콜드부트, 1080x2400 / density 420)
서버·실기기 미접촉. 스크린샷 `_workspace/screenshots/statusbar_*.png`

| # | 화면 | 가짜 상태바 | 시스템 상태바 겹침 |
|---|------|------------|-------------------|
| 01 | 온보딩(절약) | 없음 | 없음 — 「건너뛰기」가 상태바 아래 자체 여백만큼 띄워짐 |
| 02 | 로그인 | 없음 | 없음 — "모여타" 로고가 24dp 여백 아래 |
| 03 | 홈(네이버 지도) | 없음 | 없음 — 지도만 상태바 뒤로 이어짐(edge-to-edge 의도된 모습), 상태바 아이콘 가독 정상 |
| 04 | 합승 탭 | 없음 | 없음 — "합승 – 내 주변" 헤더 정상 |
| 05 | 15 목적지 검색 | 없음 | 없음 — 뒤로가기+타이틀 앱바 정상 |
| 06 | 16 도착지 확인 모달 | 없음 | 없음 — 스크림이 상태바를 덮고 모달 헤더가 그 아래에서 시작, 이중 여백 없음 |

## 참고 (이번 작업 범위 밖)
하단 `MoyeotaBottomBar` 는 제스처 네비게이션 인디케이터와 겹친다. 이번 변경 이전부터 있던
상태이며(내비게이션바 인셋 처리도 원래 0건), 상태바 과제와 분리해 두었다. 필요하면 같은 방식으로
`navigationBarsPadding()` 을 붙이는 후속 작업으로 처리 가능.
