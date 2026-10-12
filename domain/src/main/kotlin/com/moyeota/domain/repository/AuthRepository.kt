package com.moyeota.domain.repository

import com.moyeota.domain.model.AuthState
import com.moyeota.domain.model.NewUser
import com.moyeota.domain.model.UserProfileInfo
import kotlinx.coroutines.flow.StateFlow

/**
 * 인증 경계면. presentation 이 보는 유일한 계약이며 토큰은 노출하지 않는다
 * — 토큰 부착·재발급·영속화는 전부 data 계층(OkHttp 인터셉터/Authenticator)에서 끝난다.
 *
 * 모든 실패는 [com.moyeota.domain.model.AuthException] 으로 던진다.
 */
interface AuthRepository {

    /**
     * 로그인 상태의 단일 출처. 화면 진입 라우팅(스플래시 → 로그인/홈)의 근거이자,
     * **재발급까지 실패했을 때 세션 만료를 통보받는 경로**다
     * — 사용자가 아무 화면에 있든 [AuthState.Unauthenticated] 로 바뀌면 로그인 화면으로 보낸다.
     *
     * 앱 시작 직후에는 [AuthState.Unknown] 이며, 디스크 복원이 끝나면 확정 값으로 바뀐다.
     */
    val authState: StateFlow<AuthState>

    /**
     * 문자 인증번호 발송. POST /api/v1/auth/sms/send (**204 No Content**, 토큰 불필요).
     *
     * 가입 전에 불리므로 Bearer 가 붙지 않는 인증 전용 클라이언트로 나간다
     * (`/api/v1/auth/` 하위가 서버 permitAll 구간이다).
     *
     * 번호는 하이픈이 있든 없든 받아서 숫자열로 정규화해 보낸다
     * ([AuthPolicy.normalizePhoneNumber][com.moyeota.domain.model.AuthPolicy.normalizePhoneNumber]).
     *
     * **성공은 "문자를 보냈다"까지다.** 돌려받는 값이 없고, 코드는 서버만 안다.
     * 로컬·dev 서버는 실제 문자를 보내지 않는다 — `provider=log` 면 서버 로그에 코드가 찍히고,
     * dev 프로필은 `moyeota.sms.fixed-code` 가 `000000` 으로 고정돼 있다.
     *
     * 서버가 문자 비용을 쓰기 전에 **이미 가입된 번호인지 먼저 확인한다** — 그래서 이 단계에서
     * 409 가 날 수 있고, 그건 "로그인하세요"라는 뜻이다.
     *
     * @throws com.moyeota.domain.model.AuthException
     *   [com.moyeota.domain.model.AuthError.PHONE_NUMBER_DUPLICATED] (409 `USER104`) /
     *   [com.moyeota.domain.model.AuthError.SMS_COOLDOWN] (429 `USER202`) /
     *   [com.moyeota.domain.model.AuthError.SMS_DAILY_LIMIT_EXCEEDED] (429 `USER203`) /
     *   [com.moyeota.domain.model.AuthError.SMS_UNAVAILABLE] (502 `USER201` / 503 `USER208`) /
     *   [com.moyeota.domain.model.AuthError.INVALID_REQUEST] (400, 번호 형식 — 발송 쪽은 `@Pattern` 이 먼저 걸린다) /
     *   [com.moyeota.domain.model.AuthError.NETWORK]
     */
    suspend fun sendPhoneVerificationCode(phoneNumber: String)

