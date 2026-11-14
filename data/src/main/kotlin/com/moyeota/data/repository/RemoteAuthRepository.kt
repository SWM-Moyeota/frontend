package com.moyeota.data.repository

import com.moyeota.data.local.ActivePartyStorage
import com.moyeota.data.push.FcmTokenRegistrar
import com.moyeota.data.remote.AuthApi
import com.moyeota.data.remote.SocialLoginOutcome
import com.moyeota.data.remote.UserApi
import com.moyeota.data.remote.auth.jwtSubject
import com.moyeota.data.remote.authCall
import com.moyeota.data.remote.dto.AccountDeletionRequest
import com.moyeota.data.remote.dto.GoogleLoginRequestDto
import com.moyeota.data.remote.dto.LoginRequestDto
import com.moyeota.data.remote.dto.NicknameCheckRequestDto
import com.moyeota.data.remote.dto.PasswordResetConfirmRequestDto
import com.moyeota.data.remote.dto.PasswordResetSendRequestDto
import com.moyeota.data.remote.dto.PasswordResetVerifyRequestDto
import com.moyeota.data.remote.dto.PhoneCheckRequestDto
import com.moyeota.data.remote.dto.RefreshTokenRequestDto
import com.moyeota.data.remote.dto.SmsSendRequestDto
import com.moyeota.data.remote.dto.SmsVerifyRequestDto
import com.moyeota.data.remote.dto.TokenResponse
import com.moyeota.data.remote.dto.UpdateProfileRequest
import com.moyeota.data.remote.toDomain
import com.moyeota.data.remote.toDto
import com.moyeota.data.remote.toOutcome
import com.moyeota.data.session.SessionManager
import com.moyeota.data.session.StoredSession
import com.moyeota.domain.model.AuthError
import com.moyeota.domain.model.AuthException
import com.moyeota.domain.model.AuthPolicy
import com.moyeota.domain.model.AuthState
import com.moyeota.domain.model.GoogleLoginResult
import com.moyeota.domain.model.NewSocialUser
import com.moyeota.domain.model.NewUser
import com.moyeota.domain.model.PasswordResetTicket
import com.moyeota.domain.model.ProfileImage
import com.moyeota.domain.model.UserProfileInfo
import com.moyeota.domain.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * API 가 둘인 건 실수가 아니다 — [api] 는 Bearer 가 붙지 않는 인증 전용 클라이언트,
 * [userApi] 는 Bearer + 401 재발급이 붙은 일반 클라이언트에서 만들어진다
 * ([com.moyeota.data.remote.NetworkModule] 참조). 프리픽스도 `/api/v1/auth` 와 `/api/v1/local` 로 다르다.
 *
 * [fcmTokenRegistrar] 가 여기 있는 이유는 **순서 때문이다.** 푸시 토큰 등록/해제는 Bearer 를
 * 요구하므로 세션이 열린 직후·닫히기 직전이라는 좁은 창에서만 가능하다. 화면이나 앱 계층에서
 * `logout()` 과 나란히 호출하게 두면 언젠가 순서가 뒤집혀 조용히 401 이 나므로, 세션을 여닫는
 * 이 클래스가 직접 끼워 넣는다. 등록 실패는 로그인/로그아웃 결과에 영향을 주지 않는다
 * ([FcmTokenRegistrar] 가 예외를 삼킨다).
 *
 * [activePartyStorage] 도 같은 이유로 여기 있다 — **탈퇴할 때만** 비우는 로컬 기억인데
 * ([deleteAccount]), 세션을 여닫는 지점이 여기라 "세션을 비우면서 무엇까지 지우는가"를 한곳에서
 * 결정해야 한다. 화면이나 app 계층에서 나란히 호출하게 두면 순서가 어긋나거나 한쪽이 빠진다.
 * [com.moyeota.domain.repository.ActivePartyRepository] 가 아니라 저장소 인터페이스를 직접 받는 건
 * 필요한 게 `clear()` 하나이고, 도메인 계약끼리 서로 의존하게 만들 이유가 없기 때문이다.
 */
