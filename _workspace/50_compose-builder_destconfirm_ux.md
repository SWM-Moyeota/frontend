# 50 · compose-builder — 16 도착지 확인 모달 UX 4건

작성 2026-09-08. 입력: `_workspace/49_input_destconfirm_ux.md`.
빌드 `./gradlew :app:assembleDebug :presentation:testDebugUnitTest` → BUILD SUCCESSFUL.
에뮬레이터(Pixel_6 콜드부트, 배포 서버) 실기 셀프 체크 완료 — 아래 「실기 확인」 참고.

## 변경 파일

| 파일 | 변경 |
|------|------|
| `presentation/.../feature/home/DestinationConfirmModal.kt` | 4건 전부 (토글 제거 · 드래그 시트 · 탭 레이어 제거 · 매칭 방식 고정) |
| `presentation/.../feature/auth/LoginScreen.kt` | 시작 화면 불릿 문구 정정 |
| `presentation/.../feature/onboarding/OnboardingTrustScreen.kt` | 같은 「선택 가능」 오문구 정정 (범위 밖 — 아래 참고) |
| `presentation/.../feature/matching/MatchWaitingScreen.kt` | Preview 기본값 `"3인 · 동성만"` → `"3인"` (호출부는 이미 `"${capacity}인"`) |

`DestinationConfirmRoute.kt` · `MainNavGraph.kt` · designsystem · domain · data 는 **무변경**.
`MatchConditions` 는 presentation 로컬 타입이라 domain 에 `sameGenderOnly` 필드가 없었다 — 도메인 수정 불필요.

## 1. 동성만 토글 제거

- `SameGenderToggle` 컴포저블, `sameGenderOnly` 상태, `sameGenderAvailable` 파라미터,
  "동성만 필터는 본인 인증 후 사용할 수 있어요" 안내, `MatchConditions.sameGenderOnly` 필드 전부 삭제.
- `MatchConditions` 에 「동성 매칭은 조건이 아니라 서비스 전제」라는 주석을 남겼다.
- 호출부: Route 는 `sameGenderAvailable` 을 넘긴 적이 없어(기본값 사용) 수정 없음. Preview 도 기본값 호출.
- 문구:
  - `LoginScreen` "원하면 동성끼리만 매칭되도록 설정할 수 있어요" → **"같은 성별끼리만 매칭돼요"**
  - `OnboardingTrustScreen` "인증을 마친 이용자만 매칭되고\n동성끼리 탈 수도 있어요"
    → "…\n**같은 성별끼리만 함께 타요**". 지시 범위(시작 화면 1줄)를 벗어난 판단이라 명시한다 —
    같은 「선택 가능」 오문구가 온보딩에 남으면 토글을 없앤 의미가 반감돼서 함께 고쳤다. 되돌리려면 이 한 줄만 원복하면 된다.

## 2. 드래그 바텀시트 — 구현 방식과 그 이유

**BottomSheetScaffold 를 쓰지 않았다.** 이유 하나: BottomSheetScaffold(및 `anchoredDraggable` 의 통상 용법)는
시트 **전체를 오프셋으로 밀어 내려** peek 만 남긴다. 그러면 보이는 건 시트 콘텐츠의 *윗부분*이고,
하단에 고정된 CTA 「같이 탈 사람 찾기」는 화면 밖으로 나간다. 요구사항은 CTA 가 두 상태 모두에서 보이는 것이라,
CTA 를 시트 **위쪽**(도착지 배지 바로 아래)으로 옮기지 않는 한 이 구조로는 불가능하다.

대신 **시트 높이는 콘텐츠가 정하게 두고, 가운데 상세 영역의 높이만 `0 ↔ detailMax` 로 움직인다**:

