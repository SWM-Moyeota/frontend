# 배포 절차 — Google Play

승객 앱 `com.moyeota` 를 Play 스토어에 올리는 순서. 기사 앱 `com.moyeota.driver` 는 **별개 앱**이라 모든 단계를 따로 한 번 더 한다.

---

## 1. 업로드 키스토어 만들기 (최초 1회)

**이 파일과 비밀번호를 잃어버리면 앱 업데이트를 영영 못 한다.** 비밀번호 관리자에 넣고, 파일은 저장소 밖 안전한 곳에 둔다.

```bash
keytool -genkeypair -v \
  -keystore ~/moyeota-upload.jks \
  -storetype PKCS12 \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -alias moyeota-upload
```

물어보는 것들:

| 항목 | 넣을 값 |
|---|---|
| 키스토어 비밀번호 | 직접 정한다 (분실 시 복구 불가) |
| 이름/조직/도시/시/국가 | 아무 값이나 가능. 사용자에게 보이지 않는다. 국가는 `KR` |
| 키 비밀번호 | 키스토어 비밀번호와 같게 둬도 된다 |

`-validity 10000` 은 약 27년이다. Play 는 **2033-10-22 이후까지 유효한 키**를 요구하므로 이보다 짧게 잡지 않는다.

확인:

```bash
keytool -list -v -keystore ~/moyeota-upload.jks -alias moyeota-upload
```

출력의 `SHA-256` 지문을 적어 둔다 — **업로드 키**의 지문이다(아래 5번에서 쓰는 **앱 서명 키** 지문과 다르다).

---

## 2. 빌드에 키 알려주기

`local.properties` 에 네 줄을 더한다. 이 파일은 `.gitignore` 에 있어 커밋되지 않는다.

```properties
MOYEOTA_KEYSTORE_PATH=/Users/<사용자>/moyeota-upload.jks
MOYEOTA_KEYSTORE_PASSWORD=<키스토어 비밀번호>
MOYEOTA_KEY_ALIAS=moyeota-upload
MOYEOTA_KEY_PASSWORD=<키 비밀번호>
```

> 네 값 중 하나라도 없거나 키스토어 파일이 없으면 **서명을 붙이지 않는다**. 키 없는 팀원도 빌드는 성공하고, 서명 없는 결과물은 스토어에 올릴 수도 기기에 설치할 수도 없어서 실수로 올라갈 위험은 없다.

### 2-2. CI 에 등록할 GitHub Secrets

`app/build.gradle.kts` 의 `buildSetting` 이 local.properties → **환경변수** 순으로 읽으므로, CI 에서는 같은 이름의 환경변수로 넣는다. 러너에는 local.properties 가 없으니 환경변수만 보게 된다.

「Settings → Secrets and variables → Actions → New repository secret」 에서 다섯 개를 등록한다.

| 시크릿 이름 | 값의 형태 | 예 / 만드는 법 |
|---|---|---|
| `MOYEOTA_KEYSTORE_BASE64` | 키스토어 파일을 base64 로 인코딩한 **한 줄 문자열** | `base64 -i ~/moyeota-upload.jks \| pbcopy` 후 붙여넣기 |
| `MOYEOTA_KEYSTORE_PASSWORD` | 1번에서 정한 키스토어 비밀번호 (평문) | — |
| `MOYEOTA_KEY_ALIAS` | 키 별칭 (평문) | `moyeota-upload` |
| `MOYEOTA_KEY_PASSWORD` | 키 비밀번호 (평문) | 키스토어 비밀번호와 같게 뒀다면 같은 값 |
| `NAVER_MAPS_CLIENT_ID` | 네이버 지도 NCP 키 ID (평문) | 없으면 **릴리스 앱의 지도가 인증 실패(401)** 한다 |

`.jks` **파일 자체는 저장소에 두지 않는다**(`.gitignore` 가 `*.jks` 를 막는다). base64 로 시크릿에 넣고 워크플로가 시작할 때 러너의 `$RUNNER_TEMP` 에 풀어 쓰고, 끝나면 지운다.

