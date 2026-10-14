# 127 · 거짓 고지 전수 조사 — 없는 기능을 약속하던 문구 정리

- 작업일: 2026-10-13
- 브랜치: `feat/backend-sync-sse` (HEAD `a93e169`) — **커밋하지 않았다. 워킹트리에만 있다.**
- 프론트 범위: `presentation/src/main` 전체 (66개 Kotlin 파일, 한국어 문자열 리터럴 714줄 전수)
- 백엔드 근거: 읽기 전용 체크아웃 `…/scratchpad/backend-dev` (`src/main/java/team/codingforest/moyeota`)
- 발단: 1차 배포(동승 모드)에서 **성별 조건 매칭·위치 공유·요금 정산·긴급 신고가 없는데** 그것을
  자랑하는 온보딩·로그인 문구가 남아 있었다. 기능이 아니라 **문구가 틀렸다.**

## 0. 서비스 오너가 확정해 준 사실 (코드로 재확인함)

| 확정 사실 | 코드 확인 결과 |
|---|---|
| 성별 조건 매칭은 **없다** | ✅ 백엔드 `grep -i gender` → `user/local`·`user/profile`·`user/users/domain/enums` 뿐. **`matching` 패키지 0건.** 프론트도 `Gender` 가 `AuthMappers`·`AuthDtos`(가입 요청)에만 쓰이고 `PartyDtos` 에 성별 필드가 없다 |
| 채팅방은 **합류하는 순간** 열린다 | ✅ `PartyService` 가 방 생성(:78)·합류(:104) 모두 `PartyMemberJoinedEvent` 를 발행하고, `ChatRoomMatchingListener.on(…)` → `MatchingChatRoomSteps.sync` 가 방이 없으면 **그 자리에서 만들고** 멤버를 넣는다. 정원이 차는 것과 무관하다 |
| 정원이 차도 **나갈 수 있다** | ✅ 동승 모드에서 `canLeaveParty(MATCHED, taxiEnabled = false) == true` (`MatchWaitingRoute:580`). 서버 `leave` 는 ACTIVE/COMPLETED 에서 허용 |

> 「합류 시 채팅방 열림」은 백엔드 `PartyController` javadoc(「정원이 차면 … 채팅방이 열린다」)과
> 프론트 `MainNavGraph:626` 주석(「정원이 찼다 = 채팅방이 생기는 시점」)이 **둘 다 틀리게 적고 있다.**
> 구현이 오너 말대로다. 사용자 문구(`JoinConfirmScreen:242`, `ChatListScreen:83`)는 이미 맞게 적혀 있어
> 고치지 않았다. **잘못된 쪽은 주석이라 §5 에 남긴다.**

---

## 1. 고친 것 — 찾은 거짓 문구 전체

### 1-A. 성별로 매칭한다는 주장 (전부 거짓)

| 파일:라인 | 원문 | 왜 거짓인가 | 어떻게 고쳤나 |
|---|---|---|---|
| `onboarding/OnboardingTrustScreen.kt:106` (+KDoc:49) | 「같은 방향 사람과 매칭되고<br>**같은 성별끼리만 함께 타요**」 | 백엔드 `matching` 에 성별 0건. 탐색의 「여성만」 배지는 이미 같은 이유로 지웠는데(BACKEND-GAP §1) 여기만 남았다 | `OnboardingTrustCopy.body` = 「휴대폰 인증을 마친 사람만 가입하고<br>함께 탈 사람의 탑승 횟수를 보여드려요」 |
| `auth/LoginScreen.kt:119` | 「**같은 성별끼리만 매칭돼요**」 | 위와 같음 | `LoginTrustBullets[0]` = 「휴대폰 인증을 마친 사람만 가입해요」 |
| `auth/ProfileSetupScreen.kt:297` | 섹션 설명 「가입 확인과 **동성 매칭**에 쓰는 정보예요」 | 위와 같음 | 「가입할 때 본인 확인에 쓰는 정보예요」 |
| `auth/ProfileSetupScreen.kt:348` | 성별 필드 아래 「**동성 매칭 확인에만** 쓰고 프로필에는 표시되지 않아요」 | 위와 같음. 성별은 저장만 되고 어디서도 읽히지 않는다 | 「프로필에는 표시되지 않아요」 — **용도를 말하지 않고 확인 가능한 사실만** 남겼다 |
| `auth/ProfileSetupScreen.kt:244` | 「실명과 성별은 **매칭 안전을 위해서만** 사용해요」 | 성별이 매칭에 쓰이지 않으므로 「매칭 안전」이 거짓 | 「실명은 본인 확인에만 쓰고 탑승 상대에게는 보이지 않아요」 |
| `auth/IdentityVerifyScreen.kt:135` | 같은 문구 (09, **미연결 화면**) | 위와 같음 | 같게 고쳤다 |
| `auth/IdentityVerifyScreen.kt:224` | 안내 카드 제목 「**성별은 동성 매칭에만 써요**」 (09, 미연결) | 위와 같음 | 「성별은 프로필에 표시되지 않아요」 한 줄로 줄였다(중복이던 둘째 줄 제거) |

