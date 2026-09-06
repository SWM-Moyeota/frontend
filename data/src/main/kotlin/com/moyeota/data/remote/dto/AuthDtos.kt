package com.moyeota.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * POST /api/v1/auth/register — 백엔드 `UserRegisterRequest` 와 필드 1:1.
 *
 * [birthDate] 는 서버가 `Instant` 로 받는다(예: "2000-01-01T00:00:00Z"). 날짜만 보내면
 * Jackson 이 역직렬화에 실패해 400 이 나므로 매퍼에서 UTC 자정으로 확장해 넣는다.
 * [gender] 는 서버 enum 이름 그대로 "MALE" / "FEMALE".
 */
@Serializable
data class RegisterRequestDto(
    val loginId: String,
    val password: String,
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
