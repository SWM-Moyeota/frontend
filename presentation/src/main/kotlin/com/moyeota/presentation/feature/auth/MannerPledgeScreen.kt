package com.moyeota.presentation.feature.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeota.core.designsystem.component.MoyeotaTopBar
import com.moyeota.core.designsystem.component.NavigationBarSpacer
import com.moyeota.core.designsystem.component.NoticeBanner
import com.moyeota.core.designsystem.component.NoticeKind
import com.moyeota.core.designsystem.component.PrimaryCtaButton
import com.moyeota.core.designsystem.component.StatusBarSpacer
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.core.designsystem.theme.MoyeotaTheme
import com.moyeota.core.designsystem.theme.MoyeotaType

/**
 * 12 · 매너 서약 [S07]
 *
 * 진입: 11 안심 설정 / 뒤로 → 11 / 「동의하고 가입 완료」 → 13 가입 완료
 * 개별 항목 탭 → 해당 정책 상세 (미연결)
 *
 * **가입 플로우의 유일한 서버 제출 지점**이다 — [onComplete] 가 회원가입 + 자동 로그인을
 * 실행하고, 성공했을 때만 호출부가 13 으로 넘긴다. 그래서 진행 상태와 실패 사유를
 * 화면이 직접 만들지 않고 [submitting] · [errorMessage] 로 받는다
 * (예전엔 500ms 딜레이 후 무조건 다음 화면으로 넘어갔다).
 */
