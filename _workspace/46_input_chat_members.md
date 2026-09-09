# 46 · 입력 — 채팅 참여자 목록 API 연동 (보낸 사람 닉네임·내 메시지 판정 완성)

작성 2026-09-08 (리더). 백엔드 origin/develop 기준 + 리더 패치(`ChatRoomMemberResult.userId` 추가, scratchpad/chat-member-userid.patch).

## 배경
사용자가 `GET /api/v1/chat-rooms/{chatRoomId}/users` (참여자 목록) 를 추가했다(origin/feature/chatroom-members → develop 머지).
응답 `List<ChatRoomMemberResult(publicId, nickname, imageUrl, active)>` — 참여자만 조회 가능(403 CHAT_NOT_PARTICIPANT), `active=false` 는 나간 사람.
그러나 메시지 `ChatMessageResult.userId` 는 내부 PK라 **둘을 이을 키가 없었다** → 리더가 `ChatRoomMemberResult` 에 `userId`(Long, 메시지 userId 와 동일 값)를 추가(백엔드 패치, 사용자 반영 대기 — QA 서버는 패치본으로 기동).

## 계약
`GET /api/v1/chat-rooms/{chatRoomId}/users` → `[{ "userId": 1, "publicId": "uuid", "nickname": "자동에이", "imageUrl": null, "active": true }, ...]`
(패치 전 서버는 `userId` 가 없으므로 DTO 는 `userId: Long? = null` 관용 — null 이면 학습 폴백으로 동작)

## 프론트 계약 (리더 확정)
```kotlin
// domain/model/Chat.kt
data class ChatMember(val userId: Long?, val publicId: String, val nickname: String, val imageUrl: String?, val active: Boolean, val isMe: Boolean)
// ChatRepository
suspend fun getChatRoomMembers(chatRoomId: Long): List<ChatMember>
// ChatMessage.isMine / senderName 결정 순서 (RemoteChatRepository):
//  1) 방 참여자 캐시(userId→member)에 보낸 사람이 있으면: isMine = member.publicId == 세션 uuid, senderName = member.nickname
//  2) 없으면 기존 인터림(학습한 내부 id) → isMine, senderName = null
// 캐시: 방별 Map<Long, ChatMember>. getMessages(초기 로드) 시 참여자 목록을 먼저(또는 병렬) 조회해 채우고, sendMessage 응답·getMessagesAfter 에서 모르는 userId 가 나오면 1회 재조회(새 참여자). 로그아웃/계정 전환 시 캐시 초기화(learned 와 같은 uuid 가드).
```
화면 변경 없음(ChatRoute 는 isMine/senderName 만 쓴다). 선택: 방 헤더에 "참여자 N명" — 이번 범위 밖.

## 검증
- 유닛: 참여자 캐시 매핑, active=false 도 이름 표시(나간 사람 메시지), userId null(패치 전 서버) 폴백, 계정 전환 캐시 초기화.
- 실기(qa): 패치본 서버(8080) — A 앱 / B curl. A 재시작 후 재진입해도 **학습 없이** A 과거 메시지가 내 말풍선, B 메시지에 B 닉네임 표시, B 가 나간 뒤에도 B 메시지 이름 유지.
