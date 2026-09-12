package com.moyeota.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.moyeota.core.designsystem.theme.MoyeotaTheme
import com.moyeota.presentation.core.MainNavGraph

class MainActivity : ComponentActivity() {

    // 거부해도 재요청하지 않는다. 알림은 부가 기능이라 진입을 막지 않는다.
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /**
     * 알림 탭으로 열어 달라는 채팅방 id. 액티비티가 새로 뜨면 [onCreate] 의 인텐트에서,
     * 이미 떠 있으면 [onNewIntent] 로 온다(알림 인텐트가 SINGLE_TOP|CLEAR_TOP 이라 둘 다 가능).
     * NavGraph 가 이동을 마치면 null 로 되돌린다 — 안 그러면 회전할 때마다 같은 방으로 또 간다.
     */
    private val pendingChatRoomId = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 최초 생성에서만 요청한다. 화면 회전 등 구성 변경으로 재생성될 때
        // 다시 요청하면 "거부 시 재요청하지 않음" 정책이 깨진다.
        if (savedInstanceState == null) {
            requestNotificationPermissionIfNeeded()
            // 구성 변경 재생성에서는 읽지 않는다 — 이미 처리한 알림 인텐트가 되살아나 또 이동한다
            readPendingChatRoom(intent)
        }
        val container = (application as MoyeotaApplication).appContainer
        setContent {
            MoyeotaTheme {
                MainNavGraph(
                    authRepository = container.authRepository,
                    rideRepository = container.rideRepository,
                    placeRepository = container.placeRepository,
                    chatRepository = container.chatRepository,
                    dispatchRepository = container.dispatchRepository,
                    activePartyRepository = container.activePartyRepository,
                    pendingChatRoomId = pendingChatRoomId.value,
                    onPendingChatRoomHandled = { pendingChatRoomId.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readPendingChatRoom(intent)
    }

    private fun readPendingChatRoom(intent: Intent?) {
        val roomId = intent?.getLongExtra(MoyeotaFirebaseMessagingService.EXTRA_CHAT_ROOM_ID, -1L) ?: -1L
        if (roomId > 0) {
            pendingChatRoomId.value = roomId
            // 같은 인텐트를 두 번 읽지 않게 지운다(setIntent 로 남아 있는 인텐트가 재사용될 수 있다)
            intent?.removeExtra(MoyeotaFirebaseMessagingService.EXTRA_CHAT_ROOM_ID)
        }
    }

    // Android 13(TIRAMISU) 부터 알림 표시에 런타임 권한이 필요하다.
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) return

        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
