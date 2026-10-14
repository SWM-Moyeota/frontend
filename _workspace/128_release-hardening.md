# 128 — 출시 빌드 안전 설정 3건

작업 브랜치 `feat/backend-sync-sse`, HEAD `a93e169`. **커밋하지 않음 — 워킹트리에만 있다.**
실기·에뮬레이터 미사용(제약). 모든 근거는 빌드 산출물 정적 검사로 확보했다.

## 변경 파일

| 파일 | 상태 |
|---|---|
| `.github/workflows/release.yml` | 수정 (+142/-10) |
| `app/src/main/AndroidManifest.xml` | 수정 (평문 허용 제거) |
| `app/src/debug/AndroidManifest.xml` | **신규** (평문 허용 디버그 전용) |
| `app/src/main/res/xml/backup_rules.xml` | 수정 (템플릿 주석 → 전 도메인 제외) |
| `app/src/main/res/xml/data_extraction_rules.xml` | 수정 (템플릿 주석 → 전 도메인 제외 ×2) |
| `docs/RELEASE.md` | 수정 (2-2 Secrets, 4-2 워크플로, 7 안전 설정, 8 체크리스트) |

---

# ① 릴리스 워크플로가 서명된 APK 를 만든다

## 바뀐 것

`assembleDebug` + `app-debug.apk` 복사 → `assembleRelease` + `app-release.apk` 복사.
`build-apk` 잡에 네 단계를 추가했다.

| 단계 | 하는 일 |
|---|---|
| 서명 키스토어 복원 | 시크릿 4개 존재 확인 → base64 디코드 → `keytool -list` 로 비밀번호·별칭 확인 |
| APK 빌드 (서명된 릴리스) | `:app:assembleRelease` + 서명 4값·버전 2값·지도 키 환경변수 주입 |
| 서명 검증 | `apksigner verify` + 릴리스 `BASE_URL` 이 https 인지 확인 |
| 키스토어 삭제 | `if: always()` — 실패해도 지운다 |

## 서명 값 전달 방식

`app/build.gradle.kts` 의 `buildSetting()` 을 읽어 확인했다: `local.properties` → **환경변수** → 기본값
순이고, 러너에는 `local.properties` 가 없으므로(`.gitignore`) 환경변수만 보게 된다.
`storeFile = rootProject.file(keystorePath)` 는 절대 경로를 그대로 쓰므로 워크스페이스 밖 경로도 된다.

키스토어 파일은 **`$RUNNER_TEMP`** 에 푼다. `upload-artifact` 의 `path` 는 워크스페이스 기준이라
워크스페이스 밖에 두면 키가 아티팩트에 섞여 공개되는 경로가 **구조적으로** 없다.
`chmod 600`, 로그에 값·경로 내용 미출력, `if: always()` 로 삭제.

## 서명 없는 APK 가 조용히 배포되는 것을 어떻게 막았나 — 3중

**1차: 빌드 전 시크릿 존재 확인.** 네 값 중 하나라도 비면 `::error::` 와 함께 `exit 1`.
`releaseSigningReady` 가 false 가 되는 조건을 선제 차단한다.

**2차: 구조적 차단(AGP 동작).** 서명 설정이 없으면 AGP 는 산출물 이름을 바꾼다 — 실측:

```
# local.properties 의 서명 4값을 임시로 주석 처리하고 assembleRelease
=== 산출물 목록 ===
app-release-unsigned.apk       ← app-release.apk 가 아니다
output-metadata.json
=== 워크플로의 cp ===
cp: app/build/outputs/apk/release/app-release.apk: No such file or directory
cp exit=1
```
(테스트 후 `local.properties` 는 md5 `70110d03…` 로 복원 확인했다.)

**3차: `apksigner verify` 게이트.** 실측:

| 입력 | 출력 | 종료 코드 |
|---|---|---|
| 서명된 `app-release.apk` | `Verifies` / v2: true / `CN=황성윤 … RSA 4096` | **0** |
| 서명 제거한 APK | `DOES NOT VERIFY` | **1** |

