# 09 · compose-builder — 위치 표시 품질 개선 (17~19 합승 탭)

**작업**: 「위치가 대충 맞는다」 → 「거의 정확하게 나온다」로 체감을 바꾸는 3가지 —
갱신 주기 10초→**1초**, 튀는 fix 를 거르는 **정확도 게이트**, 일반 마커 →
**네이버 SDK 위치 오버레이(파란 점 + 오차 원)**.

**빌드**: `./gradlew :presentation:compileDebugKotlin :app:assembleDebug --console=plain --rerun-tasks`
→ BUILD SUCCESSFUL. 신규 경고 없음(남은 2건은 이번 변경과 무관한 기존 파일).

**진입 경로**: 앱 실행 → 온보딩 건너뛰기 → 로그인 → 14 홈 → 하단탭 「합승」 (권한 허용 시 지도)

**범위 밖(손대지 않음)**: `data/`, `domain/`, `LocationTrackingMode`/`FusedLocationSource`
(Activity 권한 콜백 포워딩을 요구해 Compose 권한 런처와 맞지 않는다 — 07 리포트 결론 유지)

---

## 변경 파일

### `presentation/core/location/MyLocationState.kt`

| 라인 | 변경 |
|------|------|
| 44–59 | `UserCoordinates` 에 `accuracyMeters` · `bearingDegrees` 추가 — **둘 다 기본값 null 이라 기존 호출부는 그대로 컴파일된다** |
| 85 | `MapLocationIntervalMs = 1_000L` — 지도 화면 기본 주기 |
| 88 | `FormLocationIntervalMs = 5_000L` — 지도가 없는 화면(15)용 |
| 105 | `AccuracyGateM = 30f` — 이보다 나쁜 픽스는 마커 갱신에 쓰지 않는다 |
| 113 | `StaleFixNanos = 20초` — 게이트 때문에 점이 영영 얼어붙는 것 방지 |
| 121 | `MinBearingSpeedMps = 0.5f` — 정지 상태 bearing 은 잡음이라 버린다 |
| 136–139 | `rememberMyLocationState(autoRequestPermission, updateIntervalMs = MapLocationIntervalMs)` — **파라미터 추가만, 기존 호출 시그니처 유지** |
| 147 | `visible` — 화면이 STARTED 이상일 때만 구독(배터리) |
| 150 | `gate = remember { FixGate() }` — 구독이 끊겼다 붙어도 판단 기준 유지 |
| 181–194 | 같은 라이프사이클 옵저버가 `ON_START/ON_STOP`(구독 on/off) + `ON_RESUME`(권한 재확인) 처리 |
| 198–216 | 구독 이펙트: `granted && visible` 일 때만. 좌표 유효성 → 게이트 순으로 통과한 fix 만 반영 |
| 257–291 | **`FixGate`** — 아래 「채택 규칙」 |
| 296–311 | `toUserCoordinatesOrNull()` 이 accuracy·bearing 까지 담아 돌려준다 |
| 330–386 | `startLocationUpdates(context, intervalMs, …)` — `getCurrentLocation(HIGH_ACCURACY)` **신규 측위 1회** 추가, `CancellationTokenSource` 로 화면 이탈 시 취소 |
| 353–357 | `LocationRequest(HIGH_ACCURACY, intervalMs)` + `minUpdateInterval = intervalMs / 2`(=0.5초) |
| 393–420 | 프레임워크 폴백도 같은 `intervalMs` 사용 |

#### 채택 규칙 (`FixGate.accept`)

순서대로 판정한다. 하나라도 걸리면 채택하고 기준값을 갱신한다.

| 조건 | 채택 | 이유 |
|------|------|------|
| 채택된 fix 보다 **시간이 과거** | ✗ | 제공자 전환 중 뒤늦게 오는 옛 fix 가 점을 되돌린다 |
| 아직 좌표가 **하나도 없음** | ✓ | 대략 위치라도 빈 지도보다 낫다(지시사항) |
| accuracy 를 **모름**(`hasAccuracy()==false`) | ✓ | 판단 근거가 없으면 새 fix 를 믿는다 |
| `accuracy ≤ 30m` | ✓ | 게이트 통과 |
| 지금 쓰는 값보다 **더 정확** | ✓ | 게이트 밖이어도 개선이다 |
| 마지막 채택 후 **20초 경과** | ✓ | 실내 등에서 점이 얼어붙는 것 방지 |
| 그 외 | ✗ | 수백 m 튀는 셀타워/와이파이 위치 차단 |

### `presentation/feature/explore/ExploreScreen.kt`

| 라인 | 변경 |
|------|------|
| 89–103 | **`MyLocationFix(position, accuracyMeters, bearingDegrees)`** 신설. 좌표·오차·방향이 **같은 fix 에서 나와야** 점과 원이 어긋나지 않아 하나로 묶었다 |
| 164 · 270 · 376 · 851 | `myLocation: LatLng?` → `myLocation: MyLocationFix?` (기본값 null 유지 — Preview 3종 그대로) |
| 862–872 | 카메라 「첫 실좌표로 1회만 이동」 로직은 `myLocation.position` 기준으로 유지 |
| 881–892 | **실좌표 있으면 `MyLocationOverlay`, 없으면 기존 「내 위치(기준점)」 마커** (폴백 동작 보존) |
| 943–952 | 오버레이 상수 — glide 800ms / snap 200m / 최소 이동 0.5m / 원 반경 상한 4000px |
| 964–1024 | **`MyLocationOverlay`** — `naverMap.locationOverlay` 제어 |
| 1031–1037 | `accuracyRadiusPx()` — 오차(m) → 현재 줌의 픽셀 |

`MyLocationOverlay` 세부:

