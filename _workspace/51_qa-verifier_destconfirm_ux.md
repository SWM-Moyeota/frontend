# 51 · qa-verifier — 16 도착지 확인 모달 UX 4건 검증

작성 2026-09-08. 입력: `_workspace/49_input_destconfirm_ux.md`, `_workspace/50_compose-builder_destconfirm_ux.md`(후속 절 포함, 인셋 계수 0.2).
환경: 에뮬레이터 Pixel_6(`emulator-5554`, 1080x2400, density 420) · 배포 서버 `https://api.moyeota.p-e.kr` · 계정 `smoke01`(닉네임 스모크).
연결된 실기기(`RFKL30AVNLL`)는 건드리지 않았다 — 모든 adb 명령에 `-s emulator-5554` 를 붙였다.

**결론: 4건 중 3건 통과, 1건 실패(④ 마커 클리핑).** 크래시·ANR 0.

---

## A. 정적 검증

| 항목 | 결과 | 근거 |
|------|------|------|
| `:app:assembleDebug :presentation:testDebugUnitTest` | ✅ PASS | BUILD SUCCESSFUL. 유닛 테스트 17건(12+5) 전부 통과, failures=0 errors=0 |
| `grep -rn '동성만\|sameGender' presentation/src/main` | ✅ PASS | 1건만 히트 — `DestinationConfirmModal.kt:99` **주석**. 실행 경로 0 |
| 시작 화면 문구 | ✅ PASS | `LoginScreen.kt:133` = `"같은 성별끼리만 매칭돼요"` |
| 온보딩 문구 | ✅ PASS | `OnboardingTrustScreen.kt:106` = `"인증을 마친 이용자만 매칭되고\n같은 성별끼리만 함께 타요"` |

---

## B. 16 실기 검증

진입 경로: 홈(14) → 최근 목적지 「서면역 1번 출구」 → 15 검색 결과 「서면역 부산1호선 1번출구」 → 「경로 확인하기」 → 16.

| # | 항목 | 결과 | 근거 |
|---|------|------|------|
| ① | 동성만 토글 없음 | ✅ PASS | 펼침 상태 「매칭 조건」 카드에 인원·출발지 반경·도착지 반경·매칭 방식 4행만. `SHOT_expanded.png` |
| ② | 매칭 방식 「주요 승차지점」 기본 선택 | ✅ PASS | 파란 선택 칩. `DestinationConfirmModal.kt:553-555` `selected = true` |
| ③ | 드래그 접힘 → 지도 확장, 경로 시야 안, 재펼침 | ✅ PASS | 접힘 시 지도 전체 확장 + 경로 전체 재fit(`SHOT_collapsed.png`). 핸들(y≈1908) 위로 드래그 시 재펼침 + fit 재적용 |
| ④ | 펼침 상태 마커 머리 잘림 없음 | ❌ **FAIL** | **출발 마커 머리가 잘린다.** 아래 D-1 |
| ⑤ | +/- 축척 변화, 팬 후 카메라 유지 | ✅ PASS | + 2회 탭으로 1km → 200m(`SHOT_zoom_before/after.png`). 팬 후 4초 뒤 스크린샷 md5 동일(`1ae545ee84ca`) → 되돌아감 없음 |
| ⑤-핀치 | 핀치 줌 | ⚠️ **미검증** | adb `input` 은 단일 터치만 지원. `sendevent` 로 virtio 멀티터치 재현 시도했으나 이벤트가 앱에 도달하지 않아 실패. 팬·+/- 가 모두 동작하므로 **투명 탭 레이어 제거는 확실히 검증됨**(터치가 지도에 도달) |
| ⑥ | 「위치 조정」 진입·복귀 | ✅ PASS | 출발지/도착지 토글 + 「이 위치로 설정」 정상. 좌표 `35.156853, 129.058949`(서면역) — **lat/lng 순서 정상, 좌표 전치 없음**. 뒤로가기 시 16 펼침 상태·fit 그대로 복귀 |
| ⑦ | CTA 실제 탭 → 방 생성 → 21 대기 | ✅ PASS | 2인 선택 후 탭 → `POST /api/v1/matching/rooms` → **room id=5** 생성, `status:"ACTIVE"`, `capacity:2`, `currentMembers:1`. 21 「같이 탈 사람 찾는 중」 진입, 「지금 같은 방향 1명 · 목표 2명」, 「매칭 조건 2인」(동성만 문구 없음). **대기 상태로 남겨 둠** |

