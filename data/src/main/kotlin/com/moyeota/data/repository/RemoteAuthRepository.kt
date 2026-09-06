package com.moyeota.data.repository

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

/**
 * API 가 둘인 건 실수가 아니다 — [api] 는 Bearer 가 붙지 않는 인증 전용 클라이언트,
 * [userApi] 는 Bearer + 401 재발급이 붙은 일반 클라이언트에서 만들어진다
 * ([com.moyeota.data.remote.NetworkModule] 참조). 프리픽스도 `/api/v1/auth` 와 `/api/v1/local` 로 다르다.
 */
class RemoteAuthRepository(
    private val api: AuthApi,
    private val userApi: UserApi,
    private val session: SessionManager,
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
        return userUuid
    }

    override suspend fun logout() {
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
}
