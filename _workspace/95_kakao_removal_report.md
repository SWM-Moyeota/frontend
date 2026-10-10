# 95 카카오 로그인 삭제 — 결과

작성: 2026-10-08

## 변경
- `presentation/.../feature/auth/LoginScreen.kt`
  - 「카카오로 3초 만에 시작」 버튼, 「준비 중」 안내 배너, `kakaoNoticeShown` 상태, `KakaoYellow`/`KakaoLabel` 색 삭제
  - `showRetryBanner` 파라미터(카카오 인증 실패 전용)와 `LoginScreenRetryBannerPreview` 삭제
  - 미사용 import 6개 정리(getValue/setValue/mutableStateOf/clip/NoticeBanner/NoticeKind), KDoc 갱신
- `presentation/.../core/MainNavGraph.kt` — 04 로그인 주석 1줄 갱신

## 검증
- `./gradlew :app:assembleDebug :data:testDebugUnitTest` (JDK 21) — BUILD SUCCESSFUL
- emulator-5554 설치 → 온보딩 건너뛰기 → 04 로그인: 카카오 버튼 없음, 레이아웃 정상
  - 스크린샷: `_workspace/screenshots/94_login_no_kakao.png`

## 비고
- 작업 트리에 이전 세션의 미커밋 변경(회원가입 nickname 연동: data/domain/ProfileSetupScreen/SignupDraft 등)이 함께 있음 — 건드리지 않음
- 백엔드에 없는 다른 기능(결제 28~32, 미연결 인증 05~09, 채팅방 생성/삭제 API, 33 평가, 34 내 탑승, 11 안심 설정, 마이페이지 2차 메뉴)은 이번 범위 밖 — 94_input 참고
