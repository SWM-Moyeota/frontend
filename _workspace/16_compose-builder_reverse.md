# 16. 역지오코딩(좌표 → 주소) 화면 연결 — compose-builder

작업일: 2026-09-01 / 선행 산출물: `15_api-integrator_reverse.md`(Repository), `10_…pin_adjust.md`, `14_…route_preview.md`

## 1. 한 일

`PlaceRepository.reverseGeocode` 를 16 도착지 확인의 **두 시점**에 연결해, 좌표만 정확하고 이름은
「현재 위치」·옛 검색명이던 자리를 실주소로 채웠다. **data/·domain/ 무수정** — presentation 에서 부르기만 했다.

1. **핀 조정 확정** — 「이 위치로 설정」 순간, 좌표가 **실제로 바뀐 필드만** 1회 호출
2. **GPS 출발지** — 16 진입 시 출발지 이름이 「현재 위치」면 1회 호출

갱신된 이름은 표시(16 배지·경로 카드·위치 조정 패널)와 `createParty` 요청 바디(`departure`/`destination`)에
함께 반영된다.

**화면 진입 경로**: 14 홈 → 검색창 → 15 목적지 → 「경로 확인하기」 → **16 도착지 확인**(`Routes.DESTINATION_CONFIRM`)
→ 지도 탭 / 「위치 조정」 칩 / 경로 카드 행별 「조정」 → 위치 조정 레이어 → 「이 위치로 설정」

라우트 상수·NavGraph 목적지 변경 **없음**(주입 인자만 1개 추가).

## 2. 변경 파일

### `presentation/.../feature/home/DestinationConfirmRoute.kt`

| 라인 | 변경 |
|---|---|
| 170–232 | **`ReverseGeocodeViewModel` 신설**. `UiState(originAddress, destinationAddress)` — null = 갱신 없음(원래 이름 사용) |
| 191–196 | 캐시 2종: `addresses`(좌표 → 주소, **null 값도 캐시**) · `requested`(대상별 마지막 요청 좌표) |
| 198–222 | `resolve(target, position)` — 같은 좌표면 즉시 return, 캐시 히트면 서버 안 감, 연타는 `jobs[target].cancel()` 로 직전 호출 취소 |
| 210–219 | 실패 처리: `CancellationException` 은 재던지고(취소는 실패가 아님), 그 외 예외는 **조용히 무시 + `requested` 기록 삭제**(다음 확정 때 재시도 가능). 예외는 캐시하지 않는다 |
| 237 | `DestinationConfirmRoute(placeRepository: PlaceRepository, …)` 파라미터 추가 |
| 272–274 | ViewModel 생성 + 상태 구독 |
| 278–283 | 진입 시 GPS 출발지 판별(`origin.name == CurrentLocationName`) → 원 좌표로 1회 호출. 키가 `origin` 이라 핀 조정으로 좌표가 바뀌어도 재실행되지 않는다 |
| 286–287 | 표시 이름 = 받아낸 주소 ?: 원래 이름 |
| 306 | 이름이 이미 주소면 `destinationAddress`(검색 도로명) 칸을 비운다 — 같은 주소를 두 번 적지 않고, 핀을 옮겼으면 그 도로명은 이미 틀린 값이다 |
| 326–332 | **확정 콜백**: `movedFrom(현재 좌표, 확정 좌표)` 이 true 인 필드만 `resolve` |
| 341–342 | `createParty(origin.movedTo(…).renamedTo(…), destination.movedTo(…).renamedTo(…))` |
| 371–372 | `Place.renamedTo(address: String?)` — null 이면 원본 유지, 있으면 `name` 교체 + `roadName` 비움 |
| 375–377 | `movedFrom(before, after)` — 위경도 직접 비교(팬 한 번에 소수점 아래가 바뀐다) |

### `presentation/.../feature/home/DestinationConfirmModal.kt`

| 라인 | 변경 |
|---|---|
| 125–127 | 이름 자리에 실주소가 들어올 수 있다는 계약을 주석으로 명시 |
| 319–331 | 도착지 배지 줄 — `동성만` 토글과 최소 간격(10dp), 이름·주소를 **빈 값 제외 후 " · " 로 합성**(구분자만 남지 않게), 1줄 말줄임 |
| 366–374 | 경로 카드 출발지 행 — `maxLines=1` + `TextOverflow.Ellipsis` + 시각 앞 8dp 간격 |
| 404–411 | 도착지 행도 동일 |

### `presentation/.../feature/home/PinAdjustOverlay.kt`

