package com.moyeota.presentation.core

import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RideStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 채팅방 감시 폴링(10초, `GET /chat-rooms/me`)이 **보일 때만** 도는지.
 *
 * 이 루프는 `MainNavGraph` 의 NavHost 컴포지션 스코프에서 돈다. 컴포지션은 **앱이 백그라운드로 가도
 * 살아 있어서**, 진행 중인 방이 있고 채팅방이 아직 없는 사용자는 어느 화면에 있든 — 앱을 내려놓아도 —
 * 10초마다 서버를 불렀다(QA 119 D-1 과 같은 성격의 누수. `120` §5 가 「가시성과 무관하게 네트워크를
 * 때리는 루프로 남은 유일한 건」으로 지목한 자리다).
 *
 * 판정만 고정한다. 「가려지면 진행 중인 조회와 `delay` 가 실제로 죽는가」는 `combine` + `collectLatest`
 * 의 취소 전파에 달려 있고, presentation 테스트 의존성이 `junit` 뿐이라(코루틴 테스트 라이브러리를
 * 넣으면 빌드 파일 변경이다) 단위 테스트로 확인할 수 없다 — QA 의 logcat 계수가 그 몫이다.
 */
class ChatRoomPollGateTest {

    /** D-1 성격의 누수 회귀 방지 — 안 보이면 **나머지 조건을 보지 않는다.** */
    @Test
    fun `안 보이면 어떤 조합에서도 폴링하지 않는다`() {
        val rides = listOf(
            null,
            ride(RideStatus.RECRUITING),
            ride(RideStatus.MATCHED),
            ride(RideStatus.DISPATCHING),
            ride(RideStatus.ONGOING),
            ride(RideStatus.COMPLETED),
        )
        for (ride in rides) {
            for (roomId in listOf(null, 7L)) {
                assertFalse(
                    "가려진 상태에서 폴링했다 (ride=${ride?.status}, chatRoomId=$roomId)",
                    shouldPollChatRoom(visible = false, ride = ride, chatRoomId = roomId),
                )
            }
        }
    }

    @Test
    fun `보이고 진행 중 방이 있고 채팅방을 모르면 폴링한다`() {
        assertTrue(shouldPollChatRoom(visible = true, ride = ride(RideStatus.MATCHED), chatRoomId = null))
    }

    /**
     * 모집 중(21 대기)에도 찾는다 — 서버가 채팅방을 **합류 시점에** 만든다(Backend #109).
     * 「정원이 차야 생긴다」던 옛 가정으로 되돌리면 1차 배포 모드(정원이 차도 대기 단계)에서
     * 21 의 「채팅 열기」가 영영 안 뜬다.
     */
    @Test
    fun `모집 중에도 채팅방을 찾는다`() {
        assertTrue(shouldPollChatRoom(visible = true, ride = ride(RideStatus.RECRUITING), chatRoomId = null))
    }

    /** 기존 종료 조건 — 찾으면 멈춘다(흐름이 null 을 내보내 진행 중 패스가 취소된다). */
    @Test
    fun `채팅방을 찾으면 멈춘다`() {
        assertFalse(shouldPollChatRoom(visible = true, ride = ride(RideStatus.ONGOING), chatRoomId = 7L))
    }

    /** 「채팅방 나가기」·403 으로 캐시를 버리면(`forgetChatRoom`) 다시 찾아야 한다. */
    @Test
    fun `채팅방 캐시를 버리면 다시 찾는다`() {
        assertTrue(shouldPollChatRoom(visible = true, ride = ride(RideStatus.ONGOING), chatRoomId = null))
    }

    @Test
    fun `진행 중인 방이 없으면 찾을 대상이 없다`() {
        assertFalse(shouldPollChatRoom(visible = true, ride = null, chatRoomId = null))
    }

    /** 끝난 방(`clearParty` 가 늦게 돌아 홀더에 남아 있는 경우)의 채팅방은 열어 줄 데가 없다. */
    @Test
    fun `끝난 방이면 찾지 않는다`() {
        assertFalse(shouldPollChatRoom(visible = true, ride = ride(RideStatus.COMPLETED), chatRoomId = null))
        assertFalse(shouldPollChatRoom(visible = true, ride = ride(RideStatus.CANCELED), chatRoomId = null))
    }

    private fun ride(status: RideStatus) = Ride(
        id = "7",
        origin = "성결대",
        destination = "안양역",
        departureLabel = "지금 출발",
        capacity = 4,
        members = emptyList(),
        farePerPerson = 2_000,
        totalFare = 8_000,
        status = status,
    )
}
