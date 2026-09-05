# 28 · 26 운행 중 화면 실지도 구현 (compose-builder)

2026-09-05 · branch `feature/naver-map`

## 무엇을 했나

26 운행 중 [S15] 상단의 회색 `MapPlaceholder`(190dp)를 실지도로 교체했다.
출발·도착 마커 + 서버 확정 경로 폴리라인 + 내 현재 위치(파란 점, 1초 갱신)를 그리고,
초기 카메라는 세 지점이 다 보이는 fitBounds 로 잡는다. 지도 외 요소(타임라인 카드,
보호자 공유 카드, 신고/채팅 버튼)는 건드리지 않았다.

## 변경 파일

| 파일 | 변경 |
|------|------|
| `presentation/src/main/kotlin/com/moyeota/presentation/feature/chat/RideOngoingRoute.kt` | ViewModel 에 `ride: StateFlow<Ride?>` 추가 — 기존 4초 완료 폴링이 읽어오는 방 상세를 그대로 흘려보낸다(추가 API 호출 없음). Route 에서 `rememberMyLocationState(autoRequestPermission = false)` 구독, `latLngOrNull` 검증 좌표·`decodePolyline` 경로를 Screen 에 전달. `partyId == null` 폴백 경로도 내 위치만으로 지도를 그린다 |
| `presentation/src/main/kotlin/com/moyeota/presentation/feature/chat/RideOngoingScreen.kt` | `MapPlaceholder` → `RideOngoingMap`(RouteMapView 래핑, 동일 190dp). 파라미터 4개 추가(origin/destination/routePath/myLocation, 전부 기본값 있어 프리뷰 유지). private `RideMyLocationOverlay`(SDK LocationOverlay + 등속 보간 + bearing 화살표) 추가 |

Routes/NavGraph 변경 없음 (기존 목적지 그대로).

## 설계 결정

- **좌표 출처**: `getPartyDetail` 응답에 `originLat/Lng`, `destinationLat/Lng`, `routePolyline` 이 이미 있다 → **백엔드 요청 불필요**. 25 DispatchStatusScreen 과 동일한 `latLngOrNull` 범위 검증을 거친다(QA D-1 위경도 전치 방어 — 전치된 좌표는 마커만 빠지고 화면은 산다).
- **카메라는 2회만 움직인다**: ① 첫 실위치 잡히면 내 위치 중심(줌 15, 서버 좌표 대기 중 폴백) ② 출발·도착 좌표 도착 시 내 위치 포함 fitBounds(패딩 40dp, Easing 애니메이션). 이후는 사용자 팬·줌이 주인 — 폴링(4초)·위치 갱신(1초)이 카메라를 되돌리지 않는다. `RouteMapView` 의 `center` 를 **상수**(`MoyeotaDefaultCamera`)로 고정한 이유: 기본값(originPosition)을 쓰면 폴링으로 좌표가 도착하는 순간 NaverMapView 가 카메라를 재적용해 fitBounds 를 덮어쓴다. 두 플래그는 `rememberSaveable` — 재진입 시 NaverMapView 의 카메라 복원을 존중한다.
- **파란 점**: ExploreScreen 의 비공개 `MyLocationOverlay` 와 같은 패턴의 화면 전용 축약판(등속 보간 800ms, 200m 이상은 스냅, bearing 있는 fix 만 화살표). 오차 원은 뺐다 — 차량 속도에서 정보가치가 낮고 줌별 픽셀 환산 리스너가 더 필요해서. 공용 승격은 explore·home 조정·여기 3곳 정리 시 함께 하는 게 맞아 보류(리더 판단 사항).
- **권한**: 새로 묻지 않는다(`autoRequestPermission = false`) — 운행 중 흐름을 다이얼로그로 끊지 않는다. 15~19 에서 이미 허용된 상태가 일반 경로.

## 검증

- `./gradlew :app:assembleDebug` **통과**.
- **에뮬레이터 실기 확인 완료** (emulator-5554 · Pixel_6 · 백엔드 localhost:8080, 5558·백엔드 프로세스 미접촉):
  1. mopass1 로그인 상태에서 홈 → 목적지 「서면역 1번 출구」 → **인원 1인**으로 방 생성(정원 차야 매칭이 시작되는 서버 로직이라 1인 방으로 즉시 MATCHING 진입 — 3인 방 + 승객 1명으로는 sweeper 가 콜을 내지 않음을 먼저 확인)
  2. 기사 qadriver1 curl: `POST /dispatch/online`(출발지 좌표) → 콜 오픈 확인(`GET /dispatch/calls/3/status`) → `accept` → `arrive` → `board`
  3. 앱이 21→25→**26 자동 전이**. 26 지도에 출발(파랑)·도착(빨강) 마커 + 경로 폴리라인 + 내 위치 파란 점 렌더, 카메라 fitBounds 로 세 지점 모두 표시 확인
  4. `adb emu geo fix` 로 경로상 3개 지점 이동 → 파란 점이 카메라를 튕기지 않고 따라오는 것 확인
  5. 기사 `complete` → 26→28 최종 요금 자동 전이 정상(기존 폴링 회귀 없음)
- 스크린샷: 세션 스크래치패드 `09_ongoing.png`(마커+점), `10_moved.png`(점 이동) — 로컬 보관 관례에 따라 커밋하지 않음.
- 검증 부산물: 서버에 party 2(취소됨)·party 3(FINISHED, fare 12,500 로 complete) 생김. 기사 online 은 DELETE 로 해제함.

## 한계 / 메모

- 도보 구간 포함 실기기 GPS 품질(FixGate 게이트)은 에뮬레이터 geo fix 로는 검증 범위 밖.
- 파란 점 오차 원 없음(설계 결정 참조). 필요해지면 ExploreScreen 구현을 공용 승격하며 함께.
- 백엔드 요청 없음. D-1(좌표 전치)이 발생하는 서버 값이 오면 해당 마커만 미표시(기존 25 와 동일 정책).
