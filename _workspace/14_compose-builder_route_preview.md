# 14. 16 도착지 확인 — 경로 미리보기 실값 연결

작업일: 2026-09-01 / 담당: compose-builder
선행 산출물: `_workspace/13_api-integrator_routes.md` (POST /matching/routes 봉인 해제)

## 1. 한 일

16 도착지 확인 모달의 하드코딩 데모값(`예상 12분 · 6.2km`, `1인 2,400원`, `오후 6:45 → 6:57`)을
`RideRepository.previewRoute` 실응답으로 교체하고, 마커만 있던 지도에 경로 폴리라인을 얹었다.

**data/·domain/ 무수정.** 시그니처는 13번 리포트대로 변경 없음 — presentation 에서 부르기만 했다.

## 2. 변경 파일

| 파일 | 라인 | 변경 |
|---|---|---|
| `presentation/.../feature/home/DestinationConfirmModal.kt` | 81 | `RoutePreviewUi` 신설 — `loading` / `estimatedMinutes` / `estimatedFare` / `encodedPath` |
| | 128 | 파라미터 교체: `routeSummaryLabel: String` · `estimatedTotalFare: Int` 삭제 → `routePreview: RoutePreviewUi` |
| | 148 | 1인 요금 = 총액 ÷ 인원, **10원 단위 반올림** (기존 FareFinal 컨벤션 유지). 값 없으면 null |
| | 153 | 요약 칩 문구 재구성 — `예상 39분 · 총 13,500원` (거리는 응답에 없어 시간·총요금 2값으로) |
| | 160 | `decodePolyline` (25 배차 화면과 같은 디코더) 로 `encodedPath` → `List<LatLng>` |
| | 207–229 | `Box` → `BoxWithConstraints` 로 바꿔 뷰포트 dp 를 얻고, `fitCamera` 결과를 `RouteMapView` 의 `center`/`zoom` 으로 전달. `routePath` 도 함께 전달 |
| | 481–486 | 하단 요금 문구 3상태 (`계산 중이에요` / 실값 / `—`) |
| | 556–597 | `fitCamera` + `WORLD_TILE_DP` / `MARKER_INSET_DP` 헬퍼 |
| `presentation/.../feature/home/DestinationConfirmRoute.kt` | 98–158 | `RoutePreviewViewModel` 신설 (좌표 StateFlow → `flatMapLatest` → Idle/Loading/Success/Error) |
| | 189–203 | 진입·좌표 변경 시 `preview()` 호출, 출발·도착 시각 라벨 계산 |
| | 248 | `clockLabel(minutesFromNow)` — minSdk 24 라 `java.time` 대신 `Calendar` |
| `presentation/.../feature/matching/DispatchStatusScreen.kt` | 135–136 | 낡은 주석 정정 (`GET+body 라 호출 불가` → 방 상세 경로를 쓰는 이유) |
| `presentation/.../feature/home/DestinationConfirmModal.kt` | 217–224 | 같은 낡은 주석 삭제 (기존 173–174행) |

**화면 진입 경로**: 홈 → 목적지 검색(15) → 장소 선택 → 「경로 확인하기」 → **16 도착지 확인**

## 3. 설계 판단 3가지

### 3-1. 연타 취소는 `flatMapLatest`
좌표를 `MutableStateFlow<Query?>` 로 들고 `flatMapLatest` 로 요청 흐름을 만든다. 핀 조정 확정을
연타하면 **직전 호출이 취소**되어, 늦게 끝난 옛 응답이 최신 값을 덮어쓰는 일이 없다.
`Query` 가 data class 라 같은 좌표로는 재발행되지 않는다 — 리컴포지션마다 `LaunchedEffect` 가
다시 돌아도 네트워크는 한 번만 나간다(실기 확인: 인원 칩을 바꿔도 POST 는 1건 유지).

### 3-2. 실패는 배너가 아니라 침묵
미리보기는 부가 정보다. 실패 시 칩을 감추고 도착시각·요금을 `—` 로 두되 **CTA 는 살려둔다** —
방 생성 요청을 받으면 서버가 어차피 요금·경로를 다시 계산하므로 여기서 막을 이유가 없다.

### 3-3. 카메라는 값 계산, 명령형 이동 금지 — 그리고 **네이버 줌은 512dp 타일**
`CameraUpdate.fitBounds` 는 `NaverMap` 을 직접 잡아 명령형으로 움직인다. `RouteMapView` 는
center/zoom 을 선언형으로 받고 사용자 카메라를 보존하는 구조라, 섞으면 주인이 둘이 된다.
그래서 웹 메르카토르로 **값만 계산**해 넘긴다.