**성별 입력란은 그대로 둔다** — 서버 가입 API(`UserRegisterRequest.gender`)가 필수로 받는다.
지운 것은 성별이 매칭에 쓰인다고 **말하는 것**뿐이다. 수집을 계속할지는 §4 운영자 결정 사항이다.

### 1-B. 위치 공유 주장 (기능 자체가 없다)

`docs/BACKEND-GAP-CLEANUP.md` §1 이 `RideRepository.reportMyLocation`·`getMemberLocations` 와
보호자 공유 카드를 모두 지웠다. 도메인에 남은 위치 조회는 `DispatchRepository.getDriverLocation`
(기사 위치, 택시 모드) **하나뿐**이다. 동승자끼리 위치를 주고받는 길은 없다 —
`ChatMessageType.LOCATION` 은 수신 렌더링(`ChatListScreen:124`)만 있고 **보내는 경로가 없다.**

| 파일:라인 | 원문 | 어떻게 고쳤나 |
|---|---|---|
| `auth/LoginScreen.kt:120` | 「운행 중 **위치를 지인과 공유**할 수 있어요」 | `LoginTrustBullets[1]` = 「함께 탈 사람의 탑승 횟수가 보여요」 |
| `auth/MannerPledgeScreen.kt:231` | 동의 항목 「**운행 중 내 위치를 동승자와 공유하는 데 동의해요**」 + 「매칭된 운행 동안에만 위치를 쓰고…」 | **항목 자체를 삭제.** §3 에 자세히 |

### 1-C. 동승 모드에서 닿을 수 없는 화면의 기능 약속

`docs/MODE-ROUTES.md`: 동승 모드 서버는 `DISPATCHING`·`ONGOING` 을 내지 않아 25·26·27·33 에 닿지 않는다.
서버도 `POST /reports` 를 `partyAccess.isRidingMember` 가 아니면 403 `REPORT_NOT_ALLOWED` 로 막는다
(`ReportService:27`). 27 긴급 신고는 `MainNavGraph:752` 기준 **26 에서만** 진입한다.

| 파일:라인 | 원문 | 왜 거짓인가 | 어떻게 고쳤나 |
|---|---|---|---|
| `onboarding/OnboardingSafetyScreen.kt:104` (+KDoc:40) | 「**운행 중 긴급 신고가 바로 되고**<br>**요금은 인원수대로 나눠 안내해요**」 | ① 동승 모드는 26 에 닿지 않아 27 로 들어갈 길이 없고 서버도 403 ② 요금 정산 기능은 없다(28~32 삭제). 앱이 보여 주는 건 「예상 요금 / **정원**」 추정치이지 인원수 분할 정산이 아니다 | `OnboardingMeetingCopy` = 「모이면, 채팅으로 만나요」 / 「합류하는 순간 채팅방이 열려요<br>어디서 만날지 서로 정하면 돼요」 — **안전 약속 자리를 빼고 참인 가치로 채웠다** |
| `matching/RideDetailScreen.kt:352–354` | 「기사님을 찾고 있어요. 배차되면 바로 알려드릴게요」 / 「**정원이 차면 기사님이 자동으로 배차돼요**」 | **모드를 보지 않고** 무조건 기사 배차를 약속했다. 21 매칭 대기는 이미 `taxiEnabled` 로 갈라 말하는데 22 만 남았다 | `taxiEnabled: Boolean = false` 파라미터를 추가해 21(`MatchWaitingScreen:339–342`)과 **짝이 되는 4갈래**로 바꿨다. 기본값은 동승(「모르면 동승」, MODE-ROUTES 원칙 2). `RideDetailRoute` → `MainNavGraph:639` 로 `appConfig.taxiEnabled` 를 흘린다 |

### 1-D. 지킬 수 없는 시간 약속

