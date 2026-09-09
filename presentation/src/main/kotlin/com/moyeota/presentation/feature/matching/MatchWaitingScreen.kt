package com.moyeota.presentation.feature.matching

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeota.core.designsystem.component.BackArrowIcon
import com.moyeota.core.designsystem.component.MapOverlayPill
import com.moyeota.core.designsystem.component.MapPlaceholder
import com.moyeota.core.designsystem.component.MapRadiusCircle
import com.moyeota.core.designsystem.component.MapSheetScaffold
import com.moyeota.core.designsystem.component.NavigationBarSpacer
import com.moyeota.core.designsystem.component.NoticeBanner
import com.moyeota.core.designsystem.component.NoticeKind
import com.moyeota.core.designsystem.component.RouteMapView
import com.moyeota.core.designsystem.component.StatusBarSpacer
import com.moyeota.core.designsystem.component.circleBoundsPoints
import com.moyeota.core.designsystem.component.decodePolyline
import com.moyeota.core.designsystem.component.fitMapCamera
import com.moyeota.core.designsystem.component.latLngOrNull
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RideStatus
import com.moyeota.domain.model.User
import com.moyeota.presentation.core.MeBadge
import com.moyeota.presentation.core.OpenChatButton
import com.moyeota.presentation.core.location.UserCoordinates
import com.moyeota.presentation.core.MemberAvatar
import com.moyeota.presentation.core.displayNickname
import com.moyeota.presentation.core.isWithdrawn
import com.moyeota.presentation.core.rideCountLabel

// 와이어프레임 그레이 (core token 미정의 색 — 화면 재현용)
private val CanvasBg = Color(0xFFF5F7FA)
private val GraySlate = Color(0xFF4B5563)
private val GrayMute = Color(0xFF8A93A0)
private val GrayAsh = Color(0xFF9AA1AC)
private val CardSoft = Color(0xFFF6F8FB)
private val GrayButtonBg = Color(0xFFEEF1F6)
private val DividerGray = Color(0xFFE7EAF0)
private val ProgressTrack = Color(0xFFE6EAF0)

private val waitingRideDummy = Ride(
    id = "ride-21",
    origin = "부산대학교 정문",
    destination = "서면역 1번 출구",
    departureLabel = "지금 출발",
    capacity = 3,
    members = listOf(User("me", "부산가자", "", 0.0, 5, isMe = true)),
    farePerPerson = 3600,
    totalFare = 10800,
    status = RideStatus.RECRUITING,
)

/**
 * 21 · 매칭 대기 [S11]
 *
 * 이동(디스크립션):
 * - 「그만 찾기」 → 방 나가기(DELETE /matching/leave) 후 14 홈 (onCancelSearch — 뒤로가기도 동일 처리)
 *   둘 다 **확인 다이얼로그를 거친다** — 뒤로가기 한 번에 방이 날아가면 사용자가 놀란다(QA O-1).
 *   [onCancelSearch] 는 사용자가 「그만 찾기」를 확인한 뒤에만 호출된다.
 *
 *   **나가기는 모집 중([RideStatus.RECRUITING] = 서버 ACTIVE)에서만 가능하다.** 서버 `Party.leave` 가
 *   `ensureRecruiting()` 으로 막아, 정원이 차 기사 매칭이 시작되면 409 PARTY_CLOSED 가 온다.
 *   폴링이 MATCHING 을 감지해 25b 로 넘어가기까지 최대 4초가 비는데, 그 사이에도 누를 수 있으면
 *   실패하는 버튼이 된다 — 그래서 status 를 보고 **즉시** 버튼을 비활성 문구로 바꾸고,
 *   뒤로가기는 방을 유지한 채 홈으로만 보낸다([onExitKeepingParty]).
 * - 카드 탭 → 22 탑승 상세 (onCardClick)
 * - 「채팅 열기」 → 24 채팅방(독립 목적지, 뒤로가기로 여기 복귀) — [onOpenChat] 이 null 이 아닐 때만 그린다.
 *   채팅방은 **정원이 찬 뒤에** 생기므로 모집 중에는 대개 null 이다. 눌러도 열 것이 없는 버튼을 두지 않는다.
 * - 매칭 성사(서버 status 전이) → 25 배차 현황 — 화면이 아니라 Route 가 관찰해 넘긴다
 * - 매칭 조건·탐색 반경은 **읽기 전용**이다. 방을 만들 때(16) 정해진 값이고 바꾸는 API 도 없다 —
 *   방장 개념이 사라진 뒤로 「누가 조건을 고치는가」에 답이 없어졌다. 눌러도 아무 일 없는
 *   「수정」 버튼을 두는 대신 카드 아래 한 줄로 이유를 적는다.
 *
 * 도메인 변경(2026-08-30): 「전원 준비 → 방장이 출발」 흐름이 백엔드에서 사라졌다.
 * `/matching/ready`, `/matching/start` 가 삭제되고 **정원이 차면 서버가 스스로 기사 매칭을 시작**한다.
 * 그래서 이 화면에는 준비/매칭 시작 버튼이 없고, 인원 현황과 자동 매칭 안내만 남는다.
 *
 * 서버 계약(2026-09): `members[]` 가 닉네임·탑승 횟수·publicId 를 함께 주면서, 기다리는 동안
 * **누가 모였는지**를 실제로 보여줄 수 있게 됐다. 내 줄은 `isMe`(publicId == 세션 uuid)로
 * 판정한다 — 예전처럼 고정 memberId 1 과 비교하지 않는다.
 *
 * 레이아웃(2026-09-08): 배경이 레이더 애니메이션에서 **실지도**로 바뀌었다. 출발지 마커 +
 * 탐색 반경 원 + 내 위치 파란 점을 그려, 「어느 범위에서 사람을 찾고 있는지」를 실제로 보여 준다.
 * 시트는 16 도착지 확인과 같은 [MapSheetScaffold] 로 접었다 폈다 한다 —
 * 접힘 = 헤드라인 · 진행바 · 「그만 찾기」만 남고 지도가 넓어진다.
 */
