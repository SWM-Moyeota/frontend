package com.moyeota.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * `AppConfigResponse` — 서버가 필드를 늘려도 깨지지 않게 모르는 키는 무시한다.
 *
 * 서버는 아직 `taxiEnabled` 를 응답에 담지만(출시된 앱 호환용, 늘 `false`) 여기에 받지 않는다.
 * 기사·배차가 사라져 갈릴 것이 없다 — 모르는 키라 그냥 흘린다.
 */
@Serializable
data class AppConfigResponse(
    /**
     * 강제 업데이트 기준. **지금 서버는 주지 않는다**(2026-11-01 실측 — 응답은 `taxiEnabled` 하나).
     * 기본값이 null 이라 안 와도 역직렬화가 깨지지 않고, 서버가 나중에 추가하면 그날부터 걸린다.
     */
    val minVersionCode: Int? = null,
    /** 권장 업데이트 기준. 막지 않고 한 번 알리기만 한다. 위와 같은 이유로 null 기본값이다. */
    val recommendedVersionCode: Int? = null,
)
