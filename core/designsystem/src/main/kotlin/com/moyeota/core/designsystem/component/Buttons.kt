package com.moyeota.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.core.designsystem.theme.MoyeotaType

// 주 CTA. 필수 조건 미충족 시 enabled=false, 제출 중이면 loading=true (공통 규칙: 중복 제출 차단)
@Composable
fun PrimaryCtaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(52.dp),
        enabled = enabled && !loading,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MoyeotaColor.Primary500,
            contentColor = MoyeotaColor.TextOnDark,
            disabledContainerColor = MoyeotaColor.SurfaceSoft,
            disabledContentColor = MoyeotaColor.TextAsh,
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text(text = text, style = MoyeotaType.ButtonLg)
        }
    }
}

/**
 * 되돌릴 수 없는 파괴적 동작의 주 CTA — 지금 쓰는 곳은 37 회원 탈퇴 하나다.
 *
 * [PrimaryCtaButton] 과 모양·크기·중복 제출 차단 규칙이 같고 **색만 Danger** 다. 색을 바꾸는 이유는
 * 장식이 아니라 경고다: 같은 자리의 같은 파란 버튼이면 「저장」·「다음」과 구분되지 않는다.
 *
 * 비활성 색은 [PrimaryCtaButton] 과 똑같이 둔다 — 못 누르는 버튼은 위험하지 않으므로
 * 빨간색을 유지하면 "눌리지 않는데 위험해 보이는" 상태가 된다.
 *
 * [SafetyButton] 과 혼동하지 말 것. 그쪽은 긴급 신고(도움을 부르는 행동) 전용이고,
 * 이쪽은 사용자가 자기 것을 지우는 행동이다.
 */
@Composable
fun DangerCtaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(52.dp),
        enabled = enabled && !loading,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MoyeotaColor.Danger500,
            contentColor = MoyeotaColor.TextOnDark,
            disabledContainerColor = MoyeotaColor.SurfaceSoft,
            disabledContentColor = MoyeotaColor.TextAsh,
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text(text = text, style = MoyeotaType.ButtonLg)
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(52.dp),
        enabled = enabled,
        shape = CircleShape,
        border = BorderStroke(1.dp, MoyeotaColor.Hairline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MoyeotaColor.InkPrimary),
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) {
        Text(text = text, style = MoyeotaType.ButtonLg)
    }
}

// 비상 신고 전용 버튼 — Safety 색은 여기 외 사용 금지
@Composable
fun SafetyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MoyeotaColor.Safety500,
            contentColor = MoyeotaColor.TextOnDark,
        ),
    ) {
        Text(text = text, style = MoyeotaType.ButtonLg)
    }
}
