package com.moyeota.app

import android.content.Context
import com.moyeota.data.push.FcmTokenRegistrar
import com.moyeota.data.remote.NetworkModule
import com.moyeota.data.repository.RemoteAuthRepository
import com.moyeota.data.repository.RemoteChatRepository
import com.moyeota.data.repository.RemoteDispatchRepository
import com.moyeota.data.repository.RemotePlaceRepository
import com.moyeota.data.repository.RemoteRideRepository
import com.moyeota.data.session.DataStoreTokenStorage
import com.moyeota.data.session.SessionManager
import com.moyeota.domain.repository.AuthRepository
import com.moyeota.domain.repository.ChatRepository
import com.moyeota.domain.repository.DispatchRepository
import com.moyeota.domain.repository.PlaceRepository
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

    // 화면이 로그인 상태와 내 UUID 를 얻는 단일 출처(내부 사용자 PK 는 앱에 없다).
    val userSession: UserSession = sessionManager

    // 긴급 신고에 싣는 실측 좌표 — 권한 없음/타임아웃이면 null 을 돌려주고 신고는 계속된다.
    private val reportLocationProvider = ReportLocationProvider(context.applicationContext)

    /**
     * FCM 기기 토큰 ↔ 서버 등록 상태를 맞추는 창구.
     *
     * 컨테이너 수명 = 앱 프로세스 수명이라 "보류 중인 토큰"이 화면 전환에도 살아남는다
     * (매번 새로 만들면 로그인 화면에서 받아 둔 토큰을 로그인 시점에 잃는다).
     * 등록/해제 호출 시점은 [authRepository] 안에 있고([RemoteAuthRepository] 참조),
     * 앱 시작·토큰 회전 경로만 [MoyeotaApplication] 이 직접 부른다.
     */
    val fcmTokenRegistrar = FcmTokenRegistrar(apis.user, sessionManager)

    val authRepository: AuthRepository =
        RemoteAuthRepository(apis.auth, apis.user, sessionManager, fcmTokenRegistrar)
    // 세션을 넘기는 건 방 상세 멤버 중 "나"를 매퍼가 판정하기 위해서다(RemoteRideRepository KDoc).
    val rideRepository: RideRepository = RemoteRideRepository(
        apis.matching,
        sessionManager,
        reportApi = apis.report,
        currentLocation = reportLocationProvider::current,
    )
    val placeRepository: PlaceRepository = RemotePlaceRepository(apis.place)
    // 세션을 넘기는 건 메시지의 "내 것" 판정 때문이다(RemoteChatRepository KDoc).
    val chatRepository: ChatRepository = RemoteChatRepository(apis.chat, sessionManager)
    val dispatchRepository: DispatchRepository = RemoteDispatchRepository(apis.dispatch)
}
