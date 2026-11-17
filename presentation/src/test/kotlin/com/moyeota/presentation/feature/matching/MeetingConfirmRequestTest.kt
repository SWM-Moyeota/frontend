package com.moyeota.presentation.feature.matching

import androidx.lifecycle.viewModelScope
import com.moyeota.domain.model.PartyDetail
import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RideStatus
import com.moyeota.presentation.analytics.AnalyticsRecorderRule
import com.moyeota.presentation.analytics.FakeRideRepository
import com.moyeota.presentation.analytics.testRide
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 「약속 시간 정하기」가 서버에 **무엇을 보내는지** — 2026-11-12 에 본문이 한 필드에서 넷으로 늘었다.
 *
 * ### 왜 이 테스트가 필요한가 ★
 * 서버 `confirm` 은 `departureAt` 외에 **만남 장소(위도·경도·이름)를 전부 `@NotNull`/`@NotBlank`**
 * 로 받는다(`2732cc75`). 넷 중 하나라도 빠지거나 0 이면:
 *
 * | 보낸 값 | 서버 | 사용자가 보는 것 |
 * |---|---|---|
 * | 장소 필드 누락 | 400 `INVALID_REQUEST` | 「약속 시간을 정하지 못했어요」 — **어떤 사유 코드도 아니다** |
 * | 좌표를 0.0 으로 채움 | 400 `OUT_OF_SERVICE_AREA` | 적도 한가운데라고 거절당한다 |
 * | 빈 장소 이름 | 400 `INVALID_REQUEST` | 같은 일반 문구 |
 *
 * 세 경우 다 **가장 찾기 어려운 실패**다: 사유가 일반 문구로만 보이고, 확정이 방을 끝내는 길의
 * 첫 단계라 그게 막히면 **그 방을 끝낼 방법이 아예 없다**(옛 「합승 완료」가 서버에서 사라졌다).
 * 그래서 「무엇을 보냈는가」를 값으로 고정한다 — 화면을 봐서는 알 수 없는 종류다.
 *
 * ### 앱은 **방의 지금 출발지를 그대로** 보낸다
 * 채팅으로 다른 곳을 정하는 UI 는 아직 없다. 그래도 서버 쪽에서는 그게 「출발지를 이 값으로
 * 덮어쓴다」는 뜻이므로, 보낸 값이 **곧 방의 새 출발지**가 된다([Ride.withMeetingConfirmed]).
 */
class MeetingConfirmRequestTest {

    @get:Rule val rule = AnalyticsRecorderRule()

    /** 보낸 네 값을 그대로 받아 두는 기록기 — 「무엇을 보냈는가」가 이 파일의 주제다. */
    private data class SentConfirm(
        val departureAt: String,
        val latitude: Double,
        val longitude: Double,
        val placeName: String,
    )

    private var sent: SentConfirm? = null
    private var detailReads = 0
    private var current: Ride = detailRide()

    private val repo = object : FakeRideRepository() {
        override suspend fun getPartyDetailWithVersion(partyId: Long): PartyDetail {
            detailReads++
            return PartyDetail(current, version = null)
        }

        override suspend fun confirmMeeting(
            partyId: Long,
            departureAt: String,
            latitude: Double,
            longitude: Double,
            placeName: String,
        ) {
            sent = SentConfirm(departureAt, latitude, longitude, placeName)
        }
    }

    private var vm: MatchWaitingViewModel? = null

    private fun enter(): MatchWaitingViewModel =
        MatchWaitingViewModel(repo, partyId = 1).also { vm = it }

    @After
    fun tearDown() {
        vm?.viewModelScope?.cancel() // SSE 구독·폴백 루프 정리
    }

    @Test
    fun `방의 지금 출발지를 좌표와 이름까지 그대로 보낸다`() {
        val viewModel = enter()

        viewModel.confirmMeeting("2026-11-06T06:30:00Z")

        assertEquals(
            SentConfirm(
                departureAt = "2026-11-06T06:30:00Z",
                latitude = 35.2314,
                longitude = 129.0846,
                placeName = "부산대 정문",
            ),
            sent,
        )
    }

