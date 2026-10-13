package com.moyeota.data.repository

import com.moyeota.data.local.ActivePartyStorage
import com.moyeota.data.push.FcmTokenRegistrar
import com.moyeota.data.remote.AuthApi
import com.moyeota.data.remote.UserApi
import com.moyeota.data.remote.auth.jwtSubject
import com.moyeota.data.remote.authCall
import com.moyeota.data.remote.dto.AccountDeletionRequest
import com.moyeota.data.remote.dto.LoginRequestDto
import com.moyeota.data.remote.dto.NicknameCheckRequestDto
import com.moyeota.data.remote.dto.RefreshTokenRequestDto
import com.moyeota.data.remote.dto.SmsSendRequestDto
import com.moyeota.data.remote.dto.SmsVerifyRequestDto
import com.moyeota.data.remote.dto.UpdateProfileRequest
import com.moyeota.data.remote.toDomain
import com.moyeota.data.remote.toDto
import com.moyeota.data.session.SessionManager
import com.moyeota.data.session.StoredSession
import com.moyeota.domain.model.AuthError
import com.moyeota.domain.model.AuthException
import com.moyeota.domain.model.AuthPolicy
import com.moyeota.domain.model.AuthState
import com.moyeota.domain.model.NewUser
import com.moyeota.domain.model.UserProfileInfo
import com.moyeota.domain.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
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

    /**
     * 로그인 응답에는 더 이상 `userId` 가 없다 — 사용자 UUID 는 액세스 토큰의 `sub` 에서 읽는다
     * ([jwtSubject] KDoc 에 근거와 대안 비교).
     *
     * 파싱 실패를 예외로 끊는 이유: UUID 없이 세션을 열면 [AuthState.Authenticated] 의 식별자가
     * 빈 문자열이 되어 "로그인은 됐는데 내가 누군지 모르는" 상태가 조용히 굳는다.
     */
    override suspend fun login(loginId: String, password: String): String {
        val response = authCall { api.login(LoginRequestDto(loginId, password)) }
        if (response.accessToken.isBlank() || response.refreshToken.isBlank()) {
            throw AuthException(AuthError.UNKNOWN, "로그인 응답에 토큰이 없습니다.")
        }

        val userUuid = jwtSubject(response.accessToken)
            ?: throw AuthException(AuthError.UNKNOWN, "로그인 토큰에서 사용자 식별자를 읽지 못했습니다.")

        session.onLoggedIn(
            StoredSession(
                userUuid = userUuid,
                accessToken = response.accessToken,
                refreshToken = response.refreshToken,
            ),
        )

        // 세션이 열린 **뒤에야** 부를 수 있다 — Bearer 없이는 서버가 대상 사용자를 찾지 못한다.
        // 보류 중인 기기 토큰이 없으면 아무 일도 하지 않고, 실패해도 로그인은 그대로 성공이다.
        fcmTokenRegistrar.registerPendingToken()
        return userUuid
    }

    override suspend fun logout() {
        // 세션을 비우기 **전에** 해제한다 — 순서가 뒤집히면 Bearer 가 사라져 401 이 나고,
        // 서버에는 죽은 토큰이 남아 다음 사용자에게 갈 알림이 이 기기로 온다.
        //
        // 다만 이건 로그아웃 경로에 네트워크 호출을 하나 끼워 넣는 일이라, 서버가 죽어 있으면
        // OkHttp 기본 타임아웃(연결+읽기 최대 20초)만큼 사용자가 버튼을 누른 채 기다리게 된다.
        // "로그아웃은 절대 막히지 않는다"는 계약을 지키려고 짧은 상한을 건다 — 초과하면 그냥 포기한다
        // (서버 토큰은 다음 로그인 때 덮어써지거나, 발송 실패로 FCM 이 스스로 정리한다).
        if (session.isLoggedIn) {
            withTimeoutOrNull(FCM_UNREGISTER_TIMEOUT_MS) { fcmTokenRegistrar.unregister() }
        }

        // 로컬을 먼저 비운다 — 서버 호출이 실패해도 사용자가 로그아웃하지 못하고 갇히면 안 된다.
        val refreshToken = session.onLoggedOut() ?: return

        // 서버 무효화는 최선 노력. 실패(오프라인 등)해도 예외를 올리지 않는다.
        try {
            api.logout(RefreshTokenRequestDto(refreshToken))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // 리프레시는 이미 로컬에서 지워졌고, 서버 쪽 토큰은 만료로 자연 소멸한다.
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

        // 여기부터는 실패해도 되돌리지 않는다 — 서버 계정은 이미 사라졌다.
        try {
            activePartyStorage.clear()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // 디스크 실패로 "탈퇴가 안 된 것처럼" 보이게 하지 않는다. 남은 기억은 다음 로그인 때
            // 계정 가드(ownerUuid 비교)가 걸러 낸다 — 최악은 쓰이지 않는 방 id 하나가 남는 것이다.
        }
        session.onLoggedOut()
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

    /** 204 라 돌려받을 값이 없다. 실패는 전부 [AuthException] 으로 좁혀 올린다. */
    override suspend fun updateProfile(nickname: String?, imageUrl: String?) {
        authCall { userApi.updateProfile(UpdateProfileRequest(nickname?.trim(), imageUrl)) }
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
