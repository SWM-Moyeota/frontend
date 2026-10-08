# 96 입력 — 로그인 시작 화면 카카오 버튼 제거

작성: 2026-10-08 (브랜치 refactor/remove-unused-screens = origin/develop 79ad602)

## 요청
- 로그인 시작 화면(LoginScreen.kt)의 「카카오로 3초 만에 시작」 노란 박스 제거
- 탭 시 뜨는 「카카오 로그인은 준비 중이에요」 INFO 배너, 미사용 showRetryBanner ERROR 배너·프리뷰 제거
- 사유: 백엔드에 소셜 로그인 엔드포인트가 없고 가입·로그인은 아이디/비밀번호뿐

## 참고
- 이전 세션이 만든 패치 `96_kakao-removal.patch` 가 현재 develop 에 `git apply --check` 통과함
- showRetryBanner 는 LoginScreen 내부에서만 쓰임 (호출부 없음)
