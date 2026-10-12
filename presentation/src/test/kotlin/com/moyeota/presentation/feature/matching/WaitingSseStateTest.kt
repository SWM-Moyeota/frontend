package com.moyeota.presentation.feature.matching

import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RideStatus
import com.moyeota.domain.model.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 21 대기 화면이 **SSE 변경분만으로 방 상태를 만드는** 규칙.
 *
 * 이 화면은 이제 평상시 서버를 주기적으로 부르지 않는다 — 들어오고 나가는 사람은 변경분으로 오고,
 * 그 변경분을 들고 있던 방에 **더하고 빼서** 화면을 만든다. 그래서 화면의 정확도가 통째로 이 함수들에 달려 있다:
 * 여기서 한 번 틀어지면 되돌릴 폴링이 없고(평상시 0회), 다음 재연결 스냅샷까지 틀린 인원이 떠 있게 된다.
 *
 * 서버가 지문(fingerprint)과 상태 조회 API 를 지우면서(Backend #221) 「바뀌었나」를 싸게 묻던 장치도 사라졌다 —
 * 갱신의 근거는 이제 **스냅샷·변경분·(끊겼을 때만) 상세** 셋뿐이다.
 */
class WaitingSseStateTest {

    private fun user(id: String, isMe: Boolean = false, rideCount: Int = 0) = User(
        id = id,
        nickname = "승객$id",
        verifiedLabel = "",
        rating = 0.0,
        rideCount = rideCount,
        isMe = isMe,
    )

    private val me = user("me", isMe = true)

    private fun ride(
        status: RideStatus = RideStatus.RECRUITING,
        capacity: Int = 4,
        members: List<User> = listOf(me),
    ) = Ride(
        id = "1",
        origin = "부산대",
        destination = "서면",
        departureLabel = "지금 출발",
        capacity = capacity,
        members = members,
        farePerPerson = 0,
        totalFare = 0,
        status = status,
    )

    // ─── 입장 ───────────────────────────────────────────────────────────────

    @Test
    fun `입장 - 멤버를 더하고 서버가 준 상태로 바꾼다`() {
        val before = ride(status = RideStatus.RECRUITING, members = listOf(me))

        val after = before.withMemberJoined(user("u2"), RideStatus.RECRUITING)

        assertEquals(listOf("me", "u2"), after.members.map { it.id })
        assertEquals(RideStatus.RECRUITING, after.status)
    }

    @Test
    fun `입장 - 마지막 자리를 채우면 서버가 실어 준 전이가 그대로 반영된다`() {
        // 서버는 정원이 차면 그 자리에서 정원 충족(동승 모드) · 배차 시작(택시 모드)까지 끝내고
        // 그 결과 상태를 member-joined 에 싣는다 — 앱이 따로 물어볼 필요가 없다.
        val before = ride(status = RideStatus.RECRUITING, capacity = 2, members = listOf(me))

        val completed = before.withMemberJoined(user("u2"), RideStatus.MATCHED)
        assertEquals(RideStatus.MATCHED, completed.status)

        val dispatching = before.withMemberJoined(user("u2"), RideStatus.DISPATCHING)
        assertEquals(RideStatus.DISPATCHING, dispatching.status)
    }

    @Test
    fun `입장 - 같은 publicId 는 자리를 지키며 교체한다 (인원이 부풀지 않는다)`() {
        val before = ride(members = listOf(me, user("u2", rideCount = 3), user("u3")))

        val after = before.withMemberJoined(user("u2", rideCount = 9), RideStatus.RECRUITING)

        assertEquals(listOf("me", "u2", "u3"), after.members.map { it.id })
        assertEquals(3, after.members.size)
        assertEquals(9, after.members[1].rideCount) // 새 값으로 덮였다
    }

    @Test
    fun `입장 - isMe 는 온 그대로 쓴다 (화면이 다시 판정하지 않는다)`() {
        // 데이터 계층이 상세와 같은 근거(publicId == 세션 uuid)로 채워 준다.
        val before = ride(members = listOf(user("u2")))

        val after = before.withMemberJoined(user("me2", isMe = true), RideStatus.RECRUITING)

        assertTrue(after.members.single { it.id == "me2" }.isMe)
        assertFalse(after.members.single { it.id == "u2" }.isMe)
    }

    @Test
    fun `입장 - id 가 빈 멤버끼리는 같다고 보지 않는다 (구별할 근거가 없다)`() {
        // 탈퇴 등으로 서버가 유저 요약을 못 만들면 publicId 가 빈 문자열로 온다.
        // 빈 id 끼리 같다고 보면 서로 다른 사람을 덮어쓴다.
        val before = ride(members = listOf(me, user("")))

        val after = before.withMemberJoined(user(""), RideStatus.RECRUITING)

        assertEquals(3, after.members.size)
    }

    // ─── 퇴장 ───────────────────────────────────────────────────────────────

    @Test
    fun `퇴장 - publicId 로 그 한 명만 지우고 상태를 바꾼다`() {
        val before = ride(status = RideStatus.MATCHED, members = listOf(me, user("u2"), user("u3")))

        val after = before.withMemberLeft("u2", RideStatus.RECRUITING)

        assertEquals(listOf("me", "u3"), after.members.map { it.id })
        assertEquals(RideStatus.RECRUITING, after.status)
    }

    @Test
    fun `퇴장 - 모르는 publicId 면 목록은 그대로 두고 상태만 반영한다`() {
        // 그 사람의 입장 신호를 놓쳤다는 뜻이다. 없는 사람을 지우려 상세를 또 읽지는 않는다.
        val before = ride(status = RideStatus.MATCHED, members = listOf(me, user("u2")))

        val after = before.withMemberLeft("u9", RideStatus.RECRUITING)

        assertEquals(listOf("me", "u2"), after.members.map { it.id })
        assertEquals(RideStatus.RECRUITING, after.status)
    }

    @Test
    fun `퇴장 - 빈 publicId 로는 아무도 지우지 않는다`() {
        // 요약이 없는 멤버(빈 id)들을 한꺼번에 날리는 사고를 막는다.
        val before = ride(members = listOf(me, user(""), user("")))

        val after = before.withMemberLeft("", RideStatus.RECRUITING)

        assertEquals(3, after.members.size)
    }

    @Test
    fun `들어왔다 나가면 원래 목록으로 돌아온다`() {
        val before = ride(members = listOf(me, user("u2")))

        val after = before
            .withMemberJoined(user("u3"), RideStatus.RECRUITING)
            .withMemberLeft("u3", RideStatus.RECRUITING)

        assertEquals(before.members, after.members)
    }

    @Test
    fun `증분은 멤버와 상태만 건드린다 - 경로·요금·좌표는 스냅샷 값 그대로다`() {
        val before = ride().copy(
            farePerPerson = 3_500,
            totalFare = 14_000,
            originLat = 35.23,
            originLng = 129.08,
            routePolyline = "abc",
            estimatedFare = 14_000,
            estimatedMinutes = 21,
            departureRadiusMeters = 500,
        )

        val joined = before.withMemberJoined(user("u2"), RideStatus.RECRUITING)
        val left = joined.withMemberLeft("u2", RideStatus.RECRUITING)

        assertEquals(before, left)
    }

    // ─── 어긋남 감지 ────────────────────────────────────────────────────────

    @Test
    fun `어긋남 - 정원이 찼다는데 목록이 모자라면 상세로 보정한다`() {
        assertTrue(hasMemberDrift(ride(status = RideStatus.MATCHED, capacity = 4, members = listOf(me))))
    }

    @Test
    fun `어긋남 - 정원이 찼고 목록도 찼으면 아무것도 안 한다`() {
        val full = ride(
            status = RideStatus.MATCHED,
            capacity = 2,
            members = listOf(me, user("u2")),
        )
        assertFalse(hasMemberDrift(full))
    }

    @Test
    fun `어긋남 - 모집 중인데 목록이 정원을 채웠으면 상세로 보정한다`() {
        // 내 목록엔 다 찬 것처럼 보이는데 서버는 아직 모집 중이다 = 퇴장 신호를 놓쳤다.
        val stale = ride(
            status = RideStatus.RECRUITING,
            capacity = 2,
            members = listOf(me, user("u2")),
        )
        assertTrue(hasMemberDrift(stale))
    }

    @Test
    fun `어긋남 - 모집 중이고 자리가 남았으면 정상이다`() {
        assertFalse(hasMemberDrift(ride(status = RideStatus.RECRUITING, capacity = 4, members = listOf(me))))
    }

    @Test
    fun `어긋남 - 배차·종료 단계는 보지 않는다 (화면이 이미 넘어간다)`() {
        for (status in listOf(RideStatus.DISPATCHING, RideStatus.ONGOING, RideStatus.COMPLETED, RideStatus.CANCELED)) {
            assertFalse(hasMemberDrift(ride(status = status, capacity = 4, members = listOf(me))))
        }
    }

    @Test
    fun `어긋남 - 정원을 모르는 방은 판단하지 않는다`() {
        assertFalse(hasMemberDrift(ride(status = RideStatus.RECRUITING, capacity = 0, members = emptyList())))
    }

    // ─── 폴링 정책 ──────────────────────────────────────────────────────────

    @Test
    fun `폴링 - SSE 가 살아 있고 방이 떠 있으면 한 번도 부르지 않는다`() {
        assertFalse(shouldPollFallback(realtimeAlive = true, hasRide = true))
    }

    @Test
    fun `폴링 - 끊긴 동안에만 폴백이 돈다`() {
        assertTrue(shouldPollFallback(realtimeAlive = false, hasRide = true))
    }

    @Test
    fun `폴링 - 화면에 방이 없으면 SSE 가 살아 있어도 읽는다 (에러 화면에 갇히지 않게)`() {
        // 변경분은 바탕이 있을 때만 쓸 수 있다 — 첫 조회가 실패한 채로 두면 다음 멤버 변화까지 에러다.
        assertTrue(shouldPollFallback(realtimeAlive = true, hasRide = false))
        assertTrue(shouldPollFallback(realtimeAlive = false, hasRide = false))
    }

    @Test
    fun `대기 단계는 모집 중과 정원 찬 상태뿐 - 그 밖이면 폴백을 끝낸다`() {
        assertTrue(isWaitingForMembers(RideStatus.RECRUITING))
        assertTrue(isWaitingForMembers(RideStatus.MATCHED))
        for (status in listOf(
            RideStatus.DISPATCHING,
            RideStatus.ONGOING,
            RideStatus.COMPLETED,
            RideStatus.CANCELED,
        )) {
            assertFalse(isWaitingForMembers(status))
        }
    }
}