| 파일:라인 | 원문 | 왜 거짓인가 | 어떻게 고쳤나 |
|---|---|---|---|
| `auth/MannerPledgeScreen.kt:228` | 「**신고가 들어오면 24시간 안에 확인해요**」 | **앱에 신고 UI 가 없다.** 백엔드에 채팅 신고 API(`ChatReportController`)가 있는데 앱이 부르지 않고, 27 긴급 신고는 운행 중인 방의 승객만이며 동승 모드는 거기 닿지 않는다. 받을 창구는 문의 메일뿐인데 `SupportLinks.CONTACT_EMAIL` 이 null 이라 **그 행조차 안 보인다** | **설명 줄을 비웠다**(제목 「불쾌한 말과 행동을 하지 않을게요」는 사용자 본인의 약속이라 유지). 대체 문구를 만들지 않은 이유: 창구가 없는 상태에서 「신고가 들어오면 확인해요」로 줄여도 **없는 창구를 있다고 말하는 셈**이다. `PledgeItem.description` 을 nullable 로 만들어 그 줄을 그리지 않는다 |
| `matching/MatchWaitingScreen.kt:292` | 「**보통 2분 안에 모여요**」 | 매칭 소요 통계가 어디에도 없다. 기다리는 사람에게 근거 없는 시각을 약속하는 자리 | 「같이 탈 사람을 찾고 있어요」 |
| `matching/DispatchStageScreens.kt:111` | 「**보통 1분 안에 배정돼요** · 최대 3분」 | 앞 절은 근거 없음. 뒤 절은 **사실** — `DispatchService.MATCHING_TIMEOUT = Duration.ofMinutes(3)` 를 `MatchingSweeper` 가 실제로 지킨다 | 「최대 3분까지 기다려요」 — 거짓만 떼고 **서버가 지키는 숫자는 남겼다**. (25, 택시 모드 전용) |
| `matching/DispatchStatusScreen.kt:261` | 「**기사님 도착 후 3분간 기다려요**」 | 백엔드에 승객 대기 시간 개념이 **아예 없다**(`RideController` 는 도착 통보 → 운행 시작 두 단계뿐). 앱도 타이머를 돌리지 않았다 | **줄을 지웠다.** 지킬 주체가 없는 약속이라 대체 문구를 두지 않았다. 유일한 사용처였던 `GrayAsh` 색 상수도 함께 제거 (25, 택시 모드 전용) |

> **「즉시/바로」가 기술적으로 사실인 것은 건드리지 않았다.** `EmergencyScreen:304` 「3초를 채우면
> 즉시 112 통화 화면이 열려요」는 실제로 즉시 다이얼 인텐트를 띄우므로 참이다.

### 1-E. 없는 기능을 있다고 말한 나머지

| 파일:라인 | 원문 | 왜 거짓인가 | 어떻게 고쳤나 |
|---|---|---|---|
| `auth/ProfileSetupScreen.kt:459` | 「얼굴 사진 없이도 **매너 기록으로** 서로를 확인할 수 있어요」 | 평가 API 가 서버에 없어 `User.rating` 은 항상 0.0 (`User` KDoc 이 「4.9·매너 98% 같은 가짜 값을 지어내면 안 된다」고 못 박았다). 매너 기록이라는 것이 없다 | 「탑승 상대에게는 닉네임과 탑승 횟수만 보여요」 |
| `matching/PartnerProfileScreen.kt:154` | 「실명과 **학번**은 서로에게 공개되지 않아요」 | 학교 인증(06·07)이 가입 경로에 없어 **학번을 받은 적이 없다.** 서버 멤버 정보는 publicId·닉네임·이미지·탑승 횟수뿐 | 「실명과 연락처는 서로에게 공개되지 않아요」 (서버가 연락처를 내리지 않으므로 참) |
| `home/DestinationScreen.kt:400` | 「도착지를 넣으면 **같은 방향 사람만 보여드려요**」 | 17 탐색은 **지도 영역으로만** 거른다(`getPartiesWithin`; 서버도 「출발지가 영역 안인 ACTIVE 방」). 도착지로 거르는 경로가 없다. 이 CTA 가 실제로 하는 일은 16 에서 경로·요금을 보여주는 것 | 「도착지를 넣으면 경로와 예상 요금을 알려드려요」 |
| `explore/ExploreScreen.kt:463` | 「위로 올리면 리스트, 목적지 정하면 **바로 자동 매칭**」 | 목적지를 정하면 `POST /matching/rooms` 로 **새 방이 만들어지고** 합류를 기다린다. 기존 방에 자동으로 넣어 주는 기능이 없다 | 「위로 올리면 리스트, 목적지를 정하면 새 방을 만들어요」 |
| `home/DestinationConfirmModal.kt:447` | 매칭 조건 행 「**매칭 방식 · 주요 승차지점**」 | 승차지점 개념이 서버에 없다(방은 사용자가 찍은 좌표 그대로 만들어진다). 칩은 `selected = true` 고정에 `onClick = {}` 무동작이었다 | **행 전체 삭제.** 고를 것도, 가리킬 기능도 없는 줄이었다 |
| `mypage/RideCompleteScreen.kt:103` | 스탯 라벨 「**내가 낸 돈**」 | 결제 기능이 없어 앱은 실제로 낸 돈을 알 수 없다. 값은 `ride.farePerPerson`(예상 요금 / 정원) **추정치**다 | 「1인 부담」 — 20·22·25 가 같은 값에 쓰는 라벨과 맞췄다 (33, 택시 모드 전용) |

