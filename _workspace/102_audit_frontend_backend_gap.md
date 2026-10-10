# 프론트 ↔ 백엔드 괴리 전수 조사 (2026-10-08)

- 프론트: refactor/remove-unused-screens (= origin/develop 79ad602 + 미커밋 변경)
- 백엔드: develop 1a75f82
- 조사 주체: Explore 에이전트 (읽기 전용). 리더 검수 메모는 맨 아래.
- 경로 표기: `P` = presentation/src/main/kotlin/com/moyeota/presentation/, `NAV` = P/core/MainNavGraph.kt
- 판정: 화면 기본값이 NAV/Route 호출부에서 덮어써지지 않아 **실제 화면에 뜨는 것만** 목 데이터로 분류. Preview 전용 더미는 제외.
- 05~09 부가 인증 화면은 NAV 미등록이라 사용자가 볼 수 없어 제외.

## 표 1. 화면별 괴리

| 화면 | 화면 요소(문구 그대로) | 유형 | 근거(파일:라인) | 필요한 백엔드 API(제안) | 비고 |
|---|---|---|---|---|---|
| 28 최종 요금 확인 [S19a] | 미터기 요금 10,200원 / 탑승 전 예상 요금 9,600원 / "기사 김OO · 부산 12가 3456" / "오후 6:58 입력 · 도착 확인 완료" | 목 데이터 | P/feature/payment/FareFinalScreen.kt:63-64,149,155 · NAV:719-724(인자 없음) | GET /matching/rooms/{id}/fare | 운행 FINISHED 시 26에서 자동 진입(NAV:697-703) → 실사용자가 반드시 본다 |
| 28 | "영수증" 링크, "금액이 다르면 «영수증»에서 이의 신청" | 무동작 + 백엔드 미지원 | FareFinalScreen.kt:162-167, :205 | 영수증 조회, 요금 이의 신청 | |
| 28 | "확인했어요 · 정산으로" | 백엔드 미지원 | FareFinalScreen.kt:196-200 → NAV:722 | 요금 확정 | 서버 호출 없이 화면만 넘어감 |
| 29 정산 [S19] | 총 요금 10,200원 · 수수료 600원 · 3명 · "카카오페이 / 신한 ••••1234" · "3,600원 결제하기" | 목 데이터 + 백엔드 미지원 | P/feature/payment/SettlementScreen.kt:70-75,205-208 · NAV:725-731 | 정산/결제 API 일체 | 결제 버튼은 32로 이동만 |
| 29 | "인원이 줄면 차액은 자동으로 돌려드려요" / "영수증은 앱에 30일 동안 남아 있어요" | 백엔드 미지원 | SettlementScreen.kt:192-193 | 환불, 영수증 보관 | |
| 30 결제 수단 [S20] | "카카오페이 신한 ••••1234 / 토스페이 국민 ••••8821 / 신용·체크카드 현대 ••••4402" | 목 데이터 | P/feature/payment/PaymentMethodsScreen.kt:73-77,81 · NAV:732-738 | 결제수단 목록 | |
| 30 | "기본 수단으로 저장" | 백엔드 미지원 | PaymentMethodsScreen.kt:202-203 → NAV:736 `{ back() }` | 기본 결제수단 설정 | 저장 없이 뒤로만 |
| 31 결제 수단 추가 | 카카오페이/토스페이/카드번호·유효기간·CVC, "추가하기" | 백엔드 미지원 | P/feature/payment/PaymentAddScreen.kt:67-69,269-273 → NAV:739-744 | PG 연동 등록 | 카드번호를 받지만 저장·전송 없음 |
| 32 결제 결과 [S21] | 10,200원 / 600원 / 3명 / "카카오페이" / "7월 24일 오후 6:58" | 목 데이터 | P/feature/payment/PaymentResultScreen.kt:57-61 · NAV:745-750 | 결제 결과·영수증 | |
| 32 | "영수증 자세히 보기", "공유" | 무동작 | PaymentResultScreen.kt:149, :187 | 영수증 상세 | |
| 33 도착 완료·평가 [S18] | "서면역 1번 출구 · 오후 6:57", 내가 낸 돈 3,600원 / 아낀 돈 6,600원 / 함께 탄 사람 2명 / "김OO · 부산대 인증 · 탑승 12회" | 목 데이터 | P/feature/mypage/RideCompleteScreen.kt:75-81 · NAV:753-758 | 운행 요약 | |
| 33 | "좋았어요/아쉬웠어요" + 태그, "평가 보내기" | 백엔드 미지원 | RideCompleteScreen.kt:82-83,223-224 → NAV:755 `onSubmit = { _, _ -> resetTo(HOME) }` | POST /parties/{id}/reviews | 입력값을 버림 |
| 33 | 동승자 이름 행 | 무동작 | RideCompleteScreen.kt:152 | — | |
| 26 운행 중 [S15] | "서면역까지 8분 남음" / "오후 6:57 도착 예정 · 위치가 실시간으로 반영돼요" | 목 데이터 | P/feature/chat/RideOngoingScreen.kt:102-103 · RideOngoingRoute.kt:195-206(안 넘김) | ETA(방 상세 estimatedMinutes 로 계산 가능) | |
| 26 | 경유 순서 "부산대 정문 · 탑승 완료 6:45" / "서면역 1번 출구로 이동 중 6:57" / "내린 뒤 현장에서 1/N 정산" | 목 데이터 | RideOngoingScreen.kt:205-209 | 탑승 시각(board) | 모든 방에 같은 문구 |
| 26 | "보호자에게 실시간 공유 중" + "어머니 · 010-••••-1234 · 도착하면 자동으로 알려드려요" + 토글 | 목 데이터 + 백엔드 미지원 | RideOngoingScreen.kt:104,118,229,251-254 | 보호자 등록·공유·도착 알림 | |
| 26 | 동승자 위치 마커(5초 보고·조회) | 백엔드 미지원(잘못된 경로) | RideOngoingRoute.kt:117-129 | 백엔드의 /chat-rooms/{id}/location* + STOMP /pub/chat-rooms/{id}/location 으로 이동 필요 | 404 를 runCatching 이 삼켜 늘 빈 목록 |
| 27 긴급 신고 [S17] | "지금 타고 있는 차" 아래 "부산대 정문 → 서면역 · 12가 3456" | 목 데이터 | P/feature/chat/EmergencyScreen.kt:159,218 · NAV:710-715(rideSummary 안 넘김) | 방 상세 + /matching/rooms/{id}/driver 로 채울 수 있음 | 신고 저장(POST /reports, PATCH /reports/call-result)은 실제 연결됨 |
| 24 채팅 [S16] | 배너 "실시간 위치 공유 중 · 동승자에게 내 위치가 보여요" + 켜진 토글 | 목 데이터 | P/feature/chat/ChatScreen.kt:305-341 (`TogglePill(on = true)`) | POST/DELETE /chat-rooms/{id}/location/sharing (있음, 미연결) | 끝난 방에서도 "공유 중" |
| 24 | 시스템 칩 "매칭 완료 · 오후 6:38" | 목 데이터 | ChatScreen.kt:359 | 방 생성 시각 | 모든 방 공통 |
| 24b 공유 시트 | "실시간 위치 공유 시작" | 백엔드 있음, 프론트 미연결 | ChatScreen.kt:457-471 → NAV:638,668(26 이동만) | POST /chat-rooms/{id}/location/sharing 호출 필요 | |
| 23 동승자 프로필 [S13] | "신고" | 무동작 | P/feature/matching/PartnerProfileScreen.kt:83,102-106 · NAV:598-602 | 사용자 신고 API(지금은 메시지 신고만) | |
| 23 | 매너 점수 "평가 준비 중" / 노쇼 "집계 전" / 인증·후기 없음 안내 | 백엔드 미지원(정직 표기) | PartnerProfileScreen.kt:166,180,202,233 | 매너점수·노쇼·인증·후기 | |
| 23 | "채팅으로 물어보기" | 동작이 문구와 다름 | PartnerProfileScreen.kt:284-291 → NAV:601(채팅 탭 목록) | 1:1 채팅방 생성 | |
| 20 합류 확인 | "여성만" 배지 | 목 데이터 | P/feature/explore/JoinConfirmScreen.kt:108(기본 femaleOnly=true), :223-224 · JoinConfirmRoute.kt:131-139(안 넘김) | — | 남성에게도 "여성만" |
| 20 | 탑승 "오후 6:45" / "오후 6:57 도착" | 목 데이터 | JoinConfirmScreen.kt:111,117,344,376 | 출발 예정·ETA | |
| 20 | "서비스 수수료 (2차) 600원", "수수료 포함 10원 단위로 나눠요" | 목 데이터 + 백엔드 미지원 | JoinConfirmScreen.kt:110,419,450 | 수수료 정책 | 1인 부담 = estimateFare/capacity(data PartyMappers.kt:41-42), 수수료 미포함 |
| 20 | 우측 상단 방패 아이콘 | 무동작 | JoinConfirmScreen.kt:158 | — | |
| 22 탑승 상세 [S12] | "여성만" | 목 데이터 | P/feature/matching/RideDetailScreen.kt:115,219 · RideDetailRoute.kt:120-126 | — | 20 과 동일 |
| 22 | "오후 6:57 도착" | 목 데이터 | RideDetailScreen.kt:118,273 | ETA | |
| 22 | "서비스 수수료 (2차) 600원", "수수료 포함 · 10원 단위로 나눠요" | 목 데이터 | RideDetailScreen.kt:119,292,317 | 수수료 정책 | |
| 22 | 방패 아이콘 | 무동작 | RideDetailScreen.kt:159 | — | |
| 25 배차 상태 [S14] | 기사 전화 아이콘 | 무동작 | P/feature/matching/DispatchStatusScreen.kt:232-238 | 기사 연락처·안심번호 | |
| 25 | "기사 정보 준비 중" | 백엔드 미지원(정직 표기) | DispatchStatusScreen.kt:224-226 | DriverSummary 에 기사명·별점 | |
| 25 | partyId 없을 때 더미 방 "부산대학교 정문 → 서면역 1번 출구 / 부산불곰·해운대곰돌" | 목 데이터(예외 경로) | DispatchStatusRoute.kt:215 · DispatchStatusScreen.kt:64-73,101 | — | 정상 경로에선 거의 안 나옴 |
| 16 도착지 확인 모달 | "도보 2분 · 180m" | 목 데이터 | P/feature/home/DestinationConfirmModal.kt:136,374 · DestinationConfirmRoute.kt:298-330(안 넘김) | 승차지점 도보 거리 | |
| 16 | 매칭 방식 "주요 승차지점" 칩 | 무동작(의도된 고정) | DestinationConfirmModal.kt:455-461 | — | |
| 15 목적지 [S10] | "최근 검색" 4건 | 목 데이터 + 백엔드 미지원 | (조사 시점) DestinationScreen.kt:118-123 | GET/DELETE /users/me/search-histories (feature 브랜치, 미머지) | **리더: 2026-10-08 제거 완료** |
| 15/16 | 위치 권한 없을 때 출발지 "부산대학교 정문"(고정 좌표) | 목 데이터(폴백) | DestinationRoute.kt:28-33,71 | — | 가짜 좌표로 실제 방이 만들어질 수 있음 |
| 14 홈 [S09] | "{이름}님, 좋은 저녁이에요" | 목 데이터 | P/feature/home/HomeScreen.kt:249 | — | 시간대 무관 항상 "저녁" |
| 14 | 자주 가는 곳 / 최근 목적지 / 「전체」 | 완료 | 미커밋 diff HomeScreen.kt | — | **즐겨찾기 API 로 교체 완료** |
| 35 마이 [S24] | "알림 설정"(2차) | 무동작 + 백엔드 미지원 | P/feature/mypage/MyPageScreen.kt:206-211 | 알림 설정(지금은 채팅방 mute 만) | |
| 35 | "고객센터 · 신고 내역" | 무동작 + 백엔드 미지원 | MyPageScreen.kt:214-219 | GET /reports/me 등 | |
| 35 | "탈퇴하기" | 무동작 + 백엔드 미지원 | MyPageScreen.kt:240-245 | DELETE /users/me | |
| 35 | 프로필 카드(›) | 무동작 | MyPageScreen.kt:111,162 | 프로필 수정 화면 + PATCH /users/me(있음, 미연결) | |
| 35 | "인증 정보 준비 중" | 백엔드 미지원(정직 표기) | MyPageScreen.kt:154 | 학교/재직 인증 | |
| 35 | 탑승 횟수 "—"(진행 중 방 없을 때) | 백엔드 미지원 | MyPageScreen.kt:176-182 · NAV:780-781 | GET /users/info 에 rideCount 추가 | |
| 35 | "탑승 기록 · 지난 탑승 보기" | 백엔드 미지원(순환 링크) | MyPageScreen.kt:197-202 → 34 → MyRidesScreen.kt:229-241 → 35 | GET /users/me/parties?status=FINISHED | 지난 기록 화면이 없다 |
| 35 | "v1.0.0" | 목 데이터 | MyPageScreen.kt:84,248 | — | BuildConfig 값 아님 |
| 35 | 안심 설정 · 결제 수단 · 매너 점수 · 탑승 42회 · 마일리지 | 완료 | 미커밋 diff MyPageScreen.kt | — | **제거 완료** |
| 34 내 탑승 [S22] | "예정" 세그먼트(항상 0건) / 필터 아이콘 | 백엔드 미지원 / 무동작 | MyRidesScreen.kt:148-150,153 · NAV:765 | 예약 탑승 개념 | |
| 11 안심 설정 [S06] | 토글 2개 / 보호자 "어머니 · 010-••••-1234" / "변경" / "한 명 더 등록하기" / "설정 저장하고 계속" | 목 데이터 + 무동작 + 백엔드 미지원 | P/feature/auth/SafetySettingsScreen.kt:60,108-125,150-169,194-195 · NAV:418-423(값 버림) | 안심설정·보호자 API | 가입 필수 경로 — 모든 신규 가입자가 본다 |
| 12 매너 서약 [S07] | "동의한 내용과 노쇼 차감 고지는 계정에 기록돼요" | 백엔드 미지원 | P/feature/auth/MannerPledgeScreen.kt:140 · UserRegisterRequest 에 동의 필드 없음 | 약관 동의 이력 | |
| 12 | 노쇼 요금 차감·결제 수단 청구·미정산 매칭 제한 문구 | 백엔드 미지원 | MannerPledgeScreen.kt:213,215 | 노쇼 패널티·결제 | |
| 12 | 서약 항목 탭(정책 상세) | 무동작 | MannerPledgeScreen.kt:227-231 | — | |
| 13 가입 완료 [S08] | 쿠폰 배너 "첫 탑승 3,000원 지원 · 30일 안에 사용" | 목 데이터 + 백엔드 미지원 | P/feature/auth/SignupCompleteScreen.kt:49,78-95 · NAV:437-440 | 쿠폰 API | 모든 가입자에게 뜸 |
| 10 프로필 만들기 [S05] | 아바타 색 선택 | 무동작(저장 안 됨) | P/feature/auth/ProfileSetupScreen.kt:101,221-234 | — | |
| 10 | "닉네임은 나중에 바꿀 수 있어요" | 프론트 미연결 | ProfileSetupScreen.kt:454 · PATCH /users/me 있으나 updateProfile 호출처 없음 | (이미 있음) | |
| 10 | 이메일 "비밀번호를 잊었을 때 쓰는 주소예요" / "매너 기록으로 서로를 확인" | 백엔드 미지원 | ProfileSetupScreen.kt:399,420 | 비밀번호 재설정, 매너 기록 | |
| 04 시작 [S01] | "이용약관 · 개인정보 처리방침" 링크 | 무동작 | P/feature/auth/LoginScreen.kt:171-182 | 약관 페이지 | |
| 04 | "운행 중 위치를 지인과 공유할 수 있어요" | 백엔드 미지원 | LoginScreen.kt:120 | 보호자 공유 | |
| 04 | 카카오 로그인 | 완료 | 미커밋 diff | — | **제거 완료** |
| 02 온보딩 | "인증을 마친 이용자만 매칭되고" | 백엔드 미지원 | P/feature/onboarding/OnboardingTrustScreen.kt:106 | 학교/재직 인증 | |
| 03 온보딩 | "1/N 정산도 자동으로 끝나요" | 백엔드 미지원 | P/feature/onboarding/OnboardingSafetyScreen.kt:105 | 결제/정산 | |

