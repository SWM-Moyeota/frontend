# 02 compose-builder — 화면 ↔ API 연결 (2026-08-24)

기준: `01_api-integrator_report.md`의 Repository 시그니처 정본 + 코디네이터 1차 QA 지적 2건 반영
검증: `./gradlew :app:assembleDebug --console=plain` **BUILD SUCCESSFUL** (신규 경고 0건 — 기존 경고 2건만 잔존)
**실서버 미검증** — 백엔드 미기동. 로딩/에러/재시도 UI는 코드 경로상 3상태 모두 렌더 가능하도록 작성.

---

## 1. 코디네이터 지적 반영

| 지적 | 반영 |
|---|---|
| 매칭·장소는 검증 실패가 400이 아니라 500으로 온다 → 상태코드 분기 금지 | 모든 매칭·장소 ViewModel이 `catch (e: Exception)` 단일 분기. **상태코드를 읽는 코드 없음.** 전부 "재시도 가능한 일반 실패" UI(`ErrorBox` 또는 `NoticeBanner`)로 처리 |
| capacity 상한 3 — 초과 시 메시지 없는 500 | `CreatePartyViewModel.MAX_PARTY_CAPACITY = 3` 상수 + `capacity.coerceIn(1, 3)` 클라이언트 가드. 16 모달 칩도 1/2/3만 노출 |

`ApiNotAvailableException`만 별도로 잡는데, 이건 상태코드가 아니라 도메인 예외 타입 분기다(합류 엔드포인트 부재 안내 전용).

---

## 2. 화면별 연결 결과

### 2-1. JoinConfirmScreen (20 합류 확인) — `explore/`
- **신규** `JoinConfirmRoute.kt` + `JoinConfirmViewModel`
- 진입 시 `getPartyDetail(partyId)`로 실제 방 정보를 그린다 (기존엔 더미 상수).
- CTA → `joinParty(partyId, userSession.currentUserId)`.
  - 성공 → `joined=true` → `LaunchedEffect`가 22 탑승 상세로 이동 (**성공 플로우 코드는 완성 상태**)
  - `ApiNotAvailableException` → **예외 메시지("합류 API가 아직 서버에 없어요")를 그대로** CTA 위 `NoticeBanner(ERROR)`에 노출
  - 기타 실패 → "합류하지 못했어요. 잠시 후 다시 시도해 주세요"
- `JoinConfirmScreen`은 순수 유지: 내부 `var joining` 제거 → `joining`/`joinErrorMessage` 파라미터로 승격.
- `partyId == null`이면 기존 더미 화면 폴백(RideDetailRoute 패턴 동일).

### 2-2. DestinationConfirmModal / HomeScreen (16·14) — `home/`
- **신규** `DestinationConfirmRoute.kt` + `CreatePartyViewModel`, `HomeRoute.kt` + `HomeViewModel`
- 16 모달 CTA → `createParty(NewParty)` → 성공 시 생성된 `Ride`를 그대로 받아 `createdPartyId`로 21 매칭 대기에 전달(재조회 없음, 01 보고서 4절 지침대로).
- 모달이 고른 조건을 `MatchConditions(capacity, departureRadiusMeters, destinationRadiusMeters, sameGenderOnly)`로 밖에 넘긴다 — 화면은 여전히 상태 없는 형태.
- 네비게이션: `popUpTo(DESTINATION_CONFIRM){inclusive=true}` — 방 생성 후 뒤로 눌러 모달로 돌아가지 않는다.
- 14 홈: `HomeRoute`가 `getFavoritePlaces`로 「자주 가는 곳」 카드를 채운다. 진입마다 `LaunchedEffect`로 재조회(15에서 ★ 등록 후 반영). 실패는 홈 진입을 막지 않고 빈 목록으로 떨어뜨린 뒤 안내 문구 표시.