    /**
     * ★ **성공하면 보낸 장소가 그 자리에서 방의 출발지가 된다.**
     *
     * SSE `confirmed` 가 같은 셋을 들고 오지만 그 스트림은 at-most-once 이고 망에 따라 아예 붙지
     * 않는다. 기다리면 **도착 버튼이 옛 출발지를 기준으로 판정**돼 약속 장소에 서 있는 사람이
     * 400 `TOO_FAR_FROM_DEPARTURE` 를 맞는다.
     *
     * 지금은 보내는 값이 방의 출발지와 같아 좌표가 움직이지 않지만, 확정 사실과 약속 시각은 바뀐다 —
     * 그 둘이 서지 않으면 화면이 확정 단계로 넘어가지 않는다([meetingStage]).
     */
    @Test
    fun `확정이 성공하면 기다리지 않고 방을 고친다`() {
        val viewModel = enter()

        viewModel.confirmMeeting("2026-11-06T06:30:00Z")

        val ride = (viewModel.uiState.value as MatchWaitingViewModel.UiState.Success).ride
        assertTrue("확정 사실이 서지 않으면 도착 버튼이 영원히 뜨지 않는다", ride.isConfirmed)
        assertEquals("2026-11-06T06:30:00Z", ride.departureAt)
        assertEquals("부산대 정문", ride.origin)
    }

    /**
     * 🔴 ★ **좌표를 모르면 요청을 만들지 않는다** — 0.0 으로 채우지 않는다.
     *
     * [Ride.originLat]·[Ride.originLng] 는 **nullable** 이다(목록·추천 응답으로 만든 [Ride] 에는
     * 좌표가 없다). 21 대기 화면이 보는 방은 상세·SSE 스냅샷에서 오므로 실제로는 늘 채워져 있지만
     * **타입이 그걸 보장하지 않는다** — 그래서 막는다.
     *
     * 0.0 으로 채우면 서버가 400 `OUT_OF_SERVICE_AREA` 로 거절하고, 그건 **방을 끝낼 길이
     * 사라지는** 실패다. 대신 사실을 말하고 **상세를 다시 읽어 둔다**: 상세 응답에는 좌표가 있으므로
     * 다시 누르면 통과한다. 「다시 시도해 주세요」를 붙이는 유일한 실패 문구인 근거다.
     */
    @Test
    fun `출발지 좌표를 모르면 보내지 않고 상세를 다시 읽는다`() {
        current = detailRide().copy(originLat = null, originLng = null)
        val viewModel = enter()
        val readsBefore = detailReads

        viewModel.confirmMeeting("2026-11-06T06:30:00Z")

        assertNull("좌표를 지어내 보냈다 — 서버가 400 OUT_OF_SERVICE_AREA 로 거절한다", sent)
        assertEquals(CONFIRM_PLACE_UNKNOWN_MESSAGE, viewModel.actionState.value.errorMessage)
        assertTrue("상세를 다시 읽지 않으면 다시 눌러도 같은 실패다", detailReads > readsBefore)
        // 확정되지 않았다는 사실도 그대로여야 한다 — 보내지 않았으니 방은 그대로다
        val ride = (viewModel.uiState.value as MatchWaitingViewModel.UiState.Success).ride
        assertEquals(false, ride.isConfirmed)
    }

    /**
     * 장소 **이름**이 비어 있어도 같다 — 서버가 `@NotBlank` 로 받는다(400 `INVALID_REQUEST`).
     *
     * 빈 이름을 보내면 그 방의 출발지 이름이 **빈 값으로 덮어써진다** — 그러면 모두의 화면에서
     * 「어디서 만나는지」가 사라진다. 좌표보다 조용한 사고다.
     */
    @Test
    fun `장소 이름이 비어 있으면 보내지 않는다`() {
        current = detailRide().copy(origin = "   ")
        val viewModel = enter()

        viewModel.confirmMeeting("2026-11-06T06:30:00Z")

        assertNull(sent)
        assertEquals(CONFIRM_PLACE_UNKNOWN_MESSAGE, viewModel.actionState.value.errorMessage)
    }

    /** 순수 판정만 따로 — 셋이 다 있을 때만 보낼 수 있다. */
    @Test
    fun `보낼 장소는 좌표와 이름이 다 있을 때만 만들어진다`() {
        val full = detailRide()

        assertEquals(MeetingPlace(35.2314, 129.0846, "부산대 정문"), meetingPlaceOrNull(full))
        assertNull(meetingPlaceOrNull(full.copy(originLat = null)))
        assertNull(meetingPlaceOrNull(full.copy(originLng = null)))
        assertNull(meetingPlaceOrNull(full.copy(origin = "")))
        assertNull(meetingPlaceOrNull(full.copy(origin = " ")))
    }

    private companion object {
        /** 상세 조회·SSE 스냅샷이 주는 모양 — **좌표가 채워져 있다.** */
        fun detailRide(): Ride = testRide(RideStatus.RECRUITING, capacity = 3, members = 2).copy(
            origin = "부산대 정문",
            originLat = 35.2314,
            originLng = 129.0846,
        )
    }
}