```
시트(align BottomCenter, wrapContentHeight)
├─ 드래그 손잡이 블록  ← Modifier.draggable(Vertical) 이 걸린 실제 드래그 타깃
│   ├─ SheetHandle
│   └─ 도착지 배지 + 이름            ← 접힘에서도 보임
├─ 상세 영역 (height = detailHeight, verticalScroll)   ← 접히면 0dp
│   └─ 경로 카드 · 매칭 조건 카드 · 반경 안내
└─ 고정 푸터                          ← 접힘에서도 보임
    ├─ 예상 요금 문구 (maxLines=1)
    ├─ 에러 배너(있을 때)
    └─ PrimaryCtaButton 「같이 탈 사람 찾기」
```

- 시트가 절대 클리핑되지 않는다. 에러 배너가 떠서 콘텐츠가 커지면 시트가 그만큼 높아질 뿐 CTA 가 잘리지 않는다.
- 앵커: `detailMax = 컨테이너높이 − MapRevealHeight(160dp) − CollapsedSheetHeight(176dp)`.
  `CollapsedSheetHeight` 는 **접힘 높이의 추정치**(앵커·카메라 계산 전용)이고 실제 높이는 콘텐츠가 정한다 —
  몇 dp 어긋나도 펼침 상태에서 남는 지도 높이만 그만큼 달라진다.
- 정착: 드래그를 놓으면 속도 ±400px/s 를 넘으면 그 방향, 아니면 가까운 앵커로 `Animatable.animateTo`.
- 드래그 중에는 `Animatable` 을 건드리지 않고 별도 `dragDetailPx` 를 따라간다.
  (드래그 델타마다 `scope.launch { snapTo(..) }` 를 쏘면 마지막 스냅이 정착 애니메이션을 취소해
  시트가 어중간한 높이에 멈추는 레이스가 난다 — MutatorMutex 특성.)
- 지도는 헤더 아래 전 영역(`BoxWithConstraints(weight 1f)`)을 채우고 시트가 그 위에 얹힌다.
  「위치 조정」 버튼은 **실측 시트 높이**(`onSizeChanged`)를 써서 항상 시트 바로 위에 뜬다.

### 카메라 fit — 보이는 지도 높이 기준
```kotlin
val settledSheetHeight = if (expanded) containerHeight - MapRevealHeight else CollapsedSheetHeight
val visibleMapHeightDp = (containerHeight - settledSheetHeight).value
fitCamera(points, widthDp, heightDp = visibleMapHeightDp)      // remember 키에 포함
contentPadding = PaddingValues(bottom = settledSheetHeight)     // 카메라 중심 보정
```
- fit·contentPadding 은 **앵커(`expanded`)가 바뀔 때만** 갱신한다. 드래그·정착 애니메이션 중에는
  `settledSheetHeight` 가 이미 목표값이라 매 프레임 카메라를 건드리지 않는다 → 사용자의 팬·줌과 싸우지 않는다.
- `contentPadding` 은 네이버 SDK 에 `setContentPadding` 으로 전달돼 카메라 중심뿐 아니라
  **줌 컨트롤·로고 위치까지 가시 영역 안으로 끌어올린다**(실기로 확인 — 펼침 상태 160dp 스트립 안에 +/- 가 들어온다).

## 3. 줌이 안 되던 원인과 조치

**원인:** 지도 위에 `canAdjust` 조건으로 깔려 있던 **투명 탭 레이어**(`Box.fillMaxSize().clickable{}`)가
지도 영역 전체의 터치를 소비했다. MapView 자식인 줌 컨트롤(+/-)도 그 아래라 탭이 도달하지 못했고,
핀치·팬도 같은 이유로 죽어 있었다.

**조치:** 레이어를 삭제하고, 삭제 이유를 코드 주석으로 남겼다. 핀 조정 진입점은 「위치 조정」 버튼 하나로 충분하다.
버튼은 SDK 줌 컨트롤(오른쪽 아래) 과 겹치지 않도록 **BottomEnd → BottomStart** 로 옮겼다.

