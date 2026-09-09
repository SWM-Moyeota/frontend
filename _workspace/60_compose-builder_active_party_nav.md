# 60 · compose-builder — 진행 중 방 복귀 · 채팅↔매칭 내비게이션

입력: `_workspace/58_input_active_party_nav.md` · 경계면: `_workspace/59_api-integrator_active_party.md`.
범위: `presentation/` + `app/`(배선 2줄).

빌드 `:app:assembleDebug :presentation:testDebugUnitTest :data:testDebugUnitTest` **BUILD SUCCESSFUL**.
실기: 에뮬레이터 `emulator-5554`(Pixel_6) · 배포 서버 `https://api.moyeota.p-e.kr` · `smokep2`(앱) / `smokep1`(curl).

---

## 1. 사용자 신고에 대한 정확한 처방

> "매칭방 합류 → 정원 충족 → 매칭 → 채팅방 생성돼 채팅 가능하게 해놨는데,
>  뒤로가기로 채팅방에 접근하면 다시 매칭 진행 화면으로 돌아갈 수 없다."

원인은 **채팅 열기가 채팅 탭으로 이동(`navigateTab`)** 한 것이었다. 탭 이동은 `popUpTo(HOME) { saveState }` 로
스택을 갈아엎어 매칭 화면 엔트리 자체를 없앤다 — 뒤로가기가 돌아갈 대상이 사라진다.

**채팅방을 독립 목적지로 만들어 매칭 화면 위에 쌓았다**(`Routes.CHAT_ROOM = "chat/room/{roomId}"`).
뒤로가기 = 원래 화면 복귀. 채팅 탭 목록에서 여는 경로는 손대지 않았다(탭 안 상태 전환 그대로).

---

## 2. 변경 파일

### 신규 (1)

| 경로 | 내용 |
|---|---|
| `presentation/.../core/ActiveParty.kt` | `ActiveStage` enum + `Ride.activeStage`/`activeStageLabel` · `ActivePartyViewModel` · `ActiveRideBanner` · `OpenChatButton` |

### 수정 (16)

| 파일 | 내용 |
|---|---|
| `app/.../AppContainer.kt` | `activePartyRepository` 배선(59 리포트 5절 스니펫 그대로, ride/chat 선언 아래) |
| `app/.../MainActivity.kt` | `MainNavGraph(activePartyRepository = …)` |
| `presentation/.../core/Routes.kt` | `CHAT_ROOM` 상수 + `chatRoom(roomId)` 헬퍼 |
| `presentation/.../core/MainNavGraph.kt` | `ActivePartyViewModel` 배치 · `navigateToStage` · `openActiveChatRoom` · 앱 시작 단계 복귀 · 채팅방 목적지 등록 · remember/clear 호출 6곳 · 홈/합승/34 배선 |
| `presentation/.../feature/chat/ChatRoute.kt` | `ChatRoomDestinationRoute`(roomId 진입점) 추가 · `ChatRoute(activePartyId, onOpenMatching)` |
| `presentation/.../feature/chat/ChatScreen.kt` | 헤더 아래 「진행 중인 탑승이 있어요 / 매칭 화면으로 →」 배너(`onOpenMatching` null 이면 미표시) |
| `presentation/.../feature/matching/MatchWaitingScreen.kt`·`MatchWaitingRoute.kt` | 21 시트 푸터에 「채팅 열기」(`onOpenChat: (() -> Unit)?`) |
| `presentation/.../feature/matching/DispatchStageScreens.kt` | 25b·25c 에 「채팅 열기」 |
| `presentation/.../feature/matching/DispatchStatusScreen.kt`·`DispatchStatusRoute.kt` | 25 에 「채팅 열기」 |
| `presentation/.../feature/explore/ExploreScreen.kt`·`ExploreRoute.kt` | `hasOngoingRide: Boolean = true`(데모 기본값) → `activeRide: Ride? = null`. 하드코딩 「진행 중 탑승 · 서면역 방향」 배너 삭제 → 공용 `ActiveRideBanner` |
| `presentation/.../feature/home/HomeScreen.kt`·`HomeRoute.kt` | 지도 노출 영역 상단에 같은 배너(신규) |
| `presentation/.../feature/mypage/MyRidesScreen.kt` | 목업 파라미터 기본값 제거 · 카드를 실데이터로 재작성 · 「실시간 위치 보기」 → 「진행 상황 보기」 |

