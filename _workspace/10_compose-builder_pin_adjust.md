# 10 · compose-builder — 출발지·도착지 위치 미세조정(핀 조정)

## 요구
16 도착지 확인에서 검색으로 잡힌 출발지·도착지 좌표를 **지도로 미세조정**하고,
조정값이 `createParty` 의 departureLat/Lng · destinationLat/Lng 까지 흘러가게 한다.
마커 드래그가 아니라 **카카오T 방식**(핀 화면 중앙 고정 + 지도를 움직임).

## 화면 진입 경로
14 홈 → 검색창 → 15 목적지(출발지·도착지 선택) → 「경로 확인하기」 →
**16 도착지 확인**(`Routes.DESTINATION_CONFIRM`, `dialog()` 목적지) →
지도 탭 / 「위치 조정」 칩 / 경로 카드의 「조정」 → **위치 조정 레이어** →
지도 팬·줌 → 「이 위치로 설정」 → 16 복귀 → 「같이 탈 사람 찾기」

라우트 상수·NavGraph 변경 **없음**.

---

## 변경 파일

### 1. `presentation/.../feature/home/PinAdjustOverlay.kt` (신규, 358줄)

| 라인 | 내용 |
|------|------|
| 54 | `enum class PinTarget { ORIGIN, DESTINATION }` |
| 88–117 | `PinAdjustOverlay(originName, destinationName, originPosition, destinationPosition, initialTarget, onCancel, onConfirm)` — 조정 대상이 하나도 없으면 즉시 return(방어). 진입 대상의 좌표가 없으면 반대쪽으로 자동 전환 |
| 105–108 | `originWorking` / `destinationWorking` / `moving` — **State 로 들고 화면 본문에서 읽지 않는다**. 카메라 콜백이 매 프레임 쓰기 때문에 본문이 읽으면 팬 중 전체가 재구성된다 |
| 112–124 | `anchor` — 카메라에 실제로 넣는 값. **대상 전환 시에만** 갱신. 팬 중에 갱신하면 `NaverMapView` 의 `LaunchedEffect(map, center, zoom)` 가 카메라를 되돌려 지도가 손가락과 싸운다 |
| 129–155 | 카메라 구독 — `OnCameraChangeListener`(이동 중 실시간 좌표 + `moving=true`) · `OnCameraIdleListener`(확정 좌표 + `moving=false`). dispose 시 둘 다 해제 |
| 159–177 | GPS 파란 점 — `rememberMyLocationState(autoRequestPermission = false, FormLocationIntervalMs)` + `naverMap.locationOverlay`. 싱글턴이라 `setMap` 이 아니라 `isVisible` 로 켜고 dispose 에서 끈다 |
| 179 | `BackHandler { onCancel() }` — 뒤로가기가 다이얼로그(16)를 통째로 닫지 않게 |
| 197–232 | 헤더 + 「출발지 / 도착지」 토글. 좌표가 없는 대상은 칩 자체를 내지 않는다 |
| 235–265 | 지도(`RouteMapView`, zoom 17.0, `useTextureView = true`) + 중앙 고정 핀. **조정 중인 쪽은 마커를 띄우지 않고**(중앙 핀과 겹친다) 반대쪽 마커만 남겨 두 지점 관계를 보이게 함 |
| 254–259 | 핀이 가리키는 지점 표시(반투명 8dp 점) — 핀이 떠 있는 동안에도 제자리에 남는다 |
| 260–268 | 핀 Box 를 `offset(y = -PinHeight/2)` — 핀 **끝**이 지도 중심(= `cameraPosition.target`)에 오게 |
| 270–318 | 하단 패널: 대상 이름 + 실시간 좌표 + 안내 문구 + `PrimaryCtaButton("이 위치로 설정")` |
| 321–347 | `CenterPin(color, lifted)` — 물방울 핀(Canvas). `lifted` 는 **지연 읽기 람다**라 카메라 이동 상태 구독이 이 컴포저블 안으로 갇힌다. 이동 중 8dp 부양(`animateDpAsState`) |
| 350–357 | `CoordinateLabel(position)` — 같은 이유로 지연 읽기. 팬 중에는 이 텍스트만 재구성된다 |

색상은 전부 `MoyeotaColor`(`MarkerOrigin`/`MarkerDestination`/`Primary500`/`SurfaceCanvas`/`InkPrimary`).
신규 하드코딩 hex 없음(그림자 spotColor `Color(0x141B2A4A)` 는 16 모달과 같은 기존 값).

### 2. `presentation/.../feature/home/DestinationConfirmModal.kt`

