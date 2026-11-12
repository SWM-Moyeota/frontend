package com.moyeota.presentation.feature.home

import com.moyeota.core.designsystem.component.DateStripCell
import com.moyeota.core.designsystem.component.DateStripTone
import com.moyeota.domain.model.PartyOpenException
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * **예약 방**(서버 `RoomType.RESERVED`)을 만들고 찾는 데 쓰는 판정과 문구를 모아 둔다.
 *
 * ### 왜 화면에서 떼어 내는가
 * 이 파일의 규칙은 전부 **시계와 달력**에 달려 있다 — 「지금부터 7일 안」·「오늘~7일 뒤」·「예약은
 * 2명부터」. 경계를 한 칸 잘못 잡아도 스크린샷에는 아무것도 보이지 않는다: 7일 뒤 23:50 을 고를 수
 * 있는 화면과 올바른 화면은 눈으로 구분할 수 없고, 그 차이는 **서버 400 `INVALID_DEPARTURE_TIME`**
 * 으로만 드러난다. 그래서 판정을 Compose 를 모르는 순수 함수로 빼고 `now` 를 **인자로** 받는다 —
 * 어느 함수도 안에서 [Instant.now] 를 부르지 않는다(부르면 테스트가 경계를 고정할 수 없다).
 *
 * 21 약속 확정의 `MeetingFlow.kt`·34 기록의 `RideHistoryDisplay.kt` 와 같은 자리·같은 이유다.
 * 안드로이드를 모르는 파일이라 JVM 단위 테스트(`ReservationRulesTest`)가 그냥 돈다.
 *
 * ### 왜 `feature/home` 인가 — 17 탐색도 여기서 가져다 쓴다
 * 예약의 **시간 계약**은 방을 만드는 쪽(16 도착지 확인)이 서버와 맺는다: 「지금부터 7일 안」이
 * 생성의 조건이고, 검색의 「오늘~7일 뒤」는 **그 계약의 그림자**다. 두 곳이 각자 7을 들고 있으면
 * 한쪽만 서버를 따라가므로, 창을 정하는 상수와 날짜 계산은 여기 한 곳에 두고 탐색이 import 한다
 * (17 이 [headcountLabel] 을 여기서 가져다 쓰는 것과 같은 선례다 — `internal` 이라 모듈 안에서는
 * 어디서나 부른다).
 *
 * ### 서버가 최종 판정이다
 * 여기 있는 게이트는 **보낼 수 없는 값을 사용자가 고르지 못하게** 하려는 것이지 서버 검증의
 * 사본이 아니다. 특히 **「출발 2시간 안에 다른 예약」(409 `PARTY_TIME_CONFLICT`)은 앱이 예측하지
 * 않는다** — 내 다른 예약 목록을 이 화면이 들고 있지 않고, 들고 있어도 낡는다. 그건 막는 대신
 * 거절당했을 때 **사유별 문구**로 받는다([reservationOpenFailureMessage]).
 */

/**
 * 예약의 날짜·시각을 **한국 시간대**로 계산한다 — 기기 시간대가 아니다.
 *
 * 서버 검색 파라미터 `date` 는 「한국 날짜 `yyyy-MM-dd`」이고(Swagger 명시), 그건 **순간(instant)이
 * 아니라 달력 날짜**다. 기기 시간대로 계산하면 기기가 KST 가 아닌 사용자(해외 체류·수동 설정)에게
 * **검색이 하루 밀린다**: 앱이 「오늘」이라고 적은 칩이 서버의 오늘과 다른 날을 가리키고, 그 날은
 * 「오늘~7일 뒤」 범위를 벗어나 400 `INVALID_SEARCH_DATE` 가 되기도 한다.
 *
 * 고르는 **시각**도 같은 시간대로 묶는다. 사용자가 「오후 6:30」을 고르면 그건 한국에서 타는 택시의
 * 6시 30분이고, 기기 시간대로 읽으면 화면의 숫자와 서버에 보낸 순간이 어긋난다 — 그러면 표시된
 * 약속 시간과 실제 약속 시간이 다른 방이 만들어진다(되돌릴 수 없다: 예약 방은 시간을 바꿀 수 없다).
 *
 * 34 기록·21 약속 표기가 `ZoneId.systemDefault()` 를 쓰는 것과 **일부러 다르다.** 그쪽은 이미
 * 일어난(또는 1시간 안의) 일을 기기 시계로 읽어 주는 자리라 기기 시간대가 맞다. 이쪽은 **서버의
 * 달력에 값을 보내는** 자리다.
 */
internal val KOREA_ZONE: ZoneId = ZoneId.of("Asia/Seoul")

/**
 * 예약 출발 시각의 상한 — **지금부터 7일**. 서버 `ReservationService` 가 그 밖을 400
 * `INVALID_DEPARTURE_TIME` 으로 거절하고, 검색 `date` 도 「오늘~7일 뒤」로 같은 창을 쓴다.
 */
internal const val RESERVATION_WINDOW_DAYS = 7L

/**
 * 시각 휠의 간격(분) — **10분**. 시 휠(0~23)과 분 휠(00·10·20·30·40·50)이 이 간격으로 값을 낸다.
 *
 * ### 30분 칩에서 10분 휠로 (2026-11, 오너 UX 피드백)
 * 예전에는 30분 간격 칩이었다 — 「10분 단위면 하루 144개라 칩으로는 고를 수 없다」가 근거였다.
 * 휠은 그 제약이 없다: 시(24)와 분(6)을 **두 축으로 나눠** 고르므로 144개를 늘어놓지 않는다.
 * 그리고 30분 단위는 실제 약속에 너무 거칠었다(「6시 40분에 모이자」를 고를 수 없다).
 *
 * 자유 입력(분 단위 키패드)을 두지 않는 근거는 그대로다(`MEETING_PRESET_MINUTES` 와 같은 판단): 휠은
 * **고를 수 있는 값만** 담는다([reservationHourOptions]·[reservationMinuteOptions]) — 지난 시각·7일 밖이
 * 목록에 아예 없으므로 고를 수 있는 모든 값이 **구성상** 서버 창 안이다.
 */
