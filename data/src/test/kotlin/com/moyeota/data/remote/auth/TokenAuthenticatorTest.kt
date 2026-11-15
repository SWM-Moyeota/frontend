package com.moyeota.data.remote.auth

import com.moyeota.data.session.TokenHolder
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class TokenAuthenticatorTest {
    @Test fun `연결 증표 만료 401은 세션을 갱신하거나 지우지 않는다`() {
        val tokens = FakeTokenHolder("access", "refresh")
        val refresher = FakeRefresher(RefreshResult.Rejected)
        val response = unauthorized("access").newBuilder()
            .body("""{"code":"USER406","message":"expired"}""".toResponseBody()).build()
        assertNull(TokenAuthenticator(tokens, refresher).authenticate(null, response))
        assertEquals(0, refresher.callCount.get())
        assertEquals(0, tokens.sessionExpiredCount)
    }

    @Test fun `공급자 토큰 거절과 인증 요청 무효 401도 재발급 없이 호출부로 넘긴다`() {
        listOf("USER401", "USER407").forEach { code ->
            val tokens = FakeTokenHolder("access", "refresh")
            val refresher = FakeRefresher(RefreshResult.Rejected)
            val response = unauthorized("access").newBuilder()
                .body("""{"code":"$code","message":"rejected"}""".toResponseBody()).build()
            assertNull(code, TokenAuthenticator(tokens, refresher).authenticate(null, response))
            assertEquals(code, 0, refresher.callCount.get())
            assertEquals(code, 0, tokens.sessionExpiredCount)
            assertEquals(code, "access", tokens.accessToken)
        }
    }

    @Test fun `연결 경로라도 액세스 토큰 만료 401이면 정상 재발급한다`() {
        val tokens = FakeTokenHolder("old.access", "old.refresh")
        val refresher = FakeRefresher(rotated("new.access", "new.refresh"))
        val request = Request.Builder().url("http://localhost:8080/api/v1/users/me/login-methods/google")
            .header("Authorization", "Bearer old.access").build()
        val response = unauthorized("old.access").newBuilder().request(request)
            .body("""{"code":"USER002","message":"expired"}""".toResponseBody()).build()

        val retry = TokenAuthenticator(tokens, refresher).authenticate(null, response)

        assertEquals("Bearer new.access", retry?.header("Authorization"))
        assertEquals("http://localhost:8080/api/v1/users/me/login-methods/google", retry?.url.toString())
        assertEquals(1, refresher.callCount.get())
        assertEquals("new.refresh", tokens.refreshToken)
        assertEquals(0, tokens.sessionExpiredCount)
    }

    @Test
    fun `401 이면 재발급한 액세스 토큰으로 원요청을 재시도한다`() {
        val tokens = FakeTokenHolder(accessToken = "old.access", refreshToken = "old.refresh")
        val refresher = FakeRefresher(rotated("new.access", "new.refresh"))

        val retry = TokenAuthenticator(tokens, refresher).authenticate(null, unauthorized("old.access"))

        assertNotNull("재시도 요청이 만들어져야 한다", retry)
        assertEquals("Bearer new.access", retry!!.header("Authorization"))
        // 경로/메서드는 원요청 그대로여야 한다.
        assertEquals("http://localhost:8080/api/v1/matching/rooms", retry.url.toString())
        assertEquals(listOf("old.refresh"), refresher.usedRefreshTokens)
    }

    @Test
    fun `재발급 성공 시 회전된 리프레시까지 저장한다`() {
        val tokens = FakeTokenHolder(accessToken = "old.access", refreshToken = "old.refresh")
        val refresher = FakeRefresher(rotated("new.access", "new.refresh"))

        TokenAuthenticator(tokens, refresher).authenticate(null, unauthorized("old.access"))

        assertEquals("new.access", tokens.accessToken)
        // 옛 리프레시는 서버에서 이미 무효화됐다 — 저장하지 않으면 다음 재발급이 401 이다.
        assertEquals("new.refresh", tokens.refreshToken)
        assertEquals(0, tokens.sessionExpiredCount)
    }

    /** 서버가 "이 리프레시는 못 쓴다"고 답한 경우(401·403·USER001~005). 재로그인 외에 길이 없다. */
    @Test
    fun `서버가 재발급을 거절하면 세션 만료를 알리고 재시도를 포기한다`() {
        val tokens = FakeTokenHolder(accessToken = "old.access", refreshToken = "revoked.refresh")
        val refresher = FakeRefresher(RefreshResult.Rejected)

        val retry = TokenAuthenticator(tokens, refresher).authenticate(null, unauthorized("old.access"))

        assertNull(retry)
        assertEquals(1, tokens.sessionExpiredCount)
        assertNull(tokens.accessToken)
        assertNull(tokens.refreshToken)
    }

    /**
     * **이 테스트가 이번 수정의 핵심이다.** 운영 액세스 토큰 수명이 30분이라 활성 사용자는 30분마다
     * 반드시 재발급을 한 번 타는데, 그 순간 지하철·엘리베이터에 있거나 서버 배포 중 503 을 맞으면
     * 예전 구현은 「응답을 못 받았다」를 「서버가 거절했다」와 똑같이 보고 멀쩡한 세션을 지웠다
     * — 서버에서는 아무 일도 없었고 리프레시는 14일 수명 그대로 유효한데 「세션이 만료됐어요」가 떴다.
     *
     * 세션을 유지한 채 null 을 돌려주면 401 이 호출부로 올라가고, 다음 요청이 재발급을 다시 시도한다.
     */
    @Test
    fun `재발급 응답을 못 받으면 세션을 유지한 채 재시도만 포기한다`() {
        val tokens = FakeTokenHolder(accessToken = "old.access", refreshToken = "live.refresh")
        val refresher = FakeRefresher(RefreshResult.Unavailable)

        val retry = TokenAuthenticator(tokens, refresher).authenticate(null, unauthorized("old.access"))

        assertNull("답을 못 받았으면 재시도할 토큰이 없다", retry)
        assertEquals("네트워크 실패로 세션을 지웠다", 0, tokens.sessionExpiredCount)
        // 다음 요청이 같은 리프레시로 재발급을 다시 시도할 수 있어야 한다.
        assertEquals("old.access", tokens.accessToken)
        assertEquals("live.refresh", tokens.refreshToken)
    }

    @Test
    fun `리프레시 토큰이 없으면 재발급을 시도하지 않고 세션을 만료시킨다`() {
        val tokens = FakeTokenHolder(accessToken = "orphan.access", refreshToken = null)
        val refresher = FakeRefresher(rotated("new.access", "new.refresh"))

        val retry = TokenAuthenticator(tokens, refresher).authenticate(null, unauthorized("orphan.access"))

        assertNull(retry)
        assertEquals(0, refresher.callCount.get())
        assertEquals(1, tokens.sessionExpiredCount)
    }

    @Test
    fun `토큰을 싣지 않은 요청의 401 은 건드리지 않는다`() {
        val tokens = FakeTokenHolder(accessToken = "any.access", refreshToken = "any.refresh")
        val refresher = FakeRefresher(rotated("new.access", "new.refresh"))

        // 미로그인 상태에서 보호 API 를 호출한 경우 — 재발급으로 풀 수 있는 문제가 아니다.
        val retry = TokenAuthenticator(tokens, refresher).authenticate(null, unauthorized(accessToken = null))

        assertNull(retry)
        assertEquals(0, refresher.callCount.get())
        assertEquals(0, tokens.sessionExpiredCount)
    }

    @Test
    fun `새 토큰으로도 401 이면 무한 루프 대신 포기한다`() {
        val tokens = FakeTokenHolder(accessToken = "new.access", refreshToken = "old.refresh")
        val refresher = FakeRefresher(rotated("newer.access", "newer.refresh"))
        val secondFailure = unauthorized("new.access", prior = unauthorized("old.access"))

        val retry = TokenAuthenticator(tokens, refresher).authenticate(null, secondFailure)

        assertNull(retry)
        assertEquals(0, refresher.callCount.get())
    }

    @Test
    fun `동시에 401 을 맞아도 재발급은 한 번만 일어난다`() {
        val tokens = FakeTokenHolder(accessToken = "old.access", refreshToken = "old.refresh")
        // 재발급이 느린 상황을 만들어 두 번째 스레드가 반드시 잠금 대기에 걸리게 한다.
        val refresher = FakeRefresher(rotated("new.access", "new.refresh"), delayMillis = 200)
        val authenticator = TokenAuthenticator(tokens, refresher)

        val start = CountDownLatch(1)
        val done = CountDownLatch(THREAD_COUNT)
        val retries = java.util.Collections.synchronizedList(mutableListOf<Request?>())
        repeat(THREAD_COUNT) {
            Thread {
                start.await()
                retries += authenticator.authenticate(null, unauthorized("old.access"))
                done.countDown()
            }.start()
        }
        start.countDown()
        check(done.await(10, TimeUnit.SECONDS)) { "스레드가 끝나지 않았다" }

        // 서버가 회전 방식이라 두 번 재발급하면 늦은 쪽이 "이미 회전됨" 401 을 맞고 세션이 날아간다.
        assertEquals(1, refresher.callCount.get())
        assertEquals(THREAD_COUNT, retries.size)
        // 재발급하지 않은 스레드도 갱신된 토큰을 받아 정상 재시도해야 한다.
        retries.forEach { assertEquals("Bearer new.access", it?.header("Authorization")) }
        assertEquals(0, tokens.sessionExpiredCount)
    }

    private fun rotated(accessToken: String, refreshToken: String) =
        RefreshResult.Rotated(TokenPair(accessToken, refreshToken))

    private fun unauthorized(accessToken: String?, prior: Response? = null): Response {
        val request = Request.Builder()
            .url("http://localhost:8080/api/v1/matching/rooms")
            .apply { if (accessToken != null) header("Authorization", "Bearer $accessToken") }
            .build()
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .priorResponse(prior)
            .build()
    }

    private companion object {
        const val THREAD_COUNT = 4
    }
}

