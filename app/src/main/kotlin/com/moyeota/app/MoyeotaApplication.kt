package com.moyeota.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging

class MoyeotaApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer()
        createNotificationChannel()
        fetchFcmToken()
    }

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
                val token = task.result
                if (!task.isSuccessful || token == null) {
                    Log.w(TAG, "FCM 토큰 조회 실패", task.exception)
                    return@addOnCompleteListener
                }
                MoyeotaFirebaseMessagingService.handleToken(token)
            }
    }

    // FCM 알림 표시에 필요한 기본 채널. O 미만은 채널 개념이 없어 생략한다.
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            MoyeotaFirebaseMessagingService.CHANNEL_ID,
            MoyeotaFirebaseMessagingService.CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private companion object {
        const val TAG = "MoyeotaFcm"
    }
}