@Composable
fun MannerPledgeScreen(
    onBack: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    submitting: Boolean = false,
    errorMessage: String? = null,
    /**
     * 닉네임 중복(409)처럼 **이 화면에서 고칠 수 없는** 실패일 때만 채워진다 —
     * 배너 아래 「닉네임 바꾸기」 가 붙고, 누르면 10 프로필 만들기로 돌아간다.
     * null 이면 배너만 뜬다(여기서 재시도하면 되는 실패).
     */
    onEditNickname: (() -> Unit)? = null,
) {
    // 전체 항목 동의 필수 → 하나라도 미체크면 CTA 비활성.
    // 개수를 [pledgeItems] 에서 끌어온다 — 항목을 늘렸을 때 체크 배열이 짧아 터지는 일이 없도록.
    val checks = remember { mutableStateListOf(*Array(pledgeItems.size) { false }) }
    val allChecked = checks.all { it }

    Column(modifier = modifier.fillMaxSize().background(MoyeotaColor.SurfaceSoft)) {
        StatusBarSpacer()
        MoyeotaTopBar(
            title = "",
            onBack = onBack,
            actions = {
                Text(
                    text = "3 / 3",
                    style = MoyeotaType.BodySm,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF8A93A0),
                )
            },
        )
        PledgeStepProgressBar(progress = 1f)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "마지막으로 약속 하나만",
                style = MoyeotaType.DisplayMd,
                color = MoyeotaColor.InkPrimary,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "모여타는 서로를 믿고 타는 서비스예요",
                style = MoyeotaType.BodySm.copy(fontSize = 14.sp),
                color = MoyeotaColor.TextMute,
            )

            Spacer(Modifier.height(24.dp))
            // 「아래 내용에 모두 동의해요」 체크 시 하위 4개 일괄 on/off
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MoyeotaColor.Primary50, RoundedCornerShape(16.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        val target = !allChecked
                        for (i in checks.indices) checks[i] = target
                    }
                    .padding(horizontal = 20.dp, vertical = 17.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PledgeCheckCircle(checked = allChecked, size = 26.dp)
                Spacer(Modifier.width(13.dp))
                Text(
                    text = "아래 내용에 모두 동의해요",
                    style = MoyeotaType.BodyMd,
                    fontWeight = FontWeight.Bold,
                    color = MoyeotaColor.Primary600,
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = "동의한 내용과 노쇼 차감 고지는 계정에 기록돼요",
                style = MoyeotaType.CaptionMd,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF9AA1AC),
                modifier = Modifier.align(Alignment.End),
            )
            Spacer(Modifier.height(6.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(4.dp, RoundedCornerShape(18.dp), spotColor = Color(0x0F1B2A4A))
                    .background(MoyeotaColor.SurfaceCanvas, RoundedCornerShape(18.dp))
                    .padding(vertical = 8.dp),
            ) {
                pledgeItems.forEachIndexed { index, item ->
                    PledgeItemRow(
                        item = item,
                        checked = checks[index],
                        onToggle = { checks[index] = !checks[index] },
                    )
                    if (index != pledgeItems.lastIndex) {
                        HorizontalDivider(
                            color = MoyeotaColor.Hairline,
                            modifier = Modifier.padding(start = 56.dp, end = 20.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            // 실패는 화면을 갈아엎지 않고 CTA 바로 위에 남긴다 — 다시 누를 곳이 아래 있기 때문
            if (errorMessage != null) {
                NoticeBanner(kind = NoticeKind.ERROR, text = errorMessage)
                if (onEditNickname != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "닉네임 바꾸기",
                        style = MoyeotaType.BodySm,
                        fontWeight = FontWeight.Bold,
                        color = MoyeotaColor.Primary500,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onEditNickname,
                            ),
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            PrimaryCtaButton(
                text = "동의하고 가입 완료",
                onClick = onComplete,
                enabled = allChecked && !submitting,
                loading = submitting,
            )
        }
        NavigationBarSpacer()
    }
}

private data class PledgeItem(val title: String, val description: String)

private val pledgeItems = listOf(
    // 09 본인 인증에 있던 「이용약관 · 개인정보 수집에 동의해요」 체크를 여기로 합쳤다.
    // 09 가 가입 경로에서 빠지기도 했지만, 그 전에도 동의를 두 화면이 나눠 받고 있었다
    // — 무엇에 동의했는지는 제출 버튼이 있는 이 화면에 모여 있어야 한다.
    PledgeItem("이용약관 · 개인정보 수집에 동의해요", "실명·생년월일·연락처는 본인 확인과 매칭 안전에만 써요"),
    PledgeItem("약속한 시간과 장소를 지킬게요", "무단 취소가 반복되면 이용이 제한돼요"),
    PledgeItem("요금은 내릴 때 바로 정산할게요", "미정산이 쌓이면 매칭이 막혀요"),
    PledgeItem("불쾌한 말과 행동을 하지 않을게요", "신고가 들어오면 24시간 안에 확인해요"),
    PledgeItem("무단 노쇼는 요금이 차감돼요", "매칭 확정 후 오지 않으면 1인 부담금이 등록한 결제 수단에서 청구돼요"),
)

@Composable
private fun PledgeItemRow(
    item: PledgeItem,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 항목 텍스트 탭 → 해당 정책 상세 (미연결, 동작 없음)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { /* 미연결 */ }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        PledgeCheckCircle(
            checked = checked,
            size = 24.dp,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle,
            ),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MoyeotaType.BodyMd,
                fontWeight = FontWeight.Bold,
                color = MoyeotaColor.InkPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = item.description,
                style = MoyeotaType.CaptionMd,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF8A93A0),
            )
        }
    }
}

// 와이어프레임 모양 그대로의 원형 체크 (체크 시 Primary 채움 + 흰 체크표시)
@Composable
private fun PledgeCheckCircle(
    checked: Boolean,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .then(
                if (checked) {
                    Modifier.background(MoyeotaColor.Primary500, CircleShape)
                } else {
                    Modifier
                        .background(MoyeotaColor.SurfaceCanvas, CircleShape)
                        .border(1.5.dp, Color(0xFFD1D5DB), CircleShape)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Canvas(modifier = Modifier.size(size / 2)) {
                val stroke = 2.dp.toPx()
                val w = this.size.width
                val h = this.size.height
                drawLine(
                    color = Color.White,
                    start = Offset(w * 0.08f, h * 0.55f),
                    end = Offset(w * 0.38f, h * 0.85f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = Color.White,
                    start = Offset(w * 0.38f, h * 0.85f),
                    end = Offset(w * 0.92f, h * 0.18f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun PledgeStepProgressBar(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(4.dp)
            .background(Color(0xFFE6EAF0), RoundedCornerShape(2.dp)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress)
                .height(4.dp)
                .background(MoyeotaColor.Primary500, RoundedCornerShape(2.dp)),
        )
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun MannerPledgeScreenPreview() {
    MoyeotaTheme {
        MannerPledgeScreen(onBack = {}, onComplete = {})
    }
}
