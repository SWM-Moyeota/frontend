# 03 · qa-verifier — 백엔드 연동 실기 검증

날짜: 2026-08-30 · 브랜치: `feature/naver-map`
빌드: `./gradlew :app:assembleDebug :data:testDebugUnitTest --rerun-tasks` ✅ **BUILD SUCCESSFUL** · **41 tests / 0 failures / 0 errors**
백엔드: `../backend` (`feature/driver-report`) **로컬 기동 성공** — H2 인메모리 + Redis(docker). **실서버 검증 완료** (01 보고서의 "실서버 미검증" 플래그 해소)
에뮬레이터: Pixel_6 콜드부트

**집계: 통과 19 · 실패 4 · 미검증 2**

> ⚠️ 검증 중 다른 세션이 같은 에뮬레이터에서 `com.moyeota.driver`(기사 앱)를 반복 포그라운드로 띄워 조작을 방해했다.
> 일부 화면은 재시도해서 확보했고, 27 신고 접수 1건은 이 경합 + D-2/D-3 때문에 끝내 미검증으로 남았다.

---

## 1. 항목별 결과

### A. 경계면 교차 비교 — **실서버 curl 응답으로 확인** (정적 추론 아님)

| # | 항목 | 결과 | 근거 |
|---|---|---|---|
| A1 | 방 생성 `creatorId` | ✅ 통과 | `POST /api/v1/matching/rooms` with `creatorId` → **200**, `currentMembers:1`. 백엔드 `OpenPartyRequest.java:3-5` 필드 10개가 `PartyDtos.kt:62-73` 과 이름·순서까지 일치. api-integrator 의 `hostId→creatorId` 수정이 옳았다 |
| A2 | `joinedAt`·`createdAt` 타입 | ✅ 통과 | 실응답 `"joinedAt":"2026-08-30T07:54:05.292839Z"` — **ISO-8601 문자열**. Jackson 3(`tools.jackson` 3.1.5)이지만 `WRITE_DATES_AS_TIMESTAMPS` 기본 off, `spring.jackson.*` 설정·`@JsonFormat` 없음. 숫자로 내려올 위험 없음 |
| A3 | join/상세의 `route`·`estimateFare`·`taxiDriverId` | ✅ 통과 | join 응답에 `route`(879자)·`estimateFare:13100`·`estimateTime:34` **모두 포함**. `route` 는 **방 생성 시점**에 산출·저장되며(`PartyApplicationService.java:35` → `Party.open`) `Party.java:64-68` 이 null 을 거부하고 `PartyEntity.java:55-62` 가 `nullable=false` → **정원 미달 상태에서도 항상 non-null**. 25 지도 경로의 전제가 성립한다. `taxiDriverId` 만 `DRIVER_ASSIGNED` 이후 채워짐(`Long?` 로 올바르게 선언) |
| A4 | `ReportRequest`/`CallResultRequest` shape | ✅ 통과 | `ReportRequest(reporterId, partyId, latitude, longitude)` / `ReportResponse(reportId)` / `CallResultRequest(called)` 모두 `ReportDtos.kt:9-26` 과 일치. 경로 `POST /api/v1/reports`, `PATCH /api/v1/reports/{reportId}/call-result` 도 일치 |
| A5 | `/api/v1` prefix 전수 감사 | ✅ 통과 | matching·dispatch·report·chat·place 전 API 경로가 컨트롤러 `@RequestMapping` 과 일치. baseUrl `http://10.0.2.2:8080/` + 상대경로 `api/v1/...` 정상 결합 |
| A6 | 목록 응답 shape | ✅ 통과 | `{"list":[{"partyId","departure","destination","currentMembers","capacity","status"}]}` — `PartyDtos.kt:14-21` 과 일치 |

### B. 실기 구동

