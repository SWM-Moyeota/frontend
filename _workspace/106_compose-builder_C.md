# 106 compose-builder-C — chat 목 문구 제거 (입력: 103 묶음 C)

## 변경 파일
- presentation/.../feature/chat/ChatScreen.kt
- presentation/.../feature/chat/RideOngoingScreen.kt
- presentation/.../feature/chat/RideOngoingRoute.kt
- presentation/.../feature/chat/EmergencyScreen.kt
(MainNavGraph.kt 미수정)

## 항목별
1. 24 채팅 — **요소 제거**
   - 「실시간 위치 공유 중 · 동승자에게 내 위치가 보여요」 배너 + 토글 전체 제거. TogglePill, BannerBg/BannerStrong/BannerSub 색 제거.
   - 시스템 칩 「매칭 완료 · 오후 6:38」 제거, SystemChipBg 제거.
   - ChatUiMessage.isLocationShare 와 위치 공유 말풍선 분기·LocationPinIcon 제거(실데이터에서 true 로 오는 경로 없음, 더미에서만 사용).
   - roomTitle/roomSubtitle/messages 기본값(서면역 동승·6:45·김OO 더미) 삭제 → 필수 파라미터. Preview 는 중립 더미(PreviewMessages)를 명시 전달.
   - onOpenRideOngoing 파라미터는 ChatRoute(내 범위 밖)·NAV 가 넘기고 있어 시그니처만 유지(@Suppress, KDoc 에 미사용 사유). 24b 공유 시트는 그대로.
2. 26 운행 중 — **실값 대체 + 요소 제거**
   - remainingLabel/arrivalLabel/guardianLabel 삭제 → originName/destinationName/estimatedMinutes(Ride.origin/destination/estimatedMinutes) 추가.
   - 시트 상단 한 줄: 「{destination}까지 약 N분」 / 소요 없음 「{destination}(으)로 이동 중」(받침 조사 처리) / 상세 미수신 「목적지로 이동 중」. 둘째 줄(도착 예정·실시간 반영) 제거.
   - 경유 순서: 시각 제거, 「{origin} · 탑승 완료」→「{destination}(으)로 이동 중」→「내린 뒤 현장에서 1/N 정산」. origin/destination 없으면 카드 숨김. RouteStepRow 의 time 파라미터 제거.
   - 보호자 공유 카드(어머니 · 010-••••-1234, 토글, 로컬 상태) 전체 제거.
   - RideOngoingRoute: ride?.origin / ride?.destination / ride?.estimatedMinutes 전달.
3. 27 긴급 신고 — **실값 대체(NAV 반영 시) / 없으면 요소 숨김**
   - EmergencyRoute·EmergencyScreen 의 rideSummary: String? = null. null 이면 「지금 타고 있는 차」 카드 숨김. 번호판 표기 제거.

## NAV 변경 필요 (리더 반영)
- presentation/.../core/MainNavGraph.kt:703-708 `EmergencyRoute(` 호출에 인자 추가:
```kotlin
            EmergencyRoute(
                repository = rideRepository,
                partyId = activePartyId ?: selectedPartyId ?: createdPartyId,
                rideSummary = activeRide?.let { "${it.origin} → ${it.destination}" },
                onBack = ::back,
                onReportSubmitted = ::back,
            )
```
- (선택) ChatScreen.onOpenRideOngoing 은 더 이상 쓰이지 않는다. 정리하려면 ChatRoute.kt:674,704,753,839,866,894 와 NAV:637,667 의 onOpenRideOngoing 전달을 함께 지우고 ChatScreen 파라미터 삭제.

## 검증
- 4개 파일 grep "8분 남음|6:57|6:45|6:38|어머니|010-••••|실시간 위치 공유 중|12가 3456|서면역|부산대 정문|김OO" → 0건.
- `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:assembleDebug` 통과 (첫 시도는 동시 빌드로 mergeLibDexDebug 캐시 저장 실패, 재시도 통과).

## 진입 경로
- 24: 하단탭 채팅 → 방 선택. 26: 25 배차 상태 → 운행 중(또는 24 공유 시트 「실시간 위치 공유 시작」). 27: 26 「신고」.
