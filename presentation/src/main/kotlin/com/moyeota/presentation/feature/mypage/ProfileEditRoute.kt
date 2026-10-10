package com.moyeota.presentation.feature.mypage

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
import com.moyeota.presentation.core.UserNameState
import com.moyeota.presentation.feature.auth.NicknameCheckState
import com.moyeota.presentation.feature.auth.NicknamePolicy
import com.moyeota.presentation.feature.auth.authUserMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 입력이 멈췄다고 보는 시간. 10 프로필 만들기와 같은 값이다 — 같은 입력에 다른 반응 속도를 주면
 * 사용자는 둘 중 하나가 고장 났다고 느낀다.
 */
private const val NICKNAME_DEBOUNCE_MS = 500L

/**
 * 36 닉네임 입력의 오류 문구. null 이면 보여 줄 오류가 없다.
 *
 * 문구는 10 프로필 만들기와 **글자 하나까지 같다** — 같은 규칙을 어겼는데 화면마다 다른 말을 하면
 * 사용자는 규칙이 다르다고 읽는다. 형식 문구는 [NicknamePolicy.validate] 가 그대로 돌려준다.
 *
 * 두 가지는 오류로 치지 않는다:
 * - **빈 입력** — "아직 안 썼다"이지 틀린 게 아니다(10 과 같은 규칙). 「저장」 이 꺼져 있어 충분하다.
 * - **현재 닉네임 그대로** — 바꿀 게 없다는 뜻이고, 자기 닉네임은 서버에 물으면 당연히 「사용 중」으로
 *   돌아온다. 그 응답을 그대로 띄우면 자기 이름이 남의 것처럼 보인다.
 */
internal fun nicknameEditError(
    input: String,
    currentNickname: String?,
    check: NicknameCheckState,
): String? {
    if (input.isEmpty()) return null
    NicknamePolicy.validate(input)?.let { return it }
    if (isNicknameUnchanged(input, currentNickname)) return null
    return when (check) {
        NicknameCheckState.Taken -> "이미 사용 중인 닉네임이에요"
        NicknameCheckState.InvalidFormat -> "닉네임은 한글·영문·숫자 2~10자로 입력해 주세요"
        else -> null
    }
}

/**
 * 「저장」 을 누를 수 있는가 — 2~10자 규칙을 만족하고, 지금 닉네임과 다르고, 중복이 아니고, 저장 중이 아니다.
 *
 * **확인 중([NicknameCheckState.Checking])이거나 확인에 실패([NicknameCheckState.Unknown])해도 막지 않는다.**
 * 10 과 같은 판단이다: 확인과 저장 사이에 누가 그 닉네임을 가져갈 수 있으므로 최종 판정은 어차피
 * `PATCH /users/me` 의 409 다. 네트워크가 나쁠 때 버튼이 영영 꺼져 있는 쪽이 더 큰 손해다.
 */
internal fun canSaveNickname(
    input: String,
    currentNickname: String?,
    check: NicknameCheckState,
    saving: Boolean = false,
): Boolean {
    if (saving) return false
    if (!NicknamePolicy.isValid(input)) return false
    if (isNicknameUnchanged(input, currentNickname)) return false
    return check != NicknameCheckState.Taken && check != NicknameCheckState.InvalidFormat
}

/** 서버가 준 현재 닉네임과 같은 값인가. 앞뒤 공백은 서버 VO 도 strip 하므로 비교에서 무시한다. */
internal fun isNicknameUnchanged(input: String, currentNickname: String?): Boolean =
    currentNickname != null && input.trim() == currentNickname.trim()

/**
 * 36 프로필 수정의 상태·저장.
 *
 * 입력값을 화면(rememberSaveable)이 아니라 여기에 두는 이유: 프리필 값이 **서버 조회 결과**로
 * 늦게 도착한다. 화면이 들고 있으면 "아직 못 받은 이름"과 "사용자가 지운 이름"을 구분할 수 없다.
 */
class ProfileEditViewModel(private val repository: AuthRepository) : ViewModel() {