| # | 항목 | 결과 | 스크린샷 |
|---|---|---|---|
| B1 | 21 매칭 대기 — ready/start 버튼 제거 | ✅ 통과 | `api2_06_matchwaiting.png` |
| B1a | 인원 현황 `지금 같은 방향 2명 · 목표 3명` | ✅ 통과 | 동 |
| B1b | 진행바가 인원 기반(2/3 ≈ 66%) | ✅ 통과 | 동 — 하드코딩 41% 아님을 육안 확인 |
| B1c | 자동 배차 안내 배너 | ✅ 통과 | 동 (`정원이 차면 기사님이 자동으로 배차돼요`) |
| B1d | 4초 폴링 | ✅ 통과 | logcat `GET /matching/rooms/1` 16:56:15.187 → 16:56:20.343 (약 4.7s = 4s delay + RTT) |
| B2 | 합류 플로우 20 → **21**(22 아님) | ✅ 통과 | `api2_05_joinconfirm.png` → `api2_06_matchwaiting.png` |
| B2a | 합류 확인의 실서버 요금(13,100원) | ✅ 통과 | `api2_05` — 서버 `estimateFare` 그대로 |
| B3 | 정원 도달 → **자동으로 25 전이** | ✅ 통과 | `api2_07_after_full.png` (member 3 curl 투입 후 4초 내 전이) |
| B3a | 25 경로 폴리라인 렌더 | ✅ 통과 | `api2_07`, `api2_14` — 파란 경로선 |
| B3b | 25 출발지 마커 | ✅ 통과 | 동 |
| B3c | 기사 미배정 시 플레이스홀더(에러로 안 덮임) | ✅ 통과 | `api2_07` — `기사님을 배정하고 있어요` / `차량 번호 확인 중` / `기사 정보 준비 중`. `/driver`·`/dispatch/rides` 가 **500** 을 반환하는데도 화면 정상 |
| B3d | 기사 배정 후 실데이터 표시 | ✅ 통과 | `api2_14_dispatch_assigned.png` — `88가 8888` / `카니발 · 6인승` / `기사 정보 준비 중` / 1인 부담 6,550원(13,100÷2) |
| B3e | **기사 위치 마커** | ❌ **실패** | `api2_15_driver_marker.png` — **D-1** |
| B3f | 화면 이탈 시 폴링 중단 | ✅ 통과 | 25 체류 중 요청 발생 → 이탈 후 12초간 요청 **0건** |
| B3g | 25에서 뒤로 → 21로 안 돌아감 | ✅ 통과 | `api2_08_back_from_25.png` — 17 탐색으로 복귀(`popUpTo(MATCH_WAITING){inclusive}` 동작) |
| B3h | 기사 위치 5초 폴링 | ✅ 통과 | logcat 17:13:15.716 → 20.739 → 25.759 |
| B4 | 27 신고 화면 진입·렌더 | ✅ 통과 | `api2_16_emergency.png` — 사유 4종 + `3초간 길게 눌러 신고` |
| B4a | 신고 접수 → `112에 전화하셨나요?` → `confirmCallResult` | ⬜ **미검증** | **D-2**(백엔드 IN_RIDE 도달 불가) + **D-3**(15초 타이머) — 아래 참고 |
| B4b | 신고 실패 경로(빨간 배너) | ⬜ **미검증** | 동상 |
| B5 | 에러 상태 — airplane 시 에러 UI | ✅ 통과 | `api2_11_airplane_explore.png` — `합승 목록을 불러오지 못했어요` + `다시 시도`, 탭바 유지, **크래시 없음** |
| B5a | 복구 후 재시도 동작 | ✅ 통과 | `api2_12_retry_recovered.png` — 200 수신 후 빈 상태로 정상 복귀 |
| B6 | 회귀 — 홈 naver-map | ⚠️ 부분 | `api2_03_home.png` — 레이아웃·시트 정상이나 **타일 미표시(D-4)** |
| B6a | 회귀 — 탐색 목록(실서버) | ✅ 통과 | `api2_04_explore.png` — `서울시청 → 강남역 1/3명` |
| B6b | 회귀 — 채팅 목록 | ✅ 통과 | `api2_09_chat.png` — 빈 상태 정상 |
| B7 | 크래시 | ✅ 통과 | 전 구간 `FATAL EXCEPTION`·`SerializationException` **0건** |

### C. 빌드

| 항목 | 결과 |
|---|---|
| `:app:assembleDebug` | ✅ BUILD SUCCESSFUL |
| `:data:testDebugUnitTest` | ✅ 41 tests / 0 failures (01 보고서 주장과 일치) |

---

## 2. 결함 목록

### D-1 · 기사 위치 좌표가 **위경도 뒤바뀜** — 25 기사 마커가 렌더되지 않음 🔴 High

**실측**: 기사 위치를 `latitude=37.5670, longitude=126.9785` 로 보고한 뒤 조회하면

```
GET /api/v1/dispatch/rides/6/42
{"longitude":37.56800086121358,"latitude":126.9799992442131}   ← 값이 서로 바뀌어 있다
```

**원인** (백엔드, 2단 전치가 상쇄되지 않고 누적):
- `backend/.../dispatch/infrastructure/DriverLocationRedis.java:69`
  `new DriverPosition(point.getX(), point.getY())` — Redis GEO 의 `getX()` 는 **경도**, `getY()` 는 **위도**인데 `DriverPosition` 은 `(latitude, longitude)` 순서다 → 1차 전치
- `backend/.../dispatch/application/RideService.java:64`
  `new DriverLocationResponse(position.longitude(), position.latitude())` → 2차 전치

**프론트 영향**: `DispatchMappers.kt` 는 이름 기준 매핑이라 정상이고 DTO 도 정확하다. 그러나 값 자체가 뒤바뀐 채 들어와
`DispatchStatusScreen.kt:139` 이 `LatLng(126.98, 37.57)` 을 만든다. **위도 126.98 은 유효 범위(-90~90) 밖**이라
네이버 마커가 그려지지 않는다.

**실측 증상**(`api2_15_driver_marker.png`): 앱이 5초마다 200 을 정상 수신하자 부제의 `· 기사님 위치를 받아오는 중` 힌트가 사라져
"위치를 받았다"고 표시되는데, **지도에는 기사 마커가 없다**. 사용자에게 아무 경고도 없는 **조용한 실패**라 더 위험하다.

**조치**
- 1차(백엔드, 근본): `RideService.java:64` 를 `new DriverLocationResponse(position.longitude(), position.latitude())` →
  `DriverPosition` 이 실제로 담고 있는 값 기준으로 바로잡거나, `DriverLocationRedis.java:69` 를
  `new DriverPosition(point.getY(), point.getX())` 로 수정 (둘 중 **하나만** 고칠 것 — 둘 다 고치면 다시 뒤바뀐다)
