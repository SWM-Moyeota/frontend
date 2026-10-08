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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeota.core.designsystem.component.BackArrowIcon
import com.moyeota.core.designsystem.component.NavigationBarSpacer
import com.moyeota.core.designsystem.component.NoticeBanner
import com.moyeota.core.designsystem.component.NoticeKind
import com.moyeota.core.designsystem.component.PrimaryCtaButton
import com.moyeota.core.designsystem.component.StatusBarSpacer
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.domain.model.Place
import com.moyeota.domain.model.FavoritePlace as SavedPlace

private val CanvasBg = Color(0xFFF5F7FA)
private val GrayDeep = Color(0xFF54637D)
private val GrayMute = Color(0xFF8A93A0)
private val GrayAsh = Color(0xFF9AA1AC)

/** 15 에서 지금 검색 입력을 받고 있는 필드 — 출발지·도착지가 같은 검색 UI 를 공유한다 */
enum class DestinationField { ORIGIN, DESTINATION }

/** 출발지와 도착지가 사실상 같은 자리로 볼 반경 — 합승이 성립하지 않는 거리 */
private const val SameSpotRadiusM = 50f

// 좌표가 없으면 판정하지 않는다(이름 비교가 남아 있다) — 근거 없는 차단이 더 나쁘다
private fun isSameSpot(originLatitude: Double?, originLongitude: Double?, destination: Place): Boolean {
    if (originLatitude == null || originLongitude == null) return false
    if (!originLatitude.isFinite() || !originLongitude.isFinite()) return false
    val result = FloatArray(1)
    android.location.Location.distanceBetween(
        originLatitude,
        originLongitude,
        destination.latitude,
        destination.longitude,
        result,
    )
    return result[0] <= SameSpotRadiusM
}

/**
 * 15 · 목적지 · 매칭 조건 [S10]
 *
 * 이동(디스크립션):
 * - 뒤로 → 14 (onBack)
 * - 출발지 행 탭 → 출발지 검색 모드 (onFieldFocus(ORIGIN)) — 결과에서 고르면 출발지 교체
 * - 도착지 행 탭/입력 → 도착지 검색 모드 (onFieldFocus(DESTINATION))
 * - 두 필드 모두 서버 장소 검색(GET /places) 결과를 공유한다 (onQueryChange → searchResults)
 * - 자주 가는 곳 카드 탭 → 활성 필드에 장소 선택 (좌표까지 확정)
 * - 최근 검색 목록은 두지 않는다 — 서버에 검색 기록 API 가 없다(develop 기준). 검색어가 비면 결과 영역도 비운다
 * - 검색 결과 행의 ★ → 자주 가는 곳 등록 (onAddFavorite)
 * - 「경로 확인하기」 → 16 도착지 확인 모달 (onConfirmRoute)
 *
 * 유효값 검증:
 * - 좌표가 있는 도착지를 고르기 전에는 CTA 비활성 (방 생성에 좌표가 필수)
 * - 출발지 = 도착지이면 「너무 가까워요」 로 차단 (이름이 같거나 50m 안)
 * - 출발지를 고르지 않으면 기기 실위치("현재 위치")를 쓴다. 실위치도 없으면([origin] null) 출발지 칸을
 *   비워 두고 CTA 를 막은 채 「현재 위치를 확인하거나 출발지를 검색해 주세요」를 안내한다 — 고정 좌표로 채우지 않는다
 */
