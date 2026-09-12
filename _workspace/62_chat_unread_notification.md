# 62 · 채팅 안읽음 개수 + 메시지 푸시 알림 연동

작성 2026-09-12. 백엔드 develop `8a059fc` 기준 (PR #96 chat-notification `bbb4b8f`, PR #97 unread-message-count `7c95683`).

## 서버 계약 (소스 확정)
1. `GET /api/v1/chat-rooms/me` 항목에 **`unreadCount: Long`** 추가 — 남이 보낸 · 삭제 안 된 · `id > COALESCE(lastReadMessageId, 0)` 메시지 수. 방마다 1쿼리(GROUP BY). 없으면 0.
2. **알림 음소거** `POST /api/v1/chat-rooms/{id}/users/notification/mute` (끄기) / `DELETE` 같은 경로 (켜기). 둘 다 200 본문 없음. 참여자 아니면 403 `CHAT_NOT_PARTICIPANT`. 상태는 목록의 `notificationMuted` 로 읽는다(단독 조회 API 없음).
3. **FCM 데이터 푸시** (`FcmChatNotifier`, priority HIGH, notification 페이로드 없음):
   `{ type:"CHAT_MESSAGE", chatRoomId, messageId, senderPublicId, senderNickname, preview }`
   — preview 는 30자 + "...", LOCATION 은 「위치를 공유했습니다」. 수신자 = 방 참여자 중 나가지 않았고 · 음소거 안 했고 · 발신자가 아닌 사람. `ChatMessageSentEvent` AFTER_COMMIT + REQUIRES_NEW 리스너.

## 앱 변경
| 계층 | 파일 | 내용 |
|---|---|---|
| domain | `Chat.kt` | `ChatRoomMembership.unreadCount: Int?`; `hasUnread` = 개수가 오면 `> 0` 이 유일한 근거, 없으면(구서버) 마지막 메시지 어림 |
| domain | `ChatRepository.kt` | `setNotificationMuted(chatRoomId, muted)` |
| data | `ChatDtos.kt` / `ChatMappers.kt` | `unreadCount: Long? = null`(구서버 호환) → Int, 음수는 0 |
| data | `ChatApi.kt` / `RemoteChatRepository.kt` | `muteNotification`(POST) / `unmuteNotification`(DELETE) |
| presentation | `ChatListScreen.kt` | 안읽음 배지 숫자(`unreadBadgeLabel`: 99+ 상한, 개수 모름이면 점) |
| presentation | `ChatRoute.kt` (ChatRoomViewModel) | `muted` 상태 — 방 열 때 `/me` 에서 읽음, `toggleMuted()` 낙관적 갱신 + 실패 시 롤백·에러 문구 |
| presentation | `ChatScreen.kt` | 24a 메뉴 「채팅방 알림 끄기/켜기」 서버 토글 (예전엔 끄기만 있는 로컬 상태), 부제 「알림 꺼짐」 |
| presentation | `ChatForeground.kt` (신규) | 지금 보고 있는 방 id — ChatRoomRoute 가 START/STOP 에 기록 |
| app | `MoyeotaFirebaseMessagingService.kt` | `CHAT_MESSAGE` → 제목 발신자 닉네임 · 본문 preview · **방 단위 알림 id**(같은 방은 갱신) · 보고 있는 방이면 생략 · 탭 시 `EXTRA_CHAT_ROOM_ID` |
| app | `MoyeotaApplication.kt` | 채팅 전용 채널 `moyeota_chat`(도착 알림과 분리해 시스템 설정에서 따로 끌 수 있게) |
| app | `MainActivity.kt` | `onCreate`(savedInstanceState == null 일 때만)/`onNewIntent` 에서 extra 읽어 `pendingChatRoomId` |
| presentation | `MainNavGraph.kt` | `pendingChatRoomId` 가 오면 로그인 상태에서 `Routes.chatRoom(id)` 를 **스택에 쌓고** 소비. 이미 맨 위면 재쌓기 없음. 미로그인이면 무시 |

## 테스트
- `ChatMappersTest` +2 (unreadCount 우선·0, 구서버 null·음수)
- `RemoteChatRepositoryTest` +2 (mute/unmute 분기, 403 → ChatException)
- `UnreadBadgeLabelTest` +3

## 검증
- 빌드·data·presentation 단위 테스트 전부 통과
- 배포 서버(api.moyeota.p-e.kr) 실측 2026-09-12 22:1x: `/chat-rooms/me` 에 `unreadCount`·`lastMessage` 모두 실려 온다(백엔드 배포 완료)
- 실기 (Pixel 6 API 35, 실제 방 3개 계정):
  - 목록: 1번 방 「shape probe」 미리보기 · 「9월 9일」 · **파란 숫자 배지 2** · 미리보기 진하게. 메시지 없는 방은 경로 부제 유지
  - 음소거: 8번 방 ⋮ → 「채팅방 알림 끄기」 → 부제에 「· 알림 꺼짐」, 서버 `notificationMuted=true` 확인 → 「채팅방 알림 켜기」 → false 복귀 확인
  - 딥링크: `am start … --el com.moyeota.app.extra.CHAT_ROOM_ID 8` 로 앱 콜드 스타트 → 8번 채팅방이 바로 열림(홈 위에 쌓임, 뒤로가기 → 홈)
  - **미검증**: 실제 FCM `CHAT_MESSAGE` 수신 → 알림 표시 → 탭 (다른 계정이 이 계정 방에 메시지를 보내야 한다). 코드 경로는 기존 `DRIVER_ARRIVED` 와 같은 `show()` 이며, 탭 이후 경로는 위 딥링크로 검증됨