internal const val RESERVATION_TIME_STEP_MINUTES = 10

/**
 * 가장 이른 출발 시각까지 두는 여유 — **지금부터 10분**(그 뒤 10분 단위로 **올림**한다 —
 * [reservationTimeRange]).
 *
 * 세 가지를 산다. ① 왕복 시간과 시계 스큐: 「1분 뒤」는 요청이 서버에 닿는 사이 과거가 되어
 * 400 `INVALID_DEPARTURE_TIME` 이 된다(`meetingPresetAt` 이 분 단위로 내림해 여유를 버는 것과 같은
 * 자리다). ② **모달에 머무는 시간**: 16 은 기준 시각을 열 때 한 번만 읽으므로(`DestinationConfirmModal`
 * 의 `now`) 사용자가 몇 분 고민하는 사이 그 여유가 줄어든다 — 10분이면 그래도 서버 창 안이다.
 * ③ 뜻: 5분 뒤에 타려는 사람에게 필요한 것은 예약 방이 아니라 **지금 출발**이고, 그쪽은
 * 경로·요금 추정까지 받는다.
 */
internal const val RESERVATION_MIN_LEAD_MINUTES = 10L

/**
 * 16 도착지 확인에서 고른 **출발 시점의 종류**.
 *
 * 두 값이 서로 다른 엔드포인트로 간다 — [NOW] 는 `POST /matching/rooms`(실시간), [SCHEDULED] 는
 * `POST /matching/reservations`(예약). 그래서 정원 범위·보여 주는 추정·실패 문구가 전부 갈린다.
 * 「예약 여부」를 불리언으로 두지 않은 이유는 그 분기들이 화면 곳곳에 있어 `when` 이 빠진 가지를
 * 잡아 주는 편이 안전하기 때문이다([MeetingStage][com.moyeota.presentation.feature.matching.MeetingStage] 와 같은 판단).
 */
internal enum class DepartureMode {
    /** 지금 모집을 시작하는 실시간 방 — 이 앱이 지금까지 만들던 유일한 종류다. */
    NOW,

    /** 출발 시각을 정해 두는 예약 방. 서버 `RESERVED`. */
    SCHEDULED,
}

/**
 * 「출발 시각」 칩 두 개의 문구. [DepartureMode.NOW] 가 기본값이라 **지금까지의 동작이 기본**이다.
 */
internal fun departureModeLabel(mode: DepartureMode): String = when (mode) {
    DepartureMode.NOW -> "지금 출발"
    DepartureMode.SCHEDULED -> "시간 정하기"
}

/**
 * 고를 수 있는 정원 — **예약은 2~3, 실시간은 1~3**.
 *
 * 서버가 그렇게 받는다: 실시간 `Capacity` 는 1~3 인데 예약은 2 이상이어야 하고(`capacity 2~3`),
 * 벗어나면 400 `INVALID_CAPACITY` 다. 혼자 타는 방을 **미리** 예약하는 것은 서버가 인정하지 않는
 * 쓰임이라(합승이 아니다) 선택지에서 지운다 — 띄워 놓고 400 을 맞게 하지 않는다.
 *
 * 1 을 고른 채 「시간 정하기」로 바꾸는 길이 **반드시 있다**(기본값은 3 이지만 사용자는 1 을 고를 수
 * 있다). 그 상태를 그대로 보내면 400 이므로 [capacityFor] 가 끌어올린다.
 */
internal fun capacityChoices(mode: DepartureMode): List<Int> = when (mode) {
    DepartureMode.NOW -> listOf(1, 2, 3)
    DepartureMode.SCHEDULED -> listOf(2, 3)
}

/**
 * **지금 고른 정원을 이 모드에서 보낼 수 있는 값으로 접는다.**
 *
 * 「1명」을 고른 뒤 「시간 정하기」로 바꾼 순간이 이 함수가 있는 이유다. 그 상태는 서버에 보낼 수
 * 없는데(400 `INVALID_CAPACITY`), 사용자가 정원 칩을 다시 건드릴 이유는 없다 — 화면에서 1 칩이
 * 사라졌을 뿐 자기가 고친 적이 없으니 2 로 바뀐 것도 모른다. 그래서 **고른 값을 지우지 않고**
 * (모드를 되돌리면 1 이 그대로 선택돼 있다) 전송·표시용 값만 범위 안으로 접고, 화면은 그 사실을
 * 한 줄로 말한다([RESERVATION_CAPACITY_NOTICE]).
 *
 * 범위를 벗어난 값은 **가장 가까운 유효값**이 된다(1 → 2). 0 이나 4 처럼 화면에 없는 값이 들어와도
 * 같은 규칙이다 — `coerceIn` 과 같은 뜻이고, 선택지가 연속 구간이라 그걸로 충분하다.
 */
internal fun capacityFor(mode: DepartureMode, selected: Int): Int {
    val choices = capacityChoices(mode)
    return selected.coerceIn(choices.first(), choices.last())
}

/**
 * 「시간 정하기」에서 정원 칩 아래 붙는 한 줄 — **1 칩이 사라진 이유**를 고르는 자리에서 말한다.
 *
 * [soloCapacityNotice] 와 같은 자리·같은 모양(13sp Medium 보조 문구)이지만 뜻이 반대다: 저쪽은
 * 「1 을 골랐으니 혼자 간다」, 이쪽은 「예약에서는 1 을 고를 수 없다」. 서버 규칙을 그대로 적는다 —
 * 「왜 2명부터인가」를 설명하지 않는 이유는 앱이 그 이유를 모르기 때문이다(서버가 그렇게 받는다).
 */
internal const val RESERVATION_CAPACITY_NOTICE = "예약 방은 2명부터 만들 수 있어요"

