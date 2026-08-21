# 산출물: FCM 푸시 알림 기본 세팅

**담당:** compose-builder
**날짜:** 2026-08-19
**빌드 결과:** `./gradlew clean :app:assembleDebug --console=plain` → **BUILD SUCCESSFUL** (신규 코드 경고 0건)

## 변경/생성 파일

### 생성
| 파일 | 내용 |
|------|------|
| `app/src/main/kotlin/com/moyeota/app/MoyeotaFirebaseMessagingService.kt` | FCM 수신 진입점. 토큰 갱신 로깅 + 알림 표시 |

### 수정
| 파일 | 변경 내용 |
|------|----------|
| `gradle/libs.versions.toml` | `googleServices = "4.5.0"`, `firebaseBom = "34.17.0"` 버전 추가 / `firebase-bom`, `firebase-messaging` 라이브러리 추가 / `google-services` 플러그인 추가 |
| `build.gradle.kts` (루트) | `alias(libs.plugins.google.services) apply false` |
| `app/build.gradle.kts` | `alias(libs.plugins.google.services)` 적용 + `platform(libs.firebase.bom)`, `libs.firebase.messaging` 의존성 |
| `app/src/main/AndroidManifest.xml` | `POST_NOTIFICATIONS` 권한 + `MoyeotaFirebaseMessagingService` 등록(exported=false, `com.google.firebase.MESSAGING_EVENT`) |
| `app/src/main/kotlin/com/moyeota/app/MoyeotaApplication.kt` | `onCreate`에서 `moyeota_default` 알림 채널 생성 (IMPORTANCE_HIGH, "모여타 알림") |
| `app/src/main/kotlin/com/moyeota/app/MainActivity.kt` | Android 13+ `POST_NOTIFICATIONS` 런타임 권한 요청 |

`app/google-services.json`은 미수정.

## 주요 결정사항

### 1. 버전 선택: google-services 4.5.0 (요청서의 4.4.x 대신)
Maven 메타데이터 확인 결과 4.5.0이 최신 안정 버전(2026-06 릴리스)이며, 본 프로젝트의 AGP 9.2.1과의 호환성을 고려해 최신을 채택했다. firebase-bom은 34.17.0(최신 안정)이며 실제 해석된 firebase-messaging은 25.1.1.

### 2. `onNewToken` + `onRegistered` 양쪽 오버라이드 (중요)
firebase-messaging 25.1.1에서 **`onNewToken`은 deprecated** 상태다. AAR 바이트코드(`handleIntent`)를 직접 디컴파일해 확인한 결과:

- `com.google.firebase.messaging.NEW_TOKEN` 액션 → `onNewToken` 호출
- `com.google.firebase.messaging.FCM_REGISTERED` 액션 → `onRegistered` 호출
- 두 분기는 **상호 배타적이며 서로를 호출하지 않는다** (베이스 구현은 양쪽 모두 빈 메서드)

따라서 한쪽만 오버라이드하면 다른 액션 경로의 토큰을 놓친다. 양쪽을 모두 오버라이드해 `handleToken()` 하나로 라우팅했다. 분기가 배타적이므로 단일 이벤트에 대한 중복 호출은 발생하지 않는다. deprecated 경고는 `@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")`로 처리.

### 3. 알림 표시 세부
- notification 페이로드 우선, 없으면 data 페이로드(`title`/`body`) 폴백. 본문이 아예 없으면 표시 생략
- 탭 시 `MainActivity` 실행 (`FLAG_IMMUTABLE` + `FLAG_UPDATE_CURRENT`, `CLEAR_TOP|SINGLE_TOP`)
- 알림 채널은 앱 시작 시점(Application.onCreate)에 선생성 — 서비스가 콜드 스타트로 깨어나도 채널 보장
- `NotificationManagerCompat.areNotificationsEnabled()`로 권한 미허용 시 조기 반환
- 스몰 아이콘은 `R.drawable.ic_launcher_foreground` 사용 (전용 알림 아이콘 에셋 없음 → 개선 여지)

### 4. 권한 요청 정책
`ActivityResultContracts.RequestPermission()` 사용. TIRAMISU 미만 스킵 + 이미 허용 시 스킵. 거부해도 재요청하지 않음 — 알림은 부가 기능이므로 진입 차단 없음.

### 5. androidx-core-ktx
`app/build.gradle.kts`에 이미 존재해 추가 불필요. `NotificationCompat`/`NotificationManagerCompat`/`ContextCompat` 모두 커버됨.

## 빌드 검증

`processDebugGoogleServices` 태스크가 정상 실행되어 리소스 생성 확인:
- `app/build/generated/res/processDebugGoogleServices/values/values.xml` → `google_app_id` = `1:568416567998:android:7a93d2e52e9ed8fd6f14ef`, `project_id` = `moyeota-b1654`

머지된 매니페스트에서 서비스 등록 및 권한 반영 확인 완료 (`POST_NOTIFICATIONS`, `com.google.android.c2dm.permission.RECEIVE` 자동 병합).

