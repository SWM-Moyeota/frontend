# 61 · 채팅 목록 마지막 메시지 연동 (GET /chat-rooms/me `lastMessage`)

작성 2026-09-11. 백엔드 develop 커밋 `963c340`(PR #91 "채팅방 목록 조회에 마지막 메시지 포함") 기준.

## 서버 계약 (소스 확정)
`GET /api/v1/chat-rooms/me` → `List<ChatRoomUserResult>` 항목에 `lastMessage` 추가:
```
LastMessage(Long id, UUID senderPublicId, String content, ChatMessageType type /*TEXT|LOCATION*/, Instant createdAt)
```
- 방에 메시지가 없으면 `lastMessage: null`
- 삭제된 메시지는 서버가 content 를 「삭제된 메시지입니다」로 치환
- `senderPublicId` 는 발신자를 못 찾으면(탈퇴) null
- 조회는 `MAX(id) GROUP BY chatRoomId` 1쿼리 + 발신자 일괄 조회 — N+1 없음
- **여전히 없는 것**: 안읽음 개수(`unreadCount`), 참여자 닉네임 → 제목용 참여자 조회(N+1)는 그대로

## 앱 변경
| 계층 | 파일 | 내용 |
|---|---|---|
| domain | `model/Chat.kt` | `ChatLastMessage(id, senderPublicId, content, type, createdAt, isMine)`, `ChatRoomMembership.lastMessage`, `hasUnread`(남의 메시지 && 커서 < id) |
| data | `dto/ChatDtos.kt` | `ChatLastMessageResponse`, `ChatRoomUserResponse.lastMessage` (기본 null → 구서버 호환) |
| data | `ChatMappers.kt` | `toMembership(myUuid)` → `isMine = senderPublicId == 세션 uuid` (빈 값 방어) |
| data | `RemoteChatRepository.kt` | 목록 조회 시 세션 uuid 를 매퍼에 전달 |
| presentation | `ChatRoute.kt` | `chatRoomSortKey` = 마지막 메시지 시각(없으면 방 개설 시각) epoch ms; `toListTimeLabel`(오늘 HH:mm / 올해 M월 d일 / 그 전 yyyy.M.d) |
| presentation | `ChatListScreen.kt` | 부제 = 마지막 메시지 미리보기(LOCATION → 「📍 위치를 공유했어요」), 우측 시각, 안읽음 점 = `hasUnread`(예전엔 「커서 null」만) |

## 테스트
- `ChatMappersTest` +4: lastMessage 매핑/안읽음, 내 메시지면 안읽음 아님, 커서 닿음·메시지 없음, 발신자 없음
- `ChatRoomTitleTest` +3: 최근 대화 순 정렬, 메시지 없는 새 방 끼어들기, 부제 규칙
- `ChatTimeLabelTest` +5: 목록 시각 오늘/올해/작년/날짜 경계/파싱 실패

## 검증
- 빌드·data·presentation 단위 테스트 통과
- 배포 서버 실측: QA 계정(`uiqa0910`)의 **수동** 채팅방 생성 경로(POST /chat-rooms → join)는 서버가 `CHAT_ROOM_ALREADY_JOINED`(409) 와 `CHAT_NOT_PARTICIPANT`(403) 를 동시에 내고 `/me` 가 빈 배열 — 수동 생성 경로의 서버 상태 불일치로 보이며 앱은 이 경로를 쓰지 않는다(자동 생성). 백엔드에 별도 보고
- 실기(에뮬레이터, 실제 방 3개 보유 계정): 배포 서버(api.moyeota.p-e.kr)는 **아직 `lastMessage` 필드를 내려주지 않는다**(`hasField=false`, 백엔드 develop 963c340 미배포). 그 상태에서 앱은 기존 표시(제목 닉네임·부제 경로·시각 없음)로 정상 렌더, 정렬은 방 개설 시각 내림차순(8 → 2 → 1) 확인. 미리보기·시각·안읽음 점의 실기 확인은 **백엔드 배포 후** 필요
