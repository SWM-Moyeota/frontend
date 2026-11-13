package com.moyeota.presentation.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.moyeota.domain.model.MatchCandidate
import com.moyeota.core.designsystem.component.MapPlaceholder
import com.moyeota.core.designsystem.component.MapSheetScaffold
import com.moyeota.core.designsystem.component.NavigationBarSpacer
import com.moyeota.core.designsystem.component.NoticeBanner
import com.moyeota.core.designsystem.component.PrimaryCtaButton
import com.moyeota.core.designsystem.component.RouteMapView
import com.moyeota.core.designsystem.component.StatusBarSpacer
import com.moyeota.core.designsystem.component.StatusBadge
import com.moyeota.core.designsystem.component.NoticeKind
import com.moyeota.core.designsystem.component.decodePolyline
import com.moyeota.core.designsystem.component.fitMapCamera
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.core.designsystem.theme.MoyeotaType
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import com.moyeota.core.designsystem.component.MapOverlayPill
import com.moyeota.core.designsystem.component.MoyeotaChip
import com.moyeota.core.designsystem.component.MoyeotaTopBar
import com.moyeota.core.designsystem.component.RouteSummary
import com.moyeota.core.designsystem.component.SectionLabel
import com.moyeota.core.designsystem.component.TextLinkButton
import com.moyeota.core.designsystem.theme.MoyeotaShape
import com.moyeota.core.designsystem.theme.MoyeotaSpacing
import com.moyeota.core.designsystem.theme.mapOverlayShadow
import com.moyeota.core.designsystem.theme.tabularNums
import com.moyeota.presentation.core.KoreanParagraphText
import com.naver.maps.geometry.LatLng
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

/**
 * 16 지도·요약 칩·요금 문구에 쓰이는 경로 미리보기 값 (POST /matching/routes 응답).
 *
 * 방을 만들기 **전**이라 서버가 확정한 값이 아니다 — 좌표만으로 뽑은 추정치이며,
 * 실패해도 방 생성은 막지 않는다(서버가 생성 시 어차피 다시 계산한다).
 * 셋 다 null 이고 [loading] 이 false 면 「값 없음」 — 칩·요금 문구를 감춘다.
 */
data class RoutePreviewUi(
    val loading: Boolean = false,
    val estimatedMinutes: Int? = null,
    val estimatedFare: Int? = null, // 미터기 추정 총액(1인 요금 아님)
    val encodedPath: String? = null, // Google Encoded Polyline — 21 대기·22 상세의 지도와 같은 형식
)

/**
 * 16 에서 고른 매칭 조건 — **정원과 출발 시각**.
 *
 * 「출발지 반경」·「도착지 반경」 칩은 지웠다(2026-10-14). 서버가 방 생성 요청에서 반경을 빼고
 * 고정값(500m)을 쓰기로 했다(Backend `a668aee`) — 보내도 버려진다. 그전에도 어떤 조회·판정도
 * 그 값을 읽지 않아(합류에 거리 검사가 없고 목록은 지도 뷰포트만 본다) 사용자는 **아무 영향 없는
 * 값**을 「매칭 조건」이라는 이름으로 고르고 있었다. 「준비 중」 자리표시를 두지 않고 요소째 내렸다.
 *
 * 「동성만」 토글도 없다 — 성별은 매칭에 쓰이지 않는다(백엔드 `matching` 패키지에 성별 필드 0건).
 * 고를 수는 있는데 아무 일도 일어나지 않는 조건은 두지 않는다.
 */
data class MatchConditions(
    val capacity: Int,
    /**
     * 예약 방의 출발 시각(ISO-8601 **순간** 문자열). **null 이면 실시간 방**이다.
     *
     * 이 한 값이 **다른 엔드포인트**를 고른다 — null 이면 `POST /matching/rooms`, 값이 있으면
     * `POST /matching/reservations`([CreatePartyViewModel.createParty]). 불리언 플래그를 따로 두지
     * 않는 이유: 「예약이다」와 「그 시각」이 각각 참·거짓이 될 수 있으면 **시각 없는 예약**이라는
     * 보낼 수 없는 조합이 타입에 생긴다. 시각이 곧 예약 여부다.
     *
     * 문자열로 들고 다니는 것은 이 저장소의 관례다(`Ride.departureAt`·`RideRecord`·`ChatMessage`) —
     * 만드는 자리([reservationDepartureIso])와 읽는 자리에서만 파싱한다.
     */
    val departureAt: String? = null,
)

/**
 * 16 · 도착지 확인 · 매칭 조건 (모달) [신규]
 *
 * 이동(디스크립션):
 * - 닫기·배경 탭 → 15 (onDismiss) — 공통 규칙: 모달은 배경 탭·닫기로만 종료
 * - CTA → 방 생성 성공 시 21 매칭 대기 (onFindCompanions). 고른 조건(인원·출발 시각)을 그대로 넘긴다.
 *   **출발 시각이 CTA 가 부르는 엔드포인트를 가른다** — 「지금 출발」은 `POST /matching/rooms`,
 *   「시간 정하기」는 `POST /matching/reservations`([MatchConditions.departureAt]).
 *
 * 선택 규칙(디스크립션):
 * - **출발 시각: 「지금 출발」(기본) / 「시간 정하기」.** 뒤쪽을 고르면 날짜 띠(오늘~7일 뒤,
 *   [ReservationDateStrip])와 10분 단위 시·분 휠([ReservationTimeWheel])이 열린다 — 휠은 그 날의 범위
 *   ([reservationTimeRange]) 안 값만 담아 고를 수 있는 모든 조합이 **구성상** 서버 창(지금~7일) 안이다.
 *   휠은 늘 값을 보여 주므로 **기본 선택은 그 날의 가장 이른 시각**이고 CTA 는 켜져 있다
 *   ([reservationSelectedTime] — 칩 시절의 「기본 선택 없음」 규칙을 바꾼 근거가 거기 있다)
 * - 인원(나 포함) → 예상 1인 요금 재계산 ([RoutePreviewUi.estimatedFare] ÷ 인원, [farePerPersonWon]).
 *   기본값 3명. **범위가 모드에 따라 다르다** — 실시간 1~3, **예약은 2~3**([capacityChoices]).
 *   1 을 고른 채 「시간 정하기」로 바꾸면 보낼 수 없는 상태가 되므로 전송·표시 값만 2 로 접고
 *   ([capacityFor]) 그 사실을 한 줄로 말한다([RESERVATION_CAPACITY_NOTICE])
 * - **1명을 고르면** 칩 행 아래에 「혼자 출발한다」 안내가 붙는다([soloCapacityNotice]) —
 *   정원 1 은 만든 즉시 꽉 찬 방이라 아무도 합류할 수 없다. 선택지와 전송 값은 그대로다
 * - **예약에서는 경로 요약 칩과 예상 요금을 감춘다**([RESERVATION_NO_ESTIMATE_NOTICE]) — 서버가
 *   예약 방에 요금·경로·소요 시간을 넣지 않아서, 추정치를 그대로 보여 주면 **방에는 없는 숫자**를
 *   보고 동의하게 된다. 지도의 경로선은 남긴다(핀 사이의 길 모양은 예측이 아니다)
 * - 예약에서는 「걸어서 가까운 방」 추천도 감춘다 — 그건 **지금 출발하는** 방 목록이고
 *   (`POST /rooms/realtime`), 시간을 정하러 온 사람이 탭하면 지금 타는 방에 합류하게 된다
 *
 * 레이아웃(디스크립션):
 * - 지도가 헤더 아래를 전부 채우고, 그 위에 **드래그로 접었다 펴는 시트**가 얹힌다
 * - 펼침 = 매칭 조건까지 전부 보임(지도 160dp 노출) / 접힘 = 도착지·요금·CTA 만 보이고 지도가 넓어짐
 * - CTA 는 시트 하단에 고정이라 두 상태 모두에서 보인다
 */
