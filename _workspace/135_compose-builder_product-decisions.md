# compose-builder — 제품 판단 3건 구현 (2026-10-28)

입력: `_workspace/00_input.md` 의 1·2·4번. 3번(`adjustResize`)은 qa-verifier 소유라 손대지 않았다
(`app/src/main/AndroidManifest.xml` 무변경).

API·DTO·Repository·domain 변경 **0건**. `presentation/` 안에서만 끝났다.

---

## 결과 요약

| 과제 | 파일 | 추가/수정 |
|---|---|---|
| 1. 16 「1명」 안내 | `feature/home/DestinationConfirmModal.kt` | `soloCapacityNotice()` 신설 + 칩 행 아래 렌더 |
| 2. 24 채팅 글자 수 + 전송 잠금 | `feature/chat/ChatScreen.kt` | `chatInputUi()`·`ChatInputUi`·`COUNTER_VISIBLE_FROM` 신설, `sendEnabled` 교체, 카운터 렌더 |
| 3. 17 내 방 제외 | `feature/explore/ExploreRoute.kt`, `ExploreScreen.kt` | `exploreList()`·`ExploreList` 신설, `myPartyHidden` 파라미터, 빈 상태 문구 분기 |

`ChatRoute.kt` 는 **한 글자도 고치지 않았다**(전송 시 1000자 검사 보존). `JoinConfirmScreen.kt` 도
읽기만 했다(`joinGate`/`ALREADY_JOINED` 보존).

---

## 과제 1 — 16 도착지 확인: 「1명」을 고르면 혼자 출발한다고 알려 준다

### 변경
- `DestinationConfirmModal.kt:707-730` — `internal fun soloCapacityNotice(capacity: Int): String?`
  (1 이면 「같이 탈 사람을 받지 않고 혼자 출발해요」, 아니면 null)
- `DestinationConfirmModal.kt:436-463` — 칩 행(`listOf(1, 2, 3)`) **바로 아래** 렌더.
  13sp Medium + `GrayMute`, 빨강·아이콘 없음
- `DestinationConfirmModal.kt:110-111` — 화면 KDoc 「선택 규칙」에 한 줄 추가

선택지(`listOf(1, 2, 3)`)와 전송 값(`MatchConditions(capacity = peopleCount)`)은 **그대로**다.

### 판단이 갈렸던 지점
**「높이가 변하는 걸 감수할지 자리를 늘 비워 둘지」 — 감수를 골랐다. 근거는 실측이 아니라 소스다:
움직이는 것이 애초에 없다.**

이 카드는 `MapSheetScaffold` 의 `sheetDetail` 안에 있고, 그 영역은
`core/designsystem/component/MapSheetScaffold.kt:115-117, 196-197` 에서
`detailMaxDp = containerHeight - mapRevealHeight - collapsedSheetHeight` 로 **앵커가 높이를 정하고
내부는 `verticalScroll`** 이다 — 콘텐츠가 시트 높이를 정하지 않는다. 그래서 안내 줄이 생겨도
시트 높이·지도 노출 높이·카메라 fit·CTA 위치가 **전부 그대로**고, 아래의 「걸어서 가까운 방」
섹션만 ~27dp 밀린다. 손가락이 닿아 있는 칩 행은 안내보다 **위**라 제자리다(그래서 안내를 칩 행
위에 두는 안은 버렸다 — 그쪽은 방금 누른 칩이 움직인다).

상시 빈 자리를 두는 안을 버린 이유: 기본값이 3명이라 **거의 항상** 비어 있는 ~27dp 가 되고,
작은 폰에서는 그 상세 영역이 이미 스크롤 상태라(360×640 기준 가용 ~224dp, 콘텐츠 ~256dp)
추천 카드 한 줄만큼을 영구히 먹는다.

**요금과의 관계**: 1명이면 `farePerPersonWon(total, 1) == total` 이라 칩의 「총 7,900원」과 푸터의
「예상 요금 1인 7,900원」이 같은 수가 된다. 그건 혼자 타는 방의 **사실**이라 모순이 아니다.
그래서 요금 문구는 손대지 않았고, 안내에 「요금 전액 부담」을 덧붙이지도 않았다 — 같은 사실을 두
자리에서 말하면 한 줄 안내가 두 줄로 늘어난다. (`SoloCapacityNoticeTest` 가 이 등식을 고정한다.)

---

## 과제 2 — 채팅 입력: 실시간 글자 수 + 초과 시 전송 잠금

### 변경
- `ChatScreen.kt:91-103` — `INPUT_HARD_LIMIT` KDoc 갱신(한도를 말하는 주체가 카운터로 바뀌었다.
  **값 2000 은 그대로**)
