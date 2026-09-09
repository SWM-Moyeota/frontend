package com.moyeota.presentation.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeota.core.designsystem.component.MoyeotaBottomBar
import com.moyeota.core.designsystem.component.MoyeotaTab
import com.moyeota.core.designsystem.component.NaverMapView
import com.moyeota.core.designsystem.component.SheetHandle
import com.moyeota.core.designsystem.component.StatusBarSpacer
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.domain.model.Ride
import com.moyeota.presentation.core.ActiveRideBanner

// 와이어프레임 그레이 (core token 미정의 색 — 화면 재현용)
private val CanvasBg = Color(0xFFF5F7FA)
private val HeroBg = Color(0xFFF1F5FD)
private val GraySlate = Color(0xFF4B5563)
private val GrayDeep = Color(0xFF54637D)
private val GrayMute = Color(0xFF8A93A0)
private val GrayAsh = Color(0xFF9AA1AC)

// 상단 지도 노출 비율 (오버레이 시트 weight 1f 기준 상대값 → 화면의 약 30%)
private const val MAP_PEEK_WEIGHT = 0.45f

// 홈·목적지 화면 공용 더미 모델
data class FavoritePlace(val label: String, val address: String)

data class RecentPlace(val name: String, val address: String, val distanceLabel: String)

/**
 * 14 · 홈 — 어디로 갈까요 [S09]
 *
 * 이동(디스크립션):
 * - 상단 「{단계} · {목적지} 보기 ›」 배너 → 진행 중인 방의 단계 화면 21/25/26 (onActiveRideClick)
 *   — 진행 중인 방이 없으면([activeRide] null) 배너 자체가 없다
 * - 「목적지 검색」 바 탭 → 15 (onSearchClick)
 * - 「자주 가는 곳」 카드 탭 → 15, 도착지 자동 입력 (onFavoritePlaceClick)
 * - 「최근 목적지」 행 탭 → 15 (onRecentPlaceClick)
 * - 하단탭 합승/채팅/마이 → 17/24/35 (onTabSelect)
 * - [미연결] 자주 가는 곳 편집 · 최근 목적지 「전체」 (onRecentAllClick)
 *
 * 레이아웃: 와이어프레임 B1「풀스크린 지도」 — 네이버 지도가 배경 레이어이고
 * 기존 홈 UI(검색 카드·자주 가는 곳·최근 목적지)는 그 위 오버레이 시트로 올라간다.
 * 시트 위쪽 여백은 터치를 소비하지 않아 지도 팬/줌이 그대로 동작한다.
 */
