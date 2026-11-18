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
        /**
         * 🔴 **`dropoffTime` 이었다.** 서버가 하차 시각을 없애고 **승차 시각 하나로 바로잡았다**
         * (백엔드 `135df701`) — 도메인에서 그 필드를 지워 컴파일로 잡았다. 그대로 뒀다면
         * `ignoreUnknownKeys` 가 없는 키를 null 로 채워 **모든 카드의 날짜가 빈칸**이 됐다
         * (에러가 아니라 빈칸이라 알아채기 어렵다). 뜻도 바뀌었다: **방장이 출발을 누른 시각**이고,
         * 아무도 누르지 않아 서버가 자동 정리한 방은 **약속 시각**이 들어온다.
         */
        pickupTime: String? = "2026-10-31T19:15:17.571486Z",
        totalFare: Int? = 16500,
        /**
         * 🔴 **1/N 이 아니다** — 방장이 사람마다 적어 넣은 금액이고(백엔드 `fe6ba35a`), 그래서
         * **같은 방을 보는 두 사람이 다른 값을 받는다**. 라벨이 「1인 …」에서 「내 몫 …」으로 바뀐
         * 근거다([RideHistoryFare] KDoc).
         */
        farePerPerson: Int? = 16500,
        memberCount: Int = 1,
        members: List<RideRecordMember> = listOf(
            RideRecordMember(id = "me", nickname = "프로브9911", imageUrl = null, isMe = true),
        ),
    ) = RideRecord(
        partyId = partyId,
        departure = departure,
        destination = destination,
        pickupTime = pickupTime,
        totalFare = totalFare,
        farePerPerson = farePerPerson,
        memberCount = memberCount,
        members = members,
    )

    // ─────────────────────────── 요금 (이 화면의 1급 규칙) ───────────────────────────

    /**
     * ★ **택시비 정산이 없는 기록은 요금이 null 이다.** 표시 모델에 요금이 아예 없어야 한다 —
     * 0원으로 접으면 「공짜로 탔다」는 거짓말이 되고, 사용자는 그걸 영수증으로 읽는다.
     * 이 저장소가 거짓 문구 19건을 들어낸 이력이 있는 바로 그 종류의 사고다.
     *
     * 옛 근거는 「1차 모드 기록(기사 없이 방장이 합승 종료)」이었다. 기사가 사라졌으니 **모든
     * 기록이 그 「1차 모드」**이고, 요금이 들어오는 유일한 길은 이제 **방장이 만든 정산 장부**다 —
     * 정산이 없거나 내가 당사자가 아니면 둘 다 null 이다. 결론은 그대로다.
     */
    @Test
    fun `요금이 없는 기록은 요금 줄을 그리지 않는다`() {
        val item = record(totalFare = null, farePerPerson = null).toHistoryItem(now, zone)

        assertNull("요금이 없는 기록에 요금 블록이 생겼다 — 화면이 「0원」을 그린다", item.fare)
    }

    /** 요금이 있는 기록은 반대로 두 줄이 다 선다. 빈 블록을 만들지 않는지 함께 본다. */
    @Test
    fun `요금이 있는 기록은 내 몫과 총액을 모두 보여준다`() {
        val fare = record().toHistoryItem(now, zone).fare

        assertNotNull(fare)
        assertEquals("내 몫 16,500원", fare!!.perPersonLabel)
        assertEquals("총 16,500원", fare.totalLabel)
    }

    /**
     * 🔴 ★ **「1인」이 아니라 「내 몫」이다** — 뜻이 바뀐 자리다.
     *
     * 옛 라벨은 「1인 2,666원」이었고, 그때 서버 `farePerPerson` 은 `totalFare / memberCount`
     * **정수 나눗셈**이라 「한 사람당 그만큼씩」이 참이었다. 서버가 택시비 정산을 **방장이 사람마다
     * 부담 금액을 넣는 장부**로 다시 짰다(백엔드 `fe6ba35a`) — 이제:
     *
     * | | 예전 | 지금 |
     * |---|---|---|
     * | 값의 출처 | 총액 ÷ 인원 | **방장이 그 사람에게 적어 넣은 금액** |
     * | 같은 방을 보는 두 사람 | 같은 값 | **다른 값** |
     *
     * 그래서 「1인 …」은 거짓이다: 내 몫이 10,000원이어도 다른 사람은 2,000원일 수 있다.
     * 그 어긋남은 **화면만 봐서는 알 수 없고**(두 사람이 각자 자기 숫자를 본다) 금액이라
     * 영수증으로 읽힌다 — 값으로 못 박는다.
     *
     * ### 앱이 다시 나누지 않는 것은 **그대로다** (이유가 하나 늘었다)
     * ① 앱의 `farePerPersonWon`(14·15 의 예상 요금)은 10원 단위로 올림/반올림하므로
     * (`FarePerPersonRuleTest`: 2666.67 → 2670 **올림**) 끌어다 쓰면 실제 금액과 어긋난다.
     * ② **이제는 나눗셈 자체가 틀린 식이다** — 방장이 손으로 조정한 금액이 뭉개진다.
     *
     * 그래서 「총액 ÷ 인원 과도, 앱의 올림과도 다른」 값을 일부러 넣어 **서버 값이 그대로 나오는지**
     * 본다: 3명 8,000원 방에서 내 몫이 5,000원인 장부는 둘 중 어느 식으로도 나오지 않는다.
     */
    @Test
    fun `내 몫은 서버 값을 그대로 쓴다 - 총액을 나누지 않는다`() {
        val item = record(totalFare = 8000, farePerPerson = 5000, memberCount = 3).toHistoryItem(now, zone)

        assertEquals("내 몫 5,000원", item.fare!!.perPersonLabel)
        assertEquals("총 8,000원", item.fare!!.totalLabel)
        // 「1인」으로 적으면 「한 사람당 5,000원씩」으로 읽혀 거짓이 된다(합이 15,000원이 아니다).
        assertFalse(
            "사람마다 다른 금액을 「1인」으로 적는다: ${item.fare!!.perPersonLabel}",
            item.fare!!.perPersonLabel!!.contains("1인"),
        )
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
        assertEquals("내 몫 3,000원", onlyPerPerson.fare!!.perPersonLabel)
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
     *
     * 🔴 **그 시각이 하차에서 승차로 바뀌었다** — 서버가 `dropoffTime` 을 지웠다(`135df701`).
     * 날짜 줄이 그 필드를 보고 있었으므로, 바꾸지 않으면 **모든 카드의 날짜가 빈칸**이 된다.
     */
    @Test
    fun `승차 시각을 기기 시간대로 바꿔 보여준다`() {
        val item = record().toHistoryItem(now, zone)

        assertEquals("11월 1일 오전 4:15", item.dateLabel)
    }

    /** 지난해 기록에는 해를 붙이고 시각은 빼 둔다 — 몇 달 전 탑승에서 분 단위는 줄만 길게 한다. */
    @Test
    fun `다른 해의 기록에는 연도를 붙인다`() {
        val item = record(pickupTime = "2025-07-25T02:10:00Z").toHistoryItem(now, zone)

        assertEquals("2025년 7월 25일", item.dateLabel)
    }

    /** 틀린 날짜보다 없는 편이 낫다 — 파싱할 수 없는 형식이면 그 줄을 비운다. */
    @Test
    fun `읽을 수 없는 시각은 날짜 줄을 비운다`() {
        assertNull(record(pickupTime = "어제").toHistoryItem(now, zone).dateLabel)
        assertNull(record(pickupTime = null).toHistoryItem(now, zone).dateLabel)
    }

    /**
     * ★ **오프셋 표기(`+09:00`)도 읽어야 한다.** 서버는 지금 UTC `Z` 로만 주지만 계약은 ISO-8601
     * 이고, `toInstantOrNull` 의 둘째 `runCatching`(`OffsetDateTime.parse`)이 그 대비다. 그 폴백이
     * 사라지면 날짜 줄이 조용히 **비어** 「기록은 있는데 날짜가 없는 카드」가 된다 — 에러가 아니라서
     * 알아챌 길이 없고, 원인이 파서라 서버·화면 어디를 봐도 안 보인다.
     *
     * 이 사실은 2026-11-12 까지 38 노쇼 기록의 테스트(`NoShowRecordsRulesTest` 의
     * `읽을 수 없는 기한은 null 이다`)가 **유일하게** 못 박고 있었다. 노쇼 제거로 그 테스트가
     * 사라지면서 같은 패키지의 공용 파서가 **아무도 지키지 않는 상태**가 되므로 여기로 옮겼다.
     *
     * `2026-10-31T19:15:17+09:00` = `2026-10-31T10:15:17Z` → KST 로 10월 31일 오후 7시 15분이다.
     */
    @Test
    fun `오프셋 표기로 온 시각도 읽는다`() {
        val item = record(pickupTime = "2026-10-31T19:15:17+09:00").toHistoryItem(now, zone)

        assertEquals("10월 31일 오후 7:15", item.dateLabel)
    }

    /** 자정이 「오전 0시」로 찍히지 않게 — `hour % 12` 만 쓰면 0 이 된다. */
    @Test
    fun `자정과 정오는 12 시로 적는다`() {
        // KST 자정 = UTC 전날 15:00
        assertEquals("11월 1일 오전 12:00", record(pickupTime = "2026-10-31T15:00:00Z").toHistoryItem(now, zone).dateLabel)
        // KST 정오 = UTC 03:00
        assertEquals("11월 1일 오후 12:00", record(pickupTime = "2026-11-01T03:00:00Z").toHistoryItem(now, zone).dateLabel)
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
    /**
     * 못 박는 것: **택시비 정산으로 가는 길이 「요금이 이미 있는 카드」에 묶여 있지 않다.**
     *
     * 이 카드가 정산으로 가는 **유일한 길**이다 — 또 하나의 진입점인 22 탑승 상세는 21 대기 화면의
     * 카드에서만 열리고, 방이 끝나면 21 이 닫혀 **끝난 방의 22 에는 닿을 수 없다.**
     *
     * 그래서 조건을 `fare != null`(= 정산이 이미 있다)로 두면 **방장이 자기 방을 영영 정산하지
     * 못한다**: 정산이 없으니 요금이 없고, 요금이 없으니 버튼이 없고, 버튼이 없으니 시작할 수 없다.
     * 그 고리를 끊는 것이 이 테스트다.
     */
    @Test
    fun `정산이 아직 없는 기록에서도 정산으로 갈 수 있다`() {
        val item = record(totalFare = null, farePerPerson = null, memberCount = 2).toHistoryItem(now, zone)

        assertNull("정산이 없으니 요금 줄은 없어야 한다", item.fare)
        assertTrue("요금이 없다고 정산 진입까지 막으면 시작할 길이 사라진다", item.canOpenFareSplit)
    }

    /**
     * 못 박는 것: **혼자 탄 방에는 정산 진입이 없다.**
     *
     * 서버가 409 `NOT_ENOUGH_RIDERS` 로 막는 자리다(「함께 탄 사람이 2명 이상이어야 택시비를 나눌
     * 수 있습니다」). 나눌 상대가 없는 방에 버튼을 띄우면 눌러서 막다른 화면에 닿는다.
     *
     * 나머지 두 조건(끝난 방인가 · 내가 탑승자인가)은 **목록에 들어온 것만으로 이미 참**이다 —
     * 서버가 도착을 누른 멤버에게만 그 방의 기록을 준다(백엔드 `2732cc75`).
     */
    @Test
    fun `혼자 탄 기록에는 정산 진입이 없다`() {
        assertFalse(record(memberCount = 1).toHistoryItem(now, zone).canOpenFareSplit)
        assertFalse("인원을 모르는 기록도 열지 않는다", record(memberCount = 0).toHistoryItem(now, zone).canOpenFareSplit)
        assertTrue(record(memberCount = 2).toHistoryItem(now, zone).canOpenFareSplit)
    }

}
