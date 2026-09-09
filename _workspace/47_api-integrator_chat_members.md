# 47 · api-integrator — 채팅 참여자 목록 연동 (보낸 사람 닉네임 · 내 메시지 판정)

입력: `_workspace/46_input_chat_members.md`. 브랜치 `feature/mvp1-followup`. 2026-09-08.

## 연동한 엔드포인트

| 메서드 | 경로 | 응답 | 근거 |
|---|---|---|---|
| GET | `/api/v1/chat-rooms/{chatRoomId}/users` | `List<ChatRoomMemberResult>` | 컨트롤러 소스 `chat/presentation/ChatRoomUserController.getMembers` + `chat/app/dto/ChatRoomMemberResult`(리더 패치본) |

응답 shape (패치본 기준):

```json
[{ "userId": 1, "publicId": "uuid", "nickname": "자동에이", "imageUrl": null, "active": true }]
```

- `userId` 는 리더 패치로 추가된 필드다. **패치 전 서버는 이 필드를 주지 않는다** → DTO 는 `Long? = null` 로 관용하고, null 이면 메시지의 내부 PK 와 이을 키가 없어 기존 학습 폴백으로 동작한다.
- `active=false` 는 방을 나간 사람. 목록에서 빼지 않는다(그 사람이 남긴 메시지의 이름 유지).
- 참여자가 아니면 403 `CHAT_NOT_PARTICIPANT`(기존 `ChatException` 매핑 그대로 흡수).

**실서버 검증: 미검증(플래그).** 8080 은 떠 있고 이 경로는 `401 {"code":"USER005"}` 를 돌려주는데, 이 서버의 Security 는 미매핑 경로도 401 로 막으므로 401 만으로는 매핑 여부·`userId` 필드 존재를 확정할 수 없다. 토큰을 얻으려면 계정 생성/로그인으로 서버 상태를 건드려야 해서 하지 않았다 — **실기 확인은 qa 몫**(패치본 서버에서 A 앱/B curl).

## 변경 파일

| 파일 | 내용 |
|---|---|
| `data/remote/dto/ChatDtos.kt` | `ChatMemberResponse(userId: Long? = null, publicId, nickname, imageUrl, active)` 추가 |
| `data/remote/ChatApi.kt` | `getMembers(chatRoomId): List<ChatMemberResponse>` 추가(헤더 없음, `@Path` 는 chatRoomId 하나) |
| `data/remote/ChatMappers.kt` | `ChatMemberResponse.toChatMember(myUuid)` 추가. `ChatIdentity` 에 `members: Map<Long, ChatMember> = emptyMap()` 추가. `toChatMessage` 의 isMine·senderName 결정에 참여자 사전 반영 |
| `data/repository/RemoteChatRepository.kt` | 방별 참여자 캐시 + 재조회 전략(아래) |
| `domain/model/Chat.kt` | `ChatMember` 추가, `ChatMessage.isMine/senderName` 주석 갱신 |
| `domain/repository/ChatRepository.kt` | `getChatRoomMembers` 추가 |
| `data/src/test/.../ChatMappersTest.kt` | +8 |
| `data/src/test/.../RemoteChatRepositoryTest.kt` | +8 (FakeChatApi 에 `getMembers`·호출 횟수·중간 합류 시나리오) |
| `data/src/test/.../AuthenticatedPathContractTest.kt` | 경로·GET·`@Path` 1개 고정 (+1) |

**presentation 무변경.** `ChatRoute` 는 `isMine`/`senderName` 만 읽으므로 손대지 않았다.

## 시그니처 (compose-builder 참고용, 화면 변경 불필요)

```kotlin
// domain/model/Chat.kt
data class ChatMember(
    val userId: Long?,      // 서버 내부 PK. 구버전 서버에서는 null → 메시지와 이을 수 없다
    val publicId: String,
    val nickname: String,
    val imageUrl: String?,
    val active: Boolean,    // false = 방을 나간 사람(목록에 남는다)
    val isMe: Boolean,
)

// domain/repository/ChatRepository.kt
suspend fun getChatRoomMembers(chatRoomId: Long): List<ChatMember>
```

