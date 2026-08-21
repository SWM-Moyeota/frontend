# QA 재검증 리포트 (2차): FCM 개선사항 A1~A3 반영분

**담당:** qa-verifier
**날짜:** 2026-08-20
**대상:** `_workspace/01_compose-builder_fcm.md` §개정 1차 (A1/A2/A3 반영분)
**성격:** incremental 재검증 — 1차 통과 P1~P8 전수 재실행 아님. 반영분 + 크래시/빌드 회귀 최소 확인만 수행.
**검증 환경:** Pixel_6 AVD / **API 37** / 콜드부트 (`-no-snapshot-load -no-audio`) / 1080x2400 / 앱 uninstall 후 재설치(최초 실행 상태 재현)

**종합 판정: A1 통과 · A2 통과 · A3 통과 (블로킹 결함 0건).** 미검증 U1/U2(실제 알림 렌더링)는 환경 제약으로 유지, 관찰사항 1건(O1) 기록.

---

## 1. 통과 (PASS)

### P0. 빌드 회귀 없음
```
./gradlew :app:assembleDebug --console=plain  →  BUILD SUCCESSFUL (exit 0)
APK: app/build/outputs/apk/debug/app-debug.apk
```
콜드 설치 후 최초 실행 및 3회 콜드 재시작 전부에서 `logcat -b crash` 버퍼 비어 있음, `FATAL` 0건.

---

### A1. 알림 스몰 아이콘 전용 에셋 — 통과 (정적 + 빌드/패키징)

**정적 스펙 확인** — `app/src/main/res/drawable/ic_notification.xml`
- `android:width/height=24dp`, `viewportWidth/Height=24` → 24dp 스펙 일치
- 단일 `<path>`, `fillColor="#FFFFFFFF"` 단색, 그라디언트·다중 레이어 없음 → 시스템이 알파 채널만 읽어 흰색 실루엣으로 변환하는 스몰 아이콘 제약에 부합 (Material 종 실루엣 형태)
- 1차 A1 결함(108x108 어댑티브 포그라운드를 재사용해 형체 불명 흰 덩어리로 렌더)의 원인이 제거됨

**참조 교차 확인**
- `MoyeotaFirebaseMessagingService.kt:53` → `.setSmallIcon(R.drawable.ic_notification)`
- `grep setSmallIcon` 결과 이 한 줄만 존재 — `ic_launcher_foreground` 잔존 참조 없음

**패키징 확인**
```
unzip -l app-debug.apk | grep ic_notification
  736  res/drawable/ic_notification.xml
```
APK에 736 bytes로 정상 포함.

> **실제 렌더링 모양은 U2로 미검증 유지** — 실제 알림을 띄우려면 인증된 FCM 발송이 필요한데, 아래 U1/U2 사유로 불가. 단, 최초 실행 권한 다이얼로그의 벨 아이콘(스크린샷 `03_a3_first_launch_perm_dialog.png`)은 별개 자원이므로 A1 렌더 증거가 아님.

---

### A2. 앱 시작 시 토큰 명시 조회 — 통과 (실기)

**대상 코드** `MoyeotaApplication.fetchFcmToken()` (`onCreate`에서 호출) → `FirebaseMessaging.getInstance().token` → `MoyeotaFirebaseMessagingService.handleToken(token)` → `Log.d("MoyeotaFcm", "FCM 토큰: …")`

**실기 결과 — 3회 클린 콜드 재시작 전부에서 토큰 로그 발화** (`am force-stop` → `logcat -c` → 콜드 스타트 → 8s 대기):
```
COLD START #A  pid 3972  D MoyeotaFcm: FCM 토큰: c4hgh5VkTKSOj-6Fjo…  (crash 0)
COLD START #B  pid 4033  D MoyeotaFcm: FCM 토큰: c4hgh5VkTKSOj-6Fjo…  (crash 0)
COLD START #C  pid 4098  D MoyeotaFcm: FCM 토큰: c4hgh5VkTKSOj-6Fjo…  (crash 0)
```
**1차 리포트 §A2의 실패 케이스(재실행 시 토큰 로그 미발화)가 해소됨.** 콜백(onNewToken/onRegistered)이 발화하지 않는 재실행에서도 명시 조회 경로가 매번 토큰을 흘려보낸다. 최초 설치 실행에서는 명시 조회 + 신규발급 콜백이 겹쳐 로그가 2회 찍혔다(정상, `handleToken` 단일 수렴).

