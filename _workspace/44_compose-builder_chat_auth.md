# 44 · compose-builder — 채팅 화면을 `@CurrentUser` 계약에 맞춤

작성 2026-09-07. 입력: `_workspace/42_input_chat_auth.md`, 경계면: 43 리포트는 작성 시점에 아직 파일로 없어 domain 소스(`ChatRepository`·`Chat.kt`·`ChatError.kt`·`UserSession.kt`)와 `RemoteChatRepository` 를 직접 읽어 맞췄다.

## 변경 파일
| 파일 | 변경 |
|---|---|
| `presentation/.../feature/chat/ChatRoute.kt` | `UserSession` 의존 전면 제거 · Repository 새 시그니처 호출 · `isMine`/`senderName` 사용 · 채팅 에러 문구 매핑 |
| `presentation/.../feature/chat/ChatScreen.kt` | 파라미터 기본값이 Preview 전용임을 주석으로 명시 (동작 변경 없음) |
| `presentation/.../core/MainNavGraph.kt` | `userSession` 파라미터·전달 제거(`MainNavGraph`·`MainNavHost`·`ChatRoute` 호출부) |
| `app/.../MainActivity.kt` | `MainNavGraph(userSession = …)` 인자 제거 |

## 결정
1. **`userSession` 제거 범위** — `ChatListViewModel`·`ChatRoomViewModel` 생성자, `ChatRoute`/`ChatListRoute`/`ChatRoomRoute` 파라미터에서 제거했다. presentation 전체에서 `UserSession` 을 쓰는 화면이 채팅뿐이었으므로(`RideDetailRoute` 는 이미 매퍼 판정으로 내려간 상태) `MainNavGraph` 의 파라미터까지 지우고 `MainActivity` 호출부를 정리했다. 세션 자체는 `AppContainer` → `RemoteChatRepository` 로만 흐른다.
2. **내/상대 판정** — `toUiMessage()` 는 `message.isMine` 을 그대로 신뢰한다. 상대 이름은 `senderName ?: "동승자"`(상수 `PEER_FALLBACK_NAME`). 내 메시지는 이름을 붙이지 않는다(기존 말풍선 규칙 유지). "멤버 {id}" 표기와 그 근거 주석은 삭제했다.
3. **학습 전 상태 안내 없음** — 서버가 `senderPublicId` 를 주기 전까지 내가 이 세션에서 첫 메시지를 보내기 전의 내 과거 메시지는 `isMine=false` 로 온다. 42 지침대로 화면은 그냥 상대 말풍선으로 그리고 별도 배너·안내를 넣지 않았다.
4. **에러 문구** — `ChatException` 의 의미 프로퍼티로 분기한다(코드 문자열 비교를 화면에 두지 않음).
   - `isNotParticipant`(403 `CHAT_NOT_PARTICIPANT`) → "이 채팅방에 참여하고 있지 않아요"
   - `isRoomClosed`(409 `CHAT_ROOM_CLOSED`) → "종료된 채팅방이에요"
   - 그 외 실패는 자리별 기본 문구 유지: 목록 "채팅방 목록을 불러오지 못했어요" · 대화 "대화를 불러오지 못했어요" · 전송 "메시지를 보내지 못했어요" · 나가기 "채팅방에서 나가지 못했어요".
   매핑은 목록 로드·대화 로드·전송·나가기 네 곳에 모두 적용했다(폴링·읽음처리는 기존대로 조용히 실패).
5. **방 제목/부제** — 제목은 서버 `ChatRoom` 실값 `"{departure} → {destination}"`(Loading/Error/Success 3상태 동일), 부제는 `"메시지 N개"`. `ChatScreen` 의 기본값 `"서면역 동승"`·더미 대화는 `@Preview` 에서만 쓰이며 그 사실을 주석으로 못박았다. 채팅 **목록** 줄도 `ChatListScreen` 에서 같은 출발→목적지 실값을 쓴다(확인만).
6. **그대로 둔 것** — 폴링 주기 3초, `getMessagesAfter` 커서 방식, 읽음 처리 정책(실패 무시), 방↔목록 전환 `rememberSaveable`, Route/Screen 분리, 하단탭 유지(QA F-1) 골격.

## 화면 진입 경로
하단탭 「채팅」(`Routes.CHAT`) → 방 목록 → 방 선택 → 대화(같은 라우트 내 전환, 뒤로가기로 목록 복귀). 나가기 성공 시 14 홈.

## 빌드
`./gradlew :app:assembleDebug --console=plain` — **BUILD SUCCESSFUL** (2026-09-07, api-integrator 의 data/domain 반영본 기준).

## 미해결 / 후속
- **첫 전송 전 내 과거 메시지 오표시**: 백엔드가 `ChatMessageResult.senderPublicId`(+`senderNickname`) 를 주면 자동 해소. 그전까지는 재진입 직후 내 지난 메시지가 상대처럼 보인다(42 에 명시된 인터림 한계).
- **발신자 이름**: 서버가 닉네임을 주지 않아 상대는 전부 "동승자" 로 보인다. `senderNickname` 추가 시 코드 변경 없이 실제 이름이 뜬다.
- **읽음 배지/실시간**: STOMP 미도입 — 폴링 유지. WebSocket 도입은 별도 범위.
- 채팅방 검색(`searchMessages`)은 Repository 계약만 열려 있고 화면 없음.

