# 103 입력 — 가입 위치정보 동의 추가 · 고정 목 문구 제거 · 평가/매너/후기 제거

작성: 2026-10-08. 브랜치 refactor/remove-unused-screens. 작업 트리에 미커밋 변경 7파일(LoginScreen, LoginFormScreen, MainNavGraph, MyPageScreen, HomeScreen, HomeRoute, DestinationScreen) — 되돌리거나 stash 금지, 그 위에 추가만.
근거 보고서: `_workspace/102_audit_frontend_backend_gap.md` (파일:라인 전부 여기 있음).
범위: presentation 모듈만. domain/data 변경 금지. 결제 흐름(28 FareFinal·29 Settlement·30 PaymentMethods·31 PaymentAdd·32 PaymentResult)은 **이번 범위 밖 — 건드리지 말 것**.

공통 원칙
- "목 데이터를 실데이터로 바꿀 수 있으면 바꾸고, 없으면 그 UI 요소를 제거한다." 자리를 비워 두거나 "준비 중" 문구로 대체하지 않는다(사용자 요청: 고정 문구 없애기).
- Ride 모델(domain/.../model/Ride.kt)에 있는 값: origin, destination, departureLabel, capacity, members(User: nickname, rideCount, isMe, verifiedLabel), farePerPerson, totalFare, status, estimatedFare, estimatedMinutes, driverId 등. 이 값으로 채울 수 있는 것만 채운다.
- 각 화면의 파라미터 기본값에 남은 목 값도 함께 삭제(Preview 는 명시적 더미를 넘기되 "서면역/김OO/12가 3456/6:57/여성만" 같은 문자열은 Preview 에서도 쓰지 않는다 — grep 검증 대상).
- KDoc·주석의 설명을 현재 상태에 맞춘다. 미사용 import/컴포저블/색상 정리.
- 빌드 명령: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:assembleDebug --console=plain -q`. **각 에이전트는 자기 묶음 작업 후 빌드 1회 통과시킬 것.** 다른 에이전트가 동시에 빌드할 수 있어 gradle 락 대기가 생기면 잠시 후 재시도.
- 에뮬레이터 설치는 리더가 한다.

## 묶음 A (compose-builder-A) — auth · home · mypage · NavGraph
파일: feature/auth/MannerPledgeScreen.kt, feature/home/HomeScreen.kt, feature/home/DestinationConfirmModal.kt, feature/home/DestinationConfirmRoute.kt(필요시), feature/mypage/MyPageScreen.kt, feature/mypage/RideCompleteScreen.kt, core/MainNavGraph.kt (**NavGraph 는 A 만 수정**)
1. 12 매너 서약 (MannerPledgeScreen.kt:207-215 `pledgeItems`): 항목 추가 — `PledgeItem("운행 중 내 위치정보를 동승자·보호자와 공유하는 데 동의해요", "매칭된 운행 동안만 수집하고, 운행이 끝나면 공유를 멈춰요")`. 첫 번째 이용약관 항목 바로 뒤에 둔다. 체크 배열은 pledgeItems.size 로 이미 동적. 백엔드에 동의 저장 필드가 없으므로 전송은 하지 않는다(보고서에 명시). 140행 "동의한 내용과 노쇼 차감 고지는 계정에 기록돼요" 는 백엔드가 기록하지 않으므로 "동의 내용은 가입 시점 기준으로 적용돼요" 로 바꾼다. 215행 노쇼 항목의 "등록한 결제 수단에서 청구돼요" 는 결제 수단이 없으니 "이용이 제한될 수 있어요" 로.
2. 14 홈 인사말 (HomeScreen.kt:249 근처): "{이름}님, 좋은 저녁이에요" → 시간대 무관 "{이름}님, 반가워요" / 이름 없으면 "반가워요".
3. 16 도착지 확인 모달 (DestinationConfirmModal.kt:136,374 "도보 2분 · 180m"): 실제 값이 없으므로 해당 텍스트 요소 제거. 파라미터가 있으면 함께 제거하고 DestinationConfirmRoute 호출부 정리.
4. 35 마이 버전 (MyPageScreen.kt:84,248 "v1.0.0"): `LocalContext.current.packageManager.getPackageInfo(packageName, 0).versionName` 으로 "v{versionName}" 표시(try/catch, 실패 시 텍스트 숨김). versionLabel 파라미터 제거.
5. 33 도착 완료 (RideCompleteScreen.kt): 
   - 평가 섹션(143행 "동승자 평가" 블록: 좋았어요/아쉬웠어요, 태그 칩) 과 「평가 보내기」 버튼 제거. onSubmit 파라미터 제거하고 `onDone: () -> Unit` 하나로. 버튼 문구 "홈으로".
   - 목 데이터(75-81행: "서면역 1번 출구 · 오후 6:57", 3,600원/6,600원/2명, "김OO · 부산대 인증 · 탑승 12회") 제거. NAV:753-758 에서 `activeRide`(또는 마지막 완료 방)를 넘길 수 있으면 origin→destination, members 수, farePerPerson 을 실값으로; 넘길 수 없으면 해당 요소 제거. "아낀 돈" 은 계산 근거가 없으면 제거.
   - NAV:755 `onSubmit = { _, _ -> resetTo(HOME) }` → `onDone = { resetTo(HOME) }`.
6. KDoc 갱신, 빌드, `_workspace/104_compose-builder_A.md` 기록.

## 묶음 B (compose-builder-B) — matching · explore
파일: feature/explore/JoinConfirmScreen.kt, feature/explore/JoinConfirmRoute.kt, feature/matching/RideDetailScreen.kt, feature/matching/RideDetailRoute.kt, feature/matching/PartnerProfileScreen.kt. **MainNavGraph.kt 수정 금지** (필요하면 보고서에 "NAV 변경 필요" 로 적어 리더에게).
1. 20 합류 확인 (JoinConfirmScreen.kt): "여성만" 배지(108 기본값 femaleOnly=true, 223-224) 제거 — 서버가 성별 조건을 주지 않으므로 요소 삭제. 탑승 "오후 6:45"/"오후 6:57 도착"(111,117,344,376) 제거 — Ride.departureLabel 이 있으면 출발 표기는 그 값으로, 도착 예정은 estimatedMinutes 가 있으면 "약 N분" 으로, 없으면 요소 제거. "서비스 수수료 (2차) 600원"·"수수료 포함 10원 단위로 나눠요"(110,419,450) 제거 — 1인 부담 = farePerPerson 만 표시. 방패 아이콘(158) 무동작이면 제거. JoinConfirmRoute 호출부 정리.
2. 22 탑승 상세 (RideDetailScreen.kt:115,118,119,159,219,273,292,317): 20 과 동일 규칙으로 여성만·6:57·수수료·방패 제거. RideDetailRoute 호출부 정리.
3. 23 동승자 프로필 (PartnerProfileScreen.kt): 지표 카드에서 매너 점수(167)·노쇼(181) 칸 제거 → 탑승 횟수 1칸만. 후기 태그 집계 섹션(227-235) 전체 제거. "아직 확인된 인증 정보가 없어요"(199-202) 는 인증 API 가 없으므로 섹션 자체 제거. 관련 파라미터(mannerScore, noShowCount, reviewTags, verifications 등) 제거. "채팅으로 물어보기"(284-291)는 그대로 둠. KDoc 갱신.
4. 빌드, `_workspace/105_compose-builder_B.md` 기록.

## 묶음 C (compose-builder-C) — chat
파일: feature/chat/ChatScreen.kt, feature/chat/RideOngoingScreen.kt, feature/chat/RideOngoingRoute.kt, feature/chat/EmergencyScreen.kt. **MainNavGraph.kt 수정 금지** (27 의 rideSummary 전달이 필요하면 보고서에 "NAV 변경 필요: ..." 로 적어 리더에게 — 리더가 넣는다).
1. 24 채팅 (ChatScreen.kt:305-341 배너 "실시간 위치 공유 중 · 동승자에게 내 위치가 보여요" + 켜진 토글): 백엔드 위치 공유 API 가 연결돼 있지 않으므로 배너 전체 제거. 359행 시스템 칩 "매칭 완료 · 오후 6:38" 제거(방 생성 시각이 모델에 없음). 24b 공유 시트(457-471)는 그대로.
2. 26 운행 중 (RideOngoingScreen.kt): "서면역까지 8분 남음"/"오후 6:57 도착 예정 · 위치가 실시간으로 반영돼요"(102-103) → Ride.destination 과 estimatedMinutes 가 있으면 "{destination}까지 약 N분" 하나만, 없으면 "{destination}으로 이동 중". 경유 순서 블록(205-209 "부산대 정문 · 탑승 완료 6:45" 등) → 시각 없이 origin/destination 만 표시하거나 블록 제거. "내린 뒤 현장에서 1/N 정산" 은 유지해도 됨(정책 문구). 보호자 공유 구역(104,118,229,251-254 "어머니 · 010-••••-1234", 토글) 전체 제거. RideOngoingRoute 에서 ride 값을 넘기도록 정리(195-206).
3. 27 긴급 신고 (EmergencyScreen.kt:159,218 "부산대 정문 → 서면역 · 12가 3456"): rideSummary 파라미터를 nullable 로 바꾸고 null 이면 "지금 타고 있는 차" 카드 자체를 숨긴다. 번호판은 모델에 없으니 표기 제거. NAV 에서 `activeRide?.let { "${it.origin} → ${it.destination}" }` 를 넘겨야 하므로 보고서에 NAV 변경 필요로 기재.
4. 빌드, `_workspace/106_compose-builder_C.md` 기록.

## 리더 사후 작업
- B/C 보고서의 "NAV 변경 필요" 반영, 최종 빌드, grep 검증(서면역|김OO|12가 3456|6:57|6:45|6:38|여성만|어머니|010-••••|도보 2분|좋은 저녁|v1.0.0 가 presentation/src/main 에 0건), 에뮬레이터 설치·스크린샷.
