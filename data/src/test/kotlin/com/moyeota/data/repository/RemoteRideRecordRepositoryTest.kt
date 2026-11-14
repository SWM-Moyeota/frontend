package com.moyeota.data.repository

import com.moyeota.data.remote.UserApi
import com.moyeota.data.remote.dto.AccountDeletionRequest
import com.moyeota.data.remote.dto.FcmTokenRequest
import com.moyeota.data.remote.dto.ProfileImageResponse
import com.moyeota.data.remote.dto.RideRecordMemberResponse
import com.moyeota.data.remote.dto.RideRecordPageResponse
import com.moyeota.data.remote.dto.RideRecordResponse
import com.moyeota.data.remote.dto.UpdateProfileRequest
import com.moyeota.data.remote.dto.UserProfileResponse
import com.moyeota.domain.model.AuthState
import com.moyeota.domain.repository.ActivePartyRepository
import com.moyeota.domain.repository.RideRecordRepository
import com.moyeota.domain.session.UserSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `GET /api/v1/users/me/parties` 저장소.
 *
 * 이 저장소가 하는 일은 둘뿐이다 — **쿼리를 그대로 넘기는 것**과 **세션 uuid 로 「나」를 만드는 것**.
 * 그래서 테스트도 그 둘과, 이 저장소에서 가장 비싼 사고(취소를 에러로 바꾸기)만 본다.
 */
class RemoteRideRecordRepositoryTest {

    private fun session(uuid: String? = "me-uuid"): UserSession = object : UserSession {
        override val authState: StateFlow<AuthState> = MutableStateFlow(
            if (uuid == null) AuthState.Unauthenticated else AuthState.Authenticated(uuid),
        )
    }

    private fun page(
        records: List<RideRecordResponse>,
        nextCursor: Long? = null,
        hasNext: Boolean = false,
    ) = RideRecordPageResponse(rides = records, nextCursor = nextCursor, hasNext = hasNext)

    private fun record(partyId: Long, memberUuid: String?) = RideRecordResponse(
        partyId = partyId,
        departure = "강남역",
        destination = "판교역",
        pickupTime = "2026-10-31T19:15:17.550211Z",
        dropoffTime = "2026-10-31T19:15:17.571486Z",
        totalFare = 16500,
        farePerPerson = 16500,
        currentMembers = 1,
        members = listOf(RideRecordMemberResponse(publicId = memberUuid, nickname = "프로브9911")),
    )

    @Test
    fun `페이지를 그대로 도메인으로 옮긴다`() {
        val api = FakeRideRecordUserApi(page(listOf(record(69, "me-uuid")), nextCursor = 69, hasNext = true))
        val repository = RemoteRideRecordRepository(api, session())

        val result = runBlocking { repository.getMyRecords() }

        assertEquals(listOf(69L), result.records.map { it.partyId })
        assertEquals(69L, result.nextCursor)
        assertTrue(result.hasNext)
        assertEquals(16500, result.records.single().farePerPerson)
    }

    /** 커서·개수를 저장소가 다듬지 않는다. 서버 범위는 1~50 이고 밖이면 400 `INVALID_PAGE_SIZE` 다. */
    @Test
    fun `커서와 개수를 서버에 그대로 넘긴다`() {
        val api = FakeRideRecordUserApi(page(emptyList()))
        val repository = RemoteRideRecordRepository(api, session())

        runBlocking { repository.getMyRecords(cursor = 69L, size = 1) }

        assertEquals(listOf(69L to 1), api.calls)
    }

    /** 인자 없이 부르면 서버 기본값과 같은 20 이 나간다 — 0·100 이 새면 목록이 통째로 빈다. */
    @Test
    fun `기본 조회 개수는 서버 기본값과 같은 20 이다`() {
        val api = FakeRideRecordUserApi(page(emptyList()))
        val repository = RemoteRideRecordRepository(api, session())

        runBlocking { repository.getMyRecords() }

        assertEquals(listOf(null to 20), api.calls)
        assertEquals(20, RideRecordRepository.DEFAULT_PAGE_SIZE)
    }

    /** 서버가 `isMe` 를 주지 않으므로 세션 uuid 가 그 판정의 유일한 근거다. */
    @Test
    fun `세션 uuid 와 같은 멤버를 나로 표시한다`() {
        val api = FakeRideRecordUserApi(page(listOf(record(69, "me-uuid"))))
        val repository = RemoteRideRecordRepository(api, session("me-uuid"))

        val member = runBlocking { repository.getMyRecords() }.records.single().members.single()

        assertTrue(member.isMe)
    }

    /** 미로그인이면 전원 false — 요약 없는 멤버가 「나」로 넘어오는 경로까지 막는다. */
    @Test
    fun `미로그인 세션에서는 아무도 나로 표시되지 않는다`() {
        val api = FakeRideRecordUserApi(page(listOf(record(69, null))))
        val repository = RemoteRideRecordRepository(api, session(uuid = null))

        val member = runBlocking { repository.getMyRecords() }.records.single().members.single()

        assertFalse(member.isMe)
    }

