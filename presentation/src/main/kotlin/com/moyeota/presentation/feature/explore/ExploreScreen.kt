package com.moyeota.presentation.feature.explore

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeota.core.designsystem.component.AvatarCircle
import com.moyeota.core.designsystem.component.MapPlaceholder
import com.moyeota.core.designsystem.component.MoyeotaBottomBar
import com.moyeota.core.designsystem.component.MoyeotaTab
import com.moyeota.core.designsystem.component.SheetHandle
import com.moyeota.core.designsystem.component.StatusBarMock
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RideStatus
import com.moyeota.domain.model.User

// 와이어프레임 그레이 (core token 미정의 색 — 화면 재현용)
private val CanvasBg = Color(0xFFF5F7FA)
private val RoadColor = Color(0xFFC9D4E6)
private val GrayMute = Color(0xFF8A93A0)
private val GrayAsh = Color(0xFF9AA1AC)
private val ChipBorder = Color(0xFFE2E7EE)
private val BadgeGrayBg = Color(0xFFF3F6FA)

// 필터 칩 라벨
private const val FilterNearOrigin = "출발지 1km"
private const val FilterSeomyeon = "서면 방향"
private const val FilterFemaleOnly = "여성만"
private const val FilterTrio = "3인"
private const val FilterSoon = "곧 출발"

/** 바텀시트 3단계 — 17 지도(peek) / 18 지도+리스트(half) / 19 리스트(full) */
enum class ExploreSheetState { PEEK, HALF, FULL }

// 더미 파티 (19 와이어프레임 카드 그대로)
private fun dummyUser(id: String, name: String) = User(id, name, "학교 인증", 4.9, 12)

private val DefaultParties = listOf(
    Ride("ride-1", "부산대 정문", "서면역", "3분 후 출발 예정 · 6.2km", 3, listOf(dummyUser("u1", "김OO"), dummyUser("u2", "이OO")), 3600, 9600, RideStatus.RECRUITING),
    Ride("ride-2", "부산대 정문", "사상역", "6분 후 출발 예정 · 8.4km", 3, listOf(dummyUser("u3", "박OO"), dummyUser("u4", "최OO")), 4200, 12600, RideStatus.RECRUITING),
    Ride("ride-3", "장전역", "서면역", "8분 후 출발 예정 · 5.1km", 3, listOf(dummyUser("u5", "정OO"), dummyUser("u6", "한OO")), 3200, 9600, RideStatus.RECRUITING),
    Ride("ride-4", "부산대 정문", "부산역", "10분 후 출발 예정 · 11km", 3, listOf(dummyUser("u7", "조OO")), 5400, 16200, RideStatus.RECRUITING),
    Ride("ride-5", "온천장역", "서면역", "12분 후 출발 예정 · 7.3km", 2, listOf(dummyUser("u8", "윤OO")), 4800, 9600, RideStatus.RECRUITING),
)

// 「여성만」 방 (Ride 도메인 모델에 없는 속성 — 와이어프레임 재현용)
private val FemaleOnlyRideIds = setOf("ride-1", "ride-3", "ride-5")

// 조건 필터 — 「여성만」 「3인」 「곧 출발」만 실제 목록을 거른다.
// 「출발지 1km」 「서면 방향」은 기준 필터로 더미 목록이 이미 적용된 값(스펙 19).
private fun applyFilters(parties: List<Ride>, filters: Set<String>): List<Ride> =
    parties.filter { ride ->
        (FilterFemaleOnly !in filters || ride.id in FemaleOnlyRideIds) &&
            (FilterTrio !in filters || ride.capacity == 3) &&
            (FilterSoon !in filters || departureMinutes(ride) <= 5)
    }

