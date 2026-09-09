# 55 · compose-builder — 매칭~배차 단계 재구성 (21 › 25b › 25c › 25)

작성 2026-09-08. 사용자 원문: "화면 구성 저렇게 해서 변경해줘 저게 더 자연스러운거같아 문구까지 상황에 맞게 변경좀",
추가: "택시 기사 찾는 중에서 나가는 API를 일부러 안 만들었는데 나가기 버튼이 왜 있는 거야?"

빌드 `./gradlew :app:assembleDebug :presentation:testDebugUnitTest :data:testDebugUnitTest --rerun-tasks` → **BUILD SUCCESSFUL** (유닛 22건 통과).
실기: 에뮬레이터 `emulator-5554`(Pixel_6 콜드부트) · 배포 서버 · smoke01 / smokep2 / smokedrv.
휴대폰은 연결돼 있지 않았고 모든 adb 에 `-s emulator-5554` 를 붙였다. **작업 후 에뮬레이터 종료함.**

## 변경 파일

| 파일 | 내용 |
|------|------|
| **`presentation/.../feature/matching/DispatchStageScreens.kt`** (신규) | 25b `DriverSearchScreen` · 25b-실패 `DriverSearchFailedScreen` · 25c `DriverAssignedScreen` + `pickupEtaMinutes` |
| **`core/designsystem/.../component/RadarSearchArea.kt`** (신규) | 21 에서 걷어냈던 레이더를 designsystem 으로 승격해 25b 에서 재사용 |
| **`presentation/src/test/.../PickupEtaTest.kt`** (신규) | 도착 예정 어림 계산 유닛 5건 |
| `presentation/.../feature/matching/DispatchStatusRoute.kt` | 한 라우트가 상태에 따라 4화면을 갈아 끼움 + `onRetryMatching` |
| `presentation/.../feature/matching/DispatchStatusScreen.kt` | `DispatchInfoRow`·`CarIcon` 을 `internal` 로 올려 25b·25c 가 재사용 |
| `presentation/.../feature/matching/MatchWaitingScreen.kt` | 문구 3건 + **상태별 나가기 게이팅** |
| `presentation/.../feature/matching/MatchWaitingRoute.kt` | `onExitKeepingParty` 전달 |
| `presentation/.../feature/matching/RideDetailScreen.kt` | 나가기 버튼을 RECRUITING 에서만 노출, 그 외엔 단계 문구 |
| `presentation/.../core/MainNavGraph.kt` | `onExitKeepingParty` · `onRetryMatching` · 배차 뒤로가기 = 홈 |
| **`domain/.../model/Ride.kt`** | `RideStatus.CANCELED` 추가 — **모듈 경계 넘음, 아래 참고** |
| **`data/.../remote/PartyMappers.kt`** (+테스트) | `"CANCELED" → RideStatus.CANCELED` (FINISHED 와 분리) |

## 1. 단계 구조 — 라우트 하나, 화면 넷

**새 네비게이션 목적지를 만들지 않았다.** 단계는 **서버 상태의 함수**라, 뒤로가기로 되돌릴 수 있는
스택으로 쌓으면 상태와 화면이 어긋난다(이미 배정됐는데 「찾는 중」으로 돌아가는 식).
`DispatchStatusRoute` 가 폴링 결과를 보고 갈아 끼운다:

| 서버 status | 판정 | 화면 |
|---|---|---|
| `ACTIVE` (정원 미달) | — | **21** 사람 모으는 중 (`MatchWaitingScreen`, 지도+반경 원) |
| `MATCHING` | `driverId == null` | **25b** `DriverSearchScreen` — 레이더 |
| `CANCELED` (3분 타임아웃) | — | **25b-실패** `DriverSearchFailedScreen` |
| `DRIVER_ASSIGNED` 첫 감지 | `driverId != null` && `!assignedSeen` | **25c** `DriverAssignedScreen` (1회성) |
| 그 이후 | `assignedSeen` | **25** `DispatchStatusScreen` — 지도·기사 마커 |

