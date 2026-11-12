package com.moyeota.presentation.core

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.moyeota.core.designsystem.component.PrimaryCtaButton
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.core.designsystem.theme.MoyeotaSpacing
import com.moyeota.core.designsystem.theme.MoyeotaTheme
import com.moyeota.core.designsystem.theme.MoyeotaType

/**
 * 서버가 「이 버전은 더 못 쓴다」고 답했을 때의 화면([UpdateRequirement.BLOCKED]).
 *
 * ### 왜 오버레이가 아니라 화면 전체를 대신하나
 * 이 화면은 **NavHost 자체를 세우지 않는 자리**에 선다([MainNavHost] 의 이른 return).
 * 오버레이 + `BackHandler` 로 막으면 컴포지션 순서와 핸들러 등록 순서에 기대게 되는데,
 * 그래프가 아예 없으면 **뒤로가기로 갈 수 있는 앱 화면이 존재하지 않는다.** 시스템 뒤로가기는
 * 앱을 종료할 뿐이고, 다시 켜면 설정을 또 받아 같은 판정이 난다.
 *
 * ### 「나중에」가 없는 이유
 * 막는 이유는 서버 계약이 이미 바뀌어 이 버전이 **정상 동작하지 않기** 때문이다. 넘어갈 길을
 * 주면 고장난 화면을 보여 주게 되고, 그건 잠긴 화면보다 나쁘다. 안내만 할 상황이면 서버가
 * `minVersionCode` 가 아니라 `recommendedVersionCode` 를 쓴다.
 */
@Composable
fun UpdateRequiredScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(
        modifier = modifier.fillMaxSize().background(MoyeotaColor.SurfaceCanvas),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = MoyeotaSpacing.ScreenGutter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "업데이트가 필요해요",
                style = MoyeotaType.HeadingXl,
                color = MoyeotaColor.InkPrimary,
            )
            Text(
                text = "지금 버전으로는 합승을 이어갈 수 없어요\n스토어에서 최신 버전을 받아 주세요",
                style = MoyeotaType.BodyMd,
                color = MoyeotaColor.TextBody,
                textAlign = TextAlign.Center,
            )
            PrimaryCtaButton(
                text = "업데이트하러 가기",
                onClick = { context.openPlayStoreListing() },
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun UpdateRequiredScreenPreview() {
    MoyeotaTheme { UpdateRequiredScreen() }
}