| 라인 | 변경 |
|------|------|
| 113 | 파라미터 `onAdjustPositions: (LatLng?, LatLng?) -> Unit` 추가 |
| 122–123 | `adjustTarget: PinTarget?`(모달 내 확장 상태) · `canAdjust` |
| 188–197 | 미리보기 지도를 덮는 **투명 탭 레이어** → 조정 진입. `MapView` 는 `AndroidView` 라 터치를 스스로 먹어 부모 `clickable` 로는 탭을 못 받는다. 겸사겸사 160dp 스트립이 제멋대로 팬·줌되는 것도 막는다 |
| 216–236 | 지도 우하단 「위치 조정」 칩 — 조정이 가능하다는 사실이 눈에 보여야 한다 |
| 320 · 353 | 경로 카드 출발지·도착지 행에 「조정」 링크 — **무엇을 조정하는지 애매하지 않도록 각각 진입점** |
| 474–487 | 루트 `Box` 최상단 레이어로 `PinAdjustOverlay` 렌더 |
| 493–506 | `AdjustLink` private 컴포저블 |
| 529 | `ConditionChip` `private` → `internal` (조정 화면 토글이 같은 칩을 재사용 — 신규 컴포넌트 없음) |

### 3. `presentation/.../feature/home/DestinationConfirmRoute.kt`

| 라인 | 변경 |
|------|------|
| 102–103 | `adjustedOrigin` / `adjustedDestination` `rememberSaveable`(LatLng 는 Parcelable) |
| 106–108 | 표시 좌표 = 조정값 ?: `latLngOrNull(검색 좌표)` — 범위 검증(QA D-1 대비)은 그대로 유지 |
| 124–127 | `onAdjustPositions` → 조정값 저장 |
| 131–137 | `createParty(origin.movedTo(adjustedOrigin), destination.movedTo(adjustedDestination), conditions)` |
| 143–145 | `Place.movedTo(LatLng?)` — 조정값이 있으면 좌표만 갈아 끼운다. **이름은 건드리지 않는다**(역지오코딩 없음) |

### 4. `core/designsystem/.../component/RouteMapView.kt`

| 라인 | 변경 |
|------|------|
| 97–98 · 111 | `onMapReady: (NaverMap) -> Unit = {}` 추가 (기본값이라 기존 호출부 무영향) |
| 122 · 130–133 | `rememberUpdatedState` 로 최신 참조만 유지 — 람다 재생성이 `NaverMapView` 이펙트를 재실행하지 않게 |

`data/` · `domain/` 무수정.

---

## UX 결정

1. **신규 네비게이션 목적지를 만들지 않았다.**
   16 은 `dialog()` 목적지라 거기서 다시 `navigate` 하면 조정 화면이 **다이얼로그 아래**에 깔린다.
   같은 다이얼로그 윈도우 안의 최상단 레이어(모달 내 확장 상태)로 띄우면 z-order·뒤로가기가 모두 예측 가능하다.
   `BackHandler` 로 뒤로가기를 가로채 16 으로만 돌아온다(다이얼로그가 통째로 닫히지 않는다).

2. **마커 드래그 금지 — 핀 고정 + 지도 이동.**
   손가락이 핀을 가리지 않고, 정밀 조정을 「확대」로 해결할 수 있다. 좌표는 `cameraPosition.target`.

3. **진입점 3개, 대상 전환 1개.**
   지도 탭 · 「위치 조정」 칩(둘 다 도착지로 진입) · 경로 카드 행별 「조정」(각각의 대상).
   조정 화면 상단에 출발지/도착지 토글을 둬 무엇을 조정 중인지 항상 라벨로 보인다(핀 색도 함께 바뀐다).

4. **확정은 양쪽을 한 번에 커밋한다.**
   토글로 둘 다 만진 뒤 「이 위치로 설정」 한 번이면 둘 다 반영된다.
   조정하지 않은 쪽은 들어온 값 그대로 되돌려주므로 덮어쓰기 사고가 없다.

5. **이름은 절대 바뀌지 않는다.** 역지오코딩을 하지 않으므로 사용자가 고른 장소명이 계속 정답이다.
   「부산대 정문」이라 적힌 채 **실제 만날 지점만** 정확해지는 것이 이 기능의 목적.

6. **조정값은 ViewModel 이 아니라 화면 상태.**
   방을 만드는 순간에만 쓰이고, 모달을 닫아 15 로 돌아가면 검색 좌표에서 다시 시작하는 편이 예측 가능하다.

7. **성능**: 팬 중에는 좌표 텍스트와 핀만 재구성된다(지연 읽기 람다). 지도·마커 파라미터는 조정 중인
   쪽을 `null` 로 두어 매 프레임 바뀌는 State 를 본문에서 읽지 않는다.