    /**
     * 문자 인증번호 확인. POST /api/v1/auth/sms/verify (200, 토큰 불필요).
     *
     * @param phoneNumber [sendPhoneVerificationCode] 에 넣은 것과 같은 번호. 표기(하이픈)는 달라도 되지만
     *   **번호 자체가 다르면 통과하지 않는다** — 서버가 번호 해시로 저장된 코드를 찾는다.
     * @param code 숫자 6자리([AuthPolicy.isValidVerificationCode][com.moyeota.domain.model.AuthPolicy.isValidVerificationCode]).
     *
     * @return 가입에 실어 보낼 `verificationToken`. 이 값을 [NewUser.verificationToken] 에 넣는다
     *   — 수명 10분, 가입 성공 시 소진된다(상세는 [NewUser.verificationToken] KDoc).
     *
     * **성공하면 서버가 인증번호를 지운다.** 같은 코드로 다시 확인하면
     * [com.moyeota.domain.model.AuthError.SMS_CODE_EXPIRED] 가 오므로, 화면은 받은 토큰을
     * 가입 제출까지 들고 있어야 한다(재확인으로 다시 얻을 수 없다).
     *
     * @throws com.moyeota.domain.model.AuthException
     *   [com.moyeota.domain.model.AuthError.SMS_CODE_MISMATCH] (400 `USER205`) /
     *   [com.moyeota.domain.model.AuthError.SMS_CODE_EXPIRED] (400 `USER204`) /
     *   [com.moyeota.domain.model.AuthError.SMS_TOO_MANY_ATTEMPTS] (429 `USER206`) /
     *   [com.moyeota.domain.model.AuthError.INVALID_PHONE_NUMBER] (400 `USER105`) /
     *   [com.moyeota.domain.model.AuthError.SMS_UNAVAILABLE] (503 `USER208`) /
     *   [com.moyeota.domain.model.AuthError.NETWORK]
     */
    suspend fun verifyPhoneCode(phoneNumber: String, code: String): String

    /**
     * 회원가입. POST /api/v1/auth/register (201).
     *
     * **가입은 로그인시키지 않는다** — 서버가 토큰이 아니라 uuid 만 준다.
     * 성공 후 화면은 같은 자격증명으로 [login] 을 이어서 호출해야 한다.
     *
     * [NewUser.nickname] 은 **필수다** — 서버가 닉네임 없이는 가입을 받지 않는다.
     *
     * [NewUser.verificationToken] 도 **필수다**(2026-10 서버 변경). 비어 있으면 서버 검증에서
     * 400 `INVALID_REQUEST` 로 튕기므로, 화면은 [sendPhoneVerificationCode] →
     * [verifyPhoneCode] 를 먼저 끝내고 그 토큰을 실어야 한다.
     *
     * **실패가 토큰을 태우지는 않는다** — 서버는 세 테이블 저장이 다 끝난 뒤에야 토큰을 지운다.
     * 아이디·닉네임 중복(409)으로 되돌아온 사용자는 입력만 고쳐 같은 토큰으로 재시도할 수 있고,
     * 문자 인증을 다시 받게 할 이유는 [com.moyeota.domain.model.AuthError.PHONE_NOT_VERIFIED] 뿐이다.
     *
     * @return 생성된 사용자의 UUID 문자열
     * @throws com.moyeota.domain.model.AuthException
     *   [com.moyeota.domain.model.AuthError.PHONE_NOT_VERIFIED] (400 `USER207`, 토큰 만료·소진·번호 불일치) /
     *   [com.moyeota.domain.model.AuthError.LOGIN_ID_DUPLICATED] (409) /
     *   [com.moyeota.domain.model.AuthError.PHONE_NUMBER_DUPLICATED] (409 `USER104`) /
     *   [com.moyeota.domain.model.AuthError.NICKNAME_DUPLICATED] (409 `USER108`) /
     *   [com.moyeota.domain.model.AuthError.INVALID_NICKNAME] (400 `USER107`) /
     *   [com.moyeota.domain.model.AuthError.INVALID_REQUEST] (400, serverMessage 가 "필드: 사유")
     */
    suspend fun register(newUser: NewUser): String