`ChatMessage` 의 필드는 그대로다(추가·삭제 없음) — 바뀐 건 값이 채워지는 정확도뿐이다.

## 판정 우선순위 (매퍼)

1. 메시지 `senderPublicId` ↔ 세션 uuid (서버가 아직 안 주지만 오면 최우선)
2. 참여자 사전(`userId` → 참여자)의 `publicId` ↔ 세션 uuid, 닉네임은 `member.nickname`
3. 학습한 내부 id ↔ `userId` (사전에 없는 발신자일 때만)

`senderName` 은 `senderNickname` → 사전 닉네임 → null. 사전 히트 시에도 캐시된 `isMe` 대신 `publicId` 를 그 자리에서 다시 비교한다(캐시가 다른 계정 시절 것이어도 오염되지 않게).

## 캐시 전략 (RemoteChatRepository)

- 방별 `Map<Long, ChatMember>`(userId 키) + `unresolved: Set<Long>`. `userId` 가 null 인 참여자는 사전에 못 넣지만 `getChatRoomMembers` 반환 목록에는 포함한다.
- **초기 로드**(`getMessages` 의 `cursor == null`)에 참여자 목록을 먼저 받는다. 실패하면 삼키고 메시지 로드는 계속(학습 폴백). 취소(`CancellationException`)만 통과시킨다.
- `getMessagesAfter`/`sendMessage`/`searchMessages`/과거 페이징에서 **사전에 없는 발신자**가 나오면 그 로드에서 **한 번만** 재조회(중간 합류자 대비).
- 재조회로도 못 찾은 id 는 `unresolved` 에 적어 다시 묻지 않는다. 이게 없으면 `userId` 를 안 주는 구버전 서버에서 **폴링마다** 참여자 목록을 부르게 된다. 단, 조회 자체가 실패한 경우(네트워크)는 적지 않는다 — 다음 로드에서 다시 시도한다.
- 계정 경계: 캐시 소유 uuid 를 함께 보관해, 읽는 시점에 세션 uuid 와 다르거나 null 이면(로그아웃/계정 전환) 캐시를 통째로 버린다. 기존 `learnedForUuid` 가드와 같은 방식.

## 테스트 결과

`./gradlew :data:testDebugUnitTest :app:assembleDebug` — **BUILD SUCCESSFUL**, 실패 0.
(ChatMappersTest 14→22, RemoteChatRepositoryTest 12→20, AuthenticatedPathContractTest 14→15)

커버한 케이스: 참여자 캐시로 isMine/senderName 결정 · 학습 없이 재진입해도 내 과거 메시지 isMine=true · `active=false` 참여자 이름 유지 · `userId` null(패치 전 서버) 학습 폴백 · 참여자 조회 실패해도 메시지 로드 계속 · 새 userId 등장 시 1회만 재조회 · 못 찾은 발신자 폴링 재조회 금지 · 계정 전환 캐시 초기화 · `getChatRoomMembers` 가 userId 없는 참여자까지 반환 · 경로/동사/`@Path` 계약.

## 남은 백엔드 요청

1. **(장기·권장) 메시지에 `senderPublicId`·`senderNickname` 을 실어 달라.** 그러면 참여자 목록 왕복도, 내부 PK 노출(`ChatMessageResult.userId`)도 없앨 수 있다. 앱 매퍼는 이미 이 필드를 최우선으로 읽으므로 **백엔드 배포만으로** 즉시 정확해지고, 그 시점에 참여자 캐시와 학습 폴백을 함께 걷어낼 수 있다.
2. `ChatRoomMemberResult.userId` 패치(리더 패치본)의 **develop 반영**. 반영 전 서버에서는 이번 연동이 사실상 무동작이고 기존 학습 폴백만 남는다.
3. 참고: 앱은 방 진입마다 메시지 + 참여자 2회를 부른다. 방 상세(`GET /chat-rooms/{id}`)에 참여자를 함께 실어 주면 1회로 줄고, 채팅 목록의 N+1(`/me` 가 방 이름을 안 줌)도 같이 정리된다.
