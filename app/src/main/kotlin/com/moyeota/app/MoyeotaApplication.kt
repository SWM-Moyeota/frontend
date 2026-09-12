package com.moyeota.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MoyeotaApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    /**
     * 화면 수명과 무관하게 살아야 하는 앱 차원의 백그라운드 작업(FCM 토큰 서버 등록) 전용 스코프.
     * SupervisorJob: 등록 실패 하나가 이후 등록까지 막지 않게 한다.
     */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(context = this, debugLogging = isDebuggableBuild())
        createNotificationChannel()
        fetchFcmToken()
    }

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
