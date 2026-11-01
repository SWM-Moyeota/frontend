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
    /**
     * 강제 업데이트 기준. **지금 서버는 주지 않는다**(2026-11-01 실측 — 응답은 `taxiEnabled` 하나).
     * 기본값이 null 이라 안 와도 역직렬화가 깨지지 않고, 서버가 나중에 추가하면 그날부터 걸린다.
     */
    val minVersionCode: Int? = null,
    /** 권장 업데이트 기준. 막지 않고 한 번 알리기만 한다. 위와 같은 이유로 null 기본값이다. */
    val recommendedVersionCode: Int? = null,
)
