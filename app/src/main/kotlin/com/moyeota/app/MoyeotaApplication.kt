package com.moyeota.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Log
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.util.DebugLogger
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * [SingletonImageLoader.Factory] 를 **구현**하는 쪽을 골랐다 — `onCreate` 에서
 * `SingletonImageLoader.setSafe` 를 부르는 방식과 비교해서다.
 *
 * `setSafe` 는 이미 기본 로더가 만들어진 뒤에 불리면 `IllegalStateException` 으로 터진다. 반면
 * Coil 의 안드로이드 구현은 첫 요청 때 `applicationContext as? SingletonImageLoader.Factory` 로
 * 직접 찾아가므로, 이렇게 두면 **호출 순서에 의존하는 경로가 아예 없어진다.**
 */
class MoyeotaApplication : Application(), SingletonImageLoader.Factory {
    lateinit var appContainer: AppContainer
        private set

    /**
     * 화면 수명과 무관하게 살아야 하는 앱 차원의 백그라운드 작업(FCM 토큰 서버 등록) 전용 스코프.
     * SupervisorJob: 등록 실패 하나가 이후 등록까지 막지 않게 한다.
     */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // 컨테이너보다 **먼저** 켠다. 컨테이너 조립이 터지면 그 크래시도 받아야 하는데,
        // 수집 스위치가 뒤에 있으면 바로 그 사고만 영영 못 본다.
        configureCrashReporting()
        appContainer = AppContainer(context = this, debugLogging = isDebuggableBuild())
        // 토큰이 비어 있으면 아무것도 꽂지 않는다(Analytics.track 무동작). 세션 구독은 appScope 수명만큼 산다.
        MixpanelAnalyticsReporter.install(
            context = this,
            token = BuildConfig.MIXPANEL_TOKEN,
            session = appContainer.userSession,
            debuggable = isDebuggableBuild(),
            scope = appScope,
        )
        createNotificationChannel()
        fetchFcmToken()
    }

    /**
     * 크래시 수집 스위치.
     *
     * **디버그 빌드에서는 끈다.** 개발 중 일부러 낸 크래시가 운영 대시보드에 섞이면
     * 「크래시 없는 사용자 %」가 무의미해지고, 진짜 사고가 개발 소음에 묻힌다.
     * 판정은 이미지 로거와 **같은 근거**([isDebuggableBuild], `FLAG_DEBUGGABLE`)를 쓴다 —
     * `BuildConfig.DEBUG` 를 쓰면 모듈마다 다른 BuildConfig 를 보게 되는 함정이 있다.
     *
     * ### setUserId 를 부르지 않는다
     * `publicId`(uuid)를 넣으면 「누가 겪은 크래시인가」를 알 수 있어 편하지만, 그 값은 서버에서
     * 실명·휴대폰번호로 되짚히는 **계정 식별자**다. 보내는 순간 크래시 보고가 「이용자와 연결된
     * 데이터」가 되어 처리방침에 계정 식별자의 국외 이전을 따로 적어야 하고, 삭제 요구가 오면
     * Google 쪽 기록까지 지워야 한다. 얻는 것보다 비용이 크다.
     *
     * ### 보고에 넣으면 안 되는 것
     * `log()`·커스텀 키·예외 메시지 어디에도 **로그인 토큰·휴대폰번호·이메일·실명·좌표·채팅 본문**을
     * 넣지 않는다. 특히 서버 응답에서 온 예외를 그대로 넘기면 안 된다 — kotlinx.serialization 의
     * 파싱 실패 메시지는 **응답 본문 조각을 그대로 담는다.** 그래서 [CrashlyticsErrorReporter] 가
     * 원본 예외를 넘기지 않고 종류와 지점만 남긴 예외를 새로 만들어 보낸다.
     */
    private fun configureCrashReporting() {
        runCatching {
            FirebaseCrashlytics.getInstance()
                .setCrashlyticsCollectionEnabled(!isDebuggableBuild())
            // 삼켜진 실패를 non-fatal 로 받는 통로. 꽂기 전의 보고는 조용히 버려지므로
            // 수집 스위치 바로 뒤, 컨테이너 조립보다 먼저 꽂는다.
            CrashlyticsErrorReporter.install()
        }.onFailure {
            // 수집 스위치를 못 켠 것이 앱 시작을 막으면 안 된다. 최악은 크래시를 못 보는 것뿐이다.
            Log.w(TAG, "Crashlytics 설정 실패", it)
        }
    }

    /**
     * 프로필 사진용 [ImageLoader]. Coil 이 첫 요청 때 **한 번만** 부른다.
     *
     * ### 왜 OkHttp 를 직접 넘기나
     * `coil-network-okhttp` 는 ServiceLoader 로 자기 기본 페처를 등록하므로 **아무것도 넘기지 않아도
     * 이미지는 뜬다.** 그래도 명시해 넘기는 이유는 "이미지 요청에는 토큰이 붙지 않는다"를 기본값에
     * 의존하지 않고 **코드로 못 박기** 위해서다([AppContainer.imageClient] KDoc 에 근거).
     * 인증 클라이언트가 섞이면 액세스 토큰이 CDN 액세스 로그로 새어 나간다.
     *
     * `OkHttpNetworkFetcherFactory(callFactory = ...)` 에서 **이름 인자를 빼면 컴파일되지 않는다** —
     * `() -> Call.Factory` 를 첫 인자로 받는 오버로드가 셋이라 trailing lambda 형태
     * (`OkHttpNetworkFetcherFactory { ... }`)는 overload resolution ambiguity 다.
     *
     * ### 캐시
     * 프리셋 PNG 는 512x512 에 장당 약 160KB 이고 CDN 이 `cache-control: max-age=31536000, immutable`
     * 을 주므로, 한 번 받으면 디스크 캐시에서 끝난다. 디렉터리 이름을 직접 지정하는 이유는 Coil 의
     * 기본 디스크 캐시가 **프로세스 전역 싱글톤 디렉터리**라, 같은 경로에 인스턴스가 둘 생기면
     * 캐시가 깨진다고 Coil 자신이 경고하기 때문이다.
     *
     * [MemoryCache.Builder] 는 **크기를 지정하지 않고 `build()` 하면 런타임에 터진다**(`maxSizeBytesFactory == null`).
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { appContainer.imageClient })) }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.15).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("moyeota_image_cache"))
                    .maxSizeBytes(32L * 1024 * 1024)
                    .build()
            }
            // ★ crossfade 를 켜지 않는다. 켜면 아바타의 대체 표시 장치가 무력화된다.
            //
            // `MemberAvatar`·`AvatarCircle` 은 이니셜/회색 원을 **아래에 깔고 `onSuccess` 에서 가린다.**
            // 그런데 Coil 은 `onState`(= onSuccess) 콜백을 **CrossfadePainter 를 설치한 뒤에** 부르고,
            // placeholder 가 없으니 그 페인터는 알파 0 에서 올라온다. 즉 crossfade 가 켜져 있으면
            // 이니셜이 사라진 **뒤** 100ms 동안 빈 원만 남았다가 사진이 떠오른다 — 서버가 전원에게
            // 기본 이미지 URL 을 주므로 첫 진입 때 21 매칭 대기의 아바타가 한꺼번에 그 깜빡임을 낸다.
            // (메모리 캐시 적중이면 Coil 이 전환을 NONE 으로 바꿔 문제가 없다. 그래서 **첫 표시에만** 보인다.)
            //
            // 36~80dp 아바타에 100ms 페이드가 주는 값이 그 깜빡임보다 작다. 사진을 크게 띄우는 화면이
            // 생기면 그 호출부에서만 `ImageRequest.Builder(...).crossfade(true)` 로 켜는 게 맞다.
            .apply { if (isDebuggableBuild()) logger(DebugLogger()) }
            .build()

    // HTTP 본문 로깅을 디버그 빌드로 제한하는 판단 근거.
    // data 는 라이브러리 모듈이라 자체 BuildConfig.DEBUG 가 app 의 빌드 타입과 일치하지 않고,
    // manifest 의 debuggable 플래그는 빌드 타입과 무관하게 "이 빌드가 디버그인가"를 그대로 알려준다.
    private fun isDebuggableBuild(): Boolean =
        applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    // onNewToken/onRegistered 콜백은 토큰이 신규 발급되거나 회전될 때만 발화한다.
    // 최초 설치 시점을 지난 기존 설치분은 콜백이 오지 않으므로 앱 시작마다 명시 조회한다.
    //
    // getToken() 은 SDK 25.x 에서 deprecated 이나 후속 API 인 register() 는
    // 매니페스트 meta-data(firebase_messaging_installation_id_enabled=true) 로
    // opt-in 하지 않으면 IllegalStateException 을 던진다. 등록 방식 자체가 바뀌는
    // 변경이라 기본 세팅 범위를 넘어서므로 현행 유지한다.
    @Suppress("DEPRECATION")
    private fun fetchFcmToken() {
        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                // 실패한 Task 의 result 는 예외를 던지므로 성공 여부를 먼저 본다
                if (!task.isSuccessful) {
                    Log.w(TAG, "FCM 토큰 조회 실패", task.exception)
                    return@addOnCompleteListener
                }
                val token = task.result
                if (token.isNullOrBlank()) {
                    Log.w(TAG, "FCM 토큰이 비어 있음")
                    return@addOnCompleteListener
                }
                onFcmTokenAvailable(token)
            }
    }

    /**
     * 기기 FCM 토큰이 확인됐을 때의 단일 진입점. 앱 시작 시 명시 조회와
     * [MoyeotaFirebaseMessagingService] 의 회전 콜백이 모두 여기로 들어온다.
     *
     * 서버 등록은 정의상 실패해도 좋은 작업이라 결과를 기다리지 않는다 — 여기서 붙잡으면
     * 앱 시작이나 FCM 콜백이 네트워크를 기다리게 된다. 로그인 상태가 아직 확정되지 않았다면
     * 등록은 [com.moyeota.data.push.FcmTokenRegistrar] 안에서 보류됐다가 로그인 직후 이어진다.
     */
    fun onFcmTokenAvailable(token: String) {
        Log.d(TAG, "FCM 토큰 확인, 서버 등록 시도")
        appScope.launch { appContainer.fcmTokenRegistrar.onTokenAvailable(token) }
    }

    // FCM 알림 표시에 필요한 기본 채널. O 미만은 채널 개념이 없어 생략한다.
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                MoyeotaFirebaseMessagingService.CHANNEL_ID,
                MoyeotaFirebaseMessagingService.CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
        // 채팅 메시지는 별도 채널 — 도착 알림은 두고 채팅만 끄고 싶은 사용자를 위해
        manager.createNotificationChannel(
            NotificationChannel(
                MoyeotaFirebaseMessagingService.CHAT_CHANNEL_ID,
                MoyeotaFirebaseMessagingService.CHAT_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
    }

    private companion object {
        const val TAG = "MoyeotaFcm"
    }
}
