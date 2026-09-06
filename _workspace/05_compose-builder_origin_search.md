# 05 · compose-builder · 15 목적지 화면 출발지 검색 연결

## 요구
15 목적지 화면의 출발지가 `DemoOrigin`("부산대학교 정문") 고정 → 도착지와 동일한 장소 검색으로 교체 가능하게.
선택한 출발지가 16 확인 모달(이름 표기 + 지도 출발 마커)과 `createParty(origin, …)` 좌표까지 흘러가야 함.

## 변경 파일

### 1. `presentation/.../feature/home/DestinationScreen.kt`
| 라인 | 변경 |
|------|------|
| 56–57 | `enum class DestinationField { ORIGIN, DESTINATION }` 신설 — 두 필드가 하나의 검색 UI를 공유 |
| 78–106 | 파라미터 추가: `originIsDefault`, `originQuery`, `activeField`, `onFieldFocus`. `onOriginClick`(미연결 stub) 제거 |
| 107–111 | `originActive` / `activeMarkerColor`(출발지=Primary500, 도착지=MarkerDestination) 파생 |
| 130–176 | 출발지·도착지 행을 공통 `PlaceFieldRow`로 통일. 출발지 활성 시 `SearchInput`("출발지를 검색해 주세요") + 스피너, 비활성 시 이름 텍스트 + 라벨(`originIsDefault` → "현재 위치", 아니면 "변경") |
| 165–176 | 도착지 행: 출발지 검색 중에는 `enabled = false` → 탭하면 포커스만 이동(부모 clickable이 받음) |
| 302–311 | 섹션 헤더가 활성 필드 기준: "출발지 검색 결과" / "검색 결과" / "최근 검색" |
| 344 | 검색 결과 행 도트 색 `activeMarkerColor` |
| 460–487 | `PlaceFieldRow` 신설 — 포커스 시 1.5dp `Primary500` 테두리, 비포커스 1dp `Hairline`(기존 파랑 포커스 컨벤션 유지). 포커스 필드는 그림자 제거 |
| 489–521 | `SearchInput` / `FieldSpinner` private 헬퍼 (도착지 입력 로직을 그대로 추출, 양쪽 필드 공용) |

단일 화면 전용이므로 designsystem 승격 없이 화면 파일 내 private Composable로 유지.
색상은 전부 `MoyeotaColor` 토큰(신규 하드코딩 hex 없음; `Color(0x1A1B2A4A)`은 기존 그림자 spotColor 그대로).

### 2. `presentation/.../feature/home/DestinationRoute.kt`
| 라인 | 변경 |
|------|------|
| 39–56 | `UiState`에 `activeField`(기본 DESTINATION), `originQuery`, `selectedOrigin: Place?` 추가. `val origin: Place get() = selectedOrigin ?: DemoOrigin` 파생 프로퍼티 |
| 86–96 | `onQueryChange`가 활성 필드의 검색어를 갱신 → `runSearch` 위임 |
| 99–110 | `focusField(field)` 신설 — 필드 전환 시 결과·로딩·에러 초기화 후 그 필드의 검색어로 재검색 |
| 112–131 | `runSearch(query)` — 기존 300ms 디바운스 + `PlaceRepository.searchPlaces` 로직을 그대로 추출(재사용, API 변경 없음) |
| 134–153 | `selectPlace`가 활성 필드로 분기. ORIGIN이면 `selectedOrigin` 교체 + `originQuery = place.name` + 활성 필드를 DESTINATION으로 자동 이동 후 도착지 검색어 재검색 |
| 176–177 | `onConfirmRoute: (Place, Place) -> Unit` — (출발지, 도착지) |
| 192–197 | Screen에 `origin = state.origin.name`, `originIsDefault = state.selectedOrigin == null`, `originQuery`, `activeField`, `onFieldFocus = viewModel::focusField` 전달 |
| 210 | `onConfirmRoute = { dest -> onConfirmRoute(state.origin, dest) }` |

`DemoOrigin`(27–32)은 기본 출발지 폴백으로 유지 — 아무것도 선택 안 하면 기존 동작 그대로.

### 3. `presentation/.../feature/home/DestinationConfirmRoute.kt`
| 라인 | 변경 |
|------|------|
| 82–83 | `origin: Place = DemoOrigin` 파라미터 추가 |
| 100 | `originStopName = origin.name` (was `DemoOrigin.name`) |
| 104 | `originPosition = latLngOrNull(origin.latitude, origin.longitude)` — 지도 출발 마커 |
| 110 | `viewModel.createParty(origin, destination, conditions)` → `NewParty.departureLat/Lng/departure`에 선택 좌표 반영 |

### 4. `presentation/.../core/MainNavGraph.kt`
| 라인 | 변경 |
|------|------|
| 37 | `import ...feature.home.DemoOrigin` |
| 74–77 | `var confirmedOrigin by remember { mutableStateOf<Place?>(null) }` 추가 |
| 250–254 | `onConfirmRoute = { origin, place -> confirmedOrigin = origin; confirmedDestination = place; navigate(DESTINATION_CONFIRM) }` |
| 263 | `origin = confirmedOrigin ?: DemoOrigin` 을 `DestinationConfirmRoute`에 전달 |

Routes 상수·신규 화면 추가 없음(기존 15/16 라우트 그대로).

## 상태 흐름

```
[15] DestinationViewModel.UiState
  activeField: ORIGIN | DESTINATION  (기본 DESTINATION)
  originQuery / query                (필드별 검색어, 검색 결과는 활성 필드 것 1벌)
  selectedOrigin: Place?             (null → DemoOrigin)
  selectedPlace: Place?              (도착지)

출발지 행 탭 → focusField(ORIGIN) → 결과 비움 → runSearch(originQuery)
타이핑        → onQueryChange → originQuery 갱신 → 300ms 디바운스 → searchPlaces(q)
결과/자주가는곳 탭 → selectPlace → selectedOrigin 교체 + activeField=DESTINATION + runSearch(query)
CTA          → onConfirmRoute(state.origin, selectedPlace)
                 ↓ MainNavGraph: confirmedOrigin / confirmedDestination
[16] DestinationConfirmRoute(origin, destination)
       originStopName = origin.name
       originPosition = latLngOrNull(origin.lat, origin.lng)   → 지도 출발 마커
       createParty(origin, destination, conditions)            → departureLat/Lng/departure
```

## 화면 진입 경로
14 홈 → 검색바/최근 목적지 탭 → **15 목적지**(`Routes.DESTINATION`) → 출발지 행 탭 → 검색·선택 → 도착지 검색·선택 → 「경로 확인하기」 → **16 확인 모달**(`Routes.DESTINATION_CONFIRM`) → 「동승자 찾기」 → 21 매칭 대기

## 범위 밖
- GPS 현재 위치 연동(기본 출발지는 여전히 `DemoOrigin` 상수)
- 최근 검색은 서버 API 부재로 더미 유지 — 출발지 모드에서도 검색어만 채운다
- data/·domain/ 무수정 (`PlaceRepository.searchPlaces` 재사용)

## 검증
`./gradlew :presentation:compileDebugKotlin :app:assembleDebug --console=plain` → **BUILD SUCCESSFUL**