/**
 * 예약 방에 **경로·예상 요금·소요 시간이 없다**는 사실을 CTA 앞에서 말한다.
 *
 * ### 이 문구가 이 작업에서 가장 조심한 자리다 ★
 * 16 모달은 지금까지 「예상 요금 1인 2,630원」과 「예상 N분 · 총 7,900원」을 보여 줬다. 그 값은
 * `POST /matching/routes`(좌표 두 개로 뽑는 추정)에서 오고, **예약에서도 호출은 성공한다** — 즉
 * 아무것도 고치지 않으면 화면은 예약 방에도 금액을 적는다. 그게 거짓이 되는 이유는 둘이다:
 *
 * 1. **서버가 예약 방에 요금·경로·소요 시간을 넣지 않는다.** 만들어진 방은 세 값이 비어 있어
 *    21 대기·22 상세에서 금액이 **사라진다** — 사용자는 2,630원을 보고 동의했는데 방에는 그 숫자가
 *    없다. 어느 쪽이 참인지 알 길이 없는 어긋남이다.
 * 2. **그 추정은 「지금」의 값이다.** 3일 뒤 오후 6:30 의 요금·소요 시간을 지금의 교통으로 셈한
 *    값이고, 서버는 그걸 그 방의 값으로 쓰지 않는다.
 *
 * 그래서 숫자를 **감춘다**(0 원·「계산 중」 같은 자리값을 두지 않는다 — `RideHistoryFare` 가 1차 모드
 * 기록에서 요금 줄을 아예 그리지 않는 것과 같은 규칙). 대신 **왜 없는지**를 적는다: 자리가 비어
 * 있으면 사용자는 로딩 실패로 읽는다.
 *
 * 지도의 경로선은 **지운 값이 아니다**. 그건 사용자가 찍은 두 핀 사이의 길 모양(기하)이고 시각·
 * 금액 예측이 아니다 — 어디로 가는 방인지 보여 주는 일은 예약에서도 참이다.
 */
internal const val RESERVATION_NO_ESTIMATE_NOTICE = "예약 방은 예상 요금과 소요 시간을 미리 알 수 없어요"

/**
 * 고를 수 있는 출발 시각이 **없다** — 비활성 CTA 옆에 **왜 못 누르는지**를 둔다(빈 비활성 버튼을 두지 않는다).
 *
 * 휠로 바뀐 뒤로는 「아직 안 골랐다」가 없다(휠은 늘 값을 보여 주고 그 값이 기본 선택이다 —
 * [reservationSelectedTime]). 그래서 이 줄이 뜨는 경우는 하나뿐이다: 고른 날에 남은 시각이 없을 때
 * (늦은 밤의 「오늘」 — [RESERVATION_NO_SLOT_NOTICE] 가 카드 안에서 같은 사실을 길게 말한다).
 */
internal const val RESERVATION_NEEDS_TIME_NOTICE = "출발 시각을 골라 주세요"

/**
 * ★ **예약 방에서는 나갈 수 없다** — 누르기 **전에** 말해야 하는 단 하나의 사실.
 *
 * ### 근거 (서버 소스 실측)
 * 예약 방은 만들어진 순간부터 확정 상태라(`Ride.isConfirmed` 가 늘 true) `DELETE /matching/leave/{id}`
 * 가 **언제나** 409 `CANNOT_LEAVE_CONFIRMED_PARTY` 다. 즉 들어간 사람에게 방을 벗어날 길이 **없고**,
 * 출발 시각에 나타나지 않으면 누군가 「출발」을 누르는 순간 **노쇼로 기록된다**(그 대가는
 * `departConfirmMessage` 가 이미 말하는 그 제재다 — 약속 시간부터 노쇼 횟수 × 24시간).
 *
 * ### 왜 다이얼로그가 아니라 버튼 **앞**인가
 * 실시간 방은 「그만 찾기」로 언제든 빠져나올 수 있어서, 사용자는 **방에 들어가는 일을 되돌릴 수
 * 있는 선택으로 배워 왔다.** 예약은 같은 모양의 버튼으로 전혀 다른 약속을 하게 만든다 — 그 차이를
 * 누른 뒤에 알리면 늦다(`MEETING_CONFIRM_HELP` 가 확정 버튼 앞에 같은 이유로 놓여 있다).
 *
 * ### 두 자리가 한 문장을 공유한다
 * 만드는 쪽(16)과 합류하는 쪽(20 합류 확인)이 같은 결과를 맞는데, 둘이 각자 문구를 들고 있으면
 * 한쪽만 다듬어져 **같은 제약이 다른 무게로** 읽힌다. 바뀌는 것은 동사 하나뿐이다.
 *
 * 「알림을 보내 드려요」류를 붙이지 않는다 — 서버에 예약 리마인더 푸시가 있지만 앱이 그 발송을
 * 확인할 길이 없고, 확인할 수 없는 것을 약속하지 않는 것이 이 저장소의 규칙이다.
 *
 * @param joining 합류하는 쪽인가(false 면 만드는 쪽)
 */
internal fun reservationCommitmentNotice(joining: Boolean): String {
    val action = if (joining) "합류하면" else "만들면"
    return "예약 방은 $action 나갈 수 없어요. 출발 시각에 안 가면 노쇼로 기록돼요"
}

/**
 * 그 날에 고를 수 있는 시각이 하나도 없다 — **늦은 밤의 「오늘」**에서 나온다.
 *
 * 오늘의 마지막 값은 23:50 이고 가장 이른 값은 「지금 + 10분」을 10분 단위로 올린 시각이므로
 * ([reservationTimeRange]) 23:40 을 넘기면 오늘 고를 수 있는 시각이 없다. 그때는 「내일」부터 고르면
 * 되고, 이 줄이 그 사실을 말한다 — 휠 자리를 비워 두면 휠이 사라진 이유를 모른다.
 *
 * 반대쪽 끝(7일 뒤)은 **비지는 않지만 짧아진다**: 그 날은 자정부터 「지금 시각(10분 단위 내림)」까지만
 * 고를 수 있다([reservationTimeRange] 의 둘째 경계).
 */
