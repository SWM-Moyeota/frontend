# 31. API 연동 — 내 정보 조회 (GET /api/v1/local/users/info)

작업일: 2026-09-02 · 범위: `data/`·`domain/` (presentation 미변경) · **실서버 검증 완료**

---

## 1. 연동한 엔드포인트

| 메서드 | 경로 | 인증 | 성공 | 실패 |
|---|---|---|---|---|
| GET | `/api/v1/local/users/info` | **Bearer 필수** | 200 | 401 (무토큰/만료) |

소스: `backend/.../user/interfaces/LocalUserController.java` (`@RequestMapping("/api/v1/local")`),
`user/application/LocalUserService.java#getProfile`.

### 프리픽스 주의 — 이게 이번 연동의 유일한 함정

같은 "사용자" 도메인인데 프리픽스가 **셋으로 갈라져 있다.**

- `/api/v1/auth/*` — 가입·로그인·재발급·로그아웃 (permitAll, Bearer 없는 별도 클라이언트)
- `/api/v1/users/me/*` — 즐겨찾기 (인증)
- `/api/v1/local/users/info` — **내 정보 조회** (인증) ← 이번 건

`local` 을 `users` 로 잘못 적으면 **404 가 아니라 401** 이 난다. Spring Security 가
`anyRequest().authenticated()` 로 미매핑 경로까지 잡기 때문이다. 즉 오타가 "경로 오류"가 아니라
"세션 만료"로 위장해 나타난다 — 디버깅에 가장 비싼 종류의 실수라
`AuthenticatedPathContractTest.내 정보 조회는 local 프리픽스를 쓴다` 로 리플렉션 고정했다.

## 2. 실제 응답 shape (실서버 curl 실측, localhost:8080)

```
$ curl -s -o /dev/null -w '%{http_code}' localhost:8080/api/v1/local/users/info
401  {"code":"USER005","message":"로그인이 필요합니다."}

$ curl -H "Authorization: Bearer $AT" localhost:8080/api/v1/local/users/info
200  {"uuid":"01a06145-3caf-7614-a3bd-cee6e25316b1","name":"김성윤"}
```

토큰은 `POST /api/v1/auth/login` (testuser1 / Passw0rd!) 로 발급. 401 본문의 `USER005` 는
`AuthMappers.toAuthException` 이 이미 `SESSION_EXPIRED` 로 매핑하고 있던 코드라 별도 처리가 없다.

### `name` 은 nullable — 방어가 아니라 계약이다

`LocalUserService.getProfile` 이 이렇게 폴백한다:

```java
String name = user.getNickname() != null ? user.getNickname()
        : userProfiles.findByUserId(userId).map(UserProfile::getName).orElse(null);
```

1. 닉네임 → 2. 프로필 실명 → 3. **`null`**

현재 서버에는 **닉네임을 설정하는 엔드포인트 자체가 없어** `User.nickname` 은 항상 null 이고,
결과적으로 실명이 온다. 하지만 프로필이 비어 있는 사용자에게는 `"name":null` 이 그대로 내려간다
(Jackson 이 null 을 지우지 않으므로 **키는 남고 값만 null**). 논-널로 선언하면 그 계정에서 파싱이 터진다.

`uuid` 는 액세스 토큰의 `sub` 와 **같은 값**이다 — 즉 uuid 만 필요하면 이 호출은 불필요하다.
이 엔드포인트를 부르는 이유는 오직 `name` 이다.

## 3. Repository 시그니처 (compose-builder 계약)

```kotlin
// domain/repository/AuthRepository.kt — 기존 인터페이스에 추가
suspend fun getMyProfile(): UserProfileInfo

// domain/model/User.kt — 신규
data class UserProfileInfo(
    val uuid: String,
    val name: String?,   // null 가능. 화면이 반드시 폴백 처리할 것
)
```

- 주입: 기존 `AppContainer.authRepository` 그대로. **새 Repository·새 DI 배선 없음.**
- 실패: 전부 `AuthException`. 401 → `AuthError.SESSION_EXPIRED`, 연결 실패 → `NETWORK`,
  응답에 uuid 가 비면 → `UNKNOWN`.