private fun departureMinutes(ride: Ride): Int =
    ride.departureLabel.substringBefore("분").filter { it.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE

// 지도 마커 자리 (와이어프레임 좌표)
private data class MarkerSlot(val x: Dp, val y: Dp, val size: Dp, val count: Int)

private val PeekMarkerSlots = listOf(
    MarkerSlot(70.dp, 148.dp, 44.dp, 12),
    MarkerSlot(250.dp, 118.dp, 38.dp, 5),
    MarkerSlot(300.dp, 238.dp, 34.dp, 3),
    MarkerSlot(90.dp, 378.dp, 34.dp, 4),
    MarkerSlot(255.dp, 408.dp, 30.dp, 2),
)

private val HalfMarkerSlots = listOf(
    MarkerSlot(70.dp, 58.dp, 40.dp, 12),
    MarkerSlot(255.dp, 48.dp, 34.dp, 5),
    MarkerSlot(300.dp, 208.dp, 32.dp, 3),
)

/**
 * 17·18·19 · 합승 — 내 주변 [V07/V07b/V07c]
 *
 * 시트 상태 전환 (디스크립션):
 * - PEEK: 시트 위로 드래그 → HALF (18)
 * - HALF: 「지도 접기 ⌄」 → FULL (19) · 시트 아래로 드래그 → PEEK (17)
 * - FULL: 「지도 펼치기 ⌃」 / 「🗺 지도」 → HALF (18)
 *
 * 이동(디스크립션):
 * - 「진행 중 탑승 · 서면역 방향 보기 ›」 배너 → 34 내 탑승 (onOngoingRideClick)
 * - 지도 마커 탭 / 카드 「합류」 → 20 합류 확인 (onJoinParty)
 * - 「＋ 새 합승 방 만들기」 → 15 목적지 입력 (onCreateRoomClick, 미연결 — 기본 무동작)
 * - 하단탭 홈 / 채팅 / 마이 → 14 / 24 / 35 (onTabSelect)
 *
 * 검증·상태:
 * - 위치 권한 없으면 지도 대신 권한 요청 안내 (locationGranted)
 * - 후보 0건이면 peek 문구 자리에 빈 상태 + 조건 완화 제안
 * - 진행 중 탑승 없으면 상단 배너 숨김 (hasOngoingRide)
 * - 정원 찬 방(3/3)은 「합류」 비활성 + 「마감」 표기
 */
@Composable
fun ExploreScreen(
    parties: List<Ride> = DefaultParties,
    waitingCount: Int = 23,
    hasOngoingRide: Boolean = true,
    locationGranted: Boolean = true,
    initialSheetState: ExploreSheetState = ExploreSheetState.PEEK,
    onJoinParty: (Ride) -> Unit = {},
    onOngoingRideClick: () -> Unit = {},
    onCreateRoomClick: () -> Unit = {}, // 미연결 (→ 15 목적지 입력)
    onTabSelect: (MoyeotaTab) -> Unit = {},
) {
    var sheetState by remember { mutableStateOf(initialSheetState) }
    var filters by remember { mutableStateOf(setOf(FilterNearOrigin, FilterSeomyeon)) }
    val visibleParties = remember(parties, filters) { applyFilters(parties, filters) }
    val toggleFilter: (String) -> Unit = { label ->
        filters = if (label in filters) filters - label else filters + label
    }

    Column(modifier = Modifier.fillMaxSize().background(CanvasBg)) {
        StatusBarMock()

        // 타이틀 행 — FULL에서는 우측에 「🗺 지도」 (→ 18)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "합승 — 내 주변",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MoyeotaColor.InkPrimary,
            )
            Spacer(Modifier.weight(1f))
            if (sheetState == ExploreSheetState.FULL) {
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(CircleShape)
                        .background(MoyeotaColor.SurfaceCanvas)
                        .border(1.dp, ChipBorder, CircleShape)
                        .clickable { sheetState = ExploreSheetState.HALF }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "🗺 지도",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MoyeotaColor.Primary500,
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (sheetState) {
                ExploreSheetState.PEEK -> PeekContent(
                    parties = visibleParties,
                    waitingCount = waitingCount,
                    hasOngoingRide = hasOngoingRide,
                    locationGranted = locationGranted,
                    filters = filters,
                    onToggleFilter = toggleFilter,
                    onOngoingRideClick = onOngoingRideClick,
                    onMarkerClick = onJoinParty,
                    onRaise = { sheetState = ExploreSheetState.HALF },
                )

                ExploreSheetState.HALF -> HalfContent(
                    parties = visibleParties,
                    locationGranted = locationGranted,
                    filters = filters,
                    onToggleFilter = toggleFilter,
                    onJoinParty = onJoinParty,
                    onMarkerClick = onJoinParty,
                    onCreateRoomClick = onCreateRoomClick,
                    onCollapseMap = { sheetState = ExploreSheetState.FULL },
                    onLower = { sheetState = ExploreSheetState.PEEK },
                )

                ExploreSheetState.FULL -> FullContent(
                    parties = visibleParties,
                    waitingCount = waitingCount,
                    filters = filters,
                    onToggleFilter = toggleFilter,
                    onJoinParty = onJoinParty,
                    onCreateRoomClick = onCreateRoomClick,
                    onExpandMap = { sheetState = ExploreSheetState.HALF },
                )
            }
        }

        MoyeotaBottomBar(selected = MoyeotaTab.EXPLORE, onSelect = onTabSelect)
        HomeIndicator()
    }
}