## 후속 과제 (범위 외)
- FCM 토큰 서버 등록 — `handleToken()`의 TODO. 백엔드 엔드포인트 확정 후 api-integrator 작업
- 알림 탭 시 딥링크 라우팅 (현재는 MainActivity 진입만)

---

# 개정 1차: QA 지적사항 반영 (2026-08-19)

**근거:** `_workspace/02_qa-verifier_report.md` §2 개선 필요 3건
**빌드 결과:** `./gradlew clean :app:assembleDebug --console=plain` → **BUILD SUCCESSFUL** (app 모듈 경고 0건)

## 수정 내역

### A3 — 권한 요청이 구성 변경마다 반복 (스펙 위반, 최우선)
`MainActivity.kt` — `requestNotificationPermissionIfNeeded()` 호출을 `savedInstanceState == null` 가드로 감쌌다. 화면 회전 등으로 Activity가 재생성돼도 재요청이 나가지 않아 "거부 시 강제 재요청하지 않음" 정책이 세션 내에서도 지켜진다. Android 13의 2회 거부 → 영구 거부 규칙에 사용자가 의도치 않게 빠지는 경로도 함께 차단된다.

### A2 — 기존 설치분 토큰 획득 불가
`MoyeotaApplication.onCreate`에 `fetchFcmToken()` 추가. `FirebaseMessaging.getInstance().token`으로 앱 시작 시마다 명시 조회하며, 실패 시 `Log.w`로 기록하고 조용히 종료한다.

중복 로직을 만들지 않기 위해 기존 `handleToken()`을 서비스의 private 인스턴스 메서드에서 **companion object의 공용 함수**로 승격했다. 이제 토큰 유입 경로 3개가 모두 한 곳으로 수렴한다:
- `onNewToken` (NEW_TOKEN 액션)
- `onRegistered` (FCM_REGISTERED 액션)
- `MoyeotaApplication.fetchFcmToken()` (앱 시작 시 명시 조회)

백엔드 엔드포인트 확정 시 `handleToken()` 한 곳만 채우면 세 경로가 동시에 반영된다.

### A1 — 알림 스몰 아이콘
`app/src/main/res/drawable/ic_notification.xml` 신규 생성. 24dp 뷰포트 / 단색(`#FFFFFFFF`) 단일 path 종 실루엣. 시스템이 알파 채널만 읽는 스몰 아이콘 제약에 맞춘 형태다. `MoyeotaFirebaseMessagingService`의 `setSmallIcon`을 `R.drawable.ic_launcher_foreground` → `R.drawable.ic_notification`으로 교체.

APK 패키징 확인: `res/drawable/ic_notification.xml` (736 bytes) 포함.

## 추가 결정사항: `getToken()` deprecated — 현행 유지

A2 구현 중 `FirebaseMessaging.getToken()`도 `onNewToken`과 같은 deprecation 대상임이 드러났다. AAR 바이트코드로 후속 API `register()`를 조사한 결과:

- `register()`는 `Task<Void>`를 반환하며 토큰을 직접 주지 않는다. 토큰은 `onRegistered` 콜백으로 전달된다
- `blockingRegister`는 캐시된 유효 토큰이 있어도 `invokeOnRegistrationChanged`를 호출하므로, 기능적으로는 A2를 해결할 수 있다
- **그러나** `register()`는 진입 즉시 `isV1RegistrationEnabled()`를 검사하고, 거짓이면 `IllegalStateException("API disabled...")`을 던진다. 활성화하려면 매니페스트에 `firebase_messaging_installation_id_enabled=true` meta-data opt-in이 필요하다

등록 방식 자체가 바뀌는 변경이라 "기본 세팅" 범위를 넘어선다고 판단해 `getToken()`을 유지하고 `@Suppress("DEPRECATION")` + 사유 주석을 남겼다. **v1 registration 전환은 별도 과제로 리더 판단 필요.**

## 개정 1차 변경 파일
| 파일 | 변경 |
|------|------|
| `app/src/main/res/drawable/ic_notification.xml` | **신규** — 24dp 단색 알림 아이콘 |
| `app/src/main/kotlin/com/moyeota/app/MoyeotaFirebaseMessagingService.kt` | `handleToken()` companion 승격 / `setSmallIcon` 교체 |
| `app/src/main/kotlin/com/moyeota/app/MoyeotaApplication.kt` | `fetchFcmToken()` 추가 |
| `app/src/main/kotlin/com/moyeota/app/MainActivity.kt` | `savedInstanceState == null` 가드 |

## 재검증 요청 (qa-verifier)
- A3: 앱 실행 → 권한 거부 → 화면 회전 시 다이얼로그 재표시 없음 확인
- A2: `adb shell am force-stop` 후 콜드 스타트 시 `MoyeotaFcm: FCM 토큰:` 로그가 **찍히는지** 확인 (기존 리포트 §A2에서 안 찍히던 케이스)
- A1: 실제 알림 표시 시 아이콘 실루엣 — 리포트 U1(Firebase 콘솔 발송) 수행 시 함께 확인
