package com.moyeota.presentation.feature.mypage

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
import com.moyeota.domain.model.AuthPolicy
import com.moyeota.domain.repository.AuthRepository
import com.moyeota.presentation.feature.auth.authUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 37 이 보여 주는 「무엇이 지워지는가」 목록. **서버가 실제로 하는 일만 적는다.**
 *
 * 각 줄의 근거(`UserDeletionService.delete` + 두 리스너):
 * | 문구 | 서버 동작 |
 * |---|---|
 * | 다시 로그인 불가 | `localUsers.deleteByUserId` — 아이디·비밀번호가 삭제된다 |
 * | 채팅방 나감 + 「탈퇴한 사용자」 | `ChatRoomUserDeletionListener` + `User.DELETED_DISPLAY_NAME` |
 * | 자주 가는 곳 삭제 | `FavoritePlaceUserDeletionListener` |
 * | 같은 번호 N일 재가입 불가 | `userDeletions.record` + `SmsVerificationService.send` 의 `USER109` |
 *
 * **적지 않은 것이 더 중요하다.** 서버는 지난 합승 기록을 지우지 않는다 — 끝난 방의 멤버로는 남고
 * 이름만 가려진다. "모든 기록이 사라져요" 같은 말을 쓰면 지킬 수 없는 약속이 된다.
 * 진행 중인 합승 제약은 경고가 아니라 **실패 사유**라 여기 넣지 않는다
 * (서버에 물어봐야 아는 값이고, 눌렀을 때 [AuthError.ONGOING_PARTY_EXISTS] 로 온다).
 */
internal val AccountDeletionWarnings: List<String> = listOf(
    "지금 아이디와 비밀번호로는 다시 로그인할 수 없어요",
    "참여 중인 채팅방에서 모두 나가고, 함께 탄 사람들에게는 「탈퇴한 사용자」로 보여요",
    "저장해 둔 자주 가는 곳이 지워져요",
    "같은 휴대폰 번호로는 ${AuthPolicy.REJOIN_RESTRICTED_DAYS}일 동안 다시 가입할 수 없어요",
)

/**
 * 「탈퇴하기」를 누를 수 있는가 — **비밀번호를 한 글자라도 썼고, 보내는 중이 아니다.**
 *
 * 길이·형식을 보지 않는 건 의도다. 서버는 `@NotBlank` 만 보고 해시를 비교하므로
 * (`UserDeletionRequest`), 앱이 `AuthPolicy.isValidPassword` 로 좁히면 **규칙이 바뀌기 전에 가입한
 * 계정이 자기 비밀번호를 넣고도 버튼을 못 누르는** 상황이 생긴다. 틀림은 서버가 판정한다
 * (400 `USER111` → [AuthError.PASSWORD_MISMATCH]).
 *
 * 공백만 입력한 경우는 막는다 — 서버 `@NotBlank` 가 어차피 400 `INVALID_REQUEST` 를 주는데,
 * 그 문구("입력한 정보를 다시 확인해 주세요")로는 사용자가 무엇을 고칠지 알 수 없다.
 */
internal fun canSubmitAccountDeletion(password: String, submitting: Boolean = false): Boolean {
    if (submitting) return false
    return password.isNotBlank()
}

/** 탈퇴 실패 문구를 어디에 그릴지. */
internal enum class AccountDeleteErrorSlot {
    /** 비밀번호 입력란 아래. 고칠 곳이 입력란에 있을 때만. */
    PASSWORD_FIELD,

    /** 버튼 위 배너. 입력으로 해결되지 않는 실패. */
    BANNER,
}

/**
 * 실패를 입력란과 배너 중 어디로 보낼지 가른다.
 *
 * 기준은 하나다 — **사용자가 이 화면에서 고칠 수 있는가.** 비밀번호 오타만 그렇고
 * (다시 입력하면 된다), 나머지는 전부 이 화면 밖의 일이다: 진행 중인 합승은 합승 화면에서 나와야
 * 풀리고, 네트워크는 기다려야 하고, 세션 만료는 로그인으로 가야 한다.
 *
 * 둘을 섞으면 입력란이 빨간데 고칠 게 없는 상태가 생기고, 사용자는 비밀번호를 계속 다시 친다.
 */
internal fun accountDeleteErrorSlot(error: AuthError): AccountDeleteErrorSlot = when (error) {
    AuthError.PASSWORD_MISMATCH -> AccountDeleteErrorSlot.PASSWORD_FIELD
    else -> AccountDeleteErrorSlot.BANNER
}

/**
 * 37 회원 탈퇴의 상태·제출.
 *
 * 비밀번호를 ViewModel 에 두는 이유: 제출 중 화면 회전으로 `rememberSaveable` 이 복원되면
 * 비밀번호가 **savedInstanceState(디스크 백업 대상) 에 실린다.** 여기 두면 프로세스 수명 안에만 남는다.
 */
class AccountDeleteViewModel(private val repository: AuthRepository) : ViewModel() {

