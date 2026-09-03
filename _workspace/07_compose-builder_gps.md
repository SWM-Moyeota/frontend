# 07 · compose-builder — 실제 GPS 위치 연동 (17~19 합승 탭 · 15 목적지)

**작업**: 하드코딩 `DemoOrigin`(부산대 정문 35.2313/129.0838)으로 고정돼 있던 「내 위치」를
기기 실제 좌표로 교체. 런타임 위치 권한 요청 + 재사용 가능한 위치 취득 유틸 신설.

**검증**: `./gradlew :presentation:compileDebugKotlin :app:assembleDebug --console=plain` → BUILD SUCCESSFUL
(`--rerun-tasks` 전체 재컴파일도 통과, 신규 경고 없음)

**진입 경로**
- 합승 탭: 앱 실행 → 로그인 → 14 홈 → 하단탭 「합승」 → **진입 즉시 위치 권한 다이얼로그**
- 15 목적지: 14 홈 → 검색 카드 → 출발지 행(권한 다이얼로그 없음 — 이미 허용된 권한만 사용)

---

## 변경 파일

### 신규 `presentation/src/main/kotlin/com/moyeota/presentation/core/location/MyLocationState.kt`

공용 위치 유틸. 별도 Gradle 모듈(`core/location`)을 만들지 않고
`presentation/core/`(LoadState·Routes·MainNavGraph 가 있는 자리) 컨벤션을 따랐다 — 소비자가
presentation 밖에 없다.

| 심볼 | 내용 |
|------|------|
| `UserCoordinates(latitude, longitude)` | 좌표 값 타입. 지도 SDK `LatLng` 에 묶지 않는다 — 지도가 없는 15 화면도 같은 값을 쓴다 |
| `MyLocationState(isGranted, coordinates, onRequestPermission)` | `@Immutable` 반환 상태 |
| `rememberMyLocationState(autoRequestPermission: Boolean = true)` | 권한 요청 + 좌표 구독 |
| `startLocationUpdates` / `startFrameworkUpdates` | 좌표 취득 (아래 참조) |

### `presentation/.../feature/explore/ExploreScreen.kt`

| 라인 | 내용 |
|------|------|
| 130–131 | KDoc — 「내 위치는 기기 GPS, 못 받으면 DemoOrigin 기준점 폴백」 |
| 143 | `myLocation: LatLng? = null` 파라미터 신규 |
| 148 | `onRequestLocationPermission: () -> Unit = {}` 신규 |
| 200–219 | PEEK/HALF 로 `myLocation` · `onRequestLocationPermission` 전달 |
| 248–271, 354–380 | `PeekContent` / `HalfContent` 시그니처 + `LocationPermissionNotice(onRequestPermission = …)` |
| 737–775 | `LocationPermissionNotice` 에 **「위치 권한 허용」 버튼** 추가 (`MoyeotaColor.Primary500` 필 · 하드코딩 hex 없음) |
| 804–810 | `MyLocation` → **`FallbackOrigin`** 으로 개명 (`DemoOrigin` 좌표 유지) |
| 830 | `ExploreMap(myLocation: LatLng?)` |
| 841–848 | 카메라: 처음 잡힌 실좌표로 **1회만** 이동 (`centeredOnMyLocation` = `rememberSaveable`) |
| 861–864 | 내 위치 마커 = 실좌표 또는 `FallbackOrigin`, 캡션 `"내 위치"` / `"내 위치(기준점)"` |

### `presentation/.../feature/explore/ExploreRoute.kt`

| 라인 | 내용 |
|------|------|
| 73 | `rememberMyLocationState()` — **Loading/Error 분기 밖**에 둔다. 목록 조회가 실패해도 권한 흐름이 죽지 않아야 한다(QA F-1 과 같은 이유) |
| 74–76 | `UserCoordinates` → `LatLng` 변환(`remember` 로 안정화) |
| 89–94 | `locationGranted = myLocation.isGranted` — **하드코딩 `true` 제거**, `myLocation`·`onRequestLocationPermission` 연결 |

### `presentation/.../feature/home/DestinationRoute.kt`

