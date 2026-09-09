# 39 · api-integrator — 동승자 표시 정보 + 닉네임(user API) 연동

작성 2026-09-07. 입력: `38_input_member_nickname.md`.
기준 소스: `../backend` `feature/user-fcm-token` 병합본(`35d9203`, 커밋됨 — 워킹트리 깨끗함).
검증: `./gradlew :data:testDebugUnitTest` (151 tests, 0 failures) + `:app:assembleDebug` BUILD SUCCESSFUL.
**실서버 미검증** — 이유는 §6.

---

## 1. 연동한 엔드포인트

| 메서드 | 경로 | 보호 | 앱 진입점 |
|---|---|---|---|
| GET | `api/v1/matching/rooms/{partyId}` | 토큰 | `RideRepository.getPartyDetail` (members shape 변경) |
| POST | `api/v1/matching/rooms/{partyId}/join` | 토큰 | `RideRepository.joinParty` (같은 shape) |
| POST | `api/v1/auth/register` | permitAll | `AuthRepository.register` (`nickname` 추가) |
| POST | `api/v1/auth/nickname/check` | permitAll | `AuthRepository.isNicknameTaken` **(신규)** |
| PATCH | `api/v1/users/me` | 토큰 | `AuthRepository.updateProfile` **(신규, 화면 미연결)** |

### 실제 응답 shape (컨트롤러/record 소스 기준)

`PartyDetailResult.MemberInfo(UUID publicId, String nickname, String imageUrl, String badgeId, Integer rideCount, Instant joinedAt)`

```json
"members":[{"publicId":"01a06145-...-cee6e25316b1","nickname":"성윤","imageUrl":null,
            "badgeId":null,"rideCount":4,"joinedAt":"2026-08-30T09:00:00Z"},
           {"publicId":null,"nickname":null,"imageUrl":null,"badgeId":null,
            "rideCount":0,"joinedAt":"2026-08-30T09:01:00Z"}]
```
둘째 항목이 **유저 요약을 못 찾은 경우**(`PartyDetailResult.toMemberInfo` 의 `summary == null` 분기) — 실제로 서버가 내려보내는 모양이다. 네 문자열 필드를 전부 nullable 로 선언한 근거이며, `PartyMappersTest` 가 이 JSON 그대로 파싱을 못 박는다.

- `POST /auth/nickname/check` → `{"exists": true|false}` (200), 형식 위반이면 `{"code":"USER107", ...}` (400)
- `PATCH /users/me` → 204 No Content (본문 없음)
- 에러 본문은 전부 `ErrorResponse(code, message)` — 기존 `ApiErrorDto` 그대로다.

## 2. 최종 시그니처 (compose-builder 가 읽는 부분)

```kotlin
// domain/model/User.kt
data class User(
    val id: String,            // publicId. 요약 없음(탈퇴)이면 "" → 탭 불가로 다룰 것
    val nickname: String,      // null 이면 "탈퇴한 회원"
    val verifiedLabel: String, // 서버 badgeId 가 항상 null → 항상 ""
    val rating: Double,        // 평가 API 없음 → 항상 0.0 ("평가 없음"으로 표시)
    val rideCount: Int,        // FINISHED 파티 수. 0 이면 "첫 탑승"
    val imageUrl: String? = null,
    val isMe: Boolean = false, // publicId == UserSession.currentUserUuid
)

// domain/model/Auth.kt
data class NewUser(loginId, password, nickname: String, name, birthDate, phoneNumber, gender, email)
// ↑ nickname 은 기본값 없음(필수). 매퍼가 보내기 전에 trim 한다.

object AuthPolicy {
    const val NICKNAME_PATTERN = "^[가-힣a-zA-Z0-9]{2,10}$"
    const val NICKNAME_MIN_LENGTH = 2
    const val NICKNAME_MAX_LENGTH = 10
    fun isValidNickname(value: String): Boolean   // trim 후 판정 (서버 VO 와 동일)
}

// domain/model/AuthError.kt — 두 값 추가
AuthError.INVALID_NICKNAME     // 400 USER107 (형식)
AuthError.NICKNAME_DUPLICATED  // 409 USER108 (중복)

// domain/repository/AuthRepository.kt
suspend fun isNicknameTaken(nickname: String): Boolean       // true = 이미 사용 중
suspend fun updateProfile(nickname: String?, imageUrl: String?)  // null 은 안 보냄(=갱신 안 함)
```

`RideRepository` 시그니처는 **변경 없다** — `getPartyDetail`/`joinParty` 가 돌려주는 `Ride.members` 의 내용만 실데이터로 바뀐다.

### 화면 쪽에서 지켜야 할 것

