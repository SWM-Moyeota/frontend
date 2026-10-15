package com.moyeota.data.remote.matching

import com.moyeota.data.remote.toPartyEvent
import com.moyeota.domain.model.PartyEvent
import com.moyeota.domain.model.RideStatus
import com.moyeota.domain.model.isHostedByMe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 매칭방 SSE 계약 테스트 — **서버가 실제로 내보내는 문자열**을 그대로 넣는다
 * (`PartySseRegistry.subscribe` 의 `connected`, `RedisPartyChangeNotifier` 의 나머지 셋).
 *
 * 이 파일이 도는 이유는 하나다: SSE 프레임의 파싱 실패는 **화면에서 "아무 일도 안 일어남"으로 보인다**.
 * 예외도, 에러 화면도 없이 대기 화면이 영원히 1명으로 남는다 — 그래서 네트워크 없이 문자열만으로
 * 계약을 못박는다([parsePartySseFrame] 를 순수 함수로 뺀 이유).
 */
class PartySseEventTest {

    /** 구독 직후 `connected` 의 data — 방 상세(`PartyDetailResult`)를 Jackson 이 직렬화한 모양 그대로. */
    private val connectedData = """
        {"id":7,"departureLat":37.5665,"departureLng":126.978,"destinationLat":37.4979,
         "destinationLng":127.0276,"departure":"서울시청","destination":"강남역","capacity":3,
         "currentMembers":2,"departureRadius":500,"destinationRadius":500,"status":"ACTIVE",
         "createdAt":"2026-10-13T09:00:00Z",
         "members":[{"publicId":"$UUID_ME","nickname":"성윤","imageUrl":null,"badgeId":null,"rideCount":4},
                    {"publicId":"$UUID_OTHER","nickname":"철수","imageUrl":null,"badgeId":null,"rideCount":3}],
         "estimateFare":9600,"estimateTime":14,"route":"_p~iF~ps|U","taxiDriverId":null}
    """.trimIndent()

    @Test
    fun `connected 는 방 상세 전체를 스냅샷으로 올린다`() {
        val event = parse(EVENT_CONNECTED, connectedData).toPartyEvent(currentUuid = UUID_ME)

        val snapshot = event as PartyEvent.Snapshot
        assertEquals("7", snapshot.ride.id)
        assertEquals("서울시청", snapshot.ride.origin)
        assertEquals(RideStatus.RECRUITING, snapshot.ride.status)
        // 상세 조회와 똑같이 멤버·요금·경로까지 다 온다 — 그래서 catch-up 조회가 필요 없다.
        // (지문 필드는 서버가 지웠다 — `04f6be7`. 상세 응답과 같은 모양이라 여기서도 없다.)
        assertEquals(listOf("성윤", "철수"), snapshot.ride.members.map { it.nickname })
        assertEquals(listOf(true, false), snapshot.ride.members.map { it.isMe })
        assertEquals(9600, snapshot.ride.estimatedFare)
        assertEquals("_p~iF~ps|U", snapshot.ride.routePolyline)
    }

    /**
     * okhttp-sse 는 여러 줄 `data:` 를 개행문자로 이어 하나의 문자열로 준다.
     * 서버가 pretty-print 로 바뀌어도 스냅샷이 깨지지 않아야 한다.
     */
    @Test
    fun `여러 줄로 쪼개진 data 도 스냅샷으로 읽는다`() {
        val multiline = "{\n  \"id\": 7,\n  \"departure\": \"서울시청\",\n  \"status\": \"ACTIVE\"\n}"

        val snapshot = parse(EVENT_CONNECTED, multiline).toPartyEvent() as PartyEvent.Snapshot

        assertEquals("7", snapshot.ride.id)
        assertEquals("서울시청", snapshot.ride.origin)
    }

    @Test
    fun `member-joined 는 들어온 멤버와 변경 후 상태를 올린다`() {
        val data = """{"member":{"publicId":"$UUID_OTHER","nickname":"철수","imageUrl":null,"badgeId":null,"rideCount":3},"status":"ACTIVE"}"""

        val joined = parse(EVENT_MEMBER_JOINED, data).toPartyEvent(currentUuid = UUID_ME) as PartyEvent.MemberJoined

        assertEquals(UUID_OTHER, joined.member.id)
        assertEquals("철수", joined.member.nickname)
        assertEquals(3, joined.member.rideCount)
        assertFalse(joined.member.isMe)
        assertEquals(RideStatus.RECRUITING, joined.status)
    }