@Composable
fun DestinationConfirmModal(
    destinationName: String,
    destinationAddress: String,
    originStopName: String,
    departureTimeLabel: String = "지금",
    arrivalTimeLabel: String = "도착 계산 중",
    // 이름 자리에는 검색 장소명 대신 **역지오코딩 실주소**가 들어올 수 있다(핀 조정·GPS 출발지).
    // 「부산광역시 금정구 부산대학로63번길 2 부산대학교」처럼 길어지므로 표기는 전부 1줄 말줄임이다 —
    // 시/도 접두 제거 같은 가공은 하지 않는다(서버가 조합해 준 값을 그대로 보여준다).
    // 지도 좌표 — 도착지가 있으면 실지도, 없으면 placeholder (15 를 거치지 않은 진입)
    destinationPosition: LatLng? = null,
    originPosition: LatLng? = null,
    routePreview: RoutePreviewUi = RoutePreviewUi(),
    creating: Boolean = false, // 방 생성 요청 중 — 중복 제출 차단
    errorMessage: String? = null,
    onDismiss: () -> Unit = {},
    // 핀 조정 확정 — (출발지, 도착지) 좌표. 조정하지 않은 쪽은 들어온 값 그대로 돌아온다
    onAdjustPositions: (LatLng?, LatLng?) -> Unit = { _, _ -> },
    onFindCompanions: (MatchConditions) -> Unit = {},
    /**
     * 「걸어서 가까운 방」 추천. **빈 목록이 기본값이자 기본 상태**다 — 서버가 출발지·도착지
     * 각각 300m 안(AND)에서만 고르기 때문에 초기에는 거의 항상 비어 있다. 비면 섹션을 그리지 않는다.
     *
     * 서버가 도보 시간 순으로 정렬해 준 순서 그대로 받는다 — **여기서 다시 정렬하지 않는다.**
     */
    nearbyCandidates: List<MatchCandidate> = emptyList(),
    /** 추천 카드를 누르면 그 방의 합류 확인(19)으로 간다 — 합류 흐름은 이미 있는 걸 그대로 쓴다. */
    onCandidateClick: (MatchCandidate) -> Unit = {},
    /**
     * 추천 **조회가 실패**했다 — 「주변에 방이 없다」와 구별해야 하는 상태다.
     *
     * 둘을 합치면 다음 행동이 정반대인 두 상황이 같게 보인다: 후보 0건이면 방을 만드는 게 맞지만,
     * 조회 실패(OSRM 장애·배포 중 503)면 **300m 안에 방이 있어도** 섹션이 사라져 사용자가 모르고
     * 새 방을 만든다. 합승 매칭이 조용히 꺼지는 자리라 작은 재시도 줄을 둔다 — 차단형 배너가
     * 아닌 이유는 추천이 부가 기능이라는 기존 판단을 유지하기 때문이다.
     */
    nearbyLoadFailed: Boolean = false,
    onRetryNearby: () -> Unit = {},
    /**
     * 처음부터 「시간 정하기」(예약)로 열 것인가. **기본값이 지금까지의 동작**이다 — 모달을 여는
     * 길은 15 목적지 확정 하나뿐이고 그쪽은 아무것도 넘기지 않는다.
     *
     * 파라미터로 둔 이유는 17 탐색의 `initialSheetState` 와 같다: 예약 상태의 미리보기를
     * 만들 수 있어야 한다(내부 상태만으로는 Preview 에서 그 화면을 볼 수 없다).
     *
     * [DepartureMode] 가 아니라 **Boolean** 인 이유는 이 컴포저블이 공개 API 라는 점이다 —
     * 그 enum 은 모듈 안의 판정 타입(internal)이고, 공개 파라미터가 internal 타입을 받으면
     * 컴파일이 막힌다. 공개 자리에는 도메인 타입과 원시값만 둔다(이 저장소의 관례).
     */
    initiallyScheduled: Boolean = false,
) {
    // 기본값: 3인 (디스크립션 유효값 규칙)
    var peopleCount by rememberSaveable { mutableIntStateOf(3) }
    // 기본값: 지금 출발 — **지금까지의 동작이 기본값이다**([DepartureMode])
    var departureMode by rememberSaveable {
        mutableStateOf(if (initiallyScheduled) DepartureMode.SCHEDULED else DepartureMode.NOW)
    }
    // 핀 조정 화면(모달 내 확장 상태). null 이면 닫혀 있다.
    var adjustTarget by remember { mutableStateOf<PinTarget?>(null) }
    val canAdjust = destinationPosition != null || originPosition != null

    // 예약 날짜·시각 계산의 기준 시각. **한 번만 읽는다** — 매 리컴포지션마다 시계를 읽으면
    // 휠의 범위가 1초마다 다시 만들어지고(지난 값이 사라지며) 고른 칸이 손가락 아래에서 움직인다.
    // 이 모달은 몇 분 안에 닫히는 화면이라 그 사이 기준이 낡아도 가장 이른 값이 10분 여유를
    // 갖고 있어([RESERVATION_MIN_LEAD_MINUTES]) 서버 창을 벗어나지 않는다. 그리고 보내는 시각은
    // **휠에 보이는 값 그대로**다(아래 departureAt) — 보내는 순간 시계를 다시 읽어 고치지 않는다.
    // 단, **보내기 직전에는 지금 시계로 다시 잰다**(아래 CTA) — 오래 열어 둬 기본값(가장 이른 시각)이 여유를
    // 잃었으면 이 기준을 지금으로 옮겨 휠을 새 범위로 접는다([reservationTimeStillValid]).
    //
    // 기준은 고른 날짜(`dateOffset`)와 **함께 저장한다**(epoch ms). 일수는 이 기준의 「오늘」로부터 센 값이라, 다시
    // 들어올 때 기준만 새로 읽고 일수를 그대로 두면 그 사이 자정을 넘긴 경우 다른 날을 가리킨다. 기준을 옮기는 길은
    // 아래 `moveClock` 하나다 — 고른 달력 날짜를 지킨다([reservationDateOffsetAfterClockMove]).
    var nowMillis by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    val now = Instant.ofEpochMilli(nowMillis)
    val dateOptions = remember(now) { reservationDates(now) }
    val today = dateOptions.first()
    // 그 날에 고를 시각이 남아 있는가 — 늦은 밤의 「오늘」만 false 다. 띠는 그 칸을 누르지 못하게 그린다.
    val dateSelectable: (LocalDate) -> Boolean = remember(now) { { date -> reservationTimeRange(date, now) != null } }
    // 고른 날짜는 **오늘로부터의 일수**로 들고 있는다 — LocalDate 는 Parcelable 이 아니라
    // rememberSaveable 에 그대로 담을 수 없고, 일수는 Int 라 구성 변경에도 살아남는다.
    var dateOffset by rememberSaveable { mutableIntStateOf(0) }
    // 기본값(오늘)이 고를 수 없는 날이면 **고를 수 있는 첫 날**로 본다 — 누를 수 없는 칸이 선택된 채
    // 열리면 휠 자리에 「고를 시간이 없어요」만 남는다([reservationSelectableDateIndex]).
    val selectedDate = dateOptions[reservationSelectableDateIndex(dateOffset, dateOptions, dateSelectable)]
    val timeRange = remember(selectedDate, now) { reservationTimeRange(selectedDate, now) }
    // 기준을 [fresh] 로 옮긴다 — 일수를 새 띠에서 **보던 날짜의 자리**로 다시 셈한다. 일수만 두면 자정을 넘긴 기준에서
    // 「내일」이 「모레」가 된다(리뷰 반영, PR #99). 보던 날이 범위를 벗어났으면 고를 수 있는 첫 날이다.
    // 돌려주는 값은 옮긴 뒤 띠에 **칠해질** 날짜다(위 `selectedDate` 와 같은 접기) — CTA 의 안내가 날짜도 바뀌었는지 가른다.
    fun moveClock(fresh: Instant): LocalDate {
        val freshDates = reservationDates(fresh)
        val freshSelectable: (LocalDate) -> Boolean = { date -> reservationTimeRange(date, fresh) != null }
        dateOffset = reservationDateOffsetAfterClockMove(selectedDate, freshDates, freshSelectable)
        nowMillis = fresh.toEpochMilli()
        return freshDates[reservationSelectableDateIndex(dateOffset, freshDates, freshSelectable)]
    }
    // 들어올 때마다(처음 · 위에 쌓인 화면에서 돌아옴 · 구성 변경) 기준을 지금으로 다시 읽는다 — 덮인 동안의 낡은 기준이
    // 남으면 이미 지난 시각을 고를 수 있다. 예전에는 기준이 `remember` 라 저절로 다시 읽혔고, 그때 일수는 그대로 남아
    // 자정을 넘기면 날짜가 밀렸다.
    LaunchedEffect(Unit) { moveClock(Instant.now()) }
    // 휠로 고른 시각(하루의 분). -1 = 아직 휠을 건드리지 않았다 → **그 날의 가장 이른 시각이 기본 선택**이다.
    //
    // 칩 시절에는 기본 선택이 없었다(아무도 원하지 않은 00:00 예약이 한 번의 탭으로 만들어진다는 이유).
    // 휠은 늘 값을 보여 주므로 「안 골랐다」를 그릴 수 없고, 그래서 규칙을 바꿨다 — 근거와 대신 세운
    // 방어(크게 보이는 휠 값 + CTA 위 되읽기 줄)는 [reservationSelectedTime] KDoc 에 있다.
    var selectedMinuteOfDay by rememberSaveable { mutableIntStateOf(-1) }
    // CTA 직전 다시 재기가 실패해 휠을 옮겼다는 안내([reservationClockMovedNotice]). null 이면 없다.
    // 휠·날짜 띠를 만지거나 CTA 를 다시 누르면 걷는다 — 사용자가 바뀐 값을 보고 손을 댔다는 뜻이다.
    var clockMovedNotice by rememberSaveable { mutableStateOf<String?>(null) }
    // 날짜를 바꾸면 **보여 줄 값만** 그 날의 범위로 접는다(고른 값은 남는다 — 정원의 [capacityFor] 와
    // 같은 처방). 이 값이 휠에 보이는 값이자 서버로 가는 값이다.
    val selectedTime = reservationSelectedTime(
        stored = selectedMinuteOfDay.takeIf { it >= 0 }?.let { LocalTime.of(it / 60, it % 60) },
        range = timeRange,
    )

    val scheduled = departureMode == DepartureMode.SCHEDULED
    // 보낼 수 있는 정원. 모드가 바뀌어도 [peopleCount] 는 그대로 둔다 — 되돌리면 고른 값이 살아 있다
    val capacity = capacityFor(departureMode, peopleCount)
    // 서버로 가는 출발 시각 = **휠에 보이는 그 값**(selectedTime)과 띠에 칠해진 그 날(selectedDate).
    // 누르는 순간 시계를 다시 읽거나 값을 다시 접지 않는다 — 화면과 보낸 값이 1분이라도 어긋나면 표시된
    // 약속 시간과 실제 약속 시간이 다른 방이 만들어지고, 예약 방은 시간을 바꿀 수 없다.
    // **「지금 출발」이면 싣지 않는다** — 휠은 늘 값이 있어 실으면 실시간 방이 예약 방이 된다([matchDepartureAt]).
    val departureAt = matchDepartureAt(scheduled, selectedDate, selectedTime)

    // 1인 요금 = 미터기 추정 총액 ÷ 인원, 10원 단위 반올림 ([farePerPersonWon] — 20 합류·22 상세가 쓰는
    // 서버 값 계산식과 **같은 규칙**이다). 추정이 아직 없거나 실패했으면 null — 문구를 감추되 CTA 는 살려둔다.
    // **예약이면 아예 셈하지 않는다**([RESERVATION_NO_ESTIMATE_NOTICE] — 서버가 그 방에 요금을 넣지 않는다).
    val farePerPersonText = routePreview.estimatedFare?.takeIf { !scheduled }?.let { total ->
        "%,d".format(farePerPersonWon(total, capacity))
    }
    // 요약 칩 — 거리는 서버 응답에 없어 시간·총요금으로 대체한다(와이어프레임의 2값 구조 유지)
    val routeSummaryLabel = when {
        // 예약 방에는 소요 시간도 요금도 없다 — 「지금」 기준 추정을 미래 출발에 붙이지 않는다
        scheduled -> null
        routePreview.loading -> "예상 경로 계산 중"
        routePreview.estimatedMinutes != null && routePreview.estimatedFare != null ->
            "예상 ${routePreview.estimatedMinutes}분 · 총 ${"%,d".format(routePreview.estimatedFare)}원"
        else -> null // 실패 — 칩 자체를 감춘다
    }
    // 폴리라인 디코딩은 21 대기·22 상세의 지도와 같은 decodePolyline 을 쓴다.
    // **예약에서도 그린다** — 두 핀 사이의 길 모양은 시각·금액 예측이 아니라 기하다
    // ([RESERVATION_NO_ESTIMATE_NOTICE] 의 마지막 단락).
    val routePath = remember(routePreview.encodedPath) {
        routePreview.encodedPath?.let(::decodePolyline).orEmpty()
    }

    // 경로 카드의 시각 두 칸.
    // - 예약: 출발 칸에 **고른 시각**을 적고(아직 안 골랐으면 비운다) 도착 칸은 **비운다** —
    //   서버가 예약 방에 소요 시간을 주지 않아 도착 시각을 셈할 근거가 없다. 「지금」 기준 추정으로
    //   채우면 3일 뒤 도착 시각을 오늘 교통으로 단정하는 말이 된다.
    // - 지금 출발: 호출부가 넘긴 값 그대로(기존 동작).
    val departureText = if (scheduled) selectedTime?.let(::reservationTimeLabel).orEmpty() else departureTimeLabel
    val arrivalText = if (scheduled) "" else arrivalTimeLabel

    Box(modifier = Modifier.fillMaxSize()) {
        // Scrim 배경 — 탭 시 닫기
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MoyeotaColor.Scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onDismiss() },
        )

        // 모달 카드 (풀스크린 라우트 겸용) — 내부 탭은 소비
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {},
        ) {
            // 상단 헤더 — 다른 하위 화면과 같은 가운데 제목 앱바(디자인 QA G7·4-6). 상태바 자리도 흰색으로
            // 칠한다: 다이얼로그 창이 시스템 바 아래까지 그리므로(MainNavGraph 의 S16-1) 인셋이 실제 값이다.
            Column(modifier = Modifier.fillMaxWidth().background(MoyeotaColor.SurfaceCanvas)) {
                StatusBarSpacer()
                // 오른쪽 방패 아이콘은 지웠다(2026-11, 오너 UX 피드백) — 누를 수도 없고 가리키는
                // 기능도 없는 장식이었다. 안전 기능(원터치 신고)은 2차 범위라 이 화면에 그 약속을
                // 그려 둘 근거가 없다(DESIGN-moyeota.md: 방패는 비상 신고 진입점에만 쓴다).
                // 그 자리에 빈칸을 남기지 않는다 — 가운데 제목이라 오른쪽이 비어도 균형이 깨지지 않는다.
                MoyeotaTopBar(title = "도착지 확인", onBack = onDismiss)
            }

            // 지도(헤더 아래 전부) 위에 드래그 시트를 얹는다 — 시트 메커니즘은 21 과 공유한다
            // 접힘 = 핸들 + 도착지 배지 + 요금 줄 + CTA (기본값과 같은 구성) + 내비게이션 바 인셋.
            // 인셋을 더하는 이유: 다이얼로그 창이 시스템 바 아래까지 그린 뒤로(S16-1) CTA 아래 NavigationBarSpacer
            // 가 실제 높이를 갖는다 — 추정치에 넣지 않으면 그만큼 펼침 상태의 지도 노출이 줄어든다.
            val navigationBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            MapSheetScaffold(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                collapsedSheetHeight = 176.dp + navigationBarHeight,
                background = { sheet ->
                    val widthDp = sheet.containerWidth.value
                    // fit 기준은 **시트에 가리지 않고 실제로 보이는 지도 높이**다.
                    // 앵커가 바뀔 때만 값이 변하므로 드래그·정착 중 카메라가 매 프레임 튀지 않는다.
                    val visibleMapHeightDp = sheet.visibleMapHeight.value
                    val fitted = remember(routePath, originPosition, destinationPosition, widthDp, visibleMapHeightDp) {
                        fitMapCamera(
                            points = routePath + listOfNotNull(originPosition, destinationPosition),
                            widthDp = widthDp,
                            heightDp = visibleMapHeightDp,
                        )
                    }
                    // 가려지는 영역을 지도에 알려 카메라 중심이 보이는 영역 기준으로 잡히게 한다
                    val mapContentPadding = remember(sheet.settledSheetHeight) {
                        PaddingValues(bottom = sheet.settledSheetHeight)
                    }
                    // 「위치 조정」 버튼은 **실측** 시트 높이를 써서 시트 바로 위에 붙인다 —
                    // 앵커 추정치를 쓰면 에러 배너가 떠 시트가 커졌을 때 버튼이 가린다.
                    val sheetHeight = sheet.sheetHeight

                    if (destinationPosition != null) {
                        RouteMapView(
                            modifier = Modifier.fillMaxSize(),
                            routePath = routePath, // previewRoute 의 encodedPath — 2점 미만이면 그려지지 않는다
                            originPosition = originPosition,
                            destinationPosition = destinationPosition,
                            center = fitted?.first ?: destinationPosition,
                            // 경로가 없을 땐 도착지 주변 블록이 식별될 정도로 당겨 본다
                            zoom = fitted?.second ?: 15.0,
                            contentPadding = mapContentPadding,
                            // 다이얼로그 목적지라 SurfaceView 대신 TextureView 로 그린다
                            useTextureView = true,
                        )
                    } else {
                        MapPlaceholder(modifier = Modifier.fillMaxSize())
                    }

                    // 지도를 덮는 투명 탭 레이어는 두지 않는다 — 그 레이어가 터치를 전부 먹어
                    // SDK 줌 컨트롤(+/-)·핀치·팬이 통째로 죽어 있었다. 핀 조정 진입은
                    // 아래 「위치 조정」 버튼 하나로 충분하다.

                    // 경로 미리보기 실패 시엔 칩을 통째로 감춘다 — 틀린 값보다 없는 편이 낫다
                    if (routeSummaryLabel != null) {
                        MapOverlayPill(modifier = Modifier.padding(MoyeotaSpacing.ScreenGutter)) {
                            Text(
                                text = routeSummaryLabel,
                                style = MoyeotaType.BodySm.tabularNums(),
                                fontWeight = FontWeight.SemiBold,
                                color = MoyeotaColor.InkPrimary,
                            )
                        }
                    }

                    // 지도 조정 진입점. 오른쪽 아래는 SDK 줌 컨트롤(+/-) 자리라 왼쪽에 둔다.
                    // 보이는 알약은 32dp 지만 **터치 영역은 48dp** 다(디자인 QA G12) — 바깥 상자가 48dp 를
                    // 잡아 두고 그 가운데에 알약을 그린다. 글자는 파랑이 아니라 검정이다: 이 화면의 파랑은
                    // CTA 하나에만 둔다(예전엔 한 시트에 파랑이 여섯 번 나왔다 — S16-2).
                    if (canAdjust) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                // 바깥 48dp 상자의 위아래 8dp 만큼 덜 띄워 알약과 시트 사이를 12dp 로 맞춘다
                                .padding(start = MoyeotaSpacing.ScreenGutter, bottom = sheetHeight + 4.dp)
                                .minimumInteractiveComponentSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .height(32.dp)
                                    .mapOverlayShadow(MoyeotaShape.Pill)
                                    .clip(MoyeotaShape.Pill)
                                    .background(MoyeotaColor.SurfaceCanvas)
                                    .clickable(role = Role.Button) { adjustTarget = PinTarget.DESTINATION }
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "위치 조정",
                                    style = MoyeotaType.ButtonSm,
                                    color = MoyeotaColor.InkPrimary,
                                )
                            }
                        }
                    }
                },
                sheetTop = {
                    // 도착지 배지 — 접힌 상태에서도 보이는 최소 정보.
                    // 배지는 **회색**이다(디자인 QA S16-5): 초록은 이 앱에서 「매칭됨·확정」을 뜻해서, 아직 아무것도
                    // 확정되지 않은 화면에 초록 배지를 달면 없는 상태를 말하게 된다. 「도착지」는 상태가 아니라 이름표다.
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = MoyeotaSpacing.ScreenGutter),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusBadge(kind = NoticeKind.NEUTRAL, text = "도착지")
                        Spacer(Modifier.width(8.dp))
                        Text(
                            // 이름이 이미 실주소면 호출부가 주소 칸을 비워 보낸다 — 구분자만 남지 않게 합친다
                            text = listOf(destinationName, destinationAddress)
                                .filter { it.isNotBlank() }
                                .joinToString(" · "),
                            style = MoyeotaType.BodySm,
                            color = MoyeotaColor.TextMute,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                },
                sheetDetail = {
                    // 상세 영역은 시트가 접히면 높이 0 이 된다 — 좌우 여백은 여기서 준다
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = MoyeotaSpacing.ScreenGutter)) {
                        // 「도착지」 배지 줄(sheetTop)과 아래 경로 요약 사이의 숨 쉴 자리 — 예전에는 둘이
                        // 맞닿아 배지 줄이 카드의 머리처럼 보였다(오너 UX 피드백). 12dp 는 4/8 리듬의
                        // `spacing.sm` 이다. **상세 영역 안에** 두는 이유: 시트를 접으면 상세가 통째로
                        // 0 이 되므로 이 간격도 함께 사라져 접힘 높이(176dp 추정치)가 흔들리지 않는다.
                        Spacer(Modifier.height(12.dp))
                        // 경로 요약 — 판(배경) 없이 흰 시트 위에 바로(디자인 QA S16-2 · G9). 20·22 와 같은 조각이다.
                        // 이름은 굵게, 시각은 보통 굵기 + 고정폭 숫자로 — 예전엔 네 줄이 모두 15sp Bold 라
                        // 무엇이 중요한지 읽히지 않았다. 출발지·도착지 각각에 「조정」을 둔다(무엇을 조정하는지
                        // 애매하지 않게 — 오너 결정으로 지도의 「위치 조정」까지 진입점 셋을 유지한다).
                        // 좌표가 없는 쪽은 조정할 핀이 없어 「조정」을 그리지 않는다(예전과 같은 조건).
                        // 출발지 → 도착지 연결선만 그린다. 도보 거리·시간은 서버가 주지 않아 표기하지 않는다.
                        RouteSummary(
                            origin = originStopName,
                            destination = destinationName,
                            originMeta = departureText,
                            destinationMeta = arrivalText,
                            onAdjustOrigin = if (originPosition != null) {
                                { adjustTarget = PinTarget.ORIGIN }
                            } else {
                                null
                            },
                            onAdjustDestination = if (destinationPosition != null) {
                                { adjustTarget = PinTarget.DESTINATION }
                            } else {
                                null
                            },
                        )

                        Spacer(Modifier.height(MoyeotaSpacing.SectionGap))

                        SectionLabel(text = "매칭 조건")

                        // 매칭 조건 — 시트 안에 그림자 카드를 또 두지 않는다(디자인 QA S16-4 · G4). 줄은 흰 시트
                        // 위에 바로 놓이고, 「출발 시각」 묶음과 「인원」 줄 사이만 Hairline 으로 나눈다.
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // 「출발 시각」이 **맨 위**에 있는 이유: 이 선택이 아래 모든 것을 바꾼다
                            // (정원 범위·요금 표시·CTA 가 부르는 엔드포인트). 아래에 두면 사용자가
                            // 정원을 고른 뒤에 선택지가 줄어드는 것을 보게 된다.
                            ConditionRow(label = "출발 시각") {
                                DepartureMode.entries.forEach { mode ->
                                    MoyeotaChip(
                                        text = departureModeLabel(mode),
                                        selected = departureMode == mode,
                                        onClick = { departureMode = mode },
                                    )
                                }
                            }

                            if (scheduled) {
                                // 날짜는 **주간 달력 띠**, 시각은 **시·분 휠**이다(2026-11, 오너 UX 피드백) —
                                // 예전 칩(「오늘·내일·11월 14일 (토)」 8개 + 30분 시각 칩 최대 48개)은 가로로
                                // 길게 밀어야 해서 요일을 한눈에 볼 수 없었고, 30분 단위는 약속에 거칠었다.
                                // 두 조각은 17 「날짜로 찾기」와 **같은 컴포넌트**다([ReservationDateStrip]).
                                // 띠는 16dp 화면 여백 안에 놓여 첫 칸이 「매칭 조건」 머리말과 같은 선에서 시작한다
                                // (디자인 QA 4-3 — 예전엔 카드 안쪽 20dp 가 더해져 36dp 였다)
                                PickerSection(label = "날짜", trailing = reservationMonthLabel(dateOptions)) {
                                    ReservationDateStrip(
                                        dates = dateOptions,
                                        selected = selectedDate,
                                        onSelect = { date ->
                                            dateOffset = dateOptions.indexOf(date).coerceAtLeast(0)
                                            clockMovedNotice = null
                                        },
                                        isSelectable = dateSelectable,
                                    )
                                }
                                val range = timeRange
                                if (range == null || selectedTime == null) {
                                    // 고를 수 있는 날이 하나도 없을 때만 닿는다 — 늦은 밤의 「오늘」은 띠가 누를 수
                                    // 없게 그리고 기본 선택도 다음 날로 옮긴다([reservationSelectableDateIndex]).
                                    // 그래도 이 길을 남겨 두는 이유: 빈 휠보다 사실을 말하는 한 줄이 낫다.
                                    ConditionNotice(RESERVATION_NO_SLOT_NOTICE)
                                } else {
                                    PickerSection(label = "시각") {
                                        ReservationTimeWheel(
                                            range = range,
                                            selected = selectedTime,
                                            onSelect = { time ->
                                                selectedMinuteOfDay = time.hour * 60 + time.minute
                                                clockMovedNotice = null
                                            },
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = MoyeotaColor.Hairline)

                            // 라벨에 「나 포함」을 박았다 — 서버 정원(`Capacity`)은 **방장을 포함한 총
                            // 인원**이라(`Party.open` 이 생성자를 멤버로 넣고 `members.size == capacity`
                            // 면 꽉 찬 것으로 본다) 「3인」만 쓰면 「나 외 3명」으로 읽힌다. 「나 포함 N명」을
                            // 칩마다 쓰지 않고 라벨로 올린 건 레이아웃 때문이다 — 칩 3개가 좁은 폰(360dp)에서
                            // 카드 밖으로 밀려 잘린 전례가 있다([ConditionRow] 주석).
                            //
                            // 선택지가 **모드에 따라 다르다**([capacityChoices]) — 예약에서 1 칩은
                            // 보내도 400 이라 지운다. 선택 표시는 전송값([capacity])을 보므로 1 을
                            // 고른 채 예약으로 넘어오면 2 가 선택된 것으로 보인다(그리고 아래 한 줄이
                            // 그 이유를 말한다).
                            ConditionRow(label = "인원(나 포함)") {
                                capacityChoices(departureMode).forEach { count ->
                                    MoyeotaChip(
                                        text = "${count}명",
                                        selected = capacity == count,
                                        onClick = { peopleCount = count },
                                    )
                                }
                            }
                            // 예약에서 1 칩이 사라진 이유를 **고르는 자리에서** 말한다. 실시간의
                            // 「혼자 출발한다」 안내와 같은 자리·같은 모양이고, 두 문구는 함께 뜨지
                            // 않는다(예약에서는 capacity 가 1 이 될 수 없다).
                            if (scheduled) {
                                ConditionNotice(RESERVATION_CAPACITY_NOTICE)
                            }
                            // 「1명」을 고른 사람에게만 **고르는 자리에서** 결과를 말해 준다
                            // ([soloCapacityNotice]). 1 은 지우지 않았다 — 혼자 타기를 상품으로
                            // 인정한 결정이다. 다만 그 선택의 결과(정원 1 = 만든 즉시 꽉 찬 방 =
                            // 아무도 합류 못 함)는 서버 규약을 아는 사람만 읽어 낼 수 있어서,
                            // 안내가 없으면 「1명」은 그냥 「나 외 1명」처럼 보인다.
                            //
                            // 자리를 늘 비워 두지 **않고** 1명일 때만 줄을 넣은 근거:
                            // 이 카드는 [MapSheetScaffold] 의 sheetDetail 안에 있고 그 영역은
                            // **앵커가 정한 고정 높이 + 세로 스크롤**이다(콘텐츠가 높이를 정하지
                            // 않는다). 그래서 이 줄이 생겨도 시트 높이·지도 노출·CTA 위치는 **한
                            // 픽셀도 움직이지 않고**, 아래의 「걸어서 가까운 방」 섹션만 그만큼
                            // 밀린다 — 손가락이 닿아 있는 칩 행은 안내보다 **위**라 제자리다.
                            // 상시 빈 자리를 두면 2·3명(기본값 3)에서 늘 비어 있는 ~30dp 가
                            // 좁은 폰의 스크롤 영역에서 추천 카드 한 줄만큼을 먹는다.
                            //
                            // 색·크기는 이 파일의 보조 문구 관습([ConditionNotice] — BodySm + TextMute)이다.
                            // 빨강·경고 아이콘을 쓰지 않는다 — 정상 선택이고, 오류로 읽히면
                            // 사용자는 고를 수 있는 값을 「막힌 값」으로 본다.
                            // 줄바꿈을 막지 않는다(maxLines 없음) — 큰 글꼴 설정에서 잘린 안내보다
                            // 두 줄이 낫고, 스크롤 영역 안이라 잘릴 일도 없다.
                            soloCapacityNotice(capacity)?.let { notice -> ConditionNotice(notice) }
                            // 「출발지 반경」·「도착지 반경」 행은 지웠다(2026-10-14) — 서버가 방 생성
                            // 요청에서 반경을 빼고 고정값(500m)을 쓴다(Backend `a668aee`). 고른 값이
                            // 전송조차 되지 않고, 전송됐을 때도 매칭이 읽지 않던 값이다([MatchConditions]).
                            //
                            // 「매칭 방식 · 주요 승차지점」 행은 지웠다(2026-10-13) — 그런 매칭 방식이
                            // 없다. 서버에 승차지점 개념이 없고(방은 사용자가 찍은 좌표 그대로 만들어진다)
                            // 칩도 항상 선택된 채 무동작이었다. 고를 것도, 가리킬 기능도 없는 줄이었다.
                            //
                            // 카드는 걷어냈다(디자인 QA S16-4) — 「출발 시각」·「인원」 두 줄이 시트 위에 바로 놓이고
                            // 둘 사이만 Hairline 으로 나눈다.
                        }

                        // 「걸어서 가까운 방」 — 후보가 있을 때만 그린다. 없는 게 기본 상태라
                        // (서버가 출발지·도착지 **각각** 300m 안에서만 고른다) 「없음」을 강조하면
                        // 방을 만들러 온 사람에게 실패처럼 읽힌다.
                        //
                        // **예약에서는 통째로 감춘다**(실패 줄까지). 이 추천은 `POST /rooms/realtime`
                        // 의 결과 = **지금 출발하는** 방이고, 탭하면 그 방의 합류 확인(19)으로 간다.
                        // 3일 뒤 출발을 정하러 온 사람에게 그 카드는 「지금 타는 방」이라는 사실이
                        // 보이지 않아, 시간을 정했다고 생각하며 지금 출발하는 방에 들어가게 된다.
                        // 조회 자체는 막지 않는다 — 모드를 되돌리면 그 결과가 그대로 보인다.
                        if (!scheduled) {
                            if (nearbyCandidates.isNotEmpty()) {
                                Spacer(Modifier.height(16.dp))
                                NearbyCandidateSection(
                                    candidates = nearbyCandidates,
                                    onCandidateClick = onCandidateClick,
                                )
                            } else if (nearbyLoadFailed) {
                                // 후보 0건과 달리 **못 물어봤다**는 뜻이라 사실을 말하고 길을 준다.
                                Spacer(Modifier.height(16.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "가까운 방을 불러오지 못했어요",
                                        style = MoyeotaType.BodySm,
                                        color = MoyeotaColor.TextMute,
                                    )
                                    // 글자만 보이지만 48dp 로 눌린다(G12) — 예전엔 12sp 글자 크기 그대로였다
                                    TextLinkButton(text = "다시 시도", onClick = onRetryNearby)
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                    }
                },
                sheetFooter = {
                    // 요금 안내(예약이면 **고른 시각**) + CTA — 접힘·펼침 모두에서 보이도록 시트 하단에
                    // 고정한다. 추정 실패 시에도 CTA 는 막지 않는다 — 방 생성 요청을 받으면 서버가
                    // 요금을 다시 계산한다.
                    //
                    // 예약은 이 자리가 **두 줄**이 된다: 고른 시각을 되읽어 주는 줄
                    // ([reservationDepartureSummary] — 날짜 띠와 시각 휠이 떨어져 있어 둘이 합쳐져
                    // 무엇이 되는지 확인할 마지막 기회다. 휠에 **기본값이 생긴 뒤로는** 손대지 않은 값이
                    // 무엇인지 알려 주는 자리이기도 하다 — 그래서 `BodyStrong` 으로 눈에 띄게 둔다)과
                    // 요금·소요 시간이 **없다는 사실**
                    // ([RESERVATION_NO_ESTIMATE_NOTICE]). 접힘 높이가 한 줄만큼 커지지만 시트 높이는
                    // 콘텐츠가 정하므로([MapSheetScaffold]) 잘리는 것은 없다.
                    if (scheduled) {
                        Text(
                            text = selectedTime
                                ?.let { "${reservationDepartureSummary(selectedDate, it, today)} 출발" }
                                ?: RESERVATION_NEEDS_TIME_NOTICE,
                            // body-strong(15/600) — 디자인 QA 4-4 의 되읽기 줄
                            style = MoyeotaType.BodyStrong,
                            color = MoyeotaColor.InkPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(horizontal = MoyeotaSpacing.ScreenGutter),
                        )
                        Spacer(Modifier.height(2.dp))
                    }
                    if (scheduled) {
                        // 예약 방에는 요금이 없다 — 「—」로 비워 두면 로딩 실패로 읽히므로
                        // 왜 없는지 적는다(없는 숫자를 지어내지 않는 것이 1급 규칙이다).
                        Text(
                            text = RESERVATION_NO_ESTIMATE_NOTICE,
                            style = MoyeotaType.CaptionMd,
                            color = MoyeotaColor.TextMute,
                            // 접힘 높이가 문구 길이에 따라 흔들리지 않게 한 줄로 고정한다
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(horizontal = MoyeotaSpacing.ScreenGutter),
                        )
                    } else {
                        // 1인 예상 요금 — **이 화면의 주인공 숫자**다(디자인 QA S16-3). 예전엔 가운데 정렬
                        // 12sp 회색(흰 바탕 대비 2.6:1) 한 줄이라 CTA 바로 위에서도 읽히지 않았다. 이름표는 왼쪽
                        // 작은 회색, 금액은 오른쪽 HeadingMd 검정으로 나눠 CTA 위 12dp 에 둔다.
                        // 뒤에 붙던 설명 문구(「인원이 확정되면…」)는 뺐다 — 금액만으로 충분하다.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = MoyeotaSpacing.ScreenGutter)
                                // 이름표와 금액을 한 문장으로 읽힌다(「1인 예상 요금, 3,200원」)
                                .semantics(mergeDescendants = true) {},
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "1인 예상 요금",
                                style = MoyeotaType.BodySm,
                                color = MoyeotaColor.TextMute,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = when {
                                    routePreview.loading -> "계산 중"
                                    farePerPersonText != null -> "${farePerPersonText}원"
                                    else -> "—"
                                },
                                style = MoyeotaType.HeadingMd.tabularNums(),
                                color = MoyeotaColor.InkPrimary,
                                maxLines = 1,
                            )
                        }
                    }
                    // ★ **나갈 수 없다**는 사실은 CTA 앞에 둔다([reservationCommitmentNotice]) —
                    // 실시간 방은 「그만 찾기」로 빠져나올 수 있어서 사용자는 방에 들어가는 일을
                    // 되돌릴 수 있는 선택으로 배워 왔고, 예약은 **같은 모양의 버튼으로 전혀 다른
                    // 약속**을 하게 만든다. 요금 문구와 달리 두 줄을 허용한다(잘리면 뜻이 반쪽이 된다).
                    //
                    // **가운데 정렬 + 어절 단위 줄바꿈**(2026-11, 오너 UX 피드백): 예전에는 왼쪽 정렬에
                    // 음절 단위로 끊겨 마지막 「요」 한 글자가 혼자 둘째 줄을 차지했다 — 버튼 바로 위라
                    // 오류처럼 보였다. 문구는 한 글자도 바꾸지 않는다([KoreanParagraphText] 가 줄바꿈만 바꾼다).
                    // 모양은 `BodySm` · `TextMute`, 최대 두 줄, CTA 위 8dp(디자인 QA 4-5) — 경고가 아니라
                    // 알아 둘 사실이라 조용한 보조 글이다.
                    if (scheduled) {
                        Spacer(Modifier.height(8.dp))
                        KoreanParagraphText(
                            text = reservationCommitmentNotice(joining = false),
                            style = MoyeotaType.BodySm.copy(color = MoyeotaColor.TextMute),
                            maxLines = 2,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = MoyeotaSpacing.ScreenGutter),
                        )
                    }
                    // CTA 위 안내 자리 하나를 둘이 나눠 쓴다. 다시 재기 안내가 먼저다 — 방금 누른 것에 대한 답이고,
                    // 서버 오류는 그 앞의 시도에 대한 말이다. 다시 재기 안내는 예약 휠 이야기라 예약일 때만 보인다.
                    val clockNotice = clockMovedNotice.takeIf { scheduled }
                    if (clockNotice != null) {
                        Spacer(Modifier.height(12.dp))
                        NoticeBanner(
                            kind = RESERVATION_CLOCK_MOVED_NOTICE_KIND,
                            text = clockNotice,
                            modifier = Modifier.padding(horizontal = MoyeotaSpacing.ScreenGutter),
                        )
                    } else if (errorMessage != null) {
                        Spacer(Modifier.height(12.dp))
                        NoticeBanner(
                            kind = NoticeKind.ERROR,
                            text = errorMessage,
                            modifier = Modifier.padding(horizontal = MoyeotaSpacing.ScreenGutter),
                        )
                    }
                    val bannerShown = clockNotice != null || errorMessage != null
                    // 예약 안내가 CTA 바로 위에 올 때는 8dp 로 붙인다 — 그 안내는 이 버튼에 대한 말이다
                    Spacer(Modifier.height(if (scheduled && !bannerShown) 8.dp else 12.dp))
                    PrimaryCtaButton(
                        // 예약은 **무엇이 만들어지는지** 적는다 — 「같이 탈 사람 찾기」는 지금
                        // 모집을 시작하는 말이라, 시간을 정해 둔 방에는 맞지 않는다.
                        text = if (scheduled) "이 시간으로 예약 방 만들기" else "같이 탈 사람 찾기",
                        onClick = {
                            // 휠은 기본값(그 날의 가장 이른 시각)이 늘 있어 한 번도 안 건드려도 보낼 수 있다. 모달을
                            // 연 채 오래 머물면 그 값이 이미 지났거나 여유가 없을 수 있다 — 그대로 보내면 서버가 400
                            // `INVALID_DEPARTURE_TIME` 으로 막고, 안내는 「7일 안이어야 해요」라 원인과 어긋난다.
                            // 그래서 **지금 시계로 다시 재고, 범위를 벗어났으면 보내지 않고 기준만 옮긴다** — 휠이
                            // 새 범위로 접혀 보이는 값이 바뀌고, 사용자는 바뀐 시각을 보고 다시 누른다
                            // (보이는 값 = 보내는 값 원칙은 그대로다).
                            // 기준을 옮길 때 고른 날짜는 지킨다(`moveClock`) — 자정을 넘겨도 「내일」이 「모레」가 되지 않는다.
                            // 옮겼으면 CTA 위에 **왜 안 보냈는지** 한 줄 남긴다(리뷰 반영 2차) — 휠 숫자만 슬쩍 바뀌면
                            // 「안 눌렸다」로 읽혀 바뀐 값을 보지 않고 다시 누른다. 다시 누르면 먼저 걷는다.
                            clockMovedNotice = null
                            val fresh = Instant.now()
                            if (scheduled && !reservationTimeStillValid(selectedDate, selectedTime, fresh)) {
                                val shownDate = moveClock(fresh)
                                clockMovedNotice = reservationClockMovedNotice(dateChanged = shownDate != selectedDate)
                            } else {
                                onFindCompanions(MatchConditions(capacity = capacity, departureAt = departureAt))
                            }
                        },
                        // 시각이 없는 예약은 보낼 수 없다 — 휠이 늘 값을 주므로 고른 날에 남은 시각이 없을
                        // 때만 꺼진다. 그때 비활성 버튼 위에 **왜 못 누르는지**를 적어 둔다
                        // ([RESERVATION_NEEDS_TIME_NOTICE]). 비활성 버튼만 두면 이유를 모른다.
                        enabled = !scheduled || departureAt != null,
                        loading = creating,
                        modifier = Modifier.padding(horizontal = MoyeotaSpacing.ScreenGutter),
                    )
                    // CTA 아래 16dp + 내비게이션 바 인셋. 다이얼로그 창이 시스템 바 아래까지 그리므로(S16-1)
                    // 인셋이 실제 값이고, 시트의 흰 바탕이 제스처 바 뒤까지 이어진다.
                    Spacer(Modifier.height(16.dp))
                    NavigationBarSpacer()
                },
            )
        }

        // 핀 조정 — 같은 다이얼로그 윈도우의 최상단 레이어로 덮는다(신규 목적지 아님)
        adjustTarget?.let { target ->
            PinAdjustOverlay(
                originName = originStopName,
                destinationName = destinationName,
                originPosition = originPosition,
                destinationPosition = destinationPosition,
                initialTarget = target,
                onCancel = { adjustTarget = null },
                onConfirm = { adjustedOrigin, adjustedDestination ->
                    adjustTarget = null
                    onAdjustPositions(adjustedOrigin, adjustedDestination)
                },
            )
        }
    }
}

