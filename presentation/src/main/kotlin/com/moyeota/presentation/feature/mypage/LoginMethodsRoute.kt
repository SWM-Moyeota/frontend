package com.moyeota.presentation.feature.mypage

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.moyeota.core.designsystem.component.*
import com.moyeota.core.designsystem.theme.MoyeotaType
import com.moyeota.domain.model.*
import com.moyeota.domain.repository.AuthRepository
import com.moyeota.presentation.core.*
import com.moyeota.presentation.feature.auth.authUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LoginMethodsViewModel(
    private val repository: AuthRepository,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
) : ViewModel() {
    data class State(
        val methods: LoginMethods? = null,
        val loading: Boolean = true,
        val busy: Boolean = false,
        val codeSent: Boolean = false,
        val code: String = "",
        val verified: Boolean = false,
        val resendSeconds: Int = 0,
        val error: String? = null,
        val notice: String? = null,
    )
    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()
    private var verificationToken: String? = null
    private var expiresAt = 0L
    private var resendAt = 0L

    init {
        refresh()
        viewModelScope.launch {
            while (true) {
                delay(1000)
                expireVerification()
                _state.update { it.copy(resendSeconds = ((resendAt - now() + 999) / 1000).coerceAtLeast(0).toInt()) }
            }
        }
    }
    fun refresh() {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val methods = repository.getLoginMethods()
                _state.update { it.copy(methods = methods, loading = false) }
            }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.update { it.copy(loading = false, error = e.loginMethodMessage()) } }
        }
    }
    fun changeCode(value: String) { if (!_state.value.busy) _state.update { it.copy(code = value.filter(Char::isDigit).take(6), error = null) } }
    fun sendCode() = action {
        if (now() < resendAt) return@action
        try { repository.sendLoginMethodCode() }
        catch (e: AuthException) {
            // 가입용 문자와 쿨다운(1분)을 같이 쓴다 — 가입 직후에는 여기서 처음 눌러도 429 USER202 가 온다.
            // 타이머를 세우지 않으면 버튼이 바로 살아나 「버튼의 남은 시간이 끝나면」이라는 안내가 거짓이 된다.
            if (e.error == AuthError.SMS_COOLDOWN) {
                val seconds = e.retryAfterSeconds ?: 60
                resendAt = now() + seconds * 1000L
                _state.update { it.copy(resendSeconds = seconds) }
            }
            throw e
        }
        verificationToken = null
        resendAt = now() + 60_000
        _state.update { it.copy(codeSent = true, code = "", verified = false, resendSeconds = 60,
            notice = "등록된 휴대폰으로 인증번호를 보냈어요") }
    }
    fun verifyCode() = action {
        if (!AuthPolicy.isValidVerificationCode(_state.value.code)) return@action
        val proof = repository.verifyLoginMethodCode(_state.value.code)
        verificationToken = proof.token
        expiresAt = now() + proof.expiresInSeconds * 1000L
        _state.update { it.copy(verified = true, code = "", notice = "5분 안에 연결할 구글 계정을 선택해 주세요") }
    }
    internal fun expireVerification() {
        if (verificationToken != null && now() >= expiresAt) {
            verificationToken = null
            _state.update { it.copy(verified = false, codeSent = false, notice = null,
                error = "연결 인증 시간이 지났어요. 문자 인증부터 다시 해 주세요") }
        }
    }
    /** 공급자 토큰은 호출 인자로만 존재하고 상태에는 넣지 않는다. */
    suspend fun linkGoogle(idToken: String) {
        if (_state.value.busy) return
        expireVerification()
        val proof = verificationToken ?: return
        _state.update { it.copy(busy = true, error = null) }
        try {
            repository.linkGoogle(idToken, proof)
            verificationToken = null
            _state.update { it.copy(verified = false, codeSent = false, notice = "구글 계정을 연결했어요") }
            val methods = repository.getLoginMethods()
            _state.update { it.copy(methods = methods) }
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            if ((e as? AuthException)?.error == AuthError.SOCIAL_LINK_VERIFICATION_EXPIRED) {
                verificationToken = null
                _state.update { it.copy(verified = false, codeSent = false) }
            }
            _state.update { it.copy(error = e.loginMethodMessage()) }
        } finally { _state.update { it.copy(busy = false) } }
    }
    fun sdkFailed() { _state.update { it.copy(error = "구글 인증을 완료하지 못했어요. 다시 시도해 주세요") } }
    private fun action(block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null, notice = null) }
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.update { it.copy(error = e.loginMethodMessage()) } }
            finally { _state.update { it.copy(busy = false) } }
        }
    }
}

