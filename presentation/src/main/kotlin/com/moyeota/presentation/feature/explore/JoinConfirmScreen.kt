package com.moyeota.presentation.feature.explore

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeota.core.designsystem.component.BackArrowIcon
import com.moyeota.core.designsystem.component.RouteStripMap
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
import com.moyeota.presentation.core.buildRouteChipLabel
import com.moyeota.presentation.core.pickupDistanceLabel
import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RideStatus
import com.moyeota.domain.model.User
import com.moyeota.presentation.core.MeBadge
import com.moyeota.presentation.core.MemberAvatar
import com.moyeota.presentation.core.displayNickname
import com.moyeota.presentation.core.isWithdrawn
import com.moyeota.presentation.core.rideCountLabel

// 와이어프레임 그레이·서페이스 (core token 미정의 색 — 화면 재현용)
private val CanvasBg = Color(0xFFF5F7FA)
private val GrayMute = Color(0xFF8A93A0)
private val GrayAsh = Color(0xFF9AA1AC)
private val GraySlateDeep = Color(0xFF54637D)
private val TextMuteWarm = Color(0xFF6B7280)
private val RouteCardBg = Color(0xFFF1F5FD)
private val FareCardBg = Color(0xFFF6F8FB)
private val PillBg = Color(0xFFF1F5FD)