private class FakeTokenHolder(
    @Volatile var accessToken: String?,
    @Volatile var refreshToken: String?,
) : TokenHolder {
    @Volatile
    var sessionExpiredCount = 0

    override fun currentAccessToken(): String? = accessToken

    override fun currentRefreshToken(): String? = refreshToken

    override fun onTokensRefreshed(accessToken: String, refreshToken: String) {
        this.accessToken = accessToken
        this.refreshToken = refreshToken
    }

    override fun onSessionExpired() {
        accessToken = null
        refreshToken = null
        sessionExpiredCount++
    }
}

/**
 * [result] 를 [RefreshResult] 로 받는 것이 중요하다. 예전 Fake 는 `TokenPair?` 하나로
 * 401·타임아웃·503 을 전부 null 로 뭉개서, 「재발급이 실패하면 세션 만료를 알린다」 테스트가
 * 오히려 결함(네트워크 실패를 인증 거절로 오인)을 통과 조건으로 고정하고 있었다.
 */
private class FakeRefresher(
    private val result: RefreshResult,
    private val delayMillis: Long = 0,
) : TokenRefresher {
    val callCount = AtomicInteger()
    val usedRefreshTokens = java.util.Collections.synchronizedList(mutableListOf<String>())

    override fun refresh(refreshToken: String): RefreshResult {
        callCount.incrementAndGet()
        usedRefreshTokens += refreshToken
        if (delayMillis > 0) Thread.sleep(delayMillis)
        return result
    }
}
