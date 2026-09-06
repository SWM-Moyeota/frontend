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
