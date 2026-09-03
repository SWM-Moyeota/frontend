package com.moyeota.presentation.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeota.core.designsystem.component.MoyeotaTextField
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
 * 04a · 아이디로 로그인 [신규]
 *
 * 진입: 04 시작 화면의 「이미 계정이 있어요 · 로그인」 · 세션 만료 · 로그아웃 직후
 *
 * 백엔드에 소셜 로그인이 없어 04 의 카카오 버튼은 「준비 중」 안내만 띄운다.
 * 실제 로그인 자격증명(loginId/password)은 이 화면에서만 받는다.
 *
 * 입력 값은 다른 인증 화면들과 같은 방식으로 화면이 직접 들고 있는다
 * — ViewModel 은 제출 결과(진행 중 · 에러 · 성공)만 관리한다.
 *
 * @param noticeMessage 세션 만료처럼 "왜 이 화면으로 돌아왔는지" 알려야 할 때의 안내 문구
 * @param errorMessage 로그인 실패 사유 (LOGIN_FAILED 등) — 필드 아래 인라인 표시
 */
@Composable
fun LoginFormScreen(
    onBack: () -> Unit = {},
    onSubmit: (loginId: String, password: String) -> Unit = { _, _ -> },
    onSignUp: () -> Unit = {},
    submitting: Boolean = false,
    errorMessage: String? = null,
    noticeMessage: String? = null,
    modifier: Modifier = Modifier,
) {
    var loginId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    // 서버 왕복 전에 거를 수 있는 것만 막는다 — 최종 판정은 서버 몫이라
    // 형식 오류를 필드 에러로 단정하지 않고 CTA 활성 조건으로만 쓴다.
    val canSubmit = loginId.isNotBlank() && password.isNotBlank() && !submitting

    Column(modifier = modifier.fillMaxSize().background(MoyeotaColor.SurfaceSoft)) {
        StatusBarSpacer()
        MoyeotaTopBar(title = "로그인", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "다시 만나 반가워요",
                style = MoyeotaType.DisplayMd,
                color = MoyeotaColor.InkPrimary,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "가입할 때 만든 아이디로 로그인해요",
                style = MoyeotaType.BodySm.copy(fontSize = 14.sp),
                color = MoyeotaColor.TextMute,
            )

            if (noticeMessage != null) {
                Spacer(Modifier.height(20.dp))
                NoticeBanner(kind = NoticeKind.INFO, text = noticeMessage)
            }

            Spacer(Modifier.height(28.dp))
            MoyeotaTextField(
                value = loginId,
                // 아이디는 영소문자·숫자·_ 만 가능하다 (AuthPolicy.LOGIN_ID_PATTERN).
                // 대문자를 눌러도 조용히 소문자로 받아 "규칙에 맞는데 왜 안 되지" 를 없앤다.
                onValueChange = { new -> loginId = new.filter { !it.isWhitespace() }.lowercase() },
                label = "아이디",
                placeholder = "moyeota",
                enabled = !submitting,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Next,
                ),
            )

            Spacer(Modifier.height(20.dp))
            MoyeotaTextField(
                value = password,
                onValueChange = { password = it },
                label = "비밀번호",
                placeholder = "비밀번호를 입력해 주세요",
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
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { passwordVisible = !passwordVisible },
                    )
                },
            )

            // 실패 사유는 화면을 갈아엎지 않고 입력 바로 아래에 남긴다 — 고칠 곳이 위에 있기 때문
            if (errorMessage != null) {
                Spacer(Modifier.height(16.dp))
                NoticeBanner(kind = NoticeKind.ERROR, text = errorMessage)
            }

            Spacer(Modifier.height(28.dp))
            PrimaryCtaButton(
                text = "로그인",
                onClick = { onSubmit(loginId.trim(), password) },
                enabled = canSubmit,
                loading = submitting,
            )

            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "아직 계정이 없어요",
                    style = MoyeotaType.BodySm,
                    color = MoyeotaColor.TextMute,
                )
                Spacer(Modifier.size(12.dp))
                Text(
                    text = "가입하기",
                    style = MoyeotaType.BodySm.copy(fontWeight = FontWeight.Bold),
                    color = MoyeotaColor.Primary500,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onSignUp,
                    ),
                )
            }
            Spacer(Modifier.height(28.dp))
        }
        NavigationBarSpacer()
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun LoginFormScreenPreview() {
    MoyeotaTheme { LoginFormScreen() }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun LoginFormScreenErrorPreview() {
    MoyeotaTheme {
        LoginFormScreen(
            errorMessage = "아이디 또는 비밀번호가 올바르지 않아요",
            noticeMessage = "세션이 만료됐어요. 다시 로그인해 주세요",
        )
    }
}
