package com.moyeota.presentation.feature.matching

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeota.core.designsystem.component.BackArrowIcon
import com.moyeota.core.designsystem.component.NavigationBarSpacer
import com.moyeota.core.designsystem.component.NoticeKind
import com.moyeota.core.designsystem.component.SheetHandle
import com.moyeota.core.designsystem.component.StatusBadge
import com.moyeota.core.designsystem.component.StatusBarSpacer
import com.moyeota.core.designsystem.component.RouteStripMap
import com.moyeota.core.designsystem.component.decodePolyline
import com.moyeota.core.designsystem.component.latLngOrNull
import com.moyeota.core.designsystem.component.polylineDistanceMeters
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.presentation.core.buildRouteChipLabel
import com.moyeota.presentation.core.pickupDistanceLabel
import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RideStatus
import com.moyeota.domain.model.User
import com.moyeota.presentation.core.HostBadge
import com.moyeota.presentation.core.MeBadge
import com.moyeota.presentation.core.MemberAvatar
import com.moyeota.presentation.core.displayNickname
import com.moyeota.presentation.core.isWithdrawn
import com.moyeota.presentation.core.rideCountLabel
import com.moyeota.presentation.core.showsHostBadge

// 와이어프레임 그레이 (core token 미정의 색 — 화면 재현용)
private val CanvasBg = Color(0xFFF5F7FA)
private val GraySlate = Color(0xFF4B5563)
private val GrayMute = Color(0xFF8A93A0)
private val GrayAsh = Color(0xFF9AA1AC)
private val ChipBg = Color(0xFFF1F5FD)
private val CardSoft = Color(0xFFF6F8FB)
private val GrayButtonBg = Color(0xFFEEF1F6)
private val DashGray = Color(0xFFB9C3D6)

/**
 * 22 · 탑승 상세 — 모집 중 [S12]
 *
 * 이동(디스크립션):
 * - 뒤로 → 21 매칭 대기 (onBack)
 * - 동승자 행 탭 → 23 동승자 프로필 (onPartnerClick)
 * - 「나가기」 → 14 홈, 탑승 이탈 (onLeave). **모집 중([RideStatus.RECRUITING])일 때만 보인다.**
 *   그 밖의 단계에서는 버튼 대신 지금 어떤 방인지를 한 줄로 적는다([stageNotice]).
 *
 *   ⚠️ 서버는 정원이 찬 방([RideStatus.MATCHED] = 서버 COMPLETED)에서도 나가기를 **허용한다**
 *   (`ensureRecruiting()` 이 ACTIVE·COMPLETED 를 통과시킨다) — 21 매칭 대기는 거기서 「방 나가기」를
 *   보여 준다([canLeaveParty][com.moyeota.presentation.feature.matching.canLeaveParty]). 이 화면만
 *   RECRUITING 으로 좁혀 둔 것은 기사 모드 시절의 기준이 남은 것이고, 지금은 두 화면이 같은 방에 대해
 *   다른 말을 한다. 의도된 차이가 아니므로 둘 중 하나로 모아야 한다 — 다만 그건 문구 정리가 아니라
 *   **동작 변경**이라 이 작업(택시 코드 제거)에서 하지 않았다.
 * - [미연결] 없음
 *
 * 도메인 변경(2026-08-30 → 현재): 백엔드에서 **수동 출발**이 사라졌다 — 「이 인원으로 출발」이라는
 * 행위 자체가 없어져 CTA 를 제거했고, 21 매칭 대기와 같은 결정이다. 그 뒤 기사·배차까지 걷어내면서
 * **이 화면이 넘길 다음 화면은 아예 없어졌다**(25 배차 현황·26 운행 중 삭제). 여기서 나가는 길은
 * 뒤로(21) · 동승자 프로필(23) · 나가기(14 홈) 셋이다.
 *
 * 방장 표시(2026-10-15): 그때 함께 지웠던 방장 배지는 **되살렸다** — 근거가 달라졌다. 그때는
 * 서버에 방장 개념이 없어 앱이 `joinedAt` 최소값으로 추정하던 것이었고, 지금은 서버가 멤버마다
 * `host` 를 내려 준다(Backend #240). 21 매칭 대기와 **같은 배지·같은 판정**을 쓴다
 * ([com.moyeota.presentation.core.showsHostBadge]) — 조건 카드 한 번 탭으로 오가는 두 화면에서
 * 같은 사람이 달라 보이면 안 된다. 이 화면은 방장 **전용 동작**은 갖지 않는다(「합승 완료」는 21 에 있다).
 *
 * 서버 계약(2026-09): 멤버가 publicId·nickname·rideCount 를 함께 준다. 「나」 판정은 [User.isMe]
 * 하나로 끝난다 — 예전에는 앱이 자기 내부 Long id 를 몰라 고정값 1 과 비교하던 자리였다.
 * 평가 API 는 아직 없어 그 줄은 표시하지 않는다.
 *
 * 안내 문구(2026-10-13 → 현재): 정원이 찬 뒤 무슨 일이 일어나는지를 한 줄로 적는다. 한동안 이 줄이
 * 모드(`taxiEnabled`)에 따라 네 갈래로 갈렸는데, 기사·배차를 걷어내면서 **갈릴 것이 없어졌다** —
 * 기사는 오지 않고, 정원이 차면 채팅으로 만날 곳을 정한다. 21 매칭 대기의 안내 배너와 **같은 말**을
 * 써야 한다(조건 카드 한 번 탭으로 오가는 두 화면이다).
 *
 * 서버가 주지 않는 값(성별 조건·도착 시각·서비스 요금)은 지어내지 않고 요소를 두지 않는다.
 * 출발 표기는 [Ride.departureLabel] 이 있을 때만, 도착 쪽은 [Ride.estimatedMinutes] 가 있을 때만
 * 「약 N분」으로 보이고, 1인 부담은 서버 매핑 값 [Ride.farePerPerson] 을 그대로 쓴다.
 */
