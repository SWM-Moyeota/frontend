package com.moyeota.data.session

import com.moyeota.domain.model.AuthState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SessionManagerTest {

    // Unconfined: init 의 복원 코루틴이 생성자 반환 전에 즉시 끝나 테스트가 결정적이 된다.
    private fun manager(storage: TokenStorage) =
        SessionManager(storage, CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun `앱 재시작 시 저장된 세션을 복원해 로그인 상태를 유지한다`() {
        val stored = StoredSession("uuid-1", "saved.access", "saved.refresh")

        val session = manager(FakeTokenStorage(stored))

        assertEquals(AuthState.Authenticated("uuid-1"), session.authState.value)
        assertEquals("uuid-1", session.currentUserUuid)
        assertTrue(session.isLoggedIn)
        assertEquals("saved.access", session.currentAccessToken())
        assertEquals("saved.refresh", session.currentRefreshToken())
    }

    @Test
    fun `저장된 세션이 없으면 미로그인으로 확정되고 토큰을 주지 않는다`() {
        val session = manager(FakeTokenStorage(null))

        assertEquals(AuthState.Unauthenticated, session.authState.value)
        assertNull(session.currentUserUuid)
        assertFalse(session.isLoggedIn)
        assertNull(session.currentAccessToken())
    }

    /**
     * 세션이 아는 식별자는 UUID 하나뿐이다. 예전에는 채팅 헤더용 고정 memberId(1L)가 여기 있었지만
     * 채팅도 `@CurrentUser` 로 넘어가면서 서버로 나가는 내부 PK 가 앱에서 완전히 사라졌다 —
     * 되살리면 "누가 로그인해도 1번 사용자로 보이는" 버그가 그대로 돌아온다.
     */
    @Test
    fun `세션은 내부 사용자 PK 를 들지 않고 UUID 만 복원한다`() {
        val session = manager(FakeTokenStorage(StoredSession("uuid-1", "a", "r")))

        assertEquals("uuid-1", session.currentUserUuid)
    }

    @Test
    fun `로그인하면 저장소에 기록하고 인증 상태로 바뀐다`() = runBlocking {
        val storage = FakeTokenStorage(null)
        val session = manager(storage)

        session.onLoggedIn(StoredSession("uuid-2", "new.access", "new.refresh"))

        assertEquals(AuthState.Authenticated("uuid-2"), session.authState.value)
        assertEquals(StoredSession("uuid-2", "new.access", "new.refresh"), storage.saved)
        assertEquals("new.access", session.currentAccessToken())
    }

    @Test
    fun `재발급 결과는 메모리에 즉시 반영되고 저장소에도 기록된다`() {
        val storage = FakeTokenStorage(StoredSession("uuid-1", "old.access", "old.refresh"))
        val session = manager(storage)

        session.onTokensRefreshed("new.access", "new.refresh")

        assertEquals("new.access", session.currentAccessToken())
        assertEquals("new.refresh", session.currentRefreshToken())
        assertEquals(StoredSession("uuid-1", "new.access", "new.refresh"), storage.saved)
        // 재발급은 로그인 상태를 바꾸지 않는다.
        assertEquals(AuthState.Authenticated("uuid-1"), session.authState.value)
    }

    @Test
    fun `세션 만료는 상태와 저장소를 모두 비운다`() {
        val storage = FakeTokenStorage(StoredSession("uuid-1", "old.access", "old.refresh"))
        val session = manager(storage)

        session.onSessionExpired()

        assertEquals(AuthState.Unauthenticated, session.authState.value)
        assertNull(session.currentAccessToken())
        assertTrue(storage.cleared)
    }

    @Test
    fun `로그아웃은 서버 무효화용 리프레시를 돌려주고 로컬을 먼저 비운다`() {
        val storage = FakeTokenStorage(StoredSession("uuid-1", "old.access", "old.refresh"))
        val session = manager(storage)

        val refreshToken = session.onLoggedOut()

        assertEquals("old.refresh", refreshToken)
        assertEquals(AuthState.Unauthenticated, session.authState.value)
        assertTrue(storage.cleared)
    }

    @Test
    fun `이미 로그아웃 상태에서 다시 로그아웃해도 안전하다`() {
        val session = manager(FakeTokenStorage(null))

        assertNull(session.onLoggedOut())
        assertEquals(AuthState.Unauthenticated, session.authState.value)
    }

    // ─────────────────────────── 저장소가 터지는 경로 ───────────────────────────
    // [FakeTokenStorage] 는 절대 예외를 던지지 않아 이 경로가 통째로 미검증이었다.
    // 실제로는 DataStore 가 파일 손상(CorruptionException)·디스크 꽉 참으로 던질 수 있고,
    // 그 예외가 복원 코루틴을 타고 올라가면 프로세스가 죽거나(크래시 루프)
    // authState 가 Unknown 에 영구히 머물러 스플래시가 무한 스피너가 된다.

    /**
     * 복원 실패를 [AuthState.Unknown] 으로 남기면 `MainNavGraph` 가 스플래시만 그려 **무한 스피너**가
     * 된다. 틀린 쪽으로 떨어지는 비용은 재로그인 한 번이므로, 모르면 미로그인으로 확정한다.
     */
    @Test
    fun `복원이 실패하면 미로그인으로 확정된다`() {
        val session = manager(ThrowingTokenStorage(failOnLoad = true))

        assertEquals(AuthState.Unauthenticated, session.authState.value)
        assertFalse(session.isLoggedIn)
        assertNull(session.currentUserUuid)
    }

    /**
     * 복원 대기 래치가 풀리지 않으면 **모든 네트워크 요청이 복원 타임아웃(5초)까지 멈춘다.**
     * 예외 경로에서도 반드시 풀려야 한다 — 즉시 답이 오는지를 경과 시간으로 확인한다.
     */
    @Test
    fun `복원이 실패해도 토큰 대기가 즉시 풀린다`() {
        val session = manager(ThrowingTokenStorage(failOnLoad = true))

        val startedAt = System.nanoTime()
        val accessToken = session.currentAccessToken()
        val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000

        assertNull(accessToken)
        assertTrue("복원 래치가 풀리지 않아 ${elapsedMillis}ms 를 기다렸다", elapsedMillis < 1_000)
    }

    /**
     * 디스크가 꽉 찬 사용자를 로그인 자체에서 막으면 앱을 전혀 쓸 수 없다. 잃는 건 "다음 실행에서의
     * 자동 로그인"뿐이고, 지금 이 세션은 메모리에 있어 그대로 동작해야 한다.
     */
    @Test
    fun `로그인 저장이 실패해도 터지지 않고 인증 상태가 된다`() = runBlocking {
        val session = manager(ThrowingTokenStorage(failOnSave = true))

        session.onLoggedIn(StoredSession("uuid-2", "new.access", "new.refresh"))

        assertEquals(AuthState.Authenticated("uuid-2"), session.authState.value)
        assertEquals("new.access", session.currentAccessToken())
    }

    /** 재발급 결과 저장은 메모리를 뒤따르는 캐시 갱신이다 — 실패해도 메모리의 새 토큰은 유효하다. */
    @Test
    fun `재발급 결과 저장이 실패해도 메모리 토큰은 갱신된다`() {
        val session = manager(
            ThrowingTokenStorage(initial = StoredSession("uuid-1", "old.access", "old.refresh"), failOnSave = true),
        )

        session.onTokensRefreshed("new.access", "new.refresh")

        assertEquals("new.access", session.currentAccessToken())
        assertEquals(AuthState.Authenticated("uuid-1"), session.authState.value)
    }

    /** 디스크 비우기 실패로 "로그아웃이 안 된 것처럼" 보이게 하지 않는다 — 화면은 이미 미로그인이다. */
    @Test
    fun `세션 비우기가 실패해도 미로그인으로 전이한다`() {
        val session = manager(
            ThrowingTokenStorage(initial = StoredSession("uuid-1", "old.access", "old.refresh"), failOnClear = true),
        )

        session.onSessionExpired()

        assertEquals(AuthState.Unauthenticated, session.authState.value)
        assertNull(session.currentAccessToken())
    }
}

/**
 * 지정한 경로에서 예외를 던지는 [TokenStorage]. DataStore 의 `CorruptionException`(파일 손상)과
 * 디스크 오류가 실제로 올라오는 자리를 흉내 낸다 — 세 경로가 서로 독립이라 각각 켤 수 있게 두었다.
 */
private class ThrowingTokenStorage(
    private val initial: StoredSession? = null,
    private val failOnLoad: Boolean = false,
    private val failOnSave: Boolean = false,
    private val failOnClear: Boolean = false,
) : TokenStorage {

    override suspend fun load(): StoredSession? {
        if (failOnLoad) throw IOException("세션 파일이 손상됐다")
        return initial
    }

    override suspend fun save(session: StoredSession) {
        if (failOnSave) throw IOException("디스크가 꽉 찼다")
    }

    override suspend fun clear() {
        if (failOnClear) throw IOException("디스크 실패")
    }
}

internal class FakeTokenStorage(private val initial: StoredSession?) : TokenStorage {
    var saved: StoredSession? = null
        private set
    var cleared = false
        private set

    override suspend fun load(): StoredSession? = initial

    override suspend fun save(session: StoredSession) {
        saved = session
        cleared = false
    }

    override suspend fun clear() {
        saved = null
        cleared = true
    }
}