1. **부드러운 이동** — 1초마다 좌표를 그대로 찍으면 점이 뚝뚝 끊긴다. 이전 좌표에서 새 좌표까지
   800ms 등속 보간(`Animatable` + `LinearEasing`)한다. 0.5m 미만(GPS 지터)이나 200m 초과(순간이동)는
   보간하지 않는다 — 지터에 계속 애니메이션이 걸리거나 점이 지도를 가로질러 기어가면 더 이상하다.
2. **부착/해제** — `locationOverlay` 는 `NaverMap` 당 하나뿐인 싱글턴이라 `setMap` 대신
   `isVisible` 로 제어하고, **dispose 시 반드시 `isVisible = false`** (같은 지도를 쓰는 다음 화면에
   낡은 점이 남지 않게).
3. **방향** — bearing 이 있을 때만 `subIcon = DEFAULT_SUB_ICON_ARROW` + `bearing` 반영.
   없으면 화살표를 띄우지 않는다(없는 방향을 북쪽이라 그리면 사용자가 반대로 걷는다).
4. **오차 원** — `circleRadius` 단위가 **픽셀**이라 같은 오차라도 줌에 따라 화면 크기가 달라진다.
   `OnCameraChangeListener` 로 카메라가 움직일 때마다 `projection.metersPerPixel` 로 다시 환산해
   원이 지면에 붙어 있는 것처럼 보이게 했다. 정확도를 모르면 `SIZE_AUTO`(=0)로 원을 그리지 않는다.
   색은 SDK 기본(파란 점과 같은 계열) — 하드코딩 hex 없음.

### `presentation/feature/explore/ExploreRoute.kt`

| 라인 | 변경 |
|------|------|
| 73–84 | `UserCoordinates` → `MyLocationFix` 변환(`remember` 로 안정화) |
| 95 | `myLocation = myFix` |

### `presentation/feature/home/DestinationRoute.kt` (15 목적지)

| 라인 | 변경 |
|------|------|
| 16 | `FormLocationIntervalMs` import |
| 207–211 | `rememberMyLocationState(autoRequestPermission = false, updateIntervalMs = FormLocationIntervalMs)` — 지도용 1초 주기를 지도 없는 화면까지 끌고 오지 않는다 |

동작은 이전과 동일(오히려 10초 → 5초로 빨라짐). `currentLocationPlace(coordinates)` 는 lat/lng 만
읽으므로 필드 추가의 영향이 없고, `setCurrentLocation` 이 같은 `Place` 를 걸러 불필요한 방출도 없다.

---

## 실기 검증 (emulator-5554 · 실기기 R3CRA0T473J 미접속 — 건드리지 않음)

백엔드 `localhost:8080` 기동 상태에서 합승 탭 진입.

| 확인 | 결과 | 근거 |
|------|------|------|
| 1초 주기가 실제로 나가는가 | ✅ | `dumpsys location` → `gps provider: ProviderRequest[@+1s0ms, HIGH_ACCURACY, WorkSource{com.moyeota}]` (기존 10초에서 변경 확인) |
| 파란 점 렌더 | ✅ | `screenshots/loc_01_blue_dot.png` — 일반 마커/캡션이 아니라 SDK 파란 점 |
| 오차 원 반영 | ✅ | `screenshots/loc_02_accuracy_circle.png` — 에뮬 fix 의 hAcc≈3.2m 이라 광역 줌에서는 점에 가려지고, 줌인하면 원이 커진다(= 줌마다 재환산 동작) |
| 1초 내 추종 | ✅ | 25m/초로 좌표 연속 주입 후 1초 간격 스크린샷 5장 — 파란 점 x좌표가 프레임마다 60~65px 전진(정지 프레임 없음). `screenshots/loc_03_walk_1s_track.png` |
| 부드러운 이동 | ✅ | 800ms 보간 중 프레임이 목표 지점 **직전**에 잡힘(1초 샘플에서 이동량이 이론값보다 약간 작음) = 순간이동이 아니라 미끄러짐 |
| 백그라운드 중단(배터리) | ✅ | 홈 키 → `ProviderRequest[OFF]`, 복귀 → 다시 `@+1s0ms` |
| 지도 화면 이탈 중단 | ✅ | 홈 탭 이동 → `[OFF]`, 합승 재진입 → `@+1s0ms` · 점 정상 복귀 (`screenshots/loc_04_reenter.png`) |
| 크래시 | 없음 | `logcat | grep -c "AndroidRuntime|FATAL"` → 0 |

관측 메모: 좌표를 **200m 순간이동**시키면 GMS fused 가 자체 모션 모델로 수 초에 걸쳐 따라온다
(gps provider 는 즉시 갱신됨). 앱이 아니라 fused provider 의 스무딩이며, 실사용 패턴인 연속 이동에서는
지연이 없다(위 「1초 내 추종」).

---

## 미해결 / 후속

1. **정확도 게이트는 에뮬레이터에서 실증 불가** — 에뮬 fix 는 항상 hAcc 3~5m 라 30m 초과 픽스를
   만들 수 없다. 실기기에서 실내→실외 이동 시 점이 튀지 않는지 확인 필요(qa-verifier 대상).
2. **bearing/화살표도 미실증** — 에뮬 fix 는 speed=0.19m/s · bearing 고정이라 화살표 조건
   (`speed ≥ 0.5m/s`)에 걸리지 않는다. 실기기 도보 이동에서 확인 필요.
3. **14 홈 지도**는 이번에도 범위 밖 — 같은 `MyLocationFix` + `MyLocationOverlay` 패턴을 그대로
   옮길 수 있다(현재 `MyLocationOverlay` 는 explore 파일 private).
4. 07 리포트의 역지오코딩(「현재 위치」 이름 고정) 건은 여전히 미해결 — api-integrator 협의 대상.
