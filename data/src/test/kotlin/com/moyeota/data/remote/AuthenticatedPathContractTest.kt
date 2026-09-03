package com.moyeota.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.lang.reflect.Method

/**
 * 백엔드가 `@CurrentUser` 로 넘어가면서 경로에서 memberId/userId 가 빠졌다.
 * 이 계층은 Retrofit 애노테이션 문자열이라 **컴파일러도 단위 테스트도 그냥은 못 잡는다** —
 * 틀리면 실행 시점에 404(또는 401)로만 드러난다. 그래서 애노테이션 값 자체를 리플렉션으로 못 박는다.
 *
 * 경로는 전부 실서버(localhost:8080) curl 실측으로 확인한 값이다.
 * 백엔드가 다시 바꾸면 이 테스트부터 깨지는 게 정상이다 — 값을 고치고 리포트를 갱신할 것.
 */
class AuthenticatedPathContractTest {

    /**
     * 인증 개편으로 가입·로그인이 `/api/v1/users` 계열에서 `/api/v1/auth/` 하위로 옮겨졌다.
     * 옛 경로는 지금 Security 가 보호 경로로 잡아 **401 을 낸다** — 404 가 아니라서
     * "아이디/비밀번호가 틀렸나?"로 오해하기 딱 좋다. 그래서 네 경로를 모두 못 박는다.
     */
    @Test
    fun `인증 네 엔드포인트는 전부 auth 프리픽스를 쓴다`() {
        assertEquals("api/v1/auth/register", AuthApi::class.java.path("register"))
        assertEquals("api/v1/auth/login", AuthApi::class.java.path("login"))
        assertEquals("api/v1/auth/reissue", AuthApi::class.java.path("reissue"))
        assertEquals("api/v1/auth/logout", AuthApi::class.java.path("logout"))
    }

    /**
     * 내 정보 조회만 프리픽스가 `/api/v1/local` 이다 — 나머지 사용자 API(`/api/v1/users/me/...`)와 다르다.
     * 틀리면 404 가 아니라 **401** 이 나서(Security 가 미매핑 경로도 보호한다) 세션 만료로 오인한다.
     */
    @Test
    fun `내 정보 조회는 local 프리픽스를 쓴다`() {
        assertEquals("api/v1/local/users/info", UserApi::class.java.path("getMyProfile"))
    }

    @Test
    fun `합류는 partyId 만 담은 경로를 쓴다`() {
        assertEquals("api/v1/matching/rooms/{partyId}/join", MatchingApi::class.java.path("joinParty"))
    }

    /**
     * 예전에는 서버 매핑에 쓰이지 않는 `{memberId}` 세그먼트가 남아 있어 더미 `/0` 을 박아야 했다.
     * 백엔드가 매핑을 `/matching/leave/{partyId}` 로 정리해 그 잔재가 사라졌다
     * (실측: `/leave/{id}` → 204, `/leave/{id}/0` → 404). 되살아나지 않게 값을 고정한다.
     */
    @Test
    fun `나가기 경로에 더미 세그먼트가 없다`() {
        assertEquals("api/v1/matching/leave/{partyId}", MatchingApi::class.java.path("leaveParty"))
    }

    @Test
    fun `기사 위치 조회는 partyId 만 담은 경로를 쓴다`() {
        assertEquals("api/v1/dispatch/rides/{partyId}", DispatchApi::class.java.path("getDriverLocation"))
    }

    @Test
    fun `즐겨찾기는 userId 없이 me 경로를 쓴다`() {
        assertEquals("api/v1/users/me/favorite-places", PlaceApi::class.java.path("addFavoritePlace"))
        assertEquals("api/v1/users/me/favorite-places", PlaceApi::class.java.path("getFavoritePlaces"))
    }

    // 백엔드가 한 번 `/reports/call-result` 로 옮겼다가 되돌린 자리다 — reportId 가 경로에 남아 있어야 한다.
    @Test
    fun `통화 결과 확정은 reportId 를 경로에 유지한다`() {
        assertEquals("api/v1/reports/{reportId}/call-result", ReportApi::class.java.path("confirmCallResult"))
    }

    /**
     * 전환된 엔드포인트에 memberId/userId 가 되살아나는 걸 막는다.
     * 채팅(X-User-Id 헤더)만 아직 전환 전이라 이 검사 대상이 아니다 —
     * 방 생성은 본문 creatorId 를 떼면서 합류됐다
     * (본문 쪽은 `PartyMappersTest.생성 요청 본문에 방장 id 를 싣지 않는다` 가 지킨다).
     */
    @Test
    fun `전환된 API 어디에도 memberId userId 파라미터가 남아 있지 않다`() {
        val leftovers = listOf(
            MatchingApi::class.java,
            DispatchApi::class.java,
            PlaceApi::class.java,
            ReportApi::class.java,
            UserApi::class.java,
        )
            .flatMap { api -> api.declaredMethods.map { api.simpleName to it } }
            .filter { (_, method) -> method.hasParameterNamed("memberId", "userId") }
            .map { (api, method) -> "$api.${method.name}" }

        assertTrue("토큰이 정하는 주체를 앱이 다시 보내고 있다: $leftovers", leftovers.isEmpty())
    }

    private fun Class<*>.path(methodName: String): String {
        val method = declaredMethods.singleOrNull { it.name == methodName }
            ?: error("$simpleName 에 $methodName 이 없거나 오버로드가 여러 개다")
        return method.getAnnotation(GET::class.java)?.value
            ?: method.getAnnotation(POST::class.java)?.value
            ?: method.getAnnotation(DELETE::class.java)?.value
            ?: method.getAnnotation(PATCH::class.java)?.value
            ?: error("$simpleName.$methodName 에 HTTP 애노테이션이 없다")
    }

    private fun Method.hasParameterNamed(vararg names: String): Boolean =
        parameterAnnotations.any { annotations ->
            annotations.any { annotation ->
                val value = (annotation as? Path)?.value ?: (annotation as? Query)?.value
                value in names
            }
        }
}
