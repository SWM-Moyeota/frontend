package com.moyeota.presentation.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.moyeota.core.designsystem.component.MoyeotaTextField
import com.moyeota.core.designsystem.component.SecondaryButton
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.core.designsystem.theme.MoyeotaType
import com.moyeota.domain.model.AuthError
import com.moyeota.domain.model.AuthException
import com.moyeota.domain.model.AuthPolicy
import com.moyeota.domain.repository.AuthRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 10 프로필 만들기 안에 들어가는 **휴대폰 문자 인증 블록** — 상태·전이 규칙·화면을 한곳에 모았다.
 *
 * 2026-10 서버 변경으로 가입(`POST /api/v1/auth/register`)에 `verificationToken` 이 필수가 됐다
 * ([com.moyeota.domain.model.NewUser.verificationToken]). 토큰이 없으면 가입이 **전부 400** 이라
 * 인증은 가입 경로의 선택 단계가 아니라 통과 조건이다.
 *
 * ### 왜 별도 화면(09 본인 인증)이 아닌가
 * 휴대폰 입력란은 이미 10 안에 있고, 가입 단계 표시가 1/2 · 2/2 다. 인증만을 위해 화면을 하나 더
 * 끼우면 (a) 단계 표시 체계가 2단계에서 3단계로 흔들리고, (b) 같은 번호를 두 화면이 각각 들고
 * 있어야 하며, (c) 인증 후 번호를 고치면 토큰이 무효가 되는데 그 되돌림이 화면 사이를 넘나든다.
 * 번호 입력 바로 아래에서 끝내면 세 문제가 전부 사라진다. Routes 의 09 `IDENTITY_VERIFY` 는
 * 통신사 본인 인증용으로 남겨 둔 미연결 화면이고, 이 블록이 그것을 대체하지는 않는다.
 *
 * ### 서버가 알려주지 않는 것 — 앱 타이머의 한계
 * 서버는 **남은 쿨다운 초·남은 발송 횟수·남은 입력 시도 횟수를 응답에 담지 않는다**
 * (`Retry-After` 헤더도 없다). 429 는 "넘었다"만 말한다. 그래서 이 블록의 카운트다운은 전부
 * **앱이 자기 발송 시점부터 자체로 세는 값**이고, 다음 한계가 따라온다:
 * - **앱을 껐다 켜면(프로세스 종료) 쿨다운을 알 수 없다.** ViewModel 과 함께 발송 시점이 사라지므로
 *   「인증번호 받기」가 곧바로 활성으로 보인다. 누르면 서버가 429 `SMS_COOLDOWN` 으로 거절하고,
 *   그때 비로소 안내할 수 있다 — 이 경로에서만 쿨다운 문구가 "먼저 뜨지 않고 나중에 뜬다".
 *   그 429 를 받으면 보수적으로 최대치(1분)만큼 쿨다운을 다시 잡는다(실제 남은 시간보다 길 수 있다).
 * - 일일 한도(5회)도 같은 이유로 **미리 막을 수 없다.** 한 번 429 를 받은 뒤에야 버튼을 잠근다.
 * - 입력 시도 횟수(5회)는 서버가 세고, 앱은 429 `SMS_TOO_MANY_ATTEMPTS` 로만 알게 된다.
 *
 * 이 한계를 없애려면 서버가 `send` 204 응답에 남은 초/횟수를 싣거나 `Retry-After` 를 줘야 한다
 * (백엔드 요청 후보로 보고서에 적었다).
 *
 * ### 상태 전이 한눈에
 * ```
 * Idle ──「인증번호 받기」 send 204──▶ CodeSent ──verify 200──▶ Verified (토큰 보관, 번호 잠금)
 *  ▲                                    │                          │
 *  │                                    │ 3분 만료·5회 초과 → 재발송 │
 *  └──── 번호가 바뀌면 전부 무효 ◀───────┴──────────────────────────┘
 * ```
 * 번호가 바뀌면 되돌리는 이유는 서버가 토큰을 **번호 해시에 묶어** 보관하기 때문이다
 * (`checkVerified` 가 가입 번호의 해시와 비교한다) — 인증한 번호와 다른 번호로 가입하면 400 이다.
 */