/**
 * CTA 직전 다시 재기 안내([reservationClockMovedNotice])의 배너 종류 — **회색 안내(INFO)**.
 *
 * 오류가 아니다: 아무것도 실패하지 않았고, 앱이 시간이 지난 값을 고쳐 두고 확인을 부탁할 뿐이다. 빨강(ERROR)은
 * 서버가 거절한 일에 쓰는 색이라 여기 쓰면 「방을 못 만들었다」로 읽힌다. 호박(WAITING)은 「기다리는 중」이라는 뜻을
 * 싣는 색이라(매칭 대기) 맞지 않는다 — 뜻이 없는 회색 안내가 이 말의 무게다([NoticeKind] KDoc).
 */
internal val RESERVATION_CLOCK_MOVED_NOTICE_KIND = NoticeKind.INFO

/**
 * 「걸어서 가까운 방」 추천 목록. **후보가 있을 때만** 불린다(빈 목록은 호출부가 거른다).
 *
 * 기존 17 탐색 카드보다 **정보가 적다** — 서버 응답에 인원·요금이 없어서다. 그래서 숫자를
 * 지어내 채우지 않고, 가진 것(출발·도착·도보 시간·정원)만 보여 준다. 인원 현황은 탭해서 들어간
 * 합류 확인(19)이 방 상세를 읽어 채운다.
 */
