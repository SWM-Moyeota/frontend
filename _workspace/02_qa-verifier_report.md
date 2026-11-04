# Pretendard QA 결과

## 통과
- 리더 실행: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:assembleDebug :data:testDebugUnitTest --console=plain` — BUILD SUCCESSFUL. 단위 테스트 UP-TO-DATE.
- APK를 emulator-5554 (Pixel_6, 1080×2400)에 설치하고 MainActivity 실행 성공.
- 시작 인증 화면의 한국어 제목, 본문, 설명, 버튼 글자가 정상 렌더링되며 잘림/겹침 없음.
- 생산자/소비자 정적 비교: PretendardFontFamily → MoyeotaType 13개 스타일 및 Material Typography 15개 스타일 → MoyeotaTheme 연결 확인.
- 앱에서 사용하는 굵기 Normal/Medium/SemiBold/Bold와 포함된 정적 폰트 4개가 일치.
- BasicTextField 6개(공통 입력, 인증번호, 목적지, 채팅 입력/검색/신고 설명)의 textStyle이 Pretendard 설정을 참조함을 확인. 목적지 입력은 기존 TextStyle에 fontFamily만 추가해 행간 유지.

## 실패
- 코드 결함 발견 없음.

## 제한 및 미검증
- `Pixel_6 -read-only -no-snapshot-load -no-snapshot-save -port 5556` 별도 콜드부트 시도는 기존 인스턴스가 read-only가 아니어서 실패. 기존 사용자 인스턴스를 종료하지 않고 실행 중 emulator-5554에서 검증했으므로 콜드부트 조건은 미충족.
- 인증 이후 화면 및 실제 입력된 글자 렌더링은 실기 미검증. 인증 자격증명을 사용하지 않음. 시작 화면 범위에서만 시각 검증.
- 지도 SDK 자체 라벨 및 Android 시스템 UI 글꼴은 앱 Compose 테마 대상 밖.

## 스크린샷
- `_workspace/screenshots/pretendard-launch.png` — 설치 후 시작 인증 화면.
- `_workspace/screenshots/pretendard-login.png` — 후속 캡처. 로그인 링크 탭 이후에도 동일 시작 화면이므로 로그인 입력 화면 검증으로 간주하지 않음.

## 최신 develop 기준 재검증
- 기준: `origin/develop` 커밋 `8e7f7aa` 위 Pretendard 변경 재적용 후 정적 재검증.
- 새 채팅 신고 설명 입력(`ChatScreen.kt`)은 `MoyeotaType.BodyMd.copy`를 사용하므로 Pretendard 적용됨. 실제 BasicTextField 호출은 총 6개이며 모두 폰트 경로 연결 확인.
- 앱 텍스트는 Material3 Text를 사용하며, 별도 테마/폰트 override 누락 없음. 사용 굵기는 Normal/Medium/SemiBold/Bold로 리소스와 일치.
- 최신 develop 기준 리더 빌드 결과: BUILD SUCCESSFUL (1분 17초). `data:testDebugUnitTest` 실제 실행, XML 합산 465 tests / failures 0 / errors 0 / skipped 0.
- 최신 APK 실기 재검증 미완료: `adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk` 도구 호출이 약 194초 동안 응답 없이 대기하여 리더가 중단함. adb install 장시간 응답 없음으로 설치 결과 확인 불가. 성공 출력이 없어 설치 완료 여부를 확인할 수 없으며, 최신 코드 기준 시작 화면 검증도 수행하지 못함.
- 위 설치/화면 통과 및 스크린샷은 rebase 이전 최초 구현의 결과이며, 최신 develop 기준 실기 통과로 간주하지 않음. 추가 ADB 호출 없이 정적 검사와 빌드/단위 테스트 결과로 이번 재검증을 마무리함.
