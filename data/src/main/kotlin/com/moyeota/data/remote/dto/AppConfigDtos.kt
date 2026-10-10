package com.moyeota.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * `AppConfigResponse` — 서버가 필드를 늘려도 깨지지 않게 모르는 키는 무시한다.
 *
 * 빠진 값은 **동승 모드(false)** 로 본다. 서버 쪽 프로퍼티 기본값(`moyeota.taxi.enabled` 미설정 = true)과
 * 일부러 다르다 — 응답에 키가 없다는 건 설정을 모른다는 뜻이고, 모를 땐 실배포 모드가 안전하다
 * ([com.moyeota.domain.model.AppConfig.Default] 와 같은 이유). 서버 컨트롤러는 항상 이 키를 내려주므로
 * 실제로는 거의 타지 않는 분기다.
 */
@Serializable
data class AppConfigResponse(
    val taxiEnabled: Boolean = false,
)
