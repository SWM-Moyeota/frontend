# 12 · compose-builder — 합승 탭 「지도 화면 범위 기반 조회」 연결

**작업**: 17·18·19 합승 탭의 방 목록을 전체 목록(`getParties`)이 아니라
**지도에 보이는 영역**(`getPartiesWithin`)으로 조회하도록 바꾸고, 좌표 기반 방 마커를 실제로 띄운다.

**빌드**: `./gradlew :presentation:compileDebugKotlin :app:assembleDebug --console=plain` → BUILD SUCCESSFUL (신규 경고 없음)

**진입 경로**: 앱 실행 → 온보딩 건너뛰기 → 로그인 → 14 홈 → 하단탭 「합승」 → 17 PEEK(전체 지도) ⇄ 18 HALF ⇄ 19 FULL

**범위 밖(손대지 않음)**: `data/`, `domain/`, `core/designsystem`

---

## 변경 파일

### `presentation/feature/explore/ExploreRoute.kt` (ViewModel)

| 라인 | 변경 |
|------|------|
| 36 | `BOUNDS_DEBOUNCE_MS = 400L` — 팬·줌 연타 중간 범위로 서버를 때리지 않는다 |
| 39 | `NEARBY_POLL_INTERVAL_MS = 4_000L` — 21 매칭 대기 화면과 같은 간격 |
| 55 | `LoadRequest(bounds, manual, seq)` — `seq` 는 **같은 범위 재조회(재시도)** 를 구분하기 위한 것. StateFlow 는 같은 값을 다시 방출하지 않아 seq 없이는 「다시 시도」가 씹힌다 |
| 71–74 | `onVisibleBoundsChange(bounds)` — 지도가 올려보낸 범위. 직전과 같은 범위면 무시 |
| 77–80 | `refresh()` — 마지막 범위를 `manual = true` 로 다시 요청 (ErrorBox 의 「다시 시도」) |
| 85–97 | `observeRequests()` — `collectLatest` 하나로 **디바운스 → 조회 → 그 범위 주기 갱신**까지 처리. 새 범위가 오면 이전 조회와 그 범위의 폴링이 **함께** 취소돼, 낡은 범위의 응답이 새 목록을 덮어쓰는 경쟁이 구조적으로 생기지 않는다 |
| 100–115 | `load(bounds)` — 목록이 없을 때만 Loading/Error 로 화면을 덮는다 (아래 「설계 판단」) |
| 117–118 | `MapBounds.query()` — **인자 순서(남서 위도 → 남서 경도 → 북동 위도 → 북동 경도)를 코드에서 딱 한 곳**에만 쓴다. 뒤바뀌면 서버가 오류가 아니라 빈 목록을 주므로 호출부마다 풀어 쓰면 눈에 안 띄는 버그가 된다 |
| 155–161 | 첫 조회 — 지도의 첫 idle 을 기다리지 않고 기준 범위로 먼저 쏜다. **지도는 Success 상태에서만 붙기 때문에**(로딩 화면엔 지도가 없다) idle 을 기다리면 Loading 에서 못 나온다. 권한 거부로 지도가 아예 없는 경로도 동일 |
| 180 | `onVisibleBoundsChange = viewModel::onVisibleBoundsChange` |
| (삭제) | 진입 시 `refresh()` (전체 목록 조회) — 범위 조회로 일원화 |

### `presentation/feature/explore/ExploreScreen.kt`

| 라인 | 변경 |
|------|------|
| 109–122 | **`MapBounds`** 신설 — 필드 순서를 서버 파라미터 순서와 맞춰 둔다 |
| 124–165 | `MapCamera` + **`ExploreCameraState`**(+Saver) 신설 — 카메라를 **화면 레벨**로 끌어올림 (아래 「결함 F-1」) |
| 234 | `ExploreScreen(onVisibleBoundsChange: (MapBounds) -> Unit = {})` 파라미터 추가 (기본값 유지 → Preview 3종 그대로) |
| 239–241 | `cameraState = rememberSaveable(ExploreCameraStateSaver)` |
| 288 · 302 · 350 · 354 · 461 · 466 | PeekContent / HalfContent 로 `cameraState`·`onVisibleBoundsChange` 전달 |
| 918–939 | `DefaultBoundsHalfSpanDeg = 0.006` + `defaultExploreBounds(center)` — 지도가 없을 때 쓰는 기준 범위. ExploreZoom 실측 가시 영역(위도 ±0.006 · 경도 ±0.004)에 맞춰, 「지도에 없는 방」이 리스트에 잠깐 떴다 사라지지 않게 한다 |
| 974–979 | `mapCamera` — NaverMapView 에 넘기는 카메라는 **지도 인스턴스 생성 시 한 번**만 정한다 (아래 「결함 F-2」) |
| 984–992 | 첫 실위치 1회 이동은 `cameraState.centeredOnMyLocation` 로 기억 — 시트 단계가 바뀌어도 다시 끌려가지 않는다 |
| 1000–1004 | `MapIdleReporter(map, cameraState, onVisibleBoundsChange)` |
| 1035–1065 | **`MapIdleReporter`** — `addOnCameraIdleListener` 로 카메라·범위 보고. 부착 직후에도 1회 보고한다(idle 은 「움직였다 멈춤」에만 오므로, 사용자가 지도를 안 건드리면 첫 이벤트가 영영 안 온다) |
| 1067–1086 | `LatLngBounds.toMapBoundsOrNull()` — 레이아웃 전의 0폭·범위 밖 좌표를 거른다. 그대로 조회하면 항상 빈 목록이라 「주변에 방이 없다」로 잘못 보인다 |
| 1010–1020 | (기존) 방 마커 — `ride.originLat/Lng` 가 이제 실제로 채워져 마커가 뜬다. 좌표 null 이면 종전대로 생략 |

