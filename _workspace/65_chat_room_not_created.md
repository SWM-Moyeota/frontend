# 65 · 매칭 채팅방이 생성되지 않던 원인 (백엔드 스키마)

작성 2026-09-13. 증상: 정원이 차도 채팅방이 없고, 21·25b·25c·26 의 「채팅 열기」 버튼이 아예 안 뜸.

## 조사
배포 서버 실측 (에뮬레이터 세션 토큰으로 직접 조회):
- party 42 (IN_RIDE, 모여터 + 스모크이) · 41 · 43 — **채팅방 없음**
- 채팅방 id 1~29 전수 조회: 마지막 자동 생성은 **room 13 / party 33 (2026-09-10 12:14 UTC)**. 이후 생성된 room 15~17 은 9/11 에 내가 curl 로 수동 생성한 것
- 같은 계정으로 2인 파티를 새로 채워 재현 → 양쪽 모두 `/chat-rooms/me` 가 빈 배열

로컬 재현:
- H2(local 프로필) → **정상 생성**. 프로덕션과 차이는 Postgres + Flyway + `ddl-auto: validate`
- 로컬 Postgres 에 V1~V3 를 적용한 새 DB(`moyeota_repro`) + dev 프로필로 재현 → **실패 재현**

```
ERROR: null value in column "joined_at" of relation "chat_room_user" violates not-null constraint
매칭 채팅방 생성 실패 partyId=1
```

## 원인
`V2__chat_room_column_convention.sql` 이 `joined_at` → `created_at` 으로 옮기며 엔티티에서 `joined_at` 매핑을 뺐는데, `V1` baseline 의 `joined_at ... not null` 이 남아 **chat_room_user 의 모든 INSERT 가 실패**. `ddl-auto: validate` 는 엔티티에 없는 여분 컬럼을 검사하지 않아 기동은 정상이었다.

연쇄 증상:
- `ChatRoomMatchingListener` 의 `REQUIRES_NEW` 트랜잭션이 첫 참여자 INSERT 에서 실패 → **방 생성까지 롤백**
- 수동 참여는 409 `CHAT_ROOM_ALREADY_JOINED` (`join` 이 `DataIntegrityViolationException` 을 그렇게 매핑 — 스키마 오류가 업무 오류로 둔갑)
- 그 방의 메시지 전송은 403 `CHAT_NOT_PARTICIPANT`
- 앱은 `/chat-rooms/me` 에서 partyId 로 채팅방을 찾는데 목록이 비어 있어 `activeChatRoomId` 가 null → **「채팅 열기」 버튼을 그리지 않음**(버튼 코드는 이미 있다)

## 수정 (백엔드 PR #98)
```sql
ALTER TABLE chat_room_user ALTER COLUMN joined_at DROP NOT NULL;
```

## 검증
V4 적용 후 로컬 Postgres 에서 재검증:
- 정원 충족 → 채팅방 자동 생성, 두 멤버 모두 active 참여자
- 메시지 전송 201, `/chat-rooms/me` 에 `unreadCount=1` · 마지막 메시지 반영
- **앱 실기**(에뮬레이터, `MOYEOTA_BASE_URL=http://10.0.2.2:8081/` 빌드): 25b 「기사님 찾는 중」에 **「채팅 열기」 표시** → 탭 → 채팅방(제목 「에뮬나」, 「매칭 화면으로 →」) → 뒤로가기 → 25b 복귀

## 남은 것
- **기존 파티는 소급 복구되지 않는다** — 리스너가 예외를 삼켜 이벤트가 「완료」로 기록되어 재시도가 없다. 배포 후 새로 만드는 파티부터 정상
- `ChatRoomUserService.join` 의 `DataIntegrityViolationException` → `ALREADY_JOINED` 무조건 매핑은 좁혀야 한다
- 나간 참여자(`left_at`) 재참여 불가도 같은 매핑에 걸려 있다(별건)
