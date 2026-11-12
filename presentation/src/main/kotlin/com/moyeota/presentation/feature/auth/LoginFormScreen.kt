package com.moyeota.presentation.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.moyeota.core.designsystem.component.MoyeotaTextField
import com.moyeota.core.designsystem.component.MoyeotaTopBar
import com.moyeota.core.designsystem.component.NavigationBarSpacer
import com.moyeota.core.designsystem.component.NoticeBanner
import com.moyeota.core.designsystem.component.NoticeKind
import com.moyeota.core.designsystem.component.PrimaryCtaButton
import com.moyeota.core.designsystem.component.StatusBarSpacer
import com.moyeota.core.designsystem.component.TextLinkButton
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.core.designsystem.theme.MoyeotaTheme
import com.moyeota.core.designsystem.theme.MoyeotaType

/**
 * 04a · 아이디로 로그인 [신규]
 *
 * 진입: 04 시작 화면의 「이미 계정이 있어요 · 로그인」 · 세션 만료 · 로그아웃 직후
 *
 * 백엔드에 소셜 로그인이 없어 04 에도 카카오 버튼은 없다.
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

    Column(modifier = modifier.fillMaxSize().imePadding().background(MoyeotaColor.SurfaceCanvas)) {
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
            Spacer(Modifier.height(12.dp))
            Text(
                text = "가입할 때 만든 아이디로 로그인해요",
                style = MoyeotaType.BodySm,
                color = MoyeotaColor.TextMute,
            )

            if (noticeMessage != null) {
                Spacer(Modifier.height(20.dp))
                NoticeBanner(kind = NoticeKind.INFO, text = noticeMessage)
            }

            Spacer(Modifier.height(32.dp))
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
                    // 글자만 눌리던 토글을 48dp 터치 + 물결로(디자인 QA G12). 글자 끝은 필드 안쪽 여백에 맞춘다
                    TextLinkButton(
                        text = if (passwordVisible) "숨기기" else "보기",
                        onClick = { passwordVisible = !passwordVisible },
                        color = MoyeotaColor.TextMute,
                        style = MoyeotaType.ButtonSm,
                        modifier = Modifier.offset(x = 8.dp),
                    )
                },
            )

            // 실패 사유는 화면을 갈아엎지 않고 입력 바로 아래에 남긴다 — 고칠 곳이 위에 있기 때문
            if (errorMessage != null) {
                Spacer(Modifier.height(16.dp))
                NoticeBanner(kind = NoticeKind.ERROR, text = errorMessage)
            }

            Spacer(Modifier.height(32.dp))
            PrimaryCtaButton(
                text = "로그인",
                onClick = { onSubmit(loginId.trim(), password) },
                enabled = canSubmit,
                loading = submitting,
            )

            Spacer(Modifier.height(4.dp))
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
                Spacer(Modifier.size(4.dp))
                // 04 시작 화면의 「로그인」과 같은 링크 — 48dp 터치 + 물결(디자인 QA G12)
                TextLinkButton(text = "가입하기", onClick = onSignUp)
            }
            Spacer(Modifier.height(24.dp))
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
