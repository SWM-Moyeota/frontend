package com.moyeota.app

import com.moyeota.data.remote.NetworkModule
import com.moyeota.data.repository.RemoteRideRepository
import com.moyeota.domain.repository.RideRepository

// 수동 DI 컨테이너. 백엔드 연동 시 Hilt 로 교체 예정.
class AppContainer {
    // 에뮬레이터에서 호스트 로컬 백엔드(localhost:8080) 접근 주소
    private val matchingApi = NetworkModule.createMatchingApi("http://10.0.2.2:8080/")

    val rideRepository: RideRepository = RemoteRideRepository(matchingApi)
}