종료 코드 1 → 스텝 실패 → `build-apk` 잡 실패 → `release` 잡(`needs: build-apk`) 미실행 →
**GitHub Release 가 아예 만들어지지 않는다.**

## jarsigner 를 쓰지 않은 이유 (실측)

`minSdk = 24` 라 AGP 가 v1(JAR) 서명을 **붙이지 않는다.** 그래서 `jarsigner` 는 쓸 수 없다:

```
=== META-INF signature files in release APK ===
(none — v1/JAR 서명 없음)

=== jarsigner -verify (올바르게 서명된 APK) ===
no manifest.
jar is unsigned.
jarsigner real exit code on SIGNED apk = 0      ← 거짓 음성 + 종료 코드 0

=== apksigner verify (같은 APK) ===
Verified using v1 scheme (JAR signing): false
Verified using v2 scheme (APK Signature Scheme v2): true
```

`jarsigner` 는 **통과·실패를 가릴 수 없어** 게이트로 쓸 수 없다. 워크플로 주석에도 적어 뒀다.
러너의 `apksigner` 는 `$ANDROID_HOME/build-tools/*/apksigner` 에서 찾는다 — AGP 가
필요한 build-tools 를 빌드 중에 내려받으므로 **빌드가 끝난 뒤** 찾고, `sort -V | tail -1` 로 최신을 쓴다.
없으면 `exit 1`.

> `docs/RELEASE.md` 4-1 의 AAB 확인은 `jarsigner` 로 남겼다 — AAB 는 JAR 서명을 쓰므로 그쪽이 맞다.
> 둘을 혼동하지 않게 문서에 경고를 달았다.

## 버전 주입 (실측)

- `MOYEOTA_VERSION_CODE` = `${{ github.run_number }}`
- `MOYEOTA_VERSION_NAME` = `${{ needs.calculate-version.outputs.version }}` (기존 `YYYY.MMDD.N` 계산 결과 재사용)

```
$ MOYEOTA_VERSION_CODE=4242 MOYEOTA_VERSION_NAME=2026.1013.7 ./gradlew :app:processReleaseManifest
$ grep versionCode/versionName merged_manifests/release/.../AndroidManifest.xml
android:versionCode="4242"
android:versionName="2026.1013.7"
```

## MOYEOTA_BASE_URL 안전장치 — 결론

**(a) 시크릿으로 두지 않았다.** 등록해 두면 누군가 스테이징 주소를 넣는 순간 스토어 빌드가 조용히
그쪽을 본다. 주입하지 않으면 러너에 아무 값도 없어 `app/build.gradle.kts` 의 기본값
`https://api.moyeota.p-e.kr/`(운영 서버)가 **항상** 박힌다. 비용 0 의 가장 강한 방어다.

**(b) 검증 단계에 한 줄 가드를 넣었다** — 생성된 `BuildConfig.java` 에서 값을 뽑아 `https://` 로
시작하는지 본다. 미래에 누가 시크릿을 추가하거나 기본값을 바꿔도 여기서 잡힌다. 실측:

| 입력 | 판정 |
|---|---|
| `https://api.moyeota.p-e.kr/` | 통과 |
| `http://10.0.2.2:8080/` | 차단 |
| `http://staging.internal/` | 차단 |
| `BuildConfig.java` 자체가 없음 | 차단 (fail-closed) |

**(c) 넣지 않은 것 — 의견.** 실제 위험은 CI 가 아니라 **로컬 `bundleRelease`** 다. 성윤님 기계의
`local.properties` 에 `MOYEOTA_BASE_URL=http://10.0.2.2:8080/` 가 들어 있는 채로 AAB 를 만들면
CI 가드가 전혀 닿지 않는다(docs/RELEASE.md 가 이미 산문으로 경고하고 있었던 것이 그 증거다).
제대로 막는 자리는 `app/build.gradle.kts` 인데 **이번 작업 범위가 아니라 손대지 않았다.**
권하는 모양(스케치):