    /** 상세와 **같은 매퍼**를 쓰므로 "나" 판정 근거도 같다(publicId == 세션 uuid). */
    @Test
    fun `내가 다른 기기로 들어온 경우도 나로 표시한다`() {
        val data = """{"member":{"publicId":"$UUID_ME","nickname":"성윤","imageUrl":null,"badgeId":null,"rideCount":4},"status":"COMPLETED"}"""

        val joined = parse(EVENT_MEMBER_JOINED, data).toPartyEvent(currentUuid = UUID_ME) as PartyEvent.MemberJoined

        assertTrue(joined.member.isMe)
        // 마지막 자리를 채우면 서버가 그 자리에서 정원 충족까지 끝내고 그 결과를 싣는다.
        assertEquals(RideStatus.MATCHED, joined.status)
    }

    /** 유저 요약이 없으면 서버가 전부 null 인 member 를 싣는다 — 상세와 같은 "탈퇴한 회원" 처리. */
    @Test
    fun `요약이 없는 멤버가 들어와도 신호를 올린다`() {
        val data = """{"member":{"publicId":null,"nickname":null,"imageUrl":null,"badgeId":null,"rideCount":0},"status":"ACTIVE"}"""

        val joined = parse(EVENT_MEMBER_JOINED, data).toPartyEvent(currentUuid = UUID_ME) as PartyEvent.MemberJoined

        assertEquals("", joined.member.id)
        assertEquals("탈퇴한 회원", joined.member.nickname)
        assertFalse(joined.member.isMe)
    }

    @Test
    fun `member-left 는 나간 사람의 publicId 와 변경 후 상태를 올린다`() {
        val data = """{"publicId":"$UUID_OTHER","status":"ACTIVE"}"""

        val left = parse(EVENT_MEMBER_LEFT, data).toPartyEvent(currentUuid = UUID_ME) as PartyEvent.MemberLeft

        assertEquals(UUID_OTHER, left.publicId)
        assertEquals(RideStatus.RECRUITING, left.status)
    }

    /**
     * **publicId 는 null 로 온다** — 탈퇴 등으로 서버가 유저 요약을 못 찾으면 그대로 null 을 싣고도
     * 신호는 보낸다(`PartyService.leave`). 논-널로 선언했다면 이 프레임에서 파싱이 터져
     * 그 뒤의 모든 변화를 놓친다. 화면은 이때 상세를 다시 받아야 한다.
     */
    @Test
    fun `member-left 의 publicId 가 null 이어도 파싱이 깨지지 않는다`() {
        val left = parse(EVENT_MEMBER_LEFT, """{"publicId":null,"status":"ACTIVE"}""")
            .toPartyEvent(currentUuid = UUID_ME) as PartyEvent.MemberLeft

        assertNull(left.publicId)
        assertEquals(RideStatus.RECRUITING, left.status)
    }

    /** 빈 문자열도 null 과 같이 다룬다 — 화면이 "모름"을 한 가지 모양으로만 보게 한다. */
    @Test
    fun `member-left 의 publicId 가 빈 문자열이면 null 로 접는다`() {
        val left = parse(EVENT_MEMBER_LEFT, """{"publicId":"","status":"CANCELED"}""")
            .toPartyEvent() as PartyEvent.MemberLeft

        assertNull(left.publicId)
        assertEquals(RideStatus.CANCELED, left.status)
    }

    /**
     * **`closed` 의 data 는 JSON 이 아니라 partyId 숫자 문자열이다**(`String.valueOf(partyId)`).
     * 역직렬화를 시도하면 실패해 종결 신호를 놓치고, 화면은 닫힌 방에 계속 매달린다.
     */
    @Test
    fun `closed 의 data 는 숫자 문자열이지만 종결 신호로 읽는다`() {
        assertEquals(PartySseEvent.Closed, parse(EVENT_CLOSED, "42"))
        assertEquals(PartyEvent.Closed, parse(EVENT_CLOSED, "42").toPartyEvent())
    }

    /** 서버가 이벤트 종류를 늘려도 앱이 멈추지 않아야 한다 — 모르는 이름은 건너뛴다. */
    @Test
    fun `모르는 이벤트 이름은 건너뛴다`() {
        val frame = parsePartySseFrame("driver-assigned", """{"driverId":42}""")

        val skipped = frame as PartySseFrame.Skipped
        assertTrue(skipped.reason.contains("driver-assigned"))
    }

    /** 이름이 없는 프레임(SSE 기본 이벤트 `message`)도 같은 길로 흘려보낸다. */
    @Test
    fun `이벤트 이름이 없으면 건너뛴다`() {
        assertTrue(parsePartySseFrame(null, "{}") is PartySseFrame.Skipped)
    }

