package com.moyeota.data.remote

import com.moyeota.data.remote.dto.RideRecordMemberResponse
import com.moyeota.data.remote.dto.RideRecordPageResponse
import com.moyeota.domain.model.WithdrawnMemberNames
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `GET /api/v1/users/me/parties` 응답 → 도메인.
 *
 * ### 왜 JSON 전문을 문자열로 박는가
 * `NetworkModule` 의 Json 이 `ignoreUnknownKeys = true` 다. 그래서 DTO 의 필드 이름이 하나만
 * 틀려도 **예외가 아니라 조용한 기본값**이 된다 — 화면에는 「0원」·빈 이름·「0명」이 뜨고
 * 아무 로그도 남지 않는다. 애노테이션을 눈으로 보는 검사로는 못 잡고, 실제 서버 문자열을
 * 디코딩해 보는 이 테스트만이 잡는다.
 *
 * 아래 JSON 은 **실측 전문**이다(2026-11-01, 로컬 `:8080`, 기사 배차 흐름을 끝까지 돌려
 * FINISHED 로 만든 party 69·67). 서버가 계약을 바꾸면 이 테스트부터 깨지는 게 정상이다.
 */
class RideRecordMappersTest {

    private val json = Json { ignoreUnknownKeys = true }

    /** 실측 응답(기록 2건, 마지막 페이지). 멤버 한 명이 나 자신이다. */
    private val twoRecordsJson = """
        {"rides":[{"partyId":69,
          "departure":"강남역",
          "destination":"판교역",
          "pickupTime":"2026-10-31T19:15:17.550211Z",
          "dropoffTime":"2026-10-31T19:15:17.571486Z",
          "totalFare":16500,
          "farePerPerson":16500,
          "currentMembers":1,
          "members":[{"publicId":"01a19361-7771-7893-a836-273e05867193",
                      "nickname":"프로브9911",
                      "imageUrl":"https://cdn.moyeota.co.kr/profile/presets/default.png"}]},
         {"partyId":67,
          "departure":"강남역",
          "destination":"판교역",
          "pickupTime":"2026-10-31T19:14:39.245151Z",
          "dropoffTime":"2026-10-31T19:14:39.296191Z",
          "totalFare":16500,
          "farePerPerson":16500,
          "currentMembers":1,
          "members":[{"publicId":"01a19361-7771-7893-a836-273e05867193",
                      "nickname":"프로브9911",
                      "imageUrl":"https://cdn.moyeota.co.kr/profile/presets/default.png"}]}],
         "nextCursor":null,"hasNext":false}
    """.trimIndent()

    private fun decode(raw: String) = json.decodeFromString<RideRecordPageResponse>(raw)

    /**
     * 필드 9개 + 중첩 멤버 3개가 **이름 그대로** 실려 오는지. 이름 하나가 틀리면
     * `ignoreUnknownKeys` 때문에 조용히 기본값이 되어 화면이 거짓 숫자를 그린다.
     */
    @Test
    fun `서버 응답 한 건이 그대로 도메인 기록이 된다`() {
        val page = decode(twoRecordsJson).toRideRecordPage(currentUuid = "01a19361-7771-7893-a836-273e05867193")

        assertEquals(2, page.records.size)
        val first = page.records.first()
        assertEquals(69L, first.partyId)
        assertEquals("강남역", first.departure)
        assertEquals("판교역", first.destination)
        assertEquals("2026-10-31T19:15:17.550211Z", first.pickupTime)
        assertEquals("2026-10-31T19:15:17.571486Z", first.dropoffTime)
        assertEquals(16500, first.totalFare)
        assertEquals(16500, first.farePerPerson)
        assertEquals(1, first.memberCount)

        val member = first.members.single()
        assertEquals("01a19361-7771-7893-a836-273e05867193", member.id)
        assertEquals("프로브9911", member.nickname)
        assertEquals("https://cdn.moyeota.co.kr/profile/presets/default.png", member.imageUrl)
    }

