package com.moyeota.data.remote

import com.moyeota.data.remote.dto.MatchCandidateResponse
import com.moyeota.domain.model.RideStatus
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「걸어서 가까운 방」 추천 응답(`POST /matching/rooms/realtime`) 파싱과 매핑.
 *
 * 이 응답은 기존 목록과 **모양이 다른 데가 세 군데**라 복사해 쓰면 조용히 틀린다:
 * 최상위가 배열이고(`{"list":[...]}` 래퍼 없음), 방 id 필드명이 `partyId` 가 아니라 `id` 고,
 * 좌표 필드명이 요청(`destLat`)과 응답(`destinationLat`)에서 서로 다르다.
 */
class MatchCandidateMapperTest {

    private val json = Json { ignoreUnknownKeys = true }

    /** 서버가 주는 모양 그대로. 최상위 배열 + 중첩 summary. */
    @Test
    fun `최상위 배열과 중첩 summary 를 읽는다`() {
        val body = """
            [
              {"summary":{"id":123,"departureLat":37.4979,"departureLng":127.0276,
                          "destinationLat":37.5006,"destinationLng":127.0364,
                          "departure":"강남역 2번 출구","destination":"선릉역","capacity":3},
               "totalSeconds":412}
            ]
        """.trimIndent()

        val candidates = json.decodeFromString<List<MatchCandidateResponse>>(body)
            .mapNotNull { it.toMatchCandidate() }

        assertEquals(1, candidates.size)
        val first = candidates.first()
        assertEquals("123", first.ride.id)
        assertEquals("강남역 2번 출구", first.ride.origin)
        assertEquals("선릉역", first.ride.destination)
        assertEquals(3, first.ride.capacity)
        assertEquals(412, first.walkSeconds)
    }

    /** 뷰포트 목록에는 없던 **도착 좌표**가 온다 — 지도에 경로를 그릴 수 있다는 뜻이라 흘리면 안 된다. */
    @Test
    fun `출발 좌표와 도착 좌표를 모두 싣는다`() {
        val candidate = parseOne(
            """{"summary":{"id":1,"departureLat":37.1,"departureLng":127.1,
                           "destinationLat":37.2,"destinationLng":127.2},"totalSeconds":60}""",
        )

        assertEquals(37.1, candidate.ride.originLat!!, 1e-9)
        assertEquals(127.1, candidate.ride.originLng!!, 1e-9)
        assertEquals(37.2, candidate.ride.destinationLat!!, 1e-9)
        assertEquals(127.2, candidate.ride.destinationLng!!, 1e-9)
    }

    /**
     * **빈 배열은 정상이다.** 출발지 *그리고* 도착지가 각각 300m 안이어야 해서 초기에는 이게
     * 기본 상태다 — 에러로 올리면 사용자가 늘 실패 화면을 본다.
     */
    @Test
    fun `빈 배열은 빈 목록이다`() {
        val candidates = json.decodeFromString<List<MatchCandidateResponse>>("[]")
            .mapNotNull { it.toMatchCandidate() }

        assertTrue(candidates.isEmpty())
    }

    /** 서버가 이미 도보 시간 순으로 정렬했다 — **앱이 다시 정렬하면 안 된다.** 받은 순서가 계약이다. */
    @Test
    fun `받은 순서를 그대로 보존한다`() {
        val body = """
            [{"summary":{"id":9},"totalSeconds":900},
             {"summary":{"id":1},"totalSeconds":100},
             {"summary":{"id":5},"totalSeconds":500}]
        """.trimIndent()

        val ids = json.decodeFromString<List<MatchCandidateResponse>>(body)
            .mapNotNull { it.toMatchCandidate() }
            .map { it.ride.id }

        assertEquals(listOf("9", "1", "5"), ids)
    }

    /** `summary` 가 통째로 없으면 방 id 가 없다 — 탭해도 갈 곳이 없으니 **버린다**(터지지 않는다). */
    @Test
    fun `summary 가 없는 항목은 버린다`() {
        val body = """[{"totalSeconds":120},{"summary":{"id":7},"totalSeconds":60}]"""

        val candidates = json.decodeFromString<List<MatchCandidateResponse>>(body)
            .mapNotNull { it.toMatchCandidate() }

        assertEquals(listOf("7"), candidates.map { it.ride.id })
    }