| 라인 | 내용 |
|------|------|
| 27–34 | `DemoOrigin` 주석 갱신 — 「GPS 미연동」 → 「실위치를 못 받았을 때의 폴백」 |
| 37–46 | `CurrentLocationName = "현재 위치"` + `currentLocationPlace(coordinates)`. **역지오코딩은 범위 밖** — 좌표만 실값, 이름·주소는 고정 문구 |
| 67–71 | `UiState.currentLocation: Place?` 추가. `origin` = `selectedOrigin ?: currentLocation ?: DemoOrigin` |
| 143–146 | `setCurrentLocation(place)` — 같은 값이면 무시(불필요한 방출 차단) |
| 207–211 | `rememberMyLocationState(autoRequestPermission = false)` + 좌표 변화 시 VM 반영 |
| 216–217 | 화면에 출발지 좌표 전달(아래 「너무 가까워요」) |

### `presentation/.../feature/home/DestinationScreen.kt`

| 라인 | 내용 |
|------|------|
| 59–75 | `SameSpotRadiusM = 50f` + `isSameSpot(...)` — `android.location.Location.distanceBetween` |
| 101–104 | `originLatitude` / `originLongitude: Double? = null` 파라미터 |
| 130–133 | `tooClose` = 이름 일치 **또는** 50m 이내 |

> 이유: 실위치 출발지의 이름은 `"현재 위치"` 라 기존의 **이름 비교만으로는 같은 자리를 못 걸러낸다**.
> 좌표 비교를 더하지 않으면 「출발지 = 도착지」 차단이 GPS 경로에서 조용히 무력화된다.
> 좌표가 없으면(null) 판정하지 않는다 — 근거 없는 차단이 더 나쁘다.

### 빌드·매니페스트

| 파일 | 내용 |
|------|------|
| `app/src/main/AndroidManifest.xml` | `ACCESS_FINE_LOCATION` · `ACCESS_COARSE_LOCATION` 추가 (병합 매니페스트 확인 완료) |
| `gradle/libs.versions.toml` | `playServicesLocation = "21.4.0"` + `play-services-location` 라이브러리 (기존 카탈로그 컨벤션) |
| `presentation/build.gradle.kts` | `play-services-location`, `androidx.activity.compose`(권한 런처), `androidx.lifecycle.runtime.compose`(ON_RESUME 재확인) |

---

## 권한 흐름

1. `ExploreRoute` 진입 → `rememberMyLocationState()` → 권한 없으면 `LaunchedEffect` 가
   `RequestMultiplePermissions` 런처로 **FINE + COARSE 동시 요청**.
2. 응답: `result.values.any { it }` — **정밀·대략 중 하나만 허용돼도 `isGranted = true`**
   (지도 중심을 잡는 용도라 대략 위치로 충분하다).
3. 거부 시: 자동 재요청 없음(`requested` = `rememberSaveable` 로 회전에도 재요청 안 함).
   지도 자리에 기존 `LocationPermissionNotice` + 신규 「위치 권한 허용」 버튼이 남아
   사용자가 직접 재요청할 수 있다. 영구 거부면 시스템이 다이얼로그 없이 즉시 거부를 돌려주고
   안내가 그대로 유지된다(무한 루프 없음).
4. 설정에서 권한을 바꾸고 복귀하는 경로: `Lifecycle.Event.ON_RESUME` 마다
   `checkSelfPermission` 재확인 → 허용됐으면 그 자리에서 구독이 시작된다.
5. 권한이 회수되면 `coordinates = null` 로 되돌려 낡은 좌표를 계속 쓰지 않는다.

### 좌표 취득 (에뮬레이터 포함)

네이버 SDK 부속 `FusedLocationSource` 는 **쓰지 않았다**. 이유 두 가지:
(a) `map-sdk-3.23.3.aar` 를 열어 보면 내부 구현이 `com.google.android.gms.location.*` 를 직접
참조하는데 현재 의존성 트리에 `play-services-location` 이 없어 그대로 쓰면 런타임에 터진다
(= 어차피 같은 의존성을 추가해야 한다),
(b) `Activity` + `onRequestPermissionsResult` 포워딩을 요구해 Compose 권한 런처와 맞지 않고,
좌표를 지도 밖(15 화면)으로 꺼내기도 어렵다.

대신 `FusedLocationProviderClient` 를 직접 쓰고 3단 폴백을 뒀다:

