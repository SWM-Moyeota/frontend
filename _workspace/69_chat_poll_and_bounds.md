# 69 · 빈 채팅방 폴링 정지 · 합승 마커 깜빡임

작성 2026-09-15. 실기 영상 피드백 2건.

## 1. 매칭 직후 채팅이 상대에게 안 보임 (나갔다 들어오면 됨)

`ChatRoomViewModel.startPolling` 이 이랬다.

```kotlin
val cursor = lastMessageId ?: continue   // ← 여기
```

`lastMessageId` 는 `markLastAsRead(messages.lastOrNull()?.id)` 로만 채워진다. 그래서 **메시지가 하나도 없는 방**
(= 매칭 직후 자동 생성된 방)은 커서가 영영 null 이고, 폴링 루프가 매 주기 `continue` 만 하다 끝났다.
상대가 보낸 **첫 메시지**를 영영 못 받는다. 나갔다 들어오면 `load()` 가 첫 페이지를 다시 읽으며 그 메시지가
들어오고, 그때 커서가 잡혀 그 뒤로는 정상 동작한다 — 사용자가 본 「나갔다 오면 잘 된다」가 이것이다.

**첫 시도(실패)**: `lastMessageId ?: 0L`. 서버가 `cursor < 1` 을 400 `CHAT_INVALID_CURSOR` 로 막아
`/messages/after?cursor=0` 이 계속 400 이었다(실기 logcat 확인).

**수정**: 커서가 없으면 `after` 대신 **첫 페이지를 다시 읽는다**.
```kotlin
if (cursor == null) repository.getMessages(roomId) else repository.getMessagesAfter(roomId, cursor)
```
첫 메시지가 들어오면 커서가 잡혀 다음 주기부터는 가벼운 `after` 조회로 돌아간다.

## 2. 확대·축소 중 합승 마커가 사라졌다 나타남

서버는 **출발지가 조회 영역 안**인 방만 돌려준다. 앱은 지도 카메라가 멈출 때마다 `contentBounds`
(가시 영역에서 시트·배너에 가린 부분을 뺀 영역)로 다시 조회한다. 그래서 경계에 걸친 방이 카메라가 조금만
움직여도 목록에서 빠졌다 들어오며 마커가 깜빡였다.

**수정**: 조회 영역을 가시 영역보다 **사방 30%** 넓힌다(`MapBounds.expanded`). 경계가 화면 밖에 있어
눈에 띄지 않는다. 지도 앱이 뷰포트보다 넓게 미리 받아 두는 것과 같은 방식이다.

## 검증
- `MapBoundsExpandTest` 3건 (확장·비례·극단값 클램프) 통과, 빌드·presentation 테스트 통과
- 실기(에뮬레이터 + 배포 서버): 계정 둘로 파티를 채워 **빈 채팅방**을 만들고 앱에서 연 채로 대기 →
  상대가 보낸 첫 메시지가 **나가지 않고** 화면에 뜸 → 두 번째 메시지도 즉시 반영.
  logcat 으로 폴링이 `after?cursor=29` → `cursor=30` 으로 넘어가는 것까지 확인
