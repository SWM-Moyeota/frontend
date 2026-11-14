package com.moyeota.presentation.feature.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.moyeota.core.designsystem.component.ChevronIcon
import com.moyeota.core.designsystem.component.FareSummary
import com.moyeota.core.designsystem.component.MapOverlayPill
import com.moyeota.core.designsystem.component.MoyeotaDefaultCamera
import com.moyeota.core.designsystem.component.MoyeotaTopBar
import com.moyeota.core.designsystem.component.RouteSummary
import com.moyeota.core.designsystem.component.SectionLabel
import com.moyeota.core.designsystem.component.RouteStripMap
import com.moyeota.core.designsystem.component.isUsableMapCoordinate
import com.moyeota.core.designsystem.component.decodePolyline
import com.moyeota.core.designsystem.component.latLngOrNull
import com.moyeota.core.designsystem.component.polylineDistanceMeters
import com.moyeota.core.designsystem.component.NavigationBarSpacer
import com.moyeota.core.designsystem.component.NoticeBanner
import com.moyeota.core.designsystem.component.NoticeKind
import com.moyeota.core.designsystem.component.PrimaryCtaButton
import com.moyeota.core.designsystem.component.SecondaryButton
import com.moyeota.core.designsystem.component.SheetHandle
import com.moyeota.core.designsystem.component.StatusBadge
import com.moyeota.core.designsystem.component.StatusBarSpacer
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.core.designsystem.theme.MoyeotaShape
import com.moyeota.core.designsystem.theme.MoyeotaSpacing
import com.moyeota.core.designsystem.theme.MoyeotaType
import com.moyeota.core.designsystem.theme.sheetShadow
import com.moyeota.presentation.core.buildRouteChipLabel
import com.moyeota.presentation.core.pickupDistanceLabel
import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RideStatus
import com.moyeota.domain.model.RoomType
import com.moyeota.domain.model.User
import com.naver.maps.geometry.LatLng
import java.time.Instant
import com.moyeota.presentation.core.MeBadge
import com.moyeota.presentation.core.MemberAvatar
import com.moyeota.presentation.core.displayNickname
import com.moyeota.presentation.core.isWithdrawn
import com.moyeota.presentation.core.rideCountLabel
import com.moyeota.presentation.feature.home.reservationCommitmentNotice

private fun won(amount: Int): String = "%,d원".format(amount)

/** 지도 스트립 높이. */
private val JoinMapHeight = 172.dp

/** 시트가 시작하는 높이 — 지도 아래 12dp 를 덮는다(그 겹침이 카메라 여백에 더해진다). */
private val JoinSheetTop = 160.dp

/** 지도 위 소요 시간 칩의 바깥 여백과 높이 — 카메라 위쪽 여백이 이 둘을 더한 만큼 커진다. */
private val EtaChipMargin = 16.dp
private val EtaChipHeight = 30.dp

/**
 * 방 좌표를 하나도 못 쓸 때 20 의 지도가 보여 줄 곳 — **내 위치**, 없으면 **앱 기본 카메라**.
 *
 * 반드시 값을 준다(null 을 돌려주지 않는다): [RouteStripMap] 은 이 값이 null 이고 도착지도 없으면
 * 자리표시자를 그리는데, 20 은 「어디서 타는 방인가」를 보는 화면이라 빈 회색 상자보다 지도가 낫다.
 * 그리고 끝까지 물러나도 **SDK 기본 카메라(서울)가 아니다**.
 */
internal fun joinMapFallback(userPosition: LatLng?): LatLng =
    userPosition?.takeIf { isUsableMapCoordinate(it.latitude, it.longitude) } ?: MoyeotaDefaultCamera