- `isMe` 는 **상세·합류 응답에서만** 채워진다. 목록(`getParties`/`getPartiesWithin`)과 방 생성(`createParty`) 응답에는 서버가 멤버 식별자를 주지 않아 자리표시 멤버(`멤버 1`, id `m0`…)가 그대로 남고 `isMe` 는 전부 false 다. 「나 제외」 카운트를 목록 화면에서 계산하면 어긋난다.
- `isNicknameTaken` 이 false 여도 가입 성공 보장은 없다(확인과 가입 사이 경쟁). 최종 판정은 `register` 의 409 `USER108`.
- 닉네임 형식 오류는 두 경로로 온다: 서버 VO 가 먼저 걸리면 `INVALID_NICKNAME`, Bean Validation(`@NotBlank`/`@Size`)이 먼저 걸리면 `INVALID_REQUEST`(message = "nickname: …"). 화면 문구는 양쪽 다 형식 안내로 수렴시키는 게 안전하다.

## 3. 변경 파일

**domain**
- `domain/model/User.kt` — `imageUrl`·`isMe` 추가(둘 다 기본값 있음), KDoc 로 rating/verifiedLabel 이 고정값인 이유 명시
- `domain/model/Auth.kt` — `NewUser.nickname`(필수), `AuthPolicy.NICKNAME_*` + `isValidNickname`
- `domain/model/AuthError.kt` — `INVALID_NICKNAME`, `NICKNAME_DUPLICATED`
- `domain/repository/AuthRepository.kt` — `isNicknameTaken`, `updateProfile`

**data**
- `data/remote/dto/PartyDtos.kt` — `MemberInfo` 교체(`memberId` 삭제 → publicId/nickname/imageUrl/badgeId nullable + `rideCount: Int = 0`)
- `data/remote/PartyMappers.kt` — `MemberInfo.toUser(currentUuid)` 신설, `PartyDetailResponse.toRide(currentUuid: String? = null)`
- `data/remote/dto/AuthDtos.kt` — `RegisterRequestDto.nickname`(loginId, password, **nickname**, name, … 순), `NicknameCheckRequestDto`, `NicknameCheckResponse`
- `data/remote/dto/UserDtos.kt` — `UpdateProfileRequest(nickname?, imageUrl?)`
- `data/remote/AuthApi.kt` — `POST api/v1/auth/nickname/check`
- `data/remote/UserApi.kt` — `PATCH api/v1/users/me`
- `data/remote/AuthMappers.kt` — `toDto` 에 nickname(trim), 에러코드 `USER106/107/108` 매핑
- `data/repository/RemoteAuthRepository.kt` — 두 메서드 구현
- `data/repository/RemoteRideRepository.kt` — 생성자 `(api, session: UserSession, local)`, 상세/합류에 `session.currentUserUuid` 전달

**app**
- `app/AppContainer.kt:59` — `RemoteRideRepository(apis.matching, sessionManager)`

**presentation: 0 파일 수정.** compose-builder 가 이미 `SignupDraft` 에 `nickname` 과 `AuthError.INVALID_NICKNAME`/`NICKNAME_DUPLICATED` 분기를 넣어 둔 상태라 컴파일 호환 수정이 필요 없었다(그래서 `NewUser.nickname` 에 기본값을 두지 않았다).

## 4. 계약 대비 변경점 (2건, 시그니처는 유지)

1. **`nickname/check` 를 `UserApi` 가 아니라 `AuthApi` 에 뒀다.** 38 문서는 `UserApi` 를 지정했지만, 이 호출은 **가입 도중=미로그인 상태**에서 나간다. `UserApi` 는 `AuthHeaderInterceptor` + `TokenAuthenticator` 가 붙은 `apiClient` 로 만들어지므로, 만료된 토큰이 세션에 남아 있으면 permitAll 경로인데도 JWT 필터에 걸려 401 이 날 수 있고 재발급까지 트리거된다. `AuthApi` 는 Bearer 가 붙지 않는 별도 클라이언트라 그 위험이 없다. Repository 시그니처(`isNicknameTaken`)는 그대로다. `AuthenticatedPathContractTest.비보호 호출은 Bearer 가 붙지 않는 AuthApi 에만 있다` 가 되살아남을 막는다. `PATCH /users/me` 는 토큰 필수라 지시대로 `UserApi` 다.
2. **닉네임 체크 DTO 를 `UserDtos.kt` 가 아니라 `AuthDtos.kt` 에 뒀다** — 1번의 결과. `UpdateProfileRequest` 만 `UserDtos.kt` 다.

## 5. 백엔드 리뷰 코멘트

