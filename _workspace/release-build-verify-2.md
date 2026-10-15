# 릴리스 산출물 재검증 리포트 (2차)

- 일시: 2026-10-15
- 브랜치: `fix/place-roadname-null` @ `8738679` (작업 트리 **깨끗** — 검증 전후 `git status --porcelain` 빈 출력)
- 검증자: qa-verifier
- 환경: macOS 24.6.0, JDK 25.0.2 (Temurin), Gradle 9.5.1, build-tools 37.0.0, compileSdk 37
- **코드는 한 줄도 고치지 않았다.** `local.properties` 도 건드리지 않았다. 산출물(.aab/.apk/.jks)은 git 에 없다
  (`git ls-files` 에서 `.aab/.apk/.jks/.keystore` 0건, `app/.gitignore:1 /build` 가 두 산출물을 모두 무시).
- 비밀값은 적지 않는다 — 아래 1-1 은 경로와 「설정됨/비어있음」만 적는다.

---

## 0. 한 줄 결론

**업로드 가능하다.** 어제 대비 커밋 8개가 반영된 새 AAB 를 `versionCode=26101501` 로 만들었고,
서명·보안 4개 항목과 신규 기능 2개 항목 전부 통과, 단위 테스트 **621개(306+315) 전부 통과**(어제 554 → +67).

새로 발견된 결함 **없음**. 어제의 F-1(문서가 버전 주입을 안내하지 않던 문제)은 **해소됐다**(아래 7절).
어제의 F-2(R8 off)·F-3(:domain/:app 테스트 0개)은 그대로다 — 둘 다 이번 업로드의 블로커가 아니다.

---

## 1. versionCode 순번 — 쓰인 번호를 먼저 확인했다

| 번호 | 어디에 쓰였나 | 확인 방법 |
|---|---|---|
| `26101401` / `2026.1014.1` | **어제 CI 가 GitHub Release 로 공개** (`v2026.1014.1`, Latest) | `gh release list` + 디스크의 옛 `apk/release/output-metadata.json` |
| `26101402` / `2026.1014.2` | 어제 로컬 AAB | 옛 AAB `base/manifest/AndroidManifest.xml` protobuf → `2026.1014.2` |
| `26101501` / `2026.1015.1` | **비어 있음 → 이번에 사용** | `gh release list` 에 `v2026.1015.*` 없음, `app/build/outputs/` 에도 없음 |

`gh release list` 최신 14건 중 2026-10-15 자 릴리스는 없다. 오늘(KST) 첫 번째이므로 순번 `01` 이 맞다.

### 1-1. 서명 설정이 실제로 켜졌는지 (빌드 전 확인)

`app/build.gradle.kts:56-62` 의 `releaseSigningReady` 는 네 값 + 키스토어 파일 실존을 모두 요구한다.
하나라도 비면 `signingConfigs` 에 `release` 가 만들어지지 않고 **서명 없는 산출물이 조용히 나온다.**

| 키 (`local.properties`) | 상태 |
|---|---|
| `MOYEOTA_KEYSTORE_PATH` | 설정됨 → `/Users/sungyoon/moyeota-upload.jks` |
| `MOYEOTA_KEYSTORE_PASSWORD` | **설정됨** (값 미기록) |
| `MOYEOTA_KEY_ALIAS` | **설정됨** (값 미기록) |
| `MOYEOTA_KEY_PASSWORD` | **설정됨** (값 미기록) |
| 키스토어 파일 실존 | 예 (4,424 바이트, 2026-10-13) |
| `MOYEOTA_BASE_URL` | **없음** → `app/build.gradle.kts:37-38` 기본값(운영 서버)이 쓰인다 |
| `NAVER_MAPS_CLIENT_ID` | 설정됨(비어 있지 않음) → 릴리스 지도 401 조건 아님 |

→ `releaseSigningReady = true`. 산출물로 재확인한 것은 3절.

---

## 2. 빌드

```bash
MOYEOTA_VERSION_CODE=26101501 \
MOYEOTA_VERSION_NAME=2026.1015.1 \
./gradlew :app:bundleRelease :app:assembleRelease \
          :data:testDebugUnitTest :presentation:testDebugUnitTest --console=plain
```