/**
 * 20 · 합류 확인 [신규]
 *
 * 진입: 18·19 목록/마커의 「합류」
 *
 * 이동(디스크립션):
 * - 「닫기」 / 뒤로가기 → 직전 목록(18/19)으로 복귀 (onDismiss)
 * - 「이 탑승에 합류하기」 → 22 탑승 상세 (onConfirmJoin, 제출 중 loading)
 * - 동승자 행 탭 → 23 동승자 프로필 (onMemberClick)
 *
 * 표시 값은 전부 방 상세([Ride])에서 나온다. 서버가 주지 않는 값(성별 조건·탑승 시각·서비스 요금)은
 * 지어내지 않고 그 요소를 아예 두지 않는다 — 출발 표기는 [Ride.departureLabel] 이 있을 때만,
 * 도착 쪽은 [Ride.estimatedMinutes] 가 있을 때만 「약 N분」으로 보이고, 1인 부담은 [Ride.farePerPerson] 그대로다.
 *
 * 검증·상태 — **판정은 [joinGate] 하나**이고 배지·배너·CTA 가 모두 그 결과를 따른다:
 * - 상태 배지는 [Ride.status] 를 그대로 읽는다([joinStatusBadge]) — 예전에는 문자열 리터럴이라
 *   **취소된 방에도 「모집 중」**이 떠 있었다. 17 목록은 15초 주기라 방을 고르는 사이에 마지막 멤버가
 *   나가 방이 닫힐 수 있다(`Party.leave` → `members.isEmpty()` → `cancel()`)
 * - 모집 중이 아니거나 정원이 찼으면 CTA 비활성 + CTA 위 NoticeBanner(ERROR) **한 줄**
 *   (배너는 **하나만** 띄운다 — 예전에는 「방금 인원이 찼어요」와 서버 실패 문구가 거의 같은 말을
 *   두 번 했다: `refreshQuietly()` 가 성공하면 둘이 함께 떴다)
 * - 내가 이미 그 방 멤버면 「이미 참여 중인 방이에요」 + CTA 는 그 방으로 돌아가기([onOpenJoinedRide])
 * - 서버 합류 실패(엔드포인트 부재 포함) 시 joinErrorMessage 를 같은 자리에 그대로 노출
 */