@Composable
fun DestinationScreen(
    /** 출발지 이름. null = 실위치도 없고 고른 출발지도 없음 → 비워 두고 CTA 비활성 */
    origin: String? = null,
    originIsDefault: Boolean = true, // true = 사용자가 고르지 않은 기본 출발지 → "현재 위치" 라벨
    // 출발지 좌표 — 「너무 가까워요」 판정용. 실위치 출발지는 이름이 "현재 위치" 라 이름 비교만으로는
    // 같은 자리를 못 걸러낸다 (null 이면 이름 비교만 한다)
    originLatitude: Double? = null,
    originLongitude: Double? = null,
    originQuery: String = "",
    activeField: DestinationField = DestinationField.DESTINATION,
    onFieldFocus: (DestinationField) -> Unit = {},
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
    onPlaceSelect: (Place) -> Unit = {},
    onAddFavorite: (Place) -> Unit = {},
    onBack: () -> Unit = {},
    onConfirmRoute: (Place) -> Unit = {},
) {
    val originActive = activeField == DestinationField.ORIGIN
    // 활성 필드의 마커색 — 출발지는 Primary, 도착지는 MarkerDestination
    val activeMarkerColor = if (originActive) MoyeotaColor.Primary500 else MoyeotaColor.MarkerDestination
    val originMissing = origin == null
    val tooClose = selectedPlace != null && origin != null && (
        selectedPlace.name.trim() == origin.trim() ||
            isSameSpot(originLatitude, originLongitude, selectedPlace)
        )
    val ctaEnabled = selectedPlace != null && !originMissing && !tooClose

    Column(modifier = Modifier.fillMaxSize().imePadding().background(CanvasBg)) {
        StatusBarSpacer()

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

            // 출발지 행 — 비활성이면 선택된 이름만, 활성이면 도착지와 같은 검색 입력이 된다
            PlaceFieldRow(
                dotColor = MoyeotaColor.Primary500,
                focused = originActive,
                // 검색 모드가 아닐 때만 그림자 — 포커스 테두리와 겹치면 지저분해진다
                elevated = !originActive,
                onClick = { onFieldFocus(DestinationField.ORIGIN) },
            ) {
                if (originActive) {
                    SearchInput(
                        value = originQuery,
                        onValueChange = onQueryChange,
                        placeholder = "출발지를 검색해 주세요",
                        modifier = Modifier.weight(1f),
                    )
                    if (searchLoading) FieldSpinner()
                } else {
                    Text(
                        text = origin ?: "출발지를 검색해 주세요",
                        fontSize = 15.sp,
                        fontWeight = if (origin == null) FontWeight.Medium else FontWeight.Bold,
                        color = if (origin == null) GrayAsh else MoyeotaColor.InkPrimary,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        // 기본값일 때만 "현재 위치" — 사용자가 직접 고른 출발지는 "변경" 로 구분.
                        // 출발지가 비었으면 "검색" — 눌러서 채우라는 신호
                        text = when {
                            origin == null -> "검색"
                            originIsDefault -> "현재 위치"
                            else -> "변경"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = GrayMute,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // 도착지 입력 행
            PlaceFieldRow(
                dotColor = MoyeotaColor.MarkerDestination,
                focused = !originActive,
                elevated = originActive,
                onClick = { onFieldFocus(DestinationField.DESTINATION) },
            ) {
                SearchInput(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = "도착지를 입력해 주세요",
                    // 출발지 검색 중에는 도착지 칸을 편집 불가로 두고, 탭하면 포커스만 옮긴다
                    enabled = !originActive,
                    modifier = Modifier.weight(1f),
                )
                if (searchLoading && !originActive) FieldSpinner()
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

            // 검색 결과(서버) — 활성 필드 기준. 검색어가 비어 있으면 아무것도 그리지 않는다
            // (최근 검색 API 가 서버에 없어 더미 목록을 두지 않는다).
            val activeQuery = if (originActive) originQuery else query
            val showingSearch = activeQuery.isNotBlank()
            if (showingSearch) {
                Text(
                    text = if (originActive) "출발지 검색 결과" else "검색 결과",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = GrayMute,
                )
                Spacer(Modifier.height(10.dp))
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
                                Box(Modifier.size(9.dp).background(activeMarkerColor, CircleShape))
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
            if (originMissing) {
                Spacer(Modifier.height(10.dp))
                NoticeBanner(kind = NoticeKind.INFO, text = "현재 위치를 확인하거나 출발지를 검색해 주세요")
            }
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
        NavigationBarSpacer()
    }
}

/**
 * 출발지·도착지 필드의 공통 껍데기.
 * focused 면 파랑 테두리 — 두 필드 중 어디에 검색어가 들어가는지 이걸로만 구분한다.
 */
@Composable
private fun PlaceFieldRow(
    dotColor: Color,
    focused: Boolean,
    elevated: Boolean,
    onClick: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .then(if (elevated) Modifier.shadow(4.dp, shape, spotColor = Color(0x1A1B2A4A)) else Modifier)
            .clip(shape)
            .background(MoyeotaColor.SurfaceCanvas)
            .border(
                width = if (focused) 1.5.dp else 1.dp,
                color = if (focused) MoyeotaColor.Primary500 else MoyeotaColor.Hairline,
                shape = shape,
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun SearchInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(modifier = modifier, contentAlignment = Alignment.CenterStart) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MoyeotaColor.InkPrimary,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = GrayAsh,
            )
        }
    }
}

@Composable
private fun FieldSpinner() {
    Spacer(Modifier.size(8.dp))
    CircularProgressIndicator(
        modifier = Modifier.size(16.dp),
        strokeWidth = 2.dp,
        color = MoyeotaColor.Primary500,
    )
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

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun DestinationScreenPreview() {
    DestinationScreen()
}