**범위는 `NaverMap.getContentBounds`** 로 읽는다 — 뷰 크기가 아니라 **contentPadding 을 뺀 실제 가시 영역**이라, 시트에 가려진 방이 목록에 섞이지 않는다.

---

## 설계 판단

1. **갱신 실패로 지도를 에러 화면으로 덮지 않는다.** 목록이 이미 있으면 실패를 삼키고 다음 주기에 다시 읽는다
   (21 매칭 대기 화면의 폴링과 같은 판단). 이유는 UX 뿐이 아니다 — Error/Loading 으로 바뀌면 **지도가
   컴포지션에서 빠졌다 다시 붙으면서 카메라가 초기화되고, 그 idle 이 또 조회를 부르는 되먹임**이 생긴다.
   첫 조회(목록이 아직 없음)만 기존 컨벤션대로 `LoadingBox`/`ErrorBox(onRetry)`.
2. **마커와 시트 목록은 같은 `visibleParties`** 에서 나온다(기존 구조 유지). 범위 조회 결과가 유일한 출처다.
3. **폴링은 마지막 범위로만** 돈다. 폴링 루프가 `collectLatest` 블록 **안**에 있어, 새 범위가 오면 자동으로 정리된다.

---

## 실기 검증 (emulator-5554 · 콜드부트 · 실기기 R3CRA0T473J 미접속 — 건드리지 않음)

로컬 서버(localhost:8080) 유지. 시드 방 4건(1: 부산대→서면역, 2: 서면역→부산역, 3: 부산대→온천장역, 4: 서면역→부전역 · creatorId 5·6·7).

| 확인 | 결과 | 근거 |
|------|------|------|
| 첫 진입 시 범위 조회가 나가는가 | ✅ | `GET /matching/rooms?swLat=35.2013…&swLng=129.0538…` (기준 범위) → 곧바로 지도 실범위로 재조회 |
| 파라미터가 남서/북동 순서로 실리는가 | ✅ | swLat 35.2256 < neLat 35.2370, swLng 129.0794 < neLng 129.0882 (좌표 전치 없음) |
| 부산대 근처 방 마커 「1명」 | ✅ | `screenshots/nearby_01_pusan_marker.png` — 마커 캡션 + 시트 "이 방향으로 1명이 대기 중" |
| 서면으로 팬 → 범위 밖 방 사라짐 | ✅ | `nearby_02_pan_to_seomyeon.png` — 응답이 partyId 2·4(서면)로 교체, 부산대 방 사라짐, 시트 2명 |
| 시트 카운트 갱신 | ✅ | 1명 → 2명 → (복귀) 2명 |
| 지도 마커 = 시트 목록 | ✅ | `nearby_03_seomyeon_list.png` — 마커 2개(서면역 중첩) / 리스트 「서면역→부산역」 「서면역→부전역」 |
| 돌아오면 재등장 | ✅ | `nearby_04_back_to_pusan.png` — partyId 1·3 복귀 |
| 디바운스 동작 | ✅ | 줌 연타(3회) 중 중간 범위 조회가 취소되고 마지막 범위만 나감 |
| 주기 갱신 | ✅ | 같은 범위로 4초 간격 요청 지속 (12:40:35 / 39 / 43 / 47) |
| 갱신 실패 시 화면 유지 | ✅ | `nearby_05_offline_keeps_list.png` — 비행기모드 10초 뒤에도 지도·목록 유지(에러 화면 아님) |
| 첫 조회 실패 → 에러·재시도 | ✅ | `nearby_06_error_retry.png` (탭바 유지 + 「다시 시도」) → `nearby_07_retry_recovered.png` (복구) |
| 마커 탭 → 20 합류 확인 | ✅ | `nearby_08_marker_tap_join.png` — 「부산대 정문 → 온천장역」 상세로 이동 |
| 크래시 | 없음 | logcat FATAL 0건 |

