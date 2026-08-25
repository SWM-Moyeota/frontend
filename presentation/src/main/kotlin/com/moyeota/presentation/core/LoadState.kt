package com.moyeota.presentation.core

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeota.core.designsystem.component.MoyeotaBottomBar
import com.moyeota.core.designsystem.component.MoyeotaTab
import com.moyeota.core.designsystem.component.MoyeotaTopBar
import com.moyeota.core.designsystem.component.StatusBarMock
import com.moyeota.core.designsystem.theme.MoyeotaColor

// 하단탭 화면(14 홈 · 17 합승 · 24 채팅 · 35 마이)의 로딩·에러 골격.
//
// LoadingBox/ErrorBox 는 fillMaxSize 라 화면 전체를 덮는다. 탭바를 각 화면이 직접 그리는 구조에서
// 이걸 Route 최상단에 그대로 두면 로딩·에러 동안 탭바가 통째로 사라져 인앱 이동이 끊긴다(QA F-1).
// 그래서 탭바는 항상 남기고 콘텐츠 영역만 상태에 따라 바꾼다.
@Composable
fun TabStateScaffold(
    selectedTab: MoyeotaTab,
    onTabSelect: (MoyeotaTab) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxSize().background(MoyeotaColor.SurfaceSoft)) {
        StatusBarMock()
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            content()
        }
        MoyeotaBottomBar(selected = selectedTab, onSelect = onTabSelect)
    }
}

// 탭바가 없는 화면(20·21·22 등)의 로딩·에러 골격.
// 이쪽의 인앱 이동 수단은 뒤로가기 하나뿐이라, 상태가 무엇이든 뒤로가기는 남겨둔다.
@Composable
fun BackStateScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxSize().background(MoyeotaColor.SurfaceSoft)) {
        Column(modifier = Modifier.fillMaxWidth().background(MoyeotaColor.SurfaceCanvas)) {
            StatusBarMock()
            MoyeotaTopBar(title = title, onBack = onBack)
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            content()
        }
    }
}

// 서버 연동 화면 공용 로딩/에러 상태
@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MoyeotaColor.Primary500)
    }
}

@Composable
fun ErrorBox(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = message,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MoyeotaColor.InkPrimary,
            )
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MoyeotaColor.Primary500)
                    .clickable { onRetry() }
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "다시 시도",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MoyeotaColor.TextOnDark,
                )
            }
        }
    }
}
