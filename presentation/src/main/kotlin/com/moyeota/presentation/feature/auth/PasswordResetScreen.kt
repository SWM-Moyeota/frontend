package com.moyeota.presentation.feature.auth

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
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
import com.moyeota.domain.model.AuthPolicy
import com.moyeota.domain.repository.AuthRepository
import com.moyeota.presentation.core.PhoneNumberTransformation

/**
 * 04c · 비밀번호 재설정 [신규, 2026-11]
 *
 * 진입: 04a 로그인의 「비밀번호 재설정」 · 04b 아이디 찾기 결과의 「비밀번호 재설정」(아이디를 채워서 온다)
 *
 * 두 단계다 (`/api/v1/auth/password-reset/send → verify → confirm`, 백엔드 #331).
 * 1. 아이디 + 휴대폰 번호로 인증번호를 받아 확인한다 → 일회용 재설정 토큰(10분)
 * 2. 새 비밀번호를 두 번 입력해 저장한다 → 04a 로 돌아가 새 비밀번호로 로그인
 *
 * **「문자를 보냈어요」라고 말하지 않는다.** 서버는 아이디·번호가 가입 정보와 맞든 틀리든 같은 접수를 주고
 * (계정이 있는지를 흘리지 않으려는 설계다), 문자는 맞을 때만 나간다. 그래서 입력칸을 연 뒤
 * 「일치하면 도착한다」와 「안 오면 아이디·번호를 확인하라」를 같이 말한다 — 그 안내가 없으면
 * 아이디를 잘못 넣은 사람이 오지 않는 문자를 3분 동안 기다린다.
 *
 * 재설정 토큰은 화면이 살아 있는 동안 메모리에만 있다 — 저장하지 않고, 화면을 나가면 사라진다.
 *
 * @param initialLoginId 04b 에서 찾은 아이디. 비어 있으면 사용자가 직접 넣는다.
 */