- 2차(프론트, 방어): `DispatchStatusScreen.kt:139` 에서 `LatLng` 생성 전 `latitude in -90.0..90.0 && longitude in -180.0..180.0` 검증 후
  범위 밖이면 마커를 그리지 않고 `기사님 위치를 받아오는 중` 을 유지 → 담당 **compose-builder**

### D-2 · 백엔드 `startRide` 미저장 — `IN_RIDE` 도달 불가 → 신고 API 사용 불가 🔴 High (백엔드)

`POST /api/v1/dispatch/rides/{partyId}/board/{driverId}` 가 **결정적으로 500**. 파티가 `DRIVER_ASSIGNED` 에서 못 벗어난다.

`backend/.../matching/application/PartyAccessService.java` 의 `startRide`(및 `completeRide`)에
`@Transactional` 이 없고 `parties.save(party)` 도 없다. `assignDriver`/`failMatching` 에는 있다.
`@Lock(PESSIMISTIC_WRITE)` 조회가 read-only 트랜잭션에서 실패하고, 설령 통과해도 detached 객체를 수정만 하고 저장하지 않는다.

**영향**: 서버가 신고를 **`IN_RIDE` 인 신고자에게만** 허용하므로(01 보고서 7절, 실측 500 확인)
`POST /api/v1/reports` 는 **현재 어떤 경로로도 성공할 수 없다**. 27 신고의 정상 경로 검증이 원천 차단됐다.

**조치**: 백엔드에 `@Transactional` + `parties.save(party)` 추가 요청. 수정 후 27 재검증 필요.

### D-3 · 26 운행 중의 15초 데모 타이머가 27 신고 진입을 방해 🟡 Medium (프론트)

`presentation/.../core/MainNavGraph.kt:373-377`
```kotlin
LaunchedEffect(Unit) {
    delay(15_000)
    navController.navigate(Routes.FARE_FINAL)
}
```
26 진입 15초 뒤 무조건 28 요금 확정으로 넘어간다. 그 뒤 「신고」 자리를 누르면 28 의 확인 버튼이 눌려 **정산 화면**으로 간다.
재현 3/3. 신고는 원래 급할 때 누르는 기능인데 15초 제한이 붙은 셈이라, 새로 연결한 27 플로우와 정면으로 충돌한다.

**주의**: 이 타이머는 이번 작업에서 추가된 게 아니라 `HEAD`(3d0c1c7)에 이미 있던 데모용 코드다.
다만 27 신고가 실제 API 에 연결된 지금은 **기능을 막는 장애물**이 됐다.

**조치**: 데모 타이머 제거하거나, 서버 상태(`FINISHED`) 관찰로 대체 → 담당 **compose-builder**

### D-4 · 네이버 지도 타일 미표시 (401 Unauthorized client) 🟡 Medium (환경)

전 지도 화면에서 `[NaverMapSdk] Authorization failed: [401] Unauthorized client` 토스트.
타일이 안 뜨고 회색 격자만 보인다(`api2_03_home.png`, `api2_07`, `api2_14`).

키 배선 자체는 정상이다 — `local.properties` 의 `NAVER_MAPS_CLIENT_ID` → `app/build.gradle.kts:38` `manifestPlaceholders` →
`AndroidManifest.xml:22` `com.naver.maps.map.NCP_KEY_ID`. **키 값이 이 패키지명으로 승인되지 않았거나 만료된 문제**다.

**영향**: 오버레이(경로선·마커)는 정상 렌더되므로 이번 작업의 `RouteMapView` 로직 검증에는 지장이 없었다.
다만 **지도 배경 위 시각 검증은 불가**했다.

**조치**: 네이버 클라우드 콘솔에서 `com.moyeota` 패키지명 등록 확인 → 담당 **리더/환경**

### D-5 · `coerceInputValues` 미설정 — 명시적 null 방어 부재 🟢 Low (프론트)

`data/.../remote/NetworkModule.kt:14`
```kotlin
private val json = Json { ignoreUnknownKeys = true }
```
kotlinx.serialization 은 **키 누락** 시에만 기본값을 쓴다. 서버가 `"type": null` 처럼 **명시적 null** 을 보내면
`String = ""` 같은 non-null 필드에서 `SerializationException` 이 난다.

**단, 현재는 실제 위험이 낮다**: `DriverSummaryResponse.type` 의 원천인 `RegisterVehicleRequest.type` 에 `@NotBlank` 가 걸려 있어
null 이 저장될 수 없고, 실응답도 `{"seats":4,"plateNumber":"77가 7777","type":"쏘나타"}` 로 정상이었다.
서버 계약이 느슨해질 때를 대비한 **예방 조치**로 제안한다.

**조치**: `Json { ignoreUnknownKeys = true; coerceInputValues = true }` → 담당 **api-integrator**

### D-6 · 백엔드 전역 예외 핸들러 부재 — 정상 상황도 500 🟢 Low (백엔드, 기확인 재확인)

matching/dispatch/report 에 `@RestControllerAdvice` 가 없다(`chat` 에만 존재). 실측:

| 호출 | 실제 |
|---|---|
| `GET /matching/rooms/1/driver` (기사 미배정) | **500** + `{"timestamp","status","error","path"}` |
| `GET /dispatch/rides/1/1` (위치 미보고) | **500** |
| `POST /reports` (IN_RIDE 아님) | **500** |
| `DELETE /matching/leave/1/1` (MATCHING 상태) | **500** |