- `ChatScreen.kt:105-127` — `internal const val COUNTER_VISIBLE_FROM = MAX_CONTENT_LENGTH - 100`
- `ChatScreen.kt:129-180` — `ChatInputUi` + `chatInputUi(input, sending)` (카운터 문구·초과
  여부·전송 가능을 **한 함수에서 함께** 뽑는다)
- `ChatScreen.kt:273-277` — `val sendEnabled = inputUi.sendEnabled` (옛 `input.isNotBlank() && !sending` 교체)
- `ChatScreen.kt:502-522` — 입력 Row **바로 위**에 카운터(12sp, 우측 정렬. 초과 시 Bold + `Danger500`)
- `ChatScreen.kt:225-230` — 화면 KDoc 「유효값」 갱신
- `ChatRoute.kt` — **무변경** (`send()` 의 1000자 검사·안내 메시지 보존)

### 판단이 갈렸던 지점
**카운터를 「항상」이 아니라 「한도 100자 전(900자)부터」 띄웠다. 근거는 두 개고, 두 번째가 결정적이다.**

1. 평상시 채팅은 수십 자다. 「12/1000자」가 늘 떠 있으면 이 화면에서 가장 중요한 영역(말풍선)의
   높이를 상시로 빼앗는다. 이 코드베이스가 「후보 0건이면 섹션을 안 그린다」·「추정 실패면 칩을
   감춘다」로 일관되게 택한 쪽과 같다.
2. **「넘은 순간에 띄운다」가 더 나쁘다.** 1001자에서 처음 나타나면 버튼이 잠기는 순간과 줄이
   생기는 순간이 같아져서, ① 경고가 아니라 사후 통보가 되고 ② 입력 바가 그 프레임에 한 줄만큼
   높아지며 **누르려던 전송 버튼이 위로 움직이면서** 잠긴다. 한도보다 앞서 띄우면 잠기는 순간
   카운터는 이미 화면에 있고 **색만** 바뀐다 — 그 순간 레이아웃은 움직이지 않는다.
   (`ChatInputLimitTest.카운터는 전송이 잠기기 전에 떠 있다` 가 이 부등식을 고정한다.)

레이아웃이 한 번 움직이는 지점은 900자 **하나**로 몰아 뒀다. 0자에서 2000자를 한 번에 붙여넣는
경우만은 등장과 잠김이 같은 프레임인데, 그때도 빨간 「2000/1000자 — 줄여야 보낼 수 있어요」가
이유를 말한다. (말풍선 위에 overlay 로 띄워 레이아웃을 아예 안 건드리는 안은 버렸다 — 마지막
메시지를 가린다.)

**길이는 `trim()` 뒤에 센다** — `ChatRoomViewModel.send()` 와 같은 기준이다. 안 맞추면 공백을
섞은 사용자에게 「999자인데 버튼이 잠김」이 보인다.

**초과 문구에 「줄여야 보낼 수 있어요」를 붙였다** — 회색 버튼과 빨간 숫자 사이의 인과를 사용자가
추측하지 않게 한다. 색만으로 말하면 색 구분이 어려운 사용자에게는 아무 말도 하지 않은 것과 같다.

### 솔직하게 적어 둘 것
`ChatRoute.kt:557-561` 의 「메시지는 1000자까지 보낼 수 있어요」 배너는 이제 **화면에서 도달하지
않는다**(버튼이 먼저 잠근다). 지시대로 방어로 남겼고, 과거 결함과는 성격이 다르다 — 그때는
사용자에게 **아무 피드백도 없었고**, 지금은 더 이른 자리(카운터)가 그 역할을 더 잘한다. 다만
「도달 불가능한 안내」가 다시 하나 생긴 것은 사실이라 적어 둔다.

---

## 과제 3 — 17 탐색: 이미 참여 중인 방을 목록에서 제외

### 변경
- `ExploreRoute.kt:327-358` — `UiState.Success` 분기에서 `exploreList(...)` 적용 후 `ExploreScreen` 호출
- `ExploreRoute.kt:361-418` — `internal data class ExploreList` + `internal fun exploreList()` +
  `private fun samePartyId()`
- `ExploreScreen.kt:258-276` — `parties` KDoc 갱신 + `myPartyHidden: Boolean = false` 파라미터
- `ExploreScreen.kt:423-427, 447` — `PeekSummaryRow`·`PartyList` 로 전달
- `ExploreScreen.kt:487-499` — `emptyListCopy(myPartyHidden)` 신설(17 접힘 요약과 18·19 빈 상태가
  **같은 두 줄**을 쓴다)