- **MATCHING 과 DRIVER_ASSIGNED 는 도메인에서 둘 다 `DISPATCHING`** 이라 `Ride.driverId` 유무로 갈랐다
  (배정되면 서버가 `taxiDriverId` 를 채운다). 이 하나로 도메인 enum 을 더 쪼개지 않아도 됐다.
- **`CANCELED` 는 도메인에 새로 넣었다.** 서버는 **기사 매칭 3분 타임아웃**에도 방을 CANCELED 로 바꾸는데
  (`MatchingSweeper.giveUp` → `failMatching`), 그게 FINISHED 와 같은 `COMPLETED` 로 접혀 있어서
  「기사님을 찾지 못했어요」를 정상 종료와 구분할 수 없었다.
- `assignedSeen` 은 `rememberSaveable` — 구성 변경에는 살아남고 프로세스 재시작에는 사라진다.
  재시작 시 25 직행은 의도한 동작이다(이미 배정을 아는 사용자다).

## 2. 레이더의 이사 (21 → 25b)

54 에서 21 배경을 실지도로 바꾸며 걷어냈던 컴포넌트를 designsystem 으로 올려 25b 가 쓴다.
**같은 그림이 21 에서는 방해였고 25b 에서는 맞다**는 것이 이번 변경의 요지다:
- 21 에는 보여 줄 지도가 있다(탐색 반경 원이 「어느 범위에서 찾는지」를 실제로 말해 준다) → 동심원은 가림막.
- 25b 에는 보여 줄 지도가 없다(기사 위치는 아직 없고, 경로는 21 에서 이미 봤다) → **기다림 자체가 화면의 내용**.

그림의 점은 **데이터가 아니라 장식**이라는 사실을 KDoc 에 명시했다(실제 기사 수가 아니다).

## 3. 최종 문구

| 화면 | 자리 | 문구 |
|---|---|---|
| 21 | 헤더 | 같이 탈 사람 찾는 중 |
| 21 | 헤드라인 | **보통 2분 안에 모여요** / (정원 도달) 정원이 다 찼어요 |
| 21 | 부제 | 지금 같은 방향 N명(나 외 M명) · 목표 K명 |
| 21 | 안내 배너 | **정원이 차면 기사님을 자동으로 찾아요** / (정원 도달) 곧 기사님을 찾기 시작해요 |
| 21 | 지도 배지 | 같이 탈 사람 찾는 중 / (정원 도달) 정원이 다 찼어요 |
| 21 | 하단 버튼 | 그만 찾기 / **기사님을 찾는 중…**(비활성, 나가기 불가 단계) |
| 25b | 헤더 | 기사님 찾는 중 |
| 25b | 헤드라인 | 주변 기사님에게 요청하고 있어요 |
| 25b | 부제 | 보통 1분 안에 배정돼요 · 최대 3분 |
| 25b | 안내 | 기사님이 수락하면 바로 알려드려요 |
| 25b-실패 | 헤드라인 | 기사님을 찾지 못했어요 |
| 25b-실패 | 부제 | 3분 동안 수락한 기사님이 없어 요청이 취소됐어요 |
| 25b-실패 | 안내 / CTA | 다시 찾으면 같은 조건으로 새 방을 만들어요 / 「다시 찾기」 |
| 25c | 헤더 | 배정 완료 |
| 25c | 헤드라인 | 기사님이 배정됐어요 |
| 25c | 부제 | 약 N분 뒤 탑승 위치 도착 예정 / (위치 없음) **탑승 위치로 오고 있어요** |
| 25c | 안내 / CTA | 기사님 정보를 확인하고 탑승 위치로 이동하세요 / 「배차 상태 보기」 |
| 24 | 나가기 자리 | (RECRUITING 아님) 기사님을 찾는 중이라 나갈 수 없어요 / 운행 중이에요 / 취소된 탑승이에요 / 완료된 탑승이에요 |

정보 카드는 25b(탑승 위치·도착지·동승자·1인 부담) / 25c(탑승 위치·동승자·1인 부담) 로 같은 컴포넌트를 쓴다.
1인 부담은 `ride.farePerPerson`(= 서버 `estimateFare` ÷ 정원)이라 하드코딩이 없다.

