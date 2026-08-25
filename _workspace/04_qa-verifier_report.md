# 04 qa-verifier — 2차 실기 검증 (2026-08-24)

**대상:** 02 compose-builder 화면 연결 완료분 + 01 api-integrator 연동분
**기기:** Pixel_6 에뮬레이터 (1080x2400, `-no-snapshot-load` **콜드부트**), `emulator-5554`
**백엔드:** **미기동** (8080 LISTEN 없음). 지시대로 **띄우지 않았다.** → 실서버 연동은 미검증, 에러 상태 UI 검증으로 대체.
**스크린샷:** `_workspace/screenshots/2nd_01`~`2nd_10*.png`

---

## 총평

| 항목 | 결과 |
|---|---|
| 1. 최종 빌드 + 유닛 테스트 | **통과** |
| 2. 에뮬레이터 실기 (5개 시나리오) | **부분 통과 — 결함 1건(High) 발견**, 3건은 서버 필요로 미검증 |
| 3. `hostId` 경계면 신규 확인 | **통과 (판정 완료)** |
| 4. 실서버 연동 / D-3 | **미검증** (백엔드 미기동, 지시대로 유지) |

**세션 전체 크래시 0건 / ANR 0건 / 역직렬화 예외 0건.** 앱은 전 과정에서 살아 있었다(`pidof com.moyeota` = 3676 유지).

**신규 결함 1건: F-1 (High) — 로딩·에러 상태에서 하단 탭바가 통째로 사라져 인앱 내비게이션이 끊긴다.**
정적 검증으로는 잡히지 않고 실기에서만 드러나는 유형이다.

---

## 1. 최종 빌드 — 통과

```
./gradlew :app:assembleDebug :data:testDebugUnitTest --console=plain
BUILD SUCCESSFUL   116 actionable tasks

data/build/test-results/testDebugUnitTest/
  ChatMappersTest    tests=8 failures=0 errors=0
  PartyMappersTest   tests=9 failures=0 errors=0
  PlaceMappersTest   tests=5 failures=0 errors=0
```

전 태스크가 UP-TO-DATE로 통과했기에 **캐시 통과가 아님을 별도로 확인**했다:
`find app/src presentation/src data/src domain/src core -name "*.kt" -newer app-debug.apk` → **결과 0건**
(APK mtime 2026-08-24 22:54:42가 모든 소스보다 최신). APK는 현재 소스를 반영한 산출물이 맞다.

APK: `app/build/outputs/apk/debug/app-debug.apk` (15.6MB) — 설치 `Success`.

---

## 2. 실기 검증 결과

### 2-1. 앱 기동 · 홈 — 통과

콜드부트 → 온보딩(`2nd_01_launch.png`) → 「건너뛰기」 → 로그인 → 홈(`2nd_03_home.png`).
`Displayed com.moyeota/.app.MainActivity for user 0: +6s558ms` — 콜드 스타트 정상 범위.

홈의 「자주 가는 곳」이 **빈 상태 안내 문구**("자주 가는 곳을 등록하면 여기서 바로 부를 수 있어요")로 떨어졌다.
`getFavoritePlaces` 실패를 홈 진입을 막지 않고 흡수한 것으로, 02 보고서 2-2의 설계대로 동작한다. ✅

### 2-2. **런타임 URL·헤더 실증 — 통과 (1차 정적 검증의 실물 확인)**

OkHttp 로그로 실제 나간 요청을 캡처했다. 1차에서 소스 대조로만 판정했던 것이 **와이어에서 그대로 확인**됐다.

```
--> GET http://10.0.2.2:8080/api/v1/users/me/favorite-places?userId=1
--> GET http://10.0.2.2:8080/api/v1/matching/rooms
--> GET http://10.0.2.2:8080/api/v1/chat-rooms/me
    X-User-Id: 1
--> GET http://10.0.2.2:8080/api/v1/places?query=seomyeon
```

