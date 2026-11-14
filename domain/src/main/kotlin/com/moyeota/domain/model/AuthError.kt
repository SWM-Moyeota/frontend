package com.moyeota.domain.model

/**
 * 인증 실패 사유. 서버의 `UserErrorCode` + 전송 계층 실패를 화면이 분기 가능한 형태로 좁힌 것.
 * ViewModel 은 이 값으로 한국어 안내 문구를 고른다.
 */
enum class AuthError {
    /** 401 LOGIN_FAILED — 아이디나 비밀번호가 다르다. */
    LOGIN_FAILED,

    /** 409 LOGIN_ID_DUPLICATED — 이미 존재하는 아이디. */
    LOGIN_ID_DUPLICATED,

    /**
     * 409 `USER104` PHONE_NUMBER_DUPLICATED — 이미 가입된 전화번호.
     *
     * **아이디 중복과 같은 409 라 코드를 보지 않으면 구분되지 않는다.** 폴백에 맡기면
     * 전화번호가 겹쳤는데 "이미 사용 중인 아이디예요"가 떠서 사용자가 아이디만 계속 바꾸게 된다
     * (서버 검사 순서: loginId → phoneNumber → nickname).
     *
     * **2026-10 부터는 가입이 아니라 문자 발송 단계에서 먼저 난다** —
     * [AuthRepository.sendPhoneVerificationCode][com.moyeota.domain.repository.AuthRepository.sendPhoneVerificationCode]
     * 가 호출하는 서버 메서드의 첫 줄이 번호 중복 검사다(문자 비용을 쓰기 전에 끊는다).
     * 즉 화면은 **전화번호를 받은 직후**, 인증번호 입력칸을 열기 전에 이 안내를 띄워야 한다
     * — 인증번호를 기다리게 한 뒤 "이미 가입된 번호"를 말하면 사용자는 오지 않는 문자를 기다린다.
     * 가입 단계의 409 는 그 사이에 같은 번호로 누가 먼저 가입한 드문 경우에만 남는다.
     */
    PHONE_NUMBER_DUPLICATED,

    /**
     * 409 `USER109` REJOIN_RESTRICTED — 탈퇴한 번호의 재가입 제한 기간
     * ([AuthPolicy.REJOIN_RESTRICTED_DAYS][com.moyeota.domain.model.AuthPolicy.REJOIN_RESTRICTED_DAYS]일)이
     * 아직 지나지 않았다.
     *
     * **[PHONE_NUMBER_DUPLICATED] 와 같은 409 이고 같은 자리에서 난다** — 서버
     * `SmsVerificationService.send` 가 번호 중복을 먼저 보고, 바로 다음 줄에서 탈퇴 기록을 본다.
     * 즉 가입 흐름에서 이 에러를 만나는 지점은 `register` 가 아니라
     * [AuthRepository.sendPhoneVerificationCode][com.moyeota.domain.repository.AuthRepository.sendPhoneVerificationCode]다
     * (문자 비용을 쓰기 전에 끊는다).
     *
     * 코드를 놓쳐 409 폴백으로 떨어지면 「이미 사용 중인 아이디예요」가 뜬다 — 사용자는 아이디를
     * 계속 바꿔 보게 되고, 진짜 원인(그 번호로는 지금 가입할 수 없다)은 영영 보이지 않는다.
     * [PHONE_NUMBER_DUPLICATED] 와도 합치면 안 된다: 그쪽은 「로그인하세요」가 답이지만
     * 여기서는 로그인할 계정 자체가 없다.
     */
    REJOIN_RESTRICTED,

    /**
     * 400 `USER107` INVALID_NICKNAME — 닉네임 형식 위반(2~10자 한글·영문·숫자가 아니다).
     *
     * [INVALID_REQUEST] 와 굳이 나눈 이유: 닉네임 중복 확인
     * ([AuthRepository.isNicknameTaken][com.moyeota.domain.repository.AuthRepository.isNicknameTaken])
     * 은 형식이 틀리면 exists 판정 전에 400 을 낸다 — 화면은 이걸 "중복"이 아니라 "형식 오류"로
     * 안내해야 한다. 서버 Bean Validation(@NotBlank/@Size) 에 먼저 걸리면 코드가
     * `INVALID_REQUEST` 로 오므로 화면 문구는 양쪽 다 형식 안내로 수렴시키는 게 안전하다.
     */
    INVALID_NICKNAME,

