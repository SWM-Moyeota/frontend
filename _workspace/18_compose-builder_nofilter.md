# 18 · 합승 탭 필터 칩 제거 (compose-builder)

## 작업 요약
합승 탭(17·18·19 · V07/V07b/V07c)의 필터 칩 행 「서면 방향 ✓」「여성만」「3인」「곧 출발」을
PEEK/HALF/FULL 세 상태 모두에서 제거하고, 칩에 매달려 있던 상태·필터링 로직·문구 전제를 함께 걷어냈다.
목록을 결정하는 것은 지도 범위 조회(`onVisibleBoundsChange`)뿐이므로 칩은 아무것도 바꾸지 못하는 장식이었다.

## 변경 파일
- `presentation/src/main/kotlin/com/moyeota/presentation/feature/explore/ExploreScreen.kt` (단일 파일)
- data/·domain/·ExploreRoute.kt 무수정 (필터 상태는 전부 Screen 안에만 있었다)

## 제거한 것
| 대상 | 내용 |
|------|------|
| 칩 라벨 상수 5개 | `FilterNearOrigin` / `FilterSeomyeon` / `FilterFemaleOnly` / `FilterTrio` / `FilterSoon` |
| 필터 로직 | `applyFilters()`, `departureMinutes()` |
| 상태 | `var filters`, `toggleFilter`, `visibleParties` (→ `parties` 직결) |
| 컴포넌트 | `FilterChip()` (elevated 변형 포함) |
| 파라미터 | `PeekContent`/`HalfContent`/`FullContent` 의 `filters`, `onToggleFilter` |
| 칩 행 | PEEK 오버레이 Row · HALF 시트 Row · FULL 헤더 Row |

`FemaleOnlyRideIds` 는 **남겼다** — 필터가 아니라 카드의 「여성만」 배지 표시용이다.
상단 「진행 중 탑승 · 서면역 방향 · 보기 ›」 배너도 요구대로 유지.

## 문구 교체 (방향 필터 전제 → 지도 범위 기반)
| 위치 | 라인 | Before | After |
|------|------|--------|-------|
| PEEK 시트 제목 | 393 | 이 방향으로 N명이 대기 중 | **주변에 N명이 대기 중** |
| PEEK 시트 제목(빈 상태) | 393 | 지금 이 방향엔 대기가 없어요 | **이 근처엔 대기가 없어요** |
| PEEK 시트 부제(빈 상태) | 399 | 필터를 완화하면 후보가 늘어나요 | **지도를 움직여 보세요** |
| HALF 목록 헤더 | 469 | 반경 1km 내 · 가까운 순 | **주변 합승 · 가까운 순** |
| FULL 목록 헤더 | 518 | 반경 1km 내 N명 · 가까운 순 | **주변 N명 · 가까운 순** |
| 목록 끝 | 572 | 이 방향은 여기까지예요 | **이 근처는 여기까지예요** |
| 빈 목록 제목 | 788 | 조건에 맞는 합승이 없어요 | **이 근처엔 합승이 없어요** |
| 빈 목록 부제 | 794 | 필터를 완화해 보세요 | **지도를 움직여 보세요** |

「반경 1km」는 「출발지 1km」 칩을 전제한 표현이라 함께 걷어냈다.
줌 상수 주석(`ExploreZoom`)도 「반경 1km 내 후보」 → 「첫 범위 조회가 훑는 범위」로 고쳤다.

## 여백 정리 (일괄 규칙)
칩이 빠지면서 헤더-리스트 간격이 화면마다 다른 여백 조각의 합(HALF 12+14 / FULL 10+14)으로 남았다.
`ListHeaderGap = 12.dp` (185행) 하나로 묶어 HALF·FULL이 같은 리듬을 갖게 했다.

- **PEEK**: 오버레이가 배너 하나뿐이라 감싸던 `Column`과 배너 뒤 `Spacer(18.dp)`를 제거.
  `OngoingRideBanner`에 `modifier` 파라미터를 추가해 padding(h20/v4)만 넘긴다 —
  오버레이가 자기 높이(48dp)만 덮고 나머지 팬·줌은 그대로 지도로 간다.
- **HALF**: 헤더 → `ListHeaderGap` → 리스트
- **FULL**: 헤더 → `ListHeaderGap` → 구분선 → `ListHeaderGap` → 리스트

## 건드리지 않은 것
범위 조회(`MapIdleReporter` / `toMapBoundsOrNull` / `defaultExploreBounds`), 마커 렌더·탭,
카메라 상태 유지(`ExploreCameraState`), 내 위치 오버레이, 시트 3단계 전환, 폴링(`ExploreRoute`).

## 검증
### 빌드
```
./gradlew :presentation:compileDebugKotlin :app:assembleDebug --console=plain
→ BUILD SUCCESSFUL
```

### 에뮬레이터 실기 (emulator-5554 · 1080x2400 · 백엔드 localhost:8080 방 1건 seeded)
진입 경로: 온보딩 「건너뛰기」 → 로그인 → 홈 → 하단탭 **합승**

| # | 스크린샷 | 확인 |
|---|----------|------|
| 1 | `nofilter_01_peek.png` | PEEK — 칩 없음 · 배너 유지 · 마커 「1명」 · 「주변에 1명이 대기 중」 |
| 2 | `nofilter_02_half.png` | HALF — 칩 없음 · 「주변 합승 · 가까운 순」 · 카드 1건 정상 |
| 3 | `nofilter_03_full.png` | FULL — 칩 없음 · 「주변 1명 · 가까운 순」 · 「이 근처는 여기까지예요」 |
| 4 | `nofilter_04_pan_empty.png` | 부곡동까지 팬 → 범위 재조회 → 목록 0건 · 「이 근처엔 합승이 없어요 / 지도를 움직여 보세요」 |
| 5 | `nofilter_05_pan_back.png` | 부산대로 팬백 → 카드·마커 복귀 (범위 조회 회귀 없음) |
| 6 | `nofilter_06_marker_join.png` | 마커 탭 → 20 합류 확인 정상 진입 |
| 7 | `nofilter_07_peek_empty.png` | PEEK 빈 상태 — 「이 근처엔 대기가 없어요 / 지도를 움직여 보세요」 · 배너 유지 |

서버·실기기는 건드리지 않았다 (백엔드는 이미 떠 있던 프로세스 그대로 조회만).
