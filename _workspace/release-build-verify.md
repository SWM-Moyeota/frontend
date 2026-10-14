# 릴리스 산출물 로컬 검증 리포트

- 일시: 2026-10-14
- 브랜치: `ci/version-code` (작업 트리 깨끗 — `.DS_Store` 변경만 있음)
- 검증자: qa-verifier
- 환경: macOS 24.6.0, JDK 25.0.2 (Temurin), Gradle 9.4.1, AGP 9.2.1, build-tools 37.0.0
- **코드는 한 줄도 고치지 않았다.** 산출물(.aab/.apk/.jks)은 git 에 올리지 않았다 (`.gitignore` 22·23·28·29 줄이 이미 막고 있고, `git ls-files` 에도 없음).

---

## 0. 한 줄 결론

**업로드 가능하다.** 서명·보안 설정 4개 항목 전부 통과, 단위 테스트 554개 전부 통과.
다만 **문서대로 `./gradlew :app:bundleRelease` 만 치면 `versionCode=1` 이 박힌 AAB 가 나온다** — 그대로 올리면 안 된다 (F-1).

---

## 1. 빌드

| 항목 | 결과 |
|---|---|
| `./gradlew :app:bundleRelease :app:assembleRelease --console=plain` | **BUILD SUCCESSFUL** (29s, exit 0) |
| `lintVitalRelease` | 통과 (릴리스 차단성 lint 없음) |
| `signReleaseBundle` / `packageRelease` 태스크 | 실행됨 → 서명 경로가 살아 있다는 뜻 |

ANDROID_HOME 환경변수는 셸에 없었으나 `local.properties` 의 `sdk.dir=/Users/sungyoon/Library/Android/sdk` 로 Gradle 은 정상 동작했다. (apksigner/aapt2 직접 호출용으로만 `ANDROID_HOME` 을 수동 지정했다.)

### 1-1. 서명 설정이 실제로 켜졌는가 — 가장 먼저 확인한 것

`app/build.gradle.kts:56-60` 의 `releaseSigningReady` 는 네 값 + 키스토어 파일 존재를 모두 요구한다. 하나라도 비면 `signingConfigs` 에 `release` 가 **안 만들어지고** 서명 없는 산출물이 조용히 나온다.

| 키 (`local.properties`) | 상태 |
|---|---|
| `MOYEOTA_KEYSTORE_PATH` | 설정됨 → `/Users/sungyoon/moyeota-upload.jks` |
| `MOYEOTA_KEYSTORE_PASSWORD` | **설정됨** (값은 기록하지 않음) |
| `MOYEOTA_KEY_ALIAS` | 설정됨 → `moyeota-upload` |
| `MOYEOTA_KEY_PASSWORD` | **설정됨** (값은 기록하지 않음) |
| 키스토어 파일 실존 | 예 (4,424 바이트) |

→ `releaseSigningReady = true`. **서명 없는 산출물이 나오는 상태가 아니다.** 아래 2번에서 산출물로 직접 재확인했다.

---

## 2. 서명 검증 — `apksigner` (jarsigner 아님)

`jarsigner -verify` 는 minSdk 24 의 v2 전용 서명 APK 에 대해 "jar is unsigned" 를 찍으면서 **종료코드 0** 을 주므로 APK 검증에 쓰지 않았다. AAB 는 v1(JAR) 서명이라 jarsigner 가 적합해 그쪽만 보조로 썼다.

```
$ANDROID_HOME/build-tools/37.0.0/apksigner verify --verbose --print-certs \
    app/build/outputs/apk/release/app-release.apk
```

```
Verifies
Verified using v1 scheme (JAR signing): false
Verified using v2 scheme (APK Signature Scheme v2): true     ← v2 서명 붙음
Verified using v3 scheme: false
Number of signers: 1
```

**v2 서명이 정상적으로 붙었다.** (v1=false 는 minSdk 24 에서 정상 — AGP 가 의도적으로 v1 을 생략한다.)

### 인증서 — 플레이 콘솔 등록용

