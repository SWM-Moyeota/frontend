package com.moyeota.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * GET /api/v1/local/users/info 의 200 응답. 백엔드 `UserResponse(UUID uuid, String name)` 와 필드 1:1.
 *
 * 실측(localhost:8080, testuser1):
 * `{"uuid":"01a06145-3caf-7614-a3bd-cee6e25316b1","name":"김성윤"}`
 *
 * **[name] 이 nullable 인 건 방어가 아니라 서버 계약 그대로다.**
 * `LocalUserService.getProfile` 은 `user.nickname` 을 먼저 보고, 없으면 프로필 실명으로 폴백한 뒤
 * 그마저 없으면 `null` 을 내려보낸다(`orElse(null)`). 현재 서버에는 닉네임 설정 경로 자체가 없어
 * 항상 실명이 오지만, 프로필이 비어 있는 사용자에게는 `"name":null` 이 그대로 내려온다
 * — Jackson 이 null 을 지우지 않으므로 키는 남고 값만 null 이다. 논-널로 선언하면 그 순간 파싱이 터진다.
 *
 * [uuid] 의 기본값은 서버가 필드를 빼는 경우가 아니라 **빈 값 판정을 Repository 한 곳으로 모으기 위한 것**이다
 * (`RegisterResponse` 와 같은 방식).
 */
@Serializable
data class UserProfileResponse(
    val uuid: String = "",
    val name: String? = null,
)

/**
 * PATCH /api/v1/users/me 의 요청 본문 — 백엔드 `UpdateProfileRequest(String nickname, String imageUrl)`.
 *
 * **null 필드는 본문에서 아예 빠진다**(kotlinx 의 `encodeDefaults=false` 기본값 + 두 필드의 기본값이 null).
 * 서버가 "넘어온 것만 갱신"하는 계약이라 이게 정확히 맞는 동작이다 — null 을 실어 보내도 결과는 같지만,
 * 빼는 쪽이 의도가 분명하다. 둘 다 null 이면 서버는 아무것도 하지 않고 204 를 준다.
 *
 * 대상 사용자는 `@CurrentUser` 가 Bearer 에서 뽑으므로 본문에 식별자를 넣을 자리가 없다.
 */
@Serializable
data class UpdateProfileRequest(
    val nickname: String? = null,
    val imageUrl: String? = null,
)

/**
 * PUT /api/v1/users/me/fcm-token 의 요청 본문.
 * 백엔드 `user.application.dto.RegisterFcmTokenRequest(@NotBlank String token)` 와 필드 1:1 —
 * 키 이름은 반드시 `token` 이다.
 *
 * **누구의 토큰인지는 싣지 않는다.** 대상 사용자는 `@CurrentUser` 가 Bearer 에서 뽑으므로
 * 본문에 userId 를 넣을 자리가 없다(넣어도 무시된다).
 *
 * 빈 문자열은 서버가 400 으로 튕긴다(`@NotBlank`). FCM SDK 가 빈 토큰을 주는 일은 없지만,
 * 보내기 전에 [com.moyeota.data.push.FcmTokenRegistrar] 가 한 번 더 걸러 낸다 —
 * 400 을 받아 봐야 앱이 할 수 있는 일이 없기 때문이다.
 */
@Serializable
data class FcmTokenRequest(
    val token: String,
)
