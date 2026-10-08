# 109 compose-builder-Q — 백엔드 미지원 기능 제거 (묶음 Q)

작성: 2026-10-08. 입력: 107. 빌드: `:app:assembleDebug` 통과, `:presentation:testDebugUnitTest` 통과.

## 변경 파일 (presentation/src/main/kotlin/com/moyeota/presentation/feature/)
- chat/RideOngoingRoute.kt — Q1: `shareLocations` 루프·`memberLocations` 상태·`LOCATION_SHARE_INTERVAL_MS`·MemberLocation/rememberUpdatedState import 제거. 완료 폴링·지도용 상세 1회 조회는 유지.
- chat/RideOngoingScreen.kt — Q1: `memberLocations` 파라미터(Screen·RideOngoingMap)와 동승자 마커(MapMarker) 제거, 내 위치 파란 점 유지. Q5: 경유 순서 「내린 뒤 현장에서 1/N 정산」 행 삭제(카드 150→110dp, 연결선 88→50dp, RouteStepState.PENDING 제거). KDoc 갱신.
- chat/ChatScreen.kt — Q1: 24b 공유 시트(「실시간 위치 공유 시작」)·입력 바 「＋」 버튼·PlusIcon·`onStartLocationShare` 파라미터 제거. KDoc 갱신.
- chat/ChatRoute.kt — Q1: 내부 ChatRoomRoute 의 `onStartLocationShare` 파라미터·전달 제거. 공개 `ChatRoute`·`ChatRoomDestinationRoute` 는 NAV 컴파일용으로 **미사용 기본값 파라미터만 남김**(P 삭제 대상).
- matching/PartnerProfileScreen.kt — Q2: 헤더 「신고」 텍스트와 `onReport` 파라미터 제거. NAV 호출부(591)는 onReport 를 넘기지 않아 NAV 변경 불필요. 26 운행 중 → 27 긴급 신고는 유지.
- matching/DispatchStatusScreen.kt — Q3: 「기사 정보 준비 중」 줄, 기사 전화 아이콘 박스, PhoneIcon 제거. 차량 카드는 번호판·차종·좌석수만. KDoc 갱신. Preview 더미 출발지명 교체.
- matching/MatchWaitingScreen.kt, matching/DispatchStageScreens.kt — Q6: Preview 더미 출발지 "부산대학교 정문" → "센텀시티역 3번 출구".
- auth/ProfileSetupScreen.kt — Q4: 아바타 색 선택(selectedColor, swatch Row, ProfileColorSwatch, profileAvatarPalette/ProfileAvatarSwatch) 제거 → 단일 기본색(AvatarOuter/AvatarInner) 실루엣.
- auth/MannerPledgeScreen.kt — Q5: 「요금은 내릴 때 바로 정산할게요 / 미정산이 쌓이면 매칭이 막혀요」 항목 삭제. 약관 동의 항목·링크 문구 유지(8번).
- onboarding/OnboardingTrustScreen.kt — Q5: 본문 → "같은 방향 사람과 매칭되고\n같은 성별끼리만 함께 타요", KDoc 목적 줄 갱신.
- onboarding/OnboardingSafetyScreen.kt — Q5: 본문 → "운행 중 긴급 신고가 바로 되고\n요금은 인원수대로 나눠 안내해요", KDoc 목적 줄 갱신.
- home/DestinationRoute.kt — Q6: DemoOrigin 선언·폴백 제거. `UiState.origin: Place?` (고른 출발지 > 실위치 > null). origin 이 null 이면 onConfirmRoute 를 호출하지 않는다.
- home/DestinationScreen.kt — Q6: `origin: String? = null`. null 이면 출발지 칸에 "출발지를 검색해 주세요"(회색) + 우측 "검색", CTA 비활성, INFO 배너 "현재 위치를 확인하거나 출발지를 검색해 주세요". 탭하면 기존 originActive 검색 모드.
- home/DestinationConfirmRoute.kt — Q6 연쇄: `origin: Place?`(DemoOrigin 기본값 제거). null 이면 방 생성 안 하고 "출발지가 없어요. 15 목적지 화면에서 출발지를 검색해 주세요" 안내.
- home/DemoOriginCompat.kt (신규, **임시**) — NAV:477 과 explore/ExploreScreen.kt:739 가 아직 DemoOrigin 을 참조해 컴파일용으로만 남김. @Deprecated.

## NAV 변경 필요 (core/MainNavGraph.kt — 묶음 P)
1. NAV:631 삭제 — `                onStartLocationShare = { navController.navigate(Routes.RIDE_ONGOING) },`
2. NAV:661 삭제 — `                    onStartLocationShare = { navController.navigate(Routes.RIDE_ONGOING) },`
3. NAV:477 `                origin = confirmedOrigin ?: DemoOrigin,` → `                origin = confirmedOrigin,`
4. NAV:45 import 삭제 — `import com.moyeota.presentation.feature.home.DemoOrigin`
5. NAV:174-175 주석 갱신 — `// 출발지는 고르지 않으면 null 이고 16 이 DemoOrigin 으로 떨어진다.` → `// 15 는 출발지(고른 곳 또는 실위치)가 있어야만 16 으로 넘긴다. 15 를 거치지 않은 복원 진입이면 null — 16 이 안내만 한다.`
6. PartnerProfileScreen 호출부(591) — 변경 불필요(onReport 미전달).

## P 가 NAV 를 고친 뒤 지울 것 (Q 쪽 잔여)
- chat/ChatRoute.kt 의 `ChatRoute` · `ChatRoomDestinationRoute` 에 남긴 `@Suppress("UNUSED_PARAMETER") onStartLocationShare: () -> Unit = {},` 두 줄(KDoc 한 줄 포함) — NAV 1·2 반영 후 삭제.
- home/DemoOriginCompat.kt — NAV 3·4 반영 **그리고** explore/ExploreScreen.kt 의 `FallbackOrigin = LatLng(DemoOrigin.latitude, DemoOrigin.longitude)`(지도 기준점·「내 위치(기준점)」 마커·defaultExploreBounds)를 다른 기준으로 옮긴 뒤 파일 삭제. ExploreScreen 은 Q 범위 밖이라 손대지 않았다 — 리더 결정 필요(예: `MoyeotaDefaultCamera` 로 교체하고 기준점 마커 제거).

## 범위 밖 잔여 후보 (보고만)
- auth/MannerPledgeScreen.kt:215-216 「운행 중 내 위치를 동승자와 공유하는 데 동의해요」 동의 항목 — 위치 공유 API 를 지웠으니 의미 없는 동의. 8번(동의 항목 유지) 지시로 남겨 둠.
- onboarding/OnboardingSafetyScreen.kt:146,195,212 일러스트 배지 「보호자에게 실시간 공유 중」 — 보호자 공유 기능 없음. "다른 카피 유지" 지시로 남겨 둠.

## grep (Q 파일 대상)
`reportMyLocation|getMemberLocations|실시간 위치 공유 시작|onStartLocationShare|기사 정보 준비 중|DemoOrigin|부산대학교 정문|profileAvatarPalette|내릴 때 바로 정산|현장에서 1/N|인증을 마친 이용자만|정산도 자동으로`
→ 남은 히트는 의도된 임시분뿐: ChatRoute.kt:676,866(onStartLocationShare 기본값), home/DemoOriginCompat.kt(DemoOrigin·부산대학교 정문). P 의 NAV 반영 + 위 정리 후 0건.
