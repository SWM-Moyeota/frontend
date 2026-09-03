package com.moyeota.data.remote

import com.moyeota.data.remote.dto.DriverLocationResponse
import com.moyeota.domain.model.DriverLocation

// 서버는 (longitude, latitude) 순서로 정의돼 있다. 도메인 모델은 지도 SDK 관례대로 위도를 앞에 둔다.
fun DriverLocationResponse.toDriverLocation(): DriverLocation = DriverLocation(
    latitude = latitude,
    longitude = longitude,
)