    /** id 가 0 이면 존재할 수 없는 PK 다 — 그걸로 상세를 읽어 봐야 404 뿐이라 후보에서 뺀다. */
    @Test
    fun `id 가 0 인 항목은 버린다`() {
        val candidates = json.decodeFromString<List<MatchCandidateResponse>>(
            """[{"summary":{"id":0},"totalSeconds":60}]""",
        ).mapNotNull { it.toMatchCandidate() }

        assertTrue(candidates.isEmpty())
    }

    /** 필드가 빠져도 터지지 않는다 — 전부 박스 타입이라 서버가 null 을 보낼 수 있다. */
    @Test
    fun `좌표와 라벨이 없어도 읽는다`() {
        val candidate = parseOne("""{"summary":{"id":4},"totalSeconds":0}""")

        assertEquals("4", candidate.ride.id)
        assertEquals("", candidate.ride.origin)
        assertNull(candidate.ride.originLat)
        assertEquals(0, candidate.walkSeconds)
    }

    /**
     * **인원을 머릿수만큼 채운다**(Backend #260). 17 탐색 목록이 같은 숫자를 같은 방식으로 쓰고 있어
     * 카드 문구(「2/3명」)가 양쪽에서 일치한다. 요금은 여전히 응답에 없어 0 이다.
     */
    @Test
    fun `currentMembers 만큼 멤버 자리를 채운다`() {
        val candidate = parseOne("""{"summary":{"id":4,"capacity":3,"currentMembers":2},"totalSeconds":120}""")

        assertEquals(2, candidate.ride.members.size)
        assertEquals(3, candidate.ride.capacity)
        assertEquals(0, candidate.ride.farePerPerson)
        assertEquals(0, candidate.ride.totalFare)
        // 서버 쿼리가 status='ACTIVE' 로 고정이라 후보는 늘 모집 중인 방이다.
        assertEquals(RideStatus.RECRUITING, candidate.ride.status)
    }

    /**
     * **`currentMembers` 가 없으면 비워 둔다.** 옛 배포(#260 이전)를 보고 있다는 뜻이다.
     * 0 으로 접어 멤버를 0명 만들면 화면이 「0/3명」이라는 없는 상태를 그린다 — 후보는 모집 중인
     * 방만 오고 거기엔 최소 방장 1명이 있으므로 0 명인 방은 존재할 수 없다.
     */
    @Test
    fun `currentMembers 가 없으면 인원을 지어내지 않는다`() {
        val candidate = parseOne("""{"summary":{"id":4,"capacity":3},"totalSeconds":120}""")

        assertTrue("없는 멤버를 만들었다", candidate.ride.members.isEmpty())
    }

    /** 서버가 명시적 null 을 보내도 같다 — kotlinx 는 명시적 null 에 기본값을 쓰지 않는다. */
    @Test
    fun `currentMembers 가 명시적 null 이어도 비워 둔다`() {
        val candidate = parseOne("""{"summary":{"id":4,"capacity":3,"currentMembers":null},"totalSeconds":120}""")

        assertTrue("null 을 0 이 아닌 멤버로 바꿨다", candidate.ride.members.isEmpty())
    }

    /**
     * **멤버 이름을 믿으면 안 된다.** 서버는 개수만 주고 누가 들었는지는 안 준다. 자리표시자가
     * 「멤버 1」 같은 가짜 닉네임을 들고 있으므로 추천 카드는 **개수만** 쓴다. 이 테스트는 그
     * 전제를 못 박아 둔다 — 나중에 카드에 프로필을 붙이려 하면 여기서 걸린다.
     */
    @Test
    fun `자리표시자 멤버는 실제 사용자가 아니다`() {
        val candidate = parseOne("""{"summary":{"id":4,"capacity":3,"currentMembers":2},"totalSeconds":120}""")

        assertTrue("실제 사용자처럼 보이는 값이 들어갔다", candidate.ride.members.all { it.rating == 0.0 && it.rideCount == 0 })
    }

    private fun parseOne(body: String) =
        json.decodeFromString<MatchCandidateResponse>(body).toMatchCandidate()!!
}
