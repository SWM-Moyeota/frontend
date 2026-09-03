# 04 · compose-builder — 16 도착지 확인 모달 실지도 교체

## 작업
16 도착지 확인 모달의 지도 영역이 `MapPlaceholder`(목업)라 실지도가 보이지 않던 문제를,
홈(01)·배차(25)와 같은 네이버 실지도로 교체했다.

## 화면 진입 경로
홈(01) → 목적지 검색(15, `Routes.DESTINATION`) → 장소 선택 →
16 모달(`Routes.DESTINATION_CONFIRM`, `MainNavGraph.kt:254` `dialog()` 목적지)

## 변경 파일

| 파일 | 라인 | 내용 |
|------|------|------|
| `presentation/.../feature/home/DestinationConfirmModal.kt` | 105–106 | 파라미터 `destinationPosition: LatLng?` · `originPosition: LatLng?` 추가 (기본 null) |
| | 166–182 | 지도 영역: `destinationPosition != null` 이면 `RouteMapView`, 아니면 기존 `MapPlaceholder` |
| | 48, 51, 57 | import (`RouteMapView`, `LatLng`) — `MapPlaceholder` import 유지 |
| `presentation/.../feature/home/DestinationConfirmRoute.kt` | 101–102 | `latLngOrNull(...)` 로 도착지·출발지(DemoOrigin) 좌표 전달 |
| | 12 | import `latLngOrNull` |
| `core/designsystem/.../component/NaverMapView.kt` | 48–52, 61, 77–79 | `useTextureView: Boolean = false` 옵션 추가 → `MapView(context, NaverMapOptions().useTextureView(...))` |
| `core/designsystem/.../component/RouteMapView.kt` | 95, 107, 123 | `useTextureView` 파라미터 pass-through |

## 결정 사항

1. **`RouteMapView` 재사용 (신규 컴포넌트 없음)**
   마커 2개 + 좌표 검증(`latLngOrNull`)이 이미 배차 25에서 검증된 형태라 그대로 썼다.
   `routePath` 는 비운다 — 이 시점엔 방이 없어 서버 폴리라인이 없고 `previewRoute` 는
   GET+body 라 앱에서 호출 불가(봉인). 2점 미만이면 `RouteMapView` 가 폴리라인을 그리지 않는다.

2. **카메라 중심 = 도착지, zoom 15.0**
   확인 대상이 도착지이므로 중심은 도착지로 고정. 160dp 스트립에서 주변 블록이 식별되는
   수준으로 기본값(14.0)보다 한 단계 당겼다.

3. **출발지 마커 포함**
   `DemoOrigin`(부산대 정문)과 도착지가 멀면 화면 밖이라 안 보이지만, 근거리 도착지에서는
   두 지점 관계가 한눈에 들어온다. 마커 색은 `MoyeotaColor.MarkerOrigin/MarkerDestination`.

4. **좌표 검증은 `latLngOrNull` 에 위임**
   서버 위경도 전치 결함(QA D-1) 대비. 범위를 벗어난 값이면 null → 엉뚱한 지점을 비추는 대신
   기존 `MapPlaceholder` 로 떨어진다. 프론트에서 좌표를 swap 하지 않는다(백엔드 수정 시 재역전).

5. **다이얼로그 목적지 → TextureView 렌더**
   16은 `dialog()` 목적지라 지도가 별도 윈도우에 올라간다. SurfaceView 는 자기 윈도우에
   구멍을 뚫는 방식이라 다이얼로그 위에서 검게 비거나 스크림에 가려질 수 있어,
   `NaverMapOptions.useTextureView(true)` 로 일반 뷰 합성 경로를 쓴다.
   홈·배차 등 전면 화면은 기본값 false 유지 — 렌더 비용이 더 큰 경로라 필요한 곳만 켠다.

6. **레이아웃 불변**
   지도 영역 높이 160dp, 위에 얹힌 예상 시간 칩(`routeSummaryLabel`)의 위치·스타일 그대로.
   생명주기는 `NaverMapView` 의 기존 `DisposableEffect` 패턴(홈·배차와 동일)에 그대로 올라탄다.

## 빌드
`./gradlew :presentation:compileDebugKotlin :app:assembleDebug --console=plain` → **BUILD SUCCESSFUL**

## QA 확인 요청 포인트
- 16 진입 시 지도 타일이 실제로 그려지는지(다이얼로그 윈도우 + TextureView 조합 실기 확인)
- 도착지 마커가 중심에 오는지, 예상 시간 칩과 겹치지 않는지
- 모달 닫기·재진입 반복 시 지도 누수/검은 화면 없는지
- 15를 거치지 않은 진입(도착지 null)에서 기존 placeholder + 안내 문구가 유지되는지
