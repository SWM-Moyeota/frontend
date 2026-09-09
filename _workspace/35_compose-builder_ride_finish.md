# 35 · 26 운행 중 → 28 최종 요금 — 서버 상태 폴링 전환 (compose-builder)

작업일 2026-09-04 · 브랜치 `feature/naver-map`

## 1. 무엇을 바꿨나

26 운행 중에서 28 최종 요금으로 넘어가는 신호를 **임시 수동 트리거(경유 카드 탭)** 에서
**서버 방 상세 status 폴링**으로 교체했다. D-2(서버 `startRide`/`completeRide` 미저장)가 해소돼
`IN_RIDE → FINISHED` 전이가 실제로 저장되므로, 승객 앱이 관찰할 수 있는 신호가 생겼다.

## 2. 변경 파일

| 파일 | 라인 | 내용 |
|------|------|------|
| `presentation/.../feature/chat/RideOngoingRoute.kt` | 신규 108줄 | `RideOngoingViewModel` + `RideOngoingRoute`. 4초 주기 `getPartyDetail` 폴링(L22 간격 상수, L48 `observeRideFinish`), `RideStatus.COMPLETED` 감지 시 `finished` 방출(L81 Route, L102 `LaunchedEffect`) |
| `presentation/.../feature/chat/RideOngoingScreen.kt` | L60–72 KDoc, L75 시그니처, L146–155 카드 | `onArrived` 파라미터 삭제, 경유 카드 `clickable` 제거, 「임시 트리거」 KDoc 정리 |
| `presentation/.../core/MainNavGraph.kt` | L36 import, L425–443 | `RideOngoingScreen` → `RideOngoingRoute(repository, partyId = activePartyId, …)`, `onRideFinished` 에 `popUpTo(RIDE_ONGOING){inclusive}` 붙여 28 로 전이 |

`data/`·`domain/` 무수정. `Routes.kt` 변경 없음(26·28 라우트 상수 그대로).

## 3. 설계 선택 3가지

**(a) 폴링 스코프는 `viewModelScope` 가 아니라 Route 의 `LaunchedEffect`**
루프 모양은 21 대기 화면 `MatchWaitingViewModel.observeAutoMatching` 과 동형(4초 `delay`,
`runCatching` 으로 실패 삼키고 다음 주기)이지만, 스코프는 25 배차 현황의 `pollDriver()` 방식을 썼다.
`viewModelScope` 로 두면 26 위에 24 채팅·27 신고를 쌓았을 때 VM 이 살아 폴링이 계속 돈다.
요구사항 4(화면 이탈 시 취소)를 문자 그대로 만족시키려면 컴포지션 수명에 묶어야 한다.

**(b) 26 은 Loading/Error 3상태를 두지 않는다**
운행 화면은 서버 값을 그리지 않고 status 하나만 관찰한다. 무엇보다 26 은 27 긴급 신고의 진입점이라
폴링 실패가 화면을 에러로 덮으면 안 된다(QA D-3 과 같은 종류의 사고). 실패는 로그만 남기고 다음 주기를 기다린다.

**(c) 28 로 넘어갈 때 26 을 스택에서 지운다**
남겨두면 28 에서 뒤로 왔을 때 status 가 여전히 FINISHED 라 폴링이 즉시 다시 28 로 튕겨내는 루프가 된다
(21→25 에 이미 같은 이유의 `popUpTo` 가 있다).
**부수효과:** 28 에서 뒤로가기가 앱 종료가 된다. 단 이는 새 결함이 아니라 위치 이동이다 —
25→26 이 `resetTo`(스택 초기화)라서 **변경 전에도 26 에서 뒤로가면 앱이 종료**됐다. 「배차 이후 되돌리기 차단」
공통 규칙의 경계가 26 에서 28 로 한 칸 옮겨졌을 뿐이다. 28 에 뒤로 갈 자리를 주려면 25→26 의 `resetTo`
자체를 손봐야 해서 이번 범위 밖으로 뒀다(리더 판단 필요).

## 4. 최종 요금: 실값 아님 — 데모값 유지

요구사항 3 확인 결과 **서버가 기사 입력 요금을 버린다.**

- `Party.completeRide(Long driverId, int fare)` (backend `matching/domain/Party.java:185`) — `fare` 파라미터를
  받기만 하고 `// TODO 결제쪽이 완료된 후 완성` 주석과 함께 status 만 FINISHED 로 바꾼다. 저장 필드가 없다.
- `PartyDetailResult` / `PartyDetailResponse` 에도 최종 요금 필드가 없다. 실측 응답(party 3, complete fare=13800 직전):
  `estimateFare: 12000` — 방 생성 시 경로 API 로 산출한 **예상** 요금뿐.