// 공개 타입인 이유: 공개 컴포저블 [ProfileSetupScreen] 의 파라미터로 나간다(NicknameCheckState 와 같은 자리).
// 전이 함수·ViewModel·블록 UI 는 모듈 안에서만 쓰이므로 internal 로 둔다.
enum class PhoneVerifyStage {
    /** 아직 인증번호를 받지 않았다(또는 받은 것이 무효가 됐다). 「인증번호 받기」만 보인다. */
    Idle,

    /** 발송 성공 — 6자리 입력칸과 만료 타이머가 열려 있다. */
    CodeSent,

    /** 인증 완료 — 토큰을 들고 있고 번호 입력란은 잠긴다. */
    Verified,
}

/** 타이머가 없다는 뜻의 기한 값. 0 을 쓰는 이유는 "지난 시각"과 같은 취급으로 끝나기 때문이다. */
internal const val NO_DEADLINE = 0L

/** 카운트다운 갱신 간격. 1초 표시인데 500ms 로 도는 건 경계에서 최대 1초 늦게 보이는 걸 줄이기 위함이다. */
private const val TICK_INTERVAL_MS = 500L

/**
 * 문자 인증 블록의 전체 상태. **불변 데이터 + 순수 전이 함수**로만 바뀐다 —
 * 전이 규칙(어떤 에러가 입력칸을 닫는지, 번호가 바뀌면 무엇이 무효인지)이 화면 코드에 흩어지면
 * 단위 테스트가 불가능해지고, 이 블록의 실패는 "가입이 400 으로 안 된다"로만 드러나서 늦게 발견된다.
 *
 * @property boundPhone 이 상태가 묶여 있는 번호(하이픈 없는 숫자열). 발송 시도·인증 성공 모두
 *   이 번호 기준이고, 입력란이 **다른 번호**로 바뀌면 상태 전체가 무효가 된다
 *   ([onPhoneDigitsChanged]). 요청이 날아간 뒤 응답이 돌아오는 사이 번호가 바뀌었는지를
 *   판정하는 기준이기도 하다.
 * @property token 인증 성공으로 받은 `verificationToken`. 가입 제출까지 이 값을 들고 있어야 한다 —
 *   같은 인증번호로 토큰을 다시 받을 길이 없다(서버가 성공 시 인증번호를 지운다).
 * @property codeExpiresAt 인증번호 만료 시각(epoch millis). [NO_DEADLINE] 이면 쓸 수 있는 인증번호가 없다.
 * @property resendAllowedAt 재전송이 가능해지는 시각(epoch millis).
 * @property phoneMessage 번호 입력란 아래에 띄울 문구. 발송 단계의 실패(중복·형식·한도·장애)가 여기 간다.
 * @property codeMessage 인증번호 입력칸 아래에 띄울 문구. 확인 단계의 실패가 여기 간다.
 * @property alreadyRegistered 발송이 409 `PHONE_NUMBER_DUPLICATED` 로 끊긴 상태. 인증번호 입력칸을
 *   열지 않고 로그인으로 가는 길을 보여준다 — 번호를 고치면 풀린다.
 * @property dailyLimitReached 하루 발송 한도(5회)를 넘겼다. 재시도를 권하지 않고 버튼을 잠근다.
 */
data class PhoneVerifyState(
    val stage: PhoneVerifyStage = PhoneVerifyStage.Idle,
    val boundPhone: String = "",
    val token: String = "",
    val code: String = "",
    val codeExpiresAt: Long = NO_DEADLINE,
    val resendAllowedAt: Long = NO_DEADLINE,
    val requesting: Boolean = false,
    val verifying: Boolean = false,
    val phoneMessage: String? = null,
    val codeMessage: String? = null,
    val alreadyRegistered: Boolean = false,
    val dailyLimitReached: Boolean = false,
)

/**
 * 10 의 휴대폰 입력란이 받는 번호 — **010 + 숫자 8자리**.
 *
 * 도메인 [AuthPolicy.PHONE_PATTERN] 은 011·016~019 와 10자리도 허용하지만, 이 화면의 입력 마스크가
 * 11자리 숫자만 만들고 기존 안내 문구도 010 기준이라 더 좁은 쪽으로 맞췄다. 블록과 화면이 같은
 * 함수를 쓰는 게 중요하다 — 「인증번호 받기」가 활성인데 「다음」은 번호가 틀렸다고 막는 식의
 * 어긋남을 구조적으로 없앤다.
 */