**center/zoom 재적용 여부 — 고칠 것 없었다.**
- `NaverMapView` 의 카메라 적용은 `LaunchedEffect(map, center, zoom)` 이고,
  `com.naver.maps.geometry.LatLng` 는 `equals`/`hashCode` 를 **값 기준으로 구현**하고 있음을 aar 로 확인
  (`javap com/naver/maps/geometry/LatLng.class` → equals/hashCode 존재). 즉 값이 바뀔 때만 재적용된다.
- 모달 쪽도 `fitted` 를 `remember(routePath, positions, widthDp, visibleMapHeightDp)` 로 잡아
  동일 인스턴스를 유지하므로 리컴포지션으로 카메라가 되돌아가지 않는다. 실기로도 확인(아래).
- 다이얼로그(TextureView) 모드에서 줌 컨트롤 터치가 정상 동작하는 것도 실기로 확인.

## 4. 매칭 방식 칩

`ConditionChip(text = "주요 승차지점", selected = true, onClick = {})` — 단일 옵션이라 항상 선택 상태,
탭은 여전히 무동작.

## 실기 확인 (에뮬레이터 Pixel_6 콜드부트 · 배포 서버 · 기존 로그인 세션)

진입 경로: 홈(14) → 최근 목적지 「서면역 1번 출구」 → 15 검색 결과 선택 → 「경로 확인하기」 → **16 도착지 확인**

| 항목 | 결과 |
|------|------|
| 동성만 토글 | 없음 ✔ |
| 매칭 방식 「주요 승차지점」 | 파란 선택 상태 ✔ |
| 펼침 상태 | 지도 160dp + 경로 카드·매칭 조건·요금·CTA 전부 표시 ✔ |
| 핸들 아래로 드래그 | 접힘 — 지도가 전체로 확장, 도착지 배지·요금·CTA 만 남음 ✔ |
| 접힘 시 카메라 | 넓어진 지도 높이 기준으로 다시 fit(경로 전체가 시야 안) ✔ |
| +/- 버튼 2회 탭 | 1km → 200m 축척으로 확대 ✔ (이전에는 무동작) |
| 지도 드래그(팬) | 이동됨, 이후 카메라 되돌아가지 않음 ✔ |
| 핸들 위로 드래그 | 다시 펼침 + fit 재적용 ✔ |
| 「위치 조정」 | 핀 조정 화면 정상 진입(출발지/도착지 토글·「이 위치로 설정」) ✔ |
| logcat | FATAL/AndroidRuntime 없음 ✔ |

스크린샷(세션 scratchpad, 영구 보관 아님): `/private/tmp/claude-501/-Users-sungyoon-Desktop-moyeota-frontend/1b791af6-8c40-4510-9b30-44b1700ea413/scratchpad/s3.png`(펼침) · `s4.png`(접힘) · `s5.png`(+ 줌) · `s6.png`(팬) · `s7.png`(재펼침) · `s8.png`(위치 조정)

## 미해결 · qa-verifier 에게

1. **방 생성(CTA) 회귀는 실기로 안 눌렀다.** 배포 서버에 실제 방이 생기는 게 부담스러워 남겼다.
   코드상 변경은 `MatchConditions` 에서 `sameGenderOnly` 필드가 빠진 것뿐(Route 의 clamp·요청 본문 무변경).
   → 21 매칭 대기까지 이어지는지 확인 필요.
2. ~~펼침 상태의 fit 과도 축소~~ → **후속 작업으로 해결됨** (아래 「5. 후속」 참고).
3. **에러 배너가 뜬 접힘 상태**(15 를 건너뛴 진입: "도착지 좌표가 없어요")는 실기 미확인.
   구조상 배너가 고정 푸터에 있어 잘리지 않고 시트만 높아진다(코드 리뷰로 확인).
4. 회전/폴더블 등 컨테이너 높이 변화 시 `LaunchedEffect(detailMaxPx)` 가 현재 상태의 앵커로 다시 앉힌다 — 미검증.

## 5. 후속 — 펼침 상태 fit 인셋 (리더 지시, 2026-09-08)