    /**
     * 취소를 에러로 바꾸면 **화면 이탈이 에러 문구로 보인다**(이 저장소 전반의 규칙).
     * 이 저장소는 `try`/`catch` 를 아예 두지 않는 쪽으로 그 규칙을 지키는데, 나중에 누군가
     * 「에러 메시지를 예쁘게」 하려고 `catch (e: Throwable)` 를 두면 이 테스트가 먼저 깨진다.
     */
    @Test
    fun `취소는 삼키지 않고 다시 던진다`() {
        val api = FakeRideRecordUserApi(page(emptyList()), failure = CancellationException("이탈"))
        val repository = RemoteRideRecordRepository(api, session())

        val thrown = runCatching { runBlocking { repository.getMyRecords() } }.exceptionOrNull()

        assertTrue("취소가 다른 예외로 바뀌었다: $thrown", thrown is CancellationException)
    }

    /**
     * ★ 이번 조사의 결론을 구조로 못 박는다 — **탑승 기록은 「지금 내가 타고 있는 방」을 대신하지 않는다.**
     *
     * 실측(2026-11-01, 로컬 `:8080`): 내가 유일 멤버인 `status=MATCHING` 방을 하나 들고
     * `GET /api/v1/users/me/parties` 를 불러도 `{"rides":[],"nextCursor":null,"hasNext":false}` 였다.
     * 서버가 하차 시각이 찍힌 기록만 거르기 때문이다(`dropoffTime is not null`).
     *
     * 다음 사람이 경로 이름(`users/me/parties`)만 보고 「내 방 목록」으로 읽어
     * [ActivePartyRepository] 의 로컬 기억을 이것으로 걷어내려 할 확률이 높다. 그러면
     * **진행 중인 방을 전부 잃는다** — 홈·탐색 배너가 사라지고, 매칭 화면 복귀가 끊기고,
     * 채팅방 탐색 게이트가 `ride == null` 에서 멈춰 「채팅 열기」가 영영 안 뜬다.
     *
     * 그래서 두 타입이 **서로를 모르는 것**을 값으로 확인한다. 엮으려면 이 테스트를 먼저 지워야 하고,
     * 지우려면 위 실측을 읽게 된다.
     */
    @Test
    fun `탑승 기록은 진행 중인 방을 대신하지 않는다`() {
        assertFalse(
            "RideRecordRepository 가 ActivePartyRepository 로 넘어갔다 — 진행 중인 방은 이 응답에 없다",
            ActivePartyRepository::class.java.isAssignableFrom(RideRecordRepository::class.java),
        )
        assertFalse(
            "ActivePartyRepository 가 RideRecordRepository 를 상속했다 — 두 계약을 섞으면 안 된다",
            RideRecordRepository::class.java.isAssignableFrom(ActivePartyRepository::class.java),
        )
        // 구현도 진행 중 방 쪽을 주입받지 않는다. 생성자 인자는 UserApi + UserSession 둘뿐이다.
        val constructor = RemoteRideRecordRepository::class.java.declaredConstructors.single()
        assertEquals(
            listOf(UserApi::class.java, UserSession::class.java),
            constructor.parameterTypes.toList(),
        )
    }

    /** 기록이 없는 계정(실측: `moyeota2`)에서 빈 페이지가 예외 없이 나와야 한다. */
    @Test
    fun `기록이 없으면 빈 페이지를 돌려준다`() {
        val api = FakeRideRecordUserApi(page(emptyList()))
        val repository = RemoteRideRecordRepository(api, session())

        val result = runBlocking { repository.getMyRecords() }

        assertTrue(result.records.isEmpty())
        assertFalse(result.hasNext)
        assertNull(result.nextCursor)
    }
}

/**
 * 탑승 기록만 돌려주는 가짜 [UserApi]. 나머지 메서드는 이 테스트의 관심이 아니라 부르면 터진다
 * — 조용히 기본값을 돌려주면 「저장소가 엉뚱한 호출을 하고 있다」를 못 잡는다.
 */
private class FakeRideRecordUserApi(
    private val page: RideRecordPageResponse,
    private val failure: Throwable? = null,
) : UserApi {
    override suspend fun getLoginMethods() = com.moyeota.data.remote.dto.LoginMethodsDto(true, emptyList())
    override suspend fun sendLoginMethodCode() = Unit
    override suspend fun verifyLoginMethodCode(request: com.moyeota.data.remote.dto.LinkCodeDto) = com.moyeota.data.remote.dto.LinkVerificationDto("link-test", 300)
    override suspend fun linkGoogle(request: com.moyeota.data.remote.dto.GoogleLinkDto) = Unit


    /** 나간 호출의 `(cursor, size)`. 저장소가 값을 다듬지 않는지 보는 자리다. */
    val calls = mutableListOf<Pair<Long?, Int>>()

    override suspend fun getMyRideRecords(cursor: Long?, size: Int): RideRecordPageResponse {
        calls += cursor to size
        failure?.let { throw it }
        return page
    }

    override suspend fun getMyProfile(): UserProfileResponse = error("이 테스트에서 부를 일이 없다")
    override suspend fun updateProfile(request: UpdateProfileRequest) = error("이 테스트에서 부를 일이 없다")
    override suspend fun getProfileImages(): List<ProfileImageResponse> = error("이 테스트에서 부를 일이 없다")
    override suspend fun resetProfileImage() = error("이 테스트에서 부를 일이 없다")
    override suspend fun registerFcmToken(request: FcmTokenRequest) = error("이 테스트에서 부를 일이 없다")
    override suspend fun deleteFcmToken() = error("이 테스트에서 부를 일이 없다")
    override suspend fun deleteAccount(request: AccountDeletionRequest) = error("이 테스트에서 부를 일이 없다")
    override suspend fun deleteSocialAccount() = error("이 테스트에서 부를 일이 없다")
}
