---
name: verify
description: 모여타 Android 앱 변경을 에뮬레이터에서 실제 구동해 확인하는 레시피
---

# 모여타 프론트엔드 검증 레시피

## 빌드 & 유닛 테스트
```bash
./gradlew :app:assembleDebug :data:testDebugUnitTest --console=plain
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## 백엔드 (서버 연동 화면 검증 시)
- 백엔드 레포: `../backend` (Spring, localhost:8080). 이미 떠 있는지 먼저 확인:
  `curl -s http://localhost:8080/api/matching/rooms`
- 데이터 시드: `backend/http/matching.http` 의 요청을 curl 로 실행 (인메모리라 서버 재시작 시 초기화)
- 에뮬레이터에서 호스트 접근 주소는 `http://10.0.2.2:8080` (AppContainer 에 하드코딩)

## 에뮬레이터
```bash
EMU=~/Library/Android/sdk/emulator/emulator; ADB=~/Library/Android/sdk/platform-tools/adb
$EMU -list-avds                       # Pixel_6 존재
nohup $EMU -avd Pixel_6 -no-snapshot-load -no-audio > /tmp/emulator.log 2>&1 &   # 콜드부트 필수 (사용자 지침)
$ADB wait-for-device                  # 이후 sys.boot_completed=1 폴링
$ADB install -r app/build/outputs/apk/debug/app-debug.apk
$ADB shell am start -n com.moyeota/com.moyeota.app.MainActivity
$ADB exec-out screencap -p > /tmp/scr.png
```

## 화면 이동 (1080x2400 기준 탭 좌표)
- 온보딩 「건너뛰기」(948,186) → 로그인 「로그인」(685,1988) → 홈
- 하단탭: 홈(173,2262) · 합승(416,2262) · 채팅(660,2262) · 마이(905,2262)
- 합승: 시트 스와이프업 `swipe 540 2040 540 1000 300` → 카드 「합류」 → 합류확인 「이 탑승에 합류하기」(702,2256) → 탑승 상세
- 네트워크 에러 프로브: `adb shell cmd connectivity airplane-mode enable|disable`

## 주의
- Explore 목록은 화면 재진입(홈탭↔합승탭) 시에만 다시 로드된다 (ViewModel init).
- 백엔드는 사용자가 직접 띄워둔 프로세스일 수 있으니 함부로 죽이지 말 것.
