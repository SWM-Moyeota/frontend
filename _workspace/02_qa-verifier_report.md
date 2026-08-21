# QA 검증 리포트: FCM 푸시 알림 기본 세팅

**담당:** qa-verifier
**날짜:** 2026-08-19
**대상:** `_workspace/01_compose-builder_fcm.md` 구현 내역
**검증 환경:** Pixel_6 AVD / **API 37 (Android 17)** / `google_apis` arm64-v8a 이미지 / 콜드부트 (`-no-snapshot-load`)

**종합 판정: 통과 (블로킹 결함 0건).** 품질 개선 항목 3건, 미검증 4건.

---

## 1. 통과 (PASS)

### P1. 빌드
```
./gradlew :app:assembleDebug --console=plain  →  BUILD SUCCESSFUL (exit 0)
APK: app/build/outputs/apk/debug/app-debug.apk (15,164,143 bytes)
```

### P2. google-services.json ↔ applicationId 정합
| 항목 | 값 | 판정 |
|------|----|------|
| `google-services.json` package_name | `com.moyeota` | — |
| `app/build.gradle.kts` applicationId | `com.moyeota` | **일치** |
| project_id | `moyeota-b1654` | — |
| mobilesdk_app_id | `1:568416567998:android:7a93d2e52e9ed8fd6f14ef` | — |

`processDebugGoogleServices` 생성 리소스 확인 (`app/build/generated/res/processDebugGoogleServices/values/values.xml`): `google_app_id`, `gcm_defaultSenderId=568416567998`, `project_id` 모두 정상 주입.

### P3. Manifest service 선언 ↔ 실제 클래스 경로 정합
소스 매니페스트는 상대 표기 `.MoyeotaFirebaseMessagingService`를 쓰고 있고, `namespace`(`com.moyeota.app`)와 `applicationId`(`com.moyeota`)가 서로 다르다. **상대 표기는 applicationId가 아니라 namespace 기준으로 해석되므로** 이 조합은 오해를 부르기 쉬운 지점이다. 머지된 매니페스트에서 최종 해석 결과를 직접 확인했다.

`app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml:48`
```
android:name="com.moyeota.app.MoyeotaFirebaseMessagingService"
```
실제 클래스 `app/src/main/kotlin/com/moyeota/app/MoyeotaFirebaseMessagingService.kt` (package `com.moyeota.app`)와 **일치**. 런타임 서비스 기동으로도 교차 확인됨(P7).

병합 결과 함께 확인:
- `POST_NOTIFICATIONS` 권한 (line 12)
- `com.google.android.c2dm.permission.RECEIVE` 자동 병합 (line 15)
- `com.google.firebase.MESSAGING_EVENT` intent-filter (line 51)
- `FirebaseInstanceIdReceiver`, `FirebaseInitProvider`(authorities `com.moyeota.firebaseinitprovider`), `ComponentDiscoveryService` 정상 병합

### P4. 크래시 없음 (콜드 설치 + 실행)
```
FirebaseApp: Device unlocked: initializing all Firebase APIs for app [DEFAULT]
FirebaseInitProvider: FirebaseApp initialization successful
```
- `adb logcat -b crash` 버퍼 **비어 있음** (최초 실행 / 재실행 모두)
- `AndroidRuntime`/`FATAL` 로그 0건
- 프로세스 생존 확인 (PID 3624 유지)
- `MoyeotaApplication.onCreate`의 알림 채널 생성이 예외 없이 통과 (채널 등록으로 역증명, P6)

### P5. POST_NOTIFICATIONS 권한 다이얼로그
최초 실행 시 `GrantPermissionsActivity`가 온보딩 화면 위로 정상 표시. Allow 탭 후:
```
android.permission.POST_NOTIFICATIONS: granted=true, flags=[ USER_SET|USER_SENSITIVE_WHEN_GRANTED|... ]
```
**스크린샷:** `_workspace/screenshots/01_post_notifications_dialog.png`

재실행(`force-stop` 후 콜드 스타트) 시 다이얼로그 **재표시 없음** — `topResumedActivity=com.moyeota/.app.MainActivity`. 구현 의도(이미 허용 시 스킵)대로 동작.

### P6. 알림 채널 `moyeota_default` 시스템 등록
`adb shell dumpsys notification --noredact`:
```
NotificationChannel{mId='moyeota_default', mName=모여타 알림, mImportance=4, ...}
```
- ID / 이름 / `mImportance=4`(IMPORTANCE_HIGH) 모두 스펙 일치
- `mDeleted=false`, `mShowBadge=true`
- 앱 재실행 후에도 채널 **유지** 확인

> 참고: 태스크에 적힌 `dumpsys notification_manager`는 존재하지 않는 서비스명이라 `Can't find service` 를 반환한다. 올바른 명령은 `dumpsys notification` 이다.