### 21 대기 화면 상태 (지시대로 유지)
- 배포 DB 에 room **id=5** 가 ACTIVE / 1명 / 정원 2 로 남아 있다.
- 정리하려면 앱에서 「그만 찾기」를 누르면 된다. (21 상단 back 화살표도 `MatchWaitingRoute.kt:160` 에서 `viewModel::leaveParty` 에 연결돼 있어 **확인 없이 방을 나간다** — 아래 관찰 O-1)

---

## C. 회귀

| 항목 | 결과 | 근거 |
|------|------|------|
| 홈(14) 지도 | ✅ PASS | 실지도 렌더, 줌 컨트롤·축척 정상 (`q01_launch.png`) |
| 15 검색 결과 화면 | ✅ PASS | 최근 목적지 → 검색 결과 → 「경로 확인하기」 정상 |
| 25 배차 화면 | ⚠️ **미검증** | 정원 2인이 안 차 배차 단계로 진입 불가(기사·상대 승객 없음). 지시대로 대기까지만 |
| 크래시 / ANR | ✅ PASS | `logcat -b crash,main` 에 FATAL·AndroidRuntime·ANR **0건** |

---

## 결함

### D-1 (실패, ④) 경로 끝점 마커의 머리가 지도 위쪽에서 잘린다 — 펼침·접힘 **둘 다**

- **파일:** `presentation/src/main/kotlin/com/moyeota/presentation/feature/home/DestinationConfirmModal.kt:690` (`MARKER_INSET_DP = 38f`) 및 `:283` (`insetDp = minOf(MARKER_INSET_DP, visibleMapHeightDp * 0.2f)`)
- **재현:** 홈 → 최근 목적지 「서면역 1번 출구」 → 검색 결과 선택 → 「경로 확인하기」 → 16 펼침 상태에서 지도 스트립 위쪽의 파란 「출발」 마커를 본다.
- **증상:** 마커 머리(둥근 부분)가 지도 상단 경계에서 수평으로 잘린다. 펼침에서 특히 심해 흰 삼각형 위쪽 blue cap 이 사라지고 두 갈래로 보인다. 접힘 상태에서도 ~7dp 잘린다.
- **스크린샷:** `SHOT_defect_marker_clip.png`(펼침·확대), `SHOT_collapsed.png`(접힘)
- **원인 (실측):**
  - 코드 주석 `:688-689` 은 「마커 아이콘은 좌표(꼭짓점)에서 위로 **~34dp** 솟고」 라고 가정하지만,
  - 잘리지 않은 도착 마커를 스크린샷에서 실측하면 꼭짓점 위로 **129px / density 2.625 = 약 49dp** 다. (`MarkerIcons.BLACK`, `RouteMapView.kt:162`)
  - 따라서 인셋이 49dp 미만이면 최상단 마커는 항상 잘린다.
    - 펼침: `min(38, 160×0.2) = **32dp**` → 약 17dp 잘림 (실측: 마커 꼭짓점이 지도 상단에서 84px=32dp)
    - 접힘: `min(38, 610×0.2) = **38dp**` → 약 11dp 잘림 (실측: 103px=39dp)
  - 즉 0.15→0.2 조정은 펼침의 잘림을 **줄였을 뿐 없애지 못했고**, 접힘의 잘림은 `MARKER_INSET_DP=38` 자체가 원인이라 이번 조정과 무관하게 원래 있었다.
- **compose-builder 에게 — 수정 제안:** 계수를 더 올리는 것(0.2→0.31)은 펼침 160dp 스트립에서 사방 49dp × 2 = 98dp 를 먹어 경로가 62dp 안으로 짓눌린다. **대칭 인셋으로는 해결이 안 된다.** 위쪽만 크게 잡는 비대칭 인셋이 맞다:
  - `fitCamera` 를 `insetTopDp` / `insetOtherDp` 로 나누고, 위쪽만 마커 실측 높이(≈50dp)를 확보,
  - 또는 네이버 SDK 의 `CameraUpdate.fitBounds(bounds, top, left, bottom, right)` 로 비대칭 패딩을 넘긴다(현재는 center+zoom 을 직접 계산 중이라 구조 변경 필요).
  - 어느 쪽이든 `:688-689` 주석의 「~34dp」 를 실측치 **~49dp** 로 정정해야 다음 사람이 같은 함정에 빠지지 않는다.

### D-2 (범위 밖·기존 결함) 반경 1km·2km 선택이 서버에 500m 로 전송된다