---

## 3. `navigateToStage` — 단계 판정과 스택 규칙

```
RECRUITING · MATCHED(서버 COMPLETED = 정원 충족)  → 21 MATCH_WAITING
DISPATCHING (driverId == null)                    → 25b ┐
DISPATCHING (driverId != null)                    → 25c·25 ┘ 같은 DISPATCH_STATUS 라우트가 갈아 끼운다
ONGOING                                            → 26 RIDE_ONGOING
그 외(COMPLETED·CANCELED)                          → 14 홈 + clear()
```

- **`MATCHED` 분기는 api-integrator 요청대로 넣었다**(59 리포트 4절 A). 이게 없으면 정원이 막 찬
  순간의 방이 「끝난 방」으로 취급돼 배너를 눌러도 홈으로 튄다.
- 스택: **이미 그 단계 화면이 스택에 있으면 `popBackStack` 으로 되돌아간다.** 없을 때만
  `popUpTo(HOME, inclusive=false)` 로 push — 「홈 위에 단계 화면 하나」. 채팅방에서 「매칭 화면으로 →」를
  눌렀을 때 같은 화면이 두 겹 쌓이지 않는다.

## 4. 「채팅 열기」를 언제 그리는가

`onOpenChat: (() -> Unit)?` 이 **null 이면 버튼 자체를 그리지 않는다.** 채팅방은 정원이 찬 뒤에 생기므로
21 모집 중에는 열 방이 없다 — 눌러도 아무 일 없는 버튼을 두는 대신 없앤다.

방 id → 채팅방 id 를 묻는 API 가 서버에 없어 `GET /chat-rooms/me` 를 훑는다(`ActivePartyViewModel.lookupChatRoom`).
`refresh()` 때마다, 그리고 **모집 중이 아닌 동안 채팅방을 아직 못 찾았을 때만** 10초 주기로 재시도한다
(`pollChatRoom`, NavHost 스코프 — 컴포지션이 사라지면 함께 멈춘다). 모집 중에는 아예 조회하지 않는다.

26 운행 중은 화면 레이아웃에 「채팅 열기」가 이미 박혀 있어 버튼을 숨길 수 없다 — 방 id 를 알면 쌓아 열고,
못 찾았으면 예전처럼 채팅 탭으로 떨어진다(길을 아예 막지는 않는다).

## 5. remember / clear 호출 지점 (전부 MainNavGraph)

| 시점 | 호출 |
|---|---|
| 16 방 생성 성공 (`onPartyCreated`) | `rememberParty(ride.id)` |
| 20 합류 성공 (`onJoined`) | `rememberParty(ride.id)` |
| 21 나가기 성공 (`onCancelSearch`) | `clearParty()` |
| 25b 타임아웃 CANCELED (`onRetryMatching`) | `clearParty()` |
| 26 운행 완료 FINISHED (`onRideFinished`) | `clearParty()` |
| `navigateToStage` 가 끝난 방을 받았을 때 | `clearParty()` + 홈 |
| 로그아웃·세션 만료 | `forget()` — **메모리만** 비운다. 로컬 기억은 계정에 매인 값이라 같은 계정 재로그인 시 되살아나야 한다 |

누락돼도 자기 치유된다: `resolve()` 가 매번 상세로 검증해 끝난 방이면 null + 기억 삭제(59 리포트 3절).
실제로 이번 실기에서 3분 타임아웃 CANCELED 를 **앱이 25b 를 보고 있지 않은 동안** 맞았는데,
재시작 시 배너·34 가 모두 스스로 비었다(§7 마지막 두 줄).

## 6. 재조회 시점

- 앱 시작: `init { refresh() }` → `resolvedOnce` 가 true 가 되는 순간 홈이면 `navigateToStage`
- 앱 포그라운드 복귀: NavHost 의 `LifecycleResumeEffect`
- 탭 진입: 홈·합승·34 각 `composable` 의 `LaunchedEffect(Unit)`
- 21 → 25 전이(`onMatchingStarted`), 25 → 26 전이(`onStartRide`) — 채팅방이 막 생기는 시점이라 같이 읽는다

## 7. 실기 확인

준비: 앱(`smokep2`)에서 2인 방 생성 → **partyId 25** → curl 로 `smokep1` 합류 → 정원 충족 →
서버가 **채팅방 8을 자동 생성**(배포 서버는 자동 생성한다 — curl `POST /chat-rooms` 불필요했다).

