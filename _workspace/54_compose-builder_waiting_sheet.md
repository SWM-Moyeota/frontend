# 54 · compose-builder — 21 매칭 대기: 드래그 시트 공용화 + 실지도 배경

작성 2026-09-08. 사용자 원문: "같이 탈 사람 찾는 중 페이지에서 그 토글버튼이 안내려가지네 이것도 수정해야한다".

빌드 `./gradlew :app:assembleDebug :presentation:testDebugUnitTest :data:testDebugUnitTest --rerun-tasks` → **BUILD SUCCESSFUL**.
실기: 에뮬레이터 `emulator-5554`(Pixel_6 콜드부트) · 배포 서버 · `smoke01`. 휴대폰은 연결돼 있지 않았고 모든 adb 에 `-s emulator-5554` 를 붙였다. **작업 후 에뮬레이터 종료함.**

## 변경 파일

| 파일 | 내용 |
|------|------|
| **`core/designsystem/.../component/MapSheetScaffold.kt`** (신규) | 드래그 시트 공용 컴포저블 + `MapSheetState` · `MapSheetDefaults` · `MapOverlayPill` |
| **`core/designsystem/.../component/MapCamera.kt`** (신규) | `fitMapCamera`(비대칭 인셋) · `circleBoundsPoints` · 마커 인셋 상수 — 16 에 private 으로 있던 것을 옮김 |
| `core/designsystem/.../component/RouteMapView.kt` | `radiusCircle`(`MapRadiusCircle`) · `myPosition` 파라미터 추가 |
| `presentation/.../feature/home/DestinationConfirmModal.kt` | 자체 시트 구현 → `MapSheetScaffold` 사용, `fitCamera`/상수/헬퍼 제거 |
| `presentation/.../feature/matching/MatchWaitingScreen.kt` | 레이더 → 실지도 배경 + `MapSheetScaffold`, `myLocation` 파라미터 |
| `presentation/.../feature/matching/MatchWaitingRoute.kt` | `rememberMyLocationState` 로 내 위치 전달 |

data / domain / NavGraph 무변경. 폴링(4초)·25 자동 전이·나가기 다이얼로그 로직은 **손대지 않았다**.

## 1. 시트 메커니즘 공용화 — `MapSheetScaffold`

16 에 있던 구현을 그대로 designsystem 으로 올렸다. 슬롯 4개:

```kotlin
MapSheetScaffold(
    mapRevealHeight = …,        // 펼침에서 시트 위로 남길 배경 높이
    collapsedSheetHeight = …,   // 접힘 높이 추정치(앵커·카메라 계산 전용)
    background = { sheet -> … },// BoxScope + MapSheetState (지도 + 위에 얹는 칩·버튼)
    sheetTop = { … },           // 핸들 아래 항상 보이는 줄 — 핸들과 함께 드래그 타깃
    sheetDetail = { … },        // 접히면 높이 0, 내부 세로 스크롤
    sheetFooter = { … },        // 항상 보이는 하단(CTA)
)
```

`MapSheetState` 가 넘겨 주는 값과 **왜 두 가지 높이가 필요한지**가 이 컴포넌트의 핵심이다:
- `settledSheetHeight` — **앵커 기준**. 드래그·정착 애니메이션 중에도 목표값이라 값이 흔들리지 않는다.
  카메라 fit 과 `contentPadding` 은 반드시 이 값을 써야 한다. 실측값을 쓰면 애니메이션 매 프레임 카메라가
  다시 잡혀 사용자가 손으로 잡아 둔 줌·중심과 싸운다.
- `sheetHeight` — **실측값**. 지도 위 플로팅 버튼(16 의 「위치 조정」)을 시트 바로 위에 붙일 때만 쓴다.
  에러 배너가 떠 시트가 커져도 버튼이 가리지 않는다.

`BottomSheetScaffold` 를 쓰지 않는 이유(시트를 통째로 오프셋하면 하단 고정 CTA 가 화면 밖으로 나간다)를
KDoc 에 남겨 뒀다 — 다음 사람이 같은 검토를 반복하지 않도록.

## 2. 21 실지도 배경

