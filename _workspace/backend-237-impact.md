# 백엔드 PR #237 — 앱 영향 분석

작성: 2026-10-15 · 조사 범위: 백엔드 `origin/develop`(#237 머지 후) ↔ 앱 `fix/place-roadname-null`
**코드 변경 없음. 조사·보고만.**

---

## 0. 요약 (한 문단)

#237 은 **합승 이력(ride history) 기능이 아니다.** 브랜치 이름만 `feature/party-ride-history` 이고,
실제 내용은 커밋 1개 — 「1차 배포 모드에서 `POST /matching/rooms/{partyId}/finish` 를 **방장만** 부를 수
있게 막고, 방 상세 응답에 `hostId` 를 추가」다. 합승 이력 이슈 **#207 은 여전히 OPEN**, 엔드포인트
`GET /api/v1/users/me/parties` 는 백엔드 develop 에도 **없다**.

그리고 **배포되면 현재 앱이 깨진다.** 앱의 「합승 완료」 버튼은 방장 여부를 보지 않으므로, 방장이 아닌
멤버가 누르면 403 `NOT_PARTY_HOST` → 「합승을 완료하지 못했어요」 배너만 뜨는 **눌러도 안 되는 버튼**이 된다.
게다가 **앱은 `hostId` 를 쓸 수 없다** — `hostId` 는 서버 내부 PK(Long)인데 앱이 가진 식별자는 publicId(UUID)
하나뿐이고, 둘을 잇는 응답이 운영·develop 어디에도 없다.

---

## 1. #237 이 정확히 바꾼 것

### 1.1 메타

| 항목 | 값 |
|---|---|
| 제목 | `feat: 1차 모드 파생 방장 합승 완료 권한` |
| 브랜치 | `feature/party-ride-history` → `develop` (**이름과 내용이 다르다**) |
| 머지 | 2026-10-15 03:21, 머지 커밋 `b8e4750` |
| 커밋 | `5bb5957 feat: 1차 모드 방장(파생) 합승 완료 권한 추가` — **이 하나뿐** |
| 변경 규모 | +113 / −12, 프로덕션 4파일 + 테스트 2파일 |
| 배포 상태 | **운영 미배포.** 직전 릴리스 #236(develop→main, 03:02) 머지 커밋 `5d8889a` 가 `origin/main` 끝이고 `5bb5957` 은 그 뒤 — `origin/main` 에 없다 |

변경 파일 전체(`gh pr diff 237 --name-only`):

```
src/main/java/.../matching/exception/MatchingErrorCode.java
src/main/java/.../matching/party/PartyController.java
src/main/java/.../matching/party/domain/Party.java
src/main/java/.../matching/party/dto/PartyDetailResult.java
src/unitTest/java/.../matching/party/PartyServiceTest.java
src/unitTest/java/.../matching/party/domain/PartyTest.java
```

### 1.2 합승 이력(ride history) — **#237 에 없다**

- #237 의 diff 에 `users/me/parties`·`finished_at`·마이그레이션(V13+)·목록 DTO 가 **하나도 없다.**
- 백엔드 이슈 **#207 「완료된 방 목록(내 탑승 기록) API — GET /api/v1/users/me/parties」 는 state: OPEN.**
  작업 목록 체크박스 전부 미체크, 「결정 필요」 3건(만료 방 포함 여부·status 허용값·`taxiDriverId` 노출)도 미해결.
- `grep -rn "me/parties" src/main/java` → 백엔드 develop 에서 **0건.**
- 운영 OpenAPI 스냅샷(39경로)에도, develop 코드에도 해당 경로 없음.

**결론: 브랜치 이름은 #207 을 염두에 두고 딴 것으로 보이지만, 거기에 실린 커밋은 방장 권한 하나다.
합승 이력은 아직 서버에 없다. 앱 34 「내 탑승」 화면은 계속 부활 불가 상태다.**
(앱 쪽 근거: `/Users/sungyoon/Desktop/moyeota/frontend/docs/BACKEND-GAP-CLEANUP.md` — 34 는 대응 API 가
없어 화면째 삭제된 상태로 기록돼 있다.)

### 1.3 「파생 방장」의 정확한 정의

`backend/src/main/java/team/codingforest/moyeota/matching/party/domain/Party.java:217-226`

```java
/**
 *  방장 = 가장 먼저 합류한 현재 멤버. 방장이 나가면 다음으로 이른 멤버가 승계된다
 */
public Long hostId() {
    return members.stream()
            .min(Comparator.comparing(PartyMember::getJoinedAt)
                    .thenComparing(PartyMember::getMemberId))
            .map(PartyMember::getMemberId)
            .orElseThrow(() -> new IllegalStateException("방에 멤버가 없습니다"));
}
```

- **저장 컬럼이 아니다.** `user_match_room.joined_at` 을 매번 읽어 계산하는 파생값이다. 스키마 변경 없음,
  마이그레이션 없음.
- 평소엔 **방 생성자 = 방장**이다 — `Party.open` 이 생성자를 첫 멤버로 넣는다(`Party.java:77`).
- **원 방장이 나가면 다음으로 일찍 합류한 멤버가 자동 승계**된다. 승계 로직이 따로 있는 게 아니라
  `members` 에서 그 사람이 빠지면 `min()` 결과가 바뀌는 것뿐이다
  (테스트 `PartyTest.방장이_나가면_다음으로_일찍_합류한_멤버가_방장이_된다`).
- `joinedAt` 동률이면 **memberId 작은 쪽**(테스트 `합류_시각이_같으면_memberId가_작은_멤버가_방장이다`).
- 멤버가 0명이면 `IllegalStateException` — 다만 호출부가 빈 명단을 먼저 걸러 실제로는 안 터진다(아래 1.5).

### 1.4 권한 규칙 변경 — `/finish` 는 방장 전용

`backend/.../matching/party/domain/Party.java:228-236`

```java
public void finishWithoutDriver(Long memberId) {
    if(!hasMember(memberId)) throw new BusinessException(MatchingErrorCode.NOT_PARTY_MEMBER);

    if(!hostId().equals(memberId)) throw new BusinessException(MatchingErrorCode.NOT_PARTY_HOST);   // ← 신규

    if(status != PartyStatus.COMPLETED) throw new BusinessException(MatchingErrorCode.PARTY_NOT_COMPLETED);
    if(taxiDriverId != null) throw new BusinessException(MatchingErrorCode.DRIVER_ALREADY_ASSIGNED);
    ...
}
```

검사 **순서가 중요하다**: 멤버 → **방장** → 상태 → 기사. 즉 정원이 안 찬 방에서 비방장이 누르면
`PARTY_NOT_COMPLETED`(409)가 아니라 `NOT_PARTY_HOST`(403)가 먼저 온다.

신규 에러 코드 — `backend/.../matching/exception/MatchingErrorCode.java:19`

```java
NOT_PARTY_HOST(HttpStatus.FORBIDDEN, "방장만 수행할 수 있습니다."),
```

Swagger 갱신 — `backend/.../matching/party/PartyController.java:90-91`
: `403` 설명이 `NOT_PARTY_MEMBER` → `NOT_PARTY_MEMBER / NOT_PARTY_HOST`,
  description 에 「방장(가장 먼저 합류한 멤버)만 호출 가능」 추가.

**택시 ON 모드 영향 없음**은 PR 본문 주장이고 코드로도 맞다 — 택시 모드에서는 정원이 차는 순간 서버가
`MATCHING` 으로 올려 `COMPLETED` 가 순간이므로 `/finish` 는 상태 검증에서 막힌다. 앱도 택시 모드에선
버튼을 아예 안 그린다(`canFinishParty` 가 `!taxiEnabled` 를 요구).

### 1.5 응답 변경 — 방 상세에 `hostId` 추가

`backend/.../matching/party/dto/PartyDetailResult.java:11-20, 45`

```java
public record PartyDetailResult(..., Long taxiDriverId,
                                Long hostId) {          // ← 신규 필드, record 마지막
...
        party.getMembers().isEmpty() ? null : party.hostId()   // 전원이 나가 취소된 방엔 방장이 없다
```

영향 경로가 **둘**이다 — `PartyDetailResult` 는 컨트롤러가 그대로 직렬화한다(별도 Response DTO 없음):

1. `GET /api/v1/matching/rooms/{partyId}` (`PartyController.java:61`)
2. **SSE `connected` 스냅샷** — `PartySseService.java:27` 이 `() -> partyService.getPartyDetail(partyId)` 를
   넘기고 `PartySseRegistry.java:34` 가 그걸 `connected` 이벤트 data 로 보낸다. 즉 앱의 21 매칭 대기가
   받는 SSE 스냅샷에도 `hostId` 가 실린다.

목록 응답(`PartyListResponse`)·합류 응답(`PartyStatusResponse`)·`MemberJoined` 델타에는 **없다.**

### 1.6 ⚠️ `hostId` 는 앱이 쓸 수 없는 식별자다 (가장 중요한 구조적 문제)

`hostId` 는 **서버 내부 사용자 PK(Long)** 다. 그런데 같은 응답의 `members[]` 에는 publicId(UUID)밖에 없다.

- 서버: `PartyDetailResult.MemberInfo(UUID publicId, String nickname, String imageUrl, String badgeId, Integer rideCount)`
  — 내부 `memberId` 가 과거에 제거됐다(`PartyDetailResult.java:22`).
- 앱: `domain/src/main/kotlin/com/moyeota/domain/session/UserSession.kt:9-18` KDoc 에 명시 —
  > **앱은 서버 내부 사용자 PK(Long)를 갖고 있지 않다.** … 앱이 아는 식별자는 `currentUserUuid` 하나뿐이고,
  > "이게 나인가" 판정은 서버가 내려주는 publicId 와의 비교로 한다.
- 내부 PK 를 주는 응답이 **운영에도 develop 에도 없다**:
  - 운영 OpenAPI 39경로에 `GET /api/v1/users/me` 자체가 없다(`delete, patch` 만).
  - `GET /api/v1/local/users/info` → `UserResponse(UUID uuid, String name)` — UUID 뿐
    (`backend/.../user/local/dto/UserResponse.java`).
  - JWT 클레임도 UUID — `TokenClaims(UUID publicId, UUID jti)`.
  - 과거에 앱이 내부 PK 를 학습했던 유일한 경로(채팅 메시지의 `userId`)도 **서버가 빼고 publicId 로 바꿨다**
    (`data/src/main/kotlin/com/moyeota/data/repository/RemoteChatRepository.kt:40-41`).
  - 운영 OpenAPI 스키마 전수 스캔 결과, 호출자 자신의 숫자 user id 를 주는 필드 없음.

**→ 앱은 `hostId` 를 `members[].publicId` 나 세션 UUID 와 비교할 수 없다. PR 본문의
「프론트: 완료 버튼 노출·방장 표시용」은 지금 형태로는 실행 불가다.**

우회로도 막혀 있다:
- 「`members[0]` 이 방장」 추정 — **불가.** `PartyEntity.members` 는 `@OneToMany` 에 `@OrderBy` 가 없고
  (`PartyEntity.java:61-62`), `PartyEntity.update()` 가 `removeIf` 후 append 하므로(`:157-165`) 순서가 보장되지 않는다.
- `MemberInfo.joinedAt` 으로 추정 — **불가.** 그 필드는 Backend #218 에서 제거됐다
  (앱 DTO 주석이 그 이력과 함께 "되살리면 '최초 입장자 = 방장' 같은 추정이 되살아난다" 고 경고해 둔 상태:
  `data/src/main/kotlin/com/moyeota/data/remote/dto/PartyDtos.kt:79-80`).

---

## 2. 앱의 현재 상태 (「합승 완료」 노출 조건)

### 2.1 호출 경로

| 레이어 | 파일:라인 |
|---|---|
| Retrofit | `data/src/main/kotlin/com/moyeota/data/remote/MatchingApi.kt:105-111` — `@POST("api/v1/matching/rooms/{partyId}/finish")` |
| Repository | `data/src/main/kotlin/com/moyeota/data/repository/RemoteRideRepository.kt:128` — `override suspend fun finishParty(partyId: Long) = api.finishParty(partyId)` |
| Domain 계약 | `domain/src/main/kotlin/com/moyeota/domain/repository/RideRepository.kt:132-138` |
| ViewModel | `presentation/src/main/kotlin/com/moyeota/presentation/feature/matching/MatchWaitingRoute.kt:399-406` — `fun finishParty()` |
| 노출 판정 | `presentation/.../MatchWaitingRoute.kt:583-588` — `canFinishParty` |
| 버튼 | `presentation/.../MatchWaitingScreen.kt:163, 393-401` |
| 확인 다이얼로그 | `presentation/.../MatchWaitingScreen.kt:423-446` |

**부르는 화면은 21 매칭 대기 하나뿐이다.** (홈 배너·22·24 등 다른 경로 없음 — 전수 grep 확인)

### 2.2 노출 조건 — 방장 여부를 **보지 않는다**

`presentation/src/main/kotlin/com/moyeota/presentation/feature/matching/MatchWaitingRoute.kt:583-588`

```kotlin
/**
 * 1차 배포 모드에서 「합승 완료」를 보여 줄 때 — 정원이 찼고(COMPLETED) 기사가 없을 때.
 * 기사 모드에선 절대 보이지 않는다(정원이 차면 곧바로 25 로 넘어간다).
 */
internal fun canFinishParty(status: RideStatus, taxiEnabled: Boolean): Boolean =
    !taxiEnabled && status == RideStatus.MATCHED
```

조건은 **(1) 택시 모드 OFF, (2) 서버 status == COMPLETED** 둘뿐. `members`·`isMe`·방장 아무것도 안 본다.
→ **정원이 찬 방의 모든 멤버에게 「합승 완료」가 보인다.**

이는 실수가 아니라 당시 계약을 그대로 옮긴 것이다. 앱 곳곳에 "참여자 누구든 한 명이 누르면 모두의 방이
닫힌다" 가 명시돼 있다:
- `MatchWaitingScreen.kt:147` — `/** 1차 배포 모드 「합승 완료」 — 참여자 누구든 한 명이 누르면 모두의 방이 닫힌다 */`
- `MatchWaitingRoute.kt:401` — `/** … 참여자 누구든 한 명이 누르면 **모두의** 방이 끝난다. */`
- `MatchingApi.kt:108` — `COMPLETED(정원 참) 방에서만 되고, 참여자 누구든 한 명이 부르면 방 전체가 FINISHED 다.`
- `docs/MODE-ROUTES.md:10` — 「끝내는 법 … **참여자**가 「합승 완료」」
- `docs/MODE-ROUTES.md:37` — 21 동승 모드 CTA 「합승 완료」(방장 조건 없음)
- 확인 다이얼로그 문구 `MatchWaitingScreen.kt:427` — 「같이 탄 사람 모두의 방이 종료돼요」

또한 앱은 과거에 **방장 개념을 의도적으로 전부 제거**했다(`_workspace/34_compose-builder_no_host.md`):
`Ride.hostId`·`NewParty.hostId`·`isHost`·「방장」 배지·「이 인원으로 출발」 CTA 가 모두 삭제됐고,
`domain/src/main/kotlin/com/moyeota/domain/model/Ride.kt:26-27`, `data/.../dto/PartyDtos.kt:38-39`,
`data/.../PartyMappers.kt:104` 에 "서버가 방장을 구분하지 않는다" 가 주석으로 못박혀 있다.
**#237 은 그 전제를 1차 모드에서만 되돌린다.**

### 2.3 실패 시 사용자에게 보이는 것

`presentation/.../MatchWaitingRoute.kt:403-405, 409-421`

```kotlin
fun finishParty() = runAction(failureMessage = "합승을 완료하지 못했어요") {
    repository.finishParty(partyId)
    _actionState.update { it.copy(finished = true) }
}

private fun runAction(failureMessage: String, block: suspend () -> Unit) { ...
    } catch (e: Exception) {
        _actionState.update { it.copy(inProgress = false, errorMessage = failureMessage) }
    }
```

**모든 예외를 고정 문구 하나로 흡수한다.** 서버 `code`·`message` 를 읽지 않는다
(`finishParty` 는 `PartyJoinException` 같은 코드 분기 경로를 타지 않는다 —
`domain/src/main/kotlin/com/moyeota/domain/model/PartyError.kt` 는 **합류 전용**이다).
→ 403 `NOT_PARTY_HOST` 가 와도 사용자는 「합승을 완료하지 못했어요」만 본다. 「방장만 할 수 있어요」 를
알려줄 길이 현재 없다.

---

## 3. #237 배포 시 현재 앱이 깨지는 부분 — **있다 (1건, 출시 블로커 후보)**

### B-1 · 비방장 멤버의 「합승 완료」가 영구 실패 버튼이 된다 — 심각도 높음

**재현 경로 (동승 모드 = 현재 운영 모드):**

1. A 가 2인 방을 만든다 → A 가 첫 멤버 → `hostId == A`
2. B 가 합류 → 정원 충족 → `COMPLETED`
3. B 의 21 화면: `canFinishParty(MATCHED, taxiEnabled=false)` → **true** → 「합승 완료」 노출
4. B 가 누름 → 확인 다이얼로그 「합승을 완료할까요? / 같이 탄 사람 모두의 방이 종료돼요」 → 확인
5. 서버 `403 NOT_PARTY_HOST`
6. 앱: 「합승을 완료하지 못했어요」 배너. 버튼은 그대로 남아 **몇 번 눌러도 같은 결과.**

**왜 심각한가**

- 앱이 사용자에게 **확인 다이얼로그까지 띄워 약속한 행동이 실패한다.** 「모두의 방이 종료돼요」 라고
  경고까지 받은 뒤 아무 일도 안 일어난다 — 신뢰를 직접 깨는 종류의 실패다.
- 실패 이유를 **알려줄 수 없다**(§2.3). 사용자는 네트워크 문제인지 서버 장애인지 알 방법이 없다.
- 2인 방에서 **정확히 50% 확률로 B 가 먼저 내리고 먼저 누른다.** 드문 경로가 아니라 절반이다.
- 「합승 완료」는 **1차 배포 모드에서 방을 닫는 유일한 사용자 행동**이다(`docs/MODE-ROUTES.md:10`).
  B 가 못 닫으면 B 는 A 가 눌러 주길 기다려야 하고, 그 사이 B 는 `ALREADY_JOINED_OTHER_PARTY` 로
  **새 방을 만들거나 다른 방에 들어갈 수 없다.**

**완화 요인 (영구 잠김은 아니다)**

- B 는 「방 나가기」로 빠져나올 수 있다 — `canLeaveParty(status, taxiEnabled)` 가 1차 모드의 `MATCHED` 에서
  나가기를 허용하고(`MatchWaitingRoute.kt:580-581`), 서버 `ensureRecruiting` 도 COMPLETED 를 허용한다.
  단 `Party.leave`(`Party.java:106-130`)가 방을 **`ACTIVE` 로 되돌려 다시 모집**하므로, 다 타고 끝난 방이
  탐색 목록에 되살아난다(이건 #237 과 무관한 기존 동작).
- 30분 뒤 `CompletedPartySweeper`(TTL 30분, 10분 주기, `taxi.enabled=false` 일 때만)가 자동 종료한다.
- 방장 A 가 누르면 정상 종료된다.

### 깨지지 **않는** 것 (확인 완료)

| 항목 | 판정 | 근거 |
|---|---|---|
| `hostId` 추가로 방 상세 파싱이 깨지나 | **아니다** | `data/src/main/kotlin/com/moyeota/data/remote/NetworkModule.kt:24` — `Json { ignoreUnknownKeys = true }`. 앱 DTO `PartyDetailResponse`(`data/.../dto/PartyDtos.kt:42-71`)에 `hostId` 가 없어도 무시된다 |
| SSE `connected` 스냅샷 파싱 | **깨지지 않음** | 같은 DTO·같은 Json 인스턴스를 재사용(`data/.../PartyMappers.kt:137`, `data/.../matching/PartySseEvent.kt:24`) |
| 택시 모드 화면(25·26·33) | **영향 없음** | `/finish` 는 `COMPLETED` 전용이고 택시 모드에선 그 상태가 순간. 앱도 `!taxiEnabled` 조건으로 버튼을 안 그린다 |
| 방장(=생성자) 본인의 「합승 완료」 | **정상 동작** | 생성자가 첫 멤버라 `hostId == 생성자`(`Party.java:77`) |
| 나가기·합류·목록·채팅 | **영향 없음** | #237 변경 파일에 없음 |
| 마이페이지 34 「내 탑승」 | **변화 없음** | #207 미구현. 지금처럼 화면 없는 상태 유지 |

---

## 4. 앱이 해야 할 일 — 3등급

### 🔴 해야 함 (#237 배포 전 또는 동시에)

**H-1 · 「합승 완료」 노출/실행 조건을 방장 규칙에 맞춘다**

- 대상: `presentation/src/main/kotlin/com/moyeota/presentation/feature/matching/MatchWaitingRoute.kt:583-588`
  (`canFinishParty`), 호출부 `presentation/.../MatchWaitingScreen.kt:163, 393-401`
- **단, 지금 그대로는 구현할 수 없다** — §1.6. `hostId`(Long)를 앱이 가진 publicId(UUID)와 비교할 수 없다.
  **서버 변경이 선행돼야 한다**(§5 의 결정 D-1).
- 선행 서버 변경이 없을 때의 차선책(아래 H-2)만으로도 B-1 의 최악(확인 다이얼로그 후 실패)은 막을 수 있다.

**H-2 · 실패 메시지를 사유별로 가른다 — 최소 안전망**

- 대상: `presentation/.../MatchWaitingRoute.kt:403-421` (`finishParty` / `runAction`)
- 현재 모든 예외가 「합승을 완료하지 못했어요」 하나로 흡수된다. 403 `NOT_PARTY_HOST` 를 읽어
  「방을 만든 사람만 합승을 완료할 수 있어요」 같은 문구를 띄우고, 그 뒤 버튼을 숨기거나 비활성화한다.
- 서버 코드 문자열 파싱 함수는 이미 있다 — `data/src/main/kotlin/com/moyeota/data/remote/PartyMappers.kt:233`
  (`Throwable.readApiError()`). `PartyError.kt` 가 합류 전용이므로 종료용 예외/코드 전달 통로를 새로 하나
  만들어야 한다(설계 판단 필요 — 기존 `PartyJoinException` 패턴 재사용 vs 공용화).
- **이 하나만으로도 「눌렀는데 이유 없이 안 됨」이 「왜 안 되는지 아는 상태」로 바뀐다.** H-1 의 서버 선행
  작업을 기다리지 않고 지금 넣을 수 있는 유일한 조치다.

**H-3 · 문서 2곳을 서버 규칙에 맞춘다**

- `docs/MODE-ROUTES.md:10` — 「끝내는 법: **참여자**가 「합승 완료」」 → 방장 조건 반영
- `docs/MODE-ROUTES.md:37` — 21 동승 모드 CTA 설명에 방장 조건 추가
- 코드 주석 중 사실과 달라지는 것들: `data/.../remote/MatchingApi.kt:108`,
  `presentation/.../MatchWaitingRoute.kt:401`, `presentation/.../MatchWaitingScreen.kt:147`,
  `presentation/.../MatchWaitingScreen.kt:427`(다이얼로그 문구는 UI 문자열이므로 H-1 과 함께)

### 🟡 하면 좋음

**N-1 · 방장 표시(배지)** — `presentation/.../MatchWaitingScreen.kt:337` 멤버 행, `RideDetailScreen.kt:311`
  (「방장 배지는 없다 — 서버에 방장 표식 자체가 없다」 주석이 사실과 달라진다).
  과거에 배지를 **의도적으로 지웠으므로**(`_workspace/34_compose-builder_no_host.md`) 되살리는 건 제품 결정이다.
  H-1 과 같은 선행 조건(publicId 기반 방장 식별)이 필요하다.

**N-2 · 비방장에게 안내 문구** — 정원이 찬 방에서 비방장에게는 버튼 대신
  「다 타고 나면 방을 만든 분이 합승을 완료해요」 류의 한 줄. 버튼만 숨기면 「왜 나는 못 끝내지」가 남는다.

**N-3 · 앱 DTO 에 `hostId` 수용 필드 추가** — `data/src/main/kotlin/com/moyeota/data/remote/dto/PartyDtos.kt:70`
  뒤. 파싱은 지금도 안 깨지므로 **급하지 않다.** 다만 `PartyDtos.kt:38-39`·`PartyMappers.kt:104`·
  `Ride.kt:26-27` 의 "서버가 방장을 구분하지 않는다" 주석이 거짓이 되므로, 필드를 안 받더라도 주석은 고쳐야
  한다(아니면 다음 사람이 같은 조사를 반복한다).

### ⚪ 할 것 없음

- **합승 이력(34 「내 탑승」)** — #237 에 없다. #207 이 구현될 때까지 앱이 할 일 없음.
- **택시 모드 경로(25·26·27·33)** — 영향 없음.
- **네트워크/직렬화 계층** — `ignoreUnknownKeys` 로 `hostId` 추가가 무해. 조치 불필요.
- **방 생성·합류·나가기·목록·채팅** — #237 변경 범위 밖.
- **`origin/main` 기준 현재 출시 빌드** — #237 이 배포되기 전까지는 아무것도 깨지지 않는다.

---

## 5. 사용자에게 물어야 할 결정 사항

**D-1 (최우선) · `hostId` 를 앱이 쓸 수 있게 하려면 서버를 바꿔야 한다. 어느 쪽으로 요청할까?**

세 안:
- (a) `MemberInfo` 에 `isHost: boolean` 추가 — 앱이 식별자를 다룰 필요가 없다. 가장 작고 안전.
- (b) `hostId` 를 Long 대신 **방장의 publicId(UUID)** 로 — 앱의 세션 UUID 와 바로 비교 가능.
  기존 `hostId` 가 아직 아무도 안 쓰므로 타입 교체 비용이 거의 없다.
- (c) `GET /api/v1/users/me` 에 내부 PK 노출 — **권하지 않는다.** 서버가 내부 PK 를 응답에서 빼 온 방향
  (`MemberInfo.memberId` 제거, 채팅 `userId` 제거)을 되돌리는 셈이다.

→ **백엔드에 (a) 또는 (b) 로 요청해야 한다.** 이 결정 없이는 H-1·N-1 을 구현할 수 없다.

**D-2 · 출시 일정과 #237 배포 순서.** 현재 출시 예정 빌드는 B-1 을 안고 있다. 선택지:
- (i) #237 을 **앱 수정이 배포될 때까지 운영에 올리지 않는다** — 가장 단순. 지금 운영은 "아무나 완료" 라서
  사용자가 막히는 일이 없다(의도와 다르긴 하지만 깨지진 않는다).
- (ii) #237 을 먼저 올리고 앱은 H-2(사유별 메시지)만 긴급 반영.
- (iii) 둘을 같은 릴리스로 묶는다.

**D-3 · 「합승 완료」를 정말 방장 전용으로 둘 것인가 (제품 결정).**
동승 모드의 「합승 완료」는 "이 방 다 끝났다" 는 **사실 신고**에 가깝고, 그 사실은 누구나 안다.
방장만 누를 수 있으면 **방장이 먼저 내려 앱을 닫은 뒤 나머지가 방에 갇힌다**(30분 스윕까지).
대안: 전원 동등 유지 + 확인 다이얼로그 강화, 또는 "과반/전원 동의", 또는 방장 우선 + N분 뒤 전원 허용.
→ #237 의 전제 자체를 되물을 가치가 있다. 백엔드에 되돌림을 요청할지 결정 필요.

**D-4 · 방장이 COMPLETED 방에서 「방 나가기」를 누르는 경로를 어떻게 다룰지.**
`Party.leave`(`Party.java:127-128`)가 방을 `ACTIVE` 로 되돌려 **다 탄 방이 탐색 목록에 되살아난다.**
#237 과 무관한 기존 결함이지만, 「합승 완료」가 방장 전용이 되면 방장이 실수로 완료 대신 나가기를 누를
위험이 커진다(그 방의 나머지 사람은 승계로 완료할 수 있지만 status 가 ACTIVE 로 떨어져 버튼이 사라진다).
→ 별도 백엔드 이슈로 올릴지 결정 필요.

**D-5 · 합승 이력(#207) 진행 상태.** 브랜치 이름이 `party-ride-history` 인데 내용은 방장 권한이다.
#207 작업이 이 브랜치에서 시작됐다가 보류된 것인지, 아니면 브랜치를 재사용한 것인지 백엔드 담당자에게
확인할 필요가 있다. #207 의 「결정 필요」 3건(만료 방 포함 여부·status 허용값·`taxiDriverId` 노출)에
프론트 선호는 이미 이슈 본문에 적혀 있다.

---

## 6. 미확인 항목

- **`NOT_PARTY_HOST` 의 실제 에러 본문 형태** — 미확인. 전역 핸들러가 `{code, message}` 를 내는 건
  기존 패턴으로 확인됐으나(`PartyMappers.kt:228`), `code` 값이 `NOT_PARTY_HOST` 문자열인지
  `MATCH0xx` 류 코드인지 보지 않았다. 확인 방법: 로컬 백엔드를 `TAXI_ENABLED=false` 로 띄워 2계정으로
  방을 채운 뒤 비방장 토큰으로 `POST /api/v1/matching/rooms/{id}/finish` 호출, 응답 본문 확인.
  (운영 서버에는 쓰기 요청을 보내지 않았다.)
- **#237 의 `/finish` 를 통한 실제 403 동작** — 코드·단위테스트로만 확인. 실기 미검증.
- **`hostId` 를 쓰는 다른 백엔드 모듈(기사 앱 등)** — 조사 범위 밖. `PartyDetailResult` 를 소비하는 곳은
  `PartyController.detail` 과 SSE 스냅샷 둘로 확인했으나, frontend-driver 저장소는 보지 않았다.
