# 죽은 코드 · 가짜 데이터 전수 조사 (출시 직전)

- 조사일: 2026-10-15
- 브랜치: `fix/place-roadname-null` (working tree clean)
- 방법: **정적 분석만** (grep / 파일 읽기). 빌드·테스트·에뮬레이터 미실행.
- 코드 변경 없음. 커밋·푸시 없음.
- 계기: `RideRepository.getMyRides()` — 호출처 0곳 + 하드코딩된 가짜 방(「안양역 → 성결대 정문, 내일 08:40, 3,200원」) 제거(`8738679`)

---

## 0. 요약 — 등급별 집계

| 등급 | 항목 수 | 대표 항목 |
|---|---|---|
| **지워야 함** | 7 묶음 | ⚠️ **21·25 라우트의 더미 폴백**(§1-B, 릴리스 화면에 뜬다) · `getNearbyParties`+`DummyRideRepository`(§1-A) · `ExploreScreen.DefaultParties` · `ChatMembership.joinedAt` · 템플릿 색상 7개 · `BadgeGrayBg` · `MapSheetContentPadding` |
| **남겨야 함** | 15 묶음 | 05~09 인증 화면·라우트(1,789줄) · `ChatError` 미사용 판정자 3개 · 프로필 이미지 4항목 · `departureRadius`류 4개 · `MemberInfo.badgeId` · `ChatMessageResponse.userId` · `PartyStatus.currentMembers` · `ChatMessage.deleted`(결함 후보) · `UserSession.*`(실은 안 죽었다) |
| **판단 필요** | 11 묶음 | `getParties` · `joinChatRoom` · `deleteMessage` · `User.rating` · `ReverseAddress.roadAddress/jibunAddress` · `UserProfileInfo.uuid` · `PartyDetailResponse.currentMembers`·`createdAt`×2 · `MoyeotaColor` 5개·`MoyeotaType` 4개 · 「부산대·부경대·동아대」 제품 주장 · 중복 색상 상수 |

**전수 조사 완료 범위**: `.kt` 207개 · DTO 클래스 45/45 · 리소스 23/23 · `Routes` 상수 30/30 ·
저장소 인터페이스 메서드 47/47 · 도메인 모델 프로퍼티 전수.
**가짜 데이터가 릴리스 화면에 닿는 경로는 §1-B 하나다.**

### 1번 질문의 답 — 「가짜 데이터가 릴리스 빌드에 닿는가」

**닿는다. 두 갈래인데, 심각한 쪽은 `DummyRideRepository` 가 아니다.**

| 갈래 | 릴리스에서 화면에 뜨는가 | 근거 |
|---|---|---|
| **(A) `DummyRideRepository`** (성윤·민지·준호 / 성결대·안양역) | **아니다 — 단, 호출 한 줄 거리** | 릴리스에 **인스턴스가 실제로 생성된다**(아래 A-1). 하지만 그 값을 꺼내는 유일한 메서드 `getNearbyParties()` 의 호출처가 presentation·app 에 **0곳**이라 화면에 뜨지 않는다. 지운 `getMyRides` 와 **똑같은 구조** |
| **(B) presentation 의 `*RideDummy`** (센텀시티역 → 서면역 / 부산불곰·해운대곰돌·부산가자 / 3,600원) | **닿는다** | 21 매칭 대기·25 배차 상태 라우트의 `partyId == null` 폴백이 **`ride` 인자를 넘기지 않아** 화면 기본값(더미)이 그려진다. 그 분기는 **구성 변경(회전 등) 재생성 후 실제로 열린다**(아래 B-2) |

즉 사용자가 **진짜 방에 들어가 있는 상태에서 화면을 회전**하면, 자기 방 정보 대신
「센텀시티역 3번 출구 → 서면역 1번 출구 · 부산가자 · 3,600원」이 뜬다 — 서버를 안 거치고도
그럴듯한 화면이 나오는, 이 작업의 출발점과 **정확히 같은 종류의 결함**이다.

> **미확인**: (B) 를 실기로 재현하지 않았다(빌드 금지 제약). 재현 절차는 §1-B-4.

---

## 1. 가짜·목업 데이터가 릴리스 빌드에 닿는 경로 — 최우선

### 전제: 간접 주입 경로는 없다 (추측 배제)

「grep 0건」으로 단정하지 않기 위해 먼저 확인했다.

- **DI 프레임워크 없음**: `gradle/libs.versions.toml` 및 모든 `build.gradle.kts` 에 hilt·dagger·koin **0건**.
  `AppContainer.kt:30` 주석도 "수동 DI 컨테이너. 백엔드 연동 시 Hilt 로 교체 예정".
- **리플렉션 없음**: `Class.forName` · `ServiceLoader` · `newInstance` · `Reflection` 전 모듈 `src` **0건**.
- 따라서 저장소 구현체가 선택되는 경로는 `AppContainer` 의 명시적 생성자 호출 **하나뿐**이다.
- **R8/난독화·축소가 꺼져 있다**: `app/build.gradle.kts:104-106` `release { optimization { enable = false } }`.
  → 미사용 클래스라도 **릴리스 APK 에서 제거되지 않는다**. 더미 문자열은 그대로 dex 에 들어간다.

### A. `DummyRideRepository` — 릴리스에 생성되지만 화면엔 안 뜬다

**A-1. 릴리스에서 인스턴스가 만들어진다.**

```
data/src/main/kotlin/com/moyeota/data/repository/RemoteRideRepository.kt:49
    private val local: RideRepository = DummyRideRepository(),
```

`AppContainer.kt:78-84` 는 `RemoteRideRepository(apis.matching, sessionManager, reportApi=…, currentLocation=…, partyEvents=…)`
로 생성한다 — **`local` 을 넘기지 않는다.** Kotlin 기본값은 미지정 시 평가되므로
`DummyRideRepository()` 가 앱 시작 시 **실제로 생성된다**(프리뷰 전용도, 디버그 전용도 아니다).

**A-2. 그러나 화면엔 닿지 않는다.** `local` 의 사용처는 단 한 줄이다.

```
RemoteRideRepository.kt:63
    override fun getNearbyParties(): List<Ride> = local.getNearbyParties()
```

`getNearbyParties` 호출처 전수 조사(`grep -rn "getNearbyParties" app data domain presentation core`, `/build/` 제외):

| 위치 | 성격 |
|---|---|
| `domain/.../RideRepository.kt:15` | 인터페이스 선언 |
| `data/.../RemoteRideRepository.kt:63` | 구현 (→ local 위임) |
| `data/.../DummyRideRepository.kt:25` | 구현 (가짜 값 2건) |
| `DummyRideRepository.kt:54,62,68,89` | 더미 **내부** 재사용 |
| `data/src/test/.../RemoteActivePartyRepositoryTest.kt:347` | 테스트 스텁 |

