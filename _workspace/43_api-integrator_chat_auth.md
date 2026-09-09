# 43 · api-integrator — 채팅 인증(@CurrentUser) 전환 연동

작성 2026-09-07. 입력: `_workspace/42_input_chat_auth.md`. 대상 모듈 `data/`·`domain/`(+ `app/AppContainer` 배선).

## 요약

채팅 API 에서 `X-User-Id` 헤더를 완전히 제거하고 주체를 Bearer 토큰 하나로 넘겼다. 그 대가로 생긴 "내 메시지 판정" 공백은 **전송 응답에서 내 내부 PK 를 학습**하는 인터림 전략으로 메웠고, 서버가 `senderPublicId` 를 주기 시작하면 코드 변경 없이 그쪽이 우선하도록 매퍼를 짜 뒀다.

- `:data:testDebugUnitTest` **통과** (15개 클래스 172 테스트, 실패 0)
- `:app:assembleDebug` **통과** — compose-builder 의 `ChatRoute` 수정이 이미 반영돼 경계면이 맞았다(아래 참조)
- **실서버 미검증** — 백엔드 프로세스를 건드리지 말라는 지시에 따라 curl 실측을 하지 않았다. 스펙 근거는 전부 백엔드 소스(아래 경로)다. 실기 검증은 qa-verifier 몫.

## 백엔드 스펙 재확인 (소스 기준, 커밋된 워킹트리)

`backend/src/main/java/team/codingforest/moyeota/chat/` 를 직접 읽어 42 문서와 대조했다 — **불일치 없음**.

| 메서드 | 경로 | 주체 | 응답 |
|---|---|---|---|
| GET | `/api/v1/chat-rooms/me` | `@CurrentUser` | `List<ChatRoomUserResult>` |
| GET | `/api/v1/chat-rooms/{id}` | `@CurrentUser`(파라미터만 받고 미사용) | `ChatRoomResult` |
| POST | `/api/v1/chat-rooms` | `@CurrentUser`(미사용) | 201 `ChatRoomResult` |
| DELETE | `/api/v1/chat-rooms/{id}` | `@CurrentUser`(미사용) | 204 |
| POST/DELETE | `/api/v1/chat-rooms/{id}/users` | `@CurrentUser` | 201 / 200, 본문 없음 |
| POST | `/api/v1/chat-rooms/{id}/users/read/{readMessageId}` | `@CurrentUser` | 200, 본문 없음 |
| GET | `/api/v1/chat-rooms/{id}/messages?cursor&size=30` | `@CurrentUser` | `ChatMessageSlice` |
| GET | `/api/v1/chat-rooms/{id}/messages/after?cursor&size=30` | `@CurrentUser` | `ChatMessageSlice` |
| POST | `/api/v1/chat-rooms/{id}/messages` | `@CurrentUser` | 201 `ChatMessageResult` |
| DELETE | `/api/v1/chat-rooms/{id}/messages/{messageId}` | `@CurrentUser` | 204 |
| GET | `/api/v1/chat-rooms/{id}/messages/search?keyword&cursor&size=30` | `@CurrentUser` | `ChatMessageSlice` (**신규 연동**) |

응답 shape (`chat/app/dto/`):
- `ChatMessageResult(id, chatRoomId, userId, content, type, createdAt, deleted)` — `userId` 는 **서버 내부 PK**. `senderPublicId`·`senderNickname` **없음**(앱은 관용 필드로 미리 열어 뒀다).
- `ChatMessageSlice(messages, nextCursor, hasNext)`
- `ChatRoomResult(id, partyId, departure, destination, createdAt, status)`
- `ChatRoomUserResult(chatRoomId, lastReadMessageId, notificationMuted, joinedAt)`

에러 (`ChatExceptionHandler` → `ErrorResponse(code, message)`):
`code` 가 **`ChatErrorCode` enum 이름 그대로**다(`CHAT_NOT_PARTICIPANT` 403, `CHAT_ROOM_CLOSED` 409, `CHAT_ROOM_NOT_FOUND` 404, `CHAT_NOT_MESSAGE_OWNER` 403, `CHAT_UNAUTHORIZED` 401, `CHAT_INVALID_KEYWORD` 400 …). 검증 실패만 `INVALID_REQUEST`. **인증 도메인의 `USER1xx` 번호 체계와 다르므로 `toAuthException()` 의 매핑을 재사용할 수 없다** — 본문 shape 만 같아 `ApiErrorDto` 를 공유한다.
`search` 의 keyword 는 2자 미만이면 400 `CHAT_INVALID_KEYWORD`.

`search` 쿼리 파라미터 이름은 `keyword`/`cursor`/`size` 로 컨트롤러 실소스 확인.

## "내 메시지" 판정 — 구현한 전략

서버가 발신자를 내부 PK 로만 표시하고 앱은 어떤 API 에서도 자기 내부 PK 를 받지 못한다(전 도메인이 publicId/UUID 기준). 기존 코드는 고정값 1 과 비교하고 있어 **1번이 아닌 계정에선 내/상대 말풍선이 통째로 뒤집혔다.**