internal const val RESERVATION_NO_SLOT_NOTICE = "이 날은 고를 수 있는 시간이 없어요. 다른 날을 골라 주세요"

/**
 * 고를 수 있는 날짜 — **오늘부터 7일 뒤까지 8개**(한국 달력).
 *
 * 검색(`GET /matching/reservations/search`)의 `date` 범위와 생성의 출발 시각 창이 같은 7일이라
 * 한 함수가 둘을 함께 댄다. 오늘을 포함하는 건 서버 규칙이다 — 오늘이면 「이미 출발한 방은 빠진다」.
 *
 * @param now 지금 시각. **호출부가 넘긴다** — 안에서 시계를 읽으면 경계를 테스트할 수 없다
 * @param zone 달력을 셈하는 시간대. 기본값이 [KOREA_ZONE] 인 근거는 그 상수의 KDoc 에 있다
 */
internal fun reservationDates(now: Instant, zone: ZoneId = KOREA_ZONE): List<LocalDate> {
    val today = now.atZone(zone).toLocalDate()
    return (0..RESERVATION_WINDOW_DAYS).map { today.plusDays(it) }
}

/**
 * 검색 파라미터 `date` 에 실을 값 — `yyyy-MM-dd`.
 *
 * [LocalDate.toString] 이 곧 ISO-8601 달력 날짜라 하는 일은 없지만 **함수로 둔다**: 이 값을
 * 「ISO 문자열을 잘라서」 만들지 말라는 금지를 이름으로 남기는 자리다. 서버가 UTC 로 내려주는
 * 순간 문자열에서 앞 10자를 떼면 KST 자정 직후가 **전날**이 된다(채팅·34 기록에서 실제로 났던
 * 결함과 같은 함정). 날짜는 [reservationDates] 가 시간대를 거쳐 만든 [LocalDate] 에서만 나온다.
 */
internal fun reservationDateApiValue(date: LocalDate): String = date.toString()

/**
 * 그 날짜가 **검색할 수 있는 날**인가 — 오늘~7일 뒤(한국 달력). 서버 400 `INVALID_SEARCH_DATE` 경계다.
 *
 * 칩이 [reservationDates] 에서 나오므로 화면에서는 늘 참이다. 그래도 두는 이유는 **날짜가 다른 길로
 * 들어올 수 있기** 때문이다: 탐색 탭은 오래 살아 있어서(백스택에 남는다) 자정을 넘기면 어제 고른
 * 칩이 그대로 남는다 — 그때 이 검사가 조회를 막고 화면이 날짜를 다시 고르게 한다.
 */
internal fun isReservationSearchDate(date: LocalDate, now: Instant, zone: ZoneId = KOREA_ZONE): Boolean {
    val dates = reservationDates(now, zone)
    return date >= dates.first() && date <= dates.last()
}

/**
 * 그 날짜에 고를 수 있는 출발 시각의 **범위**(양 끝 포함, [RESERVATION_TIME_STEP_MINUTES] 단위).
 * 하나도 없으면 null.
 *
 * 두 경계가 각각 서버의 거절 하나를 지운다.
 *
 * | 경계 | 값 | 없으면 |
 * |---|---|---|
 * | 가장 이른 | 「지금 + [RESERVATION_MIN_LEAD_MINUTES]분」을 10분 단위로 **올림** | 오늘 날짜에서 **이미 지난 시각**과 몇 분 뒤가 골라진다 → 400 `INVALID_DEPARTURE_TIME` |
 * | 가장 늦은 | 「지금 + [RESERVATION_WINDOW_DAYS]일」을 10분 단위로 **내림** | 7일 뒤 날짜의 **늦은 시각**이 창을 넘는다(지금이 09:05 면 7일 뒤 09:10 은 이미 밖이다) → 같은 400 |
 *
 * 둘째 경계가 눈에 안 보이는 쪽이다: 「7일 뒤」가 날짜 띠에 있으니 그 날은 아무 시각이나 될 것처럼
 * 보이는데, 실제로 허용되는 건 그 날 **자정부터 지금 시각까지**다.
 *
 * ### 올림·내림의 방향
 * 둘 다 **창 안쪽으로** 깎는다 — 올림은 「지금+10분」보다 이르지 않게, 내림은 「지금+7일」보다 늦지
 * 않게. 정확히 경계에 떨어진 값(초까지 0)은 그대로 쓴다: 앞쪽은 이미 10분 여유를 품은 값이고,
 * 뒤쪽은 서버가 `isAfter` 로 보므로 경계 자체를 받는다. 초·나노초가 남아 있으면 분 단위로 먼저 올리고
 * (앞쪽) 버린다(뒤쪽) — 13:00:30 + 10분을 13:10 으로 내리면 여유가 30초 모자란다.
 *
 * 계산은 **그 시간대의 벽시계**로 한다(UTC 순간으로 반올림하지 않는다). 한국 시간대는 정시 오프셋이라
 * 결과가 같지만, 30분·45분 오프셋 시간대에서 UTC 로 반올림하면 휠의 「10분 단위」가 어긋난다.
 *
 * @param now 지금 시각. **호출부가 넘긴다** — 안에서 시계를 읽으면 경계를 테스트할 수 없다
 */
internal fun reservationTimeRange(
    date: LocalDate,
    now: Instant,
    zone: ZoneId = KOREA_ZONE,
): ReservationTimeRange? {
    val step = RESERVATION_TIME_STEP_MINUTES
    val earliest = ceilToMinuteStep(
        now.plus(RESERVATION_MIN_LEAD_MINUTES, ChronoUnit.MINUTES).atZone(zone).toLocalDateTime(),
        step,
    )
    val latest = floorToMinuteStep(
        now.plus(RESERVATION_WINDOW_DAYS, ChronoUnit.DAYS).atZone(zone).toLocalDateTime(),
        step,
    )
    val dayFirst = date.atStartOfDay()
    val dayLast = date.atTime(LocalTime.of(23, 60 - step))
    val from = maxOf(dayFirst, earliest)
    val to = minOf(dayLast, latest)
    if (from.isAfter(to)) return null
    return ReservationTimeRange(earliest = from.toLocalTime(), latest = to.toLocalTime())
}