→ **presentation·app 호출처 0곳.** `RemoteRideRepository.kt:34` 의 KDoc
"매칭·신고 도메인만 서버 연동 — 나머지는 더미로 위임한다" 는 **낡은 서술**이다
(지금은 `getParties`·`getPartiesWithin`·`getPartyDetail`·`createParty`·`joinParty`·`leaveParty`·
`finishParty`·`getAssignedDriver`·`previewRoute` 가 전부 서버 연동이고, 더미로 가는 건 이 한 줄뿐).

**A-3. 가짜 값의 내용** (`DummyRideRepository.kt`)

| 라인 | 값 |
|---|---|
| 21-23 | `성윤`(4.9/12회) · `민지`(4.8/21회) · `준호`(4.7/8회), 전원 `"성결대 인증"` |
| 28-29 | 성결대 정문 → 안양역, 「지금 출발」, 3,200원 / 9,600원, 좌표 37.3789,126.9268 |
| 41-42 | 성결대 후문 → 범계역, 「18:30」, 3,600원 / 10,800원 |
| 101 | `AssignedDriver(seats=4, plateNumber="12가 3456", vehicleType="쏘나타")` |
| 104-122 | 요금·시간을 **지어내는 산식**(기본 4,800원 + km당 1,300원, 시속 22km) |
| 159 | `reportEmergency` → `1L` ("성공한 척") |

**판정: 지워야 함.** 근거:
- `getNearbyParties` 는 서버에 **대응 엔드포인트가 없다**(`MatchingApi` 에 「근처」 전용 경로 없음 —
  지도 범위 조회는 `getPartiesWithin`, 전체는 `getParties`). 즉 §2 분류의 **(b) 서버에도 없는 것**.
- 지우면 `RemoteRideRepository` 의 `local` 파라미터와 `DummyRideRepository` 클래스 전체가 함께 사라진다.
- **영향**: `data/src/test/.../RemoteRideRepositoryTest.kt:186, 229, 261` 이 `DummyRideRepository()` 를 직접
  쓴다(특히 :229 는 더미의 `previewRoute` 산식을 검증). 그 3개 테스트는 함께 지워야 한다 —
  **지워도 서버 계약 검증은 하나도 잃지 않는다**(검증 대상이 더미 자신의 산식이라서다).
- `.claude/skills/api-integration/SKILL.md:32, 56` 이 "`DummyRideRepository` 도 같은 인터페이스를
  구현하므로 함께 갱신한다"고 지시한다 → **스킬 문서도 함께 고쳐야** 다음 작업자가 되살리지 않는다.

### B. presentation 의 `*RideDummy` — **릴리스 화면에 닿는다** ⚠️

**B-1. 더미가 공개 컴포저블의 기본 인자로 박혀 있다.** 전수(5곳, 이게 전부다):

| 파일:라인 | 선언 | 기본값이 붙은 화면 |
|---|---|---|
| `presentation/.../matching/MatchWaitingScreen.kt:85` | `private val waitingRideDummy` | `:143 ride: Ride = waitingRideDummy` (21) |
| `presentation/.../matching/DispatchStatusScreen.kt:63` | `private val dispatchRideDummy` | `:101 ride: Ride = dispatchRideDummy` (25) |
| `presentation/.../matching/DispatchStageScreens.kt:436` | `internal val stageRideDummy` | `:94 DriverSearchScreen` · `:195 DriverAssignedScreen` (25b·25c) |
| `presentation/.../explore/ExploreScreen.kt:197` | `private val DefaultParties` (가짜 방 5건) | `:251 parties: List<Ride> = DefaultParties` (17~19) |

내용: 전부 **센텀시티역 3번 출구 → 서면역 1번 출구**, 멤버 `부산불곰`·`해운대곰돌`·`부산가자`,
1인 3,600원 / 총 10,800원 (`stageRideDummy` 는 6,400/12,800). `DefaultParties` 는
부산대 정문→서면역 등 5건 + `dummyUser("u1","김OO")` 류 멤버 + "3분 후 출발 예정 · 6.2km".

**B-2. 21·25 는 그 기본값이 실제로 쓰이는 분기가 있다.**

```
presentation/.../matching/MatchWaitingRoute.kt:776-782
    if (partyId == null) {
        MatchWaitingScreen(            // ← ride 를 넘기지 않는다 → waitingRideDummy
            onCancelSearch = onCancelSearch, onExitKeepingParty = onExitKeepingParty,
            onCardClick = onCardClick, onOpenChat = onOpenChat,
        )
        return
    }
```

```
presentation/.../matching/DispatchStatusRoute.kt:226-229
    if (partyId == null) {
        DispatchStatusScreen(myLocation = myLocation.coordinates, onOpenChat = onOpenChat, onBack = onBack)
        return                          // ← ride 를 넘기지 않는다 → dispatchRideDummy
    }
```

**B-3. 그 분기는 릴리스에서 열린다.** 세 사실이 겹친다.

1. `partyId` 로 넘어가는 값이 **`remember`** 다(구성 변경·프로세스 사망에 사라진다):
   - `MainNavGraph.kt:216` `var createdPartyId by remember { mutableStateOf<Long?>(null) }` → 21 에 전달(`:596`)
   - `MainNavGraph.kt:219` `var activePartyId by remember { mutableStateOf<Long?>(null) }` → 25 에 전달(`:670`)
   - 같은 파일에서 `rememberSaveable` 은 4곳만 쓰인다 — 이 둘은 거기에 **없다**.
2. **네비게이션 백스택은 살아남는다.** `rememberNavController()` 가 백스택을 saved state 로 보관하므로
   재생성 후 `Routes.MATCH_WAITING` / `Routes.DISPATCH_STATUS` 목적지가 **그대로 복원된다**.
3. **복구 경로가 막혀 있다.** 단계 복귀를 담당하는 `MainNavGraph.kt:361-372` 는
   - `var startupStageHandled by rememberSaveable { … }` → 재생성 후 **true 로 복원**되어 즉시 `return`
   - 설령 false 라도 `if (navController.currentDestination?.route != Routes.HOME) return` 에서 걸린다
     (현재 목적지가 21·25 이므로)
   → `navigateToStage` 가 다시 불리지 않아 `createdPartyId`·`activePartyId` 가 **복원되지 않는다**.

   그리고 `MainActivity` 는 `android:configChanges` 를 **선언하지 않는다**(`app/src/main/AndroidManifest.xml:34-36`
   — `exported`·`windowSoftInputMode` 만 있다) → 회전·다크모드 전환·글꼴 크기 변경·언어 변경에서 액티비티가 재생성된다.
   `MainActivity.kt:33-34, 37` 의 주석이 이 시나리오를 **이미 전제한다**: "화면 회전 등 구성 변경으로
   재생성될 때", "구성 변경 재생성에서는 읽지 않는다 — … 회전할 때마다 같은 방으로 또 간다".

   보조 경로: `navigateToStage` 는 `ride.id.toLongOrNull()?.let { … }` (`MainNavGraph.kt:280-283`) 로
   id 를 세팅하는데, **파싱 실패 시 세팅을 건너뛰고도 그대로 navigate 한다** — 그 경우도 null 폴백이다
   (서버 partyId 는 Long 이라 평시엔 안 걸리지만, 가드가 없다는 사실은 남는다).