### P7. FCM 토큰 발급 (실기)
`MoyeotaFirebaseMessagingService`의 토큰 콜백이 실제로 호출되어 실토큰이 발급됨:
```
01:53:33.484  3624  3691 D MoyeotaFcm: FCM 토큰 갱신: ejCIuixeQ9ShYU1mpxKfM4:APA91bFkZV5sk5yrv8SKPqqaLF66Ncu84vUb_6A35vRKB6DpOXPPG_4XFc7qFWlUpDg1aL9rdlpkSNHigWGZJWpBfX3g8nrGQxSJ6Q6_LTN2VL7lYAera3g
```
`google_apis`(Play 스토어 미포함) 이미지지만 GmsCore가 있어 토큰 발급까지 정상 동작. 서비스 등록 → 콜백 라우팅 → 로깅 경로가 실기에서 end-to-end로 증명됨.

### P8. `onNewToken` + `onRegistered` 이중 오버라이드 — 구현자 주장 검증 완료
compose-builder가 산출물 §2에서 내린 비표준 결정을 AAR 바이트코드로 독립 재검증했다 (`firebase-messaging-25.1.1.aar`).

`javap` 결과 — 두 메서드 모두 실존:
```
public void onNewToken(java.lang.String);
public void onRegistered(java.lang.String);
```
`handleIntent` 디스어셈블 — 각 분기가 **모두 `goto 125`(메서드 끝)로 종료**되어 상호 배타적임을 확인:
```
31: ldc  #41  // String com.google.firebase.messaging.NEW_TOKEN
47: invokevirtual #49  // Method onNewToken:(Ljava/lang/String;)V
50: goto 125
53: ldc  #53  // String com.google.firebase.messaging.FCM_REGISTERED
69: invokevirtual #55  // Method onRegistered:(Ljava/lang/String;)V
72: goto 125
```
**판정: 주장 타당.** 한쪽만 오버라이드하면 다른 액션 경로의 토큰을 놓치고, 양쪽을 오버라이드해도 단일 이벤트 중복 호출은 발생하지 않는다. 의존성 해석 결과도 문서와 일치 (`firebase-bom:34.17.0` → `firebase-messaging:25.1.1`, `firebase-installations:19.1.2`).

---

## 2. 개선 필요 (품질 이슈 — 블로킹 아님)

### A1. 알림 스몰 아이콘이 런처 포그라운드 재사용 — 실제로 깨진 아이콘으로 렌더링됨 (중요도: 중)
`app/src/main/kotlin/com/moyeota/app/MoyeotaFirebaseMessagingService.kt:58`
```kotlin
.setSmallIcon(R.drawable.ic_launcher_foreground)
```
`res/drawable/ic_launcher_foreground.xml`은 **108x108dp 어댑티브 아이콘 포그라운드**이며, 그라디언트가 들어간 full-bleed 그림자 path를 포함한 2개 path로 구성돼 있다. 안드로이드 알림 스몰 아이콘은 **알파 채널만 사용해 24dp 흰색 실루엣으로 변환**하므로, 넓은 면적에 알파가 깔린 이 드로어블은 형체를 알 수 없는 흰 덩어리로 표시된다.

`res/drawable/`에 전용 알림 아이콘 에셋이 아예 없음(`ic_launcher_background.xml`, `ic_launcher_foreground.xml` 2개뿐).

**요청 대상: compose-builder** — 24dp 단색 실루엣 전용 아이콘(예: `ic_notification.xml`) 추가 후 교체.

### A2. 기존 설치분의 토큰을 얻을 방법이 없음 — 백엔드 연동 시 조용히 실패한다 (중요도: 중, 후속 작업 필수 전제)
현재 토큰 획득 경로가 `onNewToken`/`onRegistered` 콜백 **단독**이다. 이 콜백들은 토큰이 **신규 생성되거나 회전될 때만** 발화한다.

실기에서 이를 확인했다 — 앱 재실행(force-stop 후 콜드 스타트) 시 `FirebaseApp initialization successful`은 찍히지만 **`MoyeotaFcm` 토큰 로그는 찍히지 않는다.** 즉 최초 설치 순간을 놓치면 앱은 자기 토큰을 알 방법이 없다.

`MoyeotaFirebaseMessagingService.kt:29`의 TODO(백엔드 토큰 등록)를 나중에 채울 때, 콜백에만 의존하면 **이미 설치된 사용자는 영원히 서버에 등록되지 않는다.** 앱 시작 시 `FirebaseMessaging.getInstance().token` 명시적 조회를 반드시 함께 넣어야 한다.

**요청 대상: api-integrator** — 백엔드 엔드포인트 확정 후 작업 시 이 전제를 반영할 것.

