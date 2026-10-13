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

CI 에서는 같은 이름의 **환경변수**로 넣는다(`app/build.gradle.kts` 의 `buildSetting` 이 local.properties → 환경변수 순으로 읽는다). 키스토어 파일 자체는 base64 로 시크릿에 넣고 빌드 시작 때 풀어 쓴다.

> 네 값 중 하나라도 없거나 키스토어 파일이 없으면 **서명을 붙이지 않는다**. 키 없는 팀원도 빌드는 성공하고, 서명 없는 결과물은 스토어에 올릴 수도 기기에 설치할 수도 없어서 실수로 올라갈 위험은 없다.

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

## 4. AAB 빌드

스토어는 APK 가 아니라 **AAB** 를 받는다.

```bash
./gradlew :app:bundleRelease
# 결과물: app/build/outputs/bundle/release/app-release.aab
```

서명됐는지 확인:

```bash
jarsigner -verify -verbose -certs app/build/outputs/bundle/release/app-release.aab | head
```

빌드 전에 `local.properties` 의 `MOYEOTA_BASE_URL` 이 **운영 서버**를 가리키는지 확인한다. 로컬 테스트용으로 `10.0.2.2` 를 넣어 둔 채 빌드하면 아무 데도 못 붙는 앱이 올라간다. 값을 지우면 기본값(`https://api.moyeota.p-e.kr/`)이 쓰인다.

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

아직 준비되지 않은 항목이다. 둘 다 **URL 이 있어야** 심사를 통과한다.

### 개인정보 처리방침 URL — 필수

- Play Console 에 등록하고, 앱 안 35 마이페이지에서도 같은 페이지를 열어야 한다.
- 지금 앱은 주소가 없어 해당 메뉴를 **숨기고 있다**. `presentation/core/SupportLinks.kt` 의 세 값(`TERMS_URL`·`PRIVACY_URL`·`CONTACT_EMAIL`)을 채우면 메뉴가 나타난다.

### 계정 삭제 안내 URL — 필수

- 앱 안 탈퇴는 구현돼 있다(37 화면, `DELETE /api/v1/users/me`).
- 그와 별개로 **앱을 설치하지 않고도 열리는 웹 페이지**가 필요하다. 데이터 안전 섹션에 URL 로 적는다.
- 폼이나 로그인은 필요 없다. ① 삭제 요청 방법 ② 처리 기간 ③ 삭제되는 데이터와 보관되는 데이터 — 이 세 가지가 적힌 정적 페이지 한 장이면 요건을 만족한다.

세 페이지(약관·개인정보·탈퇴 안내)를 한곳에 올리면 `SupportLinks` 한 파일만 채우고 끝난다. GitHub Pages 나 노션 공개 페이지로 충분하다.

---

## 7. 체크리스트

```
[ ] 키스토어 생성 + 비밀번호 관리자에 보관
[ ] local.properties 에 서명 4개 값
[ ] MOYEOTA_BASE_URL 이 운영 서버인지 확인 (또는 비워 두기)
[ ] versionCode 올림
[ ] ./gradlew :app:bundleRelease → jarsigner 로 서명 확인
[ ] 개인정보 처리방침 URL 준비 → SupportLinks 채우기
[ ] 계정 삭제 안내 URL 준비
[ ] Play Console 패키지 등록 (com.moyeota / com.moyeota.driver)
[ ] 첫 업로드 후 앱 서명 키 SHA-256 → 서명 키 등록
[ ] 앱 서명 키 SHA-1 → Firebase 콘솔 등록 (푸시)
[ ] 내부 테스트 트랙으로 먼저 올려 실기 확인
```
