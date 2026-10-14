# 82 · 26 운행 중 / 25 배차 현황 — 방 상세 폴링 → 가벼운 상태 조회

백엔드 #177 의 `GET /matching/rooms/{id}/status`(쿼리 1개)로 두 화면의 주기 조회를 갈아끼웠다.
방 상세(쿼리 3개 · route 포함)는 **실제로 뭔가 바뀐 주기에만** 읽는다 — 부하테스트에서 방 상세
폴링이 전체 요청의 45% 였고 그 대부분이 운행/채팅의 4초 폴링이었다.

## 변경 파일

| 파일 | 변경 |
|---|---|
| `presentation/src/main/kotlin/com/moyeota/presentation/feature/chat/RideOngoingRoute.kt` | 4초 폴링을 `getPartyStatus` 로 교체. 지도용 상세는 첫 성공 1회만. `isRideFinished` / `shouldLoadRideDetail` 추가 |
| `presentation/src/main/kotlin/com/moyeota/presentation/feature/matching/DispatchStatusRoute.kt` | 5초 폴링을 `getPartyStatus` 로 교체. `shouldReloadDetail` 추가, `markStartedIfInRide(Ride?)` → `(RideStatus?)` |
| `presentation/src/test/kotlin/com/moyeota/presentation/feature/chat/RideFinishSignalTest.kt` | 신규 5케이스 |
| `presentation/src/test/kotlin/com/moyeota/presentation/feature/matching/DetailReloadGateTest.kt` | 신규 7케이스 |

손대지 않은 것: `shouldFetchDriverInfo` / `shouldFetchDriverLocation` 와 `DriverPollGateTest`,
`RideOngoingScreen.kt`, `DispatchStatusScreen` 계열, 네비게이션(Routes/MainNavGraph), 주기 상수(4초·5초).
커밋하지 않았다.

## 1) 26 운행 중 (`RideOngoingRoute.kt`)

**루프 (4초)**: `getPartyStatus` 1회. `isRideFinished(status.status)` 면 `_finished = true` → Route 의
`onRideFinished` → 28 최종 요금(기존과 동일한 전이).

**CANCELED 는 종료로 다루지 않았다 (기존 의미 유지).**
- 기존 코드의 판정은 `status == COMPLETED` 하나였고, 주석의 근거는 "CANCELED 는 모집 중에만 발생해
  운행 중에는 올 수 없다" 였다. 그 판정을 그대로 옮겼다.
- 기존 주석의 괄호 설명(`PartyMappers: "FINISHED", "CANCELED" → COMPLETED`)은 **이미 낡은 내용**이었다.
  현재 `PartyMappers.kt:31` 은 `"CANCELED" → RideStatus.CANCELED` 로 갈라 매핑한다(25b 가
  「기사님을 찾지 못했어요」를 띄우기 위해). 즉 지금 코드에서도 CANCELED 는 종료 신호가 아니었고,
  주석만 사실과 달랐다 — 주석을 고쳤다.
- 의미상으로도 CANCELED → 28 은 틀리다: 타지 않은 운행의 최종 요금 화면이 열린다. 취소는 탑승 전
  상황이고 25 가 전담한다. 이 판단을 `isRideFinished` KDoc + 테스트로 못박았다.

**지도용 상세**: `shouldLoadRideDetail(held) = held == null` — 아직 못 받았을 때만 읽는다.
상세에서 쓰는 값(출발·도착 좌표, `routePolyline`)은 방 생성 시 확정돼 운행 중 바뀌지 않으므로 1회면 충분.
`held != null` 로만 끊은 이유는 **첫 조회가 실패할 수 있어서**다 — 예전엔 매 주기 상세를 읽어 저절로
복구됐는데, 1회 고정으로 바꾸면 블립 한 번에 운행이 끝날 때까지 마커·경로 없는 빈 지도가 남는다.
성공하면 그 뒤로는 상태 조회만 돈다.

**요청 수**: 첫 주기 2회(상세+상태) → 이후 매 주기 1회(상태, 쿼리 1개). 이전엔 매 주기 상세(쿼리 3개).

## 2) 25 배차 현황 (`DispatchStatusRoute.kt`)

**루프 (5초)**:
1. `getPartyStatus` — 매 주기.
2. `shouldReloadDetail(held, status)` 가 참일 때만 `getPartyDetail` → `UiState.Success` 갱신.
   상세만 실패하면 들고 있던 값을 유지한다(화면 깜빡임 없음).
3. `loadDriverIfAssigned(ride)` — 게이트는 그대로 `Ride.driverId`(규칙 유지, status 로 가르지 않는다).
4. `markStartedIfInRide(status?.status)` — `ONGOING`(서버 `IN_RIDE`)이면 1회성 신호 + 폴링 종료.
   상세를 기다리지 않는다(전이에 필요한 값은 status 하나). 상세 갱신이 **먼저** 돌므로 26 으로 넘어갈 때
   `UiState` 에는 이미 ONGOING 상세가 들어 있다(기존 순서 유지).
5. `shouldFetchDriverLocation(ride)` 면 `dispatchRepository.getDriverLocation` — **매 주기 그대로**.
   방 상세가 아닌 별도 API 이고, 택시가 오는 동안 마커가 움직여야 한다.

**기사 배정 감지**: 서버가 배정 시 status `MATCHING` → `DRIVER_ASSIGNED` + taxiDriverId 를 채우므로
지문이 달라진다 → 그 주기에 상세를 읽어 `driverId` 가 들어온다(D-9 재발 없음). 판정 기준은 여전히
`Ride.driverId` 다.

