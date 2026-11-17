package com.moyeota.presentation.feature.mypage

import com.moyeota.domain.model.RideRecord
import com.moyeota.domain.model.RideRecordMember
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * 34 지난 탑승 기록의 **표시 규칙** — 무엇을 글자로 만들고, 무엇을 아예 그리지 않는가.
 *
 * 화면(`RideHistoryScreen`)에서 떼어 둔 이유는 `MyPageMenu.kt` 와 같다: 「요금이 없으면 요금 줄을
 * 그리지 않는다」는 규칙은 눈으로 확인하려면 **1차 모드로 끝난 방을 하나 만들어** 다시 빌드해야
 * 하는 종류다. 순수 함수로 두면 `RideHistoryRulesTest` 가 값으로 고정한다.
 *
 * 안드로이드를 모르는 파일로 둔다(그래서 JVM 테스트가 그냥 돈다).
 */

/** 기록이 하나도 없을 때의 문구. 「없어요」까지가 사실이고 그 이상을 약속하지 않는다. */
internal const val RIDE_HISTORY_EMPTY_TEXT = "아직 탑승 기록이 없어요"

/** 불러오기가 실패했을 때의 문구. 사용자가 할 일은 「다시 시도」 하나뿐이다. */
internal const val RIDE_HISTORY_ERROR_TEXT = "탑승 기록을 불러오지 못했어요"

/**
 * 카드 한 장이 그릴 값. **없는 값은 null 이고, null 인 자리는 그리지 않는다.**
 *
 * 「0원」·「0명」·「날짜 모름」 같은 자리값을 두지 않는 것이 이 모델의 전부다 — 이 저장소가
 * 거짓 문구를 들어낸 이력이 있어 「모른다」를 **타입으로** 남긴다.
 */
internal data class RideHistoryItem(
    val partyId: Long,
    /**
     * **승차 시각** 표기. 파싱할 수 없으면 null — 틀린 날짜보다 없는 편이 낫다.
     *
     * 하차 시각이었던 것이 승차 시각으로 바뀌었다 — 서버가 `dropoffTime` 을 지우고
     * `pickupTime` 하나로 바로잡았다(백엔드 `135df701`). 자세한 뜻은 [RideRecord.pickupTime].
     */
    val dateLabel: String?,
    /** `출발 → 도착`. 둘 중 하나라도 없으면 있는 쪽만, 둘 다 없으면 null. */
    val routeLabel: String?,
    /** 「나 포함 3명」. 서버 인원이 0 이면 null(셀 수 없는 것을 세지 않는다). */
    val memberLabel: String?,
    /** **null 이면 요금 줄을 아예 그리지 않는다.** 그 방에 택시비 정산이 없다는 뜻이다. */
    val fare: RideHistoryFare?,
    /**
     * 이 카드에서 **택시비 정산으로 갈 수 있는가** — 함께 탄 사람이 2명 이상인가.
     *
     * ### 왜 [fare] 유무로 묻지 않나 ★
     * 「요금이 있다 = 정산이 있다」는 맞지만, 그걸 조건으로 쓰면 **정산을 시작할 길이 사라진다.**
     * 시작 진입점은 22 탑승 상세에도 있는데 그 화면은 21 대기 화면의 카드에서만 열리고, 방이
     * 끝나면 21 이 닫히므로 **끝난 방의 22 에는 닿을 수 없다.** 즉 이 카드가 유일한 길이고,
     * 여기서 「이미 정산이 있을 때만」 열면 방장이 자기 방을 영영 정산하지 못한다.
     *
     * ### 두 조건은 목록에 들어온 것만으로 이미 참이다
     * 서버는 **도착을 누른 멤버에게만** 그 방의 기록을 준다(백엔드 `2732cc75`: 「탑승 횟수·탑승
     * 기록은 도착한 멤버만 센다」). 그래서 이 목록에 있다는 것이 곧 「끝난 방이다」와 「내가 그
     * 방의 탑승자다」다 — 정산 당사자 조건을 이미 만족한다. 남은 것은 **함께 탄 사람이 2명 이상**
     * 뿐이다(혼자 탄 방은 나눌 것이 없다 — 서버도 409 `NOT_ENOUGH_RIDERS` 로 막는다).
     *
     * 「내가 방장인가」는 **묻지 않는다.** 그 답이 기록에 없고, 비방장도 **보내는 사람으로** 정산을
     * 열어야 한다. 시작할 수 있는 사람이 누구인지는 39 화면이 방 상세를 읽어 가른다
     * (`FareSplitAbsentScreen`) — 여기서 추측하면 비방장에게 「정산 시작하기」를 띄우게 된다.
     */
    val canOpenFareSplit: Boolean,
    val members: List<RideRecordMember>,
)