- 캐시하지 않는다. 매번 서버에 묻고, 결과 보관은 화면(ViewModel) 몫이다.
- 로그인 상태에서만 호출할 것. 미로그인 401 은 재발급으로 풀리지 않아(재시도할 토큰이 없다) 화면 에러가 된다.

### 화면 쪽 필수 처리

`name == null` 분기. "이름 없음" 폴백 문구든 이름 영역 숨김이든, 한 가지로 정해야 한다.
매퍼가 빈 문자열·공백도 `null` 로 정규화하므로 화면은 **`null` 한 가지만** 보면 된다.

## 4. 왜 AuthApi 가 아니라 새 UserApi 인가

`AuthApi` 는 **Bearer 도 401 재발급 Authenticator 도 붙지 않은 별도 OkHttp 클라이언트**로 만들어진다
(재발급 요청이 401 을 맞았을 때 다시 재발급을 트리거하는 무한 루프를 막기 위한 구조).
이 조회는 토큰이 필수라 그 클라이언트에 얹으면 **항상 401** 이다. 그래서 인증 클라이언트(`apiClient`)
쪽 Retrofit 으로 `UserApi` 를 따로 만들었다.

반대로 도메인 계약은 `AuthRepository` 에 합쳤다 — `authState` 가 이미 "나는 누구인가"의 출처라
같은 경계면에 두는 편이 화면 입장에서 일관되고, Repository 하나를 위해 DI 배선을 늘리지 않는다.
그 결과 `RemoteAuthRepository` 는 API 를 둘 받는다(`AuthApi` + `UserApi`) — 클라이언트가 다르기 때문이다.

## 5. 변경 파일

**신규**
- `data/src/main/kotlin/com/moyeota/data/remote/UserApi.kt`
- `data/src/main/kotlin/com/moyeota/data/remote/dto/UserDtos.kt` (`UserProfileResponse`)
- `data/src/main/kotlin/com/moyeota/data/remote/UserMappers.kt` (`toDomain()`)
- `data/src/test/kotlin/com/moyeota/data/remote/UserMappersTest.kt`

**수정**
- `domain/model/User.kt` — `UserProfileInfo` 추가
- `domain/repository/AuthRepository.kt` — `getMyProfile()` 추가
- `data/repository/RemoteAuthRepository.kt` — `userApi` 파라미터 + 구현
- `data/remote/NetworkModule.kt` — `Apis.user` 추가 (**apiClient 쪽 retrofit**)
- `app/AppContainer.kt` — `RemoteAuthRepository(apis.auth, apis.user, sessionManager)`
- `data/src/test/.../RemoteAuthRepositoryTest.kt` — `FakeUserApi` + 테스트 4건
- `data/src/test/.../AuthenticatedPathContractTest.kt` — 경로 고정 + 잔재 검사에 `UserApi` 포함

## 6. 검증

```
./gradlew :data:testDebugUnitTest :domain:compileDebugKotlin :app:assembleDebug --console=plain
→ BUILD SUCCESSFUL
```

| 테스트 | 건수 | 실패 |
|---|---|---|
| `UserMappersTest` | 5 | 0 |
| `RemoteAuthRepositoryTest` | 12 (신규 4) | 0 |
| `AuthenticatedPathContractTest` | 8 (신규 1) | 0 |

실서버 스모크: 무토큰 401 / 토큰 200 둘 다 확인 (위 §2). 에뮬레이터 미사용(지시대로).
**앱에서의 실제 호출은 미검증** — presentation 연결이 아직 없어 호출 지점이 없다.
compose-builder 연결 후 qa-verifier 가 화면-엔드포인트 교차 검증할 것.

## 7. 서버 쪽 관찰 (결함 아님, 기록용)

- 닉네임 설정 엔드포인트가 없어 `name` 은 사실상 항상 실명이다. 닉네임 기능이 생기면
  이 응답이 조용히 실명 → 닉네임으로 바뀐다 — 화면이 "실명"을 전제한 문구를 쓰면 그때 어긋난다.
- `GET /api/v1/local/users/info` 는 uuid 외에 아무 프로필 정보도 주지 않는다
  (별점·인증 라벨·이미지 없음). 마이페이지에 그 이상이 필요하면 백엔드 추가 요청이 필요하다.
