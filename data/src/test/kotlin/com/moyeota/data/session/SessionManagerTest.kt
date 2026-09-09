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
