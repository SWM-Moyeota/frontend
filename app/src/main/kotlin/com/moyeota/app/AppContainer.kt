package com.moyeota.app

import com.moyeota.data.remote.NetworkModule
import com.moyeota.data.repository.RemoteChatRepository
import com.moyeota.data.repository.RemotePlaceRepository
import com.moyeota.data.repository.RemoteRideRepository
import com.moyeota.data.session.FixedUserSession
import com.moyeota.domain.repository.ChatRepository
import com.moyeota.domain.repository.PlaceRepository
import com.moyeota.domain.repository.RideRepository
import com.moyeota.domain.session.UserSession

// 수동 DI 컨테이너. 백엔드 연동 시 Hilt 로 교체 예정.
// debugLogging: HTTP 본문 로깅 여부. 디버그 빌드에서만 켠다(호출자가 판단해 주입).
class AppContainer(debugLogging: Boolean) {
    // 에뮬레이터에서 호스트 로컬 백엔드(localhost:8080) 접근 주소
    private val apis = NetworkModule.create("http://10.0.2.2:8080/", debugLogging)

    // 인증 미구현 — 화면이 memberId/userId/X-User-Id 를 얻는 단일 출처.
    val userSession: UserSession = FixedUserSession()

    val rideRepository: RideRepository = RemoteRideRepository(apis.matching)
    val placeRepository: PlaceRepository = RemotePlaceRepository(apis.place)
    val chatRepository: ChatRepository = RemoteChatRepository(apis.chat)
}