```kotlin
// 릴리스는 평문이 막혀 있다 — http 가 박히면 아무 요청도 못 보내는 앱이 스토어에 올라간다.
val verifyReleaseBaseUrl = tasks.register("verifyReleaseBaseUrl") {
    val url = moyeotaBaseUrl           // 설정 시점에 값을 캡처해야 configuration-cache 와 맞는다
    doLast { require(url.startsWith("https://")) { "릴리스 BASE_URL 이 https 가 아니다: $url" } }
}
// packageRelease / bundleRelease 가 이 태스크에 의존하게 건다.
```
`gradle.taskGraph.whenReady` 방식은 configuration-cache 와 충돌하므로 쓰지 말 것.

## 덤으로 고친 것 — 지도 키

기존 `release.yml` 은 `NAVER_MAPS_CLIENT_ID` 를 **주입하지 않았다**(`build.yml` 은 한다).
디버그 APK 를 붙일 때는 티가 안 났지만, 이제 사용자가 받는 릴리스 APK 라 그대로 두면
**지도가 런타임 인증 실패(401)** 한다. 빌드 단계에 추가했다.

---

# ② 백업 규칙 — 전 도메인 제외

## 토큰이 어디 저장되는지 (추측 아님, 바이트코드로 확인)

`preferencesDataStore(name = N)` 이 만드는 실제 경로를 DataStore 1.1.7 바이트코드에서 확인했다:

```
PreferenceDataStoreFile.preferencesDataStoreFile(ctx, name)
  → DataStoreFile.dataStoreFile(ctx, name + ".preferences_pb")
      → new File(ctx.getApplicationContext().getFilesDir(), "datastore/" + fileName)
```

즉 `filesDir/datastore/<name>.preferences_pb`. 저장소는 전 코드베이스에 **둘뿐**이다
(`grep "preferencesDataStore(name"` → 2건, Room·SharedPreferences 직접 사용 0건):

| 파일 | 내용 |
|---|---|
| `files/datastore/moyeota_session.preferences_pb` | `user_uuid` + `access_token` + `refresh_token` (`DataStoreTokenStorage`) |
| `files/datastore/moyeota_active_party.preferences_pb` | `active_party_id` + `active_party_owner_uuid` (`DataStoreActivePartyStorage`) |

둘 다 **암호화되지 않은 평문**이다(`DataStoreTokenStorage` 의 KDoc 이 의도적 트레이드오프로 명시).
`TokenHolder` 는 이 저장소를 동기적으로 읽는 창구일 뿐 자체 저장은 없다.

## 선택: "민감한 것만 제외"가 아니라 **전부 제외**

근거 셋:

1. **복원해서 얻을 것이 0 이다.** 즐겨찾기·방·프로필은 전부 서버에 있고, 세션은 다시 로그인하면
   된다. 방 id 복원은 이미 끝난 방을 되살려 404 를 부를 뿐이다. 백업 가치가 있는 로컬 데이터가 없다.
2. **파일 이름 열거는 깨진다.** DataStore 는 같은 디렉터리에 쓰기 중 임시 파일도 만들고, 새 저장소를
   추가한 사람이 이 XML 을 고치는 것을 잊으면 즉시 샌다. 도메인 단위 제외는 **기본이 "제외"** 다.
3. **우리가 만들지 않은 파일이 더 위험하다.** Firebase/Play 서비스가 쓰는 SharedPreferences 에
   FCM 등록 토큰과 인스턴스 id 가 있다. 그게 새 기기로 복원되면 앱은 "유효한 토큰이 있다"고 판단해
   재등록을 건너뛰고 서버 매핑은 옛 기기를 가리킨 채 남아 **푸시가 조용히 끊긴다.**
   이건 파일명 열거로는 절대 못 덮는다.