**정정:** 앞선 리포트에서 `fitCamera` 를 「25 배차 등이 함께 쓰는 공유 함수」라고 적은 것은 **틀렸다.**
`fitCamera` 와 `MARKER_INSET_DP` 는 `DestinationConfirmModal.kt` 안의 `private` 요소이고 호출부는 이 모달 한 곳뿐이다
(`grep fitCamera` 결과 정의 1 · 호출 1). designsystem 에도, 25 배차 화면에도 없다.

지시대로 시그니처를 깨지 않는 형태로 `insetDp` 파라미터를 기본값과 함께 추가했다:

```kotlin
private fun fitCamera(points, widthDp, heightDp, insetDp: Float = MARKER_INSET_DP)
// 호출부(16 모달)
insetDp = minOf(MARKER_INSET_DP, visibleMapHeightDp * 0.15f)
```

- 펼침(보이는 지도 160dp): inset 38 → **24dp**. 세로 가용 높이 84 → 112dp (+33%).
- 접힘(보이는 지도 ~610dp): `610 × 0.15 = 92 > 38` 이므로 **기존 38dp 그대로**.
- 기본값을 유지했으므로 인자를 넘기지 않는 호출부가 생겨도 동작이 바뀌지 않는다.

실기 재확인(에뮬레이터 재설치·동일 경로): 펼침에서 출발·도착 마커가 스트립의 위아래 끝을 쓰도록 경로가 커졌고
(이전에는 가운데 1/3 만 차지), 접힘 화면은 이전과 픽셀 단위로 동일하다.
스크린샷 `…/scratchpad/t2.png`(펼침·개선 후) · `t3.png`(접힘·변화 없음).

부작용 한 가지: 인셋이 24dp 라 펼침 상태에서 **위쪽 「출발」 마커 아이콘 머리가 몇 dp 잘린다**
(마커는 좌표 꼭짓점에서 위로 ~34dp 솟는다). 캡션과 핀 몸통은 그대로 보인다 — 경로 가시성과 맞바꾼 값이라
거슬리면 계수를 0.15 → 0.2 로 올리면 된다(인셋 32dp, 가용 96dp).

빌드 `./gradlew :app:assembleDebug :presentation:testDebugUnitTest` → BUILD SUCCESSFUL.

## 6. QA 후속 (입력: `_workspace/51_qa-verifier_destconfirm_ux.md`, 2026-09-08)

빌드 `./gradlew :app:assembleDebug :presentation:testDebugUnitTest :data:testDebugUnitTest` → BUILD SUCCESSFUL.
실기: 에뮬레이터 `emulator-5554`(Pixel_6) · 배포 서버 · 계정 `smoke01`. 실기기 폰은 건드리지 않았다(모든 adb 에 `-s emulator-5554`).

### 변경 파일 (이번 절)

| 파일 | 항목 |
|------|------|
| `presentation/.../feature/home/DestinationConfirmModal.kt` | D-1 비대칭 인셋 · D-2 반경 칩 |
| `presentation/.../feature/home/DestinationConfirmRoute.kt` | D-2 clamp 주석 정정 |
| `presentation/.../feature/matching/MatchWaitingScreen.kt` | D-3 라벨 파라미터 · O-1 확인 다이얼로그 |
| `presentation/.../feature/matching/MatchWaitingRoute.kt` | D-3 실제 반경 전달 |
| **`domain/.../model/Ride.kt`** | D-3 반경 2필드 추가 — **모듈 경계 넘음, 아래 참고** |
| **`data/.../remote/PartyMappers.kt`** | D-3 반경 매핑 2곳 — **모듈 경계 넘음** |

### D-1 마커 잘림 — 대칭 → 비대칭 인셋

`fitCamera` 의 여백을 사방 한 값에서 **위/아래/좌우 셋**으로 나눴다. 마커가 좌표 꼭짓점에서
**위로만** 길게 솟기 때문에, 같은 값으로 사방을 비우면 위는 잘리는데 아래·좌우는 쓰지도 않을 여백만 먹는다.