`RemoteChatRepository` 가 다음을 한다:

1. `sendMessage` 응답의 `userId` 를 내 내부 PK 로 **학습**(`learnedInternalId`), 학습 시점의 세션 uuid 를 함께 보관(`learnedForUuid`).
2. 매 판정마다 `session.currentUserUuid == learnedForUuid` 일 때만 학습값을 쓴다. **로그아웃(uuid null)·계정 전환 시 자동으로 무효화** — authState 를 코루틴으로 구독하지 않는다(저장소가 스코프를 갖지 않아도 되고, 읽는 시점 비교가 구독보다 어긋날 여지가 적다).
3. 판정 우선순위: `senderPublicId`(오면 세션 uuid 와 비교) → 학습한 내부 id → 전부 false.
4. `userId <= 0` (DTO 기본값)은 학습하지 않는다 — 그러면 남의 메시지가 전부 내 것이 된다.

**남는 한계 (화면 안내 없음, 의도된 동작):** 이번 세션에 아직 메시지를 보내지 않았다면 **내 과거 메시지도 상대 스타일로 보인다.** 한 번 보내면 그 시점부터, 재조회한 과거 메시지까지 교정된다. 서버가 `senderPublicId` 를 추가하면 첫 진입부터 정확해지고 학습 캐시는 삭제 가능해진다.

## 변경 파일