### 2-3. MatchWaitingScreen (21) — `matching/`
- **신규** `MatchWaitingRoute.kt` + `MatchWaitingViewModel`
- `getPartyDetail` 조회 + 액션 4종:
  | 버튼 | 호출 | 성공 후 |
  |---|---|---|
  | 준비 완료 / 준비 취소 | `setReady` / `cancelReady` | 로컬 ready 토글 + 상세 재조회 |
  | 그만 찾기 | `leaveParty` | `left=true` → 14 홈 |
  | 매칭 시작하기 | `startMatching` | 상세 재조회(status→MATCHING) 후 25 배차 |
- **매칭 시작 버튼은 `ride.hostId == userSession.currentUserId` 일 때만 렌더**.
- **타 멤버 ready 표시는 구현하지 않았다**(01 플래그 6 — 서버 응답에 없음). 내 준비 상태만 로컬 상태로 관리하고, 서브타이틀에 "· 나는 준비 완료"만 덧붙인다. 코드에 사유 주석 명시.
- 기존 「조건 넓혀 찾기」 버튼(미연결 더미)은 「준비 완료/취소」로 교체, `onWidenSearch` 파라미터 제거.
- 액션 실패는 화면을 유지한 채 `NoticeBanner(ERROR)`. 중복 제출은 `inProgress` 가드로 차단.

### 2-4. DestinationScreen (15) — `home/`
- **신규** `DestinationRoute.kt` + `DestinationViewModel`
- 검색: 입력 300ms **디바운스** 후 `searchPlaces(query)`. 이전 job은 취소.
- 즐겨찾기: `getFavoritePlaces(userId)` 로드(sequence 정렬), 검색 결과 행의 ★ → `addFavoritePlace` → 성공 시 목록 재조회.
- 화면 계약을 좌표 기반으로 바꿨다: `onConfirmRoute(destination: String)` → **`onConfirmRoute(Place)`**. 방 생성이 좌표를 필수로 받기 때문에 **좌표 있는 장소를 고르기 전에는 CTA 비활성**.
- 자주 가는 곳 카드 탭 → 그 장소를 좌표까지 선택 확정.
- 도메인 `FavoritePlace`와 화면 로컬 `home.FavoritePlace` 이름 충돌은 `import ... as SavedPlace` 별칭으로 흡수(HomeScreen의 로컬 모델은 그대로 유지).

### 2-5. ChatScreen (24) — `chat/`
- **신규** `ChatRoute.kt`(ChatListViewModel + ChatRoomViewModel), `ChatListScreen.kt`
- 목록: `getMyChatRooms(userId)` → 출발→도착 제목, 방 상태, `lastReadMessageId == null`이면 안읽음 점. 진입마다 재조회.
- 방 진입: `getMessages(chatRoomId, userId)` 초기 로드 → **id 오름차순 정렬**(커서 페이징이 최신부터 올 수 있어 서버 정렬을 신뢰하지 않음) → `markAsRead(마지막 id)`.
- 폴링: **STOMP 미구현이라 3초 주기 `getMessagesAfter(cursor)`**. `viewModelScope`에 매달아 화면 이탈 시 자동 종료. 폴링 실패는 `runCatching`으로 삼켜 화면을 깨뜨리지 않고 다음 주기에 재시도.
- 전송: `sendMessage` → 응답 메시지를 목록에 병합(`distinctBy{id}`) + 읽음 갱신. **성공했을 때만 입력창을 비운다**(실패 시 사용자가 다시 타이핑하지 않게).
- 「채팅방 나가기」 → `leaveChatRoom` 호출 후 14 홈 (기존엔 서버 호출 없이 이동만 했다).
- `ChatScreen`은 순수 형태로 전환: 내부 `messages`/`input` 상태 제거 → 파라미터. 표시 모델 이름 충돌 회피로 `ChatMessage` → **`ChatUiMessage`** 개명, 도메인 모델은 `as DomainChatMessage` 별칭.
- 방 선택 상태는 `rememberSaveable` + `listSaver`로 프로세스 재생성에도 유지.

