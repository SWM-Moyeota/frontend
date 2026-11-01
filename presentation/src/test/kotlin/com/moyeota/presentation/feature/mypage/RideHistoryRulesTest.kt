package com.moyeota.presentation.feature.mypage

import com.moyeota.domain.model.RideRecord
import com.moyeota.domain.model.RideRecordMember
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * 34 지난 탑승 기록의 표시 규칙.
 *
 * **여기서 테스트가 꼭 필요한 이유:** 「요금이 없으면 요금 줄을 그리지 않는다」를 눈으로 확인하려면
 * 1차 모드(기사 없이 방장이 합승 종료)로 끝난 방을 실제로 하나 만들어야 한다 — 실서버에서
 * 기사 계정을 만들고 배차를 돌리는 일이다. `MyPageMenu` 를 떼어 둔 것과 같은 이유로 표시 규칙을
 * 순수 함수로 두고 값으로 넣어 본다.
 */
class RideHistoryRulesTest {

    private val zone: ZoneId = ZoneId.of("Asia/Seoul")
    private val now: Instant = Instant.parse("2026-11-01T00:00:00Z")

    private fun record(
        partyId: Long = 69,
        departure: String = "강남역",
        destination: String = "판교역",
        dropoffTime: String? = "2026-10-31T19:15:17.571486Z",
        totalFare: Int? = 16500,
        farePerPerson: Int? = 16500,
        memberCount: Int = 1,
        members: List<RideRecordMember> = listOf(
            RideRecordMember(id = "me", nickname = "프로브9911", imageUrl = null, isMe = true),
        ),
    ) = RideRecord(
        partyId = partyId,
        departure = departure,
        destination = destination,
        pickupTime = null,
        dropoffTime = dropoffTime,
        totalFare = totalFare,
        farePerPerson = farePerPerson,
        memberCount = memberCount,
        members = members,
    )

    // ─────────────────────────── 요금 (이 화면의 1급 규칙) ───────────────────────────

    /**
     * ★ 1차 모드 기록은 요금이 null 이다. **표시 모델에 요금이 아예 없어야** 한다 —
     * 0원으로 접으면 「공짜로 탔다」는 거짓말이 되고, 사용자는 그걸 영수증으로 읽는다.
     * 이 저장소가 거짓 문구 19건을 들어낸 이력이 있는 바로 그 종류의 사고다.
     */
    @Test
    fun `요금이 없는 기록은 요금 줄을 그리지 않는다`() {
        val item = record(totalFare = null, farePerPerson = null).toHistoryItem(now, zone)

        assertNull("요금이 없는 기록에 요금 블록이 생겼다 — 화면이 「0원」을 그린다", item.fare)
    }

    /** 요금이 있는 기록은 반대로 두 줄이 다 선다. 빈 블록을 만들지 않는지 함께 본다. */
    @Test
    fun `요금이 있는 기록은 1인 부담과 총액을 모두 보여준다`() {
        val fare = record().toHistoryItem(now, zone).fare

        assertNotNull(fare)
        assertEquals("1인 16,500원", fare!!.perPersonLabel)
        assertEquals("총 16,500원", fare.totalLabel)
    }

    /**
     * ★ **1인 부담은 서버 값을 그대로 쓴다.** 앱이 다시 나누지 않는다.
     *
     * 서버 `farePerPerson` 은 `totalFare / memberCount` **정수 나눗셈(내림)** 이고,
     * 앱의 `farePerPersonWon`(14·15 의 예상 요금)은 10원 단위로 올림/반올림한다
     * (`FarePerPersonRuleTest`: 2666.67 → 2670 **올림**). 그걸 끌어다 쓰면 기록 화면 금액이
     * 실제 결제액과 어긋나고, 사용자는 어느 쪽이 거짓인지 알 길이 없다.
     *
     * 그래서 「앱이 나누면 2670 이 되는」 값을 일부러 넣어 **서버 값 2666 이 그대로 나오는지** 본다.
     */
    @Test
    fun `1인 부담은 서버 값을 그대로 쓴다`() {
        val item = record(totalFare = 8000, farePerPerson = 2666, memberCount = 3).toHistoryItem(now, zone)

        assertEquals("1인 2,666원", item.fare!!.perPersonLabel)
        assertEquals("총 8,000원", item.fare!!.totalLabel)
    }

    /**
     * 한쪽만 온 응답(실측에서 본 적은 없다). **없는 쪽을 계산해 채우지 않는다** — 채우면 위의
     * 어긋남이 그대로 일어난다. 있는 값만 그린다.
     */
    @Test
    fun `요금 한쪽만 있으면 그 한 줄만 그린다`() {
        val onlyTotal = record(totalFare = 9000, farePerPerson = null).toHistoryItem(now, zone)
        assertNull(onlyTotal.fare!!.perPersonLabel)
        assertEquals("총 9,000원", onlyTotal.fare!!.totalLabel)

        val onlyPerPerson = record(totalFare = null, farePerPerson = 3000).toHistoryItem(now, zone)
        assertEquals("1인 3,000원", onlyPerPerson.fare!!.perPersonLabel)
        assertNull(onlyPerPerson.fare!!.totalLabel)
    }