## 표 2. 프론트가 호출하지만 백엔드 develop 에 없는 엔드포인트

| 프론트 호출 | 선언 | 실제 사용 | 영향 |
|---|---|---|---|
| POST api/v1/matching/rooms/{partyId}/location | data/.../remote/MatchingApi.kt:121 | 26 화면 5초마다(RideOngoingRoute.kt:121) | 매번 404, 조용히 무시 |
| GET api/v1/matching/rooms/{partyId}/locations | MatchingApi.kt:128 | 26 화면 5초마다(RideOngoingRoute.kt:124) | 동승자 마커가 한 번도 안 뜸 |
| POST api/v1/chat-rooms | data/.../remote/ChatApi.kt:39 | RemoteChatRepository.createChatRoom — presentation 호출처 없음 | 죽은 코드 |
| DELETE api/v1/chat-rooms/{chatRoomId} | ChatApi.kt:43 | closeChatRoom — presentation 호출처 없음 | 죽은 코드 |

나머지 Retrofit 37개 + SSE /matching/rooms/{id}/events + STOMP(/ws-chat, /pub/chat-rooms/{id}/messages, /read)는 백엔드와 일치.

**반대 방향(백엔드에 있으나 앱이 안 쓰는 것)**: POST/DELETE /chat-rooms/{id}/location/sharing, POST /chat-rooms/{id}/location, STOMP /pub/chat-rooms/{id}/location·/location/sync, POST /chat-rooms/{id}/reports, PATCH /users/me, POST /auth/phone/check, /drivers/**, /dispatch/** (기사 앱용; 승객 앱은 GET /dispatch/rides/{id} 만 사용)

## 부록. 백엔드 develop 엔드포인트 전체

- Auth: POST /auth/register · /auth/login · /auth/reissue · /auth/logout · /auth/phone/check · /auth/nickname/check
- LocalUser: POST /local/users · GET /local/users/info
- User: PATCH /users/me · PUT·DELETE /users/me/fcm-token
- FavoritePlace: POST·GET /users/me/favorite-places
- Place: GET /places · GET /places/reverse
- AppConfig: GET /config
- Party: POST /matching/rooms · POST /matching/rooms/{id}/join · DELETE /matching/leave/{id} · GET /matching/rooms/{id} · GET /matching/rooms/{id}/status · GET /matching/rooms(+bbox) · GET /matching/rooms/{id}/driver · POST /matching/rooms/{id}/finish · GET /matching/rooms/{id}/events(SSE)
- Route: POST /matching/routes
- Report: POST /reports · PATCH /reports/call-result
- Driver: POST /drivers/verify · /drivers/vehicle · /drivers/call · DELETE /drivers/call · GET /drivers/me · PUT·DELETE /drivers/fcm-token · POST /drivers
- DriverLocation: POST /dispatch/online · POST /dispatch/location · DELETE /dispatch/online
- Ride: POST /dispatch/rides/{id}/arrive · /board · /complete · GET /dispatch/rides/{id}
- DispatchCall: POST /dispatch/calls/{id}/accept · /reject · GET /dispatch/calls/{id}/status · GET /dispatch/calls/{id}
- ChatRoom: GET /chat-rooms/{id}
- ChatRoomUser: GET /chat-rooms/me · POST·DELETE /chat-rooms/{id}/users · POST /chat-rooms/{id}/users/read/{msgId} · GET /chat-rooms/{id}/users · POST·DELETE /chat-rooms/{id}/users/notification/mute
- ChatMessage: GET /chat-rooms/{id}/messages · GET …/messages/after · POST …/messages · DELETE …/messages/{msgId} · GET …/messages/search
- ChatReport: POST /chat-rooms/{id}/reports
- ChatLocation: POST·DELETE /chat-rooms/{id}/location/sharing · POST /chat-rooms/{id}/location
- STOMP(/ws-chat, prefix /pub): /chat-rooms/{id}/messages · /read · /location/sync · /location

(모든 REST 경로 앞에 /api/v1)

## 리더 검수 메모
- 15 최근 검색, 14 자주 가는 곳·최근 목적지, 35 2차 항목, 04 카카오 버튼은 조사 중 이미 처리됨(미커밋).
- 표 2 의 26 위치 공유 경로 오류는 조사 결과 중 가장 실질적인 버그(기능이 조용히 죽어 있음).

## 처리 현황 갱신 (2026-10-08 23:xx, 103 작업)
완료: 12 위치공유 동의 추가 · 11 안심 설정 화면 삭제(10→12 직결) · 13 쿠폰 배너 · 14 인사말 · 16 도보 거리 · 20/22 여성만·시각·수수료·방패 · 23 매너점수·노쇼·후기·인증 섹션 · 24 위치공유 배너·매칭시각 칩 · 26 남은시간·경유시각·보호자 · 27 차량 요약(실값, 번호판 제거) · 33 평가 섹션(요약은 실값) · 35 버전(실값) · 17 탐색 「여성만」 더미 배지.
미처리(범위 밖, 결정 대기): 28~32 결제·정산 흐름 전체 · 26 동승자 위치 공유 경로 오류(백엔드 채팅 위치 API 로 이전 필요) · 35 알림/고객센터/탈퇴/프로필 카드 무동작 · 34 예정 세그먼트 · 04 약관 링크 · 25 기사 전화.