## 도메인 9개를 하나씩 적은 이유

`root` 하나로 끝나지 않는다. 백업 엔진은 도메인마다 트리를 **따로** 순회하고 제외 목록도
**도메인별로 따로** 적용한다(루트 순회는 `files`/`databases`/`shared_prefs` 를 건너뛴다).
`root` 만 막으면 토큰이 있는 `files/` 는 그대로 백업된다.

유효 도메인은 lint 의 `FullBackupContentDetector.VALID_DOMAINS` 를 바이트코드에서 뽑아 확정했다:
`file, database, sharedpref, external, root, device_file, device_database, device_sharedpref, device_root`
— 9개 전부 넣었다. `device_*` 는 direct boot 저장소로 지금은 안 쓰지만 나중에 생겨도 새지 않게 미리 막았다.

## 두 파일 모두 / cloud-backup 과 device-transfer 따로

- `backup_rules.xml` (`android:fullBackupContent`) → **API 31 미만**. minSdk 24 라 필요하다.
- `data_extraction_rules.xml` (`android:dataExtractionRules`) → **API 31 이상**.
  `<cloud-backup>` 과 `<device-transfer>` 를 **각각** 채웠다 — 섹션을 적지 않으면 그 경로는
  "전부 전송"이 기본값이다.

## device-transfer 판단: **막았다**

D2D 전송은 사용자가 직접 트리거하고 클라우드를 거치지 않아 기밀성 위험은 백업보다 낮다.
그래도 막은 이유는 보안이 아니라 **동작이 깨지기 때문**이고, 근거를 XML KDoc 주석에 적었다:

1. **리프레시 토큰이 두 기기에 복제된다.** 서버가 리프레시를 회전시키므로(`TokenAuthenticator`)
   먼저 갱신한 쪽이 다른 쪽을 무효화한다. 지는 기기는 401 → `onSessionExpired()` 로 예고 없이
   로그아웃된다 — **합승 도중에 터질 수 있는 사고**다.
2. **옛 방 id 가 되살아난다** → `GET /matching/rooms/{id}` 404 로 시작.
3. **FCM 등록 토큰 복제** → 푸시 조용히 끊김.
4. **잃는 것이 없다** — 새 기기에서 한 번 로그인하면 끝.

`android:allowBackup` 은 `true` 로 남겼다. `false` 로 해도 **API 31+ 의 기기 간 전송은 막히지 않아**
이 두 파일이 어차피 필요하고, 같은 말을 하는 장치가 둘이면 한쪽이 드리프트한다.

## 검증 증거

**(a) lint 가 규칙을 실제로 검사하고 통과한다.** `:app:lintDebug` 결과 — 내가 건드린 네 파일에 대한
지적 **0건**(`grep "backup_rules|data_extraction|AndroidManifest|src/debug"` → 없음).
유일한 error 는 사전 존재 건이다:

```
1 error, 26 warnings
app/src/main/kotlin/com/moyeota/app/MoyeotaFirebaseMessagingService.kt:179: Error: ... [MissingPermission]
```
(이 파일은 내 변경 목록에 없다 — 사전 존재.)

**(b) 그 검사가 살아 있다는 negative 테스트.** 가짜 도메인을 주입하고 다시 돌렸다:

```
backup_rules.xml:56: Error: Unexpected domain bogus_domain, expected one of file, database,
  sharedpref, external, root, device_file, device_database, device_sharedpref, device_root
  [FullBackupContent]
```
→ `FullBackupContent` 검사가 켜져 있고 내 9개 도메인이 정확히 그 목록이다. (테스트 후 원복 확인)

**(c) 두 APK 에 실제로 들어갔다.** 바이너리 XML 을 뜯어 확인 — 릴리스는 리소스 경로가 축약돼
(`xml/backup_rules` → `res/Qq.xml`) 리소스 테이블로 역추적했다. 디버그·릴리스 모두 `full-backup-content`
9개 exclude, `data-extraction-rules` 의 `cloud-backup`·`device-transfer` 각 9개 exclude 확인.