// ─── 17 · 지도 (peek) ───────────────────────────────────────────────────────

@Composable
private fun PeekContent(
    parties: List<Ride>,
    waitingCount: Int,
    hasOngoingRide: Boolean,
    locationGranted: Boolean,
    filters: Set<String>,
    onToggleFilter: (String) -> Unit,
    onOngoingRideClick: () -> Unit,
    onMarkerClick: (Ride) -> Unit,
    onRaise: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (locationGranted) {
            ExploreMap(
                slots = PeekMarkerSlots,
                myLocationX = 178.dp,
                myLocationY = 308.dp,
                horizontalRoadFractions = listOf(0.31f, 0.70f),
                rides = parties,
                onMarkerClick = onMarkerClick,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LocationPermissionNotice(modifier = Modifier.fillMaxSize())
        }

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
            if (hasOngoingRide) {
                OngoingRideBanner(onClick = onOngoingRideClick)
                Spacer(Modifier.height(18.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(FilterSeomyeon, FilterFemaleOnly, FilterTrio, FilterSoon).forEach { label ->
                    FilterChip(
                        label = label,
                        selected = label in filters,
                        elevated = true,
                        onClick = { onToggleFilter(label) },
                    )
                }
            }
        }

        PeekSheet(
            waitingCount = waitingCount,
            isEmpty = parties.isEmpty(),
            onRaise = onRaise,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun PeekSheet(
    waitingCount: Int,
    isEmpty: Boolean,
    onRaise: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
            .shadow(14.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), spotColor = Color(0x1A000000))
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(MoyeotaColor.SurfaceCanvas)
            .dragToTransition(onDragUp = onRaise),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))
        SheetHandle()
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!isEmpty) {
                Box(Modifier.size(10.dp).background(MoyeotaColor.Primary500, CircleShape))
                Spacer(Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isEmpty) "지금 이 방향엔 대기가 없어요" else "이 방향으로 ${waitingCount}명이 대기 중",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = MoyeotaColor.InkPrimary,
                )
                Text(
                    text = if (isEmpty) "필터를 완화하면 후보가 늘어나요" else "위로 올리면 리스트, 목적지 정하면 바로 자동 매칭",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = GrayMute,
                )
            }
            ChevronUpIcon(
                color = GrayAsh,
                modifier = Modifier.clickable { onRaise() },
            )
        }
    }
}

// ─── 18 · 지도+리스트 (half) ────────────────────────────────────────────────

