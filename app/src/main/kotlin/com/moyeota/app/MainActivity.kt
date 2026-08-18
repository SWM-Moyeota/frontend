package com.moyeota.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.moyeota.core.designsystem.theme.MoyeotaTheme
import com.moyeota.presentation.core.MainNavGraph

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as MoyeotaApplication).appContainer
        setContent {
            MoyeotaTheme {
                MainNavGraph(rideRepository = container.rideRepository)
            }
        }
    }
}