| 항목 | 결과 |
|---|---|
| 전체 | **BUILD SUCCESSFUL in 24s** (exit 0), 256 tasks / 61 executed |
| `:app:lintVitalRelease` | 통과 (릴리스 차단성 lint 없음) |
| `:app:signReleaseBundle` / `:app:packageRelease` | **실행됨** → 서명 경로 살아 있음 |
| 컴파일 경고/에러 | `error:` 0건, `FAILED` 0건 |

로그: `<scratchpad>/release-build.log`

---

## 3. 산출물과 서명

### 3-1. AAB (스토어에 올리는 것)

| 항목 | 값 |
|---|---|
| 경로 | `/Users/sungyoon/Desktop/moyeota/frontend/app/build/outputs/bundle/release/app-release.aab` |
| 크기 | **41,114,245 바이트 (39 MB)** |
| 생성 시각 | 2026-10-15 15:30 |
| **versionCode** | **26101501** |
| **versionName** | **2026.1015.1** |
| 엔트리 / ABI / dex | 773개 / arm64-v8a·armeabi-v7a·x86·x86_64 / `base/dex/classes.dex`~`classes5.dex` |
| 서명 | **`jarsigner -verify` → `jar verified.` (exit 0)**, v1(JAR) 서명 |

**versionCode 를 protobuf 매니페스트에서 직접 읽은 방법.** `unzip -p … | strings | grep` 은 **versionName 만**
잡는다 — protobuf 는 컴파일된 정수를 varint 로 들고 있어 `strings` 에 안 걸린다(어제도 같은 이유로
`2026.1014.2` 만 보였다). 그래서 `base/manifest/AndroidManifest.xml`(15,918 바이트) 를 꺼내 바이트로 검증했다:

```
versionCode 속성 바이트: 76657273696f6e436f64651a08 3236313031353031 22020802289b848408 32073a0530 fd8db90c
                         "versionCode"  (len 8)"26101501"                           compiled varint ↑
  → fd8db90c = 26101501   (26101402 의 varint 9a8db90c · 26101401 의 998db90c · 1 은 0건/불일치)
versionName 속성 바이트: 76657273696f6e4e616d651a0b 323032362e313031352e31
                         "versionName"  (len 11)"2026.1015.1"
```

즉 문자열 값과 컴파일된 정수가 **둘 다 26101501** 이다.

### 3-2. APK (CI 가 GitHub Release 에 붙이는 것과 같은 산출물)

| 항목 | 값 |
|---|---|
| 경로 | `/Users/sungyoon/Desktop/moyeota/frontend/app/build/outputs/apk/release/app-release.apk` |
| 크기 | **108,701,117 바이트 (104 MB)** |
| versionCode / versionName | **26101501 / 2026.1015.1** (`output-metadata.json` + `aapt2 dump xmltree` 양쪽 일치) |
| minSdk / targetSdk / compileSdk | 24 / 36 / 37 |

```
$ANDROID_HOME/build-tools/37.0.0/apksigner verify --verbose --print-certs \
    app/build/outputs/apk/release/app-release.apk     # exit 0

Verifies
Verified using v1 scheme (JAR signing): false          ← minSdk 24 에서 정상 (AGP 가 v1 생략)
Verified using v2 scheme (APK Signature Scheme v2): true
Number of signers: 1
```

> **`jarsigner -verify` 는 APK 에 쓰지 않았다.** v2 전용 서명에 대해 "jar is unsigned" 를 찍고도
> 종료코드 0 을 주기 때문이다. APK=apksigner / AAB=jarsigner 로 갈라서 확인했다(`docs/RELEASE.md:124` 와 동일).

### 3-3. 서명 인증서 (업로드 키) — APK·AAB 동일

| 항목 | 값 |
|---|---|
| **인증서 SHA-256** | `FD:71:67:6F:96:BE:3A:20:24:F6:F2:61:E5:5F:16:E5:61:76:59:DA:F1:8A:05:52:2D:9D:DC:DF:80:B7:96:E1` |
| SHA-256 (콜론 없음) | `fd71676f96be3a2024f6f261e55f16e5617659daf18a05522d9ddcdf80b796e1` |
| 인증서 SHA-1 | `BD:9E:1D:BE:FF:16:38:2B:C0:91:9C:FA:5A:E2:FC:01:E6:A7:0F:E3` |
| 공개키 SHA-256 | `bda042cbc233cf0dd589fffcd162c4397a5a0ae634a77f5fc3c493159a8ae869` |
| DN / 발급자 | `CN=황성윤, OU=Unknown, O=Unknown, L=Unknown, ST=Unknown, C=KR` (self-signed) |
| 키 | RSA 4096-bit, SHA384withRSA |
| 유효기간 | 2026-10-13 ~ **2054-02-28** (Play 요구치 2033-10-22 을 20년 이상 넘김) |