---

## 2. 거짓을 지운 자리를 채운 것 — 코드로 확인한 사실만

온보딩은 가입 전에 유일하게 「모여타가 무엇을 해 주는가」를 말하는 자리라, 비우지 않고 **참인 것**으로 채웠다.
새 문구는 `feature/onboarding/OnboardingCopy.kt` 에 상수로 모았다.

| 쓴 말 | 근거 |
|---|---|
| 「요금이 1/N로 줄어요」 (01, 유지) | `Ride.farePerPerson` = 예상 요금 / 정원. 20·22 가 그대로 표시한다 |
| 「휴대폰 인증을 마친 사람만 가입하고」 (02 · 04) | **서버가 막는다** — 문자 인증 토큰 없이 가입하면 400 `USER207 PHONE_NOT_VERIFIED` (`AuthError.PHONE_NOT_VERIFIED`). 앱만의 방어선이 아니다 |
| 「함께 탈 사람의 탑승 횟수를 보여드려요」 (02 · 04) | 서버 `PartyDetailResult.MemberInfo.rideCount`(`MemberRideCount`) → `User.rideCountLabel` 로 20·21·22·23 에 표시 |
| 「합류하는 순간 채팅방이 열려요」 (03) | `PartyMemberJoinedEvent` → `MatchingChatRoomSteps.sync` 가 방을 만들고 멤버를 넣는다 (§0) |
| 「어디서 만날지 서로 정하면 돼요」 (03) | 21 의 정원 충족 안내(「채팅으로 만날 곳을 정해요」)와 같은 사실 |

03 은 화면 이름이 여전히 `OnboardingSafetyScreen` / 라우트 `onboarding/safety` 다 —
**문구만 고치라는 제약에 따라 내비게이션 상수와 파일명은 건드리지 않았다.**

---

## 3. 매너 서약 — 없는 기능에 받던 동의

`auth/MannerPledgeScreen.kt`. 이 화면의 동의 항목은 **전부 체크해야 가입 버튼이 열린다.**
즉 항목 한 줄은 "이걸 동의하지 않으면 가입시키지 않겠다"는 뜻이다.

**삭제: 「운행 중 내 위치를 동승자와 공유하는 데 동의해요」**
- 위치 공유 기능이 **아예 없다** (§1-B).
- **그 동의조차 서버에 저장되지 않았다** — 저장 필드가 없어 전송하지 않고, 버튼 활성 조건에만 썼다
  (BACKEND-GAP §2 가 그렇게 적어 두었다). 즉 **없는 기능에 동의를 강제로 받고 그 동의를 버리고 있었다.**
- 개인정보 처리방침의 「실시간 위치를 동승자에게 공유하는 기능이 현재 없습니다」와 정면으로 어긋났다.
- 버튼 활성 조건은 **따로 손댈 것이 없었다** — `checks` 배열 길이를 `mannerPledgeItems.size` 에서
  끌어오므로 항목을 지우면 조건도 함께 줄어든다(5개 → 4개). 체크 못 해서 가입이 막히는 일은 없다.

**축소: 「이용약관 · 개인정보 수집에 동의해요」의 설명**
- 「실명·생년월일·연락처는 본인 확인**과 매칭 안전**에만 써요」 → 「본인 확인에만 써요」.
  성별이 매칭에 쓰이지 않는 것과 같은 이유로 「매칭 안전」을 뺐다.

**남긴 동의 항목 4개 전부 재확인** — 없는 기능에 대한 동의는 더 없다.
남은 것은 ① 약관·개인정보 수집 ② 시간·장소 준수 ③ 언행 ④ 노쇼 금지 — 모두 **사용자 본인의 약속**이다.

---

## 4. 고치지 않고 남긴 것 — 이유와 운영자 결정 사항

### 4-1. 05~09 부가 인증 화면 (미연결) — 손대지 않음

`Routes.kt` 가 「**가입 그래프에 등록하지 않는다**」고 못 박았고, `MainNavGraph` 에 `ACCOUNT_TYPE`·
`SCHOOL_EMAIL`·`EMAIL_CODE`·`WORK_VERIFY`·`IDENTITY_VERIFY` composable 등록이 **0건**이다
(grep 으로 확인). 사용자에게 보이지 않는다. 이 화면들은 **그 기능이 생길 때 되살릴 자리**이고
문구는 그 기능의 동작을 설명한 것이라, 지금 「없다」로 고치면 기능이 붙을 때 되돌려야 한다.

