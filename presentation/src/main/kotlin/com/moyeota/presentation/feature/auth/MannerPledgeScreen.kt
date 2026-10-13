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
 * 진입: 10 프로필 만들기 / 뒤로 → 10 / 「동의하고 가입 완료」 → 13 가입 완료
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
    /**
     * 가입이 `PHONE_NOT_VERIFIED`(400 `USER207`) 로 거절됐을 때만 채워진다 — 문자 인증 토큰이
     * 만료·소진됐거나 번호가 달라졌다는 뜻이고, 여기서 다시 눌러도 같은 400 이다.
     * 누르면 10 으로 돌아가 인증 블록이 초기화된다.
     *
     * [onEditNickname] 과 동시에 채워지는 일은 없다 — 서버가 토큰을 중복 검사보다 먼저 보므로
     * 두 실패는 같은 요청에서 함께 오지 않는다.
     */
    onReverifyPhone: (() -> Unit)? = null,
) {
    // 전체 항목 동의 필수 → 하나라도 미체크면 CTA 비활성.
    // 개수를 [mannerPledgeItems] 에서 끌어온다 — 항목을 늘렸을 때 체크 배열이 짧아 터지는 일이 없도록.
    val checks = remember { mutableStateListOf(*Array(mannerPledgeItems.size) { false }) }
    val allChecked = checks.all { it }

    Column(modifier = modifier.fillMaxSize().background(MoyeotaColor.SurfaceSoft)) {
        StatusBarSpacer()
        MoyeotaTopBar(
            title = "",
            onBack = onBack,
            actions = {
                Text(
                    text = "2 / 2",
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
                text = "동의 내용은 가입 시점 기준으로 적용돼요",
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
                mannerPledgeItems.forEachIndexed { index, item ->
                    PledgeItemRow(
                        item = item,
                        checked = checks[index],
                        onToggle = { checks[index] = !checks[index] },
                    )
                    if (index != mannerPledgeItems.lastIndex) {
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
                // 10 으로 되돌려야 고칠 수 있는 실패에만 링크가 붙는다. 둘은 동시에 오지 않는다.
                val recovery = when {
                    onEditNickname != null -> "닉네임 바꾸기" to onEditNickname
                    onReverifyPhone != null -> "휴대폰 인증 다시 하기" to onReverifyPhone
                    else -> null
                }
                if (recovery != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = recovery.first,
                        style = MoyeotaType.BodySm,
                        fontWeight = FontWeight.Bold,
                        color = MoyeotaColor.Primary500,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = recovery.second,
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

/**
 * 12 매너 서약의 동의 항목 하나.
 *
 * [description] 이 nullable 인 이유: **보조 설명이 약속이 되는 자리**라서다. 운영이 지킬 수단이
 * 없는 말을 적을 바에는 줄을 비우는 쪽이 맞다 — 아래 「불쾌한 말과 행동」 항목이 그 경우다.
 */
internal data class PledgeItem(val title: String, val description: String? = null)

/**
 * 동의 항목 — **전부 체크해야 가입 버튼이 열린다**(체크 배열 길이를 이 목록에서 끌어온다).
 * 즉 여기 한 줄을 넣는 것은 "이걸 동의하지 않으면 가입시키지 않겠다"는 뜻이다.
 *
 * 2026-10-13 에 지운 것:
 * - 「불쾌한 말과 행동을 하지 않을게요」의 설명 **「신고가 들어오면 24시간 안에 확인해요」** —
 *   앱에 신고 UI 가 없다. 사용자 신고·메시지 신고 API 를 앱이 부르지 않고(27 긴급 신고만
 *   `POST /reports` 를 쓰는데 그것도 운행 중인 방의 승객만이고 동승 모드는 26 에 닿지 않는다),
 *   받을 창구는 문의 메일뿐인데 `SupportLinks.CONTACT_EMAIL` 이 null 이라 그 행조차 안 보인다.
 *   **24시간을 지킬 수단이 없다.** 대체 문구를 새로 만들지 않고 줄을 비웠다 — 창구가 없는 상태에서
 *   「확인해요」라고만 줄여도 없는 창구를 있다고 말하는 셈이다.
 * - 항목 **「운행 중 내 위치를 동승자와 공유하는 데 동의해요」** — 위치 공유 기능이 아예 없고
 *   (`docs/BACKEND-GAP-CLEANUP.md` §1 이 `reportMyLocation`·`getMemberLocations` 를 지웠다;
 *   남은 위치 API 는 기사 위치 조회뿐이다) **그 동의도 서버에 저장되지 않았다**(저장 필드가 없어
 *   전송하지 않았다). 없는 기능에 동의를 받아 버리고 있었다. 개인정보 처리방침의 「실시간 위치를
 *   동승자에게 공유하는 기능이 현재 없습니다」와도 정면으로 어긋났다.
 *
 * 남긴 「이용이 제한돼요」류는 운영이 약관으로 유보하는 조치라 기능 주장이 아니다 — 다만 백엔드에
 * 제재 수단이 아직 없어 운영자 판단 대상이다(산출물 보고서 참고).
 */
internal val mannerPledgeItems = listOf(
    // 09 본인 인증에 있던 「이용약관 · 개인정보 수집에 동의해요」 체크를 여기로 합쳤다.
    // 09 가 가입 경로에서 빠지기도 했지만, 그 전에도 동의를 두 화면이 나눠 받고 있었다
    // — 무엇에 동의했는지는 제출 버튼이 있는 이 화면에 모여 있어야 한다.
    PledgeItem("이용약관 · 개인정보 수집에 동의해요", "실명·생년월일·연락처는 본인 확인에만 써요"),
    PledgeItem("약속한 시간과 장소를 지킬게요", "무단 취소가 반복되면 이용이 제한돼요"),
    PledgeItem("불쾌한 말과 행동을 하지 않을게요"),
    PledgeItem("매칭이 확정되면 꼭 나갈게요", "매칭 확정 후 오지 않으면 이용이 제한될 수 있어요"),
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
            // 설명이 없는 항목은 제목만 — 빈 줄을 띄우면 문구가 지워진 자리가 드러난다
            if (item.description != null) {
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
