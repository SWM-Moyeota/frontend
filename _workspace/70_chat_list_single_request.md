# 70 · 채팅 목록 1회 요청 (백엔드 변경 반영)

작성 2026-09-15. 백엔드 develop `9ab388a` 기준.

## 백엔드에서 바뀐 것
- `69e991e` 목록 응답에 **출발지·도착지·상태** 포함
- `c0cbe7a` 목록 응답에 **참여자 목록** 포함 (`members: [{publicId, nickname, imageUrl, active}]`)
- `623359f` 읽음 처리 **STOMP 경로 추가** (`/pub/chat-rooms/{id}/read`) — 기존 REST 는 그대로 남아 있어 앱은 영향 없음
- `b0febc3` 내가 올린 `chat_room_user.joined_at` NOT NULL 해제 머지됨

## 앱 반영
`GET /chat-rooms/me` 한 번으로 목록이 완성된다. 예전에는 방마다 `GET /chat-rooms/{id}`(이름) + `/users`(참여자)를
더 불러 **요청 1+2N 회**였고 방 상세는 순차라 방 3개에 5초 가까이 걸렸다(실측).

| 계층 | 변경 |
|---|---|
| DTO | `ChatRoomUserResponse` 에 `departure·destination·status·members` (전부 nullable → 구서버 호환) |
| 매퍼 | `toChatRoomOrNull()` — 방 정보가 오면 목록 항목만으로 `ChatRoom` 생성. 출발·도착 중 하나라도 없으면 null |
| 도메인 | `ChatRoomMembership.members: List<ChatMember>` |
| 저장소 | `getMyChatRooms` 가 항목별로 병렬 매핑, 방 정보가 없을 때만 상세를 읽는다. 목록에 온 참여자는 **사전에 캐시**해 방 진입 시 `/users` 재조회를 줄인다 |
| 목록 화면 | 참여자가 다 있으면 추가 조회 없이 제목 생성. 아니면 예전 병렬 폴백 |
| 방 화면 | 음소거 상태를 목록에서 받아 넘겨(`knownMuted`) 방을 열 때 목록 API 재호출 제거 |

## 검증
- 빌드 · `:data:testDebugUnitTest`(`ChatRoomListMappingTest` 3건 신규) · `:presentation:testDebugUnitTest` 통과
- 실기(에뮬레이터 + **아직 배포 전인** 서버): 폴백 경로가 정상 동작 — `/me` + `/{id}` + `/{id}/users`. 목록·미리보기·시각 정상
- 서버 배포 후에는 `/me` 1회로 줄어드는 것을 재확인해야 한다

## 주의 — 동승자 위치 API 가 바뀌었다
내가 올린 백엔드 PR #100(`/matching/rooms/{id}/location`)은 **닫혔고**, 팀원분이 `feature/real-time-location-share`
브랜치에 **채팅 모듈 기준**으로 다시 만들었다(아직 develop 미머지).

| 용도 | 새 API |
|---|---|
| 공유 시작·종료 | `POST` / `DELETE /api/v1/chat-rooms/{chatRoomId}/location/sharing` |
| 백그라운드 발행 | `POST /api/v1/chat-rooms/{chatRoomId}/location` |
| 포그라운드 발행 | STOMP `/pub/chat-rooms/{id}/location` |
| 스냅샷 | STOMP `/pub/chat-rooms/{id}/location/sync` → `List<ChatLocationResult>` |

앱의 26 운행 중 동승자 마커(PR #29)는 지금 없는 경로(`/matching/rooms/{id}/locations`)를 부르고 있어
**그 브랜치가 머지되면 앱도 갈아타야 한다.** 스냅샷이 STOMP 전용이라 그때 WebSocket 클라이언트가 필요해진다.
