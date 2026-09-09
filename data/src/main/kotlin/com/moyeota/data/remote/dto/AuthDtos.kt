package com.moyeota.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * POST /api/v1/auth/register — 백엔드 `UserRegisterRequest` 와 필드 1:1.
 *
 * [birthDate] 는 서버가 `Instant` 로 받는다(예: "2000-01-01T00:00:00Z"). 날짜만 보내면
 * Jackson 이 역직렬화에 실패해 400 이 나므로 매퍼에서 UTC 자정으로 확장해 넣는다.
 * [gender] 는 서버 enum 이름 그대로 "MALE" / "FEMALE".
 *
 * [nickname] 은 나중에 추가된 **필수** 필드다(서버 record 필드 순서도 password 다음이다).
 * 빠뜨리면 400, 형식이 틀리면 400 `USER107`, 이미 쓰는 값이면 409 `USER108` 이다.
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