    /** 409 `USER108` NICKNAME_DUPLICATED — 이미 사용 중인 닉네임. 가입과 중복 확인 양쪽에서 나온다. */
    NICKNAME_DUPLICATED,

    /**
     * 400 `USER112` INVALID_PROFILE_IMAGE — 서버가 제공하지 않는 프로필 이미지 키를 보냈다.
     *
     * 고를 수 있는 건 `GET /api/v1/users/profile-images` 가 주는 16개 key 뿐이고, 목록 밖의 키는
     * 저장 **전에** 거절된다. 즉 이건 사용자가 고칠 수 있는 입력이 아니라 **앱이 목록에 없는 키를
     * 보낸 것**이다 — 할 일은 목록을 다시 받아 고르게 하는 것뿐이고, 같은 키로 재시도하면 같은 400 이다.
     *
     * [INVALID_REQUEST] 로 뭉치면 안 된다: 그쪽 문구는 서버 원문을 그대로 띄우는데 서버가 주는 말은
     * 「제공하지 않는 프로필 이미지입니다」라 사용자가 무엇을 해야 할지 알 수 없다. 게다가 코드를
     * 놓치면 400 폴백이 정확히 [INVALID_REQUEST] 라 조용히 그리로 떨어진다.
     *
     * **[NICKNAME_DUPLICATED] 보다 먼저 난다.** 서버는 imageKey 를 닉네임 중복보다 앞서 검사하므로
     * 닉네임과 이미지가 같이 틀렸으면 이 코드가 온다 — 화면이 「이미 사용 중인 닉네임」을 먼저 말하면
     * 거짓이 된다. 그리고 거절되면 **닉네임도 함께 안 바뀐다**(서버가 한 요청을 통째로 거절한다).
     */
    INVALID_PROFILE_IMAGE,

    /**
     * 400 `USER105` INVALID_PHONE_NUMBER — 전화번호 형식 위반.
     *
     * 서버가 번호를 `PhoneNumber` VO 로 만들다가 내는 코드다. **문자 인증 확인
     * ([AuthRepository.verifyPhoneCode][com.moyeota.domain.repository.AuthRepository.verifyPhoneCode])
     * 쪽에서 주로 보인다** — 발송 요청에는 Bean Validation `@Pattern` 이 걸려 있어 같은 상황이
     * [INVALID_REQUEST] 로 오는데, 확인 요청에는 `@NotBlank` 만 있어 VO 까지 내려가기 때문이다.
     * 사용자에게는 양쪽 다 "번호 형식" 안내로 수렴시키는 게 맞다.
     */
    INVALID_PHONE_NUMBER,

    /**
     * 429 `USER202` SMS_COOLDOWN — 재발송 대기 중.
     * 같은 번호는 [AuthPolicy.SMS_RESEND_COOLDOWN_SECONDS][com.moyeota.domain.model.AuthPolicy.SMS_RESEND_COOLDOWN_SECONDS]
     * 초에 한 번만 받을 수 있다. 화면이 카운트다운을 걸면 이 에러는 거의 보이지 않아야 정상이다.
     */
    SMS_COOLDOWN,

    /**
     * 429 `USER203` SMS_DAILY_LIMIT_EXCEEDED — 하루 발송 한도 초과
     * ([AuthPolicy.SMS_DAILY_LIMIT][com.moyeota.domain.model.AuthPolicy.SMS_DAILY_LIMIT]회).
     *
     * **[SMS_COOLDOWN] 과 같은 429 라 코드를 보지 않으면 구분되지 않는다** — "1분 뒤 다시"로
     * 안내하면 사용자가 하루 종일 1분마다 눌러 보게 된다. 여기서는 재시도를 권하면 안 된다.
     */
    SMS_DAILY_LIMIT_EXCEEDED,

