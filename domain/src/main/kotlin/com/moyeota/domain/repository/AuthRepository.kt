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
     * 회원가입. POST /api/v1/auth/register (201).
     *
     * **가입은 로그인시키지 않는다** — 서버가 토큰이 아니라 uuid 만 준다.
     * 성공 후 화면은 같은 자격증명으로 [login] 을 이어서 호출해야 한다.
     *
     * @return 생성된 사용자의 UUID 문자열
     * @throws com.moyeota.domain.model.AuthException
     *   [com.moyeota.domain.model.AuthError.LOGIN_ID_DUPLICATED] (409) /
     *   [com.moyeota.domain.model.AuthError.INVALID_REQUEST] (400, serverMessage 가 "필드: 사유")
     */
    suspend fun register(newUser: NewUser): String

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