/**
 * 한 날짜 안에서 고를 수 있는 시각의 양 끝(포함). 두 값 모두 [RESERVATION_TIME_STEP_MINUTES] 의 배수다.
 * 만드는 길은 [reservationTimeRange] 하나다 — 경계를 직접 지어내지 않는다.
 */
internal data class ReservationTimeRange(val earliest: LocalTime, val latest: LocalTime) {
    operator fun contains(time: LocalTime): Boolean = !time.isBefore(earliest) && !time.isAfter(latest)
}

/** [t] 이상인 가장 이른 [step] 분 단위 시각. 초·나노초가 남아 있으면 먼저 다음 분으로 올린다. */
internal fun ceilToMinuteStep(t: LocalDateTime, step: Int): LocalDateTime {
    val minutes = t.truncatedTo(ChronoUnit.MINUTES)
    val base = if (minutes == t) minutes else minutes.plusMinutes(1)
    val remainder = base.minute % step
    return if (remainder == 0) base else base.plusMinutes((step - remainder).toLong())
}

/** [t] 이하인 가장 늦은 [step] 분 단위 시각. 초·나노초는 버린다. */
internal fun floorToMinuteStep(t: LocalDateTime, step: Int): LocalDateTime {
    val minutes = t.truncatedTo(ChronoUnit.MINUTES)
    return minutes.minusMinutes((minutes.minute % step).toLong())
}

/**
 * 시 휠에 보일 값 — 범위 안에 한 칸이라도 걸치는 시(時)만. **범위 밖의 시는 아예 없다**(흐리게 남기지
 * 않는다 — 손가락이 거기서 멈추면 「고를 수 없는 값을 고른」 순간이 생긴다).
 */
internal fun reservationHourOptions(range: ReservationTimeRange): List<Int> =
    (range.earliest.hour..range.latest.hour).toList()

/**
 * 분 휠에 보일 값 — 고른 [hour] 안에서 범위에 드는 10분 단위만(00·10·…·50 중 일부).
 *
 * 오늘의 첫 시(예: 13:20 이 가장 이르면 13시)와 7일 뒤의 마지막 시에서만 줄어든다 — 그 밖의 시는
 * 여섯 칸이 다 있다.
 */
internal fun reservationMinuteOptions(range: ReservationTimeRange, hour: Int): List<Int> =
    (0 until 60 step RESERVATION_TIME_STEP_MINUTES).filter { minute ->
        LocalTime.of(hour.coerceIn(0, 23), minute) in range
    }

/**
 * 휠이 **지금 보여 줄(= 서버에 보낼) 출발 시각** — 고른 날의 범위로 접은 값. 그 날에 시각이 없으면 null.
 *
 * ### 기본 선택이 생겼다 ★ 규칙 변경 (2026-11, 오너 승인)
 * 칩 시절에는 **기본 선택을 두지 않았다** — 날짜마다 첫 칩의 뜻이 달라(오늘은 「가장 빠른 시각」,
 * 다른 날은 자정) 자동으로 골라 두면 아무도 원하지 않은 00:00 예약이 한 번의 탭으로 만들어진다는
 * 근거였다. 휠은 **늘 어떤 값을 가운데에 보여 준다** — 「안 골랐다」를 그릴 방법이 없고, 빈 휠은 고장으로
 * 읽힌다. 그래서 아직 손대지 않았으면([stored] 가 null) **그 날의 가장 이른 시각**을 선택으로 보고 CTA 를
 * 켠다. 00:00 사고에 대한 방어는 자리를 옮겼다:
 * - 휠의 가운데 줄이 그 값을 크게 보여 주고(굴려야만 바뀐다),
 * - CTA 바로 위 줄이 날짜와 함께 되읽어 준다(「내일 오전 12:00 출발」 — [reservationDepartureSummary]).
 *
 * ### 날짜를 바꾸면 접는다
 * 고른 값([stored])은 지우지 않고 **보여 줄 값만** 그 날의 범위로 접는다 — 7일 뒤로 가서 18:00 이
 * 13:00 으로 접혀도 다른 날로 돌아오면 18:00 이 살아 있다(정원의 [capacityFor] 와 같은 처방).
 * 10분 단위가 아닌 값이 들어오면 먼저 10분 단위로 내린다(휠에 없는 값을 보내지 않는다).
 *
 * **이 함수가 돌려준 값이 곧 보내는 값이다** — 화면은 이 값으로 휠을 그리고, 같은 값으로
 * [reservationDepartureIso] 를 만든다. 시계를 두 번 읽지 않는다(기준 시각 `now` 는 모달이 한 번 읽는다).
 *
 * @param stored 사용자가 휠로 고른 시각. 아직 손대지 않았으면 null
 */
internal fun reservationSelectedTime(stored: LocalTime?, range: ReservationTimeRange?): LocalTime? {
    range ?: return null
    stored ?: return range.earliest
    val snapped = stored.withSecond(0).withNano(0)
        .minusMinutes((stored.minute % RESERVATION_TIME_STEP_MINUTES).toLong())
    return when {
        snapped.isBefore(range.earliest) -> range.earliest
        snapped.isAfter(range.latest) -> range.latest
        else -> snapped
    }
}

/**
 * 시 휠을 [hour] 로 굴렸을 때의 새 시각 — **분은 지키되 범위 안으로 접는다.**
 *
 * 18:40 에서 시만 13 으로 굴렸는데 오늘은 13:20 부터라면 13:40 이 된다(범위 안이니 그대로). 13:00
 * 이었다면 13:20 으로 올라간다 — 시를 바꿨다고 분까지 0 으로 되돌리면 사용자가 맞춰 둔 분을 잃는다.
 */