@Composable
fun JoinConfirmScreen(
    ride: Ride,
    /**
     * 내 위치 → 이 방의 탑승 위치 거리(m). 모르면 null 이고 그 줄을 감춘다.
     * 「도보 N분」은 쓰지 않는다 — 보행 속도를 지어내야 하고, 거리와 달리 잰 값이 아니다.
     */
    pickupDistanceMeters: Double? = null,
    /**
     * 내 현재 위치. 지도가 방 좌표를 하나도 못 쓸 때(누락·(0,0)·NaN) 「내 주변」으로 물러나는 자리다.
     * 모르면 null — 그때는 앱 기본 카메라다([joinMapFallback]).
     */
    userPosition: LatLng? = null,
    joining: Boolean = false,
    joinErrorMessage: String? = null,
    onDismiss: () -> Unit = {},
    onConfirmJoin: (Ride) -> Unit = {},
    /**
     * 내가 이미 이 방 멤버일 때의 CTA — 호출부가 그 방의 진행 화면(21)으로 보낸다.
     *
     * Route 는 합류 성공과 **같은 길**(`JoinConfirmRoute` 의 onJoined)로 넘긴다: 서버가 409
     * `ALREADY_JOINED` 를 돌려줄 때 이미 그렇게 처리하고 있어서
     * ([com.moyeota.domain.model.PartyJoinException.isAlreadyInThisParty]) 새 네비게이션이 필요 없다.
     * 기본값은 방어용 빈 람다다 — 연결하지 않은 호출부에서는 버튼이 아무 일도 하지 않는다.
     */
    onOpenJoinedRide: (Ride) -> Unit = {},
    onMemberClick: (User) -> Unit = {},
) {
    val joinedCount = ride.members.size
    // 예약 방의 「오늘 오후 6:30」을 셈하는 기준 시각. **한 번만 읽는다** — 매 리컴포지션마다
    // 시계를 읽을 이유가 없고(날짜 경계를 넘길 만큼 머무는 화면이 아니다), 읽으면 같은 방이
    // 프레임마다 다른 글자를 갖는다.
    val now = remember { Instant.now() }
    // 내가 이미 이 방의 멤버인가. 「나」 판정의 근거는 서버가 멤버마다 실어 주는 publicId 와 세션
    // uuid 의 비교 하나다(`PartyMappers` 의 `toUser` — 방장 판정과 달리 **폴백이 없어** 한쪽으로
    // 기울지 않는다). 20 은 방 상세를 읽어 그리므로 이 비교가 성립한다 — 식별자가 없는 목록·생성
    // 응답의 자리 표시용 멤버는 늘 false 다. 로그인 복원 전이면 세션 uuid 가 null 이라 false 로
    // 떨어지는데, 그때 눌러도 서버 409 를 「이미 합류됨」으로 받아 21 로 보낸다
    // (JoinConfirmViewModel.join) — 문구는 덜 정확해지지만 갈 곳은 같다.
    val iAmMember = ride.members.any { it.isMe }
    // 배지·배너·CTA 를 **한 판정**에서 뽑는다 — 세 자리가 제각각 다른 조건을 보면 「모집 중」 배지 +
    // 활성 CTA + 「모집이 끝났어요」 배너처럼 서로 모순되는 화면이 나온다.
    val gate = joinGate(
        status = ride.status,
        joinedCount = joinedCount,
        capacity = ride.capacity,
        iAmMember = iAmMember,
    )
    val badge = joinStatusBadge(ride.status)
    // 배너는 한 줄만 — 막힌 이유가 있으면 그것을, 없으면 서버가 돌려준 실패 문구를 보여 준다.
    val noticeMessage = joinGateMessage(gate) ?: joinErrorMessage

    // 지도에 그릴 값 — 전부 방 상세(Ride)에서 나온다. 좌표가 못 쓸 값이면(서버 위경도 전치, QA D-1)
    // latLngOrNull 이 null 을 돌려주고 지도는 자리표시자로 떨어진다.
    val originPosition = latLngOrNull(ride.originLat, ride.originLng)
    val destinationPosition = latLngOrNull(ride.destinationLat, ride.destinationLng)
    val routePath = remember(ride.routePolyline) {
        ride.routePolyline?.let(::decodePolyline).orEmpty()
    }
    // 거리는 서버가 주지 않아 **경로선에서 직접 잰다**. 경로가 없으면 거리 표기를 생략한다 —
    // 출발·도착 직선거리로 대체하면 실제 주행 거리보다 짧아 요금과 어긋나 보인다.
    val routeKm = remember(routePath) {
        polylineDistanceMeters(routePath).takeIf { it > 0.0 }?.let { it / 1000.0 }
    }
    val etaLabel = buildRouteChipLabel(ride.estimatedMinutes, routeKm)

    Column(modifier = Modifier.fillMaxSize().background(MoyeotaColor.SurfaceCanvas)) {
        // 상단 헤더 — 하위 화면 공용 앱바(디자인 QA G7·J3). 예전엔 왼쪽 정렬 18sp Bold 를 손으로 그렸고
        // 뒤로가기는 화살표 그림만 눌렸다(48dp 가 안 됐다)
        StatusBarSpacer()
        MoyeotaTopBar(title = "합류할까요?", onBack = onDismiss)

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // 경로 지도 스트립 — 카메라는 출발·도착(+경로선)이 **가리지 않는 영역**에 다 들어오게 맞춘다
            // (2026-11, 오너 UX 피드백: 「가끔 엉뚱한 곳, 심지어 해외가 보인다」). 원인은 둘이었다:
            // ① (0,0) 같은 못 쓸 좌표가 카메라 범위에 섞여 바다 한가운데를 비췄고 ② 지도가 준비된 뒤에야
            // 카메라를 옮겨 SDK 기본 카메라(서울)가 먼저 보였다. ①은 [routeCameraTarget] 이 좌표를 걸러
            // 정해진 순서(출발지 → 도착지 → 내 주변 → 앱 기본)로 물러나 막고, ②는 [NaverMapView] 가 첫
            // 카메라를 생성 옵션으로 걸어 막는다. 방을 다시 읽어 좌표가 바뀌면 그때 다시 맞춘다.
            //
            // 여백(디자인 QA 4-7): 좌우 48dp, 위는 소요 시간 칩(16 + 30dp)만큼 더(칩이 있을 때만),
            // 아래는 시트가 덮는 12dp 만큼 더 — 마커 머리·캡션 높이는 [RouteStripMap] 이 얹는다. 최대 줌 16.
            Box(modifier = Modifier.fillMaxWidth().height(JoinMapHeight)) {
                RouteStripMap(
                    modifier = Modifier.fillMaxSize(),
                    originPosition = originPosition,
                    destinationPosition = destinationPosition,
                    routePath = routePath,
                    fallbackPosition = joinMapFallback(userPosition),
                    obscuredTop = if (etaLabel != null) EtaChipMargin + EtaChipHeight else 0.dp,
                    obscuredBottom = JoinMapHeight - JoinSheetTop,
                    sidePadding = 48.dp,
                )
                // 시간·거리 둘 다 없으면 칩을 통째로 감춘다 — 틀린 값보다 없는 편이 낫다.
                // 지도 위 공용 흰 알약([MapOverlayPill], 30dp = [EtaChipHeight])이다 — 16·21 의 요약 칩과 같은 그림자·모양
                if (etaLabel != null) {
                    MapOverlayPill(modifier = Modifier.padding(EtaChipMargin)) {
                        Text(
                            text = etaLabel,
                            style = MoyeotaType.BodySm.copy(fontWeight = FontWeight.SemiBold),
                            color = MoyeotaColor.InkPrimary,
                        )
                    }
                }
            }

            // 바텀시트
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = JoinSheetTop)
                    .sheetShadow(MoyeotaShape.Sheet)
                    .clip(MoyeotaShape.Sheet)
                    .background(MoyeotaColor.SurfaceCanvas),
            ) {
                Spacer(Modifier.height(12.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    SheetHandle()
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                ) {
                    Spacer(Modifier.height(12.dp))

                    // 상태 행
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusBadge(kind = badge.kind, text = badge.text)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = joinHeadcountLine(
                                joinedCount = joinedCount,
                                capacity = ride.capacity,
                                iAmMember = iAmMember,
                            ),
                            style = MoyeotaType.BodySm,
                            color = MoyeotaColor.TextMute,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    // 경로·요금은 **판 없이** 시트 위에 바로 놓는다(디자인 QA J1) — 예전엔 옅은 파란 판(경로) 아래
                    // 회색 판 안에 또 파란 판(1인 부담)이 있는 "카드 더미"였다. 22 탑승 상세와 같은 공용 조각이다.
                    RouteSummary(
                        origin = ride.origin,
                        destination = ride.destination,
                        // 예약 방은 **언제 출발하는지가 합류 판단의 전부**다(사흘 뒤 오전 9시 방에
                        // 들어가는 일이다). 서버가 주는 `departureLabel` 이 비어 올 수 있어
                        // 약속 시각에서 직접 만든다 — 실시간 방은 예전 표기를 그대로 둔다
                        // (그쪽 라벨에는 거리가 함께 들어 있다).
                        originMeta = joinDepartureLabel(ride, now)
                            ?: ride.departureLabel.takeIf { it.isNotBlank() },
                        destinationMeta = ride.estimatedMinutes?.let { "약 ${it}분" },
                        // 내 위치를 모르면 점선만 남는다 — 보행 시간 같은 지어낸 값을 채우지 않는다
                        connectorNote = pickupDistanceMeters?.let { "내 위치에서 ${pickupDistanceLabel(it)}" },
                    )

                    Spacer(Modifier.height(16.dp))
                    // **요금을 모르는 방에는 요금 카드를 그리지 않는다**([joinFareOrNull]) —
                    // 예전에는 예약 방에서 「총 예상 요금 0원 · 합류 시 1인 부담 0원」을 그렸다.
                    val fare = joinFareOrNull(totalFare = ride.totalFare, farePerPerson = ride.farePerPerson)
                    if (fare != null) {
                        // 계산 근거 한 줄(「총 예상 요금을 정원 N명으로 나눈 금액이에요」)은 FareSummary 가 맡는다 —
                        // 아래 안내 줄이 같은 말을 한 번 더 하던 것은 지웠다(디자인 QA J2)
                        FareSummary(
                            total = won(fare.totalFare),
                            perPerson = won(fare.farePerPerson),
                            capacity = ride.capacity,
                            perPersonLabel = "합류 시 1인 부담",
                        )
                    } else {
                        // 요금 요약과 **같은 자리·같은 구분선** 아래 한 줄 — 회색 판을 깔지 않는다
                        HorizontalDivider(color = MoyeotaColor.Hairline)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = joinFareAbsentNotice(ride.roomType),
                            style = MoyeotaType.BodySm,
                            color = MoyeotaColor.TextMute,
                        )
                    }

                    Spacer(Modifier.height(MoyeotaSpacing.SectionGap))
                    // 인원 수는 머리말에 다시 적지 않는다 — 맨 위 상태 행이 이미 「N명 참여」라고 말한다(디자인 QA J2)
                    SectionLabel(text = "이미 참여한 사람")
                    // 대표로 한 명만 노출한다 — 첫 멤버라는 것 외에 특별한 지위는 없다.
                    // 탈퇴 회원이 앞에 있으면 볼 것도 눌러 볼 것도 없어 실재하는 멤버를 앞세운다.
                    val representative = ride.members.firstOrNull { !it.isWithdrawn }
                        ?: ride.members.firstOrNull()
                    if (representative != null) {
                        MemberRow(
                            member = representative,
                            othersCount = joinedCount - 1,
                            onClick = { onMemberClick(representative) }, // → 23 동승자 프로필
                        )
                    }

                    // ★ **예약 방은 합류하면 나갈 수 없다.** 누르기 **전에** 말해야 하는 사실이라
                    // 안내 줄보다 눈에 들게 적는다([reservationCommitmentNotice] KDoc 의 근거):
                    // 실시간 방은 「그만 찾기」로 빠져나올 수 있어서 사용자는 합류를 되돌릴 수 있는
                    // 선택으로 배워 왔고, 이 버튼은 같은 모양으로 전혀 다른 약속을 하게 만든다.
                    //
                    // 공용 안내 배너(INFO)다(디자인 QA G8·J3) — INFO 는 이제 **회색**이라, CTA 바로 위의 막힘·실패
                    // 배너(빨강, 이 화면의 「배너 하나」 자리)와 색으로도 자리로도 갈린다. 이 줄은 스크롤 본문에 있고
                    // 그 배너는 CTA 위에 고정이다. 오류가 아니므로 빨강을 쓰지 않는 것은 그대로다.
                    if (ride.roomType == RoomType.RESERVATION) {
                        Spacer(Modifier.height(12.dp))
                        NoticeBanner(kind = NoticeKind.INFO, text = reservationCommitmentNotice(joining = true))
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        // 1인 부담금의 계산 방식은 바로 위 요금 요약이 이미 말한다 — 여기서 다시 쓰지 않는다(디자인 QA J2)
                        text = "합류하면 바로 채팅방에 들어가요",
                        style = MoyeotaType.CaptionMd,
                        color = MoyeotaColor.TextMute,
                    )
                    Spacer(Modifier.height(16.dp))
                }

                // 막힌 사유든 서버 실패든 **배너는 하나**다 (스펙 20). 전에는 「방금 인원이 찼어요」와
                // 서버가 돌려준 「방이 가득 찼어요」가 거의 같은 말을 두 번 했다 — 합류 실패 뒤
                // `refreshQuietly()` 가 성공하면 두 조건이 동시에 참이 된다.
                if (noticeMessage != null) {
                    NoticeBanner(
                        // 「이미 참여 중」은 실패가 아니라 안내다 — 빨간 배너로 띄우면 뭔가 잘못된
                        // 것처럼 보여 「방으로 돌아가기」 CTA 와 모순된다.
                        kind = if (gate == JoinGate.ALREADY_JOINED) NoticeKind.INFO else NoticeKind.ERROR,
                        text = noticeMessage,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SecondaryButton(
                        text = "닫기",
                        onClick = onDismiss,
                        modifier = Modifier.width(112.dp),
                    )
                    // 이미 멤버면 합류가 아니라 **그 방으로 돌아가는** 버튼이다 — 서버는 재합류를
                    // 409 로 막고, 그 409 는 이미 「이미 합류됨」으로 다뤄 21 로 보내고 있다
                    // (JoinConfirmViewModel.join). 같은 곳으로 가는 길을 버튼으로 당겨 둔 것뿐이다.
                    if (gate == JoinGate.ALREADY_JOINED) {
                        PrimaryCtaButton(
                            text = "방으로 돌아가기",
                            onClick = { onOpenJoinedRide(ride) },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        PrimaryCtaButton(
                            text = "이 탑승에 합류하기",
                            onClick = { onConfirmJoin(ride) }, // 성공 시 → 22 탑승 상세
                            // 모집 중이 아니거나 정원이 찬 방은 **눌러도 409 뿐**이다. 전에는 상태를
                            // 보지 않아 취소된 방에서도 활성이었고, 누른 뒤에야 사유를 알 수 있었다.
                            enabled = gate == JoinGate.JOINABLE,
                            loading = joining,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                NavigationBarSpacer()
            }
        }
    }
}

// ─── 단계·CTA 판정 (순수 함수 — 화면 없이 테스트한다) ───────────────────────

/** [joinStatusBadge] 가 돌려주는 배지 하나. 색과 문구는 항상 짝지어 움직인다. */
internal data class JoinStatusBadge(val kind: NoticeKind, val text: String)

/**
 * 방 단계([RideStatus]) → 상태 배지.
 *
 * enum 을 **포괄하지 않으면 컴파일되지 않는** `when` 이다 — 새 단계가 도메인에 생길 때 「모집 중」이
 * 조용히 남는 일을 막아 주는 것이 여기 분기를 다 적어 둔 이유다.
 *
 * 20 은 모집 중인 방을 고르려고 여는 화면이라, 다른 단계는 모두 **들어갈 수 없는 방**이라는 뜻이다
 * (17 목록이 15초 주기라 고르는 사이에 바뀐다). 그래서 문구를 「… 방」으로 적었다 — 지금 내가 들어갈
 * 수 있는 상황이 아니라는 사실이 배지에서 읽혀야 한다.
 */
internal fun joinStatusBadge(status: RideStatus): JoinStatusBadge = when (status) {
    RideStatus.RECRUITING -> JoinStatusBadge(NoticeKind.SUCCESS, "모집 중")
    RideStatus.MATCHED -> JoinStatusBadge(NoticeKind.WAITING, "모집 마감된 방")
    RideStatus.COMPLETED -> JoinStatusBadge(NoticeKind.INFO, "합승이 끝난 방")
    RideStatus.CANCELED -> JoinStatusBadge(NoticeKind.ERROR, "취소된 방")
}

/** 20 의 배너·CTA 를 정하는 판정 결과 — [joinGate]. */
internal enum class JoinGate {
    /** 지금 합류할 수 있다 */
    JOINABLE,

    /** 이미 내가 이 방의 멤버다 — 합류가 아니라 돌아갈 자리다 */
    ALREADY_JOINED,

    /** 모집 중이 아닌 방(모집 마감·종료·취소) */
    NOT_RECRUITING,

    /** 상태는 모집 중인데 자리가 없다 */
    FULL,
}

/**
 * 지금 합류할 수 있는가 — 20 의 **단일 판정**이다(배지 문구만 [joinStatusBadge] 가 따로 맡는다).
 *
 * 위에서부터 읽는다 — 앞의 조건이 뒤를 가린다.
 *
 * | 판정 | 조건 | 이 순서인 이유 |
 * |---|---|---|
 * | [JoinGate.ALREADY_JOINED] | [iAmMember] | 서버 목록은 **내 방을 빼지 않는다.** 17 은 이제 [exploreList] 로 걸러내지만, 목록이 낡았을 때·알림으로 20 에 바로 들어올 때는 여전히 닿는다 — **그 경우의 방어로 남긴다**(걸러내기가 사라져도 20 이 거짓을 말하지 않게 하는 안전망이다). 그때 「내가 N+1번째」는 거짓이고 합류를 시도해도 409 뿐이다 |
 * | [JoinGate.NOT_RECRUITING] | 상태가 모집 중이 아니다 | **정원 비교보다 앞**이다: 취소된 방은 멤버가 0명이라 정원만 보면 「자리 있음」으로 읽힌다 |
 * | [JoinGate.FULL] | 인원 ≥ 정원 | 상태는 아직 ACTIVE 인데 목록이 낡아 자리가 없는 경우(기존 동작 그대로) |
 * | [JoinGate.JOINABLE] | 그 밖 | |
 *
 * @param status 방 상세의 단계([com.moyeota.domain.model.Ride.status])
 * @param joinedCount 방 상세의 멤버 수 — **나도 포함된 수**다
 * @param capacity 방 정원(방장 포함)
 * @param iAmMember 멤버 중 [com.moyeota.domain.model.User.isMe] 가 있는가
 */
internal fun joinGate(
    status: RideStatus,
    joinedCount: Int,
    capacity: Int,
    iAmMember: Boolean,
): JoinGate = when {
    iAmMember -> JoinGate.ALREADY_JOINED
    status != RideStatus.RECRUITING -> JoinGate.NOT_RECRUITING
    joinedCount >= capacity -> JoinGate.FULL
    else -> JoinGate.JOINABLE
}

/**
 * 막힌 이유 한 줄. [JoinGate.JOINABLE] 이면 null — 띄울 배너가 없다는 뜻이다.
 *
 * 문구를 서버 실패 문구([joinFailureMessage])와 일부러 같은 말로 적었다 — 같은 사정을 **눈으로 보고
 * 막히는 것**과 **눌러서 알게 되는 것**이 다른 말로 보이면, 사용자는 사정이 두 가지 있는 줄로 읽는다.
 */
internal fun joinGateMessage(gate: JoinGate): String? = when (gate) {
    JoinGate.JOINABLE -> null
    JoinGate.ALREADY_JOINED -> "이미 참여 중인 방이에요"
    JoinGate.NOT_RECRUITING -> "모집이 끝난 방이에요. 목록에서 다른 동승을 선택해 주세요"
    JoinGate.FULL -> "방금 인원이 찼어요. 목록에서 다른 동승을 선택해 주세요"
}

/**
 * 상태 행의 인원 문구.
 *
 * 「내가 N번째」는 **내가 아직 멤버가 아닐 때만** 쓴다. [joinedCount] 는 방 상세의 멤버 수라 **나를
 * 포함**하므로, 내 방을 열었을 때도 +1 을 더하면 「1명 참여 · 목표 3명 · 내가 2번째」처럼 한 칸 밀린
 * 거짓이 된다(실제로는 내가 1번째다). 그 경우에는 순서를 고쳐 쓰지 않고 **이미 들어와 있다는 사실**만
 * 쓴다 — 「내가 1번째」로 바꿔 쓰면 지금 새로 합류하는 것처럼 읽힌다.
 */
internal fun joinHeadcountLine(joinedCount: Int, capacity: Int, iAmMember: Boolean): String {
    val base = "${joinedCount}명 참여 · 목표 ${capacity}명"
    return if (iAmMember) "$base · 이미 참여 중" else "$base · 내가 ${joinedCount + 1}번째"
}

/** 요금 카드가 그릴 두 금액. 하나라도 알 때만 만들어지고, 모르면 아예 없다([joinFareOrNull]). */
internal data class JoinFare(val totalFare: Int, val farePerPerson: Int)

/**
 * 요금을 **말할 수 있는 방인가** — 말할 수 없으면 null 이고 호출부가 카드를 그리지 않는다.
 *
 * ### 왜 필요해졌나 — 예약 방이 「0원」이었다 ★
 * 서버는 예약 방(`POST /matching/reservations`)에 **요금·경로·소요 시간을 넣지 않는다.** 도메인
 * [Ride.totalFare]·[Ride.farePerPerson] 은 non-null `Int` 라 그 방은 0 으로 와서, 이 화면이
 * 「총 예상 요금 0원」·「합류 시 1인 부담 0원」을 **금액으로** 그렸다. 0원은 「모른다」가 아니라
 * **「공짜로 탄다」**로 읽히고, 그건 합류 직전에 보여 주는 가장 나쁜 거짓이다.
 *
 * 0 을 「모른다」로 읽는 것은 이 저장소의 기존 관례다(`headcountLabel` 의 `joined <= 0`,
 * `RideHistoryDisplay.fareOrNull` 의 둘 다 null). 실제로 0원인 택시 요금은 없으므로 이 판정이
 * 참값을 가리는 경우도 없다.
 *
 * 한쪽만 아는 응답은 실측에서 본 적이 없지만(서버가 같은 값에서 둘을 만든다) **있는 쪽만** 그리지
 * 않고 둘 다 그린다 — 0 쪽은 어차피 「모른다」라서, 둘 중 하나라도 알면 카드를 세우고 모르는 칸은
 * 0원으로 보이는 것보다 서버가 준 값을 그대로 보여 주는 편이 낫다(그 조합은 지금 오지 않는다).
 */
internal fun joinFareOrNull(totalFare: Int, farePerPerson: Int): JoinFare? =
    if (totalFare <= 0 && farePerPerson <= 0) null else JoinFare(totalFare, farePerPerson)

/**
 * 요금 카드가 있던 자리의 한 줄 — **자리를 비워 두지 않는다**(빈 자리는 로딩 실패로 읽힌다).
 *
 * 예약 방은 **왜 없는지**를 말할 수 있다(서버가 그 방에 요금을 넣지 않는다). 실시간 방에서 요금이
 * 0 인 경우는 사유를 모르므로 **모른다고만** 한다 — 「예약이라서」라고 쓰면 틀린 설명이 된다.
 * 16 도착지 확인이 예약 모드에서 쓰는 문구와 **같은 사실**을 말하지만 문장이 다른 이유는 그쪽은
 * 만들기 전(요금이 생기지 않을 것이다)이고 이쪽은 만들어진 뒤(그 방에 요금이 없다)이기 때문이다.
 */
internal fun joinFareAbsentNotice(roomType: RoomType): String = when (roomType) {
    RoomType.RESERVATION -> "예약 방은 예상 요금이 없어요. 요금은 탈 때 미터기로 정해져요"
    RoomType.REALTIME -> "예상 요금을 받지 못했어요"
}

/**
 * 20 합류 확인의 출발 표기 — **예약 방일 때만** 약속 시각에서 만든다. 실시간 방은 null 이고
 * 호출부가 서버 `departureLabel` 을 그대로 쓴다(그 라벨에는 거리가 함께 들어 있다).
 *
 * 예약에서 이 줄이 비면 사용자는 **언제 출발하는지 모르고 합류**한다 — 사흘 뒤 오전 9시 방에
 * 들어가는 일인데 목록에서 본 시각이 이 화면에는 없게 된다. 서버 `departureLabel` 을 믿지 않는
 * 이유: 그 값은 목록·상세 응답에 따라 비어 올 수 있고, 약속 시각(`departureAt`)이 **원본**이다.
 *
 * 읽을 수 없으면 null 이다 — 「시간 미정」 같은 자리값을 두면 확정된 방을 미정으로 보여 준다.
 */
internal fun joinDepartureLabel(ride: Ride, now: Instant): String? =
    if (ride.roomType != RoomType.RESERVATION) null
    else reservationDepartureLabel(ride.departureAt, now)?.let { "$it 출발" }

// ─── 조각 ───────────────────────────────────────────────────────────────────

@Composable
private fun MemberRow(
    member: User,
    othersCount: Int,
    onClick: () -> Unit,
) {
    // 매너 %(별점 환산)는 지웠다 — 평가 API 가 없어 rating 은 항상 0.0 이고,
    // 그걸 환산하면 "매너 0%" 라는 없는 사실을 사람에 붙이게 된다.
    val othersLabel = if (othersCount > 0) " · 외 ${othersCount}명" else ""
    // 탈퇴 회원은 열어 볼 프로필이 없다 — 탭을 막는다
    val clickable = !member.isWithdrawn
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MoyeotaShape.Card)
            .then(if (clickable) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MemberAvatar(user = member, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = member.displayNickname,
                    style = MoyeotaType.BodyStrong,
                    color = if (clickable) MoyeotaColor.InkPrimary else MoyeotaColor.TextMute,
                )
                Spacer(Modifier.width(8.dp))
                if (member.isMe) MeBadge() else PartnerBadge()
            }
            Text(
                text = if (clickable) "${member.rideCountLabel}$othersLabel" else "탈퇴한 회원$othersLabel",
                style = MoyeotaType.BodySm,
                color = MoyeotaColor.TextMute,
            )
        }
        if (clickable) {
            ChevronIcon()
        }
    }
}

