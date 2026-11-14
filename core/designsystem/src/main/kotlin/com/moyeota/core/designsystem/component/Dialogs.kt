package com.moyeota.core.designsystem.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.core.designsystem.theme.MoyeotaShape
import com.moyeota.core.designsystem.theme.MoyeotaType

/**
 * 앱의 **단 하나뿐인 확인 다이얼로그** (디자인 QA G11, 2026-11).
 *
 * 예전엔 제목이 17sp Bold·16sp SemiBold 로 갈리고, 채팅방 「나가기」는 손으로 그린 다이얼로그에 **파란** 확인
 * 버튼을 달았다 — 되돌릴 수 없는 이탈에 주 행동 색을 쓴 것이다(디자인 문서: 파괴적 UI 에 primary 금지).
 *
 * - 제목 [MoyeotaType.HeadingMd], 본문 [MoyeotaType.BodySm] [MoyeotaColor.TextBody], 흰 바탕, 모서리 20dp
 * - 확인 버튼은 [destructive] 면 빨강, 아니면 진한 파랑. 취소는 [MoyeotaColor.TextMute]
 * - 바깥을 누르거나 뒤로 가기를 누르면 [onDismiss] — 취소와 같다
 *
 * [onConfirm] 을 부르기 **전에** 다이얼로그를 닫는 것은 호출부의 몫이다(상태를 호출부가 갖는다).
 *
 * @param body 없으면 제목만 묻는다
 * @param dismissText 없으면 취소 버튼을 그리지 않는다(바깥 누름으로만 닫힌다)
 */
@Composable
fun MoyeotaConfirmDialog(
    title: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    body: String? = null,
    dismissText: String? = "취소",
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(text = title, style = MoyeotaType.HeadingMd, color = MoyeotaColor.InkPrimary) },
        text = body?.let { { Text(text = it, style = MoyeotaType.BodySm, color = MoyeotaColor.TextBody) } },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmText,
                    style = MoyeotaType.ButtonMd,
                    color = if (destructive) MoyeotaColor.Danger500 else MoyeotaColor.Primary600,
                )
            }
        },
        dismissButton = dismissText?.let {
            {
                TextButton(onClick = onDismiss) {
                    Text(text = it, style = MoyeotaType.ButtonMd, color = MoyeotaColor.TextMute)
                }
            }
        },
        shape = MoyeotaShape.Dialog,
        containerColor = MoyeotaColor.SurfaceCanvas,
    )
}