    /**
     * **깨진 프레임 하나로 스트림을 죽이지 않는다.** 예외를 올리면 재연결 루프가 돌고, 서버가 같은 프레임을
     * 다시 보내면 영원히 끊겼다 붙기만 한다 — 건너뛰고 다음 스냅샷으로 보정하는 쪽이 낫다.
     */
    @Test
    fun `깨진 JSON 은 예외가 아니라 건너뜀으로 떨어진다`() {
        assertTrue(parsePartySseFrame(EVENT_MEMBER_JOINED, "not json at all") is PartySseFrame.Skipped)
        assertTrue(parsePartySseFrame(EVENT_MEMBER_LEFT, """{"publicId":}""") is PartySseFrame.Skipped)
        // connected 의 id 는 기본값이 없는 유일한 필드다 — 빠지면 스냅샷을 못 만든다(죽지는 않는다).
        assertTrue(parsePartySseFrame(EVENT_CONNECTED, """{"departure":"서울시청"}""") is PartySseFrame.Skipped)
    }

    /** 키가 빠진 프레임도 건너뛰기보다 "요약 없는 멤버"로 살려 둔다 — 인원 증가는 전해져야 한다. */
    @Test
    fun `member 키가 빠져도 입장 신호는 살린다`() {
        val joined = parse(EVENT_MEMBER_JOINED, """{"status":"ACTIVE"}""")
            .toPartyEvent() as PartyEvent.MemberJoined

        assertEquals("탈퇴한 회원", joined.member.nickname)
        assertEquals(RideStatus.RECRUITING, joined.status)
    }

    /** 모르는 상태 문자열은 상세·목록과 **같은 표**로 떨어진다 — 경로가 갈리면 화면 단계가 어긋난다. */
    @Test
    fun `모르는 상태 문자열은 모집 중으로 떨어진다`() {
        val left = parse(EVENT_MEMBER_LEFT, """{"publicId":"$UUID_OTHER","status":"SOMETHING_NEW"}""")
            .toPartyEvent() as PartyEvent.MemberLeft

        assertEquals(RideStatus.RECRUITING, left.status)
    }

    /** 끊김은 서버 프레임이 아니라 앱이 만든 신호다 — 도메인까지 그대로 올라간다. */
    @Test
    fun `끊김 신호는 그대로 도메인으로 올라간다`() {
        assertEquals(PartyEvent.Disconnected, PartySseEvent.Disconnected.toPartyEvent())
    }

    // ─────────────────────────────── 방장(Backend #240) ───────────────────────────────

    /**
     * `connected` 스냅샷에도 `host` 가 실린다 — 상세 조회와 **같은 record** 이므로 당연하지만,
     * 스냅샷이 전체 교체이기 때문에 **방장 표시의 기준선**이 바로 이 프레임이다.
     */
    @Test
    fun `connected 스냅샷이 멤버의 host 를 그대로 올린다`() {
        val data = connectedData.replace(
            """"badgeId":null,"rideCount":4""",
            """"badgeId":null,"rideCount":4,"host":true""",
        ).replace(
            """"badgeId":null,"rideCount":3""",
            """"badgeId":null,"rideCount":3,"host":false""",
        )

        val snapshot = parse(EVENT_CONNECTED, data).toPartyEvent(currentUuid = UUID_ME) as PartyEvent.Snapshot

        assertEquals(listOf(true, false), snapshot.ride.members.map { it.isHost })
        assertTrue(snapshot.ride.isHostedByMe)
    }

    /**
     * **키 이름은 `isHost` 가 아니라 `host` 다**(서버 record 컴포넌트 이름 그대로). 틀리게 적으면
     * `ignoreUnknownKeys` 가 흘려서 **조용히 늘 누락**이 되므로, 틀린 이름이 먹히지 않음을 못박는다.
     */
    @Test
    fun `isHost 라는 키는 방장으로 읽히지 않는다 — 계약 키는 host 다`() {
        val data = """{"id":7,"status":"ACTIVE","members":[
            {"publicId":"$UUID_OTHER","nickname":"철수","rideCount":0,"isHost":true}]}"""

        val snapshot = parse(EVENT_CONNECTED, data).toPartyEvent(currentUuid = UUID_ME) as PartyEvent.Snapshot

        // 내가 아닌 멤버 + host 누락 → false. isHost 키를 읽었다면 true 가 됐을 것이다.
        assertFalse(snapshot.ride.members.single().isHost)
    }

    /** `host` 가 없는 옛 스냅샷에서도 **나만** 방장으로 떨어진다 — 근거는 `PartyMappers.toUser` KDoc. */
    @Test
    fun `host 가 없는 스냅샷에서는 나만 방장으로 떨어진다`() {
        val snapshot = parse(EVENT_CONNECTED, connectedData)
            .toPartyEvent(currentUuid = UUID_ME) as PartyEvent.Snapshot

        assertEquals(listOf(true, false), snapshot.ride.members.map { it.isHost })
        assertTrue(snapshot.ride.isHostedByMe)
    }