- `ExploreScreen.kt:814-837` — `EmptyListNotice(myPartyHidden)`

### 판단이 갈렸던 지점

**① 마커도 같이 빠지는가 — 빠진다. 구조상 분리할 수 없다.**
지도 마커는 `ExploreScreen.kt:360` 의 `ExploreMap(rides = parties, ...)` → `:941 rides.forEach`
이고, 카드·헤더 N·peek 요약도 모두 같은 `parties` 파라미터에서 나온다. 그래서 필터를 그 한
파라미터에 걸면 **네 자리가 함께** 빠진다(마커는 `key(ride.id)` 로 묶여 있어 목록에서 빠질 때
dispose 된다). 「목록만 거르고 마커가 남는」 상태는 만들 수 없다.

**② 필터를 어느 층에 두는가 — ViewModel 이 아니라 Route(화면 렌더 시점).**
- `activeRide` 는 `ActivePartyViewModel`(NavHost 바깥 단일 인스턴스)의 값이고 `ExploreViewModel`
  은 그걸 모른다. 넘기면 탐색 ViewModel 에 두 번째 데이터 출처가 생긴다.
- `UiState.Success(parties, stale)` 는 **서버가 말한 것**이어야 한다. 거기서 걸러 버리면
  `stale`(갱신 연속 실패)과 목록이 다른 뜻을 갖게 되고(「서버 목록이 낡아서 빈 것」과 「내가 빼서
  빈 것」을 구분할 수 없다), 폴링 판정 `hasParties()` 가 표시용 목록을 보게 된다.
- 결론: `stale` 로직과 **충돌하지 않는다.** 두 값은 서로를 보지 않는다.

**③ 판정 기준 — `activeRide?.id` 다. 지시가 맞았고, 확인했다.**
`PartyMappers.kt:67-69 placeholderMembers` 가 목록 응답에 식별자 없는 멤버를 만들고 `User.isMe`
기본값은 false 라, **목록 데이터로는 `isMe` 가 영원히 false** 다. 그 사실을
`ExploreListFilterTest.목록의 멤버로는 나를 찾을 수 없다` 로 못 박아 뒀다(나중에 기준을
`members.any { it.isMe }` 로 바꾸려는 시도를 막는다).
id 비교는 `Long` 으로 먼저 본다 — 운영에서 두 값은 모두 `Long.toString()`(목록 `partyId`, 상세
`id`)이라 지금은 모양이 같지만, `ActiveParty.kt:149 needsDetailAfterRemember` 가 같은 이유로
Long 비교를 쓴다. 숫자로 안 읽히는 id 는 문자열로 비교한다(Preview 의 `"ride-1"`).
`activeRide` 의 **단계는 보지 않는다** — 끝난 방은 `status = ACTIVE` 목록에 아예 오지 않는다.

**④ 걸러낸 뒤 0건이 되는 경우 — 문구를 갈랐다.**
기존 빈 상태는 「이 근처엔 동승이 없어요 / 지도를 움직여 보세요」인데, 주변에 방이 **내 방뿐**일
때 그건 거짓말이다(방은 있고 그게 내 방이라서 목록이 빈 것이다). 그래서
`myPartyHidden == true` 이고 0건이면 **「지금은 내 방뿐이에요 / 지도를 움직여 다른 방을
찾아보세요」** 로 바꿨다. `myPartyHidden` 은 **실제로 걸러낸 실적이 있을 때만** 켜진다 — 내 방이
다른 동네에 있어 이 범위에 애초에 없었으면 꺼진 채다(그때 「내 방뿐」은 또 다른 거짓말이다).

문구로 **배너 위치를 가리키지 않았다**(「위 배너를 누르세요」를 쓰지 않았다): 시트를 끝까지 올린
FULL 상태에서는 그 배너가 시트에 가려 보이지 않아, 위치를 말하는 문장이 그 순간 틀린 말이 된다.
목록이 **비지 않았을 때는** 아무 문구도 더하지 않았다 — 「내 방 1개는 뺐어요」는 돌아갈 길이 이미
배너로 있는 상황에서 더해지는 소음이다.

---

## 지시 중 사실이 달랐던 것 (반박)

### 1. 「17 의 내 방 카드를 눌러도 20 에서 CTA 가 잠긴 막힌 화면에 닿는다」 — **아니다**
`JoinConfirmScreen.kt:312-318` 은 `gate == ALREADY_JOINED` 일 때 CTA 를 **「방으로 돌아가기」로
교체**하고(잠그지 않는다) 배너도 `NoticeKind.INFO` 로 띄운다. 즉 오늘도 막힌 길은 아니고,
**더 긴 우회로**다: 17 → (탭) 20 → 방 상세 1회 조회 → (탭) 21. 배너는 17 → (탭) 21 이다.