| 라인 | 변경 |
|---|---|
| 75–76 · 82 | KDoc 정정 — 「이름을 건드리지 않는다」 → **팬 중에만** 안 건드리고, 확정 뒤 호출부가 1회 갱신 |
| 283–291 | 대상 이름 라벨 말줄임(+시각 없는 행이라 간격 정리) |
| 303–304 | 하단 안내 문구 → `지도를 움직여 핀을 맞춰 주세요 · 설정하면 주소가 갱신돼요` (이름이 바뀐다는 사실을 미리 알림. 1줄에 맞춘 길이) |

### `presentation/.../feature/home/DestinationRoute.kt`

| 라인 | 변경 |
|---|---|
| 36–39 | `CurrentLocationName` `private` → `internal`. 16 이 「아직 주소를 모르는 GPS 출발지」를 이 문구로 식별한다 |

### `presentation/.../feature/matching/MatchWaitingScreen.kt`

| 라인 | 변경 |
|---|---|
| 238–248 | `ConditionRow` 값 텍스트 — `Spacer(weight)` → 값에 `weight(1f)` + `TextAlign.End` + 1줄 말줄임. **이번 변경의 하류 파손 수정**: 21 매칭 대기의 출발지 행이 전체 주소를 받자 2줄로 접히며 「출발지」 라벨을 덮었다 |

### `presentation/.../core/MainNavGraph.kt`

| 라인 | 변경 |
|---|---|
| 263–265 | `DestinationConfirmRoute(… placeRepository = placeRepository …)` |

색상 신규 하드코딩 없음(`MoyeotaColor` 외 추가 없음). `data/`·`domain/` 무수정.

## 3. 설계 판단

### 3-1. 「조정된 필드만」 호출 — 검색 장소명을 지키는 유일한 장치
조정 화면은 한쪽만 만져도 **양쪽 좌표를 함께** 돌려준다(10 리포트의 「확정은 양쪽을 한 번에 커밋」).
그대로 두 필드를 역지오코딩하면, 출발지만 옮긴 사용자의 「CGV 서면」이 주소로 덮인다.
그래서 확정 좌표를 **직전 표시 좌표와 비교**해 움직인 쪽만 부른다.
반대로 조정한 쪽은 검색명을 유지하지 않는다 — 핀을 옮긴 순간 그 이름은 더 이상 그 지점이 아니다.

### 3-2. null·예외의 처리를 갈라놓았다
- `null`(404 `ADDRESS_NOT_FOUND` / 빈 응답) = **좌표에 주소가 없다**는 안정된 사실 → 캐시하고 이름은 유지
- 예외(네트워크·400·502) = **일시적 실패** → 캐시하지 않고 `requested` 기록도 지워 다음 확정에서 재시도

둘 다 사용자에게는 아무 것도 알리지 않는다. 이름 갱신은 부가 기능이라 배너를 띄우면 방 만들기 흐름을 막는다.

### 3-3. 낙관적 진행 — 확정 버튼을 잠그지 않는다
`resolve` 는 fire-and-forget 이고 확정은 즉시 16 으로 돌아간다. 응답이 오면(실측 0.5–1.5초) 그때 이름이 바뀐다.
버튼을 잠그면 「부가 정보 때문에 본 흐름이 멈추는」 구조가 되고, 그 사이 사용자가 CTA 를 눌러도
`createParty` 는 `resolvedNames` 의 **그 시점 값**을 쓰므로 최악의 경우 원래 이름으로 나갈 뿐 잘못된 값은 안 나간다.

### 3-4. 중복 호출 차단은 2단
`requested[target]` 로 **같은 좌표 재확정**을 막고, `addresses` 로 **왕복했던 좌표**를 막는다.
진입 이펙트와 확정 콜백이 같은 좌표로 겹쳐 들어와도 서버로 나가는 건 1회다(실측 확인).

### 3-5. 긴 주소는 전부 1줄 말줄임 — 가공 금지
「부산광역시 금정구 부산대학로63번길 2 부산대학교」가 그대로 들어온다. 시/도 접두 제거 같은 가공은
하지 않고(서버 개선 예정), 넘치는 자리는 `maxLines=1` + `TextOverflow.Ellipsis` 로 자른다.
프로젝트 첫 `TextOverflow` 사용이지만 기존 컨벤션(`maxLines=1` + `weight(1f)`)의 자연스러운 연장이다 —
지금까지는 짧은 장소명뿐이라 잘릴 일이 없었을 뿐.

## 4. 검증

### 빌드
```
./gradlew :presentation:compileDebugKotlin :app:assembleDebug --console=plain
BUILD SUCCESSFUL
```