- `/api/v1` 프리픽스 반영 ✅ (구 `api/matching/...` 아님)
- baseUrl + 상대경로 조합에 **슬래시 중복·누락 없음** ✅
- **도메인별 사용자 식별 3종이 실제로 다르게 나간다** ✅
  - 장소 = 쿼리 `?userId=1`
  - 채팅 = 헤더 `X-User-Id: 1`
  - (매칭 액션의 경로 `{memberId}`는 서버 데이터 부재로 실기 미도달 — 정적 통과)
- 전 요청 `java.net.ConnectException: Failed to connect to /10.0.2.2:8080` → 서버 미기동이 원인이지 앱 결함이 아니다. ✅

### 2-3. Explore 목록 에러 / 재시도 — 통과 (단, F-1 동반)

`2nd_04_explore_error.png` — "합승 목록을 불러오지 못했어요" + 「다시 시도」 렌더 ✅
「다시 시도」 탭 → 로그에 `GET /api/v1/matching/rooms` **재요청 확인** ✅ (버튼이 실제로 동작한다)

### 2-4. Chat 목록 에러 — 통과 (단, F-1 동반)

`2nd_08_chat_error.png` — "채팅방 목록을 불러오지 못했어요" + 「다시 시도」 ✅

### 2-5. Destination 검색 — 통과

`2nd_09_destination.png` / `2nd_10_search_error.png`

- 즐겨찾기 실패가 **인라인 에러**("자주 가는 곳을 불러오지 못했어요", 빨강)로 표시되고 화면은 계속 쓸 수 있다 ✅
  (Explore/Chat과 달리 화면을 갈아엎지 않는다 — 이쪽이 올바른 패턴이다)
- 검색 실패도 인라인("장소를 검색하지 못했어요"). 입력값·키보드 유지 ✅
- **CTA 「경로 확인하기」가 비활성(회색) 유지** ✅ — 좌표 있는 Place를 고르기 전에는 방 생성으로 못 넘어간다(02 보고서 2-4 설계대로)
- 출발지가 「부산대학교 정문 / 현재 위치」 고정 — 02 보고서 보류 2(`DemoOrigin`)가 사용자에게 그대로 보인다. 위치 연동 전까지는 의도된 동작.

**디바운스 실증 — 통과**

8타를 연속 입력하고 나간 요청을 셌다.

```
8 keystrokes → /api/v1/places 요청 2건
  ?query=s          (첫 타 — adb 왕복 지연이 300ms를 넘겨 발화)
  ?query=seomyeon   (나머지 7타가 1건으로 합쳐짐, 완성된 쿼리)
```

타수마다 요청이 나가지 않고 **마지막 요청이 완성된 쿼리를 담는다.** 300ms 디바운스가 실제로 동작한다. ✅

---

## 3. 결함

### F-1 [High] 로딩·에러 상태에서 하단 탭바가 사라져 인앱 내비게이션이 끊긴다

**재현 (100%, 백엔드 미기동 상태에서 항상):**
1. 앱 실행 → 홈 (하단 탭바 있음)
2. 하단탭 「합승」 탭
3. 목록 조회 실패 → 에러 화면 표시
4. **하단 탭바가 사라진다** (`2nd_04_explore_error.png` ↔ `2nd_03_home.png` 비교)
5. 탭바가 있던 좌표(660,2262 = 채팅)를 탭해도 **아무 반응 없음** (`2nd_06_explore_tab_dead.png`)
6. 시스템 back 제스처로만 탈출 가능 (`2nd_07_after_back.png`)

**동일 증상: 「채팅」 탭도 같음** (`2nd_08_chat_error.png`).

**원인 (파일:라인):**
하단 탭바가 공용 Scaffold가 아니라 **각 화면 컴포저블 내부**에서 그려진다.
`ExploreRoute.kt:70-77`은 `onTabSelect`를 `ExploreScreen`에만 넘긴다:

```kotlin
// presentation/.../feature/explore/ExploreRoute.kt:67-78
when (val current = state) {
    ExploreViewModel.UiState.Loading -> LoadingBox()                       // ← 탭바 없음
    is ExploreViewModel.UiState.Error -> ErrorBox(message =…, onRetry =…)  // ← 탭바 없음
    is ExploreViewModel.UiState.Success -> ExploreScreen(… onTabSelect = onTabSelect)  // ← 탭바 여기에만
}
```

`LoadState.kt:26,37`의 `LoadingBox`/`ErrorBox`는 `Modifier.fillMaxSize()` Box라 화면 전체를 차지하며 탭바를 대체해버린다.

**영향:** 지금처럼 서버가 안 떠 있으면 「합승」·「채팅」 탭이 **진입 즉시 내비게이션이 끊긴 상태**가 된다.
서버가 떠 있어도 로딩 순간마다 탭바가 깜빡이며 사라지고, 일시적 네트워크 장애 시 같은 상황이 재현된다.
시스템 back으로 빠져나올 수는 있어 완전한 데드엔드는 아니지만, 앱의 1차 내비게이션이 통째로 없어지는 것은 사용자 체감상 멈춘 앱이다.

**수정 요청 대상: compose-builder**
`MainNavGraph`에서 탭 레벨 라우트(HOME/EXPLORE/CHAT/MY)를 **하단 탭바를 항상 렌더하는 공용 Scaffold로 감싸고**, 각 Route는 콘텐츠 영역만 그리도록 분리할 것. 그러면 Loading/Error/Success 어느 상태에서도 탭바가 유지된다.
차선책(국소 수정): `ExploreRoute`/`ChatRoute`의 Loading·Error 분기를 탭바를 포함한 래퍼 컴포저블 안에 넣기.

참고: `DestinationRoute`(15)는 실패를 **인라인 에러**로 처리해 화면 골격을 유지한다(2-5). 이쪽 패턴이 정답이고, Explore/Chat만 전체 치환 방식이라 어긋난다.

### F-2 [Low] 좌표 없이 16 모달에 진입하면 출발지 이름이 도착지 자리에 표시된다

`DestinationConfirmRoute.kt:93` — `val target = destination ?: DemoOrigin`
`destination`이 null일 때 폴백이 `DemoOrigin`(부산대학교 정문 = **출발지**)이라, 모달의 도착지 칸에 출발지 이름이 뜬다.
바로 아래 `errorMessage`("도착지 좌표가 없어요…", `:101`)가 상황을 설명하고 `onFindCompanions`도 `destination != null`일 때만 동작(`:104`)해 실제 잘못된 방이 만들어지지는 않는다. 표시상의 혼란만 있다.
**수정 요청 (compose-builder):** 폴백 시 도착지 이름을 `"—"`/빈 값으로 두는 편이 명확하다.

---

## 4. `hostId` 경계면 신규 확인 (지시 항목 3) — **통과**

02 보고서 7절이 "실기 최우선 확인 대상"으로 지목한 지점. **직렬화 경로를 끝까지 코드로 추적해 판정했다.**

**추적 결과 — 4단계 모두 정합:**

| 단계 | 근거 | 값 |
|---|---|---|
| ① 백엔드 record | `matching/application/dto/PartyDetailResult.java` — `PartyDetailResult(Long id, **Long hostId**, …)` | Java `Long` |
| ② JSON 직렬화 | `origin/develop` 전 소스에 `ObjectMapper`/`JsonMapper` 커스텀 빈 **0건**, `@JsonSerialize`·`@JsonFormat`·`ToStringSerializer`·`@JsonComponent` **0건** (grep 확인) → Jackson 기본 동작 | JSON **숫자** `"hostId": 1` |
| ③ 프론트 DTO | `data/.../dto/PartyDtos.kt:28` `val hostId: Long? = null` | kotlinx가 숫자 → `Long` 파싱 ✅ |
| ④ 매퍼 | `data/.../PartyMappers.kt:53` `hostId = hostId?.toString()` | `1L.toString()` = `"1"` |
| ⑤ 비교 | `MatchWaitingRoute.kt:152` `ride.hostId != null && ride.hostId == userSession.currentUserId.toString()` → `"1" == "1"` | **true** ✅ |

