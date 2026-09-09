# 42 · 입력 — 채팅 인증(@CurrentUser) 전환 연동

작성 2026-09-07 (리더). 백엔드 `feature/user-fcm-token` 워킹트리 — 채팅 인증 전환은 PR #72(feature/chatroom-auth, 2026-09-07 13:46 머지)로 커밋됨.

## 사용자 요청 (원문)
"그 채팅쪽도 그 user쪽 수정해서 한번 확인해서 연동해줘"

## 백엔드 변경 계약 (소스 확정)

### REST — 전 엔드포인트 `X-User-Id` 헤더 제거, `@CurrentUser Long userId`(Bearer 토큰 주체)
| 메서드 | 경로 | 비고 |
|---|---|---|
| GET | `/api/v1/chat-rooms/me` | `List<ChatRoomUserResult(chatRoomId, lastReadMessageId, notificationMuted, joinedAt)>` |
| GET/POST/DELETE | `/api/v1/chat-rooms[/{id}]` | `ChatRoomResult(id, partyId, departure, destination, createdAt, status)` |
| POST/DELETE | `/api/v1/chat-rooms/{id}/users` | join / leave (204) |
| POST | `/api/v1/chat-rooms/{id}/users/read/{readMessageId}` | 읽음 |
| GET | `/api/v1/chat-rooms/{id}/messages?cursor&size=30` · `/after?cursor&size` | `ChatMessageSlice(messages, nextCursor, hasNext)` |
| POST | `/api/v1/chat-rooms/{id}/messages` `{content ≤1000}` | `ChatMessageResult` |
| DELETE | `/api/v1/chat-rooms/{id}/messages/{messageId}` | 본인만(403 CHAT_NOT_MESSAGE_OWNER) |
| GET | `/api/v1/chat-rooms/{id}/messages/search?keyword(≥2)&cursor&size` | **신규** — 이번 범위에선 Repository 메서드만 추가(화면 없음) |

`ChatMessageResult(id, chatRoomId, userId, content, type, createdAt, deleted)` — **userId 는 내부 PK**. 삭제된 메시지는 content="삭제된 메시지입니다", deleted=true.
에러: `ErrorResponse(code, message)` — CHAT_NOT_PARTICIPANT 403, CHAT_ROOM_CLOSED 409, CHAT_UNAUTHORIZED 401, CHAT_ROOM_NOT_FOUND 404 등(ChatErrorCode).

### STOMP (이번 범위 밖 — 폴링 유지, 후속 제안)
`/ws-chat` 엔드포인트, CONNECT 네이티브 헤더 `Authorization: Bearer <access>`, 구독 `/sub/chat-rooms/{id}`(참여자만), 발행 `/pub/...`, 개인 큐 `/user/queue/errors`·`/user/queue/room-left`, 하트비트 10s. 앱에 STOMP/WebSocket 라이브러리 없음.

## 문제 — "내 메시지" 판정
앱은 세션에 **publicId(UUID)만** 있고 내부 Long id 를 모른다(어떤 API도 내부 id 를 주지 않음). 현재 앱은 `senderId == FIXED_MEMBER_ID(1)` 로 판정 — 계정이 바뀌면 전부 틀린다.

**백엔드 요청(리포트에 명시)**: `ChatMessageResult`에 `senderPublicId`(UUID)·`senderNickname` 추가, 내부 `userId` 노출 제거 권장(파티 members 와 동일 원칙). `ChatRoomLeftEvent.userId`도 동일.

**앱 인터림 전략(지금 구현)**: 내가 `POST .../messages` 로 보낸 응답의 `userId` 를 **내 내부 id 로 학습**해 세션 동안 기억(RemoteChatRepository 내부 캐시). 이후 모든 메시지 `isMine = (senderPublicId 가 있으면 == 세션 uuid) else (senderId == 학습한 내부 id)`. 학습 전에는 전부 상대 메시지로 표시(리포트에 한계 명시). 서버가 `senderPublicId` 를 추가하는 순간 자동으로 정확해진다.

