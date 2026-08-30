package com.moyeota.presentation.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeota.core.designsystem.component.BackArrowIcon
import com.moyeota.core.designsystem.component.NoticeBanner
import com.moyeota.core.designsystem.component.NoticeKind
import com.moyeota.core.designsystem.component.PrimaryCtaButton
import com.moyeota.core.designsystem.component.StatusBarMock
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.domain.model.Place
import com.moyeota.domain.model.FavoritePlace as SavedPlace

private val CanvasBg = Color(0xFFF5F7FA)
private val GrayDeep = Color(0xFF54637D)
private val GrayMute = Color(0xFF8A93A0)
private val GrayAsh = Color(0xFF9AA1AC)

/**
 * 15 · 목적지 · 매칭 조건 [S10]
 *
 * 이동(디스크립션):
 * - 뒤로 → 14 (onBack)
 * - 출발지 행 탭 → 지도에서 위치 조정 [미연결] (onOriginClick)
 * - 도착지 입력 → 서버 장소 검색(GET /places) 결과 노출 (onQueryChange → searchResults)
 * - 자주 가는 곳 카드 탭 → 도착지 선택 (좌표까지 확정)
 * - 최근 검색 행 탭 → 검색어 채움 (서버 검색 재실행) — 최근 검색 API 는 서버에 없어 더미 유지
 * - 검색 결과 행의 ★ → 자주 가는 곳 등록 (onAddFavorite)
 * - 「경로 확인하기」 → 16 도착지 확인 모달 (onConfirmRoute)
 *
 * 유효값 검증:
 * - 좌표가 있는 장소를 고르기 전에는 CTA 비활성 (방 생성에 좌표가 필수)
 * - 출발지 = 도착지이면 「너무 가까워요」 로 차단
 */