---

## §결함 수정 (QA 45 리포트 대응 · 2026-09-07)

### 추가·수정 파일
| 파일 | 변경 |
|---|---|
| `presentation/.../core/MainNavGraph.kt` | `TabRoutes` 상수 + `resetAfterSignOut()` — 로그아웃/세션 만료 시 저장된 탭 백스택까지 폐기 |
| `presentation/.../feature/chat/ChatRoute.kt` | `ChatRoomViewModel` 을 roomId 교체형 단일 인스턴스로 · 폴링을 수명주기에 묶음 · 재진입 재조회 · 시각 표기 시간대 변환 · 나간 방 자동 재오픈 방지 |
| `presentation/src/test/.../feature/chat/ChatTimeLabelTest.kt` | **신규** — 시각 표기 5케이스 |

### 결함-1 (높음) — 로그아웃 뒤 이전 계정 폴링 잔존
두 갈래로 막았다.
- **(a) 저장된 탭 상태 폐기**: `resetTo` 는 그대로 두고 로그아웃 전용 `resetAfterSignOut(route)` 를 만들어, `resetTo` 직후 `TabRoutes(HOME·EXPLORE·CHAT·MYPAGE)` 각각에 `navController.clearBackStack(route)` 를 호출한다. `popUpTo(0){inclusive}` 가 못 지우는 `saveState=true` 보관분(ViewModelStore·rememberSaveable 포함)이 여기서 사라진다. 세션 만료 경로도 같은 `LaunchedEffect(loggedIn)` 을 타므로 함께 해소된다. `navigateTab` 의 `saveState/restoreState` 는 유지했다 — 탭 왕복 시 홈 지도 카메라 등을 보존하는 기존 의도(주석 참조)를 로그아웃 때만 무효화하는 편이 안전하다.
- **(b) 폴링을 화면 가시성에 묶음**: `ChatRoomViewModel` 이 `pollingJob` 을 들고 `onScreenStart(roomId)` / `stopPolling()` 으로만 시작·중단한다. Route 는 `LifecycleStartEffect(room.id) { viewModel.onScreenStart(room.id); onStopOrDispose { viewModel.stopPolling() } }` 로 구동 — 탭 이동·백그라운드·이탈에서 즉시 멈춘다. `init { }` 자동 시작은 제거했다.

### 결함-2 (중간) — 방마다 VM 누적·중복 폴링·재진입 무갱신
구조까지 바꿨다. `viewModel(key = "chat-room-{roomId}")` → **고정 key `"chat-room"` 단일 인스턴스**가 `onScreenStart(roomId)` 로 방을 갈아탄다(방 변경 시 폴링 중단·진행 중 로드 취소·`lastMessageId`/입력/나가기 신호 초기화). 스토어에 VM 이 쌓이지 않으므로 동시 폴링이 원천적으로 불가능하다.
재진입 시에는 매번 `load()` 가 다시 돈다 — 이미 대화가 떠 있으면 `showLoading=false` 로 조용히 갱신해 스피너 깜빡임 없이 「내 메시지」 학습 결과가 교정된다. 조용한 갱신이 실패해도 화면을 에러로 갈아치우지 않는다(다음 폴링/다음 진입에서 회복). 에러 화면 재시도는 `retryLoad()`.
부수 정리: 방을 나가면 `openedRoom` 을 먼저 비운다 — 그러지 않으면 채팅 탭에 돌아왔을 때 이미 나간 방을 다시 열어 403 문구가 뜬다.

### 결함-3 (낮음) — 시각이 UTC로 표시
`toTimeLabel(zone: ZoneId = ZoneId.systemDefault())` 로 바꿔 `Instant.parse` → 실패 시 `OffsetDateTime.parse` 순으로 파싱하고 기기 시간대의 `HH:mm` 을 만든다. 둘 다 실패하는 형식(오프셋 없는 `LocalDateTime` 등)은 기존처럼 문자열을 잘라 쓴다. `zone` 파라미터는 테스트가 기기 시간대와 무관하게 검증하기 위한 것이다.

### 검증
- `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL
- `./gradlew :presentation:testDebugUnitTest` — BUILD SUCCESSFUL (`ChatTimeLabelTest` 5/5, `DigitGroupVisualTransformationTest` 12/12)
- 실기 재검증은 qa-verifier 몫(서버·에뮬레이터 미조작).

### 남은 것
- 방 화면이 별도 네비게이션 목적지가 아니라 `ChatRoute` 내부 상태 전환이라는 점은 그대로다(단일 VM 로 누적 문제는 해소). 딥링크·시스템 뒤로가기 일관성을 위해 목적지 분리는 후속 과제.
- 폴링은 여전히 3초 REST — STOMP 도입 시 이 수명주기 훅(`onScreenStart`/`stopPolling`)이 그대로 구독/해지 지점이 된다.