---

## 검증

### 빌드
`./gradlew :presentation:compileDebugKotlin :app:assembleDebug --console=plain --rerun-tasks`
→ **BUILD SUCCESSFUL**. 신규 경고 없음(남은 2건은 이번 변경과 무관한 기존 파일).

### 실기 (emulator-5554 · Pixel_6 콜드부트 · 실기기 R3CRA0T473J 미접속, 미사용)

| 확인 | 결과 | 근거 |
|------|------|------|
| 16 진입 · 「위치 조정」 칩 · 행별 「조정」 노출 | ✅ | `screenshots/pin_01_confirm_modal.png` |
| 「조정」 → 조정 화면, 중앙 핀 + 좌표 표시 | ✅ | `pin_02_adjust_dest.png` (35.156000, 129.060200 = 선택한 도착지) |
| 지도 팬 → 핀 고정, 좌표 갱신, 이름 불변 | ✅ | `pin_03_panned.png` (→ 35.155056, 129.060777) |
| 출발지 토글 → 카메라 이동 · 핀 색 파랑 · 라벨 「현재 위치」 | ✅ | `pin_04_origin_toggle.png` |
| GPS 파란 점 렌더(중앙 핀과 별개) | ✅ | `pin_05_origin_panned_gps.png` — 출발지를 옮기자 파란 점이 원래 GPS 위치에 남는다 |
| 「이 위치로 설정」 → 16 복귀, 지도·마커 이동 | ✅ | `pin_06_back_in_modal.png` — 카메라가 조정 지점(쥬디스태화 앞)으로 이동, 이름 그대로 |
| **createParty 요청 바디가 조정값** | ✅ | OkHttp 로그(아래) |
| 재진입 시 조정값 유지 | ✅ | `pin_07_maptap_reopen.png` — 지도 탭 진입 시 35.155056, 129.060777 로 시작 |
| 뒤로가기 = 조정만 닫힘(다이얼로그 유지) | ✅ | `pin_08_backhandler.png` |
| 크래시 | 없음 | `logcat \| grep -cE "FATAL\|AndroidRuntime"` → 0 |

```
--> POST http://10.0.2.2:8080/api/v1/matching/rooms
{"creatorId":1,
 "departureLat":35.1585134500033,"departureLng":129.05894741624627,
 "destinationLat":35.155056006929634,"destinationLng":129.06077691780064,
 "departure":"현재 위치","destination":"CGV 서면",
 "capacity":3,"departureRadius":500,"destinationRadius":500}
```
조정한 두 좌표가 그대로, **이름은 변경 없이** 실려 나갔다. 응답은 500 이지만 원인은 서버 측
(아래 환경 결함 3) — 지시대로 요청 바디까지만 확인.

### 검증 환경 메모 (전부 프론트 밖의 문제)

1. **백엔드가 떠 있지 않아 직접 기동**했다(`../backend` `./gradlew bootRun`, 8080). postgres·redis 컨테이너는 이미 떠 있었다. 종료하지 않고 그대로 둔다.
2. **장소 검색 불가** — `KAKAO_API_KEY` 미설정으로 `GET /api/v1/places` 가 항상 401→502
   (`KakaoPlaceClient`). 실기 확인을 위해 `DestinationRoute.runSearch` 의 catch 에 좌표 있는 더미
   3건을 임시로 넣어 16 까지 진입했고, **검증 후 원복**했다(현재 트리에 스텁 없음, 원복 상태로 재빌드·재설치 완료).
3. **`POST /api/v1/matching/rooms` 500** — 서버가 외부 경로 API 를 인증 없이 호출해
   `401 Authentication Failed` 가 그대로 500 으로 샌다. 앱 요청은 정상.
4. **`POST /api/v1/users/me/favorite-places` 500** — `FavoritePlaceId` 임베더블에 기본 생성자가 없어
   Hibernate `InstantiationException`. 자주 가는 곳으로 우회 진입도 막혀 있었다(백엔드 결함 보고 대상).

---

## 범위 밖 / 후속

- 조정 좌표의 **역지오코딩**(「조정한 위치 · OO빌딩 앞」 같은 표기) — 서버 API 부재. 07 리포트의 미해결 건과 같은 축.
- 조정값을 15 로 되돌려 반영하지 않는다(16 을 닫으면 초기화). 15 에서 좌표를 다시 고르면 그게 새 출발점.
- 실기기에서의 GPS 오버레이 정확도(09 리포트 후속 1·2)는 이번에도 미실증.