여기서 한 번 헛디뎠다 — 구글/OSM 통례인 `256px` 타일로 계산했더니 화면이 2.5 단계 넘게 당겨져
경로가 위아래로 잘렸다. `NaverMap.contentBounds` 를 실기에서 찍어 역산한 결과:

```
줌 12.11 · 뷰포트 411dp 폭 → 경도 0.065° 를 덮음
→ 세계 지도 폭 = 411 / (0.065/360) / 2^12.11 ≈ 512dp
```

네이버는 타일이 두 배 크고 **dp 기준**이라 같은 축척을 구글 줌 z+1 로 표현한다.
`WORLD_TILE_DP = 512.0` 으로 잡아야 맞는다 (`DestinationConfirmModal.kt:597` 에 근거 주석).
마커 좌표는 아이콘의 **꼭짓점**이라 아이콘 몸통·캡션이 그 밖으로 뻗으므로 사방 38dp 를 비운다.

## 4. 검증

### 빌드
```
./gradlew :presentation:compileDebugKotlin :app:assembleDebug --console=plain
BUILD SUCCESSFUL
```

### 에뮬레이터 실기 (emulator-5554, 콜드부트, 서버 localhost:8080 유지)

검색(IME 영문 전환 keyevent 204, "CGV") → CGV 서면 선택 → 16 진입.

| 확인 항목 | 결과 | 스크린샷 |
|---|---|---|
| 실요금·시간·경로 폴리라인 | `예상 39분 · 총 13,500원`, 폴리라인 + 출발/도착 마커가 모두 스트립 안에 | `preview_01_route_fare.png` |
| 출발–도착 시각 | `오후 5:44` → `오후 6:23 도착` (현재시각 + 39분) | 위와 동일 |
| 1인 요금 | 3인 4,500원 / **2인 6,750원** (13,500 ÷ 2, 10원 반올림) — 칩 변경 시 재계산, **추가 API 호출 없음** | `preview_02_capacity2.png` |
| 로딩 자리표시 | 칩 `예상 경로 계산 중` · 도착 `도착 계산 중` · 하단 `예상 요금 계산 중이에요` | `preview_03_recalc_loading.png` |
| 핀 조정 후 갱신 | 도착지 35.1493 → 35.0918 (약 6km 남) 확정 → 재호출 → `50분 · 24,600원`, 1인 12,300원, 카메라 재적합 | `preview_04_after_pin_adjust.png` |
| 실패 처리 | 비행기모드에서 재호출 → 칩 숨김, 도착 `—`, 요금 `—`, **크래시 없음(FATAL 0건)**, CTA 정상 | `preview_05_offline_fallback.png` |
| 방 생성 회귀 | 「같이 탈 사람 찾기」 → 21 매칭 대기 정상 진입 | `preview_06_create_party.png` |

### OkHttp 로그 (실호출 증거)
```
--> POST http://10.0.2.2:8080/api/v1/matching/routes
{"departureLat":35.2312984,"departureLng":129.0837973,
 "destinationLat":35.1492841347715,"destinationLng":129.063546082192}
<-- 200 (1309ms)
{"estimateFare":13500,"estimateTime":39,"path":"ub`vEwtzrW?BQxA@JDNFRJ…"}
```
핀 조정 후 두 번째 호출도 확인 (`destinationLat":35.09175…` → `estimateFare":24600,"estimateTime":50`).

## 5. 남은 것 / qa-verifier 요청

- **`walkLabel`("도보 2분 · 180m")은 여전히 더미** — 도보 구간을 계산할 API 가 없다.
  가상 정류장(주요 승차지점)이 정해지는 순간 실값이 생기므로 그때 같이 처리.
- **요금이 호출마다 흔들린다** — 같은 좌표인데 13,800 → 13,500 → 13,400 원. 네이버 실시간
  교통 반영이라 정상으로 보이지만, 사용자가 칩만 바꿔도 총액이 바뀌는 것처럼 보이지는 않는지
  (지금 구조에선 재호출을 안 하므로 안 바뀐다) 교차 확인 요청.
- **`fitCamera` 는 16 전용 private 헬퍼** — 25 배차 화면도 같은 fit 이 필요해지면
  `core/designsystem` 으로 승격 대상. 2개 화면이 될 때 옮긴다(승격 규칙).
