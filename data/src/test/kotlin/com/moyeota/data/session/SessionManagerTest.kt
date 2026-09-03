package com.moyeota.data.session

import com.moyeota.domain.model.AuthState
import com.moyeota.domain.session.UserSession
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

    @Test
    fun `memberId 는 로그인과 무관하게 고정값을 유지한다`() {
        // 매칭·배차·신고·즐겨찾기는 @LoginUser 로 넘어가 이 값을 더 이상 쓰지 않는다.
        // 남은 사용처는 채팅의 X-User-Id 헤더와 방 생성 본문의 creatorId 둘뿐이다.
        val session = manager(FakeTokenStorage(StoredSession("uuid-1", "a", "r")))

        assertEquals(UserSession.FIXED_MEMBER_ID, session.currentUserId)
        assertEquals(1L, session.currentUserId)
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