**B-4. 재현 절차(미실행).** 디버그 빌드로: ① 방 생성 → 21 진입 ② 개발자 옵션
「활동 유지하지 않음」 켜기 또는 기기 회전 ③ 21 로 복귀 시 출발·도착·멤버·요금이
「센텀시티역 3번 출구 / 서면역 1번 출구 / 부산가자 / 3,600원」으로 바뀌는지 확인.
25 는 택시 모드 필요(`docs/MODE-ROUTES.md`)이므로 동승 모드 실배포에서는 21 이 주 경로다.

**B-5. 같은 코드베이스가 이미 올바른 패턴을 알고 있다.** 세 라우트는 null 을 **정직하게** 처리한다:

| 라우트 | 처리 | 주석 |
|---|---|---|
| `RideDetailRoute.kt:91-97` (22) | `ErrorBox("탑승 정보를 불러오지 못했어요")` | "null 이면 **예시 방을 그리는 대신 사실대로 말하고 되돌린다**" |
| `JoinConfirmRoute.kt:165-172` (20) | `ErrorBox("동승 정보를 불러오지 못했어요")` | "**예시 방을 그리는 대신** 사실대로 말하고…" |
| `MainNavGraph.kt:652-660` (23) | `ErrorBox("동승자 정보를 불러오지 못했어요")` | "null 이면 **데모 프로필을 그리는 대신** 사실대로 말하고 되돌린다" |
| `RideOngoingRoute.kt:145-151` (26) | 화면은 띄우되 **더미 없음** — `RideOngoingScreen` 은 `originName`·`destinationName`·`estimatedMinutes` 가 전부 nullable 기본 null(`RideOngoingScreen.kt:100-113`) | "서버 좌표 없이도 지도는 띄운다" |

→ **21·25 만 이 규칙에서 빠져 있다.** 20·22·23·26 은 고쳐졌고 21·25 는 안 고쳐진 것으로 보인다.

**B-6. 폴백의 명분이 이미 사라졌다.** 두 라우트 KDoc 이 폴백 이유로 **지워진 화면**을 든다:
- `DispatchStatusRoute.kt:204` "partyId 가 없으면 … (채팅·**34 내 기록**에서 직행한 경우의 폴백 화면)"
- `RideOngoingRoute.kt:123` "partyId 가 없으면(채팅·**34 내 기록**에서 바로 들어온 경우)"

34 내 탑승 화면은 2026-09 에 삭제됐다(`docs/BACKEND-GAP-CLEANUP.md` §1). 남은 진입로는 채팅뿐인데,
채팅에서의 진입은 `MainNavGraph.kt:699, 728` 이 `navigate(Routes.RIDE_ONGOING)` 으로만 가고
21·25 로는 `navigateToStage`(id 세팅 포함)를 거친다.

**판정: 지워야 함** (가짜 데이터 — 사용자가 명시한 예외 대상).
- **지울 것**: `waitingRideDummy`·`dispatchRideDummy`·`stageRideDummy` 와 네 곳의 `ride: Ride = …Dummy` 기본값.
  `ride` 를 **필수 인자로** 바꾸면 컴파일러가 21·25 의 null 폴백을 강제로 드러낸다.
- **그 폴백은** 20·22·23 과 같은 `ErrorBox` 로 바꾸는 것이 이 코드베이스의 기존 결론이다.
- **영향**: `@Preview` 4개(`MatchWaitingScreen.kt:639`, `DispatchStatusScreen.kt:318`,
  `DispatchStageScreens.kt:453,459,465`)가 인자를 받아야 한다. `RideDetailScreen.kt:484` ·
  `JoinConfirmScreen.kt:544` 가 **이미 올바른 모범**이다 — 프리뷰 **안에** 중립 값
  (`origin="출발지"`, `nickname="동승자"`)으로 리터럴을 두고 "프리뷰 전용 더미 — 실행 경로에는
  흘러가지 않는다"고 적어 뒀다. 같은 모양으로 옮기면 된다.

**`ExploreScreen.DefaultParties` 는 등급이 다르다 — 지워야 함(단, 현재는 화면에 안 뜬다).**
`ExploreRoute.kt:282-283` 은 `parties = current.parties` 를 **항상** 넘기고(Success 분기가 유일한 호출처),
Loading·Error 는 `LoadingBox`/`ErrorBox` 로 간다(`:277-281`). 즉 오늘은 프리뷰 3개
(`:952, 958, 964`)만 이 값을 쓴다. 그래도 **기본값이라는 장치 자체가 B 와 동일한 함정**이고,
R8 이 꺼져 있어 "부산대 정문·서면역·김OO·4,200원" 문자열이 릴리스 APK 에 들어간다.
기본값을 없애고 프리뷰 안으로 옮기는 것이 맞다.

### C. `@Preview` 밖의 하드코딩된 그럴듯한 값 — 그 외는 깨끗하다

사람 이름·실지명·요금·평점·시각을 전수 grep
(`성결대|안양역|범계역|센텀시티|서면역|부산대|해운대|부산불곰|해운대곰돌|부산가자|성윤|민지|준호|쏘나타|12가`,
`src/test` 제외)한 결과, §A·§B 밖에서 `@Preview` **밖**에 있는 것은 없었다. 확인된 안전 항목:

| 위치 | 왜 괜찮은가 |
|---|---|
| `HomeScreen.kt:398-410` (김성윤 + FavoritePlace 2건) | `@Preview` 내부(`:394`, `:409`) |
| `PartnerProfileScreen.kt:254` (부산불곰) | `@Preview` 내부(`:248`). 주석 "프리뷰에서만 쓰는 예시 값" |
| `ProfileEditScreen.kt:161-195` (성윤) · `MyPageScreen.kt:437-462` (성윤) | 전부 `@Preview` 내부 |
| `domain/.../User.kt:17` · `Dispatch.kt:12,14` | KDoc 의 `예:` 서술(코드 아님) |
| `data/.../dto/UserDtos.kt:9` (`"name":"김성윤"`) | KDoc 의 응답 예시(코드 아님) |
| `DestinationConfirmModal.kt:123` · `ChatRoute.kt:97` | 주석 안의 주소 길이 예시 |
| `AccountTypeScreen.kt:130` · `SchoolEmailScreen.kt:193` ("부산대 · 부경대 · 동아대") | **도달 불가 화면**(§3). 가짜 데이터는 아니지만 **검증 안 된 제품 주장** — §3 참고 |
| `SupportLinks.kt:32-53` | 실제 운영 주소 4개(`legal.moyeota.p-e.kr`) — 자리표시 아님 |
| `AppConfigViewModel.setDebugOverride` | `MainNavGraph.kt:806` `if (debuggable)` 로 **디버그 빌드에만** 노출. 릴리스 미노출 확인 |

