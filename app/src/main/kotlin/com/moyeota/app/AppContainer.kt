package com.moyeota.app

import android.content.Context
import android.util.Log
import com.moyeota.data.local.DataStoreActivePartyStorage
import com.moyeota.data.push.FcmTokenRegistrar
import com.moyeota.data.remote.NetworkModule
import com.moyeota.data.remote.matching.PartyEventSource
import com.moyeota.data.repository.RemoteActivePartyRepository
import com.moyeota.data.repository.RemoteAppConfigRepository
import com.moyeota.data.repository.RemoteAuthRepository
import com.moyeota.data.remote.chat.ChatSocket
import com.moyeota.data.repository.RemoteChatRepository
import com.moyeota.data.repository.RemotePlaceRepository
import com.moyeota.data.repository.RemoteNoShowRepository
import com.moyeota.data.repository.RemoteRideRecordRepository
import com.moyeota.data.repository.RemoteRideRepository
import com.moyeota.data.session.DataStoreTokenStorage
import com.moyeota.data.session.SessionManager
import com.moyeota.domain.repository.ActivePartyRepository
import com.moyeota.domain.repository.AppConfigRepository
import com.moyeota.domain.repository.AuthRepository
import com.moyeota.domain.repository.ChatRepository
import com.moyeota.domain.repository.PlaceRepository
import com.moyeota.domain.repository.NoShowRepository
import com.moyeota.domain.repository.RideRecordRepository
import com.moyeota.domain.repository.RideRepository
import com.moyeota.domain.session.UserSession
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

// 수동 DI 컨테이너. 백엔드 연동 시 Hilt 로 교체 예정.
// debugLogging: HTTP 본문 로깅 여부. 디버그 빌드에서만 켠다(호출자가 판단해 주입).
class AppContainer(context: Context, debugLogging: Boolean) {

