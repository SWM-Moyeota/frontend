# 02 · compose-builder — 화면-API 연결 (매칭 재구성 · 배차 · 신고)

날짜: 2026-08-30 · 브랜치: `feature/naver-map`
계약 출처: `01_api-integrator_report.md` (Repository 시그니처 단일 진실)
검증: `./gradlew :presentation:compileDebugKotlin :app:assembleDebug --console=plain` ✅ **BUILD SUCCESSFUL**

`data/` · `domain/` 은 **한 줄도 건드리지 않았다.** naver-map 미커밋 변경도 그대로 두었다.
`app/` 은 `MainActivity` 의 `MainNavGraph(...)` 호출 인자 2개 추가만 했다(컴파일에 필수).

---

## 1. 변경·신규 파일

### 신규
| 파일 | 내용 |
|---|---|
| `core/designsystem/.../component/RouteMapView.kt` | `decodePolyline`(L31) + 경로·마커 지도 `RouteMapView`(L77) + `MapMarker`(L126) |
| `presentation/.../feature/matching/DispatchStatusRoute.kt` | 25 배차 현황 ViewModel + Route |
| `presentation/.../feature/chat/EmergencyRoute.kt` | 27 신고 ViewModel + Route |

### 수정
| 파일:라인 | 내용 |
|---|---|
| `feature/matching/MatchWaitingRoute.kt` | **전면 재작성.** `toggleReady`/`startMatching` 삭제, `ActionState`(L43)에서 `ready`·`matchingStarted` 제거, `observeAutoMatching()`(L74) 폴링 추가, Route 가 `status` 관찰(L142) |
| `feature/matching/MatchWaitingScreen.kt:87` | 파라미터에서 `isHost`·`isReady`·`onToggleReady`·`onStartMatching` 제거 |
| `feature/matching/MatchWaitingScreen.kt:100` | `isFull`·`progress` 파생 — 진행바가 하드코딩 `150f/361f` → 실제 `인원/정원` |
| `feature/matching/MatchWaitingScreen.kt:175` | 자동 매칭 안내 `NoticeBanner` 신규 |
| `feature/matching/MatchWaitingScreen.kt:215` | 「매칭 시작하기」·「준비 완료/취소」 버튼 삭제 → 「그만 찾기」 단독 |
| `feature/matching/MatchWaitingScreen.kt:283` | `GrayActionButton` 에 `enabled` 추가(나가는 중 재클릭 차단) |
| `feature/explore/JoinConfirmRoute.kt:42,72,96,115` | `joined: Boolean` → `joinedRide: Ride?`. 합류 응답을 `UiState.Success` 로 덮고 `onJoined(Ride)` 로 전달 |
| `feature/explore/JoinConfirmRoute.kt` | `ApiNotAvailableException` catch 분기 제거(엔드포인트 생겨 도달 불가) |
| `feature/matching/DispatchStatusScreen.kt:92` | 파라미터 교체 — `arrivalMinutes/pickupSpot/vehicleNumber/vehicleModel/driverLabel` → `driver: AssignedDriver?`, `driverLocation: DriverLocation?` |
| `feature/matching/DispatchStatusScreen.kt:136` | 정적 Canvas `DriverMapArea` **삭제** → `RouteMapView` 로 교체 |
| `feature/matching/DispatchStatusScreen.kt:205` | 하드코딩 `"김OO 기사 · 별점 4.9"` → `"기사 정보 준비 중"` |
| `feature/matching/DispatchStatusScreen.kt:274` | `latLngOrNull` 헬퍼 |
| `feature/chat/EmergencyScreen.kt:85` | `EmergencyScreen` 이 `Box{ EmergencyForm + CallResultOverlay }` 로 분리(L113/L312) |
| `feature/chat/EmergencyScreen.kt:230,274` | 접수 실패 `NoticeBanner(ERROR)` · 접수 중 버튼 라벨/비활성 |
| `core/MainNavGraph.kt:65-66` | 파라미터 `dispatchRepository`·`reportRepository` 추가 |
| `core/MainNavGraph.kt:82` | `activePartyId` 상태 추가(운행 중인 방 — 25·27 이 사용) |
| `core/MainNavGraph.kt:297` | JOIN_CONFIRM `onJoined` → MATCH_WAITING (기존 RIDE_DETAIL 에서 변경) |
| `core/MainNavGraph.kt:323` | MATCH_WAITING → DISPATCH_STATUS 시 `popUpTo(MATCH_WAITING){inclusive}` |
| `core/MainNavGraph.kt:351,386` | `DispatchStatusScreen`→`DispatchStatusRoute`, `EmergencyScreen`→`EmergencyRoute` |
| `app/.../MainActivity.kt` | `MainNavGraph` 에 `container.dispatchRepository`·`container.reportRepository` 전달 |

