package com.moyeota.presentation.feature.mypage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.moyeota.core.designsystem.component.DangerCtaButton
import com.moyeota.core.designsystem.component.MoyeotaTextField
import com.moyeota.core.designsystem.component.MoyeotaTopBar
import com.moyeota.core.designsystem.component.NavigationBarSpacer
import com.moyeota.core.designsystem.component.NoticeBanner
import com.moyeota.core.designsystem.component.NoticeKind
import com.moyeota.core.designsystem.component.StatusBarSpacer
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.core.designsystem.theme.MoyeotaTheme
import com.moyeota.core.designsystem.theme.MoyeotaType
import com.moyeota.presentation.core.SupportLinks
import com.moyeota.presentation.core.openWebPage

/**
 * 37 · 회원 탈퇴 (신규, 2026-10)
 *
 * 진입: 35 마이페이지 「탈퇴하기」 / 뒤로 → 35 / 성공 → 04a 로그인(세션이 사라져 NavGraph 가 보낸다)
 *
 * 구성: 상단바 「회원 탈퇴」 → 「무엇이 지워지는가」 목록 → 비밀번호 확인 입력 → 「탈퇴하기」(확인 다이얼로그)
 *
 * ### 왜 다이얼로그가 아니라 화면인가
 * 서버가 본인 확인 비밀번호를 **본문으로** 요구한다(`DELETE /api/v1/users/me`). 그러면 (a) 입력란과
 * (b) 비밀번호 틀림(400 `USER111`)·진행 중 합승(409 `USER110`) 두 가지 실패 표시가 필요하고,
 * (c) 지워지는 것 네 줄을 사용자가 읽을 자리도 있어야 한다. 다이얼로그에 그걸 다 넣으면
 * 키보드가 올라온 순간 경고 문구가 화면 밖으로 밀려 **읽히지 않는 경고**가 된다.
 *
 * 비밀번호 입력 자체가 이미 "한 번 더 확인"이지만 최종 다이얼로그를 **하나 더** 둔다 —
 * 되돌릴 수 없는 동작이고, 비밀번호는 기억해서 치는 값이라 의도 확인으로는 약하다.
 *
 * ### 경고 문구의 원칙
 * [AccountDeletionWarnings] 의 네 줄은 **서버가 실제로 하는 일만** 적는다. 지난 합승 기록은 서버가
 * 지우지 않으므로 "모든 기록이 사라져요" 같은 말을 쓰지 않는다 — 근거는 그 상수의 KDoc 에 있다.
 * 진행 중인 합승 제약은 여기 적지 않는다: 앱이 미리 알 수 없는 값이라 경고가 아니라 실패 사유다
 * (누르면 [bannerError] 로 온다).
 *
 * ### 「자세히 보기」를 35 가 아니라 여기 둔 이유 (2026-10-14)
 * 계정 삭제 안내 웹페이지([SupportLinks.ACCOUNT_DELETION_URL])는 Play 콘솔 데이터 안전 섹션에
 * 등록하는 주소이자, 네 줄 요약의 **전문**(보관 기간·남는 데이터·처리 절차)이다. 그 링크를
 * 35 마이페이지 「안내」 카드에 행으로 두면 「탈퇴하기」와 뜻이 겹치는 입구가 두 개 생긴다 —
 * 하나는 브라우저로 나가고(↗) 하나는 앱 안에서 계정을 지우는 길인데(›), 둘 다 「계정 삭제」로
 * 읽혀 파괴적인 오탭 면적만 넓어진다. **자세히 알고 싶어지는 자리는 네 줄을 읽은 직후**라,
 * 경고 카드 바로 아래 한 줄로 둔다. 네 줄을 되풀이하지 않고 "무엇이 남는지"까지 있다고만 알린다.
 *
 * 주소가 null 이면 이 줄을 그리지 않는다 — 35 의 메뉴 행과 같은 규칙이다(죽은 링크를 보여 주지 않는다).
 *
 * @param passwordError 비밀번호 입력란 아래 오류. 비밀번호 불일치만 여기 온다.
 * @param bannerError 버튼 위 배너. 진행 중 합승·네트워크·세션 만료처럼 이 화면에서 못 고치는 실패.
 * @param guideUrl 계정 삭제 안내 페이지. null 이면 「자세히 보기」 줄을 그리지 않는다.
 */