- AAB 쪽은 `META-INF/MOYEOTA-.RSA` 를 꺼내 `openssl pkcs7 → x509` 로 지문을 뽑았다
  (`keytool -printcert` 는 이 환경의 한국어 로케일에서 `IllegalFormatConversionException` 으로 죽는다 — 도구 문제이지 산출물 문제가 아니다).
- **APK 와 AAB 의 SHA-256 이 같고, 어제 값과도 같다** → 같은 업로드 키다. 키가 바뀌지 않았다.
- jarsigner 의 `Invalid certificate chain (PKIX)` / `self-signed` / `no timestamp` 경고는 **자기서명 업로드 키에서 정상**이다.
- 이 지문은 **업로드 키**의 것이다. Play Console 「설정 → 앱 서명」의 **앱 서명 키** 지문(Google 보관)과 다르다 — 서명 키 등록·Firebase 에 넣는 것은 그쪽이다.

---

## 4. 릴리스에 들어가면 안 되는 것 — 산출물에서 직접 확인

> 판정 기준은 **「실행되는가」**다. `app/build.gradle.kts:104-107` 이 R8 을 끄고 있어(`optimization { enable = false }`)
> 디버그 클래스의 바이트코드는 dex 에 **남는다** — 알려진 사실이므로 "남아 있다"로 실패 처리하지 않았다.

### 4-1. HTTP 본문 로깅 (OkHttp BODY) — **통과**

| 근거 | 확인 내용 |
|---|---|
| 게이트 (소스) | `data/src/main/kotlin/com/moyeota/data/remote/NetworkModule.kt:41,48-52` — `if (debugLogging)` 일 때만 `HttpLoggingInterceptor(Level.BODY)` 를 끼운다 |
| 주입 (소스) | `app/src/main/kotlin/com/moyeota/app/MoyeotaApplication.kt:27,35-36` — `debugLogging = isDebuggableBuild()` = `applicationInfo.flags and FLAG_DEBUGGABLE != 0` |
| **릴리스 바이트코드** | `NetworkModule.create` : `55: iload_2` (debugLogging) → `56: ifeq 101` → BODY 인터셉터 블록을 **건너뜀** |
| **릴리스 바이트코드** | `MoyeotaApplication.isDebuggableBuild` : `getApplicationInfo().flags` `iconst_2` `iand` (= FLAG_DEBUGGABLE 0x2) |
| **릴리스 매니페스트** | `android:debuggable` **없음** — APK(`aapt2 dump xmltree`)·AAB(protobuf 속성명 목록에 `debuggable` 부재) 양쪽 |

→ 릴리스에서 `FLAG_DEBUGGABLE = 0` → `debugLogging = false` → **인터셉터가 붙지 않는다.** 토큰·채팅 본문이 logcat 에 남지 않는다.

### 4-2. `usesCleartextTraffic` — **통과**

| 변이 | 결과 |
|---|---|
| debug APK | `android:usesCleartextTraffic=true` (의도대로 `app/src/debug/AndroidManifest.xml` 에만 있음) |
| **release APK** | **없음** |
| **release AAB** | **없음** (protobuf 속성명 목록: `allowBackup`·`dataExtractionRules`·`fullBackupContent`·`versionCode`·`versionName`·`minSdkVersion`·`targetSdkVersion` 뿐) |

릴리스 `<application>` 속성 전부: `theme / label / icon / name / allowBackup / supportsRtl / extractNativeLibs / fullBackupContent / roundIcon / appComponentFactory / dataExtractionRules`.
targetSdk 36 기본값(평문 차단)이 적용된다.

### 4-3. base URL 이 운영인가 — **통과**