다만 되살릴 때 **반드시 함께 검증해야 하는** 문구를 남긴다 — 지금 전부 근거가 없다:

| 파일:라인 | 문구 | 문제 |
|---|---|---|
| `AccountTypeScreen.kt:150` | 「누구나 · **매너 기록**으로 신뢰를 쌓아요」 | 평가 API 없음 |
| `AccountTypeScreen.kt:158` | 「인증 사용자에게 **마일리지를 적립**해 신뢰를 쌓는 방식은 **고도화 시 검토**」 | **기획 메모가 화면 본문으로 렌더링된다.** 되살리면 그대로 출시된다 |
| `AccountTypeScreen.kt:169` | 「인증 유형은 **마이페이지에서 추가**할 수 있어요」 | 35 마이페이지에 인증 유형 메뉴가 없다 |
| `SchoolEmailScreen.kt:121` | 「인증 메일은 **5분 안에** 도착해요」 | 메일 발송 경로 자체가 없다 |
| `SchoolEmailScreen.kt:166·168·170` | 「이름과 학번은 저장하지 않아요」 / 「메일 주소는 **암호화해 보관**해요」 / 「다른 이용자에게 공개하지 않아요」 | 저장·암호화 구현이 없다 |
| `SchoolEmailScreen.kt:224` | 「인증 정보는 **매칭 확인** 외에 쓰이지 않아요」 | 매칭이 인증 정보를 보지 않는다 |
| `EmailCodeScreen.kt:228` | 「코드는 **10분 뒤에** 만료돼요」 | 메일 코드 TTL 이 없다 |
| `WorkVerifyScreen.kt:185·187` | 「회사명과 서류는 인증 후 **즉시 삭제**해요」 / 「직업은 「직장인 인증」 **배지로만 표시**돼요」 | 서류 업로드·배지 발급이 없다. 서버 `badgeId` 는 TODO 라 항상 null |
| `WorkVerifyScreen.kt:206` | 「재직 확인은 **보통 1일 안에** 끝나요」 | 심사 주체가 없다 |

> **성별 관련 문구만 예외로 09 에서도 고쳤다**(§1-A). 성별 수집은 미연결이 아니라 10 에서
> **지금 실제로 일어나는 일**이라, 같은 거짓 고지가 두 군데 있는 상태를 남기지 않았다.

### 4-2. 「매칭 조건」의 출발지·도착지 반경 — 운영자·백엔드 결정 필요 ⚠

`home/DestinationConfirmModal.kt:427·437` (16) 과 `matching/MatchWaitingScreen.kt:388` (21, 「탐색 반경」).

- 값은 **실제로 서버에 전송되고 저장된다** (`OpenPartyRequest.departureRadius/destinationRadius`
  → `Party.departureRadius` → `PartyEntity`).
- 그런데 **어떤 조회·판정도 그 값을 읽지 않는다.** 백엔드에서 `Radius` 를 쓰는 곳은 생성·저장·복원뿐이고
  (`PartyService:67`, `Party.java`, `PartyEntity`), 합류(`party.join(memberId)`)에 거리 검사가 없고
  목록 조회는 **지도 뷰포트**만 본다.
- 즉 사용자는 「매칭 조건」이라고 적힌 칩으로 **아무 영향 없는 값**을 고르고 있다.
- **고치지 않은 이유:** 반경 행을 지우는 것은 문구 교정이 아니라 **사용자 설정 요소 제거**(화면 구조 변경)고,
  값이 저장되고 있어 백엔드가 매칭에 쓰기 시작하면 바로 사실이 된다. 「인원(정원)」은 실제 매칭 조건이라
  카드 제목 「매칭 조건」 자체가 전부 거짓도 아니다.
- **결정 필요:** ① 백엔드가 합류 시 반경을 검사하게 한다(권장 — 「같은 방향」 주장의 근거가 된다)
  ② 또는 반경 선택을 화면에서 내린다.

### 4-3. 「같은 방향 사람을 찾아요」류 — 유지, 근거 설명

| 파일:라인 | 문구 | 판단 |
|---|---|---|
| `home/HomeScreen.kt:261` | 「목적지를 넣으면 같은 방향 사람을 찾아드려요」 | **유지.** 목적지를 넣으면 그 목적지로 방이 열리고, 그 방에 합류하는 사람은 같은 목적지를 보고 합류한다 — 실제 흐름의 결과를 말한 것이다. §1-E 에서 고친 두 건은 **없는 메커니즘**(도착지 필터·자동 매칭)을 주장했기에 성격이 다르다 |
| `auth/SignupCompleteScreen.kt:136` | 「목적지를 넣으면 같은 방향 사람을 찾아요」 | 위와 같음 |
| `auth/SignupCompleteScreen.kt:138` | 「내릴 때 1/N으로 나눠 내면 끝이에요」 | **유지.** 앱이 정산한다는 주장이 아니라 **사용자가 현금으로 나눠 내라**는 안내다. BACKEND-GAP §1 이 지운 것은 「요금은 내릴 때 바로 정산**할게요**」(앱의 약속)였다. 다만 앱이 보여 주는 1인 부담은 **정원 기준** 추정치라 실제 탑승 인원과 다를 수 있다 — 20 이 「정원 기준 예상 금액」이라고 이미 적어 둔다 |

