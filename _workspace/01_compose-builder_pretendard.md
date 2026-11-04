# Pretendard 적용

## 변경
- core/designsystem/src/main/res/font/: 공식 v1.3.9 정적 TTF 4종(400/500/600/700), 원본 바이너리 그대로 포함.
- core/designsystem/src/main/assets/licenses/Pretendard-LICENSE.txt: 배포본 LICENSE 원문을 APK에 동봉.
- core/designsystem/FONTS.md: 출처/버전/갱신 경로 기록.
- theme/Type.kt: PretendardFontFamily, 기존 디자인 토큰 13종 및 Material3 Typography 15종 적용. 기존 크기/굵기/행간 유지.
- theme/Theme.kt: 공통 MaterialTheme typography 연결.
- DestinationScreen.kt: 독립 TextStyle을 쓰는 검색 입력에 fontFamily 명시.
- EmailCodeScreen.kt: 숨김 BasicTextField에도 MoyeotaType 적용.

## 점검
- MainActivity는 전체 UI를 MoyeotaTheme으로 감싸므로 공통 테마를 통해 모든 Material Text에 적용.
- 기존 입력 5곳 확인: 공용 입력, 채팅 작성/검색은 기존 MoyeotaType 상속; 목적지 검색/이메일 코드 추가 적용.
- 앱의 FontWeight 사용은 Normal/Medium/SemiBold/Bold 4종으로 모두 리소스 포함.
- OS 알림 및 네이버 지도 SDK 내부 지도 글자 등 외부 시스템 렌더링은 앱 Compose Typography 범위 밖.
- 빌드/실기 검증은 리더/qa-verifier가 수행.

## 진입 경로
앱 실행 → 온보딩/로그인; 홈 → 목적지 검색(입력); 회원가입 → 이메일 코드; 채팅 → 작성/검색.