internal fun isCompletePhoneDigits(digits: String): Boolean =
    digits.length == 11 && digits.startsWith("010")

/** 남은 초(올림). 기한이 없거나 지났으면 0 — "1초 남음"이 0 으로 보이지 않도록 올림한다. */
internal fun secondsUntil(deadlineMillis: Long, nowMillis: Long): Int {
    if (deadlineMillis <= nowMillis) return 0
    return ((deadlineMillis - nowMillis + 999L) / 1000L).toInt()
}

/**
 * 카운트다운 표시 문구 `m:ss`. 최대 3분이라 분은 한 자리로 둔다.
 * 음수가 들어와도 0:00 으로 접는다 — 시계가 뒤로 간(시간대 변경·NTP 보정) 경우의 방어다.
 */
internal fun formatRemainingSeconds(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    return "%d:%02d".format(safe / 60, safe % 60)
}

/** 인증번호가 유효한 남은 시간(초). */
internal fun PhoneVerifyState.codeRemainingSeconds(nowMillis: Long): Int =
    secondsUntil(codeExpiresAt, nowMillis)

/** 재전송까지 남은 시간(초). */
internal fun PhoneVerifyState.resendRemainingSeconds(nowMillis: Long): Int =
    secondsUntil(resendAllowedAt, nowMillis)

/** 그려야 할 카운트다운이 남아 있는가 — 틱 루프를 멈출 시점 판단에 쓴다. */
internal fun PhoneVerifyState.hasRunningCountdown(nowMillis: Long): Boolean =
    codeRemainingSeconds(nowMillis) > 0 || resendRemainingSeconds(nowMillis) > 0

/**
 * 「인증번호 받기 / 다시 받기」를 누를 수 있는가.
 *
 * - 이미 인증을 끝냈으면 더 받을 이유가 없다(번호를 바꾸려면 「번호 변경」을 먼저 눌러야 한다).
 * - 쿨다운 중에는 눌러도 429 다 — 앱이 먼저 막는다.
 * - 이미 가입된 번호·일일 한도는 **그 번호로는 끝난 상태**라 재시도를 열어 두지 않는다.
 *   둘 다 번호를 고치면 풀린다([onPhoneDigitsChanged] 가 상태를 비운다).
 */
internal fun PhoneVerifyState.canRequestCode(phoneDigits: String, nowMillis: Long): Boolean {
    if (stage == PhoneVerifyStage.Verified) return false
    if (requesting) return false
    if (alreadyRegistered || dailyLimitReached) return false
    if (resendRemainingSeconds(nowMillis) > 0) return false
    return isCompletePhoneDigits(phoneDigits)
}

/** 「확인」을 누를(또는 6자리 자동 제출을 걸) 수 있는가. */
internal fun PhoneVerifyState.canSubmitCode(nowMillis: Long): Boolean =
    stage == PhoneVerifyStage.CodeSent &&
        !verifying &&
        AuthPolicy.isValidVerificationCode(code) &&
        codeRemainingSeconds(nowMillis) > 0

/** 발송 버튼 문구. 상태마다 다른 말을 해야 해서 순수 함수로 빼 두고 테스트한다. */
internal fun PhoneVerifyState.requestButtonLabel(nowMillis: Long): String {
    if (requesting) return "인증번호 보내는 중…"
    val remaining = resendRemainingSeconds(nowMillis)
    if (remaining > 0) return "${remaining}초 후 다시 받기"
    val sentBefore = stage == PhoneVerifyStage.CodeSent || (boundPhone.isNotEmpty() && codeMessage != null)
    return if (sentBefore) "인증번호 다시 받기" else "인증번호 받기"
}

/**
 * 번호 입력란이 바뀌었다 → **다른 번호가 됐으면 인증 상태 전체를 버린다.**
 *
 * 서버가 토큰을 번호에 묶으므로(`sms:verified:<token>` 의 phoneHash) 번호를 고친 뒤 옛 토큰으로
 * 가입하면 400 `USER207` 이다. 그 400 을 가입 제출에서 맞게 두는 대신 여기서 즉시 되돌린다 —
 * 사용자는 "번호를 고쳤으니 인증을 다시 해야 한다"를 입력하는 순간에 알게 된다.
 *
 * 같은 번호면 상태를 그대로 둔다(하이픈 표기 차이는 정규화로 흡수된다).
 */
