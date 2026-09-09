# 59 · api-integrator — 진행 중 방 복귀 + 채팅 발신자 publicId 전환

범위: `domain/`·`data/` 만. **presentation/·app/ 무수정**.

---

# 1. 진행 중 방 복귀(ActivePartyRepository)

입력: `_workspace/58_input_active_party_nav.md`.

### 1. 새 파일

| 경로 | 내용 |
|---|---|
| `domain/src/main/kotlin/com/moyeota/domain/repository/ActivePartyRepository.kt` | 도메인 계약 3메서드 |
| `data/src/main/kotlin/com/moyeota/data/local/ActivePartyStorage.kt` | `RememberedParty(partyId, ownerUuid)` + 저장소 인터페이스 |
| `data/src/main/kotlin/com/moyeota/data/local/DataStoreActivePartyStorage.kt` | DataStore(Preferences) 구현, 파일명 `moyeota_active_party` |
| `data/src/main/kotlin/com/moyeota/data/repository/RemoteActivePartyRepository.kt` | 구현 |
| `data/src/test/kotlin/com/moyeota/data/repository/RemoteActivePartyRepositoryTest.kt` | 11 케이스 |

### 2. Repository 시그니처 (compose-builder 경계면 — 확정)

```kotlin
interface ActivePartyRepository {
    suspend fun remember(partyId: String)   // Ride.id 를 그대로 넘긴다(String). 미로그인이면 no-op
    suspend fun clear()
    suspend fun resolve(): Ride?            // 예외 없음. 못 찾으면 null
}
```
- 구현체: `RemoteActivePartyRepository(rideRepository, chatRepository, storage, session)`
- **호출 시점은 ViewModel 몫**(계약대로 Repository 내부에 훅을 넣지 않았다):
  `createParty` 성공 → `remember(ride.id)` / `joinParty` 성공 → `remember(ride.id)` / `leaveParty` 성공 → `clear()`
  + 진행 단계 화면에서 상세가 `COMPLETED`/`CANCELED` 로 확인되면 `clear()`.

### 3. resolve() 동작 (3값 판정)

내부 판정은 「찾음 / **확실히 아님** / **모르겠음**」 세 값이다. 두 번째와 세 번째를 뭉개면
오프라인에서 앱을 켠 사용자의 복귀 경로가 영구히 지워진다.

1. `session.currentUserUuid == null`(미로그인·복원 전) → **기억을 건드리지 않고** null.
2. 로컬 기억 존재 && `ownerUuid == 현재 uuid` → `rideRepository.getPartyDetail(partyId.toLong())`
   - `status ∈ {RECRUITING, MATCHED, DISPATCHING, ONGOING}` && `members.any { it.isMe }` → **반환**
   - 그 외 / HTTP 404·403 → 기억 삭제 후 3번으로 진행
   - 그 밖의 실패(타임아웃·5xx·IOException) → **기억 유지**, 즉시 null (채팅방 스캔도 안 함)
3. `ownerUuid` 불일치(계정 전환) → 기억 삭제 후 3번으로 진행 (이전 계정 방은 조회조차 하지 않음)
4. `chatRepository.getMyChatRooms()` → `room.id` 내림차순(= 최신순) **최대 5개**의 `room.partyId` 를 같은 기준으로 검증.
   찾으면 `remember` 후 반환. 목록 조회 실패 → null.

### 4. ⚠ 입력 문서와의 차이 2건 (의도적)

**(A) 진행 중 상태 집합에 `MATCHED` 를 추가했다.**
문서는 `{RECRUITING, DISPATCHING, ONGOING}` 이었으나, `PartyMappers.partyStatusToRideStatus` 는
서버 `COMPLETED`(= **정원 충족**, 기사 매칭 시작 직전)를 `RideStatus.MATCHED` 로 매핑한다.
문서대로면 사용자가 가장 보고 싶어 하는 「정원 다 참 → 매칭 시작」 순간의 방이 "끝난 방"으로 지워진다.
→ **compose-builder 요청**: `navigateToStage` 에 `MATCHED` 분기를 추가할 것.
`MATCHED` 는 정원 충족 직후이므로 **21(매칭 대기)** 로 보내는 게 맞다(곧 서버가 `MATCHING` 으로 바꾸면 25b 로 넘어간다).
분기 없이 `else -> 홈` 이면 이 순간에 배너를 눌러도 홈으로 튄다.
정상 종료는 `COMPLETED`(서버 FINISHED)뿐이다 — 문서의 "FINISHED" 는 도메인에서 `RideStatus.COMPLETED` 다.

**(B) 기억이 "확실히 아님"으로 판정돼 삭제된 뒤에도 채팅방 스캔으로 넘어간다.**
문서는 ②를 "기억 없음"일 때로만 적었지만, 방금 지운 직후 = 기억 없음이라 같은 상태다.
다른 기기에서 합류한 방을 여기서 건진다. 비용은 "기억 없는 사용자"와 동일하다(목록 1 + 상세 ≤5).

