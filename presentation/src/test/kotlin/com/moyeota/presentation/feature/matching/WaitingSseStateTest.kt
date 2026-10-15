package com.moyeota.presentation.feature.matching

import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RideStatus
import com.moyeota.domain.model.User
import com.moyeota.domain.model.isHostedByMe
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

    private fun user(
        id: String,
        isMe: Boolean = false,
        rideCount: Int = 0,
        isHost: Boolean = false,
    ) = User(
        id = id,
        nickname = "승객$id",
        verifiedLabel = "",
        rating = 0.0,
        rideCount = rideCount,
        isMe = isMe,
        isHost = isHost,
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
        )

        val joined = before.withMemberJoined(user("u2"), RideStatus.RECRUITING)
        val left = joined.withMemberLeft("u2", RideStatus.RECRUITING)

        assertEquals(before, left)
    }

    // ─── 방장 승계 (Backend #240) ───────────────────────────────────────────

    /**
     * **이 테스트가 깨지면 방장이 나가도 승계된 사람이 「합승 완료」를 못 본다.**
     *
     * 서버는 멤버가 나갈 때마다 남은 멤버 중 `min(joinedAt, memberId)` 를 방장으로 다시 정하고,
     * 그 결과를 `member-left` 의 `hostPublicId` 로 **한 번만** 알려 준다 — 여기서 버리면 다음
     * 재연결 스냅샷까지 승계된 사람의 화면이 낡은 채로 남는다.
     */
    @Test
    fun `승계 - 방장이 나가면 서버가 지목한 사람이 방장이 된다`() {
        val before = ride(
            status = RideStatus.MATCHED,
            members = listOf(user("u1", isHost = true), me, user("u3")),
        )

        val after = before.withMemberLeft("u1", RideStatus.RECRUITING, hostPublicId = "me")

        assertEquals(listOf("me", "u3"), after.members.map { it.id })
        assertEquals(listOf(true, false), after.members.map { it.isHost })
        assertTrue(after.isHostedByMe)
    }

    /** 승계가 나에게 오지 않았으면 내 방장 표시는 **지워져야** 한다 — 서버 말이 곧 「방장은 한 명」이다. */
    @Test
    fun `승계 - 내가 아닌 사람이 방장이 되면 내 표시가 지워진다`() {
        val before = ride(members = listOf(me.copy(isHost = true), user("u2"), user("u3")))

        val after = before.withMemberLeft("u2", RideStatus.RECRUITING, hostPublicId = "u3")

        assertFalse(after.isHostedByMe)
        assertEquals(listOf("u3"), after.members.filter { it.isHost }.map { it.id })
    }

    /** 방장이 아닌 사람이 나가면 방장은 그대로다 — 서버가 같은 publicId 를 다시 지목한다. */
    @Test
    fun `승계 - 방장이 아닌 사람이 나가면 방장은 그대로다`() {
        val before = ride(members = listOf(me.copy(isHost = true), user("u2"), user("u3")))

        val after = before.withMemberLeft("u2", RideStatus.RECRUITING, hostPublicId = "me")

        assertTrue(after.isHostedByMe)
        assertEquals(1, after.members.count { it.isHost })
    }

    /**
     * 서버가 새 방장을 특정하지 못하면(`hostSummary == null`) 들고 있던 표시를 **그대로 둔다**.
     * 로그인한 본인의 요약은 늘 찾히므로 그 상황은 사실상 "새 방장은 내가 아니다" 이고, 모른다고
     * 전부 false 로 밀면 반대로 **진짜 방장의 표시를 지우는** 쪽이 위험하다.
     */
    @Test
    fun `승계 - hostPublicId 가 없으면 방장 표시를 손대지 않는다`() {
        val before = ride(members = listOf(me.copy(isHost = true), user("u2")))

        val after = before.withMemberLeft("u2", RideStatus.RECRUITING, hostPublicId = null)

        assertTrue(after.isHostedByMe)
        // 빈 문자열도 같다 — "모름"은 한 가지 모양으로만 다룬다.
        assertTrue(before.withMemberLeft("u2", RideStatus.RECRUITING, hostPublicId = "").isHostedByMe)
    }

    /**
     * 지목된 publicId 가 들고 있는 목록에 없으면 결과적으로 아무도 방장이 아니다 — 그게 맞다:
     * 서버가 나 아닌 누군가를 방장이라고 말한 것이므로 적어도 「내가 방장이다」는 거짓이다.
     * (빠진 멤버는 [hasMemberDrift] 나 다음 스냅샷이 메운다.)
     */
    @Test
    fun `승계 - 모르는 사람이 방장으로 지목되면 내 표시만 지워진다`() {
        val before = ride(members = listOf(me.copy(isHost = true), user("u2")))

        val after = before.withMemberLeft("u2", RideStatus.RECRUITING, hostPublicId = "u9")

        assertFalse(after.isHostedByMe)
        assertTrue(after.members.none { it.isHost })
    }

    /** id 가 빈 멤버(요약 없음)를 방장으로 세우지 않는다 — 빈 문자열끼리 맞아떨어지면 안 된다. */
    @Test
    fun `승계 - id 가 빈 멤버는 방장이 되지 않는다`() {
        val before = ride(members = listOf(me, user(""), user("")))

        val after = before.withMemberLeft("u9", RideStatus.RECRUITING, hostPublicId = "me")

        assertEquals(listOf("me"), after.members.filter { it.isHost }.map { it.id })
    }

    /**
     * 승계만 반영하는 경로(`member-left` 의 publicId 가 null 일 때 ViewModel 이 쓰는 자리) —
     * 멤버 목록은 건드리지 않고 방장 표시만 옮긴다.
     */
    @Test
    fun `승계 - 목록을 못 고치는 프레임에서도 방장 표시는 옮긴다`() {
        val before = ride(members = listOf(user("u1", isHost = true), me))

        val after = before.withHostSuccession("me")

        assertEquals(before.members.map { it.id }, after.members.map { it.id })
        assertTrue(after.isHostedByMe)
    }

    /**
     * ### delta 경로와 snapshot 경로가 같은 결과를 낸다 ★
     *
     * `connected` 스냅샷은 전체 교체라 서버가 계산한 `host` 가 그대로 들어온다. 증분 경로는 그 값을
     * 쌓아서 만든다 — 두 경로가 갈리면 "재연결 전에는 방장, 후에는 아님" 같은 어긋남이 생긴다.
     *
     * 같을 수 있는 근거: 서버가 싣는 `hostPublicId` 는 **이탈을 반영한 뒤** 읽은 `party.hostId()` 이고,
     * 스냅샷의 `host` 도 같은 `Party.isHost` 가 만든다. 그리고 합류는 방장을 바꿀 수 없다
     * (합류자는 가장 늦게 들어온 사람 = `min(joinedAt)` 이 될 수 없다).
     */
    @Test
    fun `승계 - 증분으로 만든 방장 표시가 스냅샷과 같다`() {
        val before = ride(
            status = RideStatus.MATCHED,
            capacity = 3,
            members = listOf(user("u1", isHost = true), me, user("u3")),
        )

        // delta: u1(방장) 이탈 → 서버가 me 를 승계로 지목
        val delta = before.withMemberLeft("u1", RideStatus.RECRUITING, hostPublicId = "me")

        // snapshot: 같은 시점의 상세를 서버가 통째로 다시 내려 준 모양
        val snapshot = before.copy(
            members = listOf(me.copy(isHost = true), user("u3")),
            status = RideStatus.RECRUITING,
        )

        assertEquals(snapshot, delta)
    }

    /** 합류는 방장을 바꾸지 않는다 — 합류자는 `min(joinedAt)` 이 될 수 없다(서버 보장). */
    @Test
    fun `승계 - 합류는 방장을 바꾸지 않는다`() {
        val before = ride(members = listOf(me.copy(isHost = true)))

        val after = before.withMemberJoined(user("u2"), RideStatus.RECRUITING)

        assertTrue(after.isHostedByMe)
        assertEquals(1, after.members.count { it.isHost })
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
