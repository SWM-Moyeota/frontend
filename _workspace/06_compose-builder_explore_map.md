# 06 · compose-builder — 23 합승-내 주변 지도 실지도 교체

**작업**: 17·18·19 합승 — 내 주변(합승 탭)의 목업 지도(`MapPlaceholder` + Canvas 도로/마커)를
네이버 실지도로 교체하고 내 위치 마커를 표시.

**검증**: `./gradlew :presentation:compileDebugKotlin :app:assembleDebug --console=plain` → BUILD SUCCESSFUL

**진입 경로**: 앱 실행 → 로그인 → 14 홈 → 하단탭 「합승」 → 17(PEEK, 전체 지도) ⇄ 18(HALF, 상단 320dp 지도) ⇄ 19(FULL, 리스트)

---

## 변경 파일

### `presentation/src/main/kotlin/com/moyeota/presentation/feature/explore/ExploreScreen.kt` (유일한 변경 파일)

| 라인 | 내용 |
|------|------|
| 54, 59–63 | import 추가 — `latLngOrNull`, `NaverMapView`, `DemoOrigin`, `LatLng`, `NaverMap`, `Marker`, `MarkerIcons` |
| (삭제) | `import MapPlaceholder`, `CornerRadius`, `Size`, `Dp` 제거 |
| 58(구) | `RoadColor` 상수 제거 (Canvas 도로 목업 전용이었음) |
| 101–116(구) → 109–111 | `MarkerSlot` / `PeekMarkerSlots` / `HalfMarkerSlots`(와이어프레임 하드코딩 좌표) 삭제 → 시트 높이 상수 `PeekSheetHeight`(96dp) · `HalfMapHeight`(320dp) · `HalfSheetTop`(300dp) 로 대체 |
| 136 | ExploreScreen KDoc 에 「지도는 네이버 실지도, 내 위치는 DemoOrigin 고정」 명시 |
| 248–252 | PEEK: `ExploreMap(rides, onMarkerClick, contentPadding = bottom PeekSheetHeight, fillMaxSize)` |
| 294 | PeekSheet 높이를 `PeekSheetHeight` 상수로 |
| 350–364 | HALF: `ExploreMap(..., contentPadding = bottom (HalfMapHeight - HalfSheetTop) = 20dp, height = HalfMapHeight)`, 시트 top padding·권한 안내 높이도 상수화 |
| 760–769 | 새 섹션 헤더 + `MyLocation = LatLng(DemoOrigin.latitude, DemoOrigin.longitude)` (부산대 정문 35.2313 / 129.0838) + `ExploreZoom = 15.0` |
| 783–818 | `ExploreMap` 재작성 — `NaverMapView(center = MyLocation, zoom = ExploreZoom, contentPadding)` 위에 마커만 얹는다 |
| 821–849 | `ExploreMarker` 신규(private) — `DisposableEffect` 로 `Marker` 부착/해제, `MarkerIcons.BLACK` + `iconTintColor`, 클릭 콜백 옵션 |
| (삭제) | `CountMarker`(Canvas 원형 카운트 배지), `MyLocationMarker`(파란 도트 목업) 제거 |

---

## 구현 노트

- **재사용**: `core/designsystem`의 `NaverMapView`(홈 14 와 동일한 기본 SurfaceView 모드 —
  탭 화면이라 `useTextureView` 불필요)와 `RouteMapView`의 `latLngOrNull` 좌표 검증 팩토리를 그대로 사용.
  designsystem 은 수정하지 않았다.
- **내 위치**: GPS·위치 권한 미연동 상태라 앱 공통 컨벤션인 `DemoOrigin`(부산대학교 정문,
  15 목적지 입력 화면과 동일 좌표)을 마커 + 카메라 중심으로 쓴다. 색은 `MoyeotaColor.MarkerOrigin`.
  **GPS 연동은 별도 작업으로 미룸** — 실제 위치가 붙으면 `MyLocation`(ExploreScreen.kt:766) 한 줄만 교체하면 된다.
- **합승 마커**: 하드코딩 좌표 카운트 배지를 없애면 PEEK 상태의 「마커 탭 → 20 합류 확인」 경로가
  사라지므로, 서버가 좌표를 준 방(`ride.originLat/originLng`)에 한해 실제 지도 마커(캡션 "N명",
  `MoyeotaColor.Primary500`)를 찍고 클릭 시 `onMarkerClick(ride)` 를 호출하도록 옮겼다.
  좌표가 없거나 범위를 벗어나면(`latLngOrNull` = null) 마커를 생략한다 — 리스트의 「합류」 버튼으로
  같은 화면에 갈 수 있으므로 기능 손실은 없다. 더미 목록(`DefaultParties`)은 좌표가 없어 마커가 뜨지 않는다.
- **제스처**: 배너·칩·시트는 자기 바운드에서만 터치를 소비하고 감싸는 Column 은 소비하지 않으므로,
  홈 14 와 같이 나머지 영역의 팬·줌이 지도로 그대로 전달된다. 시트가 덮는 높이는 `contentPadding` 으로
  넘겨 카메라 중심이 시트 뒤로 밀리지 않게 했다.
- **색상**: 신규 하드코딩 hex 없음. 마커 색은 `MoyeotaColor.MarkerOrigin` / `Primary500`.
- `MapPlaceholder` 컴포넌트 자체는 남는다 — 20 합류 확인·34 내 탑승·16 목적지 확인이 아직 사용 중.

## 미해결 / 후속 제안

1. **GPS 연동** — 위치 권한 요청 + `FusedLocationProvider` 로 실제 좌표를 받아 `MyLocation` 대체.
   현재 `locationGranted` 파라미터는 하드코딩 true 로 내려오고 있어 권한 안내 분기가 실동작하지 않는다.
2. **목록 좌표** — `GET /matching/rooms` 응답에 departureLat/Lng 가 비어 오면 지도 마커가 하나도 뜨지 않는다.
   실기 확인 시 서버 응답에 좌표가 실려 오는지 qa-verifier 확인 필요.