400 이어야 할 것이 500 이고 한국어 사유가 본문에 없다. 앱이 원인별 분기를 못 한다.
현재 앱은 이들 실패를 삼키도록 만들어져 있어 **화면은 정상**이지만, 신고 실패 사유를 사용자에게 보여줄 수 없다.

**조치**: 백엔드 요청(01 보고서 9절 요청 3과 동일).

### D-7 · 22 탑승 상세 / 26 운행 중은 여전히 목 데이터 · 폴링 없음 🟢 Low (범위 외)

- 22 는 서버가 `DRIVER_ASSIGNED` 로 바뀐 뒤에도 `모집 중 · 2명 참여` 를 그대로 표시하고 재조회하지 않는다
- 26 은 `부산대 정문 → 서면역`, `보호자에게 실시간 공유 중` 등 전부 하드코딩
- 24 내 탑승도 목 데이터(`7월 25일`, `3,200원`)

이번 작업 범위가 아니지만, 25 가 실데이터로 바뀌면서 **화면 간 값이 서로 어긋나 보인다**(25는 6,550원, 26→28은 3,600원).

---

## 3. 해소된 미검증 플래그 (01·02 보고서 대상)

| 원 플래그 | 결과 |
|---|---|
| 🚩 실서버 미검증 (01-9절) | **해소** — 로컬 기동 후 전 엔드포인트 실측 |
| 🚩 `joinedAt` 숫자 직렬화 위험 (01-9절) | **해소** — ISO-8601 문자열 확정, 위험 없음 |
| 🚩 `join` 응답의 `route`/`estimateFare`/`taxiDriverId` (02-4절) | **해소** — route·요금은 항상 포함, taxiDriverId만 배정 후 |
| 🚩 `getDriverLocation` 미배정 시 상태코드 (02-4절) | **해소** — **500**. 앱이 삼키는 현재 동작이 적절 |
| `estimateFare` 가 경로 전체 요금인지 (01-9절) | **확인** — 13,100원 총액, 앱이 정원으로 나눠 표시(3인 4,366원 / 2인 6,550원). 해석 옳음 |
| `GET /matching/routes` 호출 불가 (01-4절) | **여전함** — `@GetMapping` + `@RequestBody` 유지. `routePolyline` 우회가 정상 동작하므로 실사용 지장 없음 |

---

## 4. 재현 절차 (결함 확인용)

### 백엔드 기동 + 기사 시드
```bash
cd ../backend && ./gradlew bootRun          # H2 인메모리 — 재시작 시 초기화
B=http://localhost:8080/api/v1
# 기사 등록 → 검증 → 차량 → 온라인  (반드시 방이 정원 차기 "전"에 온라인일 것)
D=$(curl -s -X POST $B/drivers -H 'Content-Type: application/json' \
  -d '{"userId":777,"qualificationNumber":"QA-777","bankName":"국민","accountNumber":"777"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['id'])")
curl -X POST $B/drivers/$D/verify
curl -X POST $B/drivers/$D/vehicle -H 'Content-Type: application/json' \
  -d '{"seats":6,"plateNumber":"88가 8888","type":"카니발"}'
curl -X POST $B/dispatch/online/$D -H 'Content-Type: application/json' \
  -d '{"latitude":37.5665,"longitude":126.9780}'
# 정원 2인 방 생성 (앱 사용자 1이 합류하면 정원 도달)
curl -X POST $B/matching/rooms -H 'Content-Type: application/json' \
  -d '{"creatorId":51,"departureLat":37.5665,"departureLng":126.9780,
       "destinationLat":37.4979,"destinationLng":127.0276,"departure":"서울시청",
       "destination":"강남역","capacity":2,"departureRadius":500,"destinationRadius":500}'
```
> 함정: 기사 heartbeat TTL **30초**. 정원 도달 직전에 `POST $B/dispatch/location/$D` 로 갱신할 것.
> `MatchingSweeper` 가 **3분** 넘은 MATCHING 방을 자동 취소한다.

### D-1 재현
1. 앱: 합승 탭 → 카드 「합류」 → 「이 탑승에 합류하기」 → 21 → (정원 도달) → 25 자동 전이
2. `curl -X POST $B/dispatch/calls/{partyId}/accept/$D` → `DRIVER_ASSIGNED`
3. `curl -X POST $B/dispatch/location/$D -d '{"latitude":37.5680,"longitude":126.9800}'` 를 3초 간격 반복
4. 25 부제에서 `· 기사님 위치를 받아오는 중` 이 사라지지만 **지도에 기사 마커가 없다**
5. `curl $B/dispatch/rides/{partyId}/1` → `{"longitude":37.568,"latitude":126.98}` (전치 확인)

### D-3 재현
25 화면 탭 → 26 운행 중 → **15초 이상 대기** → 「신고」 위치 탭 → 27이 아니라 **정산** 화면 (3/3 재현)

---

## 5. 담당별 조치 요약