    data class UiState(
        val nickname: String = "",
        /** 서버가 준 현재 표시 이름. null 이면 아직 못 받았거나 서버에 이름이 없다. */
        val currentNickname: String? = null,
        val check: NicknameCheckState = NicknameCheckState.Idle,
        val saving: Boolean = false,
        /** 저장 실패 문구. 중복·형식 오류는 필드가 말하므로 여기엔 담지 않는다. */
        val errorMessage: String? = null,
        val saved: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // 진행 중인 중복 확인. 다음 타자가 들어오면 취소한다 — 늦게 도착한 옛 응답이 최신 입력을
    // 덮어쓰는 사고를 구조적으로 막는다(10 과 같은 장치).
    private var checkJob: Job? = null
    private var seeded = false

    /**
     * 서버가 준 표시 이름을 반영한다. **비교 기준([UiState.currentNickname])은 올 때마다 갱신**하고,
     * **입력란은 처음 한 번만** 채운다 — 조회가 두 번 끝나도(탭 복귀, 저장 직후 재조회) 사용자가
     * 고치던 글자를 되돌리지 않기 위해서다. 기준까지 한 번만 세우면 저장 직후 다시 들어왔을 때
     * 옛 이름이 기준으로 굳어, 방금 저장한 자기 닉네임이 「이미 사용 중」으로 보인다(QA 113 O2).
     *
     * 표시 이름이 닉네임 규칙에 안 맞으면 입력란에 넣지 않는다. 닉네임이 없는 계정에는 서버가
     * 마스킹된 실명(`김*윤`)을 내려주는데, 그걸 채우면 열자마자 형식 오류가 뜬다(QA 113 O1).
     * 조회가 늦게 끝난 사이 사용자가 벌써 입력했다면 그 입력은 지킨다.
     */
    fun prefill(name: String?) {
        val seedInput = !seeded
        seeded = true
        _uiState.update {
            val prefillable = name != null && NicknamePolicy.isValid(name)
            it.copy(
                currentNickname = name,
                nickname = if (seedInput && it.nickname.isEmpty() && prefillable) name!! else it.nickname,
            )
        }
    }

    /**
     * 입력 변경. 공백은 규칙상 못 쓰는 문자라 오류로 튕기기보다 조용히 걸러 받는다(10 과 같다).
     * 형식이 맞고 지금 닉네임과 다를 때에만, 입력이 멈춘 뒤 서버에 중복을 묻는다.
     */
    fun onNicknameChange(raw: String) {
        val value = raw.filter { !it.isWhitespace() }.take(NicknamePolicy.MAX_LENGTH)
        checkJob?.cancel()
        // 입력이 바뀌면 직전 확인 결과는 더 이상 이 글자에 대한 답이 아니다 — 함께 비운다.
        _uiState.update { it.copy(nickname = value, check = NicknameCheckState.Idle, errorMessage = null) }
        val current = _uiState.value.currentNickname
        // 형식이 안 맞으면 물어볼 것도 없고, 내 닉네임을 물으면 「사용 중」이 돌아온다.
        if (!NicknamePolicy.isValid(value) || isNicknameUnchanged(value, current)) return
        checkJob = viewModelScope.launch {
            delay(NICKNAME_DEBOUNCE_MS)
            _uiState.update { it.copy(check = NicknameCheckState.Checking) }
            val result = try {
                if (repository.isNicknameTaken(value.trim())) {
                    NicknameCheckState.Taken
                } else {
                    NicknameCheckState.Available
                }
            } catch (e: AuthException) {
                if (e.isNicknameFormatError()) NicknameCheckState.InvalidFormat else NicknameCheckState.Unknown
            } catch (e: Exception) {
                NicknameCheckState.Unknown
            }
            _uiState.update { it.copy(check = result) }
        }
    }

    /**
     * `PATCH /users/me` 로 닉네임만 보낸다(imageUrl 은 null = 보내지 않음 — 서버는 넘어온 필드만 갱신한다).
     *
     * 성공하면 [UiState.saved] 로 화면 이동을 알린다. 여기서 이름 캐시를 직접 고치지 않는다
     * — 표시 이름의 단일 출처는 `UserProfileViewModel` 이고, 그쪽을 다시 조회하는 일은 NavGraph 가 맡는다.
     */
    fun save() {
        val state = _uiState.value
        if (!canSaveNickname(state.nickname, state.currentNickname, state.check, state.saving)) return
        val nickname = state.nickname.trim()
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, errorMessage = null) }
            try {
                repository.updateProfile(nickname = nickname, imageUrl = null)
                _uiState.update { it.copy(saving = false, saved = true) }
            } catch (e: Exception) {
                val duplicated = (e as? AuthException)?.error == AuthError.NICKNAME_DUPLICATED
                val invalidFormat = e.isNicknameFormatError()
                _uiState.update {
                    it.copy(
                        saving = false,
                        // 중복·형식은 필드가 빨갛게 말해 주고 「저장」 도 함께 꺼진다
                        // — 같은 문장을 버튼 아래에 한 번 더 띄우지 않는다.
                        check = when {
                            duplicated -> NicknameCheckState.Taken
                            invalidFormat -> NicknameCheckState.InvalidFormat
                            else -> it.check
                        },
                        errorMessage = if (duplicated || invalidFormat) null else e.authUserMessage(),
                    )
                }
            }
        }
    }

    companion object {
        fun factory(repository: AuthRepository) = viewModelFactory {
            initializer { ProfileEditViewModel(repository) }
        }
    }
}

/**
 * 닉네임 형식 오류는 **서버 코드 두 개로 온다** — 도메인 VO 가 먼저 걸리면 400 `USER107`,
 * Bean Validation 이 먼저 걸리면 `INVALID_REQUEST(message = "nickname: …")` 다.
 * 사용자에게는 같은 사실이므로 한 판단으로 모은다(10 의 중복 확인과 같은 규칙).
 */
private fun Throwable.isNicknameFormatError(): Boolean {
    val authException = this as? AuthException ?: return false
    return authException.error == AuthError.INVALID_NICKNAME ||
        (authException.error == AuthError.INVALID_REQUEST && authException.serverMessage?.startsWith("nickname") == true)
}

/**
 * 36 프로필 수정 — `PATCH /users/me`(nickname) 진입점.
 *
 * @param userName 35 와 같은 인스턴스(`UserProfileViewModel`)의 표시 이름. 프리필 기준값이다.
 *   조회가 끝나기 전에 들어올 수 있어 [UserNameState.Resolved] 가 될 때 한 번만 입력에 채운다.
 * @param onSaved 저장 성공. NavGraph 가 표시 이름을 다시 조회하고 35 로 되돌린다.
 */
@Composable
fun ProfileEditRoute(
    repository: AuthRepository,
    userName: UserNameState = UserNameState.Loading,
    onBack: () -> Unit = {},
    onSaved: () -> Unit = {},
) {
    val viewModel: ProfileEditViewModel =
        viewModel(factory = ProfileEditViewModel.factory(repository))
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(userName) {
        (userName as? UserNameState.Resolved)?.let { viewModel.prefill(it.name) }
    }

    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }

    ProfileEditScreen(
        nickname = state.nickname,
        currentNickname = state.currentNickname,
        check = state.check,
        saving = state.saving,
        errorMessage = state.errorMessage,
        onNicknameChange = viewModel::onNicknameChange,
        onSave = viewModel::save,
        onBack = onBack,
    )
}