> **관찰사항 O1 (블로킹 아님, 후속 백엔드 연동 시 유의):** 권한이 아직 거부 상태인 채로 콜드 스타트하면(=A3 가드가 config change만 막고 fresh launch에서는 다시 권한 다이얼로그를 띄우는 정상 동작), 앱 프로세스가 `GrantPermissionsActivity` 뒤에서 백그라운드로 밀리며 종료돼 **비동기 토큰 조회가 완료 전에 끊겨 토큰 로그가 누락**되는 경우를 재현했다(권한 부여 전 relaunch 2/3). 권한 부여 후에는 3회 모두 안정적으로 발화. 현재 `handleToken`이 로깅뿐이라 무해하나, 서버 등록을 붙일 때는 **Application.onCreate의 비동기 토큰 조회가 프로세스 조기 종료로 유실될 수 있음**을 감안해 실패 시 재시도/WorkManager 등록을 고려할 것. 프로덕션 통상 경로(최초 설치 1회만 다이얼로그)에서는 영향 미미.

---

### A3. 권한 요청 1회성 가드 — 통과 (실기)

**대상 코드** `MainActivity.onCreate` `if (savedInstanceState == null) requestNotificationPermissionIfNeeded()`

**재현 절차 & 결과**
1. 최초 실행 → 권한 다이얼로그 표시 (`03_a3_first_launch_perm_dialog.png`)
2. "Don't allow" 탭 → `POST_NOTIFICATIONS: granted=false, flags=[USER_SET|…]` (사용자 거부 1회 확정), `03_a3_after_deny.png`
3. **구성 변경 트리거: 화면 회전** `settings put system user_rotation 1`
   - Activity 재생성 로그 확인: `PrimesLogger… OnConfigurationChanged`, `WindowManager: finishDrawing of relaunch: Window{…MainActivity} 543ms`, 최종 `ROTATION_90 / land` — **재생성이 실제로 일어났음을 증명**
   - 회전 후 상태: `topResumedActivity=com.moyeota/.app.MainActivity`, `mCurrentFocus=…MainActivity` — **권한 다이얼로그(GrantPermissionsActivity) 재등장 없음**
   - 스크린샷 `03_a3_after_rotate_landscape.png` — 온보딩 화면 랜드스케이프, 다이얼로그 없음

**판정: 통과.** config change로 Activity가 재생성돼도(savedInstanceState != null) 권한 재요청이 나가지 않는다. 1차 리포트 §A3 결함 해소.

> 참고(정상 동작): fresh 콜드 스타트(force-stop 후 재실행)는 savedInstanceState == null 이므로, 권한이 미부여 상태면 다이얼로그가 다시 뜬다. 이는 "config change 재요청 차단"이라는 A3 스펙 범위를 벗어나지 않으며, Android 13의 2회 거부→영구거부는 config change 경로에서만 차단하면 되는 요구였다.

---

## 2. 미검증 (UNVERIFIED) — 환경 제약 유지