> `MOYEOTA_BASE_URL` 은 **일부러 시크릿으로 두지 않는다.** 등록해 두면 누군가 스테이징 주소를 넣는 순간 스토어 빌드가 조용히 그쪽을 보게 된다. 러너에 아무 값도 없으면 `app/build.gradle.kts` 의 기본값인 운영 서버(`https://api.moyeota.p-e.kr/`)가 그대로 박힌다.

---

## 3. 버전 올리기

```kotlin
versionCode = moyeotaVersionCode   // 기본 1, MOYEOTA_VERSION_CODE 로 덮어씀
versionName = moyeotaVersionName   // 기본 "1.0", MOYEOTA_VERSION_NAME 로 덮어씀
```

- `versionCode` 는 **올릴 때마다 반드시 1 이상 올린다. 같은 값으로는 두 번 못 올린다.**
- `versionName` 은 사용자에게 보이는 값이고 **35 마이페이지 하단에 그대로 뜬다.**

CI 예시:

```bash
MOYEOTA_VERSION_CODE=$GITHUB_RUN_NUMBER MOYEOTA_VERSION_NAME=1.0.3 ./gradlew :app:bundleRelease
```

---

## 4. 빌드

### 4-1. 스토어용 AAB (로컬)

스토어는 APK 가 아니라 **AAB** 를 받는다.

```bash
./gradlew :app:bundleRelease
# 결과물: app/build/outputs/bundle/release/app-release.aab
```

서명됐는지 확인:

```bash
jarsigner -verify -verbose -certs app/build/outputs/bundle/release/app-release.aab | head
```

> **AAB 는 jarsigner, APK 는 apksigner.** AAB 는 JAR 서명(v1)을 쓰므로 jarsigner 로 확인된다. 반면 APK 는 minSdk 24 라서 AGP 가 v1 을 **안 붙이고** v2 만 붙인다. 그래서 올바르게 서명된 APK 에도 `jarsigner -verify` 는 `jar is unsigned` 를 찍고 **종료 코드 0 을 반환한다** — APK 확인에 쓰면 안 된다. APK 는 `apksigner verify` 를 쓴다.
>
> ```bash
> "$(ls "$ANDROID_HOME"/build-tools/*/apksigner | sort -V | tail -1)" \
>   verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
> ```

빌드 전에 `local.properties` 의 `MOYEOTA_BASE_URL` 이 **운영 서버**를 가리키는지 확인한다. 로컬 테스트용으로 `10.0.2.2` 를 넣어 둔 채 빌드하면 아무 데도 못 붙는 앱이 올라간다(릴리스는 평문 통신이 막혀 있어 요청이 아예 나가지 않는다 — 7번 참고). 값을 지우거나 주석 처리하면 기본값(`https://api.moyeota.p-e.kr/`)이 쓰인다.

### 4-2. GitHub Actions 자동 릴리스 (서명된 APK)

`main` 에 푸시되면 `.github/workflows/release.yml` 이 **서명된 릴리스 APK** 를 만들어 GitHub Release 에 붙인다. (예전에는 서명 키가 없어 `assembleDebug` 산출물을 붙였다. 지금은 아니다.)

| 단계 | 하는 일 |
|---|---|
| 버전 계산 | `.github/actions/calculate-version` 이 KST 기준 `YYYY.MMDD.N` 을 만든다 |
| 서명 키스토어 복원 | 시크릿 4개가 다 있는지 먼저 확인하고, `MOYEOTA_KEYSTORE_BASE64` 를 `$RUNNER_TEMP` 에 푼 뒤 `keytool -list` 로 비밀번호·별칭이 맞는지 확인한다 |
| APK 빌드 | `./gradlew :app:assembleRelease` → `app/build/outputs/apk/release/app-release.apk` |
| 서명 검증 | `apksigner verify` 로 서명 여부를 확인하고, 릴리스 `BASE_URL` 이 `https` 인지도 확인한다 |
| 키스토어 삭제 | 성공·실패 무관하게(`if: always()`) 풀어 둔 `.jks` 를 지운다 |
| 릴리스 | `moyeota-v<버전>.apk` 를 `v<버전>` 태그의 GitHub Release 에 첨부한다 |

워크플로가 주입하는 버전:

- `MOYEOTA_VERSION_CODE` = `github.run_number` — 워크플로 실행마다 1 씩 올라간다.
- `MOYEOTA_VERSION_NAME` = 위에서 계산한 `YYYY.MMDD.N` — 35 마이페이지 하단에 이 값이 뜬다.

> **시크릿이 없으면 워크플로가 실패한다.** 예전처럼 서명 없는 APK 가 조용히 릴리스에 붙는 일은 없다. 「서명 키스토어 복원」에서 한 번, 빌드 뒤 「서명 검증」에서 또 한 번 막는다. 두 단계 중 하나라도 실패하면 `release` 잡이 실행되지 않아 GitHub Release 가 아예 만들어지지 않는다.
>
> 반대로 **시크릿을 등록하기 전에는 `main` 푸시마다 릴리스 워크플로가 빨간불**이 된다. 2-2 의 다섯 개를 먼저 넣어야 한다.

> 이 워크플로는 **APK** 만 만든다. 스토어에 올리는 **AAB 는 아직 로컬에서 4-1 로 만든다.**

---

## 5. Play Console

### 5-1. 앱 등록 (Android 개발자 인증)

「패키지 이름 및 서명 키 등록」에서:

| 항목 | 값 |
|---|---|
| 패키지 이름 | `com.moyeota` |
| 내부용 이름 | `모여타` (본인 구분용, 자유) |

기사 앱은 `com.moyeota.driver` 로 한 번 더.

### 5-2. 첫 업로드와 Play 앱 서명

AAB 를 처음 올리면 **Play 앱 서명**이 켜진다. 이때부터 키가 둘이다.

- **업로드 키** — 내가 만든 `moyeota-upload.jks`. 올릴 때 서명하는 용도.
- **앱 서명 키** — Google 이 보관. 사용자 기기에 설치될 때 검증되는 진짜 키.

「설정 → 앱 서명」 화면의 **앱 서명 키 SHA-256 지문**을 복사해 5-1 의 서명 키 등록에 넣는다. 업로드 키 지문과 헷갈리지 않는다.

> **Firebase 를 쓰고 있으므로** 앱 서명 키 SHA-1 지문도 Firebase 콘솔에 등록해야 한다. 안 하면 **스토어로 받은 앱에서만 푸시가 안 온다**(디버그 빌드는 멀쩡해서 늦게 발견된다).

---

## 6. 올리기 전에 막히는 것

~~아직 준비되지 않은 항목이다.~~ **2026-10-14 에 두 URL 이 생겼다** — `legal.moyeota.p-e.kr` 에 약관·개인정보 처리방침·계정 삭제 안내를 올렸다. **앱 쪽 연결도 끝났다.** 남은 것은 Play Console 에 그 주소를 등록하는 일이다.

### 개인정보 처리방침 URL

- Play Console 에 등록하고, 앱 안 35 마이페이지에서도 같은 페이지를 열어야 한다 → **둘이 같은 주소여야 한다**.
- 앱 쪽: `presentation/core/SupportLinks.kt` 의 네 값이 채워져 있고, 35 「안내」 카드에 개인정보 처리방침·이용약관·문의하기 세 행이 선다. 04 로그인의 「가입하면 … 동의하게 돼요」 문구도 같은 두 페이지를 연다.
- 주소를 null 로 되돌리면 **그 행만 사라진다**(앱은 깨지지 않는다). 페이지가 내려갔을 때의 긴급 복구 경로이므로 그대로 두었다.

### 계정 삭제 안내 URL

- 앱 안 탈퇴는 구현돼 있다(37 화면, `DELETE /api/v1/users/me`).
- 그와 별개로 **앱을 설치하지 않고도 열리는 웹 페이지**가 필요하다 → `/account-deletion/` 을 Play Console **데이터 안전** 섹션에 적는다.
- 앱에서는 37 안의 「지워지는 것과 남는 것 자세히 보기」가 이 페이지를 연다. 35 마이페이지에는 **일부러 행을 두지 않았다** — 「탈퇴하기」와 뜻이 겹치는 입구가 둘이 되면 파괴적인 오탭 면적만 넓어진다.