internal fun PhoneVerifyState.onPhoneDigitsChanged(phoneDigits: String): PhoneVerifyState {
    val normalized = AuthPolicy.normalizePhoneNumber(phoneDigits)
    if (normalized == boundPhone) return this
    return PhoneVerifyState()
}

/**
 * 인증 상태를 통째로 버린다. 두 곳이 쓴다 —
 * 인증 완료 후의 「번호 변경」, 그리고 가입이 [AuthError.PHONE_NOT_VERIFIED] 로 되돌려 보낸 경우.
 *
 * @param message 버린 이유를 번호 입력란 아래에 남길 문구. 「번호 변경」처럼 사용자가 스스로 한
 *   행동이면 null — 자기가 누른 버튼의 결과를 오류처럼 말할 이유가 없다.
 */
internal fun PhoneVerifyState.invalidated(message: String? = null): PhoneVerifyState =
    PhoneVerifyState(phoneMessage = message)

/** 발송 요청 시작 — 이 번호에 상태를 묶고 진행 표시를 켠다. 직전 안내 문구는 지운다. */
internal fun PhoneVerifyState.onRequestStart(phone: String): PhoneVerifyState = copy(
    boundPhone = phone,
    requesting = true,
    phoneMessage = null,
    codeMessage = null,
    alreadyRegistered = false,
)

/**
 * 발송 성공(204) — 인증번호 3분 만료와 재전송 1분 쿨다운이 **같이** 시작된다.
 *
 * 재발송이면 서버가 오입력 횟수를 0으로 되돌리므로(`saveCode` 가 ATTEMPT 키를 지운다)
 * 앱도 이전 입력값·문구를 남기지 않는다.
 */
internal fun PhoneVerifyState.onCodeSent(nowMillis: Long): PhoneVerifyState = copy(
    stage = PhoneVerifyStage.CodeSent,
    code = "",
    token = "",
    codeExpiresAt = nowMillis + AuthPolicy.SMS_CODE_TTL_SECONDS * 1000L,
    resendAllowedAt = nowMillis + AuthPolicy.SMS_RESEND_COOLDOWN_SECONDS * 1000L,
    requesting = false,
    verifying = false,
    phoneMessage = null,
    codeMessage = null,
)

/**
 * 발송 실패. **에러마다 다른 상태로 간다** — 하나로 뭉치면 사용자가 할 일이 안 보인다.
 *
 * @param message 보여줄 문구([authErrorMessage] 가 만든 값). 문구 표를 이 파일이 또 들고 있지 않도록
 *   호출부가 넘긴다 — 테스트도 문구에 의존하지 않는다.
 */
internal fun PhoneVerifyState.onRequestFailure(
    error: AuthError,
    message: String,
    nowMillis: Long,
): PhoneVerifyState = when (error) {
    // 이미 가입된 번호 — 인증번호를 기다리게 하면 오지 않는 문자를 기다린다. 입력칸을 열지 않고 로그인으로 보낸다.
    AuthError.PHONE_NUMBER_DUPLICATED -> copy(
        stage = PhoneVerifyStage.Idle,
        code = "",
        token = "",
        codeExpiresAt = NO_DEADLINE,
        resendAllowedAt = NO_DEADLINE,
        requesting = false,
        verifying = false,
        phoneMessage = message,
        codeMessage = null,
        alreadyRegistered = true,
    )
    // 쿨다운 중이었다(앱 재시작으로 발송 시점을 잃은 경우가 대표적이다). 서버가 남은 초를 주지 않으니
    // 최대치만큼 보수적으로 잡는다 — 짧게 잡으면 또 눌러 또 429 를 받는다.
    AuthError.SMS_COOLDOWN -> copy(
        requesting = false,
        phoneMessage = message,
        resendAllowedAt = maxOf(
            resendAllowedAt,
            nowMillis + AuthPolicy.SMS_RESEND_COOLDOWN_SECONDS * 1000L,
        ),
    )
    // 하루 한도 — 기다려도 안 된다. 버튼을 잠그고 재시도를 권하지 않는다.
    AuthError.SMS_DAILY_LIMIT_EXCEEDED -> copy(
        requesting = false,
        phoneMessage = message,
        dailyLimitReached = true,
    )
    // 문자 서비스 장애·네트워크·형식 오류 — 상태는 그대로 두고 다시 누를 수 있게 한다.
    // (발송 실패 쪽은 서버가 쿨다운을 풀어 주므로 1분을 기다릴 필요도 없다)
    else -> copy(requesting = false, phoneMessage = message)
}

