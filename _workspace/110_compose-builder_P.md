# 110 compose-builder-P — 결제·마이페이지·NAV 정리 (묶음 P + P6~P9)

작성: 2026-10-08. 입력: 107 묶음 P, 109 Q 보고서, 리더 추가 지시(P6~P9).
빌드·테스트: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:assembleDebug :presentation:testDebugUnitTest --console=plain -q` → exit 0.

## 삭제 파일 (presentation/src/main/kotlin/com/moyeota/presentation/)
- feature/payment/ 전체 (FareFinalScreen·SettlementScreen·PaymentMethodsScreen·PaymentAddScreen·PaymentResultScreen)
- feature/mypage/MyRidesScreen.kt
- feature/home/DemoOriginCompat.kt (Q 가 남긴 임시 파일, 미추적 상태였음)

## 변경 파일
- core/Routes.kt — FARE_FINAL/SETTLEMENT/PAYMENT_METHODS/PAYMENT_ADD/PAYMENT_RESULT/MY_RIDES 와 「H · 요금·정산·결제」 절 삭제. 절 제목 「I · 완료 · 마이페이지」.
- core/MainNavGraph.kt — 결제 5종·MyRides import 와 composable 블록 삭제. onRideFinished → `Routes.RIDE_COMPLETE` + `popUpTo(Routes.RIDE_ONGOING) { inclusive = true }`. Q NAV 1~5 반영. MYPAGE 의 refresh LaunchedEffect·rideCount·onRideHistoryClick 삭제. 34 언급 주석 정리. **추가 수정**: `finishedRide` 상태 신설 — onRideFinished 가 clearParty() 직전 activeRide 를 잡아 두고 33 이 그 값으로 요약을 그린다(기존엔 clearParty 가 activeRide 를 비운 뒤 33 이 activeRide 를 읽어 요약이 항상 빈 상태였다. 결제 흐름이 끼어 있을 땐 가려졌던 문제).
- feature/mypage/MyPageScreen.kt — 탑승 횟수 카드(StatCell)·설정 목록 카드(탑승 기록 SettingRow/SettingDivider/DocIcon)·rideCount/onRideHistoryClick 파라미터·프로필 카드 꺾쇠(ChevronRightIcon) 삭제. 구성: 프로필 카드(이름만, 표시 전용) → 로그아웃(+버전). 미사용 색/import 정리, Preview 2종(이름/이름 미설정)으로 교체.
- feature/mypage/RideCompleteScreen.kt — KDoc 진입 경로를 26 자동 전이로 갱신.
- feature/chat/ChatRoute.kt — ChatRoute·ChatRoomDestinationRoute 의 임시 `onStartLocationShare` 파라미터와 KDoc 줄 삭제.
- feature/explore/ExploreScreen.kt — FallbackOrigin 삭제. 지도 시작점·defaultExploreBounds 폴백을 `MoyeotaDefaultCamera`(core/designsystem NaverMapView.kt, 부산 서면)로. 「내 위치(기준점)」 마커 삭제(실위치 있을 때 MyLocationOverlay 만). KDoc 갱신.
- feature/onboarding/OnboardingSafetyScreen.kt — 「보호자에게 실시간 공유 중」 배지·SafetyCheckGlyph 사용부 삭제. 일러스트를 Box(contentAlignment=Center)로 바꿔 지도 목업을 카드 가운데에, 240x150 → 260x180 으로 키워 빈자리 보정(카드 높이 330dp 는 Trust 화면과 맞춰 유지). CircleShape import 정리.
- core/location/MyLocationState.kt — KDoc 의 `DemoOrigin` 언급을 현재 동작으로 갱신.
- 주석만: core/ActiveParty.kt(34 배너 언급), feature/chat/RideOngoingRoute.kt·RideOngoingScreen.kt(28 → 33), feature/matching/DispatchStatusRoute.kt(26→33), feature/home/DestinationConfirmModal.kt(25/32 → 25).

## 항목별
- P1 결제 흐름 삭제, 26 → 33 직행 + popUpTo(RIDE_ONGOING inclusive) — 완료
- P2 MyRides(34)·Routes.MY_RIDES·NAV 블록·「탑승 기록」 행/카드 삭제 — 완료
- P3 탑승 횟수 카드·rideCount·MYPAGE refresh 삭제 — 완료
- P4 프로필 카드 꺾쇠 삭제(원래 clickable 없음) — 완료
- P5 Q NAV 1~5 반영(6 은 불필요) — 완료
- P6 ChatRoute 임시 파라미터, DemoOriginCompat.kt 삭제 — 완료
- P7 FallbackOrigin → MoyeotaDefaultCamera, 기준점 마커 삭제 — 완료
- P8 보호자 공유 배지 삭제, 레이아웃 보정 — 완료
- P9 12 매너 서약 위치 공유 동의 항목 유지 — 손대지 않음

## grep (presentation/src/main)
`FARE_FINAL|SETTLEMENT|PAYMENT_|MY_RIDES|MyRidesScreen|onStartLocationShare|DemoOrigin|FallbackOrigin|부산대학교 정문|보호자에게 실시간|rideCount =|onRideHistoryClick`
→ 1건: feature/matching/PartnerProfileScreen.kt:255 `rideCount = 12,` — 23 동승자 프로필 Preview 의 `User(...)` 생성자 인자(domain User.rideCount 는 기본값 없는 필수 필드, 서버가 멤버마다 주는 실값). 마이페이지 탑승 횟수와 무관해 남김. 0건이 꼭 필요하면 grep 패턴을 `rideCount = activeRide` 등으로 좁히길 제안.

## 남은 문제
- 33 은 26 경유가 아닌 진입(앱 재시작 복원 등)이면 finishedRide 가 null 이라 요약 없이 「홈으로」만 보인다(기존 설계대로 요소 숨김).
- 에뮬레이터 실기 확인 미실시(리더 몫).
