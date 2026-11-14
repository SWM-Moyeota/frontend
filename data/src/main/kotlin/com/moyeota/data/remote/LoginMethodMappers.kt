package com.moyeota.data.remote

import com.moyeota.data.remote.dto.LoginMethodsDto
import com.moyeota.domain.model.*

/**
 * 서버가 provider·status 값을 늘려도 구버전 앱의 로그인 수단·탈퇴 화면이 막히지 않게 한다.
 * 모르는 provider는 건너뛰고, 모르는 status는 ACTIVE로 오인하지 않도록 REVOKED(재인증 필요)로 본다.
 */
internal fun LoginMethodsDto.toDomain() = LoginMethods(hasPassword, methods.mapNotNull { method ->
    val provider = SocialProvider.entries.firstOrNull { it.name == method.provider } ?: return@mapNotNull null
    val status = LoginMethodStatus.entries.firstOrNull { it.name == method.status } ?: LoginMethodStatus.REVOKED
    LoginMethod(provider, status)
})