| 담당 | 항목 |
|---|---|
| **백엔드(KII1ua)** | **D-1**(좌표 전치 — 최우선), **D-2**(startRide 미저장 — 신고 기능 전체 차단), D-6(전역 예외 핸들러), `GET /matching/routes` 를 `@RequestParam`/`POST` 로, `DriverSummary` 에 기사 이름·별점, `ReportRequest` 에 사유·상세 텍스트 |
| **compose-builder** | **D-1 2차 방어**(`DispatchStatusScreen.kt:139` 좌표 유효성 검사), **D-3**(`MainNavGraph.kt:373-377` 데모 타이머 제거) |
| **api-integrator** | D-5(`NetworkModule.kt:14` `coerceInputValues = true`) |
| **리더/환경** | D-4(네이버 지도 키 패키지명 승인) |

## 6. 재검증 필요 항목 (수정 후)

1. **B4a/B4b 신고 플로우** — D-2 수정 후에만 가능. 접수 → `112에 전화하셨나요?` → `confirmCallResult` 전 구간
2. **B3e 기사 마커** — D-1 수정 후 마커가 출발지 근처에 그려지는지
3. **B6 홈 지도** — D-4 해결 후 타일 렌더

## 7. 스크린샷

`_workspace/screenshots/` — `api2_01_launch` · `02_login` · `03_home` · `04_explore` · `05_joinconfirm` ·
`06_matchwaiting` · `07_after_full` · `08_back_from_25` · `09_chat` · `10_home` · `11_airplane_explore` ·
`12_retry_recovered` · `13_explore_reload` · **`14_dispatch_assigned`**(실기사 데이터) ·
**`15_driver_marker`**(D-1 증거) · `16_emergency` · `17_report_result`

---

# 재검증 (2026-08-30, 2차) — 수정 2건 집중

대상: 02 보고서 6절(D-3 데모 타이머 제거) · 7절(D-1 2차 방어 + `latLngOrNull` 공용 팩토리)
범위: 지정 3건만. 전체 회귀는 수행하지 않음.
에뮬레이터: **Pixel_6 콜드부트** (`emulator-5554`). 1차 기동은 `Pixel_6.avd/multiinstance.lock` 잔존으로 FATAL →
락 제거 후 **재시도 1회로 성공**(15.3초 부팅). 다른 세션은 별도 AVD `Pixel_6_QA`(`emulator-5558`, 기사 앱 전용)를
쓰고 있어 **이번엔 조작 방해 없었다**.
백엔드: 기동 중인 프로세스를 **그대로 사용**(재시작·종료하지 않음).

**집계: 통과 1 · 미검증 2 · 실패 0**

## R-0. 빌드

| 항목 | 결과 |
|---|---|
| `:app:assembleDebug` | ✅ BUILD SUCCESSFUL |
| `:data:testDebugUnitTest` | ✅ **41 tests / 0 failures / 0 errors** (XML 집계) |
| APK 신선도 | ✅ `app-debug.apk`(17:26)보다 새로운 `.kt` 소스 **0건** — 수정본이 실제로 설치됐음을 확인 |

## R-1. D-3 수정 — ✅ **통과 (3/3)**

26 운행 중 진입 경로: 마이 → 탑승 기록 → 24 내 탑승 → 「실시간 위치 보기」.

| # | 확인 | 결과 | 근거 |
|---|---|---|---|
| R-1a | 15초+ 대기해도 28로 자동 이동 없음 | ✅ 통과 | 진입 **17:40:29** → **17:41:12**(43초 경과) 시점에도 26 운행 중 유지. `reverify_08_ride_ongoing_t4s.png`(t=4s) / `reverify_09_ride_ongoing_t30s.png`(t=43s) 두 장이 픽셀 단위로 동일 화면 |
| R-1b | 27 신고 화면 진입 가능 | ✅ 통과 | 43초 체류 **후** 「신고」 탭 → `reverify_10_emergency.png` — 27 「괜찮으세요?」 + 사유 4종 + 「3초간 길게 눌러 신고」. **정산 화면 아님** (1차 QA 실패 지점 해소) |
| R-1c | 경유 순서 카드 **직접 탭** → 28 전이 | ✅ 통과 | 27에서 뒤로 → 26 → 3단 카드(「부산대 정문 · 탑승 완료 / 서면역 1번 출구로 이동 중 / 내린 뒤 현장에서 1/N 정산」) 중앙 탭(1080x2400 기준 **540,1230**) → `reverify_12_fare_final.png` — 28 「최종 요금 확인」 10,200원. 28~32 결제 경로 유지 |

정적 확인: `MainNavGraph.kt` 에 `delay(`·`15_000`·`LaunchedEffect` **잔존 0건**, `kotlinx.coroutines.delay`/`LaunchedEffect` import 도 제거됨.

> 참고: 카드가 클릭 가능하다는 시각 단서는 여전히 없다(02 보고서도 인정한 임시 트리거).
> 좌표를 모르는 사람은 못 찾는다 — 백엔드 D-2 해소 후 status 관찰로 교체할 때 함께 정리되어야 한다.

## R-2. D-1 2차 방어 — ⬜ **미검증 (실기)** / 정적 검증은 통과

**실기 미검증 사유: 25 배차 현황 화면에 도달할 수 없었다.** 결함이 아니라 환경 차단이다.

1차 QA 세션이 남긴 인메모리 상태에서 **`memberId=1`(앱의 고정 사용자, `FixedUserSession`)이 party 1 에 묶여 있고**,
party 1 은 `DRIVER_ASSIGNED` 다. 여기서 빠져나올 방법이 없다 — 실측:

| 시도 | 결과 |
|---|---|
| 앱에서 합류 (`POST /matching/rooms/7/1/join`) | **500** — `PartyApplicationService.java:126` `validateNotInOngoingParty` → `existsOngoingByMemberId(1)` |
| `DELETE /matching/leave/1/1` | **500** — `Party.java:100` `leave` 가 `ensureRecruiting()` 요구, party 1 은 DRIVER_ASSIGNED |
| `POST /matching/rooms` (creatorId=1) | **500** — 동일한 `validateNotInOngoingParty` |
| party 1 정리용 `arrive`→`board`→`complete` (driver 3) | arrive **204** / board **500** / complete **400** → **D-2 재확인**, 상태 그대로 |

즉 **D-2(백엔드 `startRide` 미저장)가 D-1 재검증까지 막는다.** 앱이 25 로 가는 경로는
① 합류 → 21 → 정원 도달, ② 22 상세의 「이 인원으로 출발」 둘뿐인데 ①은 위와 같이 차단되고,
②는 `RideDetailRoute.kt:119` `isHost = ride.hostId == currentUserId` 라 **방장에게만** 노출된다.
우회로 party 7 을 채워 22까지는 도달했으나(`reverify_17_ridedetail.png` — 서울시청→강남역 2/2, 13,100원, 멤버 999 실데이터)
user 1 이 방장이 아니라 「나가기 / 방장이 출발을 결정하면 시작돼요」만 보인다.

**백엔드를 재시작하면(H2 인메모리 초기화) 즉시 해소되지만, 「이미 떠 있으면 죽이지 말 것」 지침에 따라 하지 않았다.**

### 정적 검증 — 통과 (실측 데이터 기반)

- 전치 재확인: `POST /dispatch/location/4 {lat:37.5680, lng:126.9800}` → `GET /dispatch/rides/7/999` →
  `{"longitude":37.56800086121358,"latitude":126.9799992442131}` — **여전히 뒤바뀜**(백엔드 D-1 미수정)
- `latLngOrNull(126.98, 37.568)` → 위도 126.98 이 범위 밖 → **null 반환**이 자명하다.
  02 보고서가 근거로 든 SDK 상수를 **바이트코드로 직접 확인**했다
  (`geometry-1.3.0-runtime.jar` → `javap -constants com.naver.maps.geometry.LatLng`):
  `MINIMUM_LATITUDE=-90.0` · `MAXIMUM_LATITUDE=90.0` · `MINIMUM_LONGITUDE=-180.0` · `MAXIMUM_LONGITUDE=180.0` — **주장대로다**
- `DispatchStatusScreen.kt:102` 가 `driverPosition` 을 한 번만 계산하고, `:142` 마커와 `:171` 문구가 **같은 값**을 본다 →
  마커 미표시와 「위치를 받아오는 중」 유지가 구조적으로 **불일치할 수 없다**
- 크래시 위험: `latLngOrNull` 이 `LatLng` 생성 **전에** 거르므로 범위 밖 값이 SDK 로 들어가지 않는다

### 남은 실기 확인 (백엔드 재시작 또는 D-2 수정 후 최우선)

25 에서 **200 응답 + 전치 좌표**를 받은 상태에서 「… · 기사님 위치를 받아오는 중」이 **유지**되는지.
1차 QA 의 「문구가 지워진 무언 상태」와 구분되는 지점이 바로 이것이며, `driverLocation == null`(500 응답) 경로로는
**수정 전후가 동일하게 동작해 검증이 되지 않는다.**

## R-3. `RouteMapView` 공용 좌표 팩토리 회귀 — ⬜ **미검증 (실기)** / 정적 검증은 통과

R-2 와 **같은 이유**로 25 에 도달하지 못했다. `RouteMapView` 의 사용처는 코드베이스 전체에서
`DispatchStatusScreen.kt:139` **단 한 곳**이므로(grep 전수), 다른 화면으로의 파급은 없다 —
20 합류 확인·22 탑승 상세의 경로 그림은 `RouteMapView` 가 아니라 별도 목업(「지도 영역」)이다.

정적 확인:
- 출발지 `latLngOrNull(37.5665, 126.978)`, 도착지 `latLngOrNull(37.4979, 127.0276)` — 실서버 party 7 실값 기준 **모두 범위 내 → 마커 렌더**
- `route` 폴리라인 879자 정상 수신(`GET /matching/rooms/9` 원본 JSON은 백슬래시가 `\\` 로 **정상 이스케이프**되어 파싱 OK) →
  `decodePolyline` 결과 2점 이상 → `PathOverlay` 그려짐
- 컴파일 통과 = `latLngOrNull` 시그니처와 `RouteMapView` 파라미터 결선 일치

## R-4. 크래시 · 안정성 — ✅ 통과

세션 전 구간 logcat 11,300줄 기준:

| 항목 | 건수 |
|---|---|
| `FATAL EXCEPTION` / `SerializationException` / `has died` | **0** |
| `ANR in com.moyeota` | **0** |
| `com.moyeota` 프로세스 재시작 | **0** |

부수 확인:
- 합류 실패 시 에러 배너 「합류하지 못했어요. 잠시 후 다시 시도해 주세요」 정상 노출, 크래시 없음 (`reverify_05_after_join.png`) — 500 을 사용자 문구로 잘 흡수한다
- **D-4 여전함** — `[NaverMapSdk] Authorization failed: [401] Unauthorized client` 토스트, 타일 미표시 (`reverify_16_home_naver401.png`, `reverify_02_home.png`). 오버레이는 정상이라 로직 검증에는 지장 없음

