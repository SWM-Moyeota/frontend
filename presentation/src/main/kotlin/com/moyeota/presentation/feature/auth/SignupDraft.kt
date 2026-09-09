package com.moyeota.presentation.feature.auth

import com.moyeota.domain.model.AuthError
import com.moyeota.domain.model.AuthException
import com.moyeota.domain.model.Gender
import com.moyeota.domain.model.NewUser
import java.time.LocalDate

/**
 * 가입 플로우(10 프로필 만들기 → 12 매너 서약)가 모으는 값.
 *
 * 서버 `POST /api/v1/users` 가 요구하는 7개 필드가 그대로 이 클래스의 필드다 — 계정 유형이나
 * 학교·재직 인증 결과는 API 에 자리가 없어 담지 않는다(그 화면들이 가입 경로에서 빠진 이유이기도 하다).
 *
 * | 필드 | 수집 화면 |
 * |---|---|
 * | [nickname] | 10 프로필 만들기 (1/3, 「프로필」 절) |
 * | [name] · [birthDate] · [gender] · [phoneNumber] | 10 프로필 만들기 (1/3, 「기본 정보」 절) |
 * | [loginId] · [password] · [email] | 10 프로필 만들기 (1/3, 「로그인 정보」 절) |
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
        return NewUser(
            loginId = loginId,
            password = password,
            nickname = trimmedNickname,
            name = name,
            birthDate = birth,
            phoneNumber = phoneNumber,
            gender = genderValue,
            email = email,
        )
    }
}

/**
 * [AuthError] → 사용자에게 보여줄 한국어 문구 (21 보고서 §2 권장 문구 표).
 *
 * `INVALID_REQUEST` 만 서버 원문을 그대로 쓴다 — 서버가 "필드: 사유" 형태로 주기 때문에
 * 임의로 뭉뚱그린 문구보다 사용자가 고칠 곳을 정확히 알 수 있다.
 */
internal fun Throwable.authUserMessage(): String {
    val authException = this as? AuthException ?: return "잠시 후 다시 시도해 주세요"
    return when (authException.error) {
        AuthError.LOGIN_FAILED -> "아이디 또는 비밀번호가 올바르지 않아요"
        AuthError.LOGIN_ID_DUPLICATED -> "이미 사용 중인 아이디예요"
        // 전화번호 중복은 "계정이 이미 있다"는 뜻이라, 고칠 곳(로그인)을 함께 알려준다
        AuthError.PHONE_NUMBER_DUPLICATED -> "이미 가입된 전화번호예요. 로그인해 주세요"
        AuthError.NICKNAME_DUPLICATED -> "이미 사용 중인 닉네임이에요"
        AuthError.INVALID_NICKNAME -> "닉네임은 한글·영문·숫자 2~10자로 입력해 주세요"
        // 닉네임 형식 오류는 두 코드로 온다 — 서버 VO 가 먼저 걸리면 USER107, Bean Validation 이
        // 먼저 걸리면 INVALID_REQUEST(message = "nickname: …") 다. 사용자에겐 같은 사실이므로 한 문구로 모은다.
        AuthError.INVALID_REQUEST -> when {
            authException.serverMessage?.startsWith("nickname") == true ->
                "닉네임은 한글·영문·숫자 2~10자로 입력해 주세요"
            else -> authException.serverMessage ?: "입력한 정보를 다시 확인해 주세요"
        }
        AuthError.SESSION_EXPIRED -> "다시 로그인해 주세요"
        AuthError.NETWORK -> "네트워크 연결을 확인해 주세요"
        AuthError.UNKNOWN -> "잠시 후 다시 시도해 주세요"
    }
}