**기사명·별점은 어디에도 넣지 않았다.** 백엔드 `DriverSummary` 는 (좌석수·번호판·차종) 뿐이라
「김OO · 4.9」 같은 값을 만들 근거가 없다. 실기에서도 서버가 준 `부산99바9999 · K5 · 4인승` 만 떴다.

## 4. 나가기 규칙 — **MATCHING 이후 나가기 불가** (화면 규칙)

서버 `Party.leave` 는 `ensureRecruiting()` 으로 **ACTIVE(모집 중)에서만** 허용한다.
실측: `DELETE /api/v1/matching/leave/11` (DRIVER_ASSIGNED) → **409 `PARTY_NOT_RECRUITING`**.

앱의 모든 나가기 진입점을 이 규칙에 맞췄다:

| 화면 | 상태 | 나가기 | 뒤로가기 |
|---|---|---|---|
| 21 | `RECRUITING` | 「그만 찾기」 + 확인 다이얼로그 | 같은 다이얼로그 |
| 21 | 그 외(MATCHING 감지 직후 포함) | 「기사님을 찾는 중…」 **비활성** | 방 유지한 채 홈 탭 |
| 21 로딩·에러 | 상태 모름 | — | 방 유지한 채 홈 탭 |
| 25b / 25c / 25 | — | **없음** | 방 유지한 채 홈 탭 |
| 24 탑승 상세 | `RECRUITING` | 「나가기」 | 뒤로 |
| 24 탑승 상세 | 그 외 | 버튼 대신 **단계 문구** | 뒤로 |

- 21 은 폴링이 MATCHING 을 감지하는 **즉시** 버튼이 비활성으로 바뀐다. 25b 로 넘어가기까지의 4초 동안에도
  누를 수 없다(사용자 지적 지점).
- 24 는 버튼을 그냥 지우지 않고 **왜 못 나가는지**를 적었다 — 버튼만 사라지면 앱이 고장 난 것처럼 보인다.
- 로딩·에러 상태에서도 나가기를 걸지 않는다. 그때는 방 상태를 모르는데 leave 를 쏘면 매칭 중인 방에 409 를 던진다.

## 5. 25c 도착 예정 시간

서버에 **기사→탑승지 ETA API 가 없다.** 앱이 기사 위치와 탑승지의 **직선거리**를 25km/h 로 나눠 어림한다
(직선이 실도로보다 짧으므로 속도를 낮게 잡아 상쇄). 좌표가 없으면 시간을 **만들어 쓰지 않고**
「탑승 위치로 오고 있어요」로 떨어진다. 좌표 검증은 지도와 같은 `latLngOrNull` 이다 — 서버 위경도 전치(QA D-1)
값으로 거리를 재면 「약 9,000분 뒤 도착」이 나온다.

유닛 5건(`PickupEtaTest`): null·음수·NaN/∞ 는 계산하지 않음, 0m 도 최소 1분(「약 0분 뒤 도착」은 오류처럼 읽힌다),
25km/h 환산 3케이스.

## 실기 확인 (전 단계 실서버 통과)

승객 A `smoke01` 이 앱에서 2인 방(id=11) 생성 → curl 로 승객 B `smokep2` 합류 → 정원 도달 →
curl 로 기사 `smokedrv` 온라인·위치 보고 → `POST /dispatch/calls/11/accept`.

| 단계 | 결과 | 스크린샷 |
|---|---|---|
| 21 사람 모으는 중 (ACTIVE) — 새 문구·「그만 찾기」 활성 | ✔ | `SHOT_21_active.png` |
| B 합류 → 서버 `MATCHING` → **자동으로 25b 전이** | ✔ | — |
| 25b 기사 찾는 중 — 레이더·정보 카드·안내, **취소 버튼 없음** | ✔ | `SHOT_25b_searching.png` |
| 기사 accept → 서버 `DRIVER_ASSIGNED` → **자동으로 25c 전이** | ✔ | — |
| 25c 배정 완료 — 차량 `부산99바9999 · K5 · 4인승`(서버 값), 기사명·별점 없음 | ✔ | `SHOT_25c_assigned.png` |
| 「배차 상태 보기」 → 25 택시 오는 중(지도·차량 카드) | ✔ | `SHOT_25_onway.png` |
| FATAL / ANR | 0건 | — |
| 뒷정리 — 기사 board → complete 로 방 `FINISHED`, smoke01 활성 방 0 | ✔ | — |

