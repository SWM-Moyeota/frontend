package com.moyeota.data.remote

import com.moyeota.data.remote.dto.UserProfileResponse
import com.moyeota.domain.model.UserProfileInfo

/**
 * `UserResponse` → [UserProfileInfo].
 *
 * 서버의 `name` 은 "닉네임 → 실명 → null" 폴백의 결과라 **빈 문자열도 없음으로 취급한다** —
 * 화면이 `name.isNullOrBlank()` 와 `name == ""` 를 따로 다루게 두면 폴백 표시가 두 갈래로 갈라진다.
 * 도메인에서 없음은 오직 `null` 하나다.
 */
internal fun UserProfileResponse.toDomain(): UserProfileInfo = UserProfileInfo(
    uuid = uuid,
    name = name?.takeIf { it.isNotBlank() },
)