@Composable
internal fun PasswordResetScreen(
    initialLoginId: String = "",
    verify: PhoneVerifyState = PhoneVerifyState(),
    nowMillis: Long = 0L,
    submitting: Boolean = false,
    passwordError: String? = null,
    confirmError: String? = null,
    bannerMessage: String? = null,
    onBack: () -> Unit = {},
    onLoginIdChange: (String) -> Unit = {},
    onPhoneDigitsChange: (String) -> Unit = {},
    onRequestCode: (phoneDigits: String, loginId: String) -> Unit = { _, _ -> },
    onCodeChange: (String) -> Unit = {},
    onSubmitCode: () -> Unit = {},
    onFindId: () -> Unit = {},
    onPasswordInputChange: () -> Unit = {},
    onConfirm: (loginId: String, newPassword: String, newPasswordConfirm: String) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier,
) {
    var loginId by remember { mutableStateOf(initialLoginId) }
    var phoneDigits by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var newPasswordConfirm by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    // 인증이 풀리면(토큰 만료로 되돌림) 적어 둔 새 비밀번호를 남기지 않는다.
    LaunchedEffect(verify.stage) {
        if (verify.stage != PhoneVerifyStage.Verified) {
            newPassword = ""
            newPasswordConfirm = ""
            passwordVisible = false
        }
    }

    Column(modifier = modifier.fillMaxSize().imePadding().background(MoyeotaColor.SurfaceCanvas)) {
        StatusBarSpacer()
        MoyeotaTopBar(title = "비밀번호 재설정", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            if (verify.stage != PhoneVerifyStage.Verified) {
                // ── 1단계: 아이디 + 휴대폰 인증 ──
                // 형식이 틀린 아이디는 서버가 접수 전에 400 으로 끊는다 — 버튼을 잠가 그 왕복을 없앤다.
                val loginIdReady = AuthPolicy.isValidLoginId(loginId)

                Spacer(Modifier.height(24.dp))
                Text(
                    text = "비밀번호를 새로 만들어요",
                    style = MoyeotaType.DisplayMd,
                    color = MoyeotaColor.InkPrimary,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "아이디와 가입한 휴대폰 번호를 확인할게요",
                    style = MoyeotaType.BodySm,
                    color = MoyeotaColor.TextMute,
                )

                Spacer(Modifier.height(32.dp))
                MoyeotaTextField(
                    value = loginId,
                    onValueChange = { new ->
                        loginId = new.filter { !it.isWhitespace() }.lowercase()
                        onLoginIdChange(loginId)
                    },
                    label = "아이디",
                    placeholder = "moyeota",
                    enabled = !verify.verifying,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Next,
                    ),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "아이디가 기억나지 않아요",
                        style = MoyeotaType.CaptionMd,
                        color = MoyeotaColor.TextMute,
                    )
                    Spacer(Modifier.size(2.dp))
                    TextLinkButton(text = "아이디 찾기", onClick = onFindId, style = MoyeotaType.ButtonSm)
                }

                Spacer(Modifier.height(8.dp))
                MoyeotaTextField(
                    value = phoneDigits,
                    onValueChange = { new ->
                        phoneDigits = new.filter { it.isDigit() }.take(11)
                        onPhoneDigitsChange(phoneDigits)
                    },
                    label = "휴대폰 번호",
                    placeholder = "010-1234-5678",
                    errorText = verify.phoneMessage,
                    helperText = if (verify.stage == PhoneVerifyStage.Idle) "가입할 때 쓴 번호를 입력해 주세요" else null,
                    enabled = !verify.verifying,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    visualTransformation = PhoneNumberTransformation,
                )

                if (verify.stage == PhoneVerifyStage.CodeSent) {
                    Spacer(Modifier.height(16.dp))
                    NoticeBanner(
                        kind = NoticeKind.INFO,
                        text = "아이디와 번호가 가입 정보와 일치하면 인증번호가 문자로 도착해요. " +
                            "1~2분이 지나도 오지 않으면 아이디와 번호를 다시 확인해 주세요",
                    )
                }

                PhoneVerificationBlock(
                    state = verify,
                    nowMillis = nowMillis,
                    // 아이디가 비었거나 형식이 틀리면 번호가 멀쩡해도 요청을 열지 않는다
                    phoneDigits = if (loginIdReady) phoneDigits else "",
                    onRequestCode = { onRequestCode(phoneDigits, loginId) },
                    onCodeChange = onCodeChange,
                    onSubmitCode = onSubmitCode,
                    onPhoneEditRequested = {},
                    onGoToLogin = {},
                )
            } else {
                // ── 2단계: 새 비밀번호 ──
                // 서버 규칙(`PasswordPolicy`)과 같은 검사를 먼저 한다 — 최종 판정은 서버다.
                val localPasswordError =
                    if (newPassword.isNotEmpty() && !AuthPolicy.isValidPassword(newPassword)) {
                        "영문·숫자·특수문자를 모두 넣어 8자 이상으로 만들어 주세요"
                    } else {
                        null
                    }
                val localConfirmError =
                    if (newPasswordConfirm.isNotEmpty() && newPasswordConfirm != newPassword) {
                        "새 비밀번호와 같게 입력해 주세요"
                    } else {
                        null
                    }
                val canSubmit = !submitting &&
                    AuthPolicy.isValidPassword(newPassword) &&
                    newPasswordConfirm == newPassword

                Spacer(Modifier.height(24.dp))
                Text(
                    text = "새 비밀번호를 정해 주세요",
                    style = MoyeotaType.DisplayMd,
                    color = MoyeotaColor.InkPrimary,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "휴대폰 인증이 끝났어요. 10분 안에 새 비밀번호를 저장해 주세요",
                    style = MoyeotaType.BodySm,
                    color = MoyeotaColor.TextMute,
                )

                Spacer(Modifier.height(32.dp))
                val visual = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation()
                MoyeotaTextField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it.take(AuthPolicy.PASSWORD_MAX_LENGTH)
                        onPasswordInputChange()
                    },
                    label = "새 비밀번호",
                    placeholder = "영문·숫자·특수문자 조합 8자 이상",
                    // 앱 판정(고칠 곳이 더 구체적이다)을 서버 응답 문구보다 앞세운다
                    errorText = localPasswordError ?: passwordError,
                    enabled = !submitting,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next,
                    ),
                    visualTransformation = visual,
                    trailing = {
                        TextLinkButton(
                            text = if (passwordVisible) "숨기기" else "보기",
                            onClick = { passwordVisible = !passwordVisible },
                            color = MoyeotaColor.TextMute,
                            style = MoyeotaType.ButtonSm,
                            modifier = Modifier.offset(x = 8.dp),
                        )
                    },
                )

                Spacer(Modifier.height(20.dp))
                MoyeotaTextField(
                    value = newPasswordConfirm,
                    onValueChange = {
                        newPasswordConfirm = it.take(AuthPolicy.PASSWORD_MAX_LENGTH)
                        onPasswordInputChange()
                    },
                    label = "새 비밀번호 확인",
                    placeholder = "한 번 더 입력해 주세요",
                    errorText = localConfirmError ?: confirmError,
                    enabled = !submitting,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    visualTransformation = visual,
                )

                if (bannerMessage != null) {
                    Spacer(Modifier.height(16.dp))
                    NoticeBanner(kind = NoticeKind.ERROR, text = bannerMessage)
                }

                Spacer(Modifier.height(32.dp))
                PrimaryCtaButton(
                    text = "비밀번호 변경",
                    onClick = { onConfirm(loginId, newPassword, newPasswordConfirm) },
                    enabled = canSubmit,
                    loading = submitting,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "변경하면 다른 기기에서는 로그아웃돼요",
                    style = MoyeotaType.CaptionMd,
                    color = MoyeotaColor.TextMute,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
        NavigationBarSpacer()
    }
}