    /**
     * 400 `USER204` SMS_CODE_EXPIRED — 저장된 인증번호가 없다.
     *
     * 만료([AuthPolicy.SMS_CODE_TTL_SECONDS][com.moyeota.domain.model.AuthPolicy.SMS_CODE_TTL_SECONDS]초)
     * 뿐 아니라 **발송한 적이 없는 번호**와 **이미 한 번 인증에 성공해 코드가 지워진 번호**도 같은 코드로 온다
     * (서버가 성공 시 코드를 삭제한다). 어느 쪽이든 화면의 대응은 "재발송"이다.
     */
    SMS_CODE_EXPIRED,

    /** 400 `USER205` SMS_CODE_MISMATCH — 인증번호가 틀렸다. 남은 시도는 입력칸만 비우고 다시 받으면 된다. */
    SMS_CODE_MISMATCH,

    /**
     * 429 `USER206` SMS_TOO_MANY_ATTEMPTS — 입력 시도
     * [AuthPolicy.SMS_MAX_VERIFY_ATTEMPTS][com.moyeota.domain.model.AuthPolicy.SMS_MAX_VERIFY_ATTEMPTS]회 초과.
     *
     * **서버가 이때 코드를 지운다** — [SMS_CODE_MISMATCH] 와 달리 같은 번호를 더 입력해 봐야
     * 이후로는 [SMS_CODE_EXPIRED] 만 온다. 화면은 입력칸이 아니라 재발송으로 유도해야 한다.
     * 단 재발송에는 [SMS_COOLDOWN] 1분이 걸려 있다.
     */
    SMS_TOO_MANY_ATTEMPTS,

    /**
     * 400 `USER207` SMS_NOT_VERIFIED — 가입에 실린 `verificationToken` 이 유효하지 않다.
     *
     * 세 경우가 한 코드로 뭉쳐 온다: 토큰 만료
     * ([AuthPolicy.VERIFICATION_TOKEN_TTL_SECONDS][com.moyeota.domain.model.AuthPolicy.VERIFICATION_TOKEN_TTL_SECONDS]초),
     * 이미 가입에 성공해 소진된 토큰, **인증한 번호와 가입 번호가 다른 경우**.
     * 셋 다 화면의 대응은 하나다 — 문자 인증 단계로 되돌린다.
     */
    PHONE_NOT_VERIFIED,

    /**
     * 문자 인증을 지금 진행할 수 없다. 서버 코드 두 개를 합친 값이다 —
     * 502 `USER201` SMS_SEND_FAILED(문자 사업자 호출 실패)와 503 `USER208` SMS_UNAVAILABLE(인증 저장소 장애).
     *
     * 둘을 나누지 않은 이유는 앱의 대응이 같기 때문이다: 사용자 입력 문제가 아니고, 잠시 뒤 재시도뿐이다.
     * 참고로 발송 실패 쪽은 서버가 재발송 대기를 풀어 주므로 **1분을 기다리지 않고 바로 다시 요청할 수 있다**.
     */
    SMS_UNAVAILABLE,

    /**
     * 409 `USER110` ONGOING_PARTY_EXISTS — 진행 중인 합승이 있어 탈퇴할 수 없다.
     *
     * 서버 판정 기준은 "CANCELED·FINISHED 가 아닌 방에 멤버로 들어가 있는가"다
     * (`PartyOngoingChecker` → `Parties.existsOngoingByMemberId`). 즉 모집 중인 방도 포함이며,
     * **앱이 미리 막을 수 없다** — "지금 내가 타고 있는 방"을 묻는 엔드포인트가 없어
     * ([ActivePartyRepository][com.moyeota.domain.repository.ActivePartyRepository] 가 로컬 기억으로
     * 흉내 내는 중이다) 로컬 기억이 비어 있어도 서버에는 방이 남아 있을 수 있다.
     * 그래서 이건 "버튼을 잠글 조건"이 아니라 **눌러 본 뒤 받는 안내**다.
     *
     * 사용자가 할 일은 하나다: 합승에서 먼저 나오고 다시 탈퇴한다.
     *
     * **아이디 중복과 같은 409 라 코드를 놓치면 「이미 사용 중인 아이디예요」가 뜬다**
     * (409 폴백이 [LOGIN_ID_DUPLICATED] 다) — 탈퇴 화면에 아이디 이야기가 뜨는 꼴이다.
     */
    ONGOING_PARTY_EXISTS,

