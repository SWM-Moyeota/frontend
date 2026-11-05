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
    /** 하차 시각 표기. 파싱할 수 없으면 null — 틀린 날짜보다 없는 편이 낫다. */
    val dateLabel: String?,
    /** `출발 → 도착`. 둘 중 하나라도 없으면 있는 쪽만, 둘 다 없으면 null. */
    val routeLabel: String?,
    /** 「나 포함 3명」. 서버 인원이 0 이면 null(셀 수 없는 것을 세지 않는다). */
    val memberLabel: String?,
    /** **null 이면 요금 줄을 아예 그리지 않는다.** 1차 모드 기록이 그렇다. */
    val fare: RideHistoryFare?,
    val members: List<RideRecordMember>,
)

/**
 * 요금 두 줄. **서버 값을 그대로 쓴다** — 앱이 다시 나누지 않는다.
 *
 * 서버 `farePerPerson` 은 `totalFare / memberCount` **정수 나눗셈(내림)** 인데, 앱의
 * `farePerPersonWon`(14·15 의 예상 요금)은 10원 단위로 올림/반올림한다. 그걸 끌어다 쓰면
 * **기록 화면 금액이 실제 결제액과 어긋난다** — 사용자는 어느 쪽이 거짓인지 알 길이 없다.
 */
internal data class RideHistoryFare(
    /** 「1인 16,500원」. 서버가 1인 부담을 주지 않았으면 null. */
    val perPersonLabel: String?,
    /** 「총 16,500원」. 서버가 총액을 주지 않았으면 null. */
    val totalLabel: String?,
)

/**
 * 기록 한 건 → 카드 한 장.
 *
 * ### 요금이 없으면 요금 자체를 안 만든다 ★ 이 화면의 1급 규칙
 * [RideRecord.totalFare]·[RideRecord.farePerPerson] 이 **둘 다** null 인 기록은 1차 모드다
 * (기사 없이 방장이 합승 종료 — 서버가 요금을 입력받는 자리가 기사 하차 처리뿐이다).
 * 0 으로 접어 「0원」을 그리면 **「공짜로 탔다」는 거짓말**이 된다. 그래서 [RideHistoryFare] 를
 * 만들지 않고, 화면은 null 을 보고 줄을 건너뛴다.
 *
 * 한쪽만 있는 응답은 실측에서 본 적이 없지만(서버가 같은 `fare` 에서 둘을 만든다) 그때도
 * **있는 값만** 그린다 — 없는 쪽을 다른 쪽에서 계산해 채우면 위 어긋남이 그대로 일어난다.
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
    dateLabel = dropoffTime?.let { rideHistoryDateLabel(it, now, zone) },
    routeLabel = routeLabel(departure, destination),
    memberLabel = memberLabel(memberCount, members),
    fare = fareOrNull(totalFare, farePerPerson),
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
        perPersonLabel = farePerPerson?.let { "1인 ${wonLabel(it)}" },
        totalLabel = totalFare?.let { "총 ${wonLabel(it)}" },
    )
}

/** 이 앱의 다른 요금 자리들과 같은 표기(`%,d원`). */
internal fun wonLabel(amount: Int): String = "%,d원".format(amount)

/**
 * 하차 시각 표기. 올해면 `10월 31일 오후 4:15`, 다른 해면 `2026년 10월 31일`.
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
 * `internal` 인 이유: 같은 패키지의 38 노쇼 기록(`NoShowRecordsDisplay.kt`)이 같은 서버 표기를
 * 읽는다. 파서를 한 번 더 베끼면 한쪽만 오프셋 표기를 받아 주는 식으로 조용히 갈린다.
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