| 항목 | 값 |
|---|---|
| **인증서 SHA-256 지문** | `FD:71:67:6F:96:BE:3A:20:24:F6:F2:61:E5:5F:16:E5:61:76:59:DA:F1:8A:05:52:2D:9D:DC:DF:80:B7:96:E1` |
| 인증서 SHA-256 (콜론 없음) | `fd71676f96be3a2024f6f261e55f16e5617659daf18a05522d9ddcdf80b796e1` |
| 인증서 SHA-1 | `BD:9E:1D:BE:FF:16:38:2B:C0:91:9C:FA:5A:E2:FC:01:E6:A7:0F:E3` |
| 공개키 SHA-256 | `bda042cbc233cf0dd589fffcd162c4397a5a0ae634a77f5fc3c493159a8ae869` |
| DN | `CN=황성윤, OU=Unknown, O=Unknown, L=Unknown, ST=Unknown, C=KR` |
| 키 | RSA 4096-bit, SHA384withRSA |
| 유효기간 | 2026-10-13 ~ **2054-02-28** |

> **이 지문은 「업로드 키」의 것이다.** `docs/RELEASE.md` 5-2 대로, AAB 를 처음 올리면 Play 앱 서명이 켜지고 사용자 기기에서 검증되는 **앱 서명 키는 Google 이 따로 만든다**. 5-1 「서명 키 등록」과 Firebase 푸시 설정에 넣어야 하는 것은 Play Console 「설정 → 앱 서명」 화면에 뜨는 **앱 서명 키** 지문이지 위 값이 아니다. 위 지문은 업로드 키를 식별·대조할 때 쓴다.

> 유효기간은 Play 요구치(2033-10-22 이후)를 **27년 여유로 넘긴다.** 통과.

### AAB 서명

```
$ unzip -l app-release.aab | grep META-INF
  META-INF/MOYEOTA-.SF / MOYEOTA-.RSA / MANIFEST.MF   ← 모두 존재
$ jarsigner -verify app-release.aab
jar verified.
```

---

## 3. AAB

| 항목 | 값 |
|---|---|
| 경로 | `/Users/sungyoon/Desktop/moyeota/frontend/app/build/outputs/bundle/release/app-release.aab` |
| 크기 | **41,109,522 바이트 (39 MB)** — Play 업로드 한도에 한참 못 미침 |
| 서명 | v1(JAR) 서명됨, `jar verified` |
| 엔트리 수 | 773 |
| 포함 ABI | arm64-v8a, armeabi-v7a, x86, x86_64 (AAB 라 Play 가 기기별로 쪼개 내려줌) |
| dex | `base/dex/classes.dex` 외 4개 (총 약 32MB) |

APK 쪽: `app/build/outputs/apk/release/app-release.apk`, **108,684,733 바이트 (104 MB)**.

### 매니페스트 값 — AAB 안에서 직접 읽음

`bundletool` 이 설치돼 있지 않아(`apkanalyzer` 도 AAB 의 protobuf 매니페스트를 못 읽음) **AAB 안의 `base/manifest/AndroidManifest.xml`(protobuf) 을 직접 디코드**해 읽었다. 디코더: `/private/tmp/.../scratchpad/aabmanifest.py` (aapt2 `Resources.proto` 의 `XmlNode/XmlElement/XmlAttribute/Item/Primitive` 를 최소 파싱).

**(가) 문서의 기본 명령 그대로 빌드했을 때** — `./gradlew :app:bundleRelease`

```
<manifest>
    package = 'com.moyeota'
    android:versionCode = '1'      [compiled=1]      ← 기본값
    android:versionName = '1.0'                      ← 기본값
```

**(나) CI 와 같은 방식으로 버전을 주입했을 때** — `MOYEOTA_VERSION_CODE=26101401 MOYEOTA_VERSION_NAME=2026.1014.1 ./gradlew :app:bundleRelease`

