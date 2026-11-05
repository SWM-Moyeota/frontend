package com.moyeota.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.moyeota.core.designsystem.theme.MoyeotaTheme
import com.moyeota.presentation.core.MainNavGraph

/**
 * API 24·25 의 네비게이션 바에 깔 **어두운 스크림**. 그 버전에는 네비바 아이콘을 어둡게 만드는
 * 수단이 없어(라이트 네비바는 API 26+) 투명하게 두면 흰 배경에 흰 아이콘이 된다.
 *
 * 값은 androidx `enableEdgeToEdge` 의 기본값과 같다(`argb(0x80, 0x1b, 0x1b, 0x1b)`) — 그쪽 상수는
 * `@VisibleForTesting` 이라 직접 쓰지 않고 같은 값을 적어 둔다.
 */
private val NavigationBarDarkScrim = Color.argb(0x80, 0x1B, 0x1B, 0x1B)

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
        // 시스템 바 아이콘을 **라이트 고정**으로 둔다. 인자 없는 enableEdgeToEdge() 는
        // SystemBarStyle.auto 라 `detectDarkMode` 가 **기기의** night mode 를 보는데, 이 앱은
        // MoyeotaTheme 이 LightColorScheme 고정(「와이어프레임은 라이트 단일 테마」)이라 기기 설정과
        // 무관하게 **항상 흰 배경**을 그린다. 그래서 시스템 다크 모드 기기에서는 흰 배경 + 흰 시스템
        // 아이콘이 되어 시계·배터리·네비게이션 버튼이 보이지 않았다(모든 화면에 걸친 문제).
        // 테마가 다크 모드를 지원하게 되는 날 auto 로 되돌리면 된다.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            // 네비게이션 바도 같은 이유로 고정한다. 둘째 인자는 **어두운 아이콘을 못 쓰는 기기**에서만
            // 쓰이는 스크림이라(위 [NavigationBarDarkScrim]) API 26+ 에서는 투명 그대로다.
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, NavigationBarDarkScrim),
        )
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
                    appConfigRepository = container.appConfigRepository,
                    rideRepository = container.rideRepository,
                    placeRepository = container.placeRepository,
                    chatRepository = container.chatRepository,
                    activePartyRepository = container.activePartyRepository,
                    rideRecordRepository = container.rideRecordRepository,
                    noShowRepository = container.noShowRepository,
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