@Composable
fun AccountDeleteScreen(
    password: String,
    modifier: Modifier = Modifier,
    submitting: Boolean = false,
    passwordError: String? = null,
    bannerError: String? = null,
    guideUrl: String? = SupportLinks.ACCOUNT_DELETION_URL,
    onPasswordChange: (String) -> Unit = {},
    onDelete: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    var confirming by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    val canSubmit = canSubmitAccountDeletion(password, submitting)
    val context = LocalContext.current

    Column(modifier = modifier.fillMaxSize().imePadding().background(MoyeotaColor.SurfaceSoft)) {
        StatusBarSpacer()
        MoyeotaTopBar(title = "회원 탈퇴", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "탈퇴하면 되돌릴 수 없어요",
                style = MoyeotaType.HeadingLg,
                color = MoyeotaColor.InkPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "아래 내용을 확인한 뒤 비밀번호를 입력해 주세요",
                style = MoyeotaType.BodySm,
                color = MoyeotaColor.TextMute,
            )

            Spacer(Modifier.height(20.dp))
            // 경고를 카드로 묶어 "읽어야 하는 덩어리"로 보이게 한다. 배너(NoticeBanner)로 쓰지 않는 이유는
            // 네 줄이라 한 문단으로 뭉쳐 보이고, 줄마다 다른 사실이라는 게 사라지기 때문이다.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MoyeotaColor.Danger50, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                AccountDeletionWarnings.forEachIndexed { index, warning ->
                    if (index > 0) Spacer(Modifier.height(10.dp))
                    WarningLine(text = warning)
                }
            }

            // 경고 카드 바로 아래 — 네 줄의 전문으로 가는 한 줄. 입력란보다 위에 둬서 키보드가
            // 올라와도 밀려 사라지지 않는다(이 화면을 다이얼로그로 만들지 않은 것과 같은 이유다).
            if (guideUrl != null) {
                Row(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { context.openWebPage(guideUrl) }
                        // 글자만 눌리면 터치 영역이 20dp 밖에 안 된다 — 위아래로 넓혀 둔다
                        .padding(vertical = 10.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "지워지는 것과 남는 것 자세히 보기",
                        style = MoyeotaType.BodySm.copy(fontWeight = FontWeight.Bold),
                        color = MoyeotaColor.Link,
                    )
                    Spacer(Modifier.size(4.dp))
                    // ↗ = 앱 밖으로 나간다. 35 마이페이지와 같은 기호를 그대로 쓴다.
                    ExternalLinkIcon(tint = MoyeotaColor.Link)
                }
            }

            Spacer(Modifier.height(24.dp))
            MoyeotaTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = "비밀번호",
                placeholder = "지금 쓰고 있는 비밀번호",
                errorText = passwordError,
                helperText = if (passwordError == null) "본인 확인을 위해 한 번 더 입력해요" else null,
                enabled = !submitting,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailing = {
                    Text(
                        text = if (passwordVisible) "숨기기" else "보기",
                        style = MoyeotaType.CaptionMd.copy(fontWeight = FontWeight.Bold),
                        color = MoyeotaColor.TextMute,
                        // 04a 로그인과 같은 방식 — 비밀번호를 못 보고 치다 틀리는 쪽이 더 흔한 사고다
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { passwordVisible = !passwordVisible },
                    )
                },
            )

            // 이 화면에서 고칠 수 없는 실패(진행 중 합승·네트워크)는 버튼 위 배너로 둔다 — 입력란을
            // 빨갛게 만들면 사용자가 비밀번호를 계속 다시 친다.
            if (bannerError != null) {
                Spacer(Modifier.height(16.dp))
                NoticeBanner(kind = NoticeKind.ERROR, text = bannerError)
            }
            Spacer(Modifier.height(24.dp))
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            DangerCtaButton(
                text = "탈퇴하기",
                onClick = { confirming = true },
                enabled = canSubmit,
                loading = submitting,
            )
        }
        Spacer(Modifier.height(16.dp))
        NavigationBarSpacer()
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(text = "정말 탈퇴할까요?", style = MoyeotaType.HeadingMd) },
            text = {
                Text(
                    // 다이얼로그는 목록을 되풀이하지 않는다 — 바로 위에서 읽은 것을 다시 읽히면
                    // 사용자는 둘 중 하나만 읽고 넘긴다. 여기서는 "되돌릴 수 없다" 하나만 말한다.
                    text = "탈퇴하면 계정과 저장한 정보가 사라지고 되돌릴 수 없어요",
                    style = MoyeotaType.BodySm,
                    color = MoyeotaColor.TextBody,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    onDelete()
                }) {
                    Text(text = "탈퇴하기", fontWeight = FontWeight.Bold, color = MoyeotaColor.Danger500)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) {
                    Text(text = "취소", color = MoyeotaColor.TextMute)
                }
            },
            containerColor = MoyeotaColor.SurfaceCanvas,
        )
    }
}

/** 경고 한 줄. 불릿은 글꼴에 따라 빠질 수 있는 문자 대신 작은 점으로 그린다(35 의 아이콘 방침과 같다). */
@Composable
private fun WarningLine(text: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Spacer(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(4.dp)
                .background(MoyeotaColor.Danger500, CircleShape),
        )
        Spacer(Modifier.size(10.dp))
        Text(
            text = text,
            style = MoyeotaType.BodySm,
            color = MoyeotaColor.Danger600,
            modifier = Modifier.weight(1f),
        )
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun AccountDeleteScreenPreview() {
    MoyeotaTheme {
        AccountDeleteScreen(password = "")
    }
}

// 안내 페이지 주소를 비웠을 때 — 「자세히 보기」 줄이 사라지고 네 줄만 남는다
@Preview(showBackground = true, widthDp = 393, heightDp = 852, name = "안내 주소 없음")
@Composable
private fun AccountDeleteScreenNoGuidePreview() {
    MoyeotaTheme {
        AccountDeleteScreen(password = "", guideUrl = null)
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852, name = "비밀번호 틀림")
@Composable
private fun AccountDeleteScreenPasswordErrorPreview() {
    MoyeotaTheme {
        AccountDeleteScreen(password = "", passwordError = "비밀번호가 일치하지 않아요")
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852, name = "진행 중 합승")
@Composable
private fun AccountDeleteScreenOngoingPartyPreview() {
    MoyeotaTheme {
        AccountDeleteScreen(
            password = "Passw0rd!",
            bannerError = "진행 중인 합승이 있어요. 합승에서 먼저 나간 뒤에 탈퇴해 주세요",
        )
    }
}