1. **`badgeId` 타입 불일치 (확인됨).** `user.api.MemberSummary.badgeId` 는 `Long`, `PartyDetailResult.MemberInfo.badgeId` 는 `String` 이다. 지금은 `toMemberInfo` 가 값을 쓰지 않고 항상 `null` 을 넣어(TODO) 드러나지 않지만, 실제 값을 채우는 순간 `Long → String` 변환을 어디서 할지 정해야 한다. 앱은 `String?` 으로 받고 있으므로 서버가 Long 을 그대로 내보내면 **파싱이 터진다**(kotlinx 는 숫자→String 을 허용하지 않는다). 서버가 배지를 채우기 전에 프론트에 알려 줄 것.
2. **`UpdateProfileRequest.nickname` 에 `@Size(min=2,max=10)` 만 있고 패턴 검증이 없다.** 서버 서비스가 `Nickname` VO 를 거치면 USER107 이 나오지만, Bean Validation 이 먼저 걸리는 입력(예: 1자)은 `INVALID_REQUEST` 로 나가서 **같은 오류가 두 코드로 갈린다**. 가입(`UserRegisterRequest.nickname`)도 동일하다. 코드 하나로 모으면 화면 분기가 단순해진다.
3. ~~**`USER104`(전화번호 중복, 409)를 앱이 구분하지 못한다.**~~ → **후속 지시로 이번에 수정 완료(§8).** 프론트 쪽 결함이었다 — `toAuthException` 이 `USER104` 를 코드로 잡지 않아 409 폴백으로 `LOGIN_ID_DUPLICATED` 가 됐고, 전화번호가 겹치면 "이미 사용 중인 아이디예요"가 떴다.
4. 참고: `GET /api/v1/local/users/info` 는 shape 이 그대로라 앱 변경이 없다. `name` 이 닉네임으로 채워지므로 홈 인사말은 자동으로 닉네임이 뜬다.

## 6. 실서버 미검증 — **qa-verifier 필독**

`localhost:8080` 에 떠 있는 프로세스(PID 74595, **9/6 02:46 기동**)는 **이번 변경 이전 빌드**다. 읽기 전용 curl 로 확인:

```
POST /api/v1/auth/nickname/check  → 404 Not Found
POST /api/v1/auth/register {}     → INVALID_REQUEST, 누락 필드 목록에 nickname 이 없음
```

백엔드 소스(`35d9203`)에는 두 기능이 다 들어 있으므로 **서버를 재기동해야** 닉네임·동승자 표시 정보를 실기 검증할 수 있다. 지시대로 프로세스는 건드리지 않았다(9/7 01:42 에 뜬 두 번째 backend JVM 이 있으나 8080 을 잡지 못한 상태로 보인다 — 리더가 정리할 것). 재기동 전까지 21/23/25 화면은 **구 shape(`memberId`)** 을 받게 되고, 그때 members 는 전 필드가 기본값으로 떨어져 "탈퇴한 회원" 두 명으로 보인다(파싱은 깨지지 않는다 — `멤버 항목에 모르는 필드가 있거나 필드가 빠져도 파싱이 깨지지 않는다` 테스트가 그 경우다).

## 7. 테스트

`./gradlew :data:testDebugUnitTest` — **151 tests, 0 failures** (기존 대비 +21).

- `PartyMappersTest` (26) — 멤버 요약 → User 매핑, 요약 null(탈퇴) 시 id "" + "탈퇴한 회원", isMe 판정(일치/미로그인/자리표시), 실제 서버 JSON(정상 + 탈퇴 혼합) 역직렬화, 알 수 없는 필드·누락 필드 방어
- `RemoteAuthRepositoryTest` (24) — nickname/check 200 exists true/false, 공백 trim, 400 USER107 → INVALID_NICKNAME, 가입 요청에 nickname 실림, 가입 409 USER108 → NICKNAME_DUPLICATED, PATCH 본문(null 필드 제외)·409 매핑
- `RemoteRideRepositoryTest` (10) — 상세/합류 양쪽에서 세션 uuid 기준 isMe, 미로그인 시 아무도 나 아님
- `AuthMappersTest` (16) — USER106/107/108 코드 매핑, 가입 DTO 에 nickname 필드 포함, `AuthPolicy.isValidNickname` 경계값
- `AuthenticatedPathContractTest` (12) — `api/v1/auth/nickname/check` 경로 고정, auth 프리픽스 경로가 `UserApi` 에 섞이지 않음, `PATCH api/v1/users/me`

`./gradlew :app:assembleDebug` — BUILD SUCCESSFUL (presentation 은 compose-builder 작업 중 스냅샷 기준).

## 8. 후속 — `USER104` 전화번호 중복 분리 (리더 지시, 2026-09-07)

409 가 세 가지 뜻(아이디 `USER101` / 전화번호 `USER104` / 닉네임 `USER108`)을 갖는데 `USER104` 만 코드 매핑이 없어 폴백으로 `LOGIN_ID_DUPLICATED` 가 되던 결함을 고쳤다. `AuthError.PHONE_NUMBER_DUPLICATED` 추가 + `AuthMappers.toAuthException` 에 `"USER104" ->` 분기, 테스트 2건(`RemoteAuthRepositoryTest.가입 409 USER104 는 전화번호 중복으로 올라온다`, `AuthMappersTest.409 세 코드는 서로 다른 중복 사유로 갈린다`). presentation 문구는 손대지 않았고, compose-builder 가 이미 `SignupDraft.kt:79` 에 `"이미 가입된 전화번호예요. 로그인해 주세요"` 를 넣어 둬서 `:app:assembleDebug` 도 통과한다. 재검증: `:data:testDebugUnitTest` **153 tests, 0 failures**, `:app:assembleDebug` BUILD SUCCESSFUL(`--rerun-tasks` 로 전체 재컴파일 확인).