/** 인증번호 입력. 숫자 6자리만 받고, 고치는 순간 직전 오류 문구를 지운다. */
internal fun PhoneVerifyState.onCodeChanged(raw: String): PhoneVerifyState {
    if (stage != PhoneVerifyStage.CodeSent) return this
    val digits = raw.filter { it.isDigit() }.take(AuthPolicy.VERIFICATION_CODE_LENGTH)
    if (digits == code) return this
    return copy(code = digits, codeMessage = null)
}

/** 확인 요청 시작. */
internal fun PhoneVerifyState.onVerifyStart(): PhoneVerifyState =
    copy(verifying = true, codeMessage = null)

/**
 * 인증 성공 — 토큰을 들고 번호를 잠근다.
 *
 * 타이머는 둘 다 멈춘다: 인증번호는 서버에서 이미 지워졌고(재사용 불가), 재전송할 이유도 없다.
 * 이제부터 시간 제약은 **토큰 10분**인데, 그건 앱이 세는 대신 가입 실패
 * ([AuthError.PHONE_NOT_VERIFIED])로 알게 된다 — 남은 시간을 띄우면 가입 폼 위에 초읽기를
 * 얹는 셈이라 서두르게만 만들고, 10분은 남은 입력(아이디·비밀번호·이메일)에 충분한 시간이다.
 */
internal fun PhoneVerifyState.onVerified(verificationToken: String): PhoneVerifyState = copy(
    stage = PhoneVerifyStage.Verified,
    token = verificationToken,
    verifying = false,
    codeExpiresAt = NO_DEADLINE,
    resendAllowedAt = NO_DEADLINE,
    phoneMessage = null,
    codeMessage = null,
)

/**
 * 확인 실패. 여기서 갈라지는 핵심은 **서버에 인증번호가 아직 남아 있는가**다.
 *
 * - [AuthError.SMS_CODE_MISMATCH]: 남아 있다 → 입력칸만 비우고 다시 받는다(서버가 5회까지 센다).
 * - [AuthError.SMS_CODE_EXPIRED] · [AuthError.SMS_TOO_MANY_ATTEMPTS]: 서버가 지웠다 → 더 입력해도
 *   같은 에러뿐이다. 입력칸을 닫고 재발송으로 보낸다. 쿨다운은 건드리지 않는다 — 5회 초과는
 *   발송 1분 안에도 일어날 수 있어서 그때는 기다려야 한다.
 * - [AuthError.INVALID_PHONE_NUMBER]: 고칠 곳이 인증번호가 아니라 번호 입력란이다 → 1단계로 되돌린다.
 * - 그 밖(네트워크·장애): 입력값을 남겨 둔다. 「확인」으로 그대로 재시도할 수 있어야 한다.
 */
internal fun PhoneVerifyState.onVerifyFailure(error: AuthError, message: String): PhoneVerifyState =
    when (error) {
        AuthError.SMS_CODE_MISMATCH -> copy(verifying = false, code = "", codeMessage = message)
        AuthError.SMS_CODE_EXPIRED, AuthError.SMS_TOO_MANY_ATTEMPTS -> copy(
            verifying = false,
            code = "",
            codeExpiresAt = NO_DEADLINE,
            codeMessage = message,
        )
        AuthError.INVALID_PHONE_NUMBER -> copy(
            stage = PhoneVerifyStage.Idle,
            code = "",
            codeExpiresAt = NO_DEADLINE,
            resendAllowedAt = NO_DEADLINE,
            verifying = false,
            phoneMessage = message,
            codeMessage = null,
        )
        else -> copy(verifying = false, codeMessage = message)
    }

/**
 * 3분이 지나 인증번호가 스스로 만료됐다 — 서버에 묻지 않아도 아는 사실이라 미리 입력칸을 닫는다.
 *
 * 닫지 않으면 사용자가 만료된 번호를 다 입력하고 「확인」을 눌러 400 을 받은 뒤에야 알게 된다.
 * [codeExpiresAt] 을 [NO_DEADLINE] 으로 비우므로 같은 전이가 두 번 적용되지 않는다.
 */
