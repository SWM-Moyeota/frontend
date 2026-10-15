# PR #52 적대적 검토 — `fix/place-roadname-null` ↔ `origin/develop`

- 일시: 2026-10-15
- 범위: `git diff origin/develop...fix/place-roadname-null` 전체 (**커밋 8개**, 54 파일, +4976/−147)
  - 과제문에는 「커밋 9개」라고 적혀 있는데 `git rev-list --count origin/develop..fix/place-roadname-null` = **8** 이다.
    (`8738679 c53e008 beb4a52 f78bee3 6db78d7 249f33f 5fc8da4 01365b1`)
- 방법: 읽기·추론만. **빌드·테스트 0회**(다른 에이전트가 Gradle 점유). 운영 서버에 쓰기 0건.
- 교차 근거:
  - 백엔드 `origin/main` = `7f686fc`(#242 머지) / `de193d9`(#243 `member-left` 방장 승계)
  - 운영 OpenAPI 스냅샷 `scratchpad/prod-openapi-3.json`
- **코드 변경 0. 커밋·브랜치 변경 0.**

---

## 0. 한 줄 결론

**「머지 전 고쳐야 함」은 0건이다.** 정상 경로(방 생성 → 합류 → 정원 충족 → 방장이 「합승 완료」)는
운영 서버 계약과 일치하고, 성별·`imageKey`·`roadName` 변경은 운영 스펙 실측과 모두 맞는다.

다만 **「머지해도 되나 고쳐야 함」이 5건**이다. 그중 셋은 **이 PR 안에서 서로를 덮은 흔적**이고
(f78bee3 이 「앱은 방장을 알 수 없다」를 전제로 쓴 주석이 beb4a52 가 방장 게이팅을 넣은 뒤에도
그대로 남았다), 둘은 **서버가 실제로 내려보내는 모양을 잘못 가정한 코드**다.

검증 근거가 맞는지 코드로 재확인한 것은 §4 에 표로 남겼다 — **설명이 맞았다**(부분 오류 §2-F, §3-C).

---

## 1. 머지 전 고쳐야 함

**없다.**

사용자가 정상 경로로 만나는 결함은 찾지 못했다. 특히 다음 경로들을 코드로 따라가 깨지지 않음을 확인했다.

| 경로 | 확인 내용 |
|---|---|
| 상세 조회 → 방장 판정 | `RemoteRideRepository.getPartyDetail:76` 이 `session.currentUserUuid` 를 넘기고, `MainNavGraph:93` 이 `AuthState.Unknown` 동안 NavHost 자체를 구성하지 않는다 → **21 이 열릴 때 uuid 가 null 일 수 없다.** `isHostedByMe` 오판 경로가 닫혀 있다 |
| 방장 → 버튼 / 비방장 → 안내문 | `canFinishParty` = `isFinishStage && isHostedByMe`. 서버 `Party.finishWithoutDriver`(`Party.java:233-242`) 검사 순서 `NOT_PARTY_MEMBER → NOT_PARTY_HOST → PARTY_NOT_COMPLETED → DRIVER_ALREADY_ASSIGNED` 와 문구 표가 1:1 |
| 헤드라인·나가기 문구 | `isFinishStage` 로 갈라 비방장에게 「같이 탈 사람 찾는 중」이 뜨지 않는다 (`MatchWaitingScreen.kt:214, 444`) |
| 승계 반영 | 방장 이탈 → `member-left.hostPublicId` → `withHostSuccession` → 승계자에게 버튼. 백엔드 `PartyService.leave:131`(`party.hostId()` 를 **leave 반영 후** 읽는다)과 순서가 맞다 |
| 역직렬화 | `roadName` 두 자리만 nullable. 전역 `coerceInputValues` 는 **어디에도 없다**(grep 0건). Json 인스턴스 8개 전부 `ignoreUnknownKeys = true` — `host`·`hostPublicId`·`UserResponse.imageUrl` 추가가 흘려진다 |
| 운영 스펙 일치 | `MemberInfo.host: boolean` 존재 · `PartyDetailResult.hostId` 없음 · `UpdateProfileRequest.imageKey` · `gender: [MALE, FEMALE, UNDISCLOSED]` — `prod-openapi-3.json` 에서 전부 확인 |
| 컴파일 | `canFinishParty` 구 시그니처 호출처 0건, `getMyRides` 잔존 호출 0건, `CancellationException` import 존재(`RemoteRideRepository.kt:31`), `User.isHost` 는 기본값 있는 마지막 파라미터 |

---

## 2. 머지해도 되나 고쳐야 함

### A. 탈퇴한 방장의 이름이 안내문에 그대로 찍힌다 — 가드가 **서버가 내지 않는 모양**을 막고 있다 ★

- **자리:** `presentation/src/main/kotlin/com/moyeota/presentation/core/MemberDisplay.kt:142-143`
- **코드:**
  ```kotlin
  internal fun List<User>.hostDisplayNameOrNull(): String? =
      firstOrNull { it.showsHostBadge && !it.isWithdrawn }?.nickname?.takeIf { it.isNotBlank() }
  ```
  `isWithdrawn` 은 `id.isBlank()`(= `publicId == null`)다(`MemberDisplay.kt:37-38`).

- **왜 틀렸나 — 서버는 탈퇴자를 그 모양으로 내리지 않는다.**
  백엔드 탈퇴는 **soft delete** 다. `UserDeletionService.delete:56` → `User.delete(now)`(`User.java:95-104`)가
  `nickname = null`·`imageUrl = null`·`deletedAt = now` 만 세우고 **user 행은 지우지 않는다**
  (`UserDeletionSweeper` 는 탈퇴 **전화번호 기록**만 30일 후 지운다 — user 행 아님).
  그리고 `UserAccessService.findMemberSummaries:33-37` 은 `users.findAllByIds` →
  `userRepository.findAllById`(`UserJpa.java:65-67`)라 **탈퇴자도 그대로 돌려주며**,
  `user.displayNickname()` 이 `"탈퇴한 사용자"` 를, `profileImages.toUrl(null)` 이
  **기본 이미지 전체 URL**(`ProfileImages.java:24-26`)을 채운다.

  → 운영에서 탈퇴한 멤버의 실제 `MemberInfo` 는
  `{publicId: "...", nickname: "탈퇴한 사용자", imageUrl: "<기본 URL>", badgeId: null, rideCount: n}` 이다.
  **`publicId` 가 살아 있으므로 `isWithdrawn` 은 false** 이고 가드를 통과한다.

- **실패 시나리오:** 2인 방이 정원을 채워 `COMPLETED` 가 된 뒤 **방장이 마이페이지 → 회원 탈퇴**를 한다.
  남은 멤버의 21 화면 하단에 이렇게 뜬다.

  > 다 타면 방장 **탈퇴한 사용자**님이 「합승 완료」를 눌러 방을 끝내요

  멤버 목록의 그 줄에도 「탈퇴한 사용자」 옆에 **「방장」 배지**가 붙는다
  (`showsHostBadge` = `isHost && !isMe` — 탈퇴를 안 본다, `MemberDisplay.kt:127-128`,
  `MatchWaitingScreen.kt:530`, `RideDetailScreen.kt:349`).
  게다가 **그 사람은 영원히 누를 수 없으므로** 이 안내문은 지키지 못할 약속이다
  (복구는 `CompletedPartySweeper` 의 30분 만료뿐).

- **같은 뿌리의 두 번째 결함 — 테스트가 이 경우를 잡지 못한다.**
  `WaitingHostNoticeTest.kt`(`방장이 탈퇴 회원이면 이름을 쓰지 않는다`)의 헬퍼가
  `withdrawn() = member(id = "", nickname = "탈퇴한 회원", ...)` 이다.
  즉 테스트가 **서버가 내지 않는 모양**을 검증한다. `id = ""` 가드를 지워도 그 테스트는
  `nickname.isNotBlank()` 를 통과해 실패할 수 있지만, **운영에서 실제로 오는 모양
  (`id` 있음 + `"탈퇴한 사용자"`)은 통과도 실패도 하지 않는다 — 아예 테스트되지 않는다.**

- **세 번째 — 주석이 서버 계약을 잘못 적고 있다(이 PR 이 쓴 게 아니라 **그 전제 위에 새 코드를 올렸다**).**
  `data/src/main/kotlin/com/moyeota/data/remote/dto/PartyDtos.kt:87-89` :
  > 「탈퇴 등으로 유저 요약을 못 찾으면 서버가 `MemberInfo(null, null, null, null, rideCount)` 을 그대로 내려보낸다」

  탈퇴는 요약을 **못 찾는 경우가 아니다**(위 soft delete). `MemberDisplay.kt:133-140` 와
  `MatchWaitingRoute.kt:694` 의 「방장이 탈퇴해 요약이 없거나」도 같은 잘못된 전제를 반복한다.

- **고치는 방향(참고):** 판정을 `publicId` 유무가 아니라 **이름이 사람 이름인가**로 바꾼다 —
  서버 상수 `User.DELETED_DISPLAY_NAME`(`"탈퇴한 사용자"`)과 앱 매퍼의 `"탈퇴한 회원"` **둘 다** 걸러야 한다.
  (참고로 두 문자열이 다른 것 자체는 PR 전부터 있던 불일치다 — `displayNickname` 의 폴백은 「탈퇴한 회원」,
  서버가 실어 보내는 값은 「탈퇴한 사용자」라서 **화면에는 「탈퇴한 사용자」가 찍힌다.**)

---

### B. `withHostSuccession` 이 서버가 명시한 클라이언트 계약과 **반대로** 동작한다 — 방장이 버튼을 잃는다

- **자리:** `presentation/src/main/kotlin/com/moyeota/presentation/feature/matching/MatchWaitingRoute.kt:610-613`
  ```kotlin
  internal fun Ride.withHostSuccession(hostPublicId: String?): Ride {
      val host = hostPublicId?.takeIf { it.isNotBlank() } ?: return this
      return copy(members = members.map { it.copy(isHost = it.id.isNotBlank() && it.id == host) })
  }
  ```

- **서버가 적어 둔 계약**(`backend/.../matching/sse/PartySseController.java:27`, `@Operation` description):
  > `member-left`: … `hostPublicId` 는 이 이벤트를 반영한 뒤의 방장 — 받을 때마다 `members` 의 host 표시를
  > 이 값으로 다시 지정한다. **단 null 이거나 목록에 없는 publicId 면 host 표시를 바꾸지 말고
  > 방 상세를 다시 받는다(이벤트 순서 역전 대비)**

  앱은 두 조건 모두 어긋난다.
  - `null`/빈 문자열 → 표시는 안 바꾼다(○) 그러나 **상세를 다시 받지 않는다**(×)
  - **목록에 없는 publicId → 전원을 `isHost = false` 로 다시 칠하고**(×, 계약은 「바꾸지 말라」)
    **상세도 다시 받지 않는다**(×)

  `withHostSuccession` KDoc(`:600-604`)은 이 선택을 「그게 맞다」고 적어 두었는데,
  서버가 그 경우를 위해 반대 지시를 명문화해 둔 것을 반영하지 않았다.

- **구체적 실패 시나리오(정원 3, 나는 2번째 합류자):**
  1. 멤버 `[u1(방장), me, u3]`, 상태 `COMPLETED`
  2. `u1` 이 나간다 → 서버 프레임 A `{publicId:"u1", status:"ACTIVE", hostPublicId:"me"}`
  3. 곧바로 `u3` 이 나간다 → 프레임 B `{publicId:"u3", status:"ACTIVE", hostPublicId:"me"}`
  4. 두 프레임은 `afterCommitExecutor.execute("party.sse", …)`(`PartyService.leave:135`) → Redis pub/sub
     경로를 **비동기로** 지나므로 **순서가 역전될 수 있다**(그래서 서버가 §위 단서를 달았다).
     B → A 순서로 도착했다고 하자.
  5. B 적용: `u3` 제거 → `[u1, me]`, 재도색 `me.isHost = true`
  6. A 적용: `u1` 제거 → `[me]`, 재도색 → **`hostPublicId`(이 시점엔 과거값 `"me"`)** … 이 예에서는 운 좋게 맞는다.
     값이 달라지는 조합이면 깨진다 — 예: `[u1(방장), u2, me]` 에서 `u1` 이탈(A: `host=u2`),
     `u2` 이탈(B: `host=me`) 이 **B → A** 로 도착하면
     B 적용 후 `[u1, me]`·`me.isHost=true` → A 적용 후 `[me]` 인데 `host="u2"` 는 목록에 없으므로
     **아무도 `isHost=true` 가 아니게 된다.**
  7. 결과: 서버가 보는 방장은 `me` 인데 앱은 `isHostedByMe = false`.
     이후 누군가 합류해 정원이 다시 차면(`member-joined` 는 `host` 를 바꾸지 않는다)
     **버튼은 끝까지 안 보이고**, 그 자리에는 내가 바로 그 사람인 안내문
     「다 타면 방장(가장 먼저 참여한 분)이 … 」이 뜬다.
  8. **자가 복구가 없다.** `hasMemberDrift`(`MatchWaitingRoute.kt:633-640`)는 **정원 대 인원수**만 본다 —
     방장 표시 모순은 보지 않는다. 폴백 폴링은 `realtimeAlive == false` 일 때만 돌고(`shouldPollFallback`)
     SSE 는 살아 있다. 즉 **재연결 스냅샷이 올 때까지 낡은 채로 남는다.**
     방을 끝낼 길은 30분 만료뿐이다 — 이 PR 이 fail-open 폴백(`host ?: isMe`)으로 막으려 한 바로 그 상태가
     delta 경로로 되살아난다.

- **snapshot 경로와 delta 경로가 갈리는 지점이다.** `WaitingSseStateTest.승계 - 증분으로 만든 방장 표시가
  스냅샷과 같다` 는 **깨끗한 한 건**만 비교한다. 역전·지목 실패 조합은 테스트가 없고,
  `승계 - 모르는 사람이 방장으로 지목되면 내 표시만 지워진다` 는 **깨지는 동작을 기대값으로 고정**해 두었다.

- **고치는 방향:** `hostPublicId` 가 목록에 없으면 표시를 손대지 않고 `readDetail()` 1회 —
  `applyDelta` 가 이미 `hasMemberDrift` 로 같은 일을 하므로 drift 판정에 조건 한 줄을 더하면 된다.

---

### C. `RideRepository.finishParty` KDoc 이 **이 PR 이 구현한 것과 정반대**를 지시한다 ★

- **자리:** `domain/src/main/kotlin/com/moyeota/domain/repository/RideRepository.kt:136-140`
  > `Backend #237(develop 머지, 2026-10 기준 **운영 미배포**)이 …`
  > `앱은 **아직 방장을 식별할 수단이 없다** — 서버가 주는 **`hostId` 는 내부 PK `Long`** 인데 멤버 목록에는 `publicId` UUID 뿐이다(Backend #240 요청).`
  > `그래서 **버튼 노출로 막지 못하고**, 실패 사유로 안내한다.`

- **세 문장 모두 같은 PR 안에서 거짓이 됐다.**
  | 주석 | 사실 |
  |---|---|
  | #237 운영 미배포 | `c53e008` 이 「#239 로 운영 배포」로 고쳤고 `docs/MODE-ROUTES.md:51` 도 「2026-10-15 운영 배포」다 |
  | `hostId` 는 내부 PK | `PartyDtos.kt:38-40` 이 「서버가 **지웠다**」고 적었고 `prod-openapi-3.json` 의 `PartyDetailResult` 에 `hostId` 가 **없다** |
  | 버튼 노출로 막지 못한다 | `canFinishParty(ride, taxiEnabled)`(`MatchWaitingRoute.kt:682`)가 `ride.isHostedByMe` 로 **막고 있다** |

- **왜 위험한가:** 여기는 인터페이스 계약 문서다. `f78bee3`(게이팅 전) 이 쓴 문장이 `beb4a52`(게이팅 후)에
  덮이지 않은 **동시 편집 잔재**이고, 이 주석만 읽은 사람은 「노출 게이팅은 불가능하다」는 결론에 이른다 —
  `canFinishParty` 의 방장 조건을 「근거 없는 추정」으로 오해해 되돌리는 변경이 바로 나올 수 있다.
  이 저장소가 세 커밋 메시지에서 반복한 「틀린 주석은 없는 주석보다 나쁘다」 기준에 그대로 걸린다.

---

### D. `PartyFinishException.isNotHost` KDoc — 「승계는 SSE 변경분에 실리지 않는다」

- **자리:** `domain/src/main/kotlin/com/moyeota/domain/model/PartyError.kt:116-120`
  > `방장이 나가면 다음으로 이른 멤버에게 자동 승계되고, 그 **승계는 SSE 변경분에 실리지 않는다** —`

- **같은 PR 이 그것을 실었다.** `beb4a52` 가 `MemberLeftPayload.hostPublicId`(`PartyDtos.kt:156-166`),
  `PartyEvent.MemberLeft.hostPublicId`, `withHostSuccession` 을 추가했고, 백엔드 `MemberLeft.java:5`
  (`record MemberLeft(UUID publicId, String status, UUID hostPublicId)`)에 실제로 실려 온다.
  C 와 같은 자리에서 생긴 같은 잔재다. 403 경로가 남는 이유는 「실리지 않아서」가 아니라
  「그 한 프레임을 **놓치면**」이다 — `MatchWaitingRoute.kt:419-423` 과 `docs/MODE-ROUTES.md:66-67` 은
  이미 그렇게 고쳐 적혀 있어, **같은 사실을 세 자리가 두 가지로 말한다.**

---

### E. `finishFailureMessage` KDoc — 「앱은 그게 누구인지 모른다」

- **자리:** `presentation/.../feature/matching/MatchWaitingRoute.kt:730-733`
  > `앱은 그게 누구인지 모른다(**멤버 목록에 합류 시각이 오지 않는다**) — 그래서 이름을 말하지 않고 조건을 말한다.`

- **38줄 위의 `finishHostNotice` KDoc(`:692-695`)이 정반대를 적는다**:
  > `서버가 멤버마다 `host` 를 내려 주므로 **그 사람이 누군지 알 수 있다**([hostDisplayNameOrNull])`

  한 파일의 인접한 두 KDoc 이 같은 질문에 다른 답을 한다.
  403 문구를 조건형으로 두는 **결정 자체는 옳다**(403 이 날 때는 내 스냅샷이 낡았다는 뜻이므로
  들고 있는 이름이 오히려 틀릴 수 있다). 틀린 것은 **그 이유로 적힌 근거**다.
  근거가 「합류 시각이 안 온다」로 남아 있으면, `joinedAt` 이 응답에 돌아오는 날
  「그럼 이름을 쓰면 된다」는 **잘못된 결론**으로 이어진다.

---

## 3. 의견

### A. `SignupGenderChoicesTest` 의 핵심 단정은 **토톨로지**다

- `SignupFieldSupport.kt:194-198`
  ```kotlin
  internal val SignupGenderChoices: List<GenderChoice> = listOfNotNull(
      GenderChoice(Gender.MALE, "남성"),
      GenderChoice(Gender.FEMALE, "여성"),
      UndisclosedGenderChoice.takeIf { ServerAcceptsUndisclosedGender },
  )
  ```
- `SignupGenderChoicesTest.선택 안 함은 서버가 그 값을 받을 때만 선택지에 들어간다`
  ```kotlin
  val exposed = SignupGenderChoices.any { it.gender == Gender.UNDISCLOSED }
  assertEquals(SignupGenderChoices.toString(), ServerAcceptsUndisclosedGender, exposed)
  ```
  목록이 **그 상수로부터 파생**되므로 상수가 `true` 든 `false` 든 이 단정은 **항상 통과**한다.
  KDoc 의 「**이 테스트가 노출 스위치의 유일한 감시자다** … 둘이 어긋나면 실패한다」는
  어긋남이 **구조적으로 불가능**하다는 사실과 맞지 않는다.
  나머지 네 테스트(라벨 고정·순서·중복 없음)는 실제로 뭔가를 잡는다 — 문제는 이 하나다.
  진짜 감시가 되려면 **화면이 목록을 돌려 그리는지**(= `ProfileSetupScreen` 이 칩을 직접 나열하지 않는지)를
  봐야 하는데, 그건 Compose 테스트 없이는 단위 테스트로 잡히지 않는다. KDoc 을 사실에 맞게 줄이는 편이 낫다.

### B. `PlaceMappers` 주석 — 「화면은 이 값을 Text 에 그대로 넣으므로」

- `data/src/main/kotlin/com/moyeota/data/remote/PlaceMappers.kt:19-20`
  > `화면은 이 값을 Text 에 그대로 넣으므로 빈 문자열이면 **주소 줄 한 칸이 비는 것으로 끝난다**.`
- 한 곳은 Text 가 아니다 — `HomeRoute.kt:77` `onFavoritePlaceClick = { onPlaceQuery(it.roadName) }` 는
  `roadName` 을 **검색어**로 쓴다(`MainNavGraph.kt:509-512` → `DestinationRoute(initialQuery = …)`).
  `roadName` 이 빈 즐겨찾기를 14 홈에서 누르면 **빈 검색어로 15 로 가** 아무 결과도 안 뜬다
  (「검색」 버튼을 누른 것과 같은 상태). 깨지지는 않지만 「한 칸 비는 것으로 끝」은 아니다.

### C. `PlaceDtos` 즐겨찾기 nullable 의 **인과 설명**이 한 단계 건너뛰었다

- `data/.../dto/PlaceDtos.kt:59-61`
  > `검색에서 roadName 이 빈 장소를 즐겨찾기에 저장하면 서버가 받은 값을 그대로 보관하므로 조회 응답에도 같은 값이 돌아온다`
- 앱이 보내는 값은 **`null` 이 아니라 `""`** 다 — 도메인 `Place.roadName` 이 non-null 이고 매퍼가 이미 `""` 로
  떨궜으며(`PlaceMappers.kt:23`), 요청 DTO `FavoritePlaceRequest.roadName` 도 non-null `String`
  (`PlaceDtos.kt:46`)이다. `""` 는 구 DTO(`String = ""`)로도 멀쩡히 파싱된다.
  즉 **앱이 만든 경로로는 그 실패가 나지 않는다.**
  그래도 nullable 자체는 옳다 — 백엔드 `FavoritePlaceRequest` 에 검증이 없고
  `FavoritePlaceEntity.roadName` 에 `nullable = false` 도 없어 다른 경로가 null 을 넣으면 그대로 돌아온다.
  고칠 것은 방어가 아니라 **문장**이다.
  (덧붙여 지금은 등록 자체가 500 이다 — `FavoritePlaceId`(backend)에 **무인자 생성자가 없다**.
  `_workspace/legal-site/README.md` 의 Backend #231 서술이 맞다.)

### D. `RegisterResponse` 주석의 필드 개수가 틀렸다

- `data/.../dto/AuthDtos.kt:79` : 「`UserResponse` 는 … `imageUrl` 이 붙어 **두 필드**가 됐지만」
- 실제는 **세 필드**다 — `record UserResponse(UUID uuid, String name, String imageUrl)`
  (backend `user/local/dto/UserResponse.java:5`, `prod-openapi-3.json` 도 `uuid`/`name`/`imageUrl` 셋).
  `AuthMappersTest` KDoc 의 「서버 `UserResponse(uuid, name)`」도 같은 자리에서 한 칸 모자라다.
  `ignoreUnknownKeys` 가 흘리므로 **동작에는 영향이 없다.**

### E. `docs/RELEASE.md` 의 AAB 검증 명령은 versionCode 를 **확인해 주지 못할 수 있다**

- `docs/RELEASE.md` 4-1 에 새로 들어간
  ```bash
  unzip -p …/app-release.aab base/manifest/AndroidManifest.xml | strings | grep -E '^[0-9]{8}$|^20[0-9]{2}\.'
  ```
- 경고 자체는 **확실히 참이다** — `app/build.gradle.kts:66,69` 의 기본값이 `"1"` / `"1.0"` 이라
  주입 없이 치면 `versionCode=1` 이 박힌다.
- 다만 같은 PR 의 `_workspace/release-build-verify.md:105` 가 「`bundletool` 이 없고 `apkanalyzer` 도
  AAB 의 protobuf 매니페스트를 못 읽어 **직접 디코더를 썼다**(`scratchpad/aabmanifest.py`)」고 적고 있다.
  문서에 남은 `strings | grep` 는 그 디코더가 아니다. 성공 시 값이 보일 가능성은 있으나
  (aapt2 pb 가 속성 원문 문자열도 함께 담는다) **실패 시(=1) 출력이 비는데, 그건 grep 불일치와 구분되지 않는다.**
  **실행해 보지 못했다**(빌드 금지) — 확인하지 못한 영역으로 남긴다.

### F. `8738679` 은 같은 이유가 적용되는 코드의 **절반만** 지웠다

- 커밋 사유: 「화면이 다시 붙을 때 **서버를 안 거치고도 그럴듯한 화면이 나오는** 경로라 진짜 연동이 빠진 것을 가린다」
- 같은 구조가 남아 있다 — `DummyRideRepository.getNearbyParties()`(`:25`)도 호출처가
  presentation·app 에 **0곳**인데 하드코딩된 가짜 방·가짜 멤버(성윤/민지)를 들고 있고,
  `RemoteRideRepository:63` 이 그걸 위임하며, **릴리스에 인스턴스가 실제로 생성된다**(`AppContainer.kt:78`).
  이 PR 의 판단 기준을 그대로 적용하면 함께 지울 대상이다. (이번 PR 범위를 넓히라는 뜻은 아니다.)

### G. 날짜가 붙은 조사 보고서의 전제가 PR 안에서 뒤집혔다

`_workspace/backend-237-impact.md`(6db78d7)는 「앱이 방장을 식별할 수단이 없다 / #237 운영 미배포 /
방 상세에 `hostId` 추가」를 전제로 쓰였고, 두 커밋 뒤 `beb4a52` 가 전부 뒤집었다
(배포됨 / `hostId` 제거 / `members[].host` 로 식별 가능).
보고서 머리에 「작성: 2026-10-15 · 조사 범위 …」가 명시된 **시점 기록**이므로 거짓 문서는 아니다.
다만 같은 PR 안에 「아직 식별 못 한다」와 「이미 식별한다」가 함께 들어가니,
`backend-237-impact.md` 머리에 한 줄(「§x 는 `beb4a52` 로 해소됨」)을 남기면 읽는 사람이 헤매지 않는다.

### H. 사실 확인 통과 — 바꿀 것 없음

- **「가장 먼저 참여한 분」은 참이다.** `Party.hostId()`(`Party.java:220-226`)가
  `min(Comparator.comparing(joinedAt).thenComparing(memberId))` 다. tie-break 가 `memberId` 인 것까지
  `PartyDtos.kt:84` 와 `Ride.kt` KDoc 이 적어 두었다.
- **「방장이 나가면 자동 승계」** ✓ 같은 파생값 계산.
- **마지막 멤버 이탈에는 `member-left` 가 오지 않는다** ✓ `PartyService.leave:128-130` 이 `CANCELED` 면 `closed` 만 보낸다.
- **`member-joined` 의 `host` 는 사실상 늘 false** ✓ `PartyService.join:106` 이 `saved.isHost(memberId)`,
  SSE 문서도 「host 는 항상 false」.
- **`imageUrl` 은 이제 null 로 오지 않는다** ✓ `ProfileImages.toUrl(null)` 이 기본 키 URL 을 만든다.
  (단 §2-A 와 함께 읽으면 「null 인 경우는 유저 요약이 없는 멤버」라는 설명은 **그런 멤버가 거의 안 생기므로**
  사실상 「null 이 안 온다」가 맞다 — 결론은 같고 이유만 다르다.)
- **법적 문서**: `privacy` 제3조 표가 4행 → **5행**(실제 5개 `<th scope="row">` 확인), 「네 가지」→「다섯 가지」,
  「앞의 세 가지」→「앞의 네 가지」가 일관되게 고쳐졌다. 추가된 행의 사실성도 코드와 맞는다 —
  즐겨찾기 등록이 좌표를 보낸다(`DestinationRoute.kt:166` → `PlaceApi` → `FavoritePlaceRequest.latitude/longitude`),
  탈퇴 시 삭제된다(`FavoritePlaceUserDeletionListener`), **운영 스펙에 favorite-place `DELETE` 가 없다**
  (`prod-openapi-3.json`: `/api/v1/users/me/favorite-places` 는 `get`·`post` 뿐) → 「제15조의 이메일로 요청」이 맞다.
  제15조에 실제로 메일 주소가 있다(`privacy/index.html:663-669`, `codingforest17@gmail.com`).
  제1조 표2 에 더한 「출발지·도착지를 입력할 때」도 참이다 — 지도 없는 15 화면이 위치를 구독한다
  (`DestinationRoute.kt:203-209`).
- **랜딩 canonical/og:url** → `www` 로 둘이 **함께** 고쳐졌다 ✓
- **`ProductClaimsTest` 와 충돌 없음** — 그 테스트가 보는 범위는 **가입 전 문구**(01–04·12)뿐이고
  이번에 들어간 문구는 21·22 화면 것이라 교집합이 없다. 새 문구 쪽은
  `FinishFailureMessageTest.다시 누르면 된다고 약속하지 않는다` 와
  `WaitingHostNoticeTest.안내문은 기다리거나 다시 시도하라고 말하지 않는다` 가 금지어로 잠근다 —
  **둘 다 실제 반환값을 보므로 토톨로지가 아니다.**
- **`roadName` 회귀 테스트는 진짜 잡는다** — 테스트의 `Json { ignoreUnknownKeys = true }` 가
  `NetworkModule.kt:24` 와 동일 설정이고, DTO 를 `String = ""` 로 되돌리면 `"roadName":null` 에서
  `kotlinx.serialization` 이 터져 **세 테스트 모두 실패한다**(명시적 null 은 기본값으로 떨어지지 않는다).

---

## 4. 설명 대조 — 커밋 메시지가 코드와 맞는가

| 커밋 | 주장 | 판정 |
|---|---|---|
| `01365b1` | 명시적 null 은 기본값으로 안 떨어진다 / 전역 `coerceInputValues` 는 60개 필드에 걸린다 | **참**. `coerceInputValues` grep 0건, 테스트 설정이 운영 설정과 동일 |
| `01365b1` | 서버 `Place` 는 record 라 검증이 없다 | **참** (`place/search/domain/Place.java` — 1줄 record, `KakaoPlaceClient.toPlaceOrNull` 의 NPE catch 는 좌표 파싱용이라 null roadName 을 막지 못한다) |
| `5fc8da4`/`249f33f` | 운영 gender 가 `["MALE","FEMALE","UNDISCLOSED"]` | **참** (`prod-openapi-3.json`) |
| `5fc8da4` | 09 IdentityVerifyScreen 은 NavGraph 미연결(죽은 화면) | **참** — 참조가 자기 Preview 하나뿐 |
| `5fc8da4` | `Gender` 에 상수를 더해도 분기가 안 깨진다 | **참** — `when (gender)` 전수 분기 0건 |
| `6db78d7` | `PATCH /users/me` 가 `imageKey` / 옛 이름은 조용히 무시된다 | **참** (`UpdateProfileRequest.java:13`, `UserController:45`, 스펙 일치) |
| `6db78d7` | `imageUrl` 이 null 로 오지 않게 됐다 | **참** (`ProfileImages.toUrl`) |
| `f78bee3` | 403·409 가 각각 두 뜻 / 검사 순서가 표 순서 | **참** (`Party.java:233-242`) |
| `f78bee3` | 「가장 먼저 참여한 분」 | **참** (`Party.hostId()`) |
| `f78bee3` | 「승계는 SSE 에 안 실린다」 | **그때는 참, 지금은 거짓** → §2-D |
| `beb4a52` | `members[].host` 가 상세·`connected` 양쪽에 / `member-left.hostPublicId` 가 승계를 알려 준다 | **참** (`PartyDetailResult.java:33`, `MemberLeft.java:5`, `PartyService.leave:131-134`) |
| `beb4a52` | 서버가 `hostId`(Long)를 지웠다 | **참** — 스펙·소스 모두 없음 |
| `beb4a52` | 배지는 내 줄을 뺀다(폴백이 만든 거짓 true 는 내 줄 하나) | **참** — 다만 **탈퇴 방장 줄**에는 붙는다(§2-A) |
| `8738679` | `getMyRides` 호출처 0 · 서버에 이력 API 없음 | **참** — 단 같은 구조가 남았다(§3-F) |

---

## 5. 확인하지 못한 영역

| 영역 | 왜 못 했나 |
|---|---|
| 컴파일·테스트 실제 통과 | **빌드 금지**. 시그니처 변경(`canFinishParty`, `updateProfile`, `withMemberLeft`)의 호출처·import 는 grep 으로 전수 확인했으나 컴파일러로 확인한 것은 아니다 |
| 칩 3분할 레이아웃(360dp, font_scale 2.0) | 실기 필요. `heightIn(min=44.dp)` + `maxLines = 2` + 가로 6dp 패딩이라 잘림은 안 날 구조로 보이지만 측정은 못 했다. `_workspace/gender-undisclosed-qa.md` 에 실측 기록이 있다 |
| `strings` 로 AAB versionCode 가 보이는지 | 빌드·AAB 생성 금지(§3-E) |
| `selectable`/`selectableGroup` 의 실제 TalkBack 출력 | 실기 필요. 코드상으로는 `Role.RadioButton` + `selected` 가 의미 트리에 실린다 |
| 카카오 `road_address_name` 이 실제로 `null` 인지 `""` 인지 | 외부 API. **어느 쪽이든 이번 방어는 옳다**(백엔드 record 가 null 을 막지 않으므로) |
| 운영 SSE 프레임 실물 | 운영 쓰기·장기 구독 금지. 백엔드 소스와 `PartySseController` 의 `@Operation` 문서로 대체 판정 |
| 다른 에이전트가 지금 만들고 있는 미추적 파일 2건 | `_workspace/dead-code-sweep.md`·`release-build-verify-2.md` 는 **untracked** 라 PR #52 에 없다. 검토 범위 밖 |