| 항목 | 결과 | 스크린샷 |
|---|---|---|
| 21 매칭 대기 (1/2) — 「채팅 열기」 **없음** (방이 아직 없다) | ✔ | `60_21_waiting_no_chat.png` |
| 합류 → 서버 `MATCHING` → 자동 25b 전이 + 「채팅 열기」 **등장** | ✔ | `60_25b_open_chat.png` |
| 25b 「채팅 열기」 → 채팅방이 **쌓여** 열림 + 헤더 「매칭 화면으로 →」 | ✔ | `60_chatroom_pushed.png` |
| **뒤로가기 → 25b 복귀** (원 신고 지점) | ✔ | `60_back_to_25b.png` |
| force-stop → 재실행 → **홈 대신 25b 직행** | ✔ | `60_restart_to_25b.png` |
| 홈 배너 「● 기사님 찾는 중 · 서면역 부산1호선 1번출구 · 보기 ›」 | ✔ | `60_home_banner.png` |
| 합승 탭 배너 동일 · 탭 → 25b | ✔ | `60_explore_banner.png` |
| 34 내 탑승 — 실데이터(단계·도착·출발·동승자 2명/정원 2명) + 「진행 상황 보기 ›」 → 25b | ✔ | `60_34_real_data.png` |
| 채팅 **탭** 목록 → 방 열기(기존 경로 유지) · 헤더 「매칭 화면으로 →」 | ✔ | — |
| 3분 타임아웃 CANCELED 후 재실행 → 홈, 배너 없음 | ✔ | — |
| 34 빈 상태 「진행 중·예정 탑승이 없어요」 (목업 7월 25일·3,200원 제거 확인) | ✔ | `60_34_empty.png` |
| FATAL / ANR | 0건 | — |

원본 스크린샷: `_workspace/screenshots/60_*.png` (세션 scratchpad 에도 v01~v23 로 남아 있다).

## 8. 확인 필요 · 미검증

1. **26 운행 중과 25(기사 배정 후)의 「채팅 열기」는 실기 미검증이다.** 기사 accept → board 까지 몰아야
   닿는 화면인데, 이번엔 3분 타임아웃 안에 거기까지 가지 못했다. 코드 경로는 25b/25c 와 **완전히 같다**
   (`DispatchStatusRoute.onOpenChat` 하나가 세 화면에 그대로 내려간다). 26 만 폴백 분기가 다르다
   (방 id 를 못 찾으면 채팅 탭) — 그 분기도 미검증.
2. **채팅 탭에서 방을 열었을 때 시스템 뒤로가기는 목록이 아니라 탭 밖으로 나간다.** 이번 작업이 만든 것이
   아니라 **기존 `ChatRoute` 구조의 문제**다 — 방 전환이 네비 목적지가 아니라 내부 상태(`openedRoom`)인데
   `BackHandler` 가 없어, 시스템 뒤로가기가 `CHAT` 목적지째 팝한다. 화면 안 「←」는 정상적으로 목록으로
   돌아간다. 새로 만든 **독립 목적지 경로는 시스템 뒤로가기가 정확히 원래 화면으로 돌아간다**(실기 확인).
   고치려면 `ChatRoute` 에 `BackHandler(openedRoom != null)` 를 넣으면 된다 — 이번 지시 범위 밖이라 두었다.
3. **`ActivePartyViewModel` 유닛 테스트가 없다.** presentation 모듈에 ViewModel 테스트 인프라(코루틴 테스트
   디스패처)가 아직 없어 기존 ViewModel 들도 테스트가 없는 상태다. resolve 자체는 data 쪽 11케이스로 덮여 있다.
4. **채팅방 조회 비용.** `getMyChatRooms()` 는 목록 1 + 방 수만큼 상세다. 진행 중 + 채팅방 미발견 상태에서
   10초마다 돈다. 서버에 `GET /matching/rooms/{partyId}/chat-room` 같은 게 생기면 그 한 방으로 줄일 수 있다
   — **백엔드 요청 후보**.
5. **`upcomingRides` 는 언제나 빈 목록이다.** 「예정 탑승」에 해당하는 개념이 서버에 없다(방은 만들어지는
   순간 진행 중이다). 34 의 「예정」 세그먼트는 지금 늘 비어 있다 — 세그먼트 자체를 없앨지 판단 필요.
