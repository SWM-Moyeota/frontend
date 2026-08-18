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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
 * - 도착지 행 탭 → 자동완성 목록 노출 (같은 화면 내 — 입력 필드)
 * - 자주 가는 곳 / 최근 검색 행 탭 → 도착지 채움 (화면 내 상태)
 * - 「경로 확인하기」 → 16 도착지 확인 모달 (onConfirmRoute, 디스크립션상 미연결·연결 필요)
 *
 * 유효값 검증:
 * - 도착지 미입력 시 CTA 비활성
 * - 출발지 = 도착지이면 「너무 가까워요」 로 차단 (500m 판정은 좌표 미연동으로 동일 문자열 기준)
 */
@Composable
fun DestinationScreen(
    origin: String = "부산대학교 정문",
    initialDestination: String = "",
    favoritePlaces: List<FavoritePlace> = listOf(
        FavoritePlace("집", "서면 롯데백화점"),
        FavoritePlace("학교", "부산대학교 정문"),
        FavoritePlace("알바", "센텀시티역 3번"),
    ),
    recentSearches: List<RecentPlace> = listOf(
        RecentPlace("서면역 1번 출구", "부산진구 부전동", "6.2km"),
        RecentPlace("사상역 환승센터", "사상구 괘법동", "8.4km"),
        RecentPlace("부산역 광장", "동구 초량동", "11.0km"),
        RecentPlace("해운대역", "해운대구 우동", "18.6km"),
    ),
    onBack: () -> Unit = {},
    onOriginClick: () -> Unit = {}, // 지도에서 출발지 조정 — 미연결
    onConfirmRoute: (destination: String) -> Unit = {},
) {
    var destination by rememberSaveable(initialDestination) { mutableStateOf(initialDestination) }
    val tooClose = destination.trim().isNotEmpty() && destination.trim() == origin.trim()
    val ctaEnabled = destination.trim().isNotEmpty() && !tooClose

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
                        value = destination,
                        onValueChange = { destination = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MoyeotaColor.InkPrimary,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (destination.isEmpty()) {
                        Text(
                            text = "도착지를 입력해 주세요",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = GrayAsh,
                        )
                    }
                }
            }

            Spacer(Modifier.height(26.dp))

            // 자주 가는 곳
            Text(
                text = "자주 가는 곳",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = GrayMute,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                favoritePlaces.forEach { place ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(72.dp)
                            .shadow(4.dp, RoundedCornerShape(16.dp), spotColor = Color(0x1A1B2A4A))
                            .clip(RoundedCornerShape(16.dp))
                            .background(MoyeotaColor.SurfaceCanvas)
                            .clickable { destination = place.address }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        StarIcon()
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = place.label,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MoyeotaColor.InkPrimary,
                        )
                        Text(
                            text = place.address,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = GrayMute,
                            maxLines = 1,
                        )
                    }
                }
            }

            Spacer(Modifier.height(26.dp))

            // 최근 검색
            Text(
                text = "최근 검색",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = GrayMute,
            )
            Spacer(Modifier.height(10.dp))
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
                            .clickable { destination = place.name }
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
                onClick = { onConfirmRoute(destination.trim()) },
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
