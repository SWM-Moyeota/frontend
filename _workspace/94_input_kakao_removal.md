# 00 입력 정리 — 카카오 로그인 삭제

작성: 2026-10-08

## 요청
- 백엔드(~/Documents/GitHub/Backend)에 소셜 로그인 엔드포인트가 없으므로 프론트의 카카오 로그인 부분 삭제
- (전체 대조 결과 결제 28~32 · 미연결 인증 05~09 · 채팅방 생성/삭제 API 등도 백엔드에 없으나, 이번 요청 범위는 카카오 로그인만)

## 근거
- 백엔드 AuthController: register / login / reissue / logout / phone·nickname check 뿐. OAuth·Kakao 로그인 없음
- 백엔드의 Kakao 참조는 place/search KakaoPlaceClient(장소 검색)뿐 → 로그인과 무관
- 프론트 카카오 SDK 의존성·매니페스트 설정 없음 — 삭제 대상은 LoginScreen UI 뿐

## 변경 범위
- presentation/.../feature/auth/LoginScreen.kt
  - KakaoYellow/KakaoLabel 색, kakaoNoticeShown 상태, 「카카오로 3초 만에 시작」 버튼, 「준비 중」 안내 배너 삭제
  - showRetryBanner 파라미터(카카오 인증 취소/실패 전용) 및 해당 프리뷰 삭제
  - 불필요 import 정리, KDoc 갱신
- presentation/.../core/MainNavGraph.kt — 04 로그인 주석의 카카오 언급 갱신

## 검증
- ./gradlew :app:assembleDebug :data:testDebugUnitTest
- 에뮬레이터 04 화면 스크린샷(가능 시)