@Composable
private fun NearbyCandidateSection(
    candidates: List<MatchCandidate>,
    onCandidateClick: (MatchCandidate) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionLabel(text = "걸어서 가까운 방")
        Text(
            // 「N분 소요」라고 쓰면 목적지까지 N분으로 읽힌다 — 걷는 시간이라는 걸 못 박는다.
            text = "타러 가고, 내려서 걷는 시간을 더한 값이에요",
            style = MoyeotaType.CaptionMd,
            color = MoyeotaColor.TextMute,
        )
        Spacer(Modifier.height(4.dp))
        // 그림자 카드 대신 줄 + Hairline(디자인 QA S16-6) — 시트 안에서 카드가 또 떠 있지 않게 한다
        candidates.forEachIndexed { index, candidate ->
            if (index > 0) HorizontalDivider(color = MoyeotaColor.Hairline)
            NearbyCandidateRow(candidate = candidate, onClick = { onCandidateClick(candidate) })
        }
    }
}

@Composable
private fun NearbyCandidateRow(candidate: MatchCandidate, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${candidate.ride.origin} → ${candidate.ride.destination}",
                style = MoyeotaType.BodyStrong,
                color = MoyeotaColor.InkPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val headcount = headcountLabel(joined = candidate.ride.members.size, capacity = candidate.ride.capacity)
            if (headcount.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = headcount,
                    style = MoyeotaType.BodySm,
                    color = MoyeotaColor.TextMute,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        // 파랑이 아니라 본문 회색 SemiBold — 이 시트의 파랑은 CTA 하나다(S16-6)
        Text(
            text = walkMinutesLabel(candidate.walkSeconds),
            style = MoyeotaType.BodySm,
            fontWeight = FontWeight.SemiBold,
            color = MoyeotaColor.TextBody,
            maxLines = 1,
        )
    }
}

