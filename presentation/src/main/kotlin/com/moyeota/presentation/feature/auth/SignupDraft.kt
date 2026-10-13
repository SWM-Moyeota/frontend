package com.moyeota.presentation.feature.auth

import com.moyeota.domain.model.AuthError
import com.moyeota.domain.model.AuthException
import com.moyeota.domain.model.AuthPolicy
import com.moyeota.domain.model.Gender
import com.moyeota.domain.model.NewUser
import java.time.LocalDate

/**
 * 가입 플로우(10 프로필 만들기 → 12 매너 서약)가 모으는 값.
 *
 * 서버 `POST /api/v1/auth/register` 가 요구하는 9개 필드가 그대로 이 클래스의 필드다 — 계정 유형이나
 * 학교·재직 인증 결과는 API 에 자리가 없어 담지 않는다(그 화면들이 가입 경로에서 빠진 이유이기도 하다).
 *
 * | 필드 | 수집 화면 |
 * |---|---|
 * | [nickname] | 10 프로필 만들기 (1/2, 「프로필」 절) |
 * | [name] · [birthDate] · [gender] · [phoneNumber] | 10 프로필 만들기 (1/2, 「기본 정보」 절) |
 * | [verificationToken] | 10 프로필 만들기 (1/2, 휴대폰 번호 아래 인증 블록) |
 * | [loginId] · [password] · [email] | 10 프로필 만들기 (1/2, 「로그인 정보」 절) |
 *
 * 09 본인 인증이 앞 절반을 받던 시절의 흔적으로 필드가 화면을 넘나드는 구조가 남아 있다.
 * 지금은 10 이 한 번에 다 채우지만, 실제 본인 인증이 붙으면 다시 나뉠 자리라 그대로 뒀다.
 *
 * 제출은 12 매너 서약의 「동의하고 가입 완료」 한 곳에서만 일어난다.
 */
data class SignupDraft(
    val email: String = "",
    /**
     * 서버 가입 필수 필드. 동승자에게 보이는 유일한 이름이고 실명([name])은 공개되지 않는다.
     * 규칙은 [NicknamePolicy] — 서버 도메인 VO 와 같은 정규식이다.
     */
    val nickname: String = "",
    val name: String = "",
    val phoneNumber: String = "",
    val birthDate: LocalDate? = null,
    val gender: Gender? = null,
    val loginId: String = "",
    val password: String = "",
    /**
     * 문자 인증을 통과했다는 증표. 10 프로필 만들기의 인증 블록
     * ([PhoneVerifyState.token])이 채우고, 12 매너 서약의 제출이 그대로 서버로 싣는다.
     *
     * **[phoneNumber] 와 한 쌍이다** — 서버가 토큰에 묶인 번호와 가입 번호의 해시를 비교하므로
     * 번호만 고치면 가입이 400 `USER207` 로 떨어진다. 그래서 10 은 번호가 바뀌는 순간 토큰을 버리고
     * ([onPhoneDigitsChanged]) 인증을 다시 받게 한다 — 이 초안에 토큰만 남아 있는 상태는 생기지 않는다.
     *
     * 수명 10분, **가입 성공 시에만** 소진된다. 아이디·닉네임 중복(409)으로 가입이 실패했다면
     * 이 값을 그대로 두고 입력만 고쳐 재시도하는 게 맞다(상세는 [NewUser.verificationToken] KDoc).
     */
    val verificationToken: String = "",
) {
    /**
     * 서버 요청 모델로 변환. 아직 못 받은 값이 있으면 null 이다.
     *
     * null 은 "플로우를 건너뛰어 필수 값이 비었다"는 뜻 — 화면 순서가 바뀌었을 때
     * 400 을 맞고 나서야 알아차리는 대신 제출 전에 걸러낸다.
     */
    fun toNewUser(): NewUser? {
        val birth = birthDate ?: return null
        val genderValue = gender ?: return null
        if (loginId.isBlank() || password.isBlank()) return null
        if (name.isBlank() || phoneNumber.isBlank() || email.isBlank()) return null
        // 닉네임은 2026-09 서버 변경으로 가입 필수가 됐다 — 비어 있으면 400 을 맞기 전에 걸러낸다
        val trimmedNickname = nickname.trim()
        if (trimmedNickname.isBlank()) return null
        // 문자 인증 토큰은 2026-10 서버 변경으로 가입 필수가 됐다 — 닉네임과 같은 방식으로 먼저 걸러낸다
        // (없이 보내면 서버가 400 `INVALID_REQUEST` 로 전부 튕긴다).
        if (verificationToken.isBlank()) return null
        return NewUser(
            loginId = loginId,
            password = password,
            nickname = trimmedNickname,
            name = name,
            birthDate = birth,
            phoneNumber = phoneNumber,
            gender = genderValue,
            email = email,
            verificationToken = verificationToken,
        )
    }
}