### 2-6. ExploreRoute / RideDetailRoute
- **ExploreRoute**: `init { refresh() }` → `LaunchedEffect(Unit) { refresh() }`로 이동. 01 보고서가 지적한 "Explore는 화면 재진입 시에만 재로드" 제약을 실제로 해소 — 합류·나가기 후 돌아오면 목록이 갱신된다.
- **RideDetailRoute**: `userSession` 주입. 「나가기」가 `leaveParty(partyId, memberId)`를 실제 호출하고 **성공 후에만** 화면 전환. 실패 시 `ErrorBox`로 재시도 가능 상태. `currentUserId` 하드코딩 `"1"` 제거 → `userSession.currentUserId`, `isHost`는 `ride.hostId` 비교로 산출.

---

## 3. 상태 처리 방식 (공통)

- 조회형: `sealed interface UiState { Loading / Success / Error(한국어 메시지) }` + `LoadingBox` / `ErrorBox(onRetry)` 재사용 — 기존 `presentation/core/LoadState.kt` 그대로.
- 액션형: `data class ActionState(inProgress, errorMessage, …)` — 화면을 갈아엎지 않고 인라인 `NoticeBanner(ERROR)` + 버튼 `loading`. **중복 제출은 `inProgress` 가드로 전부 차단**.
- 화면 전환은 ViewModel이 하지 않는다. VM은 `joined`/`left`/`matchingStarted` 같은 완료 플래그만 올리고 Route의 `LaunchedEffect`가 콜백을 부른다.
- 예외는 전부 `try-catch` 또는 `runCatching`, 코루틴은 `viewModelScope`.
- Hilt 미도입 유지 — `viewModel(factory = …)` + `AppContainer`에서 내려온 Repository.

---

## 4. 변경 파일

**신규 (7)**
```
presentation/.../feature/explore/JoinConfirmRoute.kt
presentation/.../feature/home/DestinationRoute.kt
presentation/.../feature/home/DestinationConfirmRoute.kt
presentation/.../feature/home/HomeRoute.kt
presentation/.../feature/matching/MatchWaitingRoute.kt
presentation/.../feature/chat/ChatRoute.kt
presentation/.../feature/chat/ChatListScreen.kt
```

**수정 (10)**
```
app/.../MainActivity.kt                      MainNavGraph 인자 4개로 확장
presentation/.../core/MainNavGraph.kt        place/chat/session 전달, Route 교체, Place·partyId 상태 배선
presentation/.../feature/explore/ExploreRoute.kt        재진입 refresh
presentation/.../feature/explore/JoinConfirmScreen.kt   joining·joinErrorMessage 파라미터화
presentation/.../feature/home/HomeScreen.kt             즐겨찾기 빈 상태 처리
presentation/.../feature/home/DestinationScreen.kt      검색·즐겨찾기·Place 기반 CTA
presentation/.../feature/home/DestinationConfirmModal.kt MatchConditions 콜백·creating·errorMessage
presentation/.../feature/matching/MatchWaitingScreen.kt  isHost·isReady·액션 콜백
presentation/.../feature/matching/RideDetailRoute.kt     leaveParty·userSession
presentation/.../feature/chat/ChatScreen.kt             상태 파라미터화, ChatUiMessage 개명
```

`Routes.kt`는 **변경 없음** — 새 화면(채팅 목록)이 기존 `Routes.CHAT` 안의 상태 전환이라 라우트가 늘지 않았다. data 모듈은 손대지 않았다.

---

## 5. 화면 진입 경로 (실기 확인용)

| 화면 | 경로 |
|---|---|
| 15 목적지 검색·즐겨찾기 | 홈 → 「목적지 검색」 바 |
| 16 방 생성 | 15에서 검색 결과 선택 → 「경로 확인하기」 → 「같이 탈 사람 찾기」 |
| 21 매칭 대기(준비/시작/나가기) | 16 방 생성 성공 직후 자동 진입 |
| 20 합류 확인 | 하단탭 합승 → 카드 「합류」 |
| 22 탑승 상세(나가기) | 21 조건 카드 탭 또는 20 합류 성공 후 |
| 24 채팅 목록 → 방 | 하단탭 채팅 → 방 행 탭 |