---

## 7. 릴리스에만 적용되는 안전 설정

디버그와 릴리스가 **다르게** 동작하는 두 가지다. 둘 다 설정뿐이고 앱 기능에는 영향이 없다.

### 7-1. 평문(http) 통신은 디버그 전용

`android:usesCleartextTraffic="true"` 는 `app/src/debug/AndroidManifest.xml` 에만 있다. `src/main` 에는 없으므로 **릴리스는 targetSdk 36 의 기본값(평문 차단)** 이 적용된다.

- 디버그는 로컬 백엔드(`http://10.0.2.2:8080/`, 실기기는 개발 PC 의 LAN IP)에 그대로 붙는다.
- 릴리스는 http 요청이 플랫폼에서 막힌다. 그래서 `MOYEOTA_BASE_URL` 에 `10.0.2.2` 를 넣은 채 릴리스를 빌드하면 "아무 데도 못 붙는 앱"이 된다 — 4-1 의 경고가 그 뜻이다.

확인하는 방법(빌드된 매니페스트를 직접 뜯는다):

```bash
AAPT2=$(ls "$ANDROID_HOME"/build-tools/*/aapt2 | sort -V | tail -1)
for v in debug release; do
  echo "--- $v"
  "$AAPT2" dump xmltree --file AndroidManifest.xml \
    "app/build/outputs/apk/$v/app-$v.apk" | grep -i cleartext || echo "(없음)"
done
# debug  → usesCleartextTraffic=true
# release→ (없음)
```

### 7-2. 토큰은 백업·기기이전 대상이 아니다

`res/xml/backup_rules.xml`(API 31 미만)과 `res/xml/data_extraction_rules.xml`(31 이상)이 **모든 도메인을 제외**한다. 비워 두면 앱 데이터 전부가 백업 대상이고, 그러면 `files/datastore/moyeota_session.preferences_pb` 의 **평문 리프레시 토큰이 구글 드라이브로 올라가 새 기기에서 복원된다.**

- 클라우드 백업(`<cloud-backup>`)과 기기 간 직접 전송(`<device-transfer>`) **둘 다** 막았다. `allowBackup="false"` 로는 기기 간 전송이 막히지 않으므로 이 파일들이 유일한 방어선이다.
- 백업할 가치가 있는 로컬 데이터가 지금은 없다 — 즐겨찾기·방·프로필은 전부 서버에 있고 세션은 다시 로그인하면 된다.
- **새 로컬 저장소(DataStore·Room·SharedPreferences)를 추가할 때 이 두 파일을 다시 볼 필요는 없다.** 도메인 단위로 막혀 있어 기본이 "제외"다. 반대로 **백업하고 싶은 것이 생기면** 두 파일에 `<include>` 를 **각각** 넣어야 한다(한쪽만 고치면 나머지 안드로이드 버전에서 동작이 달라진다).

---

## 8. 체크리스트

```
[ ] 키스토어 생성 + 비밀번호 관리자에 보관
[ ] local.properties 에 서명 4개 값
[ ] GitHub Secrets 5개 등록 (2-2 표) — 안 하면 main 푸시마다 릴리스 워크플로 실패
[ ] MOYEOTA_BASE_URL 이 운영 서버인지 확인 (또는 비워 두기)
[ ] versionCode 올림
[ ] ./gradlew :app:bundleRelease → jarsigner 로 AAB 서명 확인
[ ] (CI 산출 APK 를 돌려볼 때) apksigner verify 로 서명 확인
[x] 개인정보 처리방침 URL 준비 → SupportLinks 채우기   (2026-10-14)
[x] 계정 삭제 안내 URL 준비                            (2026-10-14)
[ ] Play Console 에 두 URL 등록 (개인정보처리방침 / 데이터 안전 → 계정 삭제)
[ ] Play Console 패키지 등록 (com.moyeota / com.moyeota.driver)
[ ] 첫 업로드 후 앱 서명 키 SHA-256 → 서명 키 등록
[ ] 앱 서명 키 SHA-1 → Firebase 콘솔 등록 (푸시)
[ ] 내부 테스트 트랙으로 먼저 올려 실기 확인
```