`RideDetailRoute.kt:113`도 동일(`current.ride.hostId == currentUserId`, `:102`에서 `.toString()`).

**"조용히 false가 되는" 실패 시나리오도 개별 검토했다:**

- **hostId가 문자열 `"1"`로 온다면?** → kotlinx 비-lenient 기본 설정이라 `Long?` 필드에 문자열이 오면 **예외로 시끄럽게 실패**한다. 조용한 false가 아니다. 다만 ②에서 커스텀 serializer가 없음을 확인했으므로 이 경우 자체가 발생하지 않는다.
- **hostId가 null이면?** → `null?.toString()` = null → `null == "1"` = false → 매칭 시작 버튼 미노출(조용한 실패). 그러나 `Party.getHostId()`는 저장된 방에 항상 값이 있어 실현 가능성이 낮다. `MatchWaitingRoute.kt:152`가 `hostId != null` 가드를 이미 두고 있다.
- **isHost 소비처 3곳이 전부 상세 조회 기반인가?** → 확인함. `MatchWaitingRoute.kt:58`, `JoinConfirmRoute.kt:58`, `RideDetailRoute.kt:49` 모두 `repository.getPartyDetail(partyId)`를 쓴다. ✅

**판정: 현재 코드에서 hostId 비교는 정상 동작한다. 02 보고서가 우려한 조용한 실패는 발생하지 않는다.**

### 다만 — 같은 계열의 잠복 위험 1건 (지금은 무해)

`PartyMappers.kt:21-33`의 **목록** 매퍼(`PartyItem.toRide()`)는 성격이 다르다.

- 백엔드 `PartyListResponse.PartyItem`에 `hostId`가 **아예 없다** → `Ride.hostId`가 항상 `null`
- 멤버를 `User(id = "m$index", …)`로 **합성**한다 → id가 `"m0"`, `"m1"`이라 실제 memberId와 무관

즉 **목록에서 온 `Ride`로 `isHost`를 판정하거나 멤버를 필터하면 조용히 틀린다.**
`RideDetailScreen.kt:111`의 `ride.members.filter { it.id != currentUserId }`가 대표적인 소비처인데,
현재는 `RideDetailRoute`가 상세 조회 결과만 넘기므로 안전하다.
**앞으로 목록 항목을 그대로 상세/대기 화면에 넘기는 최적화를 하면 그 순간 깨진다.** 코드 주석으로 못박아두길 권한다.

---

## 5. 미검증 항목 (백엔드 미기동 — 지시대로 서버를 띄우지 않음)

| 항목 | 사유 | 대체 검증 |
|---|---|---|
| **실서버 연동 전반** | 8080 미기동 | 에러 상태 UI 검증으로 대체 (2-3~2-5) |
| **D-3 `Instant` 직렬화** (1차 리포트) | 실서버 응답 필요 | **미검증 유지.** 세션 중 역직렬화 예외 0건이나, 성공 응답을 한 번도 못 받아 무의미. 서버 기동 시 `curl -s localhost:8080/api/v1/matching/rooms/1 \| grep createdAt` 1회로 판정 |
| **JoinConfirm 「합류 API가 아직 서버에 없어요」 배너** | Explore 목록이 비어 20 화면 도달 불가 | **정적 확인 완료.** `JoinConfirmRoute.kt:72-74`가 `catch (e: ApiNotAvailableException) → errorMessage = e.message`, `RemoteRideRepository.kt:31`이 정확히 그 문자열로 throw. catch 순서도 `ApiNotAvailableException` → `Exception`으로 올바름 |
| **방 생성 모달 capacity 가드** | 16 모달 도달 불가(아래 참조) | **정적 확인 완료.** `DestinationConfirmModal.kt:313` 칩이 `listOf(1, 2, 3)`뿐이고, `DestinationConfirmRoute.kt:53` `capacity.coerceIn(1, MAX_PARTY_CAPACITY=3)` + `:55-56` `radius.coerceIn(100, 500)` 이중 가드 |
| **채팅방 진입 / 전송 실패 시 입력창 유지** | 채팅방 목록이 비어 방 도달 불가 | **정적 확인 완료.** `ChatRoute.kt` — 실패 시 `it.copy(sending=false, errorMessage=…)`로 **`text`를 보존**, 성공 시에만 `InputState()`로 초기화 |
| **MatchWaiting(21) 준비/시작/나가기, RideDetail(22) 나가기** | 서버 데이터 필요 | 미검증 |
| **3초 폴링(`getMessagesAfter`)** | 채팅방 도달 불가 | 미검증 |