---

## 2. 화면별 연결 상태

| 화면 | 호출 | 상태 |
|---|---|---|
| 21 매칭 대기 | `getPartyDetail` 최초 + **4초 폴링**, `leaveParty` | ✅ ready/start 제거 완료 |
| 20 합류 확인 | `getPartyDetail`, `joinParty → Ride` | ✅ 응답 Ride 로 상세 갱신(재조회 0회) |
| 25 배차 현황 | `getPartyDetail`, `getAssignedDriver`, `getDriverLocation` **5초 폴링** | ✅ 지도·차량정보 실데이터 |
| 25 경로 | `Ride.routePolyline` 디코딩 | ✅ `previewRoute` 미사용(호출 불가) |
| 27 긴급 신고 | `report(NewReport) → reportId`, `confirmCallResult` | ✅ 2단계 연결 |

---

## 3. UI 재구성 결정 사항

### 3-1. 21 매칭 대기 — 「준비→출발」 삭제, 「자동 배차 대기」로 재구성
백엔드에서 `/matching/ready`·`/matching/start` 가 사라지고 **정원 도달 시 서버가 스스로 매칭을 시작**한다.
앱이 트리거할 것이 없으므로 버튼을 지우고, 대신 **지금 무슨 일이 일어나는지 보이게** 바꿨다:

- 헤드라인: 정원 미달 `"보통 2분 안에 찾아요"` / 정원 도달 `"정원이 다 찼어요"`
- 부제: `"지금 같은 방향 N명 · 목표 M명"` (기존 `"· 나는 준비 완료"` 로컬 플래그 삭제)
- 진행바: **하드코딩 41% → 실제 `members.size / capacity`**
- 안내 배너: 미달 `INFO "정원이 차면 기사님이 자동으로 배차돼요"` / 도달 `WAITING "기사님을 찾고 있어요…"`
- 버튼: 「그만 찾기」 하나 (전폭). 나가는 중에는 라벨 `"나가는 중…"` + 비활성

**전이 관찰**: 매칭 시작 버튼이 없으니 25로 넘어가는 신호는 서버 status 뿐이다.
4초 폴링으로 상세를 다시 읽어 `RECRUITING/MATCHED` 를 벗어나면 폴링을 끝내고 Route 가 25로 넘긴다.
폴링 실패는 화면을 에러로 덮지 않고 다음 주기를 기다린다.

### 3-2. 20 합류 → **21 대기**로 목적지 변경 (기존: 22 상세)
준비/출발 버튼이 사라진 뒤 합류의 다음 단계는 "정원이 차기를 기다리는 것"이다.
합류 응답 `Ride` 를 그대로 넘기고 `popUpTo(JOIN_CONFIRM){inclusive}` 로 확인 화면을 스택에서 지운다.

### 3-3. 25 배차 현황 — 정적 지도 → 네이버 지도 교체 (판단 근거 포함)
`DriverMapArea` 는 도로 블록을 Canvas 로 흉내낸 **정적 플레이스홀더**였다. HomeScreen 이 이미
`core:designsystem`의 `NaverMapView` 를 쓰고 있고 SDK 가 `api` 로 노출돼 있어 교체 가능하다고 판단했다.

- 신규 `RouteMapView` 를 **designsystem 에 승격**했다 — 오버레이(경로·마커) 생명주기 관리는
  화면 로직이 아니라 `NaverMapView` 옆에 있어야 할 지도 배선이고, 26 운행 중에서도 곧 필요하다