### 4-4. 「이용이 제한돼요」 — 운영자 결정 필요 ⚠

`auth/MannerPledgeScreen.kt` 의 「무단 취소가 반복되면 **이용이 제한돼요**」(단정형)와
「매칭 확정 후 오지 않으면 이용이 제한**될 수 있어요**」.

- **백엔드에 제재 수단이 없다** — 정지·차단·노쇼 카운트 관련 코드가 0건이다
  (`grep -rln "ban\|suspend\|penalty\|blacklist\|noShow"` → 기사 계좌·이벤트 DLQ 등 무관한 파일만).
- **고치지 않은 이유:** 이건 기능 주장이 아니라 **약관이 유보하는 조치**다. 「제한될 수 있어요」는
  운영자가 권리를 남겨 두는 말이라 수단이 아직 없어도 거짓이 아니다.
- **결정 필요:** 단정형(「제한돼요」)은 수단이 생기기 전까지 「제한될 수 있어요」로 맞추는 쪽이
  안전하다. 약관 문구와 함께 봐야 해서 내가 임의로 바꾸지 않았다.

### 4-5. 그 밖에 보고만 하는 것

| 위치 | 내용 | 판단 |
|---|---|---|
| `mypage/MyPageScreen.kt:259` · `AccountDeleteScreen.kt:187` | 「계정과 저장한 정보가 사라지고 되돌릴 수 없어요」 | **유지.** 「모든 기록이 사라져요」는 아니고(BACKEND-GAP §6 금지 문구), 정확한 4줄은 37 화면이 `AccountDeletionWarnings` 로 보여 주며 `AccountDeleteRulesTest` 가 못 박는다. 다만 「저장한 정보」가 「지난 합승 기록 포함」으로 읽힐 여지는 있다(서버는 지우지 않는다) — 운영자 확인 권장 |
| `core/MemberDisplay.kt:51–52` | `User.verifiedLabelOrNone` → 「인증 정보 없음」 | **죽은 코드.** 호출처가 0건이라 사용자에게 보이지 않는다. 쓰기 시작하면 없는 인증 개념을 꺼내게 되니 되살리지 말 것 |
| `matching/PartnerProfileScreen.kt:102·123·126` | `user.verifiedLabel` 이 비어 있지 않을 때만 인증 배지 표시 | **잠복 상태.** 서버 `badgeId` 가 TODO 라 항상 빈 문자열이어서 지금은 그려지지 않는다. 가드가 있어 거짓을 말하지 않는다 |
| `explore/ExploreScreen.kt:195` | `dummyUser(…, "학교 인증", 4.9, 12)` | `@Preview` 전용 데이터. 사용자에게 보이지 않지만 `User` KDoc 이 금지한 가짜 값(4.9)을 쓰고 있다 — 실화면으로 새지 않도록 주의 |
| `auth/LoginScreen.kt:171` | 「가입하면 이용약관 · 개인정보 처리방침에 동의하게 돼요」 (탭 무동작) | 문구는 참이다(실제로 동의하게 된다). 링크가 죽은 것은 `SupportLinks` 세 주소가 전부 null 인 기존 공백 |
| 25·26·27·33 의 나머지 자체 문구 | — | MODE-ROUTES 원칙 3 에 따라 유지. 동승 모드에서 안 보일 뿐이다. 시간 약속 2건(§1-D)만 근거가 없어 고쳤다 |

---

## 5. 내가 오해했는데 사실은 참이었던 것 / 반대로 코드 주석이 틀린 것

- **「합류하면 바로 채팅방에 들어가요」(`JoinConfirmScreen:242`)와 「동승에 합류하면 채팅방이 열려요」
  (`ChatListScreen:83`) 는 참이다.** 처음엔 거짓으로 의심했다 — 백엔드 `PartyController` javadoc 이
  「정원이 차면 … 채팅방이 열린다」고 적고 프론트 `MainNavGraph:626` 주석도 「정원이 찼다 = 채팅방이
  생기는 시점」이라고 적고 있다. 구현을 따라가 보니 `PartyMemberJoinedEvent` 가 **방 생성과 모든 합류마다**
  발행되고 `MatchingChatRoomSteps.sync` 가 그 자리에서 방을 만든다. **문구가 맞고 주석 두 개가 틀렸다.**
  주석은 사용자에게 보이지 않아 고치지 않았지만, 다음 사람이 이 주석을 읽고 문구를 "고치면" 거짓이 된다
  — `MainNavGraph:626` 과 백엔드 javadoc 수정이 필요하다(§6 요청).