    /** 서버 정렬은 `dropoffTime desc, matchId desc` 다. 매퍼가 순서를 섞으면 최신순이 깨진다. */
    @Test
    fun `서버가 준 최신순 순서를 그대로 유지한다`() {
        val page = decode(twoRecordsJson).toRideRecordPage(currentUuid = null)

        assertEquals(listOf(69L, 67L), page.records.map { it.partyId })
    }

    /**
     * 1차 모드(기사 없이 방장이 합승 종료)로 끝난 기록. 요금과 탑승 시각이 null 로 **남아야** 한다
     * — 0 으로 접으면 화면이 「0원」을 그려 「공짜로 탔다」는 거짓말이 된다.
     */
    @Test
    fun `1차 모드 기록은 요금과 탑승 시각이 비어 있다`() {
        val raw = """
            {"rides":[{"partyId":12,"departure":"부산대 정문","destination":"서면역",
              "pickupTime":null,"dropoffTime":"2026-10-20T09:03:00Z",
              "totalFare":null,"farePerPerson":null,"currentMembers":3,"members":[]}],
             "nextCursor":null,"hasNext":false}
        """.trimIndent()

        val record = decode(raw).toRideRecordPage(currentUuid = null).records.single()

        assertNull("탑승 시각이 0 이나 빈 문자열로 접혔다", record.pickupTime)
        assertNull("총 요금이 0 으로 접혔다 — 화면이 「0원」을 그린다", record.totalFare)
        assertNull("1인 부담이 0 으로 접혔다 — 화면이 「0원」을 그린다", record.farePerPerson)
        // 하차 시각과 인원은 그대로 와야 한다(요금만 없는 기록이다)
        assertEquals("2026-10-20T09:03:00Z", record.dropoffTime)
        assertEquals(3, record.memberCount)
    }

    /** 서버가 `isMe` 를 주지 않으므로 매퍼가 publicId 비교로 만든다. 한 명만 나여야 한다. */
    @Test
    fun `내 publicId 가 들어 있는 멤버만 나로 표시된다`() {
        val raw = """
            {"rides":[{"partyId":5,"departure":"A","destination":"B",
              "pickupTime":null,"dropoffTime":"2026-10-20T09:03:00Z",
              "totalFare":null,"farePerPerson":null,"currentMembers":2,
              "members":[{"publicId":"me-uuid","nickname":"나","imageUrl":null},
                         {"publicId":"other-uuid","nickname":"남","imageUrl":null}]}],
             "nextCursor":null,"hasNext":false}
        """.trimIndent()

        val members = decode(raw).toRideRecordPage(currentUuid = "me-uuid").records.single().members

        assertEquals(listOf(true, false), members.map { it.isMe })
    }

    /**
     * ★ `null == null` 이 true 라서 **미로그인 상태에서 요약 없는 멤버가 "나"가 되는** 버그를 못 박는다.
     *
     * 이 묶음에서 가장 쉽게 틀리는 자리다 — `publicId == currentUuid` 만 쓰면 둘 다 null 인 조합이
     * 통과한다. `PartyMappers` 가 같은 가드(`publicId != null &&`)를 쓰는 이유다.
     */
    @Test
    fun `미로그인 상태에서는 아무도 나로 표시되지 않는다`() {
        val raw = """
            {"rides":[{"partyId":5,"departure":"A","destination":"B",
              "pickupTime":null,"dropoffTime":"2026-10-20T09:03:00Z",
              "totalFare":null,"farePerPerson":null,"currentMembers":1,
              "members":[{"publicId":null,"nickname":null,"imageUrl":null}]}],
             "nextCursor":null,"hasNext":false}
        """.trimIndent()

        val member = decode(raw).toRideRecordPage(currentUuid = null).records.single().members.single()

        assertFalse("요약 없는 멤버가 미로그인 상태에서 「나」가 됐다", member.isMe)
    }