@Composable
fun MatchWaitingScreen(
    ride: Ride = waitingRideDummy,
    foundCount: Int = 1,
    conditionLabel: String = "3인",
    // 실제 방의 반경. Route 가 [Ride] 에서 뽑아 넘긴다 — 예전엔 "1km" 가 하드코딩돼 있었다(QA D-3)
    radiusLabel: String = "—",
    /** 내 현재 위치(파란 점). 권한이 없거나 아직 못 받았으면 null — 지도에 점을 찍지 않는다 */
    myLocation: UserCoordinates? = null,
    actionInProgress: Boolean = false,
    actionErrorMessage: String? = null,
    onCancelSearch: () -> Unit = {},
    /** 방을 **유지한 채** 화면만 벗어난다. 나가기가 막힌 단계의 뒤로가기가 여기로 온다 */
    onExitKeepingParty: () -> Unit = {},
    onCardClick: () -> Unit = {},
    /** 이 방의 채팅방을 연다. **채팅방이 아직 없으면 null** — 버튼 자체를 그리지 않는다 */
    onOpenChat: (() -> Unit)? = null,
) {
    // 나가기 확인 다이얼로그. 화면 안에서만 쓰는 UI 상태라 ViewModel 로 올리지 않는다
    // (12 마이페이지의 로그아웃 확인과 같은 패턴).
    var cancelConfirming by remember { mutableStateOf(false) }

    // 서버가 나가기를 허용하는 단계인가(ACTIVE = 모집 중). KDoc 참고.
    val canLeave = ride.status == RideStatus.RECRUITING

    // 정원 도달 = 서버가 기사 매칭을 시작하는 시점. 문구·진행바가 이 경계로 갈린다.
    val isFull = foundCount >= ride.capacity
    // 나를 뺀 인원. members 가 비어 있는 응답(더미 경로)에서는 0 이다.
    val otherCount = ride.members.count { !it.isMe }
    val progress = if (ride.capacity > 0) {
        (foundCount.toFloat() / ride.capacity).coerceIn(0f, 1f)
    } else {
        0f
    }

    // 지도에 그릴 값들. 4초 폴링마다 ride 인스턴스는 새로 오지만 좌표·폴리라인 값은 같으므로
    // (LatLng 은 값 비교) 아래 remember 들이 다시 돌지 않는다 — 카메라가 폴링마다 튀지 않는 이유다.
    val originPosition = latLngOrNull(ride.originLat, ride.originLng)
    val destinationPosition = latLngOrNull(ride.destinationLat, ride.destinationLng)
    val myPosition = latLngOrNull(myLocation?.latitude, myLocation?.longitude)
    val routePath = remember(ride.routePolyline) {
        ride.routePolyline?.let(::decodePolyline).orEmpty()
    }
    // 탐색 반경 원 — 서버가 방에 박아 둔 출발지 반경이 원본이다. 21 의 「수정」으로 값이 바뀌면
    // 다음 폴링에서 새 반경이 내려오고 원도 따라 커진다(화면이 따로 캐시하지 않는다).
    val radiusMeters = ride.departureRadiusMeters?.toDouble()
    val radiusCircle = remember(originPosition, radiusMeters) {
        if (originPosition != null && radiusMeters != null && radiusMeters > 0.0) {
            MapRadiusCircle(originPosition, radiusMeters)
        } else {
            null
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(CanvasBg)) {
        StatusBarSpacer()

        // 헤더 — 뒤로가기는 탐색 취소와 동일 (진행 화면 이탈 = 14 홈)
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { if (canLeave) cancelConfirming = true else onExitKeepingParty() },
            ) { BackArrowIcon() }
            Text(
                text = "같이 탈 사람 찾는 중",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MoyeotaColor.InkPrimary,
            )
        }

        // 지도(헤더 아래 전부) 위에 드래그 시트 — 16 도착지 확인과 같은 메커니즘
        MapSheetScaffold(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            // 16 보다 지도를 더 남긴다. 21 의 펼침 콘텐츠(안내 · 모인 사람 · 조건 카드)는
            // 16 의 매칭 조건 카드보다 짧아서, 16 과 같은 160dp 로 두면 상세 영역이 남아
            // 조건 카드와 「그만 찾기」 사이가 휑하게 빈다. 남는 만큼 지도에 준다.
            // 상세 콘텐츠(안내 배너 · 모인 사람 N줄 · 조건 카드 4줄 · 안내 문구)가 멤버가 늘수록
            // 길어진다. 실기에서 2명일 때 「탐색 반경」 줄과 안내 문구가 잘려 나가는 것을 확인해
            // 16 과 같은 160dp 로 되돌렸다 — 펼침은 정보를 보는 상태이고, 지도는 접힘이 맡는다.
            mapRevealHeight = 160.dp,
            // 접힘 = 핸들 + 헤드라인 + 인원 문구 + 진행바 + 「그만 찾기」
            collapsedSheetHeight = 196.dp,
            background = { sheet ->
                val widthDp = sheet.containerWidth.value
                val visibleMapHeightDp = sheet.visibleMapHeight.value
                // 펼침(지도 160dp)에서는 **출발지와 탐색 반경 원**이 들어오게,
                // 접힘(지도 확장)에서는 **출발–도착 전체**가 들어오게 맞춘다.
                // 앵커가 바뀔 때만 값이 변하므로 사용자가 손으로 잡아 둔 줌·중심을 덮어쓰지 않는다.
                val fitted = remember(
                    sheet.expanded,
                    originPosition,
                    destinationPosition,
                    routePath,
                    radiusMeters,
                    widthDp,
                    visibleMapHeightDp,
                ) {
                    val points = if (sheet.expanded && originPosition != null && radiusMeters != null) {
                        // 중심 좌표 하나만 넣으면 원이 잘린다 — 외접 사각형 꼭짓점을 넣는다
                        circleBoundsPoints(originPosition, radiusMeters)
                    } else {
                        val whole = routePath + listOfNotNull(originPosition, destinationPosition)
                        // 도착지·경로가 아직 없으면(목록 응답 등) 반경 원 기준으로 떨어진다
                        if (whole.size >= 2) {
                            whole
                        } else if (originPosition != null && radiusMeters != null) {
                            circleBoundsPoints(originPosition, radiusMeters)
                        } else {
                            whole
                        }
                    }
                    fitMapCamera(points = points, widthDp = widthDp, heightDp = visibleMapHeightDp)
                }
                // 시트에 가리는 만큼을 지도에 알려 카메라 중심이 보이는 영역 기준으로 잡히게 한다
                val mapContentPadding = remember(sheet.settledSheetHeight) {
                    PaddingValues(bottom = sheet.settledSheetHeight)
                }

                if (originPosition != null) {
                    RouteMapView(
                        modifier = Modifier.fillMaxSize(),
                        routePath = routePath,
                        originPosition = originPosition,
                        destinationPosition = destinationPosition,
                        radiusCircle = radiusCircle,
                        myPosition = myPosition,
                        center = fitted?.first ?: originPosition,
                        zoom = fitted?.second ?: 14.0,
                        contentPadding = mapContentPadding,
                    )
                } else {
                    // 좌표 없는 방(목록 응답으로 만든 Ride 등) — 지도 대신 자리표시자
                    MapPlaceholder(modifier = Modifier.fillMaxSize())
                }

                // 레이더 애니메이션을 대신하는 상태 배지. 지도가 배경이 된 이상 큰 레이더는
                // 지도를 가리기만 해서 「찾는 중」이라는 사실만 남긴다.
                MapOverlayPill(modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) {
                    SearchingBadgeContent(isFull = isFull)
                }
            },
            sheetTop = {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)) {
                    Text(
                        text = if (isFull) "정원이 다 찼어요" else "보통 2분 안에 모여요",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MoyeotaColor.InkPrimary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        // 「나 제외」 인원은 isMe 로 센다 — 서버가 publicId 를 주기 전에는 셀 수 없던 값이다
                        text = if (otherCount > 0) {
                            "지금 같은 방향 ${foundCount}명(나 외 ${otherCount}명) · 목표 ${ride.capacity}명"
                        } else {
                            "지금 같은 방향 ${foundCount}명 · 목표 ${ride.capacity}명"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = GrayMute,
                    )
                    Spacer(Modifier.height(14.dp))

                    // 진행 바 — 서버가 내려준 실제 인원/정원 비율
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .background(ProgressTrack, CircleShape),
                    ) {
                        if (progress > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress)
                                    .height(6.dp)
                                    .background(MoyeotaColor.Primary500, CircleShape),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            },
            sheetDetail = {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    // 앱이 매칭을 트리거하지 않는다는 사실을 사용자에게 알려주는 유일한 지점
                    NoticeBanner(
                        kind = if (isFull) NoticeKind.WAITING else NoticeKind.INFO,
                        text = if (isFull) {
                            "곧 기사님을 찾기 시작해요"
                        } else {
                            "정원이 차면 기사님을 자동으로 찾아요"
                        },
                    )
                    Spacer(Modifier.height(16.dp))

                    // 지금까지 모인 사람 — 서버 members[] 를 그대로 그린다(나 포함).
                    // 대기 화면에서 가장 궁금한 것이 "누가 같이 타나"라 조건 카드보다 위에 둔다.
                    if (ride.members.isNotEmpty()) {
                        Text(
                            text = "지금 모인 사람",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = GrayMute,
                        )
                        Spacer(Modifier.height(8.dp))
                        ride.members.forEach { member ->
                            WaitingMemberRow(member = member)
                        }
                        Spacer(Modifier.height(14.dp))
                    }

                    // 조건이 왜 고정인지 한 줄로. **카드 위**에 둔다 — 멤버가 늘면 상세 영역이
                    // 스크롤되는데, 아래에 두면 이 설명이 가장 먼저 화면 밖으로 나가 「수정 버튼이
                    // 왜 없지」에 답할 기회를 잃는다. 접힘 상태에서는 상세 영역째 사라진다.
                    Text(
                        text = "매칭 조건은 방을 만들 때 정해지고 찾는 중엔 바꿀 수 없어요",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = GrayAsh,
                    )
                    Spacer(Modifier.height(8.dp))
                    // 조건 카드 — 탭 시 22 탑승 상세
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(CardSoft)
                            .clickable { onCardClick() }
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                    ) {
                        ConditionRow(label = "출발지", value = ride.origin)
                        HorizontalDivider(color = DividerGray)
                        ConditionRow(label = "도착지", value = ride.destination)
                        HorizontalDivider(color = DividerGray)
                        ConditionRow(label = "매칭 조건", value = conditionLabel)
                        HorizontalDivider(color = DividerGray)
                        ConditionRow(label = "탐색 반경", value = radiusLabel)
                    }
                    Spacer(Modifier.height(16.dp))
                }
            },
            sheetFooter = {
                if (actionErrorMessage != null) {
                    NoticeBanner(
                        kind = NoticeKind.ERROR,
                        text = actionErrorMessage,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }

                // 채팅방이 생긴 뒤에만 뜬다. 채팅 **탭**이 아니라 채팅방을 스택에 쌓아 열기 때문에
                // 뒤로가기 한 번이면 이 대기 화면으로 돌아온다.
                if (onOpenChat != null) {
                    OpenChatButton(
                        onClick = onOpenChat,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                }

                // 남은 액션은 나가기 하나뿐 — 매칭 시작·준비 버튼은 도메인에서 사라졌다.
                // 접힘 상태에서도 보이도록 시트 하단에 고정한다.
                // 정원이 차 매칭이 시작되면 서버가 나가기를 막으므로 버튼을 비활성 문구로 바꾼다.
                GrayActionButton(
                    text = when {
                        !canLeave -> "기사님을 찾는 중…"
                        actionInProgress -> "나가는 중…"
                        else -> "그만 찾기"
                    },
                    onClick = { cancelConfirming = true },
                    enabled = canLeave && !actionInProgress,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(12.dp))
                NavigationBarSpacer()
            },
        )
    }

    // 뒤로가기·「그만 찾기」 공통 확인 — 확인해야만 실제로 방을 나간다
    if (cancelConfirming) {
        AlertDialog(
            onDismissRequest = { cancelConfirming = false },
            title = { Text(text = "매칭을 그만둘까요?", fontSize = 17.sp, fontWeight = FontWeight.Bold) },
            text = { Text(text = "지금 나가면 이 방에서 빠지고 처음부터 다시 찾아야 해요", fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = {
                    cancelConfirming = false
                    onCancelSearch()
                }) {
                    Text(text = "그만 찾기", fontWeight = FontWeight.Bold, color = MoyeotaColor.Danger500)
                }
            },
            dismissButton = {
                TextButton(onClick = { cancelConfirming = false }) {
                    Text(text = "계속 찾기", color = MoyeotaColor.TextMute)
                }
            },
            containerColor = MoyeotaColor.SurfaceCanvas,
        )
    }
}