스크린샷: `/private/tmp/claude-501/-Users-sungyoon-Desktop-moyeota-frontend/1b791af6-8c40-4510-9b30-44b1700ea413/scratchpad/`
(세션 scratchpad — 영구 보관 아님)

## 확인 필요 · 백엔드 요청

1. **모듈 경계를 또 넘었다.** `domain/model/Ride.kt`(enum 상수 1개)와 `data/remote/PartyMappers.kt`(+테스트)를
   직접 고쳤다. 원래 api-integrator 영역이다. CANCELED 를 COMPLETED 와 접어 두면 25b-실패 화면을
   만들 수 없어 불가피했지만 **검토 대상**이다. `RideStatus` 에 대한 exhaustive `when` 은 매퍼 하나뿐이라
   (그마저 `else` 가 있다) 다른 화면은 영향받지 않는 것을 grep 으로 확인했다.
2. **`GET /api/v1/dispatch/rides/{partyId}` 가 500 이다** (실측). 기사가 `POST /dispatch/location` 으로
   좌표를 올려도(204) 승객이 그 값을 못 받는다. 그래서 **25c 의 「약 N분 뒤 도착」과 25 의 기사 마커가
   실기에서 한 번도 뜨지 않았다** — 25c 는 「탑승 위치로 오고 있어요」 폴백으로, 25 는 「기사님 위치를
   받아오는 중」으로 떨어졌다. 앱 쪽 폴백은 정상 동작하지만 **기능 자체가 백엔드에 막혀 있다.**
   `POST /dispatch/rides/{id}/arrive` 도 500 이었다(board·complete 는 204 정상).
3. **25b-실패(3분 타임아웃) 화면은 실기 미검증이다.** 재현하려면 기사를 3분간 오프라인으로 두고 기다려야
   해서 이번엔 성공 경로를 우선했다. Preview 와 상태 분기 코드로만 확인했다.
4. **배차 단계에서 홈으로 나가면 돌아올 길이 없다.** 51·54 에서 이미 올린 결함이 이번 설계에서 더 두드러진다 —
   25b/25c/25 의 뒤로가기가 「방 유지 + 홈」인데, 홈·합승 탭 어디에도 「진행 중인 탑승」 배너/카드가 없다.
   앱을 재시작해도 같다(활성 방 id 가 네비 로컬 state). **홈에 진행 중 배너를 넣는 별도 작업이 필요하다.**
5. 미검증: 핀치 줌, 회전, 25b→25c 사이 FCM 알림, `MATCHED`(서버 COMPLETED) 상태의 화면.

## 7. 조건 수정 제거 (사용자 요청, 2026-09-09)

사용자 원문: "매칭 조건이랑 탐색 반경을 매칭중에 수정 버튼이 있어 수정할 수 있다고 되어 있는데 아예 없애는게
맞는거같아 방장이라는 개념이 존재하지 않아서 버튼 없애고 매칭중에는 수정 불가능한걸로"

빌드 `:app:assembleDebug :presentation:testDebugUnitTest :data:testDebugUnitTest --rerun-tasks` → **BUILD SUCCESSFUL**.

### 변경

| 파일 | 내용 |
|---|---|
| `presentation/.../feature/matching/MatchWaitingScreen.kt` | 「수정」 버튼 2개 삭제 · `onEditCondition`/`onEditRadius` 파라미터 삭제 · `ConditionRow` 의 `onEdit` 파라미터 자체 제거 · 카드 아래 안내 한 줄 추가 · `mapRevealHeight` 240→216dp |

