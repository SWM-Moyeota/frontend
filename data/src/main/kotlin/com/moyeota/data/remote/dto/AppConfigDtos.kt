package com.moyeota.data.remote.dto

import kotlinx.serialization.Serializable

/** `AppConfigResponse` — 서버가 필드를 늘려도 깨지지 않게 모르는 키는 무시하고, 빠진 값은 서버 기본값(true)으로 */
@Serializable
data class AppConfigResponse(
    val taxiEnabled: Boolean = true,
)