/**
 * [AuthError] → 사용자에게 보여줄 한국어 문구. 가입·로그인·문자 인증이 **이 표 하나**를 쓴다.
 *
 * 문구 기준: 사용자가 **지금 무엇을 해야 하는지**가 한 줄에 들어가야 한다. 특히 서버가 같은 상태
 * 코드로 뭉쳐 보내는 묶음(429 세 개 · 400 세 개)은 할 일이 서로 다르므로 절대 같은 말을 쓰지 않는다
 * — "잠시 후 다시 시도해 주세요"를 일일 한도 초과에 띄우면 사용자가 1분마다 재전송을 누르며 하루를 보낸다.
 */
internal fun Throwable.authUserMessage(): String {
    val authException = this as? AuthException ?: return "잠시 후 다시 시도해 주세요"
    return authErrorMessage(authException.error, authException.serverMessage)
}

/**
 * 예외가 아니라 [AuthError] 만 들고 문구를 꺼내는 입구.
 *
 * 앱이 **스스로 아는 실패**에도 같은 문구가 필요해서 나눠 뒀다 — 인증번호 3분 만료는 서버에
 * 묻지 않아도 알 수 있고([PhoneVerifyState.onCodeExpired]), 가입이 토큰을 거절해 인증 단계로
 * 되돌릴 때도([PhoneVerifyViewModel.onSignupRejectedPhone]) 예외 객체가 손에 없다.
 *
 * `when` 을 비포괄로 두지 않는 것이 중요하다: [AuthError] 가 늘어나면 컴파일이 깨져서
 * 문구를 빠뜨린 채 "잠시 후 다시 시도해 주세요"로 뭉개는 일이 생기지 않는다(이번 문자 인증 8종이
 * 그렇게 발견됐다).
 */
