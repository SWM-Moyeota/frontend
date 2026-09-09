# 57 · compose-builder — 20 합류 확인 · 22 탑승 상세 실지도 교체

작성 2026-09-09. 사용자 원문: "승객 앱에서 매칭방에 입장할 때 지도가 정적으로 고정돼 있는데
해당 매칭방의 지도로 출발점·도착점·경로를 동적으로 보여줘야 함"

빌드 `:app:assembleDebug :presentation:testDebugUnitTest :data:testDebugUnitTest` → **BUILD SUCCESSFUL**.
실기: 에뮬레이터 `emulator-5554`(Pixel_6 콜드부트) · 배포 서버. 휴대폰 미연결, 모든 adb 에 `-s emulator-5554`.
**작업 후 에뮬레이터 종료 · 테스트 방 정리 완료.**

## 변경 파일

| 파일 | 내용 |
|---|---|
| **`core/designsystem/.../component/RouteStripMap.kt`** (신규) | 정보 화면용 지도 스트립 — 20·22 가 공유 |
| `core/designsystem/.../component/MapCamera.kt` | `polylineDistanceMeters` 추가 |
| **`presentation/.../core/RouteLabels.kt`** (신규) | `buildRouteChipLabel` · `pickupDistanceLabel` · `pickupDistanceMeters` |
| `presentation/.../feature/explore/JoinConfirmScreen.kt` | 목업 Canvas → 실지도, 칩·거리 실값, `RoutePreviewCanvas` 삭제 |
| `presentation/.../feature/explore/JoinConfirmRoute.kt` | 내 위치 전달 |
| `presentation/.../feature/matching/RideDetailScreen.kt` | 목업 Canvas → 실지도, 칩·거리 실값, `RouteMapArea` 삭제 |
| `presentation/.../feature/matching/RideDetailRoute.kt` | 내 위치 전달 |
| `presentation/.../feature/matching/MatchWaitingScreen.kt` | **21 안내 문구 잘림 수정**(아래 4절) |

25 배차·26 운행 중은 지시대로 건드리지 않았다.

## 1. 지도 — `RouteStripMap`

16·21 은 시트가 지도를 덮어 `contentPadding` 과 드래그가 필요했지만, 20·22 는 **정보 화면**이라
스트립 높이가 곧 보이는 높이다. 그래서 별도의 얇은 컴포저블로 뺐다:

```kotlin
RouteStripMap(originPosition, destinationPosition, routePath, useTextureView)
```
- 카메라는 `fitMapCamera` 의 **비대칭 인셋 기본값**(위 55 / 아래 20 / 좌우 24dp)을 그대로 쓴다 —
  마커 머리가 잘리지 않는 선까지만 당겨 잡는다(QA D-1 에서 정한 값).
- 좌표·경로·크기가 바뀔 때만 다시 계산한다. 그 사이 사용자가 핀치·팬으로 움직인 카메라는
  `NaverMapView` 가 보존한다.
- `destinationPosition == null`(좌표 미수신·못 쓸 좌표) → 기존 `MapPlaceholder` 로 떨어진다.
- 폴리라인이 없으면 마커 2개만으로 fit 한다.
- 20·22 는 일반 composable 목적지라 `useTextureView` 는 기본값 false 다(다이얼로그가 아니다).

## 2. 예상 시간 · 거리 — 잰 값만 쓴다

| 값 | 출처 |
|---|---|
| 「예상 N분」 | 서버 `estimateTime` (방 생성 시 확정) |
| 「N.Nkm」 | **서버 경로 폴리라인을 앱이 직접 잰 값**(`polylineDistanceMeters`, 이웃 점 거리 누적) |
| 「내 위치에서 약 N m」 | 내 위치 ↔ 방 출발 좌표 직선거리 |

- 서버는 **거리를 내려주지 않는다.** 출발·도착 **직선거리로 대체하지 않았다** — 실제 주행 거리보다
  늘 짧아 요금과 어긋나 보인다. 폴리라인이 없으면 거리 표기를 생략하고, 시간·거리 둘 다 없으면
  칩을 통째로 감춘다(16 경로 미리보기 칩과 같은 규칙).
- **「도보 2분 · 180m」 데모 문구는 삭제했다.** 대신 실측 직선거리만 쓴다 —
  「도보 N분」은 보행 속도를 지어내야 하고, 거리와 달리 잰 값이 아니다.
  내 위치를 모르면(권한 없음·미수신) 그 줄을 **비운다**. 권한은 자동 요청하지 않는다 —
  합류 판단에 꼭 필요한 값이 아니라 여기서 다이얼로그를 띄우면 흐름을 끊는다.

## 3. 실기 확인