**`shouldReloadDetail` 의 추가 조건 — 판단한 부분이라 따로 적는다.**
```kotlin
internal fun shouldReloadDetail(held: Ride?, status: PartyStatus): Boolean {
    if (shouldFetchPartyDetail(status, held)) return true          // 21 과 공유하는 기본 규칙
    return status.fingerprint == null &&                            // 지문 없는 서버·더미 폴백
        status.status == RideStatus.DISPATCHING &&
        held?.driverId == null
}
```
지시대로라면 `isSameAs` 하나로 끊으면 되지만, **지문이 null 인 서버·더미에서는 그게 배정을 못 본다**:
`isSameAs` 가 상태·인원수로 폴백하는데 서버 `MATCHING` 과 `DRIVER_ASSIGNED` 는 앱에서 둘 다
`RideStatus.DISPATCHING` 이고 인원도 같아서 "같다"가 되어 상세를 영영 안 읽는다 → 25b 레이더에 갇힌다
(`DummyRideRepository` + 인터페이스 기본 구현이 바로 이 경우다: 지문 없음).
그래서 **지문이 없고 아직 미배정인 동안만** 예전처럼 매 주기 상세를 읽는다. 지문이 오는 운영 환경에서는
첫 조건에서 끝나므로 지시한 동작 그대로다. 배정된 뒤 남은 전이(`DRIVER_ASSIGNED` → `IN_RIDE`)는
상태 응답만으로 보이므로 예외가 필요 없다.

**중복 주의 — 리더 판단 필요**: 같은 패키지의 `MatchWaitingRoute.kt:200` 에 다른 에이전트가 만든
`shouldFetchPartyDetail(status, held)`(= 기본 규칙)이 있다. 같은 판정을 두 이름으로 두지 않으려고
`shouldReloadDetail` 이 **그 함수를 호출**하도록 했다(`shouldFetchDriverInfo` 가
`shouldFetchDriverLocation` 을 재사용하는 것과 같은 이유). 두 파일이 서로 컴파일 의존하니, 21 쪽에서
그 함수를 옮기거나 이름을 바꾸면 25 도 같이 고쳐야 한다 — 공용 규칙이라면 `presentation/core` 나
도메인(`PartyStatus`)으로 올리는 게 깔끔하다.

## 테스트 결과

```
./gradlew :presentation:compileDebugKotlin :presentation:testDebugUnitTest --console=plain -q
→ BUILD SUCCESSFUL (경고·에러 없음)
```
| 테스트 | 결과 |
|---|---|
| `feature.chat.RideFinishSignalTest` | 5 / 실패 0 (COMPLETED 만 종료, CANCELED·ONGOING·null 아님, 상세 1회) |
| `feature.matching.DetailReloadGateTest` | 7 / 실패 0 (지문 동일→생략, 배정·탑승으로 지문 변경→조회, 지문 null 폴백 2케이스) |
| `feature.matching.DriverPollGateTest` (기존, 무수정) | 5 / 실패 0 |
| `feature.matching.WaitingPollTest`, `WaitingModeTest`, `PickupEtaTest` 등 나머지 | 전부 실패 0 (모듈 전체 77케이스) |

## 상세 조회(`getPartyDetail`)가 남아 있는 지점

내 담당 파일:
| 위치 | 성격 | 비고 |
|---|---|---|
| `DispatchStatusRoute.kt:81` (`refresh()`) | **진입 시 1회 + 에러 재시도** | 화면을 그릴 첫 상세. 유지 필요 |
| `DispatchStatusRoute.kt:120` (`pollDriver`) | **조건부** — `shouldReloadDetail` 참일 때만 | 지문이 바뀐 주기에만 |
| `RideOngoingRoute.kt:87` (`observeRideFinish`) | **첫 성공 1회** — `shouldLoadRideDetail` | 지도 좌표·경로용, 성공 후 재조회 없음 |

담당 외(참고용, 이번에 손대지 않음):
| 위치 | 성격 |
|---|---|
| `MatchWaitingRoute.kt:81 / 109 / 142` | 21 대기 — 다른 에이전트가 같은 작업 중(81 산출물) |
| `RideDetailRoute.kt:50` | 20 방 상세 — 화면 진입 시 1회(폴링 아님) |
| `JoinConfirmRoute.kt:59` | 합류 확인 — 진입 시 1회(폴링 아님) |

주기 폴링으로 상세를 읽는 곳은 이제 `shouldReloadDetail`/`shouldLoadRideDetail` 게이트를 지난 조건부
호출만 남았다. 채팅 화면(26 과 함께 45% 를 만든 다른 축)은 이번 범위가 아니다 — 남은 축으로 보고한다.

## QA 진입 경로

- **25 배차 현황**: 홈 → 합승 탐색/생성 → 21 매칭 대기(정원 충족) → 자동 전이. 확인 항목:
  (a) 기사 배정 전 5초마다 `/status` 만 나가는지(상세 없음), (b) 배정 순간 `/rooms/{id}` 1회 + 25c
  배정 확인 화면, (c) 배정 후 기사 위치는 계속 5초 주기, (d) 기사 board → 26 자동 전이,
  (e) 3분 타임아웃 시 25b-실패.
- **26 운행 중**: 25 에서 자동 전이(또는 진행 배너 → 운행 중). 확인 항목: (a) 진입 직후 상세 1회 뒤
  4초마다 `/status` 만, (b) 지도 마커·경로가 그대로 유지, (c) 기사 운행 종료 시 28 최종 요금 자동 전이,
  (d) 비행기 모드로 상태 폴링을 몇 주기 실패시켜도 화면이 에러로 덮이지 않고 27 신고 진입이 살아 있는지.