internal fun authErrorMessage(error: AuthError, serverMessage: String? = null): String = when (error) {
    AuthError.LOGIN_FAILED -> "아이디 또는 비밀번호가 올바르지 않아요"
    AuthError.LOGIN_ID_DUPLICATED -> "이미 사용 중인 아이디예요"
    // 전화번호 중복은 "계정이 이미 있다"는 뜻이라, 고칠 곳(로그인)을 함께 알려준다.
    // 2026-10 부터는 가입이 아니라 **인증번호 발송 단계**에서 먼저 난다 — 문자를 기다리게 하지 않는다.
    AuthError.PHONE_NUMBER_DUPLICATED -> "이미 가입된 번호예요. 로그인해서 이어가 주세요"
    // 같은 409, 같은 발송 단계인데 할 일이 정반대다 — 위쪽은 "로그인하세요"지만 여기엔 로그인할 계정이 없다.
    // 기다리는 수밖에 없는 상황이라 재시도를 권하지 않고 쓸 수 있는 선택지(다른 번호)를 말한다.
    // 서버 쓸어내기가 하루 한 번이라 "정확히 30일 뒤"라고 약속하지 않는다(AuthPolicy.REJOIN_RESTRICTED_DAYS).
    AuthError.REJOIN_RESTRICTED ->
        "탈퇴한 번호는 ${AuthPolicy.REJOIN_RESTRICTED_DAYS}일 동안 다시 가입할 수 없어요. 기간이 지난 뒤에 가입하거나 다른 번호를 써 주세요"
    AuthError.NICKNAME_DUPLICATED -> "이미 사용 중인 닉네임이에요"
    AuthError.INVALID_NICKNAME -> "닉네임은 한글·영문·숫자 2~10자로 입력해 주세요"
    // 닉네임 형식 오류는 두 코드로 온다 — 서버 VO 가 먼저 걸리면 USER107, Bean Validation 이
    // 먼저 걸리면 INVALID_REQUEST(message = "nickname: …") 다. 사용자에겐 같은 사실이므로 한 문구로 모은다.
    AuthError.INVALID_REQUEST -> when {
        serverMessage?.startsWith("nickname") == true ->
            "닉네임은 한글·영문·숫자 2~10자로 입력해 주세요"
        // 발송 요청의 번호 형식 위반은 @Pattern 이 먼저 걸려 이 코드로 온다(확인 요청은 USER105).
        // 서버 원문("phoneNumber: …")보다 고칠 형식을 보여주는 쪽이 낫다.
        serverMessage?.startsWith("phoneNumber") == true ->
            "휴대폰 번호를 010-1234-5678 형식으로 입력해 주세요"
        else -> serverMessage ?: "입력한 정보를 다시 확인해 주세요"
    }

    // ── 문자 인증 (2026-10 신규). 할 일이 전부 다르므로 한 문구도 겹치지 않는다 ──
    AuthError.INVALID_PHONE_NUMBER -> "휴대폰 번호를 010-1234-5678 형식으로 입력해 주세요"
    // 남은 초는 「다시 받기」 버튼이 카운트다운으로 보여 준다 — 서버가 남은 시간을 주지 않아
    // 문구에 숫자를 박을 수 없다(박으면 틀린 숫자가 된다).
    AuthError.SMS_COOLDOWN -> "인증번호는 1분에 한 번만 받을 수 있어요. 버튼의 남은 시간이 끝나면 다시 받아 주세요"
    // 기다려서 풀리는 문제가 아니다 — 재시도를 권하지 않고 다음 수단을 말한다
    AuthError.SMS_DAILY_LIMIT_EXCEEDED ->
        "오늘 받을 수 있는 인증번호를 모두 썼어요(하루 ${AuthPolicy.SMS_DAILY_LIMIT}번). 내일 다시 시도하거나 다른 번호로 가입해 주세요"
    AuthError.SMS_CODE_EXPIRED -> "인증번호가 만료됐어요. 「인증번호 다시 받기」로 새 번호를 받아 주세요"
    // 서버에 아직 인증번호가 남아 있다 — 입력칸만 비우고 다시 치면 된다(남은 시도는 서버가 센다)
    AuthError.SMS_CODE_MISMATCH -> "인증번호가 일치하지 않아요. 문자로 받은 6자리를 다시 입력해 주세요"
    // 5회를 넘기면 서버가 그 인증번호를 지운다 — 같은 번호를 더 입력해 봐야 소용없다
    AuthError.SMS_TOO_MANY_ATTEMPTS ->
        "인증번호를 ${AuthPolicy.SMS_MAX_VERIFY_ATTEMPTS}번 잘못 입력했어요. 새 인증번호를 받아 주세요"
    // 토큰 만료·소진·번호 불일치가 한 코드로 온다. 셋 다 할 일은 "인증을 다시" 하나다
    AuthError.PHONE_NOT_VERIFIED -> "휴대폰 인증이 만료됐어요. 인증을 다시 하고 가입을 마쳐 주세요"
    // 사용자 입력 문제가 아니다 — 무엇을 고쳐야 할지 찾게 만들지 않는다
    AuthError.SMS_UNAVAILABLE -> "문자 인증이 일시적으로 안 되고 있어요. 잠시 뒤 다시 시도해 주세요"

    // ── 회원 탈퇴 (2026-10 신규, 37 탈퇴 화면) ──
    // 둘 다 409 라 코드를 놓치면 "이미 사용 중인 아이디예요"(409 폴백)가 탈퇴 화면에 뜬다.
    // 할 일을 한 줄에 담는다 — 「나가기」가 어디 있는지는 사용자가 이미 안다(21·25·26 의 버튼).
    AuthError.ONGOING_PARTY_EXISTS -> "진행 중인 합승이 있어요. 합승에서 먼저 나간 뒤에 탈퇴해 주세요"
    // 로그인 실패(401)와 달리 세션은 살아 있다 — "다시 로그인"이 아니라 "다시 입력"이다.
    AuthError.PASSWORD_MISMATCH -> "비밀번호가 일치하지 않아요"

    AuthError.SESSION_EXPIRED -> "다시 로그인해 주세요"
    AuthError.NETWORK -> "네트워크 연결을 확인해 주세요"
    AuthError.UNKNOWN -> "잠시 후 다시 시도해 주세요"
}