/**
 * 방 카드의 인원 문구 — **16 추천 카드와 17 탐색 카드(목록 배지·지도 마커 캡션)가 같이 쓰는 한 곳**이다.
 *
 * 「같은 모양이다」를 주석으로만 약속해 두면 지켜지지 않는다: 예전에는 17 쪽이 문자열 리터럴
 * (`"${members.size}/${capacity}명"`)이라, 인원을 모르는 방을 16 은 「최대 3명」으로 17 은 「0/3명」으로
 * 썼고 정원을 모르는 방은 17 만 「2/0명」을 그렸다. 그래서 **호출부를 이 함수 하나로 모았다** —
 * `feature/home` 에 있지만 `internal` 이라 presentation 모듈 안에서는 어디서나 부른다(공용 자리인
 * `presentation/core` 로 옮기는 게 더 깔끔하지만, 이 함수의 근거·테스트가 16 과 함께 있어 여기 둔다).
 *
 * 이 숫자가 카드에서 가장 중요한 정보다. **2/3 방은 내가 들어가면 바로 출발하고 1/3 방은 한 명을
 * 더 기다려야 한다** — 걸어서 3분이 1/3 이고 6분이 2/3 이면 6분 쪽을 골라야 하는 상황인데,
 * 정원만 보여 주면 두 방이 똑같아 보인다.
 *
 * [joined] 는 [com.moyeota.domain.model.Ride.members] 의 크기이고 **0 은 「빈 방」이 아니라
 * 「모른다」**는 뜻이다(서버가 `currentMembers` 를 안 주는 옛 배포 — 그때 매퍼가 멤버를 지어내지 않고
 * 빈 목록으로 남긴다). 16 추천과 17 탐색 모두 `status = ACTIVE` 인 방만 받고 그런 방에는 최소 방장
 * 1명이 있으므로 0 은 나올 수 없는 값이다. 모를 때는 지어내지 않고 정원만 쓴다.
 *
 * [capacity] 까지 0 이면 **빈 문자열을 돌려주고 호출부가 줄 자체를 지운다** — 「최대 0명」은
 * 사용자에게 아무 뜻도 없다.
 */