- **카메라 중심은 출발지 고정.** 폴링으로 움직이는 기사 위치를 중심으로 쓰면 5초마다 카메라가
  튀어 사용자의 팬·줌을 덮어쓴다. 마커만 움직인다
- **경로는 `Ride.routePolyline`** 을 `decodePolyline` 으로 디코딩해 `PathOverlay` 로 그린다.
  `previewRoute` 는 호출 불가(GET+body)라 쓰지 않았다. 디코더는 표준 테스트 벡터
  `_p~iF~ps|U_ulLnnqC_mqNvxq`@` → `(38.5,-120.2) (40.7,-120.95) (43.252,-126.453)` 로 검증했고,
  깨진 문자열은 예외 대신 **읽은 데까지만** 반환한다(지도 때문에 화면이 죽으면 안 된다)
- 화면 루트의 `clickable{onStartRide}` 는 유지. 지도가 자기 제스처를 먼저 소비하므로 팬·줌과 충돌하지 않는다

**기사 정보 표시** (백엔드에 이름·별점 필드 없음 — 01 보고서 3절-3):
| 자리 | 값 |
|---|---|
| 차량번호 | `driver.plateNumber` (미수신 시 `"차량 번호 확인 중"`) |
| 차종·좌석 | `"{vehicleType} · {seats}인승"` (미수신 시 `"차량 정보를 불러오는 중이에요"`) |
| 기사명·별점 | **`"기사 정보 준비 중"` 플레이스홀더** ← 하드코딩 `"김OO 기사 · 별점 4.9"` 대체 |
| 헤드라인 | 기사 미배정 `"기사님을 배정하고 있어요"` / 배정 `"기사님이 오고 있어요"` |
| 부제 | 위치 미수신이면 `"… · 기사님 위치를 받아오는 중"` |

**폴링 중단**: `pollDriver()` 를 `suspend fun` 으로 만들어 Route 의 `LaunchedEffect` 에서 돌린다.
화면을 벗어나면 컴포지션 이탈로 코루틴이 취소돼 폴링이 함께 멈춘다(요구사항: 화면 이탈 시 중단).
기사 미배정·위치 미보고는 서버가 거절하는 **정상 상황**이라 실패를 삼키고 다음 주기를 기다린다.
`getAssignedDriver` 는 status 가 `DISPATCHING/ONGOING` 일 때만 호출한다(01 보고서 7절).

### 3-4. 27 신고 — 새 화면 없이 오버레이 1개만 추가
접수와 통화 확인은 한 흐름이라 화면을 가르면 중간 이탈 시 서버 신고 레코드가 미확정으로 남는다.
그래서 **신고 화면 위 스크림 카드**로 처리했다(신규 라우트 0개).

- 3초 롱프레스 → `report(NewReport(reporterId, partyId))` → 성공 시 `reportId` 보관
- `reportId != null` → 「신고가 접수됐어요 / 112에 전화하셨나요?」 카드 (스크림 탭으로 안 닫힘)
- 「전화했어요」/「아직이요」 → `confirmCallResult(reportId, true|false)` → 26 운행 중 복귀
- 통화 기록 실패는 삼키고 화면을 닫는다 — 신고 자체를 무르게 만들면 안 된다
- 좌표는 **보내지 않는다**(null). 위치 권한 흐름이 아직 없고 서버가 null 을 허용한다

**⚠️ 서버 계약 공백**: `ReportRequest` 에 사유·상세 텍스트 필드가 없다(`reporterId·partyId·좌표`뿐).
화면에서 고른 4개 사유와 「직접 설명할게요」 입력은 **아직 서버로 가지 않는다**. 백엔드 요청 대상.

### 3-5. `activePartyId` 분리
25·27 이 쓸 partyId 를 탐색용 `selectedPartyId` 와 분리했다. 탐색 탭에서 다른 방을 눌러도
내 운행 중인 방이 바뀌면 안 된다. 방 생성·합류·출발·배차 시작 4곳에서 갱신한다.

---

## 4. qa-verifier 확인 진입 경로

전제: 백엔드 `feature/driver-report` 기동(`http://10.0.2.2:8080/`), 에뮬레이터 **콜드부트**.
`userSession.currentUserId` 는 고정 `1L`.

