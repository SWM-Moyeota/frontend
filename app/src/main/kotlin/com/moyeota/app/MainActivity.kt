package com.moyeota.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.moyeota.core.designsystem.theme.MoyeotaTheme
import com.moyeota.presentation.core.MainNavGraph

class MainActivity : ComponentActivity() {

    // 거부해도 재요청하지 않는다. 알림은 부가 기능이라 진입을 막지 않는다.
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 최초 생성에서만 요청한다. 화면 회전 등 구성 변경으로 재생성될 때
        // 다시 요청하면 "거부 시 재요청하지 않음" 정책이 깨진다.
        if (savedInstanceState == null) {
            requestNotificationPermissionIfNeeded()
        }
        val container = (application as MoyeotaApplication).appContainer
        setContent {
            MoyeotaTheme {
                MainNavGraph(rideRepository = container.rideRepository)
            }
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
