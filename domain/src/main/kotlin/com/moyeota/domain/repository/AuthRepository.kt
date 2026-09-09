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
     * [NewUser.nickname] 은 **필수다** — 서버가 닉네임 없이는 가입을 받지 않는다.
     *
     * @return 생성된 사용자의 UUID 문자열
     * @throws com.moyeota.domain.model.AuthException
     *   [com.moyeota.domain.model.AuthError.LOGIN_ID_DUPLICATED] (409) /
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