@Composable
fun DestinationScreen(
    origin: String = "부산대학교 정문",
    query: String = "",
    onQueryChange: (String) -> Unit = {},
    searchResults: List<Place> = emptyList(),
    searchLoading: Boolean = false,
    searchErrorMessage: String? = null,
    favoritePlaces: List<SavedPlace> = emptyList(),
    favoritesLoading: Boolean = false,
    favoritesErrorMessage: String? = null,
    selectedPlace: Place? = null,
    favoriteActionMessage: String? = null,
    recentSearches: List<RecentPlace> = listOf(
        RecentPlace("서면역 1번 출구", "부산진구 부전동", "6.2km"),
        RecentPlace("사상역 환승센터", "사상구 괘법동", "8.4km"),
        RecentPlace("부산역 광장", "동구 초량동", "11.0km"),
        RecentPlace("해운대역", "해운대구 우동", "18.6km"),
    ),
    onPlaceSelect: (Place) -> Unit = {},
    onAddFavorite: (Place) -> Unit = {},
    onBack: () -> Unit = {},
    onOriginClick: () -> Unit = {}, // 지도에서 출발지 조정 — 미연결
    onConfirmRoute: (Place) -> Unit = {},
) {
    val tooClose = selectedPlace != null && selectedPlace.name.trim() == origin.trim()
    val ctaEnabled = selectedPlace != null && !tooClose

    Column(modifier = Modifier.fillMaxSize().background(CanvasBg)) {
        StatusBarMock()

        // 헤더 — 뒤로 + 좌측 정렬 타이틀
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                BackArrowIcon()
            }
            Text(
                text = "어디로 갈까요?",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MoyeotaColor.InkPrimary,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // 출발지 행 — 현재 위치
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .shadow(4.dp, RoundedCornerShape(14.dp), spotColor = Color(0x1A1B2A4A))
                    .clip(RoundedCornerShape(14.dp))
                    .background(MoyeotaColor.SurfaceCanvas)
                    .clickable { onOriginClick() }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(11.dp).background(MoyeotaColor.Primary500, CircleShape))
                Spacer(Modifier.size(14.dp))
                Text(
                    text = origin,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MoyeotaColor.InkPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "현재 위치",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = GrayMute,
                )
            }

            Spacer(Modifier.height(8.dp))

            // 도착지 입력 행
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MoyeotaColor.SurfaceCanvas)
                    .border(1.5.dp, MoyeotaColor.Primary500, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(11.dp).background(MoyeotaColor.MarkerDestination, CircleShape))
                Spacer(Modifier.size(14.dp))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MoyeotaColor.InkPrimary,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (query.isEmpty()) {
                        Text(
                            text = "도착지를 입력해 주세요",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = GrayAsh,
                        )
                    }
                }
                if (searchLoading) {
                    Spacer(Modifier.size(8.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MoyeotaColor.Primary500,
                    )
                }
            }

            // 선택한 장소 확인 — 좌표까지 확정된 상태임을 보여준다
            if (selectedPlace != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "선택: ${selectedPlace.name} · ${selectedPlace.roadName}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MoyeotaColor.Primary600,
                )
            }

            Spacer(Modifier.height(26.dp))

            // 자주 가는 곳 — 서버(GET /users/me/favorite-places)
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "자주 가는 곳",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = GrayMute,
                )
                if (favoritesLoading) {
                    Spacer(Modifier.size(8.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = MoyeotaColor.Primary500,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            when {
                favoritesErrorMessage != null -> Text(
                    text = favoritesErrorMessage,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MoyeotaColor.Danger600,
                )
                favoritePlaces.isEmpty() && !favoritesLoading -> Text(
                    text = "아직 등록한 장소가 없어요. 검색 결과의 ★ 를 눌러 등록해 보세요",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = GrayAsh,
                )
                else -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    // 한 줄에 3개까지만 — 서버는 최대 10개를 내려준다
                    favoritePlaces.take(3).forEach { place ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .height(72.dp)
                                .shadow(4.dp, RoundedCornerShape(16.dp), spotColor = Color(0x1A1B2A4A))
                                .clip(RoundedCornerShape(16.dp))
                                .background(MoyeotaColor.SurfaceCanvas)
                                .clickable {
                                    onPlaceSelect(
                                        Place(place.name, place.roadName, place.latitude, place.longitude),
                                    )
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            StarIcon()
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = place.name,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MoyeotaColor.InkPrimary,
                                maxLines = 1,
                            )
                            Text(
                                text = place.roadName,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = GrayMute,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            if (favoriteActionMessage != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = favoriteActionMessage,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = GrayDeep,
                )
            }

            Spacer(Modifier.height(26.dp))

            // 검색 결과(서버) / 최근 검색(더미 — 서버 API 없음)
            val showingSearch = query.isNotBlank()
            Text(
                text = if (showingSearch) "검색 결과" else "최근 검색",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = GrayMute,
            )
            Spacer(Modifier.height(10.dp))
            if (showingSearch) {
                when {
                    searchErrorMessage != null -> Text(
                        text = searchErrorMessage,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MoyeotaColor.Danger600,
                    )
                    searchResults.isEmpty() && !searchLoading -> Text(
                        text = "검색 결과가 없어요",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = GrayAsh,
                    )
                    else -> Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(4.dp, RoundedCornerShape(18.dp), spotColor = Color(0x1A1B2A4A))
                            .clip(RoundedCornerShape(18.dp))
                            .background(MoyeotaColor.SurfaceCanvas),
                    ) {
                        searchResults.forEachIndexed { index, place ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPlaceSelect(place) }
                                    .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(9.dp).background(MoyeotaColor.MarkerDestination, CircleShape))
                                Spacer(Modifier.size(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = place.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MoyeotaColor.InkPrimary,
                                    )
                                    Text(
                                        text = place.roadName,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = GrayMute,
                                    )
                                }
                                // ★ → 자주 가는 곳 등록
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable { onAddFavorite(place) }
                                        .padding(10.dp),
                                ) {
                                    StarIcon(color = MoyeotaColor.Primary500)
                                }
                            }
                            if (index != searchResults.lastIndex) {
                                HorizontalDivider(
                                    color = MoyeotaColor.Hairline,
                                    modifier = Modifier.padding(start = 50.dp, end = 20.dp),
                                )
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(18.dp), spotColor = Color(0x1A1B2A4A))
                        .clip(RoundedCornerShape(18.dp))
                        .background(MoyeotaColor.SurfaceCanvas),
                ) {
                    recentSearches.forEachIndexed { index, place ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                // 좌표가 없는 더미 항목 — 검색어만 채워 서버 검색을 태운다
                                .clickable { onQueryChange(place.name) }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ClockIcon()
                            Spacer(Modifier.size(12.dp))
                            Column {
                                Text(
                                    text = place.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MoyeotaColor.InkPrimary,
                                )
                                Text(
                                    text = "${place.address} · ${place.distanceLabel}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = GrayMute,
                                )
                            }
                        }
                        if (index != recentSearches.lastIndex) {
                            HorizontalDivider(
                                color = MoyeotaColor.Hairline,
                                modifier = Modifier.padding(start = 50.dp, end = 20.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }

        // 하단 — 안내 · 오류 · CTA
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(
                text = "도착지를 넣으면 같은 방향 사람만 보여드려요",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = GrayAsh,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            if (tooClose) {
                Spacer(Modifier.height(10.dp))
                NoticeBanner(kind = NoticeKind.ERROR, text = "너무 가까워요")
            }
            Spacer(Modifier.height(14.dp))
            PrimaryCtaButton(
                text = "경로 확인하기",
                onClick = { selectedPlace?.let(onConfirmRoute) },
                enabled = ctaEnabled,
            )
        }
        DestinationHomeIndicator()
    }
}

@Composable
private fun DestinationHomeIndicator() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 135.dp, height = 5.dp)
                .background(MoyeotaColor.InkPrimary, CircleShape),
        )
    }
}

// ─── 아이콘 ──────────────────────────────────────────────────────────────

@Composable
private fun StarIcon(modifier: Modifier = Modifier, color: Color = GrayAsh) {
    Canvas(modifier = modifier.size(17.dp)) {
        val r = size.minDimension / 2f
        val c = center
        val path = Path()
        for (i in 0 until 10) {
            val radius = if (i % 2 == 0) r else r * 0.45f
            val angle = -Math.PI / 2 + i * Math.PI / 5
            val x = c.x + (radius * kotlin.math.cos(angle)).toFloat()
            val y = c.y + (radius * kotlin.math.sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, color, style = Stroke(1.4.dp.toPx(), join = StrokeJoin.Round))
    }
}

@Composable
private fun ClockIcon(modifier: Modifier = Modifier, color: Color = GrayAsh) {
    Canvas(modifier = modifier.size(18.dp)) {
        val w = size.width
        val strokeWidth = 1.5.dp.toPx()
        drawCircle(color, radius = w * 0.42f, center = center, style = Stroke(strokeWidth))
        drawLine(color, center, Offset(center.x, center.y - w * 0.24f), strokeWidth, StrokeCap.Round)
        drawLine(color, center, Offset(center.x + w * 0.18f, center.y + w * 0.1f), strokeWidth, StrokeCap.Round)
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun DestinationScreenPreview() {
    DestinationScreen()
}