---

## 6. 미연결 · 보류 항목

1. **합류(join) 실패 경로만 동작** — 백엔드 매핑 부재. 성공 코드는 작성 완료라 서버 추가 시 `RemoteRideRepository.joinParty` 한 줄 수정으로 즉시 살아난다. (01 플래그 2)
2. **출발지 좌표가 고정값** — 위치 권한/GPS 미연동. `home/DestinationRoute.kt`의 `DemoOrigin`(부산대 정문 35.2313, 129.0838) 상수 하나로 모아뒀고, 실제 위치 연동 시 이 상수만 교체하면 된다. **방 생성이 좌표 필수라 우회 불가한 지점이었다.**
3. **반경 1km·2km 선택이 서버에서 500m로 clamp된다** — UI 칩은 와이어프레임대로 500m/1km/2km인데 서버 검증이 100~500m다. `coerceIn(100, 500)`으로 막았다. **UI 칩을 서버 범위에 맞춰 바꾸거나 서버 상한을 올리는 결정이 필요하다** (현재는 사용자가 2km를 골라도 500m로 나간다).
4. **동성만(sameGenderOnly) 토글이 서버로 안 나간다** — `OpenPartyRequest`에 해당 필드가 없다. `MatchConditions`에는 담아두고 전송 시 버린다.
5. **타 멤버 ready 표시 없음** — 01 플래그 6 그대로. 백엔드가 `MemberInfo`에 ready를 노출해야 가능.
6. **채팅 발신자가 "멤버 {id}"** — 닉네임 API 부재(01 플래그 7). 아바타도 공용 플레이스홀더.
7. **STOMP 미적용** — 3초 폴링 대체. 배터리·트래픽 관점에서 임시책이며, 백엔드 WebSocket Origin 허용 확인 후 교체 대상.
8. **최근 검색은 여전히 더미** — 서버에 검색기록 API가 없다(01 1-4). 탭하면 검색어만 채워 서버 검색을 태우도록 바꿔 최소한 죽은 UI는 아니게 했다.
9. **채팅방 생성(`createChatRoom`)·합류 시 방 참여(`joinChatRoom`)·메시지 삭제(`deleteMessage`) 미연결** — 대응하는 화면 동선이 와이어프레임에 없다. 합류 API가 살아나면 "합류 → joinChatRoom" 연결이 자연스러운 지점.
10. **경로 추천(`matching/routes`) 미구현** — 엔드포인트 부재(01 플래그 3). 지도/경로 카드는 계속 더미 렌더.
11. **채팅 목록 N+1** — Repository 내부 이슈(01 8절). 화면에서는 방 수만큼 지연이 누적될 수 있다.

---

## 7. 2차 QA 결함 수정 (04 보고서 대응, 2026-08-24 추가)

재검증: `./gradlew :app:assembleDebug --console=plain` **BUILD SUCCESSFUL**, 신규 경고 0건.

### F-1 [High] 로딩·에러 상태에서 하단 탭바 소실 — 해소

**지시대로 공용 Scaffold 리팩터링은 하지 않았다.** 화면 골격은 각 화면이 그대로 그리고,
Route 의 상태 분기 위치만 고쳐 콘텐츠 영역만 교체되도록 했다.

`presentation/core/LoadState.kt`에 골격 유지용 래퍼 2종 추가:

| 래퍼 | 유지하는 이동 수단 | 용도 |
|---|---|---|
| `TabStateScaffold(selectedTab, onTabSelect)` | `StatusBarMock` + **하단탭** | 탭 레벨 화면(합승·채팅) |
| `BackStateScaffold(title, onBack)` | `StatusBarMock` + **뒤로가기 앱바**(`MoyeotaTopBar`) | 탭바 없는 화면(20·21·22) |