    // ─────────────────────────── 경로·인원 ───────────────────────────

    @Test
    fun `출발지와 도착지를 화살표로 잇는다`() {
        assertEquals("강남역 → 판교역", record().toHistoryItem(now, zone).routeLabel)
    }

    /** 서버가 이름을 주지 않은 기록(매퍼가 빈 문자열로 접는다). 빈 화살표를 그리지 않는다. */
    @Test
    fun `출발지와 도착지가 둘 다 없으면 경로 줄이 없다`() {
        val item = record(departure = "", destination = "").toHistoryItem(now, zone)

        assertNull("빈 이름으로 「 → 」만 그렸다", item.routeLabel)
    }

    @Test
    fun `한쪽 이름만 있으면 있는 쪽만 그린다`() {
        assertEquals("강남역", record(destination = "").toHistoryItem(now, zone).routeLabel)
        assertEquals("판교역", record(departure = "").toHistoryItem(now, zone).routeLabel)
    }

    /**
     * 인원은 서버 `currentMembers` 를 쓴다. **정원이 아니므로 「2/3명」을 만들지 않는다** —
     * 서버가 정원을 주지 않아 분모를 지어낼 수밖에 없다.
     */
    @Test
    fun `인원은 나 포함으로 세고 정원을 지어내지 않는다`() {
        val item = record(
            memberCount = 3,
            members = listOf(
                RideRecordMember(id = "me", nickname = "나", imageUrl = null, isMe = true),
                RideRecordMember(id = "u2", nickname = "이OO", imageUrl = null, isMe = false),
                RideRecordMember(id = "", nickname = "탈퇴한 회원", imageUrl = null, isMe = false),
            ),
        ).toHistoryItem(now, zone)

        assertEquals("나 포함 3명", item.memberLabel)
        assertFalse("정원이 없는데 「/」를 그렸다: ${item.memberLabel}", item.memberLabel!!.contains("/"))
    }

    /**
     * `isMe` 는 앱이 publicId 비교로 만드는 값이라 **틀릴 수 있다**(미로그인·요약 없음).
     * 그때 「나 포함」을 붙이면 없는 근거로 단정하는 말이 된다 — 숫자만 쓴다.
     */
    @Test
    fun `내가 목록에 없으면 나 포함이라고 말하지 않는다`() {
        val item = record(
            memberCount = 2,
            members = listOf(
                RideRecordMember(id = "u1", nickname = "김OO", imageUrl = null, isMe = false),
                RideRecordMember(id = "u2", nickname = "이OO", imageUrl = null, isMe = false),
            ),
        ).toHistoryItem(now, zone)

        assertEquals("2명", item.memberLabel)
    }

    /** 셀 수 없는 것을 세지 않는다 — 서버 인원이 0 이면 「0명」이 아니라 그 줄이 없다. */
    @Test
    fun `인원이 0 이면 인원 줄이 없다`() {
        val item = record(memberCount = 0, members = emptyList()).toHistoryItem(now, zone)

        assertNull("「0명」을 그렸다", item.memberLabel)
    }

    // ─────────────────────────── 날짜 ───────────────────────────

    /**
     * 서버는 UTC ISO-8601 로 내려준다. **문자열을 그대로 자르면 KST 오전 4시가 전날 오후 7시로
     * 보인다** — 채팅에서 실제로 났던 결함(QA 결함-3)과 같은 함정이라 기기 시간대로 바꿔 쓴다.
     * 실측 값 `2026-10-31T19:15:17Z` 는 KST 로 11월 1일 오전 4시 15분이다.
     */
    @Test
    fun `하차 시각을 기기 시간대로 바꿔 보여준다`() {
        val item = record().toHistoryItem(now, zone)

        assertEquals("11월 1일 오전 4:15", item.dateLabel)
    }

    /** 지난해 기록에는 해를 붙이고 시각은 빼 둔다 — 몇 달 전 탑승에서 분 단위는 줄만 길게 한다. */
    @Test
    fun `다른 해의 기록에는 연도를 붙인다`() {
        val item = record(dropoffTime = "2025-07-25T02:10:00Z").toHistoryItem(now, zone)

        assertEquals("2025년 7월 25일", item.dateLabel)
    }