---

## 검증 중 잡은 결함 2건 (이번에 수정)

### F-1 · 시트 단계 전환이 지도와 목록을 되돌림
PEEK(전체 화면 지도)와 HALF(320dp 지도)는 **서로 다른 컴포저블**이라 시트를 올리면 지도가 새로 만들어진다.
카메라가 지도 내부 상태였던 탓에 서면까지 팬한 뒤 리스트를 보려고 시트를 올리면 **카메라가 부산대로
되돌아가고, 그 idle 이 목록까지 부산대로 되돌렸다**(수정 전 재현: `screenshots/nearby_f1_before_fix_camera_reset.png`
— 서면까지 팬한 뒤 시트를 올렸는데 지도·리스트가 부산대). 06 작업 때는 「지도만
되돌아가는」 사소한 문제였지만, 목록이 카메라를 따라가는 지금은 사용자가 보던 결과가 통째로 사라진다.
→ 카메라를 `ExploreCameraState` 로 **화면 레벨**에 올리고 `rememberSaveable` 로 탭 전환·회전까지 보존.

### F-2 · idle 카메라를 되돌려주면 조회가 영구히 멈춤
idle 로 읽은 카메라를 그대로 `NaverMapView(center=)` 로 돌려주면, SDK 가 돌려주는 좌표가 우리가 넣은
값과 부동소수점 끝자리만큼 달라 **「적용 → idle → 적용」이 무한 반복**된다. 매 idle 이 조회 요청을
재시작시켜 디바운스에 걸린 조회가 계속 취소되고, **HTTP 요청이 한 건도 나가지 않는 상태**가 된다
(로그상 17초간 폴링 0건으로 확인). → 지도에 넘기는 카메라는 인스턴스 생성 시 1회 + 첫 실위치 이동
1회만 바뀌게 하고, 사용자가 움직인 카메라는 `cameraState` 에만 적는다.

---

## 백엔드 결함 (앱 수정 불가 — api-integrator/백엔드 전달 필요)

### D-3 · 범위 조회가 **도착지 경도**까지 범위 안에 있어야 방을 준다

`GET /matching/rooms?sw/ne` 가 출발 좌표가 화면 안에 있어도 빈 목록을 준다. 실서버(localhost:8080) 실측:

| 질의(위도 35.22–35.24 고정) | 결과 |
|---|---|
| `swLng=129.05 neLng=129.10` | partyId 1 반환 |
| `swLng=129.06 neLng=129.12` | **빈 목록** |
| `swLng=129.07 neLng=129.09` | **빈 목록** |
| `swLng=129.0594 …` / `swLng=129.0595 …` | 반환 / **빈 목록** (경계값이 정확히 방의 **destinationLng 129.0594**) |

party 2(서면역→부산역, destLng 129.0423)로 교차 확인해도 동일한 규칙이다.
즉 서버 조건이 사실상 `swLng ≤ departureLng ≤ neLng` **AND** `swLng ≤ destinationLng ≤ neLng` 로 보이고,
**도착지 위도는 검사하지 않는다**(위도 범위 밖 도착지여도 통과). 위도/경도 검사 대상이 어긋난 전치 계열 결함으로 보인다.

**영향**: 실사용 시 「지도에 출발 핀이 있어야 할 방」이 조용히 사라진다. 오류가 아니라 빈 목록이라 앱에서
구분할 방법이 없다. 지도 범위 조회의 목적(출발지 근처 방 찾기)과 정면으로 어긋난다.
**요청**: 목록 필터를 **출발 좌표만** 범위에 넣도록(또는 출발/도착 각각 lat·lng 쌍을 올바르게) 수정.

**검증 우회**: 위 조건 때문에 장거리 방(부산대→서면)은 한 화면에 안 잡혀서, 출발·도착이 가까운 방
(3: 부산대→온천장역, 4: 서면역→부전역)을 추가 시드해 실기 확인했다.

---

## 미해결 / 후속

1. **D-3 수정 전까지** 실사용 체감이 반쪽이다 — 장거리 방일수록 지도에서 사라진다.
2. **필터 칩은 여전히 클라이언트 필터**(더미 id 기반 「여성만」 등). 범위 조회 결과에는 해당 속성이 없어
   「여성만」·「3인」은 서버 방에 사실상 무의미하다. 서버 필터 파라미터가 생기면 옮겨야 한다.
3. **19 FULL(리스트 전용)에는 지도가 없다** → 그 상태에서는 마지막 범위로만 폴링된다. 의도된 동작이지만,
   FULL 에서 「범위를 넓혀 보기」 같은 출구는 없다.
4. **화면을 떠나도 4초 폴링이 계속** 돈다(ViewModel 이 백스택에 남아 있는 동안). 기존 화면들과 같은 수준이라
   이번엔 손대지 않았다 — 배터리 관점의 개선 여지.
