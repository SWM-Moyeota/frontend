# 37 · D-9 — 25 배차 현황이 IN_RIDE 를 잡지 못해 26 으로 자동 전이하지 않는 결함 (compose-builder)

작업일 2026-09-05 · 브랜치 `feature/naver-map` · 검증 emulator-5554 / 서버 localhost:8080

## 1. 확정 원인 (계측 증거)

**한 줄:** 25 의 폴링 루프가 **기사 배정 직후 방 상세 조회를 스스로 멈춰**(`if (_driver.value == null)` 게이트)
status 가 `DISPATCHING` 에 얼어붙었고, 애초에 `ONGOING` 을 소비해 `onStartRide` 를 부르는 코드도 **없었다**.

### (a) 코드 상 두 개의 결손

| # | 위치(수정 전) | 내용 |
|---|---|---|
| 원인 1 | `DispatchStatusRoute.kt:80` `pollDriver()` — `if (_driver.value == null) { …getPartyDetail… }` | 방 상세 재조회가 **기사 미배정일 때만** 돈다. 기사가 배정되는 순간(=board 직전) 상세 조회가 영구 정지 → 이후 `IN_RIDE` 를 볼 수 없다 |
| 원인 2 | `DispatchStatusRoute.kt` 전역 | `RideStatus.ONGOING` 을 읽는 곳이 **어디에도 없다**. `onStartRide` 를 부르는 유일한 지점은 `DispatchStatusScreen.kt:116` 의 화면 전체 `.clickable` (수동 탭)뿐 |

지시문이 지목한 `DispatchStatusRoute.kt:102` 의 「IN_RIDE → ONGOING 매핑」 주석은 **오독을 부르는 주석**이었다.
그 주석이 붙은 `DRIVER_ASSIGNED_STATUSES` 는 *기사 정보 조회를 해도 되는 status 집합*일 뿐,
전이와는 무관하다. 매핑 자체(`PartyMappers.kt:24` `"IN_RIDE" -> RideStatus.ONGOING`)는 정상이었다.

### (b) 실기 계측 (수정 전 빌드에 임시 로그를 넣어 재현)

`pollDriver()` 에 주기별 `[계측]` 로그를 심고 party 3 으로 재현. board 는 host 00:44:27 에 호출(204).

```
00:43:53.830 [계측] 상세 조회함 status=DISPATCHING      ← 기사 배정을 잡은 마지막 조회
00:43:53.846 [계측] 주기 종료 driver=true uiStatus=DISPATCHING
00:43:58.847 [계측] 상세 조회 건너뜀(기사 배정됨)        ← 이후 조회 영구 중단
        …  (5초 주기로 "건너뜀" 만 반복) …
00:44:29.025 [계측] 주기 종료 driver=true uiStatus=DISPATCHING   ← board +2초
00:44:49.148 [계측] 주기 종료 driver=true uiStatus=DISPATCHING   ← board +22초, 여전히 25
```

**결정적 증거 두 줄:** 폴링 루프 자체는 살아 돌고 있다(주기 로그 계속). 그런데 `getPartyDetail` 이
호출되지 않아 `uiStatus` 가 board 후 22초가 지나도 `DISPATCHING` 이다. 화면도 25 유지
(`screenshots/d9_05_repro_no_transition.png`). → "폴링이 안 돈다"가 아니라 **"폴링이 상세를 안 읽는다"** 가 원인.

## 2. 수정

`presentation/.../feature/matching/DispatchStatusRoute.kt` 한 파일. `data/`·`domain/`·NavGraph 무수정
(`MainNavGraph.kt:409` `onStartRide = { resetTo(Routes.RIDE_ONGOING) }` 배선은 이미 옳았다).