```
<manifest>
    package = 'com.moyeota'
    android:versionCode = '26101401'   [compiled=26101401]   ← 날짜 기반(261014*100+01) 정상 주입
    android:versionName = '2026.1014.1'
```

→ **주입 경로 자체는 정상 동작한다.** 다만 주입하지 않으면 1/1.0 이 박힌다 (F-1).
현재 디스크에 남아 있는 AAB·APK 는 **(나) 버전**(26101401 / 2026.1014.1)이고 둘 다 서명 확인을 마쳤다.

기타 매니페스트 값: `minSdk=24`, `targetSdk=36`, `compileSdk=37`.
(`compileSdk=37` 은 **정식 출시 SDK** 다 — `platforms/android-37.0/source.properties` 의 `Platform.CodeName=` 비어 있음, `AndroidVersion.PreviewSdkInt=0`, `IsBaseSdk=true`. 프리뷰 SDK 로 인한 업로드 거부 사유에 해당하지 않는다.)

---

## 4. 릴리스에 들어가면 안 되는 것 — 산출물에서 직접 확인

### 4-1. HTTP 본문 로깅 — **통과(런타임 기준)**, 단 바이트코드는 남아 있음

- 게이트: `data/src/main/kotlin/com/moyeota/data/remote/NetworkModule.kt:49-54` — `if (debugLogging)` 일 때만 `HttpLoggingInterceptor(Level.BODY)` 를 끼운다.
- 주입: `app/src/main/kotlin/com/moyeota/app/MoyeotaApplication.kt:27,35-36` — `debugLogging = isDebuggableBuild()` = `applicationInfo.flags and FLAG_DEBUGGABLE != 0`.
- **릴리스 매니페스트에 `android:debuggable` 속성이 없다**(APK·AAB 양쪽에서 확인). → `FLAG_DEBUGGABLE = false` → 인터셉터가 **붙지 않는다**. 토큰·채팅 본문이 logcat 에 남지 않는다.

> 참고(결함 아님, F-2 로 분리): `okhttp3/logging/HttpLoggingInterceptor` 클래스 자체는 `classes5.dex` 에 **들어 있다**(참조 10건). R8 이 꺼져 있어 안 쓰이는 코드가 안 지워지기 때문이다. 실행 경로가 없으므로 동작상 위험은 없다.

### 4-2. `usesCleartextTraffic` — **통과**

```
# 릴리스 APK 매니페스트 전체에서 cleartext 검색 → 0건
# 릴리스 AAB <application> 속성 목록 → usesCleartextTraffic 없음
```

`android:usesCleartextTraffic="true"` 는 `app/src/debug/AndroidManifest.xml` 에만 있고 `src/main` 에는 없다. 릴리스는 targetSdk 36 기본값(평문 차단)이 적용된다. 확인된 릴리스 `<application>` 속성은 `allowBackup / appComponentFactory / dataExtractionRules / extractNativeLibs / fullBackupContent / icon / label / name / roundIcon / supportsRtl / theme` 뿐이다.

덧붙여 산출물에 박힌 BASE_URL 을 dex 문자열에서 확인했다:

```
https://api.moyeota.p-e.kr/        ← 운영 서버, https
```

`10.0.2.2` / `192.168.*` / `http://10.*` 같은 평문 로컬 주소는 **0건**. (`http://localhost/` 한 건이 보이나 OkHttp/Retrofit 내부 기본 문자열이고 우리 코드 값이 아니다.) `local.properties` 에 `MOYEOTA_BASE_URL` 이 아예 없어 `app/build.gradle.kts:36-38` 의 기본값이 쓰였다 — `docs/RELEASE.md` 4-1 의 경고 상황(로컬 주소 박힌 채 릴리스)에 해당하지 않는다.

### 4-3. `allowBackup` / 백업 규칙 — **통과**

릴리스 매니페스트:
```
android:allowBackup = 'true'
android:fullBackupContent     = @xml/backup_rules          (0x7f120000)
android:dataExtractionRules   = @xml/data_extraction_rules (0x7f120001)
```