## 프론트 계약 (리더 확정)

```kotlin
// domain/model/Chat.kt — ChatMessage 에 추가
val isMine: Boolean            // 위 전략으로 리포지토리가 계산
val senderName: String?        // 서버 senderNickname(있으면). 없으면 null → 화면은 "동승자"

// domain/repository/ChatRepository.kt — userId 파라미터 전부 제거
suspend fun getMyChatRooms(): List<MyChatRoom>
suspend fun joinChatRoom(chatRoomId: Long)
suspend fun leaveChatRoom(chatRoomId: Long)
suspend fun markAsRead(chatRoomId: Long, readMessageId: Long)
suspend fun getMessages(chatRoomId: Long, cursor: Long? = null, size: Int = 30): ChatMessagePage
suspend fun getMessagesAfter(chatRoomId: Long, cursor: Long, size: Int = 30): ChatMessagePage
suspend fun sendMessage(chatRoomId: Long, content: String): ChatMessage
suspend fun deleteMessage(chatRoomId: Long, messageId: Long)
suspend fun searchMessages(chatRoomId: Long, keyword: String, cursor: Long? = null, size: Int = 30): ChatMessagePage

// domain/session/UserSession.kt — currentUserId · FIXED_MEMBER_ID **삭제** (채팅이 유일한 소비처였음). SessionManager 생성자 파라미터도 제거.
// data: ChatApi 전 메서드에서 @Header("X-User-Id") 제거, search 추가. ChatMessageResponse 에 senderPublicId: String? = null, senderNickname: String? = null (백엔드 추가 대비 관용 필드).
// RemoteChatRepository(api, session: UserSession) — 학습 캐시 + isMine/senderName 계산. 로그아웃(authState Unauthenticated) 시 캐시 초기화.
// 채팅 에러: ErrorResponse(code,message) 파싱 → ChatException(code, message) 도메인 예외(기존 방식이 있으면 그대로). 403 CHAT_NOT_PARTICIPANT · 409 CHAT_ROOM_CLOSED 는 화면 문구로.
```

## 작업 범위
- **api-integrator**: 위 data/domain 전부 + KDoc(ChatApi·UserSession·ChatRepository 의 X-User-Id 설명 정리) + 테스트(`RemoteChatRepositoryTest` 신규/갱신: 헤더 미전송, isMine 학습 전/후, senderPublicId 우선, 로그아웃 캐시 초기화; `AuthenticatedPathContractTest` 채팅 경로 Bearer 필수). 리포트 `_workspace/43_api-integrator_chat_auth.md`.
- **compose-builder**: `ChatRoute.kt` 에서 `userSession.currentUserId` 의존 제거, `toUiMessage` 가 `isMine`/`senderName`("동승자" 폴백) 사용, 학습 전 상태 안내 없음(그냥 상대 스타일). ChatScreen 방 제목은 ChatRoom departure→destination 실값(이미 그렇다면 확인만). 403/409 문구. 리포트 `_workspace/44_compose-builder_chat_auth.md`.
- **qa-verifier**: 빌드·테스트, 경계면 교차 비교, 실기: 계정 A 로그인 → 방 생성 → B(curl) 합류 → 채팅방 자동 생성 여부(백엔드 chat-room-auto-create) 확인 → A 앱에서 메시지 전송 → B curl 로 메시지 전송 → A 화면에서 내/상대 구분 정확(A가 먼저 보낸 뒤), 읽음 처리, 나가기. 회귀: 채팅 탭 목록. 리포트 `_workspace/45_qa-verifier_chat_auth.md`.

## 환경
- 백엔드 QA는 **8081**(리더가 워킹트리로 기동 중). 8080 은 사용자 프로세스(구버전 가능) — 건드리지 말 것. 앱 빌드 `MOYEOTA_BASE_URL=http://10.0.2.2:8081/`.
- emulator-5554 부팅되어 있음(이전 QA 계정은 8081 재기동으로 소멸 — 재시드).