| # | 화면 | 경로 |
|---|---|---|
| 1 | **21 매칭 대기(생성)** | 온보딩 건너뛰기 → 04 로그인 → 14 홈 → 검색 → 15 목적지 선택 → 16 모달 「방 만들기」 → **21** |
| 2 | **21 자동 매칭 대기 UI** | 위 상태에서 진행바가 `현재/정원` 비율인지, 배너가 「정원이 차면 자동 배차」인지, **준비/매칭 시작 버튼이 없는지** |
| 3 | **자동 전이** | 다른 계정으로 정원까지 채우면 4초 내 자동으로 **25 배차 현황** 진입. 25에서 뒤로 → 21 로 돌아가지 않아야 함(스택에서 제거) |
| 4 | **20 합류 → 21** | 14 홈 → 하단탭 「합승」(17) → 목록 카드 「합류」(20) → 「이 탑승에 합류하기」 → **21 매칭 대기**(22 상세 아님) |
| 5 | **25 배차 현황** | 21 자동 전이, 또는 22 탑승 상세 「이 인원으로 출발」 → **25**. 지도에 네이버 지도 타일 + 경로선 + 기사 마커 |
| 6 | 25 미배정 상태 | 기사 배정 전 진입 시 「기사님을 배정하고 있어요」 + 「차량 번호 확인 중」, **에러 화면으로 덮이지 않아야 함** |
| 7 | 25 폴링 중단 | 25 → 뒤로/화면 이탈 후 logcat 에서 `/dispatch/rides/` 요청이 멈추는지 |
| 8 | **27 긴급 신고** | 25 화면 탭 → 26 운행 중 → 「신고」 → **27**. 사유 선택 → 3초 롱프레스 → 접수 → 「112에 전화하셨나요?」 카드 → 버튼 → 26 복귀 |
| 9 | 27 실패 경로 | 탑승 중(IN_RIDE)이 아닐 때 신고 → CTA 위 빨간 배너 「신고를 접수하지 못했어요…」, 카드 안 뜸 |
| 10 | 로딩/에러 3상태 | 21·25 는 `BackStateScaffold` 라 로딩·에러에서도 **뒤로가기가 살아 있어야** 함(QA F-1 동류). 에러 시 「다시 시도」 동작 |

### 교차 검증 요청 (01 보고서 10절과 함께)
- `joinParty` 응답에 `route`/`estimateFare`/`taxiDriverId` 가 실제로 담기는지 — **25 지도 경로가 이 값에 전적으로 의존**한다
- `joinedAt` 이 ISO-8601 문자열인지 (숫자면 상세 역직렬화가 통째로 깨져 21·25 가 모두 에러)
- `getDriverLocation` 이 기사 미배정 시 던지는 상태코드 — 현재 앱은 **모든 실패를 삼킨다**

---

## 5. 남은 백엔드 요청 (01 보고서 9절 + 신규 1건)

1. `DriverSummary` 에 기사 **이름·별점** 추가 → 「기사 정보 준비 중」 플레이스홀더 해제 (01-3절)
2. `GET /matching/routes` 를 `@RequestParam`/`POST` 로 변경 (01-4절) — 현재는 `routePolyline` 우회로 충족
3. matching/dispatch/report 전역 예외 핸들러 (01-7절) — 신고 실패 사유를 사용자에게 못 보여준다
4. **신규**: `ReportRequest` 에 **신고 사유·상세 텍스트** 필드 추가.
   27 화면은 사유 4종 + 500자 입력을 이미 받고 있는데 담아 보낼 자리가 없다.

---

## 6. QA 결함 조치 — D-3 (2026-08-30 추가)

### D-3 · 26 운행 중의 15초 데모 타이머가 27 신고 진입 차단 🟡 Medium → ✅ 수정