둘 다 `Column`으로 골격을 잡고 콘텐츠만 `weight(1f)` 영역에 넣는다 — `LoadingBox`/`ErrorBox`의 `fillMaxSize`가 이 영역 안으로 갇히면서 탭바를 덮지 못한다.

**적용 (지시 대상 2개 + 훑어서 발견한 동류 3개):**

| 화면 | 수정 전 | 수정 후 |
|---|---|---|
| Explore (17) | `LoadingBox()` / `ErrorBox()` 전체 치환 | `TabStateScaffold(EXPLORE, onTabSelect){ … }` |
| Chat 목록 (24) | 동일 | `TabStateScaffold(CHAT, onTabSelect){ … }` |
| Chat 방 (24) | 동일 | `TabStateScaffold(CHAT){ BackStateScaffold(방 제목, onBack){ … } }` — Success 일 때 `ChatScreen`이 뒤로가기·탭바를 **둘 다** 갖고 있어 골격을 같게 맞췄다 |
| MatchWaiting (21) | 동일 | `BackStateScaffold("같이 탈 사람 찾는 중", onCancelSearch){ … }` |
| JoinConfirm (20) | 동일 | `BackStateScaffold("합류할까요?", onDismiss){ … }` |
| RideDetail (22) | 동일 | `BackStateScaffold("탑승 상세", onBack){ … }` |

지시받은 건 Explore·Chat 2개였지만, **탭바가 없는 화면 3개도 같은 결함의 다른 형태**였다.
이쪽은 유일한 이동 수단이 뒤로가기인데 에러 시 그마저 사라져 시스템 back 외에 탈출구가 없었다.

검증: `LoadingBox()`/`ErrorBox(` 호출부 12곳을 전수 grep — **전부 래퍼 안**에 들어간 것을 확인했다.

`HomeRoute`(즐겨찾기 실패를 흡수)와 `DestinationRoute`(인라인 에러)는 원래 골격을 유지하는 구조라 손대지 않았다.

### F-2 [Low] 좌표 없이 16 모달 진입 시 출발지 이름이 도착지 자리에 표시 — 해소

`DestinationConfirmRoute.kt` — 폴백 `destination ?: DemoOrigin`을 제거하고
`destinationName = destination?.name ?: "—"`, `destinationAddress = destination?.roadName ?: ""`로 바꿨다.
출발지 이름이 도착지 칸에 새는 경로가 없어졌다. 기존 안내 메시지와 `destination != null` 가드는 그대로 유지.

### 수정 파일 (6)
```
presentation/.../core/LoadState.kt                       TabStateScaffold·BackStateScaffold 추가
presentation/.../feature/explore/ExploreRoute.kt         F-1
presentation/.../feature/explore/JoinConfirmRoute.kt     F-1 동류
presentation/.../feature/chat/ChatRoute.kt               F-1 (목록·방 양쪽)
presentation/.../feature/matching/MatchWaitingRoute.kt   F-1 동류
presentation/.../feature/matching/RideDetailRoute.kt     F-1 동류
presentation/.../feature/home/DestinationConfirmRoute.kt F-2
```
화면 컴포저블(`ExploreScreen`·`ChatScreen` 등)은 **한 줄도 건드리지 않았다** — Route 의 상태 분기만 바뀌었다.

---

## 8. 경계면 관찰 (data 수정 없이 기록만)

- `Ride.hostId`는 `String?`, `UserSession.currentUserId`는 `Long`이라 방장 판단에서 `hostId == currentUserId.toString()` 문자열 비교를 한다. 서버가 hostId를 다른 포맷으로 내리면 조용히 false가 되어 **매칭 시작 버튼이 안 보이는 형태로 실패**한다 — 실기 검증 시 최우선 확인 대상.
- `createParty` 응답에 members 배열이 없어(01 3절) 21 매칭 대기는 생성 직후 `getPartyDetail`을 다시 읽는 구조에 의존한다.