### 미검증에서 파생된 구조적 관찰 (2차의 실질 소득)

**백엔드가 없으면 매칭 플로우 전체(15 → 16 → 21 → 22)에 도달할 수 없다.**
방 생성이 좌표를 필수로 요구하는데, 좌표 있는 `Place`를 얻는 경로가 **서버 검색 결과 / 서버 즐겨찾기** 둘뿐이기 때문이다.
「최근 검색」 더미는 02 보고서 보류 8대로 검색어만 채우고 좌표를 주지 않아 CTA가 계속 비활성이다(2-5에서 실물 확인).

이건 결함이 아니라 설계상 귀결이지만, **3차 실기는 백엔드 기동 + 시드 데이터 없이는 의미가 없다**는 뜻이다.
3차 진행 시 사용자에게 백엔드 기동을 요청하고, `POST /api/v1/matching/rooms`로 방을 최소 2개 시드해야 한다
(좌표는 한국 범위 33~39N / 124~132E, capacity ≤ 3, radius 100~500 — 1차 리포트 D-1/D-2 제약).

---

## 6. 1차 리포트(03) 결함 상태 갱신

| ID | 내용 | 상태 |
|---|---|---|
| D-1 | 매칭·장소 검증 실패가 400이 아닌 500 | **compose-builder 반영 확인** — 02 보고서 1절대로 전 ViewModel이 `catch (e: Exception)` 단일 분기. 상태코드를 읽는 코드가 presentation에 **0건**(grep 확인). ✅ 단 백엔드 `@RestControllerAdvice` 추가 요청은 유효 |
| D-2 | capacity 상한 3 클라 가드 없음 | **해소** — `MAX_PARTY_CAPACITY=3` + `coerceIn` + 칩 1/2/3 (5절 참조) ✅ |
| D-3 | `Instant` 직렬화 미검증 | **미검증 유지** (백엔드 미기동) |
| D-4 | `HttpLoggingInterceptor.Level.BODY` 항상 켜짐 | **미해소** — 이번 세션 logcat에 요청 URL과 `X-User-Id` 헤더가 그대로 찍혔다(2-2). 검증에는 유용했지만 릴리즈에서는 채팅 본문·향후 토큰이 노출된다. `BuildConfig.DEBUG` 가드 필요 |
| D-5 | `NetworkModule` 데드코드 3개 | **미해소** — 호출부 여전히 0건 |
| D-6 | `KAKAO_API_KEY` 미설정 시 장소 검색 실패 | 미검증 (서버 미기동) |

---

## 7. 담당별 수정 요청

**compose-builder**
1. **F-1 (High)** — 탭 레벨 라우트를 하단 탭바 포함 공용 Scaffold로 감싸기. Explore/Chat의 Loading·Error에서 탭바 소실.
2. F-2 (Low) — `DestinationConfirmRoute.kt:93` 도착지 폴백을 `DemoOrigin` 대신 빈 값으로.

**api-integrator**
3. D-4 — `NetworkModule.kt:18` 로깅 레벨에 `BuildConfig.DEBUG` 가드.
4. D-5 — `NetworkModule.kt:43-47` 데드코드 3개 삭제.
5. D-1 잔여 — `NewParty.kt:4` 주석 "위반 시 400" → 500으로 정정.
6. `PartyMappers.kt:21-33` 목록 매퍼에 "hostId 없음 / 멤버 id 합성이라 isHost·멤버 필터에 쓰면 안 됨" 주석 추가 (4절 잠복 위험).