// 대기 화면의 멤버 한 줄 — 아바타(이니셜) · 닉네임(+나 배지) · 탑승 횟수.
// 여기서는 프로필로 들어가지 않는다(23 진입은 20·22 에서만) — 대기 중 흐름을 끊지 않기 위해서다.
@Composable
private fun WaitingMemberRow(member: User) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MemberAvatar(user = member, size = 36.dp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = member.displayNickname,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (member.isWithdrawn) GrayMute else MoyeotaColor.InkPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (member.isMe) {
                    Spacer(Modifier.width(6.dp))
                    MeBadge()
                }
            }
            Text(
                text = if (member.isWithdrawn) "탈퇴한 회원" else member.rideCountLabel,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = GrayMute,
            )
        }
    }
}

@Composable
private fun ConditionRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MoyeotaColor.TextMute)
        Spacer(Modifier.width(12.dp))
        // 출발지·도착지 이름은 16 에서 역지오코딩된 전체 주소일 수 있다(「부산광역시 …63번길 2 …」).
        // 남는 폭을 다 쓰되 라벨을 밀어내지 않도록 weight 로 잡고, 넘치면 말줄임한다.
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MoyeotaColor.InkPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 지도 위 「찾는 중」 배지 — 예전 220dp 레이더 애니메이션을 대신한다.
 *
 * 레이더는 **삭제**했다. 배경이 실지도가 된 뒤로는 동심원이 지도를 가리기만 할 뿐,
 * 「주변에서 찾고 있다」는 정보는 이제 지도 위의 **실제 탐색 반경 원**이 훨씬 정확하게 보여 준다.
 * 남길 가치가 있던 건 「지금도 돌아가고 있다」는 신호 하나라, 점 하나의 펄스로 줄였다.
 */
@Composable
private fun SearchingBadgeContent(isFull: Boolean) {
    val transition = rememberInfiniteTransition(label = "searching")
    val pulse by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing)),
        label = "pulse",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = if (isFull) {
                        MoyeotaColor.Primary500
                    } else {
                        MoyeotaColor.Primary500.copy(alpha = pulse)
                    },
                    shape = CircleShape,
                ),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (isFull) "정원이 다 찼어요" else "같이 탈 사람 찾는 중",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MoyeotaColor.InkPrimary,
        )
    }
}

// 와이어프레임의 회색 보조 버튼 (그만 찾기)
@Composable
private fun GrayActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(GrayButtonBg)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) GraySlate else MoyeotaColor.TextAsh,
        )
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun MatchWaitingScreenPreview() {
    MatchWaitingScreen()
}
