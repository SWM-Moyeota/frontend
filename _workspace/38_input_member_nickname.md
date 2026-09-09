# 38 · 입력 — 동승자 표시 정보 + 닉네임(user API) 연동

작성 2026-09-07 (리더). 백엔드 브랜치 `feature/user-fcm-token` 워킹트리(미커밋) 기준, `./gradlew compileJava` 통과 확인.

## 사용자 요청 (원문)
"동승자 표시 정보쪽 API 연결했어 한번 봐봐 추가적으로 user쪽도 API 수정했는데 맞춰서 화면등 작성해줘"

## 백엔드 변경 계약 (리더가 소스로 확정)

### 1. 방 상세 `GET /api/v1/matching/rooms/{partyId}` (join 응답 동일) — `members` 항목 **브레이킹**
```
// 이전
MemberInfo(Long memberId, Instant joinedAt)
// 이후
MemberInfo(UUID publicId, String nickname, String imageUrl, String badgeId, Integer rideCount, Instant joinedAt)
```
- `memberId`(내부 PK) **제거**. 식별자는 `publicId`(UUID v7) — JWT `sub`와 같은 값이므로 **"나" 판정 = publicId == 세션 currentUserUuid** 가 가능해졌다(기존 「나 제외」 필터 결함 해소).
- `rideCount` = 해당 멤버의 FINISHED 파티 수 (`PartyJpaRepository.countByMemberIdsAndStatus`). 0 기본.
- `badgeId` 는 서버 TODO — **항상 null**. (`MemberSummary.badgeId`는 Long, `MemberInfo.badgeId`는 String — 타입 불일치, 리뷰 코멘트 대상)
- 유저 요약이 없으면(탈퇴 등) `publicId/nickname/imageUrl/badgeId` 전부 null, `rideCount`·`joinedAt`만 채워짐 → **null 허용 필수**.

### 2. 가입 `POST /api/v1/auth/register` — `nickname` **필수 추가**
- 요청 필드 순서: `loginId, password, nickname, name, birthDate, phoneNumber, gender, email`
- 규칙(도메인 `Nickname` VO): 앞뒤 공백 strip 후 `^[가-힣a-zA-Z0-9]{2,10}$` — 공백·특수문자 불가.
- 에러: 형식 위반 **400 USER107** `INVALID_NICKNAME`, 중복 **409 USER108** `NICKNAME_DUPLICATED`. 검사 순서 loginId 중복 → 전화번호 중복 → 닉네임 중복.
- 신규 **USER106** `EMPTY_FCM_TOKEN`(400) — fcm-token PUT 빈 토큰.

### 3. 신규 `POST /api/v1/auth/nickname/check` — 비보호(`/auth/**` permitAll)
- 요청 `{ "nickname": "..." }`, 응답 `{ "exists": boolean }` (200)
- 형식이 틀리면 **400 USER107** (exists 판단 전에 VO 검증).

### 4. 프로필 `GET /api/v1/local/users/info` → `UserResponse(uuid, name)` (모양 동일)
- `name` 은 이제 **닉네임**(로컬 가입은 항상 있음), 없으면 실명 폴백. 홈 인사말은 그대로 두면 닉네임이 뜬다.

### 5. 신규 `PATCH /api/v1/users/me` `{ nickname?, imageUrl? }` → 204 (넘어온 것만 갱신)
- 이번 범위: Repository 메서드만 추가(화면 없음 — 프로필 수정 화면은 와이어프레임에 없음).

## 프론트 도메인 계약 (리더 확정 — 양 에이전트 공통)

```kotlin
// domain/model/User.kt
data class User(
    val id: String,              // publicId. 요약 없음(탈퇴)이면 ""
    val nickname: String,        // 서버 nickname; null 이면 "탈퇴한 회원"
    val verifiedLabel: String,   // badgeId 매핑 자리 — 서버가 항상 null 이므로 지금은 ""
    val rating: Double,          // 평가 API 없음 → 0.0 고정. 화면은 0.0 을 "평가 없음"으로 취급(가짜 4.9·매너 98% 금지)
    val rideCount: Int,          // 서버 rideCount
    val imageUrl: String? = null,
    val isMe: Boolean = false,   // publicId == UserSession.currentUserUuid
)

// domain/model/NewUser.kt — nickname: String 추가
// domain/repository/AuthRepository.kt
suspend fun isNicknameTaken(nickname: String): Boolean        // POST /auth/nickname/check → exists. 400 USER107 → AuthException.InvalidNickname
suspend fun updateProfile(nickname: String?, imageUrl: String?) // PATCH /users/me (화면 미연결)
// AuthException — 기존 계층에 InvalidNickname(USER107), NicknameDuplicated(USER108) 추가
// RemoteRideRepository(api, session: UserSession, local) — 매퍼가 isMe 계산 (AppContainer 배선)
```

