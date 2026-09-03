package com.moyeota.presentation.core

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.core.designsystem.theme.MoyeotaTheme

/**
 * 세션 복원 중(=[com.moyeota.domain.model.AuthState.Unknown]) 화면.
 *
 * 앱 시작 직후에는 DataStore 에서 토큰을 아직 읽지 못했다. 이때 미로그인으로 단정하면
 * **이미 로그인한 사용자가 매번 온보딩·로그인 화면을 스쳐 본다** — 그래서 확정될 때까지
 * 여기서 기다린다. 복원은 디스크 한 번 읽기라 보통 한 프레임 남짓이다.
 */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().background(MoyeotaColor.SurfaceSoft),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = "모여타",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MoyeotaColor.InkPrimary,
                letterSpacing = (-0.48).sp,
            )
            CircularProgressIndicator(
                color = MoyeotaColor.Primary500,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun SplashScreenPreview() {
    MoyeotaTheme { SplashScreen() }
}