**백엔드 (리더 경유)**
7. `PartyController`에 `join` 매핑 추가 (서비스는 이미 구현됨).
8. 매칭·장소 도메인에 `@RestControllerAdvice` 추가 — `IllegalArgumentException` → 400 + `ErrorResponse`(채팅과 동일 shape).
9. `PartyDetailResult.MemberInfo`에 `MemberStatus`(ready) 노출.

---

## 8. 3차 검증 진입 조건

1. 백엔드 기동(`origin/develop` 기준) + 방 2개 이상 시드
2. F-1 수정 반영
3. 그 뒤 확인: 실서버 목록/상세 렌더 · D-3 `createdAt` · JoinConfirm 배너 · 방 생성 → 21 → 22 전체 플로우 · 채팅방 폴링/전송 · `hostId` 기반 「매칭 시작하기」 버튼 실제 노출 여부

---

# 재검증 (루프 1회차) — 2026-08-25

**대상:** F-1 / F-2 / D-4 / D-5 수정분
**기기:** Pixel_6 에뮬레이터 **콜드부트 재기동** (`-no-snapshot-load`), 앱 재설치
**스크린샷:** `_workspace/screenshots/3rd_01`~`3rd_05*.png`

## 결과 요약

| 항목 | 결과 |
|---|---|
| 1. F-1 실기 재현 테스트 (핵심) | **통과** — 탭바 렌더 + **탭 터치로 실제 화면 전환 확인** |
| 2. F-2 도착지 칸 표시 | **통과** (정적 — 실기 도달 불가) |
| 3. D-4 주입 체인 + 로깅 차단 | **통과** (코드 리뷰 + 디버그 빌드 실증) |
| 4. 최종 게이트 빌드 | **통과** |
| (부수) D-5 데드코드 삭제 | **통과** |

**재검증 세션 크래시 0건 / ANR 0건.** → **재검증 루프 종료. 추가 수정 요청 없음.**

---

## 1. F-1 — 통과 (실기 재현 완료)

### 객관 증거: 하단 탭바 영역 픽셀 측정 (수정 전 ↔ 후)

동일 좌표대(y=2240~2320)의 배경색 대비 콘텐츠 픽셀 수를 스크립트로 셌다.

```
2nd_04_explore_error.png            (수정 전)  content px =   0  → 탭바 없음
2nd_08_chat_error.png               (수정 전)  content px =   0  → 탭바 없음
3rd_02_explore_error_tabbar.png     (수정 후)  content px = 462  → 탭바 있음
3rd_03_tab_nav_to_chat.png          (수정 후)  content px = 462  → 탭바 있음
3rd_05_chat_retry_in_scaffold.png   (수정 후)  content px = 462  → 탭바 있음
```

### 실기 시나리오 — 인앱 이동 복구 확인

2차에서 문제였던 것은 "탭바가 보이는가"가 아니라 **"눌러서 실제로 이동하는가"**였다. 그 지점을 직접 밟았다.

| 단계 | 조작 | 결과 |
|---|---|---|
| 1 | 홈 → 하단탭 「합승」 | Explore **에러 상태 + 탭바 유지**, 「합승」 파란색 활성 (`3rd_02`) |
| 2 | 에러 상태에서 하단탭 「채팅」 탭 | **실제로 채팅 화면으로 전환됨.** Chat 에러 + 탭바, 「채팅」 활성 (`3rd_03`) |
| 3 | 에러 상태에서 하단탭 「홈」 탭 | **홈으로 전환됨** (`3rd_04`) |
| 4 | Chat 에러의 「다시 시도」 탭 | `--> GET /api/v1/chat-rooms/me` **재발화 확인**, 탭바 유지 (`3rd_05`) |