internal fun reservationTimeWithHour(current: LocalTime, hour: Int, range: ReservationTimeRange): LocalTime =
    reservationSelectedTime(LocalTime.of(hour.coerceIn(0, 23), current.minute), range) ?: current

/** 분 휠을 [minute] 로 굴렸을 때의 새 시각. 분 휠은 범위 안 값만 담으므로 대개 그대로지만 한 번 더 접는다. */
internal fun reservationTimeWithMinute(current: LocalTime, minute: Int, range: ReservationTimeRange): LocalTime =
    reservationSelectedTime(LocalTime.of(current.hour, minute.coerceIn(0, 59)), range) ?: current

/** 시 휠의 글자 — 24시간제 그대로(0~23). 오전·오후는 CTA 위 되읽기 줄([reservationTimeLabel])이 말한다. */
internal fun reservationHourLabel(hour: Int): String = hour.toString()

/**
 * 분 휠의 글자 — 두 자리(`00`·`10`). 시 휠은 0~23 을 그대로 쓴다([reservationHourLabel]).
 * 분을 한 자리로 쓰면 「6 시 0 분」처럼 읽혀 휠이 흔들려 보인다.
 */
internal fun reservationMinuteLabel(minute: Int): String = "%02d".format(minute)

/**
 * 날짜 띠([com.moyeota.core.designsystem.component.WeekDateStrip])의 칸들 — 16 예약 날짜와 17 「날짜로
 * 찾기」가 **같은 함수**로 만든다(같은 8일을 두 화면이 다르게 그리지 않게).
 *
 * 칸 = 위에 요일 한 글자, 아래에 일. 토요일은 파랑, 일요일은 빨강([DateStripTone]) — 달력의 관습이고,
 * 색은 덧붙인 단서일 뿐 요일 글자가 늘 함께 있다. 오늘은 [DateStripCell.isToday] 로 작은 점이 찍힌다.
 *
 * 「오늘」·「내일」을 칸에 **글자로 쓰지 않는다** — 칸 폭(48dp)에 두 글자 요일 자리를 만들면 다른 칸과
 * 모양이 갈린다. 대신 스크린리더 문장과 CTA 위 되읽기 줄([reservationDepartureSummary])이 「오늘」을 말한다.
 *
 * 고를 시각이 없는 날(16 의 늦은 밤 「오늘」)은 **칸을 남기되 누르지 못하게** 한다([isSelectable]) — 칸을
 * 지우면 요일 줄이 밀려 오늘을 찾을 수 없다. 17 「날짜로 찾기」는 검색이라 모든 날이 눌린다(기본값).
 *
 * @param today [reservationDates] 의 첫 원소 — **같은 시간대로 셈한 오늘**이어야 점이 맞는 칸에 찍힌다
 * @param isSelectable 그 날을 고를 수 있는가. 16 은 `reservationTimeRange(date, now) != null` 을 넘긴다
 */
internal fun reservationDateCells(
    dates: List<LocalDate>,
    today: LocalDate,
    isSelectable: (LocalDate) -> Boolean = { true },
): List<DateStripCell> =
    dates.map { date ->
        val weekday = KoreanDayOfWeek.getValue(date.dayOfWeek)
        DateStripCell(
            weekday = weekday,
            day = date.dayOfMonth.toString(),
            tone = when (date.dayOfWeek) {
                DayOfWeek.SATURDAY -> DateStripTone.SATURDAY
                DayOfWeek.SUNDAY -> DateStripTone.SUNDAY
                else -> DateStripTone.WEEKDAY
            },
            isToday = date == today,
            contentDescription = reservationDateCellDescription(date, today),
            enabled = isSelectable(date),
        )
    }

/**
 * 16 이 **실제로 고른 것으로 볼 날짜**의 인덱스 — 저장된 [storedIndex] 가 고를 수 없는 날이면 고를 수 있는
 * 첫 날로 옮긴다. 고를 수 있는 날이 하나도 없으면(일어나지 않지만) 저장값을 범위로 접어 그대로 쓴다.
 *
 * 왜 필요한가: 기본 선택은 「오늘」(0)인데, 밤 23:40 이 넘으면 오늘은 고를 시각이 없어 띠에서 **누를 수
 * 없는 칸**이 된다([reservationDateCells] 의 `isSelectable`). 그 칸이 선택된 채로 열리면 휠 자리에는
 * 「이 날은 고를 수 있는 시간이 없어요」만 남고 CTA 는 꺼진다 — 사용자가 고친 적 없는 기본값 때문에
 * 화면이 막힌 것처럼 보인다. 저장값은 바꾸지 않는다 — 정원·시각과 같은 「고른 값은 지우지 않고 보여 줄
 * 값만 접는다」 규칙이다.
 */
internal fun reservationSelectableDateIndex(
    storedIndex: Int,
    dates: List<LocalDate>,
    isSelectable: (LocalDate) -> Boolean,
): Int {
    if (dates.isEmpty()) return 0
    val clamped = storedIndex.coerceIn(0, dates.lastIndex)
    if (isSelectable(dates[clamped])) return clamped
    return dates.indexOfFirst(isSelectable).takeIf { it >= 0 } ?: clamped
}

/**
 * 날짜 띠 위의 **달 표시** — `11월`, 달을 넘으면 `11–12월`(해를 넘으면 `12–1월`). 날짜가 없으면 null.
 *
 * 칸에는 일(日) 숫자만 있어서 띠만 보면 「1, 2, 3」이 몇 월인지 알 수 없다 — 특히 월말에 띠가 다음 달로
 * 넘어가는 주에 「30 · 1」이 나란히 서면 혼란스럽다. 그래서 띠가 덮는 달을 한 줄로 적는다.
 */
internal fun reservationMonthLabel(dates: List<LocalDate>): String? {
    val first = dates.firstOrNull() ?: return null
    val last = dates.last()
    return if (first.monthValue == last.monthValue && first.year == last.year) {
        "${first.monthValue}월"
    } else {
        "${first.monthValue}–${last.monthValue}월"
    }
}