클라이언트 닉네임 사전 검증(서버와 동일): trim 후 `^[가-힣a-zA-Z0-9]{2,10}$`. 기존 10 화면의 "표시 이름 3~8자·금칙어" 규칙을 이 규칙으로 교체(금칙어 검사는 유지해도 됨).

## 작업 범위

### api-integrator
- `PartyDtos.MemberInfo` 교체(전부 nullable + rideCount 기본 0), `PartyMappers` 멤버 매핑(User 계약대로, isMe), `RemoteRideRepository` 세션 주입, `AppContainer` 배선
- `UserDtos`: RegisterRequest nickname, NicknameCheckRequest/Response, UpdateProfileRequest; `UserApi` 2개 추가; `RemoteAuthRepository` 메서드·에러코드(USER106/107/108) 매핑; `NewUser.nickname`
- 테스트: `PartyMappersTest`(정상·null 요약·isMe), `RemoteAuthRepositoryTest`(닉네임 체크 200/400, 가입 409 USER108), `AuthenticatedPathContractTest`(`/auth/nickname/check` 비보호)
- 리포트 `_workspace/39_api-integrator_member_nickname.md`

### compose-builder
- **10 프로필 만들기**: 표시 이름 → 닉네임 규칙(2~10자 한글·영문·숫자, 카운터 n/10), 입력 멈춤 500ms 후 `isNicknameTaken` 호출 → "사용 가능한 닉네임이에요"/"이미 사용 중인 닉네임이에요", 400 형식 오류 문구. `SignupDraft.nickname` 로 흘려 `MainNavGraph`/`SignupSubmitRoute`에서 가입 요청에 포함(MainNavGraph:255 주석 해소). 가입 409 USER108 → 인라인 배너.
- **21/22 대기(MatchWaitingScreen/Route)**: 멤버 실닉네임·아바타(imageUrl 없으면 이니셜 원)·"탑승 N회"(0이면 "첫 탑승")·**나 배지**, 「나 제외」 카운트가 isMe 기준으로 정확히.
- **20 합류 확인(JoinConfirmScreen)**: 기존 멤버 실데이터, `매너 N%` 제거(평가 없음).
- **23 동승자 프로필(PartnerProfileScreen)**: 실 User 전달(nickname·rideCount·imageUrl), 매너 셀 "평가 준비 중", verifiedLabel 빈값이면 "인증 정보 없음" 중립 문구, 데모 "김OO/12회" 제거.
- **24 RideDetailScreen**: "탑승 N회 · 매너 98%" → 매너 제거, 나 표시. **25 DispatchStatusScreen** 동승자 실닉네임.
- 탈퇴 회원(id "") 은 탭 불가·"탈퇴한 회원" 표기. Explore 목록의 자리표시 멤버는 유지(목록 API에 멤버 없음).
- 리포트 `_workspace/40_compose-builder_member_nickname.md`

### qa-verifier
- 빌드·유닛 테스트, 경계면 교차 비교(DTO↔서버 record 필드명), 실기: 가입(닉네임 형식 오류·중복·정상) → 방 생성 → dcall2 curl 합류 → 21 두 멤버 닉네임·나 배지·탑승 N회 → 23 프로필 → 20 합류 확인(다른 계정 관점) → 홈 인사말 닉네임. 회귀: 로그인·25 배차 폴링(members 파싱 실패 없음).
- 리포트 `_workspace/41_qa-verifier_member_nickname.md`

## 환경
- 프론트 브랜치 `feature/mvp1-followup`(PR #6 열림). 백엔드 localhost:8080 local 프로필 기동 중(리더가 띄움 — 죽이지 말 것). H2 인메모리라 계정 시드 필요: `POST /auth/register` (nickname 필수!). 기사 시드: 가입→로그인→`POST /drivers`→`POST /drivers/verify`.
- 에뮬레이터 Pixel_6(5554) 콜드부트. 5558 은 사용하지 않는다.