2차에서 "탭 좌표를 눌러도 무반응 → 시스템 back으로만 탈출"이던 증상이 **완전히 해소**됐다.
`selectedTab` 하이라이트도 화면과 일치한다(합승→합승, 채팅→채팅).

**오탐 배제:** 축소 렌더에서 화면 상단에 탭 라벨이 겹쳐 보이는 듯한 인상이 있어, `y=0~390` 구간을 10px 간격으로 픽셀 스캔했다. `y=90` 이후 전 구간 **비배경 픽셀 0** — 상단 중복 렌더는 **없다**(시스템 상태바만 존재). 렌더링 아티팩트였다.

### BackStateScaffold 3화면 (20 합류 / 21 매칭대기 / 22 탑승상세) — 정적 확인

**실기 도달 불가**(2차 5절과 동일 — 서버 데이터가 없어 `partyId`를 얻을 수 없다). 코드로 확인했다.

- `JoinConfirmRoute.kt:121,124` → `BackStateScaffold("합류할까요?", **onDismiss**)`
- `MatchWaitingRoute.kt:146,149` → `BackStateScaffold("같이 탈 사람 찾는 중", **onCancelSearch**)`
- `RideDetailRoute.kt:111,114` → `BackStateScaffold("탑승 상세", **onBack**)`

세 곳 모두 Loading·Error 양쪽을 감싸고, 뒤로가기 인자가 `{}` 빈 람다가 아니라 **MainNavGraph에서 내려온 실제 네비게이션 콜백**이다. 재시도도 `viewModel::refresh`로 배선돼 있다. ✅

**채팅방(24) 중첩 구조 검토:** `ChatRoute.kt:284-289`가 `TabStateScaffold` 안에 `BackStateScaffold`를 중첩한다. 처음엔 과잉으로 보였으나, 성공 상태의 `ChatScreen.kt:391`이 실제로 `MoyeotaBottomBar`를 그리므로 **상태 간 골격이 일치**한다. 올바른 중첩이다. ✅

---

## 2. F-2 — 통과 (정적)

`DestinationConfirmRoute.kt:95-96`

```kotlin
destinationName = destination?.name ?: "—",
destinationAddress = destination?.roadName ?: "",
```

`DemoOrigin` 폴백이 제거됐다. `DemoOrigin` 잔존 참조는 `originStopName`(`:97`)과 `createParty`의 출발지 인자(`:103`) 두 곳뿐으로, **둘 다 출발지 용도라 정상**이다. 안내 문구(`:100-101`)와 `destination != null` 가드도 유지됐다. ✅
(실기 도달 불가 사유는 1항과 동일)

---

## 3. D-4 / D-5 — 통과

### 주입 체인 (3단계 전부 확인)

```
MoyeotaApplication.kt:17   AppContainer(debugLogging = isDebuggableBuild())
MoyeotaApplication.kt:26   applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
AppContainer.kt:17         NetworkModule.create("http://10.0.2.2:8080/", debugLogging)
NetworkModule.kt:35-39     if (debugLogging) { clientBuilder.addInterceptor(HttpLoggingInterceptor…) }
```

`debugLogging=false`면 **인터셉터를 아예 추가하지 않는다**(레벨을 NONE으로 낮추는 방식이 아니라 미추가 — 더 확실하다). ✅

**우회 경로 없음 확인:**
- `AppContainer(` 생성 지점 **1곳뿐** (`MoyeotaApplication.kt:17`) — 플래그 없이 만드는 경로가 없다
- `NetworkModule.` 호출 지점 **1곳뿐** (`AppContainer.kt:17`)
- D-5 데드코드 `createMatchingApi`/`createPlaceApi`/`createChatApi` **삭제 확인** (grep 0건) ✅

### 릴리즈에서 실제로 꺼지는가 — 근거 확인