**더미 저장소 구현체는 `DummyRideRepository` 하나뿐이다** — `data/.../repository/` 의 나머지 7개는
모두 `Remote*` 이고, `PlaceRepository`·`ChatRepository`·`AuthRepository` 등은 구현체가 하나씩뿐이다.

---

## 2. 부르는 데가 없는 공개 API

도메인 저장소 인터페이스의 모든 메서드를 열거해 presentation+app 참조 수를 기계적으로 센 뒤
(각 이름을 `grep -rhoE "\b<name>\b" presentation/src/main app/src/main` 로 집계), 0건인 것만
전 모듈 수동 확인했다. 아래가 0건의 **전부**다.

| 항목 | 파일:라인 | 서버에 API 가 있나 | 등급 | 근거·영향 |
|---|---|---|---|---|
| `RideRepository.getNearbyParties` | `domain/.../RideRepository.kt:15` | **없다** | **지워야 함** | §1-A. 가짜 데이터의 유일한 출구 |
| `RideRepository.getParties` | `domain/.../RideRepository.kt:19` | **있다** — `MatchingApi.kt:30` `GET /api/v1/matching/rooms` | **판단 필요** (남기는 쪽) | 화면은 범위 조회(`getPartiesWithin`)만 쓴다. 분류 (a) 「서버엔 있고 화면만 없는 것」. 구현은 진짜 서버 호출이라 **가짜 데이터가 아니다**. 지우면 전체 목록 계약 기록이 사라진다 |
| `ChatRepository.joinChatRoom` | `domain/.../ChatRepository.kt:33` | **있다** — `RemoteChatRepository.kt:123` → `api.joinRoom` | **판단 필요** (남기는 쪽) | 앱은 방 입장을 서버가 자동 처리하는 흐름(정원 충족 시 생성)이라 부를 자리가 없다. 분류 (a) |
| `ChatRepository.deleteMessage` | `domain/.../ChatRepository.kt:119` | **있다** — `ChatApi.kt:96` | **판단 필요** | 메시지 삭제 UI 가 없다(신고만 있다). 분류 (a). `ChatError.isNotMessageOwner` 와 한 묶음 |
| `ChatError.isRoomNotFound` | `domain/.../ChatError.kt:35` | 해당 서버 코드 존재 | **남겨야 함** | 전 모듈(테스트 포함) 참조 0건이지만 `CHAT_ROOM_NOT_FOUND` 서버 코드의 **계약 기록**이다. `get()` 뿐이라 런타임 비용 0 |
| `ChatError.isNotMessageOwner` | `domain/.../ChatError.kt:39` | 동일 | **남겨야 함** | 위와 같음. `deleteMessage` 가 연결되면 바로 쓰인다 |
| `ChatError.isUnauthorized` | `domain/.../ChatError.kt:43` | 동일 | **남겨야 함** | 위와 같음 |
| `UserSession.isLoggedIn` | `domain/.../UserSession.kt:26` | — | **남겨야 함** | presentation 0건이지만 `RemoteAuthRepository.kt:136` 이 쓴다. **죽지 않았다** |
| `UserSession.currentUserUuid` | `domain/.../UserSession.kt:22` | — | **남겨야 함** | presentation 0건이지만 data 계층 핵심(`RemoteRideRepository.kt:61,77`, `RemoteChatRepository.kt:79,236,243,247,256,266`, `RemoteActivePartyRepository.kt:44,58`). **죽지 않았다** |

### 모델 필드 — presentation 에서 한 번도 **읽지** 않는 것

도메인 `data class` 의 모든 프로퍼티에 대해 `.<name>` 접근을 집계한 뒤 0건을 수동 검증했다.
(`id`·`status` 처럼 클래스 간 이름이 겹치는 것은 과다 집계되므로, **0건만** 신뢰 근거로 썼다.)

| 필드 | 파일:라인 | 실태 | 등급 |
|---|---|---|---|
| `User.rating: Double` | `domain/.../User.kt:18` | **아무 화면도 읽지 않는다.** 매퍼가 **항상 `0.0` 을 써 넣는다**(`PartyMappers.kt:52, 91`). presentation 의 `rating` 등장 3곳은 전부 *쓰기* 또는 주석: `PartnerProfileScreen.kt:256`(프리뷰 리터럴), `ProfileSetupScreen.kt:467`·`JoinConfirmScreen.kt:460`(지웠다는 주석). 평가 API 제거(`BACKEND-GAP-CLEANUP` §1 「23 매너 점수·후기」)의 **잔재** | **판단 필요** — 서버에 평가 API 가 없어 (b) 지만, `User.kt:10-12` KDoc 이 "4.9·매너 98% 같은 **가짜 값을 지어내면 안 된다**"는 **가드레일**을 담고 있다. 지우면 그 경고가 사라진다. **지우려면 경고를 주석으로 옮길 것.** 그리고 이 필드가 `DummyRideRepository` 가 4.9/4.8/4.7 을 써 넣는 **통로**였다 |
| `ChatMembership.joinedAt: String` | `domain/.../Chat.kt:40` | 매퍼가 채우지만(`ChatMappers.kt:56`) **읽는 곳 0건**(main·test 모두) | **지워야 함** — 서버가 주지만 앱이 쓸 데가 없다. 영향 거의 없음(매퍼 한 줄). 주의: 파티 쪽 `joinedAt`(방장 추정)과 **다른 것**이다 — 그 금지 서술은 `PartyDtos.kt:80-85` 에 따로 있어 함께 사라지지 않는다 |
| `ChatMessage.deleted: Boolean` | `domain/.../Chat.kt:104` | 매퍼가 채우지만(`ChatMappers.kt:121`) 화면이 **읽지 않는다**(테스트만 참조) | **남겨야 함** — 그런데 이건 죽은 코드라기보다 **결함 후보**다: 서버가 `deleted=true` 로 내린 메시지를 앱이 **그대로 본문째 보여준다**. 지우지 말고 「삭제된 메시지」 처리를 검토할 것 |
| `ChatMessage.userId: Long?` | `domain/.../Chat.kt:83` | 항상 null | **남겨야 함** — `Chat.kt:77` 이 "항상 null 이다 — 서버가 내부 PK 를 응답에서 뺐다"고 **명시적으로 계약을 기록**한다 |
| `PartyStatus.currentMembers: Int` | `domain/.../Ride.kt:89` | 매퍼가 채우지만(`PartyMappers.kt:154`) 읽는 곳 0건. `MatchWaitingRoute.kt:622` 가 "합류 응답의 `currentMembers` 도 21 로 넘기지 않는다"고 **의도적으로** 적어 뒀다 | **남겨야 함** — Backend #213 합류 응답 형태(`{status, currentMembers}`)의 계약 기록 |
| `UserProfileInfo.uuid: String` | `domain/.../User.kt:68` | `UserMappers.kt:14` 가 채우지만 읽는 곳 0건. KDoc(`User.kt:64-65`) 자신이 "uuid 만 필요하면 **이 호출은 필요 없다** — 부르는 이유는 오직 `name`" 이라고 적는다 | **판단 필요** — 서버가 주는 값이고 비용 0. 지워도 무해하지만 남겨도 무해 |
| `ReverseAddress.roadAddress` / `jibunAddress` | `domain/.../Place.kt:20, 21` | `PlaceMappers.kt:51-55` 가 DTO 에서 꺼내 **도메인에 실어 보내지만 아무도 읽지 않는다**. 화면이 읽는 건 `address` 뿐(서버가 조합해 준 표시용) | **판단 필요** — **현재 브랜치가 `fix/place-roadname-null`** 이라 이 영역이 작업 중일 수 있다. 가짜 데이터 아님, 위험 0. 지우면 "도로명이 없는 좌표가 있다"는 계약 기록(`Place.kt:16`)이 약해진다. **사용자 확인 필요** |

