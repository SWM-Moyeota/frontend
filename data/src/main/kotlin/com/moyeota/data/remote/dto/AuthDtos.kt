package com.moyeota.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * POST /api/v1/auth/register — 백엔드 `UserRegisterRequest` 와 필드 1:1.
 *
 * [birthDate] 는 서버가 `Instant` 로 받는다(예: "2000-01-01T00:00:00Z"). 날짜만 보내면
 * Jackson 이 역직렬화에 실패해 400 이 나므로 매퍼에서 UTC 자정으로 확장해 넣는다.
 * [gender] 는 서버 enum 이름 그대로 "MALE" / "FEMALE" / "UNDISCLOSED"(선택 안 함)다.
 * 서버 필드는 `@NotNull` 이라 **셋 중 하나가 반드시 실려야 한다** — 「선택 안 함」도 빈 문자열이나
 * 필드 누락이 아니라 `"UNDISCLOSED"` 라는 이름을 보내는 것이다(그래서 이 필드는 nullable 이 아니다).
 *
 * **"UNDISCLOSED" 는 2026-10-15 현재 운영 서버가 모르는 값이다.** 추가는 백엔드 PR #235 에 있고
 * 아직 머지·배포되지 않았다 — 운영 스펙(`/v3/api-docs`)의 이 필드는 여전히 `["MALE","FEMALE"]` 이라
 * 지금 보내면 400 `INVALID_REQUEST` 다. 앱 코드는 미리 맞춰 두고, 화면 노출은 서버 배포 뒤에 켠다.
 *
 * [nickname] 은 나중에 추가된 **필수** 필드다(서버 record 필드 순서도 password 다음이다).
 * 빠뜨리면 400, 형식이 틀리면 400 `USER107`, 이미 쓰는 값이면 409 `USER108` 이다.
 *
 * [verificationToken] 도 **필수**다(2026-10). 빠뜨리면 400 `INVALID_REQUEST`
 * (`message = "verificationToken: 휴대폰 인증이 필요합니다."`)라서, 서버 변경 직후 앱이
 * 가입 자체를 못 하는 상태가 됐던 필드다.
 */
@Serializable
data class RegisterRequestDto(
    val loginId: String,
    val password: String,
    val nickname: String,
    val name: String,
    val birthDate: String,
    val phoneNumber: String,
    val gender: String,
    val email: String,
    val verificationToken: String,
)

/**
 * POST /api/v1/auth/sms/send 의 요청 본문 — 백엔드 `SmsSendRequest`.
 *
 * 필드가 하나뿐이고 `@Pattern(^01[016-9]-?\d{3,4}-?\d{4}$)` 가 걸려 있어 **형식 위반은 VO 가 아니라
 * Bean Validation 에 먼저 걸린다** → 400 `INVALID_REQUEST`(`USER105` 가 아니다).
 * 확인 요청([SmsVerifyRequestDto])과 갈리는 지점이라 에러 매핑에서 헷갈리기 쉽다.
 *
 * 응답은 **본문 없는 204** 다.
 */
@Serializable
data class SmsSendRequestDto(
    val phoneNumber: String,
)

/**
 * POST /api/v1/auth/sms/verify 의 요청 본문 — 백엔드 `SmsVerifyRequest`.
 *
 * [phoneNumber] 에는 `@NotBlank` 만 있다(발송 쪽과 달리 `@Pattern` 이 없다) — 형식이 틀리면
 * 서버가 `PhoneNumber` VO 를 만들다 400 `USER105` 를 낸다.
 * [code] 는 `@Pattern(^\d{6}$)` — 6자리가 아니면 비교 전에 400 `INVALID_REQUEST` 다.
 */
@Serializable
data class SmsVerifyRequestDto(
    val phoneNumber: String,
    val code: String,
)

/**
 * 200 응답 — 백엔드 `SmsVerifyResponse(String verificationToken)`. 서버는 UUID 문자열을 담는다.
 *
 * 기본값 빈 문자열은 방어다. 다만 빈 값으로 가입을 보내면 400 이 나므로
 * Repository 가 여기서 끊는다([com.moyeota.data.repository.RemoteAuthRepository.verifyPhoneCode]).
 */
@Serializable
data class SmsVerifyResponse(
    val verificationToken: String = "",
)

/** 201 응답. 백엔드 `UserResponse(UUID uuid)`. 토큰은 주지 않는다 — 가입 후 로그인을 따로 해야 한다. */
@Serializable
data class RegisterResponse(
    val uuid: String = "",
)

/**
 * POST /api/v1/auth/nickname/check 의 요청 본문 — 백엔드 `NicknameCheckRequest(@NotBlank String nickname)`.
 *
 * 가입 폼에서 로그인 전에 부르는 값이라 [com.moyeota.data.remote.AuthApi] 쪽(Bearer 미부착
 * 클라이언트)에 있다. 컨트롤러 프리픽스가 `/api/v1/auth` 인 것도 같은 이유다 — 이 구간만 permitAll 이다.
 */
@Serializable
data class NicknameCheckRequestDto(
    val nickname: String,
)

/**
 * 200 응답 — 백엔드 `NicknameCheckResponse(boolean exists)`.
 *
 * 기본값 false 는 방어다: 필드가 빠진 응답을 "사용 중"으로 읽어 멀쩡한 닉네임을 막는 것보다
 * 통과시키고 가입에서 409 로 걸리는 편이 낫다.
 */
@Serializable
data class NicknameCheckResponse(
    val exists: Boolean = false,
)

/** POST /api/v1/auth/login — 백엔드 `UserLoginRequest`. */
@Serializable
data class LoginRequestDto(
    val loginId: String,
    val password: String,
)

/** POST /api/v1/auth/reissue 및 /api/v1/auth/logout 의 공통 본문 — 백엔드 `TokenRequest`. */
@Serializable
data class RefreshTokenRequestDto(
    val refreshToken: String,
)

/**
 * **로그인과 재발급이 공유하는** 200 응답. 백엔드 `TokenResponse(accessToken, refreshToken)`.
 *
 * 로그인 응답에 있던 `userId` 는 사라졌다 — 사용자 UUID 는 액세스 토큰의 `sub` 클레임에서 읽는다
 * ([com.moyeota.data.remote.auth.jwtSubject]).
 *
 * **회전(rotation) 방식이라 리프레시도 새 값으로 바뀐다** — 응답의 refreshToken 을 반드시 저장해야
 * 다음 재발급이 가능하다. 옛 리프레시는 서버에서 즉시 삭제돼 재사용 시 401 `USER001` 이다(실측).
 */
@Serializable
data class TokenResponse(
    val accessToken: String = "",
    val refreshToken: String = "",
)
