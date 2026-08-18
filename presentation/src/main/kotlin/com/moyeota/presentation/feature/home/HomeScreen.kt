package com.moyeota.presentation.feature.home

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeota.core.designsystem.component.AvatarCircle
import com.moyeota.core.designsystem.component.MoyeotaBottomBar
import com.moyeota.core.designsystem.component.MoyeotaTab
import com.moyeota.core.designsystem.component.StatusBarMock
import com.moyeota.core.designsystem.theme.MoyeotaColor

// 와이어프레임 그레이 (core token 미정의 색 — 화면 재현용)
private val CanvasBg = Color(0xFFF5F7FA)
private val HeroBg = Color(0xFFF1F5FD)
private val GraySlate = Color(0xFF4B5563)
private val GrayDeep = Color(0xFF54637D)
private val GrayMute = Color(0xFF8A93A0)
private val GrayAsh = Color(0xFF9AA1AC)

// 홈·목적지 화면 공용 더미 모델
data class FavoritePlace(val label: String, val address: String)

data class RecentPlace(val name: String, val address: String, val distanceLabel: String)

/**
 * 14 · 홈 — 어디로 갈까요 [S09]
 *
 * 이동(디스크립션):
 * - 「목적지 검색」 바 탭 → 15 (onSearchClick)
 * - 「자주 가는 곳」 카드 탭 → 15, 도착지 자동 입력 (onFavoritePlaceClick)
 * - 「최근 목적지」 행 탭 → 15 (onRecentPlaceClick)
 * - 수요 배너 → 17 합승 지도 (onDemandBannerClick)
 * - 하단탭 합승/채팅/마이 → 17/24/35 (onTabSelect)
 * - [미연결] 자주 가는 곳 편집 · 최근 목적지 「전체」 (onRecentAllClick)
 */
@Composable
fun HomeScreen(
    userName: String = "김OO",
    searchingCount: Int = 8,
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
    onFavoritePlaceClick: (FavoritePlace) -> Unit = {},
    onRecentPlaceClick: (RecentPlace) -> Unit = {},
    onDemandBannerClick: () -> Unit = {},
    onNoticeClick: () -> Unit = {},
    onRecentAllClick: () -> Unit = {}, // 미연결
    onTabSelect: (MoyeotaTab) -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxSize().background(CanvasBg)) {
        StatusBarMock()
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            HeroSection(
                userName = userName,
                searchingCount = searchingCount,
                onSearchClick = onSearchClick,
                onDemandBannerClick = onDemandBannerClick,
                onNoticeClick = onNoticeClick,
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
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                favoritePlaces.forEachIndexed { index, place ->
                    FavoritePlaceCard(
                        place = place,
                        index = index,
                        modifier = Modifier.weight(1f),
                        onClick = { onFavoritePlaceClick(place) },
                    )
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
        HomeIndicator()
    }
}

@Composable
private fun HeroSection(
    userName: String,
    searchingCount: Int,
    onSearchClick: () -> Unit,
    onDemandBannerClick: () -> Unit,
    onNoticeClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().background(HeroBg)) {
        // 공지 배너 행 (벨 + 업데이트 문구)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 19.dp, end = 16.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .shadow(4.dp, CircleShape, spotColor = Color(0x1A1B2A4A))
                        .background(MoyeotaColor.SurfaceCanvas, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    BellIcon()
                }
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .offset(x = 25.dp, y = 5.dp)
                        .background(MoyeotaColor.Waiting500, CircleShape),
                )
            }
            Spacer(Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onNoticeClick() },
            ) {
                Text(
                    text = "현재 1.0.0 버전이 업데이트 되었습니다.",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MoyeotaColor.Primary600,
                )
                Spacer(Modifier.size(6.dp))
                ChevronIcon(down = true, color = MoyeotaColor.Primary600)
            }
            Spacer(Modifier.weight(1f))
        }

        Spacer(Modifier.height(18.dp))
        Text(
            text = "${userName}님, 좋은 저녁이에요",
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
        // 수요 배너 → 17 합승 지도
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onDemandBannerClick() }
                .padding(horizontal = 19.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(width = 62.dp, height = 26.dp)) {
                AvatarCircle(size = 26.dp)
                AvatarCircle(size = 26.dp, modifier = Modifier.offset(x = 18.dp))
                AvatarCircle(size = 26.dp, modifier = Modifier.offset(x = 36.dp))
            }
            Spacer(Modifier.size(11.dp))
            Column {
                Row {
                    Text(
                        text = "지금 ${searchingCount}명",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MoyeotaColor.Primary500,
                    )
                    Text(
                        text = "이 같이 탈 사람을 찾고 있어요",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = GraySlate,
                    )
                }
                Text(
                    text = "서면 · 사상 방향이 많아요",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = GrayDeep,
                )
            }
            Spacer(Modifier.weight(1f))
            ChevronIcon(down = false, color = MoyeotaColor.Primary500)
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

// 홈 인디케이터 (와이어프레임 하단 검은 바)
@Composable
private fun HomeIndicator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MoyeotaColor.SurfaceCanvas)
            .padding(top = 8.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 135.dp, height = 5.dp)
                .background(MoyeotaColor.InkPrimary, CircleShape),
        )
    }
}

// ─── 아이콘 (material-icons 미사용 — Canvas 직접 드로잉) ─────────────────────

@Composable
private fun BellIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(17.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawArc(
            color = Color(0xFF54637D),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.22f, h * 0.16f),
            size = Size(w * 0.56f, h * 0.6f),
            style = stroke,
        )
        drawLine(Color(0xFF54637D), Offset(w * 0.22f, h * 0.46f), Offset(w * 0.22f, h * 0.72f), stroke.width, StrokeCap.Round)
        drawLine(Color(0xFF54637D), Offset(w * 0.78f, h * 0.46f), Offset(w * 0.78f, h * 0.72f), stroke.width, StrokeCap.Round)
        drawLine(Color(0xFF54637D), Offset(w * 0.12f, h * 0.72f), Offset(w * 0.88f, h * 0.72f), stroke.width, StrokeCap.Round)
        drawCircle(Color(0xFF54637D), radius = 1.4.dp.toPx(), center = Offset(w * 0.5f, h * 0.86f))
    }
}

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
private fun ChevronIcon(down: Boolean, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(14.dp)) {
        val w = size.width
        val h = size.height
        val strokeWidth = 1.8.dp.toPx()
        if (down) {
            drawLine(color, Offset(w * 0.2f, h * 0.35f), Offset(w * 0.5f, h * 0.65f), strokeWidth, StrokeCap.Round)
            drawLine(color, Offset(w * 0.8f, h * 0.35f), Offset(w * 0.5f, h * 0.65f), strokeWidth, StrokeCap.Round)
        } else {
            drawLine(color, Offset(w * 0.35f, h * 0.2f), Offset(w * 0.65f, h * 0.5f), strokeWidth, StrokeCap.Round)
            drawLine(color, Offset(w * 0.35f, h * 0.8f), Offset(w * 0.65f, h * 0.5f), strokeWidth, StrokeCap.Round)
        }
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
    HomeScreen()
}
