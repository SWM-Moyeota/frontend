# 97 compose-builder — 로그인 시작 화면(04) 카카오 버튼 제거

작성: 2026-10-08 · 브랜치 refactor/remove-unused-screens · 입력: 96_input_kakao_removal.md

## 변경 파일
- presentation/.../feature/auth/LoginScreen.kt — `96_kakao-removal.patch` 적용(`git apply --check` 통과 후 적용) + 추가 정리
- presentation/.../core/MainNavGraph.kt — LOGIN composable 주석 1줄 (「준비 중」 안내 언급 → 10 프로필 설정 / 소셜 로그인 미제공)
- presentation/.../feature/auth/LoginFormScreen.kt — KDoc 1줄 (「04 의 카카오 버튼은 준비 중 안내만」 → 「04 에도 카카오 버튼은 없다」)

## 제거된 요소 (LoginScreen.kt)
- 「카카오로 3초 만에 시작」 노란 Box(54dp) + 그 위 `Spacer(32.dp)`
- `kakaoNoticeShown` 상태 + INFO 배너 「카카오 로그인은 준비 중이에요…」
- `showRetryBanner` 파라미터 + ERROR 배너 「다시 시도해 주세요」 + `LoginScreenRetryBannerPreview`
- 색: `KakaoYellow`, `KakaoLabel`
- import: getValue, mutableStateOf, setValue, clip, NoticeBanner, NoticeKind (패치)
- import: `Offset`, `MoyeotaType` (패치 이전부터 미사용이던 것, 추가 정리)

## 남은 레이아웃 구조
Column(fillMaxSize, bg)
- StatusBarSpacer
- 스크롤 Column(weight 1f, 좌우 16dp): 24 → 「모여타」 → 36 → 헤드라인 → 18 → 서브카피 → 26 → 신뢰 카드(불릿 2개) → 24
- 하단 고정 Column(좌우 16dp): PrimaryCtaButton 「이메일로 시작하기」 → 18 → 「이미 계정이 있어요 · 로그인」 → 11 → 약관 문구 → 28
- NavigationBarSpacer

카드 아래 24dp 뒤 남는 높이는 weight 영역이 흡수 → CTA 는 하단 고정, 간격 어색함 없음.

## 확인
- KDoc: onEmailStart → 10 프로필 설정 = MainNavGraph `Routes.PROFILE_SETUP`("auth/profile", 10 S05) 일치
- `grep -rn showRetryBanner presentation app` → 결과 없음
- 남은 import 전부 사용 중

## 빌드
`JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:assembleDebug --console=plain -q` → 성공(EXIT 0), Login/MainNavGraph 관련 경고 없음.
에뮬레이터 설치는 하지 않음 (리더 담당).

## 진입 경로
온보딩 → 03 시작하기 → 04 시작(Routes.LOGIN)