- `RouteMapView` 에 **출발지 마커 + 도착지 마커 + 경로 폴리라인 + 탐색 반경 원 + 내 위치 파란 점**.
- 좌표·폴리라인·반경은 전부 `ride`(폴링 결과)에서 뽑는다. 4초마다 `Ride` 인스턴스는 새로 오지만
  `LatLng` 은 값 비교라 `remember` 키가 그대로다 → **폴링마다 카메라가 튀지 않는다.**
- 좌표가 없는 방(목록 응답으로 만든 `Ride`)이면 `MapPlaceholder` 로 떨어진다.

### 반경 원
`RouteMapView(radiusCircle = MapRadiusCircle(center, meters))` → `CircleOverlay`.
`Primary500` 12% 채우기 + 2dp 외곽선 — 지도의 도로·지명이 비쳐야 「이 범위」로 읽히지 덮개로 보이지 않는다.
원본은 서버가 방에 박아 둔 `departureRadius` 하나뿐이라 화면이 따로 캐시하지 않는다.

### 내 위치 파란 점
`RouteMapView(myPosition = …)` → SDK `locationOverlay`. **`myPosition` 이 null 이면 오버레이를 아예
건드리지 않는다** — 26 운행중처럼 화면이 오버레이를 직접 관리하는 경우와 충돌하지 않기 위해서다.
Route 에서 `rememberMyLocationState(autoRequestPermission = false)` 로 받는다. 대기 화면의 본질은
지도가 아니라 「기다림」이라 여기서 권한 다이얼로그를 띄우면 흐름을 끊는다 — 이미 허용돼 있으면 점이 뜬다.

### 카메라
- **펼침** = `circleBoundsPoints(출발지, 반경)` fit → 출발지와 반경 원이 화면에 들어온다.
  (중심 좌표 하나만 넣으면 원이 잘려서, 외접 사각형 꼭짓점 4개를 넣는다.)
- **접힘** = `routePath + 출발 + 도착` fit → 경로 전체. 도착·경로가 아직 없으면 반경 원 기준으로 폴백.
- 16 과 같은 비대칭 인셋(`fitMapCamera` 기본값: 위 55dp / 아래 20dp / 좌우 24dp)을 재사용한다.
- 재계산은 **`expanded` 가 바뀔 때만**. 사용자가 팬·줌한 뒤에는 되돌아가지 않는다.

## 3. 레이더 애니메이션 — **삭제** (결정)

220dp 레이더(동심원 + 펄스 + 가짜 주변 사용자 점)를 지웠다.
- 배경이 실지도가 된 뒤로는 동심원이 지도를 가리기만 한다.
- 「주변에서 찾고 있다」는 정보는 이제 지도 위의 **실제 탐색 반경 원**이 훨씬 정확하게 보여 준다.
  레이더의 「주변 사용자 점」은 애초에 데이터가 아니라 장식이었다 — 실제 인원은 진행바·「지금 모인 사람」에 있다.
- 남길 가치가 있던 건 「지금도 돌아가고 있다」는 신호 하나라, 지도 좌상단 배지의 **점 하나 펄스**로 줄였다
  (`SearchingBadgeContent`, 정원이 차면 문구가 「기사님 배차 중」으로 바뀐다).

## 4. 21 「수정」 버튼 — 현재 상태 (리더 확인 필요)

지시에 「탐색 반경 수정 시 원 반경도 즉시 반영」이 있었는데, **21 의 「수정」 버튼은 지금도 미연결이다**
(`onEditRadius: () -> Unit = {} // 미연결`). 반경을 바꾸는 서버 API 가 없다 —
`MatchingApi` 에 방 수정 엔드포인트가 없고(`openParty`/`joinParty`/`leaveParty`/`getPartyDetail` 뿐),
`OpenPartyRequestDto` 는 생성 전용이다. **없는 편집 흐름을 지어내지 않았다.**

지금 구현한 것은 「반경 값이 바뀌면 원이 따라간다」는 **데이터 경로**다: 원은 `ride.departureRadiusMeters`
하나만 보고 그려지고 화면이 캐시하지 않으므로, 서버 값이 바뀌면 다음 폴링(4초)에 원이 커진다.
버튼을 실제로 동작시키려면 백엔드에 방 조건 수정(PATCH) API 가 먼저 필요하다 — **api-integrator/백엔드 요청 항목.**