@Composable
private fun HalfContent(
    parties: List<Ride>,
    locationGranted: Boolean,
    filters: Set<String>,
    onToggleFilter: (String) -> Unit,
    onJoinParty: (Ride) -> Unit,
    onMarkerClick: (Ride) -> Unit,
    onCreateRoomClick: () -> Unit,
    onCollapseMap: () -> Unit,
    onLower: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (locationGranted) {
            ExploreMap(
                slots = HalfMarkerSlots,
                myLocationX = 178.dp,
                myLocationY = 128.dp,
                horizontalRoadFractions = listOf(0.46f),
                rides = parties,
                onMarkerClick = onMarkerClick,
                modifier = Modifier.fillMaxWidth().height(320.dp),
            )
        } else {
            LocationPermissionNotice(modifier = Modifier.fillMaxWidth().height(320.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 300.dp)
                .shadow(14.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), spotColor = Color(0x1A000000))
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(MoyeotaColor.SurfaceCanvas),
        ) {
            // 핸들 + 헤더 — 아래로 드래그 → 17 (peek)
            Column(
                modifier = Modifier.fillMaxWidth().dragToTransition(onDragDown = onLower),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(12.dp))
                SheetHandle()
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "반경 1km 내 · 가까운 순",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MoyeotaColor.InkPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "지도 접기 ⌄",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MoyeotaColor.Primary500,
                        modifier = Modifier.clickable { onCollapseMap() },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(FilterNearOrigin, FilterSeomyeon, FilterTrio, FilterSoon).forEach { label ->
                    FilterChip(
                        label = label,
                        selected = label in filters,
                        onClick = { onToggleFilter(label) },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))

            PartyList(
                parties = parties,
                onJoinParty = onJoinParty,
                showEndOfList = false,
                modifier = Modifier.weight(1f),
            )

            CreateRoomButton(
                onClick = onCreateRoomClick,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
            )
        }
    }
}

// ─── 19 · 리스트 (full) ─────────────────────────────────────────────────────

@Composable
private fun FullContent(
    parties: List<Ride>,
    waitingCount: Int,
    filters: Set<String>,
    onToggleFilter: (String) -> Unit,
    onJoinParty: (Ride) -> Unit,
    onCreateRoomClick: () -> Unit,
    onExpandMap: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "반경 1km 내 ${waitingCount}명 · 가까운 순",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MoyeotaColor.InkPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "지도 펼치기 ⌃",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MoyeotaColor.Primary500,
                modifier = Modifier.clickable { onExpandMap() },
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(FilterNearOrigin, FilterSeomyeon, FilterFemaleOnly, FilterSoon).forEach { label ->
                FilterChip(
                    label = label,
                    selected = label in filters,
                    onClick = { onToggleFilter(label) },
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = ChipBorder)
        Spacer(Modifier.height(12.dp))

        PartyList(
            parties = parties,
            onJoinParty = onJoinParty,
            showEndOfList = true,
            modifier = Modifier.weight(1f),
        )

        CreateRoomButton(
            onClick = onCreateRoomClick,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
        )
    }
}

// ─── 공용 조각 ──────────────────────────────────────────────────────────────