`allowBackup="true"` 지만, **두 규칙 파일이 모든 도메인을 제외**하므로 실제로 백업되는 것이 없다. 리소스 경로가 난독화(`res/Qq.xml`, `res/4j.xml`)돼 있어 `aapt2 dump resources` 로 id→경로를 역추적한 뒤 **컴파일된 내용을 직접 디코드**했다:

| 파일 | 제외된 도메인 |
|---|---|
| `backup_rules.xml` (API<31) | `file`, `sharedpref`, `database`, `external`, `root`, `device_file`, `device_sharedpref`, `device_database`, `device_root` — **9개 전부** |
| `data_extraction_rules.xml` → `<cloud-backup>` (API≥31) | 위와 동일 **9개 전부** |
| `data_extraction_rules.xml` → `<device-transfer>` | 위와 동일 **9개 전부** |

→ `files/datastore/moyeota_session.preferences_pb` 의 **평문 리프레시 토큰이 구글 드라이브 백업에도, 기기 간 직접 전송에도 포함되지 않는다.** `docs/RELEASE.md` 7-2 의 의도대로 산출물에 반영됐다.

### 4-4. 디버그 전용 UI(ModeDebugSection) — **화면에는 안 뜬다(통과)**, 단 바이트코드는 남아 있음

- 게이트: `presentation/src/main/kotlin/com/moyeota/presentation/core/MainNavGraph.kt:152` 에서 `val debuggable = (applicationInfo.flags and FLAG_DEBUGGABLE) != 0`, 같은 파일 `806-815` 에서 `modeDebug = if (debuggable) ModeDebugOptions(...) else null`.
- 소비: `presentation/.../mypage/MyPageScreen.kt:221-224` — `if (modeDebug != null)` 일 때만 `ModeDebugSection` 을 그린다.
- 릴리스에 `debuggable` 플래그가 없으므로 `modeDebug = null` → **35 마이페이지에 개발자 옵션이 렌더되지 않는다.**

> 참고(F-2): `ModeDebugSection` 메서드와 `"개발자 옵션 · 디버그 빌드에만 보여요"` 문자열은 `classes5.dex` 에 **들어 있다**. 역시 R8 이 꺼져 있어서다. 도달 불가능한 코드라 UI 에는 영향이 없다.

---

## 5. 단위 테스트

| 모듈 | 결과 |
|---|---|
| `:data:testDebugUnitTest` | **278개 전부 통과** (failures 0, errors 0, skipped 0) |
| `:presentation:testDebugUnitTest` | **276개 전부 통과** (failures 0, errors 0, skipped 0) |
| `:domain:testDebugUnitTest` | **NO-SOURCE** — 테스트가 하나도 없다 (F-3) |
| `:app:testDebugUnitTest` | **NO-SOURCE** — 테스트가 하나도 없다 (F-3) |
| 합계 | **554 통과 / 0 실패** |

> 첫 실행에서 `UP-TO-DATE`, 두 번째에서 `FROM-CACHE` 가 떠 **실제로는 아무것도 돌지 않았다.** `--no-build-cache --rerun-tasks` 와 결과 디렉터리 삭제로 **강제 재실행해서 얻은 수치**다. 위 숫자는 이번에 실제로 돈 결과다.

---

## 결함 / 개선 요청

### F-1 (중) — 문서의 기본 명령으로 빌드하면 `versionCode=1` 이 박힌다