- **`ConditionRow` 에서 `onEdit` 파라미터를 통째로 없앴다.** null 기본값으로 남겨 두면 「언젠가 다시 붙일
  자리」처럼 읽히는데, 방장 개념이 사라진 이상 조건을 고칠 주체 자체가 없다.
- KDoc 의 이동 규칙 두 줄(「수정」 → 16/15 [미연결])을 **읽기 전용인 이유** 한 줄로 바꿨다.
- 값은 그대로 읽기 전용 표시다: `매칭 조건 2인` / `탐색 반경 300m`.
- 추가 문구: **"매칭 조건은 방을 만들 때 정해지고 찾는 중엔 바꿀 수 없어요"** (12sp, `GrayAsh`, 가운데 정렬).
  상세 영역 안에 있어 **접힘 상태에서는 자동으로 사라진다** — 접힘은 「진행 상황」만 보는 상태다.
- 안내 한 줄이 늘어 상세 콘텐츠가 ~26dp 길어졌다. 펼침에서 문구가 잘리지 않도록 `mapRevealHeight` 를
  240 → **216dp** 로 낮춰 그만큼 상세 영역에 돌려줬다.

### 다른 화면의 같은 진입점 — **없다**

`grep -rn '"수정"' presentation/src/main` → **0건**(변경 후). 매칭 조건/반경을 고치는 진입점은 21 에만 있었다.
남아 있는 `onEdit*` 콜백은 전부 무관한 것들이다:
- `MannerPledgeScreen.onEditNickname` (07 가입 — 닉네임 중복 시 되돌아가기)
- `EmailCodeScreen.onEditEmail` (05 이메일 인증 — 주소 고치기)

「조건 넓혀 찾기」 류 CTA 는 **원래 없었다**(와이어프레임에만 있던 것으로 보이며 코드에 흔적 없음).

### 실기 확인 — **하지 못했다** (중요)

에뮬레이터를 콜드부트해 21 까지 가려 했으나 **공용 테스트 계정이 다른 세션에서 사용 중**이라 방을 만들 수 없었다:
- `smoke01` — 내가 만들지 않은 방 **id=13**(부산진구 서전로10번길 5 → 전포카페거리)이 ACTIVE 로 있었다.
  잠시 뒤 다시 보니 `CANCELED`(매칭 3분 타임아웃) 상태였고 멤버는 「스모크」·「안녕이요」였다.
  **누군가 지금 이 계정으로 실제 플로우를 돌리고 있다.** 남의 세션을 깨뜨릴 수 없어 이 방은 건드리지 않았다.
- `smokep2` 로 앱 계정을 바꿔 시도했으나 방 생성이 **409** 로 막혔다(이 계정도 어딘가 활성 파티에 속해 있다).

그래서 이번 절의 변경은 **빌드·유닛·코드 리뷰까지만** 확인했고 **화면 렌더는 미확인**이다.
특히 「매칭 조건은 방을 만들 때…」 안내가 펼침 상태에서 잘리지 않는지는 dp 계산(상세 가용 ~402dp vs 콘텐츠 ~384dp)
으로만 판단했다 — **다음 QA 에서 눈으로 확인이 필요하다.** 멤버가 늘면 상세 영역이 스크롤되며 이 문구가
가장 먼저 스크롤 밖으로 나간다(구조상 마지막 항목).

### 이번 세션이 공용 자원에 남긴 것

- **에뮬레이터**: 승객 앱을 `smoke01` → 로그아웃 → `smokep2` 로 로그인한 상태로 두었다. (에뮬레이터는 종료함)
- **배포 서버**: 기사 `smokedrv` 를 진단 중 잠시 오프라인으로 내렸다가 **다시 온라인으로 복구**했다
  (`POST /dispatch/online` 은 이미 온라인이라 409, `POST /drivers/call` 204). 방 생성/삭제는 하지 않았다.
- 실기 도중 **기사 앱(`com.moyeota.driver`)이 콜 푸시로 계속 전면에 올라와** 탭이 엉켰다. 승객 앱 실기 검증
  시에는 기사 앱을 `am force-stop` 하거나 기사를 오프라인으로 두고 시작해야 한다 — QA 에 참고 사항으로 남긴다.