@Composable
fun LoginMethodsRoute(repository: AuthRepository, provider: GoogleCredentialProvider,
    onBack: () -> Unit, onPasswordReset: () -> Unit) {
    val model: LoginMethodsViewModel = viewModel(factory = viewModelFactory { initializer { LoginMethodsViewModel(repository) } })
    val state by model.state.collectAsState()
    val scope = rememberCoroutineScope()
    var sdkBusy by remember { mutableStateOf(false) }
    BackStateScaffold(title = "로그인 수단 관리", onBack = onBack) {
        when {
            state.loading -> LoadingBox()
            state.methods == null -> ErrorBox(state.error ?: "로그인 수단을 불러오지 못했어요", model::refresh)
            else -> LoginMethodsScreen(state, sdkBusy, model::sendCode, model::changeCode, model::verifyCode,
                onPasswordReset = onPasswordReset,
                onGoogle = {
                    if (!sdkBusy) {
                        sdkBusy = true
                        scope.launch {
                            try { provider.getIdToken()?.let { model.linkGoogle(it) } }
                            catch (e: CancellationException) { throw e }
                            catch (_: Exception) { model.sdkFailed() }
                            finally { sdkBusy = false }
                        }
                    }
                })
        }
    }
}

@Composable
internal fun LoginMethodsScreen(state: LoginMethodsViewModel.State, sdkBusy: Boolean,
    onSend: () -> Unit, onCode: (String) -> Unit, onVerify: () -> Unit,
    onPasswordReset: () -> Unit, onGoogle: () -> Unit) {
    val methods = state.methods ?: return
    val busy = state.busy || sdkBusy
    val google = methods.methods.firstOrNull { it.provider == SocialProvider.GOOGLE }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("연결된 로그인 수단", style = MoyeotaType.HeadingMd)
        if (methods.hasPassword) {
            Text("아이디와 비밀번호", style = MoyeotaType.BodyMd)
            SecondaryButton("비밀번호 재설정", onPasswordReset, enabled = !busy)
        }
        methods.methods.forEach {
            Text("${if (it.provider == SocialProvider.GOOGLE) "Google" else "Apple"} · ${if (it.status == LoginMethodStatus.ACTIVE) "연결됨" else "재인증 필요"}", style = MoyeotaType.BodyMd)
        }
        if (state.error != null) NoticeBanner(NoticeKind.ERROR, state.error)
        if (state.notice != null) NoticeBanner(NoticeKind.INFO, state.notice)
        if (google?.status != LoginMethodStatus.ACTIVE) {
            Text("등록된 휴대폰으로 본인을 확인한 뒤 구글 계정을 연결해요. 연결된 계정은 교체하거나 합칠 수 없어요.", style = MoyeotaType.BodyMd)
            if (!state.verified) {
                SecondaryButton(if (state.resendSeconds > 0) "다시 받기 (${state.resendSeconds}초)" else "인증번호 받기",
                    onSend, enabled = !busy && state.resendSeconds == 0)
                if (state.codeSent) {
                    MoyeotaTextField(value = state.code, onValueChange = onCode, label = "인증번호", placeholder = "숫자 6자리", enabled = !busy)
                    PrimaryCtaButton("인증번호 확인", onVerify, enabled = !busy && AuthPolicy.isValidVerificationCode(state.code))
                }
            } else {
                PrimaryCtaButton(if (busy) "연결 중…" else "Google 계정 연결", onGoogle, enabled = !busy)
            }
        }
        NavigationBarSpacer()
    }
}

private fun Throwable.loginMethodMessage(): String =
    if ((this as? AuthException)?.error == AuthError.SMS_DAILY_LIMIT_EXCEEDED)
        "오늘 받을 수 있는 인증번호를 모두 썼어요. 내일 다시 시도해 주세요"
    else authUserMessage()