| 라인 | 변경 |
|---|---|
| L62–65 | `_rideStarted` / `rideStarted: StateFlow<Boolean>` 추가. 26 의 `finished` 와 같은 모양의 1회성 신호 — `UiState` 에 섞지 않아 폴링 실패가 전이를 막지 못한다 |
| L96–114 | `pollDriver()`: **매 주기 무조건** `getPartyDetail` 호출(게이트 삭제). `MATCHING → DRIVER_ASSIGNED → IN_RIDE` 를 끝까지 따라간다 |
| L105 | `if (markStartedIfInRide(latest)) return` — IN_RIDE 를 잡으면 폴링 종료 |
| L118–123 | `loadDriverIfAssigned` 진입부에 `if (_driver.value != null) return` — 상세 게이트가 겸하던 "기사 정보 1회만 조회" 역할을 여기로 옮겼다. 삭제한 게 아니라 **제자리로 옮긴** 것이다 |
| L129–134 | `markStartedIfInRide()` — status 가 `ONGOING` 이면 `_rideStarted` 세팅 + 로그 |
| L77 | `refresh()` 도 같은 판정을 태운다. 화면 진입 시점에 이미 IN_RIDE 면(재진입·복귀) 첫 조회에서 바로 전이 |
| L182, L189–191 | Route: `rideStarted` 구독 + `LaunchedEffect(rideStarted) { if (rideStarted) onStartRide() }` — 26→28 의 `onRideFinished` 와 동형 |
| L29–35 | `DRIVER_POLL_INTERVAL_MS` KDoc — 이 값이 이제 **전이 지연의 상한**이라는 사실 명시 |

로그 태그 `DispatchStatus` 를 상설화했다(`배차 폴링 시작/중단`, `탑승 시작 감지`). 26 의 `RideOngoing` 태그와
같은 어휘라, 두 화면의 폴링을 `logcat -s DispatchStatus:D RideOngoing:D` 한 줄로 이어서 볼 수 있다.

### 설계 판단 2가지

**(a) 폴링 간격 5초를 유지했다.** 요구된 4~8초 창을 최악의 경우에도 만족하고(실측 3.5s / 5.2s),
26 완료 폴링(4초)과 같은 자릿수다. 더 촘촘히 하면 지도 마커 요청까지 같이 늘어난다.

**(b) 화면 전체 탭(수동 트리거)을 제거했다** — 리더 판단 확정에 따른 후속 반영(§2-1).

## 2-1. 후속 — 수동 탭 트리거 제거 (리더 판단 확정)

폴링이 실동작하게 됐으므로 25 의 임시 수동 트리거를 걷어냈다. 26 의 「경유 카드 탭」을 제거한 것과
같은 이유·같은 방식이다(35 보고서 §2). 이제 **25→26 의 발화점은 폴링 하나뿐**이다.

| 파일 | 라인 | 변경 |
|---|---|---|
| `DispatchStatusScreen.kt` | L5 | `androidx.compose.foundation.clickable` import 삭제(마지막 사용처였다) |
| `DispatchStatusScreen.kt` | L84–88 | KDoc — 「화면 탭 → 26」 항목을 「전이는 이 화면이 하지 않는다」로 교체, 제거 이유(오탭 사고·D-9) 명시 |
| `DispatchStatusScreen.kt` | L95–98 | 시그니처에서 `onStartRide` 파라미터 삭제 — 본문에 사용처가 없어진 죽은 파라미터 |
| `DispatchStatusScreen.kt` | L110–114 | 루트 `Column` 의 `.clickable { onStartRide() }` 삭제 |
| `DispatchStatusRoute.kt` | L154–157 | Route KDoc — 「화면 탭도 같은 콜백을 부른다」 문장 삭제, 폴링이 유일한 발화점임을 명시 |
| `DispatchStatusRoute.kt` | L168, L201–206 | `DispatchStatusScreen(...)` 두 호출부에서 `onStartRide` 인자 삭제 |

Route 의 `onStartRide` 파라미터(L163)와 `MainNavGraph.kt:409` 배선은 **그대로 둔다** — 폴링이 부르는
전이 경로 자체이기 때문이다. 사라진 건 화면 쪽 수동 발화점뿐이다.

**부수효과:** `partyId == null` 폴백 화면(채팅·34 내 기록 직행)에서는 26 으로 갈 방법이 아예 없어졌다.
원래도 그 경로엔 서버 신호가 없어 탭이 유일한 수단이었는데, 그 탭이 **탑승 여부와 무관하게** 26 을
띄우던 것이라 없어지는 편이 옳다. 정상 경로(21→25)는 폴링으로 덮인다.