| 확인 | 결과 |
|---|---|
| 릴리스 `BuildConfig` (생성물) | `BASE_URL = "https://api.moyeota.p-e.kr/"`, `DEBUG = false`, `VERSION_CODE = 26101501`, `VERSION_NAME = "2026.1015.1"` |
| 릴리스 APK dex 문자열 | `https://api.moyeota.p-e.kr/` **1건** |
| `10.0.2.2` | APK dex **0건**, AAB dex **0건** |
| `local.properties` | `MOYEOTA_BASE_URL` 키 자체가 없음 → 기본값 경로 |

### 4-4. `allowBackup` / 백업 규칙 — **통과**

릴리스 매니페스트: `allowBackup=true`, `fullBackupContent=@0x7f120000`, `dataExtractionRules=@0x7f120001`.
`aapt2 dump resources` 로 id→경로 역추적(난독화돼 있음): `0x7f120000 = xml/backup_rules → res/Qq.xml`,
`0x7f120001 = xml/data_extraction_rules → res/4j.xml`. **컴파일된 내용을 릴리스 APK 에서 직접 디코드**했다.

| 파일 | 제외 도메인 |
|---|---|
| `backup_rules.xml` (API<31) | `file`·`sharedpref`·`database`·`external`·`root`·`device_file`·`device_sharedpref`·`device_database`·`device_root` — **9개 전부** |
| `data_extraction_rules.xml` → `<cloud-backup>` | 동일 **9개 전부** |
| `data_extraction_rules.xml` → `<device-transfer>` | 동일 **9개 전부** |

AAB 쪽도 `base/res/xml/backup_rules.xml`·`base/res/xml/data_extraction_rules.xml` 이 들어 있고
도메인 집합이 같다(cloud-backup·device-transfer 각 9개).

→ `files/datastore/moyeota_session.preferences_pb` 의 **평문 리프레시 토큰이 구글 드라이브 백업에도,
기기 간 직접 전송에도 포함되지 않는다.** `allowBackup="true"` 는 그대로 둬도 된다 —
`allowBackup="false"` 로는 device-transfer 가 막히지 않으므로 이 두 파일이 유일한 방어선이고, 그게 제대로 들어갔다.

### 4-5. 디버그 전용 UI(`ModeDebugSection`) — **화면에 안 뜬다 (통과)**

릴리스 바이트코드로 분기를 따라갔다 (`presentation` release classes,
`MainNavGraphKt.MainNavHost$lambda$270$lambda$269$lambda$258(…, boolean, …)`):

```
 504: iload_3            ← debuggable (MainNavGraph.kt:152 의 flags and FLAG_DEBUGGABLE)
 505: ifeq   723         ← false 면 아래 723 으로 점프
 694: new    ModeDebugOptions          (true 분기에서만 생성)
 ...
 723: startReplaceGroup / endReplaceGroup
 740: aconst_null        ← modeDebug 인자에 null
 747: invokestatic MyPageScreenKt.MyPageScreen(… ModeDebugOptions …)
```

소비 쪽 `MyPageScreen.kt:221-224` 는 `if (modeDebug != null)` 일 때만 `ModeDebugSection` 을 그린다.
릴리스에 `debuggable` 플래그가 없으므로 `iload_3 = 0` → **null 분기 → 35 마이페이지에 개발자 옵션이 렌더되지 않는다.**

> 참고(결함 아님): `ModeDebugSection` 메서드와 `"개발자 옵션 · 디버그 빌드에만 보여요"` 문자열은 dex 에
> **남아 있다**(raw-byte grep 으로 1개 dex 에서 확인). R8 off 때문이고 도달 불가능한 코드다 → F-2.

---

## 5. 새로 들어간 것들이 릴리스 빌드에서도 맞는가

### 5-1. 성별 선택지 3개 — **통과**

| 확인 | 결과 |
|---|---|
| 릴리스 바이트코드 상수 | `public static final boolean ServerAcceptsUndisclosedGender = true;` (`SignupFieldSupportKt`) |
| `<clinit>` 배열 크기 | `48: iconst_3` / `49: anewarray GenderChoice` → **3칸** |
| 원소 | `[0] Gender.MALE "남성"`, `[1] Gender.FEMALE "여성"`, `[2] UndisclosedGenderChoice(Gender.UNDISCLOSED, "선택 안 함")` |
| `takeIf { ServerAcceptsUndisclosedGender }` | `const` 라 `iconst_1`(true)로 **접혀** 3번째가 항상 들어간다 → `CollectionsKt.listOfNotNull` |
| dex 문자열 | `"선택 안 함"`·`"남성"`·`"여성"` 모두 릴리스 dex 에 존재 (raw-byte grep) |

