package com.moyeota.data.remote

import com.moyeota.data.remote.dto.LoginMethodsDto
import com.moyeota.domain.model.*

internal fun LoginMethodsDto.toDomain() = LoginMethods(hasPassword, methods.map {
    LoginMethod(SocialProvider.valueOf(it.provider), LoginMethodStatus.valueOf(it.status))
})