- **위치:** `docs/RELEASE.md:96-99` (4-1 절), 체크리스트 `docs/RELEASE.md:230-231`
- **증상:** 4-1 이 안내하는 `./gradlew :app:bundleRelease` 에는 버전 주입이 없다. `app/build.gradle.kts:66,69` 의 기본값이 쓰여 **`versionCode=1`, `versionName="1.0"`** 인 AAB 가 나온다. 실제로 그렇게 나오는 것을 AAB 매니페스트에서 확인했다(3절 (가)).
- **왜 문제인가:** 날짜 기반 `versionCode`(YYMMDD*100+순번)는 **CI 워크플로가 주입할 때만** 생긴다(`docs/RELEASE.md:131`). 스토어용 AAB 는 지금 **로컬에서만** 만들므로(같은 문서 141줄), 안내된 명령을 그대로 따르면 1 이 올라간다. Play 는 같은 versionCode 를 두 번 받지 않으므로 **1 을 한 번 써 버리면 그 값을 영영 못 쓰고**, 다음 업로드는 2 이상이어야 해서 CI 가 쓰는 2610xxxx 체계와 번호가 엉킨다. 35 마이페이지에도 "1.0" 이 뜬다.
- **재현:** `cd /Users/sungyoon/Desktop/moyeota/frontend && ./gradlew :app:bundleRelease` → AAB 매니페스트의 `versionCode` 가 1.
- **고치는 법 (코드 수정 아님, 문서 수정):** `docs/RELEASE.md` 4-1 의 명령을 버전 주입형으로 바꾼다.
  ```bash
  # 당일 첫 빌드면 순번 01, 두 번째면 02 …
  MOYEOTA_VERSION_CODE=$(TZ=Asia/Seoul date +%y%m%d)01 \
  MOYEOTA_VERSION_NAME=$(TZ=Asia/Seoul date +%Y.%m%d).1 \
  ./gradlew :app:bundleRelease
  ```
  그리고 체크리스트 230줄 `[ ] versionCode 올림` 을 `[ ] MOYEOTA_VERSION_CODE / MOYEOTA_VERSION_NAME 를 주입해서 빌드했는지 (안 하면 1/1.0)` 로 바꾼다.
  **더 나은 방법:** `app/build.gradle.kts:66` 의 `default = "1"` 을 그대로 두되, 릴리스 빌드에서 주입이 없으면 실패시키는 체크를 두면 사람이 잊어도 막힌다. (코드 변경이라 이번 검증에서는 손대지 않았다.)
- **담당:** 릴리스 담당자 / 문서
- **검증 완료 사항:** 주입 경로 자체는 정상이다 — `MOYEOTA_VERSION_CODE=26101401` 주입 시 AAB 매니페스트에 `26101401` 이 그대로 박히는 것을 확인했다. `local.properties` 에 해당 키가 없어 `buildSetting`(`app/build.gradle.kts:11-21`)의 환경변수 폴백이 정상 동작한다.

### F-2 (하) — 릴리스에 R8 이 꺼져 있어 디버그 코드가 바이트코드로 남고 APK 가 104MB

- **위치:** `app/build.gradle.kts:104-107`
  ```kotlin
  release {
      optimization {
          enable = false      ← R8 축소/난독화/최적화 전부 꺼짐
      }
  ```
- **증상:** 산출물에서 직접 확인한 것 —
  - `okhttp3/logging/HttpLoggingInterceptor` 가 `classes5.dex` 에 남아 있음 (참조 10건)
  - `ModeDebugSection` 메서드와 `"개발자 옵션 · 디버그 빌드에만 보여요"` 문자열이 `classes5.dex` 에 남아 있음
  - 클래스명·파일명·줄번호가 그대로 (`com.moyeota.presentation.feature.mypage.ModeDebugSection (MyPageScreen.kt:379)`) — **난독화 없음**
  - 릴리스 APK 108,684,733 바이트 (104MB)