→ 릴리스 09/10 가입 화면에 성별 칩이 **3개** 선다.

### 5-2. 「합승 완료」가 방장 조건을 타는가 — **통과**

릴리스 바이트코드(`MatchWaitingRouteKt.canFinishParty(Ride, boolean)`):

```
  7: Ride.getStatus()
 11: invokestatic isFinishStage(RideStatus, Z)Z
 14: ifeq 28                      ← 단계 아니면 false
 18: invokestatic RideKt.isHostedByMe(Ride)Z
 21: ifeq 28                      ← 방장 아니면 false
 24: iconst_1 (true)
```

`isFinishStage(status, taxiEnabled) && ride.isHostedByMe` 가 그대로 컴파일돼 있다.
403 경로도 살아 있다 — `NOT_PARTY_HOST` 문자열이 릴리스 dex 에 1건 존재(`finishFailureMessage` 의 사유 구분).

### 5-3. 덤으로 확인한 두 커밋 (요청 범위 외, 산출물 스팟체크)

| 커밋 | dex 확인 |
|---|---|
| `6db78d7` imageUrl → **imageKey** | `imageKey` 3건 존재. `imageUrl` 2건은 **읽기 쪽 DTO**(`ChatDtos`·`PartyDtos`·`AuthDtos` 의 서버 응답 필드)라 정상 — 쓰기 쪽 `UpdateProfileRequest`(`UserDtos.kt:44`)는 `imageKey` 뿐이다 |
| `8738679` 죽은 코드 제거 | `getMyRides` **0건** — 제거가 산출물에 반영됐다 |

> 요청대로 **에뮬레이터 실기 구동은 하지 않았다**(다른 작업과 Gradle 이 겹침). 5-1·5-2 는 소스 + 릴리스 바이트코드 확인으로 판정했다.

---

## 6. 단위 테스트

| 모듈 | tests | failures | errors | skipped | 테스트 클래스 |
|---|---|---|---|---|---|
| `:data:testDebugUnitTest` | **306** | 0 | 0 | 0 | 20 |
| `:presentation:testDebugUnitTest` | **315** | 0 | 0 | 0 | 33 |
| 합계 | **621** | **0** | 0 | 0 | 53 |
| `:domain` / `:app` | — | — | — | — | 테스트 소스 없음 (`domain/src` 에 `main` 만, `app/src` 에 `debug`·`main` 만) → F-3 |

- 기대치(각각 306·315)와 **정확히 일치. 줄지 않았다.**
- 수치는 `{data,presentation}/build/test-results/testDebugUnitTest/TEST-*.xml` 의 `tests/failures/errors/skipped` 속성을 직접 합산한 값이다.
- **첫 실행에서 두 태스크가 `UP-TO-DATE`** 였다(같은 세션의 다른 작업이 15:27 에 이미 돌려 둠). 그래서
  `./gradlew :data:testDebugUnitTest --rerun :presentation:testDebugUnitTest --rerun` 으로 **강제 재실행**했다 →
  `BUILD SUCCESSFUL`, 결과 XML 갱신 시각 15:33:16, 위 수치는 **실제로 돈 결과**다.

---

## 7. 어제(`_workspace/release-build-verify.md`) 대비 달라진 점

### 7-1. 산출물·수치

| 항목 | 어제 (2026-10-14) | 오늘 (2026-10-15) | 차이 |
|---|---|---|---|
| 브랜치 | `ci/version-code` | `fix/place-roadname-null` @ `8738679` | 커밋 **8개** 추가 (`v2026.1014.1..HEAD`) |
| AAB versionCode / Name | `26101401`(CI 공개) / 로컬 `26101402` | **`26101501` / `2026.1015.1`** | 새 번호, 중복 아님 |
| AAB 크기 | 41,109,522 B | **41,114,245 B** | +4,723 B |
| APK 크기 | 108,684,733 B | **108,701,117 B** | +16,384 B |
| 단위 테스트 | 554 (data 278 + presentation 276) | **621 (306 + 315)** | **+67** (data +28, presentation +39) |
| 서명 인증서 SHA-256 | `FD:71:…:E1` | **동일** | 같은 업로드 키 |
| 4번 항목 (4개) | 전부 통과 | **전부 통과** | 변화 없음 |
| Gradle | 9.4.1 | 9.5.1 | 래퍼/데몬 버전만 |