// 04c 비밀번호 재설정 — 재설정 세 API 의 진입점. 성공하면 아이디를 실어 onCompleted 로 04a 에 넘긴다.
@Composable
fun PasswordResetRoute(
    repository: AuthRepository,
    initialLoginId: String = "",
    onBack: () -> Unit = {},
    onFindId: () -> Unit = {},
    onCompleted: (loginId: String) -> Unit = {},
) {
    val codeViewModel: RecoveryCodeViewModel = viewModel(
        factory = RecoveryCodeViewModel.factory(RecoveryPurpose.RESET_PASSWORD, repository),
    )
    val confirmViewModel: PasswordResetConfirmViewModel = viewModel(
        factory = PasswordResetConfirmViewModel.factory(repository),
    )
    val verify by codeViewModel.state.collectAsState()
    val nowMillis by codeViewModel.nowMillis.collectAsState()
    val confirm by confirmViewModel.uiState.collectAsState()

    // 저장에 쓴 아이디 — 성공 뒤 04a 의 아이디 칸을 채워 준다.
    var submittedLoginId by remember { mutableStateOf("") }

    LaunchedEffect(confirm.completed) {
        if (confirm.completed) onCompleted(submittedLoginId)
    }
    // 토큰이 무효다(만료·이미 사용) — 새 비밀번호를 고쳐도 풀리지 않으니 인증 단계로 되돌린다.
    LaunchedEffect(confirm.reverifyMessage) {
        confirm.reverifyMessage?.let { message ->
            codeViewModel.restart(message)
            confirmViewModel.onReverifyHandled()
        }
    }

    PasswordResetScreen(
        initialLoginId = initialLoginId,
        verify = verify,
        nowMillis = nowMillis,
        submitting = confirm.submitting,
        passwordError = confirm.passwordError,
        confirmError = confirm.confirmError,
        bannerMessage = confirm.bannerMessage,
        onBack = onBack,
        onLoginIdChange = codeViewModel::onLoginIdChanged,
        onPhoneDigitsChange = codeViewModel::onPhoneDigitsChanged,
        onRequestCode = { phoneDigits, loginId -> codeViewModel.requestCode(phoneDigits, loginId) },
        onCodeChange = codeViewModel::onCodeChanged,
        onSubmitCode = codeViewModel::verify,
        onFindId = onFindId,
        onPasswordInputChange = confirmViewModel::onInputChanged,
        onConfirm = { loginId, newPassword, newPasswordConfirm ->
            submittedLoginId = loginId
            confirmViewModel.confirm(verify.token, newPassword, newPasswordConfirm)
        },
    )
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun PasswordResetScreenPreview() {
    MoyeotaTheme { PasswordResetScreen(initialLoginId = "moyeota_test") }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun PasswordResetScreenNewPasswordPreview() {
    MoyeotaTheme {
        PasswordResetScreen(verify = PhoneVerifyState(stage = PhoneVerifyStage.Verified, token = "t"))
    }
}