---

# ③ 평문 통신 — 디버그 전용

## 선택: `src/debug/AndroidManifest.xml` (networkSecurityConfig 아님)

`main` 에서 `android:usesCleartextTraffic="true"` 를 제거하고 `app/src/debug/AndroidManifest.xml`
(신규)에만 뒀다. targetSdk 36(≥28)이라 선언이 없으면 기본값 false 가 적용된다.

`networkSecurityConfig` 를 고르지 않은 이유 두 가지:

1. **덜 단순하다.** 매니페스트 속성 + `res/xml` 리소스 + 디버그 소스셋 리소스 병합까지 세 군데가 얽힌다.
   디버그 매니페스트는 6줄 파일 하나다.
2. **열거가 깨진다.** 허용 도메인을 적어야 하는데 `app/build.gradle.kts` 주석대로 실기기 테스트는
   `http://<개발PC LAN IP>:8080/` 를 쓴다 — 개발자·공유기마다 IP 가 다르다. `10.0.2.2` 만 넣으면
   실기기 테스트가 깨지고, 열거를 포기하면 결국 이 방식과 같아진다.

**릴리스에서 평문이 완전히 막히는 결과는 두 방식이 동일**하므로 단순한 쪽을 골랐다. 이유는 파일 주석에 적었다.

## 검증 증거

**(a) 병합된 매니페스트** (`app/build/intermediates/merged_manifests/`):

```
### .../merged_manifests/debug/processDebugManifest/AndroidManifest.xml
    android:usesCleartextTraffic = true          ← 허용
### .../merged_manifests/release/processReleaseManifest/AndroidManifest.xml
   >>> usesCleartextTraffic = (ABSENT)           ← 차단
```

**(b) 패키징된 APK 의 바이너리 매니페스트** (`aapt2 dump xmltree`) — 더 강한 증거:

```
=== app/build/outputs/apk/debug/app-debug.apk ===
    A: android:usesCleartextTraffic(0x010104ec)=true
=== app/build/outputs/apk/release/app-release.apk ===
    (없음)
```

**(c) 디버그에서 로컬 서버 경로가 안 깨진다** (에뮬레이터 없이 산출물만):

```
$ MOYEOTA_BASE_URL='http://10.0.2.2:8080/' ./gradlew :app:assembleDebug   # exit 0
BuildConfig:  BASE_URL = "http://10.0.2.2:8080/"
디버그 APK:    usesCleartextTraffic=true
dex:          'http://10.0.2.2:8080/' 발견 → classes15.dex
```
`BuildConfig.BASE_URL` 은 `AppContainer` 가 Retrofit·SSE(`PartyEventSource`)·웹소켓(`ChatSocket`)
세 곳에 넘기는 단일 출처라 이 값이 http 로 박히고 매니페스트가 평문을 허용하면 경로가 성립한다.
(`ChatSocket` 은 `http://` → `ws://` 로 바꿔 쓰는데, 평문 ws 도 같은 정책을 타므로 디버그에서만 열린다.)
확인 후 환경변수 없이 재빌드해 `https://api.moyeota.p-e.kr/` 로 원복했다.

---

# ④ 문서

`docs/RELEASE.md`:

- **2-2 (신규)** — CI 에 등록할 GitHub Secrets 표 + base64 만드는 법 + `MOYEOTA_BASE_URL` 을
  시크릿으로 두지 않는 이유.
- **4 → 4-1/4-2 로 분리** — 4-1 로컬 AAB(+ AAB=jarsigner / APK=apksigner 경고),
  4-2 GitHub Actions 가 **서명된 APK** 를 만든다는 설명·단계 표·주입 버전·실패 조건.
- **7 (신규)** — 7-1 평문은 디버그 전용(확인 명령 포함), 7-2 토큰은 백업·기기이전 대상 아님
  (새 저장소 추가 시 할 일 / 백업하고 싶은 게 생겼을 때 할 일).