    /**
     * 탈퇴한 동승자는 `publicId` 가 **살아 있고** 이름만 서버가 가려 보낸다. 앱이 다시 치환하지
     * 않는다는 것 — 치환하면 「요약이 없다」와 「탈퇴했다」가 한 문자열로 합쳐져
     * 「프로필로 들어갈 수 있는가」 판정이 사라진다(`MemberDisplay` KDoc 의 그 구분).
     */
    @Test
    fun `탈퇴한 동승자는 이름이 탈퇴한 사용자로 그대로 온다`() {
        val member = RideRecordMemberResponse(
            publicId = "withdrawn-uuid",
            nickname = WithdrawnMemberNames.SERVER_DELETED,
            imageUrl = "https://cdn.moyeota.co.kr/profile/presets/default.png",
        ).toRideRecordMember(currentUuid = "me-uuid")

        assertEquals("withdrawn-uuid", member.id)
        assertEquals(WithdrawnMemberNames.SERVER_DELETED, member.nickname)
        assertFalse(member.isMe)
    }

    /**
     * 요약 자체가 없는 멤버(세 필드 전부 null). 식별자가 **빈 문자열**이어야 한다 — 화면이
     * 「탭 불가」로 다룰 근거가 그것이고(`MemberDisplay.isWithdrawn`), 이름은 앱이 채운 자리값이다.
     */
    @Test
    fun `정보가 없는 멤버는 식별자가 빈 문자열이다`() {
        val member = RideRecordMemberResponse().toRideRecordMember(currentUuid = "me-uuid")

        assertEquals("", member.id)
        assertEquals(WithdrawnMemberNames.NO_SUMMARY, member.nickname)
        assertNull(member.imageUrl)
        assertFalse(member.isMe)
    }

    /**
     * 마지막 페이지. **`hasNext=false` 인데 `nextCursor` 를 읽어 다음 페이지를 또 부르면
     * 커서 없이 첫 페이지를 다시 받아 목록이 무한히 중복된다.**
     */
    @Test
    fun `마지막 페이지는 다음 커서가 없다`() {
        val page = decode(twoRecordsJson).toRideRecordPage(currentUuid = null)

        assertFalse(page.hasNext)
        assertNull(page.nextCursor)
    }

    /** 실측 1페이지(`size=1`): 커서는 그 페이지 **마지막 기록의 방 id** 다. */
    @Test
    fun `다음 페이지가 있으면 커서가 마지막 기록의 방 id 다`() {
        val raw = """
            {"rides":[{"partyId":69,"departure":"강남역","destination":"판교역",
              "pickupTime":"2026-10-31T19:15:17.550211Z","dropoffTime":"2026-10-31T19:15:17.571486Z",
              "totalFare":16500,"farePerPerson":16500,"currentMembers":1,
              "members":[{"publicId":"u","nickname":"프로브9911","imageUrl":null}]}],
             "nextCursor":69,"hasNext":true}
        """.trimIndent()

        val page = decode(raw).toRideRecordPage(currentUuid = null)

        assertTrue(page.hasNext)
        assertEquals(69L, page.nextCursor)
        assertEquals(69L, page.records.last().partyId)
    }

    /** 기록이 없는 계정(실측: `moyeota2`). 빈 페이지가 예외 없이 나와야 한다. */
    @Test
    fun `기록이 없으면 빈 페이지다`() {
        val page = decode("""{"rides":[],"nextCursor":null,"hasNext":false}""")
            .toRideRecordPage(currentUuid = "me-uuid")

        assertTrue(page.records.isEmpty())
        assertNull(page.nextCursor)
        assertFalse(page.hasNext)
    }

    /**
     * 출발·도착이 없는 응답은 **빈 문자열로 접는다** — 화면이 null 을 또 확인하지 않게.
     * 요금과 달리 「없으면 비워 둔다」 하나뿐이라 null 을 끝까지 들고 갈 이유가 없다.
     */
    @Test
    fun `출발지와 도착지가 없으면 빈 문자열이 된다`() {
        val raw = """
            {"rides":[{"partyId":5,"departure":null,"destination":null,
              "pickupTime":null,"dropoffTime":"2026-10-20T09:03:00Z",
              "totalFare":null,"farePerPerson":null,"currentMembers":1,"members":[]}],
             "nextCursor":null,"hasNext":false}
        """.trimIndent()

        val record = decode(raw).toRideRecordPage(currentUuid = null).records.single()

        assertEquals("", record.departure)
        assertEquals("", record.destination)
    }
}
