# 20 · compose-builder — 홈 화면 데모 요소 정리

작업일: 2026-09-01 / 브랜치: feature/naver-map

## 요청
홈(14 · S09)의 하드코딩 데모 요소 2건 제거 + 죽은 배선 정리 + 시트 간격 보정.
data/·domain/ 무수정.

## 변경 파일

### 1. `presentation/src/main/kotlin/com/moyeota/presentation/feature/home/HomeScreen.kt`

| 제거 대상 | 원래 위치 | 조치 |
|---|---|---|
| 공지 배너 행 ("현재 1.0.0 버전이 업데이트 되었습니다." + 벨 아이콘 + 붉은 점 배지 + 쉐브론) | 구 `:254~293` (HeroSection 선두 Row) | Row 전체 삭제 |
| 수요 카드 ("지금 8명이 같이 탈 사람을 찾고 있어요 / 서면·사상 방향이 많아요" + 아바타 3개 + 쉐브론) | 구 `:354~392` | Row 전체 + 앞뒤 Spacer 1개 삭제 |

죽은 배선 정리:
- `HomeScreen` 파라미터 `searchingCount: Int = 8`, `onDemandBannerClick`, `onNoticeClick` 삭제
- `HeroSection` 파라미터 `searchingCount`, `onDemandBannerClick`, `onNoticeClick` 삭제
- `BellIcon()` private Composable 삭제 (공지 배너 전용)
- `ChevronIcon()` private Composable 삭제 (공지 배너 + 수요 카드에서만 사용)
- 미사용 import 제거: `androidx.compose.foundation.layout.offset`, `com.moyeota.core.designsystem.component.AvatarCircle`
- KDoc에서 "수요 배너 → 17 합승 지도" 항목 삭제

간격 보정 (현재 `:243`):
- HeroSection 선두에 `Spacer(Modifier.height(18.dp))` 유지 → 시트 핸들 Box의 하단 8dp와 합쳐 인사말 위 26dp. 제거 전 (핸들 8 + 배너 top 12 + 벨 38 + 18) 대비 시각적 리듬 유지.
- HeroSection 말미 `Spacer(Modifier.height(20.dp))` 유지 → 바깥 `Spacer(24.dp)`와 합쳐 검색바~「자주 가는 곳」 44dp. 제거 전 수요 카드~「자주 가는 곳」 간격(20+24=44)과 동일.
- 개별 리디자인 없음. 폰트/색/그림자/카드 반경 무변경.

### 2. `presentation/src/main/kotlin/com/moyeota/presentation/feature/home/HomeRoute.kt`
- `HomeRoute` 파라미터 `onDemandBannerClick: () -> Unit = {}` 삭제 (`:51`)
- `HomeScreen(...)` 호출부의 `onDemandBannerClick = onDemandBannerClick` 인자 삭제

### 3. `presentation/src/main/kotlin/com/moyeota/presentation/core/MainNavGraph.kt`
- `Routes.HOME` composable 내 `onDemandBannerClick = { navigateTab(MoyeotaTab.EXPLORE) }` 삭제 (구 `:240`)
- 합승 탭 이동은 `MoyeotaBottomBar` → `onTabSelect = ::navigateTab` 경로로 그대로 유지되므로 대체 배선 불요

Routes 상수 추가/삭제 없음. NavGraph 등록 화면 수 변동 없음.

## 검증

빌드
```
./gradlew :presentation:compileDebugKotlin :app:assembleDebug --console=plain
BUILD SUCCESSFUL in 2s
```

에뮬레이터 실기 (emulator-5554, 1080x2400) — 서버·실기기 미접촉

진입 경로: 온보딩 「건너뛰기」(948,186) → 로그인 「로그인」(685,1988) → 홈

| 스크린샷 | 확인 내용 |
|---|---|
| `_workspace/screenshots/home_clean_01_home.png` | 홈 진입 — 공지 배너·수요 카드 모두 사라짐. 핸들 → 인사말 → "어디로 갈까요?" → 서브카피 → 목적지 검색바 → 자주 가는 곳 → 최근 목적지 순으로 붙음 |
| `home_clean_02_sheet_scrolled.png` | 시트 스크롤 — 최근 목적지 3행까지 잘림 없이 노출, 하단탭과 겹침 없음 |
| `home_clean_03_search.png` | 검색바 탭 → 15 목적지 화면 정상 진입 (회귀 없음) |
| `home_clean_04_explore_tab.png` | 뒤로가기 후 하단탭 「합승」 → 17 합승 지도 정상 진입 (제거된 카드의 목적지를 탭바가 대체) |
| `home_clean_05_back_home.png` | 하단탭 「홈」 복귀 정상 (스크롤 위치 보존) |

`adb logcat -s AndroidRuntime:E` 출력 없음 — 크래시 없음.

참고: 「자주 가는 곳」이 빈 상태 문구로 보이는 것은 백엔드 즐겨찾기 미시드에 따른 기존 동작이며 이번 변경과 무관. 3-카드 Row 코드는 미수정.