internal fun headcountLabel(joined: Int, capacity: Int): String = when {
    capacity <= 0 -> ""
    joined <= 0 -> "최대 ${capacity}명"
    else -> "$joined/${capacity}명"
}

/**
 * 예상 총 요금 ÷ 인원 = 1인 요금(원), **10원 단위 반올림**.
 *
 * **이 규칙은 data 쪽 `farePerPerson` 과 한 글자도 다르면 안 된다.** 16 모달은 방을 만들기 **전**의
 * 추정치(`POST /matching/routes`)를 인원으로 나누고, 20 합류·21 대기·22 상세는 서버가 방에 박아 둔
 * 요금을 정원으로 나눈 값(`Ride.farePerPerson`)을 쓴다 — 나누는 수가 같은 값(정원)이므로 **반올림
 * 규칙만 어긋나도 같은 방이 화면마다 다른 금액**을 보여 준다(실제로 7,900원·3인 방이 16 에서
 * 2,630원, 22 에서 2,633원이었다). 모듈 경계(presentation ↔ data) 때문에 함수를 공유할 수 없어
 * 규칙을 양쪽에 적고, 「두 값이 같다」는 `FarePerPersonRuleTest`(여기)와 `PartyMappersTest`(data)가
 * **같은 표**로 못 박는다.
 *
 * 10원 단위인 근거는 택시 요금 표기 관례다 — 1원 단위 금액은 실제로 낼 수 없다.
 * [people] 이 0 이하면 0 을 돌려준다(data 쪽의 `capacity <= 0` 가드와 같은 자리). 화면에서는 칩이
 * 1~3 뿐이라 닿지 않는 길이지만, 두 규칙을 같은 모양으로 유지해 비교 테스트가 성립하게 둔다.
 */