1. `LocationManager` 의 마지막 위치를 **즉시 1회** 흘려보낸다 (첫 fix 대기 동안 지도가 폴백 좌표에 머무르지 않게).
2. Fused Provider 주기 갱신 — `PRIORITY_BALANCED_POWER_ACCURACY`, 10초 간격 / 최소 5초 / 10m.
3. Play services 가 없거나(`getFusedLocationProviderClient` 예외) 구독 `Task` 가 실패하면
   프레임워크 `LocationManager`(GPS·NETWORK·PASSIVE 중 켜진 것)로 갈아탄다.
   API 29 이하 `AbstractMethodError` 방지를 위해 `LocationListener` 4개 메서드를 모두 구현했다(minSdk 24).

---

## 폴백 규칙 (권한 거부 시 크래시 없음 — 코드 레벨 재확인)

| 상황 | 좌표 | 합승 탭 지도 | 15 출발지 |
|------|------|--------------|-----------|
| 권한 허용 + fix 수신 | 실좌표 | 실위치 중심 · 마커 캡션 `"내 위치"` | `Place("현재 위치", "", 실좌표)` |
| 권한 허용 + fix 아직 없음 | `null` | `FallbackOrigin` 중심 · 캡션 `"내 위치(기준점)"` | `DemoOrigin` |
| 권한 거부 | `null` | 지도 대신 권한 안내 + 「위치 권한 허용」 | `DemoOrigin` |
| GPS 꺼짐 / 제공자 없음 | `null` | 위와 같은 기준점 폴백 | `DemoOrigin` |

크래시 방지 근거:
- 위치 API 는 `granted == true` 인 `DisposableEffect` 분기에서만 호출된다 → `SecurityException` 경로 없음.
- 그 안에서도 모든 호출이 `runCatching`(= `Throwable` 포착) 안에 있다. Play services 클래스 부재
  (`NoClassDefFoundError`)도 여기서 잡혀 프레임워크 경로로 떨어진다.
- 0,0(널섬)·NaN·범위 밖 좌표는 `toUserCoordinatesOrNull()` 이 버린다 → 잘못된 fix 가 폴백보다 나쁜 상황 차단.
- 컴포저블이 사라지면 `onDispose` 에서 fused·프레임워크 구독을 모두 해제한다(백그라운드 위치 없음).

## 16 모달 폴백 점검 결과

`MainNavGraph.kt:265` 의 `origin = confirmedOrigin ?: DemoOrigin` 과
`DestinationConfirmRoute.kt:83` 의 기본값 `origin: Place = DemoOrigin` 은 **그대로 뒀다.**
16 으로 가는 유일한 경로가 15 의 `onConfirmRoute(state.origin, destination)` 이고,
`state.origin` 이 이미 「실위치 > DemoOrigin」 우선순위를 거친 값이라 실위치가 있으면 그 좌표가
그대로 흘러간다. 두 `DemoOrigin` 은 실사용에서 도달하지 않는 방어적 기본값이다
(`POST /matching/rooms` 의 `departureLat/Lng` 는 `origin.latitude/longitude` 를 그대로 쓴다).

---

# 실기 검증 후속 수정 (R-1 · R-2)

**재검증 빌드**: `./gradlew :presentation:compileDebugKotlin :app:assembleDebug --console=plain` → BUILD SUCCESSFUL

## R-1 · 위치 우선순위 BALANCED → HIGH_ACCURACY

**증상**: `dumpsys location` 의 GMS 요청이 `Request[BALANCED …]` 로 나가 GPS 하드웨어가 켜지지 않고,
`adb emu geo fix` 주입이 gps provider 로만 들어오는 에뮬레이터에서는 fused 가 네트워크 위치
(구글 본사)에 고정됐다. `debug_gps_04_reenter.png` 의 마커가 Mountain View 에 찍힌 게 그 결과다.

| 파일:라인 | 변경 |
|-----------|------|
| `MyLocationState.kt:219` | `Priority.PRIORITY_BALANCED_POWER_ACCURACY` → **`Priority.PRIORITY_HIGH_ACCURACY`** |
| `MyLocationState.kt:70–76` | `MinUpdateDistanceM` 10f → **0f** (이동 거리 필터 제거) |