internal fun PhoneVerifyState.onCodeExpired(message: String): PhoneVerifyState {
    if (stage != PhoneVerifyStage.CodeSent || codeExpiresAt == NO_DEADLINE) return this
    return copy(code = "", codeExpiresAt = NO_DEADLINE, verifying = false, codeMessage = message)
}

/**
 * 가입 실패가 **문자 인증을 다시 받게 만드는가**. [AuthError.PHONE_NOT_VERIFIED] 하나뿐이다.
 *
 * 아이디·닉네임·전화번호 중복(409)으로 가입이 실패해도 토큰은 **10분 동안 그대로 살아 있다** —
 * 서버가 토큰을 지우는 시점이 세 테이블 저장을 다 끝낸 뒤(`consume`)이기 때문이다.
 * 그 경우에 인증을 다시 시키면, 고칠 곳은 닉네임 한 글자인데 문자부터 다시 받게 하는 셈이다.
 */
internal fun signupErrorNeedsReverify(error: AuthError?): Boolean =
    error == AuthError.PHONE_NOT_VERIFIED

/**
 * 문자 인증의 서버 호출(발송·확인)만 담당하는 ViewModel.
 *
 * 10 의 NavBackStackEntry 에 묶이므로 **12 매너 서약에 올라가 있는 동안에도 살아 있다** —
 * 닉네임 중복(409)으로 10 으로 되돌아왔을 때 토큰과 쿨다운이 그대로 남아 있어야 하기 때문이다
 * (인증을 다시 시킬 이유는 [signupErrorNeedsReverify] 뿐이다). 카운트다운도 같은 이유로
 * 컴포지션이 아니라 여기서 돈다: 화면을 떠난 1분이 쿨다운에서 빠지지 않으면 거짓 대기가 된다.
 *
 * 남은 시간은 **기한(epoch millis)과 현재 시각의 차이**로 계산한다. 1초마다 카운터를 깎는 방식은
 * 기기가 절전(Doze)으로 틱을 거르면 그만큼 뒤처져 쿨다운이 실제보다 길게 보인다.
 */