    /** 틀린 날짜보다 없는 편이 낫다 — 파싱할 수 없는 형식이면 그 줄을 비운다. */
    @Test
    fun `읽을 수 없는 시각은 날짜 줄을 비운다`() {
        assertNull(record(dropoffTime = "어제").toHistoryItem(now, zone).dateLabel)
        assertNull(record(dropoffTime = null).toHistoryItem(now, zone).dateLabel)
    }

    /** 자정이 「오전 0시」로 찍히지 않게 — `hour % 12` 만 쓰면 0 이 된다. */
    @Test
    fun `자정과 정오는 12 시로 적는다`() {
        // KST 자정 = UTC 전날 15:00
        assertEquals("11월 1일 오전 12:00", record(dropoffTime = "2026-10-31T15:00:00Z").toHistoryItem(now, zone).dateLabel)
        // KST 정오 = UTC 03:00
        assertEquals("11월 1일 오후 12:00", record(dropoffTime = "2026-11-01T03:00:00Z").toHistoryItem(now, zone).dateLabel)
    }

    // ─────────────────────────── 페이징·빈 상태 ───────────────────────────

    /**
     * ★ `hasNext == false` 면 트리거가 서지 않아야 한다. 서버는 그때 `nextCursor` 를 항상
     * null 로 주므로, 그래도 부르면 **커서 없이 첫 페이지를 다시 받아** 목록이 무한히 중복된다.
     */
    @Test
    fun `다음 페이지가 없으면 더 불러오지 않는다`() {
        assertFalse(shouldLoadMore(hasNext = false, loading = false))
        assertFalse(shouldLoadMore(hasNext = false, loading = true))
    }

    /** 이미 받는 중이면 또 부르지 않는다 — 스크롤 트리거는 같은 커서로 두 번 불리기 쉽다. */
    @Test
    fun `이미 불러오는 중이면 또 부르지 않는다`() {
        assertTrue(shouldLoadMore(hasNext = true, loading = false))
        assertFalse(shouldLoadMore(hasNext = true, loading = true))
    }

    /**
     * ★★ 실패 뒤 **자동** 재시도를 끊는다 — 여기가 진짜 무한 루프였다.
     *
     * 다음 페이지 조회가 실패해도 `hasNext`·`nextCursor` 는 살아 있다. 그래서 실패로 `loading` 이
     * true→false 로 떨어지는 순간, 목록 바닥에 머물러 있던 스크롤이 **같은 요청을 즉시 다시 쏜다.**
     * 백오프가 없어 응답 실패마다 재발사되고, 화면에는 아무 안내도 없이 스피너만 깜빡인다 —
     * 사용자가 멈추는 길은 위로 스크롤하거나 화면을 떠나는 것뿐이다.
     *
     * 재개는 사용자가 「다시 시도」를 눌러 `failed` 를 내렸을 때만 일어나야 한다.
     */
    @Test
    fun `실패한 뒤에는 스크롤이 자동으로 다시 부르지 않는다`() {
        assertFalse(
            "실패가 남아 있는데 트리거가 섰다 — 같은 요청이 끝없이 반복된다",
            shouldLoadMore(hasNext = true, loading = false, failed = true),
        )
        // 「다시 시도」가 failed 를 내리면 그때 재개된다
        assertTrue(shouldLoadMore(hasNext = true, loading = false, failed = false))
    }

    /**
     * 빈 상태 문구는 **사실만** 말한다. 「~예요」체이고(이 저장소의 사용자 문구 규칙),
     * 사용자가 할 일이 없으니 「~해 주세요」를 쓰지 않는다.
     */
    @Test
    fun `기록이 없으면 빈 상태 문구를 보여준다`() {
        assertEquals("아직 탑승 기록이 없어요", RIDE_HISTORY_EMPTY_TEXT)
        assertTrue(RIDE_HISTORY_EMPTY_TEXT.endsWith("없어요"))
        assertFalse("빈 상태에서 사용자가 할 일이 없다", RIDE_HISTORY_EMPTY_TEXT.contains("주세요"))
    }

    /**
     * 실패 문구는 빈 상태와 **달라야 한다.** 「기록이 없다」와 「못 불러왔다」는 다른 사실이고,
     * 앞쪽을 보여 주면 사용자는 자기 기록이 사라졌다고 읽는다.
     */
    @Test
    fun `불러오기 실패 문구는 빈 상태 문구와 다르다`() {
        assertEquals("탑승 기록을 불러오지 못했어요", RIDE_HISTORY_ERROR_TEXT)
        assertFalse(RIDE_HISTORY_ERROR_TEXT == RIDE_HISTORY_EMPTY_TEXT)
    }

    /** 방 id 가 그대로 넘어와야 한다 — 목록의 key 이고, 중복 키가 들어오면 그 자리에서 터진다. */
    @Test
    fun `표시 모델은 방 id 를 그대로 들고 있다`() {
        assertEquals(67L, record(partyId = 67).toHistoryItem(now, zone).partyId)
    }
}