### U1/U2. 실제 알림 발송 → `onMessageReceived` → 알림 표시 → ic_notification 실렌더링
**시도:** 백엔드 레포(`/Users/sungyoon/Desktop/moyeota/backend`)에서 Firebase Admin 서비스 계정 JSON을 탐색(`grep -rl private_key`, `find -iname *.json`)했으나 **자격증명 없음**. FCM 연동부는 스텁이다:
```
backend/.../dispatch/infrastructure/LoggingCallNotifier.java
  /** FCM(Firebase cloud messaging) 연동 전까지 로그로 대체해서 구현 */
  public void notifyCall(...) { log.info("[콜 알림] …"); }
```
→ 인증된 FCM HTTP v1 푸시를 보낼 수 없어 U1/U2 실행 불가. 1차 리포트 U2에서 확인된 SDK 보안 검사(shell/root 인텐트 주입 거부)도 그대로 유효. **Firebase 콘솔 수동 발송(사용자 작업)으로만 해소 가능.** 콘솔 발송 시 P7/2차의 토큰(`c4hgh5VkTKSOj-6Fjo…`)으로 보내면 notification/data 폴백·NotificationCompat 표시·PendingIntent 진입·A1 아이콘 실루엣이 한 번에 검증된다.

### U3. GMS 버전 경고의 delivery 영향 / U4. data 모듈 유닛테스트
1차 리포트와 동일(변동 없음). 이번 변경과 무관하여 재검증 생략.

---

## 3. 스크린샷

| 경로 | 내용 |
|------|------|
| `_workspace/screenshots/03_a3_first_launch_perm_dialog.png` | 최초 실행 권한 다이얼로그 (벨 아이콘) |
| `_workspace/screenshots/03_a3_after_deny.png` | "Don't allow" 거부 직후 |
| `_workspace/screenshots/03_a3_after_rotate_landscape.png` | 회전(Activity 재생성) 후 — 다이얼로그 재등장 없음 |
| `_workspace/screenshots/03_a2_coldstart_permdialog_ontop.png` | 권한 미부여 콜드 스타트 시 다이얼로그 재표시(정상, O1 배경) |

---

## 4. 재현 절차

```bash
cd /Users/sungyoon/Desktop/moyeota/frontend
./gradlew :app:assembleDebug --console=plain
EMU=~/Library/Android/sdk/emulator/emulator; ADB=~/Library/Android/sdk/platform-tools/adb
nohup $EMU -avd Pixel_6 -no-snapshot-load -no-audio > /tmp/emulator.log 2>&1 &
$ADB wait-for-device; until [ "$($ADB shell getprop sys.boot_completed|tr -d '\r')" = 1 ]; do sleep 3; done
$ADB uninstall com.moyeota; $ADB install -r app/build/outputs/apk/debug/app-debug.apk

# A1: 패키징/참조
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep ic_notification
grep -rn setSmallIcon app/src/main/kotlin

# A3: 거부 후 회전 → 다이얼로그 재등장 없음
$ADB logcat -c; $ADB shell am start -n com.moyeota/com.moyeota.app.MainActivity
$ADB shell input tap 540 1490                       # Don't allow
$ADB shell settings put system accelerometer_rotation 0
$ADB shell settings put system user_rotation 1      # 회전 → 재생성
$ADB shell dumpsys activity activities | grep topResumedActivity   # …/.app.MainActivity 여야 함

# A2: 권한 부여(다이얼로그 confound 제거) 후 콜드 재시작마다 토큰 로그
$ADB shell pm grant com.moyeota android.permission.POST_NOTIFICATIONS
for i in A B C; do
  $ADB shell am force-stop com.moyeota; sleep 2; $ADB logcat -c
  $ADB shell am start -n com.moyeota/com.moyeota.app.MainActivity >/dev/null; sleep 8
  echo "#$i"; $ADB logcat -d | grep MoyeotaFcm
done
```

---

## 5. 담당자 액션 (코드 수정 요청 — 이번 검증에서 수정 안 함)

- **결함성 수정 요청 없음.** A1/A2/A3 모두 스펙대로 반영 확인.
- **(선택, api-integrator 후속)** O1 — 백엔드 토큰 등록 엔드포인트 연결 시, `MoyeotaApplication.fetchFcmToken()`의 비동기 조회가 프로세스 조기 종료로 유실될 수 있으므로 실패/미완료 시 재시도(WorkManager 등) 경로를 함께 설계할 것.
- **(사용자 수동)** U1/U2 — Firebase 콘솔에서 2차 토큰으로 테스트 발송해 알림 실표시 + ic_notification 실루엣 최종 확인.