**그래도 거르는 결정은 유지했다**, 근거를 바꿔서: 그 카드는 **거짓말을 한다**. 「합류」 버튼이
남의 방과 똑같이 활성이고, 인원 배지는 내가 들어가기 전의 수처럼 읽힌다 — 눌러서 20 에 가야
비로소 「이미 참여 중」을 알게 된다. 돌아갈 길이 이미 더 짧게 있으니 그 카드는 소음이다.
이 근거를 `exploreList` KDoc 에 적었다(「막힌 화면」이라고 적지 않았다 — 사실이 아니라서).

### 2. 기준선 「경고 0」 — **실제로는 경고 1건이 있다(내 변경 전부터)**
`presentation/.../feature/auth/AccountTypeScreen.kt:323` 의 `quadraticBezierTo` deprecation.
그 파일은 **커밋된 상태 그대로**이고(`git status` 깨끗) 내가 건드리지 않았다. 기준선을 잴 때
`:presentation:compileDebugKotlin` 이 UP-TO-DATE 였던 것으로 보인다 — 실제로 컴파일하면 나온다.
한 줄(`quadraticTo` 로 교체)이면 끝나지만 **내 소유 파일이 아니라 두었다.** 소유자가 고치면 된다.

### 3. 「테스트 802개」의 출처
`:presentation:testDebugUnitTest` 단독은 **421개**다. 802 = presentation 421 + **data 381**.
아래 집계는 두 모듈을 함께 센 값이다.

---

## 테스트 (+28: 802 → 830)

새 파일 3개. 전부 순수 함수 단위 테스트이고 **테스트 의존성은 추가하지 않았다**.

### `presentation/src/test/.../feature/home/SoloCapacityNoticeTest.kt` (7개)
- 안내가 **1에서만** 뜬다 / 2·3 과 칩에 없는 값에서는 없다 → 안내가 상시 경고가 되거나 1에서
  사라지는 회귀를 잡는다
- 문구가 「받지 않는다」와 「혼자 출발」을 **둘 다** 말한다 → 하나만 남기는 축약을 잡는다
- 내부 용어(정원·1/1·capacity·꽉) 누출을 잡는다
- CTA 와 같은 말(「같이 탈 사람」)을 쓰는지
- `farePerPersonWon(x, 1) == x` → 안내가 요금 표시와 모순되지 않는 근거가 깨지는 것을 잡는다

### `presentation/src/test/.../feature/chat/ChatInputLimitTest.kt` (11개)
- **trim 기준 어긋남**: 공백 섞인 1002자(= 서버로 갈 값 1000자)에서 카운터가 1000을 말하고 전송이
  살아 있는지 → 화면과 ViewModel 이 다른 길이를 보기 시작하면 깨진다
- **이유 없는 잠금**: 잠긴 길이(1001·1500·2000)에서 `counter != null` 을 요구 → 카운터를 지우거나
  임계값을 한도 뒤로 미루면 깨진다 (= 「조용히 막힘」으로의 회귀를 잡는 테스트)
- `COUNTER_VISIBLE_FROM < MAX_CONTENT_LENGTH` 부등식 → 등장과 잠김이 같은 프레임이 되는 것을 잡는다
- 경계값 1000 전송 가능 / 1001 잠김, 공백만 입력 비활성, 전송 중 잠김
- 한도가 상수 하나(1000)라는 것 → 화면이 다시 자기만의 숫자를 들게 되는 것을 잡는다
- 초과 문구가 「무엇을 해야 하는지」를 말하는지

### `presentation/src/test/.../feature/explore/ExploreListFilterTest.kt` (10개)
- 내 방만 빠지고 **순서까지** 그대로인지 / 미참여면 그대로 통과
- **남의 방이 함께 사라지는 사고**: `2`·`22`·`220`·`222` 중 `22` 만 빠지는지 → 비교가 느슨해지면
  합류 가능한 방이 지도에서 사라져 찾을 길이 없다(거르기가 풀리는 쪽보다 위험하다)
- `myPartyHidden` 이 **실제로 걸러낼 때만** 켜지는지(빈 목록·다른 동네·빈 id) → 빈 상태 문구가
  거짓말을 하는 경우를 잡는다