준비: `smokep1` 로 curl 로 방 생성(서면역 1번 출구 → 부산역, 3인, 반경 500m) → 앱은 `smokep2` 로
합승 탭에서 그 방을 열었다.

| 항목 | 결과 | 스크린샷 |
|---|---|---|
| 20 합류 확인 — 실지도(출발·도착 마커 + 파란 경로선) | ✔ | `SHOT_20_map.png` |
| 20 칩 「예상 20분 · 6.1km」 (서버 20분 + 폴리라인 실측 6.1km) | ✔ | 〃 |
| 20 「내 위치에서 약 10m」 (에뮬레이터 위치가 서면역) | ✔ | 〃 |
| 22 탑승 상세 — 같은 실지도·칩·거리 | ✔ | `SHOT_22_map.png` |
| 마커 잘림 | 없음 | 〃 |
| FATAL / ANR | 0건 | — |

스크린샷: `/private/tmp/claude-501/-Users-sungyoon-Desktop-moyeota-frontend/1b791af6-8c40-4510-9b30-44b1700ea413/scratchpad/`

## 4. 곁다리로 고친 것 — 55-7 절 안내 문구가 잘려 있었다

지난 작업(55 리포트 7절)에서 21 에 넣은 안내 「매칭 조건은 방을 만들 때 정해지고 찾는 중엔 바꿀 수 없어요」가
**실기에서 화면에 나오지 않았다.** 그때는 실기 검증을 못 해 dp 계산으로만 판단했는데, 계산이 틀렸다:
멤버가 2명이 되자 상세 영역이 넘쳐 「탐색 반경」 줄과 안내 문구가 「그만 찾기」 뒤로 밀렸다.

두 가지를 고쳤다:
1. `mapRevealHeight` 216 → **160dp** (16 과 같은 값). 펼침은 정보를 보는 상태이고, 지도는 접힘이 맡는다.
2. 안내 문구를 조건 카드 **아래 → 위**로 옮겼다. 멤버가 늘면 상세 영역은 어차피 스크롤되는데,
   아래에 두면 이 설명이 **가장 먼저** 화면 밖으로 나가 「수정 버튼이 왜 없지」에 답할 기회를 잃는다.

재확인(2명 상태): 안내 문구 + 조건 4줄(출발지·도착지·매칭 조건·탐색 반경) 모두 보임 — `SHOT_21_after_fix.png`.

## 5. 확인 필요 · 발견

1. **`git checkout` 으로 파일을 되돌렸다.** `JoinConfirmScreen.kt` 편집 스크립트가 중간에 실패한 뒤
   `git checkout <file>` 을 실행했다. 다행히 그 파일은 HEAD 와 동일한 상태였고(리더가 14:45~14:59 에
   이전 작업을 커밋해 둔 뒤였다) **잃은 변경은 없다**(`git diff HEAD` 로 확인). 하지만 워킹트리가
   깨끗한지 확인하지 않고 checkout 한 것은 잘못이었다 — 앞으로는 하지 않는다.
2. **「진행 중 탑승」 복귀 경로는 있으나 목적지가 데모 화면이다.** 51·54·55 에서 「진행 중인 방으로
   돌아갈 길이 없다」고 적었는데, 합승 탭 상단에 「진행 중 탑승 · 보기」 배너가 **있었다.**
   다만 눌러보니 **34 「내 탑승」**으로 가고, 그 화면은 아직 목업이다(「7월 25일 · 정문→서면역 · 3,200원」
   같은 하드코딩 값). 21 매칭 대기·25 배차로는 돌아가지 못한다. → **34 를 실데이터로 연결하는 작업이
   앞선 리포트들의 「복귀 경로 없음」에 대한 정확한 처방이다.**
3. **16 도착지 확인에는 「도보 2분 · 180m」 데모 문구가 그대로 남아 있다**(`DestinationConfirmModal.kt:135`).
   이번 지시 범위(20·22)가 아니라 손대지 않았다. 같은 종류의 지어낸 값이므로 정리 대상이다 —
   다만 16 은 시트 높이를 이미 조정해 둔 상태라 줄을 빼면 레이아웃을 다시 봐야 한다.
4. **공용 계정 상태**: `smokep1`·`smokep2` 의 테스트 방(21·22·23)은 전부 leave 로 정리했다.
   남아 있는 방 **id=16**(산들마을6단지베르빌아파트 → 서면역, 1/3)은 **내가 만든 것이 아니라
   건드리지 않았다** — 다른 세션이 쓰고 있는 것으로 보인다.
   에뮬레이터의 승객 앱은 `smokep2` 로 로그인된 상태로 종료했다.
5. 미검증: 폴리라인이 없는 방(마커 2개만), 좌표가 없는 방(자리표시자), 회전, 핀치 줌.