따라서 `FareFinalScreen` 은 기존 데모값(`finalFare=10,200` / `expectedFare=9,600`)을 그대로 유지했다.
`estimateFare`(12,000) 를 expectedFare 로만 넘기는 안도 검토했으나, 최종 요금이 데모값인 상태에서 예상값만
실값이면 화면의 「차이」가 실제와 무관한 숫자가 된다 — 반쯤 진짜인 금액이 정산 화면으로 이어지는 쪽이 더 위험하다.
**백엔드 요청:** `completeRide` 의 fare 를 Party 에 저장하고 `PartyDetailResult` 에 노출해 달라(요청 #6).

## 5. partyId 전달 경로 (요구사항 5)

`MainNavGraph.activePartyId` 한 값이 25·26·27 을 모두 먹인다.
16 방 생성 `onPartyCreated`(L325) 또는 20 합류(L356)에서 세팅 → 21 `onMatchingStarted`(L379)에서 재확인 →
25 `DispatchStatusRoute(partyId = activePartyId)`(L408) → 26 `RideOngoingRoute(partyId = activePartyId)`(L432).
25→26 은 `resetTo` 라 화면만 갈아끼우고 `activePartyId` 는 NavHost 바깥 상태라 유지된다.
채팅·34 내 기록에서 26 으로 직행하면 partyId 가 null 일 수 있어, 그때는 폴링 없이 화면만 띄운다(25 와 같은 폴백).

## 6. 실기 검증 (emulator-5554, 서버 localhost:8080)

시드: `qadriver9` 신규 등록 → `POST /drivers`(id=2) → `/drivers/verify` → `/dispatch/online`.
**주의(다음 사람 위해):** `drivers:heartbeat:*` TTL 이 **30초**라 `/dispatch/online` 한 번만으로는
30초 뒤 `findNearby` 에서 사라진다. `/dispatch/location` 을 10초 간격으로 계속 쏴야 콜이 잡힌다.

시나리오: 승객 `testuser1` 로그인 → 부산대 정문 → 서면역, **인원 1인**(정원 1이면 방 생성 즉시 MATCHING) →
party 3 생성 → 기사 accept/arrive/board(curl) → 25 탭 → 26 진입.

| 확인 항목 | 결과 | 근거 |
|---|---|---|
| 26 진입 시 폴링 시작 | PASS | `D RideOngoing: 운행 완료 폴링 시작 partyId=3` · `ridefin_10_ongoing.png` |
| 경유 카드 탭으로 전이 안 됨 | PASS | 3회 연타 후에도 26 유지 · `ridefin_11_cardtap_noop.png` |
| 화면 이탈 시 폴링 취소 | PASS | 27 신고 진입 시 `운행 완료 폴링 중단 partyId=3` · `ridefin_12_report_pollstop.png` |
| 복귀 시 폴링 재개 | PASS | 뒤로 → `운행 완료 폴링 시작 partyId=3` · `ridefin_13_back_pollresume.png` |
| **기사 complete → 4초 내 28 자동 전이** | PASS | complete(fare=13800) 호출 시각 = 감지 로그 시각(±1초, 기기 시계가 호스트보다 17초 느림 보정 후). `운행 완료 감지 partyId=3 → 28 최종 요금` · `ridefin_14_farefinal_auto.png` |
| 전이 후 폴링 종료 | PASS | 감지 직후 `폴링 중단` 1회, 이후 로그 없음 |
| 28 뒤로 → 26 재전이 루프 없음 | PASS | 26 이 스택에 없어 루프 없음(앱 종료 — §3 참고) · `ridefin_15_back_from_28.png` |

빌드: `./gradlew :presentation:compileDebugKotlin :app:assembleDebug --console=plain` → **BUILD SUCCESSFUL**

정리: 기사 `DELETE /dispatch/online`, 하트비트 중단, 앱 로그아웃해 로그인 화면으로 복귀(`ridefin_18_login_cleanup.png`).
서버는 살려뒀다. 스크린샷 19장 `_workspace/screenshots/ridefin_*.png`.

## 7. 남는 것

1. **백엔드**: `complete` 의 fare 저장 + 방 상세 노출 (§4). 그전까지 28~32 금액은 전부 데모값이다.
2. **네비게이션**: 28 뒤로가기가 앱 종료 (§3c). 25→26 `resetTo` 를 포함해 한 번에 봐야 한다.
3. `"CANCELED"` 도 `RideStatus.COMPLETED` 로 매핑된다(`PartyMappers`). 운행 중(IN_RIDE)에는 취소가 발생할 수
   없어 지금은 안전하지만, 취소 경로가 생기면 26 이 취소를 완료로 오독한다 — domain 에 별도 상태가 필요해진다.
