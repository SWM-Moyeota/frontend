# 129 · 방 생성 반경 제거 — 서버가 정하는 값이 된 뒤의 정리

- 작업일: 2026-10-14
- 브랜치: `feat/backend-sync-sse` (HEAD `1a1100c`) — **커밋하지 않았다. 워킹트리에만 있다.**
- 백엔드 근거: 읽기 전용 체크아웃 `…/scratchpad/backend-dev`, `origin/develop` = `4f3744c`
  (PR #228 `refactor/party-fixed-radius`: `a668aee` + `c1b6e35`)
- 발단: QA 127 §4-2 / §6-2 가 「반경을 매칭에 쓸 것인가, 아니면 화면에서 내릴 것인가」를
  운영자·백엔드 결정 사항으로 남겼다. 백엔드가 **후자**로 정리했고, 앱이 그 계약을 따라간다.
- **실서버 미검증** — 지시에 따라 `adb` 를 쓰지 않았다(에뮬레이터는 다른 에이전트가 사용 중).
  계약 근거는 **백엔드 소스 직접 확인**이다(아래 §1 에 파일·라인).

---

## 1. 백엔드 계약 변화 (소스로 확인)

### 요청 — 반경이 **사라졌다**

`matching/party/dto/OpenPartyRequest.java` 의 현재 필드 전체:

```java
public record OpenPartyRequest(Long creatorId,                    // 무시됨(토큰 사용), deprecated
                               Double departureLat, Double departureLng,
                               Double destinationLat, Double destinationLng,
                               String departure, String destination,
                               Integer capacity) { … }
```

- `departureRadius`·`destinationRadius` 가 `OpenPartyRequest`·`OpenPartyCommand` 양쪽에서 빠졌다.
- `PartyService.open` 이 `Radius.DEFAULT, Radius.DEFAULT` 로 방을 만든다(`PartyService.java:67`).
- `Radius.DEFAULT = new Radius(500)` 이며 **허용 상한 `MAX_RAD` 와 별도 상수**다(`c1b6e35` —
  상한을 낮추면 이미 500 으로 저장된 방이 복원 검증에서 400 으로 깨지기 때문).
- Swagger 가 「반경은 서버 고정값(500m)」으로 갱신되고 400 목록에서 `INVALID_RADIUS` 가 빠졌다
  (`PartyController.java:33-34`).
- `http/matching.http`·부하테스트 `loadtest/lib/api.js` 의 요청 예시에서도 반경이 빠졌다.
  「반경 초과 400」 케이스는 **삭제**됐다.
- **옛 앱이 계속 보내도 서버는 모르는 키로 흘린다**(백엔드 커밋 메시지: Jackson 3 기본 동작,
  웹 테스트로 확인). 즉 지금 앱은 **아무 증상 없이 버려지는 값**을 보내고 있었다.

### 응답 — 반경은 **그대로 있다**

`PartyDetailResult`·`PartyResult`·`OpenPartyResponse` 세 record 모두
`Integer departureRadius, Integer destinationRadius` 를 유지한다(`Party.getDepartureRadius().meters()`).
백엔드 커밋 메시지가 명시: 「DB 컬럼·도메인·응답의 반경 필드는 유지(앱 호환, 이후 경로 매칭에서 재사용)」.

### 그 값을 읽는 곳이 **여전히 없다** (127 §4-2 의 사실 재확인, `4f3744c` 기준)

- 합류: `PartyService.join` → `party.join(memberId)` — **거리 검사 없음**(정원·상태만 본다).
- 목록: `findActivePartiesWithin(swLat, swLng, neLat, neLng)` — **지도 뷰포트**만 본다.
- `Party.java` 에서 `Radius` 는 생성·복원·조회용 getter 뿐.
- 참고로 **기사 배차는 실제로 반경을 쓴다**(`DispatchService`: 1000m → 500m 씩 확대 → 3000m).
  동승자 찾기의 반경과는 **다른 개념**이다 — 25 의 「주변 기사님에게 요청하고 있어요」는 참이다.

---

## 2. 제거한 것

### 2-A. 요청 본문 · 도메인

| 파일 | 변경 |
|---|---|
| `data/.../remote/dto/PartyDtos.kt` | `OpenPartyRequestDto` 에서 `departureRadius`·`destinationRadius` **삭제**. 남은 키는 좌표 4 + 라벨 2 + `capacity` = **7개** |
| `domain/.../model/NewParty.kt` | `departureRadius`·`destinationRadius`·`DEFAULT_RADIUS_METERS` 와 companion object **삭제**. KDoc 의 「서버 검증 규칙 … radius 100~500」도 지웠다(이제 검증 자체가 없다) |
| `data/.../remote/PartyMappers.kt` | `NewParty.toRequestDto()` 에서 두 인자 제거 |
| `data/.../remote/MatchingApi.kt` | `openParty` KDoc 에 「반경도 요청에 없다 — 서버 고정값(500m), 400 목록에서 `INVALID_RADIUS` 빠짐」 |
| `domain/.../repository/RideRepository.kt` | `createParty` KDoc 에 「앱이 정하는 조건은 좌표·라벨·정원뿐」 |

**`RideRepository.createParty(request: NewParty): Ride` 시그니처는 그대로다** — 반경은 `NewParty`
안에 있었으므로 경계면 메서드 모양은 바뀌지 않았다. 바뀐 것은 **`NewParty` 의 모양**이다
(compose-builder 알림 대상: `NewParty` 에서 `departureRadius`/`destinationRadius` 가 없어졌고,
`MatchConditions` 가 `capacity` 하나만 들고 다닌다).

### 2-B. 화면 — 전수 조사 결과 (`radius`·`반경`·m 단위 칩)

| 화면 | 지운 것 | 파일 |
|---|---|---|
| **16 도착지 확인** | 「출발지 반경」·「도착지 반경」 `ConditionRow` 2개 + 칩 6개 + 그 사이 구분선 2개 | `feature/home/DestinationConfirmModal.kt` |
| 16 | `RadiusOptions`(100m/300m/500m) · `DefaultRadiusLabel`("300m") · `radiusLabelToMeters()` | 같음 |
| 16 | `rememberSaveable` 상태 `originRadius`·`destinationRadius` | 같음 |
| 16 | `MatchConditions.departureRadiusMeters`·`destinationRadiusMeters` (→ `capacity` 하나만 남음) | 같음 |
| 16 | 쓰이지 않게 된 `HorizontalDivider` import | 같음 |
| 16 → 생성 | `NewParty(… departureRadius = …coerceIn(100,500), destinationRadius = …)` 2줄 | `feature/home/DestinationConfirmRoute.kt` |
| **21 매칭 대기** | 조건 카드의 **「탐색 반경」 행** + 그 위 구분선 | `feature/matching/MatchWaitingScreen.kt` |
| 21 | **지도 위 탐색 반경 원** (`MapRadiusCircle` 계산 · `radiusCircle` 전달) | 같음 |
| 21 | 펼침 상태에서 반경 원 외접 사각형으로 카메라를 맞추던 분기 (`circleBoundsPoints`) → **출발–도착 전체 기준 한 가지로 단순화** | 같음 |
| 21 | `radiusLabel: String = "—"` 파라미터 | 같음 |
| 21 | `radiusLabel(ride)` · `meters(value)` 헬퍼 | `feature/matching/MatchWaitingRoute.kt` |
| core | `RouteMapView(radiusCircle = …)` 파라미터 + `CircleOverlay` DisposableEffect + `MapRadiusCircle` + `RadiusFillAlpha` | `core/designsystem/.../RouteMapView.kt` |
| core | `circleBoundsPoints()` + `METERS_PER_DEGREE_LAT` + `kotlin.math.cos` import | `core/designsystem/.../MapCamera.kt` |

**「준비 중」 자리표시는 두지 않았다.** 16 의 매칭 조건 카드는 「인원」 한 줄만 남고(구분선도 없다),
21 의 조건 카드는 출발지·도착지·매칭 조건 3줄이 된다. 카드 자체는 유지했다 —
남은 값들은 **실제로 방을 정하는 값**이라 카드 제목 「매칭 조건」이 거짓이 아니다.

### 2-C. 문구 — 반경을 설명·암시하던 것

전수 조사(`presentation/src/main` 한국어 리터럴 + `주변`·`근처`·`이내`·`범위`·`안에서`·`거리`)
결과 **사용자에게 보이는 반경 문구는 행 라벨 2개(16)·1개(21)뿐**이었다. 「300m 안에서 찾아요」류의
문장은 애초에 없었다. 다만 **KDoc·주석 5곳이 지금은 거짓**이 되어 함께 고쳤다(127 의 기준:
사용자 문구가 아니어도, 다음 사람이 그걸 읽고 거짓을 되살리는 자리는 고친다):

| 파일 | 고친 서술 |
|---|---|
| `MatchWaitingScreen.kt` KDoc | 「탐색 반경 원 … 「어느 범위에서 사람을 찾고 있는지」를 **실제로 보여 준다**」 → 원을 지운 사실과 이유 |
| `MatchWaitingScreen.kt` `SearchingBadgeContent` KDoc | 「「주변에서 찾고 있다」는 정보는 **실제 탐색 반경 원**이 훨씬 정확하게 보여 준다」 → 「매칭은 반경을 보지 않는다」 |
| `core/designsystem/.../RadarSearchArea.kt` KDoc | 「21 에서는 **실제 탐색 반경 원**이 보여 주므로 레이더를 걷어냈다」 → 레이더를 걷어낸 **진짜 이유**(지도의 출발·도착·경로가 진짜 정보다) + 25b 의 「주변 기사님」은 참이라는 근거(`DispatchService`) |
| `DestinationConfirmModal.kt` KDoc | 「매칭 방식은 「주요 승차지점」 하나뿐」 — **이미 지운 행**을 설명하던 잔존 서술(127 §1-E) |
| `DestinationConfirmModal.kt` `MatchConditions` 주석 | 「모여타는 **같은 성별끼리만 매칭되는 서비스**라 항상 참」 — 127 §1-A 가 거짓으로 확정한 주장이 주석에 남아 있었다. 「성별은 매칭에 쓰이지 않는다」로 고쳤다(토글을 두지 않는 결론은 그대로) |

---

## 3. 응답의 반경을 어떻게 처리했나 — 판단과 근거

**결론: DTO 는 남기고, 도메인·화면에서는 완전히 뺀다.**

| 계층 | 처리 | 근거 |
|---|---|---|
| `PartyDetailResponse` · `OpenPartyResponse` (DTO) | **필드 유지** + KDoc 으로 「서버 고정값, 사용자가 고른 값이 아니다, 도메인으로 옮기지 않는다」 명시 | 서버가 실제로 내려보내는 값이다. DTO 는 서버 record 의 거울이라, 지우면 다음 사람이 서버 응답과 DTO 를 비교할 때 「왜 없지」를 다시 추적해야 한다 |
| `domain/model/Ride` | `departureRadiusMeters`·`destinationRadiusMeters` **삭제**. 자리에 「왜 없는지」 주석(지문 필드 패턴과 같은 형식) | presentation 은 도메인만 본다. 도메인에 값이 없으면 **화면이 그걸 조건으로 보여줄 길 자체가 없다** |
| `PartyMappers` 상세·생성 매퍼 | 반경 매핑 2쌍 **삭제**, 자리에 이유 주석 | 같음 |

**빼는 쪽을 택한 이유** (지시의 권고와 같은 결론, 근거를 다시 적는다):

1. **사용자가 바꿀 수 없는 값이다.** 반경을 고르는 UI 가 없어졌고 변경 API 도 없다. 21 의 조건 카드는
   「방을 만들 때 정해진 값」을 보여 주는 자리인데, 반경은 이제 **방을 만든 사람이 정한 값이 아니다.**
2. **아무것도 걸러내지 않는다.** 합류에 거리 검사가 없고 목록은 뷰포트만 본다(§1). 「탐색 반경 500m」은
   "이 범위의 사람을 찾는다"로 읽히는데 그런 일이 일어나지 않는다 — 127 이 지운 거짓 고지와 같은 종류다.
3. **지도 원은 더 강한 주장이었다.** 글자는 읽지 않을 수 있지만 원은 그 자리에서 「여기까지 찾는다」를
   그려 보인다. 행만 지우고 원을 남기면 거짓의 더 센 버전만 남는다.
4. 되살릴 조건을 코드에 적어 뒀다 — **경로 매칭이 실제로 이 값을 쓰기 시작한 뒤.**
   그때는 서버 응답의 반경이 "서버가 쓰는 범위"라는 참인 의미를 갖는다.

**부수 효과(의도한 것):** 21 펼침 상태의 카메라가 「반경 원 외접 사각형」 대신 **출발–도착 전체**를
맞춘다. 좌표가 하나뿐인 방(목록 응답으로 만든 `Ride`)에서는 `fitMapCamera` 가 null 을 주고
출발지 중심 기본 줌(14.0)으로 떨어진다 — 없는 범위를 그려 화면을 메우지 않는다.

---

## 4. 테스트

### 고친 것

| 파일 | 변경 |
|---|---|
| `data/.../PartyMappersTest.kt` | 생성 요청 테스트를 **계약 테스트로 승격**(아래). `assertEquals(500, dto.departureRadius)` 2줄 삭제 |
| `data/.../PartyMappersTest.kt` | 상세 shape 테스트에 `assertEquals(500, decoded.departureRadius/destinationRadius)` 추가 — **DTO 는 서버 모양대로 받는다**를 못 박는다(도메인으로 새지 않는 것은 `Ride` 에 필드가 없어 컴파일이 보장) |
| `presentation/.../WaitingSseStateTest.kt` | 「증분은 멤버와 상태만 건드린다」의 `departureRadiusMeters = 500` 제거 |

응답 JSON 픽스처(`PartyMappersTest` 상세 shape · `detailResponse()` 헬퍼 · `PartySseEventTest`)의
`"departureRadius":500` 은 **그대로 뒀다** — 서버가 실제로 그렇게 보낸다.

### 새로 못 박은 것 (요청 본문에 반경이 없다)

`data/.../PartyMappersTest.kt` — `생성 요청 본문에는 좌표 라벨 정원만 실린다 — 사용자 id 도 반경도 없다`

```kotlin
assertFalse("반경이 본문에 되살아났다: $encoded", encoded.contains("Radius"))
assertFalse("반경이 본문에 되살아났다: $encoded", encoded.contains("radius"))
assertEquals(                       // 키 집합 자체를 고정 — 새 필드가 몰래 끼어드는 것까지 잡는다
    setOf("departureLat", "departureLng", "destinationLat", "destinationLng",
          "departure", "destination", "capacity"),
    json.parseToJsonElement(encoded).jsonObject.keys,
)
```

이 테스트가 **필요한 이유**: 반경이 본문에 되살아나도 서버는 모르는 키를 흘리고 **200 을 준다.**
증상이 없으니 아무도 눈치채지 못하고, 「앱이 고른 반경이 반영된다」는 착각만 남는다.

`presentation/.../feature/home/MatchConditionsTest.kt` (**신규**) — `방 생성으로 넘기는 매칭 조건은 정원뿐이다`
: `MatchConditions` 의 프로퍼티 목록이 `["capacity"]` 임을 고정한다(kotlin-reflect 없이 자바
`declaredFields`, Compose 가 얹는 static `$stable` 은 제외). data 쪽이 **전선에 실리는 본문**을,
이쪽이 **화면이 들고 있는 조건**을 못 박는 짝이다.

### 뮤테이션 확인 (테스트가 실제로 잡는지)

| 되살린 것 | 결과 |
|---|---|
| `OpenPartyRequestDto` 에 `departureRadius` 추가 + `toRequestDto` 가 500 을 실음 | `PartyMappersTest … 반경도 없다` **FAILED** (`PartyMappersTest.kt:313`) → 되돌린 뒤 통과 |
| `MatchConditions` 에 `departureRadiusMeters: Int = 500` 추가(기본값이라 호출부는 컴파일됨) | `MatchConditionsTest` **FAILED** → 되돌린 뒤 통과 |

### 최종 검증

```
./gradlew :app:assembleDebug :data:testDebugUnitTest :presentation:testDebugUnitTest --console=plain -q
→ exit 0 (출력 없음)
data 278건 / presentation 257건 = 총 535건, 실패 0, 건너뜀 0
```

**실기 검증은 하지 않았다**(지시 사항 — `adb` 미사용, 에뮬레이터는 다른 에이전트가 사용 중).

---

## 5. 문서 — 고치지 않았다 (근거)

`docs/MODE-ROUTES.md`·`docs/BACKEND-GAP-CLEANUP.md` 를 `반경`·`radius` 로 grep → **둘 다 0건.**
MODE-ROUTES 의 15·16 줄은 「공통」뿐이고 21 줄은 문구·CTA 만 적는다. 지시대로 **없는 서술을 새로
만들지 않았다.** (BACKEND-GAP-CLEANUP 은 「백엔드에 API 가 없어 동작하지 않던 기능」의 이력이라,
백엔드가 **의도적으로 계약을 바꾼** 이번 건과 전제가 다르다. 굳이 적을 자리면 §1 이 아니라 새 절이
필요한데, 그건 이 지시의 범위를 넘는다.)

반경 서술이 남아 있는 문서는 **저장소 루트의 `WIREFRAME-MVP1.md`** 하나다(「출발지 반경 1km 이내
Redis GEO」·「중간 지점(Midpoint)」 — 원래 기획). **손대지 않았다**: 이 문서는 구현 현황이 아니라
초기 와이어프레임·기획 스펙이고, 지시가 지정한 두 파일도 아니다. 반경을 다시 쓰려는 사람이 참고할
원 기획이라 지우는 것이 오히려 정보 손실이다.

---

## 6. 남은 것 / 다음 사람에게

1. **실서버 미검증.** 방 생성 → 21 → 22 경로를 에뮬레이터로 한 번 확인해야 한다
   (확인 포인트: ① POST 본문에 `Radius` 키가 없는지 ② 16 모달 펼침에 「인원」 한 줄만 보이는지
   ③ 21 지도에 원이 없고 출발–도착이 들어오는지 ④ 21 조건 카드 3줄). qa-verifier 에 교차 검증 요청.
2. **해소된 과거 결함** — `_workspace/51_qa-verifier_destconfirm_ux.md` 의 **D-2**(1km·2km 선택이
   500m 로 깎여 전송됨)와 **D-3**(「탐색 반경」 1km 하드코딩)은 **원인이 사라져 자동 해소**됐다.
   고른 값이 깎일 일도, 반경을 표시할 자리도 없다.
3. **127 §4-2 / §6-2 는 닫혔다.** 「반경을 매칭에 쓸 것인가」 → 백엔드가 「1차 배포에서는 쓰지 않고
   서버가 정한다」로 결정. 앱은 선택 UI 와 표시를 모두 내렸다.
4. **되살릴 때의 조건**(코드 주석에도 같은 내용을 적어 뒀다): 서버가 반경을 **실제로 읽는**
   경로 매칭이 생긴 뒤에만. 그때 필요한 것은 ① `PartyDetailResponse` → `Ride` 매핑 복원
   ② `MapRadiusCircle`·`circleBoundsPoints` 재작성(이번에 삭제) ③ 「사용자가 고르는가, 서버가 정하는가」
   재결정. 반경을 **다시 요청에 싣는 것**은 서버 record 가 받기 시작한 뒤에만 의미가 있다.
5. **백엔드 응답에 남은 반경 필드**는 지금 아무 소비자가 없다(앱도 안 읽는다). 서버가 경로 매칭에
   쓰기 전까지는 응답에서 빼도 앱은 영향이 없다 — 백엔드가 응답 축소를 고민한다면 알려 줄 사실이다.

## 7. 변경 파일

**신규**
- `presentation/src/test/kotlin/com/moyeota/presentation/feature/home/MatchConditionsTest.kt`

**수정**
- `data/src/main/kotlin/com/moyeota/data/remote/dto/PartyDtos.kt` · `PartyMappers.kt` · `MatchingApi.kt`
- `data/src/test/kotlin/com/moyeota/data/remote/PartyMappersTest.kt`
- `domain/src/main/kotlin/com/moyeota/domain/model/NewParty.kt` · `Ride.kt` ·
  `domain/src/main/kotlin/com/moyeota/domain/repository/RideRepository.kt`
- `presentation/src/main/kotlin/com/moyeota/presentation/feature/home/DestinationConfirmModal.kt` ·
  `DestinationConfirmRoute.kt`
- `presentation/src/main/kotlin/com/moyeota/presentation/feature/matching/MatchWaitingScreen.kt` ·
  `MatchWaitingRoute.kt`
- `presentation/src/test/kotlin/com/moyeota/presentation/feature/matching/WaitingSseStateTest.kt`
- `core/designsystem/src/main/kotlin/com/moyeota/core/designsystem/component/RouteMapView.kt` ·
  `MapCamera.kt` · `RadarSearchArea.kt`

**내가 건드리지 않은 워킹트리 변경**: `.DS_Store`(작업 시작 시 이미 수정 상태) ·
미추적 `.agents/` · `.codex/` · `AGENTS.md`. 커밋할 때 분리해야 한다.