    /**
     * 닉네임 중복 확인. POST /api/v1/auth/nickname/check (200, **토큰 불필요**).
     *
     * 가입 폼의 실시간 확인용이라 로그인 전에 불린다 — 그래서 Bearer 가 붙지 않는 인증 전용
     * 클라이언트로 나간다. 화면은 입력이 멈춘 뒤(디바운스) 호출해야 한다: 타자마다 부르면
     * 서버 왕복이 글자 수만큼 쌓인다.
     *
     * **여기서 false 를 받아도 가입이 성공한다는 보장은 없다** — 확인과 가입 사이에 누가 먼저
     * 그 닉네임을 가져갈 수 있다. 최종 판정은 [register] 의 409 `USER108` 다.
     *
     * @return true 면 이미 사용 중
     * @throws com.moyeota.domain.model.AuthException
     *   [com.moyeota.domain.model.AuthError.INVALID_NICKNAME] (400 `USER107`, 형식 위반) /
     *   [com.moyeota.domain.model.AuthError.NETWORK]
     */
    suspend fun isNicknameTaken(nickname: String): Boolean

    /**
     * 내 프로필 수정. PATCH /api/v1/users/me (204, **토큰 필수**).
     *
     * **null 인 값은 보내지 않는다** — 서버는 넘어온 필드만 갱신하므로, null 로 "지우기"를 표현할 수 없다.
     * 둘 다 null 이면 서버는 아무것도 하지 않고 204 를 준다.
     *
     * @throws com.moyeota.domain.model.AuthException
     *   [com.moyeota.domain.model.AuthError.INVALID_NICKNAME] (400 `USER107`) /
     *   [com.moyeota.domain.model.AuthError.NICKNAME_DUPLICATED] (409 `USER108`) /
     *   [com.moyeota.domain.model.AuthError.SESSION_EXPIRED] (401)
     */
    suspend fun updateProfile(nickname: String?, imageUrl: String?)

    /**
     * 로그인. POST /api/v1/auth/login.
     * 성공 시 토큰을 영속화하고 [authState] 를 [AuthState.Authenticated] 로 바꾼다
     * — 호출부가 별도로 세션을 저장할 필요는 없다.
     *
     * @return 로그인한 사용자의 UUID 문자열.
     *   서버 응답에는 없고 **액세스 토큰의 `sub` 클레임에서 읽는다** — 응답이 토큰 쌍만 주기 때문이다.
     * @throws com.moyeota.domain.model.AuthException
     *   [com.moyeota.domain.model.AuthError.LOGIN_FAILED] (401)
     */
    suspend fun login(loginId: String, password: String): String

    /**
     * 로그아웃. POST /api/v1/auth/logout (204, 멱등).
     *
     * **로컬 세션을 먼저 비우고 서버 무효화를 시도하므로 절대 실패하지 않는다** —
     * 서버 호출이 실패해도 사용자가 로그아웃 못 하고 갇히는 일이 없어야 한다.
     * 이미 발급된 액세스 토큰은 남은 수명(최대 30분) 동안 서버에서 유효한 채로 남는다.
     */
    suspend fun logout()

    /**
     * 로그인한 본인의 정보 조회. GET /api/v1/local/users/info (200, **토큰 필수**).
     *
     * [authState] 가 이미 uuid 를 들고 있으므로 이 호출의 목적은 사실상 **이름 하나**다
     * — 서버가 uuid 외에 주는 건 [UserProfileInfo.name] 뿐이고, 그마저 null 일 수 있다.
     *
     * 로그인 상태에서만 부를 것. 미로그인으로 부르면 401 이 나고 [AuthError.SESSION_EXPIRED] 로 올라온다
     * — 재발급으로 풀리지 않는 401 이라(재시도할 토큰 자체가 없다) 화면 에러가 된다.
     *
     * 캐시하지 않는다. 매번 서버에 묻는다 — 화면이 필요할 때만 부르고, 결과는 화면이 보관한다.
     *
     * @throws com.moyeota.domain.model.AuthException
     *   [com.moyeota.domain.model.AuthError.SESSION_EXPIRED] (401) /
     *   [com.moyeota.domain.model.AuthError.NETWORK] (연결 실패) /
     *   [com.moyeota.domain.model.AuthError.UNKNOWN]
     */
    suspend fun getMyProfile(): UserProfileInfo
}
