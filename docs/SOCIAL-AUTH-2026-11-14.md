# Android 소셜 인증 구현·검증

## 구현 결과

시작 화면의 `Google로 계속하기`(왼쪽에 Google 로고)가 앱 계층의 Credential Manager를 호출한다. 기존 회원은 기존 세션 저장·푸시 등록 경로로 로그인하고, 신규 회원은 기존 프로필·문자 인증·매너 서약 화면으로 이동한다. 소셜 가입에서는 아이디·비밀번호·이메일 입력을 표시하지 않는다.

`SocialSignupState`는 Activity 범위 ViewModel이며 가입 대기표와 제출 초안을 메모리에만 보관한다. `SavedStateHandle`, `Bundle`, 파일, 환경 설정에는 공급자 토큰과 가입 대기표를 저장하지 않는다. 회전 후 초안은 유지하고 프로세스 종료 후에는 구글 로그인으로 돌아간다. 서버의 가입 만료 응답도 구글 로그인으로 되돌린다. 취소는 오류 배너 없이 시작 화면에 남는다.

마이페이지 → 로그인 수단 관리에서는 비밀번호 보유 여부와 Google/Apple 연결 상태를 조회한다. 등록된 휴대폰 문자 인증 → 구글 SDK → 연결 API → 목록 갱신 순서로 동작한다. 클라이언트는 전화번호를 입력받거나 서버에 보내지 않는다. 연결 증표는 메모리에만 두고 5분 만료, 서버 만료, 성공 시 폐기한다. 재발송에는 1분 쿨다운을 둔다. 계정 합치기·연결 교체·삭제 기능은 제공하지 않는다.

탈퇴 화면은 로그인 수단 조회에 성공한 후에만 제출할 수 있다. `hasPassword=true`이면 기존 비밀번호 입력·검증을 유지하고, `false`이면 비밀번호 입력 없이 본문 없는 탈퇴 API를 사용한다. 비밀번호 재설정 메뉴도 로그인 수단 관리 화면에서 `hasPassword`가 참일 때만 나타난다.

디버그 HTTP 로그를 BASIC으로 제한해 인증 요청·응답 본문을 로그에 남기지 않는다. 연결 전용 인증 오류 `USER401/406/407`은 세션 재발급이나 로그아웃으로 처리하지 않는다.

## API 계약

| 동작 | 요청 | 성공 응답 |
|---|---|---|
| 구글 로그인 | `POST /api/v1/auth/login/google {idToken}` | `LOGIN` 토큰 쌍 또는 `SIGNUP_REQUIRED` 대기표 |
| 소셜 가입 | `POST /api/v1/auth/social/register` | 201 토큰 쌍 |
| 로그인 수단 | `GET /api/v1/users/me/login-methods` | `{hasPassword,methods:[{provider,status}]}` |
| 연결 문자 발송 | `POST /api/v1/users/me/login-methods/sms/send` 본문 없음 | 204 |
| 연결 문자 확인 | `POST /api/v1/users/me/login-methods/sms/verify {code}` | `{verificationToken,expiresInSeconds}` |
| 구글 연결 | `POST /api/v1/users/me/login-methods/google {idToken,verificationToken}` | 204 |
| 소셜 전용 탈퇴 | `DELETE /api/v1/users/me` 본문 없음 | 204 |

신규 API는 인증된 `UserApi` 클라이언트를 사용한다. backend 작업 트리의 `LoginMethodsController.java`, `LoginMethodsService.Methods/Method/Verification`을 읽어 경로·필드·응답 상태를 교차 확인했다. 실제 서버는 미기동 상태이므로 **실서버 미검증**이다.

`AuthRepository`에 `getLoginMethods`, `sendLoginMethodCode`, `verifyLoginMethodCode`, `linkGoogle`을 추가했다. SDK는 domain의 `GoogleCredentialProvider`를 통해 주입하며 presentation은 data DTO나 Google SDK에 의존하지 않는다.

## SDK·콘솔

- `androidx.credentials:credentials:1.6.0`, `credentials-play-services-auth:1.6.0` 안정판.
- `com.google.android.libraries.identity.googleid:googleid:1.2.0`.
- 버튼 전용 `GetSignInWithGoogleOption`과 웹 서버 client ID를 사용한다.
- 공개 client ID 기본값은 기존 콘솔 전달 문서와 같다. `GOOGLE_SERVER_CLIENT_ID`로 로컬/CI 재정의 가능하다.
- Google OAuth Android 클라이언트에는 `com.moyeota`와 실제 설치본 서명 SHA-1을 등록해야 한다. Play 앱 서명 인증서와 업로드 인증서는 구분한다.
- 로그아웃 후 `clearCredentialState`를 호출한다. 공급자 계정 자체를 로그아웃시키지 않는다.

