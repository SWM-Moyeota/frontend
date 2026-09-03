# 08 · compose-builder — 백엔드 base URL 빌드 설정화

## 배경
`AppContainer.kt`가 base URL을 `"http://10.0.2.2:8080/"`로 하드코딩하고 있었다.
10.0.2.2는 에뮬레이터에서만 호스트 PC를 가리키는 특수 주소라, 실기기로 APK를 올리면
모든 API 호출이 연결 실패로 떨어진다. 실기기 테스트 때마다 소스를 고쳤다 되돌리는
상황이라 빌드 설정으로 분리했다.

## 변경 파일

| 파일 | 라인 | 변경 |
|------|------|------|
| `app/build.gradle.kts` | 7~20 | `buildSetting(key, default)` 헬퍼 추출 — local.properties → 환경변수 → 기본값 순 |
| `app/build.gradle.kts` | 22~27 | `naverMapsClientId`를 헬퍼 사용으로 정리 (동작 동일, default `""`) |
| `app/build.gradle.kts` | 29~38 | `moyeotaBaseUrl` 추가. default `http://10.0.2.2:8080/`, 끝의 `/` 자동 보정 |
| `app/build.gradle.kts` | 50 | `buildConfigField("String", "BASE_URL", ...)` |
| `app/build.gradle.kts` | 61~65 | `buildFeatures { buildConfig = true }` — 미활성 상태였으므로 신규 활성화 |
| `app/src/main/kotlin/com/moyeota/app/AppContainer.kt` | 20~24 | `NetworkModule.create(BuildConfig.BASE_URL, debugLogging)` |
| `app/src/main/kotlin/com/moyeota/app/MoyeotaApplication.kt` | 22~23 | buildConfig 활성화로 사실이 아니게 된 주석 정정 (로직 무변경) |
| `local.properties` | 13~15 | 사용법 주석만 추가. 실값 미기입 → 에뮬레이터 기본 동작 유지 |

data/·domain/·presentation/ 무수정.

## 우선순위
1. `local.properties`의 `MOYEOTA_BASE_URL=` (개발자 로컬)
2. 환경변수 `MOYEOTA_BASE_URL` (CI·스크립트)
3. 기본값 `http://10.0.2.2:8080/`

빈 문자열은 미지정으로 간주해 다음 순위로 넘어간다. Retrofit `baseUrl`은 `/`로
끝나야 하므로 `.let { if (it.endsWith("/")) it else "$it/" }`로 보정한다 —
`http://192.168.0.42:8080` 처럼 슬래시를 빠뜨려도 `IllegalArgumentException`이 나지 않는다.

`providers.fileContents` / `providers.environmentVariable`를 쓰는 이유는 기존
NAVER 키와 동일하다: `org.gradle.configuration-cache=true`라 입력이 추적돼야 한다.

## 검증

- `./gradlew :app:assembleDebug --console=plain` → BUILD SUCCESSFUL
- 기본값 확인: 생성된 `BuildConfig.BASE_URL == "http://10.0.2.2:8080/"` (기존 리터럴과 동일)
- 오버라이드 확인: `MOYEOTA_BASE_URL=http://192.168.0.42:8080 ./gradlew :app:generateDebugBuildConfig`
  → `BASE_URL = "http://192.168.0.42:8080/"` (슬래시 보정까지 동작). 이후 기본값으로 재빌드해 원복
- 실기 구동(emulator-5554): 설치 → 온보딩 건너뛰기 → 로그인 → 나중에 인증할게요 → 홈 진입.
  crash 없음. OkHttp 로그에서 실제 호출 주소 확인:
  `--> GET http://10.0.2.2:8080/api/v1/users/me/favorite-places?userId=1` → `<-- 200`

## 실기기 빌드 절차

```
# local.properties 에 한 줄 추가 후 재빌드 (개발 PC 와 폰이 같은 Wi-Fi 여야 함)
MOYEOTA_BASE_URL=http://<개발PC LAN IP>:8080/
```

또는 CI/스크립트에서 `MOYEOTA_BASE_URL=http://<IP>:8080/ ./gradlew :app:assembleDebug`.

평문 http 통신은 `AndroidManifest.xml:16`의 `android:usesCleartextTraffic="true"`로
이미 허용돼 있어 실기기에서도 별도 조치가 필요 없다.
