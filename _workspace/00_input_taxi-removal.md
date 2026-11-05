# 택시(TAXI) 모드 제거 — 작업 지시

**날짜:** 2026-11-04 (지시) · 2026-11-05 (폴백 설계 정정 — 아래 「뒤집혔다」 절)
**브랜치:** `chore/remove-taxi-mode` ← `develop` **`1906d74`**
(지시 작성 시점엔 `8e7f7aa` 로 적었다. 그 사이 #76 Mixpanel·#77 Pretendard 가 develop 에 들어와
분기점이 `1906d74` 다. `8e7f7aa` 는 **택시 포크를 뜬 커밋** — 두 값이 다른 것이 정상이다.)

## 왜

택시 라인을 **하드 포크**로 분리했다.

```
SWM-Moyeota-Taxi/frontend   생성 2026-11-04 12:20, develop = 8e7f7aa (분기 0)
SWM-Moyeota-Taxi/frontend-driver · frontend-ios 도 같은 조직에 있음
```

사용자 확인: **「독립해서 갈거야」** — 포크는 앞으로 본 레포를 당겨 가지 않는다.
따라서 본 레포는 1차 배포(동승 / COMPANION) 전용이 되고, 택시 코드는 지운다.

**되돌리려면** 포크(`8e7f7aa` 분기) 또는 **제거 직전 커밋 `1906d74`** 에서 가져온다. 택시 화면의
마지막 모습은 `1906d74` 쪽이 더 새롭다 — Mixpanel `finish_ride`·`trigger_emergency` 기록 줄(#76)이
포크에는 없다.

## 기존 결정을 뒤집는다

`docs/MODE-ROUTES.md` 원칙 3 은 「택시 화면은 지우지 않는다」였다. 그 결정은 **택시 레포가
없던 시절**의 것이다. 이제 포크가 있으므로 전제가 사라졌다. 문서도 같이 고친다.

## 폴백 설계 — **뒤집혔다** (2026-11-05, 백엔드를 실제로 확인한 뒤)

### 처음 정한 것 (리더, 백엔드 조사 전)

> 삭제 뒤에는 `isStageOutsideMode` 안전장치가 사라진다. 서버가 `DISPATCHING`·`ONGOING` 을
> 보내면 어떻게 할 것인가:
>
> **`RideStatus` enum 과 `PartyMappers` 의 문자열 매핑은 그대로 둔다.**
> `ActiveStage` 에서 택시 단계만 빼고, 그 status 는 `ActiveStage.NONE` 으로 떨어뜨린다.
>
> | | 이유 |
> |---|---|
> | enum 값을 남기는 이유 | 서버가 `MATCHING`·`DRIVER_ASSIGNED`·`IN_RIDE` 를 보내도 **파싱이 깨지지 않아야** 한다. 값을 지우면 매퍼 `when` 이 바뀌고 모르는 문자열이 예외로 샐 수 있다 |
> | `NONE` 으로 떨어뜨리는 이유 | 이 앱에는 그 단계를 그릴 화면이 **없다**. 21 대기 화면에 가둬 두면 「같이 탈 사람 찾는 중」이라는 **거짓 문구**를 보여 주게 된다. 배너를 안 띄우는 쪽이 정직하다 |
> | 사용자가 잃는 것 | 없다. 방은 채팅 탭에 그대로 있고 들어갈 수 있다 |
>
> 동승 서버는 `TAXI_ENABLED=false` 라 이 경로 자체가 평시엔 발생하지 않는다. 방어 코드다.

이 설계는 **「서버가 그 status 를 아직 보낼 수 있다」**는 전제에 서 있었다. 그 전제가 틀렸다.

### 전제가 깨진 경위

백엔드가 기사·배차를 **자기 쪽에서 먼저 지웠다**(머지된 PR #297–#302).

- 서버 `PartyStatus` 에서 `MATCHING`·`DRIVER_ASSIGNED`·`IN_RIDE` 가 **삭제**됐다. 남은 값은
  `ACTIVE`·`COMPLETED`·`FINISHED`·`CANCELED` 네 개뿐이고, 없는 값은 **보낼 수가 없다.**
- `taxiEnabled` 는 서버에 `false` 로 박혔다 — 운영 플래그로 되돌릴 수 있는 값이 아니다.

즉 「서버가 보낼 수 있는 status 를 받는 방어 코드」가 **보낼 수 없는 status 를 받는 방어 코드**가 됐다.

### 그래서 실제로 한 것

| 결정 | 근거 |
|---|---|
| `RideStatus.DISPATCHING`·`ONGOING` 을 **지웠다** | 남은 네 값이 서버 `PartyStatus` 와 1:1 이 된다. 서버에 없는 단계를 앱만 아는 상태를 만들지 않는다 |
| `PartyMappers` 의 `MATCHING`·`DRIVER_ASSIGNED`·`IN_RIDE` 매핑 세 줄도 **지웠다** | 올 수 없는 문자열의 분기다 |
| 파싱 안전은 **이미 있던** `else -> RideStatus.RECRUITING` 에 맡긴다 | 처음 걱정했던 「모르는 문자열이 예외로 샌다」는 애초에 일어나지 않는다 — `when` 에 `else` 가 있다. 배포가 밀린 옛 서버가 `MATCHING` 을 보내도 모집 중으로 떨어지고 앱은 21 에 머문다 |
| `ActiveStage` 는 `WAITING`·`NONE` 둘만 남았다 | 떨어뜨릴 택시 단계가 없어져 `NONE` 폴백이 할 일이 없다 |
| `isStageOutsideMode` 도 **삭제** | 모드가 하나뿐이면 「모드 밖 단계」가 성립하지 않는다 |

**남겨 두는 쪽이 더 안전하지 않았나.** 아니다. 안전은 `else` 한 줄이 이미 주고 있고, enum 값을
남기면 아무도 쓰지 않는 `when` 분기·테스트·KDoc 을 같이 끌고 가야 한다. 같은 방어를 더 적은
코드로 얻는 쪽을 골랐다. **`PartyMappers` 의 `else` 한 줄은 그래서 건드리지 말 것** — 지금은
그게 옛 서버와 앱 사이의 유일한 안전장치다(해당 함수 KDoc 에 적어 뒀다).

## 지울 것 — 통째 삭제 (지시: 11 + 2 파일 → **실측 22 파일 3,495줄**, 아래 「실측」)

```
presentation/feature/matching/DispatchStageScreens.kt     480
presentation/feature/matching/DispatchStatusRoute.kt      462
presentation/feature/matching/DispatchStatusScreen.kt     328
presentation/feature/chat/RideOngoingRoute.kt             288
presentation/feature/chat/RideOngoingScreen.kt            530
presentation/feature/chat/EmergencyScreen.kt              (27 — 26 에서만 진입)
presentation/feature/mypage/RideCompleteScreen.kt         (33 — 운행 종료 뒤)
data/remote/DispatchApi.kt                                 20
data/remote/DispatchMappers.kt                             10
data/remote/dto/DispatchDtos.kt                            12
data/repository/RemoteDispatchRepository.kt                14
domain/model/Dispatch.kt                                   32
domain/repository/DispatchRepository.kt                    16
```

관련 테스트도 같이: `DispatchMappersTest` · `DriverPollGateTest` · `PickupEtaTest` ·
`RideStartSignalTest` 등 — **택시 단계에만 의미가 있는 것만.** 판단이 서지 않으면 남기고 보고한다.

### 실측 (2026-11-05, `git diff --cached --numstat`)

**소스 16 파일 3,131줄 + 테스트 6 파일 364줄 = 22 파일 3,495줄.** 지시의 목록과 다른 점만 적는다.

| 차이 | 내용 |
|---|---|
| 줄 수 정정 | `RideOngoingRoute.kt` 288 → **291**. 지시에 줄 수가 없던 두 개는 `EmergencyScreen.kt` **667**, `RideCompleteScreen.kt` **172**(괄호의 27·33 은 화면 번호였다) |
| 목록에 없던 삭제 ① | `presentation/core/AppMode.kt` 45 — 「검토」가 아니라 **통째 삭제**(아래 수술 표 참조) |
| 목록에 없던 삭제 ② | `data/remote/ReportApi.kt` 22 · `data/remote/dto/ReportDtos.kt` 30 · `data/.../ReportDtosTest.kt` 75 — **긴급 신고 배선**. 27 긴급 신고가 유일한 호출처였고 그 화면이 26 운행 중에서만 열렸으므로 함께 죽었다. 채팅 메시지 신고(`ChatReportReason`)는 **별 경로이고 그대로 살아 있다** |
| 테스트 | 지시의 4개 + `ReportDtosTest` · `RideFinishSignalTest` = 6개 |

긴급 신고가 사라져 `app/ReportLocationProvider.kt`(측위 공급자)는 **호출처가 없다.** `AppContainer`
배선은 끊었지만 파일은 남겨 뒀다 — 지울지, 21·24 에 신고를 다시 붙일지는 제품 판단이다.

## 수술할 것 — 공용 파일

| 파일 | 자리 |
|---|---|
| `presentation/core/MainNavGraph.kt` | 40 — import 5, 파라미터 2, 라우트 4개(`DISPATCH_STATUS`·`RIDE_ONGOING`·`EMERGENCY`·`RIDE_COMPLETE`), `isStageOutsideMode` 호출, 단계→라우트 분기 |
| `presentation/core/Routes.kt` | `DISPATCH_STATUS`·`RIDE_ONGOING`·`EMERGENCY`·`RIDE_COMPLETE` 상수 |
| `presentation/core/AppMode.kt` | ~~파일 통째 삭제 검토~~ → **삭제 확정**(45줄). 모드가 하나뿐이라 `AppMode`·`effectiveTaxiEnabled`·`isStageOutsideMode` 가 모두 무의미해졌다. `AppConfigViewModel` 의 디버그 강제값 경로도 함께 걷혔다 |
| `presentation/core/ActiveParty.kt` | `DRIVER_SEARCH`·`DRIVER_COMING`·`ONGOING` 단계와 그 문구 |
| `domain/model/AppConfig.kt` | `taxiEnabled` — **`minVersionCode`·`recommendedVersionCode` 는 건드리지 말 것** |
| `presentation/feature/matching/MatchWaitingScreen.kt` | 23 — 모드별 문구/버튼 분기 |
| `presentation/feature/home/DestinationConfirmModal.kt` | 3 |
| `presentation/feature/mypage/MyPageScreen.kt` | 디버그 「개발자 옵션 · 모드 강제」 |
| `app/AppContainer.kt` | `DispatchRepository` DI 배선 (+ 함께 죽은 `reportApi`·`currentLocation` 인자와 `ReportLocationProvider` 생성) |
| `docs/MODE-ROUTES.md` | ~~문서 전체 재작성 또는 삭제~~ → **재작성**. 파일명은 유지한다 — `_workspace/` 리포트 11건과 코드 KDoc 이 이 경로를 인용하고 있어 이름을 바꾸면 감사 기록이 끊긴다 |
| `docs/ANALYTICS.md` | `finish_ride`·`trigger_emergency` 두 줄 제거(기록 지점이 26·27 뿐이었다). 나머지 계약은 iOS·보드와 공유하므로 **한 글자도 건드리지 않았다** |

## 건드리지 말 것

- ~~`domain/model/Ride.kt` 의 **`RideStatus` enum 값**~~ → **뒤집혔다.** `DISPATCHING`·`ONGOING` 은
  삭제했다(위 「폴백 설계 — 뒤집혔다」). 남은 네 값과 `CANCELED`≠`COMPLETED` 분리는 그대로다.
- ~~`data/remote/PartyMappers.kt` 의 **status 문자열 매핑**~~ → **뒤집혔다.** 택시 세 줄은 지웠다.
  단 **`else -> RideStatus.RECRUITING` 한 줄은 그대로 둘 것** — 그게 폴백의 전부다.
- `AppConfig` 의 `minVersionCode`·`recommendedVersionCode` (백엔드 #277 대기 중)
- `local.properties` — **읽지도 쓰지도 말 것**
- `_workspace/` 를 다른 이름으로 옮기지 말 것 (추적 파일 200개가 `.gitignore` 의 `_workspace_*/` 에 걸린다)
- 백엔드 레포 (`../backend`) — 읽기 전용

## 완료 기준

```bash
./gradlew :app:assembleDebug :data:testDebugUnitTest :presentation:testDebugUnitTest --console=plain
```

- 빌드 통과 · 테스트 전부 통과
- `grep -ri "taxi\|dispatch" --include="*.kt"` 결과가 **의도적으로 남긴 것만**
- 동승 흐름(14 홈 → 15 목적지 → 17 탐색 → 20 합류 → 21 대기 → 24 채팅) 무사