internal class PhoneVerifyViewModel(private val repository: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(PhoneVerifyState())
    val state: StateFlow<PhoneVerifyState> = _state.asStateFlow()

    /** 화면이 카운트다운을 그릴 기준 시각. 틱마다 갱신되고, 셀 것이 없으면 멈춘다. */
    private val _nowMillis = MutableStateFlow(System.currentTimeMillis())
    val nowMillis: StateFlow<Long> = _nowMillis.asStateFlow()

    private var ticker: Job? = null

    /** 마지막으로 처리한 재인증 신호 번호. 같은 신호를 두 번 적용하지 않기 위한 기억이다. */
    private var handledReverifyRequest = 0

    /** 번호 입력란이 바뀔 때마다. 다른 번호면 인증이 무효가 된다. */
    fun onPhoneDigitsChanged(phoneDigits: String) {
        _state.update { it.onPhoneDigitsChanged(phoneDigits) }
    }

    /** 인증 완료 후 「번호 변경」 — 사용자가 스스로 무효화를 택한 경로라 안내 문구를 남기지 않는다. */
    fun onPhoneEditRequested() {
        _state.update { it.invalidated() }
    }

    /**
     * 가입이 [AuthError.PHONE_NOT_VERIFIED] 로 떨어져 되돌아온 경우 — 들고 있던 토큰은 서버에서
     * 이미 쓸 수 없는 값(만료·소진·번호 불일치)이다. 버리고 1단계로 돌아가며, 왜 되돌아왔는지를
     * 번호 입력란 아래에 남긴다.
     *
     * @param requestId 되돌림 신호의 일련번호(12 → 10 으로 보낸 횟수). **이미 처리한 번호는 무시한다.**
     *   이 방어가 없으면 10 이 다시 컴포지션에 들어올 때마다 같은 신호가 또 적용된다 —
     *   10 의 컴포지션은 12 로 올라가는 동안 버려지므로, 사용자가 12 에서 **뒤로** 눌러 돌아온
     *   정상 경로에서도 멀쩡한 인증이 날아간다. ViewModel 은 그 사이에도 살아 있어 기억할 수 있다.
     */
    fun onSignupRejectedPhone(requestId: Int) {
        if (requestId <= handledReverifyRequest) return
        handledReverifyRequest = requestId
        _state.update { it.invalidated(authErrorMessage(AuthError.PHONE_NOT_VERIFIED)) }
    }

    /** 인증번호 발송. 누를 수 없는 상태면 아무 일도 하지 않는다(화면도 버튼을 비활성으로 그린다). */
    fun requestCode(phoneDigits: String) {
        val now = System.currentTimeMillis()
        if (!_state.value.canRequestCode(phoneDigits, now)) return
        val phone = AuthPolicy.normalizePhoneNumber(phoneDigits)
        _state.update { it.onRequestStart(phone) }
        viewModelScope.launch {
            try {
                repository.sendPhoneVerificationCode(phone)
                // 요청이 날아간 사이 사용자가 번호를 고쳤으면 이 응답은 남의 것이다 — 버린다.
                if (_state.value.boundPhone != phone) return@launch
                _state.update { it.onCodeSent(System.currentTimeMillis()) }
            } catch (e: Exception) {
                if (_state.value.boundPhone != phone) return@launch
                _state.update {
                    it.onRequestFailure(e.authErrorOrUnknown(), e.authUserMessage(), System.currentTimeMillis())
                }
            }
            startTicker()
        }
    }

    /** 인증번호 입력. 6자리가 채워지면 **버튼을 누르지 않아도** 확인으로 넘어간다. */
    fun onCodeChanged(raw: String) {
        val next = _state.value.onCodeChanged(raw)
        _state.value = next
        if (AuthPolicy.isValidVerificationCode(next.code)) verify()
    }

    /** 인증번호 확인. 자동 제출과 「확인」 버튼이 같은 경로를 쓴다. */
    fun verify() {
        val current = _state.value
        if (!current.canSubmitCode(System.currentTimeMillis())) return
        val phone = current.boundPhone
        val code = current.code
        _state.update { it.onVerifyStart() }
        viewModelScope.launch {
            try {
                val token = repository.verifyPhoneCode(phone, code)
                if (_state.value.boundPhone != phone) return@launch
                _state.update { it.onVerified(token) }
            } catch (e: Exception) {
                if (_state.value.boundPhone != phone) return@launch
                _state.update { it.onVerifyFailure(e.authErrorOrUnknown(), e.authUserMessage()) }
            }
        }
    }

    /**
     * 카운트다운 루프. 하나만 돌고, 셀 것이 없어지면 스스로 멈춘다 —
     * 화면이 떠 있는 동안 1초마다 빈 루프를 도는 것을 피한다.
     */
    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                _nowMillis.value = now
                val current = _state.value
                if (current.codeRemainingSeconds(now) == 0) {
                    // 만료는 전이로 처리한다 — 입력칸을 닫고 재발송으로 유도해야 하기 때문이다.
                    _state.update { it.onCodeExpired(authErrorMessage(AuthError.SMS_CODE_EXPIRED)) }
                }
                if (!_state.value.hasRunningCountdown(System.currentTimeMillis())) break
                delay(TICK_INTERVAL_MS)
            }
        }
    }

    companion object {
        fun factory(repository: AuthRepository) = viewModelFactory {
            initializer { PhoneVerifyViewModel(repository) }
        }
    }
}

/** data 계층이 모든 실패를 [AuthException] 으로 올려 주지만, 그렇지 않은 예외도 UNKNOWN 으로 받는다. */
private fun Throwable.authErrorOrUnknown(): AuthError =
    (this as? AuthException)?.error ?: AuthError.UNKNOWN

/**
 * 휴대폰 번호 입력란 **바로 아래**에 붙는 인증 블록.
 *
 * 번호 입력란 자체는 10 화면이 들고 있다([ProfileSetupScreen]) — 인증 상태에 따라 잠기고
 * 실패 문구가 그 필드의 errorText 로 내려가야 해서, 필드를 블록 안으로 가져오면 10 의 입력 묶음
 * (이름·생년월일·성별·휴대폰)이 둘로 쪼개진다.
 *
 * 인증번호 입력에 6칸 박스(07 인증 코드 화면의 모양)를 쓰지 않은 건 의도다: 이 화면은 이미
 * 입력 필드가 8개라서 64dp 박스 6개를 세우면 번호 입력과 다음 절(로그인 정보) 사이가 한 화면을
 * 넘어간다. 같은 디자인 시스템 안에서 더 조용한 [MoyeotaTextField] 로 맞췄다.
 *
 * @param state 인증 상태. 화면은 판단하지 않고 그리기만 한다 — 전이 규칙은 전부 순수 함수다.
 * @param nowMillis 카운트다운 기준 시각. [PhoneVerifyViewModel.nowMillis] 가 틱마다 올려 준다.
 */