@Composable
private fun PartyList(
    parties: List<Ride>,
    onJoinParty: (Ride) -> Unit,
    showEndOfList: Boolean,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(parties.size) { index ->
            PartyCard(ride = parties[index], onJoin = onJoinParty)
        }
        if (parties.isEmpty()) {
            item { EmptyListNotice() }
        } else if (showEndOfList) {
            // 스펙 19: 마지막 페이지면 「이 방향은 여기까지예요」
            item {
                Text(
                    text = "이 방향은 여기까지예요",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = GrayAsh,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun PartyCard(
    ride: Ride,
    onJoin: (Ride) -> Unit,
    modifier: Modifier = Modifier,
) {
    val full = ride.members.size >= ride.capacity
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MoyeotaColor.SurfaceCanvas, RoundedCornerShape(16.dp))
            .border(1.dp, ChipBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarStack(count = ride.members.size)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = "${ride.origin} → ${ride.destination}",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MoyeotaColor.InkPrimary,
            )
            Text(
                text = ride.departureLabel,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = GrayMute,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CapacityBadge(text = "${ride.members.size}/${ride.capacity}명")
                if (ride.id in FemaleOnlyRideIds) {
                    GrayBadge(text = "여성만")
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        JoinButton(full = full, onClick = { onJoin(ride) })
    }
}

@Composable
private fun AvatarStack(count: Int) {
    val shown = count.coerceAtLeast(1).coerceAtMost(3)
    Box(modifier = Modifier.width(30.dp + 18.dp * (shown - 1)).height(30.dp)) {
        repeat(shown) { i ->
            AvatarCircle(size = 30.dp, modifier = Modifier.offset(x = 18.dp * i))
        }
    }
}

@Composable
private fun CapacityBadge(text: String) {
    Box(
        modifier = Modifier
            .background(MoyeotaColor.Waiting500, CircleShape)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(text = text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MoyeotaColor.TextOnDark)
    }
}

@Composable
private fun GrayBadge(text: String) {
    Box(
        modifier = Modifier
            .background(BadgeGrayBg, CircleShape)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(text = text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GrayMute)
    }
}

// 정원 찬 방(3/3)은 「합류」 비활성 + 「마감」 표기 (스펙 18)
@Composable
private fun JoinButton(full: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    if (full) {
        Box(
            modifier = Modifier
                .size(width = 48.dp, height = 34.dp)
                .background(MoyeotaColor.SurfaceSoft, shape),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "마감", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = GrayAsh)
        }
    } else {
        Box(
            modifier = Modifier
                .size(width = 48.dp, height = 34.dp)
                .clip(shape)
                .background(MoyeotaColor.Primary500)
                .clickable { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "합류", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MoyeotaColor.TextOnDark)
        }
    }
}

@Composable
private fun CreateRoomButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MoyeotaColor.Primary500)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "＋ 새 합승 방 만들기",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MoyeotaColor.TextOnDark,
        )
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
) {
    val base = if (elevated) {
        modifier.shadow(4.dp, CircleShape, spotColor = Color(0x14000000))
    } else {
        modifier
    }
    val withBg = base
        .clip(CircleShape)
        .background(if (selected) MoyeotaColor.Primary500 else MoyeotaColor.SurfaceCanvas)
    val withBorder = if (!selected && !elevated) {
        withBg.border(1.dp, ChipBorder, CircleShape)
    } else {
        withBg
    }
    Box(
        modifier = withBorder
            .clickable { onClick() }
            .height(if (elevated) 34.dp else 32.dp)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (selected) "$label ✓" else label,
            fontSize = if (elevated) 13.sp else 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) MoyeotaColor.TextOnDark else MoyeotaColor.InkPrimary,
        )
    }
}

@Composable
private fun OngoingRideBanner(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .shadow(8.dp, RoundedCornerShape(18.dp), spotColor = Color(0x29085AF5))
            .clip(RoundedCornerShape(18.dp))
            .background(MoyeotaColor.SurfaceCanvas)
            .clickable { onClick() }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "진행 중 탑승 · 서면역 방향",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = GrayMute,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "보기 ›",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = GrayMute,
        )
    }
}

// 위치 권한 없을 때 지도 대신 안내 (스펙 17)
@Composable
private fun LocationPermissionNotice(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(MoyeotaColor.SurfaceSoft),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "위치 권한이 필요해요",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MoyeotaColor.InkPrimary,
            )
            Text(
                text = "권한을 허용하면 내 주변 합승을 지도로 볼 수 있어요",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = GrayMute,
            )
        }
    }
}

@Composable
private fun EmptyListNotice() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "조건에 맞는 합승이 없어요",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MoyeotaColor.InkPrimary,
        )
        Text(
            text = "필터를 완화해 보세요",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = GrayMute,
        )
    }
}

// ─── 지도 (MapPlaceholder + Canvas 도로/마커) ───────────────────────────────