- 내 방뿐이면 0건 + 플래그 on → 갈라 둔 문구의 근거
- id 모양 차이(`022` ↔ `22`)·숫자 아닌 id(`ride-1`) 비교
- **목록 멤버로는 `isMe` 를 못 쓴다**는 사실 → 기준을 `members.any { it.isMe }` 로 바꾸려는 시도를 잡는다

---

## 빌드·테스트 결과

```
$ ./gradlew :app:assembleDebug :presentation:testDebugUnitTest :data:testDebugUnitTest --console=plain
...
> Task :presentation:compileDebugKotlin
w: .../feature/auth/AccountTypeScreen.kt:323:13 'fun quadraticBezierTo(...)' is deprecated.   ← 내 변경 전부터 있던 것
> Task :presentation:testDebugUnitTest
> Task :data:testDebugUnitTest
> Task :app:assembleDebug
BUILD SUCCESSFUL in 10s
```

집계(test-results XML):

```
presentation: tests=449 failures=0 errors=0   (기준선 421 → +28)
data:         tests=381 failures=0 errors=0   (무변경)
합계:          830 / 기준선 802
```

---

## 실기로 봐야 하는 것 (소스로 확정할 수 없었다)

1. **[과제 1 · 작은 폰] 안내 줄이 시트 상세 영역의 아래 경계에 걸리는가.**
   360×640 급 기기에서 상세 가용 높이는 ~224dp 인데 칩 행이 끝나는 지점이 ~210dp 로 계산된다 —
   안내 줄(~27dp)이 **접힌 경계에 반쯤 걸릴 수** 있다. 시트는 스크롤되므로 잘리지는 않지만,
   「1명」을 고른 직후 안내가 화면에 **안 보일** 가능성이 있다. 자동 스크롤로 끌어올릴 수는 없다
   (상세 영역의 `ScrollState` 는 `MapSheetScaffold` 내부이고 그 파일은 `core/designsystem` 소유).
   360dp 폰에서 1 → 2 → 1 을 눌러 보고, 안 보이면 그때 스크롤 노출 방법을 따로 논의해야 한다.
2. **[과제 1] 큰 글꼴 설정(1.3x 이상)에서 안내가 두 줄로 흐를 때의 모양.** 의도적으로
   `maxLines` 를 두지 않았다(잘린 안내보다 두 줄이 낫다). 두 줄일 때 카드가 어색하지 않은지.
3. **[과제 2] 900자에서 카운터가 처음 뜰 때 입력 바가 올라가는 양과 체감.** 12sp 한 줄 + 8dp 라
   ~22dp 로 추정하지만 실측이 아니다. 말풍선 목록이 그만큼 줄면서 마지막 말풍선이 잘리지 않는지
   (자동 스크롤 조건 `stickToBottom` 과 함께 본다 — `listScroll.maxValue` 가 바뀌어 재발화한다).
4. **[과제 2] 키보드가 올라온 상태에서 카운터가 가려지지 않는지.** `imePadding()` 과 함께 입력 바
   Column 안에 있으므로 따라 올라가야 하지만, 이건 qa-verifier 의 `adjustResize` 작업(과제 3)과
   **같은 자리**다 — API 29 측정 때 카운터까지 함께 봐 달라.
5. **[과제 2] 2000자 붙여넣기.** `singleLine = true` 입력창에 2000자가 들어간 상태의 가로 스크롤
   거동(기존 동작이지만 이제 그 상태를 **실제로 보게 된다** — 전에는 전송하면 비워졌다).
6. **[과제 3] 내 방 마커가 지도에서 실제로 사라지는지.** 구조상 함께 빠지지만(`key(ride.id)` dispose)
   눈으로 확인해야 한다. 재현: 방 생성 → 21 에서 **뒤로가기**(`onExitKeepingParty`, 방 유지) → 17.
   내 방의 핀과 카드가 **둘 다** 없고, 맨 위 배너로 한 번에 21 로 돌아가는지.
7. **[과제 3] 「지금은 내 방뿐이에요」 빈 상태의 모양.** 주변에 방이 내 방 하나뿐인 범위로 지도를
   옮겨야 재현된다(또는 다른 방들이 범위 밖으로 나가게 줌인). peek(17) 한 줄 요약과 리스트(18·19)
   빈 상태 **두 자리**를 다 본다 — 19sp 제목이 좁은 폰에서 두 줄로 흐르면 peek 시트(96dp 고정)에서
   눌릴 수 있다.
8. **[과제 3] 목록이 낡은 순간의 겹침.** `stale` 표시가 떠 있는 동안에도 내 방이 계속 걸러지는지
   (`activeRide` 는 별도 홀더라 폴링 실패와 무관해야 한다).
