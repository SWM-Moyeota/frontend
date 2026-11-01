# 01 QA 환경 준비 — API 29 키보드 측정 기기

작성: 2026-10-28 / qa-verifier
범위: **환경 준비만.** 빌드·앱 설치·소스 수정 없음. 실제 imePadding 측정은 다음 단계.

## 요약

| 항목 | 결과 |
|------|------|
| API 29 시스템 이미지 설치 | 성공 (53초, 3.1GB) |
| `Pixel_6_API29` 콜드부트 | 성공 (11초) |
| `ro.build.version.sdk` 실측 | **29** (release 10) |
| `hw.keyboard` | **no** (프레임워크 `nokeys` 확인) |
| API 37 `Pixel_6_QA` 부팅 | 성공 (sdk=37) |
| 두 기기 동시 기동 | **가능** (현재 둘 다 기동 중) |
| 소프트 키보드 실제 상승 | 양 기기 모두 실측 확인 |

## 1. 시스템 이미지 설치

```
sdkmanager "system-images;android-29;google_apis;arm64-v8a"
start: 2026-10-28 17:28:41 / end: 17:29:34 / exit=0   → 53초
```
- 설치 경로: `~/Library/Android/sdk/system-images/android-29/google_apis/arm64-v8a/` (3.1GB, system.img 3.23GB)
- 패키지: `arm64-v8a-29_r13.zip`, tag=`google_apis`
- GMS 확인: `com.google.android.gms` **versionName=20.24.14** + `com.android.vending` 존재 → 네이버 지도 SDK·Firebase 전제 충족
- 기존 `android-37.0` 이미지는 그대로. 건드리지 않음.

## 2. AVD 생성 — `Pixel_6_API29`

```
avdmanager create avd -n Pixel_6_API29 -k "system-images;android-29;google_apis;arm64-v8a" -d pixel_6
```
생성 중 `Could not load devices from .../devices.xml` 에러가 출력되나 **무해**(이미지에 자체 device 정의가 없을 때 나오는 메시지). AVD 는 정상 생성됨.

`-d pixel_6` 덕에 기존 `Pixel_6` 와 화면 제원이 자동 일치:

| 항목 | Pixel_6 (기존) | Pixel_6_API29 (신규) |
|------|----------------|----------------------|
| `hw.lcd.density` | 420 | 420 |
| `hw.lcd.width/height` | 1080 / 2400 | 1080 / 2400 |
| `hw.initialOrientation` | portrait | portrait |
| `hw.keyboard` | **yes** | **no** ← 측정 목적상 핵심 |

생성 후 수동 조정 (원본은 `config.ini.orig` 로 백업):
- `avd.id` / `avd.name`: `<build>` 플레이스홀더 → `Pixel_6_API29`
- `disk.dataPartition.path=<temp>` 줄 삭제 (userdata 가 AVD 디렉터리에 영속되도록)
- `hw.gpu.enabled`: no → yes (`Pixel_6` 와 동일)
- `showDeviceFrame`: yes → no (스킨 미설정, 스크린샷은 1080x2400 원본 그대로)

`hw.keyboard=no` 는 avdmanager 기본값이라 별도 수정 불필요했음.

## 3. 콜드부트 확인

```
emulator -avd Pixel_6_API29 -no-snapshot-load -no-audio -port 5556
17:30:41 기동 → 17:30:52 sys.boot_completed=1   (11초)
```
실측값:
```
ro.build.version.sdk     = 29
ro.build.version.release = 10
wm size                  = 1080x2400
wm density               = 420
```

## 4. 소프트 키보드 상승 실측 (가장 중요한 사전 확인)

`hw.keyboard=no` 가 실제로 먹었는지 프레임워크 레벨에서 확인:

```
adb -s emulator-5556 shell am get-config
  → keysexposed nokeys navhidden nonav      # nokeys = 하드웨어 키보드 없음
adb -s emulator-5554 shell am get-config
  → keysexposed qwerty navexposed           # API37 은 하드웨어 키보드 있음
```

실제 텍스트 필드(연락처 추가)에 탭해서 IME 상승까지 확인:
```
adb -s emulator-5556 shell dumpsys input_method | grep mInputShown
  → mShowRequested=true mShowExplicitlyRequested=true mInputShown=true
```
스크린샷: `/private/tmp/claude-501/-Users-sungyoon-Desktop-moyeota-frontend/1b791af6-8c40-4510-9b30-44b1700ea413/scratchpad/api29_ime2.png`
Gboard 가 화면 하단을 덮고 올라옴. 키보드 상단 경계 ≈ y=1536 (1080x2400 기준) → 하단 약 860px 점유.