private fun won(amount: Int): String = "%,d원".format(amount)

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
 *   **취소된 방에도 「모집 중」**이 떠 있었다. 17 목록은 4초 주기라 방을 고르는 사이에 마지막 멤버가
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

    Column(modifier = Modifier.fillMaxSize().background(CanvasBg)) {
        // 상단 헤더 (흰 배경)
        Column(modifier = Modifier.fillMaxWidth().background(MoyeotaColor.SurfaceCanvas)) {
            StatusBarSpacer()
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackArrowIcon(modifier = Modifier.clickable { onDismiss() })
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "합류할까요?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MoyeotaColor.InkPrimary,
                )
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // 경로 지도 스트립
            Box(modifier = Modifier.fillMaxWidth().height(172.dp)) {
                RouteStripMap(
                    modifier = Modifier.fillMaxSize(),
                    originPosition = originPosition,
                    destinationPosition = destinationPosition,
                    routePath = routePath,
                )
                // 시간·거리 둘 다 없으면 칩을 통째로 감춘다 — 틀린 값보다 없는 편이 낫다
                if (etaLabel != null) Box(
                    modifier = Modifier
                        .padding(16.dp)
                        .height(30.dp)
                        .shadow(4.dp, CircleShape, spotColor = Color(0x141B2A4A))
                        .background(MoyeotaColor.SurfaceCanvas, CircleShape)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = etaLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MoyeotaColor.InkPrimary,
                    )
                }
            }

            // 바텀시트
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 160.dp)
                    .shadow(14.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), spotColor = Color(0x141B2A4A))
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(MoyeotaColor.SurfaceCanvas),
            ) {
                Spacer(Modifier.height(10.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    SheetHandle()
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                ) {
                    Spacer(Modifier.height(14.dp))

                    // 상태 행
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusBadge(kind = badge.kind, text = badge.text)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = joinHeadcountLine(
                                joinedCount = joinedCount,
                                capacity = ride.capacity,
                                iAmMember = iAmMember,
                            ),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = GrayMute,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    RouteCard(
                        origin = ride.origin,
                        destination = ride.destination,
                        departureLabel = ride.departureLabel.takeIf { it.isNotBlank() },
                        pickupDistanceMeters = pickupDistanceMeters,
                        durationLabel = ride.estimatedMinutes?.let { "약 ${it}분" },
                    )

                    Spacer(Modifier.height(16.dp))
                    FareCard(
                        totalFare = ride.totalFare,
                        farePerPerson = ride.farePerPerson,
                        capacity = ride.capacity,
                    )

                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "이미 참여한 사람 · ${joinedCount}명",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = GrayMute,
                    )
                    Spacer(Modifier.height(8.dp))
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

                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "합류하면 바로 채팅방에 들어가요 · 1인 부담금은 정원 기준 예상 금액이에요",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = GrayAsh,
                        modifier = Modifier.padding(horizontal = 4.dp),
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
 * (17 목록이 4초 주기라 고르는 사이에 바뀐다). 그래서 문구를 「… 방」으로 적었다 — 지금 내가 들어갈
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

// ─── 조각 ───────────────────────────────────────────────────────────────────

@Composable
private fun RouteCard(
    origin: String,
    destination: String,
    /** 서버 출발 표기. 비어 있으면 null — 자리를 비워 둔다 */
    departureLabel: String?,
    pickupDistanceMeters: Double?,
    /** 예상 소요 시간 「약 N분」. 서버 값이 없으면 null */
    durationLabel: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(RouteCardBg, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RouteDot(color = MoyeotaColor.MarkerOrigin)
            Spacer(Modifier.width(12.dp))
            Text(
                text = origin,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MoyeotaColor.InkPrimary,
                modifier = Modifier.weight(1f),
            )
            if (departureLabel != null) {
                Text(
                    text = departureLabel,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MoyeotaColor.InkPrimary,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(11.dp), contentAlignment = Alignment.Center) {
                DashedVerticalLine()
            }
            Spacer(Modifier.width(12.dp))
            // 내 위치를 모르면 점선만 남는다 — 보행 시간 같은 지어낸 값을 채우지 않는다
            if (pickupDistanceMeters != null) {
                Text(
                    text = "내 위치에서 ${pickupDistanceLabel(pickupDistanceMeters)}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = GrayMute,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RouteDot(color = MoyeotaColor.MarkerDestination)
            Spacer(Modifier.width(12.dp))
            Text(
                text = destination,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MoyeotaColor.InkPrimary,
                modifier = Modifier.weight(1f),
            )
            if (durationLabel != null) {
                Text(
                    text = durationLabel,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MoyeotaColor.InkPrimary,
                )
            }
        }
    }
}

@Composable
private fun RouteDot(color: Color) {
    Box(Modifier.size(11.dp).background(color, CircleShape))
}

@Composable
private fun DashedVerticalLine() {
    Canvas(modifier = Modifier.size(width = 3.dp, height = 26.dp)) {
        drawLine(
            color = Color(0xFFC3CCDA),
            start = Offset(size.width / 2f, 0f),
            end = Offset(size.width / 2f, size.height),
            strokeWidth = size.width,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
        )
    }
}

@Composable
private fun FareCard(
    totalFare: Int,
    farePerPerson: Int,
    capacity: Int,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(FareCardBg, RoundedCornerShape(16.dp))
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        FareRow(label = "총 예상 요금", value = won(totalFare))
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(MoyeotaColor.Primary50, RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "합류 시 1인 부담",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MoyeotaColor.Primary600,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = won(farePerPerson),
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = MoyeotaColor.Primary500,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CheckIcon(color = GrayMute)
            Spacer(Modifier.width(6.dp))
            Text(
                text = "총 예상 요금을 정원 ${capacity}명으로 나눈 금액이에요",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = GrayMute,
            )
        }
    }
}

@Composable
private fun FareRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = TextMuteWarm,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MoyeotaColor.InkPrimary,
        )
    }
}

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
            .clip(RoundedCornerShape(12.dp))
            .then(if (clickable) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MemberAvatar(user = member, size = 40.dp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = member.displayNickname,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (clickable) MoyeotaColor.InkPrimary else GrayMute,
                )
                Spacer(Modifier.width(8.dp))
                if (member.isMe) MeBadge() else GrayPill(text = "동승자")
            }
            Text(
                text = if (clickable) "${member.rideCountLabel}$othersLabel" else "탈퇴한 회원$othersLabel",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = GrayMute,
            )
        }
        if (clickable) {
            ChevronRightIcon(color = GrayAsh)
        }
    }
}

@Composable
private fun GrayPill(text: String) {
    Box(
        modifier = Modifier
            .background(PillBg, CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = GraySlateDeep,
        )
    }
}

// ─── Canvas 아이콘·경로 (material-icons 미사용) ─────────────────────────────

@Composable
private fun CheckIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(14.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()
        drawLine(color, Offset(w * 0.15f, h * 0.55f), Offset(w * 0.42f, h * 0.8f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.42f, h * 0.8f), Offset(w * 0.85f, h * 0.25f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun ChevronRightIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(16.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()
        drawLine(color, Offset(w * 0.38f, h * 0.22f), Offset(w * 0.66f, h * 0.5f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.38f, h * 0.78f), Offset(w * 0.66f, h * 0.5f), stroke, StrokeCap.Round)
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