**증상**(QA 3/3 재현): 26 진입 15초 뒤 무조건 28 요금 확정으로 전환. 그 뒤 「신고」 자리를 누르면
28 의 확인 버튼이 눌려 정산으로 샌다. 신고는 급할 때 누르는 기능인데 15초 제한이 붙은 셈이었다.

#### 왜 「서버 상태 폴링」으로 대체하지 않았는가
`FINISHED/CANCELED → RideStatus.COMPLETED` 매핑이 있어 `getPartyDetail` 폴링이 형식상 가능하지만,
**지금은 절대 발화하지 않는다** — QA D-2 가 확인한 대로 백엔드 `PartyAccessService.startRide`/
`completeRide` 에 `@Transactional`·`parties.save()` 가 없어 파티가 `DRIVER_ASSIGNED` 를 못 벗어난다.
폴링으로 갈아끼우면 **28~32 결제 화면 5개가 통째로 도달 불가**가 되어 QA 커버리지가 되레 줄어든다.
그래서 코디네이터 지침대로 **타이머 제거 + 기존 화면 액션 재활용**을 택했다.

#### 변경 내역
| 파일:라인 | 내용 |
|---|---|
| `core/MainNavGraph.kt:370-384` | `LaunchedEffect{delay(15_000); navigate(FARE_FINAL)}` **삭제**, `onArrived` 배선 추가 |
| `core/MainNavGraph.kt:383` | `onArrived = { navController.navigate(Routes.FARE_FINAL) }` |
| `core/MainNavGraph.kt` (imports) | 미사용이 된 `LaunchedEffect`·`kotlinx.coroutines.delay` import 제거 |
| `feature/chat/RideOngoingScreen.kt:85` | `onArrived: () -> Unit = {}` 파라미터 추가 |
| `feature/chat/RideOngoingScreen.kt:150-159` | **기존** 경유 순서 카드에 `clickable { onArrived() }` 부여 (신규 UI 요소 0개) |
| `feature/chat/RideOngoingScreen.kt:66-71` | KDoc — 「[미연결] 도착 후 자동 전환」 → 임시 수동 트리거 + 교체 조건 명시 |

경유 카드를 고른 이유: 마지막 단계 라벨이 이미 **「내린 뒤 현장에서 1/N 정산」** 이라 카드 자체가
곧 다음 행동(하차→정산)을 가리킨다. 「신고」·「채팅 열기」는 각자 의미가 있어 재활용하면 안 된다.

**교체 조건**: 백엔드 D-2 수정으로 파티가 `FINISHED` 에 도달하면, `onArrived` 를
`getPartyDetail` status 관찰로 갈아끼우면 된다(21 대기 화면의 `observeAutoMatching` 과 동형).

#### 재검증 진입 경로
| # | 확인 |
|---|---|
| 1 | 25 배차 현황 → 화면 탭 → 26 운행 중 → **15초 이상 체류해도 28로 안 넘어감** (D-3 회귀) |
| 2 | 26 에서 15초 뒤 「신고」 탭 → **27 긴급 신고** 진입 (정산 아님) — B4a/B4b 재검증 차단 해제 |
| 3 | 26 → **경유 순서 카드(「부산대 정문 · 탑승 완료 / 서면역 …로 이동 중 / 내린 뒤 현장에서 1/N 정산」 3단 카드) 탭** → 28 최종 요금. 28~32 결제 플로우 경로 유지 확인 |

> ⚠️ 진입 경로 3은 **탭 대상이 카드라 육안으로 드러나지 않는다.** 임시 트리거라 와이어프레임 카피를
> 바꾸지 않았다(과한 신규 UI 금지). QA 는 위 카드를 직접 탭해 28로 진입할 것.

## 7. QA 결함 조치 — D-1 2차 방어 (2026-08-30 추가)

### D-1 · 기사 위치 위경도 전치 → 마커 미렌더 🔴 High (근본 원인은 백엔드) → ✅ 프론트 2차 방어 완료

**실측**(QA): `GET /api/v1/dispatch/rides/6/42` → `{"longitude":37.568, "latitude":126.980}` — 값이 서로 바뀌어 있다.
근본 수정은 백엔드 몫(`DriverLocationRedis.java:69` 2단 전치 누적). 프론트는 **깨진 값이 와도 안전하게 버티는** 방어만 넣었다.