/**
 * 날짜 칸의 스크린리더 문장 — `오늘, 11월 12일 목요일` · `11월 14일 토요일`.
 *
 * 칸에는 요일 한 글자와 숫자만 있어 그대로 읽히면 「토, 14」가 된다. 몇 월인지와 오늘·내일을 말로 채운다.
 */
internal fun reservationDateCellDescription(date: LocalDate, today: LocalDate): String {
    val base = "${date.monthValue}월 ${date.dayOfMonth}일 ${KoreanDayOfWeek.getValue(date.dayOfWeek)}요일"
    return when (date) {
        today -> "오늘, $base"
        today.plusDays(1) -> "내일, $base"
        else -> base
    }
}

/**
 * 서버에 보낼 출발 시각 — ISO-8601 **순간** 문자열(`2026-11-09T09:30Z`).
 *
 * 고른 것은 한국 달력의 날짜와 벽시계 시각이고, 서버가 받는 것은 `Instant` 다. 그 변환이 일어나는
 * 자리를 하나로 묶는다 — **오프셋 없는 로컬 시각(`"2026-11-09T18:30:00"`)은 400** 이고, 그 400 은
 * 어떤 사유 코드에도 걸리지 않아 일반 문구로 떨어진다(가장 찾기 어려운 실패다 —
 * `RideRepository.confirmMeeting` KDoc 의 ★ 참고).
 *
 * [Instant.toString] 은 초가 0 이면 `:00` 을 생략하므로 실제 값은 `...T09:30Z` 다 — 둘 다
 * ISO-8601 이고 서버(Jackson `Instant`)가 같게 읽는다(`meetingPresetIso` 와 같은 판단).
 */
internal fun reservationDepartureIso(
    date: LocalDate,
    time: LocalTime,
    zone: ZoneId = KOREA_ZONE,
): String = date.atTime(time).atZone(zone).toInstant().toString()

/**
 * 16 이 [MatchConditions.departureAt] 에 싣는 출발 시각 — **「시간 정하기」일 때만** 있다.
 *
 * 이 값이 있으면 CTA 가 `POST /matching/reservations` 로 가고, 없으면 `POST /matching/rooms` 로 간다
 * (`MatchConditions` KDoc). 그런데 시각 휠은 **늘 값을 보여 준다**(기본 = 그 날의 가장 이른 시각,
 * [reservationSelectedTime]) — 모드와 상관없이 휠 값을 실으면 「지금 출발」의 「같이 탈 사람 찾기」가
 * 전부 예약 방이 된다. 칩 시절에도 시각을 골랐다가 「지금 출발」로 되돌리면 같은 일이 났다.
 * 그래서 모드가 이 값의 유무를 정한다 — 모드를 되돌려도 휠에 고른 시각은 남고(다시 「시간 정하기」를
 * 누르면 그대로 보인다), 보내지만 않는다.
 */
internal fun matchDepartureAt(scheduled: Boolean, date: LocalDate, time: LocalTime?): String? =
    if (scheduled) time?.let { reservationDepartureIso(date, it) } else null

/** 요일 한 글자. `java.time` 의 로케일 표기에 맡기지 않는다 — 기기 로케일이 영어면 「Sun」이 섞인다. */
private val KoreanDayOfWeek = mapOf(
    DayOfWeek.MONDAY to "월",
    DayOfWeek.TUESDAY to "화",
    DayOfWeek.WEDNESDAY to "수",
    DayOfWeek.THURSDAY to "목",
    DayOfWeek.FRIDAY to "금",
    DayOfWeek.SATURDAY to "토",
    DayOfWeek.SUNDAY to "일",
)

/**
 * 날짜의 문구 — 오늘·내일은 **이름으로**, 그 뒤는 `11월 9일 (일)`. CTA 위 되읽기 줄([reservationDepartureSummary])
 * ·17 의 접힘 요약·예약 카드가 쓴다(고르는 자리는 주간 달력 띠다 — [reservationDateCells]).
 *
 * 요일을 붙이는 이유: 7일 창에서 사람이 고르는 기준은 날짜 숫자가 아니라 「토요일 저녁」 같은
 * 요일이다. 반대로 오늘·내일에 숫자를 함께 적지 않는 건 칩 폭 때문이다 — 가로 스크롤 칩 8개가
 * 한 줄에 들어와야 어제 고른 칩이 어디 있는지 보인다.
 *
 * @param today [reservationDates] 의 첫 원소. **호출부가 넘긴다** — 같은 시간대로 셈한 오늘이어야
 *   「오늘」 칩과 보내는 날짜가 어긋나지 않는다
 */
internal fun reservationDateLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "오늘"
    today.plusDays(1) -> "내일"
    else -> "${date.monthValue}월 ${date.dayOfMonth}일 (${KoreanDayOfWeek[date.dayOfWeek]})"
}

/**
 * 되읽기 줄·카드의 시각 문구 — `오후 6:30`(휠은 24시간제 숫자를 쓰고, 오전·오후는 이 문구가 말한다).
 *
 * 0시·12시가 12 가 되어야 한다(`hour % 12` 만 쓰면 자정이 「오전 0:30」이 된다 — `meetingTimeLabel`·
 * `rideHistoryDateLabel` 과 같은 함정·같은 처방).
 */
internal fun reservationTimeLabel(time: LocalTime): String {
    val meridiem = if (time.hour < 12) "오전" else "오후"
    val hour12 = (time.hour % 12).let { if (it == 0) 12 else it }
    return "$meridiem $hour12:${"%02d".format(time.minute)}"
}

