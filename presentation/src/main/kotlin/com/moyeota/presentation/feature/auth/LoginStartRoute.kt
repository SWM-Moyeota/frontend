package com.moyeota.presentation.feature.auth

import androidx.compose.runtime.*
import com.moyeota.domain.model.GoogleCredentialProvider
import com.moyeota.domain.model.GoogleLoginResult
import com.moyeota.domain.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun LoginStartRoute(
    repository: AuthRepository,
    provider: GoogleCredentialProvider,
    onSignupRequired: (GoogleLoginResult.SignupRequired) -> Unit,
    onLoggedIn: () -> Unit,
    onEmailStart: () -> Unit,
    onLogin: () -> Unit,
    notice: String? = null,
) {
    val google = rememberGoogleLogin(repository, provider, onLoggedIn, onSignupRequired)
    LoginScreen(onEmailStart = { if (!google.busy) onEmailStart() }, onLogin = { if (!google.busy) onLogin() },
        googleBusy = google.busy, errorMessage = google.error ?: notice,
        onGoogle = google.start)
}

/** Google 로그인 한 번의 진행 상태. 시작 화면과 로그인 화면이 같은 동작을 쓴다. */
internal class GoogleLoginState(val busy: Boolean, val error: String?, val start: () -> Unit)

/**
 * 「Google로 계속하기」의 동작 - 가입한 회원이면 [onLoggedIn], 처음이면 [onSignupRequired].
 * 계정 선택을 취소하면(토큰 null) 오류 없이 그대로 남는다.
 */
@Composable
internal fun rememberGoogleLogin(
    repository: AuthRepository,
    provider: GoogleCredentialProvider,
    onLoggedIn: () -> Unit,
    onSignupRequired: (GoogleLoginResult.SignupRequired) -> Unit,
): GoogleLoginState {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    return GoogleLoginState(busy, error) {
        if (!busy) {
            busy = true
            error = null
            scope.launch {
                try {
                    val token = provider.getIdToken()
                    if (token != null) when (val result = repository.loginWithGoogle(token)) {
                        is GoogleLoginResult.LoggedIn -> onLoggedIn()
                        is GoogleLoginResult.SignupRequired -> onSignupRequired(result)
                    }
                } catch (e: CancellationException) { throw e
                } catch (e: Exception) { error = e.authUserMessage()
                } finally { busy = false }
            }
        }
    }
}
