package com.moyeota.data.remote.auth

import com.moyeota.data.session.TokenHolder
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

    @Test
    fun `401 이면 재발급한 액세스 토큰으로 원요청을 재시도한다`() {
        val tokens = FakeTokenHolder(accessToken = "old.access", refreshToken = "old.refresh")
        val refresher = FakeRefresher(TokenPair("new.access", "new.refresh"))

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
        val refresher = FakeRefresher(TokenPair("new.access", "new.refresh"))

        TokenAuthenticator(tokens, refresher).authenticate(null, unauthorized("old.access"))

        assertEquals("new.access", tokens.accessToken)
        // 옛 리프레시는 서버에서 이미 무효화됐다 — 저장하지 않으면 다음 재발급이 401 이다.
        assertEquals("new.refresh", tokens.refreshToken)
        assertEquals(0, tokens.sessionExpiredCount)
    }

    @Test
    fun `재발급이 실패하면 세션 만료를 알리고 재시도를 포기한다`() {
        val tokens = FakeTokenHolder(accessToken = "old.access", refreshToken = "revoked.refresh")
        val refresher = FakeRefresher(result = null)

        val retry = TokenAuthenticator(tokens, refresher).authenticate(null, unauthorized("old.access"))

        assertNull(retry)
        assertEquals(1, tokens.sessionExpiredCount)
        assertNull(tokens.accessToken)
        assertNull(tokens.refreshToken)
    }

    @Test
    fun `리프레시 토큰이 없으면 재발급을 시도하지 않고 세션을 만료시킨다`() {
        val tokens = FakeTokenHolder(accessToken = "orphan.access", refreshToken = null)
        val refresher = FakeRefresher(TokenPair("new.access", "new.refresh"))

        val retry = TokenAuthenticator(tokens, refresher).authenticate(null, unauthorized("orphan.access"))

        assertNull(retry)
        assertEquals(0, refresher.callCount.get())
        assertEquals(1, tokens.sessionExpiredCount)
    }

    @Test
    fun `토큰을 싣지 않은 요청의 401 은 건드리지 않는다`() {
        val tokens = FakeTokenHolder(accessToken = "any.access", refreshToken = "any.refresh")
        val refresher = FakeRefresher(TokenPair("new.access", "new.refresh"))

        // 미로그인 상태에서 보호 API 를 호출한 경우 — 재발급으로 풀 수 있는 문제가 아니다.
        val retry = TokenAuthenticator(tokens, refresher).authenticate(null, unauthorized(accessToken = null))

        assertNull(retry)
        assertEquals(0, refresher.callCount.get())
        assertEquals(0, tokens.sessionExpiredCount)
    }

    @Test
    fun `새 토큰으로도 401 이면 무한 루프 대신 포기한다`() {
        val tokens = FakeTokenHolder(accessToken = "new.access", refreshToken = "old.refresh")
        val refresher = FakeRefresher(TokenPair("newer.access", "newer.refresh"))
        val secondFailure = unauthorized("new.access", prior = unauthorized("old.access"))

        val retry = TokenAuthenticator(tokens, refresher).authenticate(null, secondFailure)

        assertNull(retry)
        assertEquals(0, refresher.callCount.get())
    }

    @Test
    fun `동시에 401 을 맞아도 재발급은 한 번만 일어난다`() {
        val tokens = FakeTokenHolder(accessToken = "old.access", refreshToken = "old.refresh")
        // 재발급이 느린 상황을 만들어 두 번째 스레드가 반드시 잠금 대기에 걸리게 한다.
        val refresher = FakeRefresher(TokenPair("new.access", "new.refresh"), delayMillis = 200)
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

private class FakeRefresher(
    private val result: TokenPair?,
    private val delayMillis: Long = 0,
) : TokenRefresher {
    val callCount = AtomicInteger()
    val usedRefreshTokens = java.util.Collections.synchronizedList(mutableListOf<String>())

    override fun refresh(refreshToken: String): TokenPair? {
        callCount.incrementAndGet()
        usedRefreshTokens += refreshToken
        if (delayMillis > 0) Thread.sleep(delayMillis)
        return result
    }
}