class RemoteAuthRepository(
    private val api: AuthApi,
    private val userApi: UserApi,
    private val session: SessionManager,
    private val fcmTokenRegistrar: FcmTokenRegistrar,
    private val activePartyStorage: ActivePartyStorage,
) : AuthRepository {
    override suspend fun getLoginMethods() = authCall { userApi.getLoginMethods() }.toDomain()
    override suspend fun sendLoginMethodCode() { authCall { userApi.sendLoginMethodCode() } }
    override suspend fun verifyLoginMethodCode(code: String): com.moyeota.domain.model.LinkVerification {
        val response = authCall { userApi.verifyLoginMethodCode(com.moyeota.data.remote.dto.LinkCodeDto(code.trim())) }
        if (response.verificationToken.isBlank() || response.expiresInSeconds <= 0) {
            throw AuthException(AuthError.UNKNOWN, "문자 인증 응답을 확인할 수 없습니다.")
        }
        return com.moyeota.domain.model.LinkVerification(response.verificationToken, response.expiresInSeconds)
    }
    override suspend fun linkGoogle(idToken: String, verificationToken: String) {
        authCall { userApi.linkGoogle(com.moyeota.data.remote.dto.GoogleLinkDto(idToken, verificationToken)) }
    }


    // 세션 상태의 실체는 SessionManager 다. 여기서 별도 상태를 들면 두 출처가 어긋난다.
    override val authState: StateFlow<AuthState> = session.authState

    /**
     * 번호는 보내기 전에 숫자열로 정규화한다 — 발송·확인·가입이 **같은 형태**를 쓰게 하려는 것이고,
     * 근거는 [AuthPolicy.normalizePhoneNumber] KDoc 에 있다.
     *
     * 204 라 돌려받을 값이 없다. "문자가 실제로 도착했는가"는 앱이 알 방법이 없고,
     * 서버도 발송 사업자 호출이 실패하면 502 `USER201`([AuthError.SMS_UNAVAILABLE])로 알려 준다.
     */
    override suspend fun sendPhoneVerificationCode(phoneNumber: String) {
        authCall { api.sendSmsCode(SmsSendRequestDto(AuthPolicy.normalizePhoneNumber(phoneNumber))) }
    }

    /**
     * 빈 토큰을 끊는 이유는 [register]·[getMyProfile] 의 uuid 와 같다 — 여기서 통과시키면
     * 화면은 "인증 완료"로 넘어가고, 400 은 한참 뒤 가입 제출에서야 터진다.
     *
     * 코드는 다듬기만 하고 형식 검사는 하지 않는다. 입력 단계에서 막는 건 화면 몫이고
     * ([AuthPolicy.isValidVerificationCode]), 여기서 또 막으면 서버가 횟수를 세는
     * "틀린 시도"와 앱이 삼킨 시도가 어긋나 남은 횟수를 설명할 수 없게 된다.
     */
    override suspend fun verifyPhoneCode(phoneNumber: String, code: String): String {
        val response = authCall {
            api.verifySmsCode(
                SmsVerifyRequestDto(
                    phoneNumber = AuthPolicy.normalizePhoneNumber(phoneNumber),
                    code = code.trim(),
                ),
            )
        }
        return response.verificationToken.takeIf { it.isNotBlank() }
            ?: throw AuthException(AuthError.UNKNOWN, "인증 응답에 verificationToken 이 없습니다.")
    }

    override suspend fun register(newUser: NewUser): String {
        val response = authCall { api.register(newUser.toDto()) }
        // 서버가 uuid 를 반드시 채워 주지만, 빈 값이 오면 이후 화면이 조용히 깨지므로 여기서 끊는다.
        return response.uuid.takeIf { it.isNotBlank() }
            ?: throw AuthException(AuthError.UNKNOWN, "가입 응답에 uuid 가 없습니다.")
    }

    /** 세션을 여는 일은 전부 [openSession] 이 한다 — 구글 로그인·소셜 가입과 **같은 한 경로**다. */
    override suspend fun login(loginId: String, password: String): String {
        val response = authCall { api.login(LoginRequestDto(loginId, password)) }
        return openSession(response, source = "로그인")
    }

    /**
     * `status` 분기와 「그쪽에 있어야 할 값이 다 있는가」는 매퍼가 끝낸다([toOutcome]). 여기서 하는 일은
     * 하나다 — **LOGIN 일 때만 세션을 연다.**
     *
     * SIGNUP_REQUIRED 는 [session] 도 [fcmTokenRegistrar] 도 건드리지 않고 값만 돌려준다. 토큰이 없으니
     * 열 세션이 없고, 여기서 무엇이든 저장해 두면 가입을 끝내지 않고 나간 사람의 흔적이 기기에 남는다 —
     * 가입 대기표는 화면이 메모리로만 들고 다닌다([GoogleLoginResult.SignupRequired] KDoc).
     *
     * [idToken] 은 다듬지 않는다. 서명된 값이라 손대면 무효가 되고, 비었으면 서버가 400 으로 말해 준다.
     */
    override suspend fun loginWithGoogle(idToken: String): GoogleLoginResult {
        val response = authCall { api.loginWithGoogle(GoogleLoginRequestDto(idToken)) }
        return when (val outcome = response.toOutcome()) {
            is SocialLoginOutcome.Tokens ->
                GoogleLoginResult.LoggedIn(openSession(outcome.tokens, source = "구글 로그인"))
            is SocialLoginOutcome.SignupRequired -> outcome.result
        }
    }

    /**
     * [register] 와 달리 **성공한 자리에서 세션을 연다** — 201 이 uuid 가 아니라 토큰 쌍이다.
     *
     * 토큰이 비었거나 `sub` 를 못 읽으면 [openSession] 이 끊는다. 그때 서버에는 **계정이 이미 만들어져 있다**
     * (가입은 성공했고 응답만 못 읽은 것이다). 같은 대기표로 다시 제출하면 서버가 대기표를 이미 지웠으므로
     * 401 `USER402` 가 오고, 구글 로그인을 다시 하면 이번에는 LOGIN 으로 들어온다 — 즉 사용자가 갇히지는 않는다.
     * 여기서 스스로 구글 로그인을 다시 부르지 않는 건 부를 수 없어서다: idToken 은 화면(구글 SDK)만 갖고 있다.
     */
    override suspend fun registerSocial(newUser: NewSocialUser): String {
        val response = authCall { api.registerSocial(newUser.toDto()) }
        return openSession(response, source = "소셜 가입")
    }

    /**
     * 토큰 쌍으로 세션을 여는 **유일한 경로.** 로컬 로그인·구글 로그인(LOGIN)·소셜 가입이 전부 여기를 지난다.
     *
     * 한 곳에 모은 이유는 셋이 **똑같은 전이**를 해야 하기 때문이다 — 토큰 영속화,
     * [AuthState.Authenticated] 전이, 푸시 토큰 등록. 경로마다 따로 적어 두면 언젠가 하나가 빠지고,
     * 빠지는 건 대개 맨 끝의 푸시 등록이다: 로그인은 멀쩡히 되는데 **구글로 들어온 사람만 알림이 오지 않는**
     * 결함이 되고, 그건 화면에서 보이지 않는다.
     *
     * ### 사용자 UUID 는 응답이 아니라 토큰에서 읽는다
     * 세 응답 모두 토큰 쌍뿐이고 `userId` 가 없다 — 액세스 토큰의 `sub` 에서 읽는다
     * ([jwtSubject] KDoc 에 근거와 대안 비교).
     *
     * 파싱 실패를 예외로 끊는 이유: UUID 없이 세션을 열면 [AuthState.Authenticated] 의 식별자가
     * 빈 문자열이 되어 "로그인은 됐는데 내가 누군지 모르는" 상태가 조용히 굳는다.
     * 끊을 때는 **세션에 손대기 전에** 끊는다 — 실패한 호출이 반쪽짜리 세션을 남기지 않는다.
     *
     * @param source 실패 문구에 넣을 경로 이름. 셋이 같은 문구를 내면 로그에서 어느 경로인지 알 수 없다.
     */
    private suspend fun openSession(tokens: TokenResponse, source: String): String {
        if (tokens.accessToken.isBlank() || tokens.refreshToken.isBlank()) {
            throw AuthException(AuthError.UNKNOWN, "$source 응답에 토큰이 없습니다.")
        }

        val userUuid = jwtSubject(tokens.accessToken)
            ?: throw AuthException(AuthError.UNKNOWN, "$source 토큰에서 사용자 식별자를 읽지 못했습니다.")

        session.onLoggedIn(
            StoredSession(
                userUuid = userUuid,
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken,
            ),
        )

        // 세션이 열린 **뒤에야** 부를 수 있다 — Bearer 없이는 서버가 대상 사용자를 찾지 못한다.
        // 보류 중인 기기 토큰이 없으면 아무 일도 하지 않고, 실패해도 로그인은 그대로 성공이다.
        fcmTokenRegistrar.registerPendingToken()
        return userUuid
    }

    /**
     * ### 왜 [NonCancellable] 인가 — 「로그아웃했는데 안 됐다」를 막는다
     * 호출부는 `MainNavGraph` 의 `rememberCoroutineScope()` 라, 화면이 재구성되거나 액티비티가
     * 다시 만들어지면(화면 회전·시스템 종료) 이 코루틴이 **중간에 취소된다.** 그런데 아래 FCM 해제는
     * 최대 [FCM_UNREGISTER_TIMEOUT_MS] 동안 매달려 있고 [FcmTokenRegistrar.unregister] 는
     * [CancellationException] 만은 되던지므로, 그 취소가 [SessionManager.onLoggedOut] 까지
     * 도달하지 못하고 **세션이 그대로 남는다** — 사용자는 로그아웃된 줄 아는데 토큰은 살아 있다.
     * 공용 기기에서는 안전 문제다. 그래서 「해제 → 세션 비우기 → 서버 무효화」 한 묶음을
     * 취소되지 않는 구간으로 만든다. 길이는 FCM 2초 + 로그아웃 왕복으로 상한이 잡혀 있다.
     *
     * ### 순서는 그대로다 — FCM 해제가 세션 비우기보다 **앞**이다
     * "세션을 먼저 비우면 취소 문제가 사라지지 않나"는 함정이다. 서버는 `@CurrentUser` 로 대상을
     * 찾으므로 세션을 먼저 비우면 Bearer 가 사라져 해제가 **항상** 401 이 된다 — 즉 "가끔 남을 수도
     * 있다"가 아니라 "매번 남는다"가 된다. 서버는 `users.fcm_token` 한 칸에 사용자별로 토큰을
     * 들고 있고(`UserFcmTokenService`), 발송 실패 시 스스로 지우지도 않는다
     * (`FcmPassengerNotifier` 는 실패를 로그만 남긴다). 그 칸이 남아 있으면 **로그아웃한 계정의
     * 알림이 이 기기로 계속 오고**, 같은 기기에 다른 계정이 로그인하면 그 사람이 앞 사용자의 알림을
     * 보게 된다. 취소 문제는 [NonCancellable] 로 이미 막혔으니 순서를 희생할 이유가 없다.
     *
     * ### 상한을 거는 이유
     * 해제는 로그아웃 경로에 네트워크 호출을 하나 끼워 넣는 일이라, 서버가 죽어 있으면 OkHttp 기본
     * 타임아웃(연결+읽기 최대 20초)만큼 사용자가 버튼을 누른 채 기다리게 된다. "로그아웃은 절대
     * 막히지 않는다"는 계약을 지키려고 짧은 상한을 건다 — 초과하면 그냥 포기하고,
     * 남은 서버 토큰은 같은 계정이 다음에 로그인할 때 덮어써진다.
     */
    override suspend fun logout() {
        withContext(NonCancellable) {
            if (session.isLoggedIn) {
                // runCatching 으로 한 번 더 감싼다 — unregister 가 취소를 되던지므로, 감싸지 않으면
                // 그 예외가 아래 세션 비우기를 건너뛰게 만든다(이 메서드가 고치려는 결함 그 자체다).
                runCatching {
                    withTimeoutOrNull(FCM_UNREGISTER_TIMEOUT_MS) { fcmTokenRegistrar.unregister() }
                }
            }

            // 서버 무효화보다 먼저 비운다 — 서버 호출이 실패해도 사용자가 로그아웃하지 못하고 갇히면 안 된다.
            val refreshToken = session.onLoggedOut() ?: return@withContext

            // 서버 무효화는 최선 노력. 실패(오프라인 등)해도 예외를 올리지 않는다.
            try {
                api.logout(RefreshTokenRequestDto(refreshToken))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // 리프레시는 이미 로컬에서 지워졌고, 서버 쪽 토큰은 만료로 자연 소멸한다.
            }
        }
    }

    /**
     * 탈퇴. 204 를 받은 **자리에서** 로컬을 비운다.
     *
     * ### 순서가 로그아웃과 반대다
     * [logout] 은 로컬을 먼저 비우고 서버 무효화를 최선 노력으로 돌린다 — 서버가 죽어도 사용자가
     * 나갈 수 있어야 하기 때문이다. 탈퇴는 그럴 수 없다: **서버가 성공했는지가 유일한 진실**이고,
     * 미리 세션을 비우면 요청에 실을 Bearer 가 사라진다(`@CurrentUser` 가 대상을 못 찾아 401).
     * 그래서 서버 성공을 확인한 뒤에만 로컬을 지우고, 실패하면 세션을 **그대로 남긴다** —
     * 비밀번호를 틀렸거나 진행 중인 합승이 있는 사용자는 아무 일도 없었던 것처럼 계속 쓸 수 있어야 한다.
     *
     * ### 비우는 것 (로그아웃보다 하나 넓다)
     * 1. 토큰·uuid: [SessionManager.onLoggedOut] → 메모리 + 디스크(`moyeota_session`) +
     *    [com.moyeota.domain.model.AuthState.Unauthenticated] 전이
     * 2. **"진행 중인 방" 로컬 기억**(`moyeota_active_party`): [logout] 은 남기지만 여기서는 지운다.
     *    계정에 매인 값인데 그 계정이 사라졌으니 되살아날 길이 없고, 기기에 남겨 둘 이유도 없다.
     *
     * [onLoggedOut] 이 돌려주는 리프레시 토큰은 **쓰지 않는다** — 서버가 이미 전량 취소했으므로
     * `POST /auth/logout` 을 더 보내는 건 의미 없는 왕복이다.
     *
     * ### 기억을 먼저 지우는 이유
     * 세션보다 [activePartyStorage] 를 먼저 비운다. [SessionManager] 의 세션 비우기는
     * [com.moyeota.domain.model.AuthState] 전이를 **동기로** 일으켜 화면이 곧바로 로그인으로 넘어가는데,
     * 그 뒤에 디스크 쓰기를 남겨 두면 화면 전환에 묻혀 취소될 여지가 생긴다(기억 삭제는 디스크 I/O 다).
     * 순서를 뒤집어 두면 "탈퇴는 됐는데 방 기억만 남는" 상태가 나올 수 없다.
     */
    override suspend fun deleteAccount(password: String) {
        authCall { userApi.deleteAccount(AccountDeletionRequest(password)) }
        clearLocalAfterDeletion()
    }

    /**
     * 소셜 회원의 탈퇴. [deleteAccount] 와 다른 건 **요청 한 줄**(본문이 없다)뿐이고, 204 뒤의 정리는
     * [clearLocalAfterDeletion] 을 함께 쓴다 — 순서도 같다: 서버 성공을 확인한 뒤에만 로컬을 지우고,
     * 실패하면 세션을 그대로 남긴다(진행 중인 합승이 있는 사용자는 아무 일도 없었던 것처럼 계속 써야 한다).
     *
     * 비밀번호 인자를 nullable 로 바꿔 [deleteAccount] 하나로 합치지 않은 이유: 그러면 로컬 회원 화면이
     * 실수로 null 을 넘기는 것과 소셜 회원의 정상 호출이 **타입으로 구분되지 않는다.** 서버가 400 으로
     * 막아 주긴 하지만, 「비밀번호 없이 탈퇴」는 이름이 붙은 별도 동작이어야 호출부에서 눈에 띈다.
     */
    override suspend fun deleteSocialAccount() {
        authCall { userApi.deleteSocialAccount() }
        clearLocalAfterDeletion()
    }

    /**
     * 탈퇴 204 뒤의 로컬 정리. [deleteAccount]·[deleteSocialAccount] 가 함께 쓴다 —
     * 무엇을 어떤 순서로 비우는지는 [deleteAccount] KDoc 에 있다.
     */
    private suspend fun clearLocalAfterDeletion() {
        // 204 를 받은 뒤부터는 **취소되어서는 안 된다.** 서버 계정은 이미 사라졌는데 여기서 호출부
        // 스코프가 끊기면(화면 회전 등으로 액티비티 재생성) 앱만 로그인 상태로 남는다. 그러면 다음
        // 요청마다 401 을 맞고 재발급도 거절돼 「세션이 만료됐어요」가 뜬다 — **스스로 탈퇴한
        // 사용자에게는 거짓말이고**, 탈퇴가 실패한 것처럼 보여 다시 시도하게 만든다.
        // [logout] 과 같은 장치다.
        withContext(NonCancellable) {
            // 여기부터는 실패해도 되돌리지 않는다 — 서버 계정은 이미 사라졌다.
            //
            // CancellationException 을 되던지지 않는다: NonCancellable 안이라 지킬 구조적 동시성
            // 불변식이 없고, 되던지면 바로 아래 세션 비우기를 건너뛰어 이 블록의 목적을 깬다.
            try {
                activePartyStorage.clear()
            } catch (_: Throwable) {
                // 디스크 실패로 "탈퇴가 안 된 것처럼" 보이게 하지 않는다. 남은 기억은 다음 로그인 때
                // 계정 가드(ownerUuid 비교)가 걸러 낸다 — 최악은 쓰이지 않는 방 id 하나가 남는 것이다.
            }
            session.onLoggedOut()
        }
    }

    /**
     * 중복 확인은 **가입 전**, 즉 토큰이 없는 상태에서 불린다 — 그래서 [userApi] 가 아니라
     * Bearer 가 붙지 않는 [api] 쪽이다([com.moyeota.data.remote.AuthApi.checkNickname] KDoc 참조).
     *
     * 서버가 앞뒤 공백을 strip 한 뒤 검사하므로 보내기 전에 같은 기준으로 다듬는다
     * — 다듬지 않으면 `"모여타 "` 가 형식 오류가 아니라 "사용 가능"으로 잘못 보일 수 있다.
     */
    override suspend fun isNicknameTaken(nickname: String): Boolean =
        authCall { api.checkNickname(NicknameCheckRequestDto(nickname.trim())) }.exists

    /**
     * 가입 여부 확인도 **가입 전**, 즉 토큰이 없는 상태에서 불린다 — [isNicknameTaken] 과 같은 이유로
     * Bearer 가 붙지 않는 [api] 쪽이다([com.moyeota.data.remote.AuthApi.checkPhone] KDoc 참조).
     * 인증 클라이언트로 보내면 멀쩡한 번호가 401 로 떨어진다(실측: 깨진 Bearer → 401 `USER001`).
     *
     * 하이픈은 서버가 지우므로 그대로 보내도 되지만, 발송·확인·가입 세 경로가 보내는 번호와
     * 어긋나지 않게 [AuthPolicy.normalizePhoneNumber] 로 같은 형태로 맞춘다 — 어긋나면
     * 「발송은 되는데 선확인만 400」 같은, 번호 표기부터 의심해야 하는 결함이 된다.
     */
    override suspend fun isPhoneRegistered(phoneNumber: String): Boolean =
        authCall { api.checkPhone(PhoneCheckRequestDto(AuthPolicy.normalizePhoneNumber(phoneNumber))) }.exists

    /** 번호 정규화는 가입 인증과 같다 — 발송과 확인이 **같은 형태**를 보내야 서버가 같은 번호로 본다. */
    override suspend fun sendFindLoginIdCode(phoneNumber: String) {
        authCall { api.sendFindLoginIdCode(SmsSendRequestDto(AuthPolicy.normalizePhoneNumber(phoneNumber))) }
    }

    /**
     * 빈 아이디를 끊는 이유는 [verifyPhoneCode] 의 빈 토큰과 같다 — 통과시키면 화면이
     * 「회원님의 아이디는 예요」처럼 빈 값을 결과로 그린다.
     */
    override suspend fun findLoginId(phoneNumber: String, code: String): String {
        val response = authCall {
            api.findLoginId(
                SmsVerifyRequestDto(
                    phoneNumber = AuthPolicy.normalizePhoneNumber(phoneNumber),
                    code = code.trim(),
                ),
            )
        }
        return response.loginId.takeIf { it.isNotBlank() }
            ?: throw AuthException(AuthError.UNKNOWN, "아이디 찾기 응답에 loginId 가 없습니다.")
    }

    /**
     * 접수번호가 비면 끊는다 — 확인 단계가 빈 `requestId` 로 400 만 받게 되고, 사용자는 문자를 받고도
     * 인증을 끝낼 수 없다. 시간 값은 서버가 0 이하를 줘도 화면이 즉시 만료로 그리지 않게 정책 상수로 메운다.
     */
    override suspend fun requestPasswordReset(loginId: String, phoneNumber: String): PasswordResetTicket {
        val response = authCall {
            api.sendPasswordResetCode(
                PasswordResetSendRequestDto(
                    loginId = loginId.trim(),
                    phoneNumber = AuthPolicy.normalizePhoneNumber(phoneNumber),
                ),
            )
        }
        if (response.requestId.isBlank()) {
            throw AuthException(AuthError.UNKNOWN, "재설정 접수 응답에 requestId 가 없습니다.")
        }
        return PasswordResetTicket(
            requestId = response.requestId,
            expiresInSeconds = response.expiresIn.takeIf { it > 0 }?.toInt() ?: AuthPolicy.SMS_CODE_TTL_SECONDS,
            resendAfterSeconds = response.resendAfter.takeIf { it > 0 }?.toInt()
                ?: AuthPolicy.SMS_RESEND_COOLDOWN_SECONDS,
        )
    }

    override suspend fun verifyPasswordResetCode(requestId: String, code: String): String {
        val response = authCall {
            api.verifyPasswordResetCode(PasswordResetVerifyRequestDto(requestId = requestId, code = code.trim()))
        }
        return response.resetToken.takeIf { it.isNotBlank() }
            ?: throw AuthException(AuthError.UNKNOWN, "재설정 인증 응답에 resetToken 이 없습니다.")
    }

    /**
     * 비밀번호는 **다듬지 않는다** — 로그인이 입력값을 그대로 보내므로 여기서 공백을 깎으면
     * 사용자가 정한 비밀번호와 저장된 비밀번호가 달라진다.
     */
    override suspend fun confirmPasswordReset(resetToken: String, newPassword: String, newPasswordConfirm: String) {
        authCall {
            api.confirmPasswordReset(
                PasswordResetConfirmRequestDto(
                    resetToken = resetToken,
                    newPassword = newPassword,
                    newPasswordConfirm = newPasswordConfirm,
                ),
            )
        }
    }

    /** 204 라 돌려받을 값이 없다. 실패는 전부 [AuthException] 으로 좁혀 올린다. */
    override suspend fun updateProfile(nickname: String?, imageKey: String?) {
        authCall { userApi.updateProfile(UpdateProfileRequest(nickname?.trim(), imageKey)) }
    }

    /**
     * 서버 순서가 곧 표시 순서라 **정렬하지 않는다** — 섞으면 서버가 정한 추천 순서가 사라진다.
     *
     * 빈 목록도 정상 응답으로 통과시킨다(설정이 비었을 때). 화면이 "고를 게 없다"로 그리면 되고,
     * 여기서 예외로 바꾸면 멀쩡한 닉네임 수정까지 막힌다.
     */
    override suspend fun getProfileImages(): List<ProfileImage> =
        authCall { userApi.getProfileImages() }.map { it.toDomain() }

    /** 204 · 멱등이라 돌려받을 값도, 현재 상태를 미리 확인할 필요도 없다. */
    override suspend fun resetProfileImage() {
        authCall { userApi.resetProfileImage() }
    }

    /**
     * uuid 가 비면 끊는 이유는 [register] 와 같다 — 식별자 없는 "나"를 화면에 흘려보내면
     * 조용히 잘못된 상태로 굳는다. 반면 이름은 빈 값이 **정상 경우**라 그대로 통과시킨다
     * (닉네임·실명이 둘 다 없는 사용자). null 처리는 화면 몫이다.
     */
    override suspend fun getMyProfile(): UserProfileInfo {
        val response = authCall { userApi.getMyProfile() }
        if (response.uuid.isBlank()) {
            throw AuthException(AuthError.UNKNOWN, "내 정보 응답에 uuid 가 없습니다.")
        }
        return response.toDomain()
    }

    private companion object {
        /**
         * 로그아웃 시 FCM 해제에 허용하는 상한. 정상 응답은 로컬 서버 기준 수십 ms 이므로
         * 여유가 넉넉하면서도, 서버가 죽었을 때 사용자가 체감할 만큼 길지는 않은 값으로 잡았다.
         */
        const val FCM_UNREGISTER_TIMEOUT_MS = 2_000L
    }
}
