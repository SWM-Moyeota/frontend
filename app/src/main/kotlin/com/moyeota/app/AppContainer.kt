package com.moyeota.app

import android.content.Context
import com.moyeota.data.remote.NetworkModule
import com.moyeota.data.repository.RemoteAuthRepository
import com.moyeota.data.repository.RemoteChatRepository
import com.moyeota.data.repository.RemoteDispatchRepository
import com.moyeota.data.repository.RemotePlaceRepository
import com.moyeota.data.repository.RemoteReportRepository
import com.moyeota.data.repository.RemoteRideRepository
import com.moyeota.data.session.DataStoreTokenStorage
import com.moyeota.data.session.SessionManager
import com.moyeota.domain.repository.AuthRepository
import com.moyeota.domain.repository.ChatRepository
import com.moyeota.domain.repository.DispatchRepository
import com.moyeota.domain.repository.PlaceRepository
import com.moyeota.domain.repository.ReportRepository
import com.moyeota.domain.repository.RideRepository
import com.moyeota.domain.session.UserSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

// 수동 DI 컨테이너. 백엔드 연동 시 Hilt 로 교체 예정.
// debugLogging: HTTP 본문 로깅 여부. 디버그 빌드에서만 켠다(호출자가 판단해 주입).
class AppContainer(context: Context, debugLogging: Boolean) {

    // 화면 생명주기와 무관하게 살아야 하는 작업(세션 디스크 복원·토큰 저장) 전용 스코프.
    // SupervisorJob: 저장 실패 하나가 이후 저장까지 막지 않게 한다.
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 세션 단일 출처. 생성 즉시 디스크 복원을 시작하므로 앱 재시작 후에도 로그인이 유지된다.
    private val sessionManager = SessionManager(
        storage = DataStoreTokenStorage(context),
        scope = applicationScope,
    )

    // 기본값은 에뮬레이터에서 호스트 로컬 백엔드(localhost:8080)를 가리키는 10.0.2.2 주소.
    // 실기기 테스트는 local.properties 또는 환경변수의 MOYEOTA_BASE_URL 로 오버라이드한다
    // (app/build.gradle.kts 참조).
    private val apis = NetworkModule.create(BuildConfig.BASE_URL, debugLogging, sessionManager)

    // 화면이 memberId(고정 1L)와 로그인 상태를 얻는 단일 출처.
    val userSession: UserSession = sessionManager

    val authRepository: AuthRepository = RemoteAuthRepository(apis.auth, apis.user, sessionManager)
    val rideRepository: RideRepository = RemoteRideRepository(apis.matching)
    val placeRepository: PlaceRepository = RemotePlaceRepository(apis.place)
    val chatRepository: ChatRepository = RemoteChatRepository(apis.chat)
    val dispatchRepository: DispatchRepository = RemoteDispatchRepository(apis.dispatch)
    val reportRepository: ReportRepository = RemoteReportRepository(apis.report)
}