### `BACKEND-GAP-CLEANUP.md` 교차 확인 — 잔재는 **주석뿐**

문서가 "지웠다"고 적은 식별자를 전부 `src/main` 에서 grep 했다.

| 식별자 | `src/main` 코드 잔재 |
|---|---|
| `reportMyLocation` · `getMemberLocations` | **0** (`MannerPledgeScreen.kt:242` 주석 1건만) |
| `MemberLocation` · `createChatRoom` · `closeChatRoom` · `getMyRides` · `cancelReady` · `startMatching` | **0** |
| `mannerScore` · `avatarColor` · `guardian` · `coupon` · `mileage` · `rideHistory` · `paymentMethod` · `receipt` · `settlement` | **0** |
| `radiusLabel` · `fingerprint` · `isSameAs` | **0** (전부 "지웠다/되살리지 말라"는 주석) |

→ 과거 정리는 **철저히 됐다**. 그 문서가 **놓친 것**은 §1-A(`getNearbyParties`+`DummyRideRepository`)와
§1-B(21·25 더미 폴백) 둘이다 — 애초에 목록에 없었다.

**부수 발견(죽은 코드 아님, 문서 오류):** `AppContainer.kt:75` 주석이 설정 조회 실패 시 기본값을
"**기사 모드**"라고 적지만, 실제 기본값은 `AppConfig.Default = AppConfig(taxiEnabled = false)` =
**동승 모드**다(`domain/.../AppConfig.kt:25`, `AppConfigViewModel.kt:39`). `MainNavGraph.kt:147` 주석은
"못 받으면 동승 모드(실배포)"로 올바르다. 한 줄 수정 대상.

---

## 3. 도달 불가능한 화면·라우트

`Routes.kt` 의 상수를 `MainNavGraph.kt` 의 `composable(...)` 등록과 대조했다.
등록된 목적지 25개: ONBOARDING_SAVING/TRUST/SAFETY, LOGIN, LOGIN_FORM, PROFILE_SETUP,
MANNER_PLEDGE, SIGNUP_COMPLETE, HOME, DESTINATION, DESTINATION_CONFIRM, EXPLORE, JOIN_CONFIRM,
MATCH_WAITING, RIDE_DETAIL, PARTNER_PROFILE, DISPATCH_STATUS, CHAT, CHAT_ROOM, RIDE_ONGOING,
EMERGENCY, RIDE_COMPLETE, MYPAGE, PROFILE_EDIT, ACCOUNT_DELETE.

**미등록 = 도달 불가: 5개** (알려진 09 말고 **4개 더 있다**)

| 라우트 | 파일:라인 | 화면 파일 (줄 수) | `Routes.X` 참조 | 화면 함수 참조 |
|---|---|---|---|---|
| `ACCOUNT_TYPE` (05) | `Routes.kt:22` | `auth/AccountTypeScreen.kt` (398) | **0** | 자기 `@Preview`(`:392`)뿐 |
| `SCHOOL_EMAIL` (06) | `Routes.kt:23` | `auth/SchoolEmailScreen.kt` (311) | **0** | 자기 `@Preview`(`:309`)뿐 |
| `EMAIL_CODE` (07) | `Routes.kt:24` | `auth/EmailCodeScreen.kt` (419) | **0** | 자기 `@Preview`(`:417`)뿐 |
| `WORK_VERIFY` (08) | `Routes.kt:25` | `auth/WorkVerifyScreen.kt` (316) | **0** | 자기 `@Preview`(`:314`)뿐 |
| `IDENTITY_VERIFY` (09) | `Routes.kt:26` | `auth/IdentityVerifyScreen.kt` (345) | **0** | 자기 `@Preview`(`:343`)뿐 |

확인 방법: `grep -rn "Routes\.<NAME>" presentation/src app/src` → 각 0건.
`grep -rn "\b<ScreenFn>\b" presentation/src app/src` → 자기 파일의 `@Preview` 외 0건.
합계 **1,789 줄**이 프리뷰에서만 쓰인다.

**등급: 남겨야 함 (5개 모두).** 근거는 추측이 아니라 **코드에 적힌 의도**다 —
`Routes.kt:14-21` 이 "B' · 부가 인증 05~09 — **가입 그래프에 등록하지 않는다**", 이유(서버 가입에
계정 유형 개념이 없고 학교/재직 인증은 나중에 마이페이지에서), 그리고 "**화면 파일과 라우트 상수는
남겨둔다 — 인증을 붙일 때 그대로 재사용한다**"고 명시한다. `IdentityVerifyScreen.kt:54-58` 도
"파일을 남긴 이유: 실제 본인 인증(SMS·PASS)을 붙일 때 통신사 선택 UI 와 폼 배치를 재사용한다".
**가짜 사용자 데이터는 들어 있지 않다**(확인함).