**빌드:** `./gradlew :presentation:compileDebugKotlin :app:assembleDebug --console=plain` → **BUILD SUCCESSFUL**
(경고 0). 전이 경로는 §3 에서 2회 실측 검증했고 이번 변경은 발화점 삭제뿐이라 실기 재검증은 생략(리더 지시).

## 3. 실기 결과 (emulator-5554, 서버 localhost:8080)

**전용 계정 신규 가입:** `d9user1`(memberId 4) / `d9user2`(5) / 기사 `d9drv`(userId 6, driverId 2), `Passw0rd!`.
기존 `qadriver1` 은 콜 경합 우려로 쓰지 않았다. 기사는 `POST /drivers` → `/drivers/verify` → `/dispatch/online`
후 **8초 간격 위치 보고 루프**를 걸었다(`drivers:heartbeat:*` TTL 30초 — 35 보고서 주의사항 그대로).

시나리오: 앱 로그인(d9user1) → 부산대 정문 → 서면역 1번출구, **2인** 방 생성 → curl 합류(d9user2) →
status MATCHING → 기사 accept → 앱 자동 21→25 → arrive → **board** → 전이 계측 → complete → 28 까지.

| 회차 | party | board(host) | 감지(device) | **전이 지연** | 26 도달 | complete → 28 |
|---|---|---|---|---|---|---|
| 1 | 4 | 00:48:06 | 00:48:09.518 | **약 3.5초** | PASS `d9_07` | 00:48:30 → 00:48:33.801 (**3.8초**) PASS `d9_08` |
| 2 | 5 | 00:49:52 | 00:49:57.801 | **약 5.2초** | PASS `d9_09` | 00:50:19 → 00:50:22.036 (**3.0초**) PASS `d9_10` |

기기·호스트 시계 편차는 측정 시점 0~1초(`date` 대조). 두 회차 모두 요구 창 **4~8초 이내**.

로그 (2회차):
```
00:49:37.607 D DispatchStatus: 배차 폴링 시작 partyId=5
00:49:57.801 D DispatchStatus: 탑승 시작 감지 partyId=5 → 26 운행 중
00:49:57.801 D DispatchStatus: 배차 폴링 중단 partyId=5
00:49:57.872 D RideOngoing:    운행 완료 폴링 시작 partyId=5
00:50:22.036 D RideOngoing:    운행 완료 감지 partyId=5 → 28 최종 요금
```
25 의 폴링이 멈춘 71ms 뒤 26 의 폴링이 시작된다 — 두 화면이 신호를 인계하는 지점이 로그로 이어진다.

**측정에 쓴 APK 는 §2 까지 반영된 빌드**다(수동 탭이 아직 있던 상태). 전이는 전부 폴링 로그
`탑승 시작 감지` 로 발화됐고 측정 중 화면 탭은 하지 않았으므로, §2-1 의 탭 제거는 이 수치에 영향이 없다.

**진입 경로:** 홈 → 목적지 검색/최근 목적지 → 16 도착지 확인 →「같이 탈 사람 찾기」→ 21 매칭 대기
→ (정원 충족·기사 accept) 25 배차 현황 → **(기사 board) 26 운행 중** → (기사 complete) 28 최종 요금.

**정리:** 기사 `DELETE /dispatch/online` + 위치 보고 루프 중단. party 3·4·5 는 모두 FINISHED 로 닫았다.
서버는 살려뒀다. 스크린샷 10장 `_workspace/screenshots/d9_01~10_*.png`
(`d9_05` = 수정 전 재현, `d9_07`/`d9_09` = 수정 후 자동 전이).

## 4. 남는 것

1. **`CANCELED` → `COMPLETED` 매핑**(`PartyMappers.kt:25`)은 그대로다. 배차 중 취소가 생기면
   25 는 그걸 전이 신호로 읽지 않고(ONGOING 아님) 계속 폴링만 한다 — 취소 경로가 생기면 별도 처리가 필요하다.
2. 백엔드 요청 #6(완료 요금 저장·노출)은 미해결이라 28 금액은 여전히 데모값이다(35 보고서 §4).