- `app/build.gradle.kts:29` `buildFeatures { compose = true }` → **`buildConfig` 미활성** → `BuildConfig.DEBUG`가 생성되지 않는 것이 맞다. 주석의 판단 근거가 사실과 일치한다.
- `buildTypes { release { optimization { enable = false } } }` → `isDebuggable` 미지정 → AGP 기본값 `false` → 릴리즈 APK에 `FLAG_DEBUGGABLE` 미설정 → **로깅 off**
- 디버그 APK는 `aapt2 dump badging` 결과 **`application-debuggable`** 존재 → 디버그에서는 **on**

### 디버그 경로 실증

이번 세션 logcat에 `okhttp.OkHttpClient` 로그 **42줄** 발생 — 디버그 빌드에서 인터셉터가 실제로 붙는다. 조건 분기가 살아 있음을 양방향(코드/실행)으로 확인했다. ✅

---

## 4. 최종 게이트 빌드 — 통과

```
./gradlew :app:assembleDebug :data:testDebugUnitTest --console=plain
BUILD SUCCESSFUL   116 actionable tasks

ChatMappersTest   tests=8 failures=0 errors=0
PartyMappersTest  tests=9 failures=0 errors=0
PlaceMappersTest  tests=5 failures=0 errors=0     (합계 22 / 0 실패)
```

UP-TO-DATE 통과라 `find … -newer app-debug.apk` **0건**으로 APK(10:12:09)가 전 소스보다 최신임을 재확인했다. ✅

---

## 5. 재검증 중 새로 확인된 사항

### 5-1. 포트 8080은 백엔드가 아니라 **Burp Suite**가 점유 중 — 백엔드는 여전히 미기동

`lsof`에 8080 LISTEN이 잡혀 한때 백엔드가 뜬 것으로 보였으나, 실제 응답을 받아 확인하니 **Burp Suite 프록시**였다(PID 64825).

```
HTTP/1.1 200 OK
Content-Type: text/html; charset=utf-8
<html><head><title>Burp Suite</title> … Invalid client request received …
```

**지시대로 이 프로세스는 종료하지 않았다.**

- **D-3(`Instant` 직렬화)는 여전히 미검증**이다. 3차에서 실서버로 확인해야 한다.
- ⚠️ **3차 진행 전 주의:** Burp가 8080을 잡고 있으면 백엔드 기동 시 **포트 충돌**이 난다. 사용자에게 Burp 종료 또는 백엔드 포트 변경을 먼저 확인받아야 한다.

### 5-2. (부수 소득) 200 + `text/html` 응답에도 앱이 죽지 않는다

Burp 덕분에 2차보다 **강한 실패 조건**을 우연히 밟았다.
2차는 `ConnectException`(연결 자체 실패)이었지만, 이번엔 **HTTP 200 성공 응답 + JSON이 아닌 HTML 본문** → kotlinx 역직렬화 예외 경로다.

결과: **크래시 0건.** 전 ViewModel의 `catch (e: Exception)`가 흡수해 정상적인 에러 상태 UI로 떨어졌다.
프록시·캡티브 포털 환경에서 흔한 시나리오라 실전 견고성 근거가 하나 늘었다. ✅

---

## 6. 재검증 후 미해결 항목 (3차 이월)

수정 요청은 **전부 반영 확인**됐고 새로 발견한 결함은 없다. 아래는 서버가 필요해 이월되는 것들이다.

1. **D-3 `Instant` 직렬화** — 실서버 필요 (5-1 참조)
2. **실서버 연동 전반** — 목록/상세 렌더, JoinConfirm 배너, 방 생성→21→22 플로우, 채팅방 폴링/전송, `hostId` 기반 「매칭 시작하기」 노출
3. **D-6 `KAKAO_API_KEY`** — 장소 검색 실서버 확인
4. **백엔드 요청 3건** — `join` 매핑 추가 / 매칭·장소 `@RestControllerAdvice` / `MemberInfo`에 ready 노출
5. **3차 진입 조건** — Burp 포트 정리 → 백엔드 기동 → 방 2개 이상 시드(좌표 33~39N·124~132E, capacity ≤ 3, radius 100~500)