@Composable
fun HomeScreen(
    // 로그인 사용자의 실명. 아직 못 받았거나 서버에 이름이 없으면 null — 인사말에서 이름을 뺀다.
    userName: String? = null,
    /** 지금 진행 중인 내 방. null 이면 지도 위 배너를 그리지 않는다 */
    activeRide: Ride? = null,
    favoritePlaces: List<FavoritePlace> = listOf(
        FavoritePlace("집", "서면 롯데"),
        FavoritePlace("학교", "부산대 정문"),
        FavoritePlace("알바", "센텀시티"),
    ),
    recentPlaces: List<RecentPlace> = listOf(
        RecentPlace("서면역 1번 출구", "부산진구 부전동", "6.2km"),
        RecentPlace("사상역 환승센터", "사상구 괘법동", "8.4km"),
        RecentPlace("부산역 광장", "동구 초량동", "11.0km"),
    ),
    onSearchClick: () -> Unit = {},
    onActiveRideClick: () -> Unit = {},
    onFavoritePlaceClick: (FavoritePlace) -> Unit = {},
    onRecentPlaceClick: (RecentPlace) -> Unit = {},
    onRecentAllClick: () -> Unit = {}, // 미연결
    onTabSelect: (MoyeotaTab) -> Unit = {},
) {
    // 지도를 가리는 오버레이(시트+하단탭+인디케이터) 높이 — 지도 contentPadding 으로 넘겨
    // 카메라 중심이 시트 뒤가 아니라 실제 보이는 상단 영역에 잡히게 한다
    var overlayHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    Box(modifier = Modifier.fillMaxSize().background(CanvasBg)) {
        // 배경 레이어 — 풀스크린 지도
        NaverMapView(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = with(density) { overlayHeightPx.toDp() }),
        )

        Column(modifier = Modifier.fillMaxSize()) {
            StatusBarSpacer()

            // 지도 노출 영역 (터치 미소비 → 팬·줌이 지도로 전달됨).
            // 진행 중인 방이 있으면 그 위에 복귀 배너 하나만 얹는다 — 배너 자기 높이 밖은
            // 여전히 지도의 것이다(17 합승 탭의 같은 배너와 같은 배치).
            Box(modifier = Modifier.weight(MAP_PEEK_WEIGHT).fillMaxWidth()) {
                if (activeRide != null) {
                    ActiveRideBanner(
                        ride = activeRide,
                        onClick = onActiveRideClick,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onSizeChanged { overlayHeightPx = it.height },
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(CanvasBg)
                        .verticalScroll(rememberScrollState()),
                ) {
                    // 드래그 핸들 — HeroBg 로 칠해 아래 HeroSection 과 연속돼 보이게 한다
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(HeroBg)
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        SheetHandle()
                    }

                    HeroSection(
                        userName = userName,
                        onSearchClick = onSearchClick,
                    )

                    Spacer(Modifier.height(24.dp))

                    // 자주 가는 곳
                    Text(
                        text = "자주 가는 곳",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = GrayMute,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    if (favoritePlaces.isEmpty()) {
                        // 서버에 등록된 즐겨찾기가 없을 때 (15 목적지 화면에서 ★ 로 등록한다)
                        Text(
                            text = "자주 가는 곳을 등록하면 여기서 바로 부를 수 있어요",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = GrayAsh,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                        ) {
                            favoritePlaces.take(3).forEachIndexed { index, place ->
                                FavoritePlaceCard(
                                    place = place,
                                    index = index,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onFavoritePlaceClick(place) },
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    // 최근 목적지
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "최근 목적지",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = GrayMute,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "전체",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = GrayAsh,
                            modifier = Modifier.clickable { onRecentAllClick() },
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .shadow(4.dp, RoundedCornerShape(18.dp), spotColor = Color(0x1A1B2A4A))
                            .clip(RoundedCornerShape(18.dp))
                            .background(MoyeotaColor.SurfaceCanvas),
                    ) {
                        recentPlaces.forEachIndexed { index, place ->
                            RecentPlaceRow(place = place, onClick = { onRecentPlaceClick(place) })
                            if (index != recentPlaces.lastIndex) {
                                HorizontalDivider(
                                    color = MoyeotaColor.Hairline,
                                    modifier = Modifier.padding(start = 42.dp, end = 18.dp),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                }

                MoyeotaBottomBar(selected = MoyeotaTab.HOME, onSelect = onTabSelect)
            }
        }
    }
}

@Composable
private fun HeroSection(
    userName: String?,
    onSearchClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().background(HeroBg)) {
        // 시트 핸들(하단 8dp 여백)과 합쳐 인사말 위 여백을 만든다
        Spacer(Modifier.height(18.dp))
        Text(
            // 이름을 모르면 "OO님" 자리를 통째로 빼고 인사만 남긴다 — 빈 이름이 드러나지 않는다.
            text = if (userName != null) "${userName}님, 좋은 저녁이에요" else "좋은 저녁이에요",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = GraySlate,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Text(
            text = "어디로 갈까요?",
            fontSize = 27.sp,
            fontWeight = FontWeight.Bold,
            color = MoyeotaColor.InkPrimary,
            lineHeight = 34.sp,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "목적지를 넣으면 같은 방향 사람을 찾아드려요",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = GrayDeep,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        Spacer(Modifier.height(20.dp))
        // 목적지 검색 바 → 15
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .height(64.dp)
                .shadow(8.dp, RoundedCornerShape(18.dp), spotColor = Color(0x29085AF5))
                .clip(RoundedCornerShape(18.dp))
                .background(MoyeotaColor.SurfaceCanvas)
                .clickable { onSearchClick() }
                .padding(start = 18.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchIcon()
            Spacer(Modifier.size(10.dp))
            Text(
                text = "목적지 검색",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = GrayMute,
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MoyeotaColor.Primary500, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                ArrowRightIcon(color = MoyeotaColor.TextOnDark)
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun FavoritePlaceCard(
    place: FavoritePlace,
    index: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val iconBg = listOf(Color(0xFFDDE7F7), Color(0xFFD6F0E4), Color(0xFFFBE7D6))
    val iconTint = listOf(Color(0xFF4A6FA5), Color(0xFF2F9E77), Color(0xFFC97B3D))
    Column(
        modifier = modifier
            .height(100.dp)
            .shadow(4.dp, RoundedCornerShape(18.dp), spotColor = Color(0x1A1B2A4A))
            .clip(RoundedCornerShape(18.dp))
            .background(MoyeotaColor.SurfaceCanvas)
            .clickable { onClick() }
            .padding(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(iconBg[index % iconBg.size], RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center,
        ) {
            when (index % 3) {
                0 -> HouseIcon(tint = iconTint[0])
                1 -> SchoolIcon(tint = iconTint[1])
                else -> BagIcon(tint = iconTint[2])
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = place.label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MoyeotaColor.InkPrimary,
        )
        Text(
            text = place.address,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = GrayMute,
        )
    }
}

@Composable
private fun RecentPlaceRow(place: RecentPlace, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(Color(0xFFC3CCDA), CircleShape),
        )
        Spacer(Modifier.size(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = place.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MoyeotaColor.InkPrimary,
            )
            Text(
                text = place.address,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = GrayMute,
            )
        }
        Text(
            text = place.distanceLabel,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = GrayMute,
        )
    }
}

// ─── 아이콘 (material-icons 미사용 — Canvas 직접 드로잉) ─────────────────────

@Composable
private fun SearchIcon(modifier: Modifier = Modifier, color: Color = Color(0xFF54637D)) {
    Canvas(modifier = modifier.size(22.dp)) {
        val w = size.width
        val strokeWidth = 2.dp.toPx()
        drawCircle(
            color = color,
            radius = w * 0.26f,
            center = Offset(w * 0.42f, w * 0.42f),
            style = Stroke(strokeWidth),
        )
        drawLine(color, Offset(w * 0.62f, w * 0.62f), Offset(w * 0.86f, w * 0.86f), strokeWidth, StrokeCap.Round)
    }
}

@Composable
private fun ArrowRightIcon(modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier = modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val strokeWidth = 2.dp.toPx()
        drawLine(color, Offset(w * 0.12f, h * 0.5f), Offset(w * 0.85f, h * 0.5f), strokeWidth, StrokeCap.Round)
        drawLine(color, Offset(w * 0.85f, h * 0.5f), Offset(w * 0.52f, h * 0.2f), strokeWidth, StrokeCap.Round)
        drawLine(color, Offset(w * 0.85f, h * 0.5f), Offset(w * 0.52f, h * 0.8f), strokeWidth, StrokeCap.Round)
    }
}

@Composable
private fun HouseIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.12f)
            lineTo(w * 0.92f, h * 0.45f)
            lineTo(w * 0.78f, h * 0.45f)
            lineTo(w * 0.78f, h * 0.88f)
            lineTo(w * 0.22f, h * 0.88f)
            lineTo(w * 0.22f, h * 0.45f)
            lineTo(w * 0.08f, h * 0.45f)
            close()
        }
        drawPath(path, tint, style = Stroke(1.6.dp.toPx(), join = StrokeJoin.Round))
    }
}

@Composable
private fun SchoolIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val strokeWidth = 1.6.dp.toPx()
        val cap = Path().apply {
            moveTo(w * 0.5f, h * 0.15f)
            lineTo(w * 0.95f, h * 0.4f)
            lineTo(w * 0.5f, h * 0.65f)
            lineTo(w * 0.05f, h * 0.4f)
            close()
        }
        drawPath(cap, tint, style = Stroke(strokeWidth, join = StrokeJoin.Round))
        val bottom = Path().apply {
            moveTo(w * 0.25f, h * 0.52f)
            lineTo(w * 0.25f, h * 0.75f)
            quadraticTo(w * 0.5f, h * 0.95f, w * 0.75f, h * 0.75f)
            lineTo(w * 0.75f, h * 0.52f)
        }
        drawPath(bottom, tint, style = Stroke(strokeWidth, join = StrokeJoin.Round))
    }
}

@Composable
private fun BagIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val strokeWidth = 1.6.dp.toPx()
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.12f, h * 0.35f),
            size = Size(w * 0.76f, h * 0.5f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
            style = Stroke(strokeWidth),
        )
        drawArc(
            color = tint,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.35f, h * 0.16f),
            size = Size(w * 0.3f, h * 0.36f),
            style = Stroke(strokeWidth, cap = StrokeCap.Round),
        )
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun HomeScreenPreview() {
    HomeScreen(userName = "김성윤")
}
