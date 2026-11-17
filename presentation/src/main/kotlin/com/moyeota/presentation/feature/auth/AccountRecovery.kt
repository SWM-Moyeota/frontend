package com.moyeota.presentation.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.moyeota.domain.model.AuthError
import com.moyeota.domain.model.AuthException
import com.moyeota.domain.model.AuthPolicy
import com.moyeota.domain.model.PasswordResetTicket
import com.moyeota.domain.repository.AuthRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// 04b 아이디 찾기 · 04c 비밀번호 재설정이 함께 쓰는 「번호로 인증번호 받기 → 6자리 확인」 흐름.
//
// 백엔드 #314·#331 이 2026-11-14 운영에 올라온 뒤 붙였다. 이 두 화면은 **iOS 가 먼저 만들었고**
// (frontend-ios #47) 문구·규칙을 그쪽에서 옮겨 왔다. 상태 전이는 가입 문자 인증의 [PhoneVerifyState] 를
// 그대로 쓴다: 카운트다운·재발송·만료·시도 초과의 규칙이 같고, 같은 규칙을 두 벌 두면 한쪽만 고쳐진다.
//
// 가입 인증([PhoneVerifyViewModel])과 갈리는 점은 셋이다.
// 1. **선확인이 없다.** 가입은 「이미 가입된 번호」를 미리 막지만, 여기서는 가입된 번호가 정상이다.
// 2. **확인 열쇠가 다르다.** 아이디 찾기는 번호로, 재설정은 서버가 준 접수번호로 확인한다.
// 3. **결과물이 다르다.** 아이디 찾기는 아이디를, 재설정은 일회용 토큰을 [PhoneVerifyState.token] 에 담는다.

/** 인증으로 무엇을 얻으려는가. 부르는 API 와 실패 해석이 여기서 갈린다. */
internal enum class RecoveryPurpose {
    /** 04b — `find-id/send` · `find-id/verify`. 결과는 아이디 전체. */
    FIND_LOGIN_ID,

    /** 04c — `password-reset/send` · `password-reset/verify`. 결과는 재설정 토큰. */
    RESET_PASSWORD,
}

/** 발송 실패 뒤 화면이 따로 길을 내줘야 하는 경우. */
internal enum class RecoveryRequestIssue {
    /** 가입되지 않은 번호(아이디 찾기 전용) — 「가입하기」로 보낸다. */
    NOT_REGISTERED,
}

/** 아이디 찾기에서는 「다른 번호로 가입」이 답이 아니다 — 가입 화면용 문구의 꼬리만 뗀다. */
internal fun recoveryErrorMessage(error: AuthError, fallback: String): String = when (error) {
    AuthError.SMS_DAILY_LIMIT_EXCEEDED ->
        "오늘 받을 수 있는 인증번호를 모두 썼어요(하루 ${AuthPolicy.SMS_DAILY_LIMIT}번). 내일 다시 시도해 주세요"
    else -> fallback
}

/**
 * 재설정의 실패 코드를 [PhoneVerifyState] 가 아는 전이로 옮긴다 — 화면 대응이 같은 것끼리 짝짓는다.
 *
 * - 발송 429 `USER306`: 서버가 문자 쿨다운을 가입 인증과 같은 칸에 세므로 대부분 「1분 안에 또 눌렀다」다.
 *   남은 초(`Retry-After`)는 전송 계층이 올려 주지 않아 쿨다운 최대치로 보수적으로 잡는다.
 * - 확인 400 `USER301`: 접수가 살아 있어 다시 넣을 수 있다 → 불일치와 같은 전이(입력칸만 비운다).
 * - 확인 429 `USER306`: 5번을 넘겼다 → 시도 초과와 같은 전이(입력칸을 닫고 재발송으로 보낸다).
 */
internal fun recoveryTransitionError(error: AuthError, verifying: Boolean): AuthError = when (error) {
    AuthError.PASSWORD_RESET_RATE_LIMITED ->
        if (verifying) AuthError.SMS_TOO_MANY_ATTEMPTS else AuthError.SMS_COOLDOWN
    AuthError.PASSWORD_RESET_CODE_REJECTED -> AuthError.SMS_CODE_MISMATCH
    else -> error
}