    /**
     * `member-joined` 의 본문은 Backend #240 에서 바뀌지 않았지만, 품고 있는 `MemberInfo` 에
     * `host` 가 실려 온다(`PartyService.join` 이 `saved.isHost(memberId)` 로 채운다).
     * 합류자는 가장 늦게 들어온 사람이라 사실상 늘 false 지만, 앱은 **서버 값을 그대로** 옮긴다.
     */
    @Test
    fun `member-joined 의 멤버에도 host 가 실려 온다`() {
        val data = """{"member":{"publicId":"$UUID_OTHER","nickname":"철수","imageUrl":null,
            "badgeId":null,"rideCount":3,"host":false},"status":"ACTIVE"}"""

        val joined = parse(EVENT_MEMBER_JOINED, data).toPartyEvent(currentUuid = UUID_ME) as PartyEvent.MemberJoined

        assertFalse(joined.member.isHost)
    }

    /**
     * **승계 신호** — `member-left` 의 `hostPublicId` 는 서버가 이탈을 반영한 **뒤** 읽은 방장이다.
     * 이 값을 버리면 방장이 나간 뒤 승계된 사람이 자기 권한을 모른다(서버는 다시 보내지 않는다).
     */
    @Test
    fun `member-left 는 승계 후 방장의 publicId 를 올린다`() {
        val data = """{"publicId":"$UUID_OTHER","status":"ACTIVE","hostPublicId":"$UUID_ME"}"""

        val left = parse(EVENT_MEMBER_LEFT, data).toPartyEvent(currentUuid = UUID_ME) as PartyEvent.MemberLeft

        assertEquals(UUID_OTHER, left.publicId)
        assertEquals(UUID_ME, left.hostPublicId)
        assertEquals(RideStatus.RECRUITING, left.status)
    }

    /**
     * 승계된 멤버의 유저 요약을 서버가 못 찾으면 `hostPublicId` 가 null 이다
     * (`PartyService.leave` 의 `hostSummary == null`). 받는 쪽은 "새 방장을 모름"으로 다뤄야 한다.
     */
    @Test
    fun `member-left 의 hostPublicId 가 null 이어도 파싱이 깨지지 않는다`() {
        val left = parse(EVENT_MEMBER_LEFT, """{"publicId":"$UUID_OTHER","status":"ACTIVE","hostPublicId":null}""")
            .toPartyEvent(currentUuid = UUID_ME) as PartyEvent.MemberLeft

        assertNull(left.hostPublicId)
    }

    /** 옛 서버는 `hostPublicId` 를 아예 싣지 않는다 — 누락도 "모름"(null)으로 같게 떨어진다. */
    @Test
    fun `hostPublicId 가 없는 member-left 도 그대로 읽는다`() {
        val left = parse(EVENT_MEMBER_LEFT, """{"publicId":"$UUID_OTHER","status":"ACTIVE"}""")
            .toPartyEvent(currentUuid = UUID_ME) as PartyEvent.MemberLeft

        assertEquals(UUID_OTHER, left.publicId)
        assertNull(left.hostPublicId)
    }

    /** publicId 와 같은 이유로 빈 문자열까지 null 로 접는다 — "모름"은 한 가지 모양이어야 한다. */
    @Test
    fun `hostPublicId 가 빈 문자열이면 null 로 접는다`() {
        val left = parse(EVENT_MEMBER_LEFT, """{"publicId":"$UUID_OTHER","status":"ACTIVE","hostPublicId":""}""")
            .toPartyEvent() as PartyEvent.MemberLeft

        assertNull(left.hostPublicId)
    }

    /** 누가 나갔는지 모르는 프레임(publicId null)에도 승계는 실린다 — 둘은 서로 독립이다. */
    @Test
    fun `나간 사람을 몰라도 승계 후 방장은 전해진다`() {
        val left = parse(EVENT_MEMBER_LEFT, """{"publicId":null,"status":"ACTIVE","hostPublicId":"$UUID_ME"}""")
            .toPartyEvent(currentUuid = UUID_ME) as PartyEvent.MemberLeft

        assertNull(left.publicId)
        assertEquals(UUID_ME, left.hostPublicId)
    }

    private fun parse(type: String, data: String): PartySseEvent =
        (parsePartySseFrame(type, data) as PartySseFrame.Received).event

    private companion object {
        /** 서버가 내려보내는 UUID v7 문자열 형식 그대로. 로그인 세션의 `sub` 와 같은 값을 가정한다. */
        const val UUID_ME = "01a06145-3caf-7614-a3bd-cee6e25316b1"
        const val UUID_OTHER = "01a06145-3caf-7614-a3bd-cee6e2531999"
    }
}
