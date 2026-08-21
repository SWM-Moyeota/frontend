package com.moyeota.app

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

// FCM 수신 진입점. 알림 채널은 MoyeotaApplication.onCreate 에서 미리 생성한다.
class MoyeotaFirebaseMessagingService : FirebaseMessagingService() {

    // SDK 25.x 에서 deprecated 되었으나 NEW_TOKEN 액션은 여전히 이 콜백으로만 전달된다.
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        handleToken(token)
    }

    // onNewToken 의 후속 콜백. FCM_REGISTERED 액션은 이쪽으로만 전달되어 둘 다 필요하다.
    override fun onRegistered(token: String) {
        super.onRegistered(token)
        handleToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        // notification 페이로드가 없으면 data 페이로드로 대체한다.
        val title = message.notification?.title
            ?: message.data["title"]
            ?: getString(R.string.app_name)
        val body = message.notification?.body
            ?: message.data["body"]
            ?: return

        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = NotificationManagerCompat.from(this)
        // Android 13+ 에서 POST_NOTIFICATIONS 미허용이면 notify 가 무시되므로 미리 걸러낸다.
        if (!manager.areNotificationsEnabled()) {
            Log.d(TAG, "알림 권한 미허용 상태로 표시 생략")
            return
        }
        runCatching { manager.notify(System.currentTimeMillis().toInt(), notification) }
            .onFailure { Log.w(TAG, "알림 표시 실패", it) }
    }

    companion object {
        private const val TAG = "MoyeotaFcm"

        const val CHANNEL_ID = "moyeota_default"
        const val CHANNEL_NAME = "모여타 알림"

        // 토큰 처리 단일 경로. 콜백(onNewToken/onRegistered)과
        // 앱 시작 시 명시 조회(MoyeotaApplication)가 모두 이곳으로 들어온다.
        fun handleToken(token: String) {
            Log.d(TAG, "FCM 토큰: $token")
            // TODO: 백엔드 토큰 등록 엔드포인트 확정 시 서버 전송 추가
        }
    }
}