**domain/**
- `model/ChatError.kt` **(신규)** — `ChatException(code, serverMessage, httpStatus, isNetwork, cause)`. 화면이 문자열을 비교하지 않도록 `isNotParticipant`·`isRoomClosed`·`isRoomNotFound`·`isNotMessageOwner`·`isUnauthorized` 를 노출.
- `model/Chat.kt` — `ChatMessage` 에 `isMine: Boolean`, `senderName: String?` 추가(기본값 없음 — 생성 지점이 매퍼 하나뿐이라 누락을 컴파일러가 잡게).
- `repository/ChatRepository.kt` — userId 파라미터 전부 제거, `searchMessages` 추가.
- `session/UserSession.kt` — **`currentUserId`·`FIXED_MEMBER_ID` 삭제.** 앱에는 이제 서버 내부 사용자 PK 가 존재하지 않는다.

**data/**
- `remote/ChatApi.kt` — 전 메서드 `@Header("X-User-Id")` 제거, `searchMessages` 추가, KDoc 을 현재 상태로 교체.
- `remote/dto/ChatDtos.kt` — `ChatMessageResponse` 에 `senderPublicId: String? = null`, `senderNickname: String? = null`.
- `remote/ChatMappers.kt` — `ChatIdentity(myUuid, myInternalId)` 도입, `toChatMessage(identity)`/`toPage(identity)`, `toChatException()`·`chatCall {}` 추가(`authCall` 패턴).
- `repository/RemoteChatRepository.kt` — 생성자에 `session: UserSession`, 학습 캐시, 전 호출을 `chatCall` 로 감쌈.
- `session/SessionManager.kt` — `currentUserId` 생성자 파라미터 제거.

**app/**
- `AppContainer.kt` — `RemoteChatRepository(apis.chat, sessionManager)`. `NetworkModule` 은 변경 불필요(chat 은 이미 Bearer 가 붙는 `apiClient` 쪽 retrofit 으로 생성된다).

**DummyChatRepository 는 존재하지 않는다** — 채팅은 구현체가 `RemoteChatRepository` 하나뿐이라 동기화 대상 없음.

## 최종 Repository 시그니처 (compose-builder 경계면)

```kotlin
suspend fun getMyChatRooms(): List<MyChatRoom>
suspend fun getChatRoom(chatRoomId: Long): ChatRoom
suspend fun createChatRoom(partyId: Long, departure: String, destination: String): ChatRoom
suspend fun closeChatRoom(chatRoomId: Long)
suspend fun joinChatRoom(chatRoomId: Long)
suspend fun leaveChatRoom(chatRoomId: Long)
suspend fun markAsRead(chatRoomId: Long, readMessageId: Long)
suspend fun getMessages(chatRoomId: Long, cursor: Long? = null, size: Int = 30): ChatMessagePage
suspend fun getMessagesAfter(chatRoomId: Long, cursor: Long, size: Int = 30): ChatMessagePage
suspend fun sendMessage(chatRoomId: Long, content: String): ChatMessage   // 부수효과: 내 내부 id 학습
suspend fun deleteMessage(chatRoomId: Long, messageId: Long)
suspend fun searchMessages(chatRoomId: Long, keyword: String, cursor: Long? = null, size: Int = 30): ChatMessagePage
```

도메인 모델: `ChatMessage(id, chatRoomId, senderId, content, type, createdAt, deleted, isMine, senderName)`.
실패: 전부 `ChatException`. 화면 분기는 `isNotParticipant`(403) / `isRoomClosed`(409) 프로퍼티 사용.

**compose-builder 와 실제로 맞았다** — `ChatRoute.kt` 가 이미 새 시그니처(`getMyChatRooms()`, `sendMessage(chatRoomId, content)` 등)와 `ChatException.isNotParticipant`/`isRoomClosed` 를 쓰고 있어 `:app:assembleDebug` 가 그대로 통과했다.

## 테스트

`./gradlew :data:testDebugUnitTest` → **BUILD SUCCESSFUL, 172 tests / 0 failures** (`--rerun-tasks` 로 전량 재컴파일·재실행 확인).

- `RemoteChatRepositoryTest` **(신규, 11)** — 학습 전 `isMine=false` / 전송 후 같은 발신자 `isMine=true`(보낸 메시지 자신 포함) / 전송 본문에 사용자 id 없음 / `senderPublicId` 우선 / **로그아웃 후 캐시 초기화 + 다른 계정 재로그인에도 되살아나지 않음** / search 파라미터 전달 / 조회 기본값(cursor null, size 30) / 상세 실패 방 제외 / 403·409·code 없는 404 의 `ChatException` 변환.
- `ChatMappersTest` (14, +6) — 학습 전/후 판정, publicId 우선, 빈 publicId 는 없는 것으로 간주, 빈 닉네임 → null, **`senderPublicId` 가 없는 현재 서버 응답 JSON 도 그대로 역직렬화**.
- `AuthenticatedPathContractTest` (14, +2) — `ChatApi` 에 **`@Header`/`@HeaderMap` 이 하나도 없음**(리플렉션), 채팅 경로 6개 고정. 기존 "전환된 API 에 memberId/userId 파라미터 없음" 검사 대상에 `ChatApi` 추가(채팅 예외 문구 제거).
- 회귀 수정: `SessionManagerTest`(고정 memberId 테스트 → "UUID 만 복원한다"로 교체), `RemoteRideRepositoryTest`·`FcmTokenRegistrarTest` 의 페이크 세션에서 `currentUserId` 오버라이드 제거.

> 참고: MockWebServer 는 이 저장소에 의존성이 없다(`data/build.gradle.kts` testImplementation = junit + coroutines-test). 기존 저장소 테스트가 전부 **Fake API 객체** 패턴이라 그쪽을 따랐다. 헤더 미전송은 페이크로는 검증되지 않는 계층이라 **`AuthenticatedPathContractTest` 의 리플렉션 검사**로 못 박았다 — Retrofit 애노테이션이 실제 진실의 출처이므로 오히려 이쪽이 정확하다.

## 백엔드 요청 (우선순위 순)

1. **`ChatMessageResult` 에 `senderPublicId`(UUID) 추가** — 최우선. 지금 앱은 발신자를 내부 PK 로만 받아 "내 메시지"를 구조적으로 알 수 없고, 위 학습 전략은 그 공백을 임시로 메운 것이다(세션 중 첫 전송 전까지 내 과거 메시지가 상대로 보이는 한계가 남는다). 파티 `members` 가 이미 publicId 로 같은 문제를 푼 전례가 있다. 앱은 관용 필드를 이미 열어 뒀으므로 **서버 배포만으로 즉시 정확해진다**(앱 변경 0).
2. **`ChatMessageResult` 에 `senderNickname` 추가** — 현재 앱은 상대 이름을 "동승자"로 폴백한다. 방 안에 3인 이상이면 누가 말하는지 구분이 안 된다. 역시 관용 필드 준비됨.
3. **내부 `userId` 노출 제거 권장** — 1·2 가 들어오면 `ChatMessageResult.userId` 는 앱에서 쓸 일이 없다. 내부 PK 를 클라이언트에 흘리지 않는 다른 도메인 원칙과 어긋나므로 함께 정리 권장. `ChatRoomLeftEvent.userId`(STOMP `/user/queue/room-left`)도 동일 — 앱이 그 값으로 "나갔다"를 판정할 수단이 없다.
4. (참고) 실시간 수신은 여전히 폴링이다. STOMP(`/ws-chat`, CONNECT 네이티브 헤더 `Authorization: Bearer`) 는 앱에 WebSocket/STOMP 라이브러리가 없어 이번 범위 밖 — 별도 과제로 남긴다.

## qa-verifier 교차 검증 요청

- 요청에 `X-User-Id` 가 **실제로 나가지 않는지** logcat(OkHttp BODY 로깅) 확인.
- 계정 A 로그인 → 방 진입 → **보내기 전** 과거 메시지가 전부 상대 스타일인지(의도된 동작) → A 가 1건 전송 → 그 메시지와 이후 A 의 메시지가 내 스타일로 전환되는지.
- B(curl)로 같은 방에 메시지 전송 → A 화면에서 상대 스타일 유지.
- **계정 전환 회귀**: A 로그아웃 → B 로 로그인 → 같은 방에서 A 의 메시지가 내 스타일로 잘못 뜨지 않는지(학습 캐시 오염 방지 검증).
- 403(참여 안 한 방)·409(종료된 방) 문구 노출.
- 회귀: 채팅 탭 목록(`/chat-rooms/me` + 방 상세 N+1), 읽음 처리, 나가기.
- `searchMessages` 는 **화면이 없어 실기 검증 대상이 아니다**(계약만 열어 둔 상태).
