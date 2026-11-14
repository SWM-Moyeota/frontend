package com.moyeota.presentation.feature.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.core.designsystem.theme.MoyeotaType

/**
 * 글자만 있는 작은 행동(「아이디 찾기」·「가입하기」·「보기」) — 보이는 건 글자지만 터치 영역은 48dp 다.
 *
 * develop 에는 같은 이름의 공용 컴포넌트가 디자인 시스템에 있다(frontend #97). 이 릴리스 브랜치는 #97 을
 * 싣지 않으므로, 계정 찾기·로그인 화면이 쓰는 만큼만 이 패키지 안에 둔다. develop 과 합칠 때는
 * 디자인 시스템 쪽을 쓰고 이 파일은 지운다.
 */
@Composable
internal fun TextLinkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MoyeotaColor.Primary500,
    style: TextStyle = MoyeotaType.ButtonMd,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text, style = style, color = if (enabled) color else MoyeotaColor.TextMute, maxLines = 1)
    }
}