- **「요금은 인원수대로 나눠 안내해요」의 뒷부분은 절반만 거짓이었다.** 앱은 1인 부담을 실제로 안내한다.
  거짓인 지점은 ① 분모가 **인원수가 아니라 정원**이고 ② 「안내」가 「정산」으로 읽히는 자리(안심 화면,
  긴급 신고 약속과 한 문장)에 있었다는 것이다. 그래서 「없는 기능」이 아니라 **잘못 놓인 문구**로 다뤘고,
  정원 기준 추정치 안내는 20·22 에 그대로 남겼다.
- **「최대 3분」은 참이었다.** 같은 문장의 「보통 1분 안에」만 지웠다 — 서버 `MATCHING_TIMEOUT` 이 실재한다.
- **「3초를 채우면 즉시 112 통화 화면이 열려요」는 참이다.** 기술적으로 즉시인 것은 건드리지 않았다.
- **「정원이 차도 나갈 수 있다」·「합류 시 채팅방 열림」을 틀리게 설명하는 사용자 문구는 없었다.**
  21 은 이미 모드별로 맞게 적혀 있다(`canLeaveParty`/`canFinishParty` 와 문구가 같은 조건을 쓴다).

---

## 6. 백엔드·운영에 넘길 요청

1. **성별 수집을 계속할 것인가** (가장 중요). 지금 성별은 `UserRegisterRequest` 필수값으로 받아
   `UserProfileEntity` 에 저장되고 **어디에서도 읽히지 않는다.** 개인정보 최소수집 원칙상 ① 매칭에
   쓰도록 구현하거나 ② 수집을 그만두는 쪽이 맞다. 앱은 서버가 필수로 요구하는 동안 입력란을 유지한다.
2. **반경을 매칭에 쓸 것인가** (§4-2). 쓰지 않으면 16 의 「매칭 조건」 칩 두 줄을 내려야 한다.
3. **신고 창구.** 앱 내 신고 UI 가 없고 `SupportLinks.CONTACT_EMAIL` 도 null 이다. 백엔드에 이미 있는
   `ChatReportController` 를 앱이 쓰도록 연동하거나, 최소한 문의 메일 주소를 확정해야 한다.
   그전까지 매너 서약에 신고 처리 약속을 적을 수 없다.
4. **제재 수단** (§4-4). 「이용이 제한돼요」를 지킬 장치가 없다.
5. **잘못된 주석 정정**: 백엔드 `PartyController` 「방 참여」 javadoc 과 프론트 `MainNavGraph:626` —
   채팅방은 정원이 찰 때가 아니라 **합류할 때** 열린다.

---

## 7. 변경 파일

**신규**
- `presentation/src/main/kotlin/com/moyeota/presentation/feature/onboarding/OnboardingCopy.kt`
  — 01~03 헤드라인·본문을 `OnboardingSavingCopy`·`OnboardingTrustCopy`·`OnboardingMeetingCopy` 상수로.
    각 상수 KDoc 에 "왜 참인지"와 "무엇을 왜 지웠는지"를 적었다.
- `presentation/src/test/kotlin/com/moyeota/presentation/feature/onboarding/ProductClaimsTest.kt` — 12건

**수정 (문구 · 모드 분기 배선)**
- `feature/onboarding/OnboardingSavingScreen.kt` · `OnboardingTrustScreen.kt` · `OnboardingSafetyScreen.kt`
- `feature/auth/LoginScreen.kt` (`LoginTrustBullets` 상수 추출) · `MannerPledgeScreen.kt`
  (`PledgeItem.description` nullable, `mannerPledgeItems` 로 이름 변경 + internal 화) ·
  `ProfileSetupScreen.kt` · `IdentityVerifyScreen.kt`
- `feature/matching/RideDetailScreen.kt` (+`taxiEnabled`) · `RideDetailRoute.kt` (+`taxiEnabled`) ·
  `MatchWaitingScreen.kt` · `PartnerProfileScreen.kt` · `DispatchStageScreens.kt` · `DispatchStatusScreen.kt`
- `feature/home/DestinationScreen.kt` · `DestinationConfirmModal.kt`
- `feature/explore/ExploreScreen.kt`
- `feature/mypage/RideCompleteScreen.kt`
- `core/MainNavGraph.kt` (`taxiEnabled = appConfig.taxiEnabled` 를 `RideDetailRoute` 로)