@Composable
fun RideDetailScreen(
    ride: Ride,
    /** 내 위치 → 탑승 위치 거리(m). 모르면 null 이고 그 줄을 감춘다 */
    pickupDistanceMeters: Double? = null,
    onBack: () -> Unit = {},
    onPartnerClick: (User) -> Unit = {},
    onLeave: () -> Unit = {},
) {
    // 지도 값 — 방 상세에서 나온다. 못 쓸 좌표면 latLngOrNull 이 null 을 돌려 자리표시자로 떨어진다
    val originPosition = latLngOrNull(ride.originLat, ride.originLng)
    val destinationPosition = latLngOrNull(ride.destinationLat, ride.destinationLng)
    val routePath = remember(ride.routePolyline) {
        ride.routePolyline?.let(::decodePolyline).orEmpty()
    }
    val routeKm = remember(routePath) {
        polylineDistanceMeters(routePath).takeIf { it > 0.0 }?.let { it / 1000.0 }
    }
    val etaLabel = buildRouteChipLabel(ride.estimatedMinutes, routeKm)

    val partners = ride.members.filter { !it.isMe }
    val departureLabel = ride.departureLabel.takeIf { it.isNotBlank() }
    val durationLabel = ride.estimatedMinutes?.let { "약 ${it}분" }

    Column(modifier = Modifier.fillMaxSize().background(CanvasBg)) {
        // 상단 흰색 헤더
        Column(modifier = Modifier.fillMaxWidth().background(MoyeotaColor.SurfaceCanvas)) {
            StatusBarSpacer()
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { BackArrowIcon() }
                Text(
                    text = "탑승 상세",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MoyeotaColor.InkPrimary,
                )
            }
        }

        // 지도 + 예상 시간 칩 — 16·20 과 같은 실지도 스트립(높이만 이 화면에 맞춘다)
        Box(modifier = Modifier.fillMaxWidth().height(160.dp)) {
            RouteStripMap(
                modifier = Modifier.fillMaxSize(),
                originPosition = originPosition,
                destinationPosition = destinationPosition,
                routePath = routePath,
            )
            if (etaLabel != null) Box(
                modifier = Modifier
                    .padding(start = 16.dp, top = 16.dp)
                    .shadow(4.dp, CircleShape, spotColor = Color(0x141B2A4A))
                    .background(MoyeotaColor.SurfaceCanvas, CircleShape)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Text(text = etaLabel, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MoyeotaColor.InkPrimary)
            }
        }

        // 바텀시트
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .shadow(14.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), spotColor = Color(0x141B2A4A))
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(MoyeotaColor.SurfaceCanvas),
        ) {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                SheetHandle()
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                Spacer(Modifier.height(10.dp))

                // 상태 배지 행
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(kind = NoticeKind.SUCCESS, text = "모집 중")
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "${ride.members.size}명 참여 · 목표 ${ride.capacity}명",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = GrayMute,
                    )
                }
                Spacer(Modifier.height(16.dp))

                // Group A · 경로
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ChipBg, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(5.dp))
                        Box(Modifier.size(11.dp).background(MoyeotaColor.MarkerOrigin, CircleShape))
                        DashedRouteLine(modifier = Modifier.height(30.dp).padding(vertical = 3.dp))
                        Box(Modifier.size(11.dp).background(MoyeotaColor.MarkerDestination, CircleShape))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = ride.origin,
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
                        // 내 위치를 모르면 이 줄은 비운다 — 보행 시간 같은 지어낸 값을 채우지 않는다
                        if (pickupDistanceMeters != null) {
                            Text(
                                text = "내 위치에서 ${pickupDistanceLabel(pickupDistanceMeters)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = GrayMute,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = ride.destination,
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
                Spacer(Modifier.height(16.dp))

                // Group B · 요금
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardSoft, RoundedCornerShape(16.dp))
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                ) {
                    FareRow(label = "총 예상 요금", value = wonLabel(ride.totalFare))
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MoyeotaColor.Primary50, RoundedCornerShape(14.dp))
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = "1인 부담", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MoyeotaColor.Primary600)
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = wonLabel(ride.farePerPerson),
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = MoyeotaColor.Primary500,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CheckSmallIcon(tint = MoyeotaColor.Success500)
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "총 예상 요금을 정원 ${ride.capacity}명으로 나눈 금액이에요",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = GrayMute,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))

                // 함께 타는 사람
                Text(
                    text = "함께 타는 사람 · ${partners.size}명",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = GrayMute,
                )
                Spacer(Modifier.height(8.dp))
                // 나까지 포함해 전원을 보여준다 — 위 개수는 나를 뺀 값이라 목록에 내 줄이 없으면
                // "나는 이 방에 있나"를 확인할 곳이 사라진다. 대신 내 줄은 배지로 구분하고 탭을 막는다.
                ride.members.forEach { member ->
                    // 탈퇴 회원과 나는 열어 볼 프로필이 없어 탭을 막는다.
                    val clickable = !member.isMe && !member.isWithdrawn
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .then(
                                if (clickable) {
                                    Modifier.clickable { onPartnerClick(member) }
                                } else {
                                    Modifier
                                },
                            )
                            .padding(vertical = 4.dp, horizontal = 2.dp),
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
                                    color = if (member.isWithdrawn) GrayMute else MoyeotaColor.InkPrimary,
                                )
                                if (member.isMe) {
                                    Spacer(Modifier.width(6.dp))
                                    MeBadge()
                                }
                                // 「방장」 — 21 매칭 대기와 같은 판정. 서버가 멤버마다 내려 주는
                                // 값이고(추정이 아니다), 폴백 때문에 내 줄에는 붙지 않는다.
                                if (member.showsHostBadge) {
                                    Spacer(Modifier.width(6.dp))
                                    HostBadge()
                                }
                            }
                            // 평가 수치는 평가 API 가 생길 때까지 쓰지 않는다 — 없는 수치를 지어내면
                            // 사용자가 그걸 근거로 사람을 고른다
                            Text(
                                text = if (member.isWithdrawn) "탈퇴한 회원" else member.rideCountLabel,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = GrayMute,
                            )
                        }
                        if (clickable) {
                            ChevronRightIcon()
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))

                // 정원이 찬 뒤 무슨 일이 일어나는지 (21 매칭 대기의 안내 배너와 **짝인 문구**) —
                // 한쪽만 고치면 조건 카드 한 번 탭으로 오가는 두 화면이 다른 말을 한다.
                Text(
                    text = if (ride.members.size >= ride.capacity) {
                        "같이 탈 사람이 다 모였어요. 채팅으로 만날 곳을 정해요"
                    } else {
                        "정원이 차면 채팅으로 만날 곳을 정해요"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = GrayAsh,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(12.dp))
            }

            // 남은 액션은 나가기 하나뿐 — 수동 출발 버튼은 도메인에서 사라졌다.
            // 매칭이 시작된 뒤에는 서버가 나가기를 막으므로 버튼 대신 현재 단계를 적는다.
            if (ride.status == RideStatus.RECRUITING) {
                GrayActionButton(
                    text = "나가기",
                    onClick = onLeave,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            } else {
                Text(
                    text = stageNotice(ride.status),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = GrayMute,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
        }
        NavigationBarSpacer(Modifier.background(MoyeotaColor.SurfaceCanvas))
    }
}