/**
 * 「동승자」 — 옆에 서는 「나」([MeBadge])·「방장」 배지와 **같은 크기**(CaptionSm 세미볼드 · 8/2dp)로 둔다.
 * 예전엔 옅은 파란 바탕에 11sp Bold 로 혼자 컸다. 사실을 붙이는 것이라 회색이다.
 */
@Composable
private fun PartnerBadge() {
    Box(
        modifier = Modifier
            .background(MoyeotaColor.SurfaceSoft, CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = "동승자",
            style = MoyeotaType.CaptionSm,
            fontWeight = FontWeight.SemiBold,
            color = MoyeotaColor.TextBody,
        )
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun JoinConfirmScreenPreview() {
    // 프리뷰 전용 더미 — 실행 경로에는 흘러가지 않는다
    JoinConfirmScreen(
        ride = Ride(
            id = "preview",
            origin = "출발지",
            destination = "도착지",
            departureLabel = "",
            capacity = 3,
            members = listOf(User("u-1", "동승자", "", 0.0, 3)),
            farePerPerson = 3000,
            totalFare = 9000,
            status = RideStatus.RECRUITING,
            estimatedMinutes = 20,
        ),
    )
}

/**
 * **예약 방에 합류**하는 상태 — 요금 카드가 사라진 자리에 그 이유가 있고([joinFareAbsentNotice]),
 * 출발 표기가 약속 시각에서 만들어졌는지([joinDepartureLabel]) 눈으로 확인하는 자리다.
 *
 * 요금·소요 시간을 **일부러 비운다**: 서버가 예약 방에 그 값을 넣지 않으므로 이게 실제 상태다.
 */
@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun JoinConfirmScreenReservationPreview() {
    JoinConfirmScreen(
        ride = Ride(
            id = "preview-reservation",
            origin = "부산대 정문",
            destination = "서면역",
            departureLabel = "",
            capacity = 3,
            members = listOf(User("u-1", "동승자", "", 0.0, 3)),
            farePerPerson = 0,
            totalFare = 0,
            status = RideStatus.RECRUITING,
            departureAt = "2026-11-09T09:30:00Z",
            isConfirmed = true,
            roomType = RoomType.RESERVATION,
        ),
    )
}
