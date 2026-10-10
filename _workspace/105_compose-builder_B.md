# 105 compose-builder-B — matching · explore 목 문구 제거

작성: 2026-10-08. 입력: 103_input_mock_cleanup.md 묶음 B. 근거: 102_audit_frontend_backend_gap.md.

## 변경 파일
- presentation/.../feature/explore/JoinConfirmScreen.kt
- presentation/.../feature/explore/JoinConfirmRoute.kt
- presentation/.../feature/matching/RideDetailScreen.kt
- presentation/.../feature/matching/RideDetailRoute.kt
- presentation/.../feature/matching/PartnerProfileScreen.kt

## 항목별 처리
### 1. 20 합류 확인 (JoinConfirmScreen / Route)
- "여성만" 배지: 요소 제거, femaleOnly 파라미터 제거.
- 탑승·도착 시각: 하드코딩한 시각 제거. 출발 쪽은 Ride.departureLabel 이 비어 있지 않을 때만 표시하고(서버 매퍼는 현재 "" 라 실제로는 표시되지 않음), 도착 쪽은 estimatedMinutes 가 있으면 "약 N분", 없으면 표시하지 않음. pickupTimeLabel/arrivalTimeLabel 파라미터 제거.
- 서비스 요금 행·"10원 단위" 안내: 제거. 1인 부담은 ride.farePerPerson 을 그대로 표시. 보조 문구는 "총 예상 요금을 정원 N명으로 나눈 금액이에요"(매퍼 estimateFare/capacity 와 일치). "정산 방식 10원 단위" 행도 근거가 없어 제거. serviceFee 파라미터 제거.
- 방패 아이콘(동작 없음): 요소와 ShieldIcon 컴포저블 제거.
- 목 기본값 DefaultJoinRide 와 FirstJoinedMember 삭제. ride 는 필수 파라미터. Preview 는 중립 더미("출발지/도착지")를 씀.
- Route: partyId == null 일 때 더미 화면 대신 BackStateScaffold + ErrorBox("동승 정보를 불러오지 못했어요", 재시도=닫기)를 띄움.
- 미사용 import(Path, StrokeJoin, drawscope.Stroke) 정리.

### 2. 22 탑승 상세 (RideDetailScreen / Route)
- 20 과 같은 규칙: genderLabel 배지 제거, arrivalLabel 대신 "약 N분"(estimatedMinutes 가 있을 때만), departureLabel 은 비어 있지 않을 때만, 서비스 요금 행·정산 방식 행·안내 문구 제거, 방패 아이콘 제거.
- 1인 부담: 클라이언트 재계산 `(totalFare + serviceFee) / members.size / 10 * 10` 을 없애고 ride.farePerPerson 을 그대로 씀(20 과 같은 값이 보이도록).
- recruitingRideDummy 삭제, ride 는 필수. Route 의 partyId == null 처리는 20 과 같은 방식의 ErrorBox.
- 미사용 import(CornerRadius, Size, Path, StrokeJoin, Stroke)와 색상(GrayDeep, MapBg, MapBlock) 정리.

### 3. 23 동승자 프로필 (PartnerProfileScreen)
- 지표 카드: 매너 점수 칸과 노쇼 칸을 제거해 탑승 횟수 한 칸만 남김.
- 후기 태그 섹션: 통째로 제거.
- "확인된 정보" 섹션: 통째로 제거.
- 이름 아래 부제: 원래 verifiedLabelOrNone 으로 "인증 정보 없음" 고정 문구를 띄웠는데, 같은 이유로 verifiedLabel 이 비어 있지 않을 때만 표시하도록 바꿈.
- 파라미터 mannerPercent, noShowCount, verifiedItems, reviewTags 제거. NavGraph 호출부(user, onBack, onChatClick)는 이 파라미터들을 넘기지 않아 영향 없음.
- "채팅으로 물어보기"와 "신고"는 그대로 둠. KDoc 갱신.
- 미사용 import(HorizontalDivider, verifiedLabelOrNone)와 색상(GraySlate, GrayDeep, ChipBg) 정리.

## NAV 변경 필요
없음. MainNavGraph 호출부 시그니처(JoinConfirmRoute, RideDetailRoute, PartnerProfileScreen)는 바뀌지 않음.

## 검증
- `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:assembleDebug` 통과(exit 0). 이 다섯 파일에서 나오는 경고 없음.
- 다섯 파일에서 grep "여성만|6:45|6:57|수수료|매너 점수|노쇼|후기|인증 정보" 0건(주석 포함). "서면역|김OO|12가|도보 2분|부산대" 도 0건.

## 참고(범위 밖)
- 20 의 "합류하면 인원 N명으로 요금이 확정되고 채팅방에 들어가요" 문구는 손대지 않음. farePerPerson 은 정원 기준 분할이라 "참여 인원으로 확정"과 엄밀히는 맞지 않음. 리더 판단 필요.