6. 미검증: 회전, 프로세스 사망 후 복원(`startupStageHandled` 가 `rememberSaveable` 이라 저장 번들이 살아 있으면
   복귀를 건너뛴다 — 의도한 동작이지만 눈으로 확인하지 않았다), 계정 전환 후 배너.

## 9. 공용 자원 정리

- 테스트 방 **partyId 25** 는 서버가 3분 매칭 타임아웃으로 스스로 `CANCELED` 처리했다(내가 만든 방 정리 완료).
  `smokep1`·`smokep2` 둘 다 활성 파티 0건. 남은 `partyId 16`(산들마을6단지…, 1/3 ACTIVE)은 **내 것이 아니라
  건드리지 않았다** — 57 리포트에서도 다른 세션 것으로 확인된 방이다.
- 에뮬레이터: 승객 앱을 `smokep2` 로그인 상태로 두고 **종료**했다. 시작 시점에 이미 떠 있던 인스턴스라
  콜드부트를 새로 하지 않고 그대로 썼다(드라이버 앱이 포그라운드에 남아 있던 이전 세션의 잔여 상태).

---

# 부록 · 채팅 목록 제목·정렬 (같은 세션 후속 요청)

> 사용자: "채팅방이 시간순 정렬이 안 돼 있고, 방 이름이 출발지→도착지인데 참여자 닉네임으로 짓는 게 낫겠다"

## A-1. 왜 경로가 이름으로 못 쓰이는가

방 이름이 `"${departure} → ${destination}"` 이었는데, 16 도착지 확인이 좌표를 **역지오코딩한 전체 주소**를
그대로 방에 박는다. 실기 목록이 이랬다:

```
부산광역시 금정구 부산대학로63번길 2 부산대…   ← 잘림
부산광역시 부산진구 중앙대로 730 서면역 → 서   ← 잘림
```

한 줄에 안 들어가고, 들어가도 앞부분이 전부 "부산광역시 …"라 방을 **가려낼 수가 없다**.
사람 이름이 그 일을 한다. 경로는 버리지 않고 **부제로 내렸다**.

## A-2. 제목 규칙 — `chatRoomPeerTitle(members)`

| 나를 뺀 참여자 | 제목 |
|---|---|
| 1명 | `스모크일` |
| 2명 | `스모크일 · 스모크삼` |
| 3명 이상 | `스모크일 외 2명` (셋을 다 적으면 한 줄을 넘긴다) |
| 0명 | `동승자 없음` |

- **방을 나간 사람(`active=false`)은 뺀다.** 다만 그래서 아무도 안 남으면 **나간 사람이라도 쓴다** —
  대화 상대가 분명히 있었던 방을 「동승자 없음」이라 부르는 것이 더 틀렸다.
- 닉네임이 빈 문자열이면 `동승자`(기존 말풍선 폴백과 같은 말).
- **참여자 조회 실패 시 그 방만** 예전 제목(경로)으로 떨어진다. 목록 전체를 에러로 만들지 않는다.
  그때는 부제가 방 상태(`진행 중`/`종료된 방`)다 — 제목과 부제에 같은 문장을 두 번 적지 않는다.

## A-3. 정렬 — `chatRoomSortKey(item)` 한 곳

서버 `GET /chat-rooms/me` 는 `chatRoomId · lastReadMessageId · notificationMuted · joinedAt` 뿐이라
**마지막 메시지 시각이 없다.** 임시로 **방 id 내림차순**(= 개설 최신순)을 쓴다.

정직하게 말해 이건 「최근에 열린 방」이지 **「최근에 대화한 방」이 아니다.** 그래서 정렬 키를 호출부에
흩지 않고 함수 하나로 모았다 — 서버에 `lastMessageAt` 이 생기면 **이 함수만** 바꾸면 목록이 따라온다.

## A-4. N+1 은 허용하되 병렬

`getMyChatRooms()` 1회 + 방마다 `getChatRoomMembers(roomId)` 1회. 방은 사람당 많아야 몇 개라 허용하되
`coroutineScope { … async … awaitAll() }` 로 **동시에** 돈다 — 순차면 방 수만큼 왕복이 쌓여 목록이 늦게 뜬다.

## A-5. 채팅방 헤더도 같은 규칙