### A3. 권한 요청이 Activity 재생성마다 반복됨 (중요도: 하)
`app/src/main/kotlin/com/moyeota/app/MainActivity.kt:24`에서 `requestNotificationPermissionIfNeeded()`를 `onCreate`에서 호출한다. 화면 회전 등 configuration change로 Activity가 재생성되면 거부 상태에서 다시 요청이 나간다. 산출물에 명시된 정책("거부해도 재요청하지 않음")과 세션 내에서 어긋나며, Android 13의 2회 거부 시 영구 거부 규칙과 맞물려 사용자가 의도치 않게 영구 거부 상태에 빠질 수 있다.

**요청 대상: compose-builder** — `savedInstanceState == null` 가드 또는 ViewModel/`rememberSaveable` 기반 1회성 플래그 적용 검토.

---

## 3. 미검증 (UNVERIFIED)

### U1. Firebase 콘솔 실서버 푸시 발송 — **범위 외 (사용자 수동 작업)**
태스크 제약에 따라 수행하지 않음. 콘솔에서 P7의 토큰으로 테스트 메시지를 보내 최종 확인 필요.

### U2. `onMessageReceived` → 실제 알림 표시 경로 — **환경 제약으로 검증 불가**
`adb shell am startservice`로 `com.google.android.c2dm.intent.RECEIVE` 인텐트 직접 주입을 시도했다(root 포함). 결과: 서비스는 기동되나 **로그·알림 모두 발생하지 않음**, 게시된 알림 0건.

원인은 앱 결함이 아니라 SDK 설계다. 머지된 매니페스트 주석(line 68)이 명시하듯 `FirebaseMessagingService`는 런타임 보안 검사를 수행하며, `com.google.android.c2dm.permission.SEND` 권한을 가진 GmsCore 발신만 수락하고 그 외는 조용히 폐기한다. shell/root는 이 권한을 보유하지 않는다.

따라서 다음은 미검증 상태다:
- notification/data 페이로드 폴백 분기
- `NotificationCompat` 빌드 및 실제 표시
- 알림 탭 → `MainActivity` 진입 (PendingIntent)
- A1의 스몰 아이콘 실제 렌더링 모양

**U1(콘솔 발송)이 수행되면 이 항목들이 한 번에 해소된다.** U1 진행 시 위 4개를 함께 확인할 것을 권장.

### U3. Play Services 버전 경고의 메시지 수신 영향
매 실행마다 재현되는 경고:
```
W GooglePlayServicesUtil: Google Play services out of date for com.moyeota.
                          Requires 261200000 but found 261136035
```
토큰 발급(P7)은 이 경고에도 불구하고 성공했으므로 등록 경로는 정상이다. 다만 **메시지 수신(delivery) 경로까지 무사한지는 확인되지 않았다.** U1에서 콘솔 발송이 실패하면 앱 코드보다 이 GMS 버전 불일치를 먼저 의심할 것 — 에뮬레이터 GMS 업데이트가 불가하면 실기기 또는 `google_apis_playstore` 이미지에서 재확인이 필요하다.

### U4. `:data:testDebugUnitTest`
이번 변경은 app 모듈 인프라 한정이라 data 모듈 유닛 테스트는 실행하지 않았다. 회귀 위험 없음으로 판단.

---

## 4. 스크린샷

| 경로 | 내용 |
|------|------|
| `/Users/sungyoon/Desktop/moyeota/frontend/_workspace/screenshots/01_post_notifications_dialog.png` | 최초 실행 시 POST_NOTIFICATIONS 권한 다이얼로그 ("Allow 모여타 to send you notifications?") |
| `/Users/sungyoon/Desktop/moyeota/frontend/_workspace/screenshots/02_app_running_after_grant.png` | 권한 허용 후 온보딩 1페이지 정상 구동 (레이아웃 깨짐 없음) |

---

## 5. 재현 절차

```bash
cd /Users/sungyoon/Desktop/moyeota/frontend
./gradlew :app:assembleDebug --console=plain

EMU=~/Library/Android/sdk/emulator/emulator; ADB=~/Library/Android/sdk/platform-tools/adb
nohup $EMU -avd Pixel_6 -no-snapshot-load -no-audio > /tmp/emulator.log 2>&1 &
$ADB wait-for-device
until [ "$($ADB shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 2; done

$ADB uninstall com.moyeota            # 최초 실행 상태 재현을 위해 필수
$ADB install -r app/build/outputs/apk/debug/app-debug.apk
$ADB logcat -c
$ADB shell am start -n com.moyeota/com.moyeota.app.MainActivity

$ADB exec-out screencap -p > /tmp/perm_dialog.png      # 권한 다이얼로그
$ADB shell input tap 540 1343                          # Allow (1080x2400 기준)

$ADB logcat -d | grep MoyeotaFcm                       # 토큰 발급 확인
$ADB shell dumpsys notification --noredact | grep -A3 moyeota_default
$ADB shell dumpsys package com.moyeota | grep POST_NOTIFICATIONS
```
