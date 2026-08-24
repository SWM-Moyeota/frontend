# 작업 요청: FCM 푸시 알림 세팅

**날짜:** 2026-08-19
**요청:** FCM 푸시 알림 기본 세팅

## 전제 조건
- `app/google-services.json` 이미 존재 (package_name: `com.moyeota` — applicationId와 일치 확인됨)
- google-services Gradle 플러그인 미적용 상태
- Firebase 의존성 전무 (BOM/FCM 없음)

## 작업 범위
1. `gradle/libs.versions.toml` — google-services 플러그인, firebase-bom, firebase-messaging 추가
2. 루트 `build.gradle.kts` — `alias(libs.plugins.google.services) apply false`
3. `app/build.gradle.kts` — 플러그인 적용 + Firebase BOM/FCM 의존성
4. `AndroidManifest.xml` — POST_NOTIFICATIONS 권한 + FirebaseMessagingService 등록
5. `FirebaseMessagingService` 구현 (`app/src/main/kotlin/com/moyeota/app/`) — onNewToken 로깅, onMessageReceived 알림 표시, 알림 채널 생성
6. Android 13+ 알림 권한 요청 처리 (MainActivity)

## 범위 제외
- FCM 토큰 서버 등록 API 연동 (백엔드 엔드포인트 미정 → api-integrator 불필요)

## 팀 구성 판단
- 구현 영역이 app 모듈 인프라 단일 영역 → compose-builder 단독 서브 실행
- 이후 qa-verifier로 빌드 + 에뮬레이터 실기 검증
