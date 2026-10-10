# 98 입력 — 마이페이지(35) 정리: 탑승 횟수만 남기고 2차 항목 제거

작성: 2026-10-08 (브랜치 refactor/remove-unused-screens, 카카오 제거 변경 3파일이 미커밋 상태로 있음 — 건드리지 말 것)

## 사용자 요청 (원문 요지)
- 마이페이지의 안심 설정 · 결제 수단 · 매너 점수 · 마일리지 모두 제거
- 상단 요약에는 **탑승 횟수만** (몇 번 탔는지) 표시
- 설정 목록에서 안심 설정, 결제 수단 행 제거

## 현재 코드 (presentation/.../feature/mypage/MyPageScreen.kt, 488줄)
- 파라미터: mannerScoreLabel="98%", rideCountLabel="42회", mileageLabel="0P", rideHistoryValue="42회 · 지난 탑승 보기", paymentValue="카카오페이" — 전부 더미, NavGraph(MainNavGraph.kt:776)에서 넘기지 않음
- 171~182행: 매너 점수 | 탑승 | 마일리지 3칸 StatCell Row
- 194~207행: 안심 설정 SettingRow + 결제 수단 SettingRow (secondPhase=true, 무동작)
- ShieldIcon(383), CardIcon(404) 은 그 두 행에서만 사용
- KDoc 61~75행이 항목 목록을 설명

## 탑승 횟수 데이터 출처 (리더 조사 결과)
- 백엔드 `GET /users/info` 는 uuid·name 만 준다 → 내 rideCount 를 주는 API 는 **없다**
- 유일한 출처: 진행 중 방 상세 `members[]` 의 내 항목. 프론트에서는 `activePartyViewModel.ride` (StateFlow<Ride?>) → `Ride.members: List<User>` → `User.isMe && User.rideCount`
- 서버 rideCount 는 FINISHED 파티 수라 신규 가입자는 0 (MemberDisplay.kt:42 참고). 0 은 「첫 탑승」으로 표기하는 규약이 있음 (MemberDisplay.kt:44 `User.rideCountLabel`)

## 구현 방침
1. MyPageScreen 파라미터를 `rideCount: Int?` 로 바꾼다 (null = 아직 알 수 없음). mannerScoreLabel/mileageLabel/paymentValue/rideHistoryValue 파라미터 삭제.
2. 상단 요약 카드는 탑승 횟수 1칸만: 값은 `rideCount == null → "—"` (라벨 "탑승 횟수 · 집계 전"), `0 → "첫 탑승"`, `n → "n회"`. 카드 전체 폭을 쓰므로 StatCell 중앙 정렬 유지, 구분선(Box) 제거.
3. 설정 목록: 안심 설정·결제 수단 행 + 뒤따르는 SettingDivider 제거. 탑승 기록 행의 value 는 "지난 탑승 보기" 로 (더미 "42회" 제거). 알림 설정·고객센터 행은 유지.
4. ShieldIcon·CardIcon 과 관련 미사용 import/색상 제거.
5. MainNavGraph.kt:776 `MyPageScreen(` 호출에 `rideCount = activeRide?.members?.firstOrNull { it.isMe }?.rideCount` 전달. (activeRide 는 153행 `val activeRide by activePartyViewModel.ride.collectAsState()`)
   - MY_PAGE composable 에도 `LaunchedEffect(Unit) { activePartyViewModel.refresh() }` 가 있는지 확인, 없으면 MY_RIDES(760행)와 같은 방식으로 추가.
6. KDoc 과 프리뷰 갱신. 프리뷰는 rideCount=12 와 null 두 개면 좋다.

## 후속(이번 범위 밖, 보고에 명시)
- 백엔드 `/users/info` 응답에 rideCount 추가 요청 → 방이 없을 때도 표시 가능