/** 재설정 요청 제한(429 `USER306`)을 화면이 어떻게 받을지. 서버는 사유 없이 남은 초만 준다. */
internal sealed interface RecoveryRateLimit {
    /** 곧 풀린다 — 재요청 버튼을 그 초만큼 카운트다운으로 잠근다. */
    data class ShortWait(val seconds: Int, val message: String) : RecoveryRateLimit

    /** 한참 걸린다(하루 문자 횟수·계정당 시간당 제한) — 초를 세는 건 의미가 없어 버튼을 잠그고 대략의 시간만 말한다. */
    data class LongWait(val message: String) : RecoveryRateLimit
}

/**
 * 남은 시간으로 제한의 종류를 가른다.
 *
 * 문자 쿨다운은 60초이고 IP 당 제한도 60초 창이라, **60초 이하면 「방금 요청했다」** 로 본다. 실제로 가장 흔한
 * 경우는 04b 에서 아이디를 찾자마자 같은 번호로 04c 를 여는 것이다 — 두 기능이 같은 문자 대기 칸을 쓴다.
 * 그보다 길면 하루 횟수(최대 24시간)나 계정당 제한(최대 1시간)이다.
 * 서버가 값을 주지 않았으면 쿨다운 최대치로 보수적으로 잡는다.
 */
internal fun recoveryRateLimit(retryAfterSeconds: Int?): RecoveryRateLimit {
    val seconds = retryAfterSeconds
        ?: return RecoveryRateLimit.ShortWait(
            AuthPolicy.SMS_RESEND_COOLDOWN_SECONDS,
            authErrorMessage(AuthError.PASSWORD_RESET_RATE_LIMITED),
        )
    if (seconds <= AuthPolicy.SMS_RESEND_COOLDOWN_SECONDS) {
        return RecoveryRateLimit.ShortWait(
            seconds,
            "방금 이 번호로 인증번호를 요청했어요. 버튼의 남은 시간이 끝나면 다시 받아 주세요",
        )
    }
    val minutes = (seconds + 59) / 60
    val wait = if (minutes < 60) "약 ${minutes}분" else "약 ${(minutes + 59) / 60}시간"
    return RecoveryRateLimit.LongWait("인증번호 요청이 너무 많았어요. $wait 뒤에 다시 시도해 주세요")
}

private const val RECOVERY_TICK_INTERVAL_MS = 500L