### 5. AppContainer 배선 (미적용 — compose-builder 몫)

지시대로 `app/`·`presentation/` 을 건드리지 않았다. 아래를 `AppContainer` 에 그대로 추가하면 된다.

```kotlin
import com.moyeota.data.local.DataStoreActivePartyStorage
import com.moyeota.data.repository.RemoteActivePartyRepository
import com.moyeota.domain.repository.ActivePartyRepository

// 서버에 "내 진행 중인 방" 엔드포인트가 없어 앱이 세우는 복구 경로(RemoteActivePartyRepository KDoc).
val activePartyRepository: ActivePartyRepository = RemoteActivePartyRepository(
    rideRepository = rideRepository,
    chatRepository = chatRepository,
    storage = DataStoreActivePartyStorage(context),
    session = sessionManager,
)
```
`rideRepository`/`chatRepository` 선언 **아래**에 둘 것(초기화 순서).

### 6. 제안 (채택하지 않은 것)

`RemoteRideRepository` 가 `createParty/joinParty/leaveParty` 안에서 직접 remember/clear 하는 편이
호출부 실수를 없애 준다. 다만 (a) `RideRepository` 생성자에 `ActivePartyRepository` 가 들어가 순환 배선이 생기고,
(b) "방 조회했더니 로컬 파일이 바뀐다"는 숨은 부수효과가 되어 채택하지 않았다.
대신 **누락 방지 책임은 ViewModel 테스트/QA 체크리스트**로 넘긴다 — qa-verifier 교차 검증 항목:
「방 생성·합류 후 앱 강제 종료 → 재실행 시 해당 단계 화면 복귀」.

### 7. 검증

- `./gradlew :data:testDebugUnitTest` **통과** (RemoteActivePartyRepositoryTest 11/11, 전체 그린)
- `./gradlew :app:assembleDebug` **통과**
- 테스트 케이스: 기억 적중 / MATCHED 진행 중 인정 / COMPLETED → 기억 삭제 / 404 → 기억 삭제 /
  내가 멤버 아님 → 기억 삭제 / 기억 없음 → 채팅방으로 발견 + 기억 / 채팅방 스캔 최신순 5개 제한 /
  계정 전환 → 기억 무시·삭제·이전 방 미조회 / 네트워크 실패 → null + 기억 보존 + 채팅 미호출 /
  채팅 목록 실패 → null / 미로그인 → 기억 보존 + remember no-op
- **실서버 미검증** (백엔드·에뮬레이터 금지 지시). 스펙 근거는 기존 연동 코드(`PartyMappers`, `ChatApi`)이며
  새 엔드포인트는 없다 — 기존 `GET /matching/rooms/{partyId}`, `GET /chat-rooms/me` 재사용뿐이다.

---

# 2. 채팅 발신자 publicId 전환

배포 백엔드가 메시지 응답에서 `userId`(내부 PK)를 빼고 `publicId`(발신자 공개 UUID)를 싣도록 바뀌었다.
앱은 `senderPublicId` 라는 다른 이름을 기다리고 있어 그 값을 **읽지 못하고 있었다** — 결과적으로
"내 메시지" 판정이 「이번 세션에 한 번 보낸 뒤에야 참」인 학습 폴백에만 의존했고, 방을 나갔다 들어오면
내 말풍선이 상대 쪽에 붙었다.

### 2.1 배포 서버 실측 (2026-09-09, `https://api.moyeota.p-e.kr`)

계정 `smokep1` / `Passw0rd!` (JWT `sub` = `01a07fa7-50ac-7b82-9b19-a9ca3e5c52d6`), chatRoomId 1.

| 엔드포인트 | 실제 응답 shape |
|---|---|
| `GET /api/v1/chat-rooms/1/messages?size=5` | `{"messages":[{"id","chatRoomId","publicId","content","type","createdAt","deleted"}],"nextCursor","hasNext"}` |
| `POST /api/v1/chat-rooms/1/messages` | 같은 메시지 객체 1건 (`publicId` 포함) |
| `GET /api/v1/chat-rooms/1/users` | `[{"publicId","nickname","imageUrl","active"}]` — **userId 없음** |
| `GET /api/v1/chat-rooms/me` | `[{"chatRoomId","lastReadMessageId","notificationMuted","joinedAt"}]` (변동 없음) |

- 메시지의 `publicId` == 그 사용자의 JWT `sub` == 파티 `MemberInfo.publicId`. **하나의 식별 체계로 통일됐다.**
- 메시지에 `senderNickname` 은 오지 않는다 → 이름은 참여자 목록에서 채워야 한다(변동 없음).
- 참여자 응답에 `userId` 가 사라져, 「내부 PK ↔ 공개 신원」을 잇던 다리가 없어졌다. 필요도 없어졌다.

### 2.2 변경

**DTO** (`data/remote/dto/ChatDtos.kt`)
- `ChatMessageResponse`: `publicId: String? = null` 추가. `senderPublicId`·`senderNickname` 관용 필드 유지,
  `userId: Long = 0` 유지(구버전 응답 파싱용, 앱은 더 이상 읽지 않음).