참고: [Google 로그인 구현](https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation), [Credential Manager 안정판](https://developer.android.com/jetpack/androidx/releases/credentials), [Google ID SDK](https://developers.google.com/identity/android-credential-manager/android/reference/com/google/android/libraries/identity/googleid/package-summary).

## 검증

`./gradlew :app:assembleDebug :data:testDebugUnitTest :presentation:testDebugUnitTest --max-workers=2 --console=plain` 통과. 최종 실행은 7초였다.

- 단위 테스트: **data 619개, presentation 860개, 총 1,479개 통과**. 실패·오류·건너뜀 0. 기존 API·세션 테스트와 신규 소셜 가입, 문자 연결 만료/충돌/쿨다운/증표 소진, 소셜 탈퇴, Retrofit 요청 본문 계약, 401 세션 보존 테스트 포함.
- debug 빌드: 통과. `app/build/outputs/apk/debug/app-debug.apk`.
- release 빌드: `./gradlew :app:assembleRelease --max-workers=2 --console=plain` **56초, 통과**. `app/build/outputs/apk/release/app-release-unsigned.apk`(약 110MB). 서명 설정이 없어 **미서명 APK**이며 설치·스토어 제출용 결과물이 아니다.
- `git diff --check`: 통과.
- 실기: **미검증**. 기존 `emulator-5554`는 `adb devices`에 나타나지만 `adb shell getprop sys.boot_completed`가 응답하지 않았다. 기존 기기는 종료하지 않았다. 별도 `Pixel_6 -read-only -no-snapshot-load -port 5556`는 기존 인스턴스가 쓰기 가능 모드여서 에뮬레이터가 실행을 거절했다. 로그: `/private/tmp/android-social-emulator.log`.
- 실제 계정 Google 인증·SMS 발송·서버 연결·탈퇴, iOS Apple 가입 → Google 연결 → Android 같은 UUID 확인: **미검증**. 서버 기동·운영 사용자 변경은 수행하지 않았다.

기존 `AccountRecoveryTest`는 `runTest` 종료 중 실시간 ticker를 가상 시간으로 계속 실행하는 문제가 있었다. 스레드 덤프로 원인을 확인하고 테스트가 만든 ViewModel의 scope를 `finally`에서 취소하도록 정리했다. 제품의 타이머 로직은 변경하지 않았다.

## 재검증 순서

1. 응답 가능한 Google Play 에뮬레이터/기기에서 debug APK를 설치한다.
2. 시작 화면 Google 버튼 → SDK 취소 → 오류 없이 복귀를 확인한다.
3. 신규 Google 계정 → 프로필·SMS → 매너 서약 → 가입 완료와 세션을 확인한다. 입력 중 회전·프로세스 종료도 확인한다.
4. 기존 로컬 계정 → 마이 → 로그인 수단 관리 → SMS → Google 연결 → 목록 갱신·동일 UUID를 확인한다.
5. 잘못된 SMS, 만료 증표, 다른 회원에게 연결된 Google 계정, 통신 실패를 확인한다.
6. 비밀번호 보유 계정에는 비밀번호 메뉴·탈퇴 입력이 있고 소셜 전용 계정에는 없는지 확인한다.
7. iOS Apple 가입 계정에 Google을 연결한 뒤 Android Google 로그인 UUID와 탑승 기록을 대조한다.

## PR 초안

제목: `구글 네이티브 로그인과 소셜 가입·로그인 수단 연결 구현`

본문:

Android 시작 화면에 Credential Manager 기반 구글 로그인을 연결하고, 신규 회원은 기존 프로필·문자 인증으로 가입을 마친 뒤 로그인합니다. 가입 대기표는 메모리에만 보관하며 만료 시 구글 로그인으로 복귀합니다.

마이페이지에서 등록 휴대폰 재인증 후 구글 계정을 연결할 수 있습니다. 비밀번호 보유 여부에 따라 비밀번호 메뉴와 탈퇴 요청을 구분하고, 연결용 인증 만료가 앱 세션을 지우지 않도록 처리했습니다. 인증 본문은 디버그 로그에도 남기지 않습니다.

검증: debug·release 빌드 통과, 단위 테스트 총 1,479개 통과. release APK는 미서명. 실기 및 실제 Google·SMS 계정 연동은 에뮬레이터 장애와 서버 미기동으로 미검증입니다. 운영 반영 전 서버의 로그인 수단 API 배포와 Google 서명 인증서 등록을 확인해야 합니다.