`ChatRoomViewModel.peerTitle` 을 `onScreenStart` 에서 한 번 읽는다(참여자는 방이 열려 있는 동안 거의
바뀌지 않아 폴링하지 않는다). 제목 = 닉네임, 부제 = 경로. 못 받았으면 제목 = 경로, 부제 = 「메시지 N개」.
늦게 온 응답이 그 사이 바뀐 방의 제목을 덮어쓰지 않도록 `chatRoomId != roomId` 면 버린다.

`ChatRoomDestinationRoute`(21·25·26 의 「채팅 열기」)도 같은 `ChatRoomRoute` 를 쓰므로 그대로 따라온다.

## A-6. 변경 파일 (3 + 테스트 1)

| 파일 | 내용 |
|---|---|
| `presentation/.../feature/chat/ChatRoute.kt` | `ChatRoomListItem` · `chatRoomPeerTitle` · `chatRoomSortKey` · `ChatListViewModel.loadRooms()`(병렬) · `ChatRoomViewModel.peerTitle` · 헤더 제목·부제 |
| `presentation/.../feature/chat/ChatListScreen.kt` | `List<ChatRoomListItem>` 수신 · 행 제목/부제 재작성 · 말줄임 |
| `presentation/src/test/.../chat/ChatRoomTitleTest.kt` | **신규 8케이스** — 1·2·3명 이상·0명 · 나간 사람 제외 · 전원 나감 폴백 · 빈 닉네임 · 정렬 키 |

## A-7. 검증

- `./gradlew :app:assembleDebug :presentation:testDebugUnitTest` **BUILD SUCCESSFUL** ·
  presentation 유닛 **30건 전부 통과**(신규 8건 포함)
- **실기**(에뮬레이터 Pixel_6 콜드부트 · 배포 서버 · `smokep2`, 방 8·2·1 보유):

| 항목 | 결과 | 스크린샷 |
|---|---|---|
| 목록 제목이 닉네임 (`스모크일` / `스모크` / `스모크일`) · 부제가 경로 | ✔ | `60b_chatlist_nickname_title.png` |
| 정렬 = 방 id 내림차순 (8 → 2 → 1) | ✔ | 〃 |
| 방 헤더 제목 = 닉네임 · 부제 = 경로 | ✔ | `60b_room_header_after.png` |
| FATAL / ANR | 0건 | — |

## A-8. 실기에서 잡은 것 — 헤더 부제가 우측 아이콘을 덮었다

부제가 「메시지 N개」에서 **경로(전체 주소)**로 바뀌자 헤더 텍스트가 **두 줄로 흘러 🔍·⋮ 아이콘 위를
덮었다**(`60b_room_header_overflow_before.png`). 원인은 텍스트 Column 이 폭을 안 잡고 있던 것 —
짧은 부제일 때는 드러나지 않던 문제다.

`Column(Modifier.weight(1f))` + 제목·부제 `maxLines = 1, overflow = Ellipsis` 로 고쳤다
(뒤따르던 `Spacer(weight(1f))` 는 고정 8dp 간격으로 바꿨다). 수정 후 한 줄씩 말줄임되고 아이콘이 산다.

**이건 코드만 봐서는 못 잡았을 결함이다** — dp 계산이 아니라 실기 스크린샷이 잡았다(55 리포트의 21 안내
문구 잘림과 같은 종류).

## A-9. 확인 필요

1. **정렬은 「최근 대화」가 아니라 「최근 개설」이다.** 서버에 `lastMessageAt`(또는 마지막 메시지 요약)이
   추가되면 `chatRoomSortKey` 를 그걸로 바꿔야 사용자가 기대하는 시간순이 된다 — **백엔드 요청 대기**
   (리더가 별도로 올린다고 했다).
2. **참여자 조회가 방 수만큼 늘어난다.** 지금은 병렬이라 체감이 없지만, 방이 수십 개가 되면
   `GET /chat-rooms/me` 응답에 참여자 닉네임이 함께 오는 편이 맞다 — 위 요청과 같이 묶을 후보.
3. **미검증**: 3명 이상 방(「외 N명」)과 참여자 조회 실패 폴백은 실기에서 재현하지 못했다(유닛 8건으로만 덮었다).
   종료된 방(`종료 · 경로` 부제)도 실기 미확인 — 배포 서버의 방 3개가 전부 `ACTIVE` 였다.
