# 66 · 홈(14) 내 위치 표시 + 위치 오버레이 공용화

작성 2026-09-13.

## 요청
"홈 화면에서 내 위치에 대한 마킹도 표시해줘"

## 변경
- **신규 공용 컴포넌트** `core/designsystem/.../component/MyLocationOverlay.kt` — 네이버 SDK 내장 `LocationOverlay`(파란 점 + 오차 원 + 방향 화살표). 합승 탭에만 private 으로 있던 구현을 그대로 끌어올렸다. 책임 세 가지는 KDoc 에 적었다: 좌표 보간(1초 fix 를 등속으로 미끄러뜨림, 200m 이상 튀면 순간이동), 화면 이탈 시 싱글턴 오버레이 숨기기, 오차 원의 m→px 환산(카메라 변화마다 재계산).
  - `accuracyMeters = null` 이면 오차 원을 그리지 않는다 — 26 운행 중처럼 반경이 정보가 되지 않는 화면을 위한 문.
- **14 홈**: `HomeRoute` 가 `rememberMyLocationState()` 로 위치를 잡아 `HomeScreen(myLocation = ...)` 으로 넘긴다. 지도에 파란 점을 얹고, **첫 fix 때 한 번만** 카메라를 그 위치로 옮긴다(줌 15). 이후 갱신은 점만 따라간다 — 매 fix 마다 카메라를 되돌리면 지도를 팬·줌할 수 없다(합승·26 과 같은 규칙).
- **17~19 합승**: private `MyLocationOverlay` · `accuracyRadiusPx` · 보간 상수 4개를 지우고 공용 컴포넌트를 호출한다(동작 동일).

## 권한
홈은 배경이 통째로 지도인 화면이라 합승 탭과 같이 `autoRequestPermission = true` 로 요청한다. 거부하면 파란 점 없이 기본 카메라(부산)로 남고 화면은 그대로 동작한다.

## 검증
- 빌드 · `:presentation:testDebugUnitTest` 통과
- 에뮬레이터(Pixel 6, 위치 129.0596/35.1579 서면): 홈 진입 시 **파란 점 표시 + 카메라가 그 위치로 이동**, 시트를 접어도 유지. 합승 탭 회귀 없음(파란 점 동일)

## 남은 것
26 운행 중의 `RideMyLocationOverlay` 도 같은 컴포넌트로 합칠 수 있다(오차 원만 끄면 된다). 이번엔 방금 시트 구조를 바꾼 화면이라 건드리지 않았다.
