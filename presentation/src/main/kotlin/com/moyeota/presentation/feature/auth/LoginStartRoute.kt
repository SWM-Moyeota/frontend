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
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LoginScreen(onEmailStart = { if (!busy) onEmailStart() }, onLogin = { if (!busy) onLogin() },
        googleBusy = busy, errorMessage = error ?: notice,
        onGoogle = {
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
        })
}
