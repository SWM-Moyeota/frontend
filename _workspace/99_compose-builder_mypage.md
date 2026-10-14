# 99 compose-builder — 마이페이지(35) 정리

## 변경 파일
- presentation/src/main/kotlin/com/moyeota/presentation/feature/mypage/MyPageScreen.kt
- presentation/src/main/kotlin/com/moyeota/presentation/core/MainNavGraph.kt (MYPAGE composable만, 기존 카카오 주석 변경 유지)

## 제거
- 파라미터: mannerScoreLabel, rideCountLabel, mileageLabel, rideHistoryValue, paymentValue → `rideCount: Int? = null` 로 대체
- 요약 카드: 매너 점수 · 마일리지 StatCell, 세로 구분선 Box 2개
- 설정 목록: 안심 설정 · 결제 수단 SettingRow + 뒤따르던 SettingDivider 2개
- ShieldIcon, CardIcon 컴포저블, `androidx.compose.ui.graphics.Path` import (그 외 import·색상은 남은 코드에서 사용 중)

## 남은 구조
프로필 카드 → 탑승 횟수 요약(1칸, 전체 폭 중앙) → 설정 목록[탑승 기록(value "지난 탑승 보기", → 34) · 알림 설정(2차, 무동작) · 고객센터 · 신고 내역(무동작)] → 로그아웃/탈퇴/버전 → 하단탭
KDoc 갱신, 프리뷰 2개(rideCount=12, rideCount=null)

## rideCount 표시 규칙
| rideCount | 값 | 라벨 |
|---|---|---|
| null | — | 탑승 횟수 · 집계 전 |
| 0 | 첫 탑승 | 탑승 횟수 |
| n | n회 | 탑승 횟수 |

NavGraph: `rideCount = activeRide?.members?.firstOrNull { it.isMe }?.rideCount`, MYPAGE 진입 시 `LaunchedEffect(Unit) { activePartyViewModel.refresh() }` 추가(기존에 없었음, MY_RIDES와 동일 방식).

## 빌드
`JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:assembleDebug --console=plain -q` 통과, 출력 없음(경고 0). 에뮬레이터 설치 안 함.

## 후속(범위 밖)
- 백엔드 `/users/info` 응답에 rideCount 추가 요청 → 진행 중 방이 없을 때도 표시 가능
