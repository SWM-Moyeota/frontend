package com.moyeota.presentation.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.moyeota.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LoginViewModel(private val repository: AuthRepository) : ViewModel() {

    data class UiState(
        val submitting: Boolean = false,
        val errorMessage: String? = null,
        val loggedIn: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /**
     * 성공 시 토큰 저장·[AuthRepository.authState] 전환까지 Repository 안에서 끝난다
     * — 여기서 세션을 따로 보관하지 않는다 (21 보고서 §2).
     */
    fun login(loginId: String, password: String) {
        if (_uiState.value.submitting) return // 중복 제출 차단 (공통 규칙)
        viewModelScope.launch {
            _uiState.update { it.copy(submitting = true, errorMessage = null) }
            try {
                repository.login(loginId, password)
                _uiState.update { it.copy(submitting = false, loggedIn = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(submitting = false, errorMessage = e.authUserMessage()) }
            }
        }
    }

    companion object {
        fun factory(repository: AuthRepository) = viewModelFactory {
            initializer { LoginViewModel(repository) }
        }
    }
}

// 04a 아이디 로그인 — 로그인 API 진입점. 성공하면 onLoggedIn 으로 14 홈에 넘긴다.
@Composable
fun LoginFormRoute(
    repository: AuthRepository,
    onBack: () -> Unit = {},
    onLoggedIn: () -> Unit = {},
    onSignUp: () -> Unit = {},
    noticeMessage: String? = null,
) {
    val viewModel: LoginViewModel = viewModel(factory = LoginViewModel.factory(repository))
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.loggedIn) {
        if (state.loggedIn) onLoggedIn()
    }

    LoginFormScreen(
        onBack = onBack,
        onSubmit = viewModel::login,
        onSignUp = onSignUp,
        submitting = state.submitting,
        errorMessage = state.errorMessage,
        // 로그인 실패 문구가 떴다면 "왜 이 화면인지"보다 그쪽이 급하다 — 안내는 접는다
        noticeMessage = noticeMessage.takeIf { state.errorMessage == null },
    )
}
