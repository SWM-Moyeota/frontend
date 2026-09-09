package com.moyeota.presentation.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.moyeota.domain.model.AuthError
import com.moyeota.domain.model.AuthException
import com.moyeota.domain.repository.AuthRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 입력이 멈췄다고 보는 시간. 한 글자마다 서버를 두드리지 않기 위한 유일한 장치다. */
private const val NICKNAME_DEBOUNCE_MS = 500L

/**
 * 10 프로필 만들기의 닉네임 중복 확인 결과.
 *
 * [Unknown] 이 있는 이유: 확인에 실패했다는 사실은 사용자가 고칠 수 있는 게 아니다.
 * 그래서 화면은 아무 말도 하지 않고 다음 단계도 막지 않는다 — 최종 판정은 어차피 가입 시점의 서버다.
 */
enum class NicknameCheckState {
    /** 아직 물어볼 상태가 아니다(빈 입력·형식 미달·디바운스 대기). */
    Idle,

    /** 서버 조회 중. */
    Checking,

    /** `exists=false` — 지금은 비어 있는 닉네임. */
    Available,

    /** `exists=true` — 이미 누가 쓰고 있다. */
    Taken,

    /** 400 USER107 — 서버 형식 규칙에 걸렸다(앱 규칙과 어긋난 문자). */
    InvalidFormat,

    /** 네트워크·서버 오류. 조용히 넘어간다. */
    Unknown,
}

/**
 * 닉네임 중복 확인만 담당하는 ViewModel.
 *
 * 가입 제출(12 매너 서약)은 [SignupViewModel] 이 따로 한다 — 이쪽은 입력 중 조회 하나뿐이라
 * 초안(SignupDraft)을 들고 있지 않다.
 */
class NicknameCheckViewModel(private val repository: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(NicknameCheckState.Idle)
    val state: StateFlow<NicknameCheckState> = _state.asStateFlow()

    // 진행 중인 조회. 다음 타자가 들어오면 취소한다 — 늦게 도착한 옛 응답이 최신 입력을
    // 덮어쓰는 사고(디바운스 없이 흔한 결함)를 구조적으로 막는다.
    private var job: Job? = null

    fun onNicknameChanged(raw: String) {
        job?.cancel()
        val value = raw.trim()
        // 형식이 안 맞으면 물어볼 것도 없다 — 화면이 이미 형식 오류를 띄우고 있다
        if (!NicknamePolicy.isValid(value)) {
            _state.value = NicknameCheckState.Idle
            return
        }
        job = viewModelScope.launch {
            _state.value = NicknameCheckState.Idle
            delay(NICKNAME_DEBOUNCE_MS)
            _state.value = NicknameCheckState.Checking
            _state.value = try {
                if (repository.isNicknameTaken(value)) {
                    NicknameCheckState.Taken
                } else {
                    NicknameCheckState.Available
                }
            } catch (e: AuthException) {
                // 형식 오류가 두 코드로 온다: 서버 VO 가 먼저 걸리면 USER107,
                // Bean Validation 이 먼저 걸리면 INVALID_REQUEST(message = "nickname: …") — 같은 사실이다.
                val invalidFormat = e.error == AuthError.INVALID_NICKNAME ||
                    (e.error == AuthError.INVALID_REQUEST && e.serverMessage?.startsWith("nickname") == true)
                if (invalidFormat) NicknameCheckState.InvalidFormat else NicknameCheckState.Unknown
            } catch (e: Exception) {
                NicknameCheckState.Unknown
            }
        }
    }

    companion object {
        fun factory(repository: AuthRepository) = viewModelFactory {
            initializer { NicknameCheckViewModel(repository) }
        }
    }
}

// 10 프로필 만들기 — 닉네임 중복 확인(POST /auth/nickname/check) 진입점.
// 화면 자체는 입력을 들고 있는 순수 컴포저블이고, 서버에 묻는 일만 여기로 뺐다.
@Composable
fun ProfileSetupRoute(
    repository: AuthRepository,
    onBack: () -> Unit = {},
    onNext: (SignupDraft) -> Unit = {},
) {
    val viewModel: NicknameCheckViewModel =
        viewModel(factory = NicknameCheckViewModel.factory(repository))
    val checkState by viewModel.state.collectAsState()

    ProfileSetupScreen(
        onBack = onBack,
        onNext = onNext,
        nicknameCheck = checkState,
        onNicknameChange = viewModel::onNicknameChanged,
    )
}