/**
 * 고른 출발 시각을 CTA 앞에서 **되읽어 준다** — `오늘 오후 6:30` · `11월 9일 (일) 오후 6:30`.
 *
 * 날짜 띠와 시각 휠이 각각 떨어진 줄에 있어서, 두 선택이 합쳐져 무엇이 되는지 확인할 기회가 누르기
 * 직전의 이 한 줄뿐이다(`meetingConfirmTitle` 이 프리셋 「10분」을 시각으로 되읽어 주는 것과 같은
 * 이유). 예약 방은 만든 뒤 시간을 바꿀 수 없으므로 더 그렇다.
 */
internal fun reservationDepartureSummary(date: LocalDate, time: LocalTime, today: LocalDate): String =
    "${reservationDateLabel(date, today)} ${reservationTimeLabel(time)}"

/**
 * 사유를 가릴 수 없는 예약 생성 실패의 일반 문구 — 네트워크 실패와 **모르는 코드**가 여기로 떨어진다.
 * [OPEN_FAILED_MESSAGE] 와 같은 모양이고 「예약 방」이라는 말만 다르다.
 */
internal const val RESERVATION_OPEN_FAILED_MESSAGE = "예약 방을 만들지 못했어요. 잠시 후 다시 시도해 주세요"

/**
 * 예약 방 생성 실패 → 사용자 문구. [openFailureMessage] 와 **갈라 두는 쪽**이다.
 *
 * | 사유 | 서버 코드(HTTP) | 사용자가 할 일 |
 * |---|---|---|
 * | 노쇼 제재 중 | `NO_SHOW_RESTRICTED` (403) | **지금은 없다.** 제재가 끝나야 풀린다 |
 * | 출발 2시간 안에 다른 예약 | `PARTY_TIME_CONFLICT` (409) | 다른 시간을 고른다 |
 * | 출발 시각이 창을 벗어났다 | `INVALID_DEPARTURE_TIME` (400) | 다른 시간을 고른다 |
 * | 이미 참여 중인 방 | `ALREADY_JOINED_OTHER_PARTY` (409) | 홈 배너로 그 방을 정리한다 |
 * | 출발지 = 도착지 | `SAME_DEPARTURE_DESTINATION` (400) | 핀을 옮긴다 |
 * | 서비스 지역 밖 | `OUT_OF_SERVICE_AREA` (400) | 다른 장소를 고른다 |
 * | 정원이 2~3 아님 | `INVALID_CAPACITY` (400) | 인원을 다시 고른다(사실상 앱 버그다) |
 * | 그 밖·네트워크 | 코드 없음 | [RESERVATION_OPEN_FAILED_MESSAGE] |
 *
 * ### 왜 실시간 문구를 그대로 쓰지 않는가
 * 겹치는 네 가지는 **같은 문장을 재사용한다**(아래에서 [openFailureMessage] 를 부른다). 갈라야
 * 하는 것은 두 곳이다.
 *
 * 1. **정원.** 실시간은 「1~3명」, 예약은 「2~3명」이다. 같은 문구를 쓰면 예약에서 400 을 맞은
 *    사람에게 **고를 수 없는 1 을 고르라고** 말하게 된다.
 * 2. **예약에만 오는 세 코드.** 특히 403 `NO_SHOW_RESTRICTED` 와 409 `PARTY_TIME_CONFLICT` 는
 *    실시간 쪽 표에 없어서, 갈라 두지 않으면 둘 다 「잠시 후 다시 시도해 주세요」가 된다 —
 *    **영구히 거짓**이다(제재는 시각이 지나야 풀리고, 시간 충돌은 다른 시간을 골라야 풀린다).
 *
 * ### 403·409 에 「다시 시도」류를 붙이지 않는다 ★
 * `FinishFailureMessageTest` 가 세운 규칙이다. 특히 403 `NO_SHOW_RESTRICTED` 는 **재시도할 수
 * 없는 실패**다 — 끝나는 시각(`restrictedUntil`)이 서버에 있고 그 시각까지는 몇 번 눌러도 같은
 * 403 이다. 그 시각을 **여기서 말하지 않는 것**도 규칙이다: 이 화면은 그 값을 읽지 않는다(프로필
 * 응답에 있다). 모르는 숫자를 적지 않고, 다른 화면이 보여 준다고 약속하지도 않는다 — 말할 수 있는
 * 것은 **왜 막혔는가**와 **무엇이 바뀌어야 풀리는가**뿐이다.
 *
 * 경로 실패(`ROUTE_*`) 가지는 **일부러 없다.** 서버는 예약 방에 경로를 계산하지 않으므로 그 코드가
 * 올 수 없고, 올 수 없는 사유를 적으면 모르는 코드가 그 문장으로 떨어져 **없는 원인**을 말한다.
 */
internal fun reservationOpenFailureMessage(e: PartyOpenException): String = when {
    // 서버 코드 문자열은 **도메인이 걸러 준 프로퍼티로만** 본다 — presentation 이 코드 이름을
    // 알면 서버가 enum 을 바꿀 때 조용히 어긋난다([PartyOpenException] KDoc).
    e.isNoShowRestricted ->
        "노쇼 제재 중에는 방을 만들 수 없어요. 제재가 끝나면 다시 만들 수 있어요"
    e.isPartyTimeConflict ->
        "출발 2시간 안에 다른 예약이 있어요. 다른 시간으로 골라 주세요"
    e.isInvalidDepartureTime ->
        "출발 시각은 지금부터 7일 안이어야 해요. 다른 시간으로 골라 주세요"
    e.isInvalidCapacity -> "예약 방 인원은 2~3명이어야 해요"
    // 아래 네 가지(이미 참여 중·출발지=도착지·서비스 지역 밖·그 밖)는 실시간과 **같은 사실**이라
    // 같은 문장을 쓴다. 다만 「그 밖」의 일반 문구만 예약 쪽으로 바꿔 끼운다 — 「동승 방을 만들지
    // 못했어요」는 예약을 만들려던 사람에게 무엇이 실패했는지 모호하다.
    e.isAlreadyInAnotherParty || e.isSameDepartureDestination || e.isOutOfServiceArea ->
        openFailureMessage(e)
    else -> RESERVATION_OPEN_FAILED_MESSAGE
}