커밋 8개: `01365b1`(roadName null + 출시 점검) · `5fc8da4`·`249f33f`(성별 「선택 안 함」 추가 → 노출 ON) ·
`6db78d7`(imageKey) · `f78bee3`(합승 완료 실패 사유 구분) · `beb4a52`(방장 전용) · `c53e008`(문서) · `8738679`(죽은 코드 제거).

### 7-2. 어제 결함의 현재 상태

| 결함 | 상태 |
|---|---|
| **F-1 (중)** 문서 4-1 의 기본 명령으로 빌드하면 `versionCode=1` 이 박힌다 | **해소.** `01365b1` 이 `docs/RELEASE.md` 에 17줄을 더해 4-1 을 버전 주입형 명령 + 경고 블록(105-109줄) + AAB 확인 명령으로 바꿨다. 이번 검증은 그 문서를 그대로 따라 수행했다. (문서가 안내하는 `unzip | strings | grep` 은 **versionName 만** 잡는다는 한계가 남아 있다 — 3-1 의 varint 확인을 함께 적어 두면 더 낫다. 개선 제안 수준이고 결함 아님.) |
| **F-2 (하)** 릴리스 R8 off → 디버그 코드 바이트코드 잔존, APK 104MB | **그대로.** `app/build.gradle.kts:104-107` 의 `optimization { enable = false }` 변경 없음. `ModeDebugSection`·로깅 인터셉터 클래스는 dex 에 남아 있으나 **실행 경로가 없다**(4-1·4-5). AAB 39MB 로 올리므로 업로드 블로커 아님. |
| **F-3 (하)** `:domain`/`:app` 단위 테스트 0개 | **그대로.** `domain/src` 에 `test` 없음, `app/src` 에 `test` 없음 → 두 태스크가 `NO-SOURCE` 로 "성공"한다. 게이트로 세면 안 된다. |

### 7-3. 새로 발견된 결함

**없다.** 어제 통과했던 4개 항목이 커밋 8개 뒤에도 전부 통과하고, 새로 들어간 2개 기능이 릴리스
바이트코드에 의도대로 반영돼 있다.

---

## 8. 남은 것 / 주의

1. **브랜치 주의.** 이 AAB 는 `fix/place-roadname-null` 에서 만들었다 — `origin/main`·`origin/develop` **양쪽 모두에 대해 8커밋 앞서 있다.**
   CI 릴리스는 `main` 푸시에서 돌므로, 이 번호로 올린 뒤 같은 코드가 `main` 에 들어가면 CI 가 **그날의 다음 순번**(`261015xx`)을 쓰게 된다.
   같은 날 CI 가 한 번 더 돌면 `26101501` 과 겹칠 수 있는지 확인이 필요하다(`.github/actions/calculate-version` 은 **GitHub Release 태그**로 순번을 세므로,
   이 로컬 AAB 를 Play 에만 올리고 태그를 만들지 않으면 CI 가 `26101501` 을 다시 쓸 수 있다 → **Play 가 중복으로 거부**한다).
   → 올린 뒤 `v2026.1015.1` 태그/릴리스를 만들어 두거나, CI 분을 `02` 부터 쓰게 하는 쪽이 안전하다.
2. **에뮬레이터 실기 미검증** (요청 범위에서 제외). 릴리스는 평문이 막혀 로컬 백엔드에 붙지 않으므로, 실기 확인은 운영 서버 상태로 릴리스 APK 를 설치해 해야 한다.
3. Play Console 작업은 손대지 않았다 — 두 URL 등록, 패키지 등록, **앱 서명 키**(업로드 키 아님) SHA-256 등록, Firebase SHA-1 등록.
4. F-2(R8)는 1차 내부 테스트 이후 별도 작업으로. 켜는 순간 동작이 바뀔 수 있으므로 업로드 직전에 하지 않는다.
