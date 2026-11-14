package com.moyeota.data.remote

import com.moyeota.data.remote.dto.ChatReportRequestDto
import com.moyeota.data.remote.dto.ReportRequestDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.HeaderMap
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
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
    fun `인증 엔드포인트는 전부 auth 프리픽스를 쓴다`() {
        assertEquals("api/v1/auth/register", AuthApi::class.java.path("register"))
        assertEquals("api/v1/auth/login", AuthApi::class.java.path("login"))
        assertEquals("api/v1/auth/reissue", AuthApi::class.java.path("reissue"))
        assertEquals("api/v1/auth/logout", AuthApi::class.java.path("logout"))
        assertEquals("api/v1/auth/nickname/check", AuthApi::class.java.path("checkNickname"))
        assertEquals("api/v1/auth/phone/check", AuthApi::class.java.path("checkPhone"))
    }

    /**
     * 전화번호 가입 여부 선확인. 닉네임 확인과 **생김새가 닮아 경로를 섞어 쓰기 쉽다**
     * (`/nickname/check` ↔ `/phone/check`) — `/api/v1/phone/check` 처럼 auth 프리픽스를 빠뜨리면
     * 404 가 아니라 **401** 로 떨어져(Security 가 미매핑 경로도 보호한다) 세션 만료로 오인한다.
     *
     * 동사도 함께 못 박는다: 서버가 `@PostMapping` 만 매핑하므로 `GET` 으로 적으면 **405** 인데
     * (실측 2026-11-01, 로컬 :8080), 405 는 앱 로그에서 네트워크 오류와 구분이 안 된다.
     * 그리고 선확인은 결과를 조용히 버리는 호출이라, 틀려도 **화면에 아무 증상이 없다.**
     */
    @Test
    fun `전화번호 가입 여부 확인은 auth phone check 경로의 POST 다`() {
        val check = AuthApi::class.java.declaredMethods.single { it.name == "checkPhone" }

        assertEquals("api/v1/auth/phone/check", AuthApi::class.java.path("checkPhone"))
        assertTrue("가입 여부 확인이 POST 가 아니다 — GET 이면 405 다", check.isAnnotationPresent(POST::class.java))
        assertNotEquals(
            "닉네임 확인과 같은 경로를 탄다 — 번호를 닉네임으로 묻는 셈이다",
            AuthApi::class.java.path("checkNickname"),
            AuthApi::class.java.path("checkPhone"),
        )
    }

    /**
     * 문자 인증 컨트롤러는 별 파일(`user/verification/presentation/SmsVerificationController.java`)이지만
     * 프리픽스가 `/api/v1/auth/sms` 다 — **그래야 permitAll 구간에 들어와 가입 전(무토큰)에 부를 수 있다.**
     * `/api/v1/sms/...` 처럼 적으면 404 가 아니라 **401** 이 나서(Security 가 미매핑 경로도 보호한다)
     * "세션이 만료됐나"로 오해하게 된다. 두 경로는 `POST` 이고, 발송만 204 다.
     */
    @Test
    fun `문자 인증 두 경로는 auth sms 프리픽스를 쓴다`() {
        assertEquals("api/v1/auth/sms/send", AuthApi::class.java.path("sendSmsCode"))
        assertEquals("api/v1/auth/sms/verify", AuthApi::class.java.path("verifySmsCode"))

        listOf("sendSmsCode", "verifySmsCode").forEach { name ->
            val method = AuthApi::class.java.declaredMethods.single { it.name == name }
            assertTrue("$name 이 POST 가 아니다", method.isAnnotationPresent(POST::class.java))
        }
    }

    /**
     * 닉네임 중복 확인은 **가입 도중, 즉 토큰이 없는 상태에서** 불린다. permitAll 구간이 `/api/v1/auth/`
     * 하위뿐이라 경로도 그쪽이어야 하고, **[AuthApi] 에 있어야 한다** — [UserApi] 는 Bearer 부착
     * 인터셉터와 401 재발급 Authenticator 가 붙은 클라이언트로 만들어지므로, 만료된 토큰이 세션에
     * 남아 있으면 permitAll 경로인데도 JWT 필터에 걸려 401 이 날 수 있다.
     * 그래서 "auth 프리픽스인데 UserApi 에 있는" 조합을 아예 막는다.
     */
    @Test
    fun `비보호 호출은 Bearer 가 붙지 않는 AuthApi 에만 있다`() {
        val misplaced = UserApi::class.java.declaredMethods
            .filter { it.pathOrNull()?.startsWith("api/v1/auth/") == true }
            .map { it.name }

        assertTrue("토큰 없이 부르는 경로가 인증 클라이언트 쪽에 있다: $misplaced", misplaced.isEmpty())
    }

    /**
     * 프로필 수정은 반대다 — 토큰 필수라 [UserApi] 여야 하고, 서버가 `@PatchMapping` 만 매핑하므로
     * PUT/POST 로 보내면 405 다(같은 `/users/me` 경로에 fcm-token 쪽 PUT 이 있어 헷갈리기 쉽다).
     */
    @Test
    fun `프로필 수정은 users me 경로의 PATCH 다`() {
        val update = UserApi::class.java.declaredMethods.single { it.name == "updateProfile" }

        assertEquals("api/v1/users/me", UserApi::class.java.path("updateProfile"))
        assertTrue("프로필 수정이 PATCH 가 아니다", update.isAnnotationPresent(PATCH::class.java))
    }

    /**
     * 내 정보 조회만 프리픽스가 `/api/v1/local` 이다 — 나머지 사용자 API(`/api/v1/users/me/...`)와 다르다.
     * 틀리면 404 가 아니라 **401** 이 나서(Security 가 미매핑 경로도 보호한다) 세션 만료로 오인한다.
     */
    @Test
    fun `내 정보 조회는 local 프리픽스를 쓴다`() {
        assertEquals("api/v1/local/users/info", UserApi::class.java.path("getMyProfile"))
    }

    /**
     * 반대로 푸시 토큰만 `/api/v1/users` 다 — 같은 [UserApi] 안에서 내 정보 조회(`/api/v1/local`)와
     * 프리픽스가 갈린다. 백엔드 컨트롤러가 실제로 둘로 나뉘어 있어서 생긴 비대칭이고
     * (`LocalUserController` vs `UserController`), 한쪽에 맞춰 통일하면 401 이 난다.
     */
    @Test
    fun `푸시 토큰 등록과 해제는 users me 경로를 쓴다`() {
        assertEquals("api/v1/users/me/fcm-token", UserApi::class.java.path("registerFcmToken"))
        assertEquals("api/v1/users/me/fcm-token", UserApi::class.java.path("deleteFcmToken"))
    }

    /**
     * 경로가 같고 동사만 다른 쌍이라 [path] 검사만으로는 POST 오타를 못 잡는다.
     * 서버는 `@PutMapping`/`@DeleteMapping` 만 매핑하므로 POST 로 보내면 405 가 난다.
     */
    @Test
    fun `푸시 토큰 등록은 PUT 해제는 DELETE 다`() {
        val register = UserApi::class.java.declaredMethods.single { it.name == "registerFcmToken" }
        val delete = UserApi::class.java.declaredMethods.single { it.name == "deleteFcmToken" }

        assertTrue("등록이 PUT 이 아니다", register.isAnnotationPresent(PUT::class.java))
        assertTrue("해제가 DELETE 가 아니다", delete.isAnnotationPresent(DELETE::class.java))
    }

    /**
     * 회원 탈퇴. `/users/me` 는 프로필 수정(PATCH)과 **경로가 같고 동사만 다르다** — [path] 검사만으로는
     * 둘이 뒤바뀌어도 통과하므로 `@HTTP` 의 세 값을 모두 못 박는다.
     *
     * `hasBody = true` 가 특히 중요하다. 서버가 비밀번호를 `@RequestBody` 로 받는데 Retrofit 의
     * `@DELETE` 는 본문을 못 실으므로(그 조합은 실행 시점 `IllegalArgumentException` 이다)
     * `@HTTP` 가 유일한 길이다. 실제로 본문이 실려 나가는지는
     * [UserApiDeleteBodyTest] 가 Retrofit 을 돌려 확인한다.
     *
     * 탈퇴가 `AuthApi` 로 옮겨 가는 것도 막는다 — 그쪽은 Bearer 가 붙지 않아 `@CurrentUser` 가
     * 대상을 못 찾고 401 `USER005` 가 된다.
     */
    @Test
    fun `회원 탈퇴는 users me 경로의 DELETE 이고 본문을 싣는다`() {
        val delete = UserApi::class.java.declaredMethods.single { it.name == "deleteAccount" }
        val http = delete.getAnnotation(HTTP::class.java)

        assertEquals("api/v1/users/me", UserApi::class.java.path("deleteAccount"))
        assertEquals("DELETE", http?.method)
        assertTrue("탈퇴 요청이 본문을 싣지 않는다 — 비밀번호를 보낼 자리가 없다", http?.hasBody == true)
        // @Body 파라미터가 하나. 비밀번호가 경로·쿼리로 새면 서버 액세스 로그에 평문으로 남는다.
        assertEquals(
            1,
            delete.parameterAnnotations.count { annotations -> annotations.any { it is Body } },
        )
        assertTrue(
            "탈퇴가 Bearer 없는 AuthApi 쪽으로 옮겨졌다 — 401 USER005 가 된다",
            AuthApi::class.java.methods.none { it.name == "deleteAccount" },
        )
    }

    /**
     * 구글 로그인과 소셜 가입은 **로그인 전(무토큰)에** 불린다 — 서버 컨트롤러(`SocialAuthController`)는
     * [AuthApi] 의 다른 메서드들과 파일이 다르지만 프리픽스가 `/api/v1/auth` 라 permitAll 구간에 들어온다.
     * 그래서 둘 다 **[AuthApi] 에 있어야 하고 [UserApi] 에 있으면 안 된다**: 인증 클라이언트로 보내면
     * 세션에 만료된 토큰이 남은 사용자만 permitAll 경로에서 401 을 받는다(`checkPhone` KDoc 의 실측과 같은 함정).
     *
     * 소셜 가입 경로는 로컬 가입과 한 조각 차이다(`/auth/register` ↔ `/auth/social/register`). 섞이면
     * 로컬 가입 검증에 걸려 400 `INVALID_REQUEST` 가 나는데, 그건 입력 검증 실패와 구분되지 않는다.
     *
     * 기준은 백엔드 `origin/develop` 의 `user/social/presentation/SocialAuthController.java` 소스다(실서버 미검증).
     */
    @Test
    fun `구글 로그인과 소셜 가입은 토큰 없이 부르는 AuthApi 의 POST 다`() {
        assertEquals("api/v1/auth/login/google", AuthApi::class.java.path("loginWithGoogle"))
        assertEquals("api/v1/auth/social/register", AuthApi::class.java.path("registerSocial"))

        listOf("loginWithGoogle", "registerSocial").forEach { name ->
            val method = AuthApi::class.java.declaredMethods.single { it.name == name }
            assertTrue("$name 이 POST 가 아니다", method.isAnnotationPresent(POST::class.java))
            assertTrue(
                "$name 이 Bearer 가 붙는 UserApi 쪽에도 있다",
                UserApi::class.java.methods.none { it.name == name },
            )
        }
        assertNotEquals(
            "소셜 가입이 로컬 가입과 같은 경로를 탄다 — loginId 누락 400 이 된다",
            AuthApi::class.java.path("register"),
            AuthApi::class.java.path("registerSocial"),
        )
        assertNotEquals(
            "구글 로그인이 아이디 로그인과 같은 경로를 탄다",
            AuthApi::class.java.path("login"),
            AuthApi::class.java.path("loginWithGoogle"),
        )
    }

    /**
     * 소셜 회원의 탈퇴는 비밀번호 탈퇴와 **경로·동사가 같고 본문만 없다**(서버 `@RequestBody(required = false)`).
     *
     * - 본문 파라미터가 붙으면 안 된다 — 소셜 회원에게는 실을 비밀번호가 없다.
     * - 경로가 `deleteAccount` 와 달라지면 안 된다 — 서버에 탈퇴 경로는 하나다.
     * - `AuthApi` 로 옮겨 가면 안 된다 — 이 호출의 본인 확인은 **Bearer 토큰 그 자체**다. 토큰이 붙지 않는
     *   클라이언트로 나가면 401 `USER005` 다.
     *
     * 실제 요청에 본문이 없는지는 [UserApiDeleteBodyTest] 가 Retrofit 을 돌려 확인한다.
     */
    @Test
    fun `소셜 회원 탈퇴는 비밀번호 탈퇴와 같은 경로의 DELETE 이고 본문이 없다`() {
        val delete = UserApi::class.java.declaredMethods.single { it.name == "deleteSocialAccount" }

        assertEquals("api/v1/users/me", UserApi::class.java.path("deleteSocialAccount"))
        assertEquals(UserApi::class.java.path("deleteAccount"), UserApi::class.java.path("deleteSocialAccount"))
        assertTrue("소셜 탈퇴가 DELETE 가 아니다", delete.isAnnotationPresent(DELETE::class.java))
        assertTrue(
            "소셜 탈퇴에 본문 파라미터가 붙었다 — 소셜 회원은 비밀번호가 없다",
            delete.parameterAnnotations.none { annotations -> annotations.any { it is Body } },
        )
        assertEquals("소셜 탈퇴에 파라미터가 생겼다(suspend 의 Continuation 하나만 있어야 한다)", 1, delete.parameterCount)
        assertTrue(
            "소셜 탈퇴가 Bearer 없는 AuthApi 쪽으로 옮겨졌다 — 토큰이 본인 확인인데 토큰이 안 붙는다",
            AuthApi::class.java.methods.none { it.name == "deleteSocialAccount" },
        )
    }

    /**
     * 프로필 이미지 두 경로는 **생김새가 닮아 바꿔 쓰기 쉽다** — 목록은 복수형에 `/me` 가 없고
     * (`api/v1/users/profile-images`), 되돌리기는 단수형에 `/me` 가 있다
     * (`api/v1/users/me/profile-image`). 둘을 뒤집으면 404 가 아니라 **401** 로 떨어져
     * (Security 가 미매핑 경로도 보호한다) 세션 만료로 오인한다.
     *
     * **되돌리기에서 뒤 조각이 빠지면 그건 회원 탈퇴다**(`DELETE /api/v1/users/me` — 이 파일의
     * 「회원 탈퇴는 users me 경로의 DELETE 이고 본문을 싣는다」 가 고정하는 그 경로다). 동사까지 같은
     * DELETE 라 요청은 204 로 성공하고, 사용자는 「기본 이미지로 바꿨다」고 생각하는데 **계정이 지워진다.**
     * 그래서 경로 값만이 아니라 「탈퇴와 다른 경로인가」를 따로 단언한다.
     *
     * 기준은 백엔드 `user/users/presentation/UserController.java` 소스다(실서버 미검증) —
     * 다시 바뀌면 이 테스트부터 깨지는 게 정상이다.
     */
    @Test
    fun `프로필 이미지 목록과 되돌리기는 서로 다른 경로를 쓴다`() {
        val list = UserApi::class.java.declaredMethods.single { it.name == "getProfileImages" }
        val reset = UserApi::class.java.declaredMethods.single { it.name == "resetProfileImage" }

        assertEquals("api/v1/users/profile-images", UserApi::class.java.path("getProfileImages"))
        assertEquals("api/v1/users/me/profile-image", UserApi::class.java.path("resetProfileImage"))

        assertTrue("목록 조회가 GET 이 아니다", list.isAnnotationPresent(GET::class.java))
        assertTrue("되돌리기가 DELETE 가 아니다", reset.isAnnotationPresent(DELETE::class.java))

        assertNotEquals(
            "되돌리기가 회원 탈퇴와 같은 경로를 탄다 — 같은 DELETE 라 성공하고 계정이 지워진다",
            UserApi::class.java.path("deleteAccount"),
            UserApi::class.java.path("resetProfileImage"),
        )
        // 본문 없는 탈퇴(소셜 회원)와는 **애노테이션까지 같아서** 경로만이 둘을 가른다.
        assertNotEquals(
            "되돌리기가 소셜 회원 탈퇴와 같은 경로를 탄다 — 본문 없는 DELETE 라 그대로 계정이 지워진다",
            UserApi::class.java.path("deleteSocialAccount"),
            UserApi::class.java.path("resetProfileImage"),
        )
        // 탈퇴처럼 본문을 싣는 우회(@HTTP hasBody)를 복사해 오면 서버 매핑과 어긋난다 — 되돌리기는 본문이 없다.
        assertTrue(
            "되돌리기에 본문 파라미터가 붙었다 — 서버는 본문 없는 DELETE 를 매핑한다",
            reset.parameterAnnotations.none { annotations -> annotations.any { it is Body } },
        )
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

    /**
     * 방 상세 경로에 **세그먼트가 붙지 않는 것**을 못박는다.
     *
     * 예전에는 `/status` 가 붙은 가벼운 상태 조회가 따로 있었고, 이 테스트는 둘을 혼동하지 않게
     * 두 경로를 함께 고정했다. 백엔드가 `GET /matching/rooms/{partyId}/status` 와 지문을
     * **삭제했으므로**(`04f6be7` — 매칭방 변화가 SSE 전용이 되어 폴링용 조회가 쓰이지 않게 됐다)
     * 지금은 상세 하나만 남는다. `/status` 를 되살리면 **404** 다.
     *
     * 25·26 의 주기 조회가 이 경로로 옮겨와 **폴링이 이 한 경로에 걸려 있다** — 틀리면 두 화면이
     * 배차·운행 전이를 영영 못 잡는다(그 전이에는 SSE 도 FCM 도 없다).
     */
    @Test
    fun `방 상세 조회 경로에는 뒤에 붙는 세그먼트가 없다`() {
        assertEquals("api/v1/matching/rooms/{partyId}", MatchingApi::class.java.path("getPartyDetail"))
        // 삭제된 상태 조회가 되살아나면 여기서 먼저 걸린다.
        assertTrue(
            "MatchingApi 에 상태 조회(/status) 메서드가 되살아났다 — 서버에 없는 경로다",
            MatchingApi::class.java.methods.none { it.name == "getPartyStatus" },
        )
    }

    @Test
    fun `기사 위치 조회는 partyId 만 담은 경로를 쓴다`() {
        assertEquals("api/v1/dispatch/rides/{partyId}", DispatchApi::class.java.path("getDriverLocation"))
    }

    @Test
    fun `즐겨찾기는 userId 없이 me 경로를 쓴다`() {
        assertEquals("api/v1/users/me/favorite-places", PlaceApi::class.java.path("addFavoritePlace"))
        assertEquals("api/v1/users/me/favorite-places", PlaceApi::class.java.path("getFavoritePlaces"))
        // 삭제 3종(실측 2026-11-01, 로컬 :8080). `all` 리터럴은 `{placeId}` 템플릿을 이긴다(실측 204).
        assertEquals(
            "api/v1/users/me/favorite-places/{placeId}",
            PlaceApi::class.java.path("deleteFavoritePlace"),
        )
        assertEquals(
            "api/v1/users/me/favorite-places/batch-delete",
            PlaceApi::class.java.path("deleteFavoritePlaces"),
        )
        assertEquals(
            "api/v1/users/me/favorite-places/all",
            PlaceApi::class.java.path("deleteAllFavoritePlaces"),
        )
    }

    /**
     * **다건 삭제는 POST 다.** 「삭제니까 DELETE 로 정리」하는 손길을 막는 것이 이 테스트의 전부다.
     *
     * 틀렸을 때의 실패 모양이 405 가 아니라 **400
     * `{"code":"INVALID_REQUEST","message":"placeId: 형식이 올바르지 않습니다."}`** 다(실측 2026-11-01,
     * 로컬 `:8080`) — `DELETE .../favorite-places/{placeId}` 매핑이 "batch-delete" 를 placeId 로 잡아
     * Long 변환에 실패한 것이다. 즉 **조용히 엉뚱한 엔드포인트로 가서** 「메서드 미지원」처럼 보이지
     * 않으므로, 디버깅이 본문·검증 쪽으로 샌다. `UserApi.deleteAccount` 의 `@HTTP(hasBody = true)` 를
     * 복사하면 바로 이 상태가 된다.
     */
    @Test
    fun `즐겨찾기 다건 삭제는 POST 다`() {
        val method = PlaceApi::class.java.declaredMethods.single { it.name == "deleteFavoritePlaces" }

        assertTrue("batch-delete 가 POST 가 아니다", method.isAnnotationPresent(POST::class.java))
        assertTrue(
            "batch-delete 가 @HTTP 로 선언됐다 — DELETE 로 나가면 400 INVALID_REQUEST placeId 다",
            !method.isAnnotationPresent(HTTP::class.java),
        )
    }

    // 백엔드가 이 경로를 `{reportId}` 유무로 오간 이력이 있다. 현행 컨트롤러
    // (report/interfaces/ReportController.java)는 reportId 없이 @CurrentUser 의 신고에 기록한다 —
    // 컨트롤러 소스 기준(실서버 미검증)이며, 다시 바뀌면 이 테스트부터 깨지는 게 정상이다.
    @Test
    fun `통화 결과 확정은 reportId 없는 경로를 쓴다`() {
        assertEquals("api/v1/reports/call-result", ReportApi::class.java.path("confirmCallResult"))
    }

    /**
     * 전환된 엔드포인트에 memberId/userId 가 되살아나는 걸 막는다.
     * 채팅이 마지막 예외였는데 백엔드가 `@CurrentUser` 로 넘기면서 **전 도메인이 대상이 됐다**
     * (방 생성은 본문 creatorId 를 떼면서 합류됐고, 본문 쪽은
     * `PartyMappersTest.생성 요청 본문에 방장 id 를 싣지 않는다` 가 지킨다).
     */
    @Test
    fun `전환된 API 어디에도 memberId userId 파라미터가 남아 있지 않다`() {
        val leftovers = listOf(
            MatchingApi::class.java,
            DispatchApi::class.java,
            PlaceApi::class.java,
            ReportApi::class.java,
            UserApi::class.java,
            ChatApi::class.java,
        )
            .flatMap { api -> api.declaredMethods.map { api.simpleName to it } }
            .filter { (_, method) -> method.hasParameterNamed("memberId", "userId") }
            .map { (api, method) -> "$api.${method.name}" }

        assertTrue("토큰이 정하는 주체를 앱이 다시 보내고 있다: $leftovers", leftovers.isEmpty())
    }

    /**
     * 채팅은 오랫동안 `@RequestHeader("X-User-Id")` 를 받는 유일한 도메인이었다. 토큰 주체와 헤더 id 가
     * 서로 검증되지 않아 **누가 로그인하든 고정 id 로 채팅이 나갔다** — 백엔드가 `@CurrentUser` 로
     * 넘어간 지금 헤더가 하나라도 되살아나면 그 버그가 그대로 돌아온다.
     *
     * 주체는 이제 Bearer 토큰뿐이고, 그건 [ChatApi] 가 apiClient(인터셉터가 Bearer 를 붙이고
     * 401 이면 재발급하는 클라이언트)로 생성되기 때문에 붙는다
     * ([NetworkModule.create] — chat 은 authClient 가 아니라 apiClient 쪽 retrofit 이다).
     */
    // 참여자 목록도 주체는 토큰이다 — 서버가 @CurrentUser 로 "부른 사람이 참여자인지"를 검사한다.
    // GET 이라 본문이 없고, 경로에 남길 수 있는 건 chatRoomId 뿐이어야 한다.
    @Test
    fun `참여자 목록 조회는 GET 이고 방 id 만 경로에 담는다`() {
        val members = ChatApi::class.java.declaredMethods.single { it.name == "getMembers" }

        assertTrue("참여자 목록이 GET 이 아니다", members.isAnnotationPresent(GET::class.java))
        assertEquals(1, members.parameterAnnotations.count { annotations -> annotations.any { it is Path } })
    }

    @Test
    fun `채팅 API 에는 헤더 파라미터가 하나도 없다`() {
        val withHeaders = ChatApi::class.java.declaredMethods
            .filter { method ->
                method.parameterAnnotations.any { annotations ->
                    annotations.any { it is Header || it is HeaderMap }
                }
            }
            .map { it.name }

        assertTrue("채팅이 사용자 id 를 헤더로 다시 싣고 있다: $withHeaders", withHeaders.isEmpty())
    }

    /**
     * 채팅만 경로 프리픽스가 `/api/v1/chat-rooms` 다. 메시지 조회/전송은 같은 경로에 동사만 다른 쌍이라
     * [path] 만으로는 오타를 못 잡아 검색·after 처럼 세그먼트가 붙는 쪽을 함께 못 박는다.
     */
    @Test
    fun `채팅 경로는 chat-rooms 프리픽스를 쓴다`() {
        assertEquals("api/v1/chat-rooms/me", ChatApi::class.java.path("getMyRooms"))
        assertEquals("api/v1/chat-rooms/{chatRoomId}/users", ChatApi::class.java.path("joinRoom"))
        // 참여자 목록은 합류/나가기와 경로가 같고 동사만 GET 이다 — 참여자만 부를 수 있어
        // 잘못 짚으면 404 가 아니라 403(CHAT_NOT_PARTICIPANT)처럼 보인다.
        assertEquals("api/v1/chat-rooms/{chatRoomId}/users", ChatApi::class.java.path("getMembers"))
        assertEquals(
            "api/v1/chat-rooms/{chatRoomId}/users/read/{readMessageId}",
            ChatApi::class.java.path("readRoom"),
        )
        assertEquals("api/v1/chat-rooms/{chatRoomId}/messages", ChatApi::class.java.path("getMessages"))
        assertEquals("api/v1/chat-rooms/{chatRoomId}/messages/after", ChatApi::class.java.path("getMessagesAfter"))
        assertEquals("api/v1/chat-rooms/{chatRoomId}/messages/search", ChatApi::class.java.path("searchMessages"))
        // 복수형 `reports` 다. `/report` 로 적으면 Security 가 미매핑 경로도 보호해서 404 가 아니라
        // **401** 로 떨어지고, 앱은 그걸 재발급 실패(세션 만료)로 읽는다 — 신고가 안 되는 게 아니라
        // 로그인이 풀린 것처럼 보인다.
        assertEquals("api/v1/chat-rooms/{chatRoomId}/reports", ChatApi::class.java.path("reportMessage"))
    }

    /**
     * 채팅 메시지 신고와 긴급 신고는 **다른 엔드포인트·다른 본문**이다. 둘을 한 DTO 로 합치려는
     * 시도를 막는다 — 긴급 신고에는 사유 필드가 없고(`ReportDtos.kt` KDoc: 「사유(reason) 필드는
     * 서버에 없다 — 임의로 추가하지 말 것」) 메시지 신고에는 `reason` 이 **필수**다. 섞으면
     * 서버가 400 `INVALID_REQUEST` 를 낸다(실측 2026-11-01).
     *
     * 이름이 비슷해서(`reportMessage` / `report`) 다음 사람이 「신고는 하나로 합치자」로 손대기
     * 쉬운 자리다. 그러면 사유가 붙은 본문이 `POST /api/v1/reports` 로 가거나 좌표가 메시지
     * 신고로 가서 **둘 다 400** 이 된다. 신고 대상도 다르다 — 그쪽은 파티, 이쪽은 메시지 1건이다.
     */
    @Test
    fun `메시지 신고와 긴급 신고는 서로 다른 본문 타입을 쓴다`() {
        val chatReport = ChatApi::class.java.declaredMethods.single { it.name == "reportMessage" }
        val emergency = ReportApi::class.java.declaredMethods.single { it.name == "report" }

        assertEquals(
            "메시지 신고 본문이 ChatReportRequestDto 가 아니다",
            ChatReportRequestDto::class.java,
            chatReport.bodyType(),
        )
        assertEquals(
            "긴급 신고 본문이 ReportRequestDto 가 아니다",
            ReportRequestDto::class.java,
            emergency.bodyType(),
        )
        assertNotEquals(chatReport.bodyType(), emergency.bodyType())

        // 메시지 신고는 **방 id 만** 경로에 담는다 — 신고자는 토큰이, 피신고자는 서버가 메시지에서
        // 찾는다. 경로에 사용자 id 가 끼면 「전환된 API 어디에도 memberId userId 가 없다」와 충돌한다.
        assertEquals(1, chatReport.parameterAnnotations.count { annotations -> annotations.any { it is Path } })
    }

    /** `@Body` 가 붙은 파라미터의 타입. 없으면 null. */
    private fun Method.bodyType(): Class<*>? =
        parameterAnnotations.indexOfFirst { annotations -> annotations.any { it is Body } }
            .takeIf { it >= 0 }
            ?.let { parameterTypes[it] }

    /**
     * 탑승 기록(2026-11-01 신규). 경로가 **`/api/v1/users/me/parties`** 다 — 같은 [UserApi] 안에
     * 프리픽스가 셋(`/api/v1/local`·`/api/v1/users`·`/api/v1/auth` 금지)이라 가장 틀리기 쉬운 자리고,
     * 틀리면 404 가 아니라 **401** 로 떨어져 세션 만료로 오인한다(Security 가 미매핑 경로도 보호한다).
     *
     * 특히 `/api/v1/local/users/parties`(내 정보 조회의 프리픽스를 복사)와
     * `/api/v1/matching/users/me/parties`(「방 목록」으로 읽고 매칭 쪽에 두는 실수)를 값으로 막는다.
     *
     * `@Query` 가 **`cursor`·`size` 둘뿐**인 것도 함께 못 박는다. `status` 를 붙이는 실수가
     * 특히 위험하다 — 서버에 그 파라미터가 없는데 400 이 아니라 **200 + 조용히 무시**라서
     * (실측 2026-11-01: `?status=ACTIVE` → 200, 결과 동일) 「필터가 동작한다」고 착각하고 지나간다.
     */
    @Test
    fun `탑승 기록 조회는 users me parties 경로의 GET 이고 쿼리가 cursor size 둘뿐이다`() {
        val records = UserApi::class.java.declaredMethods.single { it.name == "getMyRideRecords" }

        assertEquals("api/v1/users/me/parties", UserApi::class.java.path("getMyRideRecords"))
        assertTrue("탑승 기록 조회가 GET 이 아니다", records.isAnnotationPresent(GET::class.java))

        val queries = records.parameterAnnotations
            .flatMap { annotations -> annotations.filterIsInstance<Query>().map { it.value } }
        assertEquals(listOf("cursor", "size"), queries)
    }

    private fun Class<*>.path(methodName: String): String {
        val method = declaredMethods.singleOrNull { it.name == methodName }
            ?: error("$simpleName 에 $methodName 이 없거나 오버로드가 여러 개다")
        return method.pathOrNull() ?: error("$simpleName.$methodName 에 HTTP 애노테이션이 없다")
    }

    /**
     * `@HTTP` 도 읽는다. 읽지 않으면 그 애노테이션을 쓴 메서드는 아래 「auth 프리픽스가 UserApi 에
     * 있으면 안 된다」 같은 전수 검사에서 **조용히 빠진다** — 경로 오타를 잡으려고 만든 검사가
     * 정작 특수 케이스만 못 보는 꼴이 된다(탈퇴가 그 특수 케이스다).
     */
    private fun Method.pathOrNull(): String? =
        getAnnotation(GET::class.java)?.value
            ?: getAnnotation(POST::class.java)?.value
            ?: getAnnotation(PUT::class.java)?.value
            ?: getAnnotation(DELETE::class.java)?.value
            ?: getAnnotation(PATCH::class.java)?.value
            ?: getAnnotation(HTTP::class.java)?.path

    private fun Method.hasParameterNamed(vararg names: String): Boolean =
        parameterAnnotations.any { annotations ->
            annotations.any { annotation ->
                val value = (annotation as? Path)?.value ?: (annotation as? Query)?.value
                value in names
            }
        }
}