**다만 플래그할 것 — 검증 안 된 제품 주장 2건:**
`AccountTypeScreen.kt:130` `caption = "부산대 · 부경대 · 동아대"` 와
`SchoolEmailScreen.kt:193` `"부산대 · 부경대 · 동아대 메일을 지원해요"`.
학교 인증 API 가 서버에 없으므로(`BACKEND-GAP-CLEANUP` §1·§4) **지킬 수 없는 약속**이다.
화면이 도달 불가라 **지금 사용자에게 보이지는 않지만**, 인증을 붙일 때 이 문구를 그대로
되살리면 `presentation/src/test/.../ProductClaimsTest.kt` 가 막아 주는 종류의 거짓 주장이 된다.
→ **판단 필요**: 지금 고칠 필요는 없으나, 되살릴 때 반드시 재검토.

**도달 불가 화면은 이 5개가 전부다** — 다른 모든 `Routes` 상수는 `composable(...)` 과 1:1로 짝지어졌다.

---

## 4. 안 쓰는 DTO · 필드

`data/.../remote/dto/` 의 `@Serializable` 클래스 **45개 전수** 조사.

### 4-0. 전제: 응답 DTO 필드를 지워도 크래시하지 않는다

```
data/src/main/kotlin/com/moyeota/data/remote/NetworkModule.kt:22-24
    // ignoreUnknownKeys: 서버가 필드를 추가해도 앱이 죽지 않게 한다.
    // 기본값이 있는 DTO 필드는 explicitNulls 와 무관하게 누락 시 기본값이 쓰인다.
    private val json = Json { ignoreUnknownKeys = true }
```

`NetworkModule.kt:109` 에서 Retrofit 컨버터로 쓰인다. `data/src/main` 의 디코더 **8개가 모두**
`Json { ignoreUnknownKeys = true }` 다(`PartyMappers.kt:250`, `AuthMappers.kt:16`, `PlaceMappers.kt:15`,
`ChatMappers.kt:24`, `matching/PartySseEvent.kt:57`, `chat/ChatSocket.kt:51`, `auth/JwtSubject.kt:14`).
→ **선언을 지우면 그 키는 조용히 버려진다. 역직렬화 실패 위험 없음.**

(단서: `ignoreUnknownKeys` 는 *추가* 키만 용서한다. 명시적 `null` 을 기본값으로 바꿔 주지는 않으므로
`PlaceDtos.kt:24, 62`(`roadName`)가 nullable 로 선언된 이유는 그대로 유효하다 — `PlaceDtos.kt:21-23`.)

### 4-1. 안 쓰는 DTO **클래스**: 0개

45개 전부 도달 가능하다. 다만 **API 인터페이스에 이름이 안 나오는** 것들이 있어 오해하기 쉽다 —
아래는 **살아 있다**:

| 클래스 | 왜 살아 있나 |
|---|---|
| `PartyDtos.kt:17` `PartyListResponse.PartyItem` · `:92` `MemberInfo` · `PlaceDtos.kt:13` `PlaceSearchItem` · `:57` `FavoritePlaceItem` · `ChatDtos.kt:43` `ChatLastMessageResponse` | 상위 DTO 의 **필드 타입** |
| `PartyDtos.kt:138` `MemberJoinedPayload` · `:152` `MemberLeftPayload` | **SSE** 디코더(`matching/PartySseEvent.kt:67,68`) |
| `PartyDtos.kt:43` `PartyDetailResponse` | Retrofit 반환 + **SSE `connected` 페이로드**(`PartySseEvent.kt:25,66`) |
| `ChatDtos.kt:63` `ChatMessageResponse` | Retrofit + **STOMP 프레임 본문**(`chat/ChatSocket.kt:79,155`) |
| `ErrorDtos.kt:13` `ApiErrorDto` | Retrofit 타입이 아니고 `errorBody()` 디코딩 전용(4곳) |

또한 `grep -rn "remote\.dto" presentation/src/main domain/src/main app/src/main` → **0건**:
DTO 패키지가 `data` 밖으로 새지 않는다.

### 4-2. 선언만 하고 안 읽는 필드 — 10건