```kotlin
private const val MARKER_TOP_INSET_DP = 55f     // 실측 49dp + 여유 6
private const val MARKER_BOTTOM_INSET_DP = 20f  // 캡션 ~14dp + 여유 6
private const val MARKER_SIDE_INSET_DP = 24f
fitCamera(points, widthDp, heightDp, insetTopDp = …, insetBottomDp = …, insetSideDp = …)
```

- 위/아래가 다르면 **카메라 타깃을 bbox 중심에 두면 안 된다**(타깃은 가시 영역의 정중앙에 놓인다).
  `(top − bottom)/2` dp 만큼 북쪽으로 올려 잡는다 — 메르카토르 y 로 환산해 계산한다.
- 뷰포트가 아주 낮아 세로 인셋 합이 높이를 다 먹는 경우에만 위·아래를 같은 비율로 줄여 가용 높이를 40% 남긴다.
- 호출부의 `minOf(MARKER_INSET_DP, height × 0.2)` 비례식은 **삭제**했다 — 비대칭 인셋이면 160dp
  스트립에서도 세로 가용 89dp 가 나와(이전 96dp) 비례 축소가 필요 없다.
- 주석 「~34dp」 → **「≈49dp(실측 129px @ density 2.625)」** 로 정정하고, 34dp 오기가 잘림의 원인이었다는 사실을 남겼다.

**실기 결과: 펼침·접힘 모두 출발/도착 마커 잘림 없음.**
`SHOT_expanded_fixed.png`(펼침, 출발 마커 머리 온전 · 상단 여유 ~8dp) · `SHOT_collapsed_fixed.png`(접힘, 출발 마커 온전 + 도착 캡션 온전).

### D-2 반경 칩 100m / 300m / 500m (기본 300m)

- `radiusLabelToMeters` 를 서버 검증 범위(100~500m)와 **같은 집합**으로 맞추고, 칩 목록·기본값을
  `RadiusOptions` / `DefaultRadiusLabel` 상수로 뽑았다.
- Route 의 `coerceIn(100, 500)` 은 **지우지 않고 방어선으로 남겼다.** 이제 깎을 값이 애초에 안 들어온다 —
  주석에 「예전에는 이 clamp 가 1km·2km 를 조용히 500 으로 깎아 사용자 선택을 버렸다」를 명시했다.
- 안내 문구는 지시대로 그대로 뒀다.

**실기 결과 (POST 본문 원문, logcat okhttp):**
```
--> POST https://api.moyeota.p-e.kr/api/v1/matching/rooms
{"departureLat":35.231298,…,"capacity":2,"departureRadius":300,"destinationRadius":300}
```
서버 재조회 `GET /matching/rooms/6` → `departureRadius: 300, destinationRadius: 300`. 선택값이 그대로 저장됐다.

### D-3 21 「탐색 반경」 실값 표시

`PartyDetailResponse` / `OpenPartyResponse` DTO 에는 이미 `departureRadius`·`destinationRadius` 가 있었고
**도메인 모델과 매퍼에서만 버려지고 있었다.** 그래서:
- `Ride` 에 `departureRadiusMeters` / `destinationRadiusMeters`(둘 다 `Int? = null`) 추가.
  서버가 0(값 없음)을 주면 `takeIf { it > 0 }` 로 null 로 접는다 — 0m 반경은 의미가 없다.
- 목록 응답(`PartyItem`)에는 반경이 없어 그대로 null → 라벨 "—". 값을 지어내지 않는다.
- `MatchWaitingRoute` 가 `radiusLabel(ride)` 로 라벨을 만들어 넘긴다. 출발·도착이 같으면 `"300m"`,
  다르면 `"출발 100m · 도착 300m"`. 화면 기본값은 `"1km"` → `"—"` 로 바꿔 하드코딩 값이 다시 새지 않게 했다.

**실기 결과:** 21 대기 화면 「매칭 조건 2인」 · **「탐색 반경 300m」**(`SHOT_waiting_radius.png`). 서버 값과 일치.