- **8 체크리스트** — Secrets 5개 등록 항목 추가, APK 는 apksigner 로 확인하도록 수정.

기존 5·6 절과 그 교차 참조(5-1, 5-2)는 손대지 않았다.

---

# 성윤님이 GitHub 에 등록해야 할 시크릿

「Settings → Secrets and variables → Actions → New repository secret」. **5개.**
등록 전에는 `main` 푸시마다 릴리스 워크플로가 의도적으로 실패한다.

| # | 이름 | 값의 형태 | 만드는 법 / 주의 |
|---|---|---|---|
| 1 | `MOYEOTA_KEYSTORE_BASE64` | base64 문자열 (줄바꿈 있어도 됨) | `base64 -i ~/moyeota-upload.jks \| pbcopy` → 붙여넣기. `.jks` 파일을 직접 올리지 않는다 |
| 2 | `MOYEOTA_KEYSTORE_PASSWORD` | 평문 비밀번호 | `docs/RELEASE.md` 1번에서 정한 **키스토어** 비밀번호 |
| 3 | `MOYEOTA_KEY_ALIAS` | 평문 문자열 | `moyeota-upload` |
| 4 | `MOYEOTA_KEY_PASSWORD` | 평문 비밀번호 | **키** 비밀번호. 키스토어와 같게 뒀다면 2번과 같은 값 |
| 5 | `NAVER_MAPS_CLIENT_ID` | 평문 문자열 | NCP 키 ID. **없으면 빌드는 성공하고 릴리스 앱의 지도만 401** 로 죽는다 |

1~4 는 `local.properties` 의 같은 이름 값과 **동일**하다(1 만 파일 → base64 변환).
base64 왕복이 무손실인 것은 실측 확인했다(`cmp` 동일, 4424 bytes).

### 복원 로직 실측 (로컬에서 실제 키스토어로 4 케이스)

| 시나리오 | 결과 |
|---|---|
| 시크릿 전부 있음 | `OK: 복원 + 비밀번호/별칭 검증 통과 (4424 bytes, perms -rw-------)` exit 0 |
| `MOYEOTA_KEY_ALIAS` 누락 | `::error::서명 시크릿이 비어 있다: MOYEOTA_KEY_ALIAS` exit 1 |
| base64 가 깨짐 | `::error::키스토어를 열지 못했다` exit 1 |
| 비밀번호가 틀림 | `::error::키스토어를 열지 못했다` exit 1 |

---

# 빌드 결과

```
./gradlew :app:assembleDebug --console=plain -q     → exit 0
./gradlew :app:assembleRelease                      → exit 0  (BUILD SUCCESSFUL)

최종 산출물 확인
debug    서명: OK  cleartext: 허용
release  서명: OK  cleartext: 없음(차단)
```

워크플로 YAML:

```
python3 -c "import yaml; yaml.safe_load(open('.github/workflows/release.yml'))"   → OK
actionlint + shellcheck (release.yml)                                            → exit 0
actionlint + shellcheck (저장소 전체 워크플로 8개, 회귀 확인)                      → exit 0
```

> actionlint 가 실제 버그 하나를 잡았다: 처음엔 잡 수준 `env` 에 `${{ runner.temp }}` 를 썼는데
> `runner` 컨텍스트는 거기서 쓸 수 없어 **워크플로 파싱 자체가 실패**한다
> (`context "runner" is not allowed here`). 디렉터리를 스텝 수준에서 합성하도록 고쳤다.
> 자체 재현 테스트로 이 검사가 살아 있는 것도 확인했다.
>
> 셸 로직 버그 하나도 직접 잡았다: 에러 메시지 안에 역따옴표로 예시 명령을 넣었는데
> 쌍따옴표 안이라 **명령 치환**이 될 뻔했다 — 문서 참조로 바꿨다.

---

# 남은 위험