주기(10초 / 최소 5초)는 그대로 두었다.

거리 필터를 함께 없앤 이유: 필터가 있으면 **제자리에서 정확도만 개선되는 fix 가 통째로 버려진다.**
HIGH_ACCURACY 로 바꾸는 목적이 첫 부정확한 네트워크 좌표를 GPS 좌표로 교정받는 것인데,
10m 필터가 그 교정을 막는다. 갱신 빈도는 `UpdateIntervalMs` 로 이미 제한된다.

## R-2 · 권한 허용 직후 빈 지도 — **계측으로 원인 확정**

> 1차 수정에서 「LifecycleRegistry 가 따라잡기 이벤트를 캡한다」고 추정해 고쳤으나 **재현이 계속됐다.**
> 그 추정은 아래 계측으로 **반증**됐다. 이번엔 에뮬레이터에서 직접 재현·계측해 원인을 확정했다.

### 원인 (확정)

**SurfaceView 기반 `MapView` 가 「이미 정상적으로 그려지고 있는 윈도우」에 뒤늦게 붙으면
SurfaceView 의 서피스가 생성되지 않는다.** 서피스가 없으면 네이티브 렌더러가 시작되지 않고,
그래서 `getMapAsync` 콜백조차 오지 않아 지도 배경색(#F3F2F1)만 남는다.

위치 권한을 허용하면 `LocationPermissionNotice` → `NaverMapView` 로 브랜치가 바뀌는데,
이때 호스트는 **이미 RESUMED 인 정상 상태**다. 반면 탭 재진입 시에는 목적지가 STARTED 로 올라오는
전환 중에 붙기 때문에 서피스가 정상 생성된다 — 「재진입하면 된다」가 여기서 나왔다.

### 계측 증거

임시 `Log`(태그 GPSDBG)로 브랜치·권한·MapView 생명주기·뷰 트리를 찍었다.

**1) 생명주기는 정상이었다 — 1차 추정 반증**
```
LocState: launcher RESULT {ACCESS_FINE_LOCATION=true, ACCESS_COARSE_LOCATION=true}
PeekContent: locationGranted=true          ← 지도 브랜치로 정상 진입
NaverMapView: MapView CREATED
NaverMapView: effect ENTER lifecycle=RESUMED
NaverMapView: sync target=RESUMED started=false resumed=false
NaverMapView: -> onStart()
NaverMapView: -> onResume()
(getMapAsync FIRED 없음)                    ← onStart/onResume 은 다 갔는데 지도가 안 뜬다
```
`onStart()`/`onResume()` 이 **정상 호출**됐다. 즉 생명주기 전달 문제가 아니다.
(onStart 직후 onResume 이 붙는 게 문제인가 싶어 onResume 을 다음 tick 으로 미뤄 봤지만 **여전히 실패** —
adjacency 가설도 반증.)

**2) 뷰 트리 — 서피스가 없다**

실패한 지도(권한 허용 직후, RESUMED 에서 attach):
```
TREE MapView 1080x1957 vis=0 winVis=0 attached=true
TREE   <SurfaceView> 1080x1957 attached=true  surfaceValid=false  holderSize=Rect(0,0-0,0)
TREE   ZoomControlView 0x0 vis=8 / LogoView 0x0 vis=8   ← 컨트롤도 전부 GONE
```
정상 지도(같은 실행, STARTED 에서 attach):
```
TREE MapView 1080x2400 attached=true
TREE   <SurfaceView> 1080x2400 attached=true  surfaceValid=true  holderSize=Rect(0,0-1080,2400)
```
**뷰 크기는 1080x1957 로 정상인데 서피스만 0x0 · invalid.** 레이아웃/가시성 문제가 아니라
서피스 생성 실패다. 스크린샷 픽셀도 `#F3F2F1` 로, `SurfaceSoft(#F4F6FA)`·`CanvasBg(#F5F7FA)`
어느 것도 아닌 **네이버 지도 자체 배경색**이었다(= MapView 는 거기 있었다).

**3) 수정 후 같은 지점**
```
NaverMapView: effect ENTER lifecycle=RESUMED
NaverMapView: getMapAsync FIRED
TREE   <SurfaceView> 1080x1957 surfaceValid=true holderSize=Rect(0,0-1080,1957)
```

