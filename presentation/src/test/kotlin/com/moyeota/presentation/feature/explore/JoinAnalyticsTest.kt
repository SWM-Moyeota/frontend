package com.moyeota.presentation.feature.explore

import com.moyeota.domain.analytics.AnalyticsEvent
import com.moyeota.domain.analytics.MatchFailureReason
import com.moyeota.domain.analytics.MatchRequestMethod
import com.moyeota.domain.model.PartyJoinException
import com.moyeota.domain.model.PartyStatus
import com.moyeota.domain.model.RideStatus
import com.moyeota.presentation.analytics.AnalyticsRecorderRule
import com.moyeota.presentation.analytics.FakeActivePartyRepository
import com.moyeota.presentation.analytics.FakeRideRepository
import com.moyeota.presentation.analytics.testRide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/**
 * 20 「합류하기」 — click 은 요청 직전, request_match(join) 은 성공 뒤(합류 전에 보던 상세의 정원·요금),
 * 「이미 이 방 멤버」는 성공 경로(iOS `finishJoin` 과 같이 request_match 1번, fail 없음).
 */
class JoinAnalyticsTest {

    @get:Rule val rule = AnalyticsRecorderRule()

    private val held = testRide(capacity = 3, members = 1, estimatedFare = 9_000)
    private val joined = PartyStatus(status = RideStatus.RECRUITING, currentMembers = 2)

    private fun joinError(
        code: String?,
        httpStatus: Int? = null,
        isNetwork: Boolean = false,
        joinedStatus: PartyStatus? = null,
    ) = PartyJoinException(
        code = code,
        serverMessage = null,
        httpStatus = httpStatus,
        isNetwork = isNetwork,
        joinedStatus = joinedStatus,
    )

    /**
     * ★ 못 박는 것: **배선이 실제로 돈다.** 문구 분기(`alreadyInPartyMessage`)는 순수 함수라 따로
     * 검증되지만, 「막은 방을 조회해서 그 값을 넘기는가」는 ViewModel 만 보여 줄 수 있다.
     *
     * 이게 빠지면 분기는 멀쩡한데 늘 null 이 넘어가 **영원히 「홈에서 확인해 주세요」**만 나온다 —
     * 테스트가 다 통과하면서 고친 게 사라지는 모양이다.
     */
    @Test
    fun `다른 방에 막혔을 때 그 방이 확정된 방이면 출발로 끝내라고 안내한다`() {
        val repo = FakeRideRepository().apply {
            detail = { held }
            join = { throw joinError(PartyJoinException.ALREADY_JOINED_OTHER_PARTY, httpStatus = 409) }
        }
        val blocking = FakeActivePartyRepository(active = testRide().copy(isConfirmed = true))
        val vm = JoinConfirmViewModel(repo, blocking, partyId = 7)

        vm.join()

        assertEquals(
            "약속이 정해진 방에 참여 중이에요. 출발지에서 「출발」을 눌러 그 방을 끝낸 뒤에 합류할 수 있어요",
            vm.joinState.value.errorMessage,
        )
        assertNull(vm.joinState.value.joinedPartyId)
    }

    /** 확정 전 방이 막고 있으면 지금까지의 안내(나온 뒤 합류)가 그대로 맞다. */
    @Test
    fun `막은 방이 확정 전이면 나온 뒤 합류하라고 안내한다`() {
        val repo = FakeRideRepository().apply {
            detail = { held }
            join = { throw joinError(PartyJoinException.ALREADY_JOINED_OTHER_PARTY, httpStatus = 409) }
        }
        val vm = JoinConfirmViewModel(repo, FakeActivePartyRepository(active = testRide()), partyId = 7)

        vm.join()

        assertEquals("이미 참여 중인 방이 있어요. 그 방에서 나온 뒤 합류해 주세요", vm.joinState.value.errorMessage)
    }

    /**
     * 막은 방 조회가 실패해도 **안내는 선다.** 정확한 한 줄을 만들려다 안내 자체를 잃으면 더 나쁘다.
     */
    @Test
    fun `막은 방 조회가 실패해도 안내 문구는 뜬다`() {
        val repo = FakeRideRepository().apply {
            detail = { held }
            join = { throw joinError(PartyJoinException.ALREADY_JOINED_OTHER_PARTY, httpStatus = 409) }
        }
        val blocking = FakeActivePartyRepository(resolveError = IOException("네트워크"))
        val vm = JoinConfirmViewModel(repo, blocking, partyId = 7)

        vm.join()

        assertEquals("이미 참여 중인 방이 있어요. 홈에서 진행 중인 방을 확인해 주세요", vm.joinState.value.errorMessage)
    }