@Composable
private fun FareRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MoyeotaColor.TextMute)
        Spacer(Modifier.weight(1f))
        Text(text = value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MoyeotaColor.InkPrimary)
    }
}

private fun wonLabel(amount: Int): String = "%,d원".format(amount)

@Composable
private fun DashedRouteLine(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.width(3.dp)) {
        drawLine(
            color = DashGray,
            start = Offset(size.width / 2f, 0f),
            end = Offset(size.width / 2f, size.height),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 7f)),
        )
    }
}

// ─── 아이콘 (material-icons 미사용 — Canvas 직접 드로잉) ─────────────────────

@Composable
private fun CheckSmallIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(14.dp)) {
        val w = size.width
        val h = size.height
        val strokeWidth = 1.8.dp.toPx()
        drawLine(tint, Offset(w * 0.12f, h * 0.55f), Offset(w * 0.42f, h * 0.82f), strokeWidth, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.42f, h * 0.82f), Offset(w * 0.88f, h * 0.2f), strokeWidth, StrokeCap.Round)
    }
}

@Composable
private fun ChevronRightIcon(modifier: Modifier = Modifier, tint: Color = GrayAsh) {
    Canvas(modifier = modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val strokeWidth = 2.dp.toPx()
        drawLine(tint, Offset(w * 0.38f, h * 0.24f), Offset(w * 0.66f, h * 0.5f), strokeWidth, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.38f, h * 0.76f), Offset(w * 0.66f, h * 0.5f), strokeWidth, StrokeCap.Round)
    }
}