## 5. [주의] API 37 비교군은 그대로 두면 측정 불가

`Pixel_6_QA` 는 부팅 자체는 성공했으나(`sdk=37`, release 17), **`hw.keyboard=yes`(=`qwerty`) + `show_ime_with_hard_keyboard=0`** 이라 탭해도 소프트 키보드가 올라오지 않는다. 이 상태로 비교 측정하면 회귀 비교군이 무의미해진다.

기존 AVD 설정은 지시대로 수정하지 않고, **런타임 설정으로 우회**(되돌릴 수 있음):
```
adb -s emulator-5554 shell settings put secure show_ime_with_hard_keyboard 1   # 원래 값 0
```
적용 후 동일 절차로 소프트 키보드 상승 확인 완료 (`mInputShown=true`).
스크린샷: `.../scratchpad/api37_ime.png`

- 원복: `adb -s emulator-5554 shell settings put secure show_ime_with_hard_keyboard 0`
- 주의: 이 방식은 `Configuration.keyboard` 는 여전히 `qwerty` 로 남는다. insets 측정에는 영향 없지만, 실기와 완전히 동일한 조건을 원하면 `hw.keyboard=no` 인 API 37 AVD 를 새로 만드는 쪽이 깔끔하다 (기존 AVD 는 건드리지 않는다는 지시 때문에 선택하지 않음).

## 6. 동시 기동

두 기기 동시 기동 **가능**. 현재 둘 다 떠 있는 상태로 남겨둠.
```
emulator-5554  device   # Pixel_6_QA,     API 37
emulator-5556  device   # Pixel_6_API29,  API 29
```
호스트: 24GB RAM / 12 core. 동시 기동 시 free 35%, qemu 프로세스 각 ~1GB RSS.
- 단, 두 에뮬레이터 모두 기동 로그에 `Software GL rendering will be used due to system memory pressure (Available: ~4.5GB, Required: 5120MB)` 경고 → **소프트웨어 렌더링으로 폴백**. 스크린샷 캡처는 양쪽 모두 1080x2400 PNG 로 정상 동작 확인했으므로 측정에는 지장 없음. 다만 렌더링이 느리니 탭 후 대기시간을 넉넉히(4~5초) 잡을 것.
- 스왑 사용량이 9.1GB/10.2GB 로 높다. 측정 중 체감 저하가 심하면 순차 기동(한쪽 종료 후 다른 쪽)으로 전환 권장.

## 7. 전제 사실 재확인 (읽기만, 수정 없음)

- `app/src/main/AndroidManifest.xml:36` — `android:windowSoftInputMode="adjustNothing"` **보존됨**
- `app/build.gradle.kts:85` — `minSdk = 24` (compileSdk 37 / targetSdk 36)
- `imePadding()` 호출 **10개 파일** 확인:
  - auth 7개: `LoginFormScreen:78`, `SchoolEmailScreen:84`, `EmailCodeScreen:117`, `WorkVerifyScreen:89`, `IdentityVerifyScreen:108`, `ProfileSetupScreen:213`
  - 기타: `home/DestinationScreen:133`, `chat/ChatScreen:224`, `mypage/ProfileEditScreen:73`, `mypage/AccountDeleteScreen:104`
- 추가 측정 포인트: `chat/ChatScreen.kt:200` 이 `WindowInsets.ime.getBottom(LocalDensity.current)` 을 직접 읽는다. API 29 에서 이 값이 0 으로 고정되는지 로그로 뽑으면 가설을 수치로 확정할 수 있다. 같은 파일 199행 주석에 "imePadding 이 없으면 입력 바와 하단탭이 키보드 뒤로 숨는다(실기 QA)" 가 이미 적혀 있음.

## 8. 하지 않은 것

- Gradle 빌드 / 앱 설치 — 지시대로 미실행
- 소스 파일 수정 — 없음 (`AndroidManifest.xml` 포함)
- `git checkout/stash/restore` — 미실행. 작업 트리 미커밋 변경 그대로.
- 기존 AVD(`Pixel_6`, `Pixel_6_QA`, `Driver_New`, `Pixel_6_Driver`) config 수정 — 없음 (5번의 런타임 secure 설정 1건만, 원복 가능)
- 백엔드 프로세스 — 현재 **미기동** (`curl localhost:8080` connection refused). 죽이거나 띄우지 않음. 서버 연동 화면 검증이 필요하면 별도 기동 필요.

## 다음 단계 준비 상태

에뮬레이터 2대 모두 기동 상태로 유지 중. APK 만 나오면 바로 설치·측정 가능.
```
adb -s emulator-5556 install -r app/build/outputs/apk/debug/app-debug.apk   # API 29
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk   # API 37 비교군
```
