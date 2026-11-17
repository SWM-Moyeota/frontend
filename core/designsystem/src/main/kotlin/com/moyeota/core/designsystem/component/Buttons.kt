package com.moyeota.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import com.moyeota.core.designsystem.theme.MoyeotaShape
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

/**
 * 주 CTA — **한 화면에 하나**(디자인 문서: 파랑은 화면마다 아껴 쓴다). 필수 조건 미충족 시 enabled=false,
 * 제출 중이면 loading=true (공통 규칙: 중복 제출 차단).
 *
 * 비활성 색은 [MoyeotaColor.SurfaceDisabled]/[MoyeotaColor.TextPlaceholder] 다. 예전 값(SurfaceSoft/TextAsh)은
 * 회색 화면 위에서 대비가 1.01:1 이라 버튼 모양이 사라지고 흐린 글자만 떠 있었다(디자인 QA G5).
 *
 * @param leadingIcon 글자 앞에 붙는 18dp 아이콘(23 「채팅으로 물어보기」의 말풍선). 장식이라 스크린리더는 글자만 읽는다
 */
@Composable
fun PrimaryCtaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(52.dp),
        enabled = enabled && !loading,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MoyeotaColor.Primary500,
            contentColor = MoyeotaColor.TextOnDark,
            disabledContainerColor = MoyeotaColor.SurfaceDisabled,
            disabledContentColor = MoyeotaColor.TextPlaceholder,
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            if (leadingIcon != null) {
                Box(modifier = Modifier.size(18.dp), contentAlignment = Alignment.Center) { leadingIcon() }
                Spacer(Modifier.width(8.dp))
            }
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
            disabledContainerColor = MoyeotaColor.SurfaceDisabled,
            disabledContentColor = MoyeotaColor.TextPlaceholder,
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
        // Hairline(1.15:1)은 흰 바탕에서 테두리가 안 보여 글자만 떠 있었다 — 디자인 문서 button-secondary-light 의 ash-light
        border = BorderStroke(1.dp, if (enabled) MoyeotaColor.BorderStrong else MoyeotaColor.Border),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MoyeotaColor.InkPrimary),
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) {
        Text(text = text, style = MoyeotaType.ButtonLg)
    }
}

/**
 * 옅은 파랑 알약 — **주 CTA 옆에 서는 두 번째 행동**(21 「채팅 열기」 등). 디자인 문서의 button-primary-soft.
 *
 * 왜 두 번째 파란 솔리드가 아닌가: 한 화면에 솔리드 파랑이 둘이면 무엇이 주 행동인지 눈이 고르지 못한다.
 * 모양·높이는 [PrimaryCtaButton] 과 같게 둬서 나란히 놓였을 때 "같은 가족"으로 읽히게 한다.
 */
@Composable
fun SoftButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 52.dp,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(height),
        enabled = enabled,
        shape = MoyeotaShape.Pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = MoyeotaColor.Primary50,
            contentColor = MoyeotaColor.Primary600,
            disabledContainerColor = MoyeotaColor.SurfaceDisabled,
            disabledContentColor = MoyeotaColor.TextPlaceholder,
        ),
        elevation = null,
    ) {
        Text(text = text, style = MoyeotaType.ButtonLg)
    }
}

/**
 * 바탕 없는 빨간 글자 알약 — 「그만 찾기」·「방 나가기」처럼 **되돌리기 어려운 이탈**(디자인 문서 button-danger,
 * 「요청 취소」). 누르면 늘 확인 다이얼로그가 먼저 뜨는 자리에 쓴다.
 *
 * 왜 회색 상자가 아닌가: 예전 회색 r16 상자는 주 CTA(파란 알약)와 모양이 달라 "다른 앱의 버튼"처럼 보였고,
 * 회색은 위험을 말하지 못한다. 바탕을 비워 주 CTA 보다 한 단계 아래로 내리고, 색만 경고로 둔다.
 * 비활성은 [MoyeotaColor.TextPlaceholder] 글자 — 못 누르는 버튼은 위험하지 않다([DangerCtaButton] 과 같은 방침).
 */
@Composable
fun DangerTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(48.dp),
        enabled = enabled,
        shape = MoyeotaShape.Pill,
        colors = ButtonDefaults.textButtonColors(
            contentColor = MoyeotaColor.Danger500,
            disabledContentColor = MoyeotaColor.TextPlaceholder,
        ),
    ) {
        Text(text = text, style = MoyeotaType.ButtonMd)
    }
}

/**
 * 목록 줄 안의 작은 행동(17 탐색 카드의 「합류」). 보이는 높이는 32dp 지만 **터치 영역은 48dp** 다
 * ([minimumInteractiveComponentSize]) — 예전 34dp 솔리드 파랑 버튼은 손가락에 작고, 줄마다 하나씩 있어
 * 화면에 파란 덩어리가 줄 수만큼 생겼다. 옅은 파랑이라 시트의 단 하나뿐인 솔리드 CTA 를 가리지 않는다.
 *
 * 비활성(「마감」)은 바탕 없이 [MoyeotaColor.TextPlaceholder] 글자만 남긴다.
 */
@Composable
fun CompactPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(MoyeotaShape.Pill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .height(32.dp)
                .background(if (enabled) MoyeotaColor.Primary50 else Color.Transparent, MoyeotaShape.Pill)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MoyeotaType.ButtonMd,
                color = if (enabled) MoyeotaColor.Primary600 else MoyeotaColor.TextPlaceholder,
                maxLines = 1,
            )
        }
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
