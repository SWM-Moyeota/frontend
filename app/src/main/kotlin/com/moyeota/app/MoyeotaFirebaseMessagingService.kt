package com.moyeota.app

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * FCM 수신 진입점. 알림 채널은 [MoyeotaApplication.onCreate] 에서 미리 생성한다.
 *
 * 서버가 보내는 건 **데이터 전용 메시지**다(`FcmPassengerNotifier` 는 `putData` 만 쓰고
 * `setNotification` 을 쓰지 않는다). 그래서 시스템이 알림을 대신 그려 주지 않고,
 * 앱이 포그라운드든 백그라운드든 항상 이 [onMessageReceived] 가 불려 직접 표시해야 한다.
 * 뒤집어 말하면 **여기서 그리지 않으면 알림은 어디에도 뜨지 않는다.**
 */
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

    /**
     * 토큰 회전을 앱 차원의 등록 창구로 넘긴다.
     *
     * 서버 등록은 Bearer 를 요구하는데 이 콜백은 로그인 여부와 무관한 시점에 온다
     * — 미로그인이면 [com.moyeota.data.push.FcmTokenRegistrar] 가 보류해 두었다가
     * 다음 로그인 직후 이어서 등록한다. 여기서 로그인 상태를 따질 필요가 없는 이유다.
     */
    private fun handleToken(token: String) {
        val app = applicationContext as? MoyeotaApplication
        if (app == null) {
            // 실무상 일어나지 않는다(프로세스가 뜨면 Application.onCreate 가 먼저 끝난다).
            // 그래도 여기서 죽이면 FCM 이 서비스 크래시로 재시도를 반복하므로 로그만 남긴다.
            Log.w(TAG, "Application 을 찾지 못해 토큰 등록을 건너뛴다")
            return
        }
        app.onFcmTokenAvailable(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        when (val type = message.data[KEY_TYPE]) {
            TYPE_DRIVER_ARRIVED -> showDriverArrived(message.data[KEY_PARTY_ID])
            else -> {
                Log.d(TAG, "처리 대상이 아닌 메시지 type=$type")
                showGeneric(message)
            }
        }
    }

    /**
     * 기사 도착 알림. 서버는 `{type:"DRIVER_ARRIVED", partyId:"N"}` 만 보내고 문구를 싣지 않으므로
     * **표시 문구는 전적으로 앱이 정한다** — 서버가 새 키를 추가하기 전까지 여기가 유일한 출처다.
     */
    private fun showDriverArrived(partyId: String?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            // 지금은 실어만 둔다 — 해당 파티 화면으로 보내는 딥링크는 후속 작업이다.
            // MainActivity 는 이 extra 를 아직 읽지 않으므로 탭하면 앱만 열린다.
            partyId?.let { putExtra(EXTRA_PARTY_ID, it) }
        }

        show(
            // 같은 파티의 도착 알림이 두 번 오면 쌓이지 않고 갱신되도록 파티 단위로 id 를 나눈다.
            // partyId 가 없는 비정상 메시지는 하나로 합쳐도 무방하다.
            notificationId = partyId?.hashCode() ?: DEFAULT_NOTIFICATION_ID,
            title = getString(R.string.notification_driver_arrived_title),
            body = getString(R.string.notification_driver_arrived_body),
            intent = intent,
        )
    }

    /**
     * 앞으로 서버가 다른 type 을 보내거나 notification 페이로드를 실어 보낼 때를 위한 폴백.
     * 표시할 문구를 만들 수 없으면 조용히 버린다 — 빈 알림을 띄우는 것보다 낫다.
     */
    private fun showGeneric(message: RemoteMessage) {
        val title = message.notification?.title
            ?: message.data["title"]
            ?: getString(R.string.app_name)
        val body = message.notification?.body
            ?: message.data["body"]
            ?: return

        show(
            notificationId = System.currentTimeMillis().toInt(),
            title = title,
            body = body,
            intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
        )
    }

    private fun show(notificationId: Int, title: String, body: String, intent: Intent) {
        val pendingIntent = PendingIntent.getActivity(
            this,
            // requestCode 를 알림 id 와 맞춘다. 0 으로 고정하면 FLAG_UPDATE_CURRENT 때문에
            // 나중 알림이 앞선 알림의 extra 까지 덮어써, 딥링크를 붙이는 순간 엉뚱한 파티로 간다.
            notificationId,
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
        runCatching { manager.notify(notificationId, notification) }
            .onFailure { Log.w(TAG, "알림 표시 실패", it) }
    }

    companion object {
        private const val TAG = "MoyeotaFcm"

        private const val KEY_TYPE = "type"
        private const val KEY_PARTY_ID = "partyId"

        /** 서버 `FcmPassengerNotifier.notifyDriverArrived` 가 싣는 값. 문자열이 계약이다. */
        private const val TYPE_DRIVER_ARRIVED = "DRIVER_ARRIVED"

        private const val DEFAULT_NOTIFICATION_ID = 1001

        /** 알림 탭으로 열린 [MainActivity] 가 읽을 파티 식별자. 서버가 문자열로 보내므로 문자열이다. */
        const val EXTRA_PARTY_ID = "com.moyeota.app.extra.PARTY_ID"

        const val CHANNEL_ID = "moyeota_default"
        const val CHANNEL_NAME = "모여타 알림"
    }
}