- **파일:** `presentation/src/main/kotlin/com/moyeota/presentation/feature/home/DestinationConfirmRoute.kt:69-70`
  ```kotlin
  // 서버 검증이 100~500m 라 UI 의 1km·2km 선택은 500m 로 clamp 한다.
  departureRadius = conditions.departureRadiusMeters.coerceIn(100, 500),
  destinationRadius = conditions.destinationRadiusMeters.coerceIn(100, 500),
  ```
- **재현:** 16 에서 출발지·도착지 반경을 「1km」(기본값) 로 둔 채 CTA 탭.
- **실제 전송 본문 (logcat okhttp, 15:22:30):**
  `{"…","capacity":2,"departureRadius":500,"destinationRadius":500}`
- **문제:** 16 UI 는 500m / 1km / 2km 칩을 제공하고 **기본값이 1km** 인데, 1km·2km 는 전부 조용히 500m 로 깎여 나간다. 사용자 선택이 아무 피드백 없이 버려진다. clamp 자체는 서버 400 을 막으려는 의도적 방어지만, **UI 가 서버 계약(100~500m)보다 넓은 선택지를 노출하는 것이 근본 불일치**다.
- **제안:** 둘 중 하나. (a) 16 칩을 서버 계약에 맞춰 `100m / 300m / 500m` 로 바꾼다. (b) 백엔드에 반경 상한 상향을 요청한다. 지금처럼 두면 어느 쪽이든 사용자가 속는다.
- 이번 4건 작업과 **무관한 기존 결함**이다(변경 파일 아님). 수정 여부는 리더 판단.

### D-3 (범위 밖·기존 결함) 21 대기 화면의 「탐색 반경」이 항상 "1km" 하드코딩

- **파일:** `presentation/src/main/kotlin/com/moyeota/presentation/feature/matching/MatchWaitingScreen.kt:103` (`radiusLabel: String = "1km"`) — 호출부 `MatchWaitingRoute.kt:155-162` 가 `radiusLabel` 을 **넘기지 않아** 기본값이 그대로 표시된다.
- **재현:** 방 생성 후 21 진입 → 「탐색 반경 1km」 표시. 실제 서버 방은 `departureRadius:500` (위 로그).
- **제안:** `Ride` 도메인 모델에 반경이 없으면 api-integrator 가 먼저 필드를 태워야 한다. D-2 를 (a) 로 고치면 이 라벨도 같이 정리된다.

---

## 관찰 (결함 아님)

- **O-1** 21 상단 back 화살표가 `viewModel::leaveParty` 에 연결돼 있어(`MatchWaitingRoute.kt:153-162`) 확인 다이얼로그 없이 방을 나간다. 「그만 찾기」 버튼과 동일 동작. 의도라면 그대로 두되, 뒤로가기로 방이 날아가는 건 사용자가 놀랄 수 있다.
- **O-2** 접힘 상태에서 「위치 조정」 버튼(BottomStart)이 도착 마커 캡션과 겹치는 경우가 있다(`SHOT_collapsed.png` 좌하단). 가독성 경미.
- **O-3** compose-builder 리포트의 미해결 3(에러 배너 접힘 상태)·4(회전) 는 이번에도 미검증이다. 15 를 건너뛴 진입 경로를 만들 방법이 없었다.

---

## 미검증 항목 요약

| 항목 | 사유 |
|------|------|
| 핀치 줌 | adb 단일 터치 한계. sendevent 멀티터치 재현 실패. 팬·+/- 동작으로 터치 도달은 검증됨 |
| 25 배차 화면 | 정원 미충족(상대 승객·기사 없음)으로 진입 불가 |
| 에러 배너 접힘 상태 / 회전 | 재현 경로 없음 |

---

## 스크린샷 (세션 scratchpad — 영구 보관 아님)

`/private/tmp/claude-501/-Users-sungyoon-Desktop-moyeota-frontend/1b791af6-8c40-4510-9b30-44b1700ea413/scratchpad/`

| 파일 | 내용 |
|------|------|
| `SHOT_expanded.png` | 16 펼침 (요구 D) |
| `SHOT_collapsed.png` | 16 접힘 (요구 D) |
| `SHOT_zoom_before.png` / `SHOT_zoom_after.png` | 줌 전(1km) / 후(200m) (요구 D) |
| `SHOT_defect_marker_clip.png` | **D-1** 출발 마커 잘림 확대 |
| `q16_adjust.png` | 「위치 조정」 화면 |
| `q20_cta_11s.png` | 21 매칭 대기 (room id=5) |