- **실제 위험도:** 낮다. 두 코드 모두 `FLAG_DEBUGGABLE` 로 막혀 **실행되지 않는다**(4-1·4-4 참고). "릴리스에서 동작하면 안 되는 것"은 전부 동작하지 않는다. 다만 **"포함되지 않는가"** 를 문자 그대로 보면 **포함돼 있다**.
- **부작용:** (1) APK 104MB — AAB(39MB)로 올리므로 Play 업로드에는 지장 없으나, `.github/workflows/release.yml` 이 GitHub Release 에 붙이는 APK 가 104MB 가 된다. (2) 난독화가 없어 앱 로직이 그대로 읽힌다. (3) 다운로드 용량이 커진다.
- **고치는 법:** `optimization { enable = true }` 로 켜고(= minify + 리소스 축소), Compose·kotlinx.serialization·Retrofit/OkHttp·네이버 지도 SDK 용 keep 규칙을 `proguard-rules.pro` 에 넣은 뒤 **릴리스 빌드를 실기로 한 바퀴 돌려** 리플렉션 깨짐이 없는지 확인해야 한다. 내부 테스트 1차 업로드 전에 꼭 해야 하는 일은 아니다 — **다만 켜는 순간 동작이 바뀔 수 있으므로 1차 업로드 직전에 하지 말고 별도 작업으로 잡는 편이 낫다.**
- **담당:** 빌드 설정 담당 (이번 업로드의 블로커 아님)

### F-3 (하) — `:domain` / `:app` 모듈에 단위 테스트가 하나도 없다

- **위치:** `domain/src/` 에 `main` 만 있고 `test` 없음. `app/src/` 에 `debug`·`main` 만 있고 `test` 없음.
- **증상:** `./gradlew :domain:testDebugUnitTest` 가 **NO-SOURCE 로 "성공"** 한다. 테스트가 0개인데 초록불이 떠서, 이 명령을 게이트로 쓰면 **아무것도 검증하지 않고 통과한다.**
- **고치는 법:** `domain` 의 순수 코틀린 로직(요금 분배·시간 계산 등)에 테스트를 붙이거나, 최소한 CI/문서에서 `:domain:testDebugUnitTest` 를 게이트로 세지 않는다. 지금 실질적인 커버리지는 `:data`(278) + `:presentation`(276) 뿐이다.
- **담당:** api-integrator(domain 로직) / 리더 판단

### 참고 (결함 아님)

- `docs/RELEASE.md:104-112` 의 "AAB 는 jarsigner, APK 는 apksigner" 안내는 **맞다.** 이번에 AAB 는 `jar verified`, APK 는 apksigner 로 v2 확인됐다. 문서 수정 불필요.
- `allowBackup="true"` 는 그대로 둬도 된다. 두 규칙 파일이 전 도메인을 막고 있고, `docs/RELEASE.md:217` 이 적은 대로 `allowBackup="false"` 로는 device-transfer 가 안 막히므로 지금 구조가 맞다.
- `compileSdk=37` 은 정식 SDK 라 업로드 거부 사유가 아니다(3절 참고).

---

## 아직 안 된 것 (이번 검증 범위 밖)

- **에뮬레이터 실기 구동 검증 안 함.** 이번 과제는 산출물 생성·검증 범위였다. 릴리스 빌드는 평문이 막혀 있어 로컬 백엔드(`10.0.2.2`)에 붙지 않으므로, 실기 확인은 **운영 서버가 뜬 상태에서 릴리스 APK 를 설치해** 해야 한다.
- **Play Console 업로드 자체는 안 해 봤다.** 로컬에서 만들 수 있는 데까지가 범위다.
- `docs/RELEASE.md:235-239` 의 남은 체크리스트(두 URL 등록, 패키지 등록, 앱 서명 키 지문 등록, Firebase SHA-1)는 전부 콘솔 작업이라 손대지 않았다.

## 업로드 전 해야 할 일 (우선순위)

1. **F-1 대로 버전을 주입해서 AAB 를 다시 만든다.** (지금 디스크에 있는 것은 이미 `26101401 / 2026.1014.1` 로 주입된 것이라 그대로 올려도 된다. 다만 올리기 직전에 순번을 다시 확인할 것.)
2. Play Console 「설정 → 앱 서명」에서 **앱 서명 키** SHA-256 을 받아 서명 키 등록에 넣는다 (위 2절의 업로드 키 지문과 헷갈리지 말 것).
3. 같은 화면의 **앱 서명 키 SHA-1** 을 Firebase 콘솔에 등록한다 — 안 하면 스토어로 받은 앱에서만 푸시가 안 온다.
4. F-2(R8)는 1차 내부 테스트 이후 별도 작업으로.
