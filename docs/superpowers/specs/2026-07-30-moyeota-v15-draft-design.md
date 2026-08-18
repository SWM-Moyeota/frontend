# 모여타 v15 초안 — 설계 문서

날짜: 2026-07-30
근거: Figma 「모여타 프로젝트」 파일 (nOqpTUmzAhjBW1SDLVd24A) 페이지 `모여타 v15 — 화면 + 설명 나란히` (node 2434:794)

## 목표

Figma v15 와이어프레임 35화면과 각 화면의 디스크립션(이동 · 유효값 검증 · 에러 · 미연결)을
**디자인 그대로** Jetpack Compose 코드로 옮긴 실행 가능한 초안 앱.

- 데이터: 전부 더미(하드코딩). 서버/지도 SDK 없음. 지도는 와이어프레임과 동일한 placeholder.
- 동작: 디스크립션에 적힌 것만 구현 — 화면 이동, 유효값 검증(이메일 형식·코드 자리수 등), 에러 표시.
  "미연결" 항목은 코드에서도 미연결로 둔다.
- 테스트: 정적 UI 초안이므로 단위 테스트 제외. Gradle 빌드 성공 + 실행 확인으로 검증.

## 구조 — Propose_App_Android 참고 (Clean Architecture 멀티모듈)

레퍼런스: https://github.com/lmy6268/Propose_App_Android
초안 단계에 맞춘 조정: `build-logic`(컨벤션 플러그인) 제외 — 버전 카탈로그로 대체.
Hilt 제외 — 더미 데이터 단계에선 수동 DI(AppContainer), 백엔드 연동 시 Hilt 도입.

```
frontend/
├── app/                 — 진입점: MainActivity, MoyeotaApplication, AppContainer(수동 DI)
├── core/designsystem/   — Color.kt · Type.kt · Theme.kt + 공통 컴포넌트
│                          (PrimaryButton, MoyeotaTextField, StatusBarMock, PageDots,
│                           BottomSheet, ListCard, Chip 등 와이어프레임 공통 요소)
├── presentation/        — 화면 + 네비게이션
│   └── feature/
│       ├── onboarding/  01–03  (O01–O03)
│       ├── auth/        04–13  (S01, S25, S02, S03, S26, S04–S08)
│       ├── home/        14–16  (S09, S10, 신규 모달)
│       ├── explore/     17–20  (V07, V07b, V07c, 신규 합류확인)
│       ├── matching/    21–23, 25  (S11–S14)
│       ├── chat/        24, 26, 27  (S16, S15, S17)
│       ├── payment/     28–32  (S19a, S19–S21, 신규)
│       └── mypage/      33–35  (S18, S22, S24)
│   └── core/MainNavGraph.kt — 디스크립션의 이동 규칙대로 라우팅
├── domain/              — 모델(Ride, User, Settlement…) + 리포지토리 인터페이스 (Kotlin JVM 모듈)
└── data/                — 더미 리포지토리 구현 (Kotlin JVM 모듈)
```

패키지: `com.moyeota.*` (모듈별 `com.moyeota.designsystem`, `com.moyeota.presentation` 등)

## 기술 스택

- Kotlin 2.x + Jetpack Compose (BOM), AGP 9.2.1 (기존), compileSdk 36 / minSdk 24
- Navigation Compose, Activity Compose, Lifecycle ViewModel Compose
- 색·타이포: 와이어프레임 값 그대로 (레포의 `moyeota-figma-tokens.json` 참고)

## 구현 순서

1. Gradle 멀티모듈 스캐폴딩 (settings, 버전 카탈로그, 모듈별 build.gradle.kts) → 빌드 통과
2. core/designsystem — 테마 + 공통 컴포넌트
3. domain + data — 더미 모델/리포지토리
4. presentation — 그룹 A→I 순서로 화면 구현 (Figma에서 화면별 스펙/스크린샷 확인하며)
5. MainNavGraph 연결 + app 모듈 조립
6. 전체 빌드 + 에뮬레이터 실행 확인