    /**
     * 400 `USER111` PASSWORD_MISMATCH — 탈퇴 본인 확인 비밀번호가 틀렸다.
     *
     * 소셜 회원에게는 나지 않는다 — 비밀번호가 없어 서버가 로그인 토큰만으로 본인 확인을 끝낸다
     * ([AuthRepository.deleteSocialAccount][com.moyeota.domain.repository.AuthRepository.deleteSocialAccount]).
     * 서버가 이 코드를 비밀번호 없이도 내는 경우가 하나 남아 있다: 로컬·소셜 로그인 수단이 **둘 다 없는**
     * 계정(본인 확인을 할 방법이 없다)인데, 정상 가입 경로로는 생기지 않는다.
     * 로그인 실패([LOGIN_FAILED], 401)와 달리 **세션은 그대로 살아 있다**:
     * [SESSION_EXPIRED] 로 뭉치면 멀쩡한 사용자를 로그인 화면으로 쫓아낸다.
     *
     * 화면은 이 하나만 **입력란 오류**로 그리고, 나머지 실패는 배너로 돌린다 — 고칠 곳이 입력란에 있는
     * 유일한 실패이기 때문이다.
     */
    PASSWORD_MISMATCH,

    /**
     * 404 `USER114` PHONE_NOT_REGISTERED — 이 번호로 가입한 계정이 없다(아이디 찾기).
     *
     * **발송 단계에서 난다** — 서버가 문자 비용을 쓰기 전에 끊는다. 가입 흐름의
     * [PHONE_NUMBER_DUPLICATED] 와 뜻이 정반대라(그쪽은 「계정이 있다」) 합치면 안 된다:
     * 여기서 사용자가 갈 곳은 로그인이 아니라 **가입**이다.
     *
     * 비밀번호 재설정에서는 **절대 오지 않는다** — 그쪽은 계정 존재 여부를 숨기려고 맞든 틀리든 같은 202 를 준다.
     */
    PHONE_NOT_REGISTERED,

    /**
     * 400 `USER301` PASSWORD_RESET_VERIFICATION_FAILED — 재설정 인증번호가 통과하지 못했다.
     *
     * **틀림·만료·없는 접수·아직 발송 전이 한 코드로 온다**(서버가 사유를 가르지 않는다).
     * 그중 가장 헷갈리는 건 「아이디·번호가 가입 정보와 달라 문자가 애초에 안 나간」 경우다 —
     * 발송 응답이 늘 같은 202 라 앱은 그 사실을 알 수 없고, 사용자는 오지 않는 문자를 기다린다.
     * 그래서 화면은 「다시 입력」과 함께 「아이디·번호 확인」을 같이 말해야 한다.
     *
     * 틀려도 접수는 살아 있어 5번까지 다시 넣을 수 있다. 넘기면 [PASSWORD_RESET_RATE_LIMITED] 다.
     */
    PASSWORD_RESET_CODE_REJECTED,

    /**
     * 400 `USER302` PASSWORD_RESET_TOKEN_INVALID — 재설정 권한이 없다(만료 600초·이미 사용·무효화).
     *
     * 입력을 고쳐서 풀리지 않는다 — **인증부터 다시** 해야 한다.
     */
    PASSWORD_RESET_TOKEN_INVALID,

    /**
     * 400 `USER303` PASSWORD_CONFIRM_MISMATCH — 새 비밀번호와 확인 값이 다르다.
     * 앱이 먼저 막으므로 정상 경로에서는 보이지 않는다. 토큰은 소진되지 않는다.
     */
    PASSWORD_CONFIRM_MISMATCH,