@Composable
private fun ExploreMap(
    slots: List<MarkerSlot>,
    myLocationX: Dp,
    myLocationY: Dp,
    horizontalRoadFractions: List<Float>,
    rides: List<Ride>,
    onMarkerClick: (Ride) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        MapPlaceholder(modifier = Modifier.fillMaxSize())
        Canvas(modifier = Modifier.fillMaxSize()) {
            val roadWidth = 14.dp.toPx()
            val radius = CornerRadius(4.dp.toPx())
            horizontalRoadFractions.forEach { fraction ->
                drawRoundRect(
                    color = RoadColor,
                    topLeft = Offset(-10.dp.toPx(), size.height * fraction),
                    size = Size(size.width + 20.dp.toPx(), roadWidth),
                    cornerRadius = radius,
                )
            }
            listOf(110.dp, 285.dp).forEach { x ->
                drawRoundRect(
                    color = RoadColor,
                    topLeft = Offset(x.toPx(), 0f),
                    size = Size(roadWidth, size.height),
                    cornerRadius = radius,
                )
            }
        }
        MyLocationMarker(modifier = Modifier.offset(x = myLocationX, y = myLocationY))
        slots.forEachIndexed { index, slot ->
            // 필터로 후보가 줄면 뒤 마커부터 숨김 (지도 마커 필터)
            val ride = rides.getOrNull(index) ?: return@forEachIndexed
            CountMarker(
                count = slot.count,
                size = slot.size,
                modifier = Modifier.offset(x = slot.x, y = slot.y),
                onClick = { onMarkerClick(ride) }, // 마커 탭 → 20 합류 확인
            )
        }
    }
}

@Composable
private fun CountMarker(
    count: Int,
    size: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(color = Color.White)
            drawCircle(color = MoyeotaColor.Primary500, radius = this.size.minDimension / 2f - 2.dp.toPx())
        }
        Text(
            text = "$count",
            fontSize = if (size >= 38.dp) 15.sp else 13.sp,
            fontWeight = FontWeight.Bold,
            color = MoyeotaColor.TextOnDark,
        )
    }
}

@Composable
private fun MyLocationMarker(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(36.dp)) {
        drawCircle(color = MoyeotaColor.Primary500.copy(alpha = 0.18f))
        drawCircle(color = Color.White, radius = 12.dp.toPx())
        drawCircle(color = MoyeotaColor.Primary500, radius = 10.dp.toPx())
    }
}

@Composable
private fun ChevronUpIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 2.2.dp.toPx()
        drawLine(color, Offset(w * 0.2f, h * 0.62f), Offset(w * 0.5f, h * 0.34f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.8f, h * 0.62f), Offset(w * 0.5f, h * 0.34f), stroke, StrokeCap.Round)
    }
}

// 홈 인디케이터 (와이어프레임 하단 검은 바)
@Composable
private fun HomeIndicator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MoyeotaColor.SurfaceCanvas)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 135.dp, height = 5.dp)
                .background(MoyeotaColor.InkPrimary, CircleShape),
        )
    }
}

// 시트 핸들 드래그 → 상태 전환 (위: PEEK→HALF · 아래: HALF→PEEK)
private fun Modifier.dragToTransition(
    onDragUp: (() -> Unit)? = null,
    onDragDown: (() -> Unit)? = null,
): Modifier = pointerInput(onDragUp, onDragDown) {
    var total = 0f
    val threshold = 24.dp.toPx()
    detectVerticalDragGestures(
        onDragStart = { total = 0f },
        onDragEnd = {
            if (total < -threshold) onDragUp?.invoke()
            if (total > threshold) onDragDown?.invoke()
        },
        onVerticalDrag = { change, dragAmount ->
            change.consume()
            total += dragAmount
        },
    )
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun ExploreScreenPeekPreview() {
    ExploreScreen(initialSheetState = ExploreSheetState.PEEK)
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun ExploreScreenHalfPreview() {
    ExploreScreen(initialSheetState = ExploreSheetState.HALF)
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun ExploreScreenFullPreview() {
    ExploreScreen(initialSheetState = ExploreSheetState.FULL)
}