## R-5. 스크린샷

`_workspace/screenshots/` — `reverify_01_launch` · `02_home` · `03_explore` · `04_joinconfirm` ·
`05_after_join`(합류 500 에러 배너) · `06_mypage` · `07_myrides` ·
**`08_ride_ongoing_t4s`** · **`09_ride_ongoing_t30s`**(D-3a 증거) · **`10_emergency`**(D-3b 증거) ·
`11_back_to_26` · **`12_fare_final`**(D-3c 증거) · `13_chat_stray` ·
`16_home_naver401`(D-4) · `17_ridedetail`(party 7 실데이터, 방장 아님)

## R-6. 담당별 후속 요청

| 담당 | 요청 |
|---|---|
| **백엔드(KII1ua)** | **D-2 최우선** — `PartyAccessService.startRide`/`completeRide` 에 `@Transactional` + `parties.save(party)`. 이게 막혀 있어 27 신고 정상 경로와 **D-1 재검증까지 동시에 차단**된다. 이어서 D-1(좌표 전치, `DriverLocationRedis.java:69` **또는** `RideService.java:64` 중 **하나만**) |
| **compose-builder** | 추가 수정 요청 **없음**. D-3 는 실기 통과, D-1 2차 방어는 정적 근거상 정상. 다만 R-1c 경유 카드의 클릭 단서 부재는 status 관찰로 교체할 때 정리 필요 |
| **리더/환경** | ① D-4 네이버 키 `com.moyeota` 패키지 승인 ② **재검증 전 백엔드 1회 재시작**(H2 초기화)으로 `memberId=1` 해방 — R-2/R-3 실기 검증의 전제 |

---

# 재검증 2회차 (2026-08-30, 최종) — 25 배차 현황 실기 확인

1회차에서 「25 도달 불가」로 남겼던 R-2 · R-3 을 **실기로 마감**했다.

## S-0. 백엔드 재시작 (코디네이터 승인)

재시작 전 **해당 프로세스가 우리 QA 로컬 dev 인스턴스인지 확인**하고 진행했다:

| 확인 항목 | 실측값 |
|---|---|
| 8080 LISTEN | `lsof -nP -iTCP:8080 -sTCP:LISTEN` → java **PID 36033** |
| 메인 클래스 | `team.codingforest.moyeota.MoyeotaApplication` |
| 클래스패스 루트 | `/Users/sungyoon/Desktop/moyeota/backend/build/classes/java/main` (**우리 레포**) |
| DB | `h2-2.4.240.jar` + `jdbc:h2:mem:testdb` (인메모리, prod 프로파일 아님) |
| 부모 프로세스 | PID 5993 = **Gradle 데몬** (`bootRun`) |
| 기동 시각 | **8/30 17:02:38** — 1차 QA 세션 중 (party 1 생성 08:07Z 직전) |

→ 로컬 dev 인스턴스 확정. `kill 36033` → `./gradlew bootRun` 재기동.
**H2 초기화 확인**: `GET /matching/rooms` → `{"list":[]}`, 새 방 생성 시 `id:1` 부터 재시작.
**`memberId=1` 해방 확인**: `POST /matching/rooms {creatorId:1}` → **200**(1회차엔 500이었음).

## S-1. 25 도달 — 1차 QA 와 동일 경로

1. 기사 시드: `POST /drivers`(id=1) → `verify` 204 → `vehicle`(`77가 7777`/카니발/6인승) 204 → `dispatch/online` 204
2. 정원 2인 방 생성 (creatorId=51) → **party 2**, `ACTIVE 1/2`, `estimateFare` 12,400원, `route` 879자
3. 앱: 합승 탭 → 「합류」 → 「이 탑승에 합류하기」 → 서버 `MATCHING 2/2 members[51,1]`
4. `POST /dispatch/calls/2/accept/1` 204 → `DRIVER_ASSIGNED`
5. 앱이 status 전이를 받아 **21 → 25 자동 전이** (`reverify2_06_dispatch25.png`)

**핵심 상태 재현 성공** — 앱 사용자(memberId=1) 기준:
```
GET /api/v1/dispatch/rides/2/1  →  HTTP 200
{"longitude":37.56800086121358,"latitude":126.9799992442131}   ← 위도 126.98 (범위 밖)
```
logcat 상 앱이 **5초 주기로 200 을 계속 수신**한다 (17:54:29 → 34 → 39 → 44, 각 14~30ms).
즉 「데이터를 못 받아서」가 아니라 **받고도 거르는** 상태다.

## S-2. D-1 2차 방어 실기 — ✅ **통과**

| # | 확인 | 결과 | 근거 |
|---|---|---|---|
| S-2a | 기사 마커 **미표시** | ✅ 통과 | `reverify2_06_dispatch25.png` · `07_map_zoomout.png` — 2km 축척까지 축소해도 지도 위 마커는 **출발지(파랑) 1개뿐**. 기사 마커 없음 |
| S-2b | 「… · 기사님 위치를 받아오는 중」 **유지** | ✅ 통과 | 동 — 부제가 `서울시청 앞에서 만나요 · 기사님 위치를 받아오는 중`. **200 을 받는 중인데도 유지**된다(1차 QA 의 「문구가 지워진 조용한 실패」와 정반대) |
| S-2c | 크래시 없음 | ✅ 통과 | 25 체류 + 폴링 전 구간 `FATAL EXCEPTION`·`SerializationException`·ANR **0건** |