internal fun farePerPersonWon(totalFare: Int, people: Int): Int =
    if (people <= 0) 0 else ((totalFare.toDouble() / people) / 10.0).roundToInt() * 10

/**
 * 「인원(나 포함)」에서 **1명**을 고른 상태의 안내 문구. 1 이 아니면 null — 줄을 그리지 않는다.
 *
 * 왜 필요한가: 서버 `Capacity` 는 **방장을 포함한 총 인원**이고 `Party.open` 이 생성자를 멤버로
 * 넣는다([MatchConditions]). 그래서 1 로 방을 만들면 `members.size == capacity` 가 **만든 즉시**
 * 참이 되어 아무도 합류할 수 없고, 사용자는 「같이 탈 사람 찾기」를 눌렀는데 혼자 출발한다.
 * 선택지에서 1 을 지우지 않기로 한 결정(혼자 타기를 상품으로 인정)이 성립하려면, 그 결과를
 * **고르는 자리에서** 말해 주는 것이 조건이다.
 *
 * 문구 규칙:
 * - 「1/1 로 꽉 찬 방」·「정원」 같은 내부 용어를 쓰지 않는다. 사용자가 겪는 일은 두 가지뿐이다 —
 *   **동승자를 받지 않고, 혼자 출발한다.** 둘 다 적는다(하나만 적으면 나머지를 추측하게 된다).
 * - 「같이 탈 사람」은 이 화면 CTA 와 **같은 말**이다. 「동승자」·「합승 인원」으로 바꾸지 말 것 —
 *   CTA 가 「같이 탈 사람 찾기」인데 안내가 다른 단어를 쓰면 같은 대상인지 한 번 더 생각해야 한다.
 * - 요금과 모순되지 않는다: 1명이면 [farePerPersonWon] 이 총액을 1 로 나눠 **1인 요금 = 총 요금**이
 *   되고(「예상 N분 · 총 7,900원」 칩과 푸터의 「1인 예상 요금 7,900원」이 같은 수가 된다) 그건 혼자
 *   출발하는 방의 **사실**이다. 그래서 요금 문구는 손대지 않았다 — 여기서 「요금을 전부 부담한다」를
 *   한 번 더 말하면 같은 사실을 두 자리에서 말하게 되고, 한 줄 안내가 두 줄로 늘어난다.
 */
