# 107 입력 — 백엔드 미지원 기능 일괄 제거 (감사 보고서 102 의 1~7·9~11, 8번은 유지)

작성: 2026-10-08. 브랜치 refactor/remove-unused-screens. 미커밋 변경 다수 — 되돌리거나 stash 금지, 그 위에 추가만.
근거: `_workspace/102_audit_frontend_backend_gap.md`. 기준 백엔드: Backend develop (1a75f82).
**유지(8번)**: 12 매너 서약의 동의 항목·"이용약관 · 개인정보 처리방침" 링크 문구는 그대로 둔다.

공통 원칙: 백엔드에 대응 API 가 없는 기능은 화면 요소·파라미터·라우트·컴포저블·import 를 모두 지운다. "준비 중" 같은 자리표시 문구로 바꾸지 않는다. KDoc/주석을 현재 상태로 갱신. 빌드: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:assembleDebug --console=plain -q` (동시 빌드 락이면 재시도). 에뮬레이터 설치는 리더.

## 묶음 D — api-integrator (domain/ · data/ 만)
D1. (2번) 동승자 위치 공유 API 제거: `MatchingApi` 의 `POST matching/rooms/{partyId}/location`, `GET matching/rooms/{partyId}/locations` 와 DTO(PartyDtos.kt 의 위치 관련), 매퍼(PartyMappers.kt), `RideRepository.reportMyLocation / getMemberLocations`, `RemoteRideRepository`·`DummyRideRepository` 구현, `Ride.kt` 의 MemberLocation 류 모델(다른 곳에서 안 쓰면), 관련 테스트(RemoteRideRepositoryTest, RemoteActivePartyRepositoryTest). 백엔드에 이 경로가 없다(404).
D2. (11번) 죽은 코드 제거: `ChatRepository.createChatRoom / closeChatRoom`, `ChatApi` 의 `POST api/v1/chat-rooms`, `DELETE api/v1/chat-rooms/{id}`, `RemoteChatRepository` 구현, 관련 테스트. presentation 호출처는 없다(있으면 보고).
D3. `:domain:compileDebugKotlin :data:testDebugUnitTest` 통과. 보고서 `_workspace/108_api-integrator_D.md`. **presentation 은 묶음 Q 가 호출부를 동시에 지운다 — 건드리지 말 것.**

## 묶음 Q — compose-builder-Q (chat · matching · onboarding · home · auth 일부). MainNavGraph.kt 수정 금지 → 필요한 NAV 변경은 보고서에 정확한 코드로.
Q1. (2번) RideOngoingRoute.kt:117-129 의 5초 위치 보고·조회 루프와 RideOngoingScreen 의 동승자 마커 그리기를 제거(내 위치 파란 점은 유지). ChatScreen.kt:457-471 의 24b 「실시간 위치 공유 시작」 공유 시트와 진입 버튼, `onStartLocationShare` 파라미터(ChatScreen·ChatRoute) 제거 → NAV:631,661 의 `onStartLocationShare = …` 줄 삭제 필요(보고서에 기재).
Q2. (3번) PartnerProfileScreen.kt 「신고」(72,95) 와 onReport 파라미터 제거. NAV:598-602 호출부에 onReport 가 없으면 NAV 변경 불필요. **26 운행 중의 신고(→27 긴급 신고)는 실제 연결이므로 유지.**
Q3. (6번) DispatchStatusScreen.kt 기사 전화 아이콘(232-238)과 "기사 정보 준비 중"(224-226) 제거. 기사 카드에는 서버가 주는 값(좌석·차종·번호판 등 DriverSummary 실필드)만 남긴다.
Q4. (7번) ProfileSetupScreen.kt 아바타 색 선택(101,221-234, profileAvatarPalette) 제거 — 아바타는 단일 기본색.
Q5. (9번) 문구: MannerPledgeScreen.kt pledgeItems 에서 「요금은 내릴 때 바로 정산할게요 / 미정산이 쌓이면 매칭이 막혀요」 항목 삭제. RideOngoingScreen 경유 순서의 "내린 뒤 현장에서 1/N 정산" 행 삭제. OnboardingTrustScreen.kt:106 "인증을 마친 이용자만 매칭되고\n같은 성별끼리만 함께 타요" → "같은 방향 사람과 매칭되고\n같은 성별끼리만 함께 타요". OnboardingSafetyScreen.kt:105 "운행 중 위치를 지인과 공유하고\n1/N 정산도 자동으로 끝나요" → "운행 중 긴급 신고가 바로 되고\n요금은 인원수대로 나눠 안내해요". (제목 등 다른 카피는 유지)
Q6. (10번) DestinationRoute.kt `DemoOrigin`(부산대학교 정문 고정 좌표) 폴백 제거. 실위치가 없으면 출발지를 비워 두고, 15 화면에서 출발지 입력(검색)으로 채우게 하거나 「경로 확인하기」를 비활성화 + "현재 위치를 확인하거나 출발지를 검색해 주세요" 안내. 기존 출발지 검색 모드(originActive)가 있으니 그것을 활용. DemoOrigin 을 참조하는 matching/* 더미(Preview 전용)는 Preview 안에서 자체 더미로 대체.
Q7. 빌드 통과(묶음 D 와 동시 진행이라 D1 이 끝나기 전엔 호출부 제거가 선행돼야 컴파일됨 — Q1 을 가장 먼저 하라). 보고서 `_workspace/109_compose-builder_Q.md` (NAV 변경 필요 목록 포함).

## 묶음 P — compose-builder-P (payment · mypage · NavGraph · Routes). **묶음 Q 완료 후 실행** (Q 의 NAV 변경 목록을 함께 반영).
P1. (1번) 결제 흐름 삭제: feature/payment/ 의 FareFinalScreen·SettlementScreen·PaymentMethodsScreen·PaymentAddScreen·PaymentResultScreen 파일 삭제, Routes.FARE_FINAL/SETTLEMENT/PAYMENT_METHODS/PAYMENT_ADD/PAYMENT_RESULT 삭제, NAV 의 해당 composable 블록 삭제. NAV:693 `onRideFinished` 는 `Routes.FARE_FINAL` 대신 33 `Routes.RIDE_COMPLETE`(실제 상수명 확인)로 가게 하고 popUpTo 유지. 33 RideCompleteScreen 은 이미 실값 요약 + 「홈으로」.
P2. (4번) MyRidesScreen.kt(34) 와 Routes.MY_RIDES, NAV:758 블록 삭제. MyPageScreen 「탑승 기록」 행과 onRideHistoryClick 제거 — 설정 목록 카드가 비면 카드 자체 제거. (34 가 보여주던 진행 중 방은 홈 배너가 이미 보여준다.)
P3. (5번) MyPageScreen 탑승 횟수 카드와 rideCount 파라미터 제거, NAV 의 rideCount 전달·MY_PAGE LaunchedEffect(refresh) 중 탑승 횟수 전용이면 제거.
P4. (7번) MyPageScreen 프로필 카드의 꺾쇠(›)와 clickable 제거 — 갈 화면이 없다.
P5. Q 보고서의 NAV 변경 목록 반영. 빌드 통과. 보고서 `_workspace/110_compose-builder_P.md`.

## 리더 사후
- 최종 빌드 + `:data:testDebugUnitTest`, grep(FARE_FINAL|SETTLEMENT|PAYMENT_|MY_RIDES|reportMyLocation|getMemberLocations|createChatRoom|closeChatRoom|DemoOrigin|부산대학교 정문|기사 정보 준비 중|onStartLocationShare 0건), 에뮬레이터 설치·캡처.
- 기록 MD: `docs/` 에 백엔드 미지원 기능 제거 이력 작성.