    @Test
    fun `성공하면 click 1번과 request_match 1번 - 지역은 빼고 보던 상세의 정원과 요금을 싣는다`() {
        val repo = FakeRideRepository().apply {
            detail = { held }
            join = { joined }
        }
        val vm = JoinConfirmViewModel(repo, FakeActivePartyRepository(), partyId = 7)

        vm.join()

        assertEquals(listOf("click_join_party", "request_match"), rule.names())
        assertEquals(AnalyticsEvent.PartyJoinClicked, rule.recorded[0])
        assertEquals(
            AnalyticsEvent.MatchRequested(MatchRequestMethod.JOIN, capacity = 3, originArea = null, destinationArea = null, estimatedFare = 9_000),
            rule.recorded[1],
        )
        assertEquals(7L, vm.joinState.value.joinedPartyId)
    }

    @Test
    fun `상세를 못 읽었으면 capacity 와 요금 키를 뺀다`() {
        val repo = FakeRideRepository().apply {
            detail = { throw IOException() }
            join = { joined }
        }

        JoinConfirmViewModel(repo, FakeActivePartyRepository(), partyId = 7).join()

        val requested = rule.only<AnalyticsEvent.MatchRequested>().single()
        assertNull(requested.capacity)
        assertEquals(setOf("method"), requested.properties.keys)
    }

    @Test
    fun `보던 상세의 정원이 0 이면 지어내지 않고 키를 뺀다`() {
        val repo = FakeRideRepository().apply {
            detail = { testRide(capacity = 0) }
            join = { joined }
        }

        JoinConfirmViewModel(repo, FakeActivePartyRepository(), partyId = 7).join()

        assertNull(rule.only<AnalyticsEvent.MatchRequested>().single().capacity)
    }

    @Test
    fun `실패하면 click 1번과 fail 1번 - request_match 는 보내지 않는다`() {
        val repo = FakeRideRepository().apply {
            detail = { held }
            join = { throw joinError("PARTY_FULL", 409) }
        }
        val vm = JoinConfirmViewModel(repo, FakeActivePartyRepository(), partyId = 7)

        vm.join()

        assertEquals(listOf("click_join_party", "fail_match_request"), rule.names())
        assertEquals(AnalyticsEvent.MatchRequestFailed(MatchRequestMethod.JOIN, "party_full"), rule.recorded[1])
        assertNull(vm.joinState.value.joinedPartyId)
    }

    @Test
    fun `이미 이 방에 합류됨은 성공 경로 - fail 없이 request_match 1번`() {
        val repo = FakeRideRepository().apply {
            detail = { held }
            join = { throw joinError(PartyJoinException.ALREADY_JOINED_OTHER_PARTY, 409, joinedStatus = joined) }
        }
        val vm = JoinConfirmViewModel(repo, FakeActivePartyRepository(), partyId = 7)

        vm.join()

        // iOS finishJoin 과 같다 — 복구도 서버에 내 자리가 있다는 뜻이라 request_match 로 센다(fail 은 없다)
        assertEquals(listOf("click_join_party", "request_match"), rule.names())
        assertEquals(
            AnalyticsEvent.MatchRequested(MatchRequestMethod.JOIN, capacity = 3, originArea = null, destinationArea = null, estimatedFare = 9_000),
            rule.recorded[1],
        )
        assertEquals(7L, vm.joinState.value.joinedPartyId)
        assertNull(vm.joinState.value.errorMessage)
    }

    @Test
    fun `좁혀지지 않은 예외는 unknown 으로 1번`() {
        val repo = FakeRideRepository().apply {
            detail = { held }
            join = { throw IllegalStateException("직렬화") }
        }

        JoinConfirmViewModel(repo, FakeActivePartyRepository(), partyId = 7).join()

        assertEquals(
            listOf(AnalyticsEvent.PartyJoinClicked, AnalyticsEvent.MatchRequestFailed(MatchRequestMethod.JOIN, MatchFailureReason.UNKNOWN)),
            rule.recorded,
        )
    }

    @Test
    fun `joinFailureReason 은 iOS 표와 같다`() {
        val table = listOf(
            joinError("PARTY_FULL", 409) to "party_full",
            joinError("PARTY_CLOSED", 409) to "party_closed",
            joinError("PARTY_NOT_FOUND", 404) to "party_not_found",
            joinError(null, httpStatus = 404) to "party_not_found",
            joinError(PartyJoinException.ALREADY_JOINED_OTHER_PARTY, 409) to "already_in_party",
            joinError(PartyJoinException.ALREADY_JOINED_PARTY, 409) to "already_joined",
            joinError(null, isNetwork = true) to "network",
            joinError(null, httpStatus = 500) to "unknown",
            joinError("SOMETHING_NEW", 400) to "unknown",
        )
        table.forEach { (e, reason) -> assertEquals("${e.code}/${e.httpStatus}", reason, joinFailureReason(e)) }
    }
}
