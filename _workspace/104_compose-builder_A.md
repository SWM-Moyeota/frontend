# 104 compose-builder-A — auth · home · mypage · NavGraph (103 묶음 A + 추가 7·8)

작성 2026-10-08. 기존 미커밋 변경 위에 추가만 함(stash/revert 없음).

## 변경 파일 (presentation/src/main/kotlin/com/moyeota/presentation/)
- feature/auth/MannerPledgeScreen.kt
- feature/auth/ProfileSetupScreen.kt (추가 7)
- feature/auth/SignupDraft.kt (추가 7, KDoc만)
- feature/auth/SafetySettingsScreen.kt — **삭제** (추가 7)
- feature/auth/SignupCompleteScreen.kt (추가 8)
- feature/home/HomeScreen.kt
- feature/home/DestinationConfirmModal.kt
- feature/mypage/MyPageScreen.kt
- feature/mypage/RideCompleteScreen.kt (전면 재작성)
- core/MainNavGraph.kt, core/Routes.kt
- DestinationConfirmRoute.kt 는 변경 불필요(walkLabel 을 넘기지 않고 있었음)

## 항목별
1. 12 매너 서약 — 위치 공유 동의 항목을 pledgeItems **맨 끝**에 추가(사용자 확정 문구: "운행 중 내 위치를 동승자와 공유하는 데 동의해요" / "매칭된 운행 동안에만 위치를 쓰고, 운행이 끝나면 공유도 함께 끝나요"). 백엔드에 동의 저장 필드가 없어 **서버로 전송하지 않음**(전체 체크의 일부로 CTA 활성 조건에만 쓰임). 안내 문구 → "동의 내용은 가입 시점 기준으로 적용돼요". 노쇼 항목 설명 → "매칭 확정 후 오지 않으면 이용이 제한될 수 있어요". 진행 표기 "3 / 3" → "2 / 2", KDoc 진입 "10 프로필 만들기". 참고: 노쇼 항목 제목 "무단 노쇼는 요금이 차감돼요" 는 지시대로 유지했으나 설명과 어긋남 — 리더 판단 필요.
2. 14 홈 인사말 — 실값 대체: "{이름}님, 반가워요" / 이름 없으면 "반가워요".
3. 16 도착지 확인 — 요소 제거: "도보 2분 · 180m" 텍스트와 walkLabel 파라미터 삭제(점선 연결선은 유지). destinationName/destinationAddress/originStopName 의 목 기본값(서면역 등)도 제거 → 필수 파라미터, Preview 는 "출발지/도착지" 더미.
4. 35 마이 버전 — 실값 대체: versionLabel 파라미터 삭제, packageManager.getPackageInfo(packageName,0).versionName 으로 "v{versionName}" (try/catch, 실패·null 이면 텍스트 숨김).
5. 33 도착 완료 — 평가 섹션(좋았어요/아쉬웠어요·태그)·「평가 보내기」·「다음에 할게요」 제거. 시그니처 `RideCompleteScreen(routeLabel: String?, paidAmount: Int?, companionCount: Int?, onDone: () -> Unit)`, CTA "홈으로". 목 데이터 제거 후 NAV 에서 activeRide 실값 전달: routeLabel = "origin → destination", paidAmount = farePerPerson(>0), companionCount = 나를 뺀 members 수(>0). null 인 값은 해당 줄/칸 숨김(둘 다 없으면 요약 카드 숨김). 「아낀 돈」 제거(계산 근거 없음). NAV onSubmit/onSkip → onDone = { resetTo(HOME) }.
6. KDoc 갱신 완료, 미사용 import·색상·컴포저블(SentimentButton, TagFlow, ThumbIcon) 정리.
7. 11 안심 설정 삭제 — SafetySettingsScreen.kt 파일, Routes.SAFETY_SETTINGS, NAV composable·import 제거. 10 「다음」 → Routes.MANNER_PLEDGE. ProfileSetupScreen "1 / 3"·progress 1/3 → "1 / 2"·1/2, KDoc "→ 11 안심 설정" → "→ 12 매너 서약", "10 → 11 → 12 세 단계" → "10 → 12 두 단계". SignupDraft KDoc "(1/3" → "(1/2". Routes 주석 "2단계: 10 → 12 → 13 완료".
8. 13 가입 완료 — 쿠폰 배너(첫 탑승 3,000원 지원) 컴포저블 블록·couponIssued 파라미터·미사용 import(shadow, RoundedCornerShape) 제거, 간격 30+32 → 40dp 로 정리. NAV 호출부에는 쿠폰 인자 없었음.

## 검증
- `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:assembleDebug` 통과(exit 0). 첫 시도는 다른 에이전트와 동시 빌드로 Kotlin 증분 캐시 파일 예외가 났고 재시도에서 통과.
- grep "좋은 저녁|도보 2분|180m|v1.0.0|서면역|김OO|6:57|평가 보내기|결제 수단에서 청구" — 대상 7파일 0건.
- grep "SAFETY_SETTINGS|SafetySettings" presentation app — 0건.
- grep "3,000원|쿠폰|지원금" SignupCompleteScreen.kt — 0건.

## 진입 경로
- 12: 04 → 10 프로필 만들기 「다음」 → 12 (11 경유 없음) → 13
- 14: 하단탭 홈 / 16: 14 → 15 목적지 선택 → 16 모달
- 33: 32 결제 결과 「확인」 → 33 → 「홈으로」 → 14
- 35: 하단탭 마이 (하단 우측 버전 표기)