/**
 * 요금 두 줄. **서버 값을 그대로 쓴다** — 앱이 나누지도, 되돌리지도 않는다.
 *
 * ### `farePerPerson` 은 **1/N 이 아니다** ★ (2026-11-12)
 * 예전에는 `totalFare / memberCount` 정수 나눗셈이었고, 이 KDoc 은 「앱의 `farePerPersonWon` 은
 * 10원 단위로 맞추니 끌어다 쓰면 어긋난다」만 경고하고 있었다. 서버가 택시비 정산을 **방장이
 * 사람마다 부담 금액을 넣는 장부**로 다시 짰다(백엔드 `fe6ba35a`). 그래서:
 *
 * | | 예전 | 지금 |
 * |---|---|---|
 * | 값의 출처 | 총액 ÷ 인원 | **방장이 그 사람에게 적어 넣은 금액** |
 * | 같은 방을 보는 두 사람 | 같은 값 | **다른 값** |
 * | 총액으로 되돌릴 수 있나 | 된다 | **안 된다**(손으로 조정한 금액이 뭉개진다) |
 *
 * 그래서 라벨에서 **「1인」을 뺐다.** 「1인 16,500원」은 「한 사람당 16,500원씩」으로 읽히고, 그건
 * 이제 거짓이다 — 내 몫이 16,500원이어도 다른 사람은 8,000원일 수 있다. 「내 몫」이라고 적으면
 * 받는 사람(= 방장)에게는 「자기가 부담한 몫」이라 그쪽에서도 참이다.
 *
 * `totalFare` 와 `farePerPerson` 은 **함께 채워지거나 함께 빈다** — 서버가 둘을 같은 정산에서
 * 만든다. 그래서 [fareOrNull] 이 둘 다 null 일 때만 블록을 접는다.
 */
internal data class RideHistoryFare(
    /** 「내 몫 16,500원」. 서버가 내 부담을 주지 않았으면 null. **「1인」이 아니다** — 위 KDoc 의 표. */
    val perPersonLabel: String?,
    /** 「총 16,500원」. 서버가 총액을 주지 않았으면 null. */
    val totalLabel: String?,
)

/**
 * 기록 한 건 → 카드 한 장.
 *
 * ### 요금이 없으면 요금 자체를 안 만든다 ★ 이 화면의 1급 규칙
 * [RideRecord.totalFare]·[RideRecord.farePerPerson] 이 **둘 다** null 인 기록은 **그 방에 택시비
 * 정산이 없는 것**이다(정산을 시작하지 않았거나 내가 그 정산의 당사자가 아니다 — 서버가 둘을 함께
 * 채우거나 함께 비운다). 0 으로 접어 「0원」을 그리면 **「공짜로 탔다」는 거짓말**이 된다. 그래서
 * [RideHistoryFare] 를 만들지 않고, 화면은 null 을 보고 줄을 건너뛴다.
 *
 * 옛 근거는 「1차 모드 기록이다(기사 없이 방장이 합승 종료 — 서버가 요금을 입력받는 자리가 기사
 * 하차 처리뿐이다)」였다. 기사가 사라졌으니 **모든 기록이 그 「1차 모드」**이고, 요금이 들어오는
 * 유일한 길은 이제 정산이다. 결론(둘 다 null 이면 줄을 그리지 않는다)은 그대로다.
 *
 * 한쪽만 있는 응답은 실측에서 본 적이 없지만(서버가 같은 정산에서 둘을 만든다) 그때도
 * **있는 값만** 그린다 — 없는 쪽을 다른 쪽에서 계산해 채우면 안 된다. 특히
 * `totalFare / memberCount` 는 **더 이상 `farePerPerson` 이 아니다**([RideHistoryFare] KDoc).
 *
 * ### 그리지 않는 것
 * 상태 칩(서버가 상태를 주지 않는다) · 방장 표시(`host` 필드가 없다) · 평점·매너 점수
 * (서버에 평가 API 가 없다) · 「예정」(예약 개념이 서버에 없다). 전부 지어낼 수밖에 없는 값이다.
 */