    /**
     * 화면 생명주기와 무관하게 살아야 하는 작업(세션 디스크 복원·토큰 저장) 전용 스코프.
     *
     * `SupervisorJob`: 저장 실패 하나가 이후 저장까지 막지 않게 한다. **다만 SupervisorJob 은
     * 형제 코루틴의 취소만 막고 예외를 삼키지 않는다** — `launch` 의 미처리 예외는 그대로
     * [CoroutineExceptionHandler] → (없으면) `Thread.uncaughtExceptionHandler` → 안드로이드 기본
     * 핸들러(프로세스 종료)로 간다. 그래서 핸들러를 **마지막 방어선**으로 직접 붙인다:
     * 세션 복원·저장 경로는 각자 자기 예외를 잡지만([com.moyeota.data.session.SessionManager]),
     * 앞으로 이 스코프에 올라올 작업이 빠뜨렸을 때 앱이 죽는 대신 로그만 남게 한다.
     *
     * 로그에는 **예외 종류와 메시지만** 남긴다 — 이 스코프를 타는 값에 토큰·전화번호가 섞여 있어
     * 스택트레이스나 값을 찍으면 그대로 logcat 에 새어 나간다.
     */
    private val applicationScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "애플리케이션 스코프의 처리되지 않은 예외: ${throwable.javaClass.simpleName}: ${throwable.message}")
        },
    )

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

    /**
     * 프로필 사진 로더가 쓰는 OkHttp. **인증 인터셉터가 붙지 않은** 클라이언트다
     * (근거는 [NetworkModule.Apis.imageClient] KDoc — 이미지는 모여타 API 가 아니라 CDN 에서 온다).
     *
     * [MoyeotaApplication.newImageLoader] 가 이걸 Coil 에 넘긴다. 넘기지 않으면 Coil 이 자기
     * 기본 OkHttpClient 를 따로 만들어 쓰는데, **동작은 하기 때문에** 커넥션 풀이 하나 더 생기고
     * 여기서 정한 타임아웃이 적용되지 않는 걸 눈치채기 어렵다.
     */
    val imageClient: OkHttpClient = apis.imageClient

    /**
     * FCM 기기 토큰 ↔ 서버 등록 상태를 맞추는 창구.
     *
     * 컨테이너 수명 = 앱 프로세스 수명이라 "보류 중인 토큰"이 화면 전환에도 살아남는다
     * (매번 새로 만들면 로그인 화면에서 받아 둔 토큰을 로그인 시점에 잃는다).
     * 등록/해제 호출 시점은 [authRepository] 안에 있고([RemoteAuthRepository] 참조),
     * 앱 시작·토큰 회전 경로만 [MoyeotaApplication] 이 직접 부른다.
     */
    val fcmTokenRegistrar = FcmTokenRegistrar(apis.user, sessionManager)

    /**
     * "진행 중인 방" 로컬 기억. **인스턴스를 하나만 만들어 두 곳이 나눠 쓴다** —
     * [activePartyRepository] 가 읽고 쓰며, [authRepository] 는 탈퇴 때 비운다
     * (로그아웃은 비우지 않는다 — [RemoteAuthRepository.deleteAccount] KDoc).
     * DataStore 는 같은 파일을 두 번 열면 IllegalStateException 이라 선언도 한 번뿐이어야 한다.
     */
    private val activePartyStorage = DataStoreActivePartyStorage(context)

    val authRepository: AuthRepository =
        RemoteAuthRepository(apis.auth, apis.user, sessionManager, fcmTokenRegistrar, activePartyStorage)
    /**
     * 서버 운영 설정(`GET /api/v1/config`). 앱 시작 시 한 번 읽는다([AppConfigViewModel]).
     * 택시 플래그를 걷어낸 뒤로 남은 것은 버전 게이트뿐이고, 조회가 실패하면
     * **아무도 막지 않는** 기본값으로 간다([com.moyeota.domain.model.AppConfig.Default]).
     */
    val appConfigRepository: AppConfigRepository = RemoteAppConfigRepository(apis.config)
    // 세션을 넘기는 건 방 상세 멤버 중 "나"를 매퍼가 판정하기 위해서다(RemoteRideRepository KDoc).
    val rideRepository: RideRepository = RemoteRideRepository(
        apis.matching,
        sessionManager,
        partyEvents = PartyEventSource(BuildConfig.BASE_URL, apis.streamClient),
    )
    val placeRepository: PlaceRepository = RemotePlaceRepository(apis.place)

    /**
     * 34 지난 탑승 기록(`GET /api/v1/users/me/parties`). **끝난 탑승만 다룬다** —
     * 「지금 타고 있는 방」은 [activePartyRepository] 쪽이고, 둘을 섞지 않는다.
     * 이 응답에는 진행 중인 방이 들어오지 않아(실측 2026-11-01, 로컬 `:8080`) 서로를 대체할 수 없다.
     *
     * 세션을 넘기는 건 조회가 아니라 **번역** 때문이다 — 멤버마다 `isMe` 가 오지 않아 매퍼가
     * `publicId == currentUserUuid` 로 「나」를 판정한다(RemoteRideRecordRepository KDoc).
     */
    val rideRecordRepository: RideRecordRepository =
        RemoteRideRecordRepository(apis.user, sessionManager)

    /**
     * 38 노쇼 기록·이의 신청. 매칭 API 를 그대로 쓴다 — 서버가 두 경로를 `/api/v1/matching` 아래에
     * 두었다(`NoShowController`). 따로 Retrofit 인터페이스를 만들면 같은 프리픽스가 두 곳에 생긴다.
     *
     * [rideRecordRepository] 와 가르는 이유: 그쪽은 **탄** 방(커서 페이징·읽기 전용), 이쪽은 **안 탄
     * 것으로 기록된** 방이고 이의 신청이라는 **쓰기**까지 있다.
     */
    val noShowRepository: NoShowRepository = RemoteNoShowRepository(apis.matching)
    // 세션을 넘기는 건 메시지의 "내 것" 판정 때문이다(RemoteChatRepository KDoc).
    // 채팅 받기는 실시간(STOMP) + 폴링, 보내기·읽음은 REST 다(소켓 서버는 SEND 를 받지 않는다).
    // 소켓의 TOKEN_EXPIRED 재발급은 HTTP 401 과 같은 잠금을 쓴다(apis.renewAccessToken).
    val chatRepository: ChatRepository = RemoteChatRepository(
        apis.chat,
        sessionManager,
        ChatSocket(BuildConfig.BASE_URL, apis.socketClient, sessionManager, apis.renewAccessToken),
    )

    /**
     * "지금 내가 타고 있는 방"의 단일 출처. 서버에 `GET /matching/rooms/me` 가 없어
     * 앱이 방 id 를 로컬에 기억했다가 상세 조회로 되살린다(RemoteActivePartyRepository KDoc).
     * rideRepository·chatRepository 를 그대로 쓰므로 **둘의 선언 아래**에 둔다.
     */
    val activePartyRepository: ActivePartyRepository = RemoteActivePartyRepository(
        rideRepository = rideRepository,
        chatRepository = chatRepository,
        storage = activePartyStorage,
        session = sessionManager,
    )

    private companion object {
        const val TAG = "AppContainer"
    }
}
