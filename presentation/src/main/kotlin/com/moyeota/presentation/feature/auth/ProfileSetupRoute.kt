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

/**
 * 10 프로필 만들기의 서버 호출 진입점 — 입력값 자체는 여전히 화면이 들고 있다.
 *
 * ViewModel 이 둘인 이유는 두 호출의 성격이 다르기 때문이다:
 * - [NicknameCheckViewModel] — 입력 중 디바운스 조회 하나. 결과를 버려도 가입은 된다.
 * - [PhoneVerifyViewModel] — **가입 통과 조건**인 토큰을 들고 있고, 쿨다운·만료 타이머를 돌린다.
 *   둘을 한 ViewModel 로 합치면 닉네임 한 글자마다 인증 상태까지 같이 흔들리게 된다.
 *
 * 두 ViewModel 모두 이 NavBackStackEntry 에 묶여 **12 매너 서약에 올라가 있는 동안 살아 있다** —
 * 닉네임 중복(409)으로 되돌아왔을 때 인증 토큰을 그대로 써야 하기 때문이다(재인증은 금지).
 *
 * @param reverifyPhoneRequest 12 에서 가입이 `PHONE_NOT_VERIFIED` 로 떨어져 되돌려 보낸 횟수.
 *   값이 **바뀔 때마다** 인증 블록을 초기화한다. Boolean 이 아니라 카운터인 이유는 같은 일이
 *   두 번 이상 일어날 수 있고(토큰이 또 만료될 수 있다), Boolean 이면 두 번째 신호를 놓치기 때문이다.
 *   0 은 "아직 그런 일이 없었다"는 초기값이라 아무 동작도 하지 않는다.
 * @param onGoToLogin 이미 가입된 번호(409)로 발송이 끊겼을 때 안내하는 길 — 04a 로그인으로 보낸다.
 */
@Composable
fun ProfileSetupRoute(
    repository: AuthRepository,
    onBack: () -> Unit = {},
    onNext: (SignupDraft) -> Unit = {},
    onGoToLogin: () -> Unit = {},
    reverifyPhoneRequest: Int = 0,
) {
    val viewModel: NicknameCheckViewModel =
        viewModel(factory = NicknameCheckViewModel.factory(repository))
    val phoneViewModel: PhoneVerifyViewModel =
        viewModel(factory = PhoneVerifyViewModel.factory(repository))
    val checkState by viewModel.state.collectAsState()
    val phoneState by phoneViewModel.state.collectAsState()
    val nowMillis by phoneViewModel.nowMillis.collectAsState()

    // 신호를 ViewModel 에 넘기고, 중복 적용 방어는 그쪽이 한다(10 의 컴포지션은 12 로 올라갈 때마다
    // 버려지므로, 여기서 LaunchedEffect 키만으로 판단하면 12 에서 뒤로 돌아올 때도 다시 적용된다).
    LaunchedEffect(reverifyPhoneRequest) {
        if (reverifyPhoneRequest > 0) phoneViewModel.onSignupRejectedPhone(reverifyPhoneRequest)
    }

    ProfileSetupScreen(
        onBack = onBack,
        onNext = onNext,
        nicknameCheck = checkState,
        onNicknameChange = viewModel::onNicknameChanged,
        phoneVerify = phoneState,
        nowMillis = nowMillis,
        onPhoneDigitsChange = phoneViewModel::onPhoneDigitsChanged,
        onRequestVerificationCode = phoneViewModel::requestCode,
        onVerificationCodeChange = phoneViewModel::onCodeChanged,
        onSubmitVerificationCode = phoneViewModel::verify,
        onPhoneEditRequested = phoneViewModel::onPhoneEditRequested,
        onGoToLogin = onGoToLogin,
    )
}