### 수정

| 파일:라인 | 변경 |
|-----------|------|
| `core/designsystem/.../NaverMapView.kt:186–196` | `AndroidView(factory = …)` 에서 **`mapView.post { mapView.requestLayout() }`** — 붙은 다음 프레임에 레이아웃을 한 번 더 돌려 SurfaceView 가 서피스를 다시 요청하게 한다 |
| `NaverMapView.kt:95–122` | 생명주기 전달을 멱등 `sync(target: Lifecycle.State)` 상태 머신으로 유지 (onStart→onResume / onPause→onStop 순서 보장, 중복 호출 방지). 계측상 정상 동작 확인 |

`requestLayout()` 만으로 충분했다(`invalidate()` 는 불필요 — 따로 검증). 서피스가 이미 있는
정상 경로에서는 레이아웃 1회 외에 부작용이 없고, `NaverMapView` 를 쓰는 모든 화면에 함께 적용된다.

### 덤으로 고친 것 — 「위치 권한 허용」 버튼 먹통

재검증 중 발견: 한 번 거부하면 시스템이 `USER_FIXED` 를 세워 이후 `launch()` 는 **다이얼로그 없이
즉시 거부**만 돌려준다. 그래서 안내 UI 의 버튼이 아무 반응 없는 것처럼 보였다.

| 파일:라인 | 변경 |
|-----------|------|
| `MyLocationState.kt:108–118` | 영구 거부면 `launch` 대신 앱 설정 화면으로 보낸다 |
| `MyLocationState.kt:160–178` | `Activity.canPromptForLocation()`(`shouldShowRequestPermissionRationale`) · `Activity.openAppSettings()` |

설정에서 허용하고 돌아오면 기존 `ON_RESUME` 재확인이 상태를 받아 지도로 전환된다
(이 경로도 RESUMED 에서의 브랜치 스왑이라 위 서피스 수정이 함께 필요했다).

### 재검증 결과 (에뮬레이터 직접 실행)

권한 revoke → force-stop → 온보딩 → 로그인 → 합승 탭 → 「While using the app」 절차 그대로.

| 스크린샷 | 결과 |
|----------|------|
| `gpsfix_01_permission.png` | 탭 진입 시 권한 다이얼로그 |
| `gpsfix_02_granted_map.png` | **허용 직후 재진입 없이 지도 렌더** — 로고·줌 컨트롤·「내 위치」 마커(서면역, 주입 좌표) |
| `gpsfix_03_panned.png` | 팬 정상 |
| `gpsfix_04_tab_return.png` | 홈 탭 왕복 후 정상 |
| `gpsfix_05_after_rotate.png` | 회전(가로→세로) 후 정상 |
| `gpsfix_06_denied.png` | 거부 시 크래시 없이 안내 + 버튼 |
| `gpsfix_08_settings_return.png` | 영구 거부 → 버튼 → 설정 → 허용 → 복귀 시 지도 렌더 |

`AndroidRuntime:E` 크래시 로그 없음. 임시 `Log` 는 전부 제거했고
`./gradlew :presentation:compileDebugKotlin :app:assembleDebug --console=plain --rerun-tasks` 통과.

## 미해결 / 후속 제안

1. **역지오코딩** — 실위치 출발지의 이름이 `"현재 위치"` 로 고정이라 그대로 방을 만들면
   서버에 `departure = "현재 위치"` 로 저장된다(`DestinationConfirmRoute.kt:50`).
   다른 사용자의 방 목록에 「현재 위치」가 여러 개 뜨게 되므로, 좌표 → 주소 변환이 붙어야 한다.
   **백엔드 또는 네이버 Reverse Geocoding API 필요** — api-integrator 협의 대상.
2. **15 화면 권한 요청** — 현재는 합승 탭에서만 요청한다. 홈 → 15 로 바로 들어온 사용자는
   권한을 준 적이 없어 `DemoOrigin` 을 쓰게 된다. 출발지 행에 「현재 위치로 설정」 버튼을 붙여
   그 자리에서 요청하게 하는 것이 다음 단계.
3. **14 홈 지도** — 이번 범위 밖이라 그대로 두었다. 같은 `rememberMyLocationState()` 로
   동일하게 붙일 수 있다.