internal fun soloCapacityNotice(capacity: Int): String? =
    if (capacity == 1) "같이 탈 사람을 받지 않고 혼자 출발해요" else null

/**
 * 도보 시간(초) → 표시 문구. **「걸어서」를 반드시 붙인다** — 서버가 주는 값은 탑승지까지 걷는
 * 시간과 내려서 걷는 시간의 **합**이고 차 타는 시간은 안 들어 있다. 「7분」만 쓰면 목적지까지
 * 7분으로 읽힌다.
 *
 * 1분 미만도 「걸어서 1분」으로 올린다 — 「0분」은 걸을 필요가 없다는 뜻으로 읽히는데 그런 경우가
 * 아니고(좌표가 다르니 거리는 있다), 반올림해 0 이 되는 값일 뿐이다.
 */
internal fun walkMinutesLabel(walkSeconds: Int): String {
    val minutes = ((walkSeconds + 59) / 60).coerceAtLeast(1)
    return "걸어서 ${minutes}분"
}

/**
 * 매칭 조건 카드의 보조 문구 한 줄 — 「혼자 출발한다」·「예약은 2명부터」·「고를 시간이 없다」가 쓴다.
 *
 * 세 자리가 **같은 모양**이어야 하는 이유는 세 문구가 같은 성질이기 때문이다: 정상 선택의 결과를
 * 알려 주는 말이고, 오류가 아니다. 빨강·경고 아이콘을 쓰지 않는 근거는 [soloCapacityNotice] 위의
 * 주석에 적어 둔 그대로다 — 오류로 읽히면 사용자는 고를 수 있는 값을 「막힌 값」으로 본다.
 * 줄바꿈을 막지 않는다(maxLines 없음): 큰 글꼴 설정에서 잘린 안내보다 두 줄이 낫고, 이 카드는
 * 세로 스크롤 영역 안이라 잘릴 일이 없다.
 */
@Composable
private fun ConditionNotice(text: String) {
    Text(
        text = text,
        style = MoyeotaType.BodySm,
        color = MoyeotaColor.TextMute,
        modifier = Modifier.padding(bottom = 12.dp),
    )
}

/**
 * 라벨을 **윗줄**로 올리고 그 아래에 고르는 조각(날짜 띠·시각 휠)을 놓는 조건 행.
 *
 * [ConditionRow] 의 「라벨 + 남는 폭에 오른쪽 정렬」은 칩 서너 개짜리 모양이라 띠(8칸)·휠(두 개)을 담지
 * 못한다 — 그래서 라벨을 위로 올리고 조각이 카드 폭을 다 쓰게 한다. 예전 이름은 `ScrollableChipRow`
 * 였다(날짜·시각이 가로 스크롤 칩이던 시절).
 *
 * @param trailing 라벨 줄 오른쪽 끝의 보조 글(날짜 띠 위의 「11월」 — [reservationMonthLabel]). 없으면 null
 */
@Composable
private fun PickerSection(label: String, trailing: String? = null, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MoyeotaType.BodyStrong,
                color = MoyeotaColor.InkPrimary,
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) {
                Text(
                    text = trailing,
                    style = MoyeotaType.CaptionMd,
                    color = MoyeotaColor.TextMute,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun ConditionRow(label: String, chips: @Composable () -> Unit) {
    Row(
        // 칩이 48dp 터치 영역을 가지므로(MoyeotaChip) 위아래 4dp 만 더해 줄 높이 56dp 가 된다
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MoyeotaType.BodyStrong,
            color = MoyeotaColor.InkPrimary,
        )
        Spacer(Modifier.width(12.dp))
        // 칩 행이 **남는 폭 안에서** 오른쪽 정렬된다. 예전엔 라벨 뒤 Spacer(weight) + 고정폭 칩이라
        // 작은 폰(360dp)에서 칩 3개가 카드 밖으로 밀려 「500m」이 잘리고 선택 칩의 파란 배경이 카드
        // 모서리를 넘어 보였다(실기 QA). 칩은 앱 공용 [MoyeotaChip] 이고 칩 사이는 8dp(터치 간격 규칙)다.
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            chips()
        }
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun DestinationConfirmModalPreview() {
    DestinationConfirmModal(
        destinationName = "도착지",
        destinationAddress = "",
        originStopName = "출발지",
    )
}

/**
 * 예약 상태 — 날짜 띠·시각 휠이 열리고, **요금·경로 요약이 사라진 자리에 그 이유가 적혀 있는지**를
 * 눈으로 확인하는 미리보기다. 경로 추정은 일부러 채워 둔다(`routePreview`): 값이 있어도 예약에서는
 * 숨겨야 하므로, 비워 두면 「없어서 안 보이는 것」과 구별되지 않는다.
 */
@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun DestinationConfirmModalReservationPreview() {
    DestinationConfirmModal(
        destinationName = "서면역",
        destinationAddress = "부산 부산진구 중앙대로",
        originStopName = "부산대 정문",
        routePreview = RoutePreviewUi(estimatedMinutes = 18, estimatedFare = 9_600),
        initiallyScheduled = true,
    )
}
