package com.moyeota.presentation.feature.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moyeota.core.designsystem.component.MoyeotaTextField
import com.moyeota.core.designsystem.component.MoyeotaTopBar
import com.moyeota.core.designsystem.component.NavigationBarSpacer
import com.moyeota.core.designsystem.component.PrimaryCtaButton
import com.moyeota.core.designsystem.component.StatusBarSpacer
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.core.designsystem.theme.MoyeotaTheme
import com.moyeota.core.designsystem.theme.MoyeotaType
import com.moyeota.domain.repository.AuthRepository
import com.moyeota.presentation.core.PhoneNumberTransformation

/**
 * 04b · 아이디 찾기 [신규, 2026-11]
 *
 * 진입: 04a 로그인의 「아이디 찾기」
 *
 * 가입한 휴대폰 번호로 문자 인증을 통과하면 **아이디 전체**를 보여 준다
 * (`POST /api/v1/auth/find-id/send` → `find-id/verify`, 백엔드 #314).
 *
 * 한 화면이 두 모습을 갖는다 — 인증 전에는 번호·인증번호 입력, 인증 뒤에는 결과.
 * 결과를 별 화면으로 쌓지 않는 이유: 뒤로 가면 이미 소진된 인증번호 입력칸이 다시 보이고,
 * 아이디는 인증을 통과한 이 화면 안에서만 보여야 한다(스택에 남겨 두고 돌아올 값이 아니다).
 *
 * 가입되지 않은 번호는 서버가 **발송 단계에서** 알려 준다(404 `USER114`) — 입력칸을 열지 않고 가입으로 보낸다.
 */
@Composable
internal fun FindIdScreen(
    verify: PhoneVerifyState = PhoneVerifyState(),
    nowMillis: Long = 0L,
    notRegistered: Boolean = false,
    onBack: () -> Unit = {},
    onPhoneDigitsChange: (String) -> Unit = {},
    onRequestCode: (String) -> Unit = {},
    onCodeChange: (String) -> Unit = {},
    onSubmitCode: () -> Unit = {},
    onSignUp: () -> Unit = {},
    onLogin: (loginId: String) -> Unit = {},
    onResetPassword: (loginId: String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var phoneDigits by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize().imePadding().background(MoyeotaColor.SurfaceCanvas)) {
        StatusBarSpacer()
        MoyeotaTopBar(title = "아이디 찾기", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            if (verify.stage == PhoneVerifyStage.Verified) {
                FoundLoginId(loginId = verify.token, onLogin = onLogin, onResetPassword = onResetPassword)
            } else {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "가입한 휴대폰 번호로\n아이디를 찾아요",
                    style = MoyeotaType.DisplayMd,
                    color = MoyeotaColor.InkPrimary,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "문자로 받은 인증번호를 확인하면 아이디를 알려 드려요",
                    style = MoyeotaType.BodySm,
                    color = MoyeotaColor.TextMute,
                )

                Spacer(Modifier.height(32.dp))
                MoyeotaTextField(
                    value = phoneDigits,
                    onValueChange = { new ->
                        phoneDigits = new.filter { it.isDigit() }.take(11)
                        onPhoneDigitsChange(phoneDigits)
                    },
                    label = "휴대폰 번호",
                    placeholder = "010-1234-5678",
                    errorText = verify.phoneMessage,
                    // 문자를 보낸 번호를 고치면 인증번호가 무효가 된다 — 확인하는 동안만 잠근다
                    enabled = !verify.verifying,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    visualTransformation = PhoneNumberTransformation,
                )

                if (notRegistered) {
                    // 문구는 번호 입력란 아래(errorText)에 이미 떠 있다 — 여기서는 갈 길만 준다.
                    TextLinkButton(
                        text = "가입하러 가기",
                        onClick = onSignUp,
                        style = MoyeotaType.ButtonSm,
                        modifier = Modifier.offset(x = (-8).dp),
                    )
                }

                PhoneVerificationBlock(
                    state = verify,
                    nowMillis = nowMillis,
                    phoneDigits = phoneDigits,
                    onRequestCode = { onRequestCode(phoneDigits) },
                    onCodeChange = onCodeChange,
                    onSubmitCode = onSubmitCode,
                    // 아래 둘은 가입 화면 전용 갈래다 — 이 화면에서는 그 상태(인증 완료 표시·이미 가입)에 닿지 않는다.
                    onPhoneEditRequested = {},
                    onGoToLogin = {},
                )
            }
            Spacer(Modifier.height(24.dp))
        }
        NavigationBarSpacer()
    }
}

@Composable
private fun FoundLoginId(
    loginId: String,
    onLogin: (String) -> Unit,
    onResetPassword: (String) -> Unit,
) {
    Spacer(Modifier.height(24.dp))
    Text(
        text = "아이디를 찾았어요",
        style = MoyeotaType.DisplayMd,
        color = MoyeotaColor.InkPrimary,
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = "가입할 때 만든 아이디예요",
        style = MoyeotaType.BodySm,
        color = MoyeotaColor.TextMute,
    )

    Spacer(Modifier.height(32.dp))
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MoyeotaColor.SurfaceCard, shape)
            .border(BorderStroke(1.dp, MoyeotaColor.Hairline), shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = "아이디", style = MoyeotaType.ButtonSm, color = MoyeotaColor.TextMute)
        SelectionContainer {
            Text(text = loginId, style = MoyeotaType.HeadingXl, color = MoyeotaColor.InkPrimary)
        }
    }

    Spacer(Modifier.height(32.dp))
    PrimaryCtaButton(text = "이 아이디로 로그인", onClick = { onLogin(loginId) })

    Spacer(Modifier.height(4.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "비밀번호가 기억나지 않아요",
            style = MoyeotaType.BodySm,
            color = MoyeotaColor.TextMute,
        )
        Spacer(Modifier.size(4.dp))
        TextLinkButton(text = "비밀번호 재설정", onClick = { onResetPassword(loginId) })
    }
}

// 04b 아이디 찾기 — 문자 인증 두 API 의 진입점. 결과(아이디)는 화면 안에서 보여 주고, 다음 갈 곳만 밖으로 넘긴다.
@Composable
fun FindIdRoute(
    repository: AuthRepository,
    onBack: () -> Unit = {},
    onSignUp: () -> Unit = {},
    onLogin: (loginId: String) -> Unit = {},
    onResetPassword: (loginId: String) -> Unit = {},
) {
    val viewModel: RecoveryCodeViewModel = viewModel(
        factory = RecoveryCodeViewModel.factory(RecoveryPurpose.FIND_LOGIN_ID, repository),
    )
    val state by viewModel.state.collectAsState()
    val nowMillis by viewModel.nowMillis.collectAsState()
    val issue by viewModel.requestIssue.collectAsState()

    FindIdScreen(
        verify = state,
        nowMillis = nowMillis,
        notRegistered = issue == RecoveryRequestIssue.NOT_REGISTERED,
        onBack = onBack,
        onPhoneDigitsChange = viewModel::onPhoneDigitsChanged,
        onRequestCode = { viewModel.requestCode(it) },
        onCodeChange = viewModel::onCodeChanged,
        onSubmitCode = viewModel::verify,
        onSignUp = onSignUp,
        onLogin = onLogin,
        onResetPassword = onResetPassword,
    )
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun FindIdScreenPreview() {
    MoyeotaTheme { FindIdScreen() }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun FindIdScreenFoundPreview() {
    MoyeotaTheme {
        FindIdScreen(verify = PhoneVerifyState(stage = PhoneVerifyStage.Verified, token = "moyeota_test"))
    }
}