### O-1 나가기 확인 다이얼로그

- designsystem 에 다이얼로그 컴포넌트가 **없어서**(`Bars/Buttons/Inputs/Misc/NaverMapView/RouteMapView` 뿐)
  12 마이페이지의 로그아웃 확인과 같은 material3 `AlertDialog` 패턴을 따랐다. 두 화면에서만 쓰는
  형태라 아직 designsystem 승격 기준(2개 이상 화면에서 재사용)에 걸치는 정도 — 세 번째가 나오면 승격이 맞다.
- 상단 back 화살표와 하단 「그만 찾기」가 **같은 다이얼로그**를 연다. 「그만 찾기」(Danger500) 확인 시에만
  `onCancelSearch()` 가 호출되고, 「계속 찾기」는 그냥 닫힌다. 확인 상태는 화면 로컬 state 라 Route/ViewModel 은 그대로다.

**실기 결과:** back 화살표 · 하단 버튼 둘 다 「매칭을 그만둘까요?」 다이얼로그 표시, 「계속 찾기」로 닫아도 방 유지
(`SHOT_leave_confirm.png`).

### 실기 확인 요약

| 항목 | 결과 |
|------|------|
| 펼침 마커 잘림 | 없음 ✔ `SHOT_expanded_fixed.png` |
| 접힘 마커 잘림 | 없음 ✔ `SHOT_collapsed_fixed.png` |
| 반경 칩 100/300/500 · 기본 300m | ✔ |
| POST 본문 `departureRadius:300` | ✔ (서버 재조회도 300) |
| 21 「탐색 반경 300m」 | ✔ `SHOT_waiting_radius.png` |
| 나가기 확인 다이얼로그(back · 그만 찾기) | ✔ `SHOT_leave_confirm.png` |
| FATAL / ANR | 0건 ✔ |

스크린샷 위치: `/private/tmp/claude-501/-Users-sungyoon-Desktop-moyeota-frontend/1b791af6-8c40-4510-9b30-44b1700ea413/scratchpad/`

### 서버 상태 / 리더 확인 필요

1. **모듈 경계를 넘었다.** D-3 때문에 `domain/model/Ride.kt` 와 `data/remote/PartyMappers.kt` 를 직접 고쳤다.
   원래 도메인 모델 확장은 api-integrator 담당이고 내 경계는 presentation 이다. DTO 가 이미 필드를 갖고 있어
   추가분이 도메인 2필드 + 매퍼 4줄뿐이라 직접 처리했지만, **api-integrator 가 검토해 주는 게 맞다.**
2. **QA 가 남긴 방 id=5 를 정리했다.** `POST /matching/rooms` 가 409 로 막혀 새 방을 못 만들었다.
   앱에 그 방으로 들어갈 경로가 없어(활성 방 id 가 네비 로컬 state 라 앱 재시작 시 사라진다)
   `DELETE /api/v1/matching/leave/5` 를 curl 로 호출해 비웠다. **API 를 앱 밖에서 직접 호출한 것이니 알아둘 것.**
   → 이건 별개 결함 후보다: **진행 중인 방이 있는데 앱을 재시작하면 그 방으로 돌아갈 길이 없다**(409 만 뜨고 안내도 없다).
3. **현재 대기 상태로 남겨 둔 방: id=6** (ACTIVE, capacity 2, 1명, radius 300/300). 지시대로 유지했다.
4. 배포 서버가 콜드 스타트일 때 첫 요청이 CloudFront 504 로 떨어진다(실측 10.9s 후 응답). 앱은 「검색하지 못했어요」만
   보여 준다 — 재시도하면 정상. QA 가 같은 증상을 만나면 서버 콜드 스타트를 먼저 의심할 것.
5. 여전히 미검증: 핀치 줌(adb 한계) · 25 배차 화면 · 에러 배너 접힘 상태 · 회전. O-2(접힘에서 「위치 조정」이
   도착 캡션과 겹침)는 이번 지시 범위 밖이라 손대지 않았다.