internal fun RideRecord.toHistoryItem(
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): RideHistoryItem = RideHistoryItem(
    partyId = partyId,
    // 🔴 `dropoffTime` 이 아니다 — 서버가 하차 시각을 지웠다(백엔드 `135df701`). 그 필드로 두면
    // 매퍼가 없는 키를 조용히 null 로 채워 **모든 카드의 날짜가 빈칸**이 된다(에러가 아니라 빈칸).
    dateLabel = pickupTime?.let { rideHistoryDateLabel(it, now, zone) },
    routeLabel = routeLabel(departure, destination),
    memberLabel = memberLabel(memberCount, members),
    fare = fareOrNull(totalFare, farePerPerson),
    canOpenFareSplit = memberCount >= 2,
    members = members,
)

/** `출발 → 도착`. 한쪽이 비면 있는 쪽만 쓰고, 둘 다 비면 null 이다(빈 화살표를 그리지 않는다). */
private fun routeLabel(departure: String, destination: String): String? = when {
    departure.isNotBlank() && destination.isNotBlank() -> "$departure → $destination"
    departure.isNotBlank() -> departure
    destination.isNotBlank() -> destination
    else -> null
}

/**
 * 「나 포함 3명」. 서버 `currentMembers` 를 쓰고 **`members.size` 로 세지 않는다** — 요약을 못 찾은
 * 멤버도 항목으로는 오지만, 반대로 목록이 비어 있어도 인원 수는 올 수 있다.
 *
 * 「나 포함」은 내가 실제로 그 목록에 있을 때만 붙인다. 내 기록이니 당연히 있어야 하지만,
 * 서버가 `isMe` 를 주지 않아 앱이 publicId 비교로 만드는 값이라 **틀릴 수 있다**(미로그인·요약 없음).
 * 그때 「나 포함」을 그대로 쓰면 없는 근거로 단정하는 말이 된다.
 *
 * 정원(`capacity`)이 아니므로 「2/3명」은 만들지 않는다 — 서버가 정원을 주지 않는다.
 */
private fun memberLabel(memberCount: Int, members: List<RideRecordMember>): String? = when {
    memberCount <= 0 -> null
    members.any { it.isMe } -> "나 포함 ${memberCount}명"
    else -> "${memberCount}명"
}

/** 둘 다 없으면 요금 블록 자체가 없다. 있는 값만 글자로 만든다. */
private fun fareOrNull(totalFare: Int?, farePerPerson: Int?): RideHistoryFare? {
    if (totalFare == null && farePerPerson == null) return null
    return RideHistoryFare(
        // 「1인 …」이 아니라 「내 몫 …」이다 — 방장이 사람마다 적어 넣은 금액이라 **사람마다 다르다**
        // (백엔드 `fe6ba35a`). 「1인」으로 적으면 「한 사람당 그만큼씩」으로 읽혀 거짓이 된다.
        perPersonLabel = farePerPerson?.let { "내 몫 ${wonLabel(it)}" },
        totalLabel = totalFare?.let { "총 ${wonLabel(it)}" },
    )
}

/** 이 앱의 다른 요금 자리들과 같은 표기(`%,d원`). */
internal fun wonLabel(amount: Int): String = "%,d원".format(amount)