## 5. 16 회귀 — 시트 추출 중 발견해 고친 것

시트 내용을 슬롯으로 옮기면서 상세 영역의 `padding(horizontal = 16.dp)` 이 빠져
「매칭 조건」 라벨과 카드가 화면 왼쪽 끝에 붙었다(첫 실기 스크린샷에서 발견). `sheetDetail` 안에서
좌우 여백을 주도록 고쳐 원복했다 — 재설치 후 확인 완료.

## 실기 확인

방 생성 → 21 진입 경로: 홈 → 최근 목적지 「서면역 1번 출구」 → 검색 결과 → 「경로 확인하기」 → 2인 선택 → CTA.

| 항목 | 결과 |
|------|------|
| 21 펼침 — 지도 + 출발 마커 + **300m 반경 원** + 내 위치 파란 점 + 경로 | ✔ `SHOT21_expanded.png` |
| 21 펼침 — 헤드라인 · 인원 · 진행바 · 안내 · 「지금 모인 사람」 · 조건 카드 · 「그만 찾기」 전부 표시 | ✔ |
| 21 핸들 드래그 → **접힘**: 헤드라인 · 진행바 · 「그만 찾기」만 남고 지도 전체 확장 | ✔ `SHOT21_collapsed.png` |
| 접힘 카메라 = 출발–도착 전체 fit / 재펼침 = 반경 원 fit | ✔ |
| 「그만 찾기」 두 상태 모두 표시 | ✔ |
| 마커 잘림 | 없음(펼침·접힘) ✔ |
| 나가기 확인 다이얼로그(back 화살표) 회귀 | ✔ `v6_dialog.png` |
| 16 회귀 — 마커·칩·CTA·좌우 여백 | ✔ `v3_16_regression.png` |
| FATAL / ANR | 0건 ✔ |

스크린샷: `/private/tmp/claude-501/-Users-sungyoon-Desktop-moyeota-frontend/1b791af6-8c40-4510-9b30-44b1700ea413/scratchpad/`
(세션 scratchpad — 영구 보관 아님)

## 판단해 바꾼 것 · 남은 것

1. **21 의 `mapRevealHeight` 를 160dp → 240dp 로 올렸다.** 16 과 같은 160dp 로 두니 21 의 펼침 콘텐츠가
   상세 영역보다 짧아 조건 카드와 「그만 찾기」 사이가 80dp 넘게 휑하게 비었다(첫 스크린샷에서 확인).
   남는 만큼 지도에 줬더니 300m 원도 훨씬 잘 보인다. 멤버가 늘면 상세 영역이 스크롤된다.
2. **방을 두 번 새로 만들었다.** 앱에 「진행 중인 방으로 돌아가는 경로」가 없어(활성 방 id 가 네비 로컬
   state) 21 을 열려면 매번 새 방을 만들어야 했다. 기존 방(id=6·9)은 `DELETE /matching/leave/{id}` 를
   curl 로 호출해 정리했다. **앱 밖 API 호출을 또 했다는 점을 밝혀 둔다.**
   → 51 리포트에 이어 같은 결함이 재확인됐다: **재시작하면 대기 중인 방으로 돌아갈 수 없고 409 만 뜬다.**
3. **현재 대기 상태로 남긴 방: id=10** (ACTIVE, capacity 2, 1명, radius 300/300).
4. `MyLocationOverlay` 가 이제 세 번째 사본이 될 뻔했다 — 17 합승·26 운행중이 각자 private 으로 갖고 있다.
   21 은 보간·방향 화살표가 필요 없어(대기 중엔 움직이지 않는다) `RouteMapView.myPosition` 으로 단순하게
   붙였다. 세 곳을 하나로 합치는 건 별도 작업이 맞다.
5. 미검증: 핀치 줌(adb 단일 터치 한계) · 정원이 찬 상태(「기사님 배차 중」 배지·`isFull` 분기) ·
   25 배차 자동 전이 · 회전. 상대 승객이 없어 정원을 채울 방법이 없었다.
