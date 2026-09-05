package com.moyeota.data.repository

import com.moyeota.data.push.FcmTokenRegistrar
import com.moyeota.data.remote.AuthApi
import com.moyeota.data.remote.UserApi
import com.moyeota.data.remote.auth.jwtSubject
import com.moyeota.data.remote.authCall
import com.moyeota.data.remote.dto.LoginRequestDto
import com.moyeota.data.remote.dto.RefreshTokenRequestDto
import com.moyeota.data.remote.toDomain
import com.moyeota.data.remote.toDto
import com.moyeota.data.session.SessionManager
import com.moyeota.data.session.StoredSession
import com.moyeota.domain.model.AuthError
import com.moyeota.domain.model.AuthException
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
 */
class RemoteAuthRepository(
    private val api: AuthApi,
    private val userApi: UserApi,
    private val session: SessionManager,
    private val fcmTokenRegistrar: FcmTokenRegistrar,
) : AuthRepository {

    // 세션 상태의 실체는 SessionManager 다. 여기서 별도 상태를 들면 두 출처가 어긋난다.
    override val authState: StateFlow<AuthState> = session.authState

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