/**
 * **승차 시각** 표기. 올해면 `10월 31일 오후 4:15`, 다른 해면 `2026년 10월 31일`.
 *
 * 하차 시각이었다 — 서버가 그 필드를 지웠다(`135df701`). 이름을 `…DateLabel` 로 둔 채
 * 뜻만 바뀐 자리라 **호출부가 무엇을 넘기는지**가 전부다([RideRecord.pickupTime]).
 *
 * 서버는 UTC ISO-8601 로 내려주므로 **문자열을 그대로 자르면 KST 오후 4시가 오전 7시로 보인다**
 * (채팅에서 실제로 났던 결함과 같은 함정). 기기 시간대로 바꿔 쓴다.
 *
 * 다른 해의 기록에 시각을 붙이지 않는 이유: 몇 달 전 탑승에서 사용자가 찾는 것은 「언제쯤」이고,
 * 분 단위는 줄만 길어지게 한다. 파싱할 수 없는 형식이면 null — 그 줄을 비운다.
 */
internal fun rideHistoryDateLabel(
    isoTime: String,
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): String? {
    val at = isoTime.toInstantOrNull()?.atZone(zone) ?: return null
    val thisYear = now.atZone(zone).year
    if (at.year != thisYear) return "${at.year}년 ${at.monthValue}월 ${at.dayOfMonth}일"
    val meridiem = if (at.hour < 12) "오전" else "오후"
    // 0시·12시가 12 가 되어야 한다 — `hour % 12` 만 쓰면 자정이 「오전 0:05」로 찍힌다.
    val hour12 = (at.hour % 12).let { if (it == 0) 12 else it }
    val minute = "%02d".format(at.minute)
    return "${at.monthValue}월 ${at.dayOfMonth}일 $meridiem $hour12:$minute"
}

/**
 * 서버 ISO-8601(Instant 또는 오프셋 표기) → Instant. 그 외 형식은 null.
 *
 * **오프셋 표기(`+09:00`)까지 받는 것이 핵심이다.** 서버는 지금 UTC `Z` 로만 주지만 계약은
 * ISO-8601 이고, `Instant.parse` 하나로는 오프셋 표기에서 실패한다 — 그러면 날짜 줄이 조용히
 * 비어 「기록은 있는데 날짜가 없는 카드」가 된다(에러가 아니라서 알아챌 길이 없다). 두 번째
 * `runCatching` 이 그 대비이고, `RideHistoryRulesTest` 가 두 표기를 값으로 못 박는다.
 *
 * `internal` 로 둔 것은 같은 패키지가 공유하게 하기 위해서다. 지금 호출처는 [rideHistoryDateLabel]
 * 하나뿐이지만(38 노쇼 기록이 2026-11-12 에 사라졌다) `private` 로 좁히지 않는다 — 같은 서버
 * 표기를 읽는 다음 화면이 파서를 베끼면, 한쪽만 오프셋 표기를 받아 주는 식으로 조용히 갈린다.
 */
internal fun String.toInstantOrNull(): Instant? =
    runCatching { Instant.parse(this) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(this).toInstant() }.getOrNull()

/**
 * 다음 페이지를 불러야 하는가.
 *
 * **[hasNext] 가 false 면 절대 부르지 않는다.** 서버는 `hasNext == false` 일 때 `nextCursor` 를
 * 항상 null 로 주므로, 그래도 부르면 **커서 없이 첫 페이지를 다시 받아** 목록이 무한히 중복된다
 * (실측 근거는 `RideRecordPageResponse` KDoc). [loading] 가드는 스크롤이 같은 커서로 두 번
 * 불리는 흔한 사고를 막는다.
 *
 * ### [failed] 가 세 번째 가드인 이유 — 여기가 진짜 무한 루프였다
 * 다음 페이지 조회가 실패해도 `hasNext`·`nextCursor` 는 살아 있다. 그래서 실패로 `loading` 이
 * true→false 로 떨어지는 순간 바닥에 머물러 있던 스크롤이 **같은 요청을 즉시 다시 쏜다.**
 * 백오프가 없어 응답 실패마다 재발사되고, 사용자는 화면에서 아무 안내도 못 본 채 스피너가
 * 깜빡이는 것만 본다 — 멈추는 길은 위로 스크롤하거나 화면을 떠나는 것뿐이다.
 *
 * 그래서 실패 뒤 자동 재시도를 끊고, 재개는 사용자가 「다시 시도」를 눌러 [failed] 를 내렸을 때만 한다.
 */
internal fun shouldLoadMore(hasNext: Boolean, loading: Boolean, failed: Boolean = false): Boolean =
    hasNext && !loading && !failed