    /**
     * 400 `USER304` PASSWORD_POLICY_VIOLATION — 비밀번호 규칙 위반.
     *
     * 앱도 같은 규칙([AuthPolicy.isValidPassword])으로 먼저 막는다. 그래도 남는 경우가 하나 있다 —
     * 서버는 **UTF-8 72바이트** 상한도 보는데(해시 함수의 한계) 앱 규칙에는 없다.
     */
    PASSWORD_POLICY_VIOLATION,

    /** 400 `USER305` SAME_PASSWORD — 지금 쓰는 비밀번호와 같다. **앱이 미리 알 수 없는** 유일한 입력 오류다. */
    SAME_PASSWORD,

    /**
     * 429 `USER306` PASSWORD_RESET_RATE_LIMITED — 재설정 요청 제한.
     *
     * 네 가지 제한이 한 코드로 온다: 문자 쿨다운(1분) · 하루 문자 횟수 · 계정당 시간당 10회 · IP 당 분당 30회.
     * 서버가 `Retry-After` 로 남은 초를 주지만 사유는 주지 않으므로 문구에 숫자를 박지 않는다.
     * 확인 단계에서는 「5번 틀렸다」는 뜻이다 — 그 접수로는 더 못 넣는다.
     */
    PASSWORD_RESET_RATE_LIMITED,

    /**
     * 401 `USER401` SOCIAL_TOKEN_INVALID — 서버가 구글 `idToken` 을 받아들이지 않았다.
     *
     * 위조·만료·**다른 앱용 토큰**(aud 불일치)·토큰에 email 이 없는 경우가 한 코드로 온다.
     * 사용자가 고칠 입력이 없다 — 할 일은 「구글 로그인을 다시 누른다」 하나다.
     *
     * **세션 만료와 같은 401 이라 코드를 보지 않으면 구분되지 않는다.** 401 폴백이 [SESSION_EXPIRED] 라
     * 놓치면 로그인도 안 한 사람에게 「다시 로그인해 주세요」가 뜬다. [LOGIN_FAILED] 와도 합치지 않는다 —
     * 그쪽 문구는 「아이디 또는 비밀번호」를 말하는데 구글 로그인에는 둘 다 없다.
     *
     * 반복해서 난다면 사용자 탓이 아니라 **앱 설정 탓일 가능성이 높다**: idToken 을 요청할 때 넣는
     * 서버용(웹) 클라이언트 ID 가 서버의 `GOOGLE_CLIENT_IDS` 에 없으면 모든 토큰이 이 코드로 거절된다.
     */
    SOCIAL_TOKEN_INVALID,

    /**
     * 401 `USER402` SOCIAL_SIGNUP_EXPIRED — 가입 대기표(`signupToken`)가 없거나 만료됐다
     * ([AuthPolicy.SOCIAL_SIGNUP_TOKEN_TTL_SECONDS][com.moyeota.domain.model.AuthPolicy.SOCIAL_SIGNUP_TOKEN_TTL_SECONDS]초).
     *
     * 입력을 고쳐서 풀리지 않는다 — **구글 로그인부터 다시** 해야 한다. 문자 인증 단계로 되돌리는
     * [PHONE_NOT_VERIFIED] 와 헷갈리지 말 것: 그쪽은 문자 인증만 다시 받으면 되지만 여기서는 그 앞 단계가
     * 죽었다. 서버가 대기표를 문자 인증보다 **먼저** 확인하므로 둘 다 죽었으면 이 코드가 온다.
     * 다시 받은 대기표로 가입할 때 문자 인증 토큰이 아직 살아 있으면(둘의 수명은 따로 센다) 그대로 쓸 수 있다.
     *
     * 이것도 401 이라 폴백에 맡기면 [SESSION_EXPIRED] 가 된다 — 가입 도중인 사람에게 세션 이야기가 뜬다.
     */
    SOCIAL_SIGNUP_EXPIRED,

