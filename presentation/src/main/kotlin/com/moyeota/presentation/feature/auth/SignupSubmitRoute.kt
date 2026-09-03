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

/**
 * 가입 제출 — 12 매너 서약의 「동의하고 가입 완료」 뒤에 있는 유일한 서버 호출 지점.
 *
 * 가입은 로그인시키지 않는다(서버가 토큰 대신 uuid 만 준다). 그래서 **가입 성공 직후
 * 같은 자격증명으로 로그인을 이어서 호출**해야 13 완료 화면 다음에 홈이 열린다.
 */
class SignupViewModel(private val repository: AuthRepository) : ViewModel() {

    data class UiState(
        val submitting: Boolean = false,
        val errorMessage: String? = null,
        val done: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /**
     * 가입은 성공했는데 이어지는 로그인만 실패한 경우를 기억한다.
     *
     * 이 플래그가 없으면 재시도 때 가입부터 다시 시도해 **409 아이디 중복**을 맞고,
     * 사용자는 "방금 만든 계정이 이미 있다"는 막다른 길에 갇힌다.
     */
    private var registered = false

    fun submit(draft: SignupDraft) {
        if (_uiState.value.submitting) return // 중복 제출 차단 (공통 규칙)
        val newUser = draft.toNewUser()
        if (newUser == null) {
            _uiState.update { it.copy(errorMessage = "가입 정보가 부족해요. 이전 단계를 다시 확인해 주세요") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(submitting = true, errorMessage = null) }
            try {
                if (!registered) {
                    repository.register(newUser)
                    registered = true
                }
                // 자동 로그인 — 여기서 토큰이 저장되고 authState 가 Authenticated 로 바뀐다
                repository.login(newUser.loginId, newUser.password)
                _uiState.update { it.copy(submitting = false, done = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(submitting = false, errorMessage = e.authUserMessage()) }
            }
        }
    }

    companion object {
        fun factory(repository: AuthRepository) = viewModelFactory {
            initializer { SignupViewModel(repository) }
        }
    }
}

// 12 매너 서약 — 동의 + 가입 제출 + 자동 로그인 진입점. 성공하면 13 가입 완료로 넘긴다.
@Composable
fun MannerPledgeRoute(
    repository: AuthRepository,
    draft: SignupDraft,
    onBack: () -> Unit = {},
    onCompleted: () -> Unit = {},
) {
    val viewModel: SignupViewModel = viewModel(factory = SignupViewModel.factory(repository))
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.done) {
        if (state.done) onCompleted()
    }

    MannerPledgeScreen(
        onBack = onBack,
        onComplete = { viewModel.submit(draft) },
        submitting = state.submitting,
        errorMessage = state.errorMessage,
    )
}