**가장 먼저 볼 것 — versionCode.** `github.run_number` 는 **워크플로 재실행(re-run) 시 같은 값**이다.
Play 는 같은 versionCode 를 두 번 받지 않으므로 재실행한 빌드는 올릴 수 없다. 또 과거에 수동 업로드로
run_number 보다 큰 versionCode 를 쓴 적이 있으면 영구히 거부된다. 첫 업로드 전에
`${{ github.run_number }}` 에 오프셋을 더하거나(`$(( RUN_NUMBER + 1000 ))`) 날짜 기반으로 바꿀지 결정해야 한다.

**이 워크플로는 APK 만 만든다.** 스토어는 AAB 를 받으므로 **스토어 업로드 경로는 여전히 수동**
(`bundleRelease` 로컬)이다. GitHub Release 의 APK 는 내부 배포·테스트용이다. 같은 잡에
`:app:bundleRelease` 를 붙이는 것은 쉽지만 이번 범위가 아니라 안 했다.

**`MOYEOTA_KEY_PASSWORD` 는 사전 검증되지 않는다.** `keytool -list` 는 **스토어** 비밀번호만 확인한다.
키 비밀번호가 틀리면 Gradle 서명 단계에서 터져 워크플로는 실패하지만 메시지가 덜 친절하다.
(`keytool -keypasswd` 류로 사전 확인할 수 있으나 키스토어를 변경하는 명령이라 쓰지 않았다.)

**`NAVER_MAPS_CLIENT_ID` 누락은 조용한 열화다.** 빌드는 성공하고 지도만 401 이다.
서명처럼 하드 실패로 만들 수도 있지만 보안 사안이 아니라 기능 사안이라 이번엔 표·문서 경고로만 뒀다.
원하면 「서명 키스토어 복원」의 존재 확인 목록에 한 줄 추가하면 된다.

**백업 규칙의 런타임 동작은 실기 검증을 못 했다.** 제약상 에뮬레이터를 쓰지 않았으므로 증거는
정적 검사(lint + 패키징된 바이너리 XML)까지다. 기기가 있으면
`adb shell bmgr backupnow com.moyeota` + `adb shell dumpsys backup` 으로 실제 제외를 확인할 수 있다.
XML 이 유효하고 두 APK 에 들어간 것은 확인했으니 위험은 낮다.

**`<cross-platform-transfer>` 는 선언하지 않았다.** lint 소스에 그 태그가 있고 `platform` 속성이
**필수**라 플랫폼별 **옵트인** 구조로 보인다(선언 안 하면 전송 대상 아님). 다만 Android 16 계열의
새 API 라 문서로 확정하지 못했다 — compileSdk 37 / targetSdk 36 을 올릴 때 한 번 확인하는 게 좋다.
필요해지면 두 백업 파일과 같은 제외를 거기에도 넣어야 한다.

**configuration-cache 에 서명 비밀번호가 직렬화된다.** `providers.environmentVariable()` 값이
프로젝트 `.gradle/configuration-cache` 에 들어간다. 이 디렉터리는 `setup-gradle` 이 캐시하지 않고
(`~/.gradle` 만 캐시) `upload-artifact` 도 `moyeota-v*.apk` 만 올리므로 현재 구성에서는 새지 않는다.
**앞으로 프로젝트 디렉터리나 `.gradle/` 를 아티팩트로 올리는 단계를 추가하면 그 순간 유출된다.**

**다른 에이전트와 같은 워킹트리를 쓰는 중이다.** 작업 도중 `presentation/.../feature/home/`
컴파일 에러로 빌드가 한 번 실패했다(`No parameter with name 'wide' found`) — 내 변경과 무관한
타 에이전트의 중간 상태였고 이후 복구됐다. 최종 빌드 2건은 모두 통과했지만, 커밋 전에
`presentation/` 쪽 변경이 안정된 상태에서 한 번 더 돌려 보는 것이 안전하다.