### 양성 대조 실험 (suppression 이 범위 검사 때문임을 증명)

「마커가 원래 안 그려지는 것」과 「범위 검사가 걸러낸 것」을 구분하기 위해,
**같은 200 응답 경로에 좌표만 유효 범위로 바꿔** 넣었다.
백엔드가 2단 전치하므로 `{latitude:37.568, longitude:80.0}` 을 보고하면 응답이 뒤집혀 유효해진다:

| 보고값 | 앱이 받는 응답 | 부제 | 판정 |
|---|---|---|---|
| `lat 37.5680 / lng 126.9800` (실제 상황) | `{"longitude":37.568,"latitude":126.98}` — **범위 밖** | `… · 기사님 위치를 받아오는 중` **유지** | 마커 미표시 |
| `lat 37.5680 / lng 80.0` (대조군) | `{"longitude":37.568,"latitude":79.99999}` — **범위 내** | **`서울시청 앞에서 만나요`** (힌트 사라짐) | 마커 생성 |
| 다시 `lng 126.9800` 로 복귀 | 범위 밖 | `… · 받아오는 중` **복귀** | 마커 미표시 |

`reverify2_09_positive_control.png`(힌트 사라짐) ↔ `reverify2_10_back_to_transposed.png`(힌트 복귀).
**응답 shape·경로·코드는 완전히 동일하고 좌표 범위만 다르다** → 억제 주체가 `latLngOrNull` 의 범위 검사임이 확정된다.
동시에 마커와 부제가 **같은 판정(`driverPosition`)을 공유**함도 왕복으로 증명됐다.

> 참고: 대조군 좌표(80.0N, 37.57E)는 북극해라 화면 밖이다. 백엔드가 전치된 동안에는
> **서울 근방에 유효한 기사 좌표를 만들 수 없다**(Redis GEOADD 위도 한계 ±85.05). 마커 픽셀 확인은
> 백엔드 D-1 수정 후에 가능하며, 그때는 프론트 추가 수정 없이 그려져야 한다.

## S-3. `RouteMapView` 회귀 실기 — ✅ **통과**

공용 `latLngOrNull` 팩토리로 갈아탄 뒤에도 세 요소 모두 정상 렌더:

| # | 요소 | 결과 | 근거 |
|---|---|---|---|
| S-3a | **출발지 마커**(파랑) | ✅ 통과 | `reverify2_06_dispatch25.png` — 서울시청(37.5665, 126.978) 위치, 경로 시작점 |
| S-3b | **도착지 마커**(빨강) | ✅ 통과 | `reverify2_08_map_dest.png` — 지도를 남동쪽으로 팬하면 경로 끝에 빨간 마커(강남역 37.4979, 127.0276). 출발지와 **색상 구분** 정상 |
| S-3c | **경로 폴리라인** | ✅ 통과 | `06`·`07`·`08` — 879자 `route` 가 실제 도로를 따라 파란 선으로 그려짐. 축소·팬 후에도 유지 |
| S-3d | 지도 제스처 | ✅ 통과 | 줌아웃 3회·팬 2회 모두 반응하며, 지도가 제스처를 소비해 **`onStartRide`(화면 탭 → 26) 오발동 없음** |

부수: 시트의 실서버 값도 정상 — `77가 7777` / `카니발 · 6인승` / 서울시청 → 강남역 / 나 포함 2명 / 29분 / **1인 부담 6,200원**(12,400 ÷ 2).
기사 이름·별점 자리는 `기사 정보 준비 중` 플레이스홀더 유지(백엔드 `DriverSummary` 미제공 — 기존 요청 유지).

## S-4. 스크린샷

`_workspace/screenshots/` —
`reverify2_02_home` · `03_explore` · `04_joinconfirm` · `05_matchwaiting` ·
**`06_dispatch25`**(S-2a/b, S-3a 증거) · **`07_map_zoomout`**(마커 1개뿐) ·
**`08_map_dest`**(S-3b 도착지 마커) · **`09_positive_control`**(양성 대조 — 힌트 사라짐) ·
**`10_back_to_transposed`**(왕복 복귀 — 힌트 재등장)

## S-5. 최종 상태

| 결함 | 담당 | 상태 |
|---|---|---|
| D-1 2차 방어(프론트) | compose-builder | ✅ **실기 통과** — 추가 수정 없음 |
| D-3 데모 타이머 | compose-builder | ✅ **실기 통과**(1회차 R-1) |
| `RouteMapView` 회귀 | compose-builder | ✅ **실기 통과** |
| D-1 근본(좌표 전치) | 백엔드 | ⬜ 대기 — 수정 후 마커가 출발지 근처에 뜨는지만 확인하면 끝 |
| D-2 `startRide` 미저장 | 백엔드 | ⬜ 대기 — 27 신고 정상 경로(B4a/B4b) 검증이 여전히 차단됨 |
| D-4 네이버 지도 키 | 리더/환경 | ⬜ 대기 — 401 지속, 타일 미표시(오버레이는 정상) |
| D-5 `coerceInputValues` | api-integrator | ⬜ 대기 |