internal class RecoveryCodeViewModel(
    private val purpose: RecoveryPurpose,
    private val repository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PhoneVerifyState())
    val state: StateFlow<PhoneVerifyState> = _state.asStateFlow()

    /** 카운트다운 표시에 쓰는 현재 시각. 틱이 돌 때만 갱신된다. */
    private val _nowMillis = MutableStateFlow(System.currentTimeMillis())
    val nowMillis: StateFlow<Long> = _nowMillis.asStateFlow()

    private val _requestIssue = MutableStateFlow<RecoveryRequestIssue?>(null)
    val requestIssue: StateFlow<RecoveryRequestIssue?> = _requestIssue.asStateFlow()

    private var ticker: Job? = null

    /** 재설정에서 인증번호를 요청한 아이디. 번호와 함께 「이 응답이 지금 입력의 것인가」를 가른다. */
    private var boundLoginId: String = ""

    /** 재설정 접수번호. 확인 요청의 열쇠라 화면에 내보내지 않는다. */
    private var requestId: String = ""

    /** 번호가 바뀌면 진행 중인 인증을 버린다 — 인증번호는 번호에 묶여 있다. */
    fun onPhoneDigitsChanged(phoneDigits: String) {
        val before = _state.value
        val next = before.onPhoneDigitsChanged(phoneDigits)
        _state.value = next
        if (next != before) forgetRequest()
    }

    /**
     * 재설정에서 아이디가 바뀌면 진행 중인 인증을 버린다 — 서버 접수는 「아이디 + 번호」 한 쌍에 묶여 있어
     * 옛 아이디로 받은 인증번호로 새 아이디의 비밀번호를 바꿀 수는 없다(바뀌는 건 옛 아이디다).
     */
    fun onLoginIdChanged(loginId: String) {
        val current = _state.value
        if (current.stage == PhoneVerifyStage.Idle && !current.requesting && current.boundPhone.isEmpty()) return
        if (loginId == boundLoginId) return
        _state.value = PhoneVerifyState()
        forgetRequest()
    }

    /** 인증을 통째로 버리고 처음으로 되돌린다. 재설정 토큰이 만료됐을 때 안내와 함께 부른다. */
    fun restart(message: String? = null) {
        _state.update { it.invalidated(message) }
        forgetRequest()
    }

    private fun forgetRequest() {
        _requestIssue.value = null
        requestId = ""
        boundLoginId = ""
    }

    private fun owns(phone: String, loginId: String): Boolean =
        _state.value.boundPhone == phone && boundLoginId == loginId

    fun requestCode(phoneDigits: String, loginId: String = "") {
        val now = System.currentTimeMillis()
        if (!_state.value.canRequestCode(phoneDigits, now)) return
        val phone = AuthPolicy.normalizePhoneNumber(phoneDigits)
        _state.update { it.onRequestStart(phone) }
        _requestIssue.value = null
        boundLoginId = loginId
        viewModelScope.launch {
            try {
                val ticket: PasswordResetTicket? = when (purpose) {
                    RecoveryPurpose.FIND_LOGIN_ID -> {
                        repository.sendFindLoginIdCode(phone)
                        null
                    }
                    RecoveryPurpose.RESET_PASSWORD -> repository.requestPasswordReset(loginId, phone)
                }
                // 요청이 날아간 사이 번호나 아이디를 고쳤으면 이 응답은 남의 것이다 — 버린다.
                if (!owns(phone, loginId)) return@launch
                val sentAt = System.currentTimeMillis()
                requestId = ticket?.requestId.orEmpty()
                _state.update {
                    val sent = it.onCodeSent(sentAt)
                    // 재설정은 서버가 유효 시간·재요청 간격을 직접 알려 준다 — 정책 상수보다 그 값을 믿는다.
                    if (ticket == null) {
                        sent
                    } else {
                        sent.copy(
                            codeExpiresAt = sentAt + ticket.expiresInSeconds * 1000L,
                            resendAllowedAt = sentAt + ticket.resendAfterSeconds * 1000L,
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (!owns(phone, loginId)) return@launch
                val kind = e.recoveryAuthError()
                _requestIssue.value =
                    if (kind == AuthError.PHONE_NOT_REGISTERED) RecoveryRequestIssue.NOT_REGISTERED else null
                val failedAt = System.currentTimeMillis()
                if (kind == AuthError.PASSWORD_RESET_RATE_LIMITED) {
                    // 사유는 없어도 남은 시간은 온다 — 그 값으로 「곧 풀린다」와 「한참 걸린다」를 가른다.
                    when (val limit = recoveryRateLimit((e as? AuthException)?.retryAfterSeconds)) {
                        is RecoveryRateLimit.ShortWait -> _state.update {
                            // 전이는 쿨다운 최대치(60초)로 잡는다 — 서버가 준 실제 남은 시간으로 고쳐 쓴다.
                            it.onRequestFailure(AuthError.SMS_COOLDOWN, limit.message, failedAt)
                                .copy(resendAllowedAt = failedAt + limit.seconds * 1000L)
                        }
                        // 하루 한도와 같은 전이: 버튼을 잠그고 카운트다운을 걸지 않는다. 번호를 고치면 풀린다.
                        is RecoveryRateLimit.LongWait -> _state.update {
                            it.onRequestFailure(AuthError.SMS_DAILY_LIMIT_EXCEEDED, limit.message, failedAt)
                        }
                    }
                } else {
                    _state.update {
                        it.onRequestFailure(
                            recoveryTransitionError(kind, verifying = false),
                            recoveryErrorMessage(kind, e.authUserMessage()),
                            failedAt,
                        )
                    }
                }
            }
            startTicker()
        }
    }

    fun onCodeChanged(raw: String) {
        val next = _state.value.onCodeChanged(raw)
        _state.value = next
        if (AuthPolicy.isValidVerificationCode(next.code)) verify()
    }

    fun verify() {
        val current = _state.value
        if (!current.canSubmitCode(System.currentTimeMillis())) return
        val phone = current.boundPhone
        val code = current.code
        val loginId = boundLoginId
        val request = requestId
        _state.update { it.onVerifyStart() }
        viewModelScope.launch {
            try {
                val result = when (purpose) {
                    RecoveryPurpose.FIND_LOGIN_ID -> repository.findLoginId(phone, code)
                    RecoveryPurpose.RESET_PASSWORD -> repository.verifyPasswordResetCode(request, code)
                }
                if (!owns(phone, loginId)) return@launch
                _state.update { it.onVerified(result) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (!owns(phone, loginId)) return@launch
                val kind = e.recoveryAuthError()
                _state.update {
                    it.onVerifyFailure(
                        recoveryTransitionError(kind, verifying = true),
                        recoveryErrorMessage(kind, e.authUserMessage()),
                    )
                }
            }
        }
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                _nowMillis.value = now
                if (_state.value.codeRemainingSeconds(now) == 0) {
                    _state.update { it.onCodeExpired(authErrorMessage(AuthError.SMS_CODE_EXPIRED)) }
                }
                if (!_state.value.hasRunningCountdown(System.currentTimeMillis())) break
                delay(RECOVERY_TICK_INTERVAL_MS)
            }
        }
    }

    companion object {
        fun factory(purpose: RecoveryPurpose, repository: AuthRepository) = viewModelFactory {
            initializer { RecoveryCodeViewModel(purpose, repository) }
        }
    }
}

private fun Throwable.recoveryAuthError(): AuthError =
    (this as? AuthException)?.error ?: AuthError.UNKNOWN

/** 04c 의 마지막 단계 — 새 비밀번호 저장. 제출 결과만 관리하고 입력값은 화면이 든다(04a 와 같은 분담). */
internal class PasswordResetConfirmViewModel(private val repository: AuthRepository) : ViewModel() {

    data class UiState(
        val submitting: Boolean = false,
        /** 새 비밀번호 입력란 아래에 그릴 오류(규칙 위반·지금 비밀번호와 같음). */
        val passwordError: String? = null,
        /** 확인 입력란 아래에 그릴 오류. */
        val confirmError: String? = null,
        /** 입력란에 붙일 수 없는 실패(네트워크 등). */
        val bannerMessage: String? = null,
        /** 토큰이 무효다 — 화면이 인증 단계로 되돌린다. 문구를 싣고 온다. */
        val reverifyMessage: String? = null,
        val completed: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** 입력을 고치면 그 입력란의 서버 오류는 낡은 말이 된다. */
    fun onInputChanged() {
        _uiState.update {
            if (it.passwordError == null && it.confirmError == null && it.bannerMessage == null) {
                it
            } else {
                it.copy(passwordError = null, confirmError = null, bannerMessage = null)
            }
        }
    }

    /** 화면이 인증 단계로 되돌린 뒤 부른다 — 같은 문구로 두 번 되돌리지 않게 한다. */
    fun onReverifyHandled() {
        _uiState.update { it.copy(reverifyMessage = null) }
    }

    fun confirm(resetToken: String, newPassword: String, newPasswordConfirm: String) {
        val current = _uiState.value
        if (current.submitting || current.completed) return
        _uiState.value = UiState(submitting = true)
        viewModelScope.launch {
            try {
                repository.confirmPasswordReset(resetToken, newPassword, newPasswordConfirm)
                _uiState.value = UiState(completed = true)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                val message = e.authUserMessage()
                _uiState.value = when ((e as? AuthException)?.error) {
                    AuthError.SAME_PASSWORD, AuthError.PASSWORD_POLICY_VIOLATION -> UiState(passwordError = message)
                    AuthError.PASSWORD_CONFIRM_MISMATCH -> UiState(confirmError = message)
                    AuthError.PASSWORD_RESET_TOKEN_INVALID -> UiState(reverifyMessage = message)
                    else -> UiState(bannerMessage = message)
                }
            }
        }
    }

    companion object {
        fun factory(repository: AuthRepository) = viewModelFactory {
            initializer { PasswordResetConfirmViewModel(repository) }
        }
    }
}