| 파일:라인 | 필드 | 실태 | 등급 |
|---|---|---|---|
| `PartyDtos.kt:62` `:63` | `PartyDetailResponse.departureRadius` / `destinationRadius` | `src/main` 읽기 0. `PartyMappersTest.kt:518,519` 가 `assertEquals(500, …)` 로 **서버 고정값을 못 박는다** | **남겨야 함** — `domain/.../Ride.kt:62-67` 이 "도메인에 태우면 아무것도 걸러내지 않는 숫자를 조건으로 보여 주게 된다"며 **일부러 도메인에서 뺀** 값이다. DTO 에 남은 것이 「서버는 여전히 500 을 내려보낸다」는 계약 기록이고, 테스트가 그 사실을 지킨다 |
| `PartyDtos.kt:226` `:227` | `OpenPartyResponse.departureRadius` / `destinationRadius` | 읽기 0(테스트도 *설정*만: `PartyMappersTest.kt:414,415`). `PartyMappers.kt:206` 에 건너뛰는 이유가 주석으로 있다 | **남겨야 함** — 위와 같은 이유 |
| `PartyDtos.kt:102` | `MemberInfo.badgeId` | **main·test 모두 읽기 0.** 매퍼가 대신 `verifiedLabel = ""` 를 하드코딩(`PartyMappers.kt:90`) | **남겨야 함** — 이게 `User.verifiedLabel`(화면이 **읽는** 필드, 항상 "")의 **서버측 출처**다. 서버 `badgeId` 가 TODO 라 항상 null 인 상태의 계약 기록(`PartyDtos.kt:99`, `User.kt:12`, `MemberDisplay.kt:52`). 인증 기능이 붙으면 바로 쓰인다 |
| `PartyDtos.kt:52` | `PartyDetailResponse.currentMembers` | 읽기 0. 상세는 `members.map{}`(`PartyMappers.kt:128`)으로 인원을 얻어 이 값이 필요 없다. 목록·생성·합류 응답의 동명 필드는 **읽힌다**(`:107, :154, :194`) | **판단 필요** — 서버가 주는 값이고 비용 0. 지워도 무해 |
| `PartyDtos.kt:65` `:229` | `PartyDetailResponse.createdAt` / `OpenPartyResponse.createdAt` | 읽기 0(테스트도 설정만). 화면에 방 생성 시각을 쓰는 곳이 없다 | **판단 필요** — 지워도 무해. 남겨도 무해 |
| `ChatDtos.kt:80` | `ChatMessageResponse.userId` | `src/main` 읽기 0(`ChatMappers.kt:91` 의 `userId` 는 **`ChatMemberResponse` 쪽**이다). 읽는 건 테스트 하나뿐: `ChatMappersTest.kt:335` | **남겨야 함** — 그 테스트 이름이 「`userId` 만 오는 **구버전 응답**은 파싱되되 발신자를 알 수 없다」다. 즉 **하위호환 계약을 지키는 테스트**이고, `ChatDtos.kt:61`·`domain/.../Chat.kt:77` 이 "서버가 userId 를 빼고 publicId 로 바꿨다"는 이력을 기록한다 |
| `UserDtos.kt:44` | `UpdateProfileRequest.imageKey` | 요청 필드인데 **항상 기본값 `null`** 이다. 유일한 호출 경로가 `ProfileEditRoute.kt:174 repository.updateProfile(nickname = nickname, imageKey = null)` → `RemoteAuthRepository.kt:205` | **남겨야 함** — 사용자가 지정한 예외: 프로필 이미지(`GET /users/profile-images`)는 **나중에 구현하기로 정해진 것**. `UserDtos.kt:29-31` 이 "`imageUrl` 이 아니라 `imageKey` 다"라는 하드-원 계약(Backend #235, 틀리면 400 `USER112`)을 기록한다. 최근 `6db78d7` 이 바로 이 이름을 고친 커밋이다 — 지우면 그 교훈이 사라진다 |

### 4-3. 프로필 이미지 관련 — 묶어서 **남겨야 함**

사용자가 「나중에 구현 확정」으로 지정한 항목이라 한자리에 모았다. **전부 가짜 데이터가 아니다**
(서버가 주는 실값이거나, 안 보내는 null이다).

| 항목 | 파일:라인 | 실태 |
|---|---|---|
| `User.imageUrl` | `domain/.../User.kt:28` | 서버가 **실제 URL 을 준다**(Backend #235). 매퍼가 채우지만(`PartyMappers.kt:93`) 화면이 안 그린다 |
| `MemberAvatar` 가 `imageUrl` 을 무시 | `presentation/.../MemberDisplay.kt:61-66, 69-89` | 이유가 적혀 있다: "앱에 이미지 로더(Coil 등) 의존성이 없다 … 일부만 사진이 보이는 상태가 더 어색하다". 계획은 `_workspace/profile-image-plan.md` |
| `UpdateProfileRequest.imageKey` | `UserDtos.kt:44` | §4-2 |
| `AuthRepository.updateProfile(imageKey)` 파라미터 | `domain/.../AuthRepository.kt:130` | 항상 null 전달 |

→ **손대지 말 것.** 이니셜 원을 그리는 현재 동작은 "사진이 없다"는 거짓을 말하지 않으므로
가짜 데이터가 아니다.

### 4-4. 깨끗한 파일

`AuthDtos.kt`(10 클래스) · `PlaceDtos.kt`(6) · `ReportDtos.kt`(3) · `ErrorDtos.kt`(1) ·
`AppConfigDtos.kt`(1) · `DispatchDtos.kt`(1) — **전 클래스·전 필드 사용 중**, 발견 0건.
요청 DTO 는 모두 실제 값으로 채워지고(예: `ReportRequestDto` 의 좌표는 측위 실패 시에만 null —
`RemoteRideRepository.kt:150` 의 의도된 설계), 응답 DTO 는 모든 프로퍼티가 매퍼에서 읽힌다.

### 4-5. 지울 경우 함께 고쳐야 하는 테스트

`PartyMappersTest.kt`(414, 415, 417, 505-507, 509, 518, 519, 763-765, 767, 789) ·
`ChatMappersTest.kt`(329, 335) · `RemoteAuthRepositoryTest.kt`(408, 410).
`matching/PartySseEventTest.kt`(27-30, 66, 80, 92, 206-210, 252)는 **JSON 문자열 리터럴**이라
`ignoreUnknownKeys` 덕에 DTO 필드를 지워도 **그대로 통과한다**(수정 불필요).

---

## 5. 쓰이지 않는 리소스 · 문자열 · 상수

리소스는 `app/src/main/res` **한 곳에만** 있다(23개 파일). `presentation`·`core/designsystem`·
`domain`·`data` 에는 `res/` 가 없고, `grep -rn '\bR\.' presentation/src core domain data` → **0건**
(모든 `R.*` 참조는 `app/.../MoyeotaFirebaseMessagingService.kt` 하나에 모여 있다).
리플렉션 조회(`getIdentifier` 등) **0건**이라 정적 분석으로 충분하다.

### 5-1. 미사용 색상 리소스 7개 — **지워야 함**

`app/src/main/res/values/colors.xml:3-9` — Android Studio 프로젝트 템플릿 잔재.

| 리소스 | 라인 | 값 |
|---|---|---|
| `purple_200` | 3 | `#FFBB86FC` |
| `purple_500` | 4 | `#FF6200EE` |
| `purple_700` | 5 | `#FF3700B3` |
| `teal_200` | 6 | `#FF03DAC5` |
| `teal_700` | 7 | `#FF018786` |
| `black` | 8 | `#FF000000` |
| `white` | 9 | `#FFFFFFFF` |

근거: 저장소 전체에서 `R.color.` / `@color/` 참조는 **단 2줄**이고 둘 다
`@color/ic_launcher_background` 다(`mipmap-anydpi-v26/ic_launcher.xml:3`, `ic_launcher_round.xml:3`).
`themes.xml:3` 의 `<style>` 은 `<item>` 자식이 없어 참조할 수 없다. 영향 없음.

### 5-2. 미사용 디자인시스템 멤버 — **판단 필요**

`core/designsystem/.../theme/Color.kt` — `MoyeotaColor` 35개 중 **5개** 참조 0건:
`InkDeep`(:30) · `MapOverlay`(:41) · `RouteUserB`(:47) · `MarkerPickup`(:50) · `MarkerDropoff`(:51)

`core/designsystem/.../theme/Type.kt` — `MoyeotaType` 13개 중 **4개** 참조 0건:
`DisplayXl`(:11) · `DisplayLg`(:12) · `HeadingXl`(:14) · `ButtonSm`(:23)

근거: `grep -rno "MoyeotaColor\.<Name>\b"` / `MoyeotaType.<Name>` 전 모듈 0건(선언부는 접두사가
없어 집계에 안 섞인다 — 실제 1회 사용 멤버는 1로 나온다). `with(MoyeotaColor)` ·
`import …MoyeotaColor.*` 형태의 우회 접근도 0건이라 접두사 집계가 전부를 덮는다.

**남기는 쪽 논거**: 이들은 디자인 토큰이고 `moyeota-figma-tokens.json`(52, 92-113행)에 대응 키가 있다 —
Figma 쪽 토큰과의 대조표 성격. `MarkerPickup`·`MarkerDropoff`·`RouteUserB` 는 지도 마커/경로 색으로
`_workspace/68_member_live_location.md:30` 이 계획에 쓴 이름이다(동승자 위치 공유가 되살아나면 쓰인다 —
`BACKEND-GAP-CLEANUP` §4). **사용자 판단 필요.**

### 5-3. 미사용 Kotlin 상수 2개 — **지워야 함** (사소)

| 항목 | 파일:라인 | 근거 |
|---|---|---|
| `private val BadgeGrayBg = Color(0xFFF3F6FA)` | `presentation/.../explore/ExploreScreen.kt:91` | 자기 파일 내 참조 0 (private 이므로 파일 범위가 맞는 범위). 비교군: 같은 방식으로 센 private `Color` 상수 138개 중 **이것만** 0이었다 |
| `val MapSheetContentPadding: Dp = 16.dp` | `core/designsystem/.../component/MapSheetScaffold.kt:214` | public·크로스모듈인데 전 모듈 참조 1건(선언부)뿐. 형제 상수 `MapSheetDefaults.MapRevealHeight`·`CollapsedSheetHeight`·`MapOverlayShadow`·`MoyeotaDefaultCamera` 는 모두 사용 중 |

### 5-4. 미사용 drawable·string·style·xml — **없음**

- drawable: `ic_notification.xml` 1개뿐이고 `MoyeotaFirebaseMessagingService.kt:164` 가 쓴다.
- string 5개 전부 사용(`app_name` → Manifest:23 + 서비스:132, 알림 문구 4개 → 서비스 :82,:83,:108,:110).
- `Theme.Moyeota`(themes.xml:3) → Manifest:26.
- **간접 참조라 살아 있는 것**(죽은 것으로 오해하지 말 것): 런처 아이콘 `ic_launcher`(Manifest:22)·
  `ic_launcher_round`(:24), 어댑티브 레이어 `ic_launcher_foreground`·`ic_launcher_background`
  (**XML 에서만** 참조 — `mipmap-anydpi-v26/*.xml:3-4`), `backup_rules.xml`(Manifest:21,
  API<31 전용이고 `minSdk 24` 라 여전히 유효)·`data_extraction_rules.xml`(Manifest:20, API≥31 전용).
  google-services 생성 리소스는 `app/build/generated/` 에 있어 `src/*/res` 집합 밖이다.

### 5-5. 중복 색상 상수 — **판단 필요** (출시 차단 아님)

화면 파일마다 같은 hex 를 file-private `val` 로 재선언하는 패턴이 광범위하다.
private `Color` 선언 138개 중 76개가 아래 4개 hex 다.

| hex | 선언 수 | 쓰이는 이름들 | 비고 |
|---|---|---|---|
| `0xFF8A93A0` | **23** | `GrayMute`·`MuteGray`·`TextSoft`·`EmMuteGray`·`LabelGray`·`StageGrayMute` | `EmailCodeScreen.kt:360` 엔 함수 지역 복사본까지 있다 |
| `0xFF9AA1AC` | **22** | `GrayAsh`·`AshGray`·`TextFaint`·`FooterGray`·`CounterGray`·`EmAshGray` | |
| `0xFFF5F7FA` | **16** | `CanvasBg`·`ScreenBg`·`ScreenBackground`·`EmergencyBg`·`StageCanvasBg` | `MoyeotaColor.SurfaceSoft = 0xFFF4F6FA`(Color.kt:39)와 **한 자리씩 다르다** — 의도인지 오타인지 불명 |
| `0xFF4B5563` | **15** | `GraySlate`·`SlateGray`·`TextSlate`·`StageGraySlate` | |
| `0xFFF1F5FD` | 12 | `ChipBg`·`ChipBlueBg`·`HeroBg`·`RouteCardBg`·`InfoCardBlue`·`PillBg` | `JoinConfirmScreen.kt:67,69` 는 **한 파일에 두 이름**으로 같은 hex |
| `0xFFF6F8FB` | 8 | `CardSoft`·`IconBoxIdle`·`DocCardGray`·`FareCardBg`·`SoftBg` | |
| `0x1A1B2A4A` | 4 | `CardShadow`·`EmCardShadow`·`BubbleShadow` | 디자인시스템이 이미 `MapOverlayShadow`(MapSheetScaffold.kt:211)로 내보낸 값 |

**이미 `MoyeotaColor` 에 있는 값을 다시 선언한 것 4건**:
`0xFF6B7280`=`TextMute` → `EmergencyScreen.kt:70`·`JoinConfirmScreen.kt:66` /
`0xFFD1D5DB`=`TextAsh` → `AccountTypeScreen.kt:56` /
`0xFFDC2626`=`Safety500` → `RideOngoingScreen.kt:77` /
`0xFFFDECEE`=`Danger50` → `RideOngoingScreen.kt:76` (뒤 둘은 주석이 중복을 인지하고 있다).

죽은 코드는 아니다(전부 쓰인다). **출시를 막을 사안이 아니므로 별 작업으로 분리 권장.**

---

## 6. 조사 범위와 한계 (추측 배제 기록)

**확인한 것**
- 소스 전수: `.kt` 207개(`/build/` 제외) + `app/src/main/res` 23개 + 매니페스트 2개 + Gradle 스크립트.
- DI·리플렉션 간접 주입 경로 부재(§1 전제)를 **직접 확인**했다 — "grep 0건"만으로 단정하지 않았다.
- R8 비활성(`app/build.gradle.kts:104-106`)이라 미사용 코드도 APK 에 남는다는 점을 반영했다.
- `getNearbyParties` 같은 0건 주장은 전 모듈(테스트 포함) 재확인 후 적었다.

**확인하지 못한 것 (제약: 빌드·에뮬레이터 금지)**
- §1-B 의 실기 재현(회전 후 21·25 에 더미가 뜨는지). 정적으로는 경로가 **열려 있다**:
  `remember` 소실 + 백스택 복원 + `rememberSaveable` 로 복구 차단 + `configChanges` 미선언.
  `MainActivity.kt:33-37` 주석이 구성 변경 재생성을 전제한다는 점이 보강 근거다. 절차는 §1-B-4.
- 미사용 판정은 Kotlin/Compose 정적 참조 기준이다. Compose 컴파일러 플러그인과
  kotlinx.serialization 플러그인은 코드 생성 방식이라 리플렉션 경로를 만들지 않는다(확인함).

**다음 단계 제안 (사용자 지시 대기)**
1. §1-B 를 먼저 — 유일하게 **릴리스 사용자에게 보이는** 가짜 데이터다.
2. §1-A — `getNearbyParties`·`DummyRideRepository`·테스트 3건·`api-integration` 스킬 문서를 한 묶음으로.
3. §3 의 1,789줄과 §5-2 토큰은 **의도가 코드에 적혀 있으니** 지우기 전 확인.