#### 전치(swap) 보정을 하지 않은 이유
프론트에서 `LatLng(longitude, latitude)` 로 되돌려 끼우면 당장은 맞아 보이지만,
백엔드가 D-1 을 고치는 순간 **같은 버그가 반대 방향으로 재현**된다. 범위 검증 + 렌더 스킵만이
백엔드 수정 전후 **모두** 안전하다. 코드베이스 전체에 좌표를 뒤바꾸는 코드는 없다(grep 확인).

#### ⚠️ `LatLng.isValid()` 를 쓰지 않은 이유 (중요)
SDK 의 `LatLng.isValid()` 바이트코드를 뜯어보니 **NaN·Infinite 만 검사하고 범위는 보지 않는다**:
```
isValid(): Double.isNaN(lat) || Double.isNaN(lng) || Double.isInfinite(lat) || Double.isInfinite(lng) → false
```
즉 D-1 의 `latitude = 126.98`(지구상에 없는 위도)도 **통과시킨다**. 그래서 SDK 상수
`LatLng.MINIMUM_LATITUDE`(-90.0) ~ `MAXIMUM_LATITUDE`(90.0), `MINIMUM_LONGITUDE`(-180.0) ~
`MAXIMUM_LONGITUDE`(180.0) 로 **직접 범위를 검사**한다.

#### 변경 내역
| 파일:라인 | 내용 |
|---|---|
| `core/designsystem/.../RouteMapView.kt:65-82` | 공개 검증 팩토리 `latLngOrNull(latitude, longitude): LatLng?` **신규**. null·NaN·Infinite·범위 밖이면 null 반환 |
| `feature/matching/DispatchStatusScreen.kt:100-102` | `driverPosition` 을 **한 번만** 계산 — 마커와 안내 문구가 같은 판정을 공유 |
| `feature/matching/DispatchStatusScreen.kt:142` | `driverPosition = driverPosition` — 범위 밖이면 null → **마커 미표시** |
| `feature/matching/DispatchStatusScreen.kt:171` | 문구 조건을 `driverLocation == null` → **`driverPosition == null`** — 못 쓸 좌표에도 「위치를 받아오는 중」 유지 |
| `feature/matching/DispatchStatusScreen.kt` | 검증 없던 화면 로컬 `private fun latLngOrNull` **삭제**, 위 공용 팩토리로 대체 |

**부수 효과(의도됨)**: 출발지·도착지 마커도 같은 팩토리를 타므로 서버가 이상 좌표를 주면
마커를 건너뛴다. `RouteMapView` 의 카메라 중심도 `originPosition ?: routePath.first() ?: 기본 카메라`
순으로 안전하게 흘러내린다.

#### 재검증 진입 경로
| # | 확인 |
|---|---|
| 1 | **D-1 미수정 상태**(현재): 25 배차 현황에서 기사 위치 보고 후에도 **마커가 안 뜨고** 부제가 「… · 기사님 위치를 받아오는 중」 유지. **크래시·엉뚱한 위치 마커 없음**이 핵심 |
| 2 | **D-1 수정 후**: 같은 화면에서 마커가 출발지 근처에 정상 렌더 + 부제에서 「받아오는 중」 사라짐. 프론트 추가 수정 불필요해야 함 |
| 3 | 회귀 — 경로 폴리라인·출발지 마커는 기존대로 렌더(`api2_07`, `api2_14` 동일) |

---

## 8. 배정 완료 상태

| 결함 | 담당 | 상태 |
|---|---|---|
| D-1 (2차 방어) | compose-builder | ✅ 7절 |
| D-3 (데모 타이머) | compose-builder | ✅ 6절 |
| D-1 (근본), D-2, D-6 | 백엔드 | ⬜ 대기 — **D-2 해제 전까지 27 신고 정상 경로 검증 불가** |
| D-4 (지도 키) | 리더/환경 | ⬜ 대기 |
| D-5 (`coerceInputValues`) | api-integrator | ⬜ 대기 |