- `ChatMemberResponse.userId`: nullable 유지 + "현행 서버는 주지 않는다(항상 null)" 명시.

**매퍼** (`data/remote/ChatMappers.kt`)
- `ChatIdentity` 가 `(myUuid, myInternalId, members: Map<Long, ChatMember>)` → **`(myUuid, members: Map<String, ChatMember>)`**.
  `myInternalId` 삭제.
- 발신자 publicId = `senderPublicId ?: publicId` (둘 다 blank 면 null).
- `isMine` = `발신자 publicId == session.currentUserUuid` **단 하나의 근거**. 3단 우선순위 폐기.
- `senderName` = 참여자 사전(publicId 키) → `senderNickname` 폴백 → null.
  (**순서가 뒤집혔다**: 예전엔 `senderNickname` 우선. 참여자 목록이 더 최신이라 사전을 앞세웠다.)

**저장소** (`data/repository/RemoteChatRepository.kt`)
- `learnedInternalId`·`learnedForUuid`·`learn()` **삭제**. `sendMessage` 의 학습 부수효과도 삭제.
- `RoomMembers.byUserId: Map<Long, _>` → `byPublicId: Map<String, _>`, `unresolved: Set<Long>` → `Set<String>`.
- 모르는 발신자 1회 재조회 로직은 **publicId 기준으로 유지**(중간 합류자 이름 채우기 목적).
- 계정 전환 시 캐시 폐기는 그대로.

**도메인** (`domain/model/Chat.kt`, `domain/repository/ChatRepository.kt`)
- ⚠ **지시에 없던 변경**: `ChatMessage.senderId: Long` → **`senderPublicId: String?`**.
  서버가 내부 PK 를 안 주므로 `senderId` 는 앞으로 **항상 0** 이다 — 항상 0인 Long PK 를 남겨 두면
  다음 사람이 그걸로 판정을 짜다가 조용히 깨진다. presentation 전수 grep 결과 `senderId` 사용처 0건이라
  안전하게 교체했다. 새 필드는 `User.id`(파티 멤버 publicId)와 같은 체계라 프로필 연결에도 쓸 수 있다.
- `ChatMember.userId`: nullable 유지(구버전 호환 잔여), KDoc 에 "항상 null, 판정에 쓰지 않음" 명시.
- 관련 KDoc(학습 전략 설명)을 전부 갱신.

### 2.3 폐기 고지 — 42·46 문서

42·46 리포트에 기록된 **"내가 보낸 메시지 응답에서 내부 userId 를 학습해 내 메시지를 가린다"** 전략은
**폐기됐다.** 근거(서버가 주던 `userId`)가 응답에서 사라졌고, 대체 근거(`publicId`)가 학습 없이 정확하다.
그 문서들의 「학습 전 한계」·「한 번 보낸 뒤에야 참」 서술은 더 이상 현재 코드와 맞지 않는다.

### 2.4 presentation 영향

**무변경.** 화면은 `ChatMessage.isMine` / `senderName` 만 쓴다(grep 확인). `senderId` 사용처 0건.
동작상 개선 1건: **방에 처음 들어간 첫 프레임부터 내 말풍선이 제자리**다(예전엔 이번 세션에 한 번
보내야 맞았다). 참여자 목록 조회가 실패해도 `isMine` 은 정확하고, 이름만 빈다.

### 2.5 검증

- `./gradlew :data:testDebugUnitTest` **통과** — 전체 196/196.
  `ChatMappersTest` 20건, `RemoteChatRepositoryTest` 17건으로 갱신.
- 매퍼 테스트에 **배포 서버 실측 JSON 을 그대로** 넣었다(`{"id":4,...,"publicId":...}`) — 필드명이 다시
  바뀌면 여기서 먼저 깨진다.
- 주요 케이스: 실측 publicId 매핑 / `senderPublicId` 관용 필드 / 둘 다 오면 `senderPublicId` 우선 /
  빈 식별자 → 아무의 것도 아님 / 미로그인 → 전부 남의 것 / 참여자 사전 닉네임(사전 > senderNickname) /
  나간 참여자 이름 유지 / 사전 없이도 isMine 정확 / 참여자 조회 실패해도 isMine 유지 /
  모르는 발신자 1회 재조회 후 재호출 없음 / 계정 전환 시 캐시 폐기(이름 소실로 검증) /
  구버전 userId-only 응답도 파싱은 성공하되 발신자 불명.
- **실서버 검증 완료** (1절의 ActivePartyRepository 와 달리 이쪽은 curl 로 실응답 확인).
- `./gradlew :app:assembleDebug` — **compose-builder 작업 중인 `MainNavGraph.kt:662` 컴파일 에러**
  (`No parameter with name 'onLiveLocationClick'`)로 실패. 내 변경과 무관하다
  (presentation 무수정, `senderId` 사용처 0건). `:data`·`:domain` 컴파일은 통과.