### 에뮬레이터 실기 (emulator-5554 · 서버 localhost:8080 유지 · 실기기 미사용)

| # | 확인 | 결과 | 근거 |
|---|---|---|---|
| b | 16 진입 → GPS 출발지 「현재 위치」가 실주소로 | ✅ `부산광역시 금정구 부산대학로63번길 2 부산대학교`, 호출 **1회** | `rgeo_01_confirm_entry.png` |
| b' | 검색으로 고른 「CGV 서면」은 불변 | ✅ 도착지 배지·경로 카드 모두 그대로 | 위와 동일 |
| a | 핀 조정 확정 → 도착지 이름이 주소로 | ✅ `CGV 서면` → `부산광역시 남구 문현동 1228-1` | `rgeo_04_after_confirm.png` |
| a' | 경로 미리보기 재계산과 공존 | ✅ `31분·12,500원` → `30분·12,200원`, reverse 와 routes 가 **동시에** 나가고 서로 안 막음 | 위 + OkHttp 로그 |
| — | **팬 중 호출 없음** | ✅ 조정 화면에서 2회 팬 → reverse 호출 수 변화 0 | `rgeo_02/03`, `logcat grep -c` |
| — | 출발지만 조정 → 도착지 이름 유지 | ✅ 출발지만 `…금강로 247-6` 로 갱신, `CGV 서면` 그대로. reverse 총 2회(진입+출발지) | `rgeo_07/08` |
| c | 바다에 핀 확정 | ✅ 404 `ADDRESS_NOT_FOUND` → 이름 유지, 에러 배너 없음, **크래시 0** | `rgeo_05_sea_kept.png` |
| d | 방 생성 요청 바디가 갱신된 이름 | ✅ 아래 로그 | `rgeo_06_create_party.png` |
| d' | 21 매칭 대기 표기 | ✅ 서버가 돌려준 주소가 1줄 말줄임으로 렌더(수정 전엔 2줄로 접혀 라벨 침범) | 위와 동일 |

```
--> GET  /api/v1/places/reverse?latitude=35.231301&longitude=129.0838006
<-- 200  {"address":"부산광역시 금정구 부산대학로63번길 2 부산대학교", …}

--> GET  /api/v1/places/reverse?latitude=35.147517008530556&longitude=129.0649209611488
<-- 200  {"address":"부산광역시 남구 문현동 1228-1", …}      # 핀 조정 확정 직후

--> GET  /api/v1/places/reverse?latitude=35.08434260991064&longitude=129.09706980411505
<-- 404  {"code":"ADDRESS_NOT_FOUND", …}                    # 바다 — 이름 유지

--> POST /api/v1/matching/rooms
{"creatorId":1,"departureLat":35.2312986,…,
 "departure":"부산광역시 금정구 부산대학로63번길 2 부산대학교",
 "destination":"부산광역시 남구 문현동 1228-1", …}
<-- 200
```

`logcat | grep -cE "FATAL EXCEPTION|AndroidRuntime"` → **0**

### 검증 환경 메모
- 백엔드는 이미 떠 있던 프로세스를 그대로 사용(종료하지 않음). 장소 검색(카카오)도 이번엔 정상 동작.
- 두 번째 방 생성이 409 `ALREADY_JOINED_OTHER_PARTY` 로 막혀, 앞서 만든 방을
  `DELETE /api/v1/matching/leave/1/1` 로 정리한 뒤 재시도했다. 검증 종료 시점 방 목록은 비어 있다.
- 에뮬레이터 IME 가 한글 입력을 못 받아(`input text` UTF-8 미지원) 「CGV」로 검색해 목록에서
  **CGV 서면**을 골랐다. 앱 동작과는 무관.

## 5. 남은 것 / 후속

- **주소 표기가 전체 주소 그대로다.** 「부산광역시 금정구 …」의 시/도 접두는 서버가 다듬을 예정이라
  앱에서 자르지 않았다. 서버가 짧은 표기를 주기 시작하면 말줄임이 자연히 덜 걸린다.
- **22 탑승 상세·25 배차 등 하류 화면**은 서버가 준 이름을 그대로 쓴다. 21 은 이번에 말줄임을 넣었지만,
  다른 화면에서 긴 주소가 넘치는지는 qa-verifier 교차 확인 요청(방 생성 후 각 화면 순회).
- **조정 좌표에 건물명이 붙지 않는다** — 서버 `address` 가 도로명/지번 중 하나라 「OO빌딩 앞」 같은
  표기는 아직 불가. 15 리포트의 서버 계약 그대로 노출만 한다.