    data class UiState(
        val password: String = "",
        val submitting: Boolean = false,
        /** 비밀번호 입력란 아래 오류. [AccountDeleteErrorSlot.PASSWORD_FIELD] 로 분류된 실패만 온다. */
        val passwordError: String? = null,
        /** 버튼 위 배너. 입력으로 해결되지 않는 실패. */
        val bannerError: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /**
     * 고치는 순간 직전 오류를 지운다 — 빨간 입력란을 들고 타이핑하게 두지 않는다.
     *
     * **입력을 다듬지 않는다.** 닉네임·아이디와 달리 비밀번호는 공백도 유효한 문자일 수 있고
     * (서버 패턴이 특수문자를 요구할 뿐 공백을 금지하지 않는다), trim 하면 사용자가 넣은 것과
     * **다른 값**을 보내 "맞는 비밀번호인데 틀렸다"가 된다.
     */
    fun onPasswordChange(raw: String) {
        _uiState.update { it.copy(password = raw, passwordError = null, bannerError = null) }
    }

    /**
     * `DELETE /api/v1/users/me` 로 비밀번호를 보낸다.
     *
     * 성공하면 Repository 안에서 세션·로컬 기억이 비워지고 `authState` 가 전이한다 —
     * **이 화면은 성공을 따로 알릴 필요가 없다.** NavGraph 의 로그인 전이 감지가 스택을 통째로
     * 비우며 04a 로 보내므로 이 화면도 그때 사라진다. 그래서 `deleted` 같은 성공 플래그를 두지 않고,
     * 성공 경로에서 [UiState.submitting] 을 **켜 둔 채로 끝낸다** — 화면이 사라지기까지의 한두 프레임
     * 동안 버튼이 로딩 상태로 남아, 눌릴 수 있는 버튼이 깜빡 보이는 일이 없다.
     *
     * [onSubmitting] 은 **요청을 보내기 직전에** 불린다. 성공하면 세션이 사라지는데, NavGraph 의
     * 로그인 전이 감지가 "스스로 끝낸 세션인가"를 미리 세워 둔 표시로 판단하기 때문이다 —
     * 응답이 온 뒤에 알리면 늦는다(그 전이가 이미 "세션 만료"로 처리된다).
     * 실패했으면 [onKept] 로 그 표시를 되돌린다.
     */
    fun delete(onSubmitting: () -> Unit = {}, onKept: () -> Unit = {}) {
        val state = _uiState.value
        if (!canSubmitAccountDeletion(state.password, state.submitting)) return
        viewModelScope.launch {
            _uiState.update { it.copy(submitting = true, passwordError = null, bannerError = null) }
            onSubmitting()
            try {
                repository.deleteAccount(state.password)
                // 비밀번호를 상태에 남기지 않는다 — 화면은 곧 사라지지만 ViewModel 이 몇 프레임 더 산다.
                // submitting 은 끄지 않는다(위 KDoc): 이 화면은 성공 후 로딩 상태로 사라진다.
                _uiState.update { it.copy(password = "") }
            } catch (e: Exception) {
                onKept()
                val error = (e as? AuthException)?.error ?: AuthError.UNKNOWN
                val message = e.authUserMessage()
                _uiState.update {
                    when (accountDeleteErrorSlot(error)) {
                        AccountDeleteErrorSlot.PASSWORD_FIELD ->
                            // 틀린 비밀번호는 비워 준다 — 고쳐 치는 것보다 다시 치는 쪽이 빠르다.
                            it.copy(submitting = false, password = "", passwordError = message)
                        AccountDeleteErrorSlot.BANNER ->
                            it.copy(submitting = false, bannerError = message)
                    }
                }
            }
        }
    }

    companion object {
        fun factory(repository: AuthRepository) = viewModelFactory {
            initializer { AccountDeleteViewModel(repository) }
        }
    }
}

/**
 * 37 회원 탈퇴 — `DELETE /api/v1/users/me` 진입점.
 *
 * @param onSessionEndExpected 서버에 탈퇴를 보내기 **직전**. NavGraph 가 "다음 로그아웃 전이는
 *   사용자가 의도한 것"이라고 표시해 두는 자리다 — 안 하면 성공 직후 로그인 화면에
 *   「세션이 만료됐어요」가 뜬다(스스로 탈퇴한 사람에게 할 말이 아니다).
 * @param onSessionKept 탈퇴가 실패해 세션이 그대로 남았다. 위 표시를 되돌린다.
 *
 * 성공 콜백이 **없는 게 맞다** — 세션이 사라지면 NavGraph 의 로그인 전이가 스택을 비우며 04a 로
 * 보내므로 이 화면이 할 이동이 없다. 콜백을 두면 그 전이와 겹쳐 navigate 가 두 번 일어난다.
 */
@Composable
fun AccountDeleteRoute(
    repository: AuthRepository,
    onBack: () -> Unit = {},
    onSessionEndExpected: () -> Unit = {},
    onSessionKept: () -> Unit = {},
) {
    val viewModel: AccountDeleteViewModel =
        viewModel(factory = AccountDeleteViewModel.factory(repository))
    val state by viewModel.uiState.collectAsState()

    AccountDeleteScreen(
        password = state.password,
        submitting = state.submitting,
        passwordError = state.passwordError,
        bannerError = state.bannerError,
        onPasswordChange = viewModel::onPasswordChange,
        onDelete = {
            viewModel.delete(onSubmitting = onSessionEndExpected, onKept = onSessionKept)
        },
        onBack = onBack,
    )
}