    /**
     * 409 `USER403` SOCIAL_ALREADY_REGISTERED — 그 구글 계정은 이미 가입돼 있다.
     *
     * 대기표를 받은 뒤 가입을 끝내기 전에 **같은 구글 계정으로 다른 곳에서 먼저 가입을 마친** 경우다
     * (기기 두 대, 또는 가입 제출이 성공했는데 응답을 못 받고 다시 누른 경우). 실패처럼 보이지만
     * 사용자가 원한 상태에는 이미 와 있다 — **구글 로그인을 다시 부르면 이번에는 로그인된다**
     * ([GoogleLoginResult.LoggedIn]).
     *
     * [PHONE_NUMBER_DUPLICATED] 와 합치지 않는다: 그쪽은 「그 번호를 쓰는 **다른** 계정이 있다」이고
     * 여기는 「**이** 계정이 이미 있다」다. 409 폴백은 [LOGIN_ID_DUPLICATED] 라 놓치면 아이디가 없는
     * 가입 화면에 「이미 사용 중인 아이디예요」가 뜬다.
     */
    SOCIAL_ALREADY_REGISTERED,

    /**
     * 503 `USER404` SOCIAL_UNAVAILABLE — 지금은 소셜 로그인·가입을 진행할 수 없다(가입 대기표 저장소 장애).
     *
     * 사용자 입력 문제가 아니고 잠시 뒤 재시도뿐이다. 대응은 [SMS_UNAVAILABLE] 과 같지만 **합치지 않는다** —
     * 그쪽 문구는 「문자 인증이 안 된다」인데 이 코드는 구글 로그인 버튼을 누른 자리에서도 나므로,
     * 문자를 요청한 적도 없는 사람에게 문자 이야기가 뜨게 된다.
     *
     * 번호가 404 처럼 생겼지만 **HTTP 404 가 아니라 503 이다**(`USER404` 는 코드 문자열일 뿐이다).
     */
    SOCIAL_UNAVAILABLE,

    /**
     * 400 INVALID_REQUEST — 서버 검증 실패. 서버 message 가 "필드: 사유" 형태라
     * [AuthException.serverMessage] 를 그대로 보여줘도 사용자에게 의미가 통한다.
     */
    INVALID_REQUEST,

    /**
     * 리프레시 재발급까지 실패해 세션이 끝났다(만료·회전·로그아웃·탈퇴).
     * 서버가 사유를 401 하나로 뭉치므로 앱의 대응도 "재로그인" 하나다.
     *
     * **스스로 탈퇴한 직후에는 이 값이 보이지 않아야 한다.** 탈퇴가 성공하면 서버가 토큰을 즉시
     * 무효화하므로(인증 캐시까지 비운다 — 남은 수명이 있는 액세스 토큰도 바로 막힌다) 그 뒤의 요청은
     * 전부 401 이 되는데, [AuthRepository.deleteAccount][com.moyeota.domain.repository.AuthRepository.deleteAccount]
     * 가 204 를 받은 자리에서 로컬 세션을 비우므로 401 을 맞을 요청 자체가 남지 않는다.
     * 여기까지 올라온다면 그 정리가 새고 있다는 신호다.
     */
    SESSION_EXPIRED,

    /** 연결 실패·타임아웃 등 응답을 받지 못한 경우. 재시도 안내 대상. */
    NETWORK,

    /** 5xx 또는 해석 불가. */
    UNKNOWN,
}

/**
 * [AuthRepository][com.moyeota.domain.repository.AuthRepository] 의 모든 실패는 이 예외로 통일된다.
 * data 계층의 HttpException/IOException 은 여기서 막히고 presentation 까지 새지 않는다.
 */
class AuthException(
    val error: AuthError,
    /** 서버가 준 원문 message. 없으면 null. */
    val serverMessage: String? = null,
    cause: Throwable? = null,
    /**
     * 서버가 `Retry-After` 로 알려 준 「다시 시도할 수 있을 때까지의 초」. 주지 않았으면 null.
     *
     * 지금 이 값을 주는 건 비밀번호 재설정의 429 `USER306` 뿐이다. 네 가지 제한(문자 쿨다운 60초 ·
     * 하루 문자 횟수 · 계정당 시간당 · IP 당 분당)이 한 코드로 오는데, **사유는 없어도 남은 시간은 온다** —
     * 화면은 이 값으로 「곧 풀린다」와 「한참 걸린다」를 가른다.
     */
    val retryAfterSeconds: Int? = null,
) : RuntimeException(serverMessage ?: error.name, cause)
