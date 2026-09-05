# 34 · compose-builder — 방장(host) 개념 제거 (presentation/)

리포트 33(api-integrator)의 domain/data 정리에 이어 화면을 맞춘다.
`data/`·`domain/` 은 건드리지 않았다.

## 제품 판단 (리더 확인 사항 P-4 반영)

「이 인원으로 출발」 CTA를 **화면에서 제거**했다. 자동 기사 매칭으로 전환된 도메인에는
"누군가 출발을 결정한다"는 행위 자체가 없다(서버 `/matching/start` 삭제, 정원이 차면 서버가
스스로 기사 매칭을 시작). 21 매칭 대기에서 이미 같은 결정을 내렸고, 22 도 이에 맞춘다.
따라서 22 → 25 배차 현황 네비게이션 엣지도 사라진다 — 25 로 넘기는 건 status 전이를 관찰하는
21 하나뿐이다.

## 변경 파일

| 파일:라인 | 변경 |
|---|---|
| `feature/home/DestinationConfirmRoute.kt:41` | `CreatePartyViewModel` 에서 `userSession` 의존성 제거(생성자·factory). 생성자는 토큰 주체라는 주석 추가 |
| `feature/home/DestinationConfirmRoute.kt:58` | **[P-1 컴파일 오류]** `NewParty(hostId = …)` 인자 삭제 |
| `feature/home/DestinationConfirmRoute.kt:240` | `DestinationConfirmRoute` 의 `userSession` 파라미터·`UserSession` import 제거 |
| `feature/matching/RideDetailRoute.kt:119` | **[P-2 컴파일 오류]** `isHost = current.ride.hostId == currentUserId` 삭제 |
| `feature/matching/RideDetailRoute.kt:88,~82` | `onDepart` 파라미터와 더미 분기 전달 제거 |
| `feature/matching/RideDetailRoute.kt:~103` | **[P-7]** D-5 한계 주석에서 "방장 여부"·"방장 배지" 삭제, "내 멤버십 여부"·「나 제외」 필터 제약만 유지 |
| `feature/matching/RideDetailScreen.kt:80~97` | **[P-3/P-5]** `isHost` 파라미터·`onDepart` 콜백 삭제. KDoc 의 출발 CTA 항목 제거 + 도메인 변경 문단 추가 |
| `feature/matching/RideDetailScreen.kt:~295~310` | **"방장" 배지 제거** — 멤버 전원 동등 표시. `forEachIndexed` → `forEach` |
| `feature/matching/RideDetailScreen.kt:324~331` | 문구 교체: "인원이 안 차도 방장이 시작하면…" → 21과 동일 카피(`정원이 차면 기사님이 자동으로 배차돼요` / 정원 도달 시 `기사님을 찾고 있어요. 배차되면 바로 알려드릴게요`) |
| `feature/matching/RideDetailScreen.kt:339~344` | **[P-4]** 「이 인원으로 출발」 CTA + else 대기 문구 삭제. 하단은 전폭 「나가기」 하나(21 매칭 대기와 같은 형태). `departing` 상태·미사용 import 6건(Arrangement, PrimaryCtaButton, remember/mutableStateOf/getValue/setValue) 정리 |
| `feature/explore/JoinConfirmScreen.kt:65,223~228,444~469` | **[P-6]** `JoinHost` → `FirstJoinedMember`, `host` → `representative`/`member` 리네이밍. **GrayPill "방장" → "동승자"** |
| `core/MainNavGraph.kt:316` | `DestinationConfirmRoute(userSession = …)` 인자 제거 |
| `core/MainNavGraph.kt:393` | `RideDetailRoute(onDepart = …)` 블록 삭제 + 25 진입 경로가 21뿐임을 못박는 주석 |

남은 "방장" 문자열은 **주석 4곳뿐**(RideDetailScreen KDoc·인라인, MatchWaitingScreen KDoc,
JoinConfirmScreen 더미 주석)이며 전부 "제거됐다"는 이력 서술이다. UI 문자열에는 없다.

## 빌드

```
./gradlew :presentation:compileDebugKotlin :app:assembleDebug --console=plain
BUILD SUCCESSFUL in 9s
```
(경고는 기존 것 2건 — AccountTypeScreen quadraticBezierTo deprecated, MyRidesScreen 상수 조건)

## 에뮬레이터 실기 (emulator-5554, Pixel_6 콜드부트)

시작 전 화면: 런처 홈(앱 미실행) — `_workspace/screenshots/nohost_00_before.png`.
서버(localhost:8080)는 살아 있었으나 **재시작된 상태라 testuser1·qahost01 계정이 없어**
`POST /api/v1/auth/register` 로 재생성했다(qadriver1 은 이미 존재). party 1 도 없어
qahost01 토큰으로 **party 2** 를 새로 만들었다(부산대 정문 → 서면역, capacity 3).

| # | 스크린샷 | 확인 내용 |
|---|---|---|
| 1 | `nohost_01_join_confirm.png` | testuser1 로 합류 확인 화면 — 멤버 pill 이 **"동승자"**(구 "방장") |
| 2 | `nohost_02_ride_detail_top.png` | 22 탑승 상세 2/3 — 멤버 「멤버 4」에 **방장 배지 없음**, 하단은 전폭 「나가기」 **하나뿐** |
| 3 | `nohost_03_ride_detail_bottom.png` | 스크롤 하단 — 멤버 4·멤버 3 모두 배지 없음, 안내 문구 **"정원이 차면 기사님이 자동으로 배차돼요"** |
| 4 | `nohost_04_auto_dispatch.png` | 3번째 멤버를 curl 로 합류시키자 서버가 status `ACTIVE→MATCHING` 자동 전이, 21 이 이를 관찰해 앱이 **수동 출발 없이** 배차/운행 화면으로 진행 — CTA 제거가 흐름을 막지 않음을 입증 |
| 5 | `nohost_05_end_login.png` | 정리 상태: 앱 데이터 초기화 후 로그인 화면 |

서버는 죽이지 않았고 party 2 는 MATCHING 상태로 남아 있다.

### 알려진 한계 (이번 변경과 무관, 기존 D-5)

`currentUserId` 가 고정 "1" 이라 「나 제외」 필터가 동작하지 않아 자기 자신(멤버 3)도
"함께 타는 사람" 목록에 뜬다. 서버가 "내 멤버십" 정보를 주기 전까지는 그대로다.

## 화면 진입 경로

로그인 → 홈 → 하단탭 **합승** → 시트 스와이프업 → 카드 「합류」 → 「이 탑승에 합류하기」
→ 21 매칭 대기 → 조건 카드 탭 → **22 탑승 상세**