// 와이어프레임의 회색 보조 버튼 (나가기)
@Composable
private fun GrayActionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(GrayButtonBg)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = GraySlate)
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun RideDetailScreenPreview() {
    // 프리뷰 전용 더미 — 실행 경로에는 흘러가지 않는다
    RideDetailScreen(
        ride = Ride(
            id = "preview",
            origin = "출발지",
            destination = "도착지",
            departureLabel = "",
            capacity = 3,
            members = listOf(
                User("u-1", "동승자", "", 0.0, 3),
                User("me", "나", "", 0.0, 0, isMe = true),
            ),
            farePerPerson = 3000,
            totalFare = 9000,
            status = RideStatus.RECRUITING,
            estimatedMinutes = 20,
        ),
    )
}

/**
 * 나가기 버튼 자리에 대신 적는 현재 단계.
 *
 * 「왜 나갈 수 없는지」를 사용자가 알 수 있어야 한다 — 버튼만 사라지면 앱이 고장 난 것처럼 보인다.
 *
 * 기사·배차를 걷어내면서 `DISPATCHING`(「기사님을 찾는 중이라 나갈 수 없어요」)·`ONGOING`
 * (「운행 중이에요」) 두 줄이 사라졌다 — 서버가 그 status 를 보낼 수 없다.
 *
 * [RideStatus.MATCHED] 는 **「나갈 수 없다」고 말하지 않는다.** 서버는 정원이 찬 방에서도 나가기를
 * 허용하고(`ensureRecruiting` = ACTIVE·COMPLETED), 21 매칭 대기가 실제로 그 버튼을 띄운다 — 여기서
 * 못 나간다고 적으면 거짓이 된다. 이 화면이 그 길을 갖지 않는 것은 사실이므로, 그 사실만 적어
 * **나갈 수 있는 곳을 가리킨다**(이 화면은 21 의 조건 카드에서 한 번 탭으로 들어온 자리라
 * 「매칭 대기 화면」은 뒤로 한 번이다).
 *
 * `when` 에 `else` 를 두지 않는다 — 단계가 늘면 여기서 컴파일이 막혀야 한다. RECRUITING 은
 * 버튼이 보이는 경로라 호출부가 부르지 않지만, 분기를 빼면 그 보장을 잃으므로 함께 적어 둔다.
 */
private fun stageNotice(status: RideStatus): String = when (status) {
    RideStatus.MATCHED -> "정원이 다 찼어요. 나가려면 매칭 대기 화면에서 「방 나가기」를 눌러 주세요"
    RideStatus.CANCELED -> "취소된 탑승이에요"
    RideStatus.COMPLETED -> "완료된 탑승이에요"
    // 호출부가 RECRUITING 에서는 나가기 버튼을 그리므로 여기 오지 않는다
    RideStatus.RECRUITING -> "지금은 나갈 수 없어요"
}