**화면 진입 경로** — 라우트는 하나도 추가·변경하지 않았다. 바뀐 문구가 보이는 경로:
`01 → 02 → 03 → 04`(온보딩·시작) · `04 → 10 → 12 → 13`(가입) · `14 → 15 → 16`(목적지) ·
`17`(탐색) · `21`(매칭 대기) · `20 → 22 → 23`(상세·프로필) · `25`·`33`(택시 모드 서버에서만).

## 8. 검증

```
./gradlew :app:assembleDebug :data:testDebugUnitTest :presentation:testDebugUnitTest --console=plain -q
→ 통과 (출력 없음)
테스트 총 534건 / 실패 0 / 건너뜀 0  (신규 ProductClaimsTest 12건 포함)
```

**실기 검증은 하지 않았다**(지시 사항).

### 8-1. 테스트 — 상수로 뺀 문구를 못 박았다

`ProductClaimsTest` 가 01~03 온보딩 + 04 신뢰 불릿 + 12 동의 항목을 한 덩어리로 보고 검사한다.
`AccountDeleteRulesTest`(37 탈퇴 경고)와 같은 패턴이다 — **여기서는 문구가 고지**이기 때문이다.

- 금지: 성별 매칭(「같은 성별」·「동성 매칭」·「동성끼리」·「성별끼리」) · 위치 공유(「위치를 공유」·
  「실시간 위치」·「보호자」…) · 평가(「매너 점수」·「매너 기록」·「별점」·「후기」·「평가」) ·
  정산(「정산」·「결제」·「카드」·「자동으로 나눠」) · 긴급 신고(「긴급 신고」·「신고가 바로」·「112」) ·
  운영 시한(「시간 안에」·「분 안에」·「일 안에」·「즉시 확인」) · 「신고가 들어오면」·「24시간」
- 요구: 온보딩에 「1/N」·「휴대폰 인증」·「채팅방이 열려요」·「탑승 횟수」가 **모두 있어야 한다**
  (거짓을 지운 자리가 비어 있지 않은지 — 금지 목록만으로는 문구를 통째로 지워도 통과한다)
- 구조: 온보딩 3장 전부 헤드라인·본문 있음 · 신뢰 불릿 2줄 · 동의 항목 4개 · 공백만 있는 설명 없음

**뮤테이션 확인**: `OnboardingTrustCopy.body` 를 예전 문구(「같은 성별끼리만 함께 타요」)로 되돌려
`:presentation:testDebugUnitTest --tests '*ProductClaimsTest*'` → **BUILD FAILED**. 복구 후 통과.
테스트가 실제로 잡는다는 것을 확인했다.

### 8-2. grep — 지운 문구가 `src/main` 에 남지 않았는지

`presentation/domain/data/core/app` 의 `src/main` 전체, **주석 제외** 기준:

| 지운 문구 | 잔존 |
|---|---|
| 「같은 성별」 · 「동성 매칭」 · 「성별끼리」 | **0건** |
| 「위치를 지인과」 · 「운행 중 위치」 · 「내 위치를 동승자와」 | **0건** |
| 「24시간 안에」 · 「신고가 들어오면」 | **0건** |
| 「보통 2분」 · 「운행 중 긴급 신고」 · 「인원수대로 나눠」 | **0건** |
| 「자동 매칭」 · 「같은 방향 사람만 보여」 · 「주요 승차지점」 | **0건** |
| 「내가 낸 돈」 | **0건** |
| 「매너 기록」 | **1건** — `AccountTypeScreen.kt:150` (05 **미연결**, §4-1) |
| 「학번」 | **1건** — `SchoolEmailScreen.kt:166` (06 **미연결**, §4-1) |

남은 2건은 전부 **내비게이션에 등록되지 않은 05·06 화면**이다(`MainNavGraph` 에 composable 등록 0건).
의도적으로 남겼고 되살릴 때 검증할 목록을 §4-1 에 적었다.

---

## 9. 주의 — 다른 에이전트의 워킹트리 변경

작업 시작 시 워킹트리는 `.DS_Store` 수정 + `.agents/`·`.codex/`·`AGENTS.md` 미추적뿐이었다.
작업 중에 **내가 건드리지 않은 파일들**이 변경됐다:
`.github/workflows/release.yml` · `app/src/main/AndroidManifest.xml` ·
`app/src/main/res/xml/backup_rules.xml` · `app/src/main/res/xml/data_extraction_rules.xml` ·
새 디렉터리 `app/src/debug/`.

누군가 같은 저장소에서 동시에 작업하고 있다. **나는 손대지 않았다.** 커밋할 때 분리해야 한다.