@Composable
internal fun PhoneVerificationBlock(
    state: PhoneVerifyState,
    nowMillis: Long,
    phoneDigits: String,
    onRequestCode: () -> Unit,
    onCodeChange: (String) -> Unit,
    onSubmitCode: () -> Unit,
    onPhoneEditRequested: () -> Unit,
    onGoToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (state.stage == PhoneVerifyStage.Verified) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "휴대폰 인증 완료",
                    style = MoyeotaType.CaptionMd,
                    fontWeight = FontWeight.Bold,
                    color = MoyeotaColor.Success500,
                    modifier = Modifier.weight(1f),
                )
                // 번호를 고치려면 인증을 버려야 한다(서버가 토큰을 번호에 묶는다) — 실수로 고쳐지는 대신
                // 명시적으로 누르게 하고, 누르면 1단계로 되돌아간다.
                Text(
                    text = "번호 변경",
                    style = MoyeotaType.CaptionMd,
                    fontWeight = FontWeight.Bold,
                    color = MoyeotaColor.Primary500,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onPhoneEditRequested,
                    ),
                )
            }
            return@Column
        }

        if (state.alreadyRegistered) {
            // 문구는 번호 입력란 아래(errorText)에 이미 떠 있다 — 여기서는 갈 길만 준다.
            Spacer(Modifier.height(10.dp))
            Text(
                text = "로그인하러 가기",
                style = MoyeotaType.CaptionMd,
                fontWeight = FontWeight.Bold,
                color = MoyeotaColor.Primary500,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onGoToLogin,
                ),
            )
        }

        val canRequest = state.canRequestCode(phoneDigits, nowMillis)
        if (state.stage != PhoneVerifyStage.CodeSent) {
            Spacer(Modifier.height(12.dp))
            SecondaryButton(
                text = state.requestButtonLabel(nowMillis),
                onClick = onRequestCode,
                enabled = canRequest,
            )
            return@Column
        }

        val codeRemaining = state.codeRemainingSeconds(nowMillis)
        Spacer(Modifier.height(12.dp))
        MoyeotaTextField(
            value = state.code,
            onValueChange = onCodeChange,
            label = "인증번호",
            placeholder = "문자로 받은 6자리",
            errorText = state.codeMessage,
            helperText = if (state.verifying) "인증번호를 확인하고 있어요" else "6자리를 입력하면 자동으로 확인해요",
            // 만료된 인증번호를 다 입력하고 「확인」을 눌러 400 을 받는 길을 막는다
            enabled = codeRemaining > 0 && !state.verifying,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        )

        // 「남은 시간 2:43」 + 「다시 받기」 — 07 인증 코드 화면이 쓰던 배치 그대로다.
        // 버튼 두 개를 나란히 두지 않은 이유: 반쪽 너비에 "인증번호 다시 받기"가 들어가지 않는다.
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                // 만료되면 남은 시간 대신 입력칸 아래 오류 문구가 상황을 말한다
                text = if (codeRemaining > 0) "남은 시간 ${formatRemainingSeconds(codeRemaining)}" else "",
                style = MoyeotaType.CaptionMd,
                fontWeight = FontWeight.Bold,
                color = MoyeotaColor.Waiting600,
            )
            Text(
                text = state.requestButtonLabel(nowMillis),
                style = MoyeotaType.CaptionMd,
                fontWeight = FontWeight.Bold,
                color = if (canRequest) MoyeotaColor.Primary500 else MoyeotaColor.TextAsh,
                modifier = if (canRequest) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onRequestCode,
                    )
                } else {
                    Modifier
                },
            )
        }

        // 6자리를 채우면 자동으로 확인하므로, 이 버튼은 **네트워크 실패처럼 같은 입력으로 다시
        // 눌러야 하는** 경우를 위해 남겨 둔다.
        Spacer(Modifier.height(12.dp))
        SecondaryButton(
            text = if (state.verifying) "확인 중…" else "확인",
            onClick = onSubmitCode,
            enabled = state.canSubmitCode(nowMillis),
        )
    }
}
